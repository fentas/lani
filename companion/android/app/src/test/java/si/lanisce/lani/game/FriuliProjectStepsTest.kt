package si.lanisce.lani.game

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Learner
import si.lanisce.lani.ui.scene.ChoiceDiff
import si.lanisce.lani.ui.words.WordTokens
import java.io.File
import java.time.LocalDate

/**
 * Friuli's village projects' steps as short scenes (companion/cultures/friuli/project-steps, companion/SCENES.md "Project steps";
 * docs/plans/05-built/friuli-projects-a.md), as the app reads them for a child learning Italian from German with B1 as the aim:
 * every step at A1, A2 and B1, each learner gets their level's; short, said by the project's people; every wrong choice with a
 * reaction that doesn't give the right form away, a B1 telling testing a B1 rule. And a village that was already past a step,
 * or in the middle of a project, when the steps came: it goes on from where it was, at the next step's dialog, to its end.
 */
class FriuliProjectStepsTest {
    private val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory }
    private val pair = L10n.pair
    private val projects = listOf("mlaj", "most", "mlin", "balinisce", "kapelica", "vinograd", "cebelji_travnik")
    private val learners = listOf(Learner.of("Teo"), Learner.of("Ana", "female"))
    private val levels = listOf("A1", "A2", "B1")
    private val pageLevel: Map<String, String> by lazy {
        File(root, "grammar/it").listFiles { f -> f.extension == "json" }.orEmpty().associate { f ->
            f.nameWithoutExtension to (json.parseToJsonElement(f.readText()).jsonObject["level"] as JsonPrimitive).content
        }
    }

    @After fun back() {
        L10n.pair = pair
        Cultures.use(Cultures.DEFAULT)
    }

    private fun friuli() {
        Cultures.use(Cultures.load("friuli"))
        L10n.pair = LangPair(Lang.IT, Lang.DE)
    }

    private fun spec(id: String): ProjectSpec = assertNotNull("project $id", Projects.spec(id)).let { Projects.spec(id)!! }

    private fun JsonElement?.list(): List<JsonElement> = (this as? JsonArray).orEmpty()
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private val JsonObject.ok: Boolean get() = (this["ok"] as? JsonPrimitive)?.content == "true"

    private fun steps(id: String): List<JsonObject> =
        json.parseToJsonElement(File(root, "cultures/friuli/project-steps/$id.json").readText()).jsonObject["steps"].list().mapNotNull { it.obj() }

    private fun lines(step: JsonObject, level: String): List<JsonObject> =
        step["levels"].obj()?.get(level).obj()?.get("lines").list().mapNotNull { it.obj() }

    private val scenes: List<SceneSpec> by lazy {
        File(root, "cultures/friuli/scenes").listFiles { f -> f.extension == "json" }.orEmpty().map { parseScene(it.readText()) }
    }

    @Test fun `every step of the seven projects is a short scene at A1, A2 and B1, each learner at their level's`() {
        friuli()
        var dialogs = 0
        for (id in projects) {
            val p = spec(id)
            val pack = assertNotNull("$id: its word pack", p.pack).let { p.pack!! }
            assertNotNull("$id: the app bundles its pack", FestivalPacks.pack(pack))
            for ((i, s) in p.steps.withIndex()) {
                val at = "$id step ${i + 1}"
                val play = assertNotNull("$at: a short scene", s.play).let { s.play!! }
                assertTrue("$at: ${play.levels.keys}", play.levels.keys.containsAll(levels))
                assertTrue("$at: ${play.words}", play.words.size in 1..3)
                val raw = steps(id)[i]
                for (lv in levels) {
                    val d = assertNotNull("$at $lv", Projects.dialog(p, i, lv, "it")).let { Projects.dialog(p, i, lv, "it")!! }
                    dialogs++
                    assertEquals("$at $lv: what the learner at $lv reads", lines(raw, lv).size, d.lines.size)
                    assertTrue("$at $lv: ${d.lines.size} lines", d.lines.size in 4..7)
                    assertTrue("$at $lv: the first line someone's", d.lines.first().choices.isEmpty())
                    assertTrue("$at $lv: its turns", d.lines.count { it.choices.isNotEmpty() } in 1..3)
                    val speakers = d.lines.mapNotNull { it.who }.toSet()
                    assertTrue("$at $lv: the leader speaks", p.leader in speakers)
                    assertTrue("$at $lv: only the project's people: $speakers", (speakers - p.leader - p.helpers.toSet()).isEmpty())
                }
                // above B1 they read B1's, and a learner below A1's scale A1's
                assertEquals("$at: a B2 learner reads B1's", lines(raw, "B1").size, Projects.dialog(p, i, "B2", "it")!!.lines.size)
            }
        }
        assertEquals("every step (41) at three levels", 41 * 3, dialogs)
    }

    @Test fun `a telling tests what its level has - no page above it, and B1's a rule of B1`() {
        val bad = ArrayList<String>()
        for (id in projects) for ((i, st) in steps(id).withIndex()) for (lv in levels) {
            val named = lines(st, lv).flatMap { l -> l["choices"].list().mapNotNull { it.obj()?.str("grammar") } }
            for (g in named) {
                val at = pageLevel[g]
                if (at == null) bad += "$id/${i + 1} $lv: no page $g"
                else if (levels.indexOf(at) > levels.indexOf(lv)) bad += "$id/${i + 1} $lv: $g is $at"
            }
            if (lv == "B1" && named.none { pageLevel[it] == "B1" }) bad += "$id/${i + 1} B1: no rule of B1 tested"
        }
        assertTrue(bad.joinToString("\n", prefix = "\n"), bad.isEmpty())
    }

    private fun words(t: String) = WordTokens.of(t).map { it.text.lowercase() }
    private fun says(text: String, phrase: List<String>) = phrase.isNotEmpty() && words(text).windowed(phrase.size).any { it == phrase }

    /** The runs of [right]'s words that [wrong] lacks, where the two differ in a word or two ([ChoiceDiff]). */
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

    @Test fun `no reaction says the right choice nor what sets it apart, each is short, and each choice has its own reply`() {
        val bad = LinkedHashSet<String>()
        var reactions = 0
        for (who in learners) for (id in projects) for ((i, st) in steps(id).withIndex()) for (lv in levels) {
            for ((li, l) in lines(st, lv).withIndex()) {
                val choices = l["choices"].list().mapNotNull { it.obj() }
                if (choices.isEmpty()) continue
                val at = "$id/${i + 1} $lv [$li]"
                if (choices.any { it.str("grammar") != null } && l["puzzled"] == null) bad += "$at: a form turn without its puzzled reaction"
                if (choices.map { it["reply"].toString() }.toSet().size != choices.size) bad += "$at: two choices share a reply"
                val rights = choices.filter { it.ok }.mapNotNull { it.str("it")?.let(who::render) }
                for ((k, c) in choices.withIndex()) {
                    if (c.ok) continue
                    reactions++
                    val reply = c["reply"].obj()
                    if (reply == null) { bad += "$at/$k: no reaction"; continue }
                    val said = who.render(reply.str("it").orEmpty())
                    if (reply["sky"] != null) bad += "$at/$k: a sky cue"
                    if (said.length > 80) bad += "$at/$k: a long reaction «$said»"
                    if (c["why"].obj()?.keys?.containsAll(listOf("de", "sl", "en")) != true) bad += "$at/$k: a why per base"
                    val wrong = who.render(c.str("it").orEmpty())
                    for (right in rights) {
                        if (says(said, words(right))) bad += "$at/$k says the right choice: «$said»"
                        for (run in apart(right, wrong)) {
                            if (says(said, run)) bad += "$at/$k says «${run.joinToString(" ")}»: «$said»"
                            for (b in listOf("de", "sl", "en")) {
                                val meant = reply.str(b)?.let(who::render) ?: continue
                                if (run.joinToString(" ").length >= 3 && says(meant, run)) bad += "$at/$k.$b says «${run.joinToString(" ")}»: «$meant»"
                            }
                        }
                    }
                }
            }
        }
        assertTrue(bad.joinToString("\n", prefix = "\n"), bad.isEmpty())
        assertTrue("the reactions counted: $reactions", reactions > 300)
    }

    private val day = LocalDate.parse("2026-11-03")
    private val noon: Long = day.atTime(12, 0).toEpochSecond(java.time.ZoneOffset.UTC) * 1000

    /** A synthetic village at the project's age or later, its stores full, the project's people living here, as far as [done] steps. */
    private fun village(p: ProjectSpec, done: Int): GameState {
        val who = (listOf(p.leader) + p.helpers).distinct()
        return GameState(
            age = maxOf(p.age, Age.VAS), resources = Res.entries.associateWith { 9000 }, residents = who.map { Resident(it, "2026-08-01") }, villagers = who.size,
            help = 60, lastTick = day.toString(), projects = if (done > 0) mapOf(p.id to done) else emptyMap(),
        ).let { s -> Chest.add(s, Catalog.goods.values.first { it.food && !it.rare }.id, 1) } // the last step, the feast, takes a treat
    }

    @Test fun `a village past a step or in the middle of a project goes on from there, to the project's end`() {
        friuli()
        for (id in projects) {
            val p = spec(id)
            for (done in p.steps.indices) {
                val at = "$id, $done steps done"
                val s = village(p, done)
                assertEquals(at, done, Projects.done(s, p))
                val o = Projects.option(s, p, day)
                assertTrue("$at: today's step is on offer: ${o.available}", o.available)
                // the next step's dialog, at each of a learner's levels; a practice no more
                for (lv in levels) assertNotNull("$at $lv", Projects.dialog(p, done, lv, "it"))
                val (n, r) = Projects.stepPlayed(s, id, day, noon)
                assertTrue("$at: ${r.message}", r.won)
                assertEquals(at, done + 1, n.projects[id])
                assertTrue("$at: its chronicle line, as the village's log had it before", n.log.any { it.text.startsWith(p.steps[done].line.substringBefore(" · ").take(12)) })
                // the same village at the next step's day still has its landmark
                assertTrue(at, Projects.landmarks(n).any { it.project == id && it.stage == done + 1 })
            }
            // a finished one has nothing left to play, and stays finished
            val over = village(p, p.steps.size)
            assertTrue(id, Projects.finished(over, p))
            assertNull("$id: nothing after the last step", Projects.dialog(p, p.steps.size, "A1", "it"))
            assertFalse("$id: a finished project offers no step", Projects.option(over, p, day).available)
        }
    }

    @Test fun `each step's scene is the village's and opens with what the step needs, else the step is played over the village`() {
        friuli()
        val ids = scenes.map { it.id }.toSet()
        var inScene = 0
        for (id in projects) for ((i, st) in steps(id).withIndex()) {
            val sid = st.str("scene") ?: continue
            assertTrue("$id/${i + 1}: scene $sid", sid in ids)
            inScene++
        }
        assertTrue("the steps played in a scene: $inScene", inScene >= 30)
        // the chapel's road has no scene: over the village, and the first of the maypole in the wood from the first day
        val p = spec("kapelica")
        assertNull(Projects.sceneOf(p.steps[0].play!!, scenes, village(p, 0), "it"))
        val m = spec("mlaj")
        assertEquals("nel-bosco", Projects.sceneOf(m.steps[0].play!!, scenes, village(m, 0).copy(age = Age.ZASELEK), "it")?.id)
    }
}
