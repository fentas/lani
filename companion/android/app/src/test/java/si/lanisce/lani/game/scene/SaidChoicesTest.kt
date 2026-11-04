package si.lanisce.lani.game.scene

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading
import si.lanisce.lani.data.json
import si.lanisce.lani.l10n.Lang
import java.io.File

/**
 * A spoken form is never wrong (companion/SCENES.md, "Reactions and more than one right answer"): no wrong choice of any
 * learner's turn, in a scene, a step, an arrival, a story, a party or a talk at a spot, is what the language's grading table
 * calls what people say (`nonstandard`, `verb_second`), set against that turn's right choice. Such a form is a second `ok`.
 */
class SaidChoicesTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "scenes").isDirectory && File(it, "cultures").isDirectory }

    /** Every content file with its language: the culture packs' and Jan's own. */
    private val files: List<Pair<File, Lang>> by lazy {
        val packs = File(companion, "cultures").listFiles().orEmpty().filter { File(it, "culture.json").isFile }.sortedBy { it.path }.flatMap { dir ->
            val code = (json.parseToJsonElement(File(dir, "culture.json").readText()).jsonObject["language"] as? JsonPrimitive)?.contentOrNull ?: "sl"
            dir.walk().filter { it.isFile && it.extension == "json" }.map { it to Lang.entries.first { l -> l.code == code } }.toList()
        }
        val own = listOf("scenes", "villagers").flatMap { d -> File(companion, d).listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.path }.map { it to Lang.SL } }
        packs + own
    }

    private fun turnsIn(e: JsonElement): List<List<JsonObject>> = when (e) {
        is JsonObject -> listOfNotNull((e["choices"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.takeIf { it.size >= 2 }) + e.values.flatMap { turnsIn(it) }
        is JsonArray -> e.flatMap { turnsIn(it) }
        else -> emptyList()
    }

    private val JsonObject.ok get() = (this["ok"] as? JsonPrimitive)?.booleanOrNull == true

    /** The choice in [l], as said by a man and with a name; null where it counts or fills in anything else. */
    private fun JsonObject.text(l: Lang) = (this[l.code] as? JsonPrimitive)?.contentOrNull
        ?.let { t -> Regex("""\{m:([^|}]*)\|f:[^}]*\}""").replace(t) { m -> m.groupValues[1] }.replace("{learner}", "Jan") }?.takeIf { "{" !in it }

    /**
     * What a table's rule reaches and nobody says, a learner's mistake that stays a wrong choice: weil or obwohl with the verb
     * second before the main clause (people say it only after one: Günthner, as docs/plans/05-built/cleanup-content.md has it),
     * *auf es* and the like (the row is for *auf ihn*, *auf das*), and a relative *which* for a person, which the row of a
     * reported question reads as one after *ask*.
     */
    private val notSaid = listOf(
        Regex("""(?:^|[.!?]\s)(?:Weil|Obwohl)\s[^.!?]*,"""),
        Regex("""(?<!\p{L})(?:auf|an|über|für) es(?!\p{L})"""),
        Regex("""\bsomeone which\b"""),
    )

    /** The wrong choices the tables call what people say, against their turn's right ones: "file | wrong | right". */
    private fun said(): List<String> = files.flatMap { (f, lang) ->
        turnsIn(json.parseToJsonElement(f.readText())).flatMap { turn ->
            val rights = turn.filter { it.ok }.mapNotNull { it.text(lang) }
            turn.filter { !it.ok }.mapNotNull { it.text(lang) }.mapNotNull { wrong ->
                val r = Grading.check(wrong, rights, lang)
                "${f.path.substringAfter("companion/")} | $wrong | ${r.expected}".takeIf { rights.isNotEmpty() && r.hint == Grading.Hint.NONSTANDARD && notSaid.none { it.containsMatchIn(wrong) } }
            }
        }
    }

    @Test fun `no wrong choice is what people say`() {
        val found = said()
        assertTrue("wrong choices that people say (a second ok, or another wrong choice):\n" + found.joinToString("\n"), found.isEmpty())
    }
}
