package si.lanisce.lani.game.villagers

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.EventEnd
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.Surprise
import si.lanisce.lani.game.Surprises
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.Keepers
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TownMarker
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.Whereabouts
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.villagers.VillagerLogic
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * While wolves, a bear or a storm are about the village's everyday talks wait (companion/GAME.md "People react"): no
 * happening, story, meeting, keeper's talk, surprise or bubble but the event's and the requests', and each person says
 * something of it instead of their greeting; when it's over every one of them is back, a dialog that was due still due.
 */
class ReactionTalksTest {
    private val pair = L10n.pair
    @After fun back() { L10n.pair = pair; Cultures.use(Cultures.DEFAULT) }

    private val cultures = listOf(File("../../cultures"), File("../cultures")).first { it.isDirectory }
    private val cast: List<Villager> by lazy {
        File(cultures, "primorska/villagers").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
            .map { parseVillagers("[" + it.readText() + "]").single() }.sortedBy { it.order }
    }
    private val scenes: List<SceneSpec> by lazy {
        listOf(File(cultures, "../scenes"), File(cultures, "primorska/scenes")).flatMap { d -> d.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { parseScene(it.readText()) } }
    }

    /** A village of the whole cast, everyone moved in and met: the market, the school, the watchtower, a request up. */
    private val town: GameState by lazy {
        val types = listOf(BuildingType.HUT, BuildingType.FIELD, BuildingType.WELL, BuildingType.TENT, BuildingType.KOZOLEC, BuildingType.HOUSE, BuildingType.LIPA,
            BuildingType.BEEHIVE, BuildingType.HOUSE, BuildingType.SMITHY, BuildingType.MARKET, BuildingType.HOUSE, BuildingType.SCHOOL, BuildingType.WATCHTOWER,
            BuildingType.HOUSE, BuildingType.CHURCH)
        GameState(
            seed = 42, age = Age.VAS, villagers = cast.size, morale = 80, fire = 80, land = Land.CLASSIC,
            buildings = types.mapIndexed { i, t -> Building("b$i", t, i, level = 2, builtAt = i.toLong()) } + Building("pal", BuildingType.PALISADE, NO_PLOT),
            residents = cast.map { Resident(it.id, "2026-01-01") },
            quests = listOf(Quest("q1", "Kovač Tone", "⚒️", "Pomagaj mi · Help me", "Tone prosi. · Tone asks.", si.lanisce.lani.game.Res.STONE, emptyMap(), since = "2026-10-13")),
        )
    }

    private val october = LocalDate.of(2026, 10, 13)
    private val t0 = october.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun on(s: GameState, kind: EventKind) = s.copy(event = GameEvent("ev-test", kind, 2, t0, t0 + 36 * 3_600_000L))

    /** The first time of the next days at which some happenings are on in [s] (by daylight and in the evening). */
    private fun busy(s: GameState): LocalDateTime =
        (0 until 30).flatMap { d -> listOf(10, 15, 19).map { h -> october.plusDays(d.toLong()).atTime(h, 30) } }
            .first { Happenings.active(scenes, s, it, cast = cast).size >= 2 }

    @Test fun `no happening, story or teaser while wolves, a bear or a storm are about, all of them back after`() {
        val at = busy(town)
        val before = Happenings.active(scenes, town, at, cast = cast)
        for (k in listOf(EventKind.WOLVES, EventKind.BEAR, EventKind.STORM)) {
            assertTrue("$k", Happenings.active(scenes, on(town, k), at, cast = cast).isEmpty())
            assertTrue("$k later today", Happenings.later(scenes, on(town, k), at, cast).isEmpty())
            assertTrue(Reactions.quiet(on(town, k)))
        }
        // the merchant and a feast day don't keep anyone from their day
        for (k in listOf(EventKind.MERCHANT, EventKind.FESTIVAL)) {
            assertEquals("$k", before.map { it.key }, Happenings.active(scenes, on(town, k), at, cast = cast).map { it.key })
            assertFalse(Reactions.quiet(on(town, k)))
        }
        // over (won or lost): the same happenings, each with the same dialog that was due
        val after = town.copy(ended = EventEnd(EventKind.WOLVES, won = true, at = t0))
        assertEquals(before.map { it.key to it.dialog }, Happenings.active(scenes, after, at, cast = cast).map { it.key to it.dialog })
    }

