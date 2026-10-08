package si.lanisce.lani.road

import kotlinx.serialization.Serializable
import si.lanisce.lani.data.Drill
import si.lanisce.lani.data.DrillKind
import si.lanisce.lani.data.Drills
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading
import si.lanisce.lani.data.PackSession
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.data.WordFormsWire
import si.lanisce.lani.game.DialogWords
import si.lanisce.lani.game.FormQuestion
import si.lanisce.lani.game.Forms
import si.lanisce.lani.game.Introduction
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.game.MyWords
import si.lanisce.lani.game.PlayReviewDay
import si.lanisce.lani.game.PlayReviews
import si.lanisce.lani.game.WordTest
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.tapTurn
import java.time.LocalDate
import kotlin.random.Random

/*
 * "❓ Kviz · Quiz" in the car (companion/README.md, "Im Auto · In the car"): a question is read, then its options one at a
 * time, numbered ("Ena: …", "Dve: …"), each followed by a short gap; the steering wheel's ⏭ picks the option being read
 * (or the one just read), ⏮ repeats the question and the options, ⏯ picks too where the car sends it ([QuizControls]).
 * Nothing pressed: the options come once more, then the next question. Everything here is pure: the questions from what the
 * learner has, the session's order, a question composed for the player, the feedback.
 */

/** What a quiz question is about. */
@Serializable
enum class QuizKind {
    /** A dialog's turn: its context line, then the right answer and its wrong ones. */
    DIALOG,

    /** A card's Slovene: which meaning. */
    MEANING,

    /** A card's meaning: which Slovene word. */
    WORD,

    /** A word's form in a sentence ("Jaz ___ na klop. (sesti)"): the sentence with each form. */
    FORM,

    /** Stari Janez's riddle: which answer. */
    RIDDLE,

    /** A grammar page's choice exercise ("Nimam ___. (I don't have time.)"): the sentence with each choice. */
    GRAMMAR,
}

/**
 * A card of the learner's a question tests, as it was when getting ready: whether a right answer reviews it is decided
 * when it's given ([PlayReviews.counts]: due or nearly due, not reviewed today), from these.
 */
@Serializable
data class QuizCard(
    val id: String,
    val word: String,
    val due: String? = null,
    val interval: Int = 1,
    val last: String? = null,
    val kind: String = "vocabulary",
    val repetitions: Int = 0,
) {
    fun card(): ReviewCard = ReviewCard(
        id, word, "", kind, repetitions,
        due = due?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        interval = interval,
        lastReviewed = last?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    )

    companion object {
        fun of(c: ReviewCard) = QuizCard(c.id, c.front, c.due?.toString(), c.interval, c.lastReviewed?.toString(), c.kind, c.repetitions)
    }
}

/**
 * A word of the learner's that picking an option answers: right when the option is the right one, else got wrong; [form]:
 * its form was what the option got wrong (the card stays as it is, the rule counts it), else its meaning.
 */
@Serializable
data class QuizWord(val card: String, val word: String, val form: Boolean = false)

/**
 * An option: its [text] in the target language (or a meaning in the base, for [QuizKind.MEANING]) and how it sounds; the
 * grammar book's [page] picking it counts on (right or wrong), and the learner's [words] it answers.
 */
@Serializable
data class QuizOption(val text: String, val sound: Sound, val page: String? = null, val words: List<QuizWord> = emptyList())

/**
 * A question of the quiz: what is read first ([ask]), its [options] (the right one at [right]), what follows "Prav!" or
 * "Ne, prav je:" ([answer], the right answer whole: [answerText]), the learner's [cards] it tests, and for a word's form
 * its key ([form], "pres.1sg"). [title] is what the car's screen shows.
 */
@Serializable
data class QuizQuestion(
    val id: String,
    val kind: QuizKind,
    val title: String,
    val ask: List<Sound>,
    val options: List<QuizOption>,
    val right: Int,
    val answer: List<Sound>,
    val answerText: String,
    val cards: List<QuizCard> = emptyList(),
    val form: String? = null,
) {
    /** Every sound it may play, each once: what getting ready gets onto the phone. */
    val sounds: List<Sound> get() = (ask + options.map { it.sound } + answer).filter { it !is Sound.Pause }.distinct()
}

/**
 * The quiz's fixed phrases, each a list of alternatives, best first: the target language's clip of the voice store (or the
 * node's, or the phone's voice in the target language: [Sound.Spoken]), else the base language's prompt. The first one on
 * the phone is played ([resolve]).
 */
