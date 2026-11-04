package si.lanisce.lani.game.scene

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.app.VisitController
import si.lanisce.lani.app.TownVisit
import si.lanisce.lani.data.VisitContent
import si.lanisce.lani.data.VisitFetch
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The village speaks at the learner's level (companion/SCENES.md, "Dialogs by level"): a dialog or a happening with a
 * level reaches a learner at it or above, their own level's first, the levels below on the days it has nothing; what has
 * no level is for everyone, so every scene before levels plays as it did; a child at A1 never meets a B1 or B2 dialog.
 */
class DialogLevelsTest {
    private val pair = L10n.pair

    @After fun restore() {
        L10n.pair = pair
        Cultures.use(null as String?)
    }

    private val day = LocalDate.of(2026, 11, 3)
    private val luka = ScenePerson("luka", "Pastir Luka", "🐑", "shepherd", "bank", villager = "luka")
    private fun line(sl: String) = DialogLine(who = "luka", sl = sl, en = sl)
    private fun talk(id: String, level: String? = null) =
        Dialog(id, listOf(line("$id!"), DialogLine(choices = listOf(DialogChoice("Ja.", ok = true), DialogChoice("Ne.", why = "…"))), line("Adijo.")), level = level)

    /** Luka at the brook: two dialogs for everyone (the old ones), two at B1, two at B2; and a B2 talk of its own. */
    private val stream = SceneSpec(
        id = "ob-potoku", title = "Ob potoku", art = "stream", from = listOf("spot:riverbank"), people = listOf(luka),
        happenings = listOf(
            Happening("ovce", "Luka napaja ovce", who = "luka", dialog = "ovce", dialogs = listOf("ovce-2", "ovce-b1", "ovce-b1-2", "ovce-b2", "ovce-b2-2", "ovce")),
            Happening("sejem", "Luka o sejmu", who = "luka", `when` = listOf(TimeOfDay.MORNING), dialog = "sejem", level = "B2"),
        ),
        dialogs = listOf(
            talk("ovce"), talk("ovce-2"), talk("ovce-b1", "B1"), talk("ovce-b1-2", "B1"), talk("ovce-b2", "B2"), talk("ovce-b2-2", "B2"), talk("sejem"),
        ),
    )
    private val cast = listOf(Villager("luka", "Pastir Luka", "🐑", "shepherd"))
    private fun village(heard: Map<String, DialogsHeard> = emptyMap()) = GameState(
        seed = 3, age = Age.TABOR, residents = listOf(Resident("luka", "2026-09-01")), bonds = mapOf("luka" to Bond(met = "2026-09-01")),
        migrated = setOf("arrivals"), dialogsHeard = heard,
    )

    @Test fun `levels rank from A1 up - none is everyone's and an unknown one is held back`() {
        assertEquals(-1, DialogLevels.rank(null))
        assertEquals(0, DialogLevels.rank("A1"))
        assertEquals(3, DialogLevels.rank("b2"))
        assertEquals(DialogLevels.LEVELS.size, DialogLevels.rank("B3"))
        assertEquals(0, DialogLevels.learnerRank(null))
        assertEquals(0, DialogLevels.learnerRank("A0"))
        assertTrue(DialogLevels.fits(null, "A1"))
        assertTrue(DialogLevels.fits("B1", "B1"))
        assertTrue(DialogLevels.fits("A2", "B2"))
        assertFalse(DialogLevels.fits("B1", "A2"))
        assertFalse(DialogLevels.fits("B3", "C2"))
        // a dialog is its own level's, else its happening's
        assertEquals("B2", DialogLevels.of(talk("x"), stream.happenings[1]))
        assertEquals("B1", DialogLevels.of(talk("x", "B1"), stream.happenings[1]))
        assertNull(DialogLevels.of(talk("x"), stream.happenings[0]))
    }

    @Test fun `an A1 learner meets none of the B1 and B2 dialogs nor the B2 happening`() {
        for (lv in listOf("A1", "A2")) {
            val s = DialogLevels.forLearner(stream, lv)
            assertEquals(lv, listOf("ovce", "ovce-2"), s.dialogs.map { it.id })
            assertEquals(lv, listOf("ovce"), s.happenings.map { it.id })
            assertEquals(lv, listOf("ovce-2", "ovce"), s.happenings.single().dialogs)
            assertEquals(lv, "ovce", s.happenings.single().dialog)
        }
        val b1 = DialogLevels.forLearner(stream, "B1")
        assertEquals(listOf("ovce", "ovce-2", "ovce-b1", "ovce-b1-2"), b1.dialogs.map { it.id })
        assertEquals(listOf("ovce-2", "ovce-b1", "ovce-b1-2", "ovce"), b1.happenings.single().dialogs)
        // at B2 and above everything: the same scene
        assertSame(stream, DialogLevels.forLearner(stream, "B2"))
        assertSame(stream, DialogLevels.forLearner(stream, "C1"))
    }

