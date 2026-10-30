package com.offline.bydvoice.nlu

/** Meaning units the matcher reasons about. Commands are defined in terms of these, not phrases. */
enum class C {
    // actions / polarity
    ON, OFF, OPEN, CLOSE, INCREASE, DECREASE, UP, DOWN, RAISE, LOWER, SET, LOCK, UNLOCK,
    FOLD, UNFOLD, VIEW, NOW, PARTIAL, FULL, HALF, QUARTER, STRONG, WEAK, WARMER, COOLER,
    QUESTION, CANCEL, LAUNCH, SAY, THANK, VENTILATE,
    // climate
    AC, AUTO, MODE, FAN, SPEED, WINDSHIELD, CABIN, VENTILATION, TEMPERATURE, DEGREES, AIR, BLOW,
    FACE, FEET, DIRECTION, RECIRC, OUTSIDE, INSIDE, SENSOR,
    // body
    SEAT, HEATING, STEERING_WHEEL, MIRROR, DOOR, CAR, TRUNK, WINDOW, SUNSHADE, ROOF,
    DRIVER, PASSENGER, FRONT, REAR, LEFT, RIGHT, ALL, BOTH,
    // lights
    LIGHT, AMBIENT, DAY, RUNNING, HIGH_BEAM, HAZARD, COLOR,
    // cameras / driving
    CAMERA, NORMAL, REGULAR, ECO, SPORT, SNOW, DRIVE, RECOVERY, WIPER,
    // screen
    SCREEN, SPLIT, BRIGHTNESS, NIGHT, DARK, THEME, HUD,
    // slots
    NUM, PERCENT,
}

object Lexicon {

