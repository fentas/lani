package si.lanisce.lani.road

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Build
import si.lanisce.lani.data.BuildStep
import si.lanisce.lani.data.Drill
import si.lanisce.lani.data.DrillKind
import si.lanisce.lani.data.DrillText
import si.lanisce.lani.data.Drills
import si.lanisce.lani.data.RapidSet
import si.lanisce.lani.data.Riddle
import si.lanisce.lani.data.TransformItem
import si.lanisce.lani.data.TransformSet
import si.lanisce.lani.data.VoiceProfile
import si.lanisce.lani.game.villagers.Villager

class RoadDrillsTest {
    private fun t(sl: String, en: String) = DrillText(mapOf("sl" to sl, "en" to en, "de" to "de: $en"))

    /** Every text has a clip in the first voice asked for, its file named by the text and the voice. */
    private val asked = mutableListOf<Pair<String, List<String>>>()
    private val all = ClipLookup { text, voices -> asked += text to voices; listOf("/voice/file/${voices.first()}-${Drills.slug(text)}.mp3") }
    private val none = ClipLookup { _, _ -> null }
    private fun only(vararg texts: String) = ClipLookup { text, _ -> if (text in texts) listOf("/voice/file/${Drills.slug(text)}.mp3") else null }
    private fun clipTexts(item: RoadItem) = item.sounds.filterIsInstance<Sound.Clip>().map { it.text }
    private fun prompts(item: RoadItem) = item.sounds.filterIsInstance<Sound.Prompt>().map { it.text }

    private val past = TransformSet(
        "preteklik", "pretekli-cas", "A1", DrillText(mapOf("sl" to "V preteklik", "en" to "Into the past.", "de" to "In die Vergangenheit.")),
        listOf(
            TransformItem(t("Micka kuha kosilo.", "Micka is cooking lunch."), t("Micka je kuhala kosilo.", "Micka cooked lunch.")),
            TransformItem(t("Dežuje.", "It's raining."), t("Deževalo je.", "It rained.")),
        ),
    )
    private val count = TransformSet(
        "stej", "stevila-samostalniki", "A1", t("Štej", "Count on."),
        listOf(TransformItem(t("Dve kravi.", "Two cows."), t("Tri krave.", "Three cows."), DrillText(mapOf("en" to "Three.", "de" to "Drei.")))),
    )
    private val compare = TransformSet(
        "primernik", "primernik", "A2", t("Še bolj", "Make it more."),
        listOf(TransformItem(t("Pes je hiter.", "The dog is fast."), t("Pes je hitrejši.", "The dog is faster."))),
    )
    private val numbers = RapidSet("stevila", "A1", null, t("Števila do sto", "Numbers to a hundred"), emptyList(), (1..12).toList())
    private val clock = RapidSet("ura", "A1", "kdaj-cas", t("Koliko je ura?", "What time is it?"), listOf(t("Ura je pol štirih.", "It's half past three.")), emptyList())
    private val grem = Build(
        "v-trgovino", "A1", listOf("vezniki"),
        listOf(
            BuildStep(null, t("Grem.", "I'm going.")),
            BuildStep(DrillText(mapOf("en" to "to the shop", "de" to "in den Laden")), t("Grem v trgovino.", "I'm going to the shop.")),
            BuildStep(DrillText(mapOf("en" to "tomorrow", "de" to "morgen")), t("Jutri grem v trgovino.", "Tomorrow I'm going to the shop.")),
        ),
    )
    private val withMicka = Build("z-micko", "A2", listOf("orodnik"), listOf(BuildStep(null, t("Grem.", "I'm going.")), BuildStep(DrillText(mapOf("en" to "with Micka")), t("Grem z Micko.", "I'm going with Micka."))))
    private val miza = Riddle(
        "miza", "A1",
        listOf(t("Iz lesa sem.", "I'm made of wood."), t("Imam štiri noge.", "I have four legs.")),
        t("Kaj sem?", "What am I?"), t("Miza!", "A table!"), word = "v-kuhinji/miza",
    )
    private val drills = listOf(
        Drill("preobrat", DrillKind.TRANSFORM, "sl", "🔄", t("Preobrat", "Transformations"), transforms = listOf(compare, past, count)),
        Drill("hitri-odziv", DrillKind.RAPID, "sl", "⚡", t("Hitri odziv", "Rapid fire"), rapid = listOf(numbers, clock)),
        Drill("gradnja", DrillKind.BUILD, "sl", "🧱", t("Gradnja stavkov", "Sentence building"), builds = listOf(withMicka, grem)),
        Drill("uganke", DrillKind.RIDDLE, "sl", "🕵️", t("Uganke", "Riddles"), teller = "janez", riddles = listOf(miza)),
        Drill("veloce", DrillKind.RAPID, "it", "⚡", DrillText(mapOf("it" to "Veloce", "en" to "Rapid")), rapid = listOf(numbers)),
    )

