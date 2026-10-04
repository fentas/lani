package si.lanisce.lani.game

import si.lanisce.lani.data.FriendshipList
import si.lanisce.lani.data.Friendships
import si.lanisce.lani.data.Mover
import si.lanisce.lani.data.TownActs
import si.lanisce.lani.data.FriendTown
import si.lanisce.lani.data.TownMove
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.game.villagers.VillagerLines
import si.lanisce.lani.l10n.Dates
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Localized
import si.lanisce.lani.l10n.bi
import java.time.LocalDate

/**
 * Friendship between towns (plan 2, §3.3; companion/README.md, "Friendship between towns"): what it brings the village.
 * The towns' bridges agree on the level (data/Friendship.kt); here, pure like the engine and each applied once (its key
 * in [GameState.guestbook]):
 * - help against trouble: a friend's town sent it ([aidArrived]: the event's damage is less, or, over already, the
 *   supplies come into the stores), or this village sent it ([aided]: it costs [AID_COST]);
 * - the shared feast ("skupna veselica"): both towns held a feast within a few days ([sharedFeast]: morale and 🤝);
 * - people moving: someone of a friend's town moved in ([moves]: a resident of their culture, who speaks their language
 *   with the learner now and then, [bilingual]), or someone of this village's culture moved there (a line).
 * The chronicle speaks the learner's own pair, also when a visit's language is on screen.
 */
object TownFriendship {
    /** At most this many friends' help counts against one event. */
    const val MAX_AID = 3
    /** What sending help costs: supplies for the friend's village. */
    val AID_COST: Map<Res, Int> = mapOf(Res.FOOD to 10, Res.WOOD to 10)
    /** What a shared feast brings each village. */
    const val FEAST_MORALE = 10
    const val FEAST_HELP = 2

    /** The levels' names: neutral words for what two towns are to each other (acquaintance … twin towns). */
    private val LEVEL_KEYS = listOf("friendship.level0", "friendship.level1", "friendship.level2", "friendship.level3", "friendship.level4")
    /** What each level brings (from 1). */
    private val UNLOCK_KEYS = listOf("", "friendship.unlock1", "friendship.unlock2", "friendship.unlock3", "friendship.unlock4")

    private fun levelKey(level: Int) = LEVEL_KEYS[level.coerceIn(0, LEVEL_KEYS.lastIndex)]

    /** "Sosedstvo · Good neighbours": level [level]'s name (0 … 4). */
    fun name(level: Int): String = bi(levelKey(level))

    /** Level [level]'s name as a message's argument: in each language of the message ("Sosedstvo" … "Good neighbours"). */
    fun levelName(level: Int): Localized = Localized { lang -> L10n.text(lang, levelKey(level)) }

    /** What reaching level [level] (1 … 4) brings, as a message's argument. */
    fun unlocks(level: Int): Localized = Localized { lang -> L10n.text(lang, UNLOCK_KEYS[level.coerceIn(1, UNLOCK_KEYS.lastIndex)]) }

    /** How much of what a lost event takes it still takes, with [aid] friends' help: all, a half, a third, a quarter. */
    fun damageFactor(aid: Int): Float = 1f / (1 + aid.coerceIn(0, MAX_AID))

    /** Whether the village has the supplies to send help. */
    fun canAid(s: GameState): Boolean = AID_COST.all { (r, n) -> s.res(r) >= n }

    /** "10 🌾, 10 🪵": what sending help costs. */
    fun costText(): String = AID_COST.entries.joinToString(", ") { (r, n) -> "$n ${r.emoji}" }

    private fun own(key: String, vararg args: Pair<String, Any?>): String = L10n.label(L10n.ownPair, key, args.toMap())

    private fun mark(s: GameState, key: String): GameState = s.copy(guestbook = (s.guestbook - key + key).takeLast(Guests.KEEP))

    private fun tie(s: GameState, town: String, name: String, today: LocalDate, f: (TownTies) -> TownTies): GameState {
        val t = f(s.towns[town] ?: TownTies()).copy(name = name.ifBlank { s.towns[town]?.name.orEmpty() }, last = today.toString())
        return s.copy(towns = s.towns + (town to t))
    }

    // --- help against trouble --------------------------------------------------------------------------------------------

