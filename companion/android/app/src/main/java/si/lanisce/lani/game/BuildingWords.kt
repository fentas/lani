package si.lanisce.lani.game

import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Hints
import si.lanisce.lani.data.Pack
import si.lanisce.lani.data.PackSession
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.game.BuildingType.BEEHIVE
import si.lanisce.lani.game.BuildingType.CHURCH
import si.lanisce.lani.game.BuildingType.FIELD
import si.lanisce.lani.game.BuildingType.HOUSE
import si.lanisce.lani.game.BuildingType.HUT
import si.lanisce.lani.game.BuildingType.KOZOLEC
import si.lanisce.lani.game.BuildingType.LIPA
import si.lanisce.lani.game.BuildingType.MARKET
import si.lanisce.lani.game.BuildingType.PALISADE
import si.lanisce.lani.game.BuildingType.SCHOOL
import si.lanisce.lani.game.BuildingType.SMITHY
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.BuildingType.WATCHTOWER
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.Homes
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.inWorld
import kotlin.random.Random

// "Znanje gradi · Knowledge builds" (companion/GAME.md, "Upgrades"): an upgrade asks for the building's words besides the
// resources, the words of what is in it. Building new asks only for the resources.

/** How one of the building's words stands for the learner. */
enum class WordState {
    /** Not met yet: no card of it (nor another card with the same word). A run teaches it, as a word pack does. */
    NEW,
    /** Met, never answered right yet (answered wrong while it was learned, not reviewed since): a run reviews it. */
    TRIED,
    /** Answered right at least once and not failed since ([si.lanisce.lani.data.Stats.learned]): it counts. */
    KNOWN,
    /** Its last review failed ([si.lanisce.lani.data.Stats.rusty]): it doesn't count until a right answer polishes it. */
    RUSTY,
}

/**
 * A word of a building: word [id] of pack [pack], [word] in the village's language, [meaning] in the learner's base, and
 * how it stands ([state]). [learn]: the pack's word, to teach it (a [WordState.NEW] word always has it); [card]: the
 * learner's card of it (its own, or another with the same word), to review it (a [WordState.TRIED] or [WordState.RUSTY]
 * word always has it).
 */
data class NeedWord(
    val pack: String,
    val id: String,
    val word: String,
    val meaning: String,
    val emoji: String?,
    val state: WordState,
    val learn: PackWord? = null,
    val card: ReviewCard? = null,
)

/**
 * What the upgrade of building [building] to [toLevel] asks for besides the resources: [need] of its [words] known
 * ([WordState.KNOWN]), and from [BuildingWords.NO_RUST_FROM] on none of them rusty. [from]: where the words are from, its
 * place's name ("🍳 V kuhinji · In the kitchen") or its pack's.
 */
data class UpgradeWords(
    val building: String,
    val toLevel: Int,
    val words: List<NeedWord>,
    val need: Int,
    val from: String? = null,
) {
    /** The words known. */
    val have: Int get() = words.count { it.state == WordState.KNOWN }

    /** The rusty words: they don't count, and from [BuildingWords.NO_RUST_FROM] on they hold the upgrade back. */
    val rusty: List<NeedWord> get() = words.filter { it.state == WordState.RUSTY }

    /** Rust holds this upgrade back: a level from [BuildingWords.NO_RUST_FROM] on, and a rusty word among the words. */
    val rustBlocks: Boolean get() = toLevel >= BuildingWords.NO_RUST_FROM && rusty.isNotEmpty()

    /** Nothing in the words holds the upgrade back. */
    val done: Boolean get() = have >= need && !rustBlocks

    /**
     * The words to practise for it, in the order a run takes them: the rusty ones (every one of them from
     * [BuildingWords.NO_RUST_FROM] on), then those met but not answered right yet, then new ones, each in the building's
     * order, as many as are still missing. Empty when [done].
     */
    val missing: List<NeedWord>
        get() {
            val open = words.filter { it.state != WordState.KNOWN }.sortedBy { ORDER.indexOf(it.state) }
            val rust = if (toLevel >= BuildingWords.NO_RUST_FROM) rusty else emptyList()
            val more = (need - have - rust.size).coerceAtLeast(0)
            return rust + open.filter { it !in rust }.take(more)
        }

    private companion object {
        /** A rusty word is a quick polish, a word met a quick review; a new word is learned. */
        val ORDER = listOf(WordState.RUSTY, WordState.TRIED, WordState.NEW)
    }
}

