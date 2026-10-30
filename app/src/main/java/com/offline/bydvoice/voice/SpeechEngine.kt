package com.offline.bydvoice.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Offline speech pipeline built directly on AudioRecord so we get: live mic levels for the UI, our
 * own end-of-speech detection, a digital-silence alarm, and a WAV-file input for testing.
 *
 * Moonshine Tiny ([Stt]) transcribes live while a command is being spoken, for the text you see.
 * The command itself is read from one pass over the whole utterance once you stop: that gives the
 * same text for the same audio every time (streaming passes depend on timing) and is a little more
 * accurate. In hands-free standby nothing is transcribed until the energy detector hears someone
 * talk; that stretch is then transcribed and checked for "Hi BYD".
 */
class SpeechEngine(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onLevel(level: Float)
        fun onPartial(text: String)
        /** You stopped talking; the final transcript is being worked out. */
        fun onFinalizing(text: String) {}
        /** End of one utterance. Empty = nothing was said before the timeout. */
        fun onUtterance(text: String)
        fun onMicSilent(silent: Boolean)
        fun onError(message: String)
    }

    data class Config(
        /** Give up if nobody starts talking within this. */
        val noSpeechMs: Long = 6_000,
        /** This much quiet after speech ends the command. */
        val silenceMs: Long = 1_100,
        /** Hard cut-off for one command. */
        val maxUtteranceMs: Long = 12_000,
        /** How far above the background noise (dB) counts as speech; lower = more sensitive. */
        val speechDb: Double = 10.0,
        /** Hands-free: keep listening and emit an utterance each time someone stops talking. */
        val continuous: Boolean = false,
    )

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var worker: Worker? = null
    @Volatile var paused = false
    /** Transcribe as you speak. Hands-free turns this on once it has been woken. */
    @Volatile var live = true

    val isRunning: Boolean get() = worker?.isAlive == true

    fun ensureModel(onReady: (Boolean) -> Unit) = SpeechModels.ensure(context, onReady)

    /** [config] is read continuously, so settings changed mid-session apply right away. */
    fun start(config: () -> Config, file: File? = null) {
        if (isRunning) return
        val stt = SpeechModels.stt ?: run { listener.onError("Speech model not loaded"); return }
        worker = Worker(stt, config, file).also { it.start() }
    }

    fun stop() {
        val w = worker ?: return
        w.running = false
        if (Thread.currentThread() != w) runCatching { w.join(800) }
        worker = null
    }

    private inner class Worker(
        private val stt: Stt,
        private val settings: () -> Config,
        private val file: File?,
    ) : Thread("speech-engine") {
        @Volatile var running = true

        override fun run() {
            var config = settings()
            val source: PcmSource = try {
                if (file != null) WavSource(file) else MicSource(reuse = isEmulator())
            } catch (t: Throwable) {
                post { listener.onError("Microphone unavailable: ${t.message}") }
                return
            }

            val buf = ShortArray(CHUNK)
            var streaming = false                 // Moonshine is transcribing this utterance live
            val clip = Clip(RATE * 32)            // the utterance's audio, transcribed in one pass at the end
            val preroll = ArrayDeque<ShortArray>()
            var lastShown = ""
            var level = 0f
            var floor = Double.NaN
            var chunks = 0
            var zeroMs = 0L
            var silentReported = false
            val sessionStart = SystemClock.elapsedRealtime()
            var inUtterance = false
            var uttStart = 0L
            var lastSpeech = 0L
            var lastSound = 0L

            fun endUtterance() {
                inUtterance = false; lastShown = ""
                val wasLive = streaming
                if (streaming) {
                    val shownSoFar = stt.partial
                    stt.abort(); streaming = false
                    post { listener.onFinalizing(shownSoFar) }
                }
                if (clip.size >= MIN_CLIP_SAMPLES) {
                    val (pcm, n) = clip.snapshot()
                    val stopped = SystemClock.elapsedRealtime()
                    stt.once(pcm, n) { text ->
                        val ms = SystemClock.elapsedRealtime() - stopped
                        if (wasLive) Log.i(TAG, "utterance '$text' (final after $ms ms)") else Log.d(TAG, "standby heard '$text'")
                        post { listener.onUtterance(text) }
                    }
                } else if (!config.continuous) post { listener.onUtterance("") }
                clip.clear()
            }

            try {
                source.start()
                while (running) {
                    val n = source.read(buf)
                    if (n < 0) {                     // end of a test file, or a mic error
                        if (file == null) post { listener.onError("Microphone read failed ($n)") }
                        else if (inUtterance || streaming) endUtterance()
                        break
                    }
                    if (n == 0) continue
                    chunks++
                    val now = SystemClock.elapsedRealtime()
                    if (chunks % 25 == 0) config = settings()

                    // ---- level + digital-silence detection ----
                    var sum = 0.0
                    var peak = 0
                    for (i in 0 until n) { val s = buf[i].toInt(); sum += s * s; if (s > peak) peak = s else if (-s > peak) peak = -s }
                    val db = 20 * log10(sqrt(sum / n) / 32768.0 + 1e-9)
                    val norm = ((db + 60) / 48).toFloat().coerceIn(0f, 1f)
                    level = max(norm, level * 0.82f)
                    if (chunks % 50 == 0) Log.d(TAG, "mic level %.1f dB, peak %d".format(db, peak))
                    val shown = if (paused) 0f else level
                    post { listener.onLevel(shown) }

                    if (file == null) {
                        zeroMs = if (peak == 0) zeroMs + CHUNK_MS else 0
                        val silent = zeroMs >= 1_500
                        if (silent != silentReported) { silentReported = silent; post { listener.onMicSilent(silent) } }
                    }
                    if (paused) {
                        if (streaming) { stt.abort(); streaming = false }
                        continue
                    }

                    // ---- live transcription on/off (hands-free switches it on when woken) ----
                    if (live && !streaming) { stt.begin(); streaming = true }
                    else if (!live && streaming) { stt.abort(); streaming = false }
                    if (streaming) stt.feed(buf, n)

                    // ---- adaptive noise floor + energy VAD ----
                    if (floor.isNaN()) floor = db
                    if (chunks <= 8) floor = minOf(floor, db)
                    else if (!inUtterance && db < floor + 6) floor = floor * 0.97 + db * 0.03
                    val speech = db > max(floor + config.speechDb, -52.0)
                    if (db > floor + 4) lastSound = now

                    // Keep the utterance's audio (with a little lead-in) for the final pass.
                    if (inUtterance) clip.add(buf, n)
                    else { preroll.addLast(buf.copyOf(n)); if (preroll.size > PREROLL_CHUNKS) preroll.removeFirst() }

                    val partial = if (streaming) stt.partial else ""
                    // Growing text counts as speech for quiet talkers — but only with some sound
                    // behind it, so a transcriber imagining words in road noise can't hold the mic open.
                    val grew = partial.length > lastShown.length && now - lastSound < 1_500
                    if (partial != lastShown) { lastShown = partial; post { listener.onPartial(partial) } }

                    if (speech || grew) {
                        lastSpeech = now
                        if (!inUtterance) {
                            inUtterance = true; uttStart = now
                            clip.clear(); preroll.forEach { clip.add(it, it.size) }; preroll.clear()
                        }
                    }

                    val endOfSpeech = inUtterance && now - lastSpeech >= config.silenceMs
                    val tooLong = inUtterance && now - uttStart >= config.maxUtteranceMs
                    val nothingSaid = !inUtterance && !config.continuous && now - sessionStart >= config.noSpeechMs
                    if (endOfSpeech || tooLong || nothingSaid) {
                        endUtterance()
                        if (!config.continuous) break
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "engine error", t)
                post { listener.onError(t.message ?: "audio error") }
            } finally {
                if (streaming) stt.abort()
                source.close()
                post { listener.onLevel(0f) }
            }
        }
    }

    /** Growable PCM buffer with a cap (older audio is dropped first). */
    private class Clip(private val cap: Int) {
        private var data = ShortArray(RATE * 2)
        var size = 0
            private set
        fun add(src: ShortArray, n: Int) {
            if (size + n > cap) { val drop = size + n - cap; System.arraycopy(data, drop, data, 0, size - drop); size -= drop }
            if (size + n > data.size) data = data.copyOf(minOf(cap, maxOf(size + n, data.size * 2)))
            System.arraycopy(src, 0, data, size, n); size += n
        }
        fun clear() { size = 0 }
        fun snapshot(): Pair<ShortArray, Int> = data.copyOf(size) to size
    }

    private fun post(block: () -> Unit) { main.post(block) }

    // ---------------------------------------------------------------------------------------------

    private interface PcmSource { fun start(); fun read(buf: ShortArray): Int; fun close() }

    /** One AudioRecord per session (mic fully off between) — except on emulators, whose virtual
     *  mic only feeds the first AudioRecord, so there we keep it and just start/stop recording. */
    @SuppressLint("MissingPermission")
    private class MicSource(private val reuse: Boolean) : PcmSource {
        private val record: AudioRecord = (if (reuse) shared else null)?.takeIf { it.state == AudioRecord.STATE_INITIALIZED } ?: create().also { if (reuse) shared = it }

        private fun create(): AudioRecord {
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val size = max(min, RATE * 2) // ~1 s
            for (src in listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
                val r = AudioRecord(src, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
                if (r.state == AudioRecord.STATE_INITIALIZED) return r
                r.release()
            }
            error("AudioRecord could not be initialised")
        }

        override fun start() = record.startRecording()
        override fun read(buf: ShortArray): Int = record.read(buf, 0, buf.size)
        override fun close() {
            runCatching { record.stop() }
            if (!reuse) runCatching { record.release() }
        }

        companion object { private var shared: AudioRecord? = null }
    }

    /** 16 kHz mono PCM16 WAV, played in real time, then 2 s of silence so endpointing fires. */
    private class WavSource(file: File) : PcmSource {
        private val raf = RandomAccessFile(file, "r")
        private var padding = (2_000 / CHUNK_MS).toInt()

        init {
            // Walk RIFF chunks to the "data" chunk.
            raf.seek(12)
            while (true) {
                val id = ByteArray(4).also { raf.readFully(it) }
                val size = Integer.reverseBytes(raf.readInt())
                if (String(id) == "data") break
                raf.skipBytes(size)
            }
        }

        override fun start() {}
        override fun read(buf: ShortArray): Int {
            Thread.sleep(CHUNK_MS)
            val bytes = ByteArray(buf.size * 2)
            val got = raf.read(bytes)
            if (got > 0) {
                for (i in 0 until got / 2) buf[i] = ((bytes[2 * i + 1].toInt() shl 8) or (bytes[2 * i].toInt() and 0xff)).toShort()
                for (i in got / 2 until buf.size) buf[i] = 0
                return buf.size
            }
            if (padding-- > 0) { buf.fill(0); return buf.size }
            return -1
        }
        override fun close() { runCatching { raf.close() } }
    }

    companion object {
        private const val TAG = "SpeechEngine"
        private const val RATE = 16_000
        private const val CHUNK = 640            // 40 ms
        private const val CHUNK_MS = 40L
        private const val PREROLL_CHUNKS = 10       // 400 ms
        private const val MIN_CLIP_SAMPLES = RATE * 2 / 5   // ignore clicks shorter than 0.4 s

        fun isEmulator(): Boolean {
            val fp = Build.FINGERPRINT ?: ""
            return fp.startsWith("generic") || fp.contains("emulator", true) ||
                Build.MODEL.contains("sdk_gphone") || Build.MODEL.contains("Emulator") ||
                Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu") ||
                Build.PRODUCT.contains("sdk")
        }
    }
}
