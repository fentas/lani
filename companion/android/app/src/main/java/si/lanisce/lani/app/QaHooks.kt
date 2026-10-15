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
 * What the end-to-end QA (companion/bin/qa) asks of a debug build (or of the qa build: the release build's R8 with the
 * hooks, qa --minified) through the launch intent's [EXTRA]; a release build ignores it ([enabled] is [BuildConfig.QA]).
 * QA runs the app against its dev bridge, on a copy of the learner data.
 *
 * - "zoom": the camera on screen (the village's, a scene's) zooms in, as its "Približaj · Zoom in" action does: a double
 *   tap sent through adb comes too slow on a busy machine, and adb can't reach a TalkBack action.
 * - "happening:<scene>/<happening>": that happening counts as on ([withForced]), so its deep link ("scene:<scene>@<key>")
 *   opens its dialog whatever the hour, the day's dice or who has been met; "happening:<scene>/<happening>/<dialog>" with
 *   that variant of its dialogs, whatever was heard lately; "happening:" alone lets it go again.
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
 * - "audio:<what>": the offline audio (companion/README.md, "Offline on the phone"): "audio:prefetch" makes the day's plan
 *   now and gets its clips at once; "audio:voice" downloads the offline voice; "audio:say:<text>" and "audio:slow:<text>"
 *   say a text as a 🔊 does (normal, 🐢), so QA hears the offline voice with the network off ("audio:say:<text>|<voice>|<person>"
 *   as a villager's line: that person in that voice, the debug build logging how it was said, tag Speaker); "audio:revoice:<text>|<url>"
 *   gives the text's narrator clip a new URL (url, else one the node hasn't got) as a re-voice on the node does, so its
 *   earlier clip stands in; "audio:refresh" takes the node's index again.
 * - "clock:<f>": the towns' and scenes' time runs at f of the wall's (0.05 to 1; [TownClock.rate]), so a slow emulator
 *   renders every frame of a recording that is sped up again afterwards (with the animator duration scale at 1/f for
 *   the camera's flights and the bubbles' bob); "clock:" lets it run at its pace again.
 * - "step:<project>/<n>": the village (the dev bridge's copy) gets ready for step n of that project today ([readyStep]:
 *   its steps before it done, none today yet, the stores and the 🤝 it costs, a treat for a feast), and the projects sheet
 *   opens at it ([project]): QA plays it from there (companion/SCENES.md, "Project steps"). "step:" lets it go again.
 * - "season:<day>": the landmarks with a season (the maypole, game/ProjectSeasons) stand or not as on that day
 *   ("2027-05-10", or "05-10": the next 10 May from today; [seasonDay]), on the map and in the scenes, without a line in
 *   the chronicle; the rest of the game keeps today. "season:" goes by today again.
 * - "hour:<h>": a scene is seen at that hour of today (0 to 24, "hour:7.5", to the second; [sceneNow]): its picture's light, sky and wild
 *   animals, who is there and who sleeps, without the real night sky; so a dawn's or a morning's cue looks as it does then,
 *   whatever the hour QA runs at. The village's map too ([mapNow]): its light, who is where and doing what (their day,
 *   what's on), the clock standing at that minute. "hour:" goes by the clock again.
 * - "build:<type>": the village (the dev bridge's copy) gets ready to build a building of that type ([readyBuild]: its
 *   stores full, a free plot, the next age's when its own are all taken) and the plot chooser opens for it over the map
 *   ([build], taken by the village: [tookBuild]), with its ★; QA looks at the HUD minimized over it and goes back,
 *   nothing built. "build:" lets it go.
 * - "chat:<text>": a tutor message comes into the chat on the phone (as a reply would, not sent anywhere; "\n" in [text]
 *   a new line), "chat:table" the tutor's imeti with its list ([TUTOR_TABLE]): QA pins it to the cheat sheet.
 * - "cheat" opens the cheat sheet; "cheat:<page>,<page>" as an exercise of those grammar pages would (their cards first).
 * - "school": the village (the dev bridge's copy) gets ready for the class under the linden ([readySchool]: the hamlet,
 *   the linden standing, the unit biti as never begun), and the school opens, its class begun (companion/GAME.md, "The
 *   school").
 * - "counting": counting cards (data/Counting.kt): today's asked of the dev bridge now, then a review of a few of them, due
 *   or not; "counting:type" types their steps from then on, as if each card were secure ([countingTyped]), "counting:off"
 *   lets the cards' own reviews decide again; "counting:intro" readies the class (as "school") with the unit stevila as
 *   never begun ([readyCounting]: counting off) and opens its intro, whose end ("⏭ Poznam to") turns counting on.
 * - "shadow": the developer settings shown and the shadow tutor's debug panel open (tutor/ShadowTutor);
 *   "shadow:<url>" with the Messages API's base URL set to <url> first (QA's mock, http://10.0.2.2:8771). The key is
 *   entered in the panel, as the learner would.
 */
object QaHooks {
    const val EXTRA = "si.lanisce.lani.qa"

    /** Whether the hooks answer: in a debug or qa build only. */
    var enabled = BuildConfig.QA

    /** How many zooms QA asked for: a camera on screen zooms in once for each one asked while it shows. */
    var zooms by mutableIntStateOf(0)
        private set

    /** Every word card with a form reached asks one in the next reviews ([FormsController.questions]). */
    var forms by mutableStateOf(false)
        private set

    /** The happening QA counts as on ("scene/happening", or "scene/happening/dialog" with that variant), or null. */
    var happening by mutableStateOf<String?>(null)
        private set

    /** QA asked for typed turns ("turns:type"): every rule of a dialog's form turns counts as secure. */
    var typedTurns = false
        private set

    /** QA asked for typed counting cards ("counting:type"): every counting card's steps are typed. */
    var countingTyped = false
        private set

    /** «Vidim, vidim» QA offers ("scene/slot": the scene, the thing its first round spies), or null. */
    var ispy by mutableStateOf<String?>(null)
        private set

    /** The project QA readied a step of ("step:<project>/<n>"): the projects sheet opens at it; null: none. */
    var project by mutableStateOf<String?>(null)
        private set

    /** The hour of today QA asks the scenes and the village's map to be seen at ("hour:<h>"), or null: the clock's. */
    var sceneHour by mutableStateOf<Float?>(null)
        private set

    /** How many bubbles the village shows while QA asks for fewer ("bubbles:<n>"), or null: all of them. */
    var bubbles by mutableStateOf<Int?>(null)
        private set

    /** The building QA asked the plot chooser for ("build:<type>"), until the village opens it ([tookBuild]); null: none. */
    var build by mutableStateOf<si.lanisce.lani.game.BuildingType?>(null)
        private set

    /** A tutor message with a list, as the tutor explains imeti ("chat:table"): QA pins it to the cheat sheet. */
    const val TUTOR_TABLE = "**Imeti** – the whole verb:\n" +
        "- jaz **imam** · ti **imaš** · on/ona **ima**\n" +
        "- midva **imava** · vidva/onadva **imata**\n" +
        "- mi **imamo** · vi **imate** · oni **imajo**\n\n" +
        "*Vida ima gostilno.* (one) · *Vida in Nejc imata konja.* (two) · *Vsi imajo čas.* (three+)\n\n" +
        "The two-person endings are the same everywhere: **-va** (me + one more), **-ta** (two others), like *sva / sta* with biti."

    /** The village opened the plot chooser QA asked for. */
    fun tookBuild() { build = null }

    /** Does what [command] asks; [road] gets what is asked of the road ("mini", "car"); [forms] starts the forms' review. */
    fun handle(
        command: String?, road: (String) -> Unit = {}, forms: () -> Unit = {}, step: (String, Int) -> Unit = { _, _ -> }, audio: (String) -> Unit = {},
        season: () -> Unit = {}, ready: () -> Unit = {}, chat: (String) -> Unit = {}, cheat: (List<String>) -> Unit = {}, school: () -> Unit = {},
        shadow: (String) -> Unit = {}, counting: (String) -> Unit = {},
    ) {
        if (!enabled || command.isNullOrBlank()) return
        when {
            command == "shadow" -> shadow("")
            command.startsWith("shadow:") -> shadow(command.removePrefix("shadow:"))
            command.startsWith("audio:") -> audio(command.removePrefix("audio:"))
            command == "zoom" -> zooms++
            command.startsWith("happening:") -> happening = command.removePrefix("happening:").takeIf { '/' in it }
            command.startsWith("road:") -> command.removePrefix("road:").takeIf { it.isNotBlank() }?.let(road)
            command == "forms" -> {
                this.forms = true
                forms()
            }
            command == "forms:off" -> this.forms = false
            command.startsWith("turns:") -> typedTurns = command == "turns:type"
            command == "counting" -> counting("")
            command == "counting:intro" -> counting("intro")
            command == "counting:type" -> countingTyped = true
            command == "counting:off" -> countingTyped = false
            command.startsWith("ispy:") -> ispy = command.removePrefix("ispy:").takeIf { '/' in it }
            command.startsWith("bubbles:") -> bubbles = command.removePrefix("bubbles:").toIntOrNull()?.coerceAtLeast(0)
            command.startsWith("hour:") -> sceneHour = command.removePrefix("hour:").toFloatOrNull()?.takeIf { it in 0f..24f }
            command.startsWith("clock:") -> TownClock.rate = command.removePrefix("clock:").toFloatOrNull()?.coerceIn(0.05f, 1f) ?: 1f
            command.startsWith("season:") -> {
                si.lanisce.lani.game.ProjectSeasons.qaDay = seasonDay(command.removePrefix("season:"), java.time.LocalDate.now())
                season()
            }
            command.startsWith("step:") -> {
                val arg = command.removePrefix("step:")
                val n = arg.substringAfter('/', "").toIntOrNull()
                project = arg.substringBefore('/').takeIf { it.isNotBlank() && n != null }
                project?.let { step(it, n!!) }
            }
            command == "chat:table" -> chat(TUTOR_TABLE)
            command.startsWith("chat:") -> command.removePrefix("chat:").replace("\\n", "\n").takeIf { it.isNotBlank() }?.let(chat)
            command == "cheat" -> cheat(emptyList())
            command.startsWith("cheat:") -> cheat(command.removePrefix("cheat:").split(',').map { it.trim() }.filter { it.isNotEmpty() })
            command == "school" -> school()
            command.startsWith("build:") -> {
                val name = command.removePrefix("build:").trim().uppercase()
                build = si.lanisce.lani.game.BuildingType.entries.firstOrNull { it.name == name && it.onPlot }
                if (build != null) ready()
            }
        }
    }

    /**
     * [s] ready to build a building QA chooses a plot for ("build:<type>"): the stores full ([caps]), and a free plot: when
     * the age's plots are all taken, the next age (its new plots). A debug build's, on the dev bridge's copy.
     */
    fun readyBuild(s: si.lanisce.lani.game.GameState, caps: Map<si.lanisce.lani.game.Res, Int>): si.lanisce.lani.game.GameState {
        val full = s.copy(resources = s.resources + caps.mapValues { (r, cap) -> maxOf(s.res(r), cap) })
        val plots = si.lanisce.lani.game.PLOTS_PER_AGE[s.age.ordinal]
        val free = (0 until plots).any { p -> s.buildings.none { it.type.onPlot && it.plot == p } }
        val next = si.lanisce.lani.game.Age.entries.getOrNull(s.age.ordinal + 1)
        return if (free || next == null) full else full.copy(age = next)
    }

    /**
     * [s] ready for the class under the linden ("school"): a hamlet at least (the class begins with it), the linden standing
     * (on a free plot of the age; none when they're all taken: the class then meets by the fire). A debug build's, on the
     * dev bridge's copy.
     */
    fun readySchool(s: si.lanisce.lani.game.GameState, now: Long): si.lanisce.lani.game.GameState {
        var r = if (s.age < si.lanisce.lani.game.Age.ZASELEK) s.copy(age = si.lanisce.lani.game.Age.ZASELEK) else s
        if (r.buildings.none { it.type == si.lanisce.lani.game.BuildingType.LIPA }) {
            val plots = si.lanisce.lani.game.PLOTS_PER_AGE[r.age.ordinal]
            val free = (0 until plots).firstOrNull { p -> r.buildings.none { it.type.onPlot && it.plot == p } }
            r = if (free != null) r.copy(buildings = r.buildings + si.lanisce.lani.game.Building("lipa-qa", si.lanisce.lani.game.BuildingType.LIPA, free, builtAt = now))
            else r
        }
        // the unit biti as never begun, whatever the copy's learner did with it: QA plays its intro from its first step
        val course = r.course?.let { c -> c.copy(units = c.units - si.lanisce.lani.game.School.key("sl", "biti")) }
        return r.copy(buildings = r.buildings.map { if (it.type == si.lanisce.lani.game.BuildingType.LIPA) it.copy(damaged = false) else it }, course = course)
    }

    /**
     * [s] with the unit stevila as never begun ("counting:intro"): its intro to do again, counting off until it ends. A
     * debug build's, on the dev bridge's copy.
     */
    fun readyCounting(s: si.lanisce.lani.game.GameState): si.lanisce.lani.game.GameState =
        s.copy(course = s.course?.let { c -> c.copy(units = c.units - si.lanisce.lani.game.School.key(si.lanisce.lani.data.Counting.LANGUAGE, si.lanisce.lani.data.Counting.UNIT)) })

    /** Today at the hour QA asks the village's map to be seen at ("hour:<h>"), or null: the clock's (and the real sky). */
    fun mapNow(): java.time.LocalDateTime? = if (enabled && sceneHour != null) sceneNow(java.time.LocalDateTime.now()) else null

    /** [now], or today at the hour QA asks the scenes to be seen at ("hour:<h>"). */
    fun sceneNow(now: java.time.LocalDateTime): java.time.LocalDateTime {
        val h = sceneHour?.takeIf { enabled } ?: return now
        val s = (h * 3600 + 0.5f).toInt().coerceIn(0, 24 * 3600 - 1)
        return now.toLocalDate().atTime(s / 3600, s / 60 % 60, s % 60)
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

    /**
     * The day "season:<day>" asks for: an ISO date ("2027-05-10"), or a month and day ("05-10"): the next one from [today]
     * on; null for none ("season:") or one that isn't a day.
     */
    fun seasonDay(arg: String, today: java.time.LocalDate): java.time.LocalDate? {
        runCatching { java.time.LocalDate.parse(arg) }.getOrNull()?.let { return it }
        val md = si.lanisce.lani.game.culture.StandsEntry.monthDay(arg) ?: return null
        return md.atYear(today.year).let { if (it.isBefore(today)) md.atYear(today.year + 1) else it }
    }

    /** [active] with the happening QA counts as on first, when it is one of [scenes]' and isn't on already. */
    fun withForced(scenes: List<SceneSpec>, active: List<ActiveHappening>, key: String? = happening): List<ActiveHappening> {
        if (!enabled || key == null) return active
        val (sid, hid, variant) = (key.split('/', limit = 3) + listOf("", "")).take(3)
        // a variant asked for plays even when the happening is on already (with the day's)
        if (variant.isEmpty() && active.any { it.key == "$sid/$hid" }) return active
        val s = scenes.firstOrNull { it.id == sid } ?: return active
        val h = s.happenings.firstOrNull { it.id == hid } ?: return active
        val dialog = variant.takeIf { v -> s.dialogs.any { it.id == v } }
        return listOf(ActiveHappening(s, h, s.people.firstOrNull { it.id == h.who }, dialog = dialog)) + active.filter { it.key != "$sid/$hid" }
    }
}
