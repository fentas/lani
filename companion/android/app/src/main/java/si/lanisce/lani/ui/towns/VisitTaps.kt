package si.lanisce.lani.ui.towns

import si.lanisce.lani.game.scene.TownSpots
import si.lanisce.lani.game.sky.SkyTap
import si.lanisce.lani.ui.game.PlaceTap

/**
 * What a tap on a visited town's map opens (docs/plans/05-built/visit-voices.md, "Look and learn on a visit"): the home
 * village's taps that only look or teach, as a guest, in the town's language explained in the visitor's base; what would
 * change the host (build, gather, feed the fire, the chest, a project's step) never: a place's card says "🧳 Tukaj si
 * gost · You're a guest here" instead. Pure: VisitScreen (and a scene of the town: VisitSceneScreen) follows it.
 */
object VisitTaps {
    sealed interface Open {
        /** A place's card, as a guest's: its name, what it is, its story and words, the ways into its scenes. */
        data class Place(val tap: PlaceTap) : Open

        /** Someone of the town: their card (who they are, their greeting, a talk with them). */
        data class Person(val id: String) : Open

        /** A word's card ("🔊", "Add to my words"): a wild animal's, in the town's language. */
        data class Word(val word: String) : Open

        /** The night sky's card: the moon, a star, a constellation, a planet, the Milky Way, a shooting star. */
        data class Sky(val tap: SkyTap) : Open

        /** TalkBack's "🌌 Nebo nocoj · Tonight's sky": what of the sky is up now, to open from a list. */
        data class SkyList(val up: List<SkyTap>) : Open

        /** Nothing: a tap on nothing (the meadow, the far hills with nothing to walk into). */
        data object Nothing : Open
    }

    /**
     * A tap on a place ([tap]; null: nothing there) with [scenes] ways into its scenes: its card, also a building, the fire
     * and a landmark (at home they may open what changes the village: the building's card, the fire's sheet, a project's),
     * and the far hills only when something opens there, as at home.
     */
    fun place(tap: PlaceTap?, scenes: Int): Open = when {
        tap == null -> Open.Nothing
        tap == PlaceTap.Spot(TownSpots.HORIZON) && scenes == 0 -> Open.Nothing
        else -> Open.Place(tap)
    }

    /** A tap on someone of the town: their card when the town has them ([known]: its cast, its residents, today's visitor). */
    fun person(id: String, known: (String) -> Boolean): Open = if (known(id)) Open.Person(id) else Open.Nothing

    /** A tap right on a wild animal: its [word] (Wild.word: the town's language on a visit). */
    fun animal(word: String): Open = if (word.isBlank()) Open.Nothing else Open.Word(word)

    /** A tap on the night sky. */
    fun sky(tap: SkyTap): Open = Open.Sky(tap)

    /** TalkBack's list of the sky (empty: its sheet says nothing is up now, as at home). */
    fun skyList(up: List<SkyTap>): Open = Open.SkyList(up)
}
