package si.lanisce.lani.data

import kotlinx.serialization.json.JsonObject
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
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * "Add a family member" (docs/plans/05-built/first-minutes.md, "A family member from a phone that's set up"): a device that is
 * set up shows a QR code, a new device scans it and gets what the family shares, device to device over the Wi-Fi they are
 * both on, or through the relay when one is set ([Offer.relay], [RelayWay]; docs/plans/05-built/leftovers-app.md): the same
 * exchange, sealed the same way, so a relay carries bytes it can't open. On one Wi-Fi no relay, no node and no internet are
 * needed.
 *
 * The rules, because a key travels:
 * - The QR ([Offer.text]) holds where to connect, a one-time code and the sender's public key for this one exchange: never a
 *   provider's key. A photo of it is useless once it's used or expired: the listener is gone and the keys were only in memory.
 * - Both sides make new P-256 keys for each exchange. The session key is HKDF over their ECDH secret, salted with the
 *   one-time code, so only someone who has both the code and a private key of this exchange can read it. The new device
 *   proves it has the code before anything else ([proof]).
 * - One claim: the first device with a good proof claims the code, every other gets nothing ([Sender.claim]); after
 *   [TTL_MS] nobody does.
 * - Nothing goes before a parent has seen what will be shared and to which device, and held the button ([Sender.confirm]).
 * - The two keep a bond ([bondKey]): a key both can work out from this exchange and nobody else can, the channel for what a
 *   parent sends the device later ([FamilyPost]). It never crosses, so it's in no QR and in no frame.
 *
 * Pure but for [Listener] and [fetch], the sockets, and a [Drop]. Nothing here logs what it carries.
 */
object FamilyLink {
    const val VERSION = 1

    /** How long a code lasts: enough to find the other device and scan, short enough that a photo soon means nothing. */
    const val TTL_MS = 3 * 60_000L

    private const val CURVE = "secp256r1"
    private const val CODE_BYTES = 16
    private const val IV = 12
    private val random = SecureRandom()
    private val INFO = "lani-family-v1".toByteArray()

