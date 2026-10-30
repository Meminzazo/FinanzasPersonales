package com.finanzaspersonales.data.backup
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.finanzaspersonales.domain.repository.FinanceRepository
import com.finanzaspersonales.domain.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
private val Context.backupPreferences by preferencesDataStore("backup_status")
class BackupManager @Inject constructor(@ApplicationContext private val context: Context, private val repository: FinanceRepository): BackupRepository {
 override val lastBackup=context.backupPreferences.data.map { it[stringPreferencesKey("last_backup")] ?: "Todavía no has creado un respaldo" }
 override suspend fun export(destination: String, password: CharArray): Unit = withContext(Dispatchers.IO) {
  try {
   val bytes=BackupCodec.encrypt(BackupCodec.encode(repository.snapshot()),password)
   require(bytes.size<=20_000_000) { "El respaldo excede el límite de 20 MB." }
   context.contentResolver.openOutputStream(Uri.parse(destination),"wt")?.use { it.write(bytes);it.flush() } ?: error("No se pudo abrir el destino.")
   context.backupPreferences.edit { it[stringPreferencesKey("last_backup")] = LocalDateTime.now().toString().take(16).replace('T',' ') }
  } finally { password.fill('\u0000') }
 }
 override suspend fun restore(source: String,password: CharArray): Unit = withContext(Dispatchers.IO) {
  try {
   val encrypted=context.contentResolver.openInputStream(Uri.parse(source))?.use { input ->
    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true) {val n=input.read(buffer);if(n<0)break;require(out.size()+n<=20_000_000) { "El archivo es demasiado grande." };out.write(buffer,0,n)}
    out.toByteArray()
   } ?: error("No se pudo leer el archivo.")
   val restored=BackupCodec.decode(BackupCodec.decrypt(encrypted,password))
   val safety=BackupCodec.encrypt(BackupCodec.encode(repository.snapshot()),password)
   val target=File(context.filesDir,"before-restore.fpb");val temp=File(context.filesDir,"before-restore.tmp")
   temp.outputStream().use { it.write(safety);it.fd.sync() };check(temp.renameTo(target)) { "No se pudo guardar la copia de seguridad previa." }
   repository.restore(restored)
  } finally { password.fill('\u0000') }
 }
 override suspend fun exportSafety(destination: String): Unit=withContext(Dispatchers.IO) {
  val file=File(context.filesDir,"before-restore.fpb");require(file.exists()) { "Aún no hay una copia previa a restauración." }
  context.contentResolver.openOutputStream(Uri.parse(destination),"wt")?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("No se pudo abrir el destino.")
 }
}
