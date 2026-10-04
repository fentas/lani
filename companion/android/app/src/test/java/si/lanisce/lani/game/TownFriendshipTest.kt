package si.lanisce.lani.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.data.FeastNews
import si.lanisce.lani.data.FriendTown
import si.lanisce.lani.data.FriendTrouble
import si.lanisce.lani.data.FriendshipList
import si.lanisce.lani.data.GuestAid
import si.lanisce.lani.data.GuestEntry
import si.lanisce.lani.data.GuestFrom
import si.lanisce.lani.data.Mover
import si.lanisce.lani.data.MoverFrom
import si.lanisce.lani.data.MovesInfo
import si.lanisce.lani.data.SharedFeast
import si.lanisce.lani.data.TownActs
import si.lanisce.lani.data.TownMove
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * Friendship between towns (game/TownFriendship.kt): help against trouble (taken: the event's damage is less; sent: it
 * costs), the shared feast, people moving in and out, each once; a bilingual resident's lines; what a friend asks.
 */
class TownFriendshipTest {
    private val t0 = noon(day0)
    private val pair = L10n.pair
    private val mia = "b".repeat(32)
    private val luka = "c".repeat(32)

    @Before fun home() {
        L10n.pair = LangPair(Lang.SL, Lang.EN) // Jan's
        Cultures.use("primorska")
    }

    @After fun back() {
        Cultures.use(null as String?)
        L10n.pair = pair
    }

    private fun village(res: Int = 100) = GameEngine.newGame(1, t0).copy(age = Age.VAS, resources = Res.entries.associateWith { res })

    private val wolves = GameEvent("ev-1", EventKind.WOLVES, 3, t0, t0 + 36 * 3_600_000L)

    private val chiara = Mover(
        id = "m-chiara-furlan-1x", name = "Chiara Furlan", emoji = "👩", art = "woman", voice = "female",
        role = mapOf("it" to "Pescatrice", "sl" to "Ribička", "en" to "Fisher"), family = "Furlan", culture = "friuli", language = "it",
        from = MoverFrom(mia, "Il mio villaggio"),
    )

    @Test fun `help from a friend's town lessens a lost event's damage, and helpers show in its intro`() {
        val s = village().copy(event = wolves, buildings = listOf(Building("b1", BuildingType.HUT, 1), Building("b2", BuildingType.FIELD, 2)))
        val entry = GuestEntry("$mia:aid-1", "aid-1", TownActs.AID, GuestFrom(mia, "Il mio villaggio", "Mia", "friuli", "it"), "2026-09-01T10:00:00Z", aid = GuestAid("ev-1", "WOLVES"))
        val (helped, news) = Guests.apply(s, listOf(entry), day0, t0)
        assertEquals(1, helped.event!!.aid)
        assertEquals(listOf("Mia"), helped.event!!.helpers)
        assertTrue(news.single().text.contains("Mia"))
        assertEquals(listOf(entry.key), helped.guestbook)
        assertSame(helped, Guests.apply(helped, listOf(entry), day0, t0).first) // once
        val (alone, lost) = Events.outcome(s, wolves, won = false, expired = false, now = t0)
        val (together, less) = Events.outcome(helped, helped.event!!, won = false, expired = false, now = t0)
        assertTrue("with help: ${less.losses} ≤ ${lost.losses}", less.losses.all { (r, n) -> n <= (lost.losses[r] ?: 0) } && less.losses.values.sum() < lost.losses.values.sum())
        assertTrue(kotlin.math.abs(less.losses.getValue(Res.FOOD) * 2 - lost.losses.getValue(Res.FOOD)) <= 1) // half with one helper
        assertTrue(alone.res(Res.FOOD) < together.res(Res.FOOD))
        assertTrue(Events.intro(helped, helped.event!!, 8, null).contains("🤝 Mia"))
        assertEquals(1f, TownFriendship.damageFactor(0))
        assertEquals(0.25f, TownFriendship.damageFactor(9)) // at most three helpers count
    }

