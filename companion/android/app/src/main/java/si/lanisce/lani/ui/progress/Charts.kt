package si.lanisce.lani.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.lanisce.lani.data.AccuracyPoint
import si.lanisce.lani.data.DayActivity
import si.lanisce.lani.data.Stats
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Chart colors for the current theme: one hue for data, recessive grid and text. */
data class ChartColors(val data: Color, val surface: Color, val grid: Color, val empty: Color, val text: Color)

private fun DrawScope.line(points: List<Offset>, color: Color) {
    if (points.size < 2) return
    val p = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }
    drawPath(p, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A filled dot with a surface ring, so it stays legible on top of the line. */
private fun DrawScope.dot(at: Offset, color: Color, ring: Color, r: Float = 4.dp.toPx()) {
    drawCircle(ring, r + 2.dp.toPx(), at)
    drawCircle(color, r, at)
}

/** Tiny trend line, e.g. words known per week; the last value gets an end dot. */
@Composable
fun Sparkline(values: List<Int>, color: Color, ring: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val max = (values.max()).coerceAtLeast(1)
        val min = values.min().coerceAtMost(max - 1)
        val pad = 6.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val pts = values.mapIndexed { i, v ->
            Offset(pad + if (values.size == 1) w else w * i / (values.size - 1), pad + h - h * (v - min) / (max - min).toFloat())
        }
        line(pts, color)
        dot(pts.last(), color, ring)
    }
}

/**
 * GitHub-style calendar: one column per week, Monday on top. Sequential single hue by [Stats.intensity].
 * Tapping a day reports it through [onSelect].
 */
