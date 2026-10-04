package si.lanisce.lani.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.app.Notices
import si.lanisce.lani.app.QuestionsController
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * Questions between towns (data/TownQuestions.kt, app/QuestionsController.kt): the lists as the bridge keeps them, what a
 * question's or an answer's sending came to, the tutor's check before sending, a retry with the same id, the answer that
 * waits for the guest's town, the events and where their notifications lead, and the labels with the names' Slovene forms.
 */
class TownQuestionsTest {
    private val jan = "a".repeat(32)
    private val mia = "b".repeat(32)
    private val own = L10n.pair

    @After fun home() {
        L10n.pair = own
    }

    private val listBody = """
        {"asked":[
           {"key":"$mia:ask-1","id":"ask-1","town":{"id":"$mia","name":"Il mio villaggio","learner":"Mia","culture":"friuli","language":"it"},
            "at":"2026-09-26T10:00:00Z","language":"it","text":"Come si chiama il tuo cane?","conversation":"tq_1",
            "feedback":{"text":"Brava!","score":9,"at":"x"},"answer":{"text":"Si chiama Fido.","at":"2026-09-26T11:00:00Z"}},
           {"key":"$mia:ask-2","id":"ask-2","town":{"id":"$mia","name":"Il mio villaggio","learner":"Mia"},"text":"E il gatto?"},
           {"id":"no-key"}],
         "received":[
           {"key":"$mia:ask-m1","id":"ask-m1","from":{"id":"$mia","name":"Il mio villaggio","learner":"Mia","culture":"friuli","language":"it"},
            "at":"2026-09-26T12:00:00Z","language":"sl","text":"Kako je ime tvoji vasi?"},
           {"key":"$mia:ask-m0","id":"ask-m0","from":{"id":"$mia","name":"Il mio villaggio","learner":"Mia"},"language":"sl","text":"Živjo!",
            "answer":{"text":"Živjo, Mia!","at":"x","sent":false,"conversation":"tq_9"}}],
         "answered":{"$mia":2,"bad":"x"}}
    """.trimIndent()

    @Test fun `the lists - both sides, what can't be read left out, the open questions, the answered count`() {
        val l = TownQuestions.parse(listBody)
        assertEquals(listOf("$mia:ask-1", "$mia:ask-2"), l.asked.map { it.key })
        assertEquals(9.0, l.asked[0].feedback?.score)
        assertEquals("Si chiama Fido.", l.asked[0].answer?.text)
        assertEquals("Mia", l.asked[0].who)
        assertEquals(listOf("$mia:ask-m1"), l.open.map { it.key })
        assertFalse(l.received[1].answer!!.sent)
        assertEquals(2, l.to(mia).size)
        assertEquals(mapOf(mia to 2), l.answered)
        assertEquals(TownQuestionsList(), TownQuestions.parse("[]"))
    }

    @Test fun `a text goes as one plain line, cut to its length`() {
        assertEquals("Ciao b ! come va?", TownQuestions.clean(" Ciao <b>!\ncome\tva? ", 200))
        assertEquals(200, TownQuestions.clean("x".repeat(500), TownQuestions.MAX_QUESTION).length)
    }

    @Test fun `the labels name the guest in Slovene's forms - od Jana, vprašaj Mio`() {
        L10n.pair = LangPair.DEFAULT // Jan: Slovene · English
        assertEquals("Mio", TownQuestions.accusativeSl("Mia"))
        assertEquals("Jana", TownQuestions.accusativeSl("Jan"))
        val q = BridgeEvent.TownQuestion(TownQuestions.QUESTION, "$mia:ask-m1", "Il mio villaggio", "Mia", "Kako je ime tvoji vasi?")
        assertEquals("❓ Vprašanje od Mie · A question from Mia", TownQuestions.title(q))
        assertEquals("💬 Odgovor od Mie · An answer from Mia", TownQuestions.title(q.copy(kind = TownQuestions.ANSWER)))
        L10n.pair = LangPair(Lang.IT, Lang.SL) // Luka: Italian · Slovene
        assertEquals("❓ Una domanda da Jan · Vprašanje od Jana", TownQuestions.title(BridgeEvent.TownQuestion(TownQuestions.QUESTION, "k", "Moja vas", "Jan", "?")))
        assertEquals("Chiedi a Jan · Vprašaj Jana", si.lanisce.lani.l10n.bi("questions.askWho", *TownQuestions.whoArgs("Jan")))
    }

