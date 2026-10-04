package si.lanisce.lani.game

import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.Seller
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Memory
import si.lanisce.lani.l10n.Dates
import si.lanisce.lani.l10n.bi
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * An item's name in the culture's languages (game/culture, chest.json): [nameText] as a label ("Sekira", "An axe"),
 * [accText] as the object of a sentence ("ti podari sekiro", "gives you an axe"), [theText] definite in a sentence
 * ("izboljšal sekiro", "improved the axe").
 */
data class ItemName(val emoji: String, val nameText: Text, val accText: Text = nameText, val theText: Text = accText) {
    /** The name in the target language: "Sekira". */
    val sl: String get() = nameText.target
    /** The name in the base language: "An axe". */
    val en: String get() = nameText.base
    /** As an object in the target language, for "ti podari …": "sekiro". */
    val acc: String get() = accText.target
    /** "🪓 Sekira · An axe" */
    val label: String get() = "$emoji ${nameText.bi()}"

    /** As it reads in the learner's pair now. */
    override fun toString() = "ItemName(emoji=$emoji, sl=$sl, acc=$acc, en=$en)"
}

/** What a tool changes at strength 1 (a first gift, not forged); [Chest.power] scales it. */
data class ToolEffect(
    val production: Map<Res, Float> = emptyMap(),
    /** Share of the daily food the villagers don't eat. */
    val thrift: Float = 0f,
    /** Extra hours to answer an event. */
    val warningHours: Int = 0,
    /** Added to the morale the village settles at. */
    val morale: Int = 0,
    /** Extra share of a quest's reward. */
    val questPay: Float = 0f,
    /** Added to the rate of a trade (the merchant, the market). */
    val trade: Float = 0f,
    /** Extra storage for every resource. */
    val caps: Int = 0,
) {
    fun times(f: Float) = ToolEffect(
        production = production.mapValues { it.value * f },
        thrift = thrift * f,
        warningHours = (warningHours * f).roundToInt(),
        morale = (morale * f).roundToInt(),
        questPay = questPay * f,
        trade = trade * f,
        caps = (caps * f).roundToInt(),
    )

    operator fun plus(o: ToolEffect) = ToolEffect(
        production = (production.keys + o.production.keys).associateWith { (production[it] ?: 0f) + (o.production[it] ?: 0f) },
        thrift = thrift + o.thrift,
        warningHours = warningHours + o.warningHours,
        morale = morale + o.morale,
        questPay = questPay + o.questPay,
        trade = trade + o.trade,
        caps = caps + o.caps,
    )
}

/**
 * The tool villager [giver] ([name] to friends) gives: [first] at friendship level 2, [better] at level 4; [forge]:
 * it is of iron or brass, so the smith can make it better.
 */
data class ToolLine(
    val giver: String, val name: String, val first: ItemName, val better: ItemName, val effect: ToolEffect, val forge: Boolean = false,
) {
    fun name(tier: Int): ItemName = if (tier >= 2) better else first
}

/**
 * A good: a thank-you, a gift for someone who likes it, something to sell; [food] can be served at a feast. [rare]: only
 * from a festival or the pedlar, and everyone likes it ([adult]: wine, not for the children).
 */
data class GoodSpec(val id: String, val name: ItemName, val value: Int, val food: Boolean = true, val rare: Boolean = false, val adult: Boolean = false)

/**
 * Something a villager gave Jan: a tool ([tool], its tier; [effect]: what it does now) or a good ([good]: its id). [text]
 * is the line for the chronicle and the reward.
 */
data class Gift(val from: String, val item: ItemName, val tool: Boolean, val text: String, val good: String? = null, val effect: ToolEffect? = null)

/** Where goods can be sold now, and at what rate. */
data class Market(val emoji: String, val label: String, val rate: Float)

/** A good on sale today: from [seller] ([Chest.PEDLAR], [Chest.MERCHANT], [Chest.MARKET]), for [price], [left] of it. */
data class Offer(val seller: String, val good: GoodSpec, val price: Map<Res, Int>, val left: Int)

/** Someone who sells goods today (see [Chest.stalls]). */
data class Stall(val id: String, val emoji: String, val label: String, val offers: List<Offer>)

/** Forging a tool at the smithy: its cost, the good the smith takes, and why not (null when it can be done). */
data class ForgeOption(val giver: String, val step: Int, val cost: Map<Res, Int>, val good: GoodSpec?, val reason: String?, val effect: String) {
    val available: Boolean get() = reason == null
}

