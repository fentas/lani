package si.lanisce.lani.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.game.villagers.VillagerLines
import java.time.LocalDate

class DayAudioTest {
    private val today = LocalDate.of(2026, 10, 10)
    private val tomorrow = today.plusDays(1)
    private val micka = listOf("@micka", "grandma", "female")

    private fun card(id: String, front: String, due: LocalDate?, kind: String = "vocabulary") = ReviewCard(id, front, "x", kind = kind, due = due)

    @Test fun `today's cards, the due and the overdue, as the review says them, in the companion's voices`() {
        val cards = listOf(
            card("a", "kruh", today),
            card("b", "Kako ste? / Kako si?", today.minusDays(3)),
            card("c", "hvala (lepa)", tomorrow),
            card("d", "jaz grem → mi gremo", today, kind = "grammar"),
            card("e", "*grem v trgovina*", today, kind = "error_pattern"),
        )
        val t = DayAudio.cards(cards, today, first = true, voices = micka)
        assertEquals(listOf("kruh", "kruh", "Kako ste? / Kako si?", "Kako ste?", "Kako si?"), t.map { it.text })
        assertTrue(t.all { it.voices == micka && it.why == "card" })
        // tomorrow: only those due that day; "hvala (lepa)" in both its forms
        assertEquals(listOf("hvala (lepa)", "hvala", "hvala lepa"), DayAudio.cards(cards, tomorrow, first = false, voices = micka).map { it.text })
    }

    @Test fun `a pack's next words and their examples, the examples in the narrator's voice`() {
        val w = listOf(PackWord("kruh", sl = "kruh", en = "bread", exampleSl = "Kupim kruh."), PackWord("mleko", sl = "mleko", en = "milk"))
        val t = DayAudio.packWords("v-trgovini", w, listOf("young-man", "male", "female"))
        assertEquals(listOf("kruh", "Kupim kruh.", "mleko"), t.map { it.text })
        assertEquals(listOf("young-man", "male", "female"), t[0].voices)
        assertEquals(Clips.chain(), t[1].voices)
        assertEquals("pack:v-trgovini", t[2].why)
    }

    @Test fun `a dialog, the person's lines in their voices, the right answers in the default voice, the replies in the last speaker's`() {
        val lines = listOf(
            DialogLine(who = "micka", sl = "Dober dan! Kaj boš?"),
            DialogLine(choices = listOf(
                DialogChoice("Juho, prosim.", ok = true, reply = DialogReply("Izvoli!")),
                DialogChoice("Juha, prosim.", reply = DialogReply("Juho, ne juha.")),
            )),
            DialogLine(who = "janez", sl = "Jaz tudi, {name}!"),
        )
        val voices = mapOf("micka" to micka, "janez" to listOf("grandpa", "male", "female"))
        val t = DayAudio.dialog(lines, { voices.getValue(it!!) }, "dialog:ogenj/juha")
        assertEquals(listOf("Dober dan! Kaj boš?", "Juho, prosim.", "Izvoli!", "Juho, ne juha."), t.map { it.text })
        assertEquals(listOf(micka, Clips.chain(), micka, micka), t.map { it.voices })
        // a line with a placeholder is said live, not got ahead
        assertFalse(t.any { '{' in it.text })
    }

    @Test fun `a villager's lines, without a memory's, and the day's greetings`() {
        val v = Villager(
            id = "micka", name = "Babica Micka", art = "grandma",
            lines = VillagerLines(greet = listOf(VillagerLine("Pozdravljen!")), idle = listOf(VillagerLine("Lep dan je.")), remember = listOf(VillagerLine("Še mislim na {memory}."))),
        )
        val t = DayAudio.villager(v, { it.by["sl"].orEmpty() }, listOf("Dober dan!"), micka)
        assertEquals(listOf("Dober dan!", "Pozdravljen!", "Lep dan je."), t.map { it.text })
        assertEquals("villager:micka", t.first().why)
    }

