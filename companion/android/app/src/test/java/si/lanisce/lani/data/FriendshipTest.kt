package si.lanisce.lani.data

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.game.Guests
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/** Friendship between towns on the wire (data/Friendship.kt): GET /towns/friendship, a move's answer, the market's rates, why not. */
class FriendshipTest {
    private val pair = L10n.pair

    @Before fun jan() {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
    }

    @After fun back() {
        L10n.pair = pair
    }

    /** As the bridge answers (companion/bridge/src/features/friendship.ts), with fields this app doesn't know yet. */
    private val body = """
        {"self": {"id": "a", "child": false, "language": "sl"}, "levels": [0, 8, 24, 48, 90], "points": {"visit": 2}, "feast_days": 3,
         "towns": [{
           "id": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "name": "Il mio villaggio", "learner": "Mia", "culture": "friuli", "language": "it",
           "status": "ok", "level": 3, "points": 68, "next": {"level": 4, "points": 90},
           "mine": {"visit": 13, "help": 1, "gift": 3, "trade": 3, "aid": 1, "answer": 0, "hug": 2}, "theirs": {"visit": 13},
           "mine_points": 42, "theirs_points": 26,
           "rates": {"level": 3, "fair_share": 0.35, "deal_score": 5, "max_spare": 4},
           "child": false,
           "event": {"id": "ev-1", "kind": "WOLVES", "strength": 2, "until": "2026-09-27T06:00:00.000Z", "aided": false, "can": true},
           "feast": {"mine": "2026-09-25", "theirs": "2026-09-26", "shared": {"key": "feast:b:2026-09-25:2026-09-26", "first": "2026-09-25", "last": "2026-09-26"}, "open_until": null},
           "moves": {"skill": {"language": "it", "level": "A2", "need": "A2"}, "in": {"ok": false, "why": "soon", "until": "2026-10-10"}, "out": {"ok": true},
             "list": [{"key": "a:move-2", "id": "move-2", "dir": "in", "by": "me", "status": "done", "at": "2026-09-26T10:00:00Z", "done_at": "2026-09-26T10:05:00Z",
               "person": {"id": "m-stefano-furlan-9z", "name": "Stefano Furlan", "emoji": "👨", "art": "man", "voice": "male", "role": {"it": "Pescatore", "en": "Fisher"},
                 "family": "Furlan", "culture": "friuli", "language": "it", "from": {"id": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "name": "Il mio villaggio"}}}]}
         }]}
    """.trimIndent()

    @Test fun `GET towns friendship reads, with what the app doesn't know left aside`() {
        val t = Friendships.parse(body).towns.single()
        assertEquals(3, t.level)
        assertEquals(68, t.points)
        assertEquals(4, t.next?.level)
        assertEquals(2, t.mine["hug"]) // a kind of a newer bridge: kept, counts nothing here
        assertEquals(MarketRates(3, 0.35, 5, 4), t.rates)
        assertEquals("WOLVES", t.event?.kind)
        assertTrue(t.event!!.can && !t.event!!.aided)
        assertEquals("feast:b:2026-09-25:2026-09-26", t.feast.shared?.key)
        assertNull(t.feast.openUntil)
        assertEquals("soon", t.moves.into.why)
        assertTrue(t.moves.out.ok)
        val m = t.moves.list.single()
        assertTrue(m.done && !m.open)
        assertEquals("Stefano Furlan", m.person?.name)
        assertEquals("Il mio villaggio", m.person?.from?.name)
        // an older bridge's town: all defaults
        val bare = Friendships.parse("""{"towns": [{"id": "x"}]}""").towns.single()
        assertEquals(0, bare.level)
        assertEquals(MarketRates(), bare.rates)
        assertTrue(bare.moves.list.isEmpty())
    }

    @Test fun `a move's answer, and why a friendship's write didn't go through`() {
        val m = Friendships.parseMove("""{"ok": true, "move": {"key": "a:move-3", "id": "move-3", "dir": "out", "by": "me", "status": "offered"}}""")
        assertEquals("move-3", m?.id)
        assertTrue(m!!.open)
        assertNull(Friendships.parseMove("""{"ok": true}"""))
        assertTrue(Friendships.problem(409, "too_early", "X").startsWith("💞"))
        assertTrue(Friendships.problem(409, "child", "X").startsWith("👨‍👩‍👦"))
        assertTrue(Friendships.problem(409, "skill", "X").startsWith("📚"))
        assertTrue(Friendships.problem(429, "rate_limited", "X").startsWith("⏳")) // the visit's own
        assertTrue(Friendships.newId("aid").startsWith("aid-"))
    }

    @Test fun `the market's rates - a smaller share and a lower haggle between friends`() {
        val market = TownActs.parseMarket("""{"town": "b", "wares": {"miele": 4}, "rates": {"level": 2, "fair_share": 0.4, "deal_score": 5, "max_spare": 4}}""")
        assertEquals(0.4, market.rates.fairShare, 0.0)
        assertEquals(MarketRates(), TownActs.parseMarket("""{"town": "b", "wares": {}}""").rates) // an older bridge: the fixed rates
        val honey = Bundle("friuli", goods = mapOf("miele" to 1)) // worth 20
        val wood = { n: Int -> Bundle("primorska", res = mapOf("WOOD" to n)) }
        assertFalse(Guests.fair(wood(9), honey))
        assertTrue(Guests.fair(wood(9), honey, market.rates.fairShare))
        val deal = { score: Int -> buildJsonObject { put("debrief", true); put("deal", true); put("score", JsonPrimitive(score)) } }
        assertNull(TownActs.deal(deal(5)))
        assertEquals(5, TownActs.deal(deal(5), market.rates.dealScore))
    }

    @Test fun `a guest's help against trouble reads from the guest book`() {
        val e = TownActs.parseGuests("""{"entries": [{"key": "b:aid-1", "id": "aid-1", "kind": "aid", "from": {"id": "b", "learner": "Mia"}, "at": "x", "aid": {"event": "ev-1", "kind": "WOLVES"}}]}""").single()
        assertEquals(GuestAid("ev-1", "WOLVES"), e.aid)
        assertEquals("Pomoč od Mia · Help from Mia", TownGuests.title(BridgeEvent.TownGuest(TownActs.AID, "Il mio villaggio", "Mia")))
        assertEquals("Preselitev: Mia · A move: Mia", TownGuests.title(BridgeEvent.TownGuest(TownActs.MOVE, "Il mio villaggio", "Mia")))
    }
}
