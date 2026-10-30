package com.finanzaspersonales.domain.model
import java.time.LocalDate
import java.time.YearMonth

data class InstallmentPlan(val id:String=newId(),val title:String,val accountId:String,val totalCents:Long,val months:Int,val paidBefore:Int=0,val firstDue:LocalDate,val purchaseDate:LocalDate,val categoryId:String,val historical:Boolean=false,val includedInDebt:Boolean=false,val registeredOn:LocalDate=LocalDate.now(),val amounts:List<Long> = emptyList()) {
 val movementId get()="msi-$id"
 val activeFrom get()=if(historical) registeredOn else purchaseDate
 fun amount(number:Int):Long {require(number in 1..months);return if(amounts.isEmpty()) totalCents/months + if(number==months) totalCents%months else 0 else amounts[number-1]}
 fun due(number:Int):LocalDate {require(number in 1..months);val m=YearMonth.from(firstDue).plusMonths((number-1).toLong());return m.atDay(minOf(firstDue.dayOfMonth,m.lengthOfMonth()))}
 val previousPaid get()=(1..paidBefore).sumOf {amount(it)}
 val initialRemaining get()=totalCents-previousPaid
 fun purchase()=Movement(movementId,totalCents,categoryId,purchaseDate,title,"MSI:$id",accountId)
}
fun Ledger.planRemaining(p:InstallmentPlan,asOf:LocalDate=LocalDate.now()):Long = if(asOf<p.activeFrom) 0 else p.initialRemaining-payments.filter {it.planId==p.id && it.date<=asOf}.sumOf {it.cents}
fun Ledger.nextInstallment(p:InstallmentPlan):Int? = (p.paidBefore+payments.count {it.planId==p.id}+1).takeIf {it<=p.months}
fun Ledger.freeDebt(accountId:String,asOf:LocalDate=LocalDate.now()):Long = pending(accountId,asOf)-plans.filter {it.accountId==accountId}.sumOf {planRemaining(it,asOf)}
object MsiRules {
 fun validate(l:Ledger) {
  require(l.plans.size<=10000 && l.plans.map {it.id}.distinct().size==l.plans.size) {"Compras MSI repetidas o demasiadas compras."}
  l.plans.forEach {p->
   require(p.id.isNotBlank() && p.id.length<=80 && p.title.isNotBlank() && p.title.length<=80)
   require(p.months in 1..72 && p.paidBefore in 0..p.months && p.totalCents in p.months.toLong()..Rules.MAX_CENTS) {"Revisa el total, las mensualidades (1 a 72) y cuántas ya pagaste."}
   require(p.amounts.isEmpty() || (p.amounts.size==p.months && p.amounts.all {it in 1..Rules.MAX_CENTS} && p.amounts.sum()==p.totalCents)) {"La distribución de mensualidades no coincide con el total de la compra."}
   require(l.accounts.any {it.id==p.accountId} && l.categories.any {it.id==p.categoryId && it.kind==Kind.EXPENSE}) {"Selecciona una tarjeta o deuda y categoría de gasto válidas."}
   require(p.historical || (p.paidBefore==0 && !p.includedInDebt && p.firstDue>=p.purchaseDate)) {"Una compra nueva no puede tener pagos previos y su primer vencimiento debe ser posterior a la compra."}
   if(!p.historical) require(l.movements.any {it==p.purchase()}) {"Este gasto pertenece a una compra MSI. Administra la compra desde Deudas."}
   val payments=l.payments.filter {it.planId==p.id}.sortedBy {it.installment}
   require(payments.map {it.installment}==(p.paidBefore+1..p.paidBefore+payments.size).toList()) {"Solo puedes deshacer la última mensualidad pagada, conservando el orden de los pagos."}
   require(payments.all {it.accountId==p.accountId && it.installment!=null && it.installment in 1..p.months && it.cents==p.amount(it.installment) && it.date>=p.activeFrom}) {"El importe y la cuenta del pago deben corresponder a su mensualidad MSI."}
   require(payments.zipWithNext().all {it.first.date<=it.second.date}) {"Las fechas de pago MSI deben conservar el orden de las mensualidades."}
  }
  require(l.payments.all { (it.planId==null)==(it.installment==null) && (it.planId==null || l.plans.any {p->p.id==it.planId}) }) {"Pago MSI sin compra asociada."}
 }
}
