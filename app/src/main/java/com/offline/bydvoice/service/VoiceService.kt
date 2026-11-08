package com.offline.bydvoice.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.offline.bydvoice.App
import com.offline.bydvoice.MainActivity
import com.offline.bydvoice.R
import com.offline.bydvoice.byd.AssistantPhrases
import com.offline.bydvoice.byd.BydAssistant
import com.offline.bydvoice.byd.BydController
import com.offline.bydvoice.nlu.IntentMatcher
import com.offline.bydvoice.command.Outcome
import com.offline.bydvoice.nlu.Tagger
import com.offline.bydvoice.platform.AndroidPlatform
import com.offline.bydvoice.platform.InstalledApps
import com.offline.bydvoice.state.AppSettings
import com.offline.bydvoice.state.HistoryItem
import com.offline.bydvoice.state.Phase
import com.offline.bydvoice.state.VoiceBus
import com.offline.bydvoice.ui.overlay.VoiceOverlay
import com.offline.bydvoice.voice.SpeechEngine
import com.offline.bydvoice.voice.Speaker
import com.offline.bydvoice.voice.WakeWord
import java.io.File

/**
 * The voice assistant. Fully offline:
 *   trigger → mic → SpeechEngine (Moonshine Tiny) → IntentMatcher → BydController → reply (TTS + overlay)
 *
 * Push-to-talk (default): the mic opens on a trigger (wheel button, app, notification), takes one
 * command, and closes. Hands-free (opt-in): continuous listening for "Hi BYD".
 */
class VoiceService : Service(), SpeechEngine.Listener {

    private enum class Mode { IDLE, SESSION, WAITING_WAKE, AWAKE }

    private val tag = "VoiceService"
    private val main = Handler(Looper.getMainLooper())
    private lateinit var settings: AppSettings
    private lateinit var engine: SpeechEngine
    private lateinit var speaker: Speaker
    private lateinit var overlay: VoiceOverlay
    private lateinit var controller: BydController
    private lateinit var assistant: BydAssistant
    private lateinit var matcher: IntentMatcher
    private var tone: ToneGenerator? = null
    private var fgStarted = false
    /** Live transcription runs whenever we're taking a command; standby only spots the wake word. */
    private var mode = Mode.IDLE
        set(value) { field = value; engine.live = value != Mode.WAITING_WAKE }
    private val awakeTimeout = Runnable { if (mode == Mode.AWAKE) { mode = Mode.WAITING_WAKE; overlay.hide(); idle() } }

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings.get(this)
        engine = SpeechEngine(this, this)
        speaker = Speaker(this)
        overlay = VoiceOverlay(this).apply { setOnTap { cancel() } }
        controller = BydController(this, AndroidPlatform(this)) { settings.driverOnLeft.value }
        assistant = BydAssistant(this)
        matcher = IntentMatcher(apps = InstalledApps(this))
        tone = runCatching { ToneGenerator(AudioManager.STREAM_SYSTEM, 70) }.getOrNull()
        VoiceBus.update { it.copy(serviceRunning = true) }
        engine.ensureModel { ok -> VoiceBus.update { it.copy(modelReady = ok) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!fgStarted) { startForegroundCompat(); fgStarted = true }

        when (intent?.action) {
            ACTION_TEST_TEXT -> { handle(intent.getStringExtra(EXTRA_TEXT).orEmpty()); return START_STICKY }
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(tag, "RECORD_AUDIO not granted")
            VoiceBus.update { it.copy(phase = Phase.ERROR, reply = "Microphone permission is needed") }
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_TRIGGER -> if (mode == Mode.SESSION || mode == Mode.AWAKE) cancel() else startSession()
            ACTION_TEST_AUDIO -> intent.getStringExtra(EXTRA_PATH)?.let {
                if (intent.getBooleanExtra(EXTRA_WAKE, false)) { engine.stop(); startHandsFree(File(it)) } else startSession(File(it))
            }
            else -> if (settings.handsFree.value) startHandsFree()
        }
        return START_STICKY
    }

    // ------------------------------------------------------------------ sessions

    private fun startSession(file: File? = null) {
        if (settings.handsFree.value && engine.isRunning && file == null) { wakeUp(); return }
        if (engine.isRunning) engine.stop()
        VoiceBus.update { it.copy(phase = Phase.LOADING, transcript = "", reply = null, commandTitle = null) }
        engine.ensureModel { ok ->
            if (!ok) { finish(false, "", "The speech model failed to load"); return@ensureModel }
            mode = Mode.SESSION
            VoiceBus.update { it.copy(phase = Phase.LISTENING, modelReady = true) }
            overlay.show()
            updateNotification(listening = true)
            beep(ToneGenerator.TONE_PROP_BEEP)
            engine.start({ config(continuous = false) }, file)
        }
    }

