package si.lanisce.lani.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import si.lanisce.lani.l10n.bi
import java.util.UUID

// Learning progress between linked towns (companion/README.md, "Progress between towns"). The sharer decides: each
// learner sets, per linked town, what it sees of their learning (a [Sharing] level: just the village, a summary, details).
// The app asks its own bridge: GET /towns/:id/progress (a linked town's progress at the level it shares with this one,
// with how this learner's challenges to it went), PUT /towns/:id/sharing (what this learner shares with that town; a
// parent's weekly note of it) and POST /towns/:id/challenges (a challenge to its learner, as a family challenge there).
// A child's guardian's town (a parent's) sees details, and the child's app can't choose less ([Sharing.floor]).

/** What a town shares of its learning with a linked one: [VILLAGE] (nothing of it), [SUMMARY] or [DETAILS]. */
object ShareLevels {
    const val VILLAGE = "village"
    const val SUMMARY = "summary"
    const val DETAILS = "details"
    val ALL = listOf(VILLAGE, SUMMARY, DETAILS)

    fun rank(level: String): Int = ALL.indexOf(level).coerceAtLeast(0)
}

/**
 * A link's sharing both ways (GET /towns' `sharing`, and with a progress answer): what this learner shares with that town
 * ([share]; [shareDefault]: the link's default, not chosen), whether that town is this child's guardian's ([guardian]: it
 * sees at least [floor], which the app can't go below: [choices]), what that town said it shares with this one ([theirs]),
 * and whether a weekly note of theirs is on ([weekly]).
 */
@Serializable
data class Sharing(
    val share: String = ShareLevels.VILLAGE,
    @SerialName("share_default") val shareDefault: Boolean = false,
    val guardian: Boolean = false,
    val floor: String? = null,
    val choices: List<String> = ShareLevels.ALL,
    val theirs: TheirShare? = null,
    val weekly: Boolean = false,
)

/** What a linked town shares with this one, as it said when asked last ([at]); [guardian]: this learner is its child's guardian. */
@Serializable
data class TheirShare(val share: String = ShareLevels.VILLAGE, val at: String = "", val guardian: Boolean = false)

@Serializable
data class ProgressTown(val id: String = "", val name: String = "", val learner: String = "", val culture: String = "", val language: String = "")

/** This week (Monday to Sunday): the days practised as seven dots, its minutes, reviews and new words. */
@Serializable
data class ProgressWeek(
    val start: String = "",
    val days: List<Boolean> = emptyList(),
    @SerialName("days_practised") val daysPractised: Int = 0,
    val minutes: Int = 0,
    val reviews: Int = 0,
    @SerialName("new_words") val newWords: Int = 0,
)

@Serializable
data class ProgressWords(val known: Int = 0, val tracked: Int = 0)

@Serializable
data class CourseRef(val id: String = "", val title: Map<String, String> = emptyMap())

/** The school at the learner's level: its units done of all, and the one in progress. */
@Serializable
data class ProgressCourse(val level: String = "", val done: Int = 0, val of: Int = 0, val current: CourseRef? = null)

@Serializable
data class ProgressSummary(
    val streak: Int = 0,
    @SerialName("practised_today") val practisedToday: Boolean = false,
    val week: ProgressWeek = ProgressWeek(),
    val words: ProgressWords = ProgressWords(),
    val level: String = "A1",
    @SerialName("target_level") val targetLevel: String? = null,
    val course: ProgressCourse? = null,
)

/** A card being learned: its [word], its [meanings] by language, when it was [added], and whether it is [STATE_RUSTY] etc. */
@Serializable
data class ProgressWord(
    val id: String = "",
    val word: String = "",
    val meanings: Map<String, String> = emptyMap(),
    val added: String? = null,
    val state: String = "learning",
)

@Serializable
data class HardItem(val id: String = "", val word: String = "", val meanings: Map<String, String> = emptyMap())

/** A card failed lately: [misses] reviews below 3 in the last four weeks (or rusty), the [last] one's day. */
@Serializable
data class HardWord(val id: String = "", val word: String = "", val meanings: Map<String, String> = emptyMap(), val misses: Int = 0, val last: String? = null)

