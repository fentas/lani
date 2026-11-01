package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.CultureLook
import si.lanisce.lani.game.scene.CultureLooks
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneWorld

/**
 * The scenes in a culture's look (companion/SCENES.md, "Culture looks"): the default culture's (Primorska's) pictures are
 * pixel for pixel what they were before the looks came (the digests below, taken from the painters as they were on
 * 2026-10-28); an art a culture's look doesn't touch draws the same for every culture as for the default.
 */
class CultureLooksTest {
    /**
     * What came after the digests were taken, left out of their frames so they keep holding: the square's standing spots by its
     * projects and the bee meadow (2026-10-30, place-life: PlaceLifeRenderTest has them, and checks the things drawn before
     * draw the same pixels as things now).
     */
    private val laterSpots = mapOf("square" to setOf("maypole", "fountain", "clock"))
    private val laterProjects = mapOf("apiary" to "cebelji_travnik")

    /** A few frames of every art: by day and at night, closer up, with someone at every spot. */
    private fun frames(art: String, culture: String = si.lanisce.lani.game.culture.Cultures.DEFAULT): List<Triple<String, SceneFrame, Lens>> {
        val world = SceneWorld(culture = culture).let { w -> laterProjects[art]?.let { w.stepping(it, 0) } ?: w }
        val people = (SceneArt.personSlots.getValue(art) - laterSpots[art].orEmpty()).mapIndexed { k, slot -> PersonInScene("p-$slot", SceneArt.people[k % SceneArt.people.size], slot) }
        val objects = SceneArt.objects.getValue(art).toSet()
        return listOf(
            Triple("day", SceneFrame(time = 3.0, hour = 11f, month = 6, objects = objects, people = people, world = world), Lens(1, 0, 0, 240, 160)),
            Triple("dusk-winter", SceneFrame(time = 5.0, hour = 18.5f, month = 1, objects = objects, world = world), Lens(1, 0, 0, 270, 297)),
            Triple("night-k2", SceneFrame(time = 7.0, hour = 22.5f, month = 10, objects = objects, people = people, world = world), Lens(2, 120, 80, 240, 160)),
        )
    }

    /** 64-bit FNV-1a over the pixels. */
    private fun fnv(ints: IntArray): String {
        var h = -0x340d631b7bdddcdbL
        for (v in ints) { h = h xor (v.toLong() and 0xffffffffL); h *= 0x100000001b3L }
        return java.lang.Long.toHexString(h)
    }

    /** [art]'s frames in [culture]'s look, the people in [dressedAs]'s clothes (null: the culture's own). */
    private fun digests(art: String, culture: String = si.lanisce.lani.game.culture.Cultures.DEFAULT, dressedAs: String? = null): Map<String, String> =
        frames(art, culture).associate { (name, f, lens) ->
            val c = PixelCanvas(lens.baseW, lens.baseH)
            (ScenePainters.create(art) as ArtPainter).also { it.dressedAs = dressedAs }.render(c, f, lens)
            name to fnv(c.pixels)
        }

