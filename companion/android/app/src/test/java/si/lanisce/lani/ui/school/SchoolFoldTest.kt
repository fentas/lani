package si.lanisce.lani.ui.school

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.data.UnitRef
import si.lanisce.lani.game.School
import si.lanisce.lani.l10n.bi

/**
 * The school's quiet lines (companion/COURSE.md, "What the school shows"): a level's folded units, "📝 Še 9 enot v A2 · 9
 * more units in A2", with its count in each number's form, read by TalkBack as a button; a level done, "✅ 18 opravljenih
 * enot", a button that says whether its units are shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SchoolFoldTest {
    @get:Rule val compose = createComposeRule()

    private fun ref(id: String) = UnitRef(id, mapOf("sl" to id, "en" to id))
    private fun view(rows: Int, folded: Int) = School.LevelView(
        School.LevelState("A2", open = true, needs = emptyList(), done = 1, of = 8, planned = 6),
        (1..rows).map { ref("r$it") }, (1..folded).map { ref("f$it") },
    )
    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test fun `the folded line counts in Slovene's four number forms, and says more only under rows`() {
        assertEquals("📝 Še 1 enota v A2 · 1 more unit in A2", foldedLine(view(3, 1)))
        assertEquals("📝 Še 2 enoti v A2 · 2 more units in A2", foldedLine(view(3, 2)))
        assertEquals("📝 Še 4 enote v A2 · 4 more units in A2", foldedLine(view(3, 4)))
        assertEquals("📝 Še 10 enot v A2 · 10 more units in A2", foldedLine(view(3, 10)))
        // nothing above it (a closed level, or nothing written yet): every unit of the level
        assertEquals("📝 14 enot v A2 · 14 units in A2", foldedLine(view(0, 14)))
        assertEquals("✅ 18 opravljenih enot · 18 units done", "✅ ${bi("school.unitsAllDone", "n" to 18)}")
        assertEquals("✅ 3 opravljene enote · 3 units done", "✅ ${bi("school.unitsAllDone", "n" to 3)}")
    }

    @Test fun `the folded line is a button that reads its count, its mark left out, a tap lists the units`() {
        var taps = 0
        val text = foldedLine(view(4, 10))
        compose.setContent { QuietLine(text, "›", action = bi("school.listUnits")) { taps++ } }
        compose.onNode(hasText(text) and button).assertExists().performClick()
        assertEquals(1, taps)
        compose.onNodeWithText("›").assertDoesNotExist()
        compose.onNode(SemanticsMatcher("its action") { n ->
            n.config.getOrNull(SemanticsActions.OnClick)?.label == "Pokaži seznam · Show the list"
        }).assertExists()
    }

    @Test fun `a level done is one line that says whether its units are shown`() {
        var shown = false
        compose.setContent {
            QuietLine(
                "✅ ${bi("school.unitsAllDone", "n" to 18)}", if (shown) "⌃" else "⌄",
                action = if (shown) bi("school.hideUnits") else bi("school.showUnits"),
                state = if (shown) bi("school.unitsShown") else bi("school.unitsHidden"),
            ) { shown = !shown }
        }
        val line = hasText("✅ 18 opravljenih enot · 18 units done") and button
        compose.onNode(line and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "skrite · hidden")).assertExists().performClick()
        assertEquals(true, shown)
    }
}
