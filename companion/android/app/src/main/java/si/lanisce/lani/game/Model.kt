package si.lanisce.lani.game

import kotlinx.serialization.Serializable
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.Text

// Shared contract for "Moja vas", the village game.
// Engine (rules), renderer (pixel art) and UI all build on these types.
// Change rules: add fields only with defaults, never rename or remove (the state is persisted on the node).

// The names of resources, ages, buildings and events are the culture's (game/culture, world.json): [Res.names] in
// every language of the pack; `sl` is the name in the learner's target language and `en` in their base language (the
// fields kept the names they had when both were fixed: Slovene and English for Jan).

/** Resources. Each one is earned by practising one skill. */
@Serializable
enum class Res(val emoji: String, val skill: String) {
    FOOD("🌾", "vocabulary"),
    WOOD("🪵", "listening"),
    STONE("🪨", "grammar"),
    WISDOM("📜", "conversation & writing");

    /** "Hrana", "Food", … : what the culture calls it, in each language. */
    val names: Text get() = Cultures.current.name(this)
    /** The name in the target language: "Hrana". */
    val sl: String get() = names.target
    /** The name in the base language: "Food". */
    val en: String get() = names.base
}

/** Settlement stages, in order. */
@Serializable
enum class Age(val emoji: String) {
    OGENJ("🔥"),
    TABOR("⛺"),
    ZASELEK("🛖"),
    VAS("🏘️"),
    TRG("🏛️"),
    MESTO("🏰");

    /** "Zaselek", "Hamlet", … */
    val names: Text get() = Cultures.current.name(this)
    val sl: String get() = names.target
    val en: String get() = names.base
}

/** Building plots available per age (index = Age.ordinal). The renderer places plot i on the map. */
val PLOTS_PER_AGE = listOf(2, 5, 9, 14, 20, 28)

/** The plot of a building that stands on none: the palisade rings the clearing (see [onPlot]); the tent at the pond ([TentMove]). */
const val NO_PLOT = -1

/** Every building the village can have. Costs and effects live in the engine; looks in the renderer; names in the culture. */
@Serializable
enum class BuildingType(val emoji: String) {
    TENT("⛺"),
    FIELD("🌾"),
    WELL("🪣"),
    HUT("🛖"),
    KOZOLEC("🪜"),
    BEEHIVE("🐝"),
    PALISADE("🪵"),
    WATCHTOWER("🗼"),
    SMITHY("⚒️"),
    LIPA("🌳"),
    HOUSE("🏠"),
    CHURCH("⛪"),
    SCHOOL("🏫"),
    MARKET("🏪");

    /** "Kozolec", "Hayrack", … */
    val names: Text get() = Cultures.current.name(this)
    val sl: String get() = names.target
    val en: String get() = names.base

    /**
     * Whether a building of this type takes a plot. The palisade doesn't: it stands round the clearing, just inside the
     * forest's edge, and its [Building.plot] is [NO_PLOT] (an older save's palisade gives its plot back on the next tick).
     */
    val onPlot: Boolean get() = this != PALISADE
}

@Serializable
data class Building(
    val id: String,
    val type: BuildingType,
    /**
     * Plot index, 0 until PLOTS_PER_AGE[age]; [NO_PLOT] for a type that takes none (see [BuildingType.onPlot]), and for the
     * tent that moved to the pond in the woods (see [TentMove]).
     */
    val plot: Int,
    val level: Int = 1,
    /** Damaged by an event; gives no effect until repaired. */
    val damaged: Boolean = false,
    /** Epoch millis. */
    val builtAt: Long = 0,
)

/** Village events. Each is played as a short challenge of the named skill. */
@Serializable
enum class EventKind(val emoji: String, val skill: Res) {
    WOLVES("🐺", Res.STONE),
    BEAR("🐻", Res.STONE),
    STORM("⛈️", Res.WOOD),
    MERCHANT("🧳", Res.WISDOM),
    FESTIVAL("🎉", Res.FOOD);

    /** "Volkovi", "Wolves", … */
    val names: Text get() = Cultures.current.name(this)
    val sl: String get() = names.target
    val en: String get() = names.base
}

