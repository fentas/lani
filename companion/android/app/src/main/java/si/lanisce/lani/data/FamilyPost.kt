package si.lanisce.lani.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * What a parent sends a device of the family after the exchange (docs/plans/05-built/leftovers-app.md): a daily budget, or
 * that the device leaves the family. [seq] only goes up, so a note seen once is never taken again; [cap]: dollars a day,
 * null for no limit of the family's.
 */
@Serializable
data class FamilyNote(val seq: Long, val kind: String, val cap: Double? = null) {
    companion object {
        const val BUDGET = "budget"
        const val FORGET = "forget"
    }
}

/**
 * A device this one shared with ("👪 Add a family member"): [bond] the key the two keep ([FamilyLink.bondKey]); [pending] the one
 * note on its way (a new one replaces it: only the last word counts); [cap] what the parent chose and [capThere] what the
 * device last said it has; [heard] when it was last in touch; [posted] when the pending note last went to the relay.
 */
@Serializable
data class FamilyMember(
    val id: String,
    val name: String,
    val bond: String,
    val addedAt: Long,
    val seq: Long = 0,
    val pending: FamilyNote? = null,
    val cap: Double? = null,
    val capThere: Double? = null,
    val heard: Long? = null,
    val posted: Long? = null,
) {
    /** It was told to leave and hasn't heard yet. */
    val leaving: Boolean get() = pending?.kind == FamilyNote.FORGET

    /** Never the bond itself: a member printed by accident stays harmless. */
    override fun toString(): String = "FamilyMember($id, $name, pending=$pending, heard=$heard)"
}

/**
 * The device this one came from: [family] and [member] who it is there, [name] what it calls that device, [bond] the key the two
 * keep, [seen] the last note taken; [keySha] the hash of the key that came from there, so only that key goes when this
 * device leaves, never one the learner set themselves.
 */
@Serializable
data class FamilyParent(
    val family: String,
    val member: String,
    val name: String,
    val bond: String,
    val seen: Long = 0,
    val keySha: String? = null,
    val provider: String? = null,
    val said: Long? = null,
    /** The relay the exchange named: the two meet there, whatever this device's own setting says. */
    val relay: String? = null,
) {
    override fun toString(): String = "FamilyParent($family, $member, $name, seen=$seen)"
}

/** A key that crossed but couldn't be checked yet: no network, or the provider didn't answer. Kept until a check can run. */
@Serializable
data class FamilyKeyWaiting(val provider: String, val key: String, val preset: String? = null) {
    override fun toString(): String = "FamilyKeyWaiting($provider, ‹key›, $preset)"
}

/**
 * Who this device is in its family, kept sealed in the tutor's vault (never in an export, a backup or a log): the devices it shared
 * with ([members]; a child's device never has any: only a device with the settings' page shares) and the one it came from
 * ([parent]). Pure: each change gives the next book.
 */
@Serializable
data class FamilyBook(
    val family: String? = null,
    val members: List<FamilyMember> = emptyList(),
    val parent: FamilyParent? = null,
    val keyWaiting: FamilyKeyWaiting? = null,
) {
    private fun with(id: String, change: (FamilyMember) -> FamilyMember?): FamilyBook =
        copy(members = members.mapNotNull { if (it.id == id) change(it) else it })

    fun member(id: String): FamilyMember? = members.firstOrNull { it.id == id }

    /** The parent held the button: [name] is on the list, reachable with [bond]. */
    fun added(family: String, id: String, name: String, bond: ByteArray, now: Long): FamilyBook =
        copy(family = family, members = members + FamilyMember(id, name, FamilyPost.b64(bond), now))

    /** The device's daily budget, [cap] dollars or none of the family's: on its way until the device says it has it. */
    fun setCap(id: String, cap: Double?): FamilyBook = with(id) { m ->
        if (m.leaving) return@with m
        val seq = m.seq + 1
        m.copy(seq = seq, cap = cap, pending = FamilyNote(seq, FamilyNote.BUDGET, cap).takeUnless { cap == m.capThere && m.pending == null }, posted = null)
    }

    /** The device leaves: told when it's next in reach, and on the list until then. */
    fun remove(id: String): FamilyBook = with(id) { m ->
        val seq = m.seq + 1
        m.copy(seq = seq, pending = FamilyNote(seq, FamilyNote.FORGET), posted = null)
    }

    /** Off the list at once: the parent gives up on telling it. */
    fun drop(id: String): FamilyBook = with(id) { null }

    /** The device [id] said it has seen everything up to [seen]: what that settles is settled, and a device that left is gone. */
    fun heard(id: String, seen: Long, now: Long): FamilyBook = with(id) { m ->
        val p = m.pending
        when {
            p == null || seen < p.seq -> m.copy(heard = now)
            p.kind == FamilyNote.FORGET -> null
            else -> m.copy(heard = now, pending = null, capThere = p.cap, posted = null)
        }
    }

    fun posted(id: String, now: Long): FamilyBook = with(id) { it.copy(posted = now) }

    /** This device came from a family: [gift] says who it is there, [bond] is the key the two keep, [relay] the offer's. An older sender has none. */
    fun joined(gift: FamilyLink.Gift, bond: ByteArray, relay: String? = null): FamilyBook {
        val family = gift.family ?: return this
        val member = gift.member ?: return this
        return copy(parent = FamilyParent(family, member, gift.from.orEmpty(), FamilyPost.b64(bond), keySha = gift.key?.let(FamilyPost::sha), provider = gift.provider, relay = relay))
    }

    /** This device is out of its family. A key still waiting for its check goes too: it must not arrive after the device has left. */
    fun left(): FamilyBook = copy(parent = null, keyWaiting = null)

    /** A note taken: [FamilyParent.seen] moves on; a note seen before is nothing new (null). */
    fun took(note: FamilyNote): FamilyBook? {
        val p = parent ?: return null
        if (note.seq <= p.seen) return null
        return if (note.kind == FamilyNote.FORGET) left() else copy(parent = p.copy(seen = note.seq))
    }

    override fun toString(): String = "FamilyBook(family=$family, members=${members.size}, parent=${parent?.name})"

    fun text(): String = codec.encodeToString(this)

    companion object {
        private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        /** [raw] as a book; a new one for nothing kept or something this app can't read. */
        fun parse(raw: String?): FamilyBook = raw?.let { runCatching { codec.decodeFromString<FamilyBook>(it) }.getOrNull() } ?: FamilyBook()
    }
}

