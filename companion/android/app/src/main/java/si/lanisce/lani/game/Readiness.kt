package si.lanisce.lani.game

import si.lanisce.lani.data.Drill
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.data.ReviewMark
import si.lanisce.lani.game.scene.SceneSpec
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * What the evidence says about the learner's level (companion/GAME.md, "The treasure map"): three signs that they have
 * the level [from] and are ready for [to] (null: [from] is the top, C2).
 *
 * - **The grammar**: of the level's grammar pages that the curated content tests ([pages]: an exercise, a dialog's turn,
 *   a challenge's question or a drill names them), the ones secure or mastered now ([secure]); [needSecure] of them.
 * - **The reviews**: the learner's reviews of the last [LevelReadiness.WINDOW_DAYS] days of the cards of their level
 *   (a card of a higher level is a stretch, not the level): [reviews] answered, [kept] of them right.
 * - **The time**: [practised] days of practice at the level (sessions in the log since it began), over [atLevel] days.
 */
data class Readiness(
    val from: String,
    val to: String?,
    val pages: List<String>,
    val secure: List<String>,
    val needSecure: Int,
    val reviews: Int,
    val kept: Int,
    val practised: Int,
    val atLevel: Int,
) {
    /** Enough of the level's rules are secure or mastered. */
    val grammar: Boolean get() = secure.size >= needSecure

    /** The share of the reviews answered right, 0 to 1 (0 without reviews). */
    val retention: Float get() = if (reviews == 0) 0f else kept.toFloat() / reviews

    /** The reviews go well: enough of them, and most answered right. */
    val remembered: Boolean get() = reviews >= LevelReadiness.MIN_REVIEWS && retention >= LevelReadiness.RETENTION

    /** Practised at the level a while: enough days of practice, over two weeks or more. */
    val seasoned: Boolean get() = practised >= LevelReadiness.MIN_PRACTICE_DAYS && atLevel >= LevelReadiness.MIN_DAYS_AT_LEVEL

    /** All three signs: the storyteller offers the map. */
    val ready: Boolean get() = to != null && grammar && remembered && seasoned

    /** The level's tested pages not secure yet, in the book's order: what to practise. */
    val toSecure: List<String> get() = pages - secure.toSet()
}

/**
 * Readiness for the next level from the evidence (companion/GAME.md, "The treasure map", "Readiness"). The learner's
 * level never rises by itself: once all three signs are there ([Readiness.ready]) the storyteller offers the treasure map,
 * whose hunt tests the end of the level; the learner may also start it earlier from the grammar book. Pure.
 */
object LevelReadiness {
    /** The share of the level's tested pages that are secure or mastered, at least. */
    const val SECURE_SHARE = 0.7

    /** The share of the level's reviews answered right, at least. */
    const val RETENTION = 0.8f

    /** The reviews looked at: the last two weeks (today and the 13 days before it). */
    const val WINDOW_DAYS = 14

    /** Reviews in the window, at least: fewer say too little. */
    const val MIN_REVIEWS = 30

    /** Days of practice at the level, at least (two weeks of practice, weekends off). */
    const val MIN_PRACTICE_DAYS = 10

    /** Calendar days since the level began, at least. */
    const val MIN_DAYS_AT_LEVEL = 14

    /** The level after [level] ("A1" → "A2"); null for C2 or a level not known. */
    fun next(level: String): String? = Introduction.rank(level)?.let { Introduction.LEVELS.getOrNull(it + 1) }

    /** [level] written as the levels are ("a2 " → "A2"); A1 for one not known. */
    fun norm(level: String?): String = Introduction.rank(level)?.let { Introduction.LEVELS[it] } ?: Introduction.LEVELS.first()

    /**
     * The pages the curated content names, so it tests them: a dialog's turn and its choices ([scenes], their rules as the
     * turns go by them: [si.lanisce.lani.game.scene.DialogChoice.rule]), an exercise's own page ([exercises]: the modules',
     * the challenges' questions), a drill's set or build ([drills]).
     */
    fun named(scenes: List<SceneSpec>, exercises: List<Exercise>, drills: List<Drill>): Set<String> = buildSet {
        for (s in scenes) for (d in s.dialogs) for (l in d.lines) {
            l.grammar?.let(::add)
            for (c in l.choices) c.rule(l)?.let(::add)
        }
        for (e in exercises) e.grammar?.let(::add)
        for (d in drills) {
            d.transforms.forEach { add(it.rule) }
            d.rapid.forEach { r -> r.rule?.let(::add) }
            d.builds.forEach { addAll(it.rules) }
        }
    }

    /**
     * The level's pages the curated content tests: those of [pages] at [level] that the content names ([named], see
     * [named]), in the book's order; all the level's pages when it names none of them (a language whose content doesn't
     * name its pages yet).
     */
    fun tested(level: String, pages: List<GrammarPage>, named: Set<String>): List<String> {
        val rank = Introduction.rank(level) ?: return emptyList()
        val own = pages.filter { Introduction.rank(it.level) == rank }.map { it.id }.distinct()
        return own.filter { it in named }.ifEmpty { own }
    }

    /** How many of [n] tested pages must be secure: [SECURE_SHARE] of them, rounded up. */
    fun needSecure(n: Int): Int = ceil(n * SECURE_SHARE - 1e-9).toInt()

    /**
     * Whether review [r] is of a card of the learner's level [level] or below: a card of a higher level (learned early)
     * is a stretch, not what the level asks; a card that doesn't say counts.
     */
    fun ofLevel(r: ReviewMark, level: String): Boolean {
        val card = Introduction.rank(r.level) ?: return true
        return card <= (Introduction.rank(level) ?: 0)
    }

    /**
     * What the evidence says for a learner at [level] on [today]: the book's [pages] and what the content [named], how far
     * they have each rule ([mastery]: [Mastery.SECURE] or [Mastery.MASTERED] count; null for a page the book lacks), their
     * [reviews] (the home language's), the days they practised ([practised]: the session log's) and the day the level
     * began ([since]; null: not known, as if today).
     */
    fun of(
        level: String,
        pages: List<GrammarPage>,
        named: Set<String>,
        mastery: (String) -> Mastery?,
        reviews: List<ReviewMark>,
        practised: Collection<LocalDate>,
        since: LocalDate?,
        today: LocalDate,
    ): Readiness {
        val from = norm(level)
        val tested = tested(from, pages, named)
        val secure = tested.filter { mastery(it).let { m -> m == Mastery.SECURE || m == Mastery.MASTERED } }
        val first = today.minusDays(WINDOW_DAYS - 1L)
        val window = reviews.filter { !it.day.isBefore(first) && !it.day.isAfter(today) && ofLevel(it, from) }
        val start = since?.takeIf { !it.isAfter(today) } ?: today
        val days = practised.filter { !it.isBefore(start) && !it.isAfter(today) }.toSet().size
        return Readiness(
            from = from,
            to = next(from),
            pages = tested,
            secure = secure,
            needSecure = needSecure(tested.size),
            reviews = window.size,
            kept = window.count { it.quality >= 3 },
            practised = days,
            atLevel = ChronoUnit.DAYS.between(start, today).toInt(),
        )
    }
}
