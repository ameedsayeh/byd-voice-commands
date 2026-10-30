package com.offline.bydvoice.nlu

/** One concept found in the utterance, with how sure we are (1.0 = exact word, lower = fuzzy). */
data class Tag(val concept: C, val confidence: Double, val start: Int, val end: Int, val heard: String)

/** The structured reading of an utterance that commands are matched against. */
class Tagged(
    val text: String,
    val tokens: List<String>,
    val tags: List<Tag>,
    /** A plain number that wasn't a percentage (temperature, fan level, seat level…). */
    val number: Double?,
    /** An explicit or implied percentage ("30 percent", "halfway" = 50, "a quarter" = 25). */
    val percent: Int?,
    val color: String?,
    /** Meaningful words nothing understood, in order (used for app names). */
    val unexplained: List<String>,
) {
    private val best: Map<C, Double> = tags.groupBy { it.concept }.mapValues { (_, v) -> v.maxOf { it.confidence } }

    val concepts: Set<C> get() = best.keys
    fun conf(c: C): Double = best[c] ?: 0.0
    fun has(c: C, min: Double = 0.0): Boolean = (best[c] ?: 0.0) > min
    val isEmpty: Boolean get() = tokens.isEmpty()

    override fun toString(): String =
        "'$text' -> ${tags.joinToString { "${it.concept}${if (it.confidence < 1) "~%.2f".format(it.confidence) else ""}" }}" +
            (number?.let { " num=$it" } ?: "") + (percent?.let { " pct=$it" } ?: "") +
            (color?.let { " color=$it" } ?: "") + if (unexplained.isNotEmpty()) " ?=$unexplained" else ""
}

object Tagger {

    private val percentWords = setOf("percent", "%", "percentage")
    private val degreeWords = setOf("degrees", "degree", "celsius", "fahrenheit")

    /**
     * Splits a glued word into two known parts, each exact or a close fuzzy match ("aquater" ->
     * a + quarter). One part may be a filler word, but at least one must carry meaning.
     */
    private fun splitGlued(w: String, keys: List<String>): List<Pair<String, Double>>? {
        fun resolve(part: String): Pair<String, Double>? {
            if (part in Lexicon.stopwords) return part to 1.0
            if (Lexicon.phrases.containsKey(part)) return part to 1.0
            if (part.length < 4) return null
            val best = keys.maxByOrNull { Fuzzy.match(part, it) } ?: return null
            val s = Fuzzy.match(part, best)
            return if (s > 0) best to s else null
        }
        var best: List<Pair<String, Double>>? = null
        var bestScore = 0.0
        for (cut in 1 until w.length) {
            val left = resolve(w.substring(0, cut)) ?: continue
            val right = resolve(w.substring(cut)) ?: continue
            val content = listOf(left, right).filter { it.first !in Lexicon.stopwords }
            if (content.isEmpty()) continue
            val score = content.minOf { it.second } * (if (content.size == 2) 1.0 else 0.97)
            if (score > bestScore) { bestScore = score; best = content }
        }
        return best
    }

