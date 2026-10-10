package si.lanisce.lani.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.culture.Culture
import si.lanisce.lani.game.culture.CultureError
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.scene.DialogRun

/**
 * A village project's steps as short scenes (companion/SCENES.md "Project steps"): read with the culture pack (the test
 * pack's tinyland/project-steps), the step's dialog at the learner's level in their pair, who says a helper's lines when
 * they're away, where the step's people stand in its scene, where it is played, and the step done once it is played to its
 * end; a step without a scene of its own stays a practice.
 */
class ProjectStepsTest {
    private val pair = L10n.pair

    @After fun back() {
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = pair
    }

    private fun tiny(edit: (String, String) -> String = { _, t -> t }): Culture =
        Cultures.parse("tinyland") { name -> javaClass.getResourceAsStream("/cultures-test/tinyland/$name")?.use { edit(name, it.readBytes().decodeToString()) } }

    private fun italian(): ProjectSpec {
        Cultures.use(tiny())
        L10n.pair = LangPair(Lang.IT, Lang.EN)
        return Projects.spec("mlaj")!!
    }

    private fun villager(id: String, art: String) = Villager(id = id, name = id.replaceFirstChar { it.uppercase() }, art = art)

    private fun dialog(raw: String): Dialog = json.decodeFromString(Dialog.serializer(), raw)

    /** A step of a made-up project: Luka leads, a boy and a man help; the boy speaks, then the man. */
    private val spoken = dialog(
        """{"id": "d", "lines": [
          {"who": "luka", "sl": "Gremo!", "en": "Let's go!"},
          {"who": "nejc", "sl": "Tukaj je vrv.", "en": "Here is the rope."},
          {"choices": [{"sl": "Primi vrv!", "en": "Grab the rope!", "ok": true, "reply": {"sl": "Ja!", "en": "Yes!"}}, {"sl": "Primi kruh!", "en": "Grab the bread!", "why": "Kruh is bread.", "reply": {"sl": "Kruh?", "en": "Bread?"}}]},
          {"who": "marko", "sl": "Vlecite!", "en": "Pull!"},
          {"choices": [{"sl": "Vlečemo!", "en": "We're pulling!", "ok": true}, {"sl": "Spimo!", "en": "We're sleeping!", "why": "Spimo is we sleep."}]}
        ]}""",
    )

    private val project = Projects.spec("mlaj")!!.copy(helpers = listOf("nejc", "marko", "tine"))

    private val people = listOf(
        villager("luka", "shepherd"), villager("marko", "winemaker"), villager("nejc", "child1"), villager("tine", "child3"),
        villager("zala", "child2"), villager("micka", "grandma"), villager("ana", "woman"),
    )

    @Test fun `a step's scene is read with the pack, a step without one stays a practice`() {
        val spec = italian()
        assertEquals("progetto-albero", spec.pack)
        val play = spec.steps[0].play
        assertNotNull(play)
        play!!
        assertEquals(listOf("abete", "ascia"), play.words)
        assertEquals(setOf("A1", "A2"), play.levels.keys)
        assertNull(play.scene) // over the village
        for (i in 1 until spec.steps.size) assertNull("step ${i + 1} is a practice", Projects.play(spec, i))
        // and Primorska without its files: every step a practice, as before
        val bare = Cultures.parse("primorska") { name -> if (name.startsWith("project-steps/")) null else javaClass.getResourceAsStream("/cultures/primorska/$name")?.use { it.readBytes().decodeToString() } }
        assertTrue(bare.projects.all { p -> p.pack == null && p.steps.all { it.play == null } })
    }

    @Test fun `a file in another language, or for another project, is left out and said why`() {
        val problems = ArrayList<String>()
        val was = Cultures.onProblem
        Cultures.onProblem = { e: CultureError -> problems += e.problems }
        try {
            val c = tiny { name, t -> if (name == "project-steps/mlaj.json") t.replace("\"language\": \"it\"", "\"language\": \"sl\"") else t }
            assertTrue(c.projects.single { it.id == "mlaj" }.steps.all { it.play == null })
            assertTrue(problems.toString(), problems.any { "tinyland/project-steps/mlaj.json" in it && "language \"sl\"" in it })
        } finally {
            Cultures.onProblem = was
        }
    }

    @Test fun `the step's dialog at the learner's level, read in their pair`() {
        val spec = italian()
        val a1 = Projects.dialog(spec, 0, "A1", "it")!!
        assertEquals("project:mlaj/1", a1.id)
        assertEquals("nino", a1.lines[0].who)
        assertEquals("Oggi scegliamo un abete.", a1.lines[0].sl) // what is said, in the village's language
        assertEquals("Today we choose a fir.", a1.lines[0].en) // what it means, in the learner's base
        assertEquals("Ci serve l'abete più alto del bosco.", Projects.dialog(spec, 0, "A2", "it")!!.lines[0].sl)
        // above the levels it has: its highest; a step without a dialog has none
        assertEquals("Ci serve l'abete più alto del bosco.", Projects.dialog(spec, 0, "B1", "it")!!.lines[0].sl)
        assertNull(Projects.dialog(spec, 1, "A1", "it"))
        assertEquals(listOf("nino", "pina"), Projects.speakers(spec, a1))
        // played to its end with the right answers, the reactions in between
        var run = DialogRun.start(a1, "nino", repliers = Projects.repliers(a1, "nino"))
        while (run.step != DialogRun.Step.END) {
            run = if (run.step == DialogRun.Step.LISTEN) run.next() else run.choose(run.choices.indexOfFirst { it.ok })
        }
        assertEquals(0, run.mistakes)
        assertEquals("Eccola!", run.said.last().sl)
    }

