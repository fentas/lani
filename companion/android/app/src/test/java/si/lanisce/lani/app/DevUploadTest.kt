package si.lanisce.lani.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * The developer's data on the node (app/DevUpload): only while the developer setting is on and the app is paired; after
 * the day's closing, in the background at most every 30 minutes, by the panel's button; the log's new lines of the last
 * 24 hours, at most 2,000, cleaned of tokens and keys.
 */
class DevUploadTest {
    private val zone = ZoneId.of("Europe/Ljubljana")
    private val dir: File = Files.createTempDirectory("dev-upload").toFile()
    private val start = LocalDateTime.of(2026, 10, 16, 18, 0)
    private var now = start.atZone(zone).toInstant().toEpochMilli()
    private var developer = true
    private var paired = true
    private var answer = 200
    private var lines = listOf<String>()
    private var exports = 0
    private var reads = 0
    private val sent = mutableListOf<Triple<String, String, String>>()
    private val scopes = mutableListOf<CoroutineScope>()
    private val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val node = object : DevUpload.Node {
        override val secrets = listOf(TOKEN)
        override suspend fun post(path: String, body: String, type: String): Int {
            sent += Triple(path, body, type)
            return answer
        }
    }

    private fun upload(): DevUpload {
        val scope = CoroutineScope(SupervisorJob())
        scopes += scope
        return DevUpload(
            scope, File(dir, "dev-upload.json"),
            developer = { developer },
            node = { if (paired) node else null },
            export = { secrets, max ->
                exports++
                """{"schema":"lani.shadow-export/v0","secrets":${secrets == listOf(TOKEN)},"max":$max}"""
            },
            readLog = { reads++; AppLog.Read(lines) },
            about = { "si.lanisce.lani 0.2.99 (99), Android 16 (API 36)" },
            clock = { now }, zone = { zone }, io = io,
        )
    }

    @After fun stop() {
        scopes.forEach { it.cancel() }
        io.close()
        dir.deleteRecursively()
    }

    private fun at(minutes: Long) { now = start.plusMinutes(minutes).atZone(zone).toInstant().toEpochMilli() }
    private fun line(minutesBefore: Long, text: String) =
        "${start.plusMinutes(minutesBefore * -1).format(LOGCAT)}  4321  4400 W Clips: $text"
    private fun paths() = sent.map { it.first }

    @Test fun `with the developer setting off nothing is read and nothing is sent, by any trigger`() = runBlocking {
        developer = false
        lines = listOf(line(1, "no clip of a line"))
        val u = upload()
        u.onClosing().join()
        u.onBackground().join()
        val o = u.sendNow()
        assertFalse(o.sent)
        assertTrue(o.summary.contains("developer setting is off"))
        assertTrue(sent.isEmpty())
        assertEquals(0, exports)
        assertEquals(0, reads)
    }

    @Test fun `an app that isn't paired sends nothing`() = runBlocking {
        paired = false
        val u = upload()
        u.onClosing().join()
        u.onBackground().join()
        assertFalse(u.sendNow().sent)
        assertTrue(sent.isEmpty())
        assertEquals(0, exports)
    }

    @Test fun `the day's closing sends the export and the log to the node's two routes`() = runBlocking {
        lines = listOf(line(2, "no clip of a line: java.io.IOException: HTTP 404"))
        val u = upload()
        u.onClosing().join()
        assertEquals(listOf(DevUpload.SHADOW, DevUpload.LOGS), paths())
        val (_, export, type) = sent[0]
        assertEquals("""{"schema":"lani.shadow-export/v0","secrets":true,"max":${DevUpload.MAX_BYTES}}""", export)
        assertTrue(type.startsWith("application/json"))
        val (_, log, logType) = sent[1]
        assertTrue(logType.startsWith("text/plain"))
        assertTrue(log.startsWith("=== ") && log.lines().first().contains("si.lanisce.lani 0.2.99 (99)") && log.lines().first().contains("1 line(s) of logcat"))
        assertTrue(log.contains("no clip of a line"))
        assertTrue(u.last!!.contains("the day closed") && u.last!!.contains("200"))
    }

