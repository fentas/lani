package si.lanisce.lani.game.scene

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
 * The story notebook (companion/SCENES.md, "The story notebook"): an entry a story heard, in the order they were first
 * heard, a continuation in its story's entry, a long story's evenings each under its day; the notes' text where a story
 * has them, else its telling written down without the teller's asides and the learner's turns; the sketches where they go.
 */
class NotebookTest {
    private val pair = L10n.pair
    private val day = LocalDate.of(2026, 9, 28)

    @After fun restore() {
        L10n.pair = pair
    }

    private val fire by lazy { StoryFixtures.campfire() }
    private val stories by lazy { StoryBooks.stories(listOf(fire)) }
    private fun story(id: String) = stories.first { it.id == id }

    /** [heard] after [t] was heard on [on]. */
    private fun hear(heard: Map<String, StoryHeard>, t: StoryTonight, on: LocalDate): Map<String, StoryHeard> = Stories.heard(heard, t, on)

    @Test fun `an entry a story heard, in the order they were first heard, a continuation in its story's`() {
        assertEquals(emptyList<NotebookEntry>(), Notebook.entries(stories, emptyMap()))
        val z = story("zlatorog"); val k = story("martin-krpan"); val m = story("kralj-matjaz"); val b = story("bogatinovo-zlato")
        var h = hear(emptyMap(), StoryTonight(k, 0, "A1", false), day)
        h = hear(h, StoryTonight(k, 1, "A1", false), day.plusDays(1))
        h = hear(h, StoryTonight(m, 0, "A1", false), day.plusDays(2))
        // Zlatorog comes first in the rotation, but was heard last: the notebook is in the order they were heard
        h = hear(h, StoryTonight(z, 0, "A2", false), day.plusDays(3))
        val entries = Notebook.entries(stories, h)
        assertEquals(listOf("martin-krpan", "kralj-matjaz", "zlatorog"), entries.map { it.id })
        assertEquals(listOf(day, day.plusDays(1), null, null), entries[0].days)
        assertEquals(day, entries[0].first)
        // its continuation, begun: in Zlatorog's entry, under its own day
        h = hear(h, StoryTonight(b, 0, "A1", false), day.plusDays(9))
        val again = Notebook.entries(stories, h)
        assertEquals(listOf("martin-krpan", "kralj-matjaz", "zlatorog"), again.map { it.id })
        assertEquals(listOf(day.plusDays(3), day.plusDays(9)), again.last().days)
        val evenings = Notebook.evenings(again.last(), "A2", "en")
        assertEquals(listOf(null, b.title), evenings.map { it.title })
        // heard at A1 only: written at A1 in an entry opened at A2
        assertEquals(listOf("A2", "A1"), evenings.map { it.level })
        // the same day: the rotation's order
        val same = hear(hear(emptyMap(), StoryTonight(m, 0, "A1", false), day), StoryTonight(z, 0, "A1", false), day)
        assertEquals(listOf("zlatorog", "kralj-matjaz"), Notebook.entries(stories, same).map { it.id })
    }

    @Test fun `a long story's evenings, each under its day, those to come locked`() {
        val k = story("martin-krpan")
        val h = hear(hear(emptyMap(), StoryTonight(k, 0, "A1", false), day), StoryTonight(k, 1, "A1", false), day.plusDays(2))
        val e = Notebook.entries(stories, h).single()
        val evenings = Notebook.evenings(e, "A1", "en")
        assertEquals(listOf(false, false, true, true), evenings.map { it.locked })
        assertEquals(listOf(day, day.plusDays(2), null, null), evenings.map { it.day })
        assertEquals("Krpan in cesar · Krpan and the emperor", evenings.first().title)
        assertTrue(evenings.drop(2).all { it.blocks.isEmpty() })
        assertTrue(evenings.take(2).all { ev -> ev.blocks.any { it is NoteBlock.Text } })
    }

