package com.offline.bydvoice

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.offline.bydvoice.platform.CarPermissions
import com.offline.bydvoice.service.KeyButtonService
import com.offline.bydvoice.service.TriggerKeys
import com.offline.bydvoice.service.VoiceService
import com.offline.bydvoice.state.AppSettings
import com.offline.bydvoice.state.VoiceBus
import com.offline.bydvoice.ui.home.HomeActions
import com.offline.bydvoice.ui.home.HomeModel
import com.offline.bydvoice.ui.home.HomeScreen
import com.offline.bydvoice.ui.home.Perms
import com.offline.bydvoice.ui.theme.BydTheme
import com.offline.bydvoice.voice.SpeechModels

class MainActivity : ComponentActivity() {

    private val settings by lazy { AppSettings.get(this) }
    /** Bumped on resume / permission results so the screen re-reads permission state. */
    private val refresh = mutableIntStateOf(0)

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh.intValue++
        CarPermissions.log(this)
        // Tapped "Car controls" but Android no longer shows the dialog (denied before): open the app's
        // permission page so they can be switched on there.
        if (carFromChip && CarPermissions.missing(this).isNotEmpty()) openAppDetails()
        carFromChip = false
    }
    private var carFromChip = false

    private val learned = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { refresh.intValue++ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val actions = HomeActions(
            speak = { if (hasMic()) VoiceService.trigger(this) else requestCore() },
            tryCommand = { spec -> testText(spec.examples.first()) },
            runText = { text -> testText(text) },
            toggleCommands = { ids, on -> settings.setEnabled(ids, on) },
            setHandsFree = { on ->
                settings.setHandsFree(on)
                if (on && hasMic()) VoiceService.start(this) else VoiceService.stop(this)
            },
            setVoiceReplies = settings::setVoiceReplies,
            setDriverOnLeft = settings::setDriverOnLeft,
            setTiming = settings::setTiming,
            fixMic = ::requestCore,
            fixCar = {
                val missing = CarPermissions.missing(this)
                if (missing.isEmpty()) openAppDetails() else { carFromChip = true; askPermissions.launch(missing.toTypedArray()) }
            },
            fixOverlay = { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) },
            fixNotifications = {
                if (Build.VERSION.SDK_INT >= 33) askPermissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            },
            fixSystemSettings = { open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))) },
            fixBattery = { openBatteryExemption() },
            fixAccessibility = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            learnButton = { TriggerKeys.setLearnMode(this, true); refresh.intValue++ },
        )

        setContent {
            BydTheme {
                val voice by VoiceBus.state.collectAsState()
                val history by VoiceBus.history.collectAsState()
                val disabled by settings.disabledCommands.collectAsState()
                val handsFree by settings.handsFree.collectAsState()
                val replies by settings.voiceReplies.collectAsState()
                val lhd by settings.driverOnLeft.collectAsState()
                val timing by settings.timing.collectAsState()
                refresh.intValue // read so permission state recomposes on resume
                HomeScreen(
                    HomeModel(voice, history, disabled, handsFree, replies, lhd, perms(),
                        TriggerKeys.triggerCodes(this).joinToString(", "), TriggerKeys.isLearnMode(this), timing),
                    actions,
                )
            }
        }
        if (!hasMic() || CarPermissions.missing(this).isNotEmpty()) requestCore()
        CarPermissions.log(this)
        SpeechModels.ensure(this)
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(this, learned, IntentFilter(TriggerKeys.ACTION_LEARNED), ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh.intValue++
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(learned) }
    }

    /** Microphone, notifications and the car's vehicle permissions, in one go on first start. */
    private fun requestCore() {
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            addAll(CarPermissions.missing(this@MainActivity))
        }
        askPermissions.launch(wanted.toTypedArray())
    }

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun perms() = Perms(
        mic = hasMic(),
        overlay = Settings.canDrawOverlays(this),
        notifications = NotificationManagerCompat.from(this).areNotificationsEnabled(),
        systemSettings = Settings.System.canWrite(this),
        battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
        accessibility = KeyButtonService.instance != null || accessibilityEnabled(),
        car = if (CarPermissions.isCar(this)) CarPermissions.missing(this).isEmpty() else null,
    )

    private fun accessibilityEnabled(): Boolean {
        val me = ComponentName(this, KeyButtonService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    @SuppressLint("BatteryLife")
    private fun openBatteryExemption() = open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))

    private fun openAppDetails() = open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

    /** Run a command by its text, the same path the recognizer uses (used by Try + the quick grid). */
    private fun testText(text: String) {
        if (hasMic()) {
            startForegroundService(Intent(this, VoiceService::class.java)
                .setAction(VoiceService.ACTION_TEST_TEXT).putExtra(VoiceService.EXTRA_TEXT, text))
        } else requestCore()
    }

    private fun open(intent: Intent) { runCatching { startActivity(intent) } }
}
