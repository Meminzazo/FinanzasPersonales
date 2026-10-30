package com.finanzaspersonales
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.finanzaspersonales.data.local.FinanceDatabase
import com.finanzaspersonales.data.repository.LocalFinanceRepository
import com.finanzaspersonales.data.backup.BackupCodec
import com.finanzaspersonales.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import com.finanzaspersonales.presentation.FinanceViewModel
import com.finanzaspersonales.domain.usecase.ConfirmSchedule
import com.finanzaspersonales.data.backup.BackupManager
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
@RunWith(AndroidJUnit4::class) class PersistenceTest {
 private lateinit var db:FinanceDatabase
 private lateinit var repo:LocalFinanceRepository
 @Before fun setup() {db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),FinanceDatabase::class.java).build();repo=LocalFinanceRepository(db)}
 @After fun close() {db.close()}
 @Test fun cardCutoffSurvivesDatabaseAndBackup()=runBlocking {
  repo.seed()
  val card=CreditAccount(name="Corte",type=CreditType.CARD,cutoffDay=15)
  repo.account(card)
  val stored=repo.snapshot()
  assertEquals(15,stored.accounts.single().cutoffDay)
  assertEquals(stored,BackupCodec.decode(BackupCodec.encode(stored)))
  val old=org.json.JSONObject(BackupCodec.encode(stored).toString(Charsets.UTF_8)).put("version",4)
  old.getJSONArray("accounts").getJSONObject(0).remove("cutoffDay")
  assertNull(BackupCodec.decode(old.toString().toByteArray(Charsets.UTF_8)).accounts.single().cutoffDay)
 }
 @Test fun finiteLoanConfirmsExactlyItsPaymentsAndSurvivesBackup()=runBlocking {
  repo.seed()
  val day=LocalDate.now().minusMonths(2)
  val category=repo.snapshot().categories.first {it.kind==Kind.EXPENSE}
  val loan=Schedule(title="Prestamo",cents=3000,categoryId=category.id,due=day,frequency=Frequency.MONTHLY,paymentsTotal=2,paymentsRemaining=2)
  repo.schedule(loan)
  val stored=repo.snapshot()
  assertEquals(stored,BackupCodec.decode(BackupCodec.encode(stored)))
  repo.confirm(loan.id,day,3000,day)
  val second=repo.snapshot().schedules.single()
  assertEquals(1,second.paymentsRemaining)
  assertEquals(2,second.paymentsTotal)
  repo.confirm(loan.id,second.due,3000,second.due)
  val done=repo.snapshot()
  assertTrue(done.schedules.isEmpty())
  assertEquals(6000L,done.total(Kind.EXPENSE,java.time.YearMonth.from(day))+done.total(Kind.EXPENSE,java.time.YearMonth.from(second.due)))
  assertEquals(2,done.movements.size)
  assertTrue(runCatching {repo.confirm(loan.id,second.due,3000,second.due)}.isFailure)
 }
 @Test fun creditPaymentsSurviveBackupAndRejectDeletingPaidPurchases()=runBlocking {
  repo.seed();val d=LocalDate.now();val c=repo.snapshot().categories.first {it.kind==Kind.EXPENSE}
  val a=CreditAccount(name="BBVA",type=CreditType.CARD,openedOn=d);repo.account(a)
  val charge=Movement(cents=10000,categoryId=c.id,date=d,accountId=a.id);repo.movement(charge)
  val p=DebtPayment(accountId=a.id,cents=4000,date=d);repo.payment(p)
  assertTrue(runCatching {repo.deleteMovement(charge.id)}.isFailure)
  val original=repo.snapshot();assertEquals(6000L,original.pending(a.id));assertEquals(-4000L,original.balance())
  repo.restore(BackupCodec.decode(BackupCodec.decrypt(BackupCodec.encrypt(BackupCodec.encode(original),"password123".toCharArray()),"password123".toCharArray())))
  assertEquals(original,repo.snapshot())
  repo.deletePayment(p.id);assertEquals(10000L,repo.snapshot().pending(a.id));repo.deleteMovement(charge.id)
  assertEquals(0L,repo.snapshot().pending(a.id))
 }
 @Test fun importsVersionOneBackupWithoutCreditFields()=runBlocking {
  repo.seed();val original=repo.snapshot()
  val json=org.json.JSONObject(BackupCodec.encode(original).toString(Charsets.UTF_8)).put("version",1)
  json.remove("accounts");json.remove("payments")
  assertEquals(original,BackupCodec.decode(json.toString().toByteArray(Charsets.UTF_8)))
 }
 @Test fun installmentsAreAtomicFiniteAndIncludedInEncryptedBackup()=runBlocking {
  repo.seed();val d=LocalDate.now();val c=repo.snapshot().categories.first {it.kind==Kind.EXPENSE}
  val a=CreditAccount(name="MSI",type=CreditType.CARD,openedOn=d);repo.account(a)
  val p=InstallmentPlan(title="Laptop",accountId=a.id,totalCents=10001,months=3,firstDue=d,purchaseDate=d,categoryId=c.id)
  repo.createPlan(p);assertEquals(1,repo.snapshot().movements.size)
  assertTrue(runCatching {repo.createPlan(p)}.isFailure)
  repo.confirmInstallment(p.id,1,d)
  assertTrue(runCatching {repo.confirmInstallment(p.id,1,d)}.isFailure)
  assertTrue(runCatching {repo.deletePlan(p.id)}.isFailure)
  val original=repo.snapshot()
  val decoded=BackupCodec.decode(BackupCodec.decrypt(BackupCodec.encrypt(BackupCodec.encode(original),"password123".toCharArray()),"password123".toCharArray()))
  repo.restore(decoded);assertEquals(original,repo.snapshot())
  repo.confirmInstallment(p.id,2,d);repo.confirmInstallment(p.id,3,d)
  val paid=repo.snapshot();assertNull(paid.nextInstallment(p));assertEquals(0L,paid.pending(a.id));assertEquals(1,paid.movements.size)
  val last=paid.payments.first {it.installment==3};repo.deletePayment(last.id)
  assertEquals(3,repo.snapshot().nextInstallment(p));assertEquals(3335L,repo.snapshot().pending(a.id))
 }
 @Test fun importsVersionTwoDebtBackupWithoutInstallmentFields()=runBlocking {
  repo.seed();val a=CreditAccount(name="Anterior",type=CreditType.CARD,openingCents=10000);repo.account(a)
  repo.payment(DebtPayment(accountId=a.id,cents=500,date=LocalDate.now()))
  val original=repo.snapshot();val json=org.json.JSONObject(BackupCodec.encode(original).toString(Charsets.UTF_8)).put("version",2);json.remove("plans")
  val payments=json.getJSONArray("payments");for(i in 0 until payments.length()) {payments.getJSONObject(i).remove("plan");payments.getJSONObject(i).remove("installment")}
  assertEquals(original,BackupCodec.decode(json.toString().toByteArray(Charsets.UTF_8)))
 }
 @Test fun editsPlanAndPaidInstallmentsWithoutLosingActualPayments()=runBlocking {
  repo.seed();val d=LocalDate.now();val c=repo.snapshot().categories.first {it.kind==Kind.EXPENSE}
  val a=CreditAccount(name="Original",type=CreditType.CARD);val other=CreditAccount(name="Corregida",type=CreditType.CARD);repo.account(a);repo.account(other)
  val p=InstallmentPlan(title="Compra",accountId=a.id,totalCents=10001,months=3,firstDue=d,purchaseDate=d,categoryId=c.id)
  repo.createPlan(p);repo.confirmInstallment(p.id,1,d)
  val before=repo.snapshot();val payment=before.payments.single()
  val revised=p.copy(title="Compra editada",accountId=other.id,totalCents=12000,months=4,amounts=InstallmentAmounts.allocate(12000,4,0,0,listOf(payment.cents)))
  repo.updatePlan(revised)
  val changed=repo.snapshot();assertEquals(before.balance(),changed.balance());assertEquals(payment.id,changed.payments.single().id);assertEquals(other.id,changed.payments.single().accountId)
  assertEquals(0L,changed.pending(a.id));assertEquals(8667L,changed.pending(other.id));assertEquals(1,changed.movements.size)
  repo.payment(changed.payments.single().copy(cents=3500))
  val edited=repo.snapshot();assertEquals(3500L,edited.payments.single().cents);assertEquals(3500L,edited.plans.single().amount(1));assertEquals(8500L,edited.pending(other.id))
  assertEquals(edited,BackupCodec.decode(BackupCodec.encode(edited)))
  assertTrue(runCatching {repo.updatePlan(revised.copy(totalCents=100))}.isFailure);assertEquals(edited,repo.snapshot())
 }
 @Test fun editingSavingsUpdatesInsteadOfDuplicatingEntry()=runBlocking {
  val g=Goal(name="Meta",target=10000);repo.goal(g)
  val s=Saving(goalId=g.id,cents=500,date=LocalDate.now());repo.saving(s);repo.saving(s.copy(cents=700))
  assertEquals(1,repo.snapshot().savings.size);assertEquals(700L,repo.snapshot().saved(g.id))
 }
 @Test fun importsVersionThreePlanWithoutExplicitDistribution()=runBlocking {
  repo.seed();val d=LocalDate.now();val c=repo.snapshot().categories.first {it.kind==Kind.EXPENSE};val a=CreditAccount(name="Anterior",type=CreditType.CARD);repo.account(a)
  repo.createPlan(InstallmentPlan(title="Anterior",accountId=a.id,totalCents=10001,months=3,firstDue=d,purchaseDate=d,categoryId=c.id))
  val original=repo.snapshot();val json=org.json.JSONObject(BackupCodec.encode(original).toString(Charsets.UTF_8)).put("version",3)
  val plans=json.getJSONArray("plans");for(i in 0 until plans.length()) plans.getJSONObject(i).remove("amounts")
  assertEquals(original,BackupCodec.decode(json.toString().toByteArray(Charsets.UTF_8)))
 }
 @Test fun viewModelStartsAndSeedsCategories()=runBlocking {
  lateinit var vm:FinanceViewModel
  val store=ViewModelStore()
  InstrumentationRegistry.getInstrumentation().runOnMainSync {
   vm=FinanceViewModel(repo,ConfirmSchedule(repo),BackupManager(ApplicationProvider.getApplicationContext<Context>(),repo))
   store.put("finance",vm)
  }
  try {
   withTimeout(5000) {vm.ready.first {it}}
   assertEquals(9,repo.snapshot().categories.size)
   assertFalse(vm.busy.value)
  } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync {store.clear()}}
 }
 @Test fun confirmsOnlyOnceAndRestoresEncryptedSnapshot()=runBlocking {
  repo.seed();val c=repo.snapshot().categories.first {it.kind==Kind.EXPENSE}
  val s=Schedule(title="Renta",cents=50000,categoryId=c.id,due=LocalDate.parse("2026-01-31"),frequency=Frequency.MONTHLY)
  repo.schedule(s);repo.confirm(s.id,s.due,49000,LocalDate.parse("2026-02-01"))
  assertTrue(runCatching {repo.confirm(s.id,s.due,49000,LocalDate.now())}.isFailure)
  val original=repo.snapshot();assertEquals(1,original.movements.size);assertEquals(LocalDate.parse("2026-02-28"),original.schedules.single().due)
  val bytes=BackupCodec.encrypt(BackupCodec.encode(original),"password123".toCharArray())
  assertTrue(runCatching {BackupCodec.decrypt(bytes,"wrongpass".toCharArray())}.isFailure)
  val damaged=bytes.copyOf();damaged[damaged.lastIndex]=(damaged.last().toInt() xor 1).toByte()
  assertTrue(runCatching {BackupCodec.decrypt(damaged,"password123".toCharArray())}.isFailure)
  val restored=BackupCodec.decode(BackupCodec.decrypt(bytes,"password123".toCharArray()))
  repo.deleteMovement(original.movements.single().id);repo.restore(restored);assertEquals(original,repo.snapshot())
  assertTrue(runCatching {repo.restore(original.copy(movements=listOf(original.movements.single().copy(categoryId="broken"))))}.isFailure)
  assertEquals(original,repo.snapshot())
 }
}
