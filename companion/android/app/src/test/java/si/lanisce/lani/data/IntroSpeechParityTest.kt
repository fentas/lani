package si.lanisce.lani.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.IntroSpeech.Mark
import si.lanisce.lani.data.IntroSpeech.Run
import si.lanisce.lani.data.IntroSpeech.Script
import si.lanisce.lani.l10n.Lang
import java.io.File

/**
 * The introductions read aloud as the node voices them ahead (companion/COURSE.md, "Read aloud"): the cases of
 * bridge/test/fixtures/intro-speech.json, which the bridge's port (src/course-speech.ts) reads in the smoke too. Each intro
 * is parsed as the app parses a unit's ([Courses.intro]), read with the app's check for the language learned (a clip of the
 * text on the node, [Clips.has], or [Spoken.looks]) and compared with what the fixture says is read: so voice-build makes
 * exactly the teacher's lines the app asks for, and the two can't drift.
 */
class IntroSpeechParityTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "bridge/test/fixtures").isDirectory }
    private val fixture = Json.parseToJsonElement(File(companion, "bridge/test/fixtures/intro-speech.json").readText()).jsonObject

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun lang(code: String) = Lang.of(code) ?: error("no language $code")

    /** A text per language as the app reads one: trimmed, blanks left out. */
    private fun texts(e: JsonElement?): Map<String, String>? =
        (e as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { k to it } }?.toMap()?.takeIf { it.isNotEmpty() }

    private fun mark(m: Mark): String = when (m) {
        Mark.Whole -> "whole"
        is Mark.Part -> "part:${m.line}:${m.segments.first}-${m.segments.last}"
        is Mark.Example -> "example:${m.index}"
        Mark.Question -> "question"
        Mark.Answer -> "answer"
    }

    private fun run(r: Run) = buildJsonObject {
        put("text", r.text)
        put("slovene", r.slovene)
        put("line", r.line)
        put("segments", buildJsonArray { add(r.segments.first); add(r.segments.last) })
    }

    /** [s] as the fixture writes a script: its explanation (plain, lang, the store's key, runs) and its lines. */
    private fun script(s: Script): Map<String, JsonElement> = mapOf(
        "explanation" to (s.explanation?.let { e ->
            buildJsonObject {
                put("plain", e.plain)
                put("lang", e.lang)
                put("key", IntroSpeech.key(e.lang, e.plain))
                put("runs", JsonArray(e.runs.map(::run)))
            }
        } ?: JsonNull),
        "lines" to JsonArray(s.lines.map { l -> buildJsonObject { put("text", l.text); put("slovene", l.slovene); put("mark", mark(l.mark)) } }),
    )

    @Test fun `Spoken looks reads each text as the bridge's port reads it`() {
        val cases = fixture.getValue("looks").jsonArray.map { it.jsonObject }
        assertTrue(cases.size >= 10)
        for (c in cases) {
            val text = c.str("text")!!
            assertEquals("\"$text\" (${c.str("target")} for a learner of ${c.str("base")})", c.getValue("looks").jsonPrimitive.content.toBoolean(), Spoken.looks(text, lang(c.str("target")!!), lang(c.str("base")!!)))
        }
    }

    @Test fun `each intro is read as the bridge's port reads it, step by step, the answers and the summary`() {
        val cases = fixture.getValue("intros").jsonArray.map { it.jsonObject }
        assertTrue(cases.size >= 5)
        for (c in cases) {
            val name = c.str("name")!!
            val language = c.str("language")!!
            val base = c.str("base")!!
            val title = c.str("title")
            // the voice store's index as the app gets it: the texts the node has a clip of, in their voices
            val index: ClipIndex = c.getValue("clips").jsonArray.withIndex().fold(emptyMap()) { idx, (i, e) ->
                val (text, voice) = e.jsonArray.map { it.jsonPrimitive.content }
                val key = Voice.normalize(text)
                idx + (key to (idx[key].orEmpty() + (voice to "/voice/file/$i.mp3")))
            }
            val isSlovene: (String) -> Boolean = { t -> Clips.has(index, t) || Spoken.looks(t, lang(language), lang(base)) }
            val intro = Courses.intro(c.getValue("intro").jsonObject)
            // the unit's words (its `words`), which the intro's reading counts as the language learned too
            val words = (c["words"] as? kotlinx.serialization.json.JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
            assertNotNull(name, intro)
            val expected = c.getValue("expected").jsonObject
            assertEquals("$name: the intro's own words", expected.getValue("vocabulary").jsonArray.map { it.jsonPrimitive.content }, IntroSpeech.vocabulary(intro!!, language, title, isSlovene, words).sorted())
            val steps = expected.getValue("steps").jsonArray.map { it.jsonObject }
            assertEquals("$name: the steps", steps.size, intro.steps.size)
            steps.forEachIndexed { i, want ->
                val got = script(IntroSpeech.introStep("x/$i", intro, i, language, base, title, isSlovene, words))
                assertEquals("$name, step $i: the explanation", want["explanation"], got["explanation"])
                assertEquals("$name, step $i: the lines", want["lines"], got["lines"])
                assertEquals("$name, step $i: the answer", want.str("answer"), IntroSpeech.answer(intro.steps[i].ask))
            }
            val summary = c["summary"] as? JsonObject
            val say = summary?.let { IntroSpeech.summarySay(texts(it["rule"]), texts(it["goal"]), base) }
            val want = expected["summary"] as? JsonObject
            assertEquals("$name: the summary's text", want?.str("say"), say)
            if (want != null && say != null) {
                val got = script(IntroSpeech.summary("x/summary", say, base, intro, language, title, isSlovene, words))
                assertEquals("$name: the summary's explanation", want["explanation"], got["explanation"])
                assertEquals("$name: the summary's lines", want["lines"], got["lines"])
            }
        }
    }
}
