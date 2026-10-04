package si.lanisce.lani.ui.talk

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.AnswerKeyboard
import si.lanisce.lani.ui.NoSuggestions
import si.lanisce.lani.ui.DiacriticChips
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.Suggestions
import si.lanisce.lani.ui.rememberVocabulary
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold

/**
 * Jan's side of the conversation: a big 🎤 and the input field. Tap 🎤 (or hold it) and speak, several sentences if they
 * like: ⏹ or a long pause ends it, "Next sentence" closes one and goes on. What was recognized lands in the field,
 * sentence by sentence, with the unclear words marked, to check, correct and send (➤); nothing goes out by itself.
 * ⌨️ types instead; 💡 asks for a hint.
 */
@Composable
fun TalkInput(
    vm: AppViewModel,
    enabled: Boolean,
    hintEnabled: Boolean,
    onSay: (text: String, alternatives: List<String>, typed: Boolean) -> Unit,
    onHint: () -> Unit,
) {
    val draft = remember { TalkDraft() }
    val voice = rememberTalkVoice(vm, draft)
    val mic = voice.mic
    var keyboard by remember { mutableStateOf(!voice.canListen()) }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val vocabulary = rememberVocabulary(vm)

    fun send() {
        if (!mic.canSend(enabled)) return
        val s = draft.take() ?: return
        onSay(s.text, s.alternatives, s.typed)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        mic.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (mic.asking) {
            MicRationale(onAllow = voice::allow, onNotNow = { voice.notNow(); keyboard = true })
            return@Column
        }
        MicStatus(mic)
        UnclearHint(voice, onTypeIt = { draft.select(it); focus.requestFocus() })
        if (keyboard || focused || draft.text.isNotEmpty()) {
            if (keyboard || focused) {
                DiacriticChips(draft.value, draft::edit)
                Suggestions(draft.value, vocabulary, draft::edit)
            }
            DraftRow(
                draft,
                bi("talkInput.writeSlovene"),
                canSend = mic.canSend(enabled),
                onSend = ::send,
                modifier = Modifier.focusRequester(focus).onFocusChanged { focused = it.isFocused },
            )
        }
        if (keyboard) Row(verticalAlignment = Alignment.CenterVertically) {
            if (voice.canListen()) TextButton(onClick = { keyboard = false; mic.message = null }) { EmojiLabel("🎤", Labels.SPEAK) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onHint, enabled = hintEnabled) { Text("💡 ${bi("talkInput.hint")}") }
        } else {
            if (!mic.busy && draft.text.isEmpty()) Text(
                if (enabled) bi("talkInput.holdOrTapThen") else bi("talkInput.waitAnswer"),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (mic.recording && mic.redo == null) NextSentence(voice)
                    else TextButton(onClick = { mic.cancel(); keyboard = true }) { Text("⌨️ ${bi("talkInput.type")}") }
                }
                HoldMic(voice, enabled)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onHint, enabled = hintEnabled && !mic.recording) { Text("💡 ${bi("talkInput.hint")}") }
                }
            }
        }
    }
}

