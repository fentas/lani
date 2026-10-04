package si.lanisce.lani.game.culture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.Res
import si.lanisce.lani.l10n.Lang
import java.io.File

/** The culture packs the app bundles (companion/cultures), and what the parser says about a broken one. */
class CulturePackTest {
    private val dir = listOf(File("../../cultures"), File("../cultures")).first { it.isDirectory }

    /** Pack [id]'s files as the repository has them: name → text ("chest.json", "readings/potica.json"). */
    private fun files(id: String): Map<String, String> =
        dir.resolve(id).listFiles { f -> f.name.endsWith(".json") }!!.associate { it.name to it.readText() } +
            dir.resolve(id).resolve("readings").listFiles { f -> f.name.endsWith(".json") }.orEmpty().associate { "readings/${it.name}" to it.readText() }

    private fun problems(id: String, files: Map<String, String>): List<String> = try {
        Cultures.parse(id) { files[it] }
        emptyList()
    } catch (e: CultureError) {
        e.problems
    }

    /** [files] with [name] changed by [edit] (on its JSON). */
    private fun edited(files: Map<String, String>, name: String, edit: (JsonObject) -> JsonElement): Map<String, String> =
        files + (name to edit(Json.parseToJsonElement(files.getValue(name)).jsonObject).toString())

    private fun JsonObject.with(key: String, v: JsonElement) = JsonObject(this + (key to v))

    @Test fun `every bundled pack is listed, and the default is complete`() {
        val onDisk = dir.listFiles { f -> f.resolve("culture.json").isFile }!!.map { it.name }.sorted()
        assertEquals(onDisk, Cultures.ids)
        assertTrue(Cultures.DEFAULT in onDisk)
        val c = Cultures.load(Cultures.DEFAULT)
        assertEquals(Lang.SL, c.language)
        assertEquals("complete", c.manifest.status)
        // the names of every resource, age, building and event, in the pack's language and in English
        for (r in Res.entries) assertTrue(r.name, c.name(r).has(Lang.SL) && c.name(r).has(Lang.EN))
        for (a in Age.entries) assertTrue(a.name, c.name(a).has(Lang.SL) && c.name(a).has(Lang.EN))
        for (t in BuildingType.entries) assertTrue(t.name, c.name(t).has(Lang.SL) && c.name(t).has(Lang.EN))
        // every project of the game has its words, every festival its good
        assertEquals(Catalog.projectFrames.map { it.id }, c.projects.map { it.id })
        assertTrue(c.festivals.all { f -> f.good == null || f.good in c.goods })
        assertSame("loaded once", c, Cultures.load(Cultures.DEFAULT))
    }

    @Test fun `every pack parses or is a stub, and a stub isn't played`() {
        for (id in Cultures.ids) {
            val m = checkNotNull(Cultures.manifest(id)) { id }
            assertEquals(id, m.id)
            if (m.status == "stub") {
                assertEquals(listOf("$id: a stub (only its manifest): nothing to play yet"), problems(id, files(id)))
            } else {
                assertEquals(id, emptyList<String>(), problems(id, files(id)))
            }
        }
        // the Italian town's pack is complete, and says who wrote its language
        val friuli = checkNotNull(Cultures.manifest("friuli"))
        assertEquals("it" to "complete", friuli.language to friuli.status)
        assertEquals("machine-written, not reviewed by a native speaker (the German translations, de, added by machine too)", friuli.review)
        // so is the Carinthian village's, in German
        val kaernten = checkNotNull(Cultures.manifest("kaernten"))
        assertEquals("de" to "complete", kaernten.language to kaernten.status)
        assertEquals("machine-written, not reviewed by a native speaker", kaernten.review)
        // so is the English village's
        val lakeland = checkNotNull(Cultures.manifest("lakeland"))
        assertEquals("en" to "complete", lakeland.language to lakeland.status)
        assertEquals("machine-written, not reviewed by a native speaker", lakeland.review)
        // Jan's pack says which of its translations a machine added
        assertEquals("the German and Italian translations (de, it), and the lines added for the times of day (evening greetings, daytime goodbyes, a child's bedtime line): machine-written, not reviewed by a native speaker", Cultures.manifest("primorska")?.review)
    }

