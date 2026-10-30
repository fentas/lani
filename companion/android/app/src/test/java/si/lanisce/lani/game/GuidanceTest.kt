package si.lanisce.lani.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.TownMarker
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.game.speech
import si.lanisce.lani.ui.game.text
import java.io.File
import java.time.LocalDate

/**
 * "💡 Namigi · Tips" (companion/GAME.md "Guidance"): the catalogue's moments on village states (a store full, the first of
 * each building, an age, the learning moments), the queue (one at a time, the priorities, a quiet moment, never in an
 * exercise or a threat, three a day), the update rule (a village from before: its moments past come once, one a day), what
 * the village keeps (dealt with, later, again, off, the tutor's pointer, a JSON round trip, a village founded anew).
 */
class GuidanceTest {
    private val today = LocalDate.of(2026, 10, 30)
    private val t0 = today.atTime(10, 0).toEpochSecond(java.time.ZoneOffset.UTC) * 1000
    private val minute = 60_000L

    @After fun back() {
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = LangPair.DEFAULT
    }

    /** A hamlet (stores of 500) founded a month ago, a field and a palisade (it has faced the wolves), little in store. */
    private fun hamlet(vararg extra: BuildingType, res: Map<Res, Int> = emptyMap(), founded: String = "2026-09-30") = GameState(
        age = Age.ZASELEK, foundedOn = founded, lastTick = today.toString(), stats = GameStats(eventsWon = 1),
        resources = mapOf(Res.FOOD to 40, Res.WOOD to 40, Res.STONE to 40, Res.WISDOM to 40) + res,
        buildings = (listOf(BuildingType.FIELD, BuildingType.PALISADE) + extra).mapIndexed { i, t -> Building("b$i", t, if (t.onPlot) i else NO_PLOT) },
    )

    private fun facts(now: Long = t0, day: LocalDate = today, ready: Boolean = true, canBuild: Set<BuildingType> = emptySet(), markers: List<TownMarker> = emptyList(), lesson: String? = null, due: Int = 0, reviewed: Boolean = true) =
        GuideFacts(day, now, ready = ready, canBuild = canBuild, markers = markers, lesson = lesson, due = due, reviewed = reviewed, chatted = true)

    private fun cap(s: GameState, r: Res) = GameEngine.attributes(s).caps.getValue(r)

    private fun full(s: GameState, r: Res) = s.copy(resources = s.resources + (r to cap(s, r)))

    // --- the catalogue ---------------------------------------------------------------------------------------------------

    @Test fun `a store at its cap is a bubble at it, its trade with it chosen to give`() {
        val s = full(hamlet(), Res.WISDOM)
        val t = Guidance.next(s, facts(), TipPlace.MAP)
        assertEquals("full-wisdom", t?.moment)
        assertEquals(TipAction.Trade(Res.WISDOM), t?.action)
        assertEquals("tips.full.trade", t?.key)
        // the getting one the village is shortest of, as the trade sheet chooses it
        assertEquals(Trade.suggest(s, Res.WISDOM)!!.emoji, t?.args?.get("getEmoji"))
        // at the market once it stands (it trades better), else where the resource comes from: wisdom at the linden or the fire
        assertEquals(TownPlace.Fire, t?.at)
        assertEquals(TownPlace.At(BuildingType.LIPA), Guidance.next(full(hamlet(BuildingType.LIPA), Res.WISDOM), facts(), TipPlace.MAP)?.at)
        assertEquals(TownPlace.Spot("rocks"), Guidance.next(full(hamlet(), Res.STONE), facts(), TipPlace.MAP)?.at)
        // under the cap: nothing
        assertNull(Guidance.next(hamlet(), facts(), TipPlace.MAP))
    }

