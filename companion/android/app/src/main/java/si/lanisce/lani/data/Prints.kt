package si.lanisce.lani.data

import java.net.URLEncoder

/**
 * What the school prints (companion/COURSE.md, "Printed sheets"): a unit's sheet ([Sheet], its number [n] in its level
 * as the sheets number it) or a level's [Booklet], both made by the bridge (GET /course/sheet/<unit>, GET
 * /course/booklet/<level>) as the generator makes them, and printed through Android's print dialog (ui/school/SchoolPrint).
 */
sealed interface PrintJob {
    val level: String

    data class Sheet(val unit: String, override val level: String, val n: Int) : PrintJob
    data class Booklet(override val level: String) : PrintJob
}

/** The bridge has no such page to print: an older bridge, or a unit it doesn't have (the app's own course is newer). */
class PrintMissing : java.io.IOException("no such sheet on the bridge")

/** The print's request and its job's name. Pure. */
object Prints {
    /** The languages the sheets explain in; for another base the bridge prints the learner's own (English where it has none). */
    val BASES = setOf("en", "de", "it")

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
        }
        return if (base != null && base in BASES) "$path?base=$base" else path
    }

    /**
     * The print job's name, which Android's "Save as PDF" makes the file's name too: "Lani – A1 – 01 biti", the booklet
     * "Lani – A1 – vsi listi" (as the generator names the files: 01-biti.pdf).
     */
    fun jobName(job: PrintJob): String = when (job) {
        is PrintJob.Sheet -> "Lani – ${job.level} – ${job.n.toString().padStart(2, '0')} ${job.unit}"
        is PrintJob.Booklet -> "Lani – ${job.level} – vsi listi"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
