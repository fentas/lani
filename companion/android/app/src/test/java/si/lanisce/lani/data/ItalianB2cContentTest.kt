package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang

/**
 * Italian B2's last six units (infinito-passato to connettivi: docs/plans/05-built/course-it-b2-c.md) as the app grades
 * them: a participle's ending is never a slip, a pronoun's place is grammar, what a keyboard lacks stays almost.
 */
class ItalianB2cContentTest {
    private fun it(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.IT)

    /** A sheet's item with [accept] and [spoken]: what the sheet makes of [answer]. */
    private fun sheet(answer: String, accept: List<String>, spoken: List<String> = emptyList()) =
        Sheets.check(SheetItem(accept, null, null, null, "", spoken = spoken), answer, Lang.IT)

    @Test fun `dopo aver and dopo essere - the participle's ending is the ending`() {
        assertEquals(Verdict.CORRECT, it("Dopo avere mangiato, Bruno torna.", "Dopo aver mangiato, Bruno torna.", "Dopo avere mangiato, Bruno torna.").verdict)
        assertEquals(Verdict.WRONG, it("Dopo essere arrivato, Marisa apre.", "Dopo essere arrivata, Marisa apre.").verdict)
        assertEquals(Verdict.WRONG, it("arrivati", "arrivate").verdict)
        assertEquals(Verdict.WRONG, it("averlo", "averla").verdict)
        assertEquals(Verdict.WRONG, it("averle", "averli").verdict)
        assertEquals(Verdict.WRONG, it("Finito la cena, Bepi racconta.", "Finita la cena, Bepi racconta.").verdict)
        assertEquals(Verdict.WRONG, it("Finite", "Finiti").verdict)
        assertEquals(Verdict.WRONG, it("aprita", "aperta").verdict)
        assertEquals(Verdict.WRONG, it("Dopo aver arrivato", "Dopo essere arrivato").verdict)
        // a slip in the stem stays one
        assertEquals(Verdict.ALMOST, it("mangato", "mangiato").verdict)
        assertEquals(Verdict.ALMOST, it("esersi", "essersi").verdict)
    }

    @Test fun `fare and lasciare - the pronoun's form and place are grammar`() {
        assertEquals(Verdict.WRONG, it("Ti la faccio vedere.", "Te la faccio vedere.").verdict)
        assertEquals(Verdict.WRONG, it("Faccio vederlo.", "Lo faccio vedere.").verdict)
        assertEquals(Verdict.WRONG, it("Glielo faccio vedere.", "Gliela faccio vedere.").verdict)
        assertEquals(Verdict.WRONG, it("Glila faccio vedere.", "Gliela faccio vedere.").verdict)
        assertEquals(Verdict.WRONG, it("fammilo", "fammelo").verdict)
        assertEquals(Verdict.WRONG, it("fartilo", "fartelo").verdict)
        assertEquals(Verdict.WRONG, it("L'ho fatto riparare.", "L'ho fatta riparare.").verdict)
        assertEquals(Verdict.WRONG, it("Giulia si è fatto tagliare i capelli.", "Giulia si è fatta tagliare i capelli.").verdict)
        // an apostrophe left out, where the participle agrees, stays almost
        assertEquals(Verdict.ALMOST, it("La ho fatta riparare.", "L'ho fatta riparare.").verdict)
        assertEquals(Verdict.ALMOST, it("Lo ho fatto riparare.", "L'ho fatto riparare.").verdict)
    }

    @Test fun `venire, andare and si - who and how many`() {
        assertEquals(Verdict.WRONG, it("vengono contati", "vengono contate").verdict)
        assertEquals(Verdict.WRONG, it("viene contate", "vengono contate").verdict)
        assertEquals(Verdict.WRONG, it("Il ponte è venuto riparato.", "Il ponte è stato riparato.").verdict)
        assertEquals(Verdict.WRONG, it("I bicchieri va lavati.", "I bicchieri vanno lavati.").verdict)
        assertEquals(Verdict.WRONG, it("Qui si vende i biscotti.", "Qui si vendono i biscotti.").verdict)
        assertEquals(Verdict.WRONG, it("Si sono venduto tutti i biscotti.", "Si sono venduti tutti i biscotti.").verdict)
        assertEquals(Verdict.WRONG, it("vengano", "vengono").verdict)
        // the accent a keyboard lacks stays almost
        assertEquals(Verdict.ALMOST, it("La piazza verra chiusa.", "La piazza verrà chiusa.").verdict)
    }

    @Test fun `il quale, chi and chiunque - the agreement is grammar, the spoken indicative right and labelled`() {
        assertEquals(Verdict.WRONG, it("quali", "quale").verdict)
        assertEquals(Verdict.WRONG, it("il cui", "la cui").verdict)
        assertEquals(Verdict.WRONG, it("chi", "che").verdict)
        val arriva = sheet("arriva", listOf("arrivi"), spoken = listOf("arriva"))
        assertEquals(Verdict.CORRECT to "arrivi", arriva.verdict to arriva.standard)
        val succede = sheet("succede", listOf("succeda"), spoken = listOf("succede"))
        assertEquals(Verdict.CORRECT to "succeda", succede.verdict to succede.standard)
    }

    @Test fun `a word's making - the known mistakes are mistakes, each only for a word of its kind`() {
        for ((typed, right) in listOf(
            "facilemente" to "facilmente", "gentilemente" to "gentilmente", "regolaremente" to "regolarmente", "lentomente" to "lentamente",
            "felicamente" to "felicemente", "dolcamente" to "dolcemente", "inpossibile" to "impossibile", "inbevibile" to "imbevibile",
            "inregolare" to "irregolare", "inlegale" to "illegale", "barcetta" to "barchetta",
        )) assertEquals("$typed for $right", Verdict.WRONG, it(typed, right).verdict)
        // a slip stays one: another slip in such a word, and the same letters where the rule doesn't apply
        for ((typed, right) in listOf("facilmnte" to "facilmente", "lentamnte" to "lentamente", "imposibile" to "impossibile", "veremente" to "veramente", "portne" to "portone"))
            assertEquals("$typed for $right", Verdict.ALMOST, it(typed, right).verdict)
        assertEquals(Verdict.WRONG, it("portona", "portone").verdict)
    }

    @Test fun `connectives and the front of a sentence - the pronoun that repeats it is grammar`() {
        assertEquals(Verdict.WRONG, it("Il pane ho comprato.", "Il pane l'ho comprato.").verdict)
        assertEquals(Verdict.WRONG, it("Zia Nives l'ho visto in piazza.", "Zia Nives l'ho vista in piazza.").verdict)
        assertEquals(Verdict.WRONG, it("Le chiavi le ho preso.", "Le chiavi le ho prese.").verdict)
        assertEquals(Verdict.WRONG, it("Le ringrazio.", "La ringrazio.").verdict)
        val chiunque = sheet("Chiunque", listOf("Chi"), spoken = listOf("Chiunque"))
        assertEquals(Verdict.CORRECT to "Chi", chiunque.verdict to chiunque.standard)
    }
}