    @Test fun `a full store whose trade is spent today offers what can be built with it, else nothing`() {
        val s = full(hamlet(), Res.WISDOM).copy(trades = TradeDay(today.toString(), setOf(Res.WISDOM)))
        // traded today: the store's tip is known from then on, so the learner who traded never gets it again
        assertNull(Guidance.next(s, facts(canBuild = setOf(BuildingType.BEEHIVE)), TipPlace.MAP))
        val notTraded = full(hamlet(), Res.FOOD)
        val everyFull = notTraded.copy(resources = Res.entries.associateWith { cap(notTraded, it) })
        // every store full: no trade has room, so the food is spent on what can be built (a hut costs food)
        val t = Guidance.next(everyFull, facts(canBuild = setOf(BuildingType.HUT)), TipPlace.MAP)
        assertEquals(TipAction.Build, t?.action)
        // nothing to build with it: no store's tip (what shows instead is another moment's, the maypole's step the full stores pay)
        assertNull(Guidance.next(everyFull, facts(), TipPlace.MAP)?.takeIf { it.moment.startsWith("full-") })
    }

    @Test fun `a learner who traded before gets no store tips`() {
        val s = full(hamlet(), Res.WISDOM)
        assertNull(Guidance.next(Guidance.did(s, "trade"), facts(), TipPlace.MAP))
        assertNull(Guidance.next(s.logged(t0, "⇄" to "Trade"), facts(), TipPlace.MAP))
        // and a trade settled marks it
        val (traded, deal) = Trade.settle(s, Res.WISDOM, Res.STONE, 100, 6, 6, 6, today, t0)
        assertTrue(deal.traded)
        assertTrue("trade" in traded.tips.did)
    }

    @Test fun `the first of each building says what it is for at it`() {
        val cases = mapOf(
            BuildingType.MARKET to "market", BuildingType.SMITHY to "smithy", BuildingType.LIPA to "lipa", BuildingType.WELL to "well",
            BuildingType.WATCHTOWER to "watchtower", BuildingType.CHURCH to "church", BuildingType.BEEHIVE to "beehive",
        )
        for ((type, id) in cases) {
            val s = hamlet(type).copy(age = Age.TRG)
            val t = Guidance.next(s, facts(), TipPlace.MAP)
            assertEquals("$type", id, t?.moment)
            assertEquals("$type", TownPlace.At(type), t?.at)
            assertEquals("$type", "tips.$id", t?.key)
        }
        // a house or a hut: who lives there, on its card
        val home = Guidance.next(hamlet(BuildingType.HUT), facts(), TipPlace.MAP)
        assertEquals("home", home?.moment)
        assertEquals(TipAction.Building("b2"), home?.action)
        // the palisade: known once the village has faced an event
        val pal = GameState(age = Age.TABOR, foundedOn = "2026-10-01", buildings = listOf(Building("p", BuildingType.PALISADE, NO_PLOT)))
        assertEquals("palisade", Guidance.next(pal, facts(), TipPlace.MAP)?.moment)
        assertNull(Guidance.next(pal.copy(stats = GameStats(eventsWon = 1)), facts(), TipPlace.MAP))
        // a building's scene, when its place opens one: into it, in that building
        // (the first scene to go into is a moment of its own, before it: known here, things found in the scenes)
        val church = Guidance.next(hamlet(BuildingType.CHURCH), facts().copy(found = 1, scenes = { p -> if (p == TownPlace.At(BuildingType.CHURCH)) "v-cerkvi" else null }), TipPlace.MAP)
        assertEquals("church", church?.moment)
        assertEquals(TipAction.Scene("v-cerkvi", "b2"), church?.action)
        // the village's first scene, before that: the fire's here, as the fire's scene opens from the start
        assertEquals(TipAction.Scene("ob-ognju"), Guidance.next(hamlet(), facts().copy(scenes = { p -> if (p == TownPlace.Fire) "ob-ognju" else null }), TipPlace.MAP)?.action)
        // gone in once: known
        assertNull(Guidance.next(Guidance.did(hamlet(BuildingType.CHURCH), "scene:v-cerkvi"), facts().copy(scenes = { p -> if (p == TownPlace.At(BuildingType.CHURCH)) "v-cerkvi" else null }), TipPlace.MAP)?.takeIf { it.moment == "church" })
    }