/**
 * "Skrinja · The chest" (companion/GAME.md): the tools the villagers give at friendship levels 2 and 4 (kept for
 * good, each changes a rule), the goods they thank with (to give to someone who likes them, or to sell), and the
 * smith forging tools. Pure, like the engine.
 */
internal object Chest {
    /** The friendship levels a villager gives something at. */
    val GIFT_LEVELS = listOf(2, 4)

    /** How strong a tool is: its tier (1 or 2), and half as much again for each forging. */
    fun power(t: Tool): Float = t.tier.coerceIn(1, 2) + 0.5f * t.forged.coerceIn(0, Catalog.MAX_FORGED)

    /** What tool [t] of villager [giver] does now; null for a tool the catalog doesn't know. */
    fun effectOf(giver: String, t: Tool): ToolEffect? = Catalog.tools[giver]?.effect?.times(power(t))

    /** Every tool in the chest, together. */
    fun effect(s: GameState): ToolEffect =
        s.chest.tools.entries.fold(ToolEffect()) { acc, (id, t) -> effectOf(id, t)?.let { acc + it } ?: acc }

    /** "+10 % 🪵", "−10 % 🍲", "+6 h ⏳", "+3 😊", "+10 % 📋", "+25 % ⚖️", "+40 max" */
    fun effectText(e: ToolEffect): String = buildList {
        e.production.forEach { (r, p) -> if (p > 0f) add("+${(p * 100).roundToInt()} % ${r.emoji}") }
        if (e.thrift > 0f) add("−${(e.thrift * 100).roundToInt()} % 🍲")
        if (e.warningHours > 0) add("+${e.warningHours} h ⏳")
        if (e.morale > 0) add("+${e.morale} 😊")
        if (e.questPay > 0f) add("+${(e.questPay * 100).roundToInt()} % 📋")
        if (e.trade > 0f) add("+${(e.trade * 100).roundToInt()} % ⚖️")
        if (e.caps > 0) add("+${e.caps} max")
    }.joinToString(", ")

    /** What [effectText] means, in words: "+10 % 🪵: več lesa · more wood". */
    fun effectHelp(e: ToolEffect): String = buildList {
        e.production.forEach { (r, p) -> if (p > 0f) add("+${(p * 100).roundToInt()} % ${r.emoji}: ${bi("chest.moreOf", "res" to Cultures.current.partitive(r))}") }
        if (e.thrift > 0f) add("−${(e.thrift * 100).roundToInt()} % 🍲: ${bi("chest.eatLess")}")
        if (e.warningHours > 0) add("+${e.warningHours} h ⏳: ${bi("chest.moreTime")}")
        if (e.morale > 0) add("+${e.morale} 😊: ${bi("chest.betterSpirits")}")
        if (e.questPay > 0f) add("+${(e.questPay * 100).roundToInt()} % 📋: ${bi("chest.requestsPayMore")}")
        if (e.trade > 0f) add("+${(e.trade * 100).roundToInt()} % ⚖️: ${bi("chest.betterTrades")}")
        if (e.caps > 0) add("+${e.caps}: ${bi("chest.biggerStores")}")
    }.joinToString("\n")

    // --- gifts at friendship levels -------------------------------------------------------------

    /** The good villager [id] gives when they have no tool in the catalog: one of the small gifts, by who they are. */
    fun smallGift(id: String, level: Int): GoodSpec {
        val list = Catalog.smallGifts
        return Catalog.goods.getValue(list[Math.floorMod(id.hashCode() + level, list.size)])
    }

    /**
     * What villager [id] gives at [level] (2 or 4): their tool (or the better one), else a small good. Never announced
     * before it comes (Jan: a gift is a surprise, so no screen says a gift waits or how near it is).
     */
    fun giftAt(id: String, level: Int): ItemName =
        Catalog.tools[id]?.name(if (level >= 4) 2 else 1) ?: smallGift(id, level).name

    /** What friends call villager [id]: the cast's name, a resident's first name, or the id. */
    fun nameOf(s: GameState, id: String): String =
        Catalog.tools[id]?.name ?: s.residents.firstOrNull { it.id == id }?.name?.substringBefore(' ') ?: id.replaceFirstChar { it.uppercase() }