@Serializable
data class QuizKit(
    /** "Ena.", "Dve.", "Tri.", "Štiri.": before each option. */
    val numbers: List<List<Sound>>,
    /** "Prav!" */
    val right: List<Sound>,
    /** "Ne, prav je:" */
    val wrong: List<Sound>,
    /** "Sedem od desetih.": the summary, by the right answers of [RoadQuiz.SUMMARY_EVERY] (0 to 10). */
    val scores: List<List<Sound>>,
) {
    /** The kit as played: each phrase the first of its alternatives that [has] (on the phone); null when one has none. */
    fun resolve(has: (Sound) -> Boolean): Resolved? {
        fun one(alts: List<Sound>) = alts.firstOrNull(has)
        val n = numbers.map { one(it) ?: return null }
        return Resolved(n, one(right) ?: return null, one(wrong) ?: return null, scores.map { one(it) ?: return null })
    }

    data class Resolved(val numbers: List<Sound>, val right: Sound, val wrong: Sound, val scores: List<Sound>)

    /** Every alternative, for getting ready. */
    val sounds: List<Sound> get() = (numbers.flatten() + right + wrong + scores.flatten()).distinct()
}

/**
 * What the quiz says in the learner's base language ([whatMeans] …), and its fixed phrases: each a pair of the target
 * language's text (said by its voice) and the base language's (the phone's voice, when the first can't be had).
 */
data class QuizWords(
    val whatMeans: String = "What does it mean?",
    val howSay: (String) -> String = { "How do you say: $it?" },
    val youWant: (String) -> String = { "You want to say: $it" },
    val whatAnswer: String = "What do you answer?",
    val whichRight: String = "Which is right?",
    val numbers: List<Pair<String, String>> = listOf("Ena." to "One.", "Dve." to "Two.", "Tri." to "Three.", "Štiri." to "Four."),
    val right: Pair<String, String> = "Prav!" to "Right!",
    val wrong: Pair<String, String> = "Ne, prav je:" to "No, the right one is:",
    val score: (Int) -> Pair<String, String> = { n -> "${SL_SCORES[n]} od desetih." to "$n of ten." },
) {
    companion object {
        val EN = QuizWords()
        private val SL_SCORES = listOf("Nič", "Ena", "Dve", "Tri", "Štiri", "Pet", "Šest", "Sedem", "Osem", "Devet", "Deset")
    }
}

/** What the app gathers for the quiz, beside the road's ([RoadInputs.quiz]). */
data class QuizInputs(
    /** The learner's cards with their schedule (the dashboard's pool). */
    val cards: List<ReviewCard> = emptyList(),
    /** Their one-word cards by forms: which of them a dialog's turn tests ([DialogWords.turn]). */
    val words: MyWords = MyWords.NONE,
    /** The form a card asks in the quiz, chosen ([si.lanisce.lani.game.WordForms.question]), by card id. */
    val forms: Map<String, FormQuestion> = emptyMap(),
    /** The book's pages' masteries (the home language's): the rules not yet aren't asked for; a page not here isn't in the book. */
    val mastery: Map<String, Mastery> = emptyMap(),
    /** The choice exercises that name a page (the modules', the tent's, the letters'): the grammar book's practice. */
    val exercises: List<Exercise.Choice> = emptyList(),
    /** The cards play counted today ([si.lanisce.lani.game.GameState.playReviews]) when getting ready. */
    val played: PlayReviewDay? = null,
    /** The bridge takes a dialog's words ([si.lanisce.lani.app.DialogWordsController.FEATURE]): a slip may lower a card. */
    val dialogWords: Boolean = false,
)

object RoadQuiz {
    /** A question's options at most (the numbers there are). */
    const val MAX_OPTIONS = 4

    /** The pause after the question, before "Ena". */
    const val ASK_GAP_MS = 700L

    /** After an option's number ("Ena:"), before the option. */
    const val NUMBER_GAP_MS = 150L

    /** The short gap after each option: ⏭ in it picks the option just read. */
    const val OPTION_GAP_MS = 1_200L

    /** ⏭ this soon into the next option's number still picks the one before (the grace after it ends). */
    const val GRACE_MS = 500L

    /** Between the first round of options and the second. */
    const val ROUND_MS = 1_000L

    /** The options are read this many times; nothing pressed, on to the next question. */
    const val ROUNDS = 2

    /** After the last option of the last round (its gap, then this), before the next question: ⏭ still picks it. */
    const val END_MS = 800L

    /** A summary ("Sedem od desetih.") after every this many answers. */
    const val SUMMARY_EVERY = 10

    /** At most this many of each kind on the phone. */
    const val MAX_DIALOG = 80
    const val MAX_CARDS = 60
    const val MAX_FORMS = 40
    const val MAX_RIDDLES = 40
    const val MAX_GRAMMAR = 40

    /** The id of the kit's item ([Kind.KIT]). */
    const val KIT_ID = "quiz:kit"

