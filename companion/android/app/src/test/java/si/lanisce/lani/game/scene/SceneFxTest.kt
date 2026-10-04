package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.GameState
import si.lanisce.lani.ui.scene.DialogRun
import java.time.LocalDate

class SceneFxTest {
    private val today = LocalDate.of(2026, 9, 26)

    /** Luka brings a lantern (v-sotoru "svetilka"), shortened: dark in the tent, lit with his reply, and it stays lit. */
    private val lamp = Dialog(
        id = "svetilka",
        fxStays = true,
        lines = listOf(
            DialogLine(who = "luka", sl = "V šotoru je tema.", en = "It is dark.", fx = mapOf("lantern" to 0f)),
            DialogLine(choices = listOf(
                DialogChoice("Imaš ti?", ok = true, reply = DialogReply("Imam.", fx = mapOf("lantern" to 1f))),
                DialogChoice("Ne imam.", why = "nimam"),
            )),
            DialogLine(who = "luka", sl = "Lahko noč!", en = "Good night!"),
        ),
    )

    @Test fun `effects are written as levels or true and false`() {
        val raw = """{"id":"d","fx_stays":true,"lines":[{"who":"a","sl":"A","en":"A","fx":{"oven":true,"steam":0.4}},
            {"choices":[{"sl":"B","ok":true,"reply":{"sl":"C","fx":{"oven":false,"steam":7}}}]}]}"""
        val d = json.decodeFromString(Dialog.serializer(), raw)
        assertEquals(mapOf("oven" to 1f, "steam" to 0.4f), d.lines[0].fx)
        assertEquals(mapOf("oven" to 0f, "steam" to 1f), d.lines[1].choices[0].reply?.fx)
        assertEquals(emptyMap<String, Float>(), d.lines[1].fx)
        assertTrue(d.hasFx && d.fxKept)
        assertFalse(d.copy(fxStays = null).fxKept) // by default the effects go with the dialog
    }

    @Test fun `the dialog's effects follow its lines and the replies to the picks`() {
        var run = DialogRun.start(lamp, "luka")
        assertEquals(mapOf("lantern" to 0f), run.fx(emptyMap()))
        run = run.choose(0)
        assertEquals(mapOf("lantern" to 1f), run.fx(emptyMap()))
        // over what the day has: what the dialog leaves alone stays
        assertEquals(mapOf("oven" to 1f, "lantern" to 1f), run.fx(mapOf("oven" to 1f)))
    }

    @Test fun `effects ease in, and out when no longer set`() {
        var e = FxEase.NONE.toward(mapOf("oven" to 1f), 10.0)
        assertEquals(0f, e.at(10.0).getValue("oven"), 1e-6f)
        assertEquals(0.5f, e.at(12.0).getValue("oven"), 1e-6f)
        assertEquals(1f, e.at(14.0).getValue("oven"), 1e-6f)
        // set to 0: stays, at 0 (a lantern put out)
        e = e.toward(mapOf("oven" to 0f), 14.0)
        assertEquals(0f, e.at(20.0).getValue("oven"), 1e-6f)
        // no longer set: fades out, then the painter shows the place as by itself
        e = FxEase.still(mapOf("steam" to 1f)).toward(emptyMap(), 30.0)
        assertEquals(0.5f, e.at(32.0).getValue("steam"), 1e-6f)
        assertFalse("steam" in e.at(34.0))
        assertTrue(e.toward(emptyMap(), 40.0).fades.isEmpty())
        assertEquals(emptyMap<String, Float>(), FxEase.NONE.at(0.0))
    }

    @Test fun `effects a dialog keeps stay in its scene for the day`() {
        val s = DayFx.keep(GameState(), "v-sotoru", lamp, mapOf("lantern" to 1f), today)
        assertEquals(mapOf("lantern" to 1f), DayFx.of(s, today, "v-sotoru"))
        assertEquals(emptyMap<String, Float>(), DayFx.of(s, today, "kuhinja"))
        assertEquals(emptyMap<String, Float>(), DayFx.of(s, today.plusDays(1), "v-sotoru"))
        // another scene's are kept beside it; yesterday's are gone
        val both = DayFx.keep(s, "kuhinja", lamp, mapOf("oven" to 1f), today)
        assertEquals(setOf("v-sotoru", "kuhinja"), both.sceneFx?.scenes?.keys)
        assertEquals(setOf("kuhinja"), DayFx.keep(s, "kuhinja", lamp, mapOf("oven" to 1f), today.plusDays(1)).sceneFx?.scenes?.keys)
        // a dialog that lets them go keeps nothing
        assertSame(s, DayFx.keep(s, "v-sotoru", lamp.copy(fxStays = null), mapOf("lantern" to 0f), today))
        assertEquals(both, json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), both)))
    }
}
