package si.lanisce.lani.game

import si.lanisce.lani.game.BuildingType.*
import si.lanisce.lani.game.Res.*
import si.lanisce.lani.game.culture.Cultures
import kotlin.math.roundToInt

/** What one (undamaged) building adds to the village at level 1. */
internal data class Effect(
    val pop: Int = 0,
    val defence: Int = 0,
    val morale: Int = 0,
    val production: Map<Res, Float> = emptyMap(),
    val caps: Map<Res, Int> = emptyMap(),
    /** Extra hours before an event resolves as lost. */
    val warningHours: Int = 0,
)

/** The effect at [level]: +60 % of the base per level above 1 (warning hours don't scale). */
internal fun Effect.at(level: Int): Effect {
    if (level <= 1) return this
    val f = 1f + 0.6f * (level - 1)
    fun i(n: Int) = (n * f).roundToInt()
    return copy(
        pop = i(pop),
        defence = i(defence),
        morale = i(morale),
        production = production.mapValues { (_, p) -> p * f },
        caps = caps.mapValues { (_, c) -> i(c) },
    )
}

internal data class Spec(
    val type: BuildingType,
    val cost: Map<Res, Int>,
    val minAge: Age,
    val max: Int?,
    val effect: Effect,
)

internal data class AgeRule(
    /** Undamaged buildings needed; a list of types means any of them count. */
    val needs: List<Pair<List<BuildingType>, Int>>,
    val cost: Map<Res, Int>,
    /** Words learned: this share of the words the learner's target level needs ([Catalog.ageWords]) … */
    val wordShare: Float,
    /** … and at least this many. */
    val minWords: Int = 0,
    /** Friends who live here: villagers at friendship level 2 or more (see villagers/Bonds). */
    val friends: Int = 0,
)

/** Balance numbers. GAME.md mirrors this file; keep them in sync. */
internal object Catalog {
    val earnPerAnswer = mapOf(
        si.lanisce.lani.data.Grading.Verdict.CORRECT to 6,
        si.lanisce.lani.data.Grading.Verdict.ALMOST to 3,
        si.lanisce.lani.data.Grading.Verdict.WRONG to 1,
    )

    /** Answers per day at full pay; later answers pay [TIRED_PAY] of it. */
    const val FRESH_ANSWERS = 40
    const val TIRED_PAY = 0.5f

    /** Base storage cap per resource, by age. */
    val baseCap = listOf(100, 200, 500, 800, 1200, 1800)
    const val BASE_POP = 2
    const val START_FOOD = 10
    const val START_WOOD = 10

    val specs: Map<BuildingType, Spec> = listOf(
        Spec(TENT, mapOf(FOOD to 25, WOOD to 15), Age.OGENJ, 4, Effect(pop = 2)),
        Spec(FIELD, mapOf(FOOD to 30, WOOD to 20), Age.OGENJ, 4, Effect(production = mapOf(FOOD to 0.15f))),
        Spec(WELL, mapOf(FOOD to 20, WOOD to 15, STONE to 20), Age.TABOR, 2, Effect(morale = 5, caps = mapOf(FOOD to 50))),
        Spec(HUT, mapOf(FOOD to 30, WOOD to 40, STONE to 10), Age.TABOR, null, Effect(pop = 3)),
        Spec(KOZOLEC, mapOf(FOOD to 30, WOOD to 50), Age.TABOR, null, Effect(caps = mapOf(FOOD to 150))),
        Spec(PALISADE, mapOf(FOOD to 30, WOOD to 40, STONE to 30), Age.TABOR, 1, Effect(defence = 15)),
        Spec(BEEHIVE, mapOf(FOOD to 40, WOOD to 30, WISDOM to 15), Age.ZASELEK, 3,
            Effect(production = mapOf(FOOD to 0.10f, WISDOM to 0.10f))),
        Spec(WATCHTOWER, mapOf(FOOD to 40, WOOD to 70, STONE to 50), Age.ZASELEK, 1, Effect(defence = 10, warningHours = 12)),
        Spec(SMITHY, mapOf(FOOD to 40, WOOD to 50, STONE to 70), Age.ZASELEK, 1,
            Effect(defence = 5, production = mapOf(STONE to 0.20f))),
        Spec(LIPA, mapOf(FOOD to 120, WISDOM to 40), Age.ZASELEK, 1, Effect(morale = 10)),
        Spec(HOUSE, mapOf(FOOD to 60, WOOD to 70, STONE to 50), Age.ZASELEK, null, Effect(pop = 4)),
        Spec(CHURCH, mapOf(FOOD to 140, WOOD to 120, STONE to 90, WISDOM to 70), Age.ZASELEK, 1,
            Effect(morale = 5, production = mapOf(WISDOM to 0.20f))),
        Spec(SCHOOL, mapOf(FOOD to 100, WOOD to 120, STONE to 100, WISDOM to 100), Age.VAS, 1,
            Effect(production = mapOf(WISDOM to 0.25f))),
        Spec(MARKET, mapOf(FOOD to 160, WOOD to 150, STONE to 100, WISDOM to 80), Age.VAS, 1,
            Effect(caps = Res.entries.associateWith { 200 })),
    ).associateBy { it.type }

