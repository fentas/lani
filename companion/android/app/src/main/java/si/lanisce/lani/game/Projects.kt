package si.lanisce.lani.game

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import si.lanisce.lani.data.json
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.StepPlay
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.ISpy
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.inPair
import si.lanisce.lani.game.villagers.Arrivals
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Memory
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.bi
import java.time.LocalDate

/** What a project step needs besides the practice: the materials, by kind (see [Catalog.stepCost]). */
enum class StepKind { WORK, TIMBER, STONE, CRAFT, FEAST }

/**
 * One step of a project: what it asks for ([task], a to-do), and what the chronicle tells once it's done ([line], past
 * tense); both "target · base" from the culture's words ([taskText], [lineText]). [help]: the neighbours come too, it
 * costs 🤝. A [StepKind.FEAST] step (the last) treats the helpers to a good from the chest. [play]: the step as a short
 * scene that does it (the culture's project-steps/, companion/SCENES.md "Project steps"); without one, a practice.
 */
data class ProjectStep(val kind: StepKind, val taskText: Text, val lineText: Text, val help: Boolean = false, val play: StepPlay? = null) {
    val task: String get() = taskText.bi()
    val line: String get() = lineText.bi()
}

/**
 * What the game keeps of a project ([Catalog.projectFrames]): its [age], the [landmark] the map draws at the first of the
 * [site]s that exists (a place key as scenes use it: "lipa", "spot:riverbank", "fire"), the practice ([skill],
 * [topics]), each step's kind and whether the neighbours come ([steps]), and what the finished project adds ([bonus]).
 */
data class ProjectFrame(
    val id: String,
    val landmark: String,
    val age: Age,
    val emoji: String,
    val skill: Res,
    val topics: List<String>,
    val site: List<String>,
    val steps: List<Pair<StepKind, Boolean>>,
    val bonus: ToolEffect,
)

/**
 * "Skupni projekt · A village project" (companion/GAME.md): a community build of its [age], led by villager [leader] (an
 * id), in [steps], one a day: a [ProjectFrame] with the culture's words and people (game/culture, projects.json).
 * [helpers] (ids) grow closer when it's done; [memory] is how the villagers remember it ("naš mlaj · our maypole",
 * fitting "Še vedno mislim na {memory}"). The texts read "target · base"; [short] is the name in the target language.
 */
data class ProjectSpec(
    val id: String,
    val landmark: String,
    val age: Age,
    val leader: String,
    val emoji: String,
    val nameText: Text,
    /** The name inside a sentence: "the maypole". */
    val inText: Text,
    val aboutText: Text,
    val skill: Res,
    val topics: List<String>,
    val site: List<String>,
    val steps: List<ProjectStep>,
    val bonus: ToolEffect,
    val helpers: List<String>,
    val doneText: Text,
    val memoryText: Text,
    /** Where it is, for a place its landmark opens ("v vinogradu · in the vineyard"); null: its name. */
    val whereText: Text? = null,
    /** The word pack of its words ("projekt-mlaj"), which its steps' scenes test; null: none. */
    val pack: String? = null,
) {
    /** "Mlaj · The maypole". */
    val name: String get() = nameText.bi()
    /** "Mlaj". */
    val short: String get() = nameText.target
    val about: String get() = aboutText.bi()
    val done: String get() = doneText.bi()
    val memory: String get() = memoryText.bi()
}

/**
 * A project as the village sees it now: [done] of its steps, the [next] one (null when finished) with what it costs
 * ([cost], [help] 🤝, a [treat] from the chest for the last step), and why it can't be done now ([reason], null when
 * it can). [waiting] marks the reasons that pass by themselves (tomorrow, the leader coming back).
 */
data class ProjectOption(
    val spec: ProjectSpec,
    val done: Int,
    val next: ProjectStep?,
    val cost: Map<Res, Int>,
    val help: Int,
    val treat: GoodSpec?,
    val reason: String?,
    val waiting: Why? = null,
) {
    enum class Why { AGE, TODAY, LEADER, RESOURCES, HELP, TREAT }

    val available: Boolean get() = next != null && reason == null
    val finished: Boolean get() = next == null
    val steps: Int get() = spec.steps.size
}

/**
 * A project's landmark for the map: [stage] steps of [stages] done (1 … stages; the map shows nothing before the first
 * step). Drawn at the first of [site] that exists in the village.
 */
data class Landmark(val id: String, val project: String, val site: List<String>, val stage: Int, val stages: Int) {
    val finished: Boolean get() = stage >= stages
}

/**
 * "Skupni projekti · Village projects": multi-day community builds, one or more per age from Zaselek, each led by a
 * villager who lives here. Each step costs resources (mostly the plentiful 🌾 🪵), sometimes 🤝 or a treat, and a short
 * practice with the leader; the village does one step a day. A finished project adds a small bonus for good and brings
 * its helpers closer. Pure, like the engine.
 */
