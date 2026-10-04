package si.lanisce.lani.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.app.Haggle
import si.lanisce.lani.app.VisitController
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.Happening
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.scene.DialogRun

/**
 * Things to do on a visit (data/TownActs.kt, app/VisitController.kt): the host's requests and market as the bridge passes
 * them on, a request's run in the town's language, the writes (each sent once by its id, a retry with the same), the
 * haggle's deal, the guest book, and a guest's dialog in the town's places.
 */
class TownActsTest {
    private val mia = "fec16ecd34869e522e575d0fb9ba45de"
    private val own = L10n.pair

    @Before fun visiting() {
        VisitWorld.enter("it", "friuli")
        L10n.visit = LangPair(Lang.IT, Lang.EN) // Jan visiting Mia: Italian explained in English
    }

    @After fun home() {
        VisitWorld.leave()
        Cultures.use(null as String?)
        L10n.pair = own
    }

    private val requests = """
        {"v":1,"town":"$mia","culture":"friuli","language":"it","fetched_at":"2026-09-26T10:00:00.000Z",
         "requests":[{"id":"q-1","giver":"Nonna Rosa","emoji":"👵","skill":"FOOD",
           "title":{"it":"La cucina di Nonna Rosa","sl":"Kuhinja babice Rose","en":"Nonna Rosa's kitchen"},
           "story":{"it":"Nonna Rosa fa il frico.","en":"Nonna Rosa is making frico."},
           "words":[{"id":"pane","word":"il pane","meaning":{"sl":"kruh","en":"bread"},"emoji":"🍞"},
             {"id":"latte","word":"il latte","meaning":{"sl":"mleko","en":"milk"}},
             {"id":"uovo","word":"l'uovo","meaning":{"sl":"jajce","en":"egg"}},
             {"id":"sale","word":"il sale","meaning":{"sl":"sol","en":"salt"}},
             {"id":"mela","word":"la mela","meaning":{"en":"apple"}}]},
           {"id":"q-2","giver":"Mugnaio Franco","title":{"it":"Il raccolto del mais"},"words":[]}]}
    """.trimIndent()

    @Test fun `the host's requests read in the visit's pair, and a request's run asks its words`() {
        val r = TownActs.parseRequests(requests)
        assertEquals(2, r.requests.size)
        val q = r.requests[0]
        assertEquals("La cucina di Nonna Rosa · Nonna Rosa's kitchen", TownActs.label(q.title, L10n.pair))
        assertEquals("Il raccolto del mais", TownActs.label(r.requests[1].title, L10n.pair))
        val run = TownActs.exercises(q, L10n.pair, 42)
        assertEquals(5, run.size)
        assertEquals(run, TownActs.exercises(q, L10n.pair, 42)) // the same run for the same town
        val choices = run.map { it as Exercise.Choice }
        assertTrue(choices.all { it.answer in it.options.indices && it.options.distinct().size == it.options.size && it.instruction == "👵 Nonna Rosa" })
        // "what does <word> mean?" answered with meanings in English, "how do you say <meaning>?" with the Italian words
        assertTrue(choices[0].options.all { it in setOf("bread", "milk", "egg", "salt", "apple") })
        assertTrue(choices[1].options.all { it in setOf("il pane", "il latte", "l'uovo", "il sale", "la mela") })
        assertTrue(TownActs.exercises(r.requests[1], L10n.pair, 42).isEmpty()) // no words: nothing to run
        assertEquals(4, TownActs.passMark(5))
        assertTrue(TownActs.passed(4, 5))
        assertFalse(TownActs.passed(3, 5))
        assertFalse(TownActs.passed(0, 0))
    }

