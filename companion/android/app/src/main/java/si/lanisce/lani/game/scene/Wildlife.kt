package si.lanisce.lani.game.scene

import si.lanisce.lani.game.render.Env
import java.time.LocalDate

/**
 * The wild animals of the outdoor scenes (companion/SCENES.md, "Wild animals"): who is about depends on the time of day
 * (the hare and the butterflies by day, the deer, the fox and the hedgehog at dawn and dusk, the boar, the badger, the
 * dormouse and the bats at night; very rarely the bear's eyes at the forest's edge), on the month (the hedgehog, the
 * dormouse, the bats and the bear sleep through the winter) and on the day's dice: each part of the day has its own mix,
 * the same all through it and different the next day, in each village. Most are words of their scene (an object slot of
 * the art, [Animal.word]): tapped, they show their word, and they are there to find only while they are out.
 *
 * Pure: the painters draw the animals [out] (or those the frame names, [SceneFrame.wild]) at the place [spot] gives.
 */
object Wildlife {
    /** The animals' parts of the day, by the scenes' sun ([Env.sunrise], [Env.sunset]): dawn and dusk the hours round them. */
    enum class Part { DAWN, DAY, DUSK, NIGHT }

    /**
     * One animal of an art: its [id] (the object slot it is drawn in, when a [word]), the parts of the day it is about in,
     * how likely it is out on a day in each of them ([chance]), and the [months] it is awake in.
     */
    data class Animal(val id: String, val parts: Set<Part>, val chance: Float, val months: IntRange = 1..12, val word: Boolean = true)

    private val DAWN_DUSK = setOf(Part.DAWN, Part.DUSK)
    private val TWILIGHT_DAY = setOf(Part.DAWN, Part.DAY, Part.DUSK)
    private val DUSK_NIGHT = setOf(Part.DUSK, Part.NIGHT)

    /** Every art's wild animals. An art not here has none. */
    val animals: Map<String, List<Animal>> = mapOf(
        // the clearing in the beech and spruce forest: the stag steps out of the trees at its far edge
        "forest" to listOf(
            Animal("hare", TWILIGHT_DAY, 0.5f),
            Animal("butterfly", setOf(Part.DAY), 0.7f, 4..9),
            Animal("fox", DAWN_DUSK, 0.45f),
            Animal("stag", DAWN_DUSK, 0.5f),
            Animal("hedgehog", DUSK_NIGHT, 0.4f, 4..10),
            Animal("boar", setOf(Part.NIGHT), 0.5f),
            Animal("badger", setOf(Part.NIGHT), 0.35f, 3..11),
            Animal("dormouse", setOf(Part.NIGHT), 0.5f, 5..10),
            Animal("bat", DUSK_NIGHT, 0.7f, 4..10),
            // two eyes shining in the dark under the trees, now and then; no word of the scene (it is in the pack)
            Animal("bear", setOf(Part.NIGHT), 0.03f, 3..11, word = false),
        ),
        // France's field: the roe deer graze its edge, the boar dig in it at night
        "field" to listOf(
            Animal("hare", TWILIGHT_DAY, 0.55f),
            Animal("butterfly", setOf(Part.DAY), 0.6f, 4..9),
            Animal("deer", DAWN_DUSK, 0.5f),
            Animal("fox", DAWN_DUSK, 0.35f),
            Animal("boar", setOf(Part.NIGHT), 0.45f),
            Animal("bat", DUSK_NIGHT, 0.6f, 4..10),
        ),
        // the pond in the woods: a deer drinks at the far shore, the bats hunt over the water
        "pond" to listOf(
            Animal("butterfly", setOf(Part.DAY), 0.5f, 5..9),
            Animal("deer", DAWN_DUSK, 0.4f),
            Animal("fox", setOf(Part.DAWN), 0.4f),
            Animal("bat", DUSK_NIGHT, 0.8f, 4..10),
        ),
        // the stream below the village
        "stream" to listOf(
            Animal("butterfly", setOf(Part.DAY), 0.5f, 5..9),
            Animal("fox", DAWN_DUSK, 0.35f),
            Animal("hedgehog", DUSK_NIGHT, 0.4f, 4..10),
            Animal("bat", DUSK_NIGHT, 0.7f, 4..10),
        ),
        // the pasture under Triglav: chamois on the scree, an ibex on a far rock
        "alps" to listOf(
            Animal("chamois", TWILIGHT_DAY, 0.6f),
            Animal("ibex", setOf(Part.DAWN, Part.DAY), 0.45f),
        ),
    )

    /** [art]'s animals that are words of its scene: object slots of the art ([SceneArt.objects]). */
    fun slots(art: String): List<String> = animals[art].orEmpty().filter { it.word }.map { it.id }

    /**
     * How many bats a dialog's cue brings over the water or the clearing, its level 0..1 → 0..4: none, and those out
     * aren't there to tap either ([SceneSights.shows]).
     */
    fun bats(cue: Float): Int = (cue * 4f + 0.5f).toInt().coerceIn(0, 4)

    /** Every animal of [art], words or not: what a test or a tool names to see them all ([SceneFrame.wild]). */
    fun all(art: String): Set<String> = animals[art].orEmpty().map { it.id }.toSet()

    /** The part of the day at [hour] (0..24) in [month]: dawn from an hour before sunrise to 1.5 h after, dusk as long round sunset. */
    fun part(hour: Float, month: Int): Part {
        val env = Env(12f, month, false)
        val h = ((hour % 24f) + 24f) % 24f
        return when {
            h >= env.sunrise - 1f && h < env.sunrise + 1.5f -> Part.DAWN
            h >= env.sunrise + 1.5f && h < env.sunset - 1.5f -> Part.DAY
            h >= env.sunset - 1.5f && h < env.sunset + 1f -> Part.DUSK
            else -> Part.NIGHT
        }
    }

    /**
     * The day a part of the day at [hour] of [day] (epoch day) is keyed by: the night after midnight is still the evening
     * before's, so its animals stay till dawn.
     */
    private fun keyDay(day: Long, hour: Float, part: Part): Long = if (part == Part.NIGHT && hour < 12f) day - 1 else day

    /** Which of [art]'s animals are out at [hour] of [day] (epoch day) in [month], in village [seed]: the part of the day's mix. */
    fun out(art: String, seed: Long, day: Long, hour: Float, month: Int): Set<String> {
        val list = animals[art] ?: return emptySet()
        val part = part(hour, month)
        val date = LocalDate.ofEpochDay(keyDay(day, hour, part))
        return list.filter { a -> part in a.parts && month in a.months && Happenings.roll(seed, date, "wild/$art/${a.id}/$part") < a.chance }
            .map { it.id }.toSet()
    }

    /**
     * Where [id] is today in [art], 0..1: the painter's pick among its places (along the forest's edge, which end of the
     * field), the same all through the part of the day, somewhere else another time.
     */
    fun spot(art: String, id: String, seed: Long, day: Long, hour: Float, month: Int): Float {
        val part = part(hour, month)
        return Happenings.roll(seed, LocalDate.ofEpochDay(keyDay(day, hour, part)), "wild/$art/$id/$part/spot")
    }
}
