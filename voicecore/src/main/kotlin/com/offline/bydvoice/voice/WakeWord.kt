package com.offline.bydvoice.voice

import com.offline.bydvoice.nlu.Text

/**
 * "Hi BYD" / "Hello BYD" for hands-free mode. The recognizer is nudged to write "BYD", but may still
 * hear "bid", "bite", "b y d", "buy d"… We accept a greeting followed closely by any of those, and
 * return whatever was said after it so "hi byd open the windows" works in one breath.
 */
object WakeWord {
    private val greetings = setOf("hi", "high", "hello", "hey", "ok", "okay")
    private val brand = setOf("byd", "bid", "bide", "bite", "bead", "beat", "bed", "bait", "by", "buy", "b", "be", "bee", "y", "d", "dee", "why")

    /** Null if no wake phrase; otherwise the (possibly empty) command that followed it. */
    fun remainder(text: String): String? {
        val t = Text.tokens(text)
        for (i in t.indices) {
            if (t[i] !in greetings) continue
            var j = i + 1
            var hits = 0
            while (j < t.size && j <= i + 3 && t[j] in brand) { hits++; j++ }
            if (hits > 0) return t.drop(j).joinToString(" ")
        }
        return null
    }
}
