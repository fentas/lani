package si.lanisce.lani.game

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.game.scene.tapTurn
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Learner
import si.lanisce.lani.ui.scene.ChoiceDiff
import si.lanisce.lani.ui.scene.DialogRun
import si.lanisce.lani.ui.words.WordTokens
import java.io.File

/**
 * Friuli's village projects' steps as short scenes (companion/cultures/friuli/project-steps; docs/plans/05-built/friuli-projects-b.md),
 * as the app reads them, in Teo's pair (Italian from German) and in another's: every step at A1, A2 and B1, each dialog short and said
 * by the project's people, in a scene of the village, its reactions as a scene's are, played to its end with every wrong choice first,
 * the "not yet" gating leaving a fresh learner something to answer, every word a step declares tested at each level; and a village in
 * the middle of a project, or past a step, goes on from where it is (synthetic villages, never a learner's data).
 */
class FriuliProjectStepsTest {
    private val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory }
    private val pair = L10n.pair
    private val pages = Grammar.bundled("it").associateBy { it.id }
    private val levels = listOf("A1", "A2", "B1")
    private val learners = listOf(Learner.of("Teo"), Learner.of("Ana", "female"))

    @After fun back() {
        L10n.pair = pair
        Cultures.use(Cultures.DEFAULT)
    }

    /** The projects of Friuli with steps as short scenes, in Teo's pair. */
    private fun friuli(p: LangPair = LangPair(Lang.IT, Lang.DE)): List<ProjectSpec> {
        L10n.pair = p
        Cultures.use("friuli")
        return Catalog.projects.filter { s -> s.steps.any { it.play != null } }
    }

    private val scenes: Map<String, SceneSpec> by lazy {
        File(root, "cultures/friuli/scenes").listFiles { f -> f.extension == "json" }.orEmpty().map { parseScene(it.readText()) }.associateBy { it.id }
    }

    private fun packFile(id: String): JsonObject = json.parseToJsonElement(File(root, "cultures/friuli/packs/$id.json").readText()).jsonObject

    /** The supplement's forms of each Italian lemma (what's in the repository; the phone has Wiktionary's besides). */
    private val forms: Map<String, Set<String>> by lazy {
        val extra = json.parseToJsonElement(File(root, "lexicon/it.extra.json").readText()).jsonObject
        (extra.getValue("entries") as JsonArray).map { it.jsonObject }.associate { o ->
            o.getValue("lemma").jsonPrimitive.content.lowercase() to (o["forms"] as? JsonObject)?.keys.orEmpty().map { it.lowercase() }.toSet()
        }
    }

    private val article = Regex("^(?:(?:il|lo|la|i|gli|le|un|uno|una) +|(?:l|un)['’])", RegexOption.IGNORE_CASE)

    /** A pack's words as the learner's cards (vocab_<pack>_<word>), each with its forms: its own, its plural, the supplement's. */
    private fun cards(packId: String): List<MyWord> = (packFile(packId)["words"] as JsonArray).map { w ->
        val o = w.jsonObject
        val said = o.getValue("it").jsonPrimitive.content
        val plain = said.replace(article, "").lowercase()
        val plural = o["plural"]?.jsonPrimitive?.contentOrNull?.replace(article, "")?.lowercase()
        MyWord("vocab_${packId}_${o.getValue("id").jsonPrimitive.content}", plain, o["de"]?.jsonPrimitive?.contentOrNull.orEmpty(), forms[plain].orEmpty() + plain + listOfNotNull(plural))
    }

    /** What every step here is: at A1, A2 and B1, 1-3 of its project's words, in its project's pack; the pack named as Friuli's are. */
    @Test fun `every step of these projects is a short scene at A1, A2 and B1, with the project's words`() {
        val projects = friuli()
        // part b's six, every step (37)
        val six = listOf("gasilski_dom", "igrisce", "vodnjak_na_trgu", "toplar", "razgledni_stolp", "mestna_ura")
        assertEquals(37, six.sumOf { id -> Projects.spec(id)!!.steps.count { it.play != null } })
        for (p in projects) {
            val pack = assertNotNull("${p.id}: its word pack", p.pack).let { p.pack!! }
            assertTrue("${p.id}: a pack named as Friuli's are: $pack", pack.startsWith("progetto-"))
            val file = packFile(pack)
            assertEquals("${p.id}: the pack names its project", p.id, file["project"]?.jsonPrimitive?.content)
            assertNotNull("${p.id}: the app bundles its pack", FestivalPacks.pack(pack))
            val words = cards(pack)
            assertTrue("${p.id}: ${words.size} words", words.size in 8..24)
            p.steps.forEachIndexed { i, s ->
                val play = assertNotNull("${p.id} step ${i + 1}: a short scene", s.play).let { s.play!! }
                assertTrue("${p.id} step ${i + 1}: its levels ${play.levels.keys}", play.levels.keys.containsAll(levels))
                assertTrue("${p.id} step ${i + 1}: 1-3 words", play.words.size in 1..3)
                for (w in play.words) assertTrue("${p.id} step ${i + 1}: «$w» in $pack", words.any { it.id == "vocab_${pack}_$w" })
            }
        }
    }

    @Test fun `each dialog short, said by the project's people, played in a scene of the village`() {
        for (p in friuli()) for ((i, s) in p.steps.withIndex()) {
            val play = s.play ?: continue
            val scene = play.scene?.let { id -> assertNotNull("${p.id} step ${i + 1}: scene $id", scenes[id]).let { scenes.getValue(id) } }
            for (level in levels) {
                val at = "${p.id} step ${i + 1} $level"
                val d = assertNotNull(at, Projects.dialog(p, i, level, "it")).let { Projects.dialog(p, i, level, "it")!! }
                assertTrue("$at: ${d.lines.size} lines", d.lines.size in 4..7)
                assertTrue("$at: the first line someone's", d.lines.first().choices.isEmpty())
                val turns = d.lines.filter { it.choices.isNotEmpty() }
                assertTrue("$at: ${turns.size} turns", turns.size in 1..3)
                val speakers = d.lines.mapNotNull { it.who }.toSet()
                assertTrue("$at: the leader speaks", p.leader in speakers)
                assertTrue("$at: only the leader and the helpers ($speakers)", (speakers - p.leader - p.helpers.toSet()).isEmpty())
                for (t in turns) {
                    assertTrue("$at: one or two right, a wrong one", t.choices.count { it.ok } in 1..2 && t.choices.any { !it.ok })
                    for (c in t.choices.filter { !it.ok }) {
                        assertTrue("$at: «${c.sl}» has a why", !c.why.isNullOrBlank())
                        assertNotNull("$at: «${c.sl}» has a reaction", c.reply)
                        c.grammar?.let { g -> assertTrue("$at: page $g", g in pages) }
                    }
                    if (t.tapTurn) {
                        assertNotNull("$at: a tap turn plays in a scene", scene)
                        for (c in t.choices) assertTrue("$at: ${c.tap} is a place of ${scene!!.art}", c.tap in SceneArt.objects[scene.art].orEmpty() || c.tap == p.leader)
                    }
                }
            }
        }
    }

    /** A reaction never says the right choice nor the words that set it apart, is one short line, and two choices share one only when both are right. */
    @Test fun `reactions as a scene's, and played with every wrong choice first a step still ends`() {
        fun words(t: String) = WordTokens.of(t).map { it.text.lowercase() }
        fun says(text: String, phrase: List<String>) = phrase.isNotEmpty() && words(text).windowed(phrase.size).any { it == phrase }
        val wrong = ArrayList<String>()
        for (p in friuli()) for ((i, s) in p.steps.withIndex()) {
            s.play ?: continue
            for (level in levels) {
                val d = Projects.dialog(p, i, level, "it") ?: continue
                val at = "${p.id}/${i + 1} $level"
                d.lines.forEachIndexed { li, l ->
                    if (l.choices.isEmpty()) return@forEachIndexed
                    val rights = l.choices.filter { it.ok }
                    val replies = l.choices.mapNotNull { c -> c.reply?.sl?.let { it to c.ok } }
                    for ((r, ok) in replies) if (!ok && replies.count { it.first == r } > 1) wrong += "$at/$li: a wrong choice shares its reaction «$r»"
                    for (c in l.choices.filter { !it.ok }) {
                        val reply = c.reply?.sl ?: continue
                        for (who in learners) {
                            val said = who.render(reply)
                            if (said.length > 80) wrong += "$at/$li: a long reaction «$said»"
                            for (r in rights) {
                                val right = who.render(r.sl.orEmpty())
                                if (says(said, words(right))) wrong += "$at/$li: says the right choice «$said»"
                                val marked = ChoiceDiff.marks(listOf(right, who.render(c.sl.orEmpty()))).first().map { it.text.lowercase() }
                                if (marked.isNotEmpty() && says(said, marked)) wrong += "$at/$li: says «${marked.joinToString(" ")}»: «$said»"
                            }
                        }
                    }
                }
                // played with every wrong choice first
                var run = DialogRun.start(d, p.leader, seed = 7)
                var steps = 0
                while (run.step != DialogRun.Step.END && steps++ < 60) {
                    if (run.step == DialogRun.Step.LISTEN) { run = run.next(); continue }
                    val k = run.choices.indices.firstOrNull { !run.choices[it].ok && it !in run.tried }
                    run = run.choose(k ?: run.choices.indexOfFirst { it.ok })
                }
                if (run.step != DialogRun.Step.END) wrong += "$at: never ends"
            }
        }
        assertTrue(wrong.joinToString("\n", prefix = "\n"), wrong.isEmpty())
    }

    @Test fun `the learner's placeholders are well formed, for a boy and for a girl`() {
        for (f in File(root, "cultures/friuli/project-steps").listFiles { x -> x.extension == "json" }.orEmpty()) {
            val raw = f.readText()
            for (who in learners) {
                val said = who.renderJson(raw)
                val left = Regex("""\{[^"\s}]*""").findAll(said).map { it.value }.filter { it.startsWith("{m:") || it.startsWith("{f:") || it.startsWith("{learner") }.toList()
                assertTrue("${f.name} said to ${who.name}: $left", left.isEmpty())
                assertTrue(f.name, runCatching { json.parseToJsonElement(said) }.isSuccess)
            }
        }
    }

    @Test fun `a fresh learner has something to answer, and every word a step declares is tested at each level`() {
        for (p in friuli()) {
            val words = MyWords(cards(p.pack!!))
            for ((i, s) in p.steps.withIndex()) {
                val play = s.play ?: continue
                // the things of the step's scene whose word is a word of the project's pack
                val things = play.scene?.let { scenes[it] }?.objects.orEmpty().mapNotNull { o -> words.of(o.word.replace('-', ' '))?.let { o.slot to it } ?: cards(p.pack!!).firstOrNull { it.id == "vocab_${p.pack}_${o.word}" }?.let { o.slot to it } }.toMap()
                for (level in levels) {
                    val at = "${p.id} step ${i + 1} $level"
                    val d: Dialog = Projects.dialog(p, i, level, "it")!!
                    val gated = Introduction.dialog(d) { id -> Introduction.above(pages[id]?.level, level) }
                    val turns = d.lines.indices.filter { d.lines[it].choices.isNotEmpty() }
                    assertTrue("$at: something to answer (not all echoes)", turns.any { it !in gated.echo })
                    for ((tested, how) in listOf(DialogWords.of(d, words, things) to "as written", DialogWords.of(gated.dialog, words, things) to "gated")) {
                        val got = DialogWords.cards(tested).map { it.id }.toSet()
                        for (w in play.words) assertTrue("$at: «$w» tested $how (tested: $got)", "vocab_${p.pack}_$w" in got)
                    }
                }
            }
        }
    }

    @Test fun `a helper's lines say nothing of who says them`() {
        val sl = Regex("""\bsem\s+\p{L}*(l|la|lo|en|na)\b|\b\p{L}{2,}(l|la)\s+sem\b|\b(sam|sama|bil|bila)\b""")
        val it = Regex("""\b(sono|ero|sarò)\s+(\p{L}+(at|ut|it)[oa]|stanc[oa]|content[oa]|pront[oa]|sicur[oa])\b|\bmi sono \p{L}+[oa]\b""")
        val friuli = friuli(LangPair(Lang.IT, Lang.SL))
        for (p in friuli) for ((i, s) in p.steps.withIndex()) for (level in levels) {
            if (s.play == null) continue
            val d = Projects.dialog(p, i, level, "it") ?: continue
            for (l in d.lines) if (l.who != null && l.who in p.helpers) {
                assertFalse("${p.id} step ${i + 1} $level: ${l.who}'s «${l.sl}»", it.containsMatchIn(l.sl.orEmpty().lowercase()))
                assertFalse("${p.id} step ${i + 1} $level: ${l.who}'s «${l.en}»", sl.containsMatchIn(l.en.orEmpty().lowercase()))
            }
        }
    }

    /**
     * A village that had a project going as practices, or had come past some of its steps: its count stands, the next step is a dialog at
     * its place, played to its end it counts one more and tells its chronicle's line; finished, there is no step to take. Nothing is migrated.
     */
    @Test fun `a village in the middle of a project goes on from where it is, and a finished project stays finished`() {
        val projects = friuli()
        val present = projects.flatMap { listOf(it.leader) + it.helpers }.distinct()
        for (p in projects) for (k in 0..p.steps.size) {
            val s = Chest.add(
                GameState(
                    age = Age.entries.last(), resources = Res.entries.associateWith { 4000 }, residents = present.map { Resident(it, "2026-08-01") }, villagers = present.size,
                    help = 200, lastTick = day0.toString(), projects = mapOf(p.id to k),
                ),
                Catalog.goods.values.first { it.food }.id, 5,
            )
            assertEquals("${p.id} past $k steps", k, Projects.done(s, p))
            val before = Projects.landmarks(s).firstOrNull { it.project == p.id }
            if (k == 0) assertNull(before) else assertEquals("${p.id}: its landmark at stage $k", k, before?.stage)
            if (k == p.steps.size) {
                assertTrue(Projects.finished(s, p))
                assertFalse("${p.id}: finished, nothing to take", Projects.option(s, p, day0).available)
                continue
            }
            val o = Projects.option(s, p, day0)
            assertTrue("${p.id}: step ${k + 1} can be taken: $o", o.available)
            assertNotNull("${p.id}: step ${k + 1} is a dialog", Projects.dialog(p, k, "A2", "it"))
            val (n, r) = Projects.stepPlayed(s, p.id, day0, noon(day0))
            assertTrue("${p.id}: step ${k + 1}: ${r.message}", r.won)
            assertEquals(k + 1, n.projects[p.id])
            assertTrue("${p.id}: the chronicle tells step ${k + 1}", n.log.any { it.text.contains(p.steps[k].lineText.target) })
        }
    }

    @Test fun `a step's level is the learner's, and a step whose scene isn't open plays over the village`() {
        val p = friuli().first { it.id == "mestna_ura" }
        val a = Projects.dialog(p, 1, "A1", "it")!!
        val b = Projects.dialog(p, 1, "B1", "it")!!
        assertFalse("A1 and B1 tell it differently", a.lines == b.lines)
        assertEquals("a B2 learner meets B1's", b.lines, Projects.dialog(p, 1, "B2", "it")!!.lines)
        val play = Projects.play(p, 1)!!
        val square = scenes.getValue(play.scene!!)
        assertNull("no square in a young village", Projects.sceneOf(play, listOf(square), GameState(age = Age.TABOR), "it"))
    }
}
