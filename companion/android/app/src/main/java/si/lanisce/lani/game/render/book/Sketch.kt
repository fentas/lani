package si.lanisce.lani.game.render.book

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.StoryPicture
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A picture of the story notebook (companion/SCENES.md, "The story notebook") as a pencil sketch on a card: the same
 * vignette the painter draws ([BookPictures.render], the library's backgrounds, figures and props where the story puts
 * them), gone over by hand. Its ink lines become graphite strokes, [SCALE] times finer, a little wobbly and uneven in
 * pressure, on the paper's tooth; its hatching stays hatching; its washes only a light smudge of graphite where they are
 * dark; and one watercolour at most ([accentOf]: the gold of what shines, else the fire, else the water), laid on a little
 * beside the lines, darker at its edges. The drawing fades out before the card's edge, as a sketch does. Pure Kotlin on a
 * [PixelCanvas] of [W] × [H], so it renders in the JVM tests as on the phone; the same picture always comes out the same.
 */
object Sketch {
    /** How many times finer than the vignette. */
    const val SCALE = 4

    /** The vignette's margin and printer's rule, left out. */
    private const val CROP = 5
    private const val SW = BookPictures.W - 2 * CROP
    private const val SH = BookPictures.H - 2 * CROP
    const val W = SW * SCALE
    const val H = SH * SCALE

    /** The card, graphite, and the paper's shade in its grain. */
    val CARD = Col.hex(0xFBF7EC)
    private val CARD_SHADE = Col.hex(0xEDE5D2)
    val GRAPHITE = Col.hex(0x33353B)

    /** [p] as a sketch on a new canvas. */
    fun render(p: StoryPicture): PixelCanvas = sketch(BookPictures.render(p), accentOf(p), seedOf(p))

    /** The seed of [p]'s hand: the same picture is drawn the same way every time. */
    fun seedOf(p: StoryPicture): Int = listOf(p.bg, p.night, p.chapter, p.after, p.figures, p.props).toString().hashCode()

    /**
     * The one watercolour of [p]: the gold of what shines (Zlatorog's horns, a crown, a bell), else a fire's, else the
     * water's blue where the picture is at the water; none otherwise (graphite alone).
     */
    fun accentOf(p: StoryPicture): Int? = when {
        (p.figures + p.props).any { it.gold } -> InkPal.GOLD
        p.props.any { it.id == "fire" } -> InkPal.FIRE
        !p.night && p.bg in WATERS -> InkPal.WATER
        else -> null
    }

    private val WATERS = setOf("lake", "sea", "river", "bridge")

