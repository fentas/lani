package si.lanisce.lani.game

import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.l10n.bi
import kotlin.math.ceil

/**
 * A moba (the Slovene tradition of neighbours coming to help build or bring in the harvest) for one price:
 * the neighbours cover part of its 🪵 and 🪨 for the learner's 🤝.
 */
data class Moba(
    /** Neighbours who come: everyone who lives here, babies aside. */
    val helpers: Int,
    /** Their share of the 🪵 and 🪨, at most [Catalog.MOBA_MAX_SHARE]. */
    val share: Float,
    /** What they bring (🪵, 🪨): the share, or less when the 🤝 don't reach. */
    val covers: Map<Res, Int>,
    /** 🤝 it costs. */
    val help: Int,
    /** What is left for the village to pay. */
    val rest: Map<Res, Int>,
    /** The village can pay [rest] (the 🤝 are there by construction). */
    val affordable: Boolean,
) {
    /** "−40 🪵 −20 🪨 za 12 🤝 · for 12 🤝" */
    val text: String
        get() = Res.entries.mapNotNull { r -> covers[r]?.let { "−$it ${r.emoji}" } }.joinToString(" ") + " " + bi("help.forHelp", "n" to help)
}

/**
 * 🤝 Pomoč · Help: earned by helping people (a villager's request passed, a talk with a villager, a scene's
 * happening, a family answer; never by drills or gathering), spent on a moba. Pure, like the engine.
 */
internal object Help {
    /** Adds [n] 🤝 (never below zero, never past Int.MAX_VALUE) and counts them in the stats. */
    fun earn(s: GameState, n: Int): GameState {
        if (n <= 0) return s
        val help = (s.help.toLong() + n).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val earned = (s.stats.helpEarned.toLong() + n).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return s.copy(help = help, stats = s.stats.copy(helpEarned = earned))
    }

    /**
     * Who comes to a moba: the people who live here, babies aside (by the day of the last tick). A village whose
     * people have no names yet (an older bridge) counts its population.
     */
    fun helpers(s: GameState): Int {
        if (s.residents.isEmpty()) return s.villagers.coerceAtLeast(0)
        val day = isoDay(s.lastTick) ?: return s.residents.size
        return s.residents.count { Residents.stage(it, day) != Residents.Stage.BABY }
    }

    /** The share of the 🪵 and 🪨 the neighbours cover: 10 % each, at most half. */
    fun share(helpers: Int): Float = (Catalog.MOBA_SHARE_PER_HELPER * helpers).coerceIn(0f, Catalog.MOBA_MAX_SHARE)

    /**
     * A moba for [cost]: the neighbours' share of its 🪵 and 🪨, as far as the learner's 🤝 reach
     * ([Catalog.mobaPerHelp] each). Null when there is nobody to come, no 🤝, or nothing they could bring.
     */
    fun moba(s: GameState, cost: Map<Res, Int>): Moba? {
        val helpers = helpers(s)
        val share = share(helpers)
        if (share <= 0f || s.help <= 0) return null
        val want = Catalog.MOBA_RES.associateWith { r -> ((cost[r] ?: 0) * share).toInt() }.filterValues { it > 0 }
        val total = want.values.sum()
        if (total <= 0) return null
        val per = Catalog.mobaPerHelp(s.age)
        val budget = s.help.toLong() * per
        // Not enough 🤝 for all of it: they cover what the 🤝 pay for, in the same proportion.
        val covers = if (budget >= total) want
        else want.mapValues { (_, n) -> (n.toLong() * budget / total).toInt() }.filterValues { it > 0 }
        val covered = covers.values.sum()
        if (covered <= 0) return null
        val help = ceil(covered.toDouble() / per).toInt()
        val rest = cost.mapValues { (r, n) -> n - (covers[r] ?: 0) }.filterValues { it > 0 }
        return Moba(helpers, share, covers, help, rest, rest.all { (r, n) -> s.res(r) >= n })
    }

    /**
     * Pays [cost], with a moba when [withMoba] and one is possible: the rest from the stores, the 🤝 from the
     * help. Null when the village can't pay.
     */
    fun pay(s: GameState, cost: Map<Res, Int>, withMoba: Boolean): Pair<GameState, Moba?>? {
        val moba = if (withMoba) moba(s, cost) else null
        val due = moba?.rest ?: cost
        if (due.any { (r, n) -> s.res(r) < n }) return null
        val (paid, _) = debit(s, due)
        if (moba == null) return paid to null
        return paid.copy(help = (paid.help - moba.help).coerceAtLeast(0), stats = paid.stats.copy(mobas = paid.stats.mobas + 1)) to moba
    }

    /** What a chronicle line adds for a moba: " · 🤝 moba: −40 🪵 −20 🪨"; empty without one. */
    fun note(m: Moba?): String =
        m?.let { " · 🤝 moba: " + Res.entries.mapNotNull { r -> it.covers[r]?.let { n -> "−$n ${r.emoji}" } }.joinToString(" ") }.orEmpty()
}
