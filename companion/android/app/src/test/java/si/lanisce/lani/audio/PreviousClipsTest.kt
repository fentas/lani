package si.lanisce.lani.audio

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import si.lanisce.lani.data.Clips
import java.io.File

class PreviousClipsTest {
    @get:Rule val tmp = TemporaryFolder()

    @After fun noApp() {
        PreviousClips.appIndex = null
    }

    private fun url(name: String) = "/voice/file/$name.mp3"
    private fun on(vararg names: String): (String) -> Boolean = { it.removeSuffix(".mp3") in names }

    @Test fun `a re-voiced line plays its new clip when it's here, else its earlier one, else nothing`() {
        val index = mapOf("dober dan" to mapOf("female" to url("new")))
        val previous = mapOf("dober dan" to mapOf("female" to url("old")))
        assertEquals(PreviousClips.Pick(listOf(url("new"))), PreviousClips.pick(index, previous, "Dober dan!", "female", on("new", "old")))
        val stale = PreviousClips.pick(index, previous, "Dober dan!", "female", on("old"))!!
        assertEquals(listOf(url("old")), stale.urls)
        assertTrue(stale.stale)
        assertNull(PreviousClips.pick(index, previous, "Dober dan!", "female", on()))
        // another voice has no earlier clip of its own; a text the index lacks has none either
        assertNull(PreviousClips.pick(index, previous, "Dober dan!", "male", on("old")))
        assertNull(PreviousClips.pick(index, previous, "Dober večer!", "female", on("old")))
    }

    @Test fun `a slash text takes each phrase's new clip or its earlier one`() {
        val index = mapOf("kako ste" to mapOf("female" to url("s2")), "kako si" to mapOf("female" to url("i2")))
        val previous = mapOf("kako ste" to mapOf("female" to url("s1")))
        val p = PreviousClips.pick(index, previous, "Kako ste? / Kako si?", "female", on("s1", "i2"))!!
        assertEquals(listOf(url("s1"), url("i2")), p.urls)
        assertEquals(listOf(url("s1")), p.old)
        // a phrase with neither: the text isn't on the phone
        assertNull(PreviousClips.pick(index, previous, "Kako ste? / Kako si?", "female", on("s1")))
    }

    @Test fun `a changed URL keeps the earlier file that's on the phone, the newest of a chain`() {
        val a = mapOf("kruh" to mapOf("female" to url("a")))
        val b = mapOf("kruh" to mapOf("female" to url("b")))
        val c = mapOf("kruh" to mapOf("female" to url("c")))
        // a → b with a here: a stands in
        assertEquals(a, PreviousClips.supersede(listOf(a), b, emptyMap(), on("a")))
        // a → b with a gone: nothing to keep
        assertTrue(PreviousClips.supersede(listOf(a), b, emptyMap(), on()).isEmpty())
        // a → b → c, b never downloaded: still a
        assertEquals(a, PreviousClips.supersede(listOf(b), c, a, on("a")))
        // a → b → c, b here: b, the newer
        assertEquals(b, PreviousClips.supersede(listOf(b), c, a, on("a", "b")))
        // the index again as it was (b not here yet): the earlier clip stays
        assertEquals(a, PreviousClips.supersede(listOf(b), b, a, on("a")))
        // back at a: nothing stands in; a line gone from the index: forgotten
        assertTrue(PreviousClips.supersede(listOf(b), a, a, on("a")).isEmpty())
        assertTrue(PreviousClips.supersede(listOf(b), emptyMap(), a, on("a")).isEmpty())
        // the copy on disk first, then the app's (older) one
        assertEquals(b, PreviousClips.supersede(listOf(b, a), c, emptyMap(), on("a", "b")))
        assertEquals(a, PreviousClips.supersede(listOf(b, a), c, emptyMap(), on("a")))
    }

