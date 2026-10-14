package si.lanisce.lani.tutor

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import si.lanisce.lani.data.BridgeConfig

/**
 * The shadow's guards: its bridge client sends GETs only; the key goes only to the Messages API (or a debug build's
 * mock), stored encrypted, never in a log line or the export.
 */
class GuardsTest {
    private val bridgeAnswers = Answering { Triple(200, "{}", "application/json") }
    private val bridge = ShadowBridge(BridgeConfig("http://10.0.2.2:8791", "bridge-token"), OkHttpClient.Builder().addInterceptor(bridgeAnswers).build())

    @Test fun `the shadow's bridge client refuses anything but a GET`() = runBlocking {
        for (method in listOf("POST", "PUT", "DELETE", "PATCH")) {
            try {
                bridge.send(method, "/message", "{\"kind\":\"chat\",\"text\":\"x\"}")
                fail("$method went out")
            } catch (e: ShadowWriteRefused) {
                assertTrue(e.message!!.contains(method))
            }
        }
        assertTrue("nothing reached the bridge", bridgeAnswers.requests.isEmpty())
        assertEquals(200, bridge.get("/state").first)
        assertEquals(listOf("GET"), bridgeAnswers.requests.map { it.method })
        assertEquals("Bearer bridge-token", bridgeAnswers.requests.single().header("Authorization"))
        assertNull(bridgeAnswers.requests.single().header("x-api-key"))
    }

    @Test fun `the key goes only to the Messages API, or a debug build's mock`() {
        assertTrue(KeyGuard.mayCarryKey("https://api.anthropic.com/v1/messages".toHttpUrl(), KeyGuard.API))
        assertFalse(KeyGuard.mayCarryKey("http://api.anthropic.com/v1/messages".toHttpUrl(), KeyGuard.API))
        assertFalse(KeyGuard.mayCarryKey("https://10.0.2.2:8791/state".toHttpUrl(), KeyGuard.API))
        assertTrue(KeyGuard.mayCarryKey("http://10.0.2.2:8771/v1/messages".toHttpUrl(), "http://10.0.2.2:8771"))
        assertFalse(KeyGuard.mayCarryKey("http://10.0.2.2:8791/message".toHttpUrl(), "http://10.0.2.2:8771"))
        // a release build ignores the base URL setting
        assertEquals(KeyGuard.API, KeyGuard.baseUrl(debug = false, override = "http://10.0.2.2:8771"))
        assertEquals("http://10.0.2.2:8771", KeyGuard.baseUrl(debug = true, override = "http://10.0.2.2:8771/"))
    }

    @Test fun `the transport puts the key in x-api-key, to the API only`() = runBlocking {
        val api = Answering { Triple(200, """{"data":[{"id":"claude-haiku-5-5"}]}""", "application/json") }
        AnthropicTransport(KeyGuard.API, { FAKE_KEY }, OkHttpClient.Builder().addInterceptor(api).build()).models()
        val r = api.requests.single()
        assertEquals("api.anthropic.com", r.url.host)
        assertEquals(FAKE_KEY, r.header("x-api-key"))
        assertEquals("2023-06-01", r.header("anthropic-version"))
        assertFalse(r.url.toString().contains(FAKE_KEY))
        assertNull(r.header("Authorization"))
    }

    @Test fun `the key is kept encrypted and can be removed`() = runBlocking {
        val store = MemoryStore()
        val vault = KeyVault(SoftCipher(), store)
        vault.save(" $FAKE_KEY\n")
        assertFalse(store.value!!.contains("SHADOWTESTKEY"))
        assertNotEquals(FAKE_KEY, store.value)
        assertEquals(FAKE_KEY, vault.load())
        vault.remove()
        assertNull(vault.load())
        assertFalse(vault.has())
    }

    @Test fun `anything shaped like a key is taken out of a log line`() {
        assertEquals("key sk-ant-‹…› refused", Redact.of("key $FAKE_KEY refused"))
        assertEquals("‹key›!", Redact.of("abcdefgh123!", "abcdefgh123"))
    }
}
