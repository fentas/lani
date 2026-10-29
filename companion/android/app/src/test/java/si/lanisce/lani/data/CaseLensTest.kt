package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Case
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.words.CaseLook
import si.lanisce.lani.ui.words.WordToken
import si.lanisce.lani.ui.words.WordTokens

/**
 * The case lens in the app (data/CaseLens.kt): GET /lookup's `case` as the bridge sends it (companion/bridge/src/cases.ts,
 * these answers its own) read into the word card's row in each of its states, the tapped word's place in its line, the
 * colours' marks (POST /cases) and how a case is named.
 */
class CaseLensTest {
    private fun lookupWith(case: String?) = Words.parse(
        """{"word":"w","entries":[{"lemma":"w","pos":"noun","gloss":["x"],"source":"wiktionary"}]${case?.let { ",\"case\":$it" } ?: ""}}""",
    )

    private val brezMleka = """{"i":3,"word":"mleka","start":16,"end":21,"readings":[{"lemma":"mleko","pos":"noun","case":"gen","number":"sg","genders":["n"]}],"pick":{"case":"gen","number":"sg","gender":"n","as":"preposition"},"sure":"decided","kind":"preposition","trigger":{"i":2,"word":"brez","start":11,"end":15},"sense":"other","rule":"rodilnik-predlogi","options":[{"case":"gen","number":"sg","gender":"n","as":"preposition"}],"out":[]}"""
    private val podMizo = """{"i":1,"word":"mizo","start":4,"end":8,"readings":[{"lemma":"miza","pos":"noun","case":"acc","number":"sg","genders":["f"]},{"lemma":"miza","pos":"noun","case":"ins","number":"sg","genders":["f"]}],"pick":{"case":"ins","number":"sg","gender":"f","as":"preposition"},"sure":"likely","kind":"preposition","trigger":{"i":0,"word":"Pod","start":0,"end":3},"sense":"rest","rule":"kje-mestnik-orodnik","options":[{"case":"ins","number":"sg","gender":"f","as":"preposition"},{"case":"acc","number":"sg","gender":"f","as":"preposition"}],"out":[]}"""
    private val doVasi = """{"i":4,"word":"vasi","start":16,"end":20,"readings":[],"pick":{"case":"gen","number":"sg","gender":"f","as":"preposition"},"sure":"likely","kind":"preposition","trigger":{"i":3,"word":"do","start":13,"end":15},"sense":"towards","rule":"rodilnik-predlogi","options":[{"case":"gen","number":"sg","gender":"f","as":"preposition"},{"case":"gen","number":"pl","gender":"f","as":"preposition"}],"out":[{"case":"nom","number":"du","why":"dual"},{"case":"gen","number":"du","why":"dual"},{"case":"acc","number":"du","why":"dual"},{"case":"nom","number":"pl","why":"preposition"},{"case":"acc","number":"pl","why":"preposition"},{"case":"dat","number":"sg","why":"preposition"},{"case":"loc","number":"sg","why":"preposition"}]}"""
    private val brez = """{"i":2,"word":"brez","start":11,"end":15,"words":[{"i":3,"word":"mleka","start":16,"end":21,"case":"gen"}],"governs":{"i":2,"word":"brez","start":11,"end":15,"words":[{"i":3,"word":"mleka","start":16,"end":21,"case":"gen"}]}}"""
    private val les = """{"i":1,"word":"les","start":7,"end":10,"readings":[],"pick":null,"sure":"ambiguous","kind":"form","rule":"skloni","options":[{"case":"nom","number":"sg","gender":"m","as":"form"},{"case":"acc","number":"sg","gender":"m","as":"form"}],"out":[]}"""
    private val veliko = """{"i":4,"word":"veliko","start":18,"end":24,"readings":[{"lemma":"velik","pos":"adj","case":"ins","number":"sg","genders":["f"]}],"pick":{"case":"ins","number":"sg","gender":"f","as":"preposition"},"sure":"decided","kind":"preposition","trigger":{"i":3,"word":"pod","start":14,"end":17},"sense":"rest","rule":"kje-mestnik-orodnik","options":[{"case":"ins","number":"sg","gender":"f","as":"preposition"}],"out":[{"case":"acc","number":"sg","why":"preposition"}],"agrees":{"i":5,"word":"smreko","start":25,"end":31}}"""