    @Test fun `help that comes when the trouble is over brings its supplies`() {
        val s = village(res = 20)
        val (n, line) = TownFriendship.aidArrived(s, "ev-gone", "Mia")
        assertEquals(30, n.res(Res.FOOD))
        assertEquals(30, n.res(Res.WOOD))
        assertEquals("🤝", line.first)
    }

    @Test fun `sending help costs its supplies once`() {
        val s = village(res = 25)
        assertTrue(TownFriendship.canAid(s))
        val n = TownFriendship.aided(s, "$mia:aid-1", mia, "Il mio villaggio", day0, t0)
        assertEquals(15, n.res(Res.FOOD))
        assertEquals(15, n.res(Res.WOOD))
        assertEquals(1, n.towns.getValue(mia).aid)
        assertSame(n, TownFriendship.aided(n, "$mia:aid-1", mia, "Il mio villaggio", day0, t0))
        assertFalse(TownFriendship.canAid(village(res = 5)))
    }

    @Test fun `a shared feast brings morale and help once`() {
        val s = village().copy(morale = 50)
        val (n, line) = TownFriendship.sharedFeast(s, "feast:$mia:2026-08-31:2026-09-01", mia, "Il mio villaggio", day0, t0)
        assertEquals(60, n.morale)
        assertEquals(s.help + TownFriendship.FEAST_HELP, n.help)
        assertEquals(1, n.towns.getValue(mia).feasts)
        assertTrue(line!!.second.contains("Il mio villaggio"))
        assertNull(TownFriendship.sharedFeast(n, "feast:$mia:2026-08-31:2026-09-01", mia, "Il mio villaggio", day0, t0).second)
    }

    @Test fun `someone of a friend's town moves in once, a resident of their culture, and speaks their language now and then`() {
        val s = village().copy(villagers = 2, residents = listOf(si.lanisce.lani.game.villagers.Resident("micka", "2026-08-01"), si.lanisce.lani.game.villagers.Resident("tone", "2026-08-01")))
        val done = TownMove("$mia:move-1", "move-1", "in", "me", "done", "2026-09-01T09:00:00Z", "2026-09-01T10:00:00Z", chiara)
        val offered = TownMove("$mia:move-2", "move-2", "in", "them", "offered", "2026-09-01T11:00:00Z", person = chiara.copy(id = "m-other-2y"))
        val (n, lines, arrived) = TownFriendship.moves(s, mia, "Il mio villaggio", listOf(done, offered), day0, t0)
        assertEquals(3, n.villagers)
        val r = n.residents.last()
        assertEquals(listOf(r), arrived)
        assertEquals("m-chiara-furlan-1x", r.id)
        assertEquals("friuli", r.culture)
        assertEquals("it", r.language)
        assertEquals("Il mio villaggio", r.from)
        assertEquals("Pescatrice · Fisher", r.role) // their trade in their language, with Jan's base
        assertEquals(1, n.towns.getValue(mia).moves)
        assertTrue(lines.single().second.contains("Chiara Furlan"))
        assertTrue("move:${done.key}" in n.guestbook)
        assertFalse("move:${offered.key}" in n.guestbook) // not done yet: nothing moves
        assertSame(n, TownFriendship.moves(n, mia, "Il mio villaggio", listOf(done), day0, t0).first) // once
        // the reconciliation keeps her (one villager more, one resident more)
        val (kept, _) = Residents.reconcile(n, listOf(Villager("micka", "Babica Micka", art = "grandma"), Villager("tone", "Kovač Tone", art = "man")), day0)
        assertTrue(kept.residents.any { it.id == r.id })
        // her lines: the village's (Slovene) and hers, in Italian with English, never in Slovene
        val v = Residents.villagerOf(r, emptyList(), day0)!!
        val greet = v.lines.greet
        assertTrue(greet.any { it.target(LangPair(Lang.SL, Lang.EN)) == "Dober dan!" })
        val italian = greet.filter { it.spokenIn(LangPair(Lang.SL, Lang.EN)) == "it" }
        assertTrue(italian.isNotEmpty() && italian.all { it.base(LangPair(Lang.SL, Lang.EN)).isNotBlank() && "sl" !in it.by })
        assertTrue(v.story.contains("Il mio villaggio"))
    }

