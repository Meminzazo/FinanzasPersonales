package com.finanzaspersonales.domain.model
import java.time.LocalDate
import java.time.YearMonth

/** Reports use installment due dates; cash and debt retain actual payment dates. */
fun Ledger.reportingMovements(month:YearMonth):List<Movement> {
 val purchases=plans.filterNot {it.historical}.map {it.movementId}.toSet()
 val regular=movements.filter {it.id !in purchases}.map {m ->
  val account=accounts.firstOrNull {it.id==m.accountId && it.type==CreditType.CARD}
  val reportDate=if(account?.cutoffDay!=null && m.date.dayOfMonth>account.cutoffDay) m.date.plusMonths(1) else m.date
  if(reportDate==m.date) m else m.copy(date=reportDate)
 }.filter {YearMonth.from(it.date)==month}
 val installments=plans.flatMap {p->(p.paidBefore+1..p.months).filter {YearMonth.from(p.due(it))==month}.map {n->Movement("msi-report-${p.id}-$n",p.amount(n),p.categoryId,p.due(n),"${p.title} · MSI $n/${p.months}","MSI:${p.id}",p.accountId)}}
 return (regular+installments).sortedByDescending {it.date}
}
fun Ledger.expensesOn(day:LocalDate):Long=reportingMovements(YearMonth.from(day)).filter {it.date==day && kind(it)==Kind.EXPENSE}.sumOf {it.cents}
object InstallmentAmounts {
 fun allocate(total:Long,months:Int,previousCount:Int,previousMoney:Long,recorded:List<Long>):List<Long> {
  require(months in 1..72 && previousCount in 0..months && previousCount+recorded.size<=months) {"El plazo no puede ser menor que las mensualidades ya pagadas."}
  require(total in 1..Rules.MAX_CENTS && recorded.all {it in 1..Rules.MAX_CENTS})
  require(if(previousCount==0) previousMoney==0L else previousMoney>=previousCount) {"Revisa el importe pagado antes de usar la app."}
  val left=total-previousMoney-recorded.sum();val count=months-previousCount-recorded.size
  require(if(count==0) left==0L else left>=count) {"El total debe cubrir lo ya pagado y dejar al menos un centavo para cada mensualidad pendiente. Ajusta el total o el plazo."}
  fun divide(money:Long,n:Int)=if(n==0) emptyList() else (1..n).map {money/n+if(it==n)money%n else 0}
  return divide(previousMoney,previousCount)+recorded+divide(left,count)
 }
}
/** Preserve real payments while correcting a plan, including its original card. */
fun Ledger.revisingPlan(p:InstallmentPlan):Ledger {
 val old=plans.first {it.id==p.id}
 val oldPayments=payments.filter {it.planId==p.id}.sortedBy {it.installment}
 val adjusted=oldPayments.mapIndexed {i,pay->pay.copy(accountId=p.accountId,installment=p.paidBefore+i+1)}
 val regular=movements.filterNot {!old.historical && it.id==old.movementId}
 return copy(plans=plans.filterNot {it.id==p.id}+p,payments=payments.filterNot {it.planId==p.id}+adjusted,movements=if(p.historical) regular else regular+p.purchase())
}
