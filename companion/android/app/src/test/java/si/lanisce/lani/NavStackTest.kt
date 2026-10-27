package si.lanisce.lani

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.app.SheetKind

/**
 * The back stack (Nav.kt, docs/plans/05-built/navigation.md): Back returns to the screen below, a screen of a kind already
 * in the stack is gone back to rather than opened again, a run never stays under another screen, the root is left by the
 * system, and the stack is saved for after the process's death up to the first screen that can't be opened again.
 */
class NavStackTest {
    private fun run(n: Int) = Screen.NumbersRun(emptyList(), run = n)

    private fun NavStack.screens() = entries.map { it.screen }

    private fun home(): NavStack = NavStack().apply { reset(Screen.Home) }

    @Test
    fun `back returns to the screen below, and the root stays`() {
        val nav = home()
        nav.go(Screen.Village)
        nav.go(Screen.Scene("ob-ognju"))
        assertEquals(Screen.Village, nav.below)
        assertTrue(nav.back())
        assertEquals(Screen.Village, nav.top)
        assertTrue(nav.back())
        assertEquals(Screen.Home, nav.top)
        assertFalse("Home is the root: the system's Back leaves the app there", nav.back())
        assertFalse(nav.canGoBack)
        assertNull(nav.below)
    }

    @Test
    fun `a screen opened from Home goes back Home, from the village to the village`() {
        val nav = home()
        nav.go(Screen.Scene("ob-ognju"))
        nav.back()
        assertEquals("a scene from Home's strip: Back is Home, not the village", Screen.Home, nav.top)
        nav.go(Screen.Village)
        nav.go(Screen.Packs)
        nav.back()
        assertEquals(Screen.Village, nav.top)
    }

    @Test
    fun `a screen of a kind already in the stack is gone back to, so the stack never loops`() {
        val nav = home()
        nav.go(Screen.Progress)
        nav.go(Screen.School)
        nav.go(Screen.CourseUnit("biti"))
        nav.go(Screen.Progress)
        assertEquals(listOf(Screen.Home, Screen.Progress), nav.screens())
        nav.go(Screen.Village)
        nav.go(Screen.Scene("a"))
        nav.go(Screen.Scene("b"))
        assertEquals("a scene beside another: the village under it", listOf(Screen.Home, Screen.Progress, Screen.Village, Screen.Scene("b")), nav.screens())
        nav.go(Screen.Home)
        assertEquals(listOf(Screen.Home), nav.screens())
    }

    @Test
    fun `different screens of a kind stay, so Back walks them`() {
        val nav = home()
        nav.go(Screen.CourseUnit("biti"))
        nav.go(Screen.School)
        nav.go(Screen.CourseUnit("imeti"))
        assertEquals(listOf(Screen.Home, Screen.CourseUnit("biti"), Screen.School, Screen.CourseUnit("imeti")), nav.screens())
        nav.go(Screen.Villager("luka"))
        nav.go(Screen.Villager("zala"))
        nav.back()
        assertEquals(Screen.Villager("luka"), nav.top)
        nav.go(Screen.CourseUnit("biti"))
        assertEquals("the same unit again: back to it", listOf(Screen.Home, Screen.CourseUnit("biti")), nav.screens())
    }

    @Test
    fun `a run never stays under another screen, and its end goes where it began`() {
        val nav = home()
        nav.go(Screen.Village)
        nav.go(run(1))
        nav.go(run(2)) // once more, from its end
        assertEquals(listOf(Screen.Home, Screen.Village, run(2)), nav.screens())
        nav.go(Screen.Packs) // its end's "more words"
        assertEquals("the run gave its place", listOf(Screen.Home, Screen.Village, Screen.Packs), nav.screens())
        nav.back()
        assertEquals(Screen.Village, nav.top)
        nav.go(Screen.Sheet("biti", SheetKind.WORKSHEET, "ws1", 1))
        nav.back()
        assertEquals("Back from a run: where it began", Screen.Village, nav.top)
    }

    @Test
    fun `the same screen keeps its state, another gets its own`() {
        val nav = home()
        nav.go(Screen.Village)
        nav.go(Screen.Scene("ob-ognju"))
        val id = nav.entries.last().id
        nav.go(Screen.Scene("ob-ognju", focus = "ob-ognju/story"))
        assertEquals("the same scene at a happening", id, nav.entries.last().id)
        nav.go(Screen.Scene("kuhinja"))
        assertNotEquals(id, nav.entries.last().id)
        val runId = nav.entries.last().id
        nav.go(run(1))
        nav.go(run(2))
        assertNotEquals("each run is a screen of its own", runId, nav.entries.last().id)
    }

