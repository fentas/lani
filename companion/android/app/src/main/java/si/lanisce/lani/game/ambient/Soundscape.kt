package si.lanisce.lani.game.ambient

import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.render.Env
import si.lanisce.lani.game.scene.DaySky
import si.lanisce.lani.game.scene.Sky
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// The background sounds of the village and its scenes (companion/GAME.md, "Background sounds"): what is heard where and
// when, from the place on screen, the hour, the month and the weather. Pure: the player (data/Ambience.kt) plays it.

/**
 * One loop of the background sounds: `assets/ambient/<file>.ogg`, a periodic signal of [seconds] (made by
 * tools/ambient_sounds.py; the file holds a quarter second of the loop's end before it and of its start after it).
 * [trim] evens the loops out against each other: they are made at one loudness, and a high thin sound (the crickets)
 * sounds louder than a wide one (the rain) at the same measure, while a phone's speaker loses most of a low one (the
 * fire's roar, the wind: only their crackle and hiss are heard).
 */
enum class Layer(val file: String, val seconds: Int, val trim: Float) {
    BIRDS("birds", 36, 0.8f),
    CRICKETS("crickets", 20, 0.55f),
    OWL("owl", 40, 0.7f),
    FROGS("frogs", 24, 0.55f),
    RAIN("rain", 16, 0.9f),
    STREAM("stream", 18, 0.8f),
    FIRE("fire", 20, 1f),
    WIND("wind", 30, 0.9f),
    SEA("sea", 32, 0.9f),
}

/** Where the learner is: what is on screen. */
sealed interface Place {
    /**
     * The village from above; [sea]: it lies by the coast (its culture's backdrop, the friuli pack's lagoon); [storm]: the
     * storm event is on (its lightning flashes more often than a kept sky's, [si.lanisce.lani.game.scene.Lightning]).
     */
    data class Village(val sea: Boolean = false, val storm: Boolean = false) : Place

    /** A close-up scene of [art] (SCENES.md, "Arts"); [fx]: the effects its dialog has set (the fire flaring, the forge roaring). */
    data class Scene(val art: String, val fx: Map<String, Float> = emptyMap()) : Place
}

object Soundscape {
    /**
     * What a place has of each sound, at its fullest (0..1): [weather] is how much of the rain and the wind outside is
     * heard (1 in the open and under the tent's canvas, less in a room, little in the cellar); [meadow]: grasshoppers
     * chirp there on summer days too.
     */
    internal data class Spot(
        val birds: Float = 0f,
        val crickets: Float = 0f,
        val owl: Float = 0f,
        val frogs: Float = 0f,
        val stream: Float = 0f,
        val fire: Float = 0f,
        val wind: Float = 0f,
        val sea: Float = 0f,
        val weather: Float = 1f,
        val meadow: Boolean = false,
    )

    /** Out in the open, somewhere the table doesn't know (a newer art, a tutor's): birds, crickets, now and then the owl. */
    internal val OPEN = Spot(birds = 0.5f, crickets = 0.5f, owl = 0.3f)

    /** A room: the birds and the crickets outside faint through the window, the rain on the roof. */
    private val ROOM = Spot(birds = 0.18f, crickets = 0.14f, owl = 0.1f, weather = 0.4f)

