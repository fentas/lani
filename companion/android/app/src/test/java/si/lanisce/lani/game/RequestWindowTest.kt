package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.BusyVillage.at
import si.lanisce.lani.game.BusyVillage.cast
import si.lanisce.lani.game.BusyVillage.play
import si.lanisce.lani.game.BusyVillage.queue
import si.lanisce.lani.game.BusyVillage.today
import si.lanisce.lani.game.BusyVillage.zone
import si.lanisce.lani.game.scene.TownMarker
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.ui.game.challengeReviews
import si.lanisce.lani.ui.game.questEnd

/**
 * The map's window of requests (companion/GAME.md "Quests", "A window of requests"; Jan, 2026-10-27: "all the bubbles is a
 * bit overloading + if I do one it is right back"): a few at a time, one per villager, by an explicit order; a run clears
 * its "!" either way; who was helped rests; the window grows on a roll and comes back after a while.
 */
class RequestWindowTest {
    private val morning = at(today, 9)

    private fun givers(qs: List<Quest>) = qs.map { it.giver }
    private fun ids(qs: List<Quest>) = qs.map { it.moduleId ?: it.id }

    @Test fun `the busy village shows three requests on a phone and four on a tablet, one per villager, the rest behind one chip`() {
        val phone = queue(BusyVillage.state, morning)
        assertEquals(3, phone.up.size)
        assertEquals(phone.up.size, givers(phone.up).distinct().size)
        // started and closest to its pass mark first: Tone's 7 of 12 (needs 8), Mojca's 5 of 10 (needs 6), France's 4 of 10
        assertEquals(listOf("predlogi-kraja", "dvojina-osnove", "glagoli-gibanja"), ids(phone.up))
        // nothing lost: every open request is up or waits, once
        val all = phone.up.map { it.id } + phone.later.map { it.quest.id }
        assertEquals(BusyVillage.quests.map { it.id }.sorted(), all.sorted())
        assertEquals(10, phone.waiting)
        // the map: one bubble each, no count badges
        val bubbles = TownMarkers.questMarkers(phone.up, BusyVillage.state, cast)
        assertEquals(3, bubbles.size)
        assertTrue(bubbles.all { it.count == 1 })
        val tablet = queue(BusyVillage.state, morning, base = RequestWindow.TABLET)
        assertEquals(4, tablet.up.size)
        assertEquals("imeti-in-iti", ids(tablet.up).last())
        assertEquals(RequestWindow.TABLET, RequestWindow.baseFor(600))
        assertEquals(RequestWindow.PHONE, RequestWindow.baseFor(411))
    }

    @Test fun `what waits says why and comes up in order, the window's next first`() {
        val q = queue(BusyVillage.state, morning)
        // the next for the window (each villager's best), then the villagers' other requests
        assertEquals(listOf("imeti-in-iti", "ovce-mnozina", "cebele-mestnik", "cas-ura"), ids(q.later.take(4).map { it.quest }))
        assertTrue(q.later.all { it.why == QuestQueue.Why.Queued })
        // Tone's own waits behind his task of the tutor's, and asks another day after two days untouched
        assertEquals(QuestQueue.Why.Queued, q.why("q-2026-10-27-0"))
        assertEquals(QuestQueue.Why.Stale, queue(BusyVillage.state, at(today.plusDays(2), 9)).why("q-2026-10-27-0"))
    }

