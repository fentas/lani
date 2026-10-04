package si.lanisce.lani.data

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillsTest {
    private val sl = Drills.bundled("sl")
    private fun of(kind: DrillKind) = sl.single { it.kind == kind }

    @Test fun `the four drills are bundled, Slovene, one of each kind`() {
        assertEquals(DrillKind.entries.toSet(), sl.map { it.kind }.toSet())
        assertEquals(4, sl.size)
        assertTrue(sl.all { it.language == "sl" && it.title.meaning("en").isNotBlank() })
        assertEquals(emptyList<Drill>(), Drills.bundled("xx"))
        assertEquals("Preobrat · Transformations", of(DrillKind.TRANSFORM).titleShown("en"))
    }

    @Test fun `every transformation set is of a page of the bundled grammar book, the A1 ones first`() {
        val pages = Grammar.bundled("sl").associateBy { it.id }
        val sets = of(DrillKind.TRANSFORM).transforms
        assertTrue(sets.all { it.rule in pages })
        assertTrue(sets.all { pages.getValue(it.rule).level == it.level })
        val levels = sets.map { it.level }
        assertEquals(levels.sorted(), levels)
        assertTrue(sets.flatMap { it.items }.all { it.from.said("sl").isNotBlank() && it.to.meaning("de").isNotBlank() })
    }

    @Test fun `the numbers are generated as the app counts them, asked in each base`() {
        val numbers = of(DrillKind.RAPID).rapid.first { it.numbers.isNotEmpty() }
        val items = numbers.items("sl")
        assertEquals((1..100).toList(), items.map { it.key.toInt() })
        val n21 = items.single { it.key == "21" }
        assertEquals("21", n21.figure)
        assertEquals("enaindvajset", n21.text.said("sl"))
        assertEquals("twenty-one", n21.text.meaning("en"))
        assertEquals("einundzwanzig", n21.text.meaning("de"))
        assertEquals("dvesto petinštirideset", Drills.number(245, "sl")?.said("sl"))
        assertNull(Drills.number(5, "fr"))
        // the listed answers keyed by their text, each once in its set
        for (set in of(DrillKind.RAPID).rapid) assertEquals(set.id, set.items("sl").size, set.items("sl").map { it.key }.toSet().size)
    }

    @Test fun `builds of three or four steps, each after the first adding something`() {
        val builds = of(DrillKind.BUILD).builds
        assertTrue(builds.size >= 60)
        assertTrue(builds.all { b -> b.steps.size in 3..4 && b.steps.first().add == null && b.steps.drop(1).all { it.add?.meaning("en")?.isNotBlank() == true } })
    }

    @Test fun `riddles told by Stari Janez, of a word or a villager`() {
        val d = of(DrillKind.RIDDLE)
        assertEquals("janez", d.teller)
        assertTrue(d.riddles.size >= 40)
        assertTrue(d.riddles.all { (it.word != null) != (it.villager != null) && it.clues.size >= 2 })
    }

    @Test fun `a drill of a kind this app doesn't know, or without an id, is left out, and parts it can't read too`() {
        fun parse(raw: String) = Drills.parse(json.parseToJsonElement(raw) as JsonObject)
        assertNull(parse("""{"id": "x", "kind": "quiz"}"""))
        assertNull(parse("""{"kind": "riddle"}"""))
        val d = parse("""{"id": "x", "kind": "transform", "sets": [
            {"id": "a", "rule": "tozilnik", "do": {"en": "I see"}, "items": [{"from": {"sl": "To je Micka."}, "to": {"sl": "Vidim Micko."}}, {"from": {"en": "no Slovene"}, "to": {"sl": "x"}}]},
            {"id": "b", "do": {"en": "no rule"}, "items": [{"from": {"sl": "a"}, "to": {"sl": "b"}}]}
        ]}""")!!
        assertEquals(listOf("a"), d.transforms.map { it.id })
        assertEquals(1, d.transforms.single().items.size)
        assertEquals("A1", d.transforms.single().level)
    }

    @Test fun `the bridge's drills first, the bundled ones it lacks after, and with an older bridge the bundled`() {
        val mine = Drill("uganke", DrillKind.RIDDLE, "sl", "🕵️", DrillText(mapOf("en" to "Mine")))
        assertEquals(sl, Drills.merge(sl, null))
        val merged = Drills.merge(sl, listOf(mine))
        assertEquals(listOf("uganke") + sl.map { it.id }.filter { it != "uganke" }, merged.map { it.id })
        assertEquals("Mine", merged.first().title.meaning("en"))
    }

    @Test fun `a text as a stable key`() {
        assertEquals("ura-je-pol-stirih", Drills.slug("Ura je pol štirih."))
        assertEquals("v-ponedeljek", Drills.slug("v ponedeljek"))
    }
}