    @Test fun `the school is its lesson, the class before it too, and known once a unit is begun`() {
        val s = hamlet(BuildingType.SCHOOL)
        val t = Guidance.next(s, facts(lesson = "biti"), TipPlace.MAP)
        assertEquals("school", t?.moment)
        assertEquals(TipAction.Lesson("biti"), t?.action)
        // without a lesson to take (no course): nothing, the class's tip neither
        assertNull(Guidance.next(s, facts(), TipPlace.MAP))
        // the class meets before the school: under the linden, else by the fire
        val cls = Guidance.next(hamlet(), facts(lesson = "biti"), TipPlace.MAP)
        assertEquals("class", cls?.moment)
        assertEquals(TownPlace.Fire, cls?.at)
        assertEquals(TownPlace.At(BuildingType.LIPA), Guidance.next(hamlet(BuildingType.LIPA), facts(lesson = "biti"), TipPlace.MAP).takeIf { it?.moment == "class" }?.at)
        // the school opened once (or a unit begun): known
        assertNull(Guidance.next(Guidance.did(s, "school"), facts(lesson = "biti"), TipPlace.MAP))
        val begun = s.copy(course = CourseProgress(units = mapOf("sl/biti" to UnitProgress(intro = 2))))
        assertNull(Guidance.next(begun, facts(lesson = "biti"), TipPlace.MAP))
    }

    @Test fun `an upgrade within reach asks for its words first, then opens the card`() {
        val s = hamlet(BuildingType.HUT)
        fun upgrade(missing: Int): (String) -> UpgradeOption? = { id ->
            if (id != "b2") null
            else {
                val words = UpgradeWords(id, 2, List(8) { NeedWord("v-kuhinji", "w$it", "w$it", "", null, if (it < 8 - missing) WordState.KNOWN else WordState.NEW) }, need = 8)
                UpgradeOption(2, emptyMap(), missing == 0, null, "", words = words)
            }
        }
        val words = Guidance.next(s, facts().copy(upgrade = upgrade(3)), TipPlace.MAP)
        assertEquals("upgrade", words?.moment)
        assertEquals(TipAction.Words("b2"), words?.action)
        assertEquals(3, words?.args?.get("n"))
        val ready = Guidance.next(s, facts().copy(upgrade = upgrade(0)), TipPlace.MAP)
        assertEquals(TipAction.Building("b2", "tips.do.upgrade"), ready?.action)
        // once a building was upgraded: known
        assertNull(Guidance.next(s.copy(buildings = s.buildings.map { it.copy(level = 2) }), facts().copy(upgrade = upgrade(0)), TipPlace.MAP)?.takeIf { it.moment == "upgrade" })
    }

    @Test fun `the first damaged building is its repair, before most`() {
        val s = full(hamlet(BuildingType.HUT), Res.FOOD).let { it.copy(buildings = it.buildings.map { b -> if (b.type == BuildingType.HUT) b.copy(damaged = true) else b }) }
        // wisdom, stone, wood and food full come first (nothing comes in), then the damage
        val t = Guidance.next(s.copy(resources = s.resources + (Res.FOOD to 0)), facts(), TipPlace.MAP)
        assertEquals("repair", t?.moment)
        assertEquals(TipAction.Building("b2", "tips.do.repair"), t?.action)
        assertNull(Guidance.next(Guidance.did(s.copy(resources = s.resources + (Res.FOOD to 0)), "repair"), facts(), TipPlace.MAP)?.takeIf { it.moment == "repair" })
    }