    @Test fun `a transformation is the sentence, what to do with it, a pause to say the new one, the new one, a pause to repeat it, the new one again`() {
        val item = RoadDrills.transform(past, past.items[0], "sl", "en", all)!!
        val s = item.sounds
        assertEquals("Micka kuha kosilo.", (s[0] as Sound.Clip).text)
        assertEquals(Sound.Pause(400), s[1])
        assertEquals(Sound.Prompt("Into the past.", cue = true), s[2])
        // four words: the pause to say it follows the answer, as the cards'
        assertEquals(Sound.Pause(RoadPlay.thinkMs("Micka je kuhala kosilo."), Gap.SAY), s[3])
        assertEquals("Micka je kuhala kosilo.", (s[4] as Sound.Clip).text)
        assertEquals(Sound.Pause(RoadPlay.repeatMs("Micka je kuhala kosilo."), Gap.REPEAT), s[5])
        assertEquals(s[4], s[6])
        assertEquals(Sound.Pause(RoadPlay.GAP_MS), s.last())
        assertEquals("transform:preteklik/micka-je-kuhala-kosilo", item.id)
        assertEquals(Kind.TRANSFORM, item.kind)
        // the screen: the sentence heard and what to do, never the answer
        assertEquals("Micka kuha kosilo.", item.title)
        assertEquals("V preteklik · Into the past.", item.subtitle)
        assertNull(item.rate)
        // in German, and the item's own instruction before its set's
        assertEquals("In die Vergangenheit.", prompts(RoadDrills.transform(past, past.items[0], "sl", "de", all)!!).single())
        assertEquals(listOf("Three."), prompts(RoadDrills.transform(count, count.items[0], "sl", "en", all)!!))
        // only with both sentences' clips
        assertNull(RoadDrills.transform(past, past.items[0], "sl", "en", only("Micka kuha kosilo.")))
        assertNull(RoadDrills.transform(past, past.items[0], "sl", "en", none))
    }

    @Test fun `next during the pause to say it plays the new sentence at once`() {
        val item = RoadDrills.transform(past, past.items[1], "sl", "en", all)!!
        val say = item.sounds.indexOfFirst { it is Sound.Pause && it.gap == Gap.SAY }
        val pieces = RoadPlay.pieces(item.sounds)
        val next = RoadControls.next(item, pieces.indexOf(say)) as RoadControls.Next.Reveal
        assertEquals("Deževalo je.", (item.sounds[pieces[next.piece]] as Sound.Clip).text)
        assertEquals(RoadControls.Next.Skip, RoadControls.next(item, 0))
    }

    @Test fun `rapid fire asks in the base language, about two seconds to answer, the answer once`() {
        assertEquals(2_000, RoadDrills.rapidMs("enaindvajset"))
        assertEquals(2_500, RoadDrills.rapidMs("ob petih"))
        assertEquals(3_500, RoadDrills.rapidMs("Ura je pol štirih."))
        val n21 = numbers.copy(numbers = listOf(21)).items("sl").single()
        val item = RoadDrills.rapid(numbers, n21, "sl", "en", all)!!
        assertEquals(listOf(Sound.Prompt("twenty-one", cue = true), Sound.Pause(2_000, Gap.SAY)), item.sounds.take(2))
        assertEquals(listOf("enaindvajset"), clipTexts(item))
        assertEquals(Sound.Pause(RoadDrills.RAPID_GAP_MS), item.sounds.last())
        assertEquals("rapid:stevila/21", item.id)
        assertEquals("21", item.title)
        assertEquals("einundzwanzig", prompts(RoadDrills.rapid(numbers, n21, "sl", "de", all)!!).single())
        val half = RoadDrills.rapid(clock, clock.items("sl").single(), "sl", "en", all)!!
        assertEquals("It's half past three.", half.title)
        assertEquals("rapid:ura/ura-je-pol-stirih", half.id)
        assertNull(RoadDrills.rapid(clock, clock.items("sl").single(), "sl", "en", none))
    }

