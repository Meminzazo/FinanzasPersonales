package com.finanzaspersonales.presentation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finanzaspersonales.domain.model.*
import com.finanzaspersonales.domain.repository.FinanceRepository
import com.finanzaspersonales.domain.usecase.ConfirmSchedule
import com.finanzaspersonales.domain.repository.BackupRepository
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
@HiltViewModel class FinanceViewModel @Inject constructor(private val repo: FinanceRepository, private val confirmSchedule: ConfirmSchedule, private val backups: BackupRepository): ViewModel() {
 val ledger=repo.ledger.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),Ledger())
 val lastBackup=backups.lastBackup.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),"")
 val busy=MutableStateFlow(false)
 val ready=MutableStateFlow(false)
 private val messages=MutableSharedFlow<String>(extraBufferCapacity=10)
 val message=messages.asSharedFlow()
 private val operations=Mutex()
 private var pending=0
 init { run { repo.seed(); ready.value=true } }
 fun run(success:String?=null, block:suspend ()->Unit) {
  pending++
  busy.value=true
  viewModelScope.launch {
   try { operations.withLock { block() };success?.let { messages.emit(it) } }
   catch(e:CancellationException) { throw e }
   catch(e:Exception) { messages.emit(if(e is javax.crypto.AEADBadTagException) "Contraseña incorrecta o archivo dañado." else e.message ?: "No se pudo completar la operación.") }
   finally { pending--;busy.value=pending>0 }
  }
 }
 fun movement(m:Movement)=run("Movimiento guardado") { repo.movement(m) }
 fun delete(m:Movement)=run("Movimiento eliminado") { repo.deleteMovement(m.id) }
 fun category(c:Category)=run("Categoría guardada") { repo.category(c) }
 fun schedule(s:Schedule)=run("Programación guardada") { repo.schedule(s) }
 fun delete(s:Schedule)=run("Programación eliminada") { repo.deleteSchedule(s.id) }
 fun confirm(s:Schedule,cents:Long,date:LocalDate)=run("Movimiento confirmado") { confirmSchedule(s.id,s.due,cents,date) }
 fun goal(g:Goal)=run("Meta guardada") { repo.goal(g) }
 fun saving(s:Saving)=run("Ahorro actualizado") { repo.saving(s) }
 fun createPlan(p:InstallmentPlan)=run("Compra MSI registrada") {repo.createPlan(p)}
 fun updatePlan(p:InstallmentPlan)=run("Compra MSI actualizada; pagos conservados") {repo.updatePlan(p)}
 fun deletePlan(p:InstallmentPlan)=run("Compra MSI eliminada") {repo.deletePlan(p.id)}
 fun installment(p:InstallmentPlan,n:Int,date:LocalDate)=run("Mensualidad pagada") {repo.confirmInstallment(p.id,n,date)}
 fun account(a:CreditAccount)=run("Tarjeta o deuda guardada") { repo.account(a) }
 fun payment(p:DebtPayment)=run("Pago registrado") { repo.payment(p) }
 fun delete(p:DebtPayment)=run("Pago eliminado") { repo.deletePayment(p.id) }
 fun export(uri:Uri,p:CharArray)=run("Archivo guardado. Si elegiste Drive, comprueba allí que termine de subirse.") { backups.export(uri.toString(),p) }
 fun restore(uri:Uri,p:CharArray)=run("Respaldo restaurado. Se conservó una copia previa protegida con esta contraseña.") { backups.restore(uri.toString(),p) }
 fun safety(uri:Uri)=run("Copia previa exportada") { backups.exportSafety(uri.toString()) }
}
