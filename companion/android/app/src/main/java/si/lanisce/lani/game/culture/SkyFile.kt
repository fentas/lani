package si.lanisce.lani.game.culture

import kotlinx.serialization.Serializable

// sky.json of a culture pack (lani.sky/v0, companion/GAME.md "The night sky"; the bridge's zod schema is
// companion/bridge/src/sky.ts): where the village lies under the sky, and what its people call what is in it, in the
// pack's language with translations. Optional: a pack without it has the default place (Primorska's) and the catalog's
// names. The stars, the figures and the planets themselves are the app's (game/sky); the file only names them.

/** sky.json: the village's place, the moon's words, the sky's things, the shooting stars. */
@Serializable
data class SkyFile(
    val schema: String,
    /** The pack's language: every text has it. */
    val language: String,
    /** Who wrote which of its languages and who checked them. */
    val review: String? = null,
    val where: SkyWhere,
    /** The word pack of the sky's words (the pack's packs/<id>.json): the card's "Learn them". */
    val pack: String? = null,
    val moon: SkyMoon,
    /** The constellations (figure ids), the Pleiades (m45), bright stars (their proper name, lower case), the planets, the Milky Way. */
    val things: List<SkyThing> = emptyList(),
    val meteors: SkyMeteors? = null,
)

/** Where the village lies (degrees, north and east positive), and the place's [name] ("na Trnovski planoti"). */
@Serializable
data class SkyWhere(val lat: Double, val lon: Double, val name: Text? = null)

/**
 * A line of lore, told at [level] (A1 … B2: the card shows the learner's, else the nearest easier one); a moon's line
 * may belong to one [phase] (new, waxing_crescent, first_quarter, waxing_gibbous, full, waning_gibbous, last_quarter,
 * waning_crescent).
 */
@Serializable
data class SkyLore(val level: String = "A1", val text: Text, val phase: String? = null)

/**
 * The moon: its [name], each phase's name ([phases], by the keys of [SkyLore.phase]), what it does between them
 * ([waxing]: "Luna narašča.", [waning]), lines of lore, the reading that says more ([reading], a reading of the pack:
 * the old almanac), and its [words] (in the pack's language; tapped, their cards).
 */
@Serializable
data class SkyMoon(
    val name: Text,
    val phases: Map<String, Text>,
    val waxing: Text,
    val waning: Text,
    val lore: List<SkyLore> = emptyList(),
    val reading: String? = null,
    val words: List<String> = emptyList(),
)

/**
 * A thing of the sky with a card: [id] a figure's (ori), the Pleiades' (m45), a bright star's (polaris), a planet's
 * (venus) or the Milky Way's (milky-way); its [name], the people's name for it ([folk]: "Kosci", "Gostosevci"), a
 * planet's names as the morning and the evening star ([morning]: "Danica", [evening]: "Večernica"), lore and words.
 */
@Serializable
data class SkyThing(
    val id: String,
    val name: Text,
    val folk: Text? = null,
    val morning: Text? = null,
    val evening: Text? = null,
    val lore: List<SkyLore> = emptyList(),
    val words: List<String> = emptyList(),
)

/**
 * The shooting stars: what one is called ([name]), the wish ("Utrinek! Zaželi si nekaj."), lore, words, and the
 * showers of the year the card tells of on their nights ([showers], by [si.lanisce.lani.game.sky.Meteors.SHOWERS] id).
 */
@Serializable
data class SkyMeteors(
    val name: Text,
    val wish: Text,
    val lore: List<SkyLore> = emptyList(),
    val words: List<String> = emptyList(),
    val showers: List<SkyShower> = emptyList(),
)

/** A meteor shower: its [name], the people's ([folk]: "Lovrenčeve solze"), and the card's line on its nights ([tonight]). */
@Serializable
data class SkyShower(val id: String, val name: Text, val folk: Text? = null, val tonight: Text)
