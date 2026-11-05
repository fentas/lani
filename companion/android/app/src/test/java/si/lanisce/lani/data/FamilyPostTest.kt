package si.lanisce.lani.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.app.FamilyCourier
import si.lanisce.lani.app.FamilyHome
import si.lanisce.lani.tutor.live.FamilyKey
import java.net.InetAddress
import kotlin.concurrent.thread

/** What a parent sends a device after the exchange (data/FamilyPost, docs/plans/05-built/leftovers-app.md): what it does, and who can't. */
class FamilyPostTest {
    private val bond = ByteArray(32) { it.toByte() }
    private val t0 = 1_000_000L
    private val parentBook = FamilyBook().added("fam", "m1", "Teo's tablet", bond, t0)
    private val gift = FamilyLink.Gift(provider = "anthropic", key = "sk-ant-api03-family-key-0123456789", family = "fam", member = "m1", from = "Pixel")

    // --- the book ---------------------------------------------------------------------------------------------------------

    @Test fun `a limit waits until the device says it has it`() {
        val b = parentBook.setCap("m1", 0.5)
        val m = b.member("m1")!!
        assertEquals(0.5, m.cap)
        assertEquals(FamilyNote(1, FamilyNote.BUDGET, 0.5), m.pending)
        assertNull(m.capThere)
        val told = b.heard("m1", 1, t0 + 5).member("m1")!!
        assertNull(told.pending)
        assertEquals(0.5, told.capThere)
        assertEquals(t0 + 5, told.heard)
    }

    @Test fun `only the last word counts, and a word older than it settles nothing`() {
        val b = parentBook.setCap("m1", 0.5).setCap("m1", 2.0)
        assertEquals(FamilyNote(2, FamilyNote.BUDGET, 2.0), b.member("m1")!!.pending)
        val heard = b.heard("m1", 1, t0).member("m1")!!
        assertEquals("the device has only the first", FamilyNote(2, FamilyNote.BUDGET, 2.0), heard.pending)
        assertEquals(t0, heard.heard)
    }

    @Test fun `what the device has already needs no note`() {
        val b = parentBook.setCap("m1", 0.5).heard("m1", 1, t0).setCap("m1", 0.5)
        assertNull(b.member("m1")!!.pending)
    }

    @Test fun `a device told to leave stays listed until it has heard, and is gone then`() {
        val b = parentBook.remove("m1")
        assertTrue(b.member("m1")!!.leaving)
        assertTrue("a limit no longer goes to it", b.setCap("m1", 5.0).member("m1")!!.leaving)
        assertTrue("an older word settles nothing", b.heard("m1", 0, t0).member("m1")!!.leaving)
        assertNull(b.heard("m1", 1, t0).member("m1"))
    }

    @Test fun `a parent can give up on telling it`() {
        assertNull(parentBook.remove("m1").drop("m1").member("m1"))
    }

    @Test fun `a note is taken once, and one seen before does nothing`() {
        val b = FamilyBook().joined(gift, bond)
        val once = b.took(FamilyNote(3, FamilyNote.BUDGET, 1.0))!!
        assertEquals(3L, once.parent!!.seen)
        assertNull("the same again", once.took(FamilyNote(3, FamilyNote.BUDGET, 9.0)))
        assertNull("an older one, recorded and played back", once.took(FamilyNote(2, FamilyNote.FORGET)))
    }

    @Test fun `what an older sender gave is no bond`() {
        assertNull(FamilyBook().joined(gift.copy(family = null, member = null), bond).parent)
    }

    @Test fun `this device keeps a hash of the family's key, never the key`() {
        val p = FamilyBook().joined(gift, bond).parent!!
        assertEquals(FamilyPost.sha(gift.key!!), p.keySha)
        assertFalse(gift.key!! in FamilyBook().joined(gift, bond).text())
    }

    @Test fun `the book comes back as it was kept, and garbage is a new book`() {
        val b = parentBook.setCap("m1", 0.5).copy(parent = FamilyBook().joined(gift, bond).parent, keyWaiting = FamilyKeyWaiting("anthropic", "k", "BALANCED"))
        assertEquals(b, FamilyBook.parse(b.text()))
        assertEquals(FamilyBook(), FamilyBook.parse("not json"))
        assertEquals(FamilyBook(), FamilyBook.parse(null))
    }

    @Test fun `a book, a member or a waiting key printed by accident shows no key`() {
        val b = parentBook.copy(parent = FamilyBook().joined(gift, bond).parent, keyWaiting = FamilyKeyWaiting("anthropic", gift.key!!))
        val secret = FamilyPost.b64(bond)
        for (printed in listOf(b.toString(), b.members[0].toString(), b.parent.toString(), b.keyWaiting.toString())) {
            assertFalse(secret in printed)
            assertFalse(gift.key!! in printed)
        }
    }

