package si.lanisce.lani.game

import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Pack
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.parsePack
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.l10n.Dates
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The festivals' word packs (companion/packs/praznik-*.json, lani.pack/v0), bundled with the app as Java resources
 * under packs/ (app/build.gradle.kts, "festivalPacks"): the bridge serves the same files as curated packs, so a
 * festival's words and its pack always agree. Loaded once, on first use; a pack that's missing or can't be read has
 * no words (its festival then has nothing to practise, and a test says so).
 */
object FestivalPacks {
    private val cache = HashMap<String, Pack?>()

    fun pack(id: String): Pack? = synchronized(cache) { cache.getOrPut(id) { load(id) } }

    /** Read them again (the learner the content speaks to changed: [si.lanisce.lani.l10n.Learner.current]). */
    fun forget() = synchronized(cache) { cache.clear() }

    private fun load(id: String): Pack? = runCatching {
        FestivalPacks::class.java.getResourceAsStream("/packs/$id.json")?.use { parsePack(si.lanisce.lani.l10n.Learner.current.renderJson(it.readBytes().decodeToString())) }
    }.getOrNull()
}

/** When a festival falls, each year. */
sealed interface DateRule {
    fun date(year: Int): LocalDate

    /** The same day every year. */
    data class Fixed(val month: Int, val day: Int) : DateRule {
        override fun date(year: Int): LocalDate = LocalDate.of(year, month, day)
    }

    /** [days] from Easter Sunday (pust, Shrove Tuesday, is 47 days before). */
    data class Easter(val days: Int) : DateRule {
        override fun date(year: Int): LocalDate = Calendar.easter(year).plusDays(days.toLong())
    }

    /** The last [weekday] of [month] (the grape harvest: the last Saturday of September). */
    data class Last(val month: Int, val weekday: DayOfWeek) : DateRule {
        override fun date(year: Int): LocalDate = LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(weekday))
    }
}

/**
 * A day of the year the village keeps ([Calendar]), from its culture (game/culture, festivals.json): its [rule], who
 * leads it ([leaders], villager ids, the first who is in the village), what the leader says ([ask]), a line about the
 * day ([about]), the chronicle's line once celebrated ([line]), and its [good] (×[goods]) for the chest. Its words are
 * its word [pack]'s. Texts read "target · base", A1–A2: the learner reads them.
 */
data class Festival(
    val id: String,
    val emoji: String,
    val nameText: Text,
    val rule: DateRule,
    val leaders: List<String>,
    val askText: Text,
    val aboutText: Text,
    val lineText: Text,
    val good: String? = null,
    val goods: Int = 1,
    /** Its word pack's id ("praznik-novo-leto"); the pack names it back (`festival`). */
    val pack: String = "praznik-" + id.replace('_', '-'),
) {
    /** "Martinovo · St Martin's Day". */
    val name: String get() = nameText.bi()
    /** "Martinovo": the name in the target language. */
    val sl: String get() = nameText.target
    /** "St Martin's Day": the name in the base language. */
    val en: String get() = nameText.base
    val ask: String get() = askText.bi()
    val about: String get() = aboutText.bi()
    val line: String get() = lineText.bi()

    /** Its words (A1–A2), from its pack; empty if the app was built without it. */
    val words: List<PackWord> get() = FestivalPacks.pack(pack)?.takeIf { it.festival == id }?.words.orEmpty()
}

/** A festival that is on today: its [day] this year, [late] days after it (0 on the day), [done] once celebrated. */
data class FestivalDay(val festival: Festival, val day: LocalDate, val late: Int, val done: Boolean)

/**
 * "Koledar · The calendar": the real days of the village's culture (Slovene ones in Primorska), by the phone's date.
 * The rules and rewards are here; the festivals, their dates and words are the culture's. A few days ahead the village
 * counts down; on the day (and [Catalog] grace days after, for a learner who missed it) the festival's leader asks Jan
 * to celebrate: a short run with the festival's words (its word pack's: the answers go to spaced repetition like a
 * pack session's), and the village gets its treat and good spirits. Pure: every date is a function of the year.
 */
object Calendar {
    /** Days a festival can still be celebrated after its day, and days it's announced before. */
    const val GRACE_DAYS = 2L
    const val COUNTDOWN_DAYS = 5L
    /** The festival's run: this many of its words, half right to celebrate. */
    const val EXERCISES = 6
    const val PASS_RATIO = 0.5f
    /** Morale for a festival celebrated on its day, a day or two late, and for a try that didn't pass. */
    const val MORALE = 10
    const val LATE_MORALE = 5
    const val TRY_MORALE = 3
    /** ♥ with the leader, and with everyone who lives here (the whole village celebrates). */
    const val LEADER_POINTS = 5
    const val ALL_POINTS = 1

