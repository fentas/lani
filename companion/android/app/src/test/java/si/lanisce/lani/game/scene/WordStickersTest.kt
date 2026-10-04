package si.lanisce.lani.game.scene

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.PackSession
import si.lanisce.lani.data.json
import java.io.File

/** The curated scenes and the packs, as the files have them (companion/scenes, companion/cultures/<id>/scenes, the packs). */
internal object CuratedContent {
    val companion: File = listOf(File("../.."), File(".."), File("companion")).first { it.resolve("scenes").isDirectory }

    /** Every scene, the curated ones first and then the culture packs' (as a village of their culture gets them). */
    val scenes: List<SceneSpec> by lazy {
        val files = companion.resolve("scenes").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name } +
            companion.resolve("cultures").listFiles().orEmpty().sortedBy { it.name }
                .flatMap { it.resolve("scenes").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { f -> f.name } }
        files.map { parseScene(it.readText()) }
    }

    /** "pack/word" → the word's emoji, of every pack (Jan's, the Italian ones, the culture packs'). */
    val emojis: Map<String, String?> by lazy {
        val dirs = listOf(companion.resolve("packs"), companion.resolve("packs/it")) + companion.resolve("cultures").listFiles().orEmpty().map { it.resolve("packs") }
        dirs.flatMap { it.listFiles { f -> f.extension == "json" }.orEmpty().toList() }.flatMap { f ->
            val p = json.parseToJsonElement(f.readText()).jsonObject
            val id = p.getValue("id").jsonPrimitive.content
            p.getValue("words").jsonArray.map { w ->
                val o = w.jsonObject
                WordStickers.key(id, o.getValue("id").jsonPrimitive.content) to o["emoji"]?.jsonPrimitive?.content
            }
        }.toMap()
    }
}

/**
 * The words' stickers: which pack word a scene draws where, read from the scenes (no list of things), and which cards
 * show the sticker instead of the emoji (the words no emoji shows).
 */
class WordStickersTest {
    private val index = WordStickers.of(CuratedContent.scenes)

    /** The audit's words that a scene draws, and where. */
    private val drawn = mapOf(
        "na-njivi/strasilo" to StickerSpot("field", "scarecrow"),
        "na-njivi/kosa" to StickerSpot("field", "scythe"),
        "na-njivi/grablje" to StickerSpot("field", "rake"),
        "na-njivi/mlin" to StickerSpot("stream", "mill"),
        "v-kovacnici/nakovalo" to StickerSpot("smithy", "anvil"),
        "v-kovacnici/podkev" to StickerSpot("smithy", "horseshoe"),
        "v-kovacnici/klesce" to StickerSpot("smithy", "tongs"),
        "v-kovacnici/meh" to StickerSpot("smithy", "bellows"),
        "v-kuhinji/miza" to StickerSpot("kitchen", "table"),
        "babicina-kuhinja/stedilnik" to StickerSpot("kitchen", "stove"),
        // the square's fence: the scene of its own pack, not the field's
        "na-vasi/klop" to StickerSpot("square", "bench"),
        "na-vasi/ograja" to StickerSpot("square", "fence"),
        // the stream's dragonfly, the first scene of its pack that draws one
        "ob-vodi/kacji-pastir" to StickerSpot("stream", "dragonfly"),
        "ob-vodi/caplja" to StickerSpot("pond", "heron"),
        "ob-vodi/pomol" to StickerSpot("pond", "jetty"),
        "il-mare/gabbiano" to StickerSpot("sea", "gull"),
        "il-mare/molo" to StickerSpot("sea", "pier"),
        "v-soli/kreda" to StickerSpot("school", "chalk"),
        "v-sotoru/spalna-vreca" to StickerSpot("tent", "sleepingbag"),
        // the church and the bee house (Bee Day's hive, bee house and comb too)
        "v-cerkvi/oltar" to StickerSpot("church", "altar"),
        "v-cerkvi/orgle" to StickerSpot("church", "organ"),
        "v-cerkvi/kropilnik" to StickerSpot("church", "font"),
        "pri-cebelnjaku/koncnica" to StickerSpot("apiary", "panel"),
        "pri-cebelnjaku/kadilnik" to StickerSpot("apiary", "smoker"),
        "pri-cebelnjaku/tocilo" to StickerSpot("apiary", "extractor"),
        "pri-cebelnjaku/vosek" to StickerSpot("apiary", "wax"),
        "praznik-dan-cebel/panj" to StickerSpot("apiary", "hive"),
        "praznik-dan-cebel/cebelnjak" to StickerSpot("apiary", "beehouse"),
        "praznik-dan-cebel/satje" to StickerSpot("apiary", "comb"),
        // the market's stall and weights, the watchtower's torch and crow
        "trzni-dan/stojnica" to StickerSpot("market", "stall"),
        "trzni-dan/utez" to StickerSpot("market", "weights"),
        "na-stolpu/bakla" to StickerSpot("watchtower", "torch"),
        "na-stolpu/vrana" to StickerSpot("watchtower", "crow"),
        // the hunter's high seat, the dormouse, the chamois and the ibex
        "na-prezi/preza" to StickerSpot("forest", "highseat"),
        "na-prezi/polh" to StickerSpot("forest", "dormouse"),
        "v-gorah/gams" to StickerSpot("alps", "chamois"),
        "v-gorah/kozorog" to StickerSpot("alps", "ibex"),
        // the stairs down into the cellar (and up into the attic): the first room that has them
        "dom-in-hisa/stopnice" to StickerSpot("cellar", "stairs"),
    )