/**
 * A practice run of an upgrade's missing words ("🎯 Vadi jih zdaj · Practise them now"): [learn], the new words to teach
 * (as a pack does: met, then asked twice), and [review], the words met or rusty to review once.
 */
data class UpgradeRun(val learn: List<NeedWord>, val review: List<NeedWord>) {
    val isEmpty: Boolean get() = learn.isEmpty() && review.isEmpty()
    val size: Int get() = learn.size + review.size
}

/**
 * What a practice run's answers leave: the new words' qualities by pack ([learned]: pack → word id → quality, saved like a
 * pack's) and the reviewed cards' ([reviewed]: card id → quality, saved like a review). A word never answered (the run
 * was left) isn't in either: it waits for the next run.
 */
data class RunResults(val learned: Map<String, List<Pair<String, Int>>>, val reviewed: List<Pair<String, Int>>)

/**
 * The learner's words as a building's upgrade sees them: every card ([cards]: the dashboard's pool), those that count as
 * learned ([learned]) and those rusty ([rusty]). A pack word is had by its own card or by another card with the same word
 * (as the node sees it: [PackSession.wordKeys]); a word marked homonym only by a card that means it.
 */
class WordBook(cards: List<ReviewCard>, private val learned: Set<String>, private val rusty: Set<String>) {
    private val byId = cards.associateBy { it.id }
    private val byWord: Map<String, List<ReviewCard>> = HashMap<String, MutableList<ReviewCard>>().also { m ->
        for (c in cards) if (c.kind == "vocabulary") for (k in PackSession.wordKeys(c.front)) m.getOrPut(k) { mutableListOf() } += c
    }

    /**
     * The learner's card of word [id] of [pack] ([word]): its own, else one with the same word; null when there is none.
     * [homonym]: the word's meanings when it is marked one ([PackWord.meanings], [si.lanisce.lani.game.scene.SceneObject.meanings]):
     * another card must mean it, the kitchen's bowl isn't the green's.
     */
    fun card(pack: String, id: String, word: String, homonym: Set<String>? = null): ReviewCard? {
        byId[PackSession.itemId(pack, id)]?.let { return it }
        val same = byWord[PackSession.wordKey(word)] ?: return null
        return if (homonym == null) same.first() else same.firstOrNull { PackSession.means(it.back, homonym) }
    }

    /** How a word with [card] stands (null: none, a new word). */
    fun state(card: ReviewCard?): WordState = when {
        card == null -> WordState.NEW
        card.id in rusty -> WordState.RUSTY
        card.id in learned -> WordState.KNOWN
        else -> WordState.TRIED
    }

    companion object {
        /** The learner's words in [d] (the home language's dashboard). */
        fun of(d: Dashboard): WordBook = WordBook(d.pool + d.rusty, d.learned, d.rusty.map { it.id }.toSet())
    }
}

