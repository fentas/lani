package si.lanisce.lani.phone

import android.content.Context
import java.io.File
import java.io.InputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * The curated content the phone's bridge reads (the culture packs, the modules, the packs, the dictionary …, with the
 * repository's layout: docs/plans/05-built/phone-runtime.md "Content on the phone"), copied from the APK's assets
 * (phone/project/, listed with its hash in phone/project.json: app/build.gradle.kts phoneProject) to [dir], when the
 * app's version or the content's hash differs from the last copy's. The copy goes to a directory beside [dir] and takes
 * its place at once, so the bridge never reads half of one.
 */
class Unpacker(
    private val open: (String) -> InputStream,
    /** The app's version: a new one unpacks again. */
    private val version: String,
) {
    data class Result(val unpacked: Boolean, val files: Int, val bytes: Long, val millis: Double, val hash: String)

    fun unpack(dir: File): Result {
        val t0 = System.nanoTime()
        val manifest = Json.parseToJsonElement(open("$ASSETS/project.json").use { it.readBytes().decodeToString() }).jsonObject
        val hash = manifest["hash"]!!.jsonPrimitive.content
        val files = manifest["files"]!!.jsonArray.map { it.jsonArray[0].jsonPrimitive.content to it.jsonArray[1].jsonPrimitive.long }
        val stamp = "$version $hash"
        val mark = File(dir, STAMP)
        if (mark.isFile && mark.readText().trim() == stamp) return Result(false, files.size, files.sumOf { it.second }, (System.nanoTime() - t0) / 1e6, hash)
        val parent = dir.absoluteFile.parentFile.apply { mkdirs() }
        val fresh = File(parent, "${dir.name}.new").apply { deleteRecursively(); mkdirs() }
        val buffer = ByteArray(64 * 1024)
        for ((rel, _) in files) {
            val to = File(fresh, rel)
            to.parentFile?.mkdirs()
            open("$ASSETS/project/$rel").use { input -> to.outputStream().use { out -> while (true) { val n = input.read(buffer); if (n < 0) break; out.write(buffer, 0, n) } } }
        }
        File(fresh, STAMP).writeText("$stamp\n")
        val old = File(parent, "${dir.name}.old").apply { deleteRecursively() }
        if (dir.exists() && !dir.renameTo(old)) dir.deleteRecursively()
        check(fresh.renameTo(dir)) { "the unpacked content could not take the place of $dir" }
        old.deleteRecursively()
        return Result(true, files.size, files.sumOf { it.second }, (System.nanoTime() - t0) / 1e6, hash)
    }

    companion object {
        const val ASSETS = "phone"
        const val STAMP = ".lani-unpacked"

        /** The app's own: its assets, its version (name and code). */
        fun of(context: Context): Unpacker {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            return Unpacker({ context.assets.open(it) }, "${info.versionName}+$code")
        }
    }
}