    fun tag(raw: String): Tagged {
        val tokens = Text.tokens(raw)
        val used = BooleanArray(tokens.size)
        val tags = ArrayList<Tag>()
        var number: Double? = null
        var percent: Int? = null
        var color: String? = null

        fun mark(from: Int, to: Int) { for (k in from until to) used[k] = true }
        fun add(c: C, conf: Double, from: Int, to: Int) =
            tags.add(Tag(c, conf, from, to, tokens.subList(from, to).joinToString(" ")))

        // 1) "three quarters" before numbers would eat "three"
        for (i in 0 until tokens.size - 1) {
            if ((tokens[i] == "three" || tokens[i] == "3") && tokens[i + 1] == "quarters") {
                percent = 75; add(C.PERCENT, 1.0, i, i + 2); mark(i, i + 2)
            }
        }

        // 2) numbers, with their unit when one follows
        for (span in Numbers.parse(tokens)) {
            if ((span.start until span.end).any { used[it] }) continue
            var end = span.end
            val next = tokens.getOrNull(end)
            val next2 = tokens.getOrNull(end + 1)
            when {
                tokens[span.start].endsWith("%") || next in percentWords -> {
                    percent = span.intValue.coerceIn(0, 100)
                    if (next in percentWords) end++
                    add(C.PERCENT, 1.0, span.start, end)
                }
                next == "per" && next2 == "cent" -> {
                    percent = span.intValue.coerceIn(0, 100); end += 2; add(C.PERCENT, 1.0, span.start, end)
                }
                else -> {
                    number = span.value
                    if (next in degreeWords) { end++; add(C.DEGREES, 1.0, span.start, end) }
                    add(C.NUM, 1.0, span.start, end)
                }
            }
            mark(span.start, end)
        }

        // 3) longest exact phrase / colour match on what's left
        val maxLen = Lexicon.longestPhraseWords
        var i = 0
        while (i < tokens.size) {
            if (used[i]) { i++; continue }
            var matched = false
            for (len in minOf(maxLen, tokens.size - i) downTo 1) {
                if ((i until i + len).any { used[it] }) continue
                val key = tokens.subList(i, i + len).joinToString(" ")
                Lexicon.colors[key]?.let {
                    color = it; add(C.COLOR, 1.0, i, i + len); mark(i, i + len); matched = true
                }
                if (matched) break
                Lexicon.phrases[key]?.let { cs ->
                    cs.forEach { c -> add(c, 1.0, i, i + len) }; mark(i, i + len); matched = true
                }
                if (matched) { i += len; break }
            }
            if (!matched) i++
        }

        // 4) words the recognizer split ("sun roof", "wind shield", "head up") glued back together
        for (k in 0 until tokens.size - 1) {
            if (used[k] || used[k + 1]) continue
            Lexicon.phrases[tokens[k] + tokens[k + 1]]?.let { cs ->
                cs.forEach { c -> add(c, 0.95, k, k + 2) }; mark(k, k + 2)
            }
        }

        val singleWordKeys = Lexicon.phrases.keys.filter { ' ' !in it }

        // 5) one word the recognizer glued together ("fanspeed", "ecomode", "aquarter")
        for (k in tokens.indices) {
            val w = tokens[k]
            if (used[k] || w.length < 5 || w in Lexicon.stopwords) continue
            val split = splitGlued(w, singleWordKeys) ?: continue
            for ((key, conf) in split) Lexicon.phrases[key]?.forEach { c -> add(c, conf * 0.95, k, k + 1) }
            used[k] = true
        }

        // 6) fuzzy / sound-alike match for anything still unknown
        for (k in tokens.indices) {
            val w = tokens[k]
            if (used[k] || w in Lexicon.stopwords) continue
            var bestKey: String? = null
            var bestScore = 0.0
            for (key in singleWordKeys) {
                val s = Fuzzy.match(w, key)
                if (s > bestScore) { bestScore = s; bestKey = key }
            }
            for (key in Lexicon.colors.keys) {
                val s = Fuzzy.match(w, key)
                if (s > bestScore) { bestScore = s; bestKey = key }
            }
            if (bestKey != null) {
                val cTarget = Lexicon.colors[bestKey]
                if (cTarget != null) { color = cTarget; add(C.COLOR, bestScore, k, k + 1) }
                else Lexicon.phrases.getValue(bestKey).forEach { c -> add(c, bestScore, k, k + 1) }
                used[k] = true
            }
        }

        // implied percentages
        if (percent == null) {
            if (tags.any { it.concept == C.HALF }) percent = 50
            else if (tags.any { it.concept == C.QUARTER }) percent = 25
            else if (tags.any { it.concept == C.FULL }) percent = 100
        }

        val unexplained = tokens.indices.filter { !used[it] && tokens[it] !in Lexicon.stopwords }.map { tokens[it] }
        return Tagged(tokens.joinToString(" "), tokens, tags.sortedBy { it.start }, number, percent, color, unexplained)
    }
}
