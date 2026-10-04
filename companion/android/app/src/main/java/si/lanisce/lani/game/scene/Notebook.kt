package si.lanisce.lani.game.scene

import java.time.LocalDate

/**
 * The learner's story notebook (companion/SCENES.md, "The story notebook"): "📓 Moj zvezek zgodb · My story notebook",
 * every story heard by the fire written down as the learner would have written it, listening: an entry a story (its
 * evenings, and the stories that go on from it, [StoryBooks.shelf]), in the order they were first heard, each evening
 * under the day it was heard ("28. septembra, ob ognju"), its pictures as pencil sketches. The text is the story's notes
 * ([StoryNote]: prose in the past tense, at the telling's level), or where a story has none (the tutor's, or one an older
 * bridge served without them) its telling written down: the teller's lines without his asides to the listener and
 * without the learner's turns ([prose]). Pure: it follows from the stories and what the learner has heard.
 */
object Notebook {
    /** Its entries: a story heard each ([StoryBooks.shelf]), in the order they were first heard (the rotation's, the same day). */
    fun entries(stories: List<Story>, heard: Map<String, StoryHeard>): List<NotebookEntry> =
        StoryBooks.shelf(stories, heard).map { b -> NotebookEntry(b, b.parts.map { dayOf(it, heard) }) }
            .withIndex()
            .sortedWith(compareBy({ it.value.first ?: LocalDate.MAX }, { it.index }))
            .map { it.value }

    /** The day [part] was heard: as recorded ([StoryHeard.days]), or counted back from the last one a village kept ([Stories.daysHeard]). */
    fun dayOf(part: BookPart, heard: Map<String, StoryHeard>): LocalDate? =
        if (part.heardAt.isEmpty()) null else Stories.daysHeard(part.story, heard[part.story.id])[part.chapter + 1]

    /**
     * [entry]'s evenings written at [level]: each heard at it at that level, one heard only at others at the highest of
     * those (it was written down so), one not heard yet locked. An evening of a story told in chapters is headed with the
     * chapter's title (else its number), a sequel's first with the sequel's title; the story's own one evening needs none.
     * [base]: the learner's base language (what the asides are looked for in).
     */
    fun evenings(entry: NotebookEntry, level: String, base: String): List<NoteEvening> {
        return entry.book.parts.mapIndexed { n, part ->
            val sequel = part.story.id != entry.book.story.id
            val title = part.part.title
                ?: part.story.title.takeIf { sequel && part.chapter == 0 }
                ?: "${part.chapter + 1}.".takeIf { part.story.parts.size > 1 }
            val at = if (level in part.heardAt) level else Stories.LEVELS.lastOrNull { it in part.heardAt }
            NoteEvening(part, n, title, entry.days.getOrNull(n), at, at?.let { blocks(part, it, base) }.orEmpty())
        }
    }

    /**
     * [part] at [level] as the notebook writes it: its paragraphs, and its pictures where they go. The notes' when the story
     * has them (a picture after the paragraph the note's `after` says, else the one its own `after` names, or the last);
     * else its telling written down ([prose]; a picture after what became of the paragraph it follows).
     */
    fun blocks(part: BookPart, level: String, base: String): List<NoteBlock> {
        val pictures = part.story.picturesOf(part.chapter)
        val note = part.story.noteOf(part.chapter, level)
        val (paragraphs, places) = if (note != null) {
            val n = note.text.size
            val after = if (note.after.size == pictures.size) note.after else pictures.map { it.after }
            note.text.map { NoteBlock.Text(it.sl, it.en) } to after.map { it.coerceIn(0, n) }
        } else {
            val telling = part.part.levels[level] ?: return emptyList()
            val p = prose(telling, part.story.language, base)
            p.paragraphs.map { NoteBlock.Text(it.sl, it.en) } to pictures.map { p.upTo[it.after.coerceIn(0, p.upTo.lastIndex)] }
        }
        val out = ArrayList<NoteBlock>()
        pictures.filterIndexed { i, _ -> places[i] == 0 }.forEach { out += NoteBlock.Sketch(it) }
        paragraphs.forEachIndexed { i, t ->
            out += t
            pictures.filterIndexed { k, _ -> places[k] == i + 1 }.forEach { out += NoteBlock.Sketch(it) }
        }
        return out
    }

    /** The level [entry] opens at for a learner at [level] ([StoryBooks.openAt]). */
    fun openAt(entry: NotebookEntry, level: String): String? = StoryBooks.openAt(entry.book, level)

