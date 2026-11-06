package com.offline.bydvoice.byd

import com.offline.bydvoice.command.AirMode
import com.offline.bydvoice.command.CameraView
import com.offline.bydvoice.command.Drive
import com.offline.bydvoice.command.Motion
import com.offline.bydvoice.command.SeatLevel
import com.offline.bydvoice.command.Seats
import com.offline.bydvoice.command.TrunkOp
import com.offline.bydvoice.command.VehicleAction
import com.offline.bydvoice.command.WindowTarget

/**
 * Maps a recognized command to the Chinese phrase the BYD voice assistant executes.
 *
 * Phrases marked **offline** are taken verbatim from the assistant's `local_command_code.xml`, so
 * they resolve on-device and work without internet. The rest fall back to BYD's **cloud** NLU, so
 * they only work when the car's BYD voice cloud is connected (windows, trunk, doors, lights,
 * mirrors, drive modes, cameras, HUD…). `null` means no phrase — the app uses its own path.
 */
object AssistantPhrases {

    fun phraseFor(a: VehicleAction): String? = when (a) {
        // ---- climate (offline) ----
        VehicleAction.AcOn -> "打开空调"
        VehicleAction.AcOff -> "关闭空调"
        is VehicleAction.AcAuto -> if (a.on) "空调自动模式" else "关闭空调自动模式"
        is VehicleAction.FanSet -> "空调风量调到${cn(a.level)}档"
        is VehicleAction.WindshieldFan -> if (a.on) "打开空调前除霜" else "关闭空调前除霜"
        is VehicleAction.CabinVentilation -> if (a.on) "打开空调通风" else "关闭空调通风"
        is VehicleAction.Recirculation -> if (a.inside) "打开空调内循环" else "打开空调外循环"
        // ---- climate (cloud) ----
        is VehicleAction.TempSet -> "空调温度调到${Math.round(a.value)}度"
        is VehicleAction.TempStep -> if (a.delta > 0) "温度调高" else "温度调低"
        is VehicleAction.FanStep -> if (a.delta > 0) "风量调大" else "风量调小"
        is VehicleAction.AirDirection -> when (a.mode) {
            AirMode.FACE -> "空调吹面"
            AirMode.FEET -> "空调吹脚"
            AirMode.WINDSHIELD -> "空调吹风挡"
            AirMode.FACE_FEET -> "吹面吹脚"
            AirMode.FEET_WINDSHIELD -> "吹脚吹风挡"
            AirMode.FACE_WINDSHIELD -> "吹面吹风挡"
            AirMode.ALL -> "空调全吹"
        }

        // ---- seats (offline): e.g. "主驾驶座椅加热二档" / "关闭副驾驶座椅通风" ----
        is VehicleAction.SeatHeat -> seat(a.seats, "加热", a.level)
        is VehicleAction.SeatVent -> seat(a.seats, "通风", a.level)

        // ---- roof & shade (offline) ----
        is VehicleAction.Roof -> when (a.op) {
            Motion.CLOSE -> "关闭天窗"
            Motion.HALF -> "天窗半开"
            Motion.VENT -> "天窗通风"
            Motion.OPEN, Motion.TO_PERCENT -> "打开天窗"
            Motion.STOP -> null
        }
        is VehicleAction.Sunshade -> when {
            a.percent <= 0 -> "关闭遮阳帘"
            a.percent in 1..60 -> "遮阳帘半开"
            else -> "打开遮阳帘"
        }

        // ---- windows (cloud): "全部车窗" = all windows ----
        is VehicleAction.Windows -> window(a)

        // ---- doors & trunk (cloud) ----
        is VehicleAction.Doors -> if (a.lock) "车门上锁" else "车门解锁"
        is VehicleAction.Trunk -> if (a.op == TrunkOp.CLOSE) "关闭后备箱" else "打开后备箱"

        // ---- steering & mirrors (cloud) ----
        is VehicleAction.SteeringWheelHeat -> if (a.on) "打开方向盘加热" else "关闭方向盘加热"
        is VehicleAction.MirrorHeat -> if (a.on) "打开后视镜加热" else "关闭后视镜加热"
        is VehicleAction.MirrorAutoFold -> if (a.fold) "折叠后视镜" else "展开后视镜"
        is VehicleAction.MirrorFoldNow -> if (a.fold) "折叠后视镜" else "展开后视镜"

        // ---- lights (cloud) ----
        is VehicleAction.CabinLights -> if (a.on) "打开车内灯" else "关闭车内灯"
        is VehicleAction.Ambient -> if (a.on) "打开氛围灯" else "关闭氛围灯"
        is VehicleAction.AmbientColor -> color(a.color)?.let { "氛围灯调成$it" }
        is VehicleAction.DaytimeLights -> if (a.on) "打开日间行车灯" else "关闭日间行车灯"
        is VehicleAction.AutoHighBeam -> if (a.on) "打开自动远光" else "关闭自动远光"
        is VehicleAction.Hazards -> if (a.on) "打开双闪" else "关闭双闪"

        // ---- cameras & driving (cloud) ----
        is VehicleAction.Camera -> when (a.view) {
            CameraView.FRONT -> "打开前方影像"
            CameraView.REAR -> "打开后方影像"
            CameraView.LEFT -> "打开左侧影像"
            CameraView.RIGHT -> "打开右侧影像"
        }
        is VehicleAction.DriveMode -> when (a.mode) {
            Drive.ECO -> "经济模式"
            Drive.SPORT -> "运动模式"
            Drive.NORMAL -> "标准模式"
            Drive.SNOW -> "雪地模式"
        }
        is VehicleAction.EnergyRecovery -> if (a.strong) "能量回收调高" else "能量回收调低"
        is VehicleAction.Wipers -> if (a.raise) "抬起雨刮" else "放下雨刮"
        is VehicleAction.HeadUpDisplay -> if (a.on) "打开抬头显示" else "关闭抬头显示"
        is VehicleAction.Theme -> if (a.dark) "夜间模式" else "日间模式"

        // No assistant phrase — the app handles these itself (apps) or they're unsupported here.
        is VehicleAction.Fan,
        is VehicleAction.ReadTemperature,
        is VehicleAction.Screen,
        is VehicleAction.SteeringFeel,
        VehicleAction.SplitScreen,
        is VehicleAction.Brightness,
        VehicleAction.ThankYou,
        is VehicleAction.LaunchApp -> null
    }

