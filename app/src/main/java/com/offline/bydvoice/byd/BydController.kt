package com.offline.bydvoice.byd

import android.content.Context
import android.hardware.bydauto.BYDAutoEventValue
import android.hardware.bydauto.ac.BYDAutoAcDevice
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice
import android.hardware.bydauto.doorlock.BYDAutoDoorLockDevice
import android.hardware.bydauto.doormirror.BYDAutoRearViewMirrorDevice
import android.hardware.bydauto.energy.BYDAutoEnergyDevice
import android.hardware.bydauto.light.BYDAutoLightDevice
import android.hardware.bydauto.sensor.BYDAutoSensorDevice
import android.hardware.bydauto.setting.BYDAutoSettingDevice
import android.hardware.bydauto.wiper.BYDAutoWiperDevice
import android.util.Log
import com.offline.bydvoice.command.Motion
import com.offline.bydvoice.command.Outcome
import com.offline.bydvoice.command.Seats
import com.offline.bydvoice.command.VehicleAction
import com.offline.bydvoice.command.VehicleAction.*
import com.offline.bydvoice.command.WindowTarget
import kotlin.math.roundToInt

/**
 * Executes [VehicleAction]s against the BYD DiLink API. Every mapping (method + argument values)
 * comes from diplus's own command executor — see docs/BYD_API_MAPPING.md. On a device without the
 * BYD framework every call fails safely with Outcome(ok = false).
 */
