# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ============================================================
#  proguard-rules.pro
#  Reglas para que el release build no rompa las librerías
# ============================================================

# ── Room ─────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *;
}

# ── SQLCipher ─────────────────────────────────────────────
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# ── Hilt ─────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# ── Firebase ─────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# ── Data classes (entidades Room / modelos de dominio) ────
# Cambia "com.tuapp.finanzaspersonales" por tu package real
-keep class com.tuapp.finanzaspersonales.data.local.entity.** { *; }
-keep class com.tuapp.finanzaspersonales.domain.model.** { *; }

# ── Kotlin Coroutines ─────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Vico charts ───────────────────────────────────────────
-keep class com.patrykandpatrick.vico.** { *; }