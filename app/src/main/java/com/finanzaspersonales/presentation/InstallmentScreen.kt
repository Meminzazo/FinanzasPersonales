package com.finanzaspersonales.presentation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finanzaspersonales.domain.model.*
import java.time.LocalDate

@Composable internal fun InstallmentCard(p:InstallmentPlan,l:Ledger,busy:Boolean,onPay:(InstallmentPlan,Int)->Unit,onDelete:(InstallmentPlan)->Unit,onEdit:(InstallmentPlan)->Unit) {
 val next=l.nextInstallment(p);val paid=p.paidBefore+l.payments.count {it.planId==p.id}
 Card {Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Row(verticalAlignment=Alignment.CenterVertically) {Text(p.title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);IconButton(onClick={onEdit(p)},enabled=!busy) {Icon(Icons.Default.Edit,"Editar compra MSI")};IconButton(onClick={onDelete(p)},enabled=!busy && l.payments.none {it.planId==p.id}) {Icon(Icons.Default.DeleteOutline,"Eliminar compra MSI")}}
  Text("MSI · ${l.accounts.first {it.id==p.accountId}.name}",style=MaterialTheme.typography.bodySmall)
  Text("Importe total de la compra: ${money(p.totalCents)}",style=MaterialTheme.typography.bodySmall)
  Text("$paid de ${p.months} mensualidades pagadas",fontWeight=FontWeight.SemiBold)
  LinearProgressIndicator(progress={paid.toFloat()/p.months},modifier=Modifier.fillMaxWidth())
  Text("Por pagar: ${money(l.planRemaining(p))}",style=MaterialTheme.typography.titleMedium)
  if(p.paidBefore>0) Text("${p.paidBefore} pagadas antes de usar la app",style=MaterialTheme.typography.bodySmall)
  if(next!=null) {
   Text("Mensualidad $next: ${money(p.amount(next))} · ${p.due(next)}")
   if(p.due(next)<LocalDate.now()) Text("Mensualidad vencida pendiente de confirmar",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
   Button(onClick={onPay(p,next)},enabled=!busy && p.activeFrom<=LocalDate.now(),modifier=Modifier.fillMaxWidth()) {Text("Pagar mensualidad $next")}
  } else Text("Compra liquidada",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
 }}
}

@Composable internal fun InstallmentForm(l:Ledger,initialAccount:String?,dismiss:()->Unit,existing:InstallmentPlan?=null,onAddCard:(CreditAccount)->Unit,save:(InstallmentPlan)->Unit) {
 var name by remember {mutableStateOf(existing?.title ?: "")};var total by remember {mutableStateOf(existing?.totalCents?.let(::decimal) ?: "")};var months by remember {mutableStateOf(existing?.months?.toString() ?: "12")}
 var historical by remember {mutableStateOf(existing?.historical ?: false)};var paid by remember {mutableStateOf(existing?.paidBefore?.toString() ?: "0")};var included by remember {mutableStateOf(existing?.includedInDebt ?: false)}
 var previousMoney by remember {mutableStateOf(existing?.previousPaid?.let(::decimal) ?: "")}
 var account by remember {mutableStateOf(existing?.accountId ?: initialAccount ?: l.accounts.firstOrNull {!it.archived && it.type==CreditType.CARD}?.id ?: "")}
 var category by remember {mutableStateOf(existing?.categoryId ?: l.categories.firstOrNull {it.kind==Kind.EXPENSE && !it.archived}?.id ?: "")}
 var first by remember {mutableStateOf((existing?.firstDue ?: LocalDate.now().plusMonths(1)).toString())}
 var purchase by remember {mutableStateOf((existing?.let {if(it.historical) it.registeredOn else it.purchaseDate} ?: LocalDate.now()).toString())}
 var showCard by remember {mutableStateOf(false)}
 val recorded=if(existing==null) emptyList() else l.payments.filter {it.planId==existing.id}.sortedBy {it.installment}
 fun candidate():InstallmentPlan {
  require(name.isNotBlank() && name.length<=80) {"Escribe un concepto de hasta 80 caracteres."}
  val n=months.toIntOrNull();val previous=if(historical) paid.toIntOrNull() else 0
  require(n!=null && n in 1..72 && previous!=null && previous in 0..n) {"Elige de 1 a 72 meses y revisa los pagos previos."}
  require(account.isNotBlank() && category.isNotBlank()) {"Registra una tarjeta y elige una categoría."}
  require(LocalDate.parse(purchase)<=LocalDate.now()) {"La fecha debe ser de hoy o anterior."}
  val cents=Rules.cents(total)
  val outside=if(previous==0) 0L else if(previousMoney.isBlank()) cents/n*previous else Rules.cents(previousMoney)
  val amounts=InstallmentAmounts.allocate(cents,n,previous,outside,recorded.map {it.cents})
  return InstallmentPlan(existing?.id ?: newId(),name.trim(),account,cents,n,previous,LocalDate.parse(first),LocalDate.parse(purchase),category,historical,historical && included,if(historical) LocalDate.parse(purchase) else existing?.registeredOn ?: LocalDate.now(),amounts)
 }
 val result=runCatching {candidate().also {p->Rules.validate(if(existing!=null) l.revisingPlan(p) else l.copy(plans=l.plans+p,movements=if(p.historical) l.movements else l.movements+p.purchase()))}}
 FormDialog(if(existing==null) "Compra a meses sin intereses" else "Editar compra MSI",dismiss,result.isSuccess,onSave={save(result.getOrThrow())}) {
  Field(name,"Concepto de la compra",{name=it})
  Select("Tarjeta de crédito",l.accounts.filter {(!it.archived && it.type==CreditType.CARD) || it.id==existing?.accountId}.map {it.id to it.name},account) {account=it}
  OutlinedButton(onClick={showCard=true},modifier=Modifier.fillMaxWidth()) {Text("Registrar tarjeta de crédito")}
  if(l.accounts.none {!it.archived && it.type==CreditType.CARD}) Text("Primero registra tu tarjeta. Si no debías nada antes, deja su saldo inicial en cero.",style=MaterialTheme.typography.bodySmall)
  ChoiceRow(listOf("Compra nueva","Compra anterior"),if(historical) "Compra anterior" else "Compra nueva") {historical=it!="Compra nueva"}
  Field(total,"Importe total de la compra (MXN)",{total=it},true)
  OutlinedTextField(months,{months=it},label={Text("Número de mensualidades")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
  if(historical) {
   OutlinedTextField(paid,{paid=it},label={Text("Mensualidades pagadas antes de la app")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
   if((paid.toIntOrNull() ?: 0)>0) Field(previousMoney,"Importe pagado antes de la app (opcional)",{previousMoney=it})
   Row(Modifier.toggleable(value=included,role=Role.Checkbox,onValueChange={included=it}),verticalAlignment=Alignment.CenterVertically) {Checkbox(included,null);Text("Lo restante ya está incluido en la deuda registrada de la tarjeta",Modifier.weight(1f))}
  }
  Select("Categoría de la mensualidad",l.categories.filter {it.kind==Kind.EXPENSE && (!it.archived || it.id==existing?.categoryId)}.map {it.id to it.name},category) {category=it}
  DateField(purchase,if(historical) "Fecha de inicio del seguimiento" else "Fecha de compra",{purchase=it})
  DateField(first,"Fecha de la primera mensualidad del plazo",{first=it})
  Text("Gastos del mes incluye solo la mensualidad que vence en ese mes. Los pagos afectan el efectivo en su fecha real.",style=MaterialTheme.typography.bodySmall)
  if(recorded.isNotEmpty()) Text("Pagos registrados conservados: ${money(recorded.sumOf {it.cents})}. Al editar, se recalcula únicamente lo pendiente.",fontWeight=FontWeight.Medium)
  result.getOrNull()?.let {p->val next=p.paidBefore+recorded.size+1;if(next<=p.months) Text("Próxima: ${money(p.amount(next))} el ${p.due(next)}. Última: ${money(p.amount(p.months))}.") else Text("Compra liquidada")}
  if(total.isNotBlank() && name.isNotBlank()) result.exceptionOrNull()?.message?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
 }
 if(showCard) CreditAccountForm(null,{showCard=false},CreditType.CARD) {a->onAddCard(a);account=a.id;showCard=false}
}

@Composable internal fun InstallmentPaymentForm(p:InstallmentPlan,n:Int,l:Ledger,dismiss:()->Unit,save:(LocalDate)->Unit) {
 var day by remember {mutableStateOf(LocalDate.now().toString())}
 val minimum=maxOf(p.activeFrom,l.payments.filter {it.planId==p.id}.maxOfOrNull {it.date} ?: p.activeFrom)
 val valid=runCatching {val d=LocalDate.parse(day);require(d in minimum..LocalDate.now())}.isSuccess
 FormDialog("Pagar mensualidad $n de ${p.months}",dismiss,valid,confirm="Confirmar pago",onSave={save(LocalDate.parse(day))}) {
  Text(p.title,style=MaterialTheme.typography.titleMedium)
  Text(money(p.amount(n)),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
  Text("Se descontará de tu saldo y de la deuda. No se registrará otro gasto.")
  DateField(day,"Fecha del pago",{day=it})
  Text("Fecha permitida: $minimum a ${LocalDate.now()}. Para deshacer el pago, elimina la última mensualidad desde el historial de la tarjeta.",style=MaterialTheme.typography.bodySmall)
 }
}
