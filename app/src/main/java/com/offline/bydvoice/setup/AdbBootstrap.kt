package com.offline.bydvoice.setup

import android.content.Context
import android.util.Log
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException

/**
 * Self-contained privilege bootstrap — the app sets *itself* up, no Di+ (or any other tool) needed.
 *
 * On a stock, unrooted head unit an app can't grant itself the elevated bits it needs for the
 * steering-wheel button and always-on background run. Di+ gets them by connecting to the car's OWN
 * adb daemon on `localhost:5555` and running shell commands as the `shell` user. This does the same
 * thing from inside our app, using an embedded ADB client ([dadb]):
 *
 *  1. connect to `localhost:5555` (you approve the one-time "Allow debugging?" prompt on the car),
 *  2. `pm grant` ourselves `WRITE_SECURE_SETTINGS`,
 *  3. enable our accessibility service — the wheel-button capture, which the system also auto-starts
 *     on every boot, so the app is live in the background without being launched,
 *  4. whitelist us from battery optimisation and allow the voice overlay.
 *
 * It does **not** unlock the signature-level vehicle API (that needs BYD's platform key) — car
 * control still routes through [com.offline.bydvoice.byd.BydAssistant]. What it unlocks is the wheel
 * button and auto-start, so you don't have to trust a third-party tool with your head unit.
 *
 * The RSA key we authenticate with is generated once and kept in the app's private storage, so the
 * car only ever asks you to authorise this app, once.
 */
object AdbBootstrap {

    sealed interface State {
        data object Idle : State
        data class Running(val step: String) : State
        data class Done(val log: List<String>) : State
        data class Failed(val reason: String, val log: List<String>) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Run the bootstrap. Safe to run again — every step is idempotent. */
    suspend fun run(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val pkg = app.packageName
        val accService = "$pkg/com.offline.bydvoice.service.KeyButtonService"
        val log = mutableListOf<String>()

        _state.value = State.Running("Connecting to the car…")
        val dadb = try {
            Dadb.create(HOST, PORT, loadKeyPair(app), connectTimeout = CONNECT_MS, socketTimeout = SOCKET_MS)
        } catch (e: Exception) {
            Log.e(TAG, "connect failed", e)
            _state.value = State.Failed(connectHint(e), log)
            return@withContext
        }

        try {
            dadb.use { d ->
                // Report each step as it runs; collect a short log for troubleshooting.
                fun sh(step: String, cmd: String): String {
                    _state.value = State.Running(step)
                    val r = d.shell(cmd)
                    val out = r.allOutput.trim()
                    log += "$ $cmd  (exit=${r.exitCode}) ${out.take(140)}".trim()
                    Log.i(TAG, "'$cmd' -> exit=${r.exitCode} $out")
                    return r.output.trim()
                }

                // 1. From now on the app can write secure settings on its own.
                sh("Granting secure settings…", "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")

                // 2. Enable the wheel-button accessibility service, merged into any existing list so
                //    we don't clobber another enabled service. The system re-enables it on each boot.
                val current = sh("Checking accessibility services…", "settings get secure enabled_accessibility_services")
                val merged = mergeService(current, accService)
                sh("Enabling the wheel button…", "settings put secure enabled_accessibility_services $merged")
                sh("Enabling the wheel button…", "settings put secure accessibility_enabled 1")

                // 3. Keep us running in the background / let us auto-start.
                sh("Allowing background run…", "dumpsys deviceidle whitelist +$pkg")

                // 4. Allow the voice overlay without a separate on-screen prompt (best effort).
                sh("Allowing the voice overlay…", "appops set $pkg SYSTEM_ALERT_WINDOW allow")
            }
            _state.value = State.Done(log)
            Log.i(TAG, "bootstrap done")
        } catch (e: Exception) {
            Log.e(TAG, "bootstrap failed", e)
            _state.value = State.Failed(e.message ?: "Setup failed on the car.", log)
        }
    }

    fun reset() { _state.value = State.Idle }

    /** Add our service to the colon-separated secure list if it isn't already there. */
    private fun mergeService(current: String, service: String): String {
        val parts = current.split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
        return if (parts.any { it.equals(service, ignoreCase = true) }) parts.joinToString(":")
        else (parts + service).joinToString(":")
    }

    private fun connectHint(e: Exception): String = when {
        e is ConnectException || e.message?.contains("ECONNREFUSED", ignoreCase = true) == true ->
            "Can't reach the car's ADB on localhost:$PORT. Turn on Developer options + USB/ADB debugging " +
                "on the head unit, then try again."
        e.message?.contains("timed out", ignoreCase = true) == true ->
            "Timed out reaching the car's ADB on localhost:$PORT. Make sure ADB over network is on, then retry."
        else -> "Couldn't connect to the car's ADB: ${e.message ?: e.javaClass.simpleName}."
    }

    /** One stable RSA key for this install, kept private — the car authorises this app once. */
    private fun loadKeyPair(context: Context): AdbKeyPair {
        val dir = File(context.filesDir, "adb").apply { mkdirs() }
        val priv = File(dir, "adbkey")
        val pub = File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    private const val TAG = "AdbBootstrap"
    private const val HOST = "localhost"
    private const val PORT = 5555
    private const val CONNECT_MS = 15_000
    // Generous: the first connect blocks on the read while you tap "Allow debugging?" on the car.
    private const val SOCKET_MS = 90_000
}
