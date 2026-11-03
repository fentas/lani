package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang

/**
 * German B1's last six units (haette-waere to nicht-nur-sondern: docs/plans/05-built/course-de-b1-c.md) as the app grades
 * them: the mood's umlaut is grammar, never a slip (würde and wurde, käme and kame, wüsste and wusste); hätt, wär and würd are
 * one answer with hätte, wäre, würde, as hab is habe; the wo- and da- words are each a word of their own (darum, warum,
 * worum; davon, davor), and the r is what they get wrong (woauf, darmit); wieder and weder, doch and noch are no slips. What
 * people say is never a wrong option: drauf, dran and drüber are right, labelled; what a unit teaches writing without is almost,
 * with the standard form shown (nonstandard, verb_second: Auf was, auf ihn for a thing, täte, wer, was, nix; A2's and B1's weil
 * and obwohl with the verb second, weil for denn, trotzdem for obwohl, wie for als, wann for wenn).
 */
class GermanB1cContentTest {
    private fun de(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.DE)
    private fun assertGraded(verdict: Verdict, hint: Hint, r: Grading.Result) {
        assertEquals("verdict of «${r.expected}»", verdict, r.verdict)
        assertEquals("hint of «${r.expected}»", hint, r.hint)
    }

    /** A sheet's item with [accept] and [spoken]: what the sheet makes of [answer]. */
    private fun sheet(answer: String, accept: List<String>, spoken: List<String> = emptyList()) =
        Sheets.check(SheetItem(accept, null, null, null, "", spoken = spoken), answer, Lang.DE)

    private val ids = listOf("haette-waere", "wenn-ich-haette", "passiv", "woran-daran", "jemand-irgendwer", "nicht-nur-sondern")
    private val course = checkNotNull(Courses.bundled("de"))

