package com.offline.bydvoice.platform

import android.accessibilityservice.AccessibilityService
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.offline.bydvoice.byd.Platform
import com.offline.bydvoice.service.KeyButtonService

/** Android-level actions used by some commands (screen, theme, cameras, apps). */
class AndroidPlatform(private val context: Context) : Platform {
    private val main = Handler(Looper.getMainLooper())

    /** Same broadcast diplus uses to switch the 360° camera view. */
    override fun broadcastCameraView(keyEvent: Int): Boolean {
        context.sendBroadcast(Intent("android.intent.action.AUTO_VIDEO_BUTTON").putExtra("android.intent.extra.KEY_EVENT", keyEvent))
        return true
    }

    override fun screen(on: Boolean): Boolean {
        if (on) { ScreenOffActivity.dismiss(context); return true }
        context.startActivity(Intent(context, ScreenOffActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    override fun splitScreen(): Boolean =
        KeyButtonService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) ?: false

    override fun brightness(night: Boolean): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val cr = context.contentResolver
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        return Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, if (night) 45 else 255)
    }

    /** diplus flips night mode through a hidden UiModeManager method and tells AMap too. */
    override fun theme(dark: Boolean): Boolean {
        val ui = context.getSystemService(UiModeManager::class.java)
        runCatching { ui.javaClass.getMethod("setNightModeActivated", Boolean::class.javaPrimitiveType).invoke(ui, dark) }
            .onFailure { Log.i(TAG, "setNightModeActivated unavailable: ${it.cause ?: it}") }
        runCatching { ui.nightMode = if (dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO }
        runCatching {
            context.sendBroadcast(Intent("AUTONAVI_STANDARD_BROADCAST_RECV")
                .setComponent(ComponentName("com.autonavi.amapauto", "com.autonavi.amapauto.adapter.internal.AmapAutoBroadcastReceiver"))
                .putExtra("KEY_TYPE", 10048).putExtra("EXTRA_DAY_NIGHT_MODE", if (dark) 2 else 1))
        }
        return ui.nightMode == (if (dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO)
    }

    override fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    override fun runLater(delayMs: Long, block: () -> Unit) { main.postDelayed(block, delayMs) }

    companion object { private const val TAG = "AndroidPlatform" }
}
