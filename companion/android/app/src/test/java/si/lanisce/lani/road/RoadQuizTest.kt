package si.lanisce.lani.road

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.ClipIndex
import si.lanisce.lani.data.Drill
import si.lanisce.lani.data.DrillKind
import si.lanisce.lani.data.DrillText
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.Riddle
import si.lanisce.lani.game.FormGap
import si.lanisce.lani.game.FormQuestion
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.game.MyWord
import si.lanisce.lani.game.MyWords
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.scene.SceneSpec
import java.time.LocalDate
import si.lanisce.lani.road.QuizControls.Press
import si.lanisce.lani.road.RoadQuiz.Role

/**
 * "❓ Kviz · Quiz" in the car (RoadQuiz, QuizControls): what a question sounds like, what the steering wheel's buttons do
 * at each moment of it, the questions of each kind from what the learner has, and the session's order.
 */
class RoadQuizTest {
    private val index: ClipIndex = mapOf(
        "ena" to mapOf("female" to "/voice/file/ena.mp3"),
        "dve" to mapOf("female" to "/voice/file/dve.mp3"),
        "tri" to mapOf("female" to "/voice/file/tri.mp3"),
        "prav" to mapOf("female" to "/voice/file/prav.mp3"),
        "hvala" to mapOf("female" to "/voice/file/hvala.mp3"),
        "kruh" to mapOf("female" to "/voice/file/kruh.mp3"),
        "mleko" to mapOf("female" to "/voice/file/mleko.mp3"),
        "voda" to mapOf("female" to "/voice/file/voda.mp3"),
        "kaj boš" to mapOf("grandma" to "/voice/file/kaj-bos.mp3"),
        "deset jajc prosim" to mapOf("female" to "/voice/file/jajc.mp3"),
        "deset jajce prosim" to mapOf("female" to "/voice/file/jajce.mp3"),
        "iz lesa sem" to mapOf("grandpa" to "/voice/file/lesa.mp3"),
        "kaj sem" to mapOf("grandpa" to "/voice/file/kaj-sem.mp3"),
        "miza" to mapOf("grandpa" to "/voice/file/miza.mp3"),
        "stol" to mapOf("grandpa" to "/voice/file/stol.mp3"),
        "krava" to mapOf("grandpa" to "/voice/file/krava.mp3"),
        "nimam časa" to mapOf("female" to "/voice/file/casa.mp3"),
    )
    private val clips = ClipLookup.of(index)
    private val today = "2026-10-08"

    private fun clip(text: String, file: String) = Sound.Clip(text, listOf(file))

    // --- how a question sounds, and what the buttons do -----------------------------------------------------------

    private fun question(n: Int = 3) = QuizQuestion(
        "meaning:c1", QuizKind.MEANING, "hvala", listOf(Sound.Prompt("What does it mean?"), Sound.Pause(300), clip("hvala", "hvala.mp3")),
        (0 until n).map { QuizOption("m$it", Sound.Prompt("m$it")) }, 0, listOf(clip("hvala", "hvala.mp3")), "hvala",
    )

    private val numbers = listOf(clip("Ena.", "ena.mp3"), clip("Dve.", "dve.mp3"), clip("Tri.", "tri.mp3"), Sound.Prompt("Four."))

    @Test fun `a question is read, then its options numbered, each with a gap after it, twice, then a moment before the next`() {
        val play = RoadQuiz.compose(RoadQuiz.item(question()), numbers, listOf(2, 0, 1))
        val s = play.item.sounds
        val p = play.parts
        assertEquals(s.size, p.size)
        // the question, a pause
        assertEquals(question().ask + Sound.Pause(RoadQuiz.ASK_GAP_MS), s.take(4))
        assertTrue(p.take(4).all { it.role == Role.ASK })
        // "Ena:" the option heard first (the question's third), its gap; "Dve:" …
        assertEquals(numbers[0], s[4])
        assertEquals(RoadQuiz.Part(Role.NUMBER, 0), p[4])
        assertEquals(Sound.Prompt("m2"), s[6])
        assertEquals(RoadQuiz.Part(Role.TEXT, 0), p[6])
        assertEquals(Sound.Pause(RoadQuiz.OPTION_GAP_MS), s[7])
        assertEquals(RoadQuiz.Part(Role.GAP, 0), p[7])
        assertEquals(numbers[1], s[8])
        assertEquals(Sound.Prompt("m0"), s[10])
        // the second round, after a pause, then the end
        assertEquals(2, p.count { it == RoadQuiz.Part(Role.NUMBER, 0) })
        assertEquals(1, p.count { it.role == Role.BETWEEN })
        assertEquals(RoadQuiz.Part(Role.END, 2), p.last())
        assertEquals(Sound.Pause(RoadQuiz.END_MS), s.last())
        // the order heard: place → the question's option
        assertEquals(2, play.option(0))
        assertEquals(0, play.option(1))
        assertNull(play.option(3))
    }