    /**
     * Villager [id]'s friendship reached a new level: the gifts of the levels they reached and haven't given yet
     * (a level-4 friend who never gave a tool gives the better one). Returns the new state and the gifts.
     */
    fun onBond(s: GameState, id: String, now: Long): Pair<GameState, List<Gift>> {
        val level = Bonds.level(s, id)
        val done = s.chest.gifted[id] ?: 0
        val due = GIFT_LEVELS.filter { it in (done + 1)..level }
        if (due.isEmpty()) return s to emptyList()
        var st = s
        if (Catalog.tools.containsKey(id)) {
            // one tool, at its best tier: a friend of level 4 who never gave the first one gives the better one
            val old = st.chest.tools[id]
            val tool = Tool(tier = maxOf(if (due.last() >= 4) 2 else 1, old?.tier ?: 0), forged = old?.forged ?: 0)
            st = st.copy(chest = st.chest.copy(tools = st.chest.tools + (id to tool)))
        } else {
            for (lv in due) st = add(st, smallGift(id, lv).id, 1)
        }
        st = st.copy(chest = st.chest.copy(gifted = st.chest.gifted + (id to due.last())))
        val gifts = giftsBetween(s, st, id)
        return st.logged(now, *gifts.map { it.item.emoji to it.text }.toTypedArray()) to gifts
    }

    /** The gifts villager [id] gave between two states of the village (for a result card, after [Bonds.add]). */
    fun giftsBetween(before: GameState, after: GameState, id: String): List<Gift> {
        val from = before.chest.gifted[id] ?: 0
        val to = after.chest.gifted[id] ?: 0
        if (to <= from) return emptyList()
        val name = nameOf(after, id)
        val tool = after.chest.tools[id]
        val line = Catalog.tools[id]
        val lines = Cultures.current.chest.lines
        if (line != null && tool != null) {
            val item = line.name(tool.tier)
            val effect = effectOf(id, tool)!!
            val text = lines.giftTool.bi("name" to name, "item" to item.accText, "emoji" to item.emoji, "effect" to effectText(effect))
            return listOf(Gift(id, item, tool = true, text = text, effect = effect))
        }
        return GIFT_LEVELS.filter { it in (from + 1)..to }.map { lv ->
            val good = smallGift(id, lv)
            val g = good.name
            Gift(id, g, tool = false, text = lines.gift.bi("name" to name, "item" to g.accText, "emoji" to g.emoji), good = good.id)
        }
    }

    /**
     * Friends who reached a gift's level before there were gifts (an older save) and are in the village today
     * ([present]: ids, null = everyone) give it now.
     */
    fun catchUp(s: GameState, present: Set<String>?, now: Long): Pair<GameState, List<Gift>> {
        var st = s
        val gifts = ArrayList<Gift>()
        for (id in s.bonds.keys.sorted()) {
            if (present != null && id !in present) continue
            val (next, g) = onBond(st, id, now)
            st = next
            gifts += g
        }
        return st to gifts
    }

    // --- goods ----------------------------------------------------------------------------------

    fun count(s: GameState, good: String): Int = s.chest.goods[good] ?: 0

    /** Adds [n] of [good] (removes, when negative; never below zero). */
    fun add(s: GameState, good: String, n: Int): GameState {
        val left = (count(s, good).toLong() + n).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        val goods = if (left == 0) s.chest.goods - good else s.chest.goods + (good to left)
        return s.copy(chest = s.chest.copy(goods = goods))
    }

    /** The good villager [id] thanks with after their request, as an app before the rotation did: their one `thanks` good. */
    fun thanksOf(id: String?): GoodSpec = Catalog.goods.getValue(id?.let { Catalog.thanks[it] } ?: Catalog.DEFAULT_THANKS)

    /**
     * The good villager [id] thanks with for request [request] (its id), done on [today], in village [seed]: in a
     * festival's season the one the pack gives them for it ([seasonal]); else from their rotation (chest.json
     * `thanks_rotation`): two requests in three their usual `thanks` good, the third one of the others, the same for the
     * same request however often it's opened or tried, maybe another for the next. Only goods the chest's catalogue
     * has; someone without a rotation thanks with their `thanks` good, someone the pack doesn't know with
     * [Catalog.DEFAULT_THANKS] ([thanksOf]). (The usual one stays the usual: an even turn gave friendships a push the
     * pacing simulation felt, more goods going to who likes them.)
     */
    fun thanksFor(id: String?, request: String, seed: Long, today: LocalDate): GoodSpec {
        val base = thanksOf(id)
        val r = id?.let { Catalog.thanksRotation[it] } ?: return base
        seasonal(r, today)?.let { return it }
        val others = r.goods.filter { it != base.id }.distinct().mapNotNull { Catalog.goods[it] }
        if (others.isEmpty()) return base
        // two requests in three their usual good, the third one of the others
        val k = rng(seed, "thanks", id, request).nextInt(3 * others.size)
        return if (k < 2 * others.size) base else others[k - 2 * others.size]
    }

