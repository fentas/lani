package si.lanisce.lani.game.render

import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.TentMove
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.TownSpots
import si.lanisce.lani.game.villagers.Doing
import si.lanisce.lani.game.villagers.Now
import si.lanisce.lani.game.villagers.Routine
import si.lanisce.lani.game.villagers.Trip
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.Where
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** What someone on the map is doing. */
enum class Activity {
    /** Standing about, breathing. */
    STAND,
    /** A few steps back and forth. */
    WALK,
    /** Sitting on the fallen log. */
    SIT,
    /** A rod in the water. */
    FISH,
    /** Splitting logs at the chopping block. */
    CHOP,
    /** Sitting by the fire at night. */
    WARM,
    /** Working the soil with a hoe: the field, the garden by the house. */
    HOE,
    /** At the anvil in front of the smithy, the hammer coming down. */
    HAMMER,
    /** Walking with a load ([Placed.load]): hay to the kozolec, water from the well, wood from the woodpile. */
    CARRY,
    /** Bent over the work: the hives, the sheep, the embers of the forge. */
    TEND,
    /** Kneeling at the stream's bank, washing. */
    WASH,
    /** Children playing: hopping, running about. */
    PLAY,
    /** Talking with whoever stands there, a hand going now and then. */
    CHAT,
    /** Waiting for the learner (a request, a happening): standing, a wave now and then to catch the eye. */
    WAVE,
}

/** What someone carries ([Activity.CARRY]). */
enum class Load { NONE, HAY, WOOD, WATER, BASKET }

/**
 * Someone drawn on the map: where ([x], [y] in world cells, [z] px up: on the log), what they're doing, which
 * way they face ([flip]: left), at which place, and their [slot] among the people sharing that place. [lantern]: they
 * carry a lit lantern (out at night: a ritual, the walk home from the fire), drawn with a warm glow; [load]: what they
 * carry.
 */
data class Placed(
    val villager: Villager, val x: Float, val y: Float, val activity: Activity, val flip: Boolean, val place: TownPlace,
    val slot: Int = 0, val z: Float = 0f, val lantern: Boolean = false, val load: Load = Load.NONE,
) {
    val id: String get() = villager.id
    val walking: Boolean get() = activity == Activity.WALK || activity == Activity.CARRY
    /** Sitting, crouching or kneeling: lower than standing (their bubble sits lower). */
    val seated: Boolean get() = activity == Activity.SIT || activity == Activity.WARM || activity == Activity.TEND || activity == Activity.WASH
}

/**
 * Where the people who live in the village are right now, for the renderer and the bubbles alike: pure, so both
 * agree. Each follows their day ([Routine], companion/VILLAGERS.md "A day in the village"): at their work (hoeing the
 * field, at the anvil, at the hives, washing at the stream, chopping at the woodpile, fishing at the pond), away on an
 * errand of it now and then (the hay to the kozolec, water from the well, wood from the woodpile: [errand]), resting (under
 * the linden, on the fallen log in the meadow, on the bench by the house), meeting (at the well, the market), the children
 * playing, and walking from one to the next: straight to the next place, out along the road across the clearing, into the
 * woods by the path, never through the fire or a building ([Walkways]). Nobody of the village wanders about: whoever
 * stands somewhere looks about now and then. In the dark evening some sit by the fire until their bedtime, the others are
 * home (not listed: their windows are lit, [darkWindows]); then they walk home and are in bed. At night only a ritual brings
 * someone out, with a lantern. In a storm everyone is inside; at a festival whoever is up gathers round the fire. Round the
 * fire they stand or sit beside it, facing it ([aroundFire]), never in its flames; at the other places beside each other,
 * never in a building. The strangers of the day's surprise (the pedlar, the pilgrim) keep to the road, day and night, a
 * step aside now and then. Babies are carried, so they aren't listed on their own.
 *
 * Someone with a happening on now ([TownMarkers.standing]) is there instead, day or night, waving now and then: by the
 * stream, at the kitchen's house, in front of the vineyard's terraces; so is who waits for the learner by daylight (a
 * request, someone to meet: [Routine.Day.calls]). At a place with work to do (the field, the anvil, the hives) they do it
 * between the waves: France harvests the wheat in his field. Someone whose happening is far from the clearing ([away]: in
 * the mountains or at the sea at the horizon, at a landmark out in the woods) isn't on the map at all; their bubble shows
 * their face there. So everyone is on the map at most once.
 */
object TownPeople {
    /** People at one place stand this far apart (world cells), first come first. */
    private val RING = arrayOf(
        floatArrayOf(0f, 0f), floatArrayOf(-1.1f, 0.35f), floatArrayOf(0.35f, -1.1f), floatArrayOf(1.2f, 0.45f),
        floatArrayOf(-0.45f, 1.2f), floatArrayOf(1.05f, -1.05f), floatArrayOf(-1.35f, -0.55f), floatArrayOf(0.6f, 1.45f),
    )

    /** Where the people at a place may stand: [RING], then the same again farther out; those in a building or a fire are left out. */
    private val AROUND = RING + RING.drop(1).map { floatArrayOf(it[0] * 1.9f, it[1] * 1.9f) }

