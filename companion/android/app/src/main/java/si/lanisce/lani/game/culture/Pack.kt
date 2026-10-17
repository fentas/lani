package si.lanisce.lani.game.culture

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.VillagerLines

// The files of a culture pack (lani.culture/v0, companion/cultures/<id>/, described in companion/GAME.md "Culture
// packs"; the bridge's zod schema is companion/bridge/src/cultures.ts). Every [Text] is {"<language>": "…"}: the
// pack's language, then translations into the base languages it serves. Messages name their arguments in braces;
// the comments list what each gets.

/** culture.json: which pack, in which language, for which region. */
@Serializable
data class Manifest(
    val schema: String,
    val id: String,
    /** The pack's language (ISO 639-1): the target of the learners whose town it is. */
    val language: String,
    /** complete | stub: a stub has only its manifest, and isn't used yet. */
    val status: String = "complete",
    val region: Text,
    val name: Text,
    val about: Text? = null,
    /** Who wrote the pack's language and who checked it: "machine-written, not reviewed by a native speaker". */
    val review: String? = null,
    /** What the tutor is told about the place (the bridge's, in English); the app doesn't use it. */
    val tutor: TutorTexts? = null,
)

/** culture.json `tutor`: the village's name, the role-play setting ({learner}), the style of newcomers, the voice's accent. */
@Serializable
data class TutorTexts(val village: String, val setting: String, val style: String, val voice: String)

// --- world.json -------------------------------------------------------------------------------------------------

/**
 * What the culture calls a resource, an age, a building or an event; [partitive]: a resource after "more" ("več lesa");
 * [leader]: who goes gathering a resource with the learner (a villager's name, on the training stage).
 */
@Serializable
data class Named(val name: Text, val partitive: Text? = null, val leader: String? = null)

/** Who welcomes the learner on the training stage before they have met anyone (a villager's name, and their emoji). */
@Serializable
data class Host(val name: String, val emoji: String)

/** A place the pack's texts name, for writers and the tutor: its name in each language, and what it is. */
@Serializable
data class Place(val id: String, val name: Text, val about: Text? = null)

/**
 * What lies at the village's horizon, a place the learner can go (the scenes that open from "spot:horizon", the pack's own
 * landscape): its [emoji] and [name] on the place card ("Gore · The mountains"), [where] in a sentence ("v gorah · in the
 * mountains"), [about] the card's line, and [sea]: the village's backdrop shows the sea along the horizon.
 */
@Serializable
data class Horizon(val emoji: String, val name: Text, val where: Text, val about: Text? = null, val sea: Boolean = false)

/**
 * A spot of the landscape as the culture tells it (world.json `spots`, by the spot's id: "kopa", the charcoal pile in the
 * woods; see [si.lanisce.lani.game.scene.TownSpots]): its [emoji], [name] and [where] in a sentence ("pri kopi · by
 * the charcoal pile") on its place card and bubbles, [about] the card's line, a few sentences of its [story] by level
 * ("A1", "A2", …: the learner reads the highest up to theirs), the word [pack] its words are in ([words]: which of them the
 * card shows, else all), who wrote it ([review]), and [keeper]: someone who is there now and then, with a short talk. A
 * spot without an entry keeps the app's own names.
 */
@Serializable
data class SpotTexts(
    val emoji: String? = null,
    val name: Text,
    val where: Text,
    val about: Text? = null,
    val story: Map<String, Text> = emptyMap(),
    val pack: String? = null,
    val words: List<String> = emptyList(),
    val review: String? = null,
    val keeper: Keeper? = null,
    /**
     * The spot's work over real days ([SpotCycle]: the charcoal pile from the felling of the beech to the charcoal at the
     * smith's): what it looks like, what its keeper says and does each day; none: it is always as the app draws it.
     */
    val cycle: SpotCycle? = null,
)

/**
 * A spot's work in stages over real days (world.json `spots.<id>.cycle`; companion/GAME.md "A spot's cycle"): every year
 * from [season]'s `from` to its `until` one round ([stages], each [CycleStage.days] long) after another, the first [start]
 * days after `from`, a [pause] between rounds, a round only when it ends within the season; between rounds [rest], outside
 * the season [winter]. Which day is what follows from the date alone (the lengths rolled from [seed], else the spot's id,
 * and the year: [si.lanisce.lani.game.scene.SpotCycles]), the same for every village of the culture, never from the
 * learner's visits. A round may break out one night ([breakout]) and burn down ([lost]); a night of some stages may bring
 * something its keeper tells the day after ([nights]); a stage may deliver what the round made ([delivery]).
 */