    /** The order the kinds take turns in a session. */
    private val TURNS = listOf(QuizKind.DIALOG, QuizKind.MEANING, QuizKind.FORM, QuizKind.RIDDLE, QuizKind.GRAMMAR)

    /** [MEANING][QuizKind.MEANING] and [WORD][QuizKind.WORD] take one turn: a card's two questions, one a day. */
    private fun turnOf(k: QuizKind) = if (k == QuizKind.WORD) QuizKind.MEANING else k

    private fun norm(s: String) = Grading.normalize(s)

    // --- the questions -----------------------------------------------------------------------------------------------

    private fun clip(text: String, voices: List<String>, clips: ClipLookup): Sound.Clip? =
        clips.urls(text, voices)?.takeIf { it.isNotEmpty() }?.let { Sound.Clip(text, it.map(RoadPlay::fileOf)) }

    /** [text] in the first of [voices] the voice store has it in, else to be voiced while getting ready in the first. */
    private fun said(text: String, voices: List<String>, clips: ClipLookup): Sound =
        clip(text, voices, clips) ?: Sound.Spoken(text, voices.first())

    /** A stable order of [xs] for [key]: a question's distractors, the same each time it is got ready. */
    private fun <T> stable(xs: List<T>, key: String, id: (T) -> String): List<T> =
        xs.sortedBy { (key + "|" + id(it)).hashCode() }

    /**
     * The quiz's items from [inputs] ([Kind.QUIZ], each with its question; and the kit, [Kind.KIT], when there is one):
     * dialog turns, a card's meaning and its word, a word's forms, riddles, the grammar book's choice exercises, the kinds
     * in turn (so a small library has some of each). [voicesOf] gives a scene's person's voices, [teller] a riddle drill's.
     */
    fun items(
        inputs: RoadInputs,
        clips: ClipLookup,
        words: QuizWords = QuizWords.EN,
        voicesOf: (SceneSpec, String?) -> List<String> = { _, _ -> RoadPlay.NARRATOR },
        teller: (String?) -> List<String> = { RoadPlay.NARRATOR },
    ): List<RoadItem> {
        val q = inputs.quiz
        val notYet = { page: String -> q.mastery[page] == Mastery.NOT_YET }
        val cards = q.cards.associateBy { it.id }
        val scenes = inputs.scenes.filter { it.language == inputs.target.code }
        val byKind = listOf(
            dialogs(scenes, inputs.level, notYet, q.words, cards, voicesOf, clips, words).take(MAX_DIALOG),
            cards(q.cards, clips, words).let { (m, w) -> m.take(MAX_CARDS) + w.take(MAX_CARDS) },
            forms(q.cards, q.forms, clips, words, inputs.base.code).take(MAX_FORMS),
            riddles(inputs.drills, inputs.target.code, inputs.base.code, inputs.level, notYet, cards, teller, clips).take(MAX_RIDDLES),
            grammar(q.exercises, q.mastery, clips, words).take(MAX_GRAMMAR),
        ).map { ArrayDeque(it) }
        val questions = mutableListOf<QuizQuestion>()
        while (byKind.any { it.isNotEmpty() }) byKind.forEach { d -> d.removeFirstOrNull()?.let(questions::add) }
        if (questions.isEmpty()) return emptyList()
        val items = questions.distinctBy { it.id }.map { item(it) }
        return items + kitItem(kit(words, clips))
    }

    /** A question as a library item: every sound it may play, to get ready ([QuizQuestion.sounds]). */
    fun item(q: QuizQuestion): RoadItem = RoadItem("quiz:${q.id}", Kind.QUIZ, q.title, q.kind.name.lowercase(), q.sounds, quiz = q)

    fun kitItem(kit: QuizKit): RoadItem = RoadItem(KIT_ID, Kind.KIT, "", "", kit.sounds, kit = kit)

    /** The fixed phrases: the target's clip, else voiced while getting ready, else the base language's prompt. */
    fun kit(w: QuizWords, clips: ClipLookup): QuizKit {
        fun alts(p: Pair<String, String>): List<Sound> =
            clip(p.first, RoadPlay.NARRATOR, clips)?.let(::listOf) ?: listOf(Sound.Spoken(p.first, RoadPlay.NARRATOR.first()), Sound.Prompt(p.second))
        return QuizKit(w.numbers.map(::alts), alts(w.right), alts(w.wrong), (0..SUMMARY_EVERY).map { alts(w.score(it)) })
    }

