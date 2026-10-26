package si.lanisce.lani.tutor

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.BridgeConfig

/**
 * The shadow's dry-run card writes as later reads see them (the matrix, 2026-10: "the change didn't seem to save", four
 * turns looping to the call limit): an edit, a merge, a split and a regrade laid over the deck, the way card.py writes
 * them; never a write the model asked as a dry run itself, nor a refused one; within the turn and across the day's turns.
 */
class CardOverlayTest {
    private val deck = obj(
        "vocab_mrzlo" to obj("id" to "vocab_mrzlo", "type" to "vocabulary", "content" to "mrzlo", "answer" to "cold", "category" to "weather"),
        "vocab_mrzel" to obj("id" to "vocab_mrzel", "type" to "vocabulary", "content" to "mrzel", "answer" to "cold (adjective)", "category" to "adjectives"),
        "vocab_luc" to obj(
            "id" to "vocab_luc", "content" to "luč", "answer" to "lamp, lantern", "category" to "home", "last_quality" to 1,
            "review_history" to JsonArray(listOf(obj("date" to "2026-10-19", "quality" to 4), obj("date" to "2026-10-20", "quality" to 1))),
        ),
        "vocab_svetilka" to obj("id" to "vocab_svetilka", "content" to "svetilka", "answer" to "lamp", "category" to "home"),
    )
    private val today = "2026-10-20"

    private fun card(vararg kv: Pair<String, Any?>) = obj("action" to "edit", *kv)

    @Test fun `an edit shows its new texts and an edits entry, as card py writes it`() {
        val after = CardOverlay.apply(deck, listOf(card("card_id" to "vocab_mrzlo", "back" to "it's cold (adverb)", "reason" to "mrzel was right")), today)
        val c = after["vocab_mrzlo"]!!.jsonObject
        assertEquals("it's cold (adverb)", c["answer"]!!.jsonPrimitive.content)
        assertEquals("mrzlo", c["content"]!!.jsonPrimitive.content)
        val edit = c["edits"]!!.jsonArray.single().jsonObject
        assertEquals(obj("date" to today, "action" to "edit", "answer" to "cold", "reason" to "mrzel was right"), edit)
        // a note replaces the back's notes; the other cards are as they were
        val noted = CardOverlay.apply(deck, listOf(card("card_id" to "vocab_mrzel", "note" to "a thing that is cold")), today)
        assertEquals("cold (a thing that is cold)", noted["vocab_mrzel"]!!.jsonObject["answer"]!!.jsonPrimitive.content)
        assertEquals(deck["vocab_luc"], noted["vocab_luc"])
        // nothing changed: no entry
        assertEquals(deck["vocab_mrzlo"], CardOverlay.apply(deck, listOf(card("card_id" to "vocab_mrzlo", "back" to "cold")), today)["vocab_mrzlo"])
    }

    @Test fun `a merge drops the other card into the kept one, a split adds the new card, a regrade changes the review`() {
        val merged = CardOverlay.apply(deck, listOf(obj("action" to "merge", "card_id" to "vocab_svetilka", "drop_id" to "vocab_luc", "front" to "svetilka / luč")), today)
        assertNull(merged["vocab_luc"])
        val kept = merged["vocab_svetilka"]!!.jsonObject
        assertEquals("svetilka / luč", kept["content"]!!.jsonPrimitive.content)
        assertEquals("vocab_luc", kept["merged"]!!.jsonArray.single().jsonObject["id"]!!.jsonPrimitive.content)

        val split = CardOverlay.apply(deck, listOf(obj("action" to "split", "card_id" to "vocab_mrzlo", "back" to "it's cold", "new_id" to "vocab_mraz", "new_front" to "mraz", "new_back" to "frost")), today)
        assertEquals("it's cold", split["vocab_mrzlo"]!!.jsonObject["answer"]!!.jsonPrimitive.content)
        val new = split["vocab_mraz"]!!.jsonObject
        assertEquals(listOf("mraz", "frost", "vocab_mrzlo", "2026-10-21"), listOf("content", "answer", "split_from", "due_date").map { new[it]!!.jsonPrimitive.content })

        val regraded = CardOverlay.apply(deck, listOf(obj("action" to "regrade", "card_id" to "vocab_luc", "quality" to 4)), today)["vocab_luc"]!!.jsonObject
        val last = regraded["review_history"]!!.jsonArray.last().jsonObject
        assertEquals("4", last["quality"]!!.jsonPrimitive.content)
        assertEquals("1", last["regraded"]!!.jsonObject["from"]!!.jsonPrimitive.content)
        assertEquals("4", regraded["last_quality"]!!.jsonPrimitive.content)
        // a review of another day, and a card never reviewed (its first grade)
        val earlier = CardOverlay.apply(deck, listOf(obj("action" to "regrade", "card_id" to "vocab_luc", "quality" to 5, "date" to "2026-10-19")), today)["vocab_luc"]!!.jsonObject
        assertEquals("5", earlier["review_history"]!!.jsonArray.first().jsonObject["quality"]!!.jsonPrimitive.content)
        assertEquals("1", earlier["last_quality"]!!.jsonPrimitive.content)
        val first = CardOverlay.apply(deck, listOf(obj("action" to "regrade", "card_id" to "vocab_mrzel", "quality" to 4)), today)["vocab_mrzel"]!!.jsonObject
        assertEquals("4", first["last_quality"]!!.jsonPrimitive.content)
    }

