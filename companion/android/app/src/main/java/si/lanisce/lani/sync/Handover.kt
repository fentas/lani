package si.lanisce.lani.sync

import kotlinx.serialization.Serializable
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.security.GeneralSecurityException
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * What the handover's QR code carries, opened (companion/bridge/src/handover.ts HandoverBundle): the learner's [profile]
 * and name, the node's host name, the town's key, the family page's token, and the data repository on GitHub.
 */
@Serializable
data class HandoverBundle(
    val v: Int = 1,
    val p: String = "default",
    val n: String = "",
    val h: String = "",
    val k: String,
    val t: String? = null,
    val r: String? = null,
    val b: String? = null,
)

/** The town's secrets from a handover, opened and checked: its key's fingerprint is the one the QR code names. */
data class HandoverSecrets(val bundle: HandoverBundle, val townKey: P256Key) {
    val repo: RepoRef? get() = bundle.r?.let { RepoRef.parse(it, bundle.b ?: "main") }
    val familyToken: String? get() = bundle.t?.takeIf { it.isNotBlank() }
}

/** A scanned handover code, not opened yet: [needsCode] when it waits for the code typed on the phone. */
class HandoverCode(val fingerprint: String, val expiresAt: Instant, private val sealed: ByteArray, private val key: ByteArray?, private val salt: ByteArray?) {
    val needsCode: Boolean get() = key == null

    /** Opens it ([code]: the one the node printed, when [needsCode]). */
    fun open(code: String? = null, now: Instant = Instant.now()): HandoverOpen {
        if (now.isAfter(expiresAt)) return HandoverOpen.Failed(HandoverProblem.EXPIRED)
        val k = key ?: run {
            val c = Handover.normalizeCode(code.orEmpty())
            if (!Handover.CODE.matches(c)) return HandoverOpen.Failed(HandoverProblem.WRONG_CODE)
            Handover.pbkdf2(c.toByteArray(), salt!!, Handover.CODE_ITERATIONS)
        }
        if (k.size != 32 || sealed.size < 12 + 16) return HandoverOpen.Failed(HandoverProblem.BROKEN)
        val plain = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, sealed, 0, 12))
                updateAAD("lani-handover/${Handover.VERSION}\n$fingerprint\n${expiresAt.epochSecond}".toByteArray())
                doFinal(sealed, 12, sealed.size - 12)
            }
        } catch (e: GeneralSecurityException) {
            return HandoverOpen.Failed(if (needsCode) HandoverProblem.WRONG_CODE else HandoverProblem.BROKEN)
        }
        val bundle = runCatching { syncRead.decodeFromString(HandoverBundle.serializer(), plain.decodeToString()) }.getOrNull()
            ?: return HandoverOpen.Failed(HandoverProblem.BROKEN)
        val townKey = runCatching { P256Key.ofScalar(Base64.getUrlDecoder().decode(bundle.k)) }.getOrNull()
            ?: return HandoverOpen.Failed(HandoverProblem.BROKEN)
        if (townKey.fingerprint != fingerprint) return HandoverOpen.Failed(HandoverProblem.NOT_THAT_TOWN)
        return HandoverOpen.Opened(HandoverSecrets(bundle, townKey))
    }
}

sealed interface HandoverOpen {
    data class Opened(val secrets: HandoverSecrets) : HandoverOpen
    data class Failed(val problem: HandoverProblem) : HandoverOpen
}

/** Why a handover code can't be used (shown with the l10n keys dataSync.handover.*). */
enum class HandoverProblem { NOT_HANDOVER, NEWER_VERSION, INCOMPLETE, EXPIRED, WRONG_CODE, BROKEN, NOT_THAT_TOWN, OTHER_TOWN }

sealed interface HandoverParse {
    data class Ok(val code: HandoverCode) : HandoverParse
    data class Invalid(val problem: HandoverProblem) : HandoverParse
}

/**
 * The handover's QR code, node → phone (docs/plans/05-built/git-sync.md, "The secrets: a QR code"):
 * `lani://handover?v=1&f=<town fingerprint>&x=<expires>&d=<nonce ‖ ciphertext ‖ tag>&k=<key>` (or `&s=<salt>`: the key
 * is PBKDF2-HMAC-SHA256 of the code the node printed). The node's side is companion/bridge/src/handover.ts.
 */
object Handover {
    const val VERSION = 1
    const val CODE_ITERATIONS = 200_000
    internal val CODE = Regex("^[A-HJ-NP-Z2-9]{12}$")
    private val FINGERPRINT = Regex("^[0-9a-f]{32}$")

    /** " k7q2-m9xa…" → "K7Q2M9XA…". */
    fun normalizeCode(c: String) = c.uppercase().replace(Regex("[\\s-]"), "")

