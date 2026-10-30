package com.offline.bydmock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * The "mock BYD API service": receives every call the instrumented android.hardware.bydauto.* impl
 * forwards, records it, and logs it (tag BYDMOCK) so calls are visible via `adb logcat -s BYDMOCK`.
 */
class RecordReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val device = intent.getStringExtra("device") ?: "?"
        val method = intent.getStringExtra("method") ?: "?"
        val args = intent.getStringExtra("args") ?: ""
        CallLog.init(context.applicationContext)
        CallLog.add(device, method, args)
        Log.i("BYDMOCK", "$device.$method($args)")
    }

    companion object {
        const val ACTION = "com.offline.bydmock.RECORD"
    }
}
