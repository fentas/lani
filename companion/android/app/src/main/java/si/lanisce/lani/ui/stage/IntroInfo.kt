package si.lanisce.lani.ui.stage

import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Module
import si.lanisce.lani.data.Pack
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.QuestMeta
import si.lanisce.lani.data.Scenario
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.Challenge
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.QuestQueue
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.game.villagers.at
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.Localized
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.game.progressText
import si.lanisce.lani.ui.game.whyText
import kotlin.math.ceil

/** A line in both languages. */
data class Said(val sl: String, val en: String = "")

/** "Kaj boš vadil · What you'll practise". */
data class Practice(
    /** The skills trained, the main one first. */
    val skills: List<Res>,
    /** Exercises in the run. */
    val count: Int,
    /** "izbira · choice" → how many, most first. */
    val kinds: List<Pair<String, Int>> = emptyList(),
    /** About how long it takes. */
    val minutes: Int,
    /** New words to meet first (a word pack). */
    val words: Int = 0,
    /** A timed run's limit. */
    val timeLimit: Int? = null,
    /** Right answers needed to win or to complete. */
    val passMark: Int = 0,
    /** A role-play's goals. */
    val goals: List<String> = emptyList(),
)

/** What a run pays. */
data class Pays(
    /** Paid on success (a quest's reward). */
    val reward: Map<Res, Int> = emptyMap(),
    /** The most the answers can bring (every answer right). */
    val answers: Map<Res, Int> = emptyMap(),
    /** A role-play pays this for every line. */
    val perLine: Res? = null,
    /** Friendship points with the one who asks. */
    val bond: Int = 0,
    /** What's at stake beyond resources (events), "Slovene · English". */
    val stake: String? = null,
)

enum class IntroKind { QUEST, MODULE, EVENT, GATHER, PACK, TALK }

/**
 * Everything an intro shows before a run: who asks, their request (one short Slovene line, voiced), the
 * story around it, what Jan will practise and for how long, and what it pays. Built by [Intros].
 */
data class IntroInfo(
    val kind: IntroKind,
    val who: StagePerson,
    val place: StagePlace,
    val emoji: String,
    /** "Mickina kuhinja · Micka's kitchen" */
    val title: String,
    /** What they say, voiced. */
    val request: Said,
    /** The story around it: narration lines. */
    val story: List<Said>,
    val practice: Practice,
    val pays: Pays,
    /** A warning to read before starting (leaving an event ends the fight). */
    val warning: String? = null,
    /** A note on how it runs (a timed run moves on by itself). */
    val note: String? = null,
    val start: String = "▶  ${bi("introInfo.start")}",
    /**
     * A run against the clock, said plainly before Start: "⏱ Izziv na čas: 8 vprašanj, 2 minuti · A timed challenge: 8
     * questions, 2 minutes", and how the clock goes ([Intros.timed]).
     */
    val timed: Timed? = null,
    /** A request tried before: how close Jan is ("🎯 Najboljše 7/12 · potrebuješ 8 · Best 7/12, you need 8  🔁 3 poskusi · 3 tries"). */
    val progress: String? = null,
    /** A request waiting in "Later", and why ("⏸️ Najprej · First: Kam? Na tržnico!"): it can be played all the same. */
    val waits: String? = null,
)

/** A timed run, announced: the [line] ("⏱ Izziv na čas: …"), and [how] the clock goes. */
data class Timed(val line: String, val how: String)

/** Builds [IntroInfo] for each kind of launch. Pure. */
object Intros {
    /** Shown on an event's intro and above its questions: leaving after an answer resolves the event. */
    val LEAVE_WARNING: String get() = "⚠️ ${bi("introInfo.leavingEndsFight")}"

    /** A villager's request; [why] it waits in "Later", when it does. */
    fun quest(q: Quest, c: Challenge, who: StagePerson, why: QuestQueue.Why? = null): IntroInfo = IntroInfo(
        kind = IntroKind.QUEST,
        who = who,
        place = StagePlace.of(Run.Quest(q.giver, q.emoji), who),
        emoji = q.emoji,
        title = q.title,
        request = request(who, Run.Quest(q.giver, q.emoji)),
        story = listOf(split(q.story)) + notes(c.intro, skip = q.story),
        practice = practice(c.exercises, c.skills, passMark = c.passMark),
        pays = Pays(reward = q.reward, answers = answers(c.exercises, c.skills), bond = if (who.id != null) Bonds.QUEST else 0),
        progress = progressText(q),
        waits = why?.let(::whyText),
    )