    @Test fun `every villager who asks for help has a rotation of thank-you goods of their own, never one they like`() {
        for (id in Cultures.ids.filter { Cultures.manifest(it)?.status == "complete" }) {
            val c = Cultures.load(id)
            val chest = c.chest
            // everyone with a thanks good has a rotation, and every villager who makes requests has a thanks good
            assertEquals(id, chest.thanks.keys, chest.thanksRotation.keys)
            val givers = c.quests.requests.map { si.lanisce.lani.game.Quests.idOf(it.giver) }.toSet()
            assertTrue("$id: ${givers - chest.thanks.keys}", chest.thanks.keys.containsAll(givers))
            for ((who, r) in chest.thanksRotation) {
                val likes = chest.likes[who].orEmpty()
                assertTrue("$id $who thanks with a good they like: ${r.goods.filter { it in likes }}", r.goods.none { it in likes })
                assertTrue("$id $who: ${r.goods}", r.goods.size in 2..4 && r.goods.first() == chest.thanks[who])
            }
            // the grandmother has a treat for the year's feasts: at Carnival, at Easter and at Christmas
            val granny = chest.thanksRotation.values.maxBy { it.seasonal.size }
            assertTrue("$id: ${granny.seasonal}", granny.seasonal.size >= 3)
        }
        // Babica Micka: potica, bread and socks she knitted; krofi at pust, pisanice at Easter, medenjaki at St Nicholas, poprtnik at Christmas
        val micka = Cultures.load("primorska").chest.thanksRotation.getValue("micka")
        assertEquals(listOf("potica", "kruh", "nogavice"), micka.goods)
        assertEquals(mapOf("pust" to "krofi", "velika_noc" to "pisanice", "miklavz" to "medenjaki", "bozic" to "poprtnik"), micka.seasonal)
    }

    @Test fun `a pack asked for that can't be used leaves the default, and says why`() {
        val heard = ArrayList<String>()
        Cultures.onProblem = { heard += it.message.orEmpty() }
        val stub = files("friuli") + ("culture.json" to """{"schema": "lani.culture/v0", "id": "friuli", "language": "it", "status": "stub", "region": {"it": "Friuli"}, "name": {"it": "Friuli"}}""")
        try {
            assertEquals(listOf("friuli: a stub (only its manifest): nothing to play yet"), problems("friuli", stub))
            assertEquals(Cultures.DEFAULT, Cultures.use("atlantis").id)
            assertEquals(Cultures.DEFAULT, Cultures.current.id)
            assertTrue(heard.toString(), heard.size == 1 && "atlantis/culture.json: missing" in heard[0])
            assertEquals("friuli", Cultures.use("friuli").id)
        } finally {
            Cultures.onProblem = null
            Cultures.use(Cultures.DEFAULT)
        }
    }

    @Test fun `a pack written before the rename (fluent schema ids) still loads`() {
        val good = files(Cultures.DEFAULT)
        val legacy = edited(good, "culture.json") { it.with("schema", JsonPrimitive("fluent.culture/v0")) }
            .let { f -> if ("sky.json" in f) edited(f, "sky.json") { it.with("schema", JsonPrimitive("fluent.sky/v0")) } else f }
        assertEquals(emptyList<String>(), problems(Cultures.DEFAULT, legacy))
        val other = edited(good, "culture.json") { it.with("schema", JsonPrimitive("other.culture/v0")) }
        assertTrue(problems(Cultures.DEFAULT, other).toString(), problems(Cultures.DEFAULT, other).any { "schema" in it })
    }

