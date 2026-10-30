package si.lanisce.lani.game.render

import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.render.scene.FireStyle
import si.lanisce.lani.game.render.scene.RoofKind
import si.lanisce.lani.game.render.scene.Tower
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The buildings of a culture's village as it builds them (companion/GAME.md "The map in a culture's look"; [MapLook]): what
 * [BuildingPainter] draws instead of its own sprite where the village's look has its own, on the same plot and in the
 * same pixel world (the same projection, the same palette and light, the same closer looks).
 *
 * - From the village's [FireStyle] (the scenes' village round the fire): the houses (Friuli's plastered walls under coppi,
 *   green shutters, stone window frames and the fogolâr's chimney under its little roof; Carinthia's white walls under a
 *   timbered upper storey, the balcony with its geraniums, the shingle roof with its half-hipped gable; the Lake District's
 *   stone or whitewashed walls under slate, white window frames, a slate porch and the round chimneys at the gable ends),
 *   the church (the Friulian free-standing campanile, Carinthia's onion dome, the Lake District's battlemented tower), the
 *   huts (of stone, Friuli's coast's casoni of reed), the village's oak (the Lake District's), the smithy's roof.
 * - From the look's own entries: the hayrack (the fienile, the Harpfe, the stone hay barn), the well (the vera under its
 *   iron arch, the log trough, the village pump), the hives (plain boxes, the WBC hives), the smithy's cobbles, the school.
 *
 * Every building keeps what the map's own has: its plot, its height (within [spriteHeight]), its lit windows by night and
 * dark ones while everyone sleeps, its hearth's smoke, the forge's glow.
 */
internal class CultureBuildings(private val s: SceneCtx, private val p: BuildingPainter) {
    private val i get() = s.iso
    private val c get() = s.canvas
    private val env get() = s.env
    private val k get() = s.k
    private val fine get() = s.k >= 2
    private val finest get() = s.k >= 3

    /** Draws [b] as [look]'s village builds it; false where the look has no version of its own (the map's own sprite). */
    fun draw(b: Building, x: Float, y: Float, look: MapLook): Boolean {
        val st = look.style ?: return false
        when (b.type) {
            BuildingType.HOUSE -> when {
                st.stone -> stoneHouse(b, x, y, st)
                st.timber -> timberHouse(b, x, y, st)
                else -> plasterHouse(b, x, y, st)
            }
            BuildingType.CHURCH -> when (st.tower) {
                Tower.CAMPANILE -> campanileChurch(b, x, y, st)
                Tower.ONION -> onionChurch(b, x, y, st)
                Tower.BATTLEMENTS -> towerChurch(b, x, y, st)
                Tower.SPIRE -> return false
            }
            BuildingType.HUT -> when {
                st.thatch -> casone(b, x, y)
                st.hutStone -> stoneHut(b, x, y, st)
                else -> return false
            }
            BuildingType.KOZOLEC -> when (look.hayrack) {
                Hayrack.FIENILE -> fienile(b, x, y, st)
                Hayrack.HARPFE -> harpfe(b, x, y)
                Hayrack.BARN -> hayBarn(b, x, y, st)
                Hayrack.KOZOLEC -> return false
            }
            BuildingType.WELL -> when (look.well) {
                Well.VERA -> vera(b, x, y)
                Well.TROUGH -> trough(b, x, y)
                Well.PUMP -> pump(b, x, y)
                Well.ROOFED -> return false
            }
            BuildingType.BEEHIVE -> when (look.hives) {
                Hives.PLAIN -> beeHouse(b, x, y, st)
                Hives.WBC -> wbcHives(b, x, y)
                Hives.PAINTED -> return false
            }
            BuildingType.SMITHY -> if (look.cobbles || st.roofKind != RoofKind.SLATE) smithy(b, x, y, st, look.cobbles) else return false
            BuildingType.LIPA -> if (st.oak) oak(b, x, y) else return false
            BuildingType.SCHOOL -> when (look.school) {
                SchoolKind.ITALIAN -> school(b, x, y, st, italian = true)
                SchoolKind.STONE -> school(b, x, y, st, italian = false)
                SchoolKind.OWN -> return false
            }
            else -> return false
        }
        return true
    }

    // ---------------------------------------------------------------- helpers

    private inline fun glow(block: () -> Unit) { val was = c.penEmissive; c.penEmissive = true; block(); c.penEmissive = was }

    /** The village's roof ([FireStyle.roofKind]) in its colours; snow on it in winter. */
    internal fun roofOf(st: FireStyle): BuildingPainter.Roof = when (st.roofKind) {
        RoofKind.COPPI -> if (env.snow) BuildingPainter.Roof(Pal.SNOW_L, Pal.SNOW_M, st.roof[2], rows = false)
        else BuildingPainter.Roof(st.roof[0], st.roof[1], Col.scale(st.roof[2], 0.8f), channels = true)
        RoofKind.SHINGLES -> p.shingles()
        RoofKind.SLATE -> p.slate()
        RoofKind.THATCH -> thatch()
        RoofKind.TILES -> p.tiles()
    }

    /** Reed thatch (the casoni's): rows = false, the reeds' streaks drawn by [reeds]. */
    internal fun thatch() = if (env.snow) BuildingPainter.Roof(Pal.SNOW_L, Pal.SNOW_M, THATCH_D, rows = false) else BuildingPainter.Roof(THATCH_L, THATCH_M, THATCH_D, rows = false)

    /** The walls' tones of the building on [plot]: the village's houses in turn (Friuli's ochre, terracotta and cream). */
    private fun wallsOf(st: FireStyle, plot: Int): IntArray = st.walls[Math.floorMod(plot * 5 + 1, st.walls.size)]

    /**
     * A window on the +y face: [shutter]s either side (none: null), its [frame] (stone or white paint) as a lintel and,
     * at a closer look, all round with a mullion; lit at night, dark when everyone in it sleeps. Its warm [light] on the
     * ground below it only for the ground floor's (the frame's lights are few: the upper windows glow without one).
     */
    internal fun winY(x: Float, y: Float, z: Float, w: Float, h: Float, shutter: Int?, frame: Int?, light: Boolean = true) {
        if (shutter != null) { i.panelY(x - 0.25f, y, z, 0.25f, h, shutter); i.panelY(x + w, y, z, 0.25f, h, shutter) }
        if (fine) i.panelY(x - FRAME, y, z - 1f / k, w + 2 * FRAME, h + 2f / k, frame ?: Pal.WOOD_X)
        if (p.lit) {
            glow { i.panelY(x, y, z, w, h, Pal.WINDOW_LIT); i.px(x, y, z + h - 1, Pal.WINDOW_LIT_HI) }
            if (light) s.light(i.sx(x + w / 2, y), i.sy(x + w / 2, y, z + h / 2) + 3 * k, 11f, 0.55f * env.windows)
        } else {
            i.panelY(x, y, z, w, h, Pal.GLASS); i.px(x, y, z + h - 1, Pal.GLASS_HI)
            if (finest) { i.panelY(x + 0.02f, y, z, w * 0.2f, h, Pal.CANVAS_L); i.panelY(x + w * 0.78f, y, z, w * 0.2f, h, Pal.CANVAS_L) }
        }
        if (frame != null) i.line(x - FRAME, y, z + h, x + w + FRAME, y, z + h, frame)
        if (fine) {
            val m = frame ?: Pal.WOOD_X
            i.line(x + w / 2, y, z, x + w / 2, y, z + h, m)
            if (finest) i.line(x, y, z + h / 2, x + w, y, z + h / 2, m)
        }
    }

    /** A window on the +x face (see [winY]). */
    internal fun winX(x: Float, y: Float, z: Float, d: Float, h: Float, shutter: Int?, frame: Int?, light: Boolean = true) {
        if (shutter != null) { i.panelX(x, y - 0.25f, z, 0.25f, h, Col.scale(shutter, 0.85f)); i.panelX(x, y + d, z, 0.25f, h, Col.scale(shutter, 0.85f)) }
        if (fine) i.panelX(x, y - FRAME, z - 1f / k, d + 2 * FRAME, h + 2f / k, frame?.let { Col.scale(it, 0.88f) } ?: Pal.WOOD_X)
        if (p.lit) {
            glow { i.panelX(x, y, z, d, h, Pal.WINDOW_LIT); i.px(x, y, z + h - 1, Pal.WINDOW_LIT_HI) }
            if (light) s.light(i.sx(x, y + d / 2), i.sy(x, y + d / 2, z + h / 2) + 3 * k, 11f, 0.55f * env.windows)
        } else {
            i.panelX(x, y, z, d, h, Col.scale(Pal.GLASS, 0.85f)); i.px(x, y + d - 0.1f, z + h - 1, Pal.GLASS_HI)
            if (finest) { i.panelX(x, y + 0.02f, z, d * 0.2f, h, Pal.CANVAS_M); i.panelX(x, y + d * 0.78f, z, d * 0.2f, h, Pal.CANVAS_M) }
        }
        if (frame != null) i.line(x, y - FRAME, z + h, x, y + d + FRAME, z + h, Col.scale(frame, 0.88f))
        if (fine) {
            val m = frame?.let { Col.scale(it, 0.88f) } ?: Pal.WOOD_X
            i.line(x, y + d / 2, z, x, y + d / 2, z + h, m)
            if (finest) i.line(x, y, z + h / 2, x, y + d, z + h / 2, m)
        }
    }

    /** Stone walls: their stones speckled and, closer up, their mortar; quoins of lighter stone at the corner we see. */
    internal fun stoneBox(x: Float, y: Float, z: Float, w: Float, d: Float, h: Float, t: IntArray, seed: Int, quoins: Boolean = true) {
        i.box(x, y, z, w, d, h, t[0], t[1], t[2])
        p.speckle(x, y, z, w, d, h, seed, Col.scale(t[1], 0.82f), Col.scale(t[2], 0.82f))
        p.mortar(x, y, z, w, d, h, Col.scale(t[1], 0.86f), Col.scale(t[2], 0.86f))
        if (quoins && fine) {
            var zz = z + 0.5f; var n = 0
            while (zz < z + h - 0.5f) {
                val long = n % 2 == 0
                i.line(x + w - (if (long) 0.35f else 0.2f), y + d, zz, x + w, y + d, zz, Col.mix(t[0], Col.hex(0xFFFFFF), 0.35f))
                i.line(x + w, y + d - (if (long) 0.2f else 0.35f), zz, x + w, y + d, zz, Col.mix(t[1], Col.hex(0xFFFFFF), 0.25f))
                zz += 1f; n++
            }
        }
    }

