package si.lanisce.lani.ui.game

import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Calendar
import si.lanisce.lani.game.Chest
import si.lanisce.lani.game.Festival
import si.lanisce.lani.game.FestivalDay
import si.lanisce.lani.game.ProjectOption
import si.lanisce.lani.game.Projects
import si.lanisce.lani.game.Surprise
import si.lanisce.lani.game.Surprises
import si.lanisce.lani.game.Teaser
import si.lanisce.lani.game.Tomorrow
import java.time.LocalDate
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.LogEntry
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.QuestQueue
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.scene.TownMarker
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.scene.TownSpots
import si.lanisce.lani.game.scene.general
import si.lanisce.lani.game.render.Camera
import si.lanisce.lani.game.render.CanvasPoint
import si.lanisce.lani.game.render.TownAnchors
import si.lanisce.lani.l10n.bi
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// Pure helpers behind the town view: place names, bubble layout and the scroll's community overview.

// --- places ---------------------------------------------------------------------------------------

fun TownPlace.emoji(): String = when (this) {
    TownPlace.Fire -> "🔥"
    TownPlace.Forest -> "🌲"
    is TownPlace.At -> type.emoji
    is TownPlace.Spot -> TownSpots.info(id).emoji
    is TownPlace.Landmark -> si.lanisce.lani.game.Projects.spec(project)?.emoji ?: "🏗️"
}

/** "Ogenj · The fire", "Koča · Hut". */
fun TownPlace.label(): String = when (this) {
    TownPlace.Fire -> bi("common.fire")
    TownPlace.Forest -> bi("townLogic.forest")
    is TownPlace.At -> type.label()
    is TownPlace.Spot -> TownSpots.info(id).label
    is TownPlace.Landmark -> si.lanisce.lani.game.Projects.spec(project)?.name ?: project
}

/** The place as a scene's `from` and [si.lanisce.lani.app.SceneController.at] know it: "fire", "forest", "hut". */
fun TownPlace.key(): String = when (this) {
    TownPlace.Fire -> "fire"
    TownPlace.Forest -> "forest"
    is TownPlace.At -> type.name.lowercase()
    is TownPlace.Spot -> "spot:$id"
    is TownPlace.Landmark -> "project:$project"
}

/** Where something is, with the right Slovene preposition and case: "ob ognju · by the fire", "pod lipo · under the linden tree". */
fun TownPlace.where(): String = when (this) {
    TownPlace.Fire -> bi("townLogic.byFire")
    TownPlace.Forest -> bi("townLogic.inForest")
    is TownPlace.Spot -> TownSpots.info(id).where
    // the culture pack's own words for where it is ("v vinogradu · in the vineyard"), else its name
    is TownPlace.Landmark -> si.lanisce.lani.game.Projects.spec(project)?.let { it.whereText?.bi() ?: it.name } ?: project
    is TownPlace.At -> when (type) {
        BuildingType.TENT -> bi("townLogic.tent")
        BuildingType.FIELD -> bi("townLogic.field")
        BuildingType.WELL -> bi("townLogic.atWell")
        BuildingType.HUT -> bi("townLogic.hut")
        BuildingType.KOZOLEC -> bi("townLogic.atHayrack")
        BuildingType.BEEHIVE -> bi("townLogic.atBeeHouse")
        BuildingType.PALISADE -> bi("townLogic.byPalisade")
        BuildingType.WATCHTOWER -> bi("townLogic.watchtower")
        BuildingType.SMITHY -> bi("townLogic.smithy")
        BuildingType.LIPA -> bi("townLogic.underLindenTree")
        BuildingType.HOUSE -> bi("townLogic.house")
        BuildingType.CHURCH -> bi("townLogic.church")
        BuildingType.SCHOOL -> bi("townLogic.atSchool")
        BuildingType.MARKET -> bi("townLogic.atMarket")
    }
}

fun TimeOfDay.emoji(): String = when (this) {
    TimeOfDay.MORNING, TimeOfDay.DAWN -> "🌅"
    TimeOfDay.AFTERNOON -> "☀️"
    TimeOfDay.EVENING -> "🌇"
    TimeOfDay.NIGHT -> "🌙"
}

/** "zjutraj · in the morning" (dawn is the morning's). */
fun TimeOfDay.label(): String = when (this) {
    TimeOfDay.MORNING, TimeOfDay.DAWN -> bi("townLogic.morning")
    TimeOfDay.AFTERNOON -> bi("townLogic.afternoon")
    TimeOfDay.EVENING -> bi("townLogic.evening")
    TimeOfDay.NIGHT -> bi("townLogic.atNight")
}

/**
 * The village's places in reading order: the fire, the forest, then each built type by its first plot (what stands on
 * none last: the palisade round them all, the tent at the pond).
 */
fun townPlaces(state: GameState): List<TownPlace> =
    listOf(TownPlace.Fire, TownPlace.Forest) +
        state.buildings.sortedBy { if (it.plot >= 0) it.plot else Int.MAX_VALUE }.map { it.type }.distinct().map { TownPlace.At(it) }

/**
 * Why [scene] can't be entered yet, one entry per missing thing ("🏘️ Vas · Village", "zgradi · build 🛖 Koča /
 * 🏠 Hiša"); empty when it's open.
 */
fun lockReasons(scene: SceneSpec, state: GameState): List<String> {
    if (Happenings.open(scene, state)) return emptyList()
    val out = ArrayList<String>()
    val age = scene.needs?.let { n -> Age.entries.firstOrNull { it.name.equals(n, ignoreCase = true) } }
    if (age != null && state.age < age) out += bi("townLogic.needsAge", "ageEmoji" to age.emoji, "ageLabel" to (age.label()))
    val places = scene.from.map(TownPlace::of)
    if (places.none { TownMarkers.has(state, it) }) {
        val build = places.filterIsInstance<TownPlace.At>().distinct()
        if (build.isNotEmpty()) out += "${bi("townLogic.buildThere")} " + build.joinToString(" / ") { "${it.type.emoji} ${it.type.sl}" }
        // a project's landmark: once the project is finished
        for (l in places.filterIsInstance<TownPlace.Landmark>().distinct()) si.lanisce.lani.game.Projects.spec(l.project)?.let { out += "${bi("townLogic.finishThere")} ${it.emoji} ${it.short}" }
    }
    return out
}

/** The next goal in a few words, for the pill under the HUD. */
fun NextGoal.short(): String = when (this) {
    NextGoal.FeedFire -> bi("common.fireDying")
    is NextGoal.Advance -> "${bi("townLogic.advance")}: ${to.emoji} ${to.sl}"
    is NextGoal.Build -> "${bi("townLogic.build")}: ${option.type.emoji} ${option.type.sl}"
    is NextGoal.Grow -> "${to.emoji} ${to.sl}: ${stepsLeft(missing.size)}"
    is NextGoal.Learn -> "${bi("homeScreen.newWords")}: ${to.emoji} ${to.sl}"
    is NextGoal.Friends -> "${bi("common.friends")}: ${to.emoji} ${to.sl}"
    is NextGoal.Polish -> "${bi("townLogic.polish")}: ${to.emoji} ${to.sl}"
    is NextGoal.Gather -> "${bi("townLogic.gather")}: ${res.emoji} ${res.sl}"
}

