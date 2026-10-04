package si.lanisce.lani.ui.stage

import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.Res
import si.lanisce.lani.l10n.bi

/**
 * Where the stage stands: a scene art to show behind the companion (blurred and dimmed, when a painter
 * draws it), else a gradient in the place's colours ([top] → [bottom], ARGB). [hour] fixes the light
 * (the wolves come at night); null follows the clock.
 */
enum class StagePlace(val art: String?, val top: Long, val bottom: Long, private val key: String, val hour: Float? = null) {
    FIRE("campfire", 0xFF0E1630, 0xFF5A2A12, "place.byFire"),
    NIGHT("campfire", 0xFF070B1C, 0xFF4A1016, "place.byFireAtNight", hour = 22f),
    KITCHEN("kitchen", 0xFF3B2416, 0xFFB9814A, "place.inKitchen"),
    FOREST("forest", 0xFF0F2A1C, 0xFF3E6B3A, "place.inForest"),
    SQUARE("square", 0xFF2E5C8A, 0xFF6E9E5E, "place.underLinden"),
    FIELD("field", 0xFF4A7FB5, 0xFFC9A440, "place.inField"),
    SMITHY("smithy", 0xFF1E1A1A, 0xFF8A3A1A, "place.inSmithy"),
    SCHOOL("school", 0xFF28405C, 0xFF4F7A5C, "place.atSchool"),
    QUARRY(null, 0xFF3A3F4A, 0xFF8C8F94, "place.atQuarry"),
    STORM(null, 0xFF141827, 0xFF4A4E6A, "place.inStorm"),
    ;

    /** "Ob ognju · By the fire", in the pair of the moment. */
    val label: String get() = bi(key)

    companion object {
        /** The place of [run], else where [who] is at home (by their sprite). */
        fun of(run: Run, who: StagePerson): StagePlace = when (run) {
            is Run.Event -> when (run.kind) {
                EventKind.WOLVES -> NIGHT
                EventKind.BEAR -> FOREST
                EventKind.STORM -> STORM
                EventKind.MERCHANT, EventKind.FESTIVAL -> SQUARE
            }
            is Run.Gather -> when (run.res) {
                Res.FOOD -> FIELD
                Res.WOOD -> FOREST
                Res.STONE -> QUARRY
                Res.WISDOM -> SQUARE
            }
            else -> home(who.art)
        }

        /** Where someone with sprite [art] is usually found. */
        fun home(art: String): StagePlace = when (art) {
            "grandma", "aunt", "innkeeper" -> KITCHEN
            "grandpa", "winemaker", "pedlar" -> SQUARE
            "shepherd", "beekeeper", "hunter", "burner" -> FOREST
            "smith" -> SMITHY
            "teacher" -> SCHOOL
            "farmer", "pilgrim" -> FIELD // the pilgrim: on the road past the fields
            else -> FIRE
        }
    }
}
