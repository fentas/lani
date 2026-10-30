package si.lanisce.lani.game.ambient

import si.lanisce.lani.game.scene.Strike
import si.lanisce.lani.game.scene.Sky
import kotlin.math.PI
import kotlin.math.ceil

// Short sounds where the picture already shows one (companion/GAME.md, "Background sounds"): the thunder after a flash
// (Thunder.kt), a bell swinging and ringing, the anvil struck, the cow shaking its bell, the owl's hoot, the marmot's
// whistle, the pot's lid, the cat's purr, a fire catching; the square's cat mewing up the linden, its dog's bark, a cart's
// hooves, the band's polka, the fountain's water; the music of an age's celebration (the harmonika's polka and waltz,
// the band's polka with the bells). Pure: which sound, when (on the picture's clock, as the painter draws it) and how
// loud; the player (data/AmbientPlayer.kt) plays them.

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

    /** The cat stuck up the square's linden, mewing twice. */
    MEOW("meow"),

    /** The square's dog barking twice: "hov, hov!". */
    BARK("bark"),

    /** A horse pulling a cart over the cobbles, coming nearer and stopping. */
    HOOVES("hooves"),

    /** The village band playing a few bars of a polka. */
    POLKA("polka"),

    /** The water starting to run from the fountain's pipes into its trough. */
    GUSH("gush"),

    /** A polka on the harmonika (a button accordion): a village party's (an age's celebration, game/AgeUps). */
    HARMONIKA("harmonika"),

    /** A slow waltz on the harmonika by the fire (an age's celebration). */
    VALCEK("valcek"),
    ;

    companion object {
        /** The rumbles of the thunder, taking turns. */
        val THUNDER = listOf(THUNDER_1, THUNDER_2, THUNDER_3)
    }
}

/**
 * [sound] beginning at [at] (seconds on the picture's clock: [si.lanisce.lani.game.scene.SceneFrame.time], the village's
 * frame time), at [gain] (0..1, before the level of the whole), played [rate] times as fast (and as high). Whether it
 * ducks under a voice is up to whoever queues it (si.lanisce.lani.data.Ambience: a dialog's effect doesn't, [Sfx.fx]).
 */
data class Cue(val sound: Sound, val at: Double, val gain: Float = 1f, val rate: Float = 1f)

object Sfx {
    /**
     * What a tap on [id] of [art] sounds like, its reaction [step] begun at [start] (SCENES.md, "Pokes"), timed as the
     * painter draws it: the square's bell and the church's rope ring as long as the bell swings, a strike at each end of its
     * swing; the anvil rings, then twice; the cow's bell clanks as she shakes her head (her lowing has no sound); the
     * marmot whistles; the owl hoots (its third reaction); the pot's lid rattles, and after its hop when it boils over; the
     * cat purrs (its second reaction); the campfire flares up; the square's dog barks as he jumps up (his second). Nothing
     * for the rest. [look]: the village's culture's look ([si.lanisce.lani.game.scene.CultureLook]), where it draws the thing
     * otherwise: the Lake District's rabbit in the marmot's place makes no sound.
     */
    fun poke(art: String, id: String, step: Int, start: Double, look: si.lanisce.lani.game.scene.CultureLook? = null): List<Cue> = when ("$art/$id") {
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
        "alps/marmot" -> if (step == 0 && look?.variant(art, id) == null) listOf(Cue(Sound.WHISTLE, start + 0.3)) else emptyList()
        // ForestPainter: the third tap turns its head and hoots, the calls going out from 0.6 s
        "forest/owl" -> if (step == 2) listOf(Cue(Sound.HOOT, start + 0.6)) else emptyList()
        // RoomPainter.pot: the lid rattles for a second; boiling over it hops (down again at 0.42 s), then rattles
        "kitchen/pot" -> listOf(if (step == 0) Cue(Sound.LID, start, 0.85f) else Cue(Sound.LID, start + 0.42, 1f, 0.94f))
        // RoomPainter.catAround: the second tap, eyes shut, hearts rising
        "kitchen/cat" -> if (step == 1) listOf(Cue(Sound.PURR, start + 0.15, 0.9f)) else emptyList()
        // CampfirePainter: the fire leaps up in a burst of sparks, higher the second time
        "campfire/fire" -> listOf(Cue(Sound.WHOOSH, start, if (step == 0) 0.6f else 0.85f))
        // SquarePainter.dog: the second tap, he jumps up and barks, the bark's rings going out at once
        "square/dog" -> if (step == 1) listOf(Cue(Sound.BARK, start + 0.05, 0.7f)) else emptyList()
        else -> emptyList()
    }.filter { it.gain >= MIN_GAIN }