// --- bubbles --------------------------------------------------------------------------------------

/** A bubble's offset from its place (dp): right is +x, up is −y. */
data class BubbleOffset(val x: Float, val y: Float)

/**
 * Where [n] bubbles that share one place sit around it: side by side in a gentle arc (the middle ones a little
 * higher), at most [perRow] in a row, further rows stacked above. The whole fan shifts sideways so every bubble
 * stays within [minX]..[maxX] (dp from the place; the screen edges), while their pointers still meet at the place.
 */
fun fanOut(
    n: Int,
    spacing: Float = 52f,
    rise: Float = 10f,
    rowGap: Float = 54f,
    perRow: Int = 4,
    minX: Float = Float.NEGATIVE_INFINITY,
    maxX: Float = Float.POSITIVE_INFINITY,
): List<BubbleOffset> {
    if (n <= 0) return emptyList()
    val rows = ceil(n / perRow.toFloat()).toInt()
    val out = ArrayList<BubbleOffset>(n)
    for (r in 0 until rows) {
        val count = min(perRow, n - r * perRow)
        val half = (count - 1) / 2f
        for (i in 0 until count) {
            val u = if (half > 0f) (i - half) / half else 0f
            val arc = if (count > 1) rise * (1f - u * u) else 0f
            out += BubbleOffset((i - half) * spacing, -r * rowGap - arc)
        }
    }
    val lo = out.minOf { it.x }; val hi = out.maxOf { it.x }
    val shift = when {
        hi - lo > maxX - minX -> (minX + maxX) / 2f - (lo + hi) / 2f
        lo < minX -> minX - lo
        hi > maxX -> maxX - hi
        else -> 0f
    }
    return if (shift == 0f) out else out.map { it.copy(x = it.x + shift) }
}

/** A place's bubbles for [layoutBubbles]: where the place is on screen (dp) and how many bubbles it has. */
data class BubbleGroup(val x: Float, val y: Float, val n: Int)

/** A box on screen (dp): what the bubbles keep off ([layoutBubbles]'s keepOff, the fire's tap area). */
data class BubbleBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /** Whether a bubble [size] dp square centred at ([x], [y]) comes within [air] dp of it. */
    fun meets(x: Float, y: Float, size: Float, air: Float = 0f): Boolean {
        val r = size / 2f + air
        return x + r > left && x - r < right && y + r > top && y - r < bottom
    }
}

/** How far the bubbles keep off the fire (dp): their bob (±3 dp) and a little air. */
const val FIRE_AIR = 6f

/**
 * The fire's tap area on screen through [cam] (dp at [density] px a dp), for the bubbles to keep off: its flames,
 * smoke and stones ([area], nominal px [left, top, right, bottom]: [TownAnchors.fireArea], where a tap is the fire's)
 * and its screen reader's target, [TownTargets.FIRE_DP] square round its [body] ([TownAnchors.fireBody]).
 */
fun fireKeepOff(area: FloatArray, body: CanvasPoint, cam: Camera, density: Float): BubbleBox {
    val half = TownTargets.FIRE_DP / 2f
    val bx = cam.toScreenX(body.x) / density; val by = cam.toScreenY(body.y) / density
    return BubbleBox(
        min(cam.toScreenX(area[0]) / density, bx - half), min(cam.toScreenY(area[1]) / density, by - half),
        max(cam.toScreenX(area[2]) / density, bx + half), max(cam.toScreenY(area[3]) / density, by + half),
    )
}

/**
 * Where every bubble goes (dp, absolute centres), the places laid out together so no two bubbles cover each other:
 * each place's bubbles fan out over it as [fanOut] does, [lift] above it; then, the lowest place on screen first
 * (the nearest, in the village's perspective), a fan that would land on an already placed bubble moves aside:
 * sideways when the two would sit beside each other anyway, up when one would sit on the other (its pointer just
 * gets longer). Bubbles are [size] dp squares, air included. Every push fades in with the overlap and is applied
 * once, from where the fans start, so panning and zooming mostly slide the bubbles; the hair of shared air that leaves
 * now and then is cleared below. The fans stay within [minX]..[maxX] and below [minY] (under the HUD nothing can be
 * tapped).
 *
 * The fire stays the easiest place to tap: a fan that would come within [air] of [keepOff] (its tap area, see
 * [fireKeepOff]) starts off it, away from the fire, sideways and up as it was beside it or over it ([offBox]; its
 * pointers still point at its place, longer or slanting), and the pushes above go on from there. Near the fire (moved
 * by it, pushed back onto it, or landing on a bubble it moved) a fan then moves up off every bubble already placed,
 * fading in with the overlap: beside the fire or above it, up keeps it off the fire.
 *
 * What the pushes leave of a fan's overlap (they fade in with it, so a corner of one may still sit on a corner of another)
 * it then clears the shortest way, round a corner partly both ways ([clearOf]): away from the HUD no two bubbles' boxes
 * overlap at all, corners included.
 *
 * Pushed up under the HUD, a fan comes back down to just under it; a bubble of it that lands there on one already placed
 * (the stack round the fire grown that tall) or on the fire goes aside on its own instead, left or right of what's in its
 * way, its pointer still at its place ([asideUnderHud]): no two bubbles cover each other, none is under the HUD or on the
 * fire. That's a step, not a slide, as the stack reaches the HUD or leaves it (on screen the bubble glides there:
 * [BubbleGlide]).
 *
 * Given where each bubble was a frame ago against its place ([was]: by group and bubble, dp), the layout holds on to it: a
 * fan stays there, carried along with its place, while that spot is still free and the new one isn't clearly better
 * ([stays]), and one gone aside under the HUD keeps to its side ([asideUnderHud]), so a bubble doesn't flip between two
 * spots of nearly the same cost from one frame to the next as the camera moves.
 */
