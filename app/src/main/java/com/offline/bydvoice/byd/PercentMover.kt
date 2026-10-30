package com.offline.bydvoice.byd

import android.content.Context
import android.hardware.bydauto.bodywork.AbsBYDAutoBodyworkListener
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs

/**
 * Moves a window (area 1–4) or the roof (5) to a target percent, the way diplus does: start the
 * motion, listen to onWindowOpenPercentChanged, and send STOP (3) just before the target to
 * absorb the motor's overrun. A timeout always sends STOP so nothing keeps moving.
 */
class PercentMover(private val context: Context) {
    private val tag = "PercentMover"
    private val main = Handler(Looper.getMainLooper())

    fun moveTo(area: Int, target: Int): Boolean {
        val body = BYDAutoBodyworkDevice.getInstance(context) ?: return false
        val current = runCatching { body.getWindowOpenPercent(area) }.getOrDefault(-1)
        if (current in 0..100 && abs(current - target) <= 3) return true
        val opening = current < 0 || target > current
        val stopAt = if (opening) target - OVERRUN else target + OVERRUN

        val listener = object : AbsBYDAutoBodyworkListener() {
            var done = false
            override fun onWindowOpenPercentChanged(area0: Int, percent: Int) {
                if (done || area0 != area) return
                if ((opening && percent >= stopAt) || (!opening && percent <= stopAt)) finish()
            }
            fun finish() {
                if (done) return
                done = true
                send(body, area, STOP)
                runCatching { body.unregisterListener(this) }
                Log.i(tag, "area $area stopped near $target%")
            }
        }
        body.registerListener(listener)
        send(body, area, if (opening) OPEN else CLOSE)
        main.postDelayed({ listener.finish() }, TIMEOUT_MS)
        return true
    }

    companion object {
        const val OPEN = 1
        const val CLOSE = 2
        const val STOP = 3
        private const val OVERRUN = 4
        private const val TIMEOUT_MS = 25_000L

        /** setAllWindowState takes FL, FR, RL, RR; 0 leaves a window alone. Area 5 = roof. */
        fun send(body: BYDAutoBodyworkDevice, area: Int, code: Int): Int = when (area) {
            1 -> body.setAllWindowState(code, 0, 0, 0)
            2 -> body.setAllWindowState(0, code, 0, 0)
            3 -> body.setAllWindowState(0, 0, code, 0)
            4 -> body.setAllWindowState(0, 0, 0, code)
            5 -> body.setMoonRoofState(code)
            else -> -1
        }
    }
}
