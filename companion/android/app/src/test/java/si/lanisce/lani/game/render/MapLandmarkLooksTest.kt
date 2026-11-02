package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.render.scene.Tower
import si.lanisce.lani.game.scene.CultureLooks
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The projects' landmarks and the hayrack on the village map in each culture's look, as the culture's scenes of them build
 * them (docs/plans/05-built/map-culture-2.md; companion/GAME.md "The map in a culture's look"): the map's entries are the
 * scenes' where a scene has the thing, each culture's finished landmarks are drawn pixel for pixel as reviewed (the
 * digests below, taken after a look at the sheets), and the sheets for the eyes (build/map-looks/05-landmarks-<when>.png: a
 * row a culture, a column a landmark, closer up; 06-hayrack.png: the hayrack at levels 1 to 5).
 */
class MapLandmarkLooksTest {
    private val dir = File("build/map-looks").apply { mkdirs() }

    /** The cultures, each on its usual land. */
    private val cultures = listOf("primorska" to Landscape.VALLEY, "friuli" to Landscape.HILLS, "kaernten" to Landscape.LAKE, "lakeland" to Landscape.LAKE)

    private val order = listOf(
        BuildingType.TENT, BuildingType.FIELD, BuildingType.HUT, BuildingType.KOZOLEC, BuildingType.WELL, BuildingType.HOUSE,
        BuildingType.CHURCH, BuildingType.LIPA, BuildingType.WATCHTOWER, BuildingType.SCHOOL, BuildingType.SMITHY,
        BuildingType.BEEHIVE, BuildingType.MARKET, BuildingType.HOUSE, BuildingType.HOUSE, BuildingType.FIELD, BuildingType.HUT,
        BuildingType.KOZOLEC, BuildingType.HOUSE, BuildingType.WELL, BuildingType.HOUSE, BuildingType.HOUSE, BuildingType.MARKET,
        BuildingType.HOUSE, BuildingType.BEEHIVE, BuildingType.HOUSE, BuildingType.FIELD, BuildingType.HOUSE,
    )

    /** A town on [landscape]'s land with every project finished, one villager about (nobody in front of the landmarks). */
    private fun town(landscape: Landscape): GameState {
        val base = GameEngine.newGame(2026L, 0L, landscape)
        val n = PLOTS_PER_AGE[Age.MESTO.ordinal]
        val built = order.take(n).mapIndexed { i, t -> Building("b$i", t, i, level = 2) } + Building("p", BuildingType.PALISADE, NO_PLOT, level = 1)
        return base.copy(
            age = Age.MESTO, villagers = 1, morale = 70, fire = 70, buildings = built,
            projects = Catalog.projectFrames.associate { it.id to it.steps.size },
        )
    }

    private val june = Frame(time = 3.0, hour = 11f, month = 6, today = "2026-06-20")
    private val september = Frame(time = 3.0, hour = 15f, month = 9, today = "2026-09-12")
    private val winter = Frame(time = 3.0, hour = 12f, month = 1, today = "2026-01-15")
    private val night = Frame(time = 3.0, hour = 22.5f, month = 9, today = "2026-09-12")

    private val k = 3; private val cw = 64; private val ch = 64

    /** The landmark [id] of [s] closer up (k = 3), [cw] × [ch] nominal px round it, in [culture]'s look. */
    private fun shot(s: GameState, id: String, culture: String, frame: Frame): PixelCanvas? {
        val w = 360; val h = 640
        val sp = LandmarkLayout.of(s, w, h).firstOrNull { it.landmark.project == id } ?: return null
        val comp = VillageLayout.composition(w, h, false)
        val cx = sp.x + sp.w / 2f; val cy = sp.y + sp.d / 2f
        val up = LandmarkLayout.shapeOf(sp.landmark.id).height * 0.55f
        val px = (comp.fireX + (cx - cy) * 4f).toInt(); val py = (comp.fireY + (cx + cy) * 2f - up).toInt()
        val lens = Lens(k, (px * k - cw * k / 2).coerceIn(0, w * k - cw * k), (py * k - ch * k / 2).coerceIn(0, h * k - ch * k), w, h)
        return PixelCanvas(cw * k, ch * k).also { VillageRenderer().render(it, s, frame.copy(lens = lens, culture = culture)) }
    }

