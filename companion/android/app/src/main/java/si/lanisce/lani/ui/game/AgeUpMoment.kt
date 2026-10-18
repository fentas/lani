package si.lanisce.lani.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import si.lanisce.lani.game.AgeUps
import si.lanisce.lani.game.Beat
import si.lanisce.lani.game.Celebration
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Moment
import si.lanisce.lani.game.MomentFrame
import si.lanisce.lani.game.NewThing
import si.lanisce.lani.game.render.Camera
import si.lanisce.lani.game.render.CanvasPoint
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.theme.XpGold
import kotlin.math.roundToInt

/**
 * An age's moment being played over the village (companion/GAME.md "Ages and their celebrations"): [moment] on the map
 * of [drawn] (the village as it is; played again from the chronicle, as it was the day it grew: [replay]). [elapsed]:
 * the seconds into it, kept by the screen's frames ([rememberMomentClock]); at its end (or skipped) the card.
 */
@Stable
class MomentRun(val moment: Moment, val drawn: GameState, val replay: Boolean) {
    var elapsed by mutableFloatStateOf(0f)
    /** The card shows: the moment's end reached or skipped. */
    var card by mutableStateOf(false)

    /** What shows now ([Moment.at]), at the end once the card shows. */
    val frame: MomentFrame get() = moment.at(if (card) moment.length else elapsed.toDouble())

    /** Straight to the end: the village grown and decorated, the card. */
    fun skip() {
        elapsed = moment.length.toFloat()
        card = true
    }
}

/**
 * Runs [run]'s clock on the screen's frames until its end, then shows the card; a debug build's QA may hold it at one
 * second ("ageup:at:<s>", [si.lanisce.lani.app.QaHooks.ageUpAt]) for its screenshots. With the system's animations off
 * ([reduced]) or TalkBack on ([talkBack]) it goes to the card at once: the village grown and decorated behind it, and the
 * card says what changed.
 */
@Composable
fun rememberMomentClock(run: MomentRun, reduced: Boolean, talkBack: Boolean) {
    val held = si.lanisce.lani.app.QaHooks.ageUpAt
    LaunchedEffect(run, reduced, talkBack, held) {
        if (held != null) { run.card = held >= run.moment.length; run.elapsed = held.coerceIn(0f, run.moment.length.toFloat()); return@LaunchedEffect }
        if (reduced || talkBack) { run.skip(); return@LaunchedEffect }
        val start = withFrameNanos { it } - (run.elapsed * 1e9).toLong()
        while (!run.card) {
            val t = withFrameNanos { it }
            run.elapsed = ((t - start) / 1e9).toFloat()
            if (run.elapsed >= run.moment.length) run.card = true
        }
    }
}

/** What the village screen does from the moment's card. */
class MomentActions(
    /** The card closed: back to the village. */
    val done: () -> Unit,
    /** "🎉 Na veselico · To the party": the party now. */
    val party: () -> Unit,
    /** Something new tapped: where it is. */
    val open: (NewThing) -> Unit,
)

/**
 * The moment over the map (the map itself grows behind it, [MomentRun.frame]): a title at the top as it opens ("Nova doba
 * · A new age"), the beat's caption at the bottom (the change and where, the gathering, the decorations going up, the
 * sign, the celebration's name), the sign's label over the sign at the road ([sign], through the [camera]), "⏭ Preskoči ·
 * Skip"; then the card ([AgeUpCard]). Taps on the map wait while it plays.
 */
@Composable
fun AgeUpOverlay(run: MomentRun, sign: () -> CanvasPoint?, camera: () -> Camera, actions: MomentActions, party: Celebration?, tonight: Celebration?, whatsNew: List<NewThing>) {
    val m = run.moment
    val haptic = LocalHapticFeedback.current
    val f = run.frame
    val beat = if (run.card) null else f.beat
    LaunchedEffect(beat?.kind) {
        when (beat?.kind) {
            Beat.Kind.OPEN, Beat.Kind.CHEER -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            Beat.Kind.GROW -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            else -> Unit
        }
    }
    BackHandler { if (run.card) actions.done() else run.skip() }
    Box(Modifier.fillMaxSize()) {
        if (!run.card) {
            // the map's taps wait while it plays
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {})
            Box(Modifier.fillMaxWidth().heightIn(min = 140.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(min = 200.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
            Title(m, beat, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp))
            SignLabel(m, f, sign, camera)
            Caption(m, beat, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 92.dp))
            Surface(
                onClick = run::skip, color = Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp).heightIn(min = 48.dp),
            ) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text("⏭ ${bi("ageUp.skip")}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            AgeUpCard(m, run.replay, party, tonight, whatsNew, actions)
        }
    }
}

/** "Nova doba · A new age" and the age's name, at the top while the moment plays. */
@Composable
private fun Title(m: Moment, beat: Beat?, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(bi("villageSheets.newAge"), color = XpGold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        val shown = if (beat?.kind == Beat.Kind.OPEN) m.from else m.to
        Text("${m.to.emoji} ${m.to.sl} · ${m.to.en}", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.alpha(if (shown == m.to) 1f else 0.85f))
    }
}

