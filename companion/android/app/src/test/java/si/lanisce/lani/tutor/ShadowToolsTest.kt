package si.lanisce.lani.tutor

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.BridgeConfig

/**
 * Phase A's tools in shadow mode: writes are dry runs (validated, recorded, nothing sent) that read like the live tool's
 * success, reads read, reply goes to the panel; a report about a word needs a lookup first.
 */
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
        assertEquals("kept: kurim → kuriti (to heat (a stove)); the next lookup shows it", o.content)
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

    /** What would tell the model it isn't live (the quality pass: "the app answered in shadow mode, nothing may have been saved"). */
    private fun assertLive(o: ToolOutcome) {
        for (w in listOf("shadow", "dry run", "dry-run", "not saved", "nothing was written", "nothing was sent", "simulat", "pretend", "test")) {
            assertFalse("${o.record.name}: \"$w\" in ${o.content}", o.content.contains(w, ignoreCase = true))
        }
    }

    private fun strings(vararg s: String) = JsonArray(s.map(::JsonPrimitive))

    @Test fun `each write's dry run reads like the live tool's success, and is recorded as a dry run`() {
        val writes = listOf(
            use("publish_gloss", "form" to "kurim", "lemma" to "kuriti", "gloss" to strings("to heat", "to burn"), "gloss_lang" to "en") to
                "kept: kurim → kuriti (to heat; to burn; English); the next lookup shows it",
            use("publish_sentence_note", "sentence" to " Dvanajst krav se pase. ", "text" to "**se pase**: grazes", "gloss_lang" to "en") to
                "kept: \"Dvanajst krav se pase.\" (English); the app shows it the next time the learner long-presses the sentence",
            use("record_session", "session_id" to "session-042", "date" to "2026-10-18", "skill_scores" to obj("grammar" to obj("exercises" to 8, "correct" to 6))) to
                "[Lani] ✅ Updated 6 databases for session session-042\n[Lani] 📊 This session: 6/8 correct (75%)",
            use("record_session", "session_id" to "session-042", "date" to "2026-10-18", "record_session" to false,
                "new_vocabulary" to JsonArray(listOf(obj("item_id" to "vocab_word_kuriti", "content" to "kuriti", "answer" to "to heat")))) to
                "[Lani] ✅ Added 1 review item(s) (vocab_word_kuriti); no session recorded",
            use("report_problem", "kind" to "wish", "title" to "Idea: a timer on the storm", "what_is_wrong" to "A visible timer would help.") to
                "posted: \"Idea: a timer on the storm\", a new discussion in Lani's Ideas (the learner confirmed it)",
        )
        for ((o, said) in writes) {
            assertFalse(o.content, o.isError)
            assertEquals(said, o.content)
            assertTrue(o.record.dryRun)
            assertEquals("write", o.record.kind)
            assertEquals(o.content, o.record.result)
            assertLive(o)
        }
        // set_rhythm: the settings after the change, as the bridge answers (the rest its defaults)
        val rhythm = use("set_rhythm", "morning" to "08:15", "weekly_day" to 6)
        val settings = tutorJson.parseToJsonElement(rhythm.content).jsonObject
        assertEquals(listOf("enabled", "morning", "evening", "weekly_day", "weekly"), settings.keys.toList())
        assertEquals("08:15", (settings["morning"] as JsonPrimitive).content)
        assertEquals("20:30", (settings["evening"] as JsonPrimitive).content)
        assertEquals("6", (settings["weekly_day"] as JsonPrimitive).content)
        assertTrue(rhythm.record.dryRun && !rhythm.isError)
        assertLive(rhythm)
        assertTrue(reads.isEmpty() && replies.isEmpty())
    }

    @Test fun `card's writes answer as card py does, dry_run as asked`() {
        val writes = listOf(
            use("card", "action" to "edit", "card_id" to "vocab_kurim", "back" to "I heat (the stove)", "reason" to "the meaning", "dry_run" to true),
            use("card", "action" to "regrade", "card_id" to "vocab_kurim", "quality" to 4, "reason" to "a typo the card caused"),
            use("card", "action" to "merge", "card_id" to "vocab_da", "drop_id" to "vocab_ja", "front" to "da / ja", "reason" to "one meaning"),
            use("card", "action" to "split", "card_id" to "vocab_ne", "new_id" to "vocab_ja", "new_front" to "ja", "new_back" to "yes", "reason" to "two meanings"),
            use("card", "action" to "forgive", "pattern_id" to "dual_forms", "date" to "2026-10-17", "reason" to "the key was wrong"),
        )
        for (o in writes) {
            assertFalse(o.content, o.isError)
            assertTrue(o.record.dryRun)
            assertEquals("write", o.record.kind)
            assertLive(o)
        }
        val (edit, regrade, merge, split, forgive) = writes.map { tutorJson.parseToJsonElement(it.content).jsonObject }
        assertEquals("vocab_kurim", (edit["id"] as JsonPrimitive).content)
        assertEquals("I heat (the stove)", (edit["after"]!!.jsonObject["answer"] as JsonPrimitive).content)
        assertEquals(JsonPrimitive(true), edit["dry_run"])
        assertEquals(JsonPrimitive(false), regrade["dry_run"])
        assertEquals("4", (regrade["review"]!!.jsonObject["quality"]!!.jsonObject["to"] as JsonPrimitive).content)
        assertEquals("vocab_ja", (merge["dropped"] as JsonPrimitive).content)
        assertEquals("ja", (split["new"]!!.jsonObject["content"] as JsonPrimitive).content)
        assertEquals("vocab_ne", (split["new"]!!.jsonObject["split_from"] as JsonPrimitive).content)
        assertEquals("dual_forms", (forgive["pattern"] as JsonPrimitive).content)
        // what card.py and update-db.py refuse is refused, recorded as a dry run with its error
        for (bad in listOf(
            use("card", "action" to "edit", "front" to "kurim"),
            use("card", "action" to "edit", "card_id" to "vocab_kurim"),
            use("card", "action" to "merge", "card_id" to "vocab_da", "drop_id" to "vocab_da"),
            use("card", "action" to "split", "card_id" to "vocab_ne", "new_id" to "vocab_ja"),
            use("record_session", "session_id" to "s", "date" to "2026-10-18", "record_session" to false),
        )) {
            assertTrue(bad.content, bad.isError)
            assertTrue(bad.record.dryRun)
            assertEquals(listOf(bad.content), bad.record.errors)
        }
    }

    private val vedve = arrayOf(
        "kind" to "fix",
        "title" to "Fix: course a1-03 sheet ws-2, vedve",
        "item" to obj("kind" to "course", "language" to "sl", "unit" to "a1-03", "sheet" to "ws-2", "exercise_index" to 4),
        "what_is_wrong" to "The key takes vedve, which isn't in the dictionary; it should be vidve.",
    )

    @Test fun `a report about a word fails without a lookup earlier in the turn`() {
        val o = use("report_problem", *vedve)
        assertTrue(o.isError)
        assertTrue(o.content, o.content.contains("dictionary_lookup"))
        assertTrue(o.record.dryRun)
        assertEquals(listOf(o.content), o.record.errors)
        assertLive(o)
        // a lookup that didn't run (its input invalid) doesn't count
        assertTrue(use("dictionary_lookup", "word" to "").isError)
        assertTrue(use("report_problem", *vedve).isError)
    }

    @Test fun `a report about a word passes after a lookup, in that turn only`() {
        assertFalse(use("dictionary_lookup", "word" to "vedve").isError)
        assertEquals(listOf("dictionary_lookup"), reads)
        val o = use("report_problem", *vedve)
        assertFalse(o.content, o.isError)
        assertEquals("posted: \"Fix: course a1-03 sheet ws-2, vedve\", a new discussion in Lani's Content fixes (the learner confirmed it)", o.content)
        assertTrue(o.record.dryRun)
        // the next turn's tools keep their own lookups
        val next = ShadowTools(specs, { _, _ -> ReadResult("{}") }, Guide(), notebook, onReply = {})
        assertTrue(runBlocking { next.run(ToolUse("toolu_r", "report_problem", obj(*vedve))) }.isError)
    }

    @Test fun `reports not about a word need no lookup`() {
        val wish = use("report_problem", "kind" to "wish", "title" to "Idea: the storm in the evening", "what_is_wrong" to "A storm at night would be fun.")
        assertFalse(wish.content, wish.isError)
        val picture = use(
            "report_problem", "kind" to "fix", "title" to "Fix: scene kmetija, the picture",
            "item" to obj("kind" to "scene", "scene_id" to "kmetija"), "what_is_wrong" to "The cow stands on the barn's roof.",
        )
        assertFalse(picture.content, picture.isError)
        assertTrue(reads.isEmpty())
        // what makes a report one about a word: a source with an answer or a word, its ids, or what it says
        assertTrue(ReportCheck.aboutWords(obj("kind" to "fix", "item" to obj("kind" to "pack", "pack_id" to "praznik-trgatev", "word_id" to "skarje"))))
        assertTrue(ReportCheck.aboutWords(obj("kind" to "fix", "item" to obj("card_id" to "vocab_vedve"))))
        assertTrue(ReportCheck.aboutWords(obj("kind" to "fix", "title" to "Fix: villager mojca, a line's translation")))
        assertTrue(ReportCheck.aboutWords(obj("kind" to "fix", "title" to "Fix: story x", "what_is_wrong" to "This word doesn't exist.")))
        assertFalse(ReportCheck.aboutWords(obj("kind" to "wish", "title" to "Idea: accept more answers")))
        assertFalse(ReportCheck.aboutWords(obj("kind" to "fix", "title" to "Fix: scene trg, the button", "what_is_wrong" to "It doesn't open the dialog.")))
    }

    @Test fun `the reads say nothing of the shadow`() = runBlocking {
        val state = """{"databases":{"spaced_repetition":{"items":{"vocab_kurim":{"content":"kurim","answer":"I heat"}}}}}"""
        val answers = Answering { r ->
            when (r.url.encodedPath) {
                "/lookup" -> Triple(200, """{"word":"vedve","entries":[]}""", "application/json")
                "/game" -> Triple(200, """{"state":{"age":12,"name":"Lipa"}}""", "application/json")
                else -> Triple(200, state, "application/json")
            }
        }
        val bridge = ShadowBridge(BridgeConfig("http://10.0.2.2:8791", "bridge-token"), OkHttpClient.Builder().addInterceptor(answers).build())
        val reads = BridgeReads({ bridge }, { emptyList() })
        for ((name, input) in listOf(
            "dictionary_lookup" to obj("word" to "vedve"),
            "get_village" to obj(),
            "card" to obj("action" to "find", "text" to "kurim"),
            "recent_answers" to obj("card_id" to "vocab_kurim"),
        )) {
            val r = reads.read(name, input)
            assertFalse(r.text, r.isError)
            assertFalse("$name: ${r.text}", r.text.contains("shadow", ignoreCase = true))
        }
        assertTrue(reads.read("card", obj("action" to "show", "card_id" to "vocab_kurim")).text.startsWith("1 card(s)"))
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
