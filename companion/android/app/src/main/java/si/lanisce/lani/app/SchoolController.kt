package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.Course
import si.lanisce.lani.data.CourseUnit
import si.lanisce.lani.data.Courses
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ItemMark
import si.lanisce.lani.data.Sheets
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.CourseGate
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.School
import si.lanisce.lani.game.culture.localized
import si.lanisce.lani.game.villagers.Mentions
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.game.Reward
import java.time.LocalDate

/** What a sheet of the school is: one of the unit's worksheets, its test, or the tutor's homework. */
enum class SheetKind { WORKSHEET, TEST, HOMEWORK }

/**
 * One exercise of a sheet answered, as the run reports it: its verdict, what was answered, each item's mark, and what was
 * looked up on the cheat sheet during it ([lookedUp]; each item says whether it was answered after: [ItemMark.lookedUp]).
 */
data class SheetAnswer(
    val verdict: Verdict, val paid: Verdict, val answer: String, val expected: String?, val items: List<ItemMark> = emptyList(),
    val lookedUp: si.lanisce.lani.game.LookUp? = null,
)

/** A sheet finished: what it paid ([reward]), its items ([items], paid verdicts), and whether the unit was done now. */
data class SheetDone(val reward: Reward?, val items: List<Verdict>, val unitDone: Boolean, val passed: Boolean)

/**
 * The school (companion/GAME.md, "The school"; companion/COURSE.md): the course of the village's language (the app's
 * bundled copy, then the bridge's with the tutor's homework), the classroom under the linden, the levels and the unit
 * suggested next, what a sheet pays and leaves in the progress, the forms it practised, and the tutor told.
 */
