package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.game.render.TownClock
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
 * - "bubbles:<n>": the village shows only the n bubbles nearest the fire ([fewer]), for a recording that isn't crowded;
 *   "bubbles:" shows them all again.
 * - "ispy:<scene>/<slot>": «Vidim, vidim» is offered in that scene whatever the day (games played, the hour: a child comes
 *   by if none is there), and its first round spies [slot]; the debug build logs where the round's things are on screen
 *   (tag ISpy), so QA taps them. "ispy:" lets it go again.
 * - "clock:<f>": the towns' and scenes' time runs at f of the wall's (0.05 to 1; [TownClock.rate]), so a slow emulator
 *   renders every frame of a recording that is sped up again afterwards (with the animator duration scale at 1/f for
 *   the camera's flights and the bubbles' bob); "clock:" lets it run at its pace again.
 * - "step:<project>/<n>": the village (the dev bridge's copy) gets ready for step n of that project today ([readyStep]:
 *   its steps before it done, none today yet, the stores and the 🤝 it costs, a treat for a feast), and the projects sheet
 *   opens at it ([project]): QA plays it from there (companion/SCENES.md, "Project steps"). "step:" lets it go again.
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

    /** «Vidim, vidim» QA offers ("scene/slot": the scene, the thing its first round spies), or null. */
    var ispy by mutableStateOf<String?>(null)
        private set

    /** The project QA readied a step of ("step:<project>/<n>"): the projects sheet opens at it; null: none. */
    var project by mutableStateOf<String?>(null)
        private set

    /** How many bubbles the village shows while QA asks for fewer ("bubbles:<n>"), or null: all of them. */
    var bubbles by mutableStateOf<Int?>(null)
        private set

    /** Does what [command] asks; [road] gets what is asked of the road ("mini", "car"); [forms] starts the forms' review. */
    fun handle(command: String?, road: (String) -> Unit = {}, forms: () -> Unit = {}, step: (String, Int) -> Unit = { _, _ -> }) {
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
            command.startsWith("ispy:") -> ispy = command.removePrefix("ispy:").takeIf { '/' in it }
            command.startsWith("bubbles:") -> bubbles = command.removePrefix("bubbles:").toIntOrNull()?.coerceAtLeast(0)
            command.startsWith("clock:") -> TownClock.rate = command.removePrefix("clock:").toFloatOrNull()?.coerceIn(0.05f, 1f) ?: 1f
            command.startsWith("step:") -> {
                val arg = command.removePrefix("step:")
                val n = arg.substringAfter('/', "").toIntOrNull()
                project = arg.substringBefore('/').takeIf { it.isNotBlank() && n != null }
                project?.let { step(it, n!!) }
            }
        }
    }

    /** The mastery a dialog's turns are asked by: [own] (the learner's), or secure for every rule while QA asks for typed turns. */
    fun mastery(own: (String) -> Mastery?): (String) -> Mastery? = if (enabled && typedTurns) { _ -> Mastery.SECURE } else own

    /** [items] (the village's bubbles) as shown: all, or while QA asks for fewer the [bubbles] nearest by [distance], in their order. */
    fun <T> fewer(items: List<T>, distance: (T) -> Float): List<T> {
        val n = bubbles?.takeIf { enabled } ?: return items
        val keep = items.indices.sortedBy { distance(items[it]) }.take(n).toSet()
        return items.filterIndexed { i, _ -> i in keep }
    }

    /**
     * [s] ready for step [n] (1: the first) of project [id] [today]: its steps before it done, no step today yet, the stores,
     * the 🤝 and (the feast) a treat it costs; the leader must live here already. A debug build's, on the dev bridge's copy.
     */
    fun readyStep(s: si.lanisce.lani.game.GameState, id: String, n: Int, today: java.time.LocalDate): si.lanisce.lani.game.GameState {
        val spec = si.lanisce.lani.game.Projects.spec(id) ?: return s
        val i = (n - 1).coerceIn(0, spec.steps.size - 1)
        val step = spec.steps[i]
        val cost = si.lanisce.lani.game.Catalog.stepCost(spec.age, step.kind)
        var r = s.copy(projects = s.projects + (id to i), projectDay = "", resources = s.resources + cost.mapValues { (res, c) -> maxOf(s.res(res), c) })
        if (step.help) r = r.copy(help = maxOf(r.help, si.lanisce.lani.game.Catalog.stepHelp(spec.age)))
        if (step.kind == si.lanisce.lani.game.StepKind.FEAST && si.lanisce.lani.game.Projects.treat(r) == null) {
            si.lanisce.lani.game.Catalog.goods.values.firstOrNull { it.food && !it.rare }?.let { g -> r = si.lanisce.lani.game.Chest.add(r, g.id, 1) }
        }
        return r
    }

    /** [active] with the happening QA counts as on first, when it is one of [scenes]' and isn't on already. */
    fun withForced(scenes: List<SceneSpec>, active: List<ActiveHappening>, key: String? = happening): List<ActiveHappening> {
        if (!enabled || key == null || active.any { it.key == key }) return active
        val s = scenes.firstOrNull { it.id == key.substringBefore('/') } ?: return active
        val h = s.happenings.firstOrNull { it.id == key.substringAfter('/') } ?: return active
        return listOf(ActiveHappening(s, h, s.people.firstOrNull { it.id == h.who })) + active
    }
}
