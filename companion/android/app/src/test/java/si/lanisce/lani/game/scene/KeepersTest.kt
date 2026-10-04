package si.lanisce.lani.game.scene

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.culture.Culture
import si.lanisce.lani.game.culture.CultureError
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.game.TodayItem
import si.lanisce.lani.ui.game.TownOverview
import si.lanisce.lani.ui.home.HomeLogic
import si.lanisce.lani.ui.home.VillageCall
import si.lanisce.lani.ui.scene.DialogRun
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The spots a culture tells about (world.json `spots`: the charcoal pile) and who is there now and then without living in
 * the village (the charcoal burner): his name and the spot's are the culture's, he comes in his parts of the day on the days
 * the dice give him, his bubble goes once his talk is done today (he stays), his talk is played at the learner's level in
 * their pair, and the spot's story too. The test pack (cultures-test/tinyland) has one: Carbonaio Gino.
 */
class KeepersTest {
    private val pair = L10n.pair
    private val day = LocalDate.of(2026, 9, 1)

    private fun read(name: String, edit: (String) -> String = { it }): String? =
        javaClass.getResourceAsStream("/cultures-test/tinyland/$name")?.use { it.readBytes().decodeToString() }?.let { if (name == "world.json") edit(it) else it }

    private val tiny: Culture by lazy { Cultures.parse("tinyland") { read(it) } }

    private val village = GameState(seed = 7)

    @After fun back() {
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = pair
    }

    @Test fun `the pile's name and card line are the culture's, else the app's own`() {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        // Primorska without texts of its own for it: the app's names
        Cultures.use(Cultures.DEFAULT)
        if (Cultures.current.world.spots["kopa"] == null) {
            assertEquals("Oglarska kopa · The charcoal pile", TownSpots.info("kopa").label)
            assertEquals("pri kopi · by the charcoal pile", TownSpots.info("kopa").where)
            assertEquals("♨️", TownSpots.info("kopa").emoji)
        }
        Cultures.use(tiny)
        L10n.pair = LangPair(Lang.IT, Lang.EN)
        assertEquals("La carbonaia · The charcoal pile", TownSpots.info("kopa").label)
        assertEquals("alla carbonaia · by the charcoal pile", TownSpots.info("kopa").where)
        L10n.pair = LangPair(Lang.IT, Lang.SL)
        assertEquals("La carbonaia · Oglarska kopa", TownSpots.info("kopa").label)
        // the other spots keep the app's names
        assertEquals("Lo stagno · Ribnik", TownSpots.info("pond").label)
    }

    @Test fun `he comes in his parts of the day, and his bubble goes once his talk is done`() {
        val world = tiny.world
        val evening = Keepers.here(village, day.atTime(20, 30), world)
        assertEquals(listOf("kopa/gino"), evening.map { it.id })
        assertEquals("spot:kopa/gino", evening.single().key)
        assertEquals(listOf("kopa/gino"), Keepers.here(village, day.atTime(23, 30), world).map { it.id })
        assertTrue(Keepers.here(village, day.atTime(9, 0), world).isEmpty())
        assertTrue(Keepers.here(village, day.atTime(14, 0), world).isEmpty())
        Cultures.use(tiny)
        L10n.pair = LangPair(Lang.IT, Lang.EN)
        val m = Keepers.markers(village, evening, day).single()
        assertEquals("keeper:kopa/gino", m.id)
        assertEquals(TownPlace.Spot("kopa"), m.place)
        assertEquals("Gino guarda la carbonaia · Gino watches the pile", m.label)
        assertEquals(TownMarker.Kind.HAPPENING, m.kind)
        // the village's markers carry it, after the happenings
        assertTrue(TownMarkers.of(village, emptyList(), keepers = evening, today = day).any { it.id == "keeper:kopa/gino" })
        // and so do the scroll's Today (his talk, the way to him) and Home's (his talk a tap away)
        val scroll = TownOverview.of(village, emptyList(), day.atTime(20, 30), keepers = evening)
        assertEquals(listOf(TodayItem.Keeper(evening.single(), TownPlace.Spot("kopa"))), scroll.today.filterIsInstance<TodayItem.Keeper>())
        assertEquals(TownOverview.of(village, emptyList(), day.atTime(20, 30)).todayCount + 1, scroll.todayCount)
        assertEquals(listOf(VillageCall.Keeper(evening.single(), m.emoji, m.label)), HomeLogic.calls(village, emptyList(), evening, day))
        // his talk done today (paid as a happening's): no bubble, but he's still there tonight
        val (done, got) = GameEngine.completeHappening(village, evening.single().key, mapOf(Res.WISDOM to 15), 0, day, 0L, "🧔" to "Gino")
        assertEquals(mapOf(Res.WISDOM to 15), got)
        assertTrue(Keepers.done(done, evening.single(), day))
        assertTrue(Keepers.markers(done, evening, day).isEmpty())
        assertTrue(TownOverview.of(done, emptyList(), day.atTime(21, 0), keepers = evening).today.none { it is TodayItem.Keeper })
        assertTrue(HomeLogic.calls(done, emptyList(), evening, day).isEmpty())
        assertEquals(1, Keepers.here(done, day.atTime(21, 0), world).size)
        // the next day he may come again
        assertFalse(Keepers.done(done, evening.single(), day.plusDays(1)))
        // who a bubble or a tap is about
        assertEquals("gino", Keepers.of(evening, "keeper:kopa/gino")?.keeper?.id)
        assertEquals("gino", Keepers.of(evening, "gino")?.keeper?.id)
        assertNull(Keepers.of(evening, "micka"))
    }

