package com.offline.bydvoice.nlu

/** Text cleanup shared by the recognizer output, the tagger and the UI. */
object Text {
    private val spaces = Regex("\\s+")
    private val junk = Regex("[^a-z0-9%.' ]")

    // Common ways the recognizer (or a person typing) spells things, folded to one form.
    private val rewrites = listOf(
        "a/c" to "ac", "a.c." to "ac", "air-con" to "aircon", "head-up" to "head up",
        "heads-up" to "heads up", "high-beam" to "high beam", "sun-roof" to "sunroof",
        "what's" to "what is", "it's" to "it is", "i'm" to "i am", "let's" to "let us",
        "driver's" to "driver", "drivers" to "driver", "passenger's" to "passenger",
        "passengers" to "passenger",
    )

    fun normalize(raw: String): String {
        var s = raw.lowercase().replace("[unk]", " ")
        for ((from, to) in rewrites) s = s.replace(from, to)
        s = s.replace(junk, " ").replace(spaces, " ").trim()
        // Strip trailing sentence periods but keep decimals like 22.5
        return s.split(' ').joinToString(" ") { it.trimEnd('.').trimStart('.') }.trim()
    }

    fun tokens(raw: String): List<String> = normalize(raw).split(' ').filter { it.isNotBlank() }
}
