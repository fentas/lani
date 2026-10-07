package si.lanisce.lani.app

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.data.PackSession
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.data.Writes
import si.lanisce.lani.game.DialogReviews
import si.lanisce.lani.game.DialogWords
import si.lanisce.lani.game.PlayReviews
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.MyWord
import si.lanisce.lani.game.MyWords
import si.lanisce.lani.game.TurnWords
import si.lanisce.lani.game.WordTest
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogVariants
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.ui.scene.DialogRun
import java.time.LocalDate

/**
 * The learner's own words in the dialogs (companion/SCENES.md, "Your words in the dialogs"; companion/GAME.md, "Your words
 * in the dialogs"): which turns of a dialog test them ([words], when it starts), what each first answer on one does to its
 * card ([answered]: [DialogReviews], words met in play as «Vidim, vidim»'s are, a card's schedule changed once a day: the
 * village state's record, [PlayReviews]), and the review the dialog sends at its end or when it is left ([finish]: POST
 * /reviews through the outbox, one per dialog). Only the home language's words (the review deck's), in a dialog of the home
 * language; a visit's dialogs are as they were.
 */
class DialogWordsController(
    private val content: ContentController,
    private val forms: FormsController,
    private val sync: SyncController,
    private val game: GameController,
    /** The home language (the book's): the review deck's words are in it. */
    private val home: () -> String,
) {

    /** What each dialog played counted on the learner's words so far ([idOf]): its summary at the end. */
    val outcomes = mutableStateMapOf<String, List<DialogReviews.Outcome>>()

    /** What a dialog still has to send ([finish]): where it was, when its first answer came, and its outcomes. */
    private class Pending(val talk: SceneTalk, val since: Long, val outcomes: List<DialogReviews.Outcome>)
    private val pending = HashMap<String, Pending>()

    // --- the learner's words, as the dialogs meet them ----------------------------------------------------------------

    private var index: Triple<Dashboard, Map<String, CachedForms>, MyWords>? = null

    /**
     * The learner's words now: every one-word vocabulary card of the home deck ([PackSession.wordKeys]: "dober dan / živjo"
     * gives živjo), each with the forms of its word the phone has (GET /forms, [FormsController]), made once per deck and
     * forms.
     */
    fun words(): MyWords {
        val d = content.dashboard ?: return MyWords.NONE
        val f = forms.cache
        index?.takeIf { it.first === d && it.second === f }?.let { return it.third }
        val words = d.pool.filter { it.kind == "vocabulary" }.flatMap { c ->
            PackSession.wordKeys(c.front).filter { ONE_WORD.matches(it) }.map { w -> myWord(c, w) }
        }
        return MyWords(words).also { index = Triple(d, f, it) }
    }

    private fun myWord(c: ReviewCard, word: String): MyWord {
        val table = forms.entriesOf(word).orEmpty().flatMap { e -> e.slots.flatMap { it.forms + it.also } }
        return MyWord(c.id, word, ReviewPlanner.meaning(c.back), (table.map { it.lowercase() } + word.lowercase()).toSet())
    }

    /**
     * The things of [scene] that are the learner's words, by their slot: what a tap turn there tests (its pack word's own
     * card, else a card of the same word).
     */
    private fun things(scene: SceneSpec?, words: MyWords): Map<String, MyWord> {
        val s = scene ?: return emptyMap()
        val pool = content.dashboard?.pool.orEmpty().associateBy { it.id }
        return s.objects.mapNotNull { o ->
            val pack = o.pack ?: s.pack
            val own = pack?.let { pool[PackSession.itemId(it, o.word)] }?.let { c -> myWord(c, PackSession.wordKey(o.sl.ifBlank { c.front })) }
            (own ?: o.sl.takeIf { it.isNotBlank() }?.let(words::of))?.let { o.slot to it }
        }.toMap()
    }

    /**
     * The turns of [dialog] (as it is played: trimmed, with the learner's traps) that test their words, for its run
     * ([DialogRun.start]); none in another language than the home one (a visit's).
     */
    fun words(dialog: Dialog, language: String, scene: SceneSpec? = null): Map<Int, TurnWords> {
        if (language != home()) return emptyMap()
        val words = words()
        if (words.isEmpty) return emptyMap()
        return DialogWords.of(dialog, words, things(scene, words))
    }

    /**
     * How many of the learner's words the dialog of happening [a] plays today tests ("📇 3 tvoje besede" in the scroll's
     * "Today"); 0 for a story (its telling depends on the evening) and in another language.
     */
    fun inHappening(a: ActiveHappening, state: GameState, today: LocalDate = LocalDate.now()): Int {
        if (a.happening.stories || a.scene.language != home()) return 0
        val (_, d) = DialogVariants.play(a, state, today) ?: return 0
        return DialogWords.cards(words(d, a.scene.language, a.scene)).size
    }

    // --- answers -----------------------------------------------------------------------------------------------------

    /**
     * An answer at a turn of [talk]'s dialog ([run] before it, [next] after): its new answers on the learner's words
     * ([DialogRun.wordsOf]) settled on their cards ([DialogReviews.settle]): the village state records the cards whose
     * schedule it changes today ([GameController.playReviewed]: once a day, «Vidim, vidim» too), a review counts on the
     * phone at once ([ContentController.reviewedInDialog]), and all go to the node with the dialog's others ([finish]).
     */
    fun answered(talk: SceneTalk, run: DialogRun, next: DialogRun?) {
        val new = run.wordsOf(next)
        if (new.isEmpty()) return
        val today = LocalDate.now()
        val cards = content.dashboard?.pool.orEmpty().associateBy { it.id }
        val id = idOf(talk)
        val before = outcomes[id].orEmpty()
        val out = DialogReviews.settle(new, cards::get, PlayReviews.counted(game.state, today), today, before.map { it.answer.card.id }.toSet())
        if (out.isEmpty()) return
        outcomes[id] = before + out
        val was = pending[id]
        pending[id] = Pending(talk, was?.since ?: ScreenClock.app.now(), was?.outcomes.orEmpty() + out)
        out.filter { it.changes }.map { it.answer.card.id }.takeIf { it.isNotEmpty() }?.let(game::playReviewed)
        out.filter { it.kind == DialogReviews.Kind.REVIEW }.map { it.answer.card.id }.takeIf { it.isNotEmpty() }?.let { content.reviewedInDialog(it, today) }
    }

    /** What [talk]'s dialog, as played this time, counted on the learner's words so far ([answered]). */
    fun outcomesOf(talk: SceneTalk): List<DialogReviews.Outcome> = outcomes[idOf(talk)].orEmpty()

    /**
     * [talk]'s dialog ended or was left: what it counted goes to the node, one review (POST /reviews with the dialog's
     * words: [Writes.reviews]). A bridge that doesn't take a dialog's words ([Dashboard.features] without "dialog-words")
     * gets the reviews alone, as a review's, and nothing when there are none: a slip changes nothing there.
     */
    fun finish(talk: SceneTalk) {
        val p = pending.remove(idOf(talk)) ?: return
        val sent = p.outcomes.filter { it.kind != DialogReviews.Kind.AGAIN }
        if (sent.isEmpty()) return
        val results = sent.filter { it.kind == DialogReviews.Kind.REVIEW }.map { it.answer.card.id to (it.quality ?: 4) }
        val takes = content.dashboard?.features?.contains(FEATURE) == true
        if (results.isEmpty() && !takes) return
        val minutes = (((ScreenClock.app.now() - p.since) / 60_000).toInt() + 1).coerceIn(1, 240)
        sync.submitLater(Writes.reviews(results, minutes, dialog = body(p.talk, sent)))
    }

    companion object {
        /** A dialog as played this time: its talk's key, and its run's seed (a new one each time it starts). */
        fun idOf(talk: SceneTalk): String = "${talk.key}#${talk.run.seed}"

        /** What a bridge that takes a dialog's words says in GET /state's `features`. */
        const val FEATURE = "dialog-words"

        private val ONE_WORD = Regex("""\p{L}+""")

        /**
         * POST /reviews' `dialog`: where it was (the scene, the happening, the dialog, who, its title) and each word's answer
         * (`right`, `how`: meaning or form, `review`: counted as one, what was said and expected).
         */
        fun body(talk: SceneTalk, outcomes: List<DialogReviews.Outcome>): JsonObject = buildJsonObject {
            put("scene", talk.sceneId)
            put("happening", talk.happening.id)
            put("dialog", talk.dialog.id)
            put("person", talk.person.name)
            put("title", talk.happening.title.take(120))
            put("words", buildJsonArray {
                for (o in outcomes) add(buildJsonObject {
                    val a = o.answer
                    put("item_id", a.card.id)
                    put("word", a.word.said)
                    put("lemma", a.card.word)
                    put("right", a.right)
                    put("how", if (a.how == WordTest.FORM) "form" else "meaning")
                    put("review", o.kind == DialogReviews.Kind.REVIEW)
                    if (!a.right) put("said", a.said.take(160))
                    put("expected", a.expected.take(160))
                })
            })
        }
    }
}
