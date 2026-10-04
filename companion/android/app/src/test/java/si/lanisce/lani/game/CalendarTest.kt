package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.villagers.Resident
import java.time.LocalDate

/** "Koledar · The calendar" (GAME.md): the Slovene days by the phone's date, their countdown, and celebrating them. */
class CalendarTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun f(id: String) = Calendar.byId(id)!!
    private val people = listOf("marko", "micka", "janez", "luka", "zala")
    private fun village(age: Age = Age.VAS, who: List<String> = people) = GameState(
        age = age, residents = who.map { Resident(it, "2026-01-01") }, villagers = who.size,
        resources = Res.entries.associateWith { 100 }, morale = 60,
    )

    @Test fun `Easter and the days that follow it`() {
        val known = mapOf(2000 to "2000-04-23", 2019 to "2019-04-21", 2024 to "2024-03-31", 2025 to "2025-04-20", 2026 to "2026-04-05", 2027 to "2027-03-28", 2038 to "2038-04-25")
        for ((y, e) in known) assertEquals(d(e), Calendar.easter(y))
        // pust (Shrove Tuesday) is 47 days before Easter, always a Tuesday
        assertEquals(d("2026-02-17"), f("pust").rule.date(2026))
        assertEquals(d("2027-02-09"), f("pust").rule.date(2027))
        assertEquals(d("2025-03-04"), f("pust").rule.date(2025))
        for (y in 2020..2040) assertEquals(java.time.DayOfWeek.TUESDAY, f("pust").rule.date(y).dayOfWeek)
        assertEquals(d("2026-04-05"), f("velika_noc").rule.date(2026))
    }

    @Test fun `the fixed days, and the grape harvest on September's last Saturday`() {
        assertEquals(d("2026-11-11"), f("martinovo").rule.date(2026))
        assertEquals(d("2026-12-06"), f("miklavz").rule.date(2026))
        assertEquals(d("2026-12-25"), f("bozic").rule.date(2026))
        assertEquals(d("2027-01-01"), f("novo_leto").rule.date(2027))
        assertEquals(d("2027-02-08"), f("presernov_dan").rule.date(2027))
        assertEquals(d("2027-05-01"), f("prvi_maj").rule.date(2027))
        assertEquals(d("2027-05-20"), f("dan_cebel").rule.date(2027))
        assertEquals(d("2027-06-23"), f("kres").rule.date(2027))
        assertEquals(d("2026-09-26"), f("trgatev").rule.date(2026))
        assertEquals(d("2027-09-25"), f("trgatev").rule.date(2027))
    }

    @Test fun `a countdown a few days ahead`() {
        val (m, day) = Calendar.upcoming(d("2026-11-08"))!!
        assertEquals("martinovo", m.id)
        assertEquals(d("2026-11-11"), day)
        assertEquals("Čez 3 dni: Martinovo 🍷 · St Martin's Day in 3 days", Calendar.countdown(m, 3))
        assertEquals("Čez 2 dneva: Martinovo 🍷 · St Martin's Day in 2 days", Calendar.countdown(m, 2))
        assertEquals("Čez 5 dni: Martinovo 🍷 · St Martin's Day in 5 days", Calendar.countdown(m, 5))
        assertEquals("Jutri: Martinovo 🍷 · Tomorrow: St Martin's Day", Calendar.countdown(m, 1))
        assertEquals("Danes: Martinovo 🍷 · Today: St Martin's Day", Calendar.countdown(m, 0))
        // six days ahead is too early, and the day itself isn't "upcoming"
        assertNull(Calendar.upcoming(d("2026-11-05")))
        assertNull(Calendar.upcoming(d("2026-11-11")))
        assertEquals("miklavz", Calendar.upcoming(d("2026-12-02"))?.first?.id)
        // across the new year
        assertEquals("novo_leto" to d("2027-01-01"), Calendar.upcoming(d("2026-12-31"))?.let { it.first.id to it.second })
    }

    @Test fun `on the day and two days after, kindly, then gone`() {
        val s = village()
        assertTrue(Calendar.on(s, d("2026-11-10")).isEmpty())
        assertEquals(0, Calendar.on(s, d("2026-11-11")).single().late)
        assertEquals(2, Calendar.on(s, d("2026-11-13")).single().late)
        assertTrue(Calendar.on(s, d("2026-11-14")).isEmpty())
        assertEquals("Martinovo še traja 🍷 · St Martin's Day isn't over yet", Calendar.todayText(Calendar.on(s, d("2026-11-12")).single()))
        // from Tabor: the campfire has nobody to celebrate with yet
        assertTrue(Calendar.open(village(Age.OGENJ), d("2026-11-11")).isEmpty())
        assertEquals(1, Calendar.open(village(Age.TABOR), d("2026-11-11")).size)
    }

    @Test fun `celebrating, spirits, the good, the pay, the people closer, once a year`() {
        val s = village()
        val day = d("2026-11-11")
        val (n, r) = Calendar.celebrate(s, "martinovo", 5, 6, day, noon(day))
        assertTrue(r.won)
        assertEquals(70, n.morale)
        assertEquals(1, Chest.count(n, "mlado_vino"))
        assertEquals("mlado_vino", r.thanks?.id)
        assertEquals("2026-11-11", n.festivals["martinovo"])
        // 20 🌾 and 10 of the scarcest besides, ×1.9 at Vas
        assertEquals(mapOf(Res.FOOD to 38, Res.WOOD to 19), Calendar.reward(s))
        assertEquals(Calendar.reward(s), r.rewards)
        // Marko leads it (+5 ♥ in all), everyone who lives here +1
        assertEquals(Calendar.LEADER_POINTS, n.bonds["marko"]?.points)
        assertEquals(Calendar.ALL_POINTS, n.bonds["zala"]?.points)
        assertTrue(n.log.any { it.text.startsWith("Mošt je postal vino") })
        // done: not again this year, not the day after either
        assertTrue(Calendar.open(n, day).isEmpty())
        assertTrue(Calendar.open(n, day.plusDays(1)).isEmpty())
        assertNull(GameEngine.festivalChallenge(n, "martinovo", day))
        assertFalse(Calendar.celebrate(n, "martinovo", 6, 6, day, noon(day)).second.won)
        // next year it's on again
        assertEquals(1, Calendar.open(n, d("2027-11-11")).size)
    }

    @Test fun `a day late still counts, with less of a lift, a try that fails can be tried again`() {
        val late = d("2026-11-12")
        assertEquals(65, Calendar.celebrate(village(), "martinovo", 3, 6, late, noon(late)).first.morale)
        val (tried, r) = Calendar.celebrate(village(), "martinovo", 2, 6, late, noon(late))
        assertFalse(r.won)
        assertEquals(63, tried.morale)
        assertNull(tried.festivals["martinovo"])
        assertEquals(1, Calendar.open(tried, late).size)
    }

    @Test fun `the leader is the first of its people who is here`() {
        val day = d("2026-11-11")
        assertEquals("marko", Calendar.leader(village(), f("martinovo"), day))
        assertEquals("janez", Calendar.leader(village(who = listOf("janez", "micka")), f("martinovo"), day))
        assertNull(Calendar.leader(village(who = listOf("luka")), f("martinovo"), day))
        // with nobody leading the whole village celebrates all the same
        assertTrue(Calendar.celebrate(village(who = listOf("luka")), "martinovo", 6, 6, day, noon(day)).second.won)
    }

    @Test fun `every festival, its name, its words, its good, a run of choices`() {
        assertTrue(Calendar.festivals.size >= 10)
        assertEquals(Calendar.festivals.size, Calendar.festivals.map { it.id }.toSet().size)
        for (fest in Calendar.festivals) {
            for (t in listOf(fest.name, fest.ask, fest.about, fest.line)) assertTrue("${fest.id}: $t", " · " in t)
            assertTrue(fest.id, fest.words.size >= Calendar.EXERCISES + 2) // a run takes 6, and the others are the wrong options
            assertTrue(fest.id, fest.words.all { it.word.isNotBlank() && it.meaning.isNotBlank() })
            assertEquals(fest.id, fest.words.size, fest.words.map { it.word }.toSet().size)
            assertEquals(fest.id, fest.words.size, fest.words.map { it.en }.toSet().size)
            assertTrue(fest.id, fest.leaders.all { Catalog.tools.containsKey(it) })
            fest.good?.let { assertNotNull("${fest.id}: $it", Catalog.goods[it]) }
            val ex = Calendar.exercises(fest, 7, fest.rule.date(2026))
            assertEquals(Calendar.EXERCISES, ex.size)
            for (e in ex) {
                val c = e as Exercise.Choice
                assertEquals(4, c.options.size)
                assertEquals(c.options.size, c.options.toSet().size)
                val sl = Regex("»(.+?)«").find(c.prompt)?.groupValues?.get(1)
                if (sl != null) assertEquals(fest.words.single { it.sl == sl }.en, c.options[c.answer])
                else {
                    val en = Regex("“(.+?)”").find(c.prompt)!!.groupValues[1]
                    assertEquals(fest.words.single { it.en == en }.sl, c.options[c.answer])
                }
            }
        }
        // the same run all day
        assertEquals(Calendar.exercises(f("pust"), 7, d("2026-02-17")), Calendar.exercises(f("pust"), 7, d("2026-02-17")))
        val c = GameEngine.festivalChallenge(village(), "trgatev", d("2026-09-26"))!!
        assertEquals(3, c.passMark)
        assertTrue(c.intro.startsWith("Trgatev je!"))
    }

    /** The words the festivals had before their packs: each festival keeps them. */
    private val before = mapOf(
        "novo_leto" to listOf("novo leto", "polnoč", "ognjemet", "želja", "zdravica", "Srečno!"),
        "presernov_dan" to listOf("pesnik", "pesem", "knjiga", "brati", "kultura", "himna"),
        "pust" to listOf("maska", "kurent", "krof", "zvonec", "ples", "zima"),
        "velika_noc" to listOf("pisanica", "jajce", "košara", "šunka", "hren", "pomlad"),
        "prvi_maj" to listOf("mlaj", "kres", "godba", "delo", "praznik", "smreka"),
        "dan_cebel" to listOf("čebela", "med", "panj", "cvet", "čebelar", "satje"),
        "kres" to listOf("kres", "noč", "kresnica", "poletje", "zvezda", "ogenj"),
        "trgatev" to listOf("grozdje", "trta", "vinograd", "škarje", "košara", "mošt"),
        "martinovo" to listOf("mlado vino", "gos", "mlinci", "sod", "klet", "Na zdravje!"),
        "miklavz" to listOf("Miklavž", "angel", "parkelj", "darilo", "okno", "priden"),
        "bozic" to listOf("božič", "jaslice", "sveča", "sneg", "smrečica", "Vesel božič!"),
    )

    @Test fun `every festival reads its words from its pack, the very file the bridge serves`() {
        assertEquals(before.keys, Calendar.festivals.map { it.id }.toSet())
        for (fest in Calendar.festivals) {
            val pack = FestivalPacks.pack(fest.pack)
            assertNotNull("${fest.pack} is bundled", pack)
            assertEquals(fest.id, pack!!.festival)
            assertEquals("praznik-" + fest.id.replace('_', '-'), pack.id)
            assertEquals(fest.name, pack.title)
            assertEquals(fest.emoji, pack.emoji)
            assertEquals(pack.words, fest.words)
            // the bundled copy is companion/packs' (the build copies it; the bridge serves it from there)
            val file = java.io.File("../../packs/${fest.pack}.json")
            assertTrue("$file", file.exists())
            assertEquals(si.lanisce.lani.data.parsePack(file.readText()).words, fest.words)
            // it keeps the words the festival had, and every word has an example and, as a noun with a plural, a gender
            assertTrue("${fest.id}: ${before.getValue(fest.id) - fest.words.map { it.sl }.toSet()}", fest.words.map { it.sl }.containsAll(before.getValue(fest.id)))
            for (w in fest.words) {
                assertTrue("${fest.id}/${w.id}", !w.exampleSl.isNullOrBlank() && !w.exampleEn.isNullOrBlank())
                if (w.plural != null) assertNotNull("${fest.id}/${w.id}", w.gender)
            }
            // the leader gives the pack, like a villager gives theirs
            assertTrue(fest.id, pack.giver != null)
        }
        // a festival without its pack (an app built without it) has no words, and nothing to play
        fun t(sl: String, en: String) = si.lanisce.lani.game.culture.Text(mapOf("sl" to sl, "en" to en))
        assertTrue(Festival("x", "?", t("X", "X"), DateRule.Fixed(1, 2), emptyList(), t("a", "a"), t("b", "b"), t("c", "c")).words.isEmpty())
    }

    @Test fun `the festival's run carries its pack and the word under each exercise, on its day and the grace days`() {
        val s = village()
        for (day in listOf(d("2026-09-26"), d("2026-09-28"))) {
            val c = GameEngine.festivalChallenge(s, "trgatev", day)!!
            assertEquals("praznik-trgatev", c.pack)
            assertEquals(c.exercises.size, c.packWords.size)
            assertEquals(c.packWords.size, c.packWords.toSet().size)
            val words = f("trgatev").words.associateBy { it.id }
            for ((e, id) in c.exercises.zip(c.packWords)) {
                val w = words.getValue(id)
                val choice = e as Exercise.Choice
                assertTrue("${choice.prompt} is about ${w.sl}", "»${w.sl}«" in choice.prompt || "“${w.en}”" in choice.prompt)
                assertTrue(choice.explain!!.startsWith("${w.sl} · ${w.en}"))
            }
            // the same run as the calendar's, all day
            assertEquals(Calendar.run(f("trgatev"), s.seed, d("2026-09-26")).map { it.first.id }, c.packWords)
        }
        // other runs record no pack words
        assertNull(GameEngine.surpriseChallenge(s, Fixtures.pool(), d("2026-09-26"))?.pack)
    }

    @Test fun `the countdown text of a festival's pack, and the festival to learn now`() {
        val m = f("martinovo")
        assertEquals("11. novembra · 11 November", Calendar.whenText(m, d("2026-10-01")))
        assertEquals("Čez 3 dni · In 3 days", Calendar.whenText(m, d("2026-11-08")))
        assertEquals("Čez 2 dneva · In 2 days", Calendar.whenText(m, d("2026-11-09")))
        assertEquals("Jutri · Tomorrow", Calendar.whenText(m, d("2026-11-10")))
        assertEquals("Danes · Today", Calendar.whenText(m, d("2026-11-11")))
        assertEquals("Še traja · Not over yet", Calendar.whenText(m, d("2026-11-13")))
        assertEquals("11. novembra · 11 November", Calendar.whenText(m, d("2026-11-14")))
        assertEquals(d("2027-11-11"), Calendar.next(m, d("2026-11-14")))
        assertEquals("1. januarja · 1 January", Calendar.dateText(d("2027-01-01")))
        // on the day (not celebrated yet), else the next within the countdown, else none; nothing at the campfire
        assertEquals("martinovo", Calendar.soon(village(), d("2026-11-12"))?.id)
        assertEquals("martinovo", Calendar.soon(village(), d("2026-11-08"))?.id)
        assertNull(Calendar.soon(village(), d("2026-10-20")))
        assertNull(Calendar.soon(village(Age.OGENJ), d("2026-11-11")))
        val done = Calendar.celebrate(village(), "martinovo", 6, 6, d("2026-11-11"), noon(d("2026-11-11"))).first
        assertNull(Calendar.soon(done, d("2026-11-12")))
    }

    @Test fun `the tick tells the day in the chronicle`() {
        val s = village().copy(lastTick = "2026-11-10", foundedOn = "2026-01-01")
        val day = d("2026-11-11")
        val n = GameEngine.tick(s, day, noon(day), Fixtures.pool()).state
        assertTrue(n.log.any { it.text == "Danes: Martinovo 🍷 · Today: St Martin's Day" })
    }
}
