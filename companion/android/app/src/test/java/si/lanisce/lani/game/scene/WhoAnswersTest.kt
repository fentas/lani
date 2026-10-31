package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.ui.scene.DialogRun
import java.io.File

/**
 * Who answers in a scene (companion/SCENES.md, "Format", `to`; docs/plans/05-built/scene-replier.md). In a happening's
 * dialog the happening's person answers every turn ([DialogRun]'s partner: their voice, their face), while the panel's
 * header shows the turn's `to`, else whoever spoke last ([DialogRun.facing]). So a turn that comes after someone else's
 * line names the one who answers it as its `to`, and the header shows the person whose voice and face answer. Checked
 * as the app plays it: every scene of Jan's and of the culture packs (Friuli, Kärnten, the Lake District, …), every
 * dialog and variant, a counting one at its least number, on every way through its right choices (one with a reply and
 * one without leave a different person speaking last before the next turn).
 */
class WhoAnswersTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "scenes").isDirectory && File(it, "cultures").isDirectory }

    /** The scene files: Jan's (and a village of another language's, companion/scenes/<language>/) and the culture packs'. */
    private val files = (listOf(File(companion, "scenes")) + File(companion, "cultures").listFiles().orEmpty().map { File(it, "scenes") })
        .flatMap { dir -> dir.walk().maxDepth(2).filter { it.isFile && it.extension == "json" }.toList() }
        .sortedBy { it.path }

    /**
     * Where the panel and the answer part in [d] played in [partner]'s happening: "turn <line>: the panel shows X, Y
     * answers", once for each turn, on every way through its right choices.
     */
    private fun parted(d: Dialog, partner: String): List<String> {
        val out = LinkedHashSet<String>()
        fun go(start: DialogRun) {
            var run = start
            var steps = 0
            while (run.step == DialogRun.Step.LISTEN && steps++ < 60) run = run.next()
            if (run.step != DialogRun.Step.CHOOSE) return
            val answers = run.turn?.to ?: partner
            if (run.facing != answers) out += "turn ${run.at}: the panel shows ${run.facing}, $answers answers"
            run.choices.indices.filter { run.choices[it].ok }.forEach { go(run.choose(it)) }
        }
        go(DialogRun.start(d, partner))
        return out.toList()
    }

    @Test fun `every turn of a scene shows the one who answers it`() {
        var turns = 0
        val wrong = files.flatMap { f ->
            val s = parseScene(f.readText())
            s.dialogs.flatMap { d ->
                val played = d.count?.let { Counts.render(d, it.min, s.language) } ?: d
                turns += played.lines.count { it.choices.isNotEmpty() }
                s.happenings.filter { it.dialog == d.id || d.id in it.dialogs }.map { it.who }.distinct().flatMap { partner ->
                    parted(played, partner).map { "${s.id} (${f.parentFile.parentFile.name}/${f.name}), dialog ${d.id}, $it: name $partner as its to" }
                }
            }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
        // all of them: Jan's village and every culture's, the Carinthian and the Lake District's places too
        assertTrue("${files.size} scenes", files.size >= 100)
        for (culture in listOf("friuli", "kaernten", "lakeland", "primorska")) assertTrue(culture, files.any { it.path.contains("/cultures/$culture/scenes/") })
        assertTrue("$turns turns", turns > 1000)
    }

    @Test fun `a turn after someone else's line without its to is caught, and its to settles it`() {
        // Zala's happening: Nejc shows his devil, the learner answers, Zala answers them
        val nejc = DialogLine(who = "nejc", sl = "Moj hudič je najlepši!")
        val turn = DialogLine(choices = listOf(DialogChoice(sl = "Meni je všeč, Nejc!", ok = true), DialogChoice(sl = "Mene je všeč, Nejc!")))
        val zala = DialogLine(who = "zala", sl = "Najlepši? Ima tri noge!")
        assertEquals(listOf("turn 1: the panel shows nejc, zala answers"), parted(Dialog("d", listOf(nejc, turn, zala, turn)), "zala"))
        assertEquals(emptyList<String>(), parted(Dialog("d", listOf(nejc, turn.copy(to = "zala"), zala, turn)), "zala"))
        // a reply of the happening's person before the next turn leaves them speaking last
        val answered = turn.copy(to = "zala", choices = listOf(turn.choices[0].copy(reply = DialogReply(sl = "Res?")), turn.choices[1]))
        assertEquals(emptyList<String>(), parted(Dialog("d", listOf(nejc, answered, turn)), "zala"))
        // not without: then Nejc spoke last
        assertEquals(listOf("turn 2: the panel shows nejc, zala answers"), parted(Dialog("d", listOf(nejc, turn.copy(to = "zala"), turn)), "zala"))
    }
}