/**
 * "Znanje gradi · Knowledge builds": the words a building's upgrade asks for (companion/GAME.md, "Upgrades").
 *
 * **The building's words**, in order: the things in its close-up scene as it is now ([Homes.scenesOf]: a house its own
 * room; the scene seen in this building, at its level: [Homes.world]), one per word; then, where those are fewer than the
 * upgrade asks for, the words of its scene's pack and of its type's pack in its culture ([PACKS]), in the pack's order. A
 * building without a scene (a house nobody's room is in yet, the palisade on a phone whose content is from before "Pri
 * vratih", every building of a culture whose scenes are the fire's and the horizon's) has its type's pack alone. A word the phone can't teach (its pack not on the phone yet, and no
 * card of it) isn't asked for.
 *
 * **How many** ([NEED]): 8 of them known for level 2, 14 for level 3, 20 for level 4, 26 for level 5 (or all there are,
 * when fewer): each level asks for the words before it and 6 to 8 more, one short run of new words (about half a minute a
 * word: met, then asked twice). Level 2 asks for what is in the building at level 1; upgraded, the room shows more things
 * (the kitchen's tiled stove and the table laid at 2), and the next level asks for those. From level 3 ([NO_RUST_FROM]) a
 * rusty word among them holds the upgrade back until it's polished (about ten seconds).
 */
object BuildingWords {
    /** How many of the building's words an upgrade to a level asks for known: each level's words and those before it. */
    val NEED: Map<Int, Int> = mapOf(2 to 8, 3 to 14, 4 to 20, 5 to 26)

    /** From this level on, a rusty word among the building's words holds its upgrade back until it's polished. */
    const val NO_RUST_FROM = 3

    /** A practice run teaches at most this many new words (a word pack's run takes as many at once) … */
    const val RUN_NEW = 8

    /** … and reviews at most this many met or rusty ones (one answer each, about ten seconds). */
    const val RUN_REVIEW = 12

    /**
     * The pack of each building type in each culture (by culture pack id): the words a building asks for where its scene
     * has too few or it has none (after its scene's things and its scene's own pack: [packsOf]). Each culture's own, its
     * building's scene's pack: the tent's, the field's (for the hayrack too, which stands at its edge), the square's for the
     * well and the linden, the kitchen's for the hut, the bee house's, the watchtower's, the smithy's, the church's, the
     * school's and the market's; a house whose room nobody lives in yet the living room's (Primorska's the home's); the
     * palisade the gate's, and in Primorska and Friuli the forest's, of whose trees its stakes are. Until polish-content
     * Friuli, Kärnten and the Lake District gave their buildings the language's starter packs of what each is for (food for
     * the field, the family for the house: cibo, essen, food …), from before they had rooms.
     */
    val PACKS: Map<String, Map<BuildingType, String>> = mapOf(
        "primorska" to mapOf(
            TENT to "v-sotoru", FIELD to "na-njivi", WELL to "na-vasi", HUT to "v-kuhinji", KOZOLEC to "na-njivi",
            BEEHIVE to "pri-cebelnjaku", PALISADE to "v-gozdu", WATCHTOWER to "na-stolpu", SMITHY to "v-kovacnici",
            LIPA to "na-vasi", HOUSE to "dom-in-hisa", CHURCH to "v-cerkvi", SCHOOL to "v-soli", MARKET to "na-trznici",
        ),
        "friuli" to mapOf(
            TENT to "nella-tenda", FIELD to "nel-campo", WELL to "in-piazza", HUT to "in-cucina", KOZOLEC to "nel-campo",
            BEEHIVE to "dalle-api", PALISADE to "nel-bosco", WATCHTOWER to "sulla-torre", SMITHY to "nella-fucina",
            LIPA to "in-piazza", HOUSE to "in-salotto", CHURCH to "in-chiesa", SCHOOL to "a-scuola", MARKET to "al-mercato",
        ),
        "kaernten" to mapOf(
            TENT to "im-zelt", FIELD to "auf-dem-feld", WELL to "auf-dem-dorfplatz", HUT to "in-der-kueche", KOZOLEC to "auf-dem-feld",
            BEEHIVE to "beim-bienenhaus", PALISADE to "am-tor", WATCHTOWER to "auf-dem-wachturm", SMITHY to "in-der-schmiede",
            LIPA to "auf-dem-dorfplatz", HOUSE to "in-der-stube", CHURCH to "in-der-kirche", SCHOOL to "in-der-schule", MARKET to "auf-dem-markt",
        ),
        "lakeland" to mapOf(
            TENT to "in-the-tent", FIELD to "in-the-field", WELL to "on-the-green", HUT to "in-the-kitchen", KOZOLEC to "in-the-field",
            BEEHIVE to "at-the-bees", PALISADE to "at-the-gate", WATCHTOWER to "on-the-watchtower", SMITHY to "in-the-smithy",
            LIPA to "on-the-green", HOUSE to "in-the-living-room", CHURCH to "in-church", SCHOOL to "at-school", MARKET to "at-the-market",
        ),
    )

