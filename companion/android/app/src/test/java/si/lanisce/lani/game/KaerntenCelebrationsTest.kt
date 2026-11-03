package si.lanisce.lani.game

import kotlinx.serialization.json.JsonArray
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
import org.junit.Before
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
 * Kärnten's celebrations of its ages (companion/cultures/kaernten/celebrations, GAME.md "Ages and their celebrations"), as
 * the app reads them: every age after the first its own custom with a party at A1, A2, B1 and B2, its word pack bundled,
 * each dialog short, said by its host and guests of the cast, a toast in it, a fresh learner left something to answer and
 * every word it declares tested at each level in a form the dictionary knows; a guest's lines say nothing of who says them,
 * in the translations too; and a village already past an age sees the new celebration in its chronicle and is paid only
 * for its own age.
 */
class KaerntenCelebrationsTest {
    private val root = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory }
    private val dir = File(root, "cultures/kaernten")
    private val levels = listOf("A1", "A2", "B1", "B2")
    private val pair = L10n.pair
    private val pages = Grammar.bundled("de").associateBy { it.id }
    private val cast = File(dir, "villagers").listFiles { f -> f.extension == "json" }.orEmpty().map { it.nameWithoutExtension }.toSet()

    @Before fun carinthia() {
        L10n.pair = LangPair(Lang.DE, Lang.EN)
        Cultures.use(Cultures.load("kaernten"))
    }

    @After fun back() {
        L10n.pair = pair
        Cultures.use(Cultures.DEFAULT)
    }

    private fun all(): List<Celebration> = Age.entries.drop(1).map { AgeUps.celebration(it) }

    /** The supplement's forms of each lemma, lower case (a German learner's dictionary: companion/lexicon/de.extra.json). */
    private val forms: Map<String, Set<String>> by lazy {
        val out = HashMap<String, MutableSet<String>>()
        val extra = json.parseToJsonElement(File(root, "lexicon/de.extra.json").readText()).jsonObject
        for (e in extra.getValue("entries").jsonArray) {
            val o = e.jsonObject
            val lemma = o["lemma"]?.jsonPrimitive?.contentOrNull ?: continue
            out.getOrPut(lemma.lowercase()) { HashSet() }.addAll(o["forms"]?.jsonObject?.keys.orEmpty().map { it.lowercase() })
        }
        out
    }

    private val article = Regex("^(der|die|das) ")

    /** A pack's words as the learner's cards (vocab_<pack>_<word>): each without its article, with its plural and its forms. */
    private fun cards(packId: String): List<MyWord> {
        val pack = json.parseToJsonElement(File(dir, "packs/$packId.json").readText()).jsonObject
        return (pack["words"] as JsonArray).map { w ->
            val o = w.jsonObject
            val word = o.getValue("de").jsonPrimitive.content.replace(article, "")
            val plural = o["plural"]?.jsonPrimitive?.contentOrNull?.replace(article, "")?.lowercase()
            MyWord("vocab_${packId}_${o.getValue("id").jsonPrimitive.content}", word, o["en"]?.jsonPrimitive?.contentOrNull.orEmpty(), forms[word.lowercase()].orEmpty() + setOfNotNull(plural, word.lowercase()))
        }
    }

    /** A learner at [level] who was introduced to nothing: the pages above their level are not yet. */
    private fun fresh(level: String): (String) -> Boolean = { id -> Introduction.above(pages[id]?.level, level) }

    @Test fun `every age after the first is a custom of its own, with a party at A1 to B2 and its words`() {
        val all = all()
        assertEquals(listOf("Der erste Abend am Feuer", "Die Firstfeier", "Das Dorffest", "Der Wiesenmarkt", "Das Stadtfest"), all.map { it.nameText.target })
        assertEquals(listOf("sepp", "franz", "kathi", "stefan", "eva"), all.map { it.host })
        for (c in all) {
            val at = c.age.name
            assertFalse("$at: its own", c.generic)
            assertTrue("$at: a party", c.party)
            assertTrue("$at: A1 to B2 (${c.levels.keys})", c.levels.keys.containsAll(levels))
            assertTrue("$at: host ${c.host} and guests ${c.guests} of the cast", (listOfNotNull(c.host) + c.guests).all { it in cast })
            for (lang in listOf("de", "sl", "en", "it")) {
                for (t in listOfNotNull(c.nameText, c.aboutText, c.momentText, c.lineText, c.memoryText)) assertTrue("$at: $lang of $t", t.by.containsKey(lang))
            }
            val pack = assertNotNull("$at: its word pack", c.pack).let { c.pack!! }
            assertNotNull("$at: the app bundles its pack", FestivalPacks.pack(pack))
            val words = cards(pack)
            assertTrue("$at: ${words.size} words", words.size in 8..24)
            assertTrue("$at: 1-4 words tested", c.words.size in 1..4)
            for (w in c.words) assertTrue("$at: «$w» in $pack", words.any { it.id == "vocab_${pack}_$w" })
        }
        // the decorations of each: the first evening's torches, the Firstfeier's tree on the ridge, the Dorffest's dance floor,
        // the Wiesenmarkt's stalls, the Stadtfest's banners; the music as the map plays it
        assertEquals(listOf(Decor.TORCHES, Decor.SPRUCE, Decor.FLOOR, Decor.STALLS, Decor.BANNERS), all.map { c -> c.decor.first { it != Decor.BUNTING } })
        assertEquals(listOf(Music.VALCEK, Music.VALCEK, Music.HARMONIKA, Music.HARMONIKA, Music.GODBA), all.map { it.music })
    }

    @Test fun `each party short, a toast in it, said by its host and guests`() {
        var lines = 0
        for (c in all()) for (level in levels) {
            val at = "${c.age} $level"
            val d = assertNotNull(at, AgeUps.dialog(c, level, "de")).let { AgeUps.dialog(c, level, "de")!! }
            lines += d.lines.size
            assertTrue("$at: ${d.lines.size} lines", d.lines.size in 4..10)
            assertTrue("$at: the first line someone's", d.lines.first().choices.isEmpty())
            val turns = d.lines.filter { it.choices.isNotEmpty() }
            assertTrue("$at: ${turns.size} turns", turns.size in 2..4)
            val speakers = d.lines.mapNotNull { it.who }.toSet()
            assertTrue("$at: the host speaks", c.host in speakers)
            assertTrue("$at: only the host and the guests ($speakers)", (speakers - setOfNotNull(c.host) - c.guests.toSet()).isEmpty())
            assertTrue("$at: a toast", turns.any { t -> t.choices.any { it.ok && listOf("Prost", "Zum Wohl", "Hoch").any { x -> it.sl.contains(x) } } })
            for (t in turns) {
                assertTrue("$at: a right choice", t.choices.any { it.ok })
                assertTrue("$at: a wrong choice", t.choices.any { !it.ok })
                assertFalse("$at: no tap turn over the village", t.choices.any { it.tap != null })
                for (ch in t.choices.filter { !it.ok }) {
                    assertTrue("$at: «${ch.sl}» has a why", !ch.why.isNullOrBlank())
                    assertNotNull("$at: «${ch.sl}» has a reaction", ch.reply)
                    ch.grammar?.let { g -> assertTrue("$at: page $g", g in pages) }
                    // the reaction never says the right form
                    for (ok in t.choices.filter { it.ok }) assertFalse("$at: «${ch.reply?.sl}» says «${ok.sl}»", ch.reply?.sl.orEmpty().contains(ok.sl))
                }
            }
            // read from Slovene: the German stays, its meaning follows the base
            val sl = AgeUps.dialog(c, level, "de", LangPair(Lang.DE, Lang.SL))!!
            assertEquals(d.lines.first().sl, sl.lines.first().sl)
            assertTrue("$at: the Slovene meaning", sl.lines.first().en != d.lines.first().en)
        }
        println("Kärnten's celebrations: 20 parties, $lines lines")
    }

    @Test fun `a turn always has someone who answers it`() {
        for (c in all()) for (level in levels) {
            val d = AgeUps.dialog(c, level, "de")!!
            d.lines.forEachIndexed { i, l ->
                if (l.choices.isNotEmpty()) assertNotNull("${c.age} $level turn $i", (l.to ?: d.lines.take(i).lastOrNull { it.who != null }?.who))
            }
        }
    }

    @Test fun `the learner's placeholders are well formed, for a man and for a woman`() {
        val files = File(dir, "celebrations").listFiles { x -> x.extension == "json" }.orEmpty()
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

    @Test fun `a fresh learner has something to answer, and every word a party declares is tested at each level`() {
        for (c in all()) {
            val words = MyWords(cards(c.pack!!))
            for (level in levels) {
                val at = "${c.age} $level"
                val d: Dialog = AgeUps.dialog(c, level, "de")!!
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

    @Test fun `a guest's lines say nothing of who says them, in Slovene and Italian either`() {
        val slovene = Regex("""\bsem\s+\p{L}*(l|la|lo)\b|\b\p{L}{2,}(l|la)\s+sem\b|\b(rad|rada|sam|sama)\b""")
        val italian = Regex("""\b(sono|sei)\s+\p{L}+[oa]\b""")
        val placeholder = Regex("""\{m:[^}]*\}""")
        for (f in File(dir, "celebrations").listFiles { x -> x.extension == "json" }.orEmpty()) {
            val c = json.parseToJsonElement(f.readText()).jsonObject
            val guests = c.getValue("guests").jsonArray.map { it.jsonPrimitive.content }.toSet()
            for ((level, t) in c.getValue("levels").jsonObject) for (l in t.jsonObject.getValue("lines").jsonArray) {
                val line = l.jsonObject
                if (line["who"]?.jsonPrimitive?.contentOrNull !in guests) continue
                val sl = line.getValue("sl").jsonPrimitive.content.replace(placeholder, "").lowercase()
                val it = line.getValue("it").jsonPrimitive.content.replace(placeholder, "").lowercase()
                assertFalse("${f.name} $level: «$sl»", slovene.containsMatchIn(sl))
                assertFalse("${f.name} $level: «$it»", italian.containsMatchIn(it))
            }
        }
    }

    @Test fun `a village past an age sees the new celebration in its chronicle and is paid only for its own age`() {
        val day = LocalDate.of(2026, 11, 2)
        // a Carinthian village that reached the Markt months ago on the generic gathering: no party kept
        val old = GameState(age = Age.TRG, grown = mapOf("vas" to "2026-05-01", "trg" to "2026-08-01"))
        val ms = AgeUps.milestones(old)
        assertEquals(listOf(Age.TRG, Age.VAS, Age.ZASELEK, Age.TABOR), ms.map { it.age })
        assertEquals(listOf("Der Wiesenmarkt", "Das Dorffest", "Die Firstfeier", "Der erste Abend am Feuer"), ms.map { it.celebration.nameText.target })
        // nothing comes up by itself: no party tonight, no decorations on the map
        assertNull(AgeUps.partyNow(old, day.atTime(19, 0)))
        assertNull(AgeUps.partyTonight(old, day.atTime(10, 0)))
        assertNull(AgeUps.decorOn(old, day))
        // watched again from the chronicle: the party is there at each level, an earlier age's pays nothing
        for (m in ms) for (level in levels) assertNotNull("${m.age} $level", AgeUps.dialog(m.celebration, level, "de"))
        val (same, paidEarlier) = AgeUps.partyHad(old, Age.VAS, day, 0L)
        assertFalse(paidEarlier)
        assertEquals(old, same)
        // its own age's pays once, with the culture's line, the next time nothing
        val (had, first) = AgeUps.partyHad(old, Age.TRG, day, 0L)
        assertTrue(first)
        assertEquals(day.toString(), had.parties["trg"])
        assertEquals(AgeUps.celebration(Age.TRG).line, had.log.last().text)
        val (_, again) = AgeUps.partyHad(had, Age.TRG, day.plusDays(1), 0L)
        assertFalse(again)
        // a village reaching the age today has it tonight, and the decorations
        val today = GameState(age = Age.VAS, grown = mapOf("vas" to day.toString()))
        assertEquals("Das Dorffest", AgeUps.partyNow(today, day.atTime(19, 0))?.nameText?.target)
        assertEquals("Das Dorffest", AgeUps.decorOn(today, day)?.nameText?.target)
    }
}
