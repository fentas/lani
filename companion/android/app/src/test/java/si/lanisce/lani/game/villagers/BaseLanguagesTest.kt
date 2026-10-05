package si.lanisce.lani.game.villagers

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.data.parsePack
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Learner
import java.io.File

/**
 * The curated content explained in every base language: a learner of Slovene from German (sl · de) in a Primorska village
 * and a learner of Italian from German (it · de) in a Friuli one read German under what is said: in a villager's bubble,
 * their role and likes, a word pack's words, the culture pack's texts and a scene's lines. What is said stays in the
 * village's language whatever translations a line has, and Jan (sl · en) reads English as before.
 */
class BaseLanguagesTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { it.resolve("cultures").isDirectory }
    private val pair = L10n.pair

    @After fun back() {
        L10n.pair = pair
        Cultures.use(null as String?)
    }

    private fun text(path: String) = companion.resolve(path).readText()
    /** [path] as the app reads it: said to the learner ({learner}, {m:…|f:…}: l10n/Learner.kt). */
    private fun raw(path: String) = json.parseToJsonElement(Learner.current.renderJson(text(path))).jsonObject
    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content
    private fun JsonObject.obj(k: String) = getValue(k).jsonObject

    private val jan = LangPair(Lang.SL, Lang.EN)
    private val slDe = LangPair(Lang.SL, Lang.DE)
    private val itDe = LangPair(Lang.IT, Lang.DE)

    /** Every villager of [culture], each line said in [pair]'s target and translated into its base; the first villager. */
    private fun cast(culture: String, pair: LangPair): List<Villager> {
        L10n.pair = pair
        val files = companion.resolve("cultures/$culture/villagers").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        val cast = parseVillagers(files.joinToString(",", "[", "]") { it.readText() })
        for (v in cast) {
            val lines = with(v.lines) { greet + thanks + remember + idle + cheer + comfort + listen + bye + gift.liked + gift.ordinary + gift.rare }
            for (l in lines) {
                assertEquals("${v.id}: said in the village's language", l.by.getValue(pair.target.code), l.target)
                assertEquals("${v.id}: translated into German", l.by.getValue("de"), l.base)
                assertNotEquals("${v.id}: a German translation, not the English: ${l.by}", l.by["en"], l.base)
            }
        }
        return cast
    }

    @Test fun `a Slovene village for a learner from German`() {
        val micka = cast("primorska", slDe).first { it.id == "micka" }
        val file = raw("cultures/primorska/villagers/micka.json")
        val hi = file.obj("lines").getValue("greet").jsonArray[0].jsonObject
        assertEquals(hi.str("sl") to hi.str("de"), micka.lines.greet[0].target to micka.lines.greet[0].base)
        assertEquals("${file.obj("role").str("sl")} · ${file.obj("role").str("de")}", micka.role)
        val like = file.getValue("likes").jsonArray[0].jsonObject
        assertEquals("${like.str("sl")} · ${like.str("de")}", micka.likes[0])

        // a word pack: its words' meanings, examples and notes, its title and description
        val pack = parsePack(text("packs/druzina.json"))
        val oce = pack.words.first { it.id == "oce" }
        assertEquals(listOf("oče", oce.de, oce.exampleDe, oce.noteDe), listOf(oce.word, oce.meaning, oce.exampleMeaning, oce.noteText))
        assertTrue(oce.de != null && oce.de != oce.en)
        val title = raw("packs/druzina.json").obj("title")
        assertEquals("Družina · ${title.str("de")}", pack.title)
        assertEquals(raw("packs/druzina.json").obj("description").str("de"), pack.description)

        // the culture pack's texts: a request, a good as a gift
        val c = Cultures.use("primorska")
        val request = c.quests.requests.first()
        assertEquals(request.title.by.getValue("sl") to request.title.by.getValue("de"), request.title.target to request.title.base)

        // a scene's line: said in Slovene, meant in German
        val scene = parseScene(text("cultures/primorska/scenes/na-gricih.json"))
        val first = raw("cultures/primorska/scenes/na-gricih.json").getValue("dialogs").jsonArray[0].jsonObject.getValue("lines").jsonArray[0].jsonObject
        assertEquals(first.str("sl") to first.str("de"), scene.dialogs[0].lines[0].sl to scene.dialogs[0].lines[0].en)
    }

    @Test fun `a learner of Slovene from Italian reads Italian, and Jan English as ever`() {
        L10n.pair = LangPair(Lang.SL, Lang.IT)
        val micka = parseVillagers("[${text("cultures/primorska/villagers/micka.json")}]").single()
        val hi = raw("cultures/primorska/villagers/micka.json").obj("lines").getValue("greet").jsonArray[0].jsonObject
        assertEquals(hi.str("sl") to hi.str("it"), micka.lines.greet[0].target to micka.lines.greet[0].base)
        val oce = parsePack(text("packs/druzina.json")).words.first { w -> w.id == "oce" }
        assertEquals(oce.it, oce.meaning)
        L10n.pair = jan
        val again = parseVillagers("[${text("cultures/primorska/villagers/micka.json")}]").single()
        assertEquals(hi.str("sl") to hi.str("en"), again.lines.greet[0].target to again.lines.greet[0].base)
        assertEquals("Babica · Grandmother", again.role)
        assertEquals("Družina · Family", parsePack(text("packs/druzina.json")).title)
        // a visitor learning Italian from German in Jan's town hears Slovene, whatever translations the line has
        assertEquals(hi.str("sl"), again.lines.greet[0].target(slDe))
    }

    @Test fun `an Italian village for a learner from German`() {
        val rosa = cast("friuli", itDe).first { it.id == "rosa" }
        val file = raw("cultures/friuli/villagers/rosa.json")
        val hi = file.obj("lines").getValue("greet").jsonArray[0].jsonObject
        assertEquals(hi.str("it") to hi.str("de"), rosa.lines.greet[0].target to rosa.lines.greet[0].base)
        assertEquals("Nonna · ${file.obj("role").str("de")}", rosa.role)

        val pack = parsePack(text("packs/it/famiglia.json"))
        val mamma = pack.words.first { it.id == "mamma" }
        assertEquals(listOf("la mamma", mamma.de, mamma.exampleDe), listOf(mamma.word, mamma.meaning, mamma.exampleMeaning))
        val papa = pack.words.first { it.id == "papa" }
        assertEquals(papa.noteDe, papa.noteText)
        assertEquals("La famiglia · ${raw("packs/it/famiglia.json").obj("title").str("de")}", pack.title)

        val c = Cultures.use("friuli")
        val request = c.quests.requests.first()
        assertEquals(request.title.by.getValue("it") to request.title.by.getValue("de"), request.title.target to request.title.base)

        val scene = parseScene(text("cultures/friuli/scenes/il-mare.json"))
        val first = raw("cultures/friuli/scenes/il-mare.json").getValue("dialogs").jsonArray[0].jsonObject.getValue("lines").jsonArray[0].jsonObject
        assertEquals(first.str("it") to first.str("de"), scene.dialogs[0].lines[0].sl to scene.dialogs[0].lines[0].en)
    }
}
