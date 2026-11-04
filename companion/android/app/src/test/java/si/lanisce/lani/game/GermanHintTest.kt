package si.lanisce.lani.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.game.scene.Counts
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.io.File

/**
 * "📖 Namig · Hint" at a German turn (companion/SCENES.md, "The hint"; game/GermanHints.kt): every page of the German book
 * has its rule in words, its spoken sentence and models; what decides a word's case comes from German's trigger table; what
 * in the sentence decides the form, on the curated turns; what people say in Kärnten isn't taught against; and, over every
 * curated German turn (Kärnten's scenes, steps, stories, parties), that nothing the card or the 🔊 shows gives the answer
 * away.
 */
class GermanHintTest {
    private val pages = Grammar.bundled("de")
    private val page = { id: String -> pages.firstOrNull { it.id == id } }

    @After fun jansPair() {
        L10n.pair = LangPair.DEFAULT
    }

    private fun wrong(de: String, grammar: String) = DialogChoice(de, why = "…", grammar = grammar)

    private fun hint(right: String, page: String, vararg bad: String, cast: List<String> = emptyList()): TurnHint =
        TurnHints.of(DialogLine(choices = listOf(DialogChoice(right, ok = true)) + bad.map { wrong(it, page) }), "de", cast, this.page)!!

    private fun word(right: String, page: String, vararg bad: String, cast: List<String> = emptyList()) = hint(right, page, *bad, cast = cast).trigger?.word

    /** The string table's key of the line at this turn, without "hint.de.". */
    private fun key(right: String, page: String, vararg bad: String): String? = (hint(right, page, *bad).trigger as? Trigger.Asks)?.line?.removePrefix("hint.de.")

    private fun line(h: TurnHint) = TurnHints.text(h, page).lines

    @Before fun learnsGerman() {
        // a German learner from Slovene: the card in Slovene, the German as it is
        L10n.pair = LangPair(Lang.DE, Lang.SL)
    }

    @Test fun `every page of the German book has its rule, its spoken sentence and models, in every table`() {
        assertEquals(68, pages.size)
        assertEquals(pages.map { it.id }.toSet(), GermanHints.RULES.keys)
        for ((id, r) in GermanHints.RULES) {
            assertTrue("$id: two models at least", r.models.size >= 2)
            assertEquals(id, r.said, HintSpeech.spoken(r.text))
            for (lang in Lang.entries) {
                assertTrue("${lang.code}.json lacks ${r.text}", r.text in L10n.table(lang))
                assertTrue("${lang.code}.json lacks ${r.said}", r.said in L10n.table(lang))
            }
        }
        assertEquals(68, GermanHints.RULES.values.map { it.text }.toSet().size)
    }