    /**
     * Rule to reach an age (index = Age.ordinal; OGENJ has none). The ages are milestones on the way to the
     * learner's target level: Vas at A2, Trg at B1, Mesto at the target (for B2: 300, 1000, 2000 words).
     */
    val ageRules: Map<Age, AgeRule> = mapOf(
        // The first age has two plots: a tent and whatever else was built there must be enough to go on.
        Age.TABOR to AgeRule(listOf(listOf(TENT) to 1), mapOf(FOOD to 20, WOOD to 10), wordShare = 0f, minWords = 10),
        Age.ZASELEK to AgeRule(
            listOf(listOf(PALISADE) to 1, listOf(FIELD) to 1),
            mapOf(FOOD to 170, WOOD to 80, STONE to 60), wordShare = 0.03f, minWords = 40, friends = 1,
        ),
        // Friends 1 / 3 / 5 / 8: the ages are long (they follow the target level), and the people carry them.
        Age.VAS to AgeRule(
            listOf(listOf(CHURCH) to 1, listOf(LIPA) to 1, listOf(HUT, HOUSE) to 2),
            mapOf(FOOD to 500, WOOD to 320, STONE to 130, WISDOM to 110), wordShare = 0.15f, friends = 3,
        ),
        Age.TRG to AgeRule(
            listOf(listOf(MARKET) to 1, listOf(SCHOOL) to 1, listOf(WELL) to 1),
            mapOf(FOOD to 800, WOOD to 750, STONE to 700, WISDOM to 650), wordShare = 0.5f, friends = 5,
        ),
        Age.MESTO to AgeRule(
            listOf(listOf(WATCHTOWER) to 1, listOf(SMITHY) to 1, listOf(HOUSE) to 5, listOf(KOZOLEC) to 2),
            mapOf(FOOD to 1200, WOOD to 1100, STONE to 800, WISDOM to 700), wordShare = 1f, friends = 8,
        ),
    )

    /** The target level when the learner profile names none (and the app's default). */
    const val DEFAULT_TARGET = "B2"

    /**
     * Words learned to reach each age on the way to [target] (a CEFR level): each age's share of the words that
     * level needs ([si.lanisce.lani.data.Stats.wordsToReach]), at least its minimum, and always more than the
     * age before. B2 (2000 words): 10, 60, 300, 1000, 2000; B1 (1000): 10, 40, 150, 500, 1000.
     */
    fun ageWords(target: String): Map<Age, Int> {
        val reach = si.lanisce.lani.data.Stats.wordsToReach(target)
        var before = 0
        return ageRules.entries.sortedBy { it.key.ordinal }.associate { (age, rule) ->
            val w = maxOf((reach * rule.wordShare).roundToInt(), rule.minWords, before + 1)
            before = w
            age to w
        }
    }

