package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Mentions
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Villager
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A happening's variants (companion/SCENES.md, "Variants: not the same dialog every time"): one not heard yet first,
 * never the same twice in a row, a review once all are heard after a gap, a rest day when none is due; the variant the
 * village plays today, the same all day, recorded when heard; the ones naming someone the learner doesn't know wait.
 */
class DialogVariantsTest {
    private val day = LocalDate.of(2026, 9, 27)
    private val all = listOf("a", "b", "c")
    private fun heard(vararg on: Pair<String, Int>, last: String? = on.lastOrNull()?.first) =
        DialogsHeard(on.associate { (id, ago) -> id to day.minusDays(ago.toLong()).toString() }, last)

    @Test fun `one not heard yet first, in order`() {
        assertEquals("a", DialogVariants.pick(all, all, null, day))
        assertEquals("b", DialogVariants.pick(all, all, heard("a" to 0), day))
        assertEquals("c", DialogVariants.pick(all, all, heard("a" to 5, "b" to 1), day))
        // a variant added later (the tutor's) is the one not heard yet
        assertEquals("d", DialogVariants.pick(all + "d", all + "d", heard("a" to 9, "b" to 8, "c" to 7), day))
    }

    @Test fun `once all are heard - the one heard longest ago, after the gap, never the last one`() {
        assertEquals("a", DialogVariants.pick(all, all, heard("a" to 9, "b" to 5, "c" to 1), day))
        assertEquals("b", DialogVariants.pick(all, all, heard("b" to 4, "c" to 3, "a" to 3), day))
        // the last one heard never comes again next, even when it was heard longest ago (a clock set back)
        assertEquals("b", DialogVariants.pick(all, all, DialogsHeard(mapOf("a" to "2026-09-01", "b" to "2026-09-10", "c" to "2026-09-26"), last = "a"), day))
    }

    @Test fun `none due - the happening rests until one is`() {
        val lately = heard("a" to 2, "b" to 1, "c" to 0)
        assertNull(DialogVariants.pick(all, all, lately, day))
        assertEquals("a", DialogVariants.pick(all, all, lately, day.plusDays(1)))
        assertEquals(DialogVariants.GAP, 3L)
        // two variants, played each day they come: one, the other, a rest day, the first again
        val two = listOf("x", "y")
        var h: DialogsHeard? = null
        val played = (0 until 6).map { d ->
            val today = day.plusDays(d.toLong())
            DialogVariants.pick(two, two, h, today)?.also { h = DialogsHeard((h?.heard ?: emptyMap()) + (it to today.toString()), it) }
        }
        assertEquals(listOf("x", "y", null, "x", "y", null), played)
    }

    @Test fun `a happening with one dialog plays it every time`() {
        assertEquals("a", DialogVariants.pick(listOf("a"), listOf("a"), heard("a" to 0), day))
        assertNull(DialogVariants.pick(listOf("a"), emptyList(), null, day))
        assertNull(DialogVariants.pick(emptyList(), emptyList(), null, day))
    }

    @Test fun `only the playable ones - a variant naming someone not met waits`() {
        assertEquals("b", DialogVariants.pick(all, listOf("b", "c"), null, day))
        assertEquals("c", DialogVariants.pick(all, listOf("b", "c"), heard("b" to 0), day))
        // all the playable ones heard lately: a rest, though "a" was never heard
        assertNull(DialogVariants.pick(all, listOf("b", "c"), heard("b" to 1, "c" to 0), day))
    }

    @Test fun `on a visit the day's dice picks among them`() {
        assertEquals("a", DialogVariants.pick(all, all, heard("a" to 0), day, dice = 0.1f))
        assertEquals("c", DialogVariants.pick(all, all, null, day, dice = 0.99f))
    }

    @Test fun `recorded when heard - each with its date, and the last one`() {
        val s = DialogVariants.record(GameState(), "ob-potoku/ovce", "a", day)
        val t = DialogVariants.record(s, "ob-potoku/ovce", "b", day.plusDays(1))
        assertEquals(DialogsHeard(mapOf("a" to "2026-09-27", "b" to "2026-09-28"), "b"), t.dialogsHeard["ob-potoku/ovce"])
        assertTrue(DialogVariants.record(t, "ob-potoku/ovce", "b", day.plusDays(1)) === t)
        assertTrue(DialogVariants.allHeard(listOf("a", "b"), t.dialogsHeard["ob-potoku/ovce"]))
        assertFalse(DialogVariants.allHeard(all, t.dialogsHeard["ob-potoku/ovce"]))
    }

    // --- in the village ----------------------------------------------------------------------------------------------

