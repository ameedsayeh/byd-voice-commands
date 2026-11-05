package com.offline.bydbridge

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.byd.autovoice.automata.AutomatedExecuteAidlInterface

/**
 * Hands one command string to the BYD voice assistant's automation service. The assistant runs it
 * as if recognized, using its own (platform) permissions — so this app never calls the vehicle API.
 *
 *   am start -n com.offline.bydbridge/.BridgeActivity --es cmd "打开空调"
 */
class BridgeActivity : Activity() {

    private val tag = "BydBridge"
    private var cmd = "打开空调"
    private val main = Handler(Looper.getMainLooper())

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = AutomatedExecuteAidlInterface.Stub.asInterface(binder)
            try {
                svc.c3(0, cmd)
                Log.i(tag, "c3(0, \"$cmd\") sent to assistant")
                toast("Sent to assistant: $cmd")
            } catch (t: Throwable) {
                Log.e(tag, "c3 failed", t)
                toast("c3 failed: ${t.message}")
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cmd = intent.getStringExtra("cmd") ?: cmd
        Log.i(tag, "command = \"$cmd\"")

        // 1) Arm the assistant's automation listener (it registers it on this broadcast).
        runCatching {
            sendBroadcast(
                Intent("com.byd.AUTOMATED_TEST_TASKS")
                    .setPackage("com.byd.autovoice")
                    .putExtra("TEST_TASKS", "1")
            )
            Log.i(tag, "arm broadcast sent")
        }.onFailure { Log.e(tag, "arm broadcast failed", it) }

        // 2) After the listener is registered, bind the service and call c3 on connect.
        main.postDelayed({
            val i = Intent().setClassName(
                "com.byd.autovoice",
                "com.byd.autovoice_common.utils.automated.service.AutomataTestService",
            )
            val bound = runCatching { bindService(i, conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
            Log.i(tag, "bindService -> $bound")
            if (!bound) toast("bind failed — assistant service not reachable")
        }, 900)

        main.postDelayed({ runCatching { unbindService(conn) }; finish() }, 6000)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
