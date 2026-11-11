package com.offline.bydvoice.testhook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.offline.bydvoice.byd.BydHal

/**
 * DEBUG builds only. One-shot on-car probe for the direct HAL channel ([BydHal]). It never touches
 * the assistant. Reads are side-effect-free; the write test actuates the car and is opt-in.
 *
 *   # READ probe only (safe — proves the channel is reachable, nothing moves):
 *   adb shell am broadcast -a com.offline.bydvoice.HAL_PROBE -n com.offline.bydvoice/.testhook.HalProbeReceiver
 *
 *   # READ + a reversible WRITE test (default: AC power -> 1). Override with extras:
 *   adb shell am broadcast -a com.offline.bydvoice.HAL_PROBE -n com.offline.bydvoice/.testhook.HalProbeReceiver \
 *       --ez write true --es feature AC_POWER_STATE_SET --es domain Ac --ei value 1
 *
 * Watch results:  adb logcat -s BydHal:V HalProbe:V
 */
class HalProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        Log.i(TAG, "=== HAL probe start ===")

        // 1) READ benign status features (no actuation) -> proves reachability.
        data class Read(val label: String, val device: String, val name: String, val domain: String)
        val reads = listOf(
            Read("AC power", BydHal.Dev.AC, "AC_POWER_STATE", "Ac"),
            Read("AC compressor", BydHal.Dev.AC, "AC_COMPRESSOR_MODE", "Ac"),
            Read("AC ctrl mode", BydHal.Dev.AC, "AC_CTRL_MODE", "Ac"),
        )
        var reachable = false
        for (r in reads) {
            val fid = BydHal.featureId(r.name, r.domain)
            if (fid == null) { Log.w(TAG, "read ${r.label}: '${r.name}' not resolved"); continue }
            val v = BydHal.get(ctx, r.device, fid)
            Log.i(TAG, "read ${r.label} ${r.name}(0x${Integer.toHexString(fid)}) -> ${v ?: "err/unreachable"}")
            if (v != null) reachable = true
        }
        Log.i(TAG, "HAL reachable via reads: $reachable")

        // 2) OPTIONAL reversible WRITE test (opt-in). Default toggles AC power on.
        if (intent.getBooleanExtra("write", false)) {
            val name = intent.getStringExtra("feature") ?: "AC_POWER_STATE_SET"
            val domain = intent.getStringExtra("domain") ?: "Ac"
            val device = intent.getStringExtra("device") ?: BydHal.Dev.AC
            val value = intent.getIntExtra("value", 1)
            val fid = BydHal.featureId(name, domain)
            if (fid == null) {
                Log.w(TAG, "write test: '$name' not resolved")
            } else {
                val status = BydHal.set(ctx, device, fid, value)
                val verdict = when (status) {
                    0 -> "OK"
                    BydHal.PERM_DENY -> "PERMISSION DENIED"
                    BydHal.HAL_ERR -> "HAL error/unsupported"
                    else -> "status=$status"
                }
                Log.i(TAG, "WRITE $name(0x${Integer.toHexString(fid)})=$value on $device -> $verdict ($status)")
            }
        }
        Log.i(TAG, "=== HAL probe done ===")
    }

    companion object { private const val TAG = "HalProbe" }
}