/** An error pattern with mistakes lately: its [name], how often ([count]), its [trend], and the cards its mistakes name ([items]). */
@Serializable
data class HardPattern(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val count: Int = 0,
    val last: String? = null,
    val trend: String = "steady",
    val items: List<HardItem> = emptyList(),
)

@Serializable
data class ProgressHard(val patterns: List<HardPattern> = emptyList(), val words: List<HardWord> = emptyList())

@Serializable
data class ProgressDue(val today: Int = 0, val week: Int = 0)

@Serializable
data class ProgressDetails(val learning: List<ProgressWord> = emptyList(), val hard: ProgressHard = ProgressHard(), val due: ProgressDue = ProgressDue())

/** A challenge this learner sent the town's learner, and how it went: [answered], the tutor's [score] and [note] for the sender. */
@Serializable
data class ChallengeResult(
    val id: String = "",
    val at: String = "",
    val type: String = "translate",
    val text: String = "",
    val answered: Boolean = false,
    @SerialName("answered_at") val answeredAt: String? = null,
    val score: Double? = null,
    val note: String? = null,
)

/**
 * A linked town's progress as it shares it with this learner's (GET /towns/:id/progress): its [level] ("village": nothing
 * of the learning; "summary": [summary]; "details": [details] too), whether it is a [child]'s and this learner its
 * [guardian], the language its cards' meanings are in ([base]), this learner's [challenges] to it, when it was had
 * ([fetchedAt], [stale] while the town is away), and the link's [sharing] both ways.
 */
@Serializable
data class TownProgress(
    val v: Int = 1,
    val town: ProgressTown = ProgressTown(),
    val level: String = ShareLevels.VILLAGE,
    val date: String = "",
    val child: Boolean = false,
    val guardian: Boolean = false,
    val base: String? = null,
    val summary: ProgressSummary? = null,
    val details: ProgressDetails? = null,
    val challenges: List<ChallengeResult> = emptyList(),
    @SerialName("fetched_at") val fetchedAt: String? = null,
    val stale: Boolean = false,
    val sharing: Sharing? = null,
)

/** An error answer of GET /towns/:id/progress: why not ([code]), with the link's [sharing] (what this learner shares, anyway). */
@Serializable
data class ProgressProblem(val error: String = "", val code: String = "", val sharing: Sharing? = null)

object TownProgresses {
    const val STATE_LEARNING = "learning"
    const val STATE_KNOWN = "known"
    const val STATE_RUSTY = "rusty"

    /** A challenge's kinds, as the family's (data/Family.kt): a question, something to translate, something to say. */
    val CHALLENGE_TYPES = listOf("translate", "question", "say")
    const val MAX_TEXT = 500
    const val MAX_HINT = 300

    fun parse(body: String): TownProgress = json.decodeFromString(TownProgress.serializer(), body)

    fun parseProblem(body: String): ProgressProblem = runCatching { json.decodeFromString(ProgressProblem.serializer(), body) }.getOrDefault(ProgressProblem())

    fun parseSharing(body: String): Sharing = json.decodeFromString(Sharing.serializer(), body)

    /** A new challenge's id (a retry sends the same). */
    fun newId(): String = "ch-" + UUID.randomUUID().toString().replace("-", "").take(20)

    /** Why a town's progress can't be had now, "target · base". */
    fun problem(status: Int, code: String?): String = when (code) {
        "unreachable" -> "📡 ${bi("towns.cantBeReached")}"
        "refused" -> "🔒 ${bi("towns.noLongerKnowsYou")}"
        "unsupported" -> "🧰 ${bi("townProgress.olderTown")}"
        "unknown_town" -> "🔒 ${bi("towns.noLongerKnowsYou")}"
        "guardian" -> "👪 ${bi("townProgress.parentDecides")}"
        "not_for_a_child" -> "👨‍👩‍👦 ${bi("townProgress.noLinksForChild")}"
        "too_many_open" -> "⏳ ${bi("townProgress.tooManyOpen")}"
        "rate_limited" -> "⏳ ${bi("towns.tooManyTries")}"
        else -> if (status == 404) "🧰 ${bi("towns.tutorTooOld")}" else "⚠️ ${bi("towns.somethingWentWrong", "status" to status)}"
    }
}
