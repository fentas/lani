package si.lanisce.lani.game

import kotlinx.serialization.Serializable
import si.lanisce.lani.data.Drill
import si.lanisce.lani.data.DrillKind
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.game.culture.ReadingFile
import si.lanisce.lani.game.culture.localized
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.StoryBooks
import si.lanisce.lani.game.scene.tapTurn
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.Localized
import si.lanisce.lani.l10n.bi
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The treasure hunt of one level (companion/GAME.md, "The treasure map"): the level whose end it tests ([from], "A1") and
 * the one it leads to ([to], "A2"). The storyteller [offered] the map (ISO day; "" when the learner asked for it first, from
 * the grammar book), the learner [taken] it (""; the map waits in the chest and on Home until then), the [stations] tried,
 * and the day the treasure was [found] (every station passed, dug up: the level rose). [report]: the client id of the level
 * change sent to the node ("" before it was sent); [confirmed]: the node's learner profile shows the new level.
 */
@Serializable
data class TreasureHunt(
    val from: String,
    val to: String,
    val offered: String = "",
    val taken: String = "",
    val stations: Map<String, StationRecord> = emptyMap(),
    val found: String = "",
    val report: String = "",
    val confirmed: Boolean = false,
) {
    fun record(s: Station): StationRecord = stations[s.id] ?: StationRecord()

    fun passed(s: Station): Boolean = record(s).passed.isNotEmpty()
}

/**
 * A station's tries: how many, the day of the [last], the day it was [passed] ("" while it isn't), the best try ([right] of
 * [of]), the pages its last failed try missed most ([weak]: where the path is blocked, and what to practise) and the minutes
 * of the try that passed.
 */
@Serializable
data class StationRecord(
    val tries: Int = 0,
    val last: String = "",
    val passed: String = "",
    val right: Int = 0,
    val of: Int = 0,
    val weak: List<String> = emptyList(),
    val minutes: Int = 0,
)

/**
 * A station of the hunt, in the path's order: where it is in the village (a [spot] of the landscape, or "fire"), what it
 * tests and the learner's skill it counts for in their databases ([skill]: mastery-db's).
 */
enum class Station(val id: String, val emoji: String, val spot: String, val skill: String) {
    /** A letter at the level, read first, then its questions with the text hidden (the read-first flow). */
    LETTER("letter", "✉️", "road", "reading"),

    /** Dialog turns whose choices test the forms of the level's rules. */
    DIALOG("dialog", "💬", "meadow", "speaking"),

    /** The level's grammar pages: exercises that name them, and sentences changed (the car's transformations). */
    GRAMMAR("grammar", "🪨", "rocks", "writing"),

    /** Sentences of the level heard, their meaning picked; two short ones written. */
    LISTENING("listening", "👂", "riverbank", "listening"),

    /** The storyteller's riddles by the fire. */
    RIDDLE("riddle", "🕵️", "fire", "vocabulary"),

    /** Short sentences of the level said out loud; only where something can listen. */
    SPEAKING("speaking", "🎤", "highseat", "speaking");

    /** "Pismo · The letter". */
    val label: String get() = bi(NAME_KEYS.getValue(this))

    /** Its name in each language, for a message's argument ("Pismo", "The letter"). */
    val title: Localized get() = localized { L10n.text(it, NAME_KEYS.getValue(this)) }

    /** Where it is, in [lang]: "na cesti", "by the stream". */
    fun where(lang: Lang): String = L10n.text(lang, WHERE_KEYS.getValue(this))

    /** Where it is in each language, for a message's argument. */
    val whereText: Localized get() = localized(::where)

    /** Where it is, "target · base": "Na cesti · On the road". */
    val whereShown: String get() = both(localized { where(it).replaceFirstChar { c -> c.uppercase() } })

    companion object {
        fun of(id: String): Station? = entries.firstOrNull { it.id == id }

        private val NAME_KEYS = mapOf(
            LETTER to "treasure.station.letter", DIALOG to "treasure.station.dialog", GRAMMAR to "treasure.station.grammar",
            LISTENING to "treasure.station.listening", RIDDLE to "treasure.station.riddle", SPEAKING to "treasure.station.speaking",
        )

        /** Where each is: the landscape's spots (always there, in every culture's village) and the fire. */
        private val WHERE_KEYS = mapOf(
            LETTER to "townMarkers.onRoad", DIALOG to "townMarkers.inMeadow", GRAMMAR to "townMarkers.byRocks",
            LISTENING to "townMarkers.byStream", RIDDLE to "townLogic.byFire", SPEAKING to "townMarkers.onHighSeat",
        )
    }
}

