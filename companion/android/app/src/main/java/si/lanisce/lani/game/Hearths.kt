package si.lanisce.lani.game

import kotlinx.serialization.Serializable
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.SpotCycle
import si.lanisce.lani.game.culture.WorldFile
import si.lanisce.lani.game.scene.CycleRound
import si.lanisce.lani.game.scene.SpotCycles
import si.lanisce.lani.l10n.bi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A kind of hearth (companion/GAME.md "Hearths"): the centre's three looks by the age, the homes', the school's, the forge. */
enum class HearthKind(val emoji: String) {
    /** The open campfire in the middle (Ogenj, Tabor, Zaselek): burning all day. */
    CAMPFIRE("🔥"),
    /** The stone fire ring with its benches (Vas): lit for the evening's gathering. */
    FIRE_RING("🔥"),
    /** The iron fire basket on the cobbled square (Trg, Mesto): lit in the evening; the kres on a feast evening. */
    FIRE_BASKET("🔥"),
    HUT("🛖"),
    HOUSE("🏠"),
    SCHOOL("🏫"),
    /** The smith's forge: on charcoal where the land has a charcoal burner. */
    FORGE("⚒️");

    /** One of the centre's looks. */
    val centre: Boolean get() = this == CAMPFIRE || this == FIRE_RING || this == FIRE_BASKET
}

/**
 * One of the village's hearths: [id] is [Hearths.CENTRE] or its building's id; [wood] what it burns a day while lit (0 for
 * a forge on [charcoal]).
 */
data class Hearth(val id: String, val kind: HearthKind, val building: Building? = null, val wood: Int, val charcoal: Boolean = false)

/**
 * The village's hearths as the day's tick left them ([GameState.hearths]): the day decided ([on]); the hearths out ([out]:
 * for want of wood, a cold forge, and those the storm blew out, [blown], which light again only the next day); the first
 * day the village kept its hearths ([since]: the charcoal piles whose wood was due before it count as given); the last day
 * the smith's charcoal lasts as that tick saw it ([charcoal], ISO; empty: no smithy or no charcoal burner), for the bridge
 * and the tutor (the app works it out from the date: [Hearths.charcoalUntil]).
 */
@Serializable
data class HearthDay(
    val on: String = "",
    val out: List<String> = emptyList(),
    val blown: List<String> = emptyList(),
    val since: String = "",
    val charcoal: String = "",
)

/**
 * The charcoal pile's wood the village gives (companion/GAME.md "Hearths"): [amount] 🪵 for round [round] of [spot], taken
 * on a day from [from] to [until] (its wood stages), [paid] once it was, the keeper ([keeper], "Oglar Miha") waiting
 * meanwhile ([waiting]: the days are on and it isn't paid).
 */
data class KopaWood(
    val spot: String, val round: String, val amount: Int, val from: LocalDate, val until: LocalDate,
    val paid: Boolean, val waiting: Boolean, val keeper: String?,
)

/**
 * Every building with a hearth has its own fire (companion/GAME.md "Hearths"; Jan, 2026-10-20: "The fire in the middle would
 * be exchanged with every house plus the smithy, no?"): the centre's (the campfire, the fire ring, the fire basket by the
 * age), the huts', the houses', the school's, the forge's. Each day's tick pays their wood from the store, the heart of
 * the village first, and the first it can't pay goes out with every one after it: the outlying first. The forge burns the
 * smith's charcoal (Oglar Miha's piles, whose wood the village gives once a round). Morale follows the share lit; the old
 * fire field is written from it for an older app. Pure.
 */
object Hearths {
    /** The centre's id among the hearths (its place stays the fire's: [si.lanisce.lani.game.scene.TownPlace.Fire]). */
    const val CENTRE = "centre"

    /**
     * The age of the village the screens show (the learner's: the game controller sets it as the village changes), for
     * naming its centre where a label has no state at hand ([si.lanisce.lani.game.scene.TownPlace.Fire]: "Ognjišče · The
     * fire ring" at Vas).
     */
    @Volatile
    var shown: Age = Age.OGENJ

