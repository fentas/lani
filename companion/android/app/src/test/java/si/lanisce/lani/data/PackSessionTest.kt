package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.ui.home.HomeLogic

/**
 * The packs by the learner's level (docs/plans/05-built/packs-en.md, "How packs reach a learner"): what is suggested
 * next, the packs screen's order and what waits under "Za pozneje · For later", with B1 and B2 packs among A1 and A2
 * ones in their files' order, as the bridge lists them.
 */
class PackSessionTest {
    private fun pack(id: String, level: String, learned: Int = 0, source: String = "curated", festival: String? = null, project: String? = null) =
        PackInfo(id, id, level = level, total = 20, learned = learned, source = source, festival = festival, project = project)

    // the files' order (alphabetical), the levels mixed as the B1 and B2 packs come among the older ones
    private val atWork = pack("at-work", "B1")
    private val family = pack("family", "A1")
    private val idioms = pack("idioms-of-the-dale", "B2")
    private val pub = pack("in-the-pub", "A1")
    private val news = pack("the-local-news", "B1")
    private val weather = pack("weather-and-garden", "A2")
    private val all = listOf(atWork, family, idioms, pub, news, weather)

    @Test fun `the levels apart`() {
        assertEquals(0, PackSession.above("A1", "A2"))
        assertEquals(0, PackSession.above("A2", "A2"))
        assertEquals(1, PackSession.above("B1", "A2"))
        assertEquals(2, PackSession.above("B2", "A2"))
        assertEquals(0, PackSession.above("X9", "A1"))
        assertEquals("B1–B2", PackSession.levelsOf(listOf(idioms, atWork, news)))
        assertEquals("B2", PackSession.levelsOf(listOf(idioms)))
    }

    @Test fun `an A1 learner is offered their level, then A2, then B1, never B2 first`() {
        assertEquals("family", PackSession.suggest(all, "A1")?.id)
        val a1Done = all.map { if (it.level == "A1") it.copy(learned = it.total) else it }
        assertEquals("weather-and-garden", PackSession.suggest(a1Done, "A1")?.id)
        // every A1 and A2 pack learned: the nearest level above, B1, and of it the first in the files' order
        val a2Done = a1Done.map { if (it.level == "A2") it.copy(learned = it.total) else it }
        assertEquals("at-work", PackSession.suggest(a2Done, "A1")?.id)
        val b1Done = a2Done.map { if (it.level == "B1") it.copy(learned = it.total) else it }
        assertEquals("idioms-of-the-dale", PackSession.suggest(b1Done, "A1")?.id)
    }

    @Test fun `a B2 learner gets B2 first, then the easier packs left, in the files' order`() {
        assertEquals("idioms-of-the-dale", PackSession.suggest(all, "B2")?.id)
        val b2Done = all.map { if (it.level == "B2") it.copy(learned = it.total) else it }
        assertEquals("at-work", PackSession.suggest(b2Done, "B2")?.id)
    }

    @Test fun `a pack started, a new tutor pack and the festival's come before any level`() {
        val started = idioms.copy(learned = 3)
        assertEquals("idioms-of-the-dale", PackSession.suggest(all - idioms + started, "A1")?.id)
        val tutor = pack("tutor-c1", "C1", source = "tutor")
        assertEquals("tutor-c1", PackSession.suggest(all + tutor, "A1") { it.id == "tutor-c1" }?.id)
        val feast = pack("feast-may-day", "B2", festival = "may_day")
        assertEquals("feast-may-day", PackSession.suggest(all + feast, "A1", festival = "may_day")?.id)
    }

    @Test fun `the packs screen folds the untouched packs two levels up, keeps the rest in the files' order`() {
        // A1: B1 and B2 wait for later, A2 stays among the A1 packs, as before
        assertEquals(listOf("family", "in-the-pub", "weather-and-garden"), PackSession.order(all, "A1").map { it.id })
        assertEquals(listOf("at-work", "the-local-news", "idioms-of-the-dale"), PackSession.later(all, "A1").map { it.id })
        // A2: B1 is a level up, in the list; B2 waits
        assertEquals(listOf("at-work", "family", "in-the-pub", "the-local-news", "weather-and-garden"), PackSession.order(all, "A2").map { it.id })
        assertEquals(listOf("idioms-of-the-dale"), PackSession.later(all, "A2").map { it.id })
        // B1 and up: nothing waits
        assertTrue(PackSession.later(all, "B1").isEmpty())
        assertEquals(all.map { it.id }, PackSession.order(all, "B1").map { it.id })
        // without a level (as before): everything in the list
        assertEquals(all.map { it.id }, PackSession.order(all).map { it.id })
    }

    @Test fun `a pack the learner started, a tutor's, a festival's and a project's never wait for later`() {
        val started = idioms.copy(learned = 1)
        val tutor = pack("tutor-b2", "B2", source = "tutor")
        val feast = pack("feast-b2", "B2", festival = "may_day")
        val project = pack("project-b2", "B2", project = "mlaj")
        val list = listOf(family, started, tutor, feast, project)
        assertFalse(PackSession.isLater(started, "A1"))
        assertFalse(PackSession.isLater(tutor, "A1"))
        assertFalse(PackSession.isLater(feast, "A1"))
        assertFalse(PackSession.isLater(project, "A1"))
        assertTrue(PackSession.later(list, "A1").isEmpty())
        // started first (in progress), then the untouched; the festival's and the project's have their own sections
        assertEquals(listOf("idioms-of-the-dale", "family", "tutor-b2"), PackSession.order(list, "A1").map { it.id })
    }

    @Test fun `with only A1 and A2 packs nothing changes for any level`() {
        val old = listOf(family, pub.copy(learned = 4), weather, pack("greetings", "A1", learned = 20))
        for (level in listOf("A1", "A2", "B1", "B2")) {
            assertEquals(level, PackSession.order(old).map { it.id }, PackSession.order(old, level).map { it.id })
            assertTrue(level, PackSession.later(old, level).isEmpty())
            assertEquals(level, "in-the-pub", PackSession.suggest(old, level)?.id)
            assertEquals(level, HomeLogic.words(old, emptySet()), HomeLogic.words(old, emptySet(), level))
        }
        // and the suggestion after the started one, for a learner above every pack: the first left, as before
        val notStarted = old.map { if (it.id == "in-the-pub") it.copy(learned = 0) else it }
        assertEquals("family", PackSession.suggest(notStarted, "B1")?.id)
    }

    @Test fun `the home tile counts the words left for now, those for later only when nothing else is left`() {
        // A1: family, in-the-pub and weather (60 words); the B1 and B2 packs' 60 wait
        assertEquals(HomeLogic.words(listOf(family, pub, weather), emptySet()), HomeLogic.words(all, emptySet(), "A1"))
        val rest = all.map { if (it.level in setOf("A1", "A2")) it.copy(learned = it.total) else it }
        assertEquals(HomeLogic.words(rest, emptySet()), HomeLogic.words(rest, emptySet(), "A1"))
    }
}
