package si.lanisce.lani.ui.scene

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.game.render.CameraRig
import si.lanisce.lani.game.render.FADE_MS
import si.lanisce.lani.game.render.Layer
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.Moon
import si.lanisce.lani.game.render.SceneFit
import si.lanisce.lani.game.render.Switch
import si.lanisce.lani.game.render.drawLayer
import si.lanisce.lani.game.render.TownClock
import si.lanisce.lani.game.render.rememberTownClock
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Poked
import si.lanisce.lani.game.scene.Pokes
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.SkyEase
import si.lanisce.lani.game.scene.Sleep
import si.lanisce.lani.game.scene.Stage
import si.lanisce.lani.game.sky.SkyCards
import si.lanisce.lani.game.sky.SkyTap
import si.lanisce.lani.ui.game.TownCameraState
import si.lanisce.lani.ui.game.detectTownGestures
import si.lanisce.lani.ui.game.rememberSceneCamera
import si.lanisce.lani.l10n.bi
import java.time.LocalDateTime
import kotlin.math.floor
import kotlin.math.roundToInt

/** Closer up, the scene's own canvas under the finer picture is drawn again this often (ns). */
private const val BASE_EVERY_NANOS = 500_000_000L

/**
 * The closer look is drawn again once this many times its own render time has passed (and at the latest every
 * tick): on a slow phone it animates less often rather than taking the main thread (at most a third of it).
 */
private const val FINE_SPACING = 3

/** How far (canvas px, at the fitted zoom) a tap may miss a thing and still land on it; closer up, the same on screen. */
private const val TAP_SLOP = 3

/**
 * Where the things of a [SceneView] are on screen, in the view's pixels, so Compose overlays (word bubbles,
 * speech markers) can point at them; and the [camera] the view is looked at through (pinch, pan, double tap).
 * Every position goes through the camera, so what reads one in a layout follows as the camera moves; the hits
 * are updated by the view whenever the painter's change.
 */
@Stable
class SceneAnchors(val camera: TownCameraState) {
    var hits by mutableStateOf<List<SceneHit>>(emptyList())
        internal set

    /** Whether [target] is drawn in the scene (wherever the camera looks). */
    fun drawn(target: SceneTarget): Boolean = hit(target) != null

    /** The top centre of [target] (a thing) or its head (a person). */
    fun anchor(target: SceneTarget): IntOffset? {
        val h = hit(target) ?: return null
        val c = camera.camera
        return IntOffset(c.screenX(h.anchorX), c.screenY(h.anchorY))
    }

    /** The area [target] covers (maybe partly or all out of the view, zoomed in). */
    fun bounds(target: SceneTarget): IntRect? {
        val h = hit(target) ?: return null
        val c = camera.camera
        return IntRect(c.screenX(h.left), c.screenY(h.top), c.screenX(h.right), c.screenY(h.bottom))
    }

    /** The part of [target]'s area inside the view; null when none of it is (zoomed or panned away) or it isn't drawn. */
    fun shown(target: SceneTarget): IntRect? {
        val r = camera.rig ?: return null
        return bounds(target)?.intersect(IntRect(0, 0, r.viewW, r.viewH))?.takeIf { !it.isEmpty }
    }

    /** What's under the view pixel ([px], [py]): the front-most hit. */
    fun at(px: Float, py: Float): SceneTarget? {
        val c = camera.camera
        val cx = c.pixelX(px); val cy = c.pixelY(py)
        return hits.lastOrNull { cx >= it.left && cx < it.right && cy >= it.top && cy < it.bottom }?.target
    }

    private fun hit(target: SceneTarget) = hits.lastOrNull { it.target == target }
}

/** Anchors with their own scene camera, a new pair for every [key] (a scene). */
@Composable
fun rememberSceneAnchors(key: Any?): SceneAnchors {
    val cam = rememberSceneCamera(key)
    return remember(cam) { SceneAnchors(cam) }
}

