package si.lanisce.lani.ui.scene

import si.lanisce.lani.app.SceneTalk
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.villagers.Villager

/**
 * Who speaks in a scene's dialog, as the panel and the picture show them (docs/plans/05-built/scene-speakers.md;
 * companion/SCENES.md "Format", `to`): every line of a happening's dialog shows and sounds like its own speaker, not only
 * the happening's person's. The cast is the happening's person, then everyone its lines name (`who`) and its turns answer
 * (`to`), in the order they first speak: each as the scene has them ([si.lanisce.lani.game.scene.SceneSpec.people], placed
 * in the picture or not), else as a villager of the village (their sprite in the panel, no place in the picture). Someone
 * neither knows isn't in it, and the panel shows the happening's person for them ([whoIs]), as it always did. A project's
 * step brings its cast ([si.lanisce.lani.app.StepTalk.cast]).
 */
object DialogCast {
    /** The ids who speak in [d], in the order they first do: its lines' speakers and the ones its turns answer. */
    fun ids(d: Dialog): List<String> = d.lines.flatMap { listOfNotNull(it.who, it.to) }.distinct()

    /** [talk]'s cast: its person first, then the others who speak ([ids]) found among [people], else by [villager]. */
    fun of(talk: SceneTalk, people: List<ScenePerson>, villager: (String) -> Villager?): List<ScenePerson> {
        talk.step?.let { return it.cast }
        val others = ids(talk.dialog).filter { it != talk.person.id }.mapNotNull { id ->
            people.firstOrNull { it.id == id }
                ?: villager(id)?.let { v -> ScenePerson(v.id, v.name, v.emoji, v.art, slot = "", villager = v.id) }
        }
        return listOf(talk.person) + others
    }

    /**
     * Whom the learner faces now, in the panel's header and in the picture: the one the dialog has ([si.lanisce.lani.ui.scene.DialogRun.facing]:
     * at a turn the one it names, `to`, else whoever spoke last), else the person of the dialog.
     */
    fun facing(talk: SceneTalk): String = talk.run.facing ?: talk.person.id

    /** [cast] as the panel shows and voices them, each with [voiceOf]'s voice: their portrait, name, face and voice. */
    fun speakers(cast: List<ScenePerson>, voiceOf: (ScenePerson) -> Spoken): Map<String, Speaking> =
        cast.associate { p -> p.id to Speaking(p.emoji, voiceOf(p), p.art, (p.villager ?: p.id).hashCode(), p.name) }
}
