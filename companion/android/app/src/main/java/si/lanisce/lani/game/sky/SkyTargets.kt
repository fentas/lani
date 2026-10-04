package si.lanisce.lani.game.sky

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** What a tap on the night sky landed on: its card says what it is (see [SkyCards]). */
sealed interface SkyTap {
    /** The moon: its phase, its days, when it rises and sets. */
    data object Moon : SkyTap

    /** A star of the catalog (its Harvard Revised number), of the figure [figure] (a constellation's, or the Pleiades'), if any. */
    data class Star(val hr: Int, val figure: String?) : SkyTap

    /** A constellation's figure, tapped on a line between its stars. */
    data class Figure(val id: String) : SkyTap

    /** A planet ([Planet.id]): Venus, Mars, Jupiter, Saturn. */
    data class Planet(val id: String) : SkyTap

    /** The Milky Way. */
    data object MilkyWay : SkyTap

    /** A shooting star (its [Meteor.id]), of a shower or none. */
    data class Meteor(val id: Long, val shower: String?) : SkyTap

    /** The id the picture marks while this one's card is open: the figure's, a planet's, "moon", "milky-way"; null for none. */
    val mark: String?
        get() = when (this) {
            Moon -> "moon"
            is Star -> figure
            is Figure -> id
            is Planet -> id
            MilkyWay -> "milky-way"
            is Meteor -> null
        }
}

/**
 * The tappable things of the sky of the last picture drawn, in the picture's nominal pixels (the village's nominal
 * canvas, a scene's canvas at detail 1): the moon, the stars of the figures and the named ones, the planets, the
 * figures' lines, the Milky Way and the shooting stars. What hills, clouds or the rain hide isn't there to tap: the
 * painter settles that once the picture is done ([seen], [isSky]).
 */
class SkyTargets {
    private var moonX = Float.NaN
    private var moonY = 0f
    private var moonR = 0f
    /** The moon is drawn and not hidden. */
    var moonSeen = false

    private var n = 0
    private var px = FloatArray(64)
    private var py = FloatArray(64)
    /** A star's index in [Stars.all], or −1 − a planet's ordinal. */
    private var what = IntArray(64)
    private var seenAt = BooleanArray(64)

    private var nl = 0
    private var lines = FloatArray(64)
    private var lineFigure = IntArray(16)

    private val meteors = ArrayList<Pair<Meteor, FloatArray>>()

    /** How bright the Milky Way is at a nominal point (0 none .. 1), or null while it doesn't show. */
    var milkyWay: ((Float, Float) -> Float)? = null

    /** Whether the nominal point is still sky in the finished picture (no hill, cloud or rain drawn over it). */
    var isSky: (Float, Float) -> Boolean = { _, _ -> true }

    fun clear() {
        moonX = Float.NaN; moonSeen = false
        n = 0; nl = 0
        meteors.clear()
        milkyWay = null
        isSky = { _, _ -> true }
    }

    fun moon(x: Float, y: Float, r: Float) { moonX = x; moonY = y; moonR = r; moonSeen = true }

    /** A star ([index] in [Stars.all]) drawn at ([x], [y]); returns its slot, for [seen]. */
    fun star(index: Int, x: Float, y: Float): Int = point(index, x, y)

    fun planet(p: Planet, x: Float, y: Float): Int = point(-1 - p.ordinal, x, y)

    private fun point(w: Int, x: Float, y: Float): Int {
        if (n == px.size) { px = px.copyOf(n * 2); py = py.copyOf(n * 2); what = what.copyOf(n * 2); seenAt = seenAt.copyOf(n * 2) }
        px[n] = x; py[n] = y; what[n] = w; seenAt[n] = true
        return n++
    }

    /** Whether the star or planet of [slot] is still to be seen in the finished picture. */
    fun seen(slot: Int, visible: Boolean) { if (slot in 0 until n) seenAt[slot] = visible }

    /** A line of figure [figure] (its index in [Stars.figures]) from ([x0], [y0]) to ([x1], [y1]). */
    fun line(figure: Int, x0: Float, y0: Float, x1: Float, y1: Float) {
        if (nl * 4 + 4 > lines.size) lines = lines.copyOf(lines.size * 2)
        if (nl == lineFigure.size) lineFigure = lineFigure.copyOf(nl * 2)
        lines[nl * 4] = x0; lines[nl * 4 + 1] = y0; lines[nl * 4 + 2] = x1; lines[nl * 4 + 3] = y1
        lineFigure[nl++] = figure
    }

    /** A shooting star drawn from ([x0], [y0]) toward ([x1], [y1]). */
    fun meteor(m: Meteor, x0: Float, y0: Float, x1: Float, y1: Float) { meteors.add(m to floatArrayOf(x0, y0, x1, y1)) }

