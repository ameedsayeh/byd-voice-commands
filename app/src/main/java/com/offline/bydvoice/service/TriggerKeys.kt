package com.offline.bydvoice.service

import android.content.Context

/**
 * Which hardware key(s) toggle the listener, persisted so you can "learn" your car's button.
 *
 * Defaults cover the usual suspects on DiLink units: the generic voice-assist key, headset hook,
 * and the custom steering-wheel keycodes (291/292/293) observed in diplus. Use the "Learn button"
 * flow in the app to capture whatever your wheel's mic button actually sends.
 */
object TriggerKeys {
    private const val PREFS = "trigger_keys"
    private const val KEY_SET = "codes"
    private const val KEY_LEARN = "learn_mode"

    // KEYCODE_VOICE_ASSIST=231, KEYCODE_HEADSETHOOK=79, BYD wheel customs 291/292/293.
    private val DEFAULTS = setOf(231, 79, 291, 292, 293)

    fun triggerCodes(context: Context): Set<Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_SET, null) ?: return DEFAULTS
        return stored.mapNotNull { it.toIntOrNull() }.toSet().ifEmpty { DEFAULTS }
    }

    fun setTriggerCode(context: Context, code: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_SET, setOf(code.toString())).apply()
    }

    fun isLearnMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LEARN, false)

    fun setLearnMode(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LEARN, on).apply()
    }

    const val ACTION_LEARNED = "com.offline.bydvoice.action.KEY_LEARNED"
    const val EXTRA_CODE = "code"
}
