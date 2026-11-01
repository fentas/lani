package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.scene.Looks
import si.lanisce.lani.game.render.scene.PeoplePainter
import si.lanisce.lani.game.render.scene.Wardrobes
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.parseVillagers
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The map's people in each culture's clothes (docs/plans/05-built/map-people.md): the 14 px and 7 px figures wear what the
 * scenes' sprites wear (Friuli's, Carinthia's and the Lake District's [Wardrobes]; Primorska's small figures, drawn for these
 * sizes, agree with its sprites in every mark they have), and the village's own people are dressed the same at every detail
 * and in their scenes. Contact sheets for the eyes, in build/map-people/. `00-cast-<culture>.png`: the culture's whole cast, the strangers at the road and some of the village's
 * own people in a lineup by the fire, at the map's three details (the 7 px figures, the 14 px ones, the scene's sprites)
 * by day and at night. `01-village-<culture>.png`: the culture's village with its own people about their day, the whole map
 * and closer, in the morning and in the evening by the fire.
 */
class MapPeopleTest {
    private val dir = File("build/map-people").apply { mkdirs() }
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { it.resolve("cultures").isDirectory }

    private val cultures = listOf("primorska" to Landscape.VALLEY, "friuli" to Landscape.HILLS, "kaernten" to Landscape.LAKE, "lakeland" to Landscape.LAKE)

    /** [culture]'s cast as its villager files have them. */
    private fun cast(culture: String): List<Villager> {
        val files = companion.resolve("cultures/$culture/villagers").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        return parseVillagers(files.joinToString(",", "[", "]") { it.readText() }).sortedBy { it.order }
    }

    /** Everyone in the lineup: the cast, the strangers at the road, three women and three men of the village's own. */
    private fun lineup(culture: String): List<Villager> = cast(culture) +
        listOf("pedlar", "pilgrim").map { Villager(id = "stranger-$it", name = it, art = it) } +
        (1..3).map { Villager(id = "resident-w$it", name = "w$it", art = "woman") } +
        (1..3).map { Villager(id = "resident-m$it", name = "m$it", art = "man", voice = "male") }

    private val W = 360; private val H = 640

    /** World cells for a point [dx], [dy] nominal px from the fire's middle (the map's isometry: 4 px across, 2 down a cell). */
    private fun cell(dx: Float, dy: Float): FloatArray = floatArrayOf((dy / 2f + dx / 4f) / 2f, (dy / 2f - dx / 4f) / 2f)

    /** The lineup in rows of [per] in front of the fire, every other one turned, talking or waving; a lantern each at [night]. */
    private fun placed(people: List<Villager>, per: Int, night: Boolean): List<Placed> = people.mapIndexed { i, v ->
        val row = i / per; val col = i % per
        val p = cell((col - (per - 1) / 2f) * 12f, 20f + row * 22f)
        val activity = when (i % 3) { 0 -> Activity.STAND; 1 -> Activity.CHAT; else -> Activity.WALK }
        Placed(v, p[0], p[1], activity, flip = i % 2 == 1, place = TownPlace.Fire, slot = i, lantern = night && i % 2 == 0)
    }

    private fun village(landscape: Landscape, age: Age = Age.VAS): GameState =
        GameEngine.newGame(2026L, 0L, landscape).copy(age = age, villagers = 0, morale = 70, fire = 70, buildings = emptyList())

    private fun render(s: GameState, f: Frame, w: Int, h: Int): PixelCanvas = PixelCanvas(w, h).also { VillageRenderer().render(it, s, f) }

    /** A window of [w] × [h] canvas px at detail [k], its middle [cy] nominal px below the fire. */
    private fun window(k: Int, w: Int, h: Int, cy: Int): Lens {
        val comp = VillageLayout.composition(W, H, false)
        val fx = comp.fireX; val fy = comp.fireY + cy
        return Lens(k, (fx * k - w / 2).coerceIn(0, W * k - w), (fy * k - h / 2).coerceIn(0, H * k - h), W, H)
    }

