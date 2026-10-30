package si.lanisce.lani.hosted

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import si.lanisce.lani.phone.HostFs
import si.lanisce.lani.phone.PhoneBridge

/**
 * Export and import between the modes (hosted/DataExport, docs/plans/05-built/hosted-mode.md): the zip and its manifest
 * (lani.export/v1, the same as `lani export` on a node), what stays out, the import's checks (the manifest; validate-data
 * and read-db through the db port, the phone bridge's TypeScript on the JVM), the replace with its local backup, the
 * refusals; and the node's own `lani export` read here, this app's export verified by the node's `lani import --verify`.
 */
class HostedExportTest {
    companion object {
        val repo = File(System.getProperty("lani.repo") ?: "../../..").canonicalFile
        val bundle = File(System.getProperty("lani.phoneBundle") ?: "build/generated/phoneBridge/phone/bridge-phone.js")
        lateinit var tmp: File
        lateinit var checker: PhoneBridge
        val json = Json { ignoreUnknownKeys = true }
        val from = buildJsonObject { put("side", "phone"); put("mode", "hosted"); put("name", "Test phone"); put("app", "si.lanisce.lani test") }

        @BeforeClass
        @JvmStatic
        fun boot() {
            check(bundle.isFile) { "no bundle at $bundle: ./gradlew :app:phoneBridgeBundle" }
            tmp = Files.createTempDirectory("lani-export-test").toFile()
            // only loaded, never booted: its db port checks the imports (as the onboarding's import does)
            checker = PhoneBridge(
                PhoneBridge.Config(
                    projectDir = repo, dataDir = File(tmp, "unused"), home = tmp,
                    roots = listOf(HostFs.Root(tmp, writable = true), HostFs.Root(repo, writable = false)),
                    bundle = { bundle.readText() },
                ),
            )
        }

        @AfterClass
        @JvmStatic
        fun done() {
            checker.stop()
            tmp.deleteRecursively()
        }

        /** A learner's data as a phone holds it: the six databases, the bridge's files, a secret, private and runtime files. */
        fun learner(dir: File, name: String = "Ana"): File {
            dir.mkdirs()
            File(repo, "data-examples").listFiles { f -> f.name.endsWith("-template.json") }!!.forEach { it.copyTo(File(dir, it.name.replace("-template", ""))) }
            val profile = File(dir, "learner-profile.json")
            profile.writeText(profile.readText().replace("{YOUR_NAME}", name).replace("{LANGUAGE_YOU_WANT_TO_LEARN}", "Slovene"))
            File(dir, "app").mkdirs()
            File(dir, "app/game.json").writeText("""{"rev": 3, "state": {"schema": 1, "village": "Testna vas"}}""")
            File(dir, "app/towns.json").writeText("""{"schema": "lani.towns/v1", "links": []}""")
            File(dir, "app/bridge-key.pem").writeText("-----BEGIN PRIVATE KEY-----\nnot a real one\n-----END PRIVATE KEY-----\n")
            File(dir, "app/bridge-token").writeText("secret-token")
            File(dir, "app/answers.json").writeText("""{"answers": []}""")
            File(dir, "app/events.json").writeText("[]")
            File(dir, "app/voice").mkdirs()
            File(dir, "app/voice/x.mp3").writeBytes(byteArrayOf(1, 2, 3))
            File(dir, "app/lease.json").writeText("""{"schema": "lani.lease/v1", "holder": null}""")
            File(dir, "results").mkdirs()
            File(dir, "results/session-2026-10-18.md").writeText("# A session\n")
            File(dir, "languages/it").mkdirs()
            File(dir, "learner-profile.json").copyTo(File(dir, "languages/it/learner-profile.json"))
            return dir
        }

        fun entries(zip: File): List<String> = ZipFile(zip).use { z -> z.entries().toList().map { it.name } }
    }

    private fun db(call: String) = runBlocking { checker.db(call) }

