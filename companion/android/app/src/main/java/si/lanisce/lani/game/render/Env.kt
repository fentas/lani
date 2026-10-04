package si.lanisce.lani.game.render

import si.lanisce.lani.game.sky.SkyNow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The village palette: warm, alpine, a few accents from the app theme (flag blue, Triglav red, gold). */
object Pal {
    val OUTLINE = Col.hex(0x2A1D1A)
    val OUTLINE_TREE = Col.hex(0x14261A)

    val WALL_L = Col.hex(0xF6F1E4); val WALL_M = Col.hex(0xE0D7C3); val WALL_D = Col.hex(0xB9AD95)
    val OCHRE_L = Col.hex(0xF2DDA0); val OCHRE_M = Col.hex(0xDDC27E); val OCHRE_D = Col.hex(0xB49A5E)
    val ROOF_L = Col.hex(0xB5553A); val ROOF_M = Col.hex(0x96412A); val ROOF_D = Col.hex(0x6E2C1C)
    val SHINGLE_L = Col.hex(0x7C6552); val SHINGLE_M = Col.hex(0x654F3F); val SHINGLE_D = Col.hex(0x4A3A2F)
    val SLATE_L = Col.hex(0x6F7482); val SLATE_M = Col.hex(0x575B68); val SLATE_D = Col.hex(0x40434E)
    val WOOD_L = Col.hex(0xC08850); val WOOD_M = Col.hex(0x9A673A); val WOOD_D = Col.hex(0x6F4527); val WOOD_X = Col.hex(0x4A2D1A)
    val LOG_L = Col.hex(0xA87444); val LOG_M = Col.hex(0x8A5A32); val LOG_D = Col.hex(0x643F22)
    val STONE_L = Col.hex(0xB8B2A6); val STONE_M = Col.hex(0x96907F); val STONE_D = Col.hex(0x716C62); val STONE_X = Col.hex(0x57534C)
    val CANVAS_L = Col.hex(0xF1E3BA); val CANVAS_M = Col.hex(0xD9C28E); val CANVAS_D = Col.hex(0xB39C6A)
    val GLASS = Col.hex(0x34465A); val GLASS_HI = Col.hex(0x7F9CB8)
    val WINDOW_LIT = Col.hex(0xFFD77A); val WINDOW_LIT_HI = Col.hex(0xFFF1C2)
    val SHUTTER = Col.hex(0x3F6B3A)
    val DOOR = Col.hex(0x4A2E1C)
    val HAY_L = Col.hex(0xE8D27A); val HAY_M = Col.hex(0xC9AE55); val HAY_D = Col.hex(0x9C853A)
    val SOIL_L = Col.hex(0x8C6440); val SOIL_M = Col.hex(0x6E4C30); val SOIL_D = Col.hex(0x533823)
    val DIRT_L = Col.hex(0xC49A68); val DIRT_M = Col.hex(0xA98052); val DIRT_D = Col.hex(0x8A6540)
    val COBBLE_L = Col.hex(0xB9B4A8); val COBBLE_M = Col.hex(0x9C978B); val COBBLE_D = Col.hex(0x77736A)
    val WATER_L = Col.hex(0x6FB3E6); val WATER_M = Col.hex(0x3F84C4); val WATER_D = Col.hex(0x2B5F96)
    val ICE_L = Col.hex(0xDDEFFA); val ICE_M = Col.hex(0xB8D6EC)
    val SNOW_L = Col.hex(0xF6F9FF); val SNOW_M = Col.hex(0xDDE7F3); val SNOW_D = Col.hex(0xB9CAE0)
    val SKIN = Col.hex(0xF0C09A); val SKIN_D = Col.hex(0xC98E6A)
    val GOLD = Col.hex(0xFFB300); val GOLD_L = Col.hex(0xFFE08A)
    val FLAG_BLUE = Col.hex(0x0B4EA2); val FLAG_RED = Col.hex(0xE4002B); val FLAG_WHITE = Col.hex(0xF7F9FD)
    val GERANIUM = Col.hex(0xE8303A); val GERANIUM_D = Col.hex(0xA81E2A); val LEAF = Col.hex(0x3E8A3A)
    val FLAME = intArrayOf(Col.hex(0xFFF6C8), Col.hex(0xFFD74A), Col.hex(0xFF9A1F), Col.hex(0xE8501A), Col.hex(0xA8301A))
    val SMOKE = Col.hex(0x9A9AA2)

