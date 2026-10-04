package si.lanisce.lani.game.scene

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.time.LocalDate

/**
 * What the learner has of each story heard (companion/SCENES.md, "The story notebook"): a book for every story heard, as far
 * as it was heard, at the levels it was heard at; its paragraphs the teller's lines between the turns; the turns as the
 * questions of "🎯 Preveri se"; a continuation in the book of the story it goes on from.
 */
class StoryBooksTest {
    private val pair = L10n.pair
    private val day = LocalDate.of(2026, 9, 27)

    @After fun restore() {
        L10n.pair = pair
    }

    private val fire by lazy { StoryFixtures.campfire() }
    private val stories by lazy { StoryBooks.stories(listOf(fire)) }
    private fun story(id: String) = stories.first { it.id == id }

    /** [heard] after the learner heard [t] … on the evenings one after the other. */
    private fun hear(heard: Map<String, StoryHeard>, vararg t: StoryTonight): Map<String, StoryHeard> =
        t.fold(heard) { h, x -> Stories.heard(h, x, day) }

    @Test fun `a story becomes a book once it is heard, on its teller's shelf`() {
        assertEquals(emptyList<StoryBook>(), StoryBooks.shelf(stories, emptyMap()))
        val heard = hear(emptyMap(), StoryTonight(story("zlatorog"), 0, "A1", retold = false))
        val shelf = StoryBooks.shelf(stories, heard)
        assertEquals(listOf("zlatorog"), shelf.map { it.id })
        val b = shelf.single()
        assertEquals(listOf("A1"), b.levels)
        assertTrue(b.complete)
        assertEquals(1, b.heard)
        // the story's scenes: every story once, in the rotation's order
        assertEquals(stories.map { it.id }, StoryBooks.stories(listOf(fire, fire)).map { it.id })
    }

    @Test fun `a book's paragraphs are the teller's lines between the turns, and its turns are its questions`() {
        val b = StoryBooks.shelf(stories, hear(emptyMap(), StoryTonight(story("zlatorog"), 0, "A1", retold = false))).single()
        val paragraphs = StoryBooks.paragraphs(story("zlatorog").levels.getValue("A1"))
        assertEquals(4, paragraphs.size)
        assertEquals("Dober večer, Jan. Sedi k ognju. Nocoj ti povem zgodbo.", paragraphs.first().single().sl)
        // the learner's turns aren't told, nor the teller's replies to them
        val told = paragraphs.flatMap { p -> p.map { it.sl.orEmpty() } }
        assertTrue(told.none { it.startsWith("Dober večer! Prosim") || it.startsWith("Dobro. Poslušaj") })
        assertTrue("Lovec ustreli Zlatoroga." in told)
        // its turns, asked with the teller's line before them, the right choice the answer
        val q = StoryBooks.questions(b, "A1")
        assertEquals(4, q.size)
        assertEquals("Dober večer, Jan. Sedi k ognju. Nocoj ti povem zgodbo.", q.first().ask)
        assertEquals("Good evening, Jan. Sit by the fire. Tonight I will tell you a story.", q.first().askBase)
        assertEquals("Rdeča roža.", q.last().answer.sl)
        assertEquals("Povej mi, fant: kaj zraste, kjer pade kri?", q.last().ask)
        assertTrue(q.all { it.answer.ok && it.answer in it.options && it.right(it.answer) && it.options.filterNot { o -> o.ok }.none(it::right) })
        // the options in an order of their own, the same every time
        assertEquals(q.map { it.options }, StoryBooks.questions(b, "A1").map { it.options })
        assertEquals(listOf("Rekel je: »Pojdi!« In je šel.", "Kaj?"), StoryBooks.sentences("Rekel je: »Pojdi!« In je šel. Kaj?"))
    }

    @Test fun `a legend under way, the evenings heard in its book, the others still to come`() {
        val krpan = story("martin-krpan")
        val heard = hear(emptyMap(), StoryTonight(krpan, 0, "A1", false), StoryTonight(krpan, 1, "A1", false))
        val b = StoryBooks.shelf(stories, heard).single()
        assertEquals(4, b.parts.size)
        assertEquals(2, b.heard)
        assertFalse(b.complete)
        assertEquals(listOf(setOf("A1"), setOf("A1"), emptySet(), emptySet()), b.parts.map { it.heardAt })
        // the questions of the evenings heard only
        val q = StoryBooks.questions(b, "A1")
        val turns = krpan.parts.take(2).sumOf { p -> p.levels.getValue("A1").lines.count { it.choices.isNotEmpty() } }
        assertEquals(turns, q.size)
    }

