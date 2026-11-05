package si.lanisce.lani.game.scene

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.l10n.Learner
import si.lanisce.lani.ui.scene.ChoiceDiff
import si.lanisce.lani.ui.scene.DialogRun
import si.lanisce.lani.ui.words.WordTokens
import java.io.File

/**
 * Every wrong choice of every scene (Jan's in companion/scenes, the culture packs' own; every dialog, the happenings'
 * variants too), of every talk with someone at a spot of the landscape (the culture packs' world.json `spots`: the
 * charcoal burner by his kopa; the test pack's too), of every day of a spot's cycle (world.json `spots.<id>.cycle`: its
 * meeting, greetings, stages, nights and breakout, each by level) and of every evening story (the culture packs' stories/,
 * each telling at each level and chapter) has the person's reaction to it (companion/SCENES.md, "Reactions and more than one right
 * answer"): in every language its choice has, never saying the right form, with no weather; and a turn with two right
 * choices gives each its own reply, with the same cues. A counting dialog (companion/SCENES.md, "Numbers") is checked as
 * the app says it, at every number it can have up to 30 and round the hundreds.
 */
class DialogReactionsTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "scenes").isDirectory && File(it, "cultures").isDirectory }
    private val files = (File(companion, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().toList() +
        File(companion, "cultures").listFiles().orEmpty().flatMap { File(it, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().toList() })
        .sortedBy { it.path }
    private val langs = listOf("sl", "en", "it", "de")

    /** The learners a text is said to here: a man and a woman ({learner}, {m:…|f:…}: l10n/Learner.kt). */
    private val learners = listOf(Learner.of("Jan"), Learner.of("Ana", "female"))

    private fun JsonElement?.obj() = this as? JsonObject

    /**
     * The talks of the people at the culture packs' spots (world.json `spots.<id>.keeper`) and the test pack's, each as a
     * scene of its own: its dialogs the keeper's levels, he the person of each, its review the spot's, drawn in the woods.
     */
    private val talks: List<JsonObject> by lazy {
        (File(companion, "cultures").listFiles().orEmpty().toList() + File(companion, "android/app/src/test/resources/cultures-test").listFiles().orEmpty().toList())
            .filter { File(it, "world.json").isFile }.sortedBy { it.path }.flatMap { dir ->
                val world = json.parseToJsonElement(File(dir, "world.json").readText()).jsonObject
                val lang = json.parseToJsonElement(File(dir, "culture.json").readText()).jsonObject.str("language") ?: "sl"
                world["spots"].obj().orEmpty().mapNotNull { (spot, v) ->
                    val k = v.obj()?.get("keeper").obj() ?: return@mapNotNull null
                    val levels = k["levels"].obj() ?: return@mapNotNull null
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive("${dir.name}/$spot"), "language" to JsonPrimitive(lang), "art" to JsonPrimitive("forest"),
                            "review" to (v.obj()?.get("review") ?: JsonPrimitive("")),
                            "dialogs" to JsonArray(levels.map { (lv, t) -> JsonObject(mapOf("id" to JsonPrimitive(lv), "lines" to t.obj()!!.getValue("lines"))) }),
                            "happenings" to JsonArray(levels.keys.map { lv -> JsonObject(mapOf("dialog" to JsonPrimitive(lv), "who" to k.getValue("id"))) }),
                        ),
                    )
                }
            }
    }

    /** Every `lines` under [e] in a cycle, with where it is: a level's talk ("stages[0].talks[1].levels.B2"), whatever the part. */
    private fun linesIn(e: JsonElement?, at: String): List<Pair<String, JsonElement>> = when (e) {
        is JsonObject -> e.flatMap { (k, v) -> if (k == "lines") listOf(at to v) else linesIn(v, if (at.isEmpty()) k else "$at.$k") }
        is JsonArray -> e.flatMapIndexed { i, v -> linesIn(v, "$at[$i]") }
        else -> emptyList()
    }

    /**
     * The days of the culture packs' spots and the test pack's (world.json `spots.<id>.cycle`), each spot's as a scene of its
     * own: its dialogs every talk of the cycle by level, wherever in it (companion/VILLAGERS.md, "The pile over the days"),
     * the keeper the person of each, in the woods as his meeting is. [Keepers.cycleLines] says them one after another, so
     * each is held to the rules alone.
     */
    private val cycles: List<JsonObject> by lazy {
        (File(companion, "cultures").listFiles().orEmpty().toList() + File(companion, "android/app/src/test/resources/cultures-test").listFiles().orEmpty().toList())
            .filter { File(it, "world.json").isFile }.sortedBy { it.path }.flatMap { dir ->
                val world = json.parseToJsonElement(File(dir, "world.json").readText()).jsonObject
                val lang = json.parseToJsonElement(File(dir, "culture.json").readText()).jsonObject.str("language") ?: "sl"
                world["spots"].obj().orEmpty().mapNotNull { (spot, v) ->
                    val cycle = v.obj()?.get("cycle").obj() ?: return@mapNotNull null
                    val who = v.obj()?.get("keeper").obj()?.getValue("id") ?: return@mapNotNull null
                    val talks = linesIn(cycle, "")
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive("${dir.name}/$spot/cycle"), "language" to JsonPrimitive(lang), "art" to JsonPrimitive("forest"),
                            "dialogs" to JsonArray(talks.map { (at, lines) -> JsonObject(mapOf("id" to JsonPrimitive(at), "lines" to lines)) }),
                            "happenings" to JsonArray(talks.map { (at, _) -> JsonObject(mapOf("dialog" to JsonPrimitive(at), "who" to who)) }),
                        ),
                    )
                }
            }
    }

    /**
     * The evening stories of the culture packs (their stories/), each as a scene of its own: its dialogs the tellings (a
     * level, or a chapter's level: "2/A1"), the teller the person of each, told by the campfire (its effects).
     */
    private val stories: List<JsonObject> by lazy {
        File(companion, "cultures").listFiles().orEmpty().sortedBy { it.path }.flatMap { dir ->
            File(dir, "stories").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.path }.map { f ->
                val s = json.parseToJsonElement(f.readText()).jsonObject
                val chapters = s["chapters"] as? JsonArray
                val tellings = chapters?.flatMapIndexed { ci, c -> c.obj()!!["levels"].obj().orEmpty().map { (lv, t) -> "${ci + 1}/$lv" to t } }
                    ?: s["levels"].obj().orEmpty().toList()
                JsonObject(
                    mapOf(
                        "id" to JsonPrimitive("${dir.name}/stories/${s.str("id")}"), "language" to JsonPrimitive(s.str("language") ?: "sl"), "art" to JsonPrimitive("campfire"),
                        "dialogs" to JsonArray(tellings.map { (at, t) -> JsonObject(mapOf("id" to JsonPrimitive(at), "lines" to t.obj()!!.getValue("lines"))) }),
                        "happenings" to JsonArray(tellings.map { (at, _) -> JsonObject(mapOf("dialog" to JsonPrimitive(at), "who" to s.getValue("teller"))) }),
                    ),
                )
            }
        }
    }
    private val sceneFiles by lazy { files.map { json.parseToJsonElement(it.readText()).jsonObject } }
    private val scenes by lazy { sceneFiles + talks + cycles + stories }
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.list(k: String) = (this[k] as? JsonArray).orEmpty().mapNotNull { it.obj() }
    private val JsonObject.ok get() = (this["ok"] as? JsonPrimitive)?.booleanOrNull == true

    /** Every dialog of [s]: its dialogs and its variants. */
    private fun dialogsOf(s: JsonObject) = s.list("dialogs") + s.list("variants")

    /** The numbers a counting dialog is checked at: every one it can have up to 30, and round the hundreds. */
    private fun samples(count: JsonObject): List<Int> {
        val lo = (count["min"] as? JsonPrimitive)?.content?.toInt() ?: 1
        val hi = maxOf(lo, (count["max"] as? JsonPrimitive)?.content?.toInt() ?: 999)
        return ((lo..minOf(hi, 30)) + listOf(99, 100, 101, 102, 103, 104, 105, 111, 121, 201, 202).filter { it in lo..hi } + listOf(lo, hi)).distinct().sorted()
    }

    /**
     * [d] (a file's dialog) as the app says it with the number [n] ([Counts.text]): every text in every language, what is
     * said in the scene's [lang] in words, the translations in figures, a wrong choice's forms wrong, a why as the right
     * one; the wrong choices that come out as a right one (or as another wrong one) left out, as [Counts.render] does.
     */
    private fun rendered(d: JsonObject, n: Int, lang: String): JsonObject {
        val c = json.decodeFromJsonElement(DialogCount.serializer(), d.getValue("count"))
        val arity = if (lang == "sl") 4 else 2
        fun texts(o: JsonObject, wrong: Int?): JsonObject = JsonObject(o.mapValues { (k, v) ->
            val t = (v as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (t == null || k !in langs) v else JsonPrimitive(Counts.text(t, n, arity, wrong, if (k == lang) lang else null, c))
        })
        fun why(w: JsonElement?): JsonElement? = when (w) {
            is JsonPrimitive -> JsonPrimitive(Counts.text(w.content, n, arity, null, lang, c))
            is JsonObject -> JsonObject(w.mapValues { (_, v) -> JsonPrimitive(Counts.text((v as JsonPrimitive).content, n, arity, null, lang, c)) })
            else -> w
        }
        val lines = d.list("lines").map { l ->
            val choices = l.list("choices").map { ch ->
                val wrong = (ch["wrong_form"] as? JsonPrimitive)?.content?.toInt()?.takeIf { !ch.ok }
                val out = LinkedHashMap<String, JsonElement>(texts(ch, wrong))
                ch["reply"].obj()?.let { out["reply"] = texts(it, null) }
                why(ch["why"])?.let { out["why"] = it }
                JsonObject(out)
            }
            val seen = choices.filter { it.ok }.mapNotNull { it.str(lang) }.toHashSet()
            val kept = choices.filter { it.ok || seen.add(it.str(lang).orEmpty()) }
            JsonObject(texts(l, null) + ("choices" to JsonArray(kept)))
        }
        return JsonObject(d + ("lines" to JsonArray(lines)))
    }

    /** Each learner's turn of [s]: where it is ("scene/dialog/line", a counting dialog's at each number) and its choices. */
    private fun turns(s: JsonObject): List<Pair<String, List<JsonObject>>> = dialogsOf(s).flatMap { d ->
        val lang = s.str("language") ?: "sl"
        val sayings = d["count"].obj()?.let { c -> samples(c).map { n -> "#$n" to rendered(d, n, lang) } } ?: listOf("" to d)
        sayings.flatMap { (at, said) ->
            said.list("lines").mapIndexedNotNull { i, l -> l.list("choices").takeIf { it.isNotEmpty() }?.let { "${s.str("id")}/${d.str("id")}$at/$i" to it } }
        }
    }

    private fun words(t: String) = WordTokens.of(t).map { it.text.lowercase() }

    /** Whether [text] says [phrase] (its words, in a row). */
    private fun says(text: String, phrase: List<String>) = phrase.isNotEmpty() && words(text).windowed(phrase.size).any { it == phrase }

    /**
     * The runs of [right]'s words that [wrong] lacks, where the two differ in a word or two (a form: "vam" beside "vas");
     * none where they say different things ([ChoiceDiff] marks nothing then).
     */
    private fun apart(right: String, wrong: String): List<List<String>> {
        val marked = ChoiceDiff.marks(listOf(right, wrong)).first().toSet()
        val runs = ArrayList<List<String>>()
        var run = ArrayList<String>()
        for (t in WordTokens.of(right)) {
            if (t in marked) run += t.text.lowercase() else if (run.isNotEmpty()) { runs += run; run = ArrayList() }
        }
        if (run.isNotEmpty()) runs += run
        return runs
    }

    /** Whether every saying of [run] in [said] stands in a question ("Od spodaj? Si videl kje spodaj kakšen ogenj?"). */
    private fun asked(said: String, run: List<String>): Boolean {
        val holding = Regex("[^.!?…]+[.!?…]*").findAll(said).map { it.value.trim() }.filter { says(it, run) }.toList()
        return holding.isNotEmpty() && holding.all { it.endsWith("?") }
    }

    /** Whether two words apart from each other trade places between [r] and [w], so the sentence says the other thing. */
    private fun traded(r: List<String>, w: List<String>): Boolean {
        val differ = r.indices.filter { r[it] != w.getOrNull(it) }
        return r.size == w.size && differ.size == 2 && differ[1] - differ[0] > 1 && r[differ[0]] == w[differ[1]] && r[differ[1]] == w[differ[0]]
    }

    /** [run] with the word before it and with the word after it, wherever [r] says it. */
    private fun placed(r: List<String>, run: List<String>): List<List<String>> = r.indices.filter { r.drop(it).take(run.size) == run }.flatMap { i ->
        listOfNotNull(r.getOrNull(i - 1)?.let { listOf(it) + run }, r.getOrNull(i + run.size)?.let { run + it })
    }

    /**
     * Whether [said] says [run] only as the learner did: an echo of [wrong] ("Als waren Sie?"), not the answer. [ChoiceDiff]
     * marks a moved word as what sets [right] apart, though the wrong choice holds it too, so here the check asks what such a
     * word could give away. Not an echo, whatever else:
     * - a word [wrong] lacks ("Sneg? Saj ni zima. To je dež."), or holds fewer of while it has another word instead ("Bi ali
     *   bo?" beside "…, bi kopa" and "…, bo kopa"): the form is the answer;
     * - a word said beside what [right] puts next to it, as [wrong] doesn't have them ("Geholfen hat es" beside "geholfen hat"
     *   and "hat geholfen"): the place is the answer;
     * - one of two words that trade places, so that the sentence says the other thing ("Tone hoče drobno."), unless it's
     *   only asked back ("Od spodaj?"): a statement tells who gets which.
     * It reads forms, not meanings: a statement dressed as a question passes, and that is the reader's to see.
     */
    private fun echoes(said: String, run: List<String>, right: String, wrong: String): Boolean {
        val r = words(right)
        val w = words(wrong)
        if (!says(wrong, run)) return false
        val replaced = run.any { x -> r.count { it == x } > w.count { it == x } } && w.toSet().any { x -> w.count { it == x } > r.count { it == x } }
        return when {
            replaced -> false
            traded(r, w) -> asked(said, run)
            else -> placed(r, run).none { says(said, it) && !says(wrong, it) }
        }
    }

    /** What of [right] (the whole, or what sets it apart from [wrong]) [said] gives away, and in a translation [meant]. */
    private fun giveaways(right: String, wrong: String, said: String, meant: Map<String, String> = emptyMap()): List<String> = buildList {
        if (says(said, words(right))) add("says the right choice: «$said»")
        for (run in apart(right, wrong)) {
            val told = run.joinToString(" ")
            if (says(said, run) && !echoes(said, run, right, wrong)) add("says «$told»: «$said»")
            // in a translation too, where it could be read as the answer ("vam", not "je")
            for ((l, t) in meant) if (told.length >= 3 && says(t, run) && !echoes(t, run, right, wrong)) add(".$l says «$told»: «$t»")
        }
    }

    @Test fun `every scene of Jan's and of the culture packs is here`() {
        // the Slovene village's, the culture packs' before, Kärnten's places (places-kaernten), the Lake District's (places-lakeland, and
        // lakeland-projects-b's beck, tarn and six projects), Kärnten's wood and seven projects' places (kaernten-projects-a), its six
        // projects' places, the stream bank and the pond (kaernten-projects-b), and the Lake District's wood and seven projects'
        // places (lakeland-projects-a)
        assertEquals(31 + 39 + 15 + 23 + 8 /* kaernten-projects-a */ + 8 /* kaernten-projects-b */ + 8 /* lakeland-projects-a */, files.size)
        // and the talks of the charcoal burner at every culture's pile, and the test pack's
        assertEquals(setOf("primorska/kopa", "friuli/kopa", "kaernten/kopa", "lakeland/kopa", "tinyland/kopa"), talks.map { it.str("id") }.toSet())
        // and the cycle of every culture's pile, by what it holds: a talk at each of the levels, the turns among them
        assertEquals(setOf("primorska", "friuli", "kaernten", "lakeland"), cycles.map { it.str("id")!!.substringBefore('/') }.toSet() - "tinyland")
        assertTrue(cycles.joinToString { "${it.str("id")}: ${it.list("dialogs").size} talks, ${turns(it).size} turns" }, cycles.all { it.list("dialogs").size >= 100 && turns(it).isNotEmpty() })
        // and the evening stories of every culture, each with its tellings (new ones are held to the same)
        assertEquals(setOf("primorska", "friuli", "kaernten", "lakeland"), stories.map { it.str("id")!!.substringBefore('/') }.toSet())
        assertTrue("${stories.size} stories", stories.size >= 24 && stories.all { it.list("dialogs").isNotEmpty() })
    }

    @Test fun `every wrong choice has the person's reaction, in every language of its choice`() {
        val missing = scenes.flatMap { s ->
            turns(s).flatMap { (at, choices) ->
                choices.withIndex().filter { !it.value.ok }.mapNotNull { (k, c) ->
                    val reply = c["reply"].obj() ?: return@mapNotNull "$at/$k: no reaction"
                    val lacking = langs.filter { c.str(it) != null && reply.str(it).isNullOrBlank() }
                    if (lacking.isEmpty()) null else "$at/$k: no reaction in $lacking"
                }
            }
        }
        assertTrue(missing.joinToString("\n"), missing.isEmpty())
    }

    @Test fun `no reaction says the right choice, nor the words that set it apart`() {
        // said to a man and to a woman: a turn's forms that agree with the learner are the same for each choice
        val told = learners.flatMap { who -> scenes.flatMap { s ->
            val lang = s.str("language") ?: "sl"
            turns(s).flatMap { (at, choices) ->
                val rights = choices.filter { it.ok }.mapNotNull { it.str(lang)?.let(who::render) }
                choices.withIndex().filter { !it.value.ok }.flatMap { (k, c) ->
                    val reply = c["reply"].obj() ?: return@flatMap emptyList()
                    val said = who.render(reply.str(lang).orEmpty())
                    val wrong = who.render(c.str(lang).orEmpty())
                    val meant = (langs - lang).mapNotNull { l -> reply.str(l)?.let { l to who.render(it) } }.toMap()
                    rights.flatMap { right -> giveaways(right, wrong, said, meant).map { "$at/$k $it" } }
                }
            }
        } }.distinct()
        assertTrue(told.joinToString("\n"), told.isEmpty())
    }

    @Test fun `an echo of the learner's words passes, and what gave the answer away never does`() {
        // the nine reactions the piles' cycles once had, each reworded since, and the tenth the rule found: right, wrong, said
        val leaks = listOf(
            Triple("Ali naj jih nesem vsa naenkrat?", "Ali naj ga nesem vsa naenkrat?", "Ga? Koga? Tam jih je cel kup."),
            Triple("Če bi jih naredili preveč, bi kopa dobila preveč zraka, kajne?", "Če bi jih naredili preveč, bo kopa dobila preveč zraka, kajne?", "Bi ali bo? Odloči se, kopa ne čaka."),
            Triple("Zakaj je dež dober?", "Zakaj je sneg dober?", "Sneg? Saj ni zima. To je dež."),
            Triple("Torej debelo oglje prodate trgovcem, drobno pa ostane kovaču.", "Torej drobno oglje prodate trgovcem, debelo pa ostane kovaču.", "Ravno obratno. Tone hoče drobno."),
            Triple("Koliko vam je plačal?", "Koliko vam bo plačal?", "Bo? Saj je že plačal. Tone nikomur nič ne dolguje."),
            Triple("Koliko vam je plačal za oglje?", "Koliko vam bo plačal za oglje?", "Bo? Saj je že plačal. Tone nikomur nič ne dolguje."),
            Triple("Sì, volentieri!", "No, volentieri!", "No, volentieri? Allora sì o no?"),
            Triple("Per il fabbro Bruno?", "Per il gatto Bruno?", "Il gatto? Bruno non è un gatto: è il fabbro!"),
            Triple("Grazie! Che bella capanna!", "Grazie! Che bella barca!", "Una barca? Nel bosco? È la mia capanna!"),
            Triple("Ob das Kreuz geholfen hat, weiß man nicht, aber geschadet hat es sicher nicht.", "Ob das Kreuz hat geholfen, weiß man nicht, aber geschadet hat es sicher nicht.", "Hm. Geholfen hat es, aber der Satz ist ein bisschen durcheinander."),
        )
        for ((right, wrong, said) in leaks) assertTrue("$said: passes", giveaways(right, wrong, said).isNotEmpty())
        // and an echo with the right choice said after it isn't one, nor a word asked back while it's stated too
        assertTrue(giveaways("Wenn der Meiler kalt ist.", "Wenn der Meiler ist kalt.", "Ist kalt? Du meinst: wenn der Meiler kalt ist.").isNotEmpty())
        assertTrue(giveaways("Torej debelo oglje prodate, drobno pa ostane.", "Torej drobno oglje prodate, debelo pa ostane.", "Drobno? Tone hoče drobno.").isNotEmpty())
        // the moved word, asked back as the learner said it; a word of two that trade places; the person turned round
        val echoes = listOf(
            Triple("Was haben Sie gekocht, als Sie das erste Mal allein hier waren?", "Was haben Sie gekocht, als waren Sie das erste Mal allein hier?", "Als waren Sie? Hm, jetzt hab ich den Faden verloren."),
            Triple("How can you tell when it's thick enough?", "How can you tell when it's enough thick?", "Enough thick? It came out back to front, lad. Like my first pit."),
            Triple("Zakaj prižigate od zgoraj, ne od spodaj?", "Zakaj prižigate od spodaj, ne od zgoraj?", "Od spodaj? Si videl kje spodaj kakšen ogenj?"),
            Triple("What do you do with the small pieces?", "What you do with the small pieces?", "What I do? I'm a collier, lad."),
        )
        for ((right, wrong, said) in echoes) assertEquals(said, emptyList<String>(), giveaways(right, wrong, said))
    }

    @Test fun `a reaction passes - no weather, one short line, only its art's effects`() {
        val wrong = scenes.flatMap { s ->
            val lang = s.str("language") ?: "sl"
            val effects = SceneArt.effects.getValue(s.str("art")!!)
            turns(s).flatMap { (at, choices) ->
                choices.withIndex().filter { !it.value.ok }.mapNotNull { (k, c) ->
                    val reply = c["reply"].obj() ?: return@mapNotNull null
                    val fx = reply["fx"].obj()?.keys.orEmpty()
                    when {
                        reply["sky"] != null -> "$at/$k: a reaction with a sky cue"
                        !effects.containsAll(fx) -> "$at/$k: effects $fx, the art has $effects"
                        // as long as it is said to a man or to a woman (not its {m:…|f:…})
                        learners.maxOf { it.render(reply.str(lang).orEmpty()).length } > 80 -> "$at/$k: a long reaction «${reply.str(lang)}»"
                        else -> null
                    }
                }
            }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun `a turn with two right choices gives each its own reply with the same cues, and keeps a wrong one`() {
        var two = 0
        for (s in scenes) {
            val lang = s.str("language") ?: "sl"
            for ((at, choices) in turns(s)) {
                val rights = choices.filter { it.ok }
                assertTrue("$at: one or two right, a wrong one", rights.size in 1..2 && choices.any { !it.ok } && choices.size <= 3)
                if (rights.size < 2) continue
                two++
                val (a, b) = rights.map { it["reply"].obj() }
                assertTrue("$at: each right choice has its reply", a != null && b != null)
                // the spoken hour beside the written one ("Ob pet?", "Ob petih?"): one reply does for both, it says the written form
                // first (SCENES.md, content-fixes-2: the same recast, its clip exists)
                val spokenHour = rights.any { "as people say it" in it.str("en").orEmpty() }
                assertTrue("$at: their own replies", spokenHour || a!!.str(lang) != b!!.str(lang))
                assertTrue("$at: different answers", words(rights[0].str(lang)!!) != words(rights[1].str(lang)!!))
                assertEquals("$at: the same cues, whichever is taken", a!!["sky"] to a["fx"], b!!["sky"] to b["fx"])
            }
        }
        assertTrue("some turns have two right answers ($two)", two >= 20)
    }

    @Test fun `a scene says its reactions are machine-written`() {
        for (s in sceneFiles + talks) {
            val review = s.str("review").orEmpty()
            assertTrue("${s.str("id")}: $review", "reactions" in review && "machine-written" in review)
        }
    }

    @Test fun `played with every wrong choice first, a dialog hears each reaction and still ends`() {
        for (f in files) {
            val s = parseScene(f.readText())
            for (d in s.dialogs) {
                val partner = s.happenings.firstOrNull { it.dialog == d.id || d.id in it.dialogs }?.who ?: continue
                // a counting dialog as the app plays it, at the numbers it can have
                val c = d.count
                if (c == null) play("${s.id}/${d.id}", d, partner)
                else for (n in listOf(c.min, c.min + 1, c.max, 2, 3, 5, 21, 101, 102).filter { it in c.min..c.max }.distinct()) play("${s.id}/${d.id}#$n", Counts.render(d, n, s.language), partner)
            }
        }
        // the talks at the spots, their cycles' and the stories by the fire, as the app plays them: read in the pair, at each
        // level, the teller answering
        for (t in talks + cycles + stories) {
            val who = (t["happenings"] as JsonArray).first().obj()!!.str("who")!!
            for (d in inPair(t)["dialogs"] as JsonArray) play("${t.str("id")}/${d.obj()!!.str("id")}", json.decodeFromJsonElement(Dialog.serializer(), d), who)
        }
    }

    private fun play(at: String, d: Dialog, partner: String) {
        var run = DialogRun.start(d, partner, seed = 7)
        var wrongs = 0
        var steps = 0
        while (run.step != DialogRun.Step.END && steps++ < 60) {
            if (run.step == DialogRun.Step.LISTEN) { run = run.next(); continue }
            val k = run.choices.indices.firstOrNull { !run.choices[it].ok && it !in run.tried }
            if (k == null) { run = run.choose(run.choices.indexOfFirst { it.ok }); continue }
            val before = run.said.size
            run = run.choose(k)
            wrongs++
            assertEquals("$at: the wrong choice and the reaction are said", before + 2, run.said.size)
            assertTrue(run.said[before].wrong && run.said[before].who == null && run.said[before + 1].who == partner)
            assertEquals(DialogRun.Step.CHOOSE, run.step)
        }
        assertEquals(at, DialogRun.Step.END, run.step)
        assertEquals("$at: each wrong choice counts once", wrongs, run.mistakes)
    }
}
