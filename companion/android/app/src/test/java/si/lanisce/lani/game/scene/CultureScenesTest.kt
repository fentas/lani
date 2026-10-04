package si.lanisce.lani.game.scene

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.scene.DialogRun
import java.io.File

/**
 * The culture packs' own places (companion/cultures/<id>/scenes): Primorska's mountains at the horizon and its vineyard
 * (from the vineyard project's terraces) in Slovene, Friuli's sea at the horizon in Italian, Lakeland's fell in English;
 * the app reads a scene in the learner's pair (an Italian scene for the second learner: what is said in Italian, what it means in
 * Slovene), and the horizon's name is the village's culture's.
 */
class CultureScenesTest {
    private val cultures = listOf(File("../../cultures"), File("../cultures"), File("companion/cultures")).first { it.isDirectory }
    private val pair = L10n.pair

    @After fun restore() {
        L10n.pair = pair
        Cultures.use(null as String?)
    }

    private fun raw(culture: String, id: String) = File(cultures, "$culture/scenes/$id.json").readText()

    /** [id] of [culture] as a learner of [p] reads it. */
    private fun scene(culture: String, id: String, p: LangPair): SceneSpec {
        L10n.pair = p
        return parseScene(raw(culture, id))
    }

    private val jan = LangPair(Lang.SL, Lang.EN)
    private val second = LangPair(Lang.IT, Lang.SL)

    @Test fun `each culture's scene opens from its place and uses only what its painter draws`() {
        for ((culture, id, art, from) in listOf(
            listOf("primorska", "v-gorah", "alps", "spot:${TownSpots.HORIZON}"),
            listOf("primorska", "na-gricih", "hills", "project:vinograd"),
            listOf("friuli", "il-mare", "sea", "spot:${TownSpots.HORIZON}"),
            listOf("kaernten", "am-see", "alps", "spot:${TownSpots.HORIZON}"),
            listOf("lakeland", "on-the-fell", "alps", "spot:${TownSpots.HORIZON}"),
        )) {
            val s = scene(culture, id, when (culture) { "friuli" -> second; "kaernten" -> LangPair(Lang.DE, Lang.SL); "lakeland" -> LangPair(Lang.EN, Lang.SL); else -> jan })
            assertEquals(art, s.art)
            assertEquals(listOf(from), s.from)
            assertTrue("$id: every slot of the art, once", s.objects.map { it.slot }.sorted() == SceneArt.objects.getValue(art).sorted())
            assertTrue("$id: spots", s.people.all { it.slot in SceneArt.personSlots.getValue(art) && it.art in SceneArt.people && it.villager != null })
            val used = s.dialogs.flatMap { d -> d.lines.flatMap { l -> l.fx.keys + l.choices.flatMap { it.reply?.fx.orEmpty().keys } } }.toSet()
            assertEquals("$id: every effect of the art is used", SceneArt.effects.getValue(art).toSet(), used)
            for (h in s.happenings) assertTrue("$id/${h.id}", h.who in s.people.map { it.id } && s.dialogs.any { it.id == h.dialog } && h.memory != null)
        }
    }

    /**
     * [e] as Jan reads it: the lines' translations for other bases (German, Italian) left out, a wrong choice's `why` its
     * English, and, in a scene that names no grammar page ([guessing]), the page it tests guessed from that
     * ([si.lanisce.lani.game.Forms.rule]); the titles as they are (labels, read in the pair when parsed).
     */
    private fun janOnly(e: JsonElement, guessing: Boolean = false): JsonElement = when (e) {
        is JsonArray -> JsonArray(e.map { janOnly(it, guessing) })
        is JsonObject -> {
            val o = JsonObject(e.filterKeys { it != "de" && it != "it" }.mapValues { (k, v) ->
                when {
                    k == "title" -> v
                    k == "why" && v is JsonObject -> v.getValue("en")
                    else -> janOnly(v, guessing)
                }
            })
            val why = (o["why"] as? JsonPrimitive)?.content
            val rule = if (guessing && "why" in o && (o["ok"] as? JsonPrimitive)?.content != "true") si.lanisce.lani.game.Forms.rule(why) else null
            rule?.let { JsonObject(o + ("guess" to JsonPrimitive(it))) } ?: o
        }
        else -> e
    }

    /** A file as the app reads it: its variants (companion/SCENES.md, "Variants") among its dialogs, as the bridge serves them. */
    private fun served(o: JsonObject): JsonObject = (o["variants"] as? JsonArray)?.let { v ->
        JsonObject(o.filterKeys { it != "variants" } + ("dialogs" to JsonArray((o["dialogs"] as? JsonArray).orEmpty() + v)))
    } ?: o

    /** Whether the scene [o] names no grammar page on any turn or choice: then the app guesses them. */
    private fun names(o: JsonObject): Boolean = "\"grammar\"" in o["dialogs"].toString()

    private fun janOnly(o: JsonObject): JsonElement = janOnly(o, guessing = !names(o))

