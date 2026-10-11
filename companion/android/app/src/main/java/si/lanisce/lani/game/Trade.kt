package si.lanisce.lani.game

import kotlinx.serialization.Serializable
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.Localized
import si.lanisce.lani.l10n.bi
import java.time.LocalDate
import kotlin.math.roundToInt

/** The resources given in a trade on day [on] ([Trade]): one trade a day for each. */
@Serializable
data class TradeDay(val on: String, val gave: Set<Res> = emptySet())

/**
 * A trade's rate after its run, in hundredths (70: one given brings 0.70): [correct] of [total] fully right; [run], what the
 * answers added (nothing under half right); [market], the market's; [bonus], the better trades (Vida's scales, the lookout
 * tower's guests); [capped] when it would have been more than one for one.
 */
data class TradeRate(
    val pct: Int, val correct: Int, val total: Int, val run: Int, val market: Int, val bonus: Float, val capped: Boolean,
) {
    /** At least half of the run fully right: each right answer betters the rate. */
    val good: Boolean get() = run > 0 || Trade.good(correct, total)
}

/**
 * What a trade of [give] for [get] can be now: at most [max] of [give] (half the stock, and no more than [get]'s store can
 * take at the [best] rate the run can reach), or why it can't be ([reason], null when it can). [base] and [best]: the rate
 * after a run under half right and after a run all right (hundredths).
 */
data class TradeLimit(val give: Res, val get: Res, val max: Int, val base: Int, val best: Int, val reason: String?) {
    val available: Boolean get() = reason == null && max > 0

    /** What [amount] of [give] brings at [pct]: rounded down, at least 1. */
    fun brings(amount: Int, pct: Int): Int = Trade.brings(amount, pct)
}

/**
 * A trade done after its run (or not): [given] of [give] went out and [got] of [get] came in, at [rate]; nothing when
 * [left] (the run was left before its end) or the store had no room any more ([given] 0).
 */
data class TradeDeal(val give: Res, val get: Res, val asked: Int, val given: Int, val got: Int, val rate: TradeRate, val left: Boolean = false) {
    val traded: Boolean get() = given > 0 && got > 0
}

/**
 * "⇄ Zamenjaj · Trade" (companion/GAME.md, "Trading"): one resource for another, after a short run that tests both skills
 * (Jan, 2026-10-11: "trade resources from one to another (food -> wood), maybe with a short test mix of both categories").
 * Vocabulary is the bulk of practice, so 🌾 piles up while 🪵 and 🪨 run short; the trade turns some of it into what the
 * village lacks, at a worse rate than the merchant's and once a day for each resource given, so it helps a stuck village
 * without standing in for practice. Pure, like the engine.
 */
internal object Trade {
    /** The skill of each exercise of the run: the giving resource's and the getting one's in turn, [give] first. */
    fun mix(give: Res, get: Res, canSpeak: Boolean, n: Int = Catalog.TRADE_EXERCISES): List<Res> {
        val a = asked(give, canSpeak)
        val b = asked(get, canSpeak)
        return List(n) { if (it % 2 == 0) a else b }
    }

    /**
     * The skill a half of the run practises for [r]: its own; listening only when the phone can speak (as the storm), else
     * vocabulary, what gathering wood without a voice reads instead ("listening becomes reading"). Wisdom's half is what
     * gathering under the linden asks: the modules' conversation and writing, else the learner's cards translated.
     */
    fun asked(r: Res, canSpeak: Boolean): Res = if (r == Res.WOOD && !canSpeak) Res.FOOD else r

    /** Whether [correct] of [total] is at least half of the run right (3 of 6): from there each right answer betters the rate. */
    fun good(correct: Int, total: Int): Boolean = total > 0 && correct * 2 >= total

    /** The market (Tržnica) betters trades, from Trg, while it stands (as it buys goods: [Chest.market]). */
    fun atMarket(s: GameState): Boolean = s.age >= Age.TRG && s.buildings.any { it.type == BuildingType.MARKET && !it.damaged }

