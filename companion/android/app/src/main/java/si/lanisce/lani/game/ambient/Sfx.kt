package si.lanisce.lani.game.ambient

import si.lanisce.lani.game.scene.Sky
import kotlin.math.PI
import kotlin.math.ceil

// Short sounds where the picture already shows one (companion/GAME.md, "Background sounds"): the thunder after a flash
// (Thunder.kt), a bell swinging and ringing, the anvil struck, the cow shaking its bell, the owl's hoot, the marmot's
// whistle, the pot's lid, the cat's purr, a fire catching. Pure: which sound, when (on the picture's clock, as the painter
// draws it) and how loud; the player (data/AmbientPlayer.kt) plays them.

/**
 * A short sound, played once: `assets/ambient/<file>.ogg`, synthesized by tools/ambient_sounds.py. Its loudest moment is
 * 2 dB under a loop at its fullest (a thunder's as loud as one), so a sound at gain 1 is never louder than the ambience's
 * loudest layer.
 */
enum class Sound(val file: String) {
    THUNDER_1("thunder-1"),
    THUNDER_2("thunder-2"),
    THUNDER_3("thunder-3"),

    /** One strike of a church bell; played faster, the market's hand bell. */
    BELL("bell"),

    /** A church bell heard from far off over the land. */
    BELL_FAR("bell-far"),
    ANVIL("anvil"),

    /** One clank of a cow's bell. */
    COWBELL("cowbell"),

    /** The owl's "hu … hu-hu-hooo". */
    HOOT("hoot"),

    /** The marmot's whistle, and its echo off the slopes. */
    WHISTLE("whistle"),

    /** The pot's lid rattling on its rim, and a puff of steam. */
    LID("lid"),
    PURR("purr"),

    /** A fire catching or flaring up. */
    WHOOSH("whoosh"),
    ;

    companion object {
        /** The rumbles of the thunder, taking turns. */
        val THUNDER = listOf(THUNDER_1, THUNDER_2, THUNDER_3)
    }
}

/**
 * [sound] beginning at [at] (seconds on the picture's clock: [si.lanisce.lani.game.scene.SceneFrame.time], the village's
 * frame time), at [gain] (0..1, before the level of the whole), played [rate] times as fast (and as high).
 */
data class Cue(val sound: Sound, val at: Double, val gain: Float = 1f, val rate: Float = 1f)

object Sfx {
    /**
     * What a tap on [id] of [art] sounds like, its reaction [step] begun at [start] (SCENES.md, "Pokes"), timed as the
     * painter draws it: the square's bell and the church's rope ring as long as the bell swings, a strike at each end of its
     * swing; the anvil rings, then twice; the cow's bell clanks as she shakes her head (her lowing has no sound); the
     * marmot whistles; the owl hoots (its third reaction); the pot's lid rattles, and after its hop when it boils over; the
     * cat purrs (its second reaction); the campfire flares up. Nothing for the rest.
     */
    fun poke(art: String, id: String, step: Int, start: Double): List<Cue> = when ("$art/$id") {
        // SquarePainter: the swing sin(5.5 t) × up in 0.25 s, down from 2 to 3.2 s
        "square/tower" -> strikes(SQUARE_SWING, start + 0.1, start + 3.2).map { t ->
            Cue(Sound.BELL, t, ease(t - start, 0.0, 0.25) * (1f - ease(t - start, 2.0, 3.2)))
        }
        // ChurchPainter.bellLevel: the swing sin(4.2 t) × up from 0.1 to 0.5 s, down over its last 0.9 s
        "church/rope" -> {
            val d = if (step == 0) 2.6 else 3.6
            strikes(CHURCH_SWING, start + 0.15, start + d).map { t ->
                Cue(Sound.BELL, t, ease(t - start, 0.1, 0.5) * (1f - ease(t - start, d - 0.9, d)))
            }
        }
        // SmithyPainter.anvilRing: a ring at once, the second time another 0.65 s after
        "smithy/anvil" -> (0..step.coerceIn(0, 1)).map { b -> Cue(Sound.ANVIL, start + b * 0.65, 1f - 0.15f * b) }
        // AlpsPainter: the second tap shakes her head, sin(14 a), dying down from 1.6 to 2.6 s: a clank at each end
        "alps/cow" -> if (step != 1) emptyList() else strikes(COW_SHAKE, 0.05, 2.6).mapIndexed { i, a ->
            Cue(Sound.COWBELL, start + a, 0.8f * (1f - ease(a, 1.6, 2.6)), if (i % 2 == 0) 0.96f else 1.04f)
        }
        // AlpsPainter: up on its rock it whistles, the sound's rings going out from 0.3 s
        "alps/marmot" -> if (step == 0) listOf(Cue(Sound.WHISTLE, start + 0.3)) else emptyList()
        // ForestPainter: the third tap turns its head and hoots, the calls going out from 0.6 s
        "forest/owl" -> if (step == 2) listOf(Cue(Sound.HOOT, start + 0.6)) else emptyList()
        // RoomPainter.pot: the lid rattles for a second; boiling over it hops (down again at 0.42 s), then rattles
        "kitchen/pot" -> listOf(if (step == 0) Cue(Sound.LID, start, 0.85f) else Cue(Sound.LID, start + 0.42, 1f, 0.94f))
        // RoomPainter.catAround: the second tap, eyes shut, hearts rising
        "kitchen/cat" -> if (step == 1) listOf(Cue(Sound.PURR, start + 0.15, 0.9f)) else emptyList()
        // CampfirePainter: the fire leaps up in a burst of sparks, higher the second time
        "campfire/fire" -> listOf(Cue(Sound.WHOOSH, start, if (step == 0) 0.6f else 0.85f))
        else -> emptyList()
    }.filter { it.gain >= MIN_GAIN }

