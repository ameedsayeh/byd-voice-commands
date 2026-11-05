package com.offline.bydvoice.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Opens the mic for one command when triggered from outside — the steering-wheel button route.
 *
 * On BYD head units the wheel "voice" button is reserved by the native assistant and isn't
 * delivered as a normal key event, so an accessibility service can't catch it. The working trick
 * (the same one BYD Connect uses) is to let a button tool that *can* capture it — Di+ — fire a
 * trigger at us:
 *
 *   am broadcast -a com.offline.bydvoice.action.START_LISTEN
 *
 * Starting a microphone foreground service from a background broadcast is restricted on Android 12+,
 * so if this path is blocked, bind the button to the service directly instead (shell-started, exempt):
 *   am start-foreground-service -n com.offline.bydvoice/.service.VoiceService -a com.offline.bydvoice.action.TRIGGER_LISTEN
 */
class TriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("TriggerReceiver", "trigger: ${intent.action}")
        runCatching { VoiceService.trigger(context) }
            .onFailure { Log.e("TriggerReceiver", "failed to start listen", it) }
    }
}
