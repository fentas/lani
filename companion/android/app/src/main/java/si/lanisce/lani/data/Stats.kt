package si.lanisce.lani.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Practice on one day, summed over its sessions. */
data class DayActivity(val date: LocalDate, val minutes: Int, val exercises: Int, val sessions: Int)

/** [current] counts back from today, or from yesterday while today is still open. */
data class Streaks(val current: Int, val best: Int, val practisedToday: Boolean)

/** A Monday-to-Sunday week. */
data class WeekBucket(val start: LocalDate, val exercises: Int, val minutes: Int, val sessions: Int)

data class AccuracyPoint(val date: LocalDate, val accuracy: Float, val exercises: Int)

/** [level] 0–5 stars, [confidence] 0–1. */
data class SkillMastery(val skill: String, val level: Int, val confidence: Float, val lastPractised: LocalDate?)

/** Direction of an error pattern: [WORSE] = it keeps happening, [BETTER] = recent answers were right. */
enum class Trend { WORSE, STEADY, BETTER }

data class MistakeStat(
    val id: String,
    val label: String,
    val category: String,
    val severity: String,
    val frequency: Int,
    val lastSeen: LocalDate?,
    val trend: Trend,
    val wrong: String?,
    val right: String?,
)

/** When the next level's vocabulary milestone is reached at the recent pace. */
sealed interface Projection {
    /** Too little history to extrapolate honestly. */
    data object NotEnoughData : Projection
    data object Reached : Projection
    data class Eta(val date: LocalDate, val perWeek: Double) : Projection
    /** More than [Stats.MAX_PROJECTION_DAYS] away at this pace. */
    data class TooFar(val perWeek: Double) : Projection
}

data class DueDay(val date: LocalDate, val count: Int)

/** Everything the progress screen shows, derived from GET /state. */
data class ProgressStats(
    val today: LocalDate,
    val name: String,
    val level: String,
    val targetLevel: String,
    val nextLevel: String,
    val nextGoal: Int,
    /** Words learned: vocabulary answered right at least once ([Stats.learned]). */
    val wordsKnown: Int,
    /** Vocabulary cards under review, learned or not. */
    val wordsTracked: Int = wordsKnown,
    /** Cumulative words learned at the end of each of the last weeks (by the day they were added), oldest first. */
    val wordsTrend: List<Int>,
    val wordsPerWeek: Double,
    val projection: Projection,
    /** Weeks (oldest first) of Monday..Sunday; null = in the future. */
    val calendar: List<List<DayActivity?>>,
    val streaks: Streaks,
    val totalMinutes: Int,
    val accuracy: List<AccuracyPoint>,
    val weeks: List<WeekBucket>,
    val skills: List<SkillMastery>,
    val mistakes: List<MistakeStat>,
    /** Today (overdue included) and the next six days. */
    val due: List<DueDay>,
    val overdue: Int,
    /**
     * Every language the learner has ([LanguageSummary], the home one first). Everything above is the home language's
     * alone (its databases are GET /state's): the other languages' words, reviews and mistakes are theirs.
     */
    val languages: List<LanguageSummary> = emptyList(),
) {
    /** The languages practised elsewhere (visits), for their own small section. */
    val otherLanguages: List<LanguageSummary> get() = languages.filter { !it.home && it.code.isNotEmpty() }
}

/** Pure computations behind the progress screen. */
object Stats {
    val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")
    /** Rough vocabulary milestone to reach the level after each CEFR level (A1 → A2 ≈ 300 words). */
    private val GOALS = listOf(300, 1000, 2000, 3500, 5000, 8000)
    val SKILL_ORDER = listOf("writing", "speaking", "vocabulary", "reading", "listening")

    const val MIN_HISTORY_DAYS = 14
    const val MIN_RECENT_WORDS = 5
    const val RATE_WINDOW_DAYS = 28
    const val MAX_PROJECTION_DAYS = 3 * 365

    fun nextLevel(level: String): String = LEVELS.getOrElse(LEVELS.indexOf(level).coerceAtLeast(0) + 1) { "C2" }
    fun vocabGoal(level: String): Int = GOALS[LEVELS.indexOf(level).coerceAtLeast(0)]