/** The beat's caption, fading from one to the next. */
@Composable
private fun Caption(m: Moment, beat: Beat?, modifier: Modifier) {
    val text = beat?.let { captionOf(m, it) } ?: return
    AnimatedContent(text, modifier, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "caption") { t ->
        Surface(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(
                t, color = Color.White, style = if (beat.kind == Beat.Kind.CHEER) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** What [beat] of [m] says (target · base). */
fun captionOf(m: Moment, beat: Beat): String = when (beat.kind) {
    Beat.Kind.OPEN -> "✨ ${bi("ageUp.open", "age" to m.to.names)}"
    Beat.Kind.GROW -> beat.change?.let { AgeUps.caption(it, m.from, m.to) } ?: ""
    Beat.Kind.GATHER -> "👥 ${bi("ageUp.gather")}"
    Beat.Kind.DECORATE -> "${m.celebration.emoji} ${m.celebration.momentText.bi()}"
    Beat.Kind.SIGN -> "🪧 ${bi("ageUp.sign", "age" to m.to.names)}"
    Beat.Kind.CHEER -> "${m.celebration.emoji} ${m.celebration.name}!"
}

/**
 * The village's name over its sign at the road, from the sign's beat on: the age before until the sign has turned, then
 * the new one, in gold. Where the camera shows the sign.
 */
@Composable
private fun SignLabel(m: Moment, f: MomentFrame, sign: () -> CanvasPoint?, camera: () -> Camera) {
    val turn = f.festive.sign ?: return
    val signBeat = m.beats.firstOrNull { it.kind == Beat.Kind.SIGN } ?: return
    if (f.beat == null || f.beat.from < signBeat.from) return
    val density = LocalDensity.current
    var w by remember { mutableFloatStateOf(0f) }
    val fresh = turn >= 0.5f
    val age = if (fresh) m.to else m.from
    Surface(
        color = if (fresh) XpGold else Color.White, shape = RoundedCornerShape(8.dp), shadowElevation = 4.dp,
        modifier = Modifier
            .offset {
                val p = sign() ?: return@offset IntOffset(-10_000, -10_000)
                val c = camera()
                IntOffset((c.toScreenX(p.x) - w / 2).roundToInt(), (c.toScreenY(p.y) - with(density) { 44.dp.toPx() }).roundToInt())
            }
            .onSizeChanged { w = it.width.toFloat() }
            .clearAndSetSemantics { },
    ) {
        Text("${age.emoji} ${age.sl}", color = Color(0xFF1E2A44), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/**
 * The moment's card: the new age's name, its celebration (and what it is), the party (now: "🎉 Na veselico"; tonight: when;
 * played again from the chronicle: now), "Kaj je novo · What's new" (each to where it is), what changed on the map, and
 * "Naprej · Continue".
 */
@Composable
fun AgeUpCard(m: Moment, replay: Boolean, party: Celebration?, tonight: Celebration?, whatsNew: List<NewThing>, actions: MomentActions) {
    val c = m.celebration
    Box(
        Modifier.fillMaxSize().background(Color(0x99070D1A)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp,
            modifier = Modifier.statusBarsPadding().navigationBarsPadding().padding(16.dp).widthIn(max = 520.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(m.to.emoji, style = MaterialTheme.typography.displaySmall, modifier = Modifier.clearAndSetSemantics { })
                    Text(bi("villageSheets.newAge"), color = XpGold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("${m.to.sl} · ${m.to.en}!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(bi("villageSheets.settlementGrowsBecauseLearn"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                // the celebration, and its party
                Surface(color = XpGold.copy(alpha = 0.16f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${c.emoji} ${c.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(c.about, style = MaterialTheme.typography.bodyMedium)
                        when {
                            party != null -> BigButton("🎉 ${bi("ageUp.toParty")}", onClick = actions.party, color = XpGold)
                            tonight != null -> Text("🌙 ${bi("ageUp.tonight", "name" to tonight.nameText, "hour" to AgeUps.EVENING)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            c.generic && !replay -> Text("🔥 ${bi("ageUp.gathering")}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (whatsNew.isNotEmpty()) {
                    Text("✨ ${bi("ageUp.whatsNew")}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
                    for (n in whatsNew) NewRow(n) { actions.open(n) }
                }
                // what changed on the map (the moment showed it; TalkBack and a skip read it here)
                if (m.changes.isNotEmpty()) {
                    Text("🗺️ ${bi("ageUp.onTheMap")}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
                    for (ch in m.changes) Text(AgeUps.caption(ch, m.from, m.to), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.size(4.dp))
                BigButton(bi("common.continue"), onClick = actions.done)
            }
        }
    }
}

/** Something new: its emoji, what it is, "›" when a tap leads there. */
@Composable
private fun NewRow(n: NewThing, onClick: () -> Unit) {
    val leads = n.go != NewThing.Go.NONE
    val body: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                Text(n.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics { })
            }
            Spacer(Modifier.width(12.dp))
            Text(n.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (leads) Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { })
        }
    }
    if (leads) Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = n.text }) { body() }
    else Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) { }) { body() }
}
