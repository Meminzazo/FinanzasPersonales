package com.finanzaspersonales
import com.finanzaspersonales.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.YearMonth
class InstallmentsTest {
 private val day=LocalDate.parse("2026-01-01")
 private fun plan(historical:Boolean=false,included:Boolean=false,paidBefore:Int=0)=InstallmentPlan("p","Compra","a",10001,3,paidBefore,LocalDate.parse("2026-01-31"),day,"e",historical,included,day)
 private fun ledger(p:InstallmentPlan,opening:Long=0)=Ledger(categories=listOf(Category("e","Compras",Kind.EXPENSE),Category("i","Ingreso",Kind.INCOME)),accounts=listOf(CreditAccount("a","Tarjeta",CreditType.CARD,opening,day)),plans=listOf(p),movements=listOf(Movement(cents=50000,categoryId="i",date=day))+(if(p.historical) emptyList() else listOf(p.purchase())))
 @Test fun monthlyReportsUseDueDatesEvenWhenAllPaymentsAreEarly() {
  val p=plan();val l=ledger(p).copy(payments=(1..3).map {DebtPayment(accountId="a",cents=p.amount(it),date=day,planId="p",installment=it)})
  assertEquals(3333L,l.total(Kind.EXPENSE,YearMonth.of(2026,1)))
  assertEquals(3333L,l.total(Kind.EXPENSE,YearMonth.of(2026,2)))
  assertEquals(3335L,l.total(Kind.EXPENSE,YearMonth.of(2026,3)))
  assertEquals(0L,l.expensesOn(day))
  assertEquals(3333L,l.expensesOn(LocalDate.parse("2026-01-31")))
  assertTrue(l.reportingMovements(YearMonth.of(2026,1)).none {it.id==p.movementId})
 }
 @Test fun revisionPreservesActualPaidMoneyAndSpreadsOnlyRemainingAmounts() {
  val p=plan();val pay=DebtPayment(accountId="a",cents=3333,date=day,planId="p",installment=1)
  val l=ledger(p).copy(payments=listOf(pay))
  val amounts=InstallmentAmounts.allocate(12000,4,0,0,listOf(3333))
  assertEquals(listOf(3333L,2889L,2889L,2889L),amounts)
  val changed=p.copy(title="Corregida",totalCents=12000,months=4,amounts=amounts)
  val revised=l.revisingPlan(changed);Rules.validate(revised)
  assertEquals(l.payments,revised.payments);assertEquals(l.balance(day),revised.balance(day));assertEquals(8667L,revised.pending("a",day))
  assertTrue(runCatching {InstallmentAmounts.allocate(3000,4,0,0,listOf(3333))}.isFailure)
 }
 @Test fun changingPriorCountKeepsRealPaymentsAndRenumbersWithoutChangingCash() {
  val p=plan(true,false,1);val pay=DebtPayment(accountId="a",cents=3333,date=day,planId="p",installment=2)
  val l=ledger(p).copy(payments=listOf(pay));Rules.validate(l)
  val changed=p.copy(paidBefore=0,amounts=InstallmentAmounts.allocate(p.totalCents,3,0,0,listOf(3333)))
  val revised=l.revisingPlan(changed);Rules.validate(revised)
  assertEquals(1,revised.payments.single().installment);assertEquals(pay.id,revised.payments.single().id);assertEquals(l.balance(day),revised.balance(day))
 }
 @Test fun exactCentsAndAnchoredMonthlyDates() {
  val p=plan();assertEquals(listOf(3333L,3333L,3335L),(1..3).map(p::amount));assertEquals(p.totalCents,(1..3).sumOf(p::amount))
  assertEquals(LocalDate.parse("2026-02-28"),p.due(2));assertEquals(LocalDate.parse("2026-03-31"),p.due(3))
 }
 @Test fun fullTermEndsWithoutRepeatingPurchaseExpense() {
  val p=plan();val initial=ledger(p);Rules.validate(initial)
  assertEquals(50000L,initial.balance(day));assertEquals(10001L,initial.pending("a",day))
  val payments=(1..3).map {DebtPayment(accountId="a",cents=p.amount(it),date=day,planId="p",installment=it)}
  val full=initial.copy(payments=payments);Rules.validate(full)
  assertNull(full.nextInstallment(p));assertEquals(0L,full.pending("a",day));assertEquals(39999L,full.balance(day));assertEquals(3333L,full.total(Kind.EXPENSE,YearMonth.from(day)));assertEquals(3333L,full.total(Kind.EXPENSE,YearMonth.of(2026,2)));assertEquals(3335L,full.total(Kind.EXPENSE,YearMonth.of(2026,3)))
 }
 @Test fun historicalPlanCanUseExistingDebtWithoutAddingExpenseAgain() {
  val p=plan(true,true,1);val l=ledger(p,10000);Rules.validate(l)
  assertEquals(10000L,l.pending("a",day));assertEquals(6668L,l.planRemaining(p,day));assertEquals(3332L,l.freeDebt("a",day));assertEquals(0L,l.total(Kind.EXPENSE,YearMonth.from(day)))
  val added=l.copy(plans=listOf(p.copy(includedInDebt=false)));Rules.validate(added);assertEquals(16668L,added.pending("a",day))
 }
 @Test fun genericPaymentsCannotConsumeReservedInstallmentsAndUndoMustBeLast() {
  val p=plan();val initial=ledger(p)
  assertTrue(runCatching {Rules.validate(initial.copy(payments=listOf(DebtPayment(accountId="a",cents=1,date=day))))}.isFailure)
  val payments=(1..2).map {DebtPayment(accountId="a",cents=p.amount(it),date=day,planId="p",installment=it)}
  val l=initial.copy(payments=payments);Rules.validate(l)
  assertTrue(runCatching {Rules.validate(l.copy(payments=payments.drop(1)))}.isFailure)
  Rules.validate(l.copy(payments=payments.take(1)))
  assertTrue(runCatching {Rules.validate(l.copy(movements=l.movements.filterNot {it.id==p.movementId}))}.isFailure)
 }
}
