package com.offline.bydvoice.command

import com.offline.bydvoice.command.VehicleAction.*
import com.offline.bydvoice.nlu.C
import com.offline.bydvoice.nlu.Tagged

/**
 * Every supported command (the BYD voice command list, tab by tab), described by MEANING:
 * which concepts must be present, which rule it out, and which slots it reads. Wording is free —
 * "please put the AC on", "turn the climate on" and "AC on" all land on the same command.
 */
object CommandCatalog {

    private fun g(vararg c: C): Set<C> = c.toSet()
    private fun p(vararg groups: Set<C>) = Pattern(groups.toList())
    private fun num(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
    private fun failed(what: String) = "Sorry, \u201C$what\u201D didn't go through"

    private val ON = g(C.ON)
    private val OFF = g(C.OFF)
    private val OPENING = g(C.OPEN, C.LOWER, C.DOWN)
    private val CLOSING = g(C.CLOSE, C.RAISE, C.UP)
    private val POSITIONS = setOf(C.DRIVER, C.PASSENGER, C.REAR, C.FRONT, C.LEFT, C.RIGHT)
    private val NOT_WINDOWS = setOf(C.ROOF, C.SUNSHADE, C.TRUNK, C.MIRROR, C.SEAT)
    private val AIR_TRIGGER = g(C.AIR, C.BLOW, C.DIRECTION, C.FAN, C.VENTILATION)

    val all: List<CommandSpec> = buildList {

        /** Adds a matching "<x>_on"/"<x>_off" pair. Bare object (no on/off word) counts as "on". */
        fun toggle(
            id: String, cat: Category, titleOn: String, titleOff: String,
            obj: List<Set<C>>, exOn: List<String>, exOff: List<String>,
            forbid: Set<C> = emptySet(), optional: Set<C> = emptySet(), implicitOn: Boolean = true,
            action: (Boolean) -> VehicleAction, sayOn: String, sayOff: String,
        ) {
            val onPatterns = mutableListOf(Pattern(obj + listOf(ON)))
            if (implicitOn) onPatterns += Pattern(obj)
            add(CommandSpec("${id}_on", cat, titleOn, exOn, onPatterns, forbid + C.OFF, optional,
                action = { action(true) }, reply = { _, o -> if (o.ok) sayOn else failed(titleOn) }))
            add(CommandSpec("${id}_off", cat, titleOff, exOff, listOf(Pattern(obj + listOf(OFF))), forbid + C.ON, optional,
                action = { action(false) }, reply = { _, o -> if (o.ok) sayOff else failed(titleOff) }))
        }

        fun simple(
            id: String, cat: Category, title: String, examples: List<String>, patterns: List<Pattern>,
            forbid: Set<C> = emptySet(), optional: Set<C> = emptySet(), dynamic: String? = null,
            needsNumber: Boolean = false, needsColor: Boolean = false,
            action: (Tagged) -> VehicleAction, say: (Tagged, Outcome) -> String,
        ) = add(CommandSpec(id, cat, title, examples, patterns, forbid, optional, needsNumber, needsColor, dynamic,
            action, { t, o -> if (o.ok || o.value != null) say(t, o) else failed(title) }))

        // =========================== CLIMATE ===========================
        val climateBlock = setOf(C.SEAT, C.STEERING_WHEEL, C.MIRROR, C.WINDOW, C.ROOF, C.SUNSHADE)

        simple("inside_temp", Category.CLIMATE, "Inside temperature",
            listOf("inside temperature", "what is the temperature inside", "how hot is it in the car"),
            listOf(p(g(C.INSIDE, C.CABIN), g(C.TEMPERATURE))),
            forbid = setOf(C.OUTSIDE, C.INCREASE, C.DECREASE, C.WARMER, C.COOLER, C.NUM) + climateBlock,
            optional = setOf(C.SENSOR),
            action = { ReadTemperature(outside = false) },
            say = { _, o -> o.value?.let { "It's ${num(it)} degrees inside" } ?: "I couldn't read the inside temperature" })

        simple("outside_temp", Category.CLIMATE, "Outside temperature",
            listOf("outside temperature", "what is the temperature outside", "how cold is it outside"),
            listOf(p(g(C.OUTSIDE), g(C.TEMPERATURE))),
            forbid = setOf(C.INSIDE, C.CABIN, C.INCREASE, C.DECREASE, C.WARMER, C.COOLER, C.NUM, C.AIR) + climateBlock,
            optional = setOf(C.SENSOR),
            action = { ReadTemperature(outside = true) },
            say = { _, o -> o.value?.let { "It's ${num(it)} degrees outside" } ?: "I couldn't read the outside temperature" })

        simple("temp_set", Category.CLIMATE, "Temperature [number] degrees",
            listOf("temperature 23 degrees", "set the temperature to twenty two", "make it 21 degrees", "set the ac to 24"),
            listOf(p(g(C.TEMPERATURE), g(C.NUM)), p(g(C.DEGREES), g(C.NUM)), p(g(C.AC), g(C.NUM))),
            forbid = setOf(C.INCREASE, C.DECREASE, C.WARMER, C.COOLER, C.UP, C.DOWN, C.FAN, C.PERCENT, C.OUTSIDE, C.INSIDE) + climateBlock,
            optional = setOf(C.AC), needsNumber = true, dynamic = "16–32°",
            action = { t -> TempSet(t.number!!) },
            say = { t, _ -> "Setting the temperature to ${num(t.number!!)} degrees" })

        simple("temp_up", Category.CLIMATE, "Make it warmer",
            listOf("make it warmer", "increase the temperature", "turn up the temperature", "i am cold", "warmer by two degrees"),
            listOf(p(g(C.WARMER)), p(g(C.TEMPERATURE), g(C.INCREASE, C.UP, C.RAISE))),
            forbid = setOf(C.DECREASE, C.COOLER, C.DOWN, C.LOWER, C.FAN, C.HEATING) + climateBlock,
            optional = setOf(C.TEMPERATURE, C.AC, C.NUM, C.DEGREES, C.INCREASE),
            action = { t -> TempStep(t.number?.toInt()?.coerceIn(1, 5) ?: 1) },
            say = { t, _ -> t.number?.let { "Raising the temperature by ${num(it)} degrees" } ?: "Making it warmer" })

        simple("temp_down", Category.CLIMATE, "Make it cooler",
            listOf("make it cooler", "decrease the temperature", "lower the temperature", "i am hot", "turn the temperature down"),
            listOf(p(g(C.COOLER)), p(g(C.TEMPERATURE), g(C.DECREASE, C.DOWN, C.LOWER))),
            forbid = setOf(C.INCREASE, C.WARMER, C.UP, C.RAISE, C.FAN) + climateBlock,
            optional = setOf(C.TEMPERATURE, C.AC, C.NUM, C.DEGREES, C.DECREASE),
            action = { t -> TempStep(-(t.number?.toInt()?.coerceIn(1, 5) ?: 1)) },
            say = { t, _ -> t.number?.let { "Lowering the temperature by ${num(it)} degrees" } ?: "Making it cooler" })

        simple("fan_set", Category.CLIMATE, "Fan speed [number]",
            listOf("fan speed 3", "set the fan to level five", "fan speed two"),
            listOf(p(g(C.FAN), g(C.NUM))),
            forbid = setOf(C.INCREASE, C.DECREASE, C.TEMPERATURE, C.PERCENT, C.WINDSHIELD) + climateBlock,
            needsNumber = true, dynamic = "1–7",
            action = { t -> FanSet(t.number!!.toInt().coerceIn(1, 7)) },
            say = { t, _ -> "Fan speed ${t.number!!.toInt().coerceIn(1, 7)}" })

        simple("fan_up", Category.CLIMATE, "Increase fan speed",
            listOf("increase fan speed", "turn the fan up", "more fan", "fan faster"),
            listOf(p(g(C.FAN), g(C.INCREASE, C.UP, C.RAISE))),
            forbid = setOf(C.DECREASE, C.DOWN, C.LOWER, C.WINDSHIELD) + climateBlock, optional = setOf(C.NUM),
            action = { t -> FanStep(t.number?.toInt()?.coerceIn(1, 6) ?: 1) },
            say = { _, _ -> "Increasing the fan speed" })

        simple("fan_down", Category.CLIMATE, "Decrease fan speed",
            listOf("decrease fan speed", "turn the fan down", "less fan", "lower the fan"),
            listOf(p(g(C.FAN), g(C.DECREASE, C.DOWN, C.LOWER))),
            forbid = setOf(C.INCREASE, C.UP, C.RAISE, C.WINDSHIELD) + climateBlock, optional = setOf(C.NUM),
            action = { t -> FanStep(-(t.number?.toInt()?.coerceIn(1, 6) ?: 1)) },
            say = { _, _ -> "Decreasing the fan speed" })

        toggle("fan", Category.CLIMATE, "Turn on fan", "Turn off fan", listOf(g(C.FAN)),
            listOf("turn on fan", "switch the fan on"), listOf("turn off fan", "stop the fan"),
            forbid = setOf(C.WINDSHIELD, C.NUM, C.INCREASE, C.DECREASE) + climateBlock, implicitOn = false,
            action = { Fan(it) }, sayOn = "Turning on the fan", sayOff = "Turning off the fan")

        toggle("windshield_fan", Category.CLIMATE, "Turn on windshield fan", "Turn off windshield fan", listOf(g(C.WINDSHIELD)),
            listOf("turn on windshield fan", "defrost the windshield", "defog"), listOf("turn off windshield fan", "stop defrosting"),
            forbid = setOf(C.AIR, C.BLOW, C.DIRECTION, C.FACE, C.FEET, C.WIPER, C.MIRROR) + climateBlock,
            optional = setOf(C.FAN, C.AC),
            action = { WindshieldFan(it) }, sayOn = "Turning on the windshield fan", sayOff = "Turning off the windshield fan")

        toggle("ac", Category.CLIMATE, "Turn on air conditioner", "Turn off air conditioner", listOf(g(C.AC)),
            listOf("turn on air conditioner", "turn the ac on", "start the climate"), listOf("turn off air conditioner", "ac off"),
            forbid = setOf(C.AUTO, C.NUM, C.WARMER, C.COOLER, C.INCREASE, C.DECREASE, C.TEMPERATURE) + climateBlock,
            optional = setOf(C.FAN),
            action = { if (it) AcOn else AcOff }, sayOn = "Turning on the air conditioner", sayOff = "Turning off the air conditioner")

        toggle("cabin_vent", Category.CLIMATE, "Turn on cabin ventilation", "Turn off cabin ventilation", listOf(g(C.VENTILATION)),
            listOf("turn on cabin ventilation", "cabin ventilation on"), listOf("turn off cabin ventilation"),
            forbid = setOf(C.DRIVER, C.PASSENGER, C.BOTH, C.VENTILATE, C.FACE, C.FEET, C.WINDSHIELD) + climateBlock,
            optional = setOf(C.CABIN, C.AC, C.AIR), implicitOn = false,
            action = { CabinVentilation(it) }, sayOn = "Turning on cabin ventilation", sayOff = "Turning off cabin ventilation")

        toggle("ac_auto", Category.CLIMATE, "Turn on air conditioner auto mode", "Turn off air conditioner auto mode",
            listOf(g(C.AUTO), g(C.AC, C.MODE, C.TEMPERATURE)),
            listOf("turn on air conditioner auto mode", "auto ac", "automatic climate"),
            listOf("turn off air conditioner auto mode", "turn off auto mode"),
            forbid = setOf(C.HIGH_BEAM, C.LIGHT, C.WIPER) + climateBlock, optional = setOf(C.AC, C.MODE),
            action = { AcAuto(it) }, sayOn = "Air conditioner set to auto", sayOff = "Air conditioner auto mode off")

        // =========================== AIR FLOWS ===========================
        val airBase = setOf(C.OFF, C.SEAT, C.WINDOW, C.WIPER, C.MIRROR)
        // "Air to my feet": the short word "air" is often lost by the recognizer, and "feet"/"face" on
        // their own already mean airflow — so those zones also match without it (not windshield alone,
        // which belongs to the defrost fan).
        fun air(id: String, title: String, ex: List<String>, mode: AirMode, need: List<Set<C>>, forbid: Set<C>, implicit: Boolean = true) =
            simple(id, Category.AIRFLOW, title, ex,
                listOfNotNull(Pattern(listOf(AIR_TRIGGER) + need), if (implicit) Pattern(need) else null),
                forbid = forbid + airBase + setOf(C.HEATING, C.TEMPERATURE), optional = setOf(C.DRIVER, C.AC, C.SET, C.ON),
                action = { AirDirection(mode) }, say = { _, _ -> "Air to ${mode.spoken}" })

        air("air_face", "Air to face", listOf("air to face", "blow air on my face"), AirMode.FACE, listOf(g(C.FACE)), setOf(C.FEET, C.WINDSHIELD, C.ALL, C.BOTH))
        air("air_feet", "Air to feet", listOf("air to feet", "air to the floor"), AirMode.FEET, listOf(g(C.FEET)), setOf(C.FACE, C.WINDSHIELD, C.ALL, C.BOTH))
        air("air_windshield", "Air to windshield", listOf("air to windshield", "blow air on the windscreen"), AirMode.WINDSHIELD, listOf(g(C.WINDSHIELD)), setOf(C.FACE, C.FEET, C.ALL, C.BOTH), implicit = false)
        air("air_face_feet", "Air to feet and face", listOf("air to feet and face", "air to face and feet"), AirMode.FACE_FEET, listOf(g(C.FEET), g(C.FACE)), setOf(C.WINDSHIELD))
        air("air_feet_windshield", "Air to feet and windshield", listOf("air to feet and windshield"), AirMode.FEET_WINDSHIELD, listOf(g(C.FEET), g(C.WINDSHIELD)), setOf(C.FACE))
        air("air_face_windshield", "Air to face and windshield", listOf("air to face and windshield"), AirMode.FACE_WINDSHIELD, listOf(g(C.FACE), g(C.WINDSHIELD)), setOf(C.FEET))
        simple("air_all", Category.AIRFLOW, "Air to all directions", listOf("air to all directions", "air everywhere"),
            listOf(p(AIR_TRIGGER, g(C.ALL, C.BOTH)), p(AIR_TRIGGER, g(C.FACE), g(C.FEET), g(C.WINDSHIELD))),
            forbid = airBase, optional = setOf(C.DIRECTION, C.FACE, C.FEET, C.WINDSHIELD),
            action = { AirDirection(AirMode.ALL) }, say = { _, _ -> "Air to all directions" })

        simple("recirc", Category.AIRFLOW, "Air recirculation", listOf("air recirculation", "recirculate the air", "inside air"),
            listOf(p(g(C.RECIRC))), forbid = setOf(C.OUTSIDE, C.OFF) + climateBlock, optional = setOf(C.AIR, C.ON, C.AC, C.INSIDE),
            action = { Recirculation(inside = true) }, say = { _, _ -> "Recirculating cabin air" })
        simple("outside_air", Category.AIRFLOW, "Outside air", listOf("outside air", "fresh air", "turn off recirculation"),
            listOf(p(g(C.OUTSIDE), g(C.AIR, C.RECIRC)), p(g(C.RECIRC), g(C.OFF))),
            forbid = setOf(C.TEMPERATURE, C.INSIDE) + climateBlock, optional = setOf(C.ON, C.AC),
            action = { Recirculation(inside = false) }, say = { _, _ -> "Using outside air" })

        // =========================== SEATS ===========================
        fun seat(kind: String, heat: Boolean, who: Seats, whoGroup: Set<C>, otherSeats: Set<C>) {
            val feature = if (heat) g(C.HEATING) else g(C.VENTILATION)
            val featureOn = if (heat) g(C.HEATING, C.WARMER) else g(C.VENTILATION)
            val word = if (heat) "heating" else "ventilation"
            val label = when (who) { Seats.DRIVER -> "driver"; Seats.PASSENGER -> "passenger"; Seats.BOTH -> "seats" }
            val block = otherSeats + setOf(C.STEERING_WHEEL, C.MIRROR, C.WINDOW, C.CABIN, C.VENTILATE) +
                (if (heat) setOf(C.VENTILATION, C.COOLER) else setOf(C.HEATING, C.WARMER))
            val extra = setOf(C.SEAT, C.SPEED, C.NUM)
            val make: (SeatLevel) -> VehicleAction = { lvl -> if (heat) SeatHeat(who, lvl) else SeatVent(who, lvl) }
            val spokenWho = if (who == Seats.BOTH) "the front seats" else "the ${who.spoken} seat"

            simple("${kind}_on", Category.SEATS, "Turn on $label $word", listOf("turn on $label $word", "$label seat $word on"),
                listOf(p(featureOn, whoGroup, ON), p(feature, whoGroup)), forbid = block + setOf(C.OFF, C.STRONG),
                optional = extra + C.WEAK,
                action = { t -> make(if ((t.number ?: 1.0) >= 2.0) SeatLevel.HIGH else SeatLevel.LOW) },
                say = { t, _ -> if ((t.number ?: 1.0) >= 2.0) "Strong $word for $spokenWho" else "Turning on $word for $spokenWho" })
            simple("${kind}_strong", Category.SEATS, "Strong $label $word", listOf("strong $label $word", "$label $word on high"),
                listOf(p(feature, whoGroup, g(C.STRONG, C.FULL))), forbid = block + setOf(C.OFF, C.WEAK),
                optional = extra + C.ON,
                action = { make(SeatLevel.HIGH) }, say = { _, _ -> "Strong $word for $spokenWho" })
            simple("${kind}_off", Category.SEATS, "Turn off $label $word", listOf("turn off $label $word"),
                listOf(p(feature, whoGroup, OFF)), forbid = block + setOf(C.ON),
                optional = extra,
                action = { make(SeatLevel.OFF) }, say = { _, _ -> "Turning off $word for $spokenWho" })
        }
        seat("heat_driver", true, Seats.DRIVER, g(C.DRIVER), setOf(C.PASSENGER, C.BOTH, C.ALL))
        seat("heat_passenger", true, Seats.PASSENGER, g(C.PASSENGER), setOf(C.DRIVER, C.BOTH, C.ALL))
        seat("heat_seats", true, Seats.BOTH, g(C.SEAT, C.BOTH, C.ALL), setOf(C.DRIVER, C.PASSENGER))
        seat("vent_driver", false, Seats.DRIVER, g(C.DRIVER), setOf(C.PASSENGER, C.BOTH, C.ALL))
        seat("vent_passenger", false, Seats.PASSENGER, g(C.PASSENGER), setOf(C.DRIVER, C.BOTH, C.ALL))
        seat("vent_seats", false, Seats.BOTH, g(C.SEAT, C.BOTH, C.ALL), setOf(C.DRIVER, C.PASSENGER))

        // ======================= STEERING & MIRRORS =======================
        toggle("sw_heat", Category.STEERING_MIRRORS, "Turn on steering wheel heating", "Turn off steering wheel heating",
            listOf(g(C.STEERING_WHEEL), g(C.HEATING, C.WARMER)),
            listOf("turn on steering wheel heating", "heat the steering wheel"), listOf("turn off steering wheel heating"),
            forbid = setOf(C.SEAT, C.MIRROR, C.SPORT, C.REGULAR, C.NORMAL, C.DRIVER, C.PASSENGER),
            action = { SteeringWheelHeat(it) }, sayOn = "Heating the steering wheel", sayOff = "Steering wheel heating off")

        toggle("mirror_heat", Category.STEERING_MIRRORS, "Turn on mirror heating", "Turn off mirror heating",
            listOf(g(C.MIRROR), g(C.HEATING, C.WARMER, C.WINDSHIELD)),
            listOf("turn on mirror heating", "defrost the mirrors"), listOf("turn off mirror heating"),
            forbid = setOf(C.FOLD, C.UNFOLD, C.SEAT, C.STEERING_WHEEL),
            action = { MirrorHeat(it) }, sayOn = "Heating the mirrors", sayOff = "Mirror heating off")

        simple("mirror_fold", Category.STEERING_MIRRORS, "Fold mirrors", listOf("fold mirrors", "fold the side mirrors"),
            listOf(p(g(C.MIRROR), g(C.FOLD))), forbid = setOf(C.UNFOLD, C.NOW, C.HEATING),
            action = { MirrorAutoFold(true) }, say = { _, _ -> "Mirrors will fold when the car locks" })
        simple("mirror_unfold", Category.STEERING_MIRRORS, "Unfold mirrors", listOf("unfold mirrors"),
            listOf(p(g(C.MIRROR), g(C.UNFOLD))), forbid = setOf(C.FOLD, C.NOW, C.HEATING),
            action = { MirrorAutoFold(false) }, say = { _, _ -> "Mirrors will stay open when the car locks" })
        simple("mirror_fold_now", Category.STEERING_MIRRORS, "Fold the mirrors now", listOf("fold the mirrors now"),
            listOf(p(g(C.MIRROR), g(C.FOLD), g(C.NOW))), forbid = setOf(C.UNFOLD, C.HEATING),
            action = { MirrorFoldNow(true) }, say = { _, _ -> "Folding the mirrors" })
        simple("mirror_unfold_now", Category.STEERING_MIRRORS, "Unfold the mirrors now", listOf("unfold the mirrors now"),
            listOf(p(g(C.MIRROR), g(C.UNFOLD), g(C.NOW))), forbid = setOf(C.FOLD, C.HEATING),
            action = { MirrorFoldNow(false) }, say = { _, _ -> "Unfolding the mirrors" })

        // ========================= DOORS & TRUNK =========================
        val doorBlock = setOf(C.WINDOW, C.TRUNK, C.ROOF, C.SUNSHADE, C.MIRROR)
        simple("doors_lock", Category.DOORS_TRUNK, "Close doors", listOf("close doors", "lock the car", "lock the doors"),
            listOf(p(g(C.DOOR, C.CAR), g(C.CLOSE, C.LOCK)), p(g(C.LOCK))), forbid = doorBlock + setOf(C.OPEN, C.UNLOCK),
            optional = setOf(C.ALL),
            action = { Doors(lock = true) }, say = { _, _ -> "Locking the doors" })
        simple("doors_unlock", Category.DOORS_TRUNK, "Open doors", listOf("open doors", "unlock the car", "unlock the doors"),
            listOf(p(g(C.DOOR, C.CAR), g(C.OPEN, C.UNLOCK)), p(g(C.UNLOCK))), forbid = doorBlock + setOf(C.CLOSE, C.LOCK),
            optional = setOf(C.ALL),
            action = { Doors(lock = false) }, say = { _, _ -> "Unlocking the doors" })
        simple("trunk_open", Category.DOORS_TRUNK, "Open trunk", listOf("open trunk", "open the boot", "pop the tailgate"),
            listOf(p(g(C.TRUNK), g(C.OPEN, C.UNLOCK, C.RAISE, C.UP))), forbid = setOf(C.CLOSE, C.PARTIAL, C.HALF, C.QUARTER, C.LOCK),
            optional = setOf(C.FULL),
            action = { Trunk(TrunkOp.OPEN) }, say = { _, _ -> "Opening the trunk" })
        simple("trunk_partial", Category.DOORS_TRUNK, "Partially open trunk", listOf("partially open trunk", "open the trunk halfway"),
            listOf(p(g(C.TRUNK), g(C.PARTIAL, C.HALF, C.QUARTER))), forbid = setOf(C.CLOSE, C.LOCK),
            optional = setOf(C.OPEN),
            action = { Trunk(TrunkOp.PARTIAL) }, say = { _, _ -> "Partially opening the trunk" })
        simple("trunk_close", Category.DOORS_TRUNK, "Close trunk", listOf("close trunk", "shut the boot"),
            listOf(p(g(C.TRUNK), g(C.CLOSE, C.LOCK, C.LOWER, C.DOWN))), forbid = setOf(C.OPEN, C.UNLOCK),
            action = { Trunk(TrunkOp.CLOSE) }, say = { _, _ -> "Closing the trunk" })

        // ============================ WINDOWS ============================
        val notFraction = setOf(C.HALF, C.QUARTER, C.PARTIAL)
        simple("windows_open", Category.WINDOWS, "Open all windows", listOf("open all windows", "open the windows", "roll down the windows"),
            listOf(p(g(C.WINDOW), OPENING)), forbid = POSITIONS + NOT_WINDOWS + notFraction + setOf(C.CLOSE, C.RAISE, C.UP),
            optional = setOf(C.ALL, C.BOTH, C.PERCENT, C.FULL, C.CAR), dynamic = "optional %",
            action = { t -> t.percent?.takeIf { t.has(C.PERCENT) }?.let { Windows(WindowTarget.ALL, Motion.TO_PERCENT, it) } ?: Windows(WindowTarget.ALL, Motion.OPEN) },
            say = { t, _ -> if (t.has(C.PERCENT)) "Opening all windows to ${t.percent} percent" else "Opening all windows" })
        simple("windows_close", Category.WINDOWS, "Close all windows", listOf("close all windows", "close the windows", "roll up the windows"),
            listOf(p(g(C.WINDOW), CLOSING)), forbid = POSITIONS + NOT_WINDOWS + setOf(C.OPEN, C.LOWER, C.DOWN),
            optional = setOf(C.ALL, C.BOTH, C.CAR),
            action = { Windows(WindowTarget.ALL, Motion.CLOSE) }, say = { _, _ -> "Closing all windows" })
        simple("windows_half", Category.WINDOWS, "Open all windows halfway", listOf("open all windows halfway"),
            listOf(p(g(C.WINDOW), g(C.HALF))), forbid = POSITIONS + NOT_WINDOWS + setOf(C.CLOSE, C.QUARTER),
            optional = setOf(C.ALL, C.OPEN),
            action = { Windows(WindowTarget.ALL, Motion.HALF) }, say = { _, _ -> "Opening all windows halfway" })
        simple("windows_vent", Category.WINDOWS, "Ventilate cabin", listOf("ventilate cabin", "ventilate the car", "crack the windows"),
            listOf(p(g(C.VENTILATE)), p(g(C.WINDOW), g(C.VENTILATION, C.PARTIAL))),
            forbid = POSITIONS + setOf(C.ON, C.OFF, C.SEAT, C.CLOSE),
            optional = setOf(C.CABIN, C.ALL, C.WINDOW, C.OPEN, C.CAR),
            action = { Windows(WindowTarget.ALL, Motion.VENT) }, say = { _, _ -> "Ventilating the cabin" })

        fun window(id: String, target: WindowTarget, label: String, who: List<Set<C>>, notWho: Set<C>) {
            val block = NOT_WINDOWS + notWho
            simple("${id}_open", Category.WINDOWS, "Open $label window", listOf("open $label window", "roll down the $label window"),
                // "rear right window 30 percent": a target percentage needs no verb
                listOf(Pattern(listOf(g(C.WINDOW), OPENING) + who), Pattern(listOf(g(C.WINDOW), g(C.PERCENT)) + who)),
                forbid = block + notFraction + setOf(C.CLOSE, C.RAISE, C.UP),
                optional = setOf(C.PERCENT, C.FULL, C.FRONT, C.SEAT), dynamic = "optional %",
                action = { t -> if (t.has(C.PERCENT)) Windows(target, Motion.TO_PERCENT, t.percent) else Windows(target, Motion.OPEN) },
                say = { t, _ -> if (t.has(C.PERCENT)) "Opening ${target.spoken} to ${t.percent} percent" else "Opening ${target.spoken}" })
            simple("${id}_close", Category.WINDOWS, "Close $label window", listOf("close $label window", "roll up the $label window"),
                listOf(Pattern(listOf(g(C.WINDOW), CLOSING) + who)), forbid = block + setOf(C.OPEN, C.LOWER, C.DOWN),
                optional = setOf(C.FRONT, C.SEAT),
                action = { Windows(target, Motion.CLOSE) }, say = { _, _ -> "Closing ${target.spoken}" })
            simple("${id}_half", Category.WINDOWS, "Open $label window halfway", listOf("open $label window halfway"),
                listOf(Pattern(listOf(g(C.WINDOW), g(C.HALF)) + who)), forbid = block + setOf(C.CLOSE, C.QUARTER),
                optional = setOf(C.OPEN, C.FRONT, C.SEAT),
                action = { Windows(target, Motion.HALF) }, say = { _, _ -> "Opening ${target.spoken} halfway" })
            simple("${id}_quarter", Category.WINDOWS, "Open $label window a quarter", listOf("open $label window a quarter", "open the $label window a bit"),
                listOf(Pattern(listOf(g(C.WINDOW), g(C.QUARTER, C.PARTIAL)) + who)), forbid = block + setOf(C.CLOSE, C.HALF),
                optional = setOf(C.OPEN, C.FRONT, C.SEAT),
                action = { Windows(target, Motion.TO_PERCENT, 25) }, say = { _, _ -> "Opening ${target.spoken} a quarter" })
        }
        window("win_driver", WindowTarget.DRIVER, "driver", listOf(g(C.DRIVER)), setOf(C.PASSENGER, C.REAR, C.ALL))
        window("win_passenger", WindowTarget.PASSENGER, "passenger", listOf(g(C.PASSENGER)), setOf(C.DRIVER, C.REAR, C.ALL))
        window("win_rear_left", WindowTarget.REAR_LEFT, "rear left", listOf(g(C.REAR), g(C.LEFT)), setOf(C.RIGHT, C.DRIVER, C.PASSENGER, C.FRONT, C.ALL))
        window("win_rear_right", WindowTarget.REAR_RIGHT, "rear right", listOf(g(C.REAR), g(C.RIGHT)), setOf(C.LEFT, C.DRIVER, C.PASSENGER, C.FRONT, C.ALL))

        // ========================= CURTAIN & ROOF =========================
        val shadeBlock = setOf(C.ROOF, C.WINDOW, C.TRUNK)
        simple("shade_open", Category.ROOF_SHADE, "Open sun shade", listOf("open sun shade", "open the sunshade", "open the curtain"),
            listOf(p(g(C.SUNSHADE), g(C.OPEN, C.UP, C.RAISE))), forbid = shadeBlock + notFraction + setOf(C.CLOSE, C.PERCENT),
            optional = setOf(C.FULL),
            action = { Sunshade(100) }, say = { _, _ -> "Opening the sun shade" })
        simple("shade_partial", Category.ROOF_SHADE, "Partially open sun shade", listOf("partially open sun shade", "open the sun shade 40 percent", "open the sun shade halfway"),
            listOf(p(g(C.SUNSHADE), g(C.PARTIAL, C.HALF, C.QUARTER, C.PERCENT))), forbid = shadeBlock + setOf(C.CLOSE),
            optional = setOf(C.OPEN), dynamic = "%",
            action = { t -> Sunshade(t.percent ?: 50) }, say = { t, _ -> "Opening the sun shade to ${t.percent ?: 50} percent" })
        simple("shade_close", Category.ROOF_SHADE, "Close sun shade", listOf("close sun shade", "close the curtain"),
            listOf(p(g(C.SUNSHADE), g(C.CLOSE, C.DOWN, C.LOWER))), forbid = shadeBlock + setOf(C.OPEN),
            action = { Sunshade(0) }, say = { _, _ -> "Closing the sun shade" })

        val roofBlock = setOf(C.SUNSHADE, C.WINDOW, C.TRUNK)
        simple("roof_open", Category.ROOF_SHADE, "Open roof", listOf("open roof", "open the sunroof", "open the roof 30 percent"),
            listOf(p(g(C.ROOF), g(C.OPEN)), p(g(C.ROOF), g(C.PERCENT))), forbid = roofBlock + notFraction + setOf(C.CLOSE, C.FULL),
            optional = setOf(C.PERCENT), dynamic = "optional %",
            action = { t -> if (t.has(C.PERCENT)) Roof(Motion.TO_PERCENT, t.percent) else Roof(Motion.OPEN) },
            say = { t, _ -> if (t.has(C.PERCENT)) "Opening the roof to ${t.percent} percent" else "Opening the roof" })
        simple("roof_quarter", Category.ROOF_SHADE, "Open roof a quarter", listOf("open roof a quarter", "open the sunroof a bit"),
            listOf(p(g(C.ROOF), g(C.QUARTER, C.PARTIAL))), forbid = roofBlock + setOf(C.CLOSE, C.HALF, C.FULL),
            optional = setOf(C.OPEN),
            action = { Roof(Motion.TO_PERCENT, 25) }, say = { _, _ -> "Opening the roof a quarter" })
        simple("roof_half", Category.ROOF_SHADE, "Open roof halfway", listOf("open roof halfway"),
            listOf(p(g(C.ROOF), g(C.HALF))), forbid = roofBlock + setOf(C.CLOSE, C.QUARTER, C.FULL),
            optional = setOf(C.OPEN),
            action = { Roof(Motion.TO_PERCENT, 50) }, say = { _, _ -> "Opening the roof halfway" })
        simple("roof_full", Category.ROOF_SHADE, "Open roof fully", listOf("open roof fully", "open the sunroof all the way"),
            listOf(p(g(C.ROOF), g(C.FULL))), forbid = roofBlock + setOf(C.CLOSE, C.QUARTER, C.HALF),
            optional = setOf(C.OPEN),
            action = { Roof(Motion.TO_PERCENT, 100) }, say = { _, _ -> "Opening the roof fully" })
        simple("roof_close", Category.ROOF_SHADE, "Close roof", listOf("close roof", "shut the sunroof"),
            listOf(p(g(C.ROOF), g(C.CLOSE))), forbid = roofBlock + setOf(C.OPEN),
            action = { Roof(Motion.CLOSE) }, say = { _, _ -> "Closing the roof" })

        // ============================ LIGHTS ============================
        val lightBlock = setOf(C.AMBIENT, C.DAY, C.RUNNING, C.HIGH_BEAM, C.HAZARD, C.SCREEN, C.THEME, C.DARK,
            C.BRIGHTNESS, C.COLOR, C.NIGHT, C.MODE)
        toggle("lights", Category.LIGHTS, "Turn on lights", "Turn off lights", listOf(g(C.LIGHT)),
            listOf("turn on lights", "turn on the cabin lights", "lights on"), listOf("turn off lights", "lights off"),
            forbid = lightBlock, optional = setOf(C.CABIN, C.INSIDE, C.ALL), implicitOn = false,
            action = { CabinLights(it) }, sayOn = "Turning on the cabin lights", sayOff = "Turning off the cabin lights")

        simple("ambient_color", Category.LIGHTS, "Ambient color [color]", listOf("ambient color white", "set the ambient light to blue", "ambient lighting red"),
            listOf(p(g(C.COLOR))), forbid = setOf(C.OFF, C.THEME, C.SCREEN), needsColor = true,
            optional = setOf(C.AMBIENT, C.LIGHT, C.ON), dynamic = "white, red, blue…",
            action = { t -> AmbientColor(t.color!!) }, say = { t, _ -> "Ambient lighting set to ${t.color}" })

        toggle("ambient", Category.LIGHTS, "Turn on ambient lighting", "Turn off ambient lighting", listOf(g(C.AMBIENT)),
            listOf("turn on ambient lighting", "mood lights on"), listOf("turn off ambient lighting"),
            forbid = setOf(C.COLOR, C.SCREEN, C.THEME), optional = setOf(C.LIGHT),
            action = { Ambient(it) }, sayOn = "Turning on ambient lighting", sayOff = "Turning off ambient lighting")

        toggle("drl", Category.LIGHTS, "Turn on daytime running lights", "Turn off daytime running lights",
            listOf(g(C.DAY, C.RUNNING), g(C.LIGHT)),
            listOf("turn on daytime running lights", "drl on"), listOf("turn off daytime running lights"),
            forbid = setOf(C.SCREEN, C.BRIGHTNESS, C.AMBIENT, C.HIGH_BEAM, C.HAZARD, C.THEME), optional = setOf(C.DAY, C.RUNNING),
            implicitOn = false,
            action = { DaytimeLights(it) }, sayOn = "Turning on the daytime running lights", sayOff = "Turning off the daytime running lights")

        toggle("auto_high_beam", Category.LIGHTS, "Turn on auto high beams", "Turn off auto high beams", listOf(g(C.HIGH_BEAM)),
            listOf("turn on auto high beams", "automatic high beam on"), listOf("turn off auto high beams"),
            forbid = setOf(C.HAZARD), optional = setOf(C.AUTO, C.LIGHT),
            action = { AutoHighBeam(it) }, sayOn = "Auto high beams on", sayOff = "Auto high beams off")

        toggle("hazards", Category.LIGHTS, "Turn on hazard lights", "Turn off hazard lights", listOf(g(C.HAZARD)),
            listOf("turn on hazard lights", "hazards on"), listOf("turn off hazard lights"),
            forbid = setOf(C.THANK), optional = setOf(C.LIGHT),
            action = { Hazards(it) }, sayOn = "Hazard lights on", sayOff = "Hazard lights off")

        simple("thank_you", Category.LIGHTS, "Say thank you", listOf("say thank you", "thank you", "flash a thanks"),
            listOf(p(g(C.THANK))), optional = setOf(C.SAY, C.HAZARD, C.LIGHT),
            action = { ThankYou }, say = { _, _ -> "Saying thanks" })

        // ============================ CAMERAS ============================
        val camBlock = setOf(C.WINDOW, C.MIRROR, C.SEAT, C.DOOR, C.TRUNK, C.LIGHT)
        fun cam(id: String, title: String, view: CameraView, side: C, others: Set<C>) =
            simple(id, Category.CAMERAS, title, listOf(title.lowercase(), "show the ${view.spoken} camera"),
                listOf(p(g(C.VIEW, C.CAMERA), g(side))), forbid = camBlock + others, optional = setOf(C.CAMERA, C.VIEW),
                action = { Camera(view) }, say = { _, _ -> "Showing the ${view.spoken} view" })
        cam("cam_left", "View from left", CameraView.LEFT, C.LEFT, setOf(C.RIGHT, C.REAR, C.FRONT))
        cam("cam_right", "View from right", CameraView.RIGHT, C.RIGHT, setOf(C.LEFT, C.REAR, C.FRONT))
        cam("cam_rear", "View from rear", CameraView.REAR, C.REAR, setOf(C.LEFT, C.RIGHT, C.FRONT))
        cam("cam_front", "View from front", CameraView.FRONT, C.FRONT, setOf(C.LEFT, C.RIGHT, C.REAR))

        // ============================ DRIVING ============================
        val modeBlock = setOf(C.STEERING_WHEEL, C.SCREEN, C.THEME, C.LIGHT, C.DARK, C.AUTO, C.RECOVERY)
        fun mode(id: String, title: String, drive: Drive, c: C, others: Set<C>) =
            simple(id, Category.DRIVING, title, listOf(title.lowercase(), "switch to ${drive.spoken.lowercase()} mode"),
                listOf(p(g(c))), forbid = modeBlock + others, optional = setOf(C.MODE, C.DRIVE, C.ON),
                action = { DriveMode(drive) }, say = { _, _ -> "${drive.spoken} mode" })
        mode("mode_normal", "Normal mode", Drive.NORMAL, C.NORMAL, setOf(C.ECO, C.SPORT, C.SNOW))
        mode("mode_eco", "ECO mode", Drive.ECO, C.ECO, setOf(C.NORMAL, C.SPORT, C.SNOW))
        mode("mode_sport", "Sport mode", Drive.SPORT, C.SPORT, setOf(C.ECO, C.NORMAL, C.SNOW))
        mode("mode_snow", "Snow mode", Drive.SNOW, C.SNOW, setOf(C.ECO, C.SPORT, C.NORMAL))

        simple("regen_strong", Category.DRIVING, "Strong energy recovery", listOf("strong energy recovery", "increase regeneration"),
            listOf(p(g(C.RECOVERY), g(C.STRONG, C.INCREASE, C.FULL))), forbid = setOf(C.WEAK, C.DECREASE),
            optional = setOf(C.MODE, C.SET),
            action = { EnergyRecovery(strong = true) }, say = { _, _ -> "Strong energy recovery" })
        simple("regen_weak", Category.DRIVING, "Weak energy recovery", listOf("weak energy recovery", "standard regeneration"),
            listOf(p(g(C.RECOVERY), g(C.WEAK, C.DECREASE, C.NORMAL, C.REGULAR))), forbid = setOf(C.STRONG, C.INCREASE),
            optional = setOf(C.MODE, C.SET),
            action = { EnergyRecovery(strong = false) }, say = { _, _ -> "Standard energy recovery" })

        simple("steer_regular", Category.DRIVING, "Regular steering wheel", listOf("regular steering wheel", "comfort steering"),
            listOf(p(g(C.STEERING_WHEEL), g(C.REGULAR, C.NORMAL, C.WEAK))), forbid = setOf(C.HEATING, C.SPORT, C.STRONG),
            optional = setOf(C.MODE),
            action = { SteeringFeel(sport = false) }, say = { _, _ -> "Comfort steering" })
        simple("steer_sport", Category.DRIVING, "Sport steering wheel", listOf("sport steering wheel", "sporty steering"),
            listOf(p(g(C.STEERING_WHEEL), g(C.SPORT, C.STRONG))), forbid = setOf(C.HEATING, C.REGULAR, C.NORMAL),
            optional = setOf(C.MODE),
            action = { SteeringFeel(sport = true) }, say = { _, _ -> "Sport steering" })

        simple("wipers_raise", Category.DRIVING, "Raise wipers", listOf("raise wipers", "lift the wipers"),
            listOf(p(g(C.WIPER), g(C.RAISE, C.UP, C.OPEN))), forbid = setOf(C.LOWER, C.DOWN, C.CLOSE),
            action = { Wipers(raise = true) }, say = { _, _ -> "Raising the wipers" })
        simple("wipers_lower", Category.DRIVING, "Lower wipers", listOf("lower wipers", "put the wipers down"),
            listOf(p(g(C.WIPER), g(C.LOWER, C.DOWN, C.CLOSE))), forbid = setOf(C.RAISE, C.UP, C.OPEN),
            action = { Wipers(raise = false) }, say = { _, _ -> "Lowering the wipers" })

        // ============================ SCREEN ============================
        toggle("screen", Category.SCREEN, "Turn on screen", "Turn off screen", listOf(g(C.SCREEN)),
            listOf("turn on screen", "wake the screen"), listOf("turn off screen", "screen off"),
            forbid = setOf(C.SPLIT, C.BRIGHTNESS, C.HUD, C.DAY, C.NIGHT, C.THEME, C.CAMERA, C.DARK), implicitOn = false,
            action = { Screen(it) }, sayOn = "Screen on", sayOff = "Turning off the screen")

        simple("split_screen", Category.SCREEN, "Split screen", listOf("split screen", "split the screen"),
            listOf(p(g(C.SPLIT))), optional = setOf(C.SCREEN, C.ON),
            action = { SplitScreen }, say = { _, _ -> "Splitting the screen" })
        simple("bright_day", Category.SCREEN, "Daytime screen brightness", listOf("daytime screen brightness", "day brightness"),
            listOf(p(g(C.DAY), g(C.SCREEN, C.BRIGHTNESS))), forbid = setOf(C.NIGHT, C.LIGHT, C.RUNNING, C.THEME),
            optional = setOf(C.MODE, C.SCREEN, C.BRIGHTNESS),
            action = { Brightness(night = false) }, say = { _, _ -> "Daytime screen brightness" })
        simple("bright_night", Category.SCREEN, "Night screen brightness", listOf("night screen brightness", "dim the screen for night"),
            listOf(p(g(C.NIGHT), g(C.SCREEN, C.BRIGHTNESS))), forbid = setOf(C.DAY, C.THEME, C.LIGHT),
            optional = setOf(C.MODE, C.SCREEN, C.BRIGHTNESS),
            action = { Brightness(night = true) }, say = { _, _ -> "Night screen brightness" })
        simple("theme_light", Category.SCREEN, "Light theme", listOf("light theme", "light mode"),
            listOf(p(g(C.LIGHT), g(C.THEME, C.MODE))), forbid = setOf(C.DARK, C.NIGHT, C.ON, C.OFF, C.AMBIENT, C.HIGH_BEAM),
            action = { Theme(dark = false) }, say = { _, _ -> "Switching to the light theme" })
        simple("theme_dark", Category.SCREEN, "Dark theme", listOf("dark theme", "dark mode", "night mode"),
            listOf(p(g(C.DARK), g(C.THEME, C.MODE)), p(g(C.NIGHT), g(C.THEME, C.MODE)), p(g(C.DARK))),
            forbid = setOf(C.LIGHT, C.ON, C.OFF, C.BRIGHTNESS, C.DAY),
            optional = setOf(C.THEME, C.MODE),
            action = { Theme(dark = true) }, say = { _, _ -> "Switching to the dark theme" })

        toggle("hud", Category.SCREEN, "Turn on head up display", "Turn off head up display", listOf(g(C.HUD)),
            listOf("turn on head up display", "hud on"), listOf("turn off head up display", "hud off"),
            optional = setOf(C.SCREEN),
            action = { HeadUpDisplay(it) }, sayOn = "Head up display on", sayOff = "Head up display off")

        // ============================= APPS =============================
        // Resolved dynamically against installed apps by IntentMatcher (no fixed pattern).
        add(CommandSpec(APP_LAUNCH_ID, Category.APPS, "Open [app name]", listOf("open spotify", "launch maps", "open youtube"),
            patterns = emptyList(), dynamic = "any installed app",
            action = { error("resolved by IntentMatcher") },
            reply = { _, o -> if (o.ok) "Opening ${o.detail}" else "Sorry, I couldn't open that app" }))
    }

    const val APP_LAUNCH_ID = "app_open"

    val byId: Map<String, CommandSpec> = all.associateBy { it.id }
}
