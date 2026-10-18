package si.lanisce.lani.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * A photo made ready for the tutor ([PhotoPrep], README "Tutor chat", "Photos"), with Android's real decoder and encoder
 * (Robolectric's native graphics): a camera's JPEG with GPS, its make and model and orientation 6 comes out upright,
 * at most 1,568 px on its long side, without any of them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoPrepTest {
    /** A [w]×[h] JPEG: its left half red, its right half blue (so a turn shows where they went). */
    private fun jpeg(w: Int, h: Int): ByteArray {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(b).apply {
            drawColor(Color.BLUE)
            drawRect(0f, 0f, w / 2f, h.toFloat(), Paint().apply { color = Color.RED })
        }
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    @Test fun `the long side is 1568 at most, a smaller photo keeps its size`() {
        assertEquals(1568 to 784, PhotoPrep.fit(4000, 2000))
        assertEquals(1176 to 1568, PhotoPrep.fit(3000, 4000))
        assertEquals(1568 to 1568, PhotoPrep.fit(1568, 1568))
        assertEquals(800 to 600, PhotoPrep.fit(800, 600))
        assertEquals(2, PhotoPrep.sampleSize(4000, 3000))
        assertEquals(4, PhotoPrep.sampleSize(8000, 6000))
        assertEquals(1, PhotoPrep.sampleSize(3000, 2000))
        assertTrue(PhotoPrep.turns(6) && PhotoPrep.turns(8) && !PhotoPrep.turns(3) && !PhotoPrep.turns(1))
    }

    @Test fun `a camera's photo with GPS EXIF comes out upright, scaled, and without any metadata`() {
        val taken = TestJpegs.withSegments(jpeg(2000, 1000), TestJpegs.exif(orientation = 6), TestJpegs.comment("at home"))
        // what the camera wrote, as Android reads it
        ExifInterface(ByteArrayInputStream(taken)).let { e ->
            assertEquals(6, e.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
            assertTrue("the test photo has a place", e.getLatLong(FloatArray(2)))
            assertEquals(TestJpegs.MODEL, e.getAttribute(ExifInterface.TAG_MODEL))
        }

        val r = PhotoPrep.prepare(taken)

        // turned right (orientation 6): the 2000×1000 picture stands, its long side 1568
        assertEquals(784, r.width)
        assertEquals(1568, r.height)
        assertEquals(784 to 1568, Jpeg.size(r.jpeg))
        // no metadata at all: no EXIF segment, no place, no camera, no comment
        assertEquals(emptyList<String>(), Jpeg.metadata(r.jpeg))
        assertFalse(TestJpegs.contains(r.jpeg, "Exif"))
        assertFalse(TestJpegs.contains(r.jpeg, TestJpegs.MAKE))
        assertFalse(TestJpegs.contains(r.jpeg, TestJpegs.MODEL))
        assertFalse(TestJpegs.contains(r.jpeg, "at home"))
        ExifInterface(ByteArrayInputStream(r.jpeg)).let { e ->
            assertFalse("no place", e.getLatLong(FloatArray(2)))
            assertNull(e.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertNull(e.getAttribute(ExifInterface.TAG_MAKE))
            assertNull(e.getAttribute(ExifInterface.TAG_MODEL))
            assertEquals("no orientation left to turn it again", 0, e.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
        }
        // upright: what was the left (red) is the top now
        val out = BitmapFactory.decodeByteArray(r.jpeg, 0, r.jpeg.size)
        assertEquals(784, out.width)
        val top = out.getPixel(out.width / 2, out.height / 8)
        val bottom = out.getPixel(out.width / 2, out.height * 7 / 8)
        assertTrue("red on top: ${Integer.toHexString(top)}", Color.red(top) > 200 && Color.blue(top) < 80)
        assertTrue("blue below: ${Integer.toHexString(bottom)}", Color.blue(bottom) > 200 && Color.red(bottom) < 80)
    }

    @Test fun `a small photo keeps its size and is a JPEG of its own`() {
        val r = PhotoPrep.prepare(jpeg(640, 480))
        assertEquals(640, r.width)
        assertEquals(480, r.height)
        assertTrue(Jpeg.isJpeg(r.jpeg))
    }

    @Test fun `a transparent PNG becomes a JPEG on white`() {
        val b = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888) // all transparent
        val png = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val r = PhotoPrep.prepare(png)
        assertTrue(Jpeg.isJpeg(r.jpeg))
        val px = BitmapFactory.decodeByteArray(r.jpeg, 0, r.jpeg.size).getPixel(100, 50)
        assertTrue("white, not black: ${Integer.toHexString(px)}", Color.red(px) > 240 && Color.green(px) > 240 && Color.blue(px) > 240)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `what isn't a picture is refused`() {
        PhotoPrep.prepare("not a photo".toByteArray())
    }

    @Test fun `a file larger than the cap is refused while it is read`() {
        val big = object : java.io.InputStream() {
            var left = PhotoPrep.MAX_INPUT.toLong() + 10
            override fun read(): Int = if (left-- > 0) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = minOf(len.toLong(), left).toInt()
                left -= n
                return n
            }
        }
        val e = runCatching { PhotoPrep.read(big) }.exceptionOrNull()
        assertTrue("refused: $e", e is IllegalArgumentException)
        assertEquals(3, PhotoPrep.read(ByteArrayInputStream(byteArrayOf(1, 2, 3))).size)
    }
}
