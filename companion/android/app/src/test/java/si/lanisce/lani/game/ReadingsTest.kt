package si.lanisce.lani.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.culture.Cultures
import java.time.LocalDate
import java.time.ZoneOffset

/** What the chest's things hold to read (companion/GAME.md, "The chest"), and what answering a reading's questions pays. */
class ReadingsTest {
    private val t0 = LocalDate.of(2026, 9, 1).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @After fun home() {
        Cultures.use(Cultures.DEFAULT)
    }

    @Test fun `Micka's recipes, Janez's books, Mojca's textbook and Prešeren's poems are readable`() {
        Cultures.use(Cultures.DEFAULT)
        assertEquals(listOf("frtalja"), Readings.ofTool("micka", 1).map { it.reading.id })
        // the potica recipe took the first one's place in the chest; the frtalja stays to read
        val micka = Readings.ofTool("micka", 2)
        assertEquals(listOf("potica", "frtalja"), micka.map { it.reading.id })
        assertEquals(listOf("Recept za potico", "Recept"), micka.map { it.item.sl })
        assertTrue(micka.all { it.by == "micka" })
        assertEquals(listOf("knjiga-pregovorov", "stara-pratika"), Readings.ofTool("janez", 2).map { it.reading.id })
        assertEquals(listOf("ucbenik-dvojina"), Readings.ofTool("mojca", 2).map { it.reading.id })
        assertEquals(emptyList<String>(), Readings.ofTool("mojca", 1).map { it.reading.id }) // chalk holds nothing to read
        assertEquals(emptyList<String>(), Readings.ofTool("luka", 2).map { it.reading.id })
        assertEquals(emptyList<String>(), Readings.ofTool("nobody", 1).map { it.reading.id })
        val poems = Readings.ofGood("poezije")!!
        assertEquals("zdravljica" to null, poems.reading.id to poems.by) // nobody's voice: the narrator's
        assertNull(Readings.ofGood("potica"))
    }

    @Test fun `the kinds and levels of the packs' readings`() {
        for (id in Cultures.ids) {
            val c = Cultures.load(id)
            for (r in c.readings.values) {
                assertTrue("$id/${r.id}: ${r.kind}", r.kind in Readings.KINDS)
                assertTrue("$id/${r.id}: ${r.level}", r.level in Readings.LEVELS)
            }
        }
        assertEquals(listOf("frtalja", "knjiga-pregovorov", "potica", "stara-pratika", "ucbenik-dvojina", "zdravljica"), Cultures.load("primorska").readings.keys.sorted())
        assertEquals(listOf("frico", "gubana", "libro-antico", "proverbi"), Cultures.load("friuli").readings.keys.sorted())
        val proverbs = Cultures.load("primorska").readings.getValue("knjiga-pregovorov")
        assertTrue(proverbs.lines.size in 10..12)
        assertTrue(proverbs.lines.all { it.means != null })
        val potica = Cultures.load("primorska").readings.getValue("potica")
        assertEquals("A2" to "B1", Cultures.load("primorska").readings.getValue("frtalja").level to potica.level)
        assertEquals(listOf("Testo", "Nadev", "Za premaz"), potica.ingredients.mapNotNull { it.part?.by?.get("sl") })
    }

    @Test fun `friuli's grandmother gives frico and gubana, the storyteller his books`() {
        Cultures.use("friuli")
        assertEquals(listOf("gubana", "frico"), Readings.ofTool("rosa", 2).map { it.reading.id })
        assertEquals(listOf("proverbi", "libro-antico"), Readings.ofTool("bepi", 2).map { it.reading.id })
        assertEquals("friuli/gubana", Readings.key("gubana"))
    }

    @Test fun `lakeland's grandmother gives tatie pot and sticky toffee pudding, the storyteller his books, the teacher her page`() {
        Cultures.use("lakeland")
        try {
            assertEquals(listOf("counting-sheep", "plurals", "sayings", "sticky-toffee-pudding", "tatie-pot"), Cultures.current.readings.keys.sorted())
            assertEquals(listOf("sticky-toffee-pudding", "tatie-pot"), Readings.ofTool("maggie", 2).map { it.reading.id })
            assertEquals(listOf("sayings", "counting-sheep"), Readings.ofTool("wilf", 2).map { it.reading.id })
            assertEquals(listOf("plurals"), Readings.ofTool("hartley", 2).map { it.reading.id })
            assertEquals("lakeland/tatie-pot", Readings.key("tatie-pot"))
        } finally {
            Cultures.use(Cultures.DEFAULT)
        }
    }

    @Test fun `answering a reading's questions pays wisdom as answers do, once`() {
        Cultures.use(Cultures.DEFAULT)
        val s = GameEngine.newGame(3, t0).copy(resources = mapOf(Res.WISDOM to 10), morale = 60)
        val verdicts = listOf(Verdict.CORRECT, Verdict.WRONG)
        val (s1, paid) = Readings.finish(s, "frtalja", verdicts)
        // what two answers pay anywhere (the day's fresh answers in full)
        assertEquals(GameEngine.earn(s, verdicts.map { Res.WISDOM to it }).second, paid)
        assertTrue(paid.getValue(Res.WISDOM) > 0)
        assertEquals(setOf("primorska/frtalja"), s1.read)
        assertTrue(Readings.done(s1, "frtalja"))
        assertFalse(Readings.done(s1, "potica"))
        assertEquals(s.answersToday + 2, s1.answersToday)
        // again: nothing
        assertEquals(s1 to emptyMap<Res, Int>(), Readings.finish(s1, "frtalja", listOf(Verdict.CORRECT, Verdict.CORRECT)))
        // no answers, no pay, not read
        assertEquals(s to emptyMap<Res, Int>(), Readings.finish(s, "potica", emptyList()))
        // the same reading in another culture is another reading
        Cultures.use("friuli")
        assertFalse(Readings.done(s1, "frtalja"))
    }