    @Test fun `a sentence is built step by step, each said, then heard, the whole again at the end`() {
        val item = RoadDrills.build(grem, "sl", "en", RoadDrills.Words.EN, "Gradnja stavkov · Sentence building", all)!!
        assertEquals(listOf("Say: I'm going.", "Now add: to the shop", "Now add: tomorrow"), prompts(item))
        assertTrue(item.sounds.filterIsInstance<Sound.Prompt>().all { it.cue })
        assertEquals(listOf("Grem.", "Grem v trgovino.", "Jutri grem v trgovino.", "Jutri grem v trgovino."), clipTexts(item))
        // each step: its cue, a pause to say it that follows it, the sentence, a pause to repeat it
        val s = item.sounds
        assertEquals(Sound.Pause(RoadPlay.thinkMs("Jutri grem v trgovino."), Gap.SAY), s[9])
        assertEquals(Sound.Pause(RoadPlay.repeatMs("Jutri grem v trgovino."), Gap.REPEAT), s[11])
        assertEquals(3, s.count { it is Sound.Pause && it.gap == Gap.SAY })
        assertEquals("build:v-trgovino", item.id)
        assertEquals("Tomorrow I'm going to the shop.", item.title)
        // German prompts from the app's words; a step without its clip leaves the build out
        val de = RoadDrills.Words({ "Sag: $it" }, { "Jetzt dazu: $it" })
        assertEquals("Jetzt dazu: morgen", prompts(RoadDrills.build(grem, "sl", "de", de, "", all)!!).last())
        assertNull(RoadDrills.build(grem, "sl", "en", RoadDrills.Words.EN, "", only("Grem.", "Grem v trgovino.")))
    }

    @Test fun `a riddle in the teller's voice, a pause to guess, the answer and its meaning, then the clues with theirs`() {
        asked.clear()
        val janez = listOf("@janez", "grandpa", "male", "female")
        val item = RoadDrills.riddle(miza, "sl", "en", janez, "Uganke · Riddles", all)!!
        assertEquals(listOf("Iz lesa sem.", "Imam štiri noge.", "Kaj sem?", "Miza!", "Iz lesa sem.", "Imam štiri noge."), clipTexts(item))
        assertTrue(asked.all { it.second == janez })
        assertTrue(item.sounds.filterIsInstance<Sound.Clip>().all { c -> c.files.all { it.startsWith("@janez-") } })
        val guess = item.sounds.indexOfFirst { it is Sound.Pause && it.gap == Gap.SAY }
        assertEquals(Sound.Pause(RoadDrills.GUESS_MS, Gap.SAY), item.sounds[guess])
        assertEquals("Kaj sem?", (item.sounds[guess - 1] as Sound.Clip).text)
        assertEquals("Miza!", (item.sounds[guess + 1] as Sound.Clip).text)
        assertEquals(listOf("A table!", "I'm made of wood.", "I have four legs."), prompts(item))
        assertEquals("riddle:miza", item.id)
        assertEquals("Kaj sem? · What am I?", item.title)
        assertFalse("Miza" in item.title + item.subtitle)
        assertNull(RoadDrills.riddle(miza, "sl", "en", janez, "", only("Iz lesa sem.", "Imam štiri noge.", "Kaj sem?")))
    }

    @Test fun `only the rules introduced to the learner, at most a level above theirs, A1 first, of the village's language`() {
        val items = RoadDrills.items(drills, "sl", "en", "A1", setOf("primernik", "orodnik")::contains, { listOf("@$it", "female") }, RoadDrills.Words.EN, all)
        val ids = items.map { it.id }
        // the comparative's set and the build with the instrumental aren't introduced: left out; the A1 set first though
        // the file has the A2 one first
        assertFalse(ids.any { it.startsWith("transform:primernik/") || it == "build:z-micko" })
        assertEquals(listOf("transform:preteklik/micka-je-kuhala-kosilo", "transform:preteklik/dezevalo-je", "transform:stej/tri-krave"), ids.filter { it.startsWith("transform:") })
        assertEquals(12 + 1, ids.count { it.startsWith("rapid:") })
        assertEquals(listOf("build:v-trgovino"), ids.filter { it.startsWith("build:") })
        assertEquals(listOf("riddle:miza"), ids.filter { it.startsWith("riddle:") })
        // the Italian drill isn't this village's
        assertEquals(items.size, items.distinctBy { it.id }.size)
        // introduced (the page unlocked, answers, a card): in, after the A1 ones
        val later = RoadDrills.items(drills, "sl", "en", "A1", { false }, { listOf("female") }, RoadDrills.Words.EN, all).map { it.id }
        assertTrue(later.indexOf("transform:primernik/pes-je-hitrejsi") > later.indexOf("transform:stej/tri-krave"))
        assertTrue("build:z-micko" in later)
        // two levels up waits for the learner
        assertFalse(RoadDrills.offered("B1", emptyList(), "A1") { false })
        assertTrue(RoadDrills.offered("A2", emptyList(), "A1") { false })
        assertTrue(RoadDrills.offered("B1", emptyList(), "A2") { false })
    }

