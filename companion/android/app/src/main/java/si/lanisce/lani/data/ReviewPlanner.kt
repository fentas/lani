package si.lanisce.lani.data

import kotlin.random.Random
import si.lanisce.lani.game.FormQuestion
import si.lanisce.lani.game.WordForms
import si.lanisce.lani.l10n.bi

/**
 * Turns due spaced-repetition cards into varied exercises, Duolingo style.
 * Harder variants for better-known cards; a correct answer on a harder variant
 * earns a higher SM-2 quality.
 */
object ReviewPlanner {
    enum class Variant(val difficulty: Int) {
        FLIP(1),         // see Slovene, reveal, self-rate
        RECOGNIZE(1),    // see Slovene → pick the meaning
        LISTEN(2),       // hear Slovene → pick the meaning
        PICK_SLOVENE(2), // see meaning → pick the Slovene
        TILES(2),        // see meaning → build the Slovene from word tiles
        TYPE(3),         // see meaning → type the Slovene
        DICTATION(3),    // hear Slovene (no text) → type what was said
        SPEAK(3),        // see meaning → say the Slovene out loud (speech recognition)
        FORM(2),         // a form of the word in a sentence with a gap → pick it among the word's other forms (game/WordForms)
        FORM_TYPE(3),    // the same, typed
        ;

        /** Asks a form of the word, not its meaning: a wrong one counts on the grammar rule ([quality]). */
        val form: Boolean get() = this == FORM || this == FORM_TYPE
    }

    /** A card's exercise; [form]: the form question it asks ([Variant.form]). */
    data class Task(val card: ReviewCard, val variant: Variant, val exercise: Exercise, val form: FormQuestion? = null)

    /** Weights per familiarity: 0 = new or last failed, 1 = learning, 2 = known. */
    private val weights = mapOf(
        0 to mapOf(Variant.RECOGNIZE to 5, Variant.LISTEN to 3, Variant.PICK_SLOVENE to 2, Variant.FLIP to 1),
        1 to mapOf(Variant.RECOGNIZE to 1, Variant.LISTEN to 3, Variant.PICK_SLOVENE to 3, Variant.TILES to 3, Variant.TYPE to 2, Variant.DICTATION to 1, Variant.SPEAK to 2),
        2 to mapOf(Variant.LISTEN to 1, Variant.PICK_SLOVENE to 1, Variant.TILES to 2, Variant.TYPE to 4, Variant.DICTATION to 2, Variant.SPEAK to 3),
    )

    fun familiarity(c: ReviewCard): Int = when {
        c.repetitions == 0 || c.lastQuality < 3 -> 0
        c.repetitions <= 2 -> 1
        else -> 2
    }

    /**
     * Accepted Slovene answers for a card's front: "Kako ste? / Kako si?" → both;
     * "hvala (lepa)" → "hvala" and "hvala lepa". Empty when the front isn't a typeable phrase.
     */
    fun accepted(front: String): List<String> {
        // Rules and placeholders ("Imenujem se ...") aren't phrases you can type.
        if ('→' in front || '♂' in front || '♀' in front || "..." in front || '…' in front) return emptyList()
        val paren = Regex("""\s*\(([^)]*)\)""")
        return front.split(" / ").flatMap { alt ->
            val bare = alt.replace(paren, "").trim()
            val full = alt.replace(paren) { " " + it.groupValues[1] }.replace(Regex("\\s+"), " ").trim()
            listOf(bare, full)
        }.filter { it.isNotBlank() && it.length <= 40 }.distinct()
    }

    /** A card's meaning without teaching notes: "good day (polite hello)" → "good day". */
    fun meaning(back: String): String = back.replace(Regex("""\s*\([^)]*\)"""), "").trim().ifEmpty { back }

    /**
     * The SM-2 quality of an answer. A wrong form ([Variant.form]) leaves the word as it was known, 3: its meaning wasn't
     * asked, and the mistake counts on the form's grammar rule instead (companion/GAME.md, "A word's forms").
     */
    fun quality(variant: Variant, verdict: Grading.Verdict): Int = when (verdict) {
        Grading.Verdict.CORRECT -> 2 + variant.difficulty // 3, 4, 5
        Grading.Verdict.ALMOST -> 3
        Grading.Verdict.WRONG -> if (variant.form) 3 else 1
    }

    /**
     * @param canRecognize a speech recognizer is known to take Slovene (enables [Variant.SPEAK])
     * @param forms the form question a card asks this time, if any ([WordForms.forCard]); at most [formCap] of the cards
     *   ask one (about one in [WordForms.EVERY]), the others are asked their meaning as ever
     */
    fun plan(
        cards: List<ReviewCard>, pool: List<ReviewCard>, canSpeak: Boolean, random: Random = Random.Default,
        canRecognize: Boolean = false, forms: ((ReviewCard) -> FormQuestion?)? = null,
        formCap: Int = maxOf(1, cards.size / WordForms.EVERY),
    ): List<Task> {
        var formsLeft = formCap
        return cards.map { card ->
            val q = if (forms != null && formsLeft > 0) forms(card) else null
            if (q != null) {
                formsLeft--
                formTask(card, q)
            } else plainTask(card, pool, canSpeak, random, canRecognize)
        }
    }