    /**
     * What a festival pays, like a request: 20 🌾 (its words are vocabulary) and 10 of what the village is shortest of
     * besides, at the campfire, growing with the age.
     */
    fun reward(s: GameState): Map<Res, Int> {
        val m = Quests.ageMultiplier(s.age)
        val caps = GameEngine.attributes(s).caps
        val side = Res.entries.filter { it != Res.FOOD }.minBy { s.res(it).toFloat() / caps.getValue(it).coerceAtLeast(1) }
        return mapOf(Res.FOOD to (20 * m).roundToInt(), side to (10 * m).roundToInt())
    }

    /** Easter Sunday (the Gregorian computus, Meeus/Jones/Butcher). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    fun byId(id: String): Festival? = festivals.firstOrNull { it.id == id }

    /** [f]'s day whose celebration window ([GRACE_DAYS] after it) holds [today], or null. */
    fun occurrence(f: Festival, today: LocalDate): LocalDate? = listOf(today.year, today.year - 1).map { f.rule.date(it) }
        .firstOrNull { d -> !today.isBefore(d) && !today.isAfter(d.plusDays(GRACE_DAYS)) }

    /** The festivals on [today] (their day or a grace day after), whether done or not. */
    fun on(s: GameState, today: LocalDate): List<FestivalDay> = festivals.mapNotNull { f ->
        occurrence(f, today)?.let { d -> FestivalDay(f, d, ChronoUnit.DAYS.between(d, today).toInt(), s.festivals[f.id] == d.toString()) }
    }

    /** The festivals Jan can celebrate [today]: on, not celebrated yet, from Tabor (the village has people). */
    fun open(s: GameState, today: LocalDate): List<FestivalDay> =
        if (s.age < Age.TABOR) emptyList() else on(s, today).filter { !it.done }

    /** The next festival day after [today] within [within] days, the nearest first. */
    fun upcoming(today: LocalDate, within: Long = COUNTDOWN_DAYS): Pair<Festival, LocalDate>? =
        festivals.flatMap { f -> listOf(today.year, today.year + 1).map { f to f.rule.date(it) } }
            .filter { (_, d) -> d.isAfter(today) && !d.isAfter(today.plusDays(within)) }
            .minByOrNull { it.second }

    /** "Jutri: Martinovo 🍷 · Tomorrow: St Martin's Day", "Čez 3 dni: … · … in 3 days". */
    fun countdown(f: Festival, days: Long): String = when {
        days <= 0L -> bi("calendar.today", "name" to f.nameText, "emoji" to f.emoji)
        days == 1L -> bi("calendar.tomorrow", "name" to f.nameText, "emoji" to f.emoji)
        else -> bi("calendar.inDays", "name" to f.nameText, "emoji" to f.emoji, "days" to days)
    }

    /** [f]'s next day from [today] on: this year's, or next year's once this year's is past. */
    fun next(f: Festival, today: LocalDate): LocalDate = f.rule.date(today.year).takeIf { !it.isBefore(today) } ?: f.rule.date(today.year + 1)

    /**
     * The festival whose words to learn now (a word pack to suggest): the one that's on and not celebrated yet, else the
     * next one within the countdown. None before Tabor, like the festivals themselves; without a village, the next one.
     */
    fun soon(s: GameState?, today: LocalDate): Festival? {
        if (s != null && s.age < Age.TABOR) return null
        return s?.let { open(it, today).firstOrNull()?.festival } ?: upcoming(today)?.first
    }

    /**
     * When [f] is, for its word pack's card: "Danes · Today" on the day, "Še traja · Not over yet" on a grace day, a
     * countdown in its last days, else its date ("11. novembra · 11 November").
     */
    fun whenText(f: Festival, today: LocalDate): String {
        occurrence(f, today)?.let { d -> return if (d == today) bi("common.today") else bi("calendar.whenStillOn") }
        val d = next(f, today)
        val days = ChronoUnit.DAYS.between(today, d)
        return when {
            days == 1L -> bi("calendar.whenTomorrow")
            days <= COUNTDOWN_DAYS -> bi("calendar.whenInDays", "days" to days)
            else -> dateText(d)
        }
    }

    /** "11. novembra · 11 November": a day of the year in the learner's two languages (see [Dates]). */
    fun dateText(d: LocalDate): String = "${Dates.dayMonth(d, L10n.pair.target)} · ${Dates.dayMonth(d, L10n.pair.base)}"

