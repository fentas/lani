package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Resident
import java.time.LocalDate

/** "Jutri · Tomorrow" (GAME.md): always something to come back for. */
class TomorrowTest {
    private val people = listOf("micka", "luka", "zala", "janez", "france", "marko", "tine", "nejc")

    private fun village(age: Age = Age.ZASELEK) = GameState(
        age = age, residents = people.map { Resident(it, "2026-01-01") }, villagers = people.size,
        resources = Res.entries.associateWith { 500 }, help = 20, chest = Inventory(goods = mapOf("potica" to 2)),
    )

    private fun kinds(s: GameState, day: LocalDate) = Tomorrow.teasers(s, day).map { it.kind }

    @Test fun `a festival counts down first`() {
        val t = Tomorrow.teasers(village(), LocalDate.parse("2026-11-08"))
        assertEquals(Teaser.Kind.FESTIVAL, t.first().kind)
        assertEquals("🍷 Čez 3 dni: Martinovo · St Martin's Day in 3 days", t.first().line)
    }

    @Test fun `after today's project step, tomorrow's`() {
        val day = LocalDate.parse("2026-10-06")
        val s = Projects.finishStep(village(), "mlaj", 5, 5, day, noon(day)).first
        val t = Tomorrow.teasers(s, day).first { it.kind == Teaser.Kind.PROJECT }
        assertEquals("🌲 Jutri: Mlaj, korak 2/5 · Tomorrow: the maypole, step 2 of 5", t.line)
        assertTrue(Teaser.Kind.PROJECT !in kinds(village(), day))
    }

    @Test fun `the pedlar's next day, the next feast, and always tomorrow's surprise`() {
        val s = village()
        val day = LocalDate.parse("2026-10-06")
        val pedlarDay = (1..7L).map { day.plusDays(it) }.firstOrNull { Surprises.roll(s, it)?.kind == Surprises.PEDLAR }
        if (pedlarDay != null) {
            val (sl, en) = Tomorrow.on(pedlarDay, day)
            assertEquals("🎒 Krošnjar pride $sl · The pedlar comes $en", Tomorrow.teasers(s, day).first { it.kind == Teaser.Kind.PEDLAR }.line)
        }
        assertEquals("jutri" to "tomorrow", Tomorrow.on(day.plusDays(1), day))
        assertEquals("v soboto" to "on Saturday", Tomorrow.on(LocalDate.parse("2026-10-10"), day))
        // a feast held yesterday: the next one a week after it, announced
        val feasted = s.copy(buildings = listOf(Building("l", BuildingType.LIPA, 0)), lastFeast = day.minusDays(1).toString())
        assertEquals("🎪 Veselica bo spet v ponedeljek · The next feast is on Monday", Tomorrow.teasers(feasted, day).first { it.kind == Teaser.Kind.FEAST }.line)
        assertEquals(Teaser.Kind.SURPRISE, Tomorrow.teasers(s, day).last().kind)
        // the campfire has nothing to come back for but itself
        assertTrue(Tomorrow.teasers(GameState(), day).none { it.kind == Teaser.Kind.SURPRISE || it.kind == Teaser.Kind.PEDLAR })
    }

    @Test fun `a friend's gift is never announced, however near`() {
        val day = LocalDate.parse("2026-10-06")
        // Luka at 26 points, Micka at 149: his axe at 30, her better recipe at 150, each one talk away; they come as a surprise
        val near = village().copy(bonds = mapOf("luka" to Bond(26), "micka" to Bond(149)), chest = Inventory(gifted = mapOf("micka" to 2)))
        val lines = Tomorrow.teasers(near, day).map { it.line }
        assertTrue(lines.joinToString("\n"), lines.none { "darilo" in it || "gift" in it || "Luka" in it || "Micka" in it })
    }
}