@Serializable
data class SpotCycle(
    val seed: String? = null,
    val season: CycleSeason,
    val start: List<Int> = listOf(0, 0),
    val pause: List<Int> = listOf(3, 7),
    val stages: List<CycleStage>,
    val rest: CycleStage? = null,
    val winter: CycleStage? = null,
    val lost: CycleStage? = null,
    /**
     * His first evening with the learner while his spot has its cycle: who he is (a talk by level, as his meeting is), the
     * day's talk after it; none: his meeting ([Keeper.levels]).
     */
    val meeting: kotlinx.serialization.json.JsonObject? = null,
    /** His greeting on an evening that isn't his first (one line each, by level; picked by the time of day as his talks are). */
    val greet: List<CycleTalk> = emptyList(),
    /** What he says first when the learner has been away a while (by level, a line or two). */
    val missed: kotlinx.serialization.json.JsonObject? = null,
    val breakout: CycleBreakout? = null,
    val nights: List<CycleNight> = emptyList(),
    val delivery: CycleDelivery? = null,
)

/** When a cycle's rounds go on each year: from day [from] ("04-20") until day [until] ("10-31"), both month-day. */
@Serializable
data class CycleSeason(val from: String, val until: String)

/**
 * A stage of a [SpotCycle] (or its rest, its winter, a round lost): its [id], how long it takes ([days]: the least and the
 * most, rolled each round), how the app draws the spot meanwhile ([art], one of [si.lanisce.lani.game.scene.SpotCycles.ARTS],
 * the [smoke]'s colour where it smokes), the keeper's bubble ([title]) and the spot card's line ([about]) while it lasts,
 * whether he is there more or less often than his own [Keeper.chance] ([chance]) and at other parts of the day ([times]),
 * whether it takes the round's yield to whoever [SpotCycle.delivery] names ([delivers]: its first day), his talks while it
 * lasts ([talks]: variants, a visit after another hears the next), and what he tells of it once it is over ([done], by level:
 * a line or two, said to a learner who wasn't there).
 */
@Serializable
data class CycleStage(
    val id: String,
    val days: List<Int> = listOf(1, 1),
    val art: String,
    val smoke: String? = null,
    val title: Text? = null,
    val about: Text? = null,
    val chance: Float? = null,
    @SerialName("when") val times: List<String>? = null,
    val delivers: Boolean = false,
    val talks: List<CycleTalk> = emptyList(),
    val done: kotlinx.serialization.json.JsonObject? = null,
)

/**
 * A variant of a keeper's talk in a stage (or of his greeting): its lines by level ([levels], as his meeting's), for the
 * parts of the day it fits ([times]; none: any) and the weather it is for ([sky]: "rain", "wind"; none: any): one that
 * fits the sky goes first when it does.
 */
@Serializable
data class CycleTalk(
    @SerialName("when") val times: List<String> = emptyList(),
    val sky: String? = null,
    val levels: kotlinx.serialization.json.JsonObject,
)

/**
 * A night the round breaks out (the fire through its cover): on one day of [stages] of a round, a round in [chance] of them,
 * his talk that evening is [levels] (its last line the learner's turn, against the clock: [seconds] by level), then
 * [saved] when they chose right in time, else [lost]: the round burns down ([SpotCycle.lost] the days left of it, and no
 * yield). A learner who wasn't there hears [missed] the day after (he covered it alone). [covered] and [burnt]: the
 * chronicle's lines.
 */
@Serializable
data class CycleBreakout(
    val stages: List<String>,
    val chance: Float,
    val seconds: Map<String, Int> = emptyMap(),
    val title: Text,
    val levels: kotlinx.serialization.json.JsonObject,
    val saved: kotlinx.serialization.json.JsonObject,
    val lost: kotlinx.serialization.json.JsonObject,
    val missed: kotlinx.serialization.json.JsonObject? = null,
    val covered: Text? = null,
    val burnt: Text? = null,
)

/** Something a night of [stages] may bring ([chance] a night: the wind), which the keeper tells the day after ([told], by level). */
@Serializable
data class CycleNight(val id: String, val stages: List<String>, val chance: Float, val told: kotlinx.serialization.json.JsonObject)