    /** [type]'s pack in [culture] ([PACKS]); null for a culture without one. */
    fun packOf(type: BuildingType, culture: String = Cultures.current.id): String? = PACKS[culture]?.get(type)

    /** The scenes [b] opens, each seen in it as it is now: what the learner sees stepping in. */
    private fun rooms(state: GameState, b: Building, scenes: List<SceneSpec>): List<SceneSpec> =
        Homes.scenesOf(b, scenes, state).map { it.inWorld(Homes.world(it, state, b.id, scenes)) }

    /** The packs [b]'s words can come from, for the phone to have them: its scenes' things', its scenes', its type's. */
    fun packsOf(state: GameState, b: Building, scenes: List<SceneSpec>, culture: String = Cultures.current.id): List<String> {
        val rooms = rooms(state, b, scenes)
        return (rooms.flatMap { r -> r.objects.mapNotNull { it.pack ?: r.pack } } + rooms.mapNotNull { it.pack } + listOfNotNull(packOf(b.type, culture))).distinct()
    }

    /**
     * What the upgrade of building [buildingId] to its next level asks for, with how each word stands in [book]: from its
     * scenes ([scenes], the phone's) and the packs on the phone ([packs]). Null when it's at the top level, or it has no
     * words the phone knows of (the upgrade then asks for none).
     */
    fun forUpgrade(
        state: GameState, buildingId: String, scenes: List<SceneSpec>, packs: Map<String, Pack>, book: WordBook,
        culture: String = Cultures.current.id,
    ): UpgradeWords? {
        val b = state.buildings.firstOrNull { it.id == buildingId } ?: return null
        val to = b.level + 1
        val need = NEED[to] ?: return null
        val rooms = rooms(state, b, scenes)
        val words = ArrayList<NeedWord>()
        val ids = HashSet<String>()
        val keys = HashSet<String>()
        fun add(pack: String, id: String, said: String, meant: String, emoji: String?, homonym: Set<String>? = null) {
            val learn = packs[pack]?.words?.firstOrNull { it.id == id }
            val word = learn?.word ?: said
            val meanings = learn?.takeIf { it.homonym }?.meanings ?: homonym
            // one per word; a homonym's two meanings are two words
            val key = PackSession.wordKey(word).let { k -> if (meanings != null && k.isNotEmpty()) "$k#${meanings.sorted().joinToString()}" else k }
            if (key.isEmpty() || "$pack/$id" in ids || key in keys) return
            val card = book.card(pack, id, word, meanings)
            val st = book.state(card)
            if (st == WordState.NEW && learn == null) return // not to be taught here yet: not asked for
            ids += "$pack/$id"
            keys += key
            words += NeedWord(pack, id, word, learn?.meaning ?: meant, learn?.emoji ?: emoji, st, learn, card)
        }
        for (r in rooms) for (o in r.objects) (o.pack ?: r.pack)?.let { add(it, o.word, o.sl, o.en, o.emoji, o.meanings) }
        val more = (rooms.mapNotNull { it.pack } + listOfNotNull(packOf(b.type, culture))).distinct()
        var fromPack: Pack? = null
        for (p in more) {
            val pack = packs[p] ?: continue
            for (w in pack.words) {
                if (words.size >= need) break
                val before = words.size
                add(p, w.id, w.word, w.meaning, w.emoji)
                if (words.size > before && fromPack == null) fromPack = pack
            }
        }
        if (words.isEmpty()) return null
        val from = rooms.firstOrNull()?.let { "${it.emoji} ${it.title}" } ?: fromPack?.let { "${it.emoji} ${it.title}" }
        return UpgradeWords(b.id, to, words, minOf(need, words.size), from)
    }

