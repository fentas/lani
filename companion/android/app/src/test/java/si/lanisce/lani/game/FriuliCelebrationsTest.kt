package si.lanisce.lani.game

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
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
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Learner
import java.io.File
import java.time.LocalDate

/**
 * Friuli's celebrations of its ages (companion/cultures/friuli/celebrations, GAME.md "Ages and their celebrations"), as the
 * app reads them for a learner of Italian from German: the first hearth, the licôf, the sagra, the fair, the palio, each a
 * party at A1, A2, B1 and B2 with its word pack bundled; each short, a toast in it, said by its host and guests of the cast;
 * a fresh learner left something to answer and every word it declares tested at every level; and a village that has
 * already passed an age gets the new party in the chronicle, paying nothing.
 */
class FriuliCelebrationsTest {
    private val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory }
    private val levels = listOf("A1", "A2", "B1", "B2")
    private val pair = L10n.pair
    private val pages = Grammar.bundled("it").associateBy { it.id }
    private val cast = File(root, "cultures/friuli/villagers").listFiles { f -> f.extension == "json" }.orEmpty().map { it.nameWithoutExtension }.toSet()

    @After fun back() {
        L10n.pair = pair
        Cultures.use(Cultures.DEFAULT)
    }

    private fun friuli(): List<Celebration> {
        L10n.pair = LangPair(Lang.IT, Lang.DE)
        Cultures.use(Cultures.load("friuli"))
        return Age.entries.drop(1).map { AgeUps.celebration(it) }
    }

    private fun packFile(id: String): JsonObject = json.parseToJsonElement(File(root, "cultures/friuli/packs/$id.json").readText()).jsonObject

    private fun bare(it: String) = it.replace(Regex("^(?:(?:il|lo|la|i|gli|le) +|l')"), "")

    /** A pack's words as the learner's cards (vocab_<pack>_<word>), by the word without its article and its plural. */
    private fun cards(packId: String): List<MyWord> = (packFile(packId)["words"] as JsonArray).map { w ->
        val o = w.jsonObject
        val word = bare(o.getValue("it").jsonPrimitive.content)
        val plural = o["plural"]?.jsonPrimitive?.contentOrNull?.let(::bare)
        MyWord("vocab_${packId}_${o.getValue("id").jsonPrimitive.content}", word, o["de"]?.jsonPrimitive?.contentOrNull.orEmpty(), setOfNotNull(word, plural))
    }

    /** A learner at [level] who was introduced to nothing: the pages above their level are not yet. */
    private fun fresh(level: String): (String) -> Boolean = { id -> Introduction.above(pages[id]?.level, level) }

    @Test fun `every age after the first is a tradition of Friuli's, with a party at each level and its words`() {
        val all = friuli()
        assertEquals(listOf("Il primo fogolâr", "Il licôf", "La sagra", "La fiera", "Il palio"), all.map { it.nameText.target })
        for (c in all) {
            val at = c.age.name
            assertFalse("$at: its own", c.generic)
            assertTrue("$at: a party", c.party)
            assertEquals("$at: its levels", levels.toSet(), c.levels.keys)
            assertTrue("$at: host ${c.host} and guests ${c.guests} of the cast", (listOfNotNull(c.host) + c.guests).all { it in cast })
            for (lang in listOf("it", "sl", "en", "de")) {
                for (t in listOfNotNull(c.nameText, c.aboutText, c.momentText, c.lineText, c.memoryText)) assertTrue("$at: $lang of $t", t.by.containsKey(lang))
            }
            val pack = assertNotNull("$at: its word pack", c.pack).let { c.pack!! }
            assertNotNull("$at: the app bundles its pack", FestivalPacks.pack(pack))
            val words = cards(pack)
            assertTrue("$at: ${words.size} words", words.size in 8..24)
            assertTrue("$at: 1-4 words tested", c.words.size in 1..4)
            for (w in c.words) assertTrue("$at: «$w» in $pack", words.any { it.id == "vocab_${pack}_$w" })
        }
        assertEquals(listOf(Decor.TORCHES, Decor.SPRUCE, Decor.FLOOR, Decor.STALLS, Decor.BANNERS), all.map { c -> c.decor.first { it != Decor.BUNTING } })
        assertEquals(listOf(Music.VALCEK, Music.VALCEK, Music.HARMONIKA, Music.HARMONIKA, Music.GODBA), all.map { it.music })
        assertEquals(listOf(PartyPlace.FIRE, PartyPlace.FIRE, PartyPlace.SQUARE, PartyPlace.SQUARE, PartyPlace.SQUARE), all.map { it.place })
    }

    @Test fun `each party short, a toast in it, said by its host and guests`() {
        for (c in friuli()) for (level in levels) {
            val at = "${c.age} $level"
            val d = assertNotNull(at, AgeUps.dialog(c, level, "it")).let { AgeUps.dialog(c, level, "it")!! }
            assertTrue("$at: ${d.lines.size} lines", d.lines.size in 4..10)
            assertTrue("$at: the first line someone's", d.lines.first().choices.isEmpty())
            val turns = d.lines.filter { it.choices.isNotEmpty() }
            assertTrue("$at: ${turns.size} turns", turns.size in 2..4)
            val speakers = d.lines.mapNotNull { it.who }.toSet()
            assertTrue("$at: the host speaks", c.host in speakers)
            assertTrue("$at: only the host and the guests ($speakers)", (speakers - setOfNotNull(c.host) - c.guests.toSet()).isEmpty())
            assertTrue("$at: a toast", turns.any { t -> t.choices.any { it.ok && it.sl.contains("alla salute", ignoreCase = true) } && t.choices.any { it.ok && it.sl.contains("cin cin", ignoreCase = true) } })
            for (t in turns) {
                assertTrue("$at: a wrong choice", t.choices.any { !it.ok })
                for (ch in t.choices.filter { !it.ok }) {
                    assertTrue("$at: «${ch.sl}» has a why", !ch.why.isNullOrBlank())
                    assertNotNull("$at: «${ch.sl}» has a reaction", ch.reply)
                }
            }
            // read in another pair: its Slovene
            val sl = AgeUps.dialog(c, level, "it", LangPair(Lang.IT, Lang.SL))!!
            assertEquals(d.lines.first().sl, sl.lines.first().sl)
            assertTrue("$at: the Slovene meaning", sl.lines.first().en != d.lines.first().en)
        }
    }

    @Test fun `the learner's level picks its telling, and a learner above B2 keeps B2`() {
        val c = friuli().first { it.age == Age.VAS }
        val first = levels.associateWith { AgeUps.dialog(c, it, "it")!!.lines.first().sl }
        assertEquals("all four are told differently", 4, first.values.toSet().size)
        assertEquals(first["B2"], AgeUps.dialog(c, "C1", "it")!!.lines.first().sl)
    }

    @Test fun `the placeholders are well formed, for a boy and for a girl`() {
        val files = File(root, "cultures/friuli/celebrations").listFiles { x -> x.extension == "json" }.orEmpty()
        assertEquals(5, files.size)
        for (f in files) for (who in listOf(Learner.of("Teo", "male"), Learner.of("Ana", "female"))) {
            val said = who.renderJson(f.readText())
            val left = Regex("""\{[^"\s}]*""").findAll(said).map { it.value }.filter { it.startsWith("{m:") || it.startsWith("{f:") || it.startsWith("{learner") }.toList()
            assertTrue("${f.name} said to ${who.name}: $left", left.isEmpty())
            assertTrue(f.name, runCatching { json.parseToJsonElement(said) }.isSuccess)
        }
    }

    @Test fun `a fresh learner has something to answer, and every word a party declares is tested at every level`() {
        for (c in friuli()) {
            val words = MyWords(cards(c.pack!!))
            for (level in levels) {
                val at = "${c.age} $level"
                val d: Dialog = AgeUps.dialog(c, level, "it")!!
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

    @Test fun `a guest's or host's line says nothing of who says it`() {
        // AgeUps.cast: whoever is away has their lines said by someone else, a woman for a man: "sono arrivato" would read wrong
        val gendered = Regex("""\b(sono|sei)\s+(\p{L}+(ato|ata|uto|uta|ito|ita)|stanc[oa]|content[oa]|pront[oa]|sol[oa]|venut[oa])\b""", RegexOption.IGNORE_CASE)
        for (c in friuli()) for (level in levels) {
            val d = AgeUps.dialog(c, level, "it")!!
            for (l in d.lines) if (l.who != null) assertFalse("${c.age} $level: «${l.sl}»", gendered.containsMatchIn(l.sl.orEmpty()))
        }
    }

    @Test fun `a village past an age sees the new party in the chronicle, and it pays nothing there`() {
        friuli()
        val today = LocalDate.of(2026, 11, 2)
        val s = GameState(age = Age.TRG, grown = mapOf("tabor" to "2026-05-01", "zaselek" to "2026-06-01", "vas" to "2026-07-01", "trg" to "2026-08-01"))
        val ms = AgeUps.milestones(s)
        assertEquals(listOf("La fiera", "La sagra", "Il licôf", "Il primo fogolâr"), ms.map { it.celebration.nameText.target })
        assertTrue(ms.all { it.celebration.party })
        // long past its evenings: no bubble tonight
        assertNull(AgeUps.partyNow(s, today.atTime(19, 0)))
        // an earlier age's, played again: nothing
        val (same, first) = AgeUps.partyHad(s, Age.VAS, today, 1L)
        assertFalse(first)
        assertEquals(s, same)
        // the village's own age, never had as a party (it was a gathering): once, then not again
        val (paid, once) = AgeUps.partyHad(s, Age.TRG, today, 1L)
        assertTrue(once)
        assertEquals(today.toString(), paid.parties["trg"])
        assertFalse(AgeUps.partyHad(paid, Age.TRG, today, 2L).second)
        // a village in the middle of it: the party on the evening the age came, as any culture's
        val fresh = s.copy(grown = s.grown + ("trg" to today.toString()))
        assertEquals("La fiera", AgeUps.partyNow(fresh, today.atTime(19, 0))?.nameText?.target)
    }
}