    /** The audit's words no scene draws yet: candidates for drawing. */
    private val undrawn = listOf(
        "v-kovacnici/plug", "dom-in-hisa/soba", "dom-in-hisa/tla",
        "praznik-martinovo/klet", "na-gricih/klet", "v-soli/radirka", "telo-in-zdravje/hrbet", "babicina-kuhinja/pecica",
        "babicina-kuhinja/hladilnik", "babicina-kuhinja/oreh", "v-gozdu/jurcek", "v-sotoru/vzigalice", "praznik-miklavz/siba",
    )

    private fun split(k: String) = k.substringBefore('/') to k.substringAfter('/')

    @Test fun `every word of the audit that a scene draws has its sticker, and the card shows it`() {
        for ((k, spot) in drawn) {
            val (pack, word) = split(k)
            assertEquals(k, spot, index.spot(pack, word))
            assertEquals("$k shows its sticker", spot, index.picture(pack, word, CuratedContent.emojis[k]))
            assertEquals("$k as a review item", spot, index.ofItem(PackSession.itemId(pack, word), CuratedContent.emojis[k]))
        }
        for (k in undrawn) {
            val (pack, word) = split(k)
            assertNull("$k: no scene draws it", index.spot(pack, word))
        }
        assertEquals("the audit's words, drawn or not", WordStickers.STAND_INS - "casa-e-cucina/tavolo" - "casa-e-cucina/fornello" -
            "casa-e-cucina/frigorifero" - "festa-san-martino/cantina" - "festa-san-nicolo/noce", (drawn.keys + undrawn).toSet())
    }

    @Test fun `every stand-in is a word of a pack, and it has the pack's emoji or one that is not the thing`() {
        for (k in WordStickers.STAND_INS) assertTrue("$k is a pack word", k in CuratedContent.emojis)
    }

    @Test fun `an emoji that shows the word stays`() {
        // the axe 🪓, the fire 🔥, the bread 🍞: drawn by the scenes, but the emoji is the thing itself
        for (k in listOf("ob-ognju/sekira", "ob-ognju/ogenj", "v-kuhinji/kruh", "v-gozdu/sova")) {
            val (pack, word) = split(k)
            assertTrue("$k is drawn", index.spot(pack, word) != null)
            assertNull("$k keeps its emoji ${CuratedContent.emojis[k]}", index.picture(pack, word, CuratedContent.emojis[k]))
        }
        // a word without an emoji takes the sticker
        assertEquals(StickerSpot("campfire", "axe"), index.picture("ob-ognju", "sekira", null))
    }

    @Test fun `the scenes draw many words, each in a slot of its art`() {
        assertTrue("${index.size} words", index.size >= 120)
        for (s in CuratedContent.scenes) for (o in s.objects) {
            val spot = index.spot(o.pack ?: s.pack!!, o.word)!!
            assertTrue("${s.id}/${o.slot}", spot.slot in SceneArt.objects.getValue(spot.art))
        }
    }

    @Test fun `a gift is found by its name, as the scenes say the word`() {
        val smithy = SceneSpec(
            id = "v-kovacnici", title = "V kovačnici", art = "smithy", from = listOf("smithy"), pack = "v-kovacnici",
            objects = listOf(SceneObject("horseshoe", "podkev", sl = "podkev", emoji = "⚒️"), SceneObject("axe", "sekira", pack = "ob-ognju", sl = "sekira", emoji = "🪓")),
        )
        val named = WordStickers.of(listOf(smithy))
        assertEquals(StickerSpot("smithy", "horseshoe"), named.named("Podkev", "⚒️"))
        assertNull("Sekira keeps its 🪓", named.named("Sekira", "🪓"))
        assertNull(named.named("Potica", "🥮"))
    }

    @Test fun `a review item names its pack and word`() {
        assertEquals("v-sotoru" to "spalna-vreca", WordStickers.packWord("vocab_v-sotoru_spalna-vreca"))
        assertEquals("il-mare" to "gabbiano", WordStickers.packWord(PackSession.itemId("il-mare", "gabbiano")))
        assertNull(WordStickers.packWord("grammar_dual"))
        assertNull(WordStickers.packWord("vocab_"))
        assertNull(WordStickers.packWord("vocab_pack_"))
        assertNull(WordStickers.EMPTY.ofItem("vocab_na-njivi_kosa", null))
    }
}
