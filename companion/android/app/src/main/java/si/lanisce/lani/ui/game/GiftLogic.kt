package si.lanisce.lani.ui.game

import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.Chest
import si.lanisce.lani.game.Gift
import si.lanisce.lani.game.GoodSpec
import si.lanisce.lani.game.Inventory
import si.lanisce.lani.game.ItemName
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.villagers.FriendGain

// Pure helpers behind the gift reveal (GiftReveal.kt) and the chest's "new" dot (companion/GAME.md, "The chest").

/** Who a run's thank-you good is from, for its reveal: a villager ([emoji] [name]), or a festival ([festive]). */
data class GiftFrom(val emoji: String, val name: String, val festive: Boolean = false)

/**
 * Something that came into the chest, for its reveal: [item] ([count] of it), [from] whom or for which holiday, and
 * what it's for ([use]). [tool]: a tool, kept for good; else a good. [key]: its [ChestNews] key.
 */
data class Received(val key: String, val item: ItemName, val count: Int, val from: String?, val use: String, val tool: Boolean) {
    /** "Darilo: Potica · A gift: Potica", what TalkBack says as it opens. */
    val announce: String get() = bi("giftLogic.aGift", "item" to item.nameText) + if (count > 1) " ×$count" else ""
}

object GiftLogic {
    /**
     * What a finished run brought for the chest, in the order it's revealed: the good it was thanked with (a request
     * passed, a festival celebrated), then the gifts of a friendship that reached a level (a tool, or a small good).
     */
    fun of(r: Reward): List<Received> {
        val won = r.result?.takeIf { it.won }
        val thanks = won?.thanks?.let { g -> good(g, won.thanksCount.coerceAtLeast(1), thanksFrom(r.from)) }
        return listOfNotNull(thanks) + of(r.friend)
    }

    /** The gifts [f]'s friendship brought as it reached a level (a talk, a scene, a request). */
    fun of(f: FriendGain?): List<Received> = f?.let { friend -> friend.gifts.mapNotNull { gift(it, friend) } }.orEmpty()

    /** "👵 Babica Micka: V zahvalo · As thanks"; a festival's: "Za praznik · For the holiday: 🍷 Martinovo · St Martin's Day". */
    fun thanksFrom(from: GiftFrom?): String = when {
        from == null -> bi("villageLogic.asThanks")
        from.festive -> "${bi("villageLogic.forHoliday")}: ${from.emoji} ${from.name}"
        else -> "${from.emoji} ${from.name}: ${bi("villageLogic.asThanks")}"
    }

    /**
     * What a good is for, in one line: a rare one pleases everyone, a common one someone who likes it; one nobody
     * likes makes a feast better (food) or is sold.
     */
    fun use(g: GoodSpec): String = when {
        g.rare -> "✨ ${bi("chestSheet.everyoneLovesRareGood", "RARE_GIFT_POINTS" to Catalog.RARE_GIFT_POINTS)}"
        liked(g.id) -> "❤️ ${bi("chestSheet.giveSomeoneWhoLikes", "GIFT_POINTS" to Catalog.GIFT_POINTS)}"
        g.food -> "🎪 ${bi("chestSheet.treatsFromChestMake")}"
        else -> "🧳 ${bi("chestSheet.sellMerchantWhenHe")}"
    }

    private fun liked(good: String): Boolean = good in Catalog.defaultLikes || Catalog.likes.values.any { good in it }

    private fun good(g: GoodSpec, count: Int, from: String?) = Received(ChestNews.good(g.id), g.name, count, from, use(g), tool = false)

    private fun gift(gift: Gift, f: FriendGain): Received? {
        val from = "${f.emoji} ${f.name}: ${bi("giftLogic.forFriendship")}"
        if (gift.tool) {
            val use = gift.effect?.let(Chest::effectHelp).orEmpty()
            return Received(ChestNews.tool(gift.from), gift.item, 1, from, use, tool = true)
        }
        val g = gift.good?.let { Catalog.goods[it] } ?: return null
        return good(g, 1, from)
    }
}

/**
 * The chest's "new" dot: what came into it since it was last opened ([unseen]: "good:potica", "tool:luka"). Set as
 * things arrive ([after]), cleared when the chest opens ([opened]); a thing given away before it was seen isn't news.
 */
data class ChestNews(val unseen: Set<String> = emptySet()) {
    /** The chest went from [before] to [after]: what came in joins the news (nothing before the village is known). */
    fun after(before: Inventory?, after: Inventory): ChestNews {
        before ?: return this
        val came = arrived(before, after)
        return if (came.isEmpty() || unseen.containsAll(came)) this else ChestNews(unseen + came)
    }

    /** The chest was opened: everything in it is seen. */
    fun opened(): ChestNews = ChestNews()

    /** Whether the HUD's chest shows its dot: something new is still in [chest]. */
    fun shows(chest: Inventory): Boolean = unseen.any { it in keys(chest) }

    companion object {
        fun good(id: String) = "good:$id"
        fun tool(giver: String) = "tool:$giver"

        /** What came in between two states of the chest: more of a good, a new tool or its better one (not a forging). */
        fun arrived(before: Inventory, after: Inventory): Set<String> =
            after.goods.filter { (id, n) -> n > (before.goods[id] ?: 0) }.keys.map(::good).toSet() +
                after.tools.filter { (id, t) -> t.tier > (before.tools[id]?.tier ?: 0) }.keys.map(::tool)

        /** Everything in [chest], by key. */
        fun keys(chest: Inventory): Set<String> =
            chest.goods.filterValues { it > 0 }.keys.map(::good).toSet() + chest.tools.keys.map(::tool)
    }
}
