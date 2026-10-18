package si.lanisce.lani.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A JPEG's segments as the chat's photos read them ([Jpeg]): the orientation, the size, the metadata taken out. */
class JpegTest {
    private val plain = TestJpegs.skeleton(width = 640, height = 480)
    private val camera = TestJpegs.withSegments(plain, TestJpegs.exif(orientation = 6), TestJpegs.comment("taken at home"))

    @Test fun `a camera's photo - its orientation, its size, its metadata named`() {
        assertTrue(Jpeg.isJpeg(camera))
        assertEquals(6, Jpeg.orientation(camera))
        assertEquals(640 to 480, Jpeg.size(camera))
        assertEquals(listOf("APP1 Exif", "COM"), Jpeg.metadata(camera))
        assertTrue(TestJpegs.contains(camera, TestJpegs.MODEL))
    }

    @Test fun `stripped, no EXIF, no GPS, no camera, no comment, and the rest byte for byte`() {
        val out = Jpeg.stripMetadata(camera)
        assertEquals(emptyList<String>(), Jpeg.metadata(out))
        assertEquals(1, Jpeg.orientation(out))
        assertFalse(TestJpegs.contains(out, "Exif"))
        assertFalse(TestJpegs.contains(out, TestJpegs.MAKE))
        assertFalse(TestJpegs.contains(out, TestJpegs.MODEL))
        assertFalse(TestJpegs.contains(out, "taken at home"))
        assertEquals(640 to 480, Jpeg.size(out))
        // what wasn't metadata stays as it was: the JFIF header, the tables, the frame, the scan
        assertArrayEquals(plain, out)
    }

    @Test fun `a JPEG without metadata stays as it is, and what isn't a JPEG is left alone`() {
        assertArrayEquals(plain, Jpeg.stripMetadata(plain))
        assertEquals(1, Jpeg.orientation(plain))
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertFalse(Jpeg.isJpeg(png))
        assertArrayEquals(png, Jpeg.stripMetadata(png))
        assertEquals(null, Jpeg.size(png))
    }

    @Test fun `a colour profile (APP2 ICC_PROFILE) stays, another APP2 goes`() {
        fun app2(text: String): ByteArray {
            val d = text.toByteArray(Charsets.ISO_8859_1) + ByteArray(4)
            return byteArrayOf(0xFF.toByte(), 0xE2.toByte(), ((d.size + 2) shr 8).toByte(), (d.size + 2).toByte()) + d
        }
        val icc = app2("ICC_PROFILE\u0000")
        val withIcc = TestJpegs.withSegments(plain, icc, app2("FPXR\u0000"))
        val out = Jpeg.stripMetadata(withIcc)
        assertArrayEquals(TestJpegs.withSegments(plain, icc), out)
        assertEquals(listOf("APP2"), Jpeg.metadata(withIcc))
    }

    @Test fun `a cut-off file is left as it is`() {
        val cut = camera.copyOfRange(0, 30)
        assertArrayEquals(cut, Jpeg.stripMetadata(cut))
        assertEquals(1, Jpeg.orientation(cut))
    }
}