    @Test fun `only the writes the shadow answered as done - not the model's own dry runs, not a refused one, not a read`() {
        fun rec(input: JsonObject, error: Boolean = false, dry: Boolean = true) = ToolCallRecord("t", "card", input, "write", "ok", isError = error, dryRun = dry)
        val calls = listOf(
            rec(card("card_id" to "vocab_mrzlo", "back" to "a", "dry_run" to true)), // the model's preview: live, nothing written
            rec(card("card_id" to "vocab_mrzlo", "back" to "b"), error = true), // refused
            rec(obj("action" to "show", "card_id" to "vocab_mrzlo")), // a read
            ToolCallRecord("t", "record_session", obj("session_id" to "s"), "write", "ok", dryRun = true),
            rec(card("card_id" to "vocab_mrzlo", "back" to "it's cold")),
        )
        assertEquals(listOf("it's cold"), CardOverlay.writes(calls).map { it["back"]!!.jsonPrimitive.content })
        // a write of a card the deck lacks changes nothing
        assertEquals(deck, CardOverlay.apply(deck, listOf(card("card_id" to "vocab_nope", "back" to "x")), today))
    }

    @Test fun `a dry-run edit shows in a later card show, in the same turn and in the day's next`() = runBlocking {
        val state = obj("databases" to obj("spaced_repetition" to obj("items" to deck))).toString()
        val answers = Answering { Triple(200, state, "application/json") }
        val bridge = ShadowBridge(BridgeConfig("http://10.0.2.2:8791", "bridge-token"), OkHttpClient.Builder().addInterceptor(answers).build())
        val reads = BridgeReads({ bridge }, { emptyList() }, today = { today })
        val notebook = object : ShadowTools.NotebookHost {
            override fun note(line: String) = Unit
            override fun write(text: String) = Unit
            override val size = 0
        }
        val specs = PhaseA.load().associateBy { it.name }
        val turn = ShadowTools(specs, reads, Guide(), notebook, onReply = {})
        val show = obj("action" to "show", "card_id" to "vocab_mrzlo")
        assertTrue(turn.run(ToolUse("a", "card", show)).content.contains("\"answer\": \"cold\""))
        val edit = turn.run(ToolUse("b", "card", card("card_id" to "vocab_mrzlo", "back" to "it's cold (adverb)", "reason" to "mrzel was right")))
        assertFalse(edit.content, edit.isError)
        val again = turn.run(ToolUse("c", "card", show)).content
        assertTrue("the turn's own edit shows: $again", again.contains("\"answer\": \"it's cold (adverb)\""))
        assertFalse(again.contains("dry", ignoreCase = true) || again.contains("shadow", ignoreCase = true))
        // the day's next turn: the earlier turn's records
        val next = ShadowTools(specs, reads, Guide(), notebook, onReply = {}, earlierWrites = CardOverlay.writes(listOf(edit.record)))
        assertTrue(next.run(ToolUse("d", "card", obj("action" to "find", "text" to "mrzlo"))).content.contains("it's cold (adverb)"))
        // the deck itself wasn't asked to change: GETs only
        assertTrue(answers.requests.all { it.method == "GET" })
    }

    @Test fun `the lanes' shared reads keep each lane's writes apart`() = runBlocking {
        var asked = 0
        val shared = SharedReads(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default), kotlinx.coroutines.Dispatchers.Default, { 0L }) { _, _, writes ->
            asked++
            ReadResult("writes: ${writes.size}")
        }
        val show = obj("action" to "show", "card_id" to "vocab_mrzlo")
        assertEquals("writes: 0", shared.read("card", show).text)
        assertEquals("writes: 0", shared.read("card", show, emptyList()).text)
        assertEquals("writes: 1", shared.read("card", show, listOf(card("card_id" to "vocab_mrzlo", "back" to "x"))).text)
        assertEquals("one read for the lanes without writes, one for the lane with its edit", 2, asked)
    }
}
