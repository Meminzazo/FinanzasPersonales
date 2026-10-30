package com.finanzaspersonales.presentation

import androidx.compose.ui.res.painterResource
import com.finanzaspersonales.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finanzaspersonales.domain.model.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.text.NumberFormat
import android.net.Uri

private val Ink=Color(0xFF123E50)
private val Expense=Color(0xFFAE3C46)
private val Income=Color(0xFF146952)
internal fun money(cents:Long)=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")).format(java.math.BigDecimal.valueOf(cents,2))
private fun monthName(m:YearMonth)=m.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.forLanguageTag("es-MX"))).replaceFirstChar { it.titlecase() }
private fun date(s:String)=LocalDate.parse(s.trim())
internal fun decimal(cents:Long)=java.math.BigDecimal.valueOf(cents,2).toPlainString()
private fun frequency(f:Frequency)=when(f){Frequency.WEEKLY->"Semanal";Frequency.FIFTEEN_DAYS->"Cada 15 días";Frequency.MONTHLY->"Mensual"}

@Composable fun FinanceScreen(vm:FinanceViewModel) {
 val dark=isSystemInDarkTheme()
 val scheme=if(dark) darkColorScheme(primary=Color(0xFF9DD5E8),onPrimary=Ink,primaryContainer=Color(0xFF284D5C),onPrimaryContainer=Color(0xFFD6EEF5),surfaceContainer=Color(0xFF182A34),secondaryContainer=Color(0xFF285367),onSecondaryContainer=Color(0xFFD7EDF5),surfaceContainerHigh=Color(0xFF203640),surfaceContainerHighest=Color(0xFF263F49),secondary=Color(0xFF80D6BA),background=Color(0xFF101D25),surface=Color(0xFF182A34)) else lightColorScheme(primary=Ink,onPrimary=Color.White,primaryContainer=Color(0xFFD6E9F1),onPrimaryContainer=Ink,surfaceContainer=Color(0xFFE9F0F5),secondaryContainer=Color(0xFFD6E9F1),onSecondaryContainer=Ink,surfaceContainerHigh=Color(0xFFE5EDF3),surfaceContainerHighest=Color(0xFFDCE7EE),secondary=Income,background=Color(0xFFF3F6FA),surface=Color.White)
 MaterialTheme(colorScheme=scheme,typography=Typography(bodyLarge=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontSize=16.sp))) {
  val l by vm.ledger.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle();val ready by vm.ready.collectAsStateWithLifecycle()
  val last by vm.lastBackup.collectAsStateWithLifecycle()
  var moreSection by rememberSaveable { mutableStateOf("Resumen") }
  var accountFilter by rememberSaveable { mutableStateOf("") }
  var initialCreditType by remember {mutableStateOf(CreditType.CARD)}
  var selectedSaving by remember {mutableStateOf<Saving?>(null)}
  var creditAccount by remember { mutableStateOf<CreditAccount?>(null) }
  var debtPayment by remember { mutableStateOf<DebtPayment?>(null) }
  var msiPlan by remember {mutableStateOf<InstallmentPlan?>(null)}
  var msiNumber by remember {mutableIntStateOf(1)}
  var deleteMsi by remember {mutableStateOf<InstallmentPlan?>(null)}
  var deletePayment by remember { mutableStateOf<DebtPayment?>(null) }
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var monthString by rememberSaveable { mutableStateOf(YearMonth.now().toString()) };val month=YearMonth.parse(monthString)
  var dialog by remember { mutableStateOf<String?>(null) }
  var initialKind by remember { mutableStateOf(Kind.EXPENSE) }
  var movement by remember { mutableStateOf<Movement?>(null) };var schedule by remember { mutableStateOf<Schedule?>(null) };var newLoan by remember {mutableStateOf(false)};var goal by remember { mutableStateOf<Goal?>(null) };var category by remember { mutableStateOf<Category?>(null) }
  var deleteMovement by remember { mutableStateOf<Movement?>(null) };var deleteSchedule by remember { mutableStateOf<Schedule?>(null) }
  val editEntry:(Movement)->Unit={m->val p=l.plans.firstOrNull {m.occurrence=="MSI:${it.id}" || m.id==it.movementId};if(p!=null) {msiPlan=p;dialog="msiEdit"} else {movement=l.movements.firstOrNull {it.id==m.id} ?: m;dialog="movement"}}
  val deleteEntry:(Movement)->Unit={m->val p=l.plans.firstOrNull {m.occurrence=="MSI:${it.id}" || m.id==it.movementId};if(p!=null) deleteMsi=p else deleteMovement=m}
  val snackbar=remember { SnackbarHostState() }
  LaunchedEffect(vm) { vm.message.collect { snackbar.showSnackbar(it) } }
  var password by remember { mutableStateOf("") };var backupMode by remember { mutableStateOf("") };var restoreUri by remember { mutableStateOf<Uri?>(null) }
  val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> val p=password.toCharArray();password="";if(uri!=null) vm.export(uri,p) else p.fill('\u0000') }
  val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) restoreUri=uri else password="" }
  val safety=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if(uri!=null) vm.safety(uri) }
  val titles=listOf("Inicio","Movimientos","Programados","Ahorros","Más")
  val icons=listOf(Icons.Default.Home,Icons.AutoMirrored.Filled.List,Icons.Default.Event,Icons.Default.Savings,Icons.Default.MoreHoriz)
  Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={SnackbarHost(snackbar)},bottomBar={
   NavigationBar { titles.forEachIndexed { i,t -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icons[i],t)},label={Text(t,fontSize=10.sp)},alwaysShowLabel=true) } }
  },floatingActionButton={ if(tab==0 || tab==1) ExtendedFloatingActionButton(onClick={initialKind=Kind.EXPENSE;movement=null;dialog="movement"},icon={Icon(Icons.Default.Add,null)},text={Text("Agregar gasto")},containerColor=MaterialTheme.colorScheme.primary) }) { padding ->
   Column(Modifier.padding(padding).fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
     Column(Modifier.weight(1f)) { Text("Mis finanzas",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Tu dinero, a tu ritmo",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
     Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(44.dp)) {
      Image(painterResource(R.drawable.ic_launcher_foreground),contentDescription="Logo",modifier=Modifier.padding(2.dp).fillMaxSize())
     }
    }
    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(!ready) { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("Preparando tus finanzas…") } }
    else when(tab) {
     0 -> LazyColumn(contentPadding=PaddingValues(20.dp,6.dp,20.dp,100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
      item { MonthPicker(month,{monthString=month.minusMonths(1).toString()},{monthString=month.plusMonths(1).toString()}) }
      item {
       Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
         Row(verticalAlignment=Alignment.CenterVertically) { Text("Saldo actual",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Icon(Icons.Default.AccountBalanceWallet,null) }
         Text(money(l.balance()),fontSize=32.sp,fontWeight=FontWeight.Bold,color=if(l.balance()<0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer)
         Text("Ingresos − gastos de contado − pagos de deuda",style=MaterialTheme.typography.bodySmall)
         TextButton(onClick={moreSection="Tarjetas";tab=4},contentPadding=PaddingValues(0.dp)) {Text("Por pagar: ${money(l.totalDebt())}");Icon(Icons.Default.ChevronRight,null)}
        }
       }
      }
      item {
       Surface(color=MaterialTheme.colorScheme.primary,shape=RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
         Text("Gastos del mes",style=MaterialTheme.typography.titleMedium)
         Text(money(l.total(Kind.EXPENSE,month)),fontSize=36.sp,fontWeight=FontWeight.SemiBold)
         HorizontalDivider(color=MaterialTheme.colorScheme.onPrimary.copy(alpha=.25f))
         Text("Hoy: ${money(l.expensesOn(LocalDate.now()))}");Text("Tarjetas: compras después del corte pasan al siguiente mes. MSI: mensualidad del vencimiento",style=MaterialTheme.typography.bodySmall)
        }
       }
      }
      item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
       Stat("Ingresos del mes",money(l.total(Kind.INCOME,month)),Icons.AutoMirrored.Filled.TrendingUp,Modifier.weight(1f))
       Stat("Ahorro acumulado",money(l.savings.sumOf { it.cents }),Icons.Default.Savings,Modifier.weight(1f))
      } }
      item { TextButton(onClick={initialKind=Kind.INCOME;movement=null;dialog="movement"}) { Icon(Icons.Default.Add,null);Text("Registrar ingreso") } }
      item { Text("Pendientes por confirmar",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold) }
      if(l.schedules.isEmpty() && l.plans.none {l.nextInstallment(it)!=null}) item { Empty("Organiza tus próximos pagos y cobros.","Ir a programados") { tab=2 } }
      items(l.schedules.sortedBy { it.due }.take(3),key={"schedule-${it.id}"}) { s -> ScheduleCard(s,l,{schedule=s;dialog="confirm"},{schedule=s;dialog="schedule"},{deleteSchedule=s}) }
      items(l.plans.filter {l.nextInstallment(it)!=null}.take(2),key={"plan-home-${it.id}"}) {p->InstallmentCard(p,l,busy,{plan,n->msiPlan=plan;msiNumber=n;dialog="msiPay"},{deleteMsi=it},{msiPlan=it;dialog="msiEdit"})}
      item { Text("Últimos movimientos",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold) }
      if(l.reportingMovements(month).isEmpty()) item { Empty("Aún no hay movimientos. Registra tu primer gasto.") }
      items(l.reportingMovements(month).take(5),key={it.id}) { m -> MovementCard(m,l,{editEntry(m)},{deleteEntry(m)}) }
     }
     1 -> {
      var selectedKind by rememberSaveable { mutableStateOf("Todos") };var selectedCategory by rememberSaveable { mutableStateOf("") }
      LazyColumn(contentPadding=PaddingValues(20.dp,6.dp,20.dp,100.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
       item { MonthPicker(month,{monthString=month.minusMonths(1).toString()},{monthString=month.plusMonths(1).toString()}) }
       item {Text("Tarjetas: el mes depende del corte; MSI: de la mensualidad. Editar conserva la fecha real de compra.",style=MaterialTheme.typography.bodySmall)}
       item { ChoiceRow(listOf("Todos","Gastos","Ingresos"),selectedKind) {selectedKind=it} }
       item { Select("Categoría",listOf("" to "Todas")+l.categories.map {it.id to it.name},selectedCategory) {selectedCategory=it} }
       item { Select("Forma de pago / deuda",listOf("" to "Todas", "cash" to "Contado: efectivo o débito")+l.accounts.map {it.id to it.name},accountFilter) {accountFilter=it} }
       val filtered=l.reportingMovements(month).filter { (selectedKind=="Todos" || l.kind(it)==if(selectedKind=="Gastos") Kind.EXPENSE else Kind.INCOME) && (selectedCategory.isEmpty() || it.categoryId==selectedCategory) && (accountFilter.isEmpty() || if(accountFilter=="cash") it.accountId==null else it.accountId==accountFilter) }.sortedByDescending {it.date}
       if(filtered.isEmpty()) item { Empty("No hay movimientos con estos filtros.") }
       items(filtered,key={it.id}) {m -> MovementCard(m,l,{editEntry(m)},{deleteEntry(m)})}
      }
     }
     2 -> LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
      item { Heading("Pagos e ingresos programados","Gastos fijos, sueldos y mensualidades MSI.") }
      item { Button(onClick={schedule=null;newLoan=false;dialog="schedule"},modifier=Modifier.fillMaxWidth()) {Icon(Icons.Default.Add,null);Text("Nueva programación")} }
      item {OutlinedButton(onClick={schedule=null;newLoan=true;dialog="schedule"},modifier=Modifier.fillMaxWidth()) {Text("Agregar préstamo con cuotas")}}
      item {OutlinedButton(onClick={creditAccount=null;dialog="msi"},modifier=Modifier.fillMaxWidth()) {Text("Agregar compra MSI")}}
      if(l.schedules.isEmpty() && l.plans.none {l.nextInstallment(it)!=null}) item {Empty("Agrega tu renta, servicios o sueldo. Las fechas previstas no afectan tus totales.")}
      items(l.schedules.sortedBy {it.due},key={it.id}) {s -> ScheduleCard(s,l,{schedule=s;dialog="confirm"},{schedule=s;dialog="schedule"},{deleteSchedule=s})}
      items(l.plans.filter {l.nextInstallment(it)!=null}.sortedBy {it.due(l.nextInstallment(it)!!)},key={"plan-program-${it.id}"}) {p->InstallmentCard(p,l,busy,{plan,n->msiPlan=plan;msiNumber=n;dialog="msiPay"},{deleteMsi=it},{msiPlan=it;dialog="msiEdit"})}
     }
     3 -> LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
      item {Heading("Tus ahorros","Cada aportación te acerca a tu meta.")}
      item {Button(onClick={goal=null;dialog="goal"},modifier=Modifier.fillMaxWidth()) {Icon(Icons.Default.Add,null);Text("Crear meta")} }
      if(l.goals.isEmpty()) item {Empty("Define para qué quieres ahorrar y cuánto necesitas.")}
      items(l.goals,key={it.id}) { g ->
       Card {Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {Text(g.name,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);IconButton(onClick={goal=g;dialog="goal"}) {Icon(Icons.Default.Edit,"Editar meta")}}
        Text("${money(l.saved(g.id))} de ${money(g.target)}",fontWeight=FontWeight.Bold)
        LinearProgressIndicator(progress={(l.saved(g.id).toFloat()/g.target).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
        Text("Faltan ${money((g.target-l.saved(g.id)).coerceAtLeast(0))}${g.deadline?.let { " · Meta: $it" } ?: ""}",style=MaterialTheme.typography.bodySmall)
        Row {TextButton(onClick={goal=g;dialog="deposit"}) {Text("Aportar")};TextButton(onClick={goal=g;dialog="withdraw"}) {Text("Retirar")}}
        var showAll by rememberSaveable(g.id) {mutableStateOf(false)}
        val entries=l.savings.filter {it.goalId==g.id}.sortedByDescending {it.date}
        (if(showAll) entries else entries.take(5)).forEach {s->Row(verticalAlignment=Alignment.CenterVertically) {Text("${s.date}  ${if(s.cents>0) "+" else ""}${money(s.cents)}",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);IconButton(onClick={selectedSaving=s;dialog="editSaving"}) {Icon(Icons.Default.Edit,"Editar movimiento de ahorro")}}}
        if(entries.size>5) TextButton(onClick={showAll=!showAll}) {Text(if(showAll) "Ver menos" else "Ver todos los movimientos de ahorro")}
       }}
      }
     }
      else -> {

       LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Heading("Resumen y ajustes","Consulta tu mes o administra tus datos.") }
        item { ChoiceRow(listOf("Resumen","Tarjetas","Deudas","Categorías","Respaldos","Actualizaciones"),moreSection) { moreSection=it } }
        if(moreSection=="Resumen") {
      item {MonthPicker(month,{monthString=month.minusMonths(1).toString()},{monthString=month.plusMonths(1).toString()})}
      item {OutlinedButton(onClick={moreSection="Actualizaciones"},modifier=Modifier.fillMaxWidth()) {Text("Actualizaciones de la app")}}
      item {Card {Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
       Text("Así va tu mes",style=MaterialTheme.typography.titleLarge)
       Text("Ingresos: ${money(l.total(Kind.INCOME,month))}")
       Text("Gastos: ${money(l.total(Kind.EXPENSE,month))}")
       Text("Diferencia: ${money(l.total(Kind.INCOME,month)-l.total(Kind.EXPENSE,month))}",fontWeight=FontWeight.Bold)
       Text("La diferencia no representa tu saldo bancario.",style=MaterialTheme.typography.bodySmall)
       Text("Gasto del mes anterior: ${money(l.total(Kind.EXPENSE,month.minusMonths(1)))}",style=MaterialTheme.typography.bodySmall)
       val totals=l.reportingMovements(month).filter {l.kind(it)==Kind.EXPENSE}.groupBy {it.categoryId}.mapValues {it.value.sumOf {m->m.cents}}.toList().sortedByDescending {it.second}
       if(totals.isEmpty()) Text("Sin gastos para este periodo.")
       totals.forEach {(id,total)->Text("${l.categories.first {it.id==id}.name}: ${money(total)}");LinearProgressIndicator(progress={total.toFloat()/l.total(Kind.EXPENSE,month).coerceAtLeast(1)},modifier=Modifier.fillMaxWidth())}
      }}}
        }
        if(moreSection=="Tarjetas" || moreSection=="Deudas") {
         debtSection(l,busy,if(moreSection=="Tarjetas") CreditType.CARD else CreditType.DEBT,
          onAdd={initialCreditType=if(moreSection=="Tarjetas") CreditType.CARD else CreditType.DEBT;creditAccount=null;dialog="account"},
          onEdit={creditAccount=it;dialog="account"},
          onPay={creditAccount=it;debtPayment=null;dialog="payment"},
          onEditPayment={p->creditAccount=l.accounts.first {it.id==p.accountId};debtPayment=p;dialog="payment"},
          onDeletePayment={deletePayment=it},
          onArchive={vm.account(it.copy(archived=!it.archived))},
          onShowExpenses={accountFilter=it.id;tab=1},onAddMsi={creditAccount=it;dialog="msi"},onPayMsi={p,n->msiPlan=p;msiNumber=n;dialog="msiPay"},onDeleteMsi={deleteMsi=it},onEditMsi={msiPlan=it;dialog="msiEdit"})
        }
        if(moreSection=="Categorías") {
      item {Heading("Categorías","Archiva las que ya no uses sin perder su historial.")}
      item {OutlinedButton(onClick={category=null;dialog="category"}) {Text("Crear categoría")}}
      items(l.categories,key={it.id}) { c -> Row(verticalAlignment=Alignment.CenterVertically) {
       Column(Modifier.weight(1f)) {Text(c.name,fontWeight=FontWeight.Medium);Text("${if(c.kind==Kind.EXPENSE) "Gasto" else "Ingreso"}${if(c.archived) " · Archivada" else ""}",style=MaterialTheme.typography.bodySmall)}
       IconButton(onClick={category=c;dialog="category"}) {Icon(Icons.Default.Edit,"Editar ${c.name}")}
       IconButton(onClick={vm.category(c.copy(archived=!c.archived))},enabled=!busy) {Icon(if(c.archived) Icons.Default.Unarchive else Icons.Default.Archive,if(c.archived) "Reactivar categoría" else "Archivar categoría")}
      }}
        }
        if(moreSection=="Actualizaciones") item { GitHubUpdatesSection() }
        if(moreSection=="Respaldos") {
      item {Card {Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
       Text("Respaldos protegidos",style=MaterialTheme.typography.titleLarge)
       Text("Guarda una copia manual en tu teléfono o elige Google Drive en el selector de archivos, si está disponible. No hay sincronización automática.")
       Text("Último archivo guardado: $last",style=MaterialTheme.typography.bodySmall)
       Button(onClick={backupMode="export";dialog="password"},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("Crear respaldo")}
       OutlinedButton(onClick={backupMode="restore";dialog="password"},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("Restaurar respaldo")}
       TextButton(onClick={safety.launch("finanzas-antes-de-restaurar.fpb")},enabled=!busy) {Text("Exportar copia previa a restauración")}
       Text("Necesitarás la contraseña para recuperar cada copia. No podemos restablecerla. Moneda: MXN.",style=MaterialTheme.typography.bodySmall)
      }}}
        }
       }
      }
    }
   }
  }
  if(dialog=="msi" || dialog=="msiEdit") InstallmentForm(l,creditAccount?.id,{dialog=null},existing=if(dialog=="msiEdit") msiPlan else null,onAddCard={vm.account(it)}) {if(dialog=="msiEdit") vm.updatePlan(it) else vm.createPlan(it);dialog=null}
  if(dialog=="msiPay" && msiPlan!=null) InstallmentPaymentForm(msiPlan!!,msiNumber,l,{dialog=null}) {vm.installment(msiPlan!!,msiNumber,it);dialog=null}
  deleteMsi?.let {p->ConfirmDelete("¿Eliminar esta compra MSI?",if(p.historical) "Se eliminará el plan. Si estaba incluido en la deuda registrada, esa deuda se conservará." else "También se eliminará el gasto asociado a esta compra.",{deleteMsi=null}) {vm.deletePlan(p);deleteMsi=null}}
  if(dialog=="account") CreditAccountForm(creditAccount,{dialog=null},initialCreditType) {vm.account(it);dialog=null}
  if(dialog=="payment" && creditAccount!=null) AmountForm(if(debtPayment==null) "Pagar a ${creditAccount!!.name}" else "Editar pago",debtPayment?.cents,"Reduce la deuda y tu saldo actual; no vuelve a contarse como gasto.",{dialog=null},max=(debtPayment?.planId?.let {id->l.planRemaining(l.plans.first {it.id==id})} ?: l.freeDebt(creditAccount!!.id))+(debtPayment?.cents ?: 0),maxLabel="Pendiente por pagar",initialDate=debtPayment?.date ?: LocalDate.now()) {c,d->vm.payment(debtPayment?.copy(cents=c,date=d) ?: DebtPayment(accountId=creditAccount!!.id,cents=c,date=d));dialog=null}
  deletePayment?.let {p->ConfirmDelete("¿Eliminar este pago?","El importe volverá a tu saldo actual y aumentará la deuda pendiente.",{deletePayment=null}) {vm.delete(p);deletePayment=null}}
  if(dialog=="movement") MovementForm(l,movement,initialKind,{dialog=null}) {vm.movement(it);dialog=null}
  if(dialog=="schedule") ScheduleForm(l,schedule,newLoan,{dialog=null}) {vm.schedule(it);dialog=null}
  if(dialog=="confirm" && schedule!=null) AmountForm("Confirmar ${schedule!!.title}",schedule!!.cents,"Se registrará un movimiento real.",{dialog=null}) {c,d ->vm.confirm(schedule!!,c,d);dialog=null}
  if(dialog=="editSaving" && selectedSaving!=null) {val old=selectedSaving!!;AmountForm(if(old.cents>0) "Editar aportación" else "Editar retiro",kotlin.math.abs(old.cents),"Corrige el importe o la fecha conservando la meta.",{dialog=null},max=if(old.cents<0) l.saved(old.goalId)-old.cents else null,initialDate=old.date) {c,d->vm.saving(old.copy(cents=if(old.cents<0) -c else c,date=d));dialog=null}}
  if(dialog=="goal") GoalForm(goal,{dialog=null}) {vm.goal(it);dialog=null}
  if((dialog=="deposit" || dialog=="withdraw") && goal!=null) {
   val withdraw=dialog=="withdraw"
   AmountForm(if(withdraw) "Retirar ahorro" else "Aportar a ${goal!!.name}",null,"No se contabiliza como gasto ni ingreso.",{dialog=null},max=if(withdraw) l.saved(goal!!.id) else null) {c,d ->vm.saving(Saving(goalId=goal!!.id,cents=if(withdraw)-c else c,date=d));dialog=null}
  }
  if(dialog=="category") CategoryForm(l,category,{dialog=null}) {vm.category(it);dialog=null}
  if(dialog=="password") {
   var p by remember {mutableStateOf("")};var repeat by remember {mutableStateOf("")}
   FormDialog(if(backupMode=="export") "Proteger respaldo" else "Abrir respaldo",{dialog=null},enabled=p.length>=8 && (backupMode!="export" || p==repeat),confirm="Continuar",onSave={
    password=p;dialog=null
    if(backupMode=="export") create.launch("finanzas-${LocalDate.now()}.fpb") else open.launch(arrayOf("*/*"))
   }) {
    Text("Usa al menos 8 caracteres. Guarda la contraseña: se requiere para restaurar.")
    OutlinedTextField(p,{p=it},label={Text("Contraseña")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
    if(repeat.isNotEmpty() && repeat!=p) Text("Las contraseñas no coinciden.",color=MaterialTheme.colorScheme.error)
    if(backupMode=="export") OutlinedTextField(repeat,{repeat=it},label={Text("Repetir contraseña")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
   }
  }
  if(restoreUri!=null) AlertDialog(onDismissRequest={restoreUri=null;password=""},title={Text("¿Reemplazar tus datos?")},text={Text("Se reemplazarán todos los datos locales por los del archivo. Primero guardaremos una copia del estado actual, protegida con la contraseña que acabas de introducir.")},confirmButton={TextButton(onClick={val uri=restoreUri!!;restoreUri=null;val p=password.toCharArray();password="";vm.restore(uri,p)}) {Text("Restaurar")}},dismissButton={TextButton(onClick={restoreUri=null;password=""}) {Text("Cancelar")}})
  deleteMovement?.let { m -> ConfirmDelete("¿Eliminar este movimiento?","Los totales se actualizarán. Si vino de una programación, su próxima fecha no cambiará.",{deleteMovement=null}) {vm.delete(m);deleteMovement=null} }
  deleteSchedule?.let { s -> ConfirmDelete("¿Eliminar esta programación?","Los pagos o cobros ya registrados se conservarán.",{deleteSchedule=null}) {vm.delete(s);deleteSchedule=null} }
 }
}

@Composable internal fun Heading(title:String,subtitle:String) {Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun Stat(label:String,value:String,icon:ImageVector,modifier:Modifier) {Surface(modifier,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Text(label,style=MaterialTheme.typography.bodySmall);Text(value,fontWeight=FontWeight.Bold,fontSize=18.sp)}}}
@Composable private fun Empty(text:String,action:String?=null,onClick:()->Unit={}) {Column(Modifier.fillMaxWidth().padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(text,color=MaterialTheme.colorScheme.onSurfaceVariant);if(action!=null) TextButton(onClick=onClick) {Text(action)}}}
@Composable private fun MonthPicker(month:YearMonth,previous:()->Unit,next:()->Unit) {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {IconButton(onClick=previous) {Icon(Icons.Default.ChevronLeft,"Mes anterior")};Text(monthName(month),Modifier.weight(1f),fontWeight=FontWeight.SemiBold);IconButton(onClick=next) {Icon(Icons.Default.ChevronRight,"Mes siguiente")}}}
@Composable private fun MovementCard(m:Movement,l:Ledger,edit:()->Unit,delete:()->Unit) {
 val c=l.categories.firstOrNull {it.id==m.categoryId} ?: return
 val color=if(isSystemInDarkTheme()) {if(c.kind==Kind.EXPENSE) Color(0xFFFFB2B8) else Color(0xFF80D6BA)} else if(c.kind==Kind.EXPENSE) Expense else Income
 Surface(shape=RoundedCornerShape(14.dp)) {Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
  Icon(if(c.kind==Kind.EXPENSE) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,null,tint=color,modifier=Modifier.size(22.dp))
  Column(Modifier.weight(1f).padding(horizontal=10.dp)) {Text(c.name,fontWeight=FontWeight.Medium);Text(l.movements.firstOrNull {it.id==m.id && it.date!=m.date}?.let {"Compra: ${it.date} · Mes del corte: ${YearMonth.from(m.date)}"} ?: m.date.toString(),style=MaterialTheme.typography.bodySmall);if(m.note.isNotBlank())Text(m.note,style=MaterialTheme.typography.bodySmall);m.accountId?.let {id->Text(l.accounts.first {it.id==id}.name,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}}
  Column(horizontalAlignment=Alignment.End) {Text("${if(c.kind==Kind.EXPENSE) "−" else "+"}${money(m.cents)}",fontWeight=FontWeight.Bold,color=color);Row {IconButton(onClick=edit,modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Edit,"Editar movimiento",Modifier.size(18.dp))};IconButton(onClick=delete,modifier=Modifier.size(48.dp)) {Icon(Icons.Default.DeleteOutline,"Eliminar movimiento",Modifier.size(18.dp))}}}
 }}
}
@Composable private fun ScheduleCard(s:Schedule,l:Ledger,confirm:()->Unit,edit:()->Unit,delete:()->Unit) {Card {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
 val income=l.categories.firstOrNull {it.id==s.categoryId}?.kind==Kind.INCOME
 Row {Text(s.title,Modifier.weight(1f),fontWeight=FontWeight.Bold);Text(money(s.cents),fontWeight=FontWeight.Bold)}
 Text("${if(income) "Cobro" else "Pago"}: ${s.due} · ${frequency(s.frequency)}",style=MaterialTheme.typography.bodySmall)
 s.paymentsRemaining?.let {Text("Préstamo · cuota ${s.paymentsTotal!!-it+1} de ${s.paymentsTotal} · $it restantes",style=MaterialTheme.typography.bodySmall)}
 if(s.due<LocalDate.now()) Text("Fecha pendiente de confirmar",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
 Row(verticalAlignment=Alignment.CenterVertically) {Button(onClick=confirm,modifier=Modifier.weight(1f)) {Text(if(income) "Confirmar cobro" else "Confirmar pago")};IconButton(onClick=edit) {Icon(Icons.Default.Edit,"Editar programación")};IconButton(onClick=delete) {Icon(Icons.Default.DeleteOutline,"Eliminar programación")}}
 }}}
@Composable private fun ConfirmDelete(title:String,body:String,dismiss:()->Unit,confirm:()->Unit) {AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={Text(body)},confirmButton={TextButton(onClick=confirm) {Text("Eliminar")}},dismissButton={TextButton(onClick=dismiss) {Text("Cancelar")}})}
@Composable internal fun ChoiceRow(options:List<String>,selected:String,onSelect:(String)->Unit) {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {options.forEach {FilterChip(selected=it==selected,onClick={onSelect(it)},label={Text(it)})}}}
@Composable internal fun Select(label:String,options:List<Pair<String,String>>,selected:String,onSelect:(String)->Unit) {
 var expanded by remember {mutableStateOf(false)}
 Column {Text(label,style=MaterialTheme.typography.labelMedium);Box {OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {Text(options.firstOrNull {it.first==selected}?.second ?: "Seleccionar",Modifier.weight(1f));Icon(Icons.Default.ExpandMore,null)};DropdownMenu(expanded,{expanded=false}) {options.forEach {(id,name)->DropdownMenuItem(text={Text(name)},onClick={onSelect(id);expanded=false})}}}}
}
@Composable internal fun FormDialog(title:String,dismiss:()->Unit,enabled:Boolean=true,confirm:String="Guardar",onSave:()->Unit,content:@Composable ColumnScope.()->Unit) {
 AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)},confirmButton={TextButton(onClick=onSave,enabled=enabled) {Text(confirm)}},dismissButton={TextButton(onClick=dismiss) {Text("Cancelar")}})
}
@Composable internal fun Field(value:String,label:String,onChange:(String)->Unit,numeric:Boolean=false) {
 val error=if(numeric && value.isNotBlank()) runCatching { Rules.cents(value) }.exceptionOrNull()?.message else null
 Column { OutlinedTextField(value,onChange,label={Text(label)},isError=error!=null,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text),modifier=Modifier.fillMaxWidth())
  if(error!=null) Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=4.dp))
 }
}
@Composable private fun MovementForm(l:Ledger,m:Movement?,initialKind:Kind,dismiss:()->Unit,save:(Movement)->Unit) {
 var kind by remember {mutableStateOf(m?.let {l.kind(it)} ?: initialKind)}
 var amount by remember {mutableStateOf(m?.cents?.let(::decimal) ?: "")};var cat by remember {mutableStateOf(m?.categoryId ?: l.categories.firstOrNull {it.kind==kind && !it.archived}?.id ?: "")}
 var day by remember {mutableStateOf((m?.date ?: LocalDate.now()).toString())};var note by remember {mutableStateOf(m?.note ?: "")}
 var credit by remember {mutableStateOf(m?.accountId ?: "")}
 val valid=runCatching {Rules.cents(amount);date(day);require(cat.isNotBlank());require(note.length<=500)}.isSuccess
 FormDialog(if(m==null) "Nuevo movimiento" else "Editar movimiento",dismiss,valid,onSave={save(Movement(m?.id ?: newId(),Rules.cents(amount),cat,date(day),note,m?.occurrence,if(kind==Kind.EXPENSE) credit.takeIf {it.isNotEmpty()} else null))}) {
  ChoiceRow(listOf("Gasto","Ingreso"),if(kind==Kind.EXPENSE) "Gasto" else "Ingreso") {kind=if(it=="Gasto")Kind.EXPENSE else Kind.INCOME;cat=l.categories.firstOrNull {it.kind==kind && !it.archived}?.id ?: ""}
  Field(amount,"Monto (MXN)",{amount=it},true)
  Select("Categoría",l.categories.filter {it.kind==kind && (!it.archived || it.id==m?.categoryId)}.map {it.id to it.name},cat) {cat=it}
  if(kind==Kind.EXPENSE) Select("Forma de pago",listOf("" to "Contado: efectivo o débito")+l.accounts.filter {!it.archived || it.id==m?.accountId}.map {it.id to it.name},credit) {credit=it}
  DateField(day,"Fecha",{day=it});Field(note,"Nota opcional",{note=it})
  Text("Usa hasta 2 decimales y una fecha válida.",style=MaterialTheme.typography.bodySmall)
 }
}
@Composable private fun ScheduleForm(l:Ledger,s:Schedule?,newLoan:Boolean,dismiss:()->Unit,save:(Schedule)->Unit) {
 var title by remember {mutableStateOf(s?.title ?: "")};var amount by remember {mutableStateOf(s?.cents?.let(::decimal) ?: "")}
 var kind by remember {mutableStateOf(l.categories.firstOrNull {it.id==s?.categoryId}?.kind ?: Kind.EXPENSE)}
 var cat by remember {mutableStateOf(s?.categoryId ?: (if(newLoan) l.categories.firstOrNull {it.kind==kind && !it.archived && (it.name.contains("Préstamo",true) || it.name=="Otros gastos")}?.id else null) ?: l.categories.firstOrNull {it.kind==kind && !it.archived}?.id ?: "")}
 var day by remember {mutableStateOf((s?.due ?: LocalDate.now()).toString())};var freq by remember {mutableStateOf(s?.frequency ?: Frequency.MONTHLY)}
 var credit by remember {mutableStateOf(s?.accountId ?: "")}
 val loan=s?.paymentsTotal!=null || (s==null && newLoan)
 var remaining by remember(s?.id,newLoan) {mutableStateOf(s?.paymentsRemaining?.toString() ?: "")}
 val valid=runCatching {Rules.cents(amount);date(day);require(title.isNotBlank() && title.length<=80 && cat.isNotBlank());require(!loan || remaining.toIntOrNull()?.let {it in 1..600}==true)}.isSuccess
 FormDialog(if(loan) {if(s==null) "Nuevo préstamo con cuotas" else "Editar préstamo"} else {if(s==null) "Nueva programación" else "Editar programación"},dismiss,valid,onSave={val due=date(day);val left=if(loan) remaining.toInt() else null;val paid=if(loan && s!=null) s.paymentsTotal!!-s.paymentsRemaining!! else 0;save(Schedule(s?.id ?: newId(),title.trim(),Rules.cents(amount),cat,due,freq,if(s?.due==due) s.anchorDay else due.dayOfMonth,if(kind==Kind.EXPENSE) credit.takeIf {it.isNotEmpty()} else null,if(loan) paid+left!! else null,left))}) {
  if(s==null && !loan) ChoiceRow(listOf("Gasto fijo","Sueldo / ingreso"),if(kind==Kind.EXPENSE) "Gasto fijo" else "Sueldo / ingreso") {kind=if(it=="Gasto fijo")Kind.EXPENSE else Kind.INCOME;cat=l.categories.firstOrNull {it.kind==kind && !it.archived}?.id ?: ""}
  Field(title,if(loan) "Nombre del préstamo" else "Concepto",{title=it});Field(amount,if(loan) "Importe de cada cuota (MXN)" else "Monto previsto (MXN)",{amount=it},true)
  Select("Categoría",l.categories.filter {it.kind==kind && (!it.archived || it.id==s?.categoryId)}.map {it.id to it.name},cat) {cat=it}
  if(kind==Kind.EXPENSE) Select(if(loan) "Cómo pagas cada cuota" else "Forma de pago",listOf("" to "Contado: efectivo o débito")+l.accounts.filter {(!it.archived || it.id==s?.accountId) && (!loan || it.type==CreditType.CARD)}.map {it.id to it.name},credit) {credit=it}
  DateField(day,if(loan) "Fecha de la próxima cuota" else "Próxima fecha",{day=it})
  if(loan) {OutlinedTextField(remaining,{remaining=it.filter(Char::isDigit).take(3)},label={Text(if(s==null) "Número de cuotas" else "Cuotas restantes")},singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));Text("Cada cuota se registra como gasto solo al confirmar. Tras la última, el préstamo sale de Programados.",style=MaterialTheme.typography.bodySmall)}
  Select("Frecuencia",Frequency.entries.map {it.name to frequency(it)},freq.name) {freq=Frequency.valueOf(it)}
  Text("Cada 15 días significa días transcurridos, no dos fechas fijas al mes.",style=MaterialTheme.typography.bodySmall)
 }
}
@Composable private fun GoalForm(g:Goal?,dismiss:()->Unit,save:(Goal)->Unit) {
 var name by remember {mutableStateOf(g?.name ?: "")};var amount by remember {mutableStateOf(g?.target?.let(::decimal) ?: "")};var day by remember {mutableStateOf(g?.deadline?.toString() ?: "")}
 val valid=runCatching {Rules.cents(amount);if(day.isNotBlank())date(day);require(name.isNotBlank() && name.length<=80)}.isSuccess
 FormDialog(if(g==null) "Nueva meta" else "Editar meta",dismiss,valid,onSave={save(Goal(g?.id ?: newId(),name.trim(),Rules.cents(amount),day.takeIf {it.isNotBlank()}?.let(::date)))}) {
  Field(name,"Nombre",{name=it});Field(amount,"Objetivo (MXN)",{amount=it},true);DateField(day,"Fecha objetivo opcional",{day=it})
 }
}
@Composable internal fun AmountForm(title:String,initial:Long?,hint:String,dismiss:()->Unit,max:Long?=null,maxLabel:String="Disponible",initialDate:LocalDate=LocalDate.now(),save:(Long,LocalDate)->Unit) {
 var amount by remember {mutableStateOf(initial?.let(::decimal) ?: "")};var day by remember {mutableStateOf(initialDate.toString())}
 val valid=runCatching {val c=Rules.cents(amount);date(day);require(max==null || c<=max)}.isSuccess
 FormDialog(title,dismiss,valid,onSave={save(Rules.cents(amount),date(day))}) {Text(hint);if(max!=null)Text("$maxLabel: ${money(max)}");Field(amount,"Monto real (MXN)",{amount=it},true);DateField(day,"Fecha real",{day=it})}
}
@Composable private fun CategoryForm(l:Ledger,c:Category?,dismiss:()->Unit,save:(Category)->Unit) {
 var name by remember {mutableStateOf(c?.name ?: "")};var kind by remember {mutableStateOf(c?.kind ?: Kind.EXPENSE)}
 val valid=name.isNotBlank() && name.length<=80 && l.categories.none {it.id!=c?.id && it.kind==kind && it.name.equals(name.trim(),ignoreCase=true)}
 FormDialog(if(c==null) "Nueva categoría" else "Editar categoría",dismiss,valid,onSave={save(Category(c?.id ?: newId(),name.trim(),kind,c?.archived ?: false))}) {
  Field(name,"Nombre",{name=it})
  if(name.isNotBlank() && !valid) Text("Usa un nombre único de hasta 80 caracteres.",color=MaterialTheme.colorScheme.error)
  if(c==null) ChoiceRow(listOf("Gasto","Ingreso"),if(kind==Kind.EXPENSE) "Gasto" else "Ingreso") {kind=if(it=="Gasto")Kind.EXPENSE else Kind.INCOME}
  Text("El tipo se conserva al editar para proteger el historial.",style=MaterialTheme.typography.bodySmall)
 }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun DateField(value:String,label:String,onChange:(String)->Unit) {
 var open by remember { mutableStateOf(false) }
 val selected=value.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
 val display=selected?.format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.forLanguageTag("es-MX"))) ?: ""
 Column {
  OutlinedTextField(value=display,onValueChange={},label={Text(label)},readOnly=true,modifier=Modifier.fillMaxWidth(),trailingIcon={IconButton(onClick={open=true}) {Icon(Icons.Default.CalendarMonth,"Elegir $label")}})
  if(label.contains("opcional") && value.isNotEmpty()) TextButton(onClick={onChange("")}) {Text("Quitar fecha")}
 }
 if(open) {
  val state=rememberDatePickerState(initialSelectedDateMillis=(selected ?: LocalDate.now()).toEpochDay()*86_400_000)
  DatePickerDialog(onDismissRequest={open=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let {onChange(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())};open=false},enabled=state.selectedDateMillis!=null) {Text("Elegir fecha")}},dismissButton={TextButton(onClick={open=false}) {Text("Cancelar")}}) {DatePicker(state=state)}
 }
}
