package si.lanisce.lani.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Pack
import si.lanisce.lani.data.PackSession
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.parsePack
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.Homes
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.inWorld
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.l10n.L10n
import java.io.File
import kotlin.random.Random

/**
 * "Znanje gradi · Knowledge builds" (companion/GAME.md, "Upgrades"): an upgrade asks for the building's words, the things
 * of its close-up scene as it is now, then its packs'; 8 known for level 2, 14, 20, 26; from level 3 none of them rusty. A
 * short run of exactly the missing words makes the upgrade possible at once; the neighbours' help doesn't replace them.
 */
class BuildingWordsTest {
    private val pair = L10n.pair
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "cultures").isDirectory && File(it, "scenes").isDirectory }

    @After fun back() {
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = pair
    }

    private fun jsons(dir: File): List<File> = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }

    /** The scenes a village of [culture] gets: its language's curated ones (Slovene's only), then its own. */
    private fun scenes(culture: String, language: String): List<SceneSpec> =
        (if (language == "sl") jsons(File(companion, "scenes")) else emptyList<File>()).plus(jsons(File(companion, "cultures/$culture/scenes")))
            .map { parseScene(it.readText()) }

    /** The packs a village of [culture] gets: its language's, then its own (the bridge's packsDirs). */
    private fun packs(culture: String, language: String): Map<String, Pack> =
        (jsons(File(companion, if (language == "sl") "packs" else "packs/$language")) + jsons(File(companion, "cultures/$culture/packs")))
            .map { parsePack(it.readText()) }.associateBy { it.id }

    private val scenes by lazy { scenes("primorska", "sl") }
    private val packs by lazy { packs("primorska", "sl") }

    private fun village(vararg buildings: Building, age: Age = Age.VAS) = GameState(
        age = age, buildings = buildings.toList(), resources = Res.entries.associateWith { 5000 }, lastTick = "2026-09-29",
    )

    /** A hut at [level]: its kitchen (kuhinja) is Micka's, the words of what is in it. */
    private fun hut(level: Int = 1) = Building("hut-1", BuildingType.HUT, 0, level = level)

    /** The words of [scene] seen in [b] of [s], in its order, as "pack/word". */
    private fun roomWords(s: GameState, b: Building, scene: String): List<String> {
        val sc = scenes.first { it.id == scene }
        val room = sc.inWorld(Homes.world(sc, s, b.id, scenes))
        return room.objects.map { "${it.pack ?: sc.pack}/${it.word}" }.distinct()
    }

    /** A book where the words [known] ("pack/word") count, [rusty] failed their last review and [tried] were met only. */
    private fun book(known: Collection<String> = emptyList(), rusty: Collection<String> = emptyList(), tried: Collection<String> = emptyList()): WordBook {
        fun card(key: String): ReviewCard {
            val (p, w) = key.split('/')
            val word = packs.getValue(p).words.first { it.id == w }
            return PackSession.card(p, word)
        }
        val cards = (known + rusty + tried).map(::card)
        return WordBook(cards, known.map { card(it).id }.toSet(), rusty.map { card(it).id }.toSet())
    }

    private fun words(s: GameState, id: String, b: WordBook, culture: String = "primorska", sc: List<SceneSpec> = scenes, p: Map<String, Pack> = packs) =
        BuildingWords.forUpgrade(s, id, sc, p, b, culture)

    private fun key(w: NeedWord) = "${w.pack}/${w.id}"

    @Test fun `level 2 asks for 8 of the words of what is in the building now`() {
        val s = village(hut(1))
        val w = words(s, "hut-1", book())!!
        assertEquals(2, w.toLevel)
        assertEquals(BuildingWords.NEED[2], w.need)
        assertEquals(8, w.need)
        // the kitchen at level 1: the open hearth and the straw bed, no stove or table yet
        val room = roomWords(s, hut(1), "kuhinja")
        assertEquals(room, w.words.map(::key))
        assertTrue("v-kovacnici/ognjisce" in room && "dom-in-hisa/postelja" in room)
        assertFalse("babicina-kuhinja/stedilnik" in room)
        assertEquals(0, w.have)
        assertFalse(w.done)
        // the run takes the first eight, in the kitchen's order
        assertEquals(room.take(8), w.missing.map(::key))
        assertEquals(room.take(8), BuildingWords.run(w).learn.map(::key))
        assertTrue(w.from!!.contains("kuhinj", ignoreCase = true))
    }

    @Test fun `any 8 of them known are enough, the words count as soon as they're answered right`() {
        val s = village(hut(1))
        val room = roomWords(s, hut(1), "kuhinja")
        val seven = words(s, "hut-1", book(known = room.takeLast(7)))!!
        assertEquals(7, seven.have)
        assertFalse(seven.done)
        assertEquals(1, seven.missing.size)
        assertEquals(room.first(), key(seven.missing.single())) // the first one not known
        val eight = words(s, "hut-1", book(known = room.takeLast(8)))!!
        assertTrue(eight.done)
        assertTrue(eight.missing.isEmpty())
    }

    @Test fun `the next levels ask for more, the richer room, then the packs`() {
        // level 3: what a level-2 kitchen has (the tiled stove, the table laid), 14 of them
        val two = village(hut(2))
        val w3 = words(two, "hut-1", book())!!
        assertEquals(3, w3.toLevel)
        assertEquals(14, w3.need)
        assertEquals(roomWords(two, hut(2), "kuhinja"), w3.words.map(::key))
        assertTrue("babicina-kuhinja/stedilnik" in w3.words.map(::key))
        assertTrue(w3.words.size > 14)
        // level 4: 20 of the level-3 kitchen's (the clock, the dresser, the holy corner)
        val w4 = words(village(hut(3)), "hut-1", book())!!
        assertEquals(20, w4.need)
        assertTrue("vreme-in-dan/ura" in w4.words.map(::key))
        // level 5: 26, more than the room has: its pack's words come after its things
        val four = village(hut(4))
        val w5 = words(four, "hut-1", book())!!
        assertEquals(26, w5.need)
        val room = roomWords(four, hut(4), "kuhinja")
        assertTrue(room.size < 26)
        assertEquals(room, w5.words.map(::key).take(room.size))
        assertTrue(w5.words.drop(room.size).all { it.pack == "v-kuhinji" })
        assertEquals(26, w5.words.size)
        // at the top level none
        assertNull(words(village(hut(5)), "hut-1", book()))
    }

    @Test fun `a rusty word doesn't count, and from level 3 on it holds the upgrade back until it's polished`() {
        val one = village(hut(1))
        val room1 = roomWords(one, hut(1), "kuhinja")
        // level 2: the rusty one doesn't count; eight others known are enough, and it's first to polish while short
        val short = words(one, "hut-1", book(known = room1.drop(1).take(7), rusty = room1.take(1)))!!
        assertEquals(7, short.have)
        assertFalse(short.done)
        assertFalse(short.rustBlocks)
        assertEquals(WordState.RUSTY, short.missing.first().state)
        assertTrue(words(one, "hut-1", book(known = room1.drop(1).take(8), rusty = room1.take(1)))!!.done)
        // level 3: fourteen known, and one rusty among the kitchen's words: polish it first
        val two = village(hut(2))
        val room2 = roomWords(two, hut(2), "kuhinja")
        val rusty = words(two, "hut-1", book(known = room2.take(14), rusty = room2.takeLast(1)))!!
        assertEquals(14, rusty.have)
        assertTrue(rusty.rustBlocks)
        assertFalse(rusty.done)
        assertEquals(listOf(room2.last()), rusty.missing.map(::key))
        assertEquals(listOf(room2.last()), BuildingWords.run(rusty).review.map(::key))
        assertTrue(BuildingWords.run(rusty).learn.isEmpty())
        // short of words and rusty: the rusty ones and as many more as are missing, those met before the new ones
        val both = words(two, "hut-1", book(known = room2.take(10), rusty = room2.takeLast(2), tried = listOf(room2[12])))!!
        assertEquals(room2.takeLast(2) + room2[12] + room2.subList(10, 12).take(1), both.missing.map(::key))
        assertEquals(listOf(WordState.RUSTY, WordState.RUSTY, WordState.TRIED, WordState.NEW), both.missing.map { it.state })
    }

    @Test fun `a word the learner has under another card counts, as the node sees it`() {
        val s = village(hut(1))
        // "voda" learned in lesson 1, not from the kitchen's pack
        val lesson = ReviewCard("vocab_voda", "voda (f)", "water", repetitions = 2, lastQuality = 4)
        val w = words(s, "hut-1", WordBook(listOf(lesson), setOf("vocab_voda"), emptySet()))!!
        assertEquals(WordState.KNOWN, w.words.first { it.id == "voda" }.state)
        assertEquals(1, w.have)
    }

    @Test fun `the practice run's right answers count at once, and the upgrade can be done`() {
        val s = village(hut(1))
        val room = roomWords(s, hut(1), "kuhinja")
        // two met but never right (answered wrong in a pack), the rest new
        val tried = room.take(2)
        var d = Dashboard(
            "Jan", "A2", "B2", 0, 0, 0, wordsTracked = 2, dueCards = emptyList(), weakPatterns = emptyList(), wordsLearned = 0,
            pool = tried.map { k -> PackSession.card(k.substringBefore('/'), packs.getValue(k.substringBefore('/')).words.first { it.id == k.substringAfter('/') }).copy(lastQuality = 2) },
        )
        val before = words(s, "hut-1", WordBook.of(d))!!
        assertEquals(listOf(WordState.TRIED, WordState.TRIED), before.missing.take(2).map { it.state })
        assertSame(s, GameEngine.upgrade(s, "hut-1", 1, words = before))
        assertFalse(GameEngine.upgradeOption(s, "hut-1", before)!!.available)

        val run = BuildingWords.run(before)
        assertEquals(2, run.review.size)
        assertEquals(6, run.learn.size)
        val tasks = BuildingWords.plan(run, d.pool, canSpeak = false, random = Random(7))
        // the new words twice (met, then recalled), the met ones once; never the same word twice in a row
        assertEquals(6 * 2 + 2, tasks.size)
        assertTrue(tasks.zipWithNext().none { (a, b) -> a.card.id == b.card.id })
        val results = BuildingWords.results(run, tasks, tasks.map { Verdict.CORRECT })
        assertEquals(2, results.reviewed.size)
        assertEquals(6, results.learned.values.sumOf { it.size })
        for ((pack, qs) in results.learned) d = d.learnedWords(pack, packs.getValue(pack).words, qs)
        d = d.reviewed(results.reviewed)
        assertEquals(8, d.wordsLearned)

        val after = words(s, "hut-1", WordBook.of(d))!!
        assertTrue(after.done)
        val up = GameEngine.upgrade(s, "hut-1", 1, words = after)
        assertEquals(2, up.buildings.single().level)
    }

    @Test fun `a run left early keeps only what was answered`() {
        val s = village(hut(1))
        val w = words(s, "hut-1", book())!!
        val run = BuildingWords.run(w)
        val tasks = BuildingWords.plan(run, emptyList(), canSpeak = false, random = Random(3))
        // two answers: the first two new words met (their first round), one right, one wrong; the rest aren't saved
        val results = BuildingWords.results(run, tasks, listOf(Verdict.CORRECT, Verdict.WRONG))
        assertEquals(2, results.learned.values.sumOf { it.size })
        assertTrue(results.reviewed.isEmpty())
        assertEquals(listOf(2, 4), results.learned.values.flatten().map { it.second }.sorted())
    }

    @Test fun `the neighbours' help doesn't replace the words`() {
        // the hut to level 2: 60 🌾 80 🪵 20 🪨; five neighbours bring half the 🪵 and 🪨, the rest is in the stores
        val s = village(hut(1)).copy(help = 100, villagers = 5, resources = mapOf(Res.FOOD to 500, Res.WOOD to 40, Res.STONE to 10))
        val w = words(s, "hut-1", book())!!
        val o = GameEngine.upgradeOption(s, "hut-1", w)!!
        assertFalse(o.available)
        assertTrue(o.moba!!.affordable)
        assertFalse(o.mobaAvailable)
        assertSame(s, GameEngine.upgrade(s, "hut-1", 1, withMoba = true, words = w))
        // known, the moba comes as before
        val room = roomWords(s, hut(1), "kuhinja")
        val known = words(s, "hut-1", book(known = room))!!
        assertTrue(GameEngine.upgradeOption(s, "hut-1", known)!!.mobaAvailable)
        assertEquals(2, GameEngine.upgrade(s, "hut-1", 1, withMoba = true, words = known).buildings.single().level)
    }

    @Test fun `levels reached stay, and words for another building or level ask for nothing`() {
        val s = village(hut(3))
        val w = words(s, "hut-1", book())!!
        assertEquals(4, w.toLevel)
        assertEquals(3, s.buildings.single().level) // no downgrade: only the next level waits
        // without words (an older caller), or with another level's, as before
        assertTrue(GameEngine.upgradeOption(s, "hut-1")!!.available)
        assertTrue(GameEngine.upgradeOption(s, "hut-1", w.copy(toLevel = 3))!!.available)
        assertTrue(GameEngine.upgradeOption(s, "hut-1", w.copy(building = "hut-2"))!!.available)
        assertFalse(GameEngine.upgradeOption(s, "hut-1", w)!!.available)
    }

    @Test fun `a building without a scene asks for its type's pack`() {
        // the palisade has no place to step into: the forest's words, of whose stakes it is
        val s = village(hut(1), Building("palisade-2", BuildingType.PALISADE, NO_PLOT))
        val w = words(s, "palisade-2", book())!!
        val forest = packs.getValue("v-gozdu").words.map { "v-gozdu/${it.id}" }
        assertEquals(forest.take(8), w.words.map(::key))
        assertEquals(8, w.need)
        assertTrue(w.from!!.contains("gozd", ignoreCase = true))
        // the well before Vas: its square isn't open yet, the square's pack
        val well = words(village(Building("well-1", BuildingType.WELL, 1), age = Age.TABOR), "well-1", book())!!
        assertTrue(well.words.all { it.pack == "na-vasi" })
        // a pack not on the phone yet and no scene: nothing to ask for (the upgrade doesn't wait for it)
        assertNull(words(s, "palisade-2", book(), p = packs - "v-gozdu"))
    }

    @Test fun `a house asks for its own room's words`() {
        val house = Building("house-1", BuildingType.HOUSE, 0, builtAt = 1)
        val s = village(house).copy(residents = listOf(si.lanisce.lani.game.villagers.Resident("janez", "2026-01-01")))
        val w = words(s, "house-1", book())!!
        val room = Homes.scenesOf(house, scenes, s).single()
        assertEquals("v-hisi", room.id) // Stari Janez's living room
        // its seven things at level 1, then its pack's words: eight
        val things = roomWords(s, house, "v-hisi")
        assertEquals(7, things.size)
        assertEquals(8, w.words.size)
        assertEquals(things, w.words.map(::key).take(7))
        assertEquals("v-hisi", w.words.last().pack)
    }

    @Test fun `every culture has a pack for every building, in its language, with enough words`() {
        assertTrue(Cultures.ids.containsAll(BuildingWords.PACKS.keys))
        for (id in Cultures.ids) {
            if (runCatching { Cultures.load(id) }.isFailure) continue // a stub: no village is played in it
            assertTrue("$id has its buildings' packs", id in BuildingWords.PACKS)
            val language = Cultures.use(id).manifest.language
            val packs = packs(id, language)
            for (t in BuildingType.entries) {
                val p = BuildingWords.packOf(t, id)
                assertNotNull("$id: $t", p)
                val pack = packs[p]
                assertNotNull("$id: $t's pack $p is one of the culture's", pack)
                assertEquals("$id: $p", language, pack!!.language)
                assertNull("$id: $p is no festival's", pack.festival)
                assertTrue("$id: $p has ${pack.words.size} words", pack.words.size >= BuildingWords.NEED.getValue(2))
            }
        }
    }

    @Test fun `in the four cultures a hut asks for its words, Primorska's kitchen, the others' home and kitchen pack`() {
        val expected = mapOf("primorska" to null, "friuli" to "casa-e-cucina", "kaernten" to "haus-und-kueche", "lakeland" to "home-and-kitchen")
        for ((id, pack) in expected) {
            val language = Cultures.use(id).manifest.language
            val sc = scenes(id, language)
            val p = packs(id, language)
            val s = village(hut(1))
            val w = BuildingWords.forUpgrade(s, "hut-1", sc, p, WordBook(emptyList(), emptySet(), emptySet()), id)!!
            assertEquals(id, 8, w.need)
            if (pack == null) assertEquals(id, listOf("lonec", "okno", "vrata"), w.words.take(3).map { it.id }) // the kitchen's pot, window, door
            else assertEquals(id, p.getValue(pack).words.take(8).map { it.id }, w.words.map { it.id })
            // and known, the upgrade can be done there too
            val cards = w.words.map { PackSession.card(it.pack, it.learn!!) }
            val known = BuildingWords.forUpgrade(s, "hut-1", sc, p, WordBook(cards, cards.map { it.id }.toSet(), emptySet()), id)!!
            assertTrue(id, known.done)
        }
    }

    @Test fun `a dwelling whose upgrade waits for its words is a way to make room, its words first`() {
        val tent = Building("tent-1", BuildingType.TENT, 0)
        // every plot taken, so no dwelling can go up: the tent's upgrade is the way
        val full = village(tent, age = Age.TABOR).copy(
            buildings = listOf(tent) + (1 until PLOTS_PER_AGE[Age.TABOR.ordinal]).map { Building("field-$it", BuildingType.FIELD, it) },
        )
        val w = words(full, "tent-1", book())!!
        val way = GameEngine.roomWay(full) { id -> if (id == "tent-1") w else null }!!
        assertEquals("tent-1", way.upgrade)
        assertSame(w, way.words)
        assertEquals("🎯 Vadi besede za šotor · Practise the words for the tent ›", si.lanisce.lani.ui.game.roomLabel(way))
        // known: the upgrade itself
        val known = words(full, "tent-1", book(known = w.words.map(::key)))!!
        assertNull(GameEngine.roomWay(full) { known }!!.words)
    }
}