/**
 * Where a round's yield goes (the charcoal to the smith): to villager [to], who speaks of it for [days] after (a line of
 * [says] on his card, when he lives here), the chronicle's [line] on the day; a round lost brings none ([waits]: what he
 * says then).
 */
@Serializable
data class CycleDelivery(
    val to: String,
    val days: Int = 14,
    val line: Text,
    val says: List<Text> = emptyList(),
    val waits: List<Text> = emptyList(),
)

/**
 * A part of a keeper's own story ([Keeper.tales]): told at the end of his talk once the learner has spent [after] evenings
 * with him, one a day, in their order (as a villager's lines open with the friendship): its lines by level ([levels]).
 */
@Serializable
data class KeeperTale(val id: String, val after: Int = 0, val title: Text? = null, val levels: kotlinx.serialization.json.JsonObject)

/**
 * Someone who is at a spot now and then, and not one of the village's people (no friendship, no introduction): the charcoal
 * burner who sits by his kopa some evenings (companion/VILLAGERS.md, "At the charcoal pile"). He is staged like a villager
 * ([id], [name], [emoji], [art]: a people sprite, [voice], [speaker], [role]); he is there in the parts of the day he
 * comes ([times]: "evening", "night"; none: all day) on a day the dice give him ([chance]); his bubble says [title]; his
 * talk is a scene dialog's lines at each level ([levels]: "A1" → {"lines": […]}, his lines' `who` his [id]), read in the
 * learner's pair when it is played, and pays [reward] ("wisdom" → 15) once a day. [levels] is the first talk, the meeting;
 * then he tells his story across visits ([talks]: one a day his talk is done, in their order, and after the last the
 * first again, in its next telling; see [si.lanisce.lani.game.scene.Keepers.at]).
 */
@Serializable
data class Keeper(
    val id: String,
    val name: String,
    val emoji: String,
    val art: String,
    val voice: String = "male",
    val speaker: String? = null,
    val role: Text? = null,
    /** For writers and the tutor (English): who he is. */
    val story: String? = null,
    @SerialName("when") val times: List<String> = emptyList(),
    val chance: Float = 1f,
    val title: Text,
    val reward: Map<String, Int> = emptyMap(),
    val levels: kotlinx.serialization.json.JsonObject,
    val talks: List<KeeperStoryTalk> = emptyList(),
    /** His own story, a part at a time as the learner spends evenings with him ([KeeperTale]). */
    val tales: List<KeeperTale> = emptyList(),
)

/**
 * A talk of the story a [Keeper] tells across visits (after his meeting): its [id] (the same at every level: the learner's
 * place is a count, kept when their level rises), the bubble's [title] while it is the one he has (else his own), its lines
 * at each level ([levels], as his meeting's), and how he tells it on a later round of the story ([again]: the next pile's,
 * varied by small things). Round after round he tells the next of [levels] and [again], in turn.
 */
@Serializable
data class KeeperStoryTalk(
    val id: String,
    val title: Text? = null,
    val levels: kotlinx.serialization.json.JsonObject,
    val again: List<KeeperTelling> = emptyList(),
) {
    /** How he tells it on round [round] (0: the first): its [levels], then each of [again], in turn. */
    fun telling(round: Int): kotlinx.serialization.json.JsonObject {
        val all = listOf(levels) + again.map { it.levels }
        return all[Math.floorMod(round, all.size)]
    }
}

/** Another telling of a [KeeperStoryTalk]: its lines at each level. */
@Serializable
data class KeeperTelling(val levels: kotlinx.serialization.json.JsonObject)

@Serializable
data class WorldFile(
    val host: Host,
    /** food, wood, stone, wisdom */
    val resources: Map<String, Named>,
    /** ogenj … mesto */
    val ages: Map<String, Named>,
    /** tent … market (the building types, lower case) */
    val buildings: Map<String, Named>,
    /** wolves, bear, storm, merchant, festival */
    val events: Map<String, Named>,
    val places: List<Place> = emptyList(),
    val backdrop: Horizon? = null,
    /** The culture's own names and texts for spots of the landscape, by spot id ([SpotTexts]). */
    val spots: Map<String, SpotTexts> = emptyMap(),
)

// --- quests.json ------------------------------------------------------------------------------------------------

