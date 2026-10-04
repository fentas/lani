package si.lanisce.lani.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import si.lanisce.lani.l10n.bi

// Friendship between towns (plan 2, §3.3; companion/README.md, "Friendship between towns"). The app asks its own bridge:
// GET /towns/friendship (each linked town's level as both towns agree it, what it brings, the friend's trouble, the
// feasts, the moves), POST /towns/:id/aid (send help against a friend's trouble), POST /towns/:id/moves (offer a move)
// and POST /towns/:id/moves/:move/accept or /decline. What it brings the village is applied in game/TownFriendship.kt.

/** A market's rates for this learner's town, by the friendship (the host's): the least share, the least haggle score, how many of a good it spares. */
@Serializable
data class MarketRates(
    val level: Int = 0,
    @SerialName("fair_share") val fairShare: Double = 0.5,
    @SerialName("deal_score") val dealScore: Int = TownActs.DEAL_SCORE,
    @SerialName("max_spare") val maxSpare: Int = 3,
)

@Serializable
data class NextLevel(val level: Int = 0, val points: Int = 0)

/**
 * Trouble in a friend's town now ([kind]: WOLVES, BEAR, STORM, until [until]): [aided] this town sent help already,
 * [can] the friendship is close enough to send it.
 */
@Serializable
data class FriendTrouble(
    val id: String,
    val kind: String = "",
    val strength: Int = 1,
    val until: String = "",
    val aided: Boolean = false,
    val can: Boolean = false,
)

@Serializable
data class SharedFeast(val key: String, val first: String = "", val last: String = "")

/** The towns' last feasts ([mine], [theirs], ISO dates), the [shared] feast they make, and ([openUntil]) the day to hold one by to share the friend's. */
@Serializable
data class FeastNews(
    val mine: String? = null,
    val theirs: String? = null,
    val shared: SharedFeast? = null,
    @SerialName("open_until") val openUntil: String? = null,
)

@Serializable
data class MoverFrom(val id: String = "", val name: String = "")

/** Someone moving between two towns: a newcomer of the sending town's culture, who speaks its [language]. */
@Serializable
data class Mover(
    val id: String,
    val name: String = "",
    val emoji: String = "🙂",
    val art: String = "woman",
    val voice: String = "female",
    /** Their trade, per language. */
    val role: Map<String, String> = emptyMap(),
    val family: String = "",
    val culture: String = "",
    val language: String = "",
    val from: MoverFrom = MoverFrom(),
)

/**
 * A move between this town and a friend's, from this town's view: [dir] "in" (someone of theirs moves here) or "out"
 * (someone of here moves there); offered [by] "me" or "them"; [status] offered, done or declined.
 */
@Serializable
data class TownMove(
    val key: String,
    val id: String = "",
    val dir: String = "in",
    val by: String = "me",
    val status: String = "offered",
    val at: String = "",
    @SerialName("done_at") val doneAt: String? = null,
    val person: Mover? = null,
) {
    val done: Boolean get() = status == Friendships.DONE
    val open: Boolean get() = status == Friendships.OFFERED
}

/** Whether a move that way can be offered now, else why ([why]: level, skill, child, open, soon, enough), with the day it can ([until]) or the learner's [level]. */
@Serializable
data class MoveCan(val ok: Boolean = false, val why: String? = null, val until: String? = null, val level: String? = null)

/** The learner's level in the friend's town's language, and the level a move in needs. */
@Serializable
data class MoveSkill(val language: String = "", val level: String = "A1", val need: String = "A2")

@Serializable
data class MovesInfo(
    val skill: MoveSkill = MoveSkill(),
    @SerialName("in") val into: MoveCan = MoveCan(),
    val out: MoveCan = MoveCan(),
    val list: List<TownMove> = emptyList(),
)

/**
 * The friendship with one linked town, as both towns agree it: its [level] and [points] ([next]: the level after it),
 * what each town did for the other ([mine], [theirs]: acts by kind), the market's [rates] there, whether it is a
 * [child]'s town, its [event] (trouble now), the [feast]s and the [moves]. [status] "ok", or "stale" (its last answer),
 * "unreachable", "refused", "unsupported" (an older bridge): then the level is what could be agreed.
 */
@Serializable
data class FriendTown(
    val id: String,
    val name: String = "",
    val learner: String = "",
    val culture: String = "",
    val language: String = "",
    val status: String = "",
    val level: Int = 0,
    val points: Int = 0,
    val next: NextLevel? = null,
    val mine: Map<String, Int> = emptyMap(),
    val theirs: Map<String, Int> = emptyMap(),
    @SerialName("mine_points") val minePoints: Int = 0,
    @SerialName("theirs_points") val theirsPoints: Int = 0,
    val rates: MarketRates = MarketRates(),
    val child: Boolean = false,
    val event: FriendTrouble? = null,
    val feast: FeastNews = FeastNews(),
    val moves: MovesInfo = MovesInfo(),
)

@Serializable
data class FriendshipList(val towns: List<FriendTown> = emptyList())

object Friendships {
    /** The level from which a friend's town can send help against trouble, a feast is shared, people move (the bridge's). */
    const val AID_LEVEL = 1
    const val FEAST_LEVEL = 2
    const val MOVE_LEVEL = 3
    /** The points each level starts at (the bridge's FRIEND_LEVELS). */
    val LEVELS = listOf(0, 8, 24, 48, 90)
    const val OFFERED = "offered"
    const val DONE = "done"
    const val DECLINED = "declined"
    const val IN = "in"
    const val OUT = "out"

    /** GET /towns/friendship; null for an older bridge's 404 (the caller checks). */
    fun parse(body: String): FriendshipList = json.decodeFromString(FriendshipList.serializer(), body)

    /** POST /towns/:id/moves (and …/accept, …/decline): the move as it is now. */
    fun parseMove(body: String): TownMove? = runCatching {
        val o = json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
        json.decodeFromJsonElement(TownMove.serializer(), o["move"] ?: return null)
    }.getOrNull()

    /** A new id for an offer or help sent; a retry sends it again. */
    fun newId(kind: String): String = "$kind-${java.util.UUID.randomUUID()}"

    /** Why a friendship's write ([status], the error's [code]) didn't go through, "target · base"; [town] is the friend's village. */
    fun problem(status: Int, code: String?, town: String): String = when (code) {
        "too_early" -> "💞 ${bi("friendship.tooEarly")}"
        "no_event" -> "🌤️ ${bi("friendship.troubleOver")}"
        "already_helped" -> "🤝 ${bi("friendship.helpSent")}"
        "level" -> "💞 ${bi("friendship.moveTooEarly")}"
        "child" -> "👨‍👩‍👦 ${bi("friendship.moveChild")}"
        "open" -> "⏳ ${bi("friendship.moveOpen")}"
        "skill" -> "📚 ${bi("friendship.moveSkillThem")}"
        "soon", "enough" -> "🗓️ ${bi("friendship.moveNotNow")}"
        "declined" -> "🙅 ${bi("friendship.moveDeclined")}"
        "not_yours" -> "⏳ ${bi("friendship.moveWaiting", "town" to town)}"
        else -> TownActs.problem(status, code, town)
    }
}
