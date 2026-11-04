package si.lanisce.lani.data

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

// --- a word's forms: GET /forms (companion/bridge, src/forms.ts; companion/README.md "Word forms") ---------------------

/**
 * A line of the content where a form stands as its lemma (a scene's, a story's, a reading's, a grammar page's example, a
 * pack word's example): the sentence in the language learned ([said]: Slovene's in [sl], another language's in [text]),
 * its translations, the [form] as it stands there, and where it is from ("scene:park"). Another language's line
 * (paradigms.ts) has its Slovene translation in [sl], for a learner who reads Slovene.
 */
@Serializable
data class FormLine(
    val sl: String = "",
    val en: String = "",
    val de: String? = null,
    val it: String? = null,
    val form: String,
    val from: String? = null,
    val text: String? = null,
) {
    /** The sentence in the language learned. */
    val said: String get() = text ?: sl

    /** The translation in [base] when the content has it, else the English (none for an English line: it is the line). */
    fun meantIn(base: String): String = when (base) {
        "de" -> de
        "it" -> it
        "sl" -> sl.takeIf { text != null }
        else -> null
    }?.takeIf { it.isNotBlank() && it != said } ?: en.takeIf { it != said }.orEmpty()
}

/**
 * One form of a lemma ([key]: "pres.1sg", "past.f.sg", "loc.pl", "adj.f.sg"; companion/README.md, "Word forms"): its
 * standard [forms] (the first is shown as the answer; all are right), the spoken or variant ones also accepted ([also];
 * of them, the dictionary's old or dialect forms: [variants], brati beside bratu), the dictionary's [grammar], the grammar
 * book's [page] whose rule it is (a wrong form counts there), the other pages it [needs] introduced too, and real [lines]
 * where it stands. [checked]: the forms are sure (a hand-checked table's, or a noun's slot that fits its regular
 * declension: bridge/src/noun-paradigm.ts); only such a slot is asked as a form to produce from nothing ("Sklanjaj!").
 */
@Serializable
data class FormSlot(
    val key: String,
    val forms: List<String>,
    val also: List<String> = emptyList(),
    val grammar: String? = null,
    val page: String,
    val needs: List<String> = emptyList(),
    val lines: List<FormLine> = emptyList(),
    val variants: List<String> = emptyList(),
    val checked: Boolean = false,
) {
    /** Every page the slot needs introduced: its own, then [needs]. */
    val pages: List<String> get() = listOf(page) + needs
}

/** An aspect partner (sesti ↔ sedeti): its lemma, aspect ("pf", "impf"), the [kind] of pair ("position", "aspect") and gloss. */
@Serializable
data class FormPartner(val lemma: String, val aspect: String? = null, val kind: String = "aspect", val gloss: List<String> = emptyList())

/**
 * A lemma's forms: its part of speech, English [gloss], a noun's [gender], a verb's [aspect] and [partner] where the
 * bridge knows them, whose [table] they are ("wiktionary", or "checked": a hand-checked table of an irregular word), and
 * its [slots] in a fixed order.
 */
@Serializable
data class LemmaForms(
    val lemma: String,
    val pos: String,
    val gloss: List<String> = emptyList(),
    val gender: String? = null,
    val aspect: String? = null,
    val partner: FormPartner? = null,
    val table: String = "wiktionary",
    val slots: List<FormSlot> = emptyList(),
    /** Another language's table (Italian, German, English: their own slots, game/OtherForms); null: Slovene's. */
    val language: String? = null,
    /** A verb's auxiliary in the compound past (avere, essere; haben, sein), the usual first. */
    val aux: List<String> = emptyList(),
) {
    fun slot(key: String): FormSlot? = slots.firstOrNull { it.key == key }
}

/** A word asked about and the dictionary's lemmas it is (none: not a lemma the dictionary knows, or no forms). */
@Serializable
data class FormsWord(val word: String, val entries: List<LemmaForms> = emptyList())