    private val luka = ScenePerson("luka", "Pastir Luka", "🐑", "shepherd", "bank", villager = "luka")
    private fun line(who: String, sl: String) = DialogLine(who = who, sl = sl, en = sl)
    private fun talk(id: String, first: String) = Dialog(id, listOf(line("luka", first), DialogLine(choices = listOf(DialogChoice("Ja.", ok = true), DialogChoice("Ne.", why = "…"))), line("luka", "Adijo.")))
    private val stream = SceneSpec(
        id = "ob-potoku", title = "Ob potoku", art = "stream", from = listOf("spot:riverbank"), people = listOf(luka),
        happenings = listOf(
            Happening("ovce", "Luka napaja ovce", who = "luka", `when` = listOf(TimeOfDay.EVENING), dialog = "ovce", dialogs = listOf("ovce-2", "ovce-3", "ovce"),
                memory = DialogReply("postrv", "the trout")),
        ),
        dialogs = listOf(
            talk("ovce", "Ovce so žejne."),
            talk("ovce-2", "Anton mi je dal med.").copy(memory = DialogReply("med", "the honey")),
            Dialog("ovce-3", count = DialogCount("dice", 12, 24, gender = "f"),
                lines = listOf(line("luka", "Danes {je|sta|so|je} pri vodi {n} {ovca|ovci|ovce|ovc}."), DialogLine(choices = listOf(DialogChoice("Ja.", ok = true), DialogChoice("Ne.", why = "…"))), line("luka", "Adijo."))),
        ),
    )
    private val cast = listOf(Villager("luka", "Pastir Luka", "🐑", "shepherd"), Villager("anton", "Čebelar Anton", "🐝", "beekeeper"))
    private val evening = LocalDateTime.of(2026, 9, 27, 19, 0)
    private fun village(vararg living: String, heard: Map<String, DialogsHeard> = emptyMap()) = GameState(
        seed = 3, age = Age.TABOR, residents = living.map { Resident(it, "2026-09-01") }, bonds = living.associateWith { Bond(met = "2026-09-01") },
        migrated = setOf("arrivals"), dialogsHeard = heard,
    )

    @Test fun `the village plays today's variant - the same all day - and a rest day when none is due`() {
        val both = village("luka", "anton")
        val a = Happenings.active(listOf(stream), both, evening, cast = cast).single()
        assertEquals("ovce-2", a.dialog)
        assertTrue(a.talks)
        assertEquals(a.dialog, Happenings.active(listOf(stream), both, evening.withHour(21), cast = cast).single().dialog)
        // Anton isn't met yet: his variant waits, the next one plays
        val onlyLuka = village("luka")
        assertEquals("ovce-3", Happenings.active(listOf(stream), onlyLuka, evening, cast = cast).single().dialog)
        // all heard lately: Luka isn't at the brook with them today
        val lately = village("luka", "anton", heard = mapOf("ob-potoku/ovce" to DialogsHeard(mapOf("ovce-2" to "2026-09-25", "ovce-3" to "2026-09-26", "ovce" to "2026-09-25"), "ovce-3")))
        assertTrue(Happenings.active(listOf(stream), lately, evening, cast = cast).isEmpty())
        assertTrue(Happenings.later(listOf(stream), lately, evening.withHour(8), cast = cast).isEmpty())
        // the next day two are due again: the one first in the list
        assertEquals("ovce-2", Happenings.active(listOf(stream), lately, evening.plusDays(1), cast = cast).singleOrNull()?.dialog)
    }

    @Test fun `what it plays - the variant with its number, and what the person remembers of it`() {
        val state = village("luka", "anton")
        val a = Happenings.active(listOf(stream), state, evening, cast = cast).single()
        val (h, d) = DialogVariants.play(a, state, evening.toLocalDate())!!
        assertEquals("ovce-2", d.id)
        assertEquals("med", h.memory?.sl)
        val counted = DialogVariants.play(a.copy(dialog = "ovce-3"), state, evening.toLocalDate())!!
        val n = Counts.of(DialogCount("dice", 12, 24, gender = "f"), state, evening.toLocalDate(), "ob-potoku/ovce/ovce-3")
        assertEquals("Danes je pri vodi ${Numbers.words(n, "sl", "f")} ovc.", counted.second.lines[0].sl)
        assertEquals("postrv", counted.first.memory?.sl)
        // a happening of old, without variants, plays its one dialog
        val old = DialogVariants.play(a.copy(dialog = null), state, evening.toLocalDate())!!
        assertEquals("ovce", old.second.id)
    }

    @Test fun `the mentions - a happening names whom all its variants name, and a variant its own`() {
        val names = Mentions.names("sl", cast)
        assertEquals(setOf("anton"), Mentions.inHappening(stream, stream.happenings[0], names) - "luka")
        assertEquals(setOf("luka"), Mentions.inTitle(stream.happenings[0], names))
        assertEquals(setOf("anton"), Mentions.inDialog(stream.dialogs[1], names))
    }

    @Test fun `a file's variants are among the dialogs when the app reads it`() {
        val raw = """{"id":"s","title":"S","art":"stream","from":["fire"],"happenings":[{"id":"h","title":"H","who":"p","dialog":"a","dialogs":["b","a"]}],
            "dialogs":[{"id":"a","lines":[]}],"variants":[{"id":"b","lines":[],"memory":{"sl":"to","en":"that","de":"das"}}]}"""
        val s = parseScene(raw)
        assertEquals(listOf("a", "b"), s.dialogs.map { it.id })
        assertEquals(listOf("b", "a"), s.happenings[0].dialogs)
        assertEquals("that", s.dialogs[1].memory?.en)
    }
}