    @Test fun `people a tip points at come from the map's bubbles`() {
        val s = hamlet()
        val quest = TownMarker("quest:q1", TownMarker.Kind.QUEST, "👵", "Micka", TownPlace.At(BuildingType.FIELD))
        val t = Guidance.next(s, facts(markers = listOf(quest)), TipPlace.MAP)
        assertEquals("request", t?.moment)
        assertEquals(TipAction.Quest("q1"), t?.action)
        assertEquals(TownPlace.At(BuildingType.FIELD), t?.at)
        // a request passed before: known
        assertNull(Guidance.next(s.copy(stats = s.stats.copy(questsDone = 1)), facts(markers = listOf(quest)), TipPlace.MAP))
        val arrival = TownMarker("arrival:mojca", TownMarker.Kind.HAPPENING, "🧳", "Mojca", TownPlace.At(BuildingType.FIELD))
        assertEquals(TipAction.Meet("mojca"), Guidance.next(s, facts(markers = listOf(arrival)), TipPlace.MAP)?.action)
        val visitor = TownMarker("visitor:luka", TownMarker.Kind.HAPPENING, "🐑", "Luka", TownPlace.Spot("road"))
        assertEquals(TipAction.Villager("luka"), Guidance.next(s, facts(markers = listOf(visitor)), TipPlace.MAP)?.action)
    }

    @Test fun `a new age opens the build sheet for three days, while it opens something to build`() {
        val s = GameState(age = Age.VAS, foundedOn = "2026-09-01", grown = mapOf("vas" to today.minusDays(1).toString()))
        val t = Guidance.next(s, facts(canBuild = setOf(BuildingType.MARKET)), TipPlace.MAP)
        assertEquals("age-vas", t?.moment)
        assertEquals(TipAction.Build, t?.action)
        assertNull(Guidance.next(s, facts(day = today.plusDays(3), now = t0 + 3 * 86_400_000L, canBuild = setOf(BuildingType.MARKET)), TipPlace.MAP))
        // nothing of the age to build: nothing
        assertNull(Guidance.next(s, facts(canBuild = setOf(BuildingType.HUT)), TipPlace.MAP))
    }

    @Test fun `the learning moments are Home's and wait for the dashboard`() {
        val s = GameState(age = Age.TABOR, foundedOn = "2026-10-29", stats = GameStats(totalEarned = mapOf(Res.FOOD to 30)))
        assertNull(Guidance.next(s, facts(ready = false, due = 5, reviewed = false), TipPlace.HOME))
        val t = Guidance.next(s, facts(due = 5, reviewed = false), TipPlace.HOME)
        assertEquals("reviews", t?.moment)
        assertEquals(TipAction.Review, t?.action)
        // never on the map
        assertNull(Guidance.next(s, facts(due = 5, reviewed = false), TipPlace.MAP))
        // reviewed before: the next one, the tutor's chat (not chatted yet), then a talk
        val f = facts(due = 5).copy(chatted = false)
        assertEquals("chat", Guidance.next(s, f, TipPlace.HOME)?.moment)
        assertEquals("talk", Guidance.next(s, facts(due = 5), TipPlace.HOME)?.moment)
        assertNull(Guidance.next(Guidance.did(s, "talk"), facts(due = 5), TipPlace.HOME))
    }

    @Test fun `a scene's tip is the first time inside one`() {
        val s = hamlet()
        val f = facts().copy(scene = "ob-ognju")
        assertEquals(TipAction.None, Guidance.next(s, f, TipPlace.SCENE)?.action)
        assertNull(Guidance.next(s, f.copy(found = 1), TipPlace.SCENE))
        assertNull(Guidance.next(s, facts(), TipPlace.SCENE))
    }

    // --- the queue -------------------------------------------------------------------------------------------------------

    /** Wisdom full (95), a damaged hut (90), the school's lesson (80), the watchtower (50): four moments at once. */
    private fun busy() = full(hamlet(BuildingType.SCHOOL, BuildingType.WATCHTOWER, BuildingType.HUT), Res.WISDOM)
        .let { it.copy(buildings = it.buildings.map { b -> if (b.type == BuildingType.HUT) b.copy(damaged = true) else b }) }

