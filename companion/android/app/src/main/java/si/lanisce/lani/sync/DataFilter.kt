package si.lanisce.lani.sync

import java.io.File

/**
 * What the phone never pushes from its data directory (docs/plans/05-built/git-sync.md, "What never leaves the phone"):
 * what lani-setup's .gitignore keeps out (.claude/hooks/data-repo.gitignore, the one file the node reads too, bundled as
 * the resource sync/data-repo.gitignore: the secrets, the caches, the runtime files, the recent answers, the developer's
 * data, the chat's photos), and what the repository's own .gitignore keeps out. The shared rules come last, so a line of
 * the repository's can't bring a secret back in. `.git/` and the sync's own temporary files are never pushed either.
 *
 * The rules are git's, for what a data repository's .gitignore uses: comments, negation (!), anchored patterns (a slash
 * at the start or inside), directories (a slash at the end), *, ?, ** and [classes]. A file inside an ignored directory
 * stays ignored whatever comes after (as in git).
 */
class DataFilter(private val rules: List<Rule>) {
    /** One line of a .gitignore. */
    data class Rule(val regex: Regex, val negated: Boolean, val dirOnly: Boolean)

    /** Whether [rel] (a path from the data directory's root, with slashes) may be pushed. */
    fun pushes(rel: String): Boolean = !ignored(rel)

    fun ignored(rel: String): Boolean {
        val path = rel.trim('/')
        if (path.isEmpty()) return true
        val parts = path.split('/')
        if (parts.any { it == ".git" } || path.endsWith(TEMP_SUFFIX)) return true
        // a directory on the way that is ignored keeps everything in it out
        for (i in 1 until parts.size) if (verdict(parts.subList(0, i).joinToString("/"), dir = true)) return true
        return verdict(path, dir = false)
    }

    private fun verdict(path: String, dir: Boolean): Boolean {
        var out = false
        for (r in rules) {
            if (r.dirOnly && !dir) continue
            if (r.regex.matches(path)) out = !r.negated
        }
        return out
    }

    companion object {
        /** The suffix of the files the sync writes before renaming them into place. */
        const val TEMP_SUFFIX = ".lani-sync-tmp"

        /** The shared .gitignore (the resource sync/data-repo.gitignore), or "" when the build has none. */
        val sharedText: String by lazy {
            DataFilter::class.java.getResourceAsStream("/sync/data-repo.gitignore")?.use { it.readBytes().decodeToString() }.orEmpty()
        }

        /** The phone's filter for [dataDir]: its own .gitignore, then the shared rules. */
        fun of(dataDir: File, shared: String = sharedText): DataFilter {
            val own = File(dataDir, ".gitignore").takeIf { it.isFile }?.readText().orEmpty()
            return DataFilter(parse(own) + parse(shared))
        }

        fun parse(text: String): List<Rule> = text.lineSequence().mapNotNull(::rule).toList()

        /** A line of a .gitignore as a rule; null for a blank line or a comment. */
        fun rule(line: String): Rule? {
            var p = line.trimEnd()
            if (p.isBlank() || p.startsWith("#")) return null
            val negated = p.startsWith("!")
            if (negated) p = p.substring(1)
            if (p.startsWith("\\")) p = p.substring(1) // "\#file", "\!file"
            val dirOnly = p.endsWith("/")
            p = p.trimEnd('/')
            if (p.isEmpty()) return null
            val anchored = p.contains('/')
            p = p.trimStart('/')
            val body = globToRegex(p)
            return Rule(Regex(if (anchored) "^$body$" else "^(?:.*/)?$body$"), negated, dirOnly)
        }

        private fun globToRegex(glob: String): String {
            val sb = StringBuilder()
            var i = 0
            while (i < glob.length) {
                val c = glob[i]
                when {
                    c == '*' && glob.startsWith("**", i) -> {
                        val atStart = i == 0 || glob[i - 1] == '/'
                        val slashAfter = i + 2 < glob.length && glob[i + 2] == '/'
                        when {
                            atStart && slashAfter -> { sb.append("(?:.*/)?"); i += 3; continue }
                            i + 2 == glob.length -> { sb.append(".*"); i += 2; continue }
                            else -> { sb.append("[^/]*"); i += 2; continue }
                        }
                    }
                    c == '*' -> sb.append("[^/]*")
                    c == '?' -> sb.append("[^/]")
                    c == '[' -> {
                        val end = glob.indexOf(']', i + 2)
                        if (end < 0) sb.append("\\[") else {
                            var cls = glob.substring(i + 1, end)
                            if (cls.startsWith("!")) cls = "^" + cls.substring(1)
                            sb.append('[').append(cls.replace("\\", "\\\\")).append(']')
                            i = end + 1
                            continue
                        }
                    }
                    else -> sb.append(Regex.escape(c.toString()))
                }
                i++
            }
            return sb.toString()
        }
    }
}
