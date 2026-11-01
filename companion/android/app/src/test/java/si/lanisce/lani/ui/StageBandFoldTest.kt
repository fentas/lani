package si.lanisce.lani.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.data.Clips
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.game.villagers.VillagerLines
import si.lanisce.lani.ui.stage.StageBand
import si.lanisce.lani.ui.stage.StageLayout
import si.lanisce.lani.ui.stage.StagePerson
import si.lanisce.lani.ui.stage.StagePlace
import si.lanisce.lani.ui.stage.StageState
import java.time.LocalDate

/**
 * The stage band folded while an answer's long card is under the question (ui/stage/StageBand `fold`, ui/PlayerScreen
 * [CardFold]): on a phone upright its small band, about 56 dp for the full one's 136 and more; a tap opens it again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class StageBandFoldTest {
    @get:Rule val compose = createComposeRule()

    private val luka = StagePerson(
        id = "luka", name = "Pastir Luka", emoji = "🐑", art = "shepherd", voice = Clips.MALE, role = "Pastir · Shepherd", register = "ti",
        lines = VillagerLines(greet = listOf(VillagerLine("Dober dan!", "Good day!")), cheer = listOf(VillagerLine("Bravo!", "Well done!"))),
        level = 1,
    )

    private fun stage() = StageState(luka, StagePlace.FIRE, 7L, LocalDate.of(2026, 11, 1), speaker = null, memory = null, greet = false, befriend = {})

    private fun height(): Float = compose.onNodeWithTag("band").getBoundsInRoot().let { (it.bottom - it.top).value }

    /** The portrait holds still (animations off, as rememberReducedMotion reads it), and the clock goes by hand. */
    @Before fun still() {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.mainClock.autoAdvance = false
    }

    /** The state written here is told to the composition, then a second of frames goes by. */
    private fun settle() {
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    @Test fun `folded for a card the band is small, open again it is tall, and tall without a card`() {
        var fold by mutableStateOf(false)
        val s = stage()
        compose.setContent { Column { StageBand(s, Modifier.testTag("band"), fold = fold) } }
        settle()
        assertTrue("tall on a phone of 891 dp: ${height()}", height() >= StageLayout.TALL_DP - 1)
        fold = true
        settle()
        assertTrue("folded: the small band, ${height()}", height() < StageLayout.TALL_DP - 40)
        // a tap opens it for this card
        compose.onNodeWithTag("band").performClick()
        settle()
        assertTrue("opened again: ${height()}", height() >= StageLayout.TALL_DP - 1)
        // the next card folds it again
        fold = false
        settle()
        fold = true
        settle()
        assertTrue("the next card's fold: ${height()}", height() < StageLayout.TALL_DP - 40)
    }
}
