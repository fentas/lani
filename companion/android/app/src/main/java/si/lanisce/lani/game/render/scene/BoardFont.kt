package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.scene.Blackboard
import java.text.Normalizer

/**
 * A small hand in chalk for the school's blackboard ([SchoolPainter], companion/SCENES.md "The school's blackboard"): the
 * letters 3 px wide (m, w and a few capitals 4 or 5), capitals and tall letters 6 rows high, the small ones 4 (rows 2 to
 * 5), a row below for g, j, p, y and the comma's tail; the line is row 5. On the board's slant each letter's right part
 * is written a row lower ([slant]), but the s, drawn slanted already. Slovene's č, š, ž and the other Latin letters
 * with a mark over them are their letter and the mark (a caron, an acute, a grave, two dots, a circumflex) in the 2 rows
 * over a small letter, or over a capital (rows −2 and −1). A letter it doesn't know is left out. Pure.
 */
internal object BoardFont {
    /** How far apart two lines are, in rows, and how many the board holds. */
    const val PITCH = 8
    const val LINES = 3

    /** Between two forms on a line. */
    const val SEP = " · "

    private val glyphs: Map<Char, Array<String>> = mapOf(
        'a' to small(".##", "#.#", "#.#", ".##"),
        'b' to tall("#..", "#..", "##.", "#.#", "#.#", "##."),
        'c' to small(".##", "#..", "#..", ".##"),
        'd' to tall("..#", "..#", ".##", "#.#", "#.#", ".##"),
        'e' to small(".#.", "###", "#..", ".##"),
        'f' to tall(".##", ".#.", "###", ".#.", ".#.", ".#."),
        'g' to arrayOf("...", "...", ".##", "#.#", ".##", "..#", "##."),
        'h' to tall("#..", "#..", "##.", "#.#", "#.#", "#.#"),
        'i' to tall("#", ".", "#", "#", "#", "#"),
        'j' to arrayOf(".#", "..", ".#", ".#", ".#", ".#", "#."),
        'k' to tall("#..", "#..", "#.#", "##.", "#.#", "#.#"),
        'l' to tall("#", "#", "#", "#", "#", "#"),
        'm' to small("####.", "#.#.#", "#.#.#", "#.#.#"),
        'n' to small("##.", "#.#", "#.#", "#.#"),
        'o' to small(".#.", "#.#", "#.#", ".#."),
        'p' to arrayOf("...", "...", "##.", "#.#", "#.#", "##.", "#.."),
        'q' to arrayOf("...", "...", ".##", "#.#", "#.#", ".##", "..#"),
        'r' to small("#.#", "##.", "#..", "#.."),
        // drawn slanted already, a row taller (it would lose its shape slanting with the board)
        's' to arrayOf("...", "...", ".##", "#..", ".#.", "..#", "##."),
        't' to tall(".#.", ".#.", "###", ".#.", ".#.", "..#"),
        'u' to small("#.#", "#.#", "#.#", ".##"),
        'v' to small("#.#", "#.#", ".#.", ".#."),
        'w' to small("#...#", "#.#.#", "#.#.#", ".#.#."),
        'x' to small("#.#", ".#.", ".#.", "#.#"),
        'y' to arrayOf("...", "...", "#.#", "#.#", ".##", "..#", "##."),
        'z' to small("###", "..#", "#..", "###"),
        'A' to tall(".#.", "#.#", "#.#", "###", "#.#", "#.#"),
        'B' to tall("##.", "#.#", "##.", "#.#", "#.#", "##."),
        'C' to tall(".##", "#..", "#..", "#..", "#..", ".##"),
        'D' to tall("##.", "#.#", "#.#", "#.#", "#.#", "##."),
        'E' to tall("###", "#..", "##.", "#..", "#..", "###"),
        'F' to tall("###", "#..", "##.", "#..", "#..", "#.."),
        'G' to tall(".##", "#..", "#..", "#.#", "#.#", ".##"),
        'H' to tall("#.#", "#.#", "###", "#.#", "#.#", "#.#"),
        'I' to tall("###", ".#.", ".#.", ".#.", ".#.", "###"),
        'J' to tall("..#", "..#", "..#", "..#", "#.#", ".#."),
        'K' to tall("#..#", "#.#.", "##..", "##..", "#.#.", "#..#"),
        'L' to tall("#..", "#..", "#..", "#..", "#..", "###"),
        'M' to tall("#...#", "##.##", "#.#.#", "#...#", "#...#", "#...#"),
        'N' to tall("#..#", "##.#", "#.##", "#..#", "#..#", "#..#"),
        'O' to tall(".#.", "#.#", "#.#", "#.#", "#.#", ".#."),
        'P' to tall("##.", "#.#", "#.#", "##.", "#..", "#.."),
        'Q' to arrayOf(".#.", "#.#", "#.#", "#.#", "#.#", ".#.", "..#"),
        'R' to tall("##.", "#.#", "#.#", "##.", "#.#", "#.#"),
        'S' to tall(".##", "#..", ".#.", "..#", "..#", "##."),
        'T' to tall("###", ".#.", ".#.", ".#.", ".#.", ".#."),
        'U' to tall("#.#", "#.#", "#.#", "#.#", "#.#", "###"),
        'V' to tall("#.#", "#.#", "#.#", ".#.", ".#.", ".#."),
        'W' to tall("#...#", "#...#", "#...#", "#.#.#", "##.##", "#...#"),
        'X' to tall("#.#", "#.#", ".#.", ".#.", "#.#", "#.#"),
        'Y' to tall("#.#", "#.#", ".#.", ".#.", ".#.", ".#."),
        'Z' to tall("###", "..#", ".#.", ".#.", "#..", "###"),
        '0' to tall(".#.", "#.#", "#.#", "#.#", "#.#", ".#."),
        '1' to tall(".#.", "##.", ".#.", ".#.", ".#.", "###"),
        '2' to tall(".#.", "#.#", "..#", ".#.", "#..", "###"),
        '3' to tall("##.", "..#", ".#.", "..#", "..#", "##."),
        '4' to tall("#.#", "#.#", "###", "..#", "..#", "..#"),
        '5' to tall("###", "#..", "##.", "..#", "..#", "##."),
        '6' to tall(".##", "#..", "##.", "#.#", "#.#", ".#."),
        '7' to tall("###", "..#", "..#", ".#.", ".#.", ".#."),
        '8' to tall(".#.", "#.#", ".#.", "#.#", "#.#", ".#."),
        '9' to tall(".#.", "#.#", "#.#", ".##", "..#", "##."),
        '.' to tall(".", ".", ".", ".", ".", "#"),
        '…' to tall(".....", ".....", ".....", ".....", ".....", "#.#.#"),
        ',' to arrayOf(".", ".", ".", ".", ".", "#", "#"),
        ':' to tall(".", ".", ".", "#", ".", "#"),
        ';' to arrayOf(".", ".", ".", "#", ".", "#", "#"),
        '!' to tall("#", "#", "#", "#", ".", "#"),
        '?' to tall("##.", "..#", ".#.", ".#.", "...", ".#."),
        '-' to tall("..", "..", "..", "##", "..", ".."),
        '–' to tall("...", "...", "...", "###", "...", "..."),
        '—' to tall("....", "....", "....", "####", "....", "...."),
        '·' to tall(".", ".", ".", "#", ".", "."),
        // the ellipsis of a title or a table's cell ("Če bi deževalo, bi …", "si ga …?"): three dots on the line
        '…' to tall(".....", ".....", ".....", ".....", ".....", "#.#.#"),
        '/' to tall("..#", "..#", ".#.", ".#.", "#..", "#.."),
        '(' to tall(".#", "#.", "#.", "#.", "#.", ".#"),
        ')' to tall("#.", ".#", ".#", ".#", ".#", "#."),
        '+' to tall("...", "...", ".#.", "###", ".#.", "..."),
        '=' to tall("...", "...", "###", "...", "###", "..."),
        '\'' to tall("#", "#", ".", ".", ".", "."),
        '"' to tall("#.#", "#.#", "...", "...", "...", "..."),
        '→' to tall("....", "....", "..#.", "####", "..#.", "...."),
        ' ' to tall(".", ".", ".", ".", ".", "."),
    )