    @Test
    fun `settings pages each their own, the sections' list under them`() {
        val nav = home()
        nav.go(Screen.Settings())
        nav.go(Screen.Settings("tutor"))
        assertEquals(listOf(Screen.Home, Screen.Settings(), Screen.Settings("tutor")), nav.screens())
        nav.go(Screen.Settings())
        nav.go(Screen.Settings("data"))
        assertEquals(listOf(Screen.Home, Screen.Settings(), Screen.Settings("data")), nav.screens())
    }

    @Test
    fun `what leaves the stack is told, the top first`() {
        val nav = home()
        val gone = ArrayList<Screen>()
        nav.onRemoved = { gone += it }
        nav.go(Screen.Friends)
        nav.go(Screen.Visit("t1"))
        nav.go(Screen.VisitScene("t1", "trg"))
        nav.reset(Screen.Home)
        assertEquals(listOf(Screen.VisitScene("t1", "trg"), Screen.Visit("t1"), Screen.Friends), gone)
        gone.clear()
        nav.go(Screen.Talk)
        nav.go(Screen.Talk)
        assertTrue("the same screen again: nothing leaves", gone.isEmpty())
    }

    @Test
    fun `backTo leaves everything above the screen it finds`() {
        val nav = home()
        nav.go(Screen.Friends)
        nav.go(Screen.Visit("t1"))
        nav.go(Screen.VisitScene("t1", "trg"))
        nav.go(Screen.Talk)
        assertTrue(nav.backTo { it is Screen.Visit })
        assertEquals(Screen.Visit("t1"), nav.top)
        assertFalse(nav.backTo { it is Screen.School })
        assertEquals(Screen.Visit("t1"), nav.top)
    }

    @Test
    fun `reset keeps the state of the screens it shares at its start`() {
        val nav = home()
        val homeId = nav.entries.first().id
        nav.go(Screen.Village)
        nav.reset(Screen.Home, Screen.School)
        assertEquals(homeId, nav.entries.first().id)
        assertEquals(listOf(Screen.Home, Screen.School), nav.screens())
    }

    @Test
    fun `the stack is saved up to its first run or visit, and comes back under the same ids`() {
        val nav = home()
        nav.go(Screen.Village)
        nav.go(Screen.Scene("ob-ognju", focus = "ob-ognju/story", building = "hut-2"))
        nav.go(Screen.School)
        nav.go(Screen.CourseUnit("biti"))
        nav.go(run(3))
        val saved = nav.saved()
        val ids = nav.entries.dropLast(1).map { it.id }
        val runId = nav.entries.last().id

        val again = NavStack()
        assertTrue(again.restore(saved))
        assertEquals(
            "the run's answers live in the run: back to where it began; a scene without its dialog's focus",
            listOf(Screen.Home, Screen.Village, Screen.Scene("ob-ognju", null, "hut-2"), Screen.School, Screen.CourseUnit("biti")),
            again.screens(),
        )
        assertEquals(ids, again.entries.map { it.id })
        again.go(Screen.Talk)
        assertTrue("new ids after every one given before, the run's too", again.entries.last().id > runId)
    }

    @Test
    fun `a visit isn't saved, nor what lies over it`() {
        val nav = home()
        nav.go(Screen.Friends)
        nav.go(Screen.Visit("t1"))
        nav.go(Screen.VisitScene("t1", "trg"))
        val again = NavStack()
        assertTrue(again.restore(nav.saved()))
        assertEquals(listOf(Screen.Home, Screen.Friends), again.screens())
    }

    @Test
    fun `nothing saved, or text this app can't read, is Home alone`() {
        for (text in listOf(null, "", "not json", """[{"id":4,"route":["someday"]}]""")) {
            val nav = NavStack()
            assertFalse(text.toString(), nav.restore(text))
            assertEquals(listOf(Screen.Home), nav.screens())
        }
    }

    @Test
    fun `every screen with a route opens again from it`() {
        val screens = listOf(
            Screen.Home, Screen.Village, Screen.Packs, Screen.Readings, Screen.ReadAloud("r1"), Screen.Talk, Screen.Progress,
            Screen.MyWords, Screen.Family("c1"), Screen.Family(null), Screen.Scene("s1", null, "b1"), Screen.Scene("s2"),
            Screen.Villagers, Screen.Villager("v1"), Screen.Friends, Screen.TownProgress("t1"), Screen.TownQuestion("k1", asked = true),
            Screen.TownQuestion("k2", asked = false), Screen.Treasure, Screen.School, Screen.CourseUnit("u1"), Screen.UnitIntro("u1"),
            Screen.Numbers, Screen.Settings(), Screen.Settings("data"), Screen.GrammarBook,
        )
        for (s in screens) {
            val route = NavRoutes.route(s) ?: error("$s has no route")
            assertEquals(s, NavRoutes.screen(route))
        }
        for (s in listOf(Screen.Loading, Screen.Setup, Screen.Visit("t1"), Screen.VisitScene("t1", "x"), run(1))) {
            assertNull("$s isn't opened again from a route", NavRoutes.route(s))
        }
    }
}
