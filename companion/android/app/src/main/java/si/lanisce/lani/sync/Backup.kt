package si.lanisce.lani.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The encrypted backup's keys (docs/plans/05-built/git-sync.md, "The encrypted backup"). Everything comes from one master
 * secret, which the phone keeps sealed in the Keystore ([SecretBox.BACKUP_MASTER]) and the learner keeps as a recovery
 * code (shown once) or a passphrase:
 * - the AES-256-GCM key of each backup: HMAC(master, "lani-backup/v1 aes" ‖ the backup's salt);
 * - the key its requests to the relay are signed with: a P-256 key derived from the master ("lani-backup/v1 sign").
 *   The relay keeps a signing key's backups apart, so a new phone with the recovery code alone finds and opens them.
 */
object BackupKeys {
    private val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    /** The passphrase's stretching (PBKDF2-HMAC-SHA256), with a fixed salt: a new phone derives the same master. */
    const val PASSPHRASE_ITERATIONS = 600_000
    const val MIN_PASSPHRASE = 12

    /** A new recovery code: 160 random bits and a 20-bit check, as 9 groups of 4 letters ("K7Q2-M9XA-…"). */
    fun newRecoveryCode(random: SecureRandom = SecureRandom()): String {
        val raw = ByteArray(20).also(random::nextBytes)
        return group(base32(raw) + check(raw))
    }

    /** The master secret of a recovery code; null when it isn't one (a typo: the check doesn't match). */
    fun masterOfCode(code: String): ByteArray? {
        val c = code.uppercase().replace(Regex("[\\s-]"), "")
        if (c.length != 36 || c.any { it !in ALPHABET }) return null
        val raw = unbase32(c.substring(0, 32))
        if (check(raw) != c.substring(32)) return null
        return hmac(raw, "lani-backup/v1 master".toByteArray())
    }

    /** The master secret of a passphrase (at least [MIN_PASSPHRASE] characters; Unicode-normalized). */
    fun masterOfPassphrase(passphrase: String): ByteArray {
        val p = Normalizer.normalize(passphrase.trim(), Normalizer.Form.NFC)
        require(p.length >= MIN_PASSPHRASE) { "a passphrase of at least $MIN_PASSPHRASE characters" }
        return Handover.pbkdf2(p.toByteArray(), "lani-backup/v1 passphrase".toByteArray(), PASSPHRASE_ITERATIONS)
    }

    fun signingKey(master: ByteArray): P256Key = P256Key.derive(master, "lani-backup/v1 sign")

    internal fun aesKey(master: ByteArray, salt: ByteArray): ByteArray = hmac(master, "lani-backup/v1 aes".toByteArray() + salt)

    private fun hmac(key: ByteArray, data: ByteArray) = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }

    private fun check(raw: ByteArray): String {
        val h = MessageDigest.getInstance("SHA-256").digest(raw)
        val bits = ((h[0].toInt() and 0xff) shl 12) or ((h[1].toInt() and 0xff) shl 4) or ((h[2].toInt() and 0xff) shr 4)
        return (0 until 4).joinToString("") { ALPHABET[(bits shr (15 - it * 5)) and 31].toString() }
    }

    private fun base32(b: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (x in b) {
            buffer = (buffer shl 8) or (x.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    private fun unbase32(s: String): ByteArray {
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in s) {
            buffer = (buffer shl 5) or ALPHABET.indexOf(c)
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    private fun group(s: String) = s.chunked(4).joinToString("-")
}

/**
 * A backup's bytes: "LANIBAK1", a 16-byte salt and a 12-byte nonce (the header, also the additional data), then the
 * archive under AES-256-GCM with its tag. The relay holds only this.
 */
object BackupBox {
    private val MAGIC = "LANIBAK1".toByteArray()
    private const val HEADER = 8 + 16 + 12

    fun seal(master: ByteArray, plain: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = MAGIC + salt + nonce
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(BackupKeys.aesKey(master, salt), "AES"), GCMParameterSpec(128, nonce))
            updateAAD(header)
        }
        return header + c.doFinal(plain)
    }

    /** The archive back; [BackupError] when it isn't a backup or [master] isn't its key. */
    fun open(master: ByteArray, sealed: ByteArray): ByteArray {
        if (sealed.size < HEADER + 16 || !sealed.copyOf(8).contentEquals(MAGIC)) throw BackupError(BackupProblem.NOT_A_BACKUP)
        val salt = sealed.copyOfRange(8, 24)
        val nonce = sealed.copyOfRange(24, HEADER)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(BackupKeys.aesKey(master, salt), "AES"), GCMParameterSpec(128, nonce))
                updateAAD(sealed.copyOf(HEADER))
                doFinal(sealed, HEADER, sealed.size - HEADER)
            }
        } catch (e: GeneralSecurityException) {
            throw BackupError(BackupProblem.WRONG_KEY)
        }
    }
}

enum class BackupProblem { NOT_SET_UP, NO_RELAY, TOO_LARGE, NOT_A_BACKUP, WRONG_KEY, NONE_FOUND, UNREACHABLE, REFUSED, BROKEN }

