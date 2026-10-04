package si.lanisce.lani.game.render

import si.lanisce.lani.game.GameState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A wild animal of the woods round the village, and its word: what a tap on it looks up (the word pack
 * companion/packs/zivali-v-gozdu.json has them all, so the pack and the word card agree).
 */
enum class Wild(val word: String) {
    /** On a tree at the forest's edge, by day. */
    SQUIRREL("veverica"),
    /** In the meadow, by day and at dawn. */
    HARE("zajec"),
    /** A small flock over the far treeline, by day. */
    BIRDS("ptica"),
    /** Grazing at the forest's edge, at dawn and at dusk. */
    DEER("srna"),
    /** Slinking along outside the palisade, at dawn and dusk, sometimes at night. */
    FOX("lisica"),
    /** By a bush, at dusk and at night; asleep all winter. */
    HEDGEHOG("jež"),
    /** Over the roofs at dusk; asleep all winter. */
    BATS("netopir"),
    /** A sow and her young rooting at the meadow's edge, at night. */
    BOARS("divji prašič"),
    /** Snuffling along the forest's edge at night. */
    BADGER("jazbec"),
    /** On a branch at the forest's edge at night, its eyes glowing. */
    OWL("sova"),
    /** Very rarely at night: two eyes glowing at the forest's edge, nothing more. Asleep all winter. */
    BEAR("medved"),
    /** In winter: tracks in the snow, from the forest to the palisade and back. */
    TRACKS("sled");

    /** In the air or in a tree: the ones the palisade doesn't keep out. */
    val aloft: Boolean get() = this == BIRDS || this == BATS || this == OWL || this == SQUIRREL
}

/** The parts of the day the animals keep to (see [Wildlife.time]). */
enum class WildTime { DAWN, DAY, DUSK, NIGHT }

/** Who is about in a window: [kind], how many ([n]: a flock, a sow and her young), where ([pick] 0..1: which of its places) and its own [seed]. */
data class Sighting(val kind: Wild, val n: Int, val pick: Float, val seed: Int)

/**
 * The wild animals round the village on the map. Different by the time of day ([WildTime]: a squirrel, a hare and birds by
 * day; roe deer, a fox and a hedgehog at dawn and dusk, bats over the roofs at dusk; a boar family, a badger and an owl
 * at night, very rarely a bear's eyes) and by the season (fewer in winter, the sleepers asleep, tracks in the snow), and
 * different from day to day: each window of each day draws its own mix from its candidates, seeded by the village's seed,
 * the date and the window, so it holds through the window (every frame and every detail level agree) and changes the
 * next day. Some windows nothing shows. Rare ones are rarer (the bear's eyes), common ones common (deer at dawn).
 *
 * They keep outside the palisade, at the forest's edge and in the meadow of the woods below (see [WildPlaces]): the
 * palisade is why they stay out. Only the ones that fly or sit in the trees ([Wild.aloft]) come over it.
 */
object Wildlife {
    /** Dawn is from this long before sunrise (hours) to [TWILIGHT_IN] after it; dusk from [TWILIGHT_IN] before sunset to this long after. */
    private const val TWILIGHT_OUT = 1f
    private const val TWILIGHT_IN = 1.25f

    /** How often a window has no animals at all. */
    private const val QUIET = 0.12f

    /** At most this many kinds in a window. */
    private const val MAX_KINDS = 3

    /** How often the snow shows tracks, in winter. */
    private const val TRACKS_CHANCE = 0.75f

    private class Chance(val kind: Wild, val p: Float)

    /** Each window's candidates and how likely each is on a given day. */
    private val CANDIDATES: Map<WildTime, List<Chance>> = mapOf(
        WildTime.DAY to listOf(Chance(Wild.SQUIRREL, 0.65f), Chance(Wild.HARE, 0.5f), Chance(Wild.BIRDS, 0.6f)),
        WildTime.DAWN to listOf(Chance(Wild.DEER, 0.8f), Chance(Wild.FOX, 0.4f), Chance(Wild.HARE, 0.35f), Chance(Wild.HEDGEHOG, 0.3f)),
        WildTime.DUSK to listOf(Chance(Wild.DEER, 0.55f), Chance(Wild.BATS, 0.7f), Chance(Wild.FOX, 0.45f), Chance(Wild.HEDGEHOG, 0.45f)),
        WildTime.NIGHT to listOf(
            Chance(Wild.OWL, 0.6f), Chance(Wild.BOARS, 0.45f), Chance(Wild.BADGER, 0.35f), Chance(Wild.HEDGEHOG, 0.25f),
            Chance(Wild.FOX, 0.2f), Chance(Wild.BEAR, 0.04f),
        ),
    )

    /** Which animals can be about in [window] (whatever the day). */
    fun candidates(window: WildTime): List<Wild> = CANDIDATES.getValue(window).map { it.kind }