    /**
     * A telling written down: its paragraphs (the teller's lines between two of the learner's turns, [StoryBooks.paragraphs];
     * the turns and the replies to them aren't written), each without the asides to the listener ([aside]: a greeting, a
     * question, his name or "fant", "sit by the fire", the story itself talked of), what is left in each language of the
     * line. [upTo]: for each paragraph count of the telling, how many written ones they became (a picture's place).
     */
    fun prose(t: StoryTelling, said: String, meant: String): Prose {
        val runs = StoryBooks.paragraphs(t)
        val out = ArrayList<DialogReply>()
        val upTo = IntArray(runs.size + 1)
        runs.forEachIndexed { i, run ->
            val a = ArrayList<String>()
            val b = ArrayList<String>()
            for (l in run) {
                val (x, y) = kept(l.sl.orEmpty(), l.en.orEmpty(), said, meant)
                a += x; b += y
            }
            if (a.isNotEmpty()) out += DialogReply(a.joinToString(" "), b.joinToString(" "))
            upTo[i + 1] = out.size
        }
        return Prose(out, upTo)
    }

    /** What is left of a line ([said] in [lang], [meant] in [base]): its sentences that aren't asides, in each (the road's recaps too). */
    internal fun kept(said: String, meant: String, lang: String, base: String): Pair<List<String>, List<String>> {
        val a = StoryBooks.sentences(said)
        val b = StoryBooks.sentences(meant)
        val keptA = a.map { clean(it, lang) }
        if (a.size == b.size) {
            val pairs = keptA.zip(b).filter { it.first != null }
            return pairs.map { it.first!! } to pairs.map { (_, m) -> clean(m, base) ?: m }
        }
        return keptA.filterNotNull() to b.mapNotNull { clean(it, base) }
    }

    /** [sentence] as written down: null when it's an aside ([aside]), else without an aside it opens with ("Veš, …"). */
    fun clean(sentence: String, lang: String): String? {
        val s = sentence.trim()
        val lead = LEADS[lang]?.find(s)
        val rest = if (lead != null) s.substring(lead.range.last + 1).trim().replaceFirstChar { it.uppercase() } else s
        return rest.takeIf { it.isNotEmpty() && !aside(it, lang) }
    }

    /**
     * Whether [sentence] (in [lang]) is the teller talking to the listener rather than telling: a question, a greeting,
     * the listener named or called ("fant", "piccolo", "lad"), told to sit, listen or say, or the story itself talked of
     * ("Nocoj ti povem zgodbo"). What is said inside quotes is the story's.
     */
    fun aside(sentence: String, lang: String): Boolean {
        val outside = unquoted(sentence, lang).trim()
        if (outside.isEmpty()) return false
        if (outside.trimEnd(' ', '.', '!', '…', ':', ',', '«', '»', '"', '“', '”').endsWith('?') || outside.contains("?")) return true
        val low = outside.lowercase()
        if (NAMES.any { it.containsMatchIn(outside) }) return true
        return ASIDES[lang].orEmpty().any { it.containsMatchIn(low) } || VOCATIVES[lang]?.containsMatchIn(outside) == true
    }

    /**
     * [sentence] without what is said in it in quotes (»…«, „…“, «…», "…", '…': the story's own), also a quote it only
     * opens or only closes (speech that goes on over several sentences).
     */
    private fun unquoted(sentence: String, lang: String): String {
        val s = QUOTED.replace(sentence, " ")
        val close = (if (lang == "en") CLOSE_EN.findAll(s).lastOrNull()?.range?.last else CLOSES[lang]?.let { s.lastIndexOf(it) }?.takeIf { it >= 0 }) ?: -1
        val rest = s.substring(close + 1)
        val open = (if (lang == "en") OPEN_EN.find(rest)?.range?.first else OPENS[lang]?.let { rest.indexOf(it) }?.takeIf { it >= 0 }) ?: rest.length
        return rest.substring(0, open)
    }

    /** Quoted speech (»…«, „…“, «…», "…", '…'). */
    private val QUOTED = Regex("»[^«]*«|„[^“”]*[“”]|«[^»]*»|\"[^\"]*\"|“[^”]*”|(?<=^|[\\s(])'[^']*'(?=[\\s.,;:!?)]|$)")

    /** How a quote opens and closes in each language's stories ("farmer's" is no quote). */
    private val OPENS = mapOf("sl" to '»', "it" to '«', "de" to '„')
    private val CLOSES = mapOf("sl" to '«', "it" to '»', "de" to '“')
    private val OPEN_EN = Regex("(?<=^|[\\s(])['“\"]")
    private val CLOSE_EN = Regex("(?<=\\S)['”\"](?=[\\s.,;:!?)]|$)")

    /**
     * [phrase] as a whole word (a letter on neither side; "…*": the start of words, "zgodb*" for zgodba, zgodbo …). Spelled
     * out, since `\b` knows no č in every regex engine.
     */
    private fun word(phrase: String): Regex =
        if (phrase.endsWith("*")) Regex("(?<![\\p{L}\\p{N}])${phrase.dropLast(1)}") else Regex("(?<![\\p{L}\\p{N}])$phrase(?![\\p{L}\\p{N}])")