    /** phrase -> concepts. Multi-word phrases win over their parts (longest match first). */
    val phrases: Map<String, List<C>> = buildMap {
        fun put(concepts: List<C>, vararg words: String) = words.forEach { put(it, concepts) }
        fun put(c: C, vararg words: String) = put(listOf(c), *words)

        // ---- actions / polarity ----
        put(C.ON, "on", "turn on", "switch on", "enable", "enabled", "activate", "start", "power on", "engage", "wake", "wake up")
        put(C.OFF, "off", "turn off", "switch off", "disable", "disabled", "deactivate", "stop", "shut off", "kill", "power off")
        put(C.OPEN, "open", "opened", "roll down", "lower down", "let down", "pop", "pop open")
        put(C.CLOSE, "close", "closed", "shut", "roll up")
        put(C.INCREASE, "increase", "turn up", "higher", "boost", "more", "faster", "stronger", "bump up", "crank up")
        put(C.DECREASE, "decrease", "reduce", "turn down", "slower", "less", "weaker", "bring down")
        put(C.UP, "up")
        put(C.DOWN, "down")
        put(C.RAISE, "raise", "lift", "lift up")
        put(C.LOWER, "lower")
        put(C.SET, "set", "change", "adjust", "put", "make")
        put(C.LOCK, "lock", "locked", "secure")
        put(C.UNLOCK, "unlock", "unlocked")
        put(C.FOLD, "fold", "folded", "fold in", "retract", "tuck", "hold")
        put(C.UNFOLD, "unfold", "unfolded", "fold out", "extend", "deploy")
        put(C.VIEW, "view", "views", "show", "look", "see", "display view", "watch")
        put(C.NOW, "now", "right now", "immediately")
        put(C.PARTIAL, "partially", "partial", "partly", "a bit", "a little", "slightly", "little", "crack", "some")
        put(C.FULL, "fully", "full", "all the way", "completely", "entirely", "wide", "maximum")
        put(C.HALF, "halfway", "half way", "half")
        put(C.QUARTER, "quarter", "a quarter", "quarter way", "quarters")
        put(C.STRONG, "strong", "strongly", "high", "highest", "max", "intense", "hot", "high level")
        put(C.WEAK, "weak", "low", "lowest", "gentle", "mild", "standard", "soft", "min", "minimum")
        put(C.WARMER, "warmer", "hotter", "warm up", "heat up", "too cold", "am cold", "freezing")
        put(C.COOLER, "cooler", "colder", "cool down", "too hot", "am hot", "boiling")
        put(C.QUESTION, "what", "what is", "how", "tell me", "is it", "current")
        put(listOf(C.QUESTION, C.TEMPERATURE), "how hot", "how cold", "how warm")
        put(C.CANCEL, "cancel", "never mind", "nevermind", "forget it", "stop listening", "nothing")
        put(C.LAUNCH, "launch", "run", "go to", "switch to")
        put(C.SAY, "say")
        put(C.THANK, "thank you", "thank", "thanks", "thankyou")
        put(C.VENTILATE, "ventilate", "air out", "freshen up")

        // ---- climate ----
        put(C.AC, "ac", "a c", "dac", "air conditioner", "air conditioning", "aircon", "air con", "climate",
            "climate control", "conditioner", "conditioning", "hvac", "cooling system")
        put(C.AUTO, "auto", "automatic", "automatically", "autumn")
        put(C.MODE, "mode", "modes", "setting")
        put(C.FAN, "fan", "fans", "blower", "fan speed", "air speed", "wind speed", "air volume")
        put(C.SPEED, "speed", "level")
        put(C.WINDSHIELD, "windshield", "windscreen", "wind shield", "wind screen", "front glass",
            "defrost", "defroster", "defog", "demist")
        put(C.CABIN, "cabin", "car interior", "interior")
        put(C.VENTILATION, "ventilation", "ventilated", "ventilating", "vent", "vents", "venting", "cooling", "cooled")
        put(C.TEMPERATURE, "temperature", "temp", "temperatures", "thermostat")
        put(C.DEGREES, "degrees", "degree", "celsius", "fahrenheit")
        put(C.AIR, "air", "airflow", "air flow")
        put(C.BLOW, "blow", "blowing", "point", "direct", "aim")
        put(C.FACE, "face", "faces", "head", "chest", "upper body", "dashboard", "dash")
        put(C.FEET, "feet", "foot", "floor", "legs", "leg", "footwell")
        put(C.DIRECTION, "direction", "directions")
        put(listOf(C.RECIRC), "recirculation", "recirculate", "recirculating", "recirc", "recycle",
            "inner circulation", "internal circulation", "inside air", "circulation")
        put(C.OUTSIDE, "outside", "outdoor", "outdoors", "external", "exterior", "fresh", "out")
        put(C.INSIDE, "inside", "indoor", "indoors", "in car", "in the car", "internal")
        put(C.SENSOR, "sensor", "sensors", "reading")

        // ---- body ----
        put(C.SEAT, "seat", "seats", "chair", "chairs")
        put(C.HEATING, "heating", "heated", "heater", "heat", "warming", "warmer seat", "eat")
        put(C.STEERING_WHEEL, "steering wheel", "steering", "wheel")
        put(C.MIRROR, "mirror", "mirrors", "side mirror", "side mirrors", "wing mirror", "wing mirrors",
            "rear view mirror", "rearview mirror", "rear view mirrors")
        put(C.DOOR, "door", "doors")
        put(C.CAR, "car", "vehicle", "the car")
        put(C.TRUNK, "trunk", "boot", "tailgate", "tail gate", "liftgate", "lift gate", "hatch", "back door", "trunk lid")
        put(C.WINDOW, "window", "windows", "glass")
        put(C.SUNSHADE, "sunshade", "sun shade", "shade", "sun blind", "blind", "curtain", "curtains", "sun screen", "sunscreen")
        put(C.ROOF, "roof", "sunroof", "sun roof", "moonroof", "moon roof", "panoramic roof", "panorama roof", "skylight")
        put(C.DRIVER, "driver", "driving side", "my", "mine", "driver side")
        put(C.PASSENGER, "passenger", "co driver", "codriver", "copilot", "co pilot", "passenger side", "shotgun")
        put(C.FRONT, "front", "forward", "ahead", "front of the car")
        put(C.REAR, "rear", "back", "behind", "backseat", "back seat", "real")
        put(C.LEFT, "left", "left side")
        put(C.RIGHT, "right", "right side")
        put(C.ALL, "all", "every", "everything", "everywhere", "all of them", "entire", "whole")
        put(C.BOTH, "both", "both sides")

        // ---- lights ----
        put(C.LIGHT, "light", "lights", "lamp", "lamps", "lighting", "illumination", "lit")
        put(C.AMBIENT, "ambient", "ambience", "ambiance", "mood", "atmosphere", "atmospheric", "ambient light", "ambient lights")
        put(C.DAY, "day", "daytime", "day time", "daylight")
        put(C.RUNNING, "running")
        put(listOf(C.DAY, C.RUNNING, C.LIGHT), "drl", "drls", "d r l")
        put(C.HIGH_BEAM, "high beam", "high beams", "highbeam", "highbeams", "beam", "beams", "main beam", "main beams", "brights", "full beam")
        put(C.HAZARD, "hazard", "hazards", "hazard light", "hazard lights", "emergency lights", "flashers",
            "four way", "double flash", "warning lights", "blinkers")
        put(C.COLOR, "color", "colour", "colors", "colours")

        // ---- cameras / driving ----
        put(C.CAMERA, "camera", "cameras", "panorama", "panoramic view", "three sixty", "360", "surround view")
        put(C.NORMAL, "normal", "default")
        put(C.REGULAR, "regular", "comfort", "comfortable", "light steering", "soft steering")
        put(C.ECO, "eco", "economy", "economic", "echo", "e co", "efficient")
        put(C.SPORT, "sport", "sports", "sporty", "dynamic")
        put(C.SNOW, "snow", "snowy", "winter", "ice", "icy", "slippery")
        put(C.DRIVE, "drive", "driving", "drive mode", "driving mode")
        put(C.RECOVERY, "energy recovery", "recovery", "regeneration", "regen", "regenerative braking",
            "regenerative", "regenerating", "energy regeneration", "one pedal")
        put(C.WIPER, "wiper", "wipers", "windshield wiper", "windshield wipers", "wiper blades", "wiper arms")

        // ---- screen ----
        put(C.SCREEN, "screen", "display", "monitor", "center screen", "centre screen", "main screen")
        put(C.SPLIT, "split", "split screen", "splitscreen", "side by side", "multi window")
        put(C.BRIGHTNESS, "brightness", "bright", "brighter", "dim", "dimmer", "backlight")
        put(C.NIGHT, "night", "nighttime", "night time", "evening")
        put(C.DARK, "dark", "darker", "black")
        put(C.THEME, "theme", "themes", "appearance", "style")
        put(C.HUD, "hud", "h u d", "head up display", "heads up display", "head up", "heads up", "head-up display", "head of display", "heads of display", "projection")

        // units feeding number slots
        put(C.PERCENT, "percent", "per cent", "%", "percentage")
    }