    @Test fun `what sending came to - sent, kept for later, or why not`() {
        L10n.pair = LangPair.DEFAULT
        assertEquals(QuestionSend.Sent("$mia:ask-1"), TownQuestions.askOutcome(200, """{"ok":true,"key":"$mia:ask-1","conversation":"tq_1"}""", "Il mio villaggio"))
        val open = TownQuestions.askOutcome(409, """{"error":"x","code":"too_many_open"}""", "Il mio villaggio")
        assertTrue(open is QuestionSend.Refused && open.problem.startsWith("⏳"))
        val child = TownQuestions.askOutcome(409, """{"code":"not_for_a_child"}""", "Luka")
        assertTrue(child is QuestionSend.Refused && child.problem.startsWith("🧒"))
        val away = TownQuestions.askOutcome(502, """{"code":"unreachable"}""", "Il mio villaggio")
        assertTrue(away is QuestionSend.Refused && away.problem.contains("Il mio villaggio"))
        assertEquals(QuestionSend.Sent("k"), TownQuestions.answerOutcome(200, """{"ok":true,"key":"k","sent":true}""", "Moja vas"))
        val kept = TownQuestions.answerOutcome(200, """{"ok":true,"key":"k","sent":false,"code":"unreachable"}""", "Moja vas")
        assertTrue(kept is QuestionSend.Kept && kept.key == "k" && kept.problem.contains("Moja vas"))
        val twice = TownQuestions.answerOutcome(409, """{"code":"answered"}""", "Moja vas")
        assertTrue(twice is QuestionSend.Refused && twice.problem.startsWith("✅"))
    }

    @Test fun `the events, and where their notifications lead`() {
        val q = BridgeEvent.parse("""{"type":"town_question","key":"$jan:ask-1","from":"Moja vas","learner":"Jan","text":"Come si chiama il tuo cane?"}""")
        assertEquals(BridgeEvent.TownQuestion(TownQuestions.QUESTION, "$jan:ask-1", "Moja vas", "Jan", "Come si chiama il tuo cane?"), q)
        val a = BridgeEvent.parse("""{"type":"town_answer","key":"$mia:ask-1","from":"Il mio villaggio","learner":"Mia","text":"Si chiama Fido."}""")
        assertEquals(TownQuestions.ANSWER, (a as BridgeEvent.TownQuestion).kind)
        assertNull(BridgeEvent.parse("""{"type":"town_question","from":"x"}"""))
        assertEquals(DeepLink.Question("$jan:ask-1", asked = false), DeepLink.of(q!!))
        assertEquals(DeepLink.Question("$mia:ask-1", asked = true), DeepLink.of(a))
        for (l in listOf(DeepLink.Question("$jan:ask-1", asked = false), DeepLink.Question("$mia:ask-00000001", asked = true))) {
            assertEquals(l, DeepLink.parse(DeepLink.encode(l)))
        }
        assertNull(DeepLink.parse("question:a:"))
    }

    /** The learner's bridge, faked: what was posted, and the answers to give. */
    private class FakeBridge {
        val posts = ArrayList<Pair<String, JsonObject>>()
        var answer: (String, JsonObject) -> Pair<Int, String>? = { _, _ -> 200 to "{}" }
        var list = """{"asked":[],"received":[]}"""
        var fetches = 0

        suspend fun fetch(): Pair<Int, String>? {
            fetches++
            return 200 to list
        }

        suspend fun post(path: String, body: JsonObject): Pair<Int, String>? {
            posts += path to body
            return answer(path, body)
        }
    }

