package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Resident

/** The ages are milestones on the way to the learner's target level (GAME.md, "Ages"), and they need friends. */
class AgesTest {
    private val t0 = noon(day0)

    private fun words(target: String) = Age.entries.drop(1).map { Catalog.wordsFor(it, target) }

    @Test fun `word milestones follow the target level`() {
        // Tabor 10; Zaselek 3 % (at least 40); Vas 15 %; Trg 50 %; Mesto 100 %
        assertEquals(listOf(10, 60, 300, 1000, 2000), words("B2"))
        assertEquals(listOf(10, 40, 150, 500, 1000), words("B1"))
        assertEquals(listOf(10, 105, 525, 1750, 3500), words("C1"))
        assertEquals(listOf(10, 150, 750, 2500, 5000), words("C2"))
        // A2 (300 words): Zaselek's minimum and Vas's share come close, but each age still needs more than the last
        assertEquals(listOf(10, 40, 45, 150, 300), words("A2"))
        assertEquals(words("A2"), words("A1"))
        assertEquals(words("B2"), words(Catalog.DEFAULT_TARGET))
        assertEquals(0, Catalog.wordsFor(Age.OGENJ, "B2"))
        for (t in listOf("A1", "A2", "B1", "B2", "C1", "C2", "?")) {
            val w = words(t)
            assertTrue("$t: $w", w.zipWithNext().all { (a, b) -> b > a })
        }
    }

    @Test fun `the age check uses the target's milestones`() {
        val s = GameState(age = Age.ZASELEK, bonds = mapOf("micka" to Bond(30), "luka" to Bond(40)))
        val b1 = GameEngine.advanceCheck(s, 160, "B1").steps.first { it.kind == AgeStep.Kind.WORDS }
        assertEquals(160 to 150, b1.have to b1.need)
        assertTrue(b1.done)
        val b2 = GameEngine.advanceCheck(s, 160, "B2").steps.first { it.kind == AgeStep.Kind.WORDS }
        assertEquals(300, b2.need)
        assertFalse(b2.done)
        assertTrue(GameEngine.advanceCheck(s, 160, "B2").missing.contains("300 besed · words (you know 160)"))
    }

    @Test fun `an age once reached is kept, whatever the target`() {
        // A village that reached Trg under the old milestones (500 words) keeps it with B2's 1000: only the next age waits.
        val trg = GameState(age = Age.TRG, resources = Res.entries.associateWith { 2000 })
        val check = GameEngine.advanceCheck(trg, 520, "B2")
        assertEquals(Age.MESTO, check.next)
        assertSame(trg, GameEngine.advance(trg, 520, t0, "B2"))
    }

    @Test fun `friends are a step of the later ages`() {
        assertEquals(listOf(0, 1, 3, 5, 8), Age.entries.drop(1).map { Catalog.ageRules.getValue(it).friends })
        val s = GameState(age = Age.TABOR, bonds = mapOf("micka" to Bond(12), "luka" to Bond(29)))
        val step = GameEngine.advanceCheck(s, 60).steps.first { it.kind == AgeStep.Kind.FRIENDS }
        assertEquals(0 to 1, step.have to step.need)
        assertEquals("💞", step.emoji)
        assertTrue(GameEngine.advanceCheck(s, 60).missing.contains("1 prijatelj · 1 friend (0/1)"))
        // Luka becomes a friend at 30 points ("Prijatelj · Friend"): the step is done
        val friend = s.copy(bonds = s.bonds + ("luka" to Bond(30)))
        assertTrue(GameEngine.advanceCheck(friend, 60).steps.first { it.kind == AgeStep.Kind.FRIENDS }.done)
        // Tabor itself needs no friends: no step
        assertTrue(GameEngine.advanceCheck(GameState(), 10).steps.none { it.kind == AgeStep.Kind.FRIENDS })
    }

    @Test fun `only friends who live here count`() {
        val bonds = mapOf("micka" to Bond(40), "luka" to Bond(80), "tone" to Bond(200))
        // nobody has a name yet (an older bridge): everyone Jan knows
        assertEquals(3, Bonds.friends(GameState(bonds = bonds)))
        // Tone doesn't live here (yet, or any more)
        val here = GameState(bonds = bonds, residents = listOf(Resident("micka", "2026-09-01"), Resident("luka", "2026-09-01")))
        assertEquals(2, Bonds.friends(here))
        assertEquals(0, Bonds.friends(GameState(bonds = mapOf("micka" to Bond(29)))))
    }

    @Test fun `friends in Slovene count forms`() {
        assertEquals("1 prijatelj · 1 friend", GameEngine.friendsText(1))
        assertEquals("2 prijatelja · 2 friends", GameEngine.friendsText(2))
        assertEquals("4 prijatelji · 4 friends", GameEngine.friendsText(4))
        assertEquals("6 prijateljev · 6 friends", GameEngine.friendsText(6))
        assertEquals("3 prijatelji · 3 friends", GameEngine.friendsText(3))
        assertEquals("5 prijateljev · 5 friends", GameEngine.friendsText(5))
        assertEquals("8 prijateljev · 8 friends", GameEngine.friendsText(8))
    }

    @Test fun `the step counts friends towards the progress`() {
        val s = GameState(age = Age.VAS, bonds = mapOf("micka" to Bond(30), "luka" to Bond(30)))
        val check = GameEngine.advanceCheck(s, 0, "B2")
        val friends = check.steps.first { it.kind == AgeStep.Kind.FRIENDS }
        assertEquals(0.4f, friends.share, 0.001f) // two of Trg's five
        assertNull(GameEngine.advanceCheck(GameState(age = Age.MESTO), 5000).next)
    }
}
