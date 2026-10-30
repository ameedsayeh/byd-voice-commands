package com.offline.bydvoice.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.offline.bydvoice.state.AppSettings
import com.offline.bydvoice.service.VoiceService

/**
 * After boot: only start continuous listening when HANDS-FREE mode is enabled. In the default
 * push-to-talk mode there is nothing to start here — the mic opens only on a trigger, and the
 * always-available trigger (the steering-wheel button) is handled by the accessibility service,
 * which the system starts on its own. This keeps the mic (and battery) idle until you ask for it.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                if (AppSettings.get(context).handsFree.value) {
                    Log.i("BootReceiver", "boot: hands-free -> starting continuous listener")
                    runCatching { VoiceService.start(context) }
                } else {
                    Log.i("BootReceiver", "boot: push-to-talk -> mic stays idle until triggered")
                }
            }
        }
    }
}
