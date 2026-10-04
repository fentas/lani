package si.lanisce.lani.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.ui.scene.ChoiceMatch.Result
import si.lanisce.lani.ui.scene.ChoiceMatch.Why

class ChoiceMatchTest {
    /** The first turn of the soup dialog (DialogRunTest): the right one and two wrong ones. */
    private val soup = listOf("Ja, zelo.", "Dobro jutro!", "Na zdravje!")
    private val order = listOf("Rad bi kavo, prosim.", "Rad bi čaj, prosim.", "Nič, hvala.")
    private val fire = listOf("Potem sedim k ognju.", "Ne, grem domov.", "Kje je gozd?")
    private val long = listOf("Babica, danes sem nabral drva v gozdu.", "Jutri bom šel na trg po kruh.", "Ne vem, kje je Luka.")

    private fun said(heard: String, choices: List<String>): Int? = (ChoiceMatch.match(listOf(heard), choices) as? Result.Said)?.index
    private fun why(heard: List<String>, choices: List<String>): Why? = (ChoiceMatch.match(heard, choices) as? Result.Unsure)?.why

    @Test fun `each choice said as written is found`() {
        soup.forEachIndexed { k, c -> assertEquals(c, k, said(c, soup)) }
        order.forEachIndexed { k, c -> assertEquals(c, k, said(c, order)) }
        fire.forEachIndexed { k, c -> assertEquals(c, k, said(c, fire)) }
    }

    @Test fun `case and punctuation don't matter`() {
        assertEquals(0, said("ja zelo", soup))
        assertEquals(2, said("NA ZDRAVJE", soup))
        assertEquals(1, said("dobro jutro", soup))
    }

    @Test fun `missing diacritics don't matter`() {
        assertEquals(1, said("rad bi caj prosim", order))
        assertEquals(2, said("nic hvala", order))
        assertEquals(0, said("babica danes sem nabral drva v gozdu", long))
    }

    @Test fun `dropped or changed endings still find the choice`() {
        assertEquals(0, said("potem sedi k ognj", fire))
        assertEquals(0, said("rad bi kava prosim", order))
        assertEquals(1, said("jutri bo sel na trg po kruha", long))
    }

    @Test fun `an extra word in front is forgiven`() {
        assertEquals(0, said("ja potem sedim k ognju", fire))
        assertEquals(1, said("ja, ne, grem domov", fire))
        assertEquals(2, said("no nič hvala", order))
    }

    @Test fun `a partial sentence of most of its words counts`() {
        assertEquals(0, said("babica danes sem nabral drva", long)) // 5 of 7 words
        assertEquals(1, said("jutri bom šel na trg", long)) // 5 of 7
        assertEquals(0, said("potem sedim k", fire)) // 3 of 4
        assertEquals(1, said("grem domov", fire)) // 2 of 3
    }

    @Test fun `too little of a sentence is not guessed`() {
        assertEquals(Why.NONE_CLOSE, why(listOf("babica danes"), long)) // 2 of 7
        assertEquals(Why.NONE_CLOSE, why(listOf("jutri"), long))
        assertEquals(null, said("rad bi", order)) // 2 of 4, and in two choices
    }

    @Test fun `two similar choices are told apart by the word that differs`() {
        assertEquals(0, said("rad bi kavo prosim", order))
        assertEquals(1, said("rad bi čaj prosim", order))
        assertEquals(1, said("rad bi caj", order))
        assertEquals(0, said("rad bi kavo", order))
    }

    @Test fun `two similar choices are told apart even with the differing word a little off`() {
        assertEquals(0, said("rad bi kava prosim", order))
        assertEquals(0, said("ja rad bi kava prosim", order))
        assertEquals(1, said("rad bi caja prosim", order))
        assertEquals(1, said("dober vecer", listOf("Dober dan!", "Dober večer!")))
    }

    @Test fun `a choice that is part of another is told apart by the rest`() {
        val thanks = listOf("Hvala.", "Hvala lepa.")
        assertEquals(0, said("hvala", thanks))
        assertEquals(1, said("hvala lepa", thanks))
        assertEquals(1, said("hvala lep", thanks))
    }

    @Test fun `what fits two choices alike is asked again`() {
        assertEquals(Why.TWO_CLOSE, why(listOf("rad bi prosim"), order))
        assertEquals(Why.TWO_CLOSE, why(listOf("dober"), listOf("Dober dan!", "Dober večer!")))
    }

    @Test fun `words run together or split by the recognizer are forgiven`() {
        assertEquals(2, said("nazdravje", soup))
        assertEquals(0, said("ja ze lo", soup))
    }

