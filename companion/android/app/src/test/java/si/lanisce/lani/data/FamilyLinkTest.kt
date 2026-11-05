package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.FamilyLink.Fetched
import si.lanisce.lani.data.FamilyLink.Gift
import si.lanisce.lani.data.FamilyLink.Sender
import java.net.InetAddress
import kotlin.concurrent.thread

/** "Add a family member" (data/FamilyLink, docs/plans/05-built/first-minutes.md): what crosses, and that nothing else does. */
class FamilyLinkTest {
    private val key = "sk-ant-api03-this-is-the-family-key-0123456789"
    private val gift = Gift(provider = "anthropic", key = key, preset = "balanced", target = "it", base = "de")
    private val t0 = 1_000_000L

    private fun receiver(s: Sender, keys: java.security.KeyPair = FamilyLink.newKeys()): Triple<java.security.KeyPair, String, String> {
        val pub = FamilyLink.publicOf(keys)
        return Triple(keys, pub, FamilyLink.proof(s.offer.code, pub))
    }

    @Test fun `the QR holds no provider's key, only a code and a public key`() {
        val s = Sender("192.168.1.20", 41234, t0)
        val text = s.offer.text()
        assertFalse(key in text)
        assertFalse("sk-" in text)
        val parsed = FamilyLink.parse(text)
        assertEquals(s.offer, parsed)
        // nothing a key could hide in: exactly these fields
        assertEquals(setOf("v", "h", "p", "c", "k", "e"), text.substringAfter('?').split('&').map { it.substringBefore('=') }.toSet())
    }

    @Test fun `with a relay the QR names it and still holds no key`() {
        val s = Sender("192.168.1.20", 41234, t0, relay = "https://relay.example")
        val text = s.offer.text()
        assertEquals(setOf("v", "h", "p", "c", "k", "e", "r"), text.substringAfter('?').split('&').map { it.substringBefore('=') }.toSet())
        assertEquals(s.offer, FamilyLink.parse(text))
        assertFalse(key in text)
        assertFalse("sk-" in text)
    }

    @Test fun `only the relay reaches a device with no Wi-Fi, and an offer goes nowhere without one or the other`() {
        val alone = Sender("", 0, t0, relay = "https://relay.example").offer
        assertEquals(alone, FamilyLink.parse(alone.text()))
        assertNull("nowhere to go", FamilyLink.parse(Sender("", 0, t0).offer.text()))
        val good = Sender("h", 1, t0, relay = "https://relay.example").offer.text()
        assertNull("a relay's address with a password isn't one", FamilyLink.parse(good.replace("relay.example", "a%3Ab%40relay.example"))?.relay)
        assertNull("nor is anything but http", FamilyLink.parse(good.replace("https", "file"))?.relay)
        assertEquals("an address on the Wi-Fi still works without it", "h", FamilyLink.parse(good.replace("https", "file"))?.host)
    }

    @Test fun `only our own text is an offer`() {
        assertNull(FamilyLink.parse(null))
        assertNull(FamilyLink.parse("https://example.com"))
        assertNull(FamilyLink.parse("lani://pair?code=abc"))
        val good = Sender("h", 1, t0).offer.text()
        assertNull("another version", FamilyLink.parse(good.replace("v=1", "v=2")))
        assertNull("no code", FamilyLink.parse(good.replace(Regex("c=[^&]*"), "c=")))
        assertNull("a bad port", FamilyLink.parse(good.replace("p=1", "p=70000")))
    }

    @Test fun `the gift reaches the device that claimed the code, and only that device can read it`() {
        val s = Sender("h", 1, t0)
        val (keys, pub, proof) = receiver(s)
        assertTrue(s.claim(pub, proof, "Teo's tablet", t0 + 1))
        assertEquals("Teo's tablet", s.device)
        val sealed = s.confirm(gift, t0 + 2)!!
        assertFalse("the sealed text doesn't carry the key", key in sealed)
        val session = FamilyLink.sessionKey(keys, s.offer.publicKey, s.offer.code)!!
        assertEquals(gift, Gift.parse(FamilyLink.open(session, sealed)!!.decodeToString()))
        assertTrue("both keep the same bond, and it never crossed", s.bond.contentEquals(FamilyLink.bondKey(session)))
        // someone with the QR's photo and keys of their own can't open what went by
        val eve = FamilyLink.sessionKey(FamilyLink.newKeys(), s.offer.publicKey, s.offer.code)!!
        assertNull(FamilyLink.open(eve, sealed))
    }

