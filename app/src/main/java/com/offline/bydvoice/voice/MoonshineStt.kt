package com.offline.bydvoice.voice

import ai.moonshine.voice.TranscriberOption
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriptEvent
import ai.moonshine.voice.TranscriptLine
import android.util.Log
import com.offline.bydvoice.nlu.Lexicon
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Moonshine streaming speech-to-text (MIT, runs on-device through ONNX Runtime).
 *
 * Moonshine runs a transcription pass on the calling thread every ~0.5 s of audio, so everything
 * goes through one worker thread: the mic loop only hands over buffers and never stalls. Each
 * utterance gets its own stream (a restarted stream can carry stray words over).
 */
class MoonshineStt(dir: File, arch: Int, override val name: String) : Stt {

    private val transcriber = Transcriber(listOf(TranscriberOption("vad_threshold", "0.3")))
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "moonshine") }

    /** Line id -> text for the stream being transcribed. */
    private val lines = LinkedHashMap<Long, String>()
    @Volatile private var stream = -1
    @Volatile override var partial: String = ""
        private set

    init {
        transcriber.loadFromFiles(dir.absolutePath, arch)
        runCatching { transcriber.setKeyterms(Lexicon.recognizerTerms) }.onFailure { Log.w(TAG, "key terms not applied", it) }
        transcriber.addListener { e ->
            val (line, handle) = when (e) {
                is TranscriptEvent.LineStarted -> e.line to e.streamHandle
                is TranscriptEvent.LineTextChanged -> e.line to e.streamHandle
                is TranscriptEvent.LineCompleted -> e.line to e.streamHandle
                else -> return@addListener
            }
            put(line, handle)
        }
    }

    private fun put(line: TranscriptLine, handle: Int) {
        synchronized(lines) {
            if (handle != stream) return
            lines[line.id] = line.text.orEmpty()
            partial = lines.values.joinToString(" ").trim()
        }
    }

    override fun begin() {
        partial = ""
        run {
            synchronized(lines) {
                lines.clear()
                stream = transcriber.createStream()
            }
            transcriber.startStream(stream)
        }
    }

    override fun feed(pcm: ShortArray, n: Int) {
        if (exec.isShutdown) return
        val audio = FloatArray(n) { pcm[it] / 32768f }
        run {
            try {
                if (stream >= 0) transcriber.addAudioToStream(stream, audio, RATE)
            } catch (t: Throwable) {
                Log.e(TAG, "transcription pass failed", t)
            }
        }
    }

    /** Drops the stream without a final pass (stopping would transcribe what's left). */
    override fun abort() {
        partial = ""
        run { release() }
    }

    private fun release() {
        val s = stream
        synchronized(lines) { stream = -1; lines.clear() }
        if (s >= 0) runCatching { transcriber.freeStream(s) }
    }

    override fun once(pcm: ShortArray, n: Int, done: (String) -> Unit) {
        val audio = FloatArray(n) { pcm[it] / 32768f }
        run {
            val text = runCatching {
                transcriber.transcribeWithoutStreaming(audio, RATE).lines.joinToString(" ") { it.text.orEmpty() }.trim()
            }.onFailure { Log.e(TAG, "one-shot pass failed", it) }.getOrDefault("")
            done(text)
        }
    }

    override fun close() {
        run { runCatching { transcriber.close() } }
        exec.shutdown()
    }

    /** After close() (model switched mid-session) late calls are simply dropped. */
    private fun run(task: () -> Unit) {
        try { exec.execute(task) } catch (_: RejectedExecutionException) { }
    }

    companion object {
        private const val TAG = "MoonshineStt"
        private const val RATE = 16_000
    }
}
