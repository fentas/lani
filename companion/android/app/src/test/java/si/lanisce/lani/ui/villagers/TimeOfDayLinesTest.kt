package si.lanisce.lani.ui.villagers

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.game.Surprises
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.GiftLines
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.game.villagers.VillagerLines
import si.lanisce.lani.game.villagers.at
import si.lanisce.lani.game.villagers.parseVillagers
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.stage.Cast
import si.lanisce.lani.ui.stage.Director
import si.lanisce.lani.ui.stage.Intros
import si.lanisce.lani.ui.stage.Lines
import si.lanisce.lani.ui.stage.Run
import si.lanisce.lani.ui.stage.StagePerson
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What the villagers say fits the time of day on the phone (a line's `when`, VILLAGERS.md): at nine in the morning nobody
 * says good night or good evening, in any culture pack, on the stage, on their card, in a talk or over a gift; at ten in
 * the evening a goodnight can come, and no line nor its translation wishes a good day; a slot without a line for the time
 * says a plain one for it; old lines without `when` fit any time.
 */
class TimeOfDayLinesTest {
    private val day = LocalDate.of(2026, 9, 27)
    private val pair = L10n.pair
    private val nine = TimeOfDay.now(LocalDateTime.of(2026, 9, 27, 9, 0))
    private val ten = TimeOfDay.now(LocalDateTime.of(2026, 9, 27, 22, 0))

    @After fun back() {
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = pair
    }

    /** Each culture pack, the language its village speaks and the base its learner reads. */
    private val cultures = listOf("primorska" to LangPair(Lang.SL, Lang.EN), "friuli" to LangPair(Lang.IT, Lang.SL), "kaernten" to LangPair(Lang.DE, Lang.SL), "lakeland" to LangPair(Lang.EN, Lang.SL))

    private fun cast(culture: String): List<Villager> {
        val dir = listOf(File("../../cultures/$culture/villagers"), File("../cultures/$culture/villagers")).first { it.isDirectory }
        return parseVillagers(dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.joinToString(",", "[", "]") { it.readText() })
    }

    /** Good nights and good evenings, in every language the packs have. */
    private val night = Regex("Lahko noč|Dober večer|Večer\\.|Buonanotte|Buonasera|Buona serata|Gute Nacht|Guten Abend|schönen Abend|Good night|Good evening|nice evening|time for bed|čas za spat|ora di dormire|Zeit zum Schlafen")

    /** Good days and good mornings, as the village says them. */
    private val daytime = Regex("Dober dan|Dobro jutro|^Dan\\.|Lep dan|Buongiorno|Buona giornata|Guten Tag|Guten Morgen|schönen Tag|Good morning|Good afternoon|Good day|nice day|good day")

    private fun VillagerLines.all(): List<VillagerLine> = greet + thanks + remember + idle + cheer + comfort + listen + bye + gift.liked + gift.ordinary + gift.rare

    /** Everyone of [culture] who talks: the cast, the strangers at the road, and the people who move in (grown-ups and children). */
    private fun everyone(culture: String): List<Villager> {
        val people = Cultures.current.people.lines
        return cast(culture) + Surprises.strangers.values +
            Villager(id = "n-adult", name = "Ana", art = "woman", lines = people.adult) + Villager(id = "n-child", name = "Tine", art = "child1", register = "ti", lines = people.child)
    }

    private fun person(v: Villager, level: Int) = Cast.of(v, null).copy(level = level)

    @Test fun `the phone's clock, nine is the morning, ten in the evening the night, dawn the morning`() {
        assertEquals(TimeOfDay.MORNING, nine)
        assertEquals(TimeOfDay.NIGHT, ten)
        assertEquals(TimeOfDay.EVENING, TimeOfDay.now(LocalDateTime.of(2026, 9, 27, 19, 30)))
        assertEquals(TimeOfDay.MORNING, TimeOfDay.now(LocalDateTime.of(2026, 12, 21, 7, 30))) // dawn in December
        assertTrue(VillagerLine("Lahko noč!", "Good night!", times = setOf(TimeOfDay.EVENING, TimeOfDay.NIGHT)).fits(TimeOfDay.NIGHT))
        assertFalse(VillagerLine("Dobro jutro!", "Good morning!", times = setOf(TimeOfDay.MORNING)).fits(TimeOfDay.AFTERNOON))
        assertTrue(VillagerLine("Dobro jutro!", "Good morning!", times = setOf(TimeOfDay.MORNING)).fits(TimeOfDay.DAWN))
    }

    @Test fun `at nine nobody says good night or good evening, in any culture`() {
        for ((culture, lang) in cultures) {
            Cultures.use(culture)
            L10n.pair = lang
            for (v in everyone(culture)) {
                // no line for the morning says it, in any of its languages
                for (l in v.lines.all().at(nine)) for (s in l.by.values) assertFalse("$culture ${v.id}: «$s» at nine", night.containsMatchIn(s))
                for (level in 0..4) {
                    val hello = VillagerLogic.greet(v, level, day, nine)
                    assertFalse("$culture ${v.id} greets at $level: «${hello.sl}»", night.containsMatchIn(hello.sl) || night.containsMatchIn(hello.en))
                    VillagerLogic.idle(v, level, day, nine)?.let { assertFalse("$culture ${v.id} idles: «${it.sl}»", night.containsMatchIn(it.sl)) }
                    for (r in GiftReaction.entries) assertFalse("$culture ${v.id} thanks for a gift", night.containsMatchIn(GiftTalk.reply(v, r, level, day, nine).sl))
                    assertFalse("$culture ${v.id}: the request", night.containsMatchIn(Intros.request(person(v, level), Run.Review, nine).sl))
                    // the end of a run: the tally and their goodbye, whatever the dice say
                    for (seed in 1L..24L) {
                        val end = Director(person(v, level), seed, day, greet = true) { nine }.end(4, 5, 5).line!!.target
                        assertFalse("$culture ${v.id} at $level, seed $seed: «$end»", night.containsMatchIn(end))
                        val first = Director(person(v, level), seed, day, greet = true) { nine }.prompt(0, Exercise.Cloze("Jaz ___ Jan.", listOf("sem")), null)!!.line!!.target
                        assertFalse("$culture ${v.id} opens: «$first»", night.containsMatchIn(first))
                    }
                }
            }
        }
    }

    @Test fun `late at night nobody wishes a good day, and a goodnight can come`() {
        for ((culture, lang) in cultures) {
            Cultures.use(culture)
            L10n.pair = lang
            for (v in everyone(culture)) {
                for (l in v.lines.all().at(ten)) assertFalse("$culture ${v.id}: «${l.target}» at ten", daytime.containsMatchIn(l.target))
                // nor does what it means: "Grüß Gott!" at night is "Hello!", not "Good day!"
                for (l in v.lines.all().at(ten)) for (s in l.by.values) assertFalse("$culture ${v.id}: «${l.target}» means «$s» at ten", daytime.containsMatchIn(s))
                for (level in 0..4) for (seed in 1L..12L) {
                    val end = Director(person(v, level), seed, day) { ten }.end(4, 5, 5).line!!.target
                    assertFalse("$culture ${v.id}: «$end»", daytime.containsMatchIn(end))
                }
            }
        }
        // Jan's report: Babica Micka at the end of a morning review said "Lahko noč, fant."; now her goodnight is the evening's
        Cultures.use("primorska")
        L10n.pair = LangPair.DEFAULT
        val micka = person(cast("primorska").first { it.id == "micka" }, 2)
        fun ends(time: TimeOfDay) = (1L..80L).map { Director(micka, it, day) { time }.end(4, 5, 5).line!!.target }.toSet()
        val morning = ends(nine)
        assertTrue(morning.toString(), morning.all { it.startsWith("4 od 5 pravilno.") } && morning.none { "Lahko noč" in it } && morning.any { it.endsWith("Pa lep dan, fant. In jej!") })
        val night = ends(ten)
        assertTrue(night.toString(), night.any { it.endsWith("Lahko noč, fant. In jej!") } && night.none { "lep dan" in it.lowercase() })
    }

    @Test fun `the end of a run says a goodnight in the evening and at night, a good day by day`() {
        // Jan's report: Babica Micka at the end of a morning review said "Lahko noč, fant."
        L10n.pair = LangPair.DEFAULT
        val micka = StagePerson(
            id = "micka", name = "Babica Micka", emoji = "👵", art = "grandma", voice = Clips.FEMALE, role = "Babica · Grandmother", register = "vi", level = 2,
            lines = VillagerLines(
                bye = listOf(
                    VillagerLine("Adijo, Jan. Pridi spet!", "Bye, Jan. Come again!"),
                    VillagerLine("Lahko noč, fant. In jej!", "Good night, lad. And eat!", times = setOf(TimeOfDay.EVENING, TimeOfDay.NIGHT)),
                    VillagerLine("Pa lep dan, fant. In jej!", "Have a nice day, lad. And eat!", times = setOf(TimeOfDay.MORNING, TimeOfDay.AFTERNOON)),
                ),
            ),
        )
        fun ends(time: TimeOfDay) = (1L..80L).map { Director(micka, it, day) { time }.end(4, 5, 5).line!!.target }.toSet()
        val morning = ends(nine)
        assertTrue(morning.toString(), morning.all { it.startsWith("4 od 5 pravilno.") } && morning.none { "Lahko noč" in it } && morning.any { it.endsWith("Pa lep dan, fant. In jej!") })
        val night = ends(ten)
        assertTrue(night.toString(), night.any { it.endsWith("Lahko noč, fant. In jej!") } && night.none { "lep dan" in it.lowercase() })
    }

    @Test fun `a slot with no line for the time says a plain one for it, never a wrong-time one`() {
        L10n.pair = LangPair.DEFAULT
        val day2 = setOf(TimeOfDay.MORNING, TimeOfDay.AFTERNOON)
        val evening = setOf(TimeOfDay.EVENING, TimeOfDay.NIGHT)
        val v = Villager(
            id = "x", name = "Teta Ana", art = "aunt", register = "ti",
            lines = VillagerLines(
                greet = listOf(VillagerLine("Dober dan, Jan!", "Good day, Jan!", times = day2), VillagerLine("Dober dan!", "Good day!", times = day2)),
                idle = listOf(VillagerLine("Sonce sije.", "The sun is shining.", times = day2)),
                bye = listOf(VillagerLine("Lahko noč!", "Good night!", times = evening), VillagerLine("Lahko noč, Jan!", "Good night, Jan!", times = evening)),
            ),
        )
        // the card and a talk: a plain good evening, no idle line at night
        assertEquals("Dober večer!", VillagerLogic.greet(v, 0, day, TimeOfDay.EVENING).sl)
        assertTrue(VillagerLogic.greet(v, 0, day, TimeOfDay.MORNING).sl in setOf("Dober dan, Jan!", "Dober dan!"))
        assertNull(VillagerLogic.idle(v, 0, day, TimeOfDay.NIGHT))
        assertEquals("Sonce sije.", VillagerLogic.idle(v, 0, day, TimeOfDay.AFTERNOON)?.sl)
        assertEquals("Dober večer!", VillagerLogic.localScenario(v, Bond(), day, TimeOfDay.NIGHT).openerSl)
        // the stage: the built-in greeting in the evening, a built-in goodbye for the day in the morning
        val who = person(v, 0)
        for (seed in 1L..30L) {
            val hello = Director(who, seed, day, greet = true) { TimeOfDay.EVENING }.prompt(0, Exercise.Cloze("a ___", listOf("b")), null)!!.line!!.target
            assertTrue(hello, hello in Lines.greet("ti").map { it.target })
            val bye = Director(who, seed, day) { TimeOfDay.MORNING }.end(0, 0, 5).line!!.target
            assertTrue(bye, bye in Lines.bye("ti").at(TimeOfDay.MORNING).map { it.target })
            assertFalse(bye, "noč" in bye || "večer" in bye)
        }
        // an intro in the evening: no "Dober dan", just the ask
        assertEquals(Lines.ask(Run.Review, "ti").target, Intros.request(who, Run.Review, TimeOfDay.EVENING).sl)
        // the stage's own goodbyes: a good day by day, a good evening in the evening, a good night at night
        assertTrue(Lines.bye("vi").at(TimeOfDay.AFTERNOON).any { it.target == "Lep dan še naprej!" })
        assertEquals(listOf("Na svidenje!", "Se vidiva!", "Lep večer še naprej!", "Lahko noč!"), Lines.bye("vi").at(TimeOfDay.EVENING).map { it.target })
        assertEquals(listOf("Na svidenje!", "Se vidiva!", "Lahko noč!"), Lines.bye("vi").at(TimeOfDay.NIGHT).map { it.target })
        // a gift at night with only an evening line: plain thanks
        val party = v.copy(lines = v.lines.copy(gift = GiftLines(liked = listOf(VillagerLine("Nocoj praznujemo!", "Tonight we celebrate!", times = day2 + TimeOfDay.EVENING)))))
        assertEquals("Oh, to mi je pa res všeč! Hvala!", GiftTalk.reply(party, GiftReaction.LIKED, 0, day, TimeOfDay.NIGHT).sl)
        assertEquals("Nocoj praznujemo!", GiftTalk.reply(party, GiftReaction.LIKED, 0, day, TimeOfDay.AFTERNOON).sl)
    }

    @Test fun `old lines without a when fit any time, as they always did`() {
        L10n.pair = LangPair.DEFAULT
        val luka = StagePerson(
            id = "luka", name = "Pastir Luka", emoji = "🐑", art = "shepherd", voice = Clips.MALE, role = "Pastir · Shepherd", register = "ti",
            lines = VillagerLines(greet = listOf(VillagerLine("Dober dan!", "Good day!")), bye = listOf(VillagerLine("Adijo!", "Bye!"), VillagerLine("Čav!", "Bye!"))),
        )
        for (t in VillagerLine.PARTS) {
            assertTrue(VillagerLine("Adijo!", "Bye!").fits(t))
            assertEquals("Dober dan!", VillagerLogic.greet(Villager(id = "luka", name = "Pastir Luka", art = "shepherd", lines = luka.lines), 0, day, t).sl)
            val bye = Director(luka, 3, day) { t }.end(3, 5, 5).line!!.target
            assertTrue(bye, bye.endsWith("Adijo!") || bye.endsWith("Čav!"))
        }
        val old = parseVillagers("""[{"id": "x", "name": "X", "art": "shepherd", "lines": {"greet": [{"sl": "Dober dan!", "en": "Good day!"}]}}]""").single().lines.greet.single()
        assertEquals(emptySet<TimeOfDay>(), old.times)
        assertEquals(VillagerLine("Dober dan!", "Good day!"), old)
    }
}