    @Test fun `next picks the option being read, or the one just read, with a grace into the next one's number`() {
        val p = RoadQuiz.compose(RoadQuiz.item(question()), numbers, listOf(0, 1, 2)).parts
        fun at(part: RoadQuiz.Part, n: Int = 0) = p.withIndex().filter { it.value == part }.map { it.index }[n]
        // during the question: on to the options at once
        assertEquals(Press.Options, QuizControls.next(p, 0, 500))
        assertEquals(Press.Options, QuizControls.next(p, 3, 100))
        // the first option's number and the option: it; its gap after: it
        assertEquals(Press.Pick(0), QuizControls.next(p, at(RoadQuiz.Part(Role.NUMBER, 0)), 0))
        assertEquals(Press.Pick(0), QuizControls.next(p, at(RoadQuiz.Part(Role.TEXT, 0), 1), 900))
        assertEquals(Press.Pick(0), QuizControls.next(p, at(RoadQuiz.Part(Role.GAP, 0)), 1_100))
        // "Dve" just begun: still the first (the grace); later in it: the second
        val dve = at(RoadQuiz.Part(Role.NUMBER, 1))
        assertEquals(Press.Pick(0), QuizControls.next(p, dve, RoadQuiz.GRACE_MS - 1))
        assertEquals(Press.Pick(1), QuizControls.next(p, dve, RoadQuiz.GRACE_MS))
        assertEquals(Press.Pick(1), QuizControls.next(p, at(RoadQuiz.Part(Role.TEXT, 1), 1), 0))
        // between the rounds and at the end: the last one read
        assertEquals(Press.Pick(2), QuizControls.next(p, p.indexOfFirst { it.role == Role.BETWEEN }, 300))
        assertEquals(Press.Pick(2), QuizControls.next(p, p.lastIndex, 700))
        // the second round's first number has no grace into the round before
        assertEquals(Press.Pick(0), QuizControls.next(p, at(RoadQuiz.Part(Role.NUMBER, 0), 1), 0))
        // the second round picks as the first
        assertEquals(Press.Pick(1), QuizControls.next(p, at(RoadQuiz.Part(Role.GAP, 1), 1), 0))
    }

    @Test fun `previous repeats the question, play or pause picks during the options and pauses during the question`() {
        val p = RoadQuiz.compose(RoadQuiz.item(question()), numbers, listOf(0, 1, 2)).parts
        assertEquals(Press.Repeat, QuizControls.previous())
        assertNull(QuizControls.playPause(p, 0, 0))
        assertEquals(Press.Pick(1), QuizControls.playPause(p, p.indexOf(RoadQuiz.Part(Role.GAP, 1)), 0))
        assertEquals(Press.Pick(2), QuizControls.playPause(p, p.lastIndex, 0))
        assertEquals(4, QuizControls.optionsAt(p))
        // a feedback: next goes on, play or pause as ever
        val k = QuizKit.Resolved(numbers, clip("Prav!", "prav.mp3"), Sound.Prompt("No, the right one is:"), List(11) { Sound.Prompt("$it of ten.") })
        val f = RoadQuiz.feedback(question(), true, k)
        assertTrue(f.feedback)
        assertEquals(Press.Skip, QuizControls.next(f.parts, 0, 0))
        assertNull(QuizControls.playPause(f.parts, 0, 0))
    }

