package si.lanisce.lani.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.app.SceneTalk
import si.lanisce.lani.app.StepTalk
import si.lanisce.lani.data.Clips
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.game.villagers.Villager
import java.io.File

/**
 * Every line of a scene's happening dialog shows and sounds like its own speaker (docs/plans/05-built/scene-speakers.md):
 * the panel's cast and speakers ([DialogCast]), each line's portrait, name and voice ([whoIs], [Speaking]), whom the
 * header and the picture face ([DialogCast.facing], as [DialogRun.facing]), and someone the scene doesn't have.
 */
class DialogCastTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "scenes").isDirectory }
    private val gate = parseScene(File(companion, "scenes/pri-vratih.json").readText())
    private val zapah = gate.dialogs.first { it.id == "zapah-nejc" }
    private val joze = gate.people.first { it.id == "joze" }
    private fun talk(d: Dialog = zapah, run: DialogRun = DialogRun.start(d, joze.id), person: ScenePerson = joze) =
        SceneTalk(gate.id, "${gate.id}/zapah", person, gate.happenings.first { it.id == "zapah" }, d, run)

    /** Each voice by who they are: a villager's speaker, else the sprite's. */
    private fun voiceOf(p: ScenePerson) = SceneWords.voiceOf(p.art, p.villager?.let { Villager(it, p.name, art = p.art, voice = "male", speaker = "v-$it") })

    @Test fun `a happening's cast is its person and everyone its lines name, as the scene has them`() {
        val cast = DialogCast.of(talk(), gate.people) { null }
        assertEquals(listOf("joze", "nejc"), cast.map { it.id })
        assertEquals("Nejc as the scene has him: his sprite", "child1", cast[1].art)
        assertEquals(listOf("nejc", "joze"), DialogCast.ids(zapah))
    }

    @Test fun `each line's portrait, name and voice are its speaker's`() {
        val t = talk()
        val speakers = DialogCast.speakers(DialogCast.of(t, gate.people) { null }, ::voiceOf)
        val nejc = whoIs(t, speakers, "nejc")
        assertEquals("Nejc", nejc.name)
        assertEquals("child1", nejc.art)
        assertEquals("his own voice, not Jože's", Spoken("v-nejc", "male", "nejc"), speakers.getValue("nejc").voice)
        assertEquals(joze.name, whoIs(t, speakers, "joze").name)
        assertEquals(Spoken("v-joze", "male", "joze"), speakers.getValue("joze").voice)
        // without the speakers (as before), everyone was Jože
        assertEquals(joze.name, whoIs(t, emptyMap(), "nejc").name)
    }

    @Test fun `the header and the picture face whom the dialog does - the turn's to, else whoever spoke last`() {
        var run = DialogRun.start(zapah, joze.id)
        // Nejc's line comes with the learner's turn, which Jože answers (its to)
        assertEquals(DialogRun.Step.CHOOSE, run.step)
        assertEquals("joze", DialogCast.facing(talk(run = run)))
        run = run.choose(run.choices.indexOfFirst { it.ok })
        // the right answer has no reply: Nejc's own line is next, and he is the one the learner faces
        assertEquals("Zakaj ne? Saj nisem majhen!", run.said.last().sl)
        assertEquals("nejc", run.said.last().who)
        assertEquals("nejc", DialogCast.facing(talk(run = run)))
        run = run.next()
        assertEquals("joze", DialogCast.facing(talk(run = run)))
        // a wrong pick: Jože reacts, and he is faced
        val wrong = run.choices.indexOfFirst { !it.ok }
        assertEquals("joze", DialogCast.facing(talk(run = run.choose(wrong))))
        assertEquals("joze", run.choose(wrong).said.last().who)
    }

    @Test fun `someone the scene doesn't have falls back safely`() {
        val stranger = Dialog(
            "tujec",
            listOf(
                DialogLine(who = "tujec", sl = "Dober večer."),
                DialogLine(who = "nejc", sl = "Kdo je to?"),
                DialogLine(choices = listOf(DialogChoice(sl = "Ne vem.", ok = true), DialogChoice(sl = "Ne vemo.")), to = "joze"),
            ),
        )
        // a villager of the village the scene doesn't place: their sprite in the panel, no place in the picture
        val sepp = Villager("tujec", "Tujec Sepp", art = "pedlar", voice = "male", speaker = "v-tujec")
        val known = DialogCast.of(talk(stranger), gate.people) { if (it == "tujec") sepp else null }
        assertEquals(listOf("joze", "tujec", "nejc"), known.map { it.id })
        assertEquals("pedlar", known[1].art)
        assertEquals("", known[1].slot)
        // nobody knows them: not in the cast; the panel shows the happening's person for them, as it always did
        val unknown = DialogCast.of(talk(stranger), gate.people) { null }
        assertEquals(listOf("joze", "nejc"), unknown.map { it.id })
        val t = talk(stranger)
        assertEquals(joze.name, whoIs(t, DialogCast.speakers(unknown, ::voiceOf), "tujec").name)
        // they spoke last: the header faces them (the happening's person's portrait for them), no one in the picture turns
        val run = DialogRun.start(stranger, joze.id)
        assertTrue(run.said.first().who == "tujec")
        assertEquals("tujec", DialogCast.facing(talk(stranger, run)))
        assertTrue(gate.people.none { it.id == DialogCast.facing(talk(stranger, run)) })
    }

    @Test fun `a project's step keeps its cast`() {
        val cast = listOf(joze, ScenePerson("tone", "Kovač Tone", "🔨", "smith", "anvil", villager = "tone"))
        val t = talk().copy(step = StepTalk("most", 0, cast, over = false))
        assertEquals(cast, DialogCast.of(t, gate.people) { null })
        assertNull(DialogCast.of(talk(), gate.people) { null }.firstOrNull { it.id == "tone" })
        assertEquals(Clips.MALE, SceneWords.voiceOf("smith"))
    }
}