@Serializable
data class GameEvent(
    val id: String,
    val kind: EventKind,
    /** 1 (easy) to 5 (hard); scales questions needed and the damage on failure. */
    val strength: Int,
    /** Epoch millis. An unanswered event resolves as failed at [deadline] (on the next tick). */
    val startedAt: Long,
    val deadline: Long,
    /** Friends' towns that sent help against it (see [TownFriendship.aidArrived]): each lessens its damage. */
    val aid: Int = 0,
    /** Who sent it (the friends' learners). */
    val helpers: List<String> = emptyList(),
)

/** Where a quest comes from: generated in the app, or written by the tutor (a module with a quest block). */
@Serializable
enum class QuestSource { LOCAL, TUTOR }

@Serializable
data class Quest(
    val id: String,
    val giver: String,        // "Babica Micka"
    val emoji: String,        // "👵"
    val title: String,        // "Micka's kitchen"
    val story: String,        // one or two sentences, Slovene flavour, English meaning
    val skill: Res,           // which skill it trains (and pays mainly in)
    val reward: Map<Res, Int>,
    val source: QuestSource = QuestSource.LOCAL,
    /** TUTOR quests: the module to play. */
    val moduleId: String? = null,
    /** LOCAL quests: spaced-repetition card ids or a category to draw exercises from. */
    val cardIds: List<String> = emptyList(),
    val category: String? = null,
    val expiresAt: Long = 0,
    val done: Boolean = false,
    /** LOCAL quests: the grammar book's page of the rule its request is about (quests.json `grammar`), met when played. */
    val grammar: String? = null,
    /** How often it was played (a pass included): the villager's card, the Scroll and the intro say it ([QuestQueue]). */
    val tries: Int = 0,
    /** The best try so far: [best] right of [bestOf] (0 of 0 before the first). */
    val best: Int = 0,
    val bestOf: Int = 0,
    /** The day it was asked (ISO); null for one asked before the app kept it (see [QuestQueue.stale]). */
    val since: String? = null,
    /**
     * TUTOR quests: the module of the task this one is a smaller drill for, at the same giver (the module's quest `helps`, or
     * found by a grammar page that lists both: [QuestQueue.link]). That task waits behind it until it is passed.
     */
    val helps: String? = null,
    /** The grammar pages its wrong answers named, most missed first: what "🎯 Vadi, kar ti ne gre" practises. */
    val missed: List<String> = emptyList(),
)

/**
 * Marks practice leaves on the land, one ISO date per mark, newest last (at most [MAX] each). The renderer
 * turns the n-th felled tree into a stump at the n-th choppable tree near the woodpile, and lets it grow
 * back after some days; likewise stone taken from the rocks and berries picked at the forest edge.
 */
@Serializable
data class WorldMarks(
    val felled: List<String> = emptyList(),
    val quarried: List<String> = emptyList(),
    val picked: List<String> = emptyList(),
) {
    companion object {
        const val MAX = 60
    }
}

/** One line in the village chronicle ("Kronika"). */
@Serializable
data class LogEntry(val at: Long, val emoji: String, val text: String)

/**
 * What this village and a linked town did together (see [Guests]): the town's [name] as it showed itself last; on this
 * learner's visits there, the people [met] (villager ids), the requests [helped], the [gifts] left and [trades] made;
 * and its learner's visits here ([guests]: help, gifts and trades they brought); the [questions] answered between the
 * two, both ways (this learner's answered there, theirs answered here: game/TownQuestionTies). [last]: the ISO date of
 * the latest. The friendship between towns (plan 2, §3.3; the bridges agree on its level) brought: help sent against the
 * town's trouble ([aid]), the feasts shared ([feasts]), people who moved between the two ([moves], both ways).
 */
@Serializable
data class TownTies(
    val name: String = "",
    val met: List<String> = emptyList(),
    val helped: Int = 0,
    val gifts: Int = 0,
    val trades: Int = 0,
    val guests: Int = 0,
    val last: String = "",
    val aid: Int = 0,
    val feasts: Int = 0,
    val moves: Int = 0,
    val questions: Int = 0,
)

@Serializable
data class GameStats(
    val eventsWon: Int = 0,
    val eventsLost: Int = 0,
    val questsDone: Int = 0,
    val totalEarned: Map<Res, Int> = emptyMap(),
    /** 🤝 earned by helping people, ever. */
    val helpEarned: Int = 0,
    /** Mobas called (the neighbours came to help build). */
    val mobas: Int = 0,
)

