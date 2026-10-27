package si.lanisce.lani.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import si.lanisce.lani.l10n.bi
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

// Towns that know each other (companion/README.md, "Towns"; plan 2, §3). The app only ever talks to its own bridge:
// GET /towns lists the linked towns (the bridge asks each for its public state), POST /towns/invite makes an
// invitation (shown as a QR code of its web link, https://lani.page/town/#…, which opens this app; lani://town?… in the
// app), POST /towns/accept takes another town's. A child's town invites and accepts too, but links nothing until a parent
// says yes (companion/README.md, "A child's town"; docs/plans/05-built/invitations.md): its links that wait come with
// GET /towns ([TownsList.requests]); a parent's app sees them for each of their children (GET /towns/children,
// [TownChildren]) and says yes or no, or links their own child's town of the node directly.

/** A town as a request or a guardian names it: its id, village, learner, culture pack and language. */
@Serializable
data class TownRef(
    val id: String = "",
    val name: String = "",
    val learner: String = "",
    val culture: String = "",
    val language: String = "",
)

/**
 * This learner's own town, as GET /towns says it: [canInvite] links at once (an adult's town); a child's links once a
 * parent says yes ([parentApproves], its [guardians]: the parents' towns). [reach]: who can accept its invitations
 * ([Towns.REACH_NODE]: only the towns of the node's computer); null from an older bridge.
 */
@Serializable
data class TownSelf(
    val id: String,
    val name: String = "",
    val learner: String = "",
    val culture: String = "",
    val language: String = "",
    val child: Boolean = false,
    @SerialName("can_invite") val canInvite: Boolean = false,
    @SerialName("parent_approves") val parentApproves: Boolean = false,
    val guardians: List<TownRef> = emptyList(),
    val reach: String? = null,
    val relay: String? = null,
)

/**
 * A linked town: who it is, when and how it was linked, and ([status] "ok") what its public state says now: its age
 * (an [si.lanisce.lani.game.Age] name), how many live there, its buildings by type. Another [status]
 * ("unreachable", "refused", "bad_answer") keeps how it showed itself last; "pending": a child's town that links back
 * once its parent says yes. [sharing]: what each shares of its learning with the other (companion/README.md, "Progress
 * between towns").
 */
@Serializable
data class TownRow(
    val id: String,
    val name: String = "",
    val learner: String = "",
    val culture: String = "",
    val language: String = "",
    @SerialName("linked_at") val linkedAt: String = "",
    val by: String = "",
    val via: String = "",
    val status: String = "",
    val age: String? = null,
    val villagers: Int? = null,
    val buildings: Map<String, Int> = emptyMap(),
    val date: String? = null,
    /** What this learner shares of their learning with it, and it with them (data/TownProgress.kt); null from an older bridge. */
    val sharing: Sharing? = null,
) {
    val reachable: Boolean get() = status == "ok"
    /** A child's town that links back once its parent says yes: nothing of it can be seen or visited until then. */
    val pending: Boolean get() = status == Towns.PENDING
}

/** How a request's town looks now (its preview): its age, how many live there, its buildings by type. */
@Serializable
data class TownPreview(val age: String? = null, val villagers: Int = 0, val buildings: Map<String, Int> = emptyMap())

/**
 * A link that waits for a child's parent: the other [town], [via] "invite" (it accepted the child's invitation) or
 * "accept" (the child accepted its invitation), when, until when, and its [status] ("pending", "approved", "declined",
 * "withdrawn", "expired"); [preview] when a parent's app or the family page asks.
 */
@Serializable
data class LinkRequest(
    val id: String,
    val town: TownRef = TownRef(),
    val via: String = "",
    val at: String = "",
    @SerialName("expires_at") val expiresAt: String = "",
    val status: String = Towns.PENDING,
    @SerialName("decided_at") val decidedAt: String? = null,
    val by: String? = null,
    @SerialName("theirs_pending") val theirsPending: Boolean = false,
    val preview: TownPreview? = null,
) {
    val open: Boolean get() = status == Towns.PENDING
}

@Serializable
data class TownsList(
    val self: TownSelf,
    val towns: List<TownRow> = emptyList(),
    /** A child's links that wait for the parent, and those decided this week. */
    val requests: List<LinkRequest> = emptyList(),
)

/**
 * A child of this learner's (GET /towns/children): their town, whether it is linked with this one and this one its
 * [guardian] (then its links wait for this learner's yes: [requests]), and, for a child's town of the same node
 * ([profile]), whether this town may link it directly ([canLink]). [status] "ok", or why its town can't be asked now.
 */
