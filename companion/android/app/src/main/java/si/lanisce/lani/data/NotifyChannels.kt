package si.lanisce.lani.data

import si.lanisce.lani.Screen
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget

/** NotificationManager's importances, as plain numbers for the pure code (IMPORTANCE_NONE, _LOW, _DEFAULT). */
private const val NONE = 0
private const val LOW = 2
private const val DEFAULT = 3

/**
 * The app's notification channels (companion/README.md, "Notifications"). A channel's badge (the launcher's dot, One UI's
 * count) is fixed when Android creates it, so what is waiting for the learner ([badge]: a message, the village, school)
 * has its own channels apart from news and reminders. Each is named "[emoji] target · base" ([key]) in the learner's pair.
 * The road's getting ready keeps its own channel (road/RoadPrepService).
 */
enum class NotifyChannel(val id: String, val emoji: String, val key: String, val badge: Boolean, val importance: Int) {
    /** The tutor's answer, the family's challenge, a guest from a linked town, a town's question, a link between towns. */
    MESSAGES("messages", "💬", "notify.messages", true, DEFAULT),
    /** The village's event: wolves, a bear, a storm, a merchant, a festival. */
    VILLAGE("village", "🏘️", "notify.village", true, DEFAULT),
    /** The tutor's homework, the case trainer's assignment. */
    SCHOOL("school", "📝", "notify.school", true, DEFAULT),
    /** What the tutor published (a challenge, words, a reading, a grammar page, a scene, a story), a linked town's week: it waits, silently. */
    NEW("new", "✨", "notify.new", false, LOW),
    /** The evening's nudge: once a day at most, so it may ring. */
    REMINDERS("reminders", "🔔", "notify.reminders", false, DEFAULT),
    /** An update, a write the node didn't keep, a tool's approval the tutor waits for (minutes: it rings). */
    SYSTEM("system", "⚙️", "notify.system", false, DEFAULT),
}

/** The notifications' tags this one matches: [tag] itself, or ([prefix]) every tag starting with it. */
data class Seen(val tag: String, val prefix: Boolean = false) {
    fun matches(t: String?): Boolean = t != null && if (prefix) t.startsWith(tag) else t == tag
}

/**
 * A notification to post: its [channel], its [tag] (one notification per thread, person or thing: a new one with the same
 * tag replaces it, and the app takes it away once the learner has seen it there, [NotifyChannels.seen]), what it says, where
 * a tap leads; [replaces]: notifications of the same kind it takes the place of (an older update's).
 */
data class Notice(
    val channel: NotifyChannel, val tag: String, val title: String, val text: String, val link: DeepLink? = null, val replaces: Seen? = null,
)

/** The channels made, the old one let go, what the learner saw: the decisions, apart from Android's calls (data/Notifier). */
object NotifyChannels {
    /** The one channel every notification went to before (until 2026-10): let go once the new ones are made. */
    const val OLD = "tutor"

    /** The old channel as the learner left it: its importance ([NONE]: blocked) and its dot. */
    data class Old(val importance: Int, val badge: Boolean)

    /** A channel to make: its [importance] and its [badge]. */
    data class Spec(val channel: NotifyChannel, val importance: Int, val badge: Boolean)

    /**
     * The channels to make, given the old one ([old], null when it isn't there: a new phone, or made already). The learner's
     * settings of the old one carry over: blocked, every new one is made blocked (they turned the tutor's notifications
     * off: each kind can be turned on in the phone's settings, nothing rings that didn't); quieter (silent, minimised), no
     * new one is louder than it; without its dot, none has one.
     */
    fun specs(old: Old?): List<Spec> = NotifyChannel.entries.map { c ->
        when {
            old == null -> Spec(c, c.importance, c.badge)
            old.importance <= NONE -> Spec(c, NONE, c.badge && old.badge)
            else -> Spec(c, minOf(c.importance, old.importance), c.badge && old.badge)
        }
    }

