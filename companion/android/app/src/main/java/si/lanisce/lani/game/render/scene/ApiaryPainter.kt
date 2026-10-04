package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.Poke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pri čebelnjaku: the village's bee house as a diorama on its plot of meadow. The čebelnjak stands at the back, a
 * wooden house on a stone footing under a roof of shingles, its open front stacked with three rows of hives, each front
 * a panjska končnica: a little folk painting (a rooster, the hunter and the bear, the devil grinding an old woman's
 * tongue, a heart, the Virgin, a vase of flowers); a lantern on its corner post, lit from dusk. Bees fly out of the
 * hives' mouths to the meadow flowers and back, many in summer, none in winter or at night. In front: the smoker on a
 * stump, a straw hive (koš) on its stump by the house's end; the linden by the bench on the left, in leaf, in bloom in
 * June and July, gold in autumn, bare in winter; on the right the table with jars of honey, a comb in its frame and
 * beeswax candles, the honey extractor beside it with a jar under its tap; a trodden path through the grass. On World
 * Bee Day (20 May) bunting hangs along the eave.
 *
 * Closer up ([fine]) the extra pixels go into the paintings on the hive fronts, the shingles and the boards' grain, the
 * straw of the skep in coils, the comb's cells, the bees' stripes and wings.
 *
 * Tapped ([pokes]): the big bee at the flowers buzzes round in a figure of eight, the second time a wide round of the
 * garden; the painted fronts come up close, one painting after another.
 */
internal class ApiaryPainter : DioramaPainter() {
    override val art = "apiary"
    override val pokes = listOf(
        Poke("bee", listOf(2.4, 3.4), rest = 2.0),
        Poke("panel", listOf(3.2, 3.2, 3.2), rest = 2.0),
    )
    override val plotW = 12.5f
    override val plotD = 11f
    override val rise = 17f

    // the bee house: its body over HX0..HX1 × HY0..HY1 on a footing FT high; the hive fronts in the plane HF, three rows
    // of six; the eave at EAVE, the roof's ridge at RIDGE over RY, the roof out to RX0..RX1 × RYB..RYF
    private val hx0 = 2.3f; private val hx1 = 9.3f; private val hy0 = 0.8f; private val hy1 = 2.9f
    private val ft = 1.2f
    private val hf = 2.72f
    private val eave = 15.6f; private val ridge = 20f; private val ry = 1.85f
    private val rx0 = 2.0f; private val rx1 = 9.6f; private val ryb = 0.35f; private val ryf = 3.35f
    private val rowZ = floatArrayOf(1.7f, 5.5f, 9.3f); private val rowH = 3.4f
    private val hiveX0 = 2.72f; private val hiveW = 1.03f; private val hives = 6

    // the garden
    private val treeX = 1.35f; private val treeY = 6.0f
    private val benchX0 = 0.6f; private val benchX1 = 3.5f; private val benchY = 7.05f
    private val skepX = 10.9f; private val skepY = 1.9f
    private val smokerX = 2.9f; private val smokerY = 4.6f
    private val tx0 = 8.4f; private val tx1 = 10.6f; private val ty0 = 5.3f; private val ty1 = 6.3f; private val tz = 6.4f
    private val exX = 11.65f; private val exY = 5.8f
    private val basinX = 9.3f; private val basinY = 9.1f
    private val flX = 4.7f; private val flY = 8.9f; private val flRx = 2.1f; private val flRy = 1.25f
    private val beeX = 5.3f; private val beeY = 8.5f; private val beeZ = 5.5f

    // the people
    private val hivesPX = 4.3f; private val hivesPY = 4.15f
    private val tablePX = 7.75f; private val tablePY = 6.3f
    private val benchPX = 1.25f; private val benchPY = 7.3f

    private val woodL = Col.hex(0xB07C4A); private val woodM = Col.hex(0x8C5E36); private val woodD = Col.hex(0x6A4426); private val woodX = Col.hex(0x46291A)
    private val shingleL = Col.hex(0x8E6A4E); private val shingleM = Col.hex(0x74553E); private val shingleD = Col.hex(0x563E2E)
    private val honey = Col.hex(0xE8A21E); private val honeyL = Col.hex(0xFFD050); private val honeyD = Col.hex(0xB8700E)
    private val glassC = Col.hex(0xE8F0F4)
    private val tin = Col.hex(0xB8BCC4); private val tinL = Col.hex(0xE0E4EA); private val tinD = Col.hex(0x7A7E88)
    private val straw = Col.hex(0xD8B060); private val strawL = Col.hex(0xF0D080); private val strawD = Col.hex(0xA07C38)
    private val wax = Col.hex(0xF2D878); private val waxD = Col.hex(0xC8A848)
    private val beeC = Col.hex(0x3A2A14); private val beeY0 = Col.hex(0xE8B030)

    /** Closer up (detail 2 or 3): the finer touches the scene's own canvas has no room for. */
    private val fine: Boolean get() = detail > 1

    private fun hyp(x: Float, y: Float): Float = sqrt(x * x + y * y)

    /** How many bees are out: many in summer, fewer in spring and autumn, none in winter, at night or in the rain. */
    private fun beesOut(): Int {
        if (winter || env.dark > 0.45f || frame.sky.rain > 0.3f || frame.sky.snow > 0.2f) return 0
        val base = when (frame.month) { 6, 7 -> 26; 5, 8 -> 22; 4, 9 -> 14; 3, 10 -> 7; else -> 3 }
        return (base * (1f - 0.6f * frame.sky.gloom)).toInt()
    }

    /** World Bee Day (20 May) and the two days after it: bunting on the eave. */
    private val beeDay: Boolean get() = frame.date?.let { it.monthValue == 5 && it.dayOfMonth in 20..22 } ?: false

    override fun paint() {
        fit()
        vignette()
        plot { x, y, px, py -> ground(x, y, px, py) }
        tufts(90, 29) { x, y -> busy(x, y) }
        meadow()
        // the house's shade on the grass in front of its right end
        shade(iso.sx(hx1 + 0.4f, 1.9f), iso.sy(hx1 + 0.4f, 1.9f, 0f), 3.5f * 4 * K, 0.9f * 4 * K, 0.86f, groundId)

        thing("beehouse") { house() }
        // the painted fronts are a word of their own; the bee house's sticker (drawn alone) shows them too
        if (solo == "beehouse") thing("beehouse") { panels() } else thing("panel") { panels() }
        thing("beehouse") { houseFront() }
        prop(outline = 0) { lantern() }
        if (beeDay) prop(outline = 0) { bunting() }
        thing("hive", slop = 1) { skep() }
        thing("tree") { linden() }
        for (p in peopleAt("hives")) { footShadow(hivesPX, hivesPY); personAt(p, hivesPX, hivesPY) }
        prop { stump(smokerX, smokerY, 3f) }
        thing("smoker", slop = 1) { smoker() }
        thing("bench", slop = 1) { bench() }
        for (p in peopleAt("bench")) personAt(p, benchPX, benchPY, 0f, seated = true)
        thing("extractor", slop = 1) { extractor() }
        for (p in peopleAt("table")) { footShadow(tablePX, tablePY); personAt(p, tablePX, tablePY) }
        prop { table() }
        thing("honey", slop = 1) { jars() }
        thing("comb", slop = 1) { comb() }
        thing("wax", slop = 1) { candles() }
        prop { basin() }
        thing("flowers") { flowerHeads() }
        thing("bee", slop = 3) { bigBee() }

        bees()
        swarm(fxOn("swarm"))
        smokeOut()
        honeyRun(fxOn("honey"))
        panelLook()
        fireflies(iso.ix(0f, 11f), iso.iy(6f, 11f, 14f), iso.ix(12f, 6f), iso.iy(12f, 11f, 0f), 9, 13)
        if (env.windows > 0.35f) s.light(iso.sx(hx1 + 0.1f, hy1 + 0.35f), iso.sy(hx1 + 0.1f, hy1 + 0.35f, 12f), 30f, 0.8f * env.windows)
    }

    // ------------------------------------------------------------------ helpers

    /** Like [quadFill], but pixels the shader answers 0 for are left alone. */
    private inline fun quadPlot(
        xa: Float, ya: Float, za: Float, xb: Float, yb: Float, zb: Float,
        xc: Float, yc: Float, zc: Float, xd: Float, yd: Float, zd: Float,
        crossinline shader: (Int, Int) -> Int,
    ) {
        c.polyBegin()
        c.polyAdd(iso.sx(xa, ya), iso.sy(xa, ya, za)); c.polyAdd(iso.sx(xb, yb), iso.sy(xb, yb, zb))
        c.polyAdd(iso.sx(xc, yc), iso.sy(xc, yc, zc)); c.polyAdd(iso.sx(xd, yd), iso.sy(xd, yd, zd))
        c.polyScan { px, py -> val col = shader(px, py); if (col != 0) c.set(px, py, col) }
    }