/** A text in the learner's pair, "target · base" (one half when both read the same). */
internal fun both(t: Localized): String {
    val a = t.of(L10n.pair.target)
    val b = t.of(L10n.pair.base)
    return if (a == b) a else "$a · $b"
}

/**
 * What the hunt's stations are built from (all of it existing content): the book's [pages], the scenes' dialogs, the
 * readings of the village's culture and the tutor's ([read]: those read), the exercises that name pages (the modules', the
 * challenges' questions), the car's [drills] of the village's [language], the learner's [cards] (the fallback), and whether
 * something can speak ([canSpeak]: a voice for the listening) and listen ([canListen]: the speaking station).
 */
data class HuntContent(
    val language: String,
    val pages: List<GrammarPage> = emptyList(),
    val scenes: List<SceneSpec> = emptyList(),
    val readings: List<ReadingFile> = emptyList(),
    val read: Set<String> = emptySet(),
    val exercises: List<Exercise> = emptyList(),
    val drills: List<Drill> = emptyList(),
    val cards: List<ReviewCard> = emptyList(),
    val canSpeak: Boolean = true,
    val canListen: Boolean = true,
)

/** How the hunt stands for the learner at their level now. */
enum class HuntStatus {
    /** No hunt for this level. */
    NONE,

    /** The storyteller gave the map; it waits to be taken. */
    OFFERED,

    /** The learner is on the path. */
    ON,

    /** Every station passed and the treasure dug up: the level rose. */
    FOUND,
}

/** The treasure dug up: what came into the stores ([paid]), the 🤝, the keepsake of the level reached ([to]). */
data class Dug(val from: String, val to: String, val paid: Map<Res, Int>, val help: Int, val keepsake: Keepsake)

/** What a treasure holds for the village besides the stores: one keepsake per level reached, kept in the chest for good. */
data class Keepsake(val level: String, val emoji: String, private val key: String) {
    /** "Stari kompas · The old compass". */
    val name: String get() = bi(key)

    /** "🧭 Stari kompas" in each language, for a message's argument. */
    val text: Localized get() = localized { "$emoji ${L10n.text(it, key)}" }
}

/**
 * The treasure map (companion/GAME.md, "The treasure map"): an opt-in level-up. When the learner is ready
 * ([LevelReadiness]), the storyteller gives the map ([offer]); the learner takes it ([take]; or starts it earlier from the
 * grammar book). Its six stations test the end of the level, each on a day of its own if they like ([challenge],
 * [finishStation]); a station passes at [PASS_SHARE] of its questions right, and one not passed shows where the path is
 * blocked and can be tried again the next day. With every station passed the treasure is dug up ([dig]): lots for the
 * stores, the level's keepsake, and the new level, which the app sends to the node. Pure.
 */
object Treasure {
    /** The share of a station's questions answered right (fully) that passes it. */
    const val PASS_SHARE = 0.7

    /** Questions in the dialog, grammar and listening stations. */
    const val QUESTIONS = 6

    /** Riddles by the fire. */
    const val RIDDLES = 4

    /** Sentences said out loud. */
    const val SAYINGS = 3

    /** The share of each store's size the treasure brings. */
    const val STORE_SHARE = 0.4

    /** 🤝 the treasure brings: the whole village helped dig. */
    const val HELP = 10

    /** What each keepsake adds to every resource's production, for good. */
    const val KEEPSAKE_BONUS = 0.05f

    /** The keepsakes, by the level reached. */
    val keepsakes: List<Keepsake> = listOf(
        Keepsake("A2", "🧭", "treasure.keepsakeA2"),
        Keepsake("B1", "🏺", "treasure.keepsakeB1"),
        Keepsake("B2", "🪔", "treasure.keepsakeB2"),
        Keepsake("C1", "📜", "treasure.keepsakeC1"),
        Keepsake("C2", "👑", "treasure.keepsakeC2"),
    )

    fun keepsake(level: String): Keepsake? = keepsakes.firstOrNull { it.level == LevelReadiness.norm(level) }

    /** How many of [total] questions pass a station: [PASS_SHARE] of them, rounded (4 of 6, 4 of 5, 3 of 4, 2 of 3), 1 at least. */
    fun passMark(total: Int): Int = (total * PASS_SHARE).roundToInt().coerceIn(1, total.coerceAtLeast(1))