    @Test fun `würde, wurde and werde are three words, either way`() {
        for ((typed, right) in listOf("wurde" to "würde", "würde" to "wurde", "werde" to "wurde", "wurde" to "werde", "werde" to "würde", "würde" to "werde", "wurden" to "würden", "würden" to "wurden", "werden" to "wurden", "wurden" to "werden", "wurdest" to "würdest", "würdet" to "wurdet")) {
            assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        }
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("wuerde", "würde"))
    }

    @Test fun `the short forms of speech are one answer with the whole word, and only those`() {
        assertEquals(Verdict.CORRECT, de("ich hätt gern einen Kaffee", "ich hätte gern einen Kaffee").verdict)
        assertEquals(Verdict.CORRECT, de("das wär schön", "das wäre schön").verdict)
        assertEquals(Verdict.CORRECT, de("ich würd gern tanzen", "ich würde gern tanzen").verdict)
        assertEquals(Verdict.CORRECT, de("hätt", "hätte").verdict)
        // war and hatte stay the past
        assertEquals(Verdict.WRONG, de("das war schön", "das wäre schön").verdict)
        assertEquals(Verdict.WRONG, de("hatte", "hätte").verdict)
        assertEquals(Verdict.WRONG, de("hatt", "hätte").verdict)
    }

    @Test fun `the mood's umlaut is grammar, on the strong verbs and the modals`() {
        for ((typed, right) in listOf("kame" to "käme", "kamen" to "kämen", "kämen" to "kamen", "gabe" to "gäbe", "wusste" to "wüsste", "wüsste" to "wusste", "wussten" to "wüssten", "brauchte" to "bräuchte", "bräuchte" to "brauchte", "söllte" to "sollte", "wöllte" to "wollte", "solltet" to "solltest", "könnte" to "konnte", "konnte" to "könnte", "müsste" to "musste")) {
            assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        }
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("kaeme", "käme"))
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("koennte", "könnte"))
        // the rule is for these words: a slip in another word's stem stays one
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("Kafee", "Kaffee"))
    }

    @Test fun `a participle without its ge and gewaschen for gewachsen are mistakes, a slip elsewhere stays one`() {
        assertEquals(Verdict.WRONG, de("Die Äpfel werden waschen.", "Die Äpfel werden gewaschen.").verdict)
        assertEquals(Verdict.WRONG, de("Die Äpfel wurden sammelt.", "Die Äpfel wurden gesammelt.").verdict)
        assertEquals(Verdict.WRONG, de("gewachsen", "gewaschen").verdict)
        assertEquals(Verdict.WRONG, de("gewaschen", "gewachsen").verdict)
        assertEquals(Verdict.WRONG, de("backen", "gebacken").verdict)
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("gepflükt", "gepflückt"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("reperiert", "repariert"))
    }

    @Test fun `the wo- and da- words are words of their own, and the r is no slip`() {
        for ((typed, right) in listOf(
            "woauf" to "worauf", "daauf" to "darauf", "woan" to "woran", "daan" to "daran", "woüber" to "worüber",
            "darmit" to "damit", "wormit" to "womit", "darfür" to "dafür", "worfür" to "wofür", "darvon" to "davon",
            "darum" to "warum", "warum" to "darum", "worum" to "warum", "warum" to "worum", "worum" to "darum",
            "davon" to "davor", "davor" to "davon", "wovon" to "wovor", "wovor" to "wovon",
            "daraus" to "darauf", "darauf" to "daraus", "woraus" to "worauf", "darin" to "daran", "daran" to "darin", "worin" to "woran",
            "darauf" to "worauf", "worauf" to "darauf", "damit" to "womit", "dafür" to "wofür",
        )) assertEquals("$typed for $right", Verdict.WRONG, de(typed, right).verdict)
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("wofuer", "wofür"))
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("darueber", "darüber"))
    }

    @Test fun `weder and wieder, noch and doch are words of their own, a slip in sowohl and entweder stays one`() {
        assertEquals(Verdict.WRONG, de("wieder", "weder").verdict)
        assertEquals(Verdict.WRONG, de("jeder", "weder").verdict)
        assertEquals(Verdict.WRONG, de("weder", "wieder").verdict)
        assertEquals(Verdict.WRONG, de("doch", "noch").verdict)
        assertEquals(Verdict.WRONG, de("noch", "doch").verdict)
        assertEquals(Verdict.WRONG, de("nach", "noch").verdict)
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("swohl", "sowohl"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("entwder", "entweder"))
    }

    @Test fun `an indefinite pronoun's ending is the ending, and jemand with none is right where the sheet lists it`() {
        assertEquals(Verdict.WRONG, de("niemandem", "niemanden").verdict)
        assertEquals(Verdict.WRONG, de("jemanden", "jemandem").verdict)
        assertEquals(Verdict.WRONG, de("irgendwem", "irgendwen").verdict)
        assertEquals(Verdict.WRONG, de("keinen", "keinem").verdict)
        assertEquals(Verdict.WRONG, de("demselben", "denselben").verdict)
        assertEquals(Verdict.WRONG, de("dasselbe", "dieselbe").verdict)
        assertEquals(Verdict.WRONG, de("nicht", "nichts").verdict)
        // Duden: Akkusativ jemanden, auch jemand; niemanden, niemand
        assertEquals(Verdict.CORRECT, sheet("niemand", listOf("niemanden", "niemand")).verdict)
    }

    @Test fun `a spoken form these units keep is right, labelled with the standard one`() {
        for ((said, standard) in listOf("drauf" to "darauf", "dran" to "daran", "drüber" to "darüber")) {
            val m = sheet(said, listOf(standard), listOf(said))
            assertEquals("$said for $standard", Verdict.CORRECT to standard, m.verdict to m.standard)
        }
        val listed = course.units.getValue("woran-daran").worksheets.flatMap { it.exercises }.filter { it !is Exercise.Sort }.flatMap { Sheets.items(it, Lang.DE) }.flatMap { it.spoken }
        assertTrue("listed on a sheet: drauf", "drauf" in listed)
    }

    /** Every form that was a sheet's `spoken` until it moved to the table, with what its item accepts. */
    private val moved = listOf(
        "weil er hat keine Zeit" to listOf("weil er keine Zeit hat"),
        "weil Toni arbeitet viel" to listOf("weil Toni viel arbeitet"),
        "weil" to listOf("denn"),
        "Kathi ist müde, weil sie arbeitet viel." to listOf("Kathi ist müde, weil sie viel arbeitet."),
        "Toni kommt nicht, weil er muss arbeiten." to listOf("Toni kommt nicht, weil er arbeiten muss."),
        "Florian ist froh, weil er hat sein Schaf gefunden." to listOf("Florian ist froh, weil er sein Schaf gefunden hat."),
        "Kathi ist müde, weil sie steht um fünf auf." to listOf("Kathi ist müde, weil sie um fünf aufsteht."),
        "Maxi ist froh, weil er darf mitkommen." to listOf("Maxi ist froh, weil er mitkommen darf."),
        "obwohl er ist krank" to listOf("obwohl er krank ist", "obwohl Stefan krank ist"),
        "obwohl Stefan ist krank" to listOf("obwohl er krank ist", "obwohl Stefan krank ist"),
        "obwohl sie muss früh aufstehen" to listOf("obwohl sie früh aufstehen muss", "obwohl Kathi früh aufstehen muss"),
        "obwohl Kathi muss früh aufstehen" to listOf("obwohl sie früh aufstehen muss", "obwohl Kathi früh aufstehen muss"),
        "obwohl er hat keine Zeit" to listOf("obwohl er keine Zeit hat", "obwohl Toni keine Zeit hat"),
        "obwohl Toni hat keine Zeit" to listOf("obwohl er keine Zeit hat", "obwohl Toni keine Zeit hat"),
        "obwohl sie hört schlecht" to listOf("obwohl sie schlecht hört", "obwohl Mitzi schlecht hört"),
        "obwohl Mitzi hört schlecht" to listOf("obwohl sie schlecht hört", "obwohl Mitzi schlecht hört"),
        "trotzdem" to listOf("obwohl", "auch wenn"),
        "Trotzdem" to listOf("Obwohl", "Auch wenn"),
        "Stefan arbeitet, obwohl er ist krank." to listOf("Stefan arbeitet, obwohl er krank ist.", "Obwohl er krank ist, arbeitet Stefan.", "Obwohl Stefan krank ist, arbeitet er."),
        "Kathi kommt, obwohl sie muss früh aufstehen." to listOf("Kathi kommt, obwohl sie früh aufstehen muss.", "Obwohl sie früh aufstehen muss, kommt Kathi.", "Obwohl Kathi früh aufstehen muss, kommt sie."),
        "Franz arbeitet, obwohl er ist schon alt." to listOf("Franz arbeitet, obwohl er schon alt ist.", "Obwohl er schon alt ist, arbeitet Franz.", "Obwohl Franz schon alt ist, arbeitet er."),
        "wie" to listOf("als"),
        "Stefan ist jünger wie Franz." to listOf("Stefan ist jünger als Franz."),
        "Der Dobratsch ist höher wie der Kirchturm." to listOf("Der Dobratsch ist höher als der Kirchturm."),
        "Der Traktor ist schneller wie das Pferd." to listOf("Der Traktor ist schneller als das Pferd."),
        "Der Most ist besser wie das Wasser." to listOf("Der Most ist besser als das Wasser."),
        "Oma Resi ist älter wie Tante Mitzi." to listOf("Oma Resi ist älter als Tante Mitzi."),
        "Der Jänner ist kälter wie der März." to listOf("Der Jänner ist kälter als der März.", "Der Januar ist kälter als der März."),
        "Der Januar ist kälter wie der März." to listOf("Der Jänner ist kälter als der März.", "Der Januar ist kälter als der März."),
        "Klagenfurt ist größer wie Villach." to listOf("Klagenfurt ist größer als Villach."),
        "Wann" to listOf("Wenn"),
        "Wann es regnet, bleiben wir zu Hause." to listOf("Wenn es regnet, bleiben wir zu Hause."),
        "Wann es schneit, fährt Stefan mit dem Auto." to listOf("Wenn es schneit, fährt Stefan mit dem Auto."),
        "täte" to listOf("würde"),
        "tät" to listOf("würde"),
        "Ich täte gern tanzen." to listOf("Ich würde gern tanzen."),
        "Stefan täte gern nach Villach fahren." to listOf("Stefan würde gern nach Villach fahren."),
        "täte" to listOf("würde", "könnte"),
        "tät" to listOf("würde", "könnte"),
        "täte" to listOf("würde", "möchte"),
        "tät" to listOf("würde", "möchte"),
        "an was?" to listOf("woran?"),
        "über was?" to listOf("worüber?"),
        "mit was?" to listOf("womit?"),
        "Auf was" to listOf("Worauf"),
        "auf ihn" to listOf("darauf"),
        "auf den" to listOf("darauf"),
        "an was" to listOf("woran"),
        "Über was" to listOf("Worüber"),
        "Für das" to listOf("Dafür"),
        "Auf was wartest du?" to listOf("Worauf wartest du?"),
        "An was denkst du?" to listOf("Woran denkst du?"),
        "Ja, ich warte auf ihn." to listOf("Ja, ich warte darauf.", "Ich warte darauf.", "Ja, darauf.", "Ja, darauf warte ich."),
        "Ja, ich warte auf den." to listOf("Ja, ich warte darauf.", "Ich warte darauf.", "Ja, darauf.", "Ja, darauf warte ich."),
        "Vor was" to listOf("Wovor"),
        "vor ihm" to listOf("davor"),
        "vor dem" to listOf("davor"),
        "Für was interessierst du dich?" to listOf("Wofür interessierst du dich?"),
        "Ja, ich denke an ihn." to listOf("Ja, ich denke daran.", "Ich denke daran.", "Ja, daran.", "Ja, daran denke ich."),
        "Ja, ich denke an den." to listOf("Ja, ich denke daran.", "Ich denke daran.", "Ja, daran.", "Ja, daran denke ich."),
        "wer" to listOf("jemand", "irgendwer", "irgendjemand", "einer"),
        "nix" to listOf("niemanden", "niemand", "keinen", "nichts"),
        "nix" to listOf("nichts"),
        "wer" to listOf("irgendwer", "jemand", "irgendjemand", "einer"),
        "Ich habe nix gekauft." to listOf("Ich habe nichts gekauft."),
        "wer" to listOf("jemand", "einer", "irgendwer", "irgendjemand"),
        "was" to listOf("etwas", "irgendwas"),
        "was" to listOf("welche", "etwas"),
    )

    @Test fun `every form moved off the sheets is almost now, with a standard form shown, no mistake and no slip`() {
        for ((said, accept) in moved) {
            val r = Sheets.check(SheetItem(accept, null, null, null, ""), said, Lang.DE)
            assertEquals("«$said» for ${accept.first()}", Verdict.ALMOST to Hint.NONSTANDARD, r.verdict to r.hint)
            assertTrue("«$said» shows an accepted form", r.expected in accept)
        }
    }

    @Test fun `the table's forms go one way, only for their own word, and the verb only up behind the subject`() {
        assertEquals(Verdict.WRONG, de("worauf", "auf was").verdict)
        assertEquals(Verdict.WRONG, de("auf was", "woran").verdict)
        assertEquals(Verdict.WRONG, de("auf ihn", "daran").verdict)
        // for a person the pronoun is the answer: a da- word stays wrong
        assertEquals(Verdict.WRONG, de("Ja, ich warte darauf.", "Ja, ich warte auf ihn.").verdict)
        assertEquals(Verdict.WRONG, de("würde", "täte").verdict)
        assertEquals(Verdict.WRONG, de("jemand", "wer").verdict)
        assertEquals(Verdict.WRONG, de("nichts", "nix").verdict)
        assertEquals(Verdict.WRONG, de("wer", "niemand").verdict)
        assertEquals(Verdict.WRONG, de("was", "nichts").verdict)
        // after dass the verb has no such standing, nor a scrambled clause after weil
        assertEquals(Verdict.WRONG, de("Ich glaube, dass er ist krank.", "Ich glaube, dass er krank ist.").verdict)
        assertEquals(Verdict.WRONG, de("Er bleibt, weil krank er ist.", "Er bleibt, weil er krank ist.").verdict)
        assertEquals(Verdict.WRONG, de("Er bleibt, weil er hat Zeit keine.", "Er bleibt, weil er keine Zeit hat.").verdict)
        assertEquals(Verdict.WRONG, de("Er bleibt, weil er krank war.", "Er bleibt, weil er krank ist.").verdict)
        // a fronted clause: its own verb may move, the main clause's stays
        assertGraded(Verdict.ALMOST, Hint.NONSTANDARD, de("Obwohl er ist krank, arbeitet er.", "Obwohl er krank ist, arbeitet er."))
        assertEquals(Verdict.WRONG, de("Obwohl er krank ist, er arbeitet.", "Obwohl er krank ist, arbeitet er.").verdict)
    }

    @Test fun `no choice and no bank of the whole course offers what the table says people say`() {
        val problems = mutableListOf<String>()
        for (u in course.units.values) {
            val all = u.worksheets.flatMap { it.exercises } + u.test.exercises + u.intro!!.steps.mapNotNull { it.ask }
            for (e in all) when (e) {
                is Exercise.Choice -> {
                    val right = e.options[e.answer]
                    for ((i, o) in e.options.withIndex()) {
                        if (i == e.answer) continue
                        val alone = Grading.check(o, listOf(right), Lang.DE)
                        val inIt = Grading.check(e.prompt.replace(Regex("_{3,}"), o), listOf(e.prompt.replace(Regex("_{3,}"), right)), Lang.DE)
                        if (alone.hint == Hint.NONSTANDARD || inIt.hint == Hint.NONSTANDARD) problems += "${u.id}: «$o» in «${e.prompt}»"
                    }
                }
                is Exercise.GapText -> for (d in e.distractors) for (g in e.gaps) {
                    if (Grading.check(d, g.accept, Lang.DE).hint == Hint.NONSTANDARD) problems += "${u.id}: the distractor «$d» for ${g.accept.first()}"
                }
                else -> {}
            }
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test fun `the units are at the level's size, and the five new pages are in the book with their own cards`() {
        for (id in ids) {
            val u = course.units.getValue(id)
            assertEquals("$id: its level", "B1", u.level)
            assertEquals("$id: three sheets", 3, u.worksheets.size)
            assertTrue("$id: five steps or more", u.intro!!.steps.size >= 5)
            assertTrue("$id: a quick question a step", u.intro!!.steps.all { it.ask != null })
        }
        val book = Grammar.bundled("de").associateBy { it.id }
        for (p in listOf("konjunktiv-zwei", "passiv", "wo-da-woerter", "indefinitpronomen", "doppelkonnektoren")) {
            assertTrue("$p: its page", book.containsKey(p))
            assertEquals("$p: at B1", "B1", book.getValue(p).level)
        }
    }
}