    @Test fun `someone of this village's culture moving to a friend's is a line once, and a full village has no room`() {
        val out = TownMove("$luka:move-3", "move-3", "out", "them", "done", "2026-09-01T09:00:00Z", person = chiara.copy(culture = "primorska", language = "sl", name = "Neža Kos", voice = "female"))
        val (n, lines, arrived) = TownFriendship.moves(village(), luka, "Il mio villaggio", listOf(out), day0, t0)
        assertTrue(arrived.isEmpty())
        assertTrue(lines.single().first == "👋" && lines.single().second.contains("Neža Kos"))
        val full = village().copy(villagers = Residents.MAX_RESIDENTS, residents = List(Residents.MAX_RESIDENTS) { si.lanisce.lani.game.villagers.Resident("n-$it", "2026-08-01", name = "N $it") })
        val (f, l, a) = TownFriendship.moves(full, mia, "Il mio villaggio", listOf(TownMove("$mia:move-4", "move-4", "in", "me", "done", person = chiara)), day0, t0)
        assertTrue(a.isEmpty() && f.residents.size == Residents.MAX_RESIDENTS && l.single().first == "🏠")
        assertEquals(1, n.towns.getValue(luka).moves)
    }

    @Test fun `a newcomer leaves before someone who moved in from a friend's town`() {
        val moved = TownFriendship.resident(chiara, day0)
        val newcomer = si.lanisce.lani.game.villagers.Resident("n-ana-kos", "2026-08-10", name = "Ana Kos", voice = "female", family = "Kos")
        val s = village().copy(villagers = 1, residents = listOf(moved, newcomer))
        val (n, _) = Residents.reconcile(s, listOf(Villager("micka", "Babica Micka", art = "grandma")), day0)
        assertEquals(listOf(moved.id), n.residents.map { it.id })
    }

    @Test fun `the bridge's list applied - the shared feast and the moves, and what a friend asks shown once each`() {
        val town = FriendTown(
            id = mia, name = "Il mio villaggio", learner = "Mia", level = 3,
            event = FriendTrouble("ev-9", "WOLVES", can = true),
            feast = FeastNews(mine = "2026-08-31", theirs = "2026-09-01", shared = SharedFeast("feast:$mia:2026-08-31:2026-09-01", "2026-08-31", "2026-09-01")),
            moves = MovesInfo(list = listOf(
                TownMove("$mia:move-1", "move-1", "in", "me", "done", person = chiara),
                TownMove("$mia:move-5", "move-5", "out", "them", "offered", person = chiara.copy(id = "m-x-5")),
            )),
        )
        val list = FriendshipList(listOf(town))
        val a = TownFriendship.apply(village(), list, day0, t0)
        assertEquals(1, a.feasts)
        assertEquals(1, a.arrived.size)
        assertEquals(2, a.news.size)
        val again = TownFriendship.apply(a.state, list, day0, t0)
        assertSame(a.state, again.state)
        assertTrue(again.news.isEmpty())
        val alerts = TownFriendship.alerts(list, a.state)
        assertEquals(listOf("trouble:$mia:ev-9", "offer:$mia:move-5"), alerts.map { it.first })
        assertTrue(alerts[0].second.startsWith("🐺"))
        // help sent already, or not close enough yet: nothing to ask
        assertTrue(TownFriendship.alerts(town.copy(event = town.event!!.copy(aided = true), moves = MovesInfo()), null).isEmpty())
    }

    @Test fun `the levels have names and say what they bring`() {
        assertEquals("Dobro sosedstvo · Good neighbours", TownFriendship.name(1))
        assertEquals("Pobratenje · Twin towns", TownFriendship.name(9))
        assertEquals("Prijateljstvo", TownFriendship.levelName(2).of(Lang.SL))
        assertEquals("Amicizia", TownFriendship.levelName(2).of(Lang.IT))
        assertTrue(TownFriendship.unlocks(3).of(Lang.EN).contains("move"))
    }
}
