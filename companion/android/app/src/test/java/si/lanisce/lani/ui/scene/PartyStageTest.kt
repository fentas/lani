package si.lanisce.lani.ui.scene

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import si.lanisce.lani.game.Decor
import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Festive
import si.lanisce.lani.game.render.Flag
import si.lanisce.lani.game.render.Pal

/**
 * A party's stage in the culture's colours (companion/GAME.md "Ages and their celebrations", [partyFestive]): its garlands,
 * the spruce's ribbons, the stalls' awnings and whistles and its banners read the [Festive] the map's feasts read, so a
 * village in another culture doesn't party under Slovenia's flag; Primorska's stage draws what it always drew.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PartyStageTest {
    private fun opaque(rgb: Int): Int = Col.hex(rgb.toLong())

    private val w = 400
    private val h = 176

    private fun render(draw: DrawScope.() -> Unit): IntArray {
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat()), draw)
        return IntArray(w * h).also { image.readPixels(it) }
    }

    private fun stage(f: Festive, vararg decor: Decor) = render {
        partyDecor(decor.toList(), f, night = false, t = 0f, still = true)
        garlands(f, night = false, t = 0f, still = true)
    }

    private val slovene = setOf(Pal.FLAG_RED, Pal.FLAG_BLUE)
    private val others = listOf(Festive.of("friuli"), Festive.of("austria"), Festive.of("union"))

    @Test fun `Primorska's stage keeps its colours`() {
        val own = Festive.OWN
        assertEquals(listOf(0xE4002B, 0xF7F9FD, 0x0B4EA2, 0xFFB300, 0x3EA05A).map { opaque(it) }, own.bunting.toList())
        assertEquals(listOf(0xE4002B, 0x0B4EA2, 0xFFB300).map { opaque(it) }, own.ribbons.filter { it != Pal.FLAG_WHITE }.take(3))
        assertEquals(listOf(0xC8303A, 0xF2EEE4).map { opaque(it) }, own.stageStalls.toList())
        assertEquals(listOf(0x3EA05A, 0xFFB300, 0x0B4EA2).map { opaque(it) }, own.bunting.reversed().take(3))
        assertEquals(Flag.SLOVENIA, own.banner)
    }

    @Test fun `no other culture's banners, garlands or awnings fly Slovenia's colours`() {
        for (f in others) {
            val banners = stage(f, Decor.BANNERS)
            assertTrue("${f.banner}: its flag is drawn", banners.toSet().contains(f.banner.at(0, 0, 3, 6)))
            assertFalse("${f.banner}: Slovenia's flag", banners.toSet().any { it in slovene })
            assertFalse("${f.banner}: stage", stage(f, Decor.STALLS, Decor.SPRUCE).toSet().any { it in slovene })
        }
    }

    @Test fun `each culture's stage is its own`() {
        val pictures = (others + Festive.OWN).map { stage(it, Decor.BANNERS, Decor.STALLS, Decor.SPRUCE) }
        for (i in pictures.indices) for (j in i + 1 until pictures.size) assertFalse("$i and $j", pictures[i].contentEquals(pictures[j]))
        // and a culture's is the same each time
        assertArrayEquals(pictures[0], stage(others[0], Decor.BANNERS, Decor.STALLS, Decor.SPRUCE))
    }
}
