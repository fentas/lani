package si.lanisce.lani.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.parseVillagers
import java.io.File
import kotlin.random.Random

/**
 * "The learner can never block their own progress" (companion/GAME.md "Buildings", "When every plot is built"): random
 * build orders in every culture, from the campfire to the town, each age's plots filled as the order goes, and then the
 * next age's buildings reached the way the app offers (what can be built; a bed made for whom a workplace brings; and,
 * with every plot built, the building's way through the plots: a dwelling grown, a building not needed replaced).
 *
 * The resources and words aren't the question here (practice brings them; SimulationTest paces them): the stores are
 * full and only the buildings, the plots, the beds, the age's rules and the protections count. Every bed is taken (the
 * worst case for a workplace's person), and whom a building brings lives here once it stands.
 *
 * Two players: one builds at random among what it can, the other fills the plots with what the next age doesn't need
 * first (tents in every slot, as Teo did). With the rules before growing and replacing (the plot way off, nothing
 * grows or is replaced) the "filler" walls itself in on its way to Mesto; with them, no order does.
 */
class BuildOrdersTest {
    @After fun back() { Cultures.use(Cultures.DEFAULT) }

    private val cultures = listOf("primorska", "friuli", "kaernten", "lakeland")

    /** The cast of [culture], from its pack's files (as the node serves them). */
    private fun cast(culture: String): List<Villager> {
        val dir = File("../../cultures/$culture/villagers")
        check(dir.isDirectory) { "no villagers in ${dir.absolutePath}" }
        return dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
            .map { parseVillagers("[" + it.readText() + "]").single() }
            .sortedWith(compareBy({ it.order }, { it.id }))
    }

    private enum class Rules { BEFORE, AFTER }
    private enum class Player { RANDOM, FILLER }

    /** One order's end: the age reached, why it stopped short (a dead end) or null, what grew and was replaced; [pond]: the tent at the pond still a tent there (null: none moved). */
    private data class Run(val reached: Age, val dead: String?, val grown: Int, val replaced: Int, val pond: Boolean? = null)

    private class Village(var s: GameState, val cast: List<Villager>, val r: Random) {
        var now = 1_000L
        var grown = 0
        var replaced = 0
        /** The tent the shepherd moved to the pond, once he did. */
        var pond: String? = null

        /** Full stores, every bed taken, and whom a standing building brings living here. */
        fun settle() {
            var st = s.copy(resources = Res.entries.associateWith { 1_000_000 })
            for (t in st.buildings.map { it.type }.distinct()) {
                val v = Residents.brings(t, cast) ?: continue
                if (st.residents.none { it.id == v.id }) st = st.copy(residents = st.residents + Resident(v.id, "2026-01-01"))
            }
            val cap = GameEngine.attributes(st).populationCap
            s = st.copy(villagers = maxOf(cap, st.residents.size))
        }

        fun did(n: GameState): Boolean {
            // the engine hands the same state back when nothing could be done
            if (n === s) return false
            s = n
            now += 1_000
            settle()
            return true
        }
    }

    /** What the next age needs that isn't standing yet: the first group short of its count. */
    private fun shortGroup(s: GameState): List<BuildingType>? {
        val next = Age.entries.getOrNull(s.age.ordinal + 1) ?: return null
        return Catalog.ageRules.getValue(next).needs.firstOrNull { (types, n) -> s.buildings.count { it.type in types && !it.damaged } < n }?.first
    }

    /**
     * The next age's buildings reached the way the app offers them; null when they are, else why not (a dead end: the
     * building wanted and what stands).
     */
    private fun reach(v: Village, rules: Rules): String? {
        repeat(80) {
            val types = shortGroup(v.s) ?: return null
            val opts = GameEngine.buildOptions(v.s, v.cast).filter { it.type in types }
            opts.firstOrNull { it.available }?.let { o ->
                val free = Placement.freePlots(v.s)
                check(v.did(GameEngine.build(v.s, o.type, v.now, cast = v.cast, plot = if (o.type.onPlot) free.random(v.r) else null))) { "${o.type} offered, not built" }
                return@repeat
            }
            opts.firstNotNullOfOrNull { it.room }?.let { w -> if (room(v, w)) return@repeat }
            if (rules == Rules.AFTER) opts.firstNotNullOfOrNull { o -> o.plotWay?.let { o.type to it } }?.let { (type, way) ->
                if (plotWay(v, type, way)) return@repeat
            }
            return "${v.s.age}→${types.joinToString("/")}: " + opts.joinToString { "${it.type} (${it.reason})" } +
                " | " + v.s.buildings.filter { it.type.onPlot }.sortedBy { it.plot }.joinToString(" ") { "${it.plot}:${it.type}${it.level}" }
        }
        return "${v.s.age}: no end"
    }

    /** A bed made for whom a workplace brings: a dwelling built on a free plot, or one upgraded. */
    private fun room(v: Village, w: RoomWay): Boolean =
        if (w.upgrade != null) v.did(GameEngine.upgrade(v.s, w.upgrade, v.now))
        else v.did(GameEngine.build(v.s, w.type, v.now, cast = v.cast))

