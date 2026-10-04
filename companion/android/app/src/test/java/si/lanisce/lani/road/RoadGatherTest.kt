package si.lanisce.lani.road

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.PackInfo
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.Voice
import si.lanisce.lani.data.VoiceProfile
import si.lanisce.lani.data.json
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.Story
import si.lanisce.lani.game.scene.StoryTelling
import si.lanisce.lani.game.scene.answers
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.game.villagers.Villager
import java.io.File

class RoadGatherTest {
    @Test fun `cards due by the day of the drive, not error patterns, with their due dates`() {
        val raw = """{"databases": {"spaced_repetition": {"items": {
            "a": {"type": "vocabulary", "content": "dober dan", "answer": "good day", "due_date": "2026-09-29"},
            "b": {"type": "vocabulary", "content": "hvala", "answer": "thank you", "due_date": "2026-10-01"},
            "c": {"type": "vocabulary", "content": "prosim", "answer": "please", "due_date": "2026-10-20"},
            "d": {"type": "error_pattern", "content": "vas pomagam", "answer": "vam pomagam", "due_date": "2026-09-01"},
            "e": {"type": "vocabulary", "content": "kruh"}
        }}}}"""
        val cards = RoadGather.cards(raw, "2026-10-02")
        assertEquals(listOf("a" to "2026-09-29", "b" to "2026-10-01"), cards.map { it.first.id to it.second })
        assertTrue(RoadGather.cards("""{"databases": {}}""", "2026-10-02").isEmpty())
    }

    @Test fun `the packs under way first, then the easiest, not done ones or festivals`() {
        val packs = listOf(
            PackInfo("b2", "B2", level = "B2", total = 10),
            PackInfo("a1", "A1", level = "A1", total = 10),
            PackInfo("done", "Done", level = "A1", total = 10, learned = 10),
            PackInfo("going", "Going", level = "A2", total = 10, learned = 3),
            PackInfo("feast", "Feast", level = "A1", total = 10, festival = "pust"),
        )
        assertEquals(listOf("going", "a1", "b2"), RoadGather.packOrder(packs))
    }

    @Test fun `someone's voices, their own first, then their archetype's, speaker and narrators`() {
        val micka = Villager("micka", "Babica Micka", art = "grandma", voice = "female", speaker = "grandma")
        assertEquals(listOf("grandma", "female"), RoadGather.voices(micka, null, emptyMap()))
        val own = mapOf("micka" to VoiceProfile("@micka", "grandma", status = "own"))
        assertEquals(listOf("@micka", "grandma", "female"), RoadGather.voices(micka, null, own))
        assertEquals(listOf("male", "female"), RoadGather.voices(null, "smith", emptyMap()))
    }

    @Test fun `the library has only what has clips, the stories at the learner's level`() {
        val index = mapOf(
            "dober dan" to mapOf("female" to "/voice/file/a.mp3"),
            "kruh" to mapOf("female" to "/voice/file/k.mp3"),
            "pozdravljen" to mapOf("grandma" to "/voice/file/p.mp3"),
            "dober dan babica" to mapOf("female" to "/voice/file/d.mp3"),
            "nekoč" to mapOf("grandpa" to "/voice/file/n.mp3"),
            "nekoč je bil zmaj" to mapOf("grandpa" to "/voice/file/z.mp3"),
        )
        val villagers = listOf(
            Villager("micka", "Micka", art = "grandma", voice = "female", speaker = "grandma"),
            Villager("janez", "Janez", art = "grandpa", voice = "male", speaker = "grandpa"),
        )
        val scene = SceneSpec(
            "kuhinja", "Kuhinja · Kitchen", art = "kitchen", from = listOf("house"),
            people = listOf(ScenePerson("babica", "Babica", art = "grandma", slot = "stove", villager = "micka")),
            dialogs = listOf(Dialog("hi", listOf(DialogLine(who = "babica", sl = "Pozdravljen!", en = "Hello!"), DialogLine(choices = listOf(DialogChoice("Dober dan, babica!", "Good day, grandma!", ok = true)))))),
            stories = listOf(
                Story(
                    "zmaj", "Zmaj · The dragon", teller = "janez",
                    levels = mapOf(
                        "A1" to StoryTelling(listOf(DialogLine(sl = "Nekoč.", en = "Once."))),
                        "A2" to StoryTelling(listOf(DialogLine(sl = "Nekoč je bil zmaj.", en = "Once there was a dragon."))),
                    ),
                ),
            ),
        )
        val inputs = RoadInputs(
            cards = listOf(ReviewCard("c", "dober dan", "good day") to "2026-09-29", ReviewCard("x", "nasvidenje", "goodbye") to "2026-09-29"),
            words = listOf("hrana" to PackWord("w", sl = "kruh", en = "bread"), "hrana" to PackWord("v", sl = "voda", en = "water")),
            scenes = listOf(scene, scene.copy(id = "tuji", language = "it")),
            villagers = villagers,
            index = index,
            level = "A2",
        )
        val lib = RoadGather.library(inputs, { "You say: $it" }, now = 7)
        // the dialog's phrases to shadow last: "Pozdravljen!" is one word, too short
        assertEquals(listOf("card:c", "word:hrana/w", "dialog:kuhinja/hi", "story:zmaj/0", "phrase:dober dan babica"), lib.items.map { it.id })
        // a scene above the learner's level has no phrases to shadow (its dialogs play as before)
        val above = RoadGather.library(inputs.copy(scenes = listOf(scene.copy(level = "B1")), level = "A1"), { "You say: $it" }, now = 7)
        assertTrue(above.of(Kind.PHRASE).isEmpty())
        assertEquals(listOf("dialog:kuhinja/hi"), above.of(Kind.DIALOG).map { it.id })
        val dialog = lib.items.first { it.kind == Kind.DIALOG }
        assertEquals(listOf("p.mp3", "d.mp3"), dialog.sounds.filterIsInstance<Sound.Clip>().flatMap { it.files })
        val story = lib.items.first { it.kind == Kind.STORY }
        assertEquals(listOf("z.mp3"), story.sounds.filterIsInstance<Sound.Clip>().flatMap { it.files })
        assertEquals("en", lib.base)
        assertEquals("1 h 5 min", RoadGather.duration(3_900.0))
        assertEquals("40 min", RoadGather.duration(2_400.0))
    }

