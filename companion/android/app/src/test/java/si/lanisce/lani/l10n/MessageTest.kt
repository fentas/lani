package si.lanisce.lani.l10n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MessageTest {
    private fun fmt(lang: Lang, pattern: String, vararg args: Pair<String, Any?>) = Message.parse(pattern).format(lang, args.toMap())

    /** The hand-written helper the plural messages replace (ui/game/VillageLogic.kt), kept here as the reference. */
    private fun slCount(n: Int, one: String, two: String, few: String, many: String) = when (n % 100) {
        1 -> one
        2 -> two
        3, 4 -> few
        else -> many
    }

    @Test
    fun `plain text and arguments as a string template writes them`() {
        assertEquals("Danes", fmt(Lang.SL, "Danes"))
        assertEquals("Niz: 1000", fmt(Lang.SL, "Niz: {n}", "n" to 1000)) // no grouping: the literals had "$n"
        assertEquals("Pot do B2 · x", fmt(Lang.SL, "Pot do {level} · x", "level" to "B2"))
        assertEquals("l'orto di Luka", fmt(Lang.IT, "l'orto di {who}", "who" to "Luka")) // apostrophes are text
        assertEquals("a {missing} b", fmt(Lang.EN, "a {missing} b"))
        assertEquals("null", fmt(Lang.EN, "{x}", "x" to null))
    }

    @Test
    fun `the Slovene plural knows the dual, by the last two digits, like slCount did`() {
        val p = "{n, plural, one {korak} two {koraka} few {koraki} other {korakov}}"
        for (n in 0..1000) assertEquals("n=$n", slCount(n, "korak", "koraka", "koraki", "korakov"), fmt(Lang.SL, p, "n" to n))
    }

    @Test
    fun `English, Italian and German plurals`() {
        val en = "{n} {n, plural, one {step} other {steps}}"
        assertEquals("1 step", fmt(Lang.EN, en, "n" to 1))
        assertEquals("0 steps", fmt(Lang.EN, en, "n" to 0))
        assertEquals("21 steps", fmt(Lang.EN, en, "n" to 21))
        val it = "{n, plural, one {# passo} other {# passi}}"
        assertEquals("1 passo", fmt(Lang.IT, it, "n" to 1))
        assertEquals("2 passi", fmt(Lang.IT, it, "n" to 2))
        assertEquals("0 passi", fmt(Lang.IT, it, "n" to 0))
        // CLDR gives Italian a "many" for millions; a message without that branch falls back to other.
        assertEquals("1000000 passi", fmt(Lang.IT, it, "n" to 1_000_000))
        assertEquals("1 Schritt", fmt(Lang.DE, "{n, plural, one {# Schritt} other {# Schritte}}", "n" to 1))
    }

    @Test
    fun `exact matches go first, and # is the number`() {
        val p = "{n, plural, =0 {nič} one {# dan} two {# dneva} few {# dnevi} other {# dni}}"
        assertEquals("nič", fmt(Lang.SL, p, "n" to 0))
        assertEquals("101 dan", fmt(Lang.SL, p, "n" to 101))
        assertEquals("3 dnevi", fmt(Lang.SL, p, "n" to 3))
        assertEquals("# 5", fmt(Lang.SL, "# {n}", "n" to 5)) // # outside a plural is text
        assertEquals("5 dni", fmt(Lang.SL, p, "n" to 5L))
    }

    @Test
    fun `select picks by text, and nests`() {
        val p = "{g, select, f {pomagala} other {pomagal}} si {n, plural, one {# krat} other {#-krat}}"
        assertEquals("pomagala si 1 krat", fmt(Lang.SL, p, "g" to "f", "n" to 1))
        assertEquals("pomagal si 5-krat", fmt(Lang.SL, p, "g" to "m", "n" to 5))
        assertEquals("pomagal si 5-krat", fmt(Lang.SL, p, "n" to 5))
    }

    @Test
    fun `the argument names`() {
        assertEquals(setOf("g", "n", "who"), Message.parse("{g, select, f {{who}} other {#}} {n, plural, other {x}}").args)
    }

    @Test
    fun `malformed messages are refused`() {
        for (bad in listOf("{", "a }", "{n, plural, one {x}}", "{n, number}", "{n, plural, one {x} other {y}", "{}")) {
            assertThrows(bad, IllegalArgumentException::class.java) { Message.parse(bad) }
        }
    }
}

class L10nTest {
    @After
    fun reset() {
        Lang.entries.forEach { L10n.useTable(it, null) }
        L10n.onMissing = null
        L10n.pair = LangPair.DEFAULT
    }