    @Test fun `after a pick "Prav!" and the right answer whole, or "Ne, prav je" and it, a summary of ten`() {
        val k = QuizKit.Resolved(numbers, clip("Prav!", "prav.mp3"), Sound.Prompt("No, the right one is:"), List(11) { Sound.Prompt("$it of ten.") })
        val q = question()
        val right = RoadQuiz.feedback(q, true, k).item
        assertEquals(listOf(k.right, Sound.Pause(300)) + q.answer + Sound.Pause(1_500), right.sounds)
        assertEquals("quiz:meaning:c1#right", right.id)
        val wrong = RoadQuiz.feedback(q, false, k).item
        assertEquals(k.wrong, wrong.sounds.first())
        assertEquals("quiz:meaning:c1#wrong", wrong.id)
        assertEquals(Sound.Prompt("7 of ten."), RoadQuiz.summary(7, k).item.sounds[1])
        var score = QuizScore()
        repeat(9) { score = score.add(it % 3 != 0) }
        assertFalse(score.summary)
        score = score.add(true)
        assertTrue(score.summary)
        assertEquals(7, score.right)
    }

    @Test fun `the options heard in the day's order, the right one among them, another day another order`() {
        val q = question(4).copy(options = (0 until 4).map { QuizOption("m$it", Sound.Prompt("m$it")) }, right = 3)
        val days = (1L..20L).map { RoadQuiz.order(q, it) }
        assertTrue(days.all { it.sorted() == listOf(0, 1, 2, 3) })
        assertTrue(days.distinct().size > 3)
        assertTrue(days.map { it.indexOf(3) }.distinct().size > 1)
        // fewer numbers than options: the right one is kept
        assertTrue(days.indices.all { 3 in RoadQuiz.order(q, it.toLong(), 3) })
    }

    @Test fun `the kit plays each phrase from the first alternative on the phone`() {
        val kit = RoadQuiz.kit(QuizWords.EN, clips)
        // "Ena." in the voice store: its clip only; "Štiri." isn't: voiced while getting ready, else the base's
        assertEquals(listOf(Sound.Clip("Ena.", listOf("ena.mp3"))), kit.numbers[0])
        assertEquals(listOf(Sound.Spoken("Štiri.", "female"), Sound.Prompt("Four.")), kit.numbers[3])
        assertEquals(listOf(Sound.Clip("Prav!", listOf("prav.mp3"))), kit.right)
        assertEquals(11, kit.scores.size)
        assertEquals(Sound.Spoken("Sedem od desetih.", "female"), kit.scores[7][0])
        // nothing voiced: the prompts stand in
        val r = kit.resolve { it !is Sound.Spoken }!!
        assertEquals(Sound.Prompt("Four."), r.numbers[3])
        assertEquals(Sound.Prompt("No, the right one is:"), r.wrong)
        assertNull(kit.resolve { false })
    }

    // --- the questions of each kind -----------------------------------------------------------------------------------

    private val scene = SceneSpec(
        id = "trgovina", title = "Trgovina", art = "shop", from = emptyList(), level = "A1",
        people = listOf(ScenePerson("micka", "Micka", art = "grandma", slot = "s1")),
        dialogs = listOf(
            Dialog(
                "jajca",
                listOf(
                    DialogLine(who = "micka", sl = "Kaj boš?", en = "What will you have?"),
                    DialogLine(
                        choices = listOf(
                            DialogChoice("Deset jajc, prosim.", "Ten eggs, please.", ok = true),
                            DialogChoice("Deset jajce, prosim.", grammar = "stevila-samostalniki", why = "After deset, the genitive plural."),
                            DialogChoice("Deset jajci, prosim.", grammar = "dvojina", why = "The dual is for two."),
                        ),
                    ),
                    DialogLine(who = "micka", sl = "Izvoli.", en = "Here you go."),
                    DialogLine(choices = listOf(DialogChoice("Za vrati.", tap = "door", ok = true), DialogChoice("Pod mizo.", tap = "table"))),
                ),
            ),
        ),
    )