fun layoutBubbles(
    groups: List<BubbleGroup>,
    size: Float,
    spacing: Float,
    lift: Float,
    minX: Float = Float.NEGATIVE_INFINITY,
    maxX: Float = Float.POSITIVE_INFINITY,
    minY: Float = Float.NEGATIVE_INFINITY,
    keepOff: BubbleBox? = null,
    air: Float = FIRE_AIR,
    was: ((group: Int, bubble: Int) -> BubbleOffset?)? = null,
): List<List<BubbleOffset>> {
    val out = arrayOfNulls<List<BubbleOffset>>(groups.size)
    val placed = ArrayList<BubbleOffset>()
    /** Whether each placed bubble was moved by the fire (or by one it moved). */
    val placedByFire = ArrayList<Boolean>()
    val ramp = size * 0.3f
    fun r01(v: Float) = v.coerceIn(0f, 1f)
    /** How far one over the other: 0 barely, 1 fully lined up. */
    fun stacked(ox: Float) = r01((ox / size - 0.2f) / 0.35f)
    fun soft(ox: Float, oy: Float) = min(1f, ox / ramp) * min(1f, oy / ramp)
    /** How far a fan of [fan] at ([x], [y]) (its place's offsets, [lift] above it) moves off the fire, or null. */
    fun offFire(fan: List<BubbleOffset>, x: Float, y: Float): BubbleOffset? {
        if (keepOff == null) return null
        val x0 = x + fan.minOf { it.x }; val y0 = y - lift + fan.minOf { it.y }
        return offBox(keepOff, x0, y0, x + fan.maxOf { it.x }, y - lift + fan.maxOf { it.y }, size / 2f + air, minX, maxX, minY)
    }
    for (g in groups.indices.sortedByDescending { groups[it].y }) {
        val at = groups[g]
        if (at.n <= 0) { out[g] = emptyList(); continue }
        // off the fire first: the fan starts from there, as if its place were (its pointers still go to the place), so
        // the pushes below keep it off the bubbles already placed as they always do
        val grp = offFire(fanOut(at.n, spacing = spacing, minX = minX - at.x, maxX = maxX - at.x), at.x, at.y)?.let { at.copy(x = at.x + it.x, y = at.y + it.y) } ?: at
        val fan = fanOut(grp.n, spacing = spacing, minX = minX - grp.x, maxX = maxX - grp.x)
        val lo = fan.minOf { it.x }; val hi = fan.maxOf { it.x }
        // the pairs that overlap where the fan starts: [dx, dy, ox, oy]
        val pairs = ArrayList<FloatArray>()
        for (f in fan) for (p in placed) {
            val dx = grp.x + f.x - p.x; val dy = grp.y + f.y - lift - p.y
            val ox = size - abs(dx); val oy = size - abs(dy)
            if (ox > 0f && oy > 0f) pairs += floatArrayOf(dx, dy, ox, oy)
        }
        // side by side (much the same height, or the other one already lifted): move aside, but not when they're
        // lined up (there no side is ever "chosen": moving aside would flip with the camera). Pushes from both
        // sides cancel (a squeeze), and what isn't settled sideways is settled upwards below.
        var right = 0f; var left = 0f
        for (q in pairs) {
            val beside = if (q[1] >= 0f) 1f else r01((q[3] / size - 0.5f) / 0.4f)
            val side = q[2] * beside * r01((0.95f - q[2] / size) / 0.2f) * soft(q[2], q[3])
            if (q[0] >= 0f) right = max(right, side) else left = max(left, side)
        }
        val roomLo = minX - grp.x - lo; val roomHi = maxX - grp.x - hi
        var sx = if (roomLo > roomHi) (roomLo + roomHi) / 2f else (right - left).coerceIn(roomLo, roomHi)
        // lined up: move above the other, as far as the sideways move didn't settle it. Over one that's already
        // above (lifted earlier) it's all the way or nothing: half way would land right on it.
        var up = 0f
        for (q in pairs) {
            val away = if (q[0] >= 0f) sx else -sx
            val settled = r01(away / q[2])
            val lined = if (q[1] >= 0f) r01(stacked(q[2]) * 2f) else stacked(q[2])
            up = max(up, (size + q[1]) * lined * soft(q[2], q[3]) * r01((1f - settled) / 0.5f))
        }
        var sy = -up
        // moving may land the fan on a bubble it was clear of: settle those too, upwards, a few times over
        for (round in 1 until 4) {
            var more = 0f
            for (f in fan) for (p in placed) {
                val dx = grp.x + f.x + sx - p.x; val dy = grp.y + f.y - lift + sy - p.y
                val ox = size - abs(dx); val oy = size - abs(dy)
                if (ox <= 0f || oy <= 0f) continue
                // a pair that overlapped from the start was pushed once already and stays as it is
                val first = min(size - abs(grp.x + f.x - p.x), size - abs(grp.y + f.y - lift - p.y))
                more = max(more, (size + dy) * stacked(ox) * soft(ox, oy) * r01(1f - first / ramp))
            }
            if (more <= 0.01f) break
            sy -= more
        }
        // Near the fire (moved by it, or on a bubble it moved: it lines its neighbours up along its top, squeezing them):
        // up off every bubble already placed, the ones it started on too, fading in with the overlap as the pushes above
        // do; then off the fire again should that have put it there (from below it), and so on
        var byFire = grp !== at
        fun onFireMoved() = fan.any { f ->
            placed.indices.any { i -> placedByFire[i] && abs(grp.x + f.x + sx - placed[i].x) < size && abs(grp.y + f.y - lift + sy - placed[i].y) < size }
        }
        for (pass in 0 until 3) {
            val m = offFire(fan, grp.x + sx, grp.y + sy)
            if (m != null) { sx += m.x; sy += m.y; byFire = true }
            else if (pass > 0) break
            if (!byFire && !onFireMoved()) break
            byFire = true
            for (round in 0 until 8) {
                var more = 0f
                for (f in fan) for (p in placed) {
                    val dx = grp.x + f.x + sx - p.x; val dy = grp.y + f.y - lift + sy - p.y
                    val ox = size - abs(dx); val oy = size - abs(dy)
                    if (ox > 0f && oy > 0f) more = max(more, (size + dy) * soft(ox, oy))
                }
                if (more <= 0.01f) break
                sy -= more
            }
        }
        // what's left of the overlap (a corner on a corner, where the pushes fade in): cleared the shortest way
        val clear = clearOf(fan.map { BubbleOffset(grp.x + it.x + sx, grp.y + it.y - lift + sy) }, placed, size, keepOff, air, minX, maxX)
        sx += clear.x; sy += clear.y
        // kept below the HUD (see [bubblePresence] for a place scrolled far past the top); pushed back down under it, a
        // bubble that lands on one it was pushed up off (or on the fire) goes aside instead ([asideUnderHud])
        val top = fan.minOf { it.y } - lift + grp.y + sy
        if (top < minY) sy += minY - top
        // where each was a frame ago, carried along with its place
        val before = was?.let { w -> fan.indices.map { i -> w(g, i)?.let { BubbleOffset(at.x + it.x, at.y + it.y) } } }
        var list = fan.map { BubbleOffset(grp.x + it.x + sx, grp.y + it.y - lift + sy) }
        if (top < minY) list = asideUnderHud(list, fan.map { grp.y + it.y - lift }, placed, size, keepOff, air, minX, maxX, before)
        // the layout chose another spot than a frame ago: it stays where it was while that's still free and the new one
        // isn't clearly better ([stays])
        if (before != null && before.all { it != null }) {
            val home = fanOut(at.n, spacing = spacing, minX = minX - at.x, maxX = maxX - at.x).map { BubbleOffset(at.x + it.x, at.y + it.y - lift) }
            // kept on the screen and below the HUD, and nudged off what came a hair too near as everything moved
            var kept = before.map { BubbleOffset(it!!.x.coerceIn(minX, maxX), max(it.y, minY)) }
            val nudge = clearOf(kept, placed, size, keepOff, air, minX, maxX)
            if (abs(nudge.x) + abs(nudge.y) <= SLOT) kept = kept.map { BubbleOffset(it.x + nudge.x, max(it.y + nudge.y, minY)) }
            if (stays(kept, list, home, placed, size, keepOff, air, minX, maxX, minY)) list = kept
        }
        out[g] = list
        placed += list
        repeat(list.size) { placedByFire += byFire }
    }
    return out.map { it ?: emptyList() }
}

