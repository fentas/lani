package si.lanisce.lani.game

import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Memory
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.l10n.bi
import java.time.LocalDate

/** What a project step needs besides the practice: the materials, by kind (see [Catalog.stepCost]). */
enum class StepKind { WORK, TIMBER, STONE, CRAFT, FEAST }

/**
 * One step of a project: what it asks for ([task], a to-do), and what the chronicle tells once it's done ([line], past
 * tense); both "target · base" from the culture's words ([taskText], [lineText]). [help]: the neighbours come too, it
 * costs 🤝. A [StepKind.FEAST] step (the last) treats the helpers to a good from the chest.
 */
data class ProjectStep(val kind: StepKind, val taskText: Text, val lineText: Text, val help: Boolean = false) {
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

    /**
     * A step's practice was played: [correct] of [total]. A pass (as for a request) does the step: pays its cost (the 🤝,
     * the treat), tells its line in the chronicle, and the leader grows closer; the last step finishes the project (its
     * bonus, its helpers closer). A fail costs nothing: the village tries again. [s] unchanged, and a lost result, when
     * the step can't be done now.
     */
    fun finishStep(s: GameState, id: String, correct: Int, total: Int, today: LocalDate, now: Long): Pair<GameState, ChallengeResult> {
        val spec = spec(id) ?: return s to lost("")
        val o = option(s, spec, today)
        val step = o.next ?: return s to lost("")
        val leader = Catalog.tools[spec.leader]?.name ?: spec.leader
        if (!Quests.passed(correct, total)) {
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
