package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.scene.SceneObject
import si.lanisce.lani.game.scene.SceneSpec
import java.io.File
import java.nio.file.Files

class SceneStoreTest {
    private val fire = SceneSpec(
        id = "ob-ognju", title = "Ob ognju", art = "campfire", from = listOf("fire"), pack = "ob-ognju",
        objects = listOf(SceneObject("fire", "ogenj", sl = "ogenj", en = "fire", gender = "m"), SceneObject("logs", "drva")),
    )

    @Test fun `things are found once, only the scene's own`() {
        val f1 = SceneFound.discover(emptyMap(), fire, "fire")
        assertEquals(mapOf("ob-ognju" to setOf("fire")), f1)
        assertSame(f1, SceneFound.discover(f1, fire, "fire"))
        assertSame(f1, SceneFound.discover(f1, fire, "moon")) // scenery, no word
        val f2 = SceneFound.discover(f1, fire, "logs")
        assertEquals(2, SceneFound.count(f2, fire))
        // A slot the scene dropped doesn't count.
        assertEquals(1, SceneFound.count(f2, fire.copy(objects = fire.objects.take(1))))
        assertEquals(0, SceneFound.count(f2, fire.copy(id = "other")))
    }

    @Test fun `found things, scenes and packs survive a restart`() {
        val dir = Files.createTempDirectory("scenes").toFile()
        try {
            val a = SceneStore(dir)
            assertTrue(a.readFound().isEmpty())
            assertNull(a.readScenes())
            assertTrue(a.readPacks().isEmpty())
            a.writeFound(mapOf("ob-ognju" to setOf("logs", "fire")))
            a.writeScenes(listOf(fire))
            a.writePacks(mapOf("ob-ognju" to Pack("ob-ognju", "Ob ognju", words = listOf(PackWord("ogenj", "ogenj", "fire")), learned = listOf("ogenj"))))
            val b = SceneStore(dir)
            assertEquals(mapOf("ob-ognju" to setOf("fire", "logs")), b.readFound())
            assertEquals(listOf(fire), b.readScenes())
            assertEquals(listOf("ogenj"), b.readPacks()["ob-ognju"]?.learned)
            assertFalse(File(dir, "scenes-found.json.tmp").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun `a broken cache reads as empty`() {
        val dir = Files.createTempDirectory("scenes").toFile()
        try {
            File(dir, "scenes-found.json").writeText("{not json")
            File(dir, "scenes.json").writeText("[{\"id\":1}]")
            assertTrue(SceneStore(dir).readFound().isEmpty())
            assertNull(SceneStore(dir).readScenes())
        } finally {
            dir.deleteRecursively()
        }
    }
}
