package si.lanisce.lani.ui.towns

import si.lanisce.lani.data.Bundle
import si.lanisce.lani.data.GuestEntry
import si.lanisce.lani.data.TownActs
import si.lanisce.lani.game.Chest
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Guests
import si.lanisce.lani.game.ItemName
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.bi

/**
 * The things to do on a visit, as the screens pick them (VisitActs.kt): the market's choices (what the learner wants of
 * the town's wares, what they offer of their own), whether a haggle can start, and a guest's visit as a line. Pure.
 */
object VisitLogic {
    /** A resource is offered this many at a time, up to [MAX_RES]. */
    const val RES_STEP = 5
    const val MAX_RES = 100
    /** At most this many goods of one kind are offered. */
    const val MAX_EACH = 10
    /** A trade takes at most this many kinds of the town's goods (and [si.lanisce.lani.data.TownMarket]'s at most 3 of each). */
    const val MAX_WANT = 3

    /** The market's wares less what a trade took. */
    fun left(wares: Map<String, Int>, took: Bundle): Map<String, Int> =
        wares.mapValues { (id, n) -> n - (took.goods[id] ?: 0) }.filterValues { it > 0 }

    /** [m] with [id] changed by [by], within 0..[max]; none of it leaves it out. */
    fun step(m: Map<String, Int>, id: String, by: Int, max: Int): Map<String, Int> {
        val n = ((m[id] ?: 0) + by).coerceIn(0, max.coerceAtLeast(0))
        return if (n == 0) m - id else m + (id to n)
    }

    /** What the learner can offer: the goods in their chest (of their own culture) and the resources in their stores. */
    fun offerable(s: GameState): Pair<Map<String, Int>, Map<Res, Int>> =
        s.chest.goods.filter { (id, n) -> n > 0 && id in Cultures.home.goods } to Res.entries.associateWith { s.res(it) }.filterValues { it > 0 }

    /**
     * Whether a haggle over [give] for [get] can start: something of the town's wanted (at most [MAX_WANT] kinds, no more
     * than it spares), something offered that the learner has, and worth at least [share] of it ([Guests.fair]: half,
     * less between friends: the market's rate, [si.lanisce.lani.data.MarketRates]).
     */
    fun canHaggle(s: GameState?, give: Bundle, get: Bundle, wares: Map<String, Int>, share: Double = 0.5): Boolean =
        s != null && !give.empty && !get.empty && get.goods.size <= MAX_WANT && get.goods.all { (id, n) -> n <= (wares[id] ?: 0) } &&
            Guests.has(s, give) && Guests.fair(give, get, share)

    /** Why a haggle can't start yet, "target · base"; null when it can. */
    fun haggleHint(s: GameState?, give: Bundle, get: Bundle, wares: Map<String, Int>, share: Double = 0.5): String? = when {
        get.empty -> "👉 ${bi("visit.pickWhatYouWant")}"
        give.empty -> "👉 ${bi("visit.pickWhatYouOffer")}"
        s == null || !Guests.has(s, give) -> "🧺 ${bi("visit.youDontHaveThat")}"
        !Guests.fair(give, get, share) -> "⚖️ ${bi("visit.tooLittle")}"
        !canHaggle(s, give, get, wares, share) -> "🏪 ${bi("visit.notForSale")}"
        else -> null
    }

    /** A good of [culture] by its id, as the chest shows it; null when that culture doesn't have it. */
    fun good(culture: String, id: String): ItemName? = runCatching { Cultures.load(culture) }.getOrNull()?.goods?.get(id)?.name

    /** The good the village keeps when [culture]'s [id] comes into its chest ([Guests.nativeGood]). */
    fun nativeName(culture: String, id: String): ItemName? = Guests.nativeGood(culture, id)?.let { Cultures.home.goods[it]?.name }

    /** A guest's visit as a line for the Friends screen: who, and what they brought (a message stays apart, plain). */
    fun guestLine(e: GuestEntry): String {
        val who = e.from.learner.ifBlank { e.from.name }
        return when (e.kind) {
            TownActs.HELP -> "🤝 ${bi("guests.helped", "who" to who, "giver" to e.help?.giver.orEmpty(), "n" to (e.help?.amount ?: 0))}"
            TownActs.GIFT -> e.gift?.good?.let { g -> nativeName(g.culture, g.id) }?.let { "🎁 ${bi("guests.gift", "who" to who, "item" to "${it.emoji} ${it.sl}")}" }
                ?: "💌 ${bi("guests.message", "who" to who)}"
            TownActs.TRADE -> "⚖️ ${bi("guests.traded", "who" to who, "got" to Guests.text(e.trade?.got ?: Bundle()), "gave" to Guests.text(e.trade?.gave ?: Bundle()))}"
            TownActs.AID -> "🤝 ${bi("friendship.aidFrom", "who" to who)}"
            else -> "🧳 $who"
        }
    }

    /** How many of [id] the learner has: for the gift's choice. */
    fun have(s: GameState?, id: String): Int = s?.let { Chest.count(it, id) } ?: 0
}
