package si.lanisce.lani.game

import si.lanisce.lani.data.ModuleInfo
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.villagers.Mentions
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

internal object Quests {
    const val OPEN_LOCAL = 3
    const val LIFETIME_DAYS = 3
    const val PASS_RATIO = 0.6f
    const val BASE_REWARD = 30
    const val SIDE_REWARD = 10
    const val TUTOR_DEFAULT_REWARD = 40
    const val TUTOR_MAX_REWARD = 300
    /** On-topic cards a quest needs before it is built around its topic. */
    const val MIN_TOPIC_CARDS = 4

    /**
     * A quest template. [topics] are card categories the story is about ("greetings", "food", a word-pack id);
     * a card fits when its category is one of them or contains one as a word ("grammar_cases" fits "grammar").
     * [grammar]: the grammar book's page of the rule it is about, if any.
     */
    private data class Giver(
        val name: String, val emoji: String, val skill: Res, val title: String, val story: String,
        val topics: List<String> = emptyList(), val grammar: String? = null,
    )

    /**
     * Quest templates: the culture's (game/culture, quests.json), their texts in the learner's pair of the moment. One
     * villager may have several; a refill never opens two quests from the same villager. The cast and their voices
     * are described in GAME.md ("Villagers").
     */
    private val givers: List<Giver>
        get() = Cultures.current.quests.requests.map { r ->
            Giver(r.giver, r.emoji, Res.valueOf(r.skill.uppercase()), r.title.bi(), r.story.bi(), r.topics, r.grammar)
        }

    fun ageMultiplier(age: Age) = 1f + 0.3f * age.ordinal

    fun passed(correct: Int, total: Int) = total > 0 && correct >= ceil(total * PASS_RATIO).toInt()

    fun passMark(total: Int) = ceil(total * PASS_RATIO).toInt().coerceAtLeast(1)

    /**
     * Drops finished and expired local quests, and those of someone no longer in the village ([present]: the
     * names of who is, null for everyone; a visitor's request goes with them; the tent's move waits); tutor quests
     * stay (done ones mark the module as played). Returns the expired ones, for the chronicle.
     */
    fun prune(s: GameState, now: Long, present: Set<String>? = null): Pair<GameState, List<Quest>> {
        val expired = s.quests.filter { it.source == QuestSource.LOCAL && !it.done && it.expiresAt in 1..now }
        // the shepherd's request to move the tent waits for him (see TentMove), like a tutor's
        val gone = s.quests.filter { it.source == QuestSource.LOCAL && !it.done && present != null && it.giver !in present && it.id != TentMove.ID }
        val kept = s.quests.filter { q -> q.source == QuestSource.TUTOR || (!q.done && q !in expired && q !in gone) }
        return s.copy(quests = kept) to expired
    }

    fun refill(s: GameState, today: LocalDate, now: Long, pool: ContentPool): Pair<GameState, List<Quest>> {
        val open = s.quests.filter { it.source == QuestSource.LOCAL && !it.done }
        // the tent's move isn't one of the three (its giver asks nothing else meanwhile)
        val missing = OPEN_LOCAL - open.count { it.id != TentMove.ID }
        if (missing <= 0 || pool.cards.isEmpty()) return s to emptyList()
        val r = rng(s.seed, "quest", today.toEpochDay())
        val busy = open.map { it.giver }.toSet()
        val mult = ageMultiplier(s.age)
        // One template per free villager: a story that fits the learner's cards when there is one.
        // Villagers with a fitting story come first; the order among them stays random. A story names only who is here
        // and met ("Micka's kitchen" asks while Micka is; Mentions): another takes its place.
        val here = pool.present
        val names = Mentions.names(Cultures.current.manifest.language)
        fun speaks(g: Giver) = here == null || (names.of(Mentions.said(g.title)) + names.of(Mentions.said(g.story))).all { names.names[it] in here }
        val picks = givers.filter { it.name !in busy && (here == null || it.name in here) && speaks(it) }.groupBy { it.name }.values.shuffled(r).map { templates ->
            val fitting = templates.filter { g -> pool.cards.count { fits(it.category, g.topics) } >= MIN_TOPIC_CARDS }
            fitting.ifEmpty { templates }.random(r) to fitting.isNotEmpty()
        }.sortedBy { !it.second }.take(missing).map { it.first }
        val caps = GameEngine.attributes(s).caps
        val added = picks.mapIndexed { i, g ->
            // The side reward tops up what the village is shortest of: a way out when one resource is stuck.
            val side = Res.entries.filter { it != g.skill }.minBy { s.res(it).toFloat() / caps.getValue(it) }
            val (cards, category) = cardsFor(g.topics, pool.cards, 5 + r.nextInt(2), r)
            Quest(
                id = "q-$today-$i",
                giver = g.name,
                emoji = g.emoji,
                title = g.title,
                story = g.story,
                skill = g.skill,
                reward = mapOf(g.skill to (BASE_REWARD * mult).roundToInt(), side to (SIDE_REWARD * mult).roundToInt()),
                cardIds = cards.map { it.id },
                category = category,
                expiresAt = now + LIFETIME_DAYS * DAY_MS,
                grammar = g.grammar,
                since = today.toString(),
            )
        }
        return s.copy(quests = s.quests + added) to added
    }