    /** Each art's sounds (SCENES.md, "Arts"). */
    internal val SPOTS: Map<String, Spot> = mapOf(
        // the fire in the middle, crackling day and night, the woods round it
        "campfire" to Spot(birds = 0.45f, crickets = 0.6f, owl = 0.5f, fire = 0.8f),
        // birdsong thickest, the owl at night, the brook among the trees faint
        "forest" to Spot(birds = 1f, crickets = 0.35f, owl = 0.85f, stream = 0.15f, wind = 0.08f, weather = 0.9f),
        "field" to Spot(birds = 0.75f, crickets = 0.9f, owl = 0.3f, wind = 0.12f, meadow = true),
        // the rain drums on the canvas
        "tent" to Spot(birds = 0.4f, crickets = 0.55f, owl = 0.4f),
        "square" to Spot(birds = 0.45f, crickets = 0.4f, owl = 0.2f),
        "hills" to Spot(birds = 0.6f, crickets = 0.8f, owl = 0.3f, wind = 0.18f, meadow = true),
        // the lagoon's waves; no songbirds over the water
        "sea" to Spot(crickets = 0.2f, wind = 0.25f, sea = 0.85f),
        // the pasture's brook and the wind off the peaks; choughs, no crickets up there
        "alps" to Spot(birds = 0.3f, stream = 0.6f, wind = 0.45f),
        // the stream and the mill
        "stream" to Spot(birds = 0.65f, crickets = 0.5f, owl = 0.35f, frogs = 0.3f, stream = 0.85f),
        // still water: the brook that feeds it faint, the frogs
        "pond" to Spot(birds = 0.65f, crickets = 0.5f, owl = 0.35f, frogs = 0.8f, stream = 0.3f),
        "apiary" to Spot(birds = 0.6f, crickets = 0.5f, owl = 0.3f, meadow = true),
        "market" to Spot(birds = 0.3f, crickets = 0.25f),
        // up on the tower the wind blows
        "watchtower" to Spot(birds = 0.35f, crickets = 0.35f, owl = 0.4f, wind = 0.45f),
        // the rooms: the stove crackles faintly in the kitchen and in the smithy's forge
        "kitchen" to ROOM.copy(fire = 0.25f),
        "livingroom" to ROOM.copy(birds = 0.15f, crickets = 0.12f, weather = 0.35f),
        "workshop" to ROOM,
        "school" to ROOM.copy(owl = 0f),
        "smithy" to ROOM.copy(birds = 0.15f, crickets = 0.1f, owl = 0f, fire = 0.45f),
        // right under the roof: the rain loud on the tiles, the wind round the gable
        "attic" to ROOM.copy(birds = 0.15f, owl = 0.15f, wind = 0.1f, weather = 0.7f),
        // under the ground: only a little of the rain
        "cellar" to Spot(weather = 0.12f),
        // a quiet church
        "church" to Spot(weather = 0.3f),
    )

    /** The village from above: its birds and crickets, the owl from the woods; the sea along the horizon by the coast. */
    private fun village(sea: Boolean) = Spot(birds = 0.6f, crickets = 0.6f, owl = 0.4f, sea = if (sea) 0.35f else 0f)

    /**
     * What plays at [place] at [hour] (local, 0..24) of a day in [month] (1..12) under [sky]: each loop's level, 0..1
     * (loops at 0 are left out). By day the birds sing (more at dawn, fewer in winter); from dusk the crickets chirp in the
     * warm months and the owl calls; the frogs at the water on spring and summer nights. Rain and snow hush them; the rain
     * and the wind are heard as much as the place lets them in. The effects a dialog sets join in: the fire flaring, the
     * stove and the forge fired up, the owl hooting, the sea rising.
     */
    fun mix(place: Place, hour: Float, month: Int, sky: Sky = Sky.CLEAR): Map<Layer, Float> {
        val spot = when (place) {
            is Place.Village -> village(place.sea)
            is Place.Scene -> SPOTS[place.art] ?: OPEN
        }
        val fx = (place as? Place.Scene)?.fx.orEmpty()
        val m = ((month - 1).mod(12)) + 1
        val day = daylight(hour, m)
        val night = 1f - day
        val wet = (sky.rain * 2.5f).coerceIn(0f, 1f) // a shower hushes the birds; by a heavy one all are quiet
        val muffle = (1f - sky.fog * 0.4f) * (1f - sky.snow * 0.6f)

        val birds = spot.birds * day * BIRDS[m - 1] * dawn(hour, m) * (1f - wet) * muffle
        // grasshoppers on a summer meadow by day, crickets from dusk
        val chirping = max(night, if (spot.meadow) day * MEADOW_DAY else 0f)
        val crickets = spot.crickets * chirping * CRICKETS[m - 1] * (1f - wet) * (1f - sky.snow)
        val owl = max(spot.owl * night * OWLS[m - 1], (fx["hoot"] ?: 0f) * 0.9f) * (1f - wet)
        // frogs love the rain: it only halves them
        val frogs = spot.frogs * max(night, day * FROGS_DAY) * FROGS[m - 1] * (1f - wet * 0.5f) * (1f - sky.snow)
        val rain = if (sky.rain < 0.02f) 0f else spot.weather * sqrt(sky.rain)
        val wind = max(spot.wind, spot.weather * sky.wind)
        // the stream runs fuller in the rain
        val stream = spot.stream * (1f + 0.2f * sky.rain)
        val fire = when {
            place !is Place.Scene -> spot.fire
            place.art == "campfire" -> spot.fire + 0.2f * (fx["flare"] ?: 0f)
            place.art == "kitchen" -> max(spot.fire, 0.45f * (fx["oven"] ?: 0f))
            place.art == "livingroom" -> max(spot.fire, 0.35f * (fx["stove"] ?: 0f))
            place.art == "smithy" -> max(spot.fire, 0.75f * (fx["forge"] ?: 0f))
            else -> spot.fire
        }
        val sea = if (spot.sea > 0f) spot.sea + 0.15f * (fx["waves"] ?: 0f) else 0f

        val out = LinkedHashMap<Layer, Float>()
        fun put(l: Layer, v: Float) { val c = v.coerceIn(0f, 1f); if (c >= MIN) out[l] = c }
        put(Layer.BIRDS, birds)
        put(Layer.CRICKETS, crickets)
        put(Layer.OWL, owl)
        put(Layer.FROGS, frogs)
        put(Layer.RAIN, rain)
        put(Layer.STREAM, stream)
        put(Layer.FIRE, fire)
        put(Layer.WIND, wind)
        put(Layer.SEA, sea)
        return out
    }