    /**
     * What a dialog's effects sound like as they come on (SCENES.md, "Effects"): each effect of [after] that rose by at
     * least [RISE] since [before] (the same scene's, the cue before), at [now]. A bell rings a peal of a few seconds, a
     * strike at each end of its swing as the painter swings it (far off over the field, the stream, the hills, the lake and
     * the woods); the cows' bells clank; the oven, the stove and the forge catch with a whoosh, the campfire as it flares
     * up; the smith strikes the anvil a few times with the sparks. Only the cue's first seconds: the picture may go on
     * swinging, the sound doesn't.
     */
    fun fx(art: String, before: Map<String, Float>, after: Map<String, Float>, now: Double): List<Cue> {
        val out = ArrayList<Cue>()
        for ((name, level) in after) {
            val was = before[name] ?: 0f
            if (level < 0.05f || level - was < RISE) continue
            val l = level.coerceIn(0f, 1f)
            when (name) {
                "bell" -> BELLS[art]?.let { b -> out += peal(b, now, l) }
                "cowbells" -> if (art == "alps") out += herd(now, l)
                "oven", "stove", "forge" -> out += Cue(Sound.WHOOSH, now, 0.45f + 0.4f * l)
                "flare" -> if (art == "campfire") out += Cue(Sound.WHOOSH, now, (0.3f + 0.6f * (level - was)).coerceAtMost(0.85f))
                "sparks" -> if (art == "smithy") out += blows(now, l)
            }
        }
        return out
    }

    /** The sounds [place] under [sky] may play, to have them ready before they're wanted (a tap is heard at once). */
    fun sounds(place: Place, sky: Sky): Set<Sound> {
        val out = HashSet<Sound>()
        if (Thunder.lightning(place, sky) != null) out += Sound.THUNDER
        if (place is Place.Scene) {
            POKE_SOUNDS[place.art]?.let { out += it }
            BELLS[place.art]?.let { out += it.sound }
            when (place.art) {
                "alps" -> out += Sound.COWBELL
                "kitchen", "livingroom", "campfire" -> out += Sound.WHOOSH
                "smithy" -> { out += Sound.WHOOSH; out += Sound.ANVIL }
            }
        }
        return out
    }

    /** A rise this much (or more) of an effect's level sounds; a smaller change doesn't. */
    const val RISE = 0.25f

    /** A bell rings this long after its cue (s), its last strikes fading. */
    const val PEAL_S = 5.0

    /**
     * How a bell of an art rings: [sound], played [rate] times as fast, at [gain]; its strikes at the ends of its swing
     * `sin(swing × t)` (the painter's), or, for a bell out of sight whose rings go out in the sky, every [every] seconds
     * as a new ring starts.
     */
    internal data class Bell(val sound: Sound, val swing: Double = 0.0, val every: Double = 0.0, val gain: Float = 1f, val rate: Float = 1f)

    /** Each art's bell (SCENES.md, "Arts": the `bell` effect), as its painter swings it or sends its rings out. */
    internal val BELLS: Map<String, Bell> = mapOf(
        "square" to Bell(Sound.BELL, swing = SQUARE_SWING),
        "church" to Bell(Sound.BELL, swing = CHURCH_SWING),
        // the market's hand bell on its post: small, high and quick
        "market" to Bell(Sound.BELL, swing = 6.0, gain = 0.6f, rate = 1.6f),
        // the church on the hill, the one by the lake: across the valley (a far bell's long tail adds up in a quick peal:
        // under 0.9 it stays under a loop at its fullest)
        "hills" to Bell(Sound.BELL_FAR, swing = 5.0, gain = 0.75f),
        "alps" to Bell(Sound.BELL_FAR, swing = 5.0, gain = 0.7f),
        // the village's bell coming over the field and down to the water (Wild.farBell: a ring every 1 / 1.1 s)
        "field" to Bell(Sound.BELL_FAR, every = 1.0 / 1.1, gain = 0.85f),
        "stream" to Bell(Sound.BELL_FAR, every = 1.0 / 1.1, gain = 0.75f),
        // a bell far beyond the woods (CampfirePainter.distantBell: its rings go out three to each 1.25 s: a strike each)
        "campfire" to Bell(Sound.BELL_FAR, every = 1.25, gain = 0.8f),
    )

