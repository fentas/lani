package si.lanisce.lani.tutor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the phone is, for the Gemini API's free tier: Google allows only the paid services for an app's users in the
 * EEA, Switzerland and the UK, so there the free tier isn't offered, and a key marked free is treated as paid.
 */
class RegionTest {
    @Test fun `the country - the mobile network's, else the SIM's, else the locale's`() {
        assertEquals("SI", Region.country("si", "at", "DE"))
        assertEquals("AT", Region.country("", "at", "DE"))
        assertEquals("DE", Region.country(null, " ", "DE"))
        assertEquals("US", Region.country(null, null, "us"))
        assertNull(Region.country(null, "", ""))
        assertNull("not a country code", Region.country("310", null, null))
    }

    @Test fun `the EEA, Switzerland and the UK - paid only, elsewhere the free tier stays`() {
        assertEquals(30, Region.EEA.size)
        for (c in listOf("SI", "AT", "IT", "DE", "HR", "GR", "IE", "NO", "IS", "LI", "CH", "GB", "UK", "si")) assertTrue(c, Region.geminiPaidOnly(c))
        for (c in listOf("US", "RS", "BA", "TR", "UA", "JP", "BR", "EL")) assertFalse(c, Region.geminiPaidOnly(c))
        assertFalse("not known: as before", Region.geminiPaidOnly(null))
        assertEquals("the EEA: the EU's 27 and three", 27, (Region.EEA - setOf("IS", "LI", "NO")).size)
    }

    @Test fun `a key marked free is treated as paid where only paid is allowed, its privacy note kept`() {
        assertTrue(GeminiTerms.freeCalls(GeminiTerms.FREE, paidOnly = false))
        assertTrue(GeminiTerms.freeCalls(GeminiTerms.FREE_SEEN, paidOnly = false))
        assertFalse(GeminiTerms.freeCalls(GeminiTerms.FREE, paidOnly = true))
        assertFalse(GeminiTerms.freeCalls(GeminiTerms.PAID, paidOnly = false))
        // the note follows the marking, not the pricing
        assertTrue(GeminiTerms.isFree(GeminiTerms.FREE))
    }

    @Test fun `the credentials - billed and paced as paid in the EEA, as free elsewhere`() = runBlocking {
        var paidOnly = true
        val c = GeminiCredentials(KeyVault(SoftCipher(), MemoryStore()), MemoryStore(), GeminiPacer(sleep = {}), paidOnly = { paidOnly })
        c.setTier(GeminiTerms.FREE)
        assertEquals(GeminiTerms.FREE, c.tier())
        assertEquals(Billing.API_KEY, c.billing())
        assertFalse(c.pacer.free)
        paidOnly = false
        assertEquals(Billing.FREE_TIER, c.billing())
        assertTrue(c.pacer.free)
    }
}
