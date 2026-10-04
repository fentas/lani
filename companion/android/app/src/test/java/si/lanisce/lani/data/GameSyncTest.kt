package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res

class GameSyncTest {
    private val a = GameState(seed = 1, resources = mapOf(Res.FOOD to 3))
    private val b = GameState(seed = 1, resources = mapOf(Res.FOOD to 9), age = Age.TABOR)

    @Test fun `no village anywhere creates one`() {
        assertEquals(GameSync.Decision.Create, GameSync.reconcile(null, null))
    }

    @Test fun `a local village the node lost is pushed from rev 0`() {
        val d = GameSync.reconcile(GameCache(4, a), null)
        assertEquals(GameSync.Decision.Push(GameCache(0, a, dirty = true)), d)
    }

    @Test fun `clean local copy adopts the node`() {
        assertEquals(GameSync.Decision.Adopt(GameSnapshot(5, b)), GameSync.reconcile(GameCache(4, a), GameSnapshot(5, b)))
        assertEquals(GameSync.Decision.Adopt(GameSnapshot(5, b)), GameSync.reconcile(null, GameSnapshot(5, b)))
    }

    @Test fun `offline changes on the latest rev are pushed, stale ones lose`() {
        assertEquals(GameSync.Decision.Push(GameCache(5, a, true)), GameSync.reconcile(GameCache(5, a, true), GameSnapshot(5, b)))
        assertEquals(GameSync.Decision.Adopt(GameSnapshot(6, b)), GameSync.reconcile(GameCache(5, a, true), GameSnapshot(6, b)))
    }

    @Test fun `after a put`() {
        assertEquals(GameCache(6, a, dirty = false), GameSync.afterPut(a, a, GamePut.Saved(6)))
        assertTrue(GameSync.afterPut(a, b, GamePut.Saved(6)).dirty) // changed while in flight: push again
        val c = GameSync.afterPut(a, a, GamePut.Conflict(GameSnapshot(9, b)))
        assertEquals(GameCache(9, b, dirty = false), c)
    }

    @Test fun `cache round-trips through the app's json`() {
        val s = b.copy(event = GameEvent("e1", EventKind.WOLVES, 2, 10, 20))
        val c = GameCache(3, s, dirty = true)
        assertEquals(c, GameSync.decode(GameSync.encode(c)))
        assertEquals(null, GameSync.decode("not json"))
        assertFalse(GameSync.encode(c).contains("\"state\":null"))
    }

    @Test fun `village alerts fire once per running event`() {
        val e = GameEvent("e1", EventKind.WOLVES, 2, startedAt = 0, deadline = 10_000)
        val s = a.copy(event = e)
        assertEquals(e, VillageAlert.pending(s, null, 5_000))
        assertEquals(null, VillageAlert.pending(s, "e1", 5_000))
        assertEquals(null, VillageAlert.pending(s, null, 20_000))
        assertEquals(null, VillageAlert.pending(a, null, 0))
        assertTrue(VillageAlert.title(EventKind.WOLVES).contains("Wolves are attacking your village"))
    }

    @Test fun `wire snapshot decodes with unknown fields`() {
        val raw = """{"rev":7,"state":{"schema":1,"age":"VAS","resources":{"FOOD":4},"future":true},"extra":1}"""
        val s = json.decodeFromString(GameSnapshot.serializer(), raw)
        assertEquals(7, s.rev)
        assertEquals(Age.VAS, s.state.age)
        assertEquals(4, s.state.res(Res.FOOD))
    }
}