    private fun config(continuous: Boolean) = settings.timing.value.let { t ->
        SpeechEngine.Config(t.noSpeechMs, t.silenceMs, t.maxUtteranceMs, t.sensitivity.speechDb, continuous)
    }

    private fun startHandsFree(file: File? = null) {
        if (engine.isRunning) return
        engine.ensureModel { ok ->
            if (!ok) return@ensureModel
            mode = Mode.WAITING_WAKE
            engine.start({ config(continuous = true) }, file)
            updateNotification(listening = false)
        }
    }

    private fun wakeUp() {
        mode = Mode.AWAKE
        VoiceBus.update { it.copy(phase = Phase.LISTENING, transcript = "", reply = null, commandTitle = null) }
        overlay.show()
        beep(ToneGenerator.TONE_PROP_BEEP)
        main.removeCallbacks(awakeTimeout)
        main.postDelayed(awakeTimeout, settings.timing.value.awakeMs)
    }

    private fun cancel() {
        main.removeCallbacks(awakeTimeout)
        if (mode == Mode.SESSION) engine.stop()
        mode = if (settings.handsFree.value) Mode.WAITING_WAKE else Mode.IDLE
        speaker.stop()
        overlay.hide()
        idle()
    }

    // ------------------------------------------------------------------ engine callbacks

    override fun onLevel(level: Float) {
        if (mode == Mode.SESSION || mode == Mode.AWAKE) {
            overlay.level(level)
            VoiceBus.update { it.copy(level = level) }
        }
    }

    override fun onPartial(text: String) {
        // Intentionally not surfaced. Streaming partials are unreliable on poor cabin-mic audio and
        // flash obviously-wrong text that reads worse than nothing; the waveform already shows we're
        // listening, and the recognised words appear once the utterance finalizes (onFinalizing ->
        // "working on it", then the result). Only the final transcript is ever shown to the user.
    }

    override fun onFinalizing(text: String) {
        if (mode == Mode.SESSION || mode == Mode.AWAKE) {
            overlay.thinking(text)
            VoiceBus.update { it.copy(phase = Phase.PROCESSING, transcript = text) }
        }
    }

    override fun onUtterance(text: String) {
        when (mode) {
            Mode.SESSION -> { engine.stop(); handle(text) }
            Mode.WAITING_WAKE -> {
                val rest = WakeWord.remainder(text) ?: return
                if (rest.isBlank()) wakeUp() else { mode = Mode.AWAKE; handle(rest) }
            }
            Mode.AWAKE -> { main.removeCallbacks(awakeTimeout); handle(text) }
            Mode.IDLE -> Unit
        }
    }

    override fun onMicSilent(silent: Boolean) {
        Log.w(tag, "microphone delivering digital silence: $silent")
        VoiceBus.update { it.copy(micSilent = silent) }
        if (silent) overlay.warn("Microphone is silent — check mic permission / routing")
    }

    override fun onError(message: String) {
        Log.w(tag, "engine: $message")
        finish(false, "", message)
    }

    // ------------------------------------------------------------------ understanding + acting

    private fun handle(heard: String) {
        if (heard.isBlank()) { finish(false, "", "I didn't hear anything", speak = false); return }

        VoiceBus.update { it.copy(phase = Phase.PROCESSING, transcript = heard) }
        overlay.thinking(heard)
        if (matcher.isCancel(Tagger.tag(heard))) { finish(false, heard, "Cancelled", speak = false); return }

        val match = matcher.match(heard, settings::isEnabled)
        Log.i(tag, "heard '$heard' -> ${match?.spec?.id} (${match?.confidence?.let { "%.2f".format(it) }}) ${match?.tagged}")
        if (match == null) { finish(false, heard, "Sorry, I didn't catch that"); return }

        // On a DiLink head unit the vehicle API is locked to platform apps, so we hand the command
        // to the BYD voice assistant (which has the permissions). Anything without an assistant
        // phrase (apps, camera, screen) still goes through our own path.
        val phrase = if (settings.useAssistant.value) AssistantPhrases.phraseFor(match.action) else null
        val reply: String
        val ok: Boolean
        if (phrase != null && assistant.isAvailable()) {
            assistant.run(phrase, silence = AssistantPhrases.isOffline(match.action))
            Log.i(tag, "via assistant: '$heard' -> ${match.spec.id} phrase='$phrase'")
            reply = match.spec.reply(match.tagged, Outcome(true))
            ok = true
        } else {
            val outcome = controller.execute(match.action)
            val needs = outcome.detail?.takeIf { !outcome.ok && it.startsWith("needs") }
            reply = when {
                needs != null -> "${match.spec.title} $needs"
                !outcome.ok && outcome.value == null && outcome.detail != null ->
                    "${match.spec.reply(match.tagged, outcome)} (${outcome.detail})"
                else -> match.spec.reply(match.tagged, outcome)
            }
            ok = outcome.ok || outcome.value != null
        }
        finish(ok, heard, reply, title = match.spec.title)
    }

