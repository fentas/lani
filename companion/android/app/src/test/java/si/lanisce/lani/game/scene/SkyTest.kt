package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.GameState
import si.lanisce.lani.ui.scene.DialogRun
import java.time.LocalDate

class SkyTest {
    private val today = LocalDate.of(2026, 9, 26)

    /** Luka and the hay (na-njivi "seno"), shortened: the rain begins with his reply and pours at the end. */
    private val hay = Dialog(
        id = "seno",
        lines = listOf(
            DialogLine(who = "luka", sl = "Dež gre!", en = "Rain is coming!", sky = SkyCue(gloom = 0.4f, wind = 0.3f)),
            DialogLine(choices = listOf(
                DialogChoice("Prav!", ok = true, reply = DialogReply("Hitro, prve kaplje!", sky = SkyCue(rain = 0.2f))),
                DialogChoice("Kje?", why = "no"),
            )),
            DialogLine(who = "luka", sl = "Uspelo nama je!", en = "We made it!"),
            DialogLine(choices = listOf(
                DialogChoice("Dobra ekipa sva!", ok = true, reply = DialogReply("Dežuje.", sky = SkyCue(rain = 0.8f, gloom = 0.8f))),
                DialogChoice("Dobra ekipa smo!", why = "dual"),
            )),
        ),
    )

    @Test fun `a cue sets what it names and keeps the rest, within 0 to 1`() {
        val s = Sky(rain = 0.5f, fog = 0.3f) + SkyCue(rain = 1.4f, gloom = -1f, lightning = true)
        assertEquals(Sky(rain = 1f, fog = 0.3f, gloom = 0f, lightning = true), s)
        assertSame(s, s + null)
        assertTrue(Sky.CLEAR.clear)
        assertFalse(Sky(wind = 0.1f).clear)
    }

    @Test fun `the dialog's sky follows the lines played and the replies to the picks`() {
        var run = DialogRun.start(hay, "luka")
        // the first line is said and the turn is shown: his line's cue
        assertEquals(Sky(gloom = 0.4f, wind = 0.3f), run.sky(Sky.CLEAR))
        // a wrong pick changes nothing
        run = run.choose(1)
        assertEquals(Sky(gloom = 0.4f, wind = 0.3f), run.sky(Sky.CLEAR))
        // the right one: his reply brings the first drops
        run = run.choose(0)
        assertEquals(Sky(rain = 0.2f, gloom = 0.4f, wind = 0.3f), run.sky(Sky.CLEAR))
        run = run.next()
        run = run.choose(0)
        assertEquals(Sky(rain = 0.8f, gloom = 0.8f, wind = 0.3f), run.sky(Sky.CLEAR))
        assertEquals(listOf(0, 0), run.picks)
    }

    @Test fun `a line not said yet brings nothing, a turn brings its cue when it comes up`() {
        val d = Dialog("x", listOf(
            DialogLine(who = "a", sl = "A", en = "A"),
            DialogLine(choices = listOf(DialogChoice("B", ok = true)), sky = SkyCue(fog = 0.5f)),
            DialogLine(who = "a", sl = "C", en = "C", sky = SkyCue(snow = 1f)),
        ))
        assertEquals(Sky.CLEAR, d.skyAt(0, emptyList()))
        assertEquals(Sky(fog = 0.5f), d.skyAt(1, emptyList()))
        assertEquals(Sky(fog = 0.5f), d.skyAt(2, listOf(0)))
        assertEquals(Sky(fog = 0.5f, snow = 1f), d.skyAt(3, listOf(0)))
        // over the day's sky: what a cue leaves out stays as the day has it
        assertEquals(Sky(rain = 0.6f, fog = 0.5f), d.skyAt(1, emptyList(), Sky(rain = 0.6f)))
    }