    /**
     * Words to reach [level]: A2 300, B1 1000, B2 2000, C1 3500, C2 5000 (the goal of the level before it). A1 counts
     * as A2 (there is nothing to reach below it); an unknown level as B2, the default target.
     */
    fun wordsToReach(level: String): Int {
        val i = LEVELS.indexOf(level.trim().uppercase()).takeIf { it >= 0 } ?: LEVELS.indexOf("B2")
        return GOALS[(i - 1).coerceAtLeast(0)]
    }

    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Heatmap step 0–4 for a day: by minutes, at least 1 when anything was practised. */
    fun intensity(d: DayActivity?): Int = when {
        d == null || (d.minutes <= 0 && d.exercises <= 0) -> 0
        d.minutes >= 40 -> 4
        d.minutes >= 20 -> 3
        d.minutes >= 10 -> 2
        else -> 1
    }

    /** Sessions summed per day. */
    fun daily(sessions: List<DayActivity>): Map<LocalDate, DayActivity> =
        sessions.groupBy { it.date }.mapValues { (d, s) -> DayActivity(d, s.sumOf { it.minutes }, s.sumOf { it.exercises }, s.sumOf { it.sessions }) }

    fun streaks(days: Set<LocalDate>, today: LocalDate): Streaks {
        val practisedToday = today in days
        var current = 0
        var d = if (practisedToday) today else today.minusDays(1)
        while (d in days) { current++; d = d.minusDays(1) }
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (day in days.filter { !it.isAfter(today) }.sorted()) {
            run = if (prev != null && ChronoUnit.DAYS.between(prev, day) == 1L) run + 1 else 1
            best = maxOf(best, run)
            prev = day
        }
        return Streaks(current, maxOf(best, current), practisedToday)
    }

    /** The last [weeks] weeks as columns of Monday..Sunday, ending with the current week. */
    fun calendar(days: Map<LocalDate, DayActivity>, today: LocalDate, weeks: Int = 12): List<List<DayActivity?>> {
        val first = weekStart(today).minusWeeks((weeks - 1).toLong())
        return (0 until weeks).map { w ->
            (0 until 7).map { i ->
                val d = first.plusDays(w * 7L + i)
                if (d.isAfter(today)) null else days[d] ?: DayActivity(d, 0, 0, 0)
            }
        }
    }

    /** The last [weeks] Monday-start weeks, empty ones included, oldest first. */
    fun weekly(days: Map<LocalDate, DayActivity>, today: LocalDate, weeks: Int = 8): List<WeekBucket> {
        val first = weekStart(today).minusWeeks((weeks - 1).toLong())
        return (0 until weeks).map { w ->
            val start = first.plusWeeks(w.toLong())
            val inWeek = days.values.filter { !it.date.isBefore(start) && it.date.isBefore(start.plusWeeks(1)) }
            WeekBucket(start, inWeek.sumOf { it.exercises }, inWeek.sumOf { it.minutes }, inWeek.sumOf { it.sessions })
        }
    }

    /** Words known at the end of each of the last [weeks] weeks (the current one ends today). */
    fun wordsTrend(created: List<LocalDate>, today: LocalDate, weeks: Int = 8): List<Int> {
        val first = weekStart(today).minusWeeks((weeks - 1).toLong())
        return (0 until weeks).map { w ->
            val end = minOf(first.plusWeeks(w + 1L).minusDays(1), today)
            created.count { !it.isAfter(end) }
        }
    }

    /** Words added per week over the last [RATE_WINDOW_DAYS] days. */
    fun wordsPerWeek(created: List<LocalDate>, today: LocalDate): Double {
        val from = today.minusDays(RATE_WINDOW_DAYS - 1L)
        val recent = created.count { !it.isBefore(from) && !it.isAfter(today) }
        return recent * 7.0 / RATE_WINDOW_DAYS
    }

