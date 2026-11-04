package si.lanisce.lani.game.scene

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.scene.DialogRun
import si.lanisce.lani.ui.villagers.VillagerLogic
import java.time.LocalDate

/**
 * Friuli's charcoal pile over the days (world.json `spots.kopa.cycle`, village-b2-friuli; companion/VILLAGERS.md, "The pile
 * over the days"): Carbonaio Pieri's talk is the day's at every stage, in the rest and the winter, each variant playing to
 * its end at A1, A2, B1 and B2 with a wrong choice to learn from, in Teo's pair (Italian from German) and the other bases;
 * his meeting the first evening; the night his pile breaks out a race whose clock is shorter the higher the level; his own
 * story a part at a time; the charcoal goes to Fabbro Bruno, who says so.
 */
class FriuliKopaCycleTest {
    private val pair = L10n.pair
    private val village = GameState(seed = 42)
    private val pairs = listOf(LangPair(Lang.IT, Lang.DE), LangPair(Lang.IT, Lang.SL), LangPair(Lang.IT, Lang.EN))
    private val levels = listOf("A1", "A2", "B1", "B2")
    private val key = "spot:kopa/carbonaio"

    @After fun back() {
        SpotCycles.qaDay = null
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = pair
    }

    private val world get() = Cultures.current.world
    private val cycle get() = world.spots.getValue("kopa").cycle!!

    /** Pieri at the pile on [day] at [hour] (there whatever his dice: QA's day), as [state] has him. */
    private fun pieri(state: GameState, day: LocalDate, hour: Int = 21): ActiveKeeper {
        SpotCycles.qaDay = day
        return Keepers.here(state, day.atTime(hour, 0), world).single()
    }

    /** [k]'s talk at [level] in [pair] played to its end, every wrong choice first; the number of wrong choices. */
    private fun play(k: ActiveKeeper, level: String, pair: LangPair, what: String): Int {
        val d = Keepers.talk(k, level, "it", pair)
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
        assertTrue("$what: his lines first, meant in the base", run.said.first().who == k.keeper.id && run.said.first().en.isNotBlank())
        assertTrue("$what: no placeholder left: ${run.said.map { it.sl }}", run.said.none { '{' in it.sl || '{' in it.en })
        return wrongs
    }

    private fun rounds(year: Int = 2026) = SpotCycles.rounds(cycle, "kopa", year)

    @Test fun `every stage, the rest and the winter, each of his talks plays to its end at four levels, with a wrong choice to learn from`() {
        Cultures.use("friuli")
        val r = rounds().first()
        val days = r.spans.map { it.stage.id to it.from } + listOf("rest" to r.until.plusDays(2), "winter" to LocalDate.of(2026, 12, 20))
        assertEquals("ten stages", 10, r.spans.map { it.stage.id }.distinct().size)
        for ((id, day) in days) {
            val shown = SpotCycles.on(cycle, "kopa", day).shown!!
            assertEquals(id, SpotCycles.on(cycle, "kopa", day).id)
            assertTrue("$id: at least two talks", shown.talks.size >= 2)
            for (hour in listOf(20, 23)) for (n in shown.talks.indices) {
                val state = village.copy(keepers = mapOf(key to KeeperTold(1 + n, day.minusDays(1).toString())))
                val k = pieri(state, day, hour)
                assertEquals(id, k.day?.id)
                if (k.breakout) continue
                for (p in pairs) for (level in levels) assertTrue("$id $hour:00 #$n $level ${p.code}: a wrong choice", play(k, level, p, "$id $hour:00 #$n $level ${p.code}") >= 1)
            }
        }
    }

    @Test fun `the first evening is his meeting, at the learner's level`() {
        Cultures.use("friuli")
        L10n.pair = LangPair(Lang.IT, Lang.DE)
        val day = rounds().first().spans.first { it.stage.id == "catasta" }.from
        val k = pieri(village, day)
        assertEquals(0, k.at)
        assertEquals("Buonasera! Io sono Pieri, il carbonaio.", Keepers.talk(k, "A1", "it")!!.lines.first().sl)
        assertTrue(Keepers.talk(k, "B2", "it")!!.lines.first().sl!!.startsWith("Buonasera! Scusa se non mi alzo"))
        assertEquals("a learner above B2 hears B2's", Keepers.talk(k, "B2", "it")!!.lines.first().sl, Keepers.talk(k, "C1", "it")!!.lines.first().sl)
        assertEquals("Il carbonaio Pieri alza la carbonaia · Köhler Pieri setzt den Meiler", k.title.bi())
        for (level in levels) play(k, level, L10n.pair, "meeting $level")
    }

