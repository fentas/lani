package si.lanisce.lani.ui.cases

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.CaseBanks
import si.lanisce.lani.data.CaseTrainer
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.alsoAsk
import si.lanisce.lani.data.ask
import si.lanisce.lani.data.ofId
import si.lanisce.lani.data.ofSlot
import si.lanisce.lani.data.slName
import si.lanisce.lani.data.wordAt
import si.lanisce.lani.game.Case
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.LocalSpeaker
import si.lanisce.lani.ui.Outcome
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.TypedEx
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed

// A task of the case trainer on screen (companion/README.md "Case trainer"; data/CaseTrainer.kt):
// - "Kateri sklon? · Which case?": the sentence, the word asked about framed; the six cases as the school asks them
//   (kdo? kaj? … s kom? s čim?, the case's number and name small, each in its colour); then "Zakaj? · Why?": a tap on the
//   word that decides it, or "Nič: je osebek / predmet · Nothing: it's the subject / the object"; marked at once.
// - "Kje ali kam?": the place's picture where the village draws it, the sentence with its gap, the options; then, when it
//   has one, the verb that says kje or kam tapped.
// - "Sklanjaj! · Decline it": the frame's sentence, the word in brackets, typed (č, š, ž chips, 💡, Check), graded as any
//   typed answer; a spoken form right and labelled.
// The run's feedback box follows with the rule's line, the other readings of an ambiguous form and the page's link.

/** [ex] as its kind asks it; [done] with the outcome once every step is answered. */
@Composable
fun CaseTaskEx(vm: AppViewModel, ex: Exercise.CaseTask, locked: Boolean, done: (Outcome) -> Unit) {
    when (ex.kind) {
        "decline" -> DeclineEx(vm, ex, locked, done)
        "place" -> PlaceEx(vm, ex, locked, done)
        else -> WhichEx(vm, ex, locked, done)
    }
}

/** The instruction above a task: what to do, small. */
@Composable
private fun Ask(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
}

/** The sentence's meaning, small and quiet, under it. */
@Composable
private fun Meaning(text: String?) {
    if (text.isNullOrBlank()) return
    Text(text, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

// --- Kateri sklon? ------------------------------------------------------------------------------------------------------

@Composable
private fun WhichEx(vm: AppViewModel, ex: Exercise.CaseTask, locked: Boolean, done: (Outcome) -> Unit) {
    var picked by remember(ex.key) { mutableStateOf<Case?>(null) }
    var tapped by remember(ex.key) { mutableStateOf<Int?>(null) }
    var role by remember(ex.key) { mutableStateOf<String?>(null) }
    val right = Case.ofId(ex.case) ?: Case.NOM
    val p = picked
    val whyDone = tapped != null || role != null
    fun finish() {
        val p0 = picked ?: return
        val caseRight = CaseTrainer.pickRight(ex, p0)
        val whyRight = if (ex.askWhy) CaseTrainer.whyRight(ex, tapped, role) else null
        vm.caseTrainer.marked(ex.key, whyRight)
        val answer = CaseTrainer.answerLine(ex, "${p0.n}. ${p0.slName}", tapped?.let { ex.wordAt(it) } ?: role)
        done(Outcome(CaseTrainer.verdict(caseRight, whyRight), answer, null, explain(ex, whyRight)))
    }
    Ask("🧩 ${bi("cases.ask.which")}")
    Sentence(vm, ex, ex.tokens, ex.word, shownCase = if (p != null) right else null, marked = if (whyDone || locked) ex.taps.toSet() else emptySet(), wrongTap = tapped?.takeIf { it !in ex.taps && (whyDone || locked) })
    Meaning(ex.translation)
    Spacer(Modifier.height(16.dp))
    CaseGrid(picked = p, right = right.takeIf { p != null }, enabled = p == null && !locked) { k ->
        picked = k
        if (!ex.askWhy) finish()
    }
    if (p != null && ex.askWhy) {
        Spacer(Modifier.height(16.dp))
        Text("🤔 ${bi("cases.ask.why")}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
        Spacer(Modifier.height(8.dp))
        Taps(ex.tokens, except = ex.word, right = ex.taps.toSet(), tapped = tapped, enabled = !whyDone && !locked, case = right) { i ->
            tapped = i
            finish()
        }
        Spacer(Modifier.height(8.dp))
        RoleButtons(ex, role, enabled = !whyDone && !locked) { r ->
            role = r
            finish()
        }
    }
}

/**
 * The sentence, its tokens as they stand: the one asked about ([word]) framed (in its case's colour once answered:
 * [shownCase]), the deciding words ([marked]) underlined in it after the why, a wrong tap ([wrongTap]) struck through; its
 * 🔊 beside it (hearing it gives nothing away).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Sentence(vm: AppViewModel, ex: Exercise.CaseTask, tokens: List<String>, word: Int, shownCase: Case?, marked: Set<Int>, wrongTap: Int?) {
    val caseColor = shownCase?.color()
    val frame = MaterialTheme.colorScheme.primary
    // what shows is what is read and said: a storm's phrase ("brez mleka"), else the sentence
    val shown = tokens.joinToString(" ")
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            Modifier.weight(1f).semantics(mergeDescendants = true) {
                contentDescription = shown + (tokens.getOrNull(word)?.let { ", ${bi("cases.asked", "word" to CaseBanks.bare(it))}" }.orEmpty())
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tokens.forEachIndexed { i, t ->
                val asked = i == word
                val deciding = i in marked
                when {
                    asked -> Surface(
                        shape = RoundedCornerShape(8.dp), color = (caseColor ?: frame).copy(alpha = 0.18f),
                        border = BorderStroke(2.dp, caseColor ?: frame),
                    ) {
                        Text(t, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    else -> Text(
                        t, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = if (deciding) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            i == wrongTap -> TriglavRed
                            deciding && caseColor != null -> caseColor
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = when {
                            i == wrongTap -> TextDecoration.LineThrough
                            deciding -> TextDecoration.Underline
                            else -> null
                        },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
        LocalSpeaker.current?.let { SpeakButton(it, if (tokens == CaseBanks.tokens(ex.sentence)) ex.sentence else shown, Modifier.padding(start = 8.dp)) }
    }
}

/**
 * The six cases as the school asks them, in its order, two or three a row (a tablet three): each its question words big,
 * its number and name small, its colour. Once picked: the right one filled, a wrong pick outlined in red.
 */
@Composable
private fun CaseGrid(picked: Case?, right: Case?, enabled: Boolean, onPick: (Case) -> Unit) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val perRow = if (wide) 3 else 2
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in Case.entries.chunked(perRow)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in row) CaseChip(k, picked = picked == k, right = right == k, answered = right != null, enabled = enabled, modifier = Modifier.weight(1f)) { onPick(k) }
        }
    }
}