/** A request a villager of the cast may make: [giver] is their name as the cast has it ("Babica Micka"). */
@Serializable
data class RequestTemplate(
    val giver: String,
    val emoji: String,
    /** food | wood | stone | wisdom: what it trains and pays in. */
    val skill: String,
    val title: Text,
    val story: Text,
    /** Card categories the story is about (see Quests.fits). */
    val topics: List<String> = emptyList(),
    /**
     * How the giver remembers the learner's help with it, fitting their remember lines ("il giorno che mi hai aiutato
     * in cucina"; Slovene in the accusative, "dan, ko si mi pomagal v kuhinji"); without it the app makes one.
     */
    val memory: Text? = null,
    /**
     * The grammar book's page of the rule the request is about ("articoli-genere", companion/grammar/<language>): its
     * intro leads there, and playing it unlocks the page, as a module that lists a page does.
     */
    val grammar: String? = null,
)

@Serializable
data class QuestLines(
    /** {giver} */
    val asks: Text,
    /** {giver} */
    val expired: Text,
    /** {giver} */
    val thanked: Text,
    /** {giver} */
    @SerialName("try_again") val tryAgain: Text,
    /** {name} (who), {item} (the good as an object) */
    @SerialName("thanks_good") val thanksGood: Text,
)

/**
 * A question about what someone said: [ask] it, the answer first among the [options] (in the pack's language; a picture
 * in front of each where one shows it, "🪷 ob ribniku"), and [explain] it (the preposition and the case it takes);
 * [grammar]: the grammar book's page of its rule ("kje-mestnik-orodnik", companion/grammar), which the question unlocks.
 */
@Serializable
data class PlaceQuestion(val ask: Text, val options: List<String>, val explain: Text, val grammar: String? = null)

/**
 * `tent_move`: the shepherd's request to move the tent out of the village to the pond in the woods (game/TentMove), once
 * people sleep under a proper roof. Who asks ([giver], their name as the cast has it, and [emoji]), the [skill] it trains
 * and pays in, its [title], [story] and [memory] as a request's; [said]: his description of the new place, which the run
 * is about; the run's instruction when it's heard ([listen]) or read ([read]), and [heard] the prompt it's read in
 * ({said}); the [questions] about it; [moved]: the chronicle's line once the tent stands there.
 */
@Serializable
data class TentMoveTexts(
    val giver: String,
    val emoji: String,
    /** food | wood | stone | wisdom */
    val skill: String = "wood",
    val title: Text,
    val story: Text,
    val memory: Text? = null,
    val said: Text,
    val listen: Text,
    val read: Text,
    /** {said}: the description, in the target language */
    val heard: Text,
    val questions: List<PlaceQuestion>,
    val moved: Text,
)

@Serializable
data class QuestsFile(
    val requests: List<RequestTemplate>,
    val lines: QuestLines,
    /** The shepherd's request to move the tent to the pond; a pack without it never asks. */
    @SerialName("tent_move") val tentMove: TentMoveTexts? = null,
)

// --- festivals.json ---------------------------------------------------------------------------------------------

/** When: {"month", "day"}, or {"easter": days from Easter Sunday}, or {"month", "last": weekday}. */
@Serializable
data class DateEntry(val month: Int? = null, val day: Int? = null, val easter: Int? = null, val last: String? = null)

@Serializable
data class FestivalEntry(
    val id: String,
    val emoji: String,
    val date: DateEntry,
    val name: Text,
    /** Villager ids: the first who is in the village leads it. */
    val leaders: List<String>,
    /** What the leader says to start it. */
    val ask: Text,
    /** A line about the day. */
    val about: Text,
    /** The chronicle's line once celebrated. */
    val line: Text,
    /** The good it brings to the chest (a chest.json id), [goods] of it. */
    val good: String? = null,
    val goods: Int = 1,
    /** The word pack of its words (companion/packs, lani.pack/v0). */
    val words: String,
    /**
     * A quiet day, kept and not celebrated (Dan spomina na mrtve): the leader asks Jan to come and remember, and it brings
     * no festive good, no morale and no treat; see [si.lanisce.lani.game.Festival.quiet].
     */
    val quiet: Boolean = false,
)

@Serializable
data class FestivalLines(
    /** The run's instruction. */
    val tag: Text,
    /** {word}: a word of the pack, in the target language */
    val means: Text,
    /** {meaning}: what a word means, in the base language */
    val say: Text,
    /** A try that didn't pass. */
    val tried: Text,
)

@Serializable
data class FestivalsFile(val festivals: List<FestivalEntry>, val lines: FestivalLines)