    /**
     * The seasonal thank-you of [r] on [today]: the good of a festival whose season it is ([Catalog.THANKS_SEASON_DAYS]
     * before its day to its last grace day, [Calendar.GRACE_DAYS]); of two, the one nearer its day. Null out of season,
     * or for a festival or a good the culture doesn't have.
     */
    fun seasonal(r: si.lanisce.lani.game.culture.ThanksRotation, today: LocalDate): GoodSpec? =
        r.seasonal.mapNotNull { (fid, good) ->
            val g = Catalog.goods[good] ?: return@mapNotNull null
            val f = Calendar.byId(fid) ?: return@mapNotNull null
            val away = (today.year - 1..today.year + 1).map { java.time.temporal.ChronoUnit.DAYS.between(f.rule.date(it), today) }
                .filter { it in -Catalog.THANKS_SEASON_DAYS..Calendar.GRACE_DAYS }.minByOrNull { kotlin.math.abs(it) }
                ?: return@mapNotNull null
            g to kotlin.math.abs(away)
        }.minByOrNull { it.second }?.first

    /**
     * The goods villager [id] likes: their favourites, and every rare good (no wine for the children, nor for someone
     * the catalog doesn't know: they may be a child born here).
     */
    fun likes(id: String): List<GoodSpec> {
        val grown = Catalog.likes.containsKey(id) && id !in Catalog.children
        val rare = Catalog.goods.values.filter { it.rare && (grown || !it.adult) }
        return (Catalog.likes[id] ?: Catalog.defaultLikes).mapNotNull { Catalog.goods[it] } + rare
    }

    /** Whether [good] is one of villager [id]'s own favourites (the pack's likes; someone it doesn't know, the defaults). */
    fun favourite(id: String, good: String): Boolean = good in (Catalog.likes[id] ?: Catalog.defaultLikes)

    /**
     * ♥ for [good] given to villager [id]: a rare one counts twice, one of their favourites [Catalog.GIFT_POINTS], any
     * other good [Catalog.PLAIN_GIFT_POINTS] (warm thanks).
     */
    fun giftPoints(good: String, id: String): Int = when {
        Catalog.goods[good]?.rare == true -> Catalog.RARE_GIFT_POINTS
        favourite(id, good) -> Catalog.GIFT_POINTS
        else -> Catalog.PLAIN_GIFT_POINTS
    }

    /**
     * Whether [good] is for villager [id] at all: anything, but no wine for the children (nor for someone the catalog
     * doesn't know: they may be a child born here).
     */
    fun forThem(id: String, good: String): Boolean =
        Catalog.goods[good]?.adult != true || (Catalog.likes.containsKey(id) && id !in Catalog.children)

    /** Why [good] can't be given to [id] today, or null when it can: any good that's [forThem], once a day each. */
    fun giveBlocker(s: GameState, good: String, id: String, today: LocalDate): String? = when {
        count(s, good) <= 0 -> bi("chest.youHaveNone")
        !forThem(id, good) -> bi("chest.notForChildren")
        s.chest.given[id] == today.toString() -> bi("chest.gaveToday")
        else -> null
    }

    /**
     * Jan gives [good] to villager [id]: one fewer in the chest, ♥ by what it is to them ([giftPoints]; their
     * friendship may reach a level, and a gift of theirs), and they keep [memory]. Once a day each; [s] unchanged
     * when it can't be given.
     */
    fun give(s: GameState, good: String, id: String, today: LocalDate, now: Long, memory: Memory? = null): GameState {
        if (giveBlocker(s, good, id, today) != null) return s
        val g = Catalog.goods.getValue(good)
        val given = add(s, good, -1).let { it.copy(chest = it.chest.copy(given = it.chest.given.filterValues { d -> d == today.toString() } + (id to today.toString()))) }
            .logged(now, g.name.emoji to Cultures.current.chest.lines.given.bi("name" to nameOf(s, id), "item" to g.name.accText))
        return Bonds.add(given, id, giftPoints(good, id), today, memory, now = now)
    }