    /** The plane x = [xp] over [ya]..[yb] × [za]..[zb], the shader getting (y, z); 0 leaves a pixel. */
    private inline fun onLeft(xp: Float, ya: Float, yb: Float, za: Float, zb: Float, crossinline shader: (Float, Float) -> Int) =
        quadPlot(xp, ya, za, xp, yb, za, xp, yb, zb, xp, ya, zb) { px, py -> shader(wallXy(px, xp), wallXz(px, py, xp)) }

    /** The plane y = [yp] over [xa]..[xb] × [za]..[zb], the shader getting (x, z). */
    private inline fun onRight(yp: Float, xa: Float, xb: Float, za: Float, zb: Float, crossinline shader: (Float, Float) -> Int) =
        quadPlot(xa, yp, za, xb, yp, za, xb, yp, zb, xa, yp, zb) { px, py -> shader(wallYx(px, yp), wallYz(px, py, yp)) }

    /** The level z = [z] over [xa]..[xb] × [ya]..[yb], the shader getting (x, y). */
    private inline fun onFlat(z: Float, xa: Float, xb: Float, ya: Float, yb: Float, crossinline shader: (Float, Float) -> Int) =
        quadPlot(xa, ya, z, xb, ya, z, xb, yb, z, xa, yb, z) { px, py -> shader(flatX(px, py, z), flatY(px, py, z)) }

    /** A box: its +y face [front], its +x face [side], its top. */
    private inline fun block(
        xa: Float, ya: Float, xb: Float, yb: Float, za: Float, zb: Float,
        crossinline front: (Float, Float) -> Int, crossinline side: (Float, Float) -> Int, crossinline top: (Float, Float) -> Int,
    ) {
        onRight(yb, xa, xb, za, zb) { x, z -> front(x, z) }
        onLeft(xb, ya, yb, za, zb) { y, z -> side(y, z) }
        onFlat(zb, xa, xb, ya, yb) { x, y -> top(x, y) }
    }

    private fun footShadow(x: Float, y: Float) = shade(iso.sx(x, y), iso.sy(x, y, 0f), 5f * K, 1.6f * K, 0.74f, groundId)

    /** Boards along [a] (cells) at [v] (their own across, cells), with seams every [bw] and grain closer up. */
    private fun board(a: Float, v: Float, bw: Float, light: Float): Int {
        val u = v / bw; val b = floor(u).toInt(); val f = u - b
        val tone = Noise.rnd(b, 71)
        var col = when {
            f < 0.1f -> woodX
            tone < 0.3f -> woodL
            tone > 0.75f -> woodD
            else -> woodM
        }
        if (fine && f >= 0.1f && abs((f * 3f + Noise.v1(a * 2.2f + b * 5f, 72) * 1.3f) % 1f - 0.5f) < 0.06f) col = Col.scale(col, 0.85f)
        return if (light == 1f) col else Col.scale(col, light)
    }

    // ------------------------------------------------------------------ the ground and the meadow

    /** Grass; bare earth in front of the hives, where the beekeeper works; a trodden path from the front. */
    private fun ground(x: Float, y: Float, px: Int, py: Int): Int {
        if (x > hx0 + 0.15f && x < hx1 - 0.15f && y > hy0 + 0.15f && y < hy1 - 0.15f) return Pal.STONE_D
        val g = grassAt(x, y, px, py)
        val front = if (x in hx0 - 0.3f..hx1 + 0.3f && y > hy1 - 0.2f && y < hy1 + 1.5f) 1f - (y - hy1) / 1.5f else -1f
        val t = ((y - 3.4f) / (plotD - 3.4f)).coerceIn(0f, 1f)
        val px0 = 6.1f + (7.3f - 6.1f) * t
        val path = if (y > 3.1f) 1f - abs(x - px0 + (Noise.v1(y * 1.4f, 7) - 0.5f) * 0.3f) / 0.55f else -1f
        val m = max(front, path)
        if (m <= 0f) return g
        return dirtAt(px, py, (m * (0.7f + Noise.v2(x * 2f, y * 2f, 3) * 0.5f)).coerceIn(0f, 1f), g)
    }

    /** The ground under the bee house's roof, the bare earth in front of the hives under its eaves too: the rain keeps off it. */
    override fun sheltered(x: Float, y: Float): Boolean = x > rx0 && x < rx1 && y > ryb && y < ryf

    private fun busy(x: Float, y: Float): Boolean =
        (x in hx0 - 0.4f..rx1 + 0.3f && y < hy1 + 0.4f) || hyp(x - treeX, y - treeY) < 0.7f || hyp(x - skepX, y - skepY) < 0.8f ||
            (x in tx0 - 0.2f..exX + 0.8f && y in ty0 - 0.3f..ty1 + 0.5f) || hyp(x - basinX, y - basinY) < 0.9f || flower(x, y) < 1.1f || (x in benchX0 - 0.2f..benchX1 + 0.2f && abs(y - benchY) < 0.5f)

    /** How far ([x], [y]) is into the flower bed: under 1 inside. */
    private fun flower(x: Float, y: Float): Float = hyp((x - flX) / flRx, (y - flY) / flRy)

    /** The meadow's flowers in the bed as the season has them (dandelions and daisies in spring, phacelia and clover in summer, asters in autumn); snow in winter. */
    private fun meadow() {
        if (winter) {
            prop(outline = 0) { c.fillEllipse(iso.sx(flX, flY), iso.sy(flX, flY, 0f), flRx * 4.2f * K, flRy * 2.4f * K, Pal.SNOW_M) }
            return
        }
        prop(outline = 0) {
            // the leaves: a darker, taller green
            val cx = iso.sx(flX, flY); val cy = iso.sy(flX, flY, 0f)
            val rx = (flRx + flRy) * 3.2f * K; val ryy = (flRx + flRy) * 1.1f * K
            for (py in max(c.top, floor(cy - ryy - 2f * K).toInt())..min(c.bottom - 1, floor(cy + ryy).toInt())) for (px in max(c.left, floor(cx - rx).toInt())..min(c.right - 1, floor(cx + rx).toInt())) {
                val x = flatX(px, py, 0f); val y = flatY(px, py, 0f)
                val f = flower(x, y)
                if (f > 1f) continue
                val n = Noise.rnd(px / detail, py / detail, 33)
                c.set(px, py, if (n < 0.35f) Col.hex(0x3E7A34) else if (n < 0.7f) Col.hex(0x4E8C3A) else Col.hex(0x2E6428))
            }
        }
    }

    /** The blooms over the bed: a dot of colour each, a stalk under it. */
    private fun flowerHeads() {
        if (winter) {
            // the bed under snow: a few dry stalks stick out
            for (j in 0 until 10) {
                val x = flX + (Noise.rnd(j, 34) - 0.5f) * flRx * 1.6f; val y = flY + (Noise.rnd(j, 35) - 0.5f) * flRy * 1.6f
                val bx = iso.ix(x, y); val by = iso.iy(x, y, 0f)
                c.fillRect(bx, by - 2 * K, detail, 2 * K, Col.hex(0x8A7A5A))
            }
            return
        }
        val m = frame.month
        val (a, b, cc) = when (m) {
            3, 4, 5 -> Triple(Col.hex(0xF6D83A), Col.hex(0xF8F6EC), Col.hex(0xF6D83A)) // dandelions, daisies
            6, 7, 8 -> Triple(Col.hex(0x8A7AD8), Col.hex(0xE87AA8), Col.hex(0xF4F2EA)) // phacelia, clover
            9, 10 -> Triple(Col.hex(0x9A6AD0), Col.hex(0xE8C040), Col.hex(0x9A6AD0)) // asters
            else -> Triple(Col.hex(0xD8D0B0), Col.hex(0xC8B890), Col.hex(0xD8D0B0))
        }
        val n = if (m in 4..9) 34 else 16
        for (j in 0 until n) {
            val ang = Noise.rnd(j, 36) * 2f * PI.toFloat(); val r = sqrt(Noise.rnd(j, 37)) * 0.95f
            val x = flX + cos(ang) * r * flRx; val y = flY + sin(ang) * r * flRy
            val bx = iso.ix(x, y); val by = iso.iy(x, y, 0f)
            val hgt = (1.6f + Noise.rnd(j, 38) * 1.6f) * K
            val col = when (j % 3) { 0 -> a; 1 -> b; else -> cc }
            c.fillRect(bx, floor(by - hgt).toInt(), detail, floor(hgt).toInt(), Col.hex(0x3A6A2E))
            val sway = floor(sin(t * 1.3 + j) * 0.4f * gust).toInt() * detail
            c.fillRect(bx - detail + sway, floor(by - hgt).toInt() - detail, 2 * detail + (if (fine) detail else 0), 2 * detail, col)
            if (fine) c.fillRect(bx + sway, floor(by - hgt).toInt() - detail, detail, detail, Col.mix(col, Col.hex(0xFFF6A0), 0.5f))
        }
    }