    @Test fun `the dice decide his days`() {
        val some = tiny.world.spots.getValue("kopa")
        val rare = tiny.world.copy(spots = mapOf("kopa" to some.copy(keeper = some.keeper!!.copy(chance = 0.4f))))
        val days = (0 until 200).count { d -> Keepers.here(village, day.plusDays(d.toLong()).atTime(21, 0), rare).isNotEmpty() }
        assertTrue("$days of 200", days in 50..110)
        // the same day, the same answer all evening
        for (d in 0 until 30) {
            val on = day.plusDays(d.toLong())
            assertEquals(Keepers.here(village, on.atTime(17, 30), rare).size, Keepers.here(village, on.atTime(21, 45), rare).size)
        }
    }

    @Test fun `he is drawn as a person of the map, not of the village`() {
        Cultures.use(tiny)
        L10n.pair = LangPair(Lang.IT, Lang.EN)
        val k = Keepers.here(village, day.atTime(21, 0), tiny.world).single()
        val v = Keepers.villager(k)
        assertEquals("gino", v.id)
        assertEquals("burner", v.art)
        assertEquals(listOf("spot:kopa"), v.home)
        assertEquals("Carbonaio · Charcoal burner", v.role)
        assertTrue("the sprite is one of the game's", v.art in SceneArt.people)
        val p = Keepers.person(k)
        assertNull("no friendship", p.villager)
        assertEquals(mapOf("wisdom" to 15), Keepers.happening(k).reward)
    }

    @Test fun `his talk is played at the learner's level, in their pair, to the end`() {
        val k = Keepers.here(village, day.atTime(21, 0), tiny.world).single()
        val a1 = Keepers.talk(k, "A1", "it", LangPair(Lang.IT, Lang.EN))!!
        assertEquals("Buonasera!", a1.lines[0].sl)
        assertEquals("Good evening!", a1.lines[0].en)
        assertEquals("gino", a1.lines[0].who)
        // a learner past A2 hears the A2 talk (the highest there is up to theirs); in Slovene as their base
        val b2 = Keepers.talk(k, "B2", "it", LangPair(Lang.IT, Lang.SL))!!
        assertEquals("Buonasera! Guardo la carbonaia tutta la notte.", b2.lines[0].sl)
        assertEquals("Dober večer! Vso noč pazim na kopo.", b2.lines[0].en)
        val wrong = b2.lines[1].choices.first { !it.ok }
        assertEquals("Dove vpraša kje. Vprašati hočeš zakaj: perché.", wrong.why)
        assertEquals("Kje? Tukaj, pred tabo!", wrong.reply?.en)
        // played with the wrong choice first: his reaction, then the right one, to the end
        var run = DialogRun.start(a1, "gino", seed = 3)
        assertEquals(DialogRun.Step.CHOOSE, run.step)
        val bad = run.choices.indexOfFirst { !it.ok }
        run = run.choose(bad)
        assertEquals("Giorno? È notte!", run.said.last().sl)
        run = run.choose(run.choices.indexOfFirst { it.ok })
        while (run.step == DialogRun.Step.LISTEN) run = run.next()
        assertEquals(DialogRun.Step.END, run.step)
        assertEquals(1, run.mistakes)
    }

