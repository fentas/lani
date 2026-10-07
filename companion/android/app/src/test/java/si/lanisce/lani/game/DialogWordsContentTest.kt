package si.lanisce.lani.game

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.scene.Counts
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.game.scene.tapTurn
import java.io.File

/**
 * The learner's words in the curated Slovene scenes (companion/SCENES.md, "Your words in the dialogs"): how many of their
 * turns test one of the packs' one-word words, the words' forms as the bundled dictionary has them (what GET /forms
 * serves the phone). A learner who has those words as cards meets them there.
 */
class DialogWordsContentTest {
    private val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "scenes").isDirectory }

    /** The packs' one-word words (the Slovene packs and the culture packs' own), each with its forms from the dictionary. */
    private fun packWords(): MyWords {
        val dict = json.parseToJsonElement(File(root, "lexicon/sl.json").readText()).jsonObject
        val lemmas = dict.getValue("lemmas").jsonArray.map { it.jsonObject.getValue("lemma").jsonPrimitive.content }
        val forms = HashMap<String, MutableSet<String>>()
        for ((form, readings) in dict.getValue("forms").jsonObject) {
            for (r in readings.jsonArray) forms.getOrPut(lemmas[r.jsonArray[0].jsonPrimitive.int]) { HashSet() } += form
        }
        val files = File(root, "packs").listFiles { f -> f.extension == "json" }.orEmpty().toList() +
            File(root, "cultures").listFiles().orEmpty().filter { File(it, "packs").isDirectory && it.name == "primorska" }
                .flatMap { File(it, "packs").listFiles { f -> f.extension == "json" }.orEmpty().toList() }
        val words = files.flatMap { f ->
            val p = json.parseToJsonElement(f.readText()).jsonObject
            val id = p["id"]?.jsonPrimitive?.contentOrNull ?: f.nameWithoutExtension
            (p["words"] as? JsonArray).orEmpty().mapNotNull { w ->
                val o = w.jsonObject
                val sl = o["sl"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: return@mapNotNull null
                if (!Regex("""\p{L}+""").matches(sl)) return@mapNotNull null
                MyWord("vocab_${id}_${o["id"]?.jsonPrimitive?.contentOrNull}", sl, o["en"]?.jsonPrimitive?.contentOrNull.orEmpty(), forms[sl].orEmpty() + sl)
            }
        }.distinctBy { it.word }
        return MyWords(words)
    }

    @Test fun `many of the curated turns test a word of the packs`() {
        val words = packWords()
        val files = File(root, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        var turns = 0
        var testing = 0
        val tested = HashMap<String, Int>()
        for (f in files) {
            // the variants among the dialogs, as the bridge serves them
            val o = json.parseToJsonElement(f.readText()).jsonObject
            val all = JsonArray(o["dialogs"]?.jsonArray.orEmpty() + o["variants"]?.jsonArray.orEmpty())
            val s = parseScene(JsonObject(o + ("dialogs" to all) - "variants").toString())
            if (s.language != "sl") continue
            for (d in s.dialogs) {
                val played = if (d.count != null) Counts.render(d, 12, "sl") else d
                val choiceTurns = played.lines.withIndex().filter { it.value.choices.size >= 2 && !it.value.tapTurn }
                turns += choiceTurns.size
                val t = DialogWords.of(played, words)
                testing += t.size
                for (w in DialogWords.cards(t)) tested[w.word] = (tested[w.word] ?: 0) + 1
            }
        }
        println("curated Slovene turns: $turns, testing a pack word: $testing, different words: ${tested.size}; most: " +
            tested.entries.sortedByDescending { it.value }.take(12).joinToString { "${it.key} ${it.value}" })
        assertTrue("turns $turns", turns > 400)
        assertTrue("a good part of them test a pack word ($testing of $turns)", testing * 4 > turns)
        assertTrue("many different words (${tested.size})", tested.size > 100)
    }
}