    // --- sealed, one way each ---------------------------------------------------------------------------------------------

    private val keys = FamilyPost.Keys(bond)

    @Test fun `a note opens only under its bond, and only as a note`() {
        val n = FamilyNote(1, FamilyNote.BUDGET, 0.5)
        val sealed = FamilyPost.sealNote(keys, n)
        assertEquals(n, FamilyPost.openNote(keys, sealed))
        assertNull("another family's", FamilyPost.openNote(FamilyPost.Keys(ByteArray(32) { 9 }), sealed))
        assertNull("what a device says is no command", FamilyPost.openNote(keys, FamilyPost.sealSeen(keys, 7)))
        assertNull("nor a note what a device says", FamilyPost.openSeen(keys, sealed))
    }

    @Test fun `a kind this app doesn't know is nothing`() {
        val sealed = FamilyLink.seal(keys.down, """{"seq":1,"kind":"give-key"}""".toByteArray())
        assertNull(FamilyPost.openNote(keys, sealed))
    }

    @Test fun `each direction has its key and box, and a bond too short is none`() {
        assertFalse(keys.down.contentEquals(keys.up))
        assertNotEquals(keys.boxDown, keys.boxUp)
        assertEquals(64, keys.boxDown.length)
        assertNull(FamilyPost.Keys.of(FamilyPost.b64(ByteArray(4))))
        assertNull(FamilyPost.Keys.of("not base64 ***"))
    }

    @Test fun `the name on the Wi-Fi says nothing of the family`() {
        val n = FamilyPost.serviceName("fam")
        assertFalse("fam" in n.removePrefix("lani-"))
        assertEquals(n, FamilyPost.serviceName("fam"))
        assertNotEquals(n, FamilyPost.serviceName("other"))
    }

    // --- a parent's device and a device of the family -----------------------------------------------------------------------

    private class Device(start: FamilyBook) : FamilyHome {
        @Volatile var book = start
        val caps = mutableListOf<Pair<Double?, String>>()
        var forgot: FamilyParent? = null
        val courier = FamilyCourier({ book }, { f -> synchronized(this) { book = f(book) } }, this, clock = { now })
        @Volatile var now = 1_000_000L

        override suspend fun cap(cap: Double?, by: String) {
            caps += cap to by
        }

        override suspend fun forget(p: FamilyParent) {
            forgot = p
        }

        override suspend fun check(k: FamilyKeyWaiting) = FamilyKey.KEPT
    }

    private fun parent() = Device(parentBook)
    private fun child() = Device(FamilyBook().joined(gift, bond))

    /** The device's last word reaches the office a moment after the visit is over. */
    private fun settled(done: () -> Boolean) {
        val until = System.currentTimeMillis() + 2_000
        while (!done() && System.currentTimeMillis() < until) Thread.sleep(5)
    }

    private fun <T> withOffice(p: Device, go: (Int) -> T): T {
        val loop = InetAddress.getLoopbackAddress()
        FamilyPost.Office(loop, p.courier).use { o ->
            thread { o.serve() }
            return go(o.port)
        }
    }

    @Test fun `over the Wi-Fi a limit reaches the device, and both know it arrived`() {
        val p = parent()
        val c = child()
        p.book = p.book.setCap("m1", 0.5)
        withOffice(p) { port ->
            assertTrue(c.courier.visit("127.0.0.1", port))
            settled { p.book.member("m1")?.pending == null }
        }
        assertEquals(listOf(0.5 to "Pixel"), c.caps)
        assertEquals(1L, c.book.parent!!.seen)
        val m = p.book.member("m1")!!
        assertNull(m.pending)
        assertEquals(0.5, m.capThere)
        assertNotNull(m.heard)
    }

    @Test fun `over the Wi-Fi a device with nothing waiting is seen, and goes`() {
        val p = parent()
        val c = child()
        withOffice(p) { port -> assertTrue(c.courier.visit("127.0.0.1", port)) }
        assertTrue(c.caps.isEmpty())
        assertNotNull(p.book.member("m1")!!.heard)
    }

    @Test fun `a removal reaches the device, it forgets, and it's off the list`() {
        val p = parent()
        val c = child()
        p.book = p.book.remove("m1")
        withOffice(p) { port ->
            assertTrue(c.courier.visit("127.0.0.1", port))
            settled { p.book.member("m1") == null }
        }
        assertEquals("Pixel", c.forgot?.name)
        assertNull(c.book.parent)
        assertNull(p.book.member("m1"))
    }

