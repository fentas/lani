package si.lanisce.lani.game.scene

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.scene.DialogRun
import java.time.LocalDate

/**
 * The Lake District's charcoal pit over the days (world.json `spots.kopa.cycle`; docs/plans/05-built/village-b2-lakeland.md):
 * Collier Ned's talk is the day's at every stage, in the rest and the winter, each variant playing to its end at A1, A2, B1
 * and B2 in every base with a wrong choice to learn from; a learner's level picks the telling; his breakout is a race at
 * every level; his pit goes to Mr Dixon's forge, and the year has its pits.
 */
class LakelandPitTest {
    private val pair = L10n.pair
    private val village = GameState(seed = 42)
    private val pairs = listOf(LangPair(Lang.EN, Lang.SL), LangPair(Lang.EN, Lang.DE), LangPair(Lang.EN, Lang.IT))
    private val levels = listOf("A1", "A2", "B1", "B2")

    @Before fun lakeland() = Cultures.use("lakeland")

    @After fun back() {
        SpotCycles.qaDay = null
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = pair
    }

    private val world get() = Cultures.current.world
    private val cycle get() = world.spots.getValue("kopa").cycle!!

    private fun ned(state: GameState, day: LocalDate, hour: Int = 21): ActiveKeeper {
        SpotCycles.qaDay = day
        return Keepers.here(state, day.atTime(hour, 0), world).single()
    }

    /** [k]'s talk at [level] in [pair] played to its end, every wrong choice first; the number of wrong choices. */
    private fun play(k: ActiveKeeper, level: String, pair: LangPair, what: String): Int {
        val d = Keepers.talk(k, level, "en", pair)
        assertNotNull(what, d)
        var run = DialogRun.start(d!!, k.keeper.id, seed = 5)
        var wrongs = 0
        var steps = 0
        while (run.step != DialogRun.Step.END && steps++ < 60) {
            if (run.step == DialogRun.Step.LISTEN) { run = run.next(); continue }
            val bad = run.choices.indices.firstOrNull { !run.choices[it].ok && it !in run.tried }
            if (bad == null) { run = run.choose(run.choices.indexOfFirst { it.ok }); continue }
            run = run.choose(bad)
            wrongs++
            assertEquals("$what: he reacts", k.keeper.id, run.said.last().who)
        }
        assertEquals(what, DialogRun.Step.END, run.step)
        assertTrue("$what: no placeholder left: ${run.said.map { it.sl }}", run.said.none { '{' in it.sl.orEmpty() || '{' in it.en.orEmpty() })
        return wrongs
    }

    @Test fun `every stage, the rest and the winter, each of his talks plays to its end at every level, with a wrong choice to learn from`() {
        val r = SpotCycles.rounds(cycle, "kopa", 2026).first()
        val days = r.spans.map { it.stage.id to it.from } + listOf("rest" to r.until.plusDays(2), "winter" to LocalDate.of(2026, 12, 20))
        for ((id, day) in days) {
            val shown = SpotCycles.on(cycle, "kopa", day).shown!!
            assertTrue("$id: at least one talk", shown.talks.isNotEmpty())
            for (hour in listOf(20, 23)) for (n in shown.talks.indices) {
                val state = village.copy(keepers = mapOf("spot:kopa/collier" to KeeperTold(1 + n, day.minusDays(1).toString())))
                val k = ned(state, day, hour)
                if (k.breakout) continue
                for (p in pairs) for (level in levels) assertTrue("$id $hour:00 #$n $level: a wrong choice", play(k, level, p, "$id $hour:00 #$n $level ${p.code}") >= 1)
            }
        }
    }

    @Test fun `a learner's level picks its telling`() {
        L10n.pair = pairs.first()
        val r = SpotCycles.rounds(cycle, "kopa", 2026).first()
        val day = r.spans.first { it.stage.id == "white-smoke" }.from.plusDays(1)
        val k = ned(village.copy(keepers = mapOf("spot:kopa/collier" to KeeperTold(1, day.minusDays(1).toString()))), day)
        val told = levels.map { lv -> Keepers.talk(k, lv, "en")!!.lines.mapNotNull { it.sl }.joinToString(" ") }
        assertEquals("four tellings: $told", 4, told.toSet().size)
    }

    @Test fun `the night his pit breaks out is a race at every level, with its ends`() {
        val b = cycle.breakout!!
        assertEquals(setOf("A1", "A2", "B1", "B2"), b.seconds.keys)
        val night = (2026..2030).asSequence().flatMap { SpotCycles.rounds(cycle, "kopa", it).asSequence() }.firstNotNullOf { it.breakout }
        val k = ned(village.copy(keepers = mapOf("spot:kopa/collier" to KeeperTold(3, night.minusDays(1).toString()))), night, 23)
        assertTrue("breakout on $night", k.breakout)
        for (level in levels) {
            val race = Keepers.race(k, level)
            assertNotNull(level, race)
            assertEquals(level, b.seconds[level], race!!.seconds)
            assertTrue(level, race.saved.isNotEmpty() && race.lost.isNotEmpty())
        }
    }

    @Test fun `the pit goes to Mr Dixon's forge, and the year has its pits`() {
        assertEquals("dixon", cycle.delivery!!.to)
        for (year in 2026..2035) {
            val rs = SpotCycles.rounds(cycle, "kopa", year)
            assertTrue("$year: ${rs.size} pits", rs.size in 3..7)
            assertTrue(rs.all { it.from.monthValue in 4..10 && it.until.monthValue <= 10 })
        }
    }

    @Test fun `his own story and his meeting are told at every level`() {
        val k = world.spots.getValue("kopa").keeper!!
        assertEquals(6, k.tales.size)
        assertTrue(k.tales.all { it.levels.keys.containsAll(levels) })
        assertTrue(k.levels.keys.containsAll(levels))
    }
}
