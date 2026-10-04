package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import kotlin.random.Random

class PacksTest {
    private val raw = """
        {
          "schema": "lani.pack/v0", "id": "v-kuhinji", "title": "V kuhinji · In the kitchen", "emoji": "🍳",
          "level": "A1", "description": "Babica Micka's kitchen.", "giver": { "name": "Babica Micka", "emoji": "👵" },
          "source": "tutor", "future_field": 1,
          "words": [
            { "id": "kruh", "sl": "kruh", "en": "bread", "emoji": "🍞", "gender": "m", "plural": "kruhi",
              "example_sl": "Kruh je še topel.", "example_en": "The bread is still warm.", "note": "A staple." },
            { "id": "mleko", "sl": "mleko", "en": "milk" },
            { "id": "juha", "sl": "juha", "en": "soup", "gender": "f" },
            { "id": "sir", "sl": "sir", "en": "cheese" },
            { "id": "jajce", "sl": "jajce", "en": "egg", "gender": "n" },
            { "id": "vilice", "sl": "vilice", "en": "fork" },
            { "id": "noz", "sl": "nož", "en": "knife" },
            { "id": "zlica", "sl": "žlica", "en": "spoon" },
            { "id": "kroznik", "sl": "krožnik", "en": "plate" },
            { "id": "kozarec", "sl": "kozarec", "en": "glass" }
          ],
          "learned": ["mleko"]
        }
    """.trimIndent()

    private val pack = parsePack(raw)

    @Test fun `parses a pack with every optional field and ignores unknown ones`() {
        assertEquals("v-kuhinji", pack.id)
        assertEquals("👵", pack.giver?.emoji)
        assertEquals("tutor", pack.source)
        val kruh = pack.words.first()
        assertEquals("Kruh je še topel.", kruh.exampleSl)
        assertEquals("The bread is still warm.", kruh.exampleEn)
        assertEquals("kruhi", kruh.plural)
        assertNull(pack.words[1].emoji)
        assertEquals(listOf("mleko"), pack.learned)
    }

    @Test fun `a pack a tutor published before the rename (schema fluent pack v0) parses the same`() {
        assertEquals(pack, parsePack(raw.replace("\"lani.pack/v0\"", "\"fluent.pack/v0\"")))
    }

    @Test fun `parses the pack list with defaults`() {
        val list = parsePacks("""[{"id":"a","title":"A","level":"A2","total":20,"learned":12},{"id":"b","title":"B","total":8,"learned":8}]""")
        assertEquals("📚", list[0].emoji)
        assertEquals("curated", list[0].source)
        assertEquals(8, list[0].left)
        assertFalse(list[0].done)
        assertTrue(list[1].done)
    }

    @Test fun `pack_published event parses`() {
        val ev = BridgeEvent.parse("""{"type":"pack_published","id":"pri-zdravniku","title":"Pri zdravniku","note":"Doctor words"}""")
        assertEquals(BridgeEvent.PackPublished("pri-zdravniku", "Pri zdravniku", "Doctor words"), ev)
    }

    @Test fun `next words skip learned ones and take six at a time`() {
        val next = PackSession.nextWords(pack)
        assertEquals(6, next.size)
        assertTrue(next.none { it.id == "mleko" })
        val nearlyDone = pack.copy(learned = pack.words.take(3).map { it.id })
        assertEquals(7, PackSession.nextWords(nearlyDone).size) // 7 left: all of them, no lonely leftover
        assertTrue(PackSession.nextWords(pack.copy(learned = pack.words.map { it.id })).isEmpty())
    }

    @Test fun `quality mapping`() {
        assertEquals(4, PackSession.quality(Verdict.CORRECT))
        assertEquals(3, PackSession.quality(Verdict.ALMOST))
        assertEquals(2, PackSession.quality(Verdict.WRONG))
        assertEquals(3, PackSession.quality(null))
    }

