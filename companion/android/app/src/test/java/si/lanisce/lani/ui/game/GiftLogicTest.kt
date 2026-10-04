package si.lanisce.lani.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.ChallengeResult
import si.lanisce.lani.game.Chest
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Inventory
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Tool
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.ui.villagers.FriendGain
import java.time.LocalDate

class GiftLogicTest {
    private val potica = Catalog.goods.getValue("potica")
    private fun won(good: String, count: Int = 1) =
        ChallengeResult(true, mapOf(Res.FOOD to 30), emptyMap(), emptyList(), "", help = 3, thanks = Catalog.goods.getValue(good), thanksCount = count)

    @Test fun `a request passed reveals the giver's thank-you good, after the resources`() {
        val r = Reward(mapOf(Res.FOOD to 59, Res.WOOD to 13), won("potica"), from = GiftFrom("👵", "Babica Micka"))
        val g = GiftLogic.of(r).single()
        assertEquals("good:potica", g.key)
        assertEquals("Potica", g.item.sl)
        assertEquals(1, g.count)
        assertFalse(g.tool)
        assertEquals("👵 Babica Micka: V zahvalo · As thanks", g.from)
        assertEquals("❤️ Podari nekomu, komur je všeč · Give it to someone who likes it (+5 ♥)", g.use)
        assertEquals("Darilo: Potica · A gift: Potica (the rolled walnut cake)", g.announce)
    }

    @Test fun `nothing to reveal when the request failed or brought no good`() {
        assertTrue(GiftLogic.of(Reward(mapOf(Res.FOOD to 6), won("potica").copy(won = false))).isEmpty())
        assertTrue(GiftLogic.of(Reward(mapOf(Res.FOOD to 6))).isEmpty())
        assertTrue(GiftLogic.of(Reward(emptyMap(), won("potica").copy(thanks = null))).isEmpty())
    }

    @Test fun `a festival's good comes as many times as it gives, for the holiday`() {
        val r = Reward(emptyMap(), won("med", count = 2), from = GiftFrom("🐝", "Svetovni dan čebel · World Bee Day", festive = true))
        val g = GiftLogic.of(r).single()
        assertEquals(2, g.count)
        assertEquals("Za praznik · For the holiday: 🐝 Svetovni dan čebel · World Bee Day", g.from)
        assertTrue(g.announce, g.announce.endsWith(" ×2"))
        // a rare good pleases everyone
        val rare = GiftLogic.of(Reward(emptyMap(), won("mlado_vino"), from = GiftFrom("🍷", "Martinovo · St Martin's Day", festive = true))).single()
        assertEquals("✨ Redke dobrote so všeč vsem (+10 ♥) · Everyone loves a rare good (+10 ♥)", rare.use)
    }

    @Test fun `without a giver the good still says it's thanks`() {
        assertEquals("V zahvalo · As thanks", GiftLogic.of(Reward(emptyMap(), won("jabolka"))).single().from)
    }

    @Test fun `a friendship's level brings its gift, a tool with what it does or a small good`() {
        val today = LocalDate.of(2026, 9, 25)
        // Luka reaches "Prijatelj · Friend": his axe
        val before = GameState(bonds = mapOf("luka" to Bond(25)))
        val after = Bonds.add(before, "luka", 10, today)
        val axe = Chest.giftsBetween(before, after, "luka")
        val gain = FriendGain("luka", "Pastir Luka", "🐑", 10, 25, 35, gifts = axe)
        val tool = GiftLogic.of(gain).single()
        assertEquals("tool:luka", tool.key)
        assertTrue(tool.tool)
        assertEquals("Sekira", tool.item.sl)
        assertEquals("🐑 Pastir Luka: Za prijateljstvo · For your friendship", tool.from)
        assertTrue(tool.use, tool.use.startsWith("+10 % 🪵: "))
        // someone the catalog has no tool for gives a small good; a quest's thanks comes first, then the friend's gifts
        val ana = GameState(bonds = mapOf("ana" to Bond(25)))
        val small = Chest.giftsBetween(ana, Bonds.add(ana, "ana", 10, today), "ana")
        val r = Reward(emptyMap(), won("potica"), friend = FriendGain("ana", "Ana", "👩", 10, 25, 35, gifts = small))
        val both = GiftLogic.of(r)
        assertEquals(listOf("good:potica", ChestNews.good(small.single().good!!)), both.map { it.key })
        assertTrue(GiftLogic.of(null as FriendGain?).isEmpty())
    }

    @Test fun `the chest's news is set as things come in and cleared when it opens`() {
        val empty = Inventory()
        val one = Inventory(goods = mapOf("potica" to 1))
        val news = ChestNews().after(empty, one)
        assertEquals(setOf("good:potica"), news.unseen)
        assertTrue(news.shows(one))
        // a tool given, and more of a good; the first village loaded is no news
        val more = one.copy(goods = mapOf("potica" to 2, "kruh" to 1), tools = mapOf("luka" to Tool()))
        assertEquals(setOf("good:potica", "good:kruh", "tool:luka"), news.after(one, more).unseen)
        assertEquals(ChestNews(), ChestNews().after(null, more))
        // using up goods or forging a tool isn't news
        val forged = more.copy(goods = mapOf("potica" to 1), tools = mapOf("luka" to Tool(forged = 1)))
        assertEquals(ChestNews(), ChestNews().after(more, forged))
        // the better tool is
        assertEquals(setOf("tool:luka"), ChestNews().after(more, more.copy(tools = mapOf("luka" to Tool(tier = 2)))).unseen)
        // given away before the chest was opened: no dot
        assertFalse(news.shows(empty))
        // opened: seen
        assertFalse(news.opened().shows(more))
        assertTrue(news.opened().unseen.isEmpty())
    }
}
