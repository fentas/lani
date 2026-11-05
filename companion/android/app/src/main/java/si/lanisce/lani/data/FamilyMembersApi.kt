package si.lanisce.lani.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * A family on a computer, from a parent's device (companion/bridge/src/features/family-members.ts; docs/plans/05-built/
 * leftovers-app.md): the members this learner looks after, a new member, a code for their device, a device's daily budget and
 * its removal. A child's bridge refuses these, and [rules] is all it answers. An older node answers 404: null.
 */
class FamilyMembersApi(private val config: BridgeConfig) {
    // adding a member starts their bridge, and waits for it
    private val http = Http.client(connectSeconds = 5, readSeconds = 60)

    data class Device(val id: String, val name: String, val lastSeen: String?, val cap: Double?)

    data class Member(val profile: String, val name: String, val child: Boolean, val self: Boolean, val up: Boolean, val devices: List<Device>)

    /** [canAdd]; [whyNot]: "off" (the computer's owner), "full". */
    data class Family(val members: List<Member>, val canAdd: Boolean, val whyNot: String?)

    /** What's left on the computer: [what] publish, start, url or tutor; [command] what to run there. */
    data class Left(val what: String, val command: String)

    /** A member with a way in: [pair] the QR code's text (a one-time code and an address: no key), null when no address is known. */
    data class Code(val profile: String, val name: String?, val up: Boolean, val pair: String?, val left: List<Left>, val townLinked: Boolean)

    /** Why the computer said no: the bridge's [code] ("full", "off", "child", "no_culture" …). */
    class Refused(val code: String) : IOException("the computer refused: $code")

    private suspend fun call(method: String, path: String, body: JsonObject? = null): String? {
        val b = config.request(path)
        val rb = body?.toString()?.toRequestBody(Http.jsonType)
        when (method) {
            "POST" -> b.post(rb ?: "{}".toRequestBody(Http.jsonType))
            "PUT" -> b.put(rb ?: "{}".toRequestBody(Http.jsonType))
            "DELETE" -> b.delete()
        }
        val (code, raw) = http.exchange(b.build())
        if (code == 404 && !raw.contains("\"code\"")) return null
        if (code !in 200..299) throw Refused(runCatching { json.parseToJsonElement(raw).jsonObject["code"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: "http_$code")
        return raw
    }

    /** The family as this learner sees it; null from an older node, or where there's no computer. */
    suspend fun list(): Family? = call("GET", PATH)?.let(::parseFamily)

    /** A new member; [Refused] when the computer won't. */
    suspend fun add(name: String, target: String, base: String, child: Boolean): Code? =
        call("POST", PATH, buildJsonObject { put("name", name); put("target", target); put("base", base); put("child", child) })?.let(::parseCode)

    /** Another code for [profile]: a code that ran out, a second device. */
    suspend fun code(profile: String): Code? = call("POST", "$PATH/$profile/pair")?.let(::parseCode)

    /** [device] of [profile] unpaired: its token stops at its next request. */
    suspend fun unpair(profile: String, device: String) {
        call("DELETE", "$PATH/$profile/devices/$device")
    }

    /** The daily budget of [device], [cap] dollars or none. */
    suspend fun budget(profile: String, device: String, cap: Double?) {
        call("PUT", "$PATH/$profile/devices/$device/budget", buildJsonObject { put("daily_cap_usd", cap) })
    }

    /** What a parent set for this device. Null from an older node. */
    suspend fun rules(): Rule? = call("GET", "/family/rules")?.let(::parseRule)

    data class Rule(val cap: Double?, val by: String?)

    companion object {
        const val PATH = "/family/members"

        private fun JsonObject.s(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        private fun JsonObject.b(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() } == true

        internal fun parseFamily(raw: String): Family? = runCatching {
            val o = json.parseToJsonElement(raw).jsonObject
            val members = o["members"]!!.jsonArray.map { m ->
                val x = m.jsonObject
                Member(
                    x.s("profile")!!, x.s("name").orEmpty(), x.b("child"), x.b("self"), x.b("up"),
                    x["devices"]!!.jsonArray.map { d ->
                        val y = d.jsonObject
                        Device(y.s("id")!!, y.s("name").orEmpty(), y.s("last_seen"), y["daily_cap_usd"]?.jsonPrimitive?.doubleOrNull)
                    },
                )
            }
            Family(members, o.b("can_add"), o.s("why_not"))
        }.getOrNull()

        internal fun parseCode(raw: String): Code? = runCatching {
            val o = json.parseToJsonElement(raw).jsonObject
            val pair = (o["pair"] as? JsonObject)?.s("uri")
            val left = o["left"]?.jsonArray?.map { l -> l.jsonObject.let { Left(it.s("what").orEmpty(), it.s("command").orEmpty()) } }.orEmpty()
            Code(o.s("profile")!!, o.s("name"), o.b("up"), pair, left, o.b("town_linked"))
        }.getOrNull()

        internal fun parseRule(raw: String): Rule? = runCatching {
            val o = json.parseToJsonElement(raw).jsonObject
            Rule(o["daily_cap_usd"]?.jsonPrimitive?.doubleOrNull, o.s("by"))
        }.getOrNull()
    }
}
