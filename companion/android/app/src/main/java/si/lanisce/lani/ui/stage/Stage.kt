package si.lanisce.lani.ui.stage

import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Speaker
import si.lanisce.lani.game.render.Moon
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Portrait
import si.lanisce.lani.l10n.bi
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.ceil

/** The stage of the run on screen, for exercises that react to it (a hint, an audio prompt in the companion's voice). */
val LocalStage = staticCompositionLocalOf<StageState?> { null }

/**
 * The training stage of one run: who's on it, where, and what they do now. The exercise flow calls
 * [prompt], [answered], [hint] and [finish]; [Director] decides the reactions, this plays them: the pose,
 * the line in the bubble, and the voice (unless [muted]).
 */
@Stable
class StageState(
    val who: StagePerson,
    val place: StagePlace,
    seed: Long,
    day: LocalDate,
    private val speaker: Speaker?,
    private val memory: StageMemory?,
    greet: Boolean,
    private val befriend: (String) -> Unit,
) {
    private val director = Director(who, seed, day, greet)
    private var prev: Exercise? = null
    private var finished = false

    /** What they do and say now. */
    var cue by mutableStateOf<Cue?>(null)
        private set
    var pose by mutableStateOf(Pose.IDLE)
        private set
    /** Bumped by every cue, so a pose's hold ends only for the cue it belongs to. */
    var beat by mutableIntStateOf(0)
        private set
    var muted by mutableStateOf(memory?.muted == true)
        private set
    /** The goodbye, once the run is over; the finish screen plays it. */
    var finale by mutableStateOf<Cue?>(null)
        private set

    fun prompt(index: Int, ex: Exercise, fast: Boolean = false) {
        director.prompt(index, ex, prev, fast)?.let(::play)
        prev = ex
    }

    fun answered(verdict: Verdict, hints: Int, ex: Exercise, fast: Boolean) = play(director.answer(verdict, hints, ex, fast))

    fun hint() = play(director.hint())

    /**
     * The run of [total] exercises is over with [verdicts] (what was answered): the goodbye waits for the
     * finish screen, and the friendship grows by a training's points when anything was answered. Once per run.
     */
    fun finish(verdicts: List<Verdict>, total: Int = verdicts.size) {
        if (finished) return
        finished = true
        finale = director.end(verdicts.count { it == Verdict.CORRECT }, verdicts.size, total)
        if (verdicts.isNotEmpty()) who.id?.let(befriend)
    }

    /** The finish screen shows: wave and say goodbye. */
    fun playFinale() {
        val f = finale ?: return
        if (cue != f) play(f)
    }

    /** The pose of cue [beat] has been held long enough. */
    fun settle(beat: Int) {
        if (this.beat == beat) pose = Pose.IDLE
    }

    /** Says the current line again (a tap on the bubble), even when muted: Jan asked for it. */
    fun replay() {
        cue?.line?.let { say(it) }
    }

    fun toggleMute() {
        muted = !muted
        memory?.muted = muted
        if (muted) speaker?.stop()
    }

    /** The voice an audio prompt is read in: the companion's. */
    /** The narrator of their gender, for exercise audio (prebuilt in female and male only); their own lines use [StagePerson.speaker]. */
    val voice: String get() = who.voice

    private fun play(c: Cue) {
        cue = c
        pose = c.pose
        beat++
        if (c.voice && !muted) c.line?.let(::say)
    }

    private fun say(line: VillagerLine) {
        if (speaker?.canSay == true) speaker.say(line.target, voiceName = who.speaker, fallback = who.voice, person = who.id)
    }
}

/**
 * The stage for [run], kept for as long as the screen shows that run. [greet]: the run has no intro, so
 * the companion greets first (the reviews).
 */
@Composable
fun rememberStage(vm: AppViewModel, run: Run, greet: Boolean = false): StageState {
    val context = LocalContext.current
    return remember(run) {
        val memory = StageMemory(context)
        val today = LocalDate.now()
        val state = vm.game.state
        val cast = StageCast.of(vm.villagers.all, state, today)
        val who = Cast.onStage(run, cast, state, today, memory.daily(today))
        val daily = run == Run.Review || (run is Run.Module && run.giver == null) || (run is Run.Pack && run.giver == null)
        if (daily) who.id?.let { memory.pinDaily(today, it) }
        StageState(
            who, StagePlace.of(run, who), state?.seed ?: 0L, today, vm.speaker, memory, greet,
            befriend = { id -> vm.villagers.befriend(id, Bonds.TRAINING, training = true) },
        )
    }
}

