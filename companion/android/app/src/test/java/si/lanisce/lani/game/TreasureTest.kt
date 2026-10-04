package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Build
import si.lanisce.lani.data.BuildStep
import si.lanisce.lani.data.Drill
import si.lanisce.lani.data.DrillKind
import si.lanisce.lani.data.DrillText
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.GrammarExample
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.data.Riddle
import si.lanisce.lani.data.TransformItem
import si.lanisce.lani.data.TransformSet
import si.lanisce.lani.game.culture.ReadingFile
import si.lanisce.lani.game.culture.ReadingLine
import si.lanisce.lani.game.culture.ReadingQuestion
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.ScenePerson
import java.time.LocalDate

/**
 * The treasure map (companion/GAME.md, "The treasure map"): the offer, the hunt's six stations built from existing
 * content, a station's pass mark, a path blocked and tried again the next day, the treasure dug up, and the level the app
 * goes by until the node has it. A fixture village and content, never the real data.
 */
class TreasureTest {
    private val day = LocalDate.of(2026, 10, 20)
    private val now = Fixtures.noon(day)
    private val s0 = GameEngine.newGame(11, now)

    private fun text(sl: String, en: String) = DrillText(mapOf("sl" to sl, "en" to en))

    private fun page(id: String, level: String, vararg examples: Pair<String, String>) = GrammarPage(
        id, "sl", level, "📖", mapOf("sl" to id, "en" to id), mapOf("en" to "…"),
        examples = examples.map { (sl, en) -> GrammarExample(mapOf("sl" to sl, "en" to en)) },
    )

    private val pages = listOf(
        page("tozilnik", "A1", "Vidim mizo." to "I see the table.", "Imam psa." to "I have a dog.", "Kupim kruh." to "I buy bread."),
        page("pretekli-cas", "A1", "Včeraj sem kuhal kosilo." to "Yesterday I cooked lunch.", "Micka je pekla kruh." to "Micka baked bread."),
        page("kam-tozilnik", "A1", "Grem v trgovino." to "I'm going to the shop.", "Jutri gremo na trg." to "Tomorrow we go to the square."),
        page("dajalnik", "A2", "Dam mami rožo." to "I give mum a flower."),
    )

    /** A scene whose learner's turns test forms: two of A1 rules, one of an A2 rule (not the level's). */
    private val scene = SceneSpec(
        id = "trg", title = "Trg", art = "square", from = listOf("fire"),
        people = listOf(ScenePerson("micka", "Babica Micka", art = "grandma", slot = "left")),
        dialogs = listOf(Dialog("d", listOf(
            DialogLine(who = "micka", sl = "Kam greš?", en = "Where are you going?"),
            DialogLine(choices = listOf(
                DialogChoice("Grem v trgovino.", "I'm going to the shop.", ok = true),
                DialogChoice("Grem v trgovini.", why = "Kam? v + the accusative.", grammar = "kam-tozilnik"),
                DialogChoice("Rad imam kavo.", why = "That's about coffee."),
            )),
            DialogLine(who = "micka", sl = "Kaj vidiš?"),
            DialogLine(choices = listOf(
                DialogChoice("Vidim mizo.", "I see the table.", ok = true),
                DialogChoice("Vidim miza.", why = "The object: the accusative, mizo.", grammar = "tozilnik"),
            )),
            DialogLine(who = "micka", sl = "Komu daš rožo?"),
            DialogLine(choices = listOf(
                DialogChoice("Dam mami rožo.", ok = true),
                DialogChoice("Dam mama rožo.", why = "Komu? the dative.", grammar = "dajalnik"),
            )),
        ))),
    )

    private val reading = ReadingFile(
        id = "pismo", kind = "letter", level = "A1", title = Text(mapOf("sl" to "Pismo", "en" to "A letter")),
        lines = listOf(ReadingLine(Text(mapOf("sl" to "Draga Micka! V soboto pridem. Lep pozdrav, Marko.", "en" to "Dear Micka! I'm coming on Saturday. Best wishes, Marko.")))),
        questions = listOf(
            ReadingQuestion(Text(mapOf("sl" to "Kdo piše?", "en" to "Who writes?")), listOf("Marko", "Micka", "Luka"), Text(mapOf("sl" to "Marko.", "en" to "Marko."))),
            ReadingQuestion(Text(mapOf("sl" to "Kdaj pride?", "en" to "When does he come?")), listOf("V soboto", "V nedeljo", "Jutri"), Text(mapOf("sl" to "V soboto.", "en" to "On Saturday."))),
            ReadingQuestion(Text(mapOf("sl" to "Marko pride v nedeljo.", "en" to "Marko comes on Sunday.")), explain = Text(mapOf("sl" to "V soboto.", "en" to "On Saturday.")), type = "true_false", answer = false),
        ),
    )

