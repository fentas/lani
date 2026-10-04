package si.lanisce.lani.ui.game

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import si.lanisce.lani.game.render.Camera
import si.lanisce.lani.game.render.CameraRig
import si.lanisce.lani.game.render.CanvasPoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Where the learner left a camera, to come back to it on the same view. */
internal class CameraMemory {
    var rig: CameraRig? = null
    var home: Camera? = null
    var camera: Camera? = null
}

/**
 * Where the learner left the village's camera, for when they come back from a quest or a scene. Kept for the
 * app process only: a new session starts at the village's home view.
 */
private val TownMemory = CameraMemory()

/**
 * The town view's camera: pinch, pan, fling, double tap, fly-to, coming to rest on crisp whole-number zooms.
 * [home] frames the clearing in the part of the screen the HUD leaves free; [moved] shows the recenter button.
 * A close-up scene looks through one too ([rememberSceneCamera]): home is its whole picture, and no [memory].
 */
@Stable
class TownCameraState internal constructor(private val scope: CoroutineScope, private val memory: CameraMemory?) {
    var rig by mutableStateOf<CameraRig?>(null)
        private set
    var camera by mutableStateOf(Camera(1f, 0f, 0f))
        private set
    var home by mutableStateOf<Camera?>(null)
        private set
    /**
     * The village's detail level on screen (see [si.lanisce.lani.game.render.Lens]): follows the camera's
     * zoom. Zooming in, it steps up only once a gesture or a flight has come to rest, so the picture never
     * changes grain under moving fingers; zooming out, it drops at once, since a closer look's window can't
     * cover a wider view.
     */
    var detail by mutableStateOf(1)
        private set
    private var flight: Job? = null

    /** Away from home (panned or zoomed), so recentring means something. */
    val moved: Boolean
        get() {
            val r = rig ?: return false
            val h = home ?: return false
            return r.distance(camera, h) > 24f
        }

    /** A view, canvas or home (the clearing grew, the HUD got measured): keep the camera if the learner moved it. */
    fun update(rig: CameraRig, home: Camera) {
        val oldRig = this.rig
        val oldHome = this.home
        this.rig = rig
        this.home = home
        when {
            oldRig == null -> { camera = memory?.takeIf { it.rig == rig && it.home == home }?.camera ?: home; rest() }
            oldRig != rig -> { flight?.cancel(); camera = home; rest() }
            oldHome != home && (oldHome == null || rig.distance(camera, oldHome) < 2f) -> flyTo(home)
        }
    }

    /** The camera has come to rest: the detail level follows its zoom. */
    private fun rest() { rig?.let { detail = it.detail(camera.scale) } }

    /** The camera moves (a gesture, a flight): a closer look than the zoom warrants drops at once. */
    private fun move(c: Camera) {
        camera = c
        rig?.let { r -> if (r.detail(c.scale) < detail) detail = r.detail(c.scale) }
    }

    internal fun save() {
        memory?.let { it.rig = rig; it.home = home; it.camera = camera }
    }

    /** One step of a pinch or drag: zoom by [zoom] around the fingers' centroid, then pan. */
    fun gesture(centroid: Offset, pan: Offset, zoom: Float) {
        val r = rig ?: return
        flight?.cancel()
        move(r.pan(r.zoomAt(camera, centroid.x, centroid.y, zoom), pan.x, pan.y))
    }

    /** Fingers up: glide on with the release [velocity] (px/s), then rest on a crisp zoom around [focus]. */
    fun release(velocity: Velocity, focus: Offset) {
        val r = rig ?: return
        flight?.cancel()
        flight = scope.launch {
            // a burst of events with the same timestamp (a scripted swipe) can report an infinite velocity
            val speed = hypot(velocity.x, velocity.y).takeIf { it.isFinite() } ?: 0f
            if (speed > 400f) {
                var last = 0f
                animateDecay(0f, min(speed, 8000f), FloatExponentialDecaySpec(frictionMultiplier = 2.2f)) { value, _ ->
                    val d = value - last
                    last = value
                    move(r.pan(camera, velocity.x / speed * d, velocity.y / speed * d))
                }
            }
            glide(r.settle(camera, focus.x, focus.y), 160)
        }
    }

    /** Double tap: zoom in twice as close at the point, or (already closer than home, or all the way in) back home. */
    fun doubleTap(at: Offset) {
        val r = rig ?: return
        val h = home ?: return
        if (camera.scale >= r.maxScale - 0.01f || camera.scale > h.scale + 0.5f) flyTo(h)
        else flyTo(r.settle(r.zoomAt(camera, at.x, at.y, 2f), at.x, at.y))
    }

