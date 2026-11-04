package si.lanisce.lani.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
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
 * what decides the form in the sentence, as the trigger table has it: game/CaseTriggers.kt; a model with other words),
 * and that it never gives the answer away, over the curated scenes. What the table decides otherwise than the app's own
 * rules did before it: `what the trigger table decides that the app's old rules didn't`; that the chip and the case lens
 * agree on the same turn: TurnHintLensTest.
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
        // the cases v takes in the table's order: where to (the accusative), then where (the locative)
        assertEquals(Trigger.Preposition("v", 2, listOf(Case.ACC, Case.LOC)), h.trigger)
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

    @Test fun `the B1 pages have a rule of their own, never the turn's form`() {
        // a turn of each page (none of the curated scenes names them yet): the rule in words, a model with other words
        val turns = mapOf(
            "sklanjatev-posebnosti" to (DialogChoice("Pomagam očetu.", ok = true) to wrong("Pomagam oču.", "sklanjatev-posebnosti")),
            "dvojina-skloni" to (DialogChoice("Grem z dvema prijateljema.", ok = true) to wrong("Grem z dvema prijatelji.", "dvojina-skloni")),
            "vid-casi" to (DialogChoice("Ne odpiraj okna!", ok = true) to wrong("Ne odpri okna!", "vid-casi")),
            "dolocna-oblika" to (DialogChoice("Daj mi rdeči trak.", ok = true) to wrong("Daj mi rdeč trak.", "dolocna-oblika")),
            "odvisniki" to (DialogChoice("Ostal sem doma, ker je deževalo.", ok = true) to wrong("Ostal sem doma, zato je deževalo.", "odvisniki")),
        )
        val keys = mapOf(
            "sklanjatev-posebnosti" to "hint.ruleStems", "dvojina-skloni" to "hint.ruleDualCases", "vid-casi" to "hint.ruleAspectTenses",
            "dolocna-oblika" to "hint.ruleDefinite", "odvisniki" to "hint.ruleClauses",
        )
        for ((id, turn) in turns) {
            val h = TurnHints.of(DialogLine(choices = listOf(turn.first, turn.second)), "sl", page)!!
            assertEquals(id, listOf(id), h.pages)
            assertEquals(id, keys.getValue(id), h.rule)
            assertTrue(id, h.models.isNotEmpty())
            val t = TurnHints.text(h, page)
            assertTrue(id, t.lines.isNotEmpty())
            // nothing shown is the turn's own form
            assertTrue(id, (t.lines + listOfNotNull(t.models)).all { TurnHints.fair(it, h) })
        }
    }

    @Test fun `the A2, B1b and B2 pages have a rule of their own, never the turn's form`() {
        // a turn of each page (the course units name them; no curated scene does yet): the rule in words, a model in other words
        val turns = listOf(
            Triple("cigav", "Čigav je ta klobuk? Lukov.", "Čigav je ta klobuk? Lukova.") to "hint.ruleWhose",
            Triple("mnozina-skloni", "Pri Vidi je danes veliko gostov.", "Pri Vidi je danes veliko gosti.") to "hint.rulePluralCases",
            Triple("cas-izrazi", "Mlin so zgradili leta 1890.", "Mlin so zgradili leto 1890.") to "hint.ruleTimeNoPrep",
            Triple("custva", "Mamo boli glava.", "Mami boli glava.") to "hint.ruleFeelings",
            Triple("stevila-glagol", "Na trgatev je prišlo dvanajst sosedov.", "Na trgatev je prišli dvanajst sosedov.") to "hint.ruleNumberVerb",
            Triple("pogojnik-ce-bi", "Če bi deževalo, bi ostali doma.", "Če bi deževalo, bi ostanemo doma.") to "hint.ruleIfWould",
            Triple("naslonke-red", "Dal ti ga bom.", "Dal ga ti bom.") to "hint.ruleCliticOrder",
            Triple("glagolniki", "Branje je moj konjiček.", "Brati je moj konjiček.") to "hint.ruleVerbalNoun",
            Triple("nedolocni-zaimki", "Nihče ni prišel.", "Nekdo ni prišel.") to "hint.ruleIndefinite",
            Triple("trpnik", "Trgovina je zaprta.", "Trgovina je zaprt.") to "hint.rulePassive",
            Triple("stopnjevanje", "Jeseni so dnevi čedalje krajši.", "Jeseni so dnevi čedalje kratki.") to "hint.ruleCompareFull",
            Triple("predpreteklik", "Ko je Nejc prišel, je bil avtobus že odpeljal.", "Ko je Nejc prišel, je bil avtobus že odpelje.") to "hint.rulePluperfect",
            Triple("odvisniki-napredno", "Počakaj, dokler ne pridem.", "Počakaj, dokler ne prišel.") to "hint.ruleClausesMore",
            Triple("frazemi", "Držim pesti zate!", "Držim roke zate!") to "hint.ruleIdioms",
            Triple("stevniki", "Mojca bere pravljice trikrat na teden.", "Mojca bere pravljice tri na teden.") to "hint.ruleNumberWords",
            Triple("delezja", "Luka je molče gledal ovce.", "Luka je molčal gledal ovce.") to "hint.ruleGerund",
            Triple("pomanjsevalnice", "Nejc sedi na stolčku.", "Nejc sedi na stolček.") to "hint.ruleDiminutive",
            Triple("ukazi", "Naj Nejc prinese drva.", "Naj Nejc prinesi drva.") to "hint.ruleCommands",
            Triple("delezniki", "Skozi odprta vrata je pritekel Muri.", "Skozi odprt vrata je pritekel Muri.") to "hint.ruleParticiple",
            Triple("izpeljanke", "Pek peče kruh v pekarni.", "Pek peče kruh v pekarju.") to "hint.ruleWordFamily",
            Triple("predpone", "Nejc je prepisal nalogo.", "Nejc je pisal nalogo.") to "hint.rulePrefix",
            Triple("predlogi-dva-sklona", "Babica je poslala Nejca po kruh.", "Babica je poslala Nejca po kruhu.") to "hint.ruleTwoCases",
            Triple("govorjeni-jezik", "A si že jedel?", "A so že jedel?") to "hint.ruleSpoken",
            Triple("uradno-pismo", "Spoštovani gospod župan, pišem Vam.", "Spoštovani gospod župan, pišem ti.") to "hint.ruleFormalLetter",
            Triple("pogojnik-pretekli", "Bil bi prišel, pa je deževalo.", "Bil bi pride, pa je deževalo.") to "hint.rulePastConditional",
            Triple("brezosebno", "Pred zimo je treba popraviti streho.", "Pred zimo je treba popravim streho.") to "hint.ruleImpersonal",
            Triple("oziralni-kateri", "Fant, katerega sem videl, je Tine.", "Fant, kateri sem videl, je Tine.") to "hint.ruleWhichWhose",
            Triple("besedni-red", "Kruh je prinesel Luka.", "Luka je prinesel kruh.") to "hint.ruleWordOrder",
        )
        // every page of the book has a rule now
        val ruled = turns.map { it.first.first }.toSet()
        assertEquals(28, ruled.size)
        for ((turn, key) in turns) {
            val (id, right, bad) = turn
            assertTrue(id, page(id) != null)
            val h = TurnHints.of(DialogLine(choices = listOf(DialogChoice(right, ok = true), wrong(bad, id))), "sl", page)!!
            assertEquals(id, listOf(id), h.pages)
            assertEquals(id, key, h.rule)
            assertTrue(id, h.models.isNotEmpty())
            val t = TurnHints.text(h, page)
            assertTrue("$id: ${t.lines}", t.lines.isNotEmpty())
            assertTrue(id, (t.lines + t.rows + listOfNotNull(t.models)).all { TurnHints.fair(it, h) })
            assertTrue("$id: the model shows the turn's form", h.models.none { m -> h.forms.any { TurnHints.shows(m, it, stem = true) } })
        }
    }

    @Test fun `the year and the day of a told time, pol before an hour, and the two cases' prepositions, as the lens names them`() {
        fun hint(right: String, bad: String, id: String) = TurnHints.of(DialogLine(choices = listOf(DialogChoice(right, ok = true), wrong(bad, id))), "sl", page)!!
        // leta 1890: the year after it decides, the genitive of time (Kdaj?); nekega dne: the determiner before it
        val year = hint("Mlin so zgradili leta 1890.", "Mlin so zgradili leto 1890.", "cas-izrazi")
        assertEquals(Trigger.When("1890", 4), year.trigger)
        assertEquals(listOf(CaseLine("Kdaj?", "hint.qWhen", Case.GEN)), year.cases)
        assertEquals("with «1890»", TurnHints.text(year, page).lines[1])
        assertEquals(Trigger.When("Nekega", 0), hint("Nekega dne je v vas prišel tujec.", "Nekega dan je v vas prišel tujec.", "cas-izrazi").trigger)
        // od leta 2024: the preposition's, not the genitive of time
        assertTrue(CaseTriggers.of("Tukaj živim od leta 2024.", 3).first() is Trigger.Preposition)
        // ob pol dveh: pol decides the next hour's genitive, the time (Kdaj?); čez pol ure: how much (Koliko?)
        val half = hint("Domov gremo ob pol dveh.", "Domov gremo ob pol dva.", "kdaj-cas")
        assertEquals(Trigger.Amount("pol", 3), half.trigger)
        assertEquals(listOf(CaseLine("Kdaj?", "hint.qWhen", Case.GEN)), half.cases)
        assertEquals(listOf(CaseLine("Koliko?", "hint.qHowMany", Case.GEN)), hint("Čez pol ure pridem.", "Čez pol uro pridem.", "rodilnik-kolicina").cases)
        // the spoken hour after ob: the locative ob asks for (ob štiri, written ob štirih)
        assertEquals(listOf(CaseLine("Kdaj?", "hint.qWhen", Case.LOC)), hint("Prav. Jutri spet, ob štiri?", "Prav. Jutri spet, ob štirimi?", "kdaj-cas").cases)
        // po kruh, pod stol: both of the preposition's cases with their questions, the accusative first, and the rule
        val po = hint("Babica je poslala Nejca po kruh.", "Babica je poslala Nejca po kruhu.", "predlogi-dva-sklona")
        assertEquals(listOf(CaseLine("Koga? Kaj?", null, Case.ACC), CaseLine("Pri kom? Pri čem?", null, Case.LOC)), po.cases)
        assertEquals("hint.ruleTwoCases", po.rule)
        val pod = hint("Muri je skočil pod stol.", "Muri je skočil pod stolom.", "predlogi-dva-sklona")
        assertEquals(listOf(CaseLine("Kam?", "hint.qWhereTo", Case.ACC), CaseLine("Kje?", "hint.qWhere", Case.INS)), pod.cases)
        // a word of feeling: zebe, boli take the accusative of who feels it, mudi se the dative
        assertEquals(listOf(Case.ACC), hint("Mamo boli glava.", "Mami boli glava.", "custva").cases.map { it.case })
        assertEquals(listOf(Case.DAT), hint("Janezu se mudi.", "Janez se mudi.", "custva").cases.map { it.case })
    }

    @Test fun `what the trigger table decides that the app's old rules didn't`() {
        // a number of one to four agrees with its noun as an adjective does: after z the instrumental (before: the number
        // dvema decided, the nominative)
        val two = DialogLine(choices = listOf(DialogChoice("Grem z dvema prijateljema.", ok = true), wrong("Grem z dvema prijatelji.", "dvojina-skloni")))
        val h2 = TurnHints.of(two, "sl", page)!!
        assertEquals(Trigger.Preposition("z", 1, listOf(Case.INS, Case.GEN)), h2.trigger)
        assertEquals(listOf(CaseLine("S kom? S čim?", null, Case.INS)), h2.cases)
        // the case is the preposition's, the form the page's: its rule is said too
        assertEquals("hint.ruleDualCases", h2.rule)
        // po is after (po kosilu) or to fetch (po kruh), never where or where to (before: Kje? and Kam?, both)
        val after = DialogLine(choices = listOf(DialogChoice("Po kosilu grem spat.", ok = true), wrong("Po kosilo grem spat.", "mestnik")))
        assertEquals(listOf(CaseLine("Pri kom? Pri čem?", null, Case.LOC)), TurnHints.of(after, "sl", page)!!.cases)
        // blizu, okoli, sredi … are where (rest): Kje? (before: the genitive's own question)
        val near = DialogLine(choices = listOf(DialogChoice("Hiša je blizu cerkve.", ok = true), wrong("Hiša je blizu cerkev.", "rodilnik-predlogi")))
        assertEquals(listOf(CaseLine("Kje?", "hint.qWhere", Case.GEN)), TurnHints.of(near, "sl", page)!!.cases)
        // the table's verbs and words of feeling (zahvaliti se, vseeno: komu?) and its measures (a slice of bread)
        val thanks = DialogLine(choices = listOf(DialogChoice("Mojca se zahvali babici.", ok = true), wrong("Mojca se zahvali babico.", "dajalnik")))
        assertEquals(Trigger.Partner("zahvali", 2, listOf(Case.DAT), "zahvaliti"), TurnHints.of(thanks, "sl", page)!!.trigger)
        val same = DialogLine(choices = listOf(DialogChoice("Teti je vseeno.", ok = true), wrong("Teta je vseeno.", "dajalnik")))
        assertEquals(Trigger.Partner("vseeno", 2, listOf(Case.DAT), "vseeno"), TurnHints.of(same, "sl", page)!!.trigger)
        val slice = DialogLine(choices = listOf(DialogChoice("Rezino kruha, prosim.", ok = true), wrong("Rezino kruh, prosim.", "rodilnik-kolicina")))
        assertEquals(Trigger.Amount("Rezino", 0), TurnHints.of(slice, "sl", page)!!.trigger)
        assertEquals(listOf(CaseLine("Koliko?", "hint.qHowMany", Case.GEN)), TurnHints.of(slice, "sl", page)!!.cases)
        // a verb that takes the dative and the accusative (dati, kupiti: komu? kaj?) names its object too; kupi is buy!,
        // not a heap (kup) of what follows
        val buy = DialogLine(choices = listOf(DialogChoice("Kupi kruh!", ok = true), wrong("Kupi kruha!", "tozilnik")))
        val hb = TurnHints.of(buy, "sl", page)!!
        assertEquals(Trigger.Partner("Kupi", 0, listOf(Case.DAT, Case.ACC), "kupiti"), hb.trigger)
        assertEquals(listOf(CaseLine("Koga? Kaj?", null, Case.ACC)), hb.cases)
        // the dual's imperative (pomagajva: let's help, the two of us)
        val cat = DialogLine(choices = listOf(DialogChoice("Mačka ne more dol. Pomagajva ji!", ok = true), wrong("Mačka ne more dol. Pomagajva jo!", "dajalnik")))
        assertEquals(Trigger.Partner("Pomagajva", 4, listOf(Case.DAT), "pomagati"), TurnHints.of(cat, "sl", page)!!.trigger)
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
        assertEquals(Trigger.Preposition("za", 1, listOf(Case.ACC, Case.INS)), h.trigger)
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

    @Test fun `a name is who does it, Micka in Luka are two, and a tie takes the word a rule names`() {
        val sl = DialogLine(choices = listOf(DialogChoice("Micka kuha juho.", ok = true), wrong("Micka kuham juho.", "glagoli-sedanjik")))
        assertNull(TurnHints.of(sl, "sl", page)!!.trigger) // no cast: a word at the start is no one
        assertEquals(Trigger.Cue("Micka", 0, Trigger.Cue.Kind.WHO), TurnHints.of(sl, "sl", listOf("Babica Micka"), page)!!.trigger)
        val two = DialogLine(choices = listOf(DialogChoice("Micka in Luka gresta domov.", ok = true), wrong("Micka in Luka gremo domov.", "dvojina")))
        assertEquals("Micka in Luka", TurnHints.of(two, "sl", listOf("Micka", "Luka"), page)!!.trigger!!.word)
        // the tie: a wrong choice of meaning at one word (devet), one of a page at another (jajce): the gap is the page's word
        val tie = DialogLine(choices = listOf(DialogChoice("Deset jajc, prosim.", ok = true), DialogChoice("Devet jajc, prosim.", why = "Nine?"), wrong("Deset jajce, prosim.", "stevila-samostalniki")))
        assertEquals("Deset", Forms.turn(tie.choices.map { it.sl }, 0)!!.gap.word) // the texts alone: the first one's word, as before
        assertEquals("jajc", Forms.turn(tie, 0)!!.gap.word)
        assertEquals("jajc", TurnHints.of(tie, "sl", page)!!.right.single())
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.contentOrNull

    /** The learner's turns in a culture file's JSON (a project's step, a story, a party, an arrival), by their Slovene. */
    private fun turnsIn(e: JsonElement): List<DialogLine> = when (e) {
        is JsonObject -> {
            val choices = (e["choices"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            val own = if (choices.size >= 2 && choices.all { str(it, "sl") != null && "{" !in str(it, "sl")!! }) listOf(
                DialogLine(grammar = str(e, "grammar"), choices = choices.map { c ->
                    DialogChoice(str(c, "sl")!!, str(c, "en").orEmpty(), ok = (c["ok"] as? JsonPrimitive)?.booleanOrNull == true, grammar = str(c, "grammar"), tap = str(c, "tap"))
                }),
            ) else emptyList()
            own + e.values.flatMap { turnsIn(it) }
        }
        is JsonArray -> e.flatMap { turnsIn(it) }
        else -> emptyList()
    }

    /**
     * What the tie and the cast change for Slovene's curated turns (docs/plans/05-built/hint-chip-en.md): the scenes', and
     * Primorska's steps, stories, parties and arrivals; the turns whose gap moves to the word a rule names, and those whose
     * hint names a person now; every such turn goes to build/reports/hints/sl-changes.txt for a reader.
     */
    @Test fun `what the tie and the cast change for Slovene's curated turns`() {
        val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "scenes").isDirectory }
        val files = File(root, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().toList() +
            File(root, "cultures").listFiles().orEmpty().flatMap { File(it, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().toList() }
        val primorska = File(root, "cultures/primorska")
        val scenes = files.map { parseScene(it.readText()) }.filter { it.language == "sl" }
        val villagers = File(primorska, "villagers").listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { (json.parseToJsonElement(it.readText()) as? JsonObject)?.let { o -> str(o, "name") } }
        val everyone = (villagers + scenes.flatMap { sc -> sc.people.map { it.name } }).distinct()
        val turns = scenes.flatMap { sc -> sc.dialogs.flatMap { d -> d.lines.filter { it.choices.size >= 2 }.map { "${sc.id}/${d.id}" to it } } } +
            primorska.walk().filter { it.isFile && it.extension == "json" && "scenes" !in it.path.split(File.separatorChar) }.flatMap { f ->
                turnsIn(json.parseToJsonElement(si.lanisce.lani.l10n.Learner.current.renderJson(f.readText()))).map { f.name to it }
            }.toList()
        val report = mutableListOf<String>()
        var moved = 0
        var named = 0
        for ((where, line) in turns) {
            val right = line.choices.indexOfFirst { it.ok }.takeIf { line.choices.count { c -> c.ok } == 1 } ?: continue
            val old = Forms.turn(line.choices.map { it.sl }, right)
            val now = Forms.turn(line, right)
            if (old?.gap?.slot != now?.gap?.slot) {
                moved++
                report += "moved | $where | ${line.choices[right].sl} | ${old?.gap?.word} → ${now?.gap?.word}"
            }
            val bare = TurnHints.of(line, "sl", page)?.trigger
            val h = TurnHints.of(line, "sl", everyone, page) ?: continue
            if (h.trigger != bare) {
                named++
                assertTrue("$where: ${h.trigger}", h.trigger is Trigger.Cue && (h.trigger as Trigger.Cue).kind == Trigger.Cue.Kind.WHO)
                report += "named | $where | ${line.choices[right].sl} | ${TurnHints.text(h, page).lines.firstOrNull()}"
            }
        }
        File("build/reports/hints").apply { mkdirs() }.resolve("sl-changes.txt").writeText(report.joinToString("\n") + "\n")
        println("Slovene: ${turns.size} turns; the tie moves the gap of $moved, the cast names who does it at $named")
    }

    private val CASE_PAGES = setOf(
        "stevila-samostalniki", "rodilnik-kolicina", "rodilnik-nikalnica", "rodilnik-predlogi", "kje-mestnik-orodnik", "kam-tozilnik", "orodnik", "dajalnik",
    )
}
