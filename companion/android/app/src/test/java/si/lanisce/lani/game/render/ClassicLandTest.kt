package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType.BEEHIVE
import si.lanisce.lani.game.BuildingType.CHURCH
import si.lanisce.lani.game.BuildingType.FIELD
import si.lanisce.lani.game.BuildingType.HOUSE
import si.lanisce.lani.game.BuildingType.HUT
import si.lanisce.lani.game.BuildingType.KOZOLEC
import si.lanisce.lani.game.BuildingType.LIPA
import si.lanisce.lani.game.BuildingType.MARKET
import si.lanisce.lani.game.BuildingType.PALISADE
import si.lanisce.lani.game.BuildingType.SCHOOL
import si.lanisce.lani.game.BuildingType.SMITHY
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.BuildingType.WATCHTOWER
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.Fixtures
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.WorldMarks
import si.lanisce.lani.game.villagers.Villager
import java.io.File

/**
 * Existing villages keep their look: a village from before generated land stands on the classic valley (its land marked
 * "classic" by the next tick, see [Land]), and renders pixel for pixel as it did when the layout was fixed. The digests
 * below were taken from the renderer before the land was generated (the fixed VillageLayout), for a village like Jan's
 * (a hamlet, its tent moved to the pond, a palisade, a tree felled and stone quarried) and a town with every kind of
 * building, its projects' landmarks and wolves at the gate, on a phone on its side, upright (with the woods below), two
 * zoomed-in lenses, the Home header's strip and the widget.
 *
 * A painter changed on purpose changes the digests too: take them again from a render that still draws the classic land
 * (the test prints them), and check the pictures (build/land-snapshots/classic-…) look as they should.
 */
class ClassicLandTest {
    private val today = "2026-09-27"

    /** A village like Jan's (data/app/game.json, 2026-09-27): a hamlet, the tent at the pond, a palisade, the marks of practice. */
    private val janLike = GameState(
        seed = 8993091993378563000L, age = Age.ZASELEK, villagers = 7, morale = 70, fire = 70,
        buildings = listOf(
            Building("b0", TENT, NO_PLOT), Building("b1", FIELD, 1), Building("b2", HUT, 2, level = 2), Building("b3", WELL, 3),
            Building("b4", PALISADE, NO_PLOT), Building("b5", KOZOLEC, 8), Building("b6", BEEHIVE, 5, level = 2),
            Building("b7", CHURCH, 6), Building("b8", HOUSE, 0),
        ),
        world = WorldMarks(felled = listOf("2026-09-25"), quarried = listOf("2026-09-20")),
    )

    private val townTypes = listOf(FIELD, HUT, WELL, KOZOLEC, HOUSE, PALISADE, LIPA, CHURCH, BEEHIVE, SMITHY, HOUSE, WATCHTOWER, FIELD, HOUSE, MARKET, HOUSE, SCHOOL, KOZOLEC, HOUSE, BEEHIVE, HOUSE, WELL, HOUSE, MARKET)
    private val town = GameState(
        seed = 42, age = Age.MESTO, villagers = 16, morale = 70, fire = 70,
        buildings = townTypes.mapIndexed { i, t -> Building("b$i", t, i, level = if (i % 4 == 3) 2 else 1) },
        projects = mapOf("most" to 6, "mlin" to 6, "mlaj" to 5, "kapelica" to 3),
        event = GameEvent("e", EventKind.WOLVES, 3, 0, 0),
    )

    private val cast = listOf(
        Villager(id = "micka", name = "Babica Micka", emoji = "👵", art = "grandma", home = listOf("house", "hut")),
        Villager(id = "luka", name = "Pastir Luka", emoji = "🐑", art = "shepherd", home = listOf("spot:meadow")),
        Villager(id = "tone", name = "Kovač Tone", emoji = "⚒️", art = "smith", home = listOf("spot:woodpile")),
        Villager(id = "anton", name = "Čebelar Anton", emoji = "🐝", art = "beekeeper", home = listOf("beehive", "spot:riverbank")),
    )

    /** The digests of the fixed layout's renders (see the class's note). */
    private val golden = mapOf(
        "jan-like" to mapOf(
            "side-day" to "55ec8cebd23a88", "tall-day" to "b31cbb085ff759e1", "tall-dusk-k2" to "46079da81b31ca50",
            "tall-night-k3" to "9d73e8aee5151931", "compact" to "1934de8ad4b0682b", "widget" to "f8330619ecea4a4b",
        ),
        "town" to mapOf(
            "side-day" to "ddb9a45bd559a91c", "tall-day" to "bd5f1395382ad063", "tall-dusk-k2" to "473fba74ab5175bc",
            "tall-night-k3" to "2a1e50a6c85a4bbc", "compact" to "f937a0419a48b3c8", "widget" to "2494de93332a4430",
        ),
    )

    /** 64-bit FNV-1a over the pixels. */
    private fun fnv(ints: IntArray): String {
        var h = -0x340d631b7bdddcdbL
        for (v in ints) { h = h xor (v.toLong() and 0xffffffffL); h *= 0x100000001b3L }
        return java.lang.Long.toHexString(h)
    }