    /** Words learned to reach [age] on the way to [target]; 0 for the first age. */
    fun wordsFor(age: Age, target: String): Int = ageWords(target)[age] ?: 0

    fun spec(type: BuildingType) = specs.getValue(type)

    // --- helping people (🤝 Pomoč, see Help) ---------------------------------------------------------

    /** 🤝 for a villager's request passed (a local or a tutor quest). */
    const val HELP_QUEST = 3
    /** 🤝 for a talk with a villager (a role-play with the tutor playing them). */
    const val HELP_TALK = 2
    /** 🤝 for a scene's happening done (its dialog played to the end, once a day). */
    const val HELP_HAPPENING = 1
    /** 🤝 for answering a family challenge. */
    const val HELP_FAMILY = 1

    /** A moba: each neighbour who comes covers this share of the 🪵 and 🪨 … */
    const val MOBA_SHARE_PER_HELPER = 0.1f
    /** … up to half of it. */
    const val MOBA_MAX_SHARE = 0.5f
    /** What a moba can cover: the timber and the stone (the neighbours bring them and their hands). */
    val MOBA_RES = listOf(WOOD, STONE)

    /** 🪵/🪨 one 🤝 covers: 5 at the campfire, growing with the age like quest rewards (7, 8, 10, 11, 13). */
    fun mobaPerHelp(age: Age): Int = (5 * (1f + 0.3f * age.ordinal)).roundToInt()

    // --- the chest: tools and goods (see Chest) ----------------------------------------------------

    /** ♥ for a good given to a villager who likes it (one a day each). */
    const val GIFT_POINTS = 5
    /** ♥ for an ordinary good given (one they don't especially like): warm thanks, and the day's gift is given. */
    const val PLAIN_GIFT_POINTS = 2
    /** The smith forges a tool at most this many times; each adds half its first strength. */
    const val MAX_FORGED = 2

    /** Forging a tool the [n]-th time (1 or 2): what it costs, and the age it needs (a store must hold it). */
    fun forgeCost(n: Int): Map<Res, Int> =
        if (n <= 1) mapOf(FOOD to 200, WOOD to 300, STONE to 200) else mapOf(FOOD to 400, WOOD to 600, STONE to 400)

    fun forgeAge(n: Int): Age = if (n <= 1) Age.ZASELEK else Age.VAS

    /**
     * A village feast under the linden (from Zaselek, the linden's age, once a week): what it costs by age (a store
     * must hold it), and the treats served at the tables. The steady use for full stores while an age waits on words.
     */
    fun feastCost(age: Age): Map<Res, Int> = when {
        // food and firewood: what everyday practice piles up, never the scarce stone and wisdom
        age >= Age.MESTO -> mapOf(FOOD to 1000, WOOD to 500)
        age >= Age.TRG -> mapOf(FOOD to 700, WOOD to 350)
        age >= Age.VAS -> mapOf(FOOD to 450, WOOD to 250)
        else -> mapOf(FOOD to 300, WOOD to 150)
    }
    const val FEAST_GOODS = 2
    const val FEAST_EVERY_DAYS = 7L
    /** A feast lifts the morale like a won festival, and everyone who lives here grows a little closer (+1 ♥ a treat). */
    const val FEAST_MORALE = 15
    const val FEAST_POINTS = 1

    /** Selling goods: the merchant pays their value, the market (from Trg) half as much again. */
    const val MERCHANT_RATE = 1f
    const val MARKET_RATE = 1.5f