    private fun finish(ok: Boolean, heard: String, reply: String, title: String? = null, speak: Boolean = true) {
        VoiceBus.update { it.copy(phase = if (ok) Phase.SUCCESS else Phase.ERROR, reply = reply, commandTitle = title, level = 0f) }
        if (heard.isNotBlank() || title != null) VoiceBus.log(HistoryItem(System.currentTimeMillis(), heard, title, reply, ok))
        overlay.result(ok, heard, reply)

        val handsFree = settings.handsFree.value
        val done = {
            overlay.hide(900)
            mode = if (handsFree) Mode.WAITING_WAKE else Mode.IDLE
            engine.paused = false
            main.postDelayed({ idle() }, 900)
        }
        if (speak && settings.voiceReplies.value) {
            engine.paused = true               // don't let the assistant hear itself (hands-free)
            speaker.speak(reply) { main.post { done() } }
        } else {
            beep(if (ok) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_SUP_ERROR)
            main.postDelayed({ done() }, 1_400)
        }
    }

    private fun idle() {
        VoiceBus.update { if (it.phase == Phase.LISTENING || it.phase == Phase.PROCESSING || it.phase == Phase.LOADING) it.copy(phase = Phase.IDLE, level = 0f) else it.copy(level = 0f) }
        updateNotification(listening = false)
    }

    // ------------------------------------------------------------------ housekeeping

    private fun beep(type: Int) { runCatching { tone?.startTone(type, 120) } }

    private fun buildNotification(listening: Boolean): Notification {
        val handsFree = settings.handsFree.value
        val text = when {
            listening -> getString(R.string.notif_ptt)
            handsFree -> getString(R.string.notif_listening)
            else -> getString(R.string.notif_ready)
        }
        val pi = PendingIntent.getForegroundService(this, 1, Intent(this, VoiceService::class.java).setAction(ACTION_TRIGGER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, if (listening) "Stop" else "Speak", pi)
            .build()
    }

    private fun startForegroundCompat() {
        val n = buildNotification(listening = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIF_ID, n)
    }

    private fun updateNotification(listening: Boolean) {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(listening)) }
    }

    override fun onDestroy() {
        engine.stop()
        speaker.shutdown()
        overlay.hide()
        runCatching { tone?.release() }
        VoiceBus.update { it.copy(serviceRunning = false, phase = Phase.IDLE, level = 0f) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 42
        const val ACTION_TRIGGER = "com.offline.bydvoice.action.TRIGGER_LISTEN"
        const val ACTION_STOP = "com.offline.bydvoice.action.STOP"
        const val ACTION_TEST_TEXT = "com.offline.bydvoice.action.TEST_TEXT"
        const val ACTION_TEST_AUDIO = "com.offline.bydvoice.action.TEST_AUDIO"
        const val EXTRA_TEXT = "text"
        const val EXTRA_PATH = "path"
        const val EXTRA_WAKE = "wake"

        /** Start (hands-free) or just bring the service up. */
        fun start(context: Context) {
            if (hasMic(context)) ContextCompat.startForegroundService(context, Intent(context, VoiceService::class.java))
        }

        fun stop(context: Context) = context.stopService(Intent(context, VoiceService::class.java))

        /**
         * Push-to-talk: open the mic for one command (or cancel if already listening). Without the
         * mic permission a microphone service can't start at all, so open the app to ask for it.
         */
        fun trigger(context: Context) {
            if (hasMic(context)) {
                ContextCompat.startForegroundService(context, Intent(context, VoiceService::class.java).setAction(ACTION_TRIGGER))
            } else {
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        private fun hasMic(context: Context) =
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
}