    @Test fun `a happening whose dialog goes plays the first left - and one with nothing left goes`() {
        val s = stream.copy(
            happenings = listOf(
                Happening("a", "A", who = "luka", dialog = "ovce-b1", dialogs = listOf("ovce-b1", "ovce-2")),
                Happening("b", "B", who = "luka", dialogs = listOf("ovce-b1", "ovce-b2")),
            ),
        )
        val a1 = DialogLevels.forLearner(s, "A1")
        assertEquals(listOf("a"), a1.happenings.map { it.id })
        assertEquals("ovce-2", a1.happenings.single().dialog)
        assertEquals(listOf("ovce-2"), a1.happenings.single().dialogs)
        // a dialog only a held back happening plays goes with it (the car's road plays the scenes' dialogs)
        assertFalse(DialogLevels.forLearner(stream, "B1").dialogs.any { it.id == "sejem" })
    }

    @Test fun `scenes without levels are the same scenes - every list as it was`() {
        val old = stream.copy(happenings = listOf(stream.happenings[0].copy(dialogs = listOf("ovce-2", "ovce"))), dialogs = listOf(talk("ovce"), talk("ovce-2")))
        val list = listOf(old, old.copy(id = "drugi"))
        for (lv in DialogLevels.LEVELS) assertSame(lv, list, DialogLevels.forLearner(list, { lv }))
        // and the pick by level with a single rank is the pick of before, day after day
        val all = listOf("a", "b", "c")
        var h: DialogsHeard? = null
        for (d in 0 until 12) {
            val today = day.plusDays(d.toLong())
            val before = DialogVariants.pick(all, all, h, today)
            assertEquals(before, DialogVariants.pick(all, all, h, today) { DialogLevels.rank(null) })
            before?.let { h = DialogsHeard((h?.heard ?: emptyMap()) + (it to today.toString()), it) }
        }
    }

    @Test fun `the learner's own level first - a lower one only on a day it has none`() {
        val rank = { id: String -> DialogLevels.rankOf(stream, stream.happenings[0], id) }
        val all = stream.happenings[0].dialogs
        // a B2 learner: the B2 ones first, in order; then, while those rest, the B1 ones; the old ones last
        var h: DialogsHeard? = null
        val played = (0 until 8).map { d ->
            val today = day.plusDays(d.toLong())
            DialogVariants.pick(all, all, h, today, rank = rank)?.also { h = DialogsHeard((h?.heard ?: emptyMap()) + (it to today.toString()), it) }
        }
        assertEquals(listOf("ovce-b2", "ovce-b2-2", "ovce-b1", "ovce-b2", "ovce-b2-2", "ovce-b1-2", "ovce-b2", "ovce-b2-2"), played)
        // a B1 learner (the scene trimmed to B1): the B1 ones, then the old ones while they rest
        val b1 = DialogLevels.forLearner(stream, "B1")
        val b1rank = { id: String -> DialogLevels.rankOf(b1, b1.happenings[0], id) }
        val b1all = b1.happenings[0].dialogs
        h = null
        val b1played = (0 until 5).map { d ->
            val today = day.plusDays(d.toLong())
            DialogVariants.pick(b1all, b1all, h, today, rank = b1rank)?.also { h = DialogsHeard((h?.heard ?: emptyMap()) + (it to today.toString()), it) }
        }
        assertEquals(listOf("ovce-b1", "ovce-b1-2", "ovce-2", "ovce-b1", "ovce-b1-2"), b1played)
        // a visit's dice picks among the learner's own level's
        assertEquals("ovce-b2-2", DialogVariants.pick(all, all, null, day, dice = 0.9f, rank = rank))
        assertEquals("ovce-b2", DialogVariants.pick(all, all, null, day, dice = 0.1f, rank = rank))
    }

    @Test fun `in the village - today's variant by the learner's level, through the scenes trimmed to it`() {
        val evening = LocalDateTime.of(2026, 11, 3, 19, 0)
        fun today(lv: String, s: GameState = village()) = Happenings.active(DialogLevels.forLearner(listOf(stream), { lv }), s, evening, cast = cast).single().dialog
        assertEquals("ovce-2", today("A1"))
        assertEquals("ovce-b1", today("B1"))
        assertEquals("ovce-b2", today("B2"))
        // the B2 morning talk: on for a B2 learner, never for an A2 one
        val morning = LocalDateTime.of(2026, 11, 3, 9, 0)
        assertTrue(Happenings.active(DialogLevels.forLearner(listOf(stream), { "B2" }), village(), morning, cast = cast).any { it.happening.id == "sejem" })
        assertFalse(Happenings.active(DialogLevels.forLearner(listOf(stream), { "A2" }), village(), morning, cast = cast).any { it.happening.id == "sejem" })
        // all the B2 ones heard lately: a B1 one plays rather than a rest day
        val lately = village(mapOf("ob-potoku/ovce" to DialogsHeard(mapOf("ovce-b2" to "2026-11-02", "ovce-b2-2" to "2026-11-03"), "ovce-b2-2")))
        assertEquals("ovce-b1", today("B2", lately))
    }