    private fun firstLine(k: ActiveKeeper, level: String = "A1") = Keepers.talk(k, level, "it", LangPair(Lang.IT, Lang.EN))!!.lines[0].sl

    @Test fun `after his meeting he tells his story, a talk a day it is done, and after the last the first again in its next telling`() {
        Cultures.use(tiny)
        L10n.pair = LangPair(Lang.IT, Lang.EN)
        var s = village
        var on = day
        fun tonight() = Keepers.here(s, on.atTime(21, 0), tiny.world).single()
        fun bubble(k: ActiveKeeper) = Keepers.markers(s, listOf(k), on).single().label
        // the first evening: his meeting, under his own title
        var k = tonight()
        assertEquals(0, k.at)
        assertNull(k.story)
        assertEquals("Buonasera!", firstLine(k))
        assertEquals("keeper:kopa/gino", Keepers.talk(k, "A1", "it")!!.id)
        assertEquals("Gino guarda la carbonaia · Gino watches the pile", bubble(k))
        // told to its end: tapped again the same evening he says it again, and nothing more is counted
        s = Keepers.told(s, k, on)
        assertEquals(KeeperTold(1, on.toString()), s.keepers["spot:kopa/gino"])
        k = tonight()
        assertEquals(0, k.at)
        assertEquals(s, Keepers.told(s, k, on))
        // the next evening: the first talk of his story, its title on his bubble, in the scroll's Today and the chronicle
        on = on.plusDays(1)
        k = tonight()
        assertEquals("fuoco", k.story?.id)
        assertEquals("Gino accende la carbonaia · Gino lights the pile", bubble(k))
        assertEquals("Gino accende la carbonaia · Gino lights the pile", Keepers.happening(k).title)
        assertEquals("Stasera accendo la carbonaia.", firstLine(k))
        assertEquals("keeper:kopa/gino/fuoco", Keepers.talk(k, "A1", "it")!!.id)
        // an evening it isn't done (he isn't there, or the learner doesn't come) changes nothing: the next, the same talk
        on = on.plusDays(1)
        assertEquals("fuoco", tonight().story?.id)
        s = Keepers.told(s, tonight(), on)
        // the second talk, which has no title of its own: his
        on = on.plusDays(1)
        k = tonight()
        assertEquals("carbone", k.story?.id)
        assertEquals("Gino guarda la carbonaia · Gino watches the pile", bubble(k))
        assertEquals("Ecco il carbone!", firstLine(k))
        s = Keepers.told(s, k, on)
        // all told: the first talk again, a new pile, in its next telling; never the meeting again
        on = on.plusDays(1)
        k = tonight()
        assertEquals("fuoco", k.story?.id)
        assertEquals(1, Keepers.round(k.keeper, k.at))
        assertEquals("Una carbonaia nuova! Stasera piove, ma la accendo.", firstLine(k))
        assertEquals("Una carbonaia nuova! Piove, ma stasera la accendo lo stesso.", firstLine(k, "A2"))
        s = Keepers.told(s, k, on)
        on = on.plusDays(1)
        k = tonight()
        // the talk with one telling tells it each round
        assertEquals("carbone" to "Ecco il carbone!", k.story?.id to firstLine(k))
        s = Keepers.told(s, k, on)
        // the round after: its first telling again
        on = on.plusDays(1)
        k = tonight()
        assertEquals(2, Keepers.round(k.keeper, k.at))
        assertEquals("Stasera accendo la carbonaia.", firstLine(k))
        assertEquals(5, k.at)
    }

    @Test fun `a learner whose level rises keeps their place in his story`() {
        val first = Keepers.here(village, day.atTime(21, 0), tiny.world).single()
        val s = Keepers.told(Keepers.told(village, first, day), first.copy(at = 1), day.plusDays(1))
        val k = Keepers.here(s, day.plusDays(2).atTime(21, 0), tiny.world).single()
        assertEquals("carbone", k.story?.id)
        assertEquals("Ecco il carbone!", firstLine(k, "A1"))
        assertEquals("Ho aperto la carbonaia. Ecco il carbone!", firstLine(k, "A2"))
        assertEquals("Ho aperto la carbonaia. Ecco il carbone!", firstLine(k, "B2"))
    }

