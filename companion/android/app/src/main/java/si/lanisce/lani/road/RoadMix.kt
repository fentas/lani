package si.lanisce.lani.road

import kotlinx.serialization.Serializable
import si.lanisce.lani.data.OutboxEntry
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.data.Writes
import si.lanisce.lani.data.Grading

/** The sessions of the browse tree, and what plays in which order in each. Pure: the service and the tests share it. */
object RoadMix {
    /** How long a "🚗 Za pot · For the road" block is; the next block follows with new items. */
    const val BLOCK_MINUTES = 30

    /** A story's evening about every this many minutes of a block. */
    const val STORY_EVERY_MINUTES = 10

    /** A new word comes again after this many more items. */
    const val AGAIN_AFTER = 3

    /** A short burst of drills ([RoadDrills.burst]) about every this many minutes of a block … */
    const val DRILL_EVERY_MINUTES = 7

    /** … the first after this many. */
    const val DRILL_FIRST_MINUTES = 4

    /** The day's seed of the rapid fire's order ([RoadDrills.rapid]): [today] (ISO). */
    fun seed(today: String): Long = today.hashCode().toLong()

    /** The sessions of the browse tree ([RoadService]'s ids) in its order: the drills' ([RoadDrills]) after the others. */
    val SESSIONS = listOf(
        RoadService.MIX, RoadService.EASY, RoadService.SHADOW, RoadService.REVIEWS, RoadService.WORDS, RoadService.DIALOGS,
        RoadService.STORIES, RoadService.TRANSFORMS, RoadService.RAPID, RoadService.BUILDS, RoadService.RIDDLES,
    )

    /**
     * What session [id] plays ([SESSIONS]), in its order, before the files on the phone are checked: "🚗 Za pot" its first
     * block; nothing for an id that isn't a session.
     */
    fun session(id: String, lib: RoadLibrary, heard: Map<String, Long>, today: String): List<RoadItem> = when (id) {
        RoadService.MIX -> block(lib, heard, today)
        RoadService.EASY -> easy(lib, heard)
        RoadService.SHADOW -> shadow(lib, heard)
        RoadService.REVIEWS -> reviews(lib, heard, today)
        RoadService.WORDS -> words(lib, heard)
        RoadService.DIALOGS -> dialogs(lib, heard)
        RoadService.STORIES -> stories(lib, heard)
        RoadService.TRANSFORMS -> RoadDrills.transforms(lib, heard)
        RoadService.RAPID -> RoadDrills.rapid(lib, heard, seed(today))
        RoadService.BUILDS -> RoadDrills.builds(lib, heard)
        RoadService.RIDDLES -> RoadDrills.riddles(lib, heard)
        else -> emptyList()
    }

    /** The flash items' and dialogs' turn in a block: two cards, two words, a dialog. */
    private val SLOTS = listOf(Kind.CARD, Kind.WORD, Kind.CARD, Kind.WORD, Kind.DIALOG)

    /** Least recently heard first; never heard first of all, in the library's order. */
    fun fresh(items: List<RoadItem>, heard: Map<String, Long>): List<RoadItem> =
        items.withIndex().sortedWith(compareBy({ heard[it.value.id] ?: 0L }, { it.index })).map { it.value }

    /** "🔁 Ponavljanje · Reviews": today's due cards, least recently heard first. */
    fun reviews(lib: RoadLibrary, heard: Map<String, Long>, today: String): List<RoadItem> = fresh(lib.due(today), heard)

    /** "🆕 Besede · New words": the words, least recently heard first, each heard again a few items later. */
    fun words(lib: RoadLibrary, heard: Map<String, Long>): List<RoadItem> = withAgain(fresh(lib.of(Kind.WORD), heard))

    /** "💬 Pogovori · Dialogs". */
    fun dialogs(lib: RoadLibrary, heard: Map<String, Long>): List<RoadItem> = fresh(lib.of(Kind.DIALOG), heard)