    /** The sounds of each art's pokes ([poke]). */
    private val POKE_SOUNDS: Map<String, Set<Sound>> = mapOf(
        "square" to setOf(Sound.BELL),
        "church" to setOf(Sound.BELL),
        "smithy" to setOf(Sound.ANVIL),
        "alps" to setOf(Sound.COWBELL, Sound.WHISTLE),
        "forest" to setOf(Sound.HOOT),
        "kitchen" to setOf(Sound.LID, Sound.PURR),
        "campfire" to setOf(Sound.WHOOSH),
    )

    /** A bell's peal at [level] from [now]: its strikes for [PEAL_S], the last half fading. */
    private fun peal(b: Bell, now: Double, level: Float): List<Cue> {
        val times = if (b.every > 0.0) {
            // the rings start at whole multiples of `every`: the next ones after the cue
            val first = ceil((now + 0.05) / b.every)
            generateSequence(first) { it + 1.0 }.map { it * b.every }.takeWhile { it < now + PEAL_S }.toList()
        } else strikes(b.swing, now + 0.1, now + PEAL_S)
        return times.map { t -> Cue(b.sound, t, b.gain * level * (1f - ease(t - now, PEAL_S / 2, PEAL_S)), b.rate) }
            .filter { it.gain >= MIN_GAIN }
    }

    /** The cows' bells ringing ([level]): three of them swinging (the painter's sin(7 t), each its own way), for 2.5 s. */
    private fun herd(now: Double, level: Float): List<Cue> = (0 until 3).flatMap { k ->
        strikes(7.0, now + 0.05 + 0.13 * k, now + 2.5, phase = 2.1 * k).mapIndexed { i, t ->
            val gain = level * (0.55f - 0.1f * k) * (1f - ease(t - now, 1.5, 2.5))
            Cue(Sound.COWBELL, t, gain, (0.92f + 0.07f * k) * (if (i % 2 == 0) 1f else 1.03f))
        }
    }.filter { it.gain >= MIN_GAIN }.sortedBy { it.at }

    /** The smith's blows on the anvil with the sparks (SmithyPainter.anvilSparks: a blow every 0.62 s), for 3 s. */
    private fun blows(now: Double, level: Float): List<Cue> {
        val first = ceil(now / SMITH_BLOW)
        return generateSequence(first) { it + 1.0 }.map { it * SMITH_BLOW }.takeWhile { it < now + 3.1 }
            .map { t -> Cue(Sound.ANVIL, t, 0.7f * level * (1f - ease(t - now, 1.8, 3.1)), 1.03f) }
            .filter { it.gain >= MIN_GAIN }.toList()
    }

    /**
     * When a bell swinging as `sin(swing × t + phase)` strikes, in [from, to): at each end of its swing, where the sine is
     * at ±1 (the clapper hits the side the bell swings up to).
     */
    internal fun strikes(swing: Double, from: Double, to: Double, phase: Double = 0.0): List<Double> {
        if (swing <= 0.0 || !(to > from)) return emptyList()
        val out = ArrayList<Double>()
        var k = ceil((from * swing + phase - PI / 2) / PI)
        while (true) {
            val t = (PI / 2 + k * PI - phase) / swing
            if (t >= to) break
            if (t >= from) out.add(t)
            k += 1.0
        }
        return out
    }

    /** A 0..1 step from [a] to [b], slow at both ends (PokeArt.ease, the painters' own). */
    private fun ease(v: Double, a: Double, b: Double): Float {
        val x = ((v - a) / (b - a)).coerceIn(0.0, 1.0).toFloat()
        return x * x * (3f - 2f * x)
    }

    /** A strike quieter than this isn't played. */
    private const val MIN_GAIN = 0.08f

    /** The square's bell swings as sin(5.5 t) (SquarePainter), the church's as sin(4.2 t) (ChurchPainter.swing). */
    private const val SQUARE_SWING = 5.5
    private const val CHURCH_SWING = 4.2

    /** The cow shakes her head as sin(14 a) (AlpsPainter). */
    private const val COW_SHAKE = 14.0

    /** A blow on the anvil every 0.62 s while the sparks fly (SmithyPainter.anvilSparks). */
    private const val SMITH_BLOW = 0.62
}