    val SHIRTS = intArrayOf(
        Col.hex(0xC8392F), Col.hex(0x2E63B0), Col.hex(0x3E8E4E), Col.hex(0xE9E2D0),
        Col.hex(0xD4972E), Col.hex(0x7A4E9A), Col.hex(0x2C7F86),
    )
    val HAIR = intArrayOf(Col.hex(0x4A2E1C), Col.hex(0x22181A), Col.hex(0xD7B25A), Col.hex(0x8A4A22), Col.hex(0xB5B0A8))
    val PAINTED = intArrayOf(
        Col.hex(0xD8392E), Col.hex(0xF0C23A), Col.hex(0x2F6FC0), Col.hex(0x3E9A52), Col.hex(0xF4EEDD), Col.hex(0xE07A2A),
    )
}

enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

/**
 * Grass, tree and sky colours for one frame; derived from the hour and the month, greyed and darkened by a [storm]
 * or, a share of that, by [gloom] 0..1 (an overcast sky a scene's dialog brought). The moon is [moonAge] days old
 * (see [Moon]; full when nobody says); with [real], the sky over the village at this minute, it is the real moon, up
 * where and when it is (see [SkyNow]).
 */
class Env(hour: Float, val month: Int, storm: Boolean, gloom: Float = 0f, moonAge: Float = Moon.FULL, val real: SkyNow? = null) {
    /** The moon's age as it is drawn: days since the new moon, from the real phase when there is one. */
    val moonAge: Float = real?.moonAge ?: moonAge

    val season: Season = when (month) {
        3, 4, 5 -> Season.SPRING
        6, 7, 8 -> Season.SUMMER
        9, 10, 11 -> Season.AUTUMN
        else -> Season.WINTER
    }
    val snow = season == Season.WINTER

    /** Sun elevation proxy: 1 at noon, 0 at sunrise/sunset, negative at night (down to -1). */
    val sun: Float
    val morning: Boolean
    val sunrise: Float
    val sunset: Float

    init {
        // Day length at ~46°N (Ljubljana), noon at 12:30 in summer time.
        val doy = (month - 1) * 30.4f + 15f
        val dl = 12.15f + 3.45f * cos((2 * PI * (doy - 172) / 365.0)).toFloat()
        val noon = if (month in 4..10) 13.1f else 12.1f
        sunrise = noon - dl / 2; sunset = noon + dl / 2
        val h = ((hour % 24f) + 24f) % 24f
        morning = h < noon
        sun = if (h in sunrise..sunset) sin(PI * (h - sunrise) / (sunset - sunrise)).toFloat()
        else {
            val dist = min(circ(h, sunrise), circ(h, sunset))
            -min(1f, dist / 1.6f)
        }
    }

    private fun circ(a: Float, b: Float): Float { val d = abs(a - b); return min(d, 24 - d) }

    /** 0 in daylight .. 1 in deep night. */
    val dark: Float = ((0.05f - sun) / 0.55f).coerceIn(0f, 1f)

    /** The share of the moon that is lit: 0 new .. 1 full (lit on the right while [Moon.waxing]). */
    val moonLit: Float = real?.moonLit ?: Moon.lit(this.moonAge)

    /**
     * Where the moon is on its way across the sky: 0 rising (on the left, like the sun) .. 1 setting; negative while
     * down. The real moon's is its azimuth's share of the way from the east to the west.
     */
    val moonArc: Float = when {
        real == null -> Moon.arc(this.moonAge, hour, (sunrise + sunset) / 2f, month)
        real.moonAlt < -0.3 -> -1f
        else -> ((real.moonAz - 90.0) / 180.0).toFloat().coerceIn(0f, 1f)
    }

    /** Its height along the way: 0 on the horizon .. 1 at its highest (the real moon's: 1 from 50° up). */
    val moonHeight: Float = if (real == null) Moon.height(moonArc) else (real.moonAlt / 50.0).toFloat().coerceIn(0f, 1f)

    /** The moon is up and not new: a painter draws it (by night; by day a faint ghost, where a scene shows one). */
    val moonShows: Boolean = real?.moonShows ?: (moonArc >= 0f && moonLit >= Moon.NEW_LIT)
    /** Windows light up from dusk. */
    val windows: Float = ((0.18f - sun) / 0.25f).coerceIn(0f, 1f)