    /**
     * A village task that isn't a request: a project step, a festival, the day's surprise (companion/GAME.md). [who]
     * asks ([run] puts them on stage; [ask], when given, is what they say instead of the usual ask), the challenge's
     * intro is the story, [reward] what success pays besides the answers, [stake] what else it means ("the step costs …"),
     * [bond] the ♥ with them.
     */
    fun task(
        c: Challenge, who: StagePerson, run: Run, ask: Said? = null, reward: Map<Res, Int> = emptyMap(), stake: String? = null, bond: Int = 0,
    ): IntroInfo = IntroInfo(
        kind = IntroKind.QUEST,
        who = who,
        place = StagePlace.of(run, who),
        emoji = c.emoji,
        title = c.title.removePrefix("${c.emoji} "),
        request = ask ?: request(who, run),
        story = notes(c.intro, skip = ask?.let { "${it.sl} · ${it.en}" }),
        practice = practice(c.exercises, c.skills, passMark = c.passMark),
        pays = Pays(reward = reward, answers = answers(c.exercises, c.skills), bond = if (who.id != null) bond else 0, stake = stake),
    )

    /** The event's defence; [kind] is the running event's. [bond]: friendship points a session still brings today. */
    fun event(kind: EventKind, c: Challenge, who: StagePerson, bond: Int): IntroInfo {
        val run = Run.Event(kind)
        return IntroInfo(
            kind = IntroKind.EVENT,
            who = who,
            place = StagePlace.of(run, who),
            emoji = c.emoji,
            title = c.title.removePrefix("${c.emoji} "),
            request = request(who, run),
            story = notes(c.intro),
            practice = practice(c.exercises, c.skills, timeLimit = c.timeLimitSeconds, passMark = c.passMark),
            pays = Pays(answers = answers(c.exercises, c.skills), bond = bond, stake = stake(kind)),
            warning = bi("introInfo.afterFirstAnswerAnswers", "LEAVE_WARNING" to LEAVE_WARNING),
            note = if (c.timeLimitSeconds != null) bi("introInfo.rightAnswersMoveBy") else null,
            start = "⚔️  ${bi("introInfo.start")}",
            timed = c.timeLimitSeconds?.let { timed(c.exercises.size, it) },
        )
    }

    /**
     * A timed run announced: "⏱ Izziv na čas: 8 vprašanj, 2 minuti · A timed challenge: 8 questions, 2 minutes" (the
     * time in half minutes, as [si.lanisce.lani.game.RunTime.limit] gives it), and under it how the clock goes: it starts
     * after 3, 2, 1 and waits while a sound loads or plays.
     */
    fun timed(questions: Int, seconds: Int): Timed {
        val time = Localized { lang -> duration(lang, seconds) }
        return Timed("⏱ ${bi("intro.timed", "n" to questions, "time" to time)}", bi("intro.clockWaits"))
    }

    /** [seconds] in whole and half minutes, in [lang]: "2 minuti in pol", "2½ minutes"; less than a minute, "pol minute". */
    fun duration(lang: Lang, seconds: Int): String {
        val m = seconds / 60
        val half = seconds % 60 >= 30
        return if (m == 0) L10n.text(lang, "intro.halfMinute") else L10n.text(lang, "intro.minutes", mapOf("m" to m, "half" to if (half) "yes" else "no"))
    }

    fun gather(res: Res, c: Challenge, who: StagePerson, bond: Int): IntroInfo {
        val run = Run.Gather(res)
        return IntroInfo(
            kind = IntroKind.GATHER,
            who = who,
            place = StagePlace.of(run, who),
            emoji = c.emoji,
            title = c.title.removePrefix("${c.emoji} "),
            request = request(who, run),
            story = notes(c.intro),
            practice = practice(c.exercises, c.skills),
            pays = Pays(answers = answers(c.exercises, c.skills), bond = bond),
        )
    }