    /**
     * Buying goods (the market from Trg, the merchant while he's here, the pedlar on his day): twice their value,
     * growing with the age like the sale price, paid in the plentiful resources: [BUY_FOOD_SHARE] in 🌾, the rest in
     * 🪵. Nobody pays more for a good than they ask for it: a sale never brings more than [BUY_RATE].
     */
    const val BUY_RATE = 2f
    const val BUY_FOOD_SHARE = 0.6f
    /** How many goods each seller offers a day (the pedlar: his rare good and these), and how many of each. */
    const val MARKET_OFFERS = 5
    const val MARKET_STOCK = 3
    const val MERCHANT_OFFERS = 3
    const val MERCHANT_STOCK = 2
    const val PEDLAR_OFFERS = 2
    const val PEDLAR_STOCK = 2
    const val RARE_STOCK = 1
    /** A rare good (a festival's, the pedlar's) given to someone who likes it: twice a good's ♥. */
    const val RARE_GIFT_POINTS = 10
    /** The rare goods the pedlar brings (one a visit): the culture's (surprises.json, pedlar.goods). */
    val pedlarGoods: List<String> get() = Cultures.current.surprises.pedlar.goods
    /** The children of the cast: no wine for them (their likes leave out [GoodSpec.adult] goods). The culture's. */
    val children: Set<String> get() = Cultures.current.children

    /**
     * What a good is worth to the merchant, by its price class (a culture pack's goods name one): the numbers are the
     * game's, which goods there are is the culture's.
     */
    val GOOD_PRICES: Map<String, Int> = mapOf("small" to 10, "plain" to 15, "good" to 20, "fine" to 25, "rich" to 30, "precious" to 35)

    /**
     * What a villager's tool does at strength 1, by the kind a culture pack's tool names: more of a resource from every
     * answer, less food eaten (a recipe), more time for events (a radio, a horn), better spirits (candles, keepsakes),
     * better pay for requests (chalk), better trades (scales), bigger stores (a barrel).
     */
    val TOOL_EFFECTS: Map<String, ToolEffect> = mapOf(
        "wood" to ToolEffect(production = mapOf(WOOD to 0.10f)),
        "food" to ToolEffect(production = mapOf(FOOD to 0.10f)),
        "stone" to ToolEffect(production = mapOf(STONE to 0.10f)),
        "wisdom" to ToolEffect(production = mapOf(WISDOM to 0.10f)),
        "thrift" to ToolEffect(thrift = 0.10f),
        "warning" to ToolEffect(warningHours = 6),
        "morale" to ToolEffect(morale = 3),
        "quest_pay" to ToolEffect(questPay = 0.10f),
        "trade" to ToolEffect(trade = 0.25f),
        "stores" to ToolEffect(caps = 40),
        // the children's keepsakes: small, but they're theirs
        "keepsake_morale" to ToolEffect(morale = 2),
        "keepsake_warning" to ToolEffect(warningHours = 3),
    )

    /**
     * The tool each villager gives at friendship level 2 ([ToolLine.first]) and 4 (the better one, [ToolLine.better]),
     * at strength 1; [ToolLine.forge]: iron or brass, so the smith can make it better. The culture's (chest.json), with
     * the effects of [TOOL_EFFECTS].
     */
    val tools: Map<String, ToolLine> get() = Cultures.current.tools

    /** Goods: a thank-you, a gift, something to trade. [GoodSpec.value] is what the merchant pays at the campfire. The culture's. */
    val goods: Map<String, GoodSpec> get() = Cultures.current.goods

    /** The good a villager thanks with after their request; others give [DEFAULT_THANKS]. */
    val thanks: Map<String, String> get() = Cultures.current.chest.thanks
    val DEFAULT_THANKS: String get() = Cultures.current.chest.thanksDefault

    /** What a villager thanks with in turn, and in a festival's season (chest.json `thanks_rotation`; see [Chest.thanksFor]). */
    val thanksRotation: Map<String, si.lanisce.lani.game.culture.ThanksRotation> get() = Cultures.current.chest.thanksRotation

    /** A festival's seasonal thank-you comes from this many days before its day to its last grace day ([Calendar.GRACE_DAYS]). */
    const val THANKS_SEASON_DAYS = 7L