/**
 * A close-up scene drawn by its painter ([ScenePainters.of] the scene's art) in pixel art: a whole-number
 * upscale like the village (nominal 240×160, more floor or sky rather than a stretch), animated at about
 * 12 fps, lit by the local hour and season. Like the village it can be pinched closer (up to 4 ×), dragged
 * about and double-tapped, coming to rest on whole screen pixels per canvas pixel, never dragged off the view;
 * and like the village it gets finer closer up: from 1.5 × the scene is drawn again at twice the detail, from
 * 2.5 × at three times (see [si.lanisce.lani.game.scene.ScenePainter.render]), just the part in view, the new
 * look fading in over the one before. The scene's own canvas stays underneath (drawn now and then closer up),
 * so a pan between frames never shows a hole, and it gives the overlays their anchors.
 *
 * @param people who is there now ([si.lanisce.lani.game.scene.Happenings.peopleIn]), talking or not
 * @param highlight the object slot or person id to outline, e.g. the one just tapped
 * @param onTap what a tap landed on (through the camera, to the pixel of the picture showing); null for the scenery. The
 *   thing or creature there may react besides, its poke ([si.lanisce.lani.game.scene.Poke]): decoration, nothing more
 * @param anchors the camera, and where things are through it, for overlays
 * @param cues the weather and the effects over it, easing as a dialog's lines bring rain or light the lantern (see [SceneCues])
 * @param world what the village has built ([SceneWorld]): the kozolec, the mill, the maypole show only once they're there
 * @param onPoke a reaction began: its poke's id, and its step and start on [clock] (a sleeper's, [Sleep.pokeId]: step 0
 *   they turn over, 1 they mumble)
 * @param village the village's seed: its wild animals of the day ([si.lanisce.lani.game.scene.Wildlife])
 * @param onSky a tap on the night sky of an outdoor scene where nothing else is: the moon, a star, a planet, a
 *   constellation, the Milky Way, a shooting star; null: the sky isn't tappable
 * @param skyMark what of the sky to show brighter, its card being open ([SkyTap.mark])
 * @param onSkyList TalkBack's "🌌 Nebo nocoj · Tonight's sky" on an outdoor scene: what of the sky the picture shows now, to
 *   open from a list ([si.lanisce.lani.game.sky.SkyTargets.up]); with [onSky], the moon's card is an action too
 * @param clock the picture's clock (its frames, its pokes); the caller's, to time the sounds of what it shows on it too
 * @param taps a tap turn's places (companion/SCENES.md, "Tap turns": spots of the stage, things, people); a tap on one of
 *   them goes to [onTapTurn] instead of [onTap] (the things among them can be tapped meanwhile, words of the scene or not)
 */
