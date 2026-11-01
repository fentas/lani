package si.lanisce.lani.app

import si.lanisce.lani.audio.DayAudio
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Speaker
import si.lanisce.lani.data.VoiceProfile
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.DialogVariants
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.ui.scene.SceneWords
import si.lanisce.lani.ui.villagers.VillagerLogic
import java.time.LocalDateTime

/**
 * A visit's first lines, got onto the phone as it begins ([Clips.ahead]; companion/README.md "Voices on a visit"), so the
 * host's clips of them play at once instead of waiting for the host (up to 4 s) when they are said: everyone out in the town
 * greets ("Dober dan!" over someone tapped in a place, in their voice) and says hello on their card (a guest's greeting:
 * [VillagerLogic.greeting] with no bond), then the first lines of today's dialogs for guests in its places, each in the
 * voices [Speaker.say] asks for them (someone's own voice first, else their archetype's; [Speaker.voices]). Best first, at
 * most [Clips.AHEAD_MAX], each once.
 */
object VisitAhead {
    /** Lines of each dialog got ahead: what is said before the learner's first turn, usually one or two. */
    private const val DIALOG_LINES = 2

    /**
     * The lines of [visit] at [now]: its people's greetings ([people]: who is out, [TownVisit.people]), then its dialogs'
     * first lines ([active]: today's happenings for guests, [Happenings.active]), each in its voices ([profiles]: the
     * town's people's, as the node says them).
     */
    fun lines(
        people: List<Villager>,
        active: List<ActiveHappening>,
        state: GameState?,
        cast: List<Villager>,
        profiles: Map<String, VoiceProfile>,
        now: LocalDateTime,
    ): List<Pair<String, List<String>>> {
        val today = now.toLocalDate()
        fun voicesOf(v: Villager): List<String> = Speaker.voices(v.speakerVoice, v.voice, profiles[v.id]).voices
        val out = mutableListOf<Pair<String, List<String>>>()
        fun add(text: String?, voices: List<String>) {
            DayAudio.want(text, voices, "visit")?.let { out += it.text to it.voices }
        }
        // the greeting of the hour first (said by itself over someone tapped in a place), then their card's
        val hello = "${si.lanisce.lani.ui.greeting(now.hour)}!"
        for (v in people) add(hello, voicesOf(v))
        for (v in people) {
            val said = runCatching { VillagerLogic.greeting(v, Bond(), today, TimeOfDay.of(now.hour)) }.getOrDefault(emptyList())
            for (l in said) add(l.sl, voicesOf(v))
        }
        if (state != null) for (a in active) {
            val byId = a.scene.people.associateBy { it.id }
            fun voices(who: String?): List<String> {
                val p = who?.let(byId::get) ?: return Clips.chain()
                val v = p.villager?.let { id -> cast.firstOrNull { it.id == id } }
                return v?.let(::voicesOf) ?: Clips.chain(SceneWords.voiceOf(p.art))
            }
            val lines = runCatching { DialogVariants.play(a, state, today)?.second?.lines }.getOrNull().orEmpty()
            // what is said before the learner's first turn
            for (l in lines.takeWhile { it.choices.isEmpty() }.filter { it.who != null }.take(DIALOG_LINES)) add(l.sl, voices(l.who))
        }
        return out.distinctBy { (t, v) -> "${si.lanisce.lani.data.Voice.normalize(t)}|${v.firstOrNull()}" }.take(Clips.AHEAD_MAX)
    }

    /** [lines] for a visit as it is now: who is out, today's dialogs for guests in its places ([scenes]). */
    fun lines(visit: TownVisit, scenes: List<SceneSpec> = visit.scenes, profiles: Map<String, VoiceProfile>, now: LocalDateTime = LocalDateTime.now()): List<Pair<String, List<String>>> {
        val s = visit.state
        val active = s?.let { runCatching { Happenings.active(scenes, it, now, guest = true, cast = visit.cast) }.getOrNull() }.orEmpty()
        return lines(visit.people(now.toLocalDate()), active, s, visit.cast, profiles, now)
    }
}
