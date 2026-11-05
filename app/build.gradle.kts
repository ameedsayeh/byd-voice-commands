plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.offline.bydvoice"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.offline.bydvoice"
        minSdk = 28
        targetSdk = 34
        versionCode = 3
        versionName = "3.0"
        // BYD head units are 64-bit ARM, as is the Apple-silicon emulator; one ABI keeps the APK small.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            // R8 strips unused Compose icons etc.; signed with the debug key so it sideloads like before.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
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
        compose = true
        buildConfig = true
        aidl = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"   // matches Kotlin 1.9.24
    }
    // car = real device (compileOnly stubs). mock = emulator test build that packages an
    // instrumented BYD impl which forwards calls to the recorder app.
    flavorDimensions += "backend"
    productFlavors {
        create("car") { dimension = "backend" }
        create("mock") {
            dimension = "backend"
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
        }
    }
}

dependencies {
    // car: compile against stubs; the real device provides the classes at runtime.
    "carCompileOnly"(project(":bydstub"))
    // mock: package the instrumented impl that forwards every call to the recorder app.
    "mockImplementation"(project(":bydmockimpl"))

    // Offline speech recognition: Moonshine Tiny streaming (MIT), model bundled in assets.
    implementation("ai.moonshine:moonshine-voice:0.1.5") {
        // Only used by Moonshine's model downloader / TTS UI; the models are bundled, the app stays offline.
        exclude(group = "com.squareup.okhttp3")
        exclude(group = "androidx.work")
        exclude(group = "com.google.android.material")
    }

    // NLU + command catalog (pure Kotlin, unit-tested on the JVM)
    implementation(project(":voicecore"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // UI
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
}