class SchoolController(
    private val bridge: () -> Bridge?,
    private val game: GameController,
    private val content: ContentController,
    private val grammar: GrammarController,
    private val cheat: CheatSheetController,
    private val chat: ChatController,
    private val forms: FormsController,
    private val cast: () -> List<Villager>,
    /** A unit's intro ended for the first time (finished or skipped): what it turns on says so (counting cards). */
    private val onIntroDone: (CourseUnit) -> Unit = {},
) {
    /** The course's language: the learner's own village's (as the grammar book's). */
    val language: String get() = grammar.language

    private var bundledFor: String? = null
    private var bundled: Course? = null
    private var fromBridge: Course? = null

    /** The course of the village's language; null when it has none (no classroom then). */
    var course by mutableStateOf<Course?>(null)
        private set

    init {
        refreshBundled()
    }

    /** The bundled course of the village's language, read again when the language or the learner's base changes (its texts per base). */
    private fun refreshBundled() {
        val l = language
        val key = "$l|${L10n.pair.base.code}"
        if (key == bundledFor) return
        val sameLanguage = bundledFor?.substringBefore('|') == l
        bundledFor = key
        bundled = Courses.bundled(l)
        // the bridge's course of the same language stays (it is in the learner's base already, the bridge's own)
        if (!sameLanguage) fromBridge = null
        course = fromBridge ?: bundled
    }

    /** The bridge's course (the tutor's homework in it); an older bridge, a language without one, or none now keeps the app's. */
    suspend fun reload(b: Bridge) {
        refreshBundled()
        val got = try {
            b.course()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        fromBridge = got?.takeIf { it.language == language }
        course = fromBridge ?: bundled
    }

    fun unit(id: String): CourseUnit? = course?.units?.get(id)

    /** The school's person (VILLAGERS.md, "A building brings its person"): who teaches, under the linden or indoors. */
    fun teacher(): Villager? = Residents.brings(BuildingType.SCHOOL, cast())

    /** Whether the teacher lives in the village (the school brought them), or comes over from the next village to teach. */
    fun teacherLives(s: GameState): Boolean = teacher()?.let { t -> s.residents.any { it.id == t.id } } == true

    // --- the class, the levels -------------------------------------------------------------------------------------

    /**
     * The class begun (the village a hamlet) and the levels opened since they were last kept (a school built or upgraded,
     * A1's units done): kept, and said in the chronicle, who comes to teach by name; a cheer when the class begins now.
     * Called as the village changes (an age, a building) and as the school opens.
     */
    fun touch() {
        val c = course ?: return
        val before = game.state?.course?.classroom
        val t = teacher()
        val target = L10n.pair.target
        val name = t?.let { v -> localized { lang -> if (lang == target) v.name else Mentions.personal(v) } }
        game.apply { School.opened(it, c, LocalDate.now(), System.currentTimeMillis(), name) }
        if (before == null && game.state?.course?.classroom != null) game.cheer()
    }

    /** What upgrading [buildingId] asks of the course (the school's next level: a course level done); null for any other. */
    fun gate(s: GameState, buildingId: String): CourseGate? = School.gate(s, course, buildingId)

    // --- the units --------------------------------------------------------------------------------------------------

    /** The intro of [unit] went as far as [steps] ([done]: finished or skipped). */
    fun introProgress(unit: CourseUnit, steps: Int, done: Boolean) {
        val before = game.state?.let { School.progress(it, unit.language, unit.id)?.introDone } == true
        game.apply { School.introProgress(it, unit.language, unit.id, steps, done, LocalDate.now()) }
        // the intro through (its summary reached, or skipped: "Poznam to"): the unit's pages are met, in the book and their
        // cards on the cheat sheet
        if (done || steps >= (unit.intro?.steps?.size ?: 0)) cheat.met(unit.grammar)
        // done for the first time: what it turns on says so (after the pages' news)
        if (!before && game.state?.let { School.progress(it, unit.language, unit.id)?.introDone } == true) onIntroDone(unit)
    }

    /**
     * A sheet ([kind], [sheet]'s id; "test" for the test) of [unit] was played: [answers] to its [exercises] (fewer when it
     * was left early). Every item pays as an answer (the school's types are grammar: 🪨), the forms it practised go to the
     * learner's words, and when it was played to its end its progress is kept (the test passed: the unit done, its bonus)
     * and the tutor hears of it (a session_end with `course`). Null when the village isn't loaded.
     */
    fun finish(unit: CourseUnit, kind: SheetKind, sheet: String, exercises: List<Exercise>, answers: List<SheetAnswer>, minutes: Int): SheetDone? {
        val all = answers.size >= exercises.size
        val items = answers.flatMap { a -> if (a.items.isNotEmpty()) a.items.map { it.paid } else listOf(a.paid) }
        val pairs = answers.withIndex().flatMap { (i, a) ->
            val res = exercises.getOrNull(i)?.let(GameEngine::resourceOf) ?: Res.STONE
            if (a.items.isNotEmpty()) a.items.map { res to it.paid } else listOf(res to a.paid)
        }
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val passed = kind == SheetKind.TEST && all && Sheets.passed(items, unit.test.pass)
        val done = game.finishSheet(
            pairs, items.count { it == Verdict.CORRECT }, items.count { it == Verdict.ALMOST },
            record = { s ->
                when {
                    !all -> s to false
                    kind == SheetKind.TEST -> {
                        // the unit done may open the next level (A1's last unit, with the school standing)
                        val (n, d) = School.testDone(s, unit, items, today, now)
                        (course?.let { c -> School.opened(n, c, today, now) } ?: n) to d
                    }
                    else -> School.sheetDone(s, unit.language, unit.id, sheet, items, today) to false
                }
            },
            message = "${unit.emoji} ${bi("school.unitDone", "unit" to localized { lang -> si.lanisce.lani.data.Grammar.text(unit.title, lang.code) })}",
        )
        // the forms a table or a transform list practised: the learner's words ask the wrong ones first
        val practised = answers.withIndex().flatMap { (i, a) -> exercises.getOrNull(i)?.let { Sheets.forms(Sheets.items(it), a.items) }.orEmpty() }
        forms.practised(practised, content.dashboard?.pool.orEmpty())
        if (answers.isNotEmpty()) chat.sessionEnd(summary(unit, kind, sheet, exercises, items, all, passed, done?.second == true), data(unit, kind, sheet, exercises, answers, items, minutes, all, passed, done?.second == true))
        return SheetDone(done?.first, items, done?.second == true, passed)
    }

    private fun summary(unit: CourseUnit, kind: SheetKind, sheet: String, exercises: List<Exercise>, items: List<Verdict>, all: Boolean, passed: Boolean, unitDone: Boolean): String {
        val what = when (kind) {
            SheetKind.TEST -> "the test of unit \"${si.lanisce.lani.data.Grammar.text(unit.title, "en")}\" (${unit.id}, ${unit.level})"
            SheetKind.HOMEWORK -> "homework $sheet of unit ${unit.id} (${unit.level})"
            SheetKind.WORKSHEET -> "worksheet $sheet of unit ${unit.id} (${unit.level})"
        }
        val early = if (!all) " Left early." else ""
        val result = when {
            kind != SheetKind.TEST -> ""
            unitDone -> " Passed (${unit.test.pass} % to pass): the unit is done."
            passed -> " Passed again."
            all -> " Not passed yet (${unit.test.pass} % to pass)."
            else -> ""
        }
        return "Did $what in the school: ${Sheets.scoreText(items)} items right.$result$early"
    }

    private fun data(
        unit: CourseUnit, kind: SheetKind, sheet: String, exercises: List<Exercise>, answers: List<SheetAnswer>, items: List<Verdict>, minutes: Int,
        all: Boolean, passed: Boolean, unitDone: Boolean,
    ): JsonObject = buildJsonObject {
        put("course", buildJsonObject {
            put("language", unit.language)
            put("unit", unit.id)
            put("level", unit.level)
            put("sheet", if (kind == SheetKind.TEST) "test" else sheet)
            put("kind", kind.name.lowercase())
            put("right", items.count { it == Verdict.CORRECT })
            put("almost", items.count { it == Verdict.ALMOST })
            put("of", items.size)
            if (!all) put("left_early", true)
            if (kind == SheetKind.TEST) put("passed", passed)
            if (unitDone) put("unit_done", true)
            if (unitDone && game.state?.let { School.progress(it, unit.language, unit.id)?.testedOut } == true) put("tested_out", true)
        })
        put("duration_minutes", minutes)
        put("results", buildJsonArray {
            answers.forEachIndexed { i, a ->
                add(buildJsonObject {
                    put("index", i)
                    put("type", exercises.getOrNull(i)?.let(::typeOf) ?: "?")
                    put("verdict", a.verdict.name.lowercase())
                    put("answer", a.answer.take(300))
                    a.expected?.let { put("expected", it.take(300)) }
                    a.lookedUp?.let { put("looked_up", it.json) }
                    if (a.items.isNotEmpty()) put("items", buildJsonArray {
                        val its = exercises.getOrNull(i)?.let(Sheets::items).orEmpty()
                        a.items.forEachIndexed { j, m ->
                            add(buildJsonObject {
                                put("verdict", m.verdict.name.lowercase())
                                put("answer", m.answer.take(120))
                                put("expected", m.expected.take(120))
                                its.getOrNull(j)?.let { put("in", it.frame.take(160)) }
                                if (m.hinted) put("hinted", true)
                                if (m.lookedUp) put("looked_up", true)
                            })
                        }
                    })
                })
            }
        })
    }

    /** "gaptext", "choice": an exercise's type as the JSON names it. */
    private fun typeOf(e: Exercise): String = when (e) {
        is Exercise.GapText -> "gaptext"
        is Exercise.Table -> "table"
        is Exercise.Transform -> "transform"
        is Exercise.Sort -> "sort"
        is Exercise.Unsupported -> e.kind
        else -> e::class.simpleName?.lowercase() ?: "?"
    }

    // --- printing (companion/COURSE.md, "Printed sheets") -------------------------------------------------------------

    /** The sheet or booklet being got ready for the print dialog (one at a time), and the last that couldn't be, with why. */
    var printing by mutableStateOf<si.lanisce.lani.data.PrintJob?>(null)
    var printTrouble by mutableStateOf<Pair<si.lanisce.lani.data.PrintJob, String>?>(null)

    /** [job]'s page from the bridge, in the learner's base language (ui/school/SchoolPrint prints it). */
    suspend fun printPage(job: si.lanisce.lani.data.PrintJob): String =
        (bridge() ?: throw java.io.IOException("not connected")).printPage(si.lanisce.lani.data.Prints.path(job, L10n.pair.base.code))

    // --- asking the tutor -------------------------------------------------------------------------------------------

    /** "💬 Vprašaj · Ask the tutor" about [unit] (the sheet [sheet] when on one): the chat opens with it attached. */
    fun ask(unit: CourseUnit, sheet: String? = null) {
        val s = game.state
        val p = s?.let { School.progress(it, unit.language, unit.id) }
        chat.askAbout(ChatContext("📚 ${unit.titleShown(L10n.pair)}", buildJsonObject {
            put("unit", unit.id)
            put("level", unit.level)
            put("title", si.lanisce.lani.data.Grammar.text(unit.title, "en"))
            put("grammar", buildJsonArray { unit.grammar.forEach { add(JsonPrimitive(it)) } })
            sheet?.let { put("sheet", it) }
            p?.let {
                put("intro_steps", it.intro)
                put("sheets_done", buildJsonArray { it.sheets.forEach { (id, b) -> add(buildJsonObject { put("id", id); put("best", "${b.right}/${b.of}") }) } })
                it.test?.let { t -> put("test_best", "${t.right}/${t.of}"); put("test_tries", t.tries) }
                it.done?.let { d -> put("done", d) }
            }
        }, key = "about_course"))
    }
}
