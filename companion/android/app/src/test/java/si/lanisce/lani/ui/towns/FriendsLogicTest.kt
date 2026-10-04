package si.lanisce.lani.ui.towns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.FeastNews
import si.lanisce.lani.data.FriendTown
import si.lanisce.lani.data.FriendTrouble
import si.lanisce.lani.data.Friendships
import si.lanisce.lani.data.MoveCan
import si.lanisce.lani.data.MoveSkill
import si.lanisce.lani.data.Mover
import si.lanisce.lani.data.MovesInfo
import si.lanisce.lani.data.NextLevel
import si.lanisce.lani.data.SharedFeast
import si.lanisce.lani.data.TownMove
import si.lanisce.lani.data.TownRow
import si.lanisce.lani.data.TownSelf
import si.lanisce.lani.data.TownsList
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.FeastOption
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.Res
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.time.Instant

class FriendsLogicTest {
    private val ageName = { a: Age -> a.name.lowercase() }
    private val mia = TownRow(
        id = "fec16ecd34869e522e575d0fb9ba45de", name = "Il mio villaggio", learner = "Mia", culture = "friuli", language = "it",
        status = "ok", age = "VAS", villagers = 3, buildings = mapOf("HUT" to 3, "TENT" to 1, "FIELD" to 2, "CASTLE" to 1),
    )
    private val self = TownSelf(id = "x", name = "Moja vas", learner = "Jan", culture = "primorska", language = "sl", canInvite = true)

    @Test fun `a reachable town shows flag and name, learner, age and people, its buildings`() {
        val r = FriendsLogic.row(mia, ageName)
        assertEquals("🇮🇹 Il mio villaggio", r.title)
        assertEquals("Mia · 🏘️ vas · 👥 3", r.subtitle)
        // the age, then the buildings in the game's order, each type once; a type this app doesn't know is left out
        assertEquals("🏘️  ⛺ 🌾×2 🛖×3", r.thumbnail)
        assertNull(r.status)
    }

    @Test fun `a town that can't be seen now keeps how it showed itself, and says why`() {
        val gone = FriendsLogic.row(mia.copy(status = "unreachable", age = null, villagers = null, buildings = emptyMap()), ageName)
        assertEquals("🇮🇹 Il mio villaggio", gone.title)
        assertEquals("Mia", gone.subtitle)
        assertEquals("", gone.thumbnail)
        assertTrue(gone.status!!.startsWith("📡"))
        assertTrue(FriendsLogic.row(mia.copy(status = "refused"), ageName).status!!.startsWith("🔒"))
        assertTrue(FriendsLogic.row(mia.copy(status = "bad_answer"), ageName).status!!.startsWith("⚠️"))
        // a town without a village yet: no age, no picture but nothing wrong either
        val empty = FriendsLogic.row(mia.copy(age = null, villagers = 0, buildings = emptyMap()), ageName)
        assertEquals("Mia", empty.subtitle)
        assertEquals("", empty.thumbnail)
        assertNull(empty.status)
    }

    @Test fun `blank names still show something`() {
        val r = FriendsLogic.row(TownRow(id = "a", status = "unreachable"), ageName)
        assertEquals("?", r.title)
        assertEquals("?", r.subtitle)
    }

    @Test fun `the rows keep the bridge's order`() {
        val l = TownsList(self, listOf(mia, mia.copy(id = "b", learner = "Luka")))
        assertEquals(listOf(mia.id, "b"), FriendsLogic.rows(l, ageName).map { it.id })
    }

    @Test fun `only a grown-up's app invites and accepts`() {
        assertTrue(FriendsLogic.canLink(TownsList(self)))
        assertFalse(FriendsLogic.canLink(TownsList(self.copy(child = true, canInvite = false))))
        // a bridge that says a child may invite is not believed
        assertFalse(FriendsLogic.canLink(TownsList(self.copy(child = true, canInvite = true))))
        assertFalse(FriendsLogic.canLink(null))
    }

    @Test fun `a linked town can be visited, also while it isn't answering, not once it no longer knows this one`() {
        assertTrue(FriendsLogic.row(mia, ageName).canVisit)
        assertTrue(FriendsLogic.row(mia.copy(status = "unreachable"), ageName).canVisit)
        assertFalse(FriendsLogic.row(mia.copy(status = "refused"), ageName).canVisit)
    }

    @Test fun `the own town reads as name, learner and flag`() {
        assertEquals("Moja vas · Jan 🇸🇮", FriendsLogic.selfLine(self))
    }

    @Test fun `the thumbnail keeps to a few building types`() {
        val many = mapOf("TENT" to 1, "FIELD" to 1, "WELL" to 1, "HUT" to 1, "KOZOLEC" to 1, "BEEHIVE" to 1, "PALISADE" to 1, "CHURCH" to 1)
        assertEquals(6, FriendsLogic.thumbnail(null, many).split(" ").size)
        assertEquals("🔥", FriendsLogic.thumbnail(Age.OGENJ, emptyMap()))
        assertEquals("", FriendsLogic.thumbnail(null, mapOf("HUT" to 0)))
    }

    // --- friendship between towns -------------------------------------------------------------------------------------------

    private fun friend(level: Int, points: Int) = FriendTown(
        id = mia.id, name = "Il mio villaggio", learner = "Mia", language = "it", level = level, points = points,
        next = Friendships.LEVELS.getOrNull(level + 1)?.let { NextLevel(level + 1, it) },
    )

