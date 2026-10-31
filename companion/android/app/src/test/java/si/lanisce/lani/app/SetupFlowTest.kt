package si.lanisce.lani.app

import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.app.SetupFlow.Step
import si.lanisce.lani.app.SetupFlow.Way
import si.lanisce.lani.data.PairFailure
import si.lanisce.lani.data.PairOutcome
import si.lanisce.lani.data.PairTrouble
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.bi
import java.io.IOException
import java.net.ConnectException

/** The first launch's steps (app/SetupFlow, docs/plans/05-built/onboarding.md): the choices, the errors, manual entry, the child gate. */
class SetupFlowTest {
    private val before = L10n.pair

    @After fun pairBack() {
        L10n.pair = before
    }

    // --- the welcome and the languages -------------------------------------------------------------------------------

    @Test fun `the welcome is in the phone's language, English when it isn't one of ours`() {
        assertEquals(Lang.DE, SetupFlow.start("de").base)
        assertEquals(Lang.SL, SetupFlow.start("sl").base)
        assertEquals(Lang.EN, SetupFlow.start("fr").base)
        assertEquals(Lang.EN, SetupFlow.start(null).base)
        assertEquals(Step.WELCOME, SetupFlow.start("de").step)
        assertNull(SetupFlow.start("de").target)
    }

    @Test fun `the greeting has no gender, in every language`() {
        for (lang in Lang.entries) {
            val woman = L10n.text(lang, "setup.hello", mapOf(L10n.LEARNER_GENDER to "f"))
            val man = L10n.text(lang, "setup.hello", mapOf(L10n.LEARNER_GENDER to "m"))
            assertEquals("${lang.code}: the same to anyone", woman, man)
        }
        assertEquals("Dobrodošli!", L10n.text(Lang.SL, "setup.hello"))
        assertFalse("not the masculine Dobrodošel", L10n.text(Lang.SL, "setup.hello").contains("Dobrodošel"))
    }

    @Test fun `choosing the language to learn keeps the phone's for the explanations`() {
        // Teo's tablet: German, learning Italian
        val f = SetupFlow.start("de").chooseTarget(Lang.IT)
        assertEquals(Step.LANGUAGES, f.step)
        assertEquals(LangPair(Lang.IT, Lang.DE), f.pair)
        // a German phone learning German: explained in English; an English phone learning English: in Slovene
        assertEquals(LangPair(Lang.DE, Lang.EN), SetupFlow.start("de").chooseTarget(Lang.DE).pair)
        assertEquals(LangPair(Lang.EN, Lang.SL), SetupFlow.start("en").chooseTarget(Lang.EN).pair)
    }

    @Test fun `the explanations are never in the language learned`() {
        val f = SetupFlow.start("de").chooseTarget(Lang.IT)
        assertEquals(f, f.chooseBase(Lang.IT))
        assertEquals(Lang.SL, f.chooseBase(Lang.SL).base)
    }

    @Test fun `the welcome reads in the chosen pair, target then base`() {
        val f = SetupFlow.start("de").chooseTarget(Lang.IT)
        L10n.pair = f.pair
        assertEquals("Come vuoi usare Lani? · Wie möchtest du Lani nutzen?", bi("setup.howUse"))
        assertEquals("Benvenuti! · Willkommen!", "${L10n.text(f.pair.target, "setup.hello")} · ${L10n.text(f.pair.base, "setup.hello")}")
        // Jan's pair as ever
        L10n.pair = LangPair.DEFAULT
        assertEquals("Kako želiš uporabljati Lani? · How do you want to use Lani?", bi("setup.howUse"))
    }

    @Test fun `on from the welcome only with a language chosen`() {
        val f = SetupFlow.start("de")
        assertEquals(f, f.next())
        assertEquals(Step.WAYS, f.chooseTarget(Lang.IT).next().step)
    }

    // --- the two ways ------------------------------------------------------------------------------------------------