    @Test fun `one at a time, the highest priority first, and it stays until it is dealt with`() {
        var s = busy()
        val first = Guidance.next(s, facts(lesson = "biti"), TipPlace.MAP)!!
        assertEquals("full-wisdom", first.moment)
        s = Guidance.shown(s, first, today, t0)
        // shown and not dealt with: the same one, also in a moment that isn't quiet (a card open), its bubble doesn't jump
        assertEquals("full-wisdom", Guidance.next(s, facts(now = t0 + minute, lesson = "biti"), TipPlace.MAP, quiet = false)?.moment)
        s = Guidance.dealt(s, first.moment, today, t0 + 2 * minute)
        // the next waits a quiet moment: five minutes after the last, and none while something is open
        assertNull(Guidance.next(s, facts(now = t0 + 3 * minute, lesson = "biti"), TipPlace.MAP))
        assertNull(Guidance.next(s, facts(now = t0 + 10 * minute, lesson = "biti"), TipPlace.MAP, quiet = false))
        assertEquals("repair", Guidance.next(s, facts(now = t0 + 8 * minute, lesson = "biti"), TipPlace.MAP)?.moment)
    }

    @Test fun `at most three a day are first shown`() {
        var s = busy()
        var now = t0
        val shown = mutableListOf<String>()
        repeat(6) {
            val t = Guidance.next(s, facts(now = now, lesson = "biti"), TipPlace.MAP) ?: return@repeat
            s = Guidance.dealt(Guidance.shown(s, t, today, now), t.moment, today, now + minute)
            shown += t.moment
            now += 10 * minute
        }
        assertEquals(listOf("full-wisdom", "repair", "school"), shown)
        // the next day: the next one
        val tomorrow = today.plusDays(1)
        assertEquals("watchtower", Guidance.next(s, facts(day = tomorrow, now = now + 86_400_000L), TipPlace.MAP)?.moment)
    }

    @Test fun `never while wolves, a bear or a storm are about, nor when the tips are off`() {
        val s = busy()
        val wolves = s.copy(event = GameEvent("e", EventKind.WOLVES, 1, t0, t0 + 36 * 3_600_000L))
        assertNull(Guidance.next(wolves, facts(), TipPlace.MAP))
        assertNull(Guidance.next(wolves, facts(), TipPlace.HOME))
        // the merchant holds nothing back
        assertNotNull(Guidance.next(s.copy(event = GameEvent("e", EventKind.MERCHANT, 1, t0, t0 + 36 * 3_600_000L)), facts(), TipPlace.MAP))
        val off = Guidance.off(s, true)
        assertNull(Guidance.next(off, facts(), TipPlace.MAP))
        assertNotNull(Guidance.next(Guidance.off(off, false), facts(), TipPlace.MAP))
    }

    @Test fun `later brings it back the next day, got it never again, a store full again after a week`() {
        var s = full(hamlet(), Res.WISDOM)
        val t = Guidance.next(s, facts(), TipPlace.MAP)!!
        s = Guidance.later(Guidance.shown(s, t, today, t0), t.moment, today, t0 + minute)
        assertNull(Guidance.next(s, facts(now = t0 + 30 * minute), TipPlace.MAP))
        val tomorrow = today.plusDays(1)
        assertEquals("full-wisdom", Guidance.next(s, facts(day = tomorrow, now = t0 + 86_400_000L), TipPlace.MAP)?.moment)
        // dealt with: a store full comes again a week later (while the learner never traded), a first never again
        s = Guidance.dealt(s, t.moment, tomorrow, t0 + 86_400_000L)
        assertNull(Guidance.next(s, facts(day = tomorrow.plusDays(6), now = t0 + 7 * 86_400_000L), TipPlace.MAP))
        assertEquals("full-wisdom", Guidance.next(s, facts(day = tomorrow.plusDays(7), now = t0 + 8 * 86_400_000L), TipPlace.MAP)?.moment)
        var w = hamlet(BuildingType.WATCHTOWER)
        val tw = Guidance.next(w, facts(), TipPlace.MAP)!!
        w = Guidance.dealt(Guidance.shown(w, tw, today, t0), tw.moment, today, t0)
        assertNull(Guidance.next(w, facts(day = today.plusDays(60), now = t0 + 60 * 86_400_000L), TipPlace.MAP))
    }

