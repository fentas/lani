package si.lanisce.lani.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Course
import si.lanisce.lani.data.CourseUnit
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.FormsCard
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.NumberKind
import si.lanisce.lani.data.NumberMode
import si.lanisce.lani.data.NumberRange
import si.lanisce.lani.data.NumberTongue
import si.lanisce.lani.data.NumberTask
import si.lanisce.lani.data.NumbersPractice
import si.lanisce.lani.data.NumbersProgress
import si.lanisce.lani.game.GameState
import si.lanisce.lani.ui.Outcome
import java.time.LocalDate
import kotlin.random.Random

/** A numbers run done: the range it opened (null: none), and the numbers missed that come back (as shown: "27", "15. maj"). */
data class NumbersDone(val opened: NumberRange?, val missed: List<String>, val verdicts: List<Verdict>)

/**
 * "🔢 Števila · Numbers" (data/NumbersPractice.kt; companion/README.md "Numbers") in the village's language ([tongue]: none
 * where the module doesn't speak it yet): whether it is open (its unit's intro: stevila, numeri), the ways of asking on now
 * (a voice, something to listen, the pages a date and a clock need), a run planned, its answers kept in the village state
 * (the ranges, the numbers missed), paid as practice and told the tutor (a session_end with `numbers`).
 */
