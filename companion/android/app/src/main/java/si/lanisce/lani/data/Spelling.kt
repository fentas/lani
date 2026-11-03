package si.lanisce.lani.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import si.lanisce.lani.l10n.Lang
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * How answers in one language are compared ([Grading]): a table per language, app/src/main/resources/grading/<code>.json,
 * so another language is a new table, not new code. Each rule is a regular expression and its replacement, applied in
 * order to the answer lowercased (in the language's locale) and without punctuation:
 * - [same]: spellings that are the same answer ("don't" is "do not"; "l' acqua" is "l'acqua"): correct;
 * - [fold]: the letters a keyboard may lack ("č" → "c", "è" → "e", "ä" → "ae"): an answer that differs only there is
 *   almost right, with [foldHint];
 * - [loose]: after [fold], what is almost right too, with [looseHint] (Italian: "un amica" for "un'amica", "la acqua"
 *   for "l'acqua"; English: "cant" for "can't"), and [looseExamples], a few of the language's own shown after the
 *   hint ("l'acqua, un'amica"; "can't, I'm").
 * [dropSubject]: the language leaves its subject out where the verb's ending says the person (Slovene "Spita v šotoru.",
 * Italian "Dormono in tenda."): a worksheet's person in brackets may be left out of the answer ([Sheets.withoutPerson]).
 * [wrong]: words that are a known mistake, never a slip of the keyboard, matched whole against a word of the answer as
 * [Grading.fold] writes it (Italian's regular stem where the form has its own: "anderò" for andrò, "ferò" for farò,
 * "mangierò" for mangerò, "venito" for venuto): one letter off the right word, such a word is wrong, not forgiven as a
 * slip in the stem. A [Mistake] with `for` is one only where the right word matches that too (an articulated
 * preposition for another, "delle" for dalle, while "della" for bella stays a slip).
 * [endingLetters]: how many letters at the end of a word over five letters are its ending, where a slip is no slip (two:
 * Slovene's -ega, -em, Italian's -ano; one in English, "usualy" for usually a slip, "walk" for walks still the ending).
 * [endingSwaps]: an ending typed for another on the same stem, each pair (the right word's ending, the typed one's), where
 * the two differ a letter before the plain ending of one or two letters (Italian's subjunctive and indicative: "dormono"
 * for dormano, "parlate" for parliate; the conditional and the future: "sarete" for sareste): wrong with the ending
 * hint, never a slip in the stem, while a slip elsewhere in such a word (bambeno, bambimo for bambino) stays one.
 * [nonstandard]: what people say that writing doesn't want, each for the standard word it stands for (English: "good" for
 * well, "I didn't see nothing"): almost, [Grading.Hint.NONSTANDARD], the standard form shown, one way only and never a slip
 * or a mistake. Not a worksheet item's `spoken` (COURSE.md "Spoken forms"): that is right, on one item, where a course lists
 * it; this is the language's, for all its answers. A table without the key behaves as it did.
 * A language without a table compares letters as they are.
 */
class Spelling(
    val lang: Lang,
    val same: List<Rule> = emptyList(),
    val fold: List<Rule> = emptyList(),
    val foldHint: Grading.Hint = Grading.Hint.NONE,
    val loose: List<Rule> = emptyList(),
    val looseHint: Grading.Hint = Grading.Hint.NONE,
    val looseExamples: String? = null,
    val dropSubject: Boolean = false,
    val wrong: List<Mistake> = emptyList(),
    val endingSwaps: List<Pair<String, String>> = emptyList(),
    val endingLetters: Int = 2,
    val nonstandard: List<Nonstandard> = emptyList(),
) {
    class Rule(val pattern: Regex, val replacement: String) {
        fun apply(s: String): String = pattern.replace(s, replacement)
    }

    /** A known mistake: [word] typed for the right word, or only for one that matches [forWord] where it's given. */
    class Mistake(val word: Regex, val forWord: Regex? = null) {
        fun matches(typed: String, right: String): Boolean = word.matches(typed) && (forWord?.matches(right) ?: true)
    }

    /**
     * What people say for a standard word: [word] (matched whole in the answer, [Grading.fold] written) where the right answer
     * has one matching [forWord] in its place and the rest is the same; with [after], only where the text before it (the
     * sentence before a gap's answer, and the answer's own words) has that (a negative, for a double negative).
     */
    class Nonstandard(word: String, forWord: String, after: String? = null) {
        val word = Regex("(?<!\\p{L})(?:$word)(?!\\p{L})")
        val forWord = Regex(forWord)
        val after = after?.let(::Regex)
    }

    /** Lowercasing follows the language (Turkish-safe, and whatever else a locale knows). */
    val locale: Locale = Locale.forLanguageTag(lang.code)

    companion object {
        private val tables = ConcurrentHashMap<Lang, Spelling>()

        /** [lang]'s table, read once. */
        fun of(lang: Lang): Spelling = tables.getOrPut(lang) {
            Spelling::class.java.getResourceAsStream("/grading/${lang.code}.json")?.use { parse(lang, it.readBytes().decodeToString()) } ?: Spelling(lang)
        }

        fun parse(lang: Lang, json: String): Spelling {
            val o = Json.parseToJsonElement(json).jsonObject
            return Spelling(
                lang,
                same = rules(o, "same"),
                fold = rules(o, "fold"),
                foldHint = hint(o, "fold_hint"),
                loose = rules(o, "loose"),
                looseHint = hint(o, "loose_hint"),
                looseExamples = o["loose_examples"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                dropSubject = o["drop_subject"]?.jsonPrimitive?.booleanOrNull ?: false,
                wrong = (o["wrong"] as? JsonArray).orEmpty().map { m ->
                    (m as? JsonObject)?.let { Mistake(Regex(it.getValue("word").jsonPrimitive.content), it["for"]?.let { f -> Regex(f.jsonPrimitive.content) }) }
                        ?: Mistake(Regex(m.jsonPrimitive.content))
                },
                endingSwaps = (o["ending_swaps"] as? JsonArray).orEmpty().map { p ->
                    val (right, typed) = p.jsonArray.map { it.jsonPrimitive.content }
                    right to typed
                },
                endingLetters = o["ending_letters"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 3) ?: 2,
                nonstandard = (o["nonstandard"] as? JsonArray).orEmpty().map { n ->
                    val p = n.jsonObject
                    Nonstandard(p.getValue("word").jsonPrimitive.content, p.getValue("for").jsonPrimitive.content, p["after"]?.jsonPrimitive?.contentOrNull)
                },
            )
        }

        private fun rules(o: JsonObject, key: String): List<Rule> = (o[key] as? JsonArray).orEmpty().map { r ->
            val (pattern, replacement) = r.jsonArray.map { it.jsonPrimitive.content }
            Rule(Regex(pattern), replacement)
        }

        private fun hint(o: JsonObject, key: String): Grading.Hint =
            o[key]?.jsonPrimitive?.content?.let { h -> Grading.Hint.entries.firstOrNull { it.name.equals(h, ignoreCase = true) } } ?: Grading.Hint.NONE
    }
}