    @Test fun `a broken pack says what is wrong, with the file and where in it`() {
        val good = files(Cultures.DEFAULT)
        fun p(files: Map<String, String>) = problems(Cultures.DEFAULT, files)

        assertEquals(listOf("primorska/people.json: missing"), p(good - "people.json"))
        assertTrue(p(good + ("quests.json" to "{ \"requests\": [")).single().startsWith("primorska/quests.json: "))
        // a field the format doesn't have (a typo) is one too
        val typo = edited(good, "events.json") { it.with("titel", JsonPrimitive("x")) }
        assertTrue(p(typo).toString(), p(typo).single().let { "primorska/events.json" in it && "titel" in it })

        // a text without the pack's language, a message that uses what it isn't given
        val quests = edited(good, "quests.json") { q ->
            val requests = q.getValue("requests").jsonArray
            val first = requests[0].jsonObject.with("title", JsonObject(mapOf("en" to JsonPrimitive("Micka's kitchen"))))
            val lines = q.getValue("lines").jsonObject.with("asks", JsonObject(mapOf("sl" to JsonPrimitive("{nme} te prosi"), "en" to JsonPrimitive("asks {giver}"))))
            q.with("requests", JsonArray(listOf(first) + requests.drop(1))).with("lines", lines)
        }
        assertEquals(
            listOf(
                "primorska/quests.json requests[0].title: no \"sl\" text (the pack's language)",
                "primorska/quests.json lines.asks.sl: uses {nme}, which it isn't given (it gets {giver})",
            ),
            p(quests),
        )

        // goods that aren't there, a price and an effect the game doesn't have
        val chest = edited(good, "chest.json") { c ->
            val goods = c.getValue("goods").jsonArray
            c.with("goods", JsonArray(listOf(goods[0].jsonObject.with("price", JsonPrimitive("dear"))) + goods.drop(1)))
                .with("thanks_default", JsonPrimitive("gubana"))
                .with("tools", JsonArray(c.getValue("tools").jsonArray.mapIndexed { i, t -> if (i == 0) t.jsonObject.with("effect", JsonPrimitive("magic")) else t }))
        }
        val cp = p(chest)
        assertTrue(cp.toString(), "primorska/chest.json goods[0].price: \"dear\", not one of small, plain, good, fine, rich, precious" in cp)
        assertTrue(cp.toString(), "primorska/chest.json thanks_default: no good \"gubana\" in chest.json" in cp)
        assertTrue(cp.toString(), cp.any { it.startsWith("primorska/chest.json tools[0].effect: \"magic\"") })

        // a festival's date, a project the game doesn't have, a trade that needs no building of the game's
        val festivals = edited(good, "festivals.json") { f ->
            val list = f.getValue("festivals").jsonArray
            f.with("festivals", JsonArray(listOf(list[0].jsonObject.with("date", JsonObject(mapOf("month" to JsonPrimitive(1))))) + list.drop(1)))
        }
        assertTrue(p(festivals).toString(), p(festivals).single().startsWith("primorska/festivals.json festivals[0].date: one of"))
        val projects = edited(good, "projects.json") { f ->
            val list = f.getValue("projects").jsonArray
            f.with("projects", JsonArray(listOf(list[0].jsonObject.with("id", JsonPrimitive("castle"))) + list.drop(1)))
        }
        assertTrue(p(projects).toString(), p(projects).single().startsWith("primorska/projects.json projects[0].id: \"castle\" isn't one of the game's projects"))

        // the manifest: which pack, which language
        val manifest = edited(good, "culture.json") { it.with("id", JsonPrimitive("elsewhere")).with("language", JsonPrimitive("xx")) }
        assertEquals(
            listOf(
                "primorska/culture.json id: \"elsewhere\", but the pack is \"primorska\"",
                "primorska/culture.json language: \"xx\" isn't one of the app's (sl, en, it, de)",
            ),
            p(manifest),
        )
        try {
            Cultures.parse("primorska") { manifest[it] }
            fail("a broken pack throws")
        } catch (e: CultureError) {
            assertTrue(e.message!!.startsWith("culture pack \"primorska\": "))
        }
    }

    @Test fun `a text shows in the language asked for, else English, else the pack's`() {
        val t = Text(mapOf("it" to "Ciao {name}", "sl" to "Živjo {name}"))
        assertEquals("Živjo Ana", t.of(Lang.SL, mapOf("name" to "Ana")))
        assertEquals("Ciao Ana", t.of(Lang.DE, mapOf("name" to "Ana")))
        assertEquals("Hi Ana", Text(mapOf("it" to "Ciao {name}", "en" to "Hi {name}")).of(Lang.DE, mapOf("name" to "Ana")))
        // a Localized argument reads in the message's language
        val age = Text(mapOf("sl" to "Zaselek", "en" to "Hamlet"))
        val msg = Text(mapOf("sl" to "Potrebuje {age}", "en" to "Needs {age}"))
        assertEquals("Potrebuje Zaselek", msg.of(Lang.SL, mapOf("age" to age)))
        assertEquals("Needs Hamlet", msg.of(Lang.EN, mapOf("age" to age)))
        assertEquals("the maypole", Culture.over(Text(mapOf("sl" to "Mlaj", "en" to "The maypole")), Text(mapOf("en" to "the maypole"))).of(Lang.EN))
        assertEquals("Mlaj", Culture.over(Text(mapOf("sl" to "Mlaj", "en" to "The maypole")), Text(mapOf("en" to "the maypole"))).of(Lang.SL))
    }
}
