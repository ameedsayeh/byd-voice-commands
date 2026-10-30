package com.offline.bydvoice.voice

/**
 * The free-form transcriber: turns whatever was said into text the user sees and the matcher reads.
 * Fed from the speech engine's audio thread; must never block it for long.
 */
interface Stt {
    /** For logs, e.g. "Moonshine Tiny". */
    val name: String

    /** Start live transcription of a new utterance. */
    fun begin()

    /** Add 16 kHz mono PCM. Cheap: heavy work happens on the transcriber's own thread. */
    fun feed(pcm: ShortArray, n: Int)

    /** Best text so far for the current utterance (for the live transcript). */
    val partial: String

    /** Stop live transcription. */
    fun abort()

    /** Transcribe a finished clip in one pass (the final text of every utterance). */
    fun once(pcm: ShortArray, n: Int, done: (String) -> Unit)

    fun close()
}