    fun recenter() { home?.let { flyTo(it) } }

    /** Zooms by [factor] around the view's centre (TalkBack's zoom actions). */
    fun zoomBy(factor: Float) {
        val r = rig ?: return
        flyTo(r.settle(r.zoomAt(camera, r.viewW / 2f, r.viewH / 2f, factor)))
    }

    /**
     * "Show me": flies to canvas point [p], a little closer than home, centred in the screen [area]
     * (left, top, right, bottom px: the part neither the HUD nor the bottom bar covers).
     */
    fun show(p: CanvasPoint, area: FloatArray) {
        val r = rig ?: return
        val h = home ?: return
        val s = min(r.maxScale, max(h.scale.toInt() + 2, r.minScale * 2)).toFloat()
        flyTo(r.lookAt(p.x, p.y, max(s, camera.scale), (area[0] + area[2]) / 2f, (area[1] + area[3]) / 2f))
    }

    /**
     * Brings canvas point [p] into the screen [area] if it isn't there: keeping the zoom when panning is enough,
     * else zooming in (at the fitted zoom there is nowhere to pan).
     */
    fun reveal(p: CanvasPoint, area: FloatArray) {
        val r = rig ?: return
        fun shows(c: Camera) = c.toScreenX(p.x) in area[0]..area[2] && c.toScreenY(p.y) in area[1]..area[3]
        if (shows(camera)) return
        val cx = (area[0] + area[2]) / 2f; val cy = (area[1] + area[3]) / 2f
        val moved = r.lookAt(p.x, p.y, camera.scale, cx, cy)
        flyTo(if (shows(moved)) moved else r.lookAt(p.x, p.y, max(camera.scale, r.minScale * 2f), cx, cy))
    }

    fun flyTo(target: Camera, ms: Int = 650) {
        flight?.cancel()
        flight = scope.launch { glide(target, ms) }
    }

    private suspend fun glide(target: Camera, ms: Int) {
        val r = rig ?: return
        val from = camera
        if (from == target) { rest(); return }
        animate(0f, 1f, animationSpec = tween(ms, easing = FastOutSlowInEasing)) { t, _ -> move(r.between(from, target, t)) }
        camera = target
        rest()
    }
}

@Composable
fun rememberTownCamera(): TownCameraState {
    val scope = rememberCoroutineScope()
    val state = remember { TownCameraState(scope, TownMemory) }
    DisposableEffect(state) { onDispose { state.save() } }
    return state
}

/**
 * A close-up scene's camera (see [si.lanisce.lani.game.render.CameraRig.scene]): the same gestures and
 * flights as the village's, home at the whole scene; a new one for every [key] (a scene), starting there.
 */
@Composable
fun rememberSceneCamera(key: Any?): TownCameraState {
    val scope = rememberCoroutineScope()
    return remember(key) { TownCameraState(scope, memory = null) }
}

/**
 * Pinch-zoom and drag for the town camera, reporting each step and the release velocity. A tap never moves
 * past the touch slop, so a tap detector next to this one still sees it.
 */
suspend fun PointerInputScope.detectTownGestures(
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onEnd: (velocity: Velocity, focus: Offset) -> Unit,
) {
    awaitEachGesture {
        val slop = viewConfiguration.touchSlop
        var zoom = 1f
        var pan = Offset.Zero
        var moving = false
        var focus = Offset.Zero
        val tracker = VelocityTracker()
        var fingers = 1
        val down = awaitFirstDown(requireUnconsumed = false)
        tracker.addPosition(down.uptimeMillis, down.position)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (!moving) {
                    zoom *= zoomChange
                    pan += panChange
                    val size = event.calculateCentroidSize(useCurrent = false)
                    if (abs(1 - zoom) * size > slop || pan.getDistance() > slop) moving = true
                }
                if (moving) {
                    focus = event.calculateCentroid(useCurrent = false)
                    if (zoomChange != 1f || panChange != Offset.Zero) onGesture(focus, panChange, zoomChange)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isNotEmpty() && pressed.size != fingers) { tracker.resetTracking(); fingers = pressed.size }
                if (pressed.size == 1) tracker.addPosition(pressed[0].uptimeMillis, pressed[0].position)
            }
        } while (!canceled && event.changes.any { it.pressed })
        if (moving) onEnd(if (fingers <= 1) tracker.calculateVelocity() else Velocity.Zero, focus)
    }
}
