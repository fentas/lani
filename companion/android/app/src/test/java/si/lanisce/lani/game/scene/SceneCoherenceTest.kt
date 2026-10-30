package si.lanisce.lani.game.scene

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Projects
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.io.File

/**
 * The scenes show what their dialogs say happens (docs/plans/05-built/scene-coherence.md; companion/SCENES.md "Effects" and
 * "Project steps"). Every effect a dialog cues is one its picture's painter draws: a scene's dialogs and their variants, a
 * project step played in its scene (over the village only the bell), the evening stories by the fire; a keeper's talk and an
 * age's party have no picture to cue. A project step in a scene that draws its project says on which line its work is done,
 * and the scene shows the project one stage on from there. And no line tells the learner to look at what isn't there: the
 * lint of the lines that promise something visible lists them all for review (build/reports/scene-coherence/), and fails for
 * a look (poglej, guarda, schau, look) with no cue in reach, no thing of its scene named and no review that it's fine
 * (src/test/resources/scene-coherence/looks.json).
 */
class SceneCoherenceTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "scenes").isDirectory && File(it, "cultures").isDirectory }
    private val cultures = File(companion, "cultures")

    private fun obj(f: File): JsonObject = Json.parseToJsonElement(f.readText()).jsonObject
    private fun JsonElement?.list(): List<JsonElement> = (this as? JsonArray).orEmpty()
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.flag(k: String): Boolean = (this[k] as? JsonPrimitive)?.content == "true"
    private fun JsonObject.cues(): Set<String> = this["fx"].obj()?.keys.orEmpty()
    /** A cue on [this] line or reply: effects, weather, stage directions, or a step's work done in the picture. */
    private fun JsonObject.cued(): Boolean = this["fx"] != null || this["sky"] != null || this["act"] != null || flag("done")

    /** Every scene file: the village's, a language's own village's, a culture's. */
    private val sceneFiles: List<File> by lazy {
        val dirs = listOf(File(companion, "scenes")) + File(companion, "scenes").listFiles { f -> f.isDirectory }.orEmpty() +
            cultures.listFiles().orEmpty().map { File(it, "scenes") }
        dirs.filter { it.isDirectory }.flatMap { d -> d.listFiles { f -> f.extension == "json" }.orEmpty().toList() }.sortedBy { it.path }
    }
    private val scenes: Map<String, JsonObject> by lazy { sceneFiles.map(::obj).associateBy { it.str("id")!! } }
    private fun artOf(scene: String?): String? = scene?.let { scenes[it]?.str("art") }
    private fun culturesWith(sub: String): List<File> = cultures.listFiles().orEmpty().filter { File(it, sub).isDirectory }.sortedBy { it.name }
    private fun files(culture: File, sub: String): List<File> = File(culture, sub).listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }

    /** The arts the evening stories of [culture] are told in: its scenes with a storyteller's happening. */
    private fun storyArts(culture: File): Set<String> {
        val own = files(culture, "scenes").map(::obj)
        val lang = obj(File(culture, "culture.json")).str("language") ?: "sl"
        val village = if (lang == "sl") File(companion, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().map(::obj) else emptyList()
        return (own + village).filter { s -> s["happenings"].list().any { it.obj()?.flag("stories") == true } }.mapNotNull { it.str("art") }.toSet()
    }

    /** A dialog's lines at each place a picture plays them: (where, the art whose effects it may cue or null for none, its lines). */
    private data class Telling(val src: String, val art: String?, val language: String, val lines: List<JsonObject>, val scene: String? = null, val fxOk: Set<String> = emptySet())

    private val tellings: List<Telling> by lazy {
        val out = ArrayList<Telling>()
        for (f in sceneFiles) {
            val s = obj(f); val id = s.str("id")!!; val art = s.str("art")!!; val lang = s.str("language") ?: "sl"
            for (d in s["dialogs"].list() + s["variants"].list()) {
                val o = d.obj() ?: continue
                out += Telling("scene $id/${o.str("id")}", art, lang, o["lines"].list().mapNotNull { it.obj() }, id, SceneArt.effects[art].orEmpty().toSet())
            }
        }
        for (c in culturesWith("project-steps")) for (f in files(c, "project-steps")) {
            val p = obj(f); val lang = p.str("language") ?: "sl"
            p["steps"].list().forEachIndexed { k, st ->
                val step = st.obj() ?: return@forEachIndexed
                val scene = step.str("scene"); val art = artOf(scene)
                for ((lv, t) in step["levels"].obj().orEmpty()) out += Telling(
                    "step ${p.str("project")}/$k $lv", art, lang, t.obj()?.get("lines").list().mapNotNull { it.obj() }, scene,
                    if (art != null) SceneArt.effects[art].orEmpty().toSet() else setOf("bell"),
                )
            }
        }
        for (c in culturesWith("stories")) {
            val arts = storyArts(c)
            val fx = arts.map { SceneArt.effects[it].orEmpty().toSet() }.reduceOrNull { a, b -> a intersect b }.orEmpty()
            for (f in files(c, "stories")) {
                val s = obj(f); val lang = s.str("language") ?: "sl"
                fun tellingsOf(levels: JsonObject?, tag: String) {
                    for ((lv, t) in levels.orEmpty()) out += Telling("story ${c.name}/${s.str("id")}$tag $lv", arts.singleOrNull(), lang, t.obj()?.get("lines").list().mapNotNull { it.obj() }, fxOk = fx)
                }
                tellingsOf(s["levels"].obj(), "")
                s["chapters"].list().forEachIndexed { i, ch -> tellingsOf(ch.obj()?.get("levels").obj(), " ch${i + 1}") }
            }
        }
        for (c in culturesWith("celebrations")) for (f in files(c, "celebrations")) {
            val p = obj(f); val lang = p.str("language") ?: "sl"
            for ((lv, t) in p["levels"].obj().orEmpty()) out += Telling("party ${c.name}/${p.str("age")} $lv", null, lang, t.obj()?.get("lines").list().mapNotNull { it.obj() })
        }
        for (c in cultures.listFiles().orEmpty().filter { File(it, "world.json").isFile }.sortedBy { it.name }) {
            val lang = obj(File(c, "culture.json")).str("language") ?: "sl"
            for ((spot, v) in obj(File(c, "world.json"))["spots"].obj().orEmpty()) {
                // every set of lines under the spot: its keeper's talks, the cycle's stages' talks and what they say of it
                fun walk(e: JsonElement?, path: String) {
                    when (e) {
                        is JsonObject -> {
                            val lines = e["lines"]
                            if (lines is JsonArray && lines.all { it is JsonObject }) out += Telling("keeper ${c.name}/$spot$path", null, lang, lines.map { it.jsonObject })
                            else for ((k, x) in e) walk(x, "$path/$k")
                        }
                        is JsonArray -> e.forEachIndexed { i, x -> walk(x, "$path#$i") }
                        else -> {}
                    }
                }
                walk(v, "")
            }
        }
        out
    }

    @Test fun `every effect a dialog cues is one its picture's painter draws`() {
        val bad = ArrayList<String>()
        var cues = 0
        for (t in tellings) t.lines.forEachIndexed { i, l ->
            val named = l.cues() + l["choices"].list().flatMap { c -> c.obj()?.get("reply").obj()?.cues().orEmpty() }
            cues += named.size
            for (e in named - t.fxOk) bad += "${t.src} [$i]: \"$e\" (${t.art ?: "no picture"}: ${t.fxOk.sorted().joinToString().ifEmpty { "no effects" }})"
            // a keeper's talk and an age's party are played over the village: no picture to cue, no stage to move people on
            if (t.src.startsWith("keeper") || t.src.startsWith("party")) {
                val acts = l["act"] != null || l["choices"].list().any { it.obj()?.get("reply").obj()?.get("act") != null }
                if (acts) bad += "${t.src} [$i]: a stage direction, and there's no stage"
            }
        }
        assertTrue(bad.joinToString("\n", prefix = "effects no painter draws there:\n"), bad.isEmpty())
        assertTrue("the cues counted ($cues)", cues > 400)
        // every culture's stories are told by a fire
        for (c in culturesWith("stories")) assertEquals(c.name, setOf("campfire"), storyArts(c))
    }

    /** Steps in a scene that draws their project whose work is there once they're played, with why; the others say where. */
    private val atTheEnd = mapOf(
        "most/5" to "the first cart over the bridge (its cue): the railing comes once the step is done",
        "toplar/2" to "\"Prvi stoji. Še sedem!\": all eight posts stand once the step is done",
        "mestna_ura/6" to "the clock stands at twelve while it strikes noon; it runs once the step is done",
        "vodnjak_na_trgu/2" to "\"Še veliko dela\": the carving goes on, the trough is there once the step is done",
        "vodnjak_na_trgu/5" to "the water's cue runs it from the pipes; the ditch is covered once the step is done",
    )

    @Test fun `a step says on which line its work is done, where its scene draws the project`() {
        val bad = ArrayList<String>()
        var marked = 0
        for (c in culturesWith("project-steps")) for (f in files(c, "project-steps")) {
            val p = obj(f); val project = p.str("project")!!
            p["steps"].list().forEachIndexed { k, st ->
                val step = st.obj() ?: return@forEachIndexed
                val art = artOf(step.str("scene"))
                val draws = art != null && project in SceneArt.projects[art].orEmpty()
                for ((lv, t) in step["levels"].obj().orEmpty()) {
                    val lines = t.obj()?.get("lines").list().mapNotNull { it.obj() }
                    val done = lines.withIndex().filter { it.value.flag("done") }
                    val at = "$project/$k $lv"
                    if (done.size > 1) bad += "$at: done on ${done.size} lines"
                    for ((i, l) in done) {
                        marked++
                        if (l["choices"].list().isNotEmpty()) bad += "$at [$i]: done on the learner's turn"
                        if (!draws) bad += "$at [$i]: done, but its scene (${step.str("scene") ?: "over the village"}) doesn't draw $project"
                    }
                    if (draws && done.isEmpty() && "$project/$k" !in atTheEnd) bad += "$at: played in ${step.str("scene")}, which draws $project, and no line says where its work is done (done)"
                }
            }
        }
        assertTrue(bad.joinToString("\n", prefix = "\n"), bad.isEmpty())
        assertEquals("the steps' done lines (both levels)", 46, marked)
    }

    @Test fun `the step's scene shows its work from the line that says it's done`() {
        val p = obj(File(cultures, "primorska/project-steps/mlin.json"))
        for ((k, st) in p["steps"].list().withIndex()) for ((lv, t) in st.obj()!!["levels"].obj()!!) {
            // as the app reads a step's level (Projects.dialog): a scene's dialog in the village's language, read in Jan's pair
            val raw = JsonObject(mapOf("id" to JsonPrimitive("mlin-$k-$lv"), "lines" to t.obj()!!["lines"]!!))
            val read = inPair(JsonObject(mapOf("language" to JsonPrimitive("sl"), "dialogs" to JsonArray(listOf(raw)))), LangPair(Lang.SL, Lang.EN))
            val d = json.decodeFromJsonElement(Dialog.serializer(), read["dialogs"].list().first())
            val i = d.lines.indexOfFirst { it.done }
            assertTrue("mlin/$k $lv has its done line", i > 0)
            // the line is said once the dialog has come past it (the next line to play is after it)
            assertFalse("mlin/$k $lv: before its done line", Projects.shown(d, i, ended = false))
            assertTrue("mlin/$k $lv: from its done line", Projects.shown(d, i + 1, ended = false))
            val before = SceneWorld(projects = mapOf("mlin" to k))
            assertEquals(k, Projects.stepWorld(before, "mlin", k, shown = false).stage("mlin"))
            assertEquals(k + 1, Projects.stepWorld(before, "mlin", k, shown = true).stage("mlin"))
        }
        // the last step: the wheel turns as Tine says so, the mill grinds (finished) from France's "Mlinski kamen melje žito"
        val last = p["steps"].list()[5].obj()!!["levels"].obj()!!["A1"].obj()!!["lines"].list().map { it.obj()!! }
        assertEquals(1, last.indexOfFirst { "wheel" in it.cues() })
        assertTrue(last.indexOfFirst { it.flag("done") } > 1)
        // a dialog without a done line shows the step's work once played; a world with everything keeps the others finished
        val plain = Dialog("x", listOf(DialogLine(who = "a", sl = "Dober dan.", en = "Hello.")))
        assertFalse(Projects.shown(plain, 1, ended = false))
        assertTrue(Projects.shown(plain, 1, ended = true))
        val all = SceneWorld.ALL.stepping("mlin", 5)
        assertEquals(5, all.stage("mlin"))
        assertTrue(all.finished("most") && all.finished("mlaj"))
    }

    // ------------------------------------------------------------------ the lint of what the lines promise

    /** One thing said: someone's line, the learner's right or wrong choice, a right choice's reply, a wrong one's reaction. */
    private enum class Kind { LINE, RIGHT, WRONG, REPLY, REACTION }

    private data class Said(val src: String, val art: String?, val scene: String?, val language: String, val i: Int, val kind: Kind, val text: String, val en: String, val cue: Boolean, val near: Boolean)

    private fun said(t: Telling): List<Said> {
        val out = ArrayList<Said>()
        var before = false // the unit before carried a cue: what it brought is on as this one is said
        val on = HashMap<String, Float>() // the effects the dialog's cues have on so far (a right reply's included)
        var done = false // a step's work is done in the picture from its done line on
        fun levels(o: JsonObject?) = o?.get("fx").obj()?.forEach { (k, v) -> on[k] = (v as? JsonPrimitive)?.content?.let { c -> c.toFloatOrNull() ?: if (c == "true") 1f else 0f } ?: 0f }
        fun lit() = done || on.values.any { it > 0f }
        for ((i, l) in t.lines.withIndex()) {
            val choices = l["choices"].list().mapNotNull { it.obj() }
            if (choices.isEmpty()) {
                val cue = l.cued()
                levels(l)
                out += Said(t.src, t.art, t.scene, t.language, i, Kind.LINE, l.str(t.language).orEmpty(), l.str("en").orEmpty(), cue, cue || before || lit())
                before = cue
                done = done || l.flag("done")
                continue
            }
            val turn = l.cued()
            levels(l)
            for (c in choices) {
                val ok = c.flag("ok")
                val reply = c["reply"].obj()
                val rc = reply?.cued() == true
                val near = rc || turn || before || lit()
                out += Said(t.src, t.art, t.scene, t.language, i, if (ok) Kind.RIGHT else Kind.WRONG, c.str(t.language).orEmpty(), c.str("en").orEmpty(), rc, near)
                if (reply != null) out += Said(t.src, t.art, t.scene, t.language, i, if (ok) Kind.REPLY else Kind.REACTION, reply.str(t.language).orEmpty(), reply.str("en").orEmpty(), rc, near)
            }
            choices.firstOrNull { it.flag("ok") }?.let { levels(it["reply"].obj()) }
            before = turn || choices.any { it.flag("ok") && it["reply"].obj()?.cued() == true }
        }
        return out
    }

    /** A look the learner is told to take, in the content's language or its English. */
    private val looks = mapOf(
        "sl" to Regex("""(?i)(^|[^\p{L}])(po)?glej(te|va|ta)?([^\p{L}]|$)"""),
        "it" to Regex("""(?i)(^|[^\p{L}])guard(a|ate|ala|alo|ali|ale)([^\p{L}]|$)"""),
        "de" to Regex("""(?i)(^|[^\p{L}])(schau|schaut|sieh|seht|guck|guckt)([^\p{L}]|$)"""),
        "en" to Regex("""(?i)(^|[^\p{L}])look([^\p{L}]|$)(?!\s*(like|for|forward|after|out\b|alike))"""),
    )

    /** Something visible happening or there now (the report only): per language, and the English every line has. */
    private val visible = mapOf(
        "sl" to Regex("""(?i)(^|[^\p{L}])(vrti|vrtijo|teče|tečejo|skače|skačejo|skoči|skočil\p{L}*|gori|gorijo|prižg\p{L}*|ugasn\p{L}*|odpr\p{L}*|pada|padajo|pade|padel\p{L}*|leti|letijo|odletel\p{L}*|zvoni|zvonijo|cin|bim|pije|pijejo|prihaja\p{L}*|pride|pridejo|prišl\p{L}*|domov|odhaja\p{L}*|plava|plavajo|pleše|plešejo|kadi|iskri\p{L}*|sveti|svetijo|prešte\p{L}*|štej\p{L}*|tam\s+(je|so|sta)|tukaj\s+(je|so|sta)|tu\s+(je|so|sta))([^\p{L}]|$)"""),
        "it" to Regex("""(?i)(^|[^\p{L}])(gira|girano|scorre|salta|saltano|brucia|accend\p{L}*|spegn\p{L}*|apr\p{L}*|cade|cadono|vola|volano|suona|suonano|beve|bevono|arriva|arrivano|nuota|balla|ballano|fuma|conta|contale|ecco\p{L}*)([^\p{L}]|$)"""),
        "de" to Regex("""(?i)(^|[^\p{L}])(dreht|läuft|fließt|springt|brennt|zünd\p{L}*|öffn\p{L}*|fällt|fliegt|läutet|trinkt|kommt|kommen|schwimmt|tanzt|raucht|zähl\p{L}*|da\s+(ist|sind)|hier\s+(ist|sind))([^\p{L}]|$)"""),
        "en" to Regex("""(?i)(^|[^\p{L}])(turn(s|ing)|spin(s|ning)|running|flow(s|ing)|jump(s|ed|ing)?|leap(s|t|ed|ing)?|burn(s|ing)|lit|glow(s|ing)|open(s|ed|ing)|fall(s|ing)|fell|fly(ing)?|flies|flew|ring(s|ing)|rang|drink(s|ing)|arriv(e|es|ed|ing)|coming|here\s+(it|they|he|she)\s+(is|are|comes?)|there\s+(it|they|he|she)\s+(is|are|goes)|going\s+home|swim(s|ming)|danc(es|ing)|smok(es|ing)|spark(s|ing)|rising|rises|count)([^\p{L}]|$)"""),
    )

    /** Whether [s] names one of the things of its scene (its art's slots in the English, the scene's words): it's in view. */
    private fun namesThing(s: Said): Boolean {
        val art = s.art ?: return false
        val en = s.en.lowercase()
        val slots = SceneArt.objects[art].orEmpty()
        if (slots.any { Regex("""\b${Regex.escape(it)}(s|es)?\b""").containsMatchIn(en) }) return true
        val words = scenes[s.scene]?.get("objects").list().mapNotNull { it.obj()?.str("word") }
        val text = s.text.lowercase()
        return words.any { w -> w.length >= 4 && Regex("""(^|[^\p{L}])${Regex.escape(w.take(maxOf(4, w.length - 2)))}""").containsMatchIn(text) }
    }

    /** The looks reviewed: fine as they are, each with why (a thing in view by another name, the sky, the learner's hands …). */
    private val reviewed: Map<Pair<String, String>, String> by lazy {
        val f = listOf(File("src/test/resources/scene-coherence/looks.json"), File("app/src/test/resources/scene-coherence/looks.json")).first { it.isFile }
        Json.parseToJsonElement(f.readText()).list().mapNotNull { it.obj() }.associate { (it.str("src")!! to it.str("text")!!) to it.str("why")!! }
    }

    @Test fun `no line tells the learner to look at what isn't there`() {
        val all = tellings.flatMap(::said)
        val dir = File("build/reports/scene-coherence").apply { mkdirs() }
        fun status(s: Said) = when {
            s.cue -> "cued"
            s.near -> "cued before"
            namesThing(s) -> "in view"
            (s.src to s.text) in reviewed -> "reviewed: ${reviewed.getValue(s.src to s.text)}"
            else -> "NO CUE"
        }
        // a look in the language it is said in (its translation is only read: "Smem pogledati?" is "May I look?")
        val looked = all.filter { s -> s.kind != Kind.WRONG && (looks[s.language] ?: looks.getValue("en")).containsMatchIn(s.text) }
        val promised = all.filter { s -> s.kind != Kind.WRONG && ((visible[s.language] ?: visible.getValue("en")).containsMatchIn(s.text) || visible.getValue("en").containsMatchIn(s.en)) }
        fun row(s: Said) = "${s.src} [${s.i}] ${s.kind.name.lowercase()}\t${s.text}\t${s.en}\t${status(s)}"
        File(dir, "looks.tsv").writeText(looked.joinToString("\n", postfix = "\n") { row(it) })
        File(dir, "visible-actions.tsv").writeText(promised.joinToString("\n", postfix = "\n") { row(it) })
        val counts = promised.groupingBy { status(it).substringBefore(':') }.eachCount()
        println("SceneCoherence: ${all.size} things said in ${tellings.size} tellings; ${looked.size} looks, ${promised.size} visible actions $counts")
        val unseen = looked.filter { status(it) == "NO CUE" }
        assertTrue(
            unseen.joinToString("\n", prefix = "looks with nothing cued, nothing of the scene named, not reviewed (cue it, rewrite it, or review it in looks.json):\n") { row(it) },
            unseen.isEmpty(),
        )
        // every review is still a look that needs one (a fixed line leaves its review behind otherwise)
        val stale = reviewed.keys.filter { k -> looked.none { (it.src to it.text) == k && status(it).startsWith("reviewed") } }
        assertTrue(stale.joinToString("\n", prefix = "reviews no longer needed:\n"), stale.isEmpty())
    }
}
