package si.lanisce.lani.ui.game

import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.AdvanceCheck
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.AgeStep
import si.lanisce.lani.game.BuildOption
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.ChallengeResult
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.Res
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.Localized
import si.lanisce.lani.l10n.bi

// Pure helpers behind the village screens: earning, formatting, and what to do next.

/**
 * What a finished run brought the village. [result] is set for events and quests.
 * [answered] < [total] when the learner left the run early.
 */
data class Reward(
    val earned: Map<Res, Int>,
    val result: ChallengeResult? = null,
    val correct: Int = 0,
    val total: Int = 0,
    val answered: Int = total,
    /** Almost-right answers: they earn resources but don't count as correct. */
    val almost: Int = 0,
    /** Answers past the day's full-pay ones ([si.lanisce.lani.game.Catalog.FRESH_ANSWERS]), paid half. */
    val tired: Int = 0,
    /** A quest giver's friendship grew (a villager's quest done). */
    val friend: si.lanisce.lani.ui.villagers.FriendGain? = null,
    /** 🤝 earned besides a quest's (a family answer); a quest's own are in [result]. */
    val help: Int = 0,
    /** Who [result]'s thank-you good is from (the quest's giver, the festival), for its reveal. */
    val from: GiftFrom? = null,
    /** How often the run's text (the day's letter) was brought back while a question was open, and the percent it took off. */
    val looks: Int = 0,
    val lookCost: Int = 0,
) {
    /** All the 🤝 this run brought. */
    val helpEarned: Int get() = help + (result?.takeIf { it.won }?.help ?: 0)
}

/** "+3 🤝 pomoč · help" for a result card; null when there was none. */
fun helpText(n: Int): String? = n.takeIf { it > 0 }?.let { "+$it 🤝 ${bi("villageLogic.help")}: ${bi("villageLogic.neighboursWillHelp")}" }

/** The finish screen's note when part of a run paid half; null when all of it paid in full. */
fun tiredText(r: Reward): String? = r.tired.takeIf { it > 0 }?.let { n ->
    "🌙 ${bi("villageLogic.halfPay", "n" to n)}: ${bi("villageLogic.villageRests", "fresh" to Catalog.FRESH_ANSWERS)}. " +
        "${bi("villageLogic.fullPayTomorrow")}."
}

/**
 * The result line of a run: "✅ 2 · 🟡 1 · 3/7 odgovorjenih · answered" when it was left early or had
 * almost-right answers, else "5 / 7 pravilno · correct".
 */
fun tallyText(r: Reward): String =
    if (r.answered >= r.total && r.almost == 0) bi("villageLogic.correct", "correct" to r.correct, "total" to r.total)
    else buildString {
        append("✅ ${r.correct}")
        if (r.almost > 0) append(" · 🟡 ${r.almost}")
        append(" · ${bi("villageLogic.answered", "answered" to r.answered, "total" to r.total)}")
    }

/**
 * One (resource, verdict) pair per answered exercise. [skills] (from a challenge) wins over
 * [resourceOf]; answers beyond the exercise list are ignored.
 */
fun earnPairs(
    exercises: List<Exercise>,
    verdicts: List<Verdict>,
    skills: List<Res>? = null,
    resourceOf: (Exercise) -> Res,
): List<Pair<Res, Verdict>> = verdicts.take(exercises.size).mapIndexed { i, v ->
    (skills?.getOrNull(i) ?: resourceOf(exercises[i])) to v
}

/** SM-2 quality for a card answered inside a challenge, where the review variant is unknown. */
fun challengeQuality(v: Verdict): Int = when (v) {
    Verdict.CORRECT -> 4
    Verdict.ALMOST -> 3
    Verdict.WRONG -> 1
}

/** Reviews to persist from a challenge: only exercises backed by a spaced-repetition card. */
fun challengeReviews(cardIds: List<String?>, verdicts: List<Verdict>): List<Pair<String, Int>> =
    verdicts.mapIndexedNotNull { i, v -> cardIds.getOrNull(i)?.let { it to challengeQuality(v) } }

/**
 * Answers that count towards an event's or a quest's pass mark: only fully correct ones. An almost
 * (a stem typo, a skipped tutor-graded answer) still earns resources, but does not win the fight.
 */
fun correctCount(verdicts: List<Verdict>): Int = verdicts.count { it == Verdict.CORRECT }

fun addRes(a: Map<Res, Int>, b: Map<Res, Int>): Map<Res, Int> =
    (a.keys + b.keys).associateWith { (a[it] ?: 0) + (b[it] ?: 0) }.filterValues { it != 0 }

/** "+18 🌾 +6 🪨" in resource order; empty when nothing was earned. */
fun formatRes(m: Map<Res, Int>, sign: String = "+"): String =
    Res.entries.mapNotNull { r -> m[r]?.takeIf { it > 0 }?.let { "$sign$it ${r.emoji}" } }.joinToString(" ")