// --- surprises.json ---------------------------------------------------------------------------------------------

/** A question about a letter, the answer first; [grammar]: the page of its rule, which answering it unlocks (as [PlaceQuestion]'s). */
@Serializable
data class LetterQuestion(val ask: Text, val options: List<String>, val explain: Text, val grammar: String? = null)

/** A letter for villager [to] ("jan": for the learner), [toName] their name after "for" in the target language. */
@Serializable
data class LetterEntry(
    val to: String,
    @SerialName("to_name") val toName: Text? = null,
    val female: Boolean = false,
    /** The letter, in the target language. */
    val text: Text,
    /** The answer first among the options. */
    val questions: List<LetterQuestion>,
)

/**
 * A way to show the pilgrim: what to say ([say], in the base language), the right words first, and a gloss each;
 * [grammar]: the page of the rule its wrong options break ("tu-lei": Vai for Vada), which answering it unlocks.
 */
@Serializable
data class WayEntry(val say: Text, val options: List<String>, val explain: List<Text>, val grammar: String? = null)

/** A riddle in the target language, the answer first among the options, the answer and the riddle in the base language. */
@Serializable
data class RiddleEntry(val riddle: Text, val options: List<String>, val answer: Text, val meaning: Text)

@Serializable
data class PedlarTexts(val emoji: String, val title: Text, val intro: Text, val ask: Text, /** His rare goods, one a visit. */ val goods: List<String>)

@Serializable
data class LetterTexts(
    val emoji: String,
    val title: Text,
    /** {to} (their name after "for"), {name}, {g}: f | m */
    val intro: Text,
    @SerialName("intro_mine") val introMine: Text,
    val ask: Text,
    @SerialName("ask_mine") val askMine: Text,
    val read: Text,
    /** {name} */
    val won: Text,
    @SerialName("won_mine") val wonMine: Text,
    val lost: Text,
    val letters: List<LetterEntry>,
)

@Serializable
data class LambTexts(
    val emoji: String,
    /** The villager whose lamb it is. */
    val shepherd: String,
    val title: Text,
    val intro: Text,
    val ask: Text,
    val where: Text,
    /** {said}: where the lamb is, in the target language */
    val heard: Text,
    val listen: Text,
    val read: Text,
    val won: Text,
    val lost: Text,
    /** Where it is, in the target language and translated. */
    val places: List<Text>,
)

@Serializable
data class PilgrimTexts(
    val emoji: String,
    val title: Text,
    val intro: Text,
    val ask: Text,
    /** {say}: the way to show him, in the base language */
    val prompt: Text,
    val show: Text,
    val won: Text,
    val lost: Text,
    val ways: List<WayEntry>,
)

@Serializable
data class RiddleTexts(
    val emoji: String,
    /** The child who asks when no child lives here yet. */
    val child: String,
    val title: Text,
    /** {name} */
    val intro: Text,
    val ask: Text,
    /** {name} */
    val won: Text,
    /** {name} */
    val lost: Text,
    val riddles: List<RiddleEntry>,
)

@Serializable
data class SurprisesFile(
    val pedlar: PedlarTexts,
    val letter: LetterTexts,
    val lamb: LambTexts,
    val pilgrim: PilgrimTexts,
    val riddle: RiddleTexts,
    /** The strangers at the road (pedlar, pilgrim), staged like villagers (lani.villager/v0's fields). */
    val strangers: Map<String, Villager>,
)

// --- chest.json -------------------------------------------------------------------------------------------------

/**
 * An item's name: [name] as a label ("Sekira", "An axe"), [acc] as the object of a sentence ("ti podari sekiro",
 * "gives you an axe"), [the] definite in a sentence ("izboljšal sekiro", "improved the axe"). A missing form is the one
 * before it. [read]: the reading it holds (readings/<id>.json: Micka's recipe, Janez's book).
 */
@Serializable
data class ItemEntry(val emoji: String, val name: Text, val acc: Text? = null, val the: Text? = null, val read: String? = null)

@Serializable
data class GoodEntry(
    val id: String,
    val emoji: String,
    val name: Text,
    val acc: Text? = null,
    val the: Text? = null,
    /** What the merchant pays: one of Catalog.GOOD_PRICES (small, plain, good, fine, rich, precious). */
    val price: String,
    /** Can be served at a feast. */
    val food: Boolean = true,
    /** Only from a festival or the pedlar; everyone likes it. */
    val rare: Boolean = false,
    /** Not for the children (wine). */
    val adult: Boolean = false,
    /** The reading it holds (readings/<id>.json: the poems' page). */
    val read: String? = null,
)

