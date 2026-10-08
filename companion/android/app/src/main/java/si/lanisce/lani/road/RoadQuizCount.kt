package si.lanisce.lani.road

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.OutboxEntry
import si.lanisce.lani.data.Writes
import si.lanisce.lani.game.DialogReviews
import si.lanisce.lani.game.MyWord
import si.lanisce.lani.game.PlayReviewDay
import si.lanisce.lani.game.TurnWord
import si.lanisce.lani.game.WordAnswer
import si.lanisce.lani.game.WordTest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A quiz's answer in the car: the [question] and the option [picked] (as the question had it: its words and page), whether
 * it was [right], the right answer ([rightText]), the cards the question tests as they were when getting ready, a form's
 * key, and when ([at], epoch ms).
 */
@Serializable
data class QuizAnswer(
    val question: String,
    val kind: QuizKind,
    val picked: QuizOption,
    val right: Boolean,
    val rightText: String,
    val cards: List<QuizCard> = emptyList(),
    val form: String? = null,
    val at: Long,
)

/** A rule's answer for the grammar book ([si.lanisce.lani.app.GrammarController.answered]). */
@Serializable
data class QuizRule(val page: String, val right: Boolean, val said: String, val correct: String)

/** A word's form asked, for the card's record of its forms ([si.lanisce.lani.app.FormsController]). */
@Serializable
data class QuizFormAsked(val card: String, val key: String, val right: Boolean, val repetitions: Int)

/**
 * What the car's answers leave for the app, which keeps the village state (the road's service doesn't): the rules answered
 * (the grammar book), the forms asked, the cards whose schedule the quiz changed (play's once a day,
 * [si.lanisce.lani.game.PlayReviews.record]) and those it reviewed (done for today on the phone), all on [day].
 */
@Serializable
data class QuizHandOff(
    val day: String,
    val rules: List<QuizRule> = emptyList(),
    val forms: List<QuizFormAsked> = emptyList(),
    val changed: List<String> = emptyList(),
    val reviewed: List<String> = emptyList(),
)

/** The cards the quiz answered on [on] (the first answer of the day on a card decides) and those whose schedule it changed. */
@Serializable
data class QuizDay(val on: String = "", val answered: List<String> = emptyList(), val changed: List<String> = emptyList())

/**
 * How the car's quiz answers count (companion/GAME.md, "Words met in play"), as a dialog's do: a right answer on one of the
 * learner's words is a word met in play ([DialogReviews.settle]: a review of quality 4 when the card is due or nearly and
 * wasn't reviewed today, once a day); a wrong answer about its meaning lowers its card gently (a bridge that takes a
 * dialog's words); a wrong form leaves the card as it is; a rule counts on the grammar book as a dialog's pick does. Pure.
 */
object RoadQuizCount {
    /** The bridge takes at most this many words in one dialog body (POST /reviews `dialog.words`). */
    const val WORDS_PER_WRITE = 20

    /** What [answers] come to: the writes for the outbox, what the app takes, and the day's record after them. */
    data class Settled(val writes: List<OutboxEntry>, val handOff: QuizHandOff?, val day: QuizDay)

