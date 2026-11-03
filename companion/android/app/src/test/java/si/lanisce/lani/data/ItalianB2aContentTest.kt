package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang

/**
 * Italian B2, units 1 to 6 (the congiuntivo's other tenses): the units as the app plays them and what the grading makes of
 * the mistakes they invite. [ItalianCourseContentTest] grades every right answer; here, the mistakes a slip in the stem
 * would forgive, the spoken forms and the tenses that aren't one letter from each other only by chance.
 */
class ItalianB2aContentTest {
    private val course = checkNotNull(Courses.bundled("it"))
    private fun verdict(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.IT).verdict

    private val pages = mapOf(
        "congiuntivo-passato" to listOf("congiuntivo-passato", "congiuntivo", "passato-prossimo"),
        "congiuntivo-imperfetto" to listOf("congiuntivo-imperfetto", "congiuntivo", "periodo-ipotetico"),
        "congiuntivo-trapassato" to listOf("congiuntivo-trapassato", "congiuntivo-imperfetto", "trapassato"),
        "concordanza-tempi" to listOf("concordanza-tempi", "congiuntivo-passato", "congiuntivo-imperfetto", "congiuntivo-trapassato"),
        "benche-purche" to listOf("congiunzioni-congiuntivo", "congiuntivo-espressioni", "congiunzioni"),
        "magari" to listOf("congiuntivo-indipendente", "congiuntivo-imperfetto"),
    )

    @Test fun `the six units are written at B2, each naming its pages, the outline's first six`() {
        val b2 = course.playable("B2").map { it.id }
        for ((id, want) in pages) {
            val u = course.units.getValue(id)
            assertEquals("$id: its level", "B2", u.level)
            assertEquals("$id: its pages", want, u.grammar)
            assertTrue("$id: playable", id in b2)
            assertTrue("$id: 5 or 6 steps", u.intro?.steps?.size in 5..6)
            assertEquals("$id: three worksheets", 3, u.worksheets.size)
        }
    }

    @Test fun `the new pages are in the bundled book, each with its own card`() {
        val book = Grammar.bundled("it").associateBy { it.id }
        for (id in listOf("congiuntivo-passato", "congiuntivo-imperfetto", "congiuntivo-trapassato", "concordanza-tempi", "congiunzioni-congiuntivo", "congiuntivo-indipendente")) {
            assertTrue("$id: bundled", id in book)
            assertTrue("$id: a hand-written card", book.getValue(id).card != null)
        }
    }

    @Test fun `another conjugation's vowel in the imperfect subjunctive is wrong, never a slip`() {
        for ((typed, right) in listOf(
            "dormesse" to "dormisse", "parlessi" to "parlassi", "leggasse" to "leggesse", "dormassero" to "dormissero",
            "stassi" to "stessi", "stasse" to "stesse", "dasse" to "desse", "dicasse" to "dicesse", "bevasse" to "bevesse", "parleste" to "parlaste",
        )) {
            assertEquals("$typed for $right", Verdict.WRONG, verdict(typed, right))
        }
    }

    @Test fun `the imperfect subjunctive and the remote past one letter apart are wrong, only for each other`() {
        for ((typed, right) in listOf(
            "desti" to "dessi", "dessi" to "desti", "deste" to "desse", "desse" to "deste", "dissi" to "dessi", "disse" to "desse", "foste" to "fosse", "fosse" to "foste",
            "avesti" to "avessi", "venisti" to "venissi", "parlaste" to "parlasse", "fosti" to "fossi", "dicesti" to "dicessi",
        )) {
            assertEquals("$typed for $right", Verdict.WRONG, verdict(typed, right))
        }
        assertEquals("a slip in the word stays one", Verdict.ALMOST, verdict("fose", "fosse"))
    }

    @Test fun `the person's ending is never a slip - fosse for fossi, fossero for fossimo, abbia for abbiano`() {
        for ((typed, right) in listOf(
            "fosse" to "fossi", "fossi" to "fosse", "fossero" to "fossimo", "fossimo" to "fossero", "avesse" to "avessi", "avessero" to "avessimo",
            "abbia" to "abbiano", "abbiano" to "abbiamo", "sia" to "siano", "venisse" to "venissi", "parlassero" to "parlassimo",
        )) {
            assertEquals("$typed for $right", Verdict.WRONG, verdict(typed, right))
        }
    }

    @Test fun `the helping verb and the participle's agreement are wrong in two words too`() {
        assertEquals(Verdict.WRONG, verdict("abbia partito", "sia partito"))
        assertEquals(Verdict.WRONG, verdict("sia mangiato", "abbia mangiato"))
        assertEquals(Verdict.WRONG, verdict("sia partito", "sia partita"))
        assertEquals(Verdict.WRONG, verdict("siano partite", "siano partiti"))
        assertEquals(Verdict.WRONG, verdict("avesse partito", "fosse partito"))
        assertEquals(Verdict.WRONG, verdict("abbia preso", "abbia presa"))
    }

    @Test fun `no gap offers the spoken indicative as a wrong word - it is labelled, the subjunctive its standard`() {
        val spoken = mutableListOf<String>()
        for (id in pages.keys) {
            val u = course.units.getValue(id)
            for (e in u.worksheets.flatMap { it.exercises } + u.test.exercises) {
                if (e is Exercise.Sort) continue
                for (item in Sheets.items(e, Lang.IT)) {
                    for (s in item.spoken) {
                        val m = Sheets.check(item, s, Lang.IT)
                        assertEquals("$id: «$s» is right", Verdict.CORRECT, m.verdict)
                        assertEquals("$id: «$s» labelled with the standard", item.accept.first(), m.standard)
                        spoken += s
                    }
                }
            }
        }
        for (form in listOf("ha preso", "era", "veniva", "venga", "erano andati", "aveva preparato")) assertTrue("spoken $form", form in spoken)
    }

    @Test fun `the later tense that isn't made yet is accepted where it fits`() {
        val u = course.units.getValue("concordanza-tempi")
        val ask = u.intro!!.steps[3].ask as Exercise.Cloze
        assertEquals(listOf("tornasse", "sarebbe tornato"), ask.accept)
        assertEquals(Verdict.CORRECT, verdict("sarebbe tornato", *ask.accept.toTypedArray()))
    }
}