    /**
     * What a dialog's effects sound like as they come on (SCENES.md, "Effects"): each effect of [after] that rose by at
     * least [RISE] since [before] (the same scene's, the cue before), at [now]. A bell rings a peal of a few seconds, a
     * strike at each end of its swing as the painter swings it (far off over the field, the stream, the hills, the lake and
     * the woods); the cows' bells clank, the sheep's clink (the stream's flock as it comes down to the water and goes home, Luka's past the camp); the oven, the stove and the forge catch with a whoosh, the campfire as it flares
     * up; the smith strikes the anvil a few times with the sparks; on the square the cat up the linden mews, Muri barks as he
     * comes back and as he wags, a cart's horse clops up the cobbles, the band plays its polka and the fountain's water
     * gushes. Only the cue's first seconds: the picture may go on swinging, the sound doesn't. They don't duck under the
     * voice (si.lanisce.lani.data.Ambience queues them so): it is saying the line that rings the bell, and ducked the peal
     * went unheard ("did not ring a bell"); at the background's own level it is still well under it.
     */
    fun fx(art: String, before: Map<String, Float>, after: Map<String, Float>, now: Double, count: Int? = null): List<Cue> {
        val out = ArrayList<Cue>()
        // the square's clock striking the hour: a stroke of its bell each time the painter swings it ([Strike]), as many as
        // the dialog counts, else the hour now
        if (art == "square" && (after["strike"] ?: 0f) > (before["strike"] ?: 0f)) {
            val n = count ?: Strike.hour12(java.time.LocalTime.now().hour.toFloat())
            val from = (before["strike"] ?: 0f) * Strike.SPAN
            for (i in 0 until n) {
                val at = Strike.FIRST + i * Strike.EVERY
                if (at >= from) out += Cue(Sound.BELL, now + (at - from) + Strike.EVERY * 0.25, 0.8f, 0.92f)
            }
        }
        for ((name, level) in after) {
            // the square's cat, set up the linden (her 0 is up there), mews as the painter's rings go out: twice
            if (art == "square" && name == "cat" && name !in before && level <= 0.1f) { out += mews(now); continue }
            val was = before[name] ?: 0f
            if (name == "strike" || level < 0.05f || level - was < RISE) continue
            val l = level.coerceIn(0f, 1f)
            when (name) {
                "bell" -> (BELLS[art] ?: VILLAGE_BELL.takeIf { art == VILLAGE })?.let { b -> out += peal(b, now, l) }
                "cowbells" -> if (art == "alps") out += herd(now, l)
                "sheepbells" -> when (art) {
                    "campfire" -> out += sheepBells(now, l, bells = 3, gain = 0.32f)
                    "forest" -> out += sheepBells(now, l, bells = 1, gain = 0.55f)
                }
                // the stream: the flock's bells clink as they come down to the water and as they go home (the campfire: as Luka's
                // come out of the trees and go down the path); the last one's alone
                "flock" -> if (art == "stream" || art == "campfire") out += sheepBells(now, 1f, bells = 3, gain = 0.4f)
                "straggler" -> if (art == "stream") out += sheepBells(now, 1f, bells = 1, gain = 0.45f)
                "oven", "stove", "forge" -> out += Cue(Sound.WHOOSH, now, 0.45f + 0.4f * l)
                "flare" -> if (art == "campfire") out += Cue(Sound.WHOOSH, now, (0.3f + 0.6f * (level - was)).coerceAtMost(0.85f))
                "sparks" -> if (art == "smithy") out += blows(now, l)
                // the workshop's hammer on a nail
                "hammer" -> if (art == "workshop") out += knocks(now, l)
                // the square: Muri barks as he comes back and as he wags; the cart's horse comes clopping up the cobbles
                // (the cart's way takes as long as its sound); the band strikes up, two rounds of its polka; the fountain's
                // water gushes as it comes
                "dog", "wag" -> if (art == "square") out += Cue(Sound.BARK, now + 0.25, 0.7f * l)
                "cart-pump", "cart-stone" -> if (art == "square") out += Cue(Sound.HOOVES, now, 0.9f)
                "band" -> if (art == "square") { out += Cue(Sound.POLKA, now + 0.1, 0.8f * l); out += Cue(Sound.POLKA, now + 0.1 + POLKA_S, 0.65f * l) }
                "fountain" -> if (art == "square") out += Cue(Sound.GUSH, now + 0.2, 0.75f * l)
            }
        }
        // Muri coming back and wagging at once barks once
        return out.distinctBy { it.sound to it.at }
    }

    /** The cat's two mews from [now], when the painter's rings go out: a mew starts each time (t + 0.4) mod 2.2 is 0. */
    private fun mews(now: Double): List<Cue> {
        val first = MEW_EVERY * ceil((now + 0.4 + 0.1) / MEW_EVERY) - 0.4
        return listOf(Cue(Sound.MEOW, first, 0.8f), Cue(Sound.MEOW, first + MEW_EVERY, 0.65f, 1.04f))
    }