    @Test fun `the key exchange is bound to the code`() {
        val a = FamilyLink.newKeys()
        val b = FamilyLink.newKeys()
        val one = FamilyLink.newCode()
        val two = FamilyLink.newCode()
        assertTrue(FamilyLink.sessionKey(a, FamilyLink.publicOf(b), one).contentEquals(FamilyLink.sessionKey(b, FamilyLink.publicOf(a), one)))
        assertFalse(FamilyLink.sessionKey(a, FamilyLink.publicOf(b), one).contentEquals(FamilyLink.sessionKey(a, FamilyLink.publicOf(b), two)))
        assertNotEquals(one, two)
    }

    @Test fun `a code is used once, a second scan gets nothing`() {
        val s = Sender("h", 1, t0)
        val (_, pub, proof) = receiver(s)
        assertTrue(s.claim(pub, proof, "Teo's tablet", t0))
        val (_, pub2, proof2) = receiver(s)
        assertFalse(s.claim(pub2, proof2, "someone else", t0 + 1))
        assertEquals("Teo's tablet", s.device)
        assertNotNull(s.confirm(gift, t0 + 2))
        assertNull("not sealed twice", s.confirm(gift, t0 + 3))
        assertFalse("nor claimed after", s.claim(pub2, proof2, "someone else", t0 + 4))
    }

    @Test fun `an expired code gets nothing, before or after its claim`() {
        val s = Sender("h", 1, t0)
        val (_, pub, proof) = receiver(s)
        assertFalse(s.claim(pub, proof, "late", t0 + FamilyLink.TTL_MS))
        assertEquals(Sender.State.EXPIRED, s.state)
        val c = Sender("h", 1, t0)
        val (_, pub2, proof2) = receiver(c)
        assertTrue(c.claim(pub2, proof2, "Teo's tablet", t0 + 1))
        assertNull("the parent came too late", c.confirm(gift, t0 + FamilyLink.TTL_MS))
    }

    @Test fun `a wrong proof gets nothing and doesn't spend the code`() {
        val s = Sender("h", 1, t0)
        val (_, pub, proof) = receiver(s)
        assertFalse(s.claim(pub, FamilyLink.proof(FamilyLink.newCode(), pub), "guess", t0))
        assertFalse("a proof is for its public key", s.claim(FamilyLink.publicOf(FamilyLink.newKeys()), proof, "guess", t0))
        assertTrue(s.claim(pub, proof, "Teo's tablet", t0))
    }

    @Test fun `nothing goes without the parent, and a refusal ends it`() {
        val s = Sender("h", 1, t0)
        assertNull("nobody claimed it", s.confirm(gift, t0))
        val (_, pub, proof) = receiver(s)
        assertTrue(s.claim(pub, proof, "Teo's tablet", t0))
        s.refuse()
        assertNull(s.confirm(gift, t0 + 1))
    }

    @Test fun `a byte changed on the way isn't opened`() {
        val k = FamilyLink.sessionKey(FamilyLink.newKeys(), FamilyLink.publicOf(FamilyLink.newKeys()), FamilyLink.newCode())!!
        val sealed = FamilyLink.seal(k, "hello".toByteArray())
        assertEquals("hello", FamilyLink.open(k, sealed)?.decodeToString())
        val flipped = sealed.dropLast(2) + (if (sealed[sealed.length - 2] == 'A') "B" else "A") + sealed.last()
        assertNull(FamilyLink.open(k, flipped))
    }

    @Test fun `a gift printed by accident doesn't show the key`() {
        assertFalse(key in gift.toString())
    }

    @Test fun `a gift names who the device is in the sender's list, and an older sender names nobody`() {
        val named = gift.copy(family = "fam", member = "mem", from = "Pixel")
        assertEquals(named, Gift.parse(named.json()))
        assertEquals(gift, Gift.parse(gift.json()))
    }

    @Test fun `a refusal and an expiry leave no bond`() {
        val s = Sender("h", 1, t0)
        val (_, pub, proof) = receiver(s)
        assertTrue(s.claim(pub, proof, "Teo's tablet", t0))
        s.refuse()
        assertNull(s.confirm(gift, t0 + 1))
        assertNull(s.bond)
    }

    @Test fun `what could reach a log carries neither the key nor the code`() {
        val text = Sender("h", 1, t0).offer.text()
        val line = si.lanisce.lani.tutor.Redact.of("scanned $text with $key")
        assertFalse(key in line)
        assertFalse(text in line)
        assertFalse("c=" in line)
    }

    // --- over a real socket, on this machine -------------------------------------------------------------------------

