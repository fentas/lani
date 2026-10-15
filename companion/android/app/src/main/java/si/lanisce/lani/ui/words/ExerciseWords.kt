package si.lanisce.lani.ui.words

import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Spoken
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.ui.scene.ChoiceDiff
import si.lanisce.lani.ui.unpictured

/** What a text reads as: the language learned, the learner's own (the base), or either ("miza", "table" alone). */
enum class Reads { TARGET, BASE, UNSURE }

/** How the words of a text are looked up: not at all, a tap, or a long press (an answer, where a tap chooses it). */
enum class How { NONE, TAP, HOLD }

/**
 * Which words of one of an exercise's texts can be looked up, and how ([how]). A text goes by its parts ([TextParts]): the
 * words of a part that reads as the language learned can, the rest can't (a translation in brackets, the other half of
 * "Kaj pomeni? · What does it mean?").
 *
 * [usual]: what a part reads as when it reads as neither language (a sentence with a gap, a tile, a model sentence are the
 * language learned; a meaning to say in it isn't). [brackets]: what brackets add can be looked up (not before the answer:
 * a gap's lemma "(sesti)", a hint). [quotes]: what a quoted or emphasized part reads as when it reads as neither, null for
 * as the text around it ("Kako rečeš «table»?": the meaning asked for, not a word to look up). [skip]: words that never
 * can (the words a choice's options differ in: what it tests).
 */
data class Taps(
    val how: How,
    val usual: Reads = Reads.TARGET,
    val brackets: Boolean = true,
    val quotes: Reads? = null,
    val skip: List<WordToken> = emptyList(),
) {
    /** The words of [text] that can be looked up; [emphasized]: its bold and italic parts (inlineMarkdown's), as quotes. */
    fun words(text: String, reads: (String) -> Reads, emphasized: List<IntRange> = emptyList()): List<WordToken> {
        if (how == How.NONE) return emptyList()
        val tokens = WordTokens.of(text)
        if (tokens.isEmpty()) return emptyList()
        val target = BooleanArray(text.length)
        for (chunk in TextParts.chunks(text, emphasized)) {
            val around = reads(chunk.plain(text)).or(usual)
            for (p in chunk.pieces) {
                val r = when (p.kind) {
                    TextParts.Kind.PLAIN -> around
                    TextParts.Kind.QUOTE -> reads(p.of(text)).or(quotes ?: around)
                    TextParts.Kind.BRACKET -> if (brackets) reads(p.of(text)).or(Reads.BASE) else Reads.BASE
                }
                if (r == Reads.TARGET) for (i in p.start until p.end) target[i] = true
            }
        }
        return tokens.filter { t -> target[t.start] && skip.none { it.start == t.start && it.end == t.end } }
    }

    companion object {
        val NONE = Taps(How.NONE)
    }
}

private fun Reads.or(other: Reads): Reads = if (this == Reads.UNSURE) other else this

/**
 * A text in parts: chunks apart by " · " (a label in both languages) and line breaks, each of plain text, quotes («…»,
 * »…«, „…“, “…”, "…", and *…*, **…** as markdown emphasizes) and brackets. Pure.
 */
object TextParts {
    enum class Kind { PLAIN, QUOTE, BRACKET }

    /** [start] until [end] (exclusive) of a text, without the quotes or brackets around it. */
    data class Piece(val start: Int, val end: Int, val kind: Kind) {
        fun of(text: String): String = text.substring(start, end)
    }

    /** The pieces between two separators; [from] until [to]: where it is in the text, its quotes and brackets too. */
    data class Chunk(val pieces: List<Piece>, val from: Int, val to: Int) {
        /** Its plain text, what tells its language. */
        fun plain(text: String): String = pieces.filter { it.kind == Kind.PLAIN }.joinToString(" ") { it.of(text) }
    }

    private val CLOSE = mapOf('«' to "»", '»' to "«", '„' to "“", '“' to "”", '"' to "\"", '(' to ")", '*' to "*")