    /** The task of [card] asking form question [q]: chosen among the word's forms ([Variant.FORM]), or typed. */
    fun formTask(card: ReviewCard, q: FormQuestion): Task =
        if (q.typed) {
            val text = q.gap.before + "___" + q.gap.after
            val accept = (q.accept + q.accept.map(q.gap::filled)).distinct()
            Task(card, Variant.FORM_TYPE, Exercise.Cloze(text, accept, explain = q.explain, instruction = q.instruction, grammar = q.page), q)
        } else {
            Task(card, Variant.FORM, Exercise.Choice(q.gap.shown, q.options, q.options.indexOf(q.answer), explain = q.explain, instruction = q.instruction, grammar = q.page), q)
        }

    private fun plainTask(card: ReviewCard, pool: List<ReviewCard>, canSpeak: Boolean, random: Random, canRecognize: Boolean): Task {
        val accept = accepted(card.front)
        // Distractors must be real words too: no rules ("dober/dobra/dobro …") or placeholders.
        val others = pool.filter {
            it.id != card.id && it.back != card.back && it.front != card.front && accepted(it.front).isNotEmpty()
        }
        // Rules and placeholders ("dober/dobra/dobro …", "Imenujem se ...") aren't words: recall them on a
        // flashcard instead of making a quiz out of them.
        val word = card.kind == "vocabulary" && accept.isNotEmpty()
        val allowed = if (!word) mapOf(Variant.FLIP to 1) else weights.getValue(familiarity(card)).filterKeys { v ->
            when (v) {
                Variant.FLIP -> true
                Variant.RECOGNIZE, Variant.PICK_SLOVENE -> others.isNotEmpty()
                Variant.LISTEN -> canSpeak && others.isNotEmpty()
                Variant.TYPE -> card.kind == "vocabulary" && accept.isNotEmpty()
                Variant.DICTATION -> canSpeak && card.kind == "vocabulary" && accept.isNotEmpty()
                Variant.SPEAK -> canRecognize && card.kind == "vocabulary" && accept.isNotEmpty()
                Variant.TILES -> card.kind == "vocabulary" && accept.isNotEmpty() && accept.first().split(' ').size >= 2
                Variant.FORM, Variant.FORM_TYPE -> false // a form is asked only when [plan] has one for the card
            }
        }.ifEmpty { mapOf(Variant.FLIP to 1) }
        val variant = pick(allowed, random)
        return Task(card, variant, build(card, variant, accept, others, random))
    }

    private fun pick(weighted: Map<Variant, Int>, random: Random): Variant {
        var r = random.nextInt(weighted.values.sum())
        for ((v, w) in weighted) {
            r -= w
            if (r < 0) return v
        }
        return weighted.keys.first()
    }

    private fun options(correct: String, wrong: List<String>, random: Random): Pair<List<String>, Int> {
        val opts = (wrong.distinct().shuffled(random).take(3) + correct).shuffled(random)
        return opts to opts.indexOf(correct)
    }

    private fun build(card: ReviewCard, v: Variant, accept: List<String>, others: List<ReviewCard>, random: Random): Exercise =
        when (v) {
            // Rules and placeholders are recalled silently: reading "dober/dobra/dobro (dan ♂ …)" aloud helps no one.
            Variant.FLIP -> Exercise.Flashcard(card.front, card.back, speak = card.kind == "vocabulary" && accept.isNotEmpty())
            Variant.RECOGNIZE -> {
                val (o, a) = options(meaning(card.back), others.map { meaning(it.back) }, random)
                Exercise.Choice(card.front, o, a, instruction = bi("reviewPlanner.whatDoesMean"), say = card.front)
            }
            Variant.LISTEN -> {
                val (o, a) = options(meaning(card.back), others.map { meaning(it.back) }, random)
                Exercise.Choice("", o, a, audio = card.front, instruction = bi("common.listenPickMeaning"))
            }
            Variant.PICK_SLOVENE -> {
                val (o, a) = options(card.front, others.map { it.front }, random)
                Exercise.Choice(meaning(card.back), o, a, instruction = bi("reviewPlanner.howDoSay"), sayOptions = true)
            }
            Variant.DICTATION -> Exercise.Dictation(
                audio = accept.first(),
                accept = listOf(accept.first()), // only what was actually spoken counts
                instruction = bi("common.writeWhatHear"),
                explain = "= ${meaning(card.back)}",
            )
            Variant.TILES -> {
                val words = accept.first().split(' ')
                val lower = words.map { it.lowercase() }.toSet()
                val extra = others.flatMap { accepted(it.front).firstOrNull()?.split(' ').orEmpty() }
                    .filter { it.lowercase() !in lower }.distinct().shuffled(random).take(3)
                Exercise.Reorder(
                    prompt = meaning(card.back),
                    instruction = bi("reviewPlanner.buildSlovene"),
                    tokens = words,
                    solutions = accept.map { it.split(' ') }.filter { it.size == words.size },
                    distractors = extra,
                )
            }
            Variant.TYPE -> Exercise.Translate(meaning(card.back), accept, instruction = bi("common.writeSlovene"))
            Variant.SPEAK -> Exercise.Speak(
                say = accept.first(),
                prompt = meaning(card.back),
                accept = accept.drop(1),
                show = false,
                instruction = bi("common.saySlovene"),
            )
            // never picked by weight (see plainTask): a flashcard, should one come here
            Variant.FORM, Variant.FORM_TYPE -> Exercise.Flashcard(card.front, card.back, speak = card.kind == "vocabulary" && accept.isNotEmpty())
        }
}