class BackupError(val problem: BackupProblem, detail: String? = null) : IOException(detail ?: problem.name)

/** What a backup's archive says about itself (manifest.json in it). */
@Serializable
data class BackupManifest(
    val schema: String = SCHEMA,
    val created: String,
    val app: String,
    val device: String,
    val files: Int,
    val bytes: Long,
    val town: String? = null,
) {
    companion object {
        const val SCHEMA = "lani.backup/v1"
    }
}

/**
 * The data directory as a zip: manifest.json and data/<path> for each file but the caches (the voice clips, the APKs),
 * the developer's data, snapshots, temporary files and the sync's. The secrets go in too (the town's key, the family
 * token): the archive is only ever stored encrypted, and a restore brings the town back.
 */
object BackupArchive {
    private val LEFT_OUT = DataFilter(DataFilter.parse(
        """
        /app/voice/
        /app/release/
        *.apk
        /app/dev/
        /.backups/
        *.tmp
        *.part
        *.backup-*
        """.trimIndent(),
    ))

    fun zip(dataDir: File, about: (files: Int, bytes: Long) -> BackupManifest): ByteArray {
        val files = mutableListOf<Pair<String, File>>()
        if (dataDir.isDirectory) Files.walk(dataDir.toPath()).use { paths ->
            for (p in paths) {
                if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) continue
                val rel = dataDir.toPath().relativize(p).joinToString("/")
                if (rel.split('/').any { it == ".git" } || rel.endsWith(DataFilter.TEMP_SUFFIX) || LEFT_OUT.ignored(rel)) continue
                files += rel to p.toFile()
            }
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            val manifest = about(files.size, files.sumOf { it.second.length() })
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write(syncJson.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            z.closeEntry()
            for ((rel, f) in files.sortedBy { it.first }) {
                z.putNextEntry(ZipEntry("data/$rel").apply { time = f.lastModified() })
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Unpacks [zip] into [to] (which must not exist yet): its manifest, checked; entries that would leave [to] refused. */
    fun unzip(zip: ByteArray, to: File): BackupManifest {
        var manifest: BackupManifest? = null
        val root = to.canonicalFile.path + File.separator
        to.mkdirs()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                if (e.name == "manifest.json") {
                    manifest = syncRead.decodeFromString(BackupManifest.serializer(), z.readBytes().decodeToString())
                    continue
                }
                val rel = e.name.removePrefix("data/")
                if (!e.name.startsWith("data/") || rel.split('/').any { it == ".." || it.isEmpty() }) throw BackupError(BackupProblem.BROKEN, "a file outside the data: ${e.name}")
                val f = File(to, rel)
                if (!f.canonicalFile.path.startsWith(root)) throw BackupError(BackupProblem.BROKEN, "a file outside the data: ${e.name}")
                f.parentFile?.mkdirs()
                f.outputStream().use { z.copyTo(it) }
                if (e.time > 0) f.setLastModified(e.time)
            }
        }
        val m = manifest ?: throw BackupError(BackupProblem.BROKEN, "no manifest")
        if (!m.schema.startsWith("lani.backup/")) throw BackupError(BackupProblem.BROKEN, "not a Lani backup: ${m.schema}")
        return m
    }
}

/** A backup on the relay: its [name], [size] in bytes and [time]. */
data class BackupEntry(val name: String, val size: Long, val time: Instant?)

/**
 * The relay's backup API (the shared contract, docs/plans/05-built/git-sync.md "The relay's API"), every request signed
 * with [key]: `X-Lani-Key` (its SubjectPublicKeyInfo DER, base64), `X-Lani-Time` (unix ms) and `X-Lani-Sig`, its
 * signature over "METHOD\npath?query\ntime\nhex(sha256(body))". The relay keeps each key's backups apart.
 */
class RelayClient(
    private val base: String,
    private val key: P256Key,
    private val http: OkHttpClient = DEFAULT_CLIENT,
    private val clock: () -> Instant = Instant::now,
) {
    suspend fun put(name: String, bytes: ByteArray) {
        if (bytes.size > MAX_BLOB) throw BackupError(BackupProblem.TOO_LARGE)
        call("PUT", "/v1/backup/$name", bytes)
    }

    suspend fun get(name: String): ByteArray = call("GET", "/v1/backup/$name")

    suspend fun delete(name: String) {
        call("DELETE", "/v1/backup/$name", expect = setOf(200, 204, 404))
    }

    /** The backups, newest first. The list's shape is read leniently: an array, or {backups|items: [...]}; a time in ms, s or ISO. */
    suspend fun list(): List<BackupEntry> {
        val e = syncRead.parseToJsonElement(call("GET", "/v1/backup").decodeToString())
        val items = (e as? JsonArray) ?: ((e as? JsonObject)?.let { it["backups"] ?: it["items"] } as? JsonArray) ?: JsonArray(emptyList())
        return items.mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val size = o["size"]?.jsonPrimitive?.longOrNull ?: 0
            val t = o["time"]?.jsonPrimitive
            val time = t?.longOrNull?.let { if (it > 100_000_000_000L) Instant.ofEpochMilli(it) else Instant.ofEpochSecond(it) }
                ?: t?.contentOrNull?.let { runCatching { Instant.parse(it) }.getOrNull() }
            BackupEntry(name, size, time)
        }.sortedWith(compareByDescending<BackupEntry> { it.time ?: Instant.EPOCH }.thenByDescending { it.name })
    }