    @Test fun `the two ways and back`() {
        val ways = SetupFlow.start("de").chooseTarget(Lang.IT).next()
        val device = ways.choose(Way.DEVICE)
        assertEquals(Step.DEVICE, device.step)
        assertEquals(Way.DEVICE, device.way)
        val computer = ways.choose(Way.COMPUTER)
        assertEquals(Step.COMPUTER, computer.step)
        val manual = computer.manual()
        assertEquals(Step.MANUAL, manual.step)
        // back, step by step, to the welcome; the choices stay
        assertEquals(Step.COMPUTER, manual.back().step)
        assertEquals(Step.WAYS, manual.back().back().step)
        assertEquals(Step.WAYS, device.back().step)
        assertEquals(Step.LANGUAGES, ways.back().step)
        assertEquals(Step.WELCOME, ways.back().back().step)
        assertEquals(Step.WELCOME, ways.back().back().back().step)
        assertEquals(Lang.IT, ways.back().back().target)
    }

    // --- typed by hand -----------------------------------------------------------------------------------------------

    @Test fun `manual entry needs an address and a token, or a pasted code`() {
        val m = SetupFlow.start("sl").chooseTarget(Lang.SL).next().choose(Way.COMPUTER).manual()
        assertFalse("nothing typed", m.canConnect)
        assertFalse("no token", m.edit(url = "https://node.ts.net:8443").canConnect)
        assertTrue(m.edit(url = "https://node.ts.net:8443", token = " abc ").canConnect)
        assertEquals("abc", m.edit(token = " abc ").token)
        // the text lani-pair prints under the QR code pairs as the code does: no token asked
        val pasted = m.edit(url = "lani://pair?v=1&u=https%3A%2F%2Fnode.ts.net&c=K7Q2M9XPA4TD&p=luka&f=20398153fcf896040daeefddca60749b")
        assertTrue(pasted.pasted)
        assertTrue(pasted.canConnect)
        assertTrue(pasted.connecting().busy)
        assertTrue(m.edit(url = "FLUENT://pair?v=1").pasted)
    }

    @Test fun `an address that isn't one says so before anything is asked`() {
        val m = SetupFlow.start("sl").chooseTarget(Lang.SL).next().choose(Way.COMPUTER).manual()
        val bad = m.edit(url = "https://node .ts.net", token = "t").connecting()
        assertFalse(bad.busy)
        assertEquals(PairFailure.BAD_ADDRESS, bad.trouble?.failure)
        assertEquals(PairFailure.Out.FIX, bad.trouble?.out)
        val good = m.edit(url = "https://node.ts.net:8443/", token = "t").connecting()
        assertTrue(good.busy)
        assertNull(good.trouble)
        // typing again clears what went wrong
        assertNull(bad.edit(url = "https://node.ts.net").trouble)
    }

    @Test fun `a typed connection's failures in plain words`() {
        fun t(e: Throwable) = PairTrouble.typed(e)
        assertEquals(PairFailure.WRONG_TOKEN, t(IOException("HTTP 401: unauthorized")).failure)
        assertEquals(PairFailure.WRONG_TOKEN, t(IOException("HTTP 403")).failure)
        assertEquals(PairFailure.NOT_LANI, t(IOException("HTTP 404: <html>")).failure)
        assertEquals(PairFailure.HTTP, t(IOException("HTTP 502: bad gateway")).failure)
        assertTrue("502" in t(IOException("HTTP 502: bad gateway")).message)
        assertEquals(PairFailure.UNREACHABLE, t(ConnectException("Failed to connect to node.ts.net/100.64.0.1:8443")).failure)
        assertEquals(PairFailure.UNREACHABLE, t(java.net.UnknownHostException("node.ts.net")).failure)
        assertEquals(PairFailure.BAD_ADDRESS, t(IllegalArgumentException("unexpected url")).failure)
        assertEquals(PairFailure.NOT_LANI, t(SerializationException("not JSON")).failure)
        // out of reach: try again, and the line names the computer and Tailscale, no "bridge" or "token"
        val far = t(ConnectException("x"))
        assertEquals(PairFailure.Out.RETRY, far.out)
        L10n.pair = LangPair(Lang.IT, Lang.DE)
        val de = t(ConnectException("x")).message
        assertTrue(de, "Computer" in de && "Tailscale" in de && "bridge" !in de.lowercase())
    }