    /** Where goods can be sold now: to the merchant while he is here, at the market from Trg; null when nowhere. */
    fun market(s: GameState): Market? {
        val bonus = 1f + bonusEffect(s).trade // Vida's scales, the lookout tower's guests
        return when {
            s.age >= Age.TRG && s.buildings.any { it.type == BuildingType.MARKET && !it.damaged } ->
                seller(MARKET).let { Market(it.emoji, it.name.bi(), Catalog.MARKET_RATE * bonus) }
            s.event?.kind == EventKind.MERCHANT -> seller(MERCHANT).let { Market(it.emoji, it.name.bi(), Catalog.MERCHANT_RATE * bonus) }
            else -> null
        }
    }

    /**
     * What [good] sells for now: its value, growing with the age like quest rewards, times the rate, in the
     * resource the village is shortest of (as much as its store can take). Null when it can't be sold now.
     */
    fun price(s: GameState, good: String): Pair<Res, Int>? {
        val m = market(s) ?: return null
        val g = Catalog.goods[good] ?: return null
        val caps = GameEngine.attributes(s).caps
        val want = Res.entries.minBy { s.res(it).toFloat() / caps.getValue(it).coerceAtLeast(1) }
        val room = (caps.getValue(want) - s.res(want)).coerceAtLeast(0)
        // nobody pays more for a good than they ask for it (even with Vida's forged scales)
        val rate = minOf(m.rate, Catalog.BUY_RATE)
        val n = minOf((g.value * Quests.ageMultiplier(s.age) * rate).roundToInt(), room)
        return if (n > 0) want to n else null
    }

    // --- buying goods ---------------------------------------------------------------------------

    const val PEDLAR = "pedlar"
    const val MERCHANT = "merchant"
    const val MARKET = "market"

    /** What [good] costs to buy: its value, growing with the age like the sale price, times [Catalog.BUY_RATE], in 🌾 and 🪵. */
    fun buyPrice(s: GameState, good: String): Map<Res, Int> {
        val g = Catalog.goods[good] ?: return emptyMap()
        val total = (g.value * Quests.ageMultiplier(s.age) * Catalog.BUY_RATE).roundToInt()
        val food = (total * Catalog.BUY_FOOD_SHARE).roundToInt()
        return mapOf(Res.FOOD to food, Res.WOOD to total - food).filterValues { it > 0 }
    }

    /** How many of [good] Jan bought from [seller] today. */
    fun boughtToday(s: GameState, seller: String, good: String, today: LocalDate): Int =
        if (s.boughtOn == today.toString()) s.bought["$seller/$good"] ?: 0 else 0

    /**
     * Who sells goods today, and what: the pedlar on his day (his rare good and two others), the merchant while he's
     * in the village, the market from Trg (five goods a day). What each offers is the day's dice, the same all day.
     */
    fun stalls(s: GameState, today: LocalDate): List<Stall> = buildList {
        val day = today.toString()
        val common = Catalog.goods.values.filter { !it.rare }.sortedBy { it.id }
        fun offers(seller: String, n: Int, stock: Int) = common.shuffled(rng(s.seed, "stall", seller, day)).take(n).map { offer(s, seller, it.id, stock, today) }
        fun stall(id: String, offers: List<Offer>) = seller(id).let { Stall(id, it.emoji, it.name.bi(), offers) }
        Surprises.today(s, today)?.takeIf { it.kind == Surprises.PEDLAR }?.let { sp ->
            val rare = Catalog.pedlarGoods[Math.floorMod(sp.variant, Catalog.pedlarGoods.size)]
            add(stall(PEDLAR, listOf(offer(s, PEDLAR, rare, Catalog.RARE_STOCK, today)) + offers(PEDLAR, Catalog.PEDLAR_OFFERS, Catalog.PEDLAR_STOCK)))
        }
        if (s.event?.kind == EventKind.MERCHANT) add(stall(MERCHANT, offers(MERCHANT, Catalog.MERCHANT_OFFERS, Catalog.MERCHANT_STOCK)))
        if (s.age >= Age.TRG && s.buildings.any { it.type == BuildingType.MARKET && !it.damaged }) {
            add(stall(MARKET, offers(MARKET, Catalog.MARKET_OFFERS, Catalog.MARKET_STOCK)))
        }
    }