    @Test fun `a word's quality is its weakest answer, 3 when only introduced`() {
        val words = PackSession.nextWords(pack)
        val tasks = PackSession.plan(pack, words, emptyList(), canSpeak = false, random = Random(1))
        val kruh = PackSession.itemId(pack.id, "kruh")
        val verdicts = tasks.map { if (it.card.id == kruh) Verdict.CORRECT else Verdict.ALMOST }.toMutableList()
        verdicts[tasks.indexOfLast { it.card.id == kruh }] = Verdict.WRONG
        val q = PackSession.qualities(pack.id, words, tasks, verdicts).toMap()
        assertEquals(2, q["kruh"])
        assertTrue(q.filterKeys { it != "kruh" }.values.all { it == 3 })
        // Stopped after the first answer: the rest were only introduced.
        val early = PackSession.qualities(pack.id, words, tasks, listOf(Verdict.CORRECT)).toMap()
        assertEquals(4, early[tasks.first().card.id.removePrefix("vocab_v-kuhinji_")])
        assertEquals(words.size - 1, early.values.count { it == 3 })
    }

    @Test fun `a hinted correct answer lowers the word's quality`() {
        val words = PackSession.nextWords(pack)
        val tasks = PackSession.plan(pack, words, emptyList(), canSpeak = false, random = Random(1))
        val first = tasks.first().card.id.removePrefix("vocab_${pack.id}_")
        val hints = tasks.indices.map { if (it == 0) 1 else 0 }
        val q = PackSession.qualities(pack.id, words, tasks, tasks.map { Verdict.CORRECT }, hints).toMap()
        assertEquals(3, q[first])
        assertTrue(q.filterKeys { it != first }.values.all { it == 4 })
    }

    @Test fun `practice covers every word twice, recognition first, never back to back`() {
        val words = PackSession.nextWords(pack)
        repeat(30) { seed ->
            val tasks = PackSession.plan(pack, words, emptyList(), canSpeak = true, random = Random(seed))
            assertEquals(words.size * 2, tasks.size)
            val ids = words.map { PackSession.itemId(pack.id, it.id) }.toSet()
            assertEquals(ids, tasks.take(words.size).map { it.card.id }.toSet())
            assertEquals(ids, tasks.drop(words.size).map { it.card.id }.toSet())
            assertTrue(tasks.take(words.size).none { it.variant.difficulty == 3 })
            assertTrue(tasks.zipWithNext().none { (a, b) -> a.card.id == b.card.id })
        }
    }

    @Test fun `suggestion prefers new tutor packs, then packs in progress, then the learner's level`() {
        val a = PackInfo("a", "A", level = "A2", total = 10)
        val b = PackInfo("b", "B", level = "A1", total = 10)
        val c = PackInfo("c", "C", level = "A1", total = 10, learned = 3)
        val t = PackInfo("t", "T", source = "tutor", total = 10)
        val done = PackInfo("d", "D", total = 8, learned = 8)
        assertEquals("t", PackSession.suggest(listOf(a, b, c, t), "A1") { it.id == "t" }?.id)
        assertEquals("c", PackSession.suggest(listOf(a, b, c, t), "A1")?.id)
        assertEquals("b", PackSession.suggest(listOf(a, b), "A1")?.id)
        assertNull(PackSession.suggest(listOf(done), "A1"))
        assertEquals(listOf("t", "c", "a", "d"), PackSession.order(listOf(done, a, c, t)) { it.id == "t" }.map { it.id })
    }