    /**
     * Walls of river cobbles with bands of brick every few courses (Friuli's smithy, fire station, the fienile's): pale
     * rounded stones speckled over the faces, a band of brick across both every 3.5 px.
     */
    internal fun cobbleBox(x: Float, y: Float, z: Float, w: Float, d: Float, h: Float, seed: Int) {
        i.box(x, y, z, w, d, h, COB[0], COB[1], COB[2])
        val n = ((w + d) * h / (if (fine) 1.6f else 4f)).toInt()
        for (j in 0 until n) {
            val u = Noise.rnd(seed, j, 37); val v = Noise.rnd(seed, j, 38)
            val col = if (j % 3 == 0) COB_D else Col.mix(COB[0], Col.hex(0xFFFFFF), 0.3f)
            if (j % 2 == 0) i.px(x + u * w, y + d, z + 0.5f + v * (h - 1f), col) else i.px(x + w, y + u * d, z + 0.5f + v * (h - 1f), Col.scale(col, 0.85f))
        }
        var zz = z + 2.8f
        while (zz < z + h - 0.5f) { i.line(x, y + d, zz, x + w, y + d, zz, BRICK_L); i.line(x + w, y, zz, x + w, y + d, zz, BRICK_D); zz += 3.5f }
    }

    /** Plastered walls; at a closer look the plaster's patches and a shadow under the eaves at [eave]. */
    internal fun plaster(x: Float, y: Float, z: Float, w: Float, d: Float, h: Float, t: IntArray, seed: Int) {
        i.box(x, y, z, w, d, h, t[0], t[1], t[2])
        if (fine) {
            for (j in 0 until ((w + d) * h / 4).toInt()) {
                val u = Noise.rnd(seed, j, 21); val v = Noise.rnd(seed, j, 22)
                if (j % 2 == 0) i.px(x + u * w, y + d, z + 0.5f + v * (h - 1f), Col.mix(t[1], t[2], 0.25f))
                else i.px(x + w, y + u * d, z + 0.5f + v * (h - 1f), Col.mix(t[2], t[1], 0.2f))
            }
            i.line(x, y + d, z + h - 0.5f, x + w, y + d, z + h - 0.5f, Col.scale(t[1], 0.86f))
            i.line(x + w, y, z + h - 0.5f, x + w, y + d, z + h - 0.5f, Col.scale(t[2], 0.86f))
        }
    }

    /** A chimney of [t]'s walls from [z], [h] high, under a little roof of the village's [r] (Friuli's), smoking while it may. */
    private fun cappedChimney(x: Float, y: Float, z: Float, h: Float, t: IntArray, r: BuildingPainter.Roof, smoke: Boolean) {
        i.box(x, y, z, 0.5f, 0.5f, h, t[0], t[1], t[2])
        // the cap: four little posts and a gable of coppi over the flue
        i.panelY(x + 0.1f, y + 0.5f, z + h, 0.3f, 1f, Col.hex(0x2A2220)); i.panelX(x + 0.5f, y + 0.1f, z + h, 0.3f, 1f, Col.hex(0x241C1A))
        i.gableX(x - 0.1f, y - 0.1f, z + h + 1f, 0.7f, 0.7f, 1.4f, r.back, r.front, t[2], r.edge)
        if (smoke) s.plume(i.sx(x + 0.25f, y + 0.25f), i.sy(x + 0.25f, y + 0.25f, z + h + 1.5f), x.toInt() * 31 + y.toInt())
    }

    /** A round chimney (the Lake District's, at the gable ends): a stone drum, lit on the left, a slate slab and pot on top. */
    private fun roundChimney(x: Float, y: Float, z: Float, h: Float, t: IntArray, smoke: Boolean) {
        val cx = i.ix(x, y); val cy = i.iy(x, y, z)
        val r = 1 // nominal half width beside its middle column
        s.sprite(cx, cy) {
            for (yy in cy - h.toInt() until cy) {
                c.set(cx - r, yy, t[0]); c.set(cx, yy, t[1]); c.set(cx + r, yy, t[2])
            }
            // the slab and the pot
            c.hline(cx - r - 1, cx + r, cy - h.toInt() - 1, Pal.SLATE_D)
            c.set(cx, cy - h.toInt() - 2, Col.hex(0xA0583A)); c.set(cx - 1, cy - h.toInt() - 2, Col.hex(0xC07050))
        }
        if (smoke) s.plume(cx.toFloat(), cy - (h + 2f) * k, x.toInt() * 37 + y.toInt())
    }

    // ---------------------------------------------------------------- houses

