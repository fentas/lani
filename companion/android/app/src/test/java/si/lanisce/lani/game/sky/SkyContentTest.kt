package si.lanisce.lani.game.sky

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.FestivalPacks
import si.lanisce.lani.game.culture.CultureError
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Every culture pack's night sky (companion/cultures/<id>/sky.json) and its sky word pack: read and checked without a
 * problem, the village where the pack says it is, a card for every constellation, the Pleiades, the planets, the Milky
 * Way and the Pole Star, lore at A1 and A2 for each, all eight phases, the six showers, the moon's and the shooting
 * stars' words in the pack's sky word pack; and the cards read in the pack's language for its learners' pairs.
 */
class SkyContentTest {
    private val pair = L10n.pair

    @After fun back() { L10n.pair = pair }

    /** The packs with a sky, and the pair of a learner of each (the pack's language, explained in Slovene or English). */
    private val packs = mapOf(
        "primorska" to LangPair(Lang.SL, Lang.EN),
        "friuli" to LangPair(Lang.IT, Lang.SL),
        "kaernten" to LangPair(Lang.DE, Lang.SL),
        "lakeland" to LangPair(Lang.EN, Lang.SL),
    )

    /** Where each village lies (sky.json `where`): the Trnovo plateau, the Collio, the lower Gailtal, a Lakeland dale. */
    private val places = mapOf(
        "primorska" to SkyPlace(45.99, 13.75), "friuli" to SkyPlace(45.97, 13.52),
        "kaernten" to SkyPlace(46.59, 13.62), "lakeland" to SkyPlace(54.53, -3.10),
    )

    private fun utc(iso: String) = LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun `every culture's sky reads without a problem and names everything the sky shows`() {
        val problems = ArrayList<CultureError>()
        Cultures.onProblem = { problems += it }
        try {
            for ((id, _) in packs) {
                val sky = checkNotNull(Cultures.sky(id)) { "$id/sky.json: ${problems.joinToString { it.message.orEmpty() }}" }
                assertEquals(id, places.getValue(id), SkyCards.place(sky))
                assertEquals(id, Cultures.load(id).language.code, sky.language)
                // every figure, the Pleiades, the planets, the Milky Way and the Pole Star have a card of the pack's own
                val ids = sky.things.map { it.id }
                val want = Stars.figures.map { it.id } + Planet.entries.map { it.id } + listOf(SkyCards.MILKY_WAY, "polaris")
                assertEquals("$id: things missing", emptyList<String>(), want - ids.toSet())
                for (t in sky.things) {
                    val levels = t.lore.map { it.level }.toSet()
                    assertTrue("$id ${t.id}: lore at A1 and A2 ($levels)", "A1" in levels && "A2" in levels)
                }
                assertEquals("$id: the eight phases", SkyCards.PHASES.toSet(), sky.moon.phases.keys)
                assertTrue("$id: the moon's lore at A1, A2 and B1", sky.moon.lore.map { it.level }.toSet().containsAll(listOf("A1", "A2", "B1")))
                assertEquals("$id: the six showers", Meteors.SHOWERS.map { it.id }.toSet(), sky.meteors!!.showers.map { it.id }.toSet())
                assertNotNull("$id: the moon's card opens a reading", sky.moon.reading)
                // the moon's, the shooting stars' and the folk names' words are in the pack's sky word pack
                val pack = checkNotNull(sky.pack?.let { FestivalPacks.pack(it) }) { "$id: its sky word pack ${sky.pack}" }
                assertEquals(id, sky.language, pack.language)
                val words = pack.words.map { it.word.lowercase() }.toSet()
                val needed = sky.moon.words + sky.meteors!!.words
                assertEquals("$id: words not in ${pack.id}", emptyList<String>(), needed.filter { it.lowercase() !in words })
                assertTrue("$id: ${pack.words.size} words in ${pack.id}", pack.words.size in 12..24)
            }
            assertEquals(emptyList<String>(), problems.map { it.message })
        } finally {
            Cultures.onProblem = null
        }
    }