    fun chunks(text: String, emphasized: List<IntRange> = emptyList()): List<Chunk> {
        val out = ArrayList<Chunk>()
        var pieces = ArrayList<Piece>()
        var from = 0
        var chunkFrom = 0
        var i = 0
        fun plainUntil(end: Int) {
            if (end > from) pieces += Piece(from, end, Kind.PLAIN)
        }
        fun endChunk(to: Int) {
            if (pieces.isNotEmpty()) out += Chunk(pieces, chunkFrom, to)
            pieces = ArrayList()
        }
        while (i < text.length) {
            val e = emphasized.firstOrNull { it.first == i && it.last >= i && it.last < text.length }
            if (e != null) {
                plainUntil(i)
                pieces += Piece(i, e.last + 1, Kind.QUOTE)
                i = e.last + 1
                from = i
                continue
            }
            val c = text[i]
            if (c == '\n' || c == '·') {
                plainUntil(i)
                endChunk(i)
                i++
                from = i
                chunkFrom = i
                continue
            }
            val close = CLOSE[c]
            if (close != null) {
                val open = if (c == '*' && text.startsWith("**", i)) 2 else 1
                val shut = if (open == 2) "**" else close
                val j = text.indexOf(shut, i + open)
                if (j > i + open) {
                    plainUntil(i)
                    pieces += Piece(i + open, j, if (c == '(') Kind.BRACKET else Kind.QUOTE)
                    i = j + shut.length
                    from = i
                    continue
                }
            }
            i++
        }
        plainUntil(text.length)
        endChunk(text.length)
        return out
    }

    /**
     * The line a word of [text] is in, as its card shows it and keeps it as a new word's example: its chunk without what
     * brackets add ("Jaz ______ na klop." of "Jaz ______ na klop. (sesti)"; "Kam gre Luka?" of "Kam gre Luka? · Where is
     * Luka going?").
     */
    fun lineOf(text: String, word: WordToken): String {
        val c = chunks(text).firstOrNull { word.start >= it.from && word.start < it.to } ?: return text.trim()
        val b = StringBuilder()
        var at = c.from
        // a bracket's piece is inside its "(" and ")": they go too, and the space before
        for (p in c.pieces) if (p.kind == Kind.BRACKET) {
            b.append(text, at, p.start - 1)
            at = p.end + 1
        }
        b.append(text, at, c.to)
        return b.toString().replace(Regex("\\s+"), " ").trim()
    }
}

/**
 * What can be looked up in an exercise ([exercise]) while it's open and once it's answered ([answered]): never what it
 * tests (companion/README.md, "Exercises": words to tap). [reads] tells a text's language ([reader]).
 *
 * - **Flashcard**: its word once turned over, not before.
 * - **Choice, multi**: a sentence with a gap: its words, not what brackets add (the lemma "(sesti)", a hint), nor the
 *   options (its forms, the words that fill it). Options in the base (meanings): the Slovene asked about isn't, the prompt
 *   is what's tested. Options in the language learned: the prompt's words in it are context ("Kam gre Luka?"), what it
 *   quotes isn't (the meaning asked for); the options themselves by a long press only when the prompt asks in the language
 *   learned, none of them is a single word, and nothing is quoted (not "Kako rečeš «good day»?"), without the words they
 *   differ in ([ChoiceDiff]): a reply, a place, not a word's meaning or form. Options of neither language: nothing.
 * - **Cloze**: the sentence round the gap, not what brackets add; the gap has nothing to tap.
 * - **Reorder**: the tiles by a long press, for their meaning (the card shows no line: not their order); a prompt's words
 *   in the language learned.
 * - **Translate**: the source's words in the language learned (hints), only while the answer is in it; a Slovene source
 *   to say in the base is what's tested.
 * - **Speak**: the prompt's words in the language learned (a sentence with a gap: its words), the model sentence once shown.
 * - **Scenario**: the line said and the scene, while the answer is a reply (options in the language learned, or typed).
 * - **Free**: its prompt. **Dictation**: nothing is shown.
 *
 * Once answered, everything can, the tested word too; options and tiles by a tap then (it chooses nothing any more).
 */
class ExerciseTaps(val exercise: Exercise, val answered: Boolean, private val reads: (String) -> Reads) {

    /** What a choice's (a multi's, a scenario's) options read as; [UNSURE] for an exercise without. */
    private val options: Reads by lazy {
        when (val ex = exercise) {
            is Exercise.Choice -> optionsRead(ex.options, ex.sayOptions, asked = ex.say != null || ex.audio != null, ex.prompt)
            is Exercise.Multi -> optionsRead(ex.options, ex.sayOptions, asked = false, ex.prompt)
            is Exercise.Scenario -> ex.options?.let { optionsRead(it, false, asked = ex.line != null, ex.prompt) } ?: Reads.UNSURE
            else -> Reads.UNSURE
        }
    }

