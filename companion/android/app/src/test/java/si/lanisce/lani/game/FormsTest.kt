package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Test
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.json
import si.lanisce.lani.game.scene.parseScene
import java.io.File

/** Which turns test a form (their gap), and the grammar page a wrong choice's why tells of (companion/SCENES.md, "Adaptive turns"). */
class FormsTest {
    @Test fun `choices that differ in one word are a form turn, the right one with a gap there`() {
        val g = Forms.gap(listOf("Deset jajce, prosim.", "Deset jajc, prosim.", "Deset jajca, prosim."), 1)!!
        assertEquals("Deset ____, prosim.", g.shown)
        assertEquals("jajc", g.word)
        assertEquals(1, g.slot)
        assertEquals("Deset jajc, prosim.", g.sentence)
        assertEquals("Deset jajce, prosim.", g.filled("jajce"))
        assertEquals(listOf("jajc", "Deset jajc, prosim."), g.accept)
        // case and punctuation aside
        assertEquals("sva", Forms.gap(listOf("Dobra ekipa sva!", "dobra ekipa smo"), 0)?.word)
    }

    @Test fun `choices that differ otherwise test no form`() {
        assertNull(Forms.gap(listOf("Ja, zelo.", "Dobro jutro!", "Na zdravje!"), 0)) // meaning
        assertNull(Forms.gap(listOf("Grem v šolo.", "Grem iz šole."), 0)) // two words
        assertNull(Forms.gap(listOf("Nimam časa.", "Nimam časa danes."), 0)) // another length
        assertNull(Forms.gap(listOf("Vidim ga.", "Vidim ga."), 0)) // no difference
        assertNull(Forms.gap(listOf("Adijo!"), 0))
    }

    @Test fun `a wrong choice that differs otherwise is no part of the form, and the word most differ in is the gap`() {
        val mixed = Forms.turn(listOf("Deset jajc, prosim.", "Dober dan!", "Deset jajce, prosim."), 0)!!
        assertEquals("Deset ____, prosim.", mixed.gap.shown)
        assertEquals(listOf(2), mixed.wrong)
        val most = Forms.turn(listOf("Dve ovci sta.", "Dve ovce sta.", "Dva ovci sta.", "Dve ovca sta."), 0)!!
        assertEquals("ovci", most.gap.word)
        assertEquals(listOf(1, 3), most.wrong)
        // a tie: the first wrong choice's word
        assertEquals("Dve", Forms.gap(listOf("Dve ovci sta.", "Dva ovci sta.", "Dve ovce sta."), 0)?.word)
    }

    @Test fun `an exercise's sentence with a gap, its translation left out of what is said`() {
        val g = Forms.gap("Nimam ___. (I don't have time.)", "časa")!!
        assertEquals("Nimam ____. (I don't have time.)", g.shown)
        assertEquals("Nimam časa.", g.sentence)
        assertEquals("Midva sva doma.", Forms.gap("___ sva doma. (We two are at home.)", "Midva")?.sentence)
        assertNull(Forms.gap("How do you say 'two'?", "dva"))
    }

    @Test fun `a word is replaced in place, its punctuation kept`() {
        assertEquals("Deset jajce, prosim.", Forms.replaceAt("Deset jajc, prosim.", 1, "jajce"))
        assertEquals("jajc", Forms.wordAt("Deset jajc, prosim.", 1))
        assertNull(Forms.wordAt("Deset jajc.", 5))
    }

