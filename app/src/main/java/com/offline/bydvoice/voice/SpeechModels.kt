package com.offline.bydvoice.voice

import ai.moonshine.voice.JNI
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.offline.bydvoice.BuildConfig
import com.offline.bydvoice.state.VoiceBus
import java.io.File
import java.util.concurrent.Executors

/** Loads the bundled Moonshine Tiny model once per process. */
object SpeechModels {
    private const val TAG = "SpeechModels"
    private const val ASSET = "moonshine/tiny-streaming-en"

    @Volatile var stt: Stt? = null
        private set

    private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "model-loader") }
    private val main = Handler(Looper.getMainLooper())

    /** Loads the model if it isn't yet; [onReady] runs on the main thread. */
    fun ensure(context: Context, onReady: (Boolean) -> Unit = {}) {
        val app = context.applicationContext
        loader.execute {
            if (stt == null) {
                val started = System.currentTimeMillis()
                stt = runCatching { MoonshineStt(unpack(app), JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING, "Moonshine Tiny") }
                    .onFailure { Log.e(TAG, "speech model failed to load", it) }
                    .getOrNull()
                Log.i(TAG, "speech model ready=${stt != null} in ${System.currentTimeMillis() - started} ms")
            }
            val ok = stt != null
            VoiceBus.update { it.copy(modelReady = ok) }
            main.post { onReady(ok) }
        }
    }

    /** ONNX Runtime needs real files: copy the bundled model out of the APK once per app version. */
    private fun unpack(app: Context): File {
        val dir = File(app.filesDir, ASSET)
        val marker = File(dir, ".v${BuildConfig.VERSION_CODE}")
        if (!marker.exists()) {
            dir.deleteRecursively()
            dir.mkdirs()
            for (name in app.assets.list(ASSET).orEmpty()) {
                app.assets.open("$ASSET/$name").use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
            }
            marker.createNewFile()
            // Models earlier versions unpacked (Vosk, Moonshine Small) are no longer used.
            File(app.filesDir, "moonshine/small-streaming-en").deleteRecursively()
            app.getExternalFilesDir(null)?.let { File(it, "model").deleteRecursively() }
        }
        return dir
    }
}