    /** The marks over a letter, by their combining character: their two rows, centred over it. */
    private val marks: Map<Char, Array<String>> = mapOf(
        '\u030C' to arrayOf("#.#", ".#."), // caron: č š ž
        '\u0301' to arrayOf("..#", ".#."), // acute: ć é
        '\u0300' to arrayOf("#..", ".#."), // grave: è
        '\u0308' to arrayOf("#.#", "..."), // two dots: ä ö ü
        '\u0302' to arrayOf(".#.", "#.#"), // circumflex: ê
    )

    private fun tall(vararg rows: String): Array<String> = arrayOf(*rows, ".".repeat(rows[0].length))
    private fun small(vararg rows: String): Array<String> = arrayOf(".".repeat(rows[0].length), ".".repeat(rows[0].length), *rows, ".".repeat(rows[0].length))

    /** [ch] as its letter and the mark over it ("č" → 'c', caron), or itself; null for one it can't write. */
    private fun parts(ch: Char): Pair<Array<String>, Array<String>?>? {
        glyphs[ch]?.let { return it to null }
        val d = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
        if (d.length != 2) return null
        val base = glyphs[d[0]] ?: return null
        val mark = marks[d[1]] ?: return null
        return base to mark
    }

    /** The letters drawn slanted already: the board doesn't slant them again ([upright]). */
    private val slanted = setOf('s')

