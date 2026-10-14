package si.lanisce.lani.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.app.ChatArchive
import si.lanisce.lani.app.ChatController
import si.lanisce.lani.app.ChatMessage
import si.lanisce.lani.app.ChatStore
import si.lanisce.lani.app.Notices
import si.lanisce.lani.app.SyncController
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.l10n.bi
import java.io.File
import java.nio.file.Files
import kotlin.math.abs

/**
 * A sideways swipe on a chat bubble, as messengers do (README "Tutor chat"): to the right a reply, to the left the
 * bookmark (on, a second time off); the bubble follows the finger and springs back; a drag up or down, or too steep, is
 * the list's; a long press keeps its menu and TalkBack its actions; a table that fits is the bubble's, a wider one keeps
 * its own scroll; with the system's animations removed the bubble is back at once ([BubbleSwipe], [SwipeRow]). A phone:
 * 411 × 891 dp at xxhdpi (the touch slop 8 dp, the threshold 64 dp past it).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatSwipeTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val did = mutableListOf<String>()
    private var message by mutableStateOf(ChatMessage(fromMe = false, text = "Pozdravljeni! Kako ste danes?", id = "m1"))

    @After fun animationsBack() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun px(dp: Int) = with(compose.density) { dp.dp.toPx() }

    // --- the decisions ---------------------------------------------------------------------------------------------

    @Test fun `a drag is the swipe's once clearly sideways, the list's once it went up or down, else not yet`() {
        val slop = 24f
        assertEquals(true, BubbleSwipe.decide(Offset(30f, 0f), slop))
        assertEquals(true, BubbleSwipe.decide(Offset(-30f, 10f), slop))
        assertEquals("30° off the horizontal is still sideways", true, BubbleSwipe.decide(Offset(40f, 23f), slop))
        assertEquals("steeper is the list's", false, BubbleSwipe.decide(Offset(40f, 24f), slop))
        assertEquals(false, BubbleSwipe.decide(Offset(0f, -30f), slop))
        assertEquals(false, BubbleSwipe.decide(Offset(20f, 20f), slop))
        assertNull("within the slop: too short to tell", BubbleSwipe.decide(Offset(20f, 5f), slop))
        assertNull(BubbleSwipe.decide(Offset(5f, 15f), slop))
    }

    @Test fun `the bubble follows the finger with resistance, and past the threshold the action is the way it went`() {
        val limit = 480f
        assertEquals(0f, BubbleSwipe.offset(0f, limit))
        assertEquals("half the finger's way at the limit", limit / 2, BubbleSwipe.offset(limit, limit), 0.01f)
        assertTrue("never past the limit", BubbleSwipe.offset(100_000f, limit) < limit)
        assertEquals(-BubbleSwipe.offset(200f, limit), BubbleSwipe.offset(-200f, limit))
        assertTrue("with the finger at first", BubbleSwipe.offset(10f, limit) > 9.7f)

        assertEquals(BubbleSwipe.Way.REPLY, BubbleSwipe.action(192f, 192f))
        assertEquals(BubbleSwipe.Way.BOOKMARK, BubbleSwipe.action(-300f, 192f))
        assertNull(BubbleSwipe.action(191f, 192f))
        assertNull(BubbleSwipe.action(-100f, 192f))

        assertEquals("no reply: it doesn't go right", 0f, BubbleSwipe.allowed(50f, reply = false, bookmark = true))
        assertEquals(-50f, BubbleSwipe.allowed(-50f, reply = false, bookmark = true))
        assertEquals("no bookmark: it doesn't go left", 0f, BubbleSwipe.allowed(-50f, reply = true, bookmark = false))
    }

    @Test fun `a swipe left bookmarks the message, a second takes it away, and the undo puts back what it had`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val dir = Files.createTempDirectory("chat").toFile()
            val notices = Notices()
            val chat = ChatController(
                scope, Prefs(context), SyncController(context, scope, { null }, notices) {}, notices,
                ChatStore(File(dir, "chat"), File(dir, "chat.json")), clock = { 1_000L },
            )
            chat.messages += ChatMessage(fromMe = false, text = "Imeti: imam, imaš, ima", id = "t1")
            assertNull("it had none", chat.toggleBookmark("t1"))
            assertTrue(chat.find("t1")!!.bookmark != null)
            chat.note("t1", "the endings")
            val kept = chat.find("t1")!!.bookmark
            assertEquals("the second swipe gives back what it took", kept, chat.toggleBookmark("t1"))
            assertNull(chat.find("t1")!!.bookmark)
            chat.restoreBookmark("t1", kept)
            assertEquals("the undo: back with its note", "the endings", chat.find("t1")!!.bookmark?.note)
        } finally {
            scope.cancel()
        }
    }

    // --- on the screen ---------------------------------------------------------------------------------------------

    /** A new bubble for each [show] after the first (the content is set once a test): its remembered state afresh. */
    private var generation by mutableStateOf(0)
    private var shown = false

    /** The bubble as the chat wires it: the long press's menu, the swipe's reply and bookmark (on or off), noted in [did]. */
    private fun show(text: String = message.text) {
        message = message.copy(text = text, bookmark = null)
        if (shown) {
            generation++
            compose.waitForIdle()
            return
        }
        shown = true
        compose.setContent {
            val m = message
            key(generation) { ChatBubble(
                m.text, m.fromMe,
                bookmarked = m.bookmark != null,
                actions = listOf(
                    BubbleAction("🔖 ${bi("chatSheet.bookmark")}") { did += "menu:bookmark" },
                    BubbleAction("↩️ ${bi("chatSheet.reply")}") { did += "menu:reply" },
                ),
                swipe = BubbleSwipes(
                    reply = { did += "reply" },
                    bookmark = {
                        did += "bookmark"
                        message = ChatArchive.bookmarked(message, on = message.bookmark == null, now = 1L)
                    },
                ),
            ) }
        }
    }

    private fun bubble() = compose.onNodeWithText("Pozdravljeni", substring = true)
    private fun left(n: SemanticsNodeInteraction) = n.fetchSemanticsNode().boundsInRoot.left

    /**
     * A drag from [n]'s centre by [dx], [dy] dp, in [steps] moves 16 ms apart; let go unless [lift] is false, after a
     * moment held still with [still] (no fling).
     */
    private fun drag(n: SemanticsNodeInteraction, dx: Int, dy: Int = 0, steps: Int = 12, lift: Boolean = true, still: Boolean = false) = n.performTouchInput {
        down(center)
        repeat(steps) { moveBy(Offset(px(dx) / steps, px(dy) / steps)) }
        if (still) advanceEventTime(200)
        if (lift) up()
    }

    @Test fun `a swipe right past the threshold replies, and the bubble springs back`() {
        show()
        val rest = left(bubble())
        drag(bubble(), 100, lift = false)
        val moved = left(bubble()) - rest
        assertTrue("it follows the finger, with resistance: $moved px of ${px(100)}", moved > px(30) && moved < px(100) - px(8))
        bubble().performTouchInput { up() }
        assertEquals(listOf("reply"), did)
        compose.waitForIdle()
        assertEquals("back where it was", rest, left(bubble()), 0.5f)
    }

    @Test fun `a swipe that stops short of the threshold does nothing`() {
        show()
        val rest = left(bubble())
        drag(bubble(), 60)
        drag(bubble(), -60)
        assertEquals(emptyList<String>(), did)
        compose.waitForIdle()
        assertEquals(rest, left(bubble()), 0.5f)
    }

    @Test fun `a swipe left bookmarks it, its 🔖 beside it, and a second swipe left takes the bookmark away`() {
        show()
        val mark = bi("chatSheet.bookmark")
        compose.onAllNodesWithContentDescription(mark).assertCountEquals(0)
        drag(bubble(), -110)
        assertEquals(listOf("bookmark"), did)
        compose.onNodeWithContentDescription(mark).assertExists()
        drag(bubble(), -110)
        assertEquals(listOf("bookmark", "bookmark"), did)
        compose.onAllNodesWithContentDescription(mark).assertCountEquals(0)
    }

    @Test fun `a long press keeps the menu, TalkBack its actions, and a swipe after a long press is nothing`() {
        show()
        val surface = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        val labels = surface.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("🔖 ${bi("chatSheet.bookmark")}", "↩️ ${bi("chatSheet.reply")}"), labels)
        bubble().performTouchInput { longClick() }
        compose.onNodeWithText("↩️ ${bi("chatSheet.reply")}").assertExists()
        assertEquals(emptyList<String>(), did)

        // held still past the long press, then dragged: the menu's gesture, not a swipe
        show()
        bubble().performTouchInput {
            down(center)
            advanceEventTime(800)
            repeat(10) { moveBy(Offset(px(12), 0f)) }
            up()
        }
        assertEquals(emptyList<String>(), did)
    }

    /** A list of 30 bubbles that swipe, as the chat's; its state in [list]. */
    private lateinit var list: LazyListState

    private fun showList() = compose.setContent {
        list = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items((1..30).toList(), key = { it }) { i ->
                ChatBubble(
                    "Sporočilo $i: kako se reče po slovensko?", fromMe = i % 2 == 0,
                    swipe = BubbleSwipes(reply = { did += "reply:$i" }, bookmark = { did += "bookmark:$i" }),
                )
            }
        }
    }

    private fun scrolled() = list.firstVisibleItemIndex * 10_000 + list.firstVisibleItemScrollOffset

    /** The bubble in the middle of the screen: its number. */
    private fun middle(): Int = compose.runOnIdle { list.layoutInfo.visibleItemsInfo.let { it[it.size / 2].key as Int } }

    private fun numbered(i: Int) = compose.onNodeWithText("Sporočilo $i:", substring = true)

    @Test fun `a drag up or down scrolls the list, a steep one too, a sideways one with a wobble is the bubble's`() {
        showList()
        drag(numbered(3), 0, -200, still = true)
        compose.waitForIdle()
        val after = scrolled()
        assertTrue("the list scrolled: $after", after > 0)
        assertEquals(emptyList<String>(), did)

        drag(numbered(middle()), 70, -160, still = true)
        compose.waitForIdle()
        assertTrue("a steep drag scrolls: ${scrolled()} from $after", scrolled() > after)
        assertEquals(emptyList<String>(), did)

        val at = scrolled()
        val i = middle()
        drag(numbered(i), 110, 20)
        compose.waitForIdle()
        assertEquals(listOf("reply:$i"), did)
        assertEquals("the list stayed", at, scrolled())

        drag(numbered(i), -110, -15)
        compose.waitForIdle()
        assertEquals(listOf("reply:$i", "bookmark:$i"), did)
        assertEquals(at, scrolled())
    }

    @Test fun `a table that fits is the bubble's to swipe, one wider than the bubble scrolls`() {
        show("Pozdravljeni!\n\n| ena | dve |\n|---|---|\n| tri | štiri |")
        drag(compose.onNodeWithText("štiri"), -110)
        assertEquals(listOf("bookmark"), did)

        val long = "dolga beseda, še ena in še ena" // 30 letters: a column of 200 dp, three of them wider than the bubble
        show("Pozdravljeni!\n\n| $long | $long | $long |\n|---|---|---|\n| a | b | konec |")
        compose.waitForIdle()
        val table = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
        fun at() = table.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertEquals(0f, at())
        drag(table, -110)
        compose.waitForIdle()
        assertEquals("no bookmark: the table's own scroll", listOf("bookmark"), did)
        assertTrue("the table scrolled: ${at()} px", at() > px(40))
    }

    @Test fun `with the system's animations removed the action runs and the bubble is back at once`() {
        fun oneFrameAfter(): Float {
            val rest = left(bubble())
            compose.mainClock.autoAdvance = false
            drag(bubble(), 100)
            compose.mainClock.advanceTimeByFrame()
            val off = abs(left(bubble()) - rest)
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            return off
        }
        show()
        val springing = oneFrameAfter()
        assertTrue("with animations it springs back: $springing px off a frame after", springing > px(4))

        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        did.clear()
        show()
        assertEquals("without: at rest a frame after", 0f, oneFrameAfter(), 0.5f)
        assertEquals(listOf("reply"), did)
    }

    @Test fun `the typing bubble and a bubble without a swipe don't move`() {
        compose.setContent { ChatBubble("Pozdravljeni, tu sem …", fromMe = false) }
        val rest = left(bubble())
        drag(bubble(), 100, lift = false)
        assertEquals(rest, left(bubble()), 0.5f)
        bubble().performTouchInput { up() }
    }
}