    @Test fun `a dialog's turn, its context line in the speaker's voice, what to answer, the right answer and its wrong ones`() {
        val words = MyWords(listOf(MyWord("jajce-card", "jajce", "egg", setOf("jajce", "jajc", "jajci", "jajca"))))
        val cards = mapOf("jajce-card" to ReviewCard("jajce-card", "jajce", "egg", due = LocalDate.parse(today), interval = 4))
        val qs = RoadQuiz.dialogs(listOf(scene), "A1", { false }, words, cards, { _, who -> if (who == "micka") listOf("grandma", "female") else RoadPlay.NARRATOR }, clips)
        // the tap turn isn't one: it's the picture
        val q = qs.single()
        assertEquals("dialog:trgovina/jajca/1", q.id)
        assertEquals(QuizKind.DIALOG, q.kind)
        // "Kaj boš?" in Micka's voice, then: a form turn, so what to say
        assertEquals(Sound.Clip("Kaj boš?", listOf("kaj-bos.mp3")), q.ask[0])
        assertEquals(Sound.Prompt("You want to say: Ten eggs, please."), q.ask.last())
        assertEquals(listOf("Deset jajc, prosim.", "Deset jajce, prosim.", "Deset jajci, prosim."), q.options.map { it.text })
        assertEquals(0, q.right)
        // the voice store's clips, and what it lacks voiced while getting ready
        assertEquals(Sound.Clip("Deset jajc, prosim.", listOf("jajc.mp3")), q.options[0].sound)
        assertEquals(Sound.Spoken("Deset jajci, prosim.", "female"), q.options[2].sound)
        // a wrong pick counts on its page, the right one on the turn's (none named by all: none)
        assertEquals("stevila-samostalniki", q.options[1].page)
        assertEquals("dvojina", q.options[2].page)
        assertNull(q.options[0].page)
        // the learner's word it tests: right with the right one, its form got wrong with the others
        assertEquals(listOf(QuizWord("jajce-card", "jajc", form = true)), q.options[0].words)
        assertEquals(listOf(QuizWord("jajce-card", "jajc", form = true)), q.options[1].words)
        assertEquals(listOf("jajce-card"), q.cards.map { it.id })
        assertEquals("Deset jajc, prosim.", q.answerText)
    }

    @Test fun `a rule not introduced yet isn't asked for, as in the dialogs, and a turn with nothing left to choose isn't a question`() {
        val qs = RoadQuiz.dialogs(listOf(scene), "A1", { it == "dvojina" }, MyWords.NONE, emptyMap(), { _, _ -> RoadPlay.NARRATOR }, clips)
        // the dual's wrong choice goes, and the form's others with it: an echo turn, no question
        assertTrue(qs.isEmpty())
        // a scene above the learner's level isn't asked
        val a2 = RoadQuiz.dialogs(listOf(scene.copy(level = "A2")), "A1", { false }, MyWords.NONE, emptyMap(), { _, _ -> RoadPlay.NARRATOR }, clips)
        assertTrue(a2.isEmpty())
    }

    private val vocab = listOf(
        ReviewCard("c1", "hvala", "thank you", due = LocalDate.parse("2026-10-20")),
        ReviewCard("c2", "kruh", "bread", due = LocalDate.parse(today)),
        ReviewCard("c3", "mleko", "milk"),
        ReviewCard("c4", "voda", "water (to drink)"),
        ReviewCard("c5", "nasvidenje", "goodbye"), // no clip
    )

    @Test fun `a card asks its meaning, or the word for its meaning, the others' as the wrong options, due ones first`() {
        val (meanings, words) = RoadQuiz.cards(vocab, clips)
        // only cards whose Slovene the voice store has; the due one first
        assertEquals(listOf("meaning:c2", "meaning:c1", "meaning:c3", "meaning:c4"), meanings.map { it.id })
        val m = meanings.first()
        assertEquals(listOf(Sound.Prompt("What does it mean?"), Sound.Pause(300), Sound.Clip("kruh", listOf("kruh.mp3"))), m.ask)
        assertEquals(3, m.options.size)
        assertEquals(Sound.Prompt("bread"), m.options[0].sound)
        assertEquals(m.options.size, m.options.map { it.text }.distinct().size)
        assertTrue(m.options.all { it.words == listOf(QuizWord("c2", "kruh")) })
        assertEquals(listOf(Sound.Clip("kruh", listOf("kruh.mp3")), Sound.Pause(300), Sound.Prompt("bread")), m.answer)
        val w = words.first { it.id == "word:c4" }
        assertEquals(listOf(Sound.Prompt("How do you say: water?")), w.ask)
        assertEquals(Sound.Clip("voda", listOf("voda.mp3")), w.options[0].sound)
        assertTrue(w.options.drop(1).all { it.sound is Sound.Clip && it.text != "voda" })
        assertEquals(LocalDate.parse(today), meanings.first().cards.single().card().due)
    }

