package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.game.scene.Counts
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.Numbers
import si.lanisce.lani.game.scene.parseScene
import java.io.File

/**
 * "📖 Namig · Hint" at a learner's turn (companion/SCENES.md, "The hint"): which pages and trigger it shows (the question,
 * what decides the form in the sentence, a model with other words), and that it never gives the answer away, over the
 * curated scenes.
 */
class TurnHintTest {
    private val pages = Grammar.bundled("sl")
    private val page = { id: String -> pages.firstOrNull { it.id == id } }

    private fun wrong(sl: String, grammar: String?, why: String = "…") = DialogChoice(sl, why = why, grammar = grammar)

    /** Micka at the market: ten eggs. */
    private val eggs = DialogLine(choices = listOf(
        DialogChoice("Deset jajc, prosim.", "Ten eggs, please.", ok = true),
        wrong("Deset jajce, prosim.", "stevila-samostalniki"),
        wrong("Deset jajca, prosim.", "stevila-samostalniki"),
    ))

    /** Luka in the rain (v-sotoru/dez-svetilka): come into the tent. */
    private val tent = DialogLine(choices = listOf(
        DialogChoice("Seveda, pridi v šotor! Sedi k svetilki.", ok = true),
        wrong("Seveda, pridi v šotoru! Sedi k svetilki.", "kam-tozilnik"),
    ))

    /** No bread. */
    private val bread = DialogLine(choices = listOf(
        DialogChoice("Ne, nimam kruha.", ok = true),
        wrong("Ne, nimam kruh.", "rodilnik-nikalnica"),
    ))

    @Test fun `a number, the case after it, the number's category, and a model of that category with other nouns`() {
        val h = TurnHints.of(eggs, "sl", page)!!
        assertEquals(listOf("stevila-samostalniki"), h.pages)
        assertEquals(Trigger.Count("Deset", 0, Numbers.MANY), h.trigger)
        assertEquals(listOf(CaseLine("Koga? Česa?", null, Case.GEN)), h.cases)
        assertEquals(setOf("jajc"), h.right)
        assertEquals(setOf("jajc", "jajce", "jajca"), h.forms)
        assertEquals("pet hiš · pet stolov · pet jabolk", h.models.first())
        val t = TurnHints.text(h, page)
        assertEquals(listOf("Koga? Česa? → 2. rodilnik (genitive)", "after «Deset»: five and up, the genitive plural"), t.lines)
        assertTrue(t.models!!.startsWith("Like: pet hiš"))
        // the page's row of 5 and up has "pet jajc": the answer, left out
        assertTrue(t.rows.none { "jajc" in it })
        assertEquals(listOf("stevila-samostalniki" to "Ena ovca, dve ovci, pet ovc · Numbers with nouns"), t.links)
    }

    @Test fun `a preposition that takes two cases, where to and where, the page's first, and the rows of the page about it`() {
        val h = TurnHints.of(tent, "sl", page)!!
        assertEquals(listOf("kam-tozilnik"), h.pages)
        assertEquals(Trigger.Preposition("v", 2, listOf(Case.LOC, Case.ACC)), h.trigger)
        assertEquals(listOf(Case.ACC, Case.LOC), h.cases.map { it.case })
        val t = TurnHints.text(h, page)
        assertEquals(
            listOf(
                "Kam? (where to? German: wohin?) → 4. tožilnik (accusative)",
                "Kje? (where? German: wo?) → 5. mestnik (locative)",
                "after «v»",
            ),
            t.lines,
        )
        assertEquals("Like: Grem v šolo. · Grem na pošto.", t.models)
        assertEquals(2, t.rows.size)
        assertTrue(t.rows.all { "v, na" in it })
        assertTrue((t.lines + t.rows + listOfNotNull(t.models)).none { TurnHints.shows(it, "šotor", stem = true) })
        // z (with): the page's case alone, not the other use of z (off, from: s pošte)
        val milk = DialogLine(choices = listOf(DialogChoice("Z mlekom, prosim.", ok = true), wrong("Z mleko, prosim.", "orodnik")))
        assertEquals(listOf("S kom? S čim? → 6. orodnik (instrumental)", "after «Z»"), TurnHints.text(TurnHints.of(milk, "sl", page)!!, page).lines)
    }

    @Test fun `pod and the instrumental, where, the page's own row of pod, and a model with pod first`() {
        val mushroom = DialogLine(choices = listOf(DialogChoice("Jurček je pod smreko!", ok = true), wrong("Jurček je pod smreka!", "orodnik")))
        val h = TurnHints.of(mushroom, "sl", page)!!
        val t = TurnHints.text(h, page)
        assertEquals(
            listOf("Kje? (where? German: wo?) → 6. orodnik (instrumental)", "Kam? (where to? German: wohin?) → 4. tožilnik (accusative)", "after «pod»"),
            t.lines,
        )
        assertEquals("pod mizo", h.models.first())
        // the row of pod with "pod veliko smreko" shows the noun of the turn: left out; the one of where to stays
        assertEquals(listOf("where to: the accusative · kam? · za vrata, pod stol"), t.rows)
    }