    @Test fun `the night his pile breaks out is a race, shorter the higher the level, saved or lost`() {
        Cultures.use("friuli")
        L10n.pair = LangPair(Lang.IT, Lang.DE)
        val r = (2026..2030).flatMap { rounds(it) }.first { it.breakout != null }
        val night = r.breakout!!
        val state = village.copy(keepers = mapOf(key to KeeperTold(4, night.minusDays(2).toString())))
        val k = pieri(state, night, 22)
        assertTrue(k.breakout)
        assertEquals("La carbonaia brucia! Il carbonaio Pieri chiama aiuto · Der Meiler brennt! Köhler Pieri ruft um Hilfe", k.title.bi())
        val seconds = mapOf("A1" to 25, "A2" to 18, "B1" to 15, "B2" to 12)
        for (level in levels) {
            val d = Keepers.talk(k, level, "it")!!
            val race = Keepers.race(k, level)!!
            assertEquals(d.lines.lastIndex, race.turn)
            assertEquals(level, seconds.getValue(level), race.seconds)
            var run = DialogRun.start(d, "carbonaio")
            while (run.step == DialogRun.Step.LISTEN) run = run.next()
            assertEquals(race.turn, run.at)
            var w = run.choose(run.choices.indexOfFirst { it.ok }).extended(race.read(race.saved, "it"))
            while (w.step == DialogRun.Step.LISTEN) w = w.next()
            assertEquals(DialogRun.Step.END, w.step)
            assertTrue(level, w.said.any { it.sl.startsWith("Uff") })
            var lost = run.instead(race.read(race.lost, "it"))
            while (lost.step == DialogRun.Step.LISTEN) lost = lost.next()
            assertEquals(DialogRun.Step.END, lost.step)
            assertTrue(level, lost.said.any { "tardi" in it.sl })
        }
        // lost: the pile is ash to the round's end, and Bruno gets no charcoal from it
        val burnt = SpotCycles.decide(state, "kopa", r.key, lost = true)
        val after = pieri(burnt, night.plusDays(1))
        assertEquals(CycleKind.LOST, after.day?.kind)
        assertEquals("ash", after.day?.look?.art)
        for (level in levels) play(after, level, L10n.pair, "lost $level")
        SpotCycles.qaDay = null
        assertNull(SpotCycles.charcoal(burnt, "bruno", r.delivery!!))
    }

    @Test fun `his own story comes a part at a time, at every level`() {
        Cultures.use("friuli")
        L10n.pair = LangPair(Lang.IT, Lang.DE)
        val kp = world.spots.getValue("kopa").keeper!!
        assertEquals(6, kp.tales.size)
        val day = rounds().first().spans.first { it.stage.id == "taglio" }.from
        assertNull(pieri(village.copy(keepers = mapOf(key to KeeperTold(1, day.minusDays(1).toString()))), day).tale)
        kp.tales.forEachIndexed { i, t ->
            val st = village.copy(keepers = mapOf(key to KeeperTold(t.after, day.minusDays(1).toString(), tales = i)))
            val k = pieri(st, day)
            assertEquals(t.id, k.tale?.id)
            for (p in pairs) for (level in levels) play(k, level, p, "tale ${t.id} $level ${p.code}")
        }
    }

    @Test fun `the charcoal goes to Bruno, the chronicle says so, and he says so on his card`() {
        Cultures.use("friuli")
        L10n.pair = LangPair(Lang.IT, Lang.DE)
        val r = rounds().first()
        val on = r.delivery!!
        assertEquals("sacchi", r.spans.first { it.from == on }.stage.id)
        assertTrue(SpotCycles.news(village, on.minusDays(1), on).isEmpty())
        val withBruno = village.copy(residents = listOf(si.lanisce.lani.game.villagers.Resident("bruno", "2026-01-01")))
        val news = SpotCycles.news(withBruno, on.minusDays(1), on)
        assertEquals(1, news.size)
        assertTrue(news.toString(), news.single().second.startsWith("Il carbonaio Pieri ha portato il carbone al fabbro Bruno."))
        val bruno = si.lanisce.lani.game.villagers.Villager("bruno", "Fabbro Bruno", "⚒️", "smith")
        assertTrue(VillagerLogic.news(bruno, village, on.plusDays(3))!!.sl.contains("Pieri"))
        assertNull(VillagerLogic.news(bruno, village, on.plusDays(20)))
    }

    @Test fun `the year's piles, three to five, from April to October, none in winter, on days of their own`() {
        Cultures.use("friuli")
        for (year in 2026..2035) {
            val rs = rounds(year)
            assertTrue("$year: ${rs.size} piles", rs.size in 3..5)
            assertTrue(rs.all { it.from.monthValue in 4..10 && it.until.monthValue <= 10 })
            assertEquals(CycleKind.WINTER, SpotCycles.on(cycle, "kopa", LocalDate.of(year, 1, 10)).kind)
        }
        // its own seed: Friuli's piles aren't Primorska's to the day
        val friuli = rounds(2027).map { it.from }
        Cultures.use("primorska")
        assertFalse(friuli == rounds(2027).map { it.from })
    }
}
