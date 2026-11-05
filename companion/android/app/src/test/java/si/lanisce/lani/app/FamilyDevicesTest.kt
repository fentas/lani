package si.lanisce.lani.app

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.data.FamilyBook
import si.lanisce.lani.data.FamilyKeyWaiting
import si.lanisce.lani.data.FamilyLink
import si.lanisce.lani.data.FamilyParent
import si.lanisce.lani.tutor.KeyVault
import si.lanisce.lani.tutor.MemoryStore
import si.lanisce.lani.tutor.SoftCipher
import si.lanisce.lani.tutor.live.FamilyKey

/** The family's devices on one device (app/FamilyDevices): what a child's can't do, what's kept, a key that waits for its check. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FamilyDevicesTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bond = ByteArray(32) { 1 }
    private val key = "sk-ant-api03-family-key-0123456789"
    private var child = false
    private var online = true
    private var answer = FamilyKey.NOT_CHECKED
    private val checked = mutableListOf<FamilyKeyWaiting>()
    @Volatile private var forgot: FamilyParent? = null
    private val vault = KeyVault(SoftCipher(), MemoryStore())

    private val home = object : FamilyHome {
        override suspend fun cap(cap: Double?, by: String) = Unit
        override suspend fun forget(p: FamilyParent) {
            forgot = p
        }

        override suspend fun check(k: FamilyKeyWaiting): FamilyKey {
            checked += k
            return answer
        }
    }

    private fun devices() = FamilyDevices(scope, ApplicationProvider.getApplicationContext(), vault, home, { child }, { "" }, { online }).also { runBlocking { it.ready() } }

    @After fun stop() = scope.cancel()

    private fun until(what: String, ok: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!ok()) delay(10) }
        assertTrue(what, ok())
    }

    private fun withMember(d: FamilyDevices): String {
        d.added("fam", "m1", "Teo's tablet", bond)
        return "m1"
    }

    @Test fun `a parent sets a limit and removes a device`() {
        val d = devices()
        val id = withMember(d)
        d.setCap(id, 0.5)
        assertEquals(0.5, d.book.member(id)!!.cap)
        d.remove(id)
        assertTrue(d.book.member(id)!!.leaving)
        d.drop(id)
        assertNull(d.book.member(id))
    }

    @Test fun `a child's device changes nothing, whatever calls it`() {
        val d = devices()
        val id = withMember(d)
        val before = d.book
        child = true
        d.setCap(id, 10.0)
        d.remove(id)
        d.drop(id)
        assertEquals(before, d.book)
    }

    @Test fun `what it keeps is sealed in the vault, and comes back`() {
        val d = devices()
        withMember(d)
        d.setCap("m1", 0.5)
        until("saved") { runBlocking { vault.load()?.let { FamilyBook.parse(it).member("m1")?.cap } == 0.5 } }
        val again = devices()
        until("read again") { again.book.member("m1") != null }
        assertEquals(0.5, again.book.member("m1")!!.cap)
    }

    @Test fun `a device can leave by itself, if the one that set its limit is gone`() {
        val d = devices()
        d.joined(FamilyLink.Gift(key = key, provider = "anthropic", family = "fam", member = "m1", from = "Pixel"), bond)
        d.leave()
        until("gone") { d.book.parent == null }
        assertEquals("Pixel", forgot?.name)
    }

    @Test fun `a family id is made once`() {
        val d = devices()
        runBlocking { assertEquals(d.familyId(), d.familyId()) }
    }

    @Test fun `what changes before the book is read stays, on top of what was kept`() = runBlocking {
        vault.save(FamilyBook(family = "fam").text())
        val d = FamilyDevices(scope, ApplicationProvider.getApplicationContext(), vault, home, { child }, { "" }, { online })
        d.added("fam", "m1", "Teo's tablet", bond)
        d.ready()
        assertEquals("fam", d.book.family)
        assertNotNull(d.book.member("m1"))
    }

    @Test fun `what came from an older sender makes no bond, a gift with one does`() {
        val d = devices()
        d.joined(FamilyLink.Gift(key = key), null)
        d.joined(FamilyLink.Gift(key = key), bond)
        assertNull(d.book.parent)
        d.joined(FamilyLink.Gift(key = key, family = "fam", member = "m1", from = "Pixel"), bond)
        assertEquals("Pixel", d.book.parent?.name)
    }

    @Test fun `a key that couldn't be checked waits, is tried when there's a network, and goes once it's settled`() {
        online = false
        val d = devices()
        d.keyWaits(FamilyLink.Gift(provider = "anthropic", key = key, preset = "BALANCED"))
        assertNotNull(d.book.keyWaiting)
        d.foreground(true)
        runBlocking { delay(200) }
        assertTrue("no network: nothing tried", checked.isEmpty())
        d.foreground(false)
        online = true
        answer = FamilyKey.NOT_CHECKED
        d.foreground(true)
        until("tried") { checked.isNotEmpty() }
        d.foreground(false)
        assertNotNull("still nothing known of it", d.book.keyWaiting)
        answer = FamilyKey.KEPT
        d.foreground(true)
        until("settled") { d.book.keyWaiting == null }
        d.foreground(false)
        assertFalse(key in d.book.text())
    }
}
