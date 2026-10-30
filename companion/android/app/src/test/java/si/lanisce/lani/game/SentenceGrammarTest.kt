package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.CaseLens
import si.lanisce.lani.data.CaseRead
import si.lanisce.lani.data.CaseRow
import si.lanisce.lani.data.Words

/**
 * "🔍 Slovnica stavka · The sentence's grammar" (companion/SCENES.md): each word of a line with its reading and, for a
 * word with a case, what the case lens says of it in the line (GET /lookup's `case`, these answers the bridge's own:
 * companion/bridge/src/case-lens.ts) — the word card's row, so the sheet and the card can never disagree; a verb's
 * person and tense.
 */
class SentenceGrammarTest {
    /** Every page: the rows link what the lens names. */
    private val any = { _: String -> true }

    private fun lens(json: String): CaseRead = Words.parse("""{"word":"w","entries":[],"case":$json}""").lens!!

    private fun r(lemma: String, pos: String, grammar: String? = null, case: String? = null) = WordReading(lemma, pos, grammar, case?.let(::lens))

    private fun rows(sentence: String, vararg readings: WordReading?) = SentenceGrammar.analyze(sentence, readings.toList(), any)

    private val brezMleka = """{"i":3,"word":"mleka","start":16,"end":21,"readings":[{"lemma":"mleko","pos":"noun","case":"gen","number":"sg","genders":["n"]}],"pick":{"case":"gen","number":"sg","gender":"n","as":"preposition"},"sure":"decided","kind":"preposition","trigger":{"i":2,"word":"brez","start":11,"end":15},"sense":"other","rule":"rodilnik-predlogi","options":[{"case":"gen","number":"sg","gender":"n","as":"preposition"}],"out":[]}"""
    private val brez = """{"i":2,"word":"brez","start":11,"end":15,"governs":{"i":2,"word":"brez","start":11,"end":15,"words":[{"i":3,"word":"mleka","start":16,"end":21,"case":"gen"}]}}"""
    private val vSotor = """{"i":3,"word":"šotor","start":16,"end":21,"readings":[{"lemma":"šotor","pos":"noun","case":"acc","number":"sg","genders":["m"]}],"pick":{"case":"acc","number":"sg","as":"preposition"},"sure":"decided","kind":"preposition","trigger":{"i":2,"word":"v","start":14,"end":15},"sense":"motion","rule":"kam-tozilnik","options":[{"case":"acc","number":"sg","as":"preposition"}],"out":[{"case":"loc","number":"sg","why":"preposition"}]}"""
    private val podMizo = """{"i":1,"word":"mizo","start":4,"end":8,"readings":[],"pick":{"case":"ins","number":"sg","gender":"f","as":"preposition"},"sure":"likely","kind":"preposition","trigger":{"i":0,"word":"Pod","start":0,"end":3},"sense":"rest","rule":"kje-mestnik-orodnik","options":[{"case":"ins","number":"sg","gender":"f","as":"preposition"},{"case":"acc","number":"sg","gender":"f","as":"preposition"}],"out":[]}"""
    private val les = """{"i":1,"word":"les","start":7,"end":10,"readings":[],"pick":null,"sure":"ambiguous","kind":"form","rule":"skloni","options":[{"case":"nom","number":"sg","as":"form"},{"case":"acc","number":"sg","as":"form"}],"out":[]}"""
    private val leta = """{"i":5,"word":"leta","start":38,"end":42,"readings":[{"lemma":"leto","pos":"noun","case":"gen","number":"sg","genders":["n"]}],"pick":{"case":"gen","number":"sg","as":"when"},"sure":"decided","kind":"when","trigger":{"i":6,"word":"1844","start":43,"end":47},"rule":"cas-izrazi","options":[{"case":"gen","number":"sg","as":"when"}],"out":[]}"""
    private val pomagamBabici = """{"i":1,"word":"babici","start":8,"end":14,"readings":[],"pick":{"case":"dat","number":"sg","as":"verb"},"sure":"decided","kind":"verb","trigger":{"i":0,"word":"Pomagam","start":0,"end":7,"lemma":"pomagati"},"rule":"dajalnik","options":[{"case":"dat","number":"sg","as":"verb"}],"out":[{"case":"loc","number":"sg","why":"needs-preposition"}]}"""
    private val unresolved = """{"i":2,"word":"jurček","start":9,"end":15,"readings":[],"pick":null,"sure":"ambiguous","kind":"unresolved","rule":"imenovalnik","options":[{"case":"nom","number":"sg","as":"unresolved"}],"out":[]}"""

