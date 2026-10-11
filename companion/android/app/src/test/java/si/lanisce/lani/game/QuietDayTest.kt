package si.lanisce.lani.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.culture.CultureError
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.villagers.Resident
import java.time.LocalDate

/**
 * A quiet day of the calendar (companion/GAME.md, "The calendar": Dan spomina na mrtve): kept, not celebrated. Martinovo
 * stands in for one here, made quiet and without its good, so the rule is checked whatever days the culture has.
 */
class QuietDayTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val day = d("2026-11-11")
    private val people = listOf("marko", "micka", "janez", "luka", "zala")
    private fun village() = GameState(
        age = Age.VAS, residents = people.map { Resident(it, "2026-01-01") }, villagers = people.size,
        resources = Res.entries.associateWith { 100 }, morale = 60,
    )

    @Before fun quiet() {
        val c = Cultures.default()
        val f = c.festivalsFile
        Cultures.use(c.withFestivals(f.copy(festivals = f.festivals.map { if (it.id == "martinovo") it.copy(quiet = true, good = null) else it })))
    }

    @After fun back() {
        Cultures.forget()
        Cultures.use(Cultures.DEFAULT)
    }

    private val m get() = Calendar.byId("martinovo")!!

    @Test fun `it asks to remember, not to celebrate, wherever the app says it`() {
        assertTrue(m.quiet)
        assertEquals("Spomni se · Remember", Calendar.action(m))
        assertEquals("Spomnili smo se · We remembered", Calendar.done(m))
        // the day itself and its countdown name it plainly; its grace days are time still to remember
        assertEquals("Danes: Martinovo 🍷 · Today: St Martin's Day", Calendar.todayText(FestivalDay(m, day, 0, false)))
        assertEquals("Martinovo: še je čas za spomin 🍷 · St Martin's Day: there's still time to remember", Calendar.todayText(FestivalDay(m, day, 1, false)))
        assertEquals("Čez 3 dni: Martinovo 🍷 · St Martin's Day in 3 days", Calendar.countdown(m, 3))
        assertEquals("Še je čas · There's still time", Calendar.whenText(m, day.plusDays(2)))
        assertEquals("Danes · Today", Calendar.whenText(m, day))
        // the run: the same kind, its words "to remember"
        val run = Calendar.exercises(m, 7, day)
        assertEquals(Calendar.EXERCISES, run.size)
        assertTrue(run.all { (it as Exercise.Choice).instruction == "🍷 Besede za spomin · Words to remember" })
        // a feast still says what it always did
        val trgatev = Calendar.byId("trgatev")!!
        assertFalse(trgatev.quiet)
        assertEquals("Praznuj · Celebrate", Calendar.action(trgatev))
        assertEquals("Praznovano · Celebrated", Calendar.done(trgatev))
        assertEquals("Trgatev še traja 🍇 · The grape harvest isn't over yet", Calendar.todayText(FestivalDay(trgatev, d("2026-09-26"), 1, false)))
    }

    @Test fun `remembered, only the people closer and the chronicle's line, no morale, no good, no pay`() {
        val s = village()
        val (n, r) = Calendar.celebrate(s, "martinovo", 4, 6, day, noon(day))
        assertTrue(r.won)
        assertEquals(s.morale, n.morale)
        assertEquals(s.resources, n.resources)
        assertTrue(r.rewards.isEmpty())
        assertNull(r.thanks)
        assertEquals(s.chest, n.chest)
        assertEquals("🍷 ${m.line}", r.message)
        assertTrue(n.log.last().text == m.line)
        assertEquals("2026-11-11", n.festivals["martinovo"])
        // Marko leads it (+5 ♥ in all), everyone who lives here +1
        assertEquals(Calendar.LEADER_POINTS, n.bonds["marko"]?.points)
        assertEquals(Calendar.ALL_POINTS, n.bonds["zala"]?.points)
        // once a year, like any day of the calendar
        assertTrue(Calendar.open(n, day).isEmpty())
        assertEquals(1, Calendar.open(n, d("2027-11-11")).size)
        // a day or two late it is the same: no morale either
        val (late, _) = Calendar.celebrate(s, "martinovo", 6, 6, day.plusDays(2), noon(day.plusDays(2)))
        assertEquals(s.morale, late.morale)
    }

    @Test fun `a try that doesn't pass is thanked for, costs nothing and pays nothing, and stays open`() {
        val s = village()
        val (n, r) = Calendar.celebrate(s, "martinovo", 1, 6, day, noon(day))
        assertFalse(r.won)
        assertSame(s, n)
        assertTrue(r.rewards.isEmpty())
        assertEquals("🍷 Hvala, ker se spominjaš z nami. · Thank you for remembering with us.", r.message)
        assertEquals(1, Calendar.open(n, day).size)
        // a feast's try lifts the spirits and pays a fifth, as it always did
        val (tried, t) = Calendar.celebrate(s, "trgatev", 1, 6, d("2026-09-26"), noon(d("2026-09-26")))
        assertEquals(s.morale + Calendar.TRY_MORALE, tried.morale)
        assertTrue(t.rewards.isNotEmpty())
    }

    @Test fun `of two days on at once, the one whose day it is comes first`() {
        // the grape harvest's grace days overlap nothing in the pack, so make one: a feast on its second grace day and the
        // quiet day on its own day
        val s = village()
        val c = Cultures.current
        val f = c.festivalsFile
        val dayOf = f.festivals.first { it.id == "trgatev" }.copy(id = "early", date = si.lanisce.lani.game.culture.DateEntry(month = 11, day = 9), words = "praznik-trgatev")
        Cultures.use(c.withFestivals(f.copy(festivals = listOf(dayOf) + f.festivals)))
        assertEquals(listOf("martinovo", "early"), Calendar.on(s, day).map { it.festival.id })
    }

    @Test fun `a quiet day brings no good, and is no season for a thank-you`() {
        fun problems(edit: (JsonObject) -> JsonObject): String = try {
            Cultures.parse("primorska") { name ->
                val raw = javaClass.getResourceAsStream("/cultures/primorska/$name")?.use { it.readBytes().decodeToString() }
                if (name != "festivals.json" || raw == null) raw else edit(Json.parseToJsonElement(raw).jsonObject).toString()
            }
            fail("accepted")
            ""
        } catch (e: CultureError) {
            e.problems.joinToString("\n")
        }
        // Martinovo quiet, but still with its young wine, and Marko's and Vida's seasonal thank-you for it
        val said = problems { o ->
            val list = o.getValue("festivals").jsonArray.map { e ->
                val f = e.jsonObject
                if (f.getValue("id").jsonPrimitive.content == "martinovo") JsonObject(f + ("quiet" to JsonPrimitive(true))) else f
            }
            JsonObject(o + ("festivals" to JsonArray(list)))
        }
        assertTrue(said, said.contains("a quiet day (quiet) brings no good"))
        assertTrue(said, said.contains("\"martinovo\" is a quiet day: no seasonal good"))
    }
}
