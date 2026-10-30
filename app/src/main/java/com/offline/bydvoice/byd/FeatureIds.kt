package com.offline.bydvoice.byd

import android.util.Log

/**
 * Feature IDs live as static ints on android.hardware.bydauto.BYDAutoFeatureIds (and nested classes
 * like BYDAutoFeatureIds$Light) on the car. They MUST be read reflectively at runtime: referencing
 * them from code would inline the stub's value at compile time. diplus does the same thing (named
 * field first, raw int fallback for older firmware).
 */
object FeatureIds {
    private const val TAG = "FeatureIds"
    private const val ROOT = "android.hardware.bydauto.BYDAutoFeatureIds"

    private val cache = HashMap<String, Int?>()

    /** Named field (optionally inside a nested class), else [fallback]. */
    fun named(field: String, nested: String? = null, fallback: Int? = null): Int? = cache.getOrPut("$nested.$field") {
        val cls = runCatching { Class.forName(if (nested == null) ROOT else "$ROOT$$nested") }.getOrNull()
        val v = runCatching { cls?.getField(field)?.getInt(null) }.getOrNull()
        if (v == null) Log.i(TAG, "$field not found, using fallback $fallback")
        v ?: fallback
    }

    /**
     * For features diplus never touched: scan every static int on BYDAutoFeatureIds (+ nested
     * classes) for a name matching [include]. The chosen name is logged so it can be verified on the
     * car (adb logcat -s FeatureIds).
     */
    fun probe(include: Regex, exclude: Regex? = null): Pair<String, Int>? = probeCache.getOrPut(include.pattern) {
        val root = runCatching { Class.forName(ROOT) }.getOrNull() ?: return@getOrPut null
        val classes = listOf(root) + root.declaredClasses
        val hits = classes.flatMap { c ->
            c.fields.filter { f ->
                f.type == Int::class.javaPrimitiveType &&
                    include.containsMatchIn(f.name) && (exclude == null || !exclude.containsMatchIn(f.name))
            }.map { "${c.simpleName}.${it.name}" to it.getInt(null) }
        }.sortedBy { it.first.length }
        Log.i(TAG, "probe /${include.pattern}/ -> ${hits.take(5)}")
        hits.firstOrNull()
    }

    private val probeCache = HashMap<String, Pair<String, Int>?>()
}