    /**
     * The dialogs' turns as questions, of the scenes at the learner's [level] or below: a turn's context (the line before
     * it, in its speaker's voice, when the voice store has it), what to answer ("You want to say: …" when the turn tests a
     * form or has no context, else "What do you answer?"), and its right answer with up to three wrong ones. The turn as the
     * learner meets it: a rule not introduced yet isn't asked for ([Introduction.turn]: its wrong choices go; an echo turn
     * isn't a question); a tap turn isn't one (it's the picture). Picking counts as the dialog's pick does: on the page its
     * choices name, and on the learner's [words] it tests ([DialogWords.turn]).
     */
    fun dialogs(
        scenes: List<SceneSpec>,
        level: String,
        notYet: (String) -> Boolean,
        words: MyWords,
        cards: Map<String, ReviewCard>,
        voicesOf: (SceneSpec, String?) -> List<String>,
        clips: ClipLookup,
        w: QuizWords = QuizWords.EN,
    ): List<QuizQuestion> {
        val at = Introduction.rank(level) ?: 0
        val out = mutableListOf<QuizQuestion>()
        val seen = HashSet<String>()
        for (scene in scenes) {
            if ((Introduction.rank(scene.level) ?: 0) > at) continue
            for (d in scene.dialogs) {
                var context: DialogLine? = null
                for ((i, line) in d.lines.withIndex()) {
                    if (line.choices.isEmpty()) {
                        if (!line.sl.isNullOrBlank()) context = line
                        continue
                    }
                    val q = turn(scene, "${scene.id}/${d.id}/$i", line, context, notYet, words, cards, voicesOf, clips, w) ?: continue
                    // the same turn in another variant of the dialog: once
                    if (seen.add(q.options.joinToString("|") { norm(it.text) })) out += q
                }
            }
        }
        return out
    }

    private fun turn(
        scene: SceneSpec,
        id: String,
        line: DialogLine,
        context: DialogLine?,
        notYet: (String) -> Boolean,
        words: MyWords,
        cards: Map<String, ReviewCard>,
        voicesOf: (SceneSpec, String?) -> List<String>,
        clips: ClipLookup,
        w: QuizWords,
    ): QuizQuestion? {
        if (line.tapTurn || line.choices.size < 2) return null
        val gate = Introduction.turn(line, notYet)
        if (gate?.echo == true) return null
        val kept = gate?.keep ?: line.choices.indices.toList()
        val right = kept.firstOrNull { line.choices[it].ok } ?: return null
        val wrongs = kept.filter { !line.choices[it].ok }.distinctBy { norm(line.choices[it].sl) }.filter { norm(line.choices[it].sl) != norm(line.choices[right].sl) }
        if (wrongs.isEmpty()) return null
        // the turn as asked: the right answer first, then its wrong ones (the order heard is the day's: compose)
        val shown = line.copy(choices = (listOf(right) + wrongs.take(MAX_OPTIONS - 1)).map { line.choices[it] })
        val choices = shown.choices
        if (choices.any { it.sl.isBlank() }) return null
        val later = gate?.pages.orEmpty().toSet()
        val tested = DialogWords.turn(shown, words)
        val rightPage = (shown.grammar ?: choices.mapNotNull { it.grammar }.distinct().singleOrNull())?.takeIf { it !in later }
        val options = choices.mapIndexed { k, c ->
            val page = if (k == 0) rightPage else (c.grammar ?: shown.grammar)?.takeIf { it !in later }
            val answered = if (k == 0) {
                tested?.byRight?.get(0).orEmpty().map { t -> QuizWord(t.word.card.id, t.word.said, form = WordTest.MEANING !in t.by.values) }
            } else {
                tested?.missed(k).orEmpty().map { (tw, how) -> QuizWord(tw.card.id, tw.said, form = how == WordTest.FORM) }
            }
            QuizOption(c.sl, said(c.sl, RoadPlay.NARRATOR, clips), page, answered)
        }
        val before = context?.sl?.takeIf { it.isNotBlank() }?.let { clip(it, voicesOf(scene, context.who), clips) }
        val form = Forms.turn(choices.map { it.sl }, 0) != null
        val en = choices[0].en.trim()
        val cue = if ((form || before == null) && en.isNotEmpty()) w.youWant(en) else w.whatAnswer
        val ask = listOfNotNull(before, before?.let { Sound.Pause(500) }, Sound.Prompt(cue))
        val tests = options.flatMap { it.words }.map { it.card }.distinct().mapNotNull { cards[it]?.let(QuizCard::of) }
        return QuizQuestion(
            "dialog:$id", QuizKind.DIALOG, before?.text ?: cue, ask, options, 0, listOf(options[0].sound), choices[0].sl, tests,
        )
    }