    // --- the hunt's state ----------------------------------------------------------------------------------------------

    /**
     * How the hunt stands at the learner's [level] (their level now, the new one once the treasure is found): a hunt of
     * another level is none (the tutor moved the level, or it's an old one).
     */
    fun status(h: TreasureHunt?, level: String): HuntStatus {
        h ?: return HuntStatus.NONE
        val l = LevelReadiness.norm(level)
        return when {
            h.found.isNotEmpty() -> if (l == h.to) HuntStatus.FOUND else HuntStatus.NONE
            h.from != l -> HuntStatus.NONE
            h.taken.isEmpty() -> HuntStatus.OFFERED
            else -> HuntStatus.ON
        }
    }

    /** The hunt of [s] as it stands at [level], or null when there is none for it ([status]). */
    fun current(s: GameState, level: String): TreasureHunt? = s.treasure?.takeIf { status(it, level) != HuntStatus.NONE }

    /** A hunt of the learner's [level] is waiting or on: no other can begin. */
    private fun busy(h: TreasureHunt?, level: String): Boolean = status(h, level).let { it == HuntStatus.OFFERED || it == HuntStatus.ON }

    /**
     * The storyteller gives the map ([teller], their name in the village's chronicle) when the learner is [ready] for the
     * next level and no hunt of their level waits or is on (the treasure that brought them to it is found: the next one
     * can come); once given it stays until taken. [s] unchanged otherwise.
     */
    fun offer(s: GameState, ready: Readiness, teller: String, today: LocalDate, now: Long): GameState {
        val to = ready.to ?: return s
        if (!ready.ready || busy(s.treasure, ready.from)) return s
        return s.copy(treasure = TreasureHunt(ready.from, to, offered = today.toString()))
            .logged(now, "🗺️" to bi("treasure.chronicleOffer", "teller" to teller))
    }

    /**
     * The learner takes the map at [level] (the storyteller's, or from the grammar book before it was offered): the hunt
     * begins (after a treasure found, the next level's). [s] unchanged when it is on already, or there is no next level.
     */
    fun take(s: GameState, level: String, today: LocalDate, now: Long): GameState {
        val from = LevelReadiness.norm(level)
        val to = LevelReadiness.next(from) ?: return s
        val h = s.treasure
        val hunt = when (status(h, from)) {
            HuntStatus.OFFERED -> h!!.copy(taken = today.toString())
            // none, or the treasure that brought the learner to this level: the next level's hunt
            HuntStatus.NONE, HuntStatus.FOUND -> TreasureHunt(from, to, taken = today.toString())
            HuntStatus.ON -> return s
        }
        return s.copy(treasure = hunt).logged(now, "🗺️" to bi("treasure.chronicleTaken", "to" to to))
    }

    /** The stations left out where the phone can't do them: the listening without a voice, the speaking with nothing to listen. */
    fun skipped(st: Station, canSpeak: Boolean, canListen: Boolean): Boolean =
        (st == Station.LISTENING && !canSpeak) || (st == Station.SPEAKING && !canListen)

    /** The stations the hunt needs passed now. */
    fun required(canSpeak: Boolean, canListen: Boolean): List<Station> = Station.entries.filterNot { skipped(it, canSpeak, canListen) }

    /** Whether station [st] of [h] can be tried on [today]: not passed, and not tried today already without passing. */
    fun canTry(h: TreasureHunt, st: Station, today: LocalDate): Boolean {
        val r = h.record(st)
        return r.passed.isEmpty() && r.last != today.toString()
    }

    /** Every station of [required] passed: the treasure can be dug up. */
    fun complete(h: TreasureHunt, required: List<Station>): Boolean = required.isNotEmpty() && required.all { h.passed(it) }

