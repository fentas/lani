package si.lanisce.lani.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType.*
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.HearthDay
import si.lanisce.lani.game.Hearths
import si.lanisce.lani.game.KopaWood
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.time.LocalDate

/**
 * The HUD's hearths tile and sheet (companion/GAME.md "Hearths", [HearthsView]): "6/7" lit of all and its bar, the line under
 * the title, each hearth's row (lit, out for want of wood, blown out, the forge on its charcoal and its days, the centre lit
 * in the evening), the wood a day and the days the store lasts, the charcoal pile's wood coming, waited for, given.
 */
class HearthsViewTest {
    @Before fun primorska() {
        Cultures.use("primorska")
        L10n.pair = LangPair(Lang.SL, Lang.EN)
    }

    private val today = LocalDate.of(2026, 7, 1)

    private fun village(out: List<String> = emptyList(), blown: List<String> = emptyList(), wood: Int = 40) = GameState(
        age = Age.VAS, resources = mapOf(Res.WOOD to wood),
        buildings = listOf(Building("smithy", SMITHY, 0), Building("hut", HUT, 1), Building("house", HOUSE, 2), Building("school", SCHOOL, 3), Building("church", CHURCH, 4)),
        // kept since June: the year's first pile (its wood in April) counts as given, its charcoal lasts to the next pile's
        hearths = HearthDay(on = today.toString(), out = out, blown = blown, since = "2026-06-01"), lastTick = today.toString(),
    )

    @Test fun `the tile, the bar and the line under the title`() {
        val all = HearthsView.of(village(), today)
        assertEquals("5/5", all.tile)
        assertEquals(1f, all.share)
        assertFalse(all.short)
        assertEquals("5 od 5 gori · 5 of 5 lit", all.sub)
        // the outlying house and the ring out for want of wood: red, "going out"
        val short = HearthsView.of(village(out = listOf("house", Hearths.CENTRE)), today)
        assertEquals("3/5", short.tile)
        assertEquals(0.6f, short.share)
        assertTrue(short.short)
        assertEquals("Ognjišča ugašajo · Hearths are going out", short.sub)
        // blown out by the storm: out, but not for want of wood
        val storm = HearthsView.of(village(out = listOf("house"), blown = listOf("house")), today)
        assertEquals("4/5", storm.tile)
        assertFalse(storm.short)
    }

    @Test fun `each hearth's row`() {
        val v = HearthsView.of(village(out = listOf("house", Hearths.CENTRE)), today)
        val rows = v.rows.associateBy { it.name }
        assertEquals(listOf("Kovačnica · Smithy", "Šola · School", "Koča · Hut", "Hiša · House", "Ognjišče · The fire ring"), v.rows.map { it.name })
        val forge = rows.getValue("Kovačnica · Smithy")
        assertTrue(forge.state, forge.state.startsWith("na oglje, še ") && forge.state.contains(" · on charcoal, "))
        assertEquals("♨️", forge.burns)
        assertEquals("gori · lit", rows.getValue("Koča · Hut").state)
        assertEquals("−1 🪵", rows.getValue("Koča · Hut").burns)
        assertEquals("ugasnilo: ni drv · out: no wood", rows.getValue("Hiša · House").state)
        assertEquals("−2 🪵", rows.getValue("Hiša · House").burns)
        assertFalse(rows.getValue("Hiša · House").lit)
        assertEquals("ugasnilo: ni drv · out: no wood", rows.getValue("Ognjišče · The fire ring").state)
        val lit = HearthsView.of(village(), today).rows.last()
        assertEquals("zvečer gori za vse · lit in the evening for everyone", lit.state)
        // the forge cold: no charcoal (a smithy built today, its sacks, would have them: here the record says it's out)
        val cold = HearthsView.of(village(out = listOf("smithy")), today).rows.first()
        assertEquals("mrzla: ni oglja · cold: no charcoal", cold.state)
        // blown out
        assertEquals("upihnil ga je veter: jutri spet gori · blown out by the wind: lit again tomorrow", HearthsView.of(village(out = listOf("hut"), blown = listOf("hut")), today).rows.first { it.name.startsWith("Koča") }.state)
    }

    @Test fun `wood a day, the days it lasts, the charcoal pile's wood`() {
        val v = HearthsView.of(village(wood = 40), today)
        // the school 1, the hut 1, the house 2, the ring 1
        assertEquals(5, v.wood)
        assertEquals(8, v.days)
        val coming = KopaWood("kopa", "2026-1", 120, today.plusDays(3), today.plusDays(8), paid = false, waiting = false, keeper = "Oglar Miha")
        assertEquals("Oglar Miha bo čez 3 dni potreboval 120 🪵 za kopo · Oglar Miha will need 120 🪵 for his charcoal pile in 3 days", HearthsView.kopa(coming, today))
        assertEquals("Oglar Miha čaka na 120 🪵 za kopo · Oglar Miha is waiting for 120 🪵 for his charcoal pile", HearthsView.kopa(coming.copy(waiting = true), today))
        assertEquals("Oglar Miha je dobil iz vasi 120 🪵 za kopo · Oglar Miha got 120 🪵 from the village for his charcoal pile", HearthsView.kopa(coming.copy(paid = true), today))
        assertEquals("Oglar Miha bo čez 1 dan potreboval 120 🪵 za kopo · Oglar Miha will need 120 🪵 for his charcoal pile in 1 day", HearthsView.kopa(coming.copy(from = today.plusDays(1)), today))
        assertEquals("Oglar Miha bo čez 2 dneva potreboval 120 🪵 za kopo · Oglar Miha will need 120 🪵 for his charcoal pile in 2 days", HearthsView.kopa(coming.copy(from = today.plusDays(2)), today))
        // no smithy, no pile's wood to give
        assertNull(HearthsView.of(village().copy(buildings = listOf(Building("hut", HUT, 1))), today).kopa)
    }
}