@Composable
fun Heatmap(
    calendar: List<List<DayActivity?>>,
    colors: ChartColors,
    selected: LocalDate?,
    description: String,
    onSelect: (DayActivity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.text, fontSize = 10.sp)
    val weeks = calendar.size
    Canvas(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .semantics { contentDescription = description }
            .pointerInput(calendar) {
                detectTapGestures { o ->
                    val g = HeatGeometry(size.width.toFloat(), weeks, density)
                    if (o.x < g.left || o.y < g.top) return@detectTapGestures
                    val w = ((o.x - g.left) / g.step).toInt()
                    val d = ((o.y - g.top) / g.step).toInt()
                    calendar.getOrNull(w)?.getOrNull(d)?.let(onSelect)
                }
            },
    ) {
        val g = HeatGeometry(size.width, weeks, density)
        listOf(0 to "Po", 2 to "Sr", 4 to "Pe", 6 to "Ne").forEach { (row, label) ->
            drawText(tm, label, Offset(g.left - g.labelWidth, g.top + row * g.step + (g.cell - 12.sp.toPx()) / 2), labelStyle)
        }
        var lastLabel = -10
        calendar.forEachIndexed { w, days ->
            val present = days.filterNotNull()
            val first = present.firstOrNull()?.date ?: return@forEachIndexed
            val labelDate = present.firstOrNull { it.date.dayOfMonth == 1 }?.date ?: if (w == 0) first else null
            if (labelDate != null && w - lastLabel >= 3) {
                drawText(tm, MONTHS[labelDate.monthValue - 1], Offset(g.left + w * g.step, 0f), labelStyle)
                lastLabel = w
            }
            days.forEachIndexed { d, day ->
                if (day == null) return@forEachIndexed
                val level = Stats.intensity(day)
                val color = if (level == 0) colors.empty else colors.data.copy(alpha = HEAT_ALPHA[level])
                val tl = Offset(g.left + w * g.step, g.top + d * g.step)
                drawRoundRect(color, tl, Size(g.cell, g.cell), CornerRadius(3.dp.toPx()))
                if (day.date == selected) {
                    drawRoundRect(colors.text, tl, Size(g.cell, g.cell), CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}

val HEAT_ALPHA = listOf(0f, 0.3f, 0.52f, 0.76f, 1f)
private val MONTHS = listOf("jan", "feb", "mar", "apr", "maj", "jun", "jul", "avg", "sep", "okt", "nov", "dec")

/** Cells up to 18 dp, the grid centred with the weekday labels [labelWidth] to its left. */
private class HeatGeometry(width: Float, weeks: Int, density: Float) {
    val labelWidth = 24 * density
    val top = 16 * density
    val step = minOf((width - labelWidth) / weeks, 18 * density)
    val left = labelWidth + (width - labelWidth - weeks * step) / 2
    val cell = step - 3 * density
}

/** Accuracy over time, 0–100 %, x spaced by date. [selected] is an index into [points]. */
@Composable
fun AccuracyChart(
    points: List<AccuracyPoint>,
    colors: ChartColors,
    selected: Int?,
    description: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.text, fontSize = 10.sp)
    fun xs(width: Float, left: Float, right: Float): List<Float> {
        if (points.size == 1) return listOf((left + width - right) / 2)
        val span = ChronoUnit.DAYS.between(points.first().date, points.last().date).coerceAtLeast(1).toFloat()
        return points.map { left + (width - left - right) * ChronoUnit.DAYS.between(points.first().date, it.date) / span }
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(160.dp)
            .semantics { contentDescription = description }
            .pointerInput(points) {
                detectTapGestures { o ->
                    val x = xs(size.width.toFloat(), 40 * density, 12 * density)
                    x.indices.minByOrNull { abs(x[it] - o.x) }?.let(onSelect)
                }
            },
    ) {
        val left = 40.dp.toPx()
        val right = 12.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        fun y(a: Float) = bottom - (bottom - top) * a
        for (t in listOf(0f, 0.5f, 1f)) {
            drawLine(colors.grid, Offset(left, y(t)), Offset(size.width - right, y(t)), 1.dp.toPx())
            val label = "${(t * 100).toInt()} %"
            drawText(tm, label, Offset(0f, y(t) - 7.sp.toPx()), labelStyle)
        }
        if (points.isEmpty()) return@Canvas
        val x = xs(size.width, left, right)
        val pts = points.mapIndexed { i, p -> Offset(x[i], y(p.accuracy)) }
        line(pts, colors.data)
        val focus = selected ?: pts.lastIndex
        pts.forEachIndexed { i, p -> if (i == focus || i == pts.lastIndex) dot(p, colors.data, colors.surface) }
        if (selected != null) drawLine(colors.text.copy(alpha = 0.4f), Offset(pts[focus].x, top), Offset(pts[focus].x, bottom), 1.dp.toPx())
    }
}

/**
 * Columns from one baseline, capped at 24 dp, rounded data end. The [selected] column is drawn at full
 * strength and the others slightly lighter; values live in the caption and the content description.
 */
@Composable
fun ColumnChart(
    values: List<Int>,
    colors: ChartColors,
    selected: Int?,
    description: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val valueStyle = TextStyle(color = colors.text, fontSize = 11.sp)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = description }
            .pointerInput(values) {
                detectTapGestures { o ->
                    if (values.isNotEmpty()) onSelect((o.x / (size.width.toFloat() / values.size)).toInt().coerceIn(0, values.lastIndex))
                }
            },
    ) {
        if (values.isEmpty()) return@Canvas
        val band = size.width / values.size
        val barW = minOf(24.dp.toPx(), band - 2.dp.toPx())
        val top = 16.dp.toPx()
        val base = size.height - 1.dp.toPx()
        val max = values.max().coerceAtLeast(1)
        val r = 4.dp.toPx()
        drawLine(colors.grid, Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
        values.forEachIndexed { i, v ->
            val cx = band * i + band / 2
            val h = (base - top) * v / max
            val focus = i == (selected ?: -1)
            if (v > 0) {
                val rr = RoundRect(
                    left = cx - barW / 2, top = base - h, right = cx + barW / 2, bottom = base,
                    topLeftCornerRadius = CornerRadius(minOf(r, h)), topRightCornerRadius = CornerRadius(minOf(r, h)),
                    bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                )
                drawPath(Path().apply { addRoundRect(rr) }, colors.data.copy(alpha = if (selected == null || focus) 1f else 0.55f))
            }
            if (focus || (selected == null && v == max && v > 0)) {
                val m = tm.measure("$v", valueStyle)
                drawText(m, topLeft = Offset(cx - m.size.width / 2f, base - h - m.size.height - 2.dp.toPx()))
            }
        }
    }
}
