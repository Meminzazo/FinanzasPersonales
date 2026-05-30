plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)   // Plugin separado desde Kotlin 2.0
    alias(libs.plugins.ksp)              // KSP reemplaza a KAPT (2x más rápido)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)  // Solo si usas Firebase
}

android {
    namespace = "com.finanzaspersonales"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.finanzaspersonales"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Necesario para SQLCipher: carga librerías nativas
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose    = true
        buildConfig = true   // Necesario para constantes de build
    }

    // KSP — directorio de schemas de Room para migraciones
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental",    "true")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}

dependencies {

    // ── AndroidX Core ────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.splashscreen)

    // ── Compose (BOM controla versiones de todos los artefactos)
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.prev)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)     // íconos financieros (wallet, trending_up, etc.)
    implementation(libs.compose.activity)

    debugImplementation(libs.compose.ui.tooling)    // Solo en debug
    debugImplementation(libs.compose.ui.test.manifest)

    // ── Lifecycle / ViewModel ─────────────────────────────────
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)  // collectAsStateWithLifecycle()

    // ── Navigation ────────────────────────────────────────────
    implementation(libs.navigation.compose)

    // ── Hilt (inyección de dependencias) ──────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)                         // KSP en lugar de kapt
    implementation(libs.hilt.navigation.compose)    // hiltViewModel() en Composables
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // ── Room (base de datos local) ────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)                   // Soporte para Coroutines / Flow
    ksp(libs.room.compiler)                         // Genera implementaciones en compile-time

    // ── SQLCipher (cifrado AES-256 de la DB) ──────────────────
    // IMPORTANTE: android-database-sqlcipher está deprecado.
    // Usar el nuevo sqlcipher-android (soporta páginas de 16KB para Play Store).
    implementation(libs.sqlcipher)
    implementation(libs.sqlite.ktx)                 // Necesario para SupportFactory

    // ── DataStore (reemplaza SharedPreferences) ───────────────
    // Usado para: tema, moneda, preferencias de backup, etc.
    implementation(libs.datastore.preferences)

    // ── Coroutines ────────────────────────────────────────────
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    // ── Firebase (respaldo opcional en la nube) ───────────────
    // BOM mantiene versiones compatibles entre sí automáticamente
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)         // Base de datos en la nube
    implementation(libs.firebase.auth)              // Auth para asociar backup al usuario

    // ── WorkManager (sync de Firestore en background) ─────────
    implementation(libs.workmanager.ktx)

    // ── Vico (gráficas de barras, líneas, pastel para reportes) ─
    implementation(libs.vico.compose.m3)
}