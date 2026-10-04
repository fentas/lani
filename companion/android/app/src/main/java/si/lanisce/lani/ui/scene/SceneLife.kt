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
import si.lanisce.lani.game.scene.DayFx
import si.lanisce.lani.game.scene.DaySky
import si.lanisce.lani.game.scene.FxEase
import si.lanisce.lani.game.scene.Placement
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.SkyEase
import si.lanisce.lani.game.scene.Stage
import si.lanisce.lani.game.scene.StageEase
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
 * The sky, the effects and the stage of [scene] now: the day's (what finished dialogs left, [DaySky], [DayFx] and
 * [DayAct]), changed by the dialog [run] played there as its lines go, each change easing in over a few seconds (a person
 * walks, dashes into hiding or pops out of it). A dialog closed before its end takes what it brought with it: the scene
 * eases back to the day's, its people back to their spots.
 */
@Composable
fun rememberSceneCues(state: GameState?, scene: SceneSpec, run: DialogRun?, today: LocalDate): SceneCues {
    val daySky = DaySky.of(state, today, scene.id)
    val dayFx = DayFx.of(state, today, scene.id)
    val sky = run?.sky(daySky) ?: daySky
    val fx = run?.fx(dayFx) ?: dayFx
    val base = remember(scene, state, today) { Stage.base(scene, DayAct.of(state, today, scene.id)) }
    val placed: Map<String, Placement> = run?.let { base + Stage.shown(scene.art, it.act(base)) } ?: base
    var cues by remember(scene.id) { mutableStateOf(SceneCues(SkyEase.still(sky), FxEase.still(fx), StageEase.still(placed))) }
    LaunchedEffect(sky, fx, placed) {
        val now = SkyEase.clock()
        cues = SceneCues(cues.sky.toward(sky, now), cues.fx.toward(fx, now), cues.stage.toward(placed, now))
    }
    return cues
}

/** The pose of the person Jan talks to (see [DialogMood]) as the dialog [run] goes on; null [run]: no dialog. */
@Composable
fun rememberPartnerPose(run: DialogRun?, talking: Boolean, listening: Boolean): Pose {
    val last = remember { arrayOfNulls<DialogRun>(1) }
    var mood by remember { mutableStateOf(Mood.NONE) }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(run) {
        val t = SystemClock.uptimeMillis()
        mood = DialogMood.react(last[0], run, mood, t)
        last[0] = run
        now = t
    }
    // back to idle once the mood's time is up
    LaunchedEffect(mood) {
        val left = mood.until - SystemClock.uptimeMillis()
        if (left > 0) delay(left)
        now = SystemClock.uptimeMillis()
    }
    return DialogMood.pose(mood, now, talking, listening)
}
