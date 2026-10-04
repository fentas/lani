package si.lanisce.lani.ui.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.villagers.VillagerLine

class LinesTest {
    private val a = VillagerLine("A", "a")
    private val b = VillagerLine("B", "b")
    private val warm = VillagerLine("W", "w", level = 2)

    @Test fun `pick keeps to the friendship level and skips the line said last`() {
        for (r in listOf(0f, 0.3f, 0.6f, 0.99f)) {
            assertNotEquals("W", Lines.pick(listOf(a, b, warm), 1, r)!!.target)
            assertNotEquals("A", Lines.pick(listOf(a, b), 0, r, avoid = "A")!!.target)
        }
        // With one line open, it is said even if it was the last.
        assertEquals("A", Lines.pick(listOf(a, warm), 0, 0.5f, avoid = "A")!!.target)
        assertNull(Lines.pick(listOf(warm), 0, 0.5f))
    }

    @Test fun `warmer lines weigh more`() {
        val picks = (0 until 100).map { Lines.pick(listOf(a, warm), 2, it / 100f)!!.target }
        assertEquals(75, picks.count { it == "W" }) // weights 1 : 3
    }

    @Test fun `their own lines are topped up with built-in ones while they have fewer than two`() {
        assertEquals(listOf(a, b), Lines.pool(listOf(a, b), Lines.cheer("ti"), 0))
        assertEquals(1 + Lines.cheer("ti").size, Lines.pool(listOf(a), Lines.cheer("ti"), 0).size)
        // A warm line not open yet doesn't count.
        assertEquals(2 + Lines.cheer("ti").size, Lines.pool(listOf(a, warm), Lines.cheer("ti"), 0).size)
    }

    @Test fun `built-in lines come in the ti and the vi form`() {
        assertEquals("Sestavi stavek.", Lines.leadIns(Exercise.Reorder("x", listOf("a"), listOf(listOf("a"))), "ti").first().target)
        assertEquals("Sestavite stavek.", Lines.leadIns(Exercise.Reorder("x", listOf("a"), listOf(listOf("a"))), "vi").first().target)
        assertEquals("Mi pomagaš?", Lines.ask(Run.Quest("Pastir Luka", "🐑"), "ti").target)
        assertEquals("Mi pomagate?", Lines.ask(Run.Quest("Gostilničarka Vida", "🍷"), "vi").target)
        // The vi forms never slip into ti.
        val tiOnly = setOf("poslušaj", "prisluhni", "pomisli", "slišiš", "ti", "te", "znaš", "odrezal", "sestavi", "dopolni", "izberi")
        val lines = listOf(Lines.cheer("vi"), Lines.comfort("vi"), Lines.listen("vi"), Lines.think("vi"), Lines.greet("vi"), Lines.bye("vi")).flatten() +
            listOf(Exercise.Cloze("a ___", listOf("b")), Exercise.Choice("p", listOf("a"), 0)).flatMap { Lines.leadIns(it, "vi") } +
            Lines.closing(9, 10, 10, "vi")!!
        for (l in lines) assertTrue(l.target, l.target.lowercase().split(Regex("[^\\p{L}]+")).none { it in tiOnly })
        assertEquals("Poslušajte …", Lines.listen("vi").first().target)
    }

    @Test fun `every run has a request`() {
        val runs = listOf(Run.Review, Run.Quest("x", "y"), Run.Module(null), Run.Module("x"), Run.Pack("x")) +
            Res.entries.map { Run.Gather(it) } + EventKind.entries.map { Run.Event(it) }
        for (r in runs) for (reg in listOf("ti", "vi")) {
            val l = Lines.ask(r, reg)
            assertTrue("$r", l.target.isNotBlank() && l.base.isNotBlank())
        }
    }

    @Test fun `the closing line has the tally and a kind word`() {
        assertEquals("9 od 10 pravilno. Odlično si se odrezal!", Lines.closing(9, 10, 10, "ti")!!.target)
        assertEquals("9 od 10 pravilno. Odlično ste se odrezali!", Lines.closing(9, 10, 10, "vi")!!.target)
        assertEquals("5 od 10 pravilno. Dobro delo!", Lines.closing(5, 10, 10, "ti")!!.target)
        assertEquals("2 od 10 pravilno. Jutri bo še bolje.", Lines.closing(2, 10, 10, "ti")!!.target)
        // Left early: the tally counts the whole run, so one right answer out of six isn't "excellent".
        assertEquals("1 od 6 pravilno. Jutri bo še bolje.", Lines.closing(1, 1, 6, "ti")!!.target)
        assertEquals("Hvala za trud! Jutri bo še bolje.", Lines.closing(0, 3, 3, "ti")!!.target)
        assertNull(Lines.closing(0, 0, 6, "ti"))
    }

    @Test fun `every exercise kind has a lead-in`() {
        val all = listOf(
            Exercise.Flashcard("a", "b"), Exercise.Choice("p", listOf("a"), 0), Exercise.Choice("", listOf("a"), 0, audio = "x"),
            Exercise.Cloze("a ___", listOf("b")), Exercise.Dictation("x", listOf("x")), Exercise.Reorder("p", listOf("a"), listOf(listOf("a"))),
            Exercise.Translate("p", listOf("a")), Exercise.Free("p", "r"), Exercise.Multi("p", listOf("a"), listOf(0)),
            Exercise.Scenario("s", "p"), Exercise.Speak("x", "p"), Exercise.Unsupported("new"),
        )
        for (ex in all) assertTrue("$ex", Lines.leadIns(ex, "ti").isNotEmpty())
    }
}
