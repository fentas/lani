package si.lanisce.lani.phone

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A file call refused or failed as node's would: "<CODE>: <text>" (the bundle's shims/host.ts errno reads it). */
class FsError(val code: String, text: String) : Exception("$code: $text")

/**
 * The phone bridge's files (node:fs over them: companion/bridge/phone/shims/fs.ts): only under [roots], each writable
 * or read only (the most specific root of a path decides: the curated content under <filesDir>/lani/project is read
 * only inside the writable <filesDir>/lani). A path is made canonical first (`..` and symlinks resolved), so nothing
 * reaches outside: anything else is EACCES, a write into a read-only root EROFS. Node's semantics where the bridge
 * relies on them: a write needs its directory, rename replaces, mkdir -p, rm -r with force, readdir in the
 * directory's own order.
 */
class HostFs(roots: List<Root>) {
    class Root(dir: File, val writable: Boolean) {
        val dir: File = dir.canonicalFile
    }

    private val roots = roots.sortedByDescending { it.dir.path.length }

    /** [path] inside a root (canonical), for a write when [write]. */
    fun resolve(path: String, write: Boolean = false): File {
        if (!path.startsWith("/")) throw FsError("EINVAL", "not an absolute path")
        val file = File(path).canonicalFile
        val root = roots.firstOrNull { file == it.dir || file.startsWith(it.dir) } ?: throw FsError("EACCES", "permission denied")
        if (write && !root.writable) throw FsError("EROFS", "read-only file system")
        return file
    }

    private fun existing(path: String, write: Boolean = false): File =
        resolve(path, write).also { if (!it.exists()) throw FsError("ENOENT", "no such file or directory") }

    private fun file(path: String): File = existing(path).also { if (it.isDirectory) throw FsError("EISDIR", "illegal operation on a directory") }

    fun readText(path: String): String = file(path).readText(Charsets.UTF_8)

    fun readBytes(path: String): ByteArray = file(path).readBytes()

    fun write(path: String, bytes: ByteArray, append: Boolean, exclusive: Boolean) {
        val f = resolve(path, write = true)
        if (f.isDirectory) throw FsError("EISDIR", "illegal operation on a directory")
        if (f.parentFile?.isDirectory != true) throw FsError("ENOENT", "no such file or directory")
        if (exclusive) {
            if (!f.createNewFile()) throw FsError("EEXIST", "file already exists")
        }
        FileOutputStream(f, append).use { it.write(bytes) }
    }

    fun exists(path: String): Boolean = try {
        resolve(path).exists()
    } catch (_: FsError) {
        false
    }

    /** JSON: {type, size, mtimeMs, ctimeMs, atimeMs, birthtimeMs, mode}, the times in milliseconds with their fraction. */
    fun stat(path: String): String {
        val f = existing(path)
        val a = Files.readAttributes(f.toPath(), BasicFileAttributes::class.java)
        val ms = { t: java.nio.file.attribute.FileTime -> t.to(TimeUnit.MICROSECONDS) / 1000.0 }
        val type = if (a.isDirectory) "dir" else if (a.isRegularFile) "file" else "other"
        return buildJsonObject {
            put("type", type)
            put("size", if (a.isDirectory) 4096 else a.size())
            put("mtimeMs", ms(a.lastModifiedTime()))
            put("ctimeMs", ms(a.lastModifiedTime()))
            put("atimeMs", ms(a.lastAccessTime()))
            put("birthtimeMs", ms(a.creationTime()))
            put("mode", if (a.isDirectory) 0x41ed else 0x81a4)
        }.toString()
    }

    /** JSON: [[name, type]…] in the directory's order, as Bun's readdirSync gives them (node's sorts them). */
    fun readdir(path: String): String {
        val dir = existing(path)
        if (!dir.isDirectory) throw FsError("ENOTDIR", "not a directory")
        val names = dir.list() ?: throw FsError("EACCES", "permission denied")
        return JsonArray(names.map { n ->
            val c = File(dir, n)
            JsonArray(listOf(JsonPrimitive(n), JsonPrimitive(if (c.isDirectory) "dir" else if (c.isFile) "file" else "other")))
        }).toString()
    }

    fun mkdir(path: String, recursive: Boolean) {
        val f = resolve(path, write = true)
        if (f.exists()) {
            if (recursive && f.isDirectory) return
            throw FsError("EEXIST", "file already exists")
        }
        if (recursive) {
            if (!f.mkdirs() && !f.isDirectory) throw FsError(if (generateSequence(f) { it.parentFile }.any { it.isFile }) "ENOTDIR" else "EACCES", "cannot make the directory")
        } else {
            if (f.parentFile?.isDirectory != true) throw FsError("ENOENT", "no such file or directory")
            if (!f.mkdir()) throw FsError("EACCES", "permission denied")
        }
    }

    fun rename(from: String, to: String) {
        val a = existing(from, write = true)
        val b = resolve(to, write = true)
        if (b.parentFile?.isDirectory != true) throw FsError("ENOENT", "no such file or directory")
        if (b.isDirectory && !a.isDirectory) throw FsError("EISDIR", "illegal operation on a directory")
        if (b.isDirectory && (b.list()?.isNotEmpty() == true)) throw FsError("ENOTEMPTY", "directory not empty")
        try {
            Files.move(a.toPath(), b.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            // a file system without an atomic move with replace: rename(2) through File, which replaces on POSIX
            if (!a.renameTo(b)) throw FsError("EIO", "rename failed")
        }
    }

    fun unlink(path: String) {
        val f = existing(path, write = true)
        if (f.isDirectory) throw FsError("EISDIR", "illegal operation on a directory")
        if (!f.delete()) throw FsError("EACCES", "permission denied")
    }

    fun rmdir(path: String) {
        val f = existing(path, write = true)
        if (!f.isDirectory) throw FsError("ENOTDIR", "not a directory")
        if (f.list()?.isNotEmpty() == true) throw FsError("ENOTEMPTY", "directory not empty")
        if (!f.delete()) throw FsError("EACCES", "permission denied")
    }

    fun rm(path: String, recursive: Boolean, force: Boolean) {
        val f = resolve(path, write = true)
        if (!f.exists()) {
            if (force) return
            throw FsError("ENOENT", "no such file or directory")
        }
        if (f.isDirectory) {
            if (!recursive) throw FsError("EISDIR", "is a directory")
            if (!f.deleteRecursively()) throw FsError("EACCES", "permission denied")
        } else if (!f.delete()) throw FsError("EACCES", "permission denied")
    }

    fun copy(from: String, to: String, exclusive: Boolean) {
        val a = file(from)
        val b = resolve(to, write = true)
        if (b.parentFile?.isDirectory != true) throw FsError("ENOENT", "no such file or directory")
        if (exclusive && b.exists()) throw FsError("EEXIST", "file already exists")
        if (b.isDirectory) throw FsError("EISDIR", "illegal operation on a directory")
        Files.copy(a.toPath(), b.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun realpath(path: String): String = existing(path).path

    /** The app's files keep their own modes: only whether a write there would be allowed. */
    fun chmod(path: String) {
        existing(path, write = true)
    }
}
