package si.lanisce.lani.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.JanRequests
import si.lanisce.lani.game.QuestQueue
import si.lanisce.lani.game.scene.TownMarker
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.ui.stage.Intros

/** What a request's row says (the villager's card, the Scroll, the intro): how close Jan is, and why one waits. */
class QuestRowsTest {
    private val vAliNa = JanRequests.quests.single { it.moduleId == "predlogi-kraja" }

    @Test fun `a request tried says its best try, the pass mark and the tries`() {
        assertEquals("🎯 Najboljše 7/12 · potrebuješ 8 · Best 7/12, you need 8  🔁 3 poskusi · 3 tries", progressText(vAliNa))
        assertNull(progressText(JanRequests.naTrznico)) // not tried yet: nothing to say
        // the Slovene counts in four forms
        val tries = listOf(1, 2, 3, 5).map { n -> progressText(vAliNa.copy(tries = n))!!.substringAfter("🔁 ") }
        assertEquals(listOf("1 poskus · 1 try", "2 poskusa · 2 tries", "3 poskusi · 3 tries", "5 poskusov · 5 tries"), tries)
    }

    @Test fun `a request waiting says why`() {
        assertEquals("⏸️ Najprej · First: Kam? Na tržnico! (-a → -o)", whyText(QuestQueue.Why.After(JanRequests.naTrznico)))
        assertEquals("🕰️ Čaka na vrsto · Waits its turn", whyText(QuestQueue.Why.Queued))
        assertEquals("💤 Vpraša te kdaj drugič · Asks you another day", whyText(QuestQueue.Why.Stale))
        assertEquals("📜 Kasneje · Later", laterTitle())
        assertEquals("🎯 Vadi, kar ti ne gre · Practise what's hard", practiseHardLabel())
    }

    @Test fun `the Scroll lists the requests up and, later, the ones waiting in order`() {
        val o = TownOverview.of(JanRequests.linked, emptyList(), JanRequests.today.atTime(15, 0), JanRequests.cast)
        val up = o.today.filterIsInstance<TodayItem.Task>().map { it.quest.moduleId ?: it.quest.id }
        assertEquals(listOf("dvojina-osnove", "tozilnik-osnove", JanRequests.podkev.id, "kam-na-trznico", JanRequests.zgodbe.id), up)
        assertEquals(listOf("imeti-in-iti", "rodilnik-po-nikalnici", "predlogi-kraja"), o.later.map { it.quest.moduleId })
        // who asks is listed once per request up, not for those waiting
        assertEquals(2, o.residents.single { it.name == "Kovač Tone" }.doing.count { it is si.lanisce.lani.ui.game.Doing.Asks })
    }

    @Test fun `a bubble counts someone's requests, zoomed out a place counts them all`() {
        val tone = TownMarker("quest:a", TownMarker.Kind.QUEST, "⚒️", "Tone: A, B", TownPlace.Fire, count = 2)
        val soup = TownMarker("happening:ob-ognju/juha", TownMarker.Kind.HAPPENING, "👵", "Juha", TownPlace.Fire)
        assertEquals("2", countBadge(tone, listOf(tone, soup)))
        assertNull(countBadge(soup, listOf(tone, soup)))
        val cluster = clusterBubbles(listOf(tone, soup)).single()
        assertEquals("3", countBadge(cluster, listOf(tone, soup)))
    }

    @Test fun `the intro says how close Jan is, the pass mark and why a request waits`() {
        val tone = si.lanisce.lani.ui.stage.Cast.standIn("Kovač Tone", "⚒️")
        val cloze = si.lanisce.lani.data.Exercise.Cloze("Grem ___ pošto.", listOf("na"))
        val m = si.lanisce.lani.data.Module("predlogi-kraja", 1, vAliNa.title, level = "A1", exercises = List(12) { cloze })
        val info = Intros.module(m, vAliNa, null, tone, bond = 0, why = QuestQueue.Why.After(JanRequests.naTrznico))
        assertEquals(8, info.practice.passMark) // the tutor's request is passed like any: said before Start
        assertEquals(progressText(vAliNa), info.progress)
        assertTrue(info.waits!!.startsWith("⏸️ Najprej · First: Kam? Na tržnico!"))
        // a module played for itself (its request done): no mark, nothing to say
        val done = Intros.module(m, vAliNa.copy(done = true), null, tone, bond = 0, why = QuestQueue.Why.Queued)
        assertEquals(0, done.practice.passMark)
        assertNull(done.progress)
        assertNull(done.waits)
        // a villager's own request, never tried: its mark from the run, nothing else
        val c = si.lanisce.lani.game.Challenge("⚒️ Podkev", "", "⚒️", List(5) { cloze }, List(5) { si.lanisce.lani.game.Res.STONE }, List(5) { null }, passMark = 3)
        val own = Intros.quest(JanRequests.podkev, c, tone)
        assertEquals(3, own.practice.passMark)
        assertNull(own.progress)
        assertNull(own.waits)
    }
}
