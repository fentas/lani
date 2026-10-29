package si.lanisce.lani.ui.words

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import si.lanisce.lani.game.Case
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang

/**
 * How the six cases look in the app (docs/plans/05-built/case-lens.md; the contract shared with the case trainer): each
 * case's colour, light (the contract's) and dark (the same hue, lighter, for the dark theme), its school question words
 * ("koga? česa?") and its name and number in the learner's languages. When the case trainer's look lands, whichever
 * comes second uses the other's, with no copy left.
 */
object CaseLook {
    private val LIGHT = mapOf(
        Case.NOM to Color(0xFF4A7FC1), Case.GEN to Color(0xFFC2503C), Case.DAT to Color(0xFFD69A2D),
        Case.ACC to Color(0xFF3E9B5F), Case.LOC to Color(0xFF8A5CB8), Case.INS to Color(0xFF2C9AA0),
    )
    private val DARK = mapOf(
        Case.NOM to Color(0xFF98B5D9), Case.GEN to Color(0xFFD9978B), Case.DAT to Color(0xFFE4C285),
        Case.ACC to Color(0xFF79C694), Case.LOC to Color(0xFFBDA4D6), Case.INS to Color(0xFF62CAD0),
    )

    /** [case]'s colour, light or [dark]. */
    fun colour(case: Case, dark: Boolean): Color = (if (dark) DARK else LIGHT).getValue(case)

    /** [case]'s colour in the theme on screen (dark when its surface is). */
    @Composable
    @ReadOnlyComposable
    fun colour(case: Case): Color = colour(case, MaterialTheme.colorScheme.surface.luminance() < 0.5f)

    /** The school's question words of [case], as the shared contract writes them: "koga? česa?", "pri kom? pri čem?". */
    fun question(case: Case): String = case.question.lowercase()

    /** [case]'s name, [number] ("sg", "du", "pl") and an adjective's [gender], short, in [lang]: "rodilnik, ed., ž.". */
    fun name(case: Case, number: String?, lang: Lang, short: Boolean = true, gender: String? = null): String {
        val n = number?.let { numberKey(it, short) }?.let { L10n.text(lang, it) }
        val g = genderKey(gender)?.let { L10n.text(lang, it) }
        return L10n.text(lang, case.key) + listOfNotNull(n, g).joinToString("") { ", $it" }
    }

    /** [name] in both of the learner's languages: "rodilnik, ed. · genitive, sg." (one, when they are the same). */
    fun bi(case: Case, number: String?, short: Boolean = true, gender: String? = null): String {
        val pair = L10n.pair
        val target = name(case, number, pair.target, short, gender)
        val base = name(case, number, pair.base, short, gender)
        return if (pair.target == pair.base || target == base) target else "$target · $base"
    }

    /** A number's key: short ("ed.", "sg.") or written out ("ednina", "singular"). */
    fun numberKey(number: String, short: Boolean): String? = when (number) {
        "sg" -> if (short) "caseLens.sg" else "wordForms.singular"
        "du" -> if (short) "caseLens.du" else "wordForms.dual"
        "pl" -> if (short) "caseLens.pl" else "wordForms.plural"
        else -> null
    }

    /** A gender's key, short: "m.", "ž.", "s.". */
    fun genderKey(gender: String?): String? = when (gender) {
        "m" -> "words.genderM"
        "f" -> "words.genderF"
        "n" -> "words.genderN"
        else -> null
    }
}

/**
 * "🎨 Skloni · Cases": the colours' marks of [line] (data/CaseLens.kt) as a text shows it after [offset] characters (a
 * prefix: "1. "); none while the colours are off or the bridge hasn't answered (asked for once the line shows).
 */
@Composable
@Suppress("ComposableNaming")
fun caseMarks(vm: si.lanisce.lani.AppViewModel, line: String, offset: Int = 0): List<si.lanisce.lani.data.CaseMark> {
    val c = vm.caseColours
    if (!c.on) return emptyList()
    androidx.compose.runtime.LaunchedEffect(line) { c.request(line) }
    val spans = c.known(line) ?: return emptyList()
    return androidx.compose.runtime.remember(spans, offset) { si.lanisce.lani.data.CaseLens.marks(spans, offset) }
}