    @Test
    fun anExportHoldsTheRepositorysFilesAndAManifest() {
        val data = learner(File(tmp, "a/data"))
        val out = File(tmp, "a/export.zip")
        val w = DataExport.write(data, out, from, Instant.parse("2026-10-19T08:15:00Z"))
        val names = entries(out)
        assertEquals(DataExport.MANIFEST, names.first())
        for (want in listOf("learner-profile.json", "spaced-repetition.json", "app/game.json", "app/towns.json", "results/session-2026-10-18.md", "languages/it/learner-profile.json", "app/lease.json")) {
            assertTrue("$want in $names", want in names)
        }
        // never the secrets, the recent answers, the caches, the runtime files
        for (never in listOf("app/bridge-key.pem", "app/bridge-token", "app/answers.json", "app/voice/x.mp3", "app/events.json")) assertFalse(never, never in names)
        val m = w.manifest
        assertEquals("lani.export/v1", m.format)
        assertEquals("2026-10-19T08:15:00Z", m.created)
        assertEquals("Ana", m.learnerName)
        assertEquals("phone", m.fromSide)
        assertEquals("1", m.schemas["app/game.json"].toString())
        assertEquals("\"lani.towns/v1\"", m.schemas["app/towns.json"].toString())
        assertEquals(names.size - 1, m.files.size)
        assertEquals(DataExport.sha256(File(data, "app/game.json")), m.files.first { it.path == "app/game.json" }.sha256)
        assertEquals("lani-export-ana-2026-10-19.zip", DataExport.fileName("Ana", Instant.parse("2026-10-19T08:15:00Z")))
        assertEquals("lani-export-ziga-skerjanc-2026-10-19.zip", DataExport.fileName("Žiga Škerjanc", Instant.parse("2026-10-19T08:15:00Z")))
    }