/** A villager's gift of their trade, kept for good: it changes a rule (see [Chest], GAME.md "The chest"). */
@Serializable
data class Tool(
    /** 1: the gift at friendship level 2; 2: the better one at level 4. */
    val tier: Int = 1,
    /** Times the smith forged it, 0 to [Catalog.MAX_FORGED]. */
    val forged: Int = 0,
)

/**
 * "Dnevno presenečenje · The day's surprise" at the road (see [Surprises]): [kind] is one of [Surprises.KINDS]
 * (a string, so a newer kind never breaks an older app), [variant] picks its content, [who] is the villager it's
 * about (Luka's lamb, the child with a riddle, the letter's addressee), [done] once it was played.
 */
@Serializable
data class Surprise(
    /** ISO date. */
    val on: String,
    val kind: String,
    val variant: Int = 0,
    val who: String? = null,
    val done: Boolean = false,
)

/** "Skrinja · The chest": the tools villagers gave, the goods they thanked with, and who gave what. */
@Serializable
data class Inventory(
    /** Tools by the id of the villager who gave them. */
    val tools: Map<String, Tool> = emptyMap(),
    /** Goods by id → how many. */
    val goods: Map<String, Int> = emptyMap(),
    /** Villager id → the friendship level of the last gift they gave (2 or 4). */
    val gifted: Map<String, Int> = emptyMap(),
    /** Villager id → ISO date Jan last gave them a good (one a day each). */
    val given: Map<String, String> = emptyMap(),
)

