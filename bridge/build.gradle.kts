// Tiny standalone app: a bridge that hands a command to the BYD voice assistant
// (com.byd.autovoice) via its exported automation service, so the platform-signed
// assistant performs the vehicle action. Kept separate from the main app for testing.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.offline.bydbridge"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.offline.bydbridge"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildFeatures { aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {}
