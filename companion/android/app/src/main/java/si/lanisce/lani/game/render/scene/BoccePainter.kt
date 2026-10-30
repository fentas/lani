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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Na balinišču: the village's bocce court (the project `balinisce`, Stari Janez's), as a diorama on its plot by the inn
 * (companion/SCENES.md "Arts"). Across the middle of the plot the long court of fine sand between its low boards, raked in
 * lines, the chalk lines across it at both ends; at its far end on the left the little jack (bulin) with the metal balls round
 * it (smooth ones and grooved ones, the two sides'), two more waiting at the near end on the right, where the thrower stands;
 * the folding rule (meter) on the back board by the far end, the rake leaning at the near end. At the back on the right the
 * inn: whitewashed walls over a stone plinth, a door with its sign (a painted bunch of grapes), windows with green shutters, a
 * roof of red tiles, a chimney smoking morning and evening; out from its front the pergola, a vine (brajda) on its beams over
 * the wooden table with the jug of wine and the glasses. Between the pergola and the court the scoreboard on its two posts (a
 * slate, MI | VI and the chalk marks), the lantern on its post for the evening games; at the back on the left the linden over
 * the court's far end; in front the spectators' bench along the court; by the inn's door a barrel and crates of bottles.
 *
 * The seasons: the vine in fresh leaf in spring, its grapes ripening from August, gold and red in October, bare canes in
 * winter; the linden in flower in June and July, gold in October, bare in winter; snow on the roof, the boards and the bench in
 * winter. At night the lantern and the inn's windows are lit.
 *
 * Its effects ([si.lanisce.lani.game.scene.SceneArt.effects]):
 * - `throw`: a ball thrown from the near end: up to 0.4 it flies in a low arc, then rolls over the sand, slowing, and comes
 *   to rest by the jack at 1 (0: still in the thrower's hand, none on the sand).
 * - `hit`: a ball thrown high at the one nearest the jack: up to 0.5 it flies in its arc, then a clack and a puff of sand,
 *   the ball it hit flying off toward the back board while it stays in its place (1).
 * - `measure`: the folding rule taken off the board and laid on the sand from the jack toward the nearest ball, unfolding
 *   (how far out; 0: on the board).
 * - `pour`: wine poured from the jug into the glass on the table: the jug tipping, a red stream, the glass filling (how
 *   full). Not set: the glass half full.
 * - `score`: the chalk marks under MI on the scoreboard, as many as a counting dialog counts (else nine), coming one by one
 *   as the level rises, under VI the other side's three. Not set: the game near its end, eleven to seven.
 * - `lantern`: 0 dark, 1 lit. Not set: lit from dusk.
 *
 * Tapped ([pokes]): a ball by the jack rolls a little and back; the second time it knocks into the next one.
 *
 * Closer up ([detail] 2 and 3): the sand's grain and the rake's lines, the boards' joints, the balls' grooves and glints, the
 * vine's leaves and grapes, the chalk on the slate, the roof's tiles.
 *
 * Friuli's court ([look]; companion/SCENES.md "Culture looks"): an osteria in ochre under coppi, its shutters brown, the
 * frasca (a leafy branch, the old sign of an osteria with its own wine open) hung over its door instead of the painted sign.
 */
internal class BoccePainter : RoomPainter() {
    override val art = "bocce"
    override val pokes = listOf(Poke("ball", listOf(1.6, 2.2), rest = 2.5))

    override val plotW = 12f
    override val plotD = 11f
    override val rise = 27f

    // the court across the plot: its sand between the boards over CX0..CX1 × CY0..CY1 (the far end at CX0), the boards BH high
    private val cx0 = 1.1f; private val cx1 = 11.3f; private val cy0 = 6.0f; private val cy1 = 8.2f; private val bh = 2.6f
    private val bt = 0.14f
    // the jack and the balls round it (x, y, side), the two waiting at the near end
    private val jackX = 2.6f; private val jackY = 7.05f
    private val balls = arrayOf(floatArrayOf(3.2f, 6.6f, 0f), floatArrayOf(2.0f, 7.5f, 1f), floatArrayOf(3.35f, 7.55f, 1f), floatArrayOf(1.95f, 6.55f, 0f))
    private val waiting = arrayOf(floatArrayOf(10.55f, 6.45f, 0f), floatArrayOf(10.7f, 7.75f, 1f))
    // the inn at the back on the right: IX0..IX1 × IY0..IY1, its walls IH high, the ridge RZ; its door, windows
    private val ix0 = 4.5f; private val ix1 = 11.7f; private val iy0 = 0.35f; private val iy1 = 2.4f; private val ih = 15f
    private val rz = 22.4f
    private val innA = 8.4f; private val innB = 9.4f; private val doorH = 12f
    private val wins = floatArrayOf(5.2f, 6.4f, 10.25f, 11.25f)
    private val winZa = 6f; private val winZb = 11.5f
    // the pergola: its front posts at y = PGY from PGX0 to PGX1, its beams at PGZ; the table under it
    private val pgy = 4.6f; private val pgx0 = 5.0f; private val pgx1 = 8.4f; private val pgz = 14.4f
    private val tbx0 = 5.6f; private val tbx1 = 7.6f; private val tby0 = 3.15f; private val tby1 = 4.0f; private val tbz = 7.2f
    // the scoreboard behind the court, the bench in front of it, the lantern's post, the rake, the linden, the barrel by the door
    private val sbY = 5.45f; private val sbX0 = 4.95f; private val sbX1 = 6.35f
    private val benchX0 = 4.6f; private val benchX1 = 8.2f; private val benchY0 = 8.95f; private val benchY1 = 9.4f; private val benchZ = 4.6f
    private val postX = 3.4f; private val postY = 4.5f; private val postZ = 18f
    private val rakeY = 5.9f
    private val treeX = 1.0f; private val treeY = 2.5f
    private val barrelX = 10.2f; private val barrelY = 3.2f

    // the people: at the near end throwing, behind the court by the jack, at the table
    private val throwPX = 10.95f; private val throwPY = 7.1f
    private val jackPX = 3.3f; private val jackPY = 5.3f
    private val tablePX = 7.4f; private val tablePY = 5.05f

    private val sandL = Col.hex(0xE8D8AE); private val sandM = Col.hex(0xD8C496); private val sandD = Col.hex(0xBCA678)
    private val boardL = Col.hex(0xB88A56); private val boardM = Col.hex(0x9A6E40); private val boardD = Col.hex(0x74502C)
    private val steelHi = Col.hex(0xF2F4F8); private val steelL = Col.hex(0xB8BEC8); private val steelM = Col.hex(0x868E9C); private val steelD = Col.hex(0x4E5664)
    private val wine = Col.hex(0x8A1E2E); private val wineL = Col.hex(0xC03A4A)
    private val slate = Col.hex(0x2E3A3A); private val chalk = Col.hex(0xEEF0EA)
    private val shutter = Col.hex(0x3F6B3A); private val shutterL = Col.hex(0x5E8E54)

    /** Friuli's osteria ([readInn]): ochre under coppi, brown shutters, the frasca over the door. */
    private var ochre = false
    private var frasca = false
    private var shutC = shutter; private var shutCL = shutterL

    private val fine: Boolean get() = detail > 1

    /** Out in the open: the light of the hour, no room's warmth. */
    override fun ambient(): FloatArray = floatArrayOf(env.ambR, env.ambG, env.ambB)

    /** Under the inn's eave the ground keeps dry. */
    override fun sheltered(x: Float, y: Float): Boolean = y < iy1 + 0.35f && x in ix0..ix1

    private fun readInn() {
        ochre = variant("inn") == "osteria"
        frasca = variant("frasca") != null
        shutC = colour("shutter", shutter); shutCL = colour("shutter.l", shutterL)
    }

    private fun lanternLit(): Boolean = fx("lantern")?.let { it > 0.5f } ?: (env.windows > 0.35f)

    override fun paint() {
        fit()
        vignette()
        lv = 1
        readInn()
        plot { x, y, px, py -> courtGround(x, y, px, py) }
        tufts(70, 37) { x, y -> busy(x, y) }
        // at the back: the linden on the left, the inn on the right and what stands against it, the pergola's table
        thing("linden") { linden() }
        thing("pergola") { pergolaBack() }
        prop { inn() }
        if (frasca) prop { frascaBranch() } else prop { grapesSign() }
        prop { barrel() }
        thing("table") { innTable() }
        thing("jug", slop = 1) { jug() }
        thing("glass", slop = 1) { glass() }
        thing("pergola") { pergolaFront() }
        // between them and the court: the lantern, the people by the jack and at the table, the scoreboard
        thing("lantern", slop = 1) { lampPost() }
        for (p in peopleAt("jack")) { footShadow(jackPX, jackPY); personAt(p, jackPX, jackPY, 0f) }
        for (p in peopleAt("table")) { footShadow(tablePX, tablePY); personAt(p, tablePX, tablePY, 0f) }
        thing("scoreboard") { scoreboard() }
        // the court: its back board and its far end's, the sand, the jack and the balls, the rule on the board, the rake
        thing("board") { boards(back = true) }
        thing("sand") { sandBed() }
        thing("court") { courtLines() }
        thing("jack", slop = 2) { jack() }
        thing("ball", slop = 1) { allBalls() }
        throwing()
        thing("meter", slop = 1) { meter() }
        for (p in peopleAt("throw")) { footShadow(throwPX, throwPY); personAt(p, throwPX, throwPY, 0f, flip = true) }
        thing("board") { boards(back = false) }
        thing("rake", slop = 1) { rake() }
        // in front: the spectators' bench
        thing("bench") { spectators() }
        fireflies(iso.ix(0f, 11f), iso.iy(11f, 11f, 18f), iso.ix(12f, 0f), iso.iy(12f, 11f, 0f), 9, 17)
        lights()
    }

    /** Where no tufts grow: the court, the inn and the pergola, the trodden ground before them, the tree's foot. */
    private fun busy(x: Float, y: Float): Boolean =
        (y in cy0 - 0.4f..cy1 + 0.4f && x < cx1 + 0.4f) || (y < pgy + 0.4f && x > ix0 - 0.4f) || hypot(x - treeX, y - treeY) < 0.7f ||
            (x in benchX0 - 0.3f..benchX1 + 0.3f && y in benchY0 - 0.3f..benchY1 + 0.3f)

    // ------------------------------------------------------------------ the ground

    override fun footprint(x: Float, y: Float) = shade(iso.sx(x, y), iso.sy(x, y, 0f), 5f * K, 1.6f * K, 0.75f, groundId)

    /** Grass, the yard before the inn trodden to gravel, bare round the court's near end; snow in winter. */
    private fun courtGround(x: Float, y: Float, px: Int, py: Int): Int {
        val g = grassAt(x, y, px, py)
        val yard = if (x > ix0 - 0.8f && y < pgy + 1.0f) 1f - max(0f, y - pgy - 0.2f) / 0.8f else 0f
        val path = if (y in cy0 - 0.9f..cy0 && x > 2.6f) 0.5f else 0f
        val end = max(0f, 1f - hypot((x - cx1 - 0.45f) * 1.4f, y - (cy0 + cy1) / 2f) / 1.5f)
        val f = max(max(yard, path), end) * (0.75f + Noise.v2(x * 2f, y * 2f, 3) * 0.45f)
        return dirtAt(px, py, f.coerceIn(0f, 1f), g)
    }

    // ------------------------------------------------------------------ the inn

    /** The inn: its front and its end wall, whitewash (ochre in Friuli) over a stone plinth, the door and the shuttered windows, the roof. */
    private fun inn() {
        val wall = if (ochre) Pal.OCHRE_M else Col.hex(0xF0E6CC)
        val wallL = if (ochre) Pal.OCHRE_L else Col.hex(0xFAF4E4)
        val wallD = if (ochre) Pal.OCHRE_D else Col.hex(0xD9CFB9)
        val lit = env.windows > 0.3f
        onY(iy1, ix0, ix1, 0f, ih) { x, z, px, py ->
            val n = Noise.rnd(px, py, 19)
            var col = when {
                x in innA..innB && z < doorH -> {
                    val e = min(min(x - innA, innB - x) * 4f, doorH - z)
                    if (e < 0.4f) Pal.STONE_L else if (floor((x - innA) / 0.25f).toInt() % 2 == 0) Pal.WOOD_M else Col.mix(Pal.WOOD_M, Pal.WOOD_D, 0.4f)
                }
                z < 2.2f -> if (n < 0.3f) Pal.STONE_D else if (n < 0.85f) Pal.STONE_M else Pal.STONE_L
                z < 2.6f -> Pal.STONE_L
                n < 0.04f -> wallL
                n > 0.96f -> wallD
                else -> wall
            }
            for (k in 0 until 2) {
                val a = wins[k * 2]; val b = wins[k * 2 + 1]
                if (z in winZa..winZb && x in a..b) {
                    val e = min(min(x - a, b - x) * 4f, min(z - winZa, winZb - z))
                    col = if (e < 0.4f) Pal.STONE_L else if (abs(x - (a + b) / 2f) < 0.05f || abs(z - (winZa + winZb) / 2f) < 0.3f) Pal.WOOD_L else if (lit) Pal.WINDOW_LIT else Pal.GLASS
                } else if (z in winZa..winZb && (x in a - 0.55f..a || x in b..b + 0.55f)) col = if (floor(z / 1.1f).toInt() % 2 == 0) shutC else shutCL
            }
            col
        }
        if (lit) glow {
            for (k in 0 until 2) {
                val a = wins[k * 2]; val b = wins[k * 2 + 1]
                onY(iy1 + 0.02f, a + 0.12f, b - 0.12f, winZa + 0.4f, winZb - 0.4f) { x, z, _, _ -> if (abs(x - (a + b) / 2f) < 0.05f || abs(z - (winZa + winZb) / 2f) < 0.3f) 0 else Pal.WINDOW_LIT }
            }
        }
        onX(ix1, iy0, iy1, 0f, ih) { _, z, px, py ->
            val n = Noise.rnd(px, py, 21)
            Col.scale(if (z < 2.2f) (if (n < 0.4f) Pal.STONE_D else Pal.STONE_M) else if (n < 0.05f) wallL else wall, 0.8f)
        }
        // the roof: red tiles (coppi in Friuli) in rows down the front slope, the gable end in the wall's plaster; snow in winter
        val (back, front, edge) = when {
            winter -> Triple(Pal.SNOW_M, Pal.SNOW_L, Pal.SNOW_D)
            else -> Triple(Pal.ROOF_D, Pal.ROOF_M, Col.hex(0x55200F))
        }
        val ry0 = iy0 - 0.3f; val ry1 = iy1 + 0.35f
        iso.gableX(ix0 - 0.25f, ry0, ih - 0.4f, ix1 - ix0 + 0.5f, ry1 - ry0, rz - ih + 0.4f, back, front, Col.scale(wall, 0.82f), edge)
        if (!winter) {
            val ym = (ry0 + ry1) / 2f
            for (k in 1 until 6) {
                val f = k / 6f
                val y = ym + (ry1 - ym) * f; val z = rz - (rz - ih + 0.4f) * f
                iso.line(ix0 - 0.25f, y, z, ix1 + 0.25f, y, z, Pal.ROOF_D)
            }
            var x = ix0
            while (x < ix1) { iso.line(x, ym, rz, x, ry1, ih - 0.4f, if (ochre) Pal.ROOF_D else Col.mix(Pal.ROOF_M, Pal.ROOF_D, 0.5f)); x += if (ochre) 0.3f else 0.55f }
            if (fine) iso.line(ix0 - 0.25f, ym, rz, ix1 + 0.25f, ym, rz, Pal.ROOF_L)
        }
        // the chimney, smoke from it morning and evening
        iso.box(ix1 - 1.6f, 1.0f, rz - 2f, 0.5f, 0.5f, 4f, Col.scale(wall, 0.95f), wall, wallD)
        if (s.hour in 6f..9f || s.hour in 17f..22f) s.smoke(iso.sx(ix1 - 1.35f, 1.25f), iso.sy(ix1 - 1.35f, 1.25f, rz + 2.4f), 0.6f, 23)
    }

    /** The painted sign over the door: a small board on its iron bracket with a bunch of grapes and a glass on it. */
    private fun grapesSign() {
        val bx = innB + 0.25f; val by = iy1 + 0.1f; val bz = doorH + 1.2f
        rod(bx, iy1, bz + 1.6f, bx + 0.9f, iy1 + 0.02f, bz + 1.6f, iron)
        val cx = iso.sx(bx + 0.55f, by); val top = iso.sy(bx + 0.55f, by, bz + 1.4f)
        val bw = 2.6f * K; val bhh = 3.2f * K
        c.fillRect(floor(cx - bw / 2).toInt(), floor(top).toInt(), bw.toInt(), bhh.toInt(), Pal.WOOD_M)
        c.fillRect(floor(cx - bw / 2 + detail).toInt(), floor(top + detail).toInt(), (bw - 2 * detail).toInt(), (bhh - 2 * detail).toInt(), Col.hex(0xF0E2C0))
        for (j in 0 until 6) {
            val gx = cx - 0.6f * K + (j % 3) * 0.55f * K - (j / 3) * 0.1f * K; val gy = top + 1.1f * K + (j / 3) * 0.6f * K
            c.fillCircle(gx, gy, 0.35f * K, Col.hex(0x5A2E6A))
        }
        c.set(floor(cx).toInt(), floor(top + 0.8f * K).toInt(), Col.hex(0x3E7A3A))
    }

    /** The frasca over the osteria's door (Friuli): a leafy branch hung from a nail, green, gold in autumn, dry in winter. */
    private fun frascaBranch() {
        val bx = (innA + innB) / 2f; val by = iy1 + 0.1f; val bz = doorH + 1.6f
        val cx = iso.sx(bx, by); val cy = iso.sy(bx, by, bz)
        val (d, m, l) = when (env.season) {
            Season.AUTUMN -> Triple(Col.hex(0x8A5A16), Col.hex(0xC0882A), Col.hex(0xE8B850))
            Season.WINTER -> Triple(Col.hex(0x5A4A36), Col.hex(0x7A644A), Col.hex(0x96805E))
            else -> Triple(Col.hex(0x2E5E26), Col.hex(0x46863A), Col.hex(0x74AE4E))
        }
        c.line(floor(cx).toInt(), floor(cy - 2f * K).toInt(), floor(cx).toInt(), floor(cy + 3f * K).toInt(), Pal.WOOD_D)
        for (k in 0 until 14 + 6 * detail) {
            val a = Noise.rnd(k, 121) * PI.toFloat() * 2f; val r = sqrt(Noise.rnd(k, 122))
            val lx = cx + cos(a) * r * 2.4f * K; val ly = cy + 1.4f * K + sin(a) * r * 2.0f * K
            c.fillRect(floor(lx).toInt(), floor(ly).toInt(), max(1, detail), max(1, detail), if (sin(a) < -0.2f) l else if (Noise.rnd(k, 123) < 0.4f) d else m)
        }
    }

    /** A wine barrel on its side on a trestle by the inn's door, a crate of bottles on the ground beside it. */
    private fun barrel() {
        val x = barrelX; val y = barrelY
        iso.box(x - 0.6f, y - 0.35f, 0f, 1.2f, 0.7f, 1.2f, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, 4.6f)
        val rx = 2.3f * K; val ry = 3.3f * K
        c.fillEllipse(cx, cy, rx * 1.25f, ry * 0.95f, Pal.WOOD_D)
        c.fillEllipse(cx - 0.3f * K, cy - 0.2f * K, rx * 1.1f, ry * 0.85f, Pal.WOOD_M)
        for (f in floatArrayOf(-0.55f, 0.55f)) c.vline(floor(cx + f * rx).toInt(), floor(cy - ry * 0.8f).toInt(), floor(cy + ry * 0.8f).toInt(), iron)
        c.fillCircle(cx + rx * 0.7f, cy, 0.5f * K, Pal.WOOD_X)
        iso.box(x + 0.7f, y - 0.2f, 0f, 0.9f, 0.6f, 2.2f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        for (k in 0 until 3) iso.px(x + 0.85f + k * 0.25f, y - 0.05f, 3.2f, Col.hex(0x2E4A2E))
    }

    // ------------------------------------------------------------------ the pergola, the table, the jug, the glass

    /** The pergola's back: the beams on the inn's front under the eave. */
    private fun pergolaBack() {
        for (px in floatArrayOf(pgx0, (pgx0 + pgx1) / 2f, pgx1)) iso.box(px - 0.08f, iy1, pgz - 0.4f, 0.16f, 0.2f, 0.5f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
    }

    /** The pergola: its two front posts, the beams back to the wall and the one across, the vine over them in its season. */
    private fun pergolaFront() {
        for (px in floatArrayOf(pgx0, pgx1)) iso.box(px - 0.11f, pgy - 0.11f, 0f, 0.22f, 0.22f, pgz, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        for (px in floatArrayOf(pgx0, (pgx0 + pgx1) / 2f, pgx1)) rod(px, iy1, pgz, px, pgy, pgz, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        rod(pgx0 - 0.2f, pgy, pgz, pgx1 + 0.2f, pgy, pgz, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        // the vine: its stem up the left post, its leaves over the beams
        rod(pgx0 + 0.04f, pgy + 0.05f, 0f, pgx0 + 0.04f, pgy + 0.05f, pgz, Col.hex(0x5A4430), Col.hex(0x7A5E40), Col.hex(0x5A4430), Col.hex(0x3E2E22))
        val season = env.season
        val (d, m, l) = when {
            season == Season.SPRING -> Triple(Col.hex(0x4E8A34), Col.hex(0x78B44E), Col.hex(0xA8D872))
            env.month == 10 -> Triple(Col.hex(0x8A3A1E), Col.hex(0xC0602A), Col.hex(0xE0A040))
            env.month == 11 -> Triple(Col.hex(0x6A4A1E), Col.hex(0x9A6E2A), Col.hex(0xBE9440))
            else -> Triple(Col.hex(0x2E5E26), Col.hex(0x4A8A36), Col.hex(0x74AE4E))
        }
        if (season == Season.WINTER) {
            for (k in 0 until 14) {
                val f = Noise.rnd(k, 131); val g = Noise.rnd(k, 132)
                val x = pgx0 + (pgx1 - pgx0) * f; val y = iy1 + (pgy - iy1) * g
                iso.line(x, y, pgz + 0.4f, x + 0.5f, y + 0.25f, pgz + 1.2f, Col.hex(0x6A5038))
            }
            if (winter) iso.line(pgx0, pgy, pgz + 0.4f, pgx1, pgy, pgz + 0.4f, Pal.SNOW_L)
            return
        }
        val n = 60 + 50 * (detail - 1)
        for (k in 0 until n) {
            val f = Noise.rnd(k, 133); val g = Noise.rnd(k, 134)
            val x = pgx0 - 0.3f + (pgx1 - pgx0 + 0.6f) * f; val y = iy1 + 0.1f + (pgy - iy1 + 0.1f) * g
            val z = pgz + 0.5f + Noise.rnd(k, 135) * 1.8f
            val cx = iso.sx(x, y); val cy = iso.sy(x, y, z)
            val r = (0.9f + Noise.rnd(k, 137) * 0.6f) * K * 0.6f
            c.fillEllipse(cx, cy, r * 1.3f, r, if (Noise.rnd(k, 138) < 0.3f) d else if (Noise.rnd(k, 139) < 0.6f) m else l)
        }
        // the bunches hanging under the leaves from August to October
        if (env.month in 8..10) for (k in 0 until 6) {
            val x = pgx0 + 0.4f + k * 0.55f; val y = pgy - 0.25f - (k % 2) * 1.1f
            val cx = iso.sx(x, y); val cy = iso.sy(x, y, pgz - 0.3f)
            for (j in 0 until 5) c.fillCircle(cx + (j % 2 - 0.5f) * 0.9f * K, cy + (j / 2) * 0.7f * K, 0.45f * K, if (j == 0) Col.hex(0x6A3A7A) else Col.hex(0x4A2458))
        }
    }

    private fun innTable() {
        iso.box(tbx0, tby0, tbz - 0.6f, tbx1 - tbx0, tby1 - tby0, 0.6f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        for ((lx, ly) in listOf(tbx0 + 0.15f to tby1 - 0.2f, tbx1 - 0.25f to tby1 - 0.2f, tbx1 - 0.25f to tby0 + 0.1f)) iso.box(lx, ly, 0f, 0.14f, 0.14f, tbz - 0.6f, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
        // the bench behind it along the inn's wall
        iso.box(tbx0 - 0.1f, iy1 + 0.1f, 3.6f, tbx1 - tbx0 + 0.2f, 0.5f, 0.5f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
    }

    /** How far the jug tips and the glass fills ([pour]); not set: upright, the glass half full. */
    private fun pour(): Float? = fx("pour")

    /** The jug of wine on the table: glazed earthenware, tipping toward the glass while it pours. */
    private fun jug() {
        val p = pour()
        val tip = if (p != null && p > 0.02f && p < 0.98f) 0.9f else 0f
        val jx = tbx0 + 0.55f; val jy = tby0 + 0.45f
        val cx = iso.sx(jx, jy) + tip * 1.2f * K; val base = iso.sy(jx, jy, tbz) - tip * 0.6f * K
        val hh = 4.6f * K
        lathe(cx, base, hh / detail, { hn -> (if (hn < 0.6f) 0.95f else if (hn < 3.2f) 1.25f - abs(hn - 1.9f) * 0.18f else 0.75f) * 0.5f * K + 0.5f }) { u, du, _, _, _ ->
            glaze(u, du, Col.hex(0xC8784A), Col.hex(0xA85A34), Col.hex(0x7A3E22), Col.hex(0xF0B890))
        }
        loop(cx + 1.2f * K, base - hh * 0.55f, 1.0f * K, 1.4f * K, max(1f, detail.toFloat()), true, Col.hex(0x7A3E22), Col.hex(0xC8784A))
        c.set(floor(cx - 1.1f * K).toInt(), floor(base - hh).toInt(), Col.hex(0xA85A34))
        if (tip > 0f) s.fx {
            val gx = iso.sx(tbx0 + 1.3f, tby0 + 0.5f); val gy = iso.sy(tbx0 + 1.3f, tby0 + 0.5f, tbz + 2.6f)
            c.line(floor(cx - 1.1f * K).toInt(), floor(base - hh).toInt(), floor(gx).toInt(), floor(gy).toInt(), wine)
            if (fine) c.line(floor(cx - 1.1f * K).toInt() + 1, floor(base - hh).toInt(), floor(gx).toInt() + 1, floor(gy).toInt(), wineL)
        }
    }

    /** The glass beside it: clear, the wine in it as full as [pour] has it. */
    private fun glass() {
        val full = pour() ?: 0.5f
        val gx = tbx0 + 1.3f; val gy = tby0 + 0.5f
        val cx = iso.sx(gx, gy); val base = iso.sy(gx, gy, tbz)
        val hh = 2.8f * K; val r = 0.75f * K
        for (yy in 0 until hh.toInt()) {
            val row = floor(base - yy).toInt()
            val inside = yy / hh < full * 0.9f
            for (xx in floor(cx - r).toInt()..floor(cx + r).toInt()) {
                val u = (xx + 0.5f - cx) / r
                if (abs(u) > 1f) continue
                c.set(xx, row, if (abs(u) > 0.7f) Col.hex(0xD8E4EC) else if (inside) (if (u < -0.2f) wineL else wine) else Col.hex(0xC4D4E0))
            }
        }
        c.hline(floor(cx - r).toInt(), floor(cx + r).toInt(), floor(base - hh).toInt(), Col.hex(0xF0F6FA))
        // a second glass, empty, at the table's far end
        val cx2 = iso.sx(tbx1 - 0.4f, tby0 + 0.4f); val b2 = iso.sy(tbx1 - 0.4f, tby0 + 0.4f, tbz)
        for (yy in 0 until hh.toInt()) for (xx in floor(cx2 - r).toInt()..floor(cx2 + r).toInt()) {
            val u = (xx + 0.5f - cx2) / r
            if (abs(u) <= 1f) c.set(xx, floor(b2 - yy).toInt(), if (abs(u) > 0.7f) Col.hex(0xD8E4EC) else Col.hex(0xC4D4E0))
        }
    }

    // ------------------------------------------------------------------ the court

    /** The court's boards: the back one and the far end's (behind the sand), or the front one and the near end's. */
    private fun boards(back: Boolean) {
        if (back) {
            iso.box(cx0 - bt, cy0 - bt, 0f, cx1 - cx0 + 2 * bt, bt, bh, boardL, boardM, boardD)
            iso.box(cx0 - bt, cy0, 0f, bt, cy1 - cy0, bh, boardL, boardM, boardD)
            return
        }
        iso.box(cx0 - bt, cy1, 0f, cx1 - cx0 + 2 * bt, bt, bh, boardL, boardM, boardD)
        iso.box(cx1, cy0, 0f, bt, cy1 - cy0, bh, boardL, boardM, boardD)
        // the planks' joints and the stakes holding them
        var x = cx0 + 1.5f
        while (x < cx1) { iso.line(x, cy1 + bt, 0f, x, cy1 + bt, bh, boardD); x += 1.9f }
        if (fine) iso.line(cx0 - bt, cy1 + bt, bh * 0.55f, cx1 + bt, cy1 + bt, bh * 0.55f, Col.mix(boardM, boardD, 0.4f))
        if (winter) {
            iso.line(cx0, cy1 + bt * 0.5f, bh + 0.3f, cx1 + bt, cy1 + bt * 0.5f, bh + 0.3f, Pal.SNOW_L)
            iso.line(cx1 + bt * 0.5f, cy0, bh + 0.3f, cx1 + bt * 0.5f, cy1 + bt, bh + 0.3f, Pal.SNOW_L)
        }
    }

    /** The sand: fine and pale, raked in lines across the court, worn down its middle where the balls roll; the ends trodden. */
    private fun sandBed() {
        onFlat(0.4f, cx0, cy0, cx1, cy1) { x, y, px, py ->
            val n = Noise.rnd(px / max(1, detail), py / max(1, detail), 161)
            var col = if (n < 0.18f) sandD else if (n < 0.8f) sandM else sandL
            val line = ((x - cx0) / 0.32f) % 1f
            val worn = abs(y - (cy0 + cy1) / 2f) < 0.45f && x < cx1 - 1.2f
            if (!worn && line < 0.2f) col = Col.mix(col, sandD, 0.6f)
            if (fine && !worn && line in 0.2f..0.32f) col = Col.mix(col, sandL, 0.5f)
            if ((x > cx1 - 1.1f || x < cx0 + 0.5f) && Noise.v2(x * 3f, y * 3f, 163) > 0.6f) col = Col.scale(col, 0.92f)
            if (winter && (min(y - cy0, cy1 - y) < 0.25f + Noise.v1(x * 2f, 165) * 0.2f)) col = Pal.SNOW_L
            col
        }
    }

    /** The lines scored across the sand at each end, where the throw starts. */
    private fun courtLines() {
        // chalk strips on the sand, as wide at every detail (a line of a pixel would peek out past the thrower differently)
        for (lx in floatArrayOf(cx1 - 1.2f, cx0 + 1.15f)) onFlat(0.42f, lx - 0.06f, cy0 + 0.05f, lx + 0.06f, cy1 - 0.05f) { x, _, px, py ->
            if (fine && x > lx + 0.03f) Col.mix(Col.hex(0xF6EEDC), sandD, 0.35f)
            else if (Noise.rnd(px / max(1, detail), py / max(1, detail), 167) < 0.12f) Col.hex(0xE4D8BE) else Col.hex(0xF6EEDC)
        }
    }

    /** The jack: a small wooden ball, pale, its glint. */
    private fun jack() {
        val cx = iso.sx(jackX, jackY); val cy = iso.sy(jackX, jackY, 0.4f)
        val r = 0.6f * K + 0.3f
        c.fillCircle(cx, cy - r, r, Col.hex(0xC8A060))
        c.fillCircle(cx - r * 0.3f, cy - r * 1.3f, r * 0.45f, Col.hex(0xF0D8A0))
    }

    /** A metal ball at ([x], [y]) (its foot at [z]), side [side] (0 smooth, 1 grooved), its glint. */
    private fun ball(x: Float, y: Float, z: Float, side: Int) {
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, z)
        val r = 1.05f * K + 0.3f
        c.fillCircle(cx, cy - r, r, steelD)
        c.fillCircle(cx - r * 0.12f, cy - r * 1.1f, r * 0.82f, steelM)
        c.fillCircle(cx - r * 0.35f, cy - r * 1.35f, r * 0.45f, steelL)
        c.set(floor(cx - r * 0.45f).toInt(), floor(cy - r * 1.5f).toInt(), steelHi)
        if (side == 1) {
            c.hline(floor(cx - r * 0.8f).toInt(), floor(cx + r * 0.8f).toInt(), floor(cy - r).toInt(), steelD)
            if (fine) c.hline(floor(cx - r * 0.7f).toInt(), floor(cx + r * 0.7f).toInt(), floor(cy - r * 0.5f).toInt(), steelD)
        }
    }

    /** Where the poked ball is: rolling a little away and back, the second time into the next one. */
    private fun pokedOffset(k: Int): Float {
        val pk = poked("ball") ?: return 0f
        if (k != 0 && !(k == 2 && pk.step == 1)) return 0f
        val a = pk.age(t)
        val dur = if (pk.step == 0) 1.6f else 2.2f
        if (a > dur) return 0f
        return sin(a / dur * PI.toFloat()) * (if (k == 0) 0.5f else 0.28f)
    }

    /** The balls round the jack and the two at the near end; the one hit flying off while `hit` plays, the thrown one where `throw` has it. */
    private fun allBalls() {
        val hit = fx("hit")
        for ((k, b) in balls.withIndex()) {
            var x = b[0]; var y = b[1]
            if (k == 0 && hit != null && hit > 0.5f) {
                // knocked toward the far end and the back board
                val u = ((hit - 0.5f) / 0.5f).coerceIn(0f, 1f)
                val e = 1f - (1f - u) * (1f - u)
                x = b[0] - 1.7f * e; y = b[1] - (b[1] - cy0 - 0.3f) * e
            }
            val off = pokedOffset(k)
            ball(x + off, y, 0.4f, b[2].toInt())
        }
        if (hit != null && hit >= 0.5f) ball(balls[0][0], balls[0][1], 0.4f, 1)
        val th = fx("throw")
        for ((k, b) in waiting.withIndex()) if (!(k == 0 && th != null && th > 0f) && !(k == 1 && hit != null && hit > 0f)) ball(b[0], b[1], 0.4f, b[2].toInt())
        if (th != null && th > 0.4f) {
            val u = ((th - 0.4f) / 0.6f).coerceIn(0f, 1f)
            val e = 1f - (1f - u) * (1f - u) * (1f - u)
            val sx = LAND_X; val sy = 7.1f; val ex = jackX + 0.75f; val ey = jackY + 0.05f
            ball(sx + (ex - sx) * e, sy + (ey - sy) * e, 0.4f, 0)
        }
    }

    /** A ball in the air: thrown low to land and roll (`throw`, up to 0.4), or high at the ball by the jack (`hit`, up to 0.5). */
    private fun throwing() {
        val th = fx("throw")
        if (th != null && th > 0f && th <= 0.4f) {
            val u = th / 0.4f
            val sx = throwPX - 0.3f; val sy = throwPY - 0.2f; val ex = LAND_X; val ey = 7.1f
            val z = 7f + (0.4f - 7f) * u + 5f * u * (1f - u)
            prop { ball(sx + (ex - sx) * u, sy + (ey - sy) * u, z, 0) }
            if (u > 0.85f) s.fx { PokeArt.puff(c, iso.sx(ex, ey), iso.sy(ex, ey, 0.5f), (u - 0.85f) / 0.15f, 2.5f * K, 4, sandL, 171, 0.6f) }
        }
        val hit = fx("hit")
        if (hit != null && hit > 0f && hit < 0.5f) {
            val u = hit / 0.5f
            val sx = waiting[1][0]; val sy = waiting[1][1]; val ex = balls[0][0]; val ey = balls[0][1]
            val z = 7f + (0.4f - 7f) * u + 26f * u * (1f - u)
            prop { ball(sx + (ex - sx) * u, sy + (ey - sy) * u, z, 1) }
        }
        if (hit != null && hit in 0.5f..0.75f) {
            val u = (hit - 0.5f) / 0.25f
            s.fx {
                val cx = iso.sx(balls[0][0], balls[0][1]); val cy = iso.sy(balls[0][0], balls[0][1], 0.6f)
                PokeArt.puff(c, cx, cy, u, 3.5f * K, 6, sandL, 173, 0.7f)
                PokeArt.rings(c, cx, cy - 2f * K, u, 7f * K, Col.hex(0xFFFFFF), max(1, detail), 0.6f, 2)
            }
        }
    }

    /** The rake leaning on the near end's board, outside it. */
    private fun rake() {
        val by = cx1 + 0.3f
        rod(by, rakeY + 0.5f, 0f, cx1 + 0.12f, rakeY, 9f, Pal.WOOD_L, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        rod(by + 0.05f, rakeY + 0.15f, 0.3f, by + 0.05f, rakeY + 0.85f, 0.3f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        var y = rakeY + 0.2f
        while (y <= rakeY + 0.82f) { iso.line(by + 0.05f, y, 0.3f, by + 0.05f, y, 0f, Pal.WOOD_D); y += 0.15f }
    }

    /** The folding rule: on the back board by the jack's end, or (`measure`) laid on the sand from the jack toward the nearest ball, unfolding. */
    private fun meter() {
        val m = fxOn("measure")
        val yel = Col.hex(0xF0D040); val yelD = Col.hex(0xB89A20)
        if (m < 0.05f) {
            val z = bh + 0.05f; val y = cy0 - bt * 0.5f
            iso.line(3.9f, y, z, 5.5f, y, z, yel)
            iso.line(3.9f, y + 0.06f, z, 5.5f, y + 0.06f, z, yelD)
            if (fine) { var x = 4.0f; while (x < 5.5f) { iso.line(x, y, z, x, y + 0.05f, z, Col.hex(0x2A2A2A)); x += 0.2f } }
            return
        }
        val ex = balls[0][0]; val ey = balls[0][1]
        val len = hypot(ex - jackX, ey - jackY)
        val dirX = (ex - jackX) / len; val dirY = (ey - jackY) / len
        val out = len * m.coerceIn(0f, 1f)
        val z = 0.45f
        iso.line(jackX, jackY, z, jackX + dirX * out, jackY + dirY * out, z, yel)
        iso.line(jackX + 0.04f, jackY + 0.04f, z, jackX + dirX * out + 0.04f, jackY + dirY * out + 0.04f, z, yelD)
        var s0 = 0.2f
        while (s0 < out) { iso.px(jackX + dirX * s0, jackY + dirY * s0, z, Col.hex(0x2A2A2A)); s0 += 0.3f }
    }

    // ------------------------------------------------------------------ the lantern, the scoreboard, the bench, the linden

    /** The lantern on its post between the court and the inn, lit for the evening games. */
    private fun lampPost() {
        iso.box(postX - 0.1f, postY - 0.1f, 0f, 0.2f, 0.2f, postZ - 1f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        val cx = iso.ix(postX, postY); val top = iso.iy(postX, postY, postZ + 3.2f); val bot = iso.iy(postX, postY, postZ - 0.8f)
        val d = detail
        val lit = lanternLit()
        c.fillRect(cx - 2 * d, top, 5 * d, d, iron)
        c.fillRect(cx - d, top - d, 3 * d, d, ironL)
        for (yy in top + d until bot) for (xx in cx - 2 * d until cx + 3 * d) {
            val edge = xx < cx - d || xx >= cx + 2 * d
            val col = when { edge -> iron; lit -> if ((yy - top) * 2 > (bot - top)) Pal.FLAME[1] else Pal.WINDOW_LIT; else -> if (xx < cx) Col.hex(0xB8C8D4) else Col.hex(0x8EA0AE) }
            if (lit && !edge) glow { c.set(xx, yy, col) } else c.set(xx, yy, col)
        }
        c.fillRect(cx - 2 * d, bot, 5 * d, d, iron)
    }

    /** How many chalk marks the scoreboard has under MI and under VI. */
    private fun marks(): Pair<Int, Int> {
        val l = fx("score") ?: return 11 to 7
        val n = frame.count ?: 9
        return (n.coerceIn(0, 15) * l.coerceIn(0f, 1f) + 0.01f).toInt() to 3
    }

    /** The scoreboard: a slate in a wooden frame on two posts behind the court, facing it, MI | VI and the tally marks. */
    private fun scoreboard() {
        for (x in floatArrayOf(sbX0 + 0.1f, sbX1 - 0.1f)) iso.box(x - 0.08f, sbY - 0.08f, 0f, 0.16f, 0.16f, 9.5f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        val z0 = 9f; val z1 = 16.5f
        val (mi, vi) = marks()
        onY(sbY + 0.1f, sbX0, sbX1, z0, z1) { x, z, px, py ->
            val e = min(min(x - sbX0, sbX1 - x) * 4f, min(z - z0, z1 - z))
            if (e < 0.5f) return@onY Pal.WOOD_M
            val u = (x - sbX0) / (sbX1 - sbX0); val v = (z - z0) / (z1 - z0)
            if (abs(u - 0.5f) < 0.025f || (abs(v - 0.72f) < 0.035f && u in 0.08f..0.92f)) return@onY chalk
            if (Noise.rnd(px, py, 141) < 0.04f) Col.mix(slate, chalk, 0.3f) else slate
        }
        tallies(mi, 0.07f, z0, z1)
        tallies(vi, 0.57f, z0, z1)
        // MI | VI over the marks, a nominal px a stroke
        for ((word, ax) in listOf("MI" to sbX0 + (sbX1 - sbX0) * 0.12f, "VI" to sbX0 + (sbX1 - sbX0) * 0.62f)) for ((k, ch) in word.withIndex()) {
            val rows = LETTERS[ch] ?: continue
            for ((r, line) in rows.withIndex()) for ((q, cc) in line.withIndex()) if (cc == 'X') iso.px(ax + (k * 4 + q) * 0.06f, sbY + 0.11f, z1 - 1.0f - r * 0.42f, chalk)
        }
    }

    /**
     * [n] tally marks on the slate under a heading starting at [left] (of its width): strokes of chalk in fives, the fifth
     * across them, a row of five under the other; a stroke a pixel wide, two at the closest, so each shows at every detail.
     */
    private fun tallies(n: Int, left: Float, z0: Float, z1: Float) {
        val y = sbY + 0.11f
        val wpx = if (detail >= 3) 2 else 1
        fun sx(u: Float) = iso.ix(sbX0 + u * (sbX1 - sbX0), y)
        fun sy(u: Float, v: Float) = iso.iy(sbX0 + u * (sbX1 - sbX0), y, z0 + v * (z1 - z0))
        for (k in 0 until n.coerceAtMost(15)) {
            val row = k / 5; val j = k % 5
            val top = 0.6f - row * 0.19f; val bottom = top - 0.14f
            if (j < 4) {
                val u = left + j * 0.075f
                for (o in 0 until wpx) c.line(sx(u) + o, sy(u, top), sx(u) + o, sy(u, bottom), chalk)
            } else {
                val ua = left - 0.02f; val ub = left + 0.25f
                for (o in 0 until wpx) c.line(sx(ua), sy(ua, bottom + 0.02f) + o, sx(ub), sy(ub, top - 0.02f) + o, chalk)
            }
        }
    }

    /** The spectators' bench in front of the court: a plank on two trestles; snow on it in winter. */
    private fun spectators() {
        iso.box(benchX0, benchY0, benchZ - 0.5f, benchX1 - benchX0, benchY1 - benchY0, 0.5f, if (winter) Pal.SNOW_L else Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        for (lx in floatArrayOf(benchX0 + 0.2f, benchX1 - 0.35f)) iso.box(lx, benchY0 + 0.05f, 0f, 0.14f, benchY1 - benchY0 - 0.1f, benchZ - 0.5f, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
    }

    /** The linden at the back on the left, over the court's far end: its trunk, a crown of clumps lit on the sun's side; in flower in June and July, bare in winter. */
    private fun linden() {
        val bx = iso.sx(treeX, treeY); val by = iso.sy(treeX, treeY, 0f)
        val trunkH = 10f * K; val tw = 1.4f * K
        for (yy in 0 until trunkH.toInt()) for (xx in floor(bx - tw).toInt()..floor(bx + tw).toInt()) {
            val u = (xx + 0.5f - bx) / tw
            c.set(xx, floor(by - yy).toInt(), if (u < -0.4f) Col.hex(0x7A604A) else if (u > 0.5f) Col.hex(0x3E3024) else Col.hex(0x5A4636))
        }
        val season = env.season
        val cx = bx; val cy = by - trunkH - 7f * K
        if (season == Season.WINTER) {
            for (b in 0 until 7) {
                val a = -PI.toFloat() / 2f + (b - 3f) * 0.38f
                val len = (8f + Noise.rnd(b, 151) * 4f) * K
                c.line(floor(bx).toInt(), floor(by - trunkH).toInt(), floor(bx + cos(a) * len).toInt(), floor(by - trunkH + sin(a) * len).toInt(), Col.hex(0x4A3A2C))
                c.set(floor(bx + cos(a) * len * 0.6f).toInt(), floor(by - trunkH + sin(a) * len * 0.6f).toInt() - 1, Pal.SNOW_L)
            }
            return
        }
        val (d, m, l) = when (season) {
            Season.AUTUMN -> Triple(Col.hex(0x8A5A16), Col.hex(0xC8922A), Col.hex(0xEEC456))
            Season.SPRING -> Triple(Col.hex(0x3E7A34), Col.hex(0x62A248), Col.hex(0x9ACC66))
            else -> Triple(Col.hex(0x2A5E30), Col.hex(0x3E7E3A), Col.hex(0x68A44C))
        }
        val bloom = env.month in 6..7
        for (k in 0 until 10) {
            val a = k * 2.3f
            val rr = (4.8f + Noise.rnd(k, 153) * 2.2f) * K
            val px = cx + sin(a) * 5.5f * K; val py = cy + cos(a) * 4f * K - (if (k > 7) 3.5f * K else 0f)
            for (yy in floor(py - rr).toInt()..floor(py + rr).toInt()) for (xx in floor(px - rr).toInt()..floor(px + rr).toInt()) {
                val dx = (xx + 0.5f - px) / rr; val dy = (yy + 0.5f - py) / (rr * 0.9f)
                val q = dx * dx + dy * dy
                if (q > 1f) continue
                if (q > 0.82f && Dither.at(xx, yy) < 0.4f) continue
                val lit = -dx * 0.5f - dy * 0.75f + (Noise.rnd(xx / detail, yy / detail, 155) - 0.5f) * 0.4f
                var col = if (lit > 0.4f) l else if (lit > -0.2f) m else d
                if (bloom && Noise.rnd(xx / detail, yy / detail, 157) < 0.06f) col = Col.hex(0xF0E89A)
                c.set(xx, yy, col)
            }
        }
    }

    // ------------------------------------------------------------------ the light

    private fun lights() {
        if (lanternLit()) {
            s.light(iso.sx(postX, postY), iso.sy(postX, postY, postZ), 110f, 1.05f * max(env.windows, if (fx("lantern") != null) 0.75f else 0f))
            s.fx {
                c.penEmissive = true
                c.ditherCircle(iso.sx(postX, postY), iso.sy(postX, postY, postZ + 1f), 5f * K, Pal.FLAME[1], 0.1f * max(env.windows, 0.6f))
                c.penEmissive = false
            }
        }
        if (env.windows > 0.3f) for (k in 0 until 2) {
            val mx = (wins[k * 2] + wins[k * 2 + 1]) / 2f
            s.light(iso.sx(mx, iy1 + 1f), iso.sy(mx, iy1 + 1f, 8f), 40f, 0.45f * env.windows)
        }
    }

    companion object {
        /** Where a thrown ball comes down on the sand, to roll on to the jack. */
        private const val LAND_X = 7.6f

        /** MI and VI on the scoreboard, three columns of five rows. */
        private val LETTERS = mapOf(
            'M' to listOf("X.X", "XXX", "X.X", "X.X", "X.X"),
            'I' to listOf(".X.", ".X.", ".X.", ".X.", ".X."),
            'V' to listOf("X.X", "X.X", "X.X", "X.X", ".X."),
        )
    }
}
