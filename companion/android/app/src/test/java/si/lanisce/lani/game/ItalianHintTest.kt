package si.lanisce.lani.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * "📖 Namig · Hint" at an Italian turn (companion/SCENES.md, "The hint"; game/ItalianHints.kt): every page of the Italian
 * book has its rule in words, its spoken sentence and models; what in the sentence decides the form, on the curated
 * turns; and, over every curated Italian turn (Friuli's scenes, steps, stories, parties), that nothing the card or the 🔊
 * shows gives the answer away.
 */
class ItalianHintTest {
    private val pages = Grammar.bundled("it")
    private val page = { id: String -> pages.firstOrNull { it.id == id } }

    @After fun jansPair() {
        L10n.pair = LangPair.DEFAULT
    }

    private fun wrong(it: String, grammar: String) = DialogChoice(it, why = "…", grammar = grammar)

    private fun hint(right: String, page: String, vararg bad: String): TurnHint =
        TurnHints.of(DialogLine(choices = listOf(DialogChoice(right, ok = true)) + bad.map { wrong(it, page) }), "it", this.page)!!

    private fun line(h: TurnHint) = TurnHints.text(h, page).lines

    @Test fun `every page of the Italian book has its rule, its spoken sentence and models, in every table`() {
        assertEquals(70, pages.size)
        assertEquals(pages.map { it.id }.toSet(), ItalianHints.RULES.keys)
        for ((id, r) in ItalianHints.RULES) {
            assertTrue("$id: two models at least", r.models.size >= 2)
            assertEquals(id, r.said, HintSpeech.spoken(r.text))
            for (lang in Lang.entries) {
                assertTrue("${lang.code}.json lacks ${r.text}", r.text in L10n.table(lang))
                assertTrue("${lang.code}.json lacks ${r.said}", r.said in L10n.table(lang))
            }
        }
        // the keys are each page's own
        assertEquals(70, ItalianHints.RULES.values.map { it.text }.toSet().size)
    }

    @Test fun `an article, its noun's gender, number and first sound, and the rule with every article`() {
        val h = hint("Ci vuole uno scivolo.", "articoli-genere", "Ci vuole un scivolo.")
        assertEquals(Trigger.Asks("scivolo", 3, "hint.it.agreesSound", "hintSay.with"), h.trigger)
        assertEquals("hint.it.ruleArticle", h.rule)
        val t = TurnHints.text(h, page)
        assertEquals("with «scivolo»: its gender, its number and its first sound", t.lines[0])
        // the rule names un and uno side by side: kept
        assertTrue(t.lines[1].startsWith("Every noun is masculine or feminine"))
        // a pronoun lo or la before a verb is no article: no noun named
        assertNull(hint("Sì! Lo facciamo volare insieme!", "articoli-genere", "Sì! La facciamo volare insieme!").trigger)
        // a fused preposition: the noun after it
        assertEquals("sponda", hint("È lì, vicino alla sponda.", "preposizioni-articolate", "È lì, vicino al sponda.").trigger!!.word)
    }

    @Test fun `an adjective agrees with its noun, before it, after it, or the subject of essere`() {
        assertEquals("Il cesto", hint("Il cesto è pieno!", "aggettivi-accordo", "Il cesto è piena!").trigger!!.word)
        assertEquals("una finestra", hint("Serve una finestra rotonda.", "aggettivi-accordo", "Serve una finestra rotondo.").trigger!!.word)
        assertEquals("castello", hint("Che bel castello!", "aggettivi-accordo", "Che bella castello!").trigger!!.word)
        assertEquals("Questa", hint("Questa è gialla, come il sole.", "aggettivi-accordo", "Questa è giallo, come il sole.").trigger!!.word)
        // the one spoken to, when the adjective is about them
        val you = hint("Sei buffo, Tommaso!", "aggettivi-accordo", "Sei buffa, Tommaso!")
        assertEquals(Trigger.Asks("Tommaso", 2, "hint.it.toWhom", "hintSay.it.toWhom"), you.trigger)
        assertEquals("talking to «Tommaso»", line(you)[0])
        // "Che belle! Ma attento, Tommaso!": the adjective isn't about Tommaso
        assertNull(hint("Che belle! Ma attento, Tommaso!", "aggettivi-accordo", "Che belli! Ma attento, Tommaso!").trigger)
        // a choice of two meanings (pompa, sedia) is no form: nothing named
        assertNull(hint("È una vecchia pompa.", "aggettivi-accordo", "È una vecchia sedia.").trigger)
    }

    @Test fun `who does it, a pronoun or the noun before the verb, and the person's rule`() {
        assertEquals(Trigger.Cue("Io", 0, Trigger.Cue.Kind.WHO), hint("Io porto l'acqua.", "presente-regolare", "Io porti l'acqua.").trigger)
        assertEquals("noi", hint("Allora vinciamo noi!", "presente-regolare", "Allora vincono noi!").trigger!!.word)
        assertEquals("i vicini", hint("Con le pale i vicini scavano un fosso.", "presente-regolare", "Con le pale i vicini scavono un fosso.").trigger!!.word)
        assertEquals("l'agnellino", hint("Ma l'agnellino non beve.", "presente-regolare", "Ma l'agnellino non bevi.").trigger!!.word)
        assertEquals("anch'io", hint("Sì, vengo anch'io!", "verbi-irregolari", "Sì, vieni anch'io!").trigger!!.word)
        val t = TurnHints.text(hint("Io porto l'acqua.", "presente-regolare", "Io porti l'acqua."), page)
        assertEquals("who: «Io»", t.lines[0])
        assertTrue(t.lines[1].startsWith("Chi? The ending says who does it"))
        // con noi is no subject
        assertNull(hint("Gioca con noi.", "presente-regolare", "Giochi con noi.").trigger)
    }

    @Test fun `a name of the cast is who does it at a sentence's start, and the one spoken to`() {
        fun cast(right: String, page: String, bad: String) =
            TurnHints.of(DialogLine(choices = listOf(DialogChoice(right, ok = true), wrong(bad, page))), "it", listOf("Giulia", "Nonna Rosa"), this.page)!!
        // without the cast a word at the start may be Adesso: nothing named
        assertNull(hint("Giulia dorme sul prato.", "presente-regolare", "Giulia dormo sul prato.").trigger)
        assertEquals(Trigger.Cue("Giulia", 0, Trigger.Cue.Kind.WHO), cast("Giulia dorme sul prato.", "presente-regolare", "Giulia dormo sul prato.").trigger)
        assertEquals("Giulia", cast("Giulia è stanca.", "aggettivi-accordo", "Giulia è stanco.").trigger!!.word)
        assertEquals("Giulia", cast("Giulia, vieni qui!", "imperativo", "Giulia, venite qui!").trigger!!.word)
        assertEquals("talking to «Giulia»", line(cast("Giulia, vieni qui!", "imperativo", "Giulia, venite qui!"))[0])
    }

    @Test fun `essere or avere, by the participle, a feeling, a reflexive verb, and the participle after its helper`() {
        assertEquals("scelto", hint("Hai scelto un posto piano, nonno.", "passato-prossimo", "Sei scelto un posto piano, nonno.").trigger!!.word)
        assertEquals("with «scelto»: its verb decides, essere or avere", line(hint("Hai scelto un posto piano, nonno.", "passato-prossimo", "Sei scelto un posto piano, nonno."))[0])
        assertEquals("portato", hint("Avevano già portato le travi.", "trapassato", "Erano già portato le travi.").trigger!!.word)
        assertEquals("fame", hint("Ho fame, Aldo!", "essere-avere", "Sono fame, Aldo!").trigger!!.word)
        assertEquals("anni", hint("Lorenzo ha sei anni.", "essere-avere", "Lorenzo sono sei anni.").trigger!!.word)
        assertEquals("Mi", hint("Mi sono bagnato dappertutto.", "riflessivi", "Mi ho bagnato dappertutto.").trigger!!.word)
        assertEquals("hanno", hint("I vicini hanno tolto i sassi.", "participi-irregolari", "I vicini hanno togliuto i sassi.").trigger!!.word)
        assertEquals("after «avremo»: the participle", line(hint("Quando avremo seminato tutto, andremo a giocare.", "futuro-anteriore", "Quando avremo seminare tutto, andremo a giocare."))[0])
        // the same helper in two tenses (abbiamo, avremo) is no choice of helper
        assertEquals("Quando", hint("Quando avremo finito, giochiamo.", "futuro-anteriore", "Quando abbiamo finito, giochiamo.").trigger!!.word)
    }

    @Test fun `the congiuntivo after its word, se in either half, cui after a preposition, che after its noun`() {
        assertEquals("Bisogna che", hint("Bisogna che l'orologio sia alzato con le corde.", "congiuntivo", "Bisogna che l'orologio è alzato con le corde.").trigger!!.word)
        assertEquals("Vorrei che", hint("Vorrei che l'acqua arrivasse nella roggia.", "congiuntivo", "Vorrei che l'acqua arrivassero nella roggia.").trigger!!.word)
        assertEquals("after «Bisogna che»: the congiuntivo", line(hint("Bisogna che l'orologio sia alzato con le corde.", "congiuntivo", "Bisogna che l'orologio è alzato con le corde."))[0])
        val half = hint("Se le assi non fossero spesse, si romperebbero.", "periodo-ipotetico", "Se le assi non fosse spesse, si romperebbero.")
        assertEquals(Trigger.Asks("Se", 0, "hint.it.ifHalf", "hintSay.after"), half.trigger)
        val other = hint("Se avessi un cuscino, dormirei qui.", "periodo-ipotetico", "Se avessi un cuscino, dormirò qui.")
        assertEquals("«Se» in the other half: this half says what follows", line(other)[0])
        assertEquals("di", hint("Sono gli ingranaggi di cui parla la maestra.", "relativi-cui", "Sono gli ingranaggi di che parla la maestra.").trigger!!.word)
        assertEquals("gli ingranaggi", hint("Sono gli ingranaggi che fanno girare le lancette.", "relative-che", "Sono gli ingranaggi chi fanno girare le lancette.").trigger!!.word)
    }

    @Test fun `who is spoken to, the no, the infinitive, the thing liked, the hour, the place, a number`() {
        assertEquals("maestra", hint("Sto bene, grazie. E Lei, maestra?", "tu-lei", "Sto bene, grazie. E tu, maestra?").trigger!!.word)
        assertEquals("zia", hint("Tira la corda, zia!", "imperativo", "Tira la torta, zia!").trigger!!.word)
        assertEquals("pecore", hint("Lo tengo! Venite, pecore!", "imperativo", "Lo tengo! Vieni, pecore!").trigger!!.word)
        assertEquals(Trigger.Negation("Non", 0), hint("Non toccare!", "imperativo", "Non tocca!").trigger)
        assertEquals("Dobbiamo", hint("Dobbiamo scegliere un posto.", "modali", "Dobbiamo scegliamo un posto.").trigger!!.word)
        assertEquals("Cominciamo", hint("Cominciamo a scavare il fosso.", "infinito-preposizioni", "Cominciamo di scavare il fosso.").trigger!!.word)
        assertEquals("la cioccolata", hint("Sì, grazie! Mi piace la cioccolata.", "piacere", "Sì, grazie! Mi piacciono la cioccolata.").trigger!!.word)
        assertEquals("mi", hint("Sì, mi piace tanto!", "piacere", "Sì, mi piaccio tanto!").trigger!!.word)
        assertEquals("i sassi", hint("Prima si tolgono i sassi.", "si-impersonale", "Prima si togliamo i sassi.").trigger!!.word)
        assertEquals("quattro", hint("Va bene. Domani di nuovo, alle quattro?", "ore-giorni", "Va bene. Domani di nuovo, a quattro?").trigger!!.word)
        assertEquals("casetta", hint("Le api abitano in una casetta.", "dove-preposizioni", "Le api abitano a una casetta.").trigger!!.word)
        assertEquals("after «due»: more than one", line(hint("Ecco due pietre piccole!", "plurale", "Ecco due pietra piccole!"))[0])
        assertEquals("Ogni", hint("Ogni pietra deve stare ferma.", "indefiniti", "Ogni pietre deve stare ferma.").trigger!!.word)
        assertEquals("più", hint("Questo pilastro è più alto di quello.", "comparativi", "Questo pilastro è più alto come quello.").trigger!!.word)
        assertEquals("la", hint("Te la passo io, nonno!", "pronomi-combinati", "Ti la passo io, nonno!").trigger!!.word)
        assertEquals("sta", hint("Il carro sta passando sul ponte!", "stare-gerundio", "Il carro sta passato sul ponte!").trigger!!.word)
        assertEquals("sta", hint("La ruota sta per girare!", "stare-per", "La ruota sta di girare!").trigger!!.word)
        assertEquals("Ha detto che", hint("Ha detto che sarebbe venuta.", "condizionale-passato", "Ha detto che saresti venuta.").trigger!!.word)
        assertEquals("Mentre", hint("Mentre leggevo, Moro è entrato.", "imperfetto-passato", "Mentre lessi, Moro è entrato.").trigger!!.word)
    }

    @Test fun `the models show no form of the turn and one with the trigger's word comes first`() {
        val h = hint("Questo pilastro è più alto di quello.", "comparativi", "Questo pilastro è più alto come quello.")
        // «più grande di me», «alto come Tommaso» show a form of the turn (di, come): left out
        assertEquals(listOf("più forte del vento", "meno freddo dell'inverno"), h.models)
    }

    @Test fun `Teo's pair, German from Italian, says it in German with the Italian as it is`() {
        L10n.pair = LangPair(Lang.IT, Lang.DE)
        val t = TurnHints.text(hint("Manca il tetto.", "articoli-genere", "Manca la tetto."), page)
        assertTrue(t.lines[0], t.lines[0].contains("«tetto»"))
        assertTrue(t.lines[0], t.lines[0] != "with «tetto»: its gender, its number and its first sound")
        val said = HintSpeech.lines(hint("Manca il tetto.", "articoli-genere", "Manca la tetto."))
        assertTrue(said.toString(), said.isNotEmpty() && said.none { "gender" in it })
    }

    @Test fun `a Slovene turn keeps its own rules, an Italian page the Slovene table doesn't read`() {
        val sl = Grammar.bundled("sl")
        val eggs = DialogLine(choices = listOf(DialogChoice("Deset jajc, prosim.", ok = true), wrong("Deset jajce, prosim.", "stevila-samostalniki")))
        assertTrue(TurnHints.of(eggs, "sl") { id -> sl.firstOrNull { it.id == id } }!!.trigger is Trigger.Count)
        // a Slovene page asked for in Italian: the links alone
        val h = TurnHints.of(eggs, "it") { id -> sl.firstOrNull { it.id == id } }!!
        assertNull(h.rule)
        assertNull(h.trigger)
    }

    // --- over the curated turns -------------------------------------------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true }

    /** Every curated Italian turn: Friuli's scenes (counting dialogs at a number of each category), steps, stories, parties, arrivals. */
    private fun curatedTurns(): List<Pair<String, DialogLine>> {
        val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory }
        val friuli = File(root, "cultures/friuli")
        val scenes = File(friuli, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().map { parseScene(it.readText()) }.flatMap { s ->
            s.dialogs.flatMap { d ->
                val played = if (d.count != null) listOf(1, 2, 3, 12).map { Counts.render(d, it, "it") } else listOf(d)
                played.flatMap { p -> p.lines.filter { it.choices.size >= 2 }.map { "${s.id}/${p.id}" to it } }
            }
        }
        val others = friuli.walk().filter { it.isFile && it.extension == "json" && "scenes" !in it.path.split(File.separatorChar) }.flatMap { f ->
            val raw = si.lanisce.lani.l10n.Learner.current.renderJson(f.readText())
            turnsIn(json.parseToJsonElement(raw)).map { "${f.name}" to it }
        }
        return scenes + others
    }

    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.contentOrNull

    private fun turnsIn(e: JsonElement): List<DialogLine> = when (e) {
        is JsonObject -> {
            val choices = (e["choices"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            val own = if (choices.size >= 2 && choices.all { str(it, "it") != null && "{" !in str(it, "it")!! }) listOf(
                DialogLine(grammar = str(e, "grammar"), choices = choices.map { c ->
                    DialogChoice(str(c, "it")!!, str(c, "en").orEmpty(), ok = (c["ok"] as? JsonPrimitive)?.booleanOrNull == true, grammar = str(c, "grammar"), tap = str(c, "tap"))
                }),
            ) else emptyList()
            own + e.values.flatMap { turnsIn(it) }
        }
        is JsonArray -> e.flatMap { turnsIn(it) }
        else -> emptyList()
    }

    @Test fun `over the curated Italian turns the hint never gives the answer away, nearly all say a rule, and many name a trigger`() {
        var turns = 0
        var hints = 0
        var said = 0
        var formTurns = 0
        val report = mutableListOf<String>()
        var triggered = 0
        val byLine = sortedMapOf<String, Int>()
        for ((where, line) in curatedTurns()) {
            turns++
            val h = TurnHints.of(line, "it", page) ?: continue
            hints++
            val t = TurnHints.text(h, page)
            if (t.lines.isNotEmpty()) said++
            for (s in t.lines + t.rows + t.links.mapNotNull { it.second }) assertTrue("$where: «$s» gives away ${h.right}", TurnHints.fair(s, h))
            for (m in h.models) assertFalse("$where: the model «$m» shows one of ${h.forms}", h.forms.any { TurnHints.shows(m, it, stem = true) })
            for (s in HintSpeech.lines(h)) {
                assertTrue("$where: the 🔊 «$s» gives away ${h.right}", TurnHints.fair(HintSpeech.plain(s), h))
            }
            val right = line.choices.indexOfFirst { it.ok }.takeIf { line.choices.count { c -> c.ok } == 1 } ?: continue
            if (Forms.turn(line.choices.map { it.sl }, right) == null) continue
            formTurns++
            report += "$where | ${line.choices[right].sl} | ${h.right} of ${h.forms} | ${h.pages.first()} | ${t.lines.firstOrNull()}"
            h.trigger?.let { tr ->
                triggered++
                val key = when (tr) {
                    is Trigger.Asks -> tr.line
                    is Trigger.Cue -> "cue." + tr.kind
                    else -> tr::class.simpleName!!
                }
                byLine[key] = (byLine[key] ?: 0) + 1
            }
        }
        // every form turn's first line, for a reader: build/reports/hints/it.txt
        File("build/reports/hints").apply { mkdirs() }.resolve("it.txt").writeText(report.sorted().joinToString("\n") + "\n")
        println("Italian: $turns turns, $hints with a hint, $said with a rule or a trigger; $triggered of $formTurns form turns name what decides: $byLine")
        assertTrue("turns with a hint: $hints", hints > 900)
        assertTrue("$said of $hints hints say more than the link", said * 100 >= hints * 95)
        assertTrue("$triggered of $formTurns form turns name what decides their form", triggered * 100 >= formTurns * 45)
    }

    @Test fun `what the cast adds on the curated Italian turns`() {
        val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory }
        val friuli = File(root, "cultures/friuli")
        val villagers = File(friuli, "villagers").listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { (json.parseToJsonElement(it.readText()) as? JsonObject)?.let { o -> str(o, "name") } }
        val cast = (villagers + File(friuli, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().flatMap { parseScene(it.readText()).people.map { p -> p.name } }).distinct()
        val changed = curatedTurns().mapNotNull { (_, line) ->
            val h = TurnHints.of(line, "it", cast, page) ?: return@mapNotNull null
            val before = TurnHints.of(line, "it", page)?.trigger
            if (h.trigger != before) "${line.choices.firstOrNull { it.ok }?.sl} | ${before?.word} → ${TurnHints.text(h, page).lines.firstOrNull()}" else null
        }
        File("build/reports/hints").apply { mkdirs() }.resolve("it-cast.txt").writeText(changed.sorted().joinToString("\n") + "\n")
        println("Italian: the cast changes what decides at ${changed.size} turns")
    }

    @Test fun `over the curated Italian turns the 🔊 says something short, the Italian marked`() {
        var hints = 0
        var spoken = 0
        var longest = 0.0
        for ((_, line) in curatedTurns()) {
            val h = TurnHints.of(line, "it", page) ?: continue
            hints++
            val pieces = HintSpeech.of(h)
            if (pieces.isNotEmpty()) spoken++
            assertNotNull(pieces)
            longest = maxOf(longest, HintSpeech.seconds(pieces))
        }
        println("Italian 🔊: $spoken of $hints say something, the longest about ${"%.0f".format(longest)} s")
        assertTrue("$spoken of $hints", spoken * 100 >= hints * 95)
        assertTrue("the longest takes $longest s", longest < 30)
    }
}