    @Test fun `a child's village - at A1 sixty days never bring a B1 or B2 dialog, at B1 never a B2 one`() {
        for ((lv, never) in listOf("A1" to setOf("A2", "B1", "B2"), "A2" to setOf("B1", "B2"), "B1" to setOf("B2"))) {
            var s = village()
            val heard = mutableSetOf<String>()
            for (d in 0 until 60) {
                val now = LocalDateTime.of(2026, 11, 3, 19, 0).plusDays(d.toLong())
                for (a in Happenings.active(DialogLevels.forLearner(listOf(stream), { lv }), s, now, cast = cast)) {
                    val id = a.dialog ?: a.happening.dialog ?: continue
                    val level = DialogLevels.of(stream.dialogs.first { it.id == id }, stream.happenings.first { it.id == a.happening.id })
                    assertFalse("$lv met $id ($level)", level in never)
                    heard += id
                    s = DialogVariants.record(s, a.key, id, now.toLocalDate())
                }
            }
            assertTrue(lv, heard.isNotEmpty())
        }
    }

    @Test fun `a visit - the town's dialogs at the visitor's level in its language`() {
        val v = TownVisit("t", "Town", "Mara", "friuli", "it")
        val got = VisitFetch.Got(VisitContent(listOf(stream.copy(language = "it")), "2026-11-03T10:00:00Z", false), stale = false, fetchedAt = "2026-11-03T10:00:00Z")
        val at = { lv: String -> VisitController.settle(v, VisitFetch.Failed("x"), VisitFetch.Failed("x"), got, day, level = { if (it == "it") lv else "C2" }) }
        assertEquals(listOf("ovce", "ovce-2"), at("A1").scenes.single().dialogs.map { it.id })
        assertEquals(7, at("B2").scenes.single().dialogs.size)
        // a visitor whose level isn't said: as at A1
        assertEquals(2, VisitController.settle(v, VisitFetch.Failed("x"), VisitFetch.Failed("x"), got, day).scenes.single().dialogs.size)
    }

    @Test fun `the app reads a level on a dialog and a happening - and an older file without them as before`() {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        val raw = """{"id":"s","title":"S","art":"stream","from":["fire"],"happenings":[{"id":"h","title":"H","who":"p","dialog":"a","dialogs":["b","a"]},
            {"id":"g","title":"G","who":"p","dialog":"c","level":"B2"}],
            "dialogs":[{"id":"a","lines":[]},{"id":"c","lines":[]}],"variants":[{"id":"b","lines":[],"level":"B1"}]}"""
        val s = parseScene(raw)
        assertEquals(listOf(null, null, "B1"), s.dialogs.map { it.level })
        assertEquals(listOf(null, "B2"), s.happenings.map { it.level })
    }

    // --- the curated content -----------------------------------------------------------------------------------------

    private val scenesDir = listOf(File("../../scenes"), File("../scenes"), File("companion/scenes")).first { it.isDirectory }
    private val culturesDir = listOf(File("../../cultures"), File("../cultures"), File("companion/cultures")).first { it.isDirectory }

    /** Every curated scene file: the Slovene village's and each culture pack's own, with its language. */
    private fun curated(): List<Pair<String, File>> =
        scenesDir.listFiles { f -> f.extension == "json" }.orEmpty().map { "primorska" to it } +
            culturesDir.listFiles().orEmpty().filter { it.isDirectory }.flatMap { c ->
                File(c, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().map { c.name to it }
            }

    @Test fun `the curated scenes - what has no level stays for everyone, in its order, at every level`() {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        var levelled = 0
        for ((_, f) in curated().sortedBy { it.second.path }) {
            val s = parseScene(f.readText())
            levelled += s.dialogs.count { it.level != null }
            for (lv in DialogLevels.LEVELS) {
                val t = DialogLevels.forLearner(s, lv)
                val plain = s.dialogs.filter { d -> d.level == null && s.happenings.filter { d.id in DialogVariants.of(it) }.let { hs -> hs.isEmpty() || hs.any { it.level == null } } }
                assertTrue("${s.id} at $lv: ${plain.map { it.id }}", t.dialogs.containsAll(plain))
                for (h in s.happenings.filter { it.level == null }) {
                    val kept = t.happenings.first { it.id == h.id }
                    val mine = DialogVariants.of(h).filter { id -> DialogLevels.fits(s.dialogs.firstOrNull { it.id == id }?.level, lv) }
                    assertEquals("${s.id}/${h.id} at $lv", mine, DialogVariants.of(kept))
                    // the happening's dialog (what an older app plays) has no level
                    assertNull("${s.id}/${h.id}", h.dialog?.let { id -> s.dialogs.firstOrNull { it.id == id }?.level })
                }
                // nothing above the learner left
                assertTrue("${s.id} at $lv", t.dialogs.none { !DialogLevels.fits(it.level, lv) } && t.happenings.none { !DialogLevels.fits(it.level, lv) })
            }
        }
        assertTrue(levelled >= 0)
    }
}
