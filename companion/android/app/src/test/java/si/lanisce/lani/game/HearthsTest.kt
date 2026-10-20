package si.lanisce.lani.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.game.BuildingType.*
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.time.LocalDate
import java.time.ZoneId

/**
 * The village's hearths (companion/GAME.md "Hearths"): which buildings have one and what each burns, the wood a day at each
 * age before and after, the order they go out when wood runs short, the morale they cost, wood brought in lighting them
 * again, the storm blowing some out, an older save loading (the forge on Oglar Miha's charcoal and his pile's wood:
 * [KopaWoodTest]).
 */
class HearthsTest {
    @Before fun primorska() {
        Cultures.use("primorska")
        L10n.pair = LangPair(Lang.SL, Lang.EN)
    }

    private val day = LocalDate.of(2026, 7, 1)

    /** A village of [age] with [types] on plots 0, 1, … (built a year back) and [wood] 🪵. */
    private fun village(age: Age, vararg types: BuildingType, wood: Int = 500) = GameState(
        seed = 7, age = age, resources = mapOf(Res.FOOD to 1000, Res.WOOD to wood),
        buildings = types.mapIndexed { i, t -> Building("${t.name.lowercase()}-$i", t, i, builtAt = millis(day.minusDays(400))) },
        lastTick = day.minusDays(1).toString(), foundedOn = day.minusDays(400).toString(), villagers = 6, morale = 70,
    )

