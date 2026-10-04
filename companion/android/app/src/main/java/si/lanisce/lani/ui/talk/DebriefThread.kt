package si.lanisce.lani.ui.talk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.ChatContext
import si.lanisce.lani.data.Talk
import si.lanisce.lani.data.TalkState
import si.lanisce.lani.l10n.bi

/** A thread under the debrief this long (messages) can go on in the tutor chat, where there's more room. */
internal const val LONG_THREAD = 6

/**
 * Under the debrief: "💬 Vprašaj učitelja · Ask your tutor". A question, typed or said with the talk's 🎤 ([voice],
 * kept by the screen), goes to the tutor as a chat about this role-play ([TalkModel.ask]); the answers show above it
 * (TalkEnd lists the thread), and a long thread can go on in the chat.
 */
@Composable
internal fun DebriefAsk(vm: AppViewModel, st: TalkState, voice: TalkVoice, modifier: Modifier = Modifier) {
    val t = vm.talk
    val draft = voice.draft
    val mic = voice.mic
    val focus = remember { FocusRequester() }

    fun send() {
        if (!mic.canSend(true)) return
        draft.take()?.let { t.ask(it.text) }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        t.threadError?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠️ ${bi("talkScreen.notSent")}: $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2)
                TextButton(onClick = t::retryThread) { Text("↻ ${bi("talkScreen.retry")}") }
            }
        }
        if (t.thread.size >= LONG_THREAD) TextButton(
            onClick = {
                vm.chat.continueThread(
                    t.thread.toList(),
                    ChatContext("${st.scenario.emoji} ${st.scenario.title}", Talk.aboutRoleplay(st), key = "about_roleplay"),
                )
            },
            enabled = !t.threadWaiting,
        ) { Text("↗ ${bi("talkScreen.openInChat")}") }
        mic.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (mic.asking) MicRationale(onAllow = voice::allow, onNotNow = voice::notNow)
        MicStatus(mic)
        if (mic.recording && mic.redo == null) NextSentence(voice)
        UnclearHint(voice, onTypeIt = { draft.select(it); focus.requestFocus() })
        DraftRow(
            draft,
            "💬 ${bi("common.askTutor")}",
            canSend = mic.canSend(true),
            onSend = ::send,
            modifier = Modifier.focusRequester(focus),
            answer = false, // a question to the tutor, in any language: the keyboard may help
            mic = { if (voice.canListen()) HoldMic(voice, enabled = true, size = 48.dp) },
        )
    }
}
