package si.lanisce.lani.road

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Getting the road ready (RoadWork, RoadPrepWork): what plays first is got ready first, the sessions can start once the
 * first block is on the phone and the rest comes in after, a run stopped halfway goes on where it stopped, and what
 * can't be had is left out.
 */
class RoadWorkTest {
    @get:Rule val tmp = TemporaryFolder()

    private val today = "2026-09-29"

    private fun lib(): RoadLibrary {
        val cards = (1..12).map {
            RoadItem("card:$it", Kind.CARD, "c$it", "m$it", listOf(Sound.Prompt("m$it"), Sound.Pause(4_000, Gap.SAY), Sound.Clip("c$it", listOf("c$it.mp3")), Sound.Pause(15_000)), Rate("$it"), due = if (it <= 10) today else "2026-10-01")
        }
        val words = (1..60).map {
            RoadItem("word:p/$it", Kind.WORD, "w$it", "wm$it", listOf(Sound.Prompt("wm$it"), Sound.Pause(4_000, Gap.SAY), Sound.Clip("w$it", listOf("w$it.mp3")), Sound.Pause(15_000)), Rate("$it", "p"))
        }
        val dialogs = (1..8).map { RoadItem("dialog:s/$it", Kind.DIALOG, "d$it", "", listOf(Sound.Clip("d$it", listOf("d$it-a.mp3", "d$it-b.mp3")), Sound.Prompt("You say: d$it"), Sound.Pause(50_000))) }
        val stories = listOf("a/0", "a/1", "b/0").map { RoadItem("story:$it", Kind.STORY, it, "", listOf(Sound.Prompt("setup $it"), Sound.Clip("s$it", listOf("s-${it.replace('/', '-')}.mp3")), Sound.Pause(180_000))) }
        val phrases = (1..10).map { RoadItem("phrase:f$it", Kind.PHRASE, "f$it", "s", listOf(Sound.Clip("f$it", listOf("f$it.mp3")), Sound.Pause(3_000), Sound.Clip("f$it", listOf("f$it.mp3")))) }
        return RoadLibrary(1, "en", cards + words + dialogs + stories + phrases)
    }

    @Test fun `what plays first is got ready first, the first block, then every session a little at a time, then the cards due later`() {
        val l = lib()
        val order = RoadWork.order(l, emptyMap(), today)
        val ids = order.items.map { it.id }
        // every item once
        assertEquals(l.items.map { it.id }.toSet(), ids.toSet())
        assertEquals(ids.size, ids.toSet().size)
        // first the first block of "🚗 Za pot", as it plays (a word heard again is the word's files)
        val block = RoadMix.block(l, emptyMap(), today).map { it.id.substringBefore('#') }.distinct()
        assertEquals(block, ids.take(order.first))
        // then the other sessions alike: the phrases to shadow (in no block) come soon, not after all the words
        val firstPhrase = ids.indexOfFirst { it.startsWith("phrase:") }
        assertTrue("$firstPhrase", firstPhrase in order.first until order.first + 6)
        assertTrue(ids.indexOfLast { it.startsWith("word:") } > ids.indexOfLast { it.startsWith("phrase:") })
        // the cards due in the next days last: no session plays them today
        assertEquals(setOf("card:11", "card:12"), ids.takeLast(2).toSet())
        // what was heard lately comes later
        val heard = RoadWork.order(l, mapOf("word:p/1" to 5L), today).items.map { it.id }
        assertTrue(heard.indexOf("word:p/1") > ids.indexOf("word:p/1"))
    }

    @Test fun `QA's small library has a couple of items of each kind, in the library's order`() {
        val m = RoadWork.mini(lib())
        assertEquals(listOf("card:1", "card:2", "word:p/1", "word:p/2", "dialog:s/1", "dialog:s/2", "story:a/0", "story:a/1", "phrase:f1", "phrase:f2"), m.items.map { it.id })
        assertEquals(lib().base, m.base)
    }