    // ------------------------------------------------------------------ the bee house

    /** The bee house: its stone footing, the end of planks under the gable, the roof of shingles. */
    private fun house() {
        // the footing
        block(hx0, hy0, hx1, hy1, 0f, ft, { _, z -> if (z > ft - 0.3f) Pal.STONE_L else Pal.STONE_M }, { _, _ -> Pal.STONE_D }, { _, _ -> Pal.STONE_M })
        // the right end: boards up to the eave, the gable over it
        onLeft(hx1, hy0, hy1, ft, ridge) { y, z ->
            val top = eave + (ridge - eave) * (1f - abs(y - ry) / (ry - ryb))
            if (z > top) 0 else if (z > eave - 0.4f && z < eave + 0.2f) woodX else board(z, y, 0.34f, 0.8f)
        }
        // the roof: shingles in staggered rows from the eave up to the ridge, snow on it in winter
        quadFill(rx0, ryf, eave, rx1, ryf, eave, rx1, ry, ridge, rx0, ry, ridge) { px, py ->
            // the slope's point under the pixel: s from the eave (0) up to the ridge (1), x along it
            val sl = ((2f * ua(px) - vb(py) + 4f * ryf - eave) / (4f * (ryf - ry) + (ridge - eave))).coerceIn(0f, 1f)
            shingle(ua(px) + ryf - sl * (ryf - ry), sl, px, py)
        }
        // the gable's bargeboards along the roof's right edge
        rodIso(rx1, ryf, eave - 0.3f, rx1, ry, ridge - 0.3f, woodD)
        rodIso(rx1, ry, ridge - 0.3f, rx1, ryb, eave - 0.3f, woodD)
        rodIso(rx0, ry, ridge + 0.2f, rx1, ry, ridge + 0.2f, woodX)
    }

    /** The front slope's shingles at [x] cells along, [u] of the way up from the eave: rows staggered, moss here and there, snow in winter. */
    private fun shingle(x: Float, u: Float, px: Int, py: Int): Int {
        if (winter && (u > 0.12f || Noise.rnd(px, py, 45) < 0.7f)) return if (Dither.at(px, py) < 0.8f) Pal.SNOW_L else Pal.SNOW_M
        val rows = 7f
        val r = floor(u * rows).toInt(); val fr = u * rows - r
        val a = x / 0.42f + (r and 1) * 0.5f; val fa = a - floor(a)
        val tone = Noise.rnd(floor(a).toInt(), r, 46)
        return when {
            fr < 0.18f -> shingleD
            fa < (if (fine) 0.07f else 0.12f) -> shingleD
            fine && fr > 0.85f -> shingleL
            tone < 0.3f -> shingleL
            tone > 0.82f -> Col.mix(shingleM, Col.hex(0x6E7A5A), 0.35f) // moss
            else -> shingleM
        }
    }

    /** A rod a scene px thick between two world points. */
    private fun rodIso(xa: Float, ya: Float, za: Float, xb: Float, yb: Float, zb: Float, col: Int, n: Int = detail) {
        val ax = iso.ix(xa, ya); val ay = iso.iy(xa, ya, za); val bx = iso.ix(xb, yb); val by = iso.iy(xb, yb, zb)
        val steep = abs(by - ay) > abs(bx - ax)
        for (o in 0 until n) if (steep) c.line(ax + o, ay, bx + o, by, col) else c.line(ax, ay + o, bx, by + o, col)
    }

    /** The house's front round the hives: corner posts, the ledges under each row, the board over the top row. */
    private fun houseFront() {
        val yf = hy1
        // the band over the hives up to the eave
        onRight(hf, hx0, hx1, rowZ[2] + rowH + 0.3f, eave) { x, z -> board(x, z, 0.9f, 1f) }
        // the ledges: the bees' landing boards under each row
        for (z in rowZ) block(hiveX0 - 0.05f, hf, hiveX0 + hives * hiveW + 0.05f, hf + 0.28f, z - 0.45f, z - 0.05f,
            { _, zz -> if (zz > z - 0.2f) woodL else woodD }, { _, _ -> woodD }, { _, _ -> woodL })
        // the corner posts
        for (x in floatArrayOf(hx0, hx1 - 0.35f)) block(x, yf - 0.35f, x + 0.35f, yf, ft, eave, { _, _ -> woodM }, { _, _ -> woodD }, { _, _ -> woodL })
    }

    /** The hive fronts, three rows of six: each a painted board with its own picture, the flight slit under it. */
    private fun panels() {
        for ((r, z) in rowZ.withIndex()) for (h in 0 until hives) {
            val xa = hiveX0 + h * hiveW; val xb = xa + hiveW - 0.05f
            val motif = MOTIFS[Math.floorMod(Noise.hash(r, h, 47), MOTIFS.size)]
            onRight(hf, xa, xb, z, z + rowH) { x, zz ->
                val u = (x - xa) / (xb - xa); val v = 1f - (zz - z) / rowH
                val eu = min(u, 1f - u) * (xb - xa) * 4.47f; val ev = min(v, 1f - v) * rowH
                when {
                    eu < 0.3f || ev < 0.3f -> woodX
                    // the flight slit in the frame's foot
                    v > 0.86f && abs(u - 0.5f) < 0.2f -> Col.hex(0x1A120C)
                    v > 0.8f -> woodD
                    else -> motif((u - 0.06f) / 0.88f, (v - 0.06f) / 0.76f)
                }
            }
        }
    }

    /** The lantern on the corner post: dark by day, lit from dusk. */
    private fun lantern() {
        val bx = iso.sx(hx1 + 0.05f, hy1 + 0.35f); val by = iso.sy(hx1 + 0.05f, hy1 + 0.35f, 12.8f)
        c.fillRect(floor(bx).toInt() - detail, floor(by - 3.4f * K).toInt(), 2 * detail + K, detail, Pal.STONE_X)
        c.fillRect(floor(bx + K).toInt(), floor(by - 3.4f * K).toInt(), detail, K, Pal.STONE_X)
        val lx = bx + K + detail / 2f; val ly = by - 1.4f * K
        c.fillRect(floor(lx - 0.9f * K).toInt(), floor(ly - 1.3f * K).toInt(), floor(1.8f * K).toInt() + 1, floor(2.4f * K).toInt(), Col.hex(0x2A2628))
        val lit = env.windows > 0.35f
        val inner = if (lit) Pal.WINDOW_LIT else Col.hex(0x5A6068)
        if (lit) glow { c.fillRect(floor(lx - 0.5f * K).toInt(), floor(ly - 0.9f * K).toInt(), K, floor(1.6f * K).toInt(), inner) }
        else c.fillRect(floor(lx - 0.5f * K).toInt(), floor(ly - 0.9f * K).toInt(), K, floor(1.6f * K).toInt(), inner)
    }

    /** Bunting for World Bee Day: little flags in white, blue and red along the eave. */
    private fun bunting() {
        val cols = intArrayOf(Pal.FLAG_WHITE, Pal.FLAG_BLUE, Pal.FLAG_RED)
        val n = 14
        for (i in 0 until n) {
            val u = (i + 0.5f) / n
            val x = rx0 + 0.2f + (rx1 - rx0 - 0.4f) * u
            val sag = 1.4f * 4f * u * (1f - u)
            val bx = iso.sx(x, ryf + 0.02f); val by = iso.sy(x, ryf + 0.02f, eave - 0.4f - sag)
            val col = cols[i % 3]
            val wave = sin(t * 3 + i).toFloat() * 0.3f * K
            c.polyBegin(); c.polyAdd(bx - 0.9f * K, by); c.polyAdd(bx + 0.9f * K, by + 0.45f * K); c.polyAdd(bx + wave, by + 2.2f * K); c.polyFill(col)
        }
        for (i in 0 until 40) {
            val u = i / 40f; val u2 = (i + 1) / 40f
            val xa = rx0 + 0.2f + (rx1 - rx0 - 0.4f) * u; val xb = rx0 + 0.2f + (rx1 - rx0 - 0.4f) * u2
            c.line(iso.ix(xa, ryf + 0.02f), iso.iy(xa, ryf + 0.02f, eave - 0.4f - 5.6f * u * (1f - u)), iso.ix(xb, ryf + 0.02f), iso.iy(xb, ryf + 0.02f, eave - 0.4f - 5.6f * u2 * (1f - u2)), Col.hex(0xE8E0D0))
        }
    }

