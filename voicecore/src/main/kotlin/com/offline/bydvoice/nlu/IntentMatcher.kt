package com.offline.bydvoice.nlu

import com.offline.bydvoice.command.CommandCatalog
import com.offline.bydvoice.command.CommandSpec
import com.offline.bydvoice.command.VehicleAction

/** Looks up launchable apps by spoken name. Android implementation lives in the service layer. */
fun interface AppDirectory {
    /** Returns (packageName, label) for the best match, or null. */
    fun find(spokenName: String): Pair<String, String>?
}

data class IntentMatch(
    val spec: CommandSpec,
    val tagged: Tagged,
    val action: VehicleAction,
    /** 0..1 — how sure we are this is the command. */
    val confidence: Double,
)

/**
 * Picks the command an utterance means.
 *
 * For each enabled command: every required concept group must be present (fuzzy hits count with
 * their confidence), no forbidden concept may be present, slots it needs must be filled. Survivors
 * are scored by how confident the hits were, how much of what was said the command accounts for
 * (coverage), and how specific it is, so "turn on DRIVER heating" beats "turn on seats heating".
 */
class IntentMatcher(
    private val specs: List<CommandSpec> = CommandCatalog.all,
    private val apps: AppDirectory? = null,
) {
    companion object {
        private const val FORBID_THRESHOLD = 0.8
        private const val MIN_GROUP_CONFIDENCE = 0.78
        private const val MIN_COVERAGE = 0.5
        private const val MIN_SCORE = 0.62
    }

    fun isCancel(t: Tagged): Boolean = t.has(C.CANCEL) && t.concepts.size <= 2

    /** Best command across several recognizer hypotheses (e.g. vocabulary-biased and free-form). */
    fun matchBest(hypotheses: List<String>, enabled: (String) -> Boolean = { true }): IntentMatch? =
        hypotheses.filter { it.isNotBlank() }.distinct()
            .mapNotNull { match(it, enabled) }
            .maxByOrNull { it.confidence }

    fun match(text: String, enabled: (String) -> Boolean = { true }): IntentMatch? {
        val t = Tagger.tag(text)
        if (t.isEmpty || isCancel(t)) return null

        var best: IntentMatch? = null
        for (spec in specs) {
            if (!enabled(spec.id) || spec.patterns.isEmpty()) continue
            val score = score(spec, t) ?: continue
            if (best == null || score > best.confidence) {
                val action = runCatching { spec.action(t) }.getOrNull() ?: continue
                best = IntentMatch(spec, t, action, score)
            }
        }
        if (best != null) return best.copy(confidence = best.confidence.coerceAtMost(1.0))

        return matchApp(t, enabled)
    }

    /** Null if the command is ruled out; otherwise a score (higher is better). */
    fun score(spec: CommandSpec, t: Tagged): Double? {
        if (spec.needsNumber && t.number == null) return null
        if (spec.needsColor && t.color == null) return null
        if (spec.forbid.any { t.conf(it) >= FORBID_THRESHOLD }) return null

        var bestPattern: Pair<Double, Int>? = null  // (avg confidence, groups)
        for (pattern in spec.patterns) {
            var sum = 0.0
            var ok = true
            for (group in pattern.groups) {
                val c = group.maxOf { t.conf(it) }
                if (c < MIN_GROUP_CONFIDENCE) { ok = false; break }
                sum += c
            }
            if (!ok) continue
            val avg = sum / pattern.groups.size
            val candidate = avg to pattern.groups.size
            if (bestPattern == null || candidate.first + 0.02 * candidate.second > bestPattern.first + 0.02 * bestPattern.second) {
                bestPattern = candidate
            }
        }
        val (avg, groups) = bestPattern ?: return null

        val content = t.concepts - CommandSpec.COMMON_OPTIONAL
        val explained = content.count { it in spec.explains }
        val coverage = if (content.isEmpty()) 1.0 else explained.toDouble() / content.size
        if (coverage < MIN_COVERAGE) return null

        // Unknown leftover words lower confidence a little (they might be the real meaning).
        val noise = t.unexplained.size.toDouble() / (t.tokens.size.coerceAtLeast(1))
        val score = avg * (0.55 + 0.45 * coverage) * (1.0 - 0.25 * noise) + 0.015 * groups
        return score.takeIf { it >= MIN_SCORE }
    }

    /** "open spotify" / "launch google maps": OPEN/LAUNCH + words nothing else understood. */
    private fun matchApp(t: Tagged, enabled: (String) -> Boolean): IntentMatch? {
        val directory = apps ?: return null
        if (!enabled(CommandCatalog.APP_LAUNCH_ID)) return null
        if (!(t.has(C.OPEN) || t.has(C.LAUNCH) || t.has(C.ON))) return null
        val name = t.unexplained.joinToString(" ").ifBlank { return null }
        val (pkg, label) = directory.find(name) ?: return null
        val spec = CommandCatalog.byId.getValue(CommandCatalog.APP_LAUNCH_ID)
        return IntentMatch(spec, t, VehicleAction.LaunchApp(pkg, label), 0.8)
    }
}