    @Test fun `the market, the answers and the guest book as the bridge passes them on`() {
        val m = TownActs.parseMarket("""{"town":"$mia","culture":"friuli","language":"it","wares":{"miele":2,"gubana":1},"seller":{"emoji":"🏪","name":{"it":"Il mercato","en":"The market"}}}""")
        assertEquals(mapOf("miele" to 2, "gubana" to 1), m.wares)
        assertEquals("Il mercato · The market", TownActs.label(m.seller!!.name, L10n.pair))
        val guests = TownActs.parseGuests("""{"entries":[
            {"key":"$mia:gift-1","id":"gift-1","kind":"gift","from":{"id":"$mia","name":"Il mio villaggio","learner":"Mia"},"at":"2026-09-26T10:00:00Z","gift":{"good":{"culture":"friuli","id":"miele"},"message":"Ciao Jan!"}},
            {"key":"$mia:rp-1","kind":"trade","from":{"id":"$mia"},"trade":{"got":{"culture":"friuli","goods":{"miele":1}},"gave":{"culture":"primorska","res":{}},"score":8}},
            {"broken":true}]}""")
        assertEquals(2, guests.size)
        assertEquals("Ciao Jan!", guests[0].gift?.message)
        assertEquals(8, guests[1].trade?.score)
        val done = TownActs.outcome(200, """{"ok":true,"key":"k","kind":"help","amount":3,"giver":"Nonna Rosa","thanks":{"culture":"friuli","good":"gubana"}}""", "Il mio villaggio")
        assertEquals("gubana", (done as VisitWrite.Done).answer.thanks?.good)
        val refused = TownActs.outcome(409, """{"error":"x","code":"not_open"}""", "Il mio villaggio")
        assertTrue((refused as VisitWrite.Refused).problem.startsWith("📜"))
        assertTrue((TownActs.outcome(429, """{"code":"rate_limited"}""", "x") as VisitWrite.Refused).problem.startsWith("⏳"))
        assertTrue((TownActs.outcome(502, """{"code":"unreachable"}""", "x") as VisitWrite.Refused).problem.startsWith("📡"))
        assertEquals("Ciao Mia b come stai?", TownActs.cleanMessage("Ciao Mia <b>\ncome stai?"))
        assertEquals(TownActs.MAX_MESSAGE, TownActs.cleanMessage("x".repeat(500)).length)
    }

    @Test fun `a haggle's deal - the tutor says deal, with a score of 6 or more`() {
        fun d(deal: Boolean?, score: Int?) = buildJsonObject { put("debrief", true); deal?.let { put("deal", it) }; score?.let { put("score", it) } }
        assertEquals(8, TownActs.deal(d(true, 8)))
        assertEquals(6, TownActs.deal(d(true, 6)))
        assertNull(TownActs.deal(d(true, 5)))
        assertNull(TownActs.deal(d(false, 9)))
        assertNull(TownActs.deal(d(null, 9)))
        assertNull(TownActs.deal(null))
    }

    /** The learner's bridge: what it was sent, and its answers. */
    private class FakeBridge {
        val writes = mutableListOf<Triple<String, String, JsonObject>>()
        var status = 200
        var answer: (String, JsonObject) -> String = { kind, body -> """{"ok":true,"key":"$kind:${body["id"]!!.jsonPrimitive.content}","kind":"$kind","amount":3,"thanks":{"culture":"friuli","good":"gubana"}}""" }
        var offline = false
        /** The ids of every write tried, sent or not. */
        val tried = mutableListOf<String>()
        suspend fun fetch(id: String, kind: String): Pair<Int, String> = when (kind) {
            Visits.PUBLIC -> 200 to """{"v":1,"town":{"id":"$id","name":"Il mio villaggio","learner":"Mia","culture":"friuli","language":"it"},"age":"VAS","residents":[]}"""
            "requests" -> 200 to """{"town":"$id","culture":"friuli","language":"it","requests":[{"id":"q-1","giver":"Nonna Rosa","words":[]}]}"""
            "market" -> 200 to """{"town":"$id","culture":"friuli","language":"it","wares":{"miele":2}}"""
            else -> 200 to """{"town":"$id","culture":"friuli","language":"it","$kind":[]}"""
        }
        suspend fun send(id: String, kind: String, body: JsonObject): Pair<Int, String> {
            tried += body["id"]!!.jsonPrimitive.content
            if (offline) throw java.io.IOException("no route")
            writes += Triple(id, kind, body)
            return status to if (status == 200) answer(kind, body) else """{"error":"no","code":"not_open"}"""
        }
    }