    // ------------------------------------------------------------------ the straw hive, the linden, the bench

    /** An old straw hive (koš) on a stump by the house's end: a dome of coiled straw, its mouth at the foot. */
    private fun skep() {
        stump(skepX, skepY, 3.2f)
        val bx = iso.sx(skepX, skepY); val by = iso.sy(skepX, skepY, 3.2f)
        val rw = 2.1f * K; val hh = 4.8f * K
        for (py in floor(by - hh).toInt()..floor(by).toInt()) {
            val v = (by - py) / hh // 0 at the foot, 1 at the top
            val half = rw * sqrt(max(0f, 1f - v * v * v)) * (1f - 0.1f * v)
            for (px in floor(bx - half).toInt()..floor(bx + half).toInt()) {
                val f = (px + 0.5f - bx) / max(0.5f, half)
                val coil = (v * 6.5f) % 1f
                val col = when {
                    winter && v > 0.72f -> Pal.SNOW_L
                    coil < 0.2f -> strawD
                    f < -0.45f -> strawL
                    f > 0.55f -> Col.scale(straw, 0.8f)
                    fine && Noise.rnd(px, py, 48) < 0.12f -> strawL
                    else -> straw
                }
                c.set(px, py, col)
            }
        }
        // its mouth
        c.fillRect(floor(bx - 0.6f * K).toInt(), floor(by - 0.9f * K).toInt(), floor(1.2f * K).toInt(), floor(0.8f * K).toInt(), Col.hex(0x2A1A0E))
    }

    /** A stump [h] px high at ([x], [y]): bark, and rings on its top. */
    private fun stump(x: Float, y: Float, h: Float) {
        val bx = iso.sx(x, y); val by = iso.sy(x, y, 0f)
        val r = 1.5f * K
        c.fillRect(floor(bx - r).toInt(), floor(by - h * K).toInt(), floor(2 * r).toInt() + 1, floor(h * K).toInt(), Pal.LOG_M)
        c.fillRect(floor(bx + r * 0.3f).toInt(), floor(by - h * K).toInt(), floor(r * 0.7f).toInt() + 1, floor(h * K).toInt(), Pal.LOG_D)
        c.fillEllipse(bx, by, r, r * 0.5f, Pal.LOG_D)
        c.fillEllipse(bx, by - h * K, r, r * 0.5f, if (winter) Pal.SNOW_L else Col.hex(0xD8B480))
        if (fine && !winter) c.fillEllipse(bx, by - h * K, r * 0.5f, r * 0.25f, Col.hex(0xB8905A))
    }

    /**
     * The linden: a stout trunk and a round crown in clumps, in fresh leaf in spring, flowering pale yellow in June and July,
     * gold in October, bare branches in winter.
     */
    private fun linden() {
        val bx = iso.sx(treeX, treeY); val by = iso.sy(treeX, treeY, 0f)
        shade(bx + 2f * K, by, 9f * K, 3f * K, 0.8f, groundId)
        // the trunk and its main boughs
        val tw = 1.2f * K
        c.fillRect(floor(bx - tw).toInt(), floor(by - 10f * K).toInt(), floor(2 * tw).toInt(), floor(10f * K).toInt(), Pal.LOG_M)
        c.fillRect(floor(bx + tw * 0.2f).toInt(), floor(by - 10f * K).toInt(), floor(tw * 0.8f).toInt(), floor(10f * K).toInt(), Pal.LOG_D)
        c.fillRect(floor(bx - tw).toInt(), floor(by - 10f * K).toInt(), detail, floor(10f * K).toInt(), Pal.LOG_L)
        val cy = by - 17f * K
        val m = frame.month
        if (m == 12 || m <= 2) {
            // bare: boughs forking into twigs, snow along them
            for (k in 0 until 6) {
                val ang = -PI.toFloat() / 2f + (k - 2.5f) * 0.36f + (Noise.rnd(k, 49) - 0.5f) * 0.15f
                val len = (5.5f + Noise.rnd(k, 50) * 2.5f) * K
                val sx0 = bx; val sy0 = by - 9.5f * K
                val ex = sx0 + cos(ang) * len; val ey = sy0 + sin(ang) * len
                for (o in 0 until detail) c.line(floor(sx0).toInt() + o, floor(sy0).toInt(), floor(ex).toInt() + o, floor(ey).toInt(), Pal.LOG_D)
                for (f in intArrayOf(-1, 1)) {
                    val a2 = ang + f * 0.45f
                    val l2 = len * 0.55f
                    val fx = ex + cos(a2) * l2; val fy = ey + sin(a2) * l2
                    c.line(floor(ex).toInt(), floor(ey).toInt(), floor(fx).toInt(), floor(fy).toInt(), Pal.LOG_M)
                    c.line(floor(fx).toInt(), floor(fy).toInt(), floor(fx + cos(a2 + f * 0.4f) * l2 * 0.5f).toInt(), floor(fy + sin(a2 + f * 0.4f) * l2 * 0.5f).toInt(), Pal.LOG_M)
                    if (winter) c.fillRect(floor(fx).toInt() - detail, floor(fy).toInt() - detail, 2 * detail, detail, Pal.SNOW_L)
                }
                if (winter) c.fillRect(floor(ex).toInt() - detail, floor(ey).toInt() - detail, 2 * detail, detail, Pal.SNOW_L)
            }
            return
        }
        val (dk, md, lt) = when {
            m in 3..4 -> Triple(Col.hex(0x5A9A3A), Col.hex(0x7AB84A), Col.hex(0xA8D868))
            m == 10 -> Triple(Col.hex(0xB07A1E), Col.hex(0xD8A02A), Col.hex(0xF2CC50))
            m == 11 -> Triple(Col.hex(0x8A6A2A), Col.hex(0xB08A34), Col.hex(0xD0A848))
            m == 9 -> Triple(Col.hex(0x5A7A2A), Col.hex(0x7E9A36), Col.hex(0xB8B84A))
            else -> Triple(Col.hex(0x2E6A2A), Col.hex(0x4A8A34), Col.hex(0x6EAE44))
        }
        val bloom = m == 6 || m == 7
        val rx = 9.5f * K; val ryy = 8f * K
        val sway = sin(t * 0.9).toFloat() * 0.4f * K * gust
        for (py in max(c.top, floor(cy - ryy).toInt())..min(c.bottom - 1, floor(cy + ryy).toInt())) for (px in max(c.left, floor(bx - rx + sway).toInt())..min(c.right - 1, floor(bx + rx + sway).toInt())) {
            val dx = (px + 0.5f - bx - sway) / rx; val dy = (py + 0.5f - cy) / ryy
            val clump = Noise.v2(px / (2.6f * K), py / (2.6f * K), 50)
            val d = dx * dx + dy * dy + (clump - 0.5f) * 0.5f
            if (d > 1f) continue
            // sparse in November
            if (m == 11 && Noise.rnd(px / (2 * detail), py / (2 * detail), 51) < 0.45f) continue
            val light = -dx * 0.6f - dy * 0.8f + (clump - 0.5f)
            var col = if (light > 0.35f) lt else if (light > -0.25f) md else dk
            if (bloom && Noise.rnd(px / detail, py / detail, 52) < 0.09f && light > -0.3f) col = Col.hex(0xF2E8A0)
            c.set(px, py, col)
        }
    }

