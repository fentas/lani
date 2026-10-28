package si.lanisce.lani.game.scene

import si.lanisce.lani.data.Course
import si.lanisce.lani.data.CourseUnit
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.School
import si.lanisce.lani.game.UnitProgress

/**
 * What the school's blackboard says (companion/SCENES.md, "The school's blackboard"; Jan, 2026-10-14: "I like the
 * chalkboard; maybe it could show what we're currently / last learned in school"): the unit the learner worked on last,
 * its [title] in the course's [language] ("Biti: sem, si, je") and a few of its [forms] from the intro's table ("jaz sem",
 * "ti si", "on je"), in the table's order, each as its cells ("jaz", "sem"), and its [words] for when none of the forms
 * fits (a table of sentences: "Ci sono tre gatti."); before the first unit no title ("Dobrodošli!" and the day;
 * "Benvenuti!" in an Italian course). The painter writes what fits ([si.lanisce.lani.game.render.scene.BoardFont.lines]). Pure.
 */
data class Blackboard(
    val title: String?,
    val forms: List<List<String>> = emptyList(),
    val language: String = "sl",
    val words: List<String> = emptyList(),
) {
    /** The board's welcome before the first unit, in the course's language ("Dobrodošli!", "Benvenuti!"). */
    val welcome: String get() = WELCOMES[language] ?: WELCOMES.getValue("sl")

    /** The day as the course's language writes it on a board: "14. 10. 2026", "14/10/2026", "14.10.2026". */
    fun day(d: java.time.LocalDate): String = when (language) {
        "it", "en" -> "${d.dayOfMonth}/${d.monthValue}/${d.year}"
        "de" -> "${d.dayOfMonth}.${d.monthValue}.${d.year}"
        else -> "${d.dayOfMonth}. ${d.monthValue}. ${d.year}"
    }

    companion object {
        /** Before the first unit: no lesson yet, a welcome. */
        val WELCOME = Blackboard(null)

        /** The welcome of a board in each course's language. */
        private val WELCOMES = mapOf("sl" to "Dobrodošli!", "it" to "Benvenuti!", "de" to "Willkommen!", "en" to "Welcome!")

        /** How many forms the board is given at most (it writes those that fit). */
        const val MAX_FORMS = 6

        /**
         * The board of [s]'s school with [course] (null without a course: the painter's own greeting): the unit worked on
         * last ([unit]), else [WELCOME].
         */
        fun of(s: GameState, course: Course?): Blackboard? {
            course ?: return null
            val u = unit(s, course) ?: return Blackboard(null, language = course.language)
            return of(u, course.language)
        }

        /** The board of [u] in a course of [language]: its title, its [forms], its words. */
        fun of(u: CourseUnit, language: String): Blackboard =
            Blackboard(Grammar.text(u.title, language), forms(u), language, u.words.distinct().take(MAX_FORMS))

        /**
         * The unit the learner worked on last: of the units with progress, the one with the latest day in it (begun, an
         * intro step, a sheet, the test, done); on the same day one under way before one done, then the later in the
         * course. Null before the first unit.
         */
        fun unit(s: GameState, course: Course): CourseUnit? {
            val order = course.levels.flatMap { it.units }.map { it.id }
            return order.mapIndexedNotNull { i, id ->
                val u = course.units[id] ?: return@mapIndexedNotNull null
                val p = School.progress(s, course.language, id) ?: return@mapIndexedNotNull null
                Triple(u, p, i)
            }.maxWithOrNull(compareBy({ last(it.second) }, { it.second.done == null }, { it.third }))?.first
        }

        /** The latest day in [p] (ISO dates compare as text). */
        private fun last(p: UnitProgress): String =
            (listOfNotNull(p.started, p.done, p.test?.day) + p.sheets.values.map { it.day }).maxOrNull().orEmpty()

        /**
         * A few forms of [u] for the board, as their cells: each row of its intro's table, its words in the course's
         * language (the translations left out), each cell its first form ("on, ona, ono" → "on", "mi / me" → "mi"), a dash
         * alone left out; of a row of three or more, the endings left out ("jaz -m delam" → "jaz delam") and the first two
         * kept ("midva delava sva delala …" → "midva delava"); a cell the next one begins with left out ("moram moram
         * delati" → "moram delati"); of a sentence and its change, the change ("Delam. Ne delam." → "Ne delam."). Without
         * an intro, its words. At most [MAX_FORMS], no two alike.
         */
        fun forms(u: CourseUnit): List<List<String>> {
            val rows = u.intro?.steps.orEmpty().flatMap { it.rows }
            val forms = rows.map { row ->
                var cells = row.mapNotNull { it.words }.map(::first).filter { w -> w.any { it.isLetterOrDigit() } }
                if (cells.size >= 3) cells = cells.filterNot { it.startsWith("-") }.take(2)
                if (cells.size == 2 && cells[1].startsWith(cells[0] + " ")) cells = cells.drop(1)
                if (cells.size == 2 && cells.all { it.last() in ".!?" }) cells = cells.drop(1)
                cells
            }.filter { it.isNotEmpty() }
            return (forms.ifEmpty { u.words.map { listOf(it) } }).distinct().take(MAX_FORMS)
        }

        /** A cell's first form: "on, ona, ono" → "on", "mi / me" → "mi". */
        private fun first(cell: String): String = cell.split(", ", " / ").first().trim()
    }
}