/** Countdown label: "2 d", "5 h", "42 min", "<1 min". */
fun timeLeft(deadline: Long, now: Long): String {
    val min = (deadline - now) / 60_000
    return when {
        min >= 48 * 60 -> "${min / (24 * 60)} d"
        min >= 60 -> "${min / 60} h"
        min >= 1 -> "$min min"
        else -> "<1 min"
    }
}

/** "1:05" for a timer. */
fun clock(seconds: Int): String = "%d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)

fun Res.label() = "$sl · $en"

/** The skill in Slovene (the English one is [Res.skill]). */
fun Res.skillSl(): String = when (this) {
    Res.FOOD -> "besede"
    Res.WOOD -> "poslušanje"
    Res.STONE -> "slovnica"
    Res.WISDOM -> "pogovor in pisanje"
}

/** After "pri" (locative): "pri besedah", "pri slovnici". */
fun Res.skillSlAt(): String = when (this) {
    Res.FOOD -> "besedah"
    Res.WOOD -> "poslušanju"
    Res.STONE -> "slovnici"
    Res.WISDOM -> "pogovoru in pisanju"
}

/** The skill in [lang]: "slovnica", "grammar", "grammatica", "Grammatik". */
fun Res.skillIn(lang: Lang): String = when (lang) {
    Lang.SL -> skillSl()
    Lang.EN -> skill
    Lang.IT -> when (this) {
        Res.FOOD -> "vocabolario"
        Res.WOOD -> "ascolto"
        Res.STONE -> "grammatica"
        Res.WISDOM -> "conversazione e scrittura"
    }
    Lang.DE -> when (this) {
        Res.FOOD -> "Wortschatz"
        Res.WOOD -> "Hören"
        Res.STONE -> "Grammatik"
        Res.WISDOM -> "Gespräch und Schreiben"
    }
}

/** The skill as a message argument: it reads in each message's language. */
fun Res.skillName(): Localized = Localized { skillIn(it) }

/** After Slovene "pri", the locative ("pri slovnici"); in another language, the skill's name. */
fun Res.skillAt(): Localized = Localized { if (it == Lang.SL) skillSlAt() else skillIn(it) }

/** "slovnica · grammar": the skill in the learner's pair. */
fun Res.skillLabel() = "${skillIn(L10n.pair.target)} · ${skillIn(L10n.pair.base)}"

/** "še 1 korak · 1 step to go"; the plurals are ICU's (Slovene: 1 korak, 2 koraka, 3–4 koraki, 5+ korakov). */
fun stepsLeft(n: Int): String = bi("villageLogic.stepsLeft", "n" to n)
fun Age.label() = "$sl · $en"
fun BuildingType.label() = "$sl · $en"

/** The single most useful thing to do in the village right now. */
sealed interface NextGoal {
    data object FeedFire : NextGoal
    data class Advance(val to: Age) : NextGoal
    data class Build(val option: BuildOption) : NextGoal
    /** Work towards the next age: its checklist, and the resource to gather. */
    data class Grow(val to: Age, val missing: List<String>, val gather: Res) : NextGoal
    /** Every plot is built and the next age waits on learning milestones (words learned). */
    data class Learn(val to: Age, val missing: List<String>) : NextGoal
    /** The next age waits on friends (and maybe words): help the villagers. */
    data class Friends(val to: Age, val missing: List<String>) : NextGoal
    /** The next age waits on [rusty] words (and maybe words or friends): a minute's review of them polishes them. */
    data class Polish(val to: Age, val missing: List<String>, val rusty: Int) : NextGoal
    data class Gather(val res: Res) : NextGoal
}

/** What a step of the next age leads to, so every step that holds the age back can be worked on right now. */
enum class StepAction { GATHER, BUILD, LEARN, POLISH, HELP }

/** Null when the step is done, or a damaged building must be repaired first (its own sheet). */
fun stepAction(step: AgeStep): StepAction? = when {
    step.done -> null
    step.kind == AgeStep.Kind.RESOURCE && step.res != null -> StepAction.GATHER
    step.kind == AgeStep.Kind.BUILDING && !step.broken -> StepAction.BUILD
    step.kind == AgeStep.Kind.WORDS -> StepAction.LEARN
    // a short review of just the rusty words, now: a right answer polishes one at once
    step.kind == AgeStep.Kind.RUSTY -> StepAction.POLISH
    step.kind == AgeStep.Kind.FRIENDS -> StepAction.HELP
    else -> null
}

/**
 * Low fire first, then advancing, then polishing rusty words when only they (and words or friends) are missing, then
 * friends when only friends (and words) are missing, then (with every plot taken) learning towards the next age, then
 * the cheapest affordable new building, then gathering for the next age.
 */
