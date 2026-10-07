package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.PlayReviews
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.ui.scene.ISpyResult
import si.lanisce.lani.ui.scene.ISpyRun
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/** «Vidim, vidim» (companion/SCENES.md, "I spy"): who plays, which things and clues, the game's turns, the day, the reviews. */
class ISpyTest {
    private val today = LocalDate.of(2026, 10, 7)

    private fun line(s: String) = ISpyLine(s, "($s)")

    private val lines = ISpyLines(
        "sl", line("Se igrava?"), line("Vidim, vidim …"), line("Še enkrat! Vidim, vidim …"),
        listOf(line("Ne, to ni to."), line("Mrzlo, mrzlo!")), listOf(line("Toplo, toplo!")),
        ISpyLine("Ja, to je {word}! Bravo!", "Yes, the {word}!"), ISpyLine("Ja, to so {word}! Bravo!", "Yes, the {word}!"),
        ISpyLine("To je {word}! Glej, tukaj je.", "It's the {word}!"), ISpyLine("To so {word}! Glej, tukaj so.", "It's the {word}!"),
        line("Hvala!"), line("Vse si našel!"), ISpyLine("Začne se s črko {letter}.", "It starts with {letter}."),
        ISpyLine("Ima {n} {črko|črki|črke|črk}.", "It has {n} {letter|letters}."),
    )

    private fun clue(kind: String, text: String, vararg grammar: String, ref: String? = null) = ISpyClue(kind, line(text), grammar.toList(), ref)

    private val knife = ISpyThing(
        "knife", listOf(
            clue("trait", "Je oster.", "pridevniki-ujemanje"),
            clue("where", "Leži na mizi.", "mestnik", ref = "table"),
            clue("use", "Rabimo ga za kruh.", "tozilnik"),
            clue("use", "Z njim režemo kruh.", "orodnik"),
        ),
    )
    private val table = ISpyThing(
        "table", listOf(
            clue("material", "Je lesena.", "pridevniki-ujemanje"),
            clue("material", "Je iz lesa.", "rodilnik-predlogi"),
            clue("size", "Je velika.", "pridevniki-ujemanje"),
            clue("where", "Stoji sredi kuhinje.", "rodilnik-predlogi"),
            clue("use", "Pri njej jemo.", "mestnik"),
        ),
    )
    private val door = ISpyThing("door", listOf(clue("colour", "So rjava.", "pridevniki-ujemanje"), clue("use", "Odpremo jih.", "tozilnik")), plural = true)
    private val book = ISpyBook("kuhinja", "sl", listOf(knife, table, door).associateBy { it.slot })

    private val a2 = setOf("orodnik", "rodilnik-predlogi", "primernik", "dajalnik")
    private val beginner: (String) -> Boolean = { it in a2 }
    private val nobody: (String) -> Boolean = { false }

    private val scene = SceneSpec(
        id = "kuhinja", title = "V kuhinji", art = "kitchen", from = listOf("hut"), pack = "v-kuhinji",
        objects = listOf(
            SceneObject("knife", "noz", sl = "nož", en = "knife", gender = "m"),
            SceneObject("table", "miza", sl = "miza", en = "table", gender = "f"),
            SceneObject("door", "vrata", pack = "dom-in-hisa", sl = "vrata", en = "door", gender = "n"),
            SceneObject("bread", "kruh", sl = "kruh", en = "bread", gender = "m"),
        ),
        people = listOf(ScenePerson("babica", "Babica Micka", "👵", "grandma", "stove", villager = "micka")),
    )

    // --- the clues ------------------------------------------------------------------------------------------------------

    @Test fun `a beginner gets the clues of the rules introduced, the vaguer first`() {
        val got = ISpy.clues(knife, beginner, { true })
        assertEquals(listOf("Je oster.", "Leži na mizi.", "Rabimo ga za kruh."), got.map { it.line.sl })
        // the instrumental introduced: the use clue that asks for it ("Z njim"), the file's last of the kind
        assertEquals("Z njim režemo kruh.", ISpy.clues(knife, nobody, { true }).last().line.sl)
    }

