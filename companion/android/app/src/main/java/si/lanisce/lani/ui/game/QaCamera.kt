package si.lanisce.lani.ui.game

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln

/**
 * QA's camera moves (a debug or qa build's hook "cam:<move>", app/QaHooks): "zoom:<f>" flies to f × the fitted zoom around
 * the view's middle and rests there; "pinch:<s>" pinches between the fitted zoom and 3 × it for s seconds, one step each
 * frame through [TownCameraState.gesture] (the path two fingers take), then lets go as fingers do. Measures the map while
 * it zooms (companion/bin/qa "zoom-frames"); adb sends no second finger.
 */
internal suspend fun TownCameraState.qaMove(move: String) {
    val r = rig ?: return
    val mid = Offset(r.viewW / 2f, r.viewH / 2f)
    when {
        move.startsWith("zoom:") -> {
            val f = move.removePrefix("zoom:").toFloatOrNull() ?: return
            flyTo(r.settle(r.zoomAt(camera, mid.x, mid.y, r.minScale * f / camera.scale), mid.x, mid.y))
        }
        move.startsWith("pinch:") -> {
            val seconds = move.removePrefix("pinch:").toFloatOrNull() ?: return
            val start = withFrameNanos { it }
            val period = 4e9 // out and in once every 4 s
            while (true) {
                val now = withFrameNanos { it }
                val t = (now - start).toDouble()
                if (t > seconds * 1e9) break
                // the zoom goes as 1 × … 3 × … 1 × of the fitted, smoothly (log scale)
                val u = (1 - cos(2 * PI * t / period)) / 2
                val want = r.minScale * exp(ln(3.0) * u).toFloat()
                gesture(mid, Offset.Zero, want / camera.scale)
            }
            release(Velocity.Zero, mid)
        }
    }
}