    @Test fun `a stranger on the Wi-Fi gets nothing and changes nothing`() {
        val p = parent()
        p.book = p.book.setCap("m1", 0.5)
        val before = p.book
        val stranger = FamilyPost.Keys(ByteArray(32) { 7 })
        withOffice(p) { port ->
            var got: FamilyNote? = null
            assertFalse("someone who knows the id but not the bond", FamilyPost.visit("127.0.0.1", port, "m1", stranger, 99) { got = it; it.seq })
            assertFalse("someone who isn't on the list", FamilyPost.visit("127.0.0.1", port, "nobody", keys, 99) { got = it; it.seq })
            assertNull(got)
        }
        assertEquals("no word of theirs settled the limit", before, p.book)
    }

    @Test fun `a word that isn't the device's doesn't take its place in the book`() {
        val p = parent()
        assertNull(p.courier.keys("nobody"))
        assertNull(p.courier.heard("nobody", 5))
        assertEquals(parentBook, p.book)
    }

    @Test fun `nothing listens, and the visit says so`() {
        val c = child()
        assertFalse(c.courier.visit("127.0.0.1", 1))
        assertEquals(0L, c.book.parent!!.seen)
    }

    @Test fun `a note played back on the wire does nothing a second time`() = runBlocking {
        val c = child()
        val n = FamilyNote(4, FamilyNote.BUDGET, 0.5)
        c.courier.take(n)
        c.courier.take(n)
        c.courier.take(FamilyNote(2, FamilyNote.FORGET))
        assertEquals(1, c.caps.size)
        assertNull(c.forgot)
        assertNotNull(c.book.parent)
    }

    @Test fun `a device with no family takes nothing`() = runBlocking {
        val c = Device(FamilyBook())
        c.courier.take(FamilyNote(1, FamilyNote.BUDGET, 0.5))
        assertTrue(c.caps.isEmpty())
    }

    // --- through the relay ------------------------------------------------------------------------------------------------

    private class Boxes : FamilyLink.Drop {
        val boxes = HashMap<String, MutableList<String>>()
        val everything = mutableListOf<String>()

        override fun put(box: String, note: String, ttlS: Int) {
            everything += note
            boxes.getOrPut(box) { mutableListOf() } += note
        }

        override fun take(box: String, waitS: Int) = boxes.remove(box).orEmpty()
    }

    @Test fun `through the relay a limit reaches the device and the parent hears, and the relay sees neither`() = runBlocking {
        val drop = Boxes()
        val p = parent()
        val c = child()
        p.book = p.book.setCap("m1", 0.5)
        p.courier.postRelay(drop)
        c.courier.collectRelay(drop)
        assertEquals(listOf(0.5 to "Pixel"), c.caps)
        p.courier.postRelay(drop)
        val m = p.book.member("m1")!!
        assertNull(m.pending)
        assertEquals(0.5, m.capThere)
        val all = drop.everything.joinToString()
        assertFalse("0.5" in all)
        assertFalse("budget" in all)
        assertFalse("m1" in all)
    }

    @Test fun `through the relay a note goes once, and again a day later if nothing came back`() {
        val drop = Boxes()
        val p = parent()
        p.book = p.book.setCap("m1", 0.5)
        p.courier.postRelay(drop)
        p.courier.postRelay(drop)
        assertEquals(1, drop.everything.size)
        p.now += FamilyPost.REPOST_MS
        p.courier.postRelay(drop)
        assertEquals(2, drop.everything.size)
    }

    @Test fun `through the relay a note that came twice counts once, a device says it's there, and not all the time`() = runBlocking {
        val drop = Boxes()
        val p = parent()
        val c = child()
        p.book = p.book.setCap("m1", 0.5)
        val k = FamilyPost.Keys(bond)
        repeat(2) { drop.put(k.boxDown, FamilyPost.sealNote(k, FamilyNote(1, FamilyNote.BUDGET, 0.5)), 60) }
        c.courier.collectRelay(drop)
        assertEquals(1, c.caps.size)
        val said = drop.everything.size
        c.courier.collectRelay(drop)
        assertEquals("nothing new, and it spoke a moment ago", said, drop.everything.size)
        c.now += FamilyPost.SAY_MS
        c.courier.collectRelay(drop)
        assertEquals(said + 1, drop.everything.size)
    }

    @Test fun `through the relay a removal goes through, and what's left of the bond is nothing`() = runBlocking {
        val drop = Boxes()
        val p = parent()
        val c = child()
        p.book = p.book.remove("m1")
        p.courier.postRelay(drop)
        c.courier.collectRelay(drop)
        assertNotNull(c.forgot)
        assertNull(c.book.parent)
        p.courier.postRelay(drop)
        assertNull(p.book.member("m1"))
    }
}
