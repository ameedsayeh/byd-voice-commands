// Compile-only stubs for BYD's proprietary framework API (android.hardware.bydauto.*).
// These classes exist in the car's Android ROM at runtime; here they only let the app
// compile off-device. NOTHING from this module is packaged into the APK (see app's
// compileOnly dependency), so the real framework classes are used on the head unit.
plugins {
    id("com.android.library")
}

android {
    namespace = "com.offline.bydstub"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