    @Test fun `the clues grow with the grammar book, one of a kind, at most four, in the kinds' order`() {
        val a1 = ISpy.clues(table, beginner, { true })
        assertEquals(listOf("Je velika.", "Je lesena.", "Pri njej jemo."), a1.map { it.line.sl })
        val a2 = ISpy.clues(table, nobody, { true })
        assertEquals(listOf("Je velika.", "Je iz lesa.", "Stoji sredi kuhinje.", "Pri njej jemo."), a2.map { it.line.sl })
        assertTrue(a2.size <= ISpy.MAX_CLUES)
        assertEquals(ISpy.KINDS.filter { k -> a2.any { it.kind == k } }, a2.map { it.kind })
    }

    @Test fun `a clue naming a thing the picture doesn't show now isn't said`() {
        val got = ISpy.clues(knife, beginner, { it != "table" })
        assertTrue(got.none { it.ref == "table" })
        assertEquals(listOf("Je oster.", "Rabimo ga za kruh."), got.map { it.line.sl })
    }

    @Test fun `a thing with too few clues of its own gets the picture-free ones`() {
        val fallback = ISpy.fallback("kruh", "sl", lines)
        assertEquals(listOf("Začne se s črko K.", "Ima štiri črke."), fallback.map { it.line.sl })
        assertEquals("It has 4 letters.", fallback[1].line.en)
        assertEquals(listOf("Začne se s črko K.", "Ima štiri črke."), ISpy.clues(null, beginner, { true }, fallback).map { it.line.sl })
        // one of its own: one picture-free one more
        val one = ISpyThing("x", listOf(clue("colour", "Je bel.", "pridevniki-ujemanje")))
        assertEquals(listOf("Je bel.", "Začne se s črko K."), ISpy.clues(one, beginner, { true }, fallback).map { it.line.sl })
        // the numbers' forms: one, two, three to four, five and up
        assertEquals("Ima pet črk.", ISpy.fallback("lonec", "sl", lines)[1].line.sl)
        assertEquals("Ima dve črki.", ISpy.fallback("ja", "sl", lines)[1].line.sl)
        // an Italian word with its article: the noun's letter; a word of two: no count
        assertEquals("Začne se s črko F.", ISpy.fallback("il fuoco", "it", lines)[0].line.sl)
        assertEquals(1, ISpy.fallback("kačji pastir", "sl", lines).size)
    }

    // --- the things spied -------------------------------------------------------------------------------------------------

    @Test fun `a game spies things in the picture now, each once, the learner's due words first`() {
        val visible = setOf("knife", "table", "door")
        val due = ReviewCard("vocab_dom-in-hisa_vrata", "vrata", "door", due = today)
        val card: (SceneObject) -> ReviewCard? = { o -> due.takeIf { o.slot == "door" } }
        var doorFirst = 0
        repeat(200) { i ->
            val r = ISpy.rounds(scene, book, lines, visible, beginner, card, emptySet(), emptySet(), today, Random(i))
            assertEquals(3, r.size)
            assertEquals(r.map { it.slot }.toSet().size, r.size)
            assertTrue(r.all { it.slot in visible })
            if (r.first().slot == "door") doorFirst++
        }
        // weighted: the due card most often first (8 of 8 + 1 + 1 by weight, about 80 %)
        assertTrue("$doorFirst of 200", doorFirst > 120)
        // the bread isn't in the picture (nor has clues): never spied
        val r = ISpy.rounds(scene, book, lines, setOf("knife"), beginner, { null }, emptySet(), emptySet(), today, Random(1))
        assertEquals(listOf("knife"), r.map { it.slot })
    }

