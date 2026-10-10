package si.lanisce.lani.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class OfflineVoicesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private val model = "fake onnx".toByteArray()
    private val tokens = "  3\na 4\n".toByteArray()
    private val sl = "sl dict".toByteArray()

    private fun info(modelSha: String = sha(model)) = OfflineVoiceInfo(
        language = "sl", id = "sl_SI-artur-medium", name = "Artur", status = "ready", bytes = (model.size + tokens.size + sl.size).toLong(),
        files = listOf(
            OfflineVoiceFile("model.onnx", model.size.toLong(), modelSha),
            OfflineVoiceFile("tokens.txt", tokens.size.toLong(), sha(tokens)),
            OfflineVoiceFile("espeak-ng-data/sl_dict", sl.size.toLong(), sha(sl)),
        ),
    )

    private val bodies = mapOf("model.onnx" to model, "tokens.txt" to tokens, "espeak-ng-data/sl_dict" to sl)

    @Test fun `a file is checked by its size and SHA-256`() {
        val f = tmp.newFile("x").apply { writeBytes(model) }
        assertTrue(OfflineVoices.check(f, model.size.toLong(), sha(model)))
        assertTrue(OfflineVoices.check(f, model.size.toLong(), sha(model).uppercase()))
        assertFalse(OfflineVoices.check(f, model.size.toLong(), sha(tokens)))
        assertFalse(OfflineVoices.check(f, model.size + 1L, sha(model)))
        assertFalse(OfflineVoices.check(File(tmp.root, "none"), 0, sha(ByteArray(0))))
    }

    @Test fun `every file checked, then the voice is in place whole`() = runBlocking {
        val v = OfflineVoices(tmp.newFolder("files"))
        val fetched = mutableListOf<String>()
        val got = v.install(info()) { f, to -> fetched += f.path; to.writeBytes(bodies.getValue(f.path)) }
        assertEquals(listOf("model.onnx", "tokens.txt", "espeak-ng-data/sl_dict"), fetched)
        assertEquals("Artur", got.name)
        assertNotNull(v.installed("sl"))
        assertFalse(File(v.dir, "sl.part").exists())
        assertEquals(sl.size.toLong(), File(v.folder("sl"), "espeak-ng-data/sl_dict").length())
        assertTrue(v.bytes("sl") > 0)
        v.remove("sl")
        assertNull(v.installed("sl"))
    }

    @Test fun `a file that doesn't match fails the install, deleted, nothing in place, the good ones kept for the next try`() = runBlocking {
        val v = OfflineVoices(tmp.newFolder("files"))
        try {
            v.install(info()) { f, to -> to.writeBytes(if (f.path == "tokens.txt") "tampered".toByteArray() else bodies.getValue(f.path)) }
            fail("a wrong checksum must fail")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("tokens.txt"))
        }
        assertNull(v.installed("sl"))
        assertFalse(File(v.dir, "sl.part/tokens.txt").exists())
        assertTrue(File(v.dir, "sl.part/model.onnx").isFile)
        // the next try fetches only what's missing
        val fetched = mutableListOf<String>()
        v.install(info()) { f, to -> fetched += f.path; to.writeBytes(bodies.getValue(f.path)) }
        assertEquals(listOf("tokens.txt", "espeak-ng-data/sl_dict"), fetched)
        assertNotNull(v.installed("sl"))
    }

    @Test fun `a path that would lead out of the voice's folder is refused`() {
        val root = tmp.newFolder("v")
        assertNotNull(OfflineVoices.safe(root, "espeak-ng-data/lang/zls/sl"))
        assertNull(OfflineVoices.safe(root, "../voice.json"))
        assertNull(OfflineVoices.safe(root, "/etc/passwd"))
        assertNull(OfflineVoices.safe(root, "a//b"))
        assertNull(OfflineVoices.safe(root, ""))
        val v = OfflineVoices(tmp.newFolder("files"))
        val bad = info().copy(files = listOf(OfflineVoiceFile("../escape", 1, sha(byteArrayOf(1)))))
        try {
            runBlocking { v.install(bad) { _, to -> to.writeBytes(byteArrayOf(1)) } }
            fail("a path out of the folder must fail")
        } catch (e: IOException) {
            assertFalse(File(tmp.root, "files/escape").exists())
        }
    }

    @Test fun `the node's list reads, unknown fields and all`() {
        val raw = """{"voices":[{"language":"sl","id":"sl_SI-artur-medium","name":"Artur","quality":"medium","bytes":63995000,"status":"ready",
            "licence":"CC BY 4.0","attribution":"ARTUR studio TTS","source":"https://huggingface.co/x","sample_rate":22050,"espeak":"sl",
            "inference":{"noise_scale":0.667,"length_scale":1,"noise_w":0.8},"files":[{"path":"model.onnx","bytes":63200622,"sha256":"7e0c"}],"new":1},
            {"language":"it","id":"it_IT-paola-medium","status":"fetching","progress":{"done":10,"total":100}}]}"""
        val l = OfflineVoiceApi.parseList(raw)
        assertEquals(2, l.size)
        assertTrue(l[0].ready)
        assertEquals(0.8f, l[0].inference!!.noiseW)
        assertFalse(l[1].ready)
        assertEquals(100L, l[1].progress!!.total)
    }
}
