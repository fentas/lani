package si.lanisce.lani.hosted

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.phone.HostFs
import si.lanisce.lani.phone.PhoneBridge

/**
 * The hosted mode's boots as the app goes through them (hosted/HostedRuntime): the onboarding's first (the learner's
 * data made, the bundle compiled and kept as bytecode, no village yet), then the app started again over the same data
 * with a village, from the bytecode. With a village the stories' daily check resolved every story at the start (a
 * second of QuickJS before the first screen): the phone's environment sets it a while after the boot.
 */
class HostedBootTest {
    private val repo = File(System.getProperty("lani.repo") ?: "../../..").canonicalFile
    private val bundle = File(System.getProperty("lani.phoneBundle") ?: "build/generated/phoneBridge/phone/bridge-phone.js")
    private val scale = System.getProperty("lani.perfScale")?.toDoubleOrNull() ?: 1.0

    private fun bridge(home: File, bytecode: File?, env: Map<String, String>) = PhoneBridge(
        PhoneBridge.Config(
            projectDir = repo, dataDir = File(home, "data"), home = home,
            roots = listOf(HostFs.Root(home, writable = true), HostFs.Root(repo, writable = false)),
            bundle = { bundle.readText() },
            env = mapOf("LANI_RHYTHM" to "off", "LANI_VARIANTS" to "off") + env,
            bytecodeDir = bytecode,
        ),
    )

    @Test
    fun aBootOverALearnersVillageIsQuick() = runBlocking {
        val home = Files.createTempDirectory("lani-boot").toFile()
        try {
            val cache = File(home, "cache/bytecode")
            // the phone's own environment (the lease's phone, the stories' check after the boot), no relay
            val phone = HostedRuntime.phoneEnvironment(home, buildRelay = "")
            val first = bridge(home, cache, phone)
            first.initLearner("""{"name":"Ana","gender":"female","target":"sl","base":"en"}""")
            val b1 = first.start()
            first.handle(PhoneBridge.Request.json("PUT", "/game", """{"rev":0,"state":{"village":"Testna vas"}}"""))
            first.stop()
            assertFalse(b1.fromBytecode)
            val boots = (1..3).map { bridge(home, cache, phone).let { b -> b.start().also { b.stop() } } }
            assertTrue(boots.all { it.fromBytecode })
            // the node's way: the stories' check in the start
            val atOnce = bridge(home, cache, phone - "LANI_STORIES_CHECK_DELAY_MS").let { b -> b.start().also { b.stop() } }
            val best = boots.minOf { it.bootMs }
            println("boot: the onboarding's ${"%.0f".format(b1.bootMs)} ms (compiled ${"%.0f".format(b1.loadMs)}); again from bytecode ${boots.joinToString { "%.0f+%.0f".format(it.loadMs, it.bootMs) }} ms; with the stories' check in it ${"%.0f".format(atOnce.bootMs)} ms")
            assertTrue("a boot over a village took $best ms", best < 600 * scale)
        } finally {
            home.deleteRecursively()
        }
    }
}
