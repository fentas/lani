package si.lanisce.lani.ui.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.sync.BackupRecord
import java.time.Instant

/** The Data sheet's line for an automatic backup that failed: in plain words, and a clock that is off on a line of its own. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoBackupFailedTest {
    @get:Rule val compose = createComposeRule()

    private var record by mutableStateOf(BackupRecord())

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    /** How often [text] is said on the screen, inside any node's text: once, not in each language's half of another line. */
    private fun said(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes()
        .sumOf { n -> n.config.getOrElse(SemanticsProperties.Text) { emptyList() }.sumOf { it.text.windowed(text.length).count { w -> w == text } } }

    @Test fun `nothing while none failed, else the reason, then what to do about a clock that is off`() {
        compose.setContent { AutoBackupFailed(record) }
        assertEquals("no failure, no line", 0, count(bi("dataSync.autoRetries")))
        val at = Instant.now().toString()
        record = BackupRecord(failedAt = at, problem = "UNREACHABLE", failures = 1)
        compose.waitForIdle()
        assertEquals("the reason said once", 1, said(bi("dataSync.backupUnreachable")))
        compose.onNodeWithText(bi("dataSync.autoRetries"), substring = true).assertExists()
        assertEquals(0, count(bi("dataSync.clockOffFix")))
        record = BackupRecord(failedAt = at, problem = "CLOCK_OFF", failures = 3)
        compose.waitForIdle()
        assertEquals("the reason said once", 1, said(bi("dataSync.backupClockOff")))
        compose.onNodeWithText(bi("dataSync.clockOffFix"), substring = true).assertExists()
        assertEquals("retrying isn't offered as the way out", 0, count(bi("dataSync.autoRetries")))
        record = BackupRecord(digest = "abc")
        compose.waitForIdle()
        assertEquals("a success clears it", 0, count(bi("dataSync.backupClockOff")))
    }
}