/** The tool villager [giver] gives at friendship level 2 ([first]) and 4 ([better]); [effect]: one of Catalog.TOOL_EFFECTS. */
@Serializable
data class ToolEntry(
    val giver: String,
    /** What friends call the giver: "Luka". */
    @SerialName("giver_name") val giverName: String,
    val effect: String,
    /** Of iron or brass: the smith can make it better. */
    val forge: Boolean = false,
    val first: ItemEntry,
    val better: ItemEntry,
)

/** The smith: villager [id], [name] to friends, [title] with his trade ("Kovač Tone"). */
@Serializable
data class Smith(val id: String, val name: String, val title: Text)

@Serializable
data class Seller(val emoji: String, val name: Text)

@Serializable
data class ChestLines(
    /** {name} (the giver), {item}, {emoji}, {effect} */
    @SerialName("gift_tool") val giftTool: Text,
    /** {name}, {item}, {emoji} */
    val gift: Text,
    /** {name}, {item} */
    val given: Text,
    /** {good} (its name in the target language), {price} */
    val bought: Text,
    /** {good}, {got}, {res} (emoji) */
    val sold: Text,
    /** {smith}, {item} (definite), {stars}, {effect} */
    val forged: Text,
    /** {served}: the treats' emojis */
    val feast: Text,
    /**
     * {item} (the good's "the" form): how a villager remembers a good the learner gave them, fitting their remember
     * lines ("{item} che mi hai regalato"; Slovene in the accusative, "{item} od tebe"); without it the app says it as
     * Primorska always has.
     */
    @SerialName("gift_memory") val giftMemory: Text? = null,
)

/**
 * What a villager thanks with after a request, besides their one `thanks` good (chest.json `thanks_rotation`, by villager
 * id; companion/GAME.md, "The chest"): [goods], 2 to 4 of their everyday goods, their `thanks` first, one of them a
 * request; [seasonal], festival id → the good they thank with in its season (the week before it to its last grace day),
 * which may be a rare one. An app from before the key reads the pack as it was (the pack is bundled with the app).
 */
@Serializable
data class ThanksRotation(val goods: List<String> = emptyList(), val seasonal: Map<String, String> = emptyMap())

@Serializable
data class ChestFile(
    val goods: List<GoodEntry>,
    val tools: List<ToolEntry>,
    /** Villager id → the good they thank with after a request (the first of their [thanksRotation], and what an older app gives). */
    val thanks: Map<String, String>,
    /** Villager id → the goods they thank with in turn, and in a festival's season (see [ThanksRotation]). */
    @SerialName("thanks_rotation") val thanksRotation: Map<String, ThanksRotation> = emptyMap(),
    @SerialName("thanks_default") val thanksDefault: String,
    /** What people who moved in or were born here give. */
    @SerialName("small_gifts") val smallGifts: List<String>,
    /** Villager id → the goods they like. */
    val likes: Map<String, List<String>>,
    @SerialName("likes_default") val likesDefault: List<String>,
    /** The children of the cast: no wine for them. */
    val children: List<String>,
    val smith: Smith,
    /** pedlar, merchant, market */
    val sellers: Map<String, Seller>,
    val lines: ChestLines,
)

// --- readings/<id>.json -----------------------------------------------------------------------------------------

/**
 * An ingredient of a recipe: its [amount] as written ("500", "½"; none for "ščepec soli", "a pinch of salt"), its [unit]
 * and the [item] as it reads after them in each language ("moke", "di farina", "flour"). A [part] starts a group of
 * them ("Testo · The dough").
 */
@Serializable
data class IngredientEntry(val part: Text? = null, val amount: String? = null, val unit: Text? = null, val item: Text)

/** A line of a reading, in the pack's language with its translations; [means]: what a proverb means, said simply. */
@Serializable
data class ReadingLine(val text: Text, val means: Text? = null)

/**
 * A closing question: [ask] it, the answer first among the [options] (in the pack's language), and [explain] why. Its
 * [type] (si.lanisce.lani.game.Readings.QUESTION_TYPES): the main idea, a detail, a [word] in context (as it stands in
 * the text), a true/false statement ([ask]; [answer] says whether it holds, and it has no options), an inference; none
 * is a plain question, as the first readings have them.
 */
