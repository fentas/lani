package si.lanisce.lani.tutor

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase A's tools in shadow mode: writes are dry runs (validated, recorded, nothing sent), reads read, reply goes to the panel. */
class ShadowToolsTest {
    private val specs = PhaseA.load().associateBy { it.name }

    private class Notebook : ShadowTools.NotebookHost {
        var text = ""
        override fun note(line: String) { text += "\n- $line" }
        override fun write(text: String) { this.text = text }
        override val size get() = text.length
    }

    private val reads = mutableListOf<String>()
    private val replies = mutableListOf<ShadowReply>()
    private val notebook = Notebook()
    private val tools = ShadowTools(
        specs,
        { name, _ -> reads += name; ReadResult("{\"read\": \"$name\"}") },
        Guide(),
        notebook,
        onReply = { replies += it },
    )

    private fun use(name: String, vararg kv: Pair<String, Any?>) = runBlocking { tools.run(ToolUse("toolu_$name", name, obj(*kv))) }

    @Test fun `the bundle has phase A's seventeen tools, in their order`() {
        assertEquals(PhaseA.TOOLS.map { it.first }, PhaseA.load().map { it.name })
        assertEquals(17, PhaseA.definitions(PhaseA.load()).size)
        // the bridge's own, exported; the app's, hand-written
        assertTrue(specs.getValue("publish_gloss").description.isNotBlank())
        assertTrue(specs.getValue("record_session").inputSchema.toString().contains("session_id"))
        assertTrue(Guide().topics.size > 10)
        assertTrue(Guide().topic("feedback")!!.contains("Score"))
    }

    @Test fun `a valid write is a dry run, recorded, nothing sent anywhere`() {
        val o = use("publish_gloss", "form" to "kurim", "lemma" to "kuriti", "gloss" to JsonArray(listOf(JsonPrimitive("to heat (a stove)"))))
        assertFalse(o.content, o.isError)
        assertTrue(o.record.dryRun)
        assertEquals("write", o.record.kind)
        assertTrue(o.content.startsWith("dry run"))
        assertTrue(reads.isEmpty() && replies.isEmpty())
        assertEquals("kurim", (o.record.input["form"] as JsonPrimitive).content)
    }

    @Test fun `an invalid write returns the schema's errors, and is recorded as such`() {
        val o = use("set_rhythm", "morning" to 7)
        assertTrue(o.isError)
        assertTrue(o.record.dryRun)
        assertTrue(o.record.errors.isNotEmpty())
        assertTrue(o.content.contains("invalid input"))
    }

    @Test fun `card reads with find and show, writes with the rest`() {
        val find = use("card", "action" to "find", "text" to "kurim")
        assertEquals("read", find.record.kind)
        assertEquals(listOf("card"), reads)
        val edit = use("card", "action" to "edit", "card_id" to "vocab_kurim", "front" to "kurim", "reason" to "the meaning was wrong")
        assertEquals("write", edit.record.kind)
        assertTrue(edit.content, edit.record.dryRun)
        assertEquals(listOf("card"), reads)
    }

    @Test fun `reply goes to the panel only`() {
        val o = use("reply", "conversation_id" to "main", "text" to "**Dobro!**", "data" to obj("score" to 8))
        assertEquals("delivered", o.content)
        assertEquals("**Dobro!**", replies.single().text)
        assertEquals(8, (replies.single().data!!["score"] as JsonPrimitive).content.toInt())
        assertTrue(use("reply", "conversation_id" to "../../x", "text" to "x").isError)
    }

    @Test fun `the guide and the notebook are the tutor's own`() {
        val g = use("guide", "topic" to "feedback")
        assertFalse(g.isError)
        assertTrue(use("guide", "topic" to "nope").isError)
        use("notebook_note", "text" to "Jan likes tables")
        assertTrue(notebook.text.contains("tables"))
        use("notebook_write", "text" to "# Notebook\n- A2")
        assertEquals("# Notebook\n- A2", notebook.text)
    }

    @Test fun `unknown tools and bad JSON are errors`() = runBlocking {
        assertTrue(tools.run(ToolUse("x", "bash", obj())).isError)
        val bad = tools.run(ToolUse("y", "reply", obj(), badJson = "{\"text\": \"cut"))
        assertTrue(bad.isError)
        assertTrue(bad.content.contains("INVALID_JSON"))
        assertTrue(replies.isEmpty())
    }

    @Test fun `the schema checker`() {
        val s = obj(
            "type" to "object",
            "properties" to obj(
                "kind" to obj("type" to "string", "enum" to JsonArray(listOf(JsonPrimitive("fix"), JsonPrimitive("wish")))),
                "title" to obj("type" to "string", "maxLength" to 5),
                "n" to obj("type" to "integer", "minimum" to 1),
                "tags" to obj("type" to "array", "items" to obj("type" to "string")),
            ),
            "required" to JsonArray(listOf(JsonPrimitive("kind"))),
            "additionalProperties" to false,
        )
        assertEquals(emptyList<String>(), Schema.check(s, obj("kind" to "fix", "title" to "ab", "n" to 2, "tags" to JsonArray(listOf(JsonPrimitive("a"))))))
        val errors = Schema.check(s, obj("kind" to "bug", "title" to "too long", "n" to 0, "tags" to JsonArray(listOf(JsonPrimitive(1))), "x" to 1))
        assertEquals(5, errors.size)
        assertTrue(Schema.check(s, obj()).single().contains("required"))
        assertTrue(Schema.check(obj("type" to "number"), JsonPrimitive(3)).isEmpty())
        assertTrue(Schema.check(specs.getValue("reply").inputSchema, obj("text" to "x")).any { it.contains("conversation_id") })
        assertTrue(specs.getValue("reply").inputSchema.jsonObject.containsKey("properties"))
    }
}