    /**
     * A friend's town ([who]: its learner) sent help against event [event]: while it is on, it has one helper more (up to
     * [MAX_AID]; a lost event then takes less, [damageFactor]); over already, the supplies they sent come into the stores.
     * Returns the village and its chronicle line.
     */
    fun aidArrived(s: GameState, event: String, who: String): Pair<GameState, Pair<String, String>> {
        val e = s.event
        if (e != null && e.id == event) {
            val n = s.copy(event = e.copy(aid = (e.aid + 1).coerceAtMost(MAX_AID), helpers = (e.helpers - who + who).takeLast(MAX_AID)))
            return n to ("🤝" to own("friendship.aidArrived", "who" to who, "event" to e.kind.names))
        }
        return credit(s, AID_COST).first to ("🤝" to own("friendship.aidLate", "who" to who, "got" to costText()))
    }

    /** This village sent help to town [town] ([name]), answered [key]: it costs [AID_COST] (never below none), once. */
    fun aided(s: GameState, key: String, town: String, name: String, today: LocalDate, now: Long): GameState {
        if (key in s.guestbook) return s
        var st = debit(s, AID_COST).first
        st = tie(st, town, name, today) { it.copy(aid = it.aid + 1) }
        st = st.logged(now, "🤝" to own("friendship.youAided", "town" to name))
        return mark(st, key)
    }

    // --- the shared feast ------------------------------------------------------------------------------------------------

    /** A shared feast with town [town] ([name]) ([key]: the bridge's, of the two feasts' days): morale and 🤝, once. */
    fun sharedFeast(s: GameState, key: String, town: String, name: String, today: LocalDate, now: Long): Pair<GameState, Pair<String, String>?> {
        if (key in s.guestbook) return s to null
        var st = GameEngine.helped(s.copy(morale = (s.morale + FEAST_MORALE).coerceAtMost(100)), FEAST_HELP)
        st = tie(st, town, name, today) { it.copy(feasts = it.feasts + 1) }
        val line = "🎉" to own("friendship.sharedFeast", "town" to name, "morale" to FEAST_MORALE, "help" to FEAST_HELP)
        return mark(st.logged(now, line), key) to line
    }

    // --- people moving ---------------------------------------------------------------------------------------------------

    /**
     * Moves done with town [town] ([name]), once each ("move:<its key>"): someone of theirs moved in (a resident of their
     * culture and language, one villager more; none when the village is full) or someone of this village's culture moved
     * there (a line). Returns the village, the chronicle lines and who moved in.
     */
    fun moves(s: GameState, town: String, name: String, list: List<TownMove>, today: LocalDate, now: Long): Triple<GameState, List<Pair<String, String>>, List<Resident>> {
        var st = s
        val lines = ArrayList<Pair<String, String>>()
        val arrived = ArrayList<Resident>()
        for (m in list.filter { it.done }) {
            val key = "move:${m.key}"
            if (key in st.guestbook) continue
            val p = m.person
            if (p != null) {
                val g = if (p.voice == "male") "m" else "f"
                val line = if (m.dir == Friendships.IN) {
                    if (st.residents.none { it.id == p.id } && st.residents.size < Residents.MAX_RESIDENTS) {
                        val r = resident(p, today)
                        st = st.copy(residents = st.residents + r, villagers = (st.villagers + 1).coerceAtMost(Residents.MAX_RESIDENTS))
                        arrived += r
                        p.emoji to own("friendship.movedIn", "name" to p.name, "town" to name, "g" to g)
                    } else "🏠" to own("friendship.noRoom", "name" to p.name, "town" to name)
                } else "👋" to own("friendship.movedOut", "name" to p.name, "town" to name, "g" to g)
                st = tie(st, town, name, today) { it.copy(moves = it.moves + 1) }.logged(now, line)
                lines += line
            }
            st = mark(st, key)
        }
        return Triple(st, lines, arrived)
    }

    /** Who moved in, as a resident: their trade in their language with the learner's base ("Boscaiola · Woodcutter"). */
    fun resident(p: Mover, today: LocalDate, pair: LangPair = L10n.ownPair): Resident {
        val theirs = Lang.of(p.language) ?: pair.target
        return Resident(
            id = p.id,
            since = today.toString(),
            name = p.name,
            emoji = p.emoji,
            art = p.art.takeIf { it == "woman" || it == "man" } ?: if (p.voice == "male") "man" else "woman",
            voice = if (p.voice == "male") "male" else "female",
            role = p.role.takeIf { it.isNotEmpty() }?.let { TownActs.label(it, LangPair(theirs, pair.base)) },
            family = p.family.ifBlank { null },
            culture = p.culture.ifBlank { null },
            language = p.language.ifBlank { null },
            from = p.from.name.ifBlank { null },
        )
    }

