package si.lanisce.lani.game.culture

import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.DateRule
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.Festival
import si.lanisce.lani.game.GoodSpec
import si.lanisce.lani.game.ItemName
import si.lanisce.lani.game.ProjectSpec
import si.lanisce.lani.game.ProjectStep
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.ToolLine
import si.lanisce.lani.l10n.Lang
import java.time.DayOfWeek

/**
 * A culture pack, read and checked (see [Cultures]): the people, stories, feasts, goods and names of one region, in its
 * language ([language]) with translations. The game reads its words from [Cultures.current]; the rules and numbers stay
 * in [Catalog]. The objects the engine works with (goods, tools, festivals, projects) are made once per pack, and show
 * their texts in the learner's pair of the moment.
 */
class Culture(
    val manifest: Manifest,
    val world: WorldFile,
    val quests: QuestsFile,
    val festivalsFile: FestivalsFile,
    val surprises: SurprisesFile,
    val chest: ChestFile,
    val projectsFile: ProjectsFile,
    val events: EventsFile,
    val people: PeopleFile,
    val chronicle: ChronicleFile,
    /** What the chest's things hold to read (readings/<id>.json), by id. */
    val readings: Map<String, ReadingFile> = emptyMap(),
    /**
     * How its people are introduced when they join the village (arrivals.json, companion/VILLAGERS.md "Arrivals"); null
     * for a pack without them: then its village introduces nobody and everyone counts as met.
     */
    val arrivals: si.lanisce.lani.game.villagers.ArrivalsFile? = null,
    /**
     * Its projects' steps as short scenes (project-steps/<project>.json, by project id; companion/SCENES.md "Project
     * steps"): a project without one plays its steps as the practice it always had.
     */
    val projectSteps: Map<String, ProjectStepsFile> = emptyMap(),
) {
    /** This pack with [a] as its arrivals (tests, and a pack whose arrivals come later). */
    fun withArrivals(a: si.lanisce.lani.game.villagers.ArrivalsFile?): Culture =
        Culture(manifest, world, quests, festivalsFile, surprises, chest, projectsFile, events, people, chronicle, readings, a, projectSteps)

    /** This pack with [steps] as its projects' steps (tests). */
    fun withProjectSteps(steps: Map<String, ProjectStepsFile>): Culture =
        Culture(manifest, world, quests, festivalsFile, surprises, chest, projectsFile, events, people, chronicle, readings, arrivals, steps)

    val id: String get() = manifest.id
    val language: Lang get() = checkNotNull(Lang.of(manifest.language)) { "${manifest.id}: language ${manifest.language} isn't one of the app's" }

    // --- the names of things ------------------------------------------------------------------------------------

    private val resources = Res.entries.associateWith { world.resources.getValue(it.name.lowercase()) }
    private val ages = Age.entries.associateWith { world.ages.getValue(it.name.lowercase()).name }
    private val buildings = BuildingType.entries.associateWith { world.buildings.getValue(it.name.lowercase()).name }
    private val eventNames = EventKind.entries.associateWith { world.events.getValue(it.name.lowercase()).name }

    fun name(r: Res): Text = resources.getValue(r).name
    /** After "more": "več lesa · more wood". */
    fun partitive(r: Res): Text = resources.getValue(r).let { it.partitive ?: it.name }
    fun name(a: Age): Text = ages.getValue(a)
    fun name(t: BuildingType): Text = buildings.getValue(t)
    fun name(k: EventKind): Text = eventNames.getValue(k)

    /** Who goes gathering [r] with the learner (a villager's name), when the culture says. */
    fun leader(r: Res): String? = resources.getValue(r).leader

    /** The resource named [s] in any of the pack's languages ("hrana", "Food"), or null. */
    fun resource(s: String): Res? = resources.entries.firstOrNull { (_, n) -> n.name.by.values.any { it.equals(s, ignoreCase = true) } }?.key

    // --- the chest ------------------------------------------------------------------------------------------------

    /** An item's forms: a form a language lacks is the one before it in that language (the = acc = name). */
    private fun item(emoji: String, name: Text, acc: Text?, the: Text?): ItemName {
        val a = over(name, acc)
        return ItemName(emoji, name, a, over(a, the))
    }

    private fun item(e: ItemEntry): ItemName = item(e.emoji, e.name, e.acc, e.the)

    /** Goods by id, in the pack's order. */
    val goods: Map<String, GoodSpec> = chest.goods.associate { g ->
        g.id to GoodSpec(g.id, item(g.emoji, g.name, g.acc, g.the), Catalog.GOOD_PRICES.getValue(g.price), g.food, g.rare, g.adult)
    }

    /** Tools by the id of the villager who gives them. */
    val tools: Map<String, ToolLine> = chest.tools.associate { t ->
        t.giver to ToolLine(t.giver, t.giverName, item(t.first), item(t.better), Catalog.TOOL_EFFECTS.getValue(t.effect), t.forge)
    }

    val children: Set<String> = chest.children.toSet()

    /** The reading villager [giver]'s tool holds at [tier] (1: the first, 2: the better one), if it holds one. */
    fun toolReading(giver: String, tier: Int): ReadingFile? =
        chest.tools.firstOrNull { it.giver == giver }?.let { if (tier >= 2) it.better else it.first }?.read?.let(readings::get)

    /** The reading good [id] holds, if it holds one. */
    fun goodReading(id: String): ReadingFile? = chest.goods.firstOrNull { it.id == id }?.read?.let(readings::get)

    // --- the calendar ---------------------------------------------------------------------------------------------

    val festivals: List<Festival> = festivalsFile.festivals.map { f ->
        Festival(f.id, f.emoji, f.name, rule(f.date), f.leaders, f.ask, f.about, f.line, f.good, f.goods, f.words)
    }

    // --- village projects: Catalog's frames with the pack's words -------------------------------------------------

    val projects: List<ProjectSpec> = Catalog.projectFrames.mapNotNull { frame ->
        val p = projectsFile.projects.firstOrNull { it.id == frame.id } ?: return@mapNotNull null
        // each step as a short scene, where the pack has one for it (a step without: the practice it always had)
        val played = projectSteps[frame.id]
        ProjectSpec(
            id = frame.id, landmark = frame.landmark, age = frame.age, leader = p.leader, emoji = frame.emoji,
            nameText = p.name, inText = over(p.name, p.inText), aboutText = p.about, skill = frame.skill, topics = frame.topics, site = frame.site,
            steps = frame.steps.zip(p.steps).mapIndexed { i, (frameStep, s) ->
                ProjectStep(frameStep.first, s.task, s.line, frameStep.second, played?.steps?.getOrNull(i)?.takeIf { it.levels.isNotEmpty() })
            },
            bonus = frame.bonus, helpers = p.helpers, doneText = p.done, memoryText = p.memory, whereText = p.where, pack = played?.pack,
        )
    }

    override fun toString() = "culture ${manifest.id} (${manifest.language})"

    companion object {
        /** [form] where it has a language, else [base]: "the maypole" in English, "Mlaj" in Slovene. */
        fun over(base: Text, form: Text?): Text = if (form == null) base else Text(base.by + form.by)

        fun rule(d: DateEntry): DateRule = when {
            d.easter != null -> DateRule.Easter(d.easter)
            d.last != null -> DateRule.Last(checkNotNull(d.month) { "a \"last\" date needs its month" }, weekday(d.last))
            else -> DateRule.Fixed(checkNotNull(d.month) { "a date needs its month" }, checkNotNull(d.day) { "a date needs its day" })
        }

        fun weekday(s: String): DayOfWeek = DayOfWeek.entries.firstOrNull { it.name.equals(s, ignoreCase = true) }
            ?: throw IllegalArgumentException("\"$s\" isn't a weekday (monday … sunday)")
    }
}