    /**
     * Friuli's house (the Collio's, the fire's village's): a low stone plinth, plastered walls in the village's ochre,
     * terracotta or cream, green shutters (blue by the sea), the windows in stone frames, the door under its stone arch with
     * the vine's pergola over it in the warm months, a low roof of coppi, an attic window in the gable, and the fogolâr's
     * chimney under its little roof.
     */
    private fun plasterHouse(b: Building, x: Float, y: Float, st: FireStyle) {
        val x0 = x + 0.25f; val y0 = y + 0.25f; val w = 2.5f
        val t = wallsOf(st, b.plot)
        val frame = ISTRIA_L
        i.box(x0, y0, 0f, w, w, 1.5f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        p.mortar(x0, y0, 0f, w, w, 1.5f, Pal.STONE_D, Pal.STONE_X)
        plaster(x0, y0, 1.5f, w, w, 10.5f, t, b.plot * 17)
        // the gable end (+y): two windows on each floor
        for (u in floatArrayOf(0.5f, 1.6f)) {
            winY(x0 + u, y0 + w, 3.5f, 0.45f, 2.8f, st.shutter, frame)
            winY(x0 + u, y0 + w, 8f, 0.45f, 2.6f, st.shutter, frame, light = false)
        }
        // the long side (+x): a window, the door under its arch, a window; above them three
        winX(x0 + w, y0 + 0.25f, 3.5f, 0.42f, 2.8f, st.shutter, frame)
        winX(x0 + w, y0 + 1.95f, 3.5f, 0.3f, 2.8f, st.shutter, frame)
        for (u in floatArrayOf(0.28f, 1.08f, 1.88f)) winX(x0 + w, y0 + u, 8f, 0.34f, 2.6f, st.shutter, frame, light = false)
        i.panelX(x0 + w, y0 + 0.95f, 0f, 0.75f, 6.6f, frame) // the stone portal
        i.panelX(x0 + w, y0 + 1.03f, 0f, 0.6f, 6f, Pal.DOOR)
        if (fine) {
            i.line(x0 + w, y0 + 1.33f, 0f, x0 + w, y0 + 1.33f, 6f, Col.scale(Pal.DOOR, 0.75f))
            c.block(i.ix(x0 + w, y0 + 1.25f), i.iy(x0 + w, y0 + 1.25f, 3f), k - 1, Pal.GOLD)
        }
        // the pergola over the door: a beam and the vine on it, its grapes in late summer (a closer look)
        if (fine && !env.snow && env.month in 4..10) {
            i.line(x0 + w + 0.5f, y0 + 0.75f, 7.3f, x0 + w + 0.5f, y0 + 1.95f, 7.3f, Pal.WOOD_D)
            for (j in 0..7) {
                val v = 0.75f + j * 0.16f
                i.px(x0 + w + 0.35f + (j % 2) * 0.1f, y0 + v, 7.6f + (j % 3) * 0.4f, if (j % 3 == 0) Col.hex(0x5DA03E) else Pal.LEAF)
                if (env.month in 8..9 && j % 3 == 1) i.px(x0 + w + 0.4f, y0 + v, 6.8f, GRAPE)
            }
        }
        p.roofY(x0 - 0.35f, y0 - 0.35f, 12f, w + 0.7f, w + 0.95f, 5.5f, roofOf(st), t[1])
        // the eaves' shadow on the walls, the attic's little window in the gable
        i.line(x0, y0 + w, 11.6f, x0 + w, y0 + w, 11.6f, Col.scale(t[1], 0.72f))
        i.line(x0 + w, y0, 11.6f, x0 + w, y0 + w, 11.6f, Col.scale(t[2], 0.72f))
        val gy = y0 + w + 0.6f
        if (p.lit) glow { i.px(x0 + w / 2 - 0.1f, gy, 14f, Pal.WINDOW_LIT) } else i.px(x0 + w / 2 - 0.1f, gy, 14f, Pal.GLASS)
        if (fine) i.line(x0 + w / 2 - 0.3f, gy, 13.4f, x0 + w / 2 + 0.1f, gy, 13.4f, frame)
        cappedChimney(x0 + 1.75f, y0 + 0.7f, 14f, 4.5f, t, roofOf(st), p.smoking)
    }

    /**
     * Carinthia's farmhouse: white walls on a stone plinth, the upper storey of dark timber, green shutters, the wooden
     * balcony across the gable with geraniums along its rail in summer, and the shingle roof with its half-hipped gable
     * (the Krüppelwalm), a white chimney.
     */
    private fun timberHouse(b: Building, x: Float, y: Float, st: FireStyle) {
        val x0 = x + 0.25f; val y0 = y + 0.25f; val w = 2.5f
        val t = wallsOf(st, b.plot)
        i.box(x0, y0, 0f, w, w, 3f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        p.speckle(x0, y0, 0f, w, w, 3f, b.plot * 17, Pal.STONE_D, Pal.STONE_X)
        p.mortar(x0, y0, 0f, w, w, 3f, Pal.STONE_D, Pal.STONE_X)
        plaster(x0, y0, 3f, w, w, 4.5f, t, b.plot * 13)
        // the timbered upper storey: boards, darker where they meet
        i.box(x0, y0, 7.5f, w, w, 4.5f, TIMBER[0], TIMBER[1], TIMBER[2])
        var u = 0.25f
        while (u < w) {
            if (fine || (u * 4).roundToInt() % 2 == 0) { i.line(x0 + u, y0 + w, 7.5f, x0 + u, y0 + w, 12f, Col.scale(TIMBER[1], 0.78f)); i.line(x0 + w, y0 + u, 7.5f, x0 + w, y0 + u, 12f, Col.scale(TIMBER[2], 0.78f)) }
            u += if (fine) 0.25f else 0.5f
        }
        i.line(x0, y0 + w, 7.5f, x0 + w, y0 + w, 7.5f, TIMBER[2])
        // windows below with their shutters, the door on the long side, windows in the timber above
        winY(x0 + 0.5f, y0 + w, 4f, 0.5f, 2.6f, st.shutter, null)
        winY(x0 + 1.6f, y0 + w, 4f, 0.5f, 2.6f, st.shutter, null)
        winX(x0 + w, y0 + 0.35f, 4f, 0.5f, 2.6f, st.shutter, null)
        p.doorX(x0 + w, y0 + 1.15f, 0f, 0.6f, 6.5f, handleU = 0.1f, handleZ = 3.5f)
        winX(x0 + w, y0 + 2.0f, 4f, 0.35f, 2.6f, null, null)
        winX(x0 + w, y0 + 0.5f, 8.5f, 0.45f, 2.2f, st.shutter, null, light = false)
        winX(x0 + w, y0 + 1.55f, 8.5f, 0.45f, 2.2f, st.shutter, null, light = false)
        // the balcony across the gable end, the door onto it, geraniums along the rail from May to October
        i.panelY(x0 + 0.95f, y0 + w, 8f, 0.55f, 3.5f, Pal.DOOR)
        i.box(x0 - 0.15f, y0 + w, 7.8f, w + 0.3f, 0.55f, 0.8f, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
        i.panelY(x0 - 0.15f, y0 + w + 0.55f, 8.6f, w + 0.3f, 2.4f, Pal.WOOD_D)
        var j = 0f
        while (j <= w + 0.3f) { i.post(x0 - 0.15f + j, y0 + w + 0.55f, 8.6f, 11f, Pal.WOOD_M); j += 0.42f }
        i.line(x0 - 0.15f, y0 + w + 0.55f, 11.2f, x0 + w + 0.15f, y0 + w + 0.55f, 11.2f, Pal.WOOD_L)
        if (env.season != Season.WINTER && env.month in 5..10) {
            var fx = x0; var n = 0
            while (fx < x0 + w + 0.1f) {
                i.px(fx, y0 + w + 0.55f, 12f, if (n % 3 == 2) Pal.LEAF else if (n % 2 == 0) Pal.GERANIUM else Pal.GERANIUM_D)
                fx += 0.22f; n++
            }
        }
        krueppelwalm(x0 - 0.45f, y0 - 0.45f, 12f, w + 0.9f, w + 1.2f, 8.5f, p.shingles(), TIMBER[1])
        i.box(x0 + 1.75f, y0 + 0.75f, 15.5f, 0.45f, 0.45f, 4.5f, t[0], t[1], t[2])
        i.top(x0 + 1.75f, y0 + 0.75f, 20f, 0.45f, 0.45f, Pal.STONE_X)
        if (p.smoking) s.plume(i.sx(x0 + 1.97f, y0 + 0.97f), i.sy(x0 + 1.97f, y0 + 0.97f, 20f), b.plot * 29 + 3)
    }

    /**
     * A gable roof with its ridge along y and the half-hipped gable at its +y end (the Krüppelwalm of Carinthia's houses):
     * the gable's tip under a little hip sloping back, the gable below it boarded ([gable]).
     */
    private fun krueppelwalm(x: Float, y: Float, z: Float, w: Float, d: Float, rise: Float, r: BuildingPainter.Roof, gable: Int) {
        val xm = x + w / 2; val zr = z + rise
        val f = 0.62f // where the hip begins, up the gable
        val zh = z + rise * f
        val xl = x + f * w / 2; val xr = x + w - f * w / 2
        val e = 0.55f // how far back the ridge ends
        fun poly(vararg pts: Float, col: Int) {
            c.polyBegin()
            var n = 0
            while (n < pts.size) { c.polyAdd(i.sx(pts[n], pts[n + 1]), i.sy(pts[n], pts[n + 1], pts[n + 2])); n += 3 }
            c.polyFill(col)
        }
        poly(x, y, z, x, y + d, z, xl, y + d, zh, xm, y + d - e, zr, xm, y, zr, col = r.back)
        poly(x, y + d, z, x + w, y + d, z, xr, y + d, zh, xl, y + d, zh, col = gable)
        poly(xm, y, zr, xm, y + d - e, zr, xr, y + d, zh, x + w, y + d, z, x + w, y, z, col = r.front)
        i.tri(xl, y + d, zh, xr, y + d, zh, xm, y + d - e, zr, Col.mix(r.back, r.front, 0.35f))
        if (fine && r.rows) {
            val n = (rise * k / 3f).roundToInt().coerceAtLeast(2)
            for (q in 1 until n) {
                val tt = q / n.toFloat(); val zz = z + tt * rise
                val end = if (zz > zh) y + d - e * (zz - zh) / (zr - zh) else y + d
                i.line(x + tt * w / 2, y, zz, x + tt * w / 2, end, zz, Col.scale(r.back, 0.86f))
                i.line(x + w - tt * w / 2, y, zz, x + w - tt * w / 2, end, zz, Col.scale(r.front, 0.84f))
            }
        }
        i.line(x, y + d, z, xl, y + d, zh, r.edge); i.line(xl, y + d, zh, xr, y + d, zh, r.edge)
        i.line(x + w, y, z, x + w, y + d, z, r.edge)
        // the gable's boards below the hip
        if (fine) { var u = x + 0.3f; while (u < x + w) { val top = z + (rise * f) * (1f - abs(u - xm) / (w / 2)).coerceAtMost(1f) * 1f; if (top > z + 0.4f) i.line(u, y + d, z, u, y + d, min(top, zh), Col.scale(gable, 0.8f)); u += 0.3f } }
    }

    /**
     * The Lake District's house: walls of grey stone (whitewashed for one in three), white window frames and no shutters,
     * a porch of stone under slate before the door, the slate roof, and the round chimneys on both gable ends.
     */
    private fun stoneHouse(b: Building, x: Float, y: Float, st: FireStyle) {
        val x0 = x + 0.25f; val y0 = y + 0.25f; val w = 2.5f
        val t = wallsOf(st, b.plot)
        val whitewashed = Col.lum(t[0]) > 0.85f
        if (whitewashed) plaster(x0, y0, 0f, w, w, 11f, t, b.plot * 7) else stoneBox(x0, y0, 0f, w, w, 11f, t, b.plot * 17)
        val frame = if (whitewashed) Col.hex(0x3A4048) else WHITE_FRAME
        for (u in floatArrayOf(0.45f, 1.6f)) {
            winY(x0 + u, y0 + w, 3f, 0.5f, 2.6f, null, frame)
            winY(x0 + u, y0 + w, 7.4f, 0.5f, 2.4f, null, frame, light = false)
        }
        winX(x0 + w, y0 + 0.3f, 3f, 0.45f, 2.6f, null, frame)
        winX(x0 + w, y0 + 1.95f, 3f, 0.4f, 2.6f, null, frame)
        for (u in floatArrayOf(0.3f, 1.12f, 1.95f)) winX(x0 + w, y0 + u, 7.4f, 0.4f, 2.4f, null, frame, light = false)
        // the porch before the door: stone walls, a slate gable over them, the door inside
        val px0 = x0 + w; val py0 = y0 + 0.95f
        stoneBox(px0, py0, 0f, 0.55f, 0.8f, 4.5f, t, b.plot * 23, quoins = false)
        i.panelX(px0 + 0.55f, py0 + 0.2f, 0f, 0.4f, 3.4f, if (p.lit) Col.hex(0x3A2A1E) else Col.hex(0x1E1A18))
        if (fine) i.line(px0 + 0.55f, py0 + 0.2f, 3.4f, px0 + 0.55f, py0 + 0.6f, 3.4f, WHITE_FRAME)
        p.roofX(px0 - 0.05f, py0 - 0.12f, 4.5f, 0.75f, 1.04f, 2.4f, p.slate(), t[2])
        p.roofY(x0 - 0.3f, y0 - 0.3f, 11f, w + 0.6f, w + 0.75f, 7f, p.slate(), t[1])
        val gy = y0 + w + 0.45f
        if (p.lit) glow { i.px(x0 + w / 2 - 0.1f, gy, 13.5f, Pal.WINDOW_LIT) } else i.px(x0 + w / 2 - 0.1f, gy, 13.5f, Pal.GLASS)
        // the round chimneys on the ridge at both gable ends, the kitchen's smoking
        roundChimney(x0 + w / 2, y0 + 0.15f, 17.4f, 3.6f, t, false)
        roundChimney(x0 + w / 2, y0 + w - 0.15f, 17.4f, 3.6f, t, p.smoking)
    }

    // ---------------------------------------------------------------- churches

    /**
     * Friuli's church: the nave with its gabled front toward the square (a portal under a stone frame, the round window
     * over it), a low coppi roof, and beside it, free-standing, the campanile of brick with its belfry's arches, its bell and
     * a low pyramid cap.
     */
    private fun campanileChurch(b: Building, x: Float, y: Float, st: FireStyle) {
        val tx = x + 2.16f; val ty = y + 0.14f; val tw = 0.78f
        campanile(tx, ty, tw, 28f, st.towerWall, st.towerTop, b.plot)
        val nx = x + 0.35f; val ny = y + 1.0f; val nw = 1.9f; val nd = 1.95f
        val t = st.churchWall
        plaster(nx, ny, 0f, nw, nd, 11f, t, b.plot * 41)
        // the side's tall arched windows
        winX(nx + nw, ny + 0.35f, 4.5f, 0.32f, 4.5f, null, ISTRIA_L)
        winX(nx + nw, ny + 1.25f, 4.5f, 0.32f, 4.5f, null, ISTRIA_L)
        p.roofY(nx - 0.15f, ny - 0.1f, 11f, nw + 0.3f, nd + 0.22f, 5f, roofOf(st), t[1])
        // the front: the portal and its frame, the rose window over it
        val fy = ny + nd
        i.panelY(nx + 0.6f, fy, 0f, 0.7f, 6.2f, ISTRIA_L)
        p.doorY(nx + 0.7f, fy, 0f, 0.5f, 5.4f)
        i.px(nx + 0.95f, fy, 5.6f, Pal.DOOR)
        val rose = if (p.lit) Pal.WINDOW_LIT else Pal.GLASS
        val rx = nx + 0.95f; val ry = fy + 0.15f
        glowIf(p.lit) { i.px(rx, ry, 12.5f, rose); if (fine) { i.px(rx - 0.15f, ry, 12.5f, rose); i.px(rx + 0.12f, ry, 12.5f, rose) } }
        if (fine) { i.line(rx - 0.3f, ry, 11.6f, rx + 0.3f, ry, 11.6f, ISTRIA_L); i.line(rx - 0.3f, ry, 13.6f, rx + 0.3f, ry, 13.6f, ISTRIA_L) }
        // the cross on the front's peak
        val ax = i.ix(nx + nw / 2, fy + 0.1f); val ay = i.iy(nx + nw / 2, fy + 0.1f, 16f)
        s.sprite(ax, ay) { c.vline(ax, ay - 3, ay, ISTRIA_L); c.set(ax - 1, ay - 2, ISTRIA_L); c.set(ax + 1, ay - 2, ISTRIA_L) }
        if (b.level >= 2 && !env.snow) p.flowers(x + 0.3f, y + 2.8f, b.plot)
    }

    /**
     * A Friulian campanile on [tx], [ty] ([tw] square, [h] up to its cornice): courses of brick, a stone band under the
     * belfry, the belfry's arched openings on both sides we see, its bell, a cornice and the low pyramid cap with a cross.
     */
    internal fun campanile(tx: Float, ty: Float, tw: Float, h: Float, wall: IntArray, cap: IntArray, seed: Int) {
        i.box(tx, ty, 0f, tw, tw, h, wall[0], wall[1], wall[2])
        if (fine) {
            var z = 1.5f
            while (z < h - 1f) {
                i.line(tx, ty + tw, z, tx + tw, ty + tw, z, Col.scale(wall[1], 0.86f)); i.line(tx + tw, ty, z, tx + tw, ty + tw, z, Col.scale(wall[2], 0.86f))
                z += if (finest) 1f else 1.5f
            }
        }
        val band = h - 7f
        i.line(tx, ty + tw, band, tx + tw, ty + tw, band, ISTRIA_L); i.line(tx + tw, ty, band, tx + tw, ty + tw, band, ISTRIA_M)
        // the belfry's openings: two arches a side closer up, one at the nominal detail
        val dark = Col.hex(0x2A2226)
        if (fine) {
            for (u in floatArrayOf(0.14f, 0.5f)) {
                i.panelY(tx + u, ty + tw, band + 1.2f, 0.24f, 3.6f, dark); i.panelX(tx + tw, ty + u, band + 1.2f, 0.24f, 3.6f, dark)
            }
        } else {
            i.panelY(tx + 0.25f, ty + tw, band + 1.2f, 0.36f, 3.6f, dark); i.panelX(tx + tw, ty + 0.25f, band + 1.2f, 0.36f, 3.6f, dark)
        }
        i.px(tx + tw, ty + tw / 2, band + 2.6f, Pal.GOLD); i.px(tx + tw, ty + tw / 2, band + 1.8f, Pal.GOLD_L)
        i.box(tx - 0.06f, ty - 0.06f, h - 0.8f, tw + 0.12f, tw + 0.12f, 0.8f, ISTRIA_L, ISTRIA_M, ISTRIA_D)
        val capL = if (env.snow) Pal.SNOW_L else cap[0]; val capD = if (env.snow) Pal.SNOW_D else cap[2]
        i.pyramid(tx - 0.08f, ty - 0.08f, h, tw + 0.16f, tw + 0.16f, 5f, capL, capD)
        val ax = i.ix(tx + tw / 2, ty + tw / 2); val ay = i.iy(tx + tw / 2, ty + tw / 2, h + 5f)
        s.sprite(ax, ay) { c.vline(ax, ay - 3, ay, Pal.GOLD); c.set(ax - 1, ay - 2, Pal.GOLD); c.set(ax + 1, ay - 2, Pal.GOLD) }
    }

    /**
     * Carinthia's church: white walls, the nave under dark shingles, and its tower behind it with the belfry, the clock's
     * face and the onion dome of green copper with its lantern, a gilt ball and the cross.
     */
    private fun onionChurch(b: Building, x: Float, y: Float, st: FireStyle) {
        val tx = x + 1.0f; val ty = y + 0.2f
        val tw = st.towerWall
        plaster(tx, ty, 0f, 1f, 1f, 21f, tw, b.plot * 43)
        i.line(tx, ty + 1f, 15f, tx + 1f, ty + 1f, 15f, Pal.STONE_M); i.line(tx + 1f, ty, 15f, tx + 1f, ty + 1f, 15f, Pal.STONE_D)
        i.panelY(tx + 0.3f, ty + 1f, 16.5f, 0.4f, 3f, Col.hex(0x2A2226))
        i.panelX(tx + 1f, ty + 0.3f, 16.5f, 0.4f, 3f, Col.hex(0x2A2226))
        i.px(tx + 1f, ty + 0.5f, 12f, Pal.GOLD_L); i.px(tx + 1f, ty + 0.5f, 11f, Pal.GOLD)
        i.box(tx - 0.08f, ty - 0.08f, 21f, 1.16f, 1.16f, 0.8f, tw[0], tw[1], tw[2])
        TowerTops.onion(s, i.ix(tx + 0.5f, ty + 0.5f), i.iy(tx + 0.5f, ty + 0.5f, 21.8f), st.towerTop)
        val nx = x + 0.5f; val ny = y + 1.2f; val nw = 2f; val nd = 1.8f
        val t = st.churchWall
        plaster(nx, ny, 0f, nw, nd, 11f, t, b.plot * 47)
        i.line(nx, ny + nd, 1f, nx + nw, ny + nd, 1f, Pal.STONE_M); i.line(nx + nw, ny, 1f, nx + nw, ny + nd, 1f, Pal.STONE_D)
        winX(nx + nw, ny + 0.3f, 4f, 0.35f, 5f, null, null); winX(nx + nw, ny + 1.15f, 4f, 0.35f, 5f, null, null)
        p.doorY(nx + 0.75f, ny + nd, 0f, 0.5f, 5f)
        i.px(nx + 1f, ny + nd, 5f, Pal.DOOR)
        p.roofY(nx - 0.2f, ny - 0.1f, 11f, nw + 0.4f, nd + 0.3f, 7.5f, p.shingles(), t[1])
        i.px(nx + 1f, ny + nd + 0.2f, 14f, if (p.lit) Pal.WINDOW_LIT else Pal.GLASS)
        if (b.level >= 2 && !env.snow) p.flowers(x + 2.8f, y + 2.8f, b.plot)
    }

    /**
     * The Lake District's church: the nave of grey stone under slate with its lancet windows, and its square tower at the
     * end, its belfry's louvres and the battlements along its top with a pinnacle at each corner.
     */
    private fun towerChurch(b: Building, x: Float, y: Float, st: FireStyle) {
        val tx = x + 0.9f; val ty = y + 0.1f; val tw = 1.3f
        TowerTops.battlemented(s, p, tx, ty, tw, 18.5f, st.towerWall, b.plot)
        val nx = x + 0.45f; val ny = y + 1.4f; val nw = 2.1f; val nd = 1.6f
        val t = st.churchWall
        stoneBox(nx, ny, 0f, nw, nd, 9f, t, b.plot * 53)
        for (u in floatArrayOf(0.3f, 1.05f)) lancetX(nx + nw, ny + u, 3f, 0.3f, 4.5f)
        // the porch on the front, its door
        p.doorY(nx + 0.8f, ny + nd, 0f, 0.5f, 4.5f)
        if (fine) i.line(nx + 0.75f, ny + nd, 4.7f, nx + 1.35f, ny + nd, 4.7f, Col.mix(t[0], Col.hex(0xFFFFFF), 0.3f))
        p.roofY(nx - 0.2f, ny - 0.1f, 9f, nw + 0.4f, nd + 0.3f, 6.5f, p.slate(), t[1])
        lancetY(nx + 0.95f, ny + nd + 0.2f, 10.5f, 0.25f, 2.5f)
        if (b.level >= 2 && !env.snow) p.flowers(x + 2.8f, y + 2.8f, b.plot)
    }

    /** A lancet window on the +x face: tall, its pointed head a pixel narrower, lit at night. */
    internal fun lancetX(x: Float, y: Float, z: Float, d: Float, h: Float) {
        val col = if (p.lit) Pal.WINDOW_LIT else Col.scale(Pal.GLASS, 0.85f)
        glowIf(p.lit) { i.panelX(x, y, z, d, h, col); i.panelX(x, y + d * 0.25f, z + h, d * 0.5f, 0.8f, col) }
        if (p.lit) s.light(i.sx(x, y + d / 2), i.sy(x, y + d / 2, z + h / 2) + 3 * k, 10f, 0.45f * env.windows)
        if (fine) i.line(x, y + d / 2, z, x, y + d / 2, z + h, Col.hex(0x5A5E64))
    }

    /** A lancet window on the +y face (see [lancetX]). */
    private fun lancetY(x: Float, y: Float, z: Float, w: Float, h: Float) {
        val col = if (p.lit) Pal.WINDOW_LIT else Pal.GLASS
        glowIf(p.lit) { i.panelY(x, y, z, w, h, col); i.panelY(x + w * 0.25f, y, z + h, w * 0.5f, 0.8f, col) }
    }

    private inline fun glowIf(on: Boolean, block: () -> Unit) { if (on) glow(block) else block() }

    /**
     * The watchtower's legs from its third level in Friuli (as Sulla torre builds it): four pillars of river cobbles with
     * bands of brick, a brick arch from each to the next under the platform on the two sides we see.
     */
    internal fun towerPillars(x0: Float, y0: Float, w: Float, seed: Int) {
        val pw = 0.36f; val top = 16f
        fun pillar(px: Float, py: Float) {
            i.box(px - pw / 2, py - pw / 2, 0f, pw, pw, top, COB[0], COB[1], COB[2])
            var z = 2.5f
            while (z < top) { i.line(px - pw / 2, py + pw / 2, z, px + pw / 2, py + pw / 2, z, BRICK_L); i.line(px + pw / 2, py - pw / 2, z, px + pw / 2, py + pw / 2, z, BRICK_D); z += 3.5f }
            if (fine) for (j in 0 until 6) i.px(px - pw / 2 + Noise.rnd(seed, j, 41) * pw, py + pw / 2, 1f + Noise.rnd(seed, j, 42) * (top - 2f), COB_D)
        }
        /** An arch of brick between two pillars' tops, under the platform. */
        fun arch(ax: Float, ay: Float, bx: Float, by: Float, col: Int) {
            val n = 8
            for (q in 0..n) {
                val u = q / n.toFloat()
                val z = top - 2.4f * (1f - sin(u * PI).toFloat()) - 0.2f
                i.px(ax + (bx - ax) * u, ay + (by - ay) * u, z, col)
            }
        }
        pillar(x0, y0)
        pillar(x0 + w, y0); pillar(x0, y0 + w)
        arch(x0, y0 + w, x0 + w, y0 + w, BRICK_L)
        arch(x0 + w, y0, x0 + w, y0 + w, BRICK_D)
        pillar(x0 + w, y0 + w)
    }

    // ---------------------------------------------------------------- huts

    /**
     * A hut of stone (Friuli's of the hills, the Lake District's of slate stone): rubble walls under the village's roof, its
     * door and a small window; its open hearth's smoke through the roof, a chimney from level 2 (round in the Lake District).
     */
    private fun stoneHut(b: Building, x: Float, y: Float, st: FireStyle) {
        val x0 = x + 0.5f; val y0 = y + 0.5f
        val t = if (st.stone) st.walls[0] else HUT_STONE
        stoneBox(x0, y0, 0f, 2f, 2f, 6.5f, t, b.plot * 19, quoins = false)
        p.doorY(x0 + 0.7f, y0 + 2f, 0f, 0.6f, 4.8f, handleU = 0.5f, handleZ = 2.4f)
        winX(x0 + 2f, y0 + 0.7f, 2.8f, 0.5f, 2f, null, if (st.stone) WHITE_FRAME else null)
        val r = roofOf(st)
        p.roofX(x0 - 0.3f, y0 - 0.4f, 6f, 2.6f, 2.8f, if (st.roofKind == RoofKind.COPPI) 4.5f else 5.5f, r, t[2])
        if (b.level >= 2) {
            if (st.stone) roundChimney(x0 + 1.9f, y0 + 1.0f, 9.5f, 4f, t, p.smoking)
            else cappedChimney(x0 + 0.3f, y0 + 1.0f, 8f, 4f, t, r, p.smoking)
        } else if (p.smoking) s.plume(i.sx(x0 + 1f, y0 + 1f), i.sy(x0 + 1f, y0 + 1f, 10.5f), b.plot * 13 + 7)
    }

    /**
     * A casone of the lagoon (Friuli's coast, as the fire's village has them by the sea): low plastered walls under a steep
     * thatch of reeds that comes down nearly to the ground, the door in its gable end, the hearth's smoke from its ridge.
     */
    private fun casone(b: Building, x: Float, y: Float) {
        val x0 = x + 0.45f; val y0 = y + 0.5f; val w = 2.1f; val d = 2f
        plaster(x0, y0, 0f, w, d, 3f, CASONE_WALL, b.plot * 31)
        val r = thatch()
        p.roofX(x0 - 0.35f, y0 - 0.45f, 2.2f, w + 0.55f, d + 0.9f, 10f, r, THATCH_L)
        if (!env.snow) {
            // the reeds' streaks down both slopes, the ridge bound dark
            val step = if (fine) 0.2f else 0.4f
            var u = 0.1f
            while (u < w + 0.55f) {
                val xx = x0 - 0.35f + u
                i.line(xx, y0 - 0.45f + (d + 0.9f) / 2, 12.2f, xx, y0 + d + 0.45f, 2.2f, if ((u / step).roundToInt() % 2 == 0) THATCH_D else Col.mix(THATCH_M, THATCH_D, 0.4f))
                if (fine) i.line(xx, y0 - 0.45f + (d + 0.9f) / 2, 12.2f, xx, y0 - 0.45f, 2.2f, Col.mix(THATCH_L, THATCH_M, 0.5f))
                u += step
            }
            i.line(x0 - 0.35f, y0 + d / 2, 12.2f, x0 + w + 0.2f, y0 + d / 2, 12.2f, Col.hex(0x5A4628))
        }
        // the door in the gable end, its little reed canopy
        val gx = x0 + w + 0.2f
        i.panelX(gx, y0 + 0.7f, 0f, 0.6f, 4.2f, if (p.lit) Col.hex(0x6A4A2A) else Col.hex(0x2A1E16))
        if (p.lit) { glow { i.panelX(gx, y0 + 0.82f, 0.4f, 0.36f, 3f, Pal.WINDOW_LIT) }; s.light(i.sx(gx, y0 + 1f), i.sy(gx, y0 + 1f, 2f), 11f, 0.5f * env.windows) }
        if (p.smoking) s.plume(i.sx(x0 + w / 2, y0 + d / 2), i.sy(x0 + w / 2, y0 + d / 2, 12.5f), b.plot * 13 + 9)
    }

    // ---------------------------------------------------------------- hayracks

    /**
     * Friuli's fienile (the field's scene's): walls of river cobbles with bands of brick, two round brick arches below (the
     * hay heaped under the left one from June), the loft above with the hay in its opening between two brick lattices, a
     * lattice in the gable, and a low roof of coppi.
     */
    private fun fienile(b: Building, x: Float, y: Float, st: FireStyle) {
        val x0 = x + 0.25f; val y0 = y + 0.8f; val w = 2.5f; val d = 1.4f; val h = 10.5f
        val hay = env.month in 6..12 || env.month <= 3
        val fy = y0 + d
        i.box(x0, y0, 0f, w, d, h, COB[0], COB[1], COB[2])
        // the cobbles: round, pale and dark, laid in courses
        for (j in 0 until if (fine) 70 else 26) {
            val u = Noise.rnd(b.plot, j, 31); val v = Noise.rnd(b.plot, j, 32)
            val col = if (j % 3 == 0) COB_D else Col.mix(COB[0], Col.hex(0xFFFFFF), 0.3f)
            if (j % 2 == 0) i.px(x0 + u * w, fy, 0.5f + v * (h - 1f), col) else i.px(x0 + w, y0 + u * d, 0.5f + v * (h - 1f), Col.scale(col, 0.85f))
        }
        // the bands of brick, three courses each, and the quoins at the corner
        for (z in floatArrayOf(3.6f, 7.4f)) {
            i.line(x0, fy, z, x0 + w, fy, z, BRICK_L); i.line(x0 + w, y0, z, x0 + w, y0 + d, z, BRICK_D)
            if (fine) { i.line(x0, fy, z + 0.5f, x0 + w, fy, z + 0.5f, BRICK_D); i.line(x0 + w, y0, z + 0.5f, x0 + w, y0 + d, z + 0.5f, Col.scale(BRICK_D, 0.85f)) }
        }
        if (fine) { var z = 0.5f; var n = 0; while (z < h) { i.line(x0 + w - if (n % 2 == 0) 0.3f else 0.16f, fy, z, x0 + w, fy, z, BRICK_L); z += 0.9f; n++ } }
        // the two arches: their dark insides, the brick rings round them
        val dark = Col.hex(0x2E2622)
        for ((n, ax) in floatArrayOf(x0 + 0.3f, x0 + 1.42f).withIndex()) {
            val aw = 0.78f
            i.panelY(ax, fy, 0f, aw, 2.6f, dark)
            i.panelY(ax + 0.08f, fy, 2.6f, aw - 0.16f, 0.6f, dark)
            i.panelY(ax + 0.2f, fy, 3.2f, aw - 0.4f, 0.3f, dark)
            i.line(ax - 0.04f, fy, 0f, ax - 0.04f, fy, 2.6f, BRICK_L); i.line(ax + aw + 0.04f, fy, 0f, ax + aw + 0.04f, fy, 2.6f, BRICK_D)
            i.line(ax - 0.04f, fy, 2.6f, ax + 0.18f, fy, 3.5f, BRICK_L); i.line(ax + 0.18f, fy, 3.5f, ax + aw - 0.18f, fy, 3.5f, BRICK_L); i.line(ax + aw - 0.18f, fy, 3.5f, ax + aw + 0.04f, fy, 2.6f, BRICK_D)
            if (n == 0 && hay) {
                i.panelY(ax + 0.06f, fy, 0f, aw - 0.12f, 1.8f, Pal.HAY_M)
                i.panelY(ax + 0.16f, fy, 1.8f, aw - 0.32f, 0.6f, Pal.HAY_M)
                for (q in 0 until 6) i.px(ax + 0.1f + Noise.rnd(b.plot, q, 33) * (aw - 0.2f), fy, 0.4f + Noise.rnd(b.plot, q, 34) * 1.6f, if (q % 2 == 0) Pal.HAY_L else Pal.HAY_D)
            }
        }
        // the loft's opening in the middle, the hay bulging out of it; a brick lattice either side
        val lx = x0 + 0.95f; val lw = 0.6f
        i.panelY(lx, fy, 5.6f, lw, 3.2f, dark)
        if (hay) {
            i.panelY(lx + 0.04f, fy, 5.6f, lw - 0.08f, 2.4f, Pal.HAY_M)
            for (q in 0 until 5) i.px(lx + 0.06f + Noise.rnd(b.plot, q, 35) * (lw - 0.12f), fy, 5.8f + Noise.rnd(b.plot, q, 36) * 2.2f, if (q % 2 == 0) Pal.HAY_L else Pal.HAY_D)
        }
        i.line(lx - 0.05f, fy, 5.5f, lx + lw + 0.05f, fy, 5.5f, ISTRIA_L)
        for (gx in floatArrayOf(x0 + 0.3f, x0 + 1.75f)) lattice(gx, fy, 5.6f, 0.45f, 2.6f)
        p.roofX(x0 - 0.3f, y0 - 0.35f, h, w + 0.6f, d + 0.7f, 4.5f, roofOf(st), COB[2])
        // the lattice high in the gable
        val gz = h + 0.6f
        for (q in 0..2) for (rr in 0..1) if ((q + rr) % 2 == 0) i.px(x0 + w + 0.3f, y0 + 0.4f + q * 0.25f, gz + rr * 1f, dark)
    }

    /** A lattice of brick (the gelosia that airs a loft) on the +y face: open and brick in a checker, finer closer up. */
    private fun lattice(x: Float, y: Float, z: Float, w: Float, h: Float) {
        val n = if (fine) 4 else 2; val m = if (fine) 6 else 3
        for (q in 0 until n) for (r in 0 until m) {
            val col = if ((q + r) % 2 == 0) Col.hex(0x2E2622) else BRICK_L
            i.panelY(x + q * w / n, y, z + r * h / m, w / n, h / m, col)
        }
    }

    /**
     * Carinthia's Harpfe (as on Am See's far shore): a single rack of four posts in a row with the laths across between
     * them, a strut against each end, hay hung on it from June to October, and a narrow shingle roof along its top.
     */
    private fun harpfe(b: Building, x: Float, y: Float) {
        val x0 = x + 0.25f; val yc = y + 1.55f; val top = 13f; val len = 2.5f
        val hay = env.month in 6..10
        // the struts at the back, then the rack
        i.line(x0 + 0.05f, yc, 9f, x0 + 0.05f, yc - 0.9f, 0f, Pal.WOOD_D)
        i.line(x0 + len - 0.05f, yc, 9f, x0 + len - 0.05f, yc - 0.9f, 0f, Pal.WOOD_D)
        for (j in 0..3) p.postBox(x0 + j * len / 3f, yc, top)
        for (z in 2..12 step 2) {
            i.line(x0, yc, z.toFloat(), x0 + len, yc, z.toFloat(), Pal.WOOD_L)
            i.line(x0, yc, z - 0.5f, x0 + len, yc, z - 0.5f, Pal.WOOD_D)
        }
        if (hay) for (bay in 0..2) {
            val bx = x0 + bay * len / 3f + 0.1f; val hh = HARPFE_HAY[(bay + b.plot) % HARPFE_HAY.size]
            i.panelY(bx, yc + 0.02f, 2.5f, len / 3f - 0.2f, hh, Pal.HAY_M)
            for (q in 0 until 7) {
                val u = Noise.rnd(b.plot * 3 + bay, q, 5); val v = Noise.rnd(b.plot * 3 + bay, q, 6)
                i.px(bx + u * (len / 3f - 0.2f), yc + 0.02f, 2.5f + v * hh, if (q % 2 == 0) Pal.HAY_L else Pal.HAY_D)
            }
        }
        i.line(x0 + 0.05f, yc, 9f, x0 + 0.05f, yc + 0.9f, 0f, Pal.WOOD_M)
        i.line(x0 + len - 0.05f, yc, 9f, x0 + len - 0.05f, yc + 0.9f, 0f, Pal.WOOD_M)
        p.roofX(x0 - 0.35f, yc - 0.48f, top - 0.3f, len + 0.7f, 0.96f, 2.6f, p.shingles(), Pal.WOOD_D)
    }

    /**
     * The Lake District's hay barn: a field barn of grey stone with its quoins, under slate; its big cart door in an arch of
     * stones, the forking hole over it with hay in it from July, the ventilation slits and the owl hole in the gable.
     */
    private fun hayBarn(b: Building, x: Float, y: Float, st: FireStyle) {
        val x0 = x + 0.35f; val y0 = y + 0.6f; val w = 2.4f; val d = 1.8f; val h = 8.5f
        val t = st.walls[0]
        stoneBox(x0, y0, 0f, w, d, h, t, b.plot * 59)
        val fy = y0 + d
        // the cart door, its arch of stones and the hay in the forking hole over it
        i.panelY(x0 + 0.7f, fy, 0f, 1.0f, 5.2f, BARN_DOOR)
        if (fine) { i.line(x0 + 1.2f, fy, 0f, x0 + 1.2f, fy, 5.2f, Col.scale(BARN_DOOR, 0.7f)); var u = 0.82f; while (u < 1.7f) { i.line(x0 + u, fy, 0.2f, x0 + u, fy, 5f, Col.scale(BARN_DOOR, 0.85f)); u += 0.2f } }
        val arch = Col.mix(t[0], Col.hex(0xFFFFFF), 0.3f)
        i.line(x0 + 0.62f, fy, 5.2f, x0 + 0.85f, fy, 6f, arch); i.line(x0 + 0.85f, fy, 6f, x0 + 1.55f, fy, 6f, arch); i.line(x0 + 1.55f, fy, 6f, x0 + 1.78f, fy, 5.2f, arch)
        i.panelY(x0 + 1.0f, fy, 6.6f, 0.4f, 1.3f, Col.hex(0x1E1A18))
        if (env.month >= 7 || env.month <= 3) { i.panelY(x0 + 1.04f, fy, 6.6f, 0.32f, 0.8f, Pal.HAY_M); i.px(x0 + 1.15f, fy, 7.2f, Pal.HAY_L) }
        // the slits on the side
        for (v in floatArrayOf(0.35f, 0.9f, 1.45f)) i.line(x0 + w, y0 + v, 2.5f, x0 + w, y0 + v, 5.5f, Col.hex(0x1E1A18))
        p.roofX(x0 - 0.25f, y0 - 0.3f, h, w + 0.5f, d + 0.6f, 6f, p.slate(), t[2])
        i.px(x0 + w + 0.25f, y0 + d / 2, h + 2.2f, Col.hex(0x1E1A18)) // the owl hole
    }

    // ---------------------------------------------------------------- wells

    /**
     * Friuli's well, la vera da pozzo: a head of pale stone with its moulded rim, the water dark in it (ice in winter), and
     * over it the wrought-iron arch with its pulley, the rope and the copper bucket on the rim.
     */
    private fun vera(b: Building, x: Float, y: Float) {
        val x0 = x + 0.9f; val y0 = y + 0.9f; val wd = 1.2f
        if (b.level >= 2 && !env.snow) { p.flowers(x + 0.6f, y + 2.4f, b.plot); p.flowers(x + 2.4f, y + 0.8f, b.plot + 1) }
        // the iron's back post first, the head, the front post and the arch
        i.post(x0 + 0.08f, y0 + wd / 2, 3.6f, 10.5f, IRON)
        i.box(x0, y0, 0f, wd, wd, 3.6f, ISTRIA_L, ISTRIA_M, ISTRIA_D)
        i.box(x0 - 0.06f, y0 - 0.06f, 3.6f, wd + 0.12f, wd + 0.12f, 0.6f, Col.mix(ISTRIA_L, Col.hex(0xFFFFFF), 0.3f), ISTRIA_L, ISTRIA_M)
        if (fine) { i.line(x0, y0 + wd, 1.6f, x0 + wd, y0 + wd, 1.6f, ISTRIA_D); i.line(x0 + wd, y0, 1.6f, x0 + wd, y0 + wd, 1.6f, Col.scale(ISTRIA_D, 0.85f)) }
        i.top(x0 + 0.15f, y0 + 0.15f, 4.2f, wd - 0.3f, wd - 0.3f, if (env.snow) Pal.ICE_M else Pal.WATER_D)
        i.post(x0 + wd - 0.08f, y0 + wd / 2, 3.6f, 10.5f, IRON)
        // the arch from post to post, its scrolls closer up, the pulley at its top
        val n = 10
        for (q in 0..n) {
            val u = q / n.toFloat()
            val z = 10.5f + sin(u * PI).toFloat() * 2.4f
            i.px(x0 + 0.08f + u * (wd - 0.16f), y0 + wd / 2, z, IRON)
            if (finest && q in 2..8 step 2) i.px(x0 + 0.08f + u * (wd - 0.16f), y0 + wd / 2, z - 1.2f, IRON)
        }
        i.px(x0 + wd / 2, y0 + wd / 2, 12.4f, Pal.STONE_X)
        i.line(x0 + wd / 2, y0 + wd / 2, 12.2f, x0 + wd / 2, y0 + wd / 2, 5.8f, Pal.CANVAS_M)
        i.box(x0 + wd / 2 - 0.15f, y0 + wd / 2 - 0.15f, 4.8f, 0.3f, 0.3f, 1.2f, COPPER_L, COPPER_M, COPPER_D)
    }

    /**
     * Carinthia's well, a Brunnentrog: a long trough of a hollowed log, the water in it, the post at its end with the spout
     * running into it (frozen in winter, icicles on the spout).
     */
    private fun trough(b: Building, x: Float, y: Float) {
        val x0 = x + 0.55f; val y0 = y + 1.35f; val len = 1.9f; val wd = 0.7f
        if (b.level >= 2 && !env.snow) { p.flowers(x + 0.6f, y + 2.6f, b.plot); p.flowers(x + 2.5f, y + 0.9f, b.plot + 1) }
        // the post behind the trough's end, the spout toward the water
        val sx = x0 + 0.15f; val sy = y0 + 0.2f
        i.box(sx, sy - 0.25f, 0f, 0.3f, 0.3f, 7f, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        i.top(sx - 0.03f, sy - 0.28f, 7f, 0.36f, 0.36f, if (env.snow) Pal.SNOW_L else Pal.LOG_D)
        i.box(x0, y0, 0f, len, wd, 2.6f, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        if (fine) { i.line(x0, y0 + wd, 1.3f, x0 + len, y0 + wd, 1.3f, Pal.LOG_D); i.top(x0 + len - 0.1f, y0, 0f, 0.1f, wd, Pal.LOG_D) }
        i.top(x0 + 0.1f, y0 + 0.1f, 2.5f, len - 0.2f, wd - 0.2f, if (env.snow) Pal.ICE_L else Pal.WATER_M)
        if (!env.snow && fine) i.line(x0 + 0.2f, y0 + 0.25f, 2.5f, x0 + len - 0.3f, y0 + 0.25f, 2.5f, Pal.WATER_L)
        // the spout: a short pipe out of the post, the water falling from it
        i.line(sx + 0.3f, sy, 5.5f, sx + 0.75f, sy + 0.2f, 5.4f, Pal.WOOD_X)
        if (env.snow) {
            i.px(sx + 0.75f, sy + 0.2f, 4.7f, Pal.ICE_L); if (fine) i.px(sx + 0.7f, sy + 0.2f, 4.1f, Pal.ICE_M)
        } else {
            val fall = ((s.time * 8).toInt() and 3)
            for (z in 3..5) i.px(sx + 0.78f, sy + 0.22f, z.toFloat() - (fall % 2) * 0.4f, if ((z + fall) % 3 == 0) Pal.WATER_L else Pal.WATER_M)
        }
    }

    /**
     * The Lake District's village pump: a stone trough, the pump of cast iron behind it (painted dark green), its spout over
     * the water and its long curved handle, a cap on its top.
     */
    private fun pump(b: Building, x: Float, y: Float) {
        val x0 = x + 0.8f; val y0 = y + 1.45f; val len = 1.5f; val wd = 0.75f
        if (b.level >= 2 && !env.snow) { p.flowers(x + 0.6f, y + 2.6f, b.plot); p.flowers(x + 2.5f, y + 0.9f, b.plot + 1) }
        // the pump's column behind the trough's middle
        val px0 = x0 + 0.55f; val py0 = y0 - 0.3f
        i.box(px0, py0, 0f, 0.32f, 0.32f, 7.5f, PUMP_L, PUMP_M, PUMP_D)
        i.box(px0 - 0.05f, py0 - 0.05f, 7.5f, 0.42f, 0.42f, 0.6f, PUMP_L, PUMP_M, PUMP_D)
        i.px(px0 + 0.16f, py0 + 0.16f, 8.6f, PUMP_L) // the knob
        // the handle: up from the column's side and down in a curve
        i.line(px0 + 0.32f, py0 + 0.16f, 6.6f, px0 + 0.85f, py0 + 0.16f, 7.6f, PUMP_D)
        i.line(px0 + 0.85f, py0 + 0.16f, 7.6f, px0 + 1.15f, py0 + 0.16f, 6.2f, PUMP_D)
        // the spout toward the trough
        i.line(px0 + 0.16f, py0 + 0.32f, 5.2f, px0 + 0.16f, py0 + 0.6f, 4.8f, PUMP_D)
        stoneBox(x0, y0, 0f, len, wd, 2.4f, HUT_STONE, b.plot * 61, quoins = false)
        i.top(x0 + 0.1f, y0 + 0.1f, 2.4f, len - 0.2f, wd - 0.2f, if (env.snow) Pal.ICE_L else Pal.WATER_M)
        if (!env.snow && fine) i.line(x0 + 0.2f, y0 + 0.3f, 2.4f, x0 + len - 0.3f, y0 + 0.3f, 2.4f, Pal.WATER_L)
    }

    // ---------------------------------------------------------------- hives

    /**
     * Friuli's bee house (Dalle api's): its hives in plain painted boxes in two tiers (no pictures), under the village's coppi;
     * a lean-to of boards at level 1, a shed of river cobbles with a band of brick from level 2.
     */
    private fun beeHouse(b: Building, x: Float, y: Float, st: FireStyle) {
        val x0 = x + 0.5f; val y0 = y + 0.9f
        if (!env.snow && env.month in 4..9) p.flowers(x + 1.5f, y + 2.7f, b.plot * 3)
        if (b.level >= 2) {
            // the shed of river cobbles with a band of brick (Dalle api's from its second level), its front open on the hives
            i.box(x0, y0, 0f, 2f, 1.3f, 7f, COB[0], COB[1], COB[2])
            i.line(x0 + 2f, y0, 3.8f, x0 + 2f, y0 + 1.3f, 3.8f, BRICK_D)
            i.panelY(x0, y0 + 1.3f, 0f, 2f, 1.2f, COB[1])
        } else {
            i.post(x0 + 0.1f, y0 + 1.3f, 0f, 1f, Pal.WOOD_X); i.post(x0 + 1.9f, y0 + 1.3f, 0f, 1f, Pal.WOOD_X)
            i.box(x0, y0, 1f, 2f, 1.3f, 6f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        }
        for (row in 0..1) for (j in 0..3) {
            val col = HIVES[(j + row * 2 + b.plot) % HIVES.size]
            val xx = x0 + 0.12f + j * 0.47f; val zz = 1.6f + row * 2.6f
            i.panelY(xx, y0 + 1.3f, zz, 0.36f, 2f, col)
            i.px(xx + 0.12f, y0 + 1.3f, zz + 0.3f, Pal.WOOD_X)
            if (fine) i.line(xx, y0 + 1.3f, zz + 1.8f, xx + 0.36f, y0 + 1.3f, zz + 1.8f, Col.scale(col, 0.8f)) // the box's lid
        }
        p.roofX(x0 - 0.3f, y0 - 0.3f, 6.8f, 2.6f, 1.9f, 3f, roofOf(st), Pal.WOOD_D)
        if (!env.snow && env.dark < 0.5f) { val cx = i.sx(x0 + 1f, y0 + 1.8f); val cy = i.sy(x0 + 1f, y0 + 1.8f, 4f); s.fx { p.bees(cx, cy, b.plot) } }
    }

    /**
     * The Lake District's beehives: three white WBC hives on their legs among the flowers, each its lifts stacked under a
     * pitched roof, the entrance dark at its foot, the bees about them by day.
     */
    private fun wbcHives(b: Building, x: Float, y: Float) {
        if (!env.snow && env.month in 4..9) { p.flowers(x + 1.5f, y + 2.7f, b.plot * 3); p.flowers(x + 0.5f, y + 1.0f, b.plot * 3 + 1) }
        for ((j, at) in WBC_AT.withIndex()) {
            val hx = x + at[0]; val hy = y + at[1]
            i.post(hx + 0.05f, hy + 0.6f, 0f, 1f, Pal.WOOD_X); i.post(hx + 0.55f, hy + 0.6f, 0f, 1f, Pal.WOOD_X)
            i.box(hx - 0.04f, hy - 0.04f, 1f, 0.68f, 0.68f, 1.4f, WBC[0], WBC[1], WBC[2])
            i.panelY(hx + 0.2f, hy + 0.64f, 1.1f, 0.2f, 0.35f, Col.hex(0x2A2420)) // the entrance
            val lifts = 2 + (b.plot + j) % 2
            for (q in 0 until lifts) {
                val z = 2.4f + q * 1.6f
                i.box(hx, hy, z, 0.6f, 0.6f, 1.5f, WBC[0], WBC[1], WBC[2])
                i.line(hx, hy + 0.6f, z, hx + 0.6f, hy + 0.6f, z, Col.scale(WBC[1], 0.85f))
            }
            val rz = 2.4f + lifts * 1.6f
            p.roofX(hx - 0.1f, hy - 0.1f, rz, 0.8f, 0.8f, 1.6f, if (env.snow) BuildingPainter.Roof(Pal.SNOW_L, Pal.SNOW_M, WBC[2], rows = false) else BuildingPainter.Roof(WBC[0], WBC[1], WBC[2], rows = false), WBC[1])
        }
        if (!env.snow && env.dark < 0.5f) { val cx = i.sx(x + 1.4f, y + 1.8f); val cy = i.sy(x + 1.4f, y + 1.8f, 4f); s.fx { p.bees(cx, cy, b.plot) } }
    }

    // ---------------------------------------------------------------- the smithy, the oak, the school

    /**
     * The smithy as the village builds it: Friuli's of river cobbles with bands of brick (its scene's walls) under coppi, an
     * iron grille in the window; elsewhere of stone under the village's roof. The forge glows while it has its charcoal.
     */
    private fun smithy(b: Building, x: Float, y: Float, st: FireStyle, cobbles: Boolean) {
        val x0 = x + 0.5f; val y0 = y + 0.5f
        if (cobbles) {
            i.box(x0, y0, 0f, 2f, 2f, 8f, COB[0], COB[1], COB[2])
            for (j in 0 until if (fine) 50 else 18) {
                val u = Noise.rnd(b.plot, j, 37); val v = Noise.rnd(b.plot, j, 38)
                val col = if (j % 3 == 0) COB_D else Col.mix(COB[0], Col.hex(0xFFFFFF), 0.3f)
                if (j % 2 == 0) i.px(x0 + u * 2f, y0 + 2f, 0.5f + v * 7f, col) else i.px(x0 + 2f, y0 + u * 2f, 0.5f + v * 7f, Col.scale(col, 0.85f))
            }
            for (z in floatArrayOf(2.8f, 5.8f)) { i.line(x0, y0 + 2f, z, x0 + 2f, y0 + 2f, z, BRICK_L); i.line(x0 + 2f, y0, z, x0 + 2f, y0 + 2f, z, BRICK_D) }
        } else {
            i.box(x0, y0, 0f, 2f, 2f, 8f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
            p.speckle(x0, y0, 0f, 2f, 2f, 8f, b.plot * 7, Pal.STONE_D, Pal.STONE_X)
            p.mortar(x0, y0, 0f, 2f, 2f, 8f, Pal.STONE_D, Pal.STONE_X)
        }
        i.panelY(x0 + 0.3f, y0 + 2f, 0f, 1.1f, 6f, Col.hex(0x2A1E18))
        val flick = 0.8f + 0.2f * sin(s.time * 13).toFloat()
        val forge = p.hearth
        if (forge) {
            glow { i.panelY(x0 + 0.5f, y0 + 2f, 0.5f, 0.6f, 2f, Pal.FLAME[2]); i.px(x0 + 0.75f, y0 + 2f, 2f, Pal.FLAME[1]) }
            s.light(i.sx(x0 + 0.8f, y0 + 2f), i.sy(x0 + 0.8f, y0 + 2f, 2f), 18f, 0.7f * flick)
        } else {
            i.panelY(x0 + 0.5f, y0 + 2f, 0.5f, 0.6f, 1f, Col.hex(0x5A5652)); i.px(x0 + 0.75f, y0 + 2f, 1.5f, Col.hex(0x6E6A64))
        }
        winX(x0 + 2f, y0 + 0.7f, 3f, 0.5f, 2f, null, if (cobbles) BRICK_L else null)
        if (cobbles) for (v in floatArrayOf(0.85f, 1.05f)) i.line(x0 + 2f, y0 + v, 3f, x0 + 2f, y0 + v, 5f, IRON) // the grille
        val ax = i.ix(x0 + 1.6f, y0 + 2.5f); val ay = i.iy(x0 + 1.6f, y0 + 2.5f, 0f)
        s.sprite(ax, ay) {
            c.fillRect(ax - 1, ay - 2, 2, 2, Pal.STONE_X); c.hline(ax - 2, ax + 1, ay - 3, Col.hex(0x3C3C44)); c.set(ax - 2, ay - 3, Col.hex(0x6A6A76))
        }
        p.roofX(x0 - 0.3f, y0 - 0.3f, 7.5f, 2.6f, 2.6f, if (st.roofKind == RoofKind.COPPI) 4f else 5f, roofOf(st), if (cobbles) COB[2] else Pal.STONE_M)
        val cw = if (cobbles) intArrayOf(BRICK_L, BRICK_M, BRICK_D) else intArrayOf(Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        i.box(x0 + 1.4f, y0 + 0.2f, 6f, 0.6f, 0.6f, 11f, cw[0], cw[1], cw[2])
        if (forge) glow { i.top(x0 + 1.5f, y0 + 0.3f, 17f, 0.4f, 0.4f, Pal.FLAME[2]) } else i.top(x0 + 1.5f, y0 + 0.3f, 17f, 0.4f, 0.4f, Pal.STONE_X)
        if (forge) {
            val cx = i.sx(x0 + 1.7f, y0 + 0.5f); val cy = i.sy(x0 + 1.7f, y0 + 0.5f, 17f)
            s.smoke(cx, cy, 0.8f, b.plot * 5); s.sparks(cx, cy, 3, b.plot)
        }
    }

    /** The Lake District's oak on the green: a short broad trunk, the wide low crown (russet late in autumn), the bench. */
    private fun oak(b: Building, x: Float, y: Float) {
        val cx = x + 1.5f; val cy = y + 1.5f
        i.box(cx - 0.28f, cy - 0.28f, 0f, 0.56f, 0.56f, 8f, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        if (fine) { i.line(cx - 0.15f, cy + 0.28f, 1f, cx - 0.15f, cy + 0.28f, 7f, Pal.LOG_D); i.line(cx + 0.28f, cy + 0.1f, 2f, cx + 0.28f, cy + 0.1f, 6f, Col.scale(Pal.LOG_D, 0.8f)) }
        // the first boughs out of the trunk
        i.line(cx, cy, 7f, cx - 0.9f, cy + 0.2f, 10.5f, Pal.LOG_M); i.line(cx, cy, 7.5f, cx + 0.8f, cy - 0.3f, 11f, Pal.LOG_D)
        val sx = i.sx(cx, cy); val sy = i.sy(cx, cy, 16.5f)
        s.trees.oak(sx, sy, 1.25f * k, env.season, b.plot * 11)
        i.box(x + 0.8f, y + 2.45f, 1.5f, 1.4f, 0.4f, 0.5f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        i.post(x + 0.9f, y + 2.85f, 0f, 1.5f, Pal.WOOD_X); i.post(x + 2.1f, y + 2.85f, 0f, 1.5f, Pal.WOOD_X)
        i.panelY(x + 0.8f, y + 2.45f, 2f, 1.4f, 1.5f, Pal.WOOD_M)
        if (fine) i.line(x + 0.8f, y + 2.45f, 2.75f, x + 2.2f, y + 2.45f, 2.75f, Pal.WOOD_D)
        if (b.level >= 2 && !env.snow) p.flowers(x + 0.5f, y + 1.5f, b.plot * 5)
        if (s.classroom) p.classroom(x, y)
    }

    /**
     * The school as the village builds it. Friuli's ([italian]): ochre plaster under coppi, a plaque of pale stone over the
     * door and the tricolore on its bracket above it, no bell turret. The Lake District's: grey stone under slate with white
     * window frames, a board over the door and the stone bellcote on the ridge. Both keep the stove's chimney.
     */
    private fun school(b: Building, x: Float, y: Float, st: FireStyle, italian: Boolean) {
        val x0 = x + 0.25f; val y0 = y + 0.25f; val w = 2.5f
        val t = if (italian) intArrayOf(Pal.OCHRE_L, Pal.OCHRE_M, Pal.OCHRE_D) else st.walls[0]
        val frame = if (italian) ISTRIA_L else WHITE_FRAME
        i.box(x0, y0, 0f, w, w, 2f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        p.mortar(x0, y0, 0f, w, w, 2f, Pal.STONE_D, Pal.STONE_X)
        if (italian) plaster(x0, y0, 2f, w, w, 11f, t, b.plot * 67) else stoneBox(x0, y0, 2f, w, w, 11f, t, b.plot * 67)
        if (italian) { i.line(x0, y0 + w, 7f, x0 + w, y0 + w, 7f, Pal.WALL_L); i.line(x0 + w, y0, 7f, x0 + w, y0 + w, 7f, Pal.WALL_M) }
        for (z in floatArrayOf(3f, 8.5f)) {
            val low = z < 5f
            winY(x0 + 0.3f, y0 + w, z, 0.45f, 3f, if (italian) st.shutter else null, frame, low); winY(x0 + 1.75f, y0 + w, z, 0.45f, 3f, if (italian) st.shutter else null, frame, low)
            for (v in floatArrayOf(0.3f, 1.05f, 1.8f)) winX(x0 + w, y0 + v, z, 0.45f, 3f, null, frame, low && v < 1.5f)
        }
        p.doorY(x0 + 1.0f, y0 + w, 2f, 0.55f, 5f)
        i.box(x0 + 0.85f, y0 + w, 0f, 0.85f, 0.35f, 2f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        if (italian) {
            // the plaque, its lettering; the tricolore on its bracket over it
            i.panelY(x0 + 0.92f, y0 + w, 7.6f, 0.7f, 1.1f, ISTRIA_L)
            if (fine) i.line(x0 + 1.0f, y0 + w, 8.1f, x0 + 1.55f, y0 + w, 8.1f, Col.hex(0x3A3A40))
            val bx = x0 + 1.27f; val by = y0 + w
            i.line(bx, by, 9f, bx, by + 0.55f, 11f, Pal.WOOD_X)
            val ax = i.ix(bx, by + 0.55f); val ay = i.iy(bx, by + 0.55f, 11f)
            val wave = ((s.time * 3).toInt() and 1)
            s.sprite(ax, ay) { for (u in 0 until 3) for (v in 0 until 3) c.set(ax - 1 - u, ay + v + if (u == 2) wave else 0, Flag.ITALY.at(u, v, 3, 3)) }
        } else {
            i.panelY(x0 + 0.95f, y0 + w, 8.5f, 0.65f, 1.6f, Col.hex(0x26302C))
            if (fine) c.set(i.ix(x0 + 1.27f, y0 + w), i.iy(x0 + 1.27f, y0 + w, 9.3f), Pal.GOLD_L)
        }
        val r = roofOf(st)
        p.roofX(x0 - 0.35f, y0 - 0.35f, 13f, w + 0.7f, w + 0.7f, if (italian) 5f else 6.5f, r, t[2])
        if (!italian) {
            // the bellcote on the ridge: a little gable of stone with its opening and the bell
            val bx = x0 + 0.6f; val by = y0 + 1.0f
            i.box(bx, by, 18.5f, 0.35f, 0.5f, 3.2f, t[0], t[1], t[2])
            i.panelX(bx + 0.35f, by + 0.15f, 19.3f, 0.2f, 1.4f, Col.hex(0x1E1A18))
            i.px(bx + 0.35f, by + 0.25f, 19.6f, Pal.GOLD)
            i.tri(bx + 0.35f, by, 21.7f, bx + 0.35f, by + 0.5f, 21.7f, bx + 0.35f, by + 0.25f, 23f, t[2])
        }
        p.chimney(x0 + 2.0f, y0 + 0.35f, if (italian) 13.5f else 14f, 6f, p.smoking)
    }

    internal companion object {
        /** A window frame's width beyond the glass, in cells (as the map's own windows'). */
        const val FRAME = 0.125f

        private fun t(l: Long, m: Long, d: Long) = intArrayOf(Col.hex(l), Col.hex(m), Col.hex(d))

        /** River cobbles (the fienile's, Friuli's smithy's), a darker one, the bands of brick. */
        val COB = t(0xD6CFC0, 0xB9B0A0, 0x8F8676)
        val COB_D = Col.hex(0x7E7666)
        val BRICK_L = Col.hex(0xC0663E); val BRICK_M = Col.hex(0xA4553A); val BRICK_D = Col.hex(0x8A4430)
        /** The pale stone of Friuli's frames, portals and wells (Istrian stone, the piasentina). */
        val ISTRIA_L = Col.hex(0xEDE7DA); val ISTRIA_M = Col.hex(0xD2CAB8); val ISTRIA_D = Col.hex(0xA89E8A)
        val IRON = Col.hex(0x2E2A30)
        val COPPER_L = Col.hex(0xE09A5A); val COPPER_M = Col.hex(0xB8743E); val COPPER_D = Col.hex(0x8A5428)
        val GRAPE = Col.hex(0x5A2E6A)
        /** The Lake District's window frames, painted white. */
        val WHITE_FRAME = Col.hex(0xF2F0EA)
        val BARN_DOOR = Col.hex(0x4E3A2A)
        /** The village pump's cast iron, painted dark green. */
        val PUMP_L = Col.hex(0x3E5A4A); val PUMP_M = Col.hex(0x2C4236); val PUMP_D = Col.hex(0x1E2E26)
        /** Carinthia's upper storey of dark timber. */
        val TIMBER = t(0x8A5E3A, 0x6A4426, 0x4A2E1A)
        /** Reed thatch, the casoni's. */
        val THATCH_L = Col.hex(0xD8C07A); val THATCH_M = Col.hex(0xB89A58); val THATCH_D = Col.hex(0x8E7440)
        val CASONE_WALL = t(0xE6DCC4, 0xCFC2A4, 0xA89A7C)
        /** A hut's rubble, warm grey. */
        val HUT_STONE = intArrayOf(Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        /** The white of the WBC hives. */
        val WBC = t(0xF6F4EC, 0xDEDACE, 0xB8B2A2)
        /** Where the three WBC hives stand on their plot (cells from its corner). */
        val WBC_AT = arrayOf(floatArrayOf(0.45f, 1.25f), floatArrayOf(1.3f, 1.55f), floatArrayOf(2.15f, 1.25f))
        /** The plain hives' paint: yellow, sky blue, sage, cream, a soft orange. */
        val HIVES = intArrayOf(Col.hex(0xF2D04A), Col.hex(0x7FB2D8), Col.hex(0x8CC07A), Col.hex(0xF4EEDD), Col.hex(0xE89A6A))
        /** How high the hay hangs in the Harpfe's bays (px). */
        val HARPFE_HAY = floatArrayOf(8f, 6.5f, 9f, 7f)
    }
}

/**
 * The tops of a culture's towers on the map, shared by its church and its town clock (the projects' landmark): the onion
 * dome of green copper, and the Lake District's battlemented tower.
 */
internal object TowerTops {
    /** The onion dome's half width by row from its foot (nominal px). */
    private val DOME = floatArrayOf(4.4f, 5.2f, 5.6f, 5.5f, 5f, 4.1f, 2.9f, 1.9f, 1.1f, 1f, 1.4f, 1.1f)

    /** An onion dome of [tones] (light, middle, dark: green copper) on a tower's top at ([ax], [ay]), its lantern, a gilt ball and the cross. */
    fun onion(s: SceneCtx, ax: Int, ay: Int, tones: IntArray) {
        val c = s.canvas; val k = s.k; val env = s.env
        val light = Col.mix(tones[0], Col.hex(0xFFFFFF), 0.3f)
        for (row in 0 until DOME.size * k) {
            val r = row / k
            val hw = DOME[r] * k
            val yy = ay - row
            for (xx in (ax - hw).roundToInt() until (ax + hw).roundToInt()) {
                val rel = (xx + 0.5f - ax) / max(hw, 1f)
                var col = when { rel < -0.4f -> tones[0]; rel < 0.35f -> tones[1]; else -> tones[2] }
                if (k >= 2 && r in 1..5 && rel in -0.32f..-0.12f) col = light
                if (env.snow && r in 3..5 && rel < 0.45f) col = if (rel < -0.2f) Pal.SNOW_L else Pal.SNOW_M
                c.set(xx, yy, col)
            }
        }
        val top = ay - DOME.size * k
        s.sprite(ax, top) {
            c.set(ax, top - 1, Pal.GOLD_L); c.set(ax - 1, top - 1, Pal.GOLD)
            c.vline(ax, top - 4, top - 2, Pal.GOLD); c.hline(ax - 1, ax + 1, top - 3, Pal.GOLD)
        }
    }

    /**
     * A square tower of stone on [tx], [ty] ([tw] square, [h] to its parapet) in [t]'s tones: its stones and mortar, the
     * belfry's louvres on both faces we see, and the battlements along its top with a pinnacle at each corner.
     */
    fun battlemented(s: SceneCtx, p: BuildingPainter, tx: Float, ty: Float, tw: Float, h: Float, t: IntArray, seed: Int) {
        val i = s.iso
        i.box(tx, ty, 0f, tw, tw, h, t[0], t[1], t[2])
        p.speckle(tx, ty, 0f, tw, tw, h, seed * 7 + 3, Col.scale(t[1], 0.82f), Col.scale(t[2], 0.82f))
        p.mortar(tx, ty, 0f, tw, tw, h, Col.scale(t[1], 0.86f), Col.scale(t[2], 0.86f))
        val dark = Col.hex(0x26282C)
        i.panelY(tx + tw / 2 - 0.2f, ty + tw, h - 5f, 0.4f, 2.6f, dark); i.panelX(tx + tw, ty + tw / 2 - 0.2f, h - 5f, 0.4f, 2.6f, dark)
        if (s.k >= 2) { i.line(tx + tw / 2 - 0.2f, ty + tw, h - 3.9f, tx + tw / 2 + 0.2f, ty + tw, h - 3.9f, t[1]); i.line(tx + tw, ty + tw / 2 - 0.2f, h - 3.9f, tx + tw, ty + tw / 2 + 0.2f, h - 3.9f, t[2]) }
        // the string course and the parapet, a little proud of the walls
        i.box(tx - 0.06f, ty - 0.06f, h, tw + 0.12f, tw + 0.12f, 1.4f, Col.mix(t[0], Col.hex(0xFFFFFF), 0.15f), t[1], t[2])
        val snow = s.env.snow
        // the merlons along the two front edges and the corner pinnacles
        val n = 4
        for (q in 0..n) {
            val u = q * (tw + 0.12f) / n
            if (q % 2 == 0) {
                i.box(tx - 0.06f + u - 0.08f, ty + tw + 0.06f - 0.16f, h + 1.4f, 0.16f, 0.16f, 1.2f, if (snow) Pal.SNOW_L else t[0], t[1], t[2])
                i.box(tx + tw + 0.06f - 0.16f, ty - 0.06f + u - 0.08f, h + 1.4f, 0.16f, 0.16f, 1.2f, if (snow) Pal.SNOW_L else t[0], t[1], t[2])
            }
        }
        for ((px, py) in listOf(tx - 0.02f to ty + tw - 0.12f, tx + tw - 0.12f to ty - 0.02f, tx + tw - 0.12f to ty + tw - 0.12f)) {
            i.box(px, py, h + 1.4f, 0.14f, 0.14f, 2.6f, t[0], t[1], t[2])
            i.px(px + 0.07f, py + 0.07f, h + 4.2f, if (snow) Pal.SNOW_L else t[0])
        }
    }
}
