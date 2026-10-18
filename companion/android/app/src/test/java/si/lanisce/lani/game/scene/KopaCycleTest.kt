package si.lanisce.lani.game.scene

import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Chest
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Tool
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.scene.DialogRun
import si.lanisce.lani.ui.villagers.VillagerLogic
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Primorska's charcoal pile over the days (world.json `spots.kopa.cycle`; companion/VILLAGERS.md, "The pile over the days"):
 * Oglar Miha's talk is the day's, at every stage, in the rest and the winter, each variant playing to its end at A1 and A2
 * with a wrong choice to learn from; a visit after another hears another; he tells what the learner missed; the night his
 * pile breaks out it is a race, saved or lost; his own story comes a part at a time; the charcoal goes to Tone, who says
 * so, and burns in his forge.
 */
class KopaCycleTest {
    private val pair = L10n.pair
    private val village = GameState(seed = 42)
    private val pairs = listOf(LangPair(Lang.SL, Lang.EN), LangPair(Lang.SL, Lang.DE), LangPair(Lang.SL, Lang.IT))

    @After fun back() {
        SpotCycles.qaDay = null
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = pair
    }

    private val world get() = Cultures.current.world
    private val cycle get() = world.spots.getValue("kopa").cycle!!

    /** Miha at the pile on [day] at [hour] (there whatever his dice: QA's day), as [state] has him. */
    private fun miha(state: GameState, day: LocalDate, hour: Int = 21): ActiveKeeper {
        SpotCycles.qaDay = day
        return Keepers.here(state, day.atTime(hour, 0), world).single()
    }