    @Test fun `a wrong choice's why names the rule it tests`() {
        val cases = mapOf(
            "Two takes the dual: dve košuti, dve srni. Košute is for three or four." to "stevila-samostalniki",
            "Just the two of you: the dual greva. Gremo is for three or more." to "dvojina",
            "Babica Micka is old: vi. Pomagam vam." to "ti-vi",
            "Kuham is 'I cook'. You are asking her: (vi) kuhate." to "glagoli-sedanjik",
            "Koliko takes the genitive: koliko časa (how much time), koliko kruha." to "rodilnik-kolicina",
            "Koliko takes the genitive plural: koliko ovc?" to "rodilnik-kolicina",
            "Več (more) takes the genitive: več kruha." to "rodilnik-kolicina",
            "Brez (without) takes the genitive: brez sladkorja, brez mleka." to "rodilnik-predlogi",
            "After a negation the genitive: nimam časa." to "rodilnik-nikalnica",
            "Z (with) takes the instrumental: z mlekom. Mleko is the nominative." to "orodnik",
            "Putting something somewhere is a direction: na mizo (accusative). Na mizi is where it already is." to "kam-tozilnik",
            "Where something hangs is a place: v + locative, v šotoru. V šotor (accusative) is a direction, into the tent." to "kje-mestnik-orodnik",
            "The plates are the object: krožnike (accusative). Krožniki is the nominative." to "tozilnik",
            "Krompir is masculine: strupen. Strupena is feminine (goba je strupena)." to "pridevniki-ujemanje",
            "Kje is 'where'. Asking about the time: kdaj (when)." to "vprasalnice",
            "The past tense needs the participle: nisem videl. Vidim is the present." to "pretekli-cas",
            "One knife: je. So is for several (noži so)." to "biti",
            "One potica: se peče. Pečejo is the plural (potice se pečejo)." to "glagoli-sedanjik",
            "Iskre are several: so. Je is for one (iskra je vroča)." to "biti",
            "Prižgeš is 'you turn on'. Offering to do it yourself: prižgem (I)." to "glagoli-sedanjik",
            // the cases' pages and the rules the dialogs test
            "Pomagati takes the dative, like German helfen: pomagam vam (not vas)." to "dajalnik",
            "Feeling cold: the dative, meni je mrzlo (to me it is cold)." to "dajalnik",
            "Before k, p, t, s, š, c, č, f, h the preposition is s: s kruhom. Z goes before other sounds: z vinom." to "orodnik",
            // pod, nad, pred, za, med + the instrumental, where: the instrumental's page (kje-mestnik-orodnik is v, na, ob, pri)
            "Where something is: pod + instrumental, pod klopjo. Pod klop (accusative) is where it goes: žoga gre pod klop." to "orodnik",
            "Za (behind) takes the instrumental for a place: za smreko, za hišo." to "orodnik",
            "Na smreki is up on the tree. Mushrooms grow under it: pod smreko." to "orodnik",
            "Pod is under. They fly over us: nad nama (the two of us: the dual; nad nami for three or more)." to "orodnik",
            "Where it goes is a direction: za + accusative, za šotor. Za šotorom (instrumental) is where it is." to "kam-tozilnik",
            // the words of a why that name the newer pages, and a word of č, š, ž the JVM's \b doesn't see
            "Kam asks where to. Where something is: kje." to "vprasalnice",
            "Ujel sem te is 'I caught you' (done). Lovil sem te is only 'I was chasing you'." to "glagolski-vid",
            "Prvi is 'the first one'. For the first time: prvič." to "datumi",
            "One fish: postrv. Postrvi is more than one (dve postrvi)." to "mnozina",
            "After a verb of going, the supine: grem spat." to "namenilnik",
            "Čez takes the accusative: čez eno uro." to "kdaj-cas",
            "Pred eno uro is an hour ago. In an hour: čez eno uro." to "kdaj-cas",
            "O (about) takes the locative: o medvedu. Medvedom is the instrumental (z medvedom)." to "mestnik",
            "When you call someone, the name stays as it is: Anton. Antona is the genitive or accusative." to "imenovalnik",
            "Whose tracks: the genitive, sledi medveda (the bear's tracks)." to "rodilnik",
            "Malo (a little) takes the genitive: malo sena, malo vode." to "rodilnik-kolicina",
            "Comparing two days: the comparative, boljša kot. Najboljša is 'the best'." to "primernik",
            "She asks what you will do: the future, bom pogrnil. Sem pogrnil means it's already done." to "prihodnjik",
            "Bom spal is the future (I will sleep). She asks about last night: sem spal." to "pretekli-cas",
            "'I would like' is rad bi. Rad bom means I will gladly." to "pogojnik",
            "After moram comes the infinitive: moram narediti." to "modalni-glagoli",
            "Lahko goes with the present: lahko pomagam (I can help). The infinitive goes with moram, hočem, znam." to "modalni-glagoli",
            "Telling someone to wait: the imperative, počakaj! Počakam is 'I wait'." to "velelnik",
            "You are saying what you are doing: držim (I hold). Drži is the imperative, hold!" to "glagoli-sedanjik",
            "Bati se takes the genitive: bojim se nevihte." to "povratni-glagoli",
            "Grablje (rake) is plural in Slovene: jih. Jo is for one feminine thing (kosa: videl sem jo)." to "osebni-zaimki",
            "It's your own hat: svoj klobuk. Tvoj is Tine's, the one you're talking to." to "svoj",
            "'Sees well' needs the adverb dobro. Dobra is an adjective (dobra juha)." to "prislovi",
            "Ob takes the locative for times: ob petih, ob sedmih." to "kdaj-cas",
            "On a day of the week: v + the accusative, v nedeljo (v soboto, v petek)." to "kdaj-cas",
            "Slovene doubles the no: ne vidim nič. Without ne the sentence is wrong." to "zanikanje",
            "Short words keep a fixed order: sem comes before jih: videl sem jih." to "naslonke",
        )
        for ((why, page) in cases) assertEquals(why, page, Forms.rule(why))
        // a word's meaning has no page
        assertNull(Forms.rule("Žejen means thirsty. Hungry is lačen."))
        assertNull(Forms.rule(null))
        assertNull(Forms.rule(""))
    }