    @Test fun `the map shows the event and the requests only, no meeting, visitor, surprise or project`() {
        // (the surprise's bubble waits; its run stays a tap away from the HUD and Home)
        val surprise = town.copy(surprise = Surprise(october.toString(), Surprises.PILGRIM))
        val quiet = on(surprise, EventKind.WOLVES)
        val at = busy(town)
        val markers = TownMarkers.of(quiet, Happenings.active(scenes, quiet, at, cast = cast), cast, october)
        assertTrue("$markers", markers.all { it.kind == TownMarker.Kind.EVENT || it.id.startsWith("quest:") })
        assertTrue(markers.any { it.kind == TownMarker.Kind.EVENT } && markers.any { it.id.startsWith("quest:") })
        assertTrue(TownMarkers.arrivals(quiet, cast, october).isEmpty())
        // the stranger keeps in at the inn; the day's surprise stays the learner's to play (the HUD, Home), as the requests
        assertNull(Surprises.stranger(quiet, october))
        assertTrue(Surprises.whoHere(quiet, quiet.surprise!!, october))
        // the merchant: the rest as ever
        val merchant = on(surprise, EventKind.MERCHANT)
        assertEquals(Surprises.stranger(surprise, october), Surprises.stranger(merchant, october))
    }

    @Test fun `the charcoal burner stays home while wolves or a bear are about, not in a storm`() {
        val evening = (0 until 60).map { october.plusDays(it.toLong()).atTime(20, 30) }.firstOrNull { Keepers.here(town, it).isNotEmpty() } ?: return
        assertTrue(Keepers.here(on(town, EventKind.WOLVES), evening).isEmpty())
        assertTrue(Keepers.here(on(town, EventKind.BEAR), evening).isEmpty())
        assertEquals(Keepers.here(town, evening).map { it.key }, Keepers.here(on(town, EventKind.STORM), evening).map { it.key })
    }

    @Test fun `each says something of it instead of their greeting, their own greeting again after`() {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        val tone = cast.first { it.id == "tone" }
        val micka = cast.first { it.id == "micka" }
        val file = Cultures.reactions("primorska")!!
        val wolves = on(town, EventKind.WOLVES)
        val said = VillagerLogic.greeting(tone, Bond(), october, state = wolves, people = cast, wall = t0 + 60_000L)
        assertEquals(1, said.size)
        assertTrue("${said[0]}", said[0].sl in file.wolves.defender.map { it.by["sl"] })
        assertTrue(VillagerLogic.idle(micka, 0, october, state = wolves, people = cast, wall = t0 + 60_000L)!!.sl in file.wolves.elder.map { it.by["sl"] })
        // the merchant's: of his wares; a feast: festive
        assertTrue(VillagerLogic.greeting(micka, Bond(), october, state = on(town, EventKind.MERCHANT), people = cast, wall = t0 + 60_000L)[0].sl in file.merchant.elder.map { it.by["sl"] })
        assertTrue(VillagerLogic.greeting(micka, Bond(), october, state = on(town, EventKind.FESTIVAL), people = cast, wall = t0 + 60_000L)[0].sl in file.festival.elder.map { it.by["sl"] })
        // over: their own greeting again
        val plain = VillagerLogic.greeting(micka, Bond(), october, state = town, people = cast)
        val after = VillagerLogic.greeting(micka, Bond(), october, state = town.copy(ended = EventEnd(EventKind.WOLVES, true, t0)), people = cast, wall = t0 + 3_600_000L)
        assertEquals(plain, after)
        assertNotEquals(plain, VillagerLogic.greeting(micka, Bond(), october, state = wolves, people = cast, wall = t0 + 60_000L))
    }

    @Test fun `the scenes show who the reaction has there - by the fire the children and the old, nobody in the field`() {
        val fireScene = scenes.first { it.from == listOf("fire") }
        val wolves = on(town, EventKind.WOLVES)
        val day = Whereabouts.day(scenes, wolves, cast, cast, october)
        for (h in listOf(10, 15)) {
            val at = october.atTime(h, 20)
            val inFire = Whereabouts.inScene(fireScene, wolves, cast, at, day, scenes).map { p -> fireScene.people.first { it.id == p.id }.villager }
            val stances = Reactions.stances(wolves, cast, EventKind.WOLVES, 2)
            assertTrue("$h: $inFire", inFire.all { it != null && Reactions.byFire(stances.getValue(it)) })
            for (s in scenes.filter { it.from.any { f -> f == "field" } }) assertTrue(Whereabouts.inScene(s, wolves, cast, at, day, scenes).isEmpty())
        }
    }

    @Test fun `no lesson nor games while they're about`() {
        val quiet = on(town, EventKind.STORM)
        for (s in scenes) assertNull(si.lanisce.lani.game.scene.SceneOffers.lesson(s, quiet, null))
        assertTrue(Reactions.quiet(quiet))
        assertFalse(Reactions.quiet(town))
        assertFalse(Reactions.quiet(null))
    }
}
