// Instrumented, PACKAGED implementation of android.hardware.bydauto.* used only by the app's
// `mock` flavor on an emulator. Every call is forwarded to the BYD Mock Service (recorder app)
// and returns success. NEVER used by the `car` flavor (which uses the compileOnly stubs).
plugins { id("com.android.library") }
android {
    namespace = "com.offline.bydmockimpl"
    compileSdk = 34
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