    private fun optionsRead(options: List<String>, sayOptions: Boolean, asked: Boolean, prompt: String): Reads {
        if (sayOptions) return Reads.TARGET
        val each = options.map { reads(unpictured(it)) }
        val t = Reads.TARGET in each
        val b = Reads.BASE in each
        return when {
            t && !b -> Reads.TARGET
            b && !t -> Reads.BASE
            t -> Reads.UNSURE
            // all of them either: the words that fill a sentence's gap, the meanings of the Slovene asked about (said,
            // heard), or the Slovene for a meaning
            GAP.containsMatchIn(prompt) && whole(prompt) != Reads.BASE -> Reads.TARGET
            asked -> Reads.BASE
            whole(prompt) == Reads.BASE -> Reads.TARGET
            else -> Reads.UNSURE
        }
    }

    /** What [text] reads as by its chunks: a language when each chunk that reads as one reads as it, else either ("Kaj pomeni? · What does it mean?"). */
    private fun whole(text: String): Reads {
        val each = TextParts.chunks(text).map { reads(it.plain(text)) }.filter { it != Reads.UNSURE }.distinct()
        return each.singleOrNull() ?: Reads.UNSURE
    }

    /** A choice's prompt: the words of it the options don't test. [text]: as shown (a typed gap's sentence, adapted). */
    private fun choicePrompt(text: String): Taps {
        val gap = GAP.containsMatchIn(text)
        return when {
            answered -> Taps(How.TAP, usual = if (gap || options == Reads.BASE) Reads.TARGET else Reads.BASE, quotes = if (options == Reads.TARGET) Reads.BASE else null)
            gap -> Taps(How.TAP, usual = Reads.TARGET, brackets = false)
            options == Reads.TARGET -> Taps(How.TAP, usual = Reads.BASE, brackets = false, quotes = Reads.BASE)
            else -> Taps.NONE
        }
    }

    /** Option [i] of [all]: none before the answer unless they're replies, places, sentences in a context ([asks]). */
    private fun option(i: Int, all: List<String>, prompt: String, context: Boolean): Taps = when {
        answered -> Taps(How.TAP, usual = if (options == Reads.TARGET) Reads.TARGET else Reads.BASE)
        options != Reads.TARGET || GAP.containsMatchIn(prompt) || counterpart(prompt) -> Taps.NONE
        !context && !asks(prompt) -> Taps.NONE
        all.any { WordTokens.of(unpictured(it)).size < 2 } -> Taps.NONE
        else -> Taps(How.HOLD, skip = ChoiceDiff.marks(all).getOrElse(i) { emptyList() })
    }

    /**
     * Whether [prompt] asks in the language learned: a part of it (or a quote: what someone says) reads so and is a
     * sentence or a question (it ends so, or has three words), not a meaning that only looks so ("so, thus").
     */
    private fun asks(prompt: String): Boolean = TextParts.chunks(prompt).any { c ->
        (listOf(c.plain(prompt)) + c.pieces.filter { it.kind == TextParts.Kind.QUOTE }.map { it.of(prompt) }).any { part ->
            val t = part.trim()
            reads(t) == Reads.TARGET && (t.lastOrNull()?.let { it in ".?!:…" } == true || WordTokens.of(t).size >= 3)
        }
    }

    /** Whether [prompt] quotes what isn't the language learned: the meaning its options say ("Kako rečeš «good day»?"). */
    private fun counterpart(prompt: String): Boolean =
        TextParts.chunks(prompt).any { c -> c.pieces.any { it.kind == TextParts.Kind.QUOTE && reads(it.of(prompt)) != Reads.TARGET } }

    /** What the answer a translation wants reads as: the language learned ("dobro jutro"), the base ("good morning"). */
    private val answer: Reads by lazy { (exercise as? Exercise.Translate)?.accept?.firstOrNull()?.let(reads) ?: Reads.UNSURE }
    private val intoTarget: Boolean get() = answer == Reads.TARGET

    /** The question or the sentence over the answers ([text] as shown: a sentence with a gap, a translation's source). */
    fun prompt(text: String, emphasized: List<IntRange> = emptyList()): List<WordToken> = promptTaps(text).words(text, reads, emphasized)

