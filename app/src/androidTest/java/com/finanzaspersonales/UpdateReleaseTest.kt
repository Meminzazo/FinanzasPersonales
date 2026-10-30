package com.finanzaspersonales

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finanzaspersonales.data.update.GitHubUpdates
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class) class UpdateReleaseTest {
 @Test fun downloadedApkCanBeInspectedBeforeInstall() {
  val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
  val apk=java.io.File(context.cacheDir,"candidate.apk")
  try {
   java.io.File(context.applicationInfo.sourceDir).inputStream().use {input->apk.outputStream().use {input.copyTo(it)}}
   val flags=if(android.os.Build.VERSION.SDK_INT>=28) android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES else android.content.pm.PackageManager.GET_SIGNATURES
   val info=context.packageManager.getPackageArchiveInfo(apk.absolutePath,flags)
   assertNotNull(info)
   assertEquals(context.packageName,info!!.packageName)
   if(android.os.Build.VERSION.SDK_INT>=28) assertTrue(info.signingInfo!!.apkContentsSigners.isNotEmpty())
  } finally {apk.delete()}
 }
 @Test fun acceptsOnlyNewerApkFromConfiguredRepository() {
  val json="""{"tag_name":"v1.1","body":"Cambios","assets":[{"name":"Finanzas Personales.apk","size":12345,"browser_download_url":"https://github.com/Meminzazo/FinanzasPersonales/releases/download/v1.1/Finanzas.apk"}]}"""
  val update=GitHubUpdates.parse(json,"1.0")!!
  assertEquals("v1.1",update.version)
  assertEquals(12345L,update.size)
  assertNull(GitHubUpdates.parse(json,"1.1"))
  assertTrue(runCatching {GitHubUpdates.parse(json.replace("github.com/Meminzazo/FinanzasPersonales","example.com/other/repo"),"1.0")}.isFailure)
  assertTrue(runCatching {GitHubUpdates.parse(json.replace("Finanzas Personales.apk","Finanzas.zip"),"1.0")}.isFailure)
 }
}