    /** The run of [w]'s missing words: at most [RUN_NEW] new ones to teach and [RUN_REVIEW] to review, in [w]'s order. */
    fun run(w: UpgradeWords): UpgradeRun {
        val missing = w.missing
        return UpgradeRun(
            learn = missing.filter { it.state == WordState.NEW && it.learn != null }.take(RUN_NEW),
            review = missing.filter { it.state != WordState.NEW && it.card != null }.take(RUN_REVIEW),
        )
    }

    /** The review card a new word becomes ([PackSession.card]); [known]: as asked the second time. */
    private fun cardOf(w: NeedWord, known: Boolean = false): ReviewCard = PackSession.card(w.pack, w.learn!!, known)

    /**
     * The run's questions: the new words asked a first time (recognition, as a pack's first round), the met and rusty
     * words reviewed, then the new words asked again (recall), never the same word twice in a row. Distractors come from
     * the run's words and [pool].
     */
    fun plan(run: UpgradeRun, pool: List<ReviewCard>, canSpeak: Boolean, canRecognize: Boolean = false, random: Random = Random.Default): List<ReviewPlanner.Task> {
        val first = run.learn.map { cardOf(it) }
        val reviews = run.review.map { it.card!! }
        val distractors = first + reviews + pool
        val a = ReviewPlanner.plan(first.shuffled(random), distractors, canSpeak, random)
        val b = ReviewPlanner.plan(reviews.shuffled(random), distractors, canSpeak, random, canRecognize)
        var c = ReviewPlanner.plan(run.learn.map { cardOf(it, known = true) }.shuffled(random), distractors, canSpeak, random)
        val before = (a + b).lastOrNull()
        if (c.size > 1 && c.first().card.id == before?.card?.id) c = c.drop(1) + c.first()
        return a + b + c
    }

    /**
     * The qualities the answers ([verdicts], with the [hints] used and whether the cheat sheet was opened during each
     * ([lookedUp]), one each for the first tasks of [tasks]: a run left early has fewer) leave: a new word its weakest
     * answer's ([PackSession.quality]), a reviewed card its answer's ([ReviewPlanner.quality]), each lowered by the hints, a
     * right one looked up at most 4 ([Hints.quality]). Words without an answer aren't in them.
     */
    fun results(run: UpgradeRun, tasks: List<ReviewPlanner.Task>, verdicts: List<Verdict>, hints: List<Int> = emptyList(), lookedUp: List<Boolean> = emptyList()): RunResults {
        val fresh = run.learn.associateBy { PackSession.itemId(it.pack, it.id) }
        val reviewed = run.review.mapNotNull { it.card?.id }.toSet()
        val learned = LinkedHashMap<String, MutableMap<String, Int>>()
        val cards = LinkedHashMap<String, Int>()
        for ((i, v) in verdicts.withIndex()) {
            val t = tasks.getOrNull(i) ?: break
            val h = hints.getOrElse(i) { 0 }
            val looked = lookedUp.getOrElse(i) { false }
            val w = fresh[t.card.id]
            if (w != null) {
                val q = Hints.quality(PackSession.quality(v), h, looked)
                val m = learned.getOrPut(w.pack) { LinkedHashMap() }
                m[w.id] = minOf(m[w.id] ?: q, q)
            } else if (t.card.id in reviewed) {
                val q = Hints.quality(ReviewPlanner.quality(t.variant, v), h, looked)
                cards[t.card.id] = minOf(cards[t.card.id] ?: q, q)
            }
        }
        return RunResults(learned.mapValues { (_, m) -> m.toList() }, cards.toList())
    }
}
