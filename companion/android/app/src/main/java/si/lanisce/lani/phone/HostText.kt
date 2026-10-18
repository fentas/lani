package si.lanisce.lani.phone

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Text and bytes for the phone bridge's shims (companion/bridge/phone/shims/buffer.ts, zlib.ts, prelude.ts): UTF-8,
 * base64 as node reads and writes it, gzip, and a collator's order for localeCompare (ICU: Android's android.icu, ICU4J
 * in the JVM tests when it's there, else Java's own), as node's ICU orders strings.
 */
object HostText {
    fun utf8Encode(s: String): ByteArray = s.toByteArray(Charsets.UTF_8)

    fun utf8Decode(b: ByteArray): String = String(b, Charsets.UTF_8)

    /** Node's base64 (padded) or base64url (unpadded). */
    fun base64Encode(b: ByteArray, url: Boolean): String =
        if (url) Base64.getUrlEncoder().withoutPadding().encodeToString(b) else Base64.getEncoder().encodeToString(b)

    /** As node decodes: either alphabet, padded or not, whitespace and other characters skipped. */
    fun base64Decode(s: String): ByteArray {
        val clean = s.replace('-', '+').replace('_', '/').filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' }
        val usable = if (clean.length % 4 == 1) clean.dropLast(1) else clean
        return Base64.getDecoder().decode(usable + "=".repeat((4 - usable.length % 4) % 4))
    }

    fun gunzip(b: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }

    fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(b) } }.toByteArray()

    private val collators = ConcurrentHashMap<String, Comparator<Any>>()

    /** [a] against [b] in [locale]'s order ("" the root's): -1, 0 or 1. */
    fun collate(a: String, b: String, locale: String): Int = collators.getOrPut(locale) { collator(locale) }.compare(a, b).coerceIn(-1, 1)

    @Suppress("UNCHECKED_CAST")
    private fun collator(locale: String): Comparator<Any> {
        val l = if (locale.isBlank()) Locale.ROOT else Locale.forLanguageTag(locale)
        for (cls in listOf("android.icu.text.Collator", "com.ibm.icu.text.Collator")) {
            try {
                val c = Class.forName(cls).getMethod("getInstance", Locale::class.java).invoke(null, l) as Comparator<Any>?
                if (c != null && c.compare("a", "b") < 0) return c
            } catch (_: Throwable) {
                // not on this platform: the next
            }
        }
        return java.text.Collator.getInstance(l) as Comparator<Any>
    }
}