    /**
     * The cards' questions, of the vocabulary cards whose Slovene the voice store has (the due ones first, the soonest
     * first): which meaning ([QuizKind.MEANING]: the Slovene, "What does it mean?", the meanings) and which word
     * ([QuizKind.WORD]: "How do you say: thank you?", the Slovene words); the wrong options other cards'. A right answer is
     * a word met in play on the card; a wrong one got its meaning wrong.
     */
    fun cards(all: List<ReviewCard>, clips: ClipLookup, w: QuizWords = QuizWords.EN): Pair<List<QuizQuestion>, List<QuizQuestion>> {
        val vocab = all.asSequence()
            .filter { it.kind == "vocabulary" && ReviewPlanner.accepted(it.front).isNotEmpty() && ReviewPlanner.meaning(it.back).isNotBlank() }
            .mapNotNull { c -> clip(c.front, RoadPlay.NARRATOR, clips)?.let { c to it } }
            .distinctBy { norm(it.first.front) }
            .toList()
            .sortedWith(compareBy({ it.first.due ?: LocalDate.MAX }, { it.first.id }))
        if (vocab.size < 3) return emptyList<QuizQuestion>() to emptyList()
        val meanings = mutableListOf<QuizQuestion>()
        val wordsOf = mutableListOf<QuizQuestion>()
        for ((c, sl) in vocab) {
            val meaning = ReviewPlanner.meaning(c.back)
            val others = stable(vocab.filter { (o, _) -> o.id != c.id }, c.id) { it.first.id }
                .filter { (o, _) -> norm(ReviewPlanner.meaning(o.back)) != norm(meaning) && norm(o.front) != norm(c.front) }
                .distinctBy { norm(ReviewPlanner.meaning(it.first.back)) }
                .take(2)
            if (others.size < 2) continue
            val word = listOf(QuizWord(c.id, c.front))
            val card = listOf(QuizCard.of(c))
            val answer = listOf(sl, Sound.Pause(300), Sound.Prompt(meaning))
            meanings += QuizQuestion(
                "meaning:${c.id}", QuizKind.MEANING, c.front, listOf(Sound.Prompt(w.whatMeans), Sound.Pause(300), sl),
                (listOf(c to sl) + others).map { (o, _) -> ReviewPlanner.meaning(o.back).let { m -> QuizOption(m, Sound.Prompt(m), words = word) } },
                0, answer, c.front, card,
            )
            wordsOf += QuizQuestion(
                "word:${c.id}", QuizKind.WORD, meaning, listOf(Sound.Prompt(w.howSay(meaning))),
                (listOf(c to sl) + others).map { (o, s) -> QuizOption(o.front, s, words = word) },
                0, answer, c.front, card,
            )
        }
        return meanings to wordsOf
    }

    /**
     * A word's form, of the learner's familiar one-word cards with a form reached ([forms]: the form each asks, chosen
     * among its own forms; the due cards first): "Which is right?", the word, the sentence's meaning when it is a line of the
     * content; the options the sentence with each form ("Jaz sedem na klop.", "Jaz sedim na klop."). Right: the word met in
     * play and right on the form's rule; wrong: a slip on the rule, the card as it is.
     */
    fun forms(all: List<ReviewCard>, forms: Map<String, FormQuestion>, clips: ClipLookup, w: QuizWords = QuizWords.EN, base: String = "en"): List<QuizQuestion> {
        val out = mutableListOf<QuizQuestion>()
        val cards = all.filter { it.id in forms && WordFormsWire.lemmaOf(it) != null && ReviewPlanner.familiarity(it) >= 2 }
            .sortedWith(compareBy({ it.due ?: LocalDate.MAX }, { it.id }))
        for (c in cards) {
            val f = forms.getValue(c.id)
            val accept = f.accept.map(::norm).toSet()
            val opts = f.options.take(MAX_OPTIONS).let { o -> if (o.any { norm(it) in accept }) o else emptyList() }
            if (opts.size < 3) continue
            val sentences = opts.map(f.gap::filled)
            if (sentences.map(::norm).distinct().size != sentences.size) continue
            val right = opts.indexOfFirst { norm(it) in accept }
            // the right one first: the order heard is the day's (compose)
            val order = listOf(right) + opts.indices.filter { it != right }
            val word = QuizWord(c.id, c.front, form = true)
            val options = order.map { QuizOption(sentences[it], said(sentences[it], RoadPlay.NARRATOR, clips), f.page, listOf(word)) }
            val lemma = clip(f.lemma, RoadPlay.NARRATOR, clips)
            val meant = f.line?.meantIn(base)?.takeIf { it.isNotBlank() }
            val ask = listOfNotNull<Sound>(Sound.Prompt(w.whichRight), lemma?.let { Sound.Pause(300) }, lemma, meant?.let { Sound.Prompt(it) })
            out += QuizQuestion(
                "form:${c.id}/${f.key}", QuizKind.FORM, f.gap.shown.replace(Regex("""\s*\([^()]*\)\s*$"""), "") + " (${f.lemma})",
                ask, options, 0, listOf(options[0].sound), options[0].text, listOf(QuizCard.of(c)), f.key,
            )
        }
        return out
    }

