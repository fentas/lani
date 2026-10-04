package si.lanisce.lani.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.parseScenes
import java.io.File
import java.time.LocalDateTime

/** The debug fixture (app/src/debug/assets) follows the lani.scene/v0 rules and plays to the end. */
class SceneFixtureTest {
    private val scenes = parseScenes(File("src/debug/assets/scenes-fixture.json").readText())

    @Test fun `the fixture parses and keeps to the art registry`() {
        assertEquals(listOf("ob-ognju", "kuhinja"), scenes.map { it.id })
        for (s in scenes) {
            val slots = SceneArt.objects.getValue(s.art)
            assertTrue(s.id, s.objects.all { it.slot in slots && it.sl.isNotBlank() && it.en.isNotBlank() })
            assertEquals(s.objects.size, s.objects.map { it.slot }.distinct().size)
            assertTrue(s.id, s.people.all { it.art in SceneArt.people && it.slot in SceneArt.personSlots.getValue(s.art) })
            assertTrue(s.id, s.from.all { it in SceneArt.places })
            for (h in s.happenings) {
                assertTrue(h.id, s.people.any { it.id == h.who })
                assertTrue(h.id, s.dialogs.any { it.id == h.dialog })
                assertTrue(h.id, SceneWords.reward(h.reward).isNotEmpty())
            }
            for (d in s.dialogs) {
                assertTrue(d.id, d.lines.size in 3..8)
                assertTrue(d.id, d.lines.filter { it.choices.isNotEmpty() }.all { t -> t.choices.size in 2..3 && t.choices.any { it.ok } && t.choices.filter { !it.ok }.all { it.why != null } })
            }
        }
    }

    @Test fun `its happenings are on in a camp with a tent, and their dialogs play to the end`() {
        val camp = GameState(seed = 3, age = Age.TABOR, buildings = listOf(Building("tent-1", BuildingType.TENT, 0)))
        val on = Happenings.active(scenes, camp, LocalDateTime.of(2026, 9, 24, 9, 0))
        assertEquals(setOf("ob-ognju/igra", "kuhinja/juha"), on.map { it.key }.toSet())
        for (a in on) {
            val d = a.scene.dialogs.first { it.id == a.happening.dialog }
            var r = DialogRun.start(d, a.person!!.id)
            var steps = 0
            while (r.step != DialogRun.Step.END && steps++ < 20) {
                r = if (r.step == DialogRun.Step.LISTEN) r.next() else r.choose(r.choices.indexOfFirst { it.ok })
            }
            assertEquals(DialogRun.Step.END, r.step)
            assertEquals(0, r.mistakes)
        }
    }
}
