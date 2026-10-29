package si.lanisce.lani.ui.talk

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Talk
import si.lanisce.lani.data.TalkState
import si.lanisce.lani.game.scene.Face
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.Portrait
import si.lanisce.lani.ui.stage.Backdrop
import si.lanisce.lani.ui.stage.Expression
import si.lanisce.lani.ui.stage.LineFace
import si.lanisce.lani.ui.stage.Look
import si.lanisce.lani.ui.stage.StageLayout
import si.lanisce.lani.ui.stage.StagePerson
import si.lanisce.lani.ui.stage.StagePlace
import si.lanisce.lani.ui.stage.described
import si.lanisce.lani.ui.stage.portraitPx
import si.lanisce.lani.ui.stage.rememberReducedMotion

/**
 * The talk's stage (companion/README.md, "Talk"): the person the learner talks with ([StagePerson]: the villager's sprite or
 * the scenario's person) as an animated bust on their place, over the conversation. Pure rules here; the composables below.
 */
object TalkStageRules {
    /** The stage's height. */
    const val TALL_DP = 148

    /** The bust on it. */
    const val PORTRAIT_DP = 132

    /** On a tall screen (a tablet upright, [BIG_SCREEN_DP] high and more) the stage and its bust are bigger. */
    const val BIG_TALL_DP = 212
    const val BIG_PORTRAIT_DP = 192
    const val BIG_SCREEN_DP = 1000

    /** The stage's height and its bust's on a screen [screenHeightDp] high: bigger on a tall one. */
    fun size(screenHeightDp: Int): Pair<Int, Int> =
        if (screenHeightDp >= BIG_SCREEN_DP) BIG_TALL_DP to BIG_PORTRAIT_DP else TALL_DP to PORTRAIT_DP

    /** The small avatar in the bar while the stage is folded. */
    const val AVATAR_DP = 40

    /** A line's face by its bubble. */
    const val BUBBLE_DP = 34

    /**
     * Whether the stage shows ([chosen]: the learner's tap on it or on the bar's avatar, null: not chosen): never with the
     * keyboard up; else as chosen, else unless the screen is short (a phone turned, a small one) or the fonts large, as the
     * training stage folds ([StageLayout.compact]).
     */
    fun shown(screenHeightDp: Int, fontScale: Float, keyboard: Boolean, chosen: Boolean?): Boolean =
        !keyboard && (chosen ?: !StageLayout.compact(screenHeightDp, fontScale, false))

    /**
     * How the character looks: listening while the learner's 🎤 records; thinking while their answer is awaited; talking
     * while their line is said, with its face; after it, its face held a while ([Expression]) and then at rest, smiling
     * once the scene is over.
     */
    fun look(st: TalkState, line: LineFace?, now: Long, talking: Boolean, listening: Boolean): Pair<Pose, Face> = when {
        listening -> Pose.LISTEN to Face.NEUTRAL
        st.waiting -> Pose.THINK to Face.THINKING
        else -> {
            val held = Expression.held(line, now, talking)
            Pose.IDLE to (held ?: if (st.sceneOver) Face.HAPPY else Face.NEUTRAL)
        }
    }
}

/**
 * Whether a voice of the app speaks now ([si.lanisce.lani.data.Speaker.talking]: the character's line said, a 🔊): the
 * character is drawn talking. Looked at a few times a second ([si.lanisce.lani.data.Speaker.talking] isn't Compose state).
 */
@Composable
internal fun rememberSpeaking(vm: AppViewModel): State<Boolean> = produceState(false, vm) {
    while (true) {
        value = vm.speaker.talking
        delay(SPEAKING_POLL_MS)
    }
}

private const val SPEAKING_POLL_MS = 120L

/** How the character of [st] looks now ([TalkStageRules.look]), the face's hold kept as their lines come. */
@Composable
internal fun rememberTalkLook(st: TalkState, talking: Boolean, listening: Boolean): Look {
    val last = st.lines.lastOrNull { !it.fromMe }
    val key = st.conversationId to last?.id
    val shownAt = remember(key) { SystemClock.uptimeMillis() }
    var quietAt by remember(key) { mutableLongStateOf(shownAt) }
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(key, talking) {
        if (!talking) quietAt = SystemClock.uptimeMillis()
        now = SystemClock.uptimeMillis()
    }
    val line = last?.let { LineFace(Talk.faceOf(st, it), shownAt, quietAt) }
    LaunchedEffect(line, talking, now, st.waiting, st.sceneOver) {
        val next = Expression.next(line, 0L, now, talking) ?: return@LaunchedEffect
        delay((next - SystemClock.uptimeMillis()).coerceAtLeast(0L) + 1L)
        now = SystemClock.uptimeMillis()
    }
    val (pose, face) = TalkStageRules.look(st, line, now, talking, listening)
    val since = remember(face) { SystemClock.uptimeMillis() }
    return Look(pose, face, talking && !listening && !st.waiting, since)
}

/**
 * The stage: [who] on their [place] (blurred, dimmed), the bust alive as [look] has them (bigger on a tall screen), their
 * name, role and the friendship beside it; shown while [visible] (it folds and unfolds over [StageLayout]'s time), a tap folds it ([onFold]).
 */
@Composable
internal fun TalkStage(who: StagePerson, place: StagePlace, look: Look, visible: Boolean, onFold: () -> Unit) {
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(MOVE_MS)) + fadeIn(tween(MOVE_MS)),
        exit = shrinkVertically(tween(MOVE_MS)) + fadeOut(tween(MOVE_MS)),
        label = "talkStage",
    ) {
        val (tall, bust) = TalkStageRules.size(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp)
        Box(
            Modifier.fillMaxWidth().height(tall.dp)
                .clickable(onClickLabel = bi("stage.makeSmaller"), onClick = onFold),
        ) {
            Backdrop(place, Modifier.matchParentSize(), dim = 0.45f)
            Row(
                Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                Portrait(
                    who.art, if (reduced) look.pose else look.portraitPose, animate = !reduced, px = portraitPx(bust.dp), seed = who.seed,
                    face = look.face,
                    modifier = Modifier.size(bust.dp).clip(shape).border(2.dp, Color.White.copy(alpha = 0.25f), shape)
                        .semantics { contentDescription = described(who.name, look.pose, look.face) },
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(bottom = 14.dp).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(who.name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (who.role.isNotEmpty()) Text(who.role, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (who.level > 0) Text(
                        "♥".repeat(who.level) + "  " + Bonds.NAMES[who.level.coerceIn(0, Bonds.NAMES.lastIndex)],
                        color = Color(0xFFFF8A9A), style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/** How long the stage takes to fold or unfold. */
private const val MOVE_MS = 220
