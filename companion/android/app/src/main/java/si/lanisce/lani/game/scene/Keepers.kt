package si.lanisce.lani.game.scene

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import si.lanisce.lani.data.json
import si.lanisce.lani.game.GameState
import kotlinx.serialization.json.JsonElement
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.Keeper
import si.lanisce.lani.game.culture.KeeperStoryTalk
import si.lanisce.lani.game.culture.KeeperTale
import si.lanisce.lani.game.culture.SpotCycle
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
data class ActiveKeeper(
    val spot: String,
    val keeper: Keeper,
    val at: Int = 0,
    /**
     * His spot's work over the days, when it has one ([SpotCycle]: the charcoal pile's rounds), and where it is today
     * ([day]); with it his talk is the day's ([Keepers.cycleLines]), not his story's.
     */
    val cycle: SpotCycle? = null,
    val day: CycleDay? = null,
    /** What he told the learner so far ([GameState.keepers]): the day of the last talk, his own story's parts. */
    val told: KeeperTold = KeeperTold(),
    /** The part of the day it is, whether it is dawn, and the weather over the village ("rain", "wind": [SpotCycles.skyOf]). */
    val time: TimeOfDay = TimeOfDay.EVENING,
    val dawn: Boolean = false,
    val sky: Set<String> = emptySet(),
    /** What the village keeps of his spot's cycle ([CycleMarks]). */
    val marks: CycleMarks = CycleMarks(),
    /** The real day ([day] may be QA's). */
    val today: LocalDate? = null,
) {
    val id: String get() = "$spot/${keeper.id}"
    val key: String get() = "spot:$id"
    val place: TownPlace get() = TownPlace.Spot(spot)

    /** The talk of his story he has for the learner now; null: his meeting ([Keeper.levels]), or his day's (a [cycle]). */
    val story: KeeperStoryTalk? get() = if (cycle != null) null else Keepers.storyTalk(keeper, at)

    /** Tonight his pile breaks out ([CycleBreakout]) and the learner hasn't kept the watch to its end yet: the race. */
    val breakout: Boolean get() {
        val r = day?.round ?: return false
        return cycle?.breakout != null && day.kind == CycleKind.STAGE && r.breakout == day.date && !marks.decided(r.key)
    }

    /** The part of his own story he tells at the end of today's talk ([Keeper.tales]), or none. */
    val tale: KeeperTale? get() = Keepers.taleOf(this)

    /** His bubble's title now: tonight's breakout, the day's stage, the talk's he has, else his own. */
    val title: Text get() = (if (breakout) cycle?.breakout?.title else null) ?: day?.shown?.title ?: story?.title ?: keeper.title
}

/**
 * What someone at a spot has told the learner of the story he tells across visits ([GameState.keepers], by his key): how
 * many talks ([told], his meeting the first), and the ISO date of the last ([on]); how many parts of his own story
 * ([tales], [si.lanisce.lani.game.culture.Keeper.tales]), and the day of the last ([taleOn]).
 */