    /**
     * The rate after a run of [total] exercises with [correct] fully right, in hundredths: [Catalog.TRADE_BASE], plus
     * [Catalog.TRADE_RUN_BONUS] shared among the run's answers (0.05 a right answer of six) once at least half are right,
     * plus [Catalog.TRADE_MARKET] at the market, times the better trades (Vida's scales, like the merchant's), at most
     * [Catalog.TRADE_MAX] (one for one), rounded down.
     */
    fun rate(s: GameState, correct: Int, total: Int = Catalog.TRADE_EXERCISES): TradeRate {
        val c = correct.coerceIn(0, total.coerceAtLeast(0))
        val run = if (good(c, total)) Catalog.TRADE_RUN_BONUS * c / total else 0
        val market = if (atMarket(s)) Catalog.TRADE_MARKET else 0
        val bonus = bonusEffect(s).trade.coerceAtLeast(0f)
        // in thousandths of the factor, so 1.25 × 0.80 is exactly 1.00
        val raw = (Catalog.TRADE_BASE + run + market) * (1000 + (bonus * 1000).roundToInt()) / 1000
        return TradeRate(raw.coerceAtMost(Catalog.TRADE_MAX), c, total, run, market, bonus, raw > Catalog.TRADE_MAX)
    }

    /** What [amount] given brings at [pct] (hundredths): rounded down, at least 1 (nothing for nothing). */
    fun brings(amount: Int, pct: Int): Int = if (amount <= 0) 0 else (amount.toLong() * pct / 100).toInt().coerceAtLeast(1)

    /** The most of a store one trade may give: half of it, rounded down. */
    fun share(stock: Int): Int = (stock * Catalog.TRADE_MAX_SHARE).toInt().coerceAtLeast(0)

    /**
     * The most that may be given for [room] in the getting store at [pct], so that what comes in ([brings], rounded down)
     * still fits: the largest amount whose rate brings less than room + 1.
     */
    fun forRoom(room: Int, pct: Int): Int =
        if (room <= 0 || pct <= 0) 0 else (((room.toLong() + 1) * 100 - 1) / pct).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Whether [give] was traded away [today] already. */
    fun tradedToday(s: GameState, give: Res, today: LocalDate): Boolean = s.trades?.takeIf { it.on == today.toString() }?.gave?.contains(give) == true

    /**
     * What a trade of [give] for [get] can be [today]: the most to give (half the stock; no more than [get]'s store can take
     * at the best rate a run of [total] can reach), the rates a run brings, and why not: the same resource, traded away
     * today already, a full store, too little to give.
     */
    fun limit(s: GameState, give: Res, get: Res, today: LocalDate, total: Int = Catalog.TRADE_EXERCISES): TradeLimit {
        val base = rate(s, 0, total).pct
        val best = rate(s, total, total).pct
        val caps = GameEngine.attributes(s).caps
        val room = (caps.getValue(get) - s.res(get)).coerceAtLeast(0)
        val max = minOf(share(s.res(give)), forRoom(room, best))
        val reason = when {
            give == get -> bi("trade.same")
            tradedToday(s, give, today) -> bi("trade.onceADay", "res" to give.names)
            room <= 0 -> bi("trade.full", "res" to get.names)
            max <= 0 -> bi("trade.tooLittle", "res" to give.names)
            else -> null
        }
        return TradeLimit(give, get, if (reason == null) max else 0, base, best, reason)
    }

    /** Why [give] can't be given at all [today] (traded away already, too little), or null. */
    fun giveBlocker(s: GameState, give: Res, today: LocalDate): String? = when {
        tradedToday(s, give, today) -> bi("trade.onceADay", "res" to give.names)
        share(s.res(give)) <= 0 -> bi("trade.tooLittle", "res" to give.names)
        else -> null
    }

    /**
     * What to get for [give] at first: the resource the village is shortest of (by its share of the store), whose store has
     * room; null when none has.
     */
    fun suggest(s: GameState, give: Res): Res? {
        val caps = GameEngine.attributes(s).caps
        return Res.entries.filter { it != give && s.res(it) < caps.getValue(it) }
            .minByOrNull { s.res(it).toFloat() / caps.getValue(it).coerceAtLeast(1) }
    }

