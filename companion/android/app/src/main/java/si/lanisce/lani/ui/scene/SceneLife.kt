package si.lanisce.lani.ui.scene

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.scene.DayAct
import si.lanisce.lani.game.scene.Face
import si.lanisce.lani.game.scene.DayFx
import si.lanisce.lani.game.scene.DaySky
import si.lanisce.lani.game.scene.FxEase
import si.lanisce.lani.game.scene.Happening
import si.lanisce.lani.game.scene.Placement
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.SkyEase
import si.lanisce.lani.game.scene.Stage
import si.lanisce.lani.game.scene.StageEase
import si.lanisce.lani.ui.stage.Expression
import si.lanisce.lani.ui.stage.LineFace
import si.lanisce.lani.ui.stage.Look
import java.time.LocalDate

// The scene takes part in the dialog played in it: the weather, the effects and the stage directions its lines bring,
// and how the person Jan talks to reacts.

/**
 * What a dialog's cues make of the scene: its weather and its effects, each easing toward where the lines have taken it,
 * and its stage ([stage]): where its people are, and who is on their way (companion/SCENES.md, "Stage directions").
 */
data class SceneCues(val sky: SkyEase = SkyEase.CLEAR, val fx: FxEase = FxEase.NONE, val stage: StageEase = StageEase.NONE) {
    companion object {
        val NONE = SceneCues()
    }
}

/**
 * The sky, the effects and the stage of [scene] now: the day's (the storm's while it is on, else what finished dialogs
 * left: [DaySky.over], [DayFx] and [DayAct]; where the happenings [waiting] have their people, [Happening.act]), changed
 * by the dialog [run] played there
 * as its lines go, each change easing in over a few seconds (a person walks, dashes into hiding or pops out of it). A
 * dialog closed before its end takes what it brought with it: the scene eases back to the day's, its people back to
 * their spots.
 */
@Composable
fun rememberSceneCues(state: GameState?, scene: SceneSpec, run: DialogRun?, today: LocalDate, waiting: List<Happening> = emptyList()): SceneCues {
    // the storm's rain while it is on (DaySky.over), over every scene, a room seeing it through its window
    val daySky = DaySky.over(state, today, scene.id)
    val dayFx = DayFx.of(state, today, scene.id)
    val sky = run?.sky(daySky) ?: daySky
    val fx = run?.fx(dayFx) ?: dayFx
    val base = remember(scene, state, today, waiting) { Stage.base(scene, DayAct.of(state, today, scene.id), waiting) }
    val placed: Map<String, Placement> = run?.let { base + Stage.shown(scene.art, it.act(scene.art, base)) } ?: base
    var cues by remember(scene.id) { mutableStateOf(SceneCues(SkyEase.still(sky), FxEase.still(fx), StageEase.still(placed))) }
    LaunchedEffect(sky, fx, placed) {
        val now = SkyEase.clock()
        cues = SceneCues(cues.sky.toward(sky, now), cues.fx.toward(fx, now), cues.stage.toward(placed, now))
    }
    return cues
}

/**
 * How the person Jan talks to looks (see [DialogMood], [Expression]) as the dialog [run] goes on: [facing] (their id; null:
 * whoever spoke last) drawn [talking] while their line is said, listening while Jan speaks into the mic, the face of their
 * newest line held while it is current and a while after, the mood's reaction meanwhile; null [run]: no dialog. [child]:
 * a child laughs at the end where a grown-up is proud.
 */
@Composable
fun rememberPartnerLook(run: DialogRun?, talking: Boolean, listening: Boolean, facing: String? = null, child: Boolean = false): Look {
    val last = remember { arrayOfNulls<DialogRun>(1) }
    var mood by remember { mutableStateOf(Mood.NONE) }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(run) {
        val t = SystemClock.uptimeMillis()
        mood = DialogMood.react(last[0], run, mood, t, child)
        last[0] = run
        now = t
    }
    // their newest line: its face, when it showed, when it was heard to its end
    val said = run?.said.orEmpty()
    val at = said.indexOfLast { it.who != null && (facing == null || it.who == facing) }
    val key = run?.dialog?.id to (run?.seed to at)
    val shownAt = remember(key) { SystemClock.uptimeMillis() }
    var quietAt by remember(key) { mutableLongStateOf(shownAt) }
    // heard to its end: its face holds from now
    LaunchedEffect(key, talking) {
        if (!talking) quietAt = SystemClock.uptimeMillis()
        now = SystemClock.uptimeMillis()
    }
    val line = said.getOrNull(at)?.let { LineFace(DialogMood.lineFace(said, at), shownAt, quietAt) }
    // the next change by itself: a mood's time up, a line's hold over
    LaunchedEffect(mood, line, talking, now) {
        val next = Expression.next(line, mood.until, now, talking) ?: return@LaunchedEffect
        delay((next - SystemClock.uptimeMillis()).coerceAtLeast(0L) + 1L)
        now = SystemClock.uptimeMillis()
    }
    val pose = DialogMood.pose(mood, now, talking, listening)
    val face = Expression.face(line, mood.face, mood.since, mood.on(now), now, talking, listening)
    return rememberLook(pose, face, talking)
}

/** A [Look] of [pose], [face] and [talking], with since when the face shows (kept while it stays). */
@Composable
fun rememberLook(pose: Pose, face: Face, talking: Boolean): Look {
    val since = remember(face) { SystemClock.uptimeMillis() }
    return Look(pose, face, talking, since)
}
