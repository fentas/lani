package si.lanisce.lani.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.util.LruCache
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A photo sent in the tutor chat (📷: a sign, a menu, a page, a worksheet, a thing at home), as its message keeps it:
 * [id] the message's client id, which names the file on the phone ([ChatPhotoStore]) and on the node
 * (`app/chat-images/<YYYY-MM>/<id>.jpg`); its size as sent ([width]×[height], [bytes]); [deleted] once the learner
 * deleted it, on the phone and on the node (the message and its text stay).
 */
@Serializable
data class ChatPhoto(val id: String, val width: Int, val height: Int, val bytes: Long, val deleted: Boolean = false)

/** A photo ready to send ([PhotoPrep.prepare]): a JPEG, upright, its long side at most [PhotoPrep.LONG_SIDE], no metadata. */
class ReadyPhoto(val jpeg: ByteArray, val width: Int, val height: Int)

/**
 * A photo made ready for the tutor, on the phone, before it goes anywhere: turned upright by its EXIF orientation, scaled
 * to a long side of [LONG_SIDE] px (what Claude reads an image at without scaling it again; smaller ones stay as they
 * are), JPEG at quality [QUALITY], and without any metadata: no place, no time, no camera, no device. The bitmap is
 * encoded afresh (Android writes none of the old metadata), then [Jpeg.stripMetadata] takes out whatever a segment
 * might still carry, a second net.
 */
object PhotoPrep {
    const val LONG_SIDE = 1568
    const val QUALITY = 80
    /** The largest file read at all: a camera's photo at full size is about 3 to 25 MB. */
    const val MAX_INPUT = 48 * 1024 * 1024

    /** [w]×[h] scaled to a long side of at most [long], its proportions kept (a smaller one as it is). */
    fun fit(w: Int, h: Int, long: Int = LONG_SIDE): Pair<Int, Int> {
        val m = max(w, h)
        if (m <= long) return w to h
        val s = long.toDouble() / m
        return max(1, (w * s).roundToInt()) to max(1, (h * s).roundToInt())
    }

    /** How much smaller to decode [w]×[h] (a power of two) so that its long side stays at least [long]: less memory. */
    fun sampleSize(w: Int, h: Int, long: Int = LONG_SIDE): Int {
        var n = 1
        while (max(w, h) / (n * 2) >= long) n *= 2
        return n
    }

    /** Whether EXIF [orientation] turns the picture by 90°: its width and height swap. */
    fun turns(orientation: Int): Boolean = orientation in 5..8

    /** [m] with what sets a picture of EXIF [orientation] upright (2 mirrored … 6 turned right … 8 turned left). */
    fun orient(m: Matrix, orientation: Int) {
        when (orientation) {
            2 -> m.postScale(-1f, 1f)
            3 -> m.postRotate(180f)
            4 -> m.postScale(1f, -1f)
            5 -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            6 -> m.postRotate(90f)
            7 -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
            8 -> m.postRotate(-90f)
        }
    }

    /** [bytes]' EXIF orientation: Android's reader (JPEG, PNG, WebP, HEIF), else [Jpeg.orientation]; 1 when none. */
    fun orientationOf(bytes: ByteArray): Int =
        runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
            .getOrNull()?.takeIf { it in 1..8 } ?: Jpeg.orientation(bytes)

    /** What [input] holds (a photo picked or taken), at most [MAX_INPUT] bytes: a larger one throws before it fills the memory. */
    fun read(input: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            require(out.size() <= MAX_INPUT) { "the photo is too large (more than ${MAX_INPUT / 1_048_576} MB)" }
        }
        return out.toByteArray()
    }

    /** [bytes] (a JPEG, PNG, WebP, HEIF … as picked or taken) ready to send; throws when it isn't an image Android decodes. */
    fun prepare(bytes: ByteArray): ReadyPhoto {
        require(bytes.size <= MAX_INPUT) { "the photo is too large (${bytes.size / 1_048_576} MB)" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not an image" }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) })
            ?: throw IllegalArgumentException("not an image")
        val orientation = orientationOf(bytes)
        val (tw, th) = fit(decoded.width, decoded.height)
        val m = Matrix().apply {
            setScale(tw / decoded.width.toFloat(), th / decoded.height.toFloat())
            orient(this, orientation)
        }
        val turned = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        // a JPEG has no transparency: what was transparent (a PNG, a screenshot) on white, not black
        val opaque = if (!turned.hasAlpha()) turned else Bitmap.createBitmap(turned.width, turned.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(turned, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG)) }
        }
        val out = ByteArrayOutputStream()
        check(opaque.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)) { "the photo couldn't be encoded" }
        val jpeg = Jpeg.stripMetadata(out.toByteArray())
        val r = ReadyPhoto(jpeg, opaque.width, opaque.height)
        for (b in setOf(decoded, turned, opaque)) b.recycle()
        return r
    }
}

/**
 * The chat's photos on the phone: `files/chat-photos/<id>.jpg`, the app's private files (never shared: the share sheet's
 * provider reaches only the cache's shared/). A photo stays while a message keeps it: [sweep] lets the others go (a
 * draft never sent, a message the chat let go after its year, ChatStore). Shown scaled ([image]), a few kept in memory.
 */
class ChatPhotoStore(val dir: File) {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun file(id: String): File = File(dir, "$id.jpg")

    fun has(id: String): Boolean = file(id).isFile

    /** Keeps [bytes] as photo [id] (through a temporary file, so a photo is never half there). */
    fun save(id: String, bytes: ByteArray) {
        dir.mkdirs()
        val tmp = File(dir, "$id.jpg.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file(id))) {
            file(id).delete()
            tmp.renameTo(file(id))
        }
    }

    fun delete(id: String) {
        file(id).delete()
        synchronized(cache) { cache.snapshot().keys.filter { it.startsWith("$id@") }.forEach(cache::remove) }
    }

    /** Photo [id] with its long side at most [maxPx] (decoded as small as that allows), or null when the phone hasn't it. */
    fun image(id: String, maxPx: Int): Bitmap? {
        val key = "$id@$maxPx"
        synchronized(cache) { cache.get(key) }?.let { return it }
        val f = file(id)
        if (!f.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null
        val b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = PhotoPrep.sampleSize(bounds.outWidth, bounds.outHeight, maxPx) }) ?: return null
        synchronized(cache) { cache.put(key, b) }
        return b
    }

    /** The photos no message keeps any more ([keep]: their ids), older than a day (a draft being made is newer), deleted. */
    fun sweep(keep: Set<String>, now: Long): List<String> {
        val gone = dir.listFiles { f -> f.isFile && (f.name.endsWith(".jpg") || f.name.endsWith(".tmp")) }.orEmpty()
            .filter { f -> f.name.substringBefore('.') !in keep && now - f.lastModified() > DAY }
        gone.forEach { it.delete() }
        return gone.map { it.name.substringBefore('.') }
    }

    private companion object {
        const val DAY = 24 * 3_600_000L
    }
}