    /** The centre's look at [age]: the campfire to Zaselek, the fire ring at Vas, the square's fire basket from Trg. */
    fun centreKind(age: Age): HearthKind = when {
        age >= Age.TRG -> HearthKind.FIRE_BASKET
        age >= Age.VAS -> HearthKind.FIRE_RING
        else -> HearthKind.CAMPFIRE
    }

    /** "Ogenj · The campfire", "Ognjišče · The fire ring", "Žerjavnica · The fire basket". */
    fun centreName(age: Age): String = when (centreKind(age)) {
        HearthKind.FIRE_BASKET -> bi("hearths.fireBasket")
        HearthKind.FIRE_RING -> bi("hearths.fireRing")
        else -> bi("hearths.campfire")
    }

    /**
     * The title a scene shows at [age] (its own, else the centre's by the age): the campfire's scene ("Ob ognju · At the
     * campfire", art `campfire`, opened from the fire) is "Ob ognjišču · At the fire ring" at Vas and "Na trgu ob ognju · By
     * the fire on the square" from Trg (companion/SCENES.md "Ob ognju"); every other scene its own.
     */
    fun sceneTitle(scene: si.lanisce.lani.game.scene.SceneSpec, age: Age = shown): String {
        if (scene.art != "campfire" || "fire" !in scene.from) return scene.title
        return when (centreKind(age)) {
            HearthKind.FIRE_BASKET -> bi("hearths.sceneSquare")
            HearthKind.FIRE_RING -> bi("hearths.sceneRing")
            else -> scene.title
        }
    }

    /** "ob ognju · by the fire", "ob ognjišču · by the fire ring", "na trgu · on the square". */
    fun centreAt(age: Age): String = when (centreKind(age)) {
        HearthKind.FIRE_BASKET -> bi("hearths.fireBasketAt")
        HearthKind.FIRE_RING -> bi("hearths.fireRingAt")
        else -> bi("hearths.campfireAt")
    }

    /**
     * Whether the smith's forge burns charcoal: the land has a charcoal burner whose pile goes to the smith (a spot's cycle
     * delivering to him: Primorska's Miha); else it is fed wood like a house.
     */
    fun onCharcoal(world: WorldFile = Cultures.current.world): Boolean = kopa(world) != null

    /** The spot whose cycle brings the smith his charcoal, and the cycle; null: none (the forge burns wood). */
    fun kopa(world: WorldFile = Cultures.current.world): Pair<String, SpotCycle>? {
        val smith = runCatching { Cultures.current.chest.smith.id }.getOrNull() ?: return null
        return world.spots.entries.firstNotNullOfOrNull { (id, s) -> s.cycle?.takeIf { it.delivery?.to == smith }?.let { id to it } }
    }

    /**
     * The village's hearths in the order their wood is paid (the last ones go out first): the forge (its own rule), the
     * campfire while it is the village's (to Zaselek), the school, the houses and huts from the plot nearest the centre
     * out, and from Vas the centre's evening fire last. A damaged building's hearth is cold and isn't among them.
     */
    fun of(s: GameState, world: WorldFile = Cultures.current.world): List<Hearth> {
        val standing = s.buildings.filter { !it.damaged }
        val charcoal = onCharcoal(world)
        val centre = centreKind(s.age)
        return buildList {
            standing.filter { it.type == BuildingType.SMITHY }.sortedBy { it.id }
                .forEach { add(Hearth(it.id, HearthKind.FORGE, it, if (charcoal) 0 else Catalog.FORGE_WOOD, charcoal)) }
            if (centre == HearthKind.CAMPFIRE) add(Hearth(CENTRE, centre, null, Catalog.CAMPFIRE_WOOD[s.age.ordinal.coerceAtMost(Catalog.CAMPFIRE_WOOD.lastIndex)]))
            standing.filter { it.type == BuildingType.SCHOOL }.sortedBy { it.id }.forEach { add(Hearth(it.id, HearthKind.SCHOOL, it, Catalog.SCHOOL_WOOD)) }
            standing.filter { it.type == BuildingType.HUT || it.type == BuildingType.HOUSE }
                .sortedWith(compareBy({ if (it.plot < 0) Int.MAX_VALUE else it.plot }, { it.id }))
                .forEach { b ->
                    if (b.type == BuildingType.HOUSE) add(Hearth(b.id, HearthKind.HOUSE, b, Catalog.HOUSE_WOOD))
                    else add(Hearth(b.id, HearthKind.HUT, b, Catalog.HUT_WOOD))
                }
            if (centre != HearthKind.CAMPFIRE) add(Hearth(CENTRE, centre, null, Catalog.CENTRE_WOOD))
        }
    }