    @Test fun `what decides a case is German's trigger table, read from its file`() {
        val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "grammar/de/data/case-triggers.json").isFile }
        val o = Json.parseToJsonElement(File(root, "grammar/de/data/case-triggers.json").readText()).jsonObject
        val t = GermanHints.table
        val words = o.getValue("prepositions").jsonArray.flatMap { p -> p.jsonObject.getValue("words").jsonArray.map { it.jsonPrimitive.content } }
        assertEquals(words.toSet(), t.prepositions.keys)
        assertEquals(o.getValue("contractions").jsonArray.map { it.jsonObject.getValue("form").jsonPrimitive.content }.toSet(), t.fused.keys)
        // every preposition's line says the table's case
        for (p in o.getValue("prepositions").jsonArray.map { it.jsonObject }) {
            val takes = p.getValue("takes").jsonArray.map { it.jsonObject }
            val written = takes.filter { it["spoken"]?.jsonPrimitive?.booleanOrNull != true }.map { it.getValue("case").jsonPrimitive.content }
            for (w in p.getValue("words").jsonArray.map { it.jsonPrimitive.content }) {
                val want = when {
                    "motion" in takes.map { it.getValue("sense").jsonPrimitive.content } -> null
                    written.size == 2 -> "afterEither"
                    takes.size == 2 -> "afterGenSpoken"
                    written == listOf("acc") -> "afterAcc"
                    written == listOf("gen") -> "afterGen"
                    else -> "afterDat"
                }
                if (want != null) assertEquals(w, want, key("Das Tier kommt $w dem Haus.", "dativ", "Das Tier kommt $w das Haus."))
            }
        }
        // a table with another case for mit would say that case: nothing is written here twice
        val other = GermanHints.parse("""{"prepositions":[{"words":["mit"],"takes":[{"case":"gen","sense":"other"}]}]}""")
        assertEquals("gen", other.prepositions.getValue("mit").single().case)
    }

    @Test fun `who does it, before the verb or after it`() {
        assertEquals("Die Enten", word("Die Enten schwimmen im Teich.", "praesens", "Die Enten schwimmt im Teich."))
        assertEquals("Herr Toni", word("Herr Toni kann das Rad reparieren.", "modalverben", "Herr Toni können das Rad reparieren."))
        assertEquals("wie viele Leute", word("Und wie viele Leute wohnen hier?", "praesens", "Und wie viele Leute wohnt hier?"))
        assertEquals("sie", word("Ich glaube, sie halten. Sie sind aus Stein.", "praesens", "Ich glaube, sie haltet. Sie sind aus Stein."))
        // after the verb: a question, something else first
        assertEquals("die Eier", word("Was kosten die Eier, Oma Resi?", "praesens", "Was kostet die Eier, Oma Resi?"))
        assertEquals("wir", word("Dann gewinnen wir!", "praesens", "Dann gewinnt wir!"))
        assertEquals("die Bienen", word("Mit Rauch sind die Bienen ruhig.", "plural", "Mit Rauch ist die Bienen ruhig."))
        assertEquals("sie", word("Fressen sie Mücken?", "praesens", "Frisst sie Mücken?"))
        // wer, was alone, das
        assertEquals("Wer", word("Oje! Wer trinkt denn seinen Schnaps?", "praesens", "Oje! Wer trinken denn seinen Schnaps?"))
        assertEquals("Was", word("Guten Morgen, Stanko! Was blüht denn da?", "praesens", "Guten Morgen, Stanko! Was blühen denn da?"))
        assertEquals("Das", word("Med ist Slowenisch. Das heißt Honig.", "praesens", "Med ist Slowenisch. Das heiße Honig."))
        // the vowel a verb changes in du and er: one verb still (kreist, frisst, blase, nimmt)
        assertEquals("Er", word("Er kreist ganz ruhig.", "praesens", "Er kreisen ganz ruhig."))
        assertEquals("Manche", word("Manche nehmen auch Fleisch für die Fülle, oder?", "indefinitpronomen", "Manche nimmt auch Fleisch für die Fülle, oder?"))
        // a noun's plural isn't one verb's person
        assertNull(hint("Alte Pfähle aus Holz.", "praesens", "Alte Pfahle aus Holz.").trigger)
        val t = TurnHints.text(hint("Die Enten schwimmen im Teich.", "praesens", "Die Enten schwimmt im Teich."), page)
        assertEquals("kdo: «Die Enten»", t.lines[0])
        assertTrue(t.lines[1], t.lines[1].startsWith("Wer? "))
    }

    @Test fun `the case after a preposition, by the table`() {
        assertEquals("afterDat", key("Am besten graben wir mit einer Schaufel.", "dativ", "Am besten graben wir mit eine Schaufel."))
        assertEquals("za «mit»: dajalnik", line(hint("Am besten graben wir mit einer Schaufel.", "dativ", "Am besten graben wir mit eine Schaufel."))[0])
        assertEquals("afterAcc", key("Das ist ein guter Handel für den Bauern!", "akkusativ", "Das ist ein guter Handel für dem Bauern!"))
        // past the determiner and the adjective, and through and
        assertEquals("Mit", word("Mit starken Beschlägen hält das Tor lange.", "dativ", "Mit starke Beschlägen hält das Tor lange."))
        assertEquals("Mit", word("Mit dem Hammer und dem Meißel!", "dativ", "Mit dem Hammer und der Meißel!"))
        // was für ein is no preposition: the nominative after ist
        assertEquals("copula", key("Nein, noch nie. Was ist das für ein Stein?", "faelle", "Nein, noch nie. Was ist das für einen Stein?"))
        // a word's meaning at the gap: nothing said
        assertNull(hint("Er kommt aus dem Wald!", "dativ", "Er kommt aus dem Trog!").trigger)
    }

    @Test fun `a two-way preposition by the verb, by a noun of time, else both`() {
        assertEquals("ob «liegt»: Wo? Dajalnik", line(hint("Dein Stock liegt direkt neben der Daube!", "wechselpraepositionen", "Dein Stock liegt direkt neben die Daube!"))[0])
        assertEquals("ob «lege»: Wohin? Tožilnik", line(hint("Ich lege ihn auf die Waage.", "wechselpraepositionen", "Ich lege ihn auf der Waage."))[0])
        assertEquals("whereTo", key("Komm schnell ins Zelt, Florian!", "wechselpraepositionen", "Komm schnell im Zelt, Florian!"))
        assertEquals("whenDat", key("Vor sechstausend Jahren, in der Steinzeit.", "dativ", "Vor sechstausend Jahre, in der Steinzeit."))
        // hängt is both, no verb, a verb the table hasn't: both said, nothing guessed
        assertEquals("twoWay", key("Dein Hut hängt an der Stange.", "wechselpraepositionen", "Dein Hut hängt an die Stange."))
        assertEquals("twoWay", key("In den Garten!", "wechselpraepositionen", "In dem Garten!"))
        assertEquals("twoWay", key("Wir stecken den Schlauch in den Bach.", "wechselpraepositionen", "Wir stecken den Schlauch in dem Bach."))
    }

    @Test fun `a verb with the dative, the object of a verb, the chunk with its preposition`() {
        assertEquals("verbDat", key("Gute Idee! Ich helfe dir.", "dativ", "Gute Idee! Ich helfe dich."))
        assertEquals("gefällt", word("Die Musik gefällt mir.", "dativ", "Die Musik gefällt ich."))
        assertEquals("verbWhom", key("Der Bauer gab dem Herzog den Klaps.", "dativ", "Der Bauer gab den Herzog den Klaps."))
        assertEquals("helfen", word("Super, Jakob! Soll ich dir helfen?", "dativ", "Super, Jakob! Soll ich dich helfen?"))
        assertEquals("object", key("Ich komme! Ich nehme den Rechen.", "akkusativ", "Ich komme! Ich nehme der Rechen."))
        assertEquals("halten", word("Darf ich den Hammer halten, Herr Toni?", "akkusativ", "Darf ich der Hammer halten, Herr Toni?"))
        assertEquals("fragen", word("Zuerst fragen wir den Fahrer.", "akkusativ", "Zuerst fragen wir der Fahrer."))
        assertEquals("Stell", word("Stell die Milch aufs Bankerl!", "akkusativ", "Stell der Milch aufs Bankerl!"))
        assertEquals("bitte", key("Einen Kaffee, bitte.", "akkusativ", "Ein Kaffee, bitte."))
        // a measure's accusative after ist: nothing said (no copula's nominative)
        assertNull(hint("Es ist einen Meter lang.", "akkusativ", "Es ist ein Meter lang.").trigger)
        assertEquals("Angst vor", word("Die Katze hat Angst vor der Uhr!", "dativ", "Die Katze hat Angst vor die Uhr!"))
        assertEquals("withAcc", key("Schön, dass der Stein heute an beide Völker erinnert.", "verben-praepositionen", "Schön, dass der Stein heute an beiden Völker erinnert."))
        assertEquals("zufrieden", word("Am Ende ist er sicher wieder zufrieden mit Ihrer Arbeit.", "adjektive-praepositionen", "Am Ende ist er sicher wieder zufrieden von Ihrer Arbeit."))
        assertEquals("gespannt", word("Darauf bin ich gespannt.", "adjektive-praepositionen", "Dafür bin ich gespannt."))
        assertEquals("erinnert", word("Ein schöner Brauch, der daran erinnert, dass Brot gut ist.", "wo-da-woerter", "Ein schöner Brauch, der dafür erinnert, dass Brot gut ist."))
    }

    @Test fun `the gender of a noun, an adjective's ending, whose it is, the n-Deklination`() {
        assertEquals("ob «Tee»: odloči njegov spol", line(hint("Danke, Oma Resi! Der Tee ist heiß.", "artikel-genus", "Danke, Oma Resi! Das Tee ist heiß."))[0])
        assertEquals("Tuch", word("Das weiße Tuch ist sehr schön.", "artikel-genus", "Der weiße Tuch ist sehr schön."))
        assertEquals("Fuchs", word("Ein Fuchs! Er ist rot.", "artikel-genus", "Ein Fuchs! Sie ist rot."))
        // no noun in the sentence before: nothing (Ja is no noun)
        assertNull(hint("Ja, sie riecht so gut!", "artikel-genus", "Ja, er riecht so gut!").trigger)
        assertEquals("adjAfter", key("Mit dem schweren Hammer, der neben dem Amboss liegt.", "adjektiv-tabelle", "Mit dem schwerem Hammer, der neben dem Amboss liegt."))
        assertEquals("adjEin", key("Das ist aber ein schneller Tanz!", "adjektiv-tabelle", "Das ist aber ein schnelles Tanz!"))
        assertEquals("adjNone", key("Nimm warme Socken mit.", "adjektiv-tabelle", "Nimm warmen Socken mit."))
        assertEquals("afterEtwas", key("Schreiben Sie ihm einfach etwas Nettes zurück.", "adjektiv-nomen", "Schreiben Sie ihm einfach etwas Netten zurück."))
        // the article before it is another phrase's: nothing
        assertNull(hint("Dass man die von einem Männlein ausgesprochene Warnung ernst nehmen sollte.", "partizipialattribut", "Dass man die von einem Männlein ausgesprochenen Warnung ernst nehmen sollte.").trigger)
        assertEquals("whose", key("Nein, aber ich kenne Bilder der Burg.", "genitiv", "Nein, aber ich kenne Bilder des Burg."))
        assertEquals("genEnding", key("Vielleicht hat man die Jahresringe des Holzes gezählt.", "genitiv", "Vielleicht hat man die Jahresringe des Holz gezählt."))
        assertEquals("weakNoun", key("Haben Sie mit dem Experten selber geredet?", "n-deklination", "Haben Sie mit dem Experte selber geredet?"))
    }

    @Test fun `more than one, kein or nicht, du or Sie`() {
        assertEquals("za «Zwei»: več kot eno", line(hint("Zwei Mütter.", "plural", "Zwei Mutter."))[0])
        assertEquals("Wie viele", word("Wie viele Schafe hast du, Florian?", "plural", "Wie viele Schaf hast du, Florian?"))
        assertEquals("Drei", word("Drei Köpfe? Lustig!", "plural", "Drei Kopf? Lustig!"))
        assertEquals("hundert", word("Zehnmal hundert Stufen? Das sind tausend!", "plural", "Zehnmal hundert Stufe? Das sind tausend!"))
        // Kilo stays after a number: no line says more than one
        assertNull(hint("Das sind genau zwei Kilo.", "plural", "Das sind genau zwei Kilos.").trigger)
        assertEquals("verbMany", key("Die Blüten sind wunderschön!", "plural", "Die Blüte sind wunderschön!"))
        assertEquals("oneOrMore", key("Ich sehe nur noch die Dächer.", "plural", "Ich sehe nur noch das Dächer."))
        assertEquals("große Spuren", word("Das sind große Spuren!", "plural", "Das ist große Spuren!"))
        assertEquals("die Spuren", word("Sind das die Spuren von einem Reh?", "fragen", "Ist das die Spuren von einem Reh?"))
        // es ist sieben Uhr: who, not more than one
        assertEquals("Es", word("Es ist schon sieben Uhr!", "sein-haben", "Es sind schon sieben Uhr!"))
        assertEquals("noNoun", key("Arme Katze! Sie mag kein Wasser.", "kein-nicht", "Arme Katze! Sie mag nicht Wasser."))
        assertEquals("noVerb", key("Ja, das stimmt. Libellen stechen nicht.", "kein-nicht", "Ja, das stimmt. Libellen stechen kein."))
        assertEquals("sehe", word("Nein, das Dorf sehe ich nicht.", "kein-nicht", "Nein, das Dorf sehe ich kein."))
        assertEquals("komu govoriš: «Opa Sepp»", line(hint("Danke, Opa Sepp! Wie geht es dir?", "du-sie", "Danke, Opa Sepp! Wie geht es Ihnen?"))[0])
        assertEquals("Herr Franz", word("Kann ich Ihnen helfen, Herr Franz?", "du-sie", "Kann ich dir helfen, Herr Franz?"))
        assertEquals("Feuerwehr", word("Tuuut! Feuerwehr, kommt!", "imperativ", "Tuuut! Feuerwehr, kommst!"))
    }

    @Test fun `sein or haben, the helper before an infinitive or a participle`() {
        assertEquals("gefahren", word("Er ist bis zum Ende gefahren. Sehr weit!", "perfekt", "Er hat bis zum Ende gefahren. Sehr weit!"))
        assertEquals("byParticiple", key("Ich frage mich, ob Domitian wirklich gelebt hat.", "perfekt", "Ich frage mich, ob Domitian wirklich gelebt ist."))
        assertEquals("weggeflogen", word("Der grüne Stock ist weggeflogen!", "perfekt", "Der grüne Stock hat weggeflogen!"))
        assertEquals("feelHaben", key("Ja, ich habe großen Hunger!", "sein-haben", "Ja, ich bin großen Hunger!"))
        assertEquals("haveThing", key("Er hat eine schöne Mähne!", "sein-haben", "Er ist eine schöne Mähne!"))
        assertEquals("alt", word("Wie alt bist du, Opa Sepp?", "sein-haben", "Wie alt hast du, Opa Sepp?"))
        assertEquals("fertig", word("Oh, toll! Ist der Honig schon fertig?", "sein-haben", "Oh, toll! Hat der Honig schon fertig?"))
        assertEquals("infinitive", key("Darf ich eine Kerze machen?", "modalverben", "Darf ich eine Kerze mache?"))
        assertEquals("wollte", word("Ich wollte dir gute Nacht sagen.", "modalverben", "Ich wollte dir gute Nacht gesagt."))
        assertEquals("lässt", word("Das lässt sich nur mit einer Probe herausfinden.", "passiv-ersatz", "Das lässt sich nur mit einer Probe herausgefunden."))
        assertEquals("doubleInf", key("Sie hätte reich bleiben können, aber sie hat lieber anderen geholfen.", "modalverben", "Sie hätte reich bleiben gekonnt, aber sie hat lieber anderen geholfen."))
        assertEquals("participle", key("Ich habe Holz gehackt.", "perfekt", "Ich habe Holz gehacken."))
        // sein the possessive is no helper; the helper after it, in a clause with its verb last
        assertEquals("hat", word("Dann hat sich Bello sein Stück Brot wirklich verdient.", "partizip-zwei", "Dann hat sich Bello sein Stück Brot wirklich verdienen."))
        assertEquals("participleBefore", key("Unglaublich, dass sich das Holz so lange gehalten hat.", "partizip-zwei", "Unglaublich, dass sich das Holz so lange gehaltet hat."))
    }

    @Test fun `the relative's case, what isn't so, a pair, when, the passive`() {
        assertEquals("verbDat", key("Das ist der Bub, dem ich gestern geholfen habe.", "relativsatz", "Das ist der Bub, den ich gestern geholfen habe."))
        assertEquals("afterDat", key("Der Stier war ein Köder, mit dem sie den Drachen anlocken wollten.", "relativsatz", "Der Stier war ein Köder, mit den sie den Drachen anlocken wollten."))
        assertEquals("whereAt", key("Und die Pfeiler, auf denen die Brücke steht?", "relativsatz", "Und die Pfeiler, auf die die Brücke steht?"))
        assertEquals("relObject", key("Ja, und der Käse, den ich dort gegessen habe, war der beste.", "relativsatz", "Ja, und der Käse, der ich dort gegessen habe, war der beste."))
        assertEquals("relSubject", key("Dein Hund, der immer bei den Schafen bleibt?", "relativsatz", "Dein Hund, den immer bei den Schafen bleibt?"))
        assertEquals("würde", word("Wenn ich krank wäre, würde ich es dir sagen.", "konjunktiv-zwei", "Wenn ich krank bin, würde ich es dir sagen."))
        assertEquals("würde", word("Wenn ich auf dem Stein säße, würde ich mir etwas wünschen.", "konjunktiv-zwei", "Wenn ich auf dem Stein saß, würde ich mir etwas wünschen."))
        assertEquals("Ohne", word("Ohne Uhr wäre ich ständig zu spät, glaube ich.", "konjunktiv-zwei", "Ohne Uhr war ich ständig zu spät, glaube ich."))
        assertEquals("polite", key("Ich hätte gern einen Verlängerten, bitte.", "konjunktiv-zwei", "Ich habe gern einen Verlängerten, bitte."))
        assertEquals("pastImagined", key("Das konnte keiner wissen. Ich hätte es genauso gemacht.", "konjunktiv-vergangenheit", "Das konnte keiner wissen. Ich habe es genauso gemacht."))
        assertEquals("sowohl", word("Sie brauchen sowohl Nägel als auch Leim, oder?", "doppelkonnektoren", "Sie brauchen sowohl Nägel und auch Leim, oder?"))
        assertEquals("nicht nur", word("Dafür brauchst du nicht nur Bretter, sondern auch neue Schindeln.", "doppelkonnektoren", "Dafür brauchst du nicht nur Bretter, aber auch neue Schindeln."))
        assertEquals("whenEvery", key("Immer wenn es geregnet hat, wusstet ihr dann nicht, wie spät es war?", "temporalsaetze", "Immer als es geregnet hat, wusstet ihr dann nicht, wie spät es war?"))
        assertEquals("whenOnce", key("Als ich zwölf war, glaube ich.", "temporalsaetze", "Wenn ich zwölf war, glaube ich."))
        assertEquals("wird", word("Daraus wird Heidensterz gekocht.", "passiv", "Daraus wird Heidensterz kochen."))
        assertEquals("müssen", word("Der Reindling und die Palatschinken müssen noch übersetzt werden.", "passiv", "Der Reindling und die Palatschinken müssen noch übersetzt worden."))
        assertEquals("worden", key("Und warum ist er damals so gejagt worden?", "passiv-zeiten", "Und warum ist er damals so gejagt geworden?"))
        assertEquals("Vorschlag", word("Du könntest doch einen Vorschlag machen.", "nomen-verb", "Du könntest doch einen Vorschlag stellen."))
        assertEquals("partNow", key("Die tanzenden Paare im Hintergrund feiern.", "partizip-adjektiv", "Die getanzten Paare im Hintergrund feiern."))
    }

    @Test fun `where the verb goes, at a turn of the same words in another order`() {
        fun order(right: String, wrong: String) = GermanHints.order(right, wrong) as? Trigger.Asks
        assertEquals("verbSecond" to "Morgen", order("Morgen komme ich wieder!", "Morgen ich komme wieder!")!!.let { it.line.removePrefix("hint.de.") to it.word })
        assertEquals("Im Winter", order("Im Winter essen wir sie!", "Im Winter wir essen sie!")!!.word)
        assertEquals("Wohin", order("Wohin fliegen sie?", "Wohin sie fliegen?")!!.word)
        assertEquals("hint.de.verbSecond", order("Bis Villach ist es weit!", "Bis Villach es ist weit!")!!.line)
        assertEquals("hint.de.verbLast", order("Damit alle pünktlich sind!", "Damit alle sind pünktlich!")!!.line)
        assertEquals("hint.de.verbLast", order("In der Nacht, damit ihn niemand sah.", "In der Nacht, damit niemand sah ihn.")!!.line)
        assertEquals("wie", order("Und dann weißt du, wie spät es ist!", "Und dann weißt du, wie spät ist es!")!!.word)
        assertEquals("hint.de.endVerb" to "kann", order("Der Winter kann kommen.", "Der Winter kommen kann.")!!.let { it.line to it.word })
        assertEquals("hint.de.noPlace", order("Entweder zahlt er, oder er hilft Ihnen.", "Entweder zahlt er, oder hilft er Ihnen.")!!.line)
        assertEquals("Obwohl das Wasser so kalt ist", order("Obwohl das Wasser so kalt ist, helfe ich dir gern.", "Obwohl das Wasser so kalt ist, ich helfe dir gern.")!!.word)
        // what moved is no verb, or the words differ: nothing
        assertNull(order("Ich glaube, du hast es nicht bereut.", "Ich glaube, du hast nicht es bereut."))
        assertNull(order("Ich mache die Tür zu!", "Ich zumache die Tür!"))
        // through the chip: a turn of no one word
        val h = TurnHints.of(DialogLine(choices = listOf(DialogChoice("Morgen helfe ich im Dorf.", ok = true), wrong("Morgen ich helfe im Dorf.", "wortstellung"))), "de", page)!!
        assertEquals("Morgen", h.trigger?.word)
        assertEquals("«Morgen» je na prvem mestu: glagol pride takoj za tem", line(h)[0])
    }

    @Test fun `what people say in Kärnten isn't taught against`() {
        val wegen = "za «Während»: v pisavi rodilnik; v govoru je prav tudi dajalnik"
        assertEquals(wegen, line(hint("Während der Wallfahrt betet man und singt, oder?", "praepositionen-genitiv", "Während die Wallfahrt betet man und singt, oder?"))[0])
        // the table's spoken dative, for every preposition that has one
        assertEquals("afterGenSpoken", key("Trotz der Hitze? Dann hol ich mir schnell einen Strohhut.", "praepositionen-genitiv", "Trotz die Hitze? Dann hol ich mir schnell einen Strohhut."))
        // a turn where both are right: no line picks one
        val both = DialogLine(choices = listOf(DialogChoice("Wegen des Regens.", ok = true), DialogChoice("Wegen dem Regen.", ok = true), wrong("Wegen der Regen.", "praepositionen-genitiv")))
        assertNull(TurnHints.of(both, "de", page)!!.trigger)
        assertTrue(TurnHints.text(TurnHints.of(both, "de", page)!!, page).lines.joinToString().contains("v govoru"))
        // weil and obwohl: what grading has as said (Spelling.verbSecond), damit no
        for (w in listOf("weil", "obwohl")) {
            val t = GermanHints.order("Ja, $w es Heidenmehl ist.", "Ja, $w es ist Heidenmehl.") as Trigger.Asks
            assertEquals(w, "hint.de.verbLastSpoken", t.line)
        }
        assertEquals("hint.de.verbLast", (GermanHints.order("Ja, damit es Heidenmehl ist.", "Ja, damit es ist Heidenmehl.") as Trigger.Asks).line)
        // a weak noun with no -e is said as it is, and that is right (GermanForms: dem Bub); with -e it changes
        assertEquals("weakNounSpoken", key("Ich gebe dem Buben ein Eis.", "n-deklination", "Ich gebe dem Bub ein Eis."))
        assertEquals("weakNoun", key("Ich gebe dem Kollegen ein Eis.", "n-deklination", "Ich gebe dem Kollege ein Eis."))
        // the rules say so: the Perfekt for the past, ich bin gesessen, von with the dative, in every base a learner has
        for (base in listOf(Lang.SL, Lang.EN, Lang.IT)) {
            L10n.pair = LangPair(Lang.DE, base)
            val table = L10n.table(base)
            assertTrue("gesessen" in table.getValue("hint.de.rulePerfekt"))
            assertTrue("wegen dem Sturm" in table.getValue("hint.de.rulePraepGen"))
            assertTrue("Perfekt" in table.getValue("hint.de.rulePraeteritum"))
            assertTrue("von" in table.getValue("hint.de.ruleGenitiv"))
        }
        // no string of the German chip calls a form wrong: the chip reads, it doesn't grade
        val never = mapOf(Lang.EN to Regex("""\b(wrong|incorrect|never say|mistake)\b"""), Lang.SL to Regex("""\b(narobe|napačn\p{L}*|napaka)\b"""), Lang.IT to Regex("""\b(sbagliat\p{L}*|errore|scorrett\p{L}*)\b"""))
        for ((lang, bad) in never) for ((k, v) in L10n.table(lang)) if (k.startsWith("hint.de.") || k.startsWith("hintSay.de.")) assertFalse("${lang.code} $k: $v", bad.containsMatchIn(v.lowercase()))
    }

    @Test fun `the models show no form of the turn and one with the trigger's word comes first`() {
        val h = hint("Darf ich eine Kerze machen?", "modalverben", "Darf ich eine Kerze mache?")
        assertTrue(h.models.toString(), h.models.size == 2 && h.models.none { m -> h.forms.any { TurnHints.shows(m, it, stem = true) } })
        // after «mit»: a model with mit first
        val d = hint("Am besten graben wir mit einer Schaufel.", "dativ", "Am besten graben wir mit eine Schaufel.")
        assertTrue(d.models[0], "mit" in Forms.words(d.models[0]))
    }

    @Test fun `an English or an Italian base says it in its own words, the German as it is`() {
        L10n.pair = LangPair(Lang.DE, Lang.EN)
        val h = hint("Am besten graben wir mit einer Schaufel.", "dativ", "Am besten graben wir mit eine Schaufel.")
        assertEquals("after «mit»: the dative", TurnHints.text(h, page).lines[0])
        val said = HintSpeech.lines(h)
        assertTrue(said.toString(), said.isNotEmpty() && said.first().startsWith("After «mit»"))
        L10n.pair = LangPair(Lang.DE, Lang.IT)
        assertEquals("dopo «mit»: il dativo", TurnHints.text(h, page).lines[0])
    }

    @Test fun `a Slovene turn keeps its own rules, a German page the Slovene table doesn't read`() {
        val sl = Grammar.bundled("sl")
        val eggs = DialogLine(choices = listOf(DialogChoice("Deset jajc, prosim.", ok = true), wrong("Deset jajce, prosim.", "stevila-samostalniki")))
        assertTrue(TurnHints.of(eggs, "sl") { id -> sl.firstOrNull { it.id == id } }!!.trigger is Trigger.Count)
        val h = TurnHints.of(eggs, "de") { id -> sl.firstOrNull { it.id == id } }!!
        assertNull(h.rule)
        assertNull(h.trigger)
    }

    // --- over the curated turns -------------------------------------------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true }

    /** Kärnten's people, as the app passes them (SceneDialog's rememberHint): its villagers' names and its scenes' people's. */
    private val cast: List<String> by lazy {
        val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory }
        val kaernten = File(root, "cultures/kaernten")
        val villagers = File(kaernten, "villagers").listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { (json.parseToJsonElement(it.readText()) as? JsonObject)?.let { o -> str(o, "name") } }
        val people = File(kaernten, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().flatMap { parseScene(it.readText()).people.map { p -> p.name } }
        (villagers + people).distinct()
    }

    /** Every curated German turn: Kärnten's scenes (counting dialogs at a number of each category), steps, stories, parties, arrivals. */
    private fun curatedTurns(): List<Pair<String, DialogLine>> {
        val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory }
        val kaernten = File(root, "cultures/kaernten")
        val scenes = File(kaernten, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().map { parseScene(it.readText()) }.flatMap { s ->
            s.dialogs.flatMap { d ->
                val played = if (d.count != null) listOf(1, 2, 3, 12).map { Counts.render(d, it, "de") } else listOf(d)
                played.flatMap { p -> p.lines.filter { it.choices.size >= 2 }.map { "${s.id}/${p.id}" to it } }
            }
        }
        val others = kaernten.walk().filter { it.isFile && it.extension == "json" && "scenes" !in it.path.split(File.separatorChar) }.flatMap { f ->
            val raw = si.lanisce.lani.l10n.Learner.current.renderJson(f.readText())
            turnsIn(json.parseToJsonElement(raw)).map { "${f.name}" to it }
        }
        return scenes + others
    }

    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.contentOrNull

    private fun turnsIn(e: JsonElement): List<DialogLine> = when (e) {
        is JsonObject -> {
            val choices = (e["choices"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            val own = if (choices.size >= 2 && choices.all { str(it, "de") != null && "{" !in str(it, "de")!! }) listOf(
                DialogLine(grammar = str(e, "grammar"), choices = choices.map { c ->
                    DialogChoice(str(c, "de")!!, str(c, "sl").orEmpty(), ok = (c["ok"] as? JsonPrimitive)?.booleanOrNull == true, grammar = str(c, "grammar"), tap = str(c, "tap"))
                }),
            ) else emptyList()
            own + e.values.flatMap { turnsIn(it) }
        }
        is JsonArray -> e.flatMap { turnsIn(it) }
        else -> emptyList()
    }

    @Test fun `over the curated German turns the hint never gives the answer away, nearly all say a rule, and many name a trigger`() {
        for (base in listOf(Lang.SL, Lang.EN, Lang.IT)) {
            L10n.pair = LangPair(Lang.DE, base)
            var turns = 0
            var hints = 0
            var said = 0
            var formTurns = 0
            var triggered = 0
            var orderTurns = 0
            val report = mutableListOf<String>()
            val silent = mutableListOf<String>()
            val byLine = sortedMapOf<String, Int>()
            for ((where, line) in curatedTurns()) {
                turns++
                val h = TurnHints.of(line, "de", cast, page) ?: continue
                hints++
                val t = TurnHints.text(h, page)
                if (t.lines.isNotEmpty()) said++ else silent += "${h.pages.first()} | ${line.choices.firstOrNull { it.ok }?.sl}"
                for (s in t.lines + t.rows + t.links.mapNotNull { it.second }) assertTrue("$where: «$s» gives away ${h.right}", TurnHints.fair(s, h))
                for (m in h.models) assertFalse("$where: the model «$m» shows one of ${h.forms}", h.forms.any { TurnHints.shows(m, it, stem = true) })
                for (s in HintSpeech.lines(h)) assertTrue("$where: the 🔊 «$s» gives away ${h.right}", TurnHints.fair(HintSpeech.plain(s), h))
                h.trigger?.let { tr ->
                    val key = when (tr) {
                        is Trigger.Asks -> tr.line
                        is Trigger.Cue -> "cue." + tr.kind
                        else -> tr::class.simpleName!!
                    }
                    byLine[key] = (byLine[key] ?: 0) + 1
                }
                val right = line.choices.indexOfFirst { it.ok }.takeIf { line.choices.count { c -> c.ok } == 1 } ?: continue
                if (Forms.turn(line, right) == null) {
                    if (h.trigger != null) orderTurns++
                    continue
                }
                formTurns++
                report += "$where | ${line.choices[right].sl} | ${h.right} of ${h.forms} | ${h.pages.first()} | ${t.lines.firstOrNull()}"
                if (h.trigger != null) triggered++
            }
            if (base == Lang.SL) {
                File("build/reports/hints").apply { mkdirs() }.resolve("de.txt").writeText(report.sorted().joinToString("\n") + "\n")
                File("build/reports/hints").resolve("de-silent.txt").writeText(silent.sorted().joinToString("\n") + "\n")
            }
            println("German (${base.code}): $turns turns, $hints with a hint, $said with a rule or a trigger; $triggered of $formTurns form turns name what decides, $orderTurns turns of word order: $byLine")
            assertTrue("turns with a hint: $hints", hints > 900)
            assertTrue("$said of $hints hints say more than the link", said * 100 >= hints * 90)
            assertTrue("$triggered of $formTurns form turns name what decides their form", triggered * 100 >= formTurns * 70)
        }
    }

    @Test fun `what the cast adds on the curated German turns`() {
        val changed = curatedTurns().mapNotNull { (_, line) ->
            val h = TurnHints.of(line, "de", cast, page) ?: return@mapNotNull null
            val before = TurnHints.of(line, "de", page)?.trigger
            if (h.trigger != before) "${line.choices.firstOrNull { it.ok }?.sl} | ${before?.word} → ${h.trigger?.word}" else null
        }
        File("build/reports/hints").apply { mkdirs() }.resolve("de-cast.txt").writeText(changed.sorted().joinToString("\n") + "\n")
        println("German: the cast changes what decides at ${changed.size} turns")
    }

    @Test fun `over the curated German turns the 🔊 says something short, the German marked`() {
        var hints = 0
        var spoken = 0
        var longest = 0.0
        for ((_, line) in curatedTurns()) {
            val h = TurnHints.of(line, "de", cast, page) ?: continue
            hints++
            val pieces = HintSpeech.of(h)
            if (pieces.isNotEmpty()) spoken++
            assertNotNull(pieces)
            longest = maxOf(longest, HintSpeech.seconds(pieces))
        }
        println("German 🔊: $spoken of $hints say something, the longest about ${"%.0f".format(longest)} s")
        assertTrue("$spoken of $hints", spoken * 100 >= hints * 95)
        assertTrue("the longest takes $longest s", longest < 30)
    }
}
