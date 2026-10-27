package si.lanisce.lani.game.scene

import si.lanisce.lani.game.Age

/**
 * Who else of the village is by the fire (companion/SCENES.md "Ob ognju by the age", VILLAGERS.md "Round the fire"). The
 * fire's scene has its own people at the spots of its stage (on the benches, between the fire and the tent, behind the
 * stump and the woodpile); the village's others whom their day has at the fire now sit or stand at the crowd's spots round
 * it ([spots]), more of them as the village grows: none round the first campfire, a couple at the camp, the hamlet's log
 * bench, the village's benches and the square round its fire basket. The painter's figures of the village's unnamed people
 * take the spots nobody took on an evening by the fire, and at a feast.
 *
 * The crowd's spots are no part of the stage: a dialog's `act` can't send anyone there, and a tap turn's place is never one.
 */
object FireCrowd {
    /** The crowd's spots are named "crowd-1", "crowd-2" …, the first the nearest the fire's seats. */
    const val PREFIX = "crowd-"

    /**
     * How many spots round the fire the crowd has at [age]: on the benches beside the stage's sitters and standing round
     * it. Where the camp's [tent] still stands by the square's fire, the spots before the homes on its right are the tent's.
     */
    fun count(age: Age, tent: Boolean = false): Int = when (age) {
        Age.OGENJ -> 0
        Age.TABOR -> 2
        Age.ZASELEK -> 3
        Age.VAS -> if (tent) 4 else 5
        Age.TRG, Age.MESTO -> if (tent) 4 else 6
    }

    /** The crowd's spots at [age], in the order they fill ([count]). */
    fun spots(age: Age, tent: Boolean = false): List<String> = (1..count(age, tent)).map { "$PREFIX$it" }

    /** The crowd's spots in [state]'s village: its age, and whether the tent stands by the fire (not moved to the pond). */
    fun spots(state: si.lanisce.lani.game.GameState): List<String> =
        spots(state.age, tent = state.buildings.any { it.type == si.lanisce.lani.game.BuildingType.TENT && it.plot != si.lanisce.lani.game.NO_PLOT })

    /** Whether [spot] is one of the crowd's (not of the stage). */
    fun isCrowd(spot: String): Boolean = spot.startsWith(PREFIX)
}