    @Test fun `the order is the next age's needs, the weak and due items, the closest to its mark, the tutor's newest, the oldest of the villagers'`() {
        val all = BusyVillage.quests
        val podkev = all.single { it.id == "q-2026-10-27-0" }
        val ovce = all.single { it.moduleId == "ovce-mnozina" }
        val dvojina = all.single { it.moduleId == "dvojina-osnove" }
        val tozilnik = all.single { it.moduleId == "tozilnik-osnove" }
        fun tier(q: Quest, needs: AgeNeeds = AgeNeeds.NONE, practice: Practice = Practice.NONE) = RequestWindow.rank(q, all, needs, practice).tier
        // nothing known: the closest to its mark, then the tutor's newest, then the villagers' own
        assertEquals(RequestWindow.Tier.CLOSE, tier(dvojina))
        assertEquals(RequestWindow.Tier.TUTOR, tier(ovce))
        assertEquals(RequestWindow.Tier.LOCAL, tier(podkev))
        val plain = all.sortedWith(RequestWindow.order(all, AgeNeeds.NONE, Practice.NONE))
        assertEquals(listOf("predlogi-kraja", "dvojina-osnove", "glagoli-gibanja", "imeti-in-iti", "ovce-mnozina", "naslonke"), ids(plain.take(6)))
        assertEquals("q-2026-10-27-0", plain.last().id)
        // due or rusty cards of the learner's in it, a weak pattern its module targets, a drill: before the closest
        val due = Practice(cards = setOf("vocab_hvala", "vocab_prosim"))
        assertEquals(RequestWindow.Tier.WEAK, tier(podkev, practice = due))
        assertEquals("q-2026-10-27-0", all.sortedWith(RequestWindow.order(all, AgeNeeds.NONE, due)).first().id)
        assertEquals(RequestWindow.Tier.WEAK, tier(tozilnik, practice = Practice(modules = setOf("tozilnik-osnove"))))
        assertEquals(RequestWindow.Tier.WEAK, tier(ovce.copy(helps = "ovce")))
        // what the next age needs, first of all: the last 40 stone of Trg's 700 (its reward is 60), or the friend it needs
        val short = AgeNeeds(res = mapOf(Res.STONE to 40))
        assertEquals(RequestWindow.Tier.AGE, tier(ovce, short))
        // every task of the tutor's pays 60 stone: all of them before the due cards' request, the one asked first first
        val byNeeds = all.sortedWith(RequestWindow.order(all, short, due))
        assertEquals("dvojina-osnove", ids(byNeeds).first())
        assertEquals("q-2026-10-27-0", byNeeds[12].id)
        // a reward short of what's missing completes nothing: no step of the age
        assertEquals(RequestWindow.Tier.TUTOR, tier(ovce, AgeNeeds(res = mapOf(Res.STONE to 600))))
        assertEquals(RequestWindow.Tier.AGE, tier(podkev, AgeNeeds(friends = setOf("Kovač Tone"))))
        // a rusty word holds the age back: a request with it polishes it
        assertEquals(RequestWindow.Tier.AGE, tier(podkev, AgeNeeds(rusty = true), Practice(rusty = setOf("vocab_zivjo"))))
    }

    @Test fun `the age's needs come from the village, a friend one pass away among them`() {
        val trg = Catalog.ageRules.getValue(Age.TRG)
        val almost = BusyVillage.state.copy(
            resources = trg.cost + (Res.STONE to trg.cost.getValue(Res.STONE) - 40),
            bonds = mapOf("luka" to Bond(points = 25), "tone" to Bond(points = 5)),
        )
        val needs = AgeNeeds.of(almost, cast)
        assertEquals(mapOf(Res.STONE to 40), needs.res)
        assertEquals(setOf("Pastir Luka"), needs.friends)
        // the stone's last bit: the tutor's tasks pay 60, so any completes it; Luka's makes the friend too, so his first
        val q = queue(almost, morning)
        assertEquals("ovce-mnozina", ids(q.up).first())
        assertTrue(q.up.all { RequestWindow.rank(it, almost.quests, needs, Practice.NONE).tier == RequestWindow.Tier.AGE })
    }

    @Test fun `a tie goes to the giver nearer the view's centre, else to the one asked first`() {
        val a = BusyVillage.local("q-a", "Gostilničarka Vida", "A")
        val b = BusyVillage.local("q-b", "Babica Micka", "B")
        val all = listOf(a, b)
        val due = Practice(cards = setOf("vocab_hvala"))
        assertEquals(listOf("q-a", "q-b"), all.sortedWith(RequestWindow.order(all, AgeNeeds.NONE, due)).map { it.id })
        val near = { q: Quest -> if (q.giver == "Babica Micka") 10f else 400f }
        assertEquals(listOf("q-b", "q-a"), all.sortedWith(RequestWindow.order(all, AgeNeeds.NONE, due, near)).map { it.id })
    }