/** The system's "remove animations" is on: portraits hold still, bubbles don't slide. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

object StageLayout {
    /** The full band: portrait, backdrop and bubble. */
    const val TALL_DP = 136
    /** Collapsed: a small avatar and one line. */
    const val SMALL_DP = 56

    /** Collapse to the small avatar on short screens, with large fonts, or while the keyboard is open. */
    fun compact(screenHeightDp: Int, fontScale: Float, keyboard: Boolean): Boolean =
        keyboard || fontScale >= 1.3f || screenHeightDp < 640
}

/**
 * The portrait's canvas size for a portrait [size] on screen: whole-number scaling (crisp pixels) that
 * fills it, with a canvas of about 32 px: the sprite from the hat to the waist at its own size.
 */
@Composable
fun portraitPx(size: Dp): Int {
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    // the portraits are the scene sprites cut at the waist, about 30 px: shown at their own pixels, scaled up whole
    val scale = ceil(px / 32.0).toInt().coerceAtLeast(1)
    return (px / scale).coerceAtLeast(16)
}

/** "Babica Micka, navija · cheers": the portrait for TalkBack. */
fun poseLabel(pose: Pose): String = when (pose) {
    Pose.IDLE -> ""
    Pose.TALK -> bi("stage.talks")
    Pose.HAPPY -> bi("stage.smiles")
    Pose.CHEER -> bi("stage.cheers")
    Pose.SAD -> bi("stage.sympathises")
    Pose.THINK -> bi("stage.thinks")
    Pose.LISTEN -> bi("stage.listens")
    Pose.WAVE -> bi("stage.waves")
    Pose.SLEEP -> bi("stage.sleeps")
}

/**
 * The band at the top of an exercise screen: the companion's portrait on a softly lit backdrop of the
 * place, and a speech bubble. Tap it to collapse or expand; it collapses by itself on small screens,
 * with large fonts and while typing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StageBand(stage: StageState, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val keyboard = WindowInsets.isImeVisible
    val auto = !StageLayout.compact(LocalConfiguration.current.screenHeightDp, LocalDensity.current.fontScale, keyboard)
    var chosen by remember { mutableStateOf<Boolean?>(null) }
    val tall = !keyboard && (chosen ?: auto)
    val beat = stage.beat
    LaunchedEffect(beat) {
        stage.cue?.holdMs?.let { delay(it); stage.settle(beat) }
    }
    val who = stage.who
    val line = stage.cue?.line
    val described = listOf(who.name, poseLabel(stage.pose)).filter { it.isNotEmpty() }.joinToString(", ")
    val toggle = Modifier.clickable(onClickLabel = if (tall) bi("stage.makeSmaller") else bi("stage.makeLarger")) { chosen = !tall }
    if (tall) {
        Box(modifier.fillMaxWidth().heightIn(min = StageLayout.TALL_DP.dp).clip(RoundedCornerShape(24.dp)).then(toggle)) {
            Backdrop(stage.place, Modifier.matchParentSize())
            Row(Modifier.fillMaxWidth().heightIn(min = StageLayout.TALL_DP.dp).padding(start = 10.dp, end = 10.dp, top = 10.dp), verticalAlignment = Alignment.Bottom) {
                Portrait(
                    who.art, stage.pose, animate = !reduced, px = portraitPx(118.dp), seed = who.seed,
                    modifier = Modifier.size(118.dp).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .border(2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .semantics { contentDescription = described },
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).align(Alignment.CenterVertically).padding(bottom = 10.dp)) {
                    NameTag(who, Modifier.padding(end = 36.dp))
                    Spacer(Modifier.height(4.dp))
                    Bubble(line, who, reduced, onReplay = stage::replay)
                }
            }
            MuteButton(stage, Modifier.align(Alignment.TopEnd))
        }
    } else {
        Row(
            modifier.fillMaxWidth().heightIn(min = StageLayout.SMALL_DP.dp).clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(Color(stage.place.top), Color(stage.place.bottom)))).then(toggle)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Portrait(
                who.art, stage.pose, animate = !reduced, px = portraitPx(46.dp), seed = who.seed,
                modifier = Modifier.size(46.dp).clip(CircleShape).semantics { contentDescription = described },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                line?.target ?: who.name,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clearAndSetSemantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = spoken(who, line)
                },
            )
            MuteButton(stage)
        }
    }
}

/** What TalkBack announces: "Babica Micka: Bravo! · Well done!". */
private fun spoken(who: StagePerson, line: VillagerLine?): String =
    if (line == null) who.name else "${who.name}: ${line.target}" + line.base.let { if (it.isNotEmpty()) " · $it" else "" }