    // --- the update rule ---------------------------------------------------------------------------------------------------

    /** Teo's village as it is (2026-10-30): Friuli, a hamlet, wisdom full, the school standing, the watchtower at level 2. */
    private fun teo(): GameState {
        Cultures.use(Cultures.load("friuli"))
        L10n.pair = LangPair(Lang.IT, Lang.DE)
        val s = GameState(
            age = Age.ZASELEK, foundedOn = "2026-10-12", lastTick = today.toString(),
            buildings = listOf(
                Building("t0", BuildingType.TENT, 0), Building("h1", BuildingType.HUT, 1), Building("h2", BuildingType.HUT, 2),
                Building("f", BuildingType.FIELD, 3), Building("s", BuildingType.SCHOOL, 4), Building("w", BuildingType.WATCHTOWER, 5, level = 2),
                Building("p", BuildingType.PALISADE, NO_PLOT),
            ),
            stats = GameStats(eventsWon = 2, totalEarned = mapOf(Res.WISDOM to 600)),
        )
        return full(s, Res.WISDOM)
    }

    @Test fun `a village from before gets the moments already past once, one a day, never a flood`() {
        var s = teo()
        s = Guidance.begin(s, facts(lesson = "ciao"))
        assertEquals(today.toString(), s.tips.since)
        assertTrue(s.tips.backlog.containsAll(setOf("full-wisdom", "school", "watchtower", "home")))
        // the palisade is known (the village faced wolves): not in it
        assertFalse("palisade" in s.tips.backlog)
        // day 1: the most useful one (wisdom full: nothing comes in), then none of the others that day
        val first = Guidance.next(s, facts(lesson = "ciao"), TipPlace.MAP)!!
        assertEquals("full-wisdom", first.moment)
        s = Guidance.dealt(Guidance.shown(s, first, today, t0), first.moment, today, t0 + minute)
        assertNull(Guidance.next(s, facts(now = t0 + 60 * minute, lesson = "ciao"), TipPlace.MAP))
        // a new moment that day is not held back by the backlog: a request
        val quest = TownMarker("quest:q1", TownMarker.Kind.QUEST, "👵", "Rosa", TownPlace.At(BuildingType.FIELD))
        assertEquals("request", Guidance.next(s, facts(now = t0 + 60 * minute, lesson = "ciao", markers = listOf(quest)), TipPlace.MAP)?.moment)
        // day 2: the school's lesson; day 3 the next
        val d2 = today.plusDays(1)
        val second = Guidance.next(s, facts(day = d2, now = t0 + 86_400_000L, lesson = "ciao"), TipPlace.MAP)!!
        assertEquals("school", second.moment)
        s = Guidance.dealt(Guidance.shown(s, second, d2, t0 + 86_400_000L), second.moment, d2, t0 + 86_400_000L)
        assertNull(Guidance.next(s, facts(day = d2, now = t0 + 86_400_000L + 60 * minute, lesson = "ciao"), TipPlace.MAP))
        assertNotNull(Guidance.next(s, facts(day = d2.plusDays(1), now = t0 + 2 * 86_400_000L, lesson = "ciao"), TipPlace.MAP))
    }