    @Test fun `a curated story plays from the clips voice-build makes, in the teller's own voice`() {
        // Zlatorog as the culture pack has it, served in the campfire scene (its words, resolved by the bridge, left out)
        val file = listOf(File("../../cultures/primorska/stories/zlatorog.json"), File("../cultures/primorska/stories/zlatorog.json")).first { it.isFile }
        val story = JsonObject(json.parseToJsonElement(file.readText()).jsonObject - "words")
        val scene = parseScene(
            buildJsonObject {
                put("id", "ob-ognju")
                put("title", "Ob ognju · At the campfire")
                put("art", "campfire")
                putJsonArray("from") { add("fire") }
                putJsonArray("people") {
                    addJsonObject { put("id", "janez"); put("name", "Stari Janez"); put("art", "grandpa"); put("slot", "fire"); put("villager", "janez") }
                }
                putJsonArray("stories") { add(story) }
            }.toString(),
        )
        val lines = scene.stories.single().levels.getValue("A1").lines
        // the key the bridge's corpus has for its first line (companion/bridge/test/smoke/stories.ts checks the same:
        // "@janez:dober večer jan sedi k ognju nocoj ti povem zgodbo")
        assertEquals("dober večer jan sedi k ognju nocoj ti povem zgodbo", Voice.normalize(lines.first().sl!!))
        // what voice-build makes (bridge/src/voice.ts, storyTexts): the teller's lines and the replies in his own voice
        // (@janez), the learner's right choices in the default voice, each phrase under its normalized text
        val index = mutableMapOf<String, MutableMap<String, String>>()
        fun voiced(text: String?, voice: String) = text?.let { Voice.phrases(it) }.orEmpty().forEach { p ->
            index.getOrPut(Voice.normalize(p)) { mutableMapOf() }[voice] = "/voice/file/${voice.hashCode()}-${Voice.normalize(p).hashCode()}.mp3"
        }
        for (l in lines) {
            if (l.choices.isEmpty()) voiced(l.sl, "@janez")
            for (c in l.choices) {
                if (c.ok) voiced(c.sl, "female")
                voiced(c.reply?.sl, "@janez")
            }
        }
        // an older clip of his first line in his archetype's voice (a scene's, the prebuild's before): his own comes first
        voiced(lines.first().sl, "grandpa")
        val janez = Villager("janez", "Stari Janez", art = "grandpa", voice = "male", speaker = "grandpa")
        val inputs = RoadInputs(
            scenes = listOf(scene),
            villagers = listOf(janez),
            profiles = mapOf("janez" to VoiceProfile("@janez", "grandpa", status = "own")),
            index = index,
            level = "A1",
        )
        val item = RoadGather.library(inputs, { "You say: $it" }, now = 7).items.single { it.kind == Kind.STORY }
        // every line of the evening, each turn's right answer and its reply: none left out
        val expected = lines.flatMap { l ->
            if (l.choices.isEmpty()) listOf(l.sl!!) else l.answers.first().let { a -> listOfNotNull(a.sl, a.reply?.sl) }
        }
        val played = item.sounds.filterIsInstance<Sound.Clip>()
        assertEquals(expected, played.map { it.text })
        // the teller's lines and the replies all in his own voice's clips, the answers in the narrator's
        fun files(voice: String) = index.values.mapNotNull { it[voice] }.map(RoadPlay::fileOf).toSet()
        val answers = lines.flatMap { l -> l.answers.take(1).map { it.sl } }.toSet()
        assertTrue(played.filter { it.text !in answers }.all { c -> c.files.all { it in files("@janez") } })
        assertTrue(played.filter { it.text in answers }.all { c -> c.files.all { it in files("female") } })
        assertEquals("story:zlatorog/0", item.id)
        // in English only the teaser, a recap after each paragraph from the notebook's notes (A1), and "You say: …"
        val english = item.sounds.filterIsInstance<Sound.Prompt>()
        assertEquals("Janez tells the story of Zlatorog", english.first().text)
        val recaps = english.drop(1).filter { !it.cue }.map { it.text }
        assertEquals(3, recaps.size)
        assertTrue(recaps[0], recaps[0].startsWith("High in the mountains, under Triglav, there lived a white chamois."))
        assertTrue(recaps[1], recaps[1].startsWith("One day a hunter came into the mountains."))
        assertTrue(recaps[2], recaps[2].startsWith("Where the blood fell, a red flower grew."))
        assertTrue(recaps[2], recaps[2].endsWith("The hunter could not see anything and fell into the abyss."))
        assertTrue(recaps.none { "Jan" in it || "?" in it })
        // no line's English after it any more: the teller's lines follow each other in Slovene
        assertEquals(lines.count { it.choices.isNotEmpty() }, english.count { it.cue })
    }
}
