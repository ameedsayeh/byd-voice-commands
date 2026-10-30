package com.offline.bydvoice.state

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How loud speech must be, relative to the background noise, before it counts as talking. */
enum class Sensitivity(val speechDb: Double, val label: String) {
    LOW(14.0, "Low"), NORMAL(10.0, "Normal"), HIGH(6.0, "High"),
}

/** How long the assistant listens. */
data class Timing(
    /** Quiet after you stop talking that ends the command. */
    val silenceMs: Long = 1_100,
    /** Give up if you haven't started talking by then. */
    val noSpeechMs: Long = 6_000,
    /** Hard limit for one command. */
    val maxUtteranceMs: Long = 12_000,
    /** Hands-free: how long it waits for a command after "Hi BYD". */
    val awakeMs: Long = 8_000,
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
)

/** Persisted user settings, exposed as StateFlows so the UI and the service stay in sync. */
class AppSettings private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _disabled = MutableStateFlow(prefs.getStringSet(K_DISABLED, emptySet())!!.toSet())
    val disabledCommands: StateFlow<Set<String>> = _disabled.asStateFlow()

    private val _handsFree = MutableStateFlow(prefs.getBoolean(K_HANDS_FREE, false))
    val handsFree: StateFlow<Boolean> = _handsFree.asStateFlow()

    private val _voiceReplies = MutableStateFlow(prefs.getBoolean(K_REPLIES, true))
    val voiceReplies: StateFlow<Boolean> = _voiceReplies.asStateFlow()

    private val _driverOnLeft = MutableStateFlow(prefs.getBoolean(K_LHD, true))
    val driverOnLeft: StateFlow<Boolean> = _driverOnLeft.asStateFlow()

    private val _timing = MutableStateFlow(Timing().let { d ->
        Timing(
            silenceMs = prefs.getLong(K_SILENCE, d.silenceMs),
            noSpeechMs = prefs.getLong(K_NO_SPEECH, d.noSpeechMs),
            maxUtteranceMs = prefs.getLong(K_MAX, d.maxUtteranceMs),
            awakeMs = prefs.getLong(K_AWAKE, d.awakeMs),
            sensitivity = runCatching { Sensitivity.valueOf(prefs.getString(K_SENSITIVITY, null)!!) }.getOrDefault(d.sensitivity),
        )
    })
    val timing: StateFlow<Timing> = _timing.asStateFlow()

    fun isEnabled(id: String) = id !in _disabled.value

    fun setEnabled(id: String, enabled: Boolean) = setEnabled(listOf(id), enabled)

    fun setEnabled(ids: Collection<String>, enabled: Boolean) {
        val next = if (enabled) _disabled.value - ids.toSet() else _disabled.value + ids
        _disabled.value = next
        prefs.edit().putStringSet(K_DISABLED, next).apply()
    }

    fun setHandsFree(on: Boolean) { _handsFree.value = on; prefs.edit().putBoolean(K_HANDS_FREE, on).apply() }
    fun setVoiceReplies(on: Boolean) { _voiceReplies.value = on; prefs.edit().putBoolean(K_REPLIES, on).apply() }
    fun setDriverOnLeft(on: Boolean) { _driverOnLeft.value = on; prefs.edit().putBoolean(K_LHD, on).apply() }
    fun setTiming(t: Timing) {
        _timing.value = t
        prefs.edit().putLong(K_SILENCE, t.silenceMs).putLong(K_NO_SPEECH, t.noSpeechMs).putLong(K_MAX, t.maxUtteranceMs)
            .putLong(K_AWAKE, t.awakeMs).putString(K_SENSITIVITY, t.sensitivity.name).apply()
    }

    companion object {
        private const val K_DISABLED = "disabled_commands"
        private const val K_HANDS_FREE = "hands_free"
        private const val K_REPLIES = "voice_replies"
        private const val K_LHD = "driver_on_left"
        private const val K_SILENCE = "silence_ms"
        private const val K_NO_SPEECH = "no_speech_ms"
        private const val K_MAX = "max_utterance_ms"
        private const val K_AWAKE = "awake_ms"
        private const val K_SENSITIVITY = "sensitivity"

        @Volatile private var instance: AppSettings? = null
        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) { instance ?: AppSettings(context.applicationContext).also { instance = it } }
    }
}
