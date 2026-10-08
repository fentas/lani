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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.LemmaForms
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.data.WordFormsWire
import si.lanisce.lani.data.json
import si.lanisce.lani.game.FormQuestion
import si.lanisce.lani.game.FormsRecord
import si.lanisce.lani.game.WordForms
import si.lanisce.lani.l10n.L10n
import java.io.File
import java.time.LocalDate

/** A lemma's forms as the phone keeps them: what GET /forms said ([entries]; none: no forms), and when ([at], ms). */
@Serializable
data class CachedForms(val lemma: String, val at: Long, val entries: List<LemmaForms> = emptyList())

/**
 * A word's forms (companion/GAME.md, "A word's forms"): the bridge's (GET /forms) for the learner's words, cached on the
 * phone for offline reviews, the form questions a review asks ([questions]), what each answer leaves for the next
 * ([answered]: the forms each card asked, kept on the phone), and the table on a word's card ([entriesOf]). An older
 * bridge without forms: none, nothing else changes.
 */
class FormsController(
    context: Context,
    private val scope: CoroutineScope,
    private val bridge: () -> Bridge?,
    private val grammar: GrammarController,
) {
    private val file = File(context.filesDir, "forms.json")
    private val askedFile = File(context.filesDir, "forms-asked.json")
    private val io = Dispatchers.IO.limitedParallelism(1)

    /** What each card asked of its forms, by "<language>/<card id>" ([WordForms.recordKey]). */
    private var asked: Map<String, FormsRecord> = emptyMap()

    /** The forms known, by [WordFormsWire.key] of the lemma. */
    var cache by mutableStateOf<Map<String, CachedForms>>(emptyMap())
        private set

    /** Lemmas being asked for now (the word card's), so a card open twice asks once. */
    private val asking = mutableSetOf<String>()

    init {
        scope.launch {
            val kept = withContext(io) { runCatching { json.decodeFromString(ListSerializer(CachedForms.serializer()), file.readText()) }.getOrNull() }
            if (kept != null) cache = kept.associateBy { WordFormsWire.key(it.lemma) } + cache
            val records = withContext(io) { runCatching { json.decodeFromString(RECORDS, askedFile.readText()) }.getOrNull() }
            if (records != null) asked = records + asked
        }
    }

    /** The cached entries of [lemma]; null when the phone hasn't asked yet. */
    fun entriesOf(lemma: String): List<LemmaForms>? = cache[WordFormsWire.key(lemma)]?.entries

    /** The entry of [lemma] a word card's entry of part of speech [pos] meaning [gloss] is about. */
    fun entryOf(lemma: String, pos: String?, gloss: List<String>): LemmaForms? {
        val all = entriesOf(lemma) ?: return null
        val same = all.filter { pos == null || it.pos == pos }.ifEmpty { all }
        return WordFormsWire.entryFor(same, gloss.joinToString(", "))
    }

    /**
     * Asks the bridge for the forms of the learner's words ([cards]: their one-word vocabulary cards) it hasn't asked about
     * in [STALE_MS], [WordFormsWire.BATCH] at a time; an older bridge (or none now) leaves the cache as it is.
     */
    suspend fun reload(b: Bridge, cards: List<ReviewCard>) {
        val now = System.currentTimeMillis()
        val wanted = cards.mapNotNull(WordFormsWire::lemmaOf).distinctBy(WordFormsWire::key)
            .filter { (cache[WordFormsWire.key(it)]?.at ?: 0L) < now - STALE_MS }
            .take(MAX_PER_RELOAD)
        fetch(b, wanted)
    }

    /** The forms of [lemmas] now, when the phone doesn't have them yet (a word card opening on one of them). */
    fun want(lemmas: List<String>) {
        val b = bridge() ?: return
        val missing = lemmas.distinctBy(WordFormsWire::key).filter { WordFormsWire.key(it) !in cache && WordFormsWire.key(it) !in asking }
        if (missing.isEmpty()) return
        asking += missing.map(WordFormsWire::key)
        scope.launch {
            try {
                fetch(b, missing)
            } finally {
                asking -= missing.map(WordFormsWire::key).toSet()
            }
        }
    }

    /** The bridge that answered it has no forms (an older one): not asked again while connected to it. */
    private var without: Bridge? = null

    private suspend fun fetch(b: Bridge, lemmas: List<String>) {
        if (lemmas.isEmpty() || b === without) return
        val got = mutableMapOf<String, CachedForms>()
        for (batch in lemmas.chunked(WordFormsWire.BATCH)) {
            val answer = try {
                b.forms(batch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                break // offline: what came is kept
            }
            if (answer == null) {
                without = b // an older bridge: no forms
                return
            }
            val now = System.currentTimeMillis()
            for (w in answer.words) got[WordFormsWire.key(w.word)] = CachedForms(w.word, now, w.entries)
        }
        if (got.isEmpty()) return
        cache = cache + got
        val all = cache.values.toList()
        withContext(io) { runCatching { write(file, json.encodeToString(ListSerializer(CachedForms.serializer()), all)) } }
    }

    /** [text] into [f], whole or not at all. */
    private fun write(f: File, text: String) {
        val tmp = File(f.path + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(f)
    }

    // --- in a review -------------------------------------------------------------------------------------------------

    /** The language the forms are of: the book's (the home language's). */
    private val language: String get() = grammar.language

    /** What [cardId] asked of its forms so far. */
    fun record(cardId: String): FormsRecord? = asked[WordForms.recordKey(language, cardId)]

    /**
     * The form question each of a review's cards asks this time, if any ([WordForms.forCard]): the home language's
     * cards only. With QA's "forms" hook every card with a reached form asks one, familiar or not.
     */
    fun questions(): (ReviewCard) -> FormQuestion? {
        val locks = grammar.formLocks()
        val base = L10n.pair.base.code
        val force = QaHooks.forms
        return { card -> WordForms.forCard(card, WordFormsWire.lemmaOf(card)?.let(::entriesOf), record(card.id), locks, base, force) }
    }

    /**
     * The form each of [cards] asks in the car's quiz ("🚗 Za pot", road/RoadQuiz): the one a review would ask next of
     * those reached ([WordForms.pick] with the card's record), always chosen among the word's own forms (the quiz is heard,
     * nothing is typed); familiar or not (the quiz takes the familiar ones). By card id; none without forms.
     */
    fun chosen(cards: List<ReviewCard>): Map<String, FormQuestion> {
        val locks = grammar.formLocks()
        val base = L10n.pair.base.code
        val open = { s: si.lanisce.lani.data.FormSlot -> locks(s).isEmpty() }
        return cards.mapNotNull { card ->
            val entries = WordFormsWire.lemmaOf(card)?.let(::entriesOf)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val lemma = WordFormsWire.entryFor(entries, card.back) ?: return@mapNotNull null
            val slot = WordForms.pick(lemma, record(card.id), open) ?: return@mapNotNull null
            WordForms.question(lemma, slot, null, ReviewPlanner.meaning(card.back), base, open)?.let { card.id to it }
        }.toMap()
    }

    /**
     * A form asked in the car's quiz (road/RoadQuizCount, handed over when the app opens): the card's record gets it as a
     * review's answer does ([answered]), so the next form is chosen as ever.
     */
    fun answeredInCar(cardId: String, key: String, right: Boolean, repetitions: Int, day: String) {
        val k = WordForms.recordKey(language, cardId)
        asked = asked + (k to WordForms.answered(asked[k], key, right, repetitions, day))
        val all = asked
        scope.launch { withContext(io) { runCatching { write(askedFile, json.encodeToString(RECORDS, all)) } } }
    }

    /**
     * A review's form answers ([tasks] with their [verdicts] and what was [answers]ed): each card's record gets the form
     * asked and whether it was right (a slip in the stem counts right: the ending is what's asked), for the next to
     * choose; the answers as POST /reviews' `forms`, for the tutor's note (null when none was a form).
     */
    fun answered(tasks: List<ReviewPlanner.Task>, verdicts: List<Verdict>, answers: List<String>): JsonArray? {
        val asked = tasks.zip(verdicts).withIndex().mapNotNull { (i, tv) -> tv.first.form?.let { Asked(tv.first.card, it, tv.second, answers.getOrNull(i).orEmpty()) } }
        if (asked.isEmpty()) return null
        val day = LocalDate.now().toString()
        this.asked = this.asked + asked.associate { a ->
            val key = WordForms.recordKey(language, a.card.id)
            key to WordForms.answered(this.asked[key], a.q.key, a.verdict != Verdict.WRONG, a.card.repetitions, day)
        }
        val all = this.asked
        scope.launch { withContext(io) { runCatching { write(askedFile, json.encodeToString(RECORDS, all)) } } }
        return buildJsonArray {
            for (a in asked) add(buildJsonObject {
                put("item_id", a.card.id)
                put("key", a.q.key)
                put("page", a.q.page)
                put("right", a.verdict != Verdict.WRONG)
                if (a.verdict == Verdict.WRONG && a.answer.isNotBlank()) put("answer", a.q.said(a.answer).take(160))
                put("expected", a.q.gap.sentence.take(160))
            })
        }
    }

    private data class Asked(val card: ReviewCard, val q: FormQuestion, val verdict: Verdict, val answer: String)

    companion object {
        private val RECORDS = MapSerializer(String.serializer(), FormsRecord.serializer())

        /** How long the phone keeps a lemma's forms before it asks again (the content's lines change now and then). */
        const val STALE_MS = 2L * 24 * 3_600_000
        /** The most lemmas one reload asks about. */
        const val MAX_PER_RELOAD = 400
    }
}
