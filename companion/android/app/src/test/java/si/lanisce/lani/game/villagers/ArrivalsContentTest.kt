package si.lanisce.lani.game.villagers

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.culture.Culture
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.io.File
import java.time.LocalDate

/**
 * The culture packs' arrivals (companion/cultures/<id>/arrivals.json, VILLAGERS.md "Arrivals") as the app plays them: every
 * pack has them, and each of its cast is introduced at every level it has, on time and late, in every base the pack
 * serves, with who speaks being who is there, every wrong choice with its reaction, and the templates' placeholders all
 * put in, for a newcomer, a mover and a baby. The bridge checks the file's shape and languages (arrivals.ts).
 */
class ArrivalsContentTest {
    private val day = LocalDate.of(2026, 9, 27)
    private lateinit var was: Culture
    private lateinit var pair: LangPair

    @Before fun setUp() {
        was = Cultures.home
        pair = L10n.pair
    }

    @After fun tearDown() {
        Cultures.use(was)
        L10n.pair = pair
    }

    private fun castOf(id: String): List<Villager> {
        val dir = listOf(File("../../cultures/$id/villagers"), File("../cultures/$id/villagers")).first { it.isDirectory }
        return dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { parseVillagers("[" + it.readText() + "]").single() }
    }

    /** Every text of [lines] as the learner sees it: what is said, what it means, the whys and the replies. */
    private fun texts(lines: List<DialogLine>): List<String> = lines.flatMap { l ->
        listOfNotNull(l.sl, l.en) + l.choices.flatMap { c -> listOfNotNull(c.sl, c.en, c.why, c.reply?.sl, c.reply?.en) }
    }

    private fun check(where: String, m: Meeting?, speakers: Set<String>) {
        assertNotNull("$where: no introduction", m)
        m!!
        val all = texts(m.dialog.lines) + listOfNotNull(m.memory?.sl, m.memory?.en)
        assertTrue("$where: a placeholder left: ${all.filter { '{' in it || '}' in it }}", all.none { '{' in it || '}' in it })
        assertTrue("$where: an empty text", all.none { it.isBlank() })
        for (l in m.dialog.lines) {
            if (l.choices.isEmpty()) assertTrue("$where: ${l.who} says «${l.sl}»", l.who in speakers)
            else {
                assertTrue("$where: a turn without a right choice", l.choices.any { it.ok })
                for (c in l.choices.filter { !it.ok }) {
                    assertNotNull("$where: «${c.sl}» has no why", c.why)
                    assertNotNull("$where: «${c.sl}» has no reaction", c.reply)
                }
            }
        }
        assertTrue("$where: the first line is someone's", m.dialog.lines.first().choices.isEmpty())
        assertNotNull("$where: no memory", m.memory)
    }

    @Test fun `every pack introduces each of its cast, at every level, on time and late, in every base`() {
        val packs = Cultures.ids.filter { Cultures.manifest(it)?.status == "complete" }
        assertEquals(listOf("friuli", "kaernten", "lakeland", "primorska"), packs.sorted())
        for (id in packs) {
            val culture = Cultures.load(id)
            assertNotNull("$id: no arrivals.json (or not the pack's language)", culture.arrivals)
            val file = culture.arrivals!!
            Cultures.use(culture)
            val cast = castOf(id)
            val target = culture.language
            for (v in cast.filter { !it.extra }) {
                val a = file.cast[v.id]
                assertNotNull("$id: no arrival for ${v.id}", a)
                val by = (a!!["by"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                val levels = (a["levels"] as kotlinx.serialization.json.JsonObject).keys
                assertTrue("$id/${v.id}: A1 and A2 at least ($levels)", "A1" in levels && "A2" in levels)
                for (base in Lang.entries.filter { it != target }) for (level in levels) for (late in listOf(false, true)) {
                    L10n.pair = LangPair(target, base)
                    val since = if (late) day.minusDays(5) else day
                    // everyone else lives here and is met, so each level plays as it is (a level that names someone the
                    // learner doesn't know gives way to an easier one: Mentions, VillageStoryTest)
                    val others = cast.filter { it.id != v.id }
                    val residents = others.map { Resident(it.id, since.minusDays(10).toString()) } + Resident(v.id, since.toString())
                    val s = GameState(
                        age = Age.MESTO, residents = residents, migrated = setOf(Arrivals.MIGRATION),
                        bonds = others.associate { it.id to Bond(points = 12, met = since.minusDays(9).toString()) },
                    )
                    val p = Arrivals.readyFor(s, v.id, day)
                    assertNotNull("$id/${v.id}: not ready to meet", p)
                    val m = Arrivals.meeting(p!!, s, cast, level, day)
                    val where = "$id/${v.id} $level ${target.code}·${base.code}${if (late) " late" else ""}"
                    check(where, m, setOfNotNull(v.id, by))
                    assertEquals(where, "culture", m!!.source)
                    assertEquals(where, level, m.level)
                    assertEquals("$where: the late line", late, m.late)
                }
            }
        }
    }

    @Test fun `every pack's templates put in who a newcomer, a mover and a baby are`() {
        for (id in Cultures.ids.filter { Cultures.manifest(it)?.status == "complete" }) {
            val culture = Cultures.load(id)
            Cultures.use(culture)
            val cast = castOf(id)
            val target = culture.language
            val folk = culture.people
            for (base in Lang.entries.filter { it != target }) {
                L10n.pair = LangPair(target, base)
                for (female in listOf(true, false)) {
                    val g = if (female) "female" else "male"
                    val first = if (female) folk.firstNames.female.first() else folk.firstNames.male.first()
                    val family = folk.surnames.first()
                    val trade = folk.trades.first { it.needs == null }.let { if (female) it.female else it.male }
                    val newcomer = Resident("n-new", day.minusDays(3).toString(), name = "$first $family", voice = g, role = trade.bi(), family = family)
                    val mover = newcomer.copy(id = "n-mover", culture = "elsewhere", language = "xx", from = "Bled")
                    val mum = Resident("n-mum", "2026-08-01", name = "${folk.firstNames.female.last()} $family", voice = "female", role = trade.bi(), family = family)
                    val dad = Resident("n-dad", "2026-08-02", name = "${folk.firstNames.male.last()} $family", voice = "male", role = trade.bi(), family = family)
                    val baby = Resident("n-baby", day.toString(), name = "$first $family", voice = g, born = day.toString(), family = family, parents = listOf(mum.id, dad.id))
                    val s = GameState(
                        age = Age.VAS, residents = listOf(mum, dad, newcomer, mover, baby), migrated = setOf(Arrivals.MIGRATION),
                        bonds = mapOf(mum.id to Bond(points = 6, met = "2026-08-02"), dad.id to Bond(points = 6, met = "2026-08-03")),
                    )
                    val people = Residents.people(s, cast, day) + cast
                    for ((r, speakers) in listOf(newcomer to setOf(newcomer.id), mover to setOf(mover.id), baby to setOf(mum.id, dad.id))) {
                        for (level in listOf("A1", "A2")) {
                            val p = Arrivals.readyFor(s, r.id, day)
                            assertNotNull("$id: ${r.id} not ready", p)
                            val m = Arrivals.meeting(p!!, s, people, level, day)
                            val where = "$id template for ${r.id} ($g) $level ${target.code}·${base.code}"
                            check(where, m, speakers)
                            assertEquals(where, "template", m!!.source)
                            val said = texts(m.dialog.lines).joinToString(" ")
                            assertTrue("$where: the name is said", first in said)
                        }
                    }
                }
            }
        }
    }
}
