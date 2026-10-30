package com.offline.bydvoice.nlu

import kotlin.math.max
import kotlin.math.min

/** Small string-similarity toolkit used to tolerate recognizer mishearings. */
object Fuzzy {

    /** Damerau-style edit distance (insert, delete, substitute, adjacent swap). */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = min(v, d[i - 2][j - 2] + 1)
                }
                d[i][j] = v
            }
        }
        return d[a.length][b.length]
    }

    fun similarity(a: String, b: String): Double {
        val m = max(a.length, b.length)
        return if (m == 0) 1.0 else 1.0 - distance(a, b).toDouble() / m
    }

    /** Classic Soundex: words that sound alike share a code ("wiper"/"viper", "roof"/"ruff"). */
    fun soundex(word: String): String {
        val w = word.lowercase().filter { it in 'a'..'z' }
        if (w.isEmpty()) return ""
        fun code(c: Char) = when (c) {
            'b', 'f', 'p', 'v' -> '1'
            'c', 'g', 'j', 'k', 'q', 's', 'x', 'z' -> '2'
            'd', 't' -> '3'
            'l' -> '4'
            'm', 'n' -> '5'
            'r' -> '6'
            else -> '0'
        }
        val sb = StringBuilder().append(w[0].uppercaseChar())
        var last = code(w[0])
        for (c in w.drop(1)) {
            val k = code(c)
            if (k != '0' && k != last) sb.append(k)
            if (c != 'h' && c != 'w') last = k
            if (sb.length == 4) break
        }
        while (sb.length < 4) sb.append('0')
        return sb.toString()
    }

    /**
     * How confidently [heard] could be [target]. Short words must match exactly (so "on" never
     * becomes "off"); longer ones tolerate a small edit distance, with a bonus when they sound alike.
     */
    fun match(heard: String, target: String): Double {
        if (heard == target) return 1.0
        if (heard.length < 4 || target.length < 4) return 0.0
        val sim = similarity(heard, target)
        val sameSound = heard[0] == target[0] && soundex(heard) == soundex(target)
        // Sound-alike gets a lift but keeps the ordering by spelling similarity (so "wite" -> "white", not "wide").
        val score = if (sameSound) minOf(1.0, sim + 0.1) else sim
        return if (score >= 0.78) score else 0.0
    }
}