    /** The 🪵 the hearths burn a day, all lit (half from the 4th missed day: [soft]). */
    fun need(s: GameState, soft: Boolean = false, world: WorldFile = Cultures.current.world): Int {
        val all = of(s, world).sumOf { it.wood }
        return if (soft) ceil(all / 2.0).toInt() else all
    }

    /**
     * The hearths lit now: those the day's tick didn't put out ([HearthDay.out]); in a save from before the hearths, as far
     * as the store pays a day of them in their order, the forge while it has charcoal.
     */
    fun lit(s: GameState, today: LocalDate = dayOf(s), world: WorldFile = Cultures.current.world): Set<String> {
        val list = of(s, world)
        val h = s.hearths
        if (h != null) return list.filter { it.id !in h.out }.map { it.id }.toSet()
        var wood = s.res(Res.WOOD)
        var short = false
        return buildSet {
            for (x in list) {
                if (x.charcoal) {
                    if ((charcoal(s, today, world) ?: 0) > 0) add(x.id)
                    continue
                }
                if (!short && wood >= x.wood) { wood -= x.wood; add(x.id) } else short = true
            }
        }
    }

    /** The hearths out now, in their order (the first to go out last). */
    fun out(s: GameState, today: LocalDate = dayOf(s), world: WorldFile = Cultures.current.world): List<Hearth> {
        val lit = lit(s, today, world)
        return of(s, world).filter { it.id !in lit }
    }

    /** The share of the hearths lit, 0..1 (1 with none). */
    fun share(s: GameState, today: LocalDate = dayOf(s), world: WorldFile = Cultures.current.world): Float {
        val all = of(s, world)
        if (all.isEmpty()) return 1f
        return lit(s, today, world).size.toFloat() / all.size
    }

    /**
     * Whether a hearth is out for want of wood (not blown out by the storm, not the forge's charcoal): the goal's "gather
     * wood". Not before the day's tick decided them (an older save before its first tick, a friend's town on a visit).
     */
    fun short(s: GameState, today: LocalDate = dayOf(s), world: WorldFile = Cultures.current.world): Boolean {
        val h = s.hearths ?: return false
        return out(s, today, world).any { !it.charcoal && it.id !in h.blown }
    }

    /** The old fire field from the share lit, for an older app or bridge: 10 + 90 × the share (100 when all are lit). */
    fun fire(share: Float): Int = (GameState.MIN_FIRE + (100 - GameState.MIN_FIRE) * share.coerceIn(0f, 1f)).roundToInt()

    /** Days the store's 🪵 lasts the hearths, all lit; null when they burn none. */
    fun daysOfWood(s: GameState, world: WorldFile = Cultures.current.world): Int? {
        val need = need(s, world = world)
        return if (need <= 0) null else s.res(Res.WOOD) / need
    }

    /** The day of [s]'s last tick, else today. */
    private fun dayOf(s: GameState): LocalDate = isoDay(s.lastTick) ?: LocalDate.now()

    // --- the day ------------------------------------------------------------------------------------------------------