    /** The landmarks the sheets and the digests show: every project's but the bridge's (MapBridgeLooksTest's). */
    private val landmarks = Catalog.projectFrames.map { it.id }.filter { it != Crossing.BRIDGE }.sorted()

    private fun fnv(ints: IntArray): String {
        var h = -0x340d631b7bdddcdbL
        for (v in ints) { h = h xor (v.toLong() and 0xffffffffL); h *= 0x100000001b3L }
        return java.lang.Long.toHexString(h)
    }

    /** Writes [rows] (a label and its cells, each a label and a canvas) as a sheet. */
    private fun sheet(name: String, rows: List<Pair<String, List<Pair<String, PixelCanvas?>>>>) {
        val cols = rows.maxOf { it.second.size }
        val lab = 14; val gw = cw * k; val gh = ch * k
        val img = BufferedImage(cols * gw, rows.size * (gh + lab), BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(30, 30, 34); g.fillRect(0, 0, img.width, img.height); g.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        for ((r, row) in rows.withIndex()) for ((col, cell) in row.second.withIndex()) {
            val (label, c) = cell
            val ox = col * gw; val oy = r * (gh + lab)
            if (c != null) for (y in 0 until c.height) for (x in 0 until c.width) img.setRGB(ox + x, oy + lab + y, c.pixels[y * c.width + x])
            g.color = Color(230, 230, 230); g.drawString("${row.first} $label", ox + 3, oy + 11)
        }
        g.dispose()
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    // ---------------------------------------------------------------- the map's entries are the scenes'

    @Test fun `the map's landmarks are the scenes' where a scene has them`() {
        for ((c, _) in cultures) {
            val look = CultureLooks.of(c)
            val map = MapLook.of(c)
            // the field: its hayrack (the kozolec, the fienile, the Harpfe, the barn) and its toplar (the double hayrack with or
            // without maize, the Doppelharpfe, the bank barn)
            assertEquals("$c's hayrack", look.variant("field", "kozolec") ?: Hayrack.KOZOLEC.id, map.hayrack.id)
            assertEquals("$c's toplar", look.variant("field", "toplar") ?: Toplar.TOPLAR.id, map.toplar.id)
            // the stream's cart bridge, and its mill's walls
            assertEquals("$c's bridge", look.variant("stream", "cartbridge") ?: Bridge.TIMBER.id, map.bridge.id)
            assertEquals("$c's mill of slate", look.variant("stream", "mill") == "slate", map.slateMill)
            // the square: its maypole, its fountain, the church tower's clock (Carinthia's under the onion dome its village
            // builds, the Lake District's faces of slate)
            assertEquals("$c's maypole", look.variant("square", "maypole") ?: Maypole.MLAJ.id, map.maypole.id)
            assertEquals("$c's fountain", look.variant("square", "fountain") ?: Fountain.OWN.id, map.fountain.id)
            assertEquals("$c's clock faces", look.variant("square", "clock") == "slate", map.slateClock)
            if (look.variant("square", "village") == "kaernten") assertEquals("$c's clock tower", Tower.ONION, map.clock)
            // the wayside chapel: the ancona, the onion-capped turret, the stone bellcote
            assertEquals("$c's chapel", look.variant("chapel", "chapel") ?: look.variant("chapel", "turret") ?: Chapel.OWN.id, map.chapel.id)
            // the fire station's walls (the hose tower too), the playground's fontanella, the bee meadow's sunflowers
            assertEquals("$c's fire station of cobbles", look.material("firestation", "walls", "plaster") == "cobbles", map.cobbleStation)
            assertEquals("$c's rescue hut of slate stone", look.material("firestation", "walls", "plaster") == "slate", map.style?.stone == true)
            assertEquals("$c's fontanella", look.variant("playground", "fontanella") != null, map.fontanella)
            assertEquals("$c's sunflowers", look.variant("apiary", "meadow") == "girasoli", map.sunflowers)
        }
        // the town clock, the lookout and the chapel of the default culture are under shingles, its toplar under boards, as
        // their scenes have them (no red tile left on them): the map's own look has no culture style to read
        assertEquals(null, MapLook.DEFAULT.clock)
        assertEquals(Toplar.TOPLAR, MapLook.DEFAULT.toplar)
    }

    @Test fun `the default culture's landmarks have no red tiles where their scenes have none`() {
        // Primorska's toplar, chapel and lookout, closer up by day: finished, they add no tile red to the picture round them
        // (their roofs are boards and shingles, as their scenes'; a red roof of theirs would add a hundred pixels and more)
        val s = town(Landscape.VALLEY)
        val tiles = setOf(Pal.ROOF_L, Pal.ROOF_M, Pal.ROOF_D, Pal.ROOF_L.let { Col.scale(it, 0.86f) })
        for (id in listOf("toplar", "kapelica", "razgledni_stolp")) {
            val built = shot(s, id, "primorska", june)!!.pixels.count { it in tiles }
            val none = shot(s.copy(projects = s.projects + (id to 1)), id, "primorska", june)!!.pixels.count { it in tiles }
            assertTrue("$id adds no red tiles ($built with it, $none with its site)", built <= none)
        }
    }

    // ---------------------------------------------------------------- the sheets

    @Test fun `render every culture's landmarks closer up`() {
        for ((name, f) in listOf("june" to june, "september" to september, "winter" to winter, "night" to night)) {
            sheet("05-landmarks-$name", cultures.map { (c, l) ->
                val s = town(l)
                c to landmarks.map { id -> id to shot(s, id, c, f) }
            })
        }
    }

    @Test fun `render every culture's hayrack at its levels`() {
        val rows = cultures.map { (c, l) ->
            val s = town(l)
            val b = s.buildings.first { it.type == BuildingType.KOZOLEC }
            c to (1..5).flatMap { lv ->
                val one = s.copy(buildings = listOf(b.copy(level = lv), Building("p", BuildingType.PALISADE, NO_PLOT)), projects = emptyMap())
                listOf("$lv" to buildingShot(one, b, c, june), "$lv winter" to buildingShot(one, b, c, winter))
            }
        }
        sheet("06-hayrack", rows)
    }

    /** Building [b] of [s] closer up (k = 3). */
    private fun buildingShot(s: GameState, b: Building, culture: String, frame: Frame): PixelCanvas {
        val w = 360; val h = 640
        val comp = VillageLayout.composition(w, h, false)
        val o = Terrain.of(s).origin(b.plot)
        val cx = o[0] + 1.5f; val cy = o[1] + 1.5f
        val px = (comp.fireX + (cx - cy) * 4f).toInt(); val py = (comp.fireY + (cx + cy) * 2f - spriteHeight(b.type) * 0.5f).toInt()
        val lens = Lens(k, (px * k - cw * k / 2).coerceIn(0, w * k - cw * k), (py * k - ch * k / 2).coerceIn(0, h * k - ch * k), w, h)
        return PixelCanvas(cw * k, ch * k).also { VillageRenderer().render(it, s, frame.copy(lens = lens, culture = culture)) }
    }

    // ---------------------------------------------------------------- the digests

    @Test fun `each culture's landmarks are drawn as reviewed`() {
        val got = LinkedHashMap<String, String>()
        for ((c, l) in cultures) {
            val s = town(l)
            for (id in landmarks) got["$c/$id"] = shot(s, id, c, june)?.let { fnv(it.pixels) } ?: "none"
        }
        if (got != golden) println("MapLandmarkLooksTest digests:\n" + got.entries.joinToString("\n") { "\"${it.key}\" to \"${it.value}\"," })
        assertEquals(golden, got)
    }

    /**
     * The digests of the June sheet's cells (see the class's note), taken 2026-11-02 after a look at the sheets: each
     * landmark as its place's scene builds it in its culture (map-culture-2).
     */
    private val golden: Map<String, String> = mapOf(
        "primorska/balinisce" to "d6bf05c7df610e47",
        "primorska/cebelji_travnik" to "42f4c8e8fbfe740",
        "primorska/gasilski_dom" to "fa8b4e671ac082aa",
        "primorska/igrisce" to "50d3a71ee2401c1d",
        "primorska/kapelica" to "222a63626235adbf",
        "primorska/mestna_ura" to "f131922e96f79e87",
        "primorska/mlaj" to "117f2a117f2e4ab6",
        "primorska/mlin" to "d7d31b47c30323c",
        "primorska/razgledni_stolp" to "2e593a0ef5624394",
        "primorska/toplar" to "13cada1a9e0e1a66",
        "primorska/vinograd" to "10aeedcff33fa5b8",
        "primorska/vodnjak_na_trgu" to "fe116789e6473f1d",
        "friuli/balinisce" to "11ef11479ed9674b",
        "friuli/cebelji_travnik" to "52ef4cb9b7ebbfdb",
        "friuli/gasilski_dom" to "66c131e8b513d963",
        "friuli/igrisce" to "eace7534b3570fe5",
        "friuli/kapelica" to "e5d69bc31f78377f",
        "friuli/mestna_ura" to "d4cbe96b5a497d6d",
        "friuli/mlaj" to "b600b772382f41cc",
        "friuli/mlin" to "fb48686a743714bc",
        "friuli/razgledni_stolp" to "8d51711446867c8b",
        "friuli/toplar" to "a069814ad971775d",
        "friuli/vinograd" to "dbcd5aed373069c2",
        "friuli/vodnjak_na_trgu" to "6fbda1f49bc59c2a",
        "kaernten/balinisce" to "469d5de1c2c56597",
        "kaernten/cebelji_travnik" to "fbe82a1715803b67",
        "kaernten/gasilski_dom" to "969f0add7d028a7e",
        "kaernten/igrisce" to "ac83006267865a31",
        "kaernten/kapelica" to "e6e3501bd1a5811c",
        "kaernten/mestna_ura" to "ba8ef751bb898948",
        "kaernten/mlaj" to "527703b5696e479b",
        "kaernten/mlin" to "fa9f80676be8b112",
        "kaernten/razgledni_stolp" to "30dccdd683e4244e",
        "kaernten/toplar" to "cb2624f28967bd21",
        "kaernten/vinograd" to "d764624540e8d89e",
        "kaernten/vodnjak_na_trgu" to "56b47a7aa7ce542d",
        "lakeland/balinisce" to "34e72ee9f6818249",
        "lakeland/cebelji_travnik" to "bbdb4311ded9f7c",
        "lakeland/gasilski_dom" to "7c710cde59e40fe4",
        "lakeland/igrisce" to "f59d33785db1e838",
        "lakeland/kapelica" to "529afcf8ecc4c663",
        "lakeland/mestna_ura" to "c2c02897c19501f4",
        "lakeland/mlaj" to "322752b67cbcee6",
        "lakeland/mlin" to "24e0f5427d236060",
        "lakeland/razgledni_stolp" to "b04c5517feb45a86",
        "lakeland/toplar" to "7a8e537b34e81188",
        "lakeland/vinograd" to "46c235288715b3b",
        "lakeland/vodnjak_na_trgu" to "5c035a5abf83c8f6",
    )
}