    /** The small gifts of people not in [tools] (who moved in or were born here), picked by who they are. */
    val smallGifts: List<String> get() = Cultures.current.chest.smallGifts

    /** Which goods each villager likes (a good given to them grows the friendship); nobody likes their own. */
    val likes: Map<String, List<String>> get() = Cultures.current.chest.likes

    /** What people who moved in or were born here like: the small gifts and bread. */
    val defaultLikes: List<String> get() = Cultures.current.chest.likesDefault

    // --- village projects (see Projects) ----------------------------------------------------------------

    /** A project step's practice: this many exercises of the leader's skill (the pass mark as for a request). */
    const val PROJECT_EXERCISES = 5
    /** ♥ with the leader for each step done together. */
    const val PROJECT_STEP_POINTS = 5
    /** ♥ with each helper who lives here (and the leader once more) when the project is finished. */
    const val PROJECT_DONE_POINTS = 5

    /** What one step costs, by the project's age (a store of that age must hold it) and what the step needs. */
    fun stepCost(age: Age, kind: StepKind): Map<Res, Int> {
        // about a day or two of a committed learner's surplus at that age: the long ages (Vas, Trg) get the big builds
        val b = when {
            age >= Age.MESTO -> 700
            age >= Age.TRG -> 520
            age >= Age.VAS -> 380
            else -> 200
        }
        fun n(f: Float) = (b * f / 5f).roundToInt() * 5
        return when (kind) {
            StepKind.WORK -> mapOf(FOOD to n(1.2f), WOOD to n(0.5f))
            StepKind.TIMBER -> mapOf(FOOD to n(0.5f), WOOD to n(1.2f))
            StepKind.STONE -> mapOf(FOOD to n(0.5f), WOOD to n(0.3f), STONE to n(0.9f))
            StepKind.CRAFT -> mapOf(FOOD to n(0.5f), WOOD to n(0.4f), WISDOM to n(0.6f))
            StepKind.FEAST -> mapOf(FOOD to n(1.5f), WOOD to n(0.6f), WISDOM to n(0.3f))
        }
    }

    /** 🤝 a step with the neighbours costs ([ProjectStep.help]): 4 at Zaselek, 6, 8, 10 at Mesto. */
    fun stepHelp(age: Age): Int = 2 * age.ordinal