    /**
     * Station [st] was played: [correct] of [total] fully right (an almost-right answer, a hinted one, doesn't count, as in
     * the requests), the pages of the wrong ones ([missed], most missed first), in [minutes]. A pass marks it passed; a fail
     * keeps where the path is blocked ([StationRecord.weak]) and waits for the next day. Nothing is lost either way.
     * [required]: the stations the hunt needs, to say how many are left.
     */
    fun finishStation(
        s: GameState, st: Station, correct: Int, total: Int, missed: List<String>, minutes: Int, required: List<Station>, today: LocalDate, now: Long,
    ): Pair<GameState, ChallengeResult> {
        val h = s.treasure?.takeIf { it.taken.isNotEmpty() && it.found.isEmpty() }
            ?: return s to ChallengeResult(false, emptyMap(), emptyMap(), emptyList(), "")
        val r = h.record(st)
        val passed = total > 0 && correct >= passMark(total)
        val day = today.toString()
        val better = correct * r.of.coerceAtLeast(1) >= r.right * total.coerceAtLeast(1)
        val rec = r.copy(
            tries = r.tries + 1,
            last = day,
            passed = if (passed) r.passed.ifEmpty { day } else r.passed,
            right = if (better) correct else r.right,
            of = if (better) total else r.of,
            weak = if (passed) emptyList() else missed.distinct().take(2),
            minutes = if (passed) minutes else r.minutes,
        )
        val hunt = h.copy(stations = h.stations + (st.id to rec))
        var n = s.copy(treasure = hunt)
        val left = required.count { !hunt.passed(it) }
        val message = if (passed) {
            n = n.logged(now, st.emoji to bi("treasure.chronicleStation", "station" to st.title, "where" to st.whereText))
            if (left == 0) "🗺️ ${bi("treasure.allPassed")}" else "🗺️ ${bi("treasure.stationsLeft", "left" to left)}"
        } else "⛔ ${bi("treasure.retryTomorrow")}"
        return n to ChallengeResult(passed, emptyMap(), emptyMap(), emptyList(), message)
    }

    /** What the treasure brings the stores: [STORE_SHARE] of each store's size now. */
    fun reward(s: GameState): Map<Res, Int> {
        val caps = GameEngine.attributes(s).caps
        return Res.entries.associateWith { r -> (caps.getValue(r) * STORE_SHARE).roundToInt() }
    }

    /**
     * The treasure is dug up (every station of [required] passed): the stores get [reward] (as much as they hold), the
     * village [HELP] 🤝, and the level's keepsake for good ([GameState.treasures]); the hunt is found, and the chronicle
     * says so. The new level is the app's to send to the node. Null (and [s]) when it can't be dug up yet.
     */
    fun dig(s: GameState, required: List<Station>, today: LocalDate, now: Long): Pair<GameState, Dug?> {
        val h = s.treasure?.takeIf { it.taken.isNotEmpty() && it.found.isEmpty() && complete(it, required) } ?: return s to null
        val keep = keepsake(h.to) ?: return s to null
        val (paid, got) = credit(s, reward(s))
        val day = today.toString()
        val n = Help.earn(paid, HELP).copy(treasure = h.copy(found = day), treasures = paid.treasures + (h.to to day))
            .logged(now, "🪙" to bi("treasure.chronicleFound", "to" to h.to, "keepsake" to keep.text))
        return n to Dug(h.from, h.to, got, HELP, keep)
    }

    /** The level report for the node was queued with client id [id]. */
    fun reported(s: GameState, id: String): GameState = s.treasure?.let { s.copy(treasure = it.copy(report = id)) } ?: s

    /**
     * The learner's level as the app goes by it: the node's ([node], its learner profile), or the level the treasure just
     * brought while the node doesn't have it yet ([TreasureHunt.found], not [TreasureHunt.confirmed]): the new rules and
     * readings don't wait for the node.
     */
    fun level(node: String, h: TreasureHunt?): String {
        h ?: return node
        if (h.found.isEmpty() || h.confirmed) return node
        val n = Introduction.rank(node) ?: 0
        return if ((Introduction.rank(h.to) ?: 0) > n) h.to else node
    }

    /** The node shows the level the treasure brought ([node]): confirmed, the app goes by the node's again. */
    fun confirm(s: GameState, node: String): GameState {
        val h = s.treasure ?: return s
        if (h.found.isEmpty() || h.confirmed) return s
        return if ((Introduction.rank(node) ?: 0) >= (Introduction.rank(h.to) ?: 0)) s.copy(treasure = h.copy(confirmed = true)) else s
    }

    /** What the keepsakes add, for good: [KEEPSAKE_BONUS] of every resource each. */
    fun effect(s: GameState): ToolEffect {
        val n = s.treasures.keys.count { keepsake(it) != null }
        return if (n == 0) ToolEffect() else ToolEffect(production = Res.entries.associateWith { KEEPSAKE_BONUS * n })
    }

