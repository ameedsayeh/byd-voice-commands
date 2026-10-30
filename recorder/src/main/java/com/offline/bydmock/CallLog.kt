package com.offline.bydmock

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Append-only record of BYD API calls received from the instrumented impl. Kept in memory and
 * mirrored to a file so the UI survives process restarts (a manifest receiver may be woken without
 * the activity).
 */
object CallLog {
    private val entries = ArrayList<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var file: File? = null

    @Synchronized
    fun init(context: Context) {
        if (file == null) {
            file = File(context.filesDir, "byd_calls.log")
            file?.takeIf { it.exists() }?.readLines()?.let { entries.addAll(it) }
        }
    }

    @Synchronized
    fun add(device: String, method: String, args: String) {
        val line = "${fmt.format(Date())}  $device.$method($args)"
        entries.add(line)
        runCatching { file?.appendText(line + "\n") }
    }

    @Synchronized
    fun snapshot(): List<String> = ArrayList(entries)

    @Synchronized
    fun clear() {
        entries.clear()
        runCatching { file?.writeText("") }
    }
}