    /**
     * The weather over the village today: the storm event's (heavy rain, wind), else what a scene's dialog left
     * ([DaySky]: a shower after Luka's hay), else clear.
     */
    fun villageSky(state: GameState?, today: LocalDate): Sky =
        if (state?.event?.kind == EventKind.STORM) STORM else DaySky.of(state, today)

    /** The storm event's weather. */
    val STORM = Sky(rain = 0.9f, wind = 0.7f, gloom = 1f, lightning = true)

    /**
     * How much it is day, 0..1, as the scenes' sun ([Env]) has it: the birds start singing about 45 minutes before the
     * sun rises and are quiet about 45 minutes after it sets; night, the other way round.
     */
    fun daylight(hour: Float, month: Int): Float {
        val env = Env(12f, month, false)
        val h = ((hour % 24f) + 24f) % 24f
        return min(ramp(h, env.sunrise - TWILIGHT, env.sunrise + 0.25f), 1f - ramp(h, env.sunset - 0.25f, env.sunset + TWILIGHT))
    }

    /** The dawn chorus: the birds a quarter louder from half an hour before sunrise to an hour and a half after. */
    private fun dawn(hour: Float, month: Int): Float {
        val rise = Env(12f, month, false).sunrise
        val h = ((hour % 24f) + 24f) % 24f
        return if (h in rise - 0.5f..rise + 1.5f) 1.25f else 1f
    }

    /** 0 before [a], 1 after [b], linear in between. */
    private fun ramp(h: Float, a: Float, b: Float): Float = ((h - a) / (b - a)).coerceIn(0f, 1f)

    /** Hours of twilight on either side of the sun's rising and setting. */
    private const val TWILIGHT = 0.75f

    /** Below this a loop is left out. */
    const val MIN = 0.01f

    /** The grasshoppers of a summer's day on a meadow, against the crickets at night. */
    private const val MEADOW_DAY = 0.45f

    /** The frogs by day, against at night. */
    private const val FROGS_DAY = 0.35f

    // by the month, January first: how many birds sing, the crickets, the frogs and the owl (tawny owls call most in autumn
    // and winter)
    private val BIRDS = floatArrayOf(0.35f, 0.45f, 0.75f, 1f, 1f, 1f, 0.8f, 0.6f, 0.6f, 0.5f, 0.4f, 0.35f)
    private val CRICKETS = floatArrayOf(0f, 0f, 0f, 0f, 0.35f, 0.8f, 1f, 1f, 0.75f, 0.25f, 0f, 0f)
    private val FROGS = floatArrayOf(0f, 0f, 0.3f, 0.8f, 1f, 1f, 0.7f, 0.35f, 0.1f, 0f, 0f, 0f)
    private val OWLS = floatArrayOf(1f, 1f, 0.9f, 0.7f, 0.6f, 0.6f, 0.6f, 0.7f, 0.8f, 1f, 1f, 1f)
}