    /** The old channel goes (its notifications with it) only once every new one exists ([existing]: the ids Android has). */
    fun deleteOld(oldThere: Boolean, existing: Set<String>): Boolean = oldThere && NotifyChannel.entries.all { it.id in existing }

    /** "💬 Sporočila · Messages": [c]'s name in the learner's own [pair] (not a visit's), as the phone's settings show it. */
    fun name(c: NotifyChannel, pair: LangPair = L10n.ownPair): String = "${c.emoji} ${L10n.label(pair, c.key)}"

    /** The evening reminder: the learner's choice ([setting]), else on for an adult and off for a child (a parent turns it on). */
    fun reminderOn(setting: Boolean?, child: Boolean): Boolean = setting ?: !child

    /** The tag of the tutor's answer in the chat (every conversation the chat shows). */
    const val CHAT = "chat"
    const val TALK = "talk"
    const val VILLAGE = "village"
    const val REMINDER = "reminder"
    const val PERMISSION = "permission"
    const val CASES = "cases"
    const val PACKS = "packs"
    const val OUTBOX = "outbox"
    const val UPDATE = "update:"

    /**
     * What the learner sees now, whose notifications go (and the launcher's dot with them): the app on screen at [screen],
     * the chat open over it ([chatOpen]), Home's banner of an update ([updateShown]), the village without an event
     * ([villageCalm]: the event fought, gone or never come). A tool's approval shows as a dialog whenever the app is on screen.
     */
    fun seen(screen: Screen, chatOpen: Boolean, updateShown: Boolean = false, villageCalm: Boolean = false): List<Seen> = buildList {
        add(Seen(PERMISSION))
        if (chatOpen) add(Seen(CHAT))
        if (updateShown && screen == Screen.Home) add(Seen(UPDATE, prefix = true))
        if (villageCalm) add(Seen(VILLAGE))
        when (screen) {
            Screen.Talk -> add(Seen(TALK))
            is Screen.Family -> screen.challengeId?.let { add(Seen("family:$it")) }
            // the event on the map, the guests' gifts applied, a story told by the fire
            Screen.Village -> {
                add(Seen(VILLAGE))
                add(Seen("guest:", prefix = true))
                add(Seen("story:/", prefix = true))
            }
            is Screen.Scene -> {
                add(Seen("scene:${screen.id}"))
                add(Seen("story:${screen.id}/", prefix = true))
            }
            is Screen.TownQuestion -> add(Seen("question:${screen.key}"))
            Screen.Friends -> add(Seen("link:", prefix = true))
            is Screen.TownProgress -> add(Seen("progress:${screen.id}"))
            // the unit shows its homework
            is Screen.CourseUnit -> add(Seen("school:${screen.id}/", prefix = true))
            is Screen.Sheet -> add(Seen("school:${screen.unit}/${screen.sheet}"))
            is Screen.Cases, is Screen.CasesRun -> add(Seen(CASES))
            Screen.Packs -> add(Seen(PACKS))
            Screen.Readings -> add(Seen("reading:", prefix = true))
            is Screen.ReadAloud -> add(Seen("reading:${screen.id}"))
            is Screen.Player -> add(Seen("module:${screen.module.id}"))
            Screen.GrammarBook -> add(Seen("grammar:", prefix = true))
            is Screen.Review -> add(Seen(REMINDER))
            else -> Unit
        }
    }

    /** An update's notification ([tag]: "update:<versionCode>") once that version, or a later one, is [installed]. */
    fun updateInstalled(tag: String?, installed: Int): Boolean =
        tag != null && tag.startsWith(UPDATE) && (tag.removePrefix(UPDATE).toIntOrNull()?.let { it <= installed } ?: false)
}

