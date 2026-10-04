package si.lanisce.lani.game.scene

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.scene.DialogRun
import java.io.File
import java.time.LocalDate

/**
 * The charcoal pile of every culture pack (world.json `spots.kopa`): its card reads in the culture's words for its learners'
 * pairs (Oglarska kopa, la carbonaia, der Kohlenmeiler, the charcoal pit), its history is told at A1 and A2 and names the
 * village's smith, its words are a pack of the culture's, and its charcoal burner comes some evenings with a talk that plays
 * to its end at both levels, every wrong choice first.
 */
class KopaCulturesTest {
    private val pair = L10n.pair
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory }

    /**
     * Culture, the pairs its learners read it in with the card's name in each, the smith its story names (his name's stem:
     * Slovene declines it, Tonetu, Brunu), the burner.
     */
    private data class Case(val id: String, val names: Map<LangPair, String>, val smith: String, val burner: String)

    private val cases = listOf(
        Case("primorska", mapOf(LangPair(Lang.SL, Lang.EN) to "Oglarska kopa · The charcoal pile", LangPair(Lang.SL, Lang.DE) to "Oglarska kopa · Der Kohlenmeiler"), "Ton", "Oglar Miha"),
        Case("friuli", mapOf(LangPair(Lang.IT, Lang.SL) to "La carbonaia · Oglarska kopa", LangPair(Lang.IT, Lang.EN) to "La carbonaia · The charcoal pile"), "Brun", "Carbonaio Pieri"),
        Case("kaernten", mapOf(LangPair(Lang.DE, Lang.SL) to "Der Kohlenmeiler · Oglarska kopa", LangPair(Lang.DE, Lang.IT) to "Der Kohlenmeiler · La carbonaia"), "Toni", "Köhler Michl"),
        Case("lakeland", mapOf(LangPair(Lang.EN, Lang.SL) to "The charcoal pit · Oglarska kopa", LangPair(Lang.EN, Lang.DE) to "The charcoal pit · Der Kohlenmeiler"), "Dixon", "Collier Ned"),
    )

    @After fun back() {
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = pair
    }

    @Test fun `every culture names its pile, and its card tells its history at A1 and A2, naming the smith`() {
        for (c in cases) {
            Cultures.use(c.id)
            val spot = Cultures.current.world.spots.getValue("kopa")
            for ((p, name) in c.names) {
                L10n.pair = p
                assertEquals("${c.id} ${p.code}", name, TownSpots.info("kopa").label)
                assertEquals("♨️", TownSpots.info("kopa").emoji)
                val a1 = Keepers.lore(spot, "A1", Cultures.current.language, p)!!
                val a2 = Keepers.lore(spot, "B2", Cultures.current.language, p)!!
                assertEquals("A2", a2.level)
                assertNotEquals(a1.said, a2.said)
                for (l in listOf(a1, a2)) {
                    assertTrue("${c.id} ${l.level}: ${l.said}", c.smith in l.said && c.smith in l.meant)
                    assertTrue("${c.id} ${l.level}: in the village's language, meant in the base", l.meant.isNotBlank() && l.meant != l.said)
                }
            }
            assertTrue("${c.id}: machine-written", "machine-written" in spot.review.orEmpty())
        }
    }

    @Test fun `every pile's words are a pack of its culture, in its language with the four`() {
        val langs = listOf("sl", "en", "de", "it")
        for (c in cases) {
            Cultures.use(c.id)
            val spot = Cultures.current.world.spots.getValue("kopa")
            val file = File(companion, "cultures/${c.id}/packs/${spot.pack}.json")
            assertTrue("${c.id}: ${file.path}", file.isFile)
            val pack = json.parseToJsonElement(file.readText()) as kotlinx.serialization.json.JsonObject
            val words = pack.getValue("words") as kotlinx.serialization.json.JsonArray
            assertTrue("${c.id}: ${words.size} words", words.size in 8..14)
            for (w in words) {
                val o = w as kotlinx.serialization.json.JsonObject
                assertTrue("${c.id}/${o["id"]}: every language", langs.all { o[it] != null })
                assertTrue("${c.id}/${o["id"]}: an example in every language", langs.all { o["example_$it"] != null })
            }
        }
    }

    @Test fun `every culture's burner comes some evenings, and his talk plays to its end at A1 and A2`() {
        val village = GameState(seed = 42)
        for (c in cases) {
            Cultures.use(c.id)
            val world = Cultures.current.world
            val keeper = world.spots.getValue("kopa").keeper!!
            assertEquals(c.burner, keeper.name)
            assertEquals("burner", keeper.art)
            // about every other evening, never by day
            val days = (0 until 60).map { LocalDate.of(2026, 9, 1).plusDays(it.toLong()) }
            val evenings = days.count { Keepers.here(village, it.atTime(21, 0), world).isNotEmpty() }
            assertTrue("${c.id}: $evenings of 60 evenings", evenings in 15..45)
            assertTrue(days.all { Keepers.here(village, it.atTime(10, 0), world).isEmpty() })
            val k = days.firstNotNullOf { Keepers.here(village, it.atTime(21, 0), world).firstOrNull() }
            for ((p, _) in c.names) for (level in listOf("A1", "A2")) playThrough(k, level, p, "${c.id} $level")
        }
    }

    @Test fun `every talk of every burner's story, in each of its tellings, plays to its end at A1 and A2`() {
        val village = GameState(seed = 42)
        for (c in cases) {
            Cultures.use(c.id)
            val world = Cultures.current.world
            val keeper = world.spots.getValue("kopa").keeper!!
            val k = (0 until 60).firstNotNullOf { Keepers.here(village, LocalDate.of(2026, 9, 1).plusDays(it.toLong()).atTime(21, 0), world).firstOrNull() }
            assertEquals("${c.id}: his talks' ids", keeper.talks.size, keeper.talks.map { it.id }.toSet().size)
            keeper.talks.forEachIndexed { i, t ->
                for (round in 0..t.again.size) {
                    val at = k.copy(at = 1 + i + round * keeper.talks.size)
                    assertEquals(t.id, at.story?.id)
                    assertEquals(round, Keepers.round(keeper, at.at))
                    for ((p, _) in c.names) for (level in listOf("A1", "A2")) playThrough(at, level, p, "${c.id} ${t.id} ($round) $level")
                }
            }
        }
    }

    /** [k]'s talk at [level] in [pair], played to its end, every wrong choice first: he reacts to each, and there is one. */
    private fun playThrough(k: ActiveKeeper, level: String, pair: LangPair, what: String) {
        val d = Keepers.talk(k, level, Cultures.current.manifest.language, pair)
        assertNotNull("$what ${pair.code}", d)
        var run = DialogRun.start(d!!, k.keeper.id, seed = 5)
        var wrongs = 0
        var steps = 0
        while (run.step != DialogRun.Step.END && steps++ < 40) {
            if (run.step == DialogRun.Step.LISTEN) { run = run.next(); continue }
            val bad = run.choices.indices.firstOrNull { !run.choices[it].ok && it !in run.tried }
            if (bad == null) { run = run.choose(run.choices.indexOfFirst { it.ok }); continue }
            run = run.choose(bad)
            wrongs++
            assertEquals("$what: he reacts", k.keeper.id, run.said.last().who)
        }
        assertEquals(what, DialogRun.Step.END, run.step)
        assertTrue("$what: a wrong choice to learn from", wrongs >= 1)
        assertEquals(wrongs, run.mistakes)
        // his lines in the village's language, meant in the base
        assertTrue("$what: ${run.said.first().sl}", run.said.first().who == k.keeper.id && run.said.first().en.isNotBlank())
    }

    @Test fun `Primorska's high seat is the hunter's, with his words`() {
        Cultures.use("primorska")
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        val seat = Cultures.current.world.spots.getValue("highseat")
        assertEquals("Preža · The high seat", TownSpots.info("highseat").label)
        assertEquals("na preži · on the high seat", TownSpots.info("highseat").where)
        assertEquals("Tu lovci opazujejo divjad, ob zori in v mraku · Here the hunters watch the game, at dawn and at dusk", seat.about?.bi())
        assertEquals("na-prezi", seat.pack)
        assertTrue(File(companion, "packs/na-prezi.json").isFile)
    }
}