    @Test fun `a word with a case says what the lens says, the word card's row, and its page`() {
        val rows = rows("Kavo pijem brez mleka.", r("kava", "noun", "accusative singular"), r("piti", "verb", "first-person singular present"), r("brez", "prep", case = brez), r("mleko", "noun", "genitive singular", case = brezMleka))
        assertEquals(listOf("Kavo", "pijem", "brez", "mleka"), rows.map { it.word })
        val milk = rows[3]
        assertEquals("genitive singular", milk.reading)
        assertEquals(Case.GEN, milk.why?.case)
        assertEquals(CaseRow.State.DECIDED, milk.why?.lens?.state)
        assertEquals(CaseLine("Koga? Česa?", null, Case.GEN), milk.why?.line)
        assertEquals("rodilnik-predlogi", milk.page)
        // the same line as the card's: the lens's why, in the base
        assertEquals("Koga? Česa? → 2. rodilnik (genitive) · After the preposition «brez»: the genitive.", SentenceGrammar.why(milk.why!!))
        assertEquals(CaseLens.why(CaseLens.row(lens(brezMleka))!!, lens(brezMleka)), CaseLens.why(milk.why.lens!!, milk.why.read!!))
        // the preposition itself: what it decides
        assertEquals(CaseRow.State.GOVERNS, rows[2].why?.lens?.state)
        assertEquals("Zahteva rodilnik: «mleka» · Takes the genitive: «mleka»", SentenceGrammar.why(rows[2].why!!))
        // a word the lookup gave no case: its reading alone
        assertEquals("accusative singular", rows[0].reading)
        assertNull(rows[0].why)
        assertNull(rows[0].page)
        // a verb: who does it
        assertEquals("jaz", rows[1].why?.person)
        assertEquals("glagoli-sedanjik", rows[1].page)
        assertEquals("who: jaz", SentenceGrammar.why(rows[1].why!!))
    }

    @Test fun `where to and where, by the preposition's sense, and a likely case says so`() {
        val tent = rows("Seveda, pridi v šotor!", r("seveda", "adv"), r("priti", "verb", "second-person singular imperative"), r("v", "prep"), r("šotor", "noun", case = vSotor))[3]
        assertEquals("accusative singular", tent.reading)
        assertEquals(CaseLine("Kam?", "hint.qWhereTo", Case.ACC), tent.why?.line)
        assertEquals("kam-tozilnik", tent.page)
        assertEquals("Kam? (where to? German: wohin?) → 4. tožilnik (accusative) · After the preposition «v»: the accusative. (where to?)", SentenceGrammar.why(tent.why!!))
        val table = rows("Pod mizo!", r("pod", "prep"), r("miza", "noun", case = podMizo))[1]
        assertEquals(CaseRow.State.LIKELY, table.why?.lens?.state)
        assertEquals("instrumental singular", table.reading)
        assertEquals("Kje?", table.why?.line?.question)
        assertTrue(SentenceGrammar.why(table.why!!)!!.startsWith("Kje? (where? German: wo?) → 6. orodnik (instrumental) (likely)"))
    }

    @Test fun `a word the line leaves open lists its readings, and one it fits none of says nothing`() {
        val wood = rows("Kamni, les, kolo …", r("kamen", "noun"), r("les", "noun", case = les), r("kolo", "noun"))[1]
        assertEquals(CaseRow.State.AMBIGUOUS, wood.why?.lens?.state)
        assertEquals("nominative singular; accusative singular", wood.reading)
        assertNull(wood.why?.line)
        assertNull(SentenceGrammar.why(wood.why!!))
        assertEquals("skloni", wood.page)
        // unresolved: no row of the lens, the dictionary's reading only (the card shows none either)
        val mushroom = rows("Vzemi ta jurček.", r("vzeti", "verb", "second-person singular imperative"), r("ta", "det"), r("jurček", "noun", "nominative singular", case = unresolved))[2]
        assertNull(mushroom.why)
        assertEquals("nominative singular", mushroom.reading)
    }

    @Test fun `the genitive of time, a verb that takes the dative, and a page the book lacks isn't linked`() {
        val year = rows("France Prešeren je Zdravljico napisal leta 1844.", null, null, null, null, null, r("leto", "noun", case = leta), null)[5]
        assertEquals(CaseLine("Kdaj?", "hint.qWhen", Case.GEN), year.why?.line)
        assertEquals("cas-izrazi", year.page)
        assertEquals("Kdaj? (when?) → 2. rodilnik (genitive) · When? The genitive of time («1844»): leta 1844, nekega dne.", SentenceGrammar.why(year.why!!))
        val help = rows("Pomagam babici.", r("pomagati", "verb", "first-person singular present"), r("babica", "noun", "dative/locative singular", case = pomagamBabici))[1]
        assertEquals("dative singular", help.reading)
        assertEquals("Komu? Čemu? → 3. dajalnik (dative) · The verb «Pomagam» (pomagati) takes the dative.", SentenceGrammar.why(help.why!!))
        assertNull(SentenceGrammar.analyze("Pomagam babici.", listOf(null, r("babica", "noun", case = pomagamBabici))) { false }[1].page)
    }

    @Test fun `without the dictionary the words alone, and a verb's tense still by its reading`() {
        val offline = SentenceGrammar.analyze("Iz Nemčije. Dvanajst krav.", emptyList(), any)
        assertEquals(listOf("Iz", "Nemčije", "Dvanajst", "krav"), offline.map { it.word })
        assertTrue(offline.all { it.why == null && it.page == null && it.reading == null })
        val past = rows("Bil sem v gozdu.", r("biti", "verb", "masculine singular l-participle"), r("biti", "verb", "first-person singular present"), r("v", "prep"), r("gozd", "noun", "dative/locative singular"))
        assertEquals("pretekli-cas", past[0].page)
        assertEquals("dative/locative singular", past[3].reading)
        assertNull(past[3].why)
    }
}
