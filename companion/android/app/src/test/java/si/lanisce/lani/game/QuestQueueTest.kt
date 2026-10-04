package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.ModuleInfo
import si.lanisce.lani.data.QuestMeta
import si.lanisce.lani.game.JanRequests.cast
import si.lanisce.lani.game.JanRequests.naTrznico
import si.lanisce.lani.game.JanRequests.podkev
import si.lanisce.lani.game.JanRequests.today
import si.lanisce.lani.game.JanRequests.vAliNa
import si.lanisce.lani.game.JanRequests.zgodbe
import si.lanisce.lani.game.scene.TownMarker
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.villagers.Residents

/** Requests up and waiting (companion/GAME.md "Quests": one bubble each, two at most, the drill before its task). */
class QuestQueueTest {
    private fun ids(qs: List<Quest>) = qs.map { it.moduleId ?: it.id }
    private fun queue(s: GameState, day: java.time.LocalDate = today) = Residents.queue(s, cast, day)

    @Test fun `the Primorska village shows one bubble per villager, never the same one stacked`() {
        val quests = TownMarkers.of(JanRequests.linked, emptyList(), cast, today).filter { it.kind == TownMarker.Kind.QUEST && it.id.startsWith("quest:") }
        // Mojca asked for three, Tone four, Janez one (in the order they first asked): three bubbles, each with how many are up
        assertEquals(listOf("👩‍🏫" to 2, "⚒️" to 2, "👴" to 1), quests.map { it.emoji to it.count })
        // Tone's own request is his bubble's first (a tap opens his card, which lists both)
        assertEquals("quest:${podkev.id}", quests[1].id)
        assertEquals("Kovač Tone: Podkev za konja, Kam? Na tržnico! (-a → -o)", quests[1].label)
        assertEquals("quest:${zgodbe.id}", quests[2].id)
        assertEquals(zgodbe.title, quests[2].label)
    }

    @Test fun `a villager shows two requests at most, their own first, then the tutor's newest`() {
        val q = queue(JanRequests.linked)
        assertEquals(listOf(podkev.id, "kam-na-trznico"), ids(q.upOf("Kovač Tone")))
        assertEquals(listOf("dvojina-osnove", "tozilnik-osnove"), ids(q.upOf("Učiteljica Mojca")))
        assertEquals(listOf(zgodbe.id), ids(q.upOf("Stari Janez")))
        // the rest wait in "Later", in the order they come up, each saying why
        assertEquals(
            listOf("imeti-in-iti" to "Queued", "rodilnik-po-nikalnici" to "Queued", "predlogi-kraja" to "After:kam-na-trznico"),
            q.later.map { w ->
                (w.quest.moduleId ?: w.quest.id) to when (val why = w.why) {
                    is QuestQueue.Why.After -> "After:${why.drill.moduleId}"
                    else -> why.toString()
                }
            },
        )
        // done ones are nowhere
        assertTrue((q.up + q.later.map { it.quest }).none { it.done })
        assertEquals(8, q.up.size + q.later.size)
    }

    @Test fun `without a local request two of the tutor's are up, the newest first`() {
        val q = queue(JanRequests.linked.copy(quests = JanRequests.linked.quests.filter { it.id != podkev.id }))
        assertEquals(listOf("kam-na-trznico", "rodilnik-po-nikalnici"), ids(q.upOf("Kovač Tone")))
        assertTrue(q.laterOf("Kovač Tone").single().why is QuestQueue.Why.After)
    }

    @Test fun `the short drill is found for the task it shares a page with, and parks it`() {
        val linked = JanRequests.linked.quests
        assertEquals("predlogi-kraja", linked.single { it.moduleId == "kam-na-trznico" }.helps)
        // nothing else is a drill: the dual shares a page with "imeti in iti", but it is a full module
        assertEquals(1, linked.count { it.helps != null })
        assertEquals(naTrznico.id, QuestQueue.drillFor(linked.single { it.moduleId == "predlogi-kraja" }, linked)?.id)
        // no pages known (an older bridge, the book not loaded): no link, nothing changes
        assertSame(JanRequests.quests, QuestQueue.link(JanRequests.quests, { emptySet() }, JanRequests.short))
        // not tagged short: no link either
        assertSame(JanRequests.quests, QuestQueue.link(JanRequests.quests, { JanRequests.pages[it].orEmpty() }, emptySet()))
    }

