package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the family's computer answers (companion/bridge/src/features/family-members.ts), as the app reads it. */
class FamilyMembersApiTest {
    @Test fun `the family, with each device's budget`() {
        val f = FamilyMembersApi.parseFamily(
            """{"members":[{"profile":"default","name":"Jan","child":false,"self":true,"up":true,"devices":[]},
               {"profile":"zan","name":"Žan","child":true,"self":false,"up":false,"devices":[{"id":"d_12345678","name":"Tablica","created_at":"x","via":"qr","last_seen":"2026-11-05T10:00:00.000Z","daily_cap_usd":0.5},{"id":"d_00000000","name":"Telefon","created_at":"x","via":"qr"}]}],"can_add":false,"why_not":"full"}""",
        )!!
        assertFalse(f.canAdd)
        assertEquals("full", f.whyNot)
        val zan = f.members[1]
        assertTrue(zan.child)
        assertFalse(zan.up)
        assertEquals(0.5, zan.devices[0].cap)
        assertEquals("2026-11-05T10:00:00.000Z", zan.devices[0].lastSeen)
        assertNull(zan.devices[1].cap)
    }

    @Test fun `a member's code, and what's left on the computer`() {
        val c = FamilyMembersApi.parseCode("""{"profile":"zan","name":"Žan","child":true,"up":false,"pair":{"uri":"lani://pair?v=1","url":"https://x","expires_at":"t"},"left":[{"what":"publish","command":"tailscale serve"}],"town_linked":true}""")!!
        assertEquals("lani://pair?v=1", c.pair)
        assertEquals(listOf(FamilyMembersApi.Left("publish", "tailscale serve")), c.left)
        assertTrue(c.townLinked)
        assertFalse(c.up)
    }

    @Test fun `no address known means no code, only what's left`() {
        val c = FamilyMembersApi.parseCode("""{"profile":"zan","up":true,"pair":null,"left":[{"what":"url","command":"lani-pair"}]}""")!!
        assertNull(c.pair)
        assertEquals("url", c.left.single().what)
    }

    @Test fun `what a parent set, or nothing`() {
        assertEquals(FamilyMembersApi.Rule(0.5, "Jan"), FamilyMembersApi.parseRule("""{"daily_cap_usd":0.5,"by":"Jan"}"""))
        assertEquals(FamilyMembersApi.Rule(null, null), FamilyMembersApi.parseRule("""{"daily_cap_usd":null,"by":null}"""))
    }

    @Test fun `anything else is nothing`() {
        assertNull(FamilyMembersApi.parseFamily("not json"))
        assertNull(FamilyMembersApi.parseCode("{}"))
    }
}
