plugins {
 alias(libs.plugins.android.application)
 alias(libs.plugins.kotlin.android)
 alias(libs.plugins.kotlin.compose)
 alias(libs.plugins.ksp)
 alias(libs.plugins.hilt)
}
android {
 namespace = "com.finanzaspersonales"
 compileSdk = 35
 defaultConfig { applicationId = "com.finanzaspersonales"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.1"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
 packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
 implementation(libs.androidx.core.ktx)
 implementation(libs.androidx.appcompat)
 implementation(libs.material)
 implementation(platform(libs.compose.bom))
 implementation(libs.compose.ui)
 implementation(libs.compose.material3)
 implementation(libs.compose.material.icons)
 implementation(libs.compose.activity)
 implementation(libs.lifecycle.viewmodel.compose)
 implementation(libs.lifecycle.runtime.compose)
 implementation(libs.hilt.android)
 ksp(libs.hilt.compiler)
 implementation(libs.room.runtime)
 implementation(libs.room.ktx)
 ksp(libs.room.compiler)
 implementation(libs.datastore.preferences)
 implementation(libs.coroutines.android)
 testImplementation(libs.junit)
 androidTestImplementation(libs.androidx.junit)
 androidTestImplementation(libs.room.testing)
 androidTestImplementation("androidx.test:runner:1.6.2")
 debugImplementation(libs.compose.ui.tooling)
}
