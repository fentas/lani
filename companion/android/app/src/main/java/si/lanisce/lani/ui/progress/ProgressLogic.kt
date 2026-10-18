package si.lanisce.lani.ui.progress

import si.lanisce.lani.data.Counting
import si.lanisce.lani.data.Course
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.Stats
import si.lanisce.lani.data.UnitRef
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.GrammarBook
import si.lanisce.lani.game.HuntStatus
import si.lanisce.lani.game.Introduction
import si.lanisce.lani.game.LevelReadiness
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.game.Readiness
import si.lanisce.lani.game.School
import si.lanisce.lani.game.Station
import si.lanisce.lani.game.TreasureHunt
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import java.time.LocalDate
import kotlin.math.roundToInt

/** A line of the way to the next level: the words, or one of the treasure map's three signs. */
enum class PathSign(val emoji: String) { WORDS("📚"), GRAMMAR("📖"), REVIEWS("🔁"), PRACTICE("📅") }

/**
 * One line of "Do A2 · To A2" ([ProgressLogic.path]): [met] (✅) or not yet (⏳), its [title] and its numbers ([value]),
 * what they are measured against ([caption]), and all of it for TalkBack in the learner's base ([description]).
 */
data class PathLine(
    val sign: PathSign,
    val met: Boolean,
    val title: String,
    val value: String,
    val caption: String?,
    val description: String,
)

/** Where the treasure map stands, for the path's head. */
sealed interface MapState {
    /** Not all three signs yet: storyteller [teller] gives the map once they are. */
    data class Coming(val teller: String) : MapState

    /** Ready, or [teller]'s map offered: it waits to be taken. */
    data class Waiting(val teller: String) : MapState

    /** The hunt is on: [done] of the [n] stations this phone needs passed. */
    data class On(val done: Int, val n: Int) : MapState
}

/** The treasure of [level] found (the level rose to it); [tutorWaits]: the node doesn't have the new level yet. */
data class TreasureFound(val level: String, val tutorWaits: Boolean)

/**
 * "🧭 Do A2 · To A2" on the progress screen: the learner's level [from] and the next one [to], the words and the three
 * signs ([lines]), the level's pages still to secure while the grammar sign isn't met ([toSecure], the book's order, the
 * first [ProgressLogic.TO_SECURE]; [toSecureMore] more), the treasure map's state ([map]), and a treasure just found ([found]).
 */
data class LevelPath(
    val from: String,
    val to: String,
    val lines: List<PathLine>,
    val toSecure: List<String>,
    val map: MapState,
    val found: TreasureFound? = null,
    val toSecureMore: Int = 0,
)

/** The school at the learner's [level]: its units [done] of all of them ([of]), the ones begun, the one suggested next. */
data class SchoolStanding(
    val level: String,
    val done: Int,
    val of: Int,
    val started: List<UnitRef>,
    val next: UnitRef?,
    /** The class has begun (the hamlet): before, nothing to open yet. */
    val open: Boolean,
)

/** A grammar page met and how well it sits ([stars] 0 to 5, null: not practised yet). */
data class PageStanding(val page: GrammarPage, val stars: Int?)

/** The book's pages of the learner's [level]: [pages] of them, [met] opened, [secure] secure or mastered; the [weakest]. */
data class BookStanding(val level: String, val pages: Int, val met: Int, val secure: Int, val weakest: List<PageStanding>)

/** The counting cards ([Counting]): [cards] of them, [secure] secure, [due] due today. */
data class CountingStanding(val cards: Int, val secure: Int, val due: Int)

/** "📝 Slovnica · Grammar" on the progress screen: the school, the grammar book, the counting cards; each null when there's none. */
data class GrammarStanding(val school: SchoolStanding?, val book: BookStanding?, val counting: CountingStanding?) {
    val empty: Boolean get() = school == null && book == null && counting == null
}

/** What the progress screen's way to the next level and its grammar section show. Pure (the texts in the learner's pair). */
object ProgressLogic {
    /** The weakest pages shown, at most. */
    const val WEAKEST = 3

    /** The pages to secure shown as chips, at most. */
    const val TO_SECURE = 8

    /** A treasure found is told this many days after (longer while the node doesn't have its level yet). */
    const val FOUND_DAYS = 7