    private fun lensAt(k: Int, w: Int, h: Int, fx: Int, fy: Int) = Lens(k, (fx * k - w / 2).coerceIn(0, w * k - w), (fy * k - h / 2).coerceIn(0, h * k - h), w, h)

    private fun views(): List<Triple<String, Pair<Int, Int>, Frame>> {
        val tall = VillageLayout.composition(360, 800, false)
        return listOf(
            Triple("side-day", 400 to 180, Frame(time = 3.0, hour = 11f, month = 9, today = today, people = cast)),
            Triple("tall-day", 360 to 800, Frame(time = 3.0, hour = 11f, month = 6, today = today, people = cast)),
            Triple("tall-dusk-k2", 360 to 800, Frame(time = 5.0, hour = 19.2f, month = 10, today = today, people = cast, lens = lensAt(2, 360, 800, tall.fireX, tall.fireY + 40))),
            Triple("tall-night-k3", 360 to 800, Frame(time = 7.0, hour = 23f, month = 1, today = today, lens = lensAt(3, 360, 800, tall.fireX - 30, tall.fireY + 20))),
            Triple("compact", 360 to 264, Frame(time = 3.0, hour = 12f, month = 6, compact = true, today = today)),
            Triple("widget", 200 to 94, Frame(time = 3.0, hour = 17f, month = 10, compact = true, today = today)),
        )
    }

    private fun digests(s: GameState, png: String? = null): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((name, size, frame) in views()) {
            val c = PixelCanvas(size.first, size.second)
            VillageRenderer().render(c, s, frame)
            out[name] = fnv(c.pixels)
            if (png != null) {
                val dir = File("build/land-snapshots").apply { mkdirs() }
                val img = java.awt.image.BufferedImage(c.width, c.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
                for (y in 0 until c.height) for (x in 0 until c.width) img.setRGB(x, y, c.pixels[y * c.width + x])
                javax.imageio.ImageIO.write(img, "png", File(dir, "$png-$name.png"))
            }
        }
        return out
    }

    @Test fun `a village from before generated land looks exactly as it did`() {
        for ((name, s) in listOf("jan-like" to janLike, "town" to town)) {
            // unmarked (as saved before), and marked classic (as the next tick leaves it): the same pixels as the fixed layout
            val before = digests(s, "classic-$name")
            println("$name $before")
            assertEquals(name, golden.getValue(name), before)
            assertEquals("$name marked classic", golden.getValue(name), digests(s.copy(land = Land.CLASSIC)))
        }
        // a real Primorska village save, when a copy of it is at build/jan/game.json (not in the repository): its digests, to compare
        val jan = File("build/jan/game.json")
        if (jan.exists()) {
            val doc = si.lanisce.lani.data.json.parseToJsonElement(jan.readText()) as kotlinx.serialization.json.JsonObject
            val st = si.lanisce.lani.data.json.decodeFromJsonElement(GameState.serializer(), doc.getValue("state"))
            println("jan ${digests(st, "jan")}; marked ${digests(st.copy(land = Land.CLASSIC))}")
        }
    }

    @Test fun `the classic land is the fixed layout, number for number`() {
        val t = Terrain.CLASSIC
        // the first plots and the clearing of every age, as the fixed layout had them
        assertEquals(listOf(33.40898f, 42.00762f, 54.692684f, 54.798775f), t.centers.take(4).map { it[2] })
        assertEquals(listOf(72.00762f, 91.46109f, 100.73032f, 120.36757f, 138.06f, 154.82741f), Age.entries.map { t.clearing(it) })
        assertEquals(-13f, t.streamX(0f))
        assertEquals(listOf(-9.298864f, 3f), t.riverbank.toList())
        assertEquals(listOf(-13f, 0f), t.bridge.toList())
        for (x in listOf(-30f, -8f, 0f, 12f, 30f)) assertEquals(0f, t.roadY(x))
        assertNull(t.woods)
        assertEquals(0, t.treeSalt)
    }

    @Test fun `the next tick marks an older save classic, and changes nothing else`() {
        val t0 = Fixtures.noon(Fixtures.day0)
        val old = janLike.copy(lastTick = Fixtures.day0.toString(), foundedOn = "2026-09-01")
        assertNull(old.land)
        val a = GameEngine.tick(old, Fixtures.day0, t0, Fixtures.pool()).state
        val b = GameEngine.tick(old.copy(land = Land.CLASSIC), Fixtures.day0, t0, Fixtures.pool()).state
        assertEquals(Land.CLASSIC, a.land)
        assertEquals(b, a)
        // written as {"kind":"classic"}, read back the same; an older app (it doesn't know the key) reads the rest as before
        val text = si.lanisce.lani.data.json.encodeToString(GameState.serializer(), a)
        assertTrue(text, "\"land\":{\"kind\":\"classic\"}" in text)
        assertEquals(a, si.lanisce.lani.data.json.decodeFromString(GameState.serializer(), text))
        // a new village is generated (a valley with a stream from its seed); a marked one is never changed again
        assertEquals("valley", GameEngine.newGame(5, t0).land?.kind)
        assertEquals(a.land, GameEngine.tick(a, Fixtures.day0.plusDays(1), t0 + 86_400_000L, Fixtures.pool()).state.land)
    }
}
