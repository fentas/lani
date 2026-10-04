package si.lanisce.lani.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import si.lanisce.lani.l10n.bi
import java.net.URI
import java.net.URLDecoder

// Towns that know each other (companion/README.md, "Towns"; plan 2, §3). The app only ever talks to its own bridge:
// GET /towns lists the linked towns (the bridge asks each for its public state), POST /towns/invite makes an
// invitation (lani://town?…, shown as a QR code), POST /towns/accept takes another town's. A child's bridge refuses
// both: a parent links a child's town on the node (companion/bin/lani-town).

/** This learner's own town, as GET /towns says it: [canInvite] is false for a child. */
@Serializable
data class TownSelf(
    val id: String,
    val name: String = "",
    val learner: String = "",
    val culture: String = "",
    val language: String = "",
    val child: Boolean = false,
    @SerialName("can_invite") val canInvite: Boolean = false,
)

/**
 * A linked town: who it is, when and how it was linked, and ([status] "ok") what its public state says now: its age
 * (an [si.lanisce.lani.game.Age] name), how many live there, its buildings by type. Another [status]
 * ("unreachable", "refused", "bad_answer") keeps how it showed itself last.
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
) {
    val reachable: Boolean get() = status == "ok"
}

@Serializable
data class TownsList(val self: TownSelf, val towns: List<TownRow> = emptyList())

/** An invitation from POST /towns/invite: its [code], and the [link] the QR code carries. */
@Serializable
data class TownInvite(
    val code: String,
    val link: String,
    val url: String = "",
    val fingerprint: String = "",
    @SerialName("expires_at") val expiresAt: String = "",
)

/**
 * An invitation as scanned or pasted: `lani://town?v=1&u=<the inviter's URL>&c=<code>&f=<its fingerprint>` (or fluent://, as
 * the bridges wrote it before the rename: [LINK_SCHEMES]).
 */
data class InviteLink(val url: String, val code: String, val fingerprint: String) {
    companion object {
        /** The invitation version this app understands. */
        const val VERSION = 1
        private val CODE = Regex("^[A-Z0-9]{6,32}$")
        private val FINGERPRINT = Regex("^[0-9a-f]{32}$")

        fun parse(text: String): InviteParse {
            val t = text.trim()
            val uri = runCatching { URI(t) }.getOrNull()
                ?: return InviteParse.Invalid(if (startsLink(t, "town")) InviteProblem.INCOMPLETE else InviteProblem.NOT_INVITE)
            if (!isLinkScheme(uri.scheme) || !uri.host.equals("town", ignoreCase = true) ||
                !(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
            ) return InviteParse.Invalid(InviteProblem.NOT_INVITE)
            val q = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
                runCatching { URLDecoder.decode(part.substringBefore('='), "UTF-8") to URLDecoder.decode(part.substringAfter('=', ""), "UTF-8") }.getOrNull()
            }.toMap()
            val version = q["v"]?.toIntOrNull() ?: return InviteParse.Invalid(InviteProblem.INCOMPLETE)
            if (version > VERSION) return InviteParse.Invalid(InviteProblem.NEWER_VERSION)
            if (version < 1) return InviteParse.Invalid(InviteProblem.INCOMPLETE)
            val url = PairPayload.bridgeUrl(q["u"] ?: return InviteParse.Invalid(InviteProblem.INCOMPLETE)) ?: return InviteParse.Invalid(InviteProblem.BAD_URL)
            val code = q["c"]?.uppercase()?.replace(Regex("[\\s-]"), "")?.takeIf { CODE.matches(it) } ?: return InviteParse.Invalid(InviteProblem.INCOMPLETE)
            val fingerprint = q["f"]?.lowercase()?.takeIf { FINGERPRINT.matches(it) } ?: return InviteParse.Invalid(InviteProblem.INCOMPLETE)
            return InviteParse.Ok(InviteLink(url, code, fingerprint))
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

/** What accepting an invitation came to: linked with a town, or why not. */
sealed interface AcceptOutcome {
    data class Linked(val name: String, val learner: String) : AcceptOutcome
    data class Failed(val message: String) : AcceptOutcome
}

object Towns {
    /** GET /towns. */
    fun parseList(body: String): TownsList = json.decodeFromString(TownsList.serializer(), body)

    fun parseInvite(body: String): TownInvite = json.decodeFromString(TownInvite.serializer(), body)

    /** The `code` of an error answer ({error, code}), if any. */
    fun errorCode(body: String): String? = runCatching {
        ((json.parseToJsonElement(body) as? JsonObject)?.get("code") as? JsonPrimitive)?.content
    }.getOrNull()

    /** POST /towns/accept's answer ([status], [body]) for the learner. */
    fun acceptOutcome(status: Int, body: String): AcceptOutcome {
        if (status == 200) {
            val town = runCatching { (json.parseToJsonElement(body) as JsonObject)["town"] as JsonObject }.getOrNull()
                ?: return AcceptOutcome.Failed("⚠️ ${bi("towns.answerNotUnderstood")}")
            val text = { k: String -> (town[k] as? JsonPrimitive)?.content.orEmpty() }
            return AcceptOutcome.Linked(text("name"), text("learner"))
        }
        return AcceptOutcome.Failed(problem(status, errorCode(body)))
    }

    /** Why an invitation couldn't be made or accepted, "target · base". */
    fun problem(status: Int, code: String?): String = when {
        code == "child" -> "👨‍👩‍👦 ${bi("towns.parentLinksChild")}"
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