    /** Whether [text] looks like a handover code at all (the scanner keeps looking otherwise). */
    fun looksLike(text: String) = text.trim().startsWith("lani://handover", ignoreCase = true)

    fun parse(scanned: String): HandoverParse {
        val text = scanned.trim()
        val uri = runCatching { URI(text) }.getOrNull()
            ?: return HandoverParse.Invalid(if (looksLike(text)) HandoverProblem.INCOMPLETE else HandoverProblem.NOT_HANDOVER)
        if (!uri.scheme.equals("lani", ignoreCase = true) || !uri.host.equals("handover", ignoreCase = true)) return HandoverParse.Invalid(HandoverProblem.NOT_HANDOVER)
        val q = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
            runCatching { URLDecoder.decode(part.substringBefore('='), "UTF-8") to URLDecoder.decode(part.substringAfter('=', ""), "UTF-8") }.getOrNull()
        }.toMap()
        val v = q["v"]?.toIntOrNull() ?: return HandoverParse.Invalid(HandoverProblem.INCOMPLETE)
        if (v > VERSION) return HandoverParse.Invalid(HandoverProblem.NEWER_VERSION)
        val f = q["f"]?.lowercase()?.takeIf(FINGERPRINT::matches) ?: return HandoverParse.Invalid(HandoverProblem.INCOMPLETE)
        val x = q["x"]?.toLongOrNull() ?: return HandoverParse.Invalid(HandoverProblem.INCOMPLETE)
        val d = q["d"]?.let(::b64url) ?: return HandoverParse.Invalid(HandoverProblem.INCOMPLETE)
        val k = q["k"]?.let(::b64url)
        val s = q["s"]?.let(::b64url)
        if ((k == null) == (s == null)) return HandoverParse.Invalid(HandoverProblem.INCOMPLETE)
        return HandoverParse.Ok(HandoverCode(f, Instant.ofEpochSecond(x), d, k, s))
    }

    private fun b64url(s: String): ByteArray? = runCatching { Base64.getUrlDecoder().decode(s) }.getOrNull()

    /** PBKDF2-HMAC-SHA256 with a 32-byte result, written out (the platform's PBKDF2 providers differ in how they read a password). */
    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(password, "HmacSHA256")) }
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val t = u.copyOf()
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
        }
        return t
    }

    /**
     * Keeps [secrets] for the phone's bridge: the town's key as <data>/app/town-key.pem and the family token as
     * <data>/app/family-token (readable by the app only; the sync and the filter keep them out of the repository), and
     * a sealed copy in the Keystore ([box]). A phone that holds another town already keeps it unless [replace].
     * Returns the problem when it didn't.
     */
    suspend fun keep(dataDir: File, secrets: HandoverSecrets, box: SecretBox, replace: Boolean = false): HandoverProblem? {
        val app = File(dataDir, "app").apply { mkdirs() }
        val pem = File(app, "town-key.pem")
        if (pem.isFile && !replace) {
            val had = runCatching { P256Key.ofPkcs8(pemBytes(pem.readText())).fingerprint }.getOrNull()
            if (had != null && had != secrets.townKey.fingerprint) return HandoverProblem.OTHER_TOWN
        }
        writePrivate(pem, secrets.townKey.pem)
        secrets.familyToken?.let { writePrivate(File(app, "family-token"), "$it\n") }
        box.put(SecretBox.TOWN, syncJson.encodeToString(HandoverBundle.serializer(), secrets.bundle))
        return null
    }

    /** The fingerprint of the town key the phone holds (<data>/app/town-key.pem), or null. */
    fun townOf(dataDir: File): String? = File(dataDir, "app/town-key.pem").takeIf { it.isFile }
        ?.let { runCatching { P256Key.ofPkcs8(pemBytes(it.readText())).fingerprint }.getOrNull() }

    /** The town's key the phone holds, for signing (the backup's requests may use it). */
    fun townKey(dataDir: File): P256Key? = File(dataDir, "app/town-key.pem").takeIf { it.isFile }
        ?.let { runCatching { P256Key.ofPkcs8(pemBytes(it.readText())) }.getOrNull() }

    internal fun pemBytes(pem: String): ByteArray =
        Base64.getMimeDecoder().decode(pem.lines().filterNot { it.startsWith("-----") }.joinToString(""))

    private fun writePrivate(f: File, text: String) {
        val tmp = File(f.path + DataFilter.TEMP_SUFFIX)
        tmp.writeText(text)
        // the app's own files are its alone already; owner-only all the same, as the bridge writes them (0600)
        tmp.setReadable(false, false); tmp.setReadable(true, true)
        tmp.setWritable(false, false); tmp.setWritable(true, true)
        if (!tmp.renameTo(f)) throw java.io.IOException("could not write ${f.name}")
    }
}