    @Test fun `who says a helper's lines when they're away`() {
        // everyone here: as the file has it
        val all = Projects.cast(project, spoken, null, people)
        assertEquals(mapOf("luka" to "luka", "nejc" to "nejc", "marko" to "marko"), all)
        // the boy away: the other boy of the project's helpers says his lines; the man here, his own
        val noNejc = Projects.cast(project, spoken, setOf("luka", "marko", "tine", "micka"), people)
        assertEquals("tine", noNejc["nejc"])
        assertEquals("marko", noNejc["marko"])
        // no other child among the helpers: a child of the village; no man helper: an adult of the village
        val few = Projects.cast(project, spoken, setOf("luka", "zala", "micka"), people)
        assertEquals("zala", few["nejc"])
        assertEquals("micka", few["marko"])
        // nobody else here: the leader says them
        val alone = Projects.cast(project, spoken, setOf("luka"), people)
        assertEquals(mapOf("luka" to "luka", "nejc" to "luka", "marko" to "luka"), alone)
        // the dialog as said, and who answers each turn: whoever spoke last before it
        val said = Projects.recast(spoken, noNejc)
        assertEquals(listOf("luka", "tine", null, "marko", null), said.lines.map { it.who })
        assertEquals(mapOf(2 to "tine", 4 to "marko"), Projects.repliers(said, "luka"))
        assertEquals(listOf("luka", "tine", "marko"), Projects.speakers(project, said))
    }

    @Test fun `where the step's people stand in its scene, and where it is played`() {
        val forest = SceneSpec(
            id = "v-gozdu", title = "V gozdu · In the forest", art = "forest", from = listOf("forest"),
            people = listOf(ScenePerson("luka", "Pastir Luka", "🐑", "shepherd", "path", villager = "luka"), ScenePerson("otroci", "Otroci", "🧒", "child2", "clearing", villager = "zala")),
        )
        val placed = Projects.placed(forest, listOf("luka", "marko", "nejc"), people)
        // Luka where the scene has him, the others on the art's free spots in order (the forest: path, clearing, highseat)
        assertEquals(listOf("path", "clearing", "highseat"), placed.map { it.slot })
        assertEquals(listOf("shepherd", "winemaker", "child1"), placed.map { it.art })
        assertEquals("marko", placed[1].villager)
        // a scene of the village when it is open, in the village's language; else over the village
        val square = SceneSpec(id = "na-vasi", title = "Na vasi", art = "square", from = listOf("lipa", "well"), needs = "vas")
        val play = si.lanisce.lani.game.culture.StepPlay(scene = "na-vasi")
        val zaselek = GameState(age = Age.ZASELEK, buildings = listOf(Building("b0", BuildingType.LIPA, 0)))
        val vas = zaselek.copy(age = Age.VAS)
        assertNull(Projects.sceneOf(play, listOf(square), zaselek, "sl"))
        assertEquals("na-vasi", Projects.sceneOf(play, listOf(square), vas, "sl")?.id)
        assertNull(Projects.sceneOf(play, listOf(square.copy(language = "it")), vas, "sl"))
        assertNull(Projects.sceneOf(play, emptyList(), vas, "sl"))
        assertNull(Projects.sceneOf(si.lanisce.lani.game.culture.StepPlay(), listOf(square), vas, "sl"))
    }

    @Test fun `played to its end, the step is done, as a passed practice does it`() {
        val who = listOf("luka", "marko", "nejc", "tine")
        val s = GameState(
            age = Age.ZASELEK, resources = Res.entries.associateWith { 400 }, residents = who.map { Resident(it, "2026-08-01") }, villagers = who.size,
            help = 20, lastTick = day0.toString(),
        )
        val o = Projects.option(s, Projects.spec("mlaj")!!, day0)
        val (n, r) = Projects.stepPlayed(s, "mlaj", day0, noon(day0))
        assertTrue(r.message, r.won)
        assertEquals(1, n.projects["mlaj"])
        assertEquals(day0.toString(), n.projectDay)
        assertEquals(400 - o.cost.getValue(Res.WOOD), n.res(Res.WOOD))
        assertTrue(n.log.any { it.text.startsWith("Luka in fantje so v gozdu izbrali visoko smreko.") })
        assertEquals(Catalog.PROJECT_STEP_POINTS, n.bonds["luka"]?.points)
        // once a day: the next is lost, nothing paid
        val (again, r2) = Projects.stepPlayed(n, "mlaj", day0, noon(day0))
        assertFalse(r2.won)
        assertEquals(n, again)
        // the stores emptied meanwhile: not done, nothing taken
        val poor = s.copy(resources = Res.entries.associateWith { 10 })
        val (same, r3) = Projects.stepPlayed(poor, "mlaj", day0, noon(day0))
        assertFalse(r3.won)
        assertEquals(poor, same)
        // a practice still passes or fails by its answers
        assertFalse(Projects.finishStep(s, "mlaj", 1, 5, day0, noon(day0)).second.won)
    }
}
