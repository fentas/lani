package si.lanisce.lani.game.scene

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import si.lanisce.lani.data.json
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.Keeper
import si.lanisce.lani.game.culture.KeeperStoryTalk
import si.lanisce.lani.game.culture.SpotTexts
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.game.culture.WorldFile
import si.lanisce.lani.game.villagers.Arrivals
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Someone at a spot of the landscape now ([Keeper], the culture's: the charcoal burner by his kopa), at [spot]. [id]
 * ("kopa/oglar") is his bubble's ("keeper:kopa/oglar"); [key] ("spot:kopa/oglar") what his talk is done under today
 * ([GameState.happeningsDone], as a happening's key) and what he has told under ([GameState.keepers]). [at]: where the
 * learner is in the story he tells across visits ([Keepers.at]: 0 his meeting, then the talks of his story in turn).
 */
data class ActiveKeeper(val spot: String, val keeper: Keeper, val at: Int = 0) {
    val id: String get() = "$spot/${keeper.id}"
    val key: String get() = "spot:$id"
    val place: TownPlace get() = TownPlace.Spot(spot)

    /** The talk of his story he has for the learner now; null: his meeting ([Keeper.levels]). */
    val story: KeeperStoryTalk? get() = Keepers.storyTalk(keeper, at)

    /** His bubble's title now: the talk's he has, else his own. */
    val title: Text get() = story?.title ?: keeper.title
}

/**
 * What someone at a spot has told the learner of the story he tells across visits ([GameState.keepers], by his key): how
 * many talks ([told], his meeting the first), and the ISO date of the last ([on]).
 */
@Serializable
data class KeeperTold(val told: Int = 0, val on: String = "")

/**
 * The spots of the landscape as the culture tells them ([SpotTexts]): the story on a spot's card, and the people who are
 * there now and then without living in the village (companion/VILLAGERS.md, "At the charcoal pile"). A keeper comes in his
 * parts of the day on the days the dice give him, as a happening is on; his bubble shows until his talk is done today, and
 * he stays there, sitting, until his time is over. He is no resident: no friendship, no introduction, no card of his own;
 * his talk is played over the village as a scene's dialog is, and pays its reward once a day.
 *
 * His talks tell a story across visits (companion/VILLAGERS.md, "His story, visit by visit"): the first time his meeting
 * ([Keeper.levels]), then each day his talk is done the next talk of his story ([Keeper.talks]: the charcoal burner's pile
 * from its stacking to his charcoal at the smith's), and after the last the first again in its next telling (a new pile,
 * told with small things changed), never the meeting again. Pure.
 */
object Keepers {
    /** The bubble id's prefix: "keeper:kopa/oglar". */
    const val MARKER = "keeper:"

    /**
     * Who is at their spot at [now]: in their part of the day (dawn: the morning's first hours, as a happening's), on a day
     * the dice give them, each where the learner is in their story ([at]). Whether their talk is done today doesn't
     * matter: they stay ([waiting] are the bubbles).
     */
    fun here(state: GameState, now: LocalDateTime, world: WorldFile = Cultures.current.world): List<ActiveKeeper> {
        val today = now.toLocalDate()
        val time = TimeOfDay.of(now.hour).name.lowercase()
        val dawn = TimeOfDay.isDawn(now.hour, now.monthValue)
        return world.spots.mapNotNull { (spot, s) -> s.keeper?.let { ActiveKeeper(spot, it) } }.filter { a ->
            val times = a.keeper.times
            (times.isEmpty() || time in times || dawn && "dawn" in times) && Happenings.roll(state.seed, today, a.key) < a.keeper.chance
        }.map { it.copy(at = at(state, it.key, today)) }
    }

    /**
     * Where the learner is [today] in the story of the keeper under [key], as [state] has what he told them
     * ([GameState.keepers]): the number of talks he told, so the next; when the last was told today, that one again (a
     * talk a day, as it pays: tapped again, he says it again). 0 is his meeting.
     */
    fun at(state: GameState, key: String, today: LocalDate): Int {
        val t = state.keepers[key] ?: return 0
        return if (t.on == today.toString()) (t.told - 1).coerceAtLeast(0) else t.told.coerceAtLeast(0)
    }

    /**
     * The talk of [k]'s story at [at] (1: the first): after the last, the first again; null at 0 (his meeting), and for a
     * keeper with no story (his meeting each time).
     */
    fun storyTalk(k: Keeper, at: Int): KeeperStoryTalk? = if (at <= 0 || k.talks.isEmpty()) null else k.talks[(at - 1) % k.talks.size]

    /** The round of [k]'s story [at] is in: 0 the first (the first pile), 1 the next, … */
    fun round(k: Keeper, at: Int): Int = if (at <= 0 || k.talks.isEmpty()) 0 else (at - 1) / k.talks.size

    /**
     * [state] with [k]'s talk told to its end [today]: the next day he is there, the next talk of his story. A talk told
     * again (the same day, or one he had told) changes nothing, and neither does a keeper with no story to tell (his one
     * talk is all there is: when a story comes, the learner starts it from his meeting).
     */
    fun told(state: GameState, k: ActiveKeeper, today: LocalDate): GameState {
        if (k.keeper.talks.isEmpty()) return state
        val was = state.keepers[k.key] ?: KeeperTold()
        if (k.at + 1 <= was.told) return state
        return state.copy(keepers = state.keepers + (k.key to KeeperTold(k.at + 1, today.toString())))
    }

    /** Whether [k]'s talk was done today. */
    fun done(state: GameState, k: ActiveKeeper, today: LocalDate): Boolean = Happenings.done(state, k.key, today)

    /** Of [here], those whose talk isn't done today, each a bubble at their spot, titled with the talk they have. */
    fun markers(state: GameState, here: List<ActiveKeeper>, today: LocalDate): List<TownMarker> =
        here.filter { !done(state, it, today) }.map { TownMarker(MARKER + it.id, TownMarker.Kind.HAPPENING, it.keeper.emoji, it.title.bi(), it.place) }

    /** The keeper a bubble ("keeper:kopa/oglar") or a tap on him (his id, "oglar") is about, among [here]. */
    fun of(here: List<ActiveKeeper>, id: String): ActiveKeeper? =
        here.firstOrNull { MARKER + it.id == id || it.keeper.id == id }

    /** [k] as the map draws him, beside the village's people: at his spot, his sprite, his name (not a resident). */
    fun villager(k: ActiveKeeper): Villager = Villager(
        id = k.keeper.id, name = k.keeper.name, emoji = k.keeper.emoji, art = k.keeper.art, voice = k.keeper.voice,
        speaker = k.keeper.speaker, role = k.keeper.role?.bi().orEmpty(), home = listOf("spot:${k.spot}"), story = k.keeper.story.orEmpty(),
    )

    /** [k] as his talk's person (no villager: the friendship doesn't count). */
    fun person(k: ActiveKeeper): ScenePerson = ScenePerson(id = k.keeper.id, name = k.keeper.name, emoji = k.keeper.emoji, art = k.keeper.art, slot = "")

    /** [k]'s talk as a happening: its title (the talk's he has) in the pair of the moment, its reward, his emoji. */
    fun happening(k: ActiveKeeper): Happening =
        Happening(id = k.keeper.id, title = k.title.bi(), who = k.keeper.id, reward = k.keeper.reward, marker = k.keeper.emoji)

    /**
     * [k]'s talk for a learner at [level] in the village's [language] (the highest level he has up to theirs, else his
     * easiest, as an introduction's), read in [pair] as a scene's dialog is ([inPair]); null when he has none. The talk is
     * the one he has now ([ActiveKeeper.at]): his meeting, else the talk of his story in this round's telling
     * ("keeper:kopa/oglar/zlaganje").
     */
    fun talk(k: ActiveKeeper, level: String, language: String, pair: LangPair = L10n.pair): Dialog? {
        val story = k.story
        val levels = story?.telling(round(k.keeper, k.at)) ?: k.keeper.levels
        val lv = Arrivals.levelFor(levels.keys, level) ?: return null
        val lines = (levels[lv] as? JsonObject)?.get("lines") as? JsonArray ?: return null
        val id = MARKER + k.id + story?.let { "/${it.id}" }.orEmpty()
        val scene = JsonObject(
            mapOf(
                "language" to JsonPrimitive(language),
                "dialogs" to JsonArray(listOf(JsonObject(mapOf("id" to JsonPrimitive(id), "lines" to lines)))),
            ),
        )
        val d = (inPair(scene, pair)["dialogs"] as? JsonArray)?.firstOrNull() ?: return null
        return runCatching { json.decodeFromJsonElement(Dialog.serializer(), d) }.getOrNull()?.takeIf { it.lines.isNotEmpty() }
    }

    /** A spot's story as its card tells it: what is said, in the village's language, and what it means in the learner's base. */
    data class Lore(val said: String, val meant: String, val level: String)

    /**
     * [s]'s story for a learner at [level] (the highest level it has up to theirs, else its easiest), in the village's
     * [language] with its meaning in [pair]'s base (none when that is the village's language); null when it has none.
     */
    fun lore(s: SpotTexts, level: String, language: Lang, pair: LangPair = L10n.pair): Lore? {
        val lv = Arrivals.levelFor(s.story.keys, level) ?: return null
        val t = s.story[lv] ?: return null
        return Lore(t.of(language), if (pair.base == language) "" else t.of(pair.base), lv)
    }
}