    @Test fun `once the new file is here the earlier one goes, unless the index still plays it or it's playing`() {
        val index = mapOf(
            "kruh" to mapOf("female" to url("k2")),
            "mleko" to mapOf("female" to url("m2")),
            "voda" to mapOf("female" to url("v2"), "male" to url("k1")),
            "sir" to mapOf("female" to url("s2")),
            "jajce" to mapOf("female" to url("j2")),
        )
        val previous = mapOf(
            "kruh" to mapOf("female" to url("k1")), // new here; k1 still voda's male clip: kept
            "mleko" to mapOf("female" to url("m1")), // new here: m1 goes
            "voda" to mapOf("female" to url("v1")), // new not here: stays
            "sir" to mapOf("female" to url("s1")), // new here, s1 playing now: stays for later
            "jajce" to mapOf("female" to url("j1")), // j1 gone (the cap dropped it): forgotten
        )
        val s = PreviousClips.settle(index, previous, on("k1", "k2", "m1", "m2", "v1", "s1", "s2", "j2"), busy = { it == "s1.mp3" })
        assertEquals(setOf("m1.mp3"), s.delete)
        assertEquals(mapOf("voda" to mapOf("female" to url("v1")), "sir" to mapOf("female" to url("s1"))), s.previous)
        // another index's file (the app's, open) isn't deleted either
        assertTrue(PreviousClips.settle(index, previous, on("m1", "m2"), referenced = setOf("m1.mp3")).delete.isEmpty())
        // only the lines asked about: the rest left as they are
        val only = PreviousClips.settle(index, previous, on("k1", "k2", "m1", "m2", "v1", "s1", "s2", "j2"), only = setOf("mleko"))
        assertEquals(setOf("m1.mp3"), only.delete)
        assertEquals(previous - "mleko", only.previous)
    }

    @Test fun `an earlier file another line still waits on isn't deleted`() {
        val index = mapOf("a" to mapOf("female" to url("a2")), "b" to mapOf("female" to url("b2")))
        val previous = mapOf("a" to mapOf("female" to url("x")), "b" to mapOf("female" to url("x")))
        val s = PreviousClips.settle(index, previous, on("a2", "x"))
        assertTrue(s.delete.isEmpty())
        assertEquals(mapOf("b" to mapOf("female" to url("x"))), s.previous)
    }

    @Test fun `a chain passing over an earlier file for a newer one on the phone lets the earlier one go`() {
        val a = mapOf("kruh" to mapOf("female" to url("a")))
        val b = mapOf("kruh" to mapOf("female" to url("b")))
        val c = mapOf("kruh" to mapOf("female" to url("c")))
        // a stood in for b; b came but a was playing then; now c: b stands in, a goes
        val s = PreviousClips.swap(listOf(b), c, a, on("a", "b"))
        assertEquals(b, s.previous)
        assertEquals(setOf("a.mp3"), s.delete)
        // with c here too: both go
        assertEquals(setOf("a.mp3", "b.mp3"), PreviousClips.swap(listOf(b), c, a, on("a", "b", "c")).delete)
        // a line gone from the index keeps its file (the cap drops it in time)
        assertTrue(PreviousClips.swap(listOf(b), mapOf("drugo" to mapOf("female" to url("d"))), a, on("a", "b")).delete.isEmpty())
    }

    @Test fun `the day keeps the earlier clips of its lines whose new ones aren't here, and they count as on the phone`() {
        val index = mapOf("kruh" to mapOf("female" to url("k2")), "mleko" to mapOf("female" to url("m2")))
        val previous = mapOf("kruh" to mapOf("female" to url("k1")), "mleko" to mapOf("female" to url("m1")))
        val has = on("k1", "m1", "m2")
        val kruh = DayWant("kruh", listOf("female"), "t")
        val mleko = DayWant("mleko", listOf("female"), "t")
        assertEquals(listOf("k1.mp3"), PreviousClips.standIns(index, previous, kruh, has))
        assertTrue(PreviousClips.standIns(index, previous, mleko, has).isEmpty())
        assertEquals(DayAudio.Stand.WANTED, DayAudio.stand(index, kruh, previous, has))
        assertEquals(DayAudio.Stand.MISSING, DayAudio.stand(index, kruh, emptyMap(), has))
    }

