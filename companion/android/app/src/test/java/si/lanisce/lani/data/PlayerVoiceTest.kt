package si.lanisce.lani.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The learner's voice (companion/SCENES.md, "The learner's voice"; Jan, 2026-10-25: "does the player have a voice, meaning
 * male or female?"): their own lines in a dialog are asked of the node as the voice "player", which says them in its cast's
 * `player` for their gender; on the phone their clips are that voice's, the narrator of their gender until the node says,
 * the female one when the profile says no gender.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerVoiceTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun `the voice by the profile's gender, the female one when it says none`() {
        assertEquals(Clips.MALE, Clips.playerOf("male"))
        assertEquals(Clips.MALE, Clips.playerOf(" Male "))
        assertEquals(Clips.MALE, Clips.playerOf("m"))
        assertEquals(Clips.FEMALE, Clips.playerOf("female"))
        assertEquals(Clips.FEMALE, Clips.playerOf("F"))
        // not said: the female voice (as the node counts a name it doesn't know), though the content speaks to them as to a man
        assertEquals(Clips.FEMALE, Clips.playerOf(null))
        assertEquals(Clips.FEMALE, Clips.playerOf(""))
        assertEquals(Clips.FEMALE, Clips.playerOf("{GENDER}"))
    }

    @Test fun `the node says the voice, and the phone looks the learner's clips up by it`() {
        assertEquals("male", Clips.parsePlayer("""{"people": {}, "player": "male"}"""))
        assertEquals("player-male", Clips.parsePlayer("""{"people": {}, "player": "player-male"}"""))
        assertNull(Clips.parsePlayer("""{"people": {}}"""))
        assertNull(Clips.parsePlayer("""{"people": {}, "player": "player"}"""))
        // the index knows the clips by the voice they are in: the learner's own as theirs ("male" for Jan), the rest as asked
        assertEquals(listOf("male"), Clips.knownAs(listOf(Clips.PLAYER), "male"))
        assertEquals(listOf("grandma", "female"), Clips.knownAs(listOf("grandma", "female"), "male"))
        assertEquals(listOf("female"), Clips.knownAs(listOf(Clips.PLAYER, "female"), "female"))
    }

    @Test fun `on the phone, the profile's gender until the node says, then the node's`() {
        val clips = Clips(ctx, MainScope())
        assertEquals("nobody said: the female voice", Clips.FEMALE, clips.playerVoice)
        clips.playerGender = { "male" }
        assertEquals(Clips.MALE, clips.playerVoice)
        clips.profilesSaid("""{"people": {"micka": {"speaker": "grandma", "archetype": "grandma"}}, "player": "player-male"}""")
        assertEquals("a voice of the learner's own on the node: one line in its cast", "player-male", clips.playerVoice)
        assertEquals("grandma", clips.profile("micka")?.speaker)
        // an older bridge's profiles say no player: what the node said before stays
        clips.profilesSaid("""{"people": {}}""")
        assertEquals("player-male", clips.playerVoice)
    }

    @Test fun `the learner's lines are heard in their voice alone, no narrator of the other gender stands in`() {
        val v = Speaker.voices(Clips.PLAYER)
        assertEquals(listOf(Clips.PLAYER), v.voices)
        assertEquals(listOf(Clips.PLAYER), v.all)
        assertFalse(v.own)
        // anyone else's line keeps its chain, the female narrator last
        assertEquals(listOf("grandma", "female"), Speaker.voices("grandma", "female").voices)
    }
}
