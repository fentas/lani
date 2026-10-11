package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.Res
import java.time.LocalDateTime

class HappeningsTest {
    private val babica = ScenePerson("babica", "Babica Micka", "👵", "grandma", "left")
    private val otrok = ScenePerson("otrok", "Otroci", "🧒", "child1", "right")
    private val fire = SceneSpec(
        id = "ob-ognju", title = "Ob ognju", art = "campfire", from = listOf("fire"),
        people = listOf(babica, otrok),
        happenings = listOf(
            Happening("juha", "Babica kuha juho", who = "babica", `when` = listOf(TimeOfDay.EVENING)),
            Happening("igra", "Otroci se igrajo", who = "otrok", `when` = listOf(TimeOfDay.AFTERNOON), chance = 0.5f),
        ),
    )
    private val kitchen = SceneSpec(
        id = "kuhinja", title = "V kuhinji", art = "kitchen", from = listOf("hut", "house"),
        people = listOf(babica.copy(slot = "stove")),
        happenings = listOf(Happening("potica", "Potica", who = "babica")),
    )
    private val camp = GameState(seed = 7, age = Age.TABOR)
    private val evening = LocalDateTime.of(2026, 9, 24, 19, 30)

    @Test fun `a happening for guests - never at home, on a visit it is on like the others`() {
        val guest = fire.copy(
            happenings = listOf(Happening("prvic", "Babica pozdravi gosta", who = "babica", guests = true), Happening("igra", "Otroci", who = "otrok")),
        )
        val byDialog = fire.copy(
            happenings = listOf(Happening("prvic", "Babica pozdravi gosta", who = "babica", dialog = "prvic")),
            dialogs = listOf(Dialog("prvic", emptyList(), guests = true)),
        )
        assertEquals(listOf("igra"), Happenings.active(listOf(guest), camp, evening).map { it.happening.id })
        assertEquals(setOf("prvic", "igra"), Happenings.active(listOf(guest), camp, evening, guest = true).map { it.happening.id }.toSet())
        assertTrue(Happenings.forGuests(byDialog, byDialog.happenings[0]))
        assertTrue(Happenings.active(listOf(byDialog), camp, evening).isEmpty())
        assertEquals(listOf("prvic"), Happenings.active(listOf(byDialog), camp, evening, guest = true).map { it.happening.id })
    }

