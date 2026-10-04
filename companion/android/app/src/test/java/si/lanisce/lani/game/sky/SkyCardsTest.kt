package si.lanisce.lani.game.sky

import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.SkyFile
import si.lanisce.lani.game.culture.SkyLore
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The sky's cards ([SkyCards]) from a small sky.json (the fixture below, in Slovene with English) and the sky of known
 * moments over the Primorska village: the moon's phase, its days, when it rises and sets (the Naval Observatory's times, on the
 * village's clock), the next full and new moons; a constellation from a tapped star, the Pole Star's own card, Venus as
 * the evening and the morning star, a shooting star on a night of the Perseids; lore at the learner's level; the app's
 * own names where a culture's sky says none; and what the check of a sky.json finds wrong.
 */
class SkyCardsTest {
    private val zone = ZoneId.of("Europe/Ljubljana")
    private val place = SkyPlace(45.99, 13.75)
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = false }
    private val sky: SkyFile = json.decodeFromString(SkyFile.serializer(), FIXTURE)
    private var pair = L10n.pair

    @Before fun sl() { pair = L10n.pair; L10n.pair = LangPair.DEFAULT }
    @After fun back() { L10n.pair = pair }

    private fun at(iso: String): SkyNow = SkyNow.exact(LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli(), place)
    private fun card(tap: SkyTap, iso: String, level: String = "A2", s: SkyFile? = sky) = checkNotNull(SkyCards.of(tap, at(iso), s, level, zone)) { "$tap at $iso" }

    @Test fun `the moon a day past full, with its days and times`() {
        // 27 September 2026, 20:00 CEST: full on the 26th at 16:49 UT; it rose at 16:54 UT (18:54) and set at 5:46 UT (7:46)
        val c = card(SkyTap.Moon, "2026-09-27T18:00")
        assertEquals(Said("Luna", "The Moon"), c.title)
        assertEquals(Said("Ščip – polna luna", "Full moon"), c.lines.first())
        assertFalse("full: neither waxing nor waning", c.lines.any { it.target.startsWith("Luna narašča") || it.target.startsWith("Luna pojema") })
        assertTrue(c.facts.toString(), c.facts.any { it.endsWith("Osvetljena: 99 % · Lit: 99%") })
        assertTrue(c.facts.toString(), c.facts.any { it.endsWith("Starost: 17 dni · Age: 17 days") })
        assertTrue(c.facts.toString(), c.facts.any { it.endsWith("Zaide ob 7:46 · Sets at 7:46") || it.endsWith("Zaide ob 7:45 · Sets at 7:45") || it.endsWith("Zaide ob 7:47 · Sets at 7:47") })
        assertTrue(c.facts.toString(), c.facts.any { Regex("Vzide ob 18:5[345] · Rises at 18:5[345]$").containsMatchIn(it) })
        assertTrue(c.facts.toString(), c.facts.any { it.endsWith("Naslednji ščip: 26. oktobra · Next full moon: 26 October") })
        assertTrue(c.facts.toString(), c.facts.any { it.endsWith("Naslednji mlaj: 10. oktobra · Next new moon: 10 October") })
        // a full moon's lore, at A2 the A2 one; the words; the almanac to read
        assertEquals("Ščip vzide, ko sonce zaide.", c.lore?.target)
        assertEquals(listOf("luna", "ščip"), c.words)
        assertEquals("stara-pratika", c.reading)
        assertEquals("moon", c.mark)
        // at A1, an A1 line: the full moon's or one of any phase, another on another day
        val a1 = setOf("Ob ščipu je noč svetla.", "Od mlaja do mlaja mine en mesec.")
        val days = (0 until 4).map { card(SkyTap.Moon, "2026-09-27T18:00".replace("27", (27 + it % 2).toString()), "A1").lore?.target }
        assertTrue(days.toString(), days.all { it in a1 } && days.toSet().size == 2)
    }

    @Test fun `a waxing crescent, and a waning one, say what the moon does`() {
        val waxing = card(SkyTap.Moon, "2026-09-14T18:00")
        assertEquals("Rastoči srp", waxing.lines[0].target)
        assertEquals(Said("Luna narašča.", "The moon is waxing."), waxing.lines[1])
        val waning = card(SkyTap.Moon, "2026-10-07T04:00")
        assertEquals("Pojemajoči srp", waning.lines[0].target)
        assertEquals(Said("Luna pojema.", "The moon is waning."), waning.lines[1])
        // the lore of any phase when none is for this one
        assertEquals("Od mlaja do mlaja mine en mesec.", waning.lore?.target)
    }

    @Test fun `a star of Orion opens Orion's card, with its folk name and where it stands`() {
        val betelgeuse = Stars.named("Betelgeuse")!!
        val c = card(SkyTap.Star(betelgeuse.hr, "ori"), "2027-01-15T21:00")
        assertEquals(Said("Orion", "Orion"), c.title)
        assertEquals(listOf(Said("Kosci", "The Mowers")), c.folk)
        assertTrue(c.facts.toString(), c.facts.any { it.contains("Zdaj") && it.contains("na jugu") && it.contains("in the south") })
        assertTrue(c.facts.toString(), c.facts.any { it.contains("Betelgeuse") && it.contains("Rigel") })
        assertEquals("ori", c.mark)
        // the same from a tap on one of its lines
        assertEquals(c.title, card(SkyTap.Figure("ori"), "2027-01-15T21:00").title)
    }

    @Test fun `the Pole Star has a card of its own, in its figure`() {
        val c = card(SkyTap.Star(Stars.named("Polaris")!!.hr, "umi"), "2026-09-27T20:00")
        assertEquals("Polarna zvezda", c.title.target)
        assertEquals("Severnica", c.folk.single().target)
        assertTrue(c.facts.toString(), c.facts.any { it.contains("na severu") })
        assertTrue(c.facts.toString(), c.facts.any { it.endsWith("Ozvezdje: Mali medved · Constellation: Mali medved") })
        assertEquals("umi", c.mark)
    }

    @Test fun `Venus is the evening star after sunset and the morning star before dawn`() {
        val evening = card(SkyTap.Planet("venus"), "2026-08-15T18:30")
        assertEquals("Večernica", evening.folk.first().target)
        assertTrue(evening.facts.any { it.contains("Planet sveti mirno") })
        val morning = card(SkyTap.Planet("venus"), "2026-12-01T05:00")
        assertEquals("Danica", morning.folk.first().target)
    }

    @Test fun `a shooting star in the Perseids asks for a wish and names the shower`() {
        val c = card(SkyTap.Meteor(1L, "perseids"), "2026-08-12T22:00")
        assertEquals("🌠", c.emoji)
        assertEquals(Said("Utrinek! Zaželi si nekaj.", "A shooting star! Make a wish."), c.lines.first())
        assertTrue(c.lines.any { it.target.startsWith("Nocoj padajo Perzeidi") })
        assertTrue(c.folk.any { it.target == "Lovrenčeve solze" })
        assertEquals(listOf("utrinek", "zaželeti si", "želja"), c.words)
        // the moon's card mentions the shower on its nights, not on others
        assertTrue(card(SkyTap.Moon, "2026-08-12T22:00").lines.any { it.target.startsWith("Nocoj padajo Perzeidi") })
        assertFalse(card(SkyTap.Moon, "2026-10-01T22:00").lines.any { it.target.startsWith("Nocoj") })
        // a sporadic one on a quiet night: the wish, no shower
        val quiet = card(SkyTap.Meteor(2L, null), "2026-10-01T22:00")
        assertTrue(quiet.folk.isEmpty() && quiet.lines.size == 1)
    }

    @Test fun `the Milky Way's card, and a lone star's`() {
        val mw = card(SkyTap.MilkyWay, "2026-10-10T20:00")
        assertEquals("Rimska cesta", mw.title.target)
        assertEquals("milky-way", mw.mark)
        // a named star of no figure: its name alone
        val fomalhaut = Stars.named("Fomalhaut")!!
        assertEquals("Fomalhaut", card(SkyTap.Star(fomalhaut.hr, null), "2026-10-10T20:00").title.target)
        // a star of no figure and no name: no card
        val plain = Stars.all.first { it.proper == null && Stars.figureOf(Stars.indexOf(it.hr)) == null }
        assertNull(SkyCards.of(SkyTap.Star(plain.hr, null), at("2026-10-10T20:00"), sky, "A2", zone))
    }

    @Test fun `without a culture's sky the app's own names`() {
        val moon = card(SkyTap.Moon, "2026-09-14T18:00", s = null)
        assertEquals(Said("Luna", "The Moon"), moon.title)
        assertEquals(Said("Rastoči srp", "Waxing crescent"), moon.lines[0])
        assertEquals(Said("Luna narašča.", "The moon is waxing."), moon.lines[1])
        assertNull(moon.lore)
        assertEquals("Orion", card(SkyTap.Figure("ori"), "2027-01-15T21:00", s = null).title.target)
        assertEquals(Said("Venera", "Venus"), card(SkyTap.Planet("venus"), "2026-08-15T18:30", s = null).title)
        assertEquals(Said("Rimska cesta", "The Milky Way"), card(SkyTap.MilkyWay, "2026-08-15T21:30", s = null).title)
        assertEquals(Said("Utrinek! Zaželi si nekaj.", "A shooting star! Make a wish."), card(SkyTap.Meteor(1L, null), "2026-08-15T21:30", s = null).lines.first())
        // in another pair, the other languages: it · sl
        L10n.pair = LangPair(Lang.IT, Lang.SL)
        assertEquals(Said("La Luna", "Luna"), card(SkyTap.Moon, "2026-09-14T18:00", s = null).title)
    }

    @Test fun `lore at the learner's level, the nearest easier one, else the easiest`() {
        fun l(level: String) = SkyLore(level, Text(mapOf("sl" to level)))
        val all = listOf(l("A1"), l("A2"), l("A2"), l("B1"))
        assertEquals("A1", SkyCards.pick(all, "A1", 0)!!.level)
        assertEquals("A2", SkyCards.pick(all, "A2", 0)!!.level)
        assertEquals("B1", SkyCards.pick(all, "B2", 0)!!.level)
        assertEquals("A2", SkyCards.pick(listOf(l("A2"), l("B1")), "A1", 0)!!.level)
        // another of the same level another day
        assertTrue(SkyCards.pick(all, "A2", 0) !== SkyCards.pick(all, "A2", 1))
        assertNull(SkyCards.pick(emptyList(), "A2", 0))
    }

    @Test fun `the check of a sky file finds what is wrong`() {
        assertEquals(emptyList<String>(), Cultures.checkSky("primorska", sky))
        val bad = json.decodeFromString(SkyFile.serializer(), FIXTURE
            .replace("\"id\": \"ori\"", "\"id\": \"unicorn\"")
            .replace("\"waxing\": { \"sl\": \"Luna narašča.\", ", "\"waxing\": { ")
            .replace("\"phase\": \"full\"", "\"phase\": \"blue\"")
            .replace("\"level\": \"A1\", \"text\": { \"sl\": \"Tri", "\"level\": \"C3\", \"text\": { \"sl\": \"Tri")
            .replace("stara-pratika", "almanah")
            .replace("\"id\": \"perseids\"", "\"id\": \"draconids\""))
        val problems = Cultures.checkSky("primorska", bad)
        for (want in listOf("things[0].id", "moon.waxing", "moon.lore[1].phase", "things[0].lore[0].level", "moon.reading", "meteors.showers[0].id")) {
            assertTrue("$want in $problems", problems.any { it.contains(want) })
        }
        // a sky in another language than the pack's
        assertTrue(Cultures.checkSky("friuli", sky).any { it.contains("language") })
        assertNotNull(SkyCards.thingIds.firstOrNull { it == "kaus-australis" })
    }

    private companion object {
        val FIXTURE = """
            {
              "schema": "lani.sky/v0",
              "language": "sl",
              "where": { "lat": 45.99, "lon": 13.75 },
              "moon": {
                "name": { "sl": "Luna", "en": "The Moon" },
                "phases": {
                  "new": { "sl": "Mlaj", "en": "New moon" },
                  "waxing_crescent": { "sl": "Rastoči srp", "en": "Waxing crescent" },
                  "first_quarter": { "sl": "Prvi krajec", "en": "First quarter" },
                  "waxing_gibbous": { "sl": "Rastoča luna", "en": "Waxing gibbous" },
                  "full": { "sl": "Ščip – polna luna", "en": "Full moon" },
                  "waning_gibbous": { "sl": "Pojemajoča luna", "en": "Waning gibbous" },
                  "last_quarter": { "sl": "Zadnji krajec", "en": "Last quarter" },
                  "waning_crescent": { "sl": "Pojemajoči srp", "en": "Waning crescent" }
                },
                "waxing": { "sl": "Luna narašča.", "en": "The moon is waxing." },
                "waning": { "sl": "Luna pojema.", "en": "The moon is waning." },
                "lore": [
                  { "level": "A1", "text": { "sl": "Od mlaja do mlaja mine en mesec.", "en": "From new moon to new moon, a month goes by." } },
                  { "level": "A1", "phase": "full", "text": { "sl": "Ob ščipu je noč svetla.", "en": "At the full moon the night is bright." } },
                  { "level": "A2", "phase": "full", "text": { "sl": "Ščip vzide, ko sonce zaide.", "en": "The full moon rises when the sun sets." } }
                ],
                "reading": "stara-pratika",
                "words": ["luna", "ščip"]
              },
              "things": [
                { "id": "ori", "name": { "sl": "Orion", "en": "Orion" }, "folk": { "sl": "Kosci", "en": "The Mowers" },
                  "lore": [{ "level": "A1", "text": { "sl": "Tri zvezde v sredini so Kosci.", "en": "The three stars in the middle are the Mowers." } }], "words": ["Kosci"] },
                { "id": "umi", "name": { "sl": "Mali medved" }, "folk": { "sl": "Mali voz", "en": "The Little Dipper" } },
                { "id": "polaris", "name": { "sl": "Polarna zvezda", "en": "The Pole Star" }, "folk": { "sl": "Severnica", "en": "The North Star" },
                  "lore": [{ "level": "A1", "text": { "sl": "Severnica je vedno na severu.", "en": "The Pole Star is always in the north." } }] },
                { "id": "venus", "name": { "sl": "Venera", "en": "Venus" }, "morning": { "sl": "Danica", "en": "The morning star" }, "evening": { "sl": "Večernica", "en": "The evening star" } },
                { "id": "milky-way", "name": { "sl": "Rimska cesta", "en": "The Milky Way" } }
              ],
              "meteors": {
                "name": { "sl": "Utrinek", "en": "A shooting star" },
                "wish": { "sl": "Utrinek! Zaželi si nekaj.", "en": "A shooting star! Make a wish." },
                "words": ["utrinek", "zaželeti si", "želja"],
                "showers": [
                  { "id": "perseids", "name": { "sl": "Perzeidi", "en": "The Perseids" }, "folk": { "sl": "Lovrenčeve solze", "en": "The Tears of St Lawrence" },
                    "tonight": { "sl": "Nocoj padajo Perzeidi.", "en": "Tonight the Perseids are falling." } }
                ]
              }
            }
        """.trimIndent()
    }
}