    private fun millis(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** [s]'s tick on [d], no event coming (a village founded yesterday: none in its first days). */
    private fun tick(s: GameState, d: LocalDate = day) = GameEngine.tick(s.copy(foundedOn = d.minusDays(1).toString()), d, noon(d), Fixtures.pool()).state

    // --- the table ------------------------------------------------------------------------------------------------------

    @Test fun `the hearths, the centre by the age, the huts, the houses, the school and the forge, in the order they are paid`() {
        val all = village(Age.VAS, TENT, HUT, FIELD, HOUSE, WELL, SMITHY, SCHOOL, CHURCH, MARKET, LIPA, KOZOLEC, BEEHIVE, WATCHTOWER, HUT, HOUSE)
        val list = Hearths.of(all)
        // the forge first (its own rule: charcoal), the school, the homes from the plot nearest the centre out, the centre's evening fire last
        assertEquals(listOf("smithy-5", "school-6", "hut-1", "house-3", "hut-13", "house-14", Hearths.CENTRE), list.map { it.id })
        assertEquals(listOf(HearthKind.FORGE, HearthKind.SCHOOL, HearthKind.HUT, HearthKind.HOUSE, HearthKind.HUT, HearthKind.HOUSE, HearthKind.FIRE_RING), list.map { it.kind })
        assertEquals(listOf(0, 1, 1, 2, 1, 2, 1), list.map { it.wood })
        assertTrue("the forge burns charcoal in Primorska", list.first().charcoal)
        // no hearth: the tent, the field, the well, the church, the market, the linden, the hayrack, the bee house, the tower, the palisade
        assertEquals(7, list.size)
        // the centre by the age: the campfire to Zaselek (the village's first, paid first), the fire ring at Vas, the fire basket from Trg
        assertEquals(HearthKind.CAMPFIRE, Hearths.of(village(Age.TABOR, TENT)).single().kind)
        assertEquals(Hearths.CENTRE, Hearths.of(village(Age.ZASELEK, HUT)).first().id)
        assertEquals(HearthKind.FIRE_RING, Hearths.of(village(Age.VAS)).single().kind)
        assertEquals(HearthKind.FIRE_BASKET, Hearths.of(village(Age.TRG)).single().kind)
        assertEquals(HearthKind.FIRE_BASKET, Hearths.of(village(Age.MESTO)).single().kind)
        // a damaged building's hearth is cold and doesn't count
        val broken = all.copy(buildings = all.buildings.map { if (it.id == "house-3") it.copy(damaged = true) else it })
        assertFalse(Hearths.of(broken).any { it.id == "house-3" })
        // the names by the age
        assertEquals("Ogenj · The campfire", Hearths.centreName(Age.TABOR))
        assertEquals("Ognjišče · The fire ring", Hearths.centreName(Age.VAS))
        assertEquals("Žerjavnica · The fire basket", Hearths.centreName(Age.TRG))
        assertEquals("ob ognjišču · by the fire ring", Hearths.centreAt(Age.VAS))
        assertEquals("na trgu · on the square", Hearths.centreAt(Age.MESTO))
    }

    @Test fun `a culture without a charcoal burner feeds its forge wood`() {
        Cultures.use("friuli")
        val f = Hearths.of(village(Age.VAS, SMITHY)).first()
        assertFalse(f.charcoal)
        assertEquals(Catalog.FORGE_WOOD, f.wood)
        assertNull(Hearths.charcoal(village(Age.VAS, SMITHY)))
    }

    /**
     * The wood a day at each age, before (the one fire: 2 + age index) and after (the hearths, all lit), for villages like the
     * simulation's (SimulationTest's hearthLines has the runs' own).
     */
    @Test fun `wood a day at each age, before and after`() {
        val villages = listOf(
            Age.OGENJ to village(Age.OGENJ, TENT),
            Age.TABOR to village(Age.TABOR, TENT, TENT, FIELD, HUT, KOZOLEC),
            Age.ZASELEK to village(Age.ZASELEK, TENT, FIELD, HUT, KOZOLEC, LIPA, HUT, CHURCH, SMITHY, SCHOOL, WELL),
            Age.VAS to village(Age.VAS, TENT, FIELD, HUT, KOZOLEC, LIPA, HUT, CHURCH, SMITHY, SCHOOL, WELL, MARKET, HOUSE, KOZOLEC, WATCHTOWER),
            Age.TRG to village(Age.TRG, TENT, FIELD, HUT, KOZOLEC, LIPA, HUT, CHURCH, SMITHY, SCHOOL, WELL, MARKET, HOUSE, KOZOLEC, WATCHTOWER, HOUSE, HOUSE, HOUSE, HOUSE, BEEHIVE, BEEHIVE),
            Age.MESTO to village(Age.MESTO, TENT, FIELD, HUT, KOZOLEC, LIPA, HUT, CHURCH, SMITHY, SCHOOL, WELL, MARKET, HOUSE, KOZOLEC, WATCHTOWER, HOUSE, HOUSE, HOUSE, HOUSE, BEEHIVE, BEEHIVE, HOUSE, HOUSE, HOUSE, HOUSE, FIELD, FIELD, BEEHIVE, HOUSE),
        )
        val before = villages.map { (a, _) -> 2 + a.ordinal }
        val after = villages.map { (_, s) -> Hearths.need(s) }
        println("wood a day before $before, after $after; the pile's wood ${Age.entries.map { Catalog.kopaWood(it) }}")
        assertEquals(listOf(2, 3, 4, 5, 6, 7), before)
        // the camp's fire as before; the hamlet's warmth moves into its huts and the school at the same 4 a day; then it grows with the houses
        assertEquals(listOf(2, 4, 4, 6, 14, 24), after)
        assertEquals(after, villages.map { (_, s) -> GameEngine.attributes(s).woodUpkeep })
        // half from the 4th missed day
        assertEquals(7, Hearths.need(villages[4].second, soft = true))
        // the charcoal pile's wood: 15 % of the age's store
        assertEquals(listOf(75, 75, 75, 120, 180, 270), Age.entries.map { Catalog.kopaWood(it) })
    }

    // --- short of wood ------------------------------------------------------------------------------------------------

    @Test fun `when wood runs short the outlying hearths go out first, the centre's evening fire before them, the forge by its charcoal`() {
        // Vas: the forge (charcoal), the school 1, hut 1, house 2, hut 1, house 2, the ring 1 = 8 a day
        val vas = village(Age.VAS, HUT, HOUSE, SMITHY, SCHOOL, HUT, HOUSE, wood = 5)
        assertEquals(8, Hearths.need(vas))
        val t = tick(vas)
        // 5 🪵: the school (1), the first hut (1), the first house (2), the second hut (1); the outlying house and the ring go out
        assertEquals(listOf("house-5", Hearths.CENTRE), t.hearths!!.out)
        assertEquals(0, t.res(Res.WOOD))
        assertTrue("the forge is on charcoal", "smithy-2" in Hearths.lit(t))
        assertTrue(Hearths.short(t))
        // 5 of 7 lit: the old fire field from the share, for an older app
        assertEquals(Hearths.fire(5 / 7f), t.fire)
        assertEquals(74, t.fire)
        // the hamlet: the campfire stays lit longest of the wood hearths
        val zaselek = tick(village(Age.ZASELEK, HUT, HUT, SCHOOL, wood = 2))
        assertEquals(listOf("hut-0", "hut-1"), zaselek.hearths!!.out)
        // the camp: the campfire alone; without wood it smoulders (out, the fire field at its floor)
        val camp = tick(village(Age.TABOR, TENT, wood = 0))
        assertEquals(listOf(Hearths.CENTRE), camp.hearths!!.out)
        assertEquals(GameState.MIN_FIRE, camp.fire)
    }

    @Test fun `a village that burns more than it gathers feels it, one hearth after another goes out`() {
        var s = village(Age.TRG, HUT, HOUSE, HOUSE, HOUSE, HOUSE, SCHOOL, wood = 40)
        val outs = ArrayList<Int>()
        for (i in 0 until 6) {
            s = tick(s, day.plusDays(i.toLong()))
            outs += s.hearths!!.out.size
        }
        // 11 a day from 40: lit three days, then the store pays fewer and fewer, the outlying first
        assertEquals(listOf(0, 0, 0, 3, 6, 7), outs)
    }

    @Test fun `morale follows the share of hearths lit`() {
        // at its target (50: nothing here raises it), all lit: it stays
        val warm = village(Age.VAS, HUT, HOUSE, HUT, HOUSE, wood = 100).copy(morale = 50)
        assertEquals(50, tick(warm).morale)
        // none lit: −6
        val cold = warm.copy(resources = warm.resources + (Res.WOOD to 0))
        assertEquals(50 - 6, tick(cold).morale)
        // three of five out: −6 × 3/5 = −4
        val some = warm.copy(resources = warm.resources + (Res.WOOD to 3))
        assertEquals(listOf("hut-2", "house-3", Hearths.CENTRE), tick(some).hearths!!.out)
        assertEquals(50 - 4, tick(some).morale)
        // from the 4th missed day gentler: −2 × the share
        val away = cold.copy(lastTick = day.minusDays(5).toString(), morale = 80)
        val back = GameEngine.tick(away, day, noon(day), Fixtures.pool()).state
        assertTrue("morale ${back.morale}", back.morale >= SOFT_MORALE_FLOOR)
    }

    @Test fun `wood brought in lights the hearths that went out again, in their order`() {
        val t = tick(village(Age.VAS, HUT, HOUSE, HUT, HOUSE, wood = 0))
        assertEquals(5, t.hearths!!.out.size)
        // a run's 🪵: the hearths light again as far as it pays their day
        val (more, got) = credit(t, mapOf(Res.WOOD to 4))
        assertEquals(4, got[Res.WOOD])
        assertEquals(listOf("house-3", Hearths.CENTRE), more.hearths!!.out)
        assertEquals(0, more.res(Res.WOOD))
        assertEquals(Hearths.fire(3 / 5f), more.fire)
        val (all, _) = credit(more, mapOf(Res.WOOD to 10))
        assertTrue(all.hearths!!.out.isEmpty())
        assertEquals(7, all.res(Res.WOOD))
        assertEquals(100, all.fire)
        // nothing to light: the wood stays in the store
        assertEquals(17, credit(all, mapOf(Res.WOOD to 10)).first.res(Res.WOOD))
    }

    @Test fun `a lost storm blows out hearths, the outlying first, until the next day`() {
        val t = tick(village(Age.VAS, HUT, HOUSE, HUT, HOUSE, wood = 100))
        val blown = Hearths.blowOut(t, 2)
        assertEquals(listOf("house-3", "hut-2"), blown.hearths!!.blown)
        assertEquals(blown.hearths!!.blown, blown.hearths!!.out)
        assertFalse("blown out, not short of wood", Hearths.short(blown))
        // wood brought today doesn't light them
        assertEquals(blown.hearths!!.out, credit(blown, mapOf(Res.WOOD to 50)).first.hearths!!.out)
        // the next day they burn again
        assertTrue(tick(blown, day.plusDays(1)).hearths!!.out.isEmpty())
        // the storm event: ⌈str/2⌉ of them
        val e = GameEvent("e", EventKind.STORM, strength = 3, startedAt = 0, deadline = 1)
        val (lost, _) = Events.outcome(t.copy(event = e), e, won = false, expired = true, now = noon(day))
        assertEquals(2, lost.hearths!!.blown.size)
    }

    // --- saved villages -------------------------------------------------------------------------------------------------

    @Test fun `a save from before the hearths loads, derives them from its buildings and wood, and keeps writing fire`() {
        // an older app's save: no hearths, its fire at 100 (Jan's village at Vas)
        val old = village(Age.VAS, HUT, HOUSE, SMITHY, SCHOOL, HUT, HOUSE, LIPA, CHURCH, wood = 300).copy(fire = 100, lastTick = day.toString())
        val json = Json { encodeDefaults = true }
        val text = json.encodeToString(GameState.serializer(), old).let { s ->
            JsonObject(Json.parseToJsonElement(s).jsonObject - "hearths").toString()
        }
        val read = si.lanisce.lani.data.json.decodeFromString(GameState.serializer(), text)
        assertNull(read.hearths)
        // derived: everything the store pays a day of is lit, the forge on the charcoal of its piles
        assertEquals(Hearths.of(read).map { it.id }.toSet(), Hearths.lit(read, day))
        assertTrue((Hearths.charcoal(read, day) ?: 0) > 0)
        // a tick the same day decides them without paying (the day's wood was the old fire's) and writes the fire for an older app
        val same = tick(read)
        assertNotNull(same.hearths)
        assertEquals(300, same.res(Res.WOOD))
        assertEquals(100, same.fire)
        assertEquals(day.toString(), same.hearths!!.since)
        // the next day: paid in order
        val next = tick(same, day.plusDays(1))
        assertEquals(300 - Hearths.need(next), next.res(Res.WOOD))
        // an older app reading the new save ignores the hearths (its fire as this app wrote it)
        val newer = si.lanisce.lani.data.json.encodeToString(GameState.serializer(), next)
        assertTrue(newer.contains("\"hearths\""))
        assertEquals(100, (Json.parseToJsonElement(newer).jsonObject["fire"] as JsonPrimitive).content.toInt())
    }
}
