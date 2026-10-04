package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.ui.scene.DialogRun
import java.io.File

/** The curated scenes (the JSON files in companion/scenes) parse with the app's format and only use what the painters draw. */
class ScenesContentTest {
    private val dir = listOf(File("../../scenes"), File("../scenes"), File("companion/scenes")).first { it.isDirectory }
    private val files = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }

    @Test fun `every curated scene parses, alone and as the served list`() {
        assertEquals(18, files.size)
        val scenes = files.map { f -> parseScene(f.readText()).also { assertEquals(f.nameWithoutExtension, it.id) } }
        assertEquals(scenes.map { it.id }, parseScenes(files.joinToString(",", "[", "]") { it.readText() }).map { it.id })
        assertEquals(
            listOf(
                "kuhinja", "na-njivi", "na-stolpu", "na-trznici", "na-vasi", "ob-ognju", "ob-potoku", "pri-cebelnjaku", "pri-ribniku", "v-cerkvi",
                "v-delavnici", "v-gozdu", "v-hisi", "v-kleti", "v-kovacnici", "v-podstresju", "v-soli", "v-sotoru",
            ),
            scenes.map { it.id },
        )
    }

    @Test fun `scenes use only the registry's arts, slots, spots and sprites`() {
        for (f in files) {
            val s = parseScene(f.readText())
            val slots = SceneArt.objects.getValue(s.art)
            val spots = SceneArt.personSlots.getValue(s.art)
            assertTrue("${s.id}: places", s.from.all { it in SceneArt.places || (it.startsWith("spot:") && it.removePrefix("spot:") in TownSpots.ids) })
            assertEquals("${s.id}: every slot once", s.objects.size, s.objects.map { it.slot }.toSet().size)
            assertTrue("${s.id}: slots", s.objects.all { it.slot in slots })
            assertTrue("${s.id}: most slots filled", s.objects.size >= slots.size * 3 / 4)
            assertTrue("${s.id}: sprites", s.people.all { it.art in SceneArt.people })
            assertTrue("${s.id}: spots", s.people.all { it.slot in spots })
            val effects = SceneArt.effects.getValue(s.art)
            val used = s.dialogs.flatMap { d -> d.lines.flatMap { l -> l.fx.keys + l.choices.flatMap { it.reply?.fx.orEmpty().keys } } }
            assertTrue("${s.id}: effects $used", used.all { it in effects })
        }
    }

    @Test fun `the interiors open from their buildings`() {
        val from = files.associate { f -> parseScene(f.readText()).let { it.id to it.from } }
        assertEquals(listOf("tent"), from["v-sotoru"])
        assertEquals(listOf("field", "kozolec"), from["na-njivi"])
        assertEquals(listOf("smithy"), from["v-kovacnici"])
        assertEquals(listOf("school"), from["v-soli"])
        // the church and the bee house open from theirs; the square from the linden or the well
        assertEquals(listOf("church"), from["v-cerkvi"])
        assertEquals(listOf("beehive"), from["pri-cebelnjaku"])
        assertEquals(listOf("lipa", "well"), from["na-vasi"])
        // the stream and the pond open from their spots of the landscape
        assertEquals(listOf("spot:riverbank"), from["ob-potoku"])
        assertEquals(listOf("spot:pond"), from["pri-ribniku"])
        // the market and the watchtower open from theirs
        assertEquals(listOf("market"), from["na-trznici"])
        assertEquals(listOf("watchtower"), from["na-stolpu"])
        assertTrue(from.values.flatten().all { TownPlace.of(it) != TownPlace.Fire || it == "fire" })
        // the rooms of the houses: the kitchen from the hut too, the others only from a house
        assertEquals(listOf("hut", "house"), from["kuhinja"])
        for (room in listOf("v-hisi", "v-delavnici", "v-kleti", "v-podstresju")) assertEquals(room, listOf("house"), from[room])
    }

    @Test fun `the rooms of the houses - who lives there, and every thing in them a word of a pack`() {
        val scenes = files.map { parseScene(it.readText()) }
        val rooms = scenes.filter { Homes.isRoom(it) }
        assertEquals(listOf("kuhinja", "v-delavnici", "v-hisi", "v-kleti", "v-podstresju"), rooms.map { it.id })
        assertEquals(
            mapOf("kuhinja" to emptyList(), "v-delavnici" to listOf("france", "tine"), "v-hisi" to listOf("janez"), "v-kleti" to listOf("marko"), "v-podstresju" to listOf("vida", "nejc")),
            rooms.associate { it.id to it.household },
        )
        // a household lives there, and is who you meet there; the kitchen is anyone's (the hut's, and a house left over)
        for (r in rooms) assertTrue(r.id, r.household.all { v -> r.people.any { it.villager == v } })
        assertEquals(mapOf("v-delavnici" to "workshop", "v-hisi" to "livingroom", "v-kleti" to "cellar", "v-podstresju" to "attic"), rooms.filter { it.id != "kuhinja" }.associate { it.id to it.art })
        for (r in rooms) for (o in r.objects) {
            val key = WordStickers.key(o.pack ?: r.pack!!, o.word)
            assertTrue("${r.id}/${o.slot}: $key is a word of its pack", key in CuratedContent.emojis)
        }
        // every room grows: some of its things come with the house's second level, some with its third, like the kitchen's
        for (r in rooms) {
            val levels = r.objects.mapNotNull { it.needs?.level }.toSet()
            assertTrue("${r.id}: $levels", 2 in levels && 3 in levels)
            assertTrue("${r.id}: most things there from the first level", r.objects.count { it.needs == null } >= 5)
        }
        // the Primorska village with everyone moved in, in the cast's order: the first house is Janez's, then France's, Marko's,
        // Vida and Nejc's; the fifth gets the kitchen (the hut's too)
        val cast = listOf("micka", "luka", "zala", "janez", "france", "tine", "ancka", "anton", "tone", "marko", "vida", "nejc", "mojca")
        val village = GameState(
            age = Age.VAS,
            buildings = listOf(Building("hut", BuildingType.HUT, 0)) + (1..5).map { Building("h$it", BuildingType.HOUSE, it, builtAt = it * 10L) },
            residents = cast.map { Resident(it, "2026-09-01") },
        )
        assertEquals(
            mapOf("h1" to "v-hisi", "h2" to "v-delavnici", "h3" to "v-kleti", "h4" to "v-podstresju", "h5" to "kuhinja"),
            Homes.deal(scenes, village).mapValues { it.value.id },
        )
    }

    /** [dialog] of [scene] played with the right choices, every step from its first line to its end. */
    private fun play(scene: String, dialog: String): List<DialogRun> {
        val d = files.map { parseScene(it.readText()) }.first { it.id == scene }.dialogs.first { it.id == dialog }
        var run = DialogRun.start(d, null)
        val out = arrayListOf(run)
        while (run.step != DialogRun.Step.END) {
            run = if (run.step == DialogRun.Step.CHOOSE) run.choose(run.choices.indexOfFirst { it.ok }) else run.next()
            out += run
        }
        return out
    }

    /** The skies [dialog] of [scene] goes through. */
    private fun skies(scene: String, dialog: String): List<Sky> = play(scene, dialog).map { it.sky(Sky.CLEAR) }

    /** The effects [dialog] of [scene] goes through. */
    private fun effects(scene: String, dialog: String): List<Map<String, Float>> = play(scene, dialog).map { it.fx(emptyMap()) }

    @Test fun `dialogs that talk about the weather bring it`() {
        // Luka's hay: grey first, the first drops midway, rain at the end, and it stays
        val hay = skies("na-njivi", "seno")
        assertTrue(hay.first().gloom > 0f && hay.first().rain == 0f)
        assertTrue(hay.any { it.rain in 0.1f..0.3f })
        assertTrue(hay.last().rain >= 0.6f && hay.last().gloom >= 0.6f)
        // Luka in the tent: pouring from the start, lightning far off, then the sun
        val tent = skies("v-sotoru", "dez")
        assertTrue(tent.first().rain >= 0.8f && tent.any { it.lightning })
        assertTrue(tent.last().clear)
        // the fog on the pasture at the fire lifts again
        assertTrue(skies("ob-ognju", "roke").any { it.fog >= 0.5f })
        // Micka's laundry by the stream: grey first, the first drops, pouring at the end, and it stays
        val wash = skies("ob-potoku", "perilo")
        assertTrue(wash.any { it.gloom > 0f && it.rain == 0f } && wash.any { it.rain in 0.1f..0.4f } && wash.last().rain >= 0.6f)
        // fog on the pond at dawn that lifts; a shower over Janez's fishing that passes
        val dawn = skies("pri-ribniku", "megla")
        assertTrue(dawn.any { it.fog >= 0.5f } && dawn.last().fog == 0f)
        val shower = skies("pri-ribniku", "ribe")
        assertTrue(shower.any { it.rain > 0f } && shower.last().clear)
        // France sees the storm come across the valley from the tower: clouds, lightning, then rain, and it stays
        val storm = skies("na-stolpu", "nevihta")
        assertTrue(storm.first().gloom > 0f && storm.first().rain == 0f && storm.any { it.lightning } && storm.last().rain >= 0.6f)
        val kept = files.map { parseScene(it.readText()) }.flatMap { s -> s.dialogs.filter { it.skyKept }.map { "${s.id}/${it.id}" } }
        // the variants of the same happenings keep theirs too (the hay, the storm, the tent in the rain, Luka staying in
        // it while it pours), and the rain that ends the children's football and the burja at night
        assertEquals(
            listOf(
                "na-njivi/seno", "na-njivi/seno-grom", "na-stolpu/nevihta", "na-stolpu/nevihta-sekunde", "na-stolpu/nevihta-seno", "na-vasi/nogomet-dez",
                "ob-potoku/perilo", "v-podstresju/burja-zgodba", "v-podstresju/burja-okno", "v-sotoru/dez", "v-sotoru/dez-cakava", "v-sotoru/dez-svetilka",
            ),
            kept,
        )
    }

    @Test fun `dialogs that say someone moves move them, and hide-and-seek is found by a tap`() {
        val scenes = files.map { parseScene(it.readText()) }.associateBy { it.id }
        val base = { id: String -> Stage.base(scenes.getValue(id), emptyMap()) }
        /** Where [dialog] of [scene] has put people at each line, taking the right choices. */
        fun acts(scene: String, dialog: String): List<Map<String, Placement>> {
            val d = scenes.getValue(scene).dialogs.first { it.id == dialog }
            val picks = d.lines.filter { it.choices.isNotEmpty() }.map { l -> l.choices.indexOfFirst { it.ok } }
            return d.lines.indices.map { at -> d.actAt(at + 1, picks.take(d.lines.take(at + 1).count { it.choices.isNotEmpty() }), base(scene)) }
        }
        // asked in out of the rain, Luka comes into the tent and sits by the lantern; the sun out, he goes back to his sheep
        for (v in listOf("dez", "dez-cakava")) {
            val placed = acts("v-sotoru", v)
            assertTrue("$v: Luka sits by the lantern", placed.any { it["luka"] == Placement("lantern", "sit") })
            assertEquals("$v: and goes out again", Placement("door"), placed.last()["luka"])
        }
        // in the downpour he stays by the lantern for the day, the lantern lit and the rain on
        val stays = scenes.getValue("v-sotoru").dialogs.first { it.id == "dez-svetilka" }
        assertTrue(stays.actKept && stays.fxKept && stays.skyKept)
        assertEquals(Placement("lantern", "sit"), acts("v-sotoru", "dez-svetilka").last()["luka"])
        // Zala hides at the count and is found by a tap on one of three places of the living room (pod mizo, za vrati:
        // the instrumental of place, on the page of orodnik)
        for (v in listOf("skrivalnice-vrata", "skrivalnice")) {
            val d = scenes.getValue("v-hisi").dialogs.first { it.id == v }
            val placed = acts("v-hisi", v)
            assertTrue("$v: Zala hides", placed.any { it["zala"]?.stance == Stance.HIDE })
            val turn = d.lines.first { it.tapTurn }
            assertEquals("$v: three places", 3, turn.choices.size)
            assertEquals("orodnik", turn.choices.single { it.ok }.grammar)
            assertTrue("$v: every place is a spot or a slot of the living room", turn.choices.all { c -> c.tap in Stage.spots("livingroom") || c.tap in SceneArt.objects.getValue("livingroom") })
            // a wrong place: she peeks out of her hiding place, a hint, and hides again at the next pick
            assertTrue("$v: a wrong place's reaction is a peek", turn.choices.filter { !it.ok }.all { it.reply?.act?.get("zala")?.pose == "peek" })
        }
    }

    @Test fun `dialogs that say the place changes set its effects`() {
        // Luka brings the lantern into the dark tent and lights it; it stays lit tonight
        val lamp = effects("v-sotoru", "svetilka")
        assertEquals(0f, lamp.first()["lantern"])
        assertEquals(1f, lamp.last()["lantern"])
        // Micka bakes the potica; Tone hammers and stops; the bell rings the children home; the crows come back
        assertEquals(1f, effects("kuhinja", "potica").first()["oven"])
        val shoe = effects("v-kovacnici", "podkev")
        assertTrue(shoe.any { it["sparks"] == 1f } && shoe.last()["sparks"] == 0f)
        assertEquals(1f, effects("na-vasi", "dvojina").last()["bell"])
        assertEquals(1f, effects("na-njivi", "strasilo").last()["crows"])
        assertEquals(1f, effects("v-gozdu", "medved").last()["birds"])
        // France's wheel stands, then turns; Luka's trout leaps; the frog jumps in; the ducks fly up; the carp jumps
        val wheel = effects("ob-potoku", "mlin")
        assertTrue(wheel.first()["wheel"] == 0f && wheel.last()["wheel"] == 1f)
        assertEquals(1f, effects("ob-potoku", "ovce").last()["trout"])
        assertEquals(1f, effects("pri-ribniku", "zaba").last()["frog"])
        assertEquals(1f, effects("pri-ribniku", "megla").last()["ducks"])
        assertEquals(1f, effects("pri-ribniku", "ribe").last()["fish"])
        // Micka lights the candles in the church, and they burn until midnight; Tine rings noon; Zala's honey runs; the swarm
        val candle = effects("v-cerkvi", "sveca")
        assertTrue(candle.first()["candles"] == 0f && candle.last()["candles"] == 1f)
        assertTrue(effects("v-cerkvi", "poldne").any { it["bell"] == 1f })
        assertEquals(1f, effects("pri-cebelnjaku", "med").first()["honey"])
        assertEquals(1f, effects("pri-cebelnjaku", "roj").last()["swarm"])
        // Luka lights the watch's torch and blows the horn at the wolves, and they go; the market opens with its bell,
        // Vida's market day is busy, Marko's laugh sends the pigeons up
        val watch = effects("na-stolpu", "straza")
        assertEquals(0f, watch.first()["torch"])
        assertTrue(watch.any { it["wolves"] == 1f } && watch.any { it["horn"] == 1f })
        assertEquals(mapOf("torch" to 1f, "wolves" to 0f, "horn" to 0f), watch.last())
        assertEquals(1f, effects("na-stolpu", "krosnjar").last()["birds"])
        assertEquals(1f, effects("na-trznici", "jajca").first()["bell"])
        assertEquals(1f, effects("na-trznici", "gostilna").first()["bustle"])
        assertEquals(1f, effects("na-trznici", "rebula").last()["pigeons"])
        val kept = files.map { parseScene(it.readText()) }.flatMap { s -> s.dialogs.filter { it.fxKept }.map { "${s.id}/${it.id}" } }
        // and their variants: the candles lit, the lantern left burning
        assertEquals(listOf("na-stolpu/straza", "v-cerkvi/sveca", "v-cerkvi/sveca-stejem", "v-cerkvi/sveca-veter", "v-sotoru/svetilka", "v-sotoru/svetilka-bom", "v-sotoru/svetilka-komu", "v-sotoru/dez-svetilka"), kept)
    }

    @Test fun `at dawn and dusk the hunter on his high seat, the mowing in the dew, fishing in the fog, the mill waking`() {
        val scenes = files.map { parseScene(it.readText()) }.associateBy { it.id }
        fun h(scene: String, id: String) = scenes.getValue(scene).happenings.first { it.id == id }
        // at dawn only (not all morning), and the hunter's at dusk
        for ((scene, id) in listOf("v-gozdu" to "preza", "na-njivi" to "kosnja", "pri-ribniku" to "zora", "ob-potoku" to "zora", "ob-potoku" to "megla")) {
            assertEquals("$scene/$id", listOf(TimeOfDay.DAWN), h(scene, id).`when`)
        }
        assertEquals(listOf(TimeOfDay.EVENING), h("v-gozdu", "mrak").`when`)
        assertEquals("joze", scenes.getValue("v-gozdu").people.first { it.id == h("v-gozdu", "preza").who }.villager)
        assertEquals("highseat", scenes.getValue("v-gozdu").people.first { it.id == "joze" }.slot)
        // the mill starts at dawn once it grinds; until then France watches the water in the mist
        assertEquals(SceneNeeds(project = "mlin"), h("ob-potoku", "zora").needs)
        assertEquals(SceneNeeds(project = "mlin", before = true), h("ob-potoku", "megla").needs)
        // Jože: the stag and his hinds step out of the mist, the fox comes along the edge; at dusk the owl, the bats
        val preza = effects("v-gozdu", "preza")
        assertTrue(preza.any { it["herd"] == 1f } && preza.last()["fox"] == 1f)
        assertTrue(skies("v-gozdu", "preza").any { it.fog > 0.2f } && skies("v-gozdu", "preza").last().fog == 0f)
        val mrak = effects("v-gozdu", "mrak")
        assertTrue(mrak.any { it["hoot"] == 1f } && mrak.last()["bats"] == 1f)
        assertEquals(1f, effects("v-gozdu", "sova").first()["hoot"])
        // Janez fishing in the fog that lifts, a fish taking; France mowing when the bell rings six; the wheel coming to life
        assertTrue(skies("pri-ribniku", "zora").first().fog >= 0.7f && skies("pri-ribniku", "zora").last().fog == 0f)
        assertTrue(effects("pri-ribniku", "zora").any { it["fish"] == 1f })
        assertTrue(effects("na-njivi", "kosnja").any { it["bell"] == 1f })
        val mill = effects("ob-potoku", "zora")
        assertTrue(mill.first()["wheel"] == 0f && mill.last()["wheel"] == 1f && mill.any { it["bell"] == 1f })
        assertTrue(skies("ob-potoku", "megla").first().fog >= 0.5f && effects("ob-potoku", "megla").any { it["trout"] == 1f })
        // none of the mist stays for the day
        assertTrue(listOf("v-gozdu/preza", "pri-ribniku/zora", "ob-potoku/zora", "ob-potoku/megla").none { k ->
            scenes.getValue(k.substringBefore('/')).dialogs.first { it.id == k.substringAfter('/') }.skyKept
        })
    }

    @Test fun `happenings and dialogs point at people and dialogs of their scene`() {
        for (f in files) {
            val s = parseScene(f.readText())
            val people = s.people.map { it.id }.toSet()
            val dialogs = s.dialogs.map { it.id }.toSet()
            assertTrue("${s.id}: 3-8 happenings", s.happenings.size in 3..8)
            for (h in s.happenings) {
                assertTrue("${s.id}/${h.id}: who", h.who in people)
                // the storyteller tells tonight's story instead of a dialog of the scene's (StoriesTest)
                assertTrue("${s.id}/${h.id}: dialog", if (h.stories) h.dialog == null else h.dialog in dialogs)
                assertTrue("${s.id}/${h.id}: its variants", h.dialogs.all { it in dialogs } && (h.dialogs.isEmpty() || !h.stories))
                assertTrue("${s.id}/${h.id}: chance", h.chance in 0f..1f)
                assertTrue("${s.id}/${h.id}: weekdays", h.weekdays.all { it in 1..7 })
            }
            for (d in s.dialogs) {
                assertTrue("${s.id}/${d.id}: 3-8 lines", d.lines.size in 3..8)
                for (l in d.lines) {
                    if (l.choices.isEmpty()) assertTrue("${s.id}/${d.id}: speaker", l.who in people && !l.sl.isNullOrBlank())
                    // a right one (two where people would accept either) and a wrong one, which says why
                    else assertTrue("${s.id}/${d.id}: one or two ok, a wrong one, why on the wrong", l.choices.count { it.ok } in 1..2 && l.choices.any { !it.ok } && l.choices.all { it.ok || !it.why.isNullOrBlank() })
                }
            }
        }
    }
}