    /**
     * Where the people at the fire stand or sit, first come first (see [aroundFire]), in nominal screen px: how much farther
     * out than beside it, and how far down the screen (negative: up, behind it). Beside it, a step behind, a step in front,
     * a step farther out; each on the right, then on the left.
     */
    private val FIRE_SLOTS = arrayOf(floatArrayOf(0f, 2f), floatArrayOf(3f, -4f), floatArrayOf(1f, 8f), floatArrayOf(8f, 2f))

    /** A person's half width and height on the map (nominal px), as a tap finds them (see [VillageRenderer]). */
    internal const val BODY_HALF = 5f
    internal const val BODY_HEIGHT = 8f

    /** The seed someone is drawn with ([VillageRenderer]'s people, [Effects.person]): their moments of work and waves. */
    internal fun drawSeed(id: String): Int = Noise.hash(id.hashCode(), 17)

    // ------------------------------------------------------------------ who stands where at a place

    /** [id]'s stay at [place] from [start] to [end] (minutes of the day), the [slot]th there. */
    private class Stay(val id: String, val order: Int, val place: TownPlace, val start: Double, val end: Double) { var slot = 0 }

    /**
     * Where each of the village stands at each place through a day: first come first served, each keeping their spot until
     * they leave (so nobody moves over when someone comes or goes), from their stretches ([Routine.stints], by daylight)
     * and their evening by the fire ([Routine.evening]). [most]: how many spots each place needs at most.
     */
    private class Stays(val byId: Map<String, List<Stay>>, val most: Map<TownPlace, Int>) {
        fun slot(id: String, place: TownPlace, minute: Double): Int? =
            byId[id]?.firstOrNull { it.place == place && minute >= it.start && minute < it.end }?.slot
    }

    private class StaysKey(val state: GameState, val people: List<Villager>, val date: LocalDate, val month: Int, val day: Routine.Day, val festival: Boolean) {
        override fun equals(other: Any?) = other is StaysKey && other.state === state && other.people === people && other.date == date &&
            other.month == month && other.day === day && other.festival == festival
        override fun hashCode() = (System.identityHashCode(state) * 31 + System.identityHashCode(people)) * 31 + date.hashCode() + month * 7 + System.identityHashCode(day) + if (festival) 1 else 0
    }

