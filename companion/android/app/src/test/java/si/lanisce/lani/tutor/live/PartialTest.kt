package si.lanisce.lani.tutor.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import si.lanisce.lani.tutor.PartialJson
import si.lanisce.lani.tutor.ResponsesAccumulator
import si.lanisce.lani.tutor.StreamAccumulator

/** The chat's live bubble: a reply's text read from its input as it streams (plan 5, "Streaming into the chat sheet"). */
class PartialTest {
    @Test fun `a string field as far as it came, escapes decoded, a cut-off escape left out`() {
        val full = """{"conversation_id":"main","text":"Dober dan! \"Peč\" je\nstove č.","data":{"text":"no"}}"""
        assertNull(PartialJson.string("""{"conversation_id":"ma""", "text"))
        assertNull(PartialJson.string("""{"conversation_id":"main","te""", "text"))
        assertNull(PartialJson.string("""{"conversation_id":"main","text":""", "text"))
        assertEquals("", PartialJson.string("""{"conversation_id":"main","text":"""", "text"))
        assertEquals("Dober dan! ", PartialJson.string("""{"conversation_id":"main","text":"Dober dan! \""", "text").let { it?.removeSuffix("\"") })
        assertEquals("Dober dan! \"Pe", PartialJson.string("""{"conversation_id":"main","text":"Dober dan! \"Pe""", "text"))
        assertEquals("Dober dan! \"Peč\" je\nstove ", PartialJson.string("""{"conversation_id":"main","text":"Dober dan! \"Peč\" je\nstove \u01""", "text"))
        assertEquals("Dober dan! \"Peč\" je\nstove č.", PartialJson.string(full, "text"))
        assertEquals("main", PartialJson.string(full, "conversation_id"))
        // a nested object's field of the same name doesn't count; one that isn't a string isn't one
        assertNull(PartialJson.string("""{"data":{"text":"inner"}""", "text"))
        assertNull(PartialJson.string("""{"text":42}""", "text"))
    }

    @Test fun `the Messages API's stream hands out a tool's input as it grows, once each`() {
        val acc = StreamAccumulator()
        acc.accept("""{"type":"message_start","message":{"id":"m","model":"claude-sonnet-5-5","usage":{"input_tokens":1}}}""")
        acc.accept("""{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_1","name":"reply","input":{}}}""")
        assertNull(acc.takeTool())
        acc.accept("""{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"conversation_id\":\"main\",\"text\":\"Živ"}}""")
        val d = acc.takeTool()!!
        assertEquals("toolu_1" to "reply", d.id to d.name)
        assertEquals("Živ", PartialJson.string(d.json, "text"))
        assertNull("once", acc.takeTool())
        acc.accept("""{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"jo!\"}"}}""")
        assertEquals("Živjo!", PartialJson.string(acc.takeTool()!!.json, "text"))
    }

    @Test fun `the Responses API's stream hands out a function call's arguments as they grow`() {
        val acc = ResponsesAccumulator()
        acc.accept("""{"type":"response.output_item.added","output_index":0,"item":{"type":"function_call","call_id":"call_1","name":"reply","arguments":""}}""")
        acc.accept("""{"type":"response.function_call_arguments.delta","output_index":0,"item_id":"fc_1","delta":"{\"conversation_id\":\"main\",\"text\":\"Do"}""")
        val d = acc.takeTool()!!
        assertEquals("call_1" to "reply", d.id to d.name)
        assertEquals("Do", PartialJson.string(d.json, "text"))
        acc.accept("""{"type":"response.function_call_arguments.delta","output_index":0,"item_id":"fc_1","delta":"ber dan\"}"}""")
        assertEquals("Dober dan", PartialJson.string(acc.takeTool()!!.json, "text"))
    }
}
