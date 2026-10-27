package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    private fun reply(conv: String) = BridgeEvent.Reply(conv, "Bravo!", score = null)

    @Test fun `every link survives the intent extra`() {
        val all = listOf(
            DeepLink.Chat, DeepLink.Talk, DeepLink.Village, DeepLink.Packs, DeepLink.Readings, DeepLink.Module("m_clitics"), DeepLink.Family("abc123"),
            DeepLink.Scene("kuhinja"), DeepLink.Scene("kuhinja", "kuhinja/juha"), DeepLink.Scene("ob-ognju", "kettle"),
            DeepLink.Home, DeepLink.Review, DeepLink.Friends, DeepLink.Grammar("sklanjatev"), DeepLink.Settings(), DeepLink.Settings("tutor"),
            DeepLink.School(), DeepLink.School("biti"), DeepLink.Question("k1", asked = true),
        )
        for (l in all) assertEquals(l, DeepLink.parse(DeepLink.encode(l)))
    }

    @Test fun `unknown or incomplete extras open nothing`() {
        assertNull(DeepLink.parse(null))
        assertNull(DeepLink.parse(""))
        assertNull(DeepLink.parse("module:"))
        assertNull(DeepLink.parse("family"))
        assertNull(DeepLink.parse("elsewhere"))
        assertNull(DeepLink.parse("grammar"))
        assertNull(DeepLink.parse("scene:"))
        assertEquals(DeepLink.Scene("kuhinja"), DeepLink.parse("scene:kuhinja@"))
    }

    @Test fun `a notification leads to what it is about`() {
        assertEquals(DeepLink.Chat, DeepLink.of(reply("main")))
        assertEquals(DeepLink.Talk, DeepLink.of(reply("rp-1234")))
        assertEquals(DeepLink.Family("c42"), DeepLink.of(reply("fam_c42")))
        assertEquals(DeepLink.Family("c7"), DeepLink.of(BridgeEvent.PartnerChallenge("c7", "Maja", null, "💌", "Kako si?")))
        assertEquals(DeepLink.Module("m1"), DeepLink.of(BridgeEvent.ModulePublished("m1", 1, "Title", null)))
        assertEquals(DeepLink.Packs, DeepLink.of(BridgeEvent.PackPublished("kitchen", null, null)))
        assertNull(DeepLink.of(BridgeEvent.AppUpdate(3, "0.1.3", null)))
        assertEquals(DeepLink.Scene("kuhinja"), DeepLink.of(BridgeEvent.ScenePublished("kuhinja", "V kuhinji", "🍳", null)))
        assertEquals(DeepLink.Village, DeepLink.of(BridgeEvent.ScenePublished("kuhinja", null, null, null, removed = true)))
        assertEquals(
            BridgeEvent.ScenePublished("ob-ognju", "Ob ognju", "🔥", "Nova zgodba"),
            BridgeEvent.parse("""{"type":"scene_published","id":"ob-ognju","title":"Ob ognju","emoji":"🔥","note":"Nova zgodba"}"""),
        )
        // a new story of the storyteller's opens the scene where it is told; one told nowhere yet, the village
        val story = BridgeEvent.parse("""{"type":"story_published","id":"zmaj","title":"Zmaj · The dragon","emoji":"🐉","scene":"ob-ognju"}""")
        assertEquals(BridgeEvent.ScenePublished("ob-ognju", "Zmaj · The dragon", "🐉", null, story = true), story)
        assertEquals(DeepLink.Scene("ob-ognju"), DeepLink.of(story!!))
        assertEquals(DeepLink.Village, DeepLink.of(BridgeEvent.parse("""{"type":"story_published","id":"zmaj","title":"Zmaj"}""")!!))
        assertEquals(BridgeEvent.ScenePublished("", null, null, null, removed = true, story = true), BridgeEvent.parse("""{"type":"story_removed","id":"zmaj"}"""))
        // something new to read, from the tutor: the reading corner
        val reading = BridgeEvent.parse("""{"type":"reading_published","id":"jutro","title":"Jutro · Morning","level":"A1","note":"Micka wrote you something"}""")
        assertEquals(BridgeEvent.ReadingPublished("jutro", "Jutro · Morning", "A1", "Micka wrote you something"), reading)
        assertEquals(DeepLink.Readings, DeepLink.of(reading!!))
        assertEquals(BridgeEvent.ReadingPublished("jutro", null, null, null, removed = true), BridgeEvent.parse("""{"type":"reading_removed","id":"jutro"}"""))
    }

    @Test fun `the tutor's new grammar page opens the book at it, the evening's reminder the review`() {
        assertEquals(DeepLink.Grammar("dvojina"), DeepLink.of(BridgeEvent.GrammarPublished("dvojina", "Dvojina", null)))
    }

    @Test fun `a lani link leads where a notification's would`() {
        assertEquals(DeepLink.Review, DeepLink.fromUri("lani://review"))
        assertEquals(DeepLink.Village, DeepLink.fromUri("lani://village/"))
        assertEquals(DeepLink.Scene("ob-ognju"), DeepLink.fromUri("lani://scene/ob-ognju"))
        assertEquals(DeepLink.Scene("ob-ognju", "ob-ognju/zgodba"), DeepLink.fromUri("lani://scene/ob-ognju/zgodba"))
        assertEquals(DeepLink.School("biti"), DeepLink.fromUri("lani://school/biti"))
        assertEquals(DeepLink.School(), DeepLink.fromUri("lani://school"))
        assertEquals(DeepLink.Grammar("dvojina"), DeepLink.fromUri("lani://grammar/dvojina"))
        assertEquals(DeepLink.Settings("data"), DeepLink.fromUri("lani://settings/data"))
        assertEquals(DeepLink.Friends, DeepLink.fromUri("lani://friends"))
        // back from "Sign in with ChatGPT" in the browser: the tutor's settings
        assertEquals(DeepLink.Settings("tutor"), DeepLink.fromUri("lani://tutor/signed-in"))
        assertEquals(DeepLink.Settings("tutor"), DeepLink.fromUri("lani://tutor"))
    }

    @Test fun `what isn't a lani link to a known place opens nothing`() {
        for (u in listOf(null, "", "https://lani.si/review", "lani://", "lani://nowhere", "lani://module", "lani://question/k1", "fluent://review", "not a uri at all")) {
            assertNull(u.toString(), DeepLink.fromUri(u))
        }
    }
}
