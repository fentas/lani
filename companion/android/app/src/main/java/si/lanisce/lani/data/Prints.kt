package si.lanisce.lani.data

import java.net.URLEncoder

/**
 * What the school prints (companion/COURSE.md, "Printed sheets"): a unit's sheet ([Sheet], its number [n] in its level
 * as the sheets number it), a level's [Booklet] or an [Overview] (the cases overview, by its grammar page [Overview.page]),
 * all made by the bridge (GET /course/sheet/<unit>, /course/booklet/<level>, /course/overview/<page>) as the generator
 * makes them, and printed through Android's print dialog (ui/school/SchoolPrint). An overview is of no level.
 */
sealed interface PrintJob {
    val level: String?

    data class Sheet(val unit: String, override val level: String, val n: Int) : PrintJob
    data class Booklet(override val level: String) : PrintJob
    data class Overview(val page: String, val name: String) : PrintJob {
        override val level: String? get() = null
    }
}

/** The bridge has no such page to print: an older bridge, or a unit it doesn't have (the app's own course is newer). */
class PrintMissing : java.io.IOException("no such sheet on the bridge")

/** The print's request and its job's name. Pure. */
object Prints {
    /** The languages the sheets explain in; for another base the bridge prints the learner's own (English where it has none). */
    val BASES = setOf("en", "de", "it")

    /**
     * The overviews the bridge prints (companion/prints/overview.ts), by "<language>/<grammar page>": their names as the
     * print job has them. Today the six Slovene cases, "Pregled sklonov", from the page skloni.
     */
    private val OVERVIEWS = mapOf("sl/skloni" to "Pregled sklonov")

    /**
     * The overview that starts from grammar page [page] of [language], or null: on that page in the book, and on a unit
     * whose first page it is (companion/COURSE.md, "The cases overview").
     */
    fun overview(language: String, page: String?): PrintJob.Overview? =
        page?.let { OVERVIEWS["$language/$it"] }?.let { PrintJob.Overview(page, it) }

    /**
     * The overview of course level [level]: the one its first written unit with an overview starts from (A2's skloni: the
     * cases), offered on the level beside its booklet, also while the level is closed (a sheet to learn from on the way).
     */
    fun overviewOf(course: Course, level: String): PrintJob.Overview? =
        course.refs(level).firstNotNullOfOrNull { ref -> course.units[ref.id]?.let { overview(course.language, it.grammar.firstOrNull()) } }

    /** Unit [id] of [course] as a sheet to print: numbered among its level's written units; null for a planned or unknown one. */
    fun sheet(course: Course, id: String): PrintJob.Sheet? {
        val level = course.levelOf(id) ?: course.units[id]?.level ?: return null
        val n = course.playable(level).indexOfFirst { it.id == id }
        return if (n < 0) null else PrintJob.Sheet(id, level, n + 1)
    }

    /** The level's booklet, when it has a written unit. */
    fun booklet(course: Course, level: String): PrintJob.Booklet? = if (course.playable(level).isEmpty()) null else PrintJob.Booklet(level)

    /** The bridge's path for [job], in the learner's [base] when the sheets have it ("/course/sheet/biti?base=de"). */
    fun path(job: PrintJob, base: String?): String {
        val path = when (job) {
            is PrintJob.Sheet -> "/course/sheet/${enc(job.unit)}"
            is PrintJob.Booklet -> "/course/booklet/${enc(job.level)}"
            is PrintJob.Overview -> "/course/overview/${enc(job.page)}"
        }
        return if (base != null && base in BASES) "$path?base=$base" else path
    }

    /**
     * The print job's name, which Android's "Save as PDF" makes the file's name too: "Lani – A1 – 01 biti", the booklet
     * "Lani – A1 – vsi listi" (as the generator names the files: 01-biti.pdf), the overview "Lani – Pregled sklonov".
     */
    fun jobName(job: PrintJob): String = when (job) {
        is PrintJob.Sheet -> "Lani – ${job.level} – ${job.n.toString().padStart(2, '0')} ${job.unit}"
        is PrintJob.Booklet -> "Lani – ${job.level} – vsi listi"
        is PrintJob.Overview -> "Lani – ${job.name}"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
