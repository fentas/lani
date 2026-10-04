package si.lanisce.lani.ui.towns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.TownVisit
import si.lanisce.lani.data.AskedQuestion
import si.lanisce.lani.data.DraftCheck
import si.lanisce.lani.data.QuestionFeedback
import si.lanisce.lani.data.QuestionSend
import si.lanisce.lani.data.ReceivedQuestion
import si.lanisce.lani.data.TownQuestions
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.DiacriticChips
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.Markdown
import si.lanisce.lani.ui.game.RewardSummary
import si.lanisce.lani.ui.theme.AlpineGreen

// Questions between towns (plan 2, §3.2, "The host takes part"; data/TownQuestions.kt): on a visit, "❓ Vprašaj ·
// Ask" (a question for the town's learner in its language, checked by the learner's tutor first if they like); in
// Friends, guests' questions to answer and the learner's own with their answers; a question's screen, to answer it in
// the learner's own town's language, or to read the answer. Everything from the other town is plain text.

/** The learner's tutor's check of a draft: waiting, then its feedback, the score and "✏️ Use the correction". */
@Composable
private fun CheckResult(c: DraftCheck, onUse: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (c.waiting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(bi("questions.tutorChecking"), style = MaterialTheme.typography.bodyMedium)
                }
                return@Column
            }
            Row {
                Text("🧑‍🏫 ${bi("common.tutor")}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                c.score?.let { Text("$it/10", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            }
            Markdown(c.feedback.orEmpty())
            c.corrected?.let { fixed ->
                Text("✏️ $fixed", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { onUse(fixed) }, contentPadding = PaddingValues(0.dp)) { Text(bi("questions.useCorrection")) }
            }
        }
    }
}

/** The tutor's feedback on a question or an answer sent, once it came. */
@Composable
private fun FeedbackCard(f: QuestionFeedback) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text("🧑‍🏫 ${bi("common.tutor")}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                f.score?.let { Text("${"%.0f".format(it)}/10", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            }
            Markdown(f.text)
        }
    }
}

/**
 * On a visit, "❓ Vprašaj Mio · Ask Mia": a question for the town's learner, in the town's language (one line, at most
 * [TownQuestions.MAX_QUESTION] characters), "✓ Preveri · Check" by the learner's own tutor first if they like, then sent.
 * The questions asked here before show below with their answers.
 */
@Composable
internal fun AskPanel(vm: AppViewModel, v: TownVisit) {
    val q = vm.questions
    val who = v.learner.ifBlank { v.name }
    LaunchedEffect(v.id) {
        q.clearCheck()
        q.load()
    }
    var text by remember(v.id) { mutableStateOf("") }
    var problem by remember(v.id) { mutableStateOf<String?>(null) }
    Text(bi("questions.askAbout", *TownQuestions.whoArgs(who), "town" to v.name), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.replace('\n', ' ').take(TownQuestions.MAX_QUESTION); problem = null },
        label = { Text(bi("questions.yourQuestion")) },
        supportingText = { Text("${text.length}/${TownQuestions.MAX_QUESTION}") },
        enabled = !q.sending,
        modifier = Modifier.fillMaxWidth(),
    )
    q.check?.let { c -> CheckResult(c) { fixed -> text = fixed } }
    problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { q.checkDraft(text, town = v.id) },
            enabled = text.isNotBlank() && q.check?.waiting != true && !q.sending,
            modifier = Modifier.heightIn(min = 56.dp),
        ) { Text("✓ ${bi("questions.check")}") }
        BigButton(
            "❓ ${bi("visit.send")}",
            onClick = {
                q.ask(v.id, v.name, text) { out ->
                    when (out) {
                        is QuestionSend.Sent -> {
                            text = ""
                            vm.notices.banner = "❓ ${bi("questions.sent")}"
                        }
                        is QuestionSend.Refused -> problem = out.problem
                        is QuestionSend.Kept -> Unit
                    }
                }
            },
            enabled = text.isNotBlank() && !q.sending,
            color = AlpineGreen,
            modifier = Modifier.weight(1f),
        )
    }
    val before = q.list?.to(v.id).orEmpty().take(5)
    if (before.isNotEmpty()) Text("❓ ${bi("questions.yourQuestions")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    for (a in before) AskedRow(a)
}