    // --- the stations' runs --------------------------------------------------------------------------------------------

    /**
     * Station [st]'s run for the hunt of [s] on [today] (another one for each try), from [content]; null when there is no
     * hunt on, or nothing to ask. Every station falls back on the learner's cards where the content has too little.
     */
    fun challenge(s: GameState, st: Station, content: HuntContent, today: LocalDate): Challenge? {
        val h = s.treasure?.takeIf { it.taken.isNotEmpty() && it.found.isEmpty() } ?: return null
        if (skipped(st, content.canSpeak, content.canListen)) return null
        val r = rng(s.seed, "treasure", h.from, st.id, h.record(st).tries, today.toString())
        val pool = ContentPool(content.cards, canSpeak = content.canSpeak)
        val run = when (st) {
            Station.LETTER -> letter(content, h.from, r, pool)
            Station.DIALOG -> Run(dialog(content, h.from, r).fill(Res.WISDOM, QUESTIONS, pool, r))
            Station.GRAMMAR -> Run(forms(content, h.from, r).fill(Res.STONE, QUESTIONS, pool, r))
            Station.LISTENING -> Run(listening(content, h.from, r).fill(Res.WOOD, QUESTIONS, pool, r))
            Station.RIDDLE -> Run(riddles(content, h.from, r).fill(Res.FOOD, RIDDLES, pool, r))
            Station.SPEAKING -> Run(sayings(content, h.from, r).fill(Res.WISDOM, SAYINGS, pool, r))
        }
        if (run.items.isEmpty()) return null
        val mark = passMark(run.items.size)
        val intro = listOf(bi(ABOUT_KEYS.getValue(st), "from" to h.from), "${st.emoji} ${st.whereShown}", "$mark/${run.items.size} ✔").joinToString("\n")
        return Challenge(
            title = "🗺️ ${st.label}", intro = intro, emoji = st.emoji,
            exercises = run.items.map { it.exercise }, skills = run.items.map { it.skill }, cardIds = run.items.map { it.cardId },
            passMark = mark, text = run.text,
        )
    }

    private val ABOUT_KEYS = mapOf(
        Station.LETTER to "treasure.about.letter", Station.DIALOG to "treasure.about.dialog", Station.GRAMMAR to "treasure.about.grammar",
        Station.LISTENING to "treasure.about.listening", Station.RIDDLE to "treasure.about.riddle", Station.SPEAKING to "treasure.about.speaking",
    )

    /** A station's run: its questions, and the text they are about (the letter), read first. */
    private data class Run(val items: List<Item>, val text: ReadFirst? = null)

    /** These questions, filled up to [n] from the learner's cards (the gathering run's way, [Content.pick]). */
    private fun List<Item>.fill(res: Res, n: Int, pool: ContentPool, r: Random): List<Item> {
        if (size >= n || pool.cards.isEmpty()) return take(n)
        return this + Content.pick(res, n - size, pool, r).items.take(n - size)
    }

    private fun rank(level: String?): Int? = Introduction.rank(level)

    /** The pages of the book at [level] exactly. */
    private fun levelPages(c: HuntContent, level: String): Set<String> = c.pages.filter { rank(it.level) == rank(level) }.map { it.id }.toSet()

    /** Round-robin over [groups] in a shuffled order: one of each, then the second of each …, [n] at most. */
    private fun <T> spread(groups: Collection<List<T>>, n: Int, r: Random): List<T> {
        val queues = groups.map { it.shuffled(r).toMutableList() }.shuffled(r)
        val out = ArrayList<T>()
        while (out.size < n && queues.any { it.isNotEmpty() }) for (q in queues) if (out.size < n && q.isNotEmpty()) out += q.removeAt(0)
        return out
    }

    /** A sentence's words as chips: split at spaces, the punctuation at their ends left off. */
    private fun chips(s: String): List<String> = s.split(' ').map { it.trim().trim(',', '.', '!', '?', ';', ':', '»', '«', '"') }.filter { it.isNotEmpty() }

    private val base: String get() = L10n.pair.base.code