    private fun lib(vararg ids: Pair<String, Kind>) = RoadLibrary(items = ids.map { (id, k) -> RoadItem(id, k, id, "", listOf(Sound.Pause(10_000))) })

    @Test fun `transformations in rounds of a few of each set, the set heard least recently first`() {
        val l = lib(*(1..8).map { "transform:a/$it" to Kind.TRANSFORM }.toTypedArray(), *(1..3).map { "transform:b/$it" to Kind.TRANSFORM }.toTypedArray())
        assertEquals(
            listOf("a/1", "a/2", "a/3", "a/4", "a/5", "a/6", "b/1", "b/2", "b/3", "a/7", "a/8"),
            RoadDrills.transforms(l, emptyMap()).map { it.id.removePrefix("transform:") },
        )
        // set a heard lately: b first; in a, the item heard longest ago first
        val heard = mapOf("transform:a/1" to 50L, "transform:a/2" to 40L, "transform:b/1" to 10L)
        assertEquals(listOf("b/2", "b/3", "b/1", "a/3", "a/4", "a/5", "a/6", "a/7", "a/8"), RoadDrills.transforms(l, heard).map { it.id.removePrefix("transform:") }.take(9))
    }

    @Test fun `rapid fire shuffles what was heard alike, the same all day, another the next`() {
        val l = lib(*(1..30).map { "rapid:stevila/$it" to Kind.RAPID }.toTypedArray(), "rapid:stotice/101" to Kind.RAPID)
        val today = RoadDrills.rapid(l, emptyMap(), RoadMix.seed("2026-09-29")).map { it.id }
        assertEquals(today, RoadDrills.rapid(l, emptyMap(), RoadMix.seed("2026-09-29")).map { it.id })
        assertNotEquals(today, RoadDrills.rapid(l, emptyMap(), RoadMix.seed("2026-09-30")).map { it.id })
        assertNotEquals((1..6).map { "rapid:stevila/$it" }, today.take(6))
        // the numbers to a hundred first: their set comes first, a round of them before the hundreds
        assertTrue(today.take(6).all { it.startsWith("rapid:stevila/") })
        assertEquals("rapid:stotice/101", today[6])
        // heard: the set not heard first, then the others of the set, those heard after them
        val heard = today.take(3).associateWith { 1L }
        val again = RoadDrills.rapid(l, heard, RoadMix.seed("2026-09-29")).map { it.id }
        assertEquals("rapid:stotice/101", again[0])
        assertEquals(today.drop(3).filter { it.startsWith("rapid:stevila/") }.take(3), again.drop(1).take(3))
        assertEquals(today.take(3), again.takeLast(3))
    }

    @Test fun `a burst is a few of one set, of the kind heard least recently, nothing queued already`() {
        val l = lib(
            *(1..8).map { "transform:a/$it" to Kind.TRANSFORM }.toTypedArray(), *(1..8).map { "transform:b/$it" to Kind.TRANSFORM }.toTypedArray(),
            *(1..9).map { "rapid:n/$it" to Kind.RAPID }.toTypedArray(),
            *(1..4).map { "build:$it" to Kind.BUILD }.toTypedArray(),
            *(1..4).map { "riddle:$it" to Kind.RIDDLE }.toTypedArray(),
        )
        val seed = RoadMix.seed("2026-09-29")
        assertEquals((1..4).map { "transform:a/$it" }, RoadDrills.burst(l, emptyMap(), Kind.TRANSFORM, seed).map { it.id })
        assertEquals((1..4).map { "transform:b/$it" }, RoadDrills.burst(l, mapOf("transform:a/1" to 5L), Kind.TRANSFORM, seed).map { it.id })
        assertEquals(listOf("transform:a/5", "transform:a/6", "transform:a/7", "transform:a/8"), RoadDrills.burst(l, emptyMap(), Kind.TRANSFORM, seed, exclude = (1..4).map { "transform:a/$it" }.toSet()).map { it.id })
        assertEquals(5, RoadDrills.burst(l, emptyMap(), Kind.RAPID, seed).size)
        assertEquals(listOf("build:1", "build:2", "build:3"), RoadDrills.burst(l, emptyMap(), Kind.BUILD, seed).map { it.id })
        assertEquals(3, RoadDrills.burst(l, emptyMap(), Kind.RIDDLE, seed).size)
        assertEquals(RoadDrills.KINDS, RoadDrills.kinds(l, emptyMap()))
        assertEquals(listOf(Kind.RAPID, Kind.BUILD, Kind.RIDDLE, Kind.TRANSFORM), RoadDrills.kinds(l, mapOf("transform:b/3" to 9L)))
        assertEquals(listOf(Kind.BUILD), RoadDrills.kinds(lib("build:1" to Kind.BUILD), emptyMap()))
    }