    @Test fun `end to end, the claim, the parent says yes, the gift, then a second scan is refused`() {
        val loop = InetAddress.getLoopbackAddress()
        FamilyLink.Listener(loop).use { l ->
            val s = Sender(loop.hostAddress!!, l.port, System.currentTimeMillis())
            val serving = thread { l.serve(s) { l.send(s.confirm(gift, System.currentTimeMillis())) } }
            val got = FamilyLink.fetch(s.offer, "Teo's tablet")
            assertEquals(gift, (got as Fetched.Got).gift)
            serving.join(5_000)
            assertEquals(Sender.State.SENT, s.state)
            assertTrue("the two keep one bond", got.bond.contentEquals(s.bond))
            // the same QR again: the exchange is over
            l.close()
            assertEquals(Fetched.Unreachable, FamilyLink.fetch(s.offer, "someone else", connectMs = 500))
        }
    }

    @Test fun `end to end, the parent says no and the device gets nothing`() {
        val loop = InetAddress.getLoopbackAddress()
        FamilyLink.Listener(loop).use { l ->
            val s = Sender(loop.hostAddress!!, l.port, System.currentTimeMillis())
            val claimed = java.util.concurrent.CountDownLatch(1)
            thread { l.serve(s) { claimed.countDown() } }
            var first: Fetched? = null
            val t = thread { first = FamilyLink.fetch(s.offer, "Teo's tablet", waitMs = 5_000) }
            assertTrue(claimed.await(5, java.util.concurrent.TimeUnit.SECONDS))
            s.refuse()
            l.send(null)
            t.join(5_000)
            assertEquals(Fetched.Refused, first)
        }
    }

    @Test fun `an expired offer isn't even tried`() {
        val s = Sender("127.0.0.1", 1, t0)
        assertEquals(Fetched.Refused, FamilyLink.fetch(s.offer, "late", now = t0 + FamilyLink.TTL_MS))
    }

    // --- through a relay: what it sees, and that the rules are the same ---------------------------------------------------

    /** A relay on this machine that keeps every byte it was given, as an honest but curious one could. */
    private class SeenDrop : FamilyLink.Drop {
        val boxes = HashMap<String, MutableList<String>>()
        val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
        var down = false

        override fun put(box: String, note: String, ttlS: Int) {
            if (down) throw java.io.IOException("no relay")
            seen += box
            seen += note
            synchronized(boxes) { boxes.getOrPut(box) { mutableListOf() } += note }
        }

        override fun take(box: String, waitS: Int): List<String> {
            if (down) throw java.io.IOException("no relay")
            val until = System.currentTimeMillis() + (if (waitS > 0) 150 else 0)
            do {
                synchronized(boxes) { boxes.remove(box) }?.let { return it }
                Thread.sleep(5)
            } while (System.currentTimeMillis() < until)
            return emptyList()
        }
    }

    private fun relaySender(now: Long = System.currentTimeMillis()) = Sender("", 0, now, relay = "http://relay.test")

    @Test fun `through a relay the gift comes, and the relay never sees a key, a name, a public key or the code`() {
        val drop = SeenDrop()
        val s = relaySender()
        val way = FamilyLink.RelayWay(drop, s.offer)
        val serving = thread { way.serve(s) { way.send(s.confirm(gift, System.currentTimeMillis())) } }
        val keys = FamilyLink.newKeys()
        val got = FamilyLink.fetch(s.offer, "Teo's tablet", waitMs = 5_000, keys = keys, drop = drop)
        serving.join(5_000)
        assertEquals(gift, (got as Fetched.Got).gift)
        assertTrue(got.bond.contentEquals(s.bond))
        val all = drop.seen.joinToString("\n")
        for (secret in listOf(key, "Teo's tablet", s.offer.code, FamilyLink.publicOf(keys), FamilyLink.proof(s.offer.code, FamilyLink.publicOf(keys)), "anthropic", "preset")) {
            assertFalse("the relay saw $secret", secret in all)
        }
        // nor is it in the box's name, and the sealed notes open for neither a stranger nor the relay
        val session = FamilyLink.sessionKey(FamilyLink.newKeys(), s.offer.publicKey, s.offer.code)!!
        assertTrue(drop.seen.none { FamilyLink.open(session, it) != null })
    }

    @Test fun `through a relay a code still works once, and the second device is told`() {
        val drop = SeenDrop()
        val s = relaySender()
        val way = FamilyLink.RelayWay(drop, s.offer)
        val serving = thread { way.serve(s) { way.send(s.confirm(gift, System.currentTimeMillis())) } }
        assertTrue(FamilyLink.fetch(s.offer, "Teo's tablet", waitMs = 5_000, drop = drop) is Fetched.Got)
        serving.join(5_000)
        assertEquals("nothing answers a used code", Fetched.Refused, FamilyLink.fetch(s.offer, "someone else", waitMs = 300, drop = drop))
    }