    /**
     * Dialog turns that test a form of a rule of [level] (a wrong choice differs from the one right choice in a word:
     * [Forms.turn]; its rule a page of the level), asked with the line before them and what the learner wants to say: the
     * form's choices only, one turn per rule first. The module's and the challenges' choice exercises with a gap on the
     * level's pages come next.
     */
    private fun dialog(c: HuntContent, level: String, r: Random): List<Item> {
        val own = levelPages(c, level)
        val byRule = LinkedHashMap<String, MutableList<Exercise.Choice>>()
        val seen = HashSet<String>()
        for (sc in c.scenes) {
            if (sc.language != c.language) continue
            val names = sc.people.associate { it.id to it.name }
            for (d in sc.dialogs) for ((i, line) in d.lines.withIndex()) {
                if (line.choices.size < 2 || line.tapTurn) continue
                val right = line.choices.indices.singleOrNull { line.choices[it].ok } ?: continue
                val form = Forms.turn(line.choices.map { it.sl }, right) ?: continue
                val rule = form.wrong.firstNotNullOfOrNull { line.choices[it].rule(line) } ?: line.grammar ?: continue
                if (rule !in own) continue
                val ok = line.choices[right]
                if (!seen.add(ok.sl)) continue
                val before = d.lines.getOrNull(i - 1)?.takeIf { it.choices.isEmpty() && !it.sl.isNullOrBlank() }
                val who = before?.who?.let { names[it] ?: it }
                val options = (listOf(right) + form.wrong).map { line.choices[it].sl }.distinct().shuffled(r)
                val why = form.wrong.firstNotNullOfOrNull { line.choices[it].why }
                byRule.getOrPut(rule) { mutableListOf() } += Exercise.Choice(
                    prompt = listOfNotNull(
                        before?.let { b -> if (who != null) "$who: »${b.sl}«" else "»${b.sl}«" },
                        ok.en.takeIf { it.isNotBlank() }?.let { "💬 ${bi("adaptive.youWantToSay")}: $it" },
                    ).joinToString("\n"),
                    options = options, answer = options.indexOf(ok.sl),
                    explain = listOfNotNull("${ok.sl} · ${ok.en}".removeSuffix(" · "), why).joinToString("\n"),
                    instruction = bi("treasure.whatDoYouSay"), sayOptions = true, grammar = rule,
                )
            }
        }
        val turns = spread(byRule.values, QUESTIONS, r).map { Item(it, Res.WISDOM, null) }
        if (turns.size >= QUESTIONS) return turns
        val gaps = c.exercises.filterIsInstance<Exercise.Choice>().filter { it.audio == null && it.grammar in own && "__" in it.prompt }
        return turns + spread(gaps.groupBy { it.grammar!! }.values, QUESTIONS - turns.size, r).map { Item(it, Res.WISDOM, null) }
    }

    /** An exercise that tests a form on paper: not heard, not graded by the tutor, not free writing. */
    private fun written(e: Exercise): Boolean = when (e) {
        is Exercise.Choice -> e.audio == null
        is Exercise.Cloze -> e.audio == null
        is Exercise.Reorder -> e.audio == null
        is Exercise.Multi -> true
        is Exercise.Translate -> e.grade != "claude"
        else -> false
    }

    /**
     * The level's pages: the exercises that name them (the modules', the challenges' questions), one per page first, and
     * sentences changed as the car's transformations change them (a set of the level: "Micka kuha kosilo." → into the past,
     * its words as chips with the old form among them), about half each.
     */
    private fun forms(c: HuntContent, level: String, r: Random): List<Item> {
        val own = levelPages(c, level)
        val named = spread(c.exercises.filter { it.grammar in own && written(it) }.distinct().groupBy { it.grammar!! }.values, QUESTIONS, r)
        val sets = c.drills.filter { it.language == c.language && it.kind == DrillKind.TRANSFORM }.flatMap { it.transforms }
            .filter { rank(it.level) == rank(level) }
        val changed = spread(sets.map { s -> s.items.mapNotNull { item -> transform(s, item, c.language) } }, QUESTIONS, r)
        val half = QUESTIONS / 2
        val first = named.take(half) + changed.take(QUESTIONS - named.take(half).size)
        val rest = (named.drop(half) + changed.drop(QUESTIONS - named.take(half).size)).take(QUESTIONS - first.size)
        return (first + rest).shuffled(r).map { Item(it, Res.STONE, null) }
    }

