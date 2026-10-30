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
}