    /**
     * Stari Janez's riddles ([RoadDrills]: those offered at the learner's [level], whose clues, question and answer the
     * voice store has in the teller's voices): the clues and "Kaj sem?", then the answer and two others of the same kind (a
     * word's or a villager's). A riddle of a pack word the learner has a card of counts on it, as a word met in play.
     */
    fun riddles(
        drills: List<Drill>,
        target: String,
        base: String,
        level: String,
        notYet: (String) -> Boolean,
        cards: Map<String, ReviewCard>,
        teller: (String?) -> List<String>,
        clips: ClipLookup,
    ): List<QuizQuestion> {
        val out = mutableListOf<QuizQuestion>()
        for (d in drills.filter { it.language == target && it.kind == DrillKind.RIDDLE }) {
            val voices = teller(d.teller)
            val riddles = d.riddles.filter { RoadDrills.offered(it.level, emptyList(), level, notYet) }
            for (r in riddles) {
                val clues = r.clues.map { clip(it.said(target), voices, clips) ?: return@map null }
                if (clues.any { it == null }) continue
                val ask = clip(r.ask.said(target), voices, clips) ?: continue
                val answer = r.answer.said(target).takeIf { it.isNotBlank() } ?: continue
                val answerClip = clip(answer, voices, clips) ?: continue
                val others = stable(riddles.filter { o -> o.id != r.id && (o.word != null) == (r.word != null) }, r.id) { it.id }
                    .map { it.answer.said(target) }.filter { it.isNotBlank() && norm(it) != norm(answer) }.distinctBy(::norm).take(2)
                if (others.size < 2) continue
                val card = r.word?.let { wd -> cards[PackSession.itemId(wd.substringBefore('/'), wd.substringAfter('/'))] }
                val word = card?.let { listOf(QuizWord(it.id, it.front)) }.orEmpty()
                val options = listOf(QuizOption(answer, answerClip, words = word)) + others.map { QuizOption(it, said(it, voices, clips), words = word) }
                val sounds = clues.filterNotNull().flatMap { listOf(it, Sound.Pause(RoadDrills.CLUE_MS)) } + ask
                val meaning = r.answer.meaning(base).takeIf { it.isNotBlank() }
                out += QuizQuestion(
                    "riddle:${r.id}", QuizKind.RIDDLE, r.ask.said(target), sounds, options, 0,
                    listOfNotNull(answerClip, meaning?.let { Sound.Pause(300) }, meaning?.let { Sound.Prompt(it) }), answer,
                    listOfNotNull(card?.let(QuizCard::of)),
                )
            }
        }
        return out
    }

    private val TRANSLATION = Regex("""\(([^()]*)\)\s*$""")