@Serializable
data class TownChild(
    val id: String = "",
    val name: String = "",
    val learner: String = "",
    val culture: String = "",
    val language: String = "",
    val profile: String? = null,
    val linked: Boolean = false,
    val guardian: Boolean = false,
    @SerialName("can_link") val canLink: Boolean = false,
    val status: String = "ok",
    val requests: List<LinkRequest> = emptyList(),
)

@Serializable
data class TownChildren(
    val children: List<TownChild> = emptyList(),
    /** This town is the node owner's: it links the node's children's towns directly. */
    @SerialName("can_link_local") val canLinkLocal: Boolean = false,
)

/**
 * An invitation from POST /towns/invite: its [code], the [link] for the app (lani://town?…) and its [webLink] (what the QR
 * code carries and a messenger makes tappable: it opens this app, else a page that says how); who can accept it
 * ([reach]); a child's ([parentApproves]).
 */
@Serializable
data class TownInvite(
    val code: String,
    val link: String,
    @SerialName("web_link") val webLink: String? = null,
    val url: String = "",
    val fingerprint: String = "",
    @SerialName("expires_at") val expiresAt: String = "",
    val reach: String? = null,
    val relay: String? = null,
    @SerialName("parent_approves") val parentApproves: Boolean = false,
) {
    /** What to show and send: the web link, else (an older bridge) the app's link. */
    val shareLink: String get() = webLink ?: link
    /** Who can accept it: as the bridge says, else from its address (an older bridge). */
    val reachNow: String get() = reach ?: Towns.reachOf(url, relay)
}

/**
 * An invitation as scanned, pasted or opened from a link: `lani://town?v=1&u=<the inviter's URL>&c=<code>&f=<its
 * fingerprint>[&r=<its relay address>]`, fluent:// as the bridges wrote it before the rename ([LINK_SCHEMES]), or its web
 * link `https://<host>/town/#v=1&…` (the parameters in the fragment, or the query): any host, as the fingerprint says
 * which town it is.
 */
data class InviteLink(val url: String, val code: String, val fingerprint: String, val relay: String? = null) {
    /** The invitation as `lani://town?…`: what this app sends its bridge (one older than the web links reads only this). */
    fun text(): String = "lani://town?v=$VERSION&u=${enc(url)}&c=$code&f=$fingerprint" + (relay?.let { "&r=${enc(it)}" } ?: "")

    companion object {
        /** The invitation version this app understands. */
        const val VERSION = 1
        private val CODE = Regex("^[A-Z0-9]{6,32}$")
        private val FINGERPRINT = Regex("^[0-9a-f]{32}$")
        private val RELAY = Regex("^https?://[^?#\\s]+/v1/towns/([0-9a-f]{32})$")
        private val WEB_PATH = Regex("/town/?$")
        /** Begins like an invitation's web link, though it may not parse. */
        private val WEB_START = Regex("^https://[^/\\s]+/town/?[#?]", RegexOption.IGNORE_CASE)

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        /** Whether [text] is (or begins like) an invitation of any of its forms: what a link that opens the app carries. */
        fun looksLike(text: String): Boolean = text.trim().let { startsLink(it, "town") || WEB_START.containsMatchIn(it) || parse(it) is InviteParse.Ok }

        fun parse(text: String): InviteParse {
            val t = text.trim()
            val uri = runCatching { URI(t) }.getOrNull()
                ?: return InviteParse.Invalid(if (startsLink(t, "town") || WEB_START.containsMatchIn(t)) InviteProblem.INCOMPLETE else InviteProblem.NOT_INVITE)
            val raw = when {
                isLinkScheme(uri.scheme) && uri.host.equals("town", ignoreCase = true) && (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") -> uri.rawQuery.orEmpty()
                uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty() && uri.rawUserInfo == null && WEB_PATH.containsMatchIn(uri.rawPath.orEmpty()) ->
                    uri.rawFragment?.takeIf { it.isNotEmpty() } ?: uri.rawQuery.orEmpty()
                else -> return InviteParse.Invalid(InviteProblem.NOT_INVITE)
            }
            val q = raw.split('&').mapNotNull { part ->
                runCatching { URLDecoder.decode(part.substringBefore('='), "UTF-8") to URLDecoder.decode(part.substringAfter('=', ""), "UTF-8") }.getOrNull()
            }.toMap()
            val version = q["v"]?.toIntOrNull() ?: return InviteParse.Invalid(InviteProblem.INCOMPLETE)
            if (version > VERSION) return InviteParse.Invalid(InviteProblem.NEWER_VERSION)
            if (version < 1) return InviteParse.Invalid(InviteProblem.INCOMPLETE)
            val url = PairPayload.bridgeUrl(q["u"] ?: return InviteParse.Invalid(InviteProblem.INCOMPLETE)) ?: return InviteParse.Invalid(InviteProblem.BAD_URL)
            val code = q["c"]?.uppercase()?.replace(Regex("[\\s-]"), "")?.takeIf { CODE.matches(it) } ?: return InviteParse.Invalid(InviteProblem.INCOMPLETE)
            val fingerprint = q["f"]?.lowercase()?.takeIf { FINGERPRINT.matches(it) } ?: return InviteParse.Invalid(InviteProblem.INCOMPLETE)
            // its relay address: only one of the invited town's own
            val relay = q["r"]?.trim()?.takeIf { r -> RELAY.find(r)?.groupValues?.get(1) == fingerprint }
            return InviteParse.Ok(InviteLink(url, code, fingerprint, relay))
        }
    }
}

sealed interface InviteParse {
    data class Ok(val link: InviteLink) : InviteParse
    data class Invalid(val problem: InviteProblem) : InviteParse
}

/** Why a scanned or pasted text isn't an invitation this app can use, "target · base". */
enum class InviteProblem(private val emoji: String, private val key: String) {
    NOT_INVITE("🤔", "towns.notInvitation"),
    NEWER_VERSION("⬆️", "towns.invitationNeedsNewer"),
    INCOMPLETE("🧩", "towns.invitationIncomplete"),
    BAD_URL("🔗", "towns.invitationBadAddress");

