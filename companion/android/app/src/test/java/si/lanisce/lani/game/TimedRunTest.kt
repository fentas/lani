package si.lanisce.lani.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.stage.Intros
import kotlin.random.Random

/**
 * A run against the clock (companion/GAME.md, "Events"): the time from what its questions take when it listens, never
 * less than the old flat time, in half minutes; a listening run mostly heard, at most every third question written, a
 * short phrase's dictation untimed and by ear against the clock (word chips, a gap); the intro's announcement.
 */
class TimedRunTest {
    private val pair = L10n.pair

    @After fun restore() {
        L10n.pair = pair
    }

    private fun heard(s: String) = Exercise.Choice("", listOf("a", "b"), 0, audio = s)
    private val chips = Exercise.Reorder("", listOf("Od", "kod", "ste?"), listOf(listOf("Od", "kod", "ste?")), audio = "Od kod ste?")
    private val gap = Exercise.Cloze("Dober ___", listOf("dan"), audio = "Dober dan")

    @Test fun `a run that listens gets 60 percent of what its questions take, in half minutes, never less than before`() {
        // a storm of 6, 8 and 10: its written ones at the third, the sixth, the ninth
        fun storm(n: Int) = (0 until n).map { i -> if (ListeningMix.typedAt(i)) (if (i % 2 == 0) gap else chips) else heard("x") }
        assertEquals(listOf(90, 120, 150), listOf(6, 8, 10).map { RunTime.limit(storm(it)) })
        // the old flat time for these: 74, 82 and 90 s
        assertEquals(listOf(74, 82, 90), listOf(6, 8, 10).map(RunTime::flat))
        // a run without listening: the flat time, only up to the half minute
        val tiles = List(8) { Exercise.Reorder("x", listOf("a", "b"), listOf(listOf("a", "b"))) }
        assertEquals(90, RunTime.limit(tiles))
        assertFalse(tiles.any(RunTime::heard))
        assertTrue(RunTime.heard(chips) && RunTime.heard(gap) && RunTime.heard(heard("x")) && RunTime.heard(Exercise.Dictation("a", listOf("a"))))
    }

    @Test fun `no event's clock is harsher than before`() {
        val pool = Fixtures.pool()
        for (kind in EventKind.entries) for (strength in 1..5) {
            val s = GameEngine.newGame(1, 0L).copy(age = Age.MESTO, event = GameEvent("ev-$kind-$strength", kind, strength, 0L, 1L))
            val c = GameEngine.eventChallenge(s, pool)!!
            val time = c.timeLimitSeconds!!
            assertTrue("$kind $strength: $time", time >= RunTime.flat(c.exercises.size) && time % RunTime.STEP == 0)
            assertEquals(RunTime.limit(c.exercises), time)
            assertFalse("$kind: ${c.intro}", Regex("\\d+ s\\b").containsMatchIn(c.intro))
        }
    }

    @Test fun `a storm is mostly heard, at most every third written by ear, never a dictation`() {
        val pool = Fixtures.pool()
        for (strength in 1..5) {
            val s = GameEngine.newGame(1, 0L).copy(age = Age.MESTO, event = GameEvent("ev-storm-$strength", EventKind.STORM, strength, 0L, 1L))
            val c = GameEngine.eventChallenge(s, pool)!!
            assertEquals(5 + strength, c.exercises.size)
            c.exercises.forEachIndexed { i, ex ->
                assertFalse("$i: $ex", ex is Exercise.Dictation)
                val byEar = ex is Exercise.Reorder || ex is Exercise.Cloze
                if (byEar) assertTrue("$i: by ear only every third", ListeningMix.typedAt(i))
                assertTrue("$i: $ex", ex.heard != null)
            }
            assertTrue(c.skills.all { it == Res.WOOD })
            assertTrue("some by ear: ${c.exercises}", c.exercises.any { it is Exercise.Reorder || it is Exercise.Cloze })
        }
    }