    /** The bees' water: a shallow stone basin with pebbles to land on, a bee or two drinking; ice in winter. */
    private fun basin() {
        val bx = iso.sx(basinX, basinY); val by = iso.sy(basinX, basinY, 0f)
        c.fillEllipse(bx, by - 0.8f * K, 3.2f * K, 1.7f * K, Pal.STONE_D)
        c.fillEllipse(bx, by - 1.4f * K, 3.1f * K, 1.5f * K, Pal.STONE_L)
        c.fillEllipse(bx, by - 1.4f * K, 2.4f * K, 1.05f * K, if (winter) Pal.ICE_M else Pal.WATER_M)
        if (!winter) {
            c.fillEllipse(bx - 0.6f * K, by - 1.7f * K, 1.2f * K, 0.4f * K, Pal.WATER_L)
            for (k in 0 until 4) c.fillEllipse(bx + (k - 1.5f) * 1.1f * K, by - 1.2f * K + (k % 2) * 0.4f * K, 0.45f * K, 0.3f * K, Pal.STONE_M)
        }
    }

    /** A plank bench on two legs under the linden. */
    private fun bench() {
        val z = 2.6f
        for (x in floatArrayOf(benchX0 + 0.2f, benchX1 - 0.3f)) block(x, benchY - 0.15f, x + 0.12f, benchY + 0.3f, 0f, z, { _, _ -> woodD }, { _, _ -> woodX }, { _, _ -> woodM })
        block(benchX0, benchY - 0.2f, benchX1, benchY + 0.35f, z, z + 0.6f, { _, _ -> woodM }, { _, _ -> woodD }, { x, y -> if (winter) Pal.SNOW_L else board(x, y - benchY, 0.18f, 1.1f) })
    }

    // ------------------------------------------------------------------ the smoker, the table, the extractor

    /** The smoker on its stump: a tin can with a hinged spout, the bellows of leather at its side. */
    private fun smoker() {
        val bx = iso.sx(smokerX, smokerY); val by = iso.sy(smokerX, smokerY, 3f)
        val rw = 0.9f * K; val hh = 3.2f * K
        c.fillRect(floor(bx - rw).toInt(), floor(by - hh).toInt(), floor(2 * rw).toInt() + 1, floor(hh).toInt(), tin)
        c.fillRect(floor(bx - rw).toInt(), floor(by - hh).toInt(), detail, floor(hh).toInt(), tinL)
        c.fillRect(floor(bx + rw * 0.4f).toInt(), floor(by - hh).toInt(), floor(rw * 0.6f).toInt() + 1, floor(hh).toInt(), tinD)
        // the spout: a cone bent over
        c.polyBegin(); c.polyAdd(bx - rw, by - hh); c.polyAdd(bx + rw, by - hh); c.polyAdd(bx + 0.6f * K, by - hh - 1.4f * K); c.polyAdd(bx - 0.2f * K, by - hh - 1.6f * K); c.polyFill(tinD)
        c.fillRect(floor(bx - 0.4f * K).toInt(), floor(by - hh - 2f * K).toInt(), floor(0.8f * K).toInt() + 1, floor(0.6f * K).toInt(), tinD)
        // the bellows
        val lx = bx - rw - 1.2f * K
        c.polyBegin(); c.polyAdd(lx, by - 0.6f * K); c.polyAdd(bx - rw, by - 0.8f * K); c.polyAdd(bx - rw, by - hh + 0.6f * K); c.polyAdd(lx - 0.4f * K, by - hh + 0.2f * K); c.polyFill(Col.hex(0x8A4A2A))
        c.line(floor(lx - 0.4f * K).toInt(), floor(by - hh + 0.2f * K).toInt(), floor(lx).toInt(), floor(by - 0.6f * K).toInt(), woodL)
        if (fine) c.line(floor(lx - 0.2f * K).toInt(), floor(by - hh * 0.5f).toInt(), floor(bx - rw).toInt(), floor(by - hh * 0.5f).toInt(), Col.hex(0x6A3A1E))
    }

    /** The table: a top of boards on four legs. */
    private fun table() {
        val leg = 0.14f
        for ((x, y) in listOf(tx0 + 0.1f to ty1 - 0.25f, tx1 - 0.25f to ty1 - 0.25f, tx1 - 0.25f to ty0 + 0.1f)) block(x, y, x + leg, y + leg, 0f, tz - 0.5f, { _, _ -> woodD }, { _, _ -> woodX }, { _, _ -> woodM })
        block(tx0, ty0, tx1, ty1, tz - 0.5f, tz, { _, _ -> woodM }, { _, _ -> woodD }, { x, y -> if (winter && Noise.rnd((x * 20).toInt(), (y * 20).toInt(), 53) < 0.3f) Pal.SNOW_L else board(x, y - ty0, 0.25f, 1.1f) })
    }

    /** Jars of honey on the table: golden and glowing in the sun, white lids, one tall jar at the back. */
    private fun jars() {
        for ((i, x) in floatArrayOf(8.75f, 9.15f, 9.55f, 8.95f).withIndex()) {
            val y = if (i == 3) ty0 + 0.3f else ty0 + 0.65f
            val tall = if (i == 3) 3.2f else 2.4f
            val bx = iso.sx(x, y); val by = iso.sy(x, y, tz)
            val rw = 0.85f * K; val hh = tall * K
            c.fillRect(floor(bx - rw).toInt(), floor(by - hh).toInt(), floor(2 * rw).toInt() + 1, floor(hh).toInt(), honey)
            c.fillRect(floor(bx - rw).toInt(), floor(by - hh).toInt(), detail, floor(hh).toInt(), honeyL)
            c.fillRect(floor(bx + rw * 0.45f).toInt(), floor(by - hh).toInt(), floor(rw * 0.55f).toInt() + 1, floor(hh).toInt(), honeyD)
            if (fine) c.fillRect(floor(bx - rw * 0.4f).toInt(), floor(by - hh * 0.75f).toInt(), detail, floor(hh * 0.4f).toInt(), Col.hex(0xFFF0B0))
            // the lid, a gingham cloth over the tall one
            val lid = if (i == 3) Col.hex(0xD83A3A) else Col.hex(0xF4F0E6)
            c.fillRect(floor(bx - rw - (if (i == 3) 0.4f * K else 0f)).toInt(), floor(by - hh - 0.6f * K).toInt(), floor(2 * rw + (if (i == 3) 0.8f * K else 0f)).toInt() + 1, floor(0.7f * K).toInt(), lid)
            if (i == 3 && fine) for (k in 0 until 4) c.fillRect(floor(bx - rw + k * 0.6f * K).toInt(), floor(by - hh - 0.6f * K).toInt(), detail, detail, Col.hex(0xF4F0E6))
        }
    }

    /** A comb in its wooden frame, standing on the table: capped honey in its cells, a few open and shining. */
    private fun comb() {
        val x0 = 9.85f; val x1 = 10.45f; val y = ty0 + 0.45f; val z0 = tz; val z1 = tz + 3.4f
        onRight(y, x0, x1, z0, z1 + 0.4f) { x, z ->
            val u = (x - x0) / (x1 - x0); val v = (z - z0) / (z1 + 0.4f - z0)
            val e = min(u, 1f - u) * (x1 - x0) * 4.47f
            when {
                e < 0.35f || v > 0.9f -> if (v > 0.92f && (u < 0.12f || u > 0.88f)) woodL else woodM
                v < 0.06f -> woodD
                else -> {
                    val cellW = if (fine) 0.62f else 1.1f
                    val a = (x - x0) * 4.47f / cellW; val row = floor(z / (cellW * 0.86f)).toInt()
                    val fa = (a + (row and 1) * 0.5f) % 1f; val fz = (z / (cellW * 0.86f)) % 1f
                    val open = Noise.rnd(floor(a + (row and 1) * 0.5f).toInt(), row, 54) < 0.25f
                    when {
                        fine && (fa < 0.14f || fz < 0.14f) -> Col.hex(0xC89A3A)
                        open -> if (fine && fa in 0.3f..0.5f && fz in 0.4f..0.6f) honeyL else honey
                        else -> wax
                    }
                }
            }
        }
    }

    /** Beeswax candles tied in a bundle and a block of wax at the table's end. */
    private fun candles() {
        val bx = iso.sx(10.3f, ty1 - 0.2f); val by = iso.sy(10.3f, ty1 - 0.2f, tz)
        for (k in 0 until 3) {
            val cx = floor(bx - 1f * K + k * 0.8f * K).toInt()
            c.fillRect(cx, floor(by - 3.6f * K).toInt(), floor(0.7f * K).toInt(), floor(3.6f * K).toInt(), if (k == 1) waxD else wax)
            c.set(cx, floor(by - 3.6f * K).toInt() - 1, Col.hex(0x3A2A1A))
        }
        c.fillRect(floor(bx - 1.2f * K).toInt(), floor(by - 2f * K).toInt(), floor(2.4f * K).toInt(), detail, Col.hex(0xA83A2A))
        // the block
        val kx = iso.sx(10.3f, ty0 + 0.25f); val ky = iso.sy(10.3f, ty0 + 0.25f, tz)
        c.fillRect(floor(kx - 0.9f * K).toInt(), floor(ky - 1.3f * K).toInt(), floor(1.8f * K).toInt(), floor(1.3f * K).toInt(), wax)
        c.fillRect(floor(kx - 0.9f * K).toInt(), floor(ky - 1.3f * K).toInt(), floor(1.8f * K).toInt(), detail, Col.hex(0xFFF0A8))
        c.fillRect(floor(kx + 0.4f * K).toInt(), floor(ky - 1.3f * K).toInt(), floor(0.5f * K).toInt(), floor(1.3f * K).toInt(), waxD)
    }