    @Test fun `the parked task comes back once its drill is passed, first among the tutor's`() {
        val (s, r) = GameEngine.completeQuest(JanRequests.linked, naTrznico.id, 9, 11, JanRequests.noonOf(today))
        assertTrue(r.won)
        val q = queue(s)
        assertEquals(listOf(podkev.id, "predlogi-kraja"), ids(q.upOf("Kovač Tone")))
        assertEquals(listOf("rodilnik-po-nikalnici"), ids(q.laterOf("Kovač Tone").map { it.quest }))
        assertTrue(QuestQueue.returning(s.quests.single { it.moduleId == "predlogi-kraja" }, s.quests))
        // failing the drill keeps it up and the task behind it
        val (f, _) = GameEngine.completeQuest(JanRequests.linked, naTrznico.id, 3, 11, JanRequests.noonOf(today))
        assertEquals(listOf(podkev.id, "kam-na-trznico"), ids(queue(f).upOf("Kovač Tone")))
        assertTrue(queue(f).why(vAliNa.id) is QuestQueue.Why.After)
    }

    @Test fun `a local request never started waits two days after it was asked, and expires as before`() {
        val tomorrow = today.plusDays(1)
        // asked on the 29th (as the app now keeps it)
        val asked = JanRequests.linked.copy(quests = JanRequests.linked.quests.map { if (it.id == podkev.id) it.copy(since = "2026-09-29") else it })
        assertNull(queue(asked, today).why(podkev.id)) // the day after: up
        val q = queue(asked, tomorrow)
        assertEquals(QuestQueue.Why.Stale, q.why(podkev.id))
        // Tone's two slots go to the tutor's; Janez's request of today stays up
        assertEquals(listOf("kam-na-trznico", "rodilnik-po-nikalnici"), ids(q.upOf("Kovač Tone")))
        assertEquals(listOf(zgodbe.id), ids(q.upOf("Stari Janez")))
        // started once, it stays up
        val tried = asked.copy(quests = asked.quests.map { if (it.id == podkev.id) QuestQueue.tried(it, 2, 5) else it })
        assertNull(queue(tried, tomorrow).why(podkev.id))
        // one asked before the app counted tries (Jan's "Podkev" as it is) may have been started: it never waits by age
        assertNull(QuestQueue.since(podkev))
        assertNull(queue(JanRequests.linked, tomorrow).why(podkev.id))
        // it expires on its third day as before
        val (pruned, expired) = Quests.prune(asked, JanRequests.noonOf(java.time.LocalDate.of(2026, 10, 2)).plus(1))
        assertTrue(expired.any { it.id == podkev.id } && pruned.quests.none { it.id == podkev.id })
        // the tent's move waits for good: never stale
        assertFalse(QuestQueue.stale(podkev.copy(id = TentMove.ID, since = "2026-01-01"), today))
    }

    @Test fun `a try counts, keeps the best result and the rules missed`() {
        val q = JanRequests.quests.single { it.moduleId == "predlogi-kraja" }
        assertEquals(Triple(3, 7, 12), Triple(q.tries, q.best, q.bestOf))
        assertEquals(8, QuestQueue.passMark(q))
        val worse = QuestQueue.tried(q, 4, 12, listOf("kje-mestnik-orodnik"))
        assertEquals(Triple(4, 7, 12), Triple(worse.tries, worse.best, worse.bestOf))
        assertEquals(listOf("kje-mestnik-orodnik"), worse.missed)
        val again = QuestQueue.tried(worse, 7, 12, listOf("kam-tozilnik", "kje-mestnik-orodnik"))
        assertEquals(listOf("kam-tozilnik", "kje-mestnik-orodnik"), again.missed)
        // a local request's runs differ in size: the best is the best share
        val local = QuestQueue.tried(QuestQueue.tried(podkev, 3, 6), 3, 5)
        assertEquals(3 to 5, local.best to local.bestOf)
        assertEquals(0, QuestQueue.passMark(podkev))
    }

