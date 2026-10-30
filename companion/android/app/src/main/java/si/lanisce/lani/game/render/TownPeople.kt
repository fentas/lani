package si.lanisce.lani.game.render

import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.TentMove
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.TownSpots
import si.lanisce.lani.game.villagers.Doing
import si.lanisce.lani.game.villagers.Now
import si.lanisce.lani.game.villagers.Reactions
import si.lanisce.lani.game.villagers.Routine
import si.lanisce.lani.game.villagers.Stir
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
    /** Sweeping in front of the door, the broom going to and fro. */
    SWEEP,
    /** At the washing line by the house: reaching up to peg the washing, down to the basket for the next. */
    HANG,
    /** Beating a rug hung over the line, the dust flying. */
    BEAT,
    /** Throwing grain to the hens pecking about their feet. */
    FEED,
    /** Crouched at the forest's edge picking berries or mushrooms, the basket beside them. */
    GATHER,
    /** Sitting and mending something in the lap, the hand going. */
    MEND,
    /** A child running about at play (tag, to the hiding place and back). */
    RUN,
    /** A child crouched in a hiding place, keeping still. */
    HIDE,
    /** The seeker counting, hands over the eyes. */
    COUNT,
    /** Facing wolves or a bear at a distance, a tool raised now and then (the smith's hammer, a stick), a torch at night. */
    GUARD,
    /** Arms up, hopping: the cheer after a won fight. */
    CHEER,
    /** Hurrying home in a storm, a hand on the head holding the hat. */
    HAT,
    /** Patching up a damaged building: the hammer at its wall, planks leaning beside. */
    REPAIR,
    /** Playing the harmonika at a feast, the notes going up. */
    MUSIC,
}

/** What someone carries ([Activity.CARRY]). */
enum class Load { NONE, HAY, WOOD, WATER, BASKET }

/**
 * Someone drawn on the map: where ([x], [y] in world cells, [z] px up: on the log), what they're doing, which
 * way they face ([flip]: left), at which place, and their [slot] among the people sharing that place. [lantern]: they
 * carry a lit lantern (out at night: a ritual, the walk home from the fire), drawn with a warm glow; [load]: what they
 * carry. [ball]: the children's ball, when this one's game has one: where it is (world cells) and how high (px).
 */
data class Placed(
    val villager: Villager, val x: Float, val y: Float, val activity: Activity, val flip: Boolean, val place: TownPlace,
    val slot: Int = 0, val z: Float = 0f, val lantern: Boolean = false, val load: Load = Load.NONE, val ball: FloatArray? = null,
) {
    val id: String get() = villager.id
    val walking: Boolean get() = activity == Activity.WALK || activity == Activity.CARRY || activity == Activity.HAT
    /** Sitting, crouching or kneeling: lower than standing (their bubble sits lower). */
    val seated: Boolean get() = activity == Activity.SIT || activity == Activity.WARM || activity == Activity.TEND || activity == Activity.WASH ||
        activity == Activity.MEND || activity == Activity.GATHER || activity == Activity.HIDE

    // the ball by where it is, not by its array
    override fun equals(other: Any?): Boolean = other is Placed && villager == other.villager && x == other.x && y == other.y &&
        activity == other.activity && flip == other.flip && place == other.place && slot == other.slot && z == other.z &&
        lantern == other.lantern && load == other.load && (ball?.toList() == other.ball?.toList())

    override fun hashCode(): Int = ((villager.id.hashCode() * 31 + x.hashCode()) * 31 + y.hashCode()) * 31 + activity.hashCode()
}

