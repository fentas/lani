package si.lanisce.lani.ui.scene

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a bubble over the scene goes: over its target, inside the view, off the top bar's pills. */
class BubblePlaceTest {
    private val w = 1080; private val h = 1200
    private val gap = 26; private val margin = 26

    // the kitchen's top bar on a 1080 px wide phone: "‹ Vas · Village", "🛖 3", "🔍 0/24"
    private val back = IntRect(21, 152, 327, 278)
    private val home = IntRect(345, 152, 540, 278)
    private val found = IntRect(847, 163, 1058, 267)
    private val pills = listOf(back, home, found)

    private fun IntRect.lies(p: BubblePlace, bw: Int, bh: Int) = left < p.x + bw && p.x < right && top < p.y + bh && p.y < bottom

    @Test fun `with nothing in the way it is centred over its target, or below it when there is no room above`() {
        val r = IntRect(500, 600, 560, 600)
        assertEquals(BubblePlace(430, 600 - gap - 100, above = true), BubblePlace.of(r, 200, 100, w, h, gap, margin, topLimit = 0, pills))
        val high = IntRect(500, 90, 560, 90)
        val p = BubblePlace.of(high, 200, 100, w, h, gap, margin, topLimit = 0, emptyList())
        assertEquals(BubblePlace(430, 90 + gap, above = false), p)
        // kept inside the view at its sides
        assertEquals(margin, BubblePlace.of(IntRect(0, 600, 20, 600), 200, 100, w, h, gap, margin, 0).x)
        assertEquals(w - margin - 200, BubblePlace.of(IntRect(1060, 600, 1080, 600), 200, 100, w, h, gap, margin, 0).x)
    }

    @Test fun `over someone at the view's edge it stays beside them, nudged in by half its width at most`() {
        // only the part of them in view counts (SceneAnchors.shown; none of them in view: no marker): a sliver at either edge
        for (r in listOf(IntRect(0, 600, 4, 600), IntRect(0, 600, 60, 600), IntRect(1070, 600, 1080, 600))) {
            val p = BubblePlace.of(r, 200, 100, w, h, gap, margin, topLimit = 0)
            val cx = (r.left + r.right) / 2
            assertTrue("$r: $p", kotlin.math.abs(p.x + 100 - cx) <= 100 + margin && p.above)
        }
    }

    @Test fun `a marker over someone high in the kitchen moves aside off the building's pill, still over them`() {
        // Micka's 🍲💬 over her head, just right of the 🛖 pill (QA: it covered part of it)
        val head = IntRect(560, 340, 680, 340)
        val bw = 200; val bh = 110
        val plain = BubblePlace.of(head, bw, bh, w, h, gap, margin, topLimit = 0)
        assertTrue("without keeping off, it lies on the pill", home.lies(plain, bw, bh))
        val p = BubblePlace.of(head, bw, bh, w, h, gap, margin, topLimit = 0, pills)
        assertTrue(pills.none { it.lies(p, bw, bh) })
        assertTrue("above them still", p.above && p.y == plain.y)
        // its tail can still point at them: their middle is under the bubble
        assertTrue(p.x <= (head.left + head.right) / 2 && (head.left + head.right) / 2 <= p.x + bw)
    }

    @Test fun `with no clear way aside it goes below what it would lie on`() {
        // a bubble as wide as the gap between two pills can't fit beside them: under them instead
        val r = IntRect(600, 330, 640, 330)
        val bw = 560; val bh = 120
        val p = BubblePlace.of(r, bw, bh, w, h, gap, margin, topLimit = 0, pills)
        assertTrue(pills.none { it.lies(p, bw, bh) })
        assertTrue(p.y >= home.bottom)
        assertFalse(p.y > h - bh)
    }
}
