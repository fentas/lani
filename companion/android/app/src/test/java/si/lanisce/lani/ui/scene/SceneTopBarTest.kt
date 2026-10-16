package si.lanisce.lani.ui.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.scene.SceneObject
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import kotlin.math.roundToInt

/**
 * The scene's top bar (companion/SCENES.md, "What the village has built": its card from inside): its pills lie side by
 * side, 8 dp apart at least (6 dp read as one tucked under the other on Jan's phone), none under another, none squeezed
 * out and none off the screen, in every pair of languages ("‹ Vas · Villaggio", "Cerkev … · Kirche …"), on a phone as
 * wide as Jan's (1080 px at 2.625: 411 dp) and narrower, with a bigger font too; in the church (its building, damaged:
 * ⚠️), the school (its building, ⬆️, and "📚 Šola") and by the fire (the class meets there: "📚 Šola"). The way back
 * gives way first: its village's word alone ("‹ Vas"), TalkBack still both. Measured with real fonts (Robolectric's
 * native graphics).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SceneTopBarTest {
    @get:Rule val compose = createComposeRule()

    private val scene = SceneSpec("v-cerkvi", "V cerkvi", art = "church", from = listOf("church"), objects = (1..12).map { SceneObject("s$it", "w$it") })

    private enum class Place { CHURCH, SCHOOL, FIRE }

    private data class Case(val pair: LangPair, val place: Place, val widthDp: Int, val fontScale: Float)

    private val pairs = Lang.entries.flatMap { t -> Lang.entries.filter { it != t }.map { b -> LangPair(t, b) } }

    private var case by mutableStateOf(Case(LangPair(Lang.SL, Lang.EN), Place.CHURCH, 411, 1f))
    private val pills = mutableStateMapOf<String, IntRect>()

    private fun show() {
        compose.setContent {
            val c = case
            key(c) {
                L10n.pair = c.pair
                val home = when (c.place) {
                    Place.CHURCH -> Building("c", BuildingType.CHURCH, 0, level = 1, damaged = true)
                    Place.SCHOOL -> Building("s", BuildingType.SCHOOL, 0, level = 3)
                    Place.FIRE -> null
                }
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, c.fontScale)) {
                    Box(Modifier.width(c.widthDp.dp)) {
                        TopBarRow(scene, 12, {}, home, c.place == Place.SCHOOL, {}, pills, if (c.place != Place.CHURCH) ({}) else null)
                    }
                }
            }
        }
    }

    /** The pills of [c] on the screen, left to right, and what's wrong with them (none: an empty list). */
    private fun check(c: Case): List<String> {
        case = c
        compose.waitForIdle()
        val px = (c.widthDp * compose.density.density).roundToInt()
        val shown = pills.entries.sortedBy { it.value.left }
        val wrong = ArrayList<String>()
        val expect = 2 + (if (c.place == Place.FIRE) 0 else 1) + (if (c.place == Place.CHURCH) 0 else 1)
        if (shown.size != expect) wrong += "${shown.map { it.key }}: $expect pills expected"
        // apart, each clearly one: 6 dp read as one tucked under the other
        val apart = (8 * compose.density.density).roundToInt()
        for ((a, b) in shown.zipWithNext()) if (a.value.right + apart > b.value.left) wrong += "${a.key} (${a.value.left}..${a.value.right}) and ${b.key} (${b.value.left}..${b.value.right}) less than 8 dp apart"
        for ((k, r) in shown) if (r.left < 0 || r.right > px || r.width <= 0) wrong += "$k at ${r.left}..${r.right} of $px"
        // every button inside its pill: none pushed out of it, under the next one
        for (n in compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()) {
            val b = n.boundsInWindow
            if (shown.none { (_, r) -> b.left >= r.left - 1 && b.right <= r.right + 1 }) {
                wrong += "the button «${n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()}» at ${b.left.roundToInt()}..${b.right.roundToInt()} outside its pill"
            }
        }
        return wrong.map { "$c: $it" }
    }

    @Test fun `the pills never run under each other, in every pair, narrow or wide, a big font or not`() {
        show()
        val failed = ArrayList<String>()
        for (pair in pairs) for (place in Place.entries) for ((w, f) in listOf(411 to 1f, 393 to 1f, 360 to 1f, 320 to 1f, 411 to 1.3f, 360 to 1.3f)) {
            failed += check(Case(pair, place, w, f))
        }
        assertTrue(failed.joinToString("\n"), failed.isEmpty())
    }

    @Test fun `the way back's label gives way to the village's word alone where both don't fit`() {
        var room by mutableStateOf(300)
        compose.setContent {
            Box(Modifier.width(room.dp)) { FitLabel("‹ Vas · Villaggio", "‹ Vas", Color.White, MaterialTheme.typography.labelLarge) }
        }
        compose.onNodeWithText("‹ Vas · Villaggio").assertExists()
        room = 60
        compose.waitForIdle()
        compose.onNodeWithText("‹ Vas").assertExists()
        compose.onNodeWithText("‹ Vas · Villaggio").assertDoesNotExist()
        room = 300
        compose.waitForIdle()
        compose.onNodeWithText("‹ Vas · Villaggio").assertExists()
    }
}