    @Test fun `a pass clears its bubble, the giver thanks and rests, and another villager's comes up at once`() {
        val s0 = RequestWindow.settle(BusyVillage.state, queue(BusyVillage.state, morning), morning, zone)
        val tone = "tutor-predlogi-kraja-v1"
        val t = at(today, 10)
        val (s, r) = play(s0, tone, 9, 12, t)
        assertTrue(r.won)
        assertTrue(s.quests.single { it.id == tone }.done)
        val q = queue(s, t)
        // three still, Tone's place taken by Micka's (the next for the window), not by his own request
        assertEquals(3, q.up.size)
        assertEquals(listOf("dvojina-osnove", "glagoli-gibanja", "imeti-in-iti"), ids(q.up))
        // his own waits: he rests three hours, into the afternoon (13:00)
        val rest = q.why("q-2026-10-27-0")
        assertEquals(QuestQueue.Why.Resting(at(today, 13)), rest)
        assertEquals("Kovač Tone", RequestWindow.thanking(s.pace, t + 60_000).keys.single())
        assertTrue(RequestWindow.thanking(s.pace, t + RequestWindow.THANKS_MS).isEmpty())
        // after his rest it may come up again (his own request, once a place is free)
        assertNull(queue(s, at(today, 13, 1)).why("q-2026-10-27-0").takeIf { it is QuestQueue.Why.Resting })
    }

    @Test fun `a rest lasts three hours and into the next part of the day`() {
        assertEquals(at(today, 11), RequestWindow.restUntil(at(today, 6), zone)) // morning to the afternoon
        assertEquals(at(today, 13, 30), RequestWindow.restUntil(at(today, 10, 30), zone))
        assertEquals(at(today, 22), RequestWindow.restUntil(at(today, 19), zone)) // the evening to the night
        assertEquals(at(today.plusDays(1), 0, 30), RequestWindow.restUntil(at(today, 21, 30), zone))
        assertEquals(at(today, 5), RequestWindow.restUntil(at(today, 1), zone)) // a night's run: till the morning
        // below the mark: the next day's morning
        assertEquals(at(today.plusDays(1), 5), RequestWindow.backAt(at(today, 16), zone))
        assertEquals(at(today.plusDays(1), 5), RequestWindow.backAt(at(today, 0, 30), zone))
    }

    @Test fun `below the mark the bubble goes too, the villager thanks, it pays less and comes back the next morning`() {
        val s0 = RequestWindow.settle(BusyVillage.state, queue(BusyVillage.state, morning), morning, zone)
        val mojca = "tutor-dvojina-osnove-v1"
        val t = at(today, 16)
        val (s, r) = play(s0, mojca, 3, 10, t)
        assertFalse(r.won)
        // a fifth of the reward, the try counted, still open
        assertTrue(r.rewards.toString(), r.rewards.getValue(Res.STONE) in 1..15 && r.rewards.getValue(Res.WISDOM) in 1..3)
        val q = s.quests.single { it.id == mojca }
        assertFalse(q.done)
        assertEquals(2, q.tries)
        // no "!" on it now: back tomorrow at five, Mojca rests till seven
        val now = queue(s, t)
        assertFalse(now.up.any { it.id == mojca })
        assertEquals(QuestQueue.Why.Again(at(today.plusDays(1), 5)), now.why(mojca))
        assertEquals(QuestQueue.Why.Resting(at(today, 19)), now.why("tutor-naslonke-v1"))
        assertEquals(3, now.up.size)
        assertTrue(TownMarkers.questMarkers(now.up, s, cast).none { it.id == "quest:$mojca" })
        // her thanks: "Hvala! Jutri še enkrat?" (the culture's line below the mark)
        assertFalse(RequestWindow.thanking(s.pace, t + 1_000).getValue("Učiteljica Mojca").passed)
        // the evening: her rest is over (her newest waits its turn), the one she asked again still waits for tomorrow
        val evening = queue(s, at(today, 19, 30))
        assertEquals(QuestQueue.Why.Again(at(today.plusDays(1), 5)), evening.why(mojca))
        assertEquals(QuestQueue.Why.Queued, evening.why("tutor-naslonke-v1"))
        // the next morning it's back, and first: the closest to its mark of hers
        val next = queue(s, at(today.plusDays(1), 6))
        assertNull(next.why(mojca)?.takeIf { it is QuestQueue.Why.Again })
        assertEquals(mojca, (next.up + next.later.map { it.quest }).first { it.giver == "Učiteljica Mojca" }.id)
    }

