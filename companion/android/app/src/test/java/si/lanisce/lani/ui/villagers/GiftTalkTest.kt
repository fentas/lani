package si.lanisce.lani.ui.villagers

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/** Giving a good (companion/VILLAGERS.md, "Giving a good"): when the moment starts, what Jan says, how they take it. */
class GiftTalkTest {
    private val day = LocalDate.of(2026, 9, 24)
    private val t0 = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val pair = L10n.pair

    @After fun back() {
        L10n.pair = pair
    }

    private val cast: List<Villager> = File("../../cultures/primorska/villagers").listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        .sortedBy { it.name }.map { json.decodeFromString(Villager.serializer(), si.lanisce.lani.l10n.Learner.current.renderJson(it.readText())) }
    private fun v(id: String) = cast.first { it.id == id }

    private fun village(vararg goods: Pair<String, Int>): GameState = GameEngine.newGame(1, t0).copy(
        age = Age.ZASELEK, villagers = 4, morale = 50, lastTick = day.toString(), foundedOn = day.toString(),
    ).let { it.copy(chest = it.chest.copy(goods = goods.toMap())) }

    private val tiForms = setOf("Izvoli, to je zate.", "Nekaj imam zate.", "Upam, da ti bo všeč.")
    private val viForms = setOf("Izvolite, to je za vas.", "Nekaj imam za vas.", "Upam, da vam bo všeč.")

    @Test fun `Jan says it in their register, two right ways, and one in the other register that says why`() {
        L10n.pair = LangPair.DEFAULT
        for (d in 0L..20L) {
            val today = day.plusDays(d)
            // Luka: ti
            val ti = GiftTalk.phrases("ti", "Luka", today, "luka")
            assertEquals(3, ti.size)
            assertEquals(2, ti.count { it.right })
            assertTrue(ti.filter { it.right }.all { it.target in tiForms && it.why == null })
            val wrongTi = ti.single { !it.right }
            assertTrue(wrongTi.target in viForms)
            // the wrong one is a right one in the other register: the why names it and the right way
            val why = wrongTi.why!!
            assertTrue(why, why.startsWith("You say ti to Luka: “${wrongTi.target}” is the polite form. Say “"))
            assertTrue(why, ti.filter { it.right }.any { why.contains("“${it.target}”") })
            assertEquals(ti.first { it.right && why.contains("“${it.target}”") }.base, wrongTi.base)
            // Micka: vi
            val vi = GiftTalk.phrases("vi", "Micka", today, "micka")
            assertEquals(2, vi.count { it.right })
            assertTrue(vi.filter { it.right }.all { it.target in viForms })
            val wrongVi = vi.single { !it.right }
            assertTrue(wrongVi.target in tiForms)
            assertTrue(wrongVi.why!!, wrongVi.why!!.startsWith("You say vi to Micka: “${wrongVi.target}” is too familiar."))
        }
        // not always in the same order
        assertTrue((0L..6L).map { d -> GiftTalk.phrases("ti", "Luka", day.plusDays(d), "luka").indexOfFirst { !it.right } }.toSet().size > 1)
    }

    @Test fun `the gift moment's words are in the sl, en and it tables`() {
        val keys = listOf(
            "giftScene.whatDoYouSay", "giftScene.whyTi", "giftScene.whyVi", "giftScene.youGive",
            "chest.gaveToday", "chest.notForChildren", "villagerScreens.somethingElse",
        )
        for (lang in listOf(Lang.SL, Lang.EN, Lang.IT)) for (k in keys) assertTrue("${lang.code}: $k", k in L10n.table(lang))
        // the why reads the register in the target language's words
        L10n.pair = LangPair(Lang.IT, Lang.EN)
        assertTrue(GiftTalk.phrases("vi", "Franco", day, "franco").single { !it.right }.why!!.startsWith("You say Lei to Franco: “"))
    }

    @Test fun `in an Italian village it is tu and Lei, explained in Slovene`() {
        L10n.pair = LangPair(Lang.IT, Lang.SL)
        val tu = GiftTalk.phrases("ti", "Davide", day, "davide")
        assertTrue(tu.filter { it.right }.all { it.target in setOf("Tieni, è per te.", "Ho una cosa per te.", "Spero che ti piaccia.") })
        assertTrue(tu.filter { it.right }.all { it.base in tiForms })
        val lei = GiftTalk.phrases("vi", "Franco", day, "franco")
        assertTrue(lei.filter { it.right }.all { it.target in setOf("Tenga, è per Lei.", "Ho una cosa per Lei.", "Spero che Le piaccia.") })
        val wrong = lei.single { !it.right }
        assertTrue(wrong.why!!, wrong.why!!.startsWith("Tu vikaš: »${wrong.target}« je tikanje."))
    }