    /**
     * Whether [ch] stands as drawn on the board's slant: a letter slanted already ([slanted], "s" and "š"). The others
     * slant with the board, their right part a row lower ([slant]).
     */
    fun upright(ch: Char): Boolean = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).firstOrNull() in slanted

    /** How many rows lower column [k] of [ch] is written on the board's slant, from its first: a row every second column. */
    fun slant(ch: Char, k: Int): Int = if (upright(ch)) 0 else k / 2

    /** Whether the board can write [ch]. */
    fun knows(ch: Char): Boolean = parts(ch) != null

    /** How wide [ch] is in columns (0 for one it can't write). */
    fun width(ch: Char): Int = parts(ch)?.first?.get(0)?.length ?: 0

    /** How wide [text] is written: each letter and a column between two. */
    fun width(text: String): Int {
        var x = 0; var n = 0
        for (ch in text) { val w = width(ch); if (w == 0) continue; x += w + 1; n++ }
        return if (n == 0) 0 else x - 1
    }

    /**
     * Calls [plot] (the letter's index in [text], its first column, the column in the letter, the row: −2 to 6, and
     * whether it is of the mark over the letter) for each chalk pixel of [text], from column 0; returns its width.
     */
    inline fun write(text: String, plot: (Int, Int, Int, Int, Boolean) -> Unit): Int {
        var x = 0; var n = 0
        for ((i, ch) in text.withIndex()) {
            val g = glyph(ch) ?: continue
            for ((row, line) in g.withIndex()) for ((k, p) in line.withIndex()) if (p == '#') plot(i, x, k, row, false)
            mark(ch)?.let { m ->
                // over a capital or a tall letter (its top row inked) above it, else in the small letter's ascender rows
                val top = if (g[0].contains('#')) -2 else 0
                val off = (g[0].length - m[0].length) / 2
                for ((row, line) in m.withIndex()) for ((k, p) in line.withIndex()) if (p == '#' && k + off >= 0) plot(i, x, k + off, top + row, true)
            }
            x += g[0].length + 1; n++
        }
        return if (n == 0) 0 else x - 1
    }

    fun glyph(ch: Char): Array<String>? = parts(ch)?.first
    fun mark(ch: Char): Array<String>? = parts(ch)?.second

    /** A line on the board: its [text], and whether it is the title (underlined). */
    data class Line(val text: String, val title: Boolean)

    /**
     * What of [board] fits on [lines] lines [width] columns wide: the title on the first line, whole; one too long for it
     * its head (before the ": ", "Biti: sem, si, je" → "Biti"), else over two lines (at its commas if it can: "Moški,
     * ženski," / "srednji spol"), else its head before the first "?", "!" or "," ("Kje? V hiši, na vrtu, pri babici" →
     * "Kje?"); then the forms, as many as fit, a line after another, [SEP] between two on a line, at most 4: a form too
     * wide for a line its last cell alone ("imenovati se imenujem se" → "imenujem se"), else left out. Before the first
     * unit ([Blackboard.WELCOME]): "Dobrodošli!" and [date] under it ("14. 10. 2026").
     */
    fun lines(board: Blackboard, width: Int, lines: Int = LINES, date: String? = null): List<Line> {
        val title = board.title ?: return listOfNotNull(Line("Dobrodošli!", true), date?.let { Line(it, false) })
        val out = ArrayList<Line>()
        for (l in titled(title, width, minOf(2, lines - 1))) out.add(Line(l, true))
        var line = ""; var forms = 0
        for (cells in board.forms) {
            if (out.size >= lines || forms >= 4) break
            val f = cells.joinToString(" ").takeIf { width(it) <= width } ?: cells.last().takeIf { width(it) <= width } ?: continue
            val with = if (line.isEmpty()) f else line + SEP + f
            if (width(with) <= width) { line = with; forms++; continue }
            out.add(Line(line, false))
            if (out.size >= lines) { line = ""; break }
            line = f; forms++
        }
        if (line.isNotEmpty() && out.size < lines) out.add(Line(line, false))
        return out
    }

    /** How [title] goes on the board in at most [max] lines (see [lines]). */
    private fun titled(title: String, width: Int, max: Int): List<String> {
        if (width(title) <= width) return listOf(title)
        val head = title.substringBefore(": ")
        if (head != title && width(head) <= width) return listOf(head)
        if (max >= 2) {
            val phrases = title.split(", ")
            wrap(phrases.mapIndexed { i, p -> if (i < phrases.lastIndex) "$p," else p }, width).takeIf { it.size <= max && it.all { l -> width(l) <= width } }?.let { return it }
            wrap(title.split(' '), width).takeIf { it.size <= max && it.all { l -> width(l) <= width } }?.let { return it }
        }
        val cut = title.indexOfFirst { it == '?' || it == '!' || it == ',' }
        val short = if (cut > 0) title.substring(0, cut + 1).trimEnd(',') else title
        return wrap(short.split(' '), width).take(max)
    }

    /** [parts] (words, or a phrase each) in lines [width] columns wide, a space between two (one too long for a line alone on its own). */
    private fun wrap(parts: List<String>, width: Int): List<String> {
        val out = ArrayList<String>(); var line = ""
        for (w in parts.filter { it.isNotEmpty() }) {
            val with = if (line.isEmpty()) w else "$line $w"
            if (line.isEmpty() || width(with) <= width) line = with else { out.add(line); line = w }
        }
        if (line.isNotEmpty()) out.add(line)
        return out
    }
}
