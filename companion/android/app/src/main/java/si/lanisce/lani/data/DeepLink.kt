package si.lanisce.lani.data

/**
 * Where a notification tap leads (docs/plans/05-built/navigation.md, "Notifications and links"): its target opens over
 * Home, Back leading Home from it. It travels in the launch intent as the string extra [EXTRA], or as a lani:// link
 * ([fromUri]: the widget's, a sign-in's way back).
 */
sealed interface DeepLink {
    /** Home, nothing over it. */
    data object Home : DeepLink
    data object Chat : DeepLink
    data object Talk : DeepLink
    data object Village : DeepLink
    data object Packs : DeepLink
    /** "📖 Branje · Reading": the reading corner (a reading the tutor wrote). */
    data object Readings : DeepLink
    data class Module(val id: String) : DeepLink
    /** A village scene, optionally at a happening ("scene/happening") or a thing: "scene:<id>" or "scene:<id>@<focus>". */
    data class Scene(val id: String, val focus: String? = null) : DeepLink
    data class Family(val challengeId: String) : DeepLink
    /** A question between towns ([key]): one the learner [asked] another town, or a guest's to them (data/TownQuestions.kt). */
    data class Question(val key: String, val asked: Boolean) : DeepLink
    /** "📈 Napredek · Progress" of linked town [town] (a parent's weekly note of their child's). */
    data class TownProgress(val town: String) : DeepLink
    /** "🏫 Šola · School": a unit of the course ([unit]: the tutor's homework is in it), or the school. */
    data class School(val unit: String? = null) : DeepLink
    /** "🤝 Prijatelji · Friends": the linked towns; a child's link that waits for the parent, or how it went (data/Towns.kt). */
    data object Friends : DeepLink
    /**
     * An invitation opened from a link (lani://town?…, fluent://town?…, https://lani.page/town/#…: the manifest's filters):
     * Friends' Accept dialog, filled in with [text]; accepted only by the learner's tap.
     */
    data class Invitation(val text: String) : DeepLink
    /** Today's reviews (the evening's "🔥 N kartic čaka", the widget's cards due); Home when none are due. */
    data object Review : DeepLink
    /** "📖 Slovnica · Grammar": the book open at page [id] (the tutor's new page). */
    data class Grammar(val id: String) : DeepLink
    /** "🧩 Skloni · Cases": the case trainer, its session focused on [focus] (the tutor's assignment) or mixed. */
    data class Cases(val focus: String? = null) : DeepLink
    /** "⚙️ Nastavitve · Settings", at [section] (lani://tutor: back from a sign-in in the browser, at the tutor's settings). */
    data class Settings(val section: String? = null) : DeepLink