    /**
     * A module; [quest] is its village quest (tutor quests pay on completion; open, its pass mark and how close Jan is),
     * [meta] the module's quest block when the village hasn't made a quest of it (yet), [why] the quest waits in "Later".
     */
    fun module(m: Module, quest: Quest?, meta: QuestMeta?, who: StagePerson, bond: Int, why: QuestQueue.Why? = null): IntroInfo {
        // their request only when they're the one on stage: someone who hasn't moved in yet leaves it to who's here
        val giver = (quest?.giver ?: meta?.giver)?.takeIf { it.equals(who.name, ignoreCase = true) }
        val run = Run.Module(giver, if (giver != null) quest?.emoji ?: meta?.emoji else null)
        val open = quest != null && !quest.done
        val story = if (giver != null) quest?.story ?: meta?.story else null
        return IntroInfo(
            kind = IntroKind.MODULE,
            who = who,
            place = StagePlace.of(run, who),
            emoji = run.emoji ?: "📘",
            title = m.title,
            request = request(who, run),
            story = listOfNotNull(story?.let(::split) ?: m.description?.let(::english)),
            // a request of the tutor's is passed like any (GameController.rewardModule): its mark said before Start
            practice = practice(m.exercises, null, passMark = if (open) si.lanisce.lani.game.Quests.passMark(m.exercises.size) else 0),
            pays = Pays(
                reward = if (open) quest!!.reward else emptyMap(),
                answers = answers(m.exercises, null),
                bond = if (open && who.id != null) Bonds.QUEST else bond,
            ),
            progress = quest?.takeIf { open }?.let(::progressText),
            waits = why?.takeIf { open }?.let(::whyText),
        )
    }

    /** A word pack session: meet [words], then practise [exercises]. */
    fun pack(p: Pack, words: List<PackWord>, exercises: List<Exercise>, who: StagePerson, bond: Int): IntroInfo {
        val run = Run.Pack(p.giver?.name, p.giver?.emoji)
        val base = practice(exercises, null)
        return IntroInfo(
            kind = IntroKind.PACK,
            who = who,
            place = StagePlace.of(run, who),
            emoji = p.emoji,
            title = p.title,
            request = request(who, run),
            story = listOfNotNull(p.description?.let(::english)),
            practice = base.copy(words = words.size, minutes = base.minutes + ceil(words.size * WORD_SECONDS / 60.0).toInt()),
            pays = Pays(answers = answers(exercises, null), bond = bond),
        )
    }

    /** A role-play with the tutor playing [s]'s character ([who]). */
    fun talk(s: Scenario, who: StagePerson): IntroInfo = IntroInfo(
        kind = IntroKind.TALK,
        who = who,
        place = StagePlace.of(Run.Talk(s), who),
        emoji = s.emoji,
        title = s.title,
        request = firstSentence(s.openerSl, s.openerEn),
        story = listOf(english(s.setting)),
        practice = Practice(listOf(Res.WISDOM), count = 0, minutes = TALK_MINUTES, goals = s.goals),
        pays = Pays(perLine = Res.WISDOM, bond = if (who.id != null) Bonds.TALK else 0),
    )

    /** Their request: the warmest greeting their level allows at [time] (not in an emergency), then the ask. */
    fun request(who: StagePerson, run: Run, time: TimeOfDay = TimeOfDay.now()): Said {
        val ask = Lines.ask(run, who.register)
        val greet = Lines.warmest(who.lines.greet.at(time), who.level)
            ?.takeIf { run !is Run.Event && run !is Run.Talk && it.target.length + ask.target.length <= MAX_REQUEST }
        // A greeting's translation may explain itself ("Day. (short for dober dan)"); in a request it just greets.
        val greetEn = greet?.base?.replace(PARENTHESES, "")?.trim()
        return if (greet == null) ask.said() else Said("${greet.target} ${ask.target}", "$greetEn ${ask.base}".trim())
    }

    private val PARENTHESES = Regex("\\s*\\([^)]*\\)")

    /** "Slovene · English" → both halves (or all of it as Slovene when it has no " · "). */
    fun split(text: String): Said {
        val i = text.indexOf(" · ")
        return if (i < 0) Said(text.trim()) else Said(text.substring(0, i).trim(), text.substring(i + 3).trim())
    }

    /** A text that is usually English only (a pack's description, a scenario's setting); bilingual when it has " · ". */
    fun english(text: String): Said = if (" · " in text) split(text) else Said("", text.trim())

    /**
     * The narration lines of a challenge's intro: what it's about and the fallback note, without the goal
     * line ("6/8 ✔ · 82 s") the intro shows by itself, and without [skip] (the quest story, shown already).
     */
    fun notes(intro: String, skip: String? = null): List<Said> =
        intro.lines().map { it.trim() }.filter { it.isNotEmpty() && '✔' !in it && it != skip?.trim() }.map(::split)