/** What a notification says while the app is closed, and where it goes (data/BackgroundCheck posts it). */
object Notices {
    /** The notification of event [ev] ([now]: epoch ms), on a [child]'s profile or an adult's; null: none (it waits in the app). */
    fun of(ev: BridgeEvent, child: Boolean = false, now: Long = System.currentTimeMillis()): Notice? {
        val link = DeepLink.of(ev)
        return when (ev) {
            // a check's or a question's feedback shows with the question in the app
            is BridgeEvent.Reply -> when {
                ev.conversationId.startsWith("ex") || ev.conversationId.startsWith(TownQuestions.CONVERSATION) -> null
                else -> Notice(NotifyChannel.MESSAGES, replyTag(ev.conversationId), "💬 ${inTarget("common.yourTutor")}", previewLine(ev.text, 140), link)
            }
            is BridgeEvent.PartnerChallenge -> Notice(NotifyChannel.MESSAGES, "family:${ev.id}", "${ev.emoji} ${Family.fromLabel(ev.from, ev.fromSl)}", ev.text, link)
            // one per guest: their next visit takes the place of the last
            is BridgeEvent.TownGuest -> Notice(NotifyChannel.MESSAGES, "guest:${ev.from}/${ev.learner}", "🧳 ${TownGuests.title(ev)}", ev.from, link)
            is BridgeEvent.TownQuestion -> Notice(NotifyChannel.MESSAGES, "question:${ev.key}", TownQuestions.title(ev), ev.text, link)
            // a child's link that waits for the parent (their app), and how it went (the child's, the other town's)
            is BridgeEvent.TownLink -> Towns.linkNotice(ev)?.let { (title, text) -> Notice(NotifyChannel.MESSAGES, "link:${ev.request.ifBlank { ev.town }}", title, text, link) }
            is BridgeEvent.WorksheetPublished -> if (ev.removed) null else Notice(NotifyChannel.SCHOOL, "school:${ev.unit}/${ev.id}", "📝 ${bi("school.newHomework")}", ev.note ?: ev.title ?: ev.id, link)
            // the tutor's assignment in the case trainer: worth a notification; items published wait quietly
            is BridgeEvent.CasesChanged ->
                if (ev.assigned && ev.focus != null) Notice(NotifyChannel.SCHOOL, NotifyChannels.CASES, "🧩 ${bi("cases.assignedTitle")}", ev.note ?: bi("cases.assignedFocus", "focus" to ev.focus), link) else null
            is BridgeEvent.ModulePublished -> Notice(NotifyChannel.NEW, "module:${ev.id}", "🎉 ${bi("backgroundCheck.newChallenge")}", ev.note ?: ev.title ?: ev.id, link)
            // the packs' list: the newest words' note in one notification
            is BridgeEvent.PackPublished -> Notice(NotifyChannel.NEW, NotifyChannels.PACKS, "📚 ${bi("homeScreen.newWords")}", ev.note ?: ev.title ?: ev.id, link)
            is BridgeEvent.ReadingPublished -> if (ev.removed) null else Notice(NotifyChannel.NEW, "reading:${ev.id}", "📖 ${bi("readings.newReading")}", ev.note ?: ev.title ?: ev.id, link)
            is BridgeEvent.GrammarPublished -> Notice(NotifyChannel.NEW, "grammar:${ev.id}", "📖 ${bi("grammar.newPage")}", ev.note ?: ev.title ?: ev.id, link)
            is BridgeEvent.ScenePublished -> when {
                ev.removed -> null
                // a new dialog of a happening there: news only with its note
                ev.variant -> ev.note?.let { Notice(NotifyChannel.NEW, "scene:${ev.id}", bi("backgroundCheck.newScene", "evEmoji" to (ev.emoji ?: "💬")), it, link) }
                ev.story -> Notice(NotifyChannel.NEW, "story:${ev.id}/${ev.title}", bi("backgroundCheck.newStory", "evEmoji" to (ev.emoji ?: "📖")), ev.note ?: ev.title ?: ev.id, link)
                else -> Notice(NotifyChannel.NEW, "scene:${ev.id}", bi("backgroundCheck.newScene", "evEmoji" to (ev.emoji ?: "📍")), ev.note ?: ev.title ?: ev.id, link)
            }
            // a parent's weekly note of their child's town
            is BridgeEvent.TownProgressNote ->
                Notice(NotifyChannel.NEW, "progress:${ev.town}", "📈 ${bi("townProgress.weeklyTitle", "name" to ev.learner.ifBlank { "?" })}", ev.text(), link)
            is BridgeEvent.AppUpdate -> Notice(
                NotifyChannel.SYSTEM, "${NotifyChannels.UPDATE}${ev.versionCode}", "⬆️ ${bi("backgroundCheck.updateVersion", "versionName" to ev.versionName)}",
                ev.notes ?: inBase("backgroundCheck.openToInstall"), link, replaces = Seen(NotifyChannels.UPDATE, prefix = true),
            )
            // the tutor waits for it: only while it is fresh
            is BridgeEvent.PermissionRequest ->
                if (now - ev.at < 5 * 60_000) Notice(NotifyChannel.SYSTEM, NotifyChannels.PERMISSION, "🔐 ${inBase("backgroundCheck.claudeNeedsApproval")}", "${ev.tool}: ${ev.description}", link) else null
            // a newcomer's introduction: the bubble in the village says it (the arrival itself was news enough)
            // a card on the cheat sheet: marked new there, no notification of its own
            is BridgeEvent.VoiceUpdated, is BridgeEvent.WordsAdded, is BridgeEvent.LexiconUpdated, is BridgeEvent.SentenceExplained, is BridgeEvent.ArrivalPublished,
            // a word asked for from its card got its meaning: quiet, the app says it when it's opened (the event replays)
            is BridgeEvent.WordRequestDone,
            // the tutor's pointer at a tip: it waits at its place in the app, no notification (a tip never nags)
            is BridgeEvent.TipAsked,
            is BridgeEvent.CardPublished -> null
        }
    }

