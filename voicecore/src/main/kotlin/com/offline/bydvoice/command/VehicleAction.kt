package com.offline.bydvoice.command

/** What a recognized command does. Executed by [com.offline.bydvoice.byd.BydController]. */
sealed interface VehicleAction {
    // ---- climate ----
    data object AcOn : VehicleAction
    data object AcOff : VehicleAction
    data class AcAuto(val on: Boolean) : VehicleAction
    data class TempSet(val value: Double) : VehicleAction
    data class TempStep(val delta: Int) : VehicleAction
    data class FanSet(val level: Int) : VehicleAction
    data class FanStep(val delta: Int) : VehicleAction
    data class Fan(val on: Boolean) : VehicleAction
    data class WindshieldFan(val on: Boolean) : VehicleAction
    data class CabinVentilation(val on: Boolean) : VehicleAction
    data class AirDirection(val mode: AirMode) : VehicleAction
    data class Recirculation(val inside: Boolean) : VehicleAction
    data class ReadTemperature(val outside: Boolean) : VehicleAction

    // ---- seats ----
    data class SeatHeat(val seats: Seats, val level: SeatLevel) : VehicleAction
    data class SeatVent(val seats: Seats, val level: SeatLevel) : VehicleAction

    // ---- steering & mirrors ----
    data class SteeringWheelHeat(val on: Boolean) : VehicleAction
    data class MirrorHeat(val on: Boolean) : VehicleAction
    data class MirrorAutoFold(val fold: Boolean) : VehicleAction
    data class MirrorFoldNow(val fold: Boolean) : VehicleAction

    // ---- doors & trunk ----
    data class Doors(val lock: Boolean) : VehicleAction
    data class Trunk(val op: TrunkOp) : VehicleAction

    // ---- windows, roof, shade ----
    data class Windows(val target: WindowTarget, val op: Motion, val percent: Int? = null) : VehicleAction
    data class Sunshade(val percent: Int) : VehicleAction
    data class Roof(val op: Motion, val percent: Int? = null) : VehicleAction

    // ---- lights ----
    data class CabinLights(val on: Boolean) : VehicleAction
    data class Ambient(val on: Boolean) : VehicleAction
    data class AmbientColor(val color: String) : VehicleAction
    data class DaytimeLights(val on: Boolean) : VehicleAction
    data class AutoHighBeam(val on: Boolean) : VehicleAction
    data class Hazards(val on: Boolean) : VehicleAction
    data object ThankYou : VehicleAction

    // ---- cameras & driving ----
    data class Camera(val view: CameraView) : VehicleAction
    data class DriveMode(val mode: Drive) : VehicleAction
    data class EnergyRecovery(val strong: Boolean) : VehicleAction
    data class SteeringFeel(val sport: Boolean) : VehicleAction
    data class Wipers(val raise: Boolean) : VehicleAction

    // ---- screen ----
    data class Screen(val on: Boolean) : VehicleAction
    data object SplitScreen : VehicleAction
    data class Brightness(val night: Boolean) : VehicleAction
    data class Theme(val dark: Boolean) : VehicleAction
    data class HeadUpDisplay(val on: Boolean) : VehicleAction

    // ---- apps ----
    data class LaunchApp(val packageName: String, val label: String) : VehicleAction
}

/** Values mined from diplus: setAcWindMode(0, code). */
enum class AirMode(val code: Int, val spoken: String) {
    FACE(1, "your face"), FACE_FEET(2, "your face and feet"), FEET(3, "your feet"),
    FEET_WINDSHIELD(4, "your feet and the windshield"), WINDSHIELD(5, "the windshield"),
    ALL(6, "all directions"), FACE_WINDSHIELD(7, "your face and the windshield"),
}

enum class Seats(val spoken: String) { DRIVER("driver"), PASSENGER("passenger"), BOTH("front seat") }

/** setSeat*State level codes: 1 off, 2 low, 3 high. */
enum class SeatLevel(val code: Int) { OFF(1), LOW(2), HIGH(3) }

/** voiceCtlBackDoor: 1 open, 3 close; 2 (partial) is provisional. */
enum class TrunkOp(val code: Int) { OPEN(1), PARTIAL(2), CLOSE(3) }

enum class WindowTarget(val spoken: String) {
    ALL("all windows"), DRIVER("the driver window"), PASSENGER("the passenger window"),
    REAR_LEFT("the rear left window"), REAR_RIGHT("the rear right window"),
}

/** setAllWindowState codes: 0 keep, 1 open, 2 close, 3 stop, 4 half, 5 vent. */
enum class Motion(val code: Int) { OPEN(1), CLOSE(2), STOP(3), HALF(4), VENT(5), TO_PERCENT(-1) }

/** AUTO_VIDEO_BUTTON key events used by the panorama app. */
enum class CameraView(val keyEvent: Int, val spoken: String) {
    FRONT(3001, "front"), REAR(3002, "rear"), LEFT(3003, "left"), RIGHT(3004, "right"),
}

/** EnergyDevice.setOperationMode codes. */
enum class Drive(val code: Int, val spoken: String) { ECO(1, "Eco"), SPORT(2, "Sport"), NORMAL(3, "Normal"), SNOW(4, "Snow") }