    /** The local day of [at] (epoch ms). */
    fun dayOf(at: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toString()

    /**
     * Settles [answers] given on [today]: [day] the quiz's record of today (another day's starts afresh), [played] the cards
     * play counted today when getting ready (the village state's then: only today's counts), [takesWords] whether the bridge
     * takes a dialog's words (an older one gets the reviews only, and nothing without one). [title] names the quiz for the
     * tutor.
     */
    fun settle(
        answers: List<QuizAnswer>,
        today: LocalDate,
        day: QuizDay,
        played: PlayReviewDay?,
        takesWords: Boolean,
        title: String = "🚗 Kviz · Quiz",
        id: () -> String = { si.lanisce.lani.data.Outbox.newId() },
        now: Long = System.currentTimeMillis(),
    ): Settled {
        val d = today.toString()
        val record = if (day.on == d) day else QuizDay(d)
        if (answers.isEmpty()) return Settled(emptyList(), null, record)
        val counted = record.changed.toSet() + (played?.takeIf { it.on == d }?.cards.orEmpty())
        val cards = answers.flatMap { it.cards }.associateBy { it.id }
        val words = answers.withIndex().flatMap { (i, a) ->
            a.picked.words.map { w ->
                val snap = cards[w.card]
                WordAnswer(
                    TurnWord(MyWord(w.card, snap?.word ?: w.word, "", emptySet()), w.word, 0),
                    a.right, if (w.form) WordTest.FORM else WordTest.MEANING, produced = false, turn = i, said = a.picked.text, expected = a.rightText,
                )
            }
        }
        val outcomes = DialogReviews.settle(words, { cards[it]?.card() }, counted, today, record.answered.toSet())
        val sent = outcomes.filter { it.kind != DialogReviews.Kind.AGAIN }
        val reviews = sent.filter { it.kind == DialogReviews.Kind.REVIEW }
        val minutes = (((answers.maxOf { it.at } - answers.minOf { it.at }) / 60_000L).toInt() + 1).coerceIn(1, 240)
        val forms = answers.filter { it.form != null && it.picked.page != null }
        val writes = mutableListOf<OutboxEntry>()
        if (reviews.isNotEmpty() || (takesWords && sent.isNotEmpty())) {
            sent.chunked(WORDS_PER_WRITE).forEachIndexed { n, chunk ->
                val results = chunk.filter { it.kind == DialogReviews.Kind.REVIEW }.map { it.answer.card.id to (it.quality ?: 4) }
                if (results.isEmpty() && !takesWords) return@forEachIndexed
                val formsJson = if (n == 0 && forms.isNotEmpty()) buildJsonArray {
                    for (a in forms) add(buildJsonObject {
                        put("item_id", a.cards.firstOrNull()?.id ?: a.question)
                        put("key", a.form!!)
                        put("page", a.picked.page!!)
                        put("right", a.right)
                        if (!a.right) put("answer", a.picked.text.take(160))
                        put("expected", a.rightText.take(160))
                    })
                } else null
                writes += Writes.reviews(results, minutes, forms = formsJson, id = id(), now = now, dialog = body(chunk, title))
            }
        }
        val handOff = QuizHandOff(
            d,
            rules = answers.mapNotNull { a -> a.picked.page?.let { QuizRule(it, a.right, a.picked.text, a.rightText) } },
            forms = answers.mapNotNull { a ->
                val key = a.form ?: return@mapNotNull null
                val card = a.cards.firstOrNull() ?: return@mapNotNull null
                QuizFormAsked(card.id, key, a.right, card.repetitions)
            },
            changed = outcomes.filter { it.changes }.map { it.answer.card.id }.distinct(),
            reviewed = reviews.map { it.answer.card.id }.distinct(),
        ).takeIf { it.rules.isNotEmpty() || it.forms.isNotEmpty() || it.changed.isNotEmpty() }
        val next = QuizDay(
            d,
            (record.answered + outcomes.map { it.answer.card.id }).distinct(),
            (record.changed + outcomes.filter { it.changes }.map { it.answer.card.id }).distinct(),
        )
        return Settled(writes, handOff, next)
    }

    /** POST /reviews' `dialog` for the quiz: where (the road's quiz) and each word's answer, as a dialog's. */
    fun body(outcomes: List<DialogReviews.Outcome>, title: String): JsonObject = buildJsonObject {
        put("scene", "road")
        put("happening", "quiz")
        put("title", title.take(120))
        put("words", buildJsonArray {
            for (o in outcomes) add(buildJsonObject {
                val a = o.answer
                put("item_id", a.card.id)
                put("word", a.word.said.take(80))
                put("lemma", a.card.word.take(80))
                put("right", a.right)
                put("how", if (a.how == WordTest.FORM) "form" else "meaning")
                put("review", o.kind == DialogReviews.Kind.REVIEW)
                if (!a.right) put("said", a.said.take(160))
                put("expected", a.expected.take(160))
            })
        })
    }
}
