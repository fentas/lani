package si.lanisce.lani.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
 * The hint's 🔊 (companion/SCENES.md, "The hint"): what it says for a turn, built from the hint's own data (what applies
 * here, the rule in one sentence, the question to ask, a model with other words), which pieces the phone's voice and the
 * app's Slovene voice say, and that it never says the answer, over the curated scenes.
 */
class HintSpeechTest {
    private val pages = Grammar.bundled("sl")
    private val page = { id: String -> pages.firstOrNull { it.id == id } }

    @After fun jansPair() {
        L10n.pair = LangPair.DEFAULT
    }

    private fun wrong(sl: String, grammar: String?) = DialogChoice(sl, why = "…", grammar = grammar)

    /** Micka at the market: twelve eggs. */
    private val eggs = DialogLine(choices = listOf(
        DialogChoice("Dvanajst jajc, prosim.", "Twelve eggs, please.", ok = true),
        wrong("Dvanajst jajce, prosim.", "stevila-samostalniki"),
        wrong("Dvanajst jajca, prosim.", "stevila-samostalniki"),
    ))

    /** Luka in the rain: come into the tent. */
    private val tent = DialogLine(choices = listOf(
        DialogChoice("Seveda, pridi v šotor! Sedi k svetilki.", ok = true),
        wrong("Seveda, pridi v šotoru! Sedi k svetilki.", "kam-tozilnik"),
    ))

    /** No bread. */
    private val bread = DialogLine(choices = listOf(
        DialogChoice("Ne, nimam kruha.", ok = true),
        wrong("Ne, nimam kruh.", "rodilnik-nikalnica"),
    ))

    private fun lines(line: DialogLine) = HintSpeech.lines(TurnHints.of(line, "sl", page)!!)

    @Test fun `a number turn says the number's category, the genitive plural, its question and a model of the category`() {
        assertEquals(
            listOf(
                "After «dvanajst», five and up:",
                "the noun goes into the genitive plural.",
                "Ask: «Koga? Česa?»",
                "Like: «pet hiš», «pet stolov», «pet jabolk».",
            ),
            lines(eggs),
        )
        // the English by the phone's voice, the Slovene by the app's, in turn
        assertEquals(
            listOf(
                HintPiece("After", false),
                HintPiece("dvanajst", true),
                HintPiece("five and up: the noun goes into the genitive plural. Ask:", false),
                HintPiece("Koga? Česa?", true),
                HintPiece("Like:", false),
                HintPiece("pet hiš", true),
                HintPiece("pet stolov", true),
                HintPiece("pet jabolk", true),
            ),
            HintSpeech.of(TurnHints.of(eggs, "sl", page)),
        )
    }

    @Test fun `a preposition turn says the preposition, the two cases its meaning decides between, both questions and a model`() {
        assertEquals(
            listOf(
                "After «v»:",
                "the meaning decides: where to, the accusative; where, the locative.",
                "Ask: «Kam?» or «Kje?»",
                "Like: «Grem v šolo.» «Grem na pošto.»",
            ),
            lines(tent),
        )
        // pod and the instrumental: a model with pod first
        val mushroom = DialogLine(choices = listOf(DialogChoice("Jurček je pod smreko!", ok = true), wrong("Jurček je pod smreka!", "orodnik")))
        assertEquals("Like: «pod mizo», «kava z mlekom».", lines(mushroom).last())
    }

    @Test fun `a negation turn says the no, the genitive, its question and a model with other words`() {
        assertEquals(
            listOf(
                "After the no «nimam»:",
                "the missing word goes into the genitive.",
                "Ask: «Koga? Česa?»",
                "Like: «Nimam časa.» «Ne pijem kave.»",
            ),
            lines(bread),
        )
    }

    @Test fun `a verb whose partner takes a case, a rule in words, a cue in the sentence`() {
        val help = DialogLine(choices = listOf(DialogChoice("Seveda vam pomagam.", ok = true), wrong("Seveda vas pomagam.", "dajalnik")))
        assertEquals(listOf("With «pomagam», like German helfen:", "the missing word goes into the dative.", "Ask: «Komu? Čemu?»"), lines(help).take(3))
        val you = DialogLine(choices = listOf(
            DialogChoice("Dober večer, babica Micka. Kaj delate?", ok = true),
            wrong("Dober večer, babica Micka. Kaj delaš?", "ti-vi"),
        ))
        assertEquals(
            listOf(
                "Someone older, a stranger, in a shop: «vi»; a friend, a child, family: «ti».",
                "Ask: «Ti ali vi?»",
                "Like: «Kako si?» «Kako ste?»",
            ),
            lines(you),
        )
        val two = DialogLine(choices = listOf(DialogChoice("Midva govoriva slovensko.", ok = true), wrong("Midva govorimo slovensko.", "dvojina")))
        assertEquals("Who does it: «midva».", lines(two).first())
    }