    // Key frames by sun elevation: night, twilight, horizon, golden, day.
    private val keys = floatArrayOf(-0.55f, -0.18f, 0.0f, 0.2f, 0.5f)
    private val skyTopK = intArrayOf(0x0A0F24, 0x1E2556, 0x3A4E92, 0x5F8FD0, 0x4D93DC)
    private val skyMidEve = intArrayOf(0x141C3C, 0x523C7A, 0xB0648A, 0xE9B27E, 0x86BCEB)
    private val skyHorEve = intArrayOf(0x223058, 0xA25A6E, 0xF4905A, 0xFFC77A, 0xC4E2F6)
    private val skyMidMorn = intArrayOf(0x141C3C, 0x3E4A86, 0xC98AA6, 0xE9C9A6, 0x86BCEB)
    private val skyHorMorn = intArrayOf(0x223058, 0x7A6892, 0xFFB08A, 0xFFE0B0, 0xC4E2F6)
    private val ambK = arrayOf(
        floatArrayOf(0.26f, 0.31f, 0.50f), floatArrayOf(0.46f, 0.42f, 0.62f), floatArrayOf(0.82f, 0.64f, 0.66f),
        floatArrayOf(1.06f, 0.92f, 0.74f), floatArrayOf(1f, 1f, 1f),
    )

    private fun key(): Pair<Int, Float> {
        val s = sun.coerceIn(keys.first(), keys.last())
        var i = 0
        while (i < keys.size - 2 && s > keys[i + 1]) i++
        return i to ((s - keys[i]) / (keys[i + 1] - keys[i])).coerceIn(0f, 1f)
    }

    private fun lerpC(arr: IntArray): Int { val (i, t) = key(); return Col.mix(Col.hex(arr[i].toLong()), Col.hex(arr[i + 1].toLong()), t) }

    val skyTop: Int
    val skyMid: Int
    val skyHorizon: Int
    val ambR: Float
    val ambG: Float
    val ambB: Float

    init {
        var top = lerpC(skyTopK)
        var mid = lerpC(if (morning) skyMidMorn else skyMidEve)
        var hor = lerpC(if (morning) skyHorMorn else skyHorEve)
        val (i, t) = key()
        var ar = ambK[i][0] + (ambK[i + 1][0] - ambK[i][0]) * t
        var ag = ambK[i][1] + (ambK[i + 1][1] - ambK[i][1]) * t
        var ab = ambK[i][2] + (ambK[i + 1][2] - ambK[i][2]) * t
        if (storm) {
            val grey = Col.hex(0x5A6272)
            top = Col.mix(top, Col.scale(grey, 0.6f + 0.4f * (1 - dark)), 0.75f)
            mid = Col.mix(mid, Col.scale(grey, 0.7f + 0.3f * (1 - dark)), 0.7f)
            hor = Col.mix(hor, Col.scale(Col.hex(0x7A8292), 0.6f + 0.4f * (1 - dark)), 0.65f)
            ar *= 0.66f; ag *= 0.70f; ab *= 0.80f
        } else if (gloom > 0f) {
            // overcast: a lighter grey than the storm's, soon all of the sky
            val g = gloom.coerceAtMost(1f)
            val f = g * (2f - g)
            val lightness = 0.45f + 0.55f * (1 - dark)
            top = Col.mix(top, Col.scale(Col.hex(0x7C8594), lightness), 0.85f * f)
            mid = Col.mix(mid, Col.scale(Col.hex(0x8E96A3), lightness), 0.8f * f)
            hor = Col.mix(hor, Col.scale(Col.hex(0xA0A7B2), lightness), 0.75f * f)
            ar *= 1f - 0.3f * g; ag *= 1f - 0.27f * g; ab *= 1f - 0.18f * g
        }
        skyTop = top; skyMid = mid; skyHorizon = hor
        ambR = ar; ambG = ag; ambB = ab
    }

    /** Apply this frame's ambient light to a daylight colour (for far layers that skip the light pass). */
    fun lit(c: Int): Int = Col.mul(c, ambR, ambG, ambB)

    // ---- seasonal ground and foliage ----
    val grass: IntArray = when (season) {
        Season.SPRING -> ints(0x86C455, 0x74B24A, 0x60993D, 0xA3D66A)
        Season.SUMMER -> ints(0x72AC47, 0x5F983D, 0x4C8234, 0x8CC25A)
        Season.AUTUMN -> if (month == 9) ints(0x93A849, 0x7F9640, 0x6A7F37, 0xB1BC5A) else ints(0xA79E4E, 0x918844, 0x78703A, 0xC0B060)
        Season.WINTER -> ints(0xF3F7FE, 0xDDE7F3, 0xC3D2E6, 0xFFFFFF)
    }
    val forestFloor: IntArray = when (season) {
        Season.WINTER -> ints(0xD5E0EE, 0xBFCEE2, 0xA9BBD4)
        Season.AUTUMN -> ints(0x6E6A33, 0x5C582C, 0x7E5A2E)
        else -> ints(0x4D7A34, 0x406B2D, 0x355A26)
    }
    val pine: IntArray = ints(0x3B7A4A, 0x2C5E3A, 0x1E452C, 0x4E9460)

    private fun ints(vararg v: Int) = IntArray(v.size) { Col.hex(v[it].toLong()) }
}