    @Test fun `the festivals' packs have their own section and are suggested only when their festival is near`() {
        val kitchen = PackInfo("v-kuhinji", "V kuhinji", level = "A1", total = 24)
        val started = PackInfo("stevila", "Števila", level = "A1", total = 24, learned = 3)
        // celebrated Martinovo: 6 of its 8 words known, in progress, but it doesn't push the curriculum aside
        val martin = PackInfo("praznik-martinovo", "Martinovo", level = "A2", total = 8, learned = 6, festival = "martinovo")
        val bozic = PackInfo("praznik-bozic", "Božič", level = "A2", total = 8, festival = "bozic")
        val pust = PackInfo("praznik-pust", "Pust", level = "A2", total = 8, learned = 8, festival = "pust")
        val all = listOf(martin, kitchen, bozic, started, pust)
        assertEquals("stevila", PackSession.suggest(all, "A1")?.id)
        assertEquals("v-kuhinji", PackSession.suggest(listOf(martin, kitchen, bozic), "A1")?.id)
        // Christmas is coming: learn its words early
        assertEquals("praznik-bozic", PackSession.suggest(all, "A1", festival = "bozic")?.id)
        // a festival whose pack is done isn't suggested; nor is anything when every pack is done
        assertEquals("stevila", PackSession.suggest(all, "A1", festival = "pust")?.id)
        assertNull(PackSession.suggest(listOf(pust), "A1", festival = "pust"))
        // a new tutor pack still comes first
        val t = PackInfo("t", "T", source = "tutor", total = 10)
        assertEquals("t", PackSession.suggest(all + t, "A1", festival = "bozic") { it.id == "t" }?.id)
        // the list: the curriculum without the festivals; the festivals by their next day, the finished ones last
        assertEquals(listOf("stevila", "v-kuhinji"), PackSession.order(all).map { it.id })
        val next = mapOf("martinovo" to java.time.LocalDate.parse("2026-11-11"), "bozic" to java.time.LocalDate.parse("2026-12-25"), "pust" to java.time.LocalDate.parse("2027-02-09"))
        assertEquals(listOf("praznik-martinovo", "praznik-bozic", "praznik-pust"), PackSession.feasts(all) { next[it] }.map { it.id })
        // GET /packs carries the festival
        val listed = parsePacks("""[{"id":"praznik-bozic","title":"Božič · Christmas","emoji":"🎄","level":"A2","festival":"bozic","source":"curated","total":8,"learned":2}]""")
        assertEquals("bozic", listed.single().festival)
        assertNull(parsePacks("""[{"id":"v-kuhinji","title":"V kuhinji","total":24,"learned":0}]""").single().festival)
    }

    @Test fun `a festival's run records one quality per word it asked, its weakest answer`() {
        val words = listOf("gos", "klet", "sod", "gos", "krst", "vinar")
        val verdicts = listOf(Verdict.CORRECT, Verdict.WRONG, Verdict.ALMOST, Verdict.WRONG, Verdict.CORRECT)
        // gos: right, then wrong → 2; the sixth wasn't answered (the run was left) → not recorded
        assertEquals(listOf("gos" to 2, "klet" to 2, "sod" to 3, "krst" to 4), PackSession.results(words, verdicts))
        assertTrue(PackSession.results(words, emptyList()).isEmpty())
        assertTrue(PackSession.results(emptyList(), verdicts).isEmpty())
        // the node gets them like a pack session's: POST /packs/:id/learn
        val w = Writes.learnPack("praznik-martinovo", PackSession.results(words, verdicts), 3, id = "x", now = 1L)
        assertEquals("/packs/praznik-martinovo/learn", w.path)
    }

    @Test fun `labels`() {
        assertEquals("m · on", PackSession.genderTag("m"))
        assertEquals("ž · ona", PackSession.genderTag("f"))
        assertEquals("s · ono", PackSession.genderTag("n"))
        assertNull(PackSession.genderTag(null))
        // "+6 besed · words" after a pack: the Slovene plural (it was besed(); ICU's now)
        assertEquals(
            listOf("beseda", "besedi", "besede", "besede", "besed", "besed"),
            listOf(1, 2, 3, 4, 5, 11).map { L10n.text(Lang.SL, "packLearnScreen.words", mapOf("n" to it)) },
        )
    }
}