    /** [src] (a vignette, [BookPictures.W] × [BookPictures.H]) gone over in pencil, [accent] its watercolour. */
    fun sketch(src: PixelCanvas, accent: Int?, seed: Int): PixelCanvas {
        val line = FloatArray(SW * SH)
        val tone = FloatArray(SW * SH)
        val tint = FloatArray(SW * SH)
        val paperLum = Col.lum(InkPal.PAPER)
        for (y in 0 until SH) for (x in 0 until SW) {
            val c = src.pixels[(y + CROP) * src.width + x + CROP]
            val i = y * SW + x
            line[i] = when {
                near(c, InkPal.INK) -> 1f
                near(c, InkPal.SOFT) -> 0.5f
                else -> 0f
            }
            if (line[i] == 0f) tone[i] = ((paperLum - Col.lum(c)) / paperLum).coerceIn(0f, 1f)
            // the sky's blue and the water's are one hue: the water is below the horizon, the sky above it
            if (accent == InkPal.WATER) { if (y + CROP >= HORIZON) tint[i] = strength(c, accent, WASHES - BLUES) }
            else if (accent != null) tint[i] = strength(c, accent, WASHES)
        }
        // the watercolour laid on in a few strokes, not pixel by pixel
        val wash = if (accent != null) spread(tint) else tint
        val out = PixelCanvas(W, H)
        for (y in 0 until H) for (x in 0 until W) {
            // where the hand is: the vignette's point, drifting a little as a hand does
            val wx = (Noise.v2(x / 41f, y / 41f, seed) - 0.5f) * 0.9f
            val wy = (Noise.v2(x / 41f + 19f, y / 41f, seed + 1) - 0.5f) * 0.9f
            val u = (x + 0.5f) / SCALE - 0.5f + wx
            val v = (y + 0.5f) / SCALE - 0.5f + wy
            // the drawing stops short of the card's edge, raggedly
            val d = min(min(x, y), min(W - 1 - x, H - 1 - y)).toFloat()
            val fade = smooth(6f, 30f, d + 18f * (Noise.v2(x / 27f, y / 27f, seed + 5) - 0.5f))
            val tooth = 0.66f + 0.34f * Noise.rnd(x, y, seed + 7)
            var col = Col.mix(CARD, CARD_SHADE, 0.35f * Noise.v2(x / 3.5f, y / 3.5f, seed + 9))
            // the watercolour, a little beside the lines, darker where it dried at its edge
            if (accent != null) {
                val w0 = sample(wash, u + 0.9f, v + 0.6f)
                if (w0 > 0.02f) {
                    val body = smooth(0.15f, 0.55f, w0)
                    val rim = (1f - abs(w0 - 0.4f) * 2.6f).coerceIn(0f, 1f)
                    val grain = 0.8f + 0.4f * Noise.v2(x / 7f, y / 7f, seed + 11)
                    // a wide water only a thin wash; a small thing that shines, full
                    val strong = if (accent == InkPal.WATER) 0.3f else 0.5f
                    val a = ((strong * body + 0.2f * rim) * grain * fade).coerceIn(0f, 0.75f)
                    col = multiply(col, Col.mix(Col.rgb(255, 255, 255), accent, a))
                }
            }
            // the washes' shade, a light smudge of graphite
            val t = sample(tone, u, v)
            val smudge = max(0f, t - 0.12f) * 0.42f * tooth * fade
            if (smudge > 0f) col = Col.mix(col, GRAPHITE, smudge)
            // the lines, graphite, their pressure uneven along the stroke; gone over twice, not quite in the same place
            val l = stroke(line, u, v)
            val again = stroke(line, u + (Noise.v2(x / 13f, y / 13f, seed + 13) - 0.5f) * 1.1f, v + (Noise.v2(x / 13f + 7f, y / 13f, seed + 15) - 0.5f) * 1.1f)
            if (l > 0f || again > 0f) {
                val pressure = 0.6f + 0.4f * Noise.v2(x / 17f, y / 17f, seed + 3)
                val g = (l * pressure + again * 0.28f) * tooth * fade
                col = Col.mix(col, GRAPHITE, g.coerceIn(0f, 0.88f))
            }
            out.pixels[y * W + x] = col
        }
        return out
    }

    /**
     * How much pencil a line of [line] leaves at the vignette's point ([u], [v]): the pen's pixels joined into strokes, a
     * straight one between each two that touch (the corner of a step left out, so steps don't come out as triangles),
     * a dot where a pixel stands alone; a hatching pixel's stroke finer and lighter.
     */
    private fun stroke(line: FloatArray, u: Float, v: Float): Float {
        fun at(x: Int, y: Int): Float = if (x < 0 || y < 0 || x >= SW || y >= SH) 0f else line[y * SW + x]
        val x0 = floor(u).toInt(); val y0 = floor(v).toInt()
        var best = 0f
        for (j in y0 - 1..y0 + 2) for (i in x0 - 1..x0 + 2) {
            val w = at(i, j)
            if (w <= 0f) continue
            best = max(best, w * pen(dist(u, v, i.toFloat(), j.toFloat()), w))
            for (k in STEPS.indices step 2) {
                val di = STEPS[k]; val dj = STEPS[k + 1]
                val wn = at(i + di, j + dj)
                if (wn <= 0f) continue
                // a diagonal step whose corner is drawn already: the corner's two strokes join them
                if (di != 0 && dj != 0 && (at(i + di, j) > 0f || at(i, j + dj) > 0f)) continue
                val ww = min(w, wn)
                best = max(best, ww * pen(segment(u, v, i.toFloat(), j.toFloat(), (i + di).toFloat(), (j + dj).toFloat()), ww))
            }
        }
        return best
    }

    /** [f] spread over its neighbours (1 2 1 each way): the gaps a line or a wave leaves in a wash filled. */
    private fun spread(f: FloatArray): FloatArray {
        val out = FloatArray(f.size)
        val k = floatArrayOf(1f, 2f, 1f)
        for (y in 0 until SH) for (x in 0 until SW) {
            var s = 0f
            for (j in -1..1) for (i in -1..1) {
                val xx = x + i; val yy = y + j
                if (xx in 0 until SW && yy in 0 until SH) s += f[yy * SW + xx] * k[i + 1] * k[j + 1]
            }
            out[y * SW + x] = max(f[y * SW + x], s / 16f * 1.3f).coerceAtMost(1f)
        }
        return out
    }