class BydController(
    private val context: Context,
    private val platform: Platform,
    /** Left-hand drive: driver window is front-left (area 1). */
    private val driverOnLeft: () -> Boolean = { true },
) {
    private val tag = "BydController"

    private val ac get() = BYDAutoAcDevice.getInstance(context)
    private val body get() = BYDAutoBodyworkDevice.getInstance(context)
    private val setting get() = BYDAutoSettingDevice.getInstance(context)
    private val light get() = BYDAutoLightDevice.getInstance(context)
    private val generic by lazy { runCatching { GenericBydDevice(context) }.getOrNull() }
    private val mover by lazy { PercentMover(context) }

    fun execute(action: VehicleAction): Outcome {
        val outcome = try {
            run(action)
        } catch (t: Throwable) {
            // NoClassDefFoundError = not a BYD head unit; anything else = this firmware rejected it.
            Log.w(tag, "$action failed: ${t.javaClass.simpleName}: ${t.message}")
            Outcome(false, detail = if (t is LinkageError) "no vehicle API" else t.message)
        }
        Log.i(tag, "$action -> $outcome")
        return outcome
    }

    private fun ok(code: Int?) = Outcome(code == null || code >= 0, detail = "code=$code")
    private fun ok(vararg codes: Int?) = Outcome(codes.all { it == null || it >= 0 }, detail = "codes=${codes.toList()}")

    private fun run(a: VehicleAction): Outcome = when (a) {
        // ---------------- climate ----------------
        AcOn -> ok(acStart())
        AcOff -> ok(ac.stop(AC_SOURCE))
        is AcAuto -> ok(ac.setAcControlMode(0, if (a.on) 0 else 1))
        is TempSet -> ok(setTemperature(a.value))
        is TempStep -> {
            val cur = ac.getTemprature(1)
            ok(setTemperature((cur + a.delta * if (ac.getTemperatureUnit() == 0) 2 else 1).toDouble(), raw = true))
        }
        is FanSet -> ok(ac.setAcWindLevel(0, a.level.coerceIn(1, 7)))
        is FanStep -> ok(ac.setAcWindLevel(0, (ac.getAcWindLevel() + a.delta).coerceIn(1, 7)))
        is Fan -> if (a.on) ok(ac.setAcWindLevel(0, maxOf(ac.getAcWindLevel(), 3))) else ok(ac.stop(AC_SOURCE))
        is WindshieldFan -> ok(ac.setAcDefrostState(0, 1, if (a.on) 1 else 0))
        is CabinVentilation -> ok(ac.setAcVentilationState(0, if (a.on) 1 else 0))
        is AirDirection -> ok(ac.setAcWindMode(0, a.mode.code))
        is Recirculation -> ok(ac.setAcCycleMode(0, if (a.inside) 1 else 0))
        is ReadTemperature -> readTemperature(a.outside)

        // ---------------- seats ----------------
        is SeatHeat -> ok(*seatIds(a.seats).map { setting.setSeatHeatingState(it, a.level.code) }.toTypedArray())
        is SeatVent -> ok(*seatIds(a.seats).map { setting.setSeatVentilatingState(it, a.level.code) }.toTypedArray())

        // ---------------- steering & mirrors ----------------
        is SteeringWheelHeat -> ok(setting.setSteeringWheelHeatingState(if (a.on) 2 else 1))
        is MirrorHeat -> ok(ac.setAcDefrostState(0, 2, if (a.on) 1 else 0))
        is MirrorAutoFold -> ok(setting.setAutoExternalRearMirrorFollowUpSwitch(if (a.fold) 1 else 0))
        is MirrorFoldNow -> probeSet(Regex("MIRROR.*FOLD|FOLD.*MIRROR"), Regex("AUTO|FOLLOW|LOCK|STATE$"), if (a.fold) 1 else 2) { ids, v ->
            BYDAutoRearViewMirrorDevice.getInstance(context).set(ids, v)
        }

        // ---------------- doors & trunk ----------------
        is Doors -> probeSet(Regex("^DOOR_LOCK.*(ALL|CENTRAL|WHOLE|VEHICLE|LOCK_SET$)"), Regex("CHILD"), if (a.lock) 1 else 2) { ids, v ->
            BYDAutoDoorLockDevice.getInstance(context).set(ids, v)
        }
        is Trunk -> ok(setting.voiceCtlBackDoor(a.op.code))

        // ---------------- windows / roof / shade ----------------
        is Windows -> windows(a)
        is Sunshade -> ok(body.setSunshadeState(a.percent.coerceIn(0, 100)))
        is Roof -> when (a.op) {
            Motion.TO_PERCENT -> Outcome(roofTo(a.percent ?: 50))
            else -> ok(body.setMoonRoofState(a.op.code))
        }

        // ---------------- lights ----------------
        is CabinLights -> ok(genericSet(GenericBydDevice.TYPE_SETTING, FeatureIds.named("SET_INSIDE_LIGHT_STATE_SET", fallback = 1330643002), if (a.on) 2 else 1))
        is Ambient -> ok(setting.setIALBrightness(3, if (a.on) 5 else 0, 0))
        is AmbientColor -> probeSet(Regex("(IAL|AMBIENT|ATMOSPHERE).*COLOU?R"), Regex("GET$"), colorIndex(a.color)) { ids, v ->
            setting.set(ids, v)
        }
        is DaytimeLights -> ok(light.setDayTimeLightState(if (a.on) 1 else 2))
        is AutoHighBeam -> ok(genericSet(GenericBydDevice.TYPE_ADAS, FeatureIds.named("ADAS_HMA_STATE_SET", fallback = 944767008), if (a.on) 1 else 0))
        is Hazards -> ok(hazards(a.on))
        ThankYou -> {
            val code = hazards(true)
            platform.runLater(THANK_YOU_MS) { runCatching { hazards(false) } }
            ok(code)
        }

        // ---------------- cameras & driving ----------------
        is Camera -> Outcome(platform.broadcastCameraView(a.view.keyEvent))
        is DriveMode -> ok(BYDAutoEnergyDevice.getInstance(context).setOperationMode(a.mode.code))
        is EnergyRecovery -> ok(setting.setEnergyFeedback(if (a.strong) 2 else 1))
        is SteeringFeel -> ok(setting.setSteerAssis(if (a.sport) 2 else 1))
        is Wipers -> probeSet(Regex("WIPER.*(MAINTEN|SERVICE|REPAIR|FIX|CHANGE|LIFT|RAISE)"), Regex("GET$"), if (a.raise) 1 else 0) { ids, v ->
            BYDAutoWiperDevice.getInstance(context).set(ids, v)
        }

        // ---------------- screen ----------------
        is Screen -> Outcome(platform.screen(a.on))
        SplitScreen -> Outcome(platform.splitScreen(), detail = "needs the button service turned on in Accessibility settings")
        is Brightness -> Outcome(platform.brightness(a.night), detail = "needs the Modify system settings permission")
        is Theme -> if (platform.theme(a.dark)) Outcome(true)
            else probeSet(Regex("(THEME|DAY_?NIGHT|NIGHT_?MODE|DISPLAY_?MODE)"), Regex("GET$|AUTO"), if (a.dark) 2 else 1) { ids, v -> setting.set(ids, v) }
        is HeadUpDisplay -> ok(genericSet(GenericBydDevice.TYPE_SETTING, FeatureIds.named("SET_HUD_SWITCH_SET", fallback = 1276174371), if (a.on) 1 else 2))

        // ---------------- apps ----------------
        is LaunchApp -> Outcome(platform.launch(a.packageName), detail = a.label)
    }

    // ---------------------------------------------------------------------------------------------

    /** No start() in the mined API; try it reflectively, otherwise auto mode switches the AC on. */
    private fun acStart(): Int {
        val dev = ac
        for (name in listOf("start", "setAcStartState", "setAcPowerState")) {
            val m = runCatching { dev.javaClass.getMethod(name, Int::class.javaPrimitiveType) }.getOrNull() ?: continue
            return (m.invoke(dev, if (name == "start") AC_SOURCE else 1) as? Int) ?: 0
        }
        return dev.setAcControlMode(0, 0)
    }

    /** Unit-aware: getTemperatureUnit() 0 = °F (64–91), 1 = °C (17–33). Converts if you said the other unit. */
    private fun setTemperature(value: Double, raw: Boolean = false): Int {
        val dev = ac
        val unit = dev.getTemperatureUnit()
        var t = value
        if (!raw && unit == 0 && t <= 40) t = t * 9 / 5 + 32
        if (!raw && unit == 1 && t >= 50) t = (t - 32) * 5 / 9
        val clamped = if (unit == 0) t.coerceIn(64.0, 91.0) else t.coerceIn(17.0, 33.0)
        return dev.setAcTemperature(0, clamped.roundToInt(), 0, unit)
    }

    private fun readTemperature(outside: Boolean): Outcome {
        val unit = if (runCatching { ac.getTemperatureUnit() }.getOrDefault(1) == 0) "°F" else "°C"
        val value: Int? = if (outside) {
            ac.getTemprature(4)
        } else {
            FeatureIds.probe(Regex("(IN_?CAR|INSIDE|INNER|INTERIOR|CABIN).*TEMP"), Regex("SET$|TARGET"))?.let { (_, id) ->
                val v = runCatching { BYDAutoSensorDevice.getInstance(context).get(intArrayOf(id), Int::class.java) }.getOrNull()
                    ?: runCatching { ac.get(intArrayOf(id), Int::class.java) }.getOrNull()
                v?.let { if (it.intValue != 0) it.intValue else it.doubleValue.roundToInt() }
            }
        }
        return if (value == null || value <= -100 || value >= 200) Outcome(false)
        else Outcome(true, value = value.toDouble(), unit = unit)
    }

    private fun seatIds(seats: Seats) = when (seats) { Seats.DRIVER -> listOf(1); Seats.PASSENGER -> listOf(2); Seats.BOTH -> listOf(1, 2) }

    private fun windows(a: Windows): Outcome {
        val areas = when (a.target) {
            WindowTarget.ALL -> listOf(1, 2, 3, 4)
            WindowTarget.DRIVER -> listOf(if (driverOnLeft()) 1 else 2)
            WindowTarget.PASSENGER -> listOf(if (driverOnLeft()) 2 else 1)
            WindowTarget.REAR_LEFT -> listOf(3)
            WindowTarget.REAR_RIGHT -> listOf(4)
        }
        if (a.op == Motion.TO_PERCENT) return Outcome(areas.all { windowTo(it, a.percent ?: 50) })
        val codes = IntArray(4)
        areas.forEach { codes[it - 1] = a.op.code }
        return ok(body.setAllWindowState(codes[0], codes[1], codes[2], codes[3]))
    }

    /** Listener-driven % move; if the firmware has no percent callback, use the closest native state. */
    private fun windowTo(area: Int, percent: Int): Boolean =
        runCatching { mover.moveTo(area, percent) }.getOrElse {
            Log.w(tag, "percent move unsupported, using nearest state", it)
            PercentMover.send(body, area, nearestCode(percent)) >= 0
        }

    private fun roofTo(percent: Int): Boolean = windowTo(5, percent)

    private fun nearestCode(percent: Int) = when {
        percent <= 5 -> PercentMover.CLOSE
        percent <= 20 -> 5   // vent
        percent <= 70 -> 4   // half
        else -> PercentMover.OPEN
    }

    private fun hazards(on: Boolean): Int {
        val v = BYDAutoEventValue().apply { intValue = if (on) 1 else 0 }
        val id = FeatureIds.named("LIGHT_TURN_SIGNAL_LIGHT_SET", nested = "Light", fallback = 871366669)!!
        return light.set(intArrayOf(id), v)
    }

    private fun genericSet(deviceType: Int, featureId: Int?, value: Int): Int {
        val dev = generic ?: throw LinkageError("AbsBYDAutoDevice unavailable")
        return dev.setFeature(deviceType, featureId ?: return -1, value)
    }

    /** For features diplus never exposed: find the feature id by name on this firmware, then set it. */
    private fun probeSet(include: Regex, exclude: Regex?, value: Int, set: (IntArray, BYDAutoEventValue) -> Int): Outcome {
        val (name, id) = FeatureIds.probe(include, exclude)
            ?: return Outcome(false, detail = "unsupported on this firmware")
        Log.i(tag, "provisional feature $name=$id value=$value")
        return ok(set(intArrayOf(id), BYDAutoEventValue().apply { intValue = value }))
    }

    private fun colorIndex(color: String) = when (color) {
        "white" -> 1; "warm white" -> 2; "red" -> 3; "orange" -> 4; "yellow" -> 5; "green" -> 6
        "cyan" -> 7; "light blue" -> 8; "blue" -> 9; "purple" -> 10; "pink" -> 11; else -> 1
    }

    companion object {
        /** diplus z.d.f4744v falls back to 0 for the AC control source. */
        const val AC_SOURCE = 0
        private const val THANK_YOU_MS = 3_000L
    }
}
