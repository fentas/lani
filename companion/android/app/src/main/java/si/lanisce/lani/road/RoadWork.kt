package si.lanisce.lani.road

import kotlinx.serialization.Serializable

/**
 * What getting ready still has to do (road/plan.json, [RoadStore.readPlan]): the library gathered from the node and where
 * its clips are. It stays until every item's files are on the phone or couldn't be had ([RoadPrepWork]), so a run
 * stopped halfway (the app or the phone stopped) goes on where it stopped when the app opens again.
 */
@Serializable
data class RoadPlan(
    val library: RoadLibrary,
    /** A clip's URL on the node by its file name ([RoadPlay.fileOf]). */
    val urls: Map<String, String> = emptyMap(),
    /** The phone's clip cache ([si.lanisce.lani.data.Clips.dir]): a clip there is copied, not downloaded. */
    val cache: String? = null,
)

/** In which order the road gets ready, and from when it plays: pure, so the tests check it. */
object RoadWork {
    /**
     * The library's items in the order their files are got ready, what plays first first ([items]): the first block of
     * "🚗 Za pot" ([RoadMix.block], the first [first] items: once they are on the phone the sessions can start); then each
     * session of the browse tree a little at a time, the one with the fewest minutes ready first, so all of them grow
     * alike; then what no session plays today (the cards due in the next days).
     */
    data class Order(val items: List<RoadItem>, val first: Int)

    fun order(lib: RoadLibrary, heard: Map<String, Long>, today: String): Order {
        val byId = lib.items.associateBy { it.id }
        val out = LinkedHashMap<String, RoadItem>()
        // a word heard again ("…#again") or an item played straight ("…#easy") plays its library item's files
        fun take(item: RoadItem) {
            val base = byId[item.id.substringBefore('#')] ?: return
            out.putIfAbsent(base.id, base)
        }
        RoadMix.block(lib, heard, today).forEach(::take)
        val first = out.size
        // the quiz's fixed phrases next: without them no question plays
        lib.of(Kind.KIT).forEach(::take)
        val queues = RoadMix.SESSIONS.filter { it != RoadService.MIX }.map { ArrayDeque(RoadMix.session(it, lib, heard, today)) }
        val minutes = DoubleArray(queues.size)
        while (true) {
            val i = queues.indices.filter { queues[it].isNotEmpty() }.minByOrNull { minutes[it] } ?: break
            val item = queues[i].removeFirst()
            take(item)
            minutes[i] += item.seconds / 60
        }
        lib.items.forEach(::take)
        return Order(out.values.toList(), first)
    }

    /**
     * QA's small library (app/QaHooks, "road:mini"): the first [each] items of every kind, got ready in a minute; of the
     * quiz [quiz] questions (the kinds in turn), and its fixed phrases.
     */
    fun mini(lib: RoadLibrary, each: Int = 2, quiz: Int = 8): RoadLibrary {
        val count = HashMap<Kind, Int>()
        return lib.copy(items = lib.items.filter { count.merge(it.kind, 1, Int::plus)!! <= if (it.kind == Kind.QUIZ) quiz else each })
    }

    /** The clip files and the prompts' texts [item] plays, each once. */
    fun clips(item: RoadItem): List<String> = item.sounds.filterIsInstance<Sound.Clip>().flatMap { it.files }.distinct()

    fun prompts(item: RoadItem): List<String> = item.sounds.filterIsInstance<Sound.Prompt>().map { it.text }.distinct()

    /** The target-language texts [item] has voiced while getting ready ([Sound.Spoken]), each once. */
    fun spoken(item: RoadItem): List<Sound.Spoken> = item.sounds.filterIsInstance<Sound.Spoken>().distinct()
}
