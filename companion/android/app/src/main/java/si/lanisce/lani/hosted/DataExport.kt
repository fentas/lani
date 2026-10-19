package si.lanisce.lani.hosted

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import si.lanisce.lani.sync.DataFilter

/**
 * Export and import between the modes (plan 5, "Data"; docs/plans/05-built/hosted-mode.md "Export and import"): one zip
 * with the data repository's layout and a manifest, `lani.export/v1`, the same file `lani export` writes on a node
 * (.claude/hooks/lani_export.py) and `lani import` reads.
 *
 * - **What's in it:** what the data repository's sync takes ([DataFilter]: the shared .gitignore and the directory's
 *   own): the six databases, the other languages' (languages/), the bridge's files (app/: the village, the tutor's
 *   content, the towns), the session results. Never the secrets, the caches, the runtime files, the recent answers,
 *   the chat's photos. [MANIFEST] at the zip's root says what it holds: each file's size and SHA-256.
 * - **Import:** [unpack] reads a zip into a staging directory and checks it (the paths, the manifest's sizes and hashes,
 *   its format); the caller checks the data with the db port ([checkData]: validate-data on every JSON file, read-db
 *   over the whole); [replace] moves the phone's data aside (a local backup, the newest [KEEP_BACKUPS] kept) and puts the
 *   staging directory in its place, with the phone's own local files the export doesn't carry (its town key, its tokens).
 */
object DataExport {
    const val MANIFEST = "lani-export.json"
    const val FORMAT = "lani.export/v1"
    /** The two secrets an export may carry (`lani export --with-secrets`): the town's key, the family page's token. */
    val SECRETS = setOf("app/town-key.pem", "app/family-token")
    /**
     * This side's own files, as `lani import` keeps them: an export carries them, an import never takes them (the
     * export's lease would say the other side writes; its .gitignore could let this side's secrets into a commit).
     */
    val OWN = setOf(".gitignore", "app/lease.json")
    const val MAX_BYTES = 1L shl 30
    const val MAX_FILES = 50_000
    const val KEEP_BACKUPS = 3

    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    /** Something that keeps an export from being imported: the reason, for the learner. */
    class Problem(message: String) : IOException(message)

    data class FileEntry(val path: String, val size: Long, val sha256: String)

    /** What the manifest says (the parts the app reads). */
    data class Manifest(
        val format: String,
        val created: String,
        val from: JsonObject,
        val learner: JsonObject,
        val schemas: JsonObject,
        val files: List<FileEntry>,
        val secrets: List<String>,
    ) {
        val learnerName: String? get() = learner["name"]?.jsonPrimitive?.contentOrNull
        val fromSide: String? get() = from["side"]?.jsonPrimitive?.contentOrNull
        val fromName: String? get() = from["name"]?.jsonPrimitive?.contentOrNull
    }

    data class Written(val file: File, val files: Int, val bytes: Long, val manifest: Manifest)

    // --- export ------------------------------------------------------------------------------------------------------

    /** The files of [dataDir] an export holds, sorted (paths with slashes, from the directory's root). */
    fun files(dataDir: File, filter: DataFilter = DataFilter.of(dataDir)): List<String> =
        dataDir.walkTopDown()
            // a directory the rules keep out isn't walked (a file in it would be ignored: its parent counts as a directory)
            .onEnter { d -> d == dataDir || !filter.ignored(d.relativeTo(dataDir).invariantSeparatorsPath + "/-") }
            .filter { it.isFile }
            .map { it.relativeTo(dataDir).invariantSeparatorsPath }
            .filter { it != MANIFEST && filter.pushes(it) }
            .sorted()
            .toList()

