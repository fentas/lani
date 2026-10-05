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
 * charcoal burner by his kopa; the test pack's too) and of every evening story (the culture packs' stories/, each telling
 * at each level and chapter) has the person's reaction to it (companion/SCENES.md, "Reactions and more than one right
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
    private val scenes by lazy { sceneFiles + talks + stories }
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

    @Test fun `every scene of Jan's and of the culture packs is here`() {
        assertEquals(18 + 8, files.size)
        // and the talks of the charcoal burner at every culture's pile, and the test pack's
        assertEquals(setOf("primorska/kopa", "friuli/kopa", "kaernten/kopa", "lakeland/kopa", "tinyland/kopa"), talks.map { it.str("id") }.toSet())
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
                    rights.flatMap { right ->
                        buildList {
                            if (says(said, words(right))) add("$at/$k says the right choice: «$said»")
                            for (run in apart(right, wrong)) {
                                if (says(said, run)) add("$at/$k says «${run.joinToString(" ")}»: «$said»")
                                // in a translation too, where it could be read as the answer ("vam", not "je")
                                for (l in langs - lang) {
                                    val meant = reply.str(l)?.let(who::render) ?: continue
                                    if (run.joinToString(" ").length >= 3 && says(meant, run)) add("$at/$k.$l says «${run.joinToString(" ")}»: «$meant»")
                                }
                            }
                        }
                    }
                }
            }
        } }.distinct()
        assertTrue(told.joinToString("\n"), told.isEmpty())
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
                assertTrue("$at: their own replies", a!!.str(lang) != b!!.str(lang))
                assertTrue("$at: different answers", words(rights[0].str(lang)!!) != words(rights[1].str(lang)!!))
                assertEquals("$at: the same cues, whichever is taken", a["sky"] to a["fx"], b["sky"] to b["fx"])
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
        // the talks at the spots and the stories by the fire, as the app plays them: read in the pair, at each level, the
        // teller answering
        for (t in talks + stories) {
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