@Serializable
data class ReadingQuestion(
    val ask: Text,
    val options: List<String> = emptyList(),
    val explain: Text,
    val type: String? = null,
    val word: String? = null,
    val answer: Boolean? = null,
)

/**
 * A new word a reading offers for the reviews: the [word] as a dictionary has it (in the pack's language), its [form] as
 * it stands in the text, its part of speech, and what it [means] in the other languages.
 */
@Serializable
data class ReadingWord(val word: String, val form: String? = null, val pos: String? = null, val means: Text)

/**
 * readings/<id>.json: something to read, in the pack's language with a translation of every line: what a chest item holds
 * (its `read`), and the reading corner's. A recipe, a book's page, proverbs, a letter, a story, an article: [kind] and
 * [level] are one of si.lanisce.lani.game.Readings' KINDS and LEVELS; [by]: who reads it aloud (a villager id; a tool's
 * reading: its giver). A recipe has its [servings], [ingredients] and numbered [steps] (imperatives); the others have
 * [lines]. The [questions] close it (1 to 5), and it may offer new [words] for the reviews; [topics] say what it is about.
 * The bridge serves it with its [source] (curated: the culture pack's; tutor: written for the learner, [machineWritten]).
 */
@Serializable
data class ReadingFile(
    val id: String,
    val kind: String,
    val level: String,
    val title: Text,
    val by: String? = null,
    /** A line before the text: who wrote it down, what it is. */
    val intro: Text? = null,
    val servings: Text? = null,
    val ingredients: List<IngredientEntry> = emptyList(),
    val steps: List<Text> = emptyList(),
    val lines: List<ReadingLine> = emptyList(),
    val questions: List<ReadingQuestion>,
    val words: List<ReadingWord> = emptyList(),
    val topics: List<String> = emptyList(),
    @SerialName("machine_written") val machineWritten: Boolean? = null,
    val review: String? = null,
    /** curated | tutor: served by the bridge (a bundled file is the culture pack's). */
    val source: String = "curated",
    @SerialName("published_at") val publishedAt: Double? = null,
    /** The culture pack a curated reading is of, as the bridge serves it. */
    val culture: String? = null,
)

// --- projects.json ----------------------------------------------------------------------------------------------

@Serializable
data class StepTexts(val task: Text, val line: Text)

/**
 * The words of a village project (its steps, costs and landmark are Catalog's): who leads it and who helps (villager
 * ids), its name ([inText]: inside a sentence, "the maypole"), what it is, each step's task and chronicle line, the
 * line when it's done, and how the villagers remember it ("naš mlaj", fitting "Še vedno mislim na {memory}"); [where]:
 * where its landmark is, when a scene opens there ("v vinogradu · in the vineyard").
 */
@Serializable
data class ProjectEntry(
    val id: String,
    val leader: String,
    val helpers: List<String> = emptyList(),
    val name: Text,
    @SerialName("in_text") val inText: Text? = null,
    val about: Text,
    val steps: List<StepTexts>,
    val done: Text,
    val memory: Text,
    val where: Text? = null,
    /** When its landmark stands once finished (the maypole in May); none: all year. */
    val stands: StandsEntry? = null,
)

/**
 * When a finished project's landmark stands (game/ProjectSeasons.kt): the village raises it again every year on [raised]
 * ("04-30", month-day) and takes it down after [until] ("05-31", the same year, not before [raised]); raised, it stands at
 * least [minDays] days (one finished outside its season, for the village's feast). [up]: the chronicle's line when the
 * village raises it again; [down]: when it takes it down.
 */
@Serializable
data class StandsEntry(
    val raised: String,
    val until: String,
    @SerialName("min_days") val minDays: Int = 14,
    val up: Text,
    val down: Text,
) {
    companion object {
        /** "04-30" as a day of the year; null when it isn't one. */
        fun monthDay(s: String): java.time.MonthDay? =
            if (Regex("\\d\\d-\\d\\d").matches(s)) runCatching { java.time.MonthDay.parse("--$s") }.getOrNull() else null
    }
}

@Serializable
data class ProjectLines(/** {leader} */ @SerialName("try_again") val tryAgain: Text)

@Serializable
data class ProjectsFile(val projects: List<ProjectEntry>, val lines: ProjectLines)

// --- project-steps/<project>.json ---------------------------------------------------------------------------------

