package si.lanisce.lani.ui.scene

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.PackSession
import si.lanisce.lani.data.Speaker
import si.lanisce.lani.game.scene.SceneObject
import si.lanisce.lani.game.scene.StickerSpot
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.greeting
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.SloBlue
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.words.StickerOr
import si.lanisce.lani.l10n.bi

/**
 * Places [content] next to [target] (view pixels, in this layout's space): above it when there is room
 * below [topLimit], else below it, kept inside the screen, with a small tail pointing at it. [target] is read
 * when placing, so a target that moves (the scene's camera) only moves the bubble; while it's null (out of
 * view) nothing is placed, but the content stays composed (its state, a greeting said once).
 */
@Composable
fun Anchored(target: () -> IntRect?, topLimit: Int, color: Color, modifier: Modifier = Modifier.fillMaxSize(), content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val gap = with(density) { 10.dp.roundToPx() }
    val margin = with(density) { 10.dp.roundToPx() }
    Layout(
        content = {
            Box(Modifier.size(14.dp).rotate(45f).background(color))
            Box { content() }
        },
        modifier = modifier,
    ) { ms, c ->
        val tail = ms[0].measure(Constraints())
        val bubble = ms[1].measure(Constraints(maxWidth = (c.maxWidth - 2 * margin).coerceAtLeast(0), maxHeight = c.maxHeight))
        layout(c.maxWidth, c.maxHeight) {
            val r = target() ?: return@layout
            val cx = (r.left + r.right) / 2
            val x = (cx - bubble.width / 2).coerceIn(margin, (c.maxWidth - margin - bubble.width).coerceAtLeast(margin))
            val above = r.top - gap - bubble.height
            val onTop = above >= topLimit
            val y = if (onTop) above else (r.bottom + gap).coerceAtMost(c.maxHeight - bubble.height).coerceAtLeast(0)
            val tx = cx.coerceIn(x + tail.width, x + bubble.width - tail.width) - tail.width / 2
            tail.place(tx, if (onTop) y + bubble.height - tail.height / 2 - 2 else y - tail.height / 2 + 2)
            bubble.place(x, y)
        }
    }
}

/** Pops in: a quick overshooting scale from [from] to 1 whenever [key] changes. */
@Composable
fun Modifier.popIn(key: Any?, from: Float = 0.6f, origin: TransformOrigin = TransformOrigin(0.5f, 1f)): Modifier {
    val s = remember(key) { Animatable(from) }
    LaunchedEffect(key) { s.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
    return graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = ((s.value - from) / (1f - from)).coerceIn(0f, 1f); transformOrigin = origin }
}

/**
 * A thing's word, over the thing: the picture (the thing's [sticker] when no emoji shows it), the Slovene with its
 * gender (said on arrival), the English, and on tap the example sentence. [fresh] when it was just found for the first time.
 * [note]: when the thing is in the picture, for a word seen before that isn't there now ("🌙 ponoči · at night").
 */
@Composable
fun WordBubble(o: SceneObject, learned: Boolean, fresh: Boolean, speaker: Speaker, color: Color, sticker: StickerSpot? = null, note: String? = null) {
    var example by remember(o.slot) { mutableStateOf(false) }
    val hasExample = !o.exampleSl.isNullOrBlank()
    Surface(
        color = color,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 0.dp,
        modifier = Modifier
            .widthIn(max = 340.dp)
            .popIn(o.slot)
            .animateContentSize()
            .then(if (hasExample) Modifier.clickable(onClickLabel = bi("sceneBubbles.example")) { example = !example } else Modifier),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StickerOr(sticker, 44.dp) { Text(o.emoji ?: "•", fontSize = 34.sp, modifier = Modifier.clearAndSetSemantics { }) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(o.sl.ifBlank { o.word }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        PackSession.genderTag(o.gender)?.let { g ->
                            Spacer(Modifier.width(8.dp))
                            GenderTag(g, genderColor(o.gender))
                        }
                    }
                    Text(o.en, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                SpeakButton(speaker, o.sl.ifBlank { o.word }, Modifier.padding(start = 6.dp))
            }
            if (!note.isNullOrBlank()) {
                Text(note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (fresh) {
                Surface(color = XpGold.copy(alpha = 0.28f), shape = RoundedCornerShape(50)) {
                    Text(
                        "✨ ${bi("sceneBubbles.newFind")}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            if (learned) {
                Text("✓ ${bi("sceneBubbles.knowWord")}", style = MaterialTheme.typography.labelMedium, color = AlpineGreen, fontWeight = FontWeight.SemiBold)
            }
            o.plural?.let { Text(bi("sceneBubbles.pl", "it" to it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (hasExample) {
                if (example) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f, fill = false)) {
                            Text("„${o.exampleSl}“", style = MaterialTheme.typography.titleSmall)
                            o.exampleEn?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        SpeakButton(speaker, o.exampleSl, Modifier.padding(start = 4.dp))
                    }
                } else {
                    Text("👆 ${bi("sceneBubbles.example")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Someone who is just there (no dialog now): they greet the learner for the time of day, in their voice ([SceneWords.voiceOf]). */
@Composable
fun PersonBubble(emoji: String, name: String, line: String?, hour: Int, speaker: Speaker, color: Color, voice: Spoken = Spoken(Clips.FEMALE)) {
    val hello = "${greeting(hour)}!"
    LaunchedEffect(name) { speaker.say(hello, voiceName = voice.voice, fallback = voice.fallback, person = voice.person) }
    Surface(color = color, shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 320.dp).popIn(name)) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(hello, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                line?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            SpeakButton(speaker, hello, Modifier.padding(start = 4.dp), voiceName = voice.voice, fallback = voice.fallback, person = voice.person)
        }
    }
}

/** Over someone with something to say: their happening's emoji, bobbing; a tap starts the dialog. */
@Composable
fun SpeechMarker(emoji: String, description: String, color: Color, onClick: () -> Unit) {
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        0f, -6f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "bob",
    )
    Surface(
        onClick = onClick,
        color = color,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .graphicsLayer { translationY = bob * density }
            .popIn(emoji)
            .semantics { contentDescription = description },
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp).clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.width(4.dp))
            Text("💬", fontSize = 16.sp)
        }
    }
}

/** A round dark button over the pixel art, readable on any scene. */
@Composable
fun OverlayPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(color = Color.Black.copy(alpha = 0.5f), contentColor = Color.White, shape = CircleShape, modifier = modifier) { content() }
}

@Composable
fun GenderTag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
        Text(text, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** Blue for masculine, red for feminine, green for neuter, as on the pack cards. */
fun genderColor(g: String?): Color = when (g) {
    "m" -> SloBlue
    "f" -> TriglavRed
    else -> AlpineGreen
}

/** A ✕ that closes a panel, labelled for TalkBack. */
@Composable
fun CloseButton(onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick) { EmojiLabel("✕", Labels.CLOSE) }
}