/**
 * How far a fan of bubbles whose centres lie in [x0]..[x1] × [y0]..[y1] (dp) moves to keep [reach] (half a bubble and
 * its air) off [box], or null when it does already. Away from the box, as seen from the middle of its bottom edge (as
 * far below it as the fan reaches): a fan beside it moves out sideways and a little up, one over it up, one at its
 * corner both; the move is continuous in where the fan is, so a bubble following someone past the fire slides round
 * it. When that would leave the screen ([minX]..[maxX]) or go under the HUD ([minY]): straight up, else straight out
 * sideways (the nearer side first), whichever fits.
 */
private fun offBox(box: BubbleBox, x0: Float, y0: Float, x1: Float, y1: Float, reach: Float, minX: Float, maxX: Float, minY: Float): BubbleOffset? {
    val hw = (x1 - x0) / 2f + reach; val hh = (y1 - y0) / 2f + reach
    val l = box.left - hw; val r = box.right + hw; val t = box.top - hh; val b = box.bottom + hh
    val px = (x0 + x1) / 2f; val py = (y0 + y1) / 2f
    if (px <= l || px >= r || py <= t || py >= b) return null
    val cx = (l + r) / 2f
    val dx = px - cx; val dy = py - b // dy < 0: the fan is above the box's bottom edge
    val k = min(if (dx > 0f) (r - cx) / dx else if (dx < 0f) (l - cx) / dx else Float.POSITIVE_INFINITY, (t - b) / dy)
    val away = BubbleOffset((k - 1f) * dx, (k - 1f) * dy)
    val out = if (dx >= 0f) r - px else l - px
    val back = if (dx >= 0f) l - px else r - px
    // on the screen (a fan wider than it never is), and not up under the HUD: pushed back down it would land on the box
    // again (out sideways, the HUD's push down keeps it beside the box)
    fun fits(m: BubbleOffset) = (x1 - x0 > maxX - minX || (x0 + m.x >= minX - 0.01f && x1 + m.x <= maxX + 0.01f)) &&
        (m.y >= 0f || y0 + m.y >= minY - 0.01f)
    return listOf(away, BubbleOffset(0f, t - py), BubbleOffset(out, 0f), BubbleOffset(back, 0f)).firstOrNull { fits(it) } ?: away
}

/**
 * How far a fan whose bubbles sit at [fan] (dp, their centres) moves so its boxes ([size]) cover none of the bubbles already
 * [placed], when the pushes left a little ([layoutBubbles]: they fade in with the overlap, so a corner may still sit on a
 * corner): off the deepest overlap first, the shortest way, sideways away from it while it's beside it, up or down while
 * it's over or under it, and round the corner partly both (from a quarter to three quarters of the way round, one of them
 * in full), so the move turns the corner continuously instead of flipping from one to the other. On the screen
 * ([minX]..[maxX]: what the edge doesn't let it move sideways it moves up or down) and off [keepOff] (the fire, with [air]:
 * the other way, if that's off it). A fan caught between them after a few such moves (squeezed between two side by side)
 * goes up off every one, as the stack round the fire does.
 */
private fun clearOf(fan: List<BubbleOffset>, placed: List<BubbleOffset>, size: Float, keepOff: BubbleBox?, air: Float, minX: Float, maxX: Float): BubbleOffset {
    if (placed.isEmpty()) return BubbleOffset(0f, 0f)
    var mx = 0f; var my = 0f
    val lo = fan.minOf { it.x }; val hi = fan.maxOf { it.x }
    fun onFire(x: Float, y: Float) = keepOff != null && fan.any { keepOff.meets(it.x + x, it.y + y, size, air) }
    for (round in 0 until 6) {
        // the deepest overlap left: [dx, dy, ox, oy]
        var deep = CLEAR; var q: FloatArray? = null
        for (f in fan) for (p in placed) {
            val dx = f.x + mx - p.x; val dy = f.y + my - p.y
            val d = min(size - abs(dx), size - abs(dy))
            if (d > deep) { deep = d; q = floatArrayOf(dx, dy, size - abs(dx), size - abs(dy)) }
        }
        if (q == null) return BubbleOffset(mx, my)
        val dx = q[0]; val dy = q[1]; val ox = q[2]; val oy = q[3]
        // how far round the corner: 0 beside it (sideways is shorter), 1 over or under it
        val t = ox / (ox + oy)
        val side = if (dx >= 0f) ox else -ox
        val vert = if (dy > 0f) oy else -oy
        var sx = side * ((0.75f - t) / 0.25f).coerceIn(0f, 1f)
        var sy = vert * ((t - 0.25f) / 0.25f).coerceIn(0f, 1f)
        val room = sx.coerceIn(minX - lo - mx, maxX - hi - mx)
        if (abs(room - sx) > 0.01f) { sx = room; sy = vert }
        if (onFire(mx + sx, my + sy)) listOf(BubbleOffset(side, 0f), BubbleOffset(0f, vert), BubbleOffset(0f, -(size + dy)))
            .firstOrNull { m -> m.x in minX - lo - mx - 0.01f..maxX - hi - mx + 0.01f && !onFire(mx + m.x, my + m.y) }
            ?.let { sx = it.x; sy = it.y }
        mx += sx; my += sy
    }
    // caught between them: up off every one
    for (round in 0 until 3 * placed.size) {
        var more = 0f
        for (f in fan) for (p in placed) {
            val dx = f.x + mx - p.x; val dy = f.y + my - p.y
            if (min(size - abs(dx), size - abs(dy)) > CLEAR) more = max(more, size + dy)
        }
        if (more <= 0f) break
        my -= more
    }
    return BubbleOffset(mx, my)
}

/** An overlap of bubbles' boxes small enough to be none (dp): rounding. */
private const val CLEAR = 0.01f

/**
 * Room to spare (dp) a bubble needs to take a new spot between others: a slot just opening between two (or a squeeze just
 * letting go) isn't one yet, so a bubble doesn't drop in for a frame and out again ([asideUnderHud], [stays]).
 */
private const val SLOT = 4f

/**
 * How much better (dp nearer its place; under the HUD a step down counts double) another spot must be for a bubble to leave
 * the one it's at ([stays], [asideUnderHud]).
 */
private const val STICK = 26f

/** A move of a bubble against its place (dp) from one frame to the next that is a step to another spot, not a slide ([stays]). */
private const val JUMP = 12f

/**
 * Whether a fan stays where it was a frame ago ([kept], carried along with its place) instead of taking the spot the layout
 * gives it now ([new]): when that's a step away (more than [JUMP] for a bubble), the old spot is still free (on none of the
 * bubbles already [placed], off [keepOff] with [air], on the screen and below the HUD: [minX]..[maxX], [minY]) and the new
 * one isn't clearly better: no nearer its place by [STICK] on average ([home]: where its bubbles would be with nothing in
 * the way), or squeezed in between two with no room to spare ([SLOT]: a squeeze just letting go). So a bubble doesn't
 * flip between two spots of nearly the same cost from one frame to the next, nor drop into a slot that has just opened.
 */