    /**
     * The trade's run: [Catalog.TRADE_EXERCISES] untimed exercises, the giving resource's skill and the getting one's in
     * turn ([mix]), from the gathering run's sources with the learner's own cards first (weakest first; the modules add
     * variety). Each answer pays what it practises. Null when [give] for [get] can't be traded [today] ([limit]).
     */
    fun challenge(s: GameState, give: Res, get: Res, amount: Int, pool: ContentPool, today: LocalDate): Challenge? {
        val l = limit(s, give, get, today)
        if (!l.available) return null
        val n = amount.coerceIn(1, l.max)
        val skills = mix(give, get, pool.canSpeak)
        val r = rng(s.seed, "trade", give, get, today.toString(), s.stats.totalEarned.values.sumOf { it.toLong() })
        // each half from its own skill (one skill for both when the phone can't speak for listening), taken in turn; the
        // second half from the cards the first didn't ask, so no word comes twice
        val used = HashSet<String>()
        val halves = skills.distinct().associateWith { res ->
            val left = pool.cards.filter { it.id !in used }.ifEmpty { pool.cards }
            val picked = Content.pick(res, skills.count { it == res }, pool, r, cards = left).items
            used += picked.mapNotNull { it.cardId }
            ArrayDeque(picked)
        }
        val items = skills.mapNotNull { halves.getValue(it).removeFirstOrNull() }
        val lines = listOfNotNull(
            bi("trade.intro", "give" to n, "giveEmoji" to give.emoji, "getEmoji" to get.emoji, "low" to brings(n, l.base), "high" to brings(n, l.best)),
            bi("trade.howRate", "base" to decimal(l.base), "bonus" to decimal(Catalog.TRADE_RUN_BONUS / Catalog.TRADE_EXERCISES), "half" to (Catalog.TRADE_EXERCISES + 1) / 2, "total" to Catalog.TRADE_EXERCISES),
            if (get == Res.WOOD && !pool.canSpeak || give == Res.WOOD && !pool.canSpeak) "🔇 ${bi("trade.noVoice")}" else null,
        )
        return Challenge(
            title = "⇄ ${bi("trade.title")}: ${give.emoji} → ${get.emoji}",
            intro = lines.joinToString("\n"),
            emoji = "⇄",
            exercises = items.map { it.exercise },
            skills = items.map { it.skill },
            cardIds = items.map { it.cardId },
            moduleIds = items.map { it.moduleId },
        )
    }

    /**
     * The trade after its run: [answered] of [total] exercises, [correct] fully right; its answers were paid already
     * ([GameEngine.earn]). A run left before its end trades nothing. Else [asked] of [give] goes out (no more than half of
     * what is in store now, nor than [get]'s store can still take at the rate) for [give] × the rate of [get] ([rate]),
     * rounded down, at least 1; [today] counts the trade for [give], and the chronicle tells it. [s] unchanged when nothing
     * was traded.
     */
    fun settle(
        s: GameState, give: Res, get: Res, asked: Int, correct: Int, total: Int, answered: Int, today: LocalDate, now: Long,
    ): Pair<GameState, TradeDeal> {
        val rate = rate(s, correct, total)
        val none = TradeDeal(give, get, asked, 0, 0, rate, left = answered < total || total <= 0)
        if (none.left || give == get || tradedToday(s, give, today)) return s to none
        val room = (GameEngine.attributes(s).caps.getValue(get) - s.res(get)).coerceAtLeast(0)
        val given = minOf(asked, share(s.res(give)), forRoom(room, rate.pct))
        if (given <= 0) return s to none
        val got = brings(given, rate.pct).coerceAtMost(room)
        val (paid, _) = debit(s, mapOf(give to given))
        val (credited, came) = credit(paid, mapOf(get to got))
        val day = today.toString()
        val gave = (s.trades?.takeIf { it.on == day }?.gave.orEmpty()) + give
        val line = bi("trade.chronicle", "given" to given, "giveEmoji" to give.emoji, "got" to (came[get] ?: 0), "getEmoji" to get.emoji, "rate" to decimal(rate.pct))
        return credited.copy(trades = TradeDay(day, gave)).logged(now, "⇄" to line) to
            TradeDeal(give, get, asked, given, came[get] ?: 0, rate)
    }

    /**
     * [d] as a run's result: won when something was traded, what came in as its reward and what went out as its loss, and
     * the line for the result card (the chronicle's; why nothing was traded).
     */
    fun result(d: TradeDeal): ChallengeResult = ChallengeResult(
        won = d.traded,
        rewards = if (d.traded) mapOf(d.get to d.got) else emptyMap(),
        losses = if (d.traded) mapOf(d.give to d.given) else emptyMap(),
        damaged = emptyList(),
        message = when {
            d.traded -> bi("trade.chronicle", "given" to d.given, "giveEmoji" to d.give.emoji, "got" to d.got, "getEmoji" to d.get.emoji, "rate" to decimal(d.rate.pct))
            d.left -> bi("trade.left")
            else -> bi("trade.noRoom", "res" to d.get.names)
        },
    )

    /** A rate (hundredths) as each language writes it: "0,70" in Slovene, German and Italian, "0.70" in English. */
    fun decimal(pct: Int): Localized = Localized { lang ->
        "%d%s%02d".format(pct / 100, if (lang == Lang.EN) "." else ",", pct % 100)
    }

    /** A factor (1.25) as each language writes it: "1,25", "1.25". */
    fun factor(f: Float): Localized = decimal((f * 100).roundToInt())
}
