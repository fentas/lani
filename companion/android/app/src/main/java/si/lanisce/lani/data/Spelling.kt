package si.lanisce.lani.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
 *   for "l'acqua").
 * A language without a table compares letters as they are.
 */
class Spelling(
    val lang: Lang,
    val same: List<Rule> = emptyList(),
    val fold: List<Rule> = emptyList(),
    val foldHint: Grading.Hint = Grading.Hint.NONE,
    val loose: List<Rule> = emptyList(),
    val looseHint: Grading.Hint = Grading.Hint.NONE,
) {
    class Rule(val pattern: Regex, val replacement: String) {
        fun apply(s: String): String = pattern.replace(s, replacement)
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