    /**
     * The grammar book's choice exercises with a sentence to fill (a page named, met: not "not yet"; not heard ones): the
     * sentence's meaning when the prompt has it in brackets ("I don't have time."), else "Which is right?"; the options
     * the sentence with each choice ("Nimam čas.", "Nimam časa."). An answer counts on its page, as a choice exercise's.
     */
    fun grammar(exercises: List<Exercise.Choice>, mastery: Map<String, Mastery>, clips: ClipLookup, w: QuizWords = QuizWords.EN): List<QuizQuestion> {
        val out = mutableListOf<QuizQuestion>()
        for (ex in exercises) {
            val page = ex.grammar ?: continue
            val m = mastery[page] ?: continue
            if (m == Mastery.NOT_YET || ex.audio != null) continue
            val right = ex.options.getOrNull(ex.answer)?.takeIf { it.isNotBlank() } ?: continue
            val gap = Forms.gap(ex.prompt, right) ?: continue
            val wrongs = ex.options.filterIndexed { i, o -> i != ex.answer && o.isNotBlank() }.take(MAX_OPTIONS - 1)
            if (wrongs.isEmpty()) continue
            val sentences = (listOf(right) + wrongs).map(gap::filled)
            if (sentences.map(::norm).distinct().size != sentences.size) continue
            val meaning = TRANSLATION.find(ex.prompt)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() && '/' !in it }
            val options = sentences.map { QuizOption(it, said(it, RoadPlay.NARRATOR, clips), page) }
            out += QuizQuestion(
                "grammar:$page/${Drills.slug(sentences[0])}", QuizKind.GRAMMAR, gap.shown.replace(Regex("""\s*\([^()]*\)\s*$"""), ""),
                listOf(Sound.Prompt(meaning ?: w.whichRight)), options, 0, listOf(options[0].sound), sentences[0],
            )
        }
        return out.distinctBy { it.id }
    }

    // --- the session --------------------------------------------------------------------------------------------------

    /** The card a card's question is about ("meaning:<card>", "word:<card>"). */
    private fun cardOf(q: QuizQuestion) = q.cards.firstOrNull()?.id ?: q.id

    /**
     * "❓ Kviz · Quiz": the questions on the phone ([lib]'s [Kind.QUIZ] items), the ones on the learner's cards due (or
     * nearly due, [PlayReviews.due]) [today] first, then the rest; each part the kinds in turn, each kind the least recently
     * heard first. Of a card's two questions (its meaning, its word) one a day, in turn.
     */
    fun session(lib: RoadLibrary, heard: Map<String, Long>, today: String): List<RoadItem> {
        val day = runCatching { LocalDate.parse(today) }.getOrDefault(LocalDate.now())
        val all = lib.of(Kind.QUIZ).filter { it.quiz != null }.filter { item ->
            val q = item.quiz!!
            when (q.kind) {
                QuizKind.MEANING, QuizKind.WORD -> (Math.floorMod(cardOf(q).hashCode() + day.toEpochDay(), 2L) == 0L) == (q.kind == QuizKind.MEANING)
                else -> true
            }
        }
        val (due, rest) = all.partition { item -> item.quiz!!.cards.any { PlayReviews.due(it.card(), day) } }
        return inTurn(due, heard) + inTurn(rest, heard)
    }

    private fun inTurn(items: List<RoadItem>, heard: Map<String, Long>): List<RoadItem> {
        val queues = TURNS.map { k -> ArrayDeque(RoadMix.fresh(items.filter { turnOf(it.quiz!!.kind) == k }, heard)) }
        val out = mutableListOf<RoadItem>()
        while (queues.any { it.isNotEmpty() }) queues.forEach { q -> q.removeFirstOrNull()?.let(out::add) }
        return out
    }

    // --- a question played --------------------------------------------------------------------------------------------

    /** What a sound of a question played is ([QuizPlay.parts]); [option] the option's place as heard (0: "Ena"). */
    enum class Role { ASK, NUMBER, TEXT, GAP, BETWEEN, END, FEEDBACK }

    data class Part(val role: Role, val option: Int = -1)

    /**
     * A question as it plays: its [item] (the question, then its options numbered, each with its gap, twice: [parts] says
     * which sound is what), the options in the order heard ([order]: heard place → the question's option), and whether it
     * was answered already.
     */
    class QuizPlay(val item: RoadItem, val parts: List<Part>, val order: List<Int>) {
        val question: QuizQuestion get() = item.quiz!!

        @Volatile
        var answered: Boolean = false

        /** The question's option heard at [shown] (0: "Ena"), or null. */
        fun option(shown: Int): Int? = order.getOrNull(shown)

        /** A feedback's or a summary's play: next goes on, previous plays it again ([QuizControls]). */
        val feedback: Boolean get() = parts.all { it.role == Role.FEEDBACK }
    }

    /** The order a question's options are heard in on [seed] (the day's): another order another day, the right one anywhere. */
    fun order(q: QuizQuestion, seed: Long, n: Int = q.options.size): List<Int> =
        q.options.indices.toList().shuffled(Random(seed * 31 + q.id.hashCode())).take(n.coerceAtMost(MAX_OPTIONS)).let { o ->
            if (q.right in o) o else listOf(q.right) + o.dropLast(1)
        }

    /**
     * [item] (a library item of a question) as it plays, with the kit's [numbers] ("Ena." …): the question, a pause; then
     * [ROUNDS] times each option with its number before it and a gap after it ([OPTION_GAP_MS]), the rounds [ROUND_MS]
     * apart; at the end [END_MS]. The options in the day's [order].
     */
    fun compose(item: RoadItem, numbers: List<Sound>, order: List<Int>): QuizPlay {
        val q = requireNotNull(item.quiz)
        val shown = order.take(numbers.size)
        val sounds = mutableListOf<Sound>()
        val parts = mutableListOf<Part>()
        fun add(s: Sound, p: Part) {
            sounds += s
            parts += p
        }
        q.ask.forEach { add(it, Part(Role.ASK)) }
        add(Sound.Pause(ASK_GAP_MS), Part(Role.ASK))
        val last = shown.lastIndex
        repeat(ROUNDS) { round ->
            if (round > 0) add(Sound.Pause(ROUND_MS), Part(Role.BETWEEN, last))
            shown.forEachIndexed { k, o ->
                add(numbers[k], Part(Role.NUMBER, k))
                add(Sound.Pause(NUMBER_GAP_MS), Part(Role.TEXT, k))
                add(q.options[o].sound, Part(Role.TEXT, k))
                add(Sound.Pause(OPTION_GAP_MS), Part(Role.GAP, k))
            }
        }
        add(Sound.Pause(END_MS), Part(Role.END, last))
        return QuizPlay(item.copy(sounds = sounds), parts, shown)
    }

    /**
     * What follows a pick: "Prav!" and the right answer whole ([right]), or "Ne, prav je:" and the right answer; then a
     * moment before the next question.
     */
    fun feedback(q: QuizQuestion, right: Boolean, kit: QuizKit.Resolved): QuizPlay {
        val sounds = listOf(if (right) kit.right else kit.wrong, Sound.Pause(if (right) 300 else 200)) + q.answer + Sound.Pause(1_500)
        val item = RoadItem("quiz:${q.id}#${if (right) "right" else "wrong"}", Kind.QUIZ, "${if (right) "✓" else "✗"} ${q.answerText}", q.kind.name.lowercase(), sounds)
        return QuizPlay(item, sounds.map { Part(Role.FEEDBACK) }, emptyList())
    }

    /** The summary after [SUMMARY_EVERY] answers: "Sedem od desetih." ([right] of them right). */
    fun summary(right: Int, kit: QuizKit.Resolved): QuizPlay {
        val n = right.coerceIn(0, SUMMARY_EVERY)
        val sounds = listOf(Sound.Pause(400), kit.scores[n], Sound.Pause(1_500))
        val item = RoadItem("quiz:score#$n", Kind.QUIZ, "⭐ $n/$SUMMARY_EVERY", "", sounds)
        return QuizPlay(item, sounds.map { Part(Role.FEEDBACK) }, emptyList())
    }
}