    @Test fun `untimed, a listening run's written ones are short dictations, at most every third`() {
        val pool = Fixtures.pool()
        repeat(20) { k ->
            val c = GameEngine.gatherChallenge(GameEngine.newGame(k.toLong(), 0L).copy(lastTick = "2026-09-0${k % 9 + 1}"), Res.WOOD, pool)
            assertNull(c.timeLimitSeconds)
            c.exercises.forEachIndexed { i, ex ->
                if (ex is Exercise.Dictation) {
                    assertTrue("$i", ListeningMix.typedAt(i))
                    assertTrue(ex.audio, ListeningMix.short(ex.audio))
                }
                assertFalse(ex is Exercise.Reorder || ex is Exercise.Cloze)
            }
        }
        // a module's long dictation is left out; against the clock its own is written by ear
        val long = Exercise.Dictation("Včeraj sem šel z bratom na dolgo pot", listOf("Včeraj sem šel z bratom na dolgo pot"))
        val withModule = Fixtures.pool(modules = listOf("m" to long))
        val untimed = Content.pick(Res.WOOD, 7, withModule, Random(1))
        assertTrue(untimed.items.none { it.exercise == long })
        val timed = Content.pick(Res.WOOD, 7, withModule, Random(1), timed = true)
        assertTrue(timed.items.any { it.moduleId == "m" && (it.exercise is Exercise.Reorder || it.exercise is Exercise.Cloze) && it.exercise.heard == long.audio })
    }

    @Test fun `the mix, the chips and the gap`() {
        assertEquals(listOf(false, false, true, false, false, true, false, false, true), (0 until 9).map(ListeningMix::typedAt))
        assertEquals(listOf(2, 2, 3), listOf(6, 8, 10).map(ListeningMix::typed))
        assertEquals(listOf("h1", "h2", "t1", "h3", "h4", "t2", "h5"), ListeningMix.arrange(7, listOf("t1", "t2", "t3"), listOf("h1", "h2", "h3", "h4", "h5")))
        // no written one left: a heard one; no heard one left: the run is shorter
        assertEquals(listOf("h1", "h2", "h3", "h4"), ListeningMix.arrange(4, emptyList(), listOf("h1", "h2", "h3", "h4", "h5")))
        assertEquals(listOf("h1", "h2", "t1"), ListeningMix.arrange(6, listOf("t1", "t2"), listOf("h1", "h2")))
        assertTrue(ListeningMix.short("Od kod ste?") && ListeningMix.short("Dober dan, gospa Micka!"))
        assertFalse(ListeningMix.short("Včeraj sem šel z bratom na pot."))
        // word chips by ear: the words and one or two others
        val c = ListeningMix.chips("Od kod ste?", listOf("Od kod ste?"), listOf("kod", "hvala", "prosim", "dan"), "i", extras = 2)!!
        assertEquals(listOf("Od", "kod", "ste?"), c.tokens)
        assertEquals(listOf("hvala", "prosim"), c.distractors)
        assertEquals("Od kod ste?", c.audio)
        assertEquals(1, ListeningMix.chips("Od kod ste?", emptyList(), listOf("hvala", "dan"), "i", extras = 1)!!.distractors.size)
        assertNull(ListeningMix.chips("Dober dan", emptyList(), listOf("hvala"), "i"))
        // a gap by ear: one word missing, its punctuation kept
        val g = ListeningMix.gap("Dober dan, gospa!", 2, "i")!!
        assertEquals("Dober dan, ___!", g.text)
        assertEquals(listOf("gospa"), g.accept)
        assertEquals("Dober dan, gospa!", g.audio)
        assertEquals(listOf(0, 1, 3), ListeningMix.gapWords("Dober dan, se gospa!"))
        assertNull(ListeningMix.gap("hvala", 0, "i"))
    }

    @Test fun `the intro says it plainly`() {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        assertEquals("⏱ Izziv na čas: 8 vprašanj, 2 minuti · A timed challenge: 8 questions, 2 minutes", Intros.timed(8, 120).line)
        assertEquals("1 minuta in pol", Intros.duration(Lang.SL, 90))
        assertEquals("2 minuti in pol", Intros.duration(Lang.SL, 150))
        assertEquals("5 minut", Intros.duration(Lang.SL, 300))
        assertEquals("2½ minutes", Intros.duration(Lang.EN, 150))
        assertEquals("1 minute", Intros.duration(Lang.EN, 60))
        assertEquals("1½ Minuten", Intros.duration(Lang.DE, 90))
        assertEquals("1 minuto e mezzo", Intros.duration(Lang.IT, 90))
        assertEquals("pol minute", Intros.duration(Lang.SL, 30))
        assertTrue(Intros.timed(6, 90).how.contains("3, 2, 1"))
    }
}