private fun stays(
    kept: List<BubbleOffset>, new: List<BubbleOffset>, home: List<BubbleOffset>, placed: List<BubbleOffset>, size: Float,
    keepOff: BubbleBox?, air: Float, minX: Float, maxX: Float, minY: Float,
): Boolean {
    if (kept.indices.none { abs(kept[it].x - new[it].x) + abs(kept[it].y - new[it].y) > JUMP }) return false
    for (b in kept) {
        if (b.y < minY - 0.01f || b.x < minX - 0.01f || b.x > maxX + 0.01f || keepOff?.meets(b.x, b.y, size, air) == true) return false
        // just under the HUD it may share the air, as one pushed down there does ([asideUnderHud])
        val share = if (b.y <= minY + 0.01f) size * 0.15f else CLEAR
        for (p in placed) if (min(size - abs(b.x - p.x), size - abs(b.y - p.y)) > share) return false
    }
    fun cost(f: List<BubbleOffset>) = f.indices.sumOf { (abs(f[it].x - home[it].x) + abs(f[it].y - home[it].y)).toDouble() }.toFloat() / f.size
    if (cost(kept) <= cost(new) + STICK) return true
    // squeezed in between two side by side, with no room to spare on either side
    return new.any { b ->
        val row = placed.filter { abs(it.y - b.y) < size }
        row.any { it.x <= b.x && b.x - it.x - size < SLOT } && row.any { it.x >= b.x && it.x - b.x - size < SLOT }
    }
}

/**
 * Where the bubbles of a fan pushed back down under the HUD go ([layoutBubbles]; [fan]: their centres there, dp, its top
 * row just under the HUD): one that lands on a bubble already [placed] (the stack it was pushed up on, round the fire),
 * on another of its fan's, or on [keepOff] (the fire) goes aside on its own, to the nearest spot where it covers none of
 * them, as near the HUD as there is room: left or right of what's in its way, else lower down beside the stack (a step
 * down counts double a step aside), never lower than where it started ([lowest]) nor off the screen ([minX]..[maxX]); its
 * pointer still goes to its place, slanting, and the rest of its fan stays. With no room there it shares a little of the
 * bubbles' air ([size] is a bubble and its air: their spacing shrunk to 92 %, then 85 %); with none even so (the fire just
 * under the HUD, its people's bubbles filling the row) it goes further down, below where it started, its pointer going up
 * to its place. One sharing no more than the air there (15 % of [size]: the bubbles themselves don't touch) and off the
 * fire stays as it is, so a stack just reaching the HUD doesn't send it across. A new spot has a little room to spare
 * ([SLOT]: not a slot just opening between two).
 *
 * Where each was a frame ago ([was], by its index in the fan) holds it: one that went aside stays aside until it would share
 * nothing back on the stack, and keeps to its side and its spot (the stretch of free room it's in, as near the stack as
 * that goes) unless another is cheaper by [STICK], so it doesn't flip from one side of the stack to the other and back as
 * the camera moves.
 */
private fun asideUnderHud(
    fan: List<BubbleOffset>, lowest: List<Float>, placed: List<BubbleOffset>, size: Float,
    keepOff: BubbleBox?, air: Float, minX: Float, maxX: Float, was: List<BubbleOffset?>? = null,
): List<BubbleOffset> {
    val out = fan.toMutableList()
    val reach = size / 2f + air
    for (i in out.indices) {
        val b = out[i]
        val before = was?.getOrNull(i)?.takeIf { abs(it.x - b.x) + abs(it.y - b.y) > size / 4f }
        val others = placed + out.filterIndexed { j, _ -> j != i }
        val depth = others.maxOfOrNull { min(size - abs(b.x - it.x), size - abs(b.y - it.y)) } ?: 0f
        if (depth <= (if (before != null) CLEAR else size * 0.15f) && keepOff?.meets(b.x, b.y, size, air) != true) continue
        val start = max(b.y, lowest[i])
        val from = min(minX, b.x); val to = max(maxX, b.x)
        // down to where it started, its spacing shrunk a little if it must; else further down (its pointer going up)
        for ((sz, bottom) in listOf(size to start, size * 0.92f to start, size * 0.85f to start, size to Float.POSITIVE_INFINITY)) {
            // its height under the HUD, and each just under a bubble or the fire
            val under = others.map { it.y + sz } + listOfNotNull(keepOff?.let { it.bottom + reach })
            val levels = (listOf(b.y) + under.filter { it > b.y && it <= bottom }).sorted()
            var best: BubbleOffset? = null
            var cost = Float.POSITIVE_INFINITY
            // the spot in the stretch of room nearest where it was, and what it costs
            var kept: BubbleOffset? = null
            var keptCost = Float.POSITIVE_INFINITY
            var keptOff = size
            for (y in levels) {
                if ((y - b.y) * 2f >= cost + (if (before != null) STICK else 0f)) break
                // where it can't be at this height (open ranges of x): on a bubble, or on the fire
                val blocked = others.filter { abs(y - it.y) < sz - 0.01f }.mapTo(ArrayList()) { floatArrayOf(it.x - sz, it.x + sz) }
                if (keepOff != null && y + reach > keepOff.top && y - reach < keepOff.bottom) blocked += floatArrayOf(keepOff.left - reach, keepOff.right + reach)
                for (gap in freeRoom(blocked, from, to)) {
                    // the spot in it nearest the stack (where its place is), and what that costs
                    val x = b.x.coerceIn(gap[0], gap[1])
                    val c = abs(x - b.x) + (y - b.y) * 2f
                    if (c < cost && (gap[1] - gap[0] >= SLOT || x == b.x)) { cost = c; best = BubbleOffset(x, y) }
                    if (before == null) continue
                    val off = max(0f, max(gap[0] - before.x, before.x - gap[1])) + abs(y - before.y)
                    if (off < keptOff) { keptOff = off; kept = BubbleOffset(x, y); keptCost = c }
                }
            }
            if (kept != null && keptCost <= cost + STICK) best = kept
            if (best != null) { out[i] = best; break }
        }
    }
    return out
}

/**
 * The stretches of [from]..[to] in none of the open ranges [blocked], in order, [lo, hi] each: where two ranges just meet,
 * a stretch of none (a bubble fits there exactly).
 */
private fun freeRoom(blocked: List<FloatArray>, from: Float, to: Float): List<FloatArray> {
    val out = ArrayList<FloatArray>()
    var lo = from
    for (q in blocked.sortedBy { it[0] }) {
        if (lo > to + 0.01f) break
        if (q[0] >= lo - 0.02f) out += floatArrayOf(lo, max(lo, min(q[0], to)))
        lo = max(lo, q[1])
    }
    if (lo <= to + 0.01f) out += floatArrayOf(lo, max(lo, to))
    return out
}

