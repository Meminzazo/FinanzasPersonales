package com.finanzaspersonales.data.repository
import androidx.room.withTransaction
import com.finanzaspersonales.data.local.*
import com.finanzaspersonales.domain.model.*
import com.finanzaspersonales.domain.repository.FinanceRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import androidx.room.InvalidationTracker

class LocalFinanceRepository @Inject constructor(private val db: FinanceDatabase): FinanceRepository {
 private val dao=db.dao()
 override val ledger: Flow<Ledger> = db.invalidationTracker.createFlow("categories","movements","schedules","goals","savings","credit_accounts","debt_payments","installment_plans", emitInitialState=true).map { snapshot() }
 private suspend fun read() = Ledger(
  dao.categories().map { Category(it.id,it.name,Kind.valueOf(it.kind),it.archived) },
  dao.movements().map { Movement(it.id,it.cents,it.categoryId,LocalDate.parse(it.date),it.note,it.occurrence,it.accountId) },
  dao.schedules().map { Schedule(it.id,it.title,it.cents,it.categoryId,LocalDate.parse(it.due),Frequency.valueOf(it.frequency),it.anchorDay,it.accountId,it.paymentsTotal,it.paymentsRemaining) },
  dao.goals().map { Goal(it.id,it.name,it.target,it.deadline?.let(LocalDate::parse)) },
  dao.savings().map { Saving(it.id,it.goalId,it.cents,LocalDate.parse(it.date)) },
  dao.accounts().map { CreditAccount(it.id,it.name,CreditType.valueOf(it.type),it.openingCents,LocalDate.parse(it.openedOn),it.archived,it.cutoffDay) },
  dao.payments().map { DebtPayment(it.id,it.accountId,it.cents,LocalDate.parse(it.date),it.planId,it.installment) },
  dao.plans().map { InstallmentPlan(it.id,it.title,it.accountId,it.totalCents,it.months,it.paidBefore,LocalDate.parse(it.firstDue),LocalDate.parse(it.purchaseDate),it.categoryId,it.historical,it.includedInDebt,LocalDate.parse(it.registeredOn),it.amounts?.split(",")?.map(String::toLong) ?: emptyList()) })
 override suspend fun snapshot() = db.withTransaction { read() }
 override suspend fun seed() { db.withTransaction { if(dao.categories().isEmpty()) {
  listOf("Alimentación","Transporte","Vivienda","Servicios","Salud","Entretenimiento","Otros gastos").forEachIndexed { i,n -> dao.category(CategoryRow("expense-$i",n,"EXPENSE",false)) }
  listOf("Sueldo","Otros ingresos").forEachIndexed { i,n -> dao.category(CategoryRow("income-$i",n,"INCOME",false)) }
 } } }
 private suspend fun change(transform: (Ledger)->Ledger, write: suspend ()->Unit) { db.withTransaction { Rules.validate(transform(read())); write() } }
 override suspend fun category(c: Category) = change({ it.copy(categories=it.categories.filterNot { v->v.id==c.id }+c) }) { dao.category(CategoryRow(c.id,c.name,c.kind.name,c.archived)) }
 override suspend fun movement(m: Movement) = change({ it.copy(movements=it.movements.filterNot { v->v.id==m.id }+m) }) { dao.movement(MovementRow(m.id,m.cents,m.categoryId,m.date.toString(),m.note,m.occurrence,m.accountId)) }
 override suspend fun deleteMovement(id: String) = change({ it.copy(movements=it.movements.filterNot { m->m.id==id }) }) { dao.deleteMovement(id) }
 override suspend fun schedule(s: Schedule) = change({ it.copy(schedules=it.schedules.filterNot { v->v.id==s.id }+s) }) { dao.schedule(ScheduleRow(s.id,s.title,s.cents,s.categoryId,s.due.toString(),s.frequency.name,s.anchorDay,s.accountId,s.paymentsTotal,s.paymentsRemaining)) }
 override suspend fun deleteSchedule(id: String) { dao.deleteSchedule(id) }
 override suspend fun confirm(id: String, due: LocalDate, cents: Long, date: LocalDate) {
  db.withTransaction {
   val l=read();val s=l.schedules.first { it.id==id }
   require(s.due==due) { "Este vencimiento ya fue confirmado. Actualiza la pantalla." }
   val key="$id:$due";require(l.movements.none { it.occurrence==key })
   movement(Movement(cents=cents,categoryId=s.categoryId,date=date,note=s.title,occurrence=key,accountId=s.accountId))
   if(s.paymentsRemaining==1) dao.deleteSchedule(id) else schedule(s.copy(due=Rules.next(s),paymentsRemaining=s.paymentsRemaining?.minus(1)))
  }
 }
 override suspend fun goal(g: Goal) = change({ it.copy(goals=it.goals.filterNot { v->v.id==g.id }+g) }) { dao.goal(GoalRow(g.id,g.name,g.target,g.deadline?.toString())) }
 override suspend fun saving(s: Saving) = change({ it.copy(savings=it.savings.filterNot {v->v.id==s.id}+s) }) { dao.saving(SavingRow(s.id,s.goalId,s.cents,s.date.toString())) }
 override suspend fun account(a: CreditAccount) = change({ it.copy(accounts=it.accounts.filterNot { v->v.id==a.id }+a) }) { dao.account(AccountRow(a.id,a.name,a.type.name,a.openingCents,a.openedOn.toString(),a.archived,a.cutoffDay)) }
 override suspend fun payment(p:DebtPayment) {
  db.withTransaction {
   val l=read();val existing=l.payments.firstOrNull {it.id==p.id}
   var next=l.copy(payments=l.payments.filterNot {it.id==p.id}+p)
   if(existing?.planId!=null) {
    require(p.date<=LocalDate.now()) {"La fecha de un pago MSI debe ser de hoy o anterior."}
    require(p.planId==existing.planId && p.installment==existing.installment && p.accountId==existing.accountId) {"Edita la tarjeta o el plazo desde la compra MSI."}
    val plan=l.plans.first {it.id==p.planId}
    val amounts=InstallmentAmounts.allocate(plan.totalCents,plan.months,plan.paidBefore,plan.previousPaid,next.payments.filter {it.planId==plan.id}.sortedBy {it.installment}.map {it.cents})
    val updated=plan.copy(amounts=amounts);next=next.copy(plans=next.plans.filterNot {it.id==plan.id}+updated)
    Rules.validate(next)
    dao.plan(PlanRow(updated.id,updated.title,updated.accountId,updated.totalCents,updated.months,updated.paidBefore,updated.firstDue.toString(),updated.purchaseDate.toString(),updated.categoryId,updated.historical,updated.includedInDebt,updated.registeredOn.toString(),updated.amounts.joinToString(",")))
   } else Rules.validate(next)
   dao.payment(PaymentRow(p.id,p.accountId,p.cents,p.date.toString(),p.planId,p.installment))
  }
 }
 override suspend fun deletePayment(id: String) = change({it.copy(payments=it.payments.filterNot {p->p.id==id})}) { dao.deletePayment(id) }
 override suspend fun createPlan(p:InstallmentPlan) {
  db.withTransaction {
   val l=read();require(l.plans.none {it.id==p.id}) {"Esta compra MSI ya fue registrada."}
   val next=l.copy(plans=l.plans+p,movements=if(p.historical) l.movements else l.movements+p.purchase())
   Rules.validate(next)
   dao.plan(PlanRow(p.id,p.title,p.accountId,p.totalCents,p.months,p.paidBefore,p.firstDue.toString(),p.purchaseDate.toString(),p.categoryId,p.historical,p.includedInDebt,p.registeredOn.toString(),p.amounts.takeIf {it.isNotEmpty()}?.joinToString(",")))
   if(!p.historical) {val m=p.purchase();dao.movement(MovementRow(m.id,m.cents,m.categoryId,m.date.toString(),m.note,m.occurrence,m.accountId))}
  }
 }
 override suspend fun updatePlan(p:InstallmentPlan) {
  db.withTransaction {
   val l=read();val next=l.revisingPlan(p);Rules.validate(next)
   dao.clearPlanPayments(p.id)
   dao.deleteMovement(p.movementId)
   dao.plan(PlanRow(p.id,p.title,p.accountId,p.totalCents,p.months,p.paidBefore,p.firstDue.toString(),p.purchaseDate.toString(),p.categoryId,p.historical,p.includedInDebt,p.registeredOn.toString(),p.amounts.takeIf {it.isNotEmpty()}?.joinToString(",")))
   if(!p.historical) {val m=p.purchase();dao.movement(MovementRow(m.id,m.cents,m.categoryId,m.date.toString(),m.note,m.occurrence,m.accountId))}
   next.payments.filter {it.planId==p.id}.forEach {dao.payment(PaymentRow(it.id,it.accountId,it.cents,it.date.toString(),it.planId,it.installment))}
  }
 }
 override suspend fun deletePlan(id:String) {
  db.withTransaction {
   val l=read();val p=l.plans.first {it.id==id};require(l.payments.none {it.planId==id}) {"Primero deshaz los pagos de esta compra, del último al primero."}
   Rules.validate(l.copy(plans=l.plans.filterNot {it.id==id},movements=if(p.historical) l.movements else l.movements.filterNot {it.id==p.movementId}))
   dao.deletePlan(id);if(!p.historical) dao.deleteMovement(p.movementId)
  }
 }
 override suspend fun confirmInstallment(id:String,number:Int,date:LocalDate) {
  db.withTransaction {
   val l=read();val p=l.plans.first {it.id==id}
   require(l.nextInstallment(p)==number) {"Esta mensualidad ya cambió. Actualiza la pantalla."}
   require(date<=LocalDate.now()) {"Registra un pago realizado, con fecha de hoy o anterior."}
   payment(DebtPayment(accountId=p.accountId,cents=p.amount(number),date=date,planId=id,installment=number))
  }
 }
 override suspend fun restore(l: Ledger) {
  Rules.validate(l)
  db.withTransaction {
   dao.clearPayments();dao.clearPlans();dao.clearAccounts();dao.clearSavings();dao.clearMovements();dao.clearSchedules();dao.clearGoals();dao.clearCategories()
   l.plans.forEach {p->dao.plan(PlanRow(p.id,p.title,p.accountId,p.totalCents,p.months,p.paidBefore,p.firstDue.toString(),p.purchaseDate.toString(),p.categoryId,p.historical,p.includedInDebt,p.registeredOn.toString(),p.amounts.takeIf {it.isNotEmpty()}?.joinToString(",")))}
   l.accounts.forEach { dao.account(AccountRow(it.id,it.name,it.type.name,it.openingCents,it.openedOn.toString(),it.archived,it.cutoffDay)) }
   l.payments.forEach { dao.payment(PaymentRow(it.id,it.accountId,it.cents,it.date.toString(),it.planId,it.installment)) }
   l.categories.forEach { dao.category(CategoryRow(it.id,it.name,it.kind.name,it.archived)) }
   l.movements.forEach { dao.movement(MovementRow(it.id,it.cents,it.categoryId,it.date.toString(),it.note,it.occurrence,it.accountId)) }
   l.schedules.forEach { dao.schedule(ScheduleRow(it.id,it.title,it.cents,it.categoryId,it.due.toString(),it.frequency.name,it.anchorDay,it.accountId,it.paymentsTotal,it.paymentsRemaining)) }
   l.goals.forEach { dao.goal(GoalRow(it.id,it.name,it.target,it.deadline?.toString())) }
   l.savings.forEach { dao.saving(SavingRow(it.id,it.goalId,it.cents,it.date.toString())) }
  }
 }
}