    val message: String get() = "$emoji ${bi(key)}"
}

/** What accepting an invitation came to: linked with a town (or waiting for a parent: [pending]), or why not. */
sealed interface AcceptOutcome {
    /** [pending]: [Towns.WAIT_PARENT] this child's parent says yes first, [Towns.WAIT_THEIR_PARENT] that child's parent does. */
    data class Linked(val name: String, val learner: String, val pending: String? = null) : AcceptOutcome
    data class Failed(val message: String) : AcceptOutcome
}

object Towns {
    /** A link that waits for a child's parent (a row's status, a request's). */
    const val PENDING = "pending"
    /** How an accepted invitation waits: for this child's parent, or the other (a child's) town's parent. */
    const val WAIT_PARENT = "parent"
    const val WAIT_THEIR_PARENT = "their_parent"
    /** Who can accept an invitation: only the towns of the node's computer, its tailnet, its network, anyone (a relay). */
    const val REACH_NODE = "node"
    const val REACH_TAILNET = "tailnet"
    const val REACH_NETWORK = "network"
    const val REACH_RELAY = "relay"

    /** GET /towns. */
    fun parseList(body: String): TownsList = json.decodeFromString(TownsList.serializer(), body)

    fun parseInvite(body: String): TownInvite = json.decodeFromString(TownInvite.serializer(), body)

    /** GET /towns/children. */
    fun parseChildren(body: String): TownChildren = json.decodeFromString(TownChildren.serializer(), body)

    /** The `code` of an error answer ({error, code}), if any. */
    fun errorCode(body: String): String? = runCatching {
        ((json.parseToJsonElement(body) as? JsonObject)?.get("code") as? JsonPrimitive)?.content
    }.getOrNull()

    /** POST /towns/accept's answer ([status], [body]) for the learner. */
    fun acceptOutcome(status: Int, body: String): AcceptOutcome {
        if (status == 200) {
            val o = runCatching { json.parseToJsonElement(body) as JsonObject }.getOrNull()
            val town = o?.get("town") as? JsonObject ?: return AcceptOutcome.Failed("⚠️ ${bi("towns.answerNotUnderstood")}")
            val text = { k: String -> (town[k] as? JsonPrimitive)?.content.orEmpty() }
            val pending = (o["pending"] as? JsonPrimitive)?.content?.takeIf { it == WAIT_PARENT || it == WAIT_THEIR_PARENT }
            return AcceptOutcome.Linked(text("name"), text("learner"), pending)
        }
        return AcceptOutcome.Failed(problem(status, errorCode(body)))
    }

