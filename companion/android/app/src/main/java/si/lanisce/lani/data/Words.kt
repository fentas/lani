package si.lanisce.lani.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi

// --- a word looked up: GET /lookup and POST /words (companion/bridge) ---------------------------

@Serializable
data class WordExample(val sl: String, val en: String = "")

/**
 * One meaning of a tapped word: its dictionary form ([lemma]), part of speech, [gloss]es (in [glossLang]: the learner's
 * base when the pack or the dictionary has it, else English with [fallback]; [glossVia] "claude": machine-written),
 * what this form is ([grammar], e.g. "locative singular"), the noun's [gender] (m | f | n), where the entry came from
 * ([source]: pack, wiktionary, extra, tutor, village), the learner's review item when there is one, and whether they
 * know it already.
 */
@Serializable
data class WordEntry(
    val lemma: String,
    val pos: String? = null,
    val gloss: List<String> = emptyList(),
    /** The language of [gloss]; null from a node before languages (English then, or the pack's base). */
    @SerialName("gloss_lang") val glossLang: String? = null,
    /**
     * How a gloss in the base language was found: "direct" (Wiktionary in the base), "translations" (a table between
     * the two languages), "pivot" (through English) or "claude" (the tutor wrote it: machine-written).
     */
    @SerialName("gloss_via") val glossVia: String? = null,
    /** The English a gloss in the base stands for. */
    @SerialName("gloss_en") val glossEn: List<String> = emptyList(),
    /** The node has no meaning in the learner's base: [gloss] is English (the tutor is asked for one). */
    val fallback: Boolean = false,
    val grammar: String? = null,
    val gender: String? = null,
    val source: String? = null,
    @SerialName("item_id") val itemId: String? = null,
    val known: Boolean = false,
    val example: WordExample? = null,
    val emoji: String? = null,
    /** A verb's aspect, "pf" or "impf", where the bridge knows it (its partners' list); null from an older one. */
    val aspect: String? = null,
    /** Its aspect partner (sesti ↔ sedeti, kupiti ↔ kupovati), where the bridge knows one. */
    val partner: FormPartner? = null,
    /** The entry the line means, by its translation, when its partner is among the entries too ("tukaj · here"). */
    val here: Boolean = false,
) {
    /** Tells entries of one lookup apart: the same lemma can be a noun and a verb. */
    val key: String get() = "$lemma|${pos.orEmpty()}"
}

/** GET /lookup: the form asked about and its meanings; no entries means the dictionary doesn't know it. */
@Serializable
data class WordLookup(val word: String, val entries: List<WordEntry> = emptyList(), val attribution: String? = null)

/** The pure parts of looking a word up and adding it to the learner's words. */
object Words {
    /** Where a word was tapped, for POST /words' `from`. */
    const val SCENE = "scene"
    const val ROLEPLAY = "roleplay"
    const val VILLAGER = "villager"
    const val CHAT = "chat"
    /** A reading: the reading corner's, a book from the chest, a story's. Only to a bridge that takes it ([source]). */
    const val READING = "reading"

    /** What every bridge takes as `from` (an older one refuses the rest with a 400). */
    val OLD_SOURCES = setOf(SCENE, ROLEPLAY, VILLAGER, CHAT)

    /**
     * The `from` to send for a word added in [from]: itself when the bridge takes it ([accepted], GET /words; null for a
     * bridge that doesn't say, an older one), else [VILLAGER], as a reading's words were sent before.
     */
    fun source(from: String, accepted: Set<String>?): String = if (from in (accepted ?: OLD_SOURCES)) from else VILLAGER

    /** GET /words: the origins the bridge takes as `from`. */
    fun parseSources(raw: String): Set<String> =
        ((json.parseToJsonElement(raw) as? JsonObject)?.get("from") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }.toSet()

    private const val MAX_LINE = 300