    /** Who sells ([PEDLAR], [MERCHANT], [MARKET]): the culture's name and emoji for them. */
    private fun seller(id: String): Seller = Cultures.current.chest.sellers.getValue(id)

    private fun offer(s: GameState, seller: String, good: String, stock: Int, today: LocalDate): Offer =
        Offer(seller, Catalog.goods.getValue(good), buyPrice(s, good), (stock - boughtToday(s, seller, good, today)).coerceAtLeast(0))

    /** Why [o] can't be bought now, or null. */
    fun buyBlocker(s: GameState, o: Offer): String? {
        val short = o.price.mapValues { (r, n) -> n - s.res(r) }.filterValues { it > 0 }
        return when {
            o.left <= 0 -> bi("chest.soldOut")
            short.isNotEmpty() -> "${bi("engine.need")} ${costText(short)}"
            else -> null
        }
    }

    /** Jan buys one [good] from [seller] today; [s] unchanged when it isn't offered, is sold out, or they can't pay. */
    fun buy(s: GameState, seller: String, good: String, today: LocalDate, now: Long): GameState {
        val o = stalls(s, today).firstOrNull { it.id == seller }?.offers?.firstOrNull { it.good.id == good } ?: return s
        if (buyBlocker(s, o) != null) return s
        val day = today.toString()
        val counts = if (s.boughtOn == day) s.bought else emptyMap()
        val key = "$seller/$good"
        val paid = add(debit(s, o.price).first, good, 1)
        return paid.copy(bought = counts + (key to (counts[key] ?: 0) + 1), boughtOn = day)
            .logged(now, o.good.name.emoji to Cultures.current.chest.lines.bought.bi("good" to o.good.name.sl, "price" to costText(o.price)))
    }

    /** Sells one [good]; returns the new state and what it brought (empty when it couldn't be sold). */
    fun sell(s: GameState, good: String, now: Long): Pair<GameState, Map<Res, Int>> {
        if (count(s, good) <= 0) return s to emptyMap()
        val (r, n) = price(s, good) ?: return s to emptyMap()
        val g = Catalog.goods.getValue(good)
        val (paid, got) = credit(add(s, good, -1), mapOf(r to n))
        return paid.logged(now, (market(s)?.emoji ?: "🧳") to Cultures.current.chest.lines.sold.bi("good" to g.name.sl, "got" to n, "res" to r.emoji)) to got
    }

    // --- the smith forges tools -----------------------------------------------------------------

    /** The good the smith takes as his pay: the one Jan has most of (the cheaper on a tie; a rare one only when there's nothing else). */
    fun smithsPay(s: GameState): GoodSpec? = s.chest.goods.filterValues { it > 0 }.keys.mapNotNull { Catalog.goods[it] }
        .sortedWith(compareBy<GoodSpec> { it.rare }.thenByDescending { count(s, it.id) }.thenBy { it.value }.thenBy { it.id }).firstOrNull()

    /** Forging tool [giver]'s once more at the smithy; null when there is no such tool, it isn't of metal, or it can't get better. */
    fun forgeOption(s: GameState, giver: String): ForgeOption? {
        val t = s.chest.tools[giver] ?: return null
        val line = Catalog.tools[giver]?.takeIf { it.forge } ?: return null
        if (t.forged >= Catalog.MAX_FORGED) return null
        val step = t.forged + 1
        val cost = Catalog.forgeCost(step)
        val pay = smithsPay(s)
        val short = cost.mapValues { (r, n) -> n - s.res(r) }.filterValues { it > 0 }
        val age = Catalog.forgeAge(step)
        val smithy = s.buildings.any { it.type == BuildingType.SMITHY && !it.damaged }
        val smith = Cultures.current.chest.smith
        val here = s.residents.isEmpty() || s.residents.any { it.id == smith.id }
        val reason = when {
            !smithy -> bi("chest.needsSmithy")
            !here -> bi("chest.smithAway", "smith" to smith.title)
            s.age < age -> bi("engine.needsAge", "sl" to age.names, "en" to age.names)
            pay == null -> bi("chest.smithWantsPay", "smith" to smith.name)
            short.isNotEmpty() -> "${bi("engine.need")} ${costText(short)}"
            else -> null
        }
        val after = line.effect.times(power(t.copy(forged = step)))
        return ForgeOption(giver, step, cost, pay, reason, effectText(after))
    }