fun nextGoal(state: GameState, options: List<BuildOption>, check: AdvanceCheck, caps: Map<Res, Int>): NextGoal {
    if (state.fire < 25) return NextGoal.FeedFire
    if (check.ok && check.next != null) return NextGoal.Advance(check.next)
    val plotsFull = state.plotsTaken >= PLOTS_PER_AGE[state.age.ordinal]
    val open = check.steps.filterNot { it.done }
    // Rusty words are a minute away, and polishing them counts them again: first, once nothing else but words and
    // friends is missing.
    val rusty = open.firstOrNull { it.kind == AgeStep.Kind.RUSTY }
    val learning = setOf(AgeStep.Kind.WORDS, AgeStep.Kind.RUSTY, AgeStep.Kind.FRIENDS)
    if (check.next != null && rusty != null && open.all { it.kind in learning }) return NextGoal.Polish(check.next, check.missing, rusty.need - rusty.have)
    // Only words and friends missing: people first (helping them is practice too), then words once every plot is
    // built. (Buildings or resources missing go on to Grow, which says what.) Without steps, the checklist's words.
    val onlyPeople = open.isNotEmpty() && open.all { it.kind == AgeStep.Kind.WORDS || it.kind == AgeStep.Kind.FRIENDS }
    if (check.next != null && onlyPeople && open.any { it.kind == AgeStep.Kind.FRIENDS }) return NextGoal.Friends(check.next, check.missing)
    val onlyWords = if (check.steps.isEmpty()) check.missing.all { "besed" in it } else open.all { it.kind == AgeStep.Kind.WORDS }
    if (plotsFull && check.next != null && onlyWords) return NextGoal.Learn(check.next, check.missing)
    val built = state.buildings.map { it.type }.toSet()
    val affordable = options.filter { o -> o.available && o.cost.all { (r, n) -> state.res(r) >= n } }
    val pick = affordable.sortedWith(compareBy({ it.type in built }, { it.cost.values.sum() })).firstOrNull()
    if (pick != null) return NextGoal.Build(pick)
    val scarce = scarcest(state, caps)
    return if (check.next != null) NextGoal.Grow(check.next, check.missing, goalGather(state, check.next) ?: scarce) else NextGoal.Gather(scarce)
}

/**
 * The way on of a build card whose person has nowhere to live ([BuildOption.room], companion/VILLAGERS.md "A building
 * brings its person"), one tap: "🏠 Postavi hišo · Build a house ›", "⬆️ Nadgradi kočo · Upgrade the hut ›", what to
 * gather for it first, "🏠 Za hišo naberi še 🪨 · For a house, gather more 🪨 ›", or the words its upgrade asks for first,
 * "🎯 Vadi besede za kočo · Practise the words for the hut ›" (companion/GAME.md, "Upgrades").
 */
fun roomLabel(w: si.lanisce.lani.game.RoomWay): String {
    val args = arrayOf<Pair<String, Any?>>("type" to w.type.name.lowercase(), "building" to w.type.names)
    val up = w.upgrade != null
    if (up && w.words != null) return "🎯 ${bi("villageSheets.roomUpgradeWords", *args)} ›"
    val text = when {
        w.short != null -> bi(if (up) "villageSheets.roomUpgradeGather" else "villageSheets.roomBuildGather", *args, "res" to w.short.emoji)
        up -> bi("villageSheets.roomUpgrade", *args)
        else -> bi("villageSheets.roomBuild", *args)
    }
    return "${if (up) "⬆️" else w.type.emoji} $text ›"
}

/**
 * What to gather for [next]: the resource with the largest shortfall for its cost plus the buildings it still
 * needs (the cheapest buildable kind for each). Null when nothing is short, e.g. only words are missing.
 */
fun goalGather(state: GameState, next: Age): Res? {
    val rule = Catalog.ageRules[next] ?: return null
    val need = rule.cost.toMutableMap()
    for ((types, n) in rule.needs) {
        val have = state.buildings.count { it.type in types && !it.damaged }
        if (have >= n) continue
        val cheapest = types.map { Catalog.spec(it) }.filter { state.age >= it.minAge }.minByOrNull { it.cost.values.sum() } ?: continue
        for ((r, c) in cheapest.cost) need[r] = (need[r] ?: 0) + c * (n - have)
    }
    return need.mapValues { (r, n) -> n - state.res(r) }.filterValues { it > 0 }.maxByOrNull { it.value }?.key
}

/** The resource with the lowest fill relative to its cap. */
fun scarcest(state: GameState, caps: Map<Res, Int>): Res =
    Res.entries.minBy { r -> state.res(r).toFloat() / (caps[r] ?: 100).coerceAtLeast(1) }