    /** A pixel's neighbours a stroke goes to: right, down, down right, down left. */
    private val STEPS = intArrayOf(1, 0, 0, 1, 1, 1, -1, 1)

    /** The pencil at distance [d] (vignette pixels) from a stroke's middle: a line's half as wide as a third of a pixel, a hatching's less. */
    private fun pen(d: Float, w: Float): Float {
        val half = if (w >= 1f) 0.3f else 0.2f
        return 1f - smooth(half - 0.11f, half + 0.11f, d)
    }

    private fun dist(x: Float, y: Float, px: Float, py: Float): Float = kotlin.math.hypot(x - px, y - py)

    /** The distance from ([x], [y]) to the segment ([ax], [ay])–([bx], [by]). */
    private fun segment(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val t = (((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return dist(x, y, ax + t * dx, ay + t * dy)
    }

    /** Whether [c] is the pen's colour [pen] (the painter sets them solid). */
    private fun near(c: Int, pen: Int): Boolean =
        abs(Col.r(c) - Col.r(pen)) + abs(Col.g(c) - Col.g(pen)) + abs(Col.b(c) - Col.b(pen)) < 24

    /** The painter's washes: a pixel is taken for the one that explains it best. */
    private val WASHES = listOf(
        InkPal.SKY, InkPal.WATER, InkPal.GRASS, InkPal.LEAF, InkPal.PINE, InkPal.EARTH, InkPal.STONE, InkPal.WOOD,
        InkPal.GOLD, InkPal.RED, InkPal.BLUE, InkPal.NIGHT, InkPal.FIRE,
    )

    /** The blues besides the water's: below the horizon, all water. */
    private val BLUES = setOf(InkPal.SKY, InkPal.BLUE, InkPal.NIGHT)

    /** The row above which a vignette's water never is: its sky's (the lake's, the sea's, the river's all start lower). */
    private const val HORIZON = 46

    /** How much of a wash of [accent] over the paper [c] is: 0 where another of [washes] explains it as well. */
    private fun strength(c: Int, accent: Int, washes: List<Int>): Float {
        val (a, off) = washOf(c, accent)
        if (a < 0.08f || off > 0.1f) return 0f
        if (washes.any { it != accent && washOf(c, it).let { (b, o) -> b >= 0.08f && o < off + 0.02f } }) return 0f
        return (a * 1.8f).coerceIn(0f, 1f)
    }

    /** [c] as a wash of [col] over the paper: how much of it, and how far off that colour it is. */
    private fun washOf(c: Int, col: Int): Pair<Float, Float> {
        val p = InkPal.PAPER
        val dr = (Col.r(p) - Col.r(col)).toFloat(); val dg = (Col.g(p) - Col.g(col)).toFloat(); val db = (Col.b(p) - Col.b(col)).toFloat()
        val cr = (Col.r(p) - Col.r(c)).toFloat(); val cg = (Col.g(p) - Col.g(c)).toFloat(); val cb = (Col.b(p) - Col.b(c)).toFloat()
        val len2 = dr * dr + dg * dg + db * db
        if (len2 < 1f) return 0f to 1f
        val a = ((cr * dr + cg * dg + cb * db) / len2).coerceIn(0f, 1.2f)
        val er = cr - a * dr; val eg = cg - a * dg; val eb = cb - a * db
        return a to kotlin.math.sqrt(er * er + eg * eg + eb * eb) / 255f
    }

    /** [f] at the vignette's point ([u], [v]), between its pixels. */
    private fun sample(f: FloatArray, u: Float, v: Float): Float {
        val x0 = floor(u).toInt(); val y0 = floor(v).toInt()
        val fx = u - x0; val fy = v - y0
        fun at(x: Int, y: Int): Float = if (x < 0 || y < 0 || x >= SW || y >= SH) 0f else f[y * SW + x]
        val a = at(x0, y0); val b = at(x0 + 1, y0); val c = at(x0, y0 + 1); val d = at(x0 + 1, y0 + 1)
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun multiply(a: Int, b: Int): Int = Col.rgb(Col.r(a) * Col.r(b) / 255, Col.g(a) * Col.g(b) / 255, Col.b(a) * Col.b(b) / 255)
}
