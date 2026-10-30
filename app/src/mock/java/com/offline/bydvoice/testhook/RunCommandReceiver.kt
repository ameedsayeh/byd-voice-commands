package com.offline.bydvoice.testhook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.offline.bydvoice.byd.BydController
import com.offline.bydvoice.command.CommandCatalog
import com.offline.bydvoice.nlu.IntentMatcher
import com.offline.bydvoice.platform.AndroidPlatform
import com.offline.bydvoice.service.VoiceService

/**
 * Mock flavor only (never in the car build). Drive the assistant from adb:
 *
 *   # full pipeline from text (NLU → BYD call → spoken reply + overlay)
 *   adb shell am broadcast -n com.offline.bydvoice.mock/com.offline.bydvoice.testhook.RunCommandReceiver --es text "turn on the ac"
 *   # full pipeline from a 16 kHz mono WAV (recognition → NLU → BYD call → reply)
 *   adb shell am broadcast -n ... --es audio /sdcard/Download/cmd.wav
 *   # same, but through hands-free standby ("Hi BYD …" wake word spotting)
 *   adb shell am broadcast -n ... --es audio /sdcard/Download/hi.wav --ez wake true
 *   # every catalog command's first example through NLU + BYD (no speech)
 *   adb shell am broadcast -n ... --es id ALL
 */
class RunCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("text")?.let { text ->
            context.startForegroundService(Intent(context, VoiceService::class.java)
                .setAction(VoiceService.ACTION_TEST_TEXT).putExtra(VoiceService.EXTRA_TEXT, text))
            return
        }
        intent.getStringExtra("audio")?.let { path ->
            context.startForegroundService(Intent(context, VoiceService::class.java)
                .setAction(VoiceService.ACTION_TEST_AUDIO).putExtra(VoiceService.EXTRA_PATH, path)
                .putExtra(VoiceService.EXTRA_WAKE, intent.getBooleanExtra("wake", false)))
            return
        }
        val id = intent.getStringExtra("id") ?: return
        val controller = BydController(context.applicationContext, AndroidPlatform(context.applicationContext))
        val matcher = IntentMatcher()
        val specs = if (id == "ALL") CommandCatalog.all.filter { it.patterns.isNotEmpty() && it.id != "screen_off" } else CommandCatalog.all.filter { it.id == id }
        var ok = 0
        for (spec in specs) {
            val example = spec.examples.first()
            val match = matcher.match(example)
            if (match?.spec?.id != spec.id) { Log.w(TAG, "NLU MISMATCH '$example' -> ${match?.spec?.id} (want ${spec.id})"); continue }
            val outcome = controller.execute(match.action)
            val reply = spec.reply(match.tagged, outcome)
            if (outcome.ok || outcome.value != null) ok++
            Log.i(TAG, "${spec.id} '$example' -> ${match.action} ok=${outcome.ok} reply='$reply'")
        }
        Log.i(TAG, "done: $ok/${specs.size} commands executed OK")
    }

    companion object { private const val TAG = "RunCommand" }
}