    @Test fun `a keeper with no story has his one talk each time, and nothing is counted`() {
        val kopa = tiny.world.spots.getValue("kopa")
        val plain = tiny.world.copy(spots = mapOf("kopa" to kopa.copy(keeper = kopa.keeper!!.copy(talks = emptyList()))))
        val k = Keepers.here(village, day.atTime(21, 0), plain).single()
        assertEquals(village, Keepers.told(village, k, day))
        // even with a count from before (a story taken out again): his meeting
        val counted = village.copy(keepers = mapOf("spot:kopa/gino" to KeeperTold(5, "2026-08-01")))
        val k5 = Keepers.here(counted, day.atTime(21, 0), plain).single()
        assertNull(k5.story)
        assertEquals("Buonasera!", firstLine(k5))
    }

    @Test fun `how far he got is kept with the village, and an older village has none`() {
        val s = village.copy(keepers = mapOf("spot:kopa/gino" to KeeperTold(3, "2026-09-02")))
        val text = si.lanisce.lani.data.json.encodeToString(GameState.serializer(), s)
        assertEquals(s.keepers, si.lanisce.lani.data.json.decodeFromString(GameState.serializer(), text).keepers)
        val now = si.lanisce.lani.data.json.parseToJsonElement(si.lanisce.lani.data.json.encodeToString(GameState.serializer(), village)) as kotlinx.serialization.json.JsonObject
        val older = kotlinx.serialization.json.JsonObject(now - "keepers").toString()
        assertTrue(si.lanisce.lani.data.json.decodeFromString(GameState.serializer(), older).keepers.isEmpty())
    }

    @Test fun `the spot's story at the learner's level, with its meaning in their base`() {
        val kopa = tiny.world.spots.getValue("kopa")
        val a1 = Keepers.lore(kopa, "A1", Lang.IT, LangPair(Lang.IT, Lang.EN))!!
        assertEquals("Qui fanno il carbone." to "Here they make charcoal.", a1.said to a1.meant)
        val b1 = Keepers.lore(kopa, "B1", Lang.IT, LangPair(Lang.IT, Lang.DE))!!
        assertEquals("A2", b1.level)
        assertEquals("Früher machte man hier Holzkohle für den Schmied.", b1.meant)
        // read by someone whose base is the village's language: no meaning under it
        assertEquals("", Keepers.lore(kopa, "A1", Lang.IT, LangPair(Lang.IT, Lang.IT))!!.meant)
        assertNull(Keepers.lore(kopa.copy(story = emptyMap()), "A1", Lang.IT))
    }

    @Test fun `a spot the game hasn't got, or a keeper drawn with no sprite of the game's, is a broken pack`() {
        val nowhere = runCatching { Cultures.parse("tinyland") { n -> read(n) { it.replace("\"kopa\": {", "\"nowhere\": {") } } }.exceptionOrNull()
        assertTrue("$nowhere", nowhere is CultureError && nowhere.problems.any { "spots.nowhere" in it && "not a spot" in it })
        val sprite = runCatching { Cultures.parse("tinyland") { n -> read(n) { it.replace("\"art\": \"burner\"", "\"art\": \"dragon\"") } } }.exceptionOrNull()
        assertTrue("$sprite", sprite is CultureError && sprite.problems.any { "spots.kopa.keeper.art" in it })
        val time = runCatching { Cultures.parse("tinyland") { n -> read(n) { it.replace("[\"evening\", \"night\"]", "[\"midnight\"]") } } }.exceptionOrNull()
        assertTrue("$time", time is CultureError && time.problems.any { "keeper.when" in it })
        assertNotNull(tiny.world.spots["kopa"]?.keeper)
        // a talk of his story twice, or at a level there isn't
        val twice = runCatching { Cultures.parse("tinyland") { n -> read(n) { it.replace("\"id\": \"carbone\"", "\"id\": \"fuoco\"") } } }.exceptionOrNull()
        assertTrue("$twice", twice is CultureError && twice.problems.any { "keeper.talks" in it && "twice" in it })
        val level = runCatching { Cultures.parse("tinyland") { n -> read(n) { it.replace("\"A2\": {\n                    \"lines\"", "\"Z9\": {\n                    \"lines\"") } } }.exceptionOrNull()
        assertTrue("$level", level is CultureError && level.problems.any { "keeper.talks[0].again[0].levels.Z9" in it && "not a level" in it })
    }
}
