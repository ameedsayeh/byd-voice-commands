package com.offline.bydvoice.nlu

/**
 * Finds spoken numbers in a token list ("twenty two", "twenty two point five", "a hundred",
 * "22", "level three", "third") and returns them as spans so the tagger can turn them into slots.
 *
 * Recognizers often hear "two" as "to"/"too" and "four" as "for"; those are only read as digits
 * right after a tens word ("twenty to" -> 22) or as the last word after "speed"/"level"
 * ("fan speed for" -> 4), never on their own ("set it to twenty" stays 20).
 */
object Numbers {

    data class Span(val start: Int, val end: Int, val value: Double) {
        val intValue: Int get() = Math.round(value).toInt()
    }

    private val units = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19,
    )
    private val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60,
        "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )
    private val ordinals = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6,
        "seventh" to 7,
    )
    private val unitHomophones = mapOf("to" to 2, "too" to 2, "for" to 4)
    private val levelWords = setOf("speed", "level", "position")

    fun parse(tokens: List<String>): List<Span> {
        val out = ArrayList<Span>()
        var i = 0
        while (i < tokens.size) {
            val span = readAt(tokens, i)
            if (span != null) {
                out.add(span)
                i = span.end
            } else {
                i++
            }
        }
        return out
    }

    private fun readAt(t: List<String>, start: Int): Span? {
        val first = t[start]

        // Digits as text, e.g. "22", "22.5", "30%".
        first.trimEnd('%').toDoubleOrNull()?.let { return Span(start, start + 1, it) }

        ordinals[first]?.let { return Span(start, start + 1, it.toDouble()) }

        // "fan speed for" / "level to" — only when nothing follows that could be the real number
        if (first in unitHomophones && levelWords.any { t.getOrNull(start - 1)?.endsWith(it) == true } && !isNumberWord(t.getOrNull(start + 1)))
            return Span(start, start + 1, unitHomophones.getValue(first).toDouble())

        var i = start
        var total = 0.0
        var current = 0.0
        var consumed = false

        // "a hundred" / "one hundred"
        if (t[i] == "a" && i + 1 < t.size && t[i + 1] == "hundred") {
            current = 1.0
            i++
        }

        while (i < t.size) {
            val w = t[i]
            when {
                units.containsKey(w) -> {
                    // two unit words in a row ("two three") are separate numbers
                    if (consumed && current % 10.0 != 0.0) break
                    current += units.getValue(w)
                    consumed = true
                }
                tens.containsKey(w) -> {
                    if (consumed && current % 100.0 != 0.0) break
                    current += tens.getValue(w)
                    consumed = true
                    // "twenty to" / "twenty for" at the end of a number
                    val next = t.getOrNull(i + 1)
                    val afterNext = t.getOrNull(i + 2)
                    if (next != null && unitHomophones.containsKey(next) && !isNumberWord(afterNext)) {
                        current += unitHomophones.getValue(next)
                        i++
                    }
                }
                w == "hundred" && consumed -> current *= 100
                w == "hundred" && !consumed -> { current = 100.0; consumed = true }
                w == "and" && consumed && isNumberWord(t.getOrNull(i + 1)) -> Unit
                w == "point" && consumed && i + 1 < t.size && units.containsKey(t[i + 1]) -> {
                    total += current
                    current = 0.0
                    var scale = 0.1
                    var j = i + 1
                    while (j < t.size && units.containsKey(t[j]) && units.getValue(t[j]) < 10) {
                        total += units.getValue(t[j]) * scale
                        scale /= 10
                        j++
                    }
                    return Span(start, j, total)
                }
                else -> break
            }
            i++
        }
        if (!consumed) return null
        return Span(start, i, total + current)
    }

    private fun isNumberWord(w: String?): Boolean =
        w != null && (units.containsKey(w) || tens.containsKey(w) || w == "hundred" || w.toDoubleOrNull() != null)
}