    private val drills = listOf(
        Drill("preobrat", DrillKind.TRANSFORM, "sl", "🔄", text("Preobrat", "Transformations"), transforms = listOf(
            TransformSet("preteklik", "pretekli-cas", "A1", text("V preteklik", "Into the past."), listOf(
                TransformItem(text("Micka kuha kosilo.", "Micka is cooking lunch."), text("Micka je kuhala kosilo.", "Micka cooked lunch.")),
                TransformItem(text("Luka pase ovce.", "Luka herds the sheep."), text("Luka je pasel ovce.", "Luka herded the sheep.")),
            )),
            TransformSet("primernik", "primernik", "A2", text("Primerjaj", "Compare."), listOf(
                TransformItem(text("Luka je hiter.", "Luka is fast."), text("Luka je hitrejši.", "Luka is faster.")),
            )),
        )),
        Drill("uganke", DrillKind.RIDDLE, "sl", "🕵️", text("Uganke", "Riddles"), teller = "janez", riddles = listOf(
            Riddle("miza", "A1", listOf(text("Iz lesa sem.", "I'm made of wood."), text("Imam štiri noge.", "I have four legs.")), text("Kaj sem?", "What am I?"), text("Miza!", "A table!")),
            Riddle("kruh", "A1", listOf(text("Babica me peče.", "Grandma bakes me."), text("Zjutraj me ješ.", "You eat me in the morning.")), text("Kaj sem?", "What am I?"), text("Kruh!", "Bread!")),
            Riddle("mleko", "A1", listOf(text("Belo sem.", "I'm white."), text("Daje me krava.", "A cow gives me.")), text("Kaj sem?", "What am I?"), text("Mleko!", "Milk!")),
            Riddle("zvezda", "B1", listOf(text("Svetim ponoči.", "I shine at night."), text("Daleč sem.", "I'm far away.")), text("Kaj sem?", "What am I?"), text("Zvezda!", "A star!")),
        )),
        Drill("gradnja", DrillKind.BUILD, "sl", "🧱", text("Gradnja", "Building"), builds = listOf(
            Build("trgovina", "A1", listOf("vezniki"), listOf(
                BuildStep(null, text("Grem.", "I'm going.")),
                BuildStep(text("", "to the shop"), text("Grem v trgovino.", "I'm going to the shop.")),
                BuildStep(text("", "tomorrow"), text("Jutri grem v trgovino.", "Tomorrow I'm going to the shop.")),
            )),
        )),
    )

    private val exercises = listOf(
        Exercise.Cloze("Nimam ___. (I have no time.)", listOf("časa"), grammar = "tozilnik"),
        Exercise.Choice("Grem ___ trgovino.", listOf("v", "na"), 0, grammar = "kam-tozilnik"),
        Exercise.Choice("Dam ___ rožo.", listOf("mami", "mama"), 0, grammar = "dajalnik"),
    )

    private val content = HuntContent(
        language = "sl", pages = pages, scenes = listOf(scene), readings = listOf(reading), exercises = exercises,
        drills = drills, cards = Fixtures.cards,
    )

    private val ready = Readiness("A1", "A2", listOf("tozilnik"), listOf("tozilnik"), 1, 40, 36, 12, 20)

    /** A village on the path. */
    private val on: GameState get() = Treasure.take(s0, "A1", day, now)

    @Test fun `a station passes at 70 percent of its questions`() {
        assertEquals(4, Treasure.passMark(6))
        assertEquals(4, Treasure.passMark(5))
        assertEquals(3, Treasure.passMark(4))
        assertEquals(2, Treasure.passMark(3))
        assertEquals(1, Treasure.passMark(2))
        assertEquals(1, Treasure.passMark(1))
    }