    @Test fun `something else entirely matches nothing`() {
        assertEquals(Why.NONE_CLOSE, why(listOf("kaj pa vem"), soup))
        assertEquals(Why.NONE_CLOSE, why(listOf("hello how are you"), fire))
        assertEquals(Why.NONE_CLOSE, why(listOf("mhm"), order))
    }

    @Test fun `nothing heard is said so`() {
        assertEquals(Why.NOTHING, why(emptyList(), soup))
        assertEquals(Why.NOTHING, why(listOf("", "  ", "…"), soup))
    }

    @Test fun `a wrong choice said is still the choice said`() {
        // The matcher finds what was said; whether it's right is the dialog's business.
        assertEquals(1, said("dobro jutro", soup))
    }

    @Test fun `a later alternative can decide, and is what was heard`() {
        val r = ChoiceMatch.match(listOf("dober utro", "dobro jutro", "dobra jutra"), soup)
        assertTrue(r is Result.Said)
        r as Result.Said
        assertEquals(1, r.index)
        assertEquals("dobro jutro", r.heard)
    }

    @Test fun `alternatives that point at two choices are asked again`() =
        assertEquals(Why.TWO_CLOSE, why(listOf("rad bi kavo prosim", "rad bi čaj prosim"), order))

    @Test fun `unsure answers keep the likeliest thing heard`() {
        val r = ChoiceMatch.match(listOf("kaj pa vem", "kaj pa ven"), soup) as Result.Unsure
        assertEquals("kaj pa vem", r.heard)
    }

    @Test fun `a turn with one choice needs it close enough`() {
        val one = listOf("Hvala lepa!")
        assertEquals(0, said("hvala lepa", one))
        assertEquals(0, said("hvala lep", one))
        assertEquals(Why.NONE_CLOSE, why(listOf("dober dan"), one))
    }

    @Test fun `similarity is 1 for the same words and falls with each word off`() {
        assertEquals(1.0, ChoiceMatch.similarity("Ja, zelo!", "ja zelo"), 1e-9)
        assertEquals(0.6, ChoiceMatch.similarity("babica danes sem", "babica danes sem nabral drva"), 1e-9)
        assertTrue(ChoiceMatch.similarity("rad bi kavo", "Rad bi čaj.") < ChoiceMatch.similarity("rad bi kavo", "Rad bi kavo."))
    }

    @Test fun `the prompt lists the choices whole, within the limit`() {
        assertEquals("Ja, zelo. Dobro jutro! Na zdravje!", ChoiceMatch.prompt(soup))
        assertEquals("Ja, zelo. Dobro jutro!", ChoiceMatch.prompt(soup, max = 25))
        assertEquals("", ChoiceMatch.prompt(listOf("x".repeat(400))))
    }

    // --- Italian: the same matching with Italian's accents and apostrophes -------------------------------------------

    /** A guest's turn at the sea (friuli's Il mare): the right one and two wrong ones. */
    private val mare = listOf("Sì, sono di Gorizia.", "Perché no? L'acqua è bella.", "Vorrei un gelato, per favore.")

    private fun saidIt(heard: String, choices: List<String>): Int? =
        (ChoiceMatch.match(listOf(heard), choices, Lang.IT) as? Result.Said)?.index

    @Test fun `italian choices are found without their accents and apostrophes`() {
        mare.forEachIndexed { k, c -> assertEquals(c, k, saidIt(c, mare)) }
        assertEquals(0, saidIt("si sono di gorizia", mare))
        assertEquals(1, saidIt("perche no l acqua e bella", mare))
        assertEquals(1, saidIt("Perchè no, l’acqua è bella", mare))
        assertEquals(2, saidIt("vorrei un gelato per favore", mare))
    }

    @Test fun `italian keys fold the accents, apostrophes and punctuation`() {
        assertEquals("si l acqua e bella", ChoiceMatch.key("Sì, l'acqua è bella!", Lang.IT))
        assertEquals("perche", ChoiceMatch.key("Perché?", Lang.IT))
        // Slovene's key keeps Italian's accents: each language folds its own letters
        assertEquals("perché", ChoiceMatch.key("Perché?", Lang.SL))
    }

    @Test fun `italian choices that differ in one word are told apart`() {
        val drinks = listOf("Un caffè, per favore.", "Un tè, per favore.")
        assertEquals(0, saidIt("un caffe per favore", drinks))
        assertEquals(1, saidIt("un te per favore", drinks))
    }
}