    /**
     * One day of the hearths ([date], half their wood from the 4th missed day: [soft]): the forge on its charcoal while it
     * lasts ([charcoalUntil]), the wood paid in order (the first the store can't pay goes out, and every one after it), the charcoal pile's wood when it is due (the store keeping [Catalog.KOPA_KEEPS_DAYS] days of the hearths' wood),
     * a pile whose wood days passed unpaid not built. Returns the state, the morale it costs (the share out) and the
     * chronicle's lines (the pile's wood given, a pile not built).
     */
    internal fun day(s0: GameState, date: LocalDate, soft: Boolean, world: WorldFile = Cultures.current.world): Day {
        var s = s0
        val was = s.hearths ?: HearthDay(since = date.toString())
        val since = was.since.ifEmpty { date.toString() }
        s = s.copy(hearths = was.copy(since = since))
        // the charcoal: what the smith has today (until the next pile after the last one he got, and a week)
        val pile = kopa(world)
        val until = charcoalUntil(s, date, world)
        // the wood, in order
        val list = of(s, world)
        val f = if (soft) 0.5 else 1.0
        var left = s.res(Res.WOOD).toDouble()
        var short = false
        val out = ArrayList<String>()
        for (h in list) {
            if (h.charcoal) {
                if (until == null || until.isBefore(date)) out += h.id
                continue
            }
            val cost = h.wood * f
            if (!short && left + 1e-9 >= cost) left -= cost else { short = true; out += h.id }
        }
        val spent = ceil(s.res(Res.WOOD) - left - 1e-9).toInt().coerceIn(0, s.res(Res.WOOD))
        s = s.copy(resources = s.resources.sumWith(mapOf(Res.WOOD to -spent)))
        s = s.copy(hearths = HearthDay(on = date.toString(), out = out, blown = emptyList(), since = since, charcoal = until?.toString().orEmpty()))
        // the charcoal pile's wood
        val news = ArrayList<Pair<String, String>>()
        if (pile != null) s = kopaDay(s, pile.first, pile.second, date, world, news)
        val share = if (list.isEmpty()) 1f else (list.size - out.size).toFloat() / list.size
        val morale = ((if (soft) Catalog.HEARTH_MORALE_SOFT else Catalog.HEARTH_MORALE) * (1f - share)).roundToInt()
        return Day(s.copy(fire = fire(share)), morale, out.isNotEmpty() && list.any { it.id in out && !it.charcoal }, news)
    }

    /**
     * A day of the hearths ([day]): the [state] after it, the [morale] it costs, whether a hearth went out for want of wood
     * ([cold]), the chronicle's lines ([news]).
     */
    internal data class Day(val state: GameState, val morale: Int, val cold: Boolean, val news: List<Pair<String, String>>)

    /**
     * The days the tick skips (a gap longer than it catches up on, [from] to [to]): the charcoal pile's wood days among them
     * passed unpaid (its rounds not built).
     */
    internal fun skipped(s: GameState, from: LocalDate, to: LocalDate, world: WorldFile = Cultures.current.world): GameState {
        val (spot, c) = kopa(world) ?: return s
        if (to.isBefore(from) || s.hearths == null) return s
        return kopaDay(s, spot, c, to.plusDays(1), world, ArrayList(), pay = false)
    }

    /**
     * Wood that came in today lights the hearths that went out again, in their order, as far as it pays their day (not
     * those the storm blew out: tomorrow). Nothing before the day's tick decided them.
     */
    fun relight(s: GameState, world: WorldFile = Cultures.current.world): GameState {
        val h = s.hearths ?: return s
        if (h.out.isEmpty()) return s
        var wood = s.res(Res.WOOD)
        val out = h.out.toMutableList()
        for (x in of(s, world)) {
            if (x.id !in out || x.charcoal || x.id in h.blown) continue
            if (wood < x.wood) break
            wood -= x.wood
            out -= x.id
        }
        if (out.size == h.out.size) return s
        val lit = s.copy(resources = s.resources + (Res.WOOD to wood), hearths = h.copy(out = out))
        return lit.copy(fire = fire(share(lit, world = world)))
    }

    /**
     * The storm's wind blows out [n] hearths, the outlying first (the centre and the forge aside while there are others),
     * until the next day: no wood brought today lights them again.
     */
    fun blowOut(s: GameState, n: Int, world: WorldFile = Cultures.current.world): GameState {
        if (n <= 0) return s
        val h = s.hearths ?: HearthDay(on = s.lastTick, since = s.lastTick)
        val lit = lit(s, world = world)
        val list = of(s, world).filter { it.id in lit }
        val homes = list.filter { !it.kind.centre && !it.charcoal }.reversed()
        val rest = list.filter { it.kind.centre || it.charcoal }.reversed()
        val blown = (homes + rest).take(n).map { it.id }
        if (blown.isEmpty()) return s
        val after = s.copy(hearths = h.copy(out = (h.out + blown).distinct(), blown = (h.blown + blown).distinct()))
        return after.copy(fire = fire(share(after, world = world)))
    }

