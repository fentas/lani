package si.lanisce.lani.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.ReRecord
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.data.Speaker
import si.lanisce.lani.ui.theme.SloBlue
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget

/** Lani's page in the system settings: after "don't ask again", only there can a permission be allowed. */
fun openAppSettings(context: Context) = runCatching {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** TalkBack labels for buttons whose only label is an emoji, bilingual like the rest of the app. */
object Labels {
    val LISTEN: String get() = bi("common.listen")
    val SLOW: String get() = bi("components.slowly")
    val CLOSE: String get() = bi("common.close")
    val BACK: String get() = bi("components.back")
    val SEND: String get() = bi("common.send")
    val ASK: String get() = bi("common.askTutor")
    val REMOVE: String get() = bi("components.remove")
    val RECORD: String get() = bi("components.record")
    val STOP: String get() = bi("components.stop")
    val DELETE: String get() = bi("common.delete")
    val EDIT: String get() = bi("components.edit")
    val SPEAK: String get() = bi("components.speak")
    val PROGRESS: String get() = bi("common.progress")
    val CHRONICLE: String get() = bi("components.chronicle")
}

/**
 * An emoji that is a button's whole label. TalkBack reads [description] instead of the emoji's
 * Unicode name ("speaker high volume"), and the button takes it over as its own label.
 */
@Composable
fun EmojiLabel(emoji: String, description: String, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified) =
    Text(emoji, style = style, color = color, modifier = Modifier.clearAndSetSemantics { contentDescription = description })

/** The TTS engine for exercise screens, so every prompt can offer read-aloud. */
val LocalSpeaker = staticCompositionLocalOf<Speaker?> { null }

/** The letters a keyboard may lack, per language: Slovene's č š ž, Italian's accents, German's umlauts; none in English. */
fun keyboardLetters(lang: Lang): List<String> = when (lang) {
    Lang.SL -> listOf("č", "š", "ž", "Č", "Š", "Ž")
    Lang.IT -> listOf("à", "è", "é", "ì", "ò", "ù")
    Lang.DE -> listOf("ä", "ö", "ü", "ß", "Ä", "Ö", "Ü")
    Lang.EN -> emptyList()
}

/** č š ž and friends ([keyboardLetters] of [lang], the learner's target), inserted at the cursor. Replaces the c` workaround. */
@Composable
fun DiacriticChips(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier, lang: Lang = L10n.pair.target) {
    val letters = keyboardLetters(lang)
    if (letters.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (c in letters) {
            AssistChip(onClick = { onChange(insertAtCursor(value, c)) }, label = { Text(c, fontSize = 16.sp) })
        }
    }
}

/**
 * The long press of a button that plays [text]: the node records it again ([Speaker.reRecord]: a word Jan hears wrong,
 * an English-sounding take). [busy] while it does, then [note] says what came of it (the new take plays, the day's
 * re-records are spent, offline); the first tap after a week of use shows once where the long press is ([tapped]).
 * Shared by [SpeakButton] and [SayButton].
 */
class ReRecording internal constructor(
    private val speaker: Speaker, private val text: String, private val voiceName: String, private val fallback: String?, private val person: String?,
) {
    var busy by mutableStateOf(false)
        private set
    var note by mutableStateOf<String?>(null)

    /** The node can record again (a 🔊 without that just plays). */
    val can: Boolean get() = speaker.canReRecord

    fun start() {
        if (busy) return
        busy = true
        note = ReRecord.working()
        speaker.reRecord(text, voiceName, fallback, person) { r ->
            busy = false
            note = ReRecord.note(r)
        }
    }

    /** A tap played the line: the long press's tip, once. */
    fun tapped() {
        if (!busy && speaker.reRecordTipOnce()) note = ReRecord.tip()
    }
}

@Composable
fun rememberReRecording(speaker: Speaker, text: String, voiceName: String = Clips.FEMALE, fallback: String? = null, person: String? = null): ReRecording =
    remember(speaker, text, voiceName, fallback, person) { ReRecording(speaker, text, voiceName, fallback, person) }

/** A tap does [onClick] and a long press records again ([r]); TalkBack gets the re-record as the action "Posnemi znova · Re-record". */
fun Modifier.reRecordable(r: ReRecording, action: String, onClick: () -> Unit): Modifier = this
    .combinedClickable(
        role = Role.Button,
        // TalkBack gets it as the custom action below (a long-click label would list it twice)
        onLongClick = if (r.can) r::start else null,
        onClick = {
            onClick()
            r.tapped()
        },
    )
    .semantics { if (r.can) customActions = listOf(CustomAccessibilityAction(action) { r.start(); true }) }

/** [r]'s note over the button: the working note stays until the node answers, the others a few seconds. */
@Composable
private fun ReRecordNote(r: ReRecording) {
    r.note?.let { n ->
        LaunchedEffect(n, r.busy) {
            if (!r.busy) {
                delay(NOTE_MS)
                r.note = null
            }
        }
        NoteBubble(n)
    }
}

/**
 * 🔊 for a line in the learner's target language: a tap says it ([Speaker.say]). A long press has the node record it
 * again ([ReRecording]): a spinner while it does, then the new take plays, or a short note says why not.
 */
@Composable
fun SpeakButton(speaker: Speaker, text: String, modifier: Modifier = Modifier, voiceName: String = Clips.FEMALE, fallback: String? = null, person: String? = null) {
    if (!speaker.canSay) return
    val r = rememberReRecording(speaker, text, voiceName, fallback, person)
    val action = bi("reRecord.action")
    Box(modifier) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .clip(CircleShape)
                .reRecordable(r, action) { speaker.say(text, voiceName = voiceName, fallback = fallback, person = person) },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (r.busy) CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = ReRecord.working() }, strokeWidth = 2.dp)
                else EmojiLabel("🔊", Labels.LISTEN)
            }
        }
        ReRecordNote(r)
    }
}

