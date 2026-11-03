package si.lanisce.lani.game

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.json
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Learner
import si.lanisce.lani.ui.scene.ChoiceDiff
import si.lanisce.lani.ui.words.WordTokens
import java.io.File
import java.time.LocalDate

/**
 * The Lake District's celebrations of its ages (companion/cultures/lakeland/celebrations, GAME.md "Ages and their
 * celebrations", docs/plans/05-built/parties-lakeland.md), as the app reads them: the merry neet, the boon day, the barn
 * dance, the crab fair and Charter Day, each with a party at A1, A2, B1 and B2, its word pack bundled, said by its host and
 * guests of the cast, every word it declares tested at every level in a form the dictionary knows; a learner's placeholders
 * well formed; no reaction saying the right answer; and a village that passed an age before the parties came.
 */
class LakelandCelebrationsTest {
    private val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory }
    private val pair = L10n.pair
    private val pages = Grammar.bundled("en").associateBy { it.id }
    private val levels = listOf("A1", "A2", "B1", "B2")
    private val cast = File(root, "cultures/lakeland/villagers").listFiles { f -> f.extension == "json" }.orEmpty().map { it.nameWithoutExtension }.toSet()

    @After fun back() {
        L10n.pair = pair
        Cultures.use(Cultures.DEFAULT)
    }

    private fun lakeland(): List<Celebration> {
        L10n.pair = LangPair(Lang.EN, Lang.SL)
        Cultures.use(Cultures.load("lakeland"))
        return Age.entries.drop(1).map { AgeUps.celebration(it) }
    }

    /** The English supplement's forms of each lemma (companion/lexicon/en.extra.json). */
    private val forms: Map<String, Set<String>> by lazy {
        val out = HashMap<String, MutableSet<String>>()
        for (e in json.parseToJsonElement(File(root, "lexicon/en.extra.json").readText()).jsonObject.getValue("entries").jsonArray) {
            val o = e.jsonObject
            val lemma = o["lemma"]?.jsonPrimitive?.contentOrNull ?: continue
            out.getOrPut(lemma) { HashSet() }.addAll((o["forms"] as? JsonObject)?.keys.orEmpty())
        }
        out
    }

    private fun packFile(id: String): JsonObject = json.parseToJsonElement(File(root, "cultures/lakeland/packs/$id.json").readText()).jsonObject

    /** A pack's words as the learner's cards (vocab_<pack>_<word>), each with its forms: the dictionary's and its plural. */
    private fun cards(packId: String): List<MyWord> = (packFile(packId)["words"] as JsonArray).map { w ->
        val o = w.jsonObject
        val en = o.getValue("en").jsonPrimitive.content
        val plural = o["plural"]?.jsonPrimitive?.contentOrNull
        MyWord("vocab_${packId}_${o.getValue("id").jsonPrimitive.content}", en, o["sl"]?.jsonPrimitive?.contentOrNull.orEmpty(), forms[en].orEmpty() + listOfNotNull(en.lowercase(), plural))
    }

    /** A learner at [level] who was introduced to nothing: the pages above their level are not yet. */
    private fun fresh(level: String): (String) -> Boolean = { id -> Introduction.above(pages[id]?.level, level) }

    @Test fun `every age after the first is a tradition of its own, with a party at every level and its words`() {
        val all = lakeland()
        assertEquals(listOf("A merry neet", "The boon day", "The barn dance", "The crab fair", "Charter Day"), all.map { it.nameText.target })
        for (c in all) {
            val at = c.age.name
            assertFalse("$at: its own", c.generic)
            assertTrue("$at: a party", c.party)
            assertTrue("$at: every level", c.levels.keys.containsAll(levels))
            assertTrue("$at: host ${c.host} and guests ${c.guests} of the cast", (listOfNotNull(c.host) + c.guests).all { it in cast })
            for (lang in listOf("sl", "en", "de", "it")) {
                for (t in listOfNotNull(c.nameText, c.aboutText, c.momentText, c.lineText, c.memoryText)) assertTrue("$at: $lang of $t", t.by.containsKey(lang))
            }
            val pack = c.pack!!
            assertTrue("$at: the pack's id says it is a party's", pack.startsWith("party-"))
            assertNotNull("$at: the app bundles its pack", FestivalPacks.pack(pack))
            val words = cards(pack)
            assertTrue("$at: ${words.size} words", words.size in 8..12)
            assertTrue("$at: 1-4 words tested", c.words.size in 1..4)
            for (w in c.words) assertTrue("$at: «$w» in $pack", words.any { it.id == "vocab_${pack}_$w" })
        }
        // what goes up and what plays: none of them Slovenia's likof spruce; the fire's two first, the green's after them
        assertFalse(all.any { Decor.SPRUCE in it.decor })
        assertEquals(listOf(Decor.TORCHES, Decor.TORCHES, Decor.FLOOR, Decor.STALLS, Decor.BANNERS), all.map { c -> c.decor.first { it != Decor.BUNTING } })
        assertEquals(listOf(Music.VALCEK, Music.VALCEK, Music.HARMONIKA, Music.HARMONIKA, Music.GODBA), all.map { it.music })
        assertEquals(listOf(PartyPlace.FIRE, PartyPlace.FIRE, PartyPlace.SQUARE, PartyPlace.SQUARE, PartyPlace.SQUARE), all.map { it.place })
    }

    @Test fun `each party short, said by its host and guests, each wrong choice with its why and reaction`() {
        var lines = 0
        for (c in lakeland()) for (level in levels) {
            val at = "${c.age} $level"
            val d = AgeUps.dialog(c, level, "en")
            assertNotNull(at, d)
            d!!
            lines += d.lines.size
            assertTrue("$at: ${d.lines.size} lines", d.lines.size in 4..10)
            assertTrue("$at: the first line someone's", d.lines.first().choices.isEmpty())
            val turns = d.lines.filter { it.choices.isNotEmpty() }
            assertTrue("$at: ${turns.size} turns", turns.size in 2..4)
            val speakers = d.lines.mapNotNull { it.who }.toSet()
            assertTrue("$at: the host speaks", c.host in speakers)
            assertTrue("$at: only the host and the guests ($speakers)", (speakers - setOfNotNull(c.host) - c.guests.toSet()).isEmpty())
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
            val de = AgeUps.dialog(c, level, "en", LangPair(Lang.EN, Lang.DE))!!
            assertEquals(d.lines.first().sl, de.lines.first().sl)
            assertTrue("$at: the German meaning", de.lines.first().en != d.lines.first().en)
        }
        assertEquals(177, lines)
    }

    @Test fun `each level has its own lines, so a B2 learner hears more than an A2 learner`() {
        for (c in lakeland()) {
            val firsts = levels.map { AgeUps.dialog(c, it, "en")!!.lines.first().sl }
            assertEquals("${c.age}: $firsts", levels.size, firsts.toSet().size)
            // above what the party is written for, the highest it has
            assertEquals(firsts.last(), AgeUps.dialog(c, "C1", "en")!!.lines.first().sl)
        }
    }

    @Test fun `the learner's placeholders are well formed, for a man and for a woman`() {
        val files = File(root, "cultures/lakeland/celebrations").listFiles { x -> x.extension == "json" }.orEmpty().toList()
        assertEquals(5, files.size)
        for (f in files + listOf("merry-neet", "boon-day", "barn-dance", "crab-fair", "charter-day").map { File(root, "cultures/lakeland/packs/party-$it.json") }) {
            val raw = f.readText()
            for (who in listOf(Learner.of("Jan", "male"), Learner.of("Ana", "female"))) {
                val said = who.renderJson(raw)
                val left = Regex("""\{[^"\s}]*""").findAll(said).map { it.value }.filter { it.startsWith("{m:") || it.startsWith("{f:") || it.startsWith("{learner") }.toList()
                assertTrue("${f.name} said to ${who.name}: $left", left.isEmpty())
                assertTrue(f.name, runCatching { json.parseToJsonElement(said) }.isSuccess)
            }
        }
    }

    @Test fun `a fresh learner has something to answer, and every word a party declares is tested at every level`() {
        for (c in lakeland()) {
            val words = MyWords(cards(c.pack!!))
            for (level in levels) {
                val at = "${c.age} $level"
                val d: Dialog = AgeUps.dialog(c, level, "en")!!
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

    private fun words(t: String) = WordTokens.of(t).map { it.text.lowercase() }
    private fun says(text: String, phrase: List<String>) = phrase.isNotEmpty() && words(text).windowed(phrase.size).any { it == phrase }

    /** The runs of [right]'s words that [wrong] lacks, where the two differ in a word or two. */
    private fun apart(right: String, wrong: String): List<List<String>> {
        val marked = ChoiceDiff.marks(listOf(right, wrong)).first().toSet()
        val runs = ArrayList<List<String>>()
        var run = ArrayList<String>()
        for (t in WordTokens.of(right)) {
            if (t in marked) run += t.text.lowercase() else if (run.isNotEmpty()) { runs += run; run = ArrayList() }
        }
        if (run.isNotEmpty()) runs += run
        return runs
    }

    @Test fun `no reaction says the right choice or the words that set it apart, two right choices have their own replies`() {
        val told = ArrayList<String>()
        for (who in listOf(Learner.of("Jan", "male"), Learner.of("Ana", "female"))) for (f in File(root, "cultures/lakeland/celebrations").listFiles { x -> x.extension == "json" }.orEmpty()) {
            val file = json.parseToJsonElement(who.renderJson(f.readText())).jsonObject
            for ((lv, t) in file.getValue("levels").jsonObject) for ((li, l) in t.jsonObject.getValue("lines").jsonArray.withIndex()) {
                val choices = (l.jsonObject["choices"] as? JsonArray)?.map { it.jsonObject } ?: continue
                fun JsonObject.en() = getValue("en").jsonPrimitive.content
                val rights = choices.filter { it["ok"] != null }
                assertEquals("${f.name} $lv[$li]: each right choice its own reply", rights.size, rights.map { it.getValue("reply").jsonObject.en() }.toSet().size)
                for (c in choices.filter { it["ok"] == null }) {
                    val said = c.getValue("reply").jsonObject.en()
                    for (r in rights) {
                        if (says(said, words(r.en()))) told += "${f.name} $lv[$li] says the right choice: «$said»"
                        for (run in apart(r.en(), c.en())) if (says(said, run)) told += "${f.name} $lv[$li] says «${run.joinToString(" ")}»: «$said»"
                    }
                }
            }
        }
        assertTrue(told.distinct().joinToString("\n"), told.isEmpty())
    }

    @Test fun `a village that passed its ages before the parties came watches them again, and is paid once at its age`() {
        lakeland()
        val day = LocalDate.of(2026, 6, 1)
        val village = GameState(
            age = Age.VAS, morale = 60, grown = mapOf("zaselek" to "2026-03-01", "vas" to day.toString()),
            residents = listOf(Resident("birkett", "2026-03-02"), Resident("sam", "2026-03-02")),
        )
        // the chronicle's ages: each with this culture's celebration now, the days the village kept
        val ages = AgeUps.milestones(village)
        assertEquals(listOf("The barn dance", "The boon day", "A merry neet"), ages.map { it.celebration.nameText.target })
        assertEquals(listOf(day, LocalDate.of(2026, 3, 1), null), ages.map { it.on })
        assertTrue(ages.all { it.celebration.party && !it.celebration.generic })
        // the moment again on the village as it was, its party's dialog there at any level
        for (m in ages) {
            val then = AgeUps.asItWas(village, m.age)
            assertEquals(m.age, then.age)
            assertEquals(m.celebration.nameText.target, AgeUps.moment(Age.entries[m.age.ordinal - 1], m.age, then).celebration.nameText.target)
            assertNotNull(AgeUps.dialog(m.celebration, "B2", "en"))
        }
        // long past the age's evenings: no bubble tonight
        assertNull(AgeUps.partyNow(village, LocalDate.of(2026, 11, 2).atTime(19, 0)))
        // an earlier age's pays nothing; its own age's once, with its line and how the people remember it
        val t = 1_780_000_000_000L
        val today = LocalDate.of(2026, 11, 2)
        val (same, none) = AgeUps.partyHad(village, Age.TABOR, today, t)
        assertFalse(none)
        assertEquals(village, same)
        val (had, first) = AgeUps.partyHad(village, Age.VAS, today, t)
        assertTrue(first)
        assertEquals(70, had.morale)
        assertEquals("At the barn dance we danced the Square Eight until our feet were sore.", AgeUps.celebration(Age.VAS).lineText.target)
        assertEquals("our barn dance", AgeUps.celebration(Age.VAS).memoryText!!.target)
        assertFalse(AgeUps.partyHad(had, Age.VAS, today, t).second)
    }
}
