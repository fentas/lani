package si.lanisce.lani.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * German's forms by case for the case trainer's "Dekliniere!" (docs/plans/05-built/case-de.md), from German's trigger table
 * (companion/grammar/de/data/case-triggers.json, bundled as resources grammar/de/data/case-triggers.json): a determiner's
 * form in a slot (dem, einen, meiner), an adjective's after it (großen, hohe; none: strong), a noun's forms by its rules
 * (dem Hund; des Hundes or des Hunds; den Kindern; dem Kollegen, the n-Deklination's; spoken dem Bub, never wrong) and how
 * sure each is. The same rules as the bridge's german-forms.ts, held to them by its parity fixture
 * (bridge/test/fixtures/german-forms.json: GermanFormsTest). Pure once loaded.
 */
class GermanForms(
    private val determiners: Map<String, Map<String, String>>,
    private val kinds: Map<String, String>,
    private val endings: Map<String, Map<String, String>>,
    private val stems: Map<String, String>,
    private val fixed: Set<String>,
    private val nNouns: Map<String, NNoun>,
) {
    /** A noun of the n-Deklination: its oblique form (Kollegen), its genitive where it adds -s (Namens), a neuter's accusative, its plural. */
    data class NNoun(val lemma: String, val oblique: String, val genitive: String? = null, val accusative: String? = null, val plural: String? = null, val gender: String? = null)

    /** A noun's forms in one slot: the standard ones (the first shown), others said and right ([also]), and whether the rules are sure. */
    data class Slot(val forms: List<String>, val also: List<String>, val sure: Boolean)

    private fun slotKey(case: String, gender: String, number: String) = "$case.${if (number == "pl") "pl" else gender}"

    /** The determiner [lemma]'s form in a slot; null where it has none (ein in the plural). */
    fun determinerForm(lemma: String, case: String, gender: String, number: String): String? = determiners[lemma]?.get(slotKey(case, gender, number))

    /** The adjectives' declension after determiner [lemma] (null: none): weak, mixed, strong. */
    fun declensionAfter(lemma: String?): String = when (lemma?.let { kinds[it] }) {
        null -> "strong"
        "ein", "possessive" -> "mixed"
        else -> "weak"
    }

    /** The adjective [lemma] in a slot after determiner [det] (none: strong): großen, hohe, rosa. */
    fun adjectiveForm(lemma: String, det: String?, case: String, gender: String, number: String): String {
        val l = lemma.lowercase()
        if (l in fixed) return l
        val stem = stems[l] ?: l
        return stem + (endings[declensionAfter(det)]?.get(slotKey(case, gender, number)) ?: "")
    }

    /** Whether [lemma] takes the n-Deklination (dem Kollegen): its -n is the standard, the bare form said (dem Bub). */
    fun isWeak(lemma: String): Boolean = lemma in nNouns

    /** The genitive singular of a der or das noun by its rules (german-forms.ts genitive). */
    fun genitive(lemma: String): Slot {
        val l = lemma.lowercase()
        val syllables = VOWELS.findAll(l).count()
        return when {
            l.endsWith("sch") -> Slot(listOf(lemma + "es"), listOf(lemma + "s"), true)
            Regex("(ss|ß|x|z|tz)$").containsMatchIn(l) || Regex("(au|ei|ai|eu|äu|ie)s$").containsMatchIn(l) -> Slot(listOf(lemma + "es"), emptyList(), true)
            l.endsWith("s") -> Slot(listOf(lemma + "es"), emptyList(), false)
            Regex("[aeiouäöüy]$").containsMatchIn(l) -> Slot(listOf(lemma + "s"), emptyList(), true)
            syllables >= 2 && Regex("(el|er|en|em|chen|lein|ling|ig|ich|ik|um|ment|in|or)$").containsMatchIn(l) -> Slot(listOf(lemma + "s"), emptyList(), true)
            syllables <= 1 -> Slot(listOf(lemma + "es"), listOf(lemma + "s"), true)
            else -> Slot(listOf(lemma + "s"), listOf(lemma + "es"), true)
        }
    }

    /** The dative plural of [plural]: -n added unless it ends in -n or -s (den Kindern, den Frauen, den Autos). */
    fun dativePlural(plural: String): String = if (plural.endsWith("n") || plural.endsWith("s")) plural else plural + "n"

    /** A noun's forms in [case] and [number] (german-forms.ts noun): its [gender], its nominative [plural] (null: unknown). */
    fun noun(lemma: String, gender: String, plural: String?, case: String, number: String): Slot {
        val n = nNouns[lemma]
        if (number == "pl") {
            val pl = n?.plural ?: n?.oblique ?: plural ?: return Slot(emptyList(), emptyList(), false)
            return Slot(listOf(if (case == "dat") dativePlural(pl) else pl), emptyList(), true)
        }
        if (n != null) {
            if (case == "nom") return Slot(listOf(lemma), emptyList(), true)
            if (n.gender == "n" && case == "acc") return Slot(listOf(n.accusative ?: lemma), emptyList(), true)
            val form = if (case == "gen") n.genitive ?: n.oblique else n.oblique
            return Slot(listOf(form), if (!lemma.endsWith("e") && case != "gen") listOf(lemma) else emptyList(), true)
        }
        if (gender == "f" || case == "nom" || case == "acc" || case == "dat") return Slot(listOf(lemma), emptyList(), true)
        return genitive(lemma)
    }

    companion object {
        private val VOWELS = Regex("[aeiouäöüy]+")

        /** The table in [raw] (lani.case-triggers/v0, language de); null when it isn't German's. */
        fun parse(raw: String): GermanForms? = runCatching {
            val o = Json.parseToJsonElement(raw).jsonObject
            if (o["language"]?.jsonPrimitive?.contentOrNull != "de") return null
            fun strMap(e: JsonObject?): Map<String, String> = e.orEmpty().mapNotNull { (k, v) -> v.jsonPrimitive.contentOrNull?.let { k to it } }.toMap()
            val dets = o["determiners"]!!.jsonArray.map { it.jsonObject }
            GermanForms(
                determiners = dets.associate { d -> d["lemma"]!!.jsonPrimitive.content to strMap(d["forms"]?.jsonObject) },
                kinds = dets.associate { d -> d["lemma"]!!.jsonPrimitive.content to d["kind"]!!.jsonPrimitive.content },
                endings = o["adjective_endings"]!!.jsonObject.mapValues { (_, v) -> strMap(v.jsonObject) },
                stems = strMap(o["adjective_stems"]?.jsonObject),
                fixed = o["adjective_fixed"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }.toSet(),
                nNouns = o["n_declension"]!!.jsonArray.map { it.jsonObject }.associate { n ->
                    fun s(k: String) = n[k]?.jsonPrimitive?.contentOrNull
                    s("lemma")!! to NNoun(s("lemma")!!, s("oblique")!!, s("genitive"), s("accusative"), s("plural"), s("gender"))
                },
            )
        }.getOrNull()

        /** The bundled table's forms; null when it can't be read. */
        val bundled: GermanForms? by lazy {
            GermanForms::class.java.getResourceAsStream("/grammar/de/data/case-triggers.json")?.use { parse(it.readBytes().decodeToString()) }
        }

        /** A pack word's German as its noun ("der Steg" → Steg; "die Stege" → Stege); null for a phrase. */
        fun bareNoun(written: String?): String? {
            val w = written?.trim().orEmpty()
            if (w.isEmpty()) return null
            val lemma = Regex("^(?:der|die|das)\\s+(\\S+)$").find(w)?.groupValues?.get(1) ?: w
            return lemma.takeIf { Regex("^\\p{Lu}[\\p{L}-]*$").matches(it) }
        }
    }
}
