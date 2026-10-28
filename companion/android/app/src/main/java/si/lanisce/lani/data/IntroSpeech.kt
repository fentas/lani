package si.lanisce.lani.data

/**
 * How a step of a unit's guided introduction is read aloud (companion/COURSE.md, "Read aloud"): the teacher's explanation
 * ([IntroStep.say], Markdown in the learner's base language with the Slovene forms marked) as one clip of the voice store
 * when the node made it ([plain], [key]: the explanation voiced in one go, in the teacher's voice), else in runs ([runs]):
 * the base-language parts by the phone's voice, the Slovene parts (in bold, italics or „quotes“ that [Speaker.isSlovene]
 * says are Slovene, as the chat's Markdown taps them) in the teacher's voice, at their place; then the step's examples;
 * then the quick question as far as it gives nothing away ([question]), and once it is answered the sentence with the gap
 * filled ([answer]). Pure. The node reads the same to voice it ahead in the teacher's voice (companion/bridge/src/
 * course-speech.ts, a port of this): bridge/test/fixtures/intro-speech.json holds what both read, checked here by
 * IntroSpeechParityTest and there by the smoke, so the two can't drift.
 *
 * Where a run is shown: the line of the Markdown it is on (its index in `text.trim().lines()`, as [si.lanisce.lani.ui.Markdown]
 * walks them) and its segments there ([segments]: the plain text and the spans of the line's inline Markdown, in order), so
 * the bubble marks what is being read.
 */
