package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.FrameBudget
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Decor
import si.lanisce.lani.game.render.Env
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.FireCrowd
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.Stance
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/**
 * The fire's scene as the village grows (companion/SCENES.md "Ob ognju by the age"): every age of every culture rendered
 * for review (build/scene-snapshots/fire-ages: each age by day, in the evening with the village round the fire, at night in
 * winter, in an autumn morning; contact sheets; every age at every detail), and checked: each age looks different from
 * the one before, every thing of the scene is there to tap at every age, the stage's spots and the crowd's are where
 * someone can stand or sit with nothing drawn over their head, the kept still picture is the picture, the moon keeps above
 * the roofs, the fire burns by the hour, and a frame stays fast.
 */
class FireAgesTest {
    private val dir = File("build/scene-snapshots/fire-ages").apply { mkdirs() }

    private val summer = LocalDate.of(2026, 7, 15)
    private val winter = LocalDate.of(2026, 1, 15)
    private val autumn = LocalDate.of(2026, 10, 20)

    private val cultures = listOf("primorska", "friuli", "kaernten", "lakeland")

    /** The buildings of a village of each age, as QA's "hearths:<age>" builds it (the tent moved to the pond from the hamlet on). */
    private fun types(age: Age): List<BuildingType> {
        val b = BuildingType.entries.associateBy { it.name }
        fun of(vararg n: String) = n.map { b.getValue(it) }
        val tabor = of("TENT", "FIELD", "HUT", "KOZOLEC")
        val zaselek = of("FIELD", "HUT", "KOZOLEC", "LIPA", "HUT", "CHURCH", "SMITHY", "SCHOOL", "HOUSE")
        val vas = zaselek + of("WELL", "MARKET", "HOUSE", "HOUSE")
        val trg = vas + of("HOUSE", "BEEHIVE", "HOUSE", "WATCHTOWER", "HOUSE", "FIELD")
        return when (age) {
            Age.OGENJ -> emptyList()
            Age.TABOR -> tabor
            Age.ZASELEK -> zaselek
            Age.VAS -> vas
            Age.TRG -> trg
            Age.MESTO -> trg + of("HOUSE", "HOUSE", "BEEHIVE", "KOZOLEC")
        }
    }

    /** The town projects as far as each age has them: the fountain from the market town, the clock half made there, finished in the town. */
    private fun projects(age: Age): Map<String, Int> = when (age) {
        Age.TRG -> mapOf("vodnjak_na_trgu" to 6, "mestna_ura" to 3)
        Age.MESTO -> mapOf("vodnjak_na_trgu" to 6, "mestna_ura" to 7)
        else -> emptyMap()
    }

    /** A village of [age] of [culture] on [date], the linden planted [linden] days before; with the camp's [tent] standing or not. */
    private fun world(
        age: Age, culture: String = "primorska", date: LocalDate = summer, tent: Boolean? = null, landscape: String = "classic",
        centre: SceneWorld.Centre = SceneWorld.Centre(), linden: Long = 200,
    ): SceneWorld {
        val ts = types(age).let { t -> if (tent == true && BuildingType.TENT !in t) listOf(BuildingType.TENT) + t else if (tent == false) t - BuildingType.TENT else t }
        val built = ts.mapIndexed { i, t -> Building("${t.name.lowercase()}-$i", t, i, level = 2, builtAt = i.toLong()) }
        return SceneWorld(
            age = age, buildings = built.groupBy { it.type }.mapValues { (_, bs) -> bs.maxOf { it.level } }, projects = projects(age),
            homes = built.filter { it.type == BuildingType.HUT || it.type == BuildingType.HOUSE }, lindenDay = date.toEpochDay() - linden,
            centre = centre, culture = culture, landscape = landscape,
        )
    }

    private fun frame(world: SceneWorld, hour: Float, date: LocalDate = summer, people: List<PersonInScene> = emptyList(), time: Double = 3.0, fx: Map<String, Float> = emptyMap()) = SceneFrame(
        time = time, hour = hour, month = date.monthValue, objects = SceneArt.objects.getValue("campfire").toSet(), people = people,
        world = world, date = date, fx = fx,
    )

    private fun render(f: SceneFrame, w: Int = 270, h: Int = 297, k: Int = 1, painter: ArtPainter = ScenePainters.create("campfire") as ArtPainter): Pair<PixelCanvas, List<SceneHit>> {
        val c = PixelCanvas(w * k, h * k)
        val hits = painter.render(c, f, Lens(k, 0, 0, w, h))
        return c to hits
    }