    /**
     * Honest projection: needs [MIN_HISTORY_DAYS] of history and [MIN_RECENT_WORDS] words in the
     * rate window, otherwise [Projection.NotEnoughData].
     */
    fun projection(known: Int, goal: Int, created: List<LocalDate>, today: LocalDate): Projection {
        if (known >= goal) return Projection.Reached
        val firstDay = created.minOrNull() ?: return Projection.NotEnoughData
        if (ChronoUnit.DAYS.between(firstDay, today) < MIN_HISTORY_DAYS) return Projection.NotEnoughData
        val from = today.minusDays(RATE_WINDOW_DAYS - 1L)
        if (created.count { !it.isBefore(from) && !it.isAfter(today) } < MIN_RECENT_WORDS) return Projection.NotEnoughData
        val perWeek = wordsPerWeek(created, today)
        val days = ceil((goal - known) / perWeek * 7).toLong()
        return if (days > MAX_PROJECTION_DAYS) Projection.TooFar(perWeek) else Projection.Eta(today.plusDays(days), perWeek)
    }

    /** Cards due today (overdue folded in) and on each of the next days. */
    fun dueForecast(dueDates: List<LocalDate>, today: LocalDate, days: Int = 7): List<DueDay> =
        (0 until days).map { i ->
            val d = today.plusDays(i.toLong())
            DueDay(d, if (i == 0) dueDates.count { !it.isAfter(today) } else dueDates.count { it == d })
        }

    /**
     * [WORSE] while it keeps recurring (wrong twice in a row, or more examples in the last two weeks
     * than the two before), [BETTER] once answered right since.
     */
    fun trend(consecutiveCorrect: Int, consecutiveIncorrect: Int, exampleDates: List<LocalDate>, today: LocalDate): Trend {
        if (consecutiveCorrect >= 1) return Trend.BETTER
        val recent = exampleDates.count { ChronoUnit.DAYS.between(it, today) in 0..13 }
        val before = exampleDates.count { ChronoUnit.DAYS.between(it, today) in 14..27 }
        return if (consecutiveIncorrect >= 2 || recent > before) Trend.WORSE else Trend.STEADY
    }

    /** Accuracy per date, weighted by exercises when a date appears twice. */
    fun accuracy(points: List<AccuracyPoint>): List<AccuracyPoint> =
        points.groupBy { it.date }.map { (d, p) ->
            val n = p.sumOf { it.exercises }
            val acc = if (n > 0) p.sumOf { it.accuracy.toDouble() * it.exercises } / n else p.map { it.accuracy.toDouble() }.average()
            AccuracyPoint(d, acc.toFloat(), n)
        }.sortedBy { it.date }

    fun humanize(id: String): String = id.replace('_', ' ').replace('-', ' ').trim().replaceFirstChar { it.uppercase() }

    // --- parsing GET /state --------------------------------------------------------------