@Serializable
data class GameState(
    val schema: Int = 1,
    val seed: Long = 0,
    val age: Age = Age.OGENJ,
    val resources: Map<Res, Int> = emptyMap(),
    val buildings: List<Building> = emptyList(),
    val villagers: Int = 1,
    /** Fire strength 0..100, fed by wood. Never below [MIN_FIRE], so the village can always recover. */
    val fire: Int = 60,
    /** Morale 0..100: food, the linden tree, festivals. */
    val morale: Int = 60,
    /** ISO date (yyyy-MM-dd) up to which daily upkeep was applied. */
    val lastTick: String = "",
    val event: GameEvent? = null,
    val quests: List<Quest> = emptyList(),
    val log: List<LogEntry> = emptyList(),
    val stats: GameStats = GameStats(),
    /** ISO date of the first tick; events stay away for the first days. */
    val foundedOn: String = "",
    /** Consecutive days with very low morale; at three, a villager leaves. */
    val lowMoraleDays: Int = 0,
    /** Answers credited since the day began (the last new-day tick); the first ones of a day pay in full. */
    val answersToday: Int = 0,
    /** Happenings finished, by "scene/happening" key → ISO date: each pays once a day (see game/scene/Happenings). */
    val happeningsDone: Map<String, String> = emptyMap(),
    /** Friendship with each villager, by villager id (see game/villagers/Bonds). */
    val bonds: Map<String, si.lanisce.lani.game.villagers.Bond> = emptyMap(),
    /** What practice changed in the landscape: trees felled, stone quarried, berries picked. */
    val world: WorldMarks = WorldMarks(),
    /** Who lives here, one per villager counted in [villagers] (see game/villagers/Residents). */
    val residents: List<si.lanisce.lani.game.villagers.Resident> = emptyList(),
    /** Someone who doesn't live here yet and comes by today (see Residents.visit). */
    val visitor: si.lanisce.lani.game.villagers.Visit? = null,
    /** 🤝 Pomoč: earned by helping people, spent on a moba (see [Help]). */
    val help: Int = 0,
    /** Tools and goods from the villagers (see [Chest]). */
    val chest: Inventory = Inventory(),
    /** ISO date of the last village feast (see [Chest.feast]); one a week. */
    val lastFeast: String = "",
    /**
     * "Skupni projekti · Village projects" (see [Projects], [Catalog.projects]): project id → steps done. A project
     * is finished when this reaches its number of steps; its landmark appears on the map as the steps are done.
     */
    val projects: Map<String, Int> = emptyMap(),
    /** ISO date of the last project step: the village does one step a day, across all projects. */
    val projectDay: String = "",
    /** Calendar festivals celebrated (see [Calendar]): festival id → ISO date of the festival day it was for. */
    val festivals: Map<String, String> = emptyMap(),
    /** Today's surprise at the road (see [Surprises]); rolled at the day's first tick. */
    val surprise: Surprise? = null,
    /** Goods bought today, by "seller/good" (see [Chest.buy]); counts from [boughtOn] only. */
    val bought: Map<String, Int> = emptyMap(),
    /** ISO date of [bought]. */
    val boughtOn: String = "",
    /** The sky a scene's dialog left today (rain after Luka's hay): over its scene and the village until midnight (see [si.lanisce.lani.game.scene.DaySky]). */
    val sky: si.lanisce.lani.game.scene.DaySky? = null,
    /** What scenes' dialogs left lit or standing today (the lantern in the tent), by scene, until midnight (see [si.lanisce.lani.game.scene.DayFx]). */
    val sceneFx: si.lanisce.lani.game.scene.DayFx? = null,
    /** Where scenes' dialogs left people today (Luka by the tent's lantern while it rains), by scene, until midnight (see [si.lanisce.lani.game.scene.DayAct]). */
    val sceneAct: si.lanisce.lani.game.scene.DayAct? = null,
    /** The storyteller's evening stories the learner has heard, by story id: told to the end, a chapter under way (see [si.lanisce.lani.game.scene.Stories]). */
    val stories: Map<String, si.lanisce.lani.game.scene.StoryHeard> = emptyMap(),
    /**
     * The happenings' dialogs the learner has heard to their end, by "scene/happening": each variant with its date, and the
     * one heard last (see [si.lanisce.lani.game.scene.DialogVariants]: a variant not heard yet plays first). The bridge
     * reads it too: when all of a happening's variants are heard it asks the tutor for a fresh one.
     */
    val dialogsHeard: Map<String, si.lanisce.lani.game.scene.DialogsHeard> = emptyMap(),
    /**
     * How far the people at the landscape's spots got with the story they tell across visits (the charcoal burner's
     * pile), by their key ("spot:kopa/oglar"): how many talks they told the learner, and when the last (see
     * [si.lanisce.lani.game.scene.Keepers.at]).
     */
    val keepers: Map<String, si.lanisce.lani.game.scene.KeeperTold> = emptyMap(),
    /** The one-time fixes of an older save that are done, by id (the kozolec to its field: [Placement.HAYRACKS_BY_FIELDS]). */
    val migrated: Set<String> = emptySet(),
    /**
     * What linked towns brought (the bridge's guest book) and what this village's visits did, applied once each: their
     * keys, the last [Guests.KEEP] (see [Guests]). The bridge reads it too: a trade not applied yet still holds its goods.
     */
    val guestbook: List<String> = emptyList(),
    /** What this village and each linked town did together, by the town's id (see [Guests]; plan 2, §3.3). */
    val towns: Map<String, TownTies> = emptyMap(),
    /** The chest's readings whose questions were answered, by "culture/reading" (see [Readings]): each pays once. */
    val read: Set<String> = emptySet(),
    /**
     * The grammar book's pages met, by "<language>/<page id>" (see [GrammarBook]): when each unlocked, the answers on its
     * rule. The bridge reads it too (the tutor's list_grammar).
     */
    val grammar: Map<String, RuleRecord> = emptyMap(),
    /**
     * The rules not introduced yet that the learner met in the dialogs, by "<language>/<page id>" (see [Introduction]): a
     * turn said a line of their form without asking for it. Enough of them, and the page opens; the bridge reads it too (the
     * tutor's list_grammar and the daily rhythm: rules ripe to introduce). An older bridge keeps it as it is.
     */
    val meetings: Map<String, RuleMeetings> = emptyMap(),
    /**
     * The land the village stands on: its landscape and the seed of its layout (see [Land]), chosen at its founding. Null in
     * a save from before generated land: the classic valley, which the next tick writes ([GameEngine.tick]).
     */
    val land: Land? = null,
    /**
     * The treasure hunt of the learner's level (see [Treasure]): the map the storyteller gave or the learner asked for,
     * the stations tried, the treasure found. Null before the first map.
     */
    val treasure: TreasureHunt? = null,
    /** The treasures found, by the level each brought ("A2" → the ISO day): their keepsakes are the village's for good. */
    val treasures: Map<String, String> = emptyMap(),
) {
    fun res(r: Res): Int = resources[r] ?: 0

    /** How many plots the buildings take (the palisade takes none, see [BuildingType.onPlot]; nor the tent at the pond, see [TentMove]). */
    val plotsTaken: Int get() = buildings.count { it.type.onPlot && it.plot != NO_PLOT }

    companion object {
        const val MIN_FIRE = 10
    }
}
