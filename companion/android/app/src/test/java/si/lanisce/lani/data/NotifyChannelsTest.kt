package si.lanisce.lani.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.Screen
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * The notifications' channels and what goes where (data/NotifyChannels.kt): every event's channel and dot, the old "tutor"
 * channel's settings carried over, a child's kind words, the evening reminder's default, what the learner saw taken away.
 */
class NotifyChannelsTest {
    private val pair = L10n.pair
    private val now = 1_000_000_000L

    @Before fun jan() {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
    }

    @After fun back() {
        L10n.pair = pair
    }

    /** One event of each kind, as the bridge sends them. */
    private val events: List<BridgeEvent> = listOf(
        BridgeEvent.Reply("main", "Odlično! **Bravo**", null),
        BridgeEvent.Reply("rp-kavarna", "Kaj boste pili?", null),
        BridgeEvent.Reply("fam_c1", "Ena ura", null),
        BridgeEvent.Reply("ex-12", "Prav!", 9),
        BridgeEvent.Reply(TownQuestions.CONVERSATION + "q1", "Lepo vprašanje", null),
        BridgeEvent.ModulePublished("dual-drill", 2, "Dvojina", "Nov izziv"),
        BridgeEvent.PermissionRequest("r1", "Bash", "update-db.py", "", now - 60_000),
        BridgeEvent.PermissionRequest("r0", "Bash", "update-db.py", "", now - 10 * 60_000),
        BridgeEvent.AppUpdate(120, "1.20", null),
        BridgeEvent.PackPublished("kuhinja", "Kuhinja", null),
        BridgeEvent.ReadingPublished("r-mlin", "Mlin", "A2", null),
        BridgeEvent.ReadingPublished("r-old", "Staro", "A2", null, removed = true),
        BridgeEvent.CardPublished("c1", "Dvojina", null),
        BridgeEvent.WorksheetPublished("biti", "hw-1", "Domača naloga", null),
        BridgeEvent.WorksheetPublished("biti", "hw-0", "Staro", null, removed = true),
        BridgeEvent.GrammarPublished("dual", "Dvojina", null),
        BridgeEvent.PartnerChallenge("c1", "Maja", null, "💌", "Koliko je ura?"),
        BridgeEvent.VoiceUpdated,
        BridgeEvent.ScenePublished("ob-ognju", "Ob ognju", "🔥", null),
        BridgeEvent.ScenePublished("ob-ognju", "Zlatorog", "📖", null, story = true),
        BridgeEvent.ScenePublished("ob-ognju", null, null, "Nov pogovor", variant = true),
        BridgeEvent.ScenePublished("ob-ognju", null, null, null, variant = true),
        BridgeEvent.ScenePublished("mlin", null, null, null, removed = true),
        BridgeEvent.WordsAdded(listOf("w1")),
        BridgeEvent.CasesChanged(assigned = true, focus = "dative", note = "Dajalnik"),
        BridgeEvent.CasesChanged(assigned = false, focus = null, note = null),
        BridgeEvent.TipAsked("trade", null, now),
        BridgeEvent.LexiconUpdated("miza"),
        BridgeEvent.WordRequestDone("mize", "miza", listOf("table"), "w2", added = true),
        BridgeEvent.SentenceExplained("Grem domov."),
        BridgeEvent.ArrivalPublished("ana", null),
        BridgeEvent.TownGuest(TownActs.GIFT, "Il mio villaggio", "Mia"),
        BridgeEvent.TownQuestion(TownQuestions.QUESTION, "k1", "Il mio villaggio", "Mia", "Come stai?"),
        BridgeEvent.TownLink("request", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "Jan", child = "Teo", request = "req1"),
        BridgeEvent.TownLink("pending", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "Jan", child = "Teo", request = "req1"),
        BridgeEvent.TownProgressNote("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "Teo", "2026-W44", 4, 23, listOf("la/il")),
    )

    /** Where each event's notification belongs (null: none, it waits in the app): exhaustive, so a new kind must say. */
    private fun expected(ev: BridgeEvent): NotifyChannel? = when (ev) {
        is BridgeEvent.Reply -> if (ev.conversationId.startsWith("ex") || ev.conversationId.startsWith(TownQuestions.CONVERSATION)) null else NotifyChannel.MESSAGES
        is BridgeEvent.PartnerChallenge, is BridgeEvent.TownGuest, is BridgeEvent.TownQuestion -> NotifyChannel.MESSAGES
        is BridgeEvent.TownLink -> if (ev.kind == "pending") null else NotifyChannel.MESSAGES
        is BridgeEvent.WorksheetPublished -> if (ev.removed) null else NotifyChannel.SCHOOL
        is BridgeEvent.CasesChanged -> if (ev.assigned) NotifyChannel.SCHOOL else null
        is BridgeEvent.ModulePublished, is BridgeEvent.PackPublished, is BridgeEvent.GrammarPublished, is BridgeEvent.TownProgressNote -> NotifyChannel.NEW
        is BridgeEvent.ReadingPublished -> if (ev.removed) null else NotifyChannel.NEW
        is BridgeEvent.ScenePublished -> if (ev.removed || (ev.variant && ev.note == null)) null else NotifyChannel.NEW
        is BridgeEvent.AppUpdate -> NotifyChannel.SYSTEM
        is BridgeEvent.PermissionRequest -> if (now - ev.at < 5 * 60_000) NotifyChannel.SYSTEM else null
        BridgeEvent.VoiceUpdated, is BridgeEvent.WordsAdded, is BridgeEvent.LexiconUpdated, is BridgeEvent.SentenceExplained,
        is BridgeEvent.ArrivalPublished, is BridgeEvent.WordRequestDone, is BridgeEvent.TipAsked, is BridgeEvent.CardPublished -> null
    }

    @Test fun `every event kind goes to its channel or none`() {
        for (ev in events) assertEquals("$ev", expected(ev), Notices.of(ev, now = now)?.channel)
    }

    @Test fun `only what waits for the learner shows the dot`() {
        assertEquals(setOf(NotifyChannel.MESSAGES, NotifyChannel.VILLAGE, NotifyChannel.SCHOOL), NotifyChannel.entries.filter { it.badge }.toSet())
        val e = GameEvent("e1", EventKind.WOLVES, 2, now, now + 3 * 3_600_000)
        assertEquals(NotifyChannel.VILLAGE, Notices.villageAlert(e, now).channel)
        assertTrue(Notices.villageAlert(e, now).channel.badge)
        assertEquals(NotifyChannel.REMINDERS, Notices.reminder(5).channel)
        assertFalse(Notices.reminder(5).channel.badge)
        assertEquals(NotifyChannel.SYSTEM, Notices.notSaved("Odgovor", "400").channel)
        // what rings: the dot's kinds, the reminder and the system; news waits silently
        assertEquals(listOf(NotifyChannel.NEW), NotifyChannel.entries.filter { it.importance < 3 })
        assertEquals(NotifyChannel.entries.size, NotifyChannel.entries.map { it.id }.toSet().size)
    }

    @Test fun `one notification per thread, person or thing`() {
        fun tag(ev: BridgeEvent) = Notices.of(ev, now = now)!!.tag
        assertEquals(tag(BridgeEvent.Reply("main", "a", null)), tag(BridgeEvent.Reply("main", "b", null)))
        assertEquals(NotifyChannels.CHAT, tag(BridgeEvent.Reply("visit-1", "c", null)))
        assertEquals(NotifyChannels.TALK, tag(BridgeEvent.Reply("rp-kavarna", "d", null)))
        // the family's challenge and the answers in its thread
        assertEquals(tag(BridgeEvent.PartnerChallenge("c1", "Maja", null, "💌", "x")), tag(BridgeEvent.Reply("fam_c1", "y", null)))
        assertEquals(tag(BridgeEvent.TownGuest(TownActs.GIFT, "V", "Mia")), tag(BridgeEvent.TownGuest(TownActs.HELP, "V", "Mia")))
        assertFalse(tag(BridgeEvent.TownGuest(TownActs.GIFT, "V", "Mia")) == tag(BridgeEvent.TownGuest(TownActs.GIFT, "V", "Luka")))
        // an update takes the place of an older one's
        val u = Notices.of(BridgeEvent.AppUpdate(121, "1.21", null))!!
        assertTrue(u.replaces!!.matches(Notices.of(BridgeEvent.AppUpdate(120, "1.20", null))!!.tag))
    }

    @Test fun `the old channel present, absent or blocked`() {
        // a new phone, or made already: each as it is
        assertEquals(NotifyChannel.entries.map { NotifyChannels.Spec(it, it.importance, it.badge) }, NotifyChannels.specs(null))
        // the old one as it was made: the same
        assertEquals(NotifyChannels.specs(null), NotifyChannels.specs(NotifyChannels.Old(3, badge = true)))
        // blocked: every new one blocked, nothing rings that didn't
        assertTrue(NotifyChannels.specs(NotifyChannels.Old(0, badge = true)).all { it.importance == 0 })
        // silenced: none louder; without its dot: none with one
        assertTrue(NotifyChannels.specs(NotifyChannels.Old(2, badge = true)).all { it.importance <= 2 })
        assertTrue(NotifyChannels.specs(NotifyChannels.Old(3, badge = false)).none { it.badge })
        // the old one goes only once all new ones are there
        val all = NotifyChannel.entries.map { it.id }.toSet()
        assertTrue(NotifyChannels.deleteOld(true, all + NotifyChannels.OLD))
        assertFalse(NotifyChannels.deleteOld(true, all - "school" + NotifyChannels.OLD))
        assertFalse(NotifyChannels.deleteOld(false, all))
    }

    @Test fun `the channels are named in the learner's own pair`() {
        assertEquals("💬 Sporočila · Messages", NotifyChannels.name(NotifyChannel.MESSAGES, LangPair(Lang.SL, Lang.EN)))
        assertEquals("✨ Novo · New in Lani", NotifyChannels.name(NotifyChannel.NEW, LangPair(Lang.SL, Lang.EN)))
        assertEquals("🏘️ Villaggio · Dorf", NotifyChannels.name(NotifyChannel.VILLAGE, LangPair(Lang.IT, Lang.DE)))
    }

    /** No alarm, no streak, no number: in Jan's pair and in Teo's. */
    @Test fun `a child's village alert and reminder are kind`() {
        val unkind = Regex("""\d|attack|napad|angreif|attacc|streak|\bniz|Serie|serie|card|kartic|Karte|carte""", RegexOption.IGNORE_CASE)
        for (p in listOf(LangPair(Lang.SL, Lang.EN), LangPair(Lang.IT, Lang.DE))) {
            L10n.pair = p
            for (kind in EventKind.entries) {
                val n = Notices.villageAlert(GameEvent("e1", kind, 3, now, now + 3 * 3_600_000), now, child = true)
                assertNull("$p $kind: ${n.title}", unkind.find(n.title))
                assertNull("$p $kind: ${n.text}", unkind.find(n.text))
            }
            val r = Notices.reminder(12, child = true)
            assertNull("$p: ${r.title}", unkind.find(r.title))
            assertNull("$p: ${r.text}", unkind.find(r.text))
        }
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        val wolves = GameEvent("e1", EventKind.WOLVES, 3, now, now + 3 * 3_600_000)
        assertEquals("🏰 Stražar je nekaj videl · The watchman saw something", Notices.villageAlert(wolves, now, child = true).title)
        assertEquals("Pridi pogledat · Come and look", Notices.villageAlert(wolves, now, child = true).text)
        assertEquals("🔥 Vas te pričakuje · The village is looking forward to you", Notices.reminder(12, child = true).title)
        // an adult's as before
        assertTrue(Notices.villageAlert(wolves, now).title.contains("Wolves are attacking your village"))
        assertTrue(Notices.reminder(12).title.contains("12"))
    }

    @Test fun `the evening reminder is on for an adult and off for a child unless set`() {
        assertTrue(NotifyChannels.reminderOn(null, child = false))
        assertFalse(NotifyChannels.reminderOn(null, child = true))
        assertTrue(NotifyChannels.reminderOn(true, child = true))
        assertFalse(NotifyChannels.reminderOn(false, child = false))
    }

    /** The screen a notification's tap leads to (AppViewModel.open), as far as these tests need it. */
    private fun screenOf(link: DeepLink): Screen? = when (link) {
        DeepLink.Talk -> Screen.Talk
        DeepLink.Village -> Screen.Village
        DeepLink.Packs -> Screen.Packs
        DeepLink.Readings -> Screen.Readings
        DeepLink.Friends -> Screen.Friends
        DeepLink.Review -> Screen.Review(emptyList())
        is DeepLink.Scene -> Screen.Scene(link.id, link.focus)
        is DeepLink.Family -> Screen.Family(link.challengeId)
        is DeepLink.Question -> Screen.TownQuestion(link.key, link.asked)
        is DeepLink.TownProgress -> Screen.TownProgress(link.town)
        is DeepLink.School -> link.unit?.let { Screen.CourseUnit(it) } ?: Screen.School
        is DeepLink.Cases -> Screen.Cases(link.focus)
        else -> null
    }

    @Test fun `what a notification leads to takes it away once seen`() {
        val e = GameEvent("e1", EventKind.BEAR, 3, now, now + 3_600_000)
        val notices = events.mapNotNull { Notices.of(it, now = now) } + Notices.villageAlert(e, now) + Notices.reminder(3)
        for (n in notices) {
            val seen = when (val l = n.link) {
                DeepLink.Chat -> NotifyChannels.seen(Screen.Home, chatOpen = true)
                is DeepLink.Grammar -> NotifyChannels.seen(Screen.GrammarBook, chatOpen = false)
                is DeepLink.Module -> null // a drill's run: Screen.Player(module)
                null -> NotifyChannels.seen(Screen.Home, chatOpen = false, updateShown = true)
                else -> screenOf(l)?.let { NotifyChannels.seen(it, chatOpen = false) }
            } ?: continue
            assertTrue("${n.tag} ← ${n.link}", seen.any { it.matches(n.tag) })
        }
    }

    @Test fun `what isn't on screen stays`() {
        val reply = Notices.of(BridgeEvent.Reply("main", "a", null))!!
        val guest = Notices.of(BridgeEvent.TownGuest(TownActs.GIFT, "V", "Mia"))!!
        val homework = Notices.of(BridgeEvent.WorksheetPublished("biti", "hw-1", null, null))!!
        val home = NotifyChannels.seen(Screen.Home, chatOpen = false)
        for (n in listOf(reply, guest, homework)) assertFalse(n.tag, home.any { it.matches(n.tag) })
        // another unit's homework stays
        assertFalse(NotifyChannels.seen(Screen.CourseUnit("imeti"), chatOpen = false).any { it.matches(homework.tag) })
        // the village's event gone: its notification goes, wherever the learner is
        assertTrue(NotifyChannels.seen(Screen.Home, chatOpen = false, villageCalm = true).any { it.matches(NotifyChannels.VILLAGE) })
        assertNotNull(NotifyChannels.seen(Screen.Home, chatOpen = false).firstOrNull { it.matches(NotifyChannels.PERMISSION) })
    }

    @Test fun `an update's notification goes once it is installed`() {
        assertTrue(NotifyChannels.updateInstalled("update:120", 120))
        assertTrue(NotifyChannels.updateInstalled("update:119", 120))
        assertFalse(NotifyChannels.updateInstalled("update:121", 120))
        assertFalse(NotifyChannels.updateInstalled("chat", 120))
        assertFalse(NotifyChannels.updateInstalled(null, 120))
    }

    @Test fun `the village is calm without an event or once its time is up`() {
        val s = si.lanisce.lani.game.GameState()
        assertTrue(VillageAlert.calm(s, now))
        assertFalse(VillageAlert.calm(null, now))
        val e = GameEvent("e1", EventKind.STORM, 3, now - 10, now + 10)
        assertFalse(VillageAlert.calm(s.copy(event = e), now))
        assertTrue(VillageAlert.calm(s.copy(event = e), now + 10))
    }
}
