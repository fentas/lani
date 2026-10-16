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

    @Test fun `what goes to the node is cleaned of tokens, keys, Bearer and query tokens`() {
        val token = "Zq8xPcL2vN0bR4tY7uW1mK3sE5hJ9dF6"
        // the device token and the key by their values; anything shaped like a key
        assertEquals("sent with ‹secret› and ‹secret›", Redact.all("sent with $token and $FAKE_KEY", listOf(token, FAKE_KEY)))
        assertEquals("a key sk-ant-‹…› in it", Redact.all("a key $FAKE_KEY in it"))
        // headers as a log line prints them
        assertEquals("Authorization: ‹…›", Redact.all("Authorization: Bearer abc.DEF-123_x"))
        assertEquals("x-api-key: ‹…› anthropic-version: 2023-06-01", Redact.all("x-api-key: $FAKE_KEY anthropic-version: 2023-06-01"))
        assertEquals("--header Bearer ‹…›", Redact.all("--header Bearer eyJhbGciOiJIUzI1NiJ9.e30.x"))
        // a URL's query tokens, keys, signatures and codes; its other parameters stay
        assertEquals("GET http://node:8790/x?token=‹…›&language=sl", Redact.all("GET http://node:8790/x?token=s3cr3tvalue&language=sl"))
        assertEquals("https://h/a?key=‹…›&sig=‹…›&n=2", Redact.all("https://h/a?key=k1&sig=s2&n=2"))
        assertEquals("access_token=‹…› device_token: ‹…›", Redact.all("access_token=abc device_token: def"))
        assertEquals("""{"token":"‹…›","api_key": "‹…›"}""", Redact.all("""{"token":"abc","api_key": "def"}"""))
        // what isn't a secret stays: counts of tokens, the words
        val usage = """{"input_tokens":12,"cache_creation_input_tokens":3100,"cache_creation":{"ephemeral_1h_input_tokens":3000},"output_tokens":95} the thread is long (21000 tokens)"""
        assertEquals(usage, Redact.all(usage))
        assertEquals("the device token was refused (401)", Redact.all("the device token was refused (401)"))
    }

    @Test fun `JSON for the node keeps its shape, strings cleaned, a secret key's string replaced, numbers kept`() {
        val o = tutorJson.parseToJsonElement(
            """{"token":"abc","settings":{"bridge_token":"def","maxTokens":64000},"usage":{"input_tokens":12},"events":[{"text":"Bearer qwertyuiop and $FAKE_KEY"}],"n":3}""",
        )
        val clean = Redact.json(o, listOf("abc-not-long"))
        assertEquals(
            """{"token":"‹…›","settings":{"bridge_token":"‹…›","maxTokens":64000},"usage":{"input_tokens":12},"events":[{"text":"Bearer ‹…› and sk-ant-‹…›"}],"n":3}""",
            clean.toString(),
        )
    }
}