    /** The window of the day at [hour] in [month]: dawn and dusk round the sunrise and the sunset of the month (see [Env]). */
    fun time(hour: Float, month: Int): WildTime = time(Env(hour, month, false), hour)

    internal fun time(env: Env, hour: Float): WildTime {
        val h = ((hour % 24f) + 24f) % 24f
        return when {
            h >= env.sunrise - TWILIGHT_OUT && h < env.sunrise + TWILIGHT_IN -> WildTime.DAWN
            h >= env.sunset - TWILIGHT_IN && h < env.sunset + TWILIGHT_OUT -> WildTime.DUSK
            h > env.sunrise && h < env.sunset -> WildTime.DAY
            else -> WildTime.NIGHT
        }
    }

    /** The day a window belongs to: days since 1970-01-01 of ISO [today] (0 when empty or unreadable); the small hours are the night before's. */
    fun day(today: String, hour: Float, month: Int): Long = day(today, hour, time(hour, month))

    internal fun day(today: String, hour: Float, window: WildTime): Long {
        val d = epochDay(today) ?: 0L
        val h = ((hour % 24f) + 24f) % 24f
        return if (h < 12f && window == WildTime.NIGHT) d - 1 else d
    }

    /** Who is about round the village of [seed] on ISO day [today] at [hour] in [month] (see [Wildlife]). */
    fun plan(seed: Long, today: String, hour: Float, month: Int): List<Sighting> = plan(seed, day(today, hour, month), time(hour, month), month)

    /** Who is about round the village of [seed] in [window] of [day] (see [day]) in [month]. */
    fun plan(seed: Long, day: Long, window: WildTime, month: Int): List<Sighting> {
        val a = (seed xor (seed ushr 32)).toInt()
        val b = Noise.hash(day.toInt(), (day ushr 32).toInt(), window.ordinal + 1)
        fun r(j: Int) = Noise.rnd(a, b, j)
        val out = ArrayList<Sighting>(4)
        if (r(0) >= QUIET) {
            val rolled = CANDIDATES.getValue(window).withIndex().filter { (i, c) -> r(10 + i) < chance(c, month) }
                .sortedBy { (i, _) -> r(30 + i) }.take(MAX_KINDS).sortedBy { it.index }
            for ((i, c) in rolled) out += Sighting(c.kind, count(c.kind, r(50 + i)), r(70 + i), Noise.hash(a, b, 90 + i))
        }
        if (winter(month) && r(5) < TRACKS_CHANCE) out += Sighting(Wild.TRACKS, 1, r(6), Noise.hash(a, b, 7))
        return out
    }

    private fun winter(month: Int) = month == 12 || month in 1..2

    /** A candidate's chance in [month]: fewer in winter, when the hedgehog, the bats and the bear sleep (and the badger mostly). */
    private fun chance(c: Chance, month: Int): Float = when {
        winter(month) -> when (c.kind) {
            Wild.HEDGEHOG, Wild.BATS, Wild.BEAR -> 0f
            Wild.BADGER -> c.p * 0.3f
            else -> c.p * 0.7f
        }
        // going to sleep, waking up
        month == 11 || month == 3 -> when (c.kind) {
            Wild.HEDGEHOG, Wild.BATS -> c.p * 0.4f
            Wild.BEAR -> c.p * 0.5f
            else -> c.p
        }
        else -> c.p
    }

    private fun count(kind: Wild, r: Float): Int = when (kind) {
        Wild.DEER -> 1 + min((r * 2).toInt(), 1)
        Wild.BOARS -> 3 + min((r * 3).toInt(), 2) // the sow and two to four young
        Wild.BIRDS -> 3 + min((r * 3).toInt(), 2)
        Wild.BATS -> 2 + min((r * 2).toInt(), 1)
        else -> 1
    }