    /** The learner the tellings talk to. */
    private val NAMES = listOf(word("Jan"))

    /** An aside a sentence opens with: "Veš, …", "Sai, …", "Weißt du, …", "You know, …". */
    private val LEADS = mapOf(
        "sl" to Regex("^(Veš|Vidiš|Glej|Poglej|Poslušaj|Saj veš|No)[,:]\\s*"),
        "it" to Regex("^(Sai|Vedi|Guarda|Senti|Ascolta|Ecco)[,:]\\s*"),
        "de" to Regex("^(Weißt du|Stell dir vor|Schau|Hör zu|Siehst du|Na)[,:]\\s*"),
        "en" to Regex("^(You know|You see|Listen|Look|Mind you|Well)[,:]\\s*"),
    )

    /** The listener called by a name the tellings give him: at a comma, a colon or the sentence's edge. */
    private val VOCATIVES = mapOf(
        "sl" to Regex("(^|[,:;!]\\s*)(fant|fantič|mali|otrok|prijatelj)\\s*([,:;!.]|$)", RegexOption.IGNORE_CASE),
        "it" to Regex("(^|[,:;!]\\s*)(piccol[oa]|ragazz[oa]|car[oa]|bambin[oa])\\s*([,:;!.]|$)", RegexOption.IGNORE_CASE),
        "de" to Regex("(^|[,:;!]\\s*)(Kleiner|Kleine|Junge|mein Junge|Bub|Bursch)\\s*([,:;!.]|$)"),
        "en" to Regex("(^|[,:;!]\\s*)(lad|son|my boy|boy|little one|young one)\\s*([,:;!.]|$)", RegexOption.IGNORE_CASE),
    )

    /** What a teller says to the listener, not of the story: the greetings, "sit", "listen", "tell me", tonight and tomorrow, the story itself. */
    private val ASIDES: Map<String, List<Regex>> = mapOf(
        "sl" to listOf(
            "dober večer", "dobro jutro", "dober dan", "lahko noč", "živjo", "zdravo", "sedi", "sedite", "poslušaj", "povej*",
            "poglej", "misliš", "veš", "vidiš", "ti povem", "ti bom", "boš videl", "nocoj", "jutri zvečer", "zgodb*", "pripovedk*",
        ),
        "it" to listOf(
            "buonasera", "buongiorno", "buonanotte", "ciao", "siediti", "ascolta", "senti", "dimmi", "sai", "ti racconto",
            "ti dico", "stasera", "domani sera", "storia", "storie", "leggend*", "racconto",
        ),
        "de" to listOf(
            "guten abend", "guten morgen", "gute nacht", "hallo", "servus", "setz dich", "hör zu", "sag mal", "sag mir",
            "weißt du", "stell dir vor", "du", "dir", "dich", "heute abend", "morgen abend", "geschichte*", "märchen",
        ),
        "en" to listOf(
            "good evening", "good morning", "good night", "hello", "sit down", "sit by", "listen", "tell me", "you know",
            "you", "your", "tonight", "tomorrow night", "story", "stories", "tale", "legend*",
        ),
    ).mapValues { (_, l) -> l.map(::word) }
}

/** A telling written down ([Notebook.prose]): its [paragraphs], and [upTo]: of the telling's first n paragraphs, how many written ones. */
class Prose(val paragraphs: List<DialogReply>, val upTo: IntArray)

/** A story in the notebook: its [book] (the story's evenings and those going on from it), and the [days] each was heard (null: not yet, or not known). */
data class NotebookEntry(val book: StoryBook, val days: List<LocalDate?>) {
    val id: String get() = book.id
    val story: Story get() = book.story

    /** The day it was first heard. */
    val first: LocalDate? get() = days.filterNotNull().minOrNull()
}

/**
 * An evening of a notebook entry: its [part], its [number] (from 0), its [title] (in a story of several evenings), the
 * [day] it was heard, the [level] it is written at and its [blocks]; [locked] while it isn't heard yet.
 */
data class NoteEvening(val part: BookPart, val number: Int, val title: String?, val day: LocalDate?, val level: String?, val blocks: List<NoteBlock>) {
    val locked: Boolean get() = level == null
}

/** What an evening of the notebook holds: a paragraph written down, or a sketch. */
sealed interface NoteBlock {
    /** A paragraph: what is written ([said], in the story's language) and what it means ([meant]). */
    data class Text(val said: String, val meant: String) : NoteBlock

    data class Sketch(val picture: StoryPicture) : NoteBlock
}