    /** One thread per conversation the app opens: the chat, the talk, a family challenge's ("fam_<id>"). */
    private fun replyTag(conversation: String): String = when {
        conversation.startsWith("rp-") -> NotifyChannels.TALK
        conversation.startsWith("fam_") -> "family:${conversation.removePrefix("fam_")}"
        else -> NotifyChannels.CHAT
    }

    /** The village's event [e] ([now]: epoch ms); a [child] is told kindly, with no alarm and no hours left. */
    fun villageAlert(e: GameEvent, now: Long, child: Boolean = false): Notice =
        Notice(NotifyChannel.VILLAGE, NotifyChannels.VILLAGE, VillageAlert.title(e.kind, child), VillageAlert.text(e, now, child), DeepLink.Village)

    /**
     * The evening's nudge, [due] cards waiting: "🔥 5 kartic čaka", the streak kept in five minutes; a [child]'s says neither
     * a number nor a streak, "🔥 Vas te pričakuje · The village is looking forward to you".
     */
    fun reminder(due: Int, child: Boolean = false): Notice =
        if (child) Notice(NotifyChannel.REMINDERS, NotifyChannels.REMINDER, "🔥 ${bi("notify.childReminderTitle")}", bi("notify.childReminderText"), DeepLink.Review)
        else Notice(NotifyChannel.REMINDERS, NotifyChannels.REMINDER, "🔥 ${inTarget("backgroundCheck.cardsWaiting", "n" to due)}", bi("backgroundCheck.fiveMinutesKeepStreak"), DeepLink.Review)

    /** A write the node rejected ([label]: what it was), [why]. */
    fun notSaved(label: String, why: String): Notice = Notice(NotifyChannel.SYSTEM, NotifyChannels.OUTBOX, "⚠️ ${bi("backgroundCheck.notSaved", "eLabel" to label)}", why)
}