    /** "📖 Zgodbe · Stories": the least recently heard story first, its evenings in their order. */
    fun stories(lib: RoadLibrary, heard: Map<String, Long>): List<RoadItem> {
        val byStory = lib.of(Kind.STORY).groupBy { storyOf(it.id) }
        return byStory.entries.withIndex()
            .sortedWith(compareBy({ g -> g.value.value.maxOf { heard[it.id] ?: 0L } }, { it.index }))
            .flatMap { it.value.value.sortedBy(::chapterOf) }
    }

    /**
     * "🌙 Mirno · Easy listening", for heavy traffic or tiredness: only the stories and the dialogs, played straight
     * ([RoadPlay.easy]: nothing to say): an evening of a story, then about [STORY_EVERY_MINUTES] minutes of dialogs, again;
     * the least recently heard first.
     */
    fun easy(lib: RoadLibrary, heard: Map<String, Long>): List<RoadItem> {
        val stories = ArrayDeque(stories(lib, heard).map(RoadPlay::easy))
        val dialogs = ArrayDeque(dialogs(lib, heard).map(RoadPlay::easy))
        val out = mutableListOf<RoadItem>()
        while (stories.isNotEmpty() || dialogs.isNotEmpty()) {
            stories.removeFirstOrNull()?.let(out::add)
            var seconds = 0.0
            while (dialogs.isNotEmpty() && (seconds < STORY_EVERY_MINUTES * 60.0 || stories.isEmpty())) {
                val d = dialogs.removeFirst()
                out += d
                seconds += d.seconds
            }
        }
        return out
    }

    /** "🗣️ Odmev · Shadowing": the dialogs' short phrases ([RoadPlay.phrase]), the least recently heard first. */
    fun shadow(lib: RoadLibrary, heard: Map<String, Long>): List<RoadItem> = fresh(lib.of(Kind.PHRASE), heard)

    /** "story:zlatorog/2" → "zlatorog". */
    fun storyOf(id: String): String = id.substringBeforeLast('/')

    private fun chapterOf(item: RoadItem): Int = item.id.substringAfterLast('/').toIntOrNull() ?: 0

    /** Each word heard again [AGAIN_AFTER] items after it. */
    fun withAgain(items: List<RoadItem>): List<RoadItem> {
        val out = mutableListOf<RoadItem>()
        val waiting = ArrayDeque<Pair<Int, RoadItem>>()
        for (item in items) {
            out += item
            RoadPlay.again(item)?.let { waiting.addLast(out.size + AGAIN_AFTER to it) }
            while (waiting.isNotEmpty() && waiting.first().first <= out.size) out += waiting.removeFirst().second
        }
        waiting.forEach { out += it.second }
        return out
    }