@Composable
private fun CaseChip(k: Case, picked: Boolean, right: Boolean, answered: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = k.color()
    val container = when {
        right -> c.copy(alpha = 0.36f)
        answered && !picked -> c.copy(alpha = 0.06f)
        else -> c.copy(alpha = 0.14f)
    }
    val border = when {
        picked && !right -> BorderStroke(3.dp, TriglavRed)
        right -> BorderStroke(3.dp, c)
        else -> BorderStroke(1.dp, c.copy(alpha = 0.6f))
    }
    val state = when {
        right -> "✓"
        picked -> "✗"
        else -> null
    }
    Card(
        onClick = { if (enabled) onClick() }, enabled = enabled || answered,
        colors = CardDefaults.cardColors(containerColor = container, disabledContainerColor = container),
        border = border, shape = RoundedCornerShape(14.dp),
        modifier = modifier.heightIn(min = 72.dp).semantics {
            role = Role.Button
            contentDescription = k.spoken()
            state?.let { stateDescription = if (right) bi("cases.right") else bi("cases.wrongPick") }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(k.ask, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                state?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = if (right) AlpineGreen else TriglavRed) }
            }
            k.alsoAsk?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface) }
            Text(k.label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The sentence's words to tap for "Zakaj?" (not the one asked about, [except]): each a chip; once tapped, the right ones
 * in the case's colour, a wrong tap red.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Taps(tokens: List<String>, except: Int, right: Set<Int>, tapped: Int?, enabled: Boolean, case: Case, onTap: (Int) -> Unit) {
    val c = case.color()
    val answered = tapped != null || !enabled
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tokens.forEachIndexed { i, t ->
            val w = CaseBanks.bare(t)
            if (i == except || w.isEmpty()) return@forEachIndexed
            val isRight = i in right
            val color = when {
                answered && isRight -> c.copy(alpha = 0.32f)
                i == tapped -> TriglavRed.copy(alpha = 0.22f)
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
            Surface(
                onClick = { if (enabled) onTap(i) }, enabled = enabled, shape = RoundedCornerShape(50), color = color,
                border = if (answered && isRight) BorderStroke(2.dp, c) else null,
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    role = Role.Button
                    if (answered && isRight) stateDescription = bi("cases.decides")
                    else if (i == tapped) stateDescription = bi("cases.wrongPick")
                },
            ) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(w, style = MaterialTheme.typography.titleMedium, fontWeight = if (answered && isRight) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

/** "Nič ne odloči: je osebek · Nothing decides it: it's the subject", and the object's. */
@Composable
private fun RoleButtons(ex: Exercise.CaseTask, said: String?, enabled: Boolean, onSay: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((r, emoji) in listOf("subject" to "👤", "object" to "🎯")) {
            val isRight = ex.role == r
            val answered = said != null || !enabled
            OutlinedButton(
                onClick = { if (enabled) onSay(r) }, enabled = enabled,
                border = when {
                    answered && isRight -> BorderStroke(2.dp, AlpineGreen)
                    said == r -> BorderStroke(2.dp, TriglavRed)
                    else -> null
                } ?: BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("$emoji ${roleLabel(r)}" + if (answered && isRight) "  ✓" else if (said == r) "  ✗" else "")
            }
        }
    }
}

/**
 * The feedback's explanation (Markdown): what decides it and the case ("**brez** → 2. rodilnik · koga? česa? · ednina"),
 * the rule's line, the same form's other readings, the item's note; whether the why was right is the verdict's.
 */
internal fun explain(ex: Exercise.CaseTask, whyRight: Boolean?): String {
    val k = Case.ofId(ex.case) ?: return ex.line
    val decider = when {
        ex.taps.isNotEmpty() && ex.kind == "which" && ex.role == null -> ex.taps.mapNotNull { ex.wordAt(it) }.distinct().joinToString(" + ") { "**$it**" }
        ex.kind == "place" && ex.taps.isNotEmpty() -> ex.taps.mapNotNull { ex.wordAt(it) }.joinToString(" + ") { "**$it**" }
        ex.role != null -> "**${roleLabel(ex.role)}**"
        else -> "**${ex.ruleSl}**"
    }
    // a place's: the verb says kje or kam, which decides the case ("**gre** → kam? → 4. tožilnik")
    val where = when {
        ex.kind != "place" -> null
        ex.why.endsWith("kam") || ex.why == "k" -> "kam?"
        ex.why.endsWith("kje") || ex.why == "pri" || ex.why == "ob" -> "kje?"
        ex.case == "rodilnik" -> "od kod?"
        else -> null
    }
    val head = listOfNotNull(decider, where, "**${k.n}. ${k.slName}** (${k.ask}) · ${numberLabel(ex.number)}").joinToString(" → ")
    val readings = ex.ambiguous.mapNotNull { s -> Case.ofSlot(s)?.let { kk -> "${kk.n}. ${kk.slName} ${shortNumber(s.substringAfter('.'))}" } }
    val form = ex.wordAt(ex.word).orEmpty()
    return listOfNotNull(
        head,
        ex.line.takeIf { it.isNotBlank() },
        readings.takeIf { it.isNotEmpty() }?.let { "🔀 ${bi("cases.alsoReads", "form" to form, "readings" to it.joinToString(", "))}" },
        ex.note?.let { "💡 $it" },
        if (whyRight == false) "🤔 ${bi("cases.whyMissed")}" else null,
    ).joinToString("\n")
}

/** "ed." / "dv." / "mn.": a number in short, as the school writes it. */
private fun shortNumber(n: String): String = when (n) {
    "pl" -> "mn."
    "du" -> "dv."
    else -> "ed."
}

// --- Kje ali kam? -------------------------------------------------------------------------------------------------------

@Composable
private fun PlaceEx(vm: AppViewModel, ex: Exercise.CaseTask, locked: Boolean, done: (Outcome) -> Unit) {
    var picked by remember(ex.key) { mutableStateOf<Int?>(null) }
    var tapped by remember(ex.key) { mutableStateOf<Int?>(null) }
    val right = Case.ofId(ex.case) ?: Case.LOC
    val p = picked
    val sentence = remember(ex.sentence) { CaseBanks.tokens(ex.sentence) }
    fun finish() {
        val p0 = picked ?: return
        val caseRight = p0 == ex.answer
        val whyRight = if (ex.askWhy) tapped?.let { it in ex.taps } ?: false else null
        vm.caseTrainer.marked(ex.key, whyRight)
        val answer = CaseTrainer.answerLine(ex, ex.options.getOrNull(p0).orEmpty(), tapped?.let { ex.wordAt(it) })
        done(Outcome(CaseTrainer.verdict(caseRight, whyRight), answer, null, explain(ex, whyRight)))
    }
    Ask(placeAsk(ex))
    ex.scene?.let { CasePicture(vm, it) }
    // the sentence with its gap, then (once picked) filled
    if (p == null) Text(ex.prompt, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { contentDescription = ex.prompt.replace("___", bi("cases.gap")) })
    else Sentence(vm, ex, sentence, ex.word, shownCase = right, marked = if (tapped != null || locked) ex.taps.toSet() else emptySet(), wrongTap = tapped?.takeIf { it !in ex.taps })
    Meaning(ex.translation)
    Spacer(Modifier.height(16.dp))
    ex.options.forEachIndexed { i, o ->
        val isRight = i == ex.answer
        val color = when {
            p == null -> CardDefaults.cardColors()
            isRight -> CardDefaults.cardColors(containerColor = right.color().copy(alpha = 0.30f))
            i == p -> CardDefaults.cardColors(containerColor = TriglavRed.copy(alpha = 0.18f))
            else -> CardDefaults.cardColors()
        }
        Card(
            onClick = { if (p == null && !locked) { picked = i; if (!ex.askWhy) finish() } },
            colors = color, shape = MaterialTheme.shapes.medium,
            border = if (p != null && isRight) BorderStroke(2.dp, right.color()) else null,
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).heightIn(min = 56.dp),
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(o, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (p != null && isRight) Text("✓", color = AlpineGreen, style = MaterialTheme.typography.titleMedium)
                else if (p == i) Text("✗", color = TriglavRed, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    if (p != null && ex.askWhy) {
        Spacer(Modifier.height(12.dp))
        Text("🤔 ${bi("cases.ask.whyPlace")}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
        Spacer(Modifier.height(8.dp))
        Taps(sentence, except = ex.word, right = ex.taps.toSet(), tapped = tapped, enabled = tapped == null && !locked, case = right) { i ->
            tapped = i
            finish()
        }
    }
}

/** A place task's instruction by what it asks: kje or kam, v or na, pri or k, od kod. */
private fun placeAsk(ex: Exercise.CaseTask): String {
    val key = when {
        ex.options.all { it.trim().lowercase() in setOf("v", "na") } -> "cases.ask.place.vna"
        ex.options.all { it.trim().lowercase() in setOf("k", "h", "pri") } -> "cases.ask.place.prik"
        ex.case == "rodilnik" -> "cases.ask.place.odkod"
        else -> "cases.ask.place.kjekam"
    }
    return "🧭 ${bi(key)}"
}

// --- Sklanjaj! ----------------------------------------------------------------------------------------------------------

@Composable
private fun DeclineEx(vm: AppViewModel, ex: Exercise.CaseTask, locked: Boolean, done: (Outcome) -> Unit) {
    Ask("✍️ ${bi("cases.ask.decline")}")
    Text(ex.prompt, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { contentDescription = ex.prompt.replace("___", bi("cases.gap")) })
    ex.hint?.let { Meaning(it) }
    if (locked) {
        Case.ofId(ex.case)?.let { k ->
            Spacer(Modifier.height(10.dp))
            CasePill(k)
        }
    }
    Spacer(Modifier.height(12.dp))
    TypedEx("", null, locked, done, accept = ex.accept) { a ->
        val g = CaseTrainer.gradeDecline(ex, a)
        vm.caseTrainer.marked(ex.key, null)
        val spoken = when {
            g.spoken -> "🗣️ ${bi("cases.spoken", "standard" to ex.shown)}"
            g.variant -> "🔀 ${bi("cases.variant", "standard" to ex.shown)}"
            else -> null
        }
        Outcome(g.verdict, a.trim(), g.expected.takeIf { g.verdict != Verdict.CORRECT || g.spoken || g.variant }, listOfNotNull(spoken, explain(ex, null)).joinToString("\n"), marks = g.marks, hint = g.hint)
    }
}

/** A colour for a verdict, the trainer's own (its feedback box keeps the run's). */
internal fun verdictTint(v: Verdict?): Color = when (v) {
    Verdict.CORRECT -> AlpineGreen
    Verdict.ALMOST -> Color(0xFFE08A1E)
    Verdict.WRONG -> TriglavRed
    null -> Color.Unspecified
}