    @Test fun `a word the line decides is one case, its trigger, its rule`() {
        val read = lookupWith(brezMleka).lens!!
        val row = CaseLens.row(read)!!
        assertEquals(CaseRow.State.DECIDED, row.state)
        assertEquals(Case.GEN, row.case)
        assertEquals("sg", row.number)
        assertEquals("preposition", row.kind)
        assertEquals("brez", row.trigger?.word)
        assertEquals(11, row.trigger?.start)
        assertEquals("rodilnik-predlogi", row.rule)
        // a noun's gender is on its entry, not the case's line
        assertEquals(listOf(CaseChoice(Case.GEN, "sg", null, role = "preposition")), row.options)
        assertNull(row.gender)
        assertTrue(row.others.isEmpty())
    }

    @Test fun `a likely one comes first with the others it leaves, and the form's other readings ruled out`() {
        val pod = CaseLens.row(lookupWith(podMizo).lens)!!
        assertEquals(CaseRow.State.LIKELY, pod.state)
        assertEquals(Case.INS, pod.case)
        assertEquals(listOf(Case.INS, Case.ACC), pod.options.map { it.case })
        val vasi = CaseLens.row(lookupWith(doVasi).lens)!!
        assertEquals(CaseRow.State.LIKELY, vasi.state)
        assertEquals(listOf("sg", "pl"), vasi.options.map { it.number })
        // what the form could be otherwise, each once, at most four: the dual first (as the bridge says why)
        assertEquals(CaseLens.MAX_OTHERS, vasi.others.size)
        assertEquals(listOf(Case.NOM to "du", Case.GEN to "du", Case.ACC to "du", Case.NOM to "pl"), vasi.others.map { it.case to it.number })
        assertEquals("dual", vasi.others.first().why)
    }

    @Test fun `a form the line can't tell keeps every reading and picks none`() {
        val row = CaseLens.row(lookupWith(les).lens)!!
        assertEquals(CaseRow.State.AMBIGUOUS, row.state)
        assertEquals(listOf(Case.NOM, Case.ACC), row.options.map { it.case })
        assertEquals(Case.NOM, row.case) // shown first, never as the answer
    }

    @Test fun `a preposition tapped says what it decides`() {
        val row = CaseLens.row(lookupWith(brez).lens)!!
        assertEquals(CaseRow.State.GOVERNS, row.state)
        assertNull(row.case)
        assertEquals(1, row.governs.size)
        val (ref, case) = row.governs.single()
        assertEquals("mleka", ref.word)
        assertEquals(16, ref.start)
        assertEquals(Case.GEN, case)
    }

    @Test fun `an adjective agrees with its noun and shows its gender`() {
        val row = CaseLens.row(lookupWith(veliko).lens)!!
        assertEquals(Case.INS, row.case)
        assertEquals("f", row.gender)
        assertEquals("smreko", row.agrees?.word)
        assertEquals(listOf(Case.ACC to "sg"), row.others.map { it.case to it.number })
    }

    @Test fun `no row from an older bridge, a word without a case, or one the line fits none of`() {
        assertNull(lookupWith(null).lens)
        assertNull(CaseLens.row(null))
        assertNull(CaseLens.row(lookupWith("""{"i":0,"word":"x","start":0,"end":1,"pick":null,"sure":"ambiguous","kind":"unresolved","rule":"skloni","options":[{"case":"gen","number":"sg"}]}""").lens))
        assertNull(CaseLens.row(CaseRead(options = listOf(CaseOptionWire("voc", "sg")))))
        assertNull(CaseLens.row(CaseRead(governs = CaseGoverns(0))))
    }