    /**
     * The lines of someone who moved in from another culture: the village's plain lines ([home], in its language) and
     * their culture's ([culture]'s people.json) in their [language], with the learner's base translation where the line
     * has it (else English): they speak their language with the learner now and then. [home] alone when the app doesn't
     * have their culture.
     */
    fun bilingual(home: VillagerLines, culture: String, language: String, pair: LangPair = L10n.ownPair): VillagerLines {
        val theirs = runCatching { Cultures.load(culture).people.lines.adult }.getOrNull() ?: return home
        fun keep(l: VillagerLine): VillagerLine? {
            val said = l.by[language] ?: return null
            val by = linkedMapOf(language to said)
            val base = pair.base.code
            if (base != language && base != pair.target.code) l.by[base]?.let { by[base] = it }
            val en = Lang.EN.code
            if (base !in by && base != language && en != language && en != pair.target.code) l.by[en]?.let { by[en] = it }
            return VillagerLine(by, l.level, l.times)
        }
        fun mix(a: List<VillagerLine>, b: List<VillagerLine>) = a + b.mapNotNull(::keep)
        return VillagerLines(
            greet = mix(home.greet, theirs.greet),
            thanks = mix(home.thanks, theirs.thanks),
            remember = mix(home.remember, theirs.remember),
            idle = mix(home.idle, theirs.idle),
            cheer = mix(home.cheer, theirs.cheer),
            comfort = mix(home.comfort, theirs.comfort),
            listen = mix(home.listen, theirs.listen),
            bye = mix(home.bye, theirs.bye),
        )
    }

    // --- all of it, from the bridge's list ----------------------------------------------------------------------------------

    /** What [apply] did: the village, the chronicle lines to show, who moved in (the tutor hears of them). */
    data class Applied(val state: GameState, val news: List<Pair<String, String>>, val arrived: List<Resident>, val feasts: Int)

    /** The friendships' news ([list]: GET /towns/friendship) applied to the village: shared feasts and moves, once each. */
    fun apply(s: GameState, list: FriendshipList, today: LocalDate, now: Long): Applied {
        var st = s
        val news = ArrayList<Pair<String, String>>()
        val arrived = ArrayList<Resident>()
        var feasts = 0
        for (t in list.towns) {
            t.feast.shared?.let { f ->
                val (n, line) = sharedFeast(st, f.key, t.id, t.name, today, now)
                st = n
                line?.let { news += it; feasts++ }
            }
            val (n, lines, people) = moves(st, t.id, t.name, t.moves.list, today, now)
            st = n
            news += lines
            arrived += people
        }
        return Applied(st, news, arrived, feasts)
    }

    /**
     * What a friend's town asks of this learner now, each with its key (shown once): its trouble while help can be sent,
     * a move it offered, its feast to share (the day to hold one by). "target · base".
     */
    fun alerts(list: FriendshipList, s: GameState?): List<Pair<String, String>> = list.towns.flatMap { t -> alerts(t, s) }

    fun alerts(t: FriendTown, s: GameState?): List<Pair<String, String>> = buildList {
        t.event?.takeIf { it.can && !it.aided }?.let { e ->
            val kind = EventKind.entries.firstOrNull { it.name == e.kind }
            add("trouble:${t.id}:${e.id}" to "${kind?.emoji ?: "⚠️"} ${bi("friendship.troubleAt", "event" to (kind?.names ?: e.kind), "town" to t.name)}")
        }
        t.moves.list.filter { it.open && it.by == "them" }.forEach { m ->
            add("offer:${m.key}" to "🧳 ${bi("friendship.offerFrom", "town" to t.name)}")
        }
        val until = t.feast.openUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (until != null && s != null) add("feast:${t.id}:${t.feast.theirs}" to "🎪 ${bi("friendship.feastOpen", "town" to t.name, "date" to Dates.short(until))}")
    }
}