    /**
     * `/lookup?w=<form>&line=<the line>&en=<its translation>`: the line lets the node pick the meaning meant there. On a
     * visit, [language] is the town's (the node looks the word up in that language's dictionary); [meaningIn] is the
     * language the meanings are wanted in (the pair's base), left out when it is the word's own.
     */
    fun url(base: String, word: String, line: String?, en: String?, language: String? = null, meaningIn: String? = null): HttpUrl =
        (base.trimEnd('/') + "/lookup").toHttpUrl().newBuilder()
            .addQueryParameter("w", word)
            .apply {
                line?.trim()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("line", it.take(MAX_LINE)) }
                en?.trim()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("en", it.take(MAX_LINE)) }
                language?.let { addQueryParameter("language", it) }
                meaningIn?.takeIf { it != language }?.let { addQueryParameter("base", it) }
            }
            .build()

    fun parse(raw: String): WordLookup = json.decodeFromString(WordLookup.serializer(), raw)

    /**
     * The entries a word card shows, each with the partner entry it carries under it (sesti with sedeti for "sedite"): an
     * entry whose aspect partner comes earlier in [entries] goes under that one, not as an entry of its own, so the card
     * shows the meaning the line means (marked [WordEntry.here]) and its partner with a line on how they differ.
     */
    fun withPartners(entries: List<WordEntry>): List<Pair<WordEntry, WordEntry?>> {
        val under = mutableMapOf<Int, Int>() // partner entry index → the entry it goes under
        for ((i, e) in entries.withIndex()) {
            val p = e.partner ?: continue
            if (i in under || under.containsValue(i)) continue
            val j = entries.indexOfFirst { it.lemma.equals(p.lemma, ignoreCase = true) && (it.pos == null || it.pos == e.pos) }
            if (j > i && j !in under) under[j] = i
        }
        return entries.withIndex().filter { it.index !in under }.map { (i, e) -> e to under.entries.firstOrNull { it.value == i }?.let { entries[it.key] } }
    }

    /** The English a new review item shows: the first few glosses. */
    fun meaning(e: WordEntry): String = e.gloss.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3).joinToString(", ")

    /**
     * The language [e]'s gloss is in when it isn't the learner's [base] (Wiktionary's English for a learner who reads
     * Slovene), for the card to say so; null when it is the base.
     */
    fun foreignGloss(e: WordEntry, base: Lang): Lang? {
        if (e.fallback && base != Lang.EN) return Lang.EN
        val lang = Lang.of(e.glossLang ?: return null) ?: return null
        return lang.takeIf { it != base }
    }

    /**
     * Whether the card tells the learner their tutor is writing a meaning in their base: the dictionary's (or the
     * supplement's) entry has only the English. The node asked the tutor (gloss_gaps); its gloss arrives as
     * lexicon_updated, and the card looks again.
     */
    fun tutorWritesMeaning(e: WordEntry, base: Lang): Boolean =
        e.fallback && base != Lang.EN && (e.source == "wiktionary" || e.source == "extra")

    /**
     * The example a new review item keeps: the line the learner met the word in (it's what they'll remember), else
     * the dictionary's example.
     */
    fun example(line: String, en: String?, e: WordEntry): WordExample? =
        if (line.isNotBlank()) WordExample(line.trim(), en?.trim().orEmpty()) else e.example

    /**
     * The chat message's data when the tutor is asked about a word: `{"lookup": {"word", "line", "en", "language"}}`;
     * [language] on a visit, the town's (the tutor's gloss goes to that language's dictionary).
     */
    fun askData(word: String, line: String, en: String?, language: String? = null): JsonObject = buildJsonObject {
        put("lookup", buildJsonObject {
            put("word", word)
            put("line", line)
            en?.takeIf { it.isNotBlank() }?.let { put("en", it) }
            language?.let { put("language", it) }
        })
    }

    /** "📖 «gozdu» — Kaj pomeni tukaj? · What does it mean here?" and the line below. */
    fun askText(word: String, line: String): String =
        "📖 «$word» — ${bi("words.whatDoesItMeanHere")}" + if (line.isNotBlank()) "\n«${line.trim()}»" else ""
}
