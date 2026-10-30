package com.offline.bydvoice.platform

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat

/**
 * "Turn off screen": a black, minimum-brightness full-screen activity. Touch anywhere (or say
 * "turn on screen") to bring the screen back. Works on any head unit without system permissions.
 */
class ScreenOffActivity : Activity() {
    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 0.01f }
        setContentView(View(this).apply { setBackgroundColor(Color.BLACK); setOnClickListener { finish() } })
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        ContextCompat.registerReceiver(this, dismissReceiver, IntentFilter(ACTION_DISMISS), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(dismissReceiver) }
        super.onDestroy()
    }

    companion object {
        private const val ACTION_DISMISS = "com.offline.bydvoice.SCREEN_ON"
        fun dismiss(context: Context) = context.sendBroadcast(Intent(ACTION_DISMISS).setPackage(context.packageName))
    }
}