    /** The honey extractor beside the table: a tin drum on three legs, the crank on top, a tap low on its front with a jar under it. */
    private fun extractor() {
        val bx = iso.sx(exX, exY); val by = iso.sy(exX, exY, 0f)
        val rw = 2.1f * K; val z0 = 2.4f * K; val z1 = 8.8f * K
        // the legs
        for (dx in floatArrayOf(-0.8f, 0.8f)) c.fillRect(floor(bx + dx * rw - detail / 2f).toInt(), floor(by - z0).toInt(), detail, floor(z0).toInt(), tinD)
        // the drum
        for (py in floor(by - z1).toInt()..floor(by - z0).toInt()) {
            for (px in floor(bx - rw).toInt()..floor(bx + rw).toInt()) {
                val f = (px + 0.5f - bx) / rw
                val col = when {
                    f < -0.6f -> tinL
                    f > 0.55f -> tinD
                    fine && abs(((by - py) / K) % 2.2f - 1.1f) < 0.12f -> tinD // the seams
                    else -> tin
                }
                c.set(px, py, col)
            }
        }
        c.fillEllipse(bx, by - z1, rw, rw * 0.45f, tinD)
        c.fillEllipse(bx, by - z1, rw * 0.8f, rw * 0.32f, Col.hex(0x3A3C42))
        c.fillEllipse(bx, by - z0, rw, rw * 0.45f, tin)
        // the crank: a bar across the top, the handle turning with the honey running
        val turn = (t * (0.5 + 6.0 * fxOn("honey"))).toFloat()
        val hx = bx + cos(turn) * 1.6f * K; val hy = by - z1 - 1.2f * K + sin(turn) * 0.6f * K
        c.fillRect(floor(bx - 0.3f * K).toInt(), floor(by - z1 - 1.6f * K).toInt(), floor(0.6f * K).toInt() + 1, floor(1.6f * K).toInt(), tinD)
        c.line(floor(bx).toInt(), floor(by - z1 - 1.2f * K).toInt(), floor(hx).toInt(), floor(hy).toInt(), Pal.STONE_X)
        c.fillRect(floor(hx).toInt() - detail / 2, floor(hy - 0.8f * K).toInt(), detail, floor(0.9f * K).toInt(), woodL)
        // the tap, and the jar under it
        c.fillRect(floor(bx - 0.3f * K).toInt(), floor(by - z0 - 1.6f * K).toInt(), floor(0.9f * K).toInt(), floor(0.7f * K).toInt(), tinD)
        val jx = bx; val jy = by - 0.2f * K
        val fill = 0.4f + 0.45f * fxOn("honey")
        c.fillRect(floor(jx - 0.8f * K).toInt(), floor(jy - 2f * K).toInt(), floor(1.6f * K).toInt() + 1, floor(2f * K).toInt(), glassC)
        c.fillRect(floor(jx - 0.8f * K).toInt(), floor(jy - 2f * K * fill).toInt(), floor(1.6f * K).toInt() + 1, floor(2f * K * fill).toInt(), honey)
        c.fillRect(floor(jx - 0.8f * K).toInt(), floor(jy - 2f * K).toInt(), detail, floor(2f * K).toInt(), Col.hex(0xFFFFFF))
    }

    // ------------------------------------------------------------------ the bees

    /** The big bee at the flowers (the word): striped, its wings a blur; tapped, it buzzes round. */
    private fun bigBee() {
        val pk = poked("bee")
        var ox = 0f; var oy = 0f
        if (pk != null) {
            val a = pk.age(t)
            if (pk.step == 0 && a < 2.4f) {
                // a figure of eight over the flowers
                val u = a / 2.4f * 2f * PI.toFloat()
                ox = sin(u) * 9f * K; oy = sin(2f * u) * 3.5f * K - sin(u * 0.5f) * 3f * K
            } else if (pk.step == 1 && a < 3.4f) {
                // a wide round of the garden, up by the linden and back past the table
                val u = a / 3.4f * 2f * PI.toFloat()
                ox = -sin(u) * 24f * K; oy = (cos(u) - 1f) * 9f * K
            }
        }
        // in winter and at night it rests at the straw hive's mouth, else it hovers over the flowers
        val home = resting()
        val hover = if (home && pk == null) 0f else sin(t * 3.1).toFloat() * 0.8f * K
        val hx = if (home) skepX else beeX; val hy = if (home) skepY + 0.45f else beeY; val hz = if (home) 3.5f else beeZ
        val bx = iso.sx(hx, hy) + ox; val by = iso.sy(hx, hy, hz) + oy + hover
        val face = if (pk != null && ox != 0f) (if (cos(pk.age(t) * 2.6f) > 0f) 1 else -1) else if (home) -1 else 1
        drawBee(floor(bx).toInt(), floor(by).toInt(), face, still = home && pk == null)
        if (pk != null && (ox != 0f || oy != 0f)) s.fx {
            // a dotted trail, fading
            for (k in 1..6) {
                val a2 = max(0f, pk.age(t) - k * 0.07f)
                val dx: Float; val dy: Float
                if (pk.step == 0) { val u = a2 / 2.4f * 2f * PI.toFloat(); dx = sin(u) * 9f * K; dy = sin(2f * u) * 3.5f * K - sin(u * 0.5f) * 3f * K }
                else { val u = a2 / 3.4f * 2f * PI.toFloat(); dx = -sin(u) * 24f * K; dy = (cos(u) - 1f) * 9f * K }
                val tx = iso.sx(hx, hy) + dx; val ty = iso.sy(hx, hy, hz) + dy
                for (yy in 0 until detail) for (xx in 0 until detail) c.blend(floor(tx).toInt() + xx, floor(ty).toInt() + yy, Col.hex(0xFFF4C0), 0.7f - k * 0.1f)
            }
        }
    }

    /** Whether the big bee rests at home: in winter and at night (the weather leaves it where it is, the words' places stay). */
    private fun resting(): Boolean = winter || env.dark > 0.55f

    /** A bee ~3 scene px long, its middle at ([x], [y]): yellow and black stripes, a dark head, wings flickering ([still]: folded). */
    private fun drawBee(x: Int, y: Int, face: Int, still: Boolean = false) {
        val d = detail
        fun p(dx: Int, dy: Int, col: Int) = c.fillRect(x + dx * d, y + dy * d, d, d, col)
        val up = !still && ((t * 20).toInt() and 1) == 0
        val wing = Col.hex(0xE8F4FF)
        p(-1, 0, beeY0); p(0, 0, beeC); p(1, 0, beeY0); p(2 * face, 0, beeC); p(-2 * face, 0, beeC)
        p(-1, 1, beeY0); p(0, 1, beeY0); p(1, 1, beeC)
        if (up) { p(-1, -1, wing); p(0, -2, wing); p(1, -1, wing) } else { p(-1, -1, wing); p(1, -1, wing); p(0, -1, Col.mix(wing, beeY0, 0.3f)) }
    }