    @Test fun `what was spied today waits, QA's first thing comes first, a plural word says so`() {
        val visible = setOf("knife", "table", "door")
        val r = ISpy.rounds(scene, book, lines, visible, beginner, { null }, emptySet(), setOf("knife", "table"), today, Random(3), count = 1)
        assertEquals(listOf("door"), r.map { it.slot })
        assertTrue(r.single().plural)
        assertEquals("door", r.single().meaning)
        val forced = ISpy.rounds(scene, book, lines, visible, beginner, { null }, emptySet(), emptySet(), today, Random(3), first = "table")
        assertEquals("table", forced.first().slot)
        // everything spied today: the game spies again rather than not at all
        assertEquals(3, ISpy.rounds(scene, book, lines, visible, beginner, { null }, emptySet(), visible, today, Random(3)).size)
    }

    @Test fun `the weights, due, learning, found before, known, never found`() {
        val card = ReviewCard("c", "nož", "knife", repetitions = 5, lastQuality = 5, due = today.plusDays(9))
        assertEquals(8, ISpy.weight(card.copy(due = today.plusDays(1)), false, today))
        assertEquals(5, ISpy.weight(card.copy(repetitions = 1), false, today))
        assertEquals(3, ISpy.weight(card, true, today))
        assertEquals(2, ISpy.weight(card, false, today))
        assertEquals(3, ISpy.weight(null, true, today))
        assertEquals(1, ISpy.weight(null, false, today))
        assertEquals("hour", ISpy.meaningOf("hour, clock, o'clock"))
        assertEquals("stove", ISpy.meaningOf("stove (the tiled one that heats the house)"))
    }

    // --- who plays --------------------------------------------------------------------------------------------------------

    private val zala = Villager("zala", "Zala", "👧", "child2", "female", speaker = "girl")
    private val nejc = Villager("nejc", "Nejc", "🧒", "child1", "male", speaker = "boy")

    @Test fun `a child of the scene in the picture plays, else one of the village comes by to a free spot`() {
        val withChild = scene.copy(people = scene.people + ScenePerson("otroci", "Otroci", "🧒", "child1", "table", villager = "nejc"))
        val there = ISpy.host(withChild, listOf(PersonInScene("otroci", "child1", "table")), listOf(zala), 7L, today) { if (it == "nejc") nejc else null }
        assertEquals("otroci", there!!.person.id)
        assertEquals("Nejc", there.person.name)
        assertFalse(there.comes)
        // asleep in the picture: not them; Zala comes by, to the door (the kitchen's spots: stove, table, door)
        val asleep = ISpy.host(withChild, listOf(PersonInScene("otroci", "child1", "table", pose = Pose.SLEEP)), listOf(zala), 7L, today)
        assertEquals("zala", asleep!!.person.id)
        assertTrue(asleep.comes)
        assertEquals("door", asleep.person.slot)
        // Micka at the door: the last spot free
        val busy = ISpy.host(scene, listOf(PersonInScene("babica", "grandma", "door")), listOf(zala, nejc), 7L, today)
        assertNotNull(busy)
        assertTrue(busy!!.person.slot in setOf("stove", "table"))
        // the same child all day in the scene
        assertEquals(busy.person.id, ISpy.host(scene, listOf(PersonInScene("babica", "grandma", "door")), listOf(nejc, zala), 7L, today)!!.person.id)
        // no child: nobody plays
        assertNull(ISpy.host(scene, emptyList(), listOf(Villager("micka", "Babica Micka", "👵", "grandma")), 7L, today))
    }

    // --- the day ----------------------------------------------------------------------------------------------------------

    @Test fun `two games a day in a scene, the friendship once a day a child, and the next day anew`() {
        var s = GameState(seed = 7L)
        assertEquals(2, ISpy.gamesLeft(s, "kuhinja", today))
        assertTrue(ISpy.befriends(s, "zala", today))
        s = ISpy.played(s, "kuhinja", listOf("knife", "door"), "zala", today)
        assertEquals(1, ISpy.gamesLeft(s, "kuhinja", today))
        assertEquals(setOf("knife", "door"), ISpy.spied(s, "kuhinja", today))
        assertFalse(ISpy.befriends(s, "zala", today))
        assertTrue(ISpy.befriends(s, "nejc", today))
        s = ISpy.played(s, "kuhinja", listOf("table"), "zala", today)
        assertEquals(0, ISpy.gamesLeft(s, "kuhinja", today))
        assertEquals(2, ISpy.gamesLeft(s, "v-hisi", today))
        // tomorrow
        val next = today.plusDays(1)
        assertEquals(2, ISpy.gamesLeft(s, "kuhinja", next))
        assertTrue(ISpy.befriends(s, "zala", next))
        assertEquals(emptySet<String>(), ISpy.spied(s, "kuhinja", next))
        assertEquals(next.toString(), ISpy.played(s, "kuhinja", listOf("knife"), null, next).ispy!!.on)
    }

