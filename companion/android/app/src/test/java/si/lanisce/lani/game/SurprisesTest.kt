package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.villagers.Resident

/** "Dnevno presenečenje · The day's surprise" (GAME.md): one a day, the same all day, about who is here. */
class SurprisesTest {
    private val everyone = listOf("micka", "luka", "zala", "janez", "marko", "vida", "tone")

    private fun village(who: List<String> = everyone, age: Age = Age.ZASELEK, seed: Long = 5) = GameState(
        seed = seed, age = age, residents = who.map { Resident(it, "2026-01-01") }, villagers = who.size,
        resources = Res.entries.associateWith { 100 }, lastTick = day0.toString(),
    )

    private fun days(n: Int) = (0 until n).map { day0.plusDays(it.toLong()) }

    @Test fun `one a day, from the seed and the date, the same all day, and it varies`() {
        val s = village()
        for (d in days(30)) assertEquals(Surprises.roll(s, d), Surprises.roll(s, d))
        val kinds = days(90).mapNotNull { Surprises.roll(s, it)?.kind }
        assertEquals(90, kinds.size) // every day from Tabor on
        assertEquals(Surprises.KINDS.toSet(), kinds.toSet())
        // another village, another year of surprises
        assertFalse(days(30).map { Surprises.roll(s, it) } == days(30).map { Surprises.roll(village(seed = 6), it) })
        // no surprises at the campfire yet
        assertNull(Surprises.roll(village(age = Age.OGENJ), day0))
    }

    @Test fun `who a surprise is about lives here`() {
        val noLuka = village(everyone - "luka")
        assertTrue(days(90).none { Surprises.roll(noLuka, it)?.kind == Surprises.LAMB })
        val noKids = village(everyone - "zala")
        assertTrue(days(90).none { Surprises.roll(noKids, it)?.kind == Surprises.RIDDLE })
        for (d in days(90)) {
            val sp = Surprises.roll(village(listOf("micka", "luka", "zala")), d) ?: continue
            when (sp.kind) {
                Surprises.LETTER -> assertTrue(sp.who, Surprises.letterOf(sp).to in listOf("micka", Surprises.JAN))
                Surprises.RIDDLE -> assertEquals("zala", sp.who)
                Surprises.LAMB -> assertEquals("luka", sp.who)
                else -> assertNull(sp.who)
            }
        }
        // a child born here asks riddles too
        val born = village(everyone - "zala").let { s -> s.copy(residents = s.residents + Resident("n-ana-furlan", "2025-11-01", name = "Ana Furlan", born = "2025-11-01", voice = "female")) }
        val riddle = days(90).mapNotNull { Surprises.roll(born, it) }.firstOrNull { it.kind == Surprises.RIDDLE }
        assertEquals("n-ana-furlan", riddle?.who)
    }

    @Test fun `the tick rolls it once a day, played, it's done until tomorrow`() {
        val s0 = village().copy(foundedOn = day0.toString())
        val ticked = GameEngine.tick(s0, day0, noon(day0), Fixtures.pool()).state
        assertEquals(Surprises.roll(s0, day0), ticked.surprise)
        // a second tick the same day keeps it
        assertEquals(ticked.surprise, GameEngine.tick(ticked, day0, noon(day0) + 1000, Fixtures.pool()).state.surprise)
        // play the day's first surprise that can be played
        val day = days(30).first { Surprises.roll(s0, it).let { sp -> sp != null && sp.kind != Surprises.PEDLAR } }
        val s = s0.copy(surprise = Surprises.roll(s0, day))
        val c = GameEngine.surpriseChallenge(s, Fixtures.pool(), day)
        assertNotNull(c)
        val (n, r) = Surprises.finish(s, c!!.exercises.size, c.exercises.size, day, noon(day))
        assertTrue(r.won)
        assertTrue(n.surprise!!.done)
        assertNull(Surprises.playable(n, day))
        assertNull(GameEngine.surpriseChallenge(n, Fixtures.pool(), day))
        assertEquals(s.help + Surprises.HELP, n.help)
        assertEquals(Surprises.reward(s), r.rewards)
        s.surprise!!.who?.let { assertTrue((n.bonds[it]?.points ?: 0) > 0) }
        // tomorrow brings a new one
        val next = GameEngine.tick(n, day.plusDays(1), noon(day.plusDays(1)), Fixtures.pool()).state
        assertEquals(day.plusDays(1).toString(), next.surprise?.on)
        assertFalse(next.surprise!!.done)
    }