    /** The building's way through the plots: the dwelling grows, or the building not needed makes room (its own bed first). */
    private fun plotWay(v: Village, type: BuildingType, way: PlotWay): Boolean = when (way) {
        is PlotWay.Grow -> v.did(Rebuild.grow(v.s, way.building, v.now)).also { if (it) v.grown++ }
        is PlotWay.Replace -> {
            val o = Rebuild.replaceOptions(v.s, way.building, v.cast).firstOrNull { it.type == type }
            when {
                o == null -> false
                o.available -> v.did(Rebuild.replace(v.s, way.building, type, v.now, cast = v.cast)).also { if (it) v.replaced++ }
                o.room != null -> room(v, o.room)
                else -> false
            }
        }
    }

    /** One age's play: what the player builds (and grows, replaces, upgrades, moves) before it goes on. */
    private fun play(v: Village, rules: Rules, player: Player) {
        repeat(60) {
            val opts = GameEngine.buildOptions(v.s, v.cast).filter { it.available }
            val needs = Age.entries.getOrNull(v.s.age.ordinal + 1)?.let { Catalog.ageRules.getValue(it).needs.flatMap { n -> n.first } }.orEmpty().toSet()
            val r = v.r.nextFloat()
            when {
                // now and then a level up, or a move: neither changes what stands where it counts
                r < 0.08f -> v.s.buildings.filter { GameEngine.upgradeOption(v.s, it.id)?.available == true }.randomOrNull(v.r)?.let { v.did(GameEngine.upgrade(v.s, it.id, v.now)) }
                r < 0.12f -> v.s.buildings.filter { it.type.onPlot && it.plot >= 0 }.randomOrNull(v.r)?.let { b ->
                    PlotChoice.move(v.s, b).let { c -> (c.free(v.s) + c.swaps(v.s)).randomOrNull(v.r)?.let { p -> v.did(GameEngine.move(v.s, b.id, p, v.now)) } }
                }
                // with the new rules the player also grows and replaces as it likes
                rules == Rules.AFTER && r < 0.18f -> v.s.buildings.mapNotNull { Rebuild.growOption(v.s, it.id) }.filter { it.available }.randomOrNull(v.r)?.let {
                    if (v.did(Rebuild.grow(v.s, it.building, v.now))) v.grown++
                }
                rules == Rules.AFTER && r < 0.24f -> v.s.buildings.filter { Rebuild.replaceable(v.s, it.id, v.cast) }.randomOrNull(v.r)?.let { b ->
                    Rebuild.replaceOptions(v.s, b.id, v.cast).filter { it.available }.randomOrNull(v.r)?.let { o ->
                        if (v.did(Rebuild.replace(v.s, b.id, o.type, v.now, cast = v.cast))) v.replaced++
                    }
                }
                // and takes one down now and then (its beds the village can do without)
                rules == Rules.AFTER && r < 0.26f -> v.s.buildings.filter { Rebuild.canTakeDown(v.s, it.id, v.cast) }.randomOrNull(v.r)?.let {
                    v.did(Rebuild.takeDown(v.s, it.id, v.now, v.cast))
                }
                opts.isEmpty() -> return
                else -> {
                    val o = when (player) {
                        Player.RANDOM -> opts.random(v.r)
                        // what the next age doesn't need first, the dwellings and the fields most of all
                        Player.FILLER -> (opts.filter { it.type !in needs && it.type in Rebuild.REPLACEABLE }.ifEmpty { opts.filter { it.type !in needs } }.ifEmpty { opts }).random(v.r)
                    }
                    val free = Placement.freePlots(v.s)
                    v.did(GameEngine.build(v.s, o.type, v.now, cast = v.cast, plot = if (o.type.onPlot) free.random(v.r) else null))
                    // the random player stops now and then: an age goes on with plots still free
                    if (player == Player.RANDOM && v.r.nextFloat() < 0.1f) return
                }
            }
        }
    }

    /**
     * One order, from the campfire on (or from [start]): each age played, then the next age's buildings reached, until the
     * town or a dead end. The tent the shepherd moved to the pond must stay a tent there, whatever is built.
     */
    private fun order(culture: String, cast: List<Villager>, seed: Long, rules: Rules, player: Player, start: GameState? = null): Run {
        val v = Village(start ?: GameEngine.newGame(seed, 0L), cast, Random(seed * 31 + culture.hashCode() + player.ordinal))
        v.settle()
        fun pondKept() = v.pond?.let { id -> v.s.buildings.any { it.id == id && it.type == BuildingType.TENT && it.plot == NO_PLOT } }
        while (v.s.age < Age.MESTO) {
            play(v, rules, player)
            reach(v, rules)?.let { return Run(v.s.age, it, v.grown, v.replaced, pondKept()) }
            v.did(v.s.copy(age = Age.entries[v.s.age.ordinal + 1]))
            // the shepherd moves the first tent to the pond (most learners do it, some never)
            if (v.pond == null && v.s.age >= Age.ZASELEK && v.r.nextFloat() < 0.6f && v.s.buildings.any { it.type == BuildingType.HUT || it.type == BuildingType.HOUSE }) {
                val tent = TentMove.tent(v.s)
                if (tent != null && v.did(TentMove.move(v.s, v.now))) v.pond = tent.id
            }
        }
        return Run(v.s.age, null, v.grown, v.replaced, pondKept())
    }