    @Test fun `the tapped word's place among the same words of its line`() {
        val line = "Hodi od vasi do vasi."
        val tokens = WordTokens.of(line)
        assertEquals(0, CaseLens.nth(line, tokens[2]))
        assertEquals(1, CaseLens.nth(line, tokens[4]))
        assertEquals(0, CaseLens.nth(line, tokens[0]))
        // the text shown with a prefix ("1. "): the same word nearest the tap
        assertEquals(1, CaseLens.nth(line, WordToken("vasi", 16 + 3, 20 + 3)))
        assertEquals(0, CaseLens.nth(line, WordToken("Vasi", 8 + 3, 12 + 3)))
    }

    @Test fun `GET lookup asks for the nth only with a line and past the first`() {
        val second = Words.url("http://n/", "vasi", "Hodi od vasi do vasi.", null, nth = 1)
        assertEquals("1", second.queryParameter("n"))
        assertNull(Words.url("http://n/", "vasi", "Hodi od vasi do vasi.", null).queryParameter("n"))
        assertNull(Words.url("http://n/", "vasi", null, null, nth = 2).queryParameter("n"))
    }

    @Test fun `the colours draw a decided case in its colour and the rest dotted`() {
        val answer = CaseLens.parseCases(
            """{"language":"sl","lines":[{"words":[{"i":0,"start":0,"end":6,"word":"Babica","sure":"decided","kind":"subject","rule":"imenovalnik","case":"nom","number":"sg"},{"i":3,"start":12,"end":19,"word":"kuhinji","sure":"decided","kind":"preposition","rule":"kje-mestnik-orodnik","case":"loc","number":"sg"},{"i":4,"start":20,"end":24,"word":"mizo","sure":"likely","case":"ins","number":"sg"}]}]}""",
        )
        val marks = CaseLens.marks(answer.lines.single().words)
        assertEquals(listOf(CaseMark(0, 6, Case.NOM), CaseMark(12, 19, Case.LOC), CaseMark(20, 24, null)), marks)
        // a text with the line after a prefix: shifted; past the text shown: left out
        assertEquals(CaseMark(3, 9, Case.NOM), CaseLens.marks(answer.lines.single().words, offset = 3).first())
        assertEquals(1, CaseLens.marks(answer.lines.single().words, length = 10).size)
        assertEquals("""{"lines":["Babica je v kuhinji.","Pod mizo!"]}""", CaseLens.casesBody(listOf("Babica je v kuhinji.", "Pod mizo!")).toString())
    }

    @Test fun `a case is named in both languages with its number, its questions as the school asks`() {
        val was = L10n.pair
        try {
            L10n.pair = LangPair(Lang.SL, Lang.EN)
            assertEquals("rodilnik, ed. · genitive, sg.", CaseLook.bi(Case.GEN, "sg"))
            assertEquals("mestnik, mn. · locative, pl.", CaseLook.bi(Case.LOC, "pl"))
            assertEquals("tožilnik · accusative", CaseLook.bi(Case.ACC, null))
            assertEquals("orodnik, ed., ž. · instrumental, sg., f.", CaseLook.bi(Case.INS, "sg", gender = "f"))
            assertEquals("orodnik, dvojina", CaseLook.name(Case.INS, "du", Lang.SL, short = false))
            assertEquals(listOf("kdo? kaj?", "koga? česa?", "komu? čemu?", "koga? kaj?", "pri kom? pri čem?", "s kom? s čim?"), Case.entries.map(CaseLook::question))
            assertFalse(CaseLook.colour(Case.NOM, dark = false) == CaseLook.colour(Case.NOM, dark = true))
        } finally {
            L10n.pair = was
        }
    }
}