    @Test fun `a village founded today begins with nothing past, and the tips wait for the dashboard to begin`() {
        val fresh = GameState(foundedOn = today.toString())
        assertEquals(fresh, Guidance.begin(fresh, facts(ready = false)))
        val begun = Guidance.begin(fresh, facts())
        assertEquals(today.toString(), begun.tips.since)
        assertTrue(begun.tips.backlog.isEmpty())
        // once begun, never again
        assertEquals(begun, Guidance.begin(begun.copy(age = Age.VAS), facts()).copy(age = begun.age))
    }

    @Test fun `a moment past that isn't worth it any more counts as seen`() {
        val s = GameState(age = Age.TABOR, foundedOn = "2026-09-01", stats = GameStats(totalEarned = mapOf(Res.FOOD to 30)))
        val begun = Guidance.begin(s, facts(due = 3).copy(packDone = "Družina", chatted = true))
        assertEquals(today.toString(), begun.tips.seen["pack-done"])
        assertFalse("pack-done" in begun.tips.backlog)
    }

    // --- the tutor, the settings, what is kept --------------------------------------------------------------------------

    @Test fun `the tutor's pointer comes first, past what was seen and the pacing, until dealt with`() {
        var s = busy()
        s = Guidance.dealt(s, "watchtower", today, t0)
        s = Guidance.ask(s, "watchtower", "Show Teo the tower", t0)
        // at once (no quiet moment needed), ahead of wisdom full
        assertEquals("watchtower", Guidance.next(s, facts(now = t0 + minute), TipPlace.MAP, quiet = false)?.moment)
        // a replay of the same event changes nothing; dealt with, it is done, and its replay (the app connecting again) too
        assertEquals(s, Guidance.ask(s, "watchtower", "again", t0))
        s = Guidance.dealt(s, "watchtower", today, t0 + 2 * minute)
        assertNull(s.tips.asked)
        assertNull(Guidance.ask(s, "watchtower", "Show Teo the tower", t0).tips.asked)
        assertNull(Guidance.ask(Guidance.again(s), "watchtower", "Show Teo the tower", t0).tips.asked)
        // a newer pointer is taken in; the tutor takes it back
        val newer = Guidance.ask(s, "school", null, t0 + 3 * minute)
        assertEquals("school", newer.tips.asked?.moment)
        assertNull(Guidance.unask(newer, t0 + 4 * minute).tips.asked)
        // an unknown moment isn't asked
        assertNull(Guidance.ask(s, "nonsense", null, t0 + 5 * minute).tips.asked)
        // trading, asked: the fullest store's trade, also when no store is full
        val trade = Guidance.ask(hamlet(res = mapOf(Res.FOOD to 300)), "trade", null, t0)
        assertEquals(TipAction.Trade(Res.FOOD), Guidance.next(trade, facts(), TipPlace.MAP)?.action)
    }

    @Test fun `show them again forgets what was dealt with and turns them on`() {
        var s = Guidance.off(Guidance.dealt(Guidance.did(hamlet(BuildingType.WATCHTOWER), "trade"), "watchtower", today, t0), true)
        s = Guidance.again(s.copy(tips = s.tips.copy(since = "2026-10-01")))
        assertFalse(s.tips.off)
        assertTrue(s.tips.seen.isEmpty() && s.tips.did.isEmpty())
        assertEquals("2026-10-01", s.tips.since)
        assertEquals("watchtower", Guidance.next(s, facts(now = t0 + 10 * minute), TipPlace.MAP)?.moment)
    }

