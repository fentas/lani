package si.lanisce.lani.game.villagers

import kotlinx.serialization.Serializable
import si.lanisce.lani.game.GameState
import si.lanisce.lani.l10n.bi
import java.time.LocalDate

/** What Jan and one villager share: friendship points, what they remember, when they last met. */
@Serializable
data class Bond(
    val points: Int = 0,
    /** Newest last, at most [Bonds.MAX_MEMORIES]. */
    val memories: List<Memory> = emptyList(),
    /** ISO date they first met. */
    val met: String? = null,
    /** ISO date they last met (a dialog, a quest, a training together). */
    val seen: String? = null,
    /** Points earned on [seen]'s day from trainings, capped by [Bonds.TRAINING_PER_DAY]. */
    val trainedToday: Int = 0,
)

/** Something a villager remembers about Jan, in their words. */
@Serializable
data class Memory(
    /** ISO date */
    val on: String,
    /** "ko si mi pomagal najti Belo" (fits "Še vedno mislim na {memory}"). */
    val sl: String,
    /** "when you helped me find Bela" */
    val en: String = "",
    /** quest | dialog | talk | training | gift | arrival (the day they met: [Arrivals]) | ispy (a game of «Vidim, vidim») */
    val kind: String = "dialog",
)

/** Friendship levels and how they grow. Pure: every change returns a new [GameState]. */
object Bonds {
    const val MAX_MEMORIES = 20

    // Points for helping (see companion/VILLAGERS.md).
    const val QUEST = 10
    const val DIALOG = 5
    const val DIALOG_WITH_MISTAKES = 3
    const val TALK = 6
    const val TRAINING = 1
    const val TRAINING_PER_DAY = 3

    /** Points needed for each level; the index is the level. */
    val THRESHOLDS = listOf(0, 10, 30, 70, 150)

    /** Level names, "target · base"; read each time, so they're in the pair of the moment. */
    val NAMES: List<String> get() = listOf(
        bi("bonds.stranger"),
        bi("bonds.acquaintance"),
        bi("villageSheets.friend"),
        bi("bonds.goodFriend"),
        bi("common.likeFamily"),
    )

    fun level(points: Int): Int = THRESHOLDS.indexOfLast { points >= it }.coerceAtLeast(0)

    fun level(state: GameState, id: String): Int = level(state.bonds[id]?.points ?: 0)

    /** 0..1 of the way to the next level (1 at the top). */
    fun progress(points: Int): Float {
        val l = level(points)
        if (l >= THRESHOLDS.lastIndex) return 1f
        val from = THRESHOLDS[l]; val to = THRESHOLDS[l + 1]
        return (points - from).toFloat() / (to - from)
    }

    /**
     * Jan helped or met villager [id]: add [points] (a training's points are capped per day), mark them
     * seen [today], and keep [memory] if there is one. A level reached may bring their gift (see
     * [si.lanisce.lani.game.Chest.onBond]); its chronicle line is dated [now] (by default the last entry's time).
     * In a village that introduces its people ([Arrivals.on]) only the introduction makes them met ([meet]); anything
     * else before it (a feast for everyone, a greeting) counts, but they're still to meet.
     */
    fun add(
        state: GameState, id: String, points: Int, today: LocalDate, memory: Memory? = null, training: Boolean = false,
        now: Long = state.log.lastOrNull()?.at ?: 0, meet: Boolean = false,
    ): GameState {
        val day = today.toString()
        val b = state.bonds[id] ?: Bond()
        val usedToday = if (b.seen == day) b.trainedToday else 0
        val gain = if (training) points.coerceAtMost(TRAINING_PER_DAY - usedToday).coerceAtLeast(0) else points.coerceAtLeast(0)
        val next = b.copy(
            points = (b.points.toLong() + gain).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            memories = (b.memories + listOfNotNull(memory)).takeLast(MAX_MEMORIES),
            met = b.met ?: day.takeIf { meet || !Arrivals.on(state) },
            seen = day,
            trainedToday = if (training) usedToday + gain else usedToday,
        )
        val added = state.copy(bonds = state.bonds + (id to next))
        return if (level(next.points) > level(b.points)) si.lanisce.lani.game.Chest.onBond(added, id, now).first else added
    }

    /** The level from which a villager counts as a friend ("Prijatelj · Friend"). */
    const val FRIEND = 2

    /**
     * Jan's friends in the village: who lives here at friendship level [FRIEND] or more (everyone Jan knows while
     * nobody has a name yet, an older bridge). A friend who moved away doesn't count.
     */
    fun friends(state: GameState): Int {
        val living = state.residents.map { it.id }.toSet()
        return state.bonds.count { (id, b) -> level(b.points) >= FRIEND && (living.isEmpty() || id in living) }
    }

    /** The lines of [pool] open at the bond's level, deepest first (the warmest they have). */
    fun linesFor(pool: List<VillagerLine>, level: Int): List<VillagerLine> =
        pool.filter { it.level <= level }.sortedByDescending { it.level }
}