    private data class Tally(val orders: Int, val dead: Int, val byAge: Map<Age, Int>, val grown: Int, val replaced: Int, val example: String?, val pondLost: Int = 0, val ponds: Int = 0)

    private fun tally(rules: Rules, perCulture: Int): Map<Pair<String, Player>, Tally> {
        val out = LinkedHashMap<Pair<String, Player>, Tally>()
        for (c in cultures) {
            Cultures.use(c)
            val cast = cast(c)
            for (p in Player.entries) {
                val runs = (1..perCulture).map { order(c, cast, it.toLong(), rules, p) }
                val dead = runs.filter { it.dead != null }
                out[c to p] = Tally(
                    runs.size, dead.size, dead.groupingBy { it.reached }.eachCount(), runs.sumOf { it.grown }, runs.sumOf { it.replaced },
                    dead.firstOrNull()?.dead, pondLost = runs.count { it.pond == false }, ponds = runs.count { it.pond != null },
                )
            }
        }
        return out
    }

    private fun report(title: String, t: Map<Pair<String, Player>, Tally>): String = "$title\n" + t.entries.joinToString("\n") { (k, v) ->
        "  ${k.first} ${k.second}: ${v.orders} orders, ${v.dead} dead ends ${v.byAge}, grown ${v.grown}, replaced ${v.replaced}, pond tents ${v.ponds} (lost ${v.pondLost})" +
            (v.example?.let { "\n    e.g. $it" } ?: "")
    }

    @Test fun `no build order in any culture walls the village in`() {
        val before = tally(Rules.BEFORE, PER_CULTURE)
        val after = tally(Rules.AFTER, PER_CULTURE)
        println(report("before (no growing, no replacing):", before))
        println(report("after:", after))
        File("build/reports/build-orders").apply { mkdirs() }.resolve("summary.txt").writeText(report("before:", before) + "\n" + report("after:", after) + "\n")
        // the dead end was real: tents and fields in every slot, Mesto's nine buildings and six plots of the market town
        assertTrue("the old rules should wall some orders in", before.values.sumOf { it.dead } > 0)
        // and is gone: every order of every culture reaches the town
        val dead = after.filterValues { it.dead > 0 }
        assertTrue(report("dead ends:", dead), dead.isEmpty())
        assertEquals(cultures.size * Player.entries.size * PER_CULTURE, after.values.sumOf { it.orders })
        // the tent at the pond stays a tent there through every order, whatever grows, is replaced or taken down
        assertTrue("some tents moved to the pond", after.values.sumOf { it.ponds } > 0)
        assertEquals(0, after.values.sumOf { it.pondLost })
    }

    /**
     * Teo's village (Jan, 2026-10-27: "Teo built 3 huts ;D"): Friuli, a camp whose five plots hold the first tent, three huts
     * and a field. Played on at random from there by both players, every order reaches the town, and once houses come
     * (the hamlet) each hut can be rebuilt as a house on its plot, its level kept, for the difference.
     */
    @Test fun `Teo's camp of three huts reaches the town`() {
        Cultures.use("friuli")
        val cast = cast("friuli")
        fun b(id: String, type: BuildingType, plot: Int) = Building(id, type, plot)
        val camp = GameState(
            seed = 11, age = Age.TABOR, villagers = 9,
            buildings = listOf(b("tent", BuildingType.TENT, 0), b("hut-1", BuildingType.HUT, 1), b("hut-2", BuildingType.HUT, 2), b("hut-3", BuildingType.HUT, 3), b("field", BuildingType.FIELD, 4)),
        )
        assertTrue(Placement.freePlots(camp).isEmpty())
        val runs = Player.entries.flatMap { p -> (1..100).map { order("friuli", cast, it.toLong(), Rules.AFTER, p, camp.copy(seed = it.toLong())) } }
        assertEquals(runs.filter { it.dead != null }.map { it.dead }.take(3).toString(), 0, runs.count { it.dead != null })
        // in the hamlet a hut is rebuilt as a house for the difference: 30 🌾 30 🪵 40 🪨 at level 1
        val hamlet = camp.copy(age = Age.ZASELEK, resources = Res.entries.associateWith { 500 })
        val o = Rebuild.growOption(hamlet, "hut-1")!!
        assertEquals(BuildingType.HOUSE, o.to)
        assertEquals(mapOf(Res.FOOD to 30, Res.WOOD to 30, Res.STONE to 40), o.cost)
        val house = Rebuild.grow(hamlet, "hut-1", 1L).buildings.single { it.plot == 1 }
        assertEquals(BuildingType.HOUSE to 1, house.type to house.level)
    }

    private companion object {
        /** Build orders per culture and player. */
        const val PER_CULTURE = 250
    }
}
