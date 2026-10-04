package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.SceneSpec

/**
 * What the end-to-end QA (companion/bin/qa) asks of a debug build through the launch intent's [EXTRA]; a release build
 * ignores it ([enabled] is [BuildConfig.DEBUG]). QA runs the app against its dev bridge, on a copy of the learner data.
 *
 * - "zoom": the camera on screen (the village's, a scene's) zooms in, as its "Približaj · Zoom in" action does: a double
 *   tap sent through adb comes too slow on a busy machine, and adb can't reach a TalkBack action.
 * - "happening:<scene>/<happening>": that happening counts as on ([withForced]), so its deep link ("scene:<scene>@<key>")
 *   opens its dialog whatever the hour, the day's dice or who has been met; "happening:" alone lets it go again.
 * - "road:<what>": asked of the road (the car's "🚗 Za pot"): "road:mini" gets a small library ready (a couple of items of
 *   each kind, in a minute), "road:car" browses and plays the sessions as Android Auto does (road/RoadCarCheck).
 * - "forms": a review of the word cards that have a form reached starts (due or not), and it and the next reviews ask
 *   a form of every such card, familiar or not (companion/GAME.md, "A word's forms"); "forms:off" ends it.
 * - "turns:type": the dialogs opened from now on ask every turn that tests a form typed into its gap, as if the learner
 *   had each rule secure ([typedTurns]; companion/SCENES.md, "Adaptive turns"): QA's typed turn with the keyboard up;
 *   "turns:" lets the learner's own masteries decide again.
 */
object QaHooks {
    const val EXTRA = "si.lanisce.lani.qa"

    /** Whether the hooks answer: in a debug build only. */
    var enabled = BuildConfig.DEBUG

    /** How many zooms QA asked for: a camera on screen zooms in once for each one asked while it shows. */
    var zooms by mutableIntStateOf(0)
        private set

    /** Every word card with a form reached asks one in the next reviews ([FormsController.questions]). */
    var forms by mutableStateOf(false)
        private set

    /** The happening QA counts as on ("scene/happening"), or null. */
    var happening by mutableStateOf<String?>(null)
        private set

    /** QA asked for typed turns ("turns:type"): every rule of a dialog's form turns counts as secure. */
    var typedTurns = false
        private set

    /** Does what [command] asks; [road] gets what is asked of the road ("mini", "car"); [forms] starts the forms' review. */
    fun handle(command: String?, road: (String) -> Unit = {}, forms: () -> Unit = {}) {
        if (!enabled || command.isNullOrBlank()) return
        when {
            command == "zoom" -> zooms++
            command.startsWith("happening:") -> happening = command.removePrefix("happening:").takeIf { '/' in it }
            command.startsWith("road:") -> command.removePrefix("road:").takeIf { it.isNotBlank() }?.let(road)
            command == "forms" -> {
                this.forms = true
                forms()
            }
            command == "forms:off" -> this.forms = false
            command.startsWith("turns:") -> typedTurns = command == "turns:type"
        }
    }

    /** The mastery a dialog's turns are asked by: [own] (the learner's), or secure for every rule while QA asks for typed turns. */
    fun mastery(own: (String) -> Mastery?): (String) -> Mastery? = if (enabled && typedTurns) { _ -> Mastery.SECURE } else own

    /** [active] with the happening QA counts as on first, when it is one of [scenes]' and isn't on already. */
    fun withForced(scenes: List<SceneSpec>, active: List<ActiveHappening>, key: String? = happening): List<ActiveHappening> {
        if (!enabled || key == null || active.any { it.key == key }) return active
        val s = scenes.firstOrNull { it.id == key.substringBefore('/') } ?: return active
        val h = s.happenings.firstOrNull { it.id == key.substringAfter('/') } ?: return active
        return listOf(ActiveHappening(s, h, s.people.firstOrNull { it.id == h.who })) + active
    }
}