@Serializable
data class KeeperTold(val told: Int = 0, val on: String = "", val tales: Int = 0, val taleOn: String = "")

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
     * matter: they stay ([waiting] are the bubbles). Nobody while wolves or a bear are about (companion/GAME.md "People
     * react").
     */
    fun here(state: GameState, now: LocalDateTime, world: WorldFile = Cultures.current.world): List<ActiveKeeper> {
        // while wolves or a bear are about nobody sits out in the woods (in a storm he does: his talks know the rain)
        if (si.lanisce.lani.game.villagers.Reactions.threat(state.event?.kind)) return emptyList()
        val today = now.toLocalDate()
        val tod = TimeOfDay.of(now.hour)
        val time = tod.name.lowercase()
        val dawn = TimeOfDay.isDawn(now.hour, now.monthValue)
        val sky = SpotCycles.skyOf(state, today)
        return world.spots.mapNotNull { (spot, s) ->
            s.keeper?.let { k ->
                val marks = SpotCycles.marks(state, spot)
                val day = s.cycle?.let { SpotCycles.on(it, spot, SpotCycles.dayOf(today), marks) }
                ActiveKeeper(spot, k, cycle = s.cycle, day = day, marks = marks, time = tod, dawn = dawn, sky = sky, today = dayFor(s.cycle, today))
            }
        }.filter { a ->
            // the day's stage may bring him more often (he watches a smouldering pile) or at other hours; on the night his
            // pile breaks out he is there, and so he is whenever QA sets the cycle's day
            val times = a.day?.shown?.times ?: a.keeper.times
            val chance = if (a.breakout || a.day != null && SpotCycles.qaDay != null) 1f else a.day?.shown?.chance ?: a.keeper.chance
            (times.isEmpty() || time in times || dawn && "dawn" in times) && Happenings.roll(state.seed, today, a.key) < chance
        }.map { it.copy(at = at(state, it.key, it.today ?: today), told = state.keepers[it.key] ?: KeeperTold()) }
    }

    /**
     * The day a keeper's talks count on: today, or with a [cycle] the cycle's day (QA's, when it sets one: a stage after
     * another on one real day, each its own evening with him, as [done] has it).
     */
    private fun dayFor(cycle: SpotCycle?, today: LocalDate): LocalDate = if (cycle != null) SpotCycles.dayOf(today) else today

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
        if (k.keeper.talks.isEmpty() && k.cycle == null && k.keeper.tales.isEmpty()) return state
        val was = state.keepers[k.key] ?: KeeperTold()
        if (k.at + 1 <= was.told) return state
        // the day he was met on (the cycle's, for one: counted after QA moved on to another, it is still that evening's)
        val on = (k.today ?: dayFor(k.cycle, today)).toString()
        // the part of his own story he told at the end of it, counted once
        val tale = k.tale?.takeIf { was.taleOn != on }
        val tales = if (tale != null) was.tales + 1 else was.tales
        return state.copy(keepers = state.keepers + (k.key to KeeperTold(k.at + 1, on, tales, if (tale != null) on else was.taleOn)))
    }

    /**
     * The part of [k]'s own story ([Keeper.tales]) he tells at the end of today's talk: the next one once the learner has
     * spent its [KeeperTale.after] evenings with him (one a day: the one told today again when he is tapped again), never at
     * his meeting nor on the night his pile breaks out.
     */
    fun taleOf(k: ActiveKeeper): KeeperTale? {
        val tales = k.keeper.tales
        if (tales.isEmpty() || k.at <= 0 || k.breakout) return null
        val today = k.today?.toString()
        if (today != null && k.told.taleOn == today) return tales.getOrNull(k.told.tales - 1)
        return tales.getOrNull(k.told.tales)?.takeIf { it.after <= k.at }
    }

    /**
     * Whether [k]'s talk was done today (with a cycle, on the cycle's day: QA's, when it sets one, so it plays a stage after
     * another on one real day).
     */
    fun done(state: GameState, k: ActiveKeeper, today: LocalDate): Boolean = Happenings.done(state, k.key, dayFor(k.cycle, today))

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
        if (k.cycle != null) return dialogOf(MARKER + k.id + "/" + (if (k.breakout) "breakout" else k.day?.id ?: "day"), cycleLines(k, level), language, pair)
        val story = k.story
        val levels = story?.telling(round(k.keeper, k.at)) ?: k.keeper.levels
        val lv = Arrivals.levelFor(levels.keys, level) ?: return null
        val lines = (levels[lv] as? JsonObject)?.get("lines") as? JsonArray ?: return null
        val id = MARKER + k.id + story?.let { "/${it.id}" }.orEmpty()
        return dialogOf(id, lines, language, pair)
    }

    /** A dialog [id] of [lines] (a scene dialog's, every language), read in [pair] as a scene's is; null when it has none. */
    internal fun dialogOf(id: String, lines: List<JsonElement>, language: String, pair: LangPair): Dialog? {
        if (lines.isEmpty()) return null
        val scene = JsonObject(
            mapOf(
                "language" to JsonPrimitive(language),
                "dialogs" to JsonArray(listOf(JsonObject(mapOf("id" to JsonPrimitive(id), "lines" to JsonArray(lines))))),
            ),
        )
        val d = (inPair(scene, pair)["dialogs"] as? JsonArray)?.firstOrNull() ?: return null
        return runCatching { json.decodeFromJsonElement(Dialog.serializer(), d) }.getOrNull()?.takeIf { it.lines.isNotEmpty() }
    }

    /**
     * [k]'s talk today by his spot's cycle (companion/VILLAGERS.md, "The pile over the days"), at [level], every language:
     * his meeting the first evening, else his greeting for the hour; then, when the learner has been away, what he did
     * meanwhile (the stages over since their last evening: the latest two, and that it has been a while), what last night
     * brought; then the day's talk (a variant for the hour and the weather, the next one each visit) and the part of his own
     * story that is due. The night his pile breaks out, the breakout's call instead, its last line the race ([race]).
     */
    fun cycleLines(k: ActiveKeeper, level: String): List<JsonElement> {
        val c = k.cycle ?: return emptyList()
        val day = k.day ?: return emptyList()
        val out = ArrayList<JsonElement>()
        if (k.at <= 0) out += SpotCycles.linesAt(c.meeting ?: k.keeper.levels, level)
        else SpotCycles.variant(SpotCycles.fitting(c.greet, k.time, k.dawn, k.sky), k.at)?.let { out += SpotCycles.linesAt(it.levels, level) }
        val b = c.breakout
        if (k.breakout && b != null) {
            out += SpotCycles.linesAt(b.levels, level)
            return out
        }
        val last = runCatching { LocalDate.parse(k.told.on) }.getOrNull()
        if (k.at > 0 && last != null) {
            val passed = SpotCycles.passed(c, k.spot, last, day.date, k.marks).filter { it.stage.done != null }.takeLast(2)
            if (passed.isNotEmpty() && java.time.temporal.ChronoUnit.DAYS.between(last, day.date) >= AWAY_DAYS) out += SpotCycles.linesAt(c.missed, level)
            for (s in passed) out += SpotCycles.linesAt(s.stage.done, level)
            out += lastNight(k, c, day, last).flatMap { SpotCycles.linesAt(it, level) }
        }
        val talks = day.shown?.talks.orEmpty()
        SpotCycles.variant(SpotCycles.fitting(talks, k.time, k.dawn, k.sky), k.at)?.let { out += SpotCycles.linesAt(it.levels, level) }
        k.tale?.let { out += SpotCycles.linesAt(it.levels, level) }
        return out
    }

    /**
     * What last night brought that [k] tells a learner who wasn't there ([last]: their last evening with him): his pile's
     * breakout, which he covered alone, or a night of its [SpotCycle.nights] (the wind); by level.
     */
    private fun lastNight(k: ActiveKeeper, c: SpotCycle, day: CycleDay, last: LocalDate): List<JsonObject> {
        // last night: the one after yesterday's evening (a learner there yesterday evening didn't see it either)
        val night = day.date.minusDays(1)
        if (last.isAfter(night)) return emptyList()
        val r = SpotCycles.roundOn(c, k.spot, night) ?: return emptyList()
        if (r.key in k.marks.lost) return emptyList()
        val b = c.breakout
        if (b != null && r.breakout == night && r.key !in k.marks.saved) return listOfNotNull(b.missed)
        val stage = r.spans.firstOrNull { night in it }?.stage?.id ?: return emptyList()
        return c.nights.filter { stage in it.stages && SpotCycles.frac(SpotCycles.seedOf(c, k.spot), night, it.id) < it.chance }.take(1).map { it.told }
    }

    /**
     * Tonight's race against the fire ([CycleBreakout]) in [k]'s talk at [level]: the turn's index among the talk's lines (its
     * call's last line), the seconds it has, and the lines that follow it, saved or lost (every language); null on any other
     * night.
     */
    fun race(k: ActiveKeeper, level: String): Race? {
        val b = k.cycle?.breakout ?: return null
        if (!k.breakout) return null
        val lines = cycleLines(k, level)
        val turn = lines.indexOfLast { (it as? JsonObject)?.get("choices") != null }.takeIf { it == lines.lastIndex } ?: return null
        val lv = Arrivals.levelFor(b.seconds.keys, level)
        return Race(turn, lv?.let { b.seconds[it] } ?: RACE_SECONDS, SpotCycles.linesAt(b.saved, level), SpotCycles.linesAt(b.lost, level), k.day?.round?.key.orEmpty())
    }

    /**
     * The race of a breakout night: the learner's [turn] (its line's index) is against the clock, [seconds] long; what follows
     * when they choose right in time ([saved]) or not ([lost]), every language; the [round] it decides.
     */
    data class Race(val turn: Int, val seconds: Int, val saved: List<JsonElement>, val lost: List<JsonElement>, val round: String) {
        /** [lines] read in [pair] as [k]'s talk is (a dialog's lines), for the run to go on with. */
        fun read(lines: List<JsonElement>, language: String, pair: LangPair = L10n.pair): List<DialogLine> =
            Keepers.dialogOf("race", lines, language, pair)?.lines.orEmpty()
    }

    /** A race's seconds when its breakout gives none for the level. */
    const val RACE_SECONDS = 25

    /** Days away after which he says it has been a while ([SpotCycle.missed]). */
    const val AWAY_DAYS = 5L

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