    /** The bees out and about: from the hives' mouths to the flowers and back in loops, a few crawling on the ledges. */
    private fun bees() {
        val n = beesOut()
        if (n == 0) return
        val d = detail
        s.fx {
            for (j in 0 until n) {
                val r = j % 3; val h = (j / 3) % hives
                val ex = hiveX0 + h * hiveW + hiveW * 0.5f; val ez = rowZ[r] + 0.2f
                val speed = 0.12 + Noise.rnd(j, 55) * 0.1
                val ph = ((t * speed + Noise.rnd(j, 56)) % 1.0).toFloat()
                // out to a flower and back, a loop at the far end
                // most to the flowers, some to the water
                val water = j % 4 == 3
                val tx = if (water) basinX + (Noise.rnd(j, 57) - 0.5f) * 0.8f else flX + (Noise.rnd(j, 57) - 0.5f) * flRx * 1.6f
                val ty = if (water) basinY + (Noise.rnd(j, 58) - 0.5f) * 0.4f else flY + (Noise.rnd(j, 58) - 0.5f) * flRy * 1.6f
                val s0 = sin(ph * PI.toFloat())
                val wob = sin(ph * 17f + j).toFloat() * 0.25f
                val x = ex + (tx - ex) * s0 + wob; val y = hf + 0.3f + (ty - hf - 0.3f) * s0 + cos(ph * 13f + j).toFloat() * 0.2f
                val z = ez + (2.5f - ez) * s0 + sin(ph * PI.toFloat() * 2f) * 3f
                val bx = iso.ix(x, y); val by = iso.iy(x, y, z)
                c.penEmissive = false
                for (yy in 0 until d) for (xx in 0 until d) c.blend(bx + xx, by + yy, beeC, 0.95f)
                if (((t * 18).toInt() + j) and 1 == 0) for (xx in 0 until d) c.blend(bx + xx, by - d, Col.hex(0xE0ECF8), 0.7f)
                if (fine) c.blend(bx + d - 1, by + d - 1, beeY0, 0.9f)
            }
            // a few on the landing boards, walking
            for (j in 0 until min(12, n / 2)) {
                val r = j % 3; val h = j % hives
                val x = hiveX0 + h * hiveW + 0.25f + ((t * 0.3 + Noise.rnd(j, 59)) % 1.0).toFloat() * 0.5f
                val bx = iso.ix(x, hf + 0.15f); val by = iso.iy(x, hf + 0.15f, rowZ[r] - 0.05f)
                for (yy in 0 until d) for (xx in 0 until d) c.blend(bx + xx, by - d + yy, beeC, 0.9f)
            }
        }
    }

    /** A swarm ([level] 0..1): a cloud of bees rising off the hives and circling up by the linden, more and further the higher. */
    private fun swarm(level: Float) {
        if (level <= 0.02f) return
        val ax = iso.sx(5.2f, hf + 0.4f); val ay = iso.sy(5.2f, hf + 0.4f, 9f)
        val bx = iso.sx(treeX + 1.2f, treeY - 1.5f); val by = iso.sy(treeX + 1.2f, treeY - 1.5f, 20f)
        val cx = ax + (bx - ax) * level + sin(t * 0.7).toFloat() * 2f * K
        val cy = ay + (by - ay) * level + cos(t * 0.9).toFloat() * 1.5f * K
        val n = (24 + 70 * level).toInt()
        val rad = (5f + 7f * level) * K
        val d = detail
        s.fx {
            for (j in 0 until n) {
                val ang = (t * (1.2 + Noise.rnd(j, 60) * 1.8) * (if (j % 2 == 0) 1 else -1) + j * 2.4).toFloat()
                val r = rad * sqrt(Noise.rnd(j, 61)) * (0.8f + 0.2f * sin(t * 2 + j).toFloat())
                val x = cx + cos(ang) * r; val y = cy + sin(ang) * r * 0.6f
                for (yy in 0 until d) for (xx in 0 until d) c.blend(floor(x).toInt() + xx, floor(y).toInt() + yy, beeC, 0.9f)
                if (fine && j % 3 == 0) c.blend(floor(x).toInt(), floor(y).toInt() - 1, Col.hex(0xE0ECF8), 0.6f)
            }
            PokeArt.rings(c, cx, cy, ((t * 0.9) % 1.0).toFloat(), rad * 1.5f, Col.hex(0xFFF0C0), max(1, d / 2), 0.35f * level, n = 1)
        }
    }

    /** The smoker's smoke: a thin thread by itself, puffs going over to the hives when it's worked (a dialog's "smoke"). */
    private fun smokeOut() {
        val bx = iso.sx(smokerX, smokerY); val by = iso.sy(smokerX, smokerY, 3f) - 5.4f * K
        val lv = fxOn("smoke")
        if (winter && lv <= 0f) return
        wisps(bx, by, 0.2f + 0.8f * lv, 62)
        if (lv > 0.02f) s.fx {
            for (k in 0 until 4) {
                val u = ((t * 0.55 + k / 4.0) % 1.0).toFloat()
                PokeArt.puff(c, bx - u * 16f * K, by - u * 10f * K, u, (3f + 5f * u) * K, 4, Col.hex(0xE6E8EC), 63 + k, 0.55f * lv)
            }
        }
    }

    /** Honey running ([level]) from the extractor's tap into the jar: a golden thread, curling where it lands. */
    private fun honeyRun(level: Float) {
        if (level <= 0.02f) return
        val bx = iso.sx(exX, exY); val by = iso.sy(exX, exY, 0f)
        val top = by - 2.4f * K - 0.9f * K; val bot = by - 0.2f * K - 2f * K * (0.4f + 0.45f * level)
        val w = max(1, (detail * (0.6f + level)).toInt())
        for (py in floor(top).toInt()..floor(bot).toInt()) {
            val wob = floor(sin(py * 0.7f / detail + t * 5).toFloat() * 0.4f * level).toInt()
            c.fillRect(floor(bx).toInt() + wob, py, w, 1, if ((py / detail) % 3 == 0) honeyL else honey)
        }
        c.fillEllipse(bx + w / 2f, bot, 0.7f * K, 0.3f * K, honeyL)
    }

    // ------------------------------------------------------------------ the painted fronts, closer

    /**
     * Tapped, a hive front comes up close over the house: a board in its frame with its painting big enough to see
     * (the devil grinding an old woman's tongue, the hunter and the bear, the rooster), growing in and shrinking away.
     */
    private fun panelLook() {
        val pk = poked("panel") ?: return
        val a = pk.age(t)
        val grow = PokeArt.ease(a, 0f, 0.3f) * (1f - PokeArt.ease(a, 2.8f, 3.2f))
        if (grow <= 0.01f) return
        val motif = LOOKS[pk.step.coerceIn(0, LOOKS.lastIndex)]
        val pw = 30f * K * grow; val ph = 20f * K * grow
        val cx = iso.sx(5.8f, ry).coerceIn(pw / 2f + K, w - pw / 2f - K)
        val cy = (iso.sy(5.8f, ry, ridge) - 8f * K).coerceIn(ph / 2f + K, h - ph / 2f - K)
        s.fx {
            val x0 = floor(cx - pw / 2f).toInt(); val y0 = floor(cy - ph / 2f).toInt()
            val x1 = floor(cx + pw / 2f).toInt(); val y1 = floor(cy + ph / 2f).toInt()
            val fr = max(detail, (1.2f * K * grow).toInt())
            for (py in max(c.top, y0 - detail)..min(c.bottom - 1, y1 + 2 * detail)) for (px in max(c.left, x0 - detail)..min(c.right - 1, x1 + 2 * detail)) {
                val shadow = px > x1 || py > y1
                if (shadow) { if (px >= x0 + 2 * detail && py >= y0 + 2 * detail) c.blend(px, py, Col.hex(0x000000), 0.35f); continue }
                if (px < x0 || py < y0) { c.blend(px, py, woodX, 1f); continue }
                val u = (px + 0.5f - x0) / (x1 - x0); val v = (py + 0.5f - y0) / (y1 - y0)
                val edge = min(min(px - x0, x1 - px), min(py - y0, y1 - py))
                val col = if (edge < fr) (if (px - x0 < fr || py - y0 < fr) woodL else woodD) else motif((px - x0 - fr + 0.5f) / (x1 - x0 - 2 * fr), (py - y0 - fr + 0.5f) / (y1 - y0 - 2 * fr))
                c.blend(px, py, col, 1f)
            }
        }
    }