    @Test fun `through a relay a second device that claims while the first waits is told no`() {
        val drop = SeenDrop()
        val s = relaySender()
        val way = FamilyLink.RelayWay(drop, s.offer)
        val mine = FamilyLink.newKeys()
        val pub = FamilyLink.publicOf(mine)
        assertTrue(s.claim(pub, FamilyLink.proof(s.offer.code, pub), "Teo's tablet", System.currentTimeMillis()))
        // what's in the box when the first claim was already taken, as two claims in one poll are
        val late = thread { assertEquals(Fetched.Refused, FamilyLink.fetch(s.offer, "someone else", waitMs = 1_000, drop = drop)) }
        Thread.sleep(50)
        val serving = thread { way.serve(s) { } }
        late.join(2_000)
        serving.join(1_000)
        assertEquals(Sender.State.CLAIMED, s.state)
        assertEquals("Teo's tablet", s.device)
    }

    @Test fun `through a relay a wrong proof gets nothing and doesn't spend the code`() {
        val drop = SeenDrop()
        val s = relaySender()
        val way = FamilyLink.RelayWay(drop, s.offer)
        val claimed = java.util.concurrent.CountDownLatch(1)
        val serving = thread { way.serve(s) { claimed.countDown() } }
        // a note that isn't sealed under the code's key, then a claim made with another code: neither is a claim
        drop.put(FamilyLink.claimBox(s.offer.code), "not sealed", 60)
        val other = Sender("", 0, System.currentTimeMillis(), relay = "http://relay.test", code = FamilyLink.newCode())
        assertEquals(Fetched.Refused, FamilyLink.fetch(other.offer, "guess", waitMs = 300, drop = drop))
        assertEquals(Sender.State.WAITING, s.state)
        val got = arrayOfNulls<Fetched>(1)
        val t = thread { got[0] = FamilyLink.fetch(s.offer, "Teo's tablet", waitMs = 5_000, drop = drop) }
        assertTrue(claimed.await(5, java.util.concurrent.TimeUnit.SECONDS))
        way.send(s.confirm(gift, System.currentTimeMillis()))
        t.join(5_000)
        serving.join(1_000)
        assertTrue(got[0] is Fetched.Got)
    }

    @Test fun `through a relay the parent says no and the device gets nothing`() {
        val drop = SeenDrop()
        val s = relaySender()
        val way = FamilyLink.RelayWay(drop, s.offer)
        val claimed = java.util.concurrent.CountDownLatch(1)
        thread { way.serve(s) { claimed.countDown() } }
        val got = arrayOfNulls<Fetched>(1)
        val t = thread { got[0] = FamilyLink.fetch(s.offer, "Teo's tablet", waitMs = 5_000, drop = drop) }
        assertTrue(claimed.await(5, java.util.concurrent.TimeUnit.SECONDS))
        s.refuse()
        way.send(null)
        t.join(5_000)
        assertEquals(Fetched.Refused, got[0])
        assertNull(s.bond)
    }

    @Test fun `the Wi-Fi goes first, the relay only when nothing answers there`() {
        val drop = SeenDrop()
        val loop = InetAddress.getLoopbackAddress()
        FamilyLink.Listener(loop).use { l ->
            val s = Sender(loop.hostAddress!!, l.port, System.currentTimeMillis(), relay = "http://relay.test")
            val serving = thread { l.serve(s) { l.send(s.confirm(gift, System.currentTimeMillis())) } }
            assertTrue(FamilyLink.fetch(s.offer, "Teo's tablet", drop = drop) is Fetched.Got)
            serving.join(5_000)
            assertTrue("nothing went through the relay", drop.seen.isEmpty())
        }
        val s = Sender("192.0.2.1", 9, System.currentTimeMillis(), relay = "http://relay.test")
        val way = FamilyLink.RelayWay(drop, s.offer)
        val serving = thread { way.serve(s) { way.send(s.confirm(gift, System.currentTimeMillis())) } }
        assertTrue(FamilyLink.fetch(s.offer, "Teo's tablet", waitMs = 5_000, connectMs = 200, drop = drop) is Fetched.Got)
        serving.join(5_000)
    }

    @Test fun `no relay in reach is an answer of its own, and the code isn't spent`() {
        val drop = SeenDrop().apply { down = true }
        val s = relaySender()
        assertEquals(Fetched.Unreachable, FamilyLink.fetch(s.offer, "Teo's tablet", drop = drop))
        assertEquals(Sender.State.WAITING, s.state)
        assertEquals("no relay given: the Wi-Fi is all there is", Fetched.Unreachable, FamilyLink.fetch(s.offer, "Teo's tablet"))
    }
}
