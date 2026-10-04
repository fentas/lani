package si.lanisce.lani.l10n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Speaker
import si.lanisce.lani.data.Spoken
import si.lanisce.lani.game.Res
import si.lanisce.lani.ui.game.skillLabel
import si.lanisce.lani.ui.keyboardLetters
import si.lanisce.lani.ui.stage.Lines

/**
 * Every pair among Slovene, English, Italian and German (12: target · base) holds together (plan 2, §1): its labels
 * read "target · base" with nothing left unfilled, the language learned is named in both halves, the phone's locales,
 * the plural rules, the grading of an accented answer, the keyboard's letters, the villagers' built-in lines and the
 * skills' names follow the pair.
 */
class LanguagePairsTest {
    private val before = L10n.pair

    @After fun restore() {
        L10n.pair = before
    }

    private val pairs: List<LangPair> = Lang.entries.flatMap { t -> Lang.entries.filter { it != t }.map { b -> LangPair(t, b) } }

    @Test fun `there are 12 pairs, and each parses back from its code`() {
        assertEquals(12, pairs.size)
        for (p in pairs) assertEquals(p, LangPair.parse(p.code))
    }

    @Test fun `a profile may name its languages in any of the four`() {
        assertEquals(LangPair(Lang.SL, Lang.DE), LangPair.fromProfile(null, "Slowenisch", null, "Deutsch"))
        assertEquals(LangPair(Lang.DE, Lang.IT), LangPair.fromProfile(null, "tedesco", null, "italiano"))
        assertEquals(LangPair(Lang.EN, Lang.SL), LangPair.fromProfile(null, "Englisch", null, "slovenščina"))
        assertEquals(LangPair(Lang.IT, Lang.EN), LangPair.fromProfile("it", "Italienisch", "en", null))
    }

    @Test fun `every table has every key`() {
        val keys = L10n.table(Lang.SL).keys
        for (l in Lang.entries) assertEquals("${l.code}.json misses ${(keys - L10n.table(l).keys).sorted().take(5)}", keys, L10n.table(l).keys)
    }

    @Test fun `a label is the target's text, then the base's`() {
        for (p in pairs) for (key in listOf("common.today", "homeScreen.challenges", "villageSheets.newAge")) {
            val label = L10n.label(p, key)
            assertEquals("$p $key", "${L10n.table(p.target).getValue(key)} · ${L10n.table(p.base).getValue(key)}", label)
            assertFalse("$p $key: $label", label.contains('{'))
        }
    }

    @Test fun `the language learned is named in both halves of the label`() {
        // "Napiši po nemško · Write it in German": for one base, each target reads differently
        for (key in listOf("common.writeSlovene", "common.saySlovene", "playerScreen.writeSlovene", "talkInput.writeSlovene", "homeScreen.installSloveneVoice")) {
            for (b in Lang.entries) {
                val halves = Lang.entries.filter { it != b }.map { t -> L10n.text(b, key, target = t) }
                assertEquals("$key in ${b.code}: $halves", halves.size, halves.toSet().size)
            }
            for (t in Lang.entries) {
                val halves = Lang.entries.filter { it != t }.map { b -> L10n.label(LangPair(t, b), key).substringBefore(" · ") }
                assertEquals("$key, target ${t.code}: the target half is the same whatever the base", 1, halves.toSet().size)
            }
        }
        assertEquals("Schreib es auf Deutsch · Scrivilo in tedesco", L10n.label(LangPair(Lang.DE, Lang.IT), "common.writeSlovene"))
        assertEquals("Napiši po slovensko · Schreib es auf Slowenisch", L10n.label(LangPair(Lang.SL, Lang.DE), "common.writeSlovene"))
        assertEquals("Write it in English · Napiši po angleško", L10n.label(LangPair(Lang.EN, Lang.SL), "common.writeSlovene"))
    }

    @Test fun `the phone's recognizer and text-to-speech take the target's locale`() {
        val want = mapOf(Lang.SL to "sl-SI", Lang.IT to "it-IT", Lang.DE to "de-DE", Lang.EN to "en-GB")
        for (p in pairs) {
            assertEquals(want.getValue(p.target), p.target.locale)
            assertEquals(p.target.code, Speaker.locale(p.target).language)
        }
    }

