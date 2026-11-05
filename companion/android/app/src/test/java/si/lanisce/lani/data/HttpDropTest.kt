package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.Base64

/** The relay's answer (companion/relay/src/drop.ts), as the app reads it. */
class HttpDropTest {
    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())

    @Test fun `what waits comes back as it went`() {
        assertEquals(listOf("one", "two"), HttpDrop.parseItems("""{"items":["${b64("one")}","${b64("two")}"]}"""))
        assertEquals(emptyList<String>(), HttpDrop.parseItems("""{"items":[]}"""))
    }

    @Test fun `an answer that isn't the relay's is a relay out of reach, not a note`() {
        for (raw in listOf("<html>", "{}", """{"items":["***"]}""")) {
            try {
                HttpDrop.parseItems(raw)
                fail(raw)
            } catch (_: IOException) {
            }
        }
    }
}
