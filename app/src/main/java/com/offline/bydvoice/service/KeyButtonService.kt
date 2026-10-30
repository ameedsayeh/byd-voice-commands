package com.offline.bydvoice.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Intercepts the steering-wheel mic button (and other configured keys) and uses it as an alternate
 * trigger for the listener — the same mechanism diplus uses: an AccessibilityService with
 * canRequestFilterKeyEvents. Returning true from onKeyEvent CONSUMES the key, which overrides the
 * car's native voice assistant so only our listener reacts.
 *
 * Requires the user to enable this service in Settings > Accessibility (it can't be auto-granted).
 */
class KeyButtonService : AccessibilityService() {

    private val tag = "KeyButtonService"

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode

        // Learn mode: capture the next pressed key as the trigger, then consume it.
        if (TriggerKeys.isLearnMode(this)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                TriggerKeys.setTriggerCode(this, code)
                TriggerKeys.setLearnMode(this, false)
                sendBroadcast(
                    Intent(TriggerKeys.ACTION_LEARNED)
                        .setPackage(packageName)
                        .putExtra(TriggerKeys.EXTRA_CODE, code)
                )
                Log.i(tag, "learned trigger keycode = $code")
            }
            return true // swallow both down and up during learning
        }

        if (code in TriggerKeys.triggerCodes(this)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                Log.d(tag, "trigger key $code -> toggle listen")
                VoiceService.trigger(this)
            }
            return true // consume -> native assistant does not fire
        }
        return super.onKeyEvent(event)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* keys only */ }
    override fun onInterrupt() {}

    companion object {
        /** Live instance while enabled — also used for the "split screen" global action. */
        @Volatile var instance: KeyButtonService? = null
    }
}
