package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.CultureLooks
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFixtures
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Every building's scene shows the building at its level (companion/SCENES.md, "What the village has built": "every
 * building art shows its level"): a building that opens into a scene (the scenes' `from`, every culture's, so a new art
 * comes in here as its scene does) is drawn at each of its levels 1 to [Catalog.MAX_LEVEL] in its art, and each level
 * looks visibly different from the one before ([VISIBLE] pixels at least of the scene's canvas by day, the room without
 * people), the top level is the picture a village with everything has ([SceneWorld.ALL], so the snapshots and the
 * stickers stay as they were), and what can be tapped at a level is what the level has ([SceneFixtures]). In the default
 * culture and in every culture whose look draws the art its own way. Contact sheets in build/scene-snapshots/levels: a
 * row a level, a column a culture.
 */
class BuildingLevelsTest {
    private val dir = File("build/scene-snapshots/levels").apply { mkdirs() }
    private val arts: Map<BuildingType, Set<String>> get() = buildingArts

    /** The cultures each art is drawn in here: the default, and every culture whose look draws it its own way. */
    private fun cultures(art: String): List<String> =
        listOf(si.lanisce.lani.game.culture.Cultures.DEFAULT) + CultureLooks.touched.filter { art in it.value }.keys.sorted()

    private fun world(type: BuildingType, level: Int?, culture: String) =
        SceneWorld(culture = culture, here = level?.let { Building("here", type, 0, level = it) })

    private fun frame(art: String, world: SceneWorld, hour: Float = 11f, month: Int = 6) =
        SceneFrame(time = 3.0, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), world = world)

    private fun render(art: String, f: SceneFrame, w: Int = 240, h: Int = 160): Pair<PixelCanvas, List<si.lanisce.lani.game.scene.SceneHit>> {
        val c = PixelCanvas(w, h)
        val hits = ScenePainters.create(art).render(c, f, Lens(1, 0, 0, w, h))
        return c to hits
    }

    private fun changed(a: PixelCanvas, b: PixelCanvas): Int = a.pixels.indices.count { a.pixels[it] != b.pixels[it] }

    @Test fun `every building that opens into a scene is in here`() {
        // the buildings with a room of their own in a culture's village (companion/SCENES.md): the palisade's comes with its gate
        val expected = BuildingType.entries.toSet() - BuildingType.PALISADE
        assertTrue("buildings without a scene: ${expected - arts.keys}", arts.keys.containsAll(expected))
    }

    @Test fun `each level of a building shows in its scene, the top one as everything built`() {
        val failed = ArrayList<String>()
        val report = StringBuilder()
        for ((type, list) in arts.toSortedMap()) for (art in list - PENDING) for (culture in cultures(art)) {
            val pics = (1..Catalog.MAX_LEVEL).map { render(art, frame(art, world(type, it, culture))) }
            val counts = (1 until pics.size).map { changed(pics[it - 1].first, pics[it].first) }
            report.append("$type/$art/$culture: ${counts.joinToString(" ")}\n")
            counts.forEachIndexed { i, n -> if (n < VISIBLE) failed += "$type in $art ($culture): level ${i + 2} differs from level ${i + 1} in $n pixels (at least $VISIBLE)" }
            val all = render(art, frame(art, world(type, null, culture))).first
            if (changed(all, pics.last().first) != 0) failed += "$type in $art ($culture): the top level isn't the picture of everything built"
            // what can be tapped is what the level has
            for ((i, p) in pics.withIndex()) {
                val w = world(type, i + 1, culture)
                for (slot in SceneArt.objects.getValue(art)) {
                    if (art in WILD_ARTS && slot in WILD) continue
                    val n = p.second.count { it.target == SceneTarget.Thing(slot) }
                    val want = if (SceneFixtures.there(art, slot, w)) 1 else 0
                    if (n != want) failed += "$type in $art ($culture) at level ${i + 1}: $slot tappable $n times, not $want"
                }
            }
        }
        println(report)
        assertEquals(emptyList<String>(), failed)
    }

    @Test fun `render every building's levels`() {
        val s = 2; val label = 16
        for ((type, list) in arts.toSortedMap()) for (art in list) {
            val cs = cultures(art)
            for ((tag, hour, month) in listOf(Triple("day", 11f, 6), Triple("evening", 20.5f, 10))) {
                val img = BufferedImage(240 * s * cs.size, (160 * s + label) * Catalog.MAX_LEVEL, BufferedImage.TYPE_INT_RGB)
                val g = img.createGraphics()
                for ((ci, culture) in cs.withIndex()) for (level in 1..Catalog.MAX_LEVEL) {
                    val c = render(art, frame(art, world(type, level, culture), hour, month)).first
                    val ox = ci * 240 * s; val oy = (level - 1) * (160 * s + label)
                    for (y in 0 until 160 * s) for (x in 0 until 240 * s) img.setRGB(ox + x, oy + label + y, c.pixels[(y / s) * 240 + x / s])
                    g.color = Color.WHITE
                    g.drawString("${type.name.lowercase()} in $art, $culture, level $level, $tag", ox + 4, oy + 12)
                }
                g.dispose()
                ImageIO.write(img, "png", File(dir, "${type.name.lowercase()}-$art-$tag.png"))
            }
        }
    }

    companion object {
        private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "scenes").isDirectory }

        /** Building type → the arts of the scenes it opens, as every culture's scenes have it (`from`). */
        val buildingArts: Map<BuildingType, Set<String>> by lazy {
            val files = (File(companion, "scenes").walkTopDown() + File(companion, "cultures").walkTopDown())
                .filter { it.isFile && it.name.endsWith(".json") && (it.parentFile.name == "scenes" || it.parentFile.parentFile?.name == "scenes") }
            val out = HashMap<BuildingType, MutableSet<String>>()
            for (f in files) {
                val o = runCatching { json.parseToJsonElement(f.readText()).jsonObject }.getOrNull() ?: continue
                if (o["schema"]?.jsonPrimitive?.content != "lani.scene/v0") continue
                val art = o["art"]?.jsonPrimitive?.content ?: continue
                if (art !in SceneArt.arts) continue
                for (from in o["from"]?.jsonArray.orEmpty()) {
                    val t = BuildingType.entries.firstOrNull { it.name.equals(from.jsonPrimitive.content, ignoreCase = true) } ?: continue
                    out.getOrPut(t) { sortedSetOf() } += art
                }
            }
            out
        }

        /** How many pixels of the 240 × 160 canvas a level changes at least, by day: a thing a child sees come with it. */
        const val VISIBLE = 300

        /** The arts whose levels are still being drawn this round (places-it-buildings): none at its end. */
        private val PENDING = setOf(
            "apiary",
            "attic",
            "cellar",
            "church",
            "field",
            "kitchen",
            "livingroom",
            "tent",
            "watchtower",
            "workshop",
        )

        /** The arts whose wild animals come and go with the day (Wildlife): not counted as a level's things. */
        private val WILD_ARTS = setOf("field", "forest", "stream", "pond", "alps")
        private val WILD = setOf("hare", "butterfly", "fox", "stag", "hedgehog", "boar", "badger", "dormouse", "bat", "deer", "chamois", "ibex")
    }
}
