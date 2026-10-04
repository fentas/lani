package si.lanisce.lani.ui.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Scenario
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.game.villagers.VillagerLines
import si.lanisce.lani.game.villagers.parseVillagers
import java.io.File
import java.time.LocalDate

class CastTest {
    private val day = LocalDate.of(2026, 9, 24)

    private fun v(id: String, name: String, art: String = "farmer", voice: String = "male", register: String = "ti") =
        Villager(id = id, name = name, art = art, voice = voice, register = register, lines = VillagerLines(cheer = listOf(VillagerLine("Bravo!", "Well done!"))))

    private val cast = listOf(
        v("micka", "Babica Micka", "grandma", "female"),
        v("janez", "Stari Janez", "grandpa"),
        v("luka", "Pastir Luka", "shepherd"),
        v("tone", "Kovač Tone", "smith"),
        v("vida", "Gostilničarka Vida", "innkeeper", "female", "vi"),
        v("france", "Mlinar France", "farmer"),
    )

    private fun scenario(id: String, role: String, voice: String? = null) = Scenario(
        id = id, title = "Test", setting = "At the bakery.", role = role, voice = voice,
        goals = listOf("greet"), openerSl = "Dober dan!", openerEn = "Good day!",
    )

    @Test fun `a quest's giver, a module's quest giver and a pack's giver come on stage from the cast`() {
        assertEquals("luka", Cast.onStage(Run.Quest("Pastir Luka", "🐑"), cast, null, day).id)
        assertEquals("tone", Cast.onStage(Run.Module("Kovač Tone"), cast, null, day).id)
        assertEquals("vida", Cast.onStage(Run.Pack("Gostilničarka Vida"), cast, null, day).id)
    }

    @Test fun `someone who doesn't live here yet leaves the stage to who does`() {
        val s = GameState(residents = listOf(
            si.lanisce.lani.game.villagers.Resident("micka", day.toString()),
            si.lanisce.lani.game.villagers.Resident("luka", day.toString()),
        ))
        // the smith gathers stone and gives a module's request, but there's no smith in the camp yet
        val stone = Cast.onStage(Run.Gather(Res.STONE), cast, s, day)
        assertTrue(stone.id in setOf("micka", "luka"))
        assertTrue(Cast.onStage(Run.Module("Kovač Tone"), cast, s, day).id in setOf("micka", "luka"))
        assertTrue(Cast.onStage(Run.Event(EventKind.WOLVES), cast, s, day).id in setOf("micka", "luka"))
        // the shepherd lives here
        assertEquals("luka", Cast.onStage(Run.Gather(Res.WOOD), cast, s, day).id)
        // today's companion is someone who's here, even when Jan met someone else on a visit
        val met = s.copy(bonds = mapOf("tone" to Bond(points = 3, met = day.toString(), seen = "2026-01-01")))
        assertTrue(Cast.onStage(Run.Review, cast, met, day).id in setOf("micka", "luka"))
    }

    @Test fun `events have their defenders and gathering its worker`() {
        assertEquals("tone", Cast.onStage(Run.Event(EventKind.WOLVES), cast, null, day).id)
        assertEquals("tone", Cast.onStage(Run.Event(EventKind.BEAR), cast, null, day).id)
        assertEquals("janez", Cast.onStage(Run.Event(EventKind.STORM), cast, null, day).id)
        assertEquals("vida", Cast.onStage(Run.Event(EventKind.MERCHANT), cast, null, day).id)
        assertEquals("micka", Cast.onStage(Run.Event(EventKind.FESTIVAL), cast, null, day).id)
        assertEquals("france", Cast.onStage(Run.Gather(Res.FOOD), cast, null, day).id)
        assertEquals("luka", Cast.onStage(Run.Gather(Res.WOOD), cast, null, day).id)
        assertEquals("tone", Cast.onStage(Run.Gather(Res.STONE), cast, null, day).id)
        assertEquals("janez", Cast.onStage(Run.Gather(Res.WISDOM), cast, null, day).id)
    }

    @Test fun `before meeting anyone, today's companion is Babica Micka`() {
        assertEquals("micka", Cast.onStage(Run.Review, cast, GameState(seed = 3), day).id)
        assertEquals("micka", Cast.onStage(Run.Module(null), cast, null, day).id)
        assertEquals("micka", Cast.onStage(Run.Pack(null), cast, null, day).id)
        // No cast at all (an older node): a stand-in Micka with the grandmother's sprite and voice.
        val standIn = Cast.onStage(Run.Review, emptyList(), null, day)
        assertEquals("Babica Micka", standIn.name)
        assertNull(standIn.id)
        assertEquals("grandma", standIn.art)
        assertEquals(Clips.FEMALE, standIn.voice)
    }