    @Test fun `what a run missed goes into the reviews, and the tutor is told`() {
        // a villager's request is the learner's cards: the wrong ones are saved as failed reviews and come back
        val podkev = BusyVillage.quests.single { it.id == "q-2026-10-27-0" }
        val reviews = challengeReviews(podkev.cardIds, listOf(Verdict.CORRECT, Verdict.WRONG, Verdict.ALMOST, Verdict.WRONG, Verdict.CORRECT))
        assertEquals(5, reviews.size)
        assertTrue(reviews.filter { it.first in setOf("vocab_prosim", "vocab_ampak") }.all { it.second < 3 })
        // a task of the tutor's: its session_end says what was missed, and that it comes back tomorrow
        val dvojina = BusyVillage.quests.single { it.moduleId == "dvojina-osnove" }
        val after = QuestQueue.tried(dvojina, 3, 10, listOf("dvojina"))
        val end = questEnd(dvojina, after, false, 10, List(3) { Verdict.CORRECT } + Verdict.ALMOST + List(6) { Verdict.WRONG })
        assertEquals("tutor-dvojina-osnove-v1", end["request"]?.toString()?.trim('"'))
        assertEquals("false", end["passed"].toString())
        assertEquals("6", end["pass_mark"].toString())
        assertEquals("[3,4,5,6,7,8,9]", end["missed"].toString())
        assertEquals("[\"dvojina\"]", end["missed_pages"].toString())
        assertEquals("\"tomorrow\"", end["comes_back"].toString())
        assertNull(questEnd(dvojina, after.copy(done = true), true, 10, List(10) { Verdict.CORRECT })["comes_back"])
    }

    @Test fun `the window grows while the learner is on a roll and comes back after a long while untouched`() {
        val p = RequestPace(roll = 0, rollAt = 0)
        assertEquals(3, RequestWindow.size(3, p, morning))
        assertEquals(3, RequestWindow.size(3, p.copy(roll = 2, rollAt = morning), morning))
        assertEquals(4, RequestWindow.size(3, p.copy(roll = 3, rollAt = morning), morning)) // cleared what it showed
        assertEquals(5, RequestWindow.size(3, p.copy(roll = 7, rollAt = morning), morning))
        assertEquals(6, RequestWindow.size(3, p.copy(roll = 12, rollAt = morning), morning))
        assertEquals(6, RequestWindow.size(3, p.copy(roll = 40, rollAt = morning), morning)) // at most three more
        assertEquals(5, RequestWindow.size(4, p.copy(roll = 4, rollAt = morning), morning)) // a tablet's 4, 5, 6
        assertEquals(6, RequestWindow.size(4, p.copy(roll = 4 + 5, rollAt = morning), morning))
        // four hours without a finished run: back at the start
        assertEquals(3, RequestWindow.size(3, p.copy(roll = 12, rollAt = morning), morning + RequestWindow.ROLL_GAP_MS + 1))
        // played: three cleared in a row, the map shows four
        var s = RequestWindow.settle(BusyVillage.state, queue(BusyVillage.state, morning), morning, zone)
        var t = morning
        for (i in 0 until 3) {
            t += 10 * 60_000L
            val id = queue(s, t).up.first().id
            s = play(s, id, 10, 10, t).first
        }
        assertEquals(3, s.pace.roll)
        assertEquals(4, queue(s, t).up.size)
        assertEquals(4, s.pace.up.size) // kept with the village
        // the next run after a long pause starts a new roll
        val later = t + RequestWindow.ROLL_GAP_MS + 60_000L
        assertEquals(3, queue(s, later).up.size)
        val id = queue(s, later).up.first().id
        assertEquals(1, play(s, id, 1, 10, later).first.pace.roll)
    }