/**
 * How much a place's bubbles show, 0..1: fully while the place is on screen or under the HUD (its bubbles hang
 * below the HUD, pointing up at it; the HUD is about four bubbles tall), fading out over the next two bubble
 * heights as it scrolls past the top ([minY] is the HUD's bottom plus half a bubble, dp). Faded bubbles don't
 * take taps.
 */
fun bubblePresence(anchorY: Float, minY: Float, size: Float): Float = ((anchorY - (minY - 6f * size)) / (2f * size)).coerceIn(0f, 1f)

/** How quickly a bubble glides to a new spot (1/s): a critically damped spring, 95 % of the way there in 0.2 s. */
const val GLIDE_RATE = 24f

/**
 * How far a bubble's spot may move against its place from one frame to the next and still be a slide (dp), besides half
 * again what its place moved: more is a step to another spot, which the bubble glides ([BubbleGlide]).
 */
const val GLIDE_FREE = 4f

/**
 * Bubbles glide to a new spot instead of jumping there. Each frame [layoutBubbles] gives every bubble its spot; what the
 * camera and the people do moves the bubbles along at once (a pan, a zoom, someone walking: each bubble keeps to its place,
 * and to the HUD and the screen's edges as its spot does), but when the spot steps away against its place (the layout chose
 * another: aside under the HUD, up off a squeeze, a fan grown by one) the bubble goes there on a critically damped spring
 * ([GLIDE_RATE]: no overshoot, there in about 0.2 s), its pointer still at its place. A bubble seen for the first time
 * starts at its spot (it fades in there); one no longer shown is forgotten. A few sums a bubble a frame.
 */
class BubbleGlide(private val rate: Float = GLIDE_RATE) {
    private class Track(var sx: Float, var sy: Float, var ax: Float, var ay: Float) {
        /** Where it shows against its spot (dp), and how fast that changes (dp/s). */
        var ex = 0f; var ey = 0f; var vx = 0f; var vy = 0f
        var seen = 0L
    }

    private val tracks = HashMap<String, Track>()
    private var time = Long.MIN_VALUE
    private var frame = 0L

    /** Where [id]'s bubble was laid out last, against its place (dp): what [layoutBubbles] holds it to (its `was`). */
    fun offset(id: String): BubbleOffset? = tracks[id]?.let { BubbleOffset(it.sx - it.ax, it.sy - it.ay) }

    /**
     * Moves the bubbles on to [nanos] (the frame's time) and takes this frame's [spots] of the bubbles [ids] (dp, centres)
     * over their [places] (where each one's pointer goes, dp); returns where each shows (dp), within [minX]..[maxX] and below
     * [minY] as its spot is.
     */
    fun step(
        nanos: Long, ids: List<String>, spots: List<BubbleOffset>, places: List<BubbleOffset>,
        minX: Float = Float.NEGATIVE_INFINITY, maxX: Float = Float.POSITIVE_INFINITY, minY: Float = Float.NEGATIVE_INFINITY,
    ): List<BubbleOffset> {
        val dt = if (time == Long.MIN_VALUE) 0f else ((nanos - time) / 1e9f).coerceIn(0f, 0.25f)
        time = nanos
        frame++
        val k = kotlin.math.exp(-rate * dt)
        val out = ArrayList<BubbleOffset>(ids.size)
        for (i in ids.indices) {
            val s = spots[i]; val a = places[i]
            val t = tracks.getOrPut(ids[i]) { Track(s.x, s.y, a.x, a.y) }
            // on along the spring towards the spot (exactly, for any frame time)
            if (dt > 0f) {
                val cx = t.vx + rate * t.ex; val cy = t.vy + rate * t.ey
                t.ex = (t.ex + cx * dt) * k; t.vx = (t.vx - rate * cx * dt) * k
                t.ey = (t.ey + cy * dt) * k; t.vy = (t.vy - rate * cy * dt) * k
            }
            // the spot's move against its place beyond a slide: the bubble glides it instead of jumping
            val jx = (s.x - t.sx) - (a.x - t.ax); val jy = (s.y - t.sy) - (a.y - t.ay)
            val j = kotlin.math.hypot(jx, jy)
            val free = GLIDE_FREE + 1.5f * kotlin.math.hypot(a.x - t.ax, a.y - t.ay)
            if (j > free) { val f = 1f - free / j; t.ex -= jx * f; t.ey -= jy * f }
            t.sx = s.x; t.sy = s.y; t.ax = a.x; t.ay = a.y; t.seen = frame
            // on the screen and below the HUD, as its spot is
            val x = (s.x + t.ex).coerceIn(min(minX, s.x), max(maxX, s.x))
            val y = max(s.y + t.ey, min(minY, s.y))
            if (x != s.x + t.ex) { t.ex = x - s.x; t.vx = 0f }
            if (y != s.y + t.ey) { t.ey = y - s.y; t.vy = 0f }
            out += BubbleOffset(x, y)
        }
        if (tracks.size > ids.size) tracks.values.removeAll { it.seen != frame }
        return out
    }
}

/**
 * The villager (id) a marker belongs to, when they're someone who lives here and is drawn on the map (so the
 * bubble can follow them): a request's giver, a happening's person while they stand at its place ([standing]), the
 * stranger of the day's surprise at the road (the pedlar, the pilgrim: [si.lanisce.lani.game.Surprises.stranger]),
 * someone at a spot of the landscape now ([keepers]: the charcoal burner by his pile). Null for the event, clusters and
 * everyone else, and for someone away from the clearing ([away]: not on the map, their bubble stays at the far place).
 */
fun markerVillager(
    m: TownMarker, state: GameState, active: List<ActiveHappening>, people: List<Villager>,
    standing: Map<String, TownPlace> = emptyMap(), away: Set<String> = emptySet(),
    keepers: List<si.lanisce.lani.game.scene.ActiveKeeper> = emptyList(),
): String? = when {
    m.id.startsWith(si.lanisce.lani.game.scene.Keepers.MARKER) ->
        si.lanisce.lani.game.scene.Keepers.of(keepers, m.id)?.keeper?.id?.takeIf { id -> people.any { it.id == id } && id !in away }
    m.id.startsWith("quest:") -> state.quests.firstOrNull { it.id == m.id.removePrefix("quest:") }?.let { q -> people.firstOrNull { it.name == q.giver }?.id }?.takeIf { it !in away }
    m.id.startsWith("happening:") -> active.firstOrNull { it.key == m.id.removePrefix("happening:") }?.person?.villager
        ?.takeIf { id -> people.any { it.id == id } && id !in away && (standing[id] ?: m.place) == m.place }
    m.id.startsWith("surprise:") -> m.id.removePrefix("surprise:").takeIf { id -> id in Surprises.strangers && people.any { it.id == id } }
    // someone to meet: at their home, where they're drawn
    m.id.startsWith("arrival:") -> m.id.removePrefix("arrival:").takeIf { id -> people.any { it.id == id } && id !in away }
    else -> null
}