    // --- a game -----------------------------------------------------------------------------------------------------------

    private val names = mapOf("knife" to ("nož" to "knife"), "table" to ("miza" to "table"), "door" to ("vrata" to "door"), "bread" to ("kruh" to "bread"))

    private fun run(): ISpyRun {
        val rounds = listOf(
            ISpyRound("knife", "nož", "knife", false, ISpy.clues(knife, beginner, { true })),
            ISpyRound("door", "vrata", "door", true, ISpy.clues(door, beginner, { true })),
        )
        return ISpyRun.start("zala", rounds, lines, names)
    }

    @Test fun `a round, the child's line and a clue, a wrong tap gets a kind reaction and the next clue, the right one a cheer`() {
        var r = run()
        assertEquals(listOf("Vidim, vidim …", "Je oster."), r.said.map { it.sl })
        assertTrue(r.said.all { it.who == "zala" })
        r = r.tap("table")
        assertEquals(listOf("Miza?", "Ne, to ni to.", "Leži na mizi."), r.said.drop(2).map { it.sl })
        assertTrue(r.said[2].wrong && r.said[2].who == null)
        assertEquals("table?", r.said[2].en)
        // the same wrong thing again: a reaction, no clue for it
        r = r.tap("table")
        assertEquals(listOf("Miza?", "Mrzlo, mrzlo!"), r.said.drop(5).map { it.sl })
        assertEquals(2, r.heard)
        // near it: warm
        r = r.tap("bread", near = true)
        assertEquals(listOf("Kruh?", "Toplo, toplo!", "Rabimo ga za kruh."), r.said.drop(7).map { it.sl })
        r = r.tap("knife")
        assertEquals(listOf("Nož?", "Ja, to je nož! Bravo!"), r.said.takeLast(2).map { it.sl })
        assertFalse(r.said.takeLast(2).first().wrong)
        assertEquals("Yes, the knife!", r.said.last().en)
        assertEquals(ISpyRun.Step.SOLVED, r.step)
        assertEquals("knife", r.solved)
        // a tap once solved does nothing
        assertEquals(r, r.tap("door"))
        val res = r.results.single()
        assertEquals(ISpyResult("knife", true, 3, 2), res)
        assertFalse(res.quick)
        assertEquals(Verdict.ALMOST, res.verdict)
    }

    @Test fun `after the last clue the child shows the thing, then the next round, and the end`() {
        var r = run().tap("knife")
        assertTrue(r.results.single().quick)
        assertEquals(Verdict.CORRECT, r.results.single().verdict)
        r = r.next()
        assertEquals(1, r.round)
        assertEquals(listOf("Še enkrat! Vidim, vidim …", "So rjava."), r.said.takeLast(2).map { it.sl })
        r = r.more()
        assertEquals("Odpremo jih.", r.said.last().sl)
        assertFalse(r.moreClues)
        r = r.tap("table")
        assertEquals("To so vrata! Glej, tukaj so.", r.said.last().sl)
        assertEquals(ISpyRun.Step.SOLVED, r.step)
        assertEquals(ISpyResult("door", false, 2, 1), r.results.last())
        assertEquals(Verdict.WRONG, r.results.last().verdict)
        r = r.next()
        assertEquals(ISpyRun.Step.END, r.step)
        assertEquals("Hvala!", r.said.last().sl)
        assertFalse(r.allFound)
        // all found: the other goodbye
        val all = run().tap("knife").next().tap("door").next()
        assertTrue(all.allFound)
        assertEquals("Vse si našel!", all.said.last().sl)
        // giving up shows the thing at once
        assertEquals("To je nož! Glej, tukaj je.", run().reveal().said.last().sl)
        assertFalse(run().reveal().results.single().found)
    }

