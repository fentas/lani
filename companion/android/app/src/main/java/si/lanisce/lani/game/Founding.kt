package si.lanisce.lani.game

import si.lanisce.lani.game.culture.Cultures

/**
 * Where a new village is founded (plan 2, "Jan's decisions": the learner chooses where their town is; GAME.md, "The choice
 * at setup"): in a region, one of the culture packs of the village's language ([culture]: the one the learner's node
 * plays), on a landscape the region offers ([landscape], see [choices]), with a [seed] for the village's dice and its land,
 * rerolled ([reroll]) until the learner likes the place. Pure: the setup screen shows [preview], [village] founds it.
 */
data class Founding(val culture: String, val landscape: Landscape, val seed: Long) {
    /** Another place: the next seed of a fixed sequence, so the same seed always rolls the same next one. */
    fun reroll(): Founding = copy(seed = next(seed))

    /** The same place on [other] (the seed stays: its dice and its land's own numbers). */
    fun on(other: Landscape): Founding = copy(landscape = other)

    /** The village as it will be founded, at its first age: what the preview draws. */
    fun preview(): GameState = village(0L)

    /** The new village, founded [now]: its dice and its land from [seed] (see [GameEngine.newGame]). */
    fun village(now: Long): GameState = GameEngine.newGame(seed, now, landscape)

    companion object {
        /** The landscapes [culture] offers a new village: every one, the coast where its horizon shows the sea. */
        fun choices(culture: String): List<Landscape> = Landscape.choices(sea = seaIn(culture))

        /** Whether [culture]'s horizon shows the sea (its world.json `backdrop.sea`); false for a pack the app can't read. */
        fun seaIn(culture: String): Boolean = runCatching { Cultures.load(culture).world.backdrop?.sea == true }.getOrDefault(false)

        /**
         * The first choice for a village in [culture] with the dice's [seed]: the landscape the node [suggested]
         * (`lani-profile add --landscape`) when the region offers it, else the region's usual one ([Landscape.usual]).
         */
        fun start(culture: String, suggested: String?, seed: Long): Founding {
            val choices = choices(culture)
            val pick = Landscape.entries.firstOrNull { it.id == suggested }?.takeIf { it in choices }
                ?: Landscape.usual(culture).takeIf { it in choices } ?: choices.first()
            return Founding(culture, pick, seed)
        }

        /** The seed after [seed] (SplitMix64): a reroll's, the same every time. */
        fun next(seed: Long): Long {
            var z = seed + -0x61c8864680b583ebL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}
