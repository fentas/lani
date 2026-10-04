package si.lanisce.lani.ui.towns

import si.lanisce.lani.data.FriendTown
import si.lanisce.lani.data.Friendships
import si.lanisce.lani.data.MoveCan
import si.lanisce.lani.data.Mover
import si.lanisce.lani.data.TownRow
import si.lanisce.lani.data.TownSelf
import si.lanisce.lani.data.Towns
import si.lanisce.lani.data.TownsList
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.FeastOption
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.TownFriendship
import si.lanisce.lani.l10n.Dates
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.Localized
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.game.label
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** One linked town on "Prijatelji · Friends". */
data class FriendRow(
    val id: String,
    /** "🇮🇹 Il mio villaggio" */
    val title: String,
    /** "Luka · 🏘️ Vas · Village · 👥 5" */
    val subtitle: String,
    /** A small picture of the town: its age and its buildings ("🏘️ 🛖×3 🌾×2 ⛪"); empty while it can't be seen. */
    val thumbnail: String,
    /** Why its state can't be seen now ("📡 …"), or null. */
    val status: String?,
    /** Whether "Obišči · Visit" shows: not for a town that no longer knows this one. */
    val canVisit: Boolean = true,
)

/** What "Prijatelji · Friends" shows, from GET /towns (data/Towns.kt). Pure. */
object FriendsLogic {
    /** The rows, in the order the bridge lists the towns (the order they were linked). */
    fun rows(list: TownsList, ageName: (Age) -> String = { it.label() }): List<FriendRow> = list.towns.map { row(it, ageName) }

    fun row(t: TownRow, ageName: (Age) -> String = { it.label() }): FriendRow {
        val age = t.age?.let { a -> Age.entries.firstOrNull { it.name == a } }
        val who = t.learner.ifBlank { "?" }
        val subtitle = listOfNotNull(
            who,
            age?.let { "${it.emoji} ${ageName(it)}" },
            t.villagers?.takeIf { t.reachable && it > 0 }?.let { "👥 $it" },
        ).joinToString(" · ")
        return FriendRow(
            id = t.id,
            title = "${Towns.flag(t.language)} ${t.name.ifBlank { "?" }}".trim(),
            subtitle = subtitle,
            thumbnail = if (t.reachable) thumbnail(age, t.buildings) else "",
            status = status(t),
            canVisit = t.status != "refused",
        )
    }

    /**
     * The town in emojis: its age, then its buildings in the game's order, each type once with how many there are
     * ("🛖×3"), at most [max] types.
     */
    fun thumbnail(age: Age?, buildings: Map<String, Int>, max: Int = 6): String {
        val parts = BuildingType.entries.mapNotNull { b -> buildings[b.name]?.takeIf { it > 0 }?.let { n -> if (n > 1) "${b.emoji}×$n" else b.emoji } }.take(max)
        return listOfNotNull(age?.emoji, parts.takeIf { it.isNotEmpty() }?.joinToString(" ")).joinToString("  ")
    }

    /** Why a town's state can't be seen now; null when it can. */
    fun status(t: TownRow): String? = when (t.status) {
        "ok" -> null
        "refused" -> "🔒 ${bi("towns.noLongerKnowsYou")}"
        "unreachable" -> "📡 ${bi("towns.cantBeReached")}"
        else -> "⚠️ ${bi("towns.answerNotUnderstood")}"
    }

    /** "Moja vas · Jan 🇸🇮": this learner's own town. */
    fun selfLine(s: TownSelf): String = "${s.name.ifBlank { "?" }} · ${s.learner} ${Towns.flag(s.language)}".trim()

    /** Whether to offer "Povabi · Invite" and "Sprejmi · Accept": not for a child, nor before the bridge answered. */
    fun canLink(list: TownsList?): Boolean = list?.self?.canInvite == true && list.self.child.not()

    /** Minutes an invitation has left (a started minute counts), from its expiry (ISO) and [now]; 0 once it ran out, null when unknown. */
    fun minutesLeft(expiresAt: String, now: Instant = Instant.now()): Long? =
        runCatching { Duration.between(now, Instant.parse(expiresAt)).seconds.let { if (it <= 0) 0 else (it + 59) / 60 } }.getOrNull()