/**
 * The channel between a parent's device and a device of the family, once the two have a bond: sealed notes one way, what the device
 * has seen the other way, over the Wi-Fi they share ([Office], [exchange]) or through the relay's boxes ([FamilyLink.Drop]).
 * The same sealed bytes on both, so neither a neighbour on the Wi-Fi nor the relay reads or makes one.
 *
 * - **Sealed both ways, each its own key:** [Keys.down] for notes, [Keys.up] for answers, so nothing can be sent back at its sender.
 * - **Only forwards:** a note's seq only goes up ([FamilyBook.took]), so a note recorded on the way and played again does nothing.
 * - **One way of command:** only the side that shared has a [FamilyMember], and only it seals with [Keys.down]; a device of
 *   the family has no list to send to, so there's nothing a child's device could tell anyone.
 *
 * Pure but for the sockets and a [FamilyLink.Drop]. Nothing here logs what it carries.
 */
object FamilyPost {
    const val VERSION = 1
    /** How long a note waits on the relay. A device that took longer gets it again ([REPOST_MS]). */
    const val NOTE_TTL_S = 14 * 86_400
    /** A note the device hasn't answered goes to the relay again after this long (it may have been taken and lost). */
    const val REPOST_MS = 86_400_000L
    /** A device says it's there through the relay at most this often. */
    const val SAY_MS = 6 * 3_600_000L

    private const val MAX_LINE = 16 * 1024
    private const val ID_BYTES = 12
    /** A whole visit to the office. */
    private const val VISIT_MS = 8_000L
    private val random = SecureRandom()

    internal fun b64(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun unb64(s: String): ByteArray? = runCatching { Base64.getUrlDecoder().decode(s) }.getOrNull()

    fun newId(): String = b64(ByteArray(ID_BYTES).also(random::nextBytes))

    /** A short hash of [text]: what's kept to know a key again, without keeping the key. */
    fun sha(text: String): String = b64(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))

    private fun hmac(key: ByteArray, what: String): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(what.toByteArray())
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    /** What a bond opens: a key for each direction and the relay's box for each. */
    class Keys(bond: ByteArray) {
        val down = hmac(bond, "lani-family-down")
        val up = hmac(bond, "lani-family-up")
        val boxDown = hex(hmac(bond, "lani-family-box-down"))
        val boxUp = hex(hmac(bond, "lani-family-box-up"))

        companion object {
            /** Null for a bond that can't be read. */
            fun of(bond: String): Keys? = unb64(bond)?.takeIf { it.size >= 32 }?.let(::Keys)
        }
    }

    /** The name a parent's device answers to on its Wi-Fi: a hash of its family's id, so the name says nothing of who they are. */
    fun serviceName(family: String): String = "lani-" + hex(MessageDigest.getInstance("SHA-256").digest(family.toByteArray())).take(16)

    const val SERVICE_TYPE = "_lani-family._tcp."

    fun sealNote(k: Keys, n: FamilyNote): String =
        FamilyLink.seal(k.down, buildJsonObject { put("seq", n.seq); put("kind", n.kind); n.cap?.let { put("cap", it) } }.toString().toByteArray())

