package si.lanisce.lani.game

import kotlinx.serialization.Serializable

/**
 * The land a village stands on (plan 2, "Generated worlds"; GAME.md, "The land"): its [kind] of landscape ([Landscape])
 * and the [seed] its layout is generated from ([si.lanisce.lani.game.render.Terrain]), chosen when the village was
 * founded and never changed after. A save from before generated land has none ([GameState.land] null): the next tick
 * marks it [CLASSIC], the fixed valley every village had until then, so it keeps its look pixel for pixel.
 *
 * Its own [seed], not the village's [GameState.seed]: a linked town sees the land (its map) without the seed of the
 * village's dice.
 */
@Serializable
data class Land(val kind: String, val seed: Int = 0) {
    /** The landscape; one this app doesn't know (a newer one) is drawn as the classic valley. */
    val landscape: Landscape get() = Landscape.of(kind)

    companion object {
        /** The fixed valley of every village from before generated land (its seed doesn't matter). */
        val CLASSIC = Land(Landscape.CLASSIC.id)

        /** A new village's land: [landscape] generated from the village's [seed] (the same seed, the same land). */
        fun of(landscape: Landscape, seed: Long): Land =
            if (landscape == Landscape.CLASSIC) CLASSIC else Land(landscape.id, (seed xor (seed ushr 32)).toInt())
    }
}

/**
 * The kinds of land a village can be founded on: the classic valley of the villages from before, and the ones a new
 * village chooses at setup (see [choices]), each generated from the village's seed. [emoji] and the l10n [key] name
 * them.
 */
enum class Landscape(val id: String, val emoji: String, val key: String) {
    /** The fixed valley of the villages from before generated land: a stream on the left, the woods all round. */
    CLASSIC("classic", "🏞️", "landscape.classic"),
    /** A valley with a stream: the classic composition, its stream, road, clearing, plots and woods from the seed. */
    VALLEY("valley", "🏞️", "landscape.valley"),
    /** Gentle rises, vineyards' terraces on the slopes behind the village, beech woods, rolling hills at the horizon. */
    HILLS("hills", "🍇", "landscape.hills"),
    /** A lake at the clearing's right, a path along its shore, the Alps behind it. */
    LAKE("lake", "🛶", "landscape.lake"),
    /** Steeper: a torrent, spruce woods with pastures and boulders, the Alps close at the horizon. */
    MOUNTAINS("mountains", "🏔️", "landscape.mountains"),
    /** The sea along the left, a beach, the stream's mouth, the sea at the horizon; only where the region has the sea. */
    COAST("coast", "🏖️", "landscape.coast");

    companion object {
        fun of(id: String?): Landscape = entries.firstOrNull { it.id == id } ?: CLASSIC

        /** What a new village may be founded on in a region: every landscape, the coast only where it has the sea. */
        fun choices(sea: Boolean): List<Landscape> = listOfNotNull(VALLEY, HILLS, LAKE, MOUNTAINS, COAST.takeIf { sea })

        /**
         * A region's usual landscape, chosen first at setup: the Collio's hills for Friuli, the lakes of Carinthia and the
         * Lake District, a valley with a stream for Primorska and any other.
         */
        fun usual(culture: String): Landscape = when (culture) {
            "friuli" -> HILLS
            "kaernten", "lakeland" -> LAKE
            else -> VALLEY
        }
    }
}