    // --- friendship between towns (data/Friendship.kt, game/TownFriendship.kt) ----------------------------------------

    /** "💞 Sosedstvo · Good neighbours · 16/24": the level both towns agree on, and the points toward the next. */
    fun levelLine(f: FriendTown): String =
        "${HEARTS.getOrElse(f.level) { "💞" }} ${TownFriendship.name(f.level)} · ${f.points}${f.next?.let { "/${it.points}" }.orEmpty()}"

    private val HEARTS = listOf("🤍", "💛", "🧡", "💞", "💖")

    /** 0..1 of the way from the level's points to the next level's (1 at the top). */
    fun progress(f: FriendTown): Float {
        val next = f.next ?: return 1f
        val from = Friendships.LEVELS.getOrElse(f.level) { 0 }
        return ((f.points - from).toFloat() / (next.points - from).coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    /** What the next level brings ("Naslednja: Prijateljstvo · Friendship: …"), or that this is the top. */
    fun nextLine(f: FriendTown): String = f.next?.let { n ->
        "✨ ${bi("friendship.next", "level" to TownFriendship.levelName(n.level), "unlocks" to TownFriendship.unlocks(n.level))}"
    } ?: "🏆 ${bi("friendship.top")}"

    /** A language's name as a message's argument: in each language of the message ("italijanščino" … "Italian"). */
    private fun languageName(code: String): Localized = LANGUAGE_KEYS[code]?.let { key -> Localized { lang -> L10n.text(lang, key) } } ?: Localized { code.uppercase() }

    private val LANGUAGE_KEYS = mapOf("sl" to "languages.sl", "it" to "languages.it", "en" to "languages.en", "de" to "languages.de")

    /**
     * What a friend's card shows besides the level, each with what can be done about it now (never a wall): their
     * trouble and help to send, the feast to share, the moves offered and done, and whether someone may move. [s]: this
     * village; [feast]: whether it can hold a feast now (Chest.feastOption); [self]: this learner's town (a child's?).
     */
    fun notices(f: FriendTown, s: GameState?, feast: FeastOption?, child: Boolean): List<FriendNotice> = buildList {
        // trouble in the friend's town: help (from neighbours on), what it costs
        f.event?.let { e ->
            val kind = EventKind.entries.firstOrNull { it.name == e.kind }
            val text = "${kind?.emoji ?: "⚠️"} ${bi("friendship.troubleAt", "event" to (kind?.names ?: e.kind), "town" to f.name)}"
            when {
                e.aided -> add(FriendNotice("$text\n✔ ${bi("friendship.helpSent")}"))
                !e.can -> add(FriendNotice("$text\n💞 ${bi("friendship.aidAt", "level" to TownFriendship.levelName(Friendships.AID_LEVEL))}"))
                else -> {
                    val can = s != null && TownFriendship.canAid(s)
                    val why = if (can) "" else "\n🧺 ${bi("friendship.aidNeeds", "cost" to TownFriendship.costText())}"
                    add(FriendNotice(text + why, FriendAction.Aid(f.id), "🤝 ${bi("friendship.sendHelp")} (−${TownFriendship.costText()})", enabled = can))
                }
            }
        }
        // the feasts
        if (f.feast.shared != null) add(FriendNotice("🎉 ${bi("friendship.sharedFeastWith", "town" to f.name)}"))
        f.feast.openUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { until ->
            val text = "🎪 ${bi("friendship.feastOpen", "town" to f.name, "date" to Dates.short(until))}"
            val why = when {
                feast == null -> "\n🌳 ${bi("friendship.feastNotYet")}"
                !feast.available -> "\n${feast.reason}"
                else -> ""
            }
            add(FriendNotice(text + why, FriendAction.Feast, "🎪 ${bi("friendship.holdFeast")}", enabled = feast?.available == true))
        }
        // the moves: offered to this town, offered by it, the last one done
        for (m in f.moves.list.filter { it.open }) {
            val p = m.person
            val who = p?.let { "${it.emoji} ${it.name}" } ?: "🧳"
            if (m.by == "them") {
                val text = if (m.dir == Friendships.IN) bi("friendship.offerIn", "who" to who, "town" to f.name, "role" to roleOf(p))
                else bi("friendship.offerOut", "who" to who, "town" to f.name)
                add(
                    FriendNotice(
                        "🧳 $text", FriendAction.Answer(f.id, m.id, true), "🏡 ${bi("friendship.sayYes")}", enabled = !child,
                        second = FriendAction.Answer(f.id, m.id, false), secondLabel = bi("friendship.notNow"),
                    ),
                )
            } else {
                add(FriendNotice("⏳ ${bi("friendship.waitingFor", "town" to f.name, "who" to who)}", FriendAction.Answer(f.id, m.id, false), bi("friendship.withdraw")))
            }
        }
        f.moves.list.filter { it.done && it.person != null }.maxByOrNull { it.doneAt ?: it.at }?.let { m ->
            val p = m.person!!
            add(FriendNotice(if (m.dir == Friendships.IN) "🏡 ${bi("friendship.movedHere", "who" to "${p.emoji} ${p.name}", "town" to f.name)}" else "👋 ${bi("friendship.movedThere", "who" to "${p.emoji} ${p.name}", "town" to f.name)}"))
        }
        // whether someone may move now (from close friends on), else what it waits for
        if (f.level >= Friendships.MOVE_LEVEL && f.moves.list.none { it.open }) {
            val into = f.moves.into
            val out = f.moves.out
            if (into.ok) add(FriendNotice("🏡 ${bi("friendship.canInvite", "town" to f.name)}", FriendAction.Offer(f.id, Friendships.IN), "🏡 ${bi("friendship.invite")}"))
            if (out.ok) add(FriendNotice("👋 ${bi("friendship.canSend", "town" to f.name)}", FriendAction.Offer(f.id, Friendships.OUT), "👋 ${bi("friendship.offer")}"))
            listOf(into, out).firstOrNull { !it.ok }?.let { no -> whyNot(f, no, child)?.let { add(FriendNotice(it)) } }
        }
    }

    /** Why a move can't be offered now, and what to do about it; null when there's nothing to say. */
    fun whyNot(f: FriendTown, no: MoveCan, child: Boolean = false): String? = when (no.why) {
        "skill" -> "📚 ${bi("friendship.moveSkill", "language" to languageName(f.moves.skill.language.ifBlank { f.language }), "need" to f.moves.skill.need, "level" to (no.level ?: f.moves.skill.level), "town" to f.name)}"
        "child" -> "👨‍👩‍👦 ${if (f.child && !child) bi("friendship.moveChildTown", "learner" to f.learner.ifBlank { f.name }) else bi("friendship.moveChild")}"
        "soon" -> no.until?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { "🗓️ ${bi("friendship.moveAfter", "date" to Dates.short(it))}" }
        else -> null
    }

    /** Someone's trade as they say it, in their language ("Boscaiola"), else in English. */
    private fun roleOf(p: Mover?): String = p?.let { it.role[it.language] ?: it.role[Lang.EN.code] }.orEmpty()
}

/** One thing on a friend's card: a line ("target · base"), and what can be done about it now ([action], [second]). */
data class FriendNotice(
    val text: String,
    val action: FriendAction? = null,
    val label: String = "",
    val enabled: Boolean = true,
    val second: FriendAction? = null,
    val secondLabel: String = "",
)

sealed interface FriendAction {
    /** Send help against the town's trouble. */
    data class Aid(val town: String) : FriendAction
    /** Hold a feast under the linden (to share the friend's). */
    data object Feast : FriendAction
    /** Offer a move: [dir] "in" (someone of theirs moves here) or "out". */
    data class Offer(val town: String, val dir: String) : FriendAction
    /** Say yes to a move ([yes]) or decline it (withdraw one's own). */
    data class Answer(val town: String, val move: String, val yes: Boolean) : FriendAction
}
