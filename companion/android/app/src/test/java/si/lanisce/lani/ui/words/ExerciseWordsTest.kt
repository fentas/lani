package si.lanisce.lani.ui.words

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.l10n.Lang
import kotlin.random.Random

/**
 * What an exercise lets the learner look up (companion/README.md, "Exercises"): its words in the language learned, never
 * what it tests while it's open; everything once answered.
 */
class ExerciseWordsTest {
    /** Jan's words: a few, as KnownWords has them from the deck. */
    private val known = KnownWords.of(listOf("miza", "sedeti", "sesti", "sedem", "sedim", "sedel", "jutro"))
    private val reads = ExerciseTaps.reader(Lang.SL, Lang.EN, known)
    private fun before(ex: Exercise) = ExerciseTaps(ex, answered = false, reads)
    private fun after(ex: Exercise) = ExerciseTaps(ex, answered = true, reads)
    private fun List<WordToken>.texts() = map { it.text }

    // --- choices ---------------------------------------------------------------------------------------------------

    @Test fun `a word to recognize isn't looked up before its answer, and is after`() {
        val ex = Exercise.Choice("miza", listOf("table", "chair", "dog"), 0, say = "miza")
        assertEquals(emptyList<String>(), before(ex).prompt("miza").texts())
        ex.options.forEachIndexed { i, o -> assertEquals("a meaning in English: $o", emptyList<String>(), before(ex).option(i, o).texts()) }
        assertEquals(listOf("miza"), after(ex).prompt("miza").texts())
        // the meanings are English: nothing to look up, also after
        assertEquals(emptyList<String>(), after(ex).option(0, "table").texts())
    }

    @Test fun `the Slovene for a meaning, none of the options before the answer, all after`() {
        val ex = Exercise.Choice("good day", listOf("dober dan", "lahko noč", "hvala lepa"), 0, sayOptions = true)
        assertEquals(emptyList<String>(), before(ex).prompt("good day").texts())
        ex.options.forEachIndexed { i, o -> assertEquals(emptyList<String>(), before(ex).option(i, o).texts()) }
        assertEquals(listOf("lahko", "noč"), after(ex).option(1, "lahko noč").texts())
        assertEquals(How.TAP, after(ex).answerHow)
    }

    @Test fun `a meaning that only looks Slovene doesn't open its options`() {
        val ex = Exercise.Choice("so, thus", listOf("dober dan", "lahko noč", "hvala lepa"), 0, sayOptions = true)
        ex.options.forEachIndexed { i, o -> assertEquals(emptyList<String>(), before(ex).option(i, o).texts()) }
    }

    @Test fun `a form in a sentence with a gap, the sentence's words, not the lemma in brackets nor the forms`() {
        val ex = Exercise.Choice("Jaz ____ na klop. (sesti)", listOf("sedem", "sedim", "sedel"), 0)
        assertEquals(listOf("Jaz", "na", "klop"), before(ex).prompt(ex.prompt).texts())
        ex.options.forEachIndexed { i, o -> assertEquals(emptyList<String>(), before(ex).option(i, o).texts()) }
        assertEquals(listOf("Jaz", "na", "klop", "sesti"), after(ex).prompt(ex.prompt).texts())
        assertEquals(listOf("sedim"), after(ex).option(1, "sedim").texts())
    }

    @Test fun `a gap's translation in brackets is never looked up`() {
        val ex = Exercise.Choice("Nimam ___. (I don't have time.)", listOf("časa", "čas", "časom"), 0, sayOptions = true)
        assertEquals(listOf("Nimam"), before(ex).prompt(ex.prompt).texts())
        assertEquals(listOf("Nimam"), after(ex).prompt(ex.prompt).texts())
        assertEquals(emptyList<String>(), before(ex).option(0, "časa").texts())
    }

    @Test fun `a question in Slovene, its words, and the places to choose by a long press`() {
        val ex = Exercise.Choice("Kam gre Luka? · Where is Luka going?", listOf("🪷 ob ribniku", "🌲 v gozdu", "🌾 na polju"), 0, sayOptions = true)
        assertEquals(listOf("Kam", "gre", "Luka"), before(ex).prompt(ex.prompt).texts())
        assertEquals(listOf("ob", "ribniku"), before(ex).option(0, ex.options[0]).texts())
        assertEquals(How.HOLD, before(ex).answerHow)
        assertTrue(before(ex).any)
    }

    @Test fun `what replies differ in is what the choice tests`() {
        val ex = Exercise.Choice("Kaj rečeš natakarju?", listOf("Prosim, en čaj.", "Prosim, eno čaj.", "Prosim, ena čaj."), 0, sayOptions = true)
        assertEquals(listOf("Prosim", "čaj"), before(ex).option(0, ex.options[0]).texts())
        assertEquals(listOf("Prosim", "eno", "čaj"), after(ex).option(1, ex.options[1]).texts())
    }

