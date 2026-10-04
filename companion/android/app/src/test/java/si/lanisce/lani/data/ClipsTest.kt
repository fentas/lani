package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipsTest {
    @Test fun `lookup order is family, clip, node, tts`() {
        assertEquals(Route.FAMILY, Clips.route(family = true, clip = true, online = true, missedRecently = false))
        assertEquals(Route.CLIP, Clips.route(family = false, clip = true, online = true, missedRecently = false))
        assertEquals(Route.CLIP, Clips.route(family = false, clip = true, online = false, missedRecently = true))
        assertEquals(Route.NODE, Clips.route(family = false, clip = false, online = true, missedRecently = false))
        assertEquals(Route.TTS, Clips.route(family = false, clip = false, online = true, missedRecently = true))
        assertEquals(Route.TTS, Clips.route(family = false, clip = false, online = false, missedRecently = false))
    }

    @Test fun `voice profiles give someone's own voice, or their archetype at their own pitch and pace`() {
        val raw = """{"people": {
            "micka": {"name": "Babica Micka", "speaker": "@micka", "archetype": "grandma", "pitch": 1, "rate": 1, "status": "own"},
            "ancka": {"name": "Teta Ančka", "speaker": "grandma", "archetype": "grandma", "pitch": 0.95, "rate": 1.03, "status": "shared"},
            "odd": {"speaker": "grandpa", "pitch": 3, "rate": "x"},
            "bad": {"archetype": "boy"}
        }}"""
        val p = Clips.parseProfiles(raw)
        assertEquals(setOf("micka", "ancka", "odd"), p.keys)
        assertTrue(p.getValue("micka").own)
        assertEquals("@micka", p.getValue("micka").speaker)
        val a = p.getValue("ancka")
        assertFalse(a.own)
        assertEquals(0.95f, a.pitch)
        assertEquals(1.03f, a.rate)
        // out of range or junk stays gentle
        assertEquals(1.2f, p.getValue("odd").pitch)
        assertEquals(1f, p.getValue("odd").rate)
        assertEquals("grandpa", p.getValue("odd").archetype)
        assertTrue(Clips.parseProfiles("""{"people": 3}""").isEmpty())
    }

    @Test fun `a new line waits seconds for the node's natural voice before the phone's tts`() {
        assertTrue(Clips.WAIT_MS >= 5_000)
    }

    @Test fun `index parses the bridge format`() {
        val raw = """{"dober dan":{"female":"/voice/file/${"a".repeat(40)}.mp3","male":"/voice/file/${"b".repeat(40)}.mp3"},"kruh":{"female":"/voice/file/c.mp3"},"odd":{}}"""
        val index = Clips.parseIndex(raw)
        assertEquals("/voice/file/${"b".repeat(40)}.mp3", index["dober dan"]!!["male"])
        assertEquals(setOf("dober dan", "kruh"), index.keys)
    }

    @Test fun `a clip is found by normalized text and voice`() {
        val index = mapOf("dober dan" to mapOf("female" to "/voice/file/f.mp3"))
        assertEquals(listOf("/voice/file/f.mp3"), Clips.resolve(index, "Dober dan!", Clips.FEMALE))
        assertNull(Clips.resolve(index, "Dober dan!", Clips.MALE))
        assertNull(Clips.resolve(index, "Dober večer", Clips.FEMALE))
    }

    @Test fun `a slash text plays its phrases only when all have a clip`() {
        val index = mapOf("kako ste" to mapOf("female" to "/s.mp3"), "kako si" to mapOf("female" to "/i.mp3"))
        assertEquals(listOf("/s.mp3", "/i.mp3"), Clips.resolve(index, "Kako ste? / Kako si?", Clips.FEMALE))
        assertNull(Clips.resolve(index - "kako si", "Kako ste? / Kako si?", Clips.FEMALE))
        assertEquals(listOf("Kako ste?", "Kako si?"), Clips.parts("Kako ste? / Kako si?"))
        assertEquals(listOf("Dober dan!"), Clips.parts("Dober dan!"))
    }

    @Test fun `a villager's line waits for their voice rather than an old narrator clip`() {
        val index = mapOf("dober dan" to mapOf("female" to "/f.mp3"))
        val voices = Clips.chain("young-man", Clips.MALE)
        // the node is asked for Luka's voice first, though it has the narrator's clip
        var asked = 0
        assertEquals("/luka.mp3" to "young-man", Clips.pick(index, "dober dan", voices) { asked++; "/luka.mp3" to "young-man" })
        assertEquals(1, asked)
        // only when it can't make it does the narrator's clip stand in
        assertEquals("/f.mp3" to "female", Clips.pick(index, "dober dan", voices) { null })
        // their own clip, when the node has it, without asking
        val theirs = mapOf("dober dan" to mapOf("female" to "/f.mp3", "young-man" to "/y.mp3"))
        assertEquals("/y.mp3" to "young-man", Clips.pick(theirs, "dober dan", voices) { error("not asked") })
        assertNull(Clips.pick(emptyMap(), "dober dan", voices) { null })
    }

    @Test fun `lru evicts the oldest until the rest fits`() {
        val files = listOf(Triple("new", 40L, 300L), Triple("old", 30L, 100L), Triple("mid", 30L, 200L))
        assertEquals(setOf("old"), Clips.evict(files, 70))
        assertEquals(setOf("old", "mid"), Clips.evict(files, 40))
        assertTrue(Clips.evict(files, 100).isEmpty())
    }

    @Test fun `scenario voice picks the character's clip voice`() {
        val base = """{"id":"x","title":"t","setting":"s","role":"Natakar Marko, the waiter.","goals":["a","b","c"],"opener_sl":"Izvolite?","opener_en":"Yes?""""
        assertEquals(Clips.MALE, parseScenario("$base,\"voice\":\"male\"}").characterVoice)
        assertEquals(Clips.FEMALE, parseScenario("$base}").characterVoice)
        // a villager's role-play carries their speaker; the gender stays the fallback
        val luka = parseScenario("$base,\"voice\":\"male\",\"speaker\":\"young-man\"}")
        assertEquals("young-man", luka.characterVoice)
        assertEquals(Clips.MALE, luka.characterGender)
    }

    @Test fun `the index carries any speaker of the voice cast`() {
        val raw = """{"dober dan":{"female":"/voice/file/f.mp3","grandma":"/voice/file/g.mp3"},"ej živjo":{"young-man":"/voice/file/y.mp3"}}"""
        val index = Clips.parseIndex(raw)
        assertEquals("/voice/file/g.mp3", index["dober dan"]!!["grandma"])
        assertEquals(listOf("/voice/file/y.mp3"), Clips.resolve(index, "Ej, živjo", "young-man"))
    }

    @Test fun `a speaker falls back to the gender voice, then female`() {
        assertEquals(listOf("grandma", "female"), Clips.chain("grandma", "female"))
        assertEquals(listOf("boy", "male", "female"), Clips.chain("boy", "male"))
        assertEquals(listOf("male", "female"), Clips.chain("male"))
        assertEquals(listOf("female"), Clips.chain())
        val index = mapOf(
            "dober dan" to mapOf("female" to "/f.mp3", "male" to "/m.mp3", "grandma" to "/g.mp3"),
            "adijo" to mapOf("female" to "/fa.mp3", "male" to "/ma.mp3"),
            "hvala" to mapOf("female" to "/fh.mp3"),
        )
        assertEquals("grandma" to listOf("/g.mp3"), Clips.resolveFirst(index, "Dober dan!", Clips.chain("grandma", "female")))
        assertEquals("the gender before female", "male" to listOf("/ma.mp3"), Clips.resolveFirst(index, "Adijo!", Clips.chain("boy", "male")))
        assertEquals("then female", "female" to listOf("/fh.mp3"), Clips.resolveFirst(index, "Hvala", Clips.chain("boy", "male")))
        assertNull("nothing: the node, then TTS", Clips.resolveFirst(index, "Kje je pošta?", Clips.chain("boy", "male")))
    }

    @Test fun `voice_updated is an event`() =
        assertEquals(BridgeEvent.VoiceUpdated, BridgeEvent.parse("""{"type":"voice_updated","clips":3}"""))
}