    /** Why an invitation couldn't be made or accepted, or a link decided, "target · base". */
    fun problem(status: Int, code: String?): String = when {
        code == "child" -> "👨‍👩‍👦 ${bi("towns.parentLinksChild")}"
        code == "too_many_requests" -> "⏳ ${bi("towns.tooManyWaiting")}"
        code == "not_owner" -> "🖥️ ${bi("towns.notNodeOwner")}"
        code == "not_local" || code == "unknown_town" -> "🔎 ${bi("towns.childTownGone")}"
        code == "decided" -> "✔ ${bi("towns.decidedAlready")}"
        code == "unknown_request" || code == "expired" -> "⌛ ${bi("towns.requestGone")}"
        code == "not_guardian" || code == "family_only" -> "👪 ${bi("towns.onlyGuardian")}"
        code == "mismatch" -> "🔒 ${bi("towns.notThatTown")}"
        code == "invalid_code" || status == 403 -> "❌ ${bi("towns.invitationUsedOrWrong")}"
        code == "expired_code" || status == 410 -> "⌛ ${bi("towns.invitationExpired")}"
        code == "rate_limited" || status == 429 -> "⏳ ${bi("towns.tooManyTries")}"
        code == "not_that_town" -> "🔒 ${bi("towns.notThatTown")}"
        code == "self" -> "🏡 ${bi("towns.ownInvitation")}"
        code == "bad_invite" -> InviteProblem.INCOMPLETE.message
        code == "unreachable" || code == "not_a_town" -> "📡 ${bi("towns.cantReachTown")}"
        status == 404 -> "🧰 ${bi("towns.tutorTooOld")}"
        else -> "⚠️ ${bi("towns.somethingWentWrong", "status" to status)}"
    }

    /**
     * Who can accept an invitation to a town at [url] (with [relay]) when the bridge doesn't say (an older one), as
     * towns.ts inviteReach: a relay anyone, a loopback address only the node's computer, a *.ts.net name or a
     * 100.64.0.0/10 address the tailnet, anything else the network.
     */
    fun reachOf(url: String, relay: String? = null): String {
        if (!relay.isNullOrBlank() || url.contains("/v1/towns/")) return REACH_RELAY
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase()?.trim('[', ']') ?: return REACH_NODE
        if (host == "localhost" || host.endsWith(".localhost") || host == "::1" || host.startsWith("127.") || host == "0.0.0.0") return REACH_NODE
        val v4 = Regex("^(\\d+)\\.(\\d+)\\.\\d+\\.\\d+$").find(host)?.groupValues
        if (host.endsWith(".ts.net") || (v4 != null && v4[1] == "100" && (v4[2].toIntOrNull() ?: 0) in 64..127) || host.startsWith("fd7a:115c:a1e0:")) return REACH_TAILNET
        return REACH_NETWORK
    }

    /**
     * What the app says of a [BridgeEvent.TownLink] while it is open (a banner), "target · base"; null for one it says
     * nothing of (its own request waiting: the list shows it).
     */
    fun linkBanner(ev: BridgeEvent.TownLink): String? = when (ev.kind) {
        "request" -> "👪 ${bi("towns.wantsFriend", "child" to ev.child.ifBlank { "?" }, "town" to ev.town)}"
        "linked" -> "🤝 ${bi("towns.linkedNotice", "town" to ev.town)}"
        "declined" -> "👋 ${bi("towns.notThisTime", "town" to ev.town)}"
        else -> null
    }

    /** A [BridgeEvent.TownLink]'s notification while the app is closed: its title and text; null for none. */
    fun linkNotice(ev: BridgeEvent.TownLink): Pair<String, String>? = when (ev.kind) {
        "request" -> "👪 ${bi("towns.linkRequestTitle", "child" to ev.child.ifBlank { "?" })}" to bi("towns.wantsFriend", "child" to ev.child.ifBlank { "?" }, "town" to ev.town)
        "linked" -> "🤝 ${bi("towns.linkedNotice", "town" to ev.town)}" to ev.learner
        "declined" -> "👋 ${bi("towns.notThisTime", "town" to ev.town)}" to ev.learner
        else -> null
    }

    /** "🇮🇹" for a town's language; its code when there is no flag for it. */
    fun flag(language: String): String = when (language) {
        "sl" -> "🇸🇮"
        "it" -> "🇮🇹"
        "de" -> "🇩🇪"
        "en" -> "🇬🇧"
        "hr" -> "🇭🇷"
        "fr" -> "🇫🇷"
        "es" -> "🇪🇸"
        else -> language.uppercase()
    }

    /** "K7Q2M9XPA4TD" → "K7Q2 M9XP A4TD": easier to read out and type. */
    fun groupCode(code: String): String = code.chunked(4).joinToString(" ")
}
