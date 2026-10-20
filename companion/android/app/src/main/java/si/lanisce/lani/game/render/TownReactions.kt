package si.lanisce.lani.game.render

import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.TentMove
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.villagers.Reactions
import si.lanisce.lani.game.villagers.Routine
import si.lanisce.lani.game.villagers.Stance
import si.lanisce.lani.game.villagers.Stir
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.Where
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Where the people go while the village reacts to its event (companion/GAME.md "People react"; who does what is
 * [Reactions]'): a reaction is a place and a pace for someone while it's on, not a new day. Each walks from where their
 * day has them to where the reaction wants them (round the fire, the buildings and the animals: [Walkways]), the
 * children hurrying, and is there once arrived; when it's over they walk back to their day the same way.
 *
 * - **Wolves or a bear**: the defenders stand between them and the village, facing them, a torch at night; the lookout
 *   is up on the watchtower waving; the children, the old and the innkeeper gather by the fire; the others go home.
 *   Nobody comes within [Threats.KEEP] of an animal, nor walks there.
 * - **A won fight**: everyone up gathers round the fire with the defenders and cheers ([Reactions.CHEER_S]), then goes
 *   back to their day. **A lost one**: back to their day; by daylight someone patches up the building it damaged, that day
 *   and the next ([Reactions.repair]).
 * - **A storm**: everyone goes home; now and then someone dashes from one door to another holding their hat, and someone
 *   takes the washing in.
 * - **The merchant**: by daylight the innkeeper and a few others stand at his cart, a few others every quarter of an hour.
 * - **A festival**: round the fire as their day has it at a feast, dancing, someone playing the harmonika.
 */
internal object TownReactions {
    /** How much quicker the children hurry away from wolves, and anyone in the rain. */
    private const val HURRY = 1.6f
    private const val RAIN = 1.3f

    /** A storm: someone dashes between two doors at the start of every this many minutes of the village's clock. */
    private const val DASH_MIN = 6

    /** ... and someone takes the washing in for this long (s) at the start of every half hour. */
    private const val LAUNDRY_MIN = 30
    private const val LAUNDRY_S = 100.0

    /** The merchant's cart: a few others come every quarter of an hour, besides the innkeeper. */
    private const val CART_MIN = 15
    private const val CART_FEW = 2

    /** The defenders' spots by the village's ways (a village with its animals: [Walkways.of]), worked out once. */
    private val guards = java.util.WeakHashMap<Walkways.Ways, List<FloatArray>>()

    /** How high a watchtower's platform is (px: its floor, see [BuildingPainter]), where the lookout stands. */
    const val TOWER_Z = 18f

    /** Where someone ends up: at [x], [y] doing [activity], or [inside] (through their door there: not on the map). */
    private class Goal(
        val x: Float, val y: Float, val activity: Activity, val flip: Boolean, val place: TownPlace,
        val z: Float = 0f, val lantern: Boolean = false, val inside: Boolean = false, val slot: Int = 0, val load: Load = Load.NONE,
        /** Their place by their day, as it is (the children's ball): it shows as it is once they're back. */
        val placed: Placed? = null,
    )

    private fun Placed.goal(): Goal = Goal(x, y, activity, flip, place, z, lantern, slot = slot, load = load, placed = this)

    /** [this] goal outside the door: someone setting off from it (a dash in the rain from one door to another). */
    private fun Goal.outside(): Goal = Goal(x, y, activity, flip, place, z, lantern, inside = false, slot = slot, load = load)

    /**
     * [routine] ([TownPeople.of]'s people by their day) as [stir] has them (null: nothing, but a building a lost fight
     * damaged may have someone at it), [day] being where they sleep and who waits. [byDay]: the people by their day at a
     * feast (true) or not, for the walk to the feast and back.
     */
    fun react(
        state: GameState, people: List<Villager>, routine: List<Placed>, stir: Stir?, time: Double, hour: Float, month: Int, w: Int, h: Int,
        standing: Map<String, TownPlace>, date: LocalDate, day: Routine.Day, wall: Long, byDay: (Boolean) -> List<Placed>,
    ): List<Placed> {
        val r = Ctx(state, people, routine, time, hour, month, w, h, standing, date, day, wall)
        val out = if (stir == null) routine else when (stir.phase) {
            Stir.Phase.ON -> r.on(stir, byDay)
            Stir.Phase.WON -> r.won(stir)
            Stir.Phase.LOST, Stir.Phase.OVER -> r.after(stir, byDay)
        }
        // the strangers at the road keep to it, but while wolves, a bear or a storm are about (they're in at the inn)
        val strangers = if (stir?.quiet == true || stir == null) emptyList() else routine.filter { it.villager.art in TownPeople.STRANGERS && out.none { o -> o.id == it.id } }
        return r.repair(out) + strangers
    }

    /**
     * Who of [placed] calls out now, and what ([stir]'s [Reactions.shouts]: "Volkovi!"): one at a time, for a couple of
     * seconds every few, each in turn of those it concerns (the ones by the fire and the defenders, the cheering), by the
     * animation clock [time]. None when the culture has no words for it.
     */
    fun shouts(stir: Stir?, placed: List<Placed>, time: Double, texts: List<Text>): List<Pair<String, Text>> {
        if (stir == null || texts.isEmpty() || stir.phase == Stir.Phase.OVER || stir.phase == Stir.Phase.LOST) return emptyList()
        val who = placed.filter { it.villager.art !in TownPeople.STRANGERS }.sortedBy { it.id }
        if (who.isEmpty()) return emptyList()
        val period = SHOUT_EVERY
        val k = floor(time / period).toLong()
        if ((time - k * period) > SHOUT_FOR) return emptyList()
        val p = who[Math.floorMod(k * 7 + 3, who.size.toLong()).toInt()]
        return listOf(p.id to texts[Math.floorMod(k, texts.size.toLong()).toInt()])
    }

    /** A call shows for this long (s) of every [SHOUT_EVERY]. */
    const val SHOUT_FOR = 2.6
    const val SHOUT_EVERY = 7.0

    private class Ctx(
        val state: GameState, val people: List<Villager>, val routine: List<Placed>, val time: Double, val hour: Float, val month: Int,
        val w: Int, val h: Int, val standing: Map<String, TownPlace>, val date: LocalDate, val day: Routine.Day, val wall: Long,
    ) {
        val comp = VillageLayout.composition(w, h, compact = false)
        val land = VillageLayout.of(state)
        val fg = Foreground.of(state, w, h)
        val scale = TownAnchors.FIRE_SCALE[state.age.ordinal]
        val dark = Env(hour, month, state.event?.kind == EventKind.STORM).dark > 0.55f
        val minute = hour * 60.0
        val ways = Walkways.of(state, w, h)
        val gate = TownAnchors.visitorSpot(state, TownPlace.Forest, comp, w, h)
        /** Where nobody stands: in a building, the fire, a field's crops. */
        val blocked = TownPeople.blocked(state, land, scale, w, h, fields = true)
        val byId: Map<String, Placed> = routine.associateBy { it.id }
        val folk = people.filter { it.art != "baby" && it.art !in TownPeople.STRANGERS }.distinctBy { it.id }
        val routineDay = day.copy(busy = day.busy + standing)

        fun asleep(v: Villager): Boolean = Routine.now(v, state, date, minute, month, routineDay).where == Where.ASLEEP

        /** The wolves or the bear about, while they are ([Threats]); null: none. */
        val threat: Threats.Threat? by lazy { Threats.of(state, w, h) }

        /**
         * Where someone at [p] near [t]'s animals (within the ground nobody walks on, [Threats.Threat.block]) leaves it, or
         * comes into it to their spot: out from them through [p], just outside it. Null: [p] isn't near them.
         */
        fun rim(t: Threats.Threat, p: FloatArray): FloatArray? {
            val dx = p[0] - t.cx; val dy = p[1] - t.cy
            val d = hypot(dx, dy)
            val r = t.keep / kotlin.math.cos(Math.PI / 8).toFloat() + 0.6f
            if (d >= r) return null
            val ux = if (d < 1e-3f) 0f else dx / d; val uy = if (d < 1e-3f) -1f else dy / d
            return floatArrayOf(t.cx + ux * r, t.cy + uy * r)
        }

        fun woods(p: TownPlace) = p == TownPlace.Forest || (p is TownPlace.Spot && p.id in TownPeople.WOODS && fg != null) ||
            (p is TownPlace.At && p.type == BuildingType.TENT && TentMove.moved(state))

        /** Their home's door (world cells): where they go in and come out. */
        fun door(v: Villager): Goal? {
            val home = day.homes[v.id] ?: TownMarkers.homeOf(v, state)
            val g = TownPeople.groundOf(v, home, state, comp, w, h, Noise.hash(v.id.hashCode(), 11), land, TownPeople.Nook.DOOR) ?: return null
            val p = TownPeople.offFire(g[0], g[1], scale)
            return Goal(p[0], p[1], Activity.STAND, p[0] > 0f, home, inside = true)
        }

        /** Where someone is by their day now: their place there, else (in the house, asleep) at their door, inside. */
        fun daily(v: Villager): Goal? = byId[v.id]?.goal() ?: door(v)

        /** The [slot]th place round the fire, facing it; [activity] there. */
        fun fire(slot: Int, activity: Activity): Goal {
            val p = TownPeople.aroundFire(slot, scale)
            return Goal(p[0], p[1], activity, p[0] - p[1] > 0f, TownPlace.Fire, slot = slot)
        }

        /**
         * [v] on the way from [a] to [b] (round what's in the way), [since] seconds after they set off at their pace
         * ([hurry] times it, running when it's more), carrying a light at night when [light]; once there, at [b] (or in the
         * house). Null: not on the map (in the house, both ends).
         */
        fun walk(v: Villager, a: Goal?, b: Goal?, since: Double, hurry: Float = 1f, light: Boolean = false, hat: Boolean = false): Placed? {
            if (b == null) return null
            val there = b.placed ?: if (b.inside) null else Placed(v, b.x, b.y, b.activity, b.flip, b.place, b.slot, b.z, b.lantern, b.load)
            if (a == null || since.isInfinite() || (a.inside && b.inside)) return there
            if (hypot(a.x - b.x, a.y - b.y) < 0.05f && a.z == b.z) return there
            val pa = floatArrayOf(a.x, a.y); val pb = floatArrayOf(b.x, b.y)
            val seed = Noise.hash(v.id.hashCode(), 11)
            val t = threat
            val way = if (t == null) TownPeople.route(pa, pb, woods(a.place), woods(b.place), gate, land, seed, ways) else {
                // wolves or a bear about: round them, not by the forest path's gate (they're on it); someone near them first
                // away from them, a defender last in to their spot facing them
                val ra = rim(t, pa); val rb = rim(t, pb)
                listOfNotNull(pa.takeIf { ra != null }) + TownPeople.route(ra ?: pa, rb ?: pb, false, false, null, land, seed, ways) + listOfNotNull(pb.takeIf { rb != null })
            }
            val len = Walkways.length(way)
            val pace = TownPeople.pace(len) * hurry
            val d = (since * pace).toFloat()
            if (d >= len) return there
            val (p, dir) = TownPeople.along(way, d)
            val act = when {
                hat -> Activity.HAT
                hurry > 1.2f && Routine.kindOf(v) == Routine.Kind.CHILD -> Activity.RUN
                else -> Activity.WALK
            }
            return Placed(v, p[0], p[1], act, flip = dir < 0f, place = b.place, slot = b.slot, lantern = light && dark)
        }

        // ------------------------------------------------------------------ while it's on

        fun on(stir: Stir, byDay: (Boolean) -> List<Placed>): List<Placed> = when {
            stir.threat -> threat(stir)
            stir.kind == EventKind.STORM -> storm(stir)
            stir.kind == EventKind.MERCHANT -> merchant(stir)
            stir.kind == EventKind.FESTIVAL -> festival(stir, byDay)
            else -> routine
        }

        /** Where [stir]'s threat wants each of the village while it's on (null: in the house). */
        fun threatGoals(stir: Stir): Map<String, Goal?> {
            val stances = Reactions.stances(state, folk, stir.kind, stir.strength)
            val threat = Threats.of(state, w, h)
            val out = LinkedHashMap<String, Goal?>()
            val guards = folk.filter { stances[it.id] == Stance.DEFENDER }
            val spots = threat?.let { guardSpots(it, guards.size) }.orEmpty()
            var fireSlot = 0
            for (v in folk) {
                val st = stances[v.id] ?: Stance.OTHER
                out[v.id] = when {
                    st == Stance.LOOKOUT -> tower(threat)?.let { Goal(it[0], it[1], Activity.WAVE, it[2] > 0f, TownPlace.At(BuildingType.WATCHTOWER), z = TOWER_Z, lantern = dark) }
                    st == Stance.DEFENDER && threat != null -> spots.getOrNull(guards.indexOf(v))?.let { g ->
                        Goal(g[0], g[1], Activity.GUARD, flip = (threat.cx - threat.cy) - (g[0] - g[1]) < 0f, place = TownPlace.Forest, lantern = dark)
                    }
                    // who's in the house or asleep stays in; who's out gathers by the fire or goes home
                    byId[v.id] == null -> null
                    Reactions.byFire(st) -> fire(fireSlot++, if (dark) Activity.WARM else Activity.STAND)
                    else -> door(v)
                }
            }
            return out
        }

        fun threat(stir: Stir): List<Placed> {
            val goals = threatGoals(stir)
            return folk.mapNotNull { v ->
                val g = goals[v.id]
                val child = Routine.kindOf(v) == Routine.Kind.CHILD
                // the defenders and the lookout get up for it (with a torch at night); the others from where their day has them
                val from = if (g != null && (g.activity == Activity.GUARD || g.z > 0f)) daily(v) else byId[v.id]?.goal()
                walk(v, from, g, stir.since, hurry = if (child) HURRY else 1.2f, light = g?.lantern == true)
            }
        }

        /** The palisade's ring, when there is one: its stakes and its gates' beams hide whoever stands just behind them. */
        val ring: PalisadeRing? by lazy {
            if (state.buildings.none { it.type == BuildingType.PALISADE }) null else Palisade.ring(land, state.age, fg)
        }

        /** The buildings on plots, but the fields (flat): they hide whoever stands behind them. */
        val tall: List<si.lanisce.lani.game.Building> by lazy {
            state.buildings.filter { it.type.onPlot && it.type != BuildingType.FIELD && it.plot in 0 until VillageLayout.MAX_PLOTS }
        }

        /**
         * Whether someone standing at world ([x], [y]) would be hidden on the map: their head or chest behind a stake of the
         * palisade, a gate's beam, an animal or a building in front of them (nearer the bottom of the screen). A defender has
         * to be seen.
         */
        fun hidden(x: Float, y: Float): Boolean {
            // the head and the chest as the closest look draws them (the scene's people: about ten nominal pixels tall, three
            // to either side), the smaller figures within
            val px = (x - y) * 4f; val py = (x + y) * 2f
            val head = py - 8f; val chest = py - 4.5f
            fun covers(top: Float, bottom: Float) = head in top..bottom || chest in top..bottom
            ring?.let { r ->
                for (st in r.stakes) {
                    // not in the palisade itself, among its stakes
                    if (abs(st.x - x) < 0.6f && abs(st.y - y) < 0.6f) return true
                    if (st.x + st.y <= x + y + 0.05f) continue
                    val sx = (st.x - st.y) * 4f; val sy = (st.x + st.y) * 2f
                    val half = (if (st.post) Palisade.POST_WIDTH else Palisade.WIDTH) * 4f + 3f
                    if (abs(sx - px) <= half && covers(sy - st.h - Palisade.POINT, sy)) return true
                }
                for (g in r.gates) {
                    if (!g.lintel) continue
                    val a = r.stakes[g.a]; val b = r.stakes[g.b]
                    if (maxOf(a.x + a.y, b.x + b.y) <= x + y + 0.05f) continue
                    val ax = (a.x - a.y) * 4f; val bx = (b.x - b.y) * 4f
                    if (px < minOf(ax, bx) - 2f || px > maxOf(ax, bx) + 2f) continue
                    val u = if (abs(bx - ax) < 0.01f) 0.5f else ((px - ax) / (bx - ax)).coerceIn(0f, 1f)
                    val base = (a.x + a.y) * 2f + ((b.x + b.y) - (a.x + a.y)) * 2f * u
                    // the beam across the posts, just below their points (two pixels thick): drawn with the nearer post, over
                    // whoever stands in the gate behind it; anywhere on them (in the gate, in front of it, they're seen)
                    val z = minOf(a.h, b.h) - 0.5f
                    if (py - 10f <= base - z + 2.5f && py >= base - z - 0.5f) return true
                }
            }
            // the animals themselves (the bear is big)
            threat?.let { t ->
                val tall = if (t.kind == EventKind.BEAR) 16f else 9f
                for (b in t.beasts) {
                    if (b.x + b.y <= x + y) continue
                    val bx = (b.x - b.y) * 4f; val by = (b.x + b.y) * 2f
                    if (abs(bx - px) <= 9f && covers(by - tall, by)) return true
                }
            }
            for (b in tall) {
                val c = land.centers[b.plot]
                if (c[0] + c[1] <= x + y) continue
                val bx = (c[0] - c[1]) * 4f; val by = (c[0] + c[1]) * 2f
                if (abs(bx - px) <= 13f && covers(by - 6f - spriteHeight(b.type), by + 6f)) return true
            }
            return false
        }

        /**
         * Whether world ([x], [y]) is out of the clearing: among the trees (they'd hide whoever stands there), or outside
         * the palisade. The defenders stand up to the animals from within.
         */
        fun outside(x: Float, y: Float): Boolean {
            val sx = (x - y) * 4f; val sy = (x + y) * 2f
            // (up to the palisade's line: its gate's opening is in)
            val edge = land.forestEdge(state.age, VillageLayout.angle(sx, sy)) - (if (ring == null) 4f else Palisade.INSET - 1f)
            return VillageLayout.screenMetric(sx, sy) > edge
        }

        /**
         * Where [n] defenders stand against [t]: on the way from the animals to the fire, as near them as [Threats.GUARD]
         * lets, within the clearing (inside the palisade), side by side across the way; each where they can stand (not in
         * a building, the fire, the crops) and be seen (not behind the palisade or a building), else a step nearer the fire.
         */
        fun guardSpots(t: Threats.Threat, n: Int): List<FloatArray> {
            if (n <= 0) return emptyList()
            // the same village and animals, the same spots: worked out once (the ways are the village's and the animals')
            synchronized(guards) { guards[ways]?.takeIf { it.size == n }?.let { return it } }
            val d = hypot(t.cx, t.cy).coerceAtLeast(0.01f)
            val ux = -t.cx / d; val uy = -t.cy / d
            val px = -uy; val py = ux
            // past the foremost of them on the way to the fire
            val front = t.beasts.maxOf { (it.x - t.cx) * ux + (it.y - t.cy) * uy } + 0.4f
            val out = ArrayList<FloatArray>()
            for (i in 0 until n) {
                val off = (if (i % 2 == 0) 1f else -1f) * ((i + 1) / 2) * 1.2f
                // the nearest the animals and their own place across the way where they can stand and be seen
                var best: FloatArray? = null
                var score = Float.MAX_VALUE
                var ahead = front
                while (ahead < d - 1.5f) {
                    var side = -3.6f
                    while (side <= 3.6f) {
                        val sc = ahead + abs(side - off) * 0.8f
                        if (sc < score) {
                            val x = t.cx + ux * ahead + px * side; val y = t.cy + uy * ahead + py * side
                            if (t.distance(x, y) >= Threats.GUARD && !blocked(x, y) && !outside(x, y) && !hidden(x, y) && out.none { hypot(it[0] - x, it[1] - y) < 0.9f }) {
                                best = floatArrayOf(x, y); score = sc
                            }
                        }
                        side += 0.6f
                    }
                    ahead += 0.3f
                }
                out += best ?: floatArrayOf(t.cx + ux * (t.keep + 1.2f) + px * off, t.cy + uy * (t.keep + 1.2f) + py * off)
            }
            synchronized(guards) { guards[ways] = out }
            return out
        }

        /** The watchtower's platform (world cells) and which way the lookout faces the animals (1: right), or null without one. */
        fun tower(t: Threats.Threat?): FloatArray? {
            val b = state.buildings.firstOrNull { it.type == BuildingType.WATCHTOWER && !it.damaged && it.plot in 0 until VillageLayout.MAX_PLOTS } ?: return null
            val o = land.origin(b.plot)
            val x = o[0] + 1.5f; val y = o[1] + 1.5f
            val face = if (t == null) 1f else if ((t.cx - t.cy) - (x - y) < 0f) 1f else -1f
            return floatArrayOf(x, y, face)
        }

        /** A storm: everyone goes home (the children running); someone dashes between doors, someone takes the washing in. */
        fun storm(stir: Stir): List<Placed> {
            val out = ArrayList<Placed>()
            val dasher = if (dark) null else stormDash()
            val laundry = if (dark || month == 12 || month <= 2) null else laundry()
            for (v in folk) {
                when {
                    v.id == dasher?.id -> walk(v, dasher.a, dasher.b, dasher.since, hurry = RAIN, hat = true)?.let { out += it }
                    v.id == laundry?.first -> laundry.second?.let { out += it }
                    else -> walk(v, byId[v.id]?.goal(), byId[v.id]?.let { door(v) }, stir.since, hurry = RAIN, hat = Routine.kindOf(v) != Routine.Kind.CHILD && Math.floorMod(v.id.hashCode(), 2) == 0)?.let { out += it }
                }
            }
            return out
        }

        /** Who dashes now (once every [DASH_MIN] minutes, between two doors), from where to where, how long ago (s); null: nobody. */
        fun stormDash(): Dash? {
            val awake = folk.filter { Routine.kindOf(it) != Routine.Kind.CHILD && !asleep(it) }
            if (awake.size < 2) return null
            val k = floor(minute / DASH_MIN).toLong()
            val since = (minute - k * DASH_MIN) * 60.0
            val i = Math.floorMod(si.lanisce.lani.game.scene.Happenings.roll(state.seed, date, "dash:$k").times(awake.size).toInt(), awake.size)
            val v = awake[i]
            val a = door(v) ?: return null
            val b = awake.map { door(it) }.firstOrNull { it != null && hypot(it.x - a.x, it.y - a.y) > 2f } ?: return null
            return Dash(v.id, a.outside(), b, since)
        }

        data class Dash(val id: String, val a: Goal, val b: Goal, val since: Double)

        /** Who takes the washing in now (the first [LAUNDRY_S] s of every half hour): the aunt or a grandmother at her line. */
        fun laundry(): Pair<String, Placed?>? {
            val k = floor(minute / LAUNDRY_MIN).toLong()
            val since = (minute - k * LAUNDRY_MIN) * 60.0
            if (since >= LAUNDRY_S + Reactions.WALK_S) return null
            val v = folk.firstOrNull { (Routine.kindOf(it) == Routine.Kind.AUNT || Routine.kindOf(it) == Routine.Kind.GRANDMA) && !asleep(it) && lineOf(it) != null } ?: return null
            val line = lineOf(v) ?: return null
            if (since < LAUNDRY_S) return v.id to Placed(v, line.x, line.y, Activity.HANG, true, line.place)
            return v.id to walk(v, line, door(v), since - LAUNDRY_S, hurry = RAIN)
        }

        /** [v]'s washing line, by their household's building, or null (no house, the tent at the pond). */
        fun lineOf(v: Villager): Goal? {
            val home = day.homes[v.id] ?: TownMarkers.homeOf(v, state)
            if (TownPeople.nookOf(state, home, si.lanisce.lani.game.villagers.Doing.HANG) != TownPeople.Nook.LINE) return null
            val g = TownPeople.groundOf(v, home, state, comp, w, h, Noise.hash(v.id.hashCode(), 11), land, TownPeople.Nook.LINE) ?: return null
            return Goal(g[0], g[1], Activity.HANG, true, home)
        }

        /**
         * Who stands at the merchant's cart in the quarter of an hour [k] (by daylight), in their order there: the innkeeper,
         * then a few others of [eligible], from every other one of them by turns, so a quarter's few are never the last one's.
         */
        fun cartFew(k: Long, eligible: List<Villager>): List<String> {
            val keeper = eligible.firstOrNull { Routine.kindOf(it) == Routine.Kind.INNKEEPER }?.id
            val pool = eligible.filter { it.id != keeper }.filterIndexed { i, _ -> i % 2 == Math.floorMod(k, 2L).toInt() }
                .sortedBy { si.lanisce.lani.game.scene.Happenings.roll(state.seed, date, "cart:$k:${it.id}") }
            return listOfNotNull(keeper) + pool.take(CART_FEW).map { it.id }
        }

        /** Where the few at the cart stand: in front of it and at its back, facing it. */
        fun cartSpots(): List<Goal> {
            val c = Threats.cart(state, w)
            val offs = arrayOf(floatArrayOf(0.9f, 1.5f), floatArrayOf(-0.1f, 1.6f), floatArrayOf(1.8f, 0.4f), floatArrayOf(1.9f, -0.7f), floatArrayOf(-1.0f, 1.5f))
            return offs.map { floatArrayOf(c[0] + it[0], c[1] + it[1]) }.filter { !blocked(it[0], it[1]) }.map { p ->
                Goal(p[0], p[1], Activity.CHAT, flip = (c[0] - c[1]) - (p[0] - p[1]) < 0f, place = TownPlace.Spot("road"))
            }
        }

        /** Of the village, who may go to the cart now: out by their day, not waiting for the learner (nor busy with a happening). */
        fun cartEligible(): List<Villager> = folk.filter { byId[it.id] != null && it.id !in standing && day.calls[it.id] == null }

        /** The merchant: by daylight the innkeeper and a few others at his cart, a few others every quarter of an hour. */
        fun merchant(stir: Stir): List<Placed> {
            if (dark) return routine
            val eligible = cartEligible()
            val spots = cartSpots()
            if (spots.isEmpty()) return routine
            val k = floor(minute / CART_MIN).toLong()
            val now = cartFew(k, eligible).take(spots.size)
            val before = cartFew(k - 1, eligible).take(spots.size)
            val sinceK = (minute - k * CART_MIN) * 60.0
            return folk.mapNotNull { v ->
                val i = now.indexOf(v.id)
                val j = before.indexOf(v.id)
                when {
                    // the innkeeper stays; the others come at the quarter's start (or the merchant's, the first of them)
                    i >= 0 -> walk(v, byId[v.id]?.goal(), spots[i], minOf(if (j == i) Double.POSITIVE_INFINITY else sinceK, stir.since))
                    // the last quarter's few walk back to their day
                    j >= 0 && sinceK < Reactions.WALK_S -> walk(v, spots[j], byId[v.id]?.goal(), sinceK)
                    else -> byId[v.id]
                }
            }
        }

        /** A festival: round the fire as their day has it at a feast, dancing, one playing the harmonika; on their way there at first. */
        fun festival(stir: Stir, byDay: (Boolean) -> List<Placed>): List<Placed> {
            val feast = routine.map { p -> if (p.place == TownPlace.Fire) p.copy(activity = if (p.id == musician()) Activity.MUSIC else Activity.PLAY) else p }
            if (stir.since >= Reactions.WALK_S * 2) return feast
            val before = byDay(false).associateBy { it.id }
            return feast.mapNotNull { p -> walk(p.villager, before[p.id]?.goal() ?: door(p.villager), p.goal(), stir.since) }
        }

        /** Who plays the harmonika at a feast: the winemaker, else a grown-up (by the village's order). */
        fun musician(): String? {
            val at = routine.filter { it.place == TownPlace.Fire }.map { it.villager }
            return (at.firstOrNull { Routine.kindOf(it) == Routine.Kind.WINEMAKER } ?: at.firstOrNull { Routine.kindOf(it) == Routine.Kind.ADULT })?.id
        }

        // ------------------------------------------------------------------ afterwards

        /** A won fight: everyone up round the fire with the defenders, cheering; then back to their day. */
        fun won(stir: Stir): List<Placed> {
            val was = threatGoals(Stir(stir.kind, Stir.Phase.ON, Double.POSITIVE_INFINITY, stir.strength))
            val stances = Reactions.stances(state, folk, stir.kind, stir.strength)
            val tower = tower(null)
            // the defenders first round the fire, the others after them
            val order = folk.filter { stances[it.id] == Stance.DEFENDER } + folk.filter { stances[it.id] != Stance.DEFENDER }
            val cheer = HashMap<String, Goal?>()
            var slot = 0
            for (v in order) {
                val st = stances[v.id]
                cheer[v.id] = when {
                    st == Stance.LOOKOUT && tower != null -> Goal(tower[0], tower[1], Activity.CHEER, tower[2] > 0f, TownPlace.At(BuildingType.WATCHTOWER), z = TOWER_Z)
                    st != Stance.DEFENDER && asleep(v) -> null
                    else -> fire(slot++, Activity.CHEER)
                }
            }
            return folk.mapNotNull { v ->
                if (stir.since < Reactions.CHEER_S) walk(v, was[v.id] ?: door(v), cheer[v.id], stir.since)
                else walk(v, cheer[v.id] ?: door(v), daily(v), stir.since - Reactions.CHEER_S)
            }
        }

        /** After anything else: from where it had them back to their day. */
        fun after(stir: Stir, byDay: (Boolean) -> List<Placed>): List<Placed> {
            val was: Map<String, Goal?> = when {
                stir.threat -> threatGoals(Stir(stir.kind, Stir.Phase.ON, Double.POSITIVE_INFINITY, stir.strength))
                stir.kind == EventKind.STORM -> folk.associate { it.id to door(it) }
                stir.kind == EventKind.FESTIVAL -> byDay(true).associate { it.id to it.goal() }
                else -> emptyMap()
            }
            if (was.isEmpty()) return routine
            return folk.mapNotNull { v -> walk(v, was[v.id] ?: door(v), byId[v.id]?.goal() ?: door(v), stir.since) }
        }

        // ------------------------------------------------------------------ a building to patch up

        /** [placed] with someone patching up the building a lost fight damaged, by daylight that day and the next ([Reactions.repair]). */
        fun repair(placed: List<Placed>): List<Placed> {
            if (dark) return placed
            val (id, b) = Reactions.repair(state, folk.filter { byId[it.id] != null && it.id !in standing && day.calls[it.id] == null }, date) ?: return placed
            val v = folk.firstOrNull { it.id == id } ?: return placed
            val spot = repairSpot(b) ?: return placed
            // on the way there right after the fight, from wherever it had them
            val since = state.ended?.takeIf { wall > 0L }?.let { (wall - it.at) / 1000.0 }?.takeIf { it in 0.0..Reactions.WALK_S } ?: Double.POSITIVE_INFINITY
            val p = walk(v, byId[v.id]?.goal(), spot, since) ?: return placed
            return placed.filter { it.id != v.id } + p
        }

        /** Where someone patches up [b]: at its right front corner, facing it; at the palisade's breach, inside it. */
        fun repairSpot(b: si.lanisce.lani.game.Building): Goal? {
            if (b.type == BuildingType.PALISADE) {
                val ring = Palisade.ring(land, state.age, fg)
                val st = Palisade.breach(ring) ?: return null
                val d = hypot(st.x, st.y).coerceAtLeast(0.01f)
                val x = st.x - st.x / d * 0.9f; val y = st.y - st.y / d * 0.9f
                return Goal(x, y, Activity.REPAIR, flip = (st.x - st.y) - (x - y) < 0f, place = TownPlace.At(BuildingType.PALISADE), load = Load.WOOD)
            }
            if (b.plot !in 0 until VillageLayout.MAX_PLOTS || (b.type == BuildingType.TENT && TentMove.moved(state))) return null
            val c = land.centers[b.plot]
            for (o in arrayOf(floatArrayOf(2.0f, 0.6f), floatArrayOf(0.6f, 2.0f), floatArrayOf(2.0f, -0.6f), floatArrayOf(-0.6f, 2.0f))) {
                val x = c[0] + o[0]; val y = c[1] + o[1]
                if (blocked(x, y)) continue
                // facing the building's wall beside them
                return Goal(x, y, Activity.REPAIR, flip = (c[0] - c[1]) - (x - y) < 0f, place = TownPlace.of(b, state), load = Load.WOOD)
            }
            return null
        }
    }
}