/** The villager whose face a happening's marker shows in a small bubble: someone of [people] away from the clearing ([away]). */
fun markerFace(m: TownMarker, active: List<ActiveHappening>, people: List<Villager>, away: Set<String>): Villager? {
    if (!m.id.startsWith("happening:")) return null
    val id = active.firstOrNull { it.key == m.id.removePrefix("happening:") }?.person?.villager ?: return null
    return if (id in away) people.firstOrNull { it.id == id } else null
}

/** Markers grouped by place, in the order they came (the event first), for the bubble overlay. */
fun markersByPlace(markers: List<TownMarker>): Map<TownPlace, List<TownMarker>> = markers.groupBy { it.place }

/** Where visitors wait (see [si.lanisce.lani.game.scene.TownMarkers.homeOf]). */
val VISITORS = TownPlace.Spot("road")

/** More bubbles than this at one place fold into a "+n" bubble that opens the place's card. */
const val MAX_BUBBLES = 5

/** Which bubbles a crowded place shows first. */
val BUBBLE_ORDER = listOf(TownMarker.Kind.EVENT, TownMarker.Kind.HAPPENING, TownMarker.Kind.QUEST)

/**
 * At most [MAX_BUBBLES] per place, the event and the people there now before the requests (the scroll lists
 * those too), then one "+n" bubble that opens the place's card.
 */
fun foldBubbles(markers: List<TownMarker>): List<TownMarker> =
    markersByPlace(markers).flatMap { (place, all) ->
        val list = all.sortedBy { BUBBLE_ORDER.indexOf(it.kind) }
        // Visitors wait on the road (villagers whose home isn't built yet): one bubble for all of them,
        // which opens the road's card with their requests, so they don't crowd the camp.
        if (place == VISITORS && list.size > 1) listOf(
            TownMarker("cluster:${place.key()}", TownMarker.Kind.QUEST, "🧳", "${bi("townLogic.visitors", "where" to (place.where()))}: " + list.joinToString(", ") { it.label }, place),
        )
        else if (list.size <= MAX_BUBBLES) list
        else list.take(MAX_BUBBLES - 1) + TownMarker(
            "more:${place.key()}", TownMarker.Kind.HAPPENING, "+${list.size - MAX_BUBBLES + 1}",
            "${bi("townLogic.nMore", "n" to list.size - MAX_BUBBLES + 1)} ${place.where()}", place,
        )
    }

/**
 * Zoomed out, one bubble per place: a single marker as it is, several as one "cluster:" bubble with the most
 * important one's look (the event's, else someone there now, else a request) that opens the place's card.
 */
fun clusterBubbles(markers: List<TownMarker>): List<TownMarker> =
    markersByPlace(markers).map { (place, all) ->
        if (all.size == 1) all.single()
        else {
            val list = all.sortedBy { BUBBLE_ORDER.indexOf(it.kind) }
            TownMarker("cluster:${place.key()}", list.first().kind, list.first().emoji, "${place.where()}: " + list.joinToString(", ") { it.label }, place)
        }
    }

/** How many markers a cluster bubble stands for (its id's place: someone's requests each), for its count badge. */
fun clusterSize(m: TownMarker, markers: List<TownMarker>): Int? =
    if (m.id.startsWith("cluster:")) markers.filter { it.place == m.place }.sumOf { it.count } else null

/**
 * A bubble's count badge: how many markers a cluster stands for, how many requests someone has up ("⚒️ 2"); null for one.
 */
fun countBadge(m: TownMarker, markers: List<TownMarker>): String? =
    clusterSize(m, markers)?.toString() ?: m.count.takeIf { it > 1 }?.toString()

// --- the scroll: what's going on in the community ---------------------------------------------

/** Something in "Danes v vasi · Today in the village". */
sealed interface TodayItem {
    val place: TownPlace

    data class Event(val event: GameEvent, override val place: TownPlace) : TodayItem

    /** A happening on [now] (true) or later today, at [time]. */
    data class Happening(val on: ActiveHappening, val time: TimeOfDay, val now: Boolean, override val place: TownPlace) : TodayItem

    data class Task(val quest: Quest, override val place: TownPlace) : TodayItem

    /** Someone who joined the village and waits to be met ([id]; companion/VILLAGERS.md "Arrivals"): 🧳 or 👶, and what happened. */
    data class Arrival(val id: String, val emoji: String, val title: String, override val place: TownPlace) : TodayItem

    /**
     * Someone at a spot of the landscape now whose talk isn't done today (the charcoal burner by his pile; companion/VILLAGERS.md
     * "At the charcoal pile"), as his bubble is on the map.
     */
    data class Keeper(val keeper: si.lanisce.lani.game.scene.ActiveKeeper, override val place: TownPlace) : TodayItem
}

/** A place of the village in "Kraji · Places": how many buildings, the scenes that open there, what's on. */
data class PlaceEntry(val place: TownPlace, val count: Int, val scenes: List<SceneSpec>, val here: List<TownMarker>)

/** A scene the village can't open yet, and what it needs. */
data class LockedScene(val scene: SceneSpec, val needs: List<String>)

/** What a villager is up to: a request, a happening, or just being in a scene. */
sealed interface Doing {
    val place: TownPlace

    data class Asks(val quest: Quest, override val place: TownPlace) : Doing
    data class Busy(val on: ActiveHappening, override val place: TownPlace) : Doing
    data class Lives(val scene: SceneSpec, override val place: TownPlace) : Doing
}

/** Someone of the village ("Prebivalci · Villagers"). */
data class Resident(val name: String, val emoji: String, val doing: List<Doing>)

/**
 * What the village itself has on today (companion/GAME.md): the festival that is on (or the next one's countdown),
 * the day's surprise at the road (with its line), the projects with a step to take, and what's coming ("Jutri").
 */
data class VillageToday(
    val festival: FestivalDay? = null,
    /** The next festival within a few days, and how many days away. */
    val countdown: Pair<Festival, Long>? = null,
    val surprise: Surprise? = null,
    /** The day's surprise in a line ("Zala ima zate uganko. · …"). */
    val surpriseText: String = "",
    /** Projects with a next step the village can work on (not waiting for their age), the ones with a step today first. */
    val projects: List<ProjectOption> = emptyList(),
    val teasers: List<Teaser> = emptyList(),
) {
    /** What's there to do now: a festival to celebrate, a surprise to play (or the pedlar), today's project step. */
    val count: Int get() = (if (festival?.done == false) 1 else 0) + (if (surprise?.done == false) 1 else 0) + (if (projects.any { it.available }) 1 else 0)

    companion object {
        /** [story]: the storyteller's teaser ([si.lanisce.lani.game.scene.Stories.teaser]), for "Jutri". */
        fun of(state: GameState, today: LocalDate, cast: List<Villager> = emptyList(), story: Teaser? = null): VillageToday {
            val name = { id: String -> cast.firstOrNull { it.id == id }?.name?.substringAfterLast(' ') ?: Chest.nameOf(state, id) }
            val sp = Surprises.today(state, today)?.takeIf { Surprises.whoHere(state, it, today) }
            val projects = Projects.options(state, today).filter { !it.finished && it.waiting != ProjectOption.Why.AGE }
                .sortedWith(compareByDescending<ProjectOption> { it.available }.thenByDescending { it.done > 0 })
            return VillageToday(
                festival = if (state.age >= Age.TABOR) Calendar.on(state, today).firstOrNull() else null,
                countdown = Calendar.upcoming(today)?.let { (f, d) -> f to java.time.temporal.ChronoUnit.DAYS.between(today, d) },
                surprise = sp,
                surpriseText = sp?.let { Surprises.intro(it, name) }.orEmpty(),
                projects = projects,
                teasers = Tomorrow.teasers(state, today, story),
            )
        }
    }
}

