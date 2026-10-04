package si.lanisce.lani.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair
import java.io.File

/**
 * The Primorska village reads exactly as before the culture pack: every text [CultureProbes] makes, for sl · en, is the one in
 * the fixture (src/test/resources/culture/parity.json), which the same probes wrote from the code before the content
 * moved into companion/cultures/primorska. What the probes make now is kept in build/reports/culture/probes.json, to
 * compare by hand.
 */
class CultureParityTest {
    @Test
    fun `every quest, festival, surprise, good, tool, project, event, name and chronicle line reads as before`() {
        val pair = L10n.pair
        L10n.pair = LangPair.DEFAULT
        val now = try {
            CultureProbes.all()
        } finally {
            L10n.pair = pair
        }
        File("build/reports/culture").apply { mkdirs() }.resolve("probes.json")
            .writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), JsonObject(now.mapValues { JsonPrimitive(it.value) })) + "\n")
        val raw = javaClass.getResourceAsStream("/culture/parity.json")?.use { it.readBytes().decodeToString() }
        checkNotNull(raw) { "the parity fixture (src/test/resources/culture/parity.json)" }
        val was = Json.parseToJsonElement(raw).jsonObject.getValue("probes").jsonObject.mapValues { (it.value as JsonPrimitive).content }
        val wrong = (was.keys + now.keys).distinct().mapNotNull { k ->
            val a = was[k]
            val b = now[k]
            if (a == b) null else "$k:\n  was «$a»\n  now «$b»"
        }
        assertTrue("${wrong.size} of ${was.size} probes differ:\n" + wrong.take(60).joinToString("\n"), wrong.isEmpty())
    }
}