    @Test fun `time of day windows`() {
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.of(4))
        assertEquals(TimeOfDay.MORNING, TimeOfDay.of(5))
        assertEquals(TimeOfDay.AFTERNOON, TimeOfDay.of(11))
        assertEquals(TimeOfDay.EVENING, TimeOfDay.of(17))
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.of(22))
    }

    @Test fun `dawn is the first hours of the morning, round the month's sunrise`() {
        assertEquals(5..6, TimeOfDay.dawn(6))
        assertEquals(6..8, TimeOfDay.dawn(12))
        for (m in 1..12) {
            val d = TimeOfDay.dawn(m)
            assertTrue("$m: $d in the morning", d.first >= 5 && d.last <= 10 && d.count() in 2..3)
            assertTrue("$m: dawn is never a part of the day of its own", d.all { TimeOfDay.of(it) == TimeOfDay.MORNING })
        }
        assertTrue(TimeOfDay.isDawn(5, 6) && !TimeOfDay.isDawn(8, 6) && TimeOfDay.isDawn(8, 12) && !TimeOfDay.isDawn(5, 12) && !TimeOfDay.isDawn(9, 12))
    }

    @Test fun `a dawn happening is on at dawn only, a morning one all morning`() {
        val hunter = ScenePerson("joze", "Lovec Jože", "🦌", "hunter", "highseat")
        val forest = SceneSpec(
            id = "v-gozdu", title = "V gozdu", art = "forest", from = listOf("forest"), people = listOf(hunter, otrok.copy(slot = "clearing")),
            happenings = listOf(
                Happening("preza", "Jože na preži", who = "joze", `when` = listOf(TimeOfDay.DAWN)),
                Happening("gobe", "Gobe", who = "otrok", `when` = listOf(TimeOfDay.MORNING)),
            ),
        )
        val june = LocalDateTime.of(2026, 6, 20, 5, 30)
        fun on(t: LocalDateTime) = Happenings.active(listOf(forest), camp, t).map { it.happening.id }.toSet()
        assertEquals(setOf("preza", "gobe"), on(june))
        assertEquals(setOf("preza", "gobe"), on(june.withHour(6)))
        assertEquals("after dawn, the morning goes on", setOf("gobe"), on(june.withHour(8)))
        assertEquals(emptySet<String>(), on(june.withHour(4)))
        assertEquals(emptySet<String>(), on(june.withHour(19)))
        // in December the sun rises late: dawn is from 6 to 8, not at 5
        val december = LocalDateTime.of(2026, 12, 20, 5, 30)
        assertEquals(setOf("gobe"), on(december))
        assertEquals(setOf("preza", "gobe"), on(december.withHour(8)))
        // later today: at three in the night of December, the dawn happening is listed for the morning
        val later = Happenings.later(listOf(forest), camp, december.withHour(3))
        assertTrue(later.any { (part, a) -> part == TimeOfDay.MORNING && a.happening.id == "preza" })
        assertTrue("and at 9 it has passed", Happenings.later(listOf(forest), camp, december.withHour(9)).none { it.second.happening.id == "preza" })
        // the file format: "dawn"
        assertEquals(listOf(TimeOfDay.DAWN, TimeOfDay.EVENING), si.lanisce.lani.data.json.decodeFromString(Happening.serializer(), """{"id": "x", "title": "x", "who": "joze", "when": ["dawn", "evening"]}""").`when`)
    }

    @Test fun `a happening and a dialog in their months only - absent all year, a variant its own`() {
        val france = ScenePerson("france", "France", "👨‍🌾", "farmer", "field")
        val luka = ScenePerson("luka", "Luka", "🐑", "shepherd", "kozolec")
        val tine = ScenePerson("tine", "Tine", "🧒", "child1", "path")
        fun lines(id: String) = listOf(DialogLine("france", id, id), DialogLine(choices = listOf(DialogChoice("Ja.", ok = true), DialogChoice("Ne.", why = "…"))), DialogLine("france", "Adijo.", "Bye."))
        val field = SceneSpec(
            id = "na-njivi", title = "Na njivi", art = "field", from = listOf("fire"), people = listOf(france, luka, tine),
            happenings = listOf(
                // the hay: in the afternoons from June to September
                Happening("seno", "Luka spravlja seno", who = "luka", `when` = listOf(TimeOfDay.AFTERNOON), months = listOf(6, 7, 8, 9), dialog = "seno"),
                // the horse all year: one variant only in winter, when the horse stays in the stable
                Happening("konj", "Francetov konj", who = "france", dialogs = listOf("konj-hlev", "konj")),
                // no months: all year
                Happening("strasilo", "Tine in strašilo", who = "tine", dialog = "strasilo"),
            ),
            dialogs = listOf(Dialog("seno", lines("seno")), Dialog("konj", lines("konj")), Dialog("konj-hlev", lines("konj-hlev"), months = listOf(12, 1, 2)), Dialog("strasilo", lines("strasilo"))),
        )
        val july = LocalDateTime.of(2026, 7, 15, 14, 0)
        val january = LocalDateTime.of(2027, 1, 15, 14, 0)
        fun on(t: LocalDateTime, s: GameState = camp) = Happenings.active(listOf(field), s, t).associate { it.happening.id to it.dialog }
        assertEquals(mapOf("seno" to "seno", "konj" to "konj", "strasilo" to "strasilo"), on(july))
        // in January no hay: not on, not later today; the horse's winter variant first (not heard yet)
        assertEquals(mapOf("konj" to "konj-hlev", "strasilo" to "strasilo"), on(january))
        assertTrue(Happenings.later(listOf(field), camp, july.withHour(6)).any { it.second.happening.id == "seno" })
        assertTrue(Happenings.later(listOf(field), camp, january.withHour(6)).none { it.second.happening.id == "seno" })
        // the edges of the window: from 1 June to 30 September
        assertTrue("seno" in on(LocalDateTime.of(2026, 6, 1, 14, 0)) && "seno" in on(LocalDateTime.of(2026, 9, 30, 14, 0)))
        assertFalse("seno" in on(LocalDateTime.of(2026, 5, 31, 14, 0)) || "seno" in on(LocalDateTime.of(2026, 10, 1, 14, 0)))
        // out of season a variant isn't picked even when it hasn't been heard: with the summer's heard just now, the horse
        // rests in July; in winter the stable's plays
        val heard = camp.copy(dialogsHeard = mapOf("na-njivi/konj" to DialogsHeard(mapOf("konj" to "2026-07-14"), "konj")))
        assertFalse("konj" in on(july, heard))
        assertEquals("konj-hlev", on(january, heard)["konj"])
        // a happening whose only dialog is out of season rests
        val winterOnly = field.copy(dialogs = field.dialogs.map { if (it.id == "strasilo") it.copy(months = listOf(12, 1, 2)) else it })
        assertTrue(Happenings.active(listOf(winterOnly), camp, july).none { it.happening.id == "strasilo" })
        assertTrue(Happenings.active(listOf(winterOnly), camp, january).any { it.happening.id == "strasilo" })
        // on a visit too: the day's dice picks among the dialogs in season only
        for (d in 0L until 20L) assertEquals("konj", Happenings.active(listOf(field), camp, july.plusDays(d), guest = true).first { it.happening.id == "konj" }.dialog)
        // a dialog begun goes on: what is played isn't asked about the date (a talk begun on 30 September still plays on the 1st)
        val begun = Happenings.active(listOf(field), camp, LocalDateTime.of(2026, 9, 30, 16, 50)).first { it.happening.id == "seno" }
        assertEquals("seno", DialogVariants.play(begun, camp, LocalDateTime.of(2026, 10, 1, 0, 1).toLocalDate())?.second?.id)
        // the pure rule
        assertTrue(inSeason(emptyList(), january.toLocalDate()) && inSeason(listOf(11, 12, 1, 2, 3), january.toLocalDate()) && !inSeason(listOf(6, 7, 8), january.toLocalDate()))
        // the file format: "months" on a happening, a dialog and a variant
        val raw = """{"id":"s","title":"S","art":"field","from":["fire"],"happenings":[{"id":"h","title":"H","who":"p","months":[6,7,8],"dialogs":["a","b"]}],
            "dialogs":[{"id":"a","lines":[],"months":[12,1,2]}],"variants":[{"id":"b","lines":[],"months":[3,4,5]}]}"""
        val s = parseScene(raw)
        assertEquals(listOf(6, 7, 8), s.happenings[0].months)
        assertEquals(listOf(listOf(12, 1, 2), listOf(3, 4, 5)), s.dialogs.map { it.months })
        assertEquals(emptyList<Int>(), parseScene("""{"id":"s","title":"S","art":"field","from":["fire"],"happenings":[{"id":"h","title":"H","who":"p"}]}""").happenings[0].months)
    }

    @Test fun `a scene opens only where its place is built`() {
        assertTrue(Happenings.open(fire, camp))
        assertFalse(Happenings.open(kitchen, camp))
        assertTrue(Happenings.open(kitchen, camp.copy(buildings = listOf(Building("h", BuildingType.HUT, 0)))))
        assertTrue(Happenings.open(kitchen, camp.copy(buildings = listOf(Building("h", BuildingType.HOUSE, 0)))))
        assertEquals(TownPlace.At(BuildingType.HOUSE), TownMarkers.placeOf(kitchen, camp.copy(buildings = listOf(Building("h", BuildingType.HOUSE, 0)))))
        assertFalse(Happenings.open(fire.copy(needs = "vas"), camp))
    }

    @Test fun `happenings follow the time of day and stop once done today`() {
        val on = Happenings.active(listOf(fire, kitchen), camp, evening)
        assertEquals(listOf("ob-ognju/juha"), on.map { it.key })
        val done = camp.copy(happeningsDone = mapOf("ob-ognju/juha" to "2026-09-24"))
        assertTrue(Happenings.active(listOf(fire), done, evening).isEmpty())
        assertEquals(1, Happenings.active(listOf(fire), done, evening.plusDays(1)).size)
    }

    @Test fun `later today lists what comes after now, by part of the day`() {
        val morning = evening.withHour(8)
        val later = Happenings.later(listOf(fire), camp, morning)
        assertTrue(later.any { (part, a) -> part == TimeOfDay.EVENING && a.key == "ob-ognju/juha" })
        assertTrue(later.none { it.second.key in Happenings.active(listOf(fire), camp, morning).map { a -> a.key } })
        assertTrue(Happenings.later(listOf(fire), camp, evening.withHour(23)).isEmpty())
    }

    @Test fun `the day's dice are stable and roughly fair`() {
        val day = evening.toLocalDate()
        assertEquals(Happenings.roll(7, day, "a"), Happenings.roll(7, day, "a"))
        val rolls = (0 until 2000).map { Happenings.roll(7, day.plusDays(it.toLong()), "ob-ognju/igra") }
        assertTrue(rolls.all { it >= 0f && it < 1f })
        val share = rolls.count { it < 0.5f } / 2000f
        assertTrue("share $share", share in 0.45f..0.55f)
    }

    @Test fun `people in a scene and markers in the village`() {
        val on = Happenings.active(listOf(fire), camp, evening)
        assertEquals(listOf(PersonInScene("babica", "grandma", "left")), Happenings.peopleIn(fire, on))
        val quest = Quest("q1", "Pastir Luka", "🐑", "Ovca", "…", Res.WOOD, emptyMap())
        // on a day without a festival (a festival's marker would join the list)
        val markers = TownMarkers.of(camp.copy(quests = listOf(quest)), on, today = java.time.LocalDate.of(2026, 10, 14))
        assertEquals(listOf("quest:q1", "happening:ob-ognju/juha"), markers.map { it.id })
        assertEquals(TownPlace.Forest, markers[0].place)
        assertEquals(TownPlace.Fire, markers[1].place)
    }
    @Test fun `today's visitor has a bubble until Jan has greeted or met them today`() {
        val day = java.time.LocalDate.of(2026, 10, 14)
        val france = si.lanisce.lani.game.villagers.Villager("france", "Mlinar France", "🌾", "miller")
        val visiting = camp.copy(visitor = si.lanisce.lani.game.villagers.Visit("france", day.toString()))
        fun bubble(s: GameState) = TownMarkers.of(s, emptyList(), listOf(france), day).any { it.id == "visitor:france" }
        assertTrue(bubble(visiting))
        // met yesterday: still a bubble today
        assertTrue(bubble(visiting.copy(bonds = mapOf("france" to si.lanisce.lani.game.villagers.Bond(points = 5, seen = day.minusDays(1).toString())))))
        // greeted (or helped) today: no bubble, they're just about
        assertFalse(bubble(visiting.copy(bonds = mapOf("france" to si.lanisce.lani.game.villagers.Bond(points = 5, seen = day.toString())))))
    }
}
