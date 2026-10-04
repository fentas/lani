package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FamilyTest {
    @Test fun `genitive of first names`() {
        assertEquals("Maje", Family.genitive("Maja"))
        assertEquals("Micke", Family.genitive("Micka"))
        assertEquals("Marka", Family.genitive("Marko"))
        assertEquals("Jana", Family.genitive("Jan"))
        assertEquals("Mojci", Family.genitive("Mojci"))
    }

    @Test fun `from label prefers the family's own spelling`() {
        assertEquals("Od Maje · From Maja", Family.fromLabel("Maja"))
        assertEquals("Od Petra · From Peter", Family.fromLabel("Peter", "Petra"))
        assertEquals("Od družine · From the family", Family.fromLabel(""))
    }

    @Test fun `challenges parse with answer and feedback`() {
        val raw = """[{"id":"k1","from":"Maja","from_sl":"Maje","emoji":"💃","type":"say","text":"Dober tek!","created_at":"x",
            "answer":{"text":"Dober tek","spoken":true,"at":"y"},
            "feedback":{"text":"**Bravo**","partner_note":"Nice","score":8.5,"at":"z"}},
            {"id":"k2","from":"Maja","type":"question","text":"Kaj?","created_at":"x"}]"""
        val (a, b) = Family.parseChallenges(raw)
        assertEquals("💃 Od Maje · From Maja", a.title)
        assertEquals(true, a.answer?.spoken)
        assertEquals(8.5, a.feedback?.score)
        assertNull(b.answer)
        assertEquals("💌 Od Maje · From Maja", b.title)
    }

    @Test fun `partner challenge event parses`() {
        val ev = BridgeEvent.parse("""{"type":"partner_challenge","id":"k1","from":"Maja","from_sl":"Maje","emoji":"💃","kind":"say","text":"Dober tek!"}""")
        assertEquals(BridgeEvent.PartnerChallenge("k1", "Maja", "Maje", "💃", "Dober tek!"), ev)
    }
}
