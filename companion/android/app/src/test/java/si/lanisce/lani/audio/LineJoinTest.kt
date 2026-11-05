package si.lanisce.lani.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

class LineJoinTest {
    private val manifest = """{"v":1,"id":"${"a".repeat(40)}","text":"Hvala, Jan. Ti si zlat.","voice":"grandma","parts":[
        {"kind":"name","url":"/voice/file/${"b".repeat(40)}.mp3","ms":640,"gain":-1.5},{"kind":"gap","ms":120},{"kind":"piece","url":"/voice/file/${"c".repeat(40)}.mp3","ms":980,"gain":0}]}"""

    @Test fun `a joined line is known by its URL and its manifest stands beside it`() {
        val url = "/voice/line/${"a".repeat(40)}.mp3"
        assertTrue(LineJoin.isLine(url))
        assertTrue(LineJoin.isLine("$url?count=0"))
        assertFalse(LineJoin.isLine("/voice/file/${"a".repeat(40)}.mp3"))
        assertEquals("/voice/line/${"a".repeat(40)}.json", LineJoin.manifestUrl("$url?count=0"))
    }

    @Test fun `the same file name as a clip's, so the cache treats it as one`() {
        assertEquals("${"a".repeat(40)}.mp3", DayAudio.fileOf("/voice/line/${"a".repeat(40)}.mp3"))
    }

    @Test fun `a manifest reads with its parts in order`() {
        val m = LineJoin.parse(manifest)!!
        assertEquals(listOf("name", "gap", "piece"), m.parts.map { it.kind })
        assertEquals(-1.5f, m.parts[0].gain)
        assertEquals(120, m.parts[1].ms)
    }

    @Test fun `what it can't join it doesn't take, so the finished file comes instead`() {
        assertNull(LineJoin.parse("not json"))
        assertNull(LineJoin.parse(manifest.replace("\"v\":1", "\"v\":2")))
        assertNull(LineJoin.parse("""{"v":1,"id":"x","parts":[]}"""))
        assertNull(LineJoin.parse("""{"v":1,"id":"x","parts":[{"kind":"name","ms":10}]}"""))
        assertNull(LineJoin.parse("""{"v":1,"id":"x","parts":[{"kind":"song","url":"/a","ms":10}]}"""))
        assertNull(LineJoin.parse("""{"v":1,"id":"x","parts":[{"kind":"gap","ms":999999}]}"""))
    }

    @Test fun `a part the manifest doesn't know the newer fields of still reads`() {
        assertNotNull(LineJoin.parse("""{"v":1,"id":"x","later":true,"parts":[{"kind":"piece","url":"/a","ms":10,"later":1}]}"""))
    }

    @Test fun `parts join sample against sample, a pause being the manifest's and nothing else`() {
        val a = ShortArray(100) { 1000 }
        val b = ShortArray(50) { -1000 }
        val j = LineJoin.join(listOf(JoinPart.Sound(a), JoinPart.Gap(10), JoinPart.Sound(b)))
        val gap = 441
        assertEquals(100 + gap + 50, j.size)
        assertEquals(1000, j[99].toInt())
        assertEquals(0, j[100].toInt())
        assertEquals(0, j[100 + gap - 1].toInt())
        assertEquals(-1000, j[100 + gap].toInt())
    }

    @Test fun `with no pause the next part follows on the very next sample`() {
        val j = LineJoin.join(listOf(JoinPart.Sound(ShortArray(3) { 7 }), JoinPart.Sound(ShortArray(3) { 9 })))
        assertArrayEquals(shortArrayOf(7, 7, 7, 9, 9, 9), j)
    }

    @Test fun `a gain is in decibels, and stays within what a sample can hold`() {
        assertEquals(5012, LineJoin.join(listOf(JoinPart.Sound(shortArrayOf(10_000), -6f))).single().toInt(), 2)
        assertEquals(19_953, LineJoin.join(listOf(JoinPart.Sound(shortArrayOf(10_000), 6f))).single().toInt(), 2)
        assertEquals(32_767, LineJoin.join(listOf(JoinPart.Sound(shortArrayOf(30_000), 6f))).single().toInt())
        assertEquals(-32_768, LineJoin.join(listOf(JoinPart.Sound(shortArrayOf(-30_000), 6f))).single().toInt())
    }

    private fun assertEquals(want: Int, got: Int, within: Int) = assertTrue("$got is not about $want", kotlin.math.abs(want - got) <= within)

    @Test fun `the sound as a WAV file a player reads, at the clips' rate`() {
        val w = LineJoin.wav(shortArrayOf(1, -2, 3))
        val b = ByteBuffer.wrap(w).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(w, 0, 4))
        assertEquals("WAVE", String(w, 8, 4))
        assertEquals(36 + 6, b.getInt(4))
        assertEquals(1, b.getShort(20).toInt()) // PCM
        assertEquals(1, b.getShort(22).toInt()) // mono
        assertEquals(44_100, b.getInt(24))
        assertEquals(88_200, b.getInt(28))
        assertEquals(16, b.getShort(34).toInt())
        assertEquals("data", String(w, 36, 4))
        assertEquals(6, b.getInt(40))
        assertEquals(-2, b.getShort(46).toInt())
        assertEquals(50, w.size)
    }

    @Test fun `a decoder that honours the gapless header has nothing taken off, as on the emulator's API 36`() {
        // what QA read: 25,799 samples for a part of 585 ms, 127,229 for one of 2,885
        assertEquals(0, LineJoin.soundStart(25_799, 25_798, 576))
        assertEquals(0, LineJoin.soundStart(127_229, 127_228, 576))
    }

    @Test fun `a decoder that leaves the delay and the padding has the delay taken off`() {
        assertEquals(576, LineJoin.soundStart(27_119, 25_798, 576))
    }

    @Test fun `a short decode never reads past its end`() {
        assertEquals(0, LineJoin.soundStart(100, 25_798, 576))
        assertEquals(100, LineJoin.soundStart(25_898, 25_798, 576))
    }

    // the first bytes of an MP3 as the node's ffmpeg writes a piece: an ID3 tag, then the frame with its LAME header
    private val ffmpegHead = Base64.getDecoder().decode(
        "SUQzBAAAAAAAIlRTU0UAAAAOAAADTGF2ZjYzLjEuMTAyAAAAAAAAAAAAAAD/+5DAAAAAAAAAAAAAAAAAAAAAAABJbmZvAAAADwAAABEAAB1iABwcHBwcKioqKioqODg4ODg4R0dHR0dHVVVVVVVVY2NjY2NjcXFxcXFxf39/f39/jo6Ojo6cnJycnJyqqqqqqqq4uLi4uLjHx8fHx8fV1dXV1dXj4+Pj4+Px8fHx8fH//////wAAAABMYXZjNjMuMS4AAAAAAAAAAAAAAAAkBVgAAAAAAAAdYnB4i9QAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
    )

    @Test fun `the encoder's delay is read from a real file's LAME header`() {
        assertEquals(576, Mp3Gapless.delayIn(ffmpegHead))
    }

    @Test fun `an MP3 with no such header has none to read`() {
        assertNull(Mp3Gapless.delayIn(ByteArray(600)))
        assertNull(Mp3Gapless.delayIn(ByteArray(0)))
        assertNull(Mp3Gapless.delayIn("ID3".toByteArray()))
    }
}