    /** Colour words -> canonical colour name (for ambient lighting). */
    val colors: Map<String, String> = mapOf(
        "white" to "white", "warm white" to "warm white", "red" to "red", "orange" to "orange",
        "amber" to "orange", "yellow" to "yellow", "gold" to "yellow", "green" to "green",
        "cyan" to "cyan", "teal" to "cyan", "turquoise" to "cyan", "blue" to "blue",
        "light blue" to "light blue", "sky blue" to "light blue", "ice blue" to "light blue",
        "purple" to "purple", "violet" to "purple", "pink" to "pink", "magenta" to "pink",
    )

    /** Words that carry no meaning for matching. Not counted as "unexplained" noise. */
    val stopwords: Set<String> = setOf(
        "the", "a", "an", "please", "can", "could", "would", "you", "me", "to", "for", "of", "in",
        "it", "is", "i", "want", "need", "turn", "switch", "kindly", "hey", "hi", "hello", "okay",
        "ok", "just", "let", "us", "be", "like", "and", "then", "also", "from", "with", "at", "by",
        "this", "that", "into", "onto", "go", "get", "do", "will", "much", "bit", "some", "are",
        "am", "so", "too", "very", "really", "byd", "bid", "bide", "bite", "b", "y", "d", "thing",
        "there", "here", "way", "our", "your", "its", "side", "position",
    )

    /**
     * The BYD command dictionary given to the speech recognizer: domain terms its decoder is nudged
     * towards (it can still write anything). Kept short on purpose — in tests a longer list (every
     * phrase, or terms picked from all catalog examples) was no better and cost accuracy elsewhere.
     */
    val recognizerTerms: List<String> = listOf(
        "BYD", "air conditioning", "AC", "temperature", "ventilation", "recirculation", "windshield",
        "defrost", "steering wheel", "passenger", "driver", "mirrors", "trunk", "sunroof", "sun shade",
        "hazard lights", "ambient light", "cabin light", "daytime running lights", "high beam",
        "camera", "sport mode", "eco mode", "energy recovery", "head up display", "wipers", "fan speed",
    )

    val longestPhraseWords: Int by lazy { phrases.keys.maxOf { it.split(' ').size } }
}
