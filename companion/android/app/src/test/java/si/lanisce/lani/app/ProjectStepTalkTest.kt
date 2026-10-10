package si.lanisce.lani.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.data.json
import si.lanisce.lani.game.ChallengeResult
import si.lanisce.lani.game.Projects
import si.lanisce.lani.game.TurnMode
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.ProjectStepsFile
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.ui.scene.DialogRun

/**
 * A village project's step played as its short scene (companion/SCENES.md "Project steps"), as the scene controller plays
 * it: in its scene, the leader and a helper on the scene's stage, a tap turn answered in the picture, and at its end the
 * step done (the game's [Projects.stepPlayed], here a stand-in) and nothing else paid; over the village without its scene,
 * the tap turn chosen. A step without a scene of its own isn't started here (it is its practice).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProjectStepTalkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val played = ArrayList<String>()
    private val paid = ArrayList<String>()

    /** The maypole's first step in the forest: Luka and Marko, a tap turn on the spruce, then a choice. */
    private val steps = json.decodeFromString(
        ProjectStepsFile.serializer(),
        """{"schema": "lani.project-steps/v0", "project": "mlaj", "language": "sl", "pack": "projekt-mlaj", "steps": [
          {"scene": "v-gozdu", "words": ["smreka"], "levels": {"A1": {"lines": [
            {"who": "luka", "sl": "Poišči visoko smreko!", "en": "Find a tall spruce!"},
            {"choices": [
              {"sl": "Tukaj je visoka smreka!", "en": "Here is a tall spruce!", "ok": true, "tap": "spruce", "reply": {"sl": "Bravo!", "en": "Well done!"}},
              {"sl": "Tukaj je visoko drevo!", "en": "Here is a tall tree!", "tap": "tree", "why": {"en": "That one has leaves."}, "reply": {"sl": "Drevo?", "en": "A tree?"}}
            ]},
            {"who": "marko", "sl": "Pazite! Smreka pada!", "en": "Watch out! The spruce is falling!"},
            {"choices": [
              {"sl": "Gremo domov!", "en": "Let's go home!", "ok": true},
              {"sl": "Gremo spat!", "en": "Let's go to sleep!", "why": {"en": "Not yet."}, "reply": {"sl": "Zdaj?", "en": "Now?"}}
            ]}
          ]}}}
        ]}""",
    )

    private val forest = SceneSpec(
        id = "v-gozdu", title = "V gozdu · In the forest", art = "forest", from = listOf("forest"),
        people = listOf(ScenePerson("luka", "Pastir Luka", "🐑", "shepherd", "path", villager = "luka")),
    )

    private val people = listOf(
        Villager(id = "luka", name = "Pastir Luka", emoji = "🐑", art = "shepherd", voice = "male"),
        Villager(id = "marko", name = "Vinar Marko", emoji = "🍇", art = "winemaker", voice = "male"),
    )

    private fun controller() = SceneController(
        context, scope, bridge = { null },
        pay = { key, _, _, _ -> paid += key; null },
        stepPlayed = { id -> played += id; ChallengeResult(true, emptyMap(), emptyMap(), emptyList(), "🌲 done") to null },
    )

    @Before fun maypole() {
        Cultures.use(Cultures.default().withProjectSteps(mapOf("mlaj" to steps)))
    }

    @After fun back() {
        Cultures.forget()
        Cultures.use(Cultures.DEFAULT)
        scope.cancel()
    }

    @Test fun `in its scene its people on the stage, a tap turn tapped in the picture, the step done at the end`() {
        val c = controller()
        val spec = Projects.spec("mlaj")!!
        assertTrue(c.startStep(spec, 0, forest, null, people))
        assertNotNull(c.talk)
        val t = c.talk!!
        assertEquals("v-gozdu", t.sceneId)
        assertEquals("project:mlaj/1", t.key)
        assertEquals("luka", t.person.id)
        val st = t.step!!
        assertFalse(st.over)
        // Luka where the forest has him, Marko on its next spot
        assertEquals(listOf("luka" to "path", "marko" to "clearing"), st.cast.map { it.id to it.slot })
        // the tap turn is answered in the picture: the spruce, after a tap on the tree (a mistake, its reaction)
        assertEquals(TurnMode.TAP, t.run.mode)
        assertEquals(setOf("spruce", "tree"), t.run.taps.toSet())
        c.tap("tree")
        assertEquals(1, c.talk!!.run.mistakes)
        assertEquals("Drevo?", c.talk!!.run.said.last().sl)
        c.tap("spruce")
        assertEquals("Bravo!", c.talk!!.run.said.last().sl)
        c.next()
        assertEquals("marko", c.talk!!.run.said.last().who)
        assertTrue(played.isEmpty())
        c.choose(c.talk!!.run.choices.indexOfFirst { it.ok })
        val end = c.talk!!
        assertEquals(DialogRun.Step.END, end.run.step)
        assertTrue(end.settled)
        assertEquals(listOf("mlaj"), played)
        assertTrue("nothing paid as a happening", paid.isEmpty())
        assertTrue(end.step!!.result!!.won)
    }

    @Test fun `over the village the same people, the tap turn chosen`() {
        val c = controller()
        val spec = Projects.spec("mlaj")!!
        assertTrue(c.startStep(spec, 0, null, setOf("luka"), people))
        val t = c.talk!!
        assertTrue(t.step!!.over)
        assertEquals("project:mlaj/1", t.sceneId)
        // Marko isn't here: Luka says his lines
        assertEquals(listOf("luka"), t.step!!.cast.map { it.id })
        assertEquals(TurnMode.CHOOSE, t.run.mode)
        assertTrue(t.run.taps.isNotEmpty()) // the turn's places, but nothing to tap them in: chosen
        // a step without a dialog of its own isn't started here: it is its practice
        assertFalse(c.startStep(spec, 1, null, null, people))
    }
}
