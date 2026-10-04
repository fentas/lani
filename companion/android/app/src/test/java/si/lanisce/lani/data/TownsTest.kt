package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class TownsTest {
    private val fp = "fec16ecd34869e522e575d0fb9ba45de"

    /** An invitation as companion/bridge/src/towns.ts writes it (inviteUri: encodeURIComponent for the URL). */
    private fun link(v: String? = "1", u: String? = "http://127.0.0.1:8792", c: String? = "4AHFMHS7GKXM", f: String? = fp) =
        "lani://town?" + listOfNotNull(
            v?.let { "v=$it" },
            u?.let { "u=" + URLEncoder.encode(it, "UTF-8").replace("+", "%20") },
            c?.let { "c=$it" },
            f?.let { "f=$it" },
        ).joinToString("&")

    private fun ok(s: String) = (InviteLink.parse(s) as InviteParse.Ok).link
    private fun problem(s: String) = (InviteLink.parse(s) as? InviteParse.Invalid)?.problem

    // --- the invitation ------------------------------------------------------------------------------

    @Test fun `the bridge's invitation parses`() {
        assertEquals(InviteLink("http://127.0.0.1:8792", "4AHFMHS7GKXM", fp), ok(link()))
        // exactly what lani-town printed in a run
        val printed = "lani://town?v=1&u=http%3A%2F%2F127.0.0.1%3A8959&c=4AHFMHS7GKXM&f=3c08cada8987b59ce1cf58503336514d"
        assertEquals("http://127.0.0.1:8959", ok(printed).url)
        assertEquals("https://node.tailnet.ts.net:8443", ok(link(u = "https://node.tailnet.ts.net:8443/")).url)
    }

    @Test fun `an invitation written before the rename (fluent) still parses`() {
        assertEquals(ok(link()), ok(link().replace("lani://town", "fluent://town")))
        assertEquals(InviteProblem.INCOMPLETE, problem("fluent://town?v=1&u=%zz"))
        assertEquals(InviteProblem.NOT_INVITE, problem("fluent://pair?v=1&u=x&c=ABCDEFGH&p=luka&f=$fp"))
    }

    @Test fun `codes and fingerprints in one case, whitespace around it ignored`() {
        val l = ok("  " + link(c = "4ahf-mhs7-gkxm", f = fp.uppercase()) + "\n")
        assertEquals("4AHFMHS7GKXM", l.code)
        assertEquals(fp, l.fingerprint)
    }

    @Test fun `what isn't an invitation says why`() {
        assertEquals(InviteProblem.NOT_INVITE, problem("https://example.com"))
        assertEquals(InviteProblem.NOT_INVITE, problem("lani://pair?v=1&u=x&c=ABCDEFGH&p=luka&f=$fp"))
        assertEquals(InviteProblem.NOT_INVITE, problem("hello"))
        assertEquals(InviteProblem.NEWER_VERSION, problem(link(v = "2")))
        assertEquals(InviteProblem.INCOMPLETE, problem(link(v = null)))
        assertEquals(InviteProblem.INCOMPLETE, problem(link(c = null)))
        assertEquals(InviteProblem.INCOMPLETE, problem(link(f = "abc")))
        assertEquals(InviteProblem.BAD_URL, problem(link(u = "ftp://node")))
        assertEquals(InviteProblem.BAD_URL, problem(link(u = "http://user:pw@node")))
        assertEquals(InviteProblem.INCOMPLETE, problem("lani://town?v=1&u=%zz"))
        assertTrue(InviteProblem.NOT_INVITE.message.startsWith("🤔 "))
    }

    // --- GET /towns ------------------------------------------------------------------------------------

    /** GET /towns as the bridge answered in the smoke test. */
    private val listed = """
        {"self":{"id":"65873bc2cb96e3bd4c781ad2485be6a8","name":"Moja vas","learner":"Jan","culture":"primorska","language":"sl","child":false,"can_invite":true},
         "towns":[
          {"id":"$fp","name":"Il mio villaggio","learner":"Mia","culture":"friuli","language":"it","linked_at":"2026-09-26T16:40:00.000Z","by":"app","via":"invite",
           "status":"ok","age":"VAS","villagers":3,"buildings":{"TENT":1,"HUT":1,"FIELD":1},"date":"2026-09-26"},
          {"id":"a2c7e0f1b2c3d4e5f60718293a4b5c6d","name":"Il mio villaggio","learner":"Luka","culture":"friuli","language":"it","linked_at":"2026-09-26T16:41:00.000Z","by":"cli","via":"link",
           "status":"unreachable","newer_field":{"x":1}}
         ]}
    """.trimIndent()

    @Test fun `the linked towns parse, unknown fields ignored`() {
        val l = Towns.parseList(listed)
        assertEquals("Moja vas", l.self.name)
        assertTrue(l.self.canInvite)
        assertFalse(l.self.child)
        assertEquals(2, l.towns.size)
        val mia = l.towns[0]
        assertTrue(mia.reachable)
        assertEquals("VAS", mia.age)
        assertEquals(3, mia.villagers)
        assertEquals(mapOf("TENT" to 1, "HUT" to 1, "FIELD" to 1), mia.buildings)
        assertEquals("invite", mia.via)
        val luka = l.towns[1]
        assertFalse(luka.reachable)
        assertNull(luka.age)
        assertEquals(emptyMap<String, Int>(), luka.buildings)
    }

    @Test fun `a child's list says it can't invite`() {
        val l = Towns.parseList("""{"self":{"id":"x","child":true,"can_invite":false},"towns":[]}""")
        assertTrue(l.self.child)
        assertFalse(l.self.canInvite)
    }

    @Test fun `an invitation from POST towns invite parses`() {
        val i = Towns.parseInvite("""{"code":"4AHFMHS7GKXM","link":"${link()}","url":"http://127.0.0.1:8792","fingerprint":"$fp","expires_at":"2026-09-26T17:00:00.000Z"}""")
        assertEquals("4AHFMHS7GKXM", i.code)
        assertEquals(ok(i.link), InviteLink("http://127.0.0.1:8792", "4AHFMHS7GKXM", fp))
        assertEquals("2026-09-26T17:00:00.000Z", i.expiresAt)
    }

    // --- accepting ----------------------------------------------------------------------------------------

    @Test fun `a linked town is named, a refusal says why`() {
        assertEquals(AcceptOutcome.Linked("Moja vas", "Jan"), Towns.acceptOutcome(200, """{"town":{"id":"x","name":"Moja vas","learner":"Jan","culture":"primorska","language":"sl"}}"""))
        val msg = { status: Int, code: String? -> (Towns.acceptOutcome(status, code?.let { """{"error":"e","code":"$it"}""" } ?: "") as AcceptOutcome.Failed).message }
        assertTrue(msg(403, "invalid_code").startsWith("❌"))
        assertTrue(msg(410, "expired_code").startsWith("⌛"))
        assertTrue(msg(429, "rate_limited").startsWith("⏳"))
        assertTrue(msg(403, "child").startsWith("👨‍👩‍👦"))
        assertTrue(msg(409, "not_that_town").startsWith("🔒"))
        assertTrue(msg(400, "self").startsWith("🏡"))
        assertTrue(msg(502, "unreachable").startsWith("📡"))
        assertTrue(msg(502, "not_a_town").startsWith("📡"))
        assertTrue(msg(400, "bad_invite").startsWith("🧩"))
        assertTrue(msg(404, null).startsWith("🧰")) // an older bridge without towns
        assertTrue(msg(500, null).contains("500"))
        assertTrue((Towns.acceptOutcome(200, "not json") as AcceptOutcome.Failed).message.startsWith("⚠️"))
    }

    @Test fun `flags and codes read well`() {
        assertEquals("🇸🇮", Towns.flag("sl"))
        assertEquals("🇮🇹", Towns.flag("it"))
        assertEquals("PT", Towns.flag("pt"))
        assertEquals("4AHF MHS7 GKXM", Towns.groupCode("4AHFMHS7GKXM"))
        assertEquals("unknown", Towns.errorCode("""{"code":"unknown"}"""))
        assertNull(Towns.errorCode("<html>"))
    }
}
