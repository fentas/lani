package si.lanisce.lani.data

import java.io.ByteArrayOutputStream

/**
 * A JPEG's segments, as far as the chat's photos need them ([PhotoPrep]): its EXIF orientation, its size, and the file
 * without its metadata. Plain bytes, no Android, so a JVM test reads them as the phone does; the node strips the same
 * segments again when the photo arrives (companion/bridge/src/chat-images.ts).
 *
 * A JPEG is a list of segments, each `FF xx` and (but for a few markers) a two-byte length, up to the start of the
 * image data (SOS, `FF DA`), which runs to the end. The metadata are segments of their own: APP1 holds EXIF (the camera,
 * the time, the place) and XMP, APP13 IPTC, COM a comment.
 */
object Jpeg {
    /** Whether [b] starts as a JPEG does (`FF D8 FF`). */
    fun isJpeg(b: ByteArray): Boolean = b.size > 3 && b[0] == FF && b[1] == 0xD8.toByte() && b[2] == FF

    /** A segment before the image data: its [marker] (0xE1 for APP1 …), its bytes [start]..[end], its data from [data]. */
    private class Segment(val marker: Int, val start: Int, val end: Int, val data: Int)

    /** The segments before the image data, and where it begins (its SOS, or EOI); null when [b] isn't a JPEG read so. */
    private fun segments(b: ByteArray): Pair<List<Segment>, Int>? {
        if (!isJpeg(b)) return null
        val out = mutableListOf<Segment>()
        var i = 2
        while (i + 1 < b.size) {
            if (b[i] != FF) return null
            var j = i + 1
            while (j < b.size && b[j] == FF) j++ // fill bytes before the marker
            if (j >= b.size) return null
            val m = b[j].toInt() and 0xFF
            if (m == 0xDA || m == 0xD9) return out to i
            if (m in 0xD0..0xD7 || m == 0x01) {
                out += Segment(m, i, j + 1, j + 1)
                i = j + 1
                continue
            }
            if (j + 2 >= b.size) return null
            val len = u16(b, j + 1)
            if (len < 2 || j + 1 + len > b.size) return null
            out += Segment(m, i, j + 1 + len, j + 3)
            i = j + 1 + len
        }
        return null
    }

    /**
     * What a photo keeps: APP0 (JFIF), APP2 only for its colour profile (ICC_PROFILE), APP14 (Adobe's colour transform,
     * which some decoders need), and what isn't metadata (the tables, the frame). Everything else of APP1–APP15 (EXIF,
     * XMP, IPTC, a maker's notes, FlashPix) and the comments go.
     */
    private fun keeps(b: ByteArray, s: Segment): Boolean = when (s.marker) {
        0xE0, 0xEE -> true
        0xE2 -> startsWith(b, s.data, ICC)
        in 0xE1..0xEF, 0xFE -> false
        else -> true
    }

    /** [b] without its metadata ([keeps]); [b] itself when it isn't a JPEG this reads. */
    fun stripMetadata(b: ByteArray): ByteArray {
        val (segs, image) = segments(b) ?: return b
        val out = ByteArrayOutputStream(b.size)
        out.write(b, 0, 2)
        for (s in segs) if (keeps(b, s)) out.write(b, s.start, s.end - s.start)
        out.write(b, image, b.size - image)
        return out.toByteArray()
    }

    /** The metadata segments [b] has ("APP1 Exif", "APP13", "COM" …), for the tests and the log; empty when none. */
    fun metadata(b: ByteArray): List<String> = segments(b)?.first.orEmpty().filterNot { keeps(b, it) }.map { s ->
        when {
            s.marker == 0xFE -> "COM"
            s.marker == 0xE1 && startsWith(b, s.data, EXIF) -> "APP1 Exif"
            s.marker == 0xE1 -> "APP1"
            else -> "APP${s.marker - 0xE0}"
        }
    }

    /** The width and height its frame (SOFn) says, or null. */
    fun size(b: ByteArray): Pair<Int, Int>? {
        val sof = segments(b)?.first?.firstOrNull { it.marker in 0xC0..0xCF && it.marker != 0xC4 && it.marker != 0xC8 && it.marker != 0xCC } ?: return null
        if (sof.data + 5 > sof.end) return null
        return u16(b, sof.data + 3) to u16(b, sof.data + 1)
    }

    /**
     * The EXIF orientation (1 upright … 8, how the camera held it; 1 when there is none): the tag 0x0112 of the first
     * IFD in APP1's TIFF, in its byte order.
     */
    fun orientation(b: ByteArray): Int {
        val app1 = segments(b)?.first?.firstOrNull { it.marker == 0xE1 && startsWith(b, it.data, EXIF) } ?: return 1
        val tiff = app1.data + EXIF.size
        if (tiff + 8 > app1.end) return 1
        val little = b[tiff] == 'I'.code.toByte()
        fun n16(at: Int) = if (little) (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) else u16(b, at)
        fun n32(at: Int) = if (little) n16(at) or (n16(at + 2) shl 16) else (u16(b, at) shl 16) or u16(b, at + 2)
        val ifd = tiff + n32(tiff + 4)
        if (ifd < tiff || ifd + 2 > app1.end) return 1
        val count = n16(ifd)
        for (k in 0 until count) {
            val e = ifd + 2 + k * 12
            if (e + 12 > app1.end) return 1
            if (n16(e) == 0x0112) return n16(e + 8).takeIf { it in 1..8 } ?: 1
        }
        return 1
    }

    private const val FF = 0xFF.toByte()
    private val EXIF = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1)
    private val ICC = "ICC_PROFILE\u0000".toByteArray(Charsets.ISO_8859_1)

    private fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun startsWith(b: ByteArray, at: Int, prefix: ByteArray): Boolean =
        at + prefix.size <= b.size && prefix.indices.all { b[at + it] == prefix[it] }
}
