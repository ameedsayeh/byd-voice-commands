package com.offline.bydvoice.eval

import com.offline.bydvoice.nlu.IntentMatcher
import com.offline.bydvoice.nlu.Lexicon
import com.offline.bydvoice.nlu.Tagger
import com.offline.bydvoice.command.CommandCatalog
import java.io.File

/**
 * Offline recognizer comparison: scores recognizer transcripts by whether they trigger the same
 * command (and the same values) as the clean phrase would.
 *
 *   gradle :voicecore:nluEval --args="examples out.txt"  writes every catalog example phrase
 *   gradle :voicecore:nluEval --args="catalog"           prints the command list as a markdown table
 *   gradle :voicecore:nluEval --args="score results.tsv" TSV: key, clean phrase, engine, hypothesis...
 *   gradle :voicecore:nluEval --args="explain 'some text'"  shows how a transcript is tagged and matched
 *
 * Several hypotheses on one row are combined with matchBest, as the app does.
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "examples" -> File(args[1]).writeText(CommandCatalog.all.flatMap { it.examples }.joinToString("\n"))
        "phrases" -> File(args[1]).writeText(Lexicon.phrases.keys.sorted().joinToString("\n"))
        "catalog" -> CommandCatalog.all.groupBy { it.category }.forEach { (cat, specs) ->
            println("| ${cat.title} | ${specs.size} | ${specs.joinToString(", ") { it.title }} |")
        }
        "score" -> score(File(args[1]))
        "explain" -> args.drop(1).forEach { println("$it\n  ${Tagger.tag(it)}\n  -> ${IntentMatcher().match(it)?.action}") }
        else -> error("usage: examples <out> | phrases <out> | catalog | score <tsv> | explain <text>...")
    }
}

private fun score(file: File) {
    val matcher = IntentMatcher()
    val rows = file.readLines().filter { it.isNotBlank() }.map { it.split('\t') }
    val byEngine = LinkedHashMap<String, MutableList<String>>()
    val correct = HashMap<String, Int>()
    for (row in rows) {
        val (key, clean, engine) = row
        val expected = matcher.match(clean)?.action ?: error("clean phrase doesn't match: $clean")
        val got = matcher.matchBest(row.drop(3))
        val misses = byEngine.getOrPut(engine) { ArrayList() }
        if (got?.action == expected) correct.merge(engine, 1, Int::plus)
        else misses += "  $key  '$clean'  heard ${row.drop(3).joinToString(" | ") { "'$it'" }} -> ${got?.action ?: "nothing"}"
    }
    for ((engine, misses) in byEngine) {
        val total = rows.count { it[2] == engine }
        println("%-28s %3d / %3d  (%.0f%%)".format(engine, correct[engine] ?: 0, total, 100.0 * (correct[engine] ?: 0) / total))
    }
    for ((engine, misses) in byEngine) {
        if (misses.isEmpty()) continue
        println("\n$engine misses:")
        misses.forEach(::println)
    }
}