    /**
     * The skills, the kinds and the number of exercises, and about how many minutes. [skills] (a
     * challenge's) wins over each exercise's own resource.
     */
    fun practice(exercises: List<Exercise>, skills: List<Res>?, timeLimit: Int? = null, passMark: Int = 0): Practice {
        val res = exercises.indices.map { i -> skills?.getOrNull(i) ?: GameEngine.resourceOf(exercises[i]) }
        val main = res.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<Res, Int>> { it.value }.thenBy { it.key.ordinal })
            .map { it.key }
        val kinds = exercises.mapNotNull(::kindLabel).groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
        val minutes = timeLimit?.let { ceil(it / 60.0).toInt() } ?: minutes(exercises)
        return Practice(main.take(2), exercises.size, kinds, minutes.coerceAtLeast(1), timeLimit = timeLimit, passMark = passMark)
    }

    /** About how long [exercises] take, whole minutes, at least 1. */
    fun minutes(exercises: List<Exercise>): Int = ceil(exercises.sumOf(::seconds) / 60.0).toInt().coerceAtLeast(1)

    /** The most the answers bring when all are right, before the village's bonuses. */
    fun answers(exercises: List<Exercise>, skills: List<Res>?): Map<Res, Int> {
        val pay = Catalog.earnPerAnswer.getValue(Verdict.CORRECT)
        return exercises.indices.map { i -> skills?.getOrNull(i) ?: GameEngine.resourceOf(exercises[i]) }
            .groupingBy { it }.eachCount().mapValues { (_, n) -> n * pay }
    }

    /** "izbira · choice": what an exercise is, for the intro; null for one this app can't show. */
    fun kindLabel(ex: Exercise): String? = when (ex) {
        is Exercise.Flashcard -> bi("introInfo.flashcards")
        is Exercise.Choice -> if (ex.audio != null) bi("introInfo.listening") else bi("introInfo.choice")
        is Exercise.Cloze -> if (ex.audio != null) bi("introInfo.gapByEar") else bi("introInfo.fillGap")
        is Exercise.Dictation -> bi("introInfo.dictation")
        is Exercise.Reorder -> if (ex.audio != null) bi("introInfo.chipsByEar") else bi("introInfo.wordOrder")
        is Exercise.Translate -> bi("introInfo.translation")
        is Exercise.Free -> bi("introInfo.writing")
        is Exercise.Multi -> bi("introInfo.pickAll")
        is Exercise.Scenario -> bi("introInfo.scene")
        is Exercise.Speak -> bi("introInfo.speaking")
        is Exercise.Unsupported -> null
    }

    /** About how long one exercise takes, in seconds. */
    fun seconds(ex: Exercise): Int = si.lanisce.lani.game.RunTime.seconds(ex)

    /** What an event puts at stake, beyond the answers' pay. */
    fun stake(kind: EventKind): String = when (kind) {
        EventKind.WOLVES, EventKind.BEAR, EventKind.STORM ->
            bi("introInfo.winProtectVillageEarn")
        EventKind.MERCHANT -> bi("introInfo.goodTradeBringsWhat")
        EventKind.FESTIVAL -> bi("introInfo.merryFeastLiftsMorale")
    }

    /**
     * The start of a role-play's opener, both languages: its first sentence, or the first two when the
     * first is only a greeting ("Dober dan!").
     */
    fun firstSentence(sl: String, en: String): Said {
        val n = if (take(sl, 1).length < SHORT_SENTENCE) 2 else 1
        return Said(take(sl, n), take(en, n))
    }

    private val SENTENCE_END = Regex("[.!?…]+(\\s|$)")

    private fun take(text: String, n: Int): String {
        val t = text.trim()
        val end = SENTENCE_END.findAll(t).drop(n - 1).firstOrNull() ?: return t
        return t.substring(0, end.range.last + 1).trim()
    }

    private const val SHORT_SENTENCE = 20
    private const val WORD_SECONDS = 20.0
    private const val TALK_MINUTES = 10
    private const val MAX_REQUEST = 64
}

/** A line as [Said]: what is said, and its translation, in the learner's pair. */
fun VillagerLine.said() = Said(target, base)