    /** Days since 1970-01-01 of an ISO date, or null. */
    internal fun epochDay(iso: String): Long? {
        if (iso.length < 10 || iso[4] != '-' || iso[7] != '-') return null
        val y = iso.substring(0, 4).toIntOrNull() ?: return null
        val m = iso.substring(5, 7).toIntOrNull() ?: return null
        val d = iso.substring(8, 10).toIntOrNull() ?: return null
        if (m !in 1..12 || d !in 1..31) return null
        val yy = if (m <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        val doy = (153 * ((m + 9) % 12) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    // ------------------------------------------------------------------ where

    /** A ground point's sprite box for the view checks (nominal px): half its width, its height. */
    private const val BODY_HALF = 5f
    private const val BODY_UP = 7f

    /** People and the woods' things keep this much room round them (nominal px). */
    private const val ROOM = 12f

    /** How far an animal goes about its place (px): along the edge, a hop aside. */
    private const val ROAM = 6f

    /** A place's bubble floats about this far above where it points and this far to each side (nominal px, at the home view). */
    private const val BUBBLE_UP = 24f
    private const val BUBBLE_HALF = 12f

    /**
     * Where the animals can be on a [w] × [h] canvas of [state]'s village (see [WildPlaces]): outside the palisade, off the
     * water, the landmarks and the campsite, clear of where people stand and bubbles point, and in plain view (no tree, stake
     * or landmark in front). [trees]: the renderer's trees, bushes and rocks (world cells: x, y, kind, size, seed); [gone]
     * says which of them aren't standing (felled, making way). Pure.
     */
    internal fun places(
        state: GameState, w: Int, h: Int, fg: Foreground?, trees: List<FloatArray>, landmarks: List<LandmarkSpot>, camp: Campsite?,
        ring: PalisadeRing?, gone: (Int) -> Boolean = { false },
    ): WildPlaces {
        val age = state.age
        val land = VillageLayout.of(state)
        val comp = VillageLayout.composition(w, h, compact = false)
        val fx = comp.fireX.toFloat(); val fy = comp.fireY.toFloat()
        val top = comp.horizon + 16f
        // where people stand, and the things of the woods below; the bubbles over the places, above where they point
        val keep = ArrayList<FloatArray>()
        val bubbles = TownAnchors.withSpots(state, w, h).values.map { floatArrayOf(it.x - BUBBLE_HALF, it.y - BUBBLE_UP, it.x + BUBBLE_HALF, it.y + 3f) }
        for (id in TownAnchors.SPOTS) TownAnchors.spotAnchor(state, w, h, id)?.let { keep += floatArrayOf(it[0].toFloat(), it[1].toFloat(), ROOM) }
        if (fg != null) {
            keep += floatArrayOf(fg.gateX, fg.gateY, ROOM)
            keep += floatArrayOf(fg.stackX, fg.stackY, ROOM)
            keep += floatArrayOf(fg.seatX, fg.seatY, 12f)
            if (fg.kopa) keep += floatArrayOf(fg.kopaX, fg.kopaY, ROOM)
            // the meadow's log and its berry bushes
            keep += floatArrayOf(fg.meadowX - 13f, fg.meadowY + 7f, 8f)
            for (b in berryBushes(fg)) keep += floatArrayOf(b[0], b[1], 6f)
        }
        // what could stand in front of an animal ([x, y (its foot), kind, size, seed]): the trees by columns of the canvas, the stakes
        val cols = HashMap<Int, ArrayList<FloatArray>>()
        for ((i, t) in trees.withIndex()) {
            if (gone(i)) continue
            val tx = fx + (t[0] - t[1]) * 4f; val ty = fy + (t[0] + t[1]) * 2f
            cols.getOrPut(floor(tx / COL).toInt()) { ArrayList() }.add(floatArrayOf(tx, ty, t[2], t[3], t[4]))
        }
        val stakes = ring?.stakes.orEmpty().map { floatArrayOf(fx + (it.x - it.y) * 4f, fy + (it.x + it.y) * 2f, STAKE, it.h + Palisade.POINT, 0f) }
        // the buildings on their plots: [left, top, right, bottom] on the canvas (the bottom their front corner)
        val houses = state.buildings.filter { it.type.onPlot && it.plot in 0 until VillageLayout.MAX_PLOTS }.map { b ->
            val o = land.origin(b.plot); val x = o[0]; val y = o[1]
            floatArrayOf(fx + (x - y - 3f) * 4f, fy + (x + y) * 2f - spriteHeight(b.type), fx + (x + 3f - y) * 4f, fy + (x + y + 6f) * 2f)
        }
        /**
         * Whether [o] stands in front of something with its feet at canvas row [feet] (its own foot lower on the screen) and
         * covers canvas point ([x], [y]) of it: its shape as the painters draw it at the nominal detail (a spruce's trunk and
         * three tiers, a beech's trunk and round crown, a bush, a rock, a stake).
         */
        fun hides(o: FloatArray, x: Float, y: Float, feet: Float): Boolean {
            if (o[1] <= feet) return false
            val u = o[1] - y; val dx = x - o[0]; val size = o[3]
            if (u < 0f) return false
            return when (o[2]) {
                0f -> {
                    // see TreePainter.pine: a 2 px trunk 3 px tall, three tiers of branches each widest at its foot
                    val h = (13 + (o[4].toInt() ushr 3) % 5) * size; val f = h - 2f
                    if (u <= 3f && dx >= -1f && dx < 1f) return true
                    (0..2).any { j ->
                        val foot = 2f + j * f * 0.28f; val tier = f * 0.46f
                        u >= foot && u <= foot + tier && abs(dx) < h * 0.31f * (1f - j * 0.24f) * (foot + tier - u) / tier + 0.5f
                    }
                }
                1f -> {
                    // see TreePainter.broadleaf and canopy: the trunk, the crown's four blobs
                    val trunk = (4 * size).toInt() + 1f
                    if (u <= trunk && dx >= -1f && dx < 1f) return true
                    // the point below the crown's middle, down the screen
                    val below = trunk + 4f * size - u
                    CROWN.any { b -> val bx = dx - b[0] * size; val by = below - b[1] * size; bx * bx + by * by <= b[2] * b[2] * size * size }
                }
                // see TreePainter.bush and rock
                2f -> u <= 4f && dx * dx / 12f + (u - 2f) * (u - 2f) / 6f <= 1f
                STAKE -> u <= size && abs(dx) < 2.2f // the stakes stand side by side: a wall
                else -> u <= 3f && abs(dx) < 3f
            }
        }
        /**
         * Whether most of a body [half] px to each side and [up] px tall with its feet at ([x], [y]) shows: at most a sixth of
         * it hidden by what stands in front of [depth] (the canvas row of its place in the painter's order: its feet, or the
         * foot of the tree it sits in).
         */
        fun inView(x: Float, y: Float, half: Float = BODY_HALF, up: Float = BODY_UP, depth: Float = y): Boolean {
            if (landmarks.any { l ->
                    val r = l.screenRect()
                    x + half > fx + r[0] && x - half < fx + r[2] && y > fy + r[1] && y - up < fy + r[3]
                }) return false
            if (houses.any { r -> x + half > r[0] && x - half < r[2] && y + 2f > r[1] && y - up < r[3] }) return false
            val c0 = floor((x - 20f) / COL).toInt(); val c1 = floor((x + 20f) / COL).toInt()
            val near = ArrayList<FloatArray>()
            for (c in c0..c1) cols[c]?.let { col -> for (o in col) if (o[1] > depth && o[1] < y + 26f) near += o }
            for (o in stakes) if (o[1] > depth && o[1] < y + 16f && abs(o[0] - x) < half + 3f) near += o
            if (near.isEmpty()) return true
            var hidden = 0
            for (row in 0..3) for (col in -2..2) {
                val px = x + col * half * 0.4f; val py = y - 0.5f - row * (up - 1f) / 3f
                if (near.any { hides(it, px, py, depth) } && ++hidden > 3) return false
            }
            return true
        }
        // on the canvas below the far treeline, and no farther down than the home view shows (the meadow of the woods)
        val bottom = min(h - 10f, TownAnchors.home(state, w, h).bottom + 20f)
        fun onCanvas(x: Float, y: Float) = x in 12f..(w - 12f) && y in top..bottom
        fun clear(x: Float, y: Float, room: List<FloatArray> = keep) =
            room.none { hypot(it[0] - x, it[1] - y) < it[2] } && (room !== keep || bubbles.none { x > it[0] && x < it[2] && y > it[1] && y - BODY_UP < it[3] })
        /** Dry land: off the stream (on its bank at most), the pond, the landmarks and the campsite. */
        fun dry(x: Float, y: Float): Boolean {
            val sx = x - fx; val sy = y - fy
            val wx = worldX(sx, sy); val wy = worldY(sx, sy)
            if (abs(wx - land.streamX(wy)) < 1.3f) return false
            if (land.water && land.wet(wx, wy, 3f)) return false
            if (fg != null && fg.pondD(x, y) < 1.3f) return false
            if (landmarks.any { it.covers(wx, wy, 1f) }) return false
            return camp == null || !camp.clears(wx, wy)
        }
        // outside the palisade: at or beyond the forest's edge (the ring stands [Palisade.INSET] inside it)
        fun outside(x: Float, y: Float, slack: Float = 0f): Boolean {
            val dx = x - fx; val dy = y - fy
            return VillageLayout.screenMetric(dx, dy) >= land.forestEdge(age, VillageLayout.angle(dx, dy)) + 1f - slack
        }
        // dry and outside all the way the animal goes about its place: along the edge, or a hop to either side
        fun ground(x: Float, y: Float, dx: Float = 1f, dy: Float = 0f): Boolean {
            if (!dry(x, y) || !clear(x, y)) return false
            val len = hypot(dx, dy).coerceAtLeast(1e-3f); val ux = dx / len * ROAM; val uy = dy / len * ROAM
            for ((px, py) in arrayOf(x + ux to y + uy, x - ux to y - uy, x + ROAM to y, x - ROAM to y)) if (!dry(px, py) || !outside(px, py, slack = 1f)) return false
            return true
        }
        fun place(x: Float, y: Float, dx: Float, dy: Float): FloatArray {
            val len = hypot(dx, dy).coerceAtLeast(1e-3f)
            return floatArrayOf(x, y, dx / len, dy / len, VillageLayout.angle(x - fx, y - fy))
        }
        /** Adds a place at ([x], [y]) going along ([dx], [dy]) to [to] when an animal can be there, not too near another. */
        fun offer(to: ArrayList<FloatArray>, x: Float, y: Float, dx: Float, dy: Float, apart: Float = 5f): Boolean {
            if (!onCanvas(x, y) || !outside(x, y) || !ground(x, y, dx, dy) || !inView(x, y)) return false
            // in view all the way it goes: along the edge, a hop aside
            val len = hypot(dx, dy).coerceAtLeast(1e-3f); val ux = dx / len * ROAM; val uy = dy / len * ROAM
            if (!inView(x + ux, y + uy) || !inView(x - ux, y - uy) || !inView(x + ROAM, y) || !inView(x - ROAM, y)) return false
            if (to.any { hypot(it[0] - x, it[1] - y) < apart }) return false
            to += place(x, y, dx, dy)
            return true
        }

        // the forest's edge, all round the clearing: the nearest point in view beyond it at each angle (angles as
        // VillageLayout.angle gives them: the edge's noise goes round once from -π to π)
        val edge = ArrayList<FloatArray>()
        for (i in 0 until EDGE_SAMPLES) {
            val a = (-PI + 2 * PI * i / EDGE_SAMPLES).toFloat()
            for (off in EDGE_OFFSETS) {
                val r = land.forestEdge(age, a) + off
                if (offer(edge, fx + r * cos(a), fy + r * sin(a) / 2.4f, -sin(a), cos(a) / 2.4f)) break
            }
        }
        // the open ways out of the clearing, where no trees stand: along the road's sides (east, and west from the
        // hamlet on), on the stream's banks, beside the path into the woods, round the pond
        val roadWest = age.ordinal >= si.lanisce.lani.game.Age.ZASELEK.ordinal
        for (dir in floatArrayOf(1f, -1f)) {
            if (dir < 0f && !roadWest) continue
            for (side in if (dir > 0f) floatArrayOf(-1.3f, 2.4f) else floatArrayOf(-1.3f, 1.3f)) {
                var wx = 0f
                while (wx < 80f) {
                    val x = dir * wx; val y = side + land.roadY(x)
                    offer(edge, fx + (x - y) * 4f, fy + (x + y) * 2f, dir * 4f, dir * 2f, apart = 7f)
                    wx += 0.7f
                }
            }
        }
        var sy = -40f
        while (sy < 70f) {
            val d = (land.streamX(sy + 0.1f) - land.streamX(sy - 0.1f)) / 0.2f
            for (side in floatArrayOf(-1.4f, 1.4f)) {
                val x = land.streamX(sy) + side
                offer(edge, fx + (x - sy) * 4f, fy + (x + sy) * 2f, (d - 1f) * 4f, (d + 1f) * 2f, apart = 7f)
            }
            sy += 0.7f
        }
        if (fg != null) {
            var y = fg.edgeY + 12f
            while (y < h - 10f) {
                for (side in floatArrayOf(-5.5f, 5.5f)) offer(edge, fg.pathX(y) + side, y, fg.pathX(y + 1f) - fg.pathX(y - 1f), 2f, apart = 7f)
                y += 3f
            }
            for (i in 0 until 24) {
                val a = (PI * (0.1 + 0.8 * i / 23)).toFloat()
                offer(edge, fg.pondX + cos(a) * fg.pondRx * 1.45f, fg.pondY + sin(a) * fg.pondRy * 1.6f, -sin(a), cos(a) * 0.4f, apart = 7f)
            }
        }
        // the meadow of the woods below and its rim, off the path; by day not where the deer graze (they're off in the
        // woods at night)
        val night = ArrayList<FloatArray>()
        val meadow = ArrayList<FloatArray>()
        if (fg != null) {
            val deer = listOf(floatArrayOf(fg.meadowX + 13f, fg.meadowY + 3f, 12f), floatArrayOf(fg.meadowX + 22f, fg.meadowY + 8f, 9f))
            for (v in -5..5) for (u in -10..10) {
                val x = fg.meadowX + u * fg.meadowRx * 0.1f; val y = fg.meadowY + v * fg.meadowRy * 0.15f + 2f
                if (fg.meadowD(x, y) > 1.1f || fg.onPath(x, y, slack = 3f)) continue
                if (offer(night, x, y, 1f, 0f) && clear(x, y, deer)) meadow += night.last()
            }
        }
        // beside a bush outside the palisade: the meadow's berry bushes, the bushes at the forest's edge and in the woods
        val bushes = ArrayList<FloatArray>()
        val beside = ArrayList<FloatArray>()
        if (fg != null) beside += berryBushes(fg)
        for ((i, t) in trees.withIndex()) if (t[2].toInt() == 2 && !gone(i)) beside += floatArrayOf(fx + (t[0] - t[1]) * 4f, fy + (t[0] + t[1]) * 2f)
        for (b in beside) {
            if (!outside(b[0], b[1])) continue
            for (side in floatArrayOf(7f, -7f)) {
                val x = b[0] + side; val y = b[1] + 1f
                if (onCanvas(x, y) && outside(x, y) && ground(x, y) && inView(x, y, 3f, 4f) && bushes.none { hypot(it[0] - x, it[1] - y) < 8f }) {
                    bushes += place(x, y, 1f, 0f)
                    break
                }
            }
        }
        // trees at the forest's edge and round the woods' clearings: a beech whose trunk shows, where a squirrel climbs, and
        // the tops of the trees, where an owl sits (and a squirrel, where no trunk shows)
        val perch = ArrayList<FloatArray>()
        val crowns = ArrayList<FloatArray>()
        for ((i, t) in trees.withIndex()) {
            val kind = t[2].toInt()
            if (kind > 1 || gone(i)) continue
            val x = fx + (t[0] - t[1]) * 4f; val y = fy + (t[0] + t[1]) * 2f
            if (landmarks.any { it.clears(t[0], t[1]) } || (camp != null && camp.clears(t[0], t[1]))) continue
            val dx = x - fx; val dy = y - fy
            val beyond = VillageLayout.screenMetric(dx, dy) - land.forestEdge(age, VillageLayout.angle(dx, dy))
            val woods = fg != null && y > fg.edgeY && y < fg.meadowY + fg.meadowRy + 24f
            if (beyond < 3f || (beyond > 28f && !woods) || x !in 12f..(w - 12f) || y > bottom) continue
            val size = t[3]
            // the top of its crown, just into the leaves or the spruce's tip (see TreePainter)
            val crown = if (kind == 1) y - ((4 * size).toInt() + 1f) - 9.8f * size + 2f else y - (13 + (t[4].toInt() ushr 3) % 5) * size + 2f
            if (crown > comp.horizon + 6f && clear(x, crown) && inView(x, crown, 2f, 4f, depth = y)) crowns += floatArrayOf(x, crown, 0f, size, i.toFloat(), y)
            if (kind != 1) continue
            val trunk = (4 * size).toInt() + 1f
            if (!onCanvas(x, y) || y - trunk - 10f * size < top) continue
            if (!clear(x, y) || !clear(x, y - trunk)) continue
            // the squirrel keeps to the trunk's right side (see the renderer)
            if (!inView(x + 2f, y, 3f, trunk + 3f)) continue
            perch += floatArrayOf(x, y, trunk, size, i.toFloat())
        }
        // over the far treeline, behind the village: where the birds fly
        val sky = ArrayList<FloatArray>()
        for (i in 0 until EDGE_SAMPLES) {
            val a = (-PI + 2 * PI * i / EDGE_SAMPLES).toFloat()
            if (sin(a) > -0.4f) continue
            val r = land.forestEdge(age, a) + 6f
            val x = fx + r * cos(a); val y = fy + r * sin(a) / 2.4f
            if (x in 24f..(w - 24f) && y > comp.horizon + 2f) sky += floatArrayOf(x, y, 1f, 0f, a)
        }
        return WildPlaces(edge, meadow, night, bushes, perch, crowns, sky)
    }

    /** The meadow's two berry bushes (canvas px; see the renderer's woods). */
    internal fun berryBushes(fg: Foreground): List<FloatArray> =
        listOf(floatArrayOf(fg.meadowX - 25f, fg.meadowY - 8f), floatArrayOf(fg.meadowX - 16f, fg.meadowY - 13f))

    private const val EDGE_SAMPLES = 160
    private val EDGE_OFFSETS = floatArrayOf(2f, 5f, 8f, 11f)

    /** The trees are looked up by columns of the canvas this wide (px). */
    private const val COL = 8f

    /** A palisade stake's kind among the things that can stand in front of an animal (the trees' are 0 to 3). */
    private const val STAKE = 9f

    /** A beech's crown: its blobs (x, y from the crown's middle, down the screen; radius), per unit of size (TreePainter's small canopy). */
    private val CROWN = arrayOf(floatArrayOf(0f, 0f, 4.2f), floatArrayOf(-3f, 1.5f, 3f), floatArrayOf(3f, 1.2f, 3.2f), floatArrayOf(-1f, -2.8f, 3f))

    private fun worldX(sx: Float, sy: Float): Float = (sx / 4f + sy / 2f) / 2f
    private fun worldY(sx: Float, sy: Float): Float = (sy / 2f - sx / 4f) / 2f
}

/**
 * Where the wild animals can show on a canvas (nominal canvas px; see [Wildlife.places]). Each place is
 * [x, y, dx, dy, angle]: a ground point, the forest edge's direction there on the screen (unit; the meadow's: along x)
 * and the edge's angle round the fire ([VillageLayout.angle]). A [perch] is [x, y, trunk px, size, tree index]: a tree's
 * foot.
 */
internal class WildPlaces(
    /** At the forest's edge outside the palisade, all round the clearing, and along the open ways out of it (the road, the stream, the woods' path, the pond). */
    val edge: List<FloatArray>,
    /** In the meadow of the woods below the village (a tall canvas), clear of where its deer graze by day. */
    val meadow: List<FloatArray>,
    /** The same meadow at night, the deer gone. */
    val meadowNight: List<FloatArray>,
    /** Beside a bush outside the palisade. */
    val bushes: List<FloatArray>,
    /** Beeches at the forest's edge whose trunk shows. */
    val perch: List<FloatArray>,
    /** The tops of the trees at the forest's edge: [x, y, 0, size, tree index, the tree's foot row]. */
    val crowns: List<FloatArray>,
    /** The ground under the sky behind the village, at the far treeline. */
    val sky: List<FloatArray>,
)

/**
 * Draws the wild animals (see [Wildlife]) at village scale (a villager is 7 px tall), each around its feet at a canvas
 * point, facing left ([flip] turns it). Callers draw them under the context's sprite zoom at the higher details. Their
 * eyes shine in the dark.
 */
internal class WildPainter(private val s: SceneCtx) {
    private val c get() = s.canvas
    private val t get() = s.time
    private val env get() = s.env

    /**
     * Character-art sprite whose bottom row sits just above [by], centred on [bx]; '.' is transparent; 'e' an eye, shining
     * in the dark. In the dark the moon catches the top of each column (the back, the ears), so the animal reads against
     * the night grass.
     */
    private fun sprite(bx: Int, by: Int, rows: Array<String>, flip: Boolean, pal: (Char) -> Int) {
        val h = rows.size
        val w = rows[0].length
        val shine = env.dark > 0.3f
        for ((r, row) in rows.withIndex()) for (j in row.indices) {
            val ch = row[j]
            if (ch == '.') continue
            val x = if (flip) bx + w / 2 - j else bx - w / 2 + j
            if (ch == 'e' && shine) { c.penEmissive = true; c.set(x, by - h + r, EYESHINE); c.penEmissive = false; continue }
            val top = r == 0 || rows[r - 1].getOrElse(j) { '.' } == '.'
            c.set(x, by - h + r, if (shine && top) Col.mix(pal(ch), MOON, 0.55f) else pal(ch))
        }
    }

    /** The wild boar sow, rooting ([root]: her snout down in the earth), bristly, her mane along her high shoulders. */
    fun boar(bx: Int, by: Int, flip: Boolean, root: Boolean) {
        val rows = if (root) arrayOf(
            "...LLLL....",
            "..dmmmmmmL.",
            ".dmmmmmmmmm",
            "demmmmmmmmm",
            "dt.mmmmmmm.",
            "s..d.d..d.d",
        ) else arrayOf(
            "...LLLL....",
            ".ddmmmmmmL.",
            "demmmmmmmmm",
            "sdmmmmmmmmm",
            "t..mmmmmmm.",
            "...d.d..d.d",
        )
        sprite(bx, by, rows, flip) {
            when (it) { 'm' -> BOAR_M; 'L' -> BOAR_L; 's' -> SNOUT; 't' -> TUSK; else -> BOAR_D }
        }
    }

    /** A young boar: striped along its back in its first summer ([stripes]), plain and reddish after. */
    fun piglet(bx: Int, by: Int, flip: Boolean, stripes: Boolean, step: Int) {
        val legs = if (step == 0) "..d.d." else "...d.d"
        sprite(bx, by, arrayOf("..blbl", "sebbbb", legs), flip) {
            when (it) { 'b' -> PIGLET; 'l' -> if (stripes) PIGLET_L else PIGLET; 's' -> SNOUT; else -> BOAR_D }
        }
    }

    /** A badger snuffling along: low and grey, its white face with the black stripe through the eye. */
    fun badger(bx: Int, by: Int, flip: Boolean, step: Int) {
        val legs = if (step == 0) ".k.k...k.k" else "..k.k...k."
        sprite(bx, by, arrayOf("...gggggg.", ".wkggGgggg", "nwwkgggggg", legs), flip) {
            when (it) { 'g' -> BADGER_G; 'G' -> BADGER_L; 'w' -> WHITE; 'n' -> NOSE; else -> NOSE }
        }
    }

    /** A fox slinking low: red with a white throat, black stockings, the brush out behind with its white tip. */
    fun fox(bx: Int, by: Int, flip: Boolean, step: Int) {
        val legs = if (step == 0) "..k.k..k.k.." else "...k.k..k.k."
        sprite(bx, by, arrayOf(
            ".k.k........",
            ".ooo........",
            "neooooooooo.",
            "..wooooooooTW",
            legs + ".",
        ).map { it.padEnd(13, '.') }.toTypedArray(), flip) {
            when (it) { 'o' -> FOX_O; 'T' -> FOX_D; 'w', 'W' -> WHITE; 'n' -> NOSE; else -> NOSE }
        }
    }

    /** A hedgehog: a round dome of spines, light-tipped, its pointed face and black nose poking out. */
    fun hedgehog(bx: Int, by: Int, flip: Boolean) {
        sprite(bx, by, arrayOf("...sSs.", ".SsSsSs", "nfsSsSs", "..f..f."), flip) {
            when (it) { 's' -> SPINE; 'S' -> SPINE_L; 'f' -> HEDGE_F; else -> NOSE }
        }
    }

    /** A brown hare sitting up, its long ears black-tipped, its white scut; [hop]: mid-leap, stretched out. */
    fun hare(bx: Int, by: Int, flip: Boolean, hop: Boolean) {
        val rows = if (hop) arrayOf(
            "k......",
            ".bb....",
            "nebbbbw",
            "..bbbb.",
            ".b...b.",
        ) else arrayOf(
            "..k....",
            "..bk...",
            ".bbb...",
            "nebbbb.",
            "..bLbbw",
            "..b.bb.",
        )
        sprite(bx, by, rows, flip) {
            when (it) { 'b' -> HARE_B; 'L' -> HARE_L; 'w' -> WHITE; else -> NOSE }
        }
    }

    /** A red squirrel clinging to a trunk, head up, its pale chest, its bushy tail curled over its back. */
    fun squirrel(bx: Int, by: Int, flip: Boolean) {
        sprite(bx, by, arrayOf("r.tt", "rrrt", "cr.t", ".rr."), flip) {
            when (it) { 't' -> SQUIRREL_T; 'c' -> WHITE; else -> SQUIRREL_R }
        }
    }

    /** A small bird in flight: wings up or level ([flap]). Screen pixels, no zoom (it's tiny at every detail). */
    fun bird(bx: Int, by: Int, flap: Int) {
        val k = s.k
        if (flap == 0) { c.block(bx - k, by - k, k, BIRD); c.block(bx, by, k, BIRD); c.block(bx + k, by - k, k, BIRD) }
        else { c.block(bx - k, by, k, BIRD); c.block(bx, by, k, BIRD); c.block(bx + k, by, k, BIRD) }
    }

    /** A bat flitting: wings up or down ([flap]). */
    fun bat(bx: Int, by: Int, flap: Int) {
        val rows = if (flap == 0) arrayOf("k...k", "kkkkk", "..k..") else arrayOf("..k..", "kkkkk", "k...k")
        sprite(bx, by, rows, false) { BAT }
    }

    /** Two eyes glowing at the forest's edge in the dark, and the shadow of something big round them: a bear, perhaps. */
    fun bearEyes(bx: Int, by: Int, open: Boolean) {
        val k = s.k
        for (r in 0 until 3) c.blend(bx - 3 * k + r * k, by - 5 * k, SHADOW, 0.35f)
        c.ditherCircle(bx.toFloat(), by - 4f * k, 5f * k, SHADOW, 0.45f)
        if (!open) return
        c.penEmissive = true
        c.block(bx - 2 * k, by - 6 * k, k, EYESHINE); c.block(bx + k, by - 6 * k, k, EYESHINE)
        c.penEmissive = false
    }

    /**
     * Tracks in the snow, from ([x], [y]) along ([dx], [dy]) (canvas px): pairs of prints, [n] of them, a hop apart.
     * Blended on the ground (their object has no outline).
     */
    fun tracks(x: Float, y: Float, dx: Float, dy: Float, n: Int) {
        val k = s.k
        val nx = -dy; val ny = dx
        for (j in 0 until n) {
            val u = j * 4.5f * k
            val side = if (j % 2 == 0) 1f else -1f
            val px = x + dx * u + nx * side * 1.2f * k; val py = y + dy * u + ny * side * 0.6f * k
            c.block(px.toInt(), py.toInt(), k, TRACK)
            c.block((px + dx * 1.5f * k).toInt(), (py + dy * 1.5f * k).toInt(), k, TRACK)
        }
    }

    private companion object {
        val EYESHINE = Col.hex(0xE8E07A)
        val MOON = Col.hex(0xD8E2FF)
        val BOAR_D = Col.hex(0x3A302A); val BOAR_M = Col.hex(0x6A584C); val BOAR_L = Col.hex(0x8E7A68)
        val SNOUT = Col.hex(0x8E7466); val TUSK = Col.hex(0xF2EAD8)
        val PIGLET = Col.hex(0x9A6A44); val PIGLET_L = Col.hex(0xD8B888)
        val BADGER_G = Col.hex(0x8A8C92); val BADGER_L = Col.hex(0xB4B6BC)
        val WHITE = Col.hex(0xF4F0E6); val NOSE = Col.hex(0x1E1A1C)
        val FOX_O = Col.hex(0xD8702A); val FOX_D = Col.hex(0xB0561E)
        val SPINE = Col.hex(0x5E4A38); val SPINE_L = Col.hex(0x9A8266); val HEDGE_F = Col.hex(0xB89A78)
        val HARE_B = Col.hex(0x9C7A56); val HARE_L = Col.hex(0xC4A27A)
        val SQUIRREL_R = Col.hex(0xD2622A); val SQUIRREL_T = Col.hex(0xEE8A3E)
        val BIRD = Col.hex(0x2E2A30)
        val BAT = Col.hex(0x241E28)
        val SHADOW = Col.hex(0x0E0C10)
        val TRACK = Col.hex(0x7686AA)
    }
}
