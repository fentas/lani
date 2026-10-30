package si.lanisce.lani.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The relay's backup API as the shared contract says (docs/plans/05-built/git-sync.md "The relay's API"): every request
 * signed (X-Lani-Key, X-Lani-Time within ±5 minutes, X-Lani-Sig over "METHOD\npath?query\ntime\nhex(sha256(body))"),
 * replays refused, each key's backups apart, at most 50 MB. The relay agent's real one replaces it.
 */
class FakeRelay : Dispatcher() {
    val server = MockWebServer().also { it.dispatcher = this }
    val url: String get() = server.url("/").toString().trimEnd('/')
    /** key (base64 SPKI) → name → (bytes, time ms) */
    val store = HashMap<String, LinkedHashMap<String, Pair<ByteArray, Long>>>()
    var now: () -> Long = System::currentTimeMillis
    private val seen = HashSet<String>()
    val refused = mutableListOf<String>()

    private fun no(code: Int, why: String) = MockResponse().setResponseCode(code).setBody("""{"error":"$why"}""").also { refused += why }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val key = request.getHeader("X-Lani-Key") ?: return no(401, "unsigned")
        val time = request.getHeader("X-Lani-Time")?.toLongOrNull() ?: return no(401, "unsigned")
        val sig = request.getHeader("X-Lani-Sig") ?: return no(401, "unsigned")
        if (kotlin.math.abs(now() - time) > 5 * 60_000) return no(401, "stale")
        val body = request.body.readByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }
        val message = "${request.method}\n${request.path}\n$time\n$hash"
        val ok = runCatching {
            val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key)))
            Signature.getInstance("SHA256withECDSA").run { initVerify(pub); update(message.toByteArray()); verify(Base64.getDecoder().decode(sig)) }
        }.getOrDefault(false)
        if (!ok) return no(401, "bad_signature")
        if (!seen.add("$key $time $sig")) return no(401, "replayed")
        val mine = store.getOrPut(key) { LinkedHashMap() }
        val path = request.path!!.substringBefore('?')
        return when {
            request.method == "GET" && path == "/v1/backup" -> MockResponse().setBody(buildJsonObject {
                put("backups", buildJsonArray { mine.forEach { (n, v) -> add(buildJsonObject { put("name", n); put("size", v.first.size); put("time", v.second) }) } })
            }.toString())
            path.startsWith("/v1/backup/") -> {
                val name = path.removePrefix("/v1/backup/")
                when (request.method) {
                    "PUT" -> if (body.size > 50 * 1024 * 1024) no(413, "too_large") else { mine[name] = body to now(); MockResponse().setResponseCode(201).setBody("{}") }
                    "GET" -> mine[name]?.let { MockResponse().setBody(okio.Buffer().write(it.first)) } ?: no(404, "not_found")
                    "DELETE" -> { mine.remove(name); MockResponse().setResponseCode(204) }
                    else -> no(405, "method")
                }
            }
            else -> no(404, "not_found")
        }
    }
}

class BackupTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var relay: FakeRelay
    private var now = Instant.parse("2026-10-18T21:00:00Z")

    @Before fun setUp() {
        relay = FakeRelay()
        relay.now = { now.toEpochMilli() }
    }

    @After fun tearDown() = relay.server.shutdown()

    private fun write(dir: File, rel: String, text: String) = File(dir, rel).apply { parentFile.mkdirs(); writeText(text) }

    private fun phone(name: String): File = tmp.newFolder(name, "lani", "data").also { d ->
        write(d, "learner-profile.json", """{"learner": {"name": "Ana"}}""")
        write(d, "app/notebook.json", """{"notes": ["likes cats"]}""")
        write(d, "app/town-key.pem", "-----BEGIN PRIVATE KEY-----\ntown\n-----END PRIVATE KEY-----\n")
        write(d, "app/family-token", "family-secret")
        write(d, "app/answers.json", """{"answers": ["kurim"]}""")
        write(d, "app/voice/files/a.mp3", "a cached clip")
        write(d, "app/dev/shadow.json", "{}")
        write(d, "results/s1.md", "# one")
    }

    private fun backups(data: File, master: ByteArray) =
        Backups(data, RelayClient(relay.url, BackupKeys.signingKey(master), clock = { now }), master, "si.lanisce.lani 0.2.9", "Pixel 8", clock = { now })

    @Test fun `a recovery code, nine groups of four, a typo caught, the same master every time`() {
        val code = BackupKeys.newRecoveryCode(SecureRandom())
        assertTrue(code, Regex("^([A-HJ-NP-Z2-9]{4}-){8}[A-HJ-NP-Z2-9]{4}$").matches(code))
        val m = BackupKeys.masterOfCode(code)!!
        assertArrayEquals(m, BackupKeys.masterOfCode(code.lowercase().replace("-", " ")))
        val typo = code.replaceRange(0, 1, if (code[0] == 'A') "B" else "A")
        assertNull(BackupKeys.masterOfCode(typo))
        assertNull(BackupKeys.masterOfCode("short"))
        assertFalse(m.contentEquals(BackupKeys.masterOfCode(BackupKeys.newRecoveryCode())!!))
        // a passphrase: long enough, normalized
        assertArrayEquals(BackupKeys.masterOfPassphrase("  korenje in čebula  "), BackupKeys.masterOfPassphrase("korenje in čebula"))
        try {
            BackupKeys.masterOfPassphrase("short")
            fail("a short passphrase")
        } catch (e: IllegalArgumentException) {
        }
    }

    @Test fun `sealed with AES-GCM, opens with its master only, and a changed byte is caught`() {
        val m = ByteArray(32) { 7 }
        val sealed = BackupBox.seal(m, "the data".toByteArray())
        assertTrue(sealed.copyOf(8).contentEquals("LANIBAK1".toByteArray()))
        assertEquals("the data", BackupBox.open(m, sealed).decodeToString())
        assertEquals(BackupProblem.WRONG_KEY, runCatching { BackupBox.open(ByteArray(32) { 8 }, sealed) }.exceptionOrNull().let { (it as BackupError).problem })
        val bad = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertEquals(BackupProblem.WRONG_KEY, (runCatching { BackupBox.open(m, bad) }.exceptionOrNull() as BackupError).problem)
        val head = sealed.copyOf().also { it[12] = (it[12] + 1).toByte() } // the salt: in the additional data
        assertEquals(BackupProblem.WRONG_KEY, (runCatching { BackupBox.open(m, head) }.exceptionOrNull() as BackupError).problem)
        assertEquals(BackupProblem.NOT_A_BACKUP, (runCatching { BackupBox.open(m, "nope".toByteArray()) }.exceptionOrNull() as BackupError).problem)
    }

    @Test fun `back up, then restore on a second phone with the recovery code alone`() = runBlocking {
        val first = phone("first")
        val code = BackupKeys.newRecoveryCode()
        val entry = backups(first, BackupKeys.masterOfCode(code)!!).backup()
        assertEquals("lani-20261018T210000Z.lbk", entry.name)
        // the relay holds ciphertext only
        val stored = relay.store.values.single().values.single().first
        val plain = String(stored, Charsets.ISO_8859_1)
        for (secret in listOf("likes cats", "family-secret", "kurim", "town", "Ana")) assertFalse(secret, plain.contains(secret))
        // a second phone with old data of its own
        val second = tmp.newFolder("second", "lani", "data").also { write(it, "learner-profile.json", """{"learner": {"name": "old"}}""") }
        now = now.plusSeconds(3600)
        val list = backups(second, BackupKeys.masterOfCode(code)!!).list()
        assertEquals(listOf("lani-20261018T210000Z.lbk"), list.map { it.name })
        val manifest = backups(second, BackupKeys.masterOfCode(code)!!).restore()
        assertEquals("si.lanisce.lani 0.2.9", manifest.app)
        for (rel in listOf("learner-profile.json", "app/notebook.json", "app/town-key.pem", "app/family-token", "app/answers.json", "results/s1.md")) {
            assertEquals(rel, File(first, rel).readText(), File(second, rel).readText())
        }
        assertFalse("the voice cache isn't backed up", File(second, "app/voice/files/a.mp3").exists())
        assertFalse("nor the developer's data", File(second, "app/dev/shadow.json").exists())
        // what the second phone had is kept beside it
        val aside = File(second.parentFile, "data.before-restore-20261018T220000Z")
        assertEquals("""{"learner": {"name": "old"}}""", File(aside, "learner-profile.json").readText())
    }

    @Test fun `each key sees its own backups, and a wrong code finds none`() = runBlocking {
        val data = phone("p")
        backups(data, ByteArray(32) { 1 }).backup()
        assertEquals(1, backups(data, ByteArray(32) { 1 }).list().size)
        assertEquals(0, backups(data, ByteArray(32) { 2 }).list().size)
        assertEquals(BackupProblem.NONE_FOUND, (runCatching { backups(data, ByteArray(32) { 2 }).restore() }.exceptionOrNull() as BackupError).problem)
        assertTrue(relay.refused.isEmpty())
    }

    @Test fun `requests are signed as the contract says, a stale time or a replay is refused`() = runBlocking {
        val key = BackupKeys.signingKey(ByteArray(32) { 3 })
        val client = RelayClient(relay.url, key, clock = { now })
        // the headers the relay checks
        val h = client.signed("PUT", "/v1/backup/x.lbk", "abc".toByteArray(), time = 1_760_000_000_000)
        assertEquals(Base64.getEncoder().encodeToString(key.spki), h["X-Lani-Key"])
        assertEquals("1760000000000", h["X-Lani-Time"])
        val msg = "PUT\n/v1/backup/x.lbk\n1760000000000\nba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertTrue(Signature.getInstance("SHA256withECDSA").run { initVerify(key.publicKey); update(msg.toByteArray()); verify(Base64.getDecoder().decode(h["X-Lani-Sig"])) })
        // a phone whose clock is 10 minutes off is refused
        val off = RelayClient(relay.url, key, clock = { now.plusSeconds(600) })
        assertEquals(BackupProblem.REFUSED, (runCatching { off.list() }.exceptionOrNull() as BackupError).problem)
        assertEquals("stale", relay.refused.last())
    }

    @Test fun `the newest ten are kept`() = runBlocking {
        val data = phone("p")
        val b = backups(data, ByteArray(32) { 4 })
        repeat(12) {
            now = now.plusSeconds(86_400)
            b.backup()
        }
        val names = b.list().map { it.name }
        assertEquals(10, names.size)
        assertEquals("lani-20261030T210000Z.lbk", names.first())
        assertFalse(names.contains("lani-20261019T210000Z.lbk"))
    }

    @Test fun `an archive with a path out of the data is refused`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("manifest.json")); z.write("""{"schema":"lani.backup/v1","created":"x","app":"a","device":"d","files":1,"bytes":1}""".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("data/../../evil.txt")); z.write("x".toByteArray()); z.closeEntry()
        }
        val to = File(tmp.root, "restore")
        val e = runCatching { BackupArchive.unzip(out.toByteArray(), to) }.exceptionOrNull()
        assertTrue(e.toString(), e is BackupError && e.problem == BackupProblem.BROKEN)
        assertFalse(File(tmp.root.parentFile, "evil.txt").exists())
    }

    // --- through the controller ------------------------------------------------------------------------

    @Test fun `DataSync sets the backup up, backs up, and a new phone restores from the code it showed`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val one = phone("one").parentFile
            val a = DataSync(one, scope, MemoryBox(), "Pixel 8", clientId = "", relayUrl = relay.url, appVersion = "0.2.9", clock = { now })
            assertFalse(a.backupReady)
            val code = a.setUpCode()
            assertTrue(a.backupReady)
            val entry = a.backupNow()
            assertEquals(entry.name, a.settings.lastBackupName)
            // the second phone: nothing but the code
            val two = tmp.newFolder("two", "lani")
            val b = DataSync(two, scope, MemoryBox(), "Tablet", clientId = "", relayUrl = relay.url, clock = { now })
            assertEquals(1, b.backupList(code).size)
            val m = b.restore(typed = code)
            assertEquals(6, m.files)
            assertEquals("""{"notes": ["likes cats"]}""", File(two, "data/app/notebook.json").readText())
            assertTrue("the code is the new phone's backup key from now on", b.backupReady)
            assertEquals(1, b.backupList().size)
            assertEquals(BackupProblem.WRONG_KEY, (runCatching { b.backupList("nonsense") }.exceptionOrNull() as BackupError).problem)
        } finally {
            scope.cancel()
        }
    }

    /**
     * On the test scheduler's virtual time (runTest; docs/plans/05-built/nav-perf.md): the scheduler's waits, its clock and
     * the test's are one clock that only the test moves, so "at the latest maxWait after the first" is checked exactly (the
     * wall clock's 600 to 1,900 ms could fail on a busy machine).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `the push waits for the writes to stop, at most so long, and the background pushes at once`() = kotlinx.coroutines.test.runTest {
        val scope = backgroundScope
        run {
            val pushes = AtomicInteger()
            val s = PushScheduler(scope, quietMs = 200, maxWaitMs = 700, now = { currentTime }) { pushes.incrementAndGet() }
            repeat(5) { s.touched(); delay(50) }
            assertEquals("not while the writes go on", 0, pushes.get())
            delay(400)
            assertEquals("once, after they stopped", 1, pushes.get())
            assertFalse(s.pending)
            // writes that never stop: pushed maxWait after the first (seen at that moment, or at the next write's)
            val start = currentTime
            while (pushes.get() == 1 && currentTime - start < 2_000) { s.touched(); delay(100) }
            assertEquals(2, pushes.get())
            assertTrue("pushed ${currentTime - start} ms after the first write", currentTime - start in 700..800)
            // to the background: at once
            delay(400)
            val before = pushes.get()
            s.touched()
            s.flush()
            delay(100)
            assertEquals(before + 1, pushes.get())
            delay(400)
            assertEquals("and not again", before + 1, pushes.get())
            s.flush()
            delay(100)
            assertEquals("nothing waits: nothing pushed", before + 1, pushes.get())
            assertNotNull(s)
        }
    }
}