    @Test fun `a failure stops the spinner and keeps the step, with its way out`() {
        val m = SetupFlow.start("sl").chooseTarget(Lang.SL).next().choose(Way.COMPUTER).manual().edit(url = "https://node.ts.net", token = "t").connecting()
        val failed = m.failed(PairTrouble.of(PairFailure.UNREACHABLE))
        assertFalse(failed.busy)
        assertEquals(Step.MANUAL, failed.step)
        // try again: the same step, nothing said any more
        assertNull(failed.out().trouble)
        assertEquals(Step.MANUAL, failed.out().step)
        // a computer too old to pair by code: to the address typed
        val old = SetupFlow.start("sl").chooseTarget(Lang.SL).next().choose(Way.COMPUTER).failed(PairTrouble.of(PairOutcome.Failed(PairFailure.OLD_COMPUTER)))
        assertEquals(Step.MANUAL, old.out().step)
        // an old code or one for someone else: a new code
        assertEquals(PairFailure.Out.SCAN, PairTrouble.of(PairFailure.EXPIRED).out)
        assertEquals(PairFailure.Out.SCAN, PairTrouble.of(PairFailure.OTHER_LEARNER, who = "Jan").out)
        assertTrue("Jan" in PairTrouble.of(PairFailure.OTHER_LEARNER, who = "Jan").message)
        assertEquals(PairFailure.Out.SCAN, PairTrouble.of(si.lanisce.lani.data.PairProblem.NOT_PAIRING).out)
    }

    @Test fun `every failure reads in both languages, without the old jargon in the main words`() {
        L10n.pair = LangPair.DEFAULT
        for (f in PairFailure.entries) {
            val m = f.message(who = "Teo", status = 500)
            assertTrue(m, " · " in m)
            assertFalse(m, "bridge" in m.lowercase() || "most " in m.lowercase())
        }
    }

    // --- a child's device ----------------------------------------------------------------------------------------------

    @Test fun `a child's tutor step is a parent's, open after a hold`() {
        val g = ParentGate(child = true)
        assertTrue(g.locked)
        assertTrue("a tap doesn't open it", g.held(150).locked)
        assertTrue("nor a short press", g.held(ParentGate.HOLD_MS - 1).locked)
        val open = g.held(ParentGate.HOLD_MS)
        assertFalse(open.locked)
        assertTrue("closed again with the sheet", open.close().locked)
        // a grown-up's is open
        assertFalse(ParentGate(child = false).locked)
        assertFalse(ParentGate(child = false).close().locked)
    }

    @Test fun `a refused key says so in plain words`() {
        L10n.pair = LangPair.DEFAULT
        assertNull(TutorKey.problem(null))
        val refused = TutorKey.problem(si.lanisce.lani.tutor.live.KeyProblem.Refused)!!
        assertTrue(refused, "sk-ant-" in refused && " · " in refused)
        assertEquals(refused, TutorKey.problem(si.lanisce.lani.tutor.live.KeyProblem.Empty))
        assertEquals("⚠️ timeout", TutorKey.problem(si.lanisce.lani.tutor.live.KeyProblem.Failed("timeout")))
        // what a failure says is never read for its words: a network message that names a 401 is still the network's
        assertEquals("⚠️ proxy said 401", TutorKey.problem(si.lanisce.lani.tutor.live.KeyProblem.Failed("proxy said 401")))
    }

    @Test fun `the hosted onboarding says a child learns there`() {
        val a = si.lanisce.lani.hosted.HostedAnswers("Teo", null, "it", "de", goal = 15, child = true)
        assertTrue("\"child\":true" in a.json())
        assertFalse("child" in si.lanisce.lani.hosted.HostedAnswers("Ana", "female", "sl", "en").json())
    }

