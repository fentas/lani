package si.lanisce.lani.game.villagers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.QuestSource
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.scene.TownPlace
import java.time.LocalDate

class BondsTest {
    private val day = LocalDate.of(2026, 9, 24)
    private val s0 = GameState(seed = 1, age = Age.TABOR)

    @Test fun `levels grow with points`() {
        assertEquals(0, Bonds.level(0))
        assertEquals(1, Bonds.level(10))
        assertEquals(2, Bonds.level(45))
        assertEquals(4, Bonds.level(10_000))
        assertEquals(0.5f, Bonds.progress(20), 0.001f)
        assertEquals(1f, Bonds.progress(500), 0.001f)
    }

    @Test fun `helping adds points and a memory, meeting is remembered`() {
        val m = Memory(day.toString(), "ko si mi pomagal najti Belo", "when you helped me find Bela", "quest")
        val s = Bonds.add(s0, "luka", Bonds.QUEST, day, m)
        val b = s.bonds.getValue("luka")
        assertEquals(10, b.points)
        assertEquals(listOf(m), b.memories)
        assertEquals("2026-09-24", b.met)
        assertEquals(1, Bonds.level(s, "luka"))
    }

    @Test fun `training points are capped per day`() {
        var s = s0
        repeat(5) { s = Bonds.add(s, "micka", Bonds.TRAINING, day, training = true) }
        assertEquals(Bonds.TRAINING_PER_DAY, s.bonds.getValue("micka").points)
        s = Bonds.add(s, "micka", Bonds.TRAINING, day.plusDays(1), training = true)
        assertEquals(Bonds.TRAINING_PER_DAY + 1, s.bonds.getValue("micka").points)
    }

    @Test fun `memories are capped, newest kept`() {
        var s = s0
        repeat(Bonds.MAX_MEMORIES + 5) { s = Bonds.add(s, "janez", 1, day, Memory(day.toString(), "m$it")) }
        val mem = s.bonds.getValue("janez").memories
        assertEquals(Bonds.MAX_MEMORIES, mem.size)
        assertEquals("m${Bonds.MAX_MEMORIES + 4}", mem.last().sl)
    }

    @Test fun `warmer lines unlock with the friendship`() {
        val pool = listOf(VillagerLine("Dober dan.", level = 0), VillagerLine("Živjo, prijatelj!", level = 2))
        assertEquals("Dober dan.", Bonds.linesFor(pool, 0).first().target)
        assertEquals("Živjo, prijatelj!", Bonds.linesFor(pool, 3).first().target)
    }

    @Test fun `villagers wait at their home, a spot when nothing is built`() {
        val tone = Villager("tone", "Kovač Tone", "⚒️", "smith", home = listOf("smithy", "spot:woodpile"))
        assertEquals(TownPlace.Spot("woodpile"), TownMarkers.homeOf(tone, s0))
        val withSmithy = s0.copy(buildings = listOf(Building("s", BuildingType.SMITHY, 0)))
        assertEquals(TownPlace.At(BuildingType.SMITHY), TownMarkers.homeOf(tone, withSmithy))
        assertEquals(TownPlace.Spot("woodpile"), TownMarkers.questPlace("Kovač Tone", QuestSource.LOCAL, s0, listOf(tone)))
        assertTrue(TownMarkers.has(s0, TownPlace.of("spot:meadow")))
        val mojca = Villager("mojca", "Učiteljica Mojca", "👩‍🏫", "teacher", home = listOf("school"))
        assertEquals("a visitor until the school stands", TownPlace.Spot("road"), TownMarkers.homeOf(mojca, s0))
    }
}