    /**
     * "Skupni projekti · Village projects": community builds, led by a villager who lives here, one step a day. Their
     * frames are the game's: the age, the landmark the map draws in stages as the steps are done ([Projects.landmarks])
     * and where, the practice ([ProjectFrame.skill], [ProjectFrame.topics]), what each step needs and what the finished
     * project adds. Their words and people (the leader, the helpers) are the culture's (projects.json): a project the
     * culture has no words for isn't built there.
     */
    val projectFrames: List<ProjectFrame> = listOf(
        ProjectFrame(
            "mlaj", landmark = "mlaj", age = Age.ZASELEK, emoji = "🌲", skill = WOOD,
            topics = listOf("nature", "animals", "directions"), site = listOf("lipa", "fire"),
            steps = listOf(StepKind.TIMBER to false, StepKind.TIMBER to true, StepKind.WORK to false, StepKind.CRAFT to false, StepKind.FEAST to false),
            bonus = ToolEffect(morale = 3),
        ),
        ProjectFrame(
            "most", landmark = "most", age = Age.ZASELEK, emoji = "🌉", skill = FOOD,
            topics = listOf("farm", "tools", "numbers", "nature"), site = listOf("spot:riverbank"),
            steps = listOf(StepKind.CRAFT to false, StepKind.STONE to false, StepKind.STONE to true, StepKind.TIMBER to false, StepKind.TIMBER to false, StepKind.FEAST to false),
            bonus = ToolEffect(production = mapOf(WOOD to 0.05f)),
        ),
        ProjectFrame(
            "mlin", landmark = "mlin", age = Age.ZASELEK, emoji = "⚙️", skill = FOOD,
            topics = listOf("food", "farm", "numbers", "tools"), site = listOf("spot:riverbank"),
            steps = listOf(StepKind.CRAFT to false, StepKind.STONE to false, StepKind.TIMBER to true, StepKind.TIMBER to false, StepKind.CRAFT to false, StepKind.FEAST to false),
            bonus = ToolEffect(thrift = 0.10f),
        ),
        ProjectFrame(
            "balinisce", landmark = "balinisce", age = Age.VAS, emoji = "🎳", skill = WISDOM,
            topics = listOf("numbers", "basics", "past", "connectors"), site = listOf("lipa", "fire"),
            steps = listOf(StepKind.WORK to false, StepKind.STONE to false, StepKind.TIMBER to false, StepKind.STONE to true, StepKind.FEAST to false),
            bonus = ToolEffect(morale = 3),
        ),
        ProjectFrame(
            "kapelica", landmark = "kapelica", age = Age.VAS, emoji = "🛐", skill = WOOD,
            topics = listOf("time", "dates", "news", "places"), site = listOf("spot:road"),
            steps = listOf(StepKind.CRAFT to false, StepKind.STONE to false, StepKind.STONE to true, StepKind.TIMBER to false, StepKind.CRAFT to false, StepKind.FEAST to false),
            bonus = ToolEffect(morale = 2),
        ),
        ProjectFrame(
            "vinograd", landmark = "vinograd", age = Age.VAS, emoji = "🍇", skill = WISDOM,
            topics = listOf("drinks", "food", "small_talk", "introductions"), site = listOf("field", "spot:meadow"),
            steps = listOf(StepKind.WORK to false, StepKind.STONE to false, StepKind.STONE to true, StepKind.WORK to false, StepKind.TIMBER to false, StepKind.CRAFT to false, StepKind.FEAST to false),
            bonus = ToolEffect(production = mapOf(WISDOM to 0.05f)),
        ),
        ProjectFrame(
            "cebelji_travnik", landmark = "cebelji_travnik", age = Age.VAS, emoji = "🌼", skill = FOOD,
            topics = listOf("nature", "colours", "colors", "food"), site = listOf("spot:meadow"),
            steps = listOf(StepKind.WORK to false, StepKind.CRAFT to false, StepKind.TIMBER to false, StepKind.CRAFT to false, StepKind.TIMBER to true, StepKind.FEAST to false),
            bonus = ToolEffect(production = mapOf(FOOD to 0.05f)),
        ),
        ProjectFrame(
            "gasilski_dom", landmark = "gasilski_dom", age = Age.VAS, emoji = "🚒", skill = WOOD,
            topics = listOf("directions", "numbers", "places", "weather"), site = listOf("fire"),
            steps = listOf(StepKind.STONE to false, StepKind.STONE to true, StepKind.TIMBER to false, StepKind.CRAFT to false, StepKind.WORK to false, StepKind.FEAST to false),
            bonus = ToolEffect(warningHours = 6),
        ),
        ProjectFrame(
            "igrisce", landmark = "igrisce", age = Age.VAS, emoji = "🎠", skill = STONE,
            topics = listOf("grammar", "dual", "gender", "agreement"), site = listOf("school", "lipa"),
            steps = listOf(StepKind.CRAFT to false, StepKind.WORK to false, StepKind.TIMBER to true, StepKind.TIMBER to false, StepKind.FEAST to false),
            bonus = ToolEffect(questPay = 0.05f),
        ),
        ProjectFrame(
            "vodnjak_na_trgu", landmark = "vodnjak_na_trgu", age = Age.TRG, emoji = "⛲", skill = STONE,
            topics = listOf("grammar", "cases", "prepositions", "tools"), site = listOf("market", "fire"),
            steps = listOf(StepKind.CRAFT to false, StepKind.STONE to false, StepKind.STONE to true, StepKind.WORK to false, StepKind.CRAFT to false, StepKind.FEAST to false),
            bonus = ToolEffect(morale = 3),
        ),
        ProjectFrame(
            "toplar", landmark = "toplar", age = Age.TRG, emoji = "🪜", skill = FOOD,
            topics = listOf("farm", "seasons", "weather", "tools"), site = listOf("kozolec", "field"),
            steps = listOf(StepKind.TIMBER to false, StepKind.STONE to false, StepKind.TIMBER to true, StepKind.TIMBER to false, StepKind.WORK to false, StepKind.FEAST to false),
            bonus = ToolEffect(caps = 60),
        ),
        ProjectFrame(
            "razgledni_stolp", landmark = "razgledni_stolp", age = Age.TRG, emoji = "⛰️", skill = WISDOM,
            topics = listOf("places", "countries", "directions", "restaurant"), site = listOf("spot:rocks", "spot:highseat"),
            steps = listOf(StepKind.CRAFT to false, StepKind.STONE to false, StepKind.TIMBER to false, StepKind.TIMBER to true, StepKind.WORK to false, StepKind.CRAFT to false, StepKind.FEAST to false),
            bonus = ToolEffect(trade = 0.10f),
        ),
        ProjectFrame(
            "mestna_ura", landmark = "mestna_ura", age = Age.MESTO, emoji = "🕰️", skill = STONE,
            topics = listOf("time", "numbers", "grammar"), site = listOf("church", "fire"),
            steps = listOf(StepKind.CRAFT to false, StepKind.STONE to false, StepKind.CRAFT to true, StepKind.TIMBER to false, StepKind.STONE to false, StepKind.WORK to false, StepKind.FEAST to false),
            bonus = ToolEffect(production = Res.entries.associateWith { 0.05f }),
        ),
    )