    @Test fun `the storyteller offers the map once the learner is ready, and it waits`() {
        assertSame(s0, Treasure.offer(s0, ready.copy(secure = emptyList()), "Stari Janez", day, now))
        val s = Treasure.offer(s0, ready, "Stari Janez", day, now)
        assertEquals(HuntStatus.OFFERED, Treasure.status(s.treasure, "A1"))
        assertEquals("A2", s.treasure!!.to)
        assertEquals(day.toString(), s.treasure!!.offered)
        assertTrue(s.log.last().text.contains("Stari Janez"))
        // offered once, and it stays when the signs dip
        assertSame(s, Treasure.offer(s, ready, "Stari Janez", day.plusDays(1), now))
        assertEquals(HuntStatus.OFFERED, Treasure.status(s.treasure, "A1"))
        // taken, the hunt is on
        val t = Treasure.take(s, "A1", day.plusDays(2), now)
        assertEquals(HuntStatus.ON, Treasure.status(t.treasure, "A1"))
        assertEquals(day.plusDays(2).toString(), t.treasure!!.taken)
        assertEquals(day.toString(), t.treasure!!.offered)
    }

    @Test fun `the grammar book starts it before it is offered, and a hunt of another level is none`() {
        val s = on
        assertEquals(HuntStatus.ON, Treasure.status(s.treasure, "A1"))
        assertEquals("", s.treasure!!.offered)
        assertSame(s, Treasure.take(s, "A1", day, now))
        // the tutor moved the level meanwhile: that hunt is none, a new one can begin
        assertEquals(HuntStatus.NONE, Treasure.status(s.treasure, "A2"))
        assertEquals("B1", Treasure.take(s, "A2", day, now).treasure!!.to)
        // nothing after C2
        assertSame(s0, Treasure.take(s0, "C2", day, now))
    }

    @Test fun `the dialog station asks the level's forms, with the line before`() {
        val c = Treasure.challenge(on, Station.DIALOG, content, day)!!
        assertEquals(Treasure.QUESTIONS, c.exercises.size)
        val turns = c.exercises.filterIsInstance<Exercise.Choice>().filter { it.instruction?.contains("What do you say") == true }
        // the A1 turns and the A1 gap exercise; never the dative's (A2)
        assertEquals(setOf("kam-tozilnik", "tozilnik"), turns.mapNotNull { it.grammar }.toSet())
        val kam = turns.first { it.options.contains("Grem v trgovino.") }
        assertEquals(setOf("Grem v trgovino.", "Grem v trgovini."), kam.options.toSet()) // the form's choices only
        assertEquals("Grem v trgovino.", kam.options[kam.answer])
        assertTrue(kam.prompt.startsWith("Babica Micka: »Kam greš?«"))
        assertTrue(kam.prompt.contains("I'm going to the shop."))
        assertTrue(c.exercises.none { it.grammar == "dajalnik" })
        assertEquals(4, c.passMark)
    }

    @Test fun `the grammar station, named exercises and transformations of the level`() {
        val c = Treasure.challenge(on, Station.GRAMMAR, content, day)!!
        assertEquals(Treasure.QUESTIONS, c.exercises.size)
        val chips = c.exercises.filterIsInstance<Exercise.Reorder>().filter { it.grammar == "pretekli-cas" }
        assertEquals(2, chips.size)
        val micka = chips.first { it.prompt.contains("Micka kuha kosilo.") }
        assertEquals(listOf("Micka", "je", "kuhala", "kosilo"), micka.tokens)
        assertEquals(listOf("kuha"), micka.distractors) // the old form among the chips
        assertTrue(micka.prompt.contains("Into the past."))
        // the named exercises of the level (not the A2 one), then cards fill the rest
        assertTrue(c.exercises.any { it is Exercise.Cloze && it.grammar == "tozilnik" })
        assertTrue(c.exercises.none { it.grammar == "dajalnik" || it.grammar == "primernik" })
    }

    @Test fun `the letter is read first, then its questions`() {
        val c = Treasure.challenge(on, Station.LETTER, content, day)!!
        assertNotNull(c.text)
        assertEquals(listOf("Draga Micka!", "V soboto pridem.", "Lep pozdrav, Marko."), c.text!!.paragraphs.single())
        assertEquals(3, c.exercises.size)
        assertEquals(2, c.passMark)
        val tf = c.exercises.last() as Exercise.Choice
        assertEquals(listOf("Drži", "Ne drži"), tf.options)
        assertEquals(1, tf.answer)
    }

