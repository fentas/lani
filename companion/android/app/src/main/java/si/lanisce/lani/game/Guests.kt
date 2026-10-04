package si.lanisce.lani.game

import si.lanisce.lani.data.Bundle
import si.lanisce.lani.data.GuestEntry
import si.lanisce.lani.data.TownActs
import si.lanisce.lani.game.culture.Culture
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import java.time.LocalDate

/**
 * Towns that visit each other (plan 2, §3; companion/README.md, "Things to do on a visit"): what guests brought this
 * village (the bridge's guest book: help, gifts, trades) and what its learner's visits did in other towns (a request
 * done, a gift left, a trade, someone met), applied to the village once each (their keys in [GameState.guestbook]), and
 * the ties with each town ([GameState.towns]). Pure, like the engine.
 *
 * Goods cross between cultures as the village's own good that looks the same (a jar of honey is a jar of honey: miele,
 * med), else one of the same price and kind ([nativeGood]). The chronicle speaks the learner's own pair, also when a
 * visit's language is on screen.
 */
object Guests {
    /** How many applied keys the village keeps: far more than a guest book shows at once. */
    const val KEEP = 300

    /** A guest's visit, as the host's app announces it: an emoji and a line. */
    data class News(val emoji: String, val text: String)

    fun applied(s: GameState, key: String): Boolean = key in s.guestbook

    private fun mark(s: GameState, key: String): GameState = s.copy(guestbook = (s.guestbook - key + key).takeLast(KEEP))

    /** A line in the learner's own pair ("target · base"), whatever is on screen. */
    private fun own(key: String, vararg args: Pair<String, Any?>): String = L10n.label(L10n.ownPair, key, args.toMap())

    private fun tie(s: GameState, town: String, name: String, today: LocalDate, f: (TownTies) -> TownTies): GameState {
        val t = f(s.towns[town] ?: TownTies()).copy(name = name.ifBlank { s.towns[town]?.name.orEmpty() }, last = today.toString())
        return s.copy(towns = s.towns + (town to t))
    }

    // --- goods between cultures --------------------------------------------------------------------------------------

    /**
     * Good [id] of culture [from] as a good of [home] (the village's own culture): itself in the same culture; else the
     * home's good with the same look (emoji), else of the same price and kind (food, rare), else of the same price, else
     * the home's thank-you default. Null for a good its culture doesn't have (or a culture the app doesn't bundle, and
     * no good of that id at home).
     */
    fun nativeGood(from: String, id: String, home: Culture = Cultures.home): String? {
        if (from == home.id || from.isBlank()) return id.takeIf { it in home.goods }
        val g = runCatching { Cultures.load(from) }.getOrNull()?.goods?.get(id) ?: return id.takeIf { it in home.goods }
        val mine = home.goods.values
        return mine.firstOrNull { it.name.emoji == g.name.emoji }?.id
            ?: mine.firstOrNull { it.value == g.value && it.food == g.food && it.rare == g.rare }?.id
            ?: mine.firstOrNull { it.value == g.value }?.id
            ?: home.chest.thanksDefault.takeIf { it in home.goods }
    }

    /** A bundle's goods as the village's own (summed where two become one). */
    fun nativeGoods(b: Bundle, home: Culture = Cultures.home): Map<String, Int> = buildMap {
        for ((id, n) in b.goods) if (n > 0) nativeGood(b.culture, id, home)?.let { put(it, (get(it) ?: 0) + n) }
    }

    private fun resOf(b: Bundle): Map<Res, Int> = b.res.mapNotNull { (k, n) -> Res.entries.firstOrNull { it.name == k }?.let { it to n } }.filter { it.second > 0 }.toMap()

    /** What a bundle is worth: its goods by their price (in their culture; a plain good when unknown), a resource 1 each. */
    fun value(b: Bundle): Int {
        val c = runCatching { Cultures.load(b.culture) }.getOrNull()
        return b.goods.entries.sumOf { (id, n) -> (c?.goods?.get(id)?.value ?: Catalog.GOOD_PRICES.getValue("plain")) * n.coerceAtLeast(0) } +
            b.res.values.sumOf { it.coerceAtLeast(0) }
    }

    /**
     * Whether the host takes [give] for [get]: worth at least [share] of it (half; less between friends, the market's
     * rate); the negotiation decides the rest.
     */
    fun fair(give: Bundle, get: Bundle, share: Double = 0.5): Boolean = !give.empty && !get.empty && value(give) >= share * value(get)

    /** Whether the village has all of [b] (its own culture's goods and its resources). */
    fun has(s: GameState, b: Bundle): Boolean =
        b.goods.all { (id, n) -> Chest.count(s, id) >= n } && resOf(b).all { (r, n) -> s.res(r) >= n }

    // --- the host: what guests brought ----------------------------------------------------------------------------------