    @Test fun `a wrong pick says why and costs nothing, the right one hands it over`() {
        L10n.pair = LangPair.DEFAULT
        val m = GiftTalk.start(village("potica" to 1), v("zala"), "potica", day)!!
        val wrong = m.choices.indexOfFirst { !it.right }
        val right = m.choices.indexOfFirst { it.right }
        val tried = m.pick(wrong)
        assertFalse(tried.handed)
        assertEquals(setOf(wrong), tried.tried)
        assertEquals(m.choices[wrong].why, tried.why)
        assertEquals(1, tried.mistakes)
        assertSame(tried, tried.pick(wrong)) // crossed out
        val handed = tried.pick(right)
        assertTrue(handed.handed)
        assertEquals(m.choices[right], handed.said)
        assertNull(handed.why)
        assertSame(handed, handed.pick(wrong)) // said is said
        assertSame(m, m.pick(9))
    }

    @Test fun `how they take it, a favourite, an ordinary good, a rare one`() {
        val potica = Catalog.goods.getValue("potica")
        assertEquals(GiftReaction.LIKED, GiftTalk.reaction(potica, "zala"))
        assertEquals(GiftReaction.ORDINARY, GiftTalk.reaction(potica, "luka")) // Luka loves jota, bread, apples, socks
        assertEquals(GiftReaction.RARE, GiftTalk.reaction(Catalog.goods.getValue("krofi"), "luka"))
        assertEquals(GiftReaction.LIKED, GiftTalk.reaction(potica, "n-ana")) // a newcomer likes the defaults
        assertEquals(GiftReaction.ORDINARY, GiftTalk.reaction(Catalog.goods.getValue("podkev"), "n-ana"))
        val s = village("potica" to 1, "krofi" to 1)
        assertEquals(GiftReaction.LIKED, GiftTalk.start(s, v("zala"), "potica", day)!!.reaction)
        assertEquals(GiftReaction.ORDINARY, GiftTalk.start(s, v("luka"), "potica", day)!!.reaction)
        assertEquals(GiftReaction.RARE, GiftTalk.start(s, v("luka"), "krofi", day)!!.reaction)
    }

    @Test fun `no moment when it can't be given today, they got something already, none left, wine for a child`() {
        L10n.pair = LangPair.DEFAULT
        val s = village("potica" to 2, "rebula" to 1)
        assertNotNull(GiftTalk.start(s, v("zala"), "potica", day))
        val given = s.copy(chest = s.chest.copy(given = mapOf("zala" to day.toString())))
        assertNull(GiftTalk.start(given, v("zala"), "potica", day))
        assertEquals("Danes si že nekaj podaril: jutri spet 🙂 · You gave them something today: again tomorrow 🙂", GiftTalk.blocker(given, "potica", "zala", day))
        assertNotNull(GiftTalk.start(given, v("zala"), "potica", day.plusDays(1))) // tomorrow again
        assertNotNull(GiftTalk.start(given, v("luka"), "potica", day)) // someone else today
        assertNull(GiftTalk.start(s, v("zala"), "med", day))
        assertNull(GiftTalk.start(s, v("zala"), "rebula", day))
        assertNotNull(GiftTalk.start(s, v("marko"), "rebula", day))
    }

    @Test fun `they answer in their own gift lines, by the gift, someone without them in plain words`() {
        L10n.pair = LangPair.DEFAULT
        val luka = v("luka")
        for (d in 0L..9L) {
            val today = day.plusDays(d)
            assertTrue(luka.lines.gift.liked.any { it.target == GiftTalk.reply(luka, GiftReaction.LIKED, 0, today).sl })
            assertTrue(luka.lines.gift.ordinary.any { it.target == GiftTalk.reply(luka, GiftReaction.ORDINARY, 0, today).sl })
            assertEquals(luka.lines.gift.rare.single().target, GiftTalk.reply(luka, GiftReaction.RARE, 0, today).sl)
        }
        assertTrue((0L..9L).map { GiftTalk.reply(luka, GiftReaction.LIKED, 0, day.plusDays(it)).sl }.toSet().size > 1)
        // a tutor's villager, a newcomer: plain words that fit ti and vi
        val plain = luka.copy(id = "n-ana", lines = luka.lines.copy(gift = si.lanisce.lani.game.villagers.GiftLines()))
        assertEquals(Said("Oh, to mi je pa res všeč! Hvala!", "Oh, I really like this! Thank you!"), GiftTalk.reply(plain, GiftReaction.LIKED, 2, day))
        assertEquals(Said("Hvala lepa, zelo prijazno.", "Thank you, that's very kind."), GiftTalk.reply(null, GiftReaction.ORDINARY, 0, day))
        assertEquals(Said("Joj, kaj takega! Najlepša hvala!", "Oh my, something like this! Thank you so much!"), GiftTalk.reply(plain, GiftReaction.RARE, 0, day))
        L10n.pair = LangPair(Lang.IT, Lang.SL)
        assertEquals(Said("Che meraviglia! Grazie mille!", "Joj, kaj takega! Najlepša hvala!"), GiftTalk.reply(null, GiftReaction.RARE, 0, day))
    }
}
