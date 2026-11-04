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
 * both on. No relay, no node and no internet are needed; the relay isn't hosted yet, and a family on "just this device"
 * has no other place the two could meet.
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
 *
 * Pure but for [Listener] and [fetch], the sockets. Nothing here logs what it carries.
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

    /** What the QR code says. [host] and [port]: where the sender listens; [code]: the one-time code; [publicKey]: its key for this exchange. */
    data class Offer(val host: String, val port: Int, val code: String, val publicKey: String, val expires: Long) {
        fun text(): String = "lani://family?v=$VERSION&h=${URLEncoder.encode(host, "UTF-8")}&p=$port&c=$code&k=$publicKey&e=$expires"
    }

    /** [text] as an [Offer]; null when it isn't one of ours, or of a version this app doesn't read. */
    fun parse(text: String?): Offer? {
        val t = text?.trim() ?: return null
        if (!t.startsWith("lani://family?")) return null
        val q = t.substringAfter('?').split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
        if (q["v"] != VERSION.toString()) return null
        val port = q["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val expires = q["e"]?.toLongOrNull() ?: return null
        val code = q["c"]?.takeIf { unb64(it)?.size == CODE_BYTES } ?: return null
        val key = q["k"]?.takeIf { unb64(it) != null } ?: return null
        val host = q["h"]?.let { URLDecoder.decode(it, "UTF-8") }?.takeIf { it.isNotBlank() } ?: return null
        return Offer(host, port, code, key, expires)
    }

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
     * person's and never comes), the languages as a suggestion. Learning progress, names and notebooks never do.
     */
    data class Gift(val provider: String? = null, val key: String? = null, val preset: String? = null, val target: String? = null, val base: String? = null) {
        fun json(): String = buildJsonObject {
            provider?.let { put("provider", it) }
            key?.let { put("key", it) }
            preset?.let { put("preset", it) }
            target?.let { put("target", it) }
            base?.let { put("base", it) }
        }.toString()

        /** Never the key itself: a [Gift] printed by accident stays harmless. */
        override fun toString(): String = "Gift(provider=$provider, key=${if (key != null) "‹key›" else "none"}, preset=$preset, target=$target, base=$base)"

        companion object {
            fun parse(raw: String): Gift? = runCatching {
                val o = json.parseToJsonElement(raw).jsonObject
                fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull
                Gift(s("provider"), s("key"), s("preset"), s("target"), s("base"))
            }.getOrNull()
        }
    }

    /** The sending device's side of one exchange: the state, without the sockets. Its keys live and die with it. */
    class Sender(host: String, port: Int, now: Long, private val keys: KeyPair = newKeys(), private val code: String = newCode()) {
        val offer = Offer(host, port, code, publicOf(keys), now + TTL_MS)

        enum class State { WAITING, CLAIMED, SENT, REFUSED, EXPIRED }

        var state = State.WAITING
            private set

        /** The device that claimed the code, as it names itself: what the parent is shown. */
        var device: String? = null
            private set
        private var session: ByteArray? = null

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
     * The sender's listener on [address], for one exchange: it waits for a good claim, tells [onClaimed], and holds that
     * connection until the parent decides ([Sender.confirm] or [Sender.refuse]: [send]). Bad claims are answered and dropped.
     * It closes with [close], after its exchange, or at the expiry.
     */
    class Listener(address: InetAddress?, private val clock: () -> Long = System::currentTimeMillis) : Closeable {
        private val server = ServerSocket(0, 4, address)
        val port: Int get() = server.localPort
        @Volatile private var claimed: Socket? = null

        /** Runs until the code is claimed or closed; meant for a background thread. */
        fun serve(sender: Sender, onClaimed: (String) -> Unit) {
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

        /** The parent decided: [sealed] to the device that waits, or no ([sealed] null). */
        fun send(sealed: String?) {
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

    /** How a fetch ended, for the new device's words. */
    sealed interface Fetched {
        data class Got(val gift: Gift) : Fetched
        /** Used already, expired or not ours: the code gives nothing. */
        data object Refused : Fetched
        /** Nothing answers there: not on the same Wi-Fi, or the sender left. */
        data object Unreachable : Fetched
    }

    /**
     * The new device's side: [offer] scanned, its proof and its own public key sent, then it waits while the parent looks ([waitMs]);
     * the gift opened with the session's key.
     */
    fun fetch(offer: Offer, device: String, now: Long = System.currentTimeMillis(), waitMs: Int = TTL_MS.toInt(), keys: KeyPair = newKeys(), connectMs: Int = 5_000): Fetched {
        if (now >= offer.expires) return Fetched.Refused
        val session = sessionKey(keys, offer.publicKey, offer.code) ?: return Fetched.Refused
        val pub = publicOf(keys)
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(offer.host, offer.port), connectMs)
                s.soTimeout = waitMs
                val out = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
                out.write(buildJsonObject { put("v", VERSION); put("pub", pub); put("proof", proof(offer.code, pub)); put("device", device.take(MAX_DEVICE)) }.toString())
                out.write("\n")
                out.flush()
                val o = json.parseToJsonElement(BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).line().orEmpty()).jsonObject
                val plain = o.str("sealed")?.let { open(session, it) } ?: return Fetched.Refused
                Gift.parse(plain.decodeToString())?.let { Fetched.Got(it) } ?: Fetched.Refused
            }
        } catch (_: IOException) {
            Fetched.Unreachable
        } catch (_: IllegalArgumentException) {
            Fetched.Refused
        }
    }
}