    // --- the reviews ------------------------------------------------------------------------------------------------------

    @Test fun `a word met in play counts a review once a day, due or nearly, never a fail`() {
        val card = ReviewCard("vocab_v-kuhinji_noz", "nož", "knife", repetitions = 3, lastQuality = 4, due = today)
        assertTrue(PlayReviews.counts(card, today, emptySet()))
        assertTrue(PlayReviews.counts(card.copy(due = today.plusDays(1)), today, emptySet()))
        assertFalse("not due for days", PlayReviews.counts(card.copy(due = today.plusDays(5)), today, emptySet()))
        assertFalse("counted in play today", PlayReviews.counts(card, today, setOf(card.id)))
        assertFalse("reviewed today", PlayReviews.counts(card.copy(lastReviewed = today), today, emptySet()))
        assertFalse("not a word", PlayReviews.counts(card.copy(kind = "phrase"), today, emptySet()))
        assertFalse("no due date: made on the phone by today's pack run", PlayReviews.counts(card.copy(due = null), today, emptySet()))
        assertFalse(PlayReviews.counts(null, today, emptySet()))
        assertEquals(listOf(card.id to 4), PlayReviews.results(listOf(card, card)))
        // the day keeps them; the next day they count again
        var s = PlayReviews.record(GameState(), listOf(card.id), today)
        assertEquals(setOf(card.id), PlayReviews.counted(s, today))
        assertEquals(emptySet<String>(), PlayReviews.counted(s, today.plusDays(1)))
        s = PlayReviews.record(s, listOf("other"), today.plusDays(1))
        assertEquals(listOf("other"), s.playReviews!!.cards)
    }

    // --- the content, as the app reads it --------------------------------------------------------------------------------

    private val ispyDir = listOf(File("../../ispy"), File("../ispy"), File("companion/ispy")).first { it.isDirectory }

    @Test fun `the child's lines read in every language and base`() {
        for (f in ispyDir.listFiles { x -> x.isFile && x.extension == "json" }.orEmpty()) for (base in listOf("sl", "en", "de", "it")) {
            val l = ISpy.parseLines(f.readText(), base)
            assertNotNull("${f.name} in $base", l)
            assertTrue(l!!.found.sl.contains("{word}") && l.show.sl.contains("{word}") && l.letter.sl.contains("{letter}"))
            if (base != l.language) assertTrue("${f.name}: a translation in $base", l.start.en.isNotBlank() && l.start.en != l.start.sl)
        }
        val sl = ISpy.parseLines(File(ispyDir, "sl.json").readText(), "en")!!
        assertEquals("Vidim, vidim nekaj, česar ti ne vidiš …", sl.start.sl)
        assertEquals("I spy with my little eye something …", sl.start.en)
        assertEquals("Ja, to je nož! Bravo!", sl.found.with("nož", "knife").sl)
        val it = ISpy.parseLines(File(ispyDir, "it.json").readText(), "sl")!!
        assertEquals("Se igrava «Vidim, vidim»?", it.offer.en)
    }

    @Test fun `a scene's clues read in the learner's pair`() {
        val k = ISpy.parseBook(File(ispyDir, "scenes/kuhinja.json").readText(), "en")!!
        assertEquals("kuhinja", k.scene)
        val pot = k.things.getValue("pot")
        assertEquals("Je moder.", pot.clues.first().line.sl)
        assertEquals("It's blue.", pot.clues.first().line.en)
        assertEquals("hearth", pot.clues[1].ref)
        assertTrue(k.things.getValue("door").plural)
        assertEquals("clock", k.things.getValue("clock").meaning)
    }
}
