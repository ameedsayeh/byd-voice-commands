package com.offline.bydvoice.command

import com.offline.bydvoice.nlu.C
import com.offline.bydvoice.nlu.Tagged

/** Groups shown in the app (the tabs of the voice command list). */
enum class Category(val title: String) {
    CLIMATE("Climate"), AIRFLOW("Air flow"), SEATS("Seats"), STEERING_MIRRORS("Steering & mirrors"),
    DOORS_TRUNK("Doors & trunk"), WINDOWS("Windows"), ROOF_SHADE("Curtain & roof"), LIGHTS("Lights"),
    CAMERAS("Cameras"), DRIVING("Driving"), SCREEN("Screen"), APPS("Apps"),
}

/** All [groups] must be present; a group is satisfied by ANY of its concepts. */
class Pattern(val groups: List<Set<C>>) {
    val concepts: Set<C> get() = groups.flatten().toSet()
}

/** Result of running an action, used to build the spoken reply. */
data class Outcome(val ok: Boolean, val value: Double? = null, val unit: String? = null, val detail: String? = null)

class CommandSpec(
    val id: String,
    val category: Category,
    /** Label shown in the app, as worded in the voice command list. */
    val title: String,
    /** Example phrasings (the first one is shown in the UI). Also used as unit-test fixtures. */
    val examples: List<String>,
    val patterns: List<Pattern>,
    /** If any of these is clearly present the command is ruled out ("on" vs "off", driver vs passenger…). */
    val forbid: Set<C> = emptySet(),
    /** Extra concepts this command can legitimately absorb (counts towards coverage). */
    val optional: Set<C> = emptySet(),
    val needsNumber: Boolean = false,
    val needsColor: Boolean = false,
    /** Short hint for parameterised commands, e.g. "16–32 °". */
    val dynamic: String? = null,
    val action: (Tagged) -> VehicleAction,
    val reply: (Tagged, Outcome) -> String,
) {
    /** Everything this command can account for in an utterance. */
    val explains: Set<C> by lazy {
        patterns.flatMap { it.concepts }.toSet() + optional + COMMON_OPTIONAL +
            (if (needsNumber || C.NUM in optional) setOf(C.NUM, C.DEGREES, C.SPEED) else emptySet())
    }

    companion object {
        /** Filler concepts any command may contain without being penalised. */
        val COMMON_OPTIONAL = setOf(C.SET, C.LAUNCH, C.QUESTION, C.CAR)
    }
}
