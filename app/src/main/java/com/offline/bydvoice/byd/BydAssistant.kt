package com.offline.bydvoice.byd

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import com.byd.autovoice.automata.AutomatedExecuteAidlInterface

/**
 * Hands a command to the BYD voice assistant (com.byd.autovoice), which runs it with its own
 * platform permissions. This is how the app controls the car on a stock, unrooted head unit: our
 * app holds no vehicle permissions, but the assistant does.
 *
 * Mechanism (reverse-engineered from the assistant):
 *  1. arm its automation listener with the `com.byd.AUTOMATED_TEST_TASKS` broadcast
 *     (a normal permission this app declares),
 *  2. bind its exported `AutomataTestService` and call `c3(0, "<chinese command>")`,
 *  3. the assistant executes the command as if it had recognized it.
 * Right after, we fire the assistant's stop-speech broadcast to cut its spoken Chinese reply.
 */
class BydAssistant(context: Context) {

    private val app = context.applicationContext
    private val thread = HandlerThread("byd-assistant").apply { start() }
    private val h = Handler(thread.looper)

    @Volatile private var svc: AutomatedExecuteAidlInterface? = null
    @Volatile private var binding = false
    private val pending = ArrayDeque<String>()

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            svc = AutomatedExecuteAidlInterface.Stub.asInterface(binder)
            binding = false
            Log.i(TAG, "assistant connected")
            h.post { flush() }
        }
        override fun onServiceDisconnected(name: ComponentName?) { svc = null }
    }

    /** True on a DiLink head unit (the assistant is installed). */
    fun isAvailable(): Boolean =
        runCatching { app.packageManager.getPackageInfo(ASSISTANT_PKG, 0); true }.getOrDefault(false)

    /** Queue a Chinese command phrase for the assistant to execute. */
    fun run(phrase: String) = h.post {
        pending.addLast(phrase)
        arm()
        if (svc != null) flush() else bind()
    }

    private fun bind() {
        if (binding) return
        binding = true
        val i = Intent().setClassName(ASSISTANT_PKG, SERVICE)
        val ok = runCatching { app.bindService(i, conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        Log.i(TAG, "bindService -> $ok")
        if (!ok) binding = false
    }

    private fun flush() {
        val s = svc ?: return
        while (pending.isNotEmpty()) {
            val cmd = pending.removeFirst()
            runCatching { s.c3(0, cmd) }
                .onSuccess { Log.i(TAG, "sent '$cmd'"); silence() }
                .onFailure { Log.e(TAG, "c3 failed for '$cmd'", it); svc = null; pending.addFirst(cmd); bind(); return }
        }
    }

    /** Register the assistant's automation listener (idempotent). */
    private fun arm() = runCatching {
        app.sendBroadcast(Intent(ARM_ACTION).setPackage(ASSISTANT_PKG).putExtra("TEST_TASKS", "1"))
    }.onFailure { Log.w(TAG, "arm failed", it) }

    /** Cut the assistant's spoken reply shortly after the command has run. */
    private fun silence() = h.postDelayed({
        runCatching { app.sendBroadcast(Intent(STOP_SPEECH)) }
    }, SILENCE_DELAY_MS)

    companion object {
        private const val TAG = "BydAssistant"
        private const val ASSISTANT_PKG = "com.byd.autovoice"
        private const val SERVICE = "com.byd.autovoice_common.utils.automated.service.AutomataTestService"
        private const val ARM_ACTION = "com.byd.AUTOMATED_TEST_TASKS"
        private const val STOP_SPEECH = "android.media.AudioRecordControl.STOP_AUTO_VOICE_ACTION"
        private const val SILENCE_DELAY_MS = 600L
    }
}