    @Test fun `a Slovene scene reads for Jan as its file has it`() {
        val o = json.parseToJsonElement(raw("primorska", "na-gricih")).jsonObject
        assertEquals(janOnly(served(o)), inPair(o, jan))
        val s = scene("primorska", "na-gricih", jan)
        assertEquals("Na gričih · On the hills", s.title)
        assertEquals("Ciao! Si prvič na gričih?", s.dialogs.first().lines.first().sl)
        val o2 = json.parseToJsonElement(raw("primorska", "v-gorah")).jsonObject
        assertEquals(janOnly(served(o2)), inPair(o2, jan))
        val m = scene("primorska", "v-gorah", jan)
        assertEquals("V gorah · In the mountains", m.title)
        assertEquals("Dober dan! Si prvič v gorah?", m.dialogs.first { it.id == "prvic" }.lines.first().sl)
    }

    @Test fun `each culture's first-visit dialog is for guests - a visitor meets it, the village's own learner never`() {
        for ((culture, id, happening) in listOf(listOf("primorska", "v-gorah", "prvic"), listOf("primorska", "na-gricih", "prvic"), listOf("friuli", "il-mare", "fuori"), listOf("kaernten", "am-see", "erstes-mal"), listOf("lakeland", "on-the-fell", "first-time"))) {
            val s = scene(culture, id, jan)
            val h = s.happenings.first { it.id == happening }
            assertTrue("$id/$happening is for guests", h.guests && Happenings.forGuests(s, h))
            assertTrue("$id: the others are for everyone", s.happenings.filter { it.id != happening }.none { Happenings.forGuests(s, it) })
            // said to anyone: no learner's name in it
            assertTrue("$id/$happening names nobody", s.dialogs.first { it.id == h.dialog }.lines.none { "Jan" in it.sl.orEmpty() || "Jan" in it.en.orEmpty() })
        }
    }

    @Test fun `the second learner reads the sea in Italian, meant in Slovene`() {
        val s = scene("friuli", "il-mare", second)
        assertEquals("it", s.language)
        assertEquals("Il mare · Morje", s.title)
        val fuori = s.dialogs.first { it.id == "fuori" }
        assertEquals("Ciao, ciao! Sei di fuori? Da dove vieni?", fuori.lines[0].sl)
        assertEquals("Živjo, živjo! Nisi od tod? Od kod prihajaš?", fuori.lines[0].en)
        val turn = fuori.lines[1]
        assertEquals("Vengo dal Collio. Sono in vacanza!", turn.choices[0].sl)
        assertEquals("Iz Brd! Potem pa dobrodošel v Gradežu!", turn.choices[0].reply?.en)
        assertEquals("Collio ima člen: da + il = dal Collio.", turn.choices[1].why)
        val h = s.happenings.first { it.id == "fuori" }
        assertEquals("Zia Nives sul molo · Teta Nives na pomolu", h.title)
        assertEquals("i gabbiani che abbiamo guardato dal molo", h.memory?.sl)
        assertEquals("galebe, ki sva jih gledala s pomola", h.memory?.en)
        // Jan visiting some day: still Italian, meant in English
        val visit = scene("friuli", "il-mare", LangPair(Lang.SL, Lang.EN))
        assertEquals("Ciao, ciao! Sei di fuori? Da dove vieni?", visit.dialogs.first().lines[0].sl)
        assertEquals("Hello, hello! You are not from here? Where do you come from?", visit.dialogs.first().lines[0].en)
        assertEquals("Collio takes the article: da + il = dal Collio.", visit.dialogs.first().lines[1].choices[1].why)
    }

