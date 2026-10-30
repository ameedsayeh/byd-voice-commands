package com.offline.bydvoice.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/** Spoken confirmations ("Turning on the air conditioner") via the device's offline TTS engine. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false
    private val pending = HashMap<String, () -> Unit>()

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) { Log.w(TAG, "TTS init failed: $status"); return }
        val lang = tts.setLanguage(Locale.US)
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.getDefault())
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setSpeechRate(1.05f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {}
            override fun onDone(id: String) { synchronized(pending) { pending.remove(id) }?.invoke() }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String) { synchronized(pending) { pending.remove(id) }?.invoke() }
        })
        ready = true
        Log.i(TAG, "TTS ready (${tts.defaultEngine})")
    }

    /** Speaks [text]; [onDone] runs when finished (or immediately if TTS is unavailable). */
    fun speak(text: String, onDone: () -> Unit = {}) {
        if (!ready || text.isBlank()) { onDone(); return }
        val id = UUID.randomUUID().toString()
        synchronized(pending) { pending[id] = onDone }
        Log.i(TAG, "say: $text")
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            synchronized(pending) { pending.remove(id) }?.invoke()
        }
    }

    fun stop() = runCatching { tts.stop() }
    fun shutdown() = runCatching { tts.shutdown() }

    companion object { private const val TAG = "Speaker" }
}
