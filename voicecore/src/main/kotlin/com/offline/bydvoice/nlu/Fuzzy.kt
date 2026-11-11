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
     * Metaphone (Philips, 1990): an English phonetic key that handles silent letters and digraphs
     * Soundex misses - "ph"->F, "kn"/"gn" dropped, silent "gh", "c"->K/S, "x"->KS - and does not
     * require the same first letter, so it links "knight"/"night", "seat"/"seed", "phone"/"fone".
     * '0' stands for the "th" sound and 'X' for "sh"/"ch".
     */
    fun metaphone(word: String, maxLen: Int = 6): String {
        val w = word.uppercase().filter { it in 'A'..'Z' }
        if (w.isEmpty()) return ""
        val n = w.length
        fun at(k: Int): Char = if (k in 0 until n) w[k] else ' '
        fun vowel(c: Char) = c in "AEIOU"
        val sb = StringBuilder()

        var i = when {
            n >= 2 && w.substring(0, 2) in setOf("AE", "GN", "KN", "PN", "WR") -> 1
            w[0] == 'X' -> { sb.append('S'); 1 }
            n >= 2 && w.substring(0, 2) == "WH" -> { sb.append('W'); 2 }
            else -> 0
        }

        while (i < n && sb.length < maxLen) {
            val c = w[i]
            if (c != 'C' && c == at(i - 1)) { i++; continue }   // drop doubled letters (keep "cc")
            val prev = at(i - 1)
            val next = at(i + 1)
            val next2 = at(i + 2)
            when (c) {
                'A', 'E', 'I', 'O', 'U' -> if (i == 0) sb.append(c)
                'B' -> if (!(i == n - 1 && prev == 'M')) sb.append('B')              // silent in "dumb"
                'C' -> when {
                    next == 'I' && next2 == 'A' -> sb.append('X')                    // "cia" -> X
                    next == 'H' -> { sb.append(if (prev == 'S') 'K' else 'X'); i++ } // "sch"->K, "ch"->X
                    next in "IEY" -> if (prev != 'S') sb.append('S')                 // "ci/ce/cy" -> S
                    else -> sb.append('K')
                }
                'D' -> if (next == 'G' && next2 in "IEY") { sb.append('J'); i += 2 } else sb.append('T')
                'F' -> sb.append('F')
                'G' -> when {
                    next == 'H' -> { if (vowel(next2)) sb.append('K'); i++ }         // "gh" silent unless before a vowel
                    next == 'N' -> {}                                                 // "gn" silent
                    next in "IEY" -> sb.append('J')
                    else -> sb.append('K')
                }
                'H' -> if (vowel(next) && prev !in "CSPTG") sb.append('H')           // kept only before a vowel
                'J' -> sb.append('J')
                'K' -> if (prev != 'C') sb.append('K')
                'L' -> sb.append('L')
                'M' -> sb.append('M')
                'N' -> sb.append('N')
                'P' -> if (next == 'H') { sb.append('F'); i++ } else sb.append('P')  // "ph" -> F
                'Q' -> sb.append('K')
                'R' -> sb.append('R')
                'S' -> when {
                    next == 'H' -> { sb.append('X'); i++ }                           // "sh" -> X
                    next == 'I' && next2 in "OA" -> sb.append('X')                   // "sio/sia" -> X
                    else -> sb.append('S')
                }
                'T' -> when {
                    next == 'H' -> { sb.append('0'); i++ }                           // "th" -> 0
                    next == 'I' && next2 in "OA" -> sb.append('X')                   // "tio/tia" -> X
                    else -> sb.append('T')
                }
                'V' -> sb.append('F')
                'W', 'Y' -> if (vowel(next)) sb.append(c)                            // kept only before a vowel
                'X' -> sb.append("KS")
                'Z' -> sb.append('S')
            }
            i++
        }
        return sb.toString().take(maxLen)
    }

    /**
     * How confidently [heard] could be [target]. Short words must match exactly (so "on" never
     * becomes "off"); longer ones tolerate a small edit distance, with a bonus when they sound alike
     * (Soundex, or Metaphone which also catches sound-alikes spelled with a different first letter).
     */
    fun match(heard: String, target: String): Double {
        if (heard == target) return 1.0
        if (heard.length < 4 || target.length < 4) return 0.0
        val sim = similarity(heard, target)
        val sameSound = (heard[0] == target[0] && soundex(heard) == soundex(target)) ||
            metaphone(heard) == metaphone(target)
        // Sound-alike gets a lift but keeps the ordering by spelling similarity (so "wite" -> "white", not "wide").
        val score = if (sameSound) minOf(1.0, sim + 0.1) else sim
        return if (score >= 0.78) score else 0.0
    }
}
