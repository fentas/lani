package si.lanisce.lani.data

import java.io.ByteArrayOutputStream

/**
 * JPEG bytes for the photo tests, made by hand: an EXIF segment as a phone's camera writes one (APP1: the orientation,
 * the camera's make and model, and a GPS IFD with the place), put into a JPEG after its start.
 */
object TestJpegs {
    /** The camera the EXIF names (its Make and Model tags): neither may be left in a photo sent. */
    const val MAKE = "QaPhoneMaker"
    const val MODEL = "QaPhone 9 Pro"

    private class Tiff {
        val b = ByteArrayOutputStream()
        fun u16(v: Int) { b.write(v shr 8 and 0xFF); b.write(v and 0xFF) }
        fun u32(v: Long) { u16((v shr 16).toInt() and 0xFFFF); u16(v.toInt() and 0xFFFF) }
        fun bytes(a: ByteArray) = b.write(a)
        val size get() = b.size()
    }

    /**
     * An APP1 Exif segment (big-endian TIFF): IFD0 with Make, Model, Orientation [orientation] and a pointer to the GPS IFD,
     * which holds GPSLatitudeRef N, GPSLatitude 46/1 3/1 55/10, GPSLongitudeRef E, GPSLongitude 14/1 30/1 0/1.
     */
    fun exif(orientation: Int = 6): ByteArray {
        val make = (MAKE + "\u0000").toByteArray(Charsets.ISO_8859_1)
        val model = (MODEL + "\u0000").toByteArray(Charsets.ISO_8859_1)
        // offsets from the TIFF header's start
        val ifd0 = 8
        val ifd0Entries = 4
        val ifd0End = ifd0 + 2 + ifd0Entries * 12 + 4
        val makeAt = ifd0End
        val modelAt = makeAt + make.size
        val gpsAt = modelAt + model.size
        val gpsEntries = 4
        val gpsEnd = gpsAt + 2 + gpsEntries * 12 + 4
        val latAt = gpsEnd
        val lonAt = latAt + 24
        val t = Tiff()
        t.bytes("MM".toByteArray()); t.u16(42); t.u32(ifd0.toLong())
        t.u16(ifd0Entries)
        t.u16(0x010F); t.u16(2); t.u32(make.size.toLong()); t.u32(makeAt.toLong())
        t.u16(0x0110); t.u16(2); t.u32(model.size.toLong()); t.u32(modelAt.toLong())
        t.u16(0x0112); t.u16(3); t.u32(1); t.u16(orientation); t.u16(0)
        t.u16(0x8825); t.u16(4); t.u32(1); t.u32(gpsAt.toLong())
        t.u32(0)
        t.bytes(make); t.bytes(model)
        check(t.size == gpsAt)
        t.u16(gpsEntries)
        t.u16(0x0001); t.u16(2); t.u32(2); t.bytes("N\u0000\u0000\u0000".toByteArray(Charsets.ISO_8859_1))
        t.u16(0x0002); t.u16(5); t.u32(3); t.u32(latAt.toLong())
        t.u16(0x0003); t.u16(2); t.u32(2); t.bytes("E\u0000\u0000\u0000".toByteArray(Charsets.ISO_8859_1))
        t.u16(0x0004); t.u16(5); t.u32(3); t.u32(lonAt.toLong())
        t.u32(0)
        check(t.size == latAt)
        for ((n, d) in listOf(46L to 1L, 3L to 1L, 55L to 10L, 14L to 1L, 30L to 1L, 0L to 1L)) { t.u32(n); t.u32(d) }
        val tiff = t.b.toByteArray()
        val data = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff
        return byteArrayOf(0xFF.toByte(), 0xE1.toByte(), ((data.size + 2) shr 8).toByte(), ((data.size + 2) and 0xFF).toByte()) + data
    }

    /** A comment segment (COM) with [text]. */
    fun comment(text: String): ByteArray {
        val data = text.toByteArray()
        return byteArrayOf(0xFF.toByte(), 0xFE.toByte(), ((data.size + 2) shr 8).toByte(), ((data.size + 2) and 0xFF).toByte()) + data
    }

    /** [jpeg] with [segments] right after its start (FF D8), before its own. */
    fun withSegments(jpeg: ByteArray, vararg segments: ByteArray): ByteArray =
        jpeg.copyOfRange(0, 2) + segments.fold(ByteArray(0)) { a, s -> a + s } + jpeg.copyOfRange(2, jpeg.size)

    /**
     * A tiny JPEG's frame without real image data, for the segment tests (no decoder reads it): SOI, APP0 JFIF, DQT,
     * SOF0 [width]×[height], DHT, SOS and a few bytes of "scan", EOI.
     */
    fun skeleton(width: Int, height: Int): ByteArray {
        val o = ByteArrayOutputStream()
        fun seg(marker: Int, data: ByteArray) {
            o.write(0xFF); o.write(marker); o.write((data.size + 2) shr 8); o.write((data.size + 2) and 0xFF); o.write(data)
        }
        o.write(0xFF); o.write(0xD8)
        seg(0xE0, "JFIF\u0000".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(1, 1, 0, 0, 1, 0, 1, 0, 0))
        seg(0xDB, ByteArray(65) { if (it == 0) 0 else 1 })
        seg(0xC0, byteArrayOf(8, (height shr 8).toByte(), height.toByte(), (width shr 8).toByte(), width.toByte(), 1, 1, 0x11, 0))
        seg(0xC4, ByteArray(29) { if (it == 0) 0 else if (it == 1) 1 else 0 })
        seg(0xDA, byteArrayOf(1, 1, 0, 0, 0x3F, 0))
        o.write(byteArrayOf(0x12, 0x34, 0xFF.toByte(), 0x00, 0x56))
        o.write(0xFF); o.write(0xD9)
        return o.toByteArray()
    }

    /** Whether [b] holds [s] (as Latin-1 bytes) anywhere. */
    fun contains(b: ByteArray, s: String): Boolean {
        val n = s.toByteArray(Charsets.ISO_8859_1)
        return (0..b.size - n.size).any { i -> n.indices.all { b[i + it] == n[it] } }
    }
}