/**
 * Where the people who live in the village are right now, for the renderer and the bubbles alike: pure, so both
 * agree. Each follows their day ([Routine], companion/VILLAGERS.md "A day in the village"): at their work (hoeing the
 * field, at the anvil, at the hives, washing at the stream, chopping at the woodpile, fishing at the pond), away on an
 * errand of it now and then (the hay to the kozolec, water from the well, wood from the woodpile: [errand]), resting (under
 * the linden, on the fallen log in the meadow, on the bench by the house), at the chores of a household, each at its nook
 * about the house ([Nook]: the garden, the washing line, the broom at the door, the hens in the yard), picking at the
 * forest's edge, meeting (at the well, the market), the children at their games ([games]), in the house and not drawn
 * ([Doing.INSIDE]: lunch, the housework), and walking from one to the next: straight to the next place, out along the
 * road across the clearing, into the woods by the path, never through the fire, a building or a field's crops
 * ([Walkways]). Nobody of the village wanders about but the children at play: whoever
 * stands somewhere looks about now and then. In the dark evening some sit by the fire until their bedtime, the others are
 * home (not listed: their windows are lit, [darkWindows]); then they walk home and are in bed. At night only a ritual brings
 * someone out, with a lantern. At a festival whoever is up gathers round the fire; what the village does in its events
 * otherwise (wolves about, a storm, the merchant) and after them is [TownReactions]' (companion/GAME.md "People react"). Round the
 * fire they stand or sit beside it, facing it ([aroundFire]), never in its flames; at the other places beside each other,
 * never in a building. The strangers of the day's surprise (the pedlar, the pilgrim) keep to the road, day and night, a
 * step aside now and then. Babies are carried, so they aren't listed on their own.
 *
 * Someone with a happening on now ([TownMarkers.standing]) is there instead, day or night, waving now and then: by the
 * stream, at the kitchen's house, in front of the vineyard's terraces; so is who waits for the learner by daylight (a
 * request, someone to meet: [Routine.Day.calls]). At a place with work to do (the field, the anvil, the hives) they do it
 * between the waves: France harvests the wheat in his field; a request's giver at a household's building does a chore
 * of the house ([houseWork]). Someone whose happening is far from the clearing ([away]: in
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

    /**
     * Where at a household's building (a hut, a house, the tent in the village) someone is, by what they do there (world
     * cells from the plot's middle): at the door (a broom, a word, waiting, going in), on the bench by the house (sitting,
     * mending), in the garden in front of its other side (the hoe), at the washing line at that side's far end (the washing,
     * a rug), in the yard before its front corner (the hens). So a few at one house are each at their own work about it.
     */
    internal enum class Nook(val dx: Float, val dy: Float) {
        DOOR(2.0f, 0.6f), BENCH(2.2f, -1.4f), GARDEN(0.0f, 2.5f), LINE(-2.0f, 2.3f), YARD(2.4f, 2.3f),
    }

    /** The households' buildings, which have their [Nook]s. */
    private val HOUSEHOLDS = setOf(BuildingType.HUT, BuildingType.HOUSE, BuildingType.TENT)

    /** The [Nook] of someone doing [doing] at [place] in [state]'s village, when it is a household's building on a plot; else null. */
    internal fun nookOf(state: GameState, place: TownPlace, doing: Doing): Nook? {
        if (place !is TownPlace.At || place.type !in HOUSEHOLDS || (place.type == BuildingType.TENT && TentMove.moved(state))) return null
        return when (doing) {
            Doing.HOE -> Nook.GARDEN
            Doing.HANG, Doing.BEAT -> Nook.LINE
            Doing.FEED -> Nook.YARD
            Doing.SIT, Doing.MEND, Doing.WARM -> Nook.BENCH
            else -> Nook.DOOR
        }
    }

    /** A spot at a place: the place, and at a household's building which of its [Nook]s. */
    private data class Pk(val place: TownPlace, val nook: Nook? = null)

    /** [id]'s stay at [at] from [start] to [end] (minutes of the day), the [slot]th there. */
    private class Stay(val id: String, val order: Int, val at: Pk, val start: Double, val end: Double) { var slot = 0 }

    /**
     * Where each of the village stands at each place through a day: first come first served, each keeping their spot until
     * they leave (so nobody moves over when someone comes or goes), from their stretches ([Routine.stints], by daylight;
     * not while they're in the house) and their evening by the fire ([Routine.evening]). [most]: how many spots each needs at most.
     */
    private class Stays(val byId: Map<String, List<Stay>>, val most: Map<Pk, Int>) {
        fun slot(id: String, at: Pk, minute: Double): Int? =
            byId[id]?.firstOrNull { it.at == at && minute >= it.start && minute < it.end }?.slot
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
            fun add(at: Pk, start: Double, end: Double) {
                if (end <= start) return
                val last = runs.lastOrNull()
                if (last != null && last.at == at && last.end >= start - 1e-6) runs[runs.lastIndex] = Stay(v.id, n, at, last.start, end)
                else runs += Stay(v.id, n, at, start, end)
            }
            // by daylight their stretches (the same spot one after another: one stay), then the evening by the fire
            for (s in Routine.stints(v, plan, state, date, home, day)) if (s.doing != Doing.INSIDE)
                add(Pk(s.place, nookOf(state, s.place, s.doing)), maxOf(s.start, light[0]).toDouble(), minOf(s.end, maxOf(light[1], plan.up)).toDouble())
            Routine.evening(v, state, date, month, day.copy(festival = festival))?.let { add(Pk(TownPlace.Fire), it[0].toDouble(), it[1].toDouble()) }
            all += runs
        }
        val most = HashMap<Pk, Int>()
        for ((at, here) in all.groupBy { it.at }) {
            val active = ArrayList<Stay>()
            for (s in here.sortedWith(compareBy<Stay>({ it.start }, { it.order }))) {
                active.removeAll { it.end <= s.start }
                var k = 0
                while (active.any { it.slot == k }) k++
                s.slot = k
                active += s
                most[at] = maxOf(most[at] ?: 0, k + 1)
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
    internal val STRANGERS = setOf("pedlar", "pilgrim")

    /** The spots in the woods below the village: a walk there goes by the forest path. */
    internal val WOODS = setOf("meadow", "pond", "highseat", "path", "kopa")

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
     * @param day where each sleeps, the storytellers, who waits for the learner, whose days are shared out ([Routine.Day])
     */
    fun of(
        state: GameState, people: List<Villager>, time: Double, hour: Float, month: Int, w: Int, h: Int,
        storm: Boolean = false, festival: Boolean = false, standing: Map<String, TownPlace> = emptyMap(),
        date: LocalDate = DEFAULT_DATE, day: Routine.Day = Routine.Day(), wall: Long = 0L,
    ): List<Placed> {
        if (people.isEmpty() || storm) return emptyList()
        // what the village reacts to now (its event, or one just over: companion/GAME.md "People react"): the people's day,
        // then where the reaction takes them, on their way there at first
        val stir = Reactions.stir(state, wall)
        val fest = festival || stir?.kind == EventKind.FESTIVAL && stir.phase == Stir.Phase.ON
        val routine = byDay(state, people, time, hour, month, w, h, fest, standing, date, day)
        if (stir == null && state.ended == null) return routine
        return TownReactions.react(state, people, routine, stir, time, hour, month, w, h, standing, date, day, wall) { f ->
            if (f == fest) routine else byDay(state, people, time, hour, month, w, h, f, standing, date, day)
        }
    }

    /** Where everyone is by their day ([of] without what the village reacts to): [festival], round the fire. */
    private fun byDay(
        state: GameState, people: List<Villager>, time: Double, hour: Float, month: Int, w: Int, h: Int,
        festival: Boolean, standing: Map<String, TownPlace>, date: LocalDate, day: Routine.Day,
    ): List<Placed> {
        val comp = VillageLayout.composition(w, h, compact = false)
        val dark = Env(hour, month, state.event?.kind == EventKind.STORM).dark > 0.55f
        val fg = Foreground.of(state, w, h)
        val land = VillageLayout.of(state)
        val gate = TownAnchors.visitorSpot(state, TownPlace.Forest, comp, w, h)
        val routine = day.copy(busy = day.busy + standing, festival = day.festival || festival)
        val minute = hour * 60.0
        val out = ArrayList<Placed>(people.size)
        fun woods(p: TownPlace) = p == TownPlace.Forest || (p is TownPlace.Spot && p.id in WOODS && fg != null) ||
            (p is TownPlace.At && p.type == BuildingType.TENT && TentMove.moved(state))
        val scale = TownAnchors.FIRE_SCALE[state.age.ordinal]
        // where nobody stands: in a building, the fire; and but for a field's own workers, in its crops
        val blocked = blocked(state, land, scale, w, h, fields = false)
        val clear = blocked(state, land, scale, w, h, fields = true)
        val ways = Walkways.of(state, w, h)
        // ... and, where the palisade stands, on the other side of its stakes from the place's ground [g]
        val walk = ways.walk
        fun clearAt(place: TownPlace, g: FloatArray): (Float, Float) -> Boolean {
            val base = if (place is TownPlace.At && place.type == BuildingType.FIELD) blocked else clear
            return if (walk == null) base else { x, y -> base(x, y) || walk.crosses(g[0], g[1], x, y) }
        }
        // where someone stands at a place doing [doing] (at a household's: its nook), clear of the fire (a building's corner
        // that would be behind its flames: beside them)
        fun ground(v: Villager, place: TownPlace, seed: Int, doing: Doing = Doing.STAND): FloatArray? =
            groundOf(v, place, state, comp, w, h, seed, land, nookOf(state, place, doing), walk)?.let { offFire(it[0], it[1], scale) }
        fun way(a: FloatArray, b: FloatArray, aWoods: Boolean, bWoods: Boolean, seed: Int) = route(a, b, aWoods, bWoods, gate, land, seed, ways)
        val nows = HashMap<String, Now>()
        fun nowOf(v: Villager) = nows.getOrPut(v.id) { Routine.now(v, state, date, minute, month, routine) }
        // what someone waiting for the learner does at their place between the waves: the field's, the anvil's, the hives';
        // at a household's building a chore of the house (a request's giver at home), by daylight
        fun workOf(v: Villager, place: TownPlace, busy: Boolean): Doing? = if (dark) null else workAt(place) ?: if (busy) null else houseWork(state, v, place, month)

        // Who stands where at each place. Whoever waits there (a happening, a request, the strangers at the road) first, in
        // the village's order: round the fire whoever a happening brings there first, beside it on the right. Then the
        // others by their day, first come first served, each keeping their spot until they leave ([stays]): nobody moves
        // over when someone comes or goes, and away on an errand their spot waits for them. Anyone else after them all.
        val stays = stays(state, people, date, month, day, routine.festival)
        val waits = LinkedHashMap<Pk, MutableList<String>>()
        for (v in people) {
            if (v.art == "baby") continue
            val busy = standing[v.id]
            val place = when {
                busy != null -> busy
                v.art in STRANGERS -> TownMarkers.homeOf(v, state)
                else -> routine.calls[v.id]?.takeIf { it == nowOf(v).place && nowOf(v).doing == Doing.WAVE }
            } ?: continue
            val work = if (v.art in STRANGERS) null else workOf(v, place, busy != null)
            waits.getOrPut(Pk(place, nookOf(state, place, work ?: Doing.WAVE))) { ArrayList() } += v.id
        }
        // the happenings' people round the fire before those called there
        waits[Pk(TownPlace.Fire)]?.sortBy { if (standing[it] == TownPlace.Fire) 0 else 1 }
        val extra = HashMap<Pk, Int>() // spot → how many have come there besides
        fun slotOf(v: Villager, at: Pk, m0: Double): Int {
            val k = waits[at]?.size ?: 0
            waits[at]?.indexOf(v.id)?.takeIf { it >= 0 }?.let { return it }
            val m = if (m0 < Routine.plan(v, v.id in day.tellers).up) -1.0 else m0
            stays.slot(v.id, at, m)?.let { return k + it }
            val n = extra[at] ?: 0
            extra[at] = n + 1
            return k + (stays.most[at] ?: 0) + n
        }
        val players = ArrayList<Player>()

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
                val slot = slotOf(v, Pk(place, nookOf(state, place, Doing.WAVE)), minute)
                if (busy == TownPlace.Fire) {
                    val g = aroundFire(slot, scale)
                    out += Placed(v, g[0], g[1], Activity.WARM, flip = g[0] - g[1] > 0f, place = busy, slot = slot)
                    continue
                }
                val g = (if (busy != null) TownAnchors.visitorSpot(state, busy, comp, w, h) else ground(v, place, seed)) ?: continue
                out += idle(v, g, place, slot, time, seed, if (busy != null) Activity.WARM else Activity.STAND, walk)
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
            // waiting for the learner where there is work (the field, the anvil, the hives, a household's chores): at it, a wave between
            val waiting = busy != null || routine.calls[v.id] == to
            val work = if (waiting) workOf(v, to, busy != null) else null
            val doing = work ?: now.doing
            val here = Pk(to, nookOf(state, to, doing))
            val at = when {
                to == TownPlace.Fire -> floatArrayOf(0f, 0f)
                waiting && work == null -> TownAnchors.visitorSpot(state, to, comp, w, h)?.let { offFire(it[0], it[1], scale) }
                else -> ground(v, to, seed, doing)
            } ?: continue
            // their own spot there (kept for them while they're away on an errand, taken on the way there): at the fire their
            // place round it, else beside whoever came before them
            val slot = slotOf(v, here, minute)
            val spot = if (to == TownPlace.Fire) aroundFire(slot, scale) else spotAt(at, slot, clearAt(to, at))
            // away on an errand of their work: there and back, the load one way; beside whoever is where they load and unload
            now.errand?.let { trip ->
                val src0 = ground(v, trip.from, seed); val dst0 = ground(v, trip.to, seed)
                if (src0 != null && dst0 != null) {
                    val src = if (trip.from == to) spot else spotAt(src0, slotOf(v, Pk(trip.from, nookOf(state, trip.from, Doing.STAND)), minute), clearAt(trip.from, src0))
                    val dst = if (trip.to == to) spot else spotAt(dst0, slotOf(v, Pk(trip.to, nookOf(state, trip.to, Doing.STAND)), minute), clearAt(trip.to, dst0))
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
                val did = Routine.now(v, state, date, left, month, routine).doing
                val was = slotOf(v, Pk(from, nookOf(state, from, did)), left)
                val start = if (from == TownPlace.Fire) aroundFire(was, scale)
                else seat(from, did, was, fg, comp) ?: ground(v, from, seed, did)?.let { spotAt(it, was, clearAt(from, it)) }
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
            // something else at the same place, somewhere else there (from the sheep to the log, from the bench to the
            // garden): a few steps over
            now.before?.let { was ->
                val wasAt = Pk(to, nookOf(state, to, was))
                val a = seat(to, was, slot, fg, comp) ?: if (wasAt == here) spot else ground(v, to, seed, was)?.let { spotAt(it, slotOf(v, wasAt, minute - now.begun / 60.0 - 1e-3), clearAt(to, it)) } ?: spot
                if (hypot(dest[0] - a[0], dest[1] - a[1]) > 0.05f) {
                    val way = way(a, dest, woods(to), woods(to), seed)
                    if (now.begun * SPEED < Walkways.length(way)) {
                        val (p, dir) = along(way, now.begun.toFloat() * SPEED)
                        out += Placed(v, p[0], p[1], Activity.WALK, flip = dir < 0f, place = to, slot = slot)
                        continue
                    }
                }
            }
            // in the house (lunch at the table, the housework): through the door, and not on the map
            if (doing == Doing.INSIDE) continue
            // there: at the fire in their place round it, else at their spot at the place, the others there beside them
            val placed = placedAt(v, spot, to, if (work != null) now.copy(doing = work) else now, slot, time, seed, fg, comp, dark, to == TownPlace.Fire, wave = work != null, hens = here.nook == Nook.YARD)
            // a child at play: their game with the others there (see [games]), easing into it as they come and out as they go
            if (placed.activity == Activity.PLAY && !waiting) {
                val into = if (now.before == null) now.since else now.begun
                players += Player(out.size, v, to, at, spot, smooth((minOf(into, now.left) / EASE).toFloat()), clearAt(to, at))
            }
            out += placed
        }
        games(state, date, time, out, players)
        return out
    }

    // ------------------------------------------------------------------ the children's games

    /** A child at play: [out]'s [index], at [place] round [g] (its ground), their own [spot] there, how far into the game ([w], 0..1). */
    private class Player(val index: Int, val v: Villager, val place: TownPlace, val g: FloatArray, val spot: FloatArray, val w: Float, val blocked: (Float, Float) -> Boolean)

    /** The children's games at a place, one a day there by the day's dice ([gameOf]). */
    enum class Game {
        /** Tag: running round and round after each other, a breather now and then. */
        TAG,
        /** The ball thrown from one to the next round a ring, caught and thrown on. */
        BALL,
        /** Hide-and-seek: one counts, the others run off and crouch in a hiding place, found one by one, back to the base. */
        HIDE,
    }

    /** Which game the children play at [place] on [date] (the day's dice): one a day, whoever joins it. */
    fun gameOf(state: GameState, date: LocalDate, place: TownPlace): Game =
        Game.entries[(si.lanisce.lani.game.scene.Happenings.roll(state.seed, date, "game:$place") * Game.entries.size).toInt().coerceIn(0, Game.entries.size - 1)]

    /** How long a child takes to ease into a game as they come, or out of it before they go (s): never a hop. */
    private const val EASE = 6.0

    /** Tag's ring (cells), and how fast they run round it (cells a second); a breather of [TAG_REST] s every [TAG_LAP]. */
    private const val TAG_R = 0.8f
    private const val TAG_SPEED = 0.9f
    private const val TAG_LAP = 14.0
    private const val TAG_REST = 3.0

    /** The ball's ring (cells), a throw's flight and the catch's hold (s). */
    private const val BALL_R = 1.0f
    private const val BALL_FLIGHT = 1.3
    private const val BALL_HOLD = 0.7

    /** Hide-and-seek: how far the hiding places are (cells), the round (s), the counting until, the seeker's pace. */
    private const val HIDE_R = 1.3f
    private const val HIDE_ROUND = 30.0
    private const val HIDE_COUNT = 9.0
    private const val HIDE_RUN = 2.5
    private const val SEEK_SPEED = 1.0f

    /** How far a game's middle may be off the place's ground (cells), to have room. */
    private const val GAME_SHIFT = 0.6f

    /** A child's own angle round a game (their hiding place's way, their place in the ring, where in the chase). */
    private fun angle(v: Villager): Double = Noise.rnd(v.id.hashCode(), 41).toDouble() * 2 * PI

    /** How much of the way from 0 to 1 at [u] (0..1), easing in and out. */
    private fun smooth(u: Float): Float { val x = u.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }

    /**
     * The children at play together at a place ([players], at [out]'s indices) in its game of the day ([gameOf]): tag round
     * a small ring, the ball thrown round a ring of them, or hide-and-seek. A child alone hops about (and with the ball
     * throws it up and catches it). Each eases into the game as they come and out of it before they go, from their own spot
     * there, so nobody jumps; whoever comes or goes doesn't move the others: each has their own place in it by their id.
     * All of it within a step or two of the place's ground, clear of the buildings and the fire.
     */
    private fun games(state: GameState, date: LocalDate, time: Double, out: MutableList<Placed>, players: List<Player>) {
        for (group in players.groupBy { p -> p.place to (p.g[0] * 10).toInt() * 100003 + (p.g[1] * 10).toInt() }.values) {
            val blocked = group.first().blocked
            val game = gameOf(state, date, group.first().place)
            val ring = group.sortedBy { angle(it.v) }
            // the game's middle: the place's ground, or a step off it where its ring has room (not in a building or the fire)
            val room = when (game) { Game.TAG -> TAG_R; Game.BALL -> BALL_R; Game.HIDE -> HIDE_R }
            val g0 = group.first().g
            fun roomy(x: Float, y: Float, r: Float) = !blocked(x, y) && (0 until 12).all { !blocked(x + r * cos(it * PI / 6).toFloat(), y + r * sin(it * PI / 6).toFloat()) }
            val g = (listOf(floatArrayOf(0f, 0f)) + (0 until 8).map { floatArrayOf(GAME_SHIFT * cos(it * PI / 4).toFloat(), GAME_SHIFT * sin(it * PI / 4).toFloat()) })
                .map { floatArrayOf(g0[0] + it[0], g0[1] + it[1]) }.firstOrNull { roomy(it[0], it[1], room * 0.6f) } ?: continue
            fun free(r: Float, a: Double) = !blocked(g[0] + r * cos(a).toFloat(), g[1] + r * sin(a).toFloat())
            fun put(p: Player, x: Float, y: Float, activity: Activity, flip: Boolean, ball: FloatArray? = null) {
                val was = out[p.index]
                val gx = p.spot[0] + (x - p.spot[0]) * p.w; val gy = p.spot[1] + (y - p.spot[1]) * p.w
                out[p.index] = was.copy(x = gx, y = gy, activity = if (p.w < 1f && activity != Activity.PLAY && activity != Activity.COUNT) Activity.RUN else activity, flip = flip, ball = ball)
            }
            fun facing(x: Float, y: Float, tx: Float, ty: Float) = ((tx - x) - (ty - y)) < 0f
            when {
                game == Game.BALL -> {
                    val r = if (roomy(g[0], g[1], BALL_R)) BALL_R else BALL_R * 0.6f
                    val at = ring.map { p -> val a = angle(p.v); floatArrayOf(g[0] + r * cos(a).toFloat(), g[1] + r * sin(a).toFloat()) }
                    for ((i, p) in ring.withIndex()) put(p, at[i][0], at[i][1], Activity.PLAY, facing(at[i][0], at[i][1], g[0], g[1]))
                    // the ball: from one to the next round the ring, or up and caught again by a child alone
                    val n = ring.size
                    val cycle = BALL_FLIGHT + BALL_HOLD
                    val k = kotlin.math.floor(time / cycle).toLong()
                    val u = ((time % cycle) / BALL_FLIGHT).toFloat()
                    val from = Math.floorMod(k, n.toLong()).toInt(); val to = (from + 1) % n
                    val a = out[ring[from].index]; val b = out[ring[to].index]
                    val ball = when {
                        n == 1 -> floatArrayOf(a.x + 0.25f, a.y + 0.25f, 6f + 12f * abs(sin(PI * time / (cycle * 0.8))).toFloat())
                        u < 1f -> floatArrayOf(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u, 6f + 10f * sin(PI * u).toFloat())
                        else -> floatArrayOf(b.x, b.y, 6f)
                    }
                    out[ring[0].index] = out[ring[0].index].copy(ball = ball)
                }
                ring.size == 1 -> Unit // alone: hopping about at their spot
                game == Game.TAG -> {
                    val r = if (roomy(g[0], g[1], TAG_R)) TAG_R else TAG_R * 0.6f
                    val laps = Math.floorDiv(time.toLong(), TAG_LAP.toLong())
                    val run = laps * (TAG_LAP - TAG_REST) + minOf(time - laps * TAG_LAP, TAG_LAP - TAG_REST)
                    val resting = time - laps * TAG_LAP >= TAG_LAP - TAG_REST
                    for (p in ring) {
                        val a = angle(p.v) + run * TAG_SPEED / r
                        val x = g[0] + r * cos(a).toFloat(); val y = g[1] + r * sin(a).toFloat()
                        // running round: facing the way they go (screen x of the step), at rest facing the middle
                        val flip = if (resting) facing(x, y, g[0], g[1]) else (-sin(a) - cos(a)) < 0.0
                        put(p, x, y, if (resting) Activity.PLAY else Activity.RUN, flip)
                    }
                }
                else -> {
                    // hide-and-seek: each round one of them counts at the ground, the others run to their hiding places;
                    // then the seeker goes round them one by one, and each found runs back to the base
                    val round = Math.floorDiv(time.toLong(), HIDE_ROUND.toLong())
                    val tau = time - round * HIDE_ROUND
                    val seeker = ring[Math.floorMod(round, ring.size.toLong()).toInt()]
                    val hiders = ring.filter { it !== seeker }
                    val spots = hiders.associateWith { p ->
                        val a0 = angle(p.v)
                        val a = (0 until 8).map { a0 + it * PI / 4 }.firstOrNull { free(HIDE_R, it) && free(HIDE_R / 2, it) }
                        if (a != null) floatArrayOf(g[0] + HIDE_R * cos(a).toFloat(), g[1] + HIDE_R * sin(a).toFloat())
                        else floatArrayOf(g[0] + 0.6f * HIDE_R * cos(a0).toFloat(), g[1] + 0.6f * HIDE_R * sin(a0).toFloat())
                    }
                    // the seeker's round: the ground, each hiding place, back
                    val stops = listOf(g) + hiders.map { spots.getValue(it) } + listOf(g)
                    val found = DoubleArray(hiders.size)
                    var tt = HIDE_COUNT
                    for (i in 1 until stops.size) {
                        tt += hypot(stops[i][0] - stops[i - 1][0], stops[i][1] - stops[i - 1][1]) / SEEK_SPEED
                        if (i - 1 < hiders.size) found[i - 1] = tt
                    }
                    // the seeker: counting, then going round
                    if (tau < HIDE_COUNT) put(seeker, g[0], g[1], Activity.COUNT, false)
                    else {
                        var left = (tau - HIDE_COUNT) * SEEK_SPEED
                        var pos = g; var dir = 0f
                        for (i in 1 until stops.size) {
                            val a = stops[i - 1]; val b = stops[i]
                            val seg = hypot(b[0] - a[0], b[1] - a[1])
                            if (left <= seg) { val f = if (seg > 0f) (left / seg).toFloat() else 1f; pos = floatArrayOf(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f); dir = (b[0] - a[0]) - (b[1] - a[1]); left = -1.0; break }
                            left -= seg; pos = b
                        }
                        put(seeker, pos[0], pos[1], if (left < 0) Activity.RUN else Activity.PLAY, dir < 0f)
                    }
                    // the others: off to their hiding places, crouched there, found, back to the base
                    for ((i, p) in hiders.withIndex()) {
                        val hs = spots.getValue(p)
                        val home = floatArrayOf(g[0] + 0.5f * cos(angle(p.v)).toFloat(), g[1] + 0.5f * sin(angle(p.v)).toFloat())
                        val back = hypot(home[0] - hs[0], home[1] - hs[1]) / TAG_SPEED
                        when {
                            tau < HIDE_RUN -> { val f = (tau / HIDE_RUN).toFloat(); put(p, home[0] + (hs[0] - home[0]) * f, home[1] + (hs[1] - home[1]) * f, Activity.RUN, facing(home[0], home[1], hs[0], hs[1])) }
                            tau < found[i] -> put(p, hs[0], hs[1], Activity.HIDE, facing(hs[0], hs[1], g[0], g[1]))
                            tau < found[i] + 1.5 -> put(p, hs[0], hs[1], Activity.PLAY, facing(hs[0], hs[1], g[0], g[1]))
                            tau < found[i] + 1.5 + back -> {
                                val f = ((tau - found[i] - 1.5) / back).toFloat()
                                put(p, hs[0] + (home[0] - hs[0]) * f, hs[1] + (home[1] - hs[1]) * f, Activity.RUN, facing(hs[0], hs[1], home[0], home[1]))
                            }
                            else -> put(p, home[0], home[1], Activity.PLAY, facing(home[0], home[1], g[0], g[1]))
                        }
                    }
                }
            }
        }
    }

    /** How fast someone walks a way of [len] cells: an unhurried stride, brisker on a long way so it takes no longer than [Routine.WALK_S]. */
    internal fun pace(len: Float): Float = maxOf(SPEED, len / Routine.WALK_S.toFloat())

    /** The work someone waiting for the learner at [place] does between the waves: the field's, the anvil's, the hives'; else none. */
    private fun workAt(place: TownPlace): Doing? = when ((place as? TownPlace.At)?.type) {
        BuildingType.FIELD -> Doing.HOE
        BuildingType.SMITHY -> Doing.HAMMER
        BuildingType.BEEHIVE -> Doing.TEND
        else -> null
    }

    /**
     * The chore of the house someone waiting for the learner at a household's building does between the waves (a request's
     * giver, someone to meet at home), each at a nook of their own: the grandmother in her garden, the aunt at the washing
     * line, the innkeeper with her broom, a child with the hens; the others by their id. Winter has no garden or washing.
     * Null anywhere else.
     */
    private fun houseWork(state: GameState, v: Villager, place: TownPlace, month: Int): Doing? {
        if (nookOf(state, place, Doing.STAND) == null) return null
        val cold = month == 12 || month in 1..2
        return when (Routine.kindOf(v)) {
            Routine.Kind.GRANDMA -> if (cold) Doing.SWEEP else Doing.HOE
            Routine.Kind.AUNT -> if (cold) Doing.BEAT else Doing.HANG
            Routine.Kind.INNKEEPER -> Doing.SWEEP
            Routine.Kind.CHILD -> Doing.FEED
            else -> listOf(Doing.SWEEP, Doing.FEED, Doing.BEAT)[Math.floorMod(v.id.hashCode(), 3)]
        }
    }

    /** The fields' middles (world cells) in [land]: their crops, the soil with its rows, half a plot and a bit across. */
    internal fun fields(state: GameState, land: Terrain): List<FloatArray> =
        state.buildings.filter { it.type == BuildingType.FIELD && it.plot in 0 until VillageLayout.MAX_PLOTS }.map { land.centers[it.plot] }

    /** How far from a field's middle its crops reach (cells): its soil, which nobody but its workers stands or walks on. */
    internal const val CROPS = 1.3f

    /**
     * Where nobody stands, world cells: in a building, in the fire of [scale] ([inFire]), in the campsite's fire or its tent
     * at the pond; with [fields] in a field's crops too (anybody but its workers: a field is walked in by them).
     */
    internal fun blocked(state: GameState, land: Terrain, scale: Float, w: Int, h: Int, fields: Boolean): (Float, Float) -> Boolean {
        val solid = state.buildings.filter { it.type.onPlot && it.type != BuildingType.FIELD && it.plot in 0 until VillageLayout.MAX_PLOTS }.map { land.centers[it.plot] }
        val crops = if (fields) fields(state, land) else emptyList()
        val camp = Campsite.of(state, w, h)
        return { x, y ->
            solid.any { abs(x - it[0]) < 1.7f && abs(y - it[1]) < 1.7f } || crops.any { abs(x - it[0]) < CROPS && abs(y - it[1]) < CROPS } || inFire(x, y, scale) || camp != null && (
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
        fg: Foreground?, comp: Composition, dark: Boolean, fire: Boolean = false, wave: Boolean = false, hens: Boolean = false,
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
            Doing.SWEEP -> Activity.SWEEP
            Doing.HANG -> Activity.HANG
            Doing.BEAT -> Activity.BEAT
            // the hens in the yard by the house; the sheep in the meadow: bent over them
            Doing.FEED -> if (hens) Activity.FEED else Activity.TEND
            Doing.GATHER -> Activity.GATHER
            Doing.MEND -> Activity.MEND
            Doing.INSIDE -> Activity.STAND
        }
        val log = if (activity == Activity.SIT || activity == Activity.MEND) seat(place, Doing.SIT, slot, fg, comp) else null
        when {
            fire -> Unit // facing the fire
            // on top of the fallen log in the meadow
            log != null -> { x = log[0]; y = log[1]; z = 2.6f; flip = false }
            activity == Activity.FISH || activity == Activity.WASH -> flip = false // the water lies up and to the left of its bank
            activity == Activity.CHOP -> flip = (seed ushr 6) % 2 == 0
            activity == Activity.HOE || activity == Activity.TEND || activity == Activity.GATHER || activity == Activity.FEED -> flip = (seed ushr 7) % 2 == 0
            // the washing line at the house's far side, the line and the rug off to their left away from it; the broom swept
            // out in front of the door, to their right
            activity == Activity.HANG || activity == Activity.BEAT -> flip = true
            activity == Activity.SWEEP -> flip = false
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
        if ((doing != Doing.SIT && doing != Doing.WARM && doing != Doing.MEND) || place != TownPlace.Spot("meadow") || fg == null || slot != 0) return null
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
        from == TownPlace.Spot("riverbank") -> Load.WATER
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
    private fun idle(v: Villager, g: FloatArray, place: TownPlace, slot: Int, time: Double, seed: Int, activity: Activity, walk: PalisadeWalk? = null): Placed {
        // beside whoever stands there, on this side of the palisade's stakes (with the step out)
        fun ours(r: FloatArray) = walk == null || !walk.crosses(g[0], g[1], g[0] + r[0], g[1] + r[1]) && !walk.crosses(g[0], g[1], g[0] + r[0] + 0.5f, g[1] + r[1])
        val ring = RING[slot % RING.size].takeIf(::ours) ?: (1..RING.size).map { RING[(slot + it) % RING.size] }.firstOrNull(::ours) ?: RING[0]
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
     * The way from [a] to [b] (world cells): into or out of the woods by the path ([gate]: where it comes into the meadow;
     * where the palisade stands, through its gate and along the path, as far down it as it goes nearest the woods' end);
     * from one side of the road to the other, out to the road (on their lane), along it and in again; else straight to it.
     * Each stretch round the fire and the buildings in the way ([Walkways]): past the fire on its nearer side round the
     * plaza, never through it; and never through the palisade's stakes, but by its gates ([Walkways.Ways.walk]).
     */
    internal fun route(a: FloatArray, b: FloatArray, aWoods: Boolean, bWoods: Boolean, gate: FloatArray?, land: Terrain, seed: Int, ways: Walkways.Ways): List<FloatArray> {
        val pts = ArrayList<FloatArray>(4)
        pts += a
        val forest = ways.forest
        if (aWoods != bWoods && gate != null && forest != null) {
            // down the path from its gate to where it goes nearest the end in the woods (or up it from there)
            val end = if (bWoods) b else a
            var k = 0
            for (i in forest.indices) if (hypot(forest[i][0] - end[0], forest[i][1] - end[1]) < hypot(forest[k][0] - end[0], forest[k][1] - end[1])) k = i
            val down = forest.subList(0, k + 1)
            pts.addAll(if (bWoods) down else down.asReversed())
        } else if (aWoods != bWoods && gate != null) pts += gate
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
     * it), at a household's building the [nook] of what they do there, else at its right front corner; elsewhere the
     * place's visitor spot. A building of [v]'s home types (the old way: their dwellings
     * shared out by id) when [place] is one of their homes' types without an id.
     */
    internal fun groundOf(
        v: Villager, place: TownPlace, state: GameState, comp: Composition, w: Int, h: Int, seed: Int, land: Terrain, nook: Nook? = null,
        walk: PalisadeWalk? = null,
    ): FloatArray? {
        if (place is TownPlace.At && place.type.onPlot && !(place.type == BuildingType.TENT && TentMove.moved(state))) {
            val b = buildingOf(place, state, seed)
            if (b != null && b.plot in 0 until VillageLayout.MAX_PLOTS) {
                val c = land.centers[b.plot]
                return when {
                    place.type == BuildingType.FIELD -> floatArrayOf(c[0] + ((seed ushr 3) % 3 - 1) * 0.7f, c[1] + 0.3f)
                    nook != null -> nookAt(state, land, c, nook, walk)
                    else -> floatArrayOf(c[0] + 2.0f, c[1] + 0.6f)
                }
            }
        }
        return TownAnchors.visitorSpot(state, place, comp, w, h)
    }

    /**
     * Where [nook] of the household's building at [c] (its plot's middle, world cells) is: its own spot by the house; but
     * where the palisade stands ([walk]) and that spot would be beyond its stakes or among them (a house at the clearing's
     * rim, its yard behind the stakes), the same spot a little nearer the house, else round the house on another side, the
     * first inside the ring, a step clear of the stakes and off the other buildings.
     */
    internal fun nookAt(state: GameState, land: Terrain, c: FloatArray, nook: Nook, walk: PalisadeWalk?): FloatArray {
        val own = floatArrayOf(c[0] + nook.dx, c[1] + nook.dy)
        if (walk == null) return own
        fun ok(x: Float, y: Float): Boolean {
            if (!walk.inside(x, y) || walk.crosses(c[0], c[1], x, y)) return false
            if (NOOK_ROOM.any { walk.crosses(x, y, x + it[0], y + it[1]) }) return false
            return state.buildings.none { b -> b.type.onPlot && b.type != BuildingType.FIELD && b.plot in 0 until VillageLayout.MAX_PLOTS &&
                land.centers[b.plot].let { o -> o !== c && abs(x - o[0]) < 1.7f && abs(y - o[1]) < 1.7f } }
        }
        if (ok(own[0], own[1])) return own
        // the same side nearer the house, then a quarter round either way, then the far side
        for ((rx, ry) in listOf(nook.dx to nook.dy, -nook.dy to nook.dx, nook.dy to -nook.dx, -nook.dx to -nook.dy)) for (f in NOOK_IN) {
            val x = c[0] + rx * f; val y = c[1] + ry * f
            if (ok(x, y)) return floatArrayOf(x, y)
        }
        return own
    }

    /** How much nearer the house a nook beyond the palisade comes, in turn (of its distance). */
    private val NOOK_IN = floatArrayOf(1f, 0.85f, 0.72f)

    /** A nook keeps this clear of the palisade's stakes (cells each way). */
    private val NOOK_ROOM = arrayOf(floatArrayOf(0.6f, 0f), floatArrayOf(-0.6f, 0f), floatArrayOf(0f, 0.6f), floatArrayOf(0f, -0.6f))

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
                // in the house by day (the housework), as the evening comes
                Where.OUT -> now.doing == Doing.INSIDE
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

    internal fun worldX(sx: Float, sy: Float): Float = (sx / 4f + sy / 2f) / 2f
    internal fun worldY(sx: Float, sy: Float): Float = (sy / 2f - sx / 4f) / 2f

    /** Someone's nominal-canvas point above their head (for a bubble), given their feet at ([x], [y]) and a sprite [height] px tall. */
    fun headAnchor(p: Placed, comp: Composition, height: Float): CanvasPoint =
        CanvasPoint(comp.fireX + (p.x - p.y) * 4f, comp.fireY + (p.x + p.y) * 2f - p.z - height - 2f)

    /** Whether [a] and [b] would stand on each other (for tests and layout checks). */
    fun overlap(a: Placed, b: Placed): Boolean = abs(a.x - b.x) < 0.3f && abs(a.y - b.y) < 0.3f
}