    /**
     * A "🚗 Za pot · For the road" block of about [minutes]: due cards and new words in turn (each new word heard again a
     * few items later), a dialog after every four, an evening of a story about every [STORY_EVERY_MINUTES] minutes, a short
     * burst of a drill about every [DRILL_EVERY_MINUTES] (their kinds in turn, the least recently heard first:
     * transformations, rapid fire, a sentence built, riddles); the least recently heard first, nothing of [exclude] (queued
     * already) while there is something else. Cards come while there are due ones; a kind without items leaves its turn to
     * the others.
     */
    fun block(
        lib: RoadLibrary,
        heard: Map<String, Long>,
        today: String,
        minutes: Int = BLOCK_MINUTES,
        exclude: Set<String> = emptySet(),
    ): List<RoadItem> {
        fun queue(items: List<RoadItem>) = ArrayDeque(items.filter { it.id !in exclude })
        val queues = mapOf(
            Kind.CARD to queue(reviews(lib, heard, today)),
            Kind.WORD to queue(fresh(lib.of(Kind.WORD), heard)),
            Kind.DIALOG to queue(dialogs(lib, heard)),
        )
        // one evening of a story at a time: the next of the story under way, else of the least recently heard
        val stories = queue(stories(lib, heard))
        val out = mutableListOf<RoadItem>()
        val again = ArrayDeque<Pair<Int, RoadItem>>()
        var seconds = 0.0
        var nextStory = (STORY_EVERY_MINUTES - 2) * 60.0
        var slot = 0
        // the drills' bursts: their kinds in turn, the one heard least recently first; each item once in the block
        val drillKinds = RoadDrills.kinds(lib, heard)
        val drilled = exclude.toMutableSet()
        var burst = 0
        var nextDrill = DRILL_FIRST_MINUTES * 60.0
        fun add(item: RoadItem) {
            out += item
            seconds += item.seconds
        }
        while (seconds < minutes * 60.0) {
            while (again.isNotEmpty() && again.first().first <= out.size) add(again.removeFirst().second)
            if (seconds >= nextStory && stories.isNotEmpty()) {
                add(stories.removeFirst())
                nextStory = seconds + STORY_EVERY_MINUTES * 60.0
                continue
            }
            if (seconds >= nextDrill && drillKinds.isNotEmpty()) {
                nextDrill = seconds + DRILL_EVERY_MINUTES * 60.0
                val items = drillKinds.indices.asSequence()
                    .map { RoadDrills.burst(lib, heard, drillKinds[(burst + it) % drillKinds.size], seed(today), drilled) }
                    .firstOrNull { it.isNotEmpty() }
                burst++
                if (items != null) {
                    items.forEach { add(it); drilled += it.id }
                    continue
                }
            }
            val skip = SLOTS.indices.firstOrNull { queues.getValue(SLOTS[(slot + it) % SLOTS.size]).isNotEmpty() }
            if (skip == null) {
                // no cards, words or dialogs left: the stories, then what waits to be heard again
                if (stories.isNotEmpty()) { add(stories.removeFirst()); continue }
                if (again.isNotEmpty()) { add(again.removeFirst().second); continue }
                break
            }
            val kind = SLOTS[(slot + skip) % SLOTS.size]
            slot += skip + 1
            val item = queues.getValue(kind).removeFirst()
            add(item)
            if (kind == Kind.WORD) RoadPlay.again(item)?.let { again.addLast(out.size + AGAIN_AFTER to it) }
        }
        // everything queued already (a long drive): round again, the least recently heard first
        if (out.isEmpty() && exclude.isNotEmpty()) return block(lib, heard, today, minutes)
        return out
    }

    /** Hours of material: every item once. */
    fun hours(lib: RoadLibrary): Double = lib.seconds / 3600.0
}

/** A rating pressed in the car: [id] and [pack] as the item's [Rate], the SM-2 [quality], when ([at], epoch ms). */
@Serializable
data class Rating(val id: String, val pack: String? = null, val quality: Int, val at: Long)

object RoadRatings {
    /** "✓ Znal sem · I knew it": what a spoken card answer said right earns ([ReviewPlanner.Variant.SPEAK]). */
    val KNEW: Int = ReviewPlanner.quality(ReviewPlanner.Variant.SPEAK, Grading.Verdict.CORRECT)

    /** "✗ Nisem · I didn't": a spoken answer that was wrong. */
    val DIDNT: Int = ReviewPlanner.quality(ReviewPlanner.Variant.SPEAK, Grading.Verdict.WRONG)

    /** [ratings] with [r] added: an item rated again keeps its last rating. */
    fun add(ratings: List<Rating>, r: Rating): List<Rating> = ratings.filterNot { it.id == r.id && it.pack == r.pack } + r

    /**
     * The writes for the node (through the app's outbox): the review cards' answers as one review session (POST /reviews),
     * each pack's words as one learn session (POST /packs/<id>/learn); the minutes from the first rating to the last.
     */
    fun writes(ratings: List<Rating>, id: () -> String = { si.lanisce.lani.data.Outbox.newId() }, now: Long = System.currentTimeMillis()): List<OutboxEntry> {
        if (ratings.isEmpty()) return emptyList()
        val minutes = (((ratings.maxOf { it.at } - ratings.minOf { it.at }) / 60_000L).toInt() + 1).coerceIn(1, 240)
        val out = mutableListOf<OutboxEntry>()
        val cards = ratings.filter { it.pack == null }
        if (cards.isNotEmpty()) out += Writes.reviews(cards.map { it.id to it.quality }, minutes, id = id(), now = now)
        for ((pack, words) in ratings.filter { it.pack != null }.groupBy { it.pack!! }) {
            out += Writes.learnPack(pack, words.map { it.id to it.quality }, minutes, id = id(), now = now)
        }
        return out
    }
}