    @Test fun `the day's list, its parts in order, each text once in its wanted voice, nothing the node can't voice`() {
        val a = listOf(DayWant("Kruh", micka, "card"), DayWant("kruh!", micka, "pack:x"), DayWant("kruh", listOf("female"), "grammar:y"))
        val list = DayAudio.list(today, listOf(a))
        // "Kruh" and "kruh!" are one text for the voice store; in another wanted voice it's another clip
        assertEquals(listOf("card", "grammar:y"), list.wants.map { it.why })
        assertEquals("2026-10-10", list.day)
        assertNull(DayAudio.want("x".repeat(DayAudio.MAX_CHARS + 1), micka, "t"))
        assertNull(DayAudio.want("  ", micka, "t"))
        assertNull(DayAudio.want("Še mislim na {memory}.", micka, "t"))
    }

    @Test fun `a want against the store, its first voice with every clip, what to ask the node for, how it stands`() {
        val index = mapOf(
            "dober dan" to mapOf("grandma" to "/voice/file/a.mp3", "female" to "/voice/file/b.mp3"),
            "kako ste" to mapOf("female" to "/voice/file/c.mp3"),
            "kako si" to mapOf("female" to "/voice/file/d.mp3"),
        )
        val hello = DayWant("Dober dan!", micka, "t")
        // no clip in her own voice: the archetype's
        assertEquals("grandma" to listOf("/voice/file/a.mp3"), DayAudio.resolve(index, hello))
        assertEquals(listOf("Dober dan!"), DayAudio.missing(index, hello))
        val both = DayWant("Kako ste? / Kako si?", listOf("female"), "t")
        assertEquals("female" to listOf("/voice/file/c.mp3", "/voice/file/d.mp3"), DayAudio.resolve(index, both))
        assertTrue(DayAudio.missing(index, both).isEmpty())
        assertEquals(DayAudio.Stand.OTHER, DayAudio.stand(index, hello) { it == "a.mp3" })
        assertEquals(DayAudio.Stand.MISSING, DayAudio.stand(index, both) { it == "c.mp3" })
        assertEquals(DayAudio.Stand.WANTED, DayAudio.stand(index, both) { true })
        assertEquals("a.mp3", DayAudio.fileOf("/voice/file/a.mp3?count=0"))
    }

    @Test fun `the plan's days from today, today's cards renewed from the node`() {
        val plan = DayPlan(
            0, "sl",
            listOf(
                DayList("2026-10-09", listOf(DayWant("včeraj", micka, "card"))),
                DayList("2026-10-10", listOf(DayWant("star", micka, "card"), DayWant("Dober dan!", micka, "dialog:a/b")), companion = micka),
            ),
        )
        val days = DayPlans.days(plan, today, listOf(card("n", "nova", today)))
        // yesterday's tomorrow is today; a tomorrow is added (empty, with the cards due then)
        assertEquals(listOf("2026-10-10", "2026-10-11"), days.map { it.day })
        assertEquals(listOf("nova", "Dober dan!"), days[0].wants.map { it.text })
        assertEquals(micka, days[0].wants[0].voices)
        // without the node's cards the plan's stay
        assertEquals(listOf("star", "Dober dan!"), DayPlans.days(plan, today, null)[0].wants.map { it.text })
        // no plan at all: the cards alone
        assertEquals(listOf("nova"), DayPlans.days(null, today, listOf(card("n", "nova", today)))[0].wants.map { it.text })
    }
}

class DayPlanTest {
    private val w = DayWant("Dober dan!", listOf("grandma", "female"), "dialog:a/b")

    @Test fun `a plan made again with the same lines is the same, whenever it was made`() {
        val a = DayPlan(1, "sl", listOf(DayList("2026-10-10", listOf(w))))
        assertTrue(a.sameAs(a.copy(made = 2)))
        // a line more (the village loaded meanwhile), another voice, another day: not the same
        assertFalse(a.sameAs(a.copy(days = listOf(DayList("2026-10-10", listOf(w, w.copy(text = "Živijo!")))))))
        assertFalse(a.sameAs(a.copy(days = listOf(DayList("2026-10-10", listOf(w.copy(voices = listOf("female"))))))))
        assertFalse(a.sameAs(a.copy(days = listOf(DayList("2026-10-11", listOf(w))))))
        assertFalse(a.sameAs(a.copy(language = "it")))
    }
}