    /**
     * The default culture's pictures as the painters drew them before the culture looks (2026-10-28); the village's places'
     * arts as they came (2026-10-30); the mill's and the stream's with their wheels' water and turning made right (2026-10-31,
     * mill-mechanics: the cog wheel turning with the waterwheel, the water in the buckets, never over the wheel's face); the
     * mill's water lit once by the light pass, as the wood beside it (2026-11-01, polish-app: its dusk and its night; by day
     * the same).
     */
    private val golden: Map<String, Map<String, String>> = mapOf(
        "alps" to mapOf("day" to "5a5d0afa6df563f4", "dusk-winter" to "be61904231098891", "night-k2" to "bce95d26f149895d"),
        "apiary" to mapOf("day" to "1e50f1d726d07a25", "dusk-winter" to "453ae0df7ded3067", "night-k2" to "9ff1c006eba8e0d6"),
        "attic" to mapOf("day" to "6bcda3ea37e68e36", "dusk-winter" to "13561ab21998b47a", "night-k2" to "958630ef3667941a"),
        "bocce" to mapOf("day" to "837c3d737072911", "dusk-winter" to "e072d2281c279b5", "night-k2" to "988317a05f34c9e6"),
        "campfire" to mapOf("day" to "c988e0ed81e30678", "dusk-winter" to "ec0e7881a3a9821b", "night-k2" to "364e561b9a420d1d"),
        "cellar" to mapOf("day" to "6633d946c91eadb1", "dusk-winter" to "3d10864ad96e0bac", "night-k2" to "3ab89f1c73da72f4"),
        "chapel" to mapOf("day" to "57ef3ad3b19cdaf1", "dusk-winter" to "3a4c3b1c58777ab9", "night-k2" to "39b8d55a448c7018"),
        "church" to mapOf("day" to "31e3524cecc81ed0", "dusk-winter" to "e0afa01d6bb37039", "night-k2" to "d27bfb5087f0e0df"),
        "field" to mapOf("day" to "53c33480469df7eb", "dusk-winter" to "bb60e7a24cdee2a2", "night-k2" to "3e8ad26693cd3c6f"),
        "firestation" to mapOf("day" to "34c5ae40e76974ef", "dusk-winter" to "fbd1e5ac93f1a8ee", "night-k2" to "7aef511846894a30"),
        "forest" to mapOf("day" to "fc9002307f0a454b", "dusk-winter" to "6cdd11be017d0d90", "night-k2" to "b6f42a1b58c4fcd8"),
        "hills" to mapOf("day" to "7c29c75bfdb6a063", "dusk-winter" to "a89c994e437f0f7c", "night-k2" to "a6f01781c31c2a4f"),
        "kitchen" to mapOf("day" to "8a26337490b31ce4", "dusk-winter" to "7f35f5dfdec87d4d", "night-k2" to "1de2dcf56a1668e0"),
        "livingroom" to mapOf("day" to "3792029f557f5463", "dusk-winter" to "9e3c1d5bc85e8d6", "night-k2" to "bf313be42e550c6e"),
        "lookout" to mapOf("day" to "1a65549e7f4f8e39", "dusk-winter" to "62605923514f4d41", "night-k2" to "9982dd3271548e37"),
        "market" to mapOf("day" to "8dd099421cc3261", "dusk-winter" to "2d072797b199d996", "night-k2" to "b3dbc364108f1053"),
        "mill" to mapOf("day" to "3cd57edce6bf5bf0", "dusk-winter" to "d4d7b159f1663722", "night-k2" to "41f2a7405d4ef0de"),
        "palisade" to mapOf("day" to "8da2e0f59f3051bf", "dusk-winter" to "67baaf3a9d2cd9ad", "night-k2" to "f446c8c1646edd68"),
        "playground" to mapOf("day" to "b58bf294c2f79037", "dusk-winter" to "614fddf15333bd83", "night-k2" to "9e1073bd8d6a20a7"),
        "pond" to mapOf("day" to "5a13676b2c982f85", "dusk-winter" to "9f8f32e1d4110d56", "night-k2" to "d62373967a2d989b"),
        "school" to mapOf("day" to "d95a98ccc0c168b4", "dusk-winter" to "166230201723e32a", "night-k2" to "c72dd088e6bbb3f6"),
        "sea" to mapOf("day" to "2838a9cce526e19e", "dusk-winter" to "aceed89f35199027", "night-k2" to "bc0cebb5ceef25a"),
        "smithy" to mapOf("day" to "379e5416ff29f6f9", "dusk-winter" to "78a0f6270a03270d", "night-k2" to "bece5f761feff57a"),
        "square" to mapOf("day" to "3f23f32460ebc75d", "dusk-winter" to "47c0dad001349fdd", "night-k2" to "5d0df14d9e9ec979"),
        "stream" to mapOf("day" to "4530e3e6545fce56", "dusk-winter" to "a046cfc67af59078", "night-k2" to "4480ed3c47028140"),
        "tent" to mapOf("day" to "6185d761453dbfe2", "dusk-winter" to "1760c092b5e36332", "night-k2" to "a66740c25f4018a8"),
        "watchtower" to mapOf("day" to "f6cc0d626eb6cbcb", "dusk-winter" to "c7e90b29a80fe587", "night-k2" to "a769b98b8858554c"),
        "workshop" to mapOf("day" to "fc680e9a7f866b70", "dusk-winter" to "39e3b3b46ae4dcda", "night-k2" to "ecc8580900399aae"),
    )