    @Test fun `the tips survive the village's JSON, and an older save reads with none`() {
        val s = Guidance.ask(Guidance.later(Guidance.did(hamlet(), "book"), "school", today, t0), "trade", "x", t0)
        val back = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), s))
        assertEquals(s.tips, back.tips)
        val old = json.decodeFromString(GameState.serializer(), """{"age":"ZASELEK","foundedOn":"2026-09-01"}""")
        assertEquals(Tips(), old.tips)
    }

    @Test fun `a village founded anew by the same learner keeps what they dealt with and off`() {
        val old = Guidance.off(Guidance.did(Guidance.dealt(hamlet(), "school", today, t0), "trade"), true)
        val m = Guidance.memory(old, "Jan")
        val fresh = GameState(foundedOn = today.toString())
        val inherited = Guidance.inherit(fresh, m, "Jan", today)
        assertTrue(inherited.tips.off)
        assertTrue("school" in inherited.tips.seen && "trade" in inherited.tips.did)
        // another learner on this phone: nothing of it
        assertEquals(fresh, Guidance.inherit(fresh, m, "Teo", today))
        assertEquals(fresh, Guidance.inherit(fresh, null, "Jan", today))
    }

    @Test fun `the tutor's recent list is the moments first shown lately`() {
        var s = hamlet()
        s = s.copy(tips = s.tips.copy(shown = mapOf("school" to "2026-10-29", "trade" to "2026-09-01", "home" to "2026-10-30")))
        assertEquals(listOf("home" to "2026-10-30", "school" to "2026-10-29"), Guidance.recent(s, today))
    }

    // --- the words -------------------------------------------------------------------------------------------------------

    @Test fun `a child's line is the base with the course language's word marked, and read in two voices`() {
        val s = teo()
        val t = Guidance.next(s, facts(lesson = "ciao"), TipPlace.MAP)!!
        assertEquals("full-wisdom", t.moment)
        // Teo: German, the Italian word marked, «Saggezza»
        val child = t.text(child = true)
        assertTrue(child, child.startsWith("Dein Lager ist voll: «Saggezza»!"))
        // the adult's: Italian · German
        val adult = t.text(child = false)
        assertTrue(adult, adult.contains("Il magazzino è pieno: Saggezza") && adult.contains(" · Dein Lager ist voll: Weisheit"))
        // the 🔊: the German by the phone's voice, the Italian word by the narrator's; no emoji said
        val said = t.speech(child = true)
        assertTrue(said.toString(), said.any { it.target && it.text == "Saggezza" } && said.any { !it.target && it.text.startsWith("Dein Lager ist voll") })
        assertTrue(said.toString(), said.none { p -> p.text.any { Character.isSurrogate(it) } })
        // the resource got is said by its name
        assertTrue(said.toString(), said.any { !it.target && it.text.contains(Trade.suggest(s, Res.WISDOM)!!.names.of(Lang.DE)) })
    }

    @Test fun `every moment's lines and buttons are in the four tables`() {
        val keys = listOf(
            "tips.full.trade", "tips.full.build", "tips.trade", "tips.school", "tips.class", "tips.market", "tips.smithy", "tips.lipa", "tips.well",
            "tips.watchtower", "tips.church", "tips.beehive", "tips.home", "tips.palisade", "tips.upgrade.ready", "tips.upgrade.words", "tips.repair",
            "tips.request", "tips.visitor", "tips.arrival", "tips.surprise", "tips.scene", "tips.age", "tips.project", "tips.festival", "tips.chest",
            "tips.reviews", "tips.packDone", "tips.cases", "tips.grammar", "tips.chat", "tips.talk", "tips.friends", "tips.inside",
        ).flatMap { listOf(it, "$it.child") }
        for (lang in Lang.entries) for (k in keys) assertNotNull("${lang.code} $k", L10n.table(lang)[k])
        val buttons = Guidance.MOMENTS.size // every moment has an id the tutor can point at
        assertEquals(buttons, Guidance.POINTABLE.toSet().size)
    }

    @Test fun `the tutor's moments are the catalogue's`() {
        // companion/bridge/src/tips.ts lists what show_tip takes: the app's catalogue, by id
        val ts = File("../../bridge/src/tips.ts").readText()
        val ids = Regex("""id:\s*'([a-z-]+)'""").findAll(ts).map { it.groupValues[1] }.toList()
        assertEquals(Guidance.POINTABLE.sorted(), ids.sorted())
    }
}