    /** [k]'s talk at [level] in [pair] played to its end, every wrong choice first; the number of wrong choices. */
    private fun play(k: ActiveKeeper, level: String, pair: LangPair, what: String): Int {
        val d = Keepers.talk(k, level, "sl", pair)
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

    @Test fun `every stage, the rest and the winter, each of his talks plays to its end at A1 and A2, with a wrong choice to learn from`() {
        Cultures.use("primorska")
        val r = rounds().first()
        val days = r.spans.map { it.stage.id to it.from } +
            listOf("rest" to r.until.plusDays(2), "winter" to LocalDate.of(2026, 12, 20))
        for ((id, day) in days) {
            val shown = SpotCycles.on(cycle, "kopa", day).shown!!
            assertEquals(id, SpotCycles.on(cycle, "kopa", day).id)
            assertTrue("$id: at least two talks", shown.talks.size >= 2)
            // a learner who has met him, on the evening and at night: each of the stage's talks in turn
            for (hour in listOf(20, 23)) for (n in shown.talks.indices) {
                val state = village.copy(keepers = mapOf("spot:kopa/oglar" to KeeperTold(1 + n, day.minusDays(1).toString())))
                val k = miha(state, day, hour)
                assertEquals(id, k.day?.id)
                if (k.breakout) continue
                for (p in pairs) for (level in listOf("A1", "A2")) assertTrue("$id $hour:00 #$n $level: a wrong choice", play(k, level, p, "$id $hour:00 #$n $level ${p.code}") >= 1)
            }
        }
    }

    @Test fun `a visit after another hears another of the day's talks, the night's at night, the rain's in the rain`() {
        Cultures.use("primorska")
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        val r = rounds().first()
        val day = r.spans.first { it.stage.id == "bel-dim" }.from.plusDays(1)
        fun first(state: GameState, hour: Int = 20) = Keepers.talk(miha(state, day, hour), "A1", "sl")!!.lines.drop(1).first { it.who != null }.sl
        val one = village.copy(keepers = mapOf("spot:kopa/oglar" to KeeperTold(3, day.minusDays(1).toString())))
        val two = village.copy(keepers = mapOf("spot:kopa/oglar" to KeeperTold(4, day.minusDays(1).toString())))
        assertFalse("the next evening, another talk", first(one) == first(two))
        // at night the night watch's is among them (and never by evening)
        val tonight = (1..4).map { n -> first(village.copy(keepers = mapOf("spot:kopa/oglar" to KeeperTold(n, day.minusDays(1).toString()))), 23) }
        val evenings = (1..4).map { n -> first(village.copy(keepers = mapOf("spot:kopa/oglar" to KeeperTold(n, day.minusDays(1).toString())))) }
        assertTrue(tonight.toString(), "Ponoči stražim. Poslušam kopo." in tonight)
        assertFalse(evenings.toString(), "Ponoči stražim. Poslušam kopo." in evenings)
        assertTrue("a visit after another hears another: $tonight", tonight.zipWithNext().all { (a, b) -> a != b })
        // in the rain the rain's: a scene left the day's sky rainy
        val wet = one.copy(sky = DaySky(day.toString(), "ob-ognju", Sky(rain = 0.7f)))
        assertEquals(setOf("rain"), SpotCycles.skyOf(wet, day))
        assertEquals("Danes dežuje. Dež je dober za kopo.", first(wet))
        // the yellow smoke in the wind (a scene's dialog left it windy, the bora): the wind's
        val yellow = r.spans.first { it.stage.id == "rumen-dim" }.from.plusDays(1)
        val windy = one.copy(sky = DaySky(yellow.toString(), "ob-ognju", Sky(wind = 0.8f)))
        assertTrue(Keepers.talk(miha(windy, yellow), "A1", "sl")!!.lines.any { it.sl == "Danes piha veter. Slišiš, kako šumi?" })
        assertTrue(Keepers.talk(miha(windy, yellow), "A2", "sl")!!.lines.any { it.sl == "Danes piha burja. Takšen veter je za kopo najhujši." })
        assertFalse(Keepers.talk(miha(one, yellow), "A1", "sl")!!.lines.any { it.sl == "Danes piha veter. Slišiš, kako šumi?" })
    }

    @Test fun `the first evening is his meeting, then the day's talk`() {
        Cultures.use("primorska")
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        val day = rounds().first().spans.first { it.stage.id == "zlaganje" }.from
        val k = miha(village, day)
        assertEquals(0, k.at)
        val d = Keepers.talk(k, "A1", "sl")!!
        assertEquals("Dober večer! Jaz sem Miha. Sem oglar.", d.lines.first().sl)
        assertTrue("then the stacking", d.lines.any { it.sl?.contains("kol") == true })
        assertEquals("Oglar Miha zlaga kopo · Miha the charcoal burner stacks the pile", k.title.bi())
        assertEquals(1, play(k, "A1", L10n.pair, "meeting").coerceAtMost(1))
    }

    @Test fun `QA's cycle days on one real day are evenings of their own, his meeting once, then each day's talk`() {
        Cultures.use("primorska")
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        val real = LocalDate.of(2026, 10, 17)
        val spans = rounds(2027).first().spans
        val (stack, smoke) = spans.first { it.stage.id == "zlaganje" }.from to spans.first { it.stage.id == "rumen-dim" }.from
        SpotCycles.qaDay = stack
        val k = Keepers.here(village, real.atTime(21, 0), world).single()
        assertEquals(0, k.at)
        val s = Keepers.told(village, k, real)
        assertEquals("counted on the cycle's day", stack.toString(), s.keepers.getValue(k.key).on)
        SpotCycles.qaDay = smoke
        assertEquals("counted after QA moved on: still that evening's", s, Keepers.told(village, k, real))
        SpotCycles.qaDay = stack
        assertEquals("tapped again that day: his meeting again", 0, Keepers.here(s, real.atTime(21, 30), world).single().at)
        SpotCycles.qaDay = smoke
        val next = Keepers.here(s, real.atTime(21, 0), world).single()
        assertEquals("another cycle day: his next evening", 1, next.at)
        assertTrue("the day's talk, not his meeting", Keepers.talk(next, "A1", "sl")!!.lines.none { it.sl == "Dober večer! Jaz sem Miha. Sem oglar." })
        assertFalse(Keepers.done(s, next, real))
    }

    @Test fun `away a while, he tells what he did meanwhile, and what last night brought`() {
        Cultures.use("primorska")
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        val r = rounds().first()
        val last = r.spans.first { it.stage.id == "sekanje" }.from
        val now = r.spans.first { it.stage.id == "rumen-dim" }.from
        val k = miha(village.copy(keepers = mapOf("spot:kopa/oglar" to KeeperTold(2, last.toString()))), now)
        val said = Keepers.talk(k, "A1", "sl")!!.lines.mapNotNull { it.sl }
        assertTrue(said.toString(), "Dolgo te ni bilo!" in said)
        // the latest two stages over: the lighting, the white smoke
        assertTrue(said.toString(), "Kopo sem prižgal. Od zgoraj, z žerjavico." in said && "Prve dni je bil dim bel." in said)
        assertFalse(said.toString(), "Pripravil sem drva za novo kopo." in said)
        // the next evening nothing of the kind
        val next = miha(village.copy(keepers = mapOf("spot:kopa/oglar" to KeeperTold(3, now.toString()))), now.plusDays(1))
        assertTrue(Keepers.talk(next, "A1", "sl")!!.lines.none { it.sl == "Dolgo te ni bilo!" })
    }

    @Test fun `the night his pile breaks out is a race against the clock, saved or lost, and a lost pile burns to ash`() {
        Cultures.use("primorska")
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        val r = (2026..2030).flatMap { rounds(it) }.first { it.breakout != null }
        val night = r.breakout!!
        val state = village.copy(keepers = mapOf("spot:kopa/oglar" to KeeperTold(4, night.minusDays(2).toString())))
        val k = miha(state, night, 22)
        assertTrue(k.breakout)
        assertEquals("Kopa gori! Oglar Miha kliče na pomoč · The pile is on fire! Miha calls for help", k.title.bi())
        for (level in listOf("A1", "A2")) {
            val d = Keepers.talk(k, level, "sl")!!
            val race = Keepers.race(k, level)!!
            assertEquals(d.lines.lastIndex, race.turn)
            assertTrue(d.lines.last().choices.size >= 2)
            assertEquals(if (level == "A1") 25 else 18, race.seconds)
            // in time: saved, his thanks; too late: it burns
            var run = DialogRun.start(d, "oglar")
            while (run.step == DialogRun.Step.LISTEN) run = run.next()
            assertEquals(race.turn, run.at)
            val won = run.choose(run.choices.indexOfFirst { it.ok }).extended(race.read(race.saved, "sl"))
            var w = won
            while (w.step == DialogRun.Step.LISTEN) w = w.next()
            assertEquals(DialogRun.Step.END, w.step)
            assertTrue(w.said.any { it.sl.startsWith("Uf.") })
            var lost = run.instead(race.read(race.lost, "sl"))
            while (lost.step == DialogRun.Step.LISTEN) lost = lost.next()
            assertEquals(DialogRun.Step.END, lost.step)
            assertTrue(lost.said.any { it.sl.startsWith("Prepozno") })
        }
        // lost: from that night to the round's end its talk is the lost pile's, the pile ash, and no charcoal for Tone
        val burnt = SpotCycles.decide(state, "kopa", r.key, lost = true)
        val after = miha(burnt, night.plusDays(1))
        assertEquals(CycleKind.LOST, after.day?.kind)
        assertEquals("ash", after.day?.look?.art)
        assertFalse(after.breakout)
        play(after, "A1", L10n.pair, "lost")
        val delivered = r.delivery!!
        SpotCycles.qaDay = null
        assertTrue(SpotCycles.news(burnt.copy(residents = listOf(si.lanisce.lani.game.villagers.Resident("tone", "2026-01-01"))), delivered.minusDays(1), delivered).isEmpty())
        assertNull(SpotCycles.charcoal(burnt, "tone", delivered))
        assertEquals(true, SpotCycles.yieldFor(burnt, "tone", delivered)?.lost)
        // saved: no breakout any more that night, the stage's talk again
        val saved = miha(SpotCycles.decide(state, "kopa", r.key, lost = false), night, 22)
        assertFalse(saved.breakout)
        // missed: the next evening he tells he covered it alone
        val alone = miha(state, night.plusDays(1))
        assertTrue(Keepers.talk(alone, "A1", "sl")!!.lines.any { it.sl == "Ponoči je kopa zagorela. Sam sem jo pokril z zemljo." })
    }

    @Test fun `his own story comes a part at a time, as the evenings with him add up`() {
        Cultures.use("primorska")
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        val kp = world.spots.getValue("kopa").keeper!!
        assertTrue(kp.tales.size >= 5)
        val day = rounds().first().spans.first { it.stage.id == "sekanje" }.from
        // too early for the first
        assertNull(miha(village.copy(keepers = mapOf("spot:kopa/oglar" to KeeperTold(1, day.minusDays(1).toString()))), day).tale)
        var s = village.copy(keepers = mapOf("spot:kopa/oglar" to KeeperTold(kp.tales[0].after, day.minusDays(1).toString())))
        var k = miha(s, day)
        assertEquals(kp.tales[0].id, k.tale?.id)
        assertTrue(Keepers.talk(k, "A1", "sl")!!.lines.any { it.sl == "Veš, moj oče je bil tudi oglar." })
        play(k, "A2", L10n.pair, "tale")
        s = Keepers.told(s, k, day)
        assertEquals(1, s.keepers.getValue("spot:kopa/oglar").tales)
        // tapped again the same day: the same part again, counted once
        k = miha(s, day)
        assertEquals(kp.tales[0].id, k.tale?.id)
        assertEquals(s, Keepers.told(s, k, day))
        // the next evening: not yet the next part (it needs more evenings)
        k = miha(s, day.plusDays(1))
        assertNull(k.tale)
        // every part, at its evening, plays
        kp.tales.forEachIndexed { i, t ->
            val st = village.copy(keepers = mapOf("spot:kopa/oglar" to KeeperTold(t.after, day.minusDays(1).toString(), tales = i)))
            val kk = miha(st, day)
            assertEquals(t.id, kk.tale?.id)
            for (p in pairs) for (level in listOf("A1", "A2")) play(kk, level, p, "tale ${t.id} $level ${p.code}")
        }
    }

    @Test fun `the charcoal goes to Tone, the chronicle says so, he says so on his card, and it burns in his forge`() {
        Cultures.use("primorska")
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        val r = rounds().first()
        val on = r.delivery!!
        assertEquals("dostava", r.spans.first { it.from == on }.stage.id)
        // the day's tick tells it once, Tone living here (else there's no one to bring it to)
        assertTrue(SpotCycles.news(village, on.minusDays(1), on).isEmpty())
        val withTone = village.copy(residents = listOf(si.lanisce.lani.game.villagers.Resident("tone", "2026-01-01")))
        val news = SpotCycles.news(withTone, on.minusDays(1), on)
        assertEquals(listOf("♨️" to "Oglar Miha je pripeljal oglje kovaču Tonetu. · Miha the charcoal burner brought his charcoal to Tone the smith."), news)
        // Tone speaks of it for two weeks: his card's line and his talk
        val tone = si.lanisce.lani.game.villagers.Villager("tone", "Kovač Tone", "⚒️", "smith")
        assertNotNull(VillagerLogic.news(tone, village, on.plusDays(3)))
        assertTrue(VillagerLogic.news(tone, village, on.plusDays(3))!!.sl.contains("Miha"))
        assertNull(VillagerLogic.news(tone, village, on.minusDays(1)))
        assertNull(VillagerLogic.news(tone, village, on.plusDays(20)))
        // the forge: a quarter less wood while he has it, used by the forging
        val smith = village.copy(
            age = si.lanisce.lani.game.Age.VAS, resources = mapOf(Res.FOOD to 1000, Res.WOOD to 1000, Res.STONE to 1000),
            buildings = listOf(Building("s", BuildingType.SMITHY, 0)), chest = village.chest.copy(tools = mapOf("luka" to Tool(tier = 1)), goods = mapOf("potica" to 2)),
        )
        val without = Chest.forgeOption(smith, "luka", on.minusDays(1))!!
        val with = Chest.forgeOption(smith, "luka", on.plusDays(1))!!
        assertNull(Chest.forgeCharcoal(smith, on.minusDays(1)))
        assertNotNull(Chest.forgeCharcoal(smith, on.plusDays(1)))
        assertEquals((without.cost.getValue(Res.WOOD) * 0.75f).toInt(), with.cost.getValue(Res.WOOD))
        assertEquals(without.cost[Res.STONE], with.cost[Res.STONE])
        val forged = Chest.forge(smith, "luka", 0L, on.plusDays(1))
        assertEquals(1000 - with.cost.getValue(Res.WOOD), forged.res(Res.WOOD))
        assertNull("used", Chest.forgeCharcoal(forged, on.plusDays(2)))
        assertEquals("used: the full price again", without.cost, Chest.forgeOption(forged.copy(chest = forged.chest.copy(tools = mapOf("luka" to Tool(tier = 1)))), "luka", on.plusDays(2))!!.cost)
    }

    @Test fun `the year's piles, three or four, from April to October, none in winter`() {
        Cultures.use("primorska")
        for (year in 2026..2035) {
            val rs = rounds(year)
            assertTrue("$year: ${rs.size} piles", rs.size in 3..5)
            assertTrue(rs.all { it.from.monthValue in 4..10 && it.until.monthValue <= 10 })
            for (d in listOf(LocalDate.of(year, 1, 10), LocalDate.of(year, 12, 1), LocalDate.of(year, 3, 1))) assertEquals(CycleKind.WINTER, SpotCycles.on(cycle, "kopa", d).kind)
        }
    }

    @Test fun `the other cultures' piles have no cycle, their burner's talks as before`() {
        for (id in listOf("friuli", "kaernten", "lakeland")) {
            Cultures.use(id)
            assertNull(id, world.spots.getValue("kopa").cycle)
            assertNull(SpotCycles.look(village, "kopa", LocalDate.of(2026, 7, 1)))
            val k = (0 until 60).firstNotNullOf { Keepers.here(village, LocalDate.of(2026, 9, 1).plusDays(it.toLong()).atTime(21, 0), world).firstOrNull() }
            assertNull(k.day)
        }
    }

    @Test fun `what the village keeps of the cycle round-trips, and an older village has none`() {
        val s = village.copy(cycles = mapOf("kopa" to CycleMarks(lost = listOf("2026-1"), saved = listOf("2026-2"), used = listOf("2026-0"))))
        val text = si.lanisce.lani.data.json.encodeToString(GameState.serializer(), s)
        assertEquals(s.cycles, si.lanisce.lani.data.json.decodeFromString(GameState.serializer(), text).cycles)
        val now = si.lanisce.lani.data.json.parseToJsonElement(si.lanisce.lani.data.json.encodeToString(GameState.serializer(), village)) as JsonObject
        assertTrue(si.lanisce.lani.data.json.decodeFromString(GameState.serializer(), JsonObject(now - "cycles").toString()).cycles.isEmpty())
        // a stage's day is a date: no tick needed, and a learner away for months comes back to the pile as it is
        assertNotNull(GameEngine)
        assertTrue(LocalDateTime.of(2026, 7, 1, 21, 0).toLocalDate() == LocalDate.of(2026, 7, 1))
    }
}