    @Test fun `in the background at most every 30 minutes, the closing and the button whenever`() = runBlocking {
        lines = listOf(line(1, "a warning"))
        val u = upload()
        u.onBackground().join()
        assertEquals(listOf(DevUpload.SHADOW, DevUpload.LOGS), paths())
        at(10)
        u.onBackground().join()
        assertEquals(2, sent.size) // 10 minutes: not again
        at(12)
        u.onClosing().join()
        assertEquals(3, sent.size) // the closing: its export (no new log lines)
        at(31)
        u.onBackground().join()
        assertEquals(3, sent.size) // 19 minutes after the closing's
        at(33)
        assertTrue(u.sendNow().sent) // the button: whenever
        assertEquals(4, sent.size)
        at(64)
        u.onBackground().join()
        assertEquals(5, sent.size) // 31 minutes after the button's
        // the throttle survives the app's restart (files/dev-upload.json)
        at(70)
        upload().onBackground().join()
        assertEquals(5, sent.size)
        at(95)
        upload().onBackground().join()
        assertEquals(6, sent.size)
    }

    @Test fun `the log has the lines after the last ones sent, of the last 24 hours, cleaned of tokens and keys`() = runBlocking {
        lines = listOf(
            line(25 * 60, "a day and more ago"),
            "--------- beginning of main",
            line(30, "sent with $TOKEN"),
            line(20, "Authorization: Bearer $TOKEN"),
            line(10, "x-api-key: $KEY"),
            line(5, "GET http://100.64.0.1:8790/state?token=abcdef123&language=sl"),
        )
        val u = upload()
        u.onClosing().join()
        val log = sent.single { it.first == DevUpload.LOGS }.second
        assertFalse(log.contains("a day and more ago"))
        assertFalse(log.contains("beginning of main"))
        assertEquals(4, log.lines().count { it.contains(" W Clips: ") })
        assertFalse(log.contains(TOKEN))
        assertFalse(log.contains("SHADOWKEY"))
        assertFalse(log.contains("abcdef123"))
        assertTrue(log.contains("x-api-key: ‹…›") && log.contains("token=‹…›&language=sl") && log.contains("Authorization: ‹…›"))

        // the next upload: only what came after; a log the node refused goes again with the next
        lines = lines + line(-3, "a new warning")
        at(5)
        answer = 500
        u.sendNow()
        answer = 200
        at(6)
        u.sendNow()
        val logs = sent.filter { it.first == DevUpload.LOGS }.map { it.second }
        assertEquals(3, logs.size)
        assertEquals(listOf(1, 1), logs.drop(1).map { l -> l.lines().count { it.contains(" W Clips: ") } })
        assertTrue(logs[2].contains("a new warning"))
        at(7)
        sent.clear()
        u.sendNow()
        assertEquals(listOf(DevUpload.SHADOW), paths()) // no new lines: no log upload
    }

    @Test fun `at most the last 2,000 lines`() {
        val many = (1..2_500).map { i -> "${start.minusSeconds(3_000L - i).format(LOGCAT)}  1  1 W Tag: line $i" }
        val nowMs = start.atZone(zone).toInstant().toEpochMilli()
        val s = AppLog.select(many, nowMs, 0, zone)
        assertEquals(AppLog.MAX_LINES, s.lines.size)
        assertTrue(s.lines.first().endsWith("line 501") && s.lines.last().endsWith("line 2500"))
        assertEquals(AppLog.timeOf(many.last(), zone), s.newest)
        // after a time: only the lines after it
        val after = AppLog.select(many, nowMs, AppLog.timeOf(many[2_489], zone)!!, zone)
        assertEquals(10, after.lines.size)
    }

    @Test fun `logcat is asked for this process's lines, the app's tags from info, every tag's warnings`() {
        val c = AppLog.command(4321)
        assertEquals(listOf("logcat", "-d"), c.take(2))
        assertTrue("--pid=4321" in c && "*:W" in c && "ShadowTutor:I" in c && "Clips:I" in c && "Speaker:I" in c && "DevUpload:I" in c)
        assertTrue(c.containsAll(listOf("-v", "year")))
    }

    private companion object {
        const val TOKEN = "Zq8xPcL2vN0bR4tY7uW1mK3sE5hJ9dF6"
        const val KEY = "sk-ant-api03-SHADOWKEY0123456789abcdef"
        val LOGCAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    }
}
