package si.lanisce.lani.l10n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Days of the year in each language: Slovene as the game has always written them, the others by java.time's locale. */
class DatesTest {
    private val martin = LocalDate.of(2026, 11, 11)
    private val march = LocalDate.of(2026, 3, 8)

    @Test fun `Slovene takes the month in the genitive, as before`() {
        val months = listOf("januarja", "februarja", "marca", "aprila", "maja", "junija", "julija", "avgusta", "septembra", "oktobra", "novembra", "decembra")
        for (m in 1..12) assertEquals("1. ${months[m - 1]}", Dates.dayMonth(LocalDate.of(2026, m, 1), Lang.SL))
        assertEquals("11. novembra", Dates.dayMonth(martin, Lang.SL))
        assertEquals("8. 3.", Dates.short(march, Lang.SL))
    }

    @Test fun `the other languages by their locale`() {
        assertEquals("11 November", Dates.dayMonth(martin, Lang.EN))
        assertEquals("11 novembre", Dates.dayMonth(martin, Lang.IT))
        assertEquals("11. November", Dates.dayMonth(martin, Lang.DE))
        assertEquals("8 March", Dates.short(march, Lang.EN))
        assertEquals("8 marzo", Dates.short(march, Lang.IT))
    }

    @Test fun `a week's days, short, in each language`() {
        val mon = LocalDate.of(2026, 9, 14)
        val sun = mon.plusDays(6)
        assertEquals("14.–20. sept.", Dates.range(mon, sun, Lang.SL))
        assertEquals("14–20 Sept", Dates.range(mon, sun, Lang.EN))
        assertEquals("14.–20. Sept.", Dates.range(mon, sun, Lang.DE))
        assertEquals("14–20 set", Dates.range(mon, sun, Lang.IT))
        // across months, and with the year
        val turn = LocalDate.of(2026, 9, 28)
        assertEquals("28. sept.–4. okt.", Dates.range(turn, turn.plusDays(6), Lang.SL))
        assertEquals("28 Sept–4 Oct", Dates.range(turn, turn.plusDays(6), Lang.EN))
        assertEquals("28. Sept.–4. Okt. 2026", Dates.range(turn, turn.plusDays(6), Lang.DE, year = true))
        val newYear = LocalDate.of(2025, 12, 29)
        assertEquals("29. dec. 2025–4. jan. 2026", Dates.range(newYear, newYear.plusDays(6), Lang.SL, year = true))
        assertEquals("29 dic 2025–4 gen 2026", Dates.range(newYear, newYear.plusDays(6), Lang.IT, year = true))
        assertEquals("25.–31. maj", Dates.range(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 5, 31), Lang.SL))
        assertEquals("28. sept.–4. okt.", Dates.range(turn, turn.plusDays(6)).of(Lang.SL))
    }

    @Test fun `as an argument, a date reads in each message's language`() {
        val d = Dates.dayMonth(martin)
        assertEquals("11. novembra", d.of(Lang.SL))
        assertEquals("11 novembre", d.of(Lang.IT))
    }
}