internal object Projects {
    fun spec(id: String): ProjectSpec? = Catalog.projects.firstOrNull { it.id == id }

    fun done(s: GameState, spec: ProjectSpec): Int = (s.projects[spec.id] ?: 0).coerceIn(0, spec.steps.size)

    fun finished(s: GameState, spec: ProjectSpec): Boolean = done(s, spec) >= spec.steps.size

    /** The projects of the ages reached, in the catalog's order. */
    fun open(s: GameState): List<ProjectSpec> = Catalog.projects.filter { s.age >= it.age }

    /**
     * Whether [spec]'s leader is in the village today and met (everyone is while nobody has a name yet: an older bridge);
     * someone who lives here but hasn't been met leads once they are ([leaderToMeet]).
     */
    fun leaderHere(s: GameState, spec: ProjectSpec, today: LocalDate): Boolean =
        Residents.present(s, today)?.contains(spec.leader) ?: true

    /** Whether [spec]'s leader lives here (or visits) but waits to be met: their introduction is the way on. */
    fun leaderToMeet(s: GameState, spec: ProjectSpec, today: LocalDate): Boolean =
        !leaderHere(s, spec, today) && Residents.living(s, today)?.contains(spec.leader) == true

    /** The village did a project step today already. */
    fun workedToday(s: GameState, today: LocalDate): Boolean = s.projectDay == today.toString()

    /** The treat for a feast step: the food good Jan has most of (a common one first; the cheaper on a tie). */
    fun treat(s: GameState): GoodSpec? = s.chest.goods.filterValues { it > 0 }.keys.mapNotNull { Catalog.goods[it]?.takeIf { g -> g.food } }
        .sortedWith(compareBy<GoodSpec> { it.rare }.thenByDescending { Chest.count(s, it.id) }.thenBy { it.value }.thenBy { it.id }).firstOrNull()

    /** [spec] now: its next step, what it costs and why not. */
    fun option(s: GameState, spec: ProjectSpec, today: LocalDate): ProjectOption {
        val done = done(s, spec)
        val next = spec.steps.getOrNull(done)
            ?: return ProjectOption(spec, done, null, emptyMap(), 0, null, bi("projects.finished"))
        val cost = Catalog.stepCost(spec.age, next.kind)
        val help = if (next.help) Catalog.stepHelp(spec.age) else 0
        val treat = if (next.kind == StepKind.FEAST) treat(s) else null
        val short = cost.mapValues { (r, n) -> n - s.res(r) }.filterValues { it > 0 }
        val leader = Catalog.tools[spec.leader]?.name ?: spec.leader.replaceFirstChar { it.uppercase() }
        val (reason, why) = when {
            s.age < spec.age -> bi("engine.needsAge", "sl" to spec.age.names, "en" to spec.age.names) to ProjectOption.Why.AGE
            leaderToMeet(s, spec, today) -> bi("projects.leaderToMeet", "leader" to leader) to ProjectOption.Why.LEADER
            !leaderHere(s, spec, today) -> bi("projects.leaderAway", "leader" to leader) to ProjectOption.Why.LEADER
            workedToday(s, today) -> bi("projects.workedToday") to ProjectOption.Why.TODAY
            short.isNotEmpty() -> "${bi("engine.need")} ${costText(short)}" to ProjectOption.Why.RESOURCES
            s.help < help -> "${bi("engine.need")} ${help - s.help} 🤝" to ProjectOption.Why.HELP
            next.kind == StepKind.FEAST && treat == null -> bi("projects.needTreat") to ProjectOption.Why.TREAT
            else -> null to null
        }
        return ProjectOption(spec, done, next, cost, help, treat, reason, why)
    }

    /** Every project of the ages reached, as [option]s. */
    fun options(s: GameState, today: LocalDate): List<ProjectOption> = open(s).map { option(s, it, today) }

    /** The project steps that can be done today (only one of them: the village does one step a day). */
    fun today(s: GameState, today: LocalDate): List<ProjectOption> = options(s, today).filter { it.available }

    /** Every finished project's bonus, together (added to the buildings' and the tools' effects). */
    fun effect(s: GameState): ToolEffect =
        Catalog.projects.filter { finished(s, it) }.fold(ToolEffect()) { acc, p -> acc + p.bonus }

    /** The landmarks the map draws: every project with a step done, at its stage. */
    fun landmarks(s: GameState): List<Landmark> = Catalog.projects.mapNotNull { p ->
        val d = done(s, p)
        if (d <= 0) null else Landmark(p.landmark, p.id, p.site, d, p.steps.size)
    }

    // --- a step as a short scene (companion/SCENES.md "Project steps") ---------------------------------------------------