    @Test fun `kept next to the index, swapped and settled on disk, the cap's drops forgotten`() {
        val files = AudioFiles(tmp.newFolder("files"))
        files.clips.mkdirs()
        fun clip(name: String, used: Long = 1_000) = File(files.clips, "$name.mp3").also { it.writeBytes(ByteArray(100)); it.setLastModified(used) }
        clip("k1")
        clip("m1")
        clip("v1")
        val before = mapOf("kruh" to mapOf("female" to url("k1")), "mleko" to mapOf("female" to url("m1")), "voda" to mapOf("female" to url("v1")))
        val after = mapOf("kruh" to mapOf("female" to url("k2")), "mleko" to mapOf("female" to url("m2")), "voda" to mapOf("female" to url("v2")))
        PreviousClips.swapIn(files, listOf(before), after, referenced = emptySet())
        // on disk, as the index is: it survives a restart
        val disk = Clips.parseIndex(File(files.clips, PreviousClips.FILE).readText())
        assertEquals(before, disk)
        assertEquals(before, PreviousClips.known(files.clips))
        // kruh's new clip came: k1 goes; the car's library got voda's: v1 goes too
        clip("k2")
        files.roadClips.mkdirs()
        File(files.roadClips, "v2.mp3").writeBytes(ByteArray(100))
        val freed = PreviousClips.settleIn(files, after, referenced = emptySet())
        assertEquals(mapOf("k1.mp3" to 100L, "v1.mp3" to 100L), freed)
        assertFalse(File(files.clips, "k1.mp3").exists())
        assertEquals(mapOf("mleko" to mapOf("female" to url("m1"))), PreviousClips.read(files.clips))
        // the cap drops m1, the least recently played: its entry goes, and the file with nothing left in it
        java.io.RandomAccessFile(File(files.clips, "m1.mp3"), "rw").use { it.setLength(40 * MB) }
        File(files.clips, "m1.mp3").setLastModified(1_000)
        java.io.RandomAccessFile(File(files.clips, "big.mp3"), "rw").use { it.setLength(40 * MB) }
        File(files.clips, "big.mp3").setLastModified(5_000)
        File(files.clips, "k2.mp3").setLastModified(9_000)
        assertEquals(1, files.trim(AudioCap.OFF))
        assertFalse(File(files.clips, "m1.mp3").exists())
        assertTrue(PreviousClips.read(files.clips).isEmpty())
        assertFalse(File(files.clips, PreviousClips.FILE).exists())
    }

    @Test fun `one line's URL changed in the app alone keeps its earlier file, the other entries as they were`() {
        val files = AudioFiles(tmp.newFolder("files"))
        files.clips.mkdirs()
        File(files.clips, "m1.mp3").writeBytes(ByteArray(10))
        File(files.clips, "k1.mp3").writeBytes(ByteArray(10))
        PreviousClips.swapIn(files, listOf(mapOf("mleko" to mapOf("female" to url("m1")))), mapOf("mleko" to mapOf("female" to url("m2"))), referenced = emptySet())
        PreviousClips.replacedIn(files, "kruh", "male", url("k1"), url("k2"))
        assertEquals(mapOf("mleko" to mapOf("female" to url("m1")), "kruh" to mapOf("male" to url("k1"))), PreviousClips.read(files.clips))
        // an earlier file not on the phone: nothing stands in
        PreviousClips.replacedIn(files, "voda", "male", url("v1"), url("v2"))
        assertNull(PreviousClips.read(files.clips)["voda"])
    }

    @Test fun `the open app's clips are never deleted by the prefetch`() {
        val files = AudioFiles(tmp.newFolder("files"))
        files.clips.mkdirs()
        File(files.clips, "k1.mp3").also { it.writeBytes(ByteArray(10)); it.setLastModified(1_000) }
        File(files.clips, "k2.mp3").writeBytes(ByteArray(10))
        val before = mapOf("kruh" to mapOf("female" to url("k1")))
        val after = mapOf("kruh" to mapOf("female" to url("k2")))
        // the app open with the index it had: it still plays k1
        PreviousClips.appIndex = { before }
        assertTrue(PreviousClips.swapIn(files, listOf(before), after).isEmpty())
        assertTrue(File(files.clips, "k1.mp3").isFile)
        PreviousClips.appIndex = { after }
        assertEquals(setOf("k1.mp3"), PreviousClips.swapIn(files, listOf(before), after).keys)
    }

