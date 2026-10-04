package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.scene.Wildlife.Part
import java.io.File
import java.time.LocalDate

/** The wild animals of the outdoor scenes ([Wildlife]): by the part of the day, the month and the day's dice. */
class WildlifeTest {
    private val day = LocalDate.of(2026, 6, 20).toEpochDay()

    @Test fun `the parts of the day follow the sun of the month`() {
        assertEquals(Part.DAWN, Wildlife.part(5.5f, 6))
        assertEquals(Part.DAY, Wildlife.part(13f, 6))
        assertEquals(Part.DUSK, Wildlife.part(20.5f, 6))
        assertEquals(Part.NIGHT, Wildlife.part(23.5f, 6))
        assertEquals(Part.NIGHT, Wildlife.part(2f, 6))
        // in December the day is short: 5.30 is still night, 17.00 already dusk or night
        assertEquals(Part.NIGHT, Wildlife.part(5.5f, 12))
        assertEquals(Part.DAWN, Wildlife.part(7.5f, 12))
        assertTrue(Wildlife.part(17.5f, 12) != Part.DAY)
    }

    @Test fun `each part of the day has its own animals`() {
        // over many days, who is ever out when
        fun ever(art: String, hour: Float, month: Int = 6) = (0 until 400).flatMap { Wildlife.out(art, 7, day + it, hour, month) }.toSet()
        assertEquals(setOf("hare", "butterfly"), ever("forest", 13f))
        assertEquals(setOf("hare", "fox", "stag"), ever("forest", 5.6f))
        assertEquals(setOf("hare", "fox", "stag", "hedgehog", "bat"), ever("forest", 20.5f))
        assertEquals(setOf("hedgehog", "boar", "badger", "dormouse", "bat", "bear"), ever("forest", 23.5f))
        // winter: the hedgehog, the dormouse, the bats and the bear sleep, the butterflies are gone
        assertEquals(setOf("boar"), ever("forest", 23.5f, 1))
        assertEquals(setOf("hare"), ever("forest", 12.5f, 1))
        assertEquals(setOf("chamois", "ibex"), ever("alps", 13f))
        assertTrue(ever("kitchen", 13f).isEmpty())
        // every animal that is a word is an object slot of its art, and the scenes' files name each
        for ((art, animals) in Wildlife.animals) for (a in animals.filter { it.word }) assertTrue("$art/${a.id}", a.id in SceneArt.objects.getValue(art))
    }

    @Test fun `the mix changes from day to day and stays all through a part of the day`() {
        val mixes = (0 until 30).map { Wildlife.out("forest", 7, day + it, 23.5f, 6) }
        assertTrue("different nights, different animals: ${mixes.toSet().size}", mixes.toSet().size >= 6)
        for (k in 0 until 30) {
            // all night long, over midnight: the same animals (the night after midnight is the evening's)
            val evening = Wildlife.out("forest", 7, day + k, 23.2f, 6)
            assertEquals(evening, Wildlife.out("forest", 7, day + k, 22.4f, 6))
            assertEquals(evening, Wildlife.out("forest", 7, day + k + 1, 2.5f, 6))
            assertEquals(Wildlife.spot("forest", "boar", 7, day + k, 23.2f, 6), Wildlife.spot("forest", "boar", 7, day + k + 1, 1f, 6))
        }
        // the same day in the same village is the same; another village has its own
        assertEquals(Wildlife.out("field", 7, day, 21f, 7), Wildlife.out("field", 7, day, 21f, 7))
        assertNotEquals((0 until 20).map { Wildlife.out("forest", 7, day + it, 23.5f, 6) }, (0 until 20).map { Wildlife.out("forest", 8, day + it, 23.5f, 6) })
        // places vary too
        assertTrue((0 until 20).map { Wildlife.spot("forest", "stag", 7, day + it, 5.5f, 6) }.toSet().size > 10)
    }

    @Test fun `the rare ones are rare`() {
        val nights = (0 until 2000).map { Wildlife.out("forest", 7, day + it, 23.5f, 7) }
        val bear = nights.count { "bear" in it } / 2000f
        val boar = nights.count { "boar" in it } / 2000f
        assertTrue("the bear on $bear of the nights", bear in 0.01f..0.06f)
        assertTrue("the boar on $boar of the nights", boar in 0.4f..0.6f)
    }

    @Test fun `the scenes name their animals as words, each in a pack`() {
        val companion = listOf(File("../.."), File("..")).first { it.resolve("scenes").isDirectory }
        val files = mapOf(
            "forest" to companion.resolve("scenes/v-gozdu.json"), "field" to companion.resolve("scenes/na-njivi.json"),
            "pond" to companion.resolve("scenes/pri-ribniku.json"), "stream" to companion.resolve("scenes/ob-potoku.json"),
            "alps" to companion.resolve("cultures/primorska/scenes/v-gorah.json"),
        )
        for ((art, f) in files) {
            val s = parseScene(f.readText())
            assertEquals(art, s.art)
            assertTrue("${s.id}: ${Wildlife.slots(art) - s.objects.map { it.slot }.toSet()}", s.objects.map { it.slot }.containsAll(Wildlife.slots(art)))
        }
    }
}
