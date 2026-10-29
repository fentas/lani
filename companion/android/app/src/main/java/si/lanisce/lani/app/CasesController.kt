package si.lanisce.lani.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.CaseAnswer
import si.lanisce.lani.data.CaseAssigned
import si.lanisce.lani.data.CaseBank
import si.lanisce.lani.data.CaseBanks
import si.lanisce.lani.data.CaseNoun
import si.lanisce.lani.data.CasePlanned
import si.lanisce.lani.data.CasePool
import si.lanisce.lani.data.CaseProgress
import si.lanisce.lani.data.CaseTrainer
import si.lanisce.lani.data.Course
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.FormsCard
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.game.Case
import si.lanisce.lani.data.MistakeNote
import si.lanisce.lani.data.WordFormsWire
import si.lanisce.lani.data.Writes
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.ui.Outcome
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/** A session done: how each task went, the items that come back (their keys), each case's stars after it. */
data class CasesDone(val verdicts: List<Verdict>, val tasks: List<si.lanisce.lani.data.CaseTask>, val missed: List<String>, val stars: Map<Case, Int?>)

/**
 * "🧩 Skloni · Cases" (data/CaseTrainer.kt; companion/README.md "Case trainer"): its bank (the bundled one, then the
 * bridge's with the tutor's items and assignment, cached for offline), what the learner may be asked (their nouns with
 * forms, the pages introduced, their level, a child's shorter sessions), a session planned, its answers kept in the village
 * state (each case's and rule's, what comes back), counted on the grammar book's pages (the run does), paid as practice and
 * persisted on the node (POST /cases/done; an older node: a session_end for the tutor). The case lens's entry point:
 * [seen].
 */