    @Test fun `the prefetch keeps an earlier clip standing in, then downloads the new one and lets the old go`() = runBlocking {
        val files = AudioFiles(tmp.newFolder("files"))
        files.clips.mkdirs()
        File(files.clips, "k1.mp3").also { it.writeBytes(ByteArray(1000)); it.setLastModified(1_000) }
        File(files.clips, "m1.mp3").also { it.writeBytes(ByteArray(1000)); it.setLastModified(1_000) }
        val before = mapOf("kruh" to mapOf("female" to url("k1")), "mleko" to mapOf("female" to url("m1")))
        val index = mapOf("kruh" to mapOf("female" to url("k2")), "mleko" to mapOf("female" to url("m2")))
        val days = listOf(DayList("2026-10-10", listOf(DayWant("kruh", listOf("female"), "t"), DayWant("mleko", listOf("female"), "t"))))
        // the node can't serve the new clips (offline, or not there yet): the earlier ones stand in, counted, kept
        val off = PrefetchRun(files, AudioCap.MB250, index, prepare = { null }, download = { _, _ -> throw java.io.IOException("offline") }, clock = { 42 }, atOnce = 1, before = before, referenced = { emptySet() })
        val s = off.run(days)
        assertEquals(2, s.wanted)
        assertEquals(0, s.missing)
        assertEquals(2, s.failed)
        assertEquals(setOf("k2.mp3", "m2.mp3", "k1.mp3", "m1.mp3"), files.keep())
        assertEquals(before, PreviousClips.read(files.clips))
        // the day's audio counted once a line
        assertEquals(2000L, files.usage().day)
        // on Wi-Fi: the new ones come, the earlier ones go
        val got = mutableListOf<String>()
        val on = PrefetchRun(files, AudioCap.MB250, index, prepare = { null }, download = { u, to -> got += u; to.writeBytes(ByteArray(1000)) }, clock = { 42 }, atOnce = 1, before = index, referenced = { emptySet() })
        val s2 = on.run(days)
        assertEquals(listOf(url("k2"), url("m2")), got)
        assertEquals(2, s2.wanted)
        assertFalse(File(files.clips, "k1.mp3").exists() || File(files.clips, "m1.mp3").exists())
        assertTrue(PreviousClips.read(files.clips).isEmpty())
        assertEquals(setOf("k2.mp3", "m2.mp3"), files.keep())
        assertEquals(2000L, files.usage().total)
    }

    @Test fun `the room the earlier clips take comes back as their new ones arrive`() = runBlocking {
        val root = tmp.newFolder("files")
        val files = AudioFiles(root)
        files.clips.mkdirs()
        // the car's library takes most of the 100 MB cap; two earlier clips of 1,000 bytes stand in for the day's lines
        File(root, "road/prompts").mkdirs()
        java.io.RandomAccessFile(File(root, "road/prompts/big.wav"), "rw").use { it.setLength(100L * MB - 3_500) }
        File(files.clips, "k1.mp3").also { it.writeBytes(ByteArray(1000)); it.setLastModified(1_000) }
        File(files.clips, "m1.mp3").also { it.writeBytes(ByteArray(1000)); it.setLastModified(1_000) }
        val before = mapOf("kruh" to mapOf("female" to url("k1")), "mleko" to mapOf("female" to url("m1")))
        val index = mapOf("kruh" to mapOf("female" to url("k2")), "mleko" to mapOf("female" to url("m2")))
        val days = listOf(DayList("2026-10-10", listOf(DayWant("kruh", listOf("female"), "t"), DayWant("mleko", listOf("female"), "t"))))
        // 1,500 bytes of room once the earlier clips are counted: the second new clip fits only as the first's earlier one goes
        val run = PrefetchRun(files, AudioCap.MB100, index, prepare = { null }, download = { _, to -> to.writeBytes(ByteArray(1000)) }, clock = { 42 }, atOnce = 1, before = before, referenced = { emptySet() })
        val s = run.run(days)
        assertFalse(s.full)
        assertEquals(2, s.downloaded)
        assertTrue(File(files.clips, "k2.mp3").isFile && File(files.clips, "m2.mp3").isFile)
    }
}