    // --- the forge's charcoal -----------------------------------------------------------------------------------------

    /**
     * Days of charcoal the smith has on [today] (that day in): until the day [charcoalUntil] gives; 0 when it is out; null
     * without a smithy or a charcoal burner.
     */
    fun charcoal(s: GameState, today: LocalDate = dayOf(s), world: WorldFile = Cultures.current.world): Int? {
        if (kopa(world) == null || s.buildings.none { it.type == BuildingType.SMITHY }) return null
        val until = charcoalUntil(s, today, world) ?: return 0
        return (ChronoUnit.DAYS.between(today, until) + 1).toInt().coerceAtLeast(0)
    }

    /**
     * The last day the smith's charcoal lasts, as of [date]: a pile's lasts until the next pile comes and a week more
     * ([Catalog.CHARCOAL_MARGIN]: "about the cycle", the season's last through the winter to the next spring's), and so do
     * the sacks a new smithy comes with (Tone brings them from the valley); the latest of those that came by [date]. A pile
     * counts when it came to the village's smith ([delivers]: built with its wood, or due before the village kept its
     * hearths). An older save's smithy, built on an unknown day, stands a year back. Null without a smithy or a charcoal
     * burner. A function of the date, the village's marks and the smithy's day, as the cycle is: the bridge works out the
     * same.
     */
    fun charcoalUntil(s: GameState, date: LocalDate, world: WorldFile = Cultures.current.world): LocalDate? {
        val (spot, c) = kopa(world) ?: return null
        val forge = s.buildings.filter { it.type == BuildingType.SMITHY }.minByOrNull { it.builtAt } ?: return null
        val built = if (forge.builtAt > 0) Instant.ofEpochMilli(forge.builtAt).atZone(ZoneId.systemDefault()).toLocalDate() else null
        val from = maxOf(built ?: date.minusDays(365), date.minusDays(365)).coerceAtMost(date)
        var until = lasts(c, spot, from)
        for ((r, on) in SpotCycles.deliveries(c, spot, from, date)) if (delivers(s, spot, c, r)) until = maxOf(until, lasts(c, spot, on))
        return until
    }

    /** The last day charcoal that came on [on] lasts: until [c]'s next pile's delivery after it, and [Catalog.CHARCOAL_MARGIN] days more. */
    private fun lasts(c: SpotCycle, spot: String, on: LocalDate): LocalDate {
        val next = SpotCycles.deliveries(c, spot, on.plusDays(1), on.plusDays(400)).firstOrNull()?.second ?: on.plusDays(365)
        return next.plusDays(Catalog.CHARCOAL_MARGIN.toLong())
    }

    // --- the charcoal pile's wood -------------------------------------------------------------------------------------

    /** Whether [c] takes its wood from the village (some stage with `wood`). */
    private fun takesWood(c: SpotCycle) = c.stages.any { it.wood }

    /** The first day [s] kept its hearths, if it has. */
    private fun since(s: GameState?): LocalDate? = s?.hearths?.since?.let(::isoDay)

    /**
     * Whether the village gives round [r] of [c] its wood: [c] takes it, a smithy stands to burn its charcoal, and the
     * round's wood days aren't over before the village kept its hearths (those count as given).
     */
    fun waits(s: GameState?, c: SpotCycle, r: CycleRound): Boolean {
        if (s == null || !takesWood(c) || s.buildings.none { it.type == BuildingType.SMITHY }) return false
        val since = since(s) ?: return false
        val until = r.woodUntil ?: return false
        return !until.isBefore(since)
    }

    /**
     * Whether round [r] of [spot]'s [c] brings the village's smith his charcoal: not burnt down, not unbuilt, and built with
     * the village's wood ([si.lanisce.lani.game.scene.CycleMarks.stacked]), or its wood due before the village kept its
     * hearths (or none of it the village's).
     */
    fun delivers(s: GameState?, spot: String, c: SpotCycle, r: CycleRound): Boolean {
        val m = SpotCycles.marks(s, spot)
        if (r.key in m.lost || r.key in m.unbuilt) return false
        if (!takesWood(c)) return true
        val until = r.woodUntil ?: return true
        val since = since(s) ?: return true
        return until.isBefore(since) || r.key in m.stacked
    }