    @Test fun `today's companion is the friend not seen for the longest`() {
        val s = GameState(
            seed = 7,
            bonds = mapOf(
                "luka" to Bond(points = 12, met = "2026-09-01", seen = "2026-09-23"),
                "tone" to Bond(points = 3, met = "2026-09-02", seen = "2026-09-10"),
                "vida" to Bond(points = 40, met = "2026-09-05", seen = "2026-09-20"),
            ),
        )
        val who = Cast.onStage(Run.Review, cast, s, day)
        assertEquals("tone", who.id)
        assertEquals(Bonds.level(3), who.level)
    }

    @Test fun `among friends not seen equally long, the day's dice pick, the same all day`() {
        val s = GameState(
            seed = 11,
            bonds = mapOf(
                "luka" to Bond(met = "2026-09-01", seen = "2026-09-10"),
                "tone" to Bond(met = "2026-09-01", seen = "2026-09-10"),
                "vida" to Bond(met = "2026-09-01", seen = "2026-09-10"),
            ),
        )
        val picks = (0 until 40).map { Cast.todaysCompanion(cast, s, day.plusDays(it.toLong()))!!.id }.toSet()
        assertTrue("the dice vary by day: $picks", picks.size > 1)
        assertTrue(picks.all { it in setOf("luka", "tone", "vida") })
        assertEquals(Cast.todaysCompanion(cast, s, day), Cast.todaysCompanion(cast, s, day))
    }

    @Test fun `the companion pinned earlier today stays, even after training changed who was seen last`() {
        val s = GameState(seed = 1, bonds = mapOf("tone" to Bond(met = "2026-09-01", seen = day.toString()), "luka" to Bond(met = "2026-09-01", seen = "2026-09-01")))
        assertEquals("luka", Cast.onStage(Run.Review, cast, s, day).id)
        assertEquals("tone", Cast.onStage(Run.Review, cast, s, day, daily = "tone").id)
        // A pinned id no longer in the cast falls back to the rule.
        assertEquals("luka", Cast.onStage(Run.Review, cast, s, day, daily = "gone").id)
    }

    @Test fun `someone the cast doesn't know stands in with their title's sprite and voice`() {
        val anka = Cast.onStage(Run.Quest("Teta Ančka", "📻"), cast, null, day)
        assertNull(anka.id)
        assertEquals("aunt", anka.art)
        assertEquals(Clips.FEMALE, anka.voice)
        assertEquals("📻", anka.emoji)
        val marko = Cast.person("Vinar Marko", null, cast, null)
        assertEquals("winemaker", marko.art)
        assertEquals(Clips.MALE, marko.voice)
        assertEquals("Vinar · Winemaker", marko.role)
        // No title: the name decides; Luka is a man's name although it ends in -a.
        assertEquals(Clips.FEMALE, Cast.standIn("Marija", null).voice)
        assertEquals(Clips.MALE, Cast.standIn("Luka", null).voice)
    }

    @Test fun `a name finds its villager by the id it suggests`() {
        assertEquals("ancka", Cast.idOf("Teta Ančka"))
        assertEquals("tone", Cast.idOf(" Kovač Tone "))
        assertEquals("tone", Cast.find("Tone", cast)?.id)
        assertEquals("vida", Cast.find("gostilničarka vida", cast)?.id)
    }

    @Test fun `a role-play's other person is the villager played, else the scenario's character`() {
        assertEquals("luka", Cast.talker(scenario("villager:luka", "Pastir Luka, the shepherd"), cast, null).id)
        assertEquals("micka", Cast.talker(scenario("nedeljsko-kosilo", "Babica Micka, the grandmother"), cast, null).id)
        val baker = Cast.talker(scenario("v-pekarni", "Prodajalka Nina, a baker in Nova Gorica", voice = Clips.FEMALE), cast, null)
        assertNull(baker.id)
        assertEquals("Prodajalka Nina", baker.name)
        assertEquals(Clips.FEMALE, baker.voice)
        assertEquals("vi", baker.register)
        assertEquals("a baker in Nova Gorica", baker.role)
    }

    @Test fun `training points left today follow the daily cap`() {
        assertEquals(0, Cast.trainingLeft(null, null, day))
        assertEquals(Bonds.TRAINING, Cast.trainingLeft(GameState(), "luka", day))
        val used = GameState(bonds = mapOf("luka" to Bond(seen = day.toString(), trainedToday = Bonds.TRAINING_PER_DAY)))
        assertEquals(0, Cast.trainingLeft(used, "luka", day))
        val yesterday = GameState(bonds = mapOf("luka" to Bond(seen = day.minusDays(1).toString(), trainedToday = Bonds.TRAINING_PER_DAY)))
        assertEquals(Bonds.TRAINING, Cast.trainingLeft(yesterday, "luka", day))
    }