    @Test fun `the cards read in each pack's language for its learners`() {
        for ((id, p) in packs) {
            L10n.pair = p
            val sky = Cultures.sky(id)!!
            val now = SkyNow.exact(utc("2026-09-27T19:00"), SkyCards.place(sky))
            val zone = ZoneId.of("Europe/Ljubljana")
            val moon = SkyCards.of(SkyTap.Moon, now, sky, "A2", zone)!!
            assertEquals("$id: the moon's name", sky.moon.name.of(p.target), moon.title.target)
            assertEquals("$id: the full moon's name", sky.moon.phases.getValue("full").of(p.target), moon.lines.first().target)
            assertTrue("$id: its translation in ${p.base}", moon.lines.first().base != null)
            assertNotNull("$id: a line of lore", moon.lore)
            val orion = SkyCards.of(SkyTap.Figure("ori"), now, sky, "A1", zone)!!
            assertTrue("$id: Orion's folk name", orion.folk.isNotEmpty())
            val wish = SkyCards.of(SkyTap.Meteor(1L, "perseids"), SkyNow.exact(utc("2026-08-12T22:00"), SkyCards.place(sky)), sky, "A1", zone)!!
            assertEquals("$id: the wish", sky.meteors!!.wish.of(p.target), wish.lines.first().target)
            assertTrue("$id: the Perseids tonight", wish.lines.size == 2)
            val venus = SkyCards.of(SkyTap.Planet("venus"), SkyNow.exact(utc("2026-08-15T18:30"), SkyCards.place(sky)), sky, "A1", zone)!!
            assertEquals("$id: Venus in the evening", sky.things.first { it.id == "venus" }.evening!!.of(p.target), venus.folk.first().target)
        }
    }

    @Test fun `Primorska's sky in Jan's words`() {
        L10n.pair = LangPair.DEFAULT
        val sky = Cultures.sky("primorska")!!
        val ori = sky.things.first { it.id == "ori" }
        assertEquals("Kosci", ori.folk!!.of(Lang.SL))
        assertEquals("Gostosevci", sky.things.first { it.id == "m45" }.folk!!.of(Lang.SL))
        assertEquals("Veliki voz", sky.things.first { it.id == "uma" }.folk!!.of(Lang.SL))
        assertEquals("Severnica", sky.things.first { it.id == "polaris" }.folk!!.of(Lang.SL))
        assertEquals("Rimska cesta", sky.things.first { it.id == "milky-way" }.name.of(Lang.SL))
        val venus = sky.things.first { it.id == "venus" }
        assertEquals("Danica" to "Večernica", venus.morning!!.of(Lang.SL) to venus.evening!!.of(Lang.SL))
        assertEquals("Lovrenčeve solze", sky.meteors!!.showers.first { it.id == "perseids" }.folk!!.of(Lang.SL))
        assertEquals("stara-pratika", sky.moon.reading)
        assertEquals(listOf("Mlaj", "Prvi krajec", "Zadnji krajec"), listOf("new", "first_quarter", "last_quarter").map { sky.moon.phases.getValue(it).of(Lang.SL) })
        assertTrue(sky.moon.phases.getValue("full").of(Lang.SL).startsWith("Ščip"))
        assertEquals("Luna narašča." to "Luna pojema.", sky.moon.waxing.of(Lang.SL) to sky.moon.waning.of(Lang.SL))
        // the almanac's lore for a waxing moon, at A2
        val waxing = SkyCards.of(SkyTap.Moon, SkyNow.exact(utc("2026-09-14T18:00"), SkyCards.place(sky)), sky, "A2", ZoneId.of("Europe/Ljubljana"))!!
        assertTrue(waxing.lore!!.target, waxing.lore!!.target.startsWith("Stara pratika pravi"))
    }
}
