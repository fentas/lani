package si.lanisce.lani.ui.cases

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.data.CaseLook
import si.lanisce.lani.data.alsoAsk
import si.lanisce.lani.data.ask
import si.lanisce.lani.data.asks
import si.lanisce.lani.data.darkArgb
import si.lanisce.lani.data.lightArgb
import si.lanisce.lani.game.Case
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi

// The cases' look, one for the app (data/CaseLook.kt, the contract between the case trainer and the case lens): each case
// its colour everywhere (the trainer's chips and results, the lens's word card and its colours in the lines), the light
// theme's or the same hue lighter for the dark one, its name through the string tables, its question words.

/** The case's colour, the light theme's or the [dark] one's. */
fun Case.color(dark: Boolean): Color = Color(if (dark) darkArgb else lightArgb)

/** The case's colour on the theme on screen (dark when its surface is: the app's own theme, not only the system's). */
@Composable
@ReadOnlyComposable
fun Case.color(): Color = color(MaterialTheme.colorScheme.surface.luminance() < 0.5f)

/** A case named with its number and an adjective's gender, as the case lens's word card says it ("rodilnik, ed., ž."). */
object CaseNames {
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

/** "2. rodilnik · genitive": the case's number and its name in the learner's pair (the string tables' [Case.key]). */
fun Case.label(): String = "$n. ${bi(key)}"

/** "osebek · the subject" or "predmet · the object": what decides the case when no word does. */
fun roleLabel(role: String): String = bi(if (role == "subject") "cases.role.subject" else "cases.role.object")

/** "množina · plural" for a number ("sg", "pl", "du"). */
fun numberLabel(n: String): String = bi(CaseLook.numberKey(n))

/** What TalkBack reads for a case: "2. rodilnik · genitive, koga? česa?". */
fun Case.spoken(): String = listOfNotNull(label(), ask, alsoAsk).joinToString(", ")

/**
 * A case as a small pill: its colour as a bar on the left, its number and name, its question words ([question]); [filled]
 * in its colour (the right one after an answer).
 */
@Composable
fun CasePill(k: Case, modifier: Modifier = Modifier, question: Boolean = true, filled: Boolean = false) {
    val c = k.color()
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (filled) c.copy(alpha = 0.28f) else c.copy(alpha = 0.12f),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = k.spoken() },
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(c).clearAndSetSemantics {})
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(k.label(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (question) Text(k.asks, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