    @Test
    fun `a label is target · base`() {
        L10n.useTable(Lang.SL, mapOf("k" to "Danes", "n" to "{n} {n, plural, one {dan} two {dneva} few {dnevi} other {dni}}"))
        L10n.useTable(Lang.EN, mapOf("k" to "Today", "n" to "{n} {n, plural, one {day} other {days}}"))
        L10n.useTable(Lang.IT, mapOf("k" to "Oggi"))
        assertEquals("Danes · Today", bi("k"))
        assertEquals("Danes", inTarget("k"))
        assertEquals("Today", inBase("k"))
        assertEquals("2 dneva · 2 days", bi("n", "n" to 2))
        L10n.pair = LangPair(Lang.IT, Lang.SL)
        assertEquals("Oggi · Danes", bi("k"))
    }

    @Test
    fun `a missing key falls back to English, then Slovene, and is reported once`() {
        val seen = mutableListOf<String>()
        L10n.onMissing = { l, k -> seen += "${l.code}:$k" }
        L10n.useTable(Lang.SL, mapOf("a" to "A-sl", "b" to "B-sl", "p" to "{n, plural, one {ena} two {dve} few {tri} other {pet}}"))
        L10n.useTable(Lang.EN, mapOf("a" to "A-en", "p" to "{n, plural, one {one} other {many}}"))
        L10n.useTable(Lang.IT, emptyMap())
        L10n.pair = LangPair(Lang.IT, Lang.SL)
        assertEquals("A-en · A-sl", bi("a"))
        assertEquals("B-sl · B-sl", bi("b")) // not in English either
        assertEquals("zzz · zzz", bi("zzz")) // nowhere: the key shows
        bi("a")
        assertEquals(listOf("it:a", "it:b", "it:zzz", "sl:zzz"), seen)
        // A fallback is formatted by its own language's rules: English "many" for 2, not a Slovene dual.
        assertEquals("many · dve", bi("p", "n" to 2))
    }

    @Test
    fun `the tables parse, notes left out`() {
        val t = L10n.parseTable("""{"_note": "machine-translated", "a.b": "Danes", "c": "{n} x"}""")
        assertEquals(mapOf("a.b" to "Danes", "c" to "{n} x"), t)
    }
}

class LangTest {
    @Test
    fun `languages by code or by name`() {
        assertEquals(Lang.SL, Lang.of("sl"))
        assertEquals(Lang.SL, Lang.of("Slovene"))
        assertEquals(Lang.SL, Lang.of(" slovenian "))
        assertEquals(Lang.IT, Lang.of("italiano"))
        assertEquals(Lang.IT, Lang.of("Italijanščina"))
        assertEquals(Lang.DE, Lang.of("German"))
        assertNull(Lang.of("fr"))
        assertNull(Lang.of(""))
        assertNull(Lang.of(null))
    }

    @Test
    fun `pairs parse from the setting`() {
        assertEquals(LangPair(Lang.IT, Lang.SL), LangPair.parse("it-sl"))
        assertEquals("it-sl", LangPair(Lang.IT, Lang.SL).code)
        assertNull(LangPair.parse("sl-sl"))
        assertNull(LangPair.parse("xx-en"))
        assertNull(LangPair.parse(""))
        assertNull(LangPair.parse(null))
    }

    @Test
    fun `choosing one side swaps when it's the other`() {
        val jan = LangPair.DEFAULT
        assertEquals(LangPair(Lang.IT, Lang.EN), jan.withTarget(Lang.IT))
        assertEquals(LangPair(Lang.EN, Lang.SL), jan.withTarget(Lang.EN))
        assertEquals(LangPair(Lang.SL, Lang.IT), jan.withBase(Lang.IT))
        assertEquals(LangPair(Lang.EN, Lang.SL), jan.withBase(Lang.SL))
        assertEquals(Lang.IT, Lang.of("italiano"))
        assertEquals("slovenščina", Lang.SL.ownName)
    }

    @Test
    fun `the pair from the learner profile, sl · en by default`() {
        assertEquals(LangPair.DEFAULT, LangPair.fromProfile(null, null, null, null))
        // Jan's profile names Slovene and no base: English, whatever else it says.
        assertEquals(LangPair.DEFAULT, LangPair.fromProfile(null, "Slovene", null, null))
        // The second learner's, as lani-profile writes it.
        assertEquals(LangPair(Lang.IT, Lang.SL), LangPair.fromProfile("it", "Italian", "sl", "Slovene"))
        assertEquals(LangPair(Lang.IT, Lang.EN), LangPair.fromProfile(null, "Italian", null, null))
        assertEquals(LangPair(Lang.DE, Lang.IT), LangPair.fromProfile(null, "German", null, "Italian"))
        // Unknown languages keep the default's side; one language twice keeps the target.
        assertEquals(LangPair(Lang.SL, Lang.EN), LangPair.fromProfile("fr", null, "fr", null))
        assertEquals(LangPair(Lang.EN, Lang.SL), LangPair.fromProfile("en", null, "en", null))
        assertEquals(LangPair(Lang.IT, Lang.EN), LangPair.fromProfile("it", null, "it", null))
    }
}