    @Test fun `a story retold a level up offers both levels`() {
        val z = story("zlatorog")
        val once = hear(emptyMap(), StoryTonight(z, 0, "A1", false))
        val twice = hear(once, StoryTonight(z, 0, "A2", true))
        val b = StoryBooks.shelf(stories, twice).single()
        assertEquals(listOf("A1", "A2"), b.levels)
        assertEquals("A2", StoryBooks.openAt(b, "A2"))
        assertEquals("A1", StoryBooks.openAt(b, "A1"))
        // a learner at B1 opens the highest it was heard at
        assertEquals("A2", StoryBooks.openAt(b, "B1"))
        assertTrue(StoryBooks.questions(b, "A1") != StoryBooks.questions(b, "A2"))
        // at a level it wasn't heard at: nothing to ask
        assertEquals(0, StoryBooks.questions(b, "B1").size)
        // a village from before the books (no levels kept): the level it was last heard at
        val old = StoryBooks.shelf(stories, mapOf("zlatorog" to StoryHeard(told = 1, level = "A2"))).single()
        assertEquals(listOf("A2"), old.levels)
    }

    @Test fun `a continuation joins the book of the story it goes on from, once begun`() {
        val krpan = story("martin-krpan")
        val sequel = story("povodni-moz").copy(id = "krpan-se-vrne", title = "Martin Krpan se vrne · Martin Krpan returns", continues = "martin-krpan", source = "tutor")
        val all = stories + sequel
        val krpanHeard = hear(emptyMap(), *(0 until 4).map { StoryTonight(krpan, it, "A1", false) }.toTypedArray())
        // not begun: Krpan's book as it was
        assertEquals(4, StoryBooks.shelf(all, krpanHeard).single().parts.size)
        val both = hear(krpanHeard, StoryTonight(sequel, 0, "A1", false))
        val shelf = StoryBooks.shelf(all, both)
        assertEquals(listOf("martin-krpan"), shelf.map { it.id })
        val b = shelf.single()
        assertEquals(5, b.parts.size)
        assertTrue(b.complete)
        assertEquals("krpan-se-vrne", b.parts.last().story.id)
        assertEquals(krpan.parts.sumOf { p -> p.levels.getValue("A1").lines.count { it.choices.isNotEmpty() } } + sequel.levels.getValue("A1").lines.count { it.choices.isNotEmpty() }, StoryBooks.questions(b, "A1").size)
    }

    @Test fun `a book counts the pictures of the evenings heard`() {
        fun pic(after: Int) = StoryPicture(after = after, bg = "mountains", figures = listOf(PictureThing("hunter")), caption = DialogReply("p$after", "p$after"))
        // a chapter's own: Krpan's second evening
        val krpan = story("martin-krpan").let { it.copy(pictures = listOf(pic(1).copy(chapter = 2), pic(1).copy(chapter = 3))) }
        val kb = StoryBooks.shelf(listOf(krpan), hear(emptyMap(), StoryTonight(krpan, 0, "A1", false), StoryTonight(krpan, 1, "A1", false))).single()
        assertEquals(1, kb.pictures)
    }

    @Test fun `a story file's pictures and continuation are read in the learner's pair, an older one has none`() {
        val file = StoryFixtures.served("friuli", "it").first().jsonObject
        val caption = JsonObject(mapOf("it" to JsonPrimitive("Le agane nel torrente"), "sl" to JsonPrimitive("Agane v potoku"), "en" to JsonPrimitive("The agane in the stream")))
        val thing = JsonObject(mapOf("id" to JsonPrimitive("fairy"), "x" to JsonPrimitive(0.4), "gold" to JsonPrimitive(true)))
        val picture = JsonObject(mapOf("after" to JsonPrimitive(2), "bg" to JsonPrimitive("river"), "figures" to JsonArray(listOf(thing)), "caption" to caption))
        val story = JsonObject(file + mapOf("pictures" to JsonArray(listOf(picture)), "continues" to JsonPrimitive("gli-sbilfs")))
        val o = StoryFixtures.obj(java.io.File(StoryFixtures.companion, "cultures/friuli/scenes/al-fuoco.json"))
        L10n.pair = LangPair(Lang.IT, Lang.SL)
        val s = parseScene(JsonObject(o + ("stories" to JsonArray(listOf(story)))).toString()).stories.single()
        assertEquals("gli-sbilfs", s.continues)
        val p = s.pictures.single()
        assertEquals(Triple(1, 2, "river"), Triple(p.chapter, p.after, p.bg))
        assertEquals(PictureThing("fairy", x = 0.4f, gold = true), p.figures.single())
        assertEquals(DialogReply("Le agane nel torrente", "Agane v potoku"), p.caption)
        assertTrue(Vignettes.problems(p).isEmpty())
        assertEquals(listOf("bg \"desert\"", "figures[0] \"unicorn\""), Vignettes.problems(p.copy(bg = "desert", figures = listOf(PictureThing("unicorn")))))
        // a story without them (as before the books): none, and it goes on from nothing
        val plain = JsonObject(StoryFixtures.served("friuli", "it").first().jsonObject - "pictures")
        val s2 = parseScene(JsonObject(o + ("stories" to JsonArray(listOf(plain)))).toString()).stories.single()
        assertTrue(s2.pictures.isEmpty() && s2.continues == null)
    }
}