/** The community overview: the scroll's (or book's) sections. */
data class TownOverview(
    val time: TimeOfDay,
    val today: List<TodayItem>,
    val places: List<PlaceEntry>,
    val locked: List<LockedScene>,
    val residents: List<Resident>,
    /** Newest first. */
    val chronicle: List<LogEntry>,
    /** The festival, the day's surprise, the projects, "Jutri". */
    val village: VillageToday = VillageToday(),
    /** The requests waiting in "📜 Kasneje · Later" ([si.lanisce.lani.game.QuestQueue]), in the order they come up, and why. */
    val later: List<QuestQueue.Waiting> = emptyList(),
) {
    val todayCount: Int get() = today.count { it !is TodayItem.Happening || it.now } + village.count

    companion object {
        /** Start hours of the parts of a day, in order (see [TimeOfDay.of]). */
        /**
         * [level]: the learner's level in a language, for the storyteller's story ("Jutri"). [keepers]: who is at a spot of
         * the landscape now ([si.lanisce.lani.game.scene.Keepers.here]), listed while their bubble is on the map.
         */
        fun of(
            state: GameState, scenes: List<SceneSpec>, now: LocalDateTime, cast: List<Villager> = emptyList(), level: (String) -> String = { "A1" },
            keepers: List<si.lanisce.lani.game.scene.ActiveKeeper> = emptyList(),
        ): TownOverview {
            val active = Happenings.active(scenes, state, now)
            val time = TimeOfDay.of(now.hour)
            val markers = TownMarkers.of(state, active, cast, now.toLocalDate(), keepers)

            val today = ArrayList<TodayItem>()
            state.event?.let { today += TodayItem.Event(it, TownMarkers.eventPlace(it.kind, state)) }
            // who joined the village and waits to be met: first, as nobody else does anything with them before
            for (m in markers.filter { it.id.startsWith("arrival:") }) today += TodayItem.Arrival(m.id.removePrefix("arrival:"), m.emoji, m.label, m.place)
            for (a in active) today += TodayItem.Happening(a, time, now = true, TownMarkers.placeOf(a, state))
            // who sits at a spot now with something to say (the burner by his pile), as his bubble: until his talk is done
            for (m in markers.filter { it.id.startsWith(si.lanisce.lani.game.scene.Keepers.MARKER) }) {
                si.lanisce.lani.game.scene.Keepers.of(keepers, m.id)?.let { today += TodayItem.Keeper(it, it.place) }
            }
            for ((t, a) in laterToday(scenes, state, now)) today += TodayItem.Happening(a, t, now = false, TownMarkers.placeOf(a, state))
            // the requests of who is here today (a tutor's request waits for its giver to come), two of each at most; the rest later
            val queue = Residents.queue(state, cast, now.toLocalDate())
            for (q in queue.up) today += TodayItem.Task(q, TownMarkers.questPlace(q.giver, q.source, state))

            // a house's room is there only in a house it is dealt to (its people live here)
            val dealt = si.lanisce.lani.game.scene.Homes.deal(scenes, state)
            val open = scenes.filter { Happenings.open(it, state) && si.lanisce.lani.game.scene.Homes.placed(it, state, dealt) }
            // every house's bubbles under the houses
            val byPlace = markers.groupBy { it.place.general() }
            // after the village's own places, the spots of the landscape where a scene opens (the stream bank, the pond), and
            // the projects' landmarks that lead into one (the vineyard)
            val spots = open.flatMap { it.from }.filter { it.startsWith("spot:") || it.startsWith("project:") }.distinct().map(TownPlace::of)
            val places = (townPlaces(state) + spots).map { p ->
                val count = (p as? TownPlace.At)?.let { at -> state.buildings.count { it.type == at.type } } ?: 1
                PlaceEntry(p, count, open.filter { p.key() in it.from }, byPlace[p].orEmpty())
            }
            val locked = scenes.filter { !Happenings.open(it, state) }.map { LockedScene(it, lockReasons(it, state)) }

            return TownOverview(
                time, today, places, locked, residents(state, open, active, cast, now, dealt, queue.up), state.log.asReversed(),
                VillageToday.of(state, now.toLocalDate(), cast, si.lanisce.lani.game.scene.Stories.teaser(scenes, state, now.toLocalDate(), level)),
                later = queue.later,
            )
        }

        /** Happenings that will be on later today (not now), by the part of the day they start in. */
        fun laterToday(scenes: List<SceneSpec>, state: GameState, now: LocalDateTime): List<Pair<TimeOfDay, ActiveHappening>> =
            Happenings.later(scenes, state, now)

        /**
         * Who lives here: the villagers with a request, and the people of the open scenes; one entry per name,
         * with everything they're up to (requests first, then what's on, then where they can be met). Someone of
         * the cast who doesn't live here (and isn't visiting today) isn't, whatever the scenes say.
         */
        private fun residents(
            state: GameState, open: List<SceneSpec>, active: List<ActiveHappening>, cast: List<Villager>, now: LocalDateTime,
            dealt: Map<String, SceneSpec> = emptyMap(), asks: List<Quest> = Residents.queue(state, cast, now.toLocalDate()).up,
        ): List<Resident> {
            // where a scene is: a house's room in its house, a place of its own
            fun placeOf(s: SceneSpec) = si.lanisce.lani.game.scene.Homes.buildingOf(s, state, null, dealt)?.let { TownPlace.of(it, state) } ?: TownMarkers.placeOf(s, state)
            val byName = LinkedHashMap<String, Pair<String, MutableList<Doing>>>()
            fun add(name: String, emoji: String, d: Doing) { byName.getOrPut(name) { emoji to ArrayList() }.second += d }
            val today = now.toLocalDate()
            val present = Residents.present(state, today)
            for (q in asks) add(q.giver, q.emoji, Doing.Asks(q, TownMarkers.questPlace(q.giver, q.source, state)))
            for (a in active) a.person?.let { add(it.name, it.emoji, Doing.Busy(a, TownMarkers.placeOf(a, state))) }
            for (s in open) for (p in s.people) {
                if (present != null && p.villager != null && p.villager !in present) continue
                val busy = byName[p.name]?.second.orEmpty().any { it is Doing.Busy && it.on.scene.id == s.id }
                if (!busy) add(p.name, p.emoji, Doing.Lives(s, placeOf(s)))
            }
            return byName.map { (name, v) -> Resident(name, v.first, v.second) }
        }
    }
}