    /** The scene's own people at the stage's seats and the children's place, and the village's others at every crowd spot of [world]. */
    private fun gathered(world: SceneWorld): List<PersonInScene> {
        val crowd = FireLayout.of(world, null).crowd.indices.map { i ->
            PersonInScene("v-$i", SceneArt.people[(i * 5 + 3) % 18], "${FireCrowd.PREFIX}${i + 1}")
        }
        return listOf(
            PersonInScene("babica", "grandma", "left"), PersonInScene("janez", "grandpa", "right", talking = true), PersonInScene("otroci", "child1", "back"),
        ) + crowd
    }

    // ------------------------------------------------------------------ for review

    @Test fun `render every age of every culture`() {
        for (culture in cultures) {
            val views = listOf(
                "day" to { a: Age -> frame(world(a, culture), 14f) },
                "evening" to { a: Age -> world(a, culture).let { w -> frame(w, 20.5f, people = gathered(w)) } },
                "winter-night" to { a: Age -> frame(world(a, culture, winter), 22.5f, winter) },
                "autumn-morning" to { a: Age -> frame(world(a, culture, autumn), 8f, autumn) },
            )
            val bw = 270; val bh = 297; val sc = 2
            val img = BufferedImage(bw * sc * views.size, bh * sc * Age.entries.size, BufferedImage.TYPE_INT_RGB)
            for ((ai, age) in Age.entries.withIndex()) for ((vi, v) in views.withIndex()) {
                val (c, _) = render(v.second(age), bw, bh)
                write(c, File(dir, "$culture-${age.name.lowercase()}-${v.first}.png"), 4)
                for (y in 0 until bh * sc) for (x in 0 until bw * sc) img.setRGB(vi * bw * sc + x, ai * bh * sc + y, c.pixels[(y / sc) * bw + x / sc])
            }
            ImageIO.write(img, "png", File(dir, "00-$culture-ages.png"))
        }
        // Teo's coast: the friuli village by the sea, the lagoon's thatched huts
        for (age in listOf(Age.TABOR, Age.ZASELEK, Age.VAS)) write(render(frame(world(age, "friuli", landscape = "coast"), 17.5f)).first, File(dir, "friuli-coast-${age.name.lowercase()}.png"), 4)
        // a tablet on its side, every age
        val wide = BufferedImage(360 * 2 * 3, 200 * 2 * 2, BufferedImage.TYPE_INT_RGB)
        for ((i, age) in Age.entries.withIndex()) {
            val (c, _) = render(world(age).let { w -> frame(w, 19.8f, people = gathered(w)) }, 360, 200)
            for (y in 0 until 400) for (x in 0 until 720) wide.setRGB((i % 3) * 720 + x, (i / 3) * 400 + y, c.pixels[(y / 2) * 360 + x / 2])
        }
        ImageIO.write(wide, "png", File(dir, "00-tablet-ages.png"))
    }

    @Test fun `render every age at every detail`() {
        val bw = 270; val bh = 297
        for (age in Age.entries) for (k in 1..Lens.MAX_DETAIL) {
            val w = world(age)
            write(render(frame(w, 19.5f, people = gathered(w)), bw, bh, k).first, File(dir, "detail-${age.name.lowercase()}-d$k.png"), 6 / k)
        }
    }

    @Test fun `render the feasts and the fire by the hour`() {
        val vas = world(Age.VAS)
        val shots = listOf(
            "tabor-torches" to frame(world(Age.TABOR, centre = SceneWorld.Centre(decor = listOf(Decor.TORCHES))), 20f),
            "zaselek-likof" to frame(world(Age.ZASELEK, centre = SceneWorld.Centre(decor = listOf(Decor.SPRUCE))), 15f),
            "vas-veselica" to frame(world(Age.VAS, centre = SceneWorld.Centre(decor = listOf(Decor.BUNTING, Decor.FLOOR), party = true, unnamed = 6)), 21f),
            "trg-kres" to frame(world(Age.TRG, centre = SceneWorld.Centre(feast = true, unnamed = 6)), 21f),
            "mesto-banners" to frame(world(Age.MESTO, centre = SceneWorld.Centre(decor = listOf(Decor.BANNERS))), 16f),
            "vas-morning" to frame(vas, 7.5f), "vas-noon" to frame(vas, 14f), "vas-lunch" to frame(vas, 12.8f), "vas-evening" to frame(vas, 19f),
            "vas-out" to frame(world(Age.VAS, centre = SceneWorld.Centre(out = true)), 19f),
            "tabor-morning" to frame(world(Age.TABOR), 7.5f), "tabor-noon" to frame(world(Age.TABOR), 13f), "tabor-evening" to frame(world(Age.TABOR), 20f),
            "vas-rain" to frame(vas, 16f).copy(sky = si.lanisce.lani.game.scene.Sky(rain = 0.8f, gloom = 0.7f)),
            "vas-snow" to frame(world(Age.VAS, date = winter), 15f, winter).copy(sky = si.lanisce.lani.game.scene.Sky(snow = 0.8f, gloom = 0.4f)),
            "mesto-young-linden" to frame(world(Age.MESTO, linden = 5), 15f),
            "vas-tent" to frame(world(Age.VAS, tent = true), 15f),
            "mesto-tent" to world(Age.MESTO, tent = true).let { w -> frame(w, 20f, people = gathered(w)) },
        )
        for ((name, f) in shots) write(render(f).first, File(dir, "$name.png"), 4)
    }

