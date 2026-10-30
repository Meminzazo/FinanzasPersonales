package com.finanzaspersonales.presentation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finanzaspersonales.domain.model.*
import java.time.LocalDate

internal fun LazyListScope.debtSection(l:Ledger,busy:Boolean,type:CreditType,onAdd:()->Unit,onEdit:(CreditAccount)->Unit,onPay:(CreditAccount)->Unit,onEditPayment:(DebtPayment)->Unit,onDeletePayment:(DebtPayment)->Unit,onArchive:(CreditAccount)->Unit,onShowExpenses:(CreditAccount)->Unit,onAddMsi:(CreditAccount?)->Unit,onPayMsi:(InstallmentPlan,Int)->Unit,onDeleteMsi:(InstallmentPlan)->Unit,onEditMsi:(InstallmentPlan)->Unit) {
 val cards=type==CreditType.CARD
 val accounts=l.accounts.filter {it.type==type}
 item { Heading(if(cards) "Tarjetas de crédito y MSI" else "Otras deudas",if(cards) "1. Registra tu tarjeta. 2. Agrega sus compras MSI. 3. Confirma pagos en Programados." else "Préstamos y otros adeudos, separados de tus tarjetas de crédito.") }
 item { Text("Total pendiente: ${money(accounts.sumOf {l.pending(it.id)})}",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
 item { Button(onClick=onAdd,enabled=!busy,modifier=Modifier.fillMaxWidth()) {Icon(Icons.Default.Add,null);Text(if(cards) "Registrar tarjeta de crédito" else "Registrar otra deuda")} }
 if(cards) item { OutlinedButton(onClick={onAddMsi(null)},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("Agregar compra MSI")} }
 if(accounts.isEmpty()) item {Text(if(cards) "Registra tu tarjeta aunque aún no debas nada: deja el saldo inicial en cero y después agrega la compra MSI." else "Agrega aquí préstamos personales u otros adeudos. Las tarjetas se registran en la sección Tarjetas.")}
 items(accounts,key={"credit-${it.id}"}) {a ->
  var allPayments by rememberSaveable(a.id) {mutableStateOf(false)}
  val payments=l.payments.filter {it.accountId==a.id}.sortedByDescending {it.date}
  Card {Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically) {Icon(if(a.type==CreditType.CARD) Icons.Default.CreditCard else Icons.AutoMirrored.Filled.ReceiptLong,null);Text(a.name,Modifier.weight(1f).padding(start=10.dp),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);IconButton(onClick={onEdit(a)}) {Icon(Icons.Default.Edit,"Editar ${a.name}")}}
   Text("${if(a.type==CreditType.CARD) "Tarjeta de crédito" else "Deuda"}${if(a.archived) " · Archivada" else ""}",style=MaterialTheme.typography.bodySmall)
   Text("Pendiente: ${money(l.pending(a.id))}",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
   Text("Saldo inicial: ${money(a.openingCents)} al ${a.openedOn}",style=MaterialTheme.typography.bodySmall)
   if(cards) Text("Corte: ${a.cutoffDay?.let { "día $it de cada mes" } ?: "sin configurar"}",style=MaterialTheme.typography.bodySmall)
   Button(onClick={onPay(a)},enabled=!busy && l.freeDebt(a.id)>0,modifier=Modifier.fillMaxWidth()) {Text(if(cards) "Registrar otro pago a la tarjeta" else "Registrar pago")}
   Text("Sin MSI: ${money(l.freeDebt(a.id))}. Las mensualidades se pagan desde Programados.",style=MaterialTheme.typography.bodySmall)
   if(cards) TextButton(onClick={onAddMsi(a)},enabled=!busy && !a.archived) {Text("Nueva compra MSI en esta cuenta")}
   Row {TextButton(onClick={onShowExpenses(a)}) {Text("Ver gastos")};TextButton(onClick={onArchive(a)},enabled=!busy) {Text(if(a.archived) "Reactivar" else "Archivar")}}
   if(payments.isEmpty()) Text("Aún no hay pagos registrados.",style=MaterialTheme.typography.bodySmall)
   else {
    Text("Pagos registrados",fontWeight=FontWeight.SemiBold)
    (if(allPayments) payments else payments.take(5)).forEach {p ->
     Row(verticalAlignment=Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {Text(money(p.cents),fontWeight=FontWeight.Medium);Text(p.date.toString(),style=MaterialTheme.typography.bodySmall);if(p.planId!=null) Text("MSI · mensualidad ${p.installment}",style=MaterialTheme.typography.bodySmall)}
      IconButton(onClick={onEditPayment(p)},enabled=!busy) {Icon(Icons.Default.Edit,"Editar pago")}
      IconButton(onClick={onDeletePayment(p)},enabled=!busy && (p.planId==null || p.installment==l.plans.first {it.id==p.planId}.paidBefore+l.payments.count {it.planId==p.planId})) {Icon(Icons.Default.DeleteOutline,"Eliminar pago")}
     }
    }
    if(payments.size>5) TextButton(onClick={allPayments=!allPayments}) {Text(if(allPayments) "Ver menos" else "Ver todos los pagos")}
   }
  }}
 }
 if(cards || l.plans.any {p->accounts.any {it.id==p.accountId}}) item {Text("Compras a MSI",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
 if(cards && l.plans.none {p->accounts.any {it.id==p.accountId}}) item {Text("Agrega una compra a meses para llevar sus mensualidades y fecha de término.")}
 items(l.plans.filter {p->accounts.any {it.id==p.accountId}},key={"plan-${it.id}"}) {p->InstallmentCard(p,l,busy,onPayMsi,onDeleteMsi,onEditMsi)}
}

@Composable internal fun CreditAccountForm(a:CreditAccount?,dismiss:()->Unit,initialType:CreditType=CreditType.CARD,save:(CreditAccount)->Unit) {
 var name by remember {mutableStateOf(a?.name ?: "")}
 val type=a?.type ?: initialType;val cards=type==CreditType.CARD
 var opening by remember {mutableStateOf(a?.openingCents?.let(::decimal) ?: "0")}
 var day by remember {mutableStateOf((a?.openedOn ?: LocalDate.now()).toString())}
 var cutoff by remember {mutableStateOf(a?.cutoffDay?.toString() ?: "")}
 fun amount():Long = if(opening.trim() in listOf("","0","0.00","0,00")) 0 else Rules.cents(opening)
 val valid=runCatching {amount();LocalDate.parse(day);require(name.isNotBlank() && name.length<=80);require(!cards || cutoff.toIntOrNull()?.let {it in 1..31}==true)}.isSuccess
 FormDialog(if(cards) {if(a==null) "Registrar tarjeta de crédito" else "Editar tarjeta de crédito"} else {if(a==null) "Registrar otra deuda" else "Editar deuda"},dismiss,valid,onSave={save(CreditAccount(a?.id ?: newId(),name.trim(),type,amount(),LocalDate.parse(day),a?.archived ?: false,if(cards) cutoff.toInt() else null))}) {
  Field(name,if(cards) "Nombre de tu tarjeta (ej. BBVA Azul)" else "Nombre del préstamo o adeudo",{name=it})
  Text(if(cards) "No necesitas el número de tarjeta ni CVV. Este registro permite asociar compras MSI a tu tarjeta." else "Registra aquí préstamos personales u otros adeudos, no tarjetas de crédito.",style=MaterialTheme.typography.bodySmall)
  OutlinedTextField(opening,{opening=it},label={Text("Lo que ya debías al empezar (MXN)")},singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal))
  Text("Si no debías nada, deja 0. No es el límite de crédito ni dinero disponible.",style=MaterialTheme.typography.bodySmall)
  DateField(day,"Fecha de ese saldo inicial",{day=it})
  if(cards) {
   OutlinedTextField(cutoff,{cutoff=it.filter(Char::isDigit).take(2)},label={Text("Día de corte (1 al 31)")},singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number),isError=cutoff.isNotBlank() && cutoff.toIntOrNull()?.let {it in 1..31}!=true)
   Text("Una compra hecha después del corte aparece en gastos del mes siguiente. La fecha de compra y la deuda no cambian. En MSI se usa la fecha de cada mensualidad.",style=MaterialTheme.typography.bodySmall)
  }
  if(a!=null) Text("Los cambios recalculan la deuda conservando compras y pagos. Debe seguir cubriendo los pagos y MSI registrados.",style=MaterialTheme.typography.bodySmall)
 }
}