    private fun controller(b: FakeBridge) = VisitController(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), b::fetch, b::send)

    @Test fun `a visit's writes - help, a gift, a trade, each once by its id, a retry with the same id`() = runBlocking {
        val b = FakeBridge()
        val c = controller(b)
        c.start(mia, "Il mio villaggio", "Mia", "friuli", "it").join()
        c.loadActs()!!.join()
        val q = c.requests!!.single()
        assertEquals(mapOf("miele" to 2), c.market?.wares)

        // the network fails: the retry sends the same id
        b.offline = true
        assertTrue(c.help(q) is VisitWrite.Refused)
        b.offline = false
        val done = c.help(q) as VisitWrite.Done
        assertEquals(1, b.writes.size)
        val id = b.writes[0].third["id"]!!.jsonPrimitive.content
        assertTrue(id.startsWith("help-"))
        assertEquals(listOf(id, id), b.tried) // the retry after the failure: the same id
        assertEquals("q-1", b.writes[0].third["request"]!!.jsonPrimitive.content)
        assertEquals("gubana", done.answer.thanks?.good)
        assertTrue(c.requests!!.isEmpty()) // done: off the list

        // a gift: a good and a message, cleaned
        c.gift("med", "Ciao <Mia>!") as VisitWrite.Done
        val gift = b.writes.last().third
        assertEquals("med", gift["good"]!!.jsonPrimitive.content)
        assertEquals("Ciao Mia !", gift["message"]!!.jsonPrimitive.content)

        // a trade: the haggle's conversation is its id; the market spares one honey less
        val h = Haggle(mia, "Il mio villaggio", Bundle("primorska", res = mapOf("WOOD" to 20)), Bundle("friuli", goods = mapOf("miele" to 1)))
        c.trade("rp-1234", h, 8) as VisitWrite.Done
        val t = b.writes.last().third
        assertEquals("rp-1234", t["id"]!!.jsonPrimitive.content)
        assertEquals(8, t["score"]!!.jsonPrimitive.int)
        assertEquals(20, t["give"]!!.jsonObject["res"]!!.jsonObject["WOOD"]!!.jsonPrimitive.int)
        assertEquals(mapOf("miele" to 1), c.market?.wares)

        // refused by the host: why, for the learner; the request stays
        b.status = 409
        c.loadActs()!!.join()
        assertTrue((c.help(c.requests!!.single()) as VisitWrite.Refused).problem.startsWith("📜"))
        assertEquals(1, c.requests!!.size)
        c.end()
        assertTrue(c.help(q) is VisitWrite.Refused) // no visit: nothing is sent
    }

    @Test fun `a guest's dialog in the town's places - played to its end, nothing paid`() {
        val c = VisitController(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), { _, _ -> null })
        val nives = ScenePerson("nives", "Zia Nives", "📻", "aunt", "pier", villager = "nives")
        val d = Dialog(
            "fuori",
            listOf(
                DialogLine(who = "nives", sl = "Sei di fuori?", en = "Are you from away?"),
                DialogLine(choices = listOf(DialogChoice(sl = "Sì, vengo dal Collio.", en = "Yes, from the Collio.", ok = true), DialogChoice(sl = "No.", en = "No.", why = "…"))),
                DialogLine(who = "nives", sl = "Benvenuto!", en = "Welcome!"),
            ),
            guests = true,
        )
        val h = Happening("fuori", "Zia Nives sul molo", who = "nives", dialog = "fuori", guests = true)
        val scene = SceneSpec(id = "il-mare", title = "Il mare", art = "sea", from = listOf("spot:horizon"), people = listOf(nives), happenings = listOf(h), dialogs = listOf(d))
        assertTrue(c.startSceneTalk(ActiveHappening(scene, h, nives)))
        var guard = 0
        while (c.sceneTalk!!.run.step != DialogRun.Step.END && guard++ < 10) {
            val run = c.sceneTalk!!.run
            // the choices come shuffled: pick the right one where it is shown
            if (run.step == DialogRun.Step.CHOOSE) c.chooseInScene(run.choices.indexOfFirst { it.ok }) else c.nextInScene()
        }
        val t = c.sceneTalk
        assertNotNull(t)
        assertTrue(t!!.settled)
        assertNull(t.paid)
        c.closeSceneTalk()
        assertNull(c.sceneTalk)
    }
}