    @Test fun `a line that would say the answer is left out, one that sets every form side by side stays`() {
        // dva brata: the rule names dva and dve both, side by side
        val brothers = DialogLine(choices = listOf(DialogChoice("Dva brata. Dvojina!", ok = true), wrong("Dve brata. Dvojina!", "stevila-samostalniki")))
        val said = lines(brothers)
        assertEquals("With the noun «brata»:", said.first())
        assertTrue(said.any { "«dva, dve»" in it })
        // a rule whose Slovene is the answer alone: never said
        val nothing = DialogLine(choices = listOf(DialogChoice("Ne vidim nič.", ok = true), wrong("Ne vidim kaj.", "zanikanje")))
        val h = TurnHints.of(nothing, "sl", page)!!
        assertTrue(HintSpeech.lines(h).none { TurnHints.shows(HintSpeech.plain(it), "nič") })
    }

    @Test fun `a turn of a rule not yet says just that it comes later, the Slovene of a title in Slovene`() {
        val later = listOf("orodnik", "rodilnik-nikalnica").map { id -> page(id)!!.let { it.target to it.titleIn("en") } }
        assertEquals(
            listOf("You'll learn this later: The instrumental: with whom? with what?, «Nimam časa»: the genitive after a negation."),
            HintSpeech.lines(null, later),
        )
        assertEquals(HintPiece("You'll learn this later: The instrumental: with whom? with what?,", false), HintSpeech.of(null, later).first())
        assertEquals("The plural: «konji, ovce, okna»", HintSpeech.title("Množina: konji, ovce, okna", "The plural: konji, ovce, okna"))
        // nothing to say: no 🔊
        assertTrue(HintSpeech.of(null).isEmpty())
        assertTrue(HintSpeech.of(TurnHints.of(eggs, "it", page)).isEmpty())
    }

    @Test fun `in another base the same parts, the Slovene still marked`() {
        L10n.pair = LangPair(Lang.SL, Lang.DE)
        assertEquals(
            listOf("Nach «dvanajst», ab fünf:", "das Nomen steht im Genitiv Plural.", "Frag: «Koga? Česa?»", "Wie: «pet hiš», «pet stolov», «pet jabolk»."),
            lines(eggs),
        )
        assertEquals("die Bedeutung entscheidet: wohin, Akkusativ; wo, Lokativ.", lines(tent)[1])
        L10n.pair = LangPair(Lang.SL, Lang.IT)
        assertEquals(listOf("Dopo la negazione «nimam»:", "la parola mancante prende il caso genitivo."), lines(bread).take(2))
    }

    @Test fun `every rule the card says in words has its spoken sentence, in every table`() {
        val rules = L10n.table(Lang.EN).keys.filter { it.startsWith("hint.rule") }
        assertTrue(rules.size > 30)
        for (r in rules) {
            val said = HintSpeech.spoken(r)
            assertNotNull("$r has no spoken sentence", said)
            for (lang in Lang.entries) assertTrue("${lang.code}.json lacks $said", said!! in L10n.table(lang))
        }
    }

    @Test fun `the pieces, the marked parts in Slovene, punctuation between them left out`() {
        assertEquals(
            listOf(HintPiece("Where something is:", false), HintPiece("v, na, ob, pri", true), HintPiece("take the locative;", false), HintPiece("pod, nad", true), HintPiece("the instrumental.", false)),
            HintSpeech.pieces("Where something is: «v, na, ob, pri» take the locative; «pod, nad» the instrumental."),
        )
        assertEquals(listOf(HintPiece("a", true), HintPiece("b", true)), HintSpeech.pieces("«a», «b»."))
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

    @Test fun `over the curated scenes the 🔊 never says the answer nor a form of the turn, and stays short`() {
        var hints = 0
        var spoken = 0
        var longest = 0.0
        for ((where, line) in curatedTurns()) {
            val h = TurnHints.of(line, "sl", page) ?: continue
            if (TurnHints.text(h, page).isEmpty) continue
            hints++
            val said = HintSpeech.lines(h)
            if (said.isEmpty()) continue
            spoken++
            for (s in said) assertTrue("$where: «$s» gives away ${h.right}", TurnHints.fair(HintSpeech.plain(s), h))
            val pieces = HintSpeech.of(h)
            val slovene = pieces.filter { it.target }.joinToString(" ") { it.text }
            val shown = h.forms.filter { TurnHints.shows(slovene, it) }
            assertTrue("$where: the Slovene «$slovene» says $shown of ${h.forms}", shown.isEmpty() || (h.forms.size > 1 && shown.size == h.forms.size))
            assertFalse("$where: an empty piece", pieces.any { it.text.isBlank() })
            longest = maxOf(longest, HintSpeech.seconds(pieces))
        }
        println("🔊: $spoken of $hints hints say something, the longest about ${"%.1f".format(longest)} s")
        // a TL;DR in nearly every hint, and never a lecture
        assertTrue("$spoken of $hints", spoken * 10 >= hints * 9)
        assertTrue("the longest takes about $longest s", longest < 20.0)
    }

    @Test fun `the three examples take about 8 to 15 seconds`() {
        for (line in listOf(eggs, tent, bread)) {
            val s = HintSpeech.seconds(HintSpeech.of(TurnHints.of(line, "sl", page)))
            assertTrue("$s s", s in 6.0..15.0)
        }
    }
}