    private fun b64(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun unb64(s: String): ByteArray? = runCatching { Base64.getUrlDecoder().decode(s) }.getOrNull()

    /**
     * What the QR code says. [host] and [port]: where the sender listens (blank and 0 when it has no Wi-Fi and only the relay
     * reaches it); [code]: the one-time code; [publicKey]: its key for this exchange; [relay]: the relay both can meet at, if
     * one is set. An older app reads what it knows of it and leaves the relay.
     */
    data class Offer(val host: String, val port: Int, val code: String, val publicKey: String, val expires: Long, val relay: String? = null) {
        fun text(): String = "lani://family?v=$VERSION&h=${URLEncoder.encode(host, "UTF-8")}&p=$port&c=$code&k=$publicKey&e=$expires" +
            (relay?.let { "&r=${URLEncoder.encode(it, "UTF-8")}" } ?: "")
    }

    /** [text] as an [Offer]; null when it isn't one of ours, or of a version this app doesn't read. */
    fun parse(text: String?): Offer? {
        val t = text?.trim() ?: return null
        if (!t.startsWith("lani://family?")) return null
        val q = t.substringAfter('?').split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
        if (q["v"] != VERSION.toString()) return null
        val expires = q["e"]?.toLongOrNull() ?: return null
        val code = q["c"]?.takeIf { unb64(it)?.size == CODE_BYTES } ?: return null
        val key = q["k"]?.takeIf { unb64(it) != null } ?: return null
        val relay = q["r"]?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }?.takeIf(::relayLike)
        val host = q["h"]?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }.orEmpty()
        val port = q["p"]?.toIntOrNull() ?: return null
        // somewhere to go: an address on the Wi-Fi, or only the relay
        if (host.isBlank() && relay == null) return null
        if (host.isNotBlank() && port !in 1..65535) return null
        return Offer(host, if (host.isBlank()) 0 else port, code, key, expires, relay)
    }

    /** An http(s) address with nothing but a host and a path, as the relay's setting is. */
    private fun relayLike(url: String): Boolean = Regex("^https?://[^\\s/?#@]+(/[^\\s?#]*)?$").matches(url)

    fun newKeys(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec(CURVE), random) }.generateKeyPair()

    fun newCode(): String = b64(ByteArray(CODE_BYTES).also(random::nextBytes))

    fun publicOf(k: KeyPair): String = b64(k.public.encoded)

    private fun publicKey(s: String): PublicKey? = runCatching { KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64(s))) }.getOrNull()

    private fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        parts.forEach(::update)
        doFinal()
    }

    /** That whoever sends [publicKey] has [code]: only the QR's holder can make it. */
    fun proof(code: String, publicKey: String): String = b64(hmac(unb64(code) ?: ByteArray(0), "proof".toByteArray(), publicKey.toByteArray()))

    /** The session's key: HKDF-SHA256 over the ECDH secret of [mine] and [theirs], salted with [code]; null for a bad public key. */
    fun sessionKey(mine: KeyPair, theirs: String, code: String): ByteArray? {
        val other = publicKey(theirs) ?: return null
        val shared = runCatching { KeyAgreement.getInstance("ECDH").run { init(mine.private); doPhase(other, true); generateSecret() } }.getOrNull() ?: return null
        val prk = hmac(unb64(code) ?: return null, shared)
        return hmac(prk, INFO, byteArrayOf(1))
    }

    /**
     * What the sender and the device keep after the exchange: a key from this exchange's session key, which only those two can
     * work out and which never crosses. It opens the channel for what a parent sends later ([FamilyPost]).
     */
    fun bondKey(session: ByteArray): ByteArray = hmac(session, "lani-family-bond-v1".toByteArray())

    /** [plain] sealed with AES-GCM under [key]: its 12-byte IV, then the ciphertext and its tag; base64. */
    fun seal(key: ByteArray, plain: ByteArray): String {
        val iv = ByteArray(IV).also(random::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)) }
        return b64(iv + c.doFinal(plain))
    }

    /** What [seal] sealed; null for anything else (the wrong key, a byte changed). */
    fun open(key: ByteArray, sealed: String): ByteArray? = runCatching {
        val b = unb64(sealed)!!
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, b.copyOfRange(0, IV))) }.doFinal(b, IV, b.size - IV)
    }.getOrNull()

    /**
     * What the family shares ([json], sealed in transit): the tutor's [provider], its [key] and [preset] (a ChatGPT sign-in is one
     * person's and never comes), the languages as a suggestion. Learning progress, names and notebooks never do. [family],
     * [member] and [from]: who the new device is in the sender's list, and what it calls the sender ([FamilyBook]); an
     * older sender has none of them, and an older device leaves them.
     */
    data class Gift(
        val provider: String? = null, val key: String? = null, val preset: String? = null, val target: String? = null, val base: String? = null,
        val family: String? = null, val member: String? = null, val from: String? = null,
    ) {
        fun json(): String = buildJsonObject {
            provider?.let { put("provider", it) }
            key?.let { put("key", it) }
            preset?.let { put("preset", it) }
            target?.let { put("target", it) }
            base?.let { put("base", it) }
            family?.let { put("family", it) }
            member?.let { put("member", it) }
            from?.let { put("from", it) }
        }.toString()

        /** Never the key itself: a [Gift] printed by accident stays harmless. */
        override fun toString(): String = "Gift(provider=$provider, key=${if (key != null) "‹key›" else "none"}, preset=$preset, target=$target, base=$base, member=$member)"

        companion object {
            fun parse(raw: String): Gift? = runCatching {
                val o = json.parseToJsonElement(raw).jsonObject
                fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull
                Gift(s("provider"), s("key"), s("preset"), s("target"), s("base"), s("family"), s("member"), s("from"))
            }.getOrNull()
        }
    }

    /** The sending device's side of one exchange: the state, without the sockets. Its keys live and die with it. */
    class Sender(host: String, port: Int, now: Long, private val keys: KeyPair = newKeys(), private val code: String = newCode(), relay: String? = null) {
        val offer = Offer(host, port, code, publicOf(keys), now + TTL_MS, relay)

        enum class State { WAITING, CLAIMED, SENT, REFUSED, EXPIRED }

        var state = State.WAITING
            private set

        /** The device that claimed the code, as it names itself: what the parent is shown. */
        var device: String? = null
            private set
        private var session: ByteArray? = null

        /** The key kept with the device once the gift is sealed ([bondKey]); null before that, and after a no. */
        var bond: ByteArray? = null
            private set

        /**
         * A new device with [publicKey], named [device], proving the code by [proof]: true when it's the one. A used or expired
         * code, or a wrong proof, gets nothing, and a wrong proof doesn't spend it.
         */
        @Synchronized
        fun claim(publicKey: String, proof: String, device: String, now: Long): Boolean {
            expire(now)
            if (state != State.WAITING) return false
            if (!MessageDigest.isEqual(proof.toByteArray(), proof(code, publicKey).toByteArray())) return false
            session = sessionKey(keys, publicKey, code) ?: return false
            this.device = device.take(MAX_DEVICE)
            state = State.CLAIMED
            return true
        }

        /** The parent held the button: [gift] sealed for the device that claimed the code, once. Null when there's none (expired, refused). */
        @Synchronized
        fun confirm(gift: Gift, now: Long): String? {
            expire(now)
            val k = session
            if (state != State.CLAIMED || k == null) return null
            state = State.SENT
            session = null
            bond = bondKey(k)
            return seal(k, gift.json().toByteArray())
        }

        /** The parent said no, or left. */
        @Synchronized
        fun refuse() {
            if (state != State.SENT) state = State.REFUSED
            session = null
        }

        /** Past its time nothing more goes: the listener closes. */
        @Synchronized
        fun expire(now: Long) {
            if (now >= offer.expires && state in setOf(State.WAITING, State.CLAIMED)) {
                state = State.EXPIRED
                session = null
            }
        }
    }

    private const val MAX_DEVICE = 60
    private const val MAX_LINE = 16 * 1024

    private fun BufferedReader.line(): String? {
        val b = StringBuilder()
        while (b.length < MAX_LINE) {
            val c = read()
            if (c < 0) return null
            if (c == '\n'.code) return b.toString()
            b.append(c.toChar())
        }
        return null
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull

    /**
     * A way a claim reaches the sender, for one exchange: [serve] waits for a good claim and tells [onClaimed], then [send] takes
     * the parent's decision back the way the claim came. It ends with [close], after its exchange, or at the expiry.
     */
    interface Way : Closeable {
        /** Runs until the code is claimed or closed; meant for a background thread. */
        fun serve(sender: Sender, onClaimed: (String) -> Unit)

        /** The parent decided: [sealed] to the device that waits, or no ([sealed] null). */
        fun send(sealed: String?)
    }

    /**
     * The sender's listener on [address]: it holds a claim's connection until the parent decides ([Sender.confirm] or
     * [Sender.refuse]: [send]). Bad claims are answered and dropped.
     */
    class Listener(address: InetAddress?, private val clock: () -> Long = System::currentTimeMillis) : Way {
        private val server = ServerSocket(0, 4, address)
        val port: Int get() = server.localPort
        @Volatile private var claimed: Socket? = null

        override fun serve(sender: Sender, onClaimed: (String) -> Unit) {
            server.soTimeout = 1_000
            while (!server.isClosed) {
                sender.expire(clock())
                if (sender.state != Sender.State.WAITING) return
                val s = try {
                    server.accept()
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                } catch (_: IOException) {
                    return
                }
                runCatching {
                    s.soTimeout = 10_000
                    val line = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).line()
                    val o = json.parseToJsonElement(line.orEmpty()).jsonObject
                    if (sender.claim(o.str("pub").orEmpty(), o.str("proof").orEmpty(), o.str("device").orEmpty(), clock())) {
                        claimed = s
                        onClaimed(sender.device.orEmpty())
                    } else {
                        s.reply("""{"refused":true}""")
                        s.close()
                    }
                }.onFailure { runCatching { s.close() } }
                if (claimed != null) return
            }
        }

        override fun send(sealed: String?) {
            val s = claimed ?: return
            claimed = null
            runCatching { s.reply(if (sealed != null) buildJsonObject { put("sealed", sealed) }.toString() else """{"refused":true}""") }
            runCatching { s.close() }
        }

        private fun Socket.reply(line: String) {
            BufferedWriter(OutputStreamWriter(getOutputStream(), Charsets.UTF_8)).run {
                write(line)
                write("\n")
                flush()
            }
        }

        override fun close() {
            send(null)
            runCatching { server.close() }
        }
    }

    /**
     * Where two devices meet when they aren't on one Wi-Fi: boxes on the relay, each named by a hash only the two can work out
     * (companion/relay/src/drop.ts). [put] leaves a note for [ttlS] seconds; [take] takes what waits in [box], waiting up to
     * [waitS] seconds for something to come. Either throws an [IOException] when the relay can't be reached.
     */
    interface Drop {
        fun put(box: String, note: String, ttlS: Int)
        fun take(box: String, waitS: Int): List<String>
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun fromCode(code: String, what: String, vararg more: ByteArray): ByteArray = hmac(unb64(code) ?: ByteArray(0), what.toByteArray(), *more)

    /** The box claims come to, and the key they're sealed under: the relay reads neither a public key nor a device's name. */
    internal fun claimBox(code: String): String = hex(fromCode(code, "claim-box"))
    private fun claimKey(code: String): ByteArray = fromCode(code, "claim-key")

    /** The box the answer to the claim of [publicKey] comes to. */
    internal fun giftBox(code: String, publicKey: String): String = hex(fromCode(code, "gift-box", publicKey.toByteArray()))

    private const val REFUSED = "refused"
    private const val DROP_POLL_S = 10

    private fun claimLine(pub: String, code: String, device: String) =
        buildJsonObject { put("v", VERSION); put("pub", pub); put("proof", proof(code, pub)); put("device", device.take(MAX_DEVICE)) }.toString()

    /**
     * The same exchange through a relay ([drop]): a claim comes sealed under a key from the code, the answer goes back sealed under
     * the session's key, or a "no" under the code's. Each exchange has boxes of its own, so a used code's are empty.
     */
    class RelayWay(private val drop: Drop, private val offer: Offer, private val clock: () -> Long = System::currentTimeMillis) : Way {
        @Volatile private var closed = false
        @Volatile private var claimant: String? = null

        override fun serve(sender: Sender, onClaimed: (String) -> Unit) {
            val box = claimBox(offer.code)
            val key = claimKey(offer.code)
            while (!closed) {
                sender.expire(clock())
                if (sender.state != Sender.State.WAITING) return
                val notes = try {
                    drop.take(box, DROP_POLL_S)
                } catch (_: IOException) {
                    runCatching { Thread.sleep(2_000) }
                    continue
                }
                for (note in notes) {
                    if (closed) return
                    val o = open(key, note)?.let { runCatching { json.parseToJsonElement(it.decodeToString()).jsonObject }.getOrNull() } ?: continue
                    val pub = o.str("pub").orEmpty()
                    if (sender.claim(pub, o.str("proof").orEmpty(), o.str("device").orEmpty(), clock())) {
                        claimant = pub
                        onClaimed(sender.device.orEmpty())
                        return
                    }
                    // a code that's used or ran out says so; a wrong proof proved nothing and gets nothing
                    if (o.str("proof") == proof(offer.code, pub)) runCatching { drop.put(giftBox(offer.code, pub), seal(key, REFUSED.toByteArray()), ANSWER_TTL_S) }
                }
            }
        }

        override fun send(sealed: String?) {
            val pub = claimant ?: return
            claimant = null
            runCatching { drop.put(giftBox(offer.code, pub), sealed ?: seal(claimKey(offer.code), REFUSED.toByteArray()), ANSWER_TTL_S) }
        }

        override fun close() {
            closed = true
            send(null)
        }
    }

    /** How long a note of an exchange waits on the relay: the code's own time, and no longer. */
    private const val ANSWER_TTL_S = (TTL_MS / 1000).toInt()

    /** How a fetch ended, for the new device's words. */
    sealed interface Fetched {
        /** [bond]: the key kept with the sender ([bondKey]). */
        class Got(val gift: Gift, val bond: ByteArray) : Fetched
        /** Used already, expired or not ours: the code gives nothing. */
        data object Refused : Fetched
        /** Nothing answers there: not on the same Wi-Fi, or the sender left. */
        data object Unreachable : Fetched
    }

    /**
     * The new device's side: [offer] scanned, its proof and its own public key sent, then it waits while the parent looks ([waitMs]);
     * the gift opened with the session's key. Over the Wi-Fi when the sender answers there; through the relay ([drop]) when it
     * doesn't and the offer names one. Only a failed connection goes on to the relay: once a claim has gone, the code is taken.
     */
    fun fetch(offer: Offer, device: String, now: Long = System.currentTimeMillis(), waitMs: Int = TTL_MS.toInt(), keys: KeyPair = newKeys(), connectMs: Int = 5_000, drop: Drop? = null): Fetched {
        if (now >= offer.expires) return Fetched.Refused
        val session = sessionKey(keys, offer.publicKey, offer.code) ?: return Fetched.Refused
        val pub = publicOf(keys)
        val viaRelay = { if (drop != null && offer.relay != null) fetchRelay(offer, drop, session, pub, device, waitMs) else Fetched.Unreachable }
        if (offer.host.isBlank()) return viaRelay()
        return try {
            Socket().use { s ->
                try {
                    s.connect(InetSocketAddress(offer.host, offer.port), connectMs)
                } catch (_: IOException) {
                    return viaRelay()
                }
                s.soTimeout = waitMs
                val out = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
                out.write(claimLine(pub, offer.code, device))
                out.write("\n")
                out.flush()
                val o = json.parseToJsonElement(BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).line().orEmpty()).jsonObject
                val plain = o.str("sealed")?.let { open(session, it) } ?: return Fetched.Refused
                Gift.parse(plain.decodeToString())?.let { Fetched.Got(it, bondKey(session)) } ?: Fetched.Refused
            }
        } catch (_: IOException) {
            Fetched.Unreachable
        } catch (_: IllegalArgumentException) {
            Fetched.Refused
        }
    }

    /** [fetch] through the relay: the claim left in its box, then the answer waited for, up to [waitMs]. */
    private fun fetchRelay(offer: Offer, drop: Drop, session: ByteArray, pub: String, device: String, waitMs: Int, clock: () -> Long = System::currentTimeMillis): Fetched {
        val key = claimKey(offer.code)
        val box = giftBox(offer.code, pub)
        return try {
            drop.put(claimBox(offer.code), seal(key, claimLine(pub, offer.code, device).toByteArray()), ANSWER_TTL_S)
            // a used code's boxes stay empty: past the code's own time nothing can come
            val until = minOf(clock() + waitMs, offer.expires)
            while (clock() < until) {
                for (note in drop.take(box, DROP_POLL_S)) {
                    open(session, note)?.let { plain -> return Gift.parse(plain.decodeToString())?.let { Fetched.Got(it, bondKey(session)) } ?: Fetched.Refused }
                    if (open(key, note)?.decodeToString() == REFUSED) return Fetched.Refused
                }
            }
            Fetched.Refused
        } catch (_: IOException) {
            Fetched.Unreachable
        }
    }
}