    /** "Danes: Martinovo 🍷 · Today: St Martin's Day", or a day or two late: "Martinovo še traja 🍷 · St Martin's Day isn't over yet". */
    fun todayText(d: FestivalDay): String =
        if (d.late == 0) countdown(d.festival, 0) else bi("calendar.notOver", "name" to d.festival.nameText, "emoji" to d.festival.emoji)

    /** Who leads [f] today: the first of its leaders in the village; null when none is (the whole village then). */
    fun leader(s: GameState, f: Festival, today: LocalDate): String? {
        val present = Residents.present(s, today) ?: return f.leaders.first()
        return f.leaders.firstOrNull { it in present }
    }

    /**
     * The festival's run: [EXERCISES] of its pack's words, half from the target language, half into it, as choices (the others
     * of the pack are the wrong options), each with the word it asks about, whose answer is recorded like a word pack's
     * (see [GameEngine.festivalChallenge]). The same run all day.
     */
    fun run(f: Festival, seed: Long, day: LocalDate): List<Pair<PackWord, Exercise>> {
        val r = rng(seed, "festival", f.id, day.toString())
        val all = f.words
        val words = all.shuffled(r).take(EXERCISES)
        val lines = Cultures.current.festivalsFile.lines
        val tag = "${f.emoji} ${lines.tag.bi()}"
        return words.mapIndexed { i, w ->
            val others = all.filter { it != w }.shuffled(r).take(3)
            // the example sentence after the answer, when the pack has one
            val example = w.example?.let { ex -> "\n$ex" + (w.exampleMeaning?.let { " · $it" } ?: "") }.orEmpty()
            w to if (i % 2 == 0) {
                val opts = (others.map { it.meaning } + w.meaning).shuffled(r)
                Exercise.Choice(lines.means.bi("word" to w.word), opts, opts.indexOf(w.meaning), explain = "${w.word} · ${w.meaning}$example", instruction = tag)
            } else {
                val opts = (others.map { it.word } + w.word).shuffled(r)
                Exercise.Choice(lines.say.bi("meaning" to w.meaning), opts, opts.indexOf(w.word), explain = "${w.word} · ${w.meaning}$example", instruction = tag)
            }
        }
    }

    /** The festival's run's exercises (see [run]). */
    fun exercises(f: Festival, seed: Long, day: LocalDate): List<Exercise> = run(f, seed, day).map { it.second }

    fun passMark(total: Int): Int = ceil(total * PASS_RATIO).toInt().coerceAtLeast(1)

    /**
     * [id]'s run was played [today]: [correct] of [total]. Enough right celebrates it: morale (less a day or two late),
     * its good for the chest, what it pays, the leader and everyone who lives here closer, the chronicle's line; once
     * each year. A try that didn't pass lifts the spirits a little and pays a fifth; it can be tried again.
     */
    fun celebrate(s: GameState, id: String, correct: Int, total: Int, today: LocalDate, now: Long): Pair<GameState, ChallengeResult> {
        val d = open(s, today).firstOrNull { it.festival.id == id } ?: return s to ChallengeResult(false, emptyMap(), emptyMap(), emptyList(), "")
        val f = d.festival
        val pay = reward(s)
        if (total <= 0 || correct < passMark(total)) {
            val (paid, got) = credit(s.copy(morale = (s.morale + TRY_MORALE).coerceAtMost(100)), pay.mapValues { (_, n) -> (n / 5).coerceAtLeast(1) })
            return paid to ChallengeResult(false, got, emptyMap(), emptyList(), "${f.emoji} ${Cultures.current.festivalsFile.lines.tried.bi()}")
        }
        val morale = if (d.late == 0) MORALE else LATE_MORALE
        var (st, got) = credit(s.copy(morale = (s.morale + morale).coerceAtMost(100)), pay)
        val good = f.good?.let { Catalog.goods[it] }
        if (good != null) st = Chest.add(st, good.id, f.goods)
        st = st.copy(festivals = st.festivals + (f.id to d.day.toString())).logged(now, f.emoji to f.line)
        leader(s, f, today)?.let { st = Bonds.add(st, it, LEADER_POINTS - ALL_POINTS, today, now = now) }
        for (r in st.residents) st = Bonds.add(st, r.id, ALL_POINTS, today, now = now)
        return st to ChallengeResult(true, got, emptyMap(), emptyList(), "${f.emoji} ${f.line}", thanks = good, thanksCount = f.goods)
    }

    /**
     * The year's festivals: the culture's (game/culture, festivals.json). Their words are in their packs
     * (companion/packs/praznik-*.json, see [Festival.pack]); a festival's good is a rare one (only festivals and the
     * pedlar have them), or a common one twice.
     */
    val festivals: List<Festival> get() = Cultures.current.festivals
}