/** A question of the learner's to another town: to whom, the question, and its answer (or that it waits for one). */
@Composable
private fun AskedRow(a: AskedQuestion, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("❓ ${bi("questions.questionTo", *TownQuestions.whoArgs(a.who))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(a.text, style = MaterialTheme.typography.bodyLarge)
            val answer = a.answer
            if (answer != null) Text("💬 „${answer.text}“", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            else Text("⏳ ${bi("questions.waiting")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A guest's question: who asked, from where, the question, and whether the learner answered it (and it reached them). */
@Composable
private fun ReceivedRow(r: ReceivedQuestion, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("❓ ${bi("questions.from", *TownQuestions.whoArgs(r.who))} · ${r.from.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            Text(r.text, style = MaterialTheme.typography.bodyLarge)
            val a = r.answer
            val status = when {
                a == null -> "▸ ${bi("common.answer")}"
                a.sent -> "✅ „${a.text}“"
                else -> "📡 ${bi("questions.notSentYet")}"
            }
            Text(status, style = MaterialTheme.typography.labelMedium, fontWeight = if (a == null) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

/**
 * Friends' questions between towns: guests' questions (the open ones first; a child's parent sees them here too), then the
 * learner's own with their answers. A tap opens the question.
 */
internal fun LazyListScope.questionItems(vm: AppViewModel) {
    val l = vm.questions.list ?: return
    if (l.asked.isEmpty() && l.received.isEmpty()) return
    item(key = "questions") {
        Text(
            "❓ ${bi("questions.title")}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
        )
    }
    val received = l.open + l.received.filter { it.answer != null }
    items(received.take(20), key = { "qr:" + it.key }) { r ->
        ReceivedRow(r, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { vm.openQuestion(r.key, asked = false) }
    }
    items(l.asked.take(20), key = { "qa:" + it.key }) { a ->
        AskedRow(a, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { vm.openQuestion(a.key, asked = true) }
    }
}

/**
 * A question between towns: a guest's ([asked] false), answered here in the learner's own town's language (their tutor
 * checks it first if they like, and grades it once sent), or the learner's own ([asked]) with the tutor's feedback and
 * the answer. Back leads to Friends.
 */
@Composable
fun TownQuestionScreen(vm: AppViewModel, key: String, asked: Boolean) {
    BackHandler { vm.openFriends() }
    val l = vm.questions.list
    val a = l?.asked?.firstOrNull { it.key == key }
    val r = l?.received?.firstOrNull { it.key == key }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::openFriends) { EmojiLabel("✕", Labels.CLOSE) }
            val title = when {
                asked && a != null -> bi("questions.questionTo", *TownQuestions.whoArgs(a.who))
                !asked && r != null -> bi("questions.from", *TownQuestions.whoArgs(r.who))
                else -> bi("questions.title")
            }
            Text("❓ $title", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        if (l == null) {
            Text(bi("common.loading"), Modifier.padding(24.dp))
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                asked && a != null -> AskedThread(vm, a)
                !asked && r != null -> ReceivedThread(vm, r)
                else -> Text("❓ ${bi("questions.unknown")}", style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (asked || r?.answer != null) {
            BigButton(bi("common.continue"), onClick = vm::openFriends)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** The other town's text: who, and what they wrote (plain text), with 🔊 in the language it is in. */
@Composable
private fun TheirBubble(vm: AppViewModel, emoji: String, who: String, text: String, speak: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(who, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    if (speak && vm.speaker.canSay) FilledTonalIconButton(onClick = { vm.speaker.say(text) }) { EmojiLabel("🔊", Labels.LISTEN) }
                }
            }
        }
    }
}

/** The learner's own text in the thread. */
@Composable
private fun MineBubble(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(bi("familyScreen.you"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** A guest's question: its bubble; the answer box, or the learner's answer, where it is, and the tutor's feedback. */
@Composable
private fun ReceivedThread(vm: AppViewModel, r: ReceivedQuestion) {
    val q = vm.questions
    // the question is in the learner's own town's language: the phone speaks it
    TheirBubble(vm, "❓", "${r.who} · ${r.from.name}", r.text, speak = true)
    Text("❓ ${bi("questions.answerInLanguage")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val answer = r.answer
    if (answer == null) {
        var value by remember(r.key) { mutableStateOf(TextFieldValue("")) }
        var problem by remember(r.key) { mutableStateOf<String?>(null) }
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.copy(text = it.text.replace('\n', ' ').take(TownQuestions.MAX_ANSWER)); problem = null },
            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
            enabled = !q.sending,
            placeholder = { Text(bi("questions.yourAnswer")) },
            supportingText = { Text("${value.text.length}/${TownQuestions.MAX_ANSWER}") },
            shape = MaterialTheme.shapes.medium,
        )
        Lang.of(r.language)?.let { DiacriticChips(value, { value = it }, lang = it) }
        q.check?.let { c -> CheckResult(c) { fixed -> value = TextFieldValue(fixed, TextRange(fixed.length)) } }
        problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { q.checkDraft(value.text, key = r.key) },
                enabled = value.text.isNotBlank() && q.check?.waiting != true && !q.sending,
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text("✓ ${bi("questions.check")}") }
            BigButton(
                bi("common.send"),
                onClick = {
                    q.answer(r, value.text) { out ->
                        when (out) {
                            is QuestionSend.Sent -> vm.notices.banner = "✅ ${bi("questions.answerSent")}"
                            is QuestionSend.Kept -> vm.notices.banner = "📡 ${bi("questions.notSentYet")}"
                            is QuestionSend.Refused -> problem = out.problem
                        }
                    }
                },
                enabled = value.text.isNotBlank() && !q.sending,
                color = AlpineGreen,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }
    MineBubble(answer.text)
    if (answer.sent) Text("✅ ${bi("questions.answerSent")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("📡 ${bi("questions.notSentYet")}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { q.answer(r, answer.text) }, enabled = !q.sending) { Text("↻ ${bi("visit.sendAgain")}") }
    }
    vm.game.lastReward?.let { RewardSummary(it, onVillage = vm::openVillage) }
    val f = answer.feedback
    if (f != null) FeedbackCard(f)
    else Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(bi("familyScreen.tutorLookingAt"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { q.load() }) { Text("↻ ${bi("familyScreen.refresh")}") }
    }
}

/** The learner's question: their text, the tutor's feedback on it, and the host's answer (or that it waits for one). */
@Composable
private fun AskedThread(vm: AppViewModel, a: AskedQuestion) {
    MineBubble(a.text)
    a.feedback?.let { FeedbackCard(it) }
    val answer = a.answer
    // the answer is in the town's language, which the phone speaks only on a visit there
    if (answer != null) TheirBubble(vm, "💬", "${a.who} · ${a.town.name}", answer.text, speak = false)
    else Row(verticalAlignment = Alignment.CenterVertically) {
        Text("⏳ ${bi("questions.waiting")}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { vm.questions.load() }) { Text("↻ ${bi("familyScreen.refresh")}") }
    }
}