    /** [e] without its `grammar` keys, as an older bridge serves a scene. */
    private fun untagged(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.filterKeys { it != "grammar" }.mapValues { (_, v) -> untagged(v) })
        is JsonArray -> JsonArray(e.map(::untagged))
        else -> e
    }

    @Test fun `a scene that names its pages is read as it is, one that names none gets its pages guessed, from the book`() {
        val book = Grammar.bundled("sl").map { it.id }.toSet()
        val dir = listOf(File("../../scenes"), File("../scenes"), File("companion/scenes")).first { it.isDirectory }
        var forms = 0
        var guessed = 0
        var pages = 0
        var tagged = 0
        var agree = 0
        for (f in dir.listFiles { x -> x.extension == "json" }.orEmpty()) {
            val raw = f.readText()
            val named = parseScene(raw)
            val names = named.dialogs.any { d -> d.lines.any { l -> l.grammar != null || l.choices.any { it.grammar != null } } }
            // the scene's own pages: no guess beside them, and the guess mostly says the same
            for (d in named.dialogs) for (l in d.lines) for (c in l.choices) {
                if (names) assertNull("${named.id}/${d.id}: a scene that names pages gets no guess", c.guess)
                val page = c.grammar ?: l.grammar ?: continue
                if (c.ok) continue
                tagged++
                if (Forms.rule(c.why) == page) agree++
            }
            // as an older bridge serves it, without them: the guesses, pages of the book
            val s = parseScene(untagged(json.parseToJsonElement(raw)).toString())
            for ((di, d) in s.dialogs.withIndex()) for ((li, l) in d.lines.withIndex()) {
                val right = l.choices.indexOfFirst { it.ok }.takeIf { r -> r >= 0 && l.choices.count { it.ok } == 1 } ?: continue
                val form = Forms.turn(l.choices.map { it.sl }, right) ?: continue
                for (ci in form.wrong) {
                    val c = l.choices[ci]
                    forms++
                    assertNull(c.grammar)
                    // the scene's own page for it
                    val own = named.dialogs[di].lines[li]
                    if ((own.choices[ci].grammar ?: own.grammar) != null) pages++
                    c.guess?.let {
                        guessed++
                        assertTrue("${s.id}/${d.id}: $it", it in book)
                    }
                }
            }
        }
        assertTrue("form turns: $forms", forms > 100)
        // the scenes name the page of nine in ten of their form choices or more (a word's meaning has none)
        assertTrue("$pages of $forms form choices name their page", pages * 10 >= forms * 9)
        // nine in ten or more (a word's meaning, a greeting's hour have no page)
        assertTrue("$guessed of $forms form choices get a page", guessed * 10 >= forms * 9)
        // the scenes' own pages: the guess says the same nine times in ten
        assertTrue("the guess agrees with $agree of $tagged pages the scenes name", tagged > 700 && agree * 10 >= tagged * 9)
    }
}
