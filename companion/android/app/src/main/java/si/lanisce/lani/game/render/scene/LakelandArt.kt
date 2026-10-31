package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import kotlin.math.abs
import kotlin.math.floor

/**
 * The Lake District's materials as the painters draw them in its look (companion/SCENES.md "Culture looks"; the
 * `LAKELAND_*` tables of [si.lanisce.lani.game.scene.CultureLooks]; docs/plans/05-built/places-lakeland.md): the dale's
 * slate stone, laid in thin courses without much mortar (its cottages, barns, smithies and cellars); the slate flags of
 * its floors; its dry-stone walls; the oak of its furniture. Pure: each a colour for a point of a surface, read by the
 * painters where their own material was.
 */
internal object Lakeland {
    /** The slate of the roofs (the fire's village's, FireStyle's: Pal.SLATE_*), and a lighter one where the sky catches it. */
    val ROOF_L = Pal.SLATE_L; val ROOF_M = Pal.SLATE_M; val ROOF_D = Pal.SLATE_D

    /** The dale's walling stone: blue-grey with a green cast, some stones browner or rusty. */
    private val STONE = intArrayOf(Col.hex(0x8C949A), Col.hex(0x7A848C), Col.hex(0x969C9A), Col.hex(0x6E7880), Col.hex(0x84888A), Col.hex(0x8E8A80))
    private val JOINT = Col.hex(0x40464C)
    /** The lime the joints are pointed with, where a wall is mortared ([slateStone]'s mortar). */
    private val MORTAR = Col.hex(0xC4C2B8)

    /** The oak of the dressers, the chests and the settles: light, middle, dark, and the shadow behind a dresser's plates. */
    val OAK_L = Col.hex(0xA87A4C); val OAK_M = Col.hex(0x825A34); val OAK_D = Col.hex(0x5E3E22); val OAK_BACK = Col.hex(0x4A3220)

    /** The dresser's plates and the plate rail's: white with a blue rim and a blue willow pattern in the middle. */
    val WILLOW = Col.hex(0x2E5A9E)

    /**
     * A wall of slate stone at [along] cells and [h] nominal px up (a room's wall, a smithy's, a barn's): thin courses of
     * long and short stones, their tops lit, dark joints between them with little mortar, a stone here and there browner or
     * greener; closer up ([fine]) the stones' split faces with a lighter edge and a fleck.
     */
    fun slateStone(along: Float, h: Float, px: Int, py: Int, fine: Boolean, mortar: Boolean = false): Int {
        val joint = if (mortar) MORTAR else JOINT
        val ch = 1.35f
        val course = floor(h / ch).toInt()
        val fv = h / ch - course
        val off = Noise.rnd(course, 211) * 0.6f
        val len = 0.32f + 0.38f * Noise.rnd(course, 213)
        val u = (along + off) / len
        val k = floor(u).toInt(); val fu = u - k
        // the joints: under each course, and between the stones of a course (some butted tight)
        if (fv < (if (mortar) 0.2f else 0.16f)) return joint
        val tight = !mortar && Noise.hash(k, course, 215) % 3 == 0
        if (!tight && (fu < (if (mortar) 0.09f else 0.07f) || fu > 0.95f)) return joint
        var col = STONE[Math.floorMod(Noise.hash(k, course * 7 + 1, 217), STONE.size)]
        // the course's top lit, its foot a little in shadow
        if (fv > 0.82f) col = Col.mix(col, Col.hex(0xC8D0D4), 0.22f) else if (fv < 0.3f) col = Col.scale(col, 0.88f)
        if (fine) {
            val n = Noise.v2(along * 9f, h * 1.6f, 219)
            if (n > 0.74f) col = Col.scale(col, 0.92f) else if (n < 0.2f) col = Col.mix(col, Col.hex(0xD8DCDC), 0.12f)
            if (fu < 0.14f && !tight) col = Col.mix(col, Col.hex(0xB8C0C4), 0.3f)
        }
        if (Noise.rnd(px, py, 221) > 0.97f) col = Col.scale(col, 0.86f)
        return col
    }

    /**
     * A floor of slate flags at ([x], [y]) in cells: big blue-grey flagstones in rows of their own widths, dark joints, each
     * stone a shade of its own, worn paler where feet go; closer up ([fine]) the riven face's ripples.
     */
    fun slateFlags(x: Float, y: Float, px: Int, py: Int, fine: Boolean): Int {
        val rw = 1.15f
        val row = floor(y / rw).toInt()
        val fv = y / rw - row
        val off = Noise.rnd(row, 231) * 1.4f
        val len = 1.1f + 0.7f * Noise.rnd(row, 233)
        val u = (x + off) / len
        val k = floor(u).toInt(); val fu = u - k
        if (fv < 0.07f || fu < 0.05f) return Col.hex(0x3A4048)
        val tones = intArrayOf(Col.hex(0x6E7884), Col.hex(0x626C78), Col.hex(0x76808A), Col.hex(0x5A6470), Col.hex(0x6A7268))
        var col = tones[Math.floorMod(Noise.hash(k, row, 235), tones.size)]
        if (fv < 0.16f || fu < 0.1f) col = Col.mix(col, Col.hex(0xA8B4BC), 0.18f)
        else if (fv > 0.93f || fu > 0.96f) col = Col.scale(col, 0.88f)
        if (fine) {
            val r = Noise.v2(x * 5f + k, y * 2f, 237)
            if (abs(r - 0.5f) < 0.04f) col = Col.scale(col, 0.9f) else if (r > 0.82f) col = Col.mix(col, Col.hex(0xB0BAC2), 0.1f)
        }
        val n = Noise.rnd(px, py, 239)
        return if (n > 0.98f) Col.scale(col, 0.85f) else if (n < 0.02f) Col.mix(col, Col.hex(0xB8C2CA), 0.3f) else col
    }

    /**
     * A dry-stone wall's face at [along] (in the painter's units across) and [h] (0 at its foot, [top] at its top), as the
     * dale's walls are built: rough stones laid without mortar in two faces, dark gaps between them, a row of through-stones
     * a little proud half-way up, the cope stones set on edge along the top; [scale] stones a unit; [dim] shades the face.
     */
    fun drystone(along: Float, h: Float, top: Float, scale: Float, px: Int, py: Int, dim: Float = 1f): Int {
        val coping = top - 0.22f * top
        if (h > coping) {
            // the cope stones on edge: narrow upright slabs side by side
            val u = along * scale * 2.2f
            val k = floor(u).toInt()
            if (u - k < 0.16f) return Col.scale(JOINT, dim)
            val col = STONE[Math.floorMod(Noise.hash(k, 241), STONE.size)]
            return Col.scale(if (h > top - 0.08f * top) Col.mix(col, Col.hex(0xC8D0D4), 0.25f) else col, dim)
        }
        val ch = top * 0.2f
        val course = floor(h / ch).toInt()
        val fv = h / ch - course
        val u = (along * scale + Noise.rnd(course, 243) * 1.7f) / (0.7f + 0.6f * Noise.rnd(course, 245))
        val k = floor(u).toInt(); val fu = u - k
        if (fv < 0.18f || fu < 0.1f || (fu > 0.9f && Noise.hash(k, course, 247) % 2 == 0)) return Col.scale(Col.hex(0x2E3236), dim)
        var col = STONE[Math.floorMod(Noise.hash(k, course * 5 + 3, 249), STONE.size)]
        if (fv > 0.78f) col = Col.mix(col, Col.hex(0xC8D0D4), 0.2f)
        if (Dither.at(px, py) < 0.08f) col = Col.scale(col, 0.88f)
        return Col.scale(col, dim)
    }
}