    companion object {
        private fun h(v: Long) = Col.hex(v)
        private fun d(x: Float, y: Float) = sqrt(x * x + y * y)

        /** An ellipse test: ([u], [v]) within ([cu], [cv]) ± ([ru], [rv]). */
        private fun ell(u: Float, v: Float, cu: Float, cv: Float, ru: Float, rv: Float): Boolean = d((u - cu) / ru, (v - cv) / rv) < 1f

        /** A line from ([au], [av]) to ([bu], [bv]), [w] thick. */
        private fun seg(u: Float, v: Float, au: Float, av: Float, bu: Float, bv: Float, w: Float): Boolean {
            val du = bu - au; val dv = bv - av
            val k = (((u - au) * du + (v - av) * dv) / (du * du + dv * dv)).coerceIn(0f, 1f)
            return d(u - au - du * k, v - av - dv * k) < w
        }

        /** A rooster on ochre: red comb and wattles, an orange body, a dark sickle tail, yellow legs on green. */
        private val rooster: (Float, Float) -> Int = { u, v ->
            when {
                v > 0.84f -> h(0x5A8A3A)
                ell(u, v, 0.64f, 0.22f, 0.07f, 0.06f) -> h(0xE02A20) // comb
                ell(u, v, 0.64f, 0.34f, 0.07f, 0.1f) -> if (ell(u, v, 0.66f, 0.3f, 0.018f, 0.025f)) h(0x1A1A1A) else h(0xC8402A)
                seg(u, v, 0.7f, 0.33f, 0.77f, 0.36f, 0.025f) -> h(0xF2C030) // beak
                ell(u, v, 0.66f, 0.43f, 0.03f, 0.05f) -> h(0xE02A20) // wattles
                ell(u, v, 0.32f, 0.36f, 0.13f, 0.24f) && !ell(u, v, 0.4f, 0.46f, 0.1f, 0.2f) -> if (u < 0.3f) h(0x1E4A30) else h(0x2A2A3A) // tail
                ell(u, v, 0.5f, 0.55f, 0.19f, 0.17f) -> if (ell(u, v, 0.46f, 0.52f, 0.1f, 0.08f)) h(0xE8902A) else h(0xB8482A)
                seg(u, v, 0.47f, 0.7f, 0.45f, 0.84f, 0.02f) || seg(u, v, 0.55f, 0.7f, 0.57f, 0.84f, 0.02f) -> h(0xF2C030)
                else -> h(0xE8C04A)
            }
        }

        /** The hunter and the bear: a hunter in a red coat and green hat aims at a brown bear on a green hill, a spruce behind. */
        private val hunter: (Float, Float) -> Int = { u, v ->
            val hill = v > 0.74f - 0.06f * sin(u * 3f)
            when {
                // the bear, up on its hind legs
                ell(u, v, 0.76f, 0.3f, 0.07f, 0.08f) || ell(u, v, 0.72f, 0.22f, 0.025f, 0.03f) || ell(u, v, 0.8f, 0.22f, 0.025f, 0.03f) -> if (ell(u, v, 0.74f, 0.29f, 0.012f, 0.02f)) h(0x1A1A1A) else h(0x6A4424)
                ell(u, v, 0.74f, 0.54f, 0.12f, 0.2f) -> if (u < 0.7f) h(0x7E5430) else h(0x5A3A1E)
                seg(u, v, 0.66f, 0.42f, 0.6f, 0.34f, 0.03f) -> h(0x5A3A1E)
                // the hunter
                ell(u, v, 0.3f, 0.3f, 0.045f, 0.07f) -> h(0xF0C8A0)
                ell(u, v, 0.3f, 0.23f, 0.07f, 0.035f) -> h(0x2E6A3A)
                u in 0.25f..0.36f && v in 0.36f..0.62f -> h(0xC03030)
                (u in 0.25f..0.29f || u in 0.32f..0.36f) && v in 0.62f..0.8f -> h(0x3A3040)
                seg(u, v, 0.34f, 0.42f, 0.56f, 0.36f, 0.018f) -> h(0x2A2020)
                // the spruce
                v in 0.2f..0.76f && abs(u - 0.1f) < (v - 0.2f) * 0.18f -> h(0x1E4A2A)
                hill -> h(0x6A9A48)
                else -> if (v < 0.35f) h(0x8EC0E0) else h(0xB8DAEC)
            }
        }

        /** The devil grinding an old woman's tongue: a grey grindstone on its trestle, the black devil at the crank, the old woman in a white kerchief with her long red tongue on the stone. */
        private val devil: (Float, Float) -> Int = { u, v ->
            val stone = d((u - 0.52f) / 0.14f, (v - 0.5f) / 0.21f)
            when {
                v > 0.86f -> h(0x7A5A3A)
                stone < 0.22f -> h(0x3A3634)
                stone < 1f -> if (u < 0.5f) h(0xB0ACA4) else h(0x8A8680)
                seg(u, v, 0.4f, 0.84f, 0.5f, 0.66f, 0.02f) || seg(u, v, 0.64f, 0.84f, 0.54f, 0.66f, 0.02f) -> h(0x5A3A22)
                // the devil
                ell(u, v, 0.8f, 0.28f, 0.05f, 0.07f) -> if (ell(u, v, 0.78f, 0.27f, 0.012f, 0.02f)) h(0xE02A20) else h(0x241C1C)
                seg(u, v, 0.77f, 0.21f, 0.75f, 0.13f, 0.012f) || seg(u, v, 0.83f, 0.21f, 0.86f, 0.13f, 0.012f) -> h(0x241C1C)
                ell(u, v, 0.81f, 0.54f, 0.07f, 0.2f) -> h(0x2E2424)
                seg(u, v, 0.76f, 0.44f, 0.6f, 0.46f, 0.018f) -> h(0x2E2424)
                seg(u, v, 0.86f, 0.7f, 0.95f, 0.5f, 0.012f) -> h(0x2E2424) // the tail
                // the old woman
                ell(u, v, 0.18f, 0.3f, 0.06f, 0.075f) -> if (v < 0.27f || u < 0.14f) h(0xF4F0E6) else h(0xF0C8A0)
                seg(u, v, 0.22f, 0.34f, 0.4f, 0.46f, 0.02f) -> h(0xD02A2A) // the tongue
                ell(u, v, 0.18f, 0.62f, 0.1f, 0.24f) -> if (v < 0.52f) h(0x8A4A2A) else h(0x3A58A0)
                else -> h(0xEAD8A6)
            }
        }

        /** A red heart among flowers on blue. */
        private val heart: (Float, Float) -> Int = { u, v ->
            val hu = (u - 0.5f) / 0.26f; val hv = (v - 0.48f) / 0.3f
            val inHeart = (hu * hu + (hv - sqrt(abs(hu)) * 0.9f + 0.2f).let { it * it } - 1f) < 0f
            when {
                inHeart -> if (hu < -0.3f && hv < 0f) h(0xE85A5A) else h(0xC8282E)
                ell(u, v, 0.14f, 0.3f, 0.06f, 0.08f) || ell(u, v, 0.86f, 0.3f, 0.06f, 0.08f) -> h(0xF2D24A)
                ell(u, v, 0.14f, 0.72f, 0.06f, 0.08f) || ell(u, v, 0.86f, 0.72f, 0.06f, 0.08f) -> h(0xF4F0E6)
                else -> h(0x2E58A0)
            }
        }

        /** The Virgin in a blue mantle with a golden halo, on red. */
        private val virgin: (Float, Float) -> Int = { u, v ->
            when {
                ell(u, v, 0.5f, 0.3f, 0.07f, 0.1f) -> h(0xF0C8A0)
                ell(u, v, 0.5f, 0.3f, 0.13f, 0.19f) && v < 0.4f -> h(0xF2C84A)
                v > 0.34f && abs(u - 0.5f) < 0.08f + (v - 0.34f) * 0.45f && v < 0.92f -> if (abs(u - 0.5f) < 0.04f + (v - 0.34f) * 0.12f) h(0xF4F0E6) else h(0x2E58B0)
                else -> h(0xB83A30)
            }
        }

        /** A vase of flowers on green. */
        private val vase: (Float, Float) -> Int = { u, v ->
            when {
                v > 0.62f && abs(u - 0.5f) < 0.1f + (v - 0.62f) * 0.2f && v < 0.9f -> h(0x3A58A0)
                ell(u, v, 0.34f, 0.3f, 0.08f, 0.1f) -> h(0xE83A3A)
                ell(u, v, 0.5f, 0.2f, 0.08f, 0.1f) -> h(0xF2D24A)
                ell(u, v, 0.66f, 0.3f, 0.08f, 0.1f) -> h(0xF4F0E6)
                seg(u, v, 0.5f, 0.62f, 0.34f, 0.34f, 0.015f) || seg(u, v, 0.5f, 0.62f, 0.5f, 0.26f, 0.015f) || seg(u, v, 0.5f, 0.62f, 0.66f, 0.34f, 0.015f) -> h(0x2E6A2E)
                else -> h(0x6A9A5A)
            }
        }

        /** The pictures on the hive fronts. */
        private val MOTIFS: List<(Float, Float) -> Int> = listOf(rooster, hunter, devil, heart, virgin, vase)

        /** What a tap on the fronts brings up close, one after another. */
        private val LOOKS: List<(Float, Float) -> Int> = listOf(devil, hunter, rooster)
    }
}