    @Test fun `a surprise that goes wrong is kind, a third of it, and it's done`() {
        val day = days(60).first { Surprises.roll(village(), it)?.kind == Surprises.RIDDLE }
        val s = village().copy(surprise = Surprises.roll(village(), day))
        val (n, r) = Surprises.finish(s, 0, 2, day, noon(day))
        assertFalse(r.won)
        assertTrue(n.surprise!!.done)
        assertEquals(s.help, n.help)
        assertEquals(Surprises.reward(s).mapValues { (_, v) -> v / 3 }, r.rewards)
        assertTrue(r.message.contains("nisi uganil"))
    }

    @Test fun `the pedlar isn't played, he sells, and his rare good is on offer`() {
        val day = days(60).first { Surprises.roll(village(), it)?.kind == Surprises.PEDLAR }
        val s = village().copy(surprise = Surprises.roll(village(), day))
        assertNull(Surprises.playable(s, day))
        val stall = Chest.stalls(s, day).single { it.id == Chest.PEDLAR }
        assertTrue(stall.offers.first().good.rare)
        assertEquals(Catalog.RARE_STOCK, stall.offers.first().left)
        assertEquals(1 + Catalog.PEDLAR_OFFERS, stall.offers.size)
        // not the next day
        assertTrue(Chest.stalls(s, day.plusDays(1)).none { it.id == Chest.PEDLAR })
    }

    @Test fun `the runs, a letter's questions, the way, Luka's calls, riddles`() {
        fun sp(kind: String, variant: Int, who: String? = null) = Surprise(day0.toString(), kind, variant, who)
        val letter = Surprises.exercises(sp(Surprises.LETTER, 0, "marko"), 1, canSpeak = false)
        assertEquals(2, letter.size)
        val q = letter[0] as Exercise.Choice
        // the letter is read first, then hidden: its questions ask about it alone
        assertFalse(q.prompt.contains("Dragi Marko!"))
        assertEquals("✉️ Kdaj pride Paolo? · When is Paolo coming?", q.prompt)
        assertEquals("v petek", q.options[q.answer])
        val way = Surprises.exercises(sp(Surprises.PILGRIM, 3), 1, canSpeak = false).map { it as Exercise.Choice }
        assertEquals(3, way.size)
        for (w in way) assertEquals(Surprises.directions.first { d -> d.ask in w.prompt }.options.first(), w.options[w.answer])
        val heard = Surprises.exercises(sp(Surprises.LAMB, 2, "luka"), 1, canSpeak = true).map { it as Exercise.Choice }
        assertEquals(3, heard.size)
        assertTrue(heard.all { it.audio != null && Surprises.lambPlaces.first { p -> p.first == it.audio }.second == it.options[it.answer] })
        val read = Surprises.exercises(sp(Surprises.LAMB, 2, "luka"), 1, canSpeak = false).map { it as Exercise.Choice }
        assertTrue(read.all { it.audio == null && it.prompt.contains("Jagnje je") })
        val riddles = Surprises.exercises(sp(Surprises.RIDDLE, 9, "zala"), 1, canSpeak = false).map { it as Exercise.Choice }
        assertEquals(2, riddles.size)
        for (r in riddles) assertEquals(Surprises.riddles.first { it.sl in r.prompt }.options.first(), r.options[r.answer])
        // every letter has two questions, its answer among four options
        for (l in Surprises.letters) {
            assertEquals(2, l.questions.size)
            assertTrue(l.questions.all { it.options.size == 4 && it.options.toSet().size == 4 && " · " in it.explain })
        }
    }

