package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict

class UpgradeTest {
    private val rich = Res.entries.associateWith { 900 }
    private fun village(age: Age, vararg b: Building) =
        GameState(seed = 1, age = age, resources = rich, buildings = b.toList())

    private val tent = Building("tent-1", BuildingType.TENT, plot = 0)

    @Test fun `each age raises the top level by one`() {
        val camp = GameEngine.upgradeOption(village(Age.OGENJ, tent), "tent-1")!!
        assertFalse(camp.available)
        assertTrue(camp.reason!!.contains("Tabor"))
        for ((age, top) in listOf(Age.TABOR to 2, Age.ZASELEK to 3, Age.VAS to 4, Age.TRG to 5)) {
            assertTrue("$age allows level $top", GameEngine.upgradeOption(village(age, tent.copy(level = top - 1)), "tent-1")!!.available)
            if (top < Catalog.MAX_LEVEL) {
                val next = GameEngine.upgradeOption(village(age, tent.copy(level = top)), "tent-1")!!
                assertFalse("$age stops at level $top", next.available)
            }
        }
        assertNull(GameEngine.upgradeOption(village(Age.MESTO, tent.copy(level = Catalog.MAX_LEVEL)), "tent-1"))
    }

    @Test fun `upgrade pays the cost and raises the effect`() {
        val s = village(Age.TABOR, tent)
        val before = GameEngine.attributes(s).populationCap
        val up = GameEngine.upgrade(s, "tent-1", now = 1)
        assertEquals(2, up.buildings.single().level)
        assertEquals(900 - 50, up.res(Res.FOOD)) // tent costs 25 🌾, level 2 is 2×
        assertTrue(GameEngine.attributes(up).populationCap > before)
        assertEquals(mapOf(Res.FOOD to 175, Res.WOOD to 105), Catalog.upgradeCost(BuildingType.TENT, 5)) // 7×
    }

    @Test fun `damaged or unaffordable buildings are not upgraded`() {
        val broken = village(Age.VAS, tent.copy(damaged = true))
        assertSame(broken, GameEngine.upgrade(broken, "tent-1", now = 1))
        val poor = village(Age.VAS, tent).copy(resources = emptyMap())
        assertSame(poor, GameEngine.upgrade(poor, "tent-1", now = 1))
    }

    @Test fun `a damaged upgraded building loses its effect but keeps its level and the stock`() {
        val hayrack = Building("koz", BuildingType.KOZOLEC, 0, level = 3)
        val s = village(Age.ZASELEK, hayrack).copy(resources = mapOf(Res.FOOD to 800), morale = 50)
        assertEquals(500 + 330, GameEngine.attributes(s).caps[Res.FOOD]) // +150 × 2.2 at level 3
        val broken = s.copy(buildings = listOf(hayrack.copy(damaged = true)))
        assertEquals(500, GameEngine.attributes(broken).caps[Res.FOOD])
        // food above the smaller store stays, but nothing more comes in until the repair
        val (after, got) = GameEngine.earn(broken, List(5) { Res.FOOD to Verdict.CORRECT })
        assertEquals(800, after.res(Res.FOOD))
        assertTrue(got.isEmpty())
        assertFalse(GameEngine.upgradeOption(broken, "koz")!!.available)
        // a repair costs half the build cost at any level and brings the whole level back
        assertEquals(mapOf(Res.FOOD to 15, Res.WOOD to 25), GameEngine.repairCost(broken, "koz"))
        val fixed = GameEngine.repair(broken.copy(resources = rich), "koz", now = 1)
        assertEquals(3, fixed.buildings.single().level)
        assertEquals(830, GameEngine.attributes(fixed).caps[Res.FOOD])
    }

    @Test fun `an upgrade raises the store at once`() {
        val hayrack = Building("koz", BuildingType.KOZOLEC, 0)
        val full = village(Age.ZASELEK, hayrack).copy(resources = mapOf(Res.FOOD to 650, Res.WOOD to 900), morale = 50)
        assertTrue(GameEngine.earn(full, List(3) { Res.FOOD to Verdict.CORRECT }).second.isEmpty())
        val up = GameEngine.upgrade(full, "koz", now = 1) // level 2: 60 🌾 100 🪵
        assertEquals(590, up.res(Res.FOOD))
        assertEquals(500 + 240, GameEngine.attributes(up).caps[Res.FOOD])
        assertEquals(18, GameEngine.earn(up, List(3) { Res.FOOD to Verdict.CORRECT }).second[Res.FOOD])
    }

    @Test fun `nothing costs more than the base store of the age that unlocks it`() {
        for (spec in Catalog.specs.values) {
            val cap = Catalog.baseCap[spec.minAge.ordinal]
            spec.cost.forEach { (r, n) -> assertTrue("${spec.type}: $n ${r.emoji} > $cap", n <= cap) }
            for (level in 2..Catalog.MAX_LEVEL) {
                val age = maxOf(Catalog.upgradeAge(level), spec.minAge)
                val max = Catalog.baseCap[age.ordinal]
                Catalog.upgradeCost(spec.type, level).forEach { (r, n) -> assertTrue("${spec.type} → $level: $n ${r.emoji} > $max", n <= max) }
            }
        }
        for ((age, rule) in Catalog.ageRules) {
            val max = Catalog.baseCap[age.ordinal - 1]
            rule.cost.forEach { (r, n) -> assertTrue("${age.sl}: $n ${r.emoji} > $max", n <= max) }
        }
    }
}
