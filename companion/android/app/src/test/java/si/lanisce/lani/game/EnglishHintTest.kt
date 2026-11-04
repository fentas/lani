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
 * "📖 Namig · Hint" at an English turn (companion/SCENES.md, "The hint"; game/EnglishHints.kt): every page of the English
 * book has its rule in words, its spoken sentence and models; what in the sentence decides the form, on the curated
 * turns; and, over every curated English turn (Lakeland's scenes, steps, stories, parties), that nothing the card or the
 * 🔊 shows gives the answer away.
 */
class EnglishHintTest {
    private val pages = Grammar.bundled("en")
    private val page = { id: String -> pages.firstOrNull { it.id == id } }

    @After fun jansPair() {
        L10n.pair = LangPair.DEFAULT
    }

    private fun wrong(en: String, grammar: String) = DialogChoice(en, why = "…", grammar = grammar)

    private fun hint(right: String, page: String, vararg bad: String, cast: List<String> = emptyList()): TurnHint =
        TurnHints.of(DialogLine(choices = listOf(DialogChoice(right, ok = true)) + bad.map { wrong(it, page) }), "en", cast, this.page)!!

    private fun word(right: String, page: String, vararg bad: String, cast: List<String> = emptyList()) = hint(right, page, *bad, cast = cast).trigger?.word

    private fun line(h: TurnHint) = TurnHints.text(h, page).lines

    @Before fun learnsEnglish() {
        // an English learner from Slovene: the card in Slovene, the English as it is
        L10n.pair = LangPair(Lang.EN, Lang.SL)
    }

    @Test fun `every page of the English book has its rule, its spoken sentence and models, in every table`() {
        assertEquals(67, pages.size)
        assertEquals(pages.map { it.id }.toSet(), EnglishHints.RULES.keys)
        for ((id, r) in EnglishHints.RULES) {
            assertTrue("$id: two models at least", r.models.size >= 2)
            assertEquals(id, r.said, HintSpeech.spoken(r.text))
            for (lang in Lang.entries) {
                assertTrue("${lang.code}.json lacks ${r.text}", r.text in L10n.table(lang))
                assertTrue("${lang.code}.json lacks ${r.said}", r.said in L10n.table(lang))
            }
        }
        assertEquals(67, EnglishHints.RULES.values.map { it.text }.toSet().size)
    }

    @Test fun `who does it, before the verb or after it in a question, and the person's rule`() {
        assertEquals("The lid", word("The lid keeps the rain out.", "present-simple", "The lid keep the rain out."))
        assertEquals("Water", word("Yes! Water always flows downhill.", "present-simple", "Yes! Water always flow downhill."))
        assertEquals("nobody", word("So nobody takes them home!", "present-simple", "So nobody take them home!"))
        assertEquals("the young trees", word("Because the young trees need a post in the wind.", "present-simple", "Because the young trees needs a post in the wind."))
        assertEquals("My favourite place", word("My favourite place is the lake.", "to-be", "My favourite place are the lake."))
        assertEquals("All three", word("There. All three are burning!", "to-be", "There. All three is burning!"))
        // a question: who comes after the verb
        assertEquals("the matches", word("Yes! Where are the matches?", "to-be", "Yes! Where is the matches?"))
        assertEquals("she", word("How many needles does she want?", "questions-do", "How many needles do she want?"))
        assertEquals("the bees", word("Why do the bees go up there?", "questions-do", "Why does the bees go up there?"))
        assertEquals("shears", word("What are shears for, Mr Dixon?", "to-be", "What is shears for, Mr Dixon?"))
        // the question word itself does it
        assertEquals("Who", word("Who sleeps in it now, Mrs Birkett?", "questions-do", "Who sleep in it now, Mrs Birkett?"))
        // which one is the best: which, or the best? nothing said
        assertNull(hint("Which one is the best?", "to-be", "Which one are the best?").trigger)
        val t = TurnHints.text(hint("The lid keeps the rain out.", "present-simple", "The lid keep the rain out."), page)
        assertEquals("kdo: «The lid»", t.lines[0])
        assertTrue(t.lines[1], t.lines[1].startsWith("Who? Navade in dejstva"))
    }

    @Test fun `a name at a sentence's start is who does it, with the cast`() {
        // Wilf alone at the start: a word at the clause's start is taken for its subject anyway
        assertEquals("Wilf", word("Wilf is the winner!", "to-be", "Wilf are the winner!"))
        assertEquals("Sooty", word("Sooty lives on the fell with her mum.", "present-simple", "Sooty live on the fell with her mum.", cast = listOf("Sooty")))
        // a title with the name
        assertEquals("Mrs Hartley", word("Yes, Mrs Hartley has got the key.", "to-have", "Yes, Mrs Hartley have got the key.", cast = listOf("Mrs Hartley")))
    }

    @Test fun `the helper before a verb, the participle after have, the -ing form after be`() {
        assertEquals("Can", word("Can I keep the key, Auntie Edna?", "can", "Can I keeps the key, Auntie Edna?"))
        assertEquals("Can", word("Can midges bite through a coat?", "can", "Can midges biting through a coat?"))
        assertEquals("Did", word("Did the thorns hurt?", "past-simple", "Did the thorns hurted?"))
        assertEquals("do", word("Where do they go in the winter?", "questions-do", "Where do they goes in the winter?"))
        assertEquals("I'll", word("I'll dig the stones out of the ground.", "future", "I'll digs the stones out of the ground."))
        assertEquals("to", word("Because the king wanted to catch him.", "past-simple", "Because the king wanted to catched him."))
        assertEquals("za «Did»: osnovna oblika", line(hint("Did you win a prize last year?", "past-simple", "Did you won a prize last year?"))[0])
        assertEquals("I've", word("No, I've never planted a tree.", "present-perfect", "No, I've never plant a tree."))
        assertEquals("It's", word("It's been dry since then. It's perfect.", "present-perfect", "It's be dry since then. It's perfect."))
        assertEquals("is", word("The wind is pulling it!", "present-continuous", "The wind is pull it!"))
        assertEquals("is", word("What is it doing?", "present-continuous", "What is it do?"))
        assertEquals("What's", word("Yes, please! What's the boat doing?", "present-continuous", "Yes, please! What's the boat do?"))
    }

    @Test fun `be before the -ing form, a feeling with be, a verb of its own with do, have got`() {
        assertEquals("going", word("I'm going to the left.", "present-continuous", "I going to the left."))
        assertEquals("drinking", word("Are they drinking at the ford?", "present-continuous", "Do they drinking at the ford?"))
        assertEquals("running", word("Look! The water's running again!", "present-continuous", "Look! The water running again!"))
        assertEquals("ob «cold»: angleščina to pove z be", line(hint("Good morning, Mrs Hartley! Are you cold?", "to-be", "Good morning, Mrs Hartley! Have you cold?"))[0])
        assertEquals("old", word("How old is it?", "to-be", "How old does it?"))
        assertEquals("tired", word("I'm tired, but happy!", "to-be", "I tired, but happy!"))
        assertEquals("very heavy", word("It's very heavy!", "to-be", "It very heavy!"))
        assertEquals("a great party", word("Yes, it's a great party!", "to-be", "Yes, it a great party!"))
        assertEquals("know", word("What is it? I don't know it.", "questions-do", "What is it? I not know it."))
        assertEquals("bring", word("Why do you bring rushes?", "questions-do", "Why are you bring rushes?"))
        assertEquals("mean", word("That's lovely. What does hush mean?", "questions-do", "That's lovely. What is hush mean?"))
        assertEquals("got", word("No, I haven't got one. Have you?", "to-have", "No, I don't got one. Have you?"))
    }

    @Test fun `what there is, a or an, a number, whose it is, a no before any, much or many`() {
        assertEquals("a lot of lakes", word("There are a lot of lakes here!", "there-is-are", "There is a lot of lakes here!"))
        assertEquals("How many seeds", word("How many seeds are there in the bag?", "there-is-are", "How many seeds is there in the bag?"))
        assertEquals("any head torches", word("Are there any head torches in the box?", "there-is-are", "Is there any head torches in the box?"))
        assertEquals("the seeds", word("Here are the seeds, Arthur.", "to-be", "Here is the seeds, Arthur."))
        assertNull(hint("No, there aren't any.", "there-is-are", "No, there isn't any.").trigger)
        assertEquals("ob «big»: odloči njen prvi glas", line(hint("I've got a big basket.", "articles", "I've got an big basket."))[0])
        assertEquals("twelve", word("It'll need twelve gears, big and small.", "plurals", "It'll need twelve gear, big and small."))
        assertEquals("six", word("We'll need six oak beams.", "plurals", "We'll need six oak beam."))
        assertEquals("two hundred", word("The shepherd has got two hundred sheep.", "plurals", "The shepherd has got two hundred sheepes."))
        assertEquals("a lot of", word("Wow! That's a lot of fells.", "plurals", "Wow! That's a lot of fell."))
        assertEquals("every", word("Wow! I can see every fell!", "plurals", "Wow! I can see every fells!"))
        assertEquals("a", word("Is he a real frog?", "articles", "Is he a real frogs?"))
        // o'clock never takes the plural: no line says more than one
        assertNull(hint("I go to bed at ten o'clock.", "plurals", "I go to bed at ten o'clocks.").trigger)
        assertEquals("shears", word("These shears are very old!", "plurals", "This shears are very old!"))
        assertEquals("sheep", word("So it's your uncle's sheep!", "possessive-s", "So it's your uncle sheep!"))
        assertEquals("turn", word("Whose turn is it now?", "possessive-s", "Who turn is it now?"))
        assertEquals("It's", word("It's yours, Mr Tyson!", "possessive-s", "It's your, Mr Tyson!"))
        assertEquals("for", word("No, Alfie. It's too sharp for you.", "possessive-s", "No, Alfie. It's too sharp for your."))
        assertEquals(Trigger.Negation("haven't", 2), hint("No, we haven't got any cement.", "some-any", "No, we haven't got some cement.").trigger)
        assertEquals("people", word("How many people carry it on the fell?", "some-any", "How much people carry it on the fell?"))
    }

    @Test fun `one no, no have, the place, going there, since or for, a word of time`() {
        assertEquals("never", word("Wow! I have never seen a raven.", "present-perfect", "Wow! I haven't never seen a raven."))
        assertEquals("I", word("I saw her by the wall this morning.", "past-simple", "I seen her by the wall this morning."))
        assertEquals("been", word("How long has it been there?", "present-perfect", "How long is it been there?"))
        assertEquals("the attic", word("Here? In the attic? OK, but quietly!", "prepositions-place", "Here? On the attic? OK, but quietly!"))
        assertEquals("London", word("In a museum in London.", "prepositions-place", "In a museum at London."))
        assertEquals("go", word("Quick, let's go to the hut!", "prepositions-place", "Quick, let's go at the hut!"))
        assertEquals("next", word("Shall I put them next to the barrel?", "prepositions-place", "Shall I put them next of the barrel?"))
        // above against under is a meaning: nothing said
        assertNull(hint("In a cave above the lake.", "prepositions-place", "In a cave under the lake.").trigger)
        assertEquals("Monday", word("Yes! I've been here since Monday.", "present-perfect", "Yes! I've been here from Monday."))
        assertEquals("ten years", word("I've lived here for ten years.", "for-since", "I've lived here since ten years."))
        assertEquals("Monday", word("We met on Monday.", "time-dates", "We met in Monday."))
        assertEquals(Trigger.Cue("yesterday", 3, Trigger.Cue.Kind.PAST), hint("I walked home yesterday.", "past-simple", "I walk home yesterday.").trigger)
        // walk against walked is no choice of who: the habit
        assertEquals("every day", word("We walk to school every day.", "present-simple", "We walked to school every day."))
    }

    @Test fun `if in either half, than, the -ing after enjoy, said, who after a noun, a question tag`() {
        assertEquals("If", word("If I had a boat, I'd row across.", "second-conditional", "If I have a boat, I'd row across."))
        assertEquals("«If» v drugi polovici: ta polovica pove, kaj sledi", line(hint("If I had a boat, I'd row across.", "second-conditional", "If I had a boat, I'll row across."))[0])
        assertEquals("than", word("Jack is taller than Sam.", "comparatives", "Jack is tallest than Sam."))
        assertEquals("the", word("It's the oldest house in the village.", "comparatives", "It's the older house in the village."))
        assertEquals("enjoy", word("I enjoy walking on the fells.", "verb-patterns", "I enjoy walk on the fells."))
        assertEquals("at", word("She's good at singing.", "verb-patterns", "She's good at sing."))
        assertEquals("said", word("She said she was tired.", "reported-speech", "She said she is tired."))
        assertEquals("the man", word("He's the man who lives by the beck.", "relative-clauses", "He's the man which lives by the beck."))
        assertEquals("It's", word("It's cold, isn't it?", "question-tags", "It's cold, is it?"))
        assertEquals("I", word("I cut myself.", "reflexive-pronouns", "I cut himself."))
        // (from Lakeland's B1 and B2 dialogs) that the oak is no noun phrase; told later moves the tense, not who
        assertEquals("the oak", word("Is that the oak which Harry climbs every day?", "relative-clauses", "Is that the oak who Harry climbs every day?"))
        assertNull(hint("He told me his bowl was closer.", "reported-speech", "He told me my bowl was closer.").trigger)
        assertEquals("months", word("You've been working on it for months!", "for-since", "You've been working on it since months!"))
        assertEquals("I was a child", word("I haven't blown bubbles since I was a child.", "for-since", "I haven't blown bubbles for I was a child."))
    }

    @Test fun `the models show no form of the turn and one with the trigger's word comes first`() {
        val h = hint("Can I keep the key, Auntie Edna?", "can", "Can I keeps the key, Auntie Edna?")
        assertTrue(h.models.toString(), h.models.size == 2 && h.models.none { m -> h.forms.any { TurnHints.shows(m, it, stem = true) } })
        // after «can»: a model with can first
        assertTrue(h.models[0], "can" in Forms.words(h.models[0]))
    }

    @Test fun `a German base says it in German, the English as it is`() {
        L10n.pair = LangPair(Lang.EN, Lang.DE)
        val t = TurnHints.text(hint("I've got a big basket.", "articles", "I've got an big basket."), page)
        assertEquals("bei «big»: sein erster Laut entscheidet", t.lines[0])
        val said = HintSpeech.lines(hint("I've got a big basket.", "articles", "I've got an big basket."))
        assertTrue(said.toString(), said.isNotEmpty() && said.none { "sound" in it })
    }

    @Test fun `a Slovene turn keeps its own rules, an English page the Slovene table doesn't read`() {
        val sl = Grammar.bundled("sl")
        val eggs = DialogLine(choices = listOf(DialogChoice("Deset jajc, prosim.", ok = true), wrong("Deset jajce, prosim.", "stevila-samostalniki")))
        assertTrue(TurnHints.of(eggs, "sl") { id -> sl.firstOrNull { it.id == id } }!!.trigger is Trigger.Count)
        val h = TurnHints.of(eggs, "en") { id -> sl.firstOrNull { it.id == id } }!!
        assertNull(h.rule)
        assertNull(h.trigger)
    }

    // --- over the curated turns -------------------------------------------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true }

    /** Lakeland's people, as the app passes them (SceneDialog's rememberHint): its villagers' names and its scenes' people's. */
    private val cast: List<String> by lazy {
        val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory }
        val lakeland = File(root, "cultures/lakeland")
        val villagers = File(lakeland, "villagers").listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { (json.parseToJsonElement(it.readText()) as? JsonObject)?.let { o -> str(o, "name") } }
        val people = File(lakeland, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().flatMap { parseScene(it.readText()).people.map { p -> p.name } }
        (villagers + people).distinct()
    }

    /** Every curated English turn: Lakeland's scenes (counting dialogs at a number of each category), steps, stories, parties, arrivals. */
    private fun curatedTurns(): List<Pair<String, DialogLine>> {
        val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory }
        val lakeland = File(root, "cultures/lakeland")
        val scenes = File(lakeland, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().map { parseScene(it.readText()) }.flatMap { s ->
            s.dialogs.flatMap { d ->
                val played = if (d.count != null) listOf(1, 2, 3, 12).map { Counts.render(d, it, "en") } else listOf(d)
                played.flatMap { p -> p.lines.filter { it.choices.size >= 2 }.map { "${s.id}/${p.id}" to it } }
            }
        }
        val others = lakeland.walk().filter { it.isFile && it.extension == "json" && "scenes" !in it.path.split(File.separatorChar) }.flatMap { f ->
            val raw = si.lanisce.lani.l10n.Learner.current.renderJson(f.readText())
            turnsIn(json.parseToJsonElement(raw)).map { "${f.name}" to it }
        }
        return scenes + others
    }

    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.contentOrNull

    private fun turnsIn(e: JsonElement): List<DialogLine> = when (e) {
        is JsonObject -> {
            val choices = (e["choices"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            val own = if (choices.size >= 2 && choices.all { str(it, "en") != null && "{" !in str(it, "en")!! }) listOf(
                DialogLine(grammar = str(e, "grammar"), choices = choices.map { c ->
                    DialogChoice(str(c, "en")!!, str(c, "sl").orEmpty(), ok = (c["ok"] as? JsonPrimitive)?.booleanOrNull == true, grammar = str(c, "grammar"), tap = str(c, "tap"))
                }),
            ) else emptyList()
            own + e.values.flatMap { turnsIn(it) }
        }
        is JsonArray -> e.flatMap { turnsIn(it) }
        else -> emptyList()
    }

    @Test fun `over the curated English turns the hint never gives the answer away, nearly all say a rule, and many name a trigger`() {
        for (base in listOf(Lang.SL, Lang.DE, Lang.IT)) {
            L10n.pair = LangPair(Lang.EN, base)
            var turns = 0
            var hints = 0
            var said = 0
            var formTurns = 0
            var triggered = 0
            val report = mutableListOf<String>()
            val byLine = sortedMapOf<String, Int>()
            for ((where, line) in curatedTurns()) {
                turns++
                val h = TurnHints.of(line, "en", cast, page) ?: continue
                hints++
                val t = TurnHints.text(h, page)
                if (t.lines.isNotEmpty()) said++
                for (s in t.lines + t.rows + t.links.mapNotNull { it.second }) assertTrue("$where: «$s» gives away ${h.right}", TurnHints.fair(s, h))
                for (m in h.models) assertFalse("$where: the model «$m» shows one of ${h.forms}", h.forms.any { TurnHints.shows(m, it, stem = true) })
                for (s in HintSpeech.lines(h)) assertTrue("$where: the 🔊 «$s» gives away ${h.right}", TurnHints.fair(HintSpeech.plain(s), h))
                val right = line.choices.indexOfFirst { it.ok }.takeIf { line.choices.count { c -> c.ok } == 1 } ?: continue
                if (Forms.turn(line, right) == null) continue
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
            if (base == Lang.SL) File("build/reports/hints").apply { mkdirs() }.resolve("en.txt").writeText(report.sorted().joinToString("\n") + "\n")
            println("English (${base.code}): $turns turns, $hints with a hint, $said with a rule or a trigger; $triggered of $formTurns form turns name what decides: $byLine")
            assertTrue("turns with a hint: $hints", hints > 700)
            // (Lakeland has many turns of two right choices or of another length, "I'm fine, thank you." and "Very well,
            // thank you."; the whole of their sentences is the answer, so a card that names I'm, can or don't is left out
            // there, and the chip shows its link alone)
            assertTrue("$said of $hints hints say more than the link", said * 100 >= hints * 90)
            assertTrue("$triggered of $formTurns form turns name what decides their form", triggered * 100 >= formTurns * 60)
        }
    }

    @Test fun `what the cast adds on the curated English turns`() {
        val changed = curatedTurns().mapNotNull { (_, line) ->
            val h = TurnHints.of(line, "en", cast, page) ?: return@mapNotNull null
            val before = TurnHints.of(line, "en", page)?.trigger
            if (h.trigger != before) "${line.choices.firstOrNull { it.ok }?.sl} | ${before?.word} → ${h.trigger?.word}" else null
        }
        File("build/reports/hints").apply { mkdirs() }.resolve("en-cast.txt").writeText(changed.sorted().joinToString("\n") + "\n")
        println("English: the cast changes what decides at ${changed.size} turns")
    }

    @Test fun `over the curated English turns the 🔊 says something short, the English marked`() {
        var hints = 0
        var spoken = 0
        var longest = 0.0
        for ((_, line) in curatedTurns()) {
            val h = TurnHints.of(line, "en", cast, page) ?: continue
            hints++
            val pieces = HintSpeech.of(h)
            if (pieces.isNotEmpty()) spoken++
            assertNotNull(pieces)
            longest = maxOf(longest, HintSpeech.seconds(pieces))
        }
        println("English 🔊: $spoken of $hints say something, the longest about ${"%.0f".format(longest)} s")
        assertTrue("$spoken of $hints", spoken * 100 >= hints * 95)
        assertTrue("the longest takes $longest s", longest < 30)
    }
}