    // ------------------------------------------------------------------ the checks

    @Test fun `each age looks different from the one before`() {
        for (culture in cultures) {
            var before: IntArray? = null
            for (age in Age.entries) {
                val px = render(frame(world(age, culture), 14f)).first.pixels
                // the camp stays the camp at Tabor (its tent up); from the hamlet on each age is a new picture
                val share = if (age == Age.TABOR) 200 else 20
                before?.let { b -> assertTrue("$culture ${age.name}: like the age before", px.indices.count { px[it] != b[it] } > px.size / share) }
                before = px
            }
        }
    }

    @Test fun `every thing of the scene is there to tap at every age`() {
        val failed = ArrayList<String>()
        for (culture in cultures) for (age in Age.entries) for ((w, h) in listOf(240 to 160, 270 to 297, 360 to 200)) for (hour in listOf(10f, 23f)) {
            val world = world(age, culture)
            val hits = render(frame(world, hour), w, h).second
            val env = Env(hour, summer.monthValue, false)
            for (slot in SceneArt.objects.getValue("campfire")) {
                val expect = when (slot) {
                    "stars" -> env.dark > 0.3f
                    "moon" -> env.moonShows
                    "tent" -> si.lanisce.lani.game.scene.SceneFixtures.there("campfire", "tent", world)
                    else -> true
                }
                val n = hits.count { it.target == SceneTarget.Thing(slot) }
                if (n != (if (expect) 1 else 0)) failed += "$culture ${age.name} ${w}x$h at $hour h: $slot $n"
            }
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
    }

    @Test fun `the stage's spots and the crowd's are where someone can be at every age`() {
        val failed = ArrayList<String>()
        val stances = listOf(
            "left" to Stance.SIT, "right" to Stance.SIT, "back" to Stance.STAND,
            "behind-stump" to Stance.STAND, "behind-stump" to Stance.PEEK, "behind-logs" to Stance.STAND, "behind-logs" to Stance.PEEK,
        )
        for (culture in cultures) for (age in Age.entries) for (tent in listOf(false, true)) for (hour in listOf(15f, 22.5f)) {
            val world = world(age, culture, tent = tent)
            val crowd = FireLayout.of(world, null).crowd.indices.map { "${FireCrowd.PREFIX}${it + 1}" to Stance.STAND }
            for ((spot, stance) in stances + crowd) for (sprite in listOf("grandpa", "child1", "pedlar")) {
                val p = ScenePainters.create("campfire") as ArtPainter
                p.keepPeople = true
                val person = PersonInScene("p", sprite, spot, stance = stance)
                val (c, hits) = render(frame(world, hour, people = listOf(person)), painter = p)
                val what = "$culture ${age.name}${if (tent) " (tent)" else ""} at $hour h: $sprite at $spot ($stance)"
                val hit = hits.firstOrNull { it.target == SceneTarget.Person("p") }
                if (hit == null) { failed += "$what: no hit"; continue }
                val hh = hit.bottom - hit.top; val ww = hit.right - hit.left
                if (hh !in 12..46 || ww !in 8..30) failed += "$what: not person-sized $hit"
                val d = p.drawnPeople.firstOrNull { it.id == "p" } ?: run { failed += "$what: not drawn"; null } ?: continue
                val top = d.at.minOf { it / c.width }; val bottom = d.at.maxOf { it / c.width }
                val line = top + ((bottom - top + 1) * 0.4f).toInt()
                // behind the stump they crouch behind it, its edge over their shoulder (as at the camp, by design)
                val over = if (spot == "behind-stump") 0 else d.covered.count { it / c.width < line }
                if (over > 0) failed += "$what: $over px of the head drawn over"
            }
        }
        assertTrue(failed.take(60).joinToString("\n", prefix = "\n") + (if (failed.size > 60) "\n… ${failed.size} in all" else ""), failed.isEmpty())
    }

    @Test fun `the village round the fire leaves the fire to tap and everyone in view`() {
        val failed = ArrayList<String>()
        for (culture in cultures) for (age in Age.entries) for (tent in listOf(false, true)) {
            val world = world(age, culture, tent = tent)
            val people = gathered(world)
            val p = ScenePainters.create("campfire") as ArtPainter
            p.keepPeople = true
            val (c, hits) = render(frame(world, 20f, people = people), painter = p)
            val what = "$culture ${age.name}${if (tent) " (tent)" else ""}"
            for (target in listOf("fire", "stones", "kettle")) if (hits.none { it.target == SceneTarget.Thing(target) }) failed += "$what: the $target hidden"
            for (person in people) {
                val d = p.drawnPeople.firstOrNull { it.id == person.id }
                if (d == null) { failed += "$what: ${person.id} at ${person.slot} not drawn"; continue }
                // nobody hides another: at most a third of someone's sprite (standing behind a sitter: their legs) under
                // what is drawn after them, nothing over their head
                val top = d.at.minOf { it / c.width }; val bottom = d.at.maxOf { it / c.width }
                val head = d.covered.count { it / c.width < top + (bottom - top + 1) * 0.4f }
                if (d.covered.size > d.at.size / 3 || head > 0) failed += "$what: ${person.id} at ${person.slot} ${d.covered.size} of ${d.at.size} px drawn over ($head of the head)"
            }
            // the fire's flames show: its pixels are more the fire's than anyone's
            val fire = hits.first { it.target == SceneTarget.Thing("fire") }
            val own = (fire.top until fire.bottom).sumOf { y -> (fire.left until fire.right).count { x -> p.targetAt(c, x, y, 0) == SceneTarget.Thing("fire") } }
            if (own < (fire.bottom - fire.top) * (fire.right - fire.left) / 6) failed += "$what: the fire mostly hidden ($own px of its own)"
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
    }

    @Test fun `the crowd's spots are as many as the village has people for`() {
        for (age in Age.entries) for (tent in listOf(false, true)) {
            val w = world(age, tent = tent)
            val standing = si.lanisce.lani.game.scene.SceneFixtures.there("campfire", "tent", w)
            assertEquals("${age.name} tent $standing", FireCrowd.count(age, standing), FireLayout.of(w, null).crowd.size)
        }
    }

    @Test fun `the kept still picture is the picture`() {
        for (age in Age.entries) for (k in 1..Lens.MAX_DETAIL) {
            val world = world(age)
            val kept = ScenePainters.create("campfire") as ArtPainter
            val lens = Lens(k, if (k == 1) 0 else 40 * k, if (k == 1) 0 else 30 * k, 270, 297)
            val cw = if (k == 1) 270 else 200; val ch = if (k == 1) 297 else 180
            val people = gathered(world)
            val a = PixelCanvas(cw, ch)
            for (i in 0 until 4) {
                val f = frame(world, 19.6f, people = people, time = 2.0 + i * 0.37)
                val hitsA = kept.render(a, f, lens)
                if (i > 0) assertTrue("${age.name} d$k: the still picture kept", kept.stillKept)
                val b = PixelCanvas(cw, ch)
                val fresh = ScenePainters.create("campfire") as ArtPainter
                fresh.keepStill = false
                val hitsB = fresh.render(b, f, lens)
                assertArrayEquals("${age.name} d$k frame $i: pixels", b.pixels, a.pixels)
                assertEquals("${age.name} d$k frame $i: hits", hitsB, hitsA)
            }
        }
    }

    @Test fun `the moon keeps above the roofs while it is up`() {
        val failed = ArrayList<String>()
        for (culture in cultures) for (age in Age.entries) for ((w, h) in listOf(240 to 160, 270 to 297, 360 to 200)) for (hour in listOf(20f, 23f, 1.2f, 6f)) {
            val f = frame(world(age, culture), hour, date = LocalDate.of(2026, 9, 26)).copy(moon = si.lanisce.lani.game.render.Moon.FULL, month = 9)
            if (!Env(hour, 9, false).moonShows) continue
            val (_, hits) = render(f, w, h)
            if (hits.none { it.target == SceneTarget.Thing("moon") }) failed += "$culture ${age.name} ${w}x$h at $hour h"
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
    }

    /** How many glowing pixels round the fire there are (its flames, its coals, its kres). */
    private fun flame(f: SceneFrame): Int {
        val (c, hits) = render(f)
        val fire = hits.first { it.target == SceneTarget.Thing("fire") }
        var n = 0
        for (y in fire.top until fire.bottom) for (x in fire.left until fire.right) if (c.emissive[y * c.width + x]) n++
        return n
    }

    @Test fun `the fire burns by the hour, the age and the day`() {
        // the camp's campfire: low in the morning, built up in the evening, let die down in the summer's noon
        val tabor = world(Age.TABOR)
        assertTrue("the camp's fire higher in the evening than in the morning", flame(frame(tabor, 20f)) > flame(frame(tabor, 7.5f)) * 1.3)
        assertTrue("the camp's fire low in the summer's noon", flame(frame(tabor, 13.5f)) < flame(frame(tabor, 10f)))
        assertTrue("never quite out", flame(frame(tabor, 13.5f)) > 0)
        // the village's fire ring: lit for the evening, its coals by day, out in the summer's noon and without wood
        val vas = world(Age.VAS)
        val evening = flame(frame(vas, 19f))
        assertTrue("the ring's evening fire over its coals by day", evening > flame(frame(vas, 10f)) * 2)
        assertTrue("its coals glow by day", flame(frame(vas, 10f)) > 0)
        assertEquals("out in the summer's noon", 0, flame(frame(vas, 14f)))
        assertTrue("low flames for the lunch by the fire", flame(frame(vas, 12.8f)) > flame(frame(vas, 10f)))
        assertEquals("out without wood", 0, flame(frame(world(Age.VAS, centre = SceneWorld.Centre(out = true)), 19f)))
        assertTrue("someone by it keeps it going", flame(frame(vas, 14f, people = listOf(PersonInScene("a", "grandma", "left")))) > 0)
        assertTrue("a dialog lights it again", flame(frame(world(Age.VAS, centre = SceneWorld.Centre(out = true)), 19f, fx = mapOf("flare" to 0.8f))) > 0)
        // the market town's kres on a feast evening
        val trg = world(Age.TRG)
        assertTrue("the kres burns higher than the basket's fire", flame(frame(world(Age.TRG, centre = SceneWorld.Centre(feast = true)), 21f)) > flame(frame(trg, 21f)) * 1.3)
    }

    @Test fun `a frame renders fast at every age`() {
        val failed = ArrayList<String>()
        for (age in Age.entries) for (k in 1..Lens.MAX_DETAIL) {
            val world = world(age)
            val f = frame(world, 21.5f, people = gathered(world))
            val bw = 360; val bh = 240
            val lens = if (k == 1) Lens(1, 0, 0, bw, bh) else Lens(k, (bw * k - bw) / 2, (bh * k - bh) / 2, bw, bh)
            fun ms(keep: Boolean): Double {
                val p = ScenePainters.create("campfire") as ArtPainter
                p.keepStill = keep
                val c = PixelCanvas(bw, bh)
                repeat(8) { p.render(c, f.copy(time = it / 12.0), lens) }
                val n = 24
                val t0 = System.nanoTime()
                repeat(n) { p.render(c, f.copy(time = 2 + it / 12.0), lens) }
                return (System.nanoTime() - t0) / 1e6 / n
            }
            val drawn = ms(false); val kept = ms(true)
            println("Fire ${age.name} at detail $k: %.2f ms/frame kept, %.2f ms drawn whole (night, %dx%d, the village round the fire)".format(kept, drawn, bw, bh))
            if (kept > FrameBudget.scaled(if (k == 1) 20.0 else 25.0)) failed += "${age.name} d$k: $kept ms"
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
    }

    @Test fun `every culture has its own village round the fire`() {
        for (age in listOf(Age.VAS, Age.MESTO)) {
            val pics = cultures.map { render(frame(world(age, it), 14f)).first.pixels.toList() }
            assertEquals("${age.name}: four cultures, four villages", cultures.size, pics.toSet().size)
        }
        assertTrue("Teo's coast", render(frame(world(Age.ZASELEK, "friuli", landscape = "coast"), 14f)).first.pixels.toList() != render(frame(world(Age.ZASELEK, "friuli"), 14f)).first.pixels.toList())
    }

    @Test fun `the linden grows over the years`() {
        val young = render(frame(world(Age.VAS, linden = 10), 14f)).first.pixels
        val grown = render(frame(world(Age.VAS, linden = 2000), 14f)).first.pixels
        assertTrue(young.indices.count { young[it] != grown[it] } > 300)
        assertTrue(FireLayout.growth(10) < FireLayout.growth(400) && FireLayout.growth(400) < FireLayout.growth(2000))
    }

    private fun write(c: PixelCanvas, f: File, scale: Int) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", f)
    }
}