    /**
     * The guest book's entries not applied yet, oldest first: help brings its 🤝, a gift its good (as the village's own)
     * and a trade what the guest gave (resources up to the stores) while what they took leaves the chest (never below
     * none). Each is written to the chronicle, counted in the ties with its town and marked applied. Returns the village
     * and what to announce.
     */
    fun apply(s: GameState, entries: List<GuestEntry>, today: LocalDate, now: Long): Pair<GameState, List<News>> {
        var st = s
        val news = ArrayList<News>()
        for (e in entries.sortedBy { it.at }) {
            if (e.key.isBlank() || applied(st, e.key)) continue
            val who = e.from.learner.ifBlank { e.from.name }
            val line: Pair<String, String>? = when (e.kind) {
                TownActs.HELP -> e.help?.let { h ->
                    st = GameEngine.helped(st, h.amount.coerceIn(0, 10))
                    "🤝" to own("guests.helped", "who" to who, "giver" to h.giver, "n" to h.amount)
                }
                TownActs.GIFT -> e.gift?.let { g ->
                    val good = g.good?.let { nativeGood(it.culture, it.id) }
                    if (good != null) st = Chest.add(st, good, 1)
                    val item = good?.let { Catalog.goods[it]?.name ?: Cultures.home.goods[it]?.name }
                    "🎁" to if (item != null) own("guests.gift", "who" to who, "item" to "${item.emoji} ${item.sl}") else own("guests.message", "who" to who)
                }
                TownActs.TRADE -> e.trade?.let { t ->
                    st = credit(st, resOf(t.got)).first
                    for ((id, n) in nativeGoods(t.got)) st = Chest.add(st, id, n)
                    for ((id, n) in t.gave.goods) st = Chest.add(st, id, -n)
                    "⚖️" to own("guests.traded", "who" to who, "got" to text(t.got), "gave" to text(t.gave))
                }
                // help against the village's trouble, from a friend's town (TownFriendship)
                TownActs.AID -> e.aid?.let { a ->
                    val (n, l) = TownFriendship.aidArrived(st, a.event, who)
                    st = n
                    l
                }
                else -> null
            }
            st = mark(st, e.key)
            if (line == null) continue
            st = tie(st, e.from.id, e.from.name, today) { it.copy(guests = it.guests + 1) }.logged(now, line)
            news += News(line.first, line.second)
            e.gift?.message?.takeIf { it.isNotBlank() }?.let { news += News("💌", "$who: $it") }
        }
        return st to news
    }

    /** "2 🍯, 20 🪵": a bundle in short, its goods by their look. */
    fun text(b: Bundle): String {
        val c = runCatching { Cultures.load(b.culture) }.getOrNull()
        val goods = b.goods.filterValues { it > 0 }.map { (id, n) -> "$n ${c?.goods?.get(id)?.name?.emoji ?: "🎁"}" }
        val res = resOf(b).map { (r, n) -> "$n ${r.emoji}" }
        return (goods + res).joinToString(", ")
    }

    // --- the visitor: what this village's visits did elsewhere ------------------------------------------------------------

    /**
     * Request [giver]'s done in town [town] ([name]), answered [key]: the giver's thank-you good ([thanks], a good of
     * [culture]) comes into the chest as the village's own, once. Returns the village and the good (null when none).
     */
    fun helped(s: GameState, key: String, town: String, name: String, giver: String, culture: String, thanks: String?, today: LocalDate, now: Long): Pair<GameState, String?> {
        if (applied(s, key)) return s to null
        val good = thanks?.let { nativeGood(culture, it) }
        var st = if (good != null) Chest.add(s, good, 1) else s
        st = tie(st, town, name, today) { it.copy(helped = it.helped + 1) }
        val item = good?.let { Cultures.home.goods[it]?.name }
        st = st.logged(now, "🤝" to own("guests.youHelped", "giver" to giver, "town" to name) + (item?.let { " · ${it.emoji}" }.orEmpty()))
        return mark(st, key) to good
    }

    /** A gift ([good], the village's own, or none: a message only) left in town [town], answered [key]: once. */
    fun gave(s: GameState, key: String, town: String, name: String, good: String?, today: LocalDate, now: Long): GameState {
        if (applied(s, key)) return s
        var st = if (good != null) Chest.add(s, good, -1) else s
        st = tie(st, town, name, today) { it.copy(gifts = it.gifts + 1) }
        st = st.logged(now, "🎁" to own("guests.youGave", "town" to name))
        return mark(st, key)
    }

    /**
     * A trade at town [town]'s market, answered [key]: [give] (the village's own goods and resources) leaves, [get] (the
     * town's goods) comes in as the village's own; once.
     */
    fun traded(s: GameState, key: String, town: String, name: String, give: Bundle, get: Bundle, today: LocalDate, now: Long): GameState {
        if (applied(s, key)) return s
        var st = debit(s, resOf(give)).first
        for ((id, n) in give.goods) st = Chest.add(st, id, -n)
        for ((id, n) in nativeGoods(get)) st = Chest.add(st, id, n)
        st = tie(st, town, name, today) { it.copy(trades = it.trades + 1) }
        st = st.logged(now, "⚖️" to own("guests.youTraded", "town" to name, "gave" to text(give), "got" to text(get)))
        return mark(st, key)
    }

    /** Villager [villager] ([who]) of town [town] was met on a visit: the first time, a line in the chronicle. */
    fun met(s: GameState, town: String, name: String, villager: String, who: String, today: LocalDate, now: Long): GameState {
        val before = s.towns[town]?.met.orEmpty()
        val st = tie(s, town, name, today) { it.copy(met = (it.met - villager + villager).takeLast(60)) }
        return if (villager in before) st else st.logged(now, "🧳" to own("guests.youMet", "who" to who, "town" to name))
    }
}