    @Test fun `Za pot has a burst of a drill now and then, their kinds in turn, each item once in a block`() {
        val cards = (1..100).map { RoadItem("card:c$it", Kind.CARD, "c$it", "m", listOf(Sound.Pause(20_000)), Rate("c$it"), "2026-09-01") }
        val drillItems = (1..12).map { RoadItem("transform:a/$it", Kind.TRANSFORM, "t", "", listOf(Sound.Pause(15_000))) } +
            (1..12).map { RoadItem("rapid:n/$it", Kind.RAPID, "r", "", listOf(Sound.Pause(5_000))) } +
            (1..6).map { RoadItem("build:$it", Kind.BUILD, "b", "", listOf(Sound.Pause(50_000))) } +
            (1..6).map { RoadItem("riddle:$it", Kind.RIDDLE, "u", "", listOf(Sound.Pause(30_000))) }
        val block = RoadMix.block(RoadLibrary(items = cards + drillItems), emptyMap(), "2026-09-29")
        val kinds = block.map { it.kind }
        // the first burst after about four minutes of cards: four transformations in a row
        val first = kinds.indexOf(Kind.TRANSFORM)
        assertEquals(12, first)
        assertEquals(List(4) { Kind.TRANSFORM }, kinds.subList(first, first + 4))
        // then the other kinds in turn, a burst about every seven minutes
        val bursts = kinds.withIndex().filter { (i, k) -> k != Kind.CARD && (i == 0 || kinds[i - 1] == Kind.CARD) }.map { it.value }
        assertEquals(listOf(Kind.TRANSFORM, Kind.RAPID, Kind.BUILD, Kind.RIDDLE), bursts.take(4))
        assertEquals(5, kinds.count { it == Kind.RAPID })
        assertEquals(block.size, block.distinctBy { it.id }.size)
        // the cards in the order they have without drills
        val plain = RoadMix.block(RoadLibrary(items = cards), emptyMap(), "2026-09-29").map { it.id }
        val withDrills = block.filter { it.kind == Kind.CARD }.map { it.id }
        assertEquals(plain.take(withDrills.size), withDrills)
        assertTrue(plain.all { it.startsWith("card:") })
    }

    @Test fun `the library has the drills' items with their clips, a riddle in Stari Janez's own voice, and keeps them on the phone`() {
        asked.clear()
        val janez = Villager("janez", "Stari Janez", art = "grandpa", voice = "male", speaker = "grandpa")
        val inputs = RoadInputs(
            villagers = listOf(janez), profiles = mapOf("janez" to VoiceProfile("@janez", "grandpa", status = "own")),
            drills = Drills.bundled("sl"), notYet = setOf("primernik", "orodnik"),
        )
        val lib = RoadGather.library(inputs, { "You say: $it" }, now = 1, clips = all)
        for (k in RoadDrills.KINDS) assertTrue(k.name, lib.of(k).isNotEmpty())
        assertTrue(lib.of(Kind.TRANSFORM).none { it.id.startsWith("transform:primernik/") || it.id.startsWith("transform:s-kom/") })
        assertTrue(lib.of(Kind.RAPID).any { it.id == "rapid:stevila/21" && Sound.Clip("enaindvajset", listOf("female-enaindvajset.mp3")) in it.sounds })
        assertTrue(asked.filter { it.first == "Kaj sem?" }.all { it.second.first() == "@janez" })
        // the library's JSON keeps them (road.json)
        val back = RoadPlay.json.decodeFromString(RoadLibrary.serializer(), RoadPlay.json.encodeToString(RoadLibrary.serializer(), lib))
        assertEquals(lib, back)
        // Mirno is only listening: no drill in it
        assertTrue(RoadMix.easy(lib, emptyMap()).none { it.kind in RoadDrills.KINDS })
    }
}