    /** True when a card's [category] is one of [topics] or contains one as a word ("grammar_cases" fits "grammar"). */
    fun fits(category: String?, topics: List<String>): Boolean {
        val c = category?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        if (topics.isEmpty()) return false
        val words = c.split('_', '-', ' ', '/', '.', ':').filter { it.isNotEmpty() }.toSet()
        return topics.any { t -> t == c || t in words }
    }

    /**
     * [n] cards for a quest about [topics]: the on-topic cards (weakest first) when there are at least
     * [MIN_TOPIC_CARDS], topped up weakest-first from the rest; otherwise weakest-first from all cards.
     * Also returns the most common category among the on-topic cards used, or null.
     */
    fun cardsFor(topics: List<String>, cards: List<ReviewCard>, n: Int, random: Random): Pair<List<ReviewCard>, String?> {
        val (onTopic, rest) = cards.partition { fits(it.category, topics) }
        if (onTopic.size < MIN_TOPIC_CARDS) return Content.pickCards(cards, n, random) to null
        val chosen = Content.pickCards(onTopic, n, random)
        val topped = chosen + Content.pickCards(rest, n - chosen.size, random)
        val category = chosen.groupingBy { it.category!!.trim().lowercase() }.eachCount().maxByOrNull { it.value }?.key
        return topped to category
    }

    /**
     * A giver's villager id guessed from their name, when the cast isn't at hand: the last word, lower case,
     * without č/š/ž ("Babica Micka" → "micka", "Teta Ančka" → "ancka"), as the cast's ids are made.
     */
    fun idOf(giver: String): String = giver.trim().substringAfterLast(' ').lowercase()
        .replace('č', 'c').replace('š', 's').replace('ž', 'z').replace('ć', 'c').replace('đ', 'd')

    /** A resource by its name: "food", or what the culture calls it in the learner's languages ("hrana", "Food"). */
    fun resOf(name: String?): Res? = name?.trim()?.lowercase()?.let { n ->
        Res.entries.firstOrNull { it.name.lowercase() == n || it.sl.lowercase() == n || it.en.lowercase() == n }
    }

    fun tutorId(m: ModuleInfo) = "tutor-${m.id}-v${m.version}"

    /**
     * The tutor's request of module [m] (its quest block), asked [today]; its `helps` (a drill for another task of the giver's)
     * names another module, never itself.
     */
    fun fromModule(m: ModuleInfo, age: Age, today: LocalDate? = null): Quest? {
        val meta = m.quest ?: return null
        val reward = meta.reward.mapNotNull { (k, v) -> resOf(k)?.let { it to v.coerceIn(0, TUTOR_MAX_REWARD) } }
            .filter { it.second > 0 }.toMap()
        val skill = resOf(meta.skill) ?: reward.maxByOrNull { it.value }?.key ?: Res.FOOD
        return Quest(
            id = tutorId(m),
            giver = meta.giver,
            emoji = meta.emoji,
            title = m.title,
            story = meta.story,
            skill = skill,
            reward = reward.ifEmpty { mapOf(skill to (TUTOR_DEFAULT_REWARD * ageMultiplier(age)).roundToInt()) },
            source = QuestSource.TUTOR,
            moduleId = m.id,
            since = today?.toString(),
            helps = meta.helps?.trim()?.takeIf { it.isNotEmpty() && it != m.id },
        )
    }
}