    @Test fun `how to say a quoted meaning, the question's words, not the meaning, not the options`() {
        val ex = Exercise.Choice("Kako rečeš «good day»? · How do you say «good day»?", listOf("dober dan", "lahko noč", "hvala lepa"), 0, sayOptions = true)
        assertEquals(listOf("Kako", "rečeš"), before(ex).prompt(ex.prompt).texts())
        ex.options.forEachIndexed { i, o -> assertEquals(emptyList<String>(), before(ex).option(i, o).texts()) }
        // the same, emphasized in markdown: as a quote
        val md = Exercise.Choice("Kako rečeš *good day*?", ex.options, 0, sayOptions = true)
        ex.options.forEachIndexed { i, o -> assertEquals(emptyList<String>(), before(md).option(i, o).texts()) }
        assertEquals(listOf("Kako", "rečeš"), before(md).prompt("Kako rečeš good day?", emphasized = listOf(11..18)).texts())
    }

    @Test fun `what a quoted word means, the whole question waits for the answer`() {
        val ex = Exercise.Choice("Kaj pomeni «miza»? · What does «miza» mean?", listOf("table", "chair", "bread"), 0)
        assertEquals(emptyList<String>(), before(ex).prompt(ex.prompt).texts())
        // after: the Slovene half, and the word quoted (one of Jan's) in either half
        assertEquals(listOf("Kaj", "pomeni", "miza", "miza"), after(ex).prompt(ex.prompt).texts())
        assertFalse(before(ex).any)
    }

    @Test fun `heard, nothing is shown to look up before the answer`() {
        val ex = Exercise.Choice("", listOf("table", "chair"), 0, audio = "miza")
        assertFalse(before(ex).any)
    }

    @Test fun `a multi's options in Slovene for an English question wait for the answer`() {
        val ex = Exercise.Multi("Which are fruit?", listOf("jabolko", "hruška", "miza"), listOf(0, 1))
        assertEquals(emptyList<String>(), before(ex).prompt(ex.prompt).texts())
        ex.options.forEachIndexed { i, o -> assertEquals(emptyList<String>(), before(ex).option(i, o).texts()) }
        assertEquals(listOf("hruška"), after(ex).option(1, "hruška").texts())
    }

    // --- typed, built, said ----------------------------------------------------------------------------------------

    @Test fun `a cloze, the sentence round the gap, without what brackets add`() {
        val ex = Exercise.Cloze("Babica je ___ kuhinji. (Grandma is in the kitchen.)", listOf("v"))
        val shown = ex.text.replace("___", "______")
        assertEquals(listOf("Babica", "je", "kuhinji"), before(ex).prompt(shown).texts())
        assertEquals(listOf("Babica", "je", "kuhinji"), after(ex).prompt(shown).texts())
    }

    @Test fun `a reorder's tiles by a long press for their meaning, the English prompt not at all`() {
        val ex = Exercise.Reorder("My name is Jan.", listOf("Imenujem", "se", "Jan."), listOf(listOf("Imenujem", "se", "Jan.")))
        assertEquals(emptyList<String>(), before(ex).prompt(ex.prompt).texts())
        assertEquals(listOf("imenujem"), before(ex).tile("imenujem").texts())
        assertEquals(How.HOLD, before(ex).answerHow)
        assertTrue(before(ex).any)
    }

    @Test fun `translating into Slovene, the English source has nothing, its Slovene hint not before the answer`() {
        val ex = Exercise.Translate("good morning", listOf("dobro jutro"))
        assertEquals(emptyList<String>(), before(ex).prompt(ex.prompt).texts())
        val hinted = Exercise.Translate("I sit down on the bench (sesti)", listOf("Sedem na klop."))
        assertEquals(emptyList<String>(), before(hinted).prompt(hinted.prompt).texts())
        assertEquals(listOf("sesti"), after(hinted).prompt(hinted.prompt).texts())
        assertEquals(listOf("Sedem", "na", "klop"), after(hinted).correct("Sedem na klop.").texts())
    }

    @Test fun `translating Slovene into English, its source is what's tested`() {
        val ex = Exercise.Translate("Dobro jutro!", listOf("Good morning!"))
        assertEquals(emptyList<String>(), before(ex).prompt(ex.prompt).texts())
        assertEquals(listOf("Dobro", "jutro"), after(ex).prompt(ex.prompt).texts())
        // a one-word answer that reads as either: not known to be Slovene, so the source waits too
        val word = Exercise.Translate("miza", listOf("table"))
        assertEquals(emptyList<String>(), before(word).prompt("miza").texts())
    }

