package si.lanisce.lani.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// The case trainer's bank ("🧩 Skloni · Cases", lani.cases/v0; companion/README.md "Case trainer"): the curated items of
// "Kateri sklon?" and "Kje ali kam?" with their gold annotations, the rules' one lines, the frames of "Sklanjaj!", the
// adjectives and possessives it declines with the learner's nouns, and nouns for when the learner has too few of their
// own. companion/cases/<language>/bank.json is bundled (app/build.gradle.kts: cases/<language>/bank.json); the bridge
// serves it with the tutor's items and assignment (GET /cases, companion/bridge/src/case-bank.ts, which checks it). Pure.

/** A text in the learner's bases: English, German, Italian; English is every base's fallback. */
@Serializable
data class CaseTexts(val en: String = "", val de: String = "", val it: String = "") {
    fun inBase(base: String): String = when (base) {
        "de" -> de
        "it" -> it
        else -> en
    }.ifBlank { en }
}

/** A rule's one line ([CaseBank.rules] by its key): its case, its grammar page (the rule id), the words that trigger it, the line. */
@Serializable
data class CaseRuleLine(
    val case: String,
    val page: String,
    val words: List<String> = emptyList(),
    val sl: String = "",
    val en: String = "",
    val de: String = "",
    val it: String = "",
) {
    fun inBase(base: String): String = CaseTexts(en, de, it).inBase(base)
}

/** A spoken form in an item's sentence: right, never a mistake; labelled with the standard one. */
@Serializable
data class CaseSpoken(val form: String, val standard: String, val note: CaseTexts = CaseTexts())

/**
 * An item of the bank (companion/bridge/src/case-bank.ts has the format and its checks): "which" (Kateri sklon?) or "place"
 * (Kje ali kam?). Tokens are the sentence split at white space ([CaseBanks.tokens]); [word] the token asked about, [trigger]
 * the one that decides the case (null: nothing does, [role]), [also] more tokens right to tap. A place item's [gap] (from,
 * to: inclusive) is chosen among [options] ([answer] right), its [cue] the noun in brackets, its [trigger] the verb saying
 * kje or kam; [scene] the village scene that draws the place.
 */
@Serializable
data class CaseItem(
    val id: String,
    val kind: String,
    val level: String = "A1",
    val sl: String,
    val en: String = "",
    val de: String = "",
    val it: String = "",
    val word: Int,
    val lemma: String,
    val pos: String = "noun",
    val gender: String? = null,
    val case: String,
    val number: String = "sg",
    val why: String,
    val trigger: Int? = null,
    val also: List<Int> = emptyList(),
    val role: String? = null,
    val ambiguous: List<String> = emptyList(),
    val note: CaseTexts? = null,
    val spoken: CaseSpoken? = null,
    val from: String = "bank",
    val question: String? = null,
    val gap: List<Int> = emptyList(),
    val options: List<String> = emptyList(),
    val answer: Int = 0,
    val cue: String? = null,
    val scene: String? = null,
    val source: String = "curated",
) {
    val tokens: List<String> get() = CaseBanks.tokens(sl)
    fun meaning(base: String): String = CaseTexts(en, de, it).inBase(base)
}

/**
 * A frame of "Sklanjaj! · Decline it": a sentence with a known trigger and its gap ({x}), {k} and {s} before it (k/h, s/z
 * as the word after them wants), {two} (dva or dve by the noun's gender); its translations with "…" for the word. [adj]:
 * an adjective or a possessive may come with the noun; [needs]: pages it needs introduced; [gender]: only these.
 */
@Serializable
data class CaseFrame(
    val id: String,
    val case: String,
    val number: String,
    val why: String,
    val level: String = "A1",
    val sl: String,
    val en: String = "",
    val de: String = "",
    val it: String = "",
    val adj: Boolean = false,
    val needs: List<String> = emptyList(),
    val gender: List<String>? = null,
) {
    fun meaning(base: String): String = CaseTexts(en, de, it).inBase(base)
}