    /** [dataDir] as an export in [out], from [from] (side, mode, name, app). */
    fun write(dataDir: File, out: File, from: JsonObject, now: Instant = Instant.now(), filter: DataFilter = DataFilter.of(dataDir)): Written {
        val paths = files(dataDir, filter)
        val entries = paths.map { p -> File(dataDir, p).let { FileEntry(p, it.length(), sha256(it)) } }
        val manifest = Manifest(
            FORMAT, DateTimeFormatter.ISO_INSTANT.format(now.truncatedTo(ChronoUnit.SECONDS)), from,
            learnerOf(File(dataDir, "learner-profile.json")), schemasOf(dataDir, paths), entries, emptyList(),
        )
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".tmp")
        ZipOutputStream(tmp.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(encode(manifest).toByteArray())
            zip.closeEntry()
            for (e in entries) {
                zip.putNextEntry(ZipEntry(e.path))
                File(dataDir, e.path).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        if (!tmp.renameTo(out)) {
            out.delete()
            if (!tmp.renameTo(out)) throw IOException("the export couldn't be written to $out")
        }
        return Written(out, entries.size, entries.sumOf { it.size }, manifest)
    }

    /** "lani-export-ana-2026-10-19.zip": the learner's name in plain letters, and the day. */
    fun fileName(learner: String?, now: Instant = Instant.now()): String {
        val name = java.text.Normalizer.normalize(learner.orEmpty(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24).ifBlank { "learner" }
        return "lani-export-$name-${now.atZone(ZoneOffset.UTC).toLocalDate()}.zip"
    }

    /** The learner's name and languages from learner-profile.json (placeholders count as none). */
    fun learnerOf(profile: File): JsonObject {
        val l = runCatching { Json.parseToJsonElement(profile.readText()).jsonObject["learner"]?.jsonObject }.getOrNull()
        fun plain(key: String): JsonElement = l?.get(key)?.let { it as? JsonPrimitive }?.contentOrNull?.trim()
            ?.takeIf { it.isNotEmpty() && !Regex("\\{[^{}]*\\}").containsMatchIn(it) }?.let(::JsonPrimitive) ?: JsonNull
        return buildJsonObject {
            put("name", plain("name"))
            put("target", plain("target_language_code"))
            put("base", plain("base_language_code"))
        }
    }

    /** Each JSON file's schema: its top-level "schema" text ("lani.towns/v1"), the village's state.schema (app/game.json). */
    fun schemasOf(dataDir: File, paths: List<String>): JsonObject = buildJsonObject {
        for (p in paths) {
            if (!p.endsWith(".json")) continue
            val o = runCatching { Json.parseToJsonElement(File(dataDir, p).readText()) as? JsonObject }.getOrNull() ?: continue
            if (p == "app/game.json") {
                (o["state"] as? JsonObject)?.get("schema")?.jsonPrimitive?.intOrNull?.let { put(p, it) }
                continue
            }
            (o["schema"] as? JsonPrimitive)?.takeIf { it.isString }?.let { put(p, it) }
        }
    }

    fun encode(m: Manifest): String = json.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("format", m.format)
        put("created", m.created)
        put("from", m.from)
        put("learner", m.learner)
        put("schemas", m.schemas)
        put("files", buildJsonArray { for (f in m.files) add(buildJsonObject { put("path", f.path); put("size", f.size); put("sha256", f.sha256) }) })
        put("secrets", JsonArray(m.secrets.map(::JsonPrimitive)))
    }) + "\n"

