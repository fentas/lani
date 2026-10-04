package si.lanisce.lani.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.SloBlue
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Piece(val x: Float, val vx: Float, val vy: Float, val spin: Float, val colorIndex: Int, val w: Float)

private val flag = listOf(SloBlue, TriglavRed, XpGold, AlpineGreen)

/** One burst of confetti in the flag's colours, from the top centre. */
@Composable
fun Confetti(key: Any, modifier: Modifier = Modifier.fillMaxSize()) {
    val colors = flag
    val pieces = remember(key) {
        val r = Random(key.hashCode())
        List(90) { Piece(0.5f + (r.nextFloat() - 0.5f) * 0.2f, (r.nextFloat() - 0.5f) * 1.4f, -0.6f - r.nextFloat() * 0.9f, r.nextFloat() * 1080f, r.nextInt(4), 10f + r.nextFloat() * 10f) }
    }
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(2600, easing = LinearEasing)) }
    if (t.value >= 1f) return
    Canvas(modifier) {
        val time = t.value * 2.6f
        for (p in pieces) {
            val x = (p.x + p.vx * time * 0.35f) * size.width
            val y = (0.05f + p.vy * time * 0.5f + 0.9f * time * time * 0.5f) * size.height
            rotate(p.spin * time, Offset(x, y)) {
                drawRect(colors[p.colorIndex].copy(alpha = 1f - t.value * 0.6f), Offset(x, y), Size(p.w, p.w * 0.5f))
            }
        }
    }
}

private class Spark(val angle: Float, val reach: Float, val spin: Float, val colorIndex: Int, val w: Float)

/** A small burst of confetti in the flag's colours, from the centre of its box outwards: a present unwrapped. */
@Composable
fun Burst(key: Any, modifier: Modifier = Modifier) {
    val sparks = remember(key) {
        val r = Random(key.hashCode())
        List(32) { i -> Spark((i + r.nextFloat() * 0.6f) / 32f * 2f * PI.toFloat(), 0.55f + r.nextFloat() * 0.45f, r.nextFloat() * 720f, r.nextInt(4), 5f + r.nextFloat() * 4f) }
    }
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(950, easing = LinearOutSlowInEasing)) }
    if (t.value >= 1f) return
    Canvas(modifier) {
        val reach = size.minDimension / 2f
        for (s in sparks) {
            val d = reach * s.reach * t.value
            // outwards, then a little down as they slow
            val x = center.x + cos(s.angle) * d
            val y = center.y + sin(s.angle) * d + reach * 0.35f * t.value * t.value
            val w = s.w * density
            rotate(s.spin * t.value, Offset(x, y)) {
                drawRect(flag[s.colorIndex].copy(alpha = 1f - t.value), Offset(x - w / 2f, y - w / 4f), Size(w, w * 0.5f))
            }
        }
    }
}