class CasesController(
    context: Context,
    private val scope: CoroutineScope,
    private val game: GameController,
    private val content: ContentController,
    private val grammar: GrammarController,
    private val forms: FormsController,
    private val sync: SyncController,
    private val notices: Notices,
    private val course: () -> Course?,
    private val child: () -> Boolean,
    private val scenes: () -> List<si.lanisce.lani.game.scene.SceneSpec>,
) {
    private val file = File(context.filesDir, "cases.json")
    private val io = Dispatchers.IO.limitedParallelism(1)

    /** The bundled bank (cases/<language>/bank.json), read off the main thread once ([load]); null until then. */
    private var bundledFor by mutableStateOf<String?>(null)
    private var bundled by mutableStateOf<CaseBank?>(null)
    private var loading: String? = null

    /** The bridge's bank (its items with the tutor's, the assignment); null before it said, from an older one, or offline at first. */
    var served by mutableStateOf<CaseBank?>(null)
        private set

    init {
        scope.launch {
            val kept = withContext(io) { runCatching { CaseBanks.parse(file.readText()) }.getOrNull() }
            if (kept != null && served == null) served = kept
        }
    }

    /** The bundled bank of the village's language read on a worker (a few hundred KB of JSON: never on the main thread). */
    private fun load(language: String) {
        if (loading == language) return
        loading = language
        scope.launch {
            val b = withContext(Dispatchers.Default) { CaseBanks.bundled(language) }
            bundled = b
            bundledFor = language
        }
    }

    /** The trainer's language: the learner's own village's, when it is one with cases (Slovene). */
    val language: String get() = grammar.language

    /** Whether the learner's village speaks a language with a trainer. */
    val available: Boolean get() = language in CaseBanks.LANGUAGES

    /** The bank merged last, and what it was merged of (by identity): the same instance while neither changed. */
    private var merged: Triple<CaseBank?, CaseBank?, CaseBank?>? = null

    /** The bank to play: the bridge's over the bundled one. */
    val bank: CaseBank?
        get() {
            if (!available) return null
            if (bundledFor != language) load(language)
            val fromBridge = served?.takeIf { it.language == language }
            val bundled = bundled.takeIf { bundledFor == language }
            merged?.let { (a, b, m) -> if (a === bundled && b === fromBridge) return m }
            return CaseBanks.merge(bundled, fromBridge).also { merged = Triple(bundled, fromBridge, it) }
        }

    /** The bridge's bank; an older bridge (404) or none now keeps what the phone has. */
    suspend fun reload(b: Bridge) {
        if (!available) return
        val got = try {
            b.caseBank()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return // offline
        } ?: return
        if (got === served) return
        served = got
        val raw = si.lanisce.lani.data.json.encodeToString(CaseBank.serializer(), got)
        withContext(io) { runCatching { file.writeText(raw) } }
    }

    /** The tutor assigned a session (or took it back), or published items: the bank again. */
    fun changed(b: Bridge?) {
        b ?: return
        scope.launch { reload(b) }
    }

    val state: GameState? get() = game.state
    val progress: CaseProgress? get() = state?.cases

    /** The tutor's assignment not played yet. */
    val assigned: CaseAssigned? get() = bank?.assigned?.takeIf { it.done == null }

    /** The learner's mistakes (mistakes-db, the dashboard's). */
    val mistakes: List<MistakeNote> get() = content.dashboard?.mistakes.orEmpty()

    /**
     * The learner's nouns with forms: their one-word vocabulary cards whose forms the phone has (GET /forms), a noun of the
     * dictionary with a gender; what each means is the card's.
     */
    fun nouns(): List<CaseNoun> {
        val cards = content.dashboard?.pool.orEmpty().filter { it.kind == "vocabulary" }
        val out = mutableListOf<CaseNoun>()
        val seen = mutableSetOf<String>()
        for (c in cards) {
            val lemma = WordFormsWire.lemmaOf(c) ?: continue
            val key = WordFormsWire.key(lemma)
            if (!seen.add(key)) continue
            val entries = forms.entriesOf(lemma)?.filter { it.pos == "noun" && it.gender != null } ?: continue
            val e = WordFormsWire.entryFor(entries, c.back) ?: continue
            val f = e.slots.associate { it.key to it.forms }
            val spoken = e.slots.filter { it.also.isNotEmpty() }.associate { it.key to it.also }
            out += CaseNoun(e.lemma, e.gender!!, si.lanisce.lani.data.ReviewPlanner.meaning(c.back), f, spoken, card = c.id)
        }
        return out
    }

    /** The last pool made and what it was made of (by identity): Home asks every time it is drawn. */
    private var memo: Pair<List<Any?>, CasePool?>? = null
    private var openMemo: Pair<CasePool?, Boolean>? = null

    /** What the trainer may ask now; null without a bank (another language, no bank bundled). */
    fun pool(): CasePool? {
        val b = bank ?: return null
        val s = state
        val inputs = listOf(b, s, content.dashboard, forms.cache, grammar.pages, course(), child(), L10n.pair, grammar.level, scenes())
        memo?.let { (k, v) -> if (k.size == inputs.size && k.indices.all { k[it] === inputs[it] }) return v }
        val introduced = FormsCard.introduced(s, course(), language)
        val pages = grammar.pages.associate { it.id to it.level }
        val open = s?.let { st -> scenes().filter { Happenings.open(it, st) }.map { it.id }.toSet() }.orEmpty()
        val p = CasePool(b, nouns(), introduced, grammar.level, child(), L10n.pair.base.code, { pages[it] }, open)
        memo = inputs to p
        return p
    }

    /** Whether there is enough to ask: the trainer opens with the first case unit's intro (or the pages met). */
    val open: Boolean
        get() {
            val p = pool()
            openMemo?.let { (k, v) -> if (k === p) return v }
            return (p?.let(CaseTrainer::isOpen) == true).also { openMemo = p to it }
        }

    /** How much the learner's mistakes weigh on page [id] (a case's or a rule's), 0 … 1: their frequency there, six counting fully. */
    fun prior(id: String): Double {
        val page = grammar.page(id) ?: return 0.0
        return Grammar.mistakes(page, mistakes).sumOf { it.frequency }.coerceAtMost(6) / 6.0
    }

    /** The items missed due today. */
    fun due(today: LocalDate = LocalDate.now()): Int = CaseTrainer.due(progress, today).size

    /** The weakest case now, for Home's suggestion (null: none weak). */
    fun weakest(): Pair<Case, Double>? = CaseTrainer.weakest(progress, ::prior)

    /** A session now, focused on [focus] (a case id, a rule id) or mixed; QA's "cases:only:<kind>" asks one kind. */
    fun plan(focus: String? = null, today: LocalDate = LocalDate.now(), random: Random = Random(System.currentTimeMillis())): List<CasePlanned> {
        val p = pool() ?: return emptyList()
        return CaseTrainer.plan(p, progress, mistakes, ::prior, today, random, focus, only = QaHooks.casesOnly)
    }

    /** A storm now ("⚡ Sklonski vihar · Case storm": phrases sorted into the six cases against the clock). */
    fun storm(today: LocalDate = LocalDate.now(), random: Random = Random(System.currentTimeMillis())): List<CasePlanned> {
        val p = pool() ?: return emptyList()
        return CaseTrainer.storm(p, progress, mistakes, ::prior, today, random)
    }

    /** The "Zakaj?" step of the exercises on screen, by their keys: right, wrong, or not asked. */
    private val why = HashMap<String, Boolean?>()

    /** An exercise's steps were answered: its deciding word tapped right ([whyRight]), wrong, or not asked (null). */
    fun marked(key: String, whyRight: Boolean?) {
        why[key] = whyRight
    }

    /**
     * A session played: [outs] the answers to [planned] (fewer when it was left early). Kept in the village (each case's
     * and rule's answers, what comes back), paid as practice, persisted on the node (an older one: told the tutor).
     */
    fun finish(planned: List<CasePlanned>, outs: List<Outcome>, minutes: Int, focus: String?, today: LocalDate = LocalDate.now(), storm: Boolean = false): CasesDone {
        val tasks = planned.map { it.task }
        val exercises = planned.map { it.exercise }
        val answers = outs.mapIndexed { i, o ->
            val ex = exercises.getOrNull(i)
            val w = ex?.let { e -> if (why.containsKey(e.key)) why[e.key] else derivedWhy(e, o.verdict) }
            CaseAnswer(o.verdict, w, o.answer, ex?.let(CaseTrainer::expectedLine).orEmpty(), lookedUp = o.lookedUp != null)
        }
        why.clear()
        val recorded = CaseTrainer.record(progress, tasks, answers, today)
        game.apply { it.copy(cases = recorded.progress) }
        val pool = pool()
        if (outs.isNotEmpty() && pool != null) {
            game.rewardPractice(exercises.take(outs.size), outs.map { it.paid })
            val body = CaseTrainer.wire(pool, recorded, tasks, exercises, answers, mistakes, minutes, focus).let { b -> if (storm) JsonObject(b + ("storm" to kotlinx.serialization.json.JsonPrimitive(true))) else b }
            sync.submitLater(Writes.casesDone(body, summary(tasks, answers, focus), sessionData(body)))
        }
        val stars = Case.entries.associateWith { CaseTrainer.stars(recorded.progress, CaseTrainer.caseKey(it)) }
        return CasesDone(answers.map { it.verdict }, tasks.take(answers.size), recorded.missed, stars)
    }

    /** Without the controller's mark (a restored screen): the why from the verdict, where it tells (almost: the why was wrong). */
    private fun derivedWhy(ex: Exercise.CaseTask, v: Verdict): Boolean? = when {
        !ex.askWhy -> null
        v == Verdict.CORRECT -> true
        v == Verdict.ALMOST -> false
        else -> null
    }

    /**
     * The case lens (the word lookup's "Sklon · Case" row) showed the learner rule [rule] (a grammar page id: the contract's
     * rule ids): counted as seen, lightly; a rule seen lately weighs a little more in the next session. Never a pass.
     */
    fun seen(rule: String) {
        if (!available || rule.isBlank()) return
        game.apply { it.copy(cases = CaseTrainer.seen(it.cases, rule, LocalDate.now())) }
    }

    /** The session for an older node's tutor: the same answers and errors, as a session_end's data. */
    private fun sessionData(body: JsonObject): JsonObject = buildJsonObject {
        put("cases", body)
        body["duration_minutes"]?.let { put("duration_minutes", it) }
        body["errors"]?.let { put("errors", it) }
    }

    private fun summary(tasks: List<si.lanisce.lani.data.CaseTask>, answers: List<CaseAnswer>, focus: String?): String {
        val right = answers.count { it.verdict == Verdict.CORRECT }
        val almost = answers.count { it.verdict == Verdict.ALMOST }
        val wrong = answers.indices.filter { answers[it].verdict == Verdict.WRONG }.take(6).joinToString("; ") { i ->
            "${tasks[i].kind.wire} ${tasks[i].case.lexicon} (${tasks[i].rule}): \"${answers[i].answer.take(40)}\" for \"${answers[i].expected.take(40)}\""
        }
        val early = if (answers.size < tasks.size) " Left early after ${answers.size} of ${tasks.size}." else ""
        return "Practised the cases (🧩 Skloni · Cases${focus?.let { ", focused on $it" }.orEmpty()}) in the app: $right/${answers.size} right" +
            (if (almost > 0) ", $almost almost (the deciding word missed)" else "") + "." + (if (wrong.isNotEmpty()) " Wrong: $wrong." else "") + early +
            " Persist it with its data.cases.errors (lani-db-updater: skills grammar, and writing for the typed ones)."
    }

}
