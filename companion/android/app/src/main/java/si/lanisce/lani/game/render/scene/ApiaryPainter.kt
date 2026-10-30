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
 *
 * It grows with the bee garden's level (BEEHIVE, read once a frame: companion/SCENES.md "What the village has built"):
 * at 1 a low bee house with one row of hives under a roof of boards, on a wooden sill, two of its fronts painted, the
 * smoker and the skep on the grass, a small bed of flowers and two jars; at 2 a second row, the house taller on its stone
 * footing, six fronts painted; at 3 the third row, the house at its full height, twelve painted; at 4 the roof of
 * shingles, every front painted, the smoker and the skep on their stumps, the flower bed full and a third jar; at 5 the
 * trodden path and the bare earth before the hives, the bees' water, the lantern on its corner post and the jar under
 * its gingham cloth. Every thing to tap is there at every level.
 *
 * In Friuli ([look]: companion/SCENES.md "Culture looks"; Apicoltore Aldo's on the Collio) the painted Slovene fronts
 * are not drawn: the apiary is a shed of river cobbles with bands of brick under a roof of coppi, and in its open front
 * Dadant box hives stand in tiers, each painted a plain colour (white, yellow, light blue, green) with its number, its
 * entrance and its landing board, a tin lid on top; the hive on its own by the shed's end is a box hive too (light blue,
 * an empty one for a swarm); the tree is an acacia (robinia), its white racemes hanging in May, yellow in autumn, its pods
 * in winter; the jars hold pale acacia honey, and in autumn the tall one dark chestnut honey; the `panel` is Aldo's sign
 * by the path, MIELE on a yellow board (tapped, it comes up close: MIELE, ACACIA, CASTAGNO); World Bee Day's bunting is
 * yellow and blue. Its levels: at 1 a lean-to of boards with three hives on one tier, at 2 the cobble walls and the coppi
 * roof over six, at 3 the shed at its full height with a second tier begun, at 4 both tiers full (with the stumps, the
 * bed and the jar as at home), at 5 the path, the water and the lantern.
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
    // Friuli: Aldo's sign by the path's foot
    private val signX = 8.25f; private val signY = 10.2f

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

    // ------------------------------------------------------------------ the level and the look, read once a frame

    /** The bee garden's level (1 … 5: BEEHIVE's, the one the scene is seen in). */
    private var lv = 5

    /** The rows of hives in the bee house (in Friuli: the tiers of box hives), its eave and its ridge. */
    private var rows = 3
    private var eaveZ = eave
    private var ridgeZ = ridge

    /** How many of the hive fronts are painted (the Slovene house: the rest are plain boards). */
    private var painted = 18

    /** The roof of boards (before the shingles, or Friuli's coppi); the stone footing (before it, a wooden sill). */
    private var boards = false
    private var footing = true

    /** The smoker and the hive on their stumps; the trodden path, the bare earth and the bees' water; the lantern. */
    private var stumps = true
    private var garden = true
    private var lanternUp = true

    /** How big the flower bed is (1: as at the top level), and how many jars stand on the table. */
    private var bed = 1f
    private var jarsN = 4

    /** Friuli's look ([look]): the shed with its box hives, Aldo's sign, the box hive apart, the acacia, its honeys. */
    private var friuli = false
    private var sign = false
    private var boxHive = false
    private var acacia = false
    private var paleHoney = false
    private var bunting2 = false

    /** Friuli's tiers of box hives: how many on each tier at this level. */
    private val tierHives = IntArray(2)

    private fun readLevel() {
        lv = frame.world.level(si.lanisce.lani.game.BuildingType.BEEHIVE).coerceIn(1, 5)
        friuli = variant("house") == "friuli"
        sign = variant("panel") == "sign"
        boxHive = variant("hive") == "box"
        acacia = variant("tree") == "acacia"
        paleHoney = variant("honey") == "acacia"
        bunting2 = variant("bunting") == "friuli"
        if (friuli) {
            rows = if (lv >= 3) 2 else 1
            tierHives[0] = if (lv == 1) 3 else F_HIVES
            tierHives[1] = when { lv >= 4 -> F_HIVES; lv == 3 -> 3; else -> 0 }
            eaveZ = if (rows == 2) eave else F_EAVE1
            boards = lv == 1
            footing = lv >= 2
        } else {
            rows = when (lv) { 1 -> 1; 2 -> 2; else -> 3 }
            eaveZ = eave - (3 - rows) * ROW_STEP
            painted = PAINTED[lv - 1]
            boards = lv <= 3
            footing = lv >= 2
        }
        ridgeZ = ridge - (eave - eaveZ)
        stumps = lv >= 4
        garden = lv >= 5
        lanternUp = lv >= 5
        bed = BED[lv - 1]
        jarsN = JARS[lv - 1]
    }

    /** The flower bed's half-widths at this level. */
    private val bedRx: Float get() = flRx * bed
    private val bedRy: Float get() = flRy * bed

    /** How high the smoker and the hive apart stand: on their stumps, or on the grass. */
    private val smokerZ: Float get() = if (stumps) 3f else 0f
    private val skepZ: Float get() = if (stumps) 3.2f else 0f

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
        readLevel()
        fit()
        vignette()
        plot { x, y, px, py -> ground(x, y, px, py) }
        tufts(90, 29) { x, y -> busy(x, y) }
        meadow()
        // the house's shade on the grass in front of its right end
        shade(iso.sx(hx1 + 0.4f, 1.9f), iso.sy(hx1 + 0.4f, 1.9f, 0f), 3.5f * 4 * K, 0.9f * 4 * K, 0.86f, groundId)

        thing("beehouse") { house() }
        if (friuli) {
            // Aldo's shed: the box hives on their tiers are the apiary's; his sign by the path is the panel (below)
            thing("beehouse") { boxes() }
        } else {
            // the fronts not painted yet are plain boards of the bee house
            if (painted < rows * hives) thing("beehouse") { plainFronts() }
            // the painted fronts are a word of their own; the bee house's sticker (drawn alone) shows them too
            if (solo == "beehouse") thing("beehouse") { panels() } else thing("panel") { panels() }
        }
        thing("beehouse") { houseFront() }
        if (lanternUp) prop(outline = 0) { lantern() }
        if (beeDay) prop(outline = 0) { bunting() }
        thing("hive", slop = 1) { if (boxHive) boxHive() else skep() }
        thing("tree") { if (acacia) acacia() else linden() }
        for (p in peopleAt("hives")) { footShadow(hivesPX, hivesPY); personAt(p, hivesPX, hivesPY) }
        if (stumps) prop { stump(smokerX, smokerY, 3f) }
        thing("smoker", slop = 1) { smoker() }
        thing("bench", slop = 1) { bench() }
        for (p in peopleAt("bench")) personAt(p, benchPX, benchPY, 0f, seated = true)
        thing("extractor", slop = 1) { extractor() }
        for (p in peopleAt("table")) { footShadow(tablePX, tablePY); personAt(p, tablePX, tablePY) }
        prop { table() }
        thing("honey", slop = 1) { jars() }
        thing("comb", slop = 1) { comb() }
        thing("wax", slop = 1) { candles() }
        if (garden) prop { basin() }
        thing("flowers") { flowerHeads() }
        if (sign) thing("panel", slop = 1) { aldoSign() }
        thing("bee", slop = 3) { bigBee() }

        bees()
        swarm(fxOn("swarm"))
        smokeOut()
        honeyRun(fxOn("honey"))
        panelLook()
        fireflies(iso.ix(0f, 11f), iso.iy(6f, 11f, 14f), iso.ix(12f, 6f), iso.iy(12f, 11f, 0f), 9, 13)
        if (lanternUp && env.windows > 0.35f) s.light(iso.sx(hx1 + 0.1f, hy1 + 0.35f), iso.sy(hx1 + 0.1f, hy1 + 0.35f, 12f), 30f, 0.8f * env.windows)
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

    /** Grass; bare earth in front of the hives, where the beekeeper works; a trodden path from the front (the top level's). */
    private fun ground(x: Float, y: Float, px: Int, py: Int): Int {
        if (x > hx0 + 0.15f && x < hx1 - 0.15f && y > hy0 + 0.15f && y < hy1 - 0.15f) return Pal.STONE_D
        val g = grassAt(x, y, px, py)
        if (!garden) return g
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
            (x in tx0 - 0.2f..exX + 0.8f && y in ty0 - 0.3f..ty1 + 0.5f) || (garden && hyp(x - basinX, y - basinY) < 0.9f) || flower(x, y) < 1.1f ||
            (x in benchX0 - 0.2f..benchX1 + 0.2f && abs(y - benchY) < 0.5f) || (sign && hyp(x - signX, y - signY) < 0.6f)

    /** How far ([x], [y]) is into the flower bed (as big as the level has it): under 1 inside. */
    private fun flower(x: Float, y: Float): Float = hyp((x - flX) / bedRx, (y - flY) / bedRy)

    /** The meadow's flowers in the bed as the season has them (dandelions and daisies in spring, phacelia and clover in summer, asters in autumn); snow in winter. */
    private fun meadow() {
        if (winter) {
            prop(outline = 0) { c.fillEllipse(iso.sx(flX, flY), iso.sy(flX, flY, 0f), bedRx * 4.2f * K, bedRy * 2.4f * K, Pal.SNOW_M) }
            return
        }
        prop(outline = 0) {
            // the leaves: a darker, taller green
            val cx = iso.sx(flX, flY); val cy = iso.sy(flX, flY, 0f)
            val rx = (bedRx + bedRy) * 3.2f * K; val ryy = (bedRx + bedRy) * 1.1f * K
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
            for (j in 0 until (10 * bed).toInt()) {
                val x = flX + (Noise.rnd(j, 34) - 0.5f) * bedRx * 1.6f; val y = flY + (Noise.rnd(j, 35) - 0.5f) * bedRy * 1.6f
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
        val n = ((if (m in 4..9) 34 else 16) * bed).toInt()
        for (j in 0 until n) {
            val ang = Noise.rnd(j, 36) * 2f * PI.toFloat(); val r = sqrt(Noise.rnd(j, 37)) * 0.95f
            val x = flX + cos(ang) * r * bedRx; val y = flY + sin(ang) * r * bedRy
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

    /**
     * The bee house: its stone footing (at first a wooden sill), the end of planks under the gable (in Friuli river cobbles
     * with bands of brick and brick quoins), the roof of shingles (at first of boards; in Friuli of coppi), as high as its
     * rows of hives.
     */
    private fun house() {
        val e = eaveZ; val r = ridgeZ
        // the footing
        if (footing) block(hx0, hy0, hx1, hy1, 0f, ft, { _, z -> if (z > ft - 0.3f) Pal.STONE_L else Pal.STONE_M }, { _, _ -> Pal.STONE_D }, { _, _ -> Pal.STONE_M })
        else block(hx0, hy0, hx1, hy1, 0f, ft, { x, z -> if (z > ft - 0.3f) woodL else board(z, x * 0.2f, 1f, 0.85f) }, { _, _ -> woodX }, { _, _ -> woodM })
        // the right end up to the eave, the gable over it: boards, or Friuli's cobbles and brick
        if (friuli && !boards) quadPlot(hx1, hy0, ft, hx1, hy1, ft, hx1, hy1, r, hx1, hy0, r) { px, py ->
            val y = wallXy(px, hx1); val z = wallXz(px, py, hx1)
            val top = e + (r - e) * (1f - abs(y - ry) / (ry - ryb))
            when {
                z > top -> 0
                z > e - 0.4f && z < e + 0.2f -> COP_D
                y < hy0 + 0.3f || y > hy1 - 0.3f -> quoin(y, z, px, py, 0.8f)
                else -> cobbleAt(y, z, px, py, 0.8f)
            }
        }
        else onLeft(hx1, hy0, hy1, ft, r) { y, z ->
            val top = e + (r - e) * (1f - abs(y - ry) / (ry - ryb))
            if (z > top) 0 else if (z > e - 0.4f && z < e + 0.2f) woodX else board(z, y, 0.34f, 0.8f)
        }
        // the roof from the eave up to the ridge: shingles in staggered rows, boards, or coppi; snow on it in winter
        quadFill(rx0, ryf, e, rx1, ryf, e, rx1, ry, r, rx0, ry, r) { px, py ->
            // the slope's point under the pixel: s from the eave (0) up to the ridge (1), x along it
            val sl = ((2f * ua(px) - vb(py) + 4f * ryf - e) / (4f * (ryf - ry) + (r - e))).coerceIn(0f, 1f)
            val x = ua(px) + ryf - sl * (ryf - ry)
            when {
                boards -> roofBoard(x, sl, px, py)
                friuli -> coppo(x, sl, px, py)
                else -> shingle(x, sl, px, py)
            }
        }
        // the gable's bargeboards along the roof's right edge
        val barge = if (friuli && !boards) COP_D else woodD
        rodIso(rx1, ryf, e - 0.3f, rx1, ry, r - 0.3f, barge)
        rodIso(rx1, ry, r - 0.3f, rx1, ryb, e - 0.3f, barge)
        rodIso(rx0, ry, r + 0.2f, rx1, ry, r + 0.2f, if (friuli && !boards) COP_D else woodX)
    }

    /** The roof of boards before the shingles (or the coppi): boards down the slope, a batten across, snow in winter. */
    private fun roofBoard(x: Float, u: Float, px: Int, py: Int): Int {
        if (winter && (u > 0.12f || Noise.rnd(px, py, 45) < 0.7f)) return if (Dither.at(px, py) < 0.8f) Pal.SNOW_L else Pal.SNOW_M
        if (abs(u - 0.55f) < 0.05f) return woodX
        return board(u * 6f, x, 0.5f, if (u > 0.9f) 1.05f else 0.92f)
    }

    /**
     * Friuli's coppi at [x] cells along the roof, [u] of the way up from the eave: half-round terracotta tiles in columns,
     * the covers' backs lit on their left, the channels between them darker, each row's lip shading the row under it; a
     * weathered tile here and there, snow in winter.
     */
    private fun coppo(x: Float, u: Float, px: Int, py: Int): Int {
        if (winter && (u > 0.1f || Noise.rnd(px, py, 45) < 0.7f)) return if (Dither.at(px, py) < 0.8f) Pal.SNOW_L else Pal.SNOW_M
        val r = floor(u * 6f).toInt(); val fr = u * 6f - r
        val a = x / 0.34f; val col = floor(a).toInt(); val fa = a - col
        val cover = (col and 1) == 0
        val tone = Noise.rnd(col, r, 146)
        val base = when { tone < 0.16f -> COP_OLD; tone > 0.86f -> COP_L; else -> COP_M }
        return when {
            fr < 0.14f -> COP_D
            cover && fa < (if (fine) 0.22f else 0.3f) -> Col.mix(base, COP_L, 0.6f)
            cover && fa > 0.8f -> COP_D
            cover -> base
            fine && fa in 0.4f..0.6f -> Col.scale(base, 0.68f)
            else -> Col.scale(base, 0.8f)
        }
    }

    /**
     * River cobbles in courses (Friuli's walls) at [a] cells along a wall and [z] px up: rounded pale stones in lime mortar,
     * each lit at its upper left, two courses of brick through them every few courses ([brick]); [light] dims the face.
     */
    private fun cobbleAt(a: Float, z: Float, px: Int, py: Int, light: Float): Int {
        val period = 6.6f
        val k = floor(z / period); val band = z - k * period
        var col = if (band < 1.5f) brick(a, band, px, py) else {
            val ch = 1.3f
            val zz = band - 1.5f + k * 5.1f
            val course = floor(zz / ch).toInt(); val fz = zz - course * ch
            val bl = 0.3f + Noise.rnd(course, 171) * 0.14f
            val u = a / bl + Noise.rnd(course, 172) * 3f
            val b = floor(u).toInt(); val fa = u - b
            val tone = Noise.rnd(b, course, 173)
            val ex = (fa - 0.5f) * 2.1f; val ez = (fz / ch - 0.5f) * 2.2f
            val e = ex * ex + ez * ez * (0.8f + tone * 0.4f)
            when {
                e > 1f -> if (ez < -0.6f && Dither.at(px, py) < 0.6f) COB_JOINT else COB_MORTAR
                ex < -0.25f && ez > 0.15f && e < 0.55f -> if (tone < 0.5f) COB_HI else COB_L
                ex > 0.45f || ez < -0.55f -> COB_D
                tone < 0.3f -> COB_L
                tone > 0.82f -> COB_SAND
                else -> COB_M
            }
        }
        if (fine && col != COB_MORTAR && Noise.rnd(px, py, 174) < 0.05f) col = Col.scale(col, 0.88f)
        return if (light == 1f) col else Col.scale(col, light)
    }

    /** Brick in courses at [a] cells along and [z] px up: long bricks, staggered, in pale mortar. */
    private fun brick(a: Float, z: Float, px: Int, py: Int): Int {
        val bh = 0.75f
        val course = floor(z / bh).toInt(); val fz = z - course * bh
        val u = a / 0.48f + (course and 1) * 0.5f; val b = floor(u).toInt(); val fa = u - b
        val tone = Noise.rnd(b, course, 175)
        return when {
            fz < 0.16f || fa < (if (fine) 0.06f else 0.1f) -> COB_MORTAR
            tone < 0.25f -> BRICK_L
            tone > 0.8f -> BRICK_D
            else -> BRICK_M
        }
    }

    /** A brick quoin at a corner of Friuli's cobble wall ([a] cells along it, [z] px up), long and short by turns. */
    private fun quoin(a: Float, z: Float, px: Int, py: Int, light: Float): Int {
        val col = brick(a + floor(z / 1.5f) * 0.24f, z, px, py)
        return if (light == 1f) col else Col.scale(col, light)
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

    /**
     * The house's front round the hives: corner posts, the ledges under each row, the board over the top row. In Friuli the
     * band over the shed's opening is of brick and cobbles between brick pillars (at first boards between wooden posts).
     */
    private fun houseFront() {
        val yf = hy1
        if (friuli) {
            val band = tierBase(rows - 1) + F_BODY + F_LID + 0.25f
            if (boards) {
                onRight(hf, hx0, hx1, band, eaveZ) { x, z -> board(x, z, 0.9f, 1f) }
                for (x in floatArrayOf(hx0, hx1 - 0.35f)) block(x, yf - 0.35f, x + 0.35f, yf, ft, eaveZ, { _, _ -> woodM }, { _, _ -> woodD }, { _, _ -> woodL })
            } else {
                quadPlot(hx0, hf, band, hx1, hf, band, hx1, hf, eaveZ, hx0, hf, eaveZ) { px, py ->
                    val x = wallYx(px, hf); val z = wallYz(px, py, hf)
                    if (z < band + 0.75f) brick(x, z - band, px, py) else cobbleAt(x, z, px, py, 1f)
                }
                for (x in floatArrayOf(hx0, hx1 - 0.4f)) quadPlot(x, yf, ft, x + 0.4f, yf, ft, x + 0.4f, yf, eaveZ, x, yf, eaveZ) { px, py ->
                    brick(wallYx(px, yf) * 2f, wallYz(px, py, yf), px, py)
                }
                for (x in floatArrayOf(hx0, hx1 - 0.4f)) quadPlot(x + 0.4f, yf - 0.4f, ft, x + 0.4f, yf, ft, x + 0.4f, yf, eaveZ, x + 0.4f, yf - 0.4f, eaveZ) { px, py ->
                    Col.scale(brick(wallXy(px, x + 0.4f) * 2f, wallXz(px, py, x + 0.4f), px, py), 0.78f)
                }
            }
            return
        }
        // the band over the hives up to the eave
        onRight(hf, hx0, hx1, rowZ[rows - 1] + rowH + 0.3f, eaveZ) { x, z -> board(x, z, 0.9f, 1f) }
        // the ledges: the bees' landing boards under each row
        for (r in 0 until rows) {
            val z = rowZ[r]
            block(hiveX0 - 0.05f, hf, hiveX0 + hives * hiveW + 0.05f, hf + 0.28f, z - 0.45f, z - 0.05f,
                { _, zz -> if (zz > z - 0.2f) woodL else woodD }, { _, _ -> woodD }, { _, _ -> woodL })
        }
        // the corner posts
        for (x in floatArrayOf(hx0, hx1 - 0.35f)) block(x, yf - 0.35f, x + 0.35f, yf, ft, eaveZ, { _, _ -> woodM }, { _, _ -> woodD }, { _, _ -> woodL })
    }

    /** Whether the front of row [r], hive [h] is painted at this level: the middle ones of the lower rows first. */
    private fun isPainted(r: Int, h: Int): Boolean {
        if (painted >= rows * hives) return true
        var k = 0
        for (f in PAINT_ORDER) {
            if (f / hives >= rows) continue
            if (f == r * hives + h) return k < painted
            k++
        }
        return false
    }

    /** The hive fronts, three rows of six: each a painted board with its own picture, the flight slit under it. */
    private fun panels() {
        for ((r, z) in rowZ.withIndex()) {
            if (r >= rows) break
            for (h in 0 until hives) {
                if (!isPainted(r, h)) continue
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
    }

    /** The fronts not painted yet: plain boards in their frame, the flight slit under them. */
    private fun plainFronts() {
        for (r in 0 until rows) for (h in 0 until hives) {
            if (isPainted(r, h)) continue
            val z = rowZ[r]
            val xa = hiveX0 + h * hiveW; val xb = xa + hiveW - 0.05f
            onRight(hf, xa, xb, z, z + rowH) { x, zz ->
                val u = (x - xa) / (xb - xa); val v = 1f - (zz - z) / rowH
                val eu = min(u, 1f - u) * (xb - xa) * 4.47f; val ev = min(v, 1f - v) * rowH
                when {
                    eu < 0.3f || ev < 0.3f -> woodX
                    v > 0.86f && abs(u - 0.5f) < 0.2f -> Col.hex(0x1A120C)
                    v > 0.8f -> woodD
                    else -> board(zz * 1.4f + h * 3f, x - xa, 0.32f, 1.05f)
                }
            }
        }
    }

    /** Friuli: the base of tier [t] of box hives (the lower on its rail over the footing, the upper on its shelf). */
    private fun tierBase(t: Int): Float = if (t == 0) F_TIER0 else F_TIER1

    /** Friuli: the slot of the [n]th hive on a tier that has [of] of them (three at first: the middle ones). */
    private fun slotOf(n: Int, of: Int): Int = if (of >= hives) n else n + (hives - of) / 2

    /**
     * Friuli's apiary inside: the shed's dark back behind its open front, the rail and the shelf, and the Dadant box hives
     * on them, each painted a plain colour (white, yellow, light blue, green), its number on it closer up, its entrance and
     * landing board at its foot, a tin lid on top (snow on it in winter).
     */
    private fun boxes() {
        val x0 = hiveX0 - 0.05f; val x1 = hiveX0 + hives * hiveW + 0.02f
        val band = tierBase(rows - 1) + F_BODY + F_LID + 0.25f
        // the shed's inside behind the hives, deep in shade
        quadPlot(x0, hf - 0.02f, ft, x1, hf - 0.02f, ft, x1, hf - 0.02f, band, x0, hf - 0.02f, band) { px, py ->
            val x = wallYx(px, hf - 0.02f); val z = wallYz(px, py, hf - 0.02f)
            if (boards) board(z, x, 0.42f, 0.42f) else cobbleAt(x, z, px, py, 0.42f)
        }
        // the rail over the footing, the shelf of the upper tier
        block(x0, hf - 0.9f, x1, hf + 0.1f, ft, F_TIER0, { _, _ -> woodD }, { _, _ -> woodX }, { _, _ -> woodM })
        if (rows > 1) block(x0, hf - 0.95f, x1, hf + 0.14f, F_TIER1 - 0.4f, F_TIER1, { _, z -> if (z > F_TIER1 - 0.15f) woodL else woodM }, { _, _ -> woodD }, { _, _ -> woodL })
        for (t in 0 until rows) for (n in 0 until tierHives[t]) boxAt(t, slotOf(n, tierHives[t]))
    }

    /** Box hive [s] on tier [t]: its body, its tin lid, its landing board, its number closer up. */
    private fun boxAt(t: Int, s: Int) {
        val xa = hiveX0 + s * hiveW + 0.08f; val xb = xa + 0.86f
        val yb = hf - 0.85f; val yf = hf + 0.1f
        val z0 = tierBase(t); val z1 = z0 + F_BODY
        val col = BOX_COLOURS[Math.floorMod(Noise.hash(t, s, 147), BOX_COLOURS.size)]
        val lt = Col.mix(col, Col.hex(0xFFFFFF), 0.3f); val dk = Col.scale(col, 0.8f)
        val number = t * hives + s + 1
        block(xa, yb, xb, yf, z0, z1, { x, z ->
            val u = (x - xa) / (xb - xa)
            when {
                z < z0 + 0.55f && z > z0 + 0.15f && abs(u - 0.5f) < 0.3f -> Col.hex(0x1A120C)
                u < 0.07f -> lt
                u > 0.93f || z < z0 + 0.12f -> dk
                fine && numberAt(number, x - xa, z1 - z) -> Col.hex(0x2A2A30)
                // a seam between the brood box and the super over it, where it has one
                abs(z - (z0 + 2.9f)) < 0.12f && (number % 3 != 1) -> dk
                else -> col
            }
        }, { _, z -> if (z < z0 + 0.12f) Col.scale(col, 0.55f) else Col.scale(col, 0.68f) }, { _, _ -> col })
        // the lid
        block(xa - 0.06f, yb - 0.04f, xb + 0.06f, yf + 0.08f, z1, z1 + F_LID,
            { _, z -> if (z > z1 + F_LID - 0.25f) TIN_L else TIN_M }, { _, _ -> TIN_D },
            { x, y -> if (winter && Noise.rnd((x * 30).toInt(), (y * 30).toInt(), 148) < 0.85f) Pal.SNOW_L else TIN_L })
        // the landing board
        block(xa + 0.12f, yf, xb - 0.12f, yf + 0.24f, z0, z0 + 0.2f, { _, _ -> woodL }, { _, _ -> woodD }, { _, _ -> woodL })
    }

    /**
     * Whether the hive's painted [number] has a stroke at [a] cells from its front's left and [d] px down from its top: small
     * figures, a pixel of the picture each, near the front's top (closer up only).
     */
    private fun numberAt(number: Int, a: Float, d: Float): Boolean {
        val text = number.toString()
        val fx = floor(a * 4f * K - (0.86f * 4f * K - BoardFont.width(text)) / 2f).toInt()
        val fy = floor(d * K - 0.8f * K).toInt()
        if (fy < 0 || fy > 5 || fx < 0) return false
        var x = 0
        for (ch in text) {
            val g = BoardFont.glyph(ch) ?: continue
            val w = g[0].length
            if (fx in x until x + w) return g[fy][fx - x] == '#'
            x += w + 1
        }
        return false
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

    /** Bunting for World Bee Day: little flags in white, blue and red along the eave (in Friuli yellow, blue and white). */
    private fun bunting() {
        val cols = if (bunting2) intArrayOf(Col.hex(0xF2C230), Col.hex(0x2E5AA8), Pal.FLAG_WHITE) else intArrayOf(Pal.FLAG_WHITE, Pal.FLAG_BLUE, Pal.FLAG_RED)
        val eave = eaveZ
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

    /** An old straw hive (koš) on a stump by the house's end (on the grass before the stumps): a dome of coiled straw, its mouth at the foot. */
    private fun skep() {
        if (stumps) stump(skepX, skepY, 3.2f)
        val bx = iso.sx(skepX, skepY); val by = iso.sy(skepX, skepY, skepZ)
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

    /**
     * Friuli's hive apart (the `hive`): a box hive on its stump by the shed's end (on two bricks before the stumps), light
     * blue under its tin lid, its entrance and landing board facing the garden: the empty one Aldo keeps for a swarm.
     */
    private fun boxHive() {
        val z0 = if (stumps) { stump(skepX, skepY, 3.2f); 3.2f } else {
            for (dx in floatArrayOf(-0.25f, 0.25f)) block(skepX + dx - 0.12f, skepY - 0.3f, skepX + dx + 0.12f, skepY + 0.3f, 0f, 0.7f,
                { _, _ -> BRICK_M }, { _, _ -> BRICK_D }, { _, _ -> BRICK_L })
            0.7f
        }
        val xa = skepX - 0.38f; val xb = skepX + 0.38f; val yb = skepY - 0.45f; val yf = skepY + 0.38f
        val z1 = z0 + 3.6f
        val col = Col.hex(0x9CC6E6)
        block(xa, yb, xb, yf, z0, z1, { x, z ->
            val u = (x - xa) / (xb - xa)
            when {
                z < z0 + 0.5f && z > z0 + 0.12f && abs(u - 0.5f) < 0.3f -> Col.hex(0x1A120C)
                u < 0.08f -> Col.mix(col, Col.hex(0xFFFFFF), 0.3f)
                u > 0.92f -> Col.scale(col, 0.8f)
                else -> col
            }
        }, { _, _ -> Col.scale(col, 0.68f) }, { _, _ -> col })
        block(xa - 0.06f, yb - 0.04f, xb + 0.06f, yf + 0.07f, z1, z1 + 0.7f,
            { _, z -> if (z > z1 + 0.45f) Pal.FLAG_WHITE else Col.hex(0xE4E2DA) }, { _, _ -> Col.hex(0xB8B6AE) },
            { _, _ -> if (winter) Pal.SNOW_L else Pal.FLAG_WHITE })
        block(xa + 0.1f, yf, xb - 0.1f, yf + 0.22f, z0, z0 + 0.18f, { _, _ -> woodL }, { _, _ -> woodD }, { _, _ -> woodL })
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

    /**
     * Friuli's acacia (the robinia of the Collio's woods and hedges) where the linden stands: a rough grey trunk with deep
     * furrows, leaning a little, forking into crooked limbs; an airy crown of small yellow-green leaves with the light coming
     * through; in May heavy with hanging white racemes (Aldo's acacia honey); fresh and thin in April, yellowing in September,
     * yellow in October, thin in November; bare in winter, its dark pods hanging, snow along the limbs.
     */
    private fun acacia() {
        val bx = iso.sx(treeX, treeY); val by = iso.sy(treeX, treeY, 0f)
        shade(bx + 2f * K, by, 9f * K, 3f * K, 0.8f, groundId)
        val m = frame.month
        // the trunk, leaning a little to the right as it rises, furrowed
        val th = 9.5f * K; val tw = 1.15f * K
        for (py in max(c.top, floor(by - th).toInt())..min(c.bottom - 1, floor(by).toInt())) {
            val v = (by - py) / th
            val lean = v * v * 1.2f * K
            for (px in floor(bx - tw + lean).toInt()..floor(bx + tw + lean).toInt()) {
                val f = (px + 0.5f - bx - lean) / tw
                val furrow = abs(((f + 1f) * 2.2f + Noise.v1(py * 0.35f / detail, 81) * 0.9f) % 1f - 0.5f) < 0.16f
                c.set(px, py, when {
                    f < -0.55f -> ACACIA_BARK_L
                    f > 0.5f -> ACACIA_BARK_D
                    furrow -> ACACIA_BARK_D
                    else -> ACACIA_BARK_M
                })
            }
        }
        // the crooked limbs from the fork, and their twigs
        val fx0 = bx + 1.2f * K; val fy0 = by - th
        val limbs = floatArrayOf(-2.35f, -1.75f, -1.2f, -0.75f)
        for ((k, a) in limbs.withIndex()) {
            val len = (6.5f + Noise.rnd(k, 82) * 2.5f) * K
            val mx = fx0 + kotlin.math.cos(a + 0.25f) * len * 0.5f; val my = fy0 + kotlin.math.sin(a + 0.25f) * len * 0.5f
            val ex = mx + kotlin.math.cos(a - 0.2f) * len * 0.55f; val ey = my + kotlin.math.sin(a - 0.2f) * len * 0.55f
            for (o in 0 until detail * (if (k == 1 || k == 2) 2 else 1)) {
                c.line(floor(fx0).toInt() + o, floor(fy0).toInt(), floor(mx).toInt() + o, floor(my).toInt(), ACACIA_BARK_D)
                c.line(floor(mx).toInt() + o, floor(my).toInt(), floor(ex).toInt() + o, floor(ey).toInt(), ACACIA_BARK_M)
            }
            for (f in intArrayOf(-1, 1)) {
                val a2 = a + f * 0.55f
                c.line(floor(ex).toInt(), floor(ey).toInt(), floor(ex + kotlin.math.cos(a2) * len * 0.35f).toInt(), floor(ey + kotlin.math.sin(a2) * len * 0.35f).toInt(), ACACIA_BARK_M)
            }
            if (m == 12 || m <= 3) {
                if (winter) c.fillRect(floor(mx).toInt() - detail, floor(my).toInt() - detail, 2 * detail, detail, Pal.SNOW_L)
                // last year's pods, dark and twisted, hanging from the twigs (not in March's first green)
                if (m != 3) for (q in 0 until 3) {
                    val qx = floor(ex + (q - 1) * 1.4f * K).toInt(); val qy = floor(ey + (Noise.rnd(k, q, 83) * 1.5f) * K).toInt()
                    c.fillRect(qx, qy, detail, floor(2.2f * K).toInt(), ACACIA_POD)
                }
            }
        }
        if (m == 12 || m <= 3) return
        val (dk, md, lt) = when (m) {
            4 -> Triple(Col.hex(0x6A9A3E), Col.hex(0x8CBC52), Col.hex(0xB8DC78))
            9 -> Triple(Col.hex(0x6A7E2E), Col.hex(0x8E9E3A), Col.hex(0xC4C258))
            10 -> Triple(Col.hex(0xA8861E), Col.hex(0xD4AE2E), Col.hex(0xF2D45A))
            11 -> Triple(Col.hex(0x8A7230), Col.hex(0xB0943C), Col.hex(0xD2B456))
            else -> Triple(Col.hex(0x46742C), Col.hex(0x649A3A), Col.hex(0x94C258))
        }
        val bloom = m == 5
        val sparse = when (m) { 4 -> 0.42f; 11 -> 0.45f; else -> 0.2f }
        val cx = bx + 1.6f * K; val cy = by - th - 6.5f * K
        val rx = 10f * K; val ryy = 7.5f * K
        val sway = sin(t * 0.9).toFloat() * 0.4f * K * gust
        // the crown in lobes, with the light coming through between its leaves
        for (py in max(c.top, floor(cy - ryy).toInt())..min(c.bottom - 1, floor(cy + ryy).toInt())) for (px in max(c.left, floor(cx - rx + sway).toInt())..min(c.right - 1, floor(cx + rx + sway).toInt())) {
            val dx = (px + 0.5f - cx - sway) / rx; val dy = (py + 0.5f - cy) / ryy
            val lobe = Noise.v2(px / (3.2f * K), py / (3.2f * K), 84)
            val d = dx * dx + dy * dy + (lobe - 0.5f) * 0.75f
            if (d > 1f) continue
            // gaps between the clumps of small leaves, the light coming through
            if (Noise.v2(px / (1.3f * K), py / (1.3f * K), 85) < sparse + max(0f, d - 0.55f) * 0.9f) continue
            val leaf = Noise.rnd(px / detail, py / detail, 88)
            val light = -dx * 0.6f - dy * 0.8f + (lobe - 0.5f) + (leaf - 0.5f) * 0.5f
            var col = if (light > 0.3f) lt else if (light > -0.3f) md else dk
            if (bloom && light > -0.45f) {
                // the racemes: white bunches hanging, a few rows long
                val rcol = Noise.rnd(px / (2 * detail), (py / (3 * detail)), 86)
                if (rcol < 0.2f) col = if (Noise.rnd(px / detail, py / detail, 87) < 0.5f) ACACIA_FLOWER else ACACIA_FLOWER_D
            }
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
        val bx = iso.sx(smokerX, smokerY); val by = iso.sy(smokerX, smokerY, smokerZ)
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

    /**
     * Jars of honey on the table (as many as the level has): golden and glowing in the sun, white lids, one tall jar at the
     * back. In Friuli pale acacia honey, and in autumn one jar of dark chestnut honey among them.
     */
    private fun jars() {
        val chestnut = paleHoney && frame.month in 9..11
        for ((i, x) in floatArrayOf(8.75f, 9.15f, 9.55f, 8.95f).withIndex()) {
            if (i >= jarsN) continue
            val y = if (i == 3) ty0 + 0.3f else ty0 + 0.65f
            val tall = if (i == 3) 3.2f else 2.4f
            val bx = iso.sx(x, y); val by = iso.sy(x, y, tz)
            val rw = 0.85f * K; val hh = tall * K
            val (hm, hl, hd) = when {
                chestnut && i == 1 -> Triple(CHESTNUT, CHESTNUT_L, CHESTNUT_D)
                paleHoney -> Triple(ACACIA_HONEY, ACACIA_HONEY_L, ACACIA_HONEY_D)
                else -> Triple(honey, honeyL, honeyD)
            }
            c.fillRect(floor(bx - rw).toInt(), floor(by - hh).toInt(), floor(2 * rw).toInt() + 1, floor(hh).toInt(), hm)
            c.fillRect(floor(bx - rw).toInt(), floor(by - hh).toInt(), detail, floor(hh).toInt(), hl)
            c.fillRect(floor(bx + rw * 0.45f).toInt(), floor(by - hh).toInt(), floor(rw * 0.55f).toInt() + 1, floor(hh).toInt(), hd)
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
        val hx = if (home) skepX else beeX; val hy = if (home) skepY + 0.45f else beeY; val hz = if (home) (if (stumps) 3.5f else if (boxHive) 1.0f else 0.3f) else beeZ
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

    /** Friuli: the box hive bee [j] flies from: its tier and its slot. */
    private fun boxOf(j: Int): Pair<Int, Int> {
        val all = tierHives[0] + (if (rows > 1) tierHives[1] else 0)
        val k = j % max(1, all)
        return if (k < tierHives[0]) 0 to slotOf(k, tierHives[0]) else 1 to slotOf(k - tierHives[0], tierHives[1])
    }

    /** The bees out and about: from the hives' mouths to the flowers and back in loops, a few crawling on the ledges. */
    private fun bees() {
        val n = beesOut()
        if (n == 0) return
        val d = detail
        s.fx {
            for (j in 0 until n) {
                val ex: Float; val ez: Float
                if (friuli) {
                    val (tt, ss) = boxOf(j)
                    ex = hiveX0 + ss * hiveW + 0.51f; ez = tierBase(tt) + 0.35f
                } else {
                    val r = j % rows; val h = (j / 3) % hives
                    ex = hiveX0 + h * hiveW + hiveW * 0.5f; ez = rowZ[r] + 0.2f
                }
                val speed = 0.12 + Noise.rnd(j, 55) * 0.1
                val ph = ((t * speed + Noise.rnd(j, 56)) % 1.0).toFloat()
                // out to a flower and back, a loop at the far end
                // most to the flowers, some to the water
                val water = garden && j % 4 == 3
                val tx = if (water) basinX + (Noise.rnd(j, 57) - 0.5f) * 0.8f else flX + (Noise.rnd(j, 57) - 0.5f) * bedRx * 1.6f
                val ty = if (water) basinY + (Noise.rnd(j, 58) - 0.5f) * 0.4f else flY + (Noise.rnd(j, 58) - 0.5f) * bedRy * 1.6f
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
                val bx: Int; val by: Int
                if (friuli) {
                    val (tt, ss) = boxOf(j)
                    val x = hiveX0 + ss * hiveW + 0.25f + ((t * 0.3 + Noise.rnd(j, 59)) % 1.0).toFloat() * 0.5f
                    bx = iso.ix(x, hf + 0.25f); by = iso.iy(x, hf + 0.25f, tierBase(tt) + 0.2f)
                } else {
                    val r = j % rows; val h = j % hives
                    val x = hiveX0 + h * hiveW + 0.25f + ((t * 0.3 + Noise.rnd(j, 59)) % 1.0).toFloat() * 0.5f
                    bx = iso.ix(x, hf + 0.15f); by = iso.iy(x, hf + 0.15f, rowZ[r] - 0.05f)
                }
                for (yy in 0 until d) for (xx in 0 until d) c.blend(bx + xx, by - d + yy, beeC, 0.9f)
            }
        }
    }

    /** A swarm ([level] 0..1): a cloud of bees rising off the hives and circling up by the linden, more and further the higher. */
    private fun swarm(level: Float) {
        if (level <= 0.02f) return
        val sz = 9f - (eave - eaveZ)
        val ax = iso.sx(5.2f, hf + 0.4f); val ay = iso.sy(5.2f, hf + 0.4f, sz)
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
        val bx = iso.sx(smokerX, smokerY); val by = iso.sy(smokerX, smokerY, smokerZ) - 5.4f * K
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

    /**
     * Friuli's `panel`: Aldo's sign by the path's foot, a yellow board on two posts with MIELE painted on it in brown, so
     * whoever passes knows there is honey; snow on its top in winter.
     */
    private fun aldoSign() {
        val bx = iso.sx(signX, signY); val by = iso.sy(signX, signY, 0f)
        val d = detail
        val text = "MIELE"
        val tw = BoardFont.width(text)
        val bw = (tw + 4) * d; val bh = 10 * d
        val x0 = floor(bx - bw / 2f).toInt(); val y0 = floor(by - 12f * K).toInt()
        // the posts
        for (px in intArrayOf(x0 + d, x0 + bw - 3 * d)) {
            c.fillRect(px, y0 + d, 2 * d, floor(by).toInt() - y0 - d, woodM)
            c.fillRect(px + d, y0 + d, d, floor(by).toInt() - y0 - d, woodD)
        }
        // the board in its frame, the letters
        c.fillRect(x0, y0, bw, bh, woodX)
        c.fillRect(x0 + d, y0 + d, bw - 2 * d, bh - 2 * d, SIGN_YELLOW)
        c.fillRect(x0 + d, y0 + bh - 2 * d, bw - 2 * d, d, SIGN_YELLOW_D)
        BoardFont.write(text) { _, x, k, row, _ -> if (row in 0..5) c.fillRect(x0 + (2 + x + k) * d, y0 + (2 + row) * d, d, d, SIGN_INK) }
        if (winter) c.fillRect(x0, y0 - d, bw, d, Pal.SNOW_L)
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
        val motif = if (sign) SIGNS[pk.step.coerceIn(0, SIGNS.lastIndex)] else LOOKS[pk.step.coerceIn(0, LOOKS.lastIndex)]
        val pw = 30f * K * grow; val ph = 20f * K * grow
        val cx = iso.sx(5.8f, ry).coerceIn(pw / 2f + K, w - pw / 2f - K)
        val cy = (iso.sy(5.8f, ry, ridgeZ) - 8f * K).coerceIn(ph / 2f + K, h - ph / 2f - K)
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

        // ---------------------------------------------------------------- the levels

        /** How much lower the bee house's eave is for each row of hives it doesn't have yet. */
        private const val ROW_STEP = 3.8f

        /** The fronts painted at each level (of 6, 12, 18 there are), the flower bed's size, the jars on the table. */
        private val PAINTED = intArrayOf(2, 6, 12, 18, 18)
        private val BED = floatArrayOf(0.6f, 0.7f, 0.82f, 1f, 1f)
        private val JARS = intArrayOf(2, 2, 3, 3, 4)

        /** The order the fronts are painted in (row × 6 + hive): the middle ones of the lower rows first. */
        private val PAINT_ORDER: List<Int> = (0 until 18).sortedBy { f ->
            val r = f / 6; val h = f % 6
            r * 0.9f + abs(h - 2.5f) * 0.55f + Noise.rnd(r, h, 149) * 0.3f
        }

        // ---------------------------------------------------------------- Friuli's look

        /** Friuli's tiers: where the lower and the upper stand, a box's body and its lid; the eave of a shed with one tier; hives a tier. */
        private const val F_TIER0 = 1.55f
        private const val F_TIER1 = 8.6f
        private const val F_BODY = 4.4f
        private const val F_LID = 0.8f
        private const val F_EAVE1 = 9.6f
        private const val F_HIVES = 6

        /** The box hives' plain colours: white, yellow, light blue, green. */
        private val BOX_COLOURS = intArrayOf(h(0xF0ECE2), h(0xF2C84A), h(0x9CC6E6), h(0x92C27E), h(0xF4E8C8), h(0x8EBCDA))
        private val TIN_L = h(0xE2E6EA); private val TIN_M = h(0xC2C6CC); private val TIN_D = h(0x8E949C)

        /** Coppi: the terracotta tiles' light, middle and dark, an old weathered one. */
        private val COP_L = h(0xE0905E); private val COP_M = h(0xC06C42); private val COP_D = h(0x86442A); private val COP_OLD = h(0x9E6A4C)

        /** River cobbles in lime mortar, and the brick through them. */
        private val COB_HI = h(0xDCD6C8); private val COB_L = h(0xC4BDAE); private val COB_M = h(0xA9A193); private val COB_D = h(0x8A8276)
        private val COB_SAND = h(0xBCA886); private val COB_MORTAR = h(0xC0B6A2); private val COB_JOINT = h(0x6E685C)
        private val BRICK_L = h(0xCC7A50); private val BRICK_M = h(0xAC5E3C); private val BRICK_D = h(0x86472E)

        /** The acacia's bark, its pods, its white flowers. */
        private val ACACIA_BARK_L = h(0x8E8678); private val ACACIA_BARK_M = h(0x6A6256); private val ACACIA_BARK_D = h(0x463E36)
        private val ACACIA_POD = h(0x4A3222)
        private val ACACIA_FLOWER = h(0xFAF6EA); private val ACACIA_FLOWER_D = h(0xE6E0C8)

        /** Acacia honey, pale and clear; chestnut honey, dark. */
        private val ACACIA_HONEY = h(0xF2D672); private val ACACIA_HONEY_L = h(0xFFF0B0); private val ACACIA_HONEY_D = h(0xD8B44C)
        private val CHESTNUT = h(0x8A4616); private val CHESTNUT_L = h(0xB4682A); private val CHESTNUT_D = h(0x5A2C0C)

        /** Aldo's sign: its yellow board, the lower edge's shade, the brown letters. */
        private val SIGN_YELLOW = h(0xF2C644); private val SIGN_YELLOW_D = h(0xD2A430); private val SIGN_INK = h(0x5A3212)

        /** A sign up close ([W] × [H] cells of the board): [text] across its top, [deco] painted under it (null: the board). */
        private fun signMotif(text: String, deco: (Float, Float) -> Int?): (Float, Float) -> Int {
            val wCells = 36f; val hCells = 24f
            val tw = BoardFont.width(text)
            val ink = BooleanArray(tw * 6)
            BoardFont.write(text) { _, x, k, row, _ -> if (row in 0..5 && x + k < tw) ink[(x + k) * 6 + row] = true }
            val ox = floor((wCells - tw) / 2f).toInt()
            return { u, v ->
                val gx = floor(u * wCells).toInt() - ox; val gy = floor(v * hCells).toInt() - 2
                if (gy in 0..5 && gx in 0 until tw && ink[gx * 6 + gy]) SIGN_INK
                else deco(u, v) ?: if (v > 0.94f) SIGN_YELLOW_D else SIGN_YELLOW
            }
        }

        /** A pot of honey with a bee by it. */
        private val potDeco: (Float, Float) -> Int? = { u, v ->
            when {
                ell(u, v, 0.77f, 0.6f, 0.025f, 0.04f) || ell(u, v, 0.73f, 0.58f, 0.025f, 0.035f) -> h(0xF4FAFF) // wings
                ell(u, v, 0.76f, 0.66f, 0.06f, 0.045f) -> if (((u - 0.7f) / 0.025f).toInt() % 2 == 0) h(0x2A1E10) else h(0xF2B42A)
                v in 0.5f..0.56f && abs(u - 0.45f) < 0.13f -> h(0xD83A3A)
                ell(u, v, 0.45f, 0.72f, 0.13f, 0.17f) && v > 0.55f -> if (u < 0.37f) h(0xFFE08A) else if (u > 0.52f) h(0xC07A16) else h(0xE8A21E)
                else -> null
            }
        }

        /** The acacia's racemes: white bunches hanging from a green twig, small leaves along it. */
        private val acaciaDeco: (Float, Float) -> Int? = { u, v ->
            val twig = abs(v - (0.46f + 0.04f * sin(u * 6f))) < 0.012f && u in 0.12f..0.88f
            var col: Int? = if (twig) h(0x4A6A2A) else null
            if (col == null) for (k in 0 until 3) {
                val cu = 0.27f + k * 0.23f
                for (q in 0 until 6) if (ell(u, v, cu + (if (q % 2 == 0) -0.015f else 0.015f), 0.53f + q * 0.055f, 0.022f, 0.026f)) col = if (q % 2 == 0) h(0xFAF6EA) else h(0xE2DCC4)
                if (ell(u, v, cu + 0.09f, 0.5f, 0.035f, 0.02f)) col = h(0x6E9A3A)
            }
            col
        }

        /** A chestnut leaf and a burr split open on its brown nut. */
        private val chestnutDeco: (Float, Float) -> Int? = { u, v ->
            val lu = (u - 0.36f) * 0.94f + (v - 0.7f) * 0.34f; val lv = -(u - 0.36f) * 0.34f + (v - 0.7f) * 0.94f
            val burr = d((u - 0.66f) / 0.1f, (v - 0.7f) / 0.15f)
            when {
                burr < 0.55f && v < 0.72f -> if (u < 0.65f) h(0xA05A2A) else h(0x6E3A18)
                burr < 1f -> if (Noise.rnd((u * 60).toInt(), (v * 60).toInt(), 150) < 0.35f) h(0xC8D86A) else h(0x8AAE3E)
                burr < 1.25f && Noise.rnd((u * 60).toInt(), (v * 60).toInt(), 151) < 0.4f -> h(0x8AAE3E)
                d(lu / 0.2f, lv / 0.06f) < 1f -> if (abs(lv) < 0.006f) h(0x3E6A24) else h(0x5E8E34)
                else -> null
            }
        }

        /** What a tap on Aldo's sign brings up close, one after another: his honey, acacia, chestnut. */
        private val SIGNS: List<(Float, Float) -> Int> = listOf(signMotif("MIELE", potDeco), signMotif("ACACIA", acaciaDeco), signMotif("CASTAGNO", chestnutDeco))
    }
}