    @Test fun `a page the syllabus added, aspect, its questions and the rule`() {
        val caught = DialogLine(choices = listOf(DialogChoice("Ujel sem te!", ok = true), wrong("Lovil sem te!", "glagolski-vid")))
        val t = TurnHints.text(TurnHints.of(caught, "sl", page)!!, page)
        assertTrue(t.lines.single().startsWith("Kaj delam? Kaj naredim? Going on, or again and again"))
        assertEquals("Like: pisal sem · napisal sem · kupujem · kupim", t.models)
    }

    @Test fun `a no, the genitive after it, with the page's own yes and no`() {
        val h = TurnHints.of(bread, "sl", page)!!
        assertEquals(Trigger.Negation("nimam", 1), h.trigger)
        val t = TurnHints.text(h, page)
        assertEquals(listOf("Koga? Česa? → 2. rodilnik (genitive)", "after the no «nimam»"), t.lines)
        assertEquals("Like: Nimam časa. · Ne pijem kave.", t.models)
        assertEquals(listOf("Imam čas. · Nimam časa.", "Imam avto. · Nimam avta."), t.rows)
    }

    @Test fun `a verb whose partner takes the dative, with the German that works the same`() {
        val help = DialogLine(choices = listOf(
            DialogChoice("Seveda vam pomagam.", ok = true),
            wrong("Seveda vas pomagam.", "dajalnik"),
        ))
        val t = TurnHints.text(TurnHints.of(help, "sl", page)!!, page)
        assertEquals(listOf("Komu? Čemu? → 3. dajalnik (dative)", "with «pomagam» (like German helfen + dative)"), t.lines)
        // the page's row with pomagam and the answer (Pomagam vam.) is left out, the other one is shown
        assertEquals(listOf("babica · babici · Pomagam babici."), t.rows)
    }

    @Test fun `a rule that isn't a case, its question, the rule in words, a cue in the sentence, a model`() {
        val you = DialogLine(choices = listOf(
            DialogChoice("Dober večer, babica Micka. Kaj delate?", ok = true),
            wrong("Dober večer, babica Micka. Kaj delaš?", "ti-vi"),
        ))
        val t = TurnHints.text(TurnHints.of(you, "sl", page)!!, page)
        assertTrue(t.lines.single().startsWith("Ti ali vi? Who are you talking to?"))
        assertEquals("Like: Kako si? · Kako ste? · Pridi! · Pridite!", t.models)
        // who does it: the pronoun in the sentence
        val two = DialogLine(choices = listOf(DialogChoice("Midva govoriva slovensko.", ok = true), wrong("Midva govorimo slovensko.", "dvojina")))
        assertEquals(Trigger.Cue("Midva", 0, Trigger.Cue.Kind.WHO), TurnHints.of(two, "sl", page)!!.trigger)
    }

    @Test fun `a number's own word agrees with its noun, the rule sets the forms side by side`() {
        val brothers = DialogLine(choices = listOf(DialogChoice("Dva brata. Dvojina!", ok = true), wrong("Dve brata. Dvojina!", "stevila-samostalniki")))
        val h = TurnHints.of(brothers, "sl", page)!!
        assertEquals(Trigger.Noun("brata", 1), h.trigger)
        val t = TurnHints.text(h, page)
        // the rule names dva and dve both, side by side (fair); the models, other numbers only
        assertEquals("with the noun «brata»: by its gender", t.lines.first())
        assertTrue(t.lines.any { "dva, dve" in it })
        assertEquals("Like: en stol · ena hiša · eno jabolko · trije stoli · tri hiše · tri jabolka", t.models)
    }

    @Test fun `a tap turn's rule is the page its right choice names, the places tapped are never modelled`() {
        val hide = DialogLine(choices = listOf(
            DialogChoice("Aha, za vrati si!", ok = true, tap = "behind-door", grammar = "kje-mestnik-orodnik"),
            DialogChoice("Aha, pod mizo si!", tap = "under-table", why = "Look for a clue."),
            DialogChoice("Aha, za pečjo si!", tap = "stove", why = "Look for a braid."),
        ))
        val h = TurnHints.of(hide, "sl", page)!!
        assertEquals(listOf("kje-mestnik-orodnik"), h.pages)
        // za vrati against za pečjo: the place's word is the gap, za decides its case (where: the instrumental)
        assertEquals(Trigger.Preposition("za", 1, listOf(Case.INS, Case.ACC)), h.trigger)
        assertEquals("Kje? (where? German: wo?) → 6. orodnik (instrumental)", TurnHints.text(h, page).lines.first())
        // places that differ in more than a word: the rule in words, and models of none of them
        val far = hide.copy(choices = hide.choices.take(2) + DialogChoice("Aha, v omari si!", tap = "cupboard", why = "Look again."))
        val f = TurnHints.of(far, "sl", page)!!
        assertNull(f.trigger)
        assertEquals(listOf("v šoli", "na travniku"), f.models)
        assertTrue(TurnHints.text(f, page).lines.single().startsWith("Kje? Where something is"))
    }