    @Test fun `the listening station, heard and two written`() {
        val c = Treasure.challenge(on, Station.LISTENING, content, day)!!
        assertEquals(Treasure.QUESTIONS, c.exercises.size)
        val heard = c.exercises.filterIsInstance<Exercise.Choice>()
        assertEquals(4, heard.size)
        assertTrue(heard.all { it.audio != null && it.options[it.answer] != it.audio })
        assertEquals(2, c.exercises.count { it is Exercise.Dictation })
        assertTrue(c.skills.all { it == Res.WOOD })
        // without a voice it is skipped
        assertNull(Treasure.challenge(on, Station.LISTENING, content.copy(canSpeak = false), day))
    }

    @Test fun `the riddles by the fire, of the level and below`() {
        val c = Treasure.challenge(on, Station.RIDDLE, content, day)!!
        // three A1 riddles (the B1 one left out), a card fills up to four
        assertEquals(Treasure.RIDDLES, c.exercises.size)
        val riddles = c.exercises.filterIsInstance<Exercise.Choice>().filter { it.prompt.startsWith("🕵️") }
        assertEquals(3, riddles.size)
        val miza = riddles.first { it.prompt.contains("Iz lesa sem.") }
        assertEquals("Miza", miza.options[miza.answer])
        assertTrue(miza.prompt.endsWith("Kaj sem?"))
        assertTrue(riddles.none { it.prompt.contains("Svetim ponoči.") })
    }

    @Test fun `the speaking station, only where something listens`() {
        assertNull(Treasure.challenge(on, Station.SPEAKING, content.copy(canListen = false), day))
        val c = Treasure.challenge(on, Station.SPEAKING, content, day)!!
        assertEquals(Treasure.SAYINGS, c.exercises.size)
        val says = c.exercises.filterIsInstance<Exercise.Speak>()
        assertEquals(3, says.size)
        assertTrue(says.all { !it.show && it.say.split(' ').size in 2..6 })
        assertEquals(listOf(Station.LETTER, Station.DIALOG, Station.GRAMMAR, Station.LISTENING, Station.RIDDLE), Treasure.required(canSpeak = true, canListen = false))
    }

    @Test fun `no hunt on, no station`() {
        assertNull(Treasure.challenge(s0, Station.LETTER, content, day))
        assertNull(Treasure.challenge(Treasure.offer(s0, ready, "Janez", day, now), Station.LETTER, content, day))
    }

    @Test fun `a station not passed shows where the path is blocked, and waits for the next day`() {
        val h0 = on
        val (s1, r1) = Treasure.finishStation(h0, Station.DIALOG, 3, 6, listOf("tozilnik", "kam-tozilnik", "tozilnik"), 4, Treasure.required(true, true), day, now)
        assertFalse(r1.won)
        val rec = s1.treasure!!.record(Station.DIALOG)
        assertEquals("", rec.passed)
        assertEquals(listOf("tozilnik", "kam-tozilnik"), rec.weak)
        assertEquals(1, rec.tries)
        assertFalse(Treasure.canTry(s1.treasure!!, Station.DIALOG, day))
        assertTrue(Treasure.canTry(s1.treasure!!, Station.DIALOG, day.plusDays(1)))
        // another station can be tried the same day
        assertTrue(Treasure.canTry(s1.treasure!!, Station.GRAMMAR, day))
        // nothing is lost: the stores are as they were
        assertEquals(h0.resources, s1.resources)
        // passed the next day
        val (s2, r2) = Treasure.finishStation(s1, Station.DIALOG, 5, 6, emptyList(), 3, Treasure.required(true, true), day.plusDays(1), now)
        assertTrue(r2.won)
        val rec2 = s2.treasure!!.record(Station.DIALOG)
        assertEquals(day.plusDays(1).toString(), rec2.passed)
        assertEquals(emptyList<String>(), rec2.weak)
        assertEquals(5 to 6, rec2.right to rec2.of)
        assertFalse(Treasure.canTry(s2.treasure!!, Station.DIALOG, day.plusDays(2)))
        assertTrue(r2.message.contains("5")) // five stations to go
    }

