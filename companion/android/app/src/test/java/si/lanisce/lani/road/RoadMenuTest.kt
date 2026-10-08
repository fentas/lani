package si.lanisce.lani.road

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The car's menu (RoadMenu): two tabs, the first four big items to start a drive with, the second each kind's session;
 * nothing to scroll in the first, and every session id a car may remember still plays. And "📖 Zgodba": the story under way.
 */
class RoadMenuTest {
    @Test fun `two tabs, "🚗 Za pot" a grid of four, "Več" a list of each kind's session`() {
        val tabs = RoadMenu.children(RoadMenu.ROOT, ready = true)!!
        assertEquals(listOf(RoadMenu.TAB, RoadMenu.MORE), tabs.map { it.id })
        assertTrue(tabs.all { it.browsable && !it.playable })
        assertEquals(listOf(true, false), tabs.map { it.grid })
        val main = RoadMenu.children(RoadMenu.TAB, ready = true)!!
        assertEquals(listOf(RoadService.MIX, RoadService.QUIZ, RoadService.EASY, RoadService.STORY), main.map { it.id })
        assertTrue(main.size <= 4)
        assertTrue(main.all { it.playable })
        val more = RoadMenu.children(RoadMenu.MORE, ready = true)!!
        assertEquals(
            listOf(
                RoadService.REVIEWS, RoadService.WORDS, RoadService.DIALOGS, RoadService.STORIES, RoadService.SHADOW,
                RoadService.TRANSFORMS, RoadService.RAPID, RoadService.BUILDS, RoadService.RIDDLES,
            ),
            more.map { it.id },
        )
        // nothing on the phone yet: where to get ready first, not playable
        val none = RoadMenu.children(RoadMenu.TAB, ready = false)!!.single()
        assertEquals(RoadMenu.NONE, none.id)
        assertFalse(none.playable)
        assertNull(RoadMenu.children(RoadService.MIX, ready = true))
    }

    @Test fun `every session id from before still plays from its id, and the old tab still browses`() {
        val before = listOf(
            "road:mix", "road:easy", "road:shadow", "road:reviews", "road:words", "road:dialogs", "road:stories",
            "road:transform", "road:rapid", "road:build", "road:riddles",
        )
        for (id in before) assertTrue(id, RoadMenu.node(id)?.playable == true && id in RoadMix.SESSIONS)
        assertEquals("road:tab", RoadMenu.TAB)
        assertTrue(RoadMenu.node("road:tab")!!.browsable)
        assertTrue(RoadMenu.node(RoadService.QUIZ)!!.playable)
        assertNull(RoadMenu.node("road:nothing"))
        // the sessions are the menu's, each once
        assertEquals(RoadMenu.SESSIONS, RoadMix.SESSIONS)
        assertEquals(RoadMix.SESSIONS.size, RoadMix.SESSIONS.toSet().size)
    }

    @Test fun `the custom actions go to "🌙 Mirno" or "❓ Kviz", and from there back to "🚗 Za pot"`() {
        assertEquals(listOf(RoadService.EASY, RoadService.QUIZ), RoadMenu.actions(RoadService.MIX))
        assertEquals(RoadService.EASY, RoadMenu.toggle(RoadService.MIX, RoadService.EASY))
        assertEquals(RoadService.MIX, RoadMenu.toggle(RoadService.EASY, RoadService.EASY))
        assertEquals(RoadService.QUIZ, RoadMenu.toggle(RoadService.EASY, RoadService.QUIZ))
        assertEquals(RoadService.MIX, RoadMenu.toggle(RoadService.QUIZ, RoadService.QUIZ))
        assertEquals(RoadService.QUIZ, RoadMenu.toggle(null, RoadService.QUIZ))
    }

    private fun story(id: String) = RoadItem("story:$id", Kind.STORY, id, "", listOf(Sound.Prompt(id)))

    @Test fun `"📖 Zgodba" goes on with the story under way from its next evening, then the others`() {
        val lib = RoadLibrary(1, "en", listOf(story("a/0"), story("a/1"), story("a/2"), story("b/0"), story("b/1"), story("c/0")))
        // nothing heard: as "📖 Zgodbe"
        assertEquals(RoadMix.stories(lib, emptyMap()), RoadMix.story(lib, emptyMap()))
        // "b" under way (its first evening heard last): its second evening, then the others
        val heard = mapOf("story:a/0" to 10L, "story:b/0" to 20L)
        assertEquals(listOf("story:b/1", "story:c/0", "story:a/0", "story:a/1", "story:a/2"), RoadMix.story(lib, heard).map { it.id })
        // "b" heard to its end: as "📖 Zgodbe", the least recently heard first
        val done = heard + ("story:b/1" to 30L)
        assertEquals(RoadMix.stories(lib, done), RoadMix.story(lib, done))
        assertEquals(RoadMix.story(lib, heard), RoadMix.session(RoadService.STORY, lib, heard, "2026-10-08"))
    }
}