    /**
     * The music of an age's celebration as its decorations go up (companion/GAME.md "Ages and their celebrations"), from
     * [now]: the harmonika's waltz or polka, or the band's polka with the church bell's peal (the town's procession); the
     * market's hand bell rings the fair in first ([fair]).
     */
    fun music(m: si.lanisce.lani.game.Music, now: Double, fair: Boolean = false): List<Cue> = buildList {
        var at = now
        if (fair) {
            val b = BELLS.getValue("market")
            addAll(strikes(b.swing, now + 0.05, now + 1.2).map { Cue(b.sound, it, b.gain, b.rate) })
            at = now + 1.0
        }
        when (m) {
            si.lanisce.lani.game.Music.VALCEK -> add(Cue(Sound.VALCEK, at, 0.9f))
            si.lanisce.lani.game.Music.HARMONIKA -> add(Cue(Sound.HARMONIKA, at, 0.9f))
            si.lanisce.lani.game.Music.GODBA -> {
                add(Cue(Sound.POLKA, at, 0.9f))
                addAll(peal(BELLS.getValue("church"), at + POLKA_S - 0.4, 0.7f))
            }
        }
    }

    /** The sounds of [m] ([music]), to have them ready before the moment needs them. */
    fun musicSounds(m: si.lanisce.lani.game.Music): Set<Sound> = when (m) {
        si.lanisce.lani.game.Music.VALCEK -> setOf(Sound.VALCEK, Sound.BELL)
        si.lanisce.lani.game.Music.HARMONIKA -> setOf(Sound.HARMONIKA, Sound.BELL)
        si.lanisce.lani.game.Music.GODBA -> setOf(Sound.POLKA, Sound.BELL)
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
                "kitchen", "livingroom" -> out += Sound.WHOOSH
                "campfire" -> { out += Sound.WHOOSH; out += Sound.COWBELL }
                "forest" -> out += Sound.COWBELL
                "stream" -> out += Sound.COWBELL
                "smithy" -> { out += Sound.WHOOSH; out += Sound.ANVIL }
                "workshop" -> out += Sound.ANVIL
                "square" -> out += SQUARE_SOUNDS
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

    /**
     * The bell rung over the village ([VILLAGE]: a project's step without its scene, ProjectStepScene): the one it hangs, the
     * chapel's little bell, small and bright, swinging quickly.
     */
    internal val VILLAGE_BELL = Bell(Sound.BELL, swing = 7.5, gain = 0.6f, rate = 1.9f)

    /** The sounds of each art's pokes ([poke]). */
    private val POKE_SOUNDS: Map<String, Set<Sound>> = mapOf(
        "square" to setOf(Sound.BELL, Sound.BARK),
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

    /**
     * Sheep's bells ringing ([level]): [bells] small ones (a cow's bell played faster and higher) clinking as the sheep move,
     * each its own way, for 3 s, at [gain]: one close by in the forest (the first: at the ends of its swing as the painter
     * swings it, [SHEEP_BELL_SWING]), the flock's far off on the pasture.
     */
    private fun sheepBells(now: Double, level: Float, bells: Int, gain: Float): List<Cue> = (0 until bells).flatMap { k ->
        strikes(SHEEP_BELL_SWING + 1.3 * k, now + 0.1 + 0.23 * k, now + 3.0, phase = 1.7 * k).mapIndexed { i, t ->
            val g = level * gain * (1f - 0.15f * k) * (1f - ease(t - now, 2.0, 3.0))
            Cue(Sound.COWBELL, t, g, (1.6f + 0.09f * k) * (if (i % 2 == 0) 1f else 1.04f))
        }
    }.filter { it.gain >= MIN_GAIN }.sortedBy { it.at }

    /**
     * The hammer driving a nail in the workshop (WorkshopPainter.lift: a blow every [HAMMER_BLOW], landing at its whole
     * multiples): a short bright knock each, the anvil's ring played quick and high, for 3 s.
     */
    private fun knocks(now: Double, level: Float): List<Cue> {
        val first = ceil(now / HAMMER_BLOW)
        return generateSequence(first) { it + 1.0 }.map { it * HAMMER_BLOW }.takeWhile { it < now + 3.1 }
            .map { t -> Cue(Sound.ANVIL, t, 0.45f * level * (1f - ease(t - now, 1.8, 3.1)), 2f) }
            .filter { it.gain >= MIN_GAIN }.toList()
    }

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

    /** The bell under the lost sheep's chin swings as sin(9.5 t) (ForestPainter.sheepBell swings it so): it clinks at each end. */
    const val SHEEP_BELL_SWING = 9.5

    /** The cow shakes her head as sin(14 a) (AlpsPainter). */
    private const val COW_SHAKE = 14.0

    /** A blow on the anvil every 0.62 s while the sparks fly (SmithyPainter.anvilSparks). */
    private const val SMITH_BLOW = 0.62

    /** A blow of the workshop's hammer every this many seconds (WorkshopPainter's swing keeps to it). */
    const val HAMMER_BLOW = 0.75

    /** The "art" of the bell a step over the village rings ([BELLS]): no scene's, the village's own little bell. */
    const val VILLAGE = "village"

    /** The sounds the square's dialogs bring (its effects: the cat, Muri, a cart, the band, the fountain). */
    private val SQUARE_SOUNDS = setOf(Sound.MEOW, Sound.BARK, Sound.HOOVES, Sound.POLKA, Sound.GUSH)

    /** The cat up the linden mews every 2.2 s (SquarePainter.catInTree's rings). */
    private const val MEW_EVERY = 2.2

    /** How long the band's polka plays (polka.ogg, its last chord died away). */
    private const val POLKA_S = 7.7
}