    private val staysMemo = object : LinkedHashMap<StaysKey, Stays>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<StaysKey, Stays>?) = size > 8
    }

    private fun stays(state: GameState, people: List<Villager>, date: LocalDate, month: Int, day: Routine.Day, festival: Boolean): Stays {
        val key = StaysKey(state, people, date, month, day, festival)
        synchronized(staysMemo) { staysMemo[key] }?.let { return it }
        val light = Routine.light(month)
        val all = ArrayList<Stay>()
        for ((n, v) in people.withIndex()) {
            if (v.art == "baby" || v.art in STRANGERS) continue
            val plan = Routine.plan(v, v.id in day.tellers)
            val home = day.homes[v.id] ?: TownMarkers.homeOf(v, state)
            val runs = ArrayList<Stay>()
            fun add(place: TownPlace, start: Double, end: Double) {
                if (end <= start) return
                val last = runs.lastOrNull()
                if (last != null && last.place == place && last.end >= start - 1e-6) runs[runs.lastIndex] = Stay(v.id, n, place, last.start, end)
                else runs += Stay(v.id, n, place, start, end)
            }
            // by daylight their stretches (the same place one after another: one stay), then the evening by the fire
            for (s in Routine.stints(v, plan, state, date, home)) add(s.place, maxOf(s.start, light[0]).toDouble(), minOf(s.end, maxOf(light[1], plan.up)).toDouble())
            Routine.evening(v, state, date, month, day.copy(festival = festival))?.let { add(TownPlace.Fire, it[0].toDouble(), it[1].toDouble()) }
            all += runs
        }
        val most = HashMap<TownPlace, Int>()
        for ((place, here) in all.groupBy { it.place }) {
            val active = ArrayList<Stay>()
            for (s in here.sortedWith(compareBy<Stay>({ it.start }, { it.order }))) {
                active.removeAll { it.end <= s.start }
                var k = 0
                while (active.any { it.slot == k }) k++
                s.slot = k
                active += s
                most[place] = maxOf(most[place] ?: 0, k + 1)
            }
        }
        return Stays(all.groupBy { it.id }, most).also { synchronized(staysMemo) { staysMemo[key] = it } }
    }

    /**
     * Where the [slot]th person at the fire stands (or sits) in world cells, the fire's middle at 0, 0: round it, beside it
     * to the right and the left beyond its reach ([fireClear]), never in its flames, nor just behind them (hidden by them)
     * or just in front (hiding them); the first on its right (the one a happening there brings), the next on its left, then
     * each side a step behind, a step in front, a step farther out ([FIRE_SLOTS]); past eight, round again farther out.
     * [scale]: the fire's size ([TownAnchors.FIRE_SCALE]).
     */
    fun aroundFire(slot: Int, scale: Float): FloatArray {
        val n = slot.coerceAtLeast(0)
        val s = FIRE_SLOTS[n / 2 % FIRE_SLOTS.size]
        val side = if (n % 2 == 0) 1f else -1f
        val sx = side * (fireClear(scale) + s[0] + n / (2 * FIRE_SLOTS.size) * 16f)
        return floatArrayOf(worldX(sx, s[1]), worldY(sx, s[1]))
    }

    /** How far to either side of the fire's middle its people stand (nominal px): past its reach by a body's half and a pixel. */
    fun fireClear(scale: Float): Float = TownAnchors.fireReach(scale) + BODY_HALF + 1f

    /**
     * Whether someone standing at world ([x], [y]) would be in the fire of [scale]: their body within its reach
     * ([TownAnchors.fireReach]) to the side, and between the top of its tallest flames and its front stones up and down.
     */
    fun inFire(x: Float, y: Float, scale: Float): Boolean {
        val sx = (x - y) * 4f; val sy = (x + y) * 2f
        return abs(sx) < TownAnchors.fireReach(scale) + BODY_HALF && sy > -(15f * scale + 6f) && sy - BODY_HEIGHT < TownAnchors.fireFront(scale)
    }

    /** [x], [y] (world cells), or beside the fire of [scale] at the same height when someone there would be in it ([inFire]). */
    internal fun offFire(x: Float, y: Float, scale: Float): FloatArray {
        if (!inFire(x, y, scale)) return floatArrayOf(x, y)
        val sx = (if (x - y < 0f) -1f else 1f) * fireClear(scale); val sy = (x + y) * 2f
        return floatArrayOf(worldX(sx, sy), worldY(sx, sy))
    }

    /** Who swings the axe at the woodpile; the others there fetch and carry. */
    private val CHOPPERS = setOf("smith", "man", "farmer", "shepherd", "winemaker", "innkeeper", "grandpa", "beekeeper", "woman", "hunter", "teacher", "aunt", "grandma")

    /** The sprites of the strangers at the road (Surprises.strangers): they don't go to bed or to the fire here. */
    private val STRANGERS = setOf("pedlar", "pilgrim")

    /** The spots in the woods below the village: a walk there goes by the forest path. */
    private val WOODS = setOf("meadow", "pond", "highseat", "path", "kopa")

    /** Seconds of a person's activity cycle (a stretch of chopping, then a rest). */
    private const val CYCLE = 16.0

    /** How fast people walk, world cells a second: an unhurried stride. */
    const val SPEED = 0.9f

    /** A carrier rests this long at each end (s): loading, unloading. */
    private const val PAUSE = 5f

    /** The day the tests and tools see when nobody says (a Monday in June). */
    val DEFAULT_DATE: LocalDate = LocalDate.of(2026, 6, 15)

    /**
     * Whether someone whose happening is at [place] is away from the clearing on a [w] × [h] canvas, so not on the map:
     * the land at the horizon, a landmark that isn't on this map or stands out in the woods, or the charcoal pile on a
     * canvas that doesn't go down that far (its burner isn't drawn then; his bubble is where the woods' are).
     */
    fun away(state: GameState, place: TownPlace, w: Int, h: Int): Boolean = when {
        TownMarkers.far(place) -> true
        place is TownPlace.Landmark -> TownAnchors.landmarkSpot(state, place.project, w, h)?.second != true
        place == TownPlace.Spot(TownSpots.KOPA) -> TownAnchors.spotAnchor(state, w, h, TownSpots.KOPA) == null
        else -> false
    }

    /** The villagers of [standing] who are away from the clearing on a [w] × [h] canvas (see [away]): only their bubbles show. */
    fun awayIds(state: GameState, standing: Map<String, TownPlace>, w: Int, h: Int): Set<String> =
        standing.filterValues { away(state, it, w, h) }.keys

    /**
     * The places of [active] happenings where someone not of [people] (drawn about the village) waits: a person of a scene
     * who isn't one of the cast, or isn't in the village's list. The renderer draws a plain villager there; the cast's own
     * stand there themselves (see [of]), never both.
     */
    fun waiting(state: GameState, active: List<si.lanisce.lani.game.scene.ActiveHappening>, people: List<Villager>): List<TownPlace> =
        active.filter { a -> a.person?.villager?.let { id -> people.none { it.id == id } } ?: true }
            .map { TownMarkers.placeOf(it, state) }.distinct()

    /**
     * @param time the animation clock (s): the walks, the chopping, the waves
     * @param hour the clock's hour with its fraction (the schedule's minute: [Routine.now])
     * @param standing who has a happening on now and where ([TownMarkers.standing]): they are there, or away (see [away])
     * @param date today, for the day's dice of everyone's day
     * @param day where each sleeps, the storytellers, who waits for the learner ([Routine.Day])
     */
    fun of(
        state: GameState, people: List<Villager>, time: Double, hour: Float, month: Int, w: Int, h: Int,
        storm: Boolean = false, festival: Boolean = false, standing: Map<String, TownPlace> = emptyMap(),
        date: LocalDate = DEFAULT_DATE, day: Routine.Day = Routine.Day(),
    ): List<Placed> {
        if (people.isEmpty() || storm) return emptyList()
        val comp = VillageLayout.composition(w, h, compact = false)
        val dark = Env(hour, month, storm).dark > 0.55f
        val fg = Foreground.of(state, w, h)
        val land = VillageLayout.of(state)
        val gate = TownAnchors.visitorSpot(state, TownPlace.Forest, comp, w, h)
        val routine = day.copy(busy = day.busy + standing, festival = day.festival || festival)
        val minute = hour * 60.0
        val out = ArrayList<Placed>(people.size)
        fun woods(p: TownPlace) = p == TownPlace.Forest || (p is TownPlace.Spot && p.id in WOODS && fg != null) ||
            (p is TownPlace.At && p.type == BuildingType.TENT && TentMove.moved(state))
        val scale = TownAnchors.FIRE_SCALE[state.age.ordinal]
        val blocked = blocked(state, land, scale, w, h)
        val ways = Walkways.of(state, w, h)
        // where someone stands at a place, clear of the fire (a building's corner that would be behind its flames: beside them)
        fun ground(v: Villager, place: TownPlace, seed: Int): FloatArray? =
            groundOf(v, place, state, comp, w, h, seed, land)?.let { offFire(it[0], it[1], scale) }
        fun way(a: FloatArray, b: FloatArray, aWoods: Boolean, bWoods: Boolean, seed: Int) = route(a, b, aWoods, bWoods, gate, land, seed, ways)
        val nows = HashMap<String, Now>()
        fun nowOf(v: Villager) = nows.getOrPut(v.id) { Routine.now(v, state, date, minute, month, routine) }

        // Who stands where at each place. Whoever waits there (a happening, a request, the strangers at the road) first, in
        // the village's order: round the fire whoever a happening brings there first, beside it on the right. Then the
        // others by their day, first come first served, each keeping their spot until they leave ([stays]): nobody moves
        // over when someone comes or goes, and away on an errand their spot waits for them. Anyone else after them all.
        val stays = stays(state, people, date, month, day, routine.festival)
        val waits = LinkedHashMap<TownPlace, MutableList<String>>()
        for (v in people) {
            if (v.art == "baby") continue
            val busy = standing[v.id]
            val place = when {
                busy != null -> busy
                v.art in STRANGERS -> TownMarkers.homeOf(v, state)
                else -> routine.calls[v.id]?.takeIf { it == nowOf(v).place && nowOf(v).doing == Doing.WAVE }
            } ?: continue
            waits.getOrPut(place) { ArrayList() } += v.id
        }
        // the happenings' people round the fire before those called there
        waits[TownPlace.Fire]?.sortBy { if (standing[it] == TownPlace.Fire) 0 else 1 }
        val extra = HashMap<TownPlace, Int>() // place → how many have come there besides
        fun slotOf(v: Villager, place: TownPlace, at: Double): Int {
            val k = waits[place]?.size ?: 0
            waits[place]?.indexOf(v.id)?.takeIf { it >= 0 }?.let { return it }
            val m = if (at < Routine.plan(v, v.id in day.tellers).up) -1.0 else at
            stays.slot(v.id, place, m)?.let { return k + it }
            val n = extra[place] ?: 0
            extra[place] = n + 1
            return k + (stays.most[place] ?: 0) + n
        }

        for (v in people) {
            if (v.art == "baby") continue
            val seed = Noise.hash(v.id.hashCode(), 11)
            // someone with a happening on is at its place (or far away: then not here at all)
            val busy = standing[v.id]
            if (busy != null && away(state, busy, w, h)) continue
            // the strangers of the day's surprise (the pedlar, the pilgrim) keep to the road, night or feast; the charcoal
            // burner sits by his pile; a stranger with a happening at the fire sits round it
            if (v.art in STRANGERS || busy == TownPlace.Spot(TownSpots.KOPA)) {
                val place = busy ?: TownMarkers.homeOf(v, state)
                val slot = slotOf(v, place, minute)
                if (busy == TownPlace.Fire) {
                    val g = aroundFire(slot, scale)
                    out += Placed(v, g[0], g[1], Activity.WARM, flip = g[0] - g[1] > 0f, place = busy, slot = slot)
                    continue
                }
                val g = (if (busy != null) TownAnchors.visitorSpot(state, busy, comp, w, h) else ground(v, place, seed)) ?: continue
                out += idle(v, g, place, slot, time, seed, if (busy != null) Activity.WARM else Activity.STAND)
                continue
            }
            val now = nowOf(v)
            if (routine.festival && now.where != Where.ASLEEP && busy == null) {
                val n = out.size
                val a = PI * 2 * (0.13 + n * 0.29)
                val r = 4.1f + (n % 2) * 0.5f
                // a wide ring round the fire, beside it rather than in it or behind its flames
                val p = offFire(cos(a).toFloat() * r, sin(a).toFloat() * r, scale)
                out += Placed(v, p[0], p[1], Activity.STAND, flip = p[0] - p[1] > 0f, place = TownPlace.Fire, slot = n)
                continue
            }
            if (!now.shown) continue
            val to = now.place ?: continue
            // waiting for the learner where there is work (the field, the anvil, the hives): at it, a wave between
            val waiting = busy != null || routine.calls[v.id] == to
            val work = if (waiting && !dark) workAt(to) else null
            val doing = work ?: now.doing
            val at = when {
                to == TownPlace.Fire -> floatArrayOf(0f, 0f)
                waiting && work == null -> TownAnchors.visitorSpot(state, to, comp, w, h)?.let { offFire(it[0], it[1], scale) }
                else -> ground(v, to, seed)
            } ?: continue
            // their own spot there (kept for them while they're away on an errand, taken on the way there): at the fire their
            // place round it, else beside whoever came before them
            val slot = slotOf(v, to, minute)
            val spot = if (to == TownPlace.Fire) aroundFire(slot, scale) else spotAt(at, slot, blocked)
            // away on an errand of their work: there and back, the load one way; beside whoever is where they load and unload
            now.errand?.let { trip ->
                val src0 = ground(v, trip.from, seed); val dst0 = ground(v, trip.to, seed)
                if (src0 != null && dst0 != null) {
                    val src = if (trip.from == to) spot else spotAt(src0, slotOf(v, trip.from, minute), blocked)
                    val dst = if (trip.to == to) spot else spotAt(dst0, slotOf(v, trip.to, minute), blocked)
                    out += errand(v, spot, to, src, dst, trip, time, seed) { a, b, ap, bp -> way(a, b, woods(ap), woods(bp), seed) }
                    continue
                }
            }
            // where they are once there: their spot, or their seat (the first to sit in the meadow, on the fallen log)
            val dest = seat(to, doing, slot, fg, comp) ?: spot
            // on the way: from their spot where they were, along the paths and the road (or the forest path) to their spot
            // where they go, never through the fire or a building; a long way at a brisker step, there by [Routine.WALK_S]
            val from = now.from
            if (from != null && (now.where == Where.HOMEWARD || from != to)) {
                val left = minute - now.since / 60.0 - 1e-3
                val was = slotOf(v, from, left)
                val start = if (from == TownPlace.Fire) aroundFire(was, scale)
                else seat(from, Routine.now(v, state, date, left, month, routine).doing, was, fg, comp) ?: ground(v, from, seed)?.let { spotAt(it, was, blocked) }
                if (start != null) {
                    val way = way(start, dest, woods(from), woods(to), seed)
                    val len = Walkways.length(way)
                    val pace = pace(len)
                    val since = now.since.toFloat()
                    if (since * pace < len) {
                        val (p, dir) = along(way, since * pace)
                        out += Placed(v, p[0], p[1], Activity.WALK, flip = dir < 0f, place = to, slot = slot, lantern = now.lantern && dark)
                        continue
                    }
                }
            }
            // home again: in the house
            if (now.where == Where.HOMEWARD) continue
            // something else at the same place, somewhere else there (from the sheep to the log): a few steps over
            now.before?.let { was ->
                val a = seat(to, was, slot, fg, comp) ?: spot
                if (hypot(dest[0] - a[0], dest[1] - a[1]) > 0.05f) {
                    val way = way(a, dest, woods(to), woods(to), seed)
                    if (now.begun * SPEED < Walkways.length(way)) {
                        val (p, dir) = along(way, now.begun.toFloat() * SPEED)
                        out += Placed(v, p[0], p[1], Activity.WALK, flip = dir < 0f, place = to, slot = slot)
                        continue
                    }
                }
            }
            // there: at the fire in their place round it, else at their spot at the place, the others there beside them
            out += placedAt(v, spot, to, if (work != null) now.copy(doing = work) else now, slot, time, seed, fg, comp, dark, to == TownPlace.Fire, wave = work != null)
        }
        return out
    }

    /** How fast someone walks a way of [len] cells: an unhurried stride, brisker on a long way so it takes no longer than [Routine.WALK_S]. */
    private fun pace(len: Float): Float = maxOf(SPEED, len / Routine.WALK_S.toFloat())

    /** The work someone waiting for the learner at [place] does between the waves: the field's, the anvil's, the hives'; else none. */
    private fun workAt(place: TownPlace): Doing? = when ((place as? TownPlace.At)?.type) {
        BuildingType.FIELD -> Doing.HOE
        BuildingType.SMITHY -> Doing.HAMMER
        BuildingType.BEEHIVE -> Doing.TEND
        else -> null
    }

    /**
     * Where nobody stands, world cells: in a building (a field is walked in), in the fire of [scale] ([inFire]), in the
     * campsite's fire or its tent at the pond.
     */
    private fun blocked(state: GameState, land: Terrain, scale: Float, w: Int, h: Int): (Float, Float) -> Boolean {
        val solid = state.buildings.filter { it.type.onPlot && it.type != BuildingType.FIELD && it.plot in 0 until VillageLayout.MAX_PLOTS }.map { land.centers[it.plot] }
        val camp = Campsite.of(state, w, h)
        return { x, y ->
            solid.any { abs(x - it[0]) < 1.7f && abs(y - it[1]) < 1.7f } || inFire(x, y, scale) || camp != null && (
                abs(x - camp.wx) < 1.7f && abs(y - camp.wy) < 1.7f ||
                    ((x - camp.fireX) - (y - camp.fireY)).let { abs(it) * 4f < 5.5f } && ((x - camp.fireX) + (y - camp.fireY)) * 2f in -12f..10f
                )
        }
    }

    /** The [slot]th place of [g]'s people (world cells): beside whoever stands there already, not in a building or a fire ([blocked]). */
    private fun spotAt(g: FloatArray, slot: Int, blocked: (Float, Float) -> Boolean): FloatArray {
        val free = AROUND.filter { !blocked(g[0] + it[0], g[1] + it[1]) }
        val ring = if (free.isEmpty()) RING[slot % RING.size] else free[slot % free.size]
        return floatArrayOf(g[0] + ring[0], g[1] + ring[1])
    }

    /**
     * Someone at their spot [g] (world cells) at [place] doing what [now] says, the [slot]th there (beside whoever came
     * before them); or, [fire], at their place round the fire, facing it. Whoever stands or keeps watch looks about now and
     * then; with [wave] (waiting for the learner at their work) they wave between the work.
     */
    private fun placedAt(
        v: Villager, g: FloatArray, place: TownPlace, now: Now, slot: Int, time: Double, seed: Int,
        fg: Foreground?, comp: Composition, dark: Boolean, fire: Boolean = false, wave: Boolean = false,
    ): Placed {
        var x = g[0]; var y = g[1]
        val phase = ((time / CYCLE + Noise.rnd(seed, 1)) % 1.0).toFloat()
        // round the fire they face it; elsewhere towards the middle of the village
        var flip = if (fire) x - y > 0f else x > 0f
        var z = 0f
        val activity = when (now.doing) {
            Doing.STAND, Doing.WATCH -> Activity.STAND
            Doing.HOE -> Activity.HOE
            Doing.HAMMER -> Activity.HAMMER
            // gathering what they came for (Micka's wood before dawn)
            Doing.CARRY -> Activity.TEND
            Doing.TEND -> Activity.TEND
            Doing.WASH -> Activity.WASH
            Doing.CHOP -> if (phase < 0.6f && v.art in CHOPPERS) Activity.CHOP else Activity.STAND
            Doing.FISH -> if (phase < 0.75f) Activity.FISH else Activity.STAND
            Doing.SIT -> Activity.SIT
            // by the fire they sit warming their hands; elsewhere they just sit
            Doing.WARM -> if (fire) Activity.WARM else Activity.SIT
            Doing.CHAT -> Activity.CHAT
            Doing.PLAY -> Activity.PLAY
            Doing.WAVE -> Activity.WAVE
        }
        val log = if (activity == Activity.SIT) seat(place, Doing.SIT, slot, fg, comp) else null
        when {
            fire -> Unit // facing the fire
            // on top of the fallen log in the meadow
            log != null -> { x = log[0]; y = log[1]; z = 2.6f; flip = false }
            activity == Activity.FISH || activity == Activity.WASH -> flip = false // the water lies up and to the left of its bank
            activity == Activity.CHOP -> flip = (seed ushr 6) % 2 == 0
            activity == Activity.HOE || activity == Activity.TEND -> flip = (seed ushr 7) % 2 == 0
            // two at a place talk to each other: the first faces the second (up and to the left of them), the second back
            activity == Activity.CHAT -> flip = slot % 2 == 0
            // standing about or keeping watch: looking this way and that now and then
            activity == Activity.STAND -> if (lookingAway(time, seed)) flip = !flip
        }
        // waiting at their work: a wave now and then (as the renderer draws one), the work between
        val shown = if (wave && !fire && Effects.waving(time, drawSeed(v.id)) >= 0f) Activity.WAVE else activity
        return Placed(v, x, y, shown, flip, place, slot, z, lantern = now.lantern && dark && now.ritual != null)
    }

    /**
     * Where the [slot]th at [place] sits doing [doing], when it isn't their spot there: the first to sit in the meadow on the
     * fallen log (see the renderer's woods: the log lies along +x at the meadow's left); else null.
     */
    private fun seat(place: TownPlace, doing: Doing, slot: Int, fg: Foreground?, comp: Composition): FloatArray? {
        if ((doing != Doing.SIT && doing != Doing.WARM) || place != TownPlace.Spot("meadow") || fg == null || slot != 0) return null
        val lx = fg.meadowX - 18f - comp.fireX; val ly = fg.meadowY + 5f - comp.fireY
        return floatArrayOf(worldX(lx, ly) + 1.1f, worldY(lx, ly) + 0.4f)
    }

    /** Whether someone standing about looks the other way now: a few seconds of every ten or so, each at their own moment. */
    private fun lookingAway(time: Double, seed: Int): Boolean {
        val period = 9.0 + Noise.rnd(seed, 5) * 6.0
        return ((time / period + Noise.rnd(seed, 6)) % 1.0) < 0.3
    }

    /** What a load from [from] is: hay from the field (or the kozolec), water from the well, wood from the woodpile, else a basket. */
    private fun loadFrom(from: TownPlace): Load = when {
        from is TownPlace.At && (from.type == BuildingType.FIELD || from.type == BuildingType.KOZOLEC) -> Load.HAY
        from is TownPlace.At && from.type == BuildingType.WELL -> Load.WATER
        from == TownPlace.Spot("woodpile") -> Load.WOOD
        else -> Load.BASKET
    }

    /**
     * Someone on an errand of their work at [work] (world cells; the place [place]): out to [src] where the load is (unless it
     * is the work's own place: the field's hay), loading it, with it to [dst], unloading, and back to the work: [trip]'s
     * [Routine.ERRAND] minutes, [Trip.since] seconds into them. The time to spare goes on a word at the well, the wood gathered
     * at the pile, the hay hung on the kozolec's racks, so they are back at the work as the errand ends (the scenes have them
     * back then). The ways by [way] (from, to, their places), round the fire and the buildings; a long one at a brisker step.
     * [src] and [dst] are their spots there, beside whoever else is there.
     */
    private fun errand(
        v: Villager, work: FloatArray, place: TownPlace, src: FloatArray, dst: FloatArray, trip: Trip,
        time: Double, seed: Int, way: (FloatArray, FloatArray, TownPlace, TownPlace) -> List<FloatArray>,
    ): Placed {
        val fromWork = trip.from != place
        val toWork = trip.to != place
        val out = if (fromWork) way(work, src, place, trip.from) else null
        val haul = way(src, dst, trip.from, trip.to)
        val back = if (toWork) way(dst, work, trip.to, place) else null
        val total = Routine.ERRAND * 60f
        val walked = (out?.let(Walkways::length) ?: 0f) + Walkways.length(haul) + (back?.let(Walkways::length) ?: 0f)
        // the walks at an unhurried stride, brisker when they wouldn't fit with the loading and a little while to spare
        val pace = maxOf(SPEED, walked / (total - 2 * PAUSE - 20f).coerceAtLeast(1f))
        val spare = (total - 2 * PAUSE - walked / pace).coerceAtLeast(0f)
        // the spare time at the load's place when it isn't the work's (a word at the well), else where it goes (the racks)
        val lingerAtSrc = fromWork
        val load = loadFrom(trip.from)
        var t = trip.since.toFloat()
        fun walking(w: List<FloatArray>, len: Float, to: TownPlace, carry: Load): Placed? {
            val dur = len / pace
            if (t < dur) {
                val (p, dir) = along(w, t * pace)
                return Placed(v, p[0], p[1], if (carry == Load.NONE) Activity.WALK else Activity.CARRY, dir < 0f, to, load = carry)
            }
            t -= dur
            return null
        }
        fun at(g: FloatArray, at: TownPlace, dur: Float, activity: Activity): Placed? {
            if (t >= dur) { t -= dur; return null }
            // a word at the well: facing the middle of the village, where the others there stand
            return Placed(v, g[0], g[1], activity, if (activity == Activity.CHAT) g[0] > 0f else (seed ushr 7) % 2 == 0, at)
        }
        val lingering = when {
            !lingerAtSrc -> Activity.TEND // the hay hung on the racks
            trip.from is TownPlace.At && (trip.from as TownPlace.At).type == BuildingType.WELL -> Activity.CHAT
            trip.from == TownPlace.Spot("woodpile") && v.art in CHOPPERS -> Activity.CHOP
            else -> Activity.TEND
        }
        out?.let { w -> walking(w, Walkways.length(w), trip.from, Load.NONE)?.let { return it } }
        at(src, trip.from, PAUSE + if (lingerAtSrc) spare else 0f, if (lingerAtSrc && t >= PAUSE) lingering else Activity.TEND)?.let { return it }
        walking(haul, Walkways.length(haul), trip.to, load)?.let { return it }
        at(dst, trip.to, PAUSE + if (lingerAtSrc) 0f else spare, if (!lingerAtSrc && t >= PAUSE) lingering else Activity.TEND)?.let { return it }
        back?.let { w -> walking(w, Walkways.length(w), place, Load.NONE)?.let { return it } }
        // back at the work (the errand's last moment)
        return Placed(v, work[0], work[1], Activity.STAND, work[0] > 0f, place)
    }

    /**
     * A stranger at [g] (the pedlar, the pilgrim at the road), standing by their things, a step aside and back now and then
     * and looking about; or [activity] there (the burner warming himself by his pile).
     */
    private fun idle(v: Villager, g: FloatArray, place: TownPlace, slot: Int, time: Double, seed: Int, activity: Activity): Placed {
        val ring = RING[slot % RING.size]
        var x = g[0] + ring[0]; val y = g[1] + ring[1]
        if (activity == Activity.WARM) return Placed(v, x, y, Activity.WARM, false, place, slot) // the pile is to his right
        val period = 24.0 + Noise.rnd(seed, 2) * 12.0
        val u = ((time / period + Noise.rnd(seed, 3)) % 1.0).toFloat()
        // most of the while standing; a step out (half a cell) at 0.55, back at 0.85
        val out = when {
            u < 0.55f -> 0f
            u < 0.6f -> (u - 0.55f) / 0.05f
            u < 0.85f -> 1f
            u < 0.9f -> 1f - (u - 0.85f) / 0.05f
            else -> 0f
        }
        x += out * 0.5f
        val moving = u in 0.55f..0.6f || u in 0.85f..0.9f
        val flip = if (moving) u >= 0.85f else (x > 0f) != lookingAway(time, seed)
        return Placed(v, x, y, if (moving) Activity.WALK else Activity.STAND, flip, place, slot)
    }

    /**
     * The way from [a] to [b] (world cells): into or out of the woods by the path's gate; from one side of the road to the
     * other, out to the road (on their lane), along it and in again; else straight to it. Each stretch round
     * the fire and the buildings in the way ([Walkways]): past the fire on its nearer side round the plaza, never through it.
     */
    internal fun route(a: FloatArray, b: FloatArray, aWoods: Boolean, bWoods: Boolean, gate: FloatArray?, land: Terrain, seed: Int, ways: Walkways.Ways): List<FloatArray> {
        val pts = ArrayList<FloatArray>(4)
        pts += a
        if (aWoods != bWoods && gate != null) pts += gate
        else if (!aWoods && !bWoods && byRoad(a, b, land)) {
            val lane = ((seed ushr 9) % 3 - 1) * 0.35f
            // the road's points, those in the fire's reach left out (the way goes round it)
            for (p in listOf(floatArrayOf(a[0], lane + land.roadY(a[0])), floatArrayOf(b[0], lane + land.roadY(b[0]))))
                if (ways.blocks.none { it.inside(p[0], p[1]) }) pts += p
        }
        pts += b
        return ways.along(pts)
    }

    /**
     * Whether the way from [a] to [b] goes by the road: they're on either side of it (each more than a step off it); a walk
     * on one side, between the houses, goes straight (round them).
     */
    private fun byRoad(a: FloatArray, b: FloatArray, land: Terrain): Boolean {
        val da = a[1] - land.roadY(a[0]); val db = b[1] - land.roadY(b[0])
        return (da > 1.2f && db < -1.2f) || (da < -1.2f && db > 1.2f)
    }

    /** The point [d] cells along [way], and which way they face there (screen x: negative is left). */
    internal fun along(way: List<FloatArray>, d: Float): Pair<FloatArray, Float> {
        var left = d
        for (i in 1 until way.size) {
            val a = way[i - 1]; val b = way[i]
            val seg = hypot(b[0] - a[0], b[1] - a[1])
            val dir = (b[0] - a[0]) - (b[1] - a[1])
            if (left <= seg || i == way.size - 1) {
                val f = if (seg <= 0f) 1f else (left / seg).coerceIn(0f, 1f)
                return floatArrayOf(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f) to dir
            }
            left -= seg
        }
        return way.last() to 0f
    }

    /**
     * Where someone is at [place], in world cells: at a building, the one [place] names (a house of its own by its id; a
     * type's by the villager, so people spread out over the fields), in the field (it's walked in: whoever is there works
     * it), else at its right front corner; elsewhere the place's visitor spot. A building of [v]'s home types (the old way: their dwellings
     * shared out by id) when [place] is one of their homes' types without an id.
     */
    private fun groundOf(v: Villager, place: TownPlace, state: GameState, comp: Composition, w: Int, h: Int, seed: Int, land: Terrain): FloatArray? {
        if (place is TownPlace.At && place.type.onPlot && !(place.type == BuildingType.TENT && TentMove.moved(state))) {
            val b = buildingOf(place, state, seed)
            if (b != null && b.plot in 0 until VillageLayout.MAX_PLOTS) {
                val c = land.centers[b.plot]
                return when {
                    place.type == BuildingType.FIELD -> floatArrayOf(c[0] + ((seed ushr 3) % 3 - 1) * 0.7f, c[1] + 0.3f)
                    else -> floatArrayOf(c[0] + 2.0f, c[1] + 0.6f)
                }
            }
        }
        return TownAnchors.visitorSpot(state, place, comp, w, h)
    }

    /** The building [place] is: by its id; the houses' first (the one [TownPlace.At] without an id names); another type's by [seed]. */
    private fun buildingOf(place: TownPlace.At, state: GameState, seed: Int): Building? {
        place.id?.let { id -> return state.buildings.firstOrNull { it.id == id } }
        if (place.type == BuildingType.HOUSE) return TownPlace.first(state, place.type)
        val all = state.buildings.filter { it.type == place.type && it.plot in 0 until VillageLayout.MAX_PLOTS }.sortedBy { it.plot }
        return if (all.isEmpty()) null else all[(seed ushr 3).mod(all.size)]
    }

    /**
     * The buildings whose windows are dark now (ids), when the lamps are lit elsewhere: of the buildings people sleep in
     * ([Routine.Day.homes]), those where nobody is up at home, and nobody went to bed in the last [Routine.LIGHTS_OUT]
     * minutes (the lamp burns a little while, then it's out); every other building from ten at night until five.
     */
    fun darkWindows(
        state: GameState, people: List<Villager>, hour: Float, month: Int, date: LocalDate = DEFAULT_DATE, day: Routine.Day = Routine.Day(),
        standing: Map<String, TownPlace> = emptyMap(),
    ): Set<String> {
        val minute = hour * 60.0
        val routine = day.copy(busy = day.busy + standing)
        val lit = HashSet<String>()
        val homes = HashSet<String>()
        for (v in people) {
            if (v.art == "baby" || v.art in STRANGERS) continue
            val home = day.homes[v.id] as? TownPlace.At ?: continue
            val b = (home.id?.let { id -> state.buildings.firstOrNull { it.id == id } } ?: TownPlace.first(state, home.type)) ?: continue
            homes += b.id
            if (b.id in lit) continue
            val now = Routine.now(v, state, date, minute, month, routine)
            val on = when (now.where) {
                Where.INDOORS -> true
                // the lamp burns a little while after they went to bed
                Where.ASLEEP -> (if (minute >= Routine.LIGHTS_OUT) Routine.now(v, state, date, minute - Routine.LIGHTS_OUT, month, routine)
                else Routine.now(v, state, date.minusDays(1), minute - Routine.LIGHTS_OUT + 1440, month, routine)).where != Where.ASLEEP
                else -> false
            }
            if (on) lit += b.id
        }
        val late = minute >= 22 * 60 || minute < 5 * 60
        return state.buildings.filter { b -> if (b.id in homes) b.id !in lit else late }.map { it.id }.toSet()
    }

    private fun worldX(sx: Float, sy: Float): Float = (sx / 4f + sy / 2f) / 2f
    private fun worldY(sx: Float, sy: Float): Float = (sy / 2f - sx / 4f) / 2f

    /** Someone's nominal-canvas point above their head (for a bubble), given their feet at ([x], [y]) and a sprite [height] px tall. */
    fun headAnchor(p: Placed, comp: Composition, height: Float): CanvasPoint =
        CanvasPoint(comp.fireX + (p.x - p.y) * 4f, comp.fireY + (p.x + p.y) * 2f - p.z - height - 2f)

    /** Whether [a] and [b] would stand on each other (for tests and layout checks). */
    fun overlap(a: Placed, b: Placed): Boolean = abs(a.x - b.x) < 0.3f && abs(a.y - b.y) < 0.3f
}