    /** A run over [store] whose clips and prompts are files written at once; [failing] can't be had. */
    private class Fake(val store: RoadStore, val failing: Set<String> = emptySet(), val stopAfter: Int = Int.MAX_VALUE) {
        val fetched = mutableListOf<String>()
        val rendered = mutableListOf<String>()
        val published = mutableListOf<Pair<RoadLibrary, Int>>() // the library, and how many prompts were rendered then
        val reported = mutableListOf<RoadProgress.Files>()
        private var now = 0L

        fun work() = RoadPrepWork(
            store,
            fetch = { f ->
                yield() // a download takes a while
                fetched += f
                if (f in failing) false else { store.clip(f).writeText("clip"); true }
            },
            render = { t ->
                yield() // the phone's voice takes a while
                if (rendered.size >= stopAfter) throw IllegalStateException("the phone stopped")
                rendered += t
                store.prompt("en", t).writeText("prompt")
                true
            },
            progress = { reported += it },
            publish = { published += it to rendered.size },
            today = "2026-09-29",
            clock = { now.also { now += 20_000 } }, // time enough between two looks for the library to be written again
        )
    }

    @Test fun `the sessions can start once the first block is on the phone, and the rest comes in after`() = runBlocking {
        val l = lib()
        val store = RoadStore(tmp.newFolder("road"))
        val fake = Fake(store)
        val result = fake.work().run(RoadPlan(l))
        val order = RoadWork.order(l, emptyMap(), today)
        val block = order.items.take(order.first).map { it.id }.toSet()
        // written first once the first block was there, while prompts were still to render
        val (first, renderedThen) = fake.published.first()
        assertTrue(first.items.map { it.id }.containsAll(block))
        assertTrue("${first.items.size}", first.items.size < l.items.size)
        val prompts = l.items.flatMap(RoadWork::prompts).toSet()
        assertTrue("$renderedThen of ${prompts.size}", renderedThen < prompts.size)
        // the prompts in the order they play: the first block's first
        val blockPrompts = order.items.take(order.first).flatMap(RoadWork::prompts).distinct()
        assertEquals(blockPrompts, fake.rendered.take(blockPrompts.size))
        // and written again as it grew, the last time with everything, in the library's order
        assertTrue(fake.published.size >= 3)
        assertEquals(l, result.library)
        assertEquals(l, store.readLibrary())
        assertEquals(prompts.size, fake.rendered.size)
        assertEquals(l.items.flatMap(RoadWork::clips).toSet(), fake.fetched.toSet())
        assertEquals(fake.fetched.size, fake.fetched.toSet().size) // each clip once, though a phrase plays it twice
        // the progress: playable from the first write, and at the end everything done
        val last = fake.reported.last()
        assertTrue(last.playable)
        assertEquals(100, last.percent)
        // by the time it takes: the clips come quickly, a prompt counts as ten of them
        assertEquals(9, RoadProgress.Files(100, 100, 0, 100).percent)
        assertEquals(54, RoadProgress.Files(100, 100, 50, 100).percent)
        assertEquals(0, RoadProgress.Files(0, 0, 0, 0).percent)
        assertFalse(fake.reported.first().playable)
    }

    @Test fun `a run stopped halfway goes on where it stopped, the files there are kept`() = runBlocking {
        val l = lib()
        val store = RoadStore(tmp.newFolder("road"))
        // stopped a few prompts after the first block
        val order = RoadWork.order(l, emptyMap(), today)
        val at = order.items.take(order.first).flatMap(RoadWork::prompts).distinct().size + 5
        val stopped = Fake(store, stopAfter = at)
        val thrown = runCatching { stopped.work().run(RoadPlan(l)) }.exceptionOrNull()
        assertTrue("$thrown", thrown is IllegalStateException)
        assertEquals(at, stopped.rendered.size)
        // the first block was on the phone already: its sessions play
        assertTrue(store.readLibrary()!!.items.map { it.id }.containsAll(order.items.take(order.first).map { it.id }))
        val again = Fake(store)
        val result = again.work().run(RoadPlan(l))
        // only what was left: no prompt rendered twice, no clip fetched twice
        assertTrue(again.rendered.intersect(stopped.rendered.toSet()).isEmpty())
        assertEquals(l.items.flatMap(RoadWork::prompts).toSet(), (stopped.rendered + again.rendered).toSet())
        assertTrue(again.fetched.intersect(stopped.fetched.toSet()).isEmpty())
        assertEquals(l, result.library)
        // a run with everything there does nothing but write the library
        val done = Fake(store)
        assertEquals(l, done.work().run(RoadPlan(l)).library)
        assertTrue(done.rendered.isEmpty() && done.fetched.isEmpty())
    }