    /** [spec]'s step [index] (0: the first) as a short scene, when the culture has one; null: it is played as a practice. */
    fun play(spec: ProjectSpec, index: Int): StepPlay? = spec.steps.getOrNull(index)?.play

    /**
     * Where step [play] is played today: in its scene when the village has it open now ([Happenings.open]: its place
     * built, the age reached) and it is in the village's [language]; null: over the village.
     */
    fun sceneOf(play: StepPlay, scenes: List<SceneSpec>, state: GameState, language: String): SceneSpec? =
        play.scene?.let { id -> scenes.firstOrNull { it.id == id } }?.takeIf { it.language == language && si.lanisce.lani.game.scene.Happenings.open(it, state) }

    /** The id a step's dialog goes by ("project:mlaj/1"): the key its words and its talk are kept under. */
    fun dialogId(spec: ProjectSpec, index: Int): String = "project:${spec.id}/${index + 1}"

    /**
     * Step [index] of [spec] as a dialog, for a learner at [level] in the village's [language] (the highest level it has up
     * to theirs, else its easiest, as an introduction's), read in [pair] as a scene's dialog is ([inPair]); null when it has
     * none. Its lines are said by the people the file names (the leader, the helpers): [recast] gives them to who is here.
     */
    fun dialog(spec: ProjectSpec, index: Int, level: String, language: String, pair: LangPair = L10n.pair): Dialog? {
        val play = play(spec, index) ?: return null
        val lv = Arrivals.levelFor(play.levels.keys, level) ?: return null
        val lines = (play.levels[lv] as? JsonObject)?.get("lines") as? JsonArray ?: return null
        val dialog = buildMap<String, JsonElement> {
            put("id", JsonPrimitive(dialogId(spec, index)))
            put("lines", lines)
            play.skyStays?.let { put("sky_stays", JsonPrimitive(it)) }
            play.fxStays?.let { put("fx_stays", JsonPrimitive(it)) }
        }
        val scene = JsonObject(mapOf("language" to JsonPrimitive(language), "dialogs" to JsonArray(listOf(JsonObject(dialog)))))
        val d = (inPair(scene, pair)["dialogs"] as? JsonArray)?.firstOrNull() ?: return null
        return runCatching { json.decodeFromJsonElement(Dialog.serializer(), d) }.getOrNull()?.takeIf { it.lines.isNotEmpty() }
    }

    /** Who speaks in [d], in the order they first do; the leader first. */
    fun speakers(spec: ProjectSpec, d: Dialog): List<String> = (listOf(spec.leader) + d.lines.mapNotNull { it.who }).distinct()

    /**
     * Who says each speaker's lines today (companion/SCENES.md "Project steps"): the leader their own; a helper theirs when
     * they are in the village and met ([present]; null: everyone, a village without names), else another of the project's
     * helpers who is and doesn't speak in it yet, a child for a child, else anyone else of the village who is, of their age,
     * else the leader. A helper's lines say nothing of who says them (no name, nothing of the speaker's gender: the
     * bridge's check), so anyone can. [people]: everyone (the cast and the village's own), in their order.
     */
    fun cast(spec: ProjectSpec, d: Dialog, present: Set<String>?, people: List<Villager>): Map<String, String> {
        val byId = people.associateBy { it.id }
        fun here(id: String) = present == null || id in present
        val speakers = speakers(spec, d)
        val out = LinkedHashMap<String, String>()
        out[spec.leader] = spec.leader
        // the helpers who are here say their own lines first, so the others are cast among those left
        for (h in speakers.drop(1)) if (here(h)) out[h] = h
        for (h in speakers.drop(1)) {
            if (h in out) continue
            val child = byId[h]?.art?.let(ISpy::isChild)
            val used = out.values.toSet() + speakers
            fun fits(v: Villager) = v.id !in used && here(v.id) && v.art != "baby" && (child == null || ISpy.isChild(v.art) == child)
            val other = spec.helpers.mapNotNull(byId::get).firstOrNull(::fits) ?: people.firstOrNull(::fits)
            out[h] = other?.id ?: spec.leader
        }
        return out
    }

    /** [d] with each line said by whom [cast] says (a speaker it doesn't name: as the file has it). */
    fun recast(d: Dialog, cast: Map<String, String>): Dialog =
        d.copy(lines = d.lines.map { l -> l.who?.let(cast::get)?.let { l.copy(who = it) } ?: l })

    /** Who answers a choice at each turn (its line index): whoever spoke last before it, the leader before anyone has. */
    fun repliers(d: Dialog, leader: String): Map<Int, String> {
        var last = leader
        val out = HashMap<Int, String>()
        d.lines.forEachIndexed { i, l -> if (l.choices.isEmpty()) l.who?.let { last = it } else out[i] = last }
        return out
    }