    // --- the aim (docs/plans/05-built/setup-refresh.md) ------------------------------------------------------------------

    @Test fun `the aim is B2 by default, above the level from B2 on`() {
        // Jan: "b2 should be default aim"
        for (level in listOf("A1", "A2", "B1")) assertEquals(level, "B2", Aim.default(level))
        assertEquals("C1", Aim.default("B2"))
        assertEquals("C2", Aim.default("C1"))
        assertEquals("C2", Aim.default("C2"))
        assertEquals("a level that isn't one counts as A1", "B2", Aim.default("x"))
        // only the levels above the learner's; C2 at C2
        assertEquals(listOf("A2", "B1", "B2", "C1", "C2"), Aim.choices("A1"))
        assertEquals(listOf("C1", "C2"), Aim.choices("b2"))
        assertEquals(listOf("C2"), Aim.choices("C2"))
        for (level in Aim.LEVELS) assertTrue(level, Aim.default(level) in Aim.choices(level))
    }

    @Test fun `an aim chosen stays while it is above the level, else the default for the new level`() {
        assertEquals("C1", Aim.keep("C1", "A1"))
        assertEquals("B1", Aim.keep("b1", "A2"))
        // the level raised to the aim or past it: the default again
        assertEquals("C1", Aim.keep("B2", "B2"))
        assertEquals("B2", Aim.keep("A2", "B1"))
        assertEquals("nothing chosen: the default", "B2", Aim.keep(null, "A1"))
    }

    @Test fun `the hosted answers carry the aim, B2 unless chosen`() {
        assertTrue("\"target_level\":\"B2\"" in si.lanisce.lani.hosted.HostedAnswers("Ana", "female", "sl", "en").json())
        val teo = si.lanisce.lani.hosted.HostedAnswers("Teo", "male", "it", "de", goal = 15, child = true, targetLevel = "B1")
        assertTrue("\"target_level\":\"B1\"" in teo.json())
        // a learner at B2 aims at C1; an aim not above the level is sent as the default
        assertTrue("\"target_level\":\"C1\"" in si.lanisce.lani.hosted.HostedAnswers("Ana", null, "sl", "en", level = "B2").json())
        assertTrue("\"target_level\":\"C1\"" in si.lanisce.lani.hosted.HostedAnswers("Ana", null, "sl", "en", level = "B2", targetLevel = "A2").json())
    }

    // --- paired: "Še o tebi · A little about you" ---------------------------------------------------------------------

    @Test fun `paired with the computer on the first launch, the aim and the voice come before Home`() {
        val computer = SetupFlow.start("en").chooseTarget(Lang.SL).next().choose(Way.COMPUTER)
        assertTrue(computer.asksAbout)
        val about = computer.manual().edit(url = "https://node.ts.net", token = "t").connecting().done().paired()
        assertEquals(Step.ABOUT, about.step)
        assertFalse(about.busy)
        assertNull(about.trouble)
        // the aim chosen there; null: the node's
        assertNull(about.aim)
        assertEquals("C1", about.chooseAim("c1").aim)
        assertNull(about.chooseAim("Z9").aim)
        // just this device asks it in its questions; the way back from the phone to a computer asks nothing again
        assertFalse(SetupFlow.start("de").chooseTarget(Lang.IT).next().choose(Way.DEVICE).asksAbout)
        assertFalse(SetupFlow(step = Step.COMPUTER, target = Lang.SL, base = Lang.EN, way = Way.COMPUTER, firstRun = false).asksAbout)
    }