/** How far the learner got in a quiz played now: the answers since the last summary, and how many were right. */
data class QuizScore(val answered: Int = 0, val right: Int = 0) {
    fun add(ok: Boolean) = QuizScore(answered + 1, right + if (ok) 1 else 0)

    /** A summary is due: [RoadQuiz.SUMMARY_EVERY] answers since the last. */
    val summary: Boolean get() = answered >= RoadQuiz.SUMMARY_EVERY
}

/**
 * What the car's buttons do in a quiz's question (companion/README.md, "Im Auto · In the car"). Pure: [RoadPlayer] asks
 * it, the tests check it. [sound] is the sound of the question played now ([RoadQuiz.QuizPlay.parts]), [intoMs] how long
 * it has played.
 */
object QuizControls {
    sealed interface Press {
        /** The option heard at [shown] (0: "Ena") is the answer. */
        data class Pick(val shown: Int) : Press

        /** From the question on to its options at once. */
        data object Options : Press

        /** The question and its options again from the start. */
        data object Repeat : Press

        /** On to the next item (the player's own next). */
        data object Skip : Press
    }

    /**
     * ⏭: the option being read (its number or itself), or the one just read (its gap, the pause between the rounds and at
     * the end, and the first [RoadQuiz.GRACE_MS] of the next one's number); during the question, on to the options; in a
     * feedback or a summary, on.
     */
    fun next(parts: List<RoadQuiz.Part>, sound: Int, intoMs: Long): Press {
        val p = parts.getOrNull(sound) ?: return Press.Skip
        return when (p.role) {
            RoadQuiz.Role.ASK -> Press.Options
            RoadQuiz.Role.NUMBER -> {
                val before = parts.getOrNull(sound - 1)
                if (p.option > 0 && before?.role == RoadQuiz.Role.GAP && intoMs < RoadQuiz.GRACE_MS) Press.Pick(p.option - 1) else Press.Pick(p.option)
            }
            RoadQuiz.Role.TEXT, RoadQuiz.Role.GAP, RoadQuiz.Role.BETWEEN, RoadQuiz.Role.END -> Press.Pick(p.option)
            RoadQuiz.Role.FEEDBACK -> Press.Skip
        }
    }

    /** ⏮: a question again from its start (never the question before: it was answered or passed); a feedback again. */
    fun previous(): Press = Press.Repeat

    /** ⏯ (where the car sends it): during the options it picks as ⏭ does; during the question or a feedback it plays or pauses (null). */
    fun playPause(parts: List<RoadQuiz.Part>, sound: Int, intoMs: Long): Press.Pick? {
        val p = parts.getOrNull(sound) ?: return null
        if (p.role == RoadQuiz.Role.ASK || p.role == RoadQuiz.Role.FEEDBACK) return null
        return next(parts, sound, intoMs) as? Press.Pick
    }

    /** Where the options begin: the first number's sound, or null. */
    fun optionsAt(parts: List<RoadQuiz.Part>): Int? = parts.indexOfFirst { it.role == RoadQuiz.Role.NUMBER }.takeIf { it >= 0 }
}