    companion object {
        const val EXTRA = "si.lanisce.lani.open"
        private val TOWN_ID = Regex("^[0-9a-f]{32}$")

        fun encode(link: DeepLink): String = when (link) {
            Home -> "home"
            Review -> "review"
            Friends -> "friends"
            is Grammar -> "grammar:${link.id}"
            is Settings -> "settings" + (link.section?.let { ":$it" } ?: "")
            is Cases -> "cases" + (link.focus?.let { ":$it" } ?: "")
            Chat -> "chat"
            Talk -> "talk"
            Village -> "village"
            Packs -> "packs"
            Readings -> "readings"
            is Module -> "module:${link.id}"
            is Scene -> "scene:${link.id}" + (link.focus?.let { "@$it" } ?: "")
            is Family -> "family:${link.challengeId}"
            is Question -> "question:${if (link.asked) "a" else "r"}:${link.key}"
            is School -> "school" + (link.unit?.let { ":$it" } ?: "")
            is TownProgress -> "progress:${link.town}"
            is Invitation -> "invite:${link.text}"
        }

        fun parse(s: String?): DeepLink? {
            val kind = s?.substringBefore(':') ?: return null
            val arg = s.substringAfter(':', "").takeIf { it.isNotBlank() }
            return when (kind) {
                "home" -> Home
                "review" -> Review
                "friends" -> Friends
                "grammar" -> arg?.let(::Grammar)
                "settings" -> Settings(arg)
                "cases" -> Cases(arg)
                "chat" -> Chat
                "talk" -> Talk
                "village" -> Village
                "packs" -> Packs
                "readings" -> Readings
                "school" -> School(arg)
                "module" -> arg?.let(::Module)
                "scene" -> arg?.let { Scene(it.substringBefore('@'), it.substringAfter('@', "").ifBlank { null }) }
                "family" -> arg?.let(::Family)
                "question" -> arg?.substringAfter(':', "")?.takeIf { it.isNotBlank() }?.let { Question(it, asked = arg.substringBefore(':') == "a") }
                "progress" -> arg?.takeIf { TOWN_ID.matches(it) }?.let(::TownProgress)
                "invite" -> arg?.takeIf { InviteLink.looksLike(it) }?.let(::Invitation)
                else -> null
            }
        }

        /**
         * The invitation a link that opened the app carries ([uri]: its intent's data), as a [DeepLink.Invitation]; null for
         * any other link (lani://tutor/signed-in only brings the app to the front).
         */
        fun ofUri(uri: String?): DeepLink? = uri?.takeIf { InviteLink.looksLike(it) }?.let(::Invitation)

        /** Where a notification about [ev] leads; null just opens the app. */
        fun of(ev: BridgeEvent): DeepLink? = when (ev) {
            is BridgeEvent.Reply -> when {
                ev.conversationId.startsWith("rp-") -> Talk
                ev.conversationId.startsWith("fam_") -> Family(ev.conversationId.removePrefix("fam_"))
                else -> Chat
            }
            is BridgeEvent.ModulePublished -> Module(ev.id)
            is BridgeEvent.PackPublished -> Packs
            is BridgeEvent.ReadingPublished -> Readings
            // the tutor's grammar page: the book open at it
            is BridgeEvent.GrammarPublished -> Grammar(ev.id)
            is BridgeEvent.PartnerChallenge -> Family(ev.id)
            is BridgeEvent.ScenePublished -> if (ev.removed || ev.id.isEmpty()) Village else Scene(ev.id)
            // A permission prompt shows as a dialog once the app is open; an update as the Home banner.
            is BridgeEvent.PermissionRequest, is BridgeEvent.AppUpdate, BridgeEvent.VoiceUpdated,
            is BridgeEvent.WordsAdded, is BridgeEvent.LexiconUpdated, is BridgeEvent.SentenceExplained, is BridgeEvent.WordRequestDone -> null
            // a card on the cheat sheet: it shows there, marked new, the next time it's opened
            is BridgeEvent.CardPublished -> null
            // the tutor's assignment: the trainer, its session focused; new items wait for the next session
            is BridgeEvent.CasesChanged -> if (ev.assigned && ev.focus != null) Cases(ev.focus) else null
            // a newcomer's introduction: their bubble waits in the village
            is BridgeEvent.ArrivalPublished -> Village
            // a guest's visit: the village applies it once open
            is BridgeEvent.TownGuest -> Village
            // a guest's question to answer, or the answer to the learner's
            is BridgeEvent.TownQuestion -> Question(ev.key, asked = ev.kind == TownQuestions.ANSWER)
            // the tutor's homework: in its unit
            is BridgeEvent.WorksheetPublished -> if (ev.removed) null else School(ev.unit)
            // a parent's weekly note: the town's progress screen
            is BridgeEvent.TownProgressNote -> TownProgress(ev.town)
            // a child's link that waits for the parent, or how it went: Friends
            is BridgeEvent.TownLink -> Friends
        }

        /**
         * A lani:// link's target ("lani://scene/ob-ognju", "lani://school/biti", "lani://review"): the same as [parse]'s,
         * its path's first part the argument; "lani://tutor" (and lani://tutor/signed-in, back from "Sign in with ChatGPT"
         * in the browser) is the tutor's settings. Null for anything else (another scheme, a target this app doesn't know).
         */
        fun fromUri(uri: String?): DeepLink? {
            val u = runCatching { java.net.URI(uri ?: return null) }.getOrNull() ?: return null
            if (u.scheme != "lani") return null
            val host = u.host ?: return null
            if (host == "tutor") return Settings("tutor")
            val arg = u.path.orEmpty().trim('/').substringBefore('/').takeIf { it.isNotBlank() }
            return when (host) {
                // a scene's happening is its own "scene/happening": all of it the focus
                "scene" -> arg?.let { Scene(it, u.path.trim('/').substringAfter('/', "").ifBlank { null }?.let { f -> "$it/$f" }) }
                "question" -> null // a question's key is the bridge's: only from a notification
                else -> parse(if (arg != null) "$host:$arg" else host)
            }
        }
    }
}