    internal fun promptTaps(text: String): Taps = when (val ex = exercise) {
        is Exercise.Choice, is Exercise.Multi -> choicePrompt(text)
        is Exercise.Scenario -> if (ex.options == null) open(Reads.BASE) else choicePrompt(text)
        is Exercise.Cloze -> open(Reads.TARGET)
        is Exercise.Reorder, is Exercise.Free -> open(Reads.BASE)
        is Exercise.Translate -> when {
            // a source to say in the base is the language learned; else only what reads as it clearly
            answered -> Taps(How.TAP, usual = if (answer == Reads.BASE) Reads.TARGET else Reads.BASE)
            intoTarget -> open(Reads.BASE)
            else -> Taps.NONE
        }
        is Exercise.Speak -> open(if (GAP.containsMatchIn(text)) Reads.TARGET else Reads.BASE)
        is Exercise.Flashcard, is Exercise.Dictation, is Exercise.Unsupported -> Taps.NONE
        // the school's sheets: the text around the gaps, a table's labels and given cells, a list's lines given are what
        // the items stand in (what brackets add, a lemma or a cue, once checked); a sort's prompt as well (its words:
        // [sorted])
        is Exercise.GapText, is Exercise.Table, is Exercise.Transform, is Exercise.Sort -> open(Reads.TARGET)
        // a counting card: its noun's card would show the forms it asks; once answered, every word
        is Exercise.Counting -> if (answered) open(Reads.TARGET) else Taps.NONE
    }

    /** A sort's word (one of the school's sheets): what it tests (its card says its gender), so only once checked. */
    fun sorted(text: String): List<WordToken> = (if (answered) Taps(How.TAP) else Taps.NONE).words(text, reads)

    /** The words of a context: what brackets add once answered. */
    private fun open(usual: Reads) = Taps(How.TAP, usual = usual, brackets = answered)

    /** Option [i] ([text]) of a choice, a multi or a scenario. */
    fun option(i: Int, text: String): List<WordToken> = optionTaps(i).words(text, reads)

    internal fun optionTaps(i: Int): Taps = when (val ex = exercise) {
        is Exercise.Choice -> option(i, ex.options, ex.prompt, context = false)
        is Exercise.Multi -> option(i, ex.options, ex.prompt, context = false)
        is Exercise.Scenario -> ex.options?.let { option(i, it, ex.prompt, context = ex.line?.let(reads) == Reads.TARGET) } ?: Taps.NONE
        else -> Taps.NONE
    }

    /** How an option's or a tile's words are looked up: a long press before the answer (a tap chooses), a tap after. */
    val answerHow: How get() = if (answered) How.TAP else How.HOLD

    /** A reorder's tile: for its meaning, before the answer too. */
    fun tile(text: String): List<WordToken> =
        (if (exercise is Exercise.Reorder) Taps(answerHow) else Taps.NONE).words(text, reads)

    /** A flashcard's word: once it's turned over ([revealed]). */
    fun front(text: String, revealed: Boolean): List<WordToken> =
        (if (revealed || answered) Taps(How.TAP) else Taps.NONE).words(text, reads)

    /** A flashcard's meaning and note, shown once turned over: what in them reads as the language learned. */
    fun back(text: String): List<WordToken> = Taps(How.TAP, usual = Reads.BASE).words(text, reads)

    /** A scenario's line said: context while the answer is a reply; what's tested while it's a meaning. */
    fun line(text: String): List<WordToken> = scenarioTaps(Reads.TARGET).words(text, reads)

    /** A scenario's scene: as its line. */
    fun scene(text: String): List<WordToken> = scenarioTaps(Reads.BASE).words(text, reads)

    private fun scenarioTaps(usual: Reads): Taps {
        val ex = exercise as? Exercise.Scenario ?: return Taps.NONE
        return if (answered || ex.options == null || options == Reads.TARGET) Taps(How.TAP, usual = usual, brackets = answered) else Taps.NONE
    }

    /** A speak exercise's model sentence, while it shows (shown, revealed by its hints, or answered). */
    fun model(text: String): List<WordToken> = Taps(How.TAP).words(text, reads)

    /** The feedback's right answer: in the language learned where the exercise answers in it. */
    fun correct(text: String): List<WordToken> = Taps(How.TAP, usual = if (answersInTarget) Reads.TARGET else Reads.BASE).words(text, reads)

    /** The feedback's whole sentence in the language learned. */
    fun sentence(text: String): List<WordToken> = Taps(How.TAP).words(text, reads)

