package si.lanisce.lani.spike.validators

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The conformance corpus in QuickJS on the host's JVM (quickjs-kt's JVM build, the same QuickJS as on the phone): what a
 * unit test of the app could check without a phone. Reads the bundle and the corpus from companion/bridge (build them
 * first); writes build/results-quickjs-jvm.json.
 *
 * Run from companion/android: ./gradlew -p spike-validators testNoneDebugUnitTest
 */
class QuickJsJvmTest {
    private val bridge = File(System.getProperty("user.dir"), "../../bridge").canonicalFile
    private val dist = File(bridge, "validators/dist")
    private val corpus = File(bridge, "test/conformance/out")
    private fun file(name: String) = if (name.startsWith("validators")) File(dist, name) else File(corpus, name)

    @Test
    fun corpus() {
        val harness = Harness(
            read = { file(it).readText() },
            names = { dir -> File(corpus, dir).list().orEmpty().toList() },
            lines = { each -> File(corpus, "cases.jsonl").bufferedReader().useLines { it.forEach(each) } },
        )
        val session = harness.session({ Quick() })
        val result = harness.run({ Quick() }) { e, out -> Quick.bytecode(e as Quick, file("validators-lexicon.js").readText(), out) }
        File(System.getProperty("user.dir"), "build/results-quickjs-jvm.json").writeText(kotlinx.serialization.json.JsonObject(result + ("session" to session)).toString())
        println("quickjs-jvm: ${result["cases"]} cases, ${result["mismatches"]} mismatches")
        result["mismatch_list"]?.let { println(it.toString().take(4000)) }
        assertEquals("mismatches", "0", result["mismatches"].toString())
    }
}