@Composable
private fun NameTag(who: StagePerson, modifier: Modifier = Modifier) {
    val hearts = "♥".repeat(who.level)
    Row(modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
        Text(who.name, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (hearts.isNotEmpty()) Text("  $hearts", color = Color(0xFFFF8A9A), style = MaterialTheme.typography.labelMedium)
    }
}

/** The speech bubble: the line in the learner's target language, its translation below; announced politely, tap to hear it again. */
@Composable
private fun Bubble(line: VillagerLine?, who: StagePerson, reduced: Boolean, onReplay: () -> Unit, maxLines: Int = 2) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
        onClick = onReplay,
        // One polite announcement per line (not over the exercise), and a tap says it again.
        modifier = Modifier.clearAndSetSemantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = spoken(who, line)
            onClick(label = bi("stage.sayAgain")) { onReplay(); true }
        },
    ) {
        val content: @Composable (VillagerLine?) -> Unit = { l ->
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    l?.target ?: "…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!l?.base.isNullOrEmpty()) Text(
                    l!!.base,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (reduced) content(line)
        else AnimatedContent(line, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut() }, label = "bubble") { content(it) }
    }
}

@Composable
private fun MuteButton(stage: StageState, modifier: Modifier = Modifier) {
    IconButton(onClick = stage::toggleMute, modifier = modifier.size(40.dp)) {
        EmojiLabel(
            if (stage.muted) "🔇" else "🔈",
            if (stage.muted) bi("stage.companionsVoiceOff") else bi("stage.companionsVoice"),
            color = Color.White,
        )
    }
}

/**
 * The place behind the stage and the intro: the scene art of [place] drawn once, blurred (Android 12+)
 * and dimmed so the portrait and the bubble stand out; a gradient of the place's colours without art.
 */
@Composable
fun Backdrop(place: StagePlace, modifier: Modifier = Modifier, dim: Float = 0.5f) {
    val painter = place.art?.let { ScenePainters.of(it) }?.takeIf { it !is ScenePainters.Placeholder }
    Box(modifier.background(Brush.verticalGradient(listOf(Color(place.top), Color(place.bottom))))) {
        if (painter != null) {
            val hour = place.hour ?: LocalTime.now().let { it.hour + it.minute / 60f }
            val image = remember(place, hour.toInt()) {
                runCatching {
                    val canvas = PixelCanvas(BACKDROP_W, BACKDROP_H)
                    painter.render(canvas, SceneFrame(time = 0.0, hour = hour, month = LocalDate.now().monthValue, moon = Moon.now()))
                    Bitmap.createBitmap(BACKDROP_W, BACKDROP_H, Bitmap.Config.ARGB_8888)
                        .apply { setPixels(canvas.pixels, 0, BACKDROP_W, 0, 0, BACKDROP_W, BACKDROP_H) }.asImageBitmap()
                }.getOrNull()
            }
            if (image != null) Image(
                image, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.None,
                modifier = Modifier.matchParentSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(6.dp) else Modifier),
            )
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = dim * 0.75f), Color.Black.copy(alpha = dim)))))
    }
}

private const val BACKDROP_W = 240
private const val BACKDROP_H = 160

/**
 * The goodbye on a finish screen: the companion waves and says the tally and goodbye (voiced once).
 * Nothing when the run had no stage.
 */
@Composable
fun StageFinale(stage: StageState?, modifier: Modifier = Modifier) {
    stage ?: return
    val f = stage.finale ?: return
    LaunchedEffect(f) { delay(500); stage.playFinale() }
    val reduced = rememberReducedMotion()
    val beat = stage.beat
    LaunchedEffect(beat) { stage.cue?.holdMs?.let { delay(it); stage.settle(beat) } }
    val pose = if (stage.cue == f) stage.pose else Pose.WAVE
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(24.dp))) {
            Backdrop(stage.place, Modifier.matchParentSize(), dim = 0.3f)
            Portrait(
                stage.who.art, pose, animate = !reduced, px = portraitPx(96.dp), seed = stage.who.seed,
                modifier = Modifier.matchParentSize().semantics { contentDescription = "${stage.who.name}, ${poseLabel(pose)}" },
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stage.who.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Bubble(f.line, stage.who, reduced, onReplay = stage::replay, maxLines = Int.MAX_VALUE)
        }
    }
}