    /** Tone forges tool [giver]'s: it gets stronger by half its first strength. [s] unchanged when it can't. */
    fun forge(s: GameState, giver: String, now: Long): GameState {
        val o = forgeOption(s, giver)?.takeIf { it.available } ?: return s
        val (paid, _) = debit(s, o.cost)
        val t = s.chest.tools.getValue(giver)
        val item = Catalog.tools.getValue(giver).name(t.tier)
        val forged = add(paid, o.good!!.id, -1)
        val line = Cultures.current.chest.lines.forged
            .bi("smith" to Cultures.current.chest.smith.title, "item" to item.theText, "stars" to stars(o.step), "effect" to o.effect)
        return forged.copy(chest = forged.chest.copy(tools = forged.chest.tools + (giver to t.copy(forged = o.step))))
            .logged(now, "⚒️" to line)
    }

    /** "★", "★★", "★★★": a tool forged [forged] times. */
    fun stars(forged: Int): String = "★".repeat(forged.coerceIn(0, Catalog.MAX_FORGED) + 1)

    // --- the village feast ------------------------------------------------------------------------

    /** The treats a feast serves: the food goods Jan has most of (the cheaper on a tie), [Catalog.FEAST_GOODS] of them. */
    fun feastGoods(s: GameState): List<GoodSpec> = s.chest.goods.flatMap { (id, n) -> List(n.coerceIn(0, Catalog.FEAST_GOODS)) { id } }
        .mapNotNull { Catalog.goods[it]?.takeIf { g -> g.food } }
        .sortedWith(compareByDescending<GoodSpec> { count(s, it.id) }.thenBy { it.value }.thenBy { it.id })
        .take(Catalog.FEAST_GOODS)

    /** The day the next feast can be held; null when now. */
    fun nextFeast(s: GameState, today: LocalDate): LocalDate? =
        isoDay(s.lastFeast)?.plusDays(Catalog.FEAST_EVERY_DAYS)?.takeIf { it.isAfter(today) }

    /** A feast under the linden [today]: what it costs and why not (null when it can be held); null before Zaselek. */
    fun feastOption(s: GameState, today: LocalDate): FeastOption? {
        if (s.age < Age.ZASELEK) return null
        val cost = Catalog.feastCost(s.age)
        val goods = feastGoods(s)
        val short = cost.mapValues { (r, n) -> n - s.res(r) }.filterValues { it > 0 }
        val next = nextFeast(s, today)
        val reason = when {
            s.buildings.none { it.type == BuildingType.LIPA && !it.damaged } -> bi("chest.needsLinden")
            next != null -> bi("chest.nextFeast", "date" to Dates.short(next))
            short.isNotEmpty() -> "${bi("engine.need")} ${costText(short)}"
            else -> null
        }
        return FeastOption(cost, goods, reason, Catalog.FEAST_POINTS + goods.size)
    }

    /**
     * Hosts a feast under the linden: pays its cost and serves up to [Catalog.FEAST_GOODS] treats from the chest;
     * morale +[Catalog.FEAST_MORALE] (up to 100), and everyone who lives here grows closer: +[Catalog.FEAST_POINTS] ♥,
     * +1 more for each treat served (a level reached brings its gift). [s] unchanged when it can't be held.
     */
    fun feast(s: GameState, today: LocalDate, now: Long): GameState {
        val o = feastOption(s, today)?.takeIf { it.available } ?: return s
        var st = debit(s, o.cost).first
        for (g in o.goods) st = add(st, g.id, -1)
        val served = o.goods.joinToString("") { it.name.emoji }
        st = st.copy(morale = (st.morale + Catalog.FEAST_MORALE).coerceAtMost(100), lastFeast = today.toString())
            .logged(now, "🎪" to Cultures.current.chest.lines.feast.bi("served" to served).trimEnd())
        for (r in s.residents) st = Bonds.add(st, r.id, o.points, today, now = now)
        return st
    }
}

/**
 * A village feast: what it costs, the treats served (the chest's, if any), the ♥ everyone who lives here gets, and
 * why not (null when it can be held).
 */
data class FeastOption(val cost: Map<Res, Int>, val goods: List<GoodSpec>, val reason: String?, val points: Int) {
    val available: Boolean get() = reason == null
}