    /**
     * Where a step's people ([ids], the leader first) stand in [scene]: where the scene has them when they are among its
     * people (Luka on the forest path), else on the first spot of its art none of them has, else beside the leader.
     */
    fun placed(scene: SceneSpec, ids: List<String>, people: List<Villager>): List<ScenePerson> {
        val spots = SceneArt.personSlots[scene.art].orEmpty()
        val taken = HashSet<String>()
        val out = ArrayList<ScenePerson>()
        for (id in ids.distinct()) {
            val v = people.firstOrNull { it.id == id }
            val own = scene.people.firstOrNull { it.villager == id }
            val slot = own?.slot?.takeIf { it !in taken } ?: spots.firstOrNull { it !in taken } ?: out.firstOrNull()?.slot ?: spots.firstOrNull() ?: ""
            taken += slot
            out += ScenePerson(id, v?.name ?: own?.name ?: id, v?.emoji ?: own?.emoji ?: "🙂", v?.art ?: own?.art ?: "man", slot, villager = id)
        }
        return out
    }

    /**
     * A step's scene was played to its end: the step is done, as a passed practice does it ([finishStep]): pays its cost
     * (the 🤝, the treat), tells its line in the chronicle, and the leader grows closer. A wrong answer in it was answered
     * again there (its reaction, its why), so there is nothing left to pass. [s] unchanged, and a lost result, when the
     * step can't be done now (the stores emptied meanwhile, already worked today).
     */
    fun stepPlayed(s: GameState, id: String, today: LocalDate, now: Long): Pair<GameState, ChallengeResult> = step(s, id, passed = true, today, now)

    /**
     * A step's practice was played: [correct] of [total]. A pass (as for a request) does the step: pays its cost (the 🤝,
     * the treat), tells its line in the chronicle, and the leader grows closer; the last step finishes the project (its
     * bonus, its helpers closer). A fail costs nothing: the village tries again. [s] unchanged, and a lost result, when
     * the step can't be done now.
     */
    fun finishStep(s: GameState, id: String, correct: Int, total: Int, today: LocalDate, now: Long): Pair<GameState, ChallengeResult> =
        step(s, id, Quests.passed(correct, total), today, now)

    private fun step(s: GameState, id: String, passed: Boolean, today: LocalDate, now: Long): Pair<GameState, ChallengeResult> {
        val spec = spec(id) ?: return s to lost("")
        val o = option(s, spec, today)
        val step = o.next ?: return s to lost("")
        val leader = Catalog.tools[spec.leader]?.name ?: spec.leader
        if (!passed) {
            return s to lost("${spec.emoji} ${Cultures.current.projectsFile.lines.tryAgain.bi("leader" to leader)}")
        }
        if (!o.available) return s to lost("${spec.emoji} ${o.reason}")
        var st = debit(s, o.cost).first
        if (o.help > 0) st = st.copy(help = (st.help - o.help).coerceAtLeast(0))
        o.treat?.let { st = Chest.add(st, it.id, -1) }
        val steps = o.done + 1
        st = st.copy(projects = st.projects + (spec.id to steps), projectDay = today.toString())
            .logged(now, spec.emoji to step.line)
        val memory = Memory(today.toString(), spec.memoryText.target, spec.memoryText.base, "project")
        st = Bonds.add(st, spec.leader, Catalog.PROJECT_STEP_POINTS, today, now = now)
        if (steps >= spec.steps.size) {
            st = st.logged(now, spec.emoji to "${spec.short}: ${spec.done}")
            val living = st.residents.map { it.id }.toSet()
            for (h in (listOf(spec.leader) + spec.helpers).distinct()) {
                if (living.isEmpty() || h in living) st = Bonds.add(st, h, Catalog.PROJECT_DONE_POINTS, today, memory, now = now)
            }
        }
        return st to ChallengeResult(true, emptyMap(), emptyMap(), emptyList(), "${spec.emoji} ${step.line}")
    }

    /** A step not done (a fail, or it can't be done now): nothing paid, nothing spent. */
    private fun lost(message: String) = ChallengeResult(false, emptyMap(), emptyMap(), emptyList(), message)

    /** "Mlaj 3/5". */
    fun progressText(s: GameState, spec: ProjectSpec): String = "${spec.short} ${done(s, spec)}/${spec.steps.size}"

    /** The bonus in short: "+3 😊", "+5 % 🪵". */
    fun bonusText(spec: ProjectSpec): String = Chest.effectText(spec.bonus)

    /** A step's price for a list: "150 🌾 60 🪵 · 4 🤝 · 🥮". */
    fun costLine(o: ProjectOption): String = listOfNotNull(
        costText(o.cost).takeIf { it.isNotEmpty() },
        o.help.takeIf { it > 0 }?.let { "$it 🤝" },
        if (o.next?.kind == StepKind.FEAST) (o.treat?.name?.emoji ?: "🧺") else null,
    ).joinToString(" · ")
}