    @Test fun `Back on every step`() {
        val welcome = SetupFlow.start("de")
        val languages = welcome.chooseTarget(Lang.IT)
        val ways = languages.next()
        val device = ways.choose(Way.DEVICE)
        val computer = ways.choose(Way.COMPUTER)
        val manual = computer.manual()
        val about = computer.paired()
        val back = mapOf(
            welcome to Step.WELCOME, languages to Step.WELCOME, ways to Step.LANGUAGES, device to Step.WAYS,
            computer to Step.WAYS, manual to Step.COMPUTER, about to Step.ABOUT,
        )
        assertEquals("every step has its Back", Step.entries.toSet(), back.keys.map { it.step }.toSet())
        for ((f, to) in back) assertEquals("${f.step}", to, f.back().step)
        // Back keeps what was chosen, and drops what went wrong and the spinner
        assertEquals(Lang.IT, ways.back().back().target)
        assertNull(manual.failed(PairTrouble.of(PairFailure.UNREACHABLE)).back().trouble)
        assertFalse(manual.edit(url = "https://node.ts.net", token = "t").connecting().back().busy)
    }

    // --- my voice at setup ------------------------------------------------------------------------------------------------

    /** The node's voices for a child: two boys', two girls' (a profile without a gender is offered both). */
    private fun state(voice: String?, chosen: Boolean = false, off: Boolean = false, options: List<si.lanisce.lani.data.MyVoiceOption> = childOptions) =
        si.lanisce.lani.data.MyVoiceState(options, voice, off, chosen, "child", "Ciao! Sono Teo.", "it")

    private val childOptions = listOf(
        si.lanisce.lani.data.MyVoiceOption("player-girl-a", "player-girl-a", "Mia", "female", "child", 0, true),
        si.lanisce.lani.data.MyVoiceOption("player-girl-b", "player-girl-b", "Sofia", "female", "child", 1, false),
        si.lanisce.lani.data.MyVoiceOption("player-boy-a", "player-boy-a", "Teddy", "male", "child", 0, true),
        si.lanisce.lani.data.MyVoiceOption("player-boy-b", "player-boy-b", "Leo", "male", "child", 1, false),
    )

    @Test fun `a child's voice at setup is a child's of their gender`() {
        val v = si.lanisce.lani.data.MyVoices
        // Teo, a boy: the first boy's voice, whatever the node's default before it knew him
        assertEquals("player-boy-a", v.preselect(state("player-girl-a"), "male", child = true))
        assertEquals("player-girl-a", v.preselect(state("player-boy-a"), "female", child = true))
        // no gender said: the node's default
        assertEquals("player-girl-a", v.preselect(state("player-girl-a"), null, child = true))
        // a grown-up gets a grown-up's (Jan: Luka)
        val adults = listOf(
            si.lanisce.lani.data.MyVoiceOption("male.sl", "male", "Luka", "male", "adult", 0, true),
            si.lanisce.lani.data.MyVoiceOption("player-man-a", "player-man-a", "Matej", "male", "adult", 1, false),
        )
        assertEquals("male.sl", v.preselect(state("male.sl", options = adults), "male", child = false))
        // chosen on the node already: their choice, or none
        assertEquals("player-boy-b", v.preselect(state("player-boy-b", chosen = true), "male", child = true))
        assertEquals(si.lanisce.lani.data.MyVoices.OFF, v.preselect(state("player-boy-a", chosen = true, off = true), "male", child = true))
        // nothing offered: nothing
        assertNull(v.preselect(state(null, options = emptyList()), "male", child = true))
    }

    @Test fun `going on at setup keeps the voice shown, the node's default as default`() {
        val v = si.lanisce.lani.data.MyVoices
        assertEquals(si.lanisce.lani.data.MyVoices.DEFAULT, v.keepAtSetup(state("player-boy-a"), "player-boy-a"))
        assertEquals("player-boy-a", v.keepAtSetup(state("player-girl-a"), "player-boy-a"))
        // chosen already (a tap on a row), or nothing shown: nothing more
        assertNull(v.keepAtSetup(state("player-boy-b", chosen = true), "player-boy-b"))
        assertNull(v.keepAtSetup(state(null, options = emptyList()), null))
    }
}