    /**
     * The charcoal pile's day [date]: on its wood days, the store gives the round's wood when it can and keeps
     * [Catalog.KOPA_KEEPS_DAYS] days of the hearths' (the chronicle says so); a round whose wood days passed unpaid while
     * the village had its smithy isn't built.
     */
    private fun kopaDay(s0: GameState, spot: String, c: SpotCycle, date: LocalDate, world: WorldFile, news: MutableList<Pair<String, String>>, pay: Boolean = true): GameState {
        var s = s0
        val rounds = SpotCycles.rounds(c, spot, date.year - 1) + SpotCycles.rounds(c, spot, date.year)
        for (r in rounds) {
            val from = r.woodFrom ?: continue
            val until = r.woodUntil ?: continue
            if (date.isBefore(from) || !waits(s, c, r)) continue
            val m = SpotCycles.marks(s, spot)
            if (r.key in m.stacked || r.key in m.unbuilt) continue
            if (!date.isAfter(until)) {
                if (!pay) continue
                val amount = Catalog.kopaWood(s.age)
                val keep = Catalog.KOPA_KEEPS_DAYS * need(s, world = world)
                if (s.res(Res.WOOD) >= amount + keep) {
                    s = s.copy(
                        resources = s.resources.sumWith(mapOf(Res.WOOD to -amount)),
                        cycles = s.cycles + (spot to m.copy(stacked = (m.stacked + r.key).takeLast(si.lanisce.lani.game.scene.CycleMarks.KEEP))),
                    )
                    news += "🪵" to bi("hearths.kopaPaid", "keeper" to (world.spots[spot]?.keeper?.name ?: ""), "wood" to amount)
                }
            } else {
                // its wood days are over, unpaid: this pile isn't built (once the smithy stood then)
                val forge = s.buildings.filter { it.type == BuildingType.SMITHY }.minOfOrNull { it.builtAt } ?: continue
                val built = if (forge > 0) Instant.ofEpochMilli(forge).atZone(ZoneId.systemDefault()).toLocalDate() else null
                if (built != null && built.isAfter(until)) continue
                s = s.copy(cycles = s.cycles + (spot to m.copy(unbuilt = (m.unbuilt + r.key).takeLast(si.lanisce.lani.game.scene.CycleMarks.KEEP))))
                news += "🪵" to bi("hearths.kopaUnbuilt", "keeper" to (world.spots[spot]?.keeper?.name ?: ""))
            }
        }
        return s
    }

    /**
     * The charcoal pile's wood as the HUD's sheet shows it on [today]: the round whose wood days are on (paid or waiting), else
     * the next one within [AHEAD] days; null when there is none or the village doesn't give it (no smithy).
     */
    fun kopaWood(s: GameState, today: LocalDate, world: WorldFile = Cultures.current.world): KopaWood? {
        val (spot, c) = kopa(world) ?: return null
        val day = SpotCycles.dayOf(today)
        val rounds = SpotCycles.rounds(c, spot, day.year) + SpotCycles.rounds(c, spot, day.year + 1)
        val r = rounds.firstOrNull { r -> r.woodUntil?.let { !it.isBefore(day) } == true && r.woodFrom?.let { ChronoUnit.DAYS.between(day, it) <= AHEAD } == true }
            ?: return null
        val from = r.woodFrom ?: return null
        val until = r.woodUntil ?: return null
        // a round still ahead counts once the village keeps its hearths: today's, else the first tick's
        val probe = if (s.hearths == null) s.copy(hearths = HearthDay(since = day.toString())) else s
        if (!waits(probe, c, r)) return null
        val m = SpotCycles.marks(s, spot)
        if (r.key in m.unbuilt) return null
        val paid = r.key in m.stacked
        return KopaWood(spot, r.key, Catalog.kopaWood(s.age), from, until, paid, waiting = !paid && !day.isBefore(from), keeper = world.spots[spot]?.keeper?.name)
    }

    /** How far ahead the HUD's sheet shows the charcoal pile's wood (days). */
    const val AHEAD = 7L
}