    @Test fun `looking back at the text takes a little of the pay, the reading still read`() {
        Cultures.use(Cultures.DEFAULT)
        val s = GameEngine.newGame(3, t0).copy(resources = mapOf(Res.WISDOM to 10), morale = 60)
        val verdicts = List(5) { Verdict.CORRECT }
        val full = Readings.finish(s, "frtalja", verdicts).second.getValue(Res.WISDOM)
        val (s1, paid) = Readings.finish(s, "frtalja", verdicts, cost = 30)
        assertEquals(Peeks.cut(mapOf(Res.WISDOM to full), 30), paid)
        assertTrue(paid.getValue(Res.WISDOM) < full)
        assertTrue(Readings.done(s1, "frtalja"))
        assertEquals(s.answersToday + 5, s1.answersToday)
    }

    // --- the reading corner ------------------------------------------------------------------------------------------

    @Test fun `every pack bundles its readings for the corner, what the chest holds among them, each checked`() {
        for (id in Cultures.ids.filter { Cultures.manifest(it)?.status == "complete" }) {
            val library = Cultures.library(id)
            assertTrue("$id: ${Cultures.load(id).readings.keys - library.map { it.id }.toSet()}", library.map { it.id }.containsAll(Cultures.load(id).readings.keys))
            for (r in library) assertEquals("$id/${r.id}", emptyList<String>(), Cultures.check(id, r))
        }
        assertTrue(Cultures.library("primorska").map { it.id }.containsAll(listOf("frtalja", "knjiga-pregovorov", "potica", "stara-pratika", "ucbenik-dvojina", "zdravljica")))
    }

    @Test fun `Jan's corner has readings at A1, with every question type and new words, and the chest's have theirs`() {
        val lib = Cultures.library("primorska")
        val a1 = lib.filter { it.level == "A1" }
        assertEquals(listOf("jutro-v-vasi", "pismo-od-marka"), a1.map { it.id }.sorted())
        for (r in a1) {
            assertEquals(r.id, Readings.QUESTION_TYPES.toSet(), r.questions.mapNotNull { it.type }.toSet())
            assertTrue(r.id, r.words.size in 4..Readings.MAX_WORDS)
            assertTrue(r.id, Readings.length(r, si.lanisce.lani.l10n.Lang.SL) in 40..80)
        }
        // every reading the chest holds offers words, and a statement to say true or false (the potica a word in context)
        for (r in Cultures.load("primorska").readings.values) assertTrue(r.id, r.words.isNotEmpty() && r.questions.all { it.type != null })
        assertEquals(listOf("burja"), lib.filter { it.level == "A2" && it.id !in Cultures.load("primorska").readings }.map { it.id })
    }

    @Test fun `a reading's length is its words to read, and about how long they take at its level`() {
        val frtalja = Cultures.load("primorska").readings.getValue("frtalja")
        val words = Readings.length(frtalja, si.lanisce.lani.l10n.Lang.SL)
        // the intro, the ingredients and the steps; not the title nor the servings
        assertEquals(Readings.texts(frtalja, si.lanisce.lani.l10n.Lang.SL).sumOf { it.split(" ").size }, words)
        assertTrue(words in 80..120)
        assertEquals(listOf(60, 80, 100, 120), Readings.LEVELS.map(Readings::pace))
        assertEquals(1 to 2, Readings.minutes(60, "A1") to Readings.minutes(61, "A1"))
    }

    @Test fun `the corner, the learner's level first, then a step up, the easier, the harder, the unread before the read`() {
        assertEquals(Readings.Fit.AT, Readings.fit("A1", "A1"))
        assertEquals(Readings.Fit.NEXT, Readings.fit("A2", "A1"))
        assertEquals(Readings.Fit.HARDER, Readings.fit("B1", "A1"))
        assertEquals(Readings.Fit.EASIER, Readings.fit("A1", "B1"))
        assertEquals(Readings.Fit.AT, Readings.fit("B2", "C1")) // above B2: the B2 readings are theirs
        val all = Cultures.library("primorska")
        val order = Readings.corner(all, "A2", read = setOf("frtalja"), lang = si.lanisce.lani.l10n.Lang.SL)
        val levels = order.map { it.level }
        assertEquals(levels.sortedBy { Readings.fit(it, "A2").ordinal }, levels)
        val a2 = order.filter { it.level == "A2" }
        assertEquals("frtalja", a2.last().id) // read: after the unread of its level
        assertEquals(all.size, order.size)
        assertEquals("🥣" to "📖", Readings.Readable.of(Cultures.load("primorska").readings.getValue("potica")).item.emoji to Readings.emojiOf("story"))
    }
}