    @Test fun `an item whose clip can't be had is left out, and without a voice only what needs no prompt`() = runBlocking {
        val l = lib()
        val store = RoadStore(tmp.newFolder("road"))
        val result = Fake(store, failing = setOf("w3.mp3", "d2-b.mp3")).work().run(RoadPlan(l))
        val ids = result.library.items.map { it.id }
        assertFalse("word:p/3" in ids)
        assertFalse("dialog:s/2" in ids)
        assertEquals(l.items.size - 2, ids.size)
        assertEquals(2, result.clipsFailed)
        assertFalse(result.noVoice)

        val mute = RoadStore(tmp.newFolder("mute"))
        val noVoice = RoadPrepWork(mute, fetch = { f -> mute.clip(f).writeText("clip"); true }, render = { throw RoadPrepWork.NoVoice() }, today = today).run(RoadPlan(l))
        assertTrue(noVoice.noVoice)
        assertTrue(noVoice.library.items.all { RoadWork.prompts(it).isEmpty() })
    }

    @Test fun `the quiz's texts the voice store lacked are voiced while getting ready, a question whose can't be isn't played, the kit always is`() = runBlocking {
        val q = { id: String, wrong: String ->
            RoadQuiz.item(
                QuizQuestion(
                    id, QuizKind.DIALOG, id, listOf(Sound.Prompt("What do you answer?")),
                    listOf(QuizOption("Hvala.", Sound.Clip("Hvala.", listOf("hvala.mp3"))), QuizOption(wrong, Sound.Spoken(wrong, "female"))),
                    0, listOf(Sound.Clip("Hvala.", listOf("hvala.mp3"))), "Hvala.",
                ),
            )
        }
        val kit = RoadQuiz.kitItem(QuizKit(listOf(listOf(Sound.Spoken("Ena.", "female"), Sound.Prompt("One."))), listOf(Sound.Prompt("Right!")), listOf(Sound.Prompt("No:")), emptyList()))
        val l = lib().let { it.copy(items = it.items + q("a", "Hvala ti.") + q("b", "Hvalim.") + kit) }
        val store = RoadStore(tmp.newFolder("road"))
        val asked = mutableListOf<List<String>>()
        val result = RoadPrepWork(
            store,
            fetch = { f -> store.clip(f).writeText("clip"); true },
            render = { t -> store.prompt("en", t).writeText("prompt"); true },
            today = today,
            // the node voices one, not the other; nor "Ena." (the kit's prompt stands in)
            speak = { texts ->
                asked += texts.map { it.text }
                texts.map { s -> (s.text == "Hvala ti.").also { if (it) store.spoken(s.voice, s.text).writeText("mp3") } }
            },
        ).run(RoadPlan(l))
        val ids = result.library.items.map { it.id }
        assertTrue("quiz:a" in ids)
        assertFalse("quiz:b" in ids)
        assertTrue(RoadQuiz.KIT_ID in ids)
        assertEquals(2, result.spokenFailed)
        // each text asked once, the kit's soon after the first block
        assertEquals(setOf("Hvala ti.", "Hvalim.", "Ena."), asked.flatten().toSet())
        assertEquals(asked.flatten().size, asked.flatten().toSet().size)
        assertTrue(store.playable(result.library.items.first { it.id == "quiz:a" }, "en"))
        assertEquals(Sound.Prompt("One."), result.library.kit!!.resolve { store.has(it, "en") }!!.numbers.single())
        // a second run asks nothing that is there
        val again = mutableListOf<String>()
        RoadPrepWork(store, fetch = { true }, render = { true }, today = today, speak = { t -> again += t.map { it.text }; t.map { false } }).run(RoadPlan(l))
        assertFalse("Hvala ti." in again)
    }
}