    @Test fun `the engine records each try, a pass included`() {
        val s = JanRequests.linked
        val (failed, _) = GameEngine.completeQuest(s, vAliNa.id, 6, 12, 0L, missed = listOf("kam-tozilnik"))
        val q = failed.quests.single { it.id == vAliNa.id }
        assertEquals(4, q.tries)
        assertEquals(7 to 12, q.best to q.bestOf)
        assertEquals("kam-tozilnik", q.missed.first())
        assertFalse(q.done)
        val (passed, r) = GameEngine.completeQuest(s, vAliNa.id, 10, 12, 0L)
        assertTrue(r.won)
        assertEquals(Triple(4, 10, true), passed.quests.single { it.id == vAliNa.id }.let { Triple(it.tries, it.best, it.done) })
    }

    @Test fun `tried three times without a drill it offers practice, with one it waits`() {
        val plain = JanRequests.quests // no link found yet
        assertTrue(QuestQueue.hard(plain.single { it.moduleId == "predlogi-kraja" }, plain))
        val linked = JanRequests.linked.quests
        assertFalse(QuestQueue.hard(linked.single { it.moduleId == "predlogi-kraja" }, linked))
        assertFalse(QuestQueue.hard(podkev, plain)) // never tried
        assertTrue(QuestQueue.hard(QuestQueue.tried(QuestQueue.tried(QuestQueue.tried(podkev, 1, 5), 2, 5), 2, 5), plain))
    }

    @Test fun `a module's quest block says which task it helps, an older bridge's is found by its pages`() {
        val quest = { helps: String? -> QuestMeta("Kovač Tone", "⚒️", "Tone gre na tržnico.", skill = "stone", helps = helps) }
        val modules = listOf(
            ModuleInfo("predlogi-kraja", 1, "V ali na", "A1", quest = quest(null)),
            ModuleInfo("kam-na-trznico", 1, "Kam? Na tržnico!", "A1", quest = quest("predlogi-kraja")),
            ModuleInfo("sam-sebi", 1, "Sam sebi", "A1", quest = quest("sam-sebi")),
        )
        val s = GameEngine.withTutorQuests(GameState(age = Age.VAS), modules, JanRequests.noonOf(today))
        assertEquals(listOf(null, "predlogi-kraja", null), s.quests.map { it.helps })
        assertTrue(s.quests.all { it.since == java.time.Instant.ofEpochMilli(JanRequests.noonOf(today)).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() })
        assertTrue(QuestQueue.of(s.quests, s.quests, today).why("tutor-predlogi-kraja-v1") is QuestQueue.Why.After)
        // a request made before the app knew the field takes the link from its module
        val older = GameState(age = Age.VAS, quests = s.quests.map { it.copy(helps = null) })
        assertEquals("predlogi-kraja", GameEngine.withTutorQuests(older, modules, 0L).quests[1].helps)
        // an older bridge (no field): found by the pages, when the drill is short
        val plain = modules.map { it.copy(quest = it.quest?.copy(helps = null)) }
        val tagged = plain.map { if (it.id == "kam-na-trznico") it.copy(tags = listOf("grammar", QuestQueue.SHORT)) else it }
        val found = GameEngine.withTutorQuests(GameState(age = Age.VAS), tagged, 0L) { JanRequests.pages[it].orEmpty() }
        assertEquals("predlogi-kraja", found.quests.single { it.moduleId == "kam-na-trznico" }.helps)
        assertNull(GameEngine.withTutorQuests(GameState(age = Age.VAS), plain, 0L) { JanRequests.pages[it].orEmpty() }.quests.single { it.moduleId == "kam-na-trznico" }.helps)
    }

    @Test fun `two drills that name each other park neither`() {
        val a = naTrznico.copy(helps = "predlogi-kraja")
        val b = vAliNa.copy(helps = "kam-na-trznico")
        val q = QuestQueue.of(listOf(a, b), listOf(a, b), today)
        assertEquals(2, q.up.size)
        assertTrue(q.later.isEmpty())
    }

    @Test fun `someone the cast doesn't know gets one bubble for their place`() {
        val a = naTrznico.copy(id = "a", giver = "Ribič Gregor", emoji = "🎣", moduleId = "a")
        val b = naTrznico.copy(id = "b", giver = "Ribič Gregor", emoji = "🎣", moduleId = "b")
        val m = TownMarkers.questMarkers(listOf(a, b), JanRequests.state, cast)
        assertEquals(1, m.size)
        assertEquals(2, m.single().count)
    }
}