    @Test fun `what is up stays up while it's open, a newer and better one waits its turn, for the day`() {
        val s0 = RequestWindow.settle(BusyVillage.state, queue(BusyVillage.state, morning), morning, zone)
        assertEquals(listOf("tutor-predlogi-kraja-v1", "tutor-dvojina-osnove-v1", "tutor-glagoli-gibanja-v1"), s0.pace.up.map { it.id })
        // the tutor publishes a drill for Luka (the weak tier, above all three) and the learner's due cards come in
        val drill = BusyVillage.tutor("ovce-kratko", "Pastir Luka", "Ena ovca, dve ovci", since = today.toString(), helps = "ovce-mnozina")
        val s1 = s0.copy(quests = s0.quests + drill)
        val due = Practice(cards = setOf("vocab_hvala"))
        val q = queue(s1, at(today, 10), practice = due)
        assertEquals(listOf("predlogi-kraja", "dvojina-osnove", "glagoli-gibanja"), ids(q.up))
        assertEquals(QuestQueue.Why.Queued, q.why(drill.id))
        assertEquals(drill.id, q.later.first().quest.id) // the next for the window
        // the next day the window chooses again: the drill first
        assertEquals("ovce-kratko", ids(queue(s1, at(today.plusDays(1), 8), practice = due).up).first())
        // settling twice changes nothing
        val s2 = RequestWindow.settle(s1, queue(s1, at(today, 10)), at(today, 10), zone)
        assertTrue(s2 === RequestWindow.settle(s2, queue(s2, at(today, 10)), at(today, 10), zone))
    }

    @Test fun `while wolves are about none is up, and the same come back after`() {
        val s0 = RequestWindow.settle(BusyVillage.state, queue(BusyVillage.state, morning), morning, zone)
        val wolves = s0.copy(event = GameEvent("e1", EventKind.WOLVES, 3, morning + 3600_000L, 0))
        val q = queue(wolves, morning)
        assertTrue(q.up.isEmpty())
        assertEquals(listOf("predlogi-kraja", "dvojina-osnove", "glagoli-gibanja"), ids(q.later.take(3).map { it.quest }))
        assertTrue(q.later.take(3).all { it.why == QuestQueue.Why.Danger(EventKind.WOLVES) })
        // the window is kept as it was meanwhile
        assertTrue(RequestWindow.settle(wolves, q, morning, zone) === wolves)
        assertEquals(ids(queue(s0, morning).up), ids(queue(wolves.copy(event = null), morning).up))
    }

    @Test fun `the map's bubbles are the window's, one quiet chip counts the rest`() {
        val s = BusyVillage.state
        val markers = TownMarkers.questMarkers(queue(s, morning).up, s, cast)
        assertEquals(listOf("⚒️", "👩‍🏫", "👨‍🌾"), markers.filter { it.kind == TownMarker.Kind.QUEST }.map { it.emoji })
        assertEquals(10, queue(s, morning).waiting)
        // a drill whose task is passed is practice: not counted
        val passed = s.quests.map { if (it.moduleId == "ovce-mnozina") it.copy(done = true) else it } +
            BusyVillage.tutor("ovce-kratko", "Pastir Luka", "Ena ovca, dve ovci", helps = "ovce-mnozina")
        val q = queue(s.copy(quests = passed), morning)
        assertTrue(q.why("tutor-ovce-kratko-v1") is QuestQueue.Why.Spent)
        assertEquals(q.later.size - 1, q.waiting)
    }
}