    private fun controller(b: FakeBridge, onList: (TownQuestionsList) -> Unit = {}, onAnswered: () -> Unit = {}) =
        QuestionsController(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), b::fetch, b::post, Notices(), onList, onAnswered)

    private fun reply(conversation: String, text: String, data: JsonObject? = null) = BridgeEvent.Reply(conversation, text, score = null, data = data)

    @Test fun `the tutor checks a draft first - its feedback comes on the check's conversation`() = runBlocking {
        val b = FakeBridge().apply { answer = { _, _ -> 200 to """{"conversation_id":"tq_check1"}""" } }
        val c = controller(b)
        c.checkDraft("Come si chiama il tuo cane", town = mia).join()
        assertEquals("/towns/questions/check" to buildJsonObject { put("text", "Come si chiama il tuo cane"); put("town", mia) }, b.posts.single())
        assertTrue(c.check!!.waiting)
        assertFalse(c.onReply(reply("main", "Hi")))
        assertTrue(c.onReply(reply("tq_check1", "Manca il punto di domanda.", buildJsonObject { put("score", 7); put("corrected", "Come si chiama il tuo cane?") })))
        val done = c.check!!
        assertEquals(7, done.score)
        assertEquals("Come si chiama il tuo cane?", done.corrected)
        assertEquals("Manca il punto di domanda.", done.feedback)
    }

    @Test fun `a question is sent once by its id - a retry of the same sends the same, checked when the tutor saw it`() = runBlocking {
        val b = FakeBridge()
        val lists = ArrayList<TownQuestionsList>()
        val c = controller(b, onList = { lists += it })
        // the check saw this very text
        b.answer = { _, _ -> 200 to """{"conversation_id":"tq_c"}""" }
        c.checkDraft("Come si chiama il tuo cane?", town = mia).join()
        c.onReply(reply("tq_c", "Perfetto!", buildJsonObject { put("score", 10) }))
        // the learner's bridge can't be reached, then it can
        b.answer = { _, _ -> null }
        var out: QuestionSend? = null
        c.ask(mia, "Il mio villaggio", "Come si chiama il tuo cane?") { out = it }.join()
        assertTrue(out is QuestionSend.Refused)
        b.answer = { _, body -> 200 to """{"ok":true,"key":"$mia:${body["id"]!!.jsonPrimitive.content}"}""" }
        c.ask(mia, "Il mio villaggio", "Come si chiama il tuo cane?") { out = it }.join()
        val asks = b.posts.filter { it.first == "/towns/$mia/questions" }.map { it.second }
        assertEquals(2, asks.size)
        assertEquals(asks[0]["id"], asks[1]["id"])
        assertEquals("true", asks[1]["checked"]!!.jsonPrimitive.content)
        assertTrue(out is QuestionSend.Sent)
        assertNull(c.check) // sent: the check is done with
        assertTrue(lists.isNotEmpty()) // the list came again
        // another question is another id
        c.ask(mia, "Il mio villaggio", "E il gatto?").join()
        val third = b.posts.last { it.first == "/towns/$mia/questions" }.second
        assertNotEquals(asks[0]["id"], third["id"])
        assertEquals("false", third["checked"]!!.jsonPrimitive.content)
    }

    @Test fun `an answer is kept for later when the guest's town is away - it pays once`() = runBlocking {
        val b = FakeBridge()
        var paid = 0
        val c = controller(b, onAnswered = { paid++ })
        val q = TownQuestions.parse(listBody).received[0]
        b.answer = { _, _ -> 200 to """{"ok":true,"key":"${q.key}","sent":false,"code":"unreachable"}""" }
        var out: QuestionSend? = null
        c.answer(q, "Moja vas je pod Sabotinom.") { out = it }.join()
        assertTrue(out is QuestionSend.Kept)
        assertEquals("/towns/questions/answer" to buildJsonObject { put("key", q.key); put("text", "Moja vas je pod Sabotinom.") }, b.posts.last())
        // sent again, from the question with its answer kept: the same text, no second pay
        b.answer = { _, _ -> 200 to """{"ok":true,"key":"${q.key}","sent":true}""" }
        c.answer(q.copy(answer = HostAnswer("Moja vas je pod Sabotinom.", sent = false)), "Moja vas je pod Sabotinom.") { out = it }.join()
        assertTrue(out is QuestionSend.Sent)
        assertEquals(1, paid)
        // a grading of the answer comes on its conversation: the list comes again
        val before = b.fetches
        assertTrue(c.onReply(reply("tq_9", "Bravo!")))
        assertEquals(before + 1, b.fetches)
    }
}
