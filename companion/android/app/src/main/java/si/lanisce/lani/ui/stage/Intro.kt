package si.lanisce.lani.ui.stage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import si.lanisce.lani.data.Speaker
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.Portrait
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.game.RewardChips
import si.lanisce.lani.ui.game.clock
import si.lanisce.lani.ui.game.color
import si.lanisce.lani.ui.game.formatRes
import si.lanisce.lani.ui.game.skillLabel
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.WordToken
import si.lanisce.lani.ui.words.lookUpWords

/**
 * Before a run: the villager asks. Their portrait on their place, their request in Slovene (voiced in
 * their voice, English below), the story around it, what Jan will practise and for how long, what it
 * pays, and Start / Later. [extra] adds what a kind of run needs (a role-play's useful phrases).
 */
@Composable
fun IntroSheet(
    info: IntroInfo,
    speaker: Speaker?,
    onStart: () -> Unit,
    onLater: () -> Unit,
    later: String = bi("common.later"),
    lookUp: ((line: String, en: String?) -> (WordToken) -> Unit)? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    BackHandler(onBack = onLater)
    val context = LocalContext.current
    val memory = remember { StageMemory(context) }
    val reduced = rememberReducedMotion()
    // Each "say" plays the request and holds the talking pose about as long as it takes.
    var said by remember(info.request) { mutableIntStateOf(0) }
    var pose by remember(info.request) { mutableStateOf(Pose.IDLE) }
    LaunchedEffect(said, info.request) {
        delay(if (said == 0) 450 else 0)
        pose = Pose.TALK
        if ((said > 0 || !memory.muted) && speaker?.canSay == true) speaker.say(info.request.sl, voiceName = info.who.speaker, fallback = info.who.voice, person = info.who.id)
        delay(Director.talkMs(VillagerLine(info.request.sl, info.request.en)))
        pose = Pose.IDLE
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Asker(info, pose, reduced, lookUp, onSay = { said++ })
            Details(info, speaker, lookUp, Modifier.pullUp(24.dp), extra)
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BigButton(info.start, onClick = onStart, color = if (info.kind == IntroKind.EVENT) TriglavRed else AlpineGreen)
                TextButton(onClick = onLater) { Text(later) }
            }
        }
    }
}