/**
 * A village project's steps as short scenes (project-steps/<project>.json, lani.project-steps/v0; companion/SCENES.md
 * "Project steps"): each step, in order, as it is played (null: as the practice it always was); [pack]: the word pack of
 * the project's words, which its steps' turns test; [review]: who wrote it and who checked it. Read leniently (an older
 * app never sees the file; a newer key is read past), checked by the bridge (project-steps.ts).
 */
@Serializable
data class ProjectStepsFile(
    val schema: String,
    val project: String,
    val language: String,
    val review: String? = null,
    val pack: String? = null,
    val steps: List<StepPlay?> = emptyList(),
)

/**
 * One step as a short scene: the [scene] it is played in (a scene id; played over the village when the village hasn't it
 * open, or it is missing), the [words] of the project's pack its turns test, its dialog at each level ([levels]: "A1" →
 * {"lines": […]}, a scene dialog's lines said by the project's leader and helpers, read in the learner's pair when it is
 * played), and whether the sky or the effects it brings stay for the day ([skyStays], [fxStays], as a scene dialog's).
 */
@Serializable
data class StepPlay(
    val scene: String? = null,
    val words: List<String> = emptyList(),
    val levels: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()),
    @SerialName("sky_stays") val skyStays: Boolean? = null,
    @SerialName("fx_stays") val fxStays: Boolean? = null,
)

// --- events.json ------------------------------------------------------------------------------------------------

@Serializable
data class EventTexts(
    /** Who stands with the learner against it, on the training stage (a villager's name); else today's companion. */
    val leader: String? = null,
    val title: Text,
    /** Wolves at the palisade, once there is one. */
    @SerialName("title_walled") val titleWalled: Text? = null,
    val intro: Text,
    /** The merchant's: {paid} {give} → {got} {want} (amounts and emojis) */
    val won: Text,
    val lost: Text,
    /** The merchant's gift, when a trade can't be made. */
    val gift: Text? = null,
    /** A feast day nobody came to. */
    val quiet: Text? = null,
)

@Serializable
data class EventsFile(
    val wolves: EventTexts,
    val bear: EventTexts,
    val storm: EventTexts,
    val merchant: EventTexts,
    val festival: EventTexts,
    @SerialName("too_late") val tooLate: Text,
)

// --- people.json ------------------------------------------------------------------------------------------------

@Serializable
data class Gendered(val female: Text, val male: Text)

/** A trade someone who moves in may have; [needs] a building (lower case) for it. */
@Serializable
data class Trade(val needs: String? = null, val female: Text, val male: Text)

@Serializable
data class FirstNames(val female: List<String>, val male: List<String>)

@Serializable
data class Stages(val baby: Gendered, val child: Gendered, val youth: Gendered)

@Serializable
data class PeopleLines(val adult: VillagerLines, val child: VillagerLines)

@Serializable
data class PeopleNews(
    /** {name}, {g} */
    @SerialName("moved_in_cast") val movedInCast: Text,
    /** {first}, {family}, {family_at} ("pri Furlanovih"), {g} */
    val born: Text,
    /** {name}, {role}, {g} */
    @SerialName("moved_in") val movedIn: Text,
    /** {name}, {g} */
    val left: Text,
)

@Serializable
data class PeopleFile(
    @SerialName("first_names") val firstNames: FirstNames,
    /** Family names of the region. */
    val surnames: List<String>,
    val trades: List<Trade>,
    val stages: Stages,
    /** A baby's role. */
    val newborn: Text,
    /** For the tutor (English): {family}, {since} */
    val story: Text,
    /** The plain lines of people who moved in or were born here. */
    val lines: PeopleLines,
    val news: PeopleNews,
)

// --- chronicle.json ---------------------------------------------------------------------------------------------

@Serializable
data class ChronicleFile(
    val founded: Text,
    /** {building} */
    val built: Text,
    /** {building}, {level} */
    val upgraded: Text,
    /** {building} */
    val repaired: Text,
    /** {age} */
    @SerialName("new_age") val newAge: Text,
    val felled: Text,
    @SerialName("felled_more") val felledMore: Text,
    val quarried: Text,
    @SerialName("quarried_more") val quarriedMore: Text,
    val picked: Text,
    @SerialName("picked_more") val pickedMore: Text,
    /** {days} */
    val waited: Text,
    /** {days} */
    val hungry: Text,
    /** {days} */
    val cold: Text,
    /** {n} */
    val joined: Text,
    /** {n} */
    val left: Text,
)