    @Test fun `a word's form, the sentence with each form, the right one on the form's rule, the card as it is when wrong`() {
        val card = ReviewCard("s1", "sesti", "to sit down", repetitions = 3, lastQuality = 4)
        val f = FormQuestion(
            "sesti", "pres.1sg", "glagoli-sedanjik", FormGap("Jaz ", "sedem", " na klop. (sesti)"), listOf("sedem"),
            listOf("sedim", "sedem", "sede"), "", "",
        )
        val q = RoadQuiz.forms(listOf(card), mapOf("s1" to f), clips).single()
        assertEquals("form:s1/pres.1sg", q.id)
        assertEquals(listOf("Jaz sedem na klop.", "Jaz sedim na klop.", "Jaz sede na klop."), q.options.map { it.text })
        assertTrue(q.options.all { it.page == "glagoli-sedanjik" && it.words == listOf(QuizWord("s1", "sesti", form = true)) })
        assertEquals(Sound.Prompt("Which is right?"), q.ask.first())
        assertEquals("pres.1sg", q.form)
        assertEquals("Jaz sedem na klop.", q.answerText)
        // a word whose meaning the learner doesn't know well yet asks its meaning only
        assertTrue(RoadQuiz.forms(listOf(card.copy(repetitions = 0, lastQuality = 0)), mapOf("s1" to f), clips).isEmpty())
    }

    @Test fun `a riddle, the clues in the teller's voice, "Kaj sem?", the answer and two others, a pack word's counts on its card`() {
        fun t(sl: String, en: String) = DrillText(mapOf("sl" to sl, "en" to en))
        val riddles = listOf(
            Riddle("miza", "A1", listOf(t("Iz lesa sem.", "I'm made of wood.")), t("Kaj sem?", "What am I?"), t("Miza!", "A table!"), word = "v-kuhinji/miza"),
            Riddle("stol", "A1", listOf(t("Iz lesa sem.", "I'm made of wood.")), t("Kaj sem?", "What am I?"), t("Stol!", "A chair!"), word = "v-kuhinji/stol"),
            Riddle("krava", "A1", listOf(t("Iz lesa sem.", "I'm made of wood.")), t("Kaj sem?", "What am I?"), t("Krava!", "A cow!"), word = "na-kmetiji/krava"),
        )
        val drill = Drill("uganke", DrillKind.RIDDLE, "sl", "🕵️", t("Uganke", "Riddles"), teller = "janez", riddles = riddles)
        val cards = mapOf("vocab_v-kuhinji_miza" to ReviewCard("vocab_v-kuhinji_miza", "miza", "table"))
        val qs = RoadQuiz.riddles(listOf(drill), "sl", "en", "A1", { false }, cards, { listOf("grandpa", "male") }, clips)
        assertEquals(3, qs.size)
        val q = qs.first()
        assertEquals("riddle:miza", q.id)
        assertEquals(listOf(Sound.Clip("Iz lesa sem.", listOf("lesa.mp3")), Sound.Pause(RoadDrills.CLUE_MS), Sound.Clip("Kaj sem?", listOf("kaj-sem.mp3"))), q.ask)
        assertEquals("Miza!", q.options[0].text)
        assertEquals(setOf("Stol!", "Krava!"), q.options.drop(1).map { it.text }.toSet())
        assertTrue(q.options.all { it.words == listOf(QuizWord("vocab_v-kuhinji_miza", "miza")) })
        assertEquals(Sound.Prompt("A table!"), q.answer.last())
        assertTrue(qs[1].options.all { it.words.isEmpty() })
    }

