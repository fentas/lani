package si.lanisce.lani.audio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class PrefetchRunTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun list(day: String, vararg wants: DayWant) = DayList(day, wants.toList())
    private fun want(text: String, vararg voices: String) = DayWant(text, voices.toList().ifEmpty { listOf("female") }, "t")

    /** A fake node: its downloads ([downloads], each file [size] bytes) and the texts it voices ([voices]: text → url). */
    private class Node(val size: Int = 1000, val voices: Map<String, String> = emptyMap()) {
        val downloads = mutableListOf<String>()
        val asked = mutableListOf<List<Pair<String, String>>>()
        suspend fun prepare(texts: List<Pair<String, String>>): List<String?> {
            asked += texts
            return texts.map { voices[it.first] }
        }
        suspend fun download(url: String, to: File) {
            synchronized(downloads) { downloads += url }
            to.writeBytes(ByteArray(size))
        }
    }

    private fun run(files: AudioFiles, cap: AudioCap, index: Map<String, Map<String, String>>, node: Node, days: List<DayList>) = runBlocking {
        PrefetchRun(files, cap, index, prepare = node::prepare, download = node::download, clock = { 42 }, atOnce = 1).let { it to it.run(days) }
    }

    @Test fun `today's clips come, then tomorrow's, the node voices what it lacks, the rest falls back to another voice`() {
        val files = AudioFiles(tmp.newFolder("files"))
        val index = mapOf(
            "kruh" to mapOf("female" to "/voice/file/kruh.mp3"),
            "dober dan" to mapOf("female" to "/voice/file/dd-f.mp3"),
            "jutri" to mapOf("female" to "/voice/file/jutri.mp3"),
        )
        // the node can voice "Dober dan!" in grandma's voice now, not "Lahko noč!"
        val node = Node(voices = mapOf("Dober dan!" to "/voice/file/dd-g.mp3"))
        val days = listOf(
            list("2026-10-10", want("kruh"), want("Dober dan!", "grandma", "female"), want("Lahko noč!", "grandma", "female")),
            list("2026-10-11", want("jutri")),
        )
        val (r, s) = run(files, AudioCap.MB250, index, node, days)
        assertEquals(listOf("Dober dan!" to "grandma", "Lahko noč!" to "grandma"), node.asked.single())
        assertEquals(listOf("/voice/file/kruh.mp3", "/voice/file/dd-g.mp3", "/voice/file/jutri.mp3"), node.downloads)
        assertEquals("/voice/file/dd-g.mp3", r.updated["dober dan"]?.get("grandma"))
        assertEquals(1, s.voiced)
        assertEquals(3, s.wants)
        assertEquals(2, s.wanted)
        assertEquals(1, s.missing) // "Lahko noč!": no clip in any of its voices
        assertTrue(s.tomorrow)
        assertEquals(setOf("kruh.mp3", "dd-g.mp3", "jutri.mp3"), files.keep())
        // a second run has everything: nothing downloaded again, the node isn't asked for what it has
        val (_, again) = run(files, AudioCap.MB250, r.updated, node, days)
        assertEquals(0, again.downloaded)
        assertEquals(3, node.downloads.size)
    }

    @Test fun `the node voicing nothing isn't asked again this run`() {
        val files = AudioFiles(tmp.newFolder("files"))
        val node = Node()
        val many = (1..45).map { want("stavek $it") }.toTypedArray()
        run(files, AudioCap.MB250, emptyMap(), node, listOf(list("2026-10-10", *many), list("2026-10-11", want("še eden"))))
        assertEquals(1, node.asked.size)
        assertEquals(PrefetchRun.PREPARE_AT_ONCE, node.asked.single().size)
    }

    @Test fun `the cap stops it, today's first, tomorrow's not, and nothing the day keeps is dropped`() {
        val root = tmp.newFolder("files")
        val files = AudioFiles(root)
        // the car's library takes most of the 100 MB cap already
        File(root, "road/prompts").mkdirs()
        File(root, "road/prompts/big.wav").writeBytes(ByteArray(0))
        java.io.RandomAccessFile(File(root, "road/prompts/big.wav"), "rw").use { it.setLength(100L * MB - 2_500) }
        val index = (1..5).associate { "s$it" to mapOf("female" to "/voice/file/s$it.mp3") }
        val node = Node(size = 1000)
        val days = listOf(list("2026-10-10", want("s1"), want("s2")), list("2026-10-11", want("s3"), want("s4"), want("s5")))
        val (_, s) = run(files, AudioCap.MB100, index, node, days)
        assertTrue(s.full)
        assertFalse(s.tomorrow)
        assertEquals(2, s.wanted) // today's both fit
        assertTrue(File(files.clips, "s1.mp3").isFile && File(files.clips, "s2.mp3").isFile)
        assertTrue(AudioFiles.list(files.clips, ids = false).size <= 2)
    }

    @Test fun `a clip the car's library has is linked, not downloaded`() {
        val root = tmp.newFolder("files")
        val files = AudioFiles(root)
        files.roadClips.mkdirs()
        File(files.roadClips, "kruh.mp3").writeBytes(ByteArray(500) { 7 })
        val node = Node()
        val (_, s) = run(files, AudioCap.MB250, mapOf("kruh" to mapOf("female" to "/voice/file/kruh.mp3")), node, listOf(list("2026-10-10", want("kruh"))))
        assertTrue(node.downloads.isEmpty())
        assertEquals(1, s.wanted)
        val linked = File(files.clips, "kruh.mp3")
        assertEquals(500L, linked.length())
        // one file on disk with two names, counted once
        assertEquals(AudioFiles.fileId(File(files.roadClips, "kruh.mp3")), AudioFiles.fileId(linked))
        assertEquals(500L, files.usage().total)
    }

    @Test fun `two asking for one clip at once, one download, the other links it`() = runBlocking {
        val root = tmp.newFolder("files")
        val a = File(root, "voice-clips/x.mp3")
        val b = File(root, "road/clips/x.mp3")
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var downloads = 0
        val first = async(kotlinx.coroutines.Dispatchers.IO) {
            ClipFiles.get(a, emptyList()) { to ->
                downloads++
                started.complete(Unit)
                release.await()
                to.writeBytes(ByteArray(10))
            }
        }
        started.await()
        val second = async(kotlinx.coroutines.Dispatchers.IO) { ClipFiles.get(b, emptyList()) { downloads++; it.writeBytes(ByteArray(10)) } }
        kotlinx.coroutines.delay(300) // the second is waiting for the first's by then
        release.complete(Unit)
        assertTrue(first.await() && second.await())
        assertEquals(1, downloads)
        assertEquals(Files.readAttributes(a.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java).fileKey(), AudioFiles.fileId(b))
    }

    @Test fun `trimming, the cap with the car's library in it, the day's clips kept, off is the old 50 MB`() {
        val root = tmp.newFolder("files")
        val files = AudioFiles(root)
        files.clips.mkdirs()
        fun clip(name: String, mb: Long, used: Long) = File(files.clips, name).also { f ->
            java.io.RandomAccessFile(f, "rw").use { it.setLength(mb * MB) }
            f.setLastModified(used)
        }
        clip("old.mp3", 40, 1_000)
        clip("today.mp3", 40, 2_000)
        clip("new.mp3", 40, 3_000)
        files.writeKeep(listOf("today.mp3"))
        File(root, "road/prompts").mkdirs()
        java.io.RandomAccessFile(File(root, "road/prompts/p.wav"), "rw").use { it.setLength(60 * MB) }
        files.forgetRoad()
        // 120 + 60 = 180 MB under a 100 MB cap: the oldest goes, then the next not kept; today's stays
        assertEquals(2, files.trim(AudioCap.MB100))
        assertEquals(listOf("today.mp3"), AudioFiles.list(files.clips, ids = false).map { it.name })
        // unlimited: nothing goes
        clip("more.mp3", 40, 4_000)
        assertEquals(0, files.trim(AudioCap.UNLIMITED))
        // off: the cache alone to 50 MB, the car's library not counted (today's is kept even so)
        assertEquals(1, files.trim(AudioCap.OFF))
    }
}