    @Test fun `no page, no hint, another language gets its pages alone`() {
        val meaning = DialogLine(choices = listOf(DialogChoice("Ja, zelo.", ok = true), DialogChoice("Dobro jutro!", why = "It's evening.")))
        assertNull(TurnHints.of(meaning, "sl", page))
        assertNull(TurnHints.of(eggs, "sl") { null }) // a page the book lacks
        val it = TurnHints.of(eggs, "it", page)!!
        assertEquals(listOf("stevila-samostalniki"), it.pages)
        assertNull(it.trigger)
        assertTrue(it.cases.isEmpty() && it.models.isEmpty() && it.rule == null)
        // the turn's own page, when its choices name none; else a guess from the whys
        val turn = DialogLine(grammar = "tozilnik", choices = listOf(DialogChoice("Pijem vodo.", ok = true), wrong("Pijem voda.", null)))
        assertEquals(listOf("tozilnik"), TurnHints.pagesOf(turn))
        val guessed = DialogLine(choices = listOf(DialogChoice("Pijem vodo.", ok = true), DialogChoice("Pijem voda.", guess = "tozilnik")))
        assertEquals(listOf("tozilnik"), TurnHints.pagesOf(guessed))
    }

    @Test fun `a title that shows the answer is left out of the link`() {
        val time = DialogLine(choices = listOf(DialogChoice("Ne, nimam časa.", ok = true), wrong("Ne, nimam čas.", "rodilnik-nikalnica")))
        val t = TurnHints.text(TurnHints.of(time, "sl", page)!!, page)
        assertEquals(listOf("rodilnik-nikalnica" to null), t.links) // "Nimam časa: rodilnik po nikalnici"
        assertTrue(t.rows.none { TurnHints.shows(it, "časa") })
    }

    /** Every Slovene scene's turns, their counting dialogs with a number of each category. */
    private fun curatedTurns(): List<Pair<String, DialogLine>> {
        val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "scenes").isDirectory }
        val files = File(root, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().toList() +
            File(root, "cultures").listFiles().orEmpty().flatMap { File(it, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().toList() }
        return files.map { parseScene(it.readText()) }.filter { it.language == "sl" }.flatMap { s ->
            s.dialogs.flatMap { d ->
                val played = if (d.count != null) listOf(1, 2, 3, 12).map { Counts.render(d, it, "sl") } else listOf(d)
                played.flatMap { p -> p.lines.filter { it.choices.size >= 2 }.map { "${s.id}/${p.id}" to it } }
            }
        }
    }

    @Test fun `over the curated scenes the hint never gives the answer away, and most case turns get their trigger`() {
        var hints = 0
        var said = 0
        var cased = 0
        var triggered = 0
        for ((where, line) in curatedTurns()) {
            val h = TurnHints.of(line, "sl", page) ?: continue
            hints++
            val t = TurnHints.text(h, page)
            if (t.lines.isNotEmpty()) said++
            for (s in t.lines + t.rows + t.links.mapNotNull { it.second }) assertTrue("$where: «$s» gives away ${h.right}", TurnHints.fair(s, h))
            for (m in h.models) assertFalse("$where: the model «$m» shows one of ${h.forms}", h.forms.any { TurnHints.shows(m, it, stem = true) })
            val right = line.choices.indexOfFirst { it.ok }.takeIf { r -> line.choices.count { it.ok } == 1 } ?: continue
            if (Forms.turn(line.choices.map { it.sl }, right) == null) continue
            if (h.pages.first() in CASE_PAGES) {
                cased++
                if (h.trigger != null) triggered++
            }
        }
        println("hints: $hints turns, $said with a question or a rule; $triggered of $cased case turns with their trigger")
        assertTrue("turns with a hint: $hints", hints > 400)
        // a question or a rule in nearly every one, not just the link to the book
        assertTrue("$said of $hints hints say more than the link", said * 10 >= hints * 9)
        // the preposition, the number, the amount, the no, the verb: found in most case turns
        assertTrue("$triggered of $cased case turns name what decides their form", triggered * 10 >= cased * 7)
    }

    private val CASE_PAGES = setOf(
        "stevila-samostalniki", "rodilnik-kolicina", "rodilnik-nikalnica", "rodilnik-predlogi", "kje-mestnik-orodnik", "kam-tozilnik", "orodnik", "dajalnik",
    )
}