/** GET /forms: the forms of each word asked about, and the attribution Wiktionary's tables carry. */
@Serializable
data class FormsAnswer(val language: String = "", val words: List<FormsWord> = emptyList(), val attribution: String? = null)

/** The pure parts of asking the bridge for words' forms. */
object WordFormsWire {
    /** The most words one GET /forms asks about. */
    const val BATCH = 40

    /** The forms the app reads: 2 has Italian's, German's and English's tables too (an older bridge ignores it). */
    const val VERSION = 2

    /** `/forms?w=sesti&w=hiša&v=2`. */
    fun url(base: String, words: List<String>): HttpUrl =
        (base.trimEnd('/') + "/forms").toHttpUrl().newBuilder().apply {
            words.forEach { addQueryParameter("w", it) }
            addQueryParameter("v", VERSION.toString())
        }.build()

    fun parse(raw: String): FormsAnswer = json.decodeFromString(FormsAnswer.serializer(), raw)

    /**
     * The lemma a card is about, as GET /forms is asked: its front when it is one word ("sesti", "hiša"), else null (a
     * phrase, a rule, a placeholder: no forms).
     */
    fun lemmaOf(card: ReviewCard, language: String = "sl"): String? {
        if (card.kind != "vocabulary") return null
        val w = bare(card.front.trim(), language).first
        return w.takeIf { it.length in 2..40 && WORD.matches(it) }
    }

    /**
     * The part of speech a card's front says of its word (the article of "la casa", "der Wald": a noun; English's "to"
     * of "to run": a verb), else null; Slovene's say none.
     */
    fun posOf(card: ReviewCard, language: String = "sl"): String? = if (card.kind != "vocabulary") null else bare(card.front.trim(), language).second

    /** [word] as GET /forms is asked it: without an article or a "to" ("das Haus" → Haus; Slovene's as it is). */
    fun bareOf(word: String, language: String): String = bare(word.trim(), language).first

    /** Italian's and German's articles before a noun. */
    private val ARTICLES = mapOf(
        "it" to setOf("il", "lo", "la", "i", "gli", "le", "un", "uno", "una"),
        "de" to setOf("der", "die", "das", "ein", "eine"),
    )
    private val ELIDED = Regex("^(?:l|un)['’](\\p{L}+)$")

    /** [front] without its article or its "to" ("la casa" → casa, a noun; "l'acqua" → acqua; "to run" → run, a verb). */
    private fun bare(front: String, language: String): Pair<String, String?> {
        if (language == "it") ELIDED.find(front)?.let { return it.groupValues[1] to "noun" }
        val parts = front.split(' ')
        if (parts.size == 2) {
            if (parts[0].lowercase() in ARTICLES[language].orEmpty()) return parts[1] to "noun"
            if (language == "en" && parts[0].lowercase() == "to") return parts[1] to "verb"
        }
        return front to null
    }

    /** The key a lemma is cached under: lower case. */
    fun key(lemma: String): String = lemma.trim().lowercase()

    private val WORD = Regex("""\p{L}+""")

    /**
     * The entry of [entries] a card meaning [meaning] is about: the one whose gloss shares the most words with it (the noun
     * "dan", day, not another reading), else the first. Null when there is none.
     */
    fun entryFor(entries: List<LemmaForms>, meaning: String, pos: String? = null): LemmaForms? {
        val of = pos?.let { p -> entries.filter { it.pos == p } }?.takeIf { it.isNotEmpty() } ?: entries
        if (of.size <= 1) return of.firstOrNull()
        val meant = words(meaning)
        return of.withIndex().maxByOrNull { (i, e) -> words(e.gloss.joinToString(" ")).count { it in meant } * 100 - i }?.value
    }

    private val STOP = setOf("to", "the", "a", "an", "of", "be", "or", "and")
    private fun words(s: String): Set<String> =
        Regex("""\p{L}+""").findAll(s.lowercase()).map { it.value }.filter { it.length > 1 && it !in STOP }.toSet()
}