    /**
     * True when the assistant resolves this command on-device (from its local_command_code.xml), so
     * it finishes in a few hundred ms and we can safely cut its spoken reply. Cloud commands take up
     * to ~2 s and share the call that shows the window, so we must not interrupt them.
     */
    fun isOffline(a: VehicleAction): Boolean = when (a) {
        VehicleAction.AcOn, VehicleAction.AcOff,
        is VehicleAction.AcAuto, is VehicleAction.FanSet, is VehicleAction.WindshieldFan,
        is VehicleAction.CabinVentilation, is VehicleAction.Recirculation,
        is VehicleAction.SeatHeat, is VehicleAction.SeatVent,
        is VehicleAction.Roof, is VehicleAction.Sunshade -> true
        else -> false
    }

    /** Chinese numeral for a fan level 1..7 (the assistant wants "四档", not "4档"). */
    private fun cn(level: Int): String =
        listOf("一", "二", "三", "四", "五", "六", "七").getOrElse((level - 1).coerceIn(0, 6)) { "一" }

    /** Offline seat phrases: "主驾驶座椅加热二档" / "关闭副驾驶座椅通风". */
    private fun seat(s: Seats, kind: String, level: SeatLevel): String {
        val who = when (s) { Seats.DRIVER -> "主驾驶"; Seats.PASSENGER -> "副驾驶"; Seats.BOTH -> "主驾驶" }
        return if (level == SeatLevel.OFF) "关闭${who}座椅$kind" else "${who}座椅$kind${if (level == SeatLevel.HIGH) "二" else "一"}档"
    }

    private fun window(a: VehicleAction.Windows): String? {
        val who = when (a.target) {
            WindowTarget.ALL -> "全部车窗"
            WindowTarget.DRIVER -> "主驾车窗"
            WindowTarget.PASSENGER -> "副驾车窗"
            WindowTarget.REAR_LEFT -> "左后车窗"
            WindowTarget.REAR_RIGHT -> "右后车窗"
        }
        return when (a.op) {
            Motion.OPEN, Motion.TO_PERCENT -> "打开$who"
            Motion.CLOSE -> "关闭$who"
            Motion.HALF -> "打开${who}一半"
            Motion.VENT -> "打开${who}一点"
            Motion.STOP -> null
        }
    }

    private fun color(en: String): String? = mapOf(
        "red" to "红色", "blue" to "蓝色", "green" to "绿色", "yellow" to "黄色", "orange" to "橙色",
        "purple" to "紫色", "pink" to "粉色", "white" to "白色", "cyan" to "青色", "gold" to "金色",
    )[en.lowercase()]
}