    @Test fun `plurals follow each language's rules, the Slovene dual too`() {
        assertEquals("two", PluralCategories.select(Lang.SL, 2.0))
        assertEquals("few", PluralCategories.select(Lang.SL, 3.0))
        for (l in listOf(Lang.EN, Lang.IT, Lang.DE)) {
            assertEquals("one", PluralCategories.select(l, 1.0))
            assertEquals("other", PluralCategories.select(l, 2.0))
        }
        val n = { l: Lang, k: Int -> L10n.text(l, "packsScreen.youKnowWords", mapOf("n" to k)) }
        assertEquals(listOf("Znaš 1 besedo", "Znaš 2 besedi", "Znaš 3 besede", "Znaš 5 besed"), listOf(1, 2, 3, 5).map { n(Lang.SL, it) })
        assertEquals(listOf("Du kennst 1 Wort", "Du kennst 2 Wörter"), listOf(1, 2).map { n(Lang.DE, it) })
        assertEquals(listOf("You know 1 word", "You know 2 words"), listOf(1, 2).map { n(Lang.EN, it) })
        assertEquals(listOf("Sai 1 parola", "Sai 2 parole"), listOf(1, 2).map { n(Lang.IT, it) })
    }

    @Test fun `an answer without the target's accents is almost right, with the target's hint`() {
        val cases = mapOf(
            Lang.SL to Triple("dober vecer", "Dober večer.", Hint.DIACRITICS),
            Lang.IT to Triple("perche no", "Perché no?", Hint.ACCENT),
            Lang.DE to Triple("schoen", "schön", Hint.UMLAUT),
        )
        for (p in pairs) {
            L10n.pair = p
            val c = cases[p.target]
            if (c == null) {
                // English has no letters to fold: its contractions are the same answer
                assertEquals(Verdict.CORRECT, Grading.check("I don't know", listOf("I do not know.")).verdict)
                continue
            }
            val r = Grading.check(c.first, listOf(c.second))
            assertEquals("$p", Verdict.ALMOST, r.verdict)
            assertEquals("$p", c.third, r.hint)
        }
    }

    @Test fun `the keyboard's letters are the target's`() {
        assertEquals(listOf("č", "š", "ž"), keyboardLetters(Lang.SL).take(3))
        assertTrue("à" in keyboardLetters(Lang.IT))
        assertTrue("ß" in keyboardLetters(Lang.DE))
        assertTrue(keyboardLetters(Lang.EN).isEmpty())
    }

    @Test fun `villagers greet in the target, and the base reads the translation`() {
        val hello = mapOf(Lang.SL to "Pozdravljen, Jan!", Lang.IT to "Ciao! Eccoti qua!", Lang.DE to "Servus! Da bist du ja!", Lang.EN to "Hello! There you are!")
        for (p in pairs) {
            L10n.pair = p
            val line = Lines.greet("ti").first()
            assertEquals("$p", hello.getValue(p.target), line.target(p))
            assertTrue("$p: a translation", line.base(p).isNotBlank())
        }
        L10n.pair = LangPair(Lang.SL, Lang.DE)
        assertEquals("Servus! Da bist du ja!", Lines.greet("ti").first().base(L10n.pair))
        L10n.pair = LangPair(Lang.DE, Lang.EN)
        assertEquals("Hello! There you are!", Lines.greet("ti").first().base(L10n.pair))
        val translate = Lines.leadIns(Exercise.Translate("x", listOf("y")), "ti").first { it.target(L10n.pair).startsWith("Wie sagt man") }
        assertEquals("Wie sagt man das auf Deutsch?", translate.target(L10n.pair))
        assertEquals("How do you say this in German?", translate.base(L10n.pair))
    }

    @Test fun `a sentence in the target reads as the target, one in the base doesn't`() {
        assertTrue(Spoken.looks("Wie geht es dir heute?", Lang.DE, Lang.EN))
        assertFalse(Spoken.looks("How are you today?", Lang.DE, Lang.EN))
        assertTrue(Spoken.looks("Come stai oggi?", Lang.IT, Lang.SL))
        assertTrue(Spoken.looks("Where are you from?", Lang.EN, Lang.DE))
        assertFalse(Spoken.looks("Woher kommst du? Ich bin aus Kärnten.", Lang.EN, Lang.DE))
        assertTrue(Spoken.looks("Kako si?", Lang.SL, Lang.IT))
    }

    @Test fun `skills are named in the pair`() {
        L10n.pair = LangPair(Lang.DE, Lang.IT)
        assertEquals("Grammatik · grammatica", Res.STONE.skillLabel())
        L10n.pair = LangPair.DEFAULT
        assertEquals("slovnica · grammar", Res.STONE.skillLabel())
    }
}