    /** The headers that sign a request (the contract's): public for the tests, which check them as the relay does. */
    fun signed(method: String, pathWithQuery: String, body: ByteArray, time: Long = clock().toEpochMilli()): Map<String, String> {
        val hash = MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }
        val message = "$method\n$pathWithQuery\n$time\n$hash"
        return mapOf(
            "X-Lani-Key" to Base64.getEncoder().encodeToString(key.spki),
            "X-Lani-Time" to time.toString(),
            "X-Lani-Sig" to Base64.getEncoder().encodeToString(key.sign(message.toByteArray())),
        )
    }

    private suspend fun call(method: String, path: String, body: ByteArray? = null, expect: Set<Int> = setOf(200, 201, 204)): ByteArray = withContext(Dispatchers.IO) {
        val sent = (base.trimEnd('/') + path).toHttpUrl()
        val pathWithQuery = sent.encodedPath + (sent.encodedQuery?.let { "?$it" } ?: "")
        val req = Request.Builder().url(sent)
            .apply { signed(method, pathWithQuery, body ?: ByteArray(0)).forEach { (k, v) -> header(k, v) } }
            .method(method, body?.toRequestBody(BLOB))
            .build()
        val r = try {
            http.newCall(req).execute()
        } catch (e: IOException) {
            throw BackupError(BackupProblem.UNREACHABLE, e.message)
        }
        r.use {
            val bytes = it.body?.bytes() ?: ByteArray(0)
            when {
                it.code in expect -> bytes
                it.code == 404 -> throw BackupError(BackupProblem.NONE_FOUND)
                it.code == 413 -> throw BackupError(BackupProblem.TOO_LARGE)
                it.code == 401 || it.code == 403 -> throw BackupError(BackupProblem.REFUSED, bytes.decodeToString().take(200))
                else -> throw BackupError(BackupProblem.UNREACHABLE, "HTTP ${it.code}: ${bytes.decodeToString().take(200)}")
            }
        }
    }

    companion object {
        /** The contract's limit for one backup. */
        const val MAX_BLOB = 50 * 1024 * 1024
        private val BLOB = "application/octet-stream".toMediaType()
        val DEFAULT_CLIENT: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()
    }
}

/**
 * Backing up the phone's data to the relay and restoring it (docs/plans/05-built/git-sync.md, "The encrypted backup"):
 * [backup] zips <filesDir>/lani/data, seals it ([BackupBox]) and puts it as lani-<time>.lbk, keeping the newest [KEEP];
 * [restore] fetches one, opens it and puts it in place of the data directory, which is kept beside it first
 * (data.before-restore-<time>).
 */
class Backups(
    private val dataDir: File,
    private val relay: RelayClient,
    private val master: ByteArray,
    private val app: String,
    private val device: String,
    private val clock: () -> Instant = Instant::now,
) {
    suspend fun backup(): BackupEntry {
        val now = clock()
        val zip = withContext(Dispatchers.IO) {
            BackupArchive.zip(dataDir) { files, bytes -> BackupManifest(created = Lease.iso(now), app = app, device = device, files = files, bytes = bytes, town = Handover.townOf(dataDir)) }
        }
        val sealed = BackupBox.seal(master, zip)
        if (sealed.size > RelayClient.MAX_BLOB) throw BackupError(BackupProblem.TOO_LARGE, "${sealed.size / 1024 / 1024} MB")
        val name = "lani-${STAMP.format(now)}.lbk"
        relay.put(name, sealed)
        // the newest KEEP stay
        runCatching { relay.list().drop(KEEP).forEach { relay.delete(it.name) } }
        return BackupEntry(name, sealed.size.toLong(), now)
    }

    suspend fun list(): List<BackupEntry> = relay.list()

    /**
     * Restores [name] (the newest when null) into [dataDir]: the data there now is moved to data.before-restore-<time>
     * beside it first. Returns the backup's manifest.
     */
    suspend fun restore(name: String? = null): BackupManifest {
        val which = name ?: relay.list().firstOrNull()?.name ?: throw BackupError(BackupProblem.NONE_FOUND)
        val zip = BackupBox.open(master, relay.get(which))
        return withContext(Dispatchers.IO) {
            val parent = dataDir.absoluteFile.parentFile
            val tmp = File(parent, "data.restore-tmp")
            if (tmp.exists()) tmp.deleteRecursively()
            val manifest = BackupArchive.unzip(zip, tmp)
            if (dataDir.exists()) {
                val aside = File(parent, "data.before-restore-${STAMP.format(clock())}")
                if (!dataDir.renameTo(aside)) throw BackupError(BackupProblem.BROKEN, "could not keep the data there was")
            }
            if (!tmp.renameTo(dataDir)) throw BackupError(BackupProblem.BROKEN, "could not put the backup in place")
            manifest
        }
    }

    companion object {
        const val KEEP = 10
        private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    }
}