    private fun JsonElement?.obj() = this as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonElement?.arr() = this as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.roundToInt() } ?: 0
    private fun JsonObject.dbl(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull ?: 0.0
    private fun JsonObject.date(k: String) = str(k)?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    /**
     * A spaced-repetition item is learned once it was answered right and not failed since: SM-2 repetitions or its
     * mastery level at 1 or more, or, before its first review, answered right while it was learned (a word pack's
     * session stores that answer as `last_quality`). A failed review makes it [rusty], so the word drops out until
     * it's right again, whatever its mastery. A card added without an answer (looked up in a dialog, source
     * "lookup") is tracked, not learned.
     */
    fun learned(item: JsonObject): Boolean = !rusty(item) && (
        item.int("repetitions") >= 1 || item.int("mastery_level") >= 1 ||
            (item.int("total_reviews") == 0 && item.int("last_quality") >= 3 && item.str("source") != "lookup")
        )

    /**
     * A rusty word: its last review failed. update-db.py's SM-2 then resets `repetitions` to 0 and stores the low
     * `last_quality` (below 3), after at least one review (`total_reviews`); a word only learned in a pack, never
     * reviewed, can't be rusty. It stays rusty until a review gets it right (repetitions 1 again), the next day's or
     * a short review of the rusty words right away, and until then the village's next age waits (GAME.md, "Rusty
     * words"). A card added without an answer (source "lookup") never rusts: it wasn't learned.
     */
    fun rusty(item: JsonObject): Boolean =
        item.int("total_reviews") >= 1 && item.int("repetitions") == 0 && "last_quality" in item &&
            item.int("last_quality") < 3 && item.str("source") != "lookup"

    fun parse(raw: String, fallbackToday: LocalDate = LocalDate.now()): ProgressStats {
        val root = json.parseToJsonElement(raw).jsonObject
        val db = root["databases"].obj()
        val today = root["computed"].obj().date("today") ?: fallbackToday
        val profile = db["learner_profile"].obj()
        val learner = profile["learner"].obj()
        val level = learner.str("current_level") ?: "A1"
        val items = db["spaced_repetition"].obj()["items"].obj().values.map { it.obj() }

        // Words learned (answered right at least once), by the day they were added: the count, its trend and pace.
        val vocab = items.filter { it.str("type") == "vocabulary" }
        val vocabCreated = vocab.filter(::learned).map { it.date("created_date") ?: today }
        val known = vocabCreated.size
        val goal = vocabGoal(level)

        val sessions = db["session_log"].obj()["sessions"].arr().mapNotNull { e ->
            val s = e.obj()
            val d = s.date("date") ?: return@mapNotNull null
            DayActivity(d, s.int("duration_minutes"), s.int("exercises_completed"), 1)
        }
        val days = daily(sessions)

        val progress = db["progress_db"].obj()
        val acc = accuracy(progress["accuracy_trend"].arr().mapNotNull { e ->
            val p = e.obj()
            AccuracyPoint(p.date("date") ?: return@mapNotNull null, p.dbl("accuracy").toFloat().coerceIn(0f, 1f), p.int("exercises"))
        })

        val skillsObj = db["mastery_db"].obj()["skills"].obj()
        val skillNames = SKILL_ORDER + skillsObj.keys.filter { it !in SKILL_ORDER }
        val skills = skillNames.map { k ->
            val s = skillsObj[k].obj()
            SkillMastery(k, s.int("mastery_level").coerceIn(0, 5), s.dbl("confidence_score").toFloat().coerceIn(0f, 1f), s.date("last_practiced"))
        }

        val mistakes = db["mistakes_db"].obj()["error_patterns"].obj().map { (id, e) ->
            val p = e.obj()
            val examples = p["examples"].arr().map { it.obj() }
            val last = examples.lastOrNull()
            MistakeStat(
                id = id,
                label = p.str("description") ?: humanize(id),
                category = p.str("category") ?: "",
                severity = p.str("severity") ?: "",
                frequency = p.int("frequency"),
                lastSeen = p.date("last_seen") ?: p.date("last_occurred"),
                trend = trend(p.int("consecutive_correct"), p.int("consecutive_incorrect"), examples.mapNotNull { it.date("date") }, today),
                wrong = last?.str("incorrect"),
                right = last?.str("correct"),
            )
        }.sortedWith(compareByDescending<MistakeStat> { it.frequency }.thenByDescending { it.lastSeen }).take(5)

        // Same cards the app reviews: error patterns are drilled through modules instead.
        val dueDates = items.filter { it.str("type") != "error_pattern" }.mapNotNull { it.date("due_date") ?: it.date("next_review") }

        return ProgressStats(
            today = today,
            name = learner.str("name") ?: "",
            level = level,
            targetLevel = learner.str("target_level") ?: "B2",
            nextLevel = nextLevel(level),
            nextGoal = goal,
            wordsKnown = known,
            wordsTracked = vocab.size,
            wordsTrend = wordsTrend(vocabCreated, today),
            wordsPerWeek = wordsPerWeek(vocabCreated, today),
            projection = projection(known, goal, vocabCreated, today),
            calendar = calendar(days, today),
            streaks = streaks(days.filterValues { it.sessions > 0 }.keys, today),
            totalMinutes = sessions.sumOf { it.minutes },
            accuracy = acc,
            weeks = weekly(days, today),
            skills = skills,
            mistakes = mistakes,
            due = dueForecast(dueDates, today),
            overdue = dueDates.count { it.isBefore(today) },
            languages = LanguageSummary.parseAll(root),
        )
    }
}
