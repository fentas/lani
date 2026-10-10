package si.lanisce.lani.game

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.json
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.game.scene.tapTurn
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Learner
import java.io.File

/**
 * Primorska's village projects' steps as short scenes (companion/cultures/primorska/project-steps, companion/SCENES.md
 * "Project steps"), as the app reads them: every step of every project at A1 and A2, each dialog short and said by the
 * project's leader and helpers, played in a scene the village has (its tap turns at places of its art), its placeholders
 * well formed for a man and a woman, the "not yet" gating leaving a fresh learner something to answer, and every word a
 * step declares tested by a turn at both levels, also gated, in a form the dictionary knows (so it counts on the card).
 */
class ProjectStepsContentTest {
    private val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory }
    private val pair = L10n.pair
    private val pages = Grammar.bundled("sl").associateBy { it.id }

    @After fun back() {
        L10n.pair = pair
        Cultures.use(Cultures.DEFAULT)
    }

    private fun primorska(): List<ProjectSpec> {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        Cultures.use(Cultures.default())
        return Catalog.projects
    }

    /** The dictionary's forms of each lemma (Wiktionary's, companion/lexicon/sl.json, and the supplement's, sl.extra.json). */
    private val forms: Map<String, Set<String>> by lazy {
        val out = HashMap<String, MutableSet<String>>()
        val dict = json.parseToJsonElement(File(root, "lexicon/sl.json").readText()).jsonObject
        val entries = dict.getValue("lemmas").jsonArray.map { it.jsonObject }
        val lemmas = entries.map { it.getValue("lemma").jsonPrimitive.content }
        val wiktionary = entries.map { "${it.getValue("lemma").jsonPrimitive.content}|${it["pos"]?.jsonPrimitive?.contentOrNull}" }.toSet()
        for ((form, readings) in dict.getValue("forms").jsonObject) for (r in readings.jsonArray) out.getOrPut(lemmas[r.jsonArray[0].jsonPrimitive.int]) { HashSet() } += form
        val extra = json.parseToJsonElement(File(root, "lexicon/sl.extra.json").readText()).jsonObject
        for (e in extra.getValue("entries").jsonArray) {
            val o = e.jsonObject
            val lemma = o["lemma"]?.jsonPrimitive?.contentOrNull ?: continue
            // the supplement's forms count for a word Wiktionary lacks with that part of speech (lexicon.ts lemmasOf)
            if ("$lemma|${o["pos"]?.jsonPrimitive?.contentOrNull}" in wiktionary) continue
            out.getOrPut(lemma) { HashSet() }.addAll((o["forms"] as? JsonObject)?.keys.orEmpty())
        }
        out
    }

    private fun packFile(id: String): JsonObject =
        (listOf(File(root, "cultures/primorska/packs/$id.json"), File(root, "packs/$id.json")).first { it.isFile }).readText().let { json.parseToJsonElement(it).jsonObject }

    /** A pack's words as the learner's cards (vocab_<pack>_<word>), each with its dictionary forms. */
    private fun cards(packId: String): List<MyWord> = (packFile(packId)["words"] as JsonArray).map { w ->
        val o = w.jsonObject
        val sl = o.getValue("sl").jsonPrimitive.content
        MyWord("vocab_${packId}_${o.getValue("id").jsonPrimitive.content}", sl, o["en"]?.jsonPrimitive?.contentOrNull.orEmpty(), forms[sl].orEmpty() + sl.lowercase())
    }

    /** The scenes of the village (the curated Slovene ones and Primorska's own), their things with their words. */
    private val scenes: Map<String, Pair<SceneSpec, Map<String, String>>> by lazy {
        val files = File(root, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().toList() +
            File(root, "cultures/primorska/scenes").listFiles { f -> f.extension == "json" }.orEmpty().toList()
        files.map { parseScene(it.readText()) }.filter { it.language == "sl" }.associate { s ->
            val words = s.objects.mapNotNull { o ->
                val pack = runCatching { packFile(o.pack ?: s.pack ?: return@mapNotNull null) }.getOrNull() ?: return@mapNotNull null
                val w = (pack["words"] as JsonArray).firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == o.word } ?: return@mapNotNull null
                o.slot to w.jsonObject.getValue("sl").jsonPrimitive.content
            }.toMap()
            s.id to (s to words)
        }
    }

    /** A learner at [level] who was introduced to nothing: the pages above their level are not yet. */
    private fun fresh(level: String): (String) -> Boolean = { id -> Introduction.above(pages[id]?.level, level) }

    @Test fun `every step of every project is a short scene at A1 and A2, with the project's words`() {
        val projects = primorska()
        assertEquals(13, projects.size)
        var steps = 0
        for (p in projects) {
            val pack = assertNotNull("${p.id}: its word pack", p.pack).let { p.pack!! }
            val file = packFile(pack)
            assertEquals("${p.id}: the pack names its project", p.id, file["project"]?.jsonPrimitive?.content)
            assertNotNull("${p.id}: the app bundles its pack", FestivalPacks.pack(pack))
            val words = cards(pack)
            assertTrue("${p.id}: ${words.size} words", words.size in 8..24)
            p.steps.forEachIndexed { i, s ->
                val play = s.play
                assertNotNull("${p.id} step ${i + 1}: a short scene", play)
                assertTrue("${p.id} step ${i + 1}: A1 and A2", play!!.levels.keys.containsAll(listOf("A1", "A2")))
                assertTrue("${p.id} step ${i + 1}: 1-3 words", play.words.size in 1..3)
                for (w in play.words) assertTrue("${p.id} step ${i + 1}: «$w» in ${pack}", words.any { it.id == "vocab_${pack}_$w" })
                steps++
            }
        }
        assertEquals(78, steps)
    }

    @Test fun `each dialog short, said by the project's people, played in a scene of the village`() {
        val projects = primorska()
        var dialogs = 0
        var lines = 0
        var taps = 0
        var inScene = 0
        for (p in projects) for ((i, s) in p.steps.withIndex()) {
            val play = s.play ?: continue
            val scene = play.scene?.let { id -> assertNotNull("${p.id} step ${i + 1}: scene $id", scenes[id]).let { scenes.getValue(id).first } }
            if (scene != null) inScene++
            for (level in listOf("A1", "A2")) {
                val at = "${p.id} step ${i + 1} $level"
                val d = assertNotNull(at, Projects.dialog(p, i, level, "sl")).let { Projects.dialog(p, i, level, "sl")!! }
                dialogs++
                lines += d.lines.size
                assertTrue("$at: ${d.lines.size} lines", d.lines.size in 4..7)
                assertTrue("$at: the first line someone's", d.lines.first().choices.isEmpty())
                val turns = d.lines.filter { it.choices.isNotEmpty() }
                assertTrue("$at: ${turns.size} turns", turns.size in 1..3)
                val speakers = d.lines.mapNotNull { it.who }.toSet()
                assertTrue("$at: the leader speaks", p.leader in speakers)
                assertTrue("$at: only the leader and the helpers ($speakers)", (speakers - p.leader - p.helpers.toSet()).isEmpty())
                for (t in turns) {
                    assertTrue("$at: a right choice", t.choices.any { it.ok })
                    assertTrue("$at: a wrong choice", t.choices.any { !it.ok })
                    for (c in t.choices.filter { !it.ok }) {
                        assertTrue("$at: «${c.sl}» has a why", !c.why.isNullOrBlank())
                        assertNotNull("$at: «${c.sl}» has a reaction", c.reply)
                        c.grammar?.let { g -> assertTrue("$at: page $g", g in pages) }
                    }
                    if (t.tapTurn) {
                        taps++
                        assertNotNull("$at: a tap turn plays in a scene", scene)
                        for (c in t.choices) assertTrue("$at: ${c.tap} is a place of ${scene!!.art}", c.tap in SceneArt.objects[scene.art].orEmpty() || c.tap == p.leader)
                    }
                }
            }
        }
        println("project steps: $dialogs dialogs, $lines lines, ${taps} tap turns, $inScene of 78 steps in a scene of their own")
        assertEquals(156, dialogs)
        assertTrue("some tap turns ($taps)", taps >= 10)
    }

    @Test fun `the learner's placeholders are well formed, for a man and for a woman`() {
        val dir = File(root, "cultures/primorska/project-steps")
        for (f in dir.listFiles { x -> x.extension == "json" }.orEmpty()) {
            val raw = f.readText()
            for (who in listOf(Learner.of("Jan", "male"), Learner.of("Ana", "female"))) {
                val said = who.renderJson(raw)
                val left = Regex("""\{[^"\s}]*""").findAll(said).map { it.value }.filter { it.startsWith("{m:") || it.startsWith("{f:") || it.startsWith("{learner") }.toList()
                assertTrue("${f.name} said to ${who.name}: $left", left.isEmpty())
                assertTrue(f.name, runCatching { json.parseToJsonElement(said) }.isSuccess)
            }
        }
    }

    @Test fun `a fresh learner has something to answer, and every word a step declares is tested at both levels`() {
        val projects = primorska()
        var gatedTurns = 0
        var echoes = 0
        for (p in projects) {
            val words = MyWords(cards(p.pack!!))
            for ((i, s) in p.steps.withIndex()) {
                val play = s.play ?: continue
                val things = play.scene?.let { scenes[it]?.second }.orEmpty().mapNotNull { (slot, sl) -> words.of(sl)?.let { slot to it } }.toMap()
                for (level in listOf("A1", "A2")) {
                    val at = "${p.id} step ${i + 1} $level"
                    val d: Dialog = Projects.dialog(p, i, level, "sl")!!
                    // a learner at the dialog's level who was introduced to nothing: its rules above that level not yet
                    val gated = Introduction.dialog(d, fresh(level))
                    val turns = d.lines.indices.filter { d.lines[it].choices.isNotEmpty() }
                    gatedTurns += gated.notYet.size
                    echoes += gated.echo.size
                    assertTrue("$at: something to answer (not all echoes)", turns.any { it !in gated.echo })
                    for ((tested, how) in listOf(DialogWords.of(d, words, things) to "as written", DialogWords.of(gated.dialog, words, things) to "gated")) {
                        val got = DialogWords.cards(tested).map { it.id }.toSet()
                        for (w in play.words) assertTrue("$at: «$w» tested $how (tested: $got)", "vocab_${p.pack}_$w" in got)
                    }
                }
            }
        }
        println("project steps for a fresh learner at their level: $gatedTurns turns with a rule not yet, $echoes echoes")
    }

    @Test fun `a helper's lines say nothing of who says them`() {
        val projects = primorska()
        val gendered = Regex("""\bsem\s+\p{L}*(l|la|lo|en|na)\b|\b\p{L}{2,}(l|la)\s+sem\b|\b(sam|sama|bil|bila)\b""")
        for (p in projects) for ((i, s) in p.steps.withIndex()) for (level in listOf("A1", "A2")) {
            val d = Projects.dialog(p, i, level, "sl") ?: continue
            for (l in d.lines) if (l.who != null && l.who in p.helpers) {
                assertTrue("${p.id} step ${i + 1} $level: ${l.who}'s «${l.sl}»", !gendered.containsMatchIn(l.sl.orEmpty().lowercase()))
            }
        }
    }
}