    /** A transformation as word chips: the new sentence's words, and the old words that change among them. */
    private fun transform(s: si.lanisce.lani.data.TransformSet, item: si.lanisce.lani.data.TransformItem, language: String): Exercise.Reorder? {
        val to = item.to.said(language)
        val from = item.from.said(language)
        val tokens = chips(to)
        if (tokens.size !in 2..9) return null
        val have = tokens.map { it.lowercase() }.toSet()
        val extra = chips(from).filter { it.lowercase() !in have }.distinct().take(2)
        val todo = item.instruction ?: s.instruction
        val what = listOf(todo.said(language), todo.meaning(base)).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        return Exercise.Reorder(
            prompt = "»$from«\n🔄 $what",
            tokens = tokens, solutions = listOf(tokens), distractors = extra,
            explain = "$to · ${item.to.meaning(base)}", instruction = bi("treasure.changeIt"), grammar = s.rule,
        )
    }

    /** The book's example sentences of [level] and the levels below, [words] long: (the sentence, its meaning, its page). */
    private fun examples(c: HuntContent, level: String, words: IntRange): List<Triple<String, String, String>> {
        val top = rank(level) ?: 0
        return c.pages.filter { (rank(it.level) ?: 0) <= top }.sortedByDescending { rank(it.level) ?: 0 }.flatMap { p ->
            p.examples.mapNotNull { e ->
                val said = e.target(p.language).trim()
                val meant = e.meaning(base).trim()
                (Triple(said, meant, p.id)).takeIf { said.isNotEmpty() && meant.isNotEmpty() && meant != said && chips(said).size in words }
            }
        }.distinctBy { it.first }
    }

    /**
     * Sentences of the level heard (the book's examples, the level's own first), their meaning picked among others', and
     * two short ones written as heard.
     */
    private fun listening(c: HuntContent, level: String, r: Random): List<Item> {
        val all = examples(c, level, 2..12)
        if (all.size < 4) return emptyList()
        val own = levelPages(c, level)
        val (mine, lower) = all.partition { it.third in own }
        val order = mine.shuffled(r) + lower.shuffled(r)
        val written = order.filter { ListeningMix.short(it.first) }.take(2)
        val heard = order.filter { it !in written }.take(QUESTIONS - written.size)
        val meanings = all.map { it.second }.distinct()
        val choices = heard.mapNotNull { (said, meant, _) ->
            val wrong = meanings.filter { it != meant }.shuffled(r).take(3)
            if (wrong.size < 2) return@mapNotNull null
            val opts = (wrong + meant).shuffled(r)
            Item(Exercise.Choice("", opts, opts.indexOf(meant), explain = "$said · $meant", audio = said, instruction = bi("common.listenPickMeaning")), Res.WOOD, null)
        }
        val typed = written.map { (said, meant, _) ->
            Item(Exercise.Dictation(said, listOf(said), explain = "= $meant", instruction = bi("common.writeWhatHear")), Res.WOOD, null)
        }
        // the written ones among the heard, not first
        return (choices.take(2) + typed + choices.drop(2)).take(QUESTIONS)
    }

    /** The storyteller's riddles of the level and below (the car's riddle drill), else the culture's riddles by the road. */
    private fun riddles(c: HuntContent, level: String, r: Random): List<Item> {
        val top = rank(level) ?: 0
        val lang = c.language
        val drill = c.drills.filter { it.language == lang && it.kind == DrillKind.RIDDLE }.flatMap { it.riddles }.filter { (rank(it.level) ?: 0) <= top }
        fun bare(s: String) = s.trim().trimEnd('!', '.', '?')
        if (drill.size >= 2) {
            val answers = drill.map { bare(it.answer.said(lang)) }.distinct()
            return drill.shuffled(r).take(RIDDLES).map { q ->
                val right = bare(q.answer.said(lang))
                val opts = (answers.filter { it != right }.shuffled(r).take(3) + right).shuffled(r)
                val clues = (q.clues.map { it.said(lang) } + q.ask.said(lang)).joinToString("\n")
                Item(
                    Exercise.Choice("🕵️ $clues", opts, opts.indexOf(right), explain = "${q.answer.said(lang)} · ${q.answer.meaning(base)}", instruction = bi("treasure.canYouGuess"), sayOptions = true),
                    Res.FOOD, null,
                )
            }
        }
        return Surprises.riddles.shuffled(r).take(RIDDLES).map { q ->
            val opts = q.options.shuffled(r)
            Item(Exercise.Choice("❓ »${q.sl}«", opts, opts.indexOf(q.options.first()), explain = "${q.options.first()} · ${q.en}", instruction = bi("treasure.canYouGuess"), sayOptions = true), Res.FOOD, null)
        }
    }

