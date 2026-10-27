package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * Invitations without a PC (docs/plans/05-built/invitations.md): the web link (https://lani.page/town/#…) read as the
 * lani:// one, the link that opens the app, who can accept an invitation, a child's links that wait for a parent as
 * GET /towns and GET /towns/children answer them, what accepting one came to, and their events.
 */
class TownInvitationsTest {
    private val fp = "fec16ecd34869e522e575d0fb9ba45de"
    private val app = "lani://town?v=1&u=http%3A%2F%2F127.0.0.1%3A8792&c=4AHFMHS7GKXM&f=$fp"
    private val relay = "https://relay.lani.team/v1/towns/$fp"

    private fun ok(s: String) = (InviteLink.parse(s) as InviteParse.Ok).link
    private fun problem(s: String) = (InviteLink.parse(s) as? InviteParse.Invalid)?.problem

    private fun <T> inSlEn(block: () -> T): T {
        val before = L10n.pair
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        try {
            return block()
        } finally {
            L10n.pair = before
        }
    }

    // --- the web link --------------------------------------------------------------------------------------

    @Test fun `the web link reads as the app's link, the invitation in its fragment`() {
        val web = "https://lani.page/town/#v=1&u=http%3A%2F%2F127.0.0.1%3A8792&c=4AHFMHS7GKXM&f=$fp"
        assertEquals(ok(app), ok(web))
        // as the bridge writes it (inviteWebUri), and on another host, in its query, without the slash
        assertEquals(ok(app), ok("https://lani.team/town?v=1&u=http%3A%2F%2F127.0.0.1%3A8792&c=4AHFMHS7GKXM&f=$fp"))
        assertEquals(ok(app), ok("https://lani.page/town#v=1&u=http%3A%2F%2F127.0.0.1%3A8792&c=4AHFMHS7GKXM&f=$fp"))
    }

    @Test fun `a web page that isn't an invitation, or a broken one, says why`() {
        assertEquals(InviteProblem.NOT_INVITE, problem("https://lani.page/"))
        assertEquals(InviteProblem.NOT_INVITE, problem("https://lani.page/#v=1&u=x&c=4AHFMHS7GKXM&f=$fp"))
        assertEquals(InviteProblem.NOT_INVITE, problem("http://lani.page/town/#v=1&u=http%3A%2F%2Fx&c=4AHFMHS7GKXM&f=$fp"))
        assertEquals(InviteProblem.INCOMPLETE, problem("https://lani.page/town/#v=1&c=4AHFMHS7GKXM"))
        assertEquals(InviteProblem.INCOMPLETE, problem("https://lani.page/town/#v=1&u=%zz"))
        assertEquals(InviteProblem.NEWER_VERSION, problem("https://lani.page/town/#v=2&u=http%3A%2F%2Fx&c=4AHFMHS7GKXM&f=$fp"))
    }

    @Test fun `a relay address is kept when it is the invited town's own, and the text the bridge gets carries it`() {
        val l = ok("$app&r=${java.net.URLEncoder.encode(relay, "UTF-8")}")
        assertEquals(relay, l.relay)
        assertNull(ok("$app&r=${java.net.URLEncoder.encode("https://relay.lani.team/v1/towns/${"0".repeat(32)}", "UTF-8")}").relay)
        // what the app sends its bridge: lani://town?…, which any bridge reads, the same invitation
        assertEquals(l, ok(l.text()))
        assertTrue(l.text().startsWith("lani://town?v=1&u=http%3A%2F%2F127.0.0.1%3A8792&c=4AHFMHS7GKXM&f=$fp&r="))
        assertEquals(app, ok(app).text())
    }

    @Test fun `a link that opens the app is an invitation of any form, or nothing`() {
        assertTrue(InviteLink.looksLike(app))
        assertTrue(InviteLink.looksLike(app.replace("lani://", "fluent://")))
        assertTrue(InviteLink.looksLike("https://lani.page/town/#v=1&u=x"))
        assertFalse(InviteLink.looksLike("lani://tutor/signed-in"))
        assertFalse(InviteLink.looksLike("https://lani.page/"))
        assertEquals(DeepLink.Invitation("https://lani.page/town/#v=1&c=X"), DeepLink.ofUri("https://lani.page/town/#v=1&c=X"))
        assertNull(DeepLink.ofUri("lani://tutor/signed-in"))
        assertNull(DeepLink.ofUri(null))
        // it waits in the intent's extra like any link, to be opened once connected
        for (l in listOf(DeepLink.Invitation(app), DeepLink.Invitation("https://lani.page/town/#v=1&u=http%3A%2F%2Fx&c=4AHFMHS7GKXM&f=$fp"), DeepLink.Friends)) {
            assertEquals(l, DeepLink.parse(DeepLink.encode(l)))
        }
        assertNull(DeepLink.parse("invite:hello"))
    }

    // --- who can accept it ----------------------------------------------------------------------------------

    @Test fun `who can accept an invitation, from its address (an older bridge says nothing)`() {
        assertEquals(Towns.REACH_NODE, Towns.reachOf("http://127.0.0.1:8790"))
        assertEquals(Towns.REACH_NODE, Towns.reachOf("http://localhost:8792"))
        assertEquals(Towns.REACH_TAILNET, Towns.reachOf("https://node.tail1234.ts.net:8443"))
        assertEquals(Towns.REACH_TAILNET, Towns.reachOf("http://100.101.2.3:8790"))
        assertEquals(Towns.REACH_NETWORK, Towns.reachOf("https://lani.home.example"))
        assertEquals(Towns.REACH_RELAY, Towns.reachOf("http://127.0.0.1:8790", relay))
        assertEquals(Towns.REACH_RELAY, Towns.reachOf(relay))
        val old = Towns.parseInvite("""{"code":"4AHFMHS7GKXM","link":"$app","url":"http://127.0.0.1:8792","fingerprint":"$fp","expires_at":"x"}""")
        assertEquals(Towns.REACH_NODE, old.reachNow)
        assertEquals(app, old.shareLink) // no web link from an older bridge: the app's link
        val now = Towns.parseInvite("""{"code":"4AHFMHS7GKXM","link":"$app","web_link":"https://lani.page/town/#v=1","url":"http://127.0.0.1:8792","reach":"relay","relay":"$relay","parent_approves":true}""")
        assertEquals(Towns.REACH_RELAY, now.reachNow)
        assertEquals("https://lani.page/town/#v=1", now.shareLink)
        assertTrue(now.parentApproves)
    }

    // --- a child's links that wait for a parent ------------------------------------------------------------------

    @Test fun `a child's GET towns - its parents, its links that wait, a town that waits for its own parent`() {
        val l = Towns.parseList(
            """{"self":{"id":"c","name":"Il villaggio","learner":"Teo","child":true,"can_invite":false,"parent_approves":true,
                "guardians":[{"id":"g","name":"Moja vas","learner":"Jan"}],"reach":"node"},
              "towns":[{"id":"$fp","name":"Ana's","learner":"Ana","status":"pending"}],
              "requests":[{"id":"lr_0123456789abcdef","town":{"id":"$fp","name":"Il mio villaggio","learner":"Mia","culture":"friuli","language":"it"},
                "via":"accept","at":"2026-10-27T10:00:00Z","expires_at":"2026-11-10T10:00:00Z","status":"pending"},
               {"id":"lr_fedcba9876543210","town":{"id":"$fp","name":"Drugi"},"via":"invite","status":"declined","decided_at":"2026-10-27T11:00:00Z","by":"family"}]}""",
        )
        assertTrue(l.self.parentApproves)
        assertEquals("Jan", l.self.guardians.single().learner)
        assertEquals(Towns.REACH_NODE, l.self.reach)
        assertTrue(l.towns.single().pending)
        assertFalse(l.towns.single().reachable)
        assertEquals(listOf(true, false), l.requests.map { it.open })
        assertEquals("accept", l.requests[0].via)
        assertEquals("family", l.requests[1].by)
    }

    @Test fun `a parent's GET towns children - who, what waits with the other town's village, and a child to link`() {
        val c = Towns.parseChildren(
            """{"children":[
                {"id":"$fp","name":"Il villaggio di Teo","learner":"Teo","language":"it","profile":"teo","linked":true,"guardian":true,"can_link":true,"status":"ok",
                 "requests":[{"id":"lr_0123456789abcdef","town":{"id":"a","name":"Il mio villaggio","learner":"Mia","language":"it"},"via":"invite","status":"pending",
                   "preview":{"age":"VAS","villagers":3,"buildings":{"HUT":1}}}]},
                {"id":"","name":"Luka's","learner":"Luka","profile":"luka","linked":false,"guardian":false,"can_link":false,"status":"unreachable"}],
              "can_link_local":true}""",
        )
        assertTrue(c.canLinkLocal)
        val teo = c.children[0]
        assertEquals("VAS", teo.requests.single().preview?.age)
        assertEquals(mapOf("HUT" to 1), teo.requests.single().preview?.buildings)
        assertEquals("unreachable", c.children[1].status)
        // an older bridge: nothing new
        assertEquals(TownChildren(), Towns.parseChildren("""{"children":[]}"""))
    }

    @Test fun `accepting an invitation says whose parent says yes first`() {
        assertEquals(AcceptOutcome.Linked("Il mio villaggio", "Mia", Towns.WAIT_PARENT), Towns.acceptOutcome(200, """{"town":{"name":"Il mio villaggio","learner":"Mia"},"pending":"parent","request":"lr_0123456789abcdef"}"""))
        assertEquals(AcceptOutcome.Linked("Il villaggio", "Teo", Towns.WAIT_THEIR_PARENT), Towns.acceptOutcome(200, """{"town":{"name":"Il villaggio","learner":"Teo"},"pending":"their_parent"}"""))
        assertEquals(AcceptOutcome.Linked("Moja vas", "Jan"), Towns.acceptOutcome(200, """{"town":{"name":"Moja vas","learner":"Jan"},"pending":"something else"}"""))
    }

    @Test fun `the refusals of a child's links say what to do`() = inSlEn {
        val msg = { status: Int, code: String -> Towns.problem(status, code) }
        assertTrue(msg(429, "too_many_requests").startsWith("⏳"))
        assertTrue(msg(403, "not_owner").startsWith("🖥️"))
        assertTrue(msg(409, "decided").startsWith("✔"))
        assertTrue(msg(410, "expired").startsWith("⌛"))
        assertTrue(msg(404, "unknown_request").startsWith("⌛"))
        assertTrue(msg(403, "not_guardian").startsWith("👪"))
        assertTrue(msg(403, "family_only").startsWith("👪"))
        assertTrue(msg(409, "mismatch").startsWith("🔒"))
        assertTrue(msg(404, "not_local").startsWith("🔎"))
        assertEquals("✔ O tem je že odločeno · That was decided already", msg(409, "decided"))
    }

    @Test fun `a link's events - the parent hears of a request, the child how it went, banners and notices`() = inSlEn {
        val req = BridgeEvent.parse("""{"type":"town_link","kind":"request","request":"lr_0123456789abcdef","child":{"id":"c","name":"Il villaggio","learner":"Teo"},"town":{"name":"Il mio villaggio","learner":"Mia"}}""") as BridgeEvent.TownLink
        assertEquals(BridgeEvent.TownLink("request", "Il mio villaggio", "Mia", "Teo", "lr_0123456789abcdef"), req)
        assertEquals("👪 Teo si želi prijateljstva: Il mio villaggio · Teo would like to be friends: Il mio villaggio", Towns.linkBanner(req))
        assertEquals("👪 Teo: nov prijatelj? · Teo: a new friend?", Towns.linkNotice(req)?.first)
        val linked = BridgeEvent.parse("""{"type":"town_link","kind":"linked","town":{"id":"$fp","name":"Il mio villaggio","learner":"Mia"}}""") as BridgeEvent.TownLink
        assertEquals("🤝 Zdaj prijatelji: Il mio villaggio · Friends now: Il mio villaggio", Towns.linkBanner(linked))
        val no = BridgeEvent.TownLink("declined", "Drugi", "Ana")
        assertEquals("👋 Ne tokrat: Drugi · Not this time: Drugi", Towns.linkBanner(no))
        // the child's own request waiting: the list shows it, no banner or notice
        val pending = BridgeEvent.TownLink("pending", "Il mio villaggio", "Mia")
        assertNull(Towns.linkBanner(pending))
        assertNull(Towns.linkNotice(pending))
        assertEquals(DeepLink.Friends, DeepLink.of(req))
        assertNull(BridgeEvent.parse("""{"type":"town_link"}"""))
    }
}
