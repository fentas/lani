package si.lanisce.lani.game.ambient

import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.scene.Lightning
import si.lanisce.lani.game.scene.Sky
import kotlin.math.pow

/**
 * The thunder after the lightning (companion/GAME.md, "Background sounds"): the storm is far off, so a low rumble comes a
 * few seconds after each flash the picture shows, from the same flashes ([Lightning]: the painters draw them from it).
 * Quieter in a room, a little in the cellar. Pure: the player (data/AmbientPlayer.kt) looks at the picture's clock, takes
 * the thunder of the flashes since it last looked and plays each when its time comes.
 */
object Thunder {
    /** The soonest a rumble follows its flash (s): about a kilometre off … */
    const val SOONEST = 2.5

    /** … and the latest: two kilometres. */
    const val LATEST = 6.0

    /**
     * The flashes over [place] under [sky], or null where none shows: a clear sky; a newer art's picture (a placeholder
     * draws no weather). The village flashes in its storm too.
     */
    fun lightning(place: Place, sky: Sky): Lightning? = when (place) {
        is Place.Village -> if (place.storm || sky.lightning) Lightning.village(place.storm) else null
        is Place.Scene -> if (sky.lightning && place.art in Soundscape.SPOTS) Lightning.SCENE else null
    }

    /**
     * How much of the thunder is heard at [place], 0..1: all of it in the open and under the tent's canvas; through a
     * room's walls about half (a low rumble comes through walls better than the rain, [Soundscape.Spot.weather]), a fifth
     * in the cellar.
     */
    fun heard(place: Place): Float = when (place) {
        is Place.Village -> 1f
        is Place.Scene -> (Soundscape.SPOTS[place.art]?.weather ?: 1f).coerceIn(0f, 1f).pow(0.75f)
    }

    /**
     * The thunder of flash [bolt] of [lightning], heard at [heard] ([Thunder.heard]): how far off the bolt struck decides
     * when it comes ([SOONEST] … [LATEST] s after the flash), how strong it is (the farthest at a bit over half) and how
     * deep (played a little slower). The three rumbles take turns.
     */
    fun of(lightning: Lightning, bolt: Int, heard: Float = 1f): Cue {
        val far = Noise.rnd(bolt, 91) // 0: the nearest, 1: the farthest
        return Cue(
            Sound.THUNDER[bolt.mod(Sound.THUNDER.size)],
            at = lightning.at(bolt) + SOONEST + (LATEST - SOONEST) * far,
            gain = heard.coerceIn(0f, 1f) * (1f - 0.45f * far),
            rate = 1.06f - 0.16f * far,
        )
    }

    /** The thunder of the flashes of [lightning] that began in [from, to) (the picture's clock), each once. */
    fun after(lightning: Lightning, from: Double, to: Double, heard: Float = 1f): List<Cue> =
        lightning.between(from, to).map { of(lightning, it, heard) }
}