    /** Once the picture is done: a shooting star whose way lies all behind the hills isn't there to tap. */
    fun settleMeteors() {
        meteors.retainAll { (_, s) -> (0..8).any { j -> val u = j / 8f; isSky(s[0] + (s[2] - s[0]) * u, s[1] + (s[3] - s[1]) * u) } }
    }

    /** Everything drawn, for tests: the stars' and planets' points still seen. */
    fun points(): List<Pair<Int, FloatArray>> = (0 until n).filter { seenAt[it] }.map { what[it] to floatArrayOf(px[it], py[it]) }

    /** The shooting stars of the picture and their way across it (x0, y0, x1, y1), for tests. */
    fun meteors(): List<Pair<Meteor, FloatArray>> = meteors.toList()

    /** Where the moon was drawn (nominal x, y, radius), when it is seen. */
    fun moonAt(): FloatArray? = if (moonSeen && !moonX.isNaN()) floatArrayOf(moonX, moonY, moonR) else null

    /**
     * What the picture shows now to open, each once, for TalkBack ("🌌 Nebo nocoj · Tonight's sky": the sky is drawn in
     * pixels): the moon, the planets, the figures whose stars are seen, the named stars, the Milky Way. A shooting star
     * is gone before it could be picked from a list: none.
     */
    fun up(): List<SkyTap> {
        val out = LinkedHashSet<SkyTap>()
        if (moonSeen && !moonX.isNaN()) out += SkyTap.Moon
        for (i in 0 until n) if (seenAt[i] && what[i] < 0) out += SkyTap.Planet(Planet.entries[-1 - what[i]].id)
        for (i in 0 until n) if (seenAt[i] && what[i] >= 0) Stars.figureOf(what[i])?.let { out += SkyTap.Figure(it.id) }
        for (i in 0 until n) {
            if (!seenAt[i] || what[i] < 0 || Stars.all[what[i]].proper == null) continue
            out += SkyTap.Star(Stars.all[what[i]].hr, Stars.figureOf(what[i])?.id)
        }
        if (milkyWay != null) out += SkyTap.MilkyWay
        return out.toList()
    }

    /**
     * What is at nominal point ([x], [y]) at [wall] (epoch ms), within [slop] px: a shooting star in flight (or just gone,
     * generously), the moon, a planet, a star (the nearest), a figure's line, the Milky Way; null for plain sky or none.
     */
    fun at(x: Float, y: Float, slop: Float, wall: Long): SkyTap? {
        for ((m, s) in meteors) {
            if (wall < m.start - 200 || wall > m.end + Meteors.GRACE_MS) continue
            if (segment(x, y, s[0], s[1], s[2], s[3]) <= slop * 2.2f) return SkyTap.Meteor(m.id, m.shower)
        }
        if (moonSeen && !moonX.isNaN() && hypot(x - moonX, y - moonY) <= moonR + slop) return SkyTap.Moon
        var best = -1; var bestD = Float.MAX_VALUE
        for (i in 0 until n) {
            if (!seenAt[i]) continue
            // a planet reaches a little further: a bright point alone
            val reach = if (what[i] < 0) slop * 1.3f else slop
            val d = hypot(x - px[i], y - py[i])
            if (d <= reach && d < bestD) { bestD = d; best = i }
        }
        if (best >= 0) {
            val w = what[best]
            if (w < 0) return SkyTap.Planet(Planet.entries[-1 - w].id)
            return SkyTap.Star(Stars.all[w].hr, Stars.figureOf(w)?.id)
        }
        if (!isSky(x, y)) return null
        var line = -1; var lineD = Float.MAX_VALUE
        for (j in 0 until nl) {
            val d = segment(x, y, lines[j * 4], lines[j * 4 + 1], lines[j * 4 + 2], lines[j * 4 + 3])
            if (d <= slop * 0.75f && d < lineD) { lineD = d; line = j }
        }
        if (line >= 0) return SkyTap.Figure(Stars.figures[lineFigure[line]].id)
        if ((milkyWay?.invoke(x, y) ?: 0f) >= MILKY_WAY_TAP) return SkyTap.MilkyWay
        return null
    }

    companion object {
        /** How bright the Milky Way must be where it is tapped. */
        const val MILKY_WAY_TAP = 0.3f

        /** The distance from ([x], [y]) to the segment ([x0], [y0])–([x1], [y1]). */
        fun segment(x: Float, y: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float {
            val dx = x1 - x0; val dy = y1 - y0
            val len2 = dx * dx + dy * dy
            val u = if (len2 <= 0f) 0f else max(0f, min(1f, ((x - x0) * dx + (y - y0) * dy) / len2))
            return hypot(x - (x0 + u * dx), y - (y0 + u * dy))
        }
    }
}