/** An adjective or a possessive with every form ("gen.f.sg", "acc.m.sg.anim"), its level and meaning. */
@Serializable
data class CaseAdjective(
    val lemma: String,
    val kind: String = "adj",
    val level: String = "A1",
    val en: String = "",
    val de: String = "",
    val it: String = "",
    val forms: Map<String, String> = emptyMap(),
) {
    val possessive: Boolean get() = kind == "possessive"
    fun meaning(base: String): String = CaseTexts(en, de, it).inBase(base)
}

/** A noun the frames use when the learner has too few of their own: its gender, meaning and forms by slot (one or more each). */
@Serializable
data class CaseBankNoun(
    val lemma: String,
    val gender: String,
    val level: String = "A1",
    val en: String = "",
    val de: String = "",
    val it: String = "",
    val forms: Map<String, JsonElement> = emptyMap(),
) {
    /** Its forms by slot, each a list (the bank writes one as a string). */
    val slots: Map<String, List<String>>
        get() = forms.mapValues { (_, v) ->
            when (v) {
                is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> listOfNotNull(v.contentOrNull)
                else -> emptyList()
            }
        }
}

/** The tutor's assignment: a session focused on a case or a rule, with a note. */
@Serializable
data class CaseAssigned(val focus: String, val note: String? = null, val at: String = "", val done: String? = null)

/** The bank as the bridge serves it (GET /cases) or the app bundles it. */
@Serializable
data class CaseBank(
    val schema: String = "lani.cases/v0",
    val language: String = "sl",
    @kotlinx.serialization.SerialName("machine_written") val machineWritten: Boolean = true,
    val reviewed: Boolean = false,
    val review: String = "",
    val rules: Map<String, CaseRuleLine> = emptyMap(),
    val items: List<CaseItem> = emptyList(),
    val frames: List<CaseFrame> = emptyList(),
    val adjectives: List<CaseAdjective> = emptyList(),
    val nouns: List<CaseBankNoun> = emptyList(),
    /** Mass nouns the frames keep to the singular (voda, mleko: "Grem k vodam" is no Slovene). */
    val uncountable: List<String> = emptyList(),
    val assigned: CaseAssigned? = null,
) {
    fun rule(key: String): CaseRuleLine? = rules[key]

    /** The rule id (grammar page) of rule [key]. */
    fun page(key: String): String? = rules[key]?.page
}

object CaseBanks {
    /** The languages that have a trainer: the cases are Slovene's (the forms book's FORMS_LANGUAGES). */
    val LANGUAGES = setOf("sl")

    /** A sentence's tokens: split at white space, punctuation on its word (as the bridge's case-bank.ts tokensOf). */
    fun tokens(sentence: String): List<String> = sentence.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** A token without the punctuation around it ("šoli." → "šoli"; as case-bank.ts bare). */
    fun bare(token: String): String = token.replace(Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$"), "")

    /** The bank in [raw] (GET /cases, or the bundled file); null when it isn't one. */
    fun parse(raw: String): CaseBank? = runCatching { json.decodeFromString(CaseBank.serializer(), raw) }.getOrNull()
        ?.takeIf { it.schema == "lani.cases/v0" }

    /** The bundled bank of [language] (app resources cases/<language>/bank.json); null for a language without one. */
    fun bundled(language: String): CaseBank? {
        if (language !in LANGUAGES) return null
        val raw = CaseBanks::class.java.getResourceAsStream("/cases/$language/bank.json")?.use { it.readBytes().decodeToString() } ?: return null
        return parse(raw)
    }

    /**
     * The bank to play: the bridge's (its items include the tutor's, its assignment) when it has one, else the bundled; an
     * item the bridge lacks that the bundled has (a newer app than its bridge) stays.
     */
    fun merge(bundled: CaseBank?, bridge: CaseBank?): CaseBank? {
        if (bridge == null) return bundled
        if (bundled == null) return bridge
        val ids = bridge.items.map { it.id }.toSet()
        return bridge.copy(
            items = bridge.items + bundled.items.filter { it.id !in ids },
            rules = bundled.rules + bridge.rules,
            frames = bridge.frames.ifEmpty { bundled.frames },
            adjectives = bridge.adjectives.ifEmpty { bundled.adjectives },
            nouns = bridge.nouns.ifEmpty { bundled.nouns },
            uncountable = bridge.uncountable.ifEmpty { bundled.uncountable },
        )
    }
}