    /**
     * The way from [Readiness.from] to the next level ([r]; null at the top, C2): the words ([known] against the level's
     * goal, [Stats.vocabGoal]: a milestone, not a sign), then the grammar, the reviews and the practice as the treasure map
     * weighs them ([LevelReadiness]). The map: the hunt [status] (of [hunt], the stations [required] on this phone), else
     * waiting once the signs are met or the storyteller [teller] offered it, else coming. A treasure found is told for
     * [FOUND_DAYS] after [today], and while [waiting]: the node doesn't have its level yet.
     */
    fun path(
        r: Readiness, known: Int, status: HuntStatus, hunt: TreasureHunt?, required: List<Station>, teller: String, waiting: Boolean,
        today: LocalDate = LocalDate.now(),
    ): LevelPath? {
        val to = r.to ?: return null
        val goal = Stats.vocabGoal(r.from)
        val pct = (r.retention * 100).roundToInt()
        val share = (LevelReadiness.SECURE_SHARE * 100).roundToInt()
        val retention = (LevelReadiness.RETENTION * 100).roundToInt()
        val lines = listOf(
            line(PathSign.WORDS, known >= goal, "progressScreen.pathWords", emptyArray(), "$known / $goal", null),
            line(
                PathSign.GRAMMAR, r.grammar, "progressScreen.pathGrammar", arrayOf("from" to r.from), "${r.secure.size} / ${r.needSecure}",
                "progressScreen.pathGrammarOf" to arrayOf("need" to r.needSecure, "n" to r.pages.size, "pct" to share),
            ),
            line(
                PathSign.REVIEWS, r.remembered, "progressScreen.pathReviews", arrayOf("days" to LevelReadiness.WINDOW_DAYS),
                if (r.reviews == 0) "—" else "$pct %",
                "progressScreen.pathReviewsOf" to arrayOf("n" to r.reviews, "min" to LevelReadiness.MIN_REVIEWS, "pct" to retention),
            ),
            line(
                PathSign.PRACTICE, r.seasoned, "progressScreen.pathPractice", arrayOf("from" to r.from), "${r.practised} / ${LevelReadiness.MIN_PRACTICE_DAYS}",
                "progressScreen.pathAtLevel" to arrayOf("from" to r.from, "days" to r.atLevel, "min" to LevelReadiness.MIN_DAYS_AT_LEVEL),
            ),
        )
        val map = when {
            status == HuntStatus.ON && hunt != null -> MapState.On(required.count { hunt.passed(it) }, required.size)
            status == HuntStatus.OFFERED || r.ready -> MapState.Waiting(teller)
            else -> MapState.Coming(teller)
        }
        val found = hunt?.takeIf { status == HuntStatus.FOUND && (waiting || recent(it.found, today)) }?.let { TreasureFound(it.to, waiting) }
        val still = if (r.grammar) emptyList() else r.toSecure
        return LevelPath(r.from, to, lines, still.take(TO_SECURE), map, found, (still.size - TO_SECURE).coerceAtLeast(0))
    }

    /** A page's chip: its title in the language learned up to its colon ("Dvojina: midva sva, dva brata" → "Dvojina"). */
    fun chipTitle(p: GrammarPage): String = p.target.substringBefore(": ").trim().ifEmpty { p.target }

    /** Day [day] (ISO) is [today] or one of the [FOUND_DAYS] before it. */
    private fun recent(day: String, today: LocalDate): Boolean =
        runCatching { LocalDate.parse(day) }.getOrNull()?.let { !it.isAfter(today) && !it.plusDays(FOUND_DAYS.toLong()).isBefore(today) } == true

    private fun line(
        sign: PathSign, met: Boolean, title: String, titleArgs: Array<Pair<String, Any?>>, value: String,
        caption: Pair<String, Array<Pair<String, Any?>>>?,
    ): PathLine {
        val said = listOfNotNull(
            "${inBase(title, *titleArgs)}: $value",
            caption?.let { (k, a) -> inBase(k, *a) },
            inBase(if (met) "progressScreen.signMet" else "progressScreen.signNotYet"),
        )
        return PathLine(
            sign, met, bi(title, *titleArgs), value, caption?.let { (k, a) -> bi(k, *a) },
            said.joinToString(". "),
        )
    }

    /**
     * The grammar at the learner's [level] in [language]: the school's units of the level ([course], the progress in [s]:
     * done, begun, and the one [School.next] suggests), the book's [pages] of the level (met in [s], secure or mastered by
     * [mastery], the weakest by their stars, [meter]), and the [counting] cards (null while counting is off) on [today].
     */
    fun grammar(
        level: String,
        language: String,
        course: Course?,
        s: GameState?,
        pages: List<GrammarPage>,
        mastery: (String) -> Mastery?,
        meter: (String) -> Int?,
        counting: List<ReviewCard>?,
        today: LocalDate,
    ): GrammarStanding = GrammarStanding(
        school = s?.let { school(level, course, it) },
        book = s?.let { book(level, language, pages, it, mastery, meter) },
        counting = counting?.let { cards ->
            CountingStanding(cards.size, cards.count(Counting::secure), cards.count { c -> c.due?.let { !it.isAfter(today) } == true })
        },
    )

    /** The school's units of [level]; null without a course, or with no units of that level in it (a level not written yet). */
    fun school(level: String, course: Course?, s: GameState): SchoolStanding? {
        course ?: return null
        val lv = LevelReadiness.norm(level)
        val refs = course.refs(lv).takeIf { it.isNotEmpty() } ?: return null
        val progress = refs.associate { it.id to School.progress(s, course.language, it.id) }
        return SchoolStanding(
            level = lv,
            done = refs.count { progress[it.id]?.done != null },
            of = refs.size,
            started = course.playable(lv).filter { progress[it.id] != null && progress[it.id]?.done == null },
            next = School.next(s, course),
            open = School.open(s),
        )
    }

    /** The book's pages of [level]; null when the book has none of that level. */
    fun book(level: String, language: String, pages: List<GrammarPage>, s: GameState, mastery: (String) -> Mastery?, meter: (String) -> Int?): BookStanding? {
        val rank = Introduction.rank(level) ?: return null
        val own = pages.filter { Introduction.rank(it.level) == rank }.distinctBy { it.id }.takeIf { it.isNotEmpty() } ?: return null
        val met = GrammarBook.met(s, language)
        val opened = own.filter { it.id in met }
        val secure = opened.filter { mastery(it.id).let { m -> m == Mastery.SECURE || m == Mastery.MASTERED } }.map { it.id }.toSet()
        // the pages met and not secure yet: the least stars first, then those not practised yet; the book's order between them
        val weakest = opened.filter { it.id !in secure }.map { PageStanding(it, meter(it.id)) }
            .sortedWith(compareBy<PageStanding> { it.stars == null }.thenBy { it.stars ?: 0 })
            .take(WEAKEST)
        return BookStanding(LevelReadiness.norm(level), own.size, opened.size, secure.size, weakest)
    }
}