    fun decode(text: String): Manifest {
        val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrElse { throw Problem("not a Lani export: its manifest isn't JSON") }
        val format = o["format"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val v = Regex("^lani\\.export/v(\\d+)$").matchEntire(format)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw Problem("not a Lani export (${format.ifBlank { "no format" }})")
        if (v > 1) throw Problem("made by a newer Lani ($format): update this app first")
        val files = runCatching {
            o["files"]!!.jsonArray.map { f ->
                val e = f.jsonObject
                FileEntry(e["path"]!!.jsonPrimitive.content, e["size"]!!.jsonPrimitive.longOrNull!!, e["sha256"]!!.jsonPrimitive.content.lowercase())
            }
        }.getOrElse { throw Problem("not a Lani export: its list of files can't be read") }
        return Manifest(
            format, o["created"]?.jsonPrimitive?.contentOrNull.orEmpty(), o["from"] as? JsonObject ?: JsonObject(emptyMap()),
            o["learner"] as? JsonObject ?: JsonObject(emptyMap()), o["schemas"] as? JsonObject ?: JsonObject(emptyMap()), files,
            (o["secrets"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
        )
    }

    // --- import ------------------------------------------------------------------------------------------------------

    /** Whether [name] is a path an export may hold: relative, no "..", no backslash or NUL, nothing of git's. */
    fun safePath(name: String): Boolean {
        if (name.isEmpty() || name.startsWith("/") || name.contains('\\') || name.contains('\u0000')) return false
        val parts = name.split('/')
        return parts.none { it.isEmpty() || it == "." || it == ".." || it == ".git" }
    }

    /**
     * The zip from [input] unpacked into [staging] (made empty first) and checked against its manifest: every entry
     * listed, every listed file there, their sizes and hashes; at most [MAX_FILES] files and [MAX_BYTES]. Nothing that the
     * export's rules keep out ([filter]) unless it is one of the [SECRETS] the manifest names. [Problem] otherwise.
     */
    fun unpack(input: InputStream, staging: File, filter: DataFilter? = null): Manifest {
        staging.deleteRecursively()
        staging.mkdirs()
        val root = staging.canonicalFile
        var manifestText: String? = null
        val seen = LinkedHashMap<String, Pair<Long, String>>()
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name
                    if (name == MANIFEST) {
                        manifestText = zip.readBytes().decodeToString()
                        continue
                    }
                    if (!safePath(name)) throw Problem("the export holds a path it may not: $name")
                    if (seen.size >= MAX_FILES) throw Problem("the export holds too many files")
                    val to = File(root, name).canonicalFile
                    if (!to.path.startsWith(root.path + File.separator)) throw Problem("the export holds a path it may not: $name")
                    if (seen.containsKey(name)) throw Problem("the export holds $name twice")
                    to.parentFile?.mkdirs()
                    val digest = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    to.outputStream().use { out ->
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            size += n
                            total += n
                            if (total > MAX_BYTES) throw Problem("the export is too big (over 1 GB)")
                            digest.update(buffer, 0, n)
                            out.write(buffer, 0, n)
                        }
                    }
                    seen[name] = size to digest.digest().joinToString("") { "%02x".format(it) }
                }
            }
        } catch (e: Problem) {
            throw e
        } catch (e: java.util.zip.ZipException) {
            throw Problem("not a Lani export: ${e.message ?: "not a zip"}")
        }
        val m = decode(manifestText ?: throw Problem(if (seen.isEmpty()) "not a Lani export (not a zip, or empty)" else "not a Lani export: no $MANIFEST"))
        // the export's own .gitignore first, the shared rules last: a "!" of its own can't bring a secret in
        val rules = filter ?: DataFilter.of(staging)
        val listed = m.files.associateBy { it.path }
        for (name in seen.keys) if (name !in listed) throw Problem("the export holds a file its manifest doesn't list: $name")
        for (s in m.secrets) if (s !in SECRETS || s !in listed) throw Problem("the export names a secret it may not carry: $s")
        for (f in m.files) {
            val (size, sha) = seen[f.path] ?: throw Problem("the export misses ${f.path}")
            if (size != f.size || sha != f.sha256) throw Problem("${f.path} isn't what the export's manifest says (damaged?)")
            val secret = f.path in SECRETS && f.path in m.secrets
            if (!secret && rules.ignored(f.path)) throw Problem("the export holds a file that never leaves a data repository: ${f.path}")
        }
        if (!File(staging, "learner-profile.json").isFile) throw Problem("the export has no learner profile")
        return m
    }

    /**
     * The unpacked data checked with the db port ([db]: PhoneBridge.db): validate-data on every JSON file (it parses),
     * read-db over the directory (the six databases load). The problems, none when it's fine. validate-data's backup
     * copies are removed again.
     */
    suspend fun checkData(staging: File, db: suspend (String) -> String): List<String> {
        val problems = mutableListOf<String>()
        val before = staging.walkTopDown().filter { it.isFile }.map { it.path }.toSet()
        val env = buildJsonObject { put("LANI_DATA_DIR", staging.path) }
        for (f in staging.walkTopDown().filter { it.isFile && it.name.endsWith(".json") && it.name != MANIFEST }.sortedBy { it.path }.toList()) {
            val call = buildJsonObject {
                put("script", "validate-data")
                put("stdin", buildJsonObject { put("tool_input", buildJsonObject { put("file_path", f.path) }) }.toString())
                put("env", env)
                put("cwd", staging.path)
            }
            val r = Json.parseToJsonElement(db(call.toString())).jsonObject
            val status = r["status"]?.jsonPrimitive?.intOrNull ?: 1
            if (status != 0) problems += "${f.relativeTo(staging).invariantSeparatorsPath}: " + (r["stderr"]?.jsonPrimitive?.contentOrNull.orEmpty().lines().firstOrNull { it.contains("Error") || it.contains("WARNING") } ?: "not valid")
        }
        staging.walkTopDown().filter { it.isFile && it.path !in before && it.name.contains(".backup-") }.forEach { it.delete() }
        // read-db on a copy of the databases (the top's and the other languages'): its migrations may write, and what
        // takes the data's place is the export as it came (as `lani import` checks it)
        val copy = File(staging.parentFile, "${staging.name}.check").apply { deleteRecursively(); mkdirs() }
        try {
            staging.listFiles { f -> f.isFile && f.name.endsWith(".json") && f.name != MANIFEST }?.forEach { it.copyTo(File(copy, it.name)) }
            File(staging, "languages").listFiles { f -> f.isDirectory }?.forEach { d ->
                d.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach { it.copyTo(File(copy, "languages/${d.name}/${it.name}")) }
            }
            val env2 = buildJsonObject { put("LANI_DATA_DIR", copy.path) }
            val read = Json.parseToJsonElement(db(buildJsonObject { put("script", "read-db"); put("env", env2); put("cwd", copy.path) }.toString())).jsonObject
            val status = read["status"]?.jsonPrimitive?.intOrNull ?: 1
            val out = read["stdout"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (status != 0 || runCatching { Json.parseToJsonElement(out).jsonObject }.isFailure) {
                problems += "read-db: " + (read["stderr"]?.jsonPrimitive?.contentOrNull.orEmpty().trim().lines().lastOrNull() ?: "the databases don't load")
            }
        } finally {
            copy.deleteRecursively()
        }
        return problems
    }

    /**
     * [staging] (unpacked and checked, its manifest removed) takes [dataDir]'s place. The data there goes to
     * `<dataDir>.before-import-<time>` first (the newest [KEEP_BACKUPS] kept); the phone's own local files the export
     * doesn't carry (what the rules keep out: its tokens, its town key, its caches) and its [OWN] files come along into
     * the new data. The backup directory, or null when there was no data.
     */
    fun replace(dataDir: File, staging: File, now: Instant = Instant.now(), filter: DataFilter = DataFilter.of(dataDir)): File? {
        File(staging, MANIFEST).delete()
        for (own in OWN) File(staging, own).delete()
        val parent = dataDir.absoluteFile.parentFile
        val backup = setAside(dataDir, "before-import", now, keep = false)
        if (backup != null) {
            backup.walkTopDown()
                .onEnter { d -> d == backup || d.name != ".git" }
                .filter { it.isFile }
                .forEach { f ->
                    val rel = f.relativeTo(backup).invariantSeparatorsPath
                    val to = File(staging, rel)
                    if ((rel in OWN || filter.ignored(rel)) && !to.exists() && !rel.endsWith(DataFilter.TEMP_SUFFIX)) {
                        to.parentFile?.mkdirs()
                        f.copyTo(to)
                    }
                }
        }
        if (!staging.renameTo(dataDir)) {
            // put the old data back: nothing is lost
            backup?.renameTo(dataDir)
            throw IOException("the imported data couldn't take the data's place")
        }
        prune(parent, "${dataDir.name}.before-import-")
        return backup
    }

    /**
     * [dataDir] moved aside as `<dataDir>.<label>-<time>` (an import's backup, a fresh start over a learner's data), the
     * newest [KEEP_BACKUPS] of that label kept when [keep]; null when there was nothing to move.
     */
    fun setAside(dataDir: File, label: String, now: Instant = Instant.now(), keep: Boolean = true): File? {
        if (!dataDir.exists()) return null
        val parent = dataDir.absoluteFile.parentFile
        val to = File(parent, "${dataDir.name}.$label-${stamp.format(now)}").apply { deleteRecursively() }
        if (!dataDir.renameTo(to)) throw IOException("the data couldn't be moved aside")
        if (keep) prune(parent, "${dataDir.name}.$label-")
        return to
    }

    private fun prune(parent: File, prefix: String) {
        parent.listFiles { f -> f.isDirectory && f.name.startsWith(prefix) }
            ?.sortedByDescending { it.name }?.drop(KEEP_BACKUPS)?.forEach { it.deleteRecursively() }
    }

    fun sha256(f: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                d.update(buf, 0, n)
            }
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }
}