/** Who asks: their place behind them, the portrait, name and role, and the request in a speech bubble. */
@Composable
private fun Asker(info: IntroInfo, pose: Pose, reduced: Boolean, lookUp: ((String, String?) -> (WordToken) -> Unit)?, onSay: () -> Unit) {
    val who = info.who
    Box(Modifier.fillMaxWidth()) {
        Backdrop(info.place, Modifier.matchParentSize(), dim = 0.55f)
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp)) {
            Text(
                "📍 ${info.place.label}",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                val shape = RoundedCornerShape(24.dp)
                Portrait(
                    who.art, pose, animate = !reduced, px = portraitPx(128.dp), seed = who.seed,
                    modifier = Modifier.size(128.dp).clip(shape).border(2.dp, Color.White.copy(alpha = 0.3f), shape)
                        .semantics { contentDescription = listOf(who.name, poseLabel(pose)).filter { it.isNotEmpty() }.joinToString(", ") },
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(bottom = 6.dp)) {
                    Text("${who.emoji} ${who.name}", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (who.role.isNotEmpty()) Text(who.role, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                    if (who.level > 0) Text(
                        "♥".repeat(who.level) + "  " + Bonds.NAMES[who.level.coerceIn(0, Bonds.NAMES.lastIndex)],
                        color = Color(0xFFFF8A9A),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val onWord = lookUp?.invoke(info.request.sl, info.request.en)
                    Column(
                        Modifier.weight(1f).clearAndSetSemantics {
                            contentDescription = "${who.name}: ${info.request.sl}" + if (info.request.en.isNotEmpty()) " · ${info.request.en}" else ""
                            onWord?.let { lookUpWords(info.request.sl, it) }
                        },
                    ) {
                        // a tap on a word opens its card
                        WordText(info.request.sl, onWord, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (info.request.en.isNotEmpty()) Text(
                            info.request.en,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalIconButton(onClick = onSay) { EmojiLabel("🔊", Labels.LISTEN) }
                }
            }
        }
    }
}

/** The sheet under the request: title, story (its words tapped for their card, 🔊), what Jan practises, what it pays. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(
    info: IntroInfo, speaker: Speaker?, lookUp: ((String, String?) -> (WordToken) -> Unit)?, modifier: Modifier,
    extra: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.emoji, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clearAndSetSemantics { })
                Spacer(Modifier.width(10.dp))
                Text(info.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            }
            for (s in info.story) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (s.sl.isNotEmpty()) WordText(s.sl, lookUp?.invoke(s.sl, s.en), style = MaterialTheme.typography.bodyLarge)
                    if (s.en.isNotEmpty()) Text(s.en, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (s.sl.isNotEmpty() && speaker != null) SpeakButton(speaker, s.sl, Modifier.padding(start = 6.dp))
            }
            // against the clock: said plainly before Start
            info.timed?.let { t ->
                Surface(color = XpGold.copy(alpha = 0.18f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t.line, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(t.how, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Section("🎯 ${bi("intro.whatYoullPractise")}") { PracticeView(info) }
            Section("🎁 ${bi("intro.whatGet")}") { PaysView(info) }
            info.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            info.warning?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (MaterialTheme.colorScheme.background.luminanceIsDark()) XpGold else TriglavRed) }
            extra()
        }
    }
}

/** Draws [by] higher and takes that much less room, so the sheet overlaps the backdrop above it without a gap below. */
private fun Modifier.pullUp(by: Dp): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val up = by.roundToPx()
    layout(p.width, (p.height - up).coerceAtLeast(0)) { p.place(0, -up) }
}

private fun Color.luminanceIsDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeView(info: IntroInfo) {
    val p = info.practice
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in p.skills) Pill("${r.emoji} ${r.skillLabel()}", r.color())
    }
    Text(practiceLine(p), style = MaterialTheme.typography.titleMedium)
    if (p.kinds.isNotEmpty()) Text(
        p.kinds.take(4).joinToString("  ·  ") { (k, n) -> "$n× $k" },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (p.passMark > 0 && p.count > 0) Text(
        "🏁 ${inTarget("intro.rightOf", "pass" to p.passMark, "count" to p.count)} " + if (info.kind == IntroKind.EVENT) bi("intro.rightWin") else bi("intro.rightSucceed"),
        style = MaterialTheme.typography.bodyMedium,
    )
    // a request tried before: how close Jan is; one waiting in "Later": why (it plays all the same)
    info.progress?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
    info.waits?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    for (g in p.goals) Text("☐  $g", style = MaterialTheme.typography.bodyLarge)
}

/** "✨ 6 novih besed · 6 new words · 📝 12 vaj · exercises · ⏱ ~5 min". */
fun practiceLine(p: Practice): String = listOfNotNull(
    p.words.takeIf { it > 0 }?.let { "✨ ${bi("intro.newWords", "it" to it)}" },
    p.count.takeIf { it > 0 }?.let { "📝 ${bi("intro.exercises", "it" to it)}" },
    p.goals.size.takeIf { it > 0 }?.let { "🎯 ${bi("intro.goals", "it" to it)}" },
    p.timeLimit?.let { "⏱ ${clock(it)}" } ?: "⏱ ~${p.minutes} min",
).joinToString("  ·  ")

@Composable
private fun PaysView(info: IntroInfo) {
    val pays = info.pays
    if (pays.reward.isNotEmpty()) {
        Text(bi("intro.reward"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RewardChips(pays.reward, startDelayMs = 250)
    }
    if (pays.answers.isNotEmpty()) {
        Text(bi("intro.upAnswers"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatRes(pays.answers), style = MaterialTheme.typography.titleMedium)
    }
    pays.perLine?.let { r ->
        Text(bi("intro.everyLineYouSay", "rEmoji" to r.emoji), style = MaterialTheme.typography.bodyMedium)
    }
    if (pays.bond > 0) Text(
        "+${pays.bond} ♥ ${info.who.short} · ${bi("intro.friendship")}",
        style = MaterialTheme.typography.titleSmall,
        color = if (MaterialTheme.colorScheme.background.luminanceIsDark()) Color(0xFFFF8A9A) else Color(0xFFC2185B),
    )
    pays.stake?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun Pill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
