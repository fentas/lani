package si.lanisce.lani.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.app.FirstRun.Ask
import si.lanisce.lani.game.FirstWords
import si.lanisce.lani.game.LevelPlacement
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/** What the first run leaves for later (app/FirstRun, docs/plans/05-built/first-minutes.md): each asked when it matters, once. */
class FirstRunTest {
    private val here = FirstRun().begin(onDevice = true)
    private val paired = FirstRun().begin(onDevice = false)

    @Test fun `an existing learner sees none of it, on nothing kept, with no migration`() {
        for (f in listOf(FirstRun(), FirstRun.decode(null), FirstRun.decode(""))) {
            assertFalse(f.fresh)
            assertFalse(f.moment)
            assertFalse(f.pace(practised = true, child = false))
            assertFalse(f.voiceAfterDialog)
            assertFalse(f.tutor(ready = false))
            assertFalse("Jan's and Teo's notifications as ever", f.holdsNotifications(practised = false, child = false))
        }
    }

    @Test fun `the moment comes once after the founding`() {
        assertTrue(here.moment)
        assertTrue(paired.moment)
        assertFalse(here.settle(Ask.MOMENT).moment)
    }

    @Test fun `the aim and the minutes wait for a finished session, and come once, skipped or answered`() {
        assertFalse(here.pace(practised = false, child = false))
        assertTrue(here.pace(practised = true, child = false))
        assertFalse(here.settle(Ask.PACE).pace(practised = true, child = false))
    }

    @Test fun `a child isn't asked them, and a family's computer asked them already`() {
        assertFalse(here.pace(practised = true, child = true))
        assertFalse(paired.pace(practised = true, child = false))
    }

    @Test fun `the voice waits for the end of the first dialog, once`() {
        assertTrue(here.voiceAfterDialog)
        assertTrue(paired.voiceAfterDialog)
        assertFalse(here.settle(Ask.VOICE).voiceAfterDialog)
    }

    @Test fun `the tutor's ways come the first time, on this device alone, with none set up`() {
        assertTrue(here.tutor(ready = false))
        assertFalse(here.tutor(ready = true))
        assertFalse(here.settle(Ask.TUTOR).tutor(ready = false))
        assertFalse("a family's computer has its tutor", paired.tutor(ready = false))
    }

    @Test fun `the school's card, dismissed, stays dismissed for anyone`() {
        assertTrue(FirstRun().placementCard)
        assertFalse(FirstRun().settle(Ask.PLACEMENT).placementCard)
        assertFalse(FirstRun.decode(FirstRun().settle(Ask.PLACEMENT).encode()).placementCard)
        // a course with no village yet: the card, and the row once dismissed
        val course = si.lanisce.lani.data.Courses.bundled("sl")
        assertEquals(LevelPlacement.Offer.CARD, LevelPlacement.offer(null, course))
        assertEquals(LevelPlacement.Offer.ROW, LevelPlacement.offer(null, course, dismissed = true))
    }

    @Test fun `the system's notification question waits for the first minutes to be over`() {
        assertTrue("not over the founding", here.holdsNotifications(practised = false, child = false))
        assertTrue("not over the question after the first session", here.holdsNotifications(practised = true, child = false))
        assertFalse(here.settle(Ask.PACE).holdsNotifications(practised = true, child = false))
        // nobody asks a child or a paired learner that question: the first session ends the wait
        assertTrue(here.holdsNotifications(practised = false, child = true))
        assertFalse(here.holdsNotifications(practised = true, child = true))
        assertFalse(paired.holdsNotifications(practised = true, child = false))
    }

    @Test fun `it survives the app, and a first run begins once`() {
        val f = here.settle(Ask.MOMENT).settle(Ask.TUTOR)
        assertEquals(f, FirstRun.decode(f.encode()))
        assertEquals(paired, FirstRun.decode(paired.encode()))
        assertEquals("the way it began stays", f, f.begin(onDevice = false))
    }

    @Test fun `the question offers the aims around the one marked`() {
        assertEquals(listOf("B1", "B2", "C1"), Aim.near("A1", "B2"))
        assertEquals(listOf("A2", "B1"), Aim.near("A1", "A2"))
        assertEquals(listOf("C2"), Aim.near("C2", "C2"))
    }

    // --- the first words ---------------------------------------------------------------------------------------------

    private val pairs = Lang.entries.flatMap { t -> Lang.entries.filter { it != t }.map { LangPair(t, it) } }

    @Test fun `in all 12 pairs a greeting said aloud, one right answer and something to do`() {
        for (p in pairs) {
            val choices = FirstWords.choices(p)
            assertEquals("$p", 2, choices.size)
            assertEquals("$p", FirstWords.greeting(p.target), choices.single { it.right }.text)
            assertTrue("$p", choices.map { it.text }.distinct().size == 2)
            val opening = FirstWords.opening(p, arrive = { "somewhere in ${it.code}" })
            assertEquals("$p the greeting is said in the language learned", p.target, opening.first { it.text == FirstWords.greeting(p.target) }.lang)
            assertEquals("$p what to do is in the base", L10n.text(p.base, "firstRun.sayBack", target = p.target), opening.last().text)
            assertTrue("$p the win", FirstWords.won(p).any { !it.target })
        }
    }

    @Test fun `each language has its own greeting`() {
        assertEquals(listOf("Dober dan!", "Hello!", "Buongiorno!", "Guten Tag!"), listOf(Lang.SL, Lang.EN, Lang.IT, Lang.DE).map(FirstWords::greeting))
    }

    @Test fun `a wrong tap hears the greeting again`() {
        val again = FirstWords.again(LangPair.DEFAULT)
        assertEquals("Dober dan!", again.last().text)
        assertEquals(Lang.EN, again.first().lang)
    }
}