    @Test fun `a letter is read first, its lines a sentence each to hear, then hidden while its questions are asked`() {
        fun sp(kind: String, variant: Int, who: String? = null) = Surprise(day0.toString(), kind, variant, who)
        val text = Surprises.text(sp(Surprises.LETTER, 0, "marko"))!!
        assertEquals("✉️ Preberi pismo · Read the letter", text.title)
        assertEquals(
            listOf(listOf("Dragi Marko!"), listOf("V petek pridem v Brda.", "Prosim, pripravi deset steklenic rebule."), listOf("Lep pozdrav,"), listOf("Paolo iz Gorice")),
            text.paragraphs,
        )
        assertEquals("Dragi Marko!\nV petek pridem v Brda. Prosim, pripravi deset steklenic rebule.\nLep pozdrav,\nPaolo iz Gorice", text.text)
        // every letter reads so; the other surprises have nothing to read first
        for (v in Surprises.letters.indices) assertTrue(Surprises.text(sp(Surprises.LETTER, v))!!.paragraphs.flatten().isNotEmpty())
        for (k in listOf(Surprises.PILGRIM, Surprises.LAMB, Surprises.RIDDLE, Surprises.PEDLAR)) assertNull(k, Surprises.text(sp(k, 0)))
        // the day's run carries it: a letter's challenge, not the others'
        val s = village()
        val letterDay = days(90).first { Surprises.roll(s, it)?.kind == Surprises.LETTER }
        val c = GameEngine.surpriseChallenge(s.copy(surprise = Surprises.roll(s, letterDay)), Fixtures.pool(), letterDay)!!
        assertEquals(Surprises.text(Surprises.roll(s, letterDay)!!), c.text)
        val riddleDay = days(90).first { Surprises.roll(s, it)?.kind == Surprises.RIDDLE }
        assertNull(GameEngine.surpriseChallenge(s.copy(surprise = Surprises.roll(s, riddleDay)), Fixtures.pool(), riddleDay)!!.text)
    }

    @Test fun `looking back at the letter takes a little of the reward, never the pass`() {
        val day = days(90).first { Surprises.roll(village(), it)?.kind == Surprises.LETTER }
        val s = village().copy(surprise = Surprises.roll(village(), day))
        val full = Surprises.finish(s, 2, 2, day, noon(day)).second
        val looked = Surprises.finish(s, 2, 2, day, noon(day), cost = 30).second
        assertTrue(looked.won)
        assertEquals(full.help, looked.help)
        assertEquals(Peeks.cut(Surprises.reward(s), 30), looked.rewards)
        assertTrue(looked.rewards.values.sum() < full.rewards.values.sum())
        // a letter not passed pays its third, less the looks, at least 1
        val lost = Surprises.finish(s, 0, 2, day, noon(day), cost = 30).second
        assertFalse(lost.won)
        assertEquals(Peeks.cut(Surprises.reward(s).mapValues { (_, v) -> v / 3 }, 30), lost.rewards)
        assertTrue(lost.rewards.values.all { it >= 1 })
    }

    @Test fun `its line names who it's about`() {
        val name = { id: String -> Chest.nameOf(village(), id) }
        assertEquals(
            "Poštar je prinesel pismo za Micko. Preberi ji ga! · The postman brought a letter for Micka. Read it to her!",
            Surprises.intro(Surprise("x", Surprises.LETTER, 1, "micka"), name),
        )
        assertEquals("Zala ima zate uganko. · Zala has a riddle for you.", Surprises.intro(Surprise("x", Surprises.RIDDLE, 0, "zala"), name))
        assertTrue(Surprises.intro(Surprise("x", Surprises.LETTER, 4), name).startsWith("Poštar je prinesel pismo zate."))
        // what the one on the stage says to start it
        for (k in Surprises.KINDS) assertTrue(k, " · " in Surprises.ask(Surprise("x", k, 0)))
        assertEquals("Pismo je prišlo zate! · A letter came for you!", Surprises.ask(Surprise("x", Surprises.LETTER, 4)))
    }
}
