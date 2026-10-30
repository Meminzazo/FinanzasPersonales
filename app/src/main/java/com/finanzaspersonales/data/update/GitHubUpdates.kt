package com.finanzaspersonales.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class GitHubUpdate(val version: String, val notes: String, val url: String, val size: Long, val digest: String?)

object GitHubUpdates {
 private const val repo="Meminzazo/FinanzasPersonales"
 private const val maxApk=120L*1024*1024
 private val versionPattern=Regex("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?$")

 fun newer(remote:String,current:String):Boolean {
  fun parts(value:String)=versionPattern.matchEntire(value)?.groupValues?.drop(1)?.map {it.toIntOrNull() ?: 0}
  val a=parts(remote) ?: return false;val b=parts(current) ?: return false
  return (0..2).firstOrNull {a[it]!=b[it]}?.let {a[it]>b[it]} ?: false
 }

 fun parse(json:String,current:String):GitHubUpdate? {
  val release=JSONObject(json);val version=release.getString("tag_name")
  require(versionPattern.matches(version)) {"La versión publicada no tiene un formato válido (usa v1.1 o v1.1.0)."}
  if(!newer(version,current)) return null
  val assets=release.getJSONArray("assets")
  val apk=(0 until assets.length()).map {assets.getJSONObject(it)}.firstOrNull {
   it.getString("name").endsWith(".apk",true) && !it.getString("name").contains("androidTest",true)
  } ?: error("La versión $version no incluye un archivo APK.")
  val url=apk.getString("browser_download_url")
  require(url.startsWith("https://github.com/$repo/releases/download/")) {"El enlace del APK no pertenece a este repositorio."}
  val size=apk.getLong("size");require(size in 1..maxApk) {"El tamaño del APK no es válido."}
  val digest=apk.optString("digest").takeIf {it.isNotBlank()}
  require(digest==null || Regex("^sha256:[0-9a-fA-F]{64}$").matches(digest)) {"La huella del APK no es válida."}
  return GitHubUpdate(version,release.optString("body").take(3000),url,size,digest?.removePrefix("sha256:"))
 }

 suspend fun check(context:Context):GitHubUpdate?=withContext(Dispatchers.IO) {
  val connection=(URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
   connectTimeout=15000;readTimeout=15000;setRequestProperty("Accept","application/vnd.github+json");setRequestProperty("User-Agent","FinanzasPersonales-Android")
  }
  try {
   if(connection.responseCode==404) return@withContext null
   require(connection.responseCode==200) {"GitHub respondió ${connection.responseCode}. Intenta de nuevo."}
   val json=connection.inputStream.bufferedReader().use {it.readText()}
   val current=context.packageManager.getPackageInfo(context.packageName,0).versionName ?: "0.0"
   parse(json,current)
  } finally {connection.disconnect()}
 }

 suspend fun download(context:Context,update:GitHubUpdate):Uri=withContext(Dispatchers.IO) {
  val directory=File(context.cacheDir,"updates").apply {mkdirs()}
  val file=File(directory,"finanzas-update.apk")
  val partial=File(directory,"finanzas-update-download.apk")
  partial.delete()
  val connection=(URL(update.url).openConnection() as HttpURLConnection).apply {
   connectTimeout=15000;readTimeout=30000;setRequestProperty("User-Agent","FinanzasPersonales-Android")
  }
  try {
   require(connection.responseCode==200 && connection.url.protocol=="https") {"No se pudo descargar el APK desde GitHub."}
   require(connection.url.host=="github.com" || connection.url.host.endsWith(".githubusercontent.com")) {"La descarga salió de GitHub."}
   require(connection.contentLengthLong in -1..maxApk) {"El APK excede el tamaño permitido."}
   val hash=MessageDigest.getInstance("SHA-256");var total=0L
   connection.inputStream.use {input->partial.outputStream().use {output->
    val buffer=ByteArray(64*1024)
    while(true) {val n=input.read(buffer);if(n<0)break;total+=n;require(total<=maxApk) {"El APK excede el tamaño permitido."};hash.update(buffer,0,n);output.write(buffer,0,n)}
   }}
   require(total==update.size) {"La descarga está incompleta."}
   update.digest?.let {expected->require(hash.digest().joinToString("") {"%02x".format(it.toInt() and 0xff)}.equals(expected,true)) {"El APK no coincide con la huella publicada."}}
   val pm=context.packageManager
   val flags=if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
   val archive=pm.getPackageArchiveInfo(partial.absolutePath,flags) ?: error("El archivo descargado no es un APK válido.")
   val installed=pm.getPackageInfo(context.packageName,flags)
   require(archive.packageName==context.packageName) {"El APK pertenece a otra aplicación."}
   fun code(info:android.content.pm.PackageInfo)=if(Build.VERSION.SDK_INT>=28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
   require(code(archive)>code(installed)) {"El APK publicado no tiene un código de versión superior."}
   @Suppress("DEPRECATION") fun signers(info:android.content.pm.PackageInfo):Set<String> = if(Build.VERSION.SDK_INT>=28) info.signingInfo?.apkContentsSigners?.map {it.toCharsString()}?.toSet() ?: emptySet() else info.signatures?.map {it.toCharsString()}?.toSet() ?: emptySet()
   require(signers(archive).isNotEmpty() && signers(archive)==signers(installed)) {"El APK tiene una firma distinta. Publica la actualización con la misma clave de firma."}
   if(file.exists()) file.delete()
   require(partial.renameTo(file)) {"No se pudo preparar el APK descargado."}
   FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
  } catch(e:Exception) {partial.delete();throw e} finally {connection.disconnect()}
 }

 fun install(context:Context,uri:Uri):Boolean {
  if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()) {
   context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")))
   return false
  }
  context.startActivity(Intent(Intent.ACTION_VIEW).apply {setDataAndType(uri,"application/vnd.android.package-archive");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)})
  return true
 }
}
