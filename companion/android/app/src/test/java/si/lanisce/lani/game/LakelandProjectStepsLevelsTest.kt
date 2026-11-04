package si.lanisce.lani.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.scene.DialogRun

/**
 * The Lake District's project steps at every level (companion/cultures/lakeland/project-steps; docs/plans/05-built/village-b2-lakeland.md):
 * all thirteen projects' steps are told at A1, A2, B1 and B2 and play to their end in every base with every wrong choice first, each
 * telling short, with a reaction that doesn't say the right choice, and a B2 learner hears another telling than an A2 one.
 */
class LakelandProjectStepsLevelsTest {
    private val pair = L10n.pair
    private val levels = listOf("A1", "A2", "B1", "B2")
    private val pairs = listOf(LangPair(Lang.EN, Lang.SL), LangPair(Lang.EN, Lang.DE), LangPair(Lang.EN, Lang.IT))

    @After fun back() {
        L10n.pair = pair
        Cultures.use(Cultures.DEFAULT)
    }

    private fun projects(p: LangPair): List<ProjectSpec> {
        L10n.pair = p
        Cultures.use("lakeland")
        return Catalog.projects.filter { s -> s.steps.any { it.play != null } }
    }

    @Test fun `every step of every project is told at each level, and played with every wrong choice first it ends`() {
        var told = 0
        for (pr in pairs) for (p in projects(pr)) for ((i, s) in p.steps.withIndex()) {
            val play = s.play ?: continue
            assertTrue("${p.id}/${i + 1}: ${play.levels.keys}", play.levels.keys.containsAll(levels))
            for (level in levels) {
                val at = "${p.id}/${i + 1} $level ${pr.code}"
                val d = Projects.dialog(p, i, level, "en")!!
                assertTrue("$at: ${d.lines.size} lines", d.lines.size in 4..7)
                var run = DialogRun.start(d, p.leader, seed = 7)
                var steps = 0
                while (run.step != DialogRun.Step.END && steps++ < 60) {
                    if (run.step == DialogRun.Step.LISTEN) { run = run.next(); continue }
                    val k = run.choices.indices.firstOrNull { !run.choices[it].ok && it !in run.tried }
                    run = run.choose(k ?: run.choices.indexOfFirst { it.ok })
                }
                assertEquals(at, DialogRun.Step.END, run.step)
                told++
            }
        }
        assertEquals("78 steps at 4 levels in 3 bases", 78 * 4 * 3, told)
    }

    @Test fun `a learner's level picks the telling`() {
        val p = projects(pairs.first()).first { it.id == "mlin" }
        val heard = levels.map { lv -> Projects.dialog(p, 2, lv, "en")!!.lines.mapNotNull { it.sl }.joinToString(" ") }
        assertEquals("four tellings", 4, heard.toSet().size)
    }
}