    @Test fun `the scene format carries sky cues and sky_stays`() {
        val raw = """{"id":"d","sky_stays":false,"lines":[{"who":"a","sl":"Dež!","en":"Rain!","sky":{"rain":0.5,"lightning":true}},
            {"choices":[{"sl":"Ja","ok":true,"reply":{"sl":"Sonce!","en":"Sun!","sky":{"rain":0,"gloom":0}}}]}]}"""
        val d = json.decodeFromString(Dialog.serializer(), raw)
        assertEquals(SkyCue(rain = 0.5f, lightning = true), d.lines[0].sky)
        assertEquals(SkyCue(rain = 0f, gloom = 0f), d.lines[1].choices[0].reply?.sky)
        assertEquals(false, d.skyStays)
        assertTrue(d.hasSky)
        assertFalse(d.skyKept)
        assertTrue(hay.skyKept)
        assertFalse(Dialog("plain", hay.lines.map { it.copy(sky = null, choices = it.choices.map { c -> c.copy(reply = c.reply?.copy(sky = null)) }) }).hasSky)
    }

    @Test fun `each level eases in over a few seconds, on its own`() {
        var e = SkyEase.CLEAR.toward(Sky(rain = 1f), 10.0)
        assertEquals(0f, e.at(10.0).rain, 1e-6f)
        assertTrue(e.at(11.0).rain in 0.05f..0.3f) // slowly at first
        assertEquals(0.5f, e.at(12.0).rain, 1e-6f)
        assertEquals(1f, e.at(14.0).rain, 1e-6f)
        // a new cue for the fog midway leaves the rain's fade going
        e = e.toward(Sky(rain = 1f, fog = 1f), 12.0)
        assertEquals(0.5f, e.at(12.0).rain, 1e-6f)
        assertEquals(1f, e.at(14.0).rain, 1e-6f)
        assertEquals(0.5f, e.at(14.0).fog, 1e-6f)
        // turning back midway starts from where it is
        e = e.toward(Sky(rain = 0f, fog = 1f), 13.0)
        assertEquals(e.rain.from, e.at(13.0).rain, 1e-6f)
        assertEquals(0f, e.at(17.0).rain, 1e-6f)
        assertEquals(Sky(fog = 1f), e.target)
        assertEquals(Sky(snow = 0.4f), SkyEase.still(Sky(snow = 0.4f)).at(0.0))
    }

    @Test fun `the day's sky shows over its scene and the village until midnight`() {
        val s = GameState(sky = DaySky("2026-09-26", "na-njivi", Sky(rain = 0.8f)))
        assertEquals(Sky(rain = 0.8f), DaySky.of(s, today, "na-njivi"))
        assertEquals(Sky(rain = 0.8f), DaySky.of(s, today))
        assertEquals(Sky.CLEAR, DaySky.of(s, today, "kuhinja"))
        assertEquals(Sky.CLEAR, DaySky.of(s, today.plusDays(1), "na-njivi"))
        assertEquals(Sky.CLEAR, DaySky.of(s, ""))
        assertEquals(Sky.CLEAR, DaySky.of(null, today))
    }

    @Test fun `a finished dialog keeps its sky for the day, unless it says not to`() {
        val s = GameState()
        val kept = DaySky.keep(s, "na-njivi", hay, Sky(rain = 0.8f), today)
        assertEquals(DaySky("2026-09-26", "na-njivi", Sky(rain = 0.8f)), kept.sky)
        // a dialog without cues, or one that lets its sky go, leaves the day as it is
        assertSame(kept, DaySky.keep(kept, "kuhinja", Dialog("plain", hay.lines.take(1).map { it.copy(sky = null) }), Sky.CLEAR, today))
        assertSame(kept, DaySky.keep(kept, "na-njivi", hay.copy(skyStays = false), Sky(fog = 1f), today))
        // one that ends clear (the sun came out) clears it
        assertNull(DaySky.keep(kept, "v-sotoru", hay, Sky.CLEAR, today).sky)
        // and it survives saving the village
        assertEquals(kept, json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), kept)))
    }
}
