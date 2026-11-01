package si.lanisce.lani.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.CaseLens
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import java.io.File

/**
 * The hint chip (📖 Namig at a turn, [TurnHints]) and the case lens (the word card's row, the sentence's grammar: the
 * bridge's case-lens.ts) read the same trigger table; on the same turn they name the same thing. The turns and what the
 * lens says of each turn's gap word are bridge/test/fixtures/hint-lens.json (made by the bridge's test/hint-lens.ts; the
 * smoke checks the lens still answers so): the chip names the lens's trigger (the same word of the sentence), its case
 * first, and the question the sentence's grammar asks from the lens's row ([SentenceGrammar.question]).
 */
class TurnHintLensTest {
    private val pages = Grammar.bundled("sl")
    private val page = { id: String -> pages.firstOrNull { it.id == id } }
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "bridge/test/fixtures").isDirectory }
    private val turns = Json.parseToJsonElement(File(companion, "bridge/test/fixtures/hint-lens.json").readText()).jsonObject.getValue("turns").jsonArray

    @Test fun `on the same turn the chip names the trigger, the case and the question the case lens names`() {
        val failed = ArrayList<String>()
        val kinds = HashSet<String>()
        for (e in turns) {
            val o = e.jsonObject
            val sl = o.getValue("sl").jsonPrimitive.content
            val read = Words.parse("""{"word":"w","entries":[],"case":${o.getValue("lens")}}""").lens!!
            val row = CaseLens.row(read)!!
            kinds += row.kind
            val line = DialogLine(choices = listOf(DialogChoice(sl, ok = true), DialogChoice(o.getValue("wrong").jsonPrimitive.content, why = "…", grammar = o.getValue("page").jsonPrimitive.content)))
            val h = TurnHints.of(line, "sl", page)
            val said = "$sl: the lens ${row.kind} «${read.trigger?.word}» ${row.case} ${SentenceGrammar.question(row)}; the chip ${h?.trigger} ${h?.cases}"
            when {
                h == null -> failed += "$said (no hint)"
                h.trigger?.at != read.trigger?.i || !h.trigger?.word.equals(read.trigger?.word, ignoreCase = true) -> failed += "$said (the trigger)"
                h.cases.firstOrNull()?.case != row.case -> failed += "$said (the case)"
                h.cases.first() != SentenceGrammar.question(row) -> failed += "$said (the question)"
            }
        }
        assertEquals(emptyList<String>(), failed)
        // every kind of trigger the chip knows is among them
        assertTrue("$kinds", kinds.containsAll(listOf("preposition", "negation", "quantity", "number", "verb")))
        assertTrue("${turns.size} turns", turns.size >= 20)
    }
}