    /**
     * A line of the feedback's explanation (after the answer): prose of both languages, so part by part (a quote, a bold
     * word, the halves of "sl · en") and word by word: every word of a part with no word of the base in it and half its
     * words or more in the language learned ("Grem v šolo.", "**dela**"; not "dela: no -m and no -š …", which has "and",
     * nor "genitive: koga? česa?"), and on its own a word of three letters or more that reads so (its letters, one of the
     * learner's: "delaš", "česa", not "so"); never an ending ("-m").
     */
    fun explanation(text: String, emphasized: List<IntRange> = emptyList()): List<WordToken> {
        val tokens = WordTokens.of(text)
        if (tokens.isEmpty()) return emptyList()
        val whole = BooleanArray(text.length)
        for (c in TextParts.chunks(text, emphasized)) for (p in c.pieces) {
            val each = WordTokens.of(p.of(text)).map { reads(it.text) }
            if (each.isNotEmpty() && Reads.BASE !in each && each.count { it == Reads.TARGET } * 2 >= each.size) for (i in p.start until p.end) whole[i] = true
        }
        return tokens.filter { t ->
            val ending = t.start > 0 && text[t.start - 1] == '-' && (t.start < 2 || !text[t.start - 2].isLetter())
            !ending && (whole[t.start] || (t.text.length >= 3 && reads(t.text) == Reads.TARGET))
        }
    }

    private val answersInTarget: Boolean
        get() = when (val ex = exercise) {
            is Exercise.Choice, is Exercise.Multi -> options == Reads.TARGET
            is Exercise.Scenario -> ex.options == null || options == Reads.TARGET
            is Exercise.Translate -> intoTarget
            is Exercise.Flashcard, is Exercise.Free, is Exercise.Unsupported -> false
            is Exercise.Cloze, is Exercise.Reorder, is Exercise.Dictation, is Exercise.Speak -> true
            is Exercise.GapText, is Exercise.Table, is Exercise.Transform, is Exercise.Sort, is Exercise.Counting -> true
        }

    /** Whether anything of the exercise as it shows can be looked up now (the first time, a tip says so). */
    val any: Boolean
        get() = when (val ex = exercise) {
            is Exercise.Choice -> prompt(ex.prompt).isNotEmpty() || ex.options.indices.any { option(it, ex.options[it]).isNotEmpty() }
            is Exercise.Multi -> prompt(ex.prompt).isNotEmpty() || ex.options.indices.any { option(it, ex.options[it]).isNotEmpty() }
            is Exercise.Cloze -> prompt(ex.text).isNotEmpty()
            is Exercise.Reorder -> prompt(ex.prompt).isNotEmpty() || (ex.tokens + ex.distractors).any { tile(it).isNotEmpty() }
            is Exercise.Translate -> prompt(ex.prompt).isNotEmpty()
            is Exercise.Free -> prompt(ex.prompt).isNotEmpty()
            is Exercise.Speak -> prompt(ex.prompt).isNotEmpty() || (ex.show && model(ex.say).isNotEmpty())
            is Exercise.Scenario -> scene(ex.scene).isNotEmpty() || ex.line?.let { line(it).isNotEmpty() } == true || prompt(ex.prompt).isNotEmpty() ||
                ex.options.orEmpty().let { o -> o.indices.any { option(it, o[it]).isNotEmpty() } }
            is Exercise.Flashcard -> front(ex.front, revealed = false).isNotEmpty()
            is Exercise.Dictation, is Exercise.Unsupported -> false
            is Exercise.GapText -> prompt(ex.text).isNotEmpty()
            is Exercise.Table -> ex.rows.any { r -> prompt(r.label).isNotEmpty() || r.cells.any { c -> c.given?.let { prompt(it).isNotEmpty() } == true } }
            is Exercise.Transform -> ex.items.any { prompt(it.from).isNotEmpty() }
            is Exercise.Sort -> ex.prompt?.let { prompt(it).isNotEmpty() } == true
            is Exercise.Counting -> false
        }

    companion object {
        /** A gap to fill: "___" (and longer, as shown). */
        val GAP = Regex("_{2,}")

        /**
         * What a text reads as, for a learner of [target] explained in [base]: the target's letters or frequent words
         * ([Spoken]), else the base's; a text the voices have a clip of ([voiced]) or whose words are all the learner's
         * own ([known]) reads as the target; else either.
         */
        fun reader(target: Lang, base: Lang, known: KnownWords? = null, voiced: (String) -> Boolean = { false }): (String) -> Reads = { s ->
            val words = WordTokens.of(s)
            when {
                words.isEmpty() -> Reads.UNSURE
                Spoken.looks(s, target, base) -> Reads.TARGET
                Spoken.looks(s, base, target) -> Reads.BASE
                voiced(s.trim()) -> Reads.TARGET
                known != null && words.all { known.knows(it.text) } -> Reads.TARGET
                else -> Reads.UNSURE
            }
        }
    }
}
