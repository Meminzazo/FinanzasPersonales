package com.finanzaspersonales.presentation

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.finanzaspersonales.data.update.GitHubUpdate
import com.finanzaspersonales.data.update.GitHubUpdates
import kotlinx.coroutines.launch

@Composable internal fun GitHubUpdatesSection() {
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 var update by remember {mutableStateOf<GitHubUpdate?>(null)}
 var downloaded by remember {mutableStateOf<Uri?>(null)}
 var checked by remember {mutableStateOf(false)}
 var working by remember {mutableStateOf(false)}
 var message by remember {mutableStateOf("")}
 val version=context.packageManager.getPackageInfo(context.packageName,0).versionName ?: "?"
 Card {Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text("Actualizar desde GitHub",style=MaterialTheme.typography.titleLarge)
  Text("Versión instalada: $version")
  Text("Consulta manualmente los Releases de Meminzazo/FinanzasPersonales. Tus datos siguen guardados en el teléfono.",style=MaterialTheme.typography.bodySmall)
  Button(onClick={scope.launch {
   working=true;message="";downloaded=null
   try {update=GitHubUpdates.check(context);checked=true;message=if(update==null) "No hay una versión más reciente publicada." else "Hay una nueva versión disponible."}
   catch(e:Exception) {checked=false;message=e.message ?: "No se pudo consultar GitHub."}
   finally {working=false}
  }},enabled=!working,modifier=Modifier.fillMaxWidth()) {Text("Buscar actualización")}
  if(working) LinearProgressIndicator(Modifier.fillMaxWidth())
  if(checked && update!=null) {
   Text("Nueva versión: ${update!!.version}",style=MaterialTheme.typography.titleMedium)
   if(update!!.notes.isNotBlank()) Text(update!!.notes,Modifier.heightIn(max=180.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)
   Button(onClick={scope.launch {
    working=true;message=if(downloaded==null) "Descargando APK…" else "Abriendo instalador…"
    try {
     val uri=downloaded ?: GitHubUpdates.download(context,update!!).also {downloaded=it}
     message=if(GitHubUpdates.install(context,uri)) "El instalador de Android comprobará la actualización." else "Permite instalar desde esta app en Ajustes y vuelve para pulsar Instalar."
    } catch(e:Exception) {message=e.message ?: "No se pudo instalar la actualización."}
    finally {working=false}
   }},enabled=!working,modifier=Modifier.fillMaxWidth()) {Text(if(downloaded==null) "Descargar e instalar" else "Instalar APK descargado")}
  }
  if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall)
  Text("Android solicitará tu aprobación antes de instalar. Se conservarán tus datos locales.",style=MaterialTheme.typography.bodySmall)
 }}
}