    @Test fun `review planner exercises never open their tested word`() {
        val card = ReviewCard("vocab_miza", "miza", "table")
        val pool = listOf(card, ReviewCard("vocab_stol", "stol", "chair"), ReviewCard("vocab_pes", "pes", "dog"), ReviewCard("vocab_hisa", "hiša", "house"))
        for (seed in 0 until 40) {
            for (t in ReviewPlanner.plan(listOf(card, card.copy(repetitions = 4, lastQuality = 5)), pool, canSpeak = true, Random(seed), canRecognize = true)) {
                val ex = t.exercise
                val taps = before(ex)
                val looked = when (ex) {
                    is Exercise.Choice -> taps.prompt(ex.prompt).texts() + ex.options.flatMapIndexed { i, o -> taps.option(i, o).texts() }
                    is Exercise.Translate -> taps.prompt(ex.prompt).texts()
                    is Exercise.Speak -> taps.prompt(ex.prompt).texts()
                    is Exercise.Flashcard -> taps.front(ex.front, revealed = false).texts()
                    else -> emptyList()
                }
                assertFalse("${t.variant} looks up «miza» before its answer: $ex", "miza" in looked)
            }
        }
    }

    @Test fun `a speak exercise, the English prompt has nothing, a sentence with a gap its words, the model once shown`() {
        val ex = Exercise.Speak(say = "Dobro jutro.", prompt = "Good morning.", show = false)
        assertEquals(emptyList<String>(), before(ex).prompt(ex.prompt).texts())
        assertEquals(listOf("Dobro", "jutro"), before(ex).model(ex.say).texts())
        val gap = Exercise.Speak(say = "Jaz sedem na klop.", prompt = "Jaz ____ na klop. (sesti)", show = false)
        assertEquals(listOf("Jaz", "na", "klop"), before(gap).prompt(gap.prompt).texts())
    }

    @Test fun `a scenario's line is context for a reply, and what's tested for a meaning`() {
        val reply = Exercise.Scenario("In a café.", "What do you say?", line = "Kaj boste?", options = listOf("Kavo, prosim.", "Kava je dobra."), answer = 0)
        assertEquals(listOf("Kaj", "boste"), before(reply).line("„Kaj boste?“").texts())
        assertEquals(listOf("Kavo", "prosim"), before(reply).option(0, "Kavo, prosim.").texts())
        val meaning = Exercise.Scenario("In a café.", "What does the waiter ask?", line = "Kaj boste?", options = listOf("what you'll have", "where you're from"), answer = 0)
        assertEquals(emptyList<String>(), before(meaning).line("„Kaj boste?“").texts())
        assertEquals(listOf("Kaj", "boste"), after(meaning).line("„Kaj boste?“").texts())
        val typed = Exercise.Scenario("In a café.", "Answer him:", line = "Kaj boste?", accept = listOf("Kavo, prosim."))
        assertEquals(listOf("Kaj", "boste"), before(typed).line("„Kaj boste?“").texts())
    }

    @Test fun `a flashcard's word once turned over`() {
        val ex = Exercise.Flashcard("miza", "table")
        assertEquals(emptyList<String>(), before(ex).front("miza", revealed = false).texts())
        assertEquals(listOf("miza"), before(ex).front("miza", revealed = true).texts())
        assertEquals(emptyList<String>(), before(ex).back("table").texts())
        assertFalse(before(ex).any)
    }

    @Test fun `a dictation shows nothing`() = assertFalse(before(Exercise.Dictation("dober dan", listOf("dober dan"))).any)

    // --- text parts ------------------------------------------------------------------------------------------------

    @Test fun `a word's line, its half of a label in both languages, without what brackets add`() {
        fun lineOf(text: String, word: String) = TextParts.lineOf(text, WordTokens.of(text).first { it.text == word })
        assertEquals("Jaz ______ na klop.", lineOf("Jaz ______ na klop. (sesti)", "klop"))
        assertEquals("Kam gre Luka?", lineOf("Kam gre Luka? · Where is Luka going?", "gre"))
        assertEquals("🏕 Luka: «Grem k vodi.»", lineOf("🏕 Luka: «Grem k vodi.»\n\nKam gre?", "vodi"))
    }

    @Test fun `parts, quotes, emphasis and brackets`() {
        val t = "Kako rečeš «table» (ali **mizo**)?"
        val kinds = TextParts.chunks(t).single().pieces.map { it.kind to it.of(t) }
        assertEquals(
            listOf(TextParts.Kind.PLAIN to "Kako rečeš ", TextParts.Kind.QUOTE to "table", TextParts.Kind.PLAIN to " ", TextParts.Kind.BRACKET to "ali **mizo**", TextParts.Kind.PLAIN to "?"),
            kinds,
        )
    }
}
