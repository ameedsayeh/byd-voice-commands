package com.offline.bydvoice.nlu

import com.offline.bydvoice.command.AirMode
import com.offline.bydvoice.command.CameraView
import com.offline.bydvoice.command.Drive
import com.offline.bydvoice.command.CommandCatalog
import com.offline.bydvoice.command.Motion
import com.offline.bydvoice.command.SeatLevel
import com.offline.bydvoice.command.Seats
import com.offline.bydvoice.command.VehicleAction
import com.offline.bydvoice.command.WindowTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentMatcherTest {

    private val apps = AppDirectory { name ->
        listOf("com.spotify.music" to "Spotify", "com.google.android.youtube" to "YouTube", "com.google.android.apps.maps" to "Maps")
            .firstOrNull { (_, label) -> Fuzzy.similarity(label.lowercase(), name.lowercase()) > 0.7 }
    }
    private val matcher = IntentMatcher(apps = apps)

    private fun id(text: String) = matcher.match(text)?.spec?.id
    private fun action(text: String) = matcher.match(text)?.action

    @Test fun everyCatalogExampleMatchesItsOwnCommand() {
        val failures = ArrayList<String>()
        for (spec in CommandCatalog.all) {
            for (ex in spec.examples) {
                val got = matcher.match(ex)
                if (got?.spec?.id != spec.id) failures += "'$ex' -> expected ${spec.id}, got ${got?.spec?.id}   [${Tagger.tag(ex)}]"
            }
        }
        assertTrue("Mismatches (${failures.size}):\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun paraphrasesAndFillers() {
        val cases = mapOf(
            "could you please turn the air conditioning on" to "ac_on",
            "switch off the climate" to "ac_off",
            "it is freezing in here" to "temp_up",
            "can you set it to twenty four degrees please" to "temp_set",
            "put the fan on level four" to "fan_set",
            "fan up" to "fan_up",
            "the fan is too strong turn it down" to "fan_down",
            "i want the air on my feet" to "air_feet",
            "point the air at my face and feet" to "air_face_feet",
            "heat my seat" to "heat_driver_on",
            "turn on the passenger seat heater" to "heat_passenger_on",
            "cool my seat" to null,
            "turn on the seat cooling for the passenger" to "vent_passenger_on",
            "warm up the steering wheel" to "sw_heat_on",
            "lock it" to "doors_lock",
            "open the back door" to "trunk_open",
            "lower my window" to "win_driver_open",
            "put the rear right window up" to "win_rear_right_close",
            "open the sunroof halfway" to "roof_half",
            "show me the back camera" to "cam_rear",
            "switch to sport mode" to "mode_sport",
            "make the regen stronger" to "regen_strong",
            "turn the screen off" to "screen_off",
            "flash the hazards" to "hazards_on",
            "what is the temperature outside right now" to "outside_temp",
        )
        val wrong = cases.filter { (t, want) -> id(t) != want }.map { (t, want) -> "'$t' -> want $want got ${id(t)} [${Tagger.tag(t)}]" }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun toleratesRecognizerMishearings() {
        val cases = mapOf(
            "turn on the air conditionar" to "ac_on",
            "open the sun roof" to "roof_open",
            "turn on wind shield fan" to "windshield_fan_on",
            "ambient colour wite" to "ambient_color",
            "turn off the hazzard lights" to "hazards_off",
            "turn on the steering weel heating" to "sw_heat_on",
            "echo mode" to "mode_eco",
            "turn on the head up display" to "hud_on",
            "temperature twenty to degrees" to "temp_set",
        )
        val wrong = cases.filter { (t, want) -> id(t) != want }.map { (t, want) -> "'$t' -> want $want got ${id(t)} [${Tagger.tag(t)}]" }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    /** Actual (vocab, free) recognizer outputs captured on the emulator from spoken audio. */
    @Test fun realRecognizerOutputs() {
        val cases = listOf(
            listOf("look temperature twenty blue degrees", "said the temperature to twenty two degrees") to "temp_set",
            listOf("make inner bid warmer", "make it a bit warmer") to "temp_up",
            listOf("fan speed four", "fan speed for") to "fan_set",
            listOf("my feet", "there's a my feet") to "air_feet",
            listOf("the driver window halfway", "than the driver window halfway") to "win_driver_half",
            listOf("opened rear right window thirty percent", "open the rewrite right window thirty percent") to "win_rear_right_open",
            listOf("close all windows", "those all windows") to "windows_close",
            listOf("opened sunroof quarter", "open the sunroof a quarter") to "roof_quarter",
            listOf("locked per", "log the car") to "doors_lock",
            listOf("hazard lights", "turn on the hazard lights") to "hazards_on",
            listOf("thirty ambient light to blue", "the the ambient light to blue") to "ambient_color",
            listOf("switch dashboard mode", "switched to sport mode") to "mode_sport",
            listOf("folded mirrors now", "though the mirrors now") to "mirror_fold_now",
            listOf("turn off steering wheel heating", "the off the steering wheel heating") to "sw_heat_off",
            listOf("into face and windshield", "the to face and windshield") to "air_face_windshield",
            listOf("weak energy recovery", "week energy recovery") to "regen_weak",
        )
        val wrong = cases.mapNotNull { (hyps, want) ->
            val got = matcher.matchBest(hyps)?.spec?.id
            if (got != want) "$hyps -> want $want got $got" else null
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    /** Moonshine writes sentences: capitals, punctuation, digits, and the odd glued or misheard word. */
    @Test fun moonshineStyleTranscripts() {
        val cases = mapOf(
            "Set the temperature to 22 degrees." to VehicleAction.TempSet(22.0),
            "Set the temperature to 19.5." to VehicleAction.TempSet(19.5),
            "Fan speed 4" to VehicleAction.FanSet(4),
            "Fanspeed for." to VehicleAction.FanSet(4),
            "Open the rear right window 30%." to VehicleAction.Windows(WindowTarget.REAR_RIGHT, Motion.TO_PERCENT, 30),
            "Open the sunroof aquater." to VehicleAction.Roof(Motion.TO_PERCENT, 25),
            "Show me the real camera." to VehicleAction.Camera(CameraView.REAR),
            "Turn on the head of display." to VehicleAction.HeadUpDisplay(true),
            "Turn off dac." to VehicleAction.AcOff,
            "Hold the mirrors now." to VehicleAction.MirrorFoldNow(true),
            "What is the temperature outside?" to VehicleAction.ReadTemperature(true),
        )
        val wrong = cases.mapNotNull { (text, want) ->
            val got = action(text)
            if (got != want) "'$text' -> want $want got $got   [${Tagger.tag(text)}]" else null
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
        assertEquals(VehicleAction.DriveMode(Drive.ECO), action("Ecomode."))
    }

    @Test fun polarityAndSidesNeverCross() {
        assertEquals("ac_on", id("turn on the ac"))
        assertEquals("ac_off", id("turn off the ac"))
        assertEquals("heat_driver_on", id("turn on driver heating"))
        assertEquals("heat_passenger_off", id("turn off passenger heating"))
        assertEquals("heat_seats_strong", id("strong seats heating"))
        assertEquals("vent_driver_off", id("turn off driver ventilation"))
        assertEquals("cabin_vent_on", id("turn on cabin ventilation"))
        assertEquals("windows_vent", id("ventilate cabin"))
        assertEquals("doors_lock", id("close doors"))
        assertEquals("doors_unlock", id("open doors"))
        assertEquals("windows_close", id("close all windows"))
        assertEquals("win_rear_left_open", id("open rear left window"))
        assertEquals("win_rear_right_open", id("open rear right window"))
        assertEquals("lights_on", id("turn on lights"))
        assertEquals("drl_on", id("turn on daytime running lights"))
        assertEquals("bright_day", id("daytime screen brightness"))
        assertEquals("theme_light", id("light theme"))
        assertEquals("steer_sport", id("sport steering wheel"))
        assertEquals("mode_sport", id("sport mode"))
        assertEquals("mirror_fold", id("fold mirrors"))
        assertEquals("mirror_fold_now", id("fold the mirrors now"))
        assertEquals("ac_auto_off", id("turn off air conditioner auto mode"))
        assertEquals("auto_high_beam_on", id("turn on auto high beams"))
    }

    @Test fun slotsAreExtracted() {
        assertEquals(VehicleAction.TempSet(23.0), action("temperature 23 degrees"))
        assertEquals(VehicleAction.TempSet(22.5), action("set the temperature to twenty two point five"))
        assertEquals(VehicleAction.TempStep(2), action("make it warmer by two degrees"))
        assertEquals(VehicleAction.TempStep(-1), action("make it cooler"))
        assertEquals(VehicleAction.FanSet(3), action("fan speed three"))
        assertEquals(VehicleAction.FanSet(7), action("fan speed nine"))
        assertEquals(VehicleAction.Windows(WindowTarget.DRIVER, Motion.TO_PERCENT, 30), action("open the driver window thirty percent"))
        assertEquals(VehicleAction.Windows(WindowTarget.PASSENGER, Motion.HALF), action("open passenger window halfway"))
        assertEquals(VehicleAction.Windows(WindowTarget.REAR_LEFT, Motion.TO_PERCENT, 25), action("open rear left window a quarter"))
        assertEquals(VehicleAction.Sunshade(40), action("open the sun shade forty percent"))
        assertEquals(VehicleAction.Roof(Motion.TO_PERCENT, 75), action("open the roof three quarters"))
        assertEquals(VehicleAction.AmbientColor("blue"), action("set the ambient lights to blue"))
        assertEquals(VehicleAction.SeatHeat(Seats.DRIVER, SeatLevel.HIGH), action("turn on driver heating level two"))
        assertEquals(VehicleAction.SeatHeat(Seats.PASSENGER, SeatLevel.LOW), action("turn on passenger heating"))
        assertEquals(VehicleAction.AirDirection(AirMode.FACE_WINDSHIELD), action("air to face and windshield"))
        assertEquals(VehicleAction.LaunchApp("com.spotify.music", "Spotify"), action("open spotify"))
    }

    @Test fun outOfDomainIsRejected() {
        listOf("hello how are you", "what time is it", "play some music", "banana telephone", "", "the", "turn", "open")
            .forEach { assertNull("'$it' should not match", id(it)) }
    }

    @Test fun cancelIsDetected() {
        assertTrue(matcher.isCancel(Tagger.tag("cancel")))
        assertTrue(matcher.isCancel(Tagger.tag("never mind")))
    }

    @Test fun numbersParse() {
        fun n(s: String) = Numbers.parse(Text.tokens(s)).map { it.value }
        assertEquals(listOf(22.0), n("twenty two"))
        assertEquals(listOf(22.0), n("twenty to"))
        assertEquals(listOf(20.0), n("set it to twenty"))
        assertEquals(listOf(22.5), n("twenty two point five"))
        assertEquals(listOf(120.0), n("one hundred twenty"))
        assertEquals(listOf(100.0), n("a hundred"))
        assertEquals(listOf(2.0, 3.0), n("two three"))
        assertEquals(listOf(30.0), n("30%"))
        assertEquals(listOf(3.0), n("third"))
    }
}