    private fun withPair(block: () -> Unit) {
        val before = L10n.pair
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        try {
            block()
        } finally {
            L10n.pair = before
        }
    }

    private val rich = GameEngine.newGame(1, 0).copy(resources = Res.entries.associateWith { 50 })

    @Test fun `a friend's card - the level, how far to the next and what it brings`() = withPair {
        val f = friend(1, 16)
        assertEquals("💛 Dobro sosedstvo · Good neighbours · 16/24", FriendsLogic.levelLine(f))
        assertEquals(0.5f, FriendsLogic.progress(f))
        assertTrue(FriendsLogic.nextLine(f).startsWith("✨ Naslednja stopnja: Prijateljstvo: skupna veselica"))
        assertTrue(FriendsLogic.nextLine(f).contains(" · Next: Friendship: a shared feast"))
        assertEquals(1f, FriendsLogic.progress(friend(4, 120)))
        assertTrue(FriendsLogic.nextLine(friend(4, 120)).startsWith("🏆"))
    }

    @Test fun `trouble at a friend's - help to send, what it costs, or why not yet`() = withPair {
        val f = friend(1, 16).copy(event = FriendTrouble("ev-1", "WOLVES", can = true))
        val n = FriendsLogic.notices(f, rich, null, child = false).single()
        assertTrue(n.text.startsWith("🐺 V vasi Il mio villaggio: Volkovi!"))
        assertEquals(FriendAction.Aid(mia.id), n.action)
        assertTrue(n.enabled && n.label.contains("10 🌾"))
        val poor = FriendsLogic.notices(f, rich.copy(resources = emptyMap()), null, child = false).single()
        assertFalse(poor.enabled)
        assertTrue(poor.text.contains("🧺"))
        assertNull(FriendsLogic.notices(f.copy(event = f.event!!.copy(aided = true)), rich, null, false).single().action)
        assertTrue(FriendsLogic.notices(f.copy(event = f.event!!.copy(can = false)), rich, null, false).single().text.contains("Dobro sosedstvo"))
    }

    @Test fun `a friend's feast to share - hold one, or why not yet`() = withPair {
        val f = friend(2, 30).copy(feast = FeastNews(theirs = "2026-09-26", openUntil = "2026-09-29"))
        val none = FriendsLogic.notices(f, rich, null, false).single()
        assertEquals(FriendAction.Feast, none.action)
        assertFalse(none.enabled) // no feast before the village has grown: says so
        assertTrue(none.text.contains("🌳"))
        val can = FriendsLogic.notices(f, rich, FeastOption(emptyMap(), emptyList(), null, 1), false).single()
        assertTrue(can.enabled)
        assertTrue(FriendsLogic.notices(f.copy(feast = FeastNews(shared = SharedFeast("k"))), rich, null, false).single().text.startsWith("🎉"))
    }

    @Test fun `moves - an offer to say yes or no to (not a child's), one's own to withdraw, and what a move waits for`() = withPair {
        val p = Mover("m-chiara-1", "Chiara Furlan", "👩", role = mapOf("it" to "Pescatrice", "en" to "Fisher"), language = "it")
        val offer = TownMove("b:move-1", "move-1", "in", "them", "offered", person = p)
        val f = friend(3, 60).copy(moves = MovesInfo(list = listOf(offer)))
        val n = FriendsLogic.notices(f, rich, null, child = false).single()
        assertTrue(n.text.contains("Chiara Furlan (Pescatrice)"))
        assertEquals(FriendAction.Answer(mia.id, "move-1", true), n.action)
        assertEquals(FriendAction.Answer(mia.id, "move-1", false), n.second)
        assertFalse(FriendsLogic.notices(f, rich, null, child = true).single().enabled) // a child's app doesn't say yes
        val mine = FriendsLogic.notices(f.copy(moves = MovesInfo(list = listOf(offer.copy(by = "me")))), rich, null, false).single()
        assertEquals(FriendAction.Answer(mia.id, "move-1", false), mine.action)
        // nothing open: offer one either way it can go, and say what the other waits for
        val can = f.copy(moves = MovesInfo(skill = MoveSkill("it", "A1", "A2"), into = MoveCan(false, "skill", level = "A1"), out = MoveCan(true)))
        val ns = FriendsLogic.notices(can, rich, null, false)
        assertEquals(listOf(FriendAction.Offer(mia.id, "out"), null), ns.map { it.action })
        assertTrue(ns[1].text.startsWith("📚") && ns[1].text.contains("Italijanščina, raven A2 (zdaj A1)"))
        val child = FriendsLogic.whyNot(can.copy(child = true, learner = "Luka"), MoveCan(false, "child"))!!
        assertTrue(child.contains("(Luka)"))
        // below close friends: no moves on the card (the next level says it)
        assertTrue(FriendsLogic.notices(friend(2, 30).copy(moves = can.moves), rich, null, false).isEmpty())
    }

    @Test fun `an invitation's minutes left count a started minute`() {
        val now = Instant.parse("2026-09-26T16:30:00Z")
        assertEquals(30L, FriendsLogic.minutesLeft("2026-09-26T17:00:00.000Z", now))
        assertEquals(1L, FriendsLogic.minutesLeft("2026-09-26T16:30:01Z", now))
        assertEquals(0L, FriendsLogic.minutesLeft("2026-09-26T16:00:00Z", now))
        assertNull(FriendsLogic.minutesLeft("soon", now))
    }
}