    @Test fun `the grammar book's choice exercise with a gap, the sentence with each choice, on its page, not a rule not yet`() {
        val ex = Exercise.Choice("Nimam ___. (I don't have time.)", listOf("čas", "časa", "času"), 1, grammar = "rodilnik-nikalnica")
        val q = RoadQuiz.grammar(listOf(ex), mapOf("rodilnik-nikalnica" to Mastery.LEARNING), clips).single()
        assertEquals(listOf(Sound.Prompt("I don't have time.")), q.ask)
        assertEquals(listOf("Nimam časa.", "Nimam čas.", "Nimam času."), q.options.map { it.text })
        assertEquals(Sound.Clip("Nimam časa.", listOf("casa.mp3")), q.options[0].sound)
        assertTrue(q.options.all { it.page == "rodilnik-nikalnica" })
        assertTrue(RoadQuiz.grammar(listOf(ex), mapOf("rodilnik-nikalnica" to Mastery.NOT_YET), clips).isEmpty())
        // a page the book lacks, a heard one, one without a gap: none
        assertTrue(RoadQuiz.grammar(listOf(ex), emptyMap(), clips).isEmpty())
        assertTrue(RoadQuiz.grammar(listOf(ex.copy(audio = "x")), mapOf("rodilnik-nikalnica" to Mastery.NEW), clips).isEmpty())
        assertTrue(RoadQuiz.grammar(listOf(ex.copy(prompt = "Kaj je prav?")), mapOf("rodilnik-nikalnica" to Mastery.NEW), clips).isEmpty())
        // brackets that aren't a meaning: "Which is right?"
        val choose = RoadQuiz.grammar(listOf(ex.copy(prompt = "Nimam ___. (čas/časa)")), mapOf("rodilnik-nikalnica" to Mastery.NEW), clips).single()
        assertEquals(listOf(Sound.Prompt("Which is right?")), choose.ask)
    }

    @Test fun `the library's quiz, the kinds in turn, every sound to get ready, and its fixed phrases`() {
        val inputs = RoadInputs(scenes = listOf(scene), level = "A1", quiz = QuizInputs(cards = vocab))
        val items = RoadQuiz.items(inputs, clips)
        val kinds = items.mapNotNull { it.quiz?.kind }
        assertEquals(listOf(QuizKind.DIALOG, QuizKind.MEANING), kinds.take(2))
        assertEquals(Kind.KIT, items.last().kind)
        assertNotNull(items.last().kit)
        val q = items.first()
        assertEquals("quiz:dialog:trgovina/jajca/1", q.id)
        assertEquals(q.quiz!!.sounds, q.sounds)
        assertTrue(q.sounds.none { it is Sound.Pause })
        assertTrue(Sound.Spoken("Deset jajci, prosim.", "female") in q.sounds)
        // nothing to ask: no quiz, no kit
        assertTrue(RoadQuiz.items(RoadInputs(), clips).isEmpty())
    }

    // --- the session ------------------------------------------------------------------------------------------------

    @Test fun `the session, the due cards' questions first, the kinds in turn, one of a card's two a day, the least recently heard first`() {
        val inputs = RoadInputs(scenes = listOf(scene), level = "A1", quiz = QuizInputs(cards = vocab))
        val lib = RoadLibrary(1, "en", RoadQuiz.items(inputs, clips))
        val s = RoadQuiz.session(lib, emptyMap(), today)
        val ids = s.map { it.id }
        // a card's two questions, one of them today, the other tomorrow
        for (c in listOf("c1", "c2", "c3", "c4")) assertEquals(c, 1, ids.count { it == "quiz:meaning:$c" || it == "quiz:word:$c" })
        val tomorrow = RoadQuiz.session(lib, emptyMap(), "2026-10-09").map { it.id }
        assertNotEquals(ids.filter { "c1" in it }, tomorrow.filter { "c1" in it })
        // "kruh" is due today: its question first
        assertTrue(ids.first().endsWith(":c2"))
        // heard lately: later
        val heard = RoadQuiz.session(lib, mapOf("quiz:dialog:trgovina/jajca/1" to 5L), today).map { it.id }
        assertTrue(heard.indexOf("quiz:dialog:trgovina/jajca/1") >= ids.indexOf("quiz:dialog:trgovina/jajca/1"))
        assertTrue(s.none { it.kind == Kind.KIT })
    }
}