    @Test
    fun aRoundTripLosesNothing() {
        val data = learner(File(tmp, "b/data"))
        val out = File(tmp, "b/export.zip")
        DataExport.write(data, out, from)
        // the phone that imports: another learner, its own key and lease
        val target = learner(File(tmp, "b/phone/data"), name = "Bo")
        File(target, "app/bridge-key.pem").writeText("the phone's own key")
        File(target, "app/lease.json").writeText("""{"schema": "lani.lease/v1", "holder": {"side": "phone"}}""")
        File(target, "stale.json").writeText("{}")
        val staging = File(tmp, "b/phone/import")
        val m = out.inputStream().use { DataExport.unpack(it, staging) }
        assertEquals(emptyList<String>(), runBlocking { DataExport.checkData(staging) { checker.db(it) } })
        val backup = DataExport.replace(target, staging)
        assertNotNull(backup)
        assertTrue(backup!!.isDirectory)
        for (f in m.files.filter { it.path !in DataExport.OWN }) assertEquals(f.path, f.sha256, DataExport.sha256(File(target, f.path)))
        assertEquals("Ana", learnerName(target))
        // the phone's own: its key and its lease stay; a file the export hasn't went with the old data (in the backup)
        assertEquals("the phone's own key", File(target, "app/bridge-key.pem").readText())
        assertTrue(File(target, "app/lease.json").readText().contains("\"phone\""))
        assertFalse(File(target, "stale.json").exists())
        assertTrue(File(backup, "stale.json").exists())
        assertFalse(staging.exists())
        // read-db on the imported data, through the db port
        val r = json.parseToJsonElement(db(buildJsonObject { put("script", "read-db"); put("env", buildJsonObject { put("LANI_DATA_DIR", target.path) }) }.toString())).jsonObject
        assertEquals(r.toString().take(400), "0", r["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun theNewestBackupsAreKept() {
        val data = learner(File(tmp, "c/data"))
        val out = File(tmp, "c/export.zip")
        DataExport.write(data, out, from)
        val target = learner(File(tmp, "c/phone/data"))
        for (i in 0 until 5) {
            val staging = File(tmp, "c/phone/import")
            out.inputStream().use { DataExport.unpack(it, staging) }
            DataExport.replace(target, staging, Instant.parse("2026-10-19T08:0$i:00Z"))
        }
        val kept = File(tmp, "c/phone").listFiles { f -> f.name.startsWith("data.before-import-") }!!.map { it.name }.sorted()
        assertEquals(listOf("data.before-import-20261019-080200", "data.before-import-20261019-080300", "data.before-import-20261019-080400"), kept)
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z -> for ((n, b) in entries) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return bytes.toByteArray()
    }

    private fun refused(zip: ByteArray, why: String) {
        val staging = File(tmp, "refused/import")
        val r = runCatching { zip.inputStream().use { DataExport.unpack(it, staging) } }
        assertTrue("expected a refusal: $why", r.exceptionOrNull() is DataExport.Problem)
        assertTrue("${r.exceptionOrNull()?.message} for $why", r.exceptionOrNull()!!.message.orEmpty().contains(why))
    }

    private fun manifestFor(files: Map<String, ByteArray>, format: String = "lani.export/v1", secrets: List<String> = emptyList()): ByteArray {
        val m = DataExport.Manifest(format, "2026-10-19T08:15:00Z", from, buildJsonObject { }, buildJsonObject { },
            files.map { (p, b) -> DataExport.FileEntry(p, b.size.toLong(), sha(b)) }, secrets)
        return DataExport.encode(m).toByteArray()
    }

    private fun sha(b: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test
    fun theImportRefuses() {
        val profile = File(repo, "data-examples/learner-profile-template.json").readBytes()
        val good = mapOf("learner-profile.json" to profile)
        refused(zipOf("learner-profile.json" to profile), "no lani-export.json")
        refused(byteArrayOf(1, 2, 3, 4), "not a Lani export")
        refused(zipOf(DataExport.MANIFEST to manifestFor(good, "lani.export/v2"), "learner-profile.json" to profile), "newer Lani")
        refused(zipOf(DataExport.MANIFEST to manifestFor(good, "something/v1"), "learner-profile.json" to profile), "not a Lani export")
        refused(zipOf(DataExport.MANIFEST to manifestFor(good), "learner-profile.json" to profile, "../evil.json" to "{}".toByteArray()), "a path it may not")
        refused(zipOf(DataExport.MANIFEST to manifestFor(good), "learner-profile.json" to profile, ".git/config" to "x".toByteArray()), "a path it may not")
        refused(zipOf(DataExport.MANIFEST to manifestFor(good), "learner-profile.json" to profile, "extra.json" to "{}".toByteArray()), "doesn't list")
        refused(zipOf(DataExport.MANIFEST to manifestFor(good), "learner-profile.json" to "{}".toByteArray()), "isn't what the export's manifest says")
        refused(zipOf(DataExport.MANIFEST to manifestFor(good + ("progress-db.json" to "{}".toByteArray())), "learner-profile.json" to profile), "misses progress-db.json")
        val secret = good + ("app/bridge-token" to "t".toByteArray())
        refused(zipOf(DataExport.MANIFEST to manifestFor(secret), "learner-profile.json" to profile, "app/bridge-token" to "t".toByteArray()), "never leaves")
        val answers = good + ("app/answers.json" to "{}".toByteArray())
        refused(zipOf(DataExport.MANIFEST to manifestFor(answers), "learner-profile.json" to profile, "app/answers.json" to "{}".toByteArray()), "never leaves")
        // a town key named among the secrets comes in; one not named doesn't
        val town = good + ("app/town-key.pem" to "k".toByteArray())
        refused(zipOf(DataExport.MANIFEST to manifestFor(town), "learner-profile.json" to profile, "app/town-key.pem" to "k".toByteArray()), "never leaves")
        val staging = File(tmp, "refused/secret")
        val m = zipOf(DataExport.MANIFEST to manifestFor(town, secrets = listOf("app/town-key.pem")), "learner-profile.json" to profile, "app/town-key.pem" to "k".toByteArray())
            .inputStream().use { DataExport.unpack(it, staging) }
        assertEquals(listOf("app/town-key.pem"), m.secrets)
        refused(zipOf(DataExport.MANIFEST to manifestFor(good, secrets = listOf("app/bridge-token")), "learner-profile.json" to profile), "a secret it may not carry")
        refused(zipOf(DataExport.MANIFEST to manifestFor(mapOf("progress-db.json" to "{}".toByteArray())), "progress-db.json" to "{}".toByteArray()), "no learner profile")
    }

    @Test
    fun brokenDatabasesAreFoundByTheDbPort() {
        val data = learner(File(tmp, "d/data"))
        File(data, "mistakes-db.json").writeText("{ not json")
        val out = File(tmp, "d/export.zip")
        DataExport.write(data, out, from)
        val staging = File(tmp, "d/phone/import")
        out.inputStream().use { DataExport.unpack(it, staging) }
        val problems = runBlocking { DataExport.checkData(staging) { checker.db(it) } }
        assertTrue(problems.toString(), problems.any { it.startsWith("mistakes-db.json") })
        // validate-data's backup copies don't stay
        assertFalse(staging.walkTopDown().any { it.name.contains(".backup-") })
    }

    private fun python(vararg args: String): Pair<Int, String> {
        val p = ProcessBuilder("python3", File(repo, ".claude/hooks/lani_export.py").path, *args)
            .redirectErrorStream(true)
            .apply {
                environment()["HOME"] = tmp.path
                environment()["XDG_CONFIG_HOME"] = File(tmp, "config").path
                environment()["PYTHONDONTWRITEBYTECODE"] = "1"
                // the learners on this machine: none here (never the checkout's own profiles/), nor a child's flag of the shell's
                environment()["LANI_PROFILES_DIR"] = File(tmp, "profiles").path
                environment().remove("LANI_CHILD")
            }
            .start()
        val out = p.inputStream.readBytes().decodeToString()
        p.waitFor(60, TimeUnit.SECONDS)
        return p.exitValue() to out
    }

    private fun hasPython() = runCatching { ProcessBuilder("python3", "--version").start().waitFor() == 0 }.getOrDefault(false)

    @Test
    fun aNodesExportImportsHereAndThisAppsVerifiesThere() {
        assumeTrue("python3 is needed for the node's side", hasPython())
        // the node's lani export → this app's import
        val node = learner(File(tmp, "e/node-data"))
        val nodeZip = File(tmp, "e/node-export.zip")
        val (code, out) = python("export", "--data", node.path, "--out", nodeZip.path)
        assertEquals(out, 0, code)
        val staging = File(tmp, "e/phone/import")
        val m = nodeZip.inputStream().use { DataExport.unpack(it, staging) }
        assertEquals("node", m.fromSide)
        assertEquals(emptyList<String>(), runBlocking { DataExport.checkData(staging) { checker.db(it) } })
        // this app's export → the node's lani import --verify
        val phone = learner(File(tmp, "e/phone-data"))
        val phoneZip = File(tmp, "e/phone-export.zip")
        DataExport.write(phone, phoneZip, from)
        val (vcode, vout) = python("verify", phoneZip.path)
        assertEquals(vout, 0, vcode)
        assertTrue(vout, vout.contains("Ana"))
        assertFalse(vout, vout.contains("a child"))
    }

    @Test
    fun aChildsExportSaysSoOnBothSides() {
        assumeTrue("python3 is needed for the node's side", hasPython())
        // this phone's learner a child's (its marker beside the data): the manifest says it, the node reads it
        val phone = learner(File(tmp, "c/phone-data"), name = "Teo")
        val zip = File(tmp, "c/teo.zip")
        assertTrue(DataExport.write(phone, zip, from, child = true).manifest.child)
        val m = zip.inputStream().use { DataExport.unpack(it, File(tmp, "c/import")) }
        assertTrue("the manifest read back", m.child)
        val (code, out) = python("verify", zip.path)
        assertEquals(out, 0, code)
        assertTrue(out, out.contains("Teo") && out.contains("a child"))
        // a grown-up's: no flag at all, as before
        val adult = File(tmp, "c/ana.zip")
        assertFalse(DataExport.write(learner(File(tmp, "c/ana-data")), adult, from).manifest.child)
        assertFalse(ZipFile(adult).use { z -> z.getInputStream(z.getEntry(DataExport.MANIFEST)).readBytes().decodeToString() }.contains("\"child\""))
        // a node's child profile (lani profile add --child) → this app's import knows it
        val reg = File(tmp, "profiles").apply { mkdirs() }
        learner(File(reg, "teo/data"), name = "Teo")
        File(reg, "profiles.json").writeText("""{"schema": "lani.profiles/v1", "profiles": [{"id": "teo", "name": "Teo", "child": true, "data_dir": "teo/data"}]}""")
        val nodeZip = File(tmp, "c/node-teo.zip")
        val (ecode, eout) = python("export", "--profile", "teo", "--out", nodeZip.path)
        assertEquals(eout, 0, ecode)
        assertTrue(nodeZip.inputStream().use { DataExport.unpack(it, File(tmp, "c/import2")) }.child)
    }
}

/** The learner profile's name in [dataDir]. */
private fun learnerName(dataDir: File): String? =
    DataExport.learnerOf(File(dataDir, "learner-profile.json"))["name"]?.jsonPrimitive?.content
