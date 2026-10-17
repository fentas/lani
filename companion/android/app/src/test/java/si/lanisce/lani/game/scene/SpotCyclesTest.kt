package si.lanisce.lani.game.scene

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.culture.SpotCycle
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A spot's cycle over real days (companion/GAME.md, "A spot's cycle"): its rounds follow from the date alone, each stage as
 * long as its range allows, within the season, a pause between rounds, nothing in the winter; the same days as the bridge
 * gives them (companion/bridge/test/fixtures/spot-cycle.json, made by the bridge's spot-cycles.ts), and the same dice.
 */
class SpotCyclesTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory }
    private val fixture: JsonObject by lazy { Json.parseToJsonElement(File(companion, "bridge/test/fixtures/spot-cycle.json").readText()).jsonObject }
    private val cycle: SpotCycle by lazy { Json { ignoreUnknownKeys = false }.decodeFromJsonElement(SpotCycle.serializer(), fixture.getValue("cycle")) }
    private val spot get() = fixture.getValue("spot").jsonPrimitive.content

    @Test fun `the dice are the bridge's`() {
        for (h in fixture.getValue("hashes").jsonArray) {
            val o = h.jsonObject
            val parts = o.getValue("parts").jsonArray.map<kotlinx.serialization.json.JsonElement, Any> { p -> (p as JsonPrimitive).let { if (it.isString) it.content else it.int } }
            assertEquals("$parts", o.getValue("hash").jsonPrimitive.long, SpotCycles.hash(*parts.toTypedArray()))
        }
    }

    @Test fun `its rounds, their breakouts and deliveries are the bridge's, to the day`() {
        val years = fixture.getValue("rounds").jsonArray
        for ((i, year) in listOf(2026, 2027).withIndex()) {
            val want = years[i].jsonArray.map { it.jsonObject }
            val got = SpotCycles.rounds(cycle, spot, year)
            assertEquals("$year", want.map { it.getValue("key").jsonPrimitive.content }, got.map { it.key })
            for ((w, r) in want.zip(got)) {
                assertEquals(r.key, w.getValue("from").jsonPrimitive.content, r.from.toString())
                assertEquals(r.key, w.getValue("until").jsonPrimitive.content, r.until.toString())
                assertEquals(r.key, w.getValue("breakout").jsonPrimitive.contentOrNull, r.breakout?.toString())
                assertEquals(r.key, w.getValue("delivery").jsonPrimitive.contentOrNull, r.delivery?.toString())
            }
        }
    }

    @Test fun `every day of the year is the stage, the rest or the winter the bridge says`() {
        fun check(list: JsonArray, marks: CycleMarks) {
            for (d in list) {
                val o = d.jsonObject
                val date = LocalDate.parse(o.getValue("date").jsonPrimitive.content)
                val got = SpotCycles.on(cycle, spot, date, marks)
                assertEquals("$date", o.getValue("kind").jsonPrimitive.content, got.kind.name.lowercase())
                assertEquals("$date", o.getValue("id").jsonPrimitive.content, got.id)
                assertEquals("$date", o.getValue("day").jsonPrimitive.int, got.day)
                assertEquals("$date", o.getValue("days").jsonPrimitive.int, got.days)
                assertEquals("$date", o.getValue("round").jsonPrimitive.contentOrNull, got.round?.key)
            }
        }
        check(fixture.getValue("days").jsonArray, CycleMarks())
        val lost = fixture.getValue("marks").jsonObject.getValue("lost").jsonArray.map { it.jsonPrimitive.content }
        check(fixture.getValue("lost").jsonArray, CycleMarks(lost = lost))
    }

    @Test fun `a round's stages are as long as their ranges, within the season, a pause between rounds`() {
        for (year in 2026..2031) {
            val rounds = SpotCycles.rounds(cycle, spot, year)
            assertTrue("$year: ${rounds.size} rounds", rounds.size in 3..6)
            val (from, until) = SpotCycles.season(cycle, year)!!
            assertTrue("$year starts within its start range", ChronoUnit.DAYS.between(from, rounds.first().from) in 0L..9L)
            assertFalse("$year ends in the season", rounds.last().until.isAfter(until))
            for (r in rounds) {
                assertEquals(cycle.stages.map { it.id }, r.spans.map { it.stage.id })
                r.spans.zipWithNext().forEach { (a, b) -> assertEquals("${r.key}: one stage after another", a.until.plusDays(1), b.from) }
                for (s in r.spans) assertTrue("${r.key} ${s.stage.id}: ${s.days}", s.days in s.stage.days[0]..s.stage.days[1])
                // the breakout's night is a night of its stages
                r.breakout?.let { b -> assertTrue(r.spans.first { b in it }.stage.id in cycle.breakout!!.stages) }
                assertEquals(r.spans.first { it.stage.delivers }.from, r.delivery)
            }
            rounds.zipWithNext().forEach { (a, b) -> assertTrue("$year: pause ${a.key}", ChronoUnit.DAYS.between(a.until, b.from) - 1 in 5L..12L) }
            // the same days asked again
            assertEquals(rounds, SpotCycles.rounds(cycle, spot, year))
        }
        // another seed, another year's pile
        assertTrue(SpotCycles.rounds(cycle, "elsewhere", 2026).map { it.from } != SpotCycles.rounds(cycle, spot, 2026).map { it.from })
    }

    @Test fun `the pile looks as its stage, the stack growing, the holes going down as the smoke goes from white to blue`() {
        val r = SpotCycles.rounds(cycle, spot, 2026).first()
        fun at(id: String, day: Int = 0) = SpotCycles.on(cycle, spot, r.spans.first { it.stage.id == id }.from.plusDays(day.toLong())).look
        assertEquals("logs", at("fell").art)
        assertEquals("stack", at("stack").art)
        assertEquals("lit", at("light").art)
        val smoke = r.spans.filter { it.stage.art == "smoke" }.flatMap { s -> (0 until s.days).map { s.from.plusDays(it.toLong()) } }.map { SpotCycles.on(cycle, spot, it).look }
        assertEquals("white", smoke.first().smoke)
        assertEquals("blue", smoke.last().smoke)
        assertTrue("the holes go lower day by day", smoke.zipWithNext().all { (a, b) -> b.progress > a.progress })
        assertEquals(1f, smoke.last().progress, 0.001f)
        assertEquals(listOf("white", "yellow", "blue"), smoke.mapNotNull { it.smoke }.distinct())
        assertEquals("sacks", at("sacks").art)
        // between rounds and in the winter: the bare kopišče
        assertEquals(CycleKind.REST, SpotCycles.on(cycle, spot, r.until.plusDays(1)).kind)
        assertEquals(CycleKind.WINTER, SpotCycles.on(cycle, spot, LocalDate.of(2026, 1, 15)).kind)
        assertEquals("empty", SpotCycles.on(cycle, spot, LocalDate.of(2026, 12, 24)).look.art)
    }

    @Test fun `a round lost burns from its breakout night to its end, and nothing is delivered`() {
        val r = SpotCycles.rounds(cycle, spot, 2026).first { it.breakout != null }
        val b = r.breakout!!
        val marks = CycleMarks(lost = listOf(r.key))
        assertEquals(CycleKind.STAGE, SpotCycles.on(cycle, spot, b.minusDays(1), marks).kind)
        assertEquals(CycleKind.LOST, SpotCycles.on(cycle, spot, b, marks).kind)
        assertEquals("ash", SpotCycles.on(cycle, spot, r.until, marks).look.art)
        // decided once: a round saved stays saved
        var s = SpotCycles.decide(GameState(), spot, r.key, lost = false)
        assertEquals(listOf(r.key), s.cycles[spot]?.saved)
        assertEquals(s, SpotCycles.decide(s, spot, r.key, lost = true))
        s = SpotCycles.decide(GameState(), spot, r.key, lost = true)
        assertTrue(s.cycles[spot]!!.decided(r.key))
    }

    @Test fun `the stages a learner missed are the ones that ended while they were away`() {
        val r = SpotCycles.rounds(cycle, spot, 2026).first()
        val after = r.spans[1].from // they came while the logs were stood up
        val now = r.spans[5].from.plusDays(1) // and come again in the yellow smoke
        // the stack too when it went on after the day they came
        val stack = r.spans[1].days > 1
        assertEquals((if (stack) listOf("stack") else emptyList()) + listOf("cover", "light", "white"), SpotCycles.passed(cycle, spot, after, now).map { it.stage.id })
        assertTrue(SpotCycles.passed(cycle, spot, now, now).isEmpty())
    }

    @Test fun `what a round delivers is the receiver's for its days, unless it burnt down, and a forging uses it`() {
        val world = si.lanisce.lani.game.culture.Cultures.default().world.let { w ->
            w.copy(spots = mapOf(spot to w.spots.getValue("kopa").copy(cycle = cycle)))
        }
        val r = SpotCycles.rounds(cycle, spot, 2026).first()
        val on = r.delivery!!
        assertNull(SpotCycles.yieldFor(GameState(), "tone", on.minusDays(1), world)?.takeIf { it.round == r.key })
        val y = SpotCycles.charcoal(GameState(), "tone", on.plusDays(3), world)
        assertNotNull(y)
        assertEquals(r.key, y!!.round)
        assertNull("after its days, none", SpotCycles.yieldFor(GameState(), "tone", on.plusDays(14), world))
        assertNull("not someone else's", SpotCycles.yieldFor(GameState(), "micka", on, world))
        val used = SpotCycles.use(GameState(), y)
        assertNull("used in a forging", SpotCycles.charcoal(used, "tone", on.plusDays(3), world))
        // the chronicle's line on the day, once, when the receiver lives here
        val tone = GameState(residents = listOf(si.lanisce.lani.game.villagers.Resident("tone", "2026-01-01")))
        assertEquals(1, SpotCycles.news(tone, on.minusDays(1), on, world).size)
        assertTrue(SpotCycles.news(tone, on, on.plusDays(1), world).isEmpty())
        assertTrue(SpotCycles.news(GameState(), on.minusDays(1), on, world).isEmpty())
    }
}