    @Test fun `the pages of the wrong answers, most missed first`() {
        val ex = listOf(
            Exercise.Cloze("a", listOf("a"), grammar = "x"), Exercise.Cloze("b", listOf("b"), grammar = "y"),
            Exercise.Cloze("c", listOf("c"), grammar = "y"), Exercise.Cloze("d", listOf("d")),
        )
        assertEquals(listOf("y", "x"), Treasure.missed(ex, listOf(false, false, false, false)))
        assertEquals(listOf("y"), Treasure.missed(ex, listOf(true, false, true, false)))
    }

    private fun passAll(s: GameState, stations: List<Station>): GameState =
        stations.fold(s) { acc, st -> Treasure.finishStation(acc, st, 4, 5, emptyList(), 3, stations, day, now).first }

    @Test fun `every station passed, the treasure is dug up and the level rises`() {
        val required = Treasure.required(canSpeak = true, canListen = false)
        val almost = passAll(on, required.dropLast(1))
        assertFalse(Treasure.complete(almost.treasure!!, required))
        assertNull(Treasure.dig(almost, required, day, now).second)
        val all = passAll(almost, required.takeLast(1))
        assertTrue(Treasure.complete(all.treasure!!, required))
        val before = all.help
        val (dug, what) = Treasure.dig(all, required, day, now)
        assertNotNull(what)
        assertEquals("A2", what!!.to)
        assertEquals("🧭", what.keepsake.emoji)
        assertEquals(before + Treasure.HELP, dug.help)
        assertEquals(day.toString(), dug.treasures["A2"])
        assertEquals(HuntStatus.FOUND, Treasure.status(dug.treasure, "A2"))
        // the stores got 40 % of their size, as much as they hold
        val caps = GameEngine.attributes(all).caps
        for (r in Res.entries) assertEquals(minOf(caps.getValue(r), all.res(r) + (caps.getValue(r) * 0.4).toInt()), dug.res(r))
        // dug once
        assertNull(Treasure.dig(dug, required, day, now).second)
        // the keepsake: +5 % of everything, for good
        assertEquals(0.05f, Treasure.effect(dug).production.getValue(Res.STONE), 1e-6f)
        assertTrue(GameEngine.attributes(dug).production.getValue(Res.STONE) > GameEngine.attributes(all).production.getValue(Res.STONE))
    }

    @Test fun `the app goes by the new level until the node has it`() {
        val required = Treasure.required(canSpeak = true, canListen = true)
        val (dug, _) = Treasure.dig(passAll(on, required), required, day, now)
        val h = dug.treasure
        assertEquals("A2", Treasure.level("A1", h))
        assertEquals("B1", Treasure.level("B1", h))
        assertEquals("A1", Treasure.level("A1", on.treasure))
        assertSame(dug, Treasure.confirm(dug, "A1"))
        val confirmed = Treasure.confirm(dug, "A2")
        assertTrue(confirmed.treasure!!.confirmed)
        // from then on the node's level is the level (the tutor may correct it)
        assertEquals("A1", Treasure.level("A1", confirmed.treasure))
        assertEquals("id-1", Treasure.reported(dug, "id-1").treasure!!.report)
        // at the new level, the next map can come once the learner is ready for it
        val next = Treasure.offer(confirmed, ready.copy(from = "A2", to = "B1"), "Stari Janez", day.plusDays(20), now)
        assertEquals(HuntStatus.OFFERED, Treasure.status(next.treasure, "A2"))
        assertEquals("B1", next.treasure!!.to)
        assertEquals("B1", Treasure.take(confirmed, "A2", day, now).treasure!!.to)
        // the keepsake stays
        assertEquals(day.toString(), next.treasures["A2"])
    }

    @Test fun `every station falls back on the cards where the content has nothing`() {
        val bare = HuntContent(language = "sl", cards = Fixtures.cards)
        for (st in Station.entries) {
            val c = Treasure.challenge(on, st, bare, day)
            assertNotNull("$st", c)
            assertTrue("$st", c!!.exercises.isNotEmpty())
            assertEquals(c.exercises.size, c.skills.size)
            assertEquals(c.exercises.size, c.cardIds.size)
        }
    }
}