    /** The village projects of the culture: [projectFrames] with the culture's words and people, in this order. */
    val projects: List<ProjectSpec> get() = Cultures.current.projects

    const val MAX_LEVEL = 5

    /** Upgrade price as a multiple of the build cost, by the level reached (index = level). */
    val upgradeFactor = listOf(0f, 1f, 2f, 3f, 5f, 7f)

    fun upgradeCost(type: BuildingType, toLevel: Int): Map<Res, Int> {
        val f = upgradeFactor[toLevel.coerceIn(2, MAX_LEVEL)]
        return spec(type).cost.mapValues { (_, n) -> (n * f).roundToInt() }
    }

    /**
     * Each age raises the top level by one: level 2 from Tabor, 3 from Zaselek, 4 from Vas, 5 from Trg.
     * Upgrades are the steady sink between the word milestones, when the plots are full.
     */
    fun upgradeAge(toLevel: Int): Age = Age.entries[(toLevel - 1).coerceIn(1, Age.entries.size - 1)]

    fun effectText(e: Effect): String = buildList {
        if (e.pop > 0) add("+${e.pop} 👥")
        e.production.forEach { (r, p) -> add("+${(p * 100).toInt()} % ${r.emoji}") }
        e.caps.forEach { (r, c) -> add("+$c ${r.emoji} max") }
        if (e.morale > 0) add("+${e.morale} 😊")
        if (e.defence > 0) add("+${e.defence} 🛡️")
        if (e.warningHours > 0) add("+${e.warningHours} h ⏳")
    }.joinToString(", ")
}

internal fun ageName(a: Age) = a.names.bi()

internal fun buildingName(t: BuildingType) = t.names.bi()

internal fun costText(cost: Map<Res, Int>) = cost.entries.joinToString(", ") { (r, n) -> "$n ${r.emoji}" }

/** Adds amounts per resource, dropping zeros. */
internal fun Map<Res, Int>.sumWith(other: Map<Res, Int>): Map<Res, Int> =
    (keys + other.keys).associateWith { (this[it] ?: 0) + (other[it] ?: 0) }.filterValues { it != 0 }