    /** The canvases as one picture, a row each, every cell [scale] times bigger, [gap] px between. */
    private fun write(name: String, rows: List<List<Pair<PixelCanvas, Int>>>, gap: Int = 4) {
        val cw = rows.flatten().maxOf { (c, s) -> c.width * s }; val ch = rows.map { r -> r.maxOf { (c, s) -> c.height * s } }
        val cols = rows.maxOf { it.size }
        val img = BufferedImage(cols * (cw + gap) + gap, ch.sum() + gap * (rows.size + 1), BufferedImage.TYPE_INT_RGB)
        val g = img.graphics; g.color = java.awt.Color(0x22, 0x24, 0x2A); g.fillRect(0, 0, img.width, img.height); g.dispose()
        var oy = gap
        for ((r, row) in rows.withIndex()) {
            for ((n, cell) in row.withIndex()) {
                val (c, s) = cell
                val ox = gap + n * (cw + gap)
                for (y in 0 until c.height * s) for (x in 0 until c.width * s) img.setRGB(ox + x, oy + y, c.pixels[(y / s) * c.width + x / s])
            }
            oy += ch[r] + gap
        }
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    /** [c] cut to [x], [y], [w], [h]. */
    private fun crop(c: PixelCanvas, x: Int, y: Int, w: Int, h: Int): PixelCanvas {
        val o = PixelCanvas(w, h)
        for (yy in 0 until h) for (xx in 0 until w) {
            val sx = x + xx; val sy = y + yy
            if (sx in 0 until c.width && sy in 0 until c.height) o.pixels[yy * w + xx] = c.pixels[sy * c.width + sx]
        }
        return o
    }

    /** The people the map draws: the cast's sprites, the strangers, the village's own (not the baby: carried). */
    private val arts = SceneArt.people - "baby"

    /** Every pixel colour of [art]'s figure at detail [k] of [culture]'s map, standing (person [id]). */
    private fun colours(art: String, culture: String, k: Int, id: String = "who-$art"): Set<Int> {
        val c = PixelCanvas(40 * k, 40 * k)
        val s = SceneCtx(c).apply { lens = Lens(k, 0, 0, c.width, c.height); look = MapLook.of(culture); time = 0.4 }
        val look = Effects.lookOf(art, Noise.hash(id.hashCode(), 13), culture, Looks.seedOf(id))
        s.effects.person(c.width / 2, c.height - 2, art, look, TownPeople.drawSeed(id), Activity.STAND, flip = false, lookSeed = Looks.seedOf(id))
        return c.pixels.filter { it ushr 24 != 0 }.toSet()
    }

    @Test fun `the 14 px figures wear the clothes of the scenes' sprites in every culture with a wardrobe`() {
        val failed = ArrayList<String>()
        for (culture in Wardrobes.ids) for (art in arts) for (n in 0..2) {
            val id = "who-$art-$n"
            val L = Looks.of(art, Looks.seedOf(id), culture)
            val px = colours(art, culture, 2, id)
            val want = buildMap {
                put("shirt", L.top)
                // (a child's wellies come up to the shorts at this size)
                if (L.skirt || !(L.boots && L.child)) put(if (L.skirt) "skirt" else "trousers", L.bottom)
                if (L.apron != 0) put("apron", L.apron)
                if (L.vest != 0) put("waistcoat", L.vest)
                if (L.checks != 0) put("check", L.checks)
                if (L.hat == Looks.HAT_FELT || L.hat == Looks.HAT_CAP || L.hat == Looks.HAT_SCARF || L.hat == Looks.HAT_BANDANA) put("hat", L.hatCol)
                if (L.boots || L.toes != 0 || L.shoes != Effects.SHOES) put("shoes", L.shoes)
                if (L.toes != 0) put("toes", L.toes)
            }
            for ((what, col) in want) if (col !in px) failed += "$culture $art ($n): its $what ${Integer.toHexString(col)}"
            // the sprite at the finest detail is the scene's: drawn by the scene's painter, its clothes the same
            val fine = colours(art, culture, 3, id)
            val scene = PixelCanvas(40, 40).also { PeoplePainter().also { p -> p.culture = culture }.draw(it, art, 20, 38, 0.4, Looks.seedOf(id), talking = false, flip = false, walking = false, seated = false) }
                .pixels.filter { it ushr 24 != 0 }.toSet()
            for ((what, col) in want) if (col in scene && col !in fine) failed += "$culture $art ($n): on the map closer up without its $what"
        }
        assertEquals(emptyList<String>(), failed)
    }

    /** What Primorska's small figures, drawn for the map's sizes before the sprites, draw otherwise (kept pixel for pixel). */
    private val primorskaOwn = setOf("beekeeper.trousers", "teacher.skirt", "burner.trousers")

    @Test fun `Primorska's small figures agree with its sprites in every mark they have`() {
        val failed = ArrayList<String>()
        for (art in arts - "woman" - "man") {
            val L = Looks.of(art, 0, "primorska"); val m = Effects.lookOf(art, 7, "primorska")
            fun check(what: String, ok: Boolean) { if (!ok && "$art.$what" !in primorskaOwn) failed += "$art.$what" }
            check("kerchief", m.kerchief == (L.hat == Looks.HAT_SCARF || L.hat == Looks.HAT_BANDANA))
            check("hat", (m.hat != 0) == (L.hat in setOf(Looks.HAT_FELT, Looks.HAT_CAP, Looks.HAT_STRAW, Looks.HAT_VEIL)))
            if (L.hat == Looks.HAT_FELT || L.hat == Looks.HAT_CAP) check("hat colour", m.hat == L.hatCol)
            check("straw", m.straw == (L.hat == Looks.HAT_STRAW))
            check("shirt", m.shirt == L.top || L.hat == Looks.HAT_VEIL)
            if (L.skirt) check("skirt", m.skirtColour == L.bottom) else check("trousers", m.pants == L.bottom)
            check("apron", m.apron == L.apron)
            check("dress", m.skirt == L.skirt)
            check("beard", m.beard == L.beard)
            check("child", m.small == L.child)
            check("prop", m.prop == Effects.PROPS[L.prop])
            check("load", m.pack == L.pack)
        }
        assertEquals(emptyList<String>(), failed)
    }

    @Test fun `the village's own people are dressed alike at every detail and in their scenes`() {
        for (culture in Wardrobes.ids) for (art in listOf("woman", "man")) {
            val looks = (0 until 12).map { Looks.of(art, Looks.seedOf("resident-$it"), culture) }
            assertTrue("$culture: newcomers don't all look alike", looks.toSet().size >= 6)
            for (i in 0 until 12) {
                val id = "resident-$i"
                val small = Effects.lookOf(art, Noise.hash(id.hashCode(), 13), culture, Looks.seedOf(id))
                val L = looks[i]
                assertEquals("$culture $id: the shirt", L.top, small.shirt)
                assertEquals("$culture $id: the kerchief", L.hat == Looks.HAT_SCARF, small.kerchief)
                assertEquals("$culture $id: the hat", if (L.hat == Looks.HAT_FELT || L.hat == Looks.HAT_CAP) L.hatCol else 0, if (small.kerchief) 0 else small.hat)
            }
        }
        // the unnamed: the same person at every detail (their clothes by the same seed)
        for (culture in Wardrobes.ids) for (seed in listOf(11, 4242, 98765)) {
            val art = Effects.anonArt(seed, culture)
            assertTrue(art == "woman" || art == "man")
            assertEquals(Looks.of(art, seed, culture).top, Effects.lookOf(null, seed, culture).shirt)
        }
    }

    @Test fun `render each culture's people at the map's three details by day and at night`() {
        for ((culture, land) in cultures) {
            val people = lineup(culture)
            val per = 8
            val s = village(land, Age.MESTO)
            val rows = ArrayList<List<Pair<PixelCanvas, Int>>>()
            // the lineup's box, nominal px: 104 × 84, its middle 40 px below the fire's
            val bw = 104; val bh = 84; val cy = 40
            for (night in listOf(false, true)) {
                val f = Frame(time = 3.0, hour = if (night) 22.5f else 11f, month = 6, today = "2026-06-20", culture = culture, placed = placed(people, per, night))
                // the 7 px figures (the whole map's lineup, six times bigger), the 14 px ones (three times), the sprites (twice)
                val comp = VillageLayout.composition(W, H, false)
                val k1 = render(s, f.copy(lens = Lens(1, 0, 0, W, H)), W, H)
                rows += listOf(
                    crop(k1, comp.fireX - bw / 2, comp.fireY + cy - bh / 2, bw, bh) to 6,
                    render(s, f.copy(lens = window(2, bw * 2, bh * 2, cy)), bw * 2, bh * 2) to 3,
                    render(s, f.copy(lens = window(3, bw * 3, bh * 3, cy)), bw * 3, bh * 3) to 2,
                )
            }
            write("00-cast-$culture", rows)
        }
    }

    @Test fun `render each culture's village with its people about their day`() {
        for ((culture, land) in cultures) {
            val people = cast(culture)
            val s = GameEngine.newGame(2026L, 0L, land).let { g ->
                val order = listOf(
                    si.lanisce.lani.game.BuildingType.TENT, si.lanisce.lani.game.BuildingType.FIELD, si.lanisce.lani.game.BuildingType.HUT,
                    si.lanisce.lani.game.BuildingType.KOZOLEC, si.lanisce.lani.game.BuildingType.WELL, si.lanisce.lani.game.BuildingType.HOUSE,
                    si.lanisce.lani.game.BuildingType.CHURCH, si.lanisce.lani.game.BuildingType.LIPA, si.lanisce.lani.game.BuildingType.WATCHTOWER,
                    si.lanisce.lani.game.BuildingType.SCHOOL, si.lanisce.lani.game.BuildingType.SMITHY, si.lanisce.lani.game.BuildingType.BEEHIVE,
                    si.lanisce.lani.game.BuildingType.MARKET, si.lanisce.lani.game.BuildingType.HOUSE,
                )
                val n = si.lanisce.lani.game.PLOTS_PER_AGE[Age.VAS.ordinal]
                g.copy(age = Age.VAS, villagers = 20, morale = 70, fire = 70, buildings = order.take(n).mapIndexed { i, t -> si.lanisce.lani.game.Building("b$i", t, i, level = 2) })
            }
            val rows = ArrayList<List<Pair<PixelCanvas, Int>>>()
            for (hour in listOf(10.5f, 20.75f)) {
                val f = Frame(time = 3.0, hour = hour, month = 6, today = "2026-06-20", culture = culture, people = people)
                val comp = VillageLayout.composition(W, H, false)
                val whole = render(s, f.copy(lens = Lens(1, 0, 0, W, H)), W, H)
                rows += listOf(
                    crop(whole, 0, comp.horizon - 20, W, 300) to 2,
                    render(s, f.copy(lens = window(2, 360, 300, 0)), 360, 300) to 2,
                    render(s, f.copy(lens = window(3, 360, 300, 0)), 360, 300) to 2,
                )
            }
            write("01-village-$culture", rows)
        }
    }
}