class NumbersController(
    private val scope: CoroutineScope,
    private val game: GameController,
    private val chat: ChatController,
    private val notices: Notices,
    private val course: () -> Course?,
    private val language: () -> String?,
    private val canSay: () -> Boolean,
    private val canListen: () -> Boolean,
) {
    /** The numbers of the village's language; null where the module doesn't speak it yet. */
    val tongue: NumberTongue? get() = NumberTongue.of(language())

    /**
     * The intro of [unit] ended for the first time: the unit stevila's (numeri's) opens the module, the unit kdaj-cas's
     * (ore-giorni's) its clock times; said once, after the news on screen (the pages met, the counting cards' note):
     * "🔢 Števila so odprta …".
     */
    fun introDone(unit: CourseUnit) {
        val t = NumberTongue.of(unit.language) ?: return
        val key = when {
            unit.id == t.unit -> "numbers.on"
            unit.id == t.timeUnit && NumbersPractice.open(state, t) -> "numbers.timeOn"
            else -> return
        }
        val note = "${if (key == "numbers.on") "🔢" else "⏰"} ${bi(key)}"
        scope.launch {
            withTimeoutOrNull(NEWS_WAIT_MS) { while (notices.banner != null) delay(200) }
            // the counting cards' note may come right after the pages' news: after it too
            delay(1_500)
            withTimeoutOrNull(NEWS_WAIT_MS) { while (notices.banner != null) delay(200) }
            notices.banner = note
        }
    }

    val state: GameState? get() = game.state

    val progress: NumbersProgress? get() = state?.numbers

    /** Open: its unit's intro was done (never in a language the module doesn't speak). */
    val open: Boolean get() = tongue?.let { NumbersPractice.open(state, it) } == true

    /** The grammar pages introduced (a date and a clock time need theirs: datumi, kdaj-cas; ore-giorni). */
    fun introduced(): Set<String> = tongue?.let { FormsCard.introduced(state, course(), it.lang.code) }.orEmpty()

    /** The ways of asking on now. */
    fun modes(): Set<NumberMode> = tongue?.let { NumbersPractice.modes(canSay(), canListen(), introduced(), it) }.orEmpty()

    /** The numbers missed due today. */
    fun due(today: LocalDate = LocalDate.now()): Int = if (open) NumbersPractice.due(progress, today).size else 0

    /** A run's tasks now (QA's "numbers:only:<mode>" asks every task one way). */
    fun plan(today: LocalDate = LocalDate.now(), random: Random = Random(System.currentTimeMillis())): List<NumberTask> =
        NumbersPractice.plan(progress, open, modes(), today, random, only = QaHooks.numbersOnly)

    /**
     * A run played: [outs] the answers to [exercises] (fewer when it was left early). Kept in the village (the ranges'
     * answers, the numbers missed, a range opened), paid as practice, and told the tutor with each wrong answer's kind.
     */
    fun finish(tasks: List<NumberTask>, exercises: List<Exercise>, outs: List<Outcome>, minutes: Int, today: LocalDate = LocalDate.now()): NumbersDone {
        val tongue = tongue ?: NumberTongue.SL
        val verdicts = outs.map { it.verdict }
        val answers = outs.mapIndexed { i, o -> o.answer to kindOf(exercises.getOrNull(i), tasks.getOrNull(i), o) }
        val modes = modes()
        val before = progress
        val recorded = NumbersPractice.record(before, open, tasks, verdicts, today)
        game.apply { it.copy(numbers = recorded.progress) }
        if (outs.isNotEmpty()) {
            game.rewardPractice(exercises.take(outs.size), outs.map { it.paid })
            chat.sessionEnd(summary(tasks, answers, verdicts, recorded.opened), data(recorded, tasks, exercises, outs, answers, verdicts, modes, minutes, today))
        }
        val missed = tasks.zip(verdicts).filter { (_, v) -> v == Verdict.WRONG }.map { (t, _) -> NumbersPractice.shownOf(t, tongue) }
        return NumbersDone(recorded.opened, missed, verdicts)
    }

    private companion object {
        /** The longest the note waits for the news on screen before it. */
        const val NEWS_WAIT_MS = 12_000L
    }

    /** What a wrong answer was wrong about: by the numbers' grading again (pure), or for a said one by what was heard. */
    private fun kindOf(ex: Exercise?, t: NumberTask?, o: Outcome): NumberKind? {
        val tongue = tongue ?: NumberTongue.SL
        if (o.verdict != Verdict.WRONG || t == null) return null
        return when (ex) {
            is Exercise.Numeral -> NumbersPractice.grade(ex, o.answer).kind
            is Exercise.Speak -> NumbersPractice.saidKind(t.n, o.answer, tongue)
            else -> null
        }
    }

    private fun summary(tasks: List<NumberTask>, answers: List<Pair<String, NumberKind?>>, verdicts: List<Verdict>, opened: NumberRange?): String {
        val right = verdicts.count { it == Verdict.CORRECT }
        val almost = verdicts.count { it == Verdict.ALMOST }
        val wrong = verdicts.indices.filter { verdicts[it] == Verdict.WRONG }.take(6).joinToString("; ") { i ->
            "${tasks[i].mode.wire} ${NumbersPractice.shownOf(tasks[i], tongue ?: NumberTongue.SL)} answered \"${answers[i].first.take(40)}\"${answers[i].second?.let { " (${it.id})" }.orEmpty()}"
        }
        val early = if (verdicts.size < tasks.size) " Left early after ${verdicts.size} of ${tasks.size}." else ""
        return "Practised the numbers (🔢 Števila · Numbers) in the app: $right/${verdicts.size} right${if (almost > 0) ", $almost almost" else ""}." +
            (opened?.let { " The range ${it.label} opened now." }.orEmpty()) + (if (wrong.isNotEmpty()) " Wrong: $wrong." else "") + early
    }

    private fun data(
        recorded: si.lanisce.lani.data.NumbersRecorded, tasks: List<NumberTask>, exercises: List<Exercise>, outs: List<Outcome>,
        answers: List<Pair<String, NumberKind?>>, verdicts: List<Verdict>, modes: Set<NumberMode>, minutes: Int, today: LocalDate,
    ): JsonObject = buildJsonObject {
        put("numbers", NumbersPractice.wire(recorded, open, tasks, exercises, answers, verdicts, modes, today, tongue ?: NumberTongue.SL))
        put("duration_minutes", minutes)
        put("results", buildJsonArray {
            outs.forEachIndexed { i, o ->
                add(buildJsonObject {
                    put("index", i)
                    put("type", "numbers_${tasks.getOrNull(i)?.mode?.wire ?: "?"}")
                    put("verdict", o.verdict.name.lowercase())
                    put("answer", o.answer.take(80))
                    put("expected", NumbersPractice.expected(exercises.getOrNull(i)).take(80))
                    if (o.spoken) put("spoken", true)
                    if (o.hints > 0) put("hints_used", o.hints)
                    o.lookedUp?.let { put("looked_up", it.json) }
                })
            }
        })
    }
}
