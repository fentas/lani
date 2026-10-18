package si.lanisce.lani.game

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.json
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Learner
import java.io.File

/**
 * Primorska's celebrations of its ages (companion/cultures/primorska/celebrations, GAME.md "Ages and their celebrations"),
 * as the app reads them: every age after the first its own tradition with a party at A1 and A2, its word pack bundled,
 * each dialog short (a toast, a song or a dance, a task), said by its host and guests of the cast, its placeholders well
 * formed for a man and a woman, a fresh learner left something to answer, every word it declares tested at both levels in
 * a form the dictionary knows; and the other cultures' generic gathering.
 */
class CelebrationsContentTest {
    private val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory }
    private val pair = L10n.pair
    private val pages = Grammar.bundled("sl").associateBy { it.id }
    private val cast = File(root, "cultures/primorska/villagers").listFiles { f -> f.extension == "json" }.orEmpty().map { it.nameWithoutExtension }.toSet()

    @After fun back() {
        L10n.pair = pair
        Cultures.use(Cultures.DEFAULT)
    }

    private fun primorska(): List<Celebration> {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        Cultures.use(Cultures.default())
        return Age.entries.drop(1).map { AgeUps.celebration(it) }
    }

    /** The dictionary's forms of each lemma (Wiktionary's, companion/lexicon/sl.json, and the supplement's, sl.extra.json). */
    private val forms: Map<String, Set<String>> by lazy {
        val out = HashMap<String, MutableSet<String>>()
        val dict = json.parseToJsonElement(File(root, "lexicon/sl.json").readText()).jsonObject
        val entries = dict.getValue("lemmas").jsonArray.map { it.jsonObject }
        val lemmas = entries.map { it.getValue("lemma").jsonPrimitive.content }
        val wiktionary = entries.map { "${it.getValue("lemma").jsonPrimitive.content}|${it["pos"]?.jsonPrimitive?.contentOrNull}" }.toSet()
        for ((form, readings) in dict.getValue("forms").jsonObject) for (r in readings.jsonArray) out.getOrPut(lemmas[r.jsonArray[0].jsonPrimitive.int]) { HashSet() } += form
        val extra = json.parseToJsonElement(File(root, "lexicon/sl.extra.json").readText()).jsonObject
        for (e in extra.getValue("entries").jsonArray) {
            val o = e.jsonObject
            val lemma = o["lemma"]?.jsonPrimitive?.contentOrNull ?: continue
            if ("$lemma|${o["pos"]?.jsonPrimitive?.contentOrNull}" in wiktionary) continue
            out.getOrPut(lemma) { HashSet() }.addAll((o["forms"] as? JsonObject)?.keys.orEmpty())
        }
        out
    }

    private fun packFile(id: String): JsonObject = File(root, "cultures/primorska/packs/$id.json").readText().let { json.parseToJsonElement(it).jsonObject }

    /** A pack's words as the learner's cards (vocab_<pack>_<word>), each with its dictionary forms. */
    private fun cards(packId: String): List<MyWord> = (packFile(packId)["words"] as JsonArray).map { w ->
        val o = w.jsonObject
        val sl = o.getValue("sl").jsonPrimitive.content
        MyWord("vocab_${packId}_${o.getValue("id").jsonPrimitive.content}", sl, o["en"]?.jsonPrimitive?.contentOrNull.orEmpty(), forms[sl].orEmpty() + sl.lowercase())
    }

    /** A learner at [level] who was introduced to nothing: the pages above their level are not yet. */
    private fun fresh(level: String): (String) -> Boolean = { id -> Introduction.above(pages[id]?.level, level) }

    @Test fun `every age after the first is a tradition of its own, with a party at A1 and A2 and its words`() {
        val all = primorska()
        assertEquals(listOf("Prvi večer ob ognju", "Likof", "Veselica", "Semenj", "Mestni praznik"), all.map { it.nameText.target })
        for (c in all) {
            val at = c.age.name
            assertFalse("$at: its own", c.generic)
            assertTrue("$at: a party", c.party)
            assertTrue("$at: A1 and A2", c.levels.keys.containsAll(listOf("A1", "A2")))
            assertTrue("$at: decorations", c.decor.isNotEmpty())
            assertTrue("$at: host ${c.host} and guests ${c.guests} of the cast", (listOfNotNull(c.host) + c.guests).all { it in cast })
            for (lang in listOf("sl", "en", "de", "it")) {
                for (t in listOfNotNull(c.nameText, c.aboutText, c.momentText, c.lineText, c.memoryText)) assertTrue("$at: $lang of $t", t.by.containsKey(lang))
            }
            val pack = assertNotNull("$at: its word pack", c.pack).let { c.pack!! }
            assertNotNull("$at: the app bundles its pack", FestivalPacks.pack(pack))
            val words = cards(pack)
            assertTrue("$at: ${words.size} words", words.size in 8..24)
            assertTrue("$at: 1-4 words tested", c.words.size in 1..4)
            for (w in c.words) assertTrue("$at: «$w» in $pack", words.any { it.id == "vocab_${pack}_$w" })
        }
        // the decorations of each: the first evening's torches, the likof's spruce, the veselica's dance floor, the fair's
        // stalls, the town's banners
        assertEquals(listOf(Decor.TORCHES, Decor.SPRUCE, Decor.FLOOR, Decor.STALLS, Decor.BANNERS), all.map { c -> c.decor.first { it != Decor.BUNTING } })
        assertEquals(listOf(Music.VALCEK, Music.VALCEK, Music.HARMONIKA, Music.HARMONIKA, Music.GODBA), all.map { it.music })
    }

    @Test fun `each party short, a toast in it, said by its host and guests`() {
        var lines = 0
        for (c in primorska()) for (level in listOf("A1", "A2")) {
            val at = "${c.age} $level"
            val d = assertNotNull(at, AgeUps.dialog(c, level, "sl")).let { AgeUps.dialog(c, level, "sl")!! }
            lines += d.lines.size
            assertTrue("$at: ${d.lines.size} lines", d.lines.size in 4..10)
            assertTrue("$at: the first line someone's", d.lines.first().choices.isEmpty())
            val turns = d.lines.filter { it.choices.isNotEmpty() }
            assertTrue("$at: ${turns.size} turns", turns.size in 2..4)
            val speakers = d.lines.mapNotNull { it.who }.toSet()
            assertTrue("$at: the host speaks", c.host in speakers)
            assertTrue("$at: only the host and the guests ($speakers)", (speakers - setOfNotNull(c.host) - c.guests.toSet()).isEmpty())
            // a toast: "Na zdravje!" or "Naj živi!" among the right choices
            assertTrue("$at: a toast", turns.any { t -> t.choices.any { it.ok && (it.sl.contains("Na zdravje") || it.sl.contains("Naj živi")) } })
            for (t in turns) {
                assertTrue("$at: a right choice", t.choices.any { it.ok })
                assertTrue("$at: a wrong choice", t.choices.any { !it.ok })
                assertFalse("$at: no tap turn over the village", t.choices.any { it.tap != null })
                for (ch in t.choices.filter { !it.ok }) {
                    assertTrue("$at: «${ch.sl}» has a why", !ch.why.isNullOrBlank())
                    assertNotNull("$at: «${ch.sl}» has a reaction", ch.reply)
                    ch.grammar?.let { g -> assertTrue("$at: page $g", g in pages) }
                }
            }
            // read in another pair: its German
            val de = AgeUps.dialog(c, level, "sl", LangPair(Lang.SL, Lang.DE))!!
            assertEquals(d.lines.first().sl, de.lines.first().sl)
            assertTrue("$at: the German meaning", de.lines.first().en != d.lines.first().en)
        }
        println("celebrations: 10 parties, $lines lines")
    }

    @Test fun `the learner's placeholders are well formed, for a man and for a woman`() {
        val dir = File(root, "cultures/primorska/celebrations")
        val files = dir.listFiles { x -> x.extension == "json" }.orEmpty()
        assertEquals(5, files.size)
        for (f in files) {
            val raw = f.readText()
            for (who in listOf(Learner.of("Jan", "male"), Learner.of("Ana", "female"))) {
                val said = who.renderJson(raw)
                val left = Regex("""\{[^"\s}]*""").findAll(said).map { it.value }.filter { it.startsWith("{m:") || it.startsWith("{f:") || it.startsWith("{learner") }.toList()
                assertTrue("${f.name} said to ${who.name}: $left", left.isEmpty())
                assertTrue(f.name, runCatching { json.parseToJsonElement(said) }.isSuccess)
            }
        }
    }

    @Test fun `a fresh learner has something to answer, and every word a party declares is tested at both levels`() {
        for (c in primorska()) {
            val words = MyWords(cards(c.pack!!))
            for (level in listOf("A1", "A2")) {
                val at = "${c.age} $level"
                val d: Dialog = AgeUps.dialog(c, level, "sl")!!
                val gated = Introduction.dialog(d, fresh(level))
                val turns = d.lines.indices.filter { d.lines[it].choices.isNotEmpty() }
                assertTrue("$at: something to answer (not all echoes)", turns.any { it !in gated.echo })
                for ((tested, how) in listOf(DialogWords.of(d, words, emptyMap()) to "as written", DialogWords.of(gated.dialog, words, emptyMap()) to "gated")) {
                    val got = DialogWords.cards(tested).map { it.id }.toSet()
                    for (w in c.words) assertTrue("$at: «$w» tested $how (tested: $got)", "vocab_${c.pack}_$w" in got)
                }
            }
        }
    }

    @Test fun `a guest's lines say nothing of who says them`() {
        val gendered = Regex("""\bsem\s+\p{L}*(l|la|lo|en|na)\b|\b\p{L}{2,}(l|la)\s+sem\b|\b(sam|sama|bil|bila)\b""")
        for (c in primorska()) for (level in listOf("A1", "A2")) {
            val d = AgeUps.dialog(c, level, "sl") ?: continue
            for (l in d.lines) if (l.who != null && l.who in c.guests) {
                assertTrue("${c.age} $level: ${l.who}'s «${l.sl}»", !gendered.containsMatchIn(l.sl.orEmpty().lowercase()))
            }
        }
    }

    @Test fun `the other cultures have the generic gathering`() {
        for (id in listOf("friuli", "kaernten", "lakeland")) {
            Cultures.use(Cultures.load(id))
            for (a in Age.entries.drop(1)) {
                val c = AgeUps.celebration(a)
                assertTrue("$id $a: generic", c.generic)
                assertFalse("$id $a: no party", c.party)
            }
        }
    }
}