    /**
     * Short sentences to say out loud from what they mean (said by the learner, heard by the node's Whisper or the phone):
     * the sentences built in the car at the level, else the book's examples.
     */
    private fun sayings(c: HuntContent, level: String, r: Random): List<Item> {
        val top = rank(level) ?: 0
        val lang = c.language
        val built = c.drills.filter { it.language == lang && it.kind == DrillKind.BUILD }.flatMap { it.builds }
            .filter { (rank(it.level) ?: 0) <= top }
            .map { b -> b.steps.map { it.text }.filter { chips(it.said(lang)).size in 2..6 }.map { Triple(it.said(lang), it.meaning(base), b.rules.firstOrNull()) } }
            .filter { it.isNotEmpty() }
        val picked = built.shuffled(r).map { it.random(r) }.take(SAYINGS)
        val lines = picked + examples(c, level, 2..6).shuffled(r).map { Triple(it.first, it.second, it.third as String?) }.take(SAYINGS - picked.size)
        return lines.map { (said, meant, page) ->
            Item(Exercise.Speak(said, meant, show = false, instruction = bi("treasure.sayIt"), explain = "$said · $meant", grammar = page), Res.WISDOM, null)
        }
    }

    /**
     * The letter: a reading of the level (a letter first, one not read yet first; the level below where there is none),
     * read first and hidden while its questions are asked; else two of the culture's letters by the road.
     */
    private fun letter(c: HuntContent, level: String, r: Random, pool: ContentPool): Run {
        val top = rank(level) ?: 0
        fun score(f: ReadingFile): Int = (if (f.kind == "letter") 0 else 2) + (if (f.id in c.read) 1 else 0)
        val usable = c.readings.filter { it.questions.isNotEmpty() && it.lines.isNotEmpty() || it.steps.isNotEmpty() && it.questions.isNotEmpty() }
        val at = usable.filter { rank(it.level) == top }.ifEmpty { usable.filter { (rank(it.level) ?: 0) == top - 1 } }
        val pick = at.shuffled(r).minByOrNull(::score)
        if (pick != null) return reading(pick, r)
        val letters = Surprises.letters.shuffled(r).take(2)
        if (letters.isNotEmpty()) {
            val items = letters.flatMap { l ->
                l.questions.map { q ->
                    val opts = q.options.shuffled(r)
                    Item(Exercise.Choice("✉️ ${q.ask}", opts, opts.indexOf(q.options.first()), explain = q.explain, instruction = bi("treasure.aboutTheLetter"), sayOptions = true, grammar = q.grammar), Res.WISDOM, null)
                }
            }
            val paragraphs = letters.flatMap { l -> l.text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { StoryBooks.sentences(it) } }
            return Run(items, ReadFirst("✉️ ${Station.LETTER.label}", paragraphs))
        }
        return Run(Content.pick(Res.WISDOM, QUESTIONS, pool, r).items)
    }

    /** Reading [f] as the letter's station: its text to read first, its questions (a true/false one as "Drži" or "Ne drži"). */
    private fun reading(f: ReadingFile, r: Random): Run {
        val t = L10n.pair.target
        val lines = listOfNotNull(f.intro?.of(t)) + f.steps.mapIndexed { i, s -> "${i + 1}. ${s.of(t)}" } + f.lines.map { it.text.of(t) }
        val paragraphs = lines.map { it.trim() }.filter { it.isNotEmpty() }.map { StoryBooks.sentences(it) }
        val yes = L10n.text(t, "reading.true")
        val no = L10n.text(t, "reading.false")
        val items = f.questions.map { q ->
            val (options, answer) = if (q.type == "true_false") listOf(yes, no) to (if (q.answer == true) yes else no)
            else q.options.shuffled(r) to q.options.first()
            Item(
                Exercise.Choice("✉️ ${q.ask.bi()}", options, options.indexOf(answer), explain = q.explain.bi(), instruction = bi("treasure.aboutTheLetter"), sayOptions = q.type != "true_false"),
                Res.WISDOM, null,
            )
        }
        return Run(items, ReadFirst("${Station.LETTER.emoji} ${f.title.bi()}", paragraphs))
    }

    /** The pages of the wrong answers of a run ([exercises], [right]: one a question), most missed first. */
    fun missed(exercises: List<Exercise>, right: List<Boolean>): List<String> =
        exercises.indices.filter { right.getOrNull(it) != true }.mapNotNull { exercises[it].grammar }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
}