    @Test fun `a scene read once reads the same again (the app keeps what it got)`() {
        L10n.pair = second
        // as the bridge serves it: the gull resolved from the pack, in every language the word has
        val o = json.parseToJsonElement(raw("friuli", "il-mare")).jsonObject
        val gullWord = mapOf(
            "pack" to "il-mare", "it" to "il gabbiano", "sl" to "galeb", "en" to "gull",
            "example_it" to "Il gabbiano vola sopra il mare.", "example_sl" to "Galeb leti nad morjem.", "example_en" to "The gull flies over the sea.",
        ).mapValues { JsonPrimitive(it.value) }
        val objects = o.getValue("objects").jsonArray.map { e -> e.jsonObject.let { if (it["slot"]?.jsonPrimitive?.content == "gull") JsonObject(it + gullWord) else it } }
        val once = parseScenes(JsonArray(listOf(JsonObject(o + ("objects" to JsonArray(objects))))).toString())
        val gull = once.first().objects.first { it.slot == "gull" }
        assertEquals("il gabbiano", gull.sl)
        assertEquals("galeb", gull.en)
        assertEquals("Il gabbiano vola sopra il mare.", gull.exampleSl)
        assertEquals("Galeb leti nad morjem.", gull.exampleEn)
        val again = parseScenes(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SceneSpec.serializer()), once))
        assertEquals(once, again)
    }

    @Test fun `a learner of English reads the fell in English, meant in their base, and the same again`() {
        val fromDe = LangPair(Lang.EN, Lang.DE)
        val file = json.parseToJsonElement(raw("lakeland", "on-the-fell")).jsonObject
        val s = scene("lakeland", "on-the-fell", fromDe)
        assertEquals("en", s.language)
        assertEquals("en-de", s.readIn)
        val line = file.getValue("dialogs").jsonArray.first().jsonObject.getValue("lines").jsonArray.first().jsonObject
        val first = s.dialogs.first().lines.first()
        assertEquals(line.getValue("en").jsonPrimitive.content to line.getValue("de").jsonPrimitive.content, first.sl to first.en)
        // the app keeps the scenes it got and reads them again: English stays what is said (its "en" holds the German now)
        val again = parseScenes(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SceneSpec.serializer()), listOf(s))).single()
        assertEquals(s, again)
        assertEquals(line.getValue("en").jsonPrimitive.content, again.dialogs.first().lines.first().sl)
    }

    /** [dialog] of [scene] played with the right choices, every step from its first line to its end. */
    private fun play(s: SceneSpec, dialog: String): List<DialogRun> {
        var run = DialogRun.start(s.dialogs.first { it.id == dialog }, null)
        val out = arrayListOf(run)
        while (run.step != DialogRun.Step.END) {
            run = if (run.step == DialogRun.Step.CHOOSE) run.choose(run.choices.indexOfFirst { it.ok }) else run.next()
            out += run
        }
        return out
    }

    @Test fun `the dialogs bring the weather and the place joins in`() {
        val hills = scene("primorska", "na-gricih", jan)
        assertEquals(1f, play(hills, "prvic").last().fx(emptyMap())["starlings"])
        val storm = play(hills, "nevihta")
        assertTrue(storm.any { it.fx(emptyMap())["bell"] == 1f })
        assertTrue(storm.last().sky(Sky.CLEAR).rain >= 0.6f && storm.last().sky(Sky.CLEAR).gloom >= 0.6f)
        assertEquals(1f, play(hills, "traktor").last().fx(emptyMap())["tractor"])
        // in the mountains: the choughs off the peak and a storm over Triglav; the cows' bells; the morning fog that lifts
        // with the church bell, and a fish jumping
        val mountains = scene("primorska", "v-gorah", jan)
        val triglav = play(mountains, "prvic")
        assertTrue(triglav.any { it.fx(emptyMap())["birds"] == 1f } && triglav.any { it.sky(Sky.CLEAR).lightning })
        assertTrue(triglav.last().sky(Sky.CLEAR).rain >= 0.6f)
        assertEquals(1f, play(mountains, "planina").first().fx(emptyMap())["cowbells"])
        val fish = play(mountains, "ribe")
        assertTrue("morning fog", fish.first().sky(Sky.CLEAR).fog >= 0.5f)
        assertTrue(fish.any { it.fx(emptyMap())["bell"] == 1f } && fish.any { it.fx(emptyMap())["fish"] == 1f })
        assertEquals("the fog lifts", 0f, fish.last().sky(Sky.CLEAR).fog)
        assertTrue(!mountains.dialogs.first { it.id == "ribe" }.skyKept)
        val sea = scene("friuli", "il-mare", second)
        assertEquals(1f, play(sea, "fuori").last().fx(emptyMap())["gulls"])
        val squall = play(sea, "onde")
        assertTrue(squall.any { it.sky(Sky.CLEAR).lightning } && squall.last().sky(Sky.CLEAR).rain >= 0.6f)
        val sail = play(sea, "vela")
        assertTrue("sea mist, lifting", sail.any { it.sky(Sky.CLEAR).fog >= 0.5f } && sail.last().sky(Sky.CLEAR).fog == 0f)
        assertEquals(1f, sail.last().fx(emptyMap())["sail"])
        assertTrue(sea.dialogs.first { it.id == "vela" }.fxKept && !sea.dialogs.first { it.id == "vela" }.skyKept)
    }

    @Test fun `the horizon is named for what the village's culture has there`() {
        L10n.pair = jan
        assertEquals("Gore · The mountains", TownSpots.info(TownSpots.HORIZON).label)
        assertEquals("v gorah · in the mountains", TownSpots.info(TownSpots.HORIZON).where)
        assertEquals("🏔️", TownSpots.info(TownSpots.HORIZON).emoji)
        assertEquals(false, Cultures.current.world.backdrop?.sea)
        Cultures.use("friuli")
        L10n.pair = second
        assertEquals("Il mare · Morje", TownSpots.info(TownSpots.HORIZON).label)
        assertEquals("🌊", TownSpots.info(TownSpots.HORIZON).emoji)
        assertEquals(true, Cultures.current.world.backdrop?.sea)
        assertNotNull(Cultures.current.world.backdrop?.about)
    }
}
