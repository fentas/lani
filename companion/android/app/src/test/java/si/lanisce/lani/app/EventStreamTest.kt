package si.lanisce.lani.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventStreamTest {
    @Test fun `a replayed event is handled once`() {
        val c = EventCursor(100)
        assertTrue(c.accept(101))
        assertTrue(c.accept(102))
        // A reconnect that replays from an older position (or the same event twice) is skipped.
        assertFalse(c.accept(101))
        assertFalse(c.accept(102))
        assertTrue(c.accept(103))
        assertEquals(103, c.lastId)
    }

    @Test fun `an event without an id passes and keeps the position`() {
        val c = EventCursor(5)
        assertTrue(c.accept(null))
        assertEquals(5, c.lastId)
    }

    @Test fun `reconnects back off while the node is unreachable, up to a minute`() {
        assertEquals(3_000L, EventStream.reconnectDelay(0)) // after a stream that worked
        assertEquals(3_000L, EventStream.reconnectDelay(1))
        assertEquals(6_000L, EventStream.reconnectDelay(2))
        assertEquals(12_000L, EventStream.reconnectDelay(3))
        assertEquals(60_000L, EventStream.reconnectDelay(6))
        assertEquals(60_000L, EventStream.reconnectDelay(500))
    }
}
