package si.lanisce.lani.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * "🗣️ Moj glas · My voice" (companion/SCENES.md, "The learner's voice"; docs/plans/05-built/my-voice.md; Jan, 2026-10-29:
 * "for teo this should be maybe a boy voice -> maybe we offer (in general) like 2,3 voices to choose from?"): what the
 * node says of the learner's voice, the offer at the first line, and the phone's own voice when no clip can be had (its
 * gender, a child's higher).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MyVoiceTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /** The node's answer for Teo: the friuli boys' voices, the first his, not chosen yet. */
    private val teo = """{"language":"it","sample":"Ciao! Sono Teo.","voice":"player-boy-a","off":false,"chosen":false,"choice":null,"age":"child",
        "options":[{"id":"player-boy-a","voice":"player-boy-a","name":"Teddy","gender":"male","age":"child","pick":0,"default":true},
                   {"id":"player-boy-b","voice":"player-boy-b","name":"Leo","gender":"male","age":"child","pick":1,"default":false}]}"""

    @Test fun `the node's voices to choose from, the one in effect, the sample with the learner's name`() {
        val s = MyVoices.parseState(teo)!!
        assertEquals(listOf("Teddy", "Leo"), s.options.map { it.name })
        assertTrue(s.options.all { it.child && it.gender == Clips.MALE })
        assertEquals("player-boy-a", s.current?.id)
        assertTrue(s.options[0].default)
        assertFalse(s.chosen)
        assertEquals("Ciao! Sono Teo.", s.sample)
        // an answer that isn't one (an older node's 404 page): none
        assertNull(MyVoices.parseState("not json"))
        // after a choice: the phone's view of it, as /voice/profiles would say it
        val picked = MyVoices.mineOf(s.copy(voice = "player-boy-b", chosen = true))
        assertEquals(MineVoice("player-boy-b", "player-boy-b", "Leo", Clips.MALE, MyVoices.CHILD, off = false, chosen = true), picked)
        val off = MyVoices.mineOf(s.copy(off = true, chosen = true))
        assertTrue(off.off)
    }

    @Test fun `the offer comes once, at the first line, while nothing is chosen, the lines on and a voice there`() {
        val fresh = MineVoice("male", "male.sl", "Luka", Clips.MALE, MyVoices.ADULT, off = false, chosen = false)
        assertTrue(MyVoices.offerDue(fresh, offered = false))
        assertFalse("once while the app runs", MyVoices.offerDue(fresh, offered = true))
        assertFalse("chosen (a voice, off or the default kept): never again", MyVoices.offerDue(fresh.copy(chosen = true), offered = false))
        assertFalse("off", MyVoices.offerDue(fresh.copy(off = true), offered = false))
        assertFalse("no voice the node may say them in", MyVoices.offerDue(fresh.copy(voice = null), offered = false))
        assertFalse("an older node, which doesn't say", MyVoices.offerDue(null, offered = false))
    }

    @Test fun `the node's profiles say the learner's voice, and the phone plays their lines in it, none when the node has none`() {
        val clips = Clips(ctx, MainScope())
        clips.playerGender = { "male" }
        clips.profilesSaid("""{"people": {}, "player": "player-boy-a", "mine": {"voice": "player-boy-a", "id": "player-boy-a", "name": "Teddy", "gender": "male", "age": "child", "off": false, "chosen": false}}""")
        assertEquals("player-boy-a", clips.playerVoice)
        assertEquals("Teddy", clips.mine?.name)
        assertTrue(clips.mine!!.child)
        // a choice made: the next line in the new voice at once (its clips under it), nothing voiced again in bulk
        clips.mineSaid(clips.mine!!.copy(voice = "player-boy-b", id = "player-boy-b", name = "Leo", chosen = true))
        assertEquals("player-boy-b", clips.playerVoice)
        // the node has no voice that may say them (a cast whose narrator is a villager's voice too): none of the phone's clips
        clips.profilesSaid("""{"people": {}, "player": null, "mine": {"voice": null, "id": null, "name": null, "gender": "male", "age": "child", "off": false, "chosen": true}}""")
        assertEquals(Clips.NONE, clips.playerVoice)
        // an older node (no "player", no "mine"): what it said before stays, the narrator of the gender before anything
        val old = Clips(ctx, MainScope())
        old.playerGender = { "male" }
        old.profilesSaid("""{"people": {}}""")
        assertEquals(Clips.MALE, old.playerVoice)
        assertNull(old.mine)
    }

    private fun voice(name: String, lang: String = "it-IT", features: Set<String> = emptySet(), network: Boolean = false, quality: Int = 300) =
        PlayerTts.VoiceInfo(name, Locale.forLanguageTag(lang), features, network, quality)

    @Test fun `the phone's voice for a line of the learner's, their voice's gender where the phone names one, a child's higher`() {
        assertEquals(Clips.MALE, PlayerTts.genderOf(voice("it-IT-SMTm00")))
        assertEquals(Clips.FEMALE, PlayerTts.genderOf(voice("it-IT-SMTf00")))
        assertEquals(Clips.MALE, PlayerTts.genderOf(voice("en-us-x-sfg#male_1-local", "en-US")))
        assertEquals(Clips.FEMALE, PlayerTts.genderOf(voice("x", features = setOf("gender:female"))))
        assertEquals("not a man's: 'female' holds 'male'", Clips.FEMALE, PlayerTts.genderOf(voice("x", features = setOf("female"))))
        assertNull(PlayerTts.genderOf(voice("it-it-x-kda-local")))
        val tablet = listOf(voice("it-IT-SMTf00"), voice("it-IT-SMTm00"), voice("it-IT-SMTm01", network = true), voice("de-DE-SMTm00", "de-DE"))
        val boy = MineVoice("player-boy-a", "player-boy-a", "Teddy", Clips.MALE, MyVoices.CHILD, off = false, chosen = true)
        // Teo's tablet: a man's Italian voice on the phone itself, at a child's pitch
        assertEquals(PlayerTts.Style("it-IT-SMTm00", PlayerTts.CHILD_PITCH), PlayerTts.style(boy, Locale.ITALY, tablet))
        // a man's line without a man's voice on the phone (Google's unnamed voices): the default, a little lower
        val jan = boy.copy(age = MyVoices.ADULT)
        assertEquals(PlayerTts.Style(null, PlayerTts.MAN_PITCH), PlayerTts.style(jan, Locale.forLanguageTag("sl-SI"), listOf(voice("sl-si-x-abc-local", "sl-SI"))))
        // a woman's: the default as it is; a girl's: higher
        val girl = boy.copy(gender = Clips.FEMALE)
        assertEquals(PlayerTts.Style("it-IT-SMTf00", PlayerTts.CHILD_PITCH), PlayerTts.style(girl, Locale.ITALY, tablet))
        assertEquals(PlayerTts.Style(null, 1f), PlayerTts.style(girl.copy(age = MyVoices.ADULT), Locale.ITALY, emptyList()))
        // nothing known of the voice: as the phone speaks
        assertEquals(PlayerTts.Style(null, 1f), PlayerTts.style(null, Locale.ITALY, tablet))
    }
}