object IntroSpeech {
    /** The inline Markdown the app draws (ui/Markdown.kt): **bold**, *italic*, `code`, „quotes“. */
    val INLINE = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`|„(.+?)[“”]""")

    /** A piece of a line's inline Markdown: plain text, or a span ([kind]); [body] its text without the marks. */
    data class Segment(val body: String, val kind: Kind) {
        enum class Kind { PLAIN, BOLD, ITALIC, CODE, QUOTED }

        /** Bold, italic or quoted: what the app looks at for Slovene ([Speaker.isSlovene]). */
        val span: Boolean get() = kind == Kind.BOLD || kind == Kind.ITALIC || kind == Kind.QUOTED
    }

    /**
     * A run read in one voice: [text] as said, [slovene] (the teacher's voice) or in the learner's base language (the
     * phone's), shown on source [line] as its [segments].
     */
    data class Run(val text: String, val slovene: Boolean, val line: Int, val segments: IntRange)

    /** [s]'s inline Markdown in its segments, in order: the plain text between the spans (when not empty) and each span. */
    fun segments(s: String): List<Segment> {
        val out = mutableListOf<Segment>()
        var i = 0
        for (m in INLINE.findAll(s)) {
            if (m.range.first > i) out += Segment(s.substring(i, m.range.first), Segment.Kind.PLAIN)
            val (bold, italic, code, quoted) = m.destructured
            out += when {
                bold.isNotEmpty() -> Segment(bold, Segment.Kind.BOLD)
                italic.isNotEmpty() -> Segment(italic, Segment.Kind.ITALIC)
                code.isNotEmpty() -> Segment(code, Segment.Kind.CODE)
                else -> Segment(quoted, Segment.Kind.QUOTED)
            }
            i = m.range.last + 1
        }
        if (i < s.length) out += Segment(s.substring(i), Segment.Kind.PLAIN)
        return out
    }

    /**
     * The lines of [markdown] that are read, by their index in `markdown.trim().lines()`, each as the Markdown draws its
     * text: a heading without its #, a quote without its >, a list item without its bullet or number. Blank lines, rules
     * and tables aren't read (the step's own table is shown beside it).
     */
    fun lines(markdown: String): List<Pair<Int, String>> {
        val lines = markdown.trim().lines()
        val out = mutableListOf<Pair<Int, String>>()
        for ((i, raw) in lines.withIndex()) {
            val t = raw.trimEnd()
            val text = when {
                t.isBlank() -> continue
                t.trimStart().startsWith("|") -> continue
                RULE.matches(t.trim()) -> continue
                t.startsWith("#") -> t.trimStart('#', ' ')
                t.startsWith(">") -> t.removePrefix(">").trim()
                t.startsWith("- ") || t.startsWith("• ") || t.startsWith("* ") -> t.drop(2)
                NUMBERED.matches(t) -> NUMBERED.find(t)!!.groupValues[2]
                else -> t
            }
            if (text.isNotBlank()) out += i to text
        }
        return out
    }

    /**
     * [markdown] in the runs it is read in, in order: a span [isSlovene] says is Slovene on its own (its text without the
     * marks), what lies between such spans together in the base language (a translation in brackets right after a
     * Slovene form, "(I am)", among it); a run with nothing to say (", " between two forms) is left out.
     */
    fun runs(markdown: String, isSlovene: (String) -> Boolean): List<Run> {
        val out = mutableListOf<Run>()
        for ((line, text) in lines(markdown)) {
            val segs = segments(text)
            var from = -1
            val base = StringBuilder()
            fun flush(to: Int) {
                if (from < 0) return
                said(base.toString())?.let { out += Run(it, false, line, from..to) }
                from = -1
                base.clear()
            }
            segs.forEachIndexed { k, s ->
                val body = bare(s.body)
                if (s.span && isSlovene(body)) {
                    flush(k - 1)
                    said(body)?.let { out += Run(it, true, line, k..k) }
                } else {
                    if (from < 0) from = k
                    base.append(if (s.kind == Segment.Kind.PLAIN) s.body else body)
                }
            }
            flush(segs.lastIndex)
        }
        return out
    }

    /**
     * The explanation as the node voices it in one go: each line's text without its marks (the Slovene forms left inside
     * as they are), without emoji, a line each.
     */
    fun plain(markdown: String): String =
        lines(markdown).mapNotNull { (_, t) -> said(segments(t).joinToString("") { if (it.kind == Segment.Kind.PLAIN) it.body else bare(it.body) }) }
            .joinToString("\n")

    /**
     * The voice store's key of an explanation in [lang] ([plain]): the language and its text as the store keys a line
     * ([Voice.normalize] has no "|": never a Slovene line's key). The node's index lists it so ("en|biti to be has …").
     */
    fun key(lang: String, text: String): String = "$lang|${Voice.normalize(text)}"

    /**
     * A quick question's prompt as read before it is answered, giving nothing away: the sentence with its gap as a pause
     * ("Luka … na pašniku."), without a translation in brackets at its end; a choice's options aren't read. Null when
     * there's nothing to read (another kind of question).
     */
    fun question(ask: Exercise): String? {
        val text = when (ask) {
            is Exercise.Choice -> ask.prompt
            is Exercise.Cloze -> ask.text
            else -> return null
        }
        return said(text.replace(Sheets.GAP, "…").replace(TRAILING_BRACKETS, "").trim())
    }

    /**
     * The words of the language learned that [intro] shows, lower case: its examples in [language], its table's words (its
     * head's in [language] too), its questions' Slovene ([isSlovene]: a prompt in the base adds nothing), the unit's [title]
     * in [language] and its [words] (the unit's `words`: an infinitive or an ending its explanation names, "**cantare**"). A
     * span of only these is Slovene ([within]) though the app's check alone wouldn't say so ("**Biti**" of the unit "Biti:
     * sem, si, je").
     */
    fun vocabulary(intro: Intro, language: String, title: String? = null, isSlovene: (String) -> Boolean = { false }, words: List<String> = emptyList()): Set<String> {
        val texts = buildList {
            title?.let(::add)
            addAll(words)
            intro.head.forEach { c -> (c.words ?: c.text?.get(language))?.let(::add) }
            for (s in intro.steps) {
                s.examples.forEach { add(it.target(language)) }
                s.rows.flatten().filter { it.isWords }.forEach { add(it.words!!) }
                when (val a = s.ask) {
                    is Exercise.Choice -> {
                        a.prompt.replace(Sheets.GAP, " ").takeIf(isSlovene)?.let(::add)
                        a.options.filter { a.sayOptions || isSlovene(it) }.forEach(::add)
                    }
                    is Exercise.Cloze -> {
                        a.text.replace(Sheets.GAP, " ").takeIf(isSlovene)?.let(::add)
                        addAll(a.accept)
                    }
                    else -> Unit
                }
            }
        }
        return texts.flatMapTo(HashSet()) { t -> WORD.findAll(t.lowercase()).map { it.value } }
    }

    /** [text] has words, and all of them are [known] ones ([vocabulary]). */
    fun within(text: String, known: Set<String>): Boolean {
        val words = WORD.findAll(text.lowercase()).map { it.value }.toList()
        return words.isNotEmpty() && words.all { it in known }
    }

    private val WORD = Regex("""\p{L}+""")

    // --- a step's script ----------------------------------------------------------------------------------------------

    /** What is being read, for the step to mark it. */
    sealed interface Mark {
        /** The explanation as a whole: its one clip. */
        data object Whole : Mark

        /** A run of the explanation: its source line and segments there ([Run]). */
        data class Part(val line: Int, val segments: IntRange) : Mark

        /** The step's example [index]. */
        data class Example(val index: Int) : Mark

        /** The quick question. */
        data object Question : Mark

        /** The sentence of the answer, after it was given. */
        data object Answer : Mark
    }

    /** A line read in one voice: [text], [slovene] (the teacher's voice) or in the learner's base (the phone's), [mark]. */
    data class Line(val text: String, val slovene: Boolean, val mark: Mark)

    /**
     * The explanation of a step: [plain] (in [lang], as the node voices it in one go) and its [runs], what is read in its
     * stead when its clip can't be had.
     */
    data class Explanation(val plain: String, val lang: String, val runs: List<Run>)

    /** What a step reads, in order: its [explanation], then its [lines] (the examples, the quick question). [id]: whose. */
    data class Script(val id: String, val explanation: Explanation?, val lines: List<Line>) {
        /** The explanation's runs as lines, as they're read without its clip. */
        val stitched: List<Line> get() = explanation?.runs.orEmpty().map { Line(it.text, it.slovene, Mark.Part(it.line, it.segments)) }
    }

    /**
     * What a step reads when it appears: the explanation [say] (in [base]), its [examples] in the language learned (their
     * translations not read), then its quick question as far as it gives nothing away ([questionLines]).
     */
    fun step(id: String, say: String, base: String, examples: List<String>, ask: Exercise?, isSlovene: (String) -> Boolean): Script {
        val plain = plain(say)
        return Script(
            id,
            Explanation(plain, base, runs(say, isSlovene)).takeIf { plain.isNotEmpty() },
            examples.mapIndexedNotNull { i, e -> said(e)?.let { Line(it, true, Mark.Example(i)) } } + questionLines(ask, isSlovene),
        )
    }

    /**
     * What step [i] of [intro] reads (ui/school/IntroScreen, the day's audio): its explanation in [base] ([Grammar.text]: the
     * base's, else the English), its examples in [language], its quick question; Slovene what [isSlovene] (the app's check,
     * [Speaker.isSlovene]) says is, and the words the intro itself shows ([vocabulary], [within]: "**Biti**"). [id]: whose.
     */
    fun introStep(id: String, intro: Intro, i: Int, language: String, base: String, title: String?, isSlovene: (String) -> Boolean, words: List<String> = emptyList()): Script {
        val s = intro.steps[i]
        val known = vocabulary(intro, language, title, isSlovene, words)
        return step(id, Grammar.text(s.say, base), base, s.examples.map { it.target(language) }, s.ask) { t -> isSlovene(t) || within(t, known) }
    }

    /**
     * What the summary at an intro's end reads: the unit's grammar page's [rule] in [base] (its table is to look at), else
     * the unit's [goal]; null with neither.
     */
    fun summarySay(rule: Map<String, String>?, goal: Map<String, String>?, base: String): String? =
        rule?.let { Grammar.text(it, base) }?.takeIf { it.isNotBlank() } ?: goal?.let { Grammar.text(it, base) }?.takeIf { it.isNotBlank() }

    /** The summary read as a step is ([summarySay]: its explanation, nothing else), the intro's words Slovene as in its steps. */
    fun summary(id: String, say: String, base: String, intro: Intro, language: String, title: String?, isSlovene: (String) -> Boolean, words: List<String> = emptyList()): Script {
        val known = vocabulary(intro, language, title, isSlovene, words)
        return step(id, say, base, emptyList(), null) { t -> isSlovene(t) || within(t, known) }
    }

    /** The sentence of a quick question's answer, read once it is answered: its prompt with the gap filled with the right answer. */
    fun answer(ask: Exercise?): String? = when (ask) {
        is Exercise.Choice -> gapFilled(ask.prompt, ask.options.getOrNull(ask.answer))
        is Exercise.Cloze -> gapFilled(ask.text, ask.accept.firstOrNull())
        else -> null
    }

    /** A sentence with its gap filled with [answer], without a translation in brackets at its end; null when there's no gap. */
    fun gapFilled(text: String, answer: String?): String? {
        if (answer == null || !Sheets.GAP.containsMatchIn(text)) return null
        return text.replace(Sheets.GAP, answer).replace(TRAILING_BRACKETS, "").trim()
    }

    /**
     * A quick question as read before it's answered: its instruction ([instructionLines]), then its prompt with the gap a
     * pause ([question]), in the teacher's voice when it is in the language learned (a sentence with a gap to fill and no
     * Markdown, a word named in »quotes«, or what [isSlovene] says is), else in its runs (a question in the base marks its
     * Slovene: "Which form goes with **mi**? ___"). Nothing for another kind of question.
     */
    fun questionLines(ask: Exercise?, isSlovene: (String) -> Boolean): List<Line> {
        val q = ask?.let(::question) ?: return emptyList()
        val (instruction, gapped) = when (ask) {
            is Exercise.Choice -> ask.instruction to Sheets.GAP.containsMatchIn(ask.prompt)
            is Exercise.Cloze -> ask.instruction to true
            else -> null to false
        }
        val first = instruction?.let { instructionLines(it, isSlovene) }.orEmpty()
        val learned = (gapped && !INLINE.containsMatchIn(q)) || NAMED.matches(q) || isSlovene(q)
        val prompt = if (learned) listOf(Line(q, true, Mark.Question)) else runs(q, isSlovene).map { Line(it.text, it.slovene, Mark.Question) }
        return first + prompt
    }

    /**
     * A quick question's instruction as read: a label as the app writes them, the language learned and then the base
     * ("Kakšnega spola? · Which gender?", no Markdown), its first part in the teacher's voice and its second in the base;
     * else in its runs.
     */
    fun instructionLines(text: String, isSlovene: (String) -> Boolean): List<Line> {
        val label = LABEL.matchEntire(text)?.takeIf { !INLINE.containsMatchIn(text) }
        if (label != null) {
            return listOfNotNull(
                said(label.groupValues[1])?.let { Line(it, true, Mark.Question) },
                said(label.groupValues[2])?.let { Line(it, false, Mark.Question) },
            )
        }
        return runs(text, isSlovene).map { Line(it.text, it.slovene, Mark.Question) }
    }

    /** A Markdown body without the marks of the spans inside it. */
    private fun bare(s: String): String = s.replace(INLINE) { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty() }

    /**
     * [s] as a voice says it: emoji and other symbols out (the phone's voice would name them), an arrow a pause,
     * whitespace one space; null when nothing is left to say.
     */
    private fun said(s: String): String? {
        val t = buildString {
            for (ch in s.replace("→", ", ")) {
                val type = Character.getType(ch)
                if (type == Character.OTHER_SYMBOL.toInt() || ch == '️' || ch == '‍' || type == Character.SURROGATE.toInt()) continue
                append(ch)
            }
        }.replace(SPACE, " ").trim()
        return t.takeIf { it.any(Char::isLetterOrDigit) }
    }

    private val RULE = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
    private val NUMBERED = Regex("""^(\d+)[.)]\s+(.*)""")
    private val SPACE = Regex("""\s+""")
    private val TRAILING_BRACKETS = Regex("""\s*\([^()]*\)\s*$""")

    /** A label: the language learned, then the base ("Izberi · Pick"). */
    private val LABEL = Regex("""([^·]+) · ([^·]+)""")

    /** A word or phrase named in »quotes« (»smreka«, the prompt of "which gender?"). */
    private val NAMED = Regex("""»[^»«]+«""")
}