/** The field with Send: what was recognized, to check and correct; unclear words are underlined in red. */
@Composable
internal fun DraftRow(
    draft: TalkDraft, placeholder: String, canSend: Boolean, onSend: () -> Unit, modifier: Modifier = Modifier,
    answer: Boolean = true, mic: (@Composable () -> Unit)? = null,
) {
    val color = MaterialTheme.colorScheme.error
    val marks = draft.marks
    val underline = remember(marks, color) { MarkWords(marks, color) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // what Jan says in a talk is their answer: no suggestions; a question to the tutor may have them
        NoSuggestions(answer) {
            OutlinedTextField(
                value = draft.value,
                onValueChange = draft::edit,
                modifier = modifier.weight(1f),
                placeholder = { Text(placeholder) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
                visualTransformation = underline,
                keyboardOptions = if (answer) AnswerKeyboard else KeyboardOptions.Default,
                trailingIcon = if (draft.text.isNotEmpty()) {
                    { IconButton(onClick = draft::clear) { EmojiLabel("✕", Labels.REMOVE) } }
                } else null,
            )
        }
        mic?.invoke()
        FilledIconButton(onClick = onSend, enabled = canSend) { EmojiLabel("➤", Labels.SEND) }
    }
}

/** Underlines the marked words (same offsets: the text itself is unchanged). */
private data class MarkWords(val marks: List<Unclear>, val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (marks.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val style = SpanStyle(color = color, textDecoration = TextDecoration.Underline)
        val b = AnnotatedString.Builder(text)
        for (m in marks) if (m.start >= 0 && m.start < m.end && m.end <= text.length) b.addStyle(style, m.start, m.end)
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/** While the microphone is on: what it hears, and its level; then "transcribing" until the text is in. */
@Composable
internal fun MicStatus(mic: TalkMic) {
    val redo = mic.redo
    if (!mic.busy) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            mic.recording && mic.partial.isNotBlank() -> Text(
                mic.partial,
                style = MaterialTheme.typography.titleMedium,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> Text(
                when {
                    mic.recording && redo != null -> "🎤 ${bi("talkInput.sayWord", "word" to redo.word)}"
                    mic.recording -> "🔴 ${bi("talkInput.listening")}" + if (mic.transcribing) "  ✍️" else ""
                    else -> "✍️ ${bi("talkInput.transcribing")}"
                },
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (mic.recording) {
            LinearProgressIndicator(progress = { mic.level }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = TriglavRed)
            if (redo == null) Text(
                bi("talkInput.pauseEnds"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The words the node's recognizer was unsure of: tap one (here, or in the field) to say it again or type it. Shown
 * while the draft has such words; Jan can wave each off (✕).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnclearHint(voice: TalkVoice, onTypeIt: (Unclear) -> Unit) {
    val draft = voice.draft
    val marks = draft.marks
    if (marks.isEmpty()) return
    val picked = draft.picked
    Surface(color = XpGold.copy(alpha = 0.16f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (picked == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🔈", modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp))
                    for (m in marks) SuggestionChip(onClick = { draft.pick(m) }, label = { Text("«${m.word}»") })
                }
                Text(bi("talkInput.unclear"), style = MaterialTheme.typography.bodySmall)
            } else {
                Text("🔈 «${picked.word}»", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { voice.sayAgain(picked) }, enabled = !voice.mic.recording) { Text("🎤 ${bi("talkInput.sayAgain")}") }
                    TextButton(onClick = { onTypeIt(picked) }) { Text("⌨️ ${bi("talkInput.typeIt")}") }
                    TextButton(onClick = { draft.unmark(picked) }) { EmojiLabel("✕", Labels.REMOVE) }
                }
            }
        }
    }
}

/** "Next sentence": ends one sentence with a full stop and records on. */
@Composable
internal fun NextSentence(voice: TalkVoice) {
    val label = bi("talkInput.nextSentence")
    OutlinedButton(onClick = voice::next, modifier = Modifier.semantics { contentDescription = label }) {
        Text("． ${inTarget("talkInput.nextSentence")}", modifier = Modifier.clearAndSetSemantics {})
    }
}

/**
 * Tap to start and tap again to stop (a long pause stops it too), or press and hold to talk and let go. TalkBack sees a
 * button that starts and stops.
 */
@Composable
internal fun HoldMic(voice: TalkVoice, enabled: Boolean, size: Dp = 88.dp) {
    val mic = voice.mic
    val on by rememberUpdatedState(enabled)
    val scale by animateFloatAsState(if (mic.recording) 1f + mic.level * 0.25f else 1f, label = "mic")
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(if (mic.recording) TriglavRed else MaterialTheme.colorScheme.primary)
            .alpha(if (enabled) 1f else 0.4f)
            // A plain pointer handler is invisible to TalkBack: give it a button's label and a tap.
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = if (mic.recording) Labels.STOP else Labels.SPEAK
                if (!enabled) disabled()
                onClick { voice.toggle(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (!on) return@detectTapGestures
                    if (mic.recording) {
                        voice.stop()
                        return@detectTapGestures
                    }
                    val t0 = System.currentTimeMillis()
                    voice.toggle()
                    tryAwaitRelease()
                    if (System.currentTimeMillis() - t0 > HOLD_MS && mic.recording) voice.stop()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (mic.recording) "⏹" else "🎤",
            style = if (size >= 64.dp) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
        )
    }
}

private const val HOLD_MS = 450

@Composable
internal fun MicRationale(onAllow: () -> Unit, onNotNow: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🎤 ${bi("common.microphone")}", style = MaterialTheme.typography.titleMedium)
            Text(
                bi("talkInput.hearLaniNeedsMicrophone"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onNotNow, modifier = Modifier.weight(1f)) { Text("⌨️ ${bi("talkInput.type")}") }
                FilledTonalButton(onClick = onAllow, modifier = Modifier.weight(1f)) { Text(bi("common.allow")) }
            }
        }
    }
}