/**
 * A bigger button that plays [text] (a listening test's 🔊 and 🐢, a model sentence's): a tap plays it ([slow]: slowly),
 * a long press records it again as [SpeakButton]'s does. [tonal]: filled like a tonal button, else outlined.
 */
@Composable
fun SayButton(
    speaker: Speaker, text: String, modifier: Modifier = Modifier, slow: Boolean = false, voiceName: String = Clips.FEMALE,
    tonal: Boolean = true, shape: Shape = ButtonDefaults.shape, content: @Composable () -> Unit,
) {
    val r = rememberReRecording(speaker, text, voiceName)
    val action = bi("reRecord.action")
    // the button fills what [modifier] gives it (a weight, a height), as a Button would
    Box(modifier, propagateMinConstraints = true) {
        Surface(
            shape = shape,
            color = if (tonal) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            contentColor = if (tonal) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
            border = if (tonal) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.clip(shape).reRecordable(r, action) { speaker.say(text, slow = slow, voiceName = voiceName) },
        ) {
            Box(Modifier.heightIn(min = ButtonDefaults.MinHeight).padding(ButtonDefaults.ContentPadding), contentAlignment = Alignment.Center) {
                if (r.busy) CircularProgressIndicator(Modifier.size(24.dp).semantics { contentDescription = ReRecord.working() }, strokeWidth = 2.dp)
                else content()
            }
        }
        ReRecordNote(r)
    }
}

/** How long a note over a 🔊 stays. */
private const val NOTE_MS = 3_500L

/** A short note over a button (above it, else below; kept on the screen), read out by TalkBack. */
@Composable
private fun NoteBubble(text: String) {
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    val position = remember(gap) { AboveOrBelow(gap) }
    Popup(popupPositionProvider = position, properties = PopupProperties(focusable = false)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 4.dp,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.widthIn(max = 280.dp).padding(horizontal = 12.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Places a popup centred above its anchor, or below it when there's no room above, and never off the window's sides. */
private class AboveOrBelow(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - gap - popupContentSize.height
        return IntOffset(x, if (above >= 0) above else anchorBounds.bottom + gap)
    }
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    disabledColor: Color = Color.Unspecified,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp), // grows with large fonts instead of clipping
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = disabledColor),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp)) }
}

/** Whole minutes on screen since [started] (ScreenClock.app.now()), rounded up: a session's duration_minutes (at least 1). */
fun minutesSince(started: Long, now: Long = ScreenClock.app.now()): Int = ((now - started) / 60_000).toInt().coerceAtLeast(0) + 1

/** Where back leads from a screen that opens from both Home and the village. */
fun backLabel(fromVillage: Boolean): String = if (fromVillage) "← ${bi("common.village")}" else "← ${bi("common.home")}"

/** The blue gradient header of the list screens: a back link, then [content], below the status bar. */
@Composable
fun GradientHeader(back: String, onBack: () -> Unit, bottom: Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(SloBlueDeep, SloBlue)))) {
        Column(Modifier.statusBarsPadding().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = bottom)) {
            TextButton(onClick = onBack) { Text(back, color = Color.White) }
            Column(Modifier.padding(start = 12.dp), content = content)
        }
    }
}

/** [GradientHeader] with a title and an optional subtitle. */
@Composable
fun GradientHeader(title: String, subtitle: String?, back: String, onBack: () -> Unit, bottom: Dp = 24.dp) =
    GradientHeader(back, onBack, bottom) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        subtitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
        }
    }

/** A greeting for the time of day in the learner's target language: in Slovene, the ones from lesson 1 and 2. */
fun greeting(hour: Int): String = when (hour) {
    in 4..9 -> inTarget("components.goodMorning")
    in 10..17 -> inTarget("components.goodDay")
    else -> inTarget("components.goodEvening")
}

/**
 * A bar behind the status bar that fades in once [list] scrolls past its first item (an edge-to-edge
 * header), so the clock and icons never sit on top of card text.
 */
@Composable
fun StatusBarScrim(list: LazyListState, modifier: Modifier = Modifier) {
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
    val alpha by animateFloatAsState(if (scrolled) 0.96f else 0f, label = "scrim")
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.background.copy(alpha = alpha)),
    )
}