@Composable
fun SceneView(
    scene: SceneSpec,
    people: List<PersonInScene>,
    highlight: String?,
    onTap: (SceneTarget?) -> Unit,
    modifier: Modifier = Modifier,
    anchors: SceneAnchors = rememberSceneAnchors(scene.id),
    cues: SceneCues = SceneCues.NONE,
    world: SceneWorld = SceneWorld.ALL,
    onPoke: (String, Poked) -> Unit = { _, _ -> },
    village: Long = 0L,
    onSky: ((SkyTap) -> Unit)? = null,
    skyMark: String? = null,
    onSkyList: ((List<SkyTap>) -> Unit)? = null,
    clock: TownClock = rememberTownClock(),
    taps: List<String> = emptyList(),
    onTapTurn: (String) -> Unit = {},
) {
    // one painter per picture: each hit-tests the last frame it drew
    val painter = remember(scene.art) { ScenePainters.create(scene.art) }
    val closer = remember(scene.art) { ScenePainters.create(scene.art) }
    // a tap turn's things (and the things that hide its spots) can be tapped while it lasts
    val objects = remember(scene, taps) { scene.objects.map { it.slot }.toSet() + Stage.tapSlots(scene.art, taps) }
    val currentTaps by rememberUpdatedState(taps)
    val tapTurn by rememberUpdatedState(onTapTurn)
    // who is where on the stage: at rest, the same list every frame (the pictures redraw only for a new one)
    val settled = remember(people, cues.stage) { cues.stage.apply(people, Double.MAX_VALUE) }
    val shown = remember { arrayOf(settled) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // what reacts to a tap: the painter's things, and whoever sleeps here (they turn over, then mumble)
    val asleep = people.filter { it.pose == Pose.SLEEP }.map { it.id }
    val pokes = remember(scene.art, asleep) { Pokes(painter.pokes + asleep.map(Sleep::poke)) }
    val currentPokes by rememberUpdatedState(pokes)
    val currentAsleep by rememberUpdatedState(asleep)
    val tap by rememberUpdatedState(onTap)
    val poked by rememberUpdatedState(onPoke)
    val tapSky by rememberUpdatedState(onSky)
    val skyList by rememberUpdatedState(onSkyList)
    /** The painter drew the real sky (an outdoor scene): TalkBack gets the moon's card and tonight's sky. */
    var skyShown by remember(scene.art) { mutableStateOf(false) }
    val cam = anchors.camera
    // QA's "zoom" (a debug build's hook, app/QaHooks): closer, as the "Približaj · Zoom in" action
    val qaZooms = si.lanisce.lani.app.QaHooks.zooms
    var qaSeen by remember { mutableStateOf(qaZooms) }
    LaunchedEffect(qaZooms) { if (qaZooms > qaSeen && cam.rig != null) { qaSeen = qaZooms; cam.zoomBy(2f) } }

    val fit = SceneFit.of(size.width, size.height, compact = false)
    val rig = remember(fit, size) { CameraRig.scene(fit, size.width, size.height) }
    if (size.width > 0 && size.height > 0) SideEffect { if (cam.rig != rig) cam.update(rig, rig.fitted()) }
    val pics = remember(fit.width, fit.height) { Pictures(fit.width, fit.height) }
    val currentPics by rememberUpdatedState(pics)
    val currentPainter by rememberUpdatedState(painter)
    val currentCloser by rememberUpdatedState(closer)

    Canvas(
        modifier
            .clipToBounds() // closer up, the picture reaches past the view
            .onSizeChanged { size = it }
            .semantics {
                contentDescription = "${bi("sceneView.scene")}: ${scene.title}"
                customActions = listOfNotNull(
                    CustomAccessibilityAction(bi("common.zoom")) { cam.zoomBy(2f); true },
                    CustomAccessibilityAction(bi("common.zoomOut")) { cam.zoomBy(0.5f); true },
                    CustomAccessibilityAction(bi("common.recenter")) { cam.recenter(); true },
                    // the night sky of an outdoor scene, drawn in pixels: the moon's card, and what's up now, a list to open
                    if (skyShown && onSky != null) CustomAccessibilityAction("🌙 ${bi("sky.moon")}") { tapSky?.invoke(SkyTap.Moon); true } else null,
                    if (skyShown && onSkyList != null) CustomAccessibilityAction("🌌 ${bi("sky.tonight")}") { skyList?.invoke(currentPainter.skyUp()); true } else null,
                )
            }
            .pointerInput(anchors) {
                detectTapGestures(onDoubleTap = { cam.doubleTap(it) }, onTap = { p ->
                    // through the camera to the pixel of the picture showing (what's drawn there, or next to it), then the hit rectangles
                    val c = cam.camera
                    val ps = currentPics
                    val fine = ps.showsFine
                    val layer = if (fine) ps.fine else ps.base
                    val lens = layer.lens
                    val x = floor(lens.cx(c.toCanvasX(p.x))).toInt(); val y = floor(lens.cy(c.toCanvasY(p.y))).toInt()
                    val slop = (cam.rig?.slop(c, TAP_SLOP) ?: TAP_SLOP) * lens.k
                    val drew = if (fine) currentCloser else currentPainter
                    val target = drew.targetAt(layer.canvas, x, y, slop) ?: anchors.at(p.x, p.y)
                    // a tap turn: the place, the thing or the person a choice stands for (or the thing that hides it)
                    val turn = currentTaps
                    if (turn.isNotEmpty()) {
                        val spot = drew.spotAt(c.toCanvasX(p.x), c.toCanvasY(p.y), slop.toFloat() / lens.k)
                        val drawn = shown[0]
                        Stage.tapped(scene.art, target, spot, turn) { id -> drawn.firstOrNull { it.id == id }?.slot }?.let {
                            tapTurn(it)
                            return@detectTapGestures
                        }
                    }
                    // nothing there but the night sky: the moon, a star, a planet … (in the scene canvas's pixels)
                    if (target == null) {
                        val sky = tapSky?.let { drew.skyAt(c.toCanvasX(p.x), c.toCanvasY(p.y), 2.2f * slop / lens.k, System.currentTimeMillis()) }
                        if (sky != null) { tapSky?.invoke(sky); return@detectTapGestures }
                    }
                    // what's there reacts too (the cat stretches, a sleeper turns over), on the frames' clock
                    val sleeper = (target as? SceneTarget.Person)?.id?.takeIf { it in currentAsleep }?.let(Sleep::pokeId)
                    (drew.pokeAt(layer.canvas, x, y, slop) ?: (target as? SceneTarget.Thing)?.slot ?: sleeper)?.let { id ->
                        val now = clock.tick / 1e9
                        if (currentPokes.tap(id, now)) currentPokes.at(now)[id]?.let { poked(id, it) }
                    }
                    tap(target)
                })
            }
            // a drag or a pinch moves past the touch slop and consumes it: the tap detector above lets it go
            .pointerInput(anchors) { detectTownGestures(cam::gesture, cam::release) },
    ) {
        if (size.width == 0 || size.height == 0 || cam.rig != rig) return@Canvas
        val tick = clock.tick // reading the clock here re-runs only the draw phase
        val c = cam.camera
        val ps = pics
        // a painter that can't draw its closer look keeps the scene as it is, magnified
        val k = if (ps.fineFailed) 1 else cam.detail.coerceIn(1, Lens.MAX_DETAIL)
        val sw = ps.switch
        // a detail switch: keep what shows now, to fade the new picture in over it once it's rendered
        if (k != sw.wantK) {
            if (sw.shownK > 0) ps.keep(if (sw.shownK > 1) ps.fine else ps.base)
            sw.wantK = k
        }
        val now = LocalDateTime.now()
        // someone on their way (a walk, a dash into hiding): where they are this frame; at rest the settled list
        val cueNow = SkyEase.clock()
        val cast = if (cues.stage.moving(cueNow)) cues.stage.apply(people, cueNow) else settled
        shown[0] = cast
        val frame = SceneFrame(
            time = tick / 1e9,
            hour = now.hour + now.minute / 60f + now.second / 3600f,
            month = now.monthValue,
            objects = objects,
            people = cast,
            highlight = highlight,
            sky = cues.sky.at(SkyEase.clock()),
            fx = cues.fx.at(SkyEase.clock()),
            pokes = pokes.at(tick / 1e9),
            world = world,
            moon = Moon.now(),
            date = now.toLocalDate(),
            village = village,
            day = now.toLocalDate().toEpochDay(),
            // the real sky over the village this minute, the shooting stars by the clock
            skyNow = SkyCards.now(),
            wall = System.currentTimeMillis(),
            skyMark = skyMark,
        )
        // the scene's canvas: every tick while it is the picture, closer up now and then (the anchors, the edges mid-pan)
        if (ps.drawnBase.stale(if (k == 1) tick else tick / BASE_EVERY_NANOS, cast, highlight, objects)) {
            val t0 = System.nanoTime()
            val hits = painter.render(ps.base.canvas, frame)
            if (BuildConfig.DEBUG) ps.timedBase(scene.art, System.nanoTime() - t0)
            // Overlays follow the things; only a change (a person moving, a new size) updates them.
            if (hits != anchors.hits) anchors.hits = hits
            if (painter.showsSky != skyShown) skyShown = painter.showsSky
            ps.base.lens = Lens(1, 0, 0, ps.w, ps.h)
            ps.base.upload()
            ps.backdrop = painter.backdrop(frame)
        }
        // the closer look: every tick (or as often as its render time allows), at the window the camera sees now
        val due = ps.fine.lens.k != k || tick - ps.fineAt >= ps.fineCost * FINE_SPACING
        if (due) ps.fineKey = tick * 4 + k
        if (k > 1 && ps.drawnFine.stale(ps.fineKey, cast, highlight, objects)) {
            val lens = Lens.of(k, c, size.width, size.height, ps.w, ps.h, ps.fine.w, ps.fine.h)
            try {
                val t0 = System.nanoTime()
                closer.render(ps.fine.canvas, frame, lens)
                val took = System.nanoTime() - t0
                ps.fineCost = if (ps.fineCost == 0L) took else (ps.fineCost * 3 + took) / 4
                ps.fineAt = tick
                if (BuildConfig.DEBUG) ps.timed(scene.art, k, took)
                ps.fine.lens = lens
                ps.fine.upload()
            } catch (e: RuntimeException) {
                Log.w("SceneView", "${scene.art} can't be drawn at detail $k", e)
                ps.fineFailed = true
            }
        }
        // the new picture is there: it is what shows, fading in over the one before
        val ready = if (k > 1) ps.fine.valid && ps.fine.lens.k == k else ps.base.valid
        if (ready && sw.shownK != k) {
            if (sw.shownK > 0 && ps.prev?.valid == true) { sw.fadeFrom = tick; clock.frames = true }
            sw.shownK = k
        }
        var alpha = 1f
        if (sw.fadeFrom >= 0) {
            alpha = ((clock.frame - sw.fadeFrom) / 1e6 / FADE_MS).toFloat().coerceIn(0f, 1f)
            if (alpha >= 1f) { sw.fadeFrom = -1L; clock.frames = false }
        }
        val prev = ps.prev?.takeIf { it.valid }
        val fading = sw.fadeFrom >= 0 && prev != null

        drawRect(Color(ps.backdrop))
        drawLayer(ps.base, c)
        when {
            !ready -> if (prev != null) drawLayer(prev, c) // the picture before, until the new detail is rendered
            k > 1 && fading -> { drawLayer(prev!!, c); drawLayer(ps.fine, c, alpha) }
            k > 1 -> drawLayer(ps.fine, c)
            fading -> drawLayer(prev!!, c, 1f - alpha)
        }
    }
}

/**
 * The pictures of a scene view over a [w] × [h] scene canvas: the scene's canvas ([base]); the closer look ([fine]),
 * a window of the picture at detail 2 or 3 big enough to cover the view from the zoom its detail starts at (1.5 ×
 * for twice the detail: a third more than the canvas); and the picture shown before a detail switch ([prev]).
 */
private class Pictures(val w: Int, val h: Int) {
    val base = Layer(w, h)
    val fine = Layer(w * 4 / 3 + 4, h * 4 / 3 + 4)
    var prev: Layer? = null
        private set
    val switch = Switch()
    val drawnBase = Drawn()
    val drawnFine = Drawn()
    var backdrop = 0
    /** The painter failed to draw the closer look: the scene stays as it is, magnified. */
    var fineFailed = false
    /** The closer look's render time (ns, a running average), when it was last drawn (clock ns), and the key it was drawn for. */
    var fineCost = 0L
    var fineAt = 0L
    var fineKey = 0L

    private var timedNanos = 0L
    private var timedFrames = 0
    private var baseNanos = 0L
    private var baseFrames = 0

    /** Debug builds: the scene canvas's render time, logged every 24 frames. */
    fun timedBase(art: String, nanos: Long) {
        baseNanos += nanos
        if (++baseFrames < 24) return
        Log.d("SceneView", "%s at detail 1: %.1f ms per frame (%d × %d)".format(art, baseNanos / 1e6 / baseFrames, w, h))
        baseNanos = 0L; baseFrames = 0
    }

    /** Debug builds: the closer look's render time, logged every 24 frames, about 2 s (QA reads it). */
    fun timed(art: String, k: Int, nanos: Long) {
        timedNanos += nanos
        if (++timedFrames < 24) return
        Log.d("SceneView", "%s at detail %d: %.1f ms per frame (%d × %d)".format(art, k, timedNanos / 1e6 / timedFrames, fine.w, fine.h))
        timedNanos = 0L; timedFrames = 0
    }

    /** The closer look is what shows (taps go to its pixels). */
    val showsFine: Boolean get() = switch.shownK > 1 && fine.valid

    /** Keeps [from]'s picture to fade from. */
    fun keep(from: Layer) {
        val p = prev?.takeIf { it.w == from.w && it.h == from.h } ?: Layer(from.w, from.h).also { prev = it }
        p.copyFrom(from)
    }
}

/** What the bitmap shows: rendered again only for a new clock tick or new inputs (compared by identity), not for each step of a pinch. */
private class Drawn {
    private var tick = -1L
    private var people: Any? = null
    private var highlight: String? = null
    private var objects: Any? = null
    var backdrop = 0

    fun stale(tick: Long, people: Any?, highlight: String?, objects: Any?): Boolean {
        if (tick == this.tick && people === this.people && highlight == this.highlight && objects === this.objects) return false
        this.tick = tick; this.people = people; this.highlight = highlight; this.objects = objects
        return true
    }
}