    /** What [sealNote] sealed; null for anything else, and for a kind this app doesn't know. */
    fun openNote(k: Keys, sealed: String): FamilyNote? = runCatching {
        val o = json.parseToJsonElement(FamilyLink.open(k.down, sealed)!!.decodeToString()).jsonObject
        val kind = o["kind"]!!.jsonPrimitive.content
        FamilyNote(o["seq"]!!.jsonPrimitive.content.toLong(), kind, o["cap"]?.jsonPrimitive?.contentOrNull?.toDouble()).takeIf { kind == FamilyNote.BUDGET || kind == FamilyNote.FORGET }
    }.getOrNull()

    /** What a device says: it has seen everything up to [seen]. */
    fun sealSeen(k: Keys, seen: Long): String = FamilyLink.seal(k.up, buildJsonObject { put("seen", seen) }.toString().toByteArray())

    fun openSeen(k: Keys, sealed: String): Long? = runCatching {
        json.parseToJsonElement(FamilyLink.open(k.up, sealed)!!.decodeToString()).jsonObject["seen"]!!.jsonPrimitive.content.toLong()
    }.getOrNull()

    /** A line, by [until]: a read's timeout starts again with every byte, so someone sending one now and then would hold the office. */
    private fun BufferedReader.line(until: Long): String? {
        val b = StringBuilder()
        while (b.length < MAX_LINE) {
            val c = read()
            if (c < 0 || System.currentTimeMillis() > until) return null
            if (c == '\n'.code) return b.toString()
            b.append(c.toChar())
        }
        return null
    }

    private fun Socket.say(line: String) {
        BufferedWriter(OutputStreamWriter(getOutputStream(), Charsets.UTF_8)).run {
            write(line)
            write("\n")
            flush()
        }
    }

    /** What the parent's side does with a device's word; [Office] holds no book of its own. */
    interface Desk {
        /** The keys of member [id]; null for anyone who isn't on the list. */
        fun keys(id: String): Keys?

        /** The member said it has seen up to [seen]; the note waiting for it now, if any. */
        fun heard(id: String, seen: Long): FamilyNote?
    }

    /**
     * The parent's device, answering on its Wi-Fi while it has something to say or the family's page is open. Each device comes
     * by, says what it has seen, gets what waits and says it has it. Anyone else gets a closed connection.
     */
    class Office(address: InetAddress?, private val desk: Desk, private val visitMs: Long = VISIT_MS) : Closeable {
        private val server = ServerSocket(0, 8, address)
        val port: Int get() = server.localPort

        /** Runs until [close]; meant for a background thread. */
        fun serve() {
            server.soTimeout = 1_000
            while (!server.isClosed) {
                val s = try {
                    server.accept()
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                } catch (_: IOException) {
                    return
                }
                runCatching { s.use(::visit) }
            }
        }

        private fun visit(s: Socket) {
            s.soTimeout = 5_000
            val until = System.currentTimeMillis() + visitMs
            val input = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val hello = json.parseToJsonElement(input.line(until).orEmpty()).jsonObject
            val id = hello["m"]?.jsonPrimitive?.contentOrNull ?: return
            val k = desk.keys(id) ?: return
            val seen = hello["s"]?.jsonPrimitive?.contentOrNull?.let { openSeen(k, it) } ?: return
            val note = desk.heard(id, seen)
            if (note == null) {
                s.say("""{"none":true}""")
                return
            }
            s.say(buildJsonObject { put("s", sealNote(k, note)) }.toString())
            val answer = json.parseToJsonElement(input.line(until).orEmpty()).jsonObject
            answer["s"]?.jsonPrimitive?.contentOrNull?.let { openSeen(k, it) }?.let { desk.heard(id, it) }
        }

        override fun close() {
            runCatching { server.close() }
        }
    }

    /**
     * The device's visit to the [Office] at [host]:[port]: it says it's [member] and has seen up to [seen], [take] gets the note
     * that waits, if any, and what it answers with goes back. False when nothing answered.
     */
    fun visit(host: String, port: Int, member: String, k: Keys, seen: Long, connectMs: Int = 3_000, take: (FamilyNote) -> Long): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), connectMs)
            s.soTimeout = 5_000
            s.say(buildJsonObject { put("v", VERSION); put("m", member); put("s", sealSeen(k, seen)) }.toString())
            val input = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val o = json.parseToJsonElement(input.line(System.currentTimeMillis() + VISIT_MS).orEmpty()).jsonObject
            o["s"]?.jsonPrimitive?.contentOrNull?.let { openNote(k, it) }?.let { note -> s.say(buildJsonObject { put("s", sealSeen(k, take(note))) }.toString()) }
            o.containsKey("s") || o.containsKey("none")
        }
    } catch (_: IOException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}
