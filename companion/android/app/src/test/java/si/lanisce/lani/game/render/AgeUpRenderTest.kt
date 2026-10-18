package si.lanisce.lani.game.render

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.AgeUps
import si.lanisce.lani.game.Beat
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType.CHURCH
import si.lanisce.lani.game.BuildingType.FIELD
import si.lanisce.lani.game.BuildingType.HOUSE
import si.lanisce.lani.game.BuildingType.HUT
import si.lanisce.lani.game.BuildingType.KOZOLEC
import si.lanisce.lani.game.BuildingType.LIPA
import si.lanisce.lani.game.BuildingType.MARKET
import si.lanisce.lani.game.BuildingType.PALISADE
import si.lanisce.lani.game.BuildingType.SCHOOL
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.Decor
import si.lanisce.lani.game.Festive
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Growth
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.Res
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * An age's moment on the map (GAME.md "Ages and their celebrations"): grown all the way it is the age as it always was,
 * pixel for pixel; at its start it is the age before (the forest, the road, the square, the fire as they were); each
 * celebration's decorations show and the sign stands at the road (its anchor known); the camera's shots fall on the
 * canvas. For review in build/village-snapshots/ageup-*.png (Zaselek → Vas and Vas → Trg, beat by beat).
 */
class AgeUpRenderTest {
    private val frame = Frame(time = 3.0, hour = 17.5f, month = 7)
    private val dir = File("build/village-snapshots").apply { mkdirs() }

    /** A village like Jan's when it grew to Vas: the church, the linden, a hut and a house, the field and its kozolec. */
    private val vas = GameState(
        seed = 42, age = Age.VAS, villagers = 9, morale = 70, fire = 70, resources = Res.entries.associateWith { 999 },
        buildings = listOf(
            Building("b0", TENT, NO_PLOT), Building("b1", FIELD, 1), Building("b2", HUT, 2, level = 2, builtAt = 10), Building("b3", WELL, 3),
            Building("b4", PALISADE, NO_PLOT), Building("b5", KOZOLEC, 8), Building("b7", CHURCH, 6), Building("b8", HOUSE, 0, builtAt = 20),
            Building("b9", LIPA, 4),
        ),
    )
    private val trg = vas.copy(age = Age.TRG, buildings = vas.buildings + Building("m", MARKET, 10) + Building("s", SCHOOL, 11))

    private fun render(s: GameState, f: Frame = frame, w: Int = 360, h: Int = 800, r: VillageRenderer = VillageRenderer()): PixelCanvas =
        PixelCanvas(w, h).also { r.render(it, s, f) }

    @Test fun `grown all the way, the age is as it always was`() {
        for (s in listOf(vas, trg)) {
            val from = Age.entries[s.age.ordinal - 1]
            assertArrayEquals("${s.age}", render(s).pixels, render(s, frame.copy(growth = Growth(from))).pixels)
        }
    }

    @Test fun `at the moment's start the map is the age before`() {
        // the forest, the road and the square as they were: the map differs from the grown one, and comes close to the age
        // before's (its plots and people are the new age's)
        val start = render(vas, frame.copy(growth = Growth(Age.ZASELEK, 0f, 0f, 0f, 0f, 0f, 0f)))
        val grown = render(vas)
        val before = render(vas.copy(age = Age.ZASELEK))
        val off = start.pixels.indices.count { start.pixels[it] != grown.pixels[it] }
        val near = start.pixels.indices.count { start.pixels[it] != before.pixels[it] }
        assertTrue("the start differs from the grown age: $off px", off > 2000)
        assertTrue("the start is near the age before: $near px vs $off", near < off)
    }

    @Test fun `each celebration's decorations show, and the sign at the road`() {
        val plain = render(vas)
        for (d in Decor.entries) {
            val f = render(vas, frame.copy(festive = Festive(listOf(d))))
            assertFalse("$d shows", plain.pixels.contentEquals(f.pixels))
        }
        val r = VillageRenderer()
        render(vas, frame.copy(festive = Festive(listOf(Decor.BUNTING), sign = 1f)), r = r)
        val sign = r.signAnchor
        assertNotNull(sign)
        assertTrue("on the canvas: $sign", sign!!.x in 0f..360f && sign.y in 0f..800f)
        render(vas, r = r)
        assertNull("no festive frame, no sign", r.signAnchor)
        // nothing up yet, nothing drawn
        assertArrayEquals(plain.pixels, render(vas, frame.copy(festive = Festive(listOf(Decor.STALLS), up = listOf(0f)))).pixels)
    }

    @Test fun `the moment's shots fall on the village`() {
        for ((s, from) in listOf(vas to Age.ZASELEK, trg to Age.VAS)) {
            val m = AgeUps.moment(from, s.age, s)
            for (b in m.beats) {
                val shot = MomentShots.of(b, m, s, 360, 800)
                val p = shot.at ?: continue
                assertTrue("${s.age} ${b.kind} ${b.change}: $p", p.x in 0f..360f && p.y in 0f..800f)
            }
            assertTrue(MomentShots.of(m.beats.first(), m, s, 360, 800).home)
            assertTrue(MomentShots.of(m.beats.last(), m, s, 360, 800).home)
        }
    }

    @Test fun `snapshots of the moments, beat by beat`() {
        for ((s, from) in listOf(vas to Age.ZASELEK, trg to Age.VAS)) {
            val m = AgeUps.moment(from, s.age, s)
            for ((i, b) in m.beats.withIndex()) {
                val t = (b.from + b.to) / 2 + if (b.kind == Beat.Kind.DECORATE) 1.0 else 0.0
                val f = m.at(t)
                val c = render(s, frame.copy(time = t, growth = f.growth, festive = f.festive))
                write(c, File(dir, "ageup-${s.age.name.lowercase()}-$i-${b.kind.name.lowercase()}${b.change?.let { "-" + it.name.lowercase() } ?: ""}.png"))
            }
        }
    }

    private fun write(c: PixelCanvas, f: File) {
        val img = BufferedImage(c.width * 2, c.height * 2, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * 2) for (x in 0 until c.width * 2) img.setRGB(x, y, c.pixels[(y / 2) * c.width + x / 2])
        ImageIO.write(img, "png", f)
    }
}
