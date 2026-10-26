package si.lanisce.lani.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import si.lanisce.lani.app.VisitVoicePolicy.Kind

/**
 * A visit's first line nothing could say (companion/README.md, "Voices on a visit"): the offline voice of the town's language
 * offered once, else the note that nothing speaks it (with the download while the node has it); never silent without saying
 * so, and nothing when the voice is on the phone or on its way.
 */
class VisitVoicePolicyTest {
    @Test fun `the node has the voice and it was never declined, the offer`() {
        assertEquals(Kind.OFFER, VisitVoicePolicy.prompt(installed = false, offered = true, declined = false, downloading = false))
    }

    @Test fun `declined on an earlier visit, the note with the download`() {
        assertEquals(Kind.NOTICE_DOWNLOAD, VisitVoicePolicy.prompt(installed = false, offered = true, declined = true, downloading = false))
    }

    @Test fun `no node offers it (the hosted mode, an older node), the note alone`() {
        assertEquals(Kind.NOTICE, VisitVoicePolicy.prompt(installed = false, offered = false, declined = false, downloading = false))
        assertEquals(Kind.NOTICE, VisitVoicePolicy.prompt(installed = false, offered = false, declined = true, downloading = false))
    }

    @Test fun `the voice on the phone or on its way, nothing to say`() {
        assertNull(VisitVoicePolicy.prompt(installed = true, offered = true, declined = false, downloading = false))
        assertNull(VisitVoicePolicy.prompt(installed = false, offered = true, declined = false, downloading = true))
    }
}
