package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang

/**
 * Italian B2's units 7–12 (docs/plans/05-built/course-it-b2-b.md): the past conditional, se potessi, se avessi saputo, reported
 * speech after a past, telling a story, the past gerund, with their new pages and the mistakes they added to grading/it.json.
 * What every unit of the course must do (played at its level, every right answer right, no distractor even almost right, each
 * base there) is [ItalianCourseContentTest]'s; this is what these six add.
 */
class ItalianB2bContentTest {
    private val ids = listOf("condizionale-passato", "se-potessi", "se-avessi-saputo", "disse-che", "raccontare", "gerundio-passato")
    private val course = checkNotNull(Courses.bundled("it")) { "the bundled Italian course" }
    private val bases = listOf("de", "sl", "en")

    private fun it(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.IT)
    private fun wrong(answer: String, right: String) = assertEquals("$answer for $right", Verdict.WRONG, it(answer, right).verdict)
    private fun slip(answer: String, right: String) {
        val r = it(answer, right)
        assertEquals("$answer for $right", Verdict.ALMOST to Hint.TYPO, r.verdict to r.hint)
    }

    private fun exercises(unit: String): List<Exercise> {
        val u = checkNotNull(course.units[unit]) { unit }
        return u.worksheets.flatMap { it.exercises } + u.test.exercises
    }

    private fun sheetItems(unit: String): List<SheetItem> = exercises(unit).filter { it !is Exercise.Sort }.flatMap { Sheets.items(it, Lang.IT) }

    @Test fun `units 7 to 12 of B2 are written in the outline's order, at the size of the level`() {
        assertEquals(ids, course.refs("B2").subList(6, 12).map { it.id })
        for (id in ids) {
            val u = checkNotNull(course.units[id]) { "$id isn't written" }
            assertEquals(id, "B2", u.level)
            assertTrue("$id: ${u.intro?.steps?.size} steps", u.intro?.steps?.size in 5..6)
            assertEquals("$id: worksheets", 3, u.worksheets.size)
            assertEquals("$id: pass", 80, u.test.pass)
            assertTrue("$id: flagged for a native check", u.machineWritten && u.review.orEmpty().contains("native speaker"))
        }
    }

    @Test fun `each new page has a card of its own in every base, and is the first page of the unit that brings it`() {
        val book = Grammar.bundled("it").associateBy { it.id }
        val first = mapOf(
            "condizionale-passato" to "condizionale-passato",
            "periodo-ipotetico-irrealta" to "se-avessi-saputo",
            "discorso-indiretto-passato" to "disse-che",
            "raccontare" to "raccontare",
            "gerundio-passato" to "gerundio-passato",
        )
        for ((id, unit) in first) {
            val page = checkNotNull(book[id]) { "no page $id" }
            assertEquals(id, "B2", page.level)
            val card = checkNotNull(page.card) { "$id: no card of its own" }
            for (b in bases) {
                assertTrue("$id: the card's rule in $b", !card.rule[b].isNullOrBlank())
                assertTrue("$id: the card's tip in $b", !card.tip?.get(b).isNullOrBlank())
            }
            assertEquals(id, course.units.getValue(unit).grammar.first())
        }
        assertEquals("periodo-ipotetico", course.units.getValue("se-potessi").grammar.first())
    }

    @Test fun `with all of B2 merged, the if-sentences name the congiuntivo's tenses they use`() {
        assertEquals("congiuntivo-imperfetto", course.units.getValue("se-potessi").grammar.last())
        assertEquals("congiuntivo-trapassato", course.units.getValue("se-avessi-saputo").grammar.last())
    }

    @Test fun `the imperfect subjunctive with another conjugation's vowel is wrong, on the same stem only`() {
        for ((typed, right) in listOf(
            "dormessi" to "dormissi", "parlessi" to "parlassi", "dicassi" to "dicessi", "facassi" to "facessi", "bevassi" to "bevessi",
            "dassi" to "dessi", "stassi" to "stessi", "potassi" to "potessi", "avassi" to "avessi", "sapasse" to "sapesse",
            "venasse" to "venisse", "parlesse" to "parlasse", "parlessero" to "parlassero", "dormessero" to "dormissero",
            "parleste" to "parlaste", "dormeste" to "dormiste", "parlessimo" to "parlassimo", "desse" to "disse",
        )) wrong(typed, right)
        // a slip elsewhere in such a word stays one, and a superlative isn't a subjunctive
        slip("palassi", "parlassi")
        slip("vanisse", "venisse")
        slip("bellessimo", "bellissimo")
        // a short noun's vowel stays a slip: the rule is for a verb's stem
        for ((typed, right) in listOf("sessi" to "sassi", "pessi" to "passi", "fiste" to "feste", "tiste" to "teste", "treste" to "triste", "clesse" to "classe", "quaste" to "queste", "gassi" to "gessi")) slip(typed, right)
    }