    @Test fun `places follow the run, else the person's home`() {
        val micka = Cast.onStage(Run.Review, cast, null, day)
        assertEquals(StagePlace.KITCHEN, StagePlace.of(Run.Review, micka))
        assertEquals(StagePlace.NIGHT, StagePlace.of(Run.Event(EventKind.WOLVES), micka))
        assertEquals(StagePlace.STORM, StagePlace.of(Run.Event(EventKind.STORM), micka))
        assertEquals(StagePlace.FOREST, StagePlace.of(Run.Gather(Res.WOOD), micka))
        assertEquals(StagePlace.QUARRY, StagePlace.of(Run.Gather(Res.STONE), micka))
        assertEquals(StagePlace.SMITHY, StagePlace.home("smith"))
        assertEquals(StagePlace.FIRE, StagePlace.home("child1"))
    }

    /** The curated cast (companion/cultures/primorska/villagers) is one the stage can play: every line kind, valid sprites and voices. */
    @Test fun `the cast has everyone the stage calls, with lines for every moment`() {
        val fixture = File("../../cultures/primorska/villagers").listFiles { f -> f.name.endsWith(".json") }!!.map { parseVillagers("[" + it.readText() + "]").single() }
        assertTrue(fixture.size >= 10)
        assertEquals(fixture.size, fixture.map { it.id }.toSet().size)
        for (x in fixture) {
            assertTrue(x.id, x.art in si.lanisce.lani.game.scene.SceneArt.people)
            assertTrue(x.id, x.voice == Clips.FEMALE || x.voice == Clips.MALE)
            assertTrue(x.id, x.register == "ti" || x.register == "vi")
            val l = x.lines
            for ((kind, pool) in listOf("greet" to l.greet, "cheer" to l.cheer, "comfort" to l.comfort, "listen" to l.listen, "bye" to l.bye)) {
                assertTrue("${x.id} has $kind lines at level 0", pool.any { it.level == 0 })
                assertTrue("${x.id}: $kind lines are bilingual", pool.all { it.target.isNotBlank() && it.base.isNotBlank() })
            }
            assertTrue(x.id, l.remember.all { "{memory}" in it.target })
            assertEquals("${x.name} is found by the id their name suggests, or by name", x.id, Cast.find(x.name, fixture)?.id)
        }
        val names = fixture.map { it.name }.toSet()
        for (n in EventKind.entries.mapNotNull(Cast::defender) + Res.entries.mapNotNull(Cast::gatherer) + Cast.HOST) assertTrue(n, n in names)
    }

    @Test fun `the pilgrim stands on the stage himself, a stranger with no friendship to grow`() {
        val s = GameState(residents = cast.map { si.lanisce.lani.game.villagers.Resident(it.id, "2026-01-01") }, villagers = cast.size)
        val romar = Cast.onStage(Run.Stranger(si.lanisce.lani.game.Surprises.PILGRIM), cast, s, day)
        assertNull(romar.id)
        assertEquals("pilgrim", romar.art)
        assertEquals("Romar", romar.short)
        assertEquals(Clips.MALE, romar.voice)
        assertEquals(0, romar.level)
        assertTrue(romar.formal) // a stranger says vi to Jan, and hears vi
        assertEquals(StagePlace.FIELD, StagePlace.of(Run.Stranger("pilgrim"), romar))
        // his own lines on the stage, bilingual, a greeting and a goodbye
        for (pool in listOf(romar.lines.greet, romar.lines.cheer, romar.lines.comfort, romar.lines.bye)) {
            assertTrue(pool.isNotEmpty() && pool.all { it.target.isNotBlank() && it.base.isNotBlank() })
        }
        assertEquals(Lines.ask(Run.Quest("x", "?"), "vi"), Lines.ask(Run.Stranger("pilgrim"), "vi"))
        // the pedlar, when someone stages him (his stall), is himself too; anyone unknown falls back to today's companion
        assertEquals("pedlar", Cast.onStage(Run.Stranger(si.lanisce.lani.game.Surprises.PEDLAR), cast, s, day).art)
        assertTrue(Cast.onStage(Run.Stranger("nobody"), cast, s, day).id in cast.map { it.id } + null)
        // the portraits and the voices go by their sprites: men
        for (art in listOf("pedlar", "pilgrim")) assertEquals(Clips.MALE, si.lanisce.lani.ui.scene.SceneWords.voiceOf(art))
    }
}