    @Test fun `a village from before the notebook kept the last evening's day, the others counted back`() {
        val k = story("martin-krpan")
        val old = mapOf("martin-krpan" to StoryHeard(told = 1, level = "A1", on = "2026-09-20"))
        val e = Notebook.entries(stories, old).single()
        assertEquals((17..20).map { LocalDate.of(2026, 9, it) }, e.days)
        // under way: its evenings so far
        val going = mapOf("martin-krpan" to StoryHeard(chapter = 2, level = "A1", on = "2026-09-20"))
        assertEquals(listOf(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 20), null, null), Notebook.entries(stories, going).single().days)
        // heard again: the days it was first heard stay
        val retold = hear(old, StoryTonight(k, 0, "A2", true), day)
        assertEquals(mapOf(1 to "2026-09-17", 2 to "2026-09-18", 3 to "2026-09-19", 4 to "2026-09-20"), retold.getValue("martin-krpan").days)
    }

    @Test fun `a telling written down, its prose without the asides and the turns`() {
        val z = story("zlatorog")
        val p = Notebook.prose(z.levels.getValue("A1"), "sl", "en")
        assertEquals(
            listOf(
                DialogReply("Visoko v gorah, pod Triglavom, živi bel gams. Ime mu je Zlatorog. Z njim živijo bele vile.", "High in the mountains, under Triglav, lives a white chamois. His name is Zlatorog. The white fairies live with him."),
                DialogReply("Nekega dne pride v gore lovec. Hoče zlato. Lovec ustreli Zlatoroga.", "One day a hunter comes into the mountains. He wants gold. The hunter shoots Zlatorog."),
            ),
            p.paragraphs.take(2),
        )
        assertEquals(3, p.paragraphs.size)
        assertTrue(p.paragraphs.last().sl.endsWith("Lovec ne vidi nič in pade v prepad."))
        // the greeting's paragraph is gone: a picture after the telling's second paragraph follows the first written one
        assertEquals(listOf(0, 0, 1, 2, 3), p.upTo.toList())
        // an aside a sentence opens with goes, the rest stays
        assertEquals("Zlatorogovi rogovi so ključ do zaklada.", Notebook.clean("Veš, zlatorogovi rogovi so ključ do zaklada.", "sl"))
        assertNull(Notebook.clean("Povej mi, fant: kaj zraste, kjer pade kri?", "sl"))
        assertNull(Notebook.clean("Dimmi, piccolo: dove vivono le agane?", "it"))
        assertNull(Notebook.clean("Heute Abend erzähle ich dir eine Sage.", "de"))
        assertNull(Notebook.clean("Good evening, lad. Did you help Granny Maggie today?", "en"))
        // what is said in the story, in quotes, is the story's
        assertEquals("»Kdo me kliče?« je zaklical.", Notebook.clean("»Kdo me kliče?« je zaklical.", "sl"))
        assertEquals("'A new coat, a new hat, the hob will work no more for that!'", Notebook.clean("'A new coat, a new hat, the hob will work no more for that!'", "en"))
        assertFalse(Notebook.aside("Un piccolo paese era sulla collina.", "it"))
        assertFalse(Notebook.aside("Das Mädchen war eine kleine Fee.", "de"))
    }

    @Test fun `every curated telling written down has no aside, no turn and no reply, and keeps the story`() {
        val packs = listOf("primorska" to LangPair(Lang.SL, Lang.EN), "friuli" to LangPair(Lang.IT, Lang.SL), "kaernten" to LangPair(Lang.DE, Lang.SL), "lakeland" to LangPair(Lang.EN, Lang.DE))
        var tellings = 0
        for ((culture, p) in packs) {
            for (s in StoryBooks.stories(listOf(StoryFixtures.campfire(culture, p)))) for ((c, part) in s.parts.withIndex()) for ((lv, t) in part.levels) {
                val prose = Notebook.prose(t, s.language, p.base.code)
                val at = "${s.id} ${c + 1} $lv"
                assertTrue(at, prose.paragraphs.isNotEmpty())
                val said = prose.paragraphs.joinToString(" ") { it.sl }
                val words = said.split(" ").size
                val told = t.lines.filter { it.choices.isEmpty() }.joinToString(" ") { it.sl.orEmpty() }.split(" ").size
                assertTrue("$at: $words of $told words kept", words * 2 >= told)
                for (para in prose.paragraphs) {
                    assertTrue(at, para.en.isNotBlank())
                    for (x in StoryBooks.sentences(para.sl)) assertFalse("$at: $x", Notebook.aside(x, s.language))
                    // (its translation goes with it, sentence for sentence: "Your son is crying." is what »Tvoj sin joka.« means)
                }
                // (what the teller says himself too, "Martin Krpan!", is his)
                val his = t.lines.filter { it.choices.isEmpty() }.joinToString(" ") { it.sl.orEmpty() }
                val turns = t.lines.flatMap { it.choices }.flatMap { ch -> listOfNotNull(ch.sl, ch.reply?.sl) }.filter { it.length > 12 && it !in his }
                for (x in turns) assertFalse("$at: $x", x in said)
                assertEquals(at, StoryBooks.paragraphs(t).size + 1, prose.upTo.size)
                tellings++
            }
        }
        assertEquals(112, tellings)
    }

    @Test fun `every curated telling is written down in its notes, in the learner's pair, its sketches placed`() {
        val packs = listOf("primorska" to LangPair(Lang.SL, Lang.EN), "friuli" to LangPair(Lang.IT, Lang.SL), "kaernten" to LangPair(Lang.DE, Lang.SL), "lakeland" to LangPair(Lang.EN, Lang.DE))
        var notes = 0
        for ((culture, p) in packs) for (s in StoryBooks.stories(listOf(StoryFixtures.campfire(culture, p)))) for ((c, part) in s.parts.withIndex()) for (lv in part.levels.keys) {
            val at = "${s.id} ${c + 1} $lv"
            val note = s.noteOf(c, lv)
            assertTrue("$at: no notes", note != null)
            note!!
            assertTrue(at, note.text.size in 2..10 && note.text.all { it.sl.isNotBlank() && it.en.isNotBlank() && it.sl != it.en })
            assertEquals(at, s.picturesOf(c).size, note.after.size)
            // written down, not told: no question to the listener, not his name
            for (t in note.text) assertFalse("$at: ${t.sl}", Regex("(?<![\\p{L}])Jan(?![\\p{L}])").containsMatchIn(t.sl) && s.id != "pegam-in-lambergar")
            val heard = Stories.heard(emptyMap(), StoryTonight(s, c, lv, false), day)
            val entry = Notebook.entries(listOf(s), heard).single()
            val ev = Notebook.evenings(entry, lv, p.base.code)[c]
            assertEquals(at, note.text.map { it.sl }, ev.blocks.filterIsInstance<NoteBlock.Text>().map { it.said })
            assertEquals(at, s.picturesOf(c).size, ev.blocks.count { it is NoteBlock.Sketch })
            notes++
        }
        assertEquals(112, notes)
    }

    @Test fun `the notes' text where a story has them, its sketches after the paragraphs they say`() {
        val z = story("zlatorog")
        fun t(n: Int) = DialogReply("Odstavek $n.", "Paragraph $n.")
        val note = StoryNote(level = "A1", text = (1..4).map(::t), after = listOf(1, 1, 4))
        val with = z.copy(notes = listOf(note))
        val e = Notebook.entries(listOf(with), hear(emptyMap(), StoryTonight(with, 0, "A1", false), day)).single()
        val blocks = Notebook.evenings(e, "A1", "en").single().blocks
        assertEquals(
            listOf("Odstavek 1.", "sketch 2", "sketch 3", "Odstavek 2.", "Odstavek 3.", "Odstavek 4.", "sketch 4"),
            blocks.map { if (it is NoteBlock.Sketch) "sketch ${it.picture.after}" else (it as NoteBlock.Text).said },
        )
        assertEquals("Paragraph 1.", (blocks.first() as NoteBlock.Text).meant)
        // a note that doesn't say where: each picture after the paragraph its own `after` names, the last at most
        val loose = z.copy(notes = listOf(note.copy(after = emptyList(), text = (1..3).map(::t))))
        val e2 = Notebook.entries(listOf(loose), hear(emptyMap(), StoryTonight(loose, 0, "A1", false), day)).single()
        assertEquals(
            listOf("Odstavek 1.", "Odstavek 2.", "sketch 2", "Odstavek 3.", "sketch 3", "sketch 4"),
            Notebook.evenings(e2, "A1", "en").single().blocks.map { if (it is NoteBlock.Sketch) "sketch ${it.picture.after}" else (it as NoteBlock.Text).said },
        )
        // at a level without notes: the telling written down, the sketches after what their paragraphs became
        val a2 = hear(emptyMap(), StoryTonight(with, 0, "A2", false), day)
        val written = Notebook.evenings(Notebook.entries(listOf(with), a2).single(), "A2", "en").single().blocks
        assertTrue((written.first() as NoteBlock.Text).said.startsWith("Nekoč je v Trenti živel mlad lovec."))
        assertEquals(3, written.count { it is NoteBlock.Sketch })
    }

    @Test fun `a story file's notes are read in the learner's pair`() {
        val file = StoryFixtures.served("friuli", "it").first() as kotlinx.serialization.json.JsonObject
        fun o(vararg p: Pair<String, String>) = kotlinx.serialization.json.JsonObject(p.associate { it.first to kotlinx.serialization.json.JsonPrimitive(it.second) })
        val para = o("it" to "C'erano una volta le agane.", "sl" to "Nekoč so bile agane.", "en" to "Once there were the agane.", "de" to "Es waren einmal die Agane.")
        val note = kotlinx.serialization.json.JsonObject(
            mapOf(
                "level" to kotlinx.serialization.json.JsonPrimitive("A1"),
                "after" to kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(1), kotlinx.serialization.json.JsonPrimitive(1))),
                "text" to kotlinx.serialization.json.JsonArray(listOf(para)),
            ),
        )
        val story = kotlinx.serialization.json.JsonObject(file + ("notes" to kotlinx.serialization.json.JsonArray(listOf(note))))
        val scene = StoryFixtures.obj(java.io.File(StoryFixtures.companion, "cultures/friuli/scenes/al-fuoco.json"))
        L10n.pair = LangPair(Lang.IT, Lang.SL)
        val s = parseScene(kotlinx.serialization.json.JsonObject(scene + ("stories" to kotlinx.serialization.json.JsonArray(listOf(story)))).toString()).stories.single()
        assertEquals(StoryNote(level = "A1", text = listOf(DialogReply("C'erano una volta le agane.", "Nekoč so bile agane.")), after = listOf(1, 1)), s.noteOf(0, "A1"))
        assertNull(s.noteOf(0, "A2"))
    }
}