    @Test fun `the other persons of the imperfect subjunctive and se's conditional are wrong, never a slip`() {
        for ((typed, right) in listOf(
            "potesse" to "potessi", "potessi" to "potesse", "fosse" to "fossi", "fosti" to "fossi", "avesti" to "avessi", "aveste" to "avesse",
            "parlaste" to "parlasse", "parlasse" to "parlaste", "foste" to "fosse", "fosse" to "foste", "deste" to "desse", "desti" to "dessi", "sarei" to "fossi", "potrei" to "potessi", "avrei" to "avessi",
        )) wrong(typed, right)
    }

    @Test fun `the past conditional's helper - the future's forms and the other persons are wrong`() {
        for ((typed, right) in listOf(
            "avremo mangiato" to "avremmo mangiato", "saremo venuti" to "saremmo venuti", "avrete mangiato" to "avreste mangiato",
            "sarete venuti" to "sareste venuti", "avrai mangiato" to "avrei mangiato", "sarai venuto" to "sarei venuto",
            "avrebbe mangiato" to "avrebbero mangiato", "sarebbe venuto" to "sarebbe venuta", "sarebbero venuti" to "sarebbero venute",
        )) wrong(typed, right)
    }

    @Test fun `the remote past against the imperfetto and the future on the same stem is wrong`() {
        for ((typed, right) in listOf(
            "mangiavano" to "mangiarono", "mangiarono" to "mangiavano", "parlavano" to "parlarono", "dormivano" to "dormirono",
            "partiranno" to "partirono", "partirono" to "partiranno", "mangiavamo" to "mangiammo", "credevano" to "crederono",
            "vede" to "vide", "vide" to "vede", "mese" to "mise",
        )) wrong(typed, right)
        // the accent stays almost, as since B1
        assertEquals(Verdict.ALMOST to Hint.ACCENT, it("ando", "andò").let { it.verdict to it.hint })
        assertEquals(Verdict.ALMOST to Hint.ACCENT, it("parti", "partì").let { it.verdict to it.hint })
    }

    @Test fun `the past gerund - avando and essando are wrong, avendo for essendo too, a slip stays one`() {
        wrong("avando", "avendo")
        wrong("essando", "essendo")
        wrong("avandolo", "avendolo")
        wrong("avendo arrivato", "essendo arrivato")
        slip("avendo fnito", "avendo finito")
    }

    @Test fun `the double imperfetto is right and labelled in the irreal if-sentence, with its mixes, and never an option`() {
        val line = sheetItems("se-avessi-saputo").first { it.accept.first() == "Se Lorenzo l'avesse saputo, sarebbe venuto." }
        for (said in listOf("Se Lorenzo lo sapeva, veniva.", "Se Lorenzo lo sapeva, sarebbe venuto.", "Se Lorenzo l'avesse saputo, veniva.")) {
            val m = Sheets.check(line, said, Lang.IT)
            assertEquals(said, Verdict.CORRECT to line.accept.first(), m.verdict to m.standard)
        }
        assertEquals(Verdict.WRONG, Sheets.check(line, "Se Lorenzo l'avrebbe saputo, sarebbe venuto.", Lang.IT).verdict)
    }

    @Test fun `no choice of the two units with spoken forms offers one, right or wrong`() {
        for (unit in listOf("se-avessi-saputo", "disse-che")) {
            val said = sheetItems(unit).flatMap { it.spoken }.map { Grading.canonical(it, Lang.IT) }.toSet()
            assertTrue("$unit has spoken forms", said.isNotEmpty())
            for (c in exercises(unit).filterIsInstance<Exercise.Choice>()) {
                for (o in c.options) assertFalse("$unit: «$o» in «${c.prompt}»", Grading.canonical(o, Lang.IT) in said)
            }
        }
    }

    @Test fun `after a past the future is the past conditional - the plain conditional is wrong, the spoken imperfetto right and labelled`() {
        val line = sheetItems("disse-che").first { it.accept.first() == "Davide disse che sarebbe partito il giorno dopo." }
        assertEquals(Verdict.WRONG, Sheets.check(line, "Davide disse che partirebbe il giorno dopo.", Lang.IT).verdict)
        val m = Sheets.check(line, "Davide disse che partiva il giorno dopo.", Lang.IT)
        assertEquals(Verdict.CORRECT to line.accept.first(), m.verdict to m.standard)
    }
}
