package si.lanisce.lani.game

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.data.GameSnapshot
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.data.json
import si.lanisce.lani.game.scene.Counts
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.parseScene
import java.io.File

/**
 * Rules not yet over the curated Slovene scenes (companion/SCENES.md, "Rules not yet"): how many of their turns a learner
 * at a level is not asked for yet, and by which rules. A fresh A1 learner meets the A2 and B1 rules without being asked
 * for them; a B1 learner is asked for all. With LANI_GATE_DATA pointing at a copy of a learner's data (its
 * learner-profile.json and the other databases, and app/game.json), it says the same for that learner.
 */
class IntroductionContentTest {
    private val pages = Grammar.bundled("sl")
    private val byId = pages.associateBy { it.id }

    /** Every learner's turn of the Slovene scenes (a counting dialog once, at twelve), by where it is. */
    private fun curatedTurns(): List<Pair<String, DialogLine>> {
        val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "scenes").isDirectory }
        val files = File(root, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().toList() +
            File(root, "cultures").listFiles().orEmpty().flatMap { File(it, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().toList() }
        return files.map { parseScene(it.readText()) }.filter { it.language == "sl" }.flatMap { s ->
            s.dialogs.flatMap { d ->
                val played = if (d.count != null) Counts.render(d, 12, "sl") else d
                played.lines.filter { it.choices.size >= 2 }.map { "${s.id}/${d.id}" to it }
            }
        }
    }

    /** What rules not yet do to the curated turns, for a learner whose rules [notYet] says. */
    private data class Tally(
        val turns: Int, val form: Int, val gated: Int, val gatedForm: Int, val echo: Int, val trimmed: Int, val tap: Int,
        val byRule: Map<String, Int>,
    ) {
        override fun toString() =
            "$turns turns ($form test a form): $gated with a rule not yet ($gatedForm of the form turns): $echo echoes, $trimmed chosen about meaning, $tap tap turns only met; " +
                "by rule: " + byRule.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" }
    }

    private fun tally(turns: List<Pair<String, DialogLine>>, notYet: (String) -> Boolean): Tally {
        var form = 0
        var gated = 0
        var gatedForm = 0
        var echo = 0
        var trimmed = 0
        var tap = 0
        val byRule = HashMap<String, Int>()
        for ((_, line) in turns) {
            val c = line.choices
            val right = c.indexOfFirst { it.ok }.takeIf { r -> r >= 0 && c.count { it.ok } == 1 }
            val isForm = right != null && Forms.turn(c.map { it.sl }, right) != null
            if (isForm) form++
            val g = Introduction.turn(line, notYet) ?: continue
            gated++
            if (isForm) gatedForm++
            when {
                g.echo -> echo++
                g.keep.size < c.size -> trimmed++
                else -> tap++
            }
            for (p in g.pages) byRule[p] = (byRule[p] ?: 0) + 1
        }
        return Tally(turns.size, form, gated, gatedForm, echo, trimmed, tap, byRule)
    }

    /** A learner at [level] who was introduced to nothing: the pages above their level are not yet. */
    private fun fresh(level: String): (String) -> Boolean = { id -> Introduction.above(byId[id]?.level, level) }

    @Test fun `a fresh A1 learner meets the rules above A1 without being asked for them, and a B1 learner is asked for all`() {
        val turns = curatedTurns()
        val a1 = tally(turns, fresh("A1"))
        val a2 = tally(turns, fresh("A2"))
        val b1 = tally(turns, fresh("B1"))
        println("fresh A1: $a1")
        println("fresh A2: $a2")
        println("fresh B1: $b1")
        assertTrue("turns: ${a1.turns}", a1.turns > 400)
        // the A2 rules the A1 scenes use (the dative, the instrumental …) are not asked for at A1
        assertTrue("A1 gated: ${a1.gated}", a1.gated > 50)
        assertTrue(a1.byRule.keys.all { byId[it]?.level != "A1" })
        // fewer at A2, none at B1 (the book's pages go up to B1)
        assertTrue(a2.gated < a1.gated)
        assertEquals(0, b1.gated)
        // an echo keeps nothing wrong to choose, a trimmed turn something about meaning: never a wall
        for ((where, line) in turns) {
            val g = Introduction.turn(line, fresh("A1")) ?: continue
            assertTrue("$where keeps its right choice", g.keep.any { line.choices[it].ok })
        }
    }

    @Test fun `a copy of a learner's data, when there is one`() {
        val dir = (System.getenv("LANI_GATE_DATA") ?: System.getenv("FLUENT_GATE_DATA"))?.let(::File)?.takeIf { it.isDirectory } ?: return
        fun read(name: String) = File(dir, name).takeIf { it.isFile }?.readText()?.let { json.parseToJsonElement(it) } ?: JsonObject(emptyMap())
        val dbs = JsonObject(
            mapOf(
                "learner_profile" to read("learner-profile.json"),
                "progress_db" to read("progress-db.json"),
                "mistakes_db" to read("mistakes-db.json"),
                "spaced_repetition" to read("spaced-repetition.json"),
                "session_log" to read("session-log.json"),
            ),
        )
        val d = Dashboard.parse(JsonObject(mapOf("databases" to dbs, "computed" to JsonObject(mapOf("today" to JsonPrimitive("2026-09-28"))))).toString())
        val state = json.decodeFromString(GameSnapshot.serializer(), File(dir, "app/game.json").readText()).state
        val met = GrammarBook.met(state, "sl")
        val level = d.levelIn("sl")
        fun notYet(p: GrammarPage): Boolean =
            Introduction.mastery(p.level, level, p.id in met, met[p.id], Grammar.cards(p, d.pool), Grammar.mistakes(p, d.mistakes)) == Mastery.NOT_YET
        val gatedPages = pages.filter(::notYet).map { it.id }.toSet()
        val introduced = pages.filter { p -> Introduction.introduced(p.id in met, met[p.id], Grammar.cards(p, d.pool), Grammar.mistakes(p, d.mistakes)) }
        println("learner at $level: pages met ${met.keys.sorted()}; introduced ${introduced.map { "${it.id} (${it.level})" }}")
        println("learner: not yet ${gatedPages.sorted()}")
        println("learner: ${tally(curatedTurns()) { it in gatedPages }}")
        println("fresh A1: ${tally(curatedTurns(), fresh("A1"))}")
    }
}