    @Test fun `the default culture's scenes look exactly as before the culture looks`() {
        val got = SceneArt.arts.sorted().associateWith { digests(it) }
        val changed = got.filter { (art, d) -> golden[art] != d }
        if (changed.isNotEmpty()) println(changed.entries.joinToString(",\n") { (art, d) -> "\"$art\" to mapOf(${d.entries.joinToString(", ") { "\"${it.key}\" to \"${it.value}\"" }})" })
        // a new art has its digests added here as it comes; every art there was is the same
        assertEquals("the default culture's arts drawn otherwise than before", emptySet<String>(), changed.keys.filter { it in golden }.toSet())
    }

    /** The cultures there are, and one there isn't. */
    private val cultures = listOf("friuli", "kaernten", "lakeland", "nowhere")

    /** The arts that drew a culture's village their own way before the looks: the campfire's village round it (FireStyle). */
    private val ownStyle = setOf("campfire")

    /**
     * The people wear their culture's clothes in every art ([Wardrobes]; the test below), so an art's own look is compared with
     * Primorska's picture of it with the culture's people in it.
     */
    @Test fun `an art a culture's look doesn't touch draws as the default culture's`() {
        val failed = ArrayList<String>()
        for (art in SceneArt.arts.sorted()) {
            if (art in ownStyle) continue
            for (c in cultures) {
                if (CultureLooks.of(c).touches(art)) continue
                if (digests(art, c) != digests(art, dressedAs = c)) failed += "$art in $c"
            }
        }
        assertEquals(emptyList<String>(), failed)
    }

    /** Who stands in a culture's lineup: the cast, the strangers, two women and two men of the village's own. */
    private val lineupArts = SceneArt.people - "baby" + listOf("woman", "woman", "man", "man")

    /** [culture]'s people standing in a row as the scenes draw them. */
    private fun lineup(culture: String): IntArray {
        val c = PixelCanvas(lineupArts.size * 24 + 8, 48)
        val p = PeoplePainter().also { it.culture = culture }
        for ((i, art) in lineupArts.withIndex()) p.draw(c, art, 12 + i * 24, c.height - 2, 0.4, Looks.seedOf("lineup-$i"), talking = false, flip = i % 2 == 1, walking = false, seated = false)
        return c.pixels
    }

    /** [culture]'s people standing in a row as the village map draws them at detail [k]: the 7 px figures (1), the 14 px ones (2). */
    private fun mapLineup(culture: String, k: Int): IntArray {
        val w = lineupArts.size * 10 * k + 8; val h = 20 * k
        val c = PixelCanvas(w, h)
        val s = si.lanisce.lani.game.render.SceneCtx(c).apply { lens = Lens(k, 0, 0, w, h); look = si.lanisce.lani.game.render.MapLook.of(culture); time = 0.4 }
        for ((i, art) in lineupArts.withIndex()) {
            val id = "lineup-$i"
            val look = si.lanisce.lani.game.render.Effects.lookOf(art, si.lanisce.lani.game.render.Noise.hash(id.hashCode(), 13), culture, Looks.seedOf(id))
            s.effects.person(6 + i * 10 * k, h - 2, art, look, si.lanisce.lani.game.render.TownPeople.drawSeed(id), si.lanisce.lani.game.render.Activity.STAND, flip = i % 2 == 1, lookSeed = Looks.seedOf(id))
        }
        return c.pixels
    }

    /**
     * Each culture's people in its own clothes ([Wardrobes], `people.wardrobe`; docs/plans/05-built/map-people.md), drawn as
     * they were made (2026-11-01, map-people): Primorska's as they always were, Friuli's, Carinthia's and the Lake District's
     * their own, and a culture without a wardrobe as Primorska's. Each digest is the lineup as the scenes draw it, as the
     * map draws it at 14 px, and at 7 px.
     */
    private val people: Map<String, String> = mapOf(
        "primorska" to "f890cd4bada877d2/34863970621fd5e1/f240f67d42e797ad",
        "friuli" to "fc3cfaffb5cacf4d/7ce17b669d18f9ed/8c1156e45451f696",
        "kaernten" to "29158de4b39af093/97e7ecd2ec4df900/eeb3c401743598d9",
        "lakeland" to "b1502422fe5b92af/e43f190cfb35b173/2dff393a00440cc4",
    )

    @Test fun `each culture's people wear its own clothes`() {
        // each size apart: the sprites, the 14 px figures and the 7 px ones differ from Primorska's in every culture with a wardrobe
        for (c in cultures - "nowhere") for (k in 0..2) {
            val a = listOf(lineup(c), mapLineup(c, 2), mapLineup(c, 1))[k]; val b = listOf(lineup("primorska"), mapLineup("primorska", 2), mapLineup("primorska", 1))[k]
            assertTrue("$c, size $k: dressed otherwise than Primorska", !a.contentEquals(b))
        }
        val got = (listOf("primorska") + cultures).associateWith { "${fnv(lineup(it))}/${fnv(mapLineup(it, 2))}/${fnv(mapLineup(it, 1))}" }
        assertEquals("a culture without a wardrobe dresses its people as Primorska's", got["primorska"], got["nowhere"])
        assertEquals("four cultures, four wardrobes", 4, got.values.toSet().size)
        val changed = people.filter { (c, d) -> got[c] != d }
        if (changed.isNotEmpty()) println(got.filterKeys { it in people }.entries.joinToString(",\n") { "\"${it.key}\" to \"${it.value}\"" })
        assertEquals("people drawn otherwise than when they were made", emptySet<String>(), changed.keys)
    }

    @Test fun `a look gives what it sets of an art and the painter's own default for the rest`() {
        val t = CultureLooks.Table.of("school", colours = mapOf("plaster.l" to 0x11223344), materials = mapOf("wainscot" to "painted"), variants = mapOf("map" to "friuli", "abacus" to "pallottoliere"))
        val look = CultureLook("test", "classic", t)
        assertEquals(0x11223344, look.colour("school", "plaster.l", 7))
        assertEquals(7, look.colour("school", "plaster.m", 7))
        assertEquals(7, look.colour("kitchen", "plaster.l", 7))
        assertEquals("painted", look.material("school", "wainscot", "wood"))
        assertEquals("wood", look.material("kitchen", "wainscot", "wood"))
        assertEquals("friuli", look.variant("school", "map"))
        assertEquals(null, look.variant("school", "flag"))
        assertEquals(setOf("school"), t.arts)
        assertEquals("test" to "", look.keyOf("school") to look.keyOf("kitchen"))
        // tables add up, one art's names its own
        val both = t + CultureLooks.Table.of("kitchen", variants = mapOf("stove" to "fogolar"))
        assertEquals(setOf("school", "kitchen"), both.arts)
        // the default culture and one there isn't: nothing set, every art its own
        for (c in listOf(si.lanisce.lani.game.culture.Cultures.DEFAULT, "nowhere")) {
            val d = CultureLooks.of(c)
            assertEquals(5, d.colour("school", "plaster.l", 5))
            assertEquals(null, d.variant("school", "map"))
            assertTrue(SceneArt.arts.none { d.touches(it) })
        }
        assertTrue(SceneWorld().look === CultureLooks.of(si.lanisce.lani.game.culture.Cultures.DEFAULT))
    }
}
