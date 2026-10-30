package com.finanzaspersonales
import com.finanzaspersonales.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
class RulesTest {
 @Test fun finiteLoanNeedsExpenseCategoryAndValidRemainingPayments() {
  val expense=Category("e","Prestamos",Kind.EXPENSE)
  val income=Category("i","Sueldo",Kind.INCOME)
  val due=LocalDate.parse("2026-10-22")
  val loan=Schedule("loan","Juego",1000,"e",due,Frequency.MONTHLY,paymentsTotal=3,paymentsRemaining=3)
  val ledger=Ledger(categories=listOf(expense,income),schedules=listOf(loan))
  Rules.validate(ledger)
  assertEquals(LocalDate.parse("2026-11-22"),Rules.next(loan))
  assertTrue(runCatching {Rules.validate(ledger.copy(schedules=listOf(loan.copy(paymentsRemaining=0))))}.isFailure)
  assertTrue(runCatching {Rules.validate(ledger.copy(schedules=listOf(loan.copy(categoryId="i"))))}.isFailure)
  assertTrue(runCatching {Rules.validate(ledger.copy(schedules=listOf(loan.copy(paymentsTotal=null))))}.isFailure)
 }
 @Test fun cardCutoffMovesOnlyReportMonthAndKeepsActualDebtDate() {
  val e=Category("e","Compras",Kind.EXPENSE)
  val card=CreditAccount("a","Tarjeta",CreditType.CARD,openedOn=LocalDate.parse("2026-10-01"),cutoffDay=15)
  val before=Movement("before",1000,"e",LocalDate.parse("2026-10-15"),accountId="a")
  val after=Movement("after",2000,"e",LocalDate.parse("2026-10-22"),accountId="a")
  val l=Ledger(categories=listOf(e),accounts=listOf(card),movements=listOf(before,after))
  Rules.validate(l)
  assertEquals(1000L,l.total(Kind.EXPENSE,YearMonth.of(2026,10)))
  assertEquals(2000L,l.total(Kind.EXPENSE,YearMonth.of(2026,11)))
  assertEquals(3000L,l.pending("a",LocalDate.parse("2026-10-22")))
  assertEquals(LocalDate.parse("2026-10-22"),l.movements.last().date)
  assertEquals(0L,l.balance(LocalDate.parse("2026-10-22")))
  val noCutoff=l.copy(accounts=listOf(card.copy(cutoffDay=null)))
  assertEquals(3000L,noCutoff.total(Kind.EXPENSE,YearMonth.of(2026,10)))
  val debt=l.copy(accounts=listOf(card.copy(type=CreditType.DEBT,cutoffDay=null)))
  assertEquals(3000L,debt.total(Kind.EXPENSE,YearMonth.of(2026,10)))
  assertTrue(runCatching {Rules.validate(l.copy(accounts=listOf(card.copy(cutoffDay=32))))}.isFailure)
 }
 @Test fun creditPurchaseAndPaymentDoNotDoubleCountExpenses() {
  val d=LocalDate.parse("2026-10-29")
  val categories=listOf(Category("i","Sueldo",Kind.INCOME),Category("e","Compras",Kind.EXPENSE))
  val a=CreditAccount("a","Tarjeta",CreditType.CARD,2000,d)
  val income=Movement(cents=10000,categoryId="i",date=d)
  val charge=Movement(cents=3000,categoryId="e",date=d,accountId="a")
  val l=Ledger(categories=categories,movements=listOf(income,charge),accounts=listOf(a))
  Rules.validate(l);assertEquals(5000L,l.pending("a",d));assertEquals(10000L,l.balance(d))
  val paid=l.copy(payments=listOf(DebtPayment(accountId="a",cents=1000,date=d)))
  Rules.validate(paid);assertEquals(4000L,paid.pending("a",d));assertEquals(9000L,paid.balance(d));assertEquals(3000L,paid.total(Kind.EXPENSE,YearMonth.from(d)))
  assertTrue(runCatching {Rules.validate(paid.copy(payments=listOf(DebtPayment(accountId="a",cents=6000,date=d))))}.isFailure)
  assertTrue(runCatching {Rules.validate(l.copy(movements=listOf(income.copy(accountId="a"))))}.isFailure)
 }
 @Test fun paymentCannotUseAFutureChargeOrAllowDeletingAnAlreadyPaidCharge() {
  val d=LocalDate.parse("2026-10-29")
  val a=CreditAccount("a","Deuda",CreditType.DEBT,0,d)
  val charge=Movement(cents=500,categoryId="e",date=d.plusDays(1),accountId="a")
  val l=Ledger(categories=listOf(Category("e","Compras",Kind.EXPENSE)),movements=listOf(charge),accounts=listOf(a),payments=listOf(DebtPayment(accountId="a",cents=100,date=d)))
  assertTrue(runCatching {Rules.validate(l)}.isFailure)
  val valid=l.copy(payments=listOf(DebtPayment(accountId="a",cents=100,date=d.plusDays(1))))
  Rules.validate(valid)
  assertEquals(0L,valid.pending("a",d));assertEquals(400L,valid.pending("a",d.plusDays(1)))
  assertTrue(runCatching {Rules.validate(valid.copy(movements=emptyList()))}.isFailure)
 }

