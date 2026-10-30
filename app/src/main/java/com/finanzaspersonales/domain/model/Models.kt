package com.finanzaspersonales.domain.model
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

fun newId() = UUID.randomUUID().toString()
enum class Kind { EXPENSE, INCOME }
enum class Frequency { WEEKLY, FIFTEEN_DAYS, MONTHLY }
data class Category(val id: String = newId(), val name: String, val kind: Kind, val archived: Boolean = false)
data class Movement(val id: String = newId(), val cents: Long, val categoryId: String, val date: LocalDate, val note: String = "", val occurrence: String? = null, val accountId: String? = null)
data class Schedule(val id: String = newId(), val title: String, val cents: Long, val categoryId: String, val due: LocalDate, val frequency: Frequency, val anchorDay: Int = due.dayOfMonth, val accountId: String? = null, val paymentsTotal: Int? = null, val paymentsRemaining: Int? = null)
data class Goal(val id: String = newId(), val name: String, val target: Long, val deadline: LocalDate? = null)
data class Saving(val id: String = newId(), val goalId: String, val cents: Long, val date: LocalDate)
enum class CreditType { CARD, DEBT }
data class CreditAccount(val id: String = newId(), val name: String, val type: CreditType, val openingCents: Long = 0, val openedOn: LocalDate = LocalDate.now(), val archived: Boolean = false, val cutoffDay: Int? = null)
data class DebtPayment(val id: String = newId(), val accountId: String, val cents: Long, val date: LocalDate, val planId: String? = null, val installment: Int? = null)
data class Ledger(val categories: List<Category> = emptyList(), val movements: List<Movement> = emptyList(), val schedules: List<Schedule> = emptyList(), val goals: List<Goal> = emptyList(), val savings: List<Saving> = emptyList(), val accounts: List<CreditAccount> = emptyList(), val payments: List<DebtPayment> = emptyList(), val plans: List<InstallmentPlan> = emptyList()) {
 fun kind(m: Movement) = categories.first { it.id == m.categoryId }.kind
 fun total(kind: Kind, month: YearMonth) = reportingMovements(month).filter { kind(it) == kind }.sumOf { it.cents }
 fun balance(asOf: LocalDate = LocalDate.now()): Long = movements.filter { !it.date.isAfter(asOf) && it.accountId==null }.sumOf { if(kind(it)==Kind.INCOME) it.cents else -it.cents } - payments.filter { !it.date.isAfter(asOf) }.sumOf { it.cents }
 fun pending(id: String, asOf: LocalDate = LocalDate.now()): Long {
  val account=accounts.first { it.id==id }
  return (if(account.openedOn<=asOf) account.openingCents else 0) + plans.filter {it.accountId==id && it.historical && !it.includedInDebt && it.activeFrom<=asOf}.sumOf {it.initialRemaining} + movements.filter { it.accountId==id && it.date<=asOf }.sumOf { it.cents } - payments.filter { it.accountId==id && it.date<=asOf }.sumOf { it.cents }
 }
 fun totalDebt(asOf: LocalDate = LocalDate.now()) = accounts.sumOf { pending(it.id,asOf) }
 fun saved(goal: String) = savings.filter { it.goalId == goal }.sumOf { it.cents }
}
object Rules {
 const val MAX_CENTS = 999_999_999_99L
 fun cents(input: String): Long {
  require(input.trim().matches(Regex("[0-9]{1,9}([.,][0-9]{1,2})?"))) { "Escribe un monto válido con hasta 2 decimales." }
  return BigDecimal(input.trim().replace(',', '.')).movePointRight(2).longValueExact().also { require(it in 1..MAX_CENTS) { "El monto debe ser mayor que cero." } }
 }
 fun next(s: Schedule): LocalDate = when(s.frequency) {
  Frequency.WEEKLY -> s.due.plusWeeks(1)
  Frequency.FIFTEEN_DAYS -> s.due.plusDays(15)
  Frequency.MONTHLY -> YearMonth.from(s.due).plusMonths(1).let { it.atDay(minOf(s.anchorDay, it.lengthOfMonth())) }
 }
 fun validate(l: Ledger) {
  require(l.categories.size <= 1000 && l.movements.size <= 100000 && l.schedules.size <= 10000 && l.goals.size <= 10000 && l.savings.size <= 100000 && l.accounts.size <= 1000 && l.payments.size <= 100000) { "El respaldo excede los límites permitidos." }
  fun unique(ids: List<String>) { require(ids.all { it.isNotBlank() && it.length <= 100 } && ids.distinct().size == ids.size) { "Identificadores inválidos o repetidos." } }
  unique(l.accounts.map { it.id }); unique(l.payments.map { it.id })
  unique(l.categories.map { it.id }); unique(l.movements.map { it.id }); unique(l.schedules.map { it.id }); unique(l.goals.map { it.id }); unique(l.savings.map { it.id })
  val cats=l.categories.map { it.id }.toSet(); val goals=l.goals.map { it.id }.toSet()
  val accountIds=l.accounts.map { it.id }.toSet()
  require(l.accounts.all { it.name.isNotBlank() && it.name.length<=80 && it.openingCents in 0..MAX_CENTS && (it.cutoffDay==null || (it.type==CreditType.CARD && it.cutoffDay in 1..31)) }) { "Revisa el nombre y saldo inicial de la tarjeta o deuda." }
  require(l.movements.all { it.accountId==null || (it.accountId in accountIds && l.kind(it)==Kind.EXPENSE) }) { "Solo los gastos pueden asociarse a una tarjeta o deuda existente." }
  require(l.schedules.all { it.accountId==null || (it.accountId in accountIds && l.categories.firstOrNull { c->c.id==it.categoryId }?.kind==Kind.EXPENSE) }) { "Solo los gastos fijos pueden cargarse a una tarjeta o deuda." }
  require(l.payments.all { it.accountId in accountIds && it.cents in 1..MAX_CENTS }) { "Revisa el monto y destino del pago." }
  MsiRules.validate(l)
  l.accounts.forEach { a ->
   val events=listOf(a.openedOn to a.openingCents) + l.plans.filter {it.accountId==a.id}.map {it.activeFrom to if(it.historical && !it.includedInDebt) it.initialRemaining else 0L} + l.movements.filter { it.accountId==a.id }.map { it.date to it.cents } + l.payments.filter { it.accountId==a.id }.map { it.date to -it.cents }
   var pending=0L
   var reserved=0L
   val reserves=(l.plans.filter {it.accountId==a.id}.map {it.activeFrom to it.initialRemaining} + l.payments.filter {it.accountId==a.id && it.planId!=null}.map {it.date to -it.cents}).groupBy {it.first}.mapValues {entry->entry.value.sumOf {it.second}}
   events.groupBy { it.first }.toSortedMap().values.forEach { day ->
    pending+=day.sumOf { it.second }
    require(pending>=0) { "El pago excede la deuda de ${a.name} en esa fecha. Revisa también los cargos ya pagados antes de modificarlos." }
    reserved+=reserves[day.first().first] ?: 0
    require(pending>=reserved) { "Esta deuda está asignada a MSI. Registra sus mensualidades desde Programados; revisa también si ya incluiste la compra en la deuda de la tarjeta." }
   }
  }
  require(l.categories.all { it.name.isNotBlank() && it.name.length <= 80 })
  require(l.movements.all { it.cents in 1..MAX_CENTS && it.categoryId in cats && it.note.length <= 500 })
  require(l.schedules.all { it.cents in 1..MAX_CENTS && it.categoryId in cats && it.anchorDay in 1..31 && it.title.isNotBlank() && it.title.length <= 80 && (if(it.paymentsTotal==null) it.paymentsRemaining==null else it.paymentsTotal in 1..600 && it.paymentsRemaining?.let {remaining->remaining in 1..it.paymentsTotal}==true && l.categories.first {c->c.id==it.categoryId}.kind==Kind.EXPENSE && (it.accountId==null || l.accounts.any {a->a.id==it.accountId && a.type==CreditType.CARD})) }) { "Revisa el número de cuotas y la forma de pago del préstamo." }
  require(l.goals.all { it.target in 1..MAX_CENTS && it.name.isNotBlank() && it.name.length <= 80 })
  require(l.savings.all { it.goalId in goals && it.cents != 0L && it.cents in -MAX_CENTS..MAX_CENTS })
  require(l.goals.all { l.saved(it.id) >= 0 }) { "Un ahorro no puede quedar en negativo." }
  val occurrences=l.movements.mapNotNull { it.occurrence }; require(occurrences.distinct().size == occurrences.size)
 }
}