 @Test fun balanceIncludesPreviousMonthsButNotFutureOrScheduledAmounts() {
  val today=LocalDate.parse("2026-10-29")
  val income=Category("i","Sueldo",Kind.INCOME);val expense=Category("e","Comida",Kind.EXPENSE)
  val goal=Goal("g","Ahorro",100000)
  val l=Ledger(categories=listOf(income,expense),movements=listOf(
   Movement(cents=100000,categoryId="i",date=today.minusMonths(1)),
   Movement(cents=25000,categoryId="e",date=today),
   Movement(cents=30000,categoryId="e",date=today.plusDays(1))),
   schedules=listOf(Schedule(title="Pendiente",cents=40000,categoryId="e",due=today,frequency=Frequency.MONTHLY)),
   goals=listOf(goal),savings=listOf(Saving(goalId="g",cents=10000,date=today)))
  assertEquals(75000L,l.balance(today))
  assertEquals(45000L,l.balance(today.plusDays(1)))
  assertEquals(-25000L,l.copy(movements=l.movements.filter {it.categoryId=="e"}).balance(today))
  assertEquals(0L,Ledger().balance(today))
 }

 @Test fun parsesExactMoneyAndRejectsAmbiguousValues() {
  assertEquals(12345L,Rules.cents("123,45"));assertEquals(1L,Rules.cents("0.01"))
  listOf("0","-1","1.234","1e3","NaN","1,000.00").forEach {assertTrue(it,runCatching {Rules.cents(it)}.isFailure)}
 }
 @Test fun monthlyRecurrenceKeepsOriginalDayAfterFebruary() {
  val s=Schedule(title="Renta",cents=100,categoryId="c",due=LocalDate.parse("2025-01-31"),frequency=Frequency.MONTHLY)
  val feb=Rules.next(s);assertEquals(LocalDate.parse("2025-02-28"),feb)
  assertEquals(LocalDate.parse("2025-03-31"),Rules.next(s.copy(due=feb)))
 }
 @Test fun savingsDoNotBecomeExpensesOrIncome() {
  val c=Category("c","Comida",Kind.EXPENSE);val g=Goal("g","Meta",10000)
  val l=Ledger(categories=listOf(c),movements=listOf(Movement(cents=100,categoryId="c",date=LocalDate.parse("2026-10-01"))),goals=listOf(g),savings=listOf(Saving(goalId="g",cents=300,date=LocalDate.now())))
  Rules.validate(l);assertEquals(100,l.total(Kind.EXPENSE,YearMonth.of(2026,10)));assertEquals(0,l.total(Kind.INCOME,YearMonth.of(2026,10)));assertEquals(300,l.saved("g"))
  assertTrue(runCatching {Rules.validate(l.copy(savings=l.savings+Saving(goalId="g",cents=-301,date=LocalDate.now())))}.isFailure)
 }
 @Test fun rejectsBrokenReferencesAndDuplicateOccurrences() {
  val m=Movement(cents=100,categoryId="missing",date=LocalDate.now(),occurrence="s:2026-01-01")
  assertTrue(runCatching {Rules.validate(Ledger(movements=listOf(m)))}.isFailure)
  val c=Category("missing","Comida",Kind.EXPENSE)
  assertTrue(runCatching {Rules.validate(Ledger(categories=listOf(c),movements=listOf(m,m.copy(id="other"))))}.isFailure)
 }
}
