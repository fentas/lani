package si.lanisce.lani.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import si.lanisce.lani.game.AgeStep
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Teaser
import java.time.LocalDate

/** The HUD variants' texts (ui/game/HudVariants.kt): a two-language line in its halves, the timeline's when and order. */
class HudVariantsTest {
    private val friday = LocalDate.of(2026, 10, 16)

    @Test fun `a two-language line splits at its middle separator`() {
        assertEquals("Jutri: Most čez potok, korak 5/6" to "Tomorrow: a bridge over the stream, step 5 of 6", HudText.halves("Jutri: Most čez potok, korak 5/6 · Tomorrow: a bridge over the stream, step 5 of 6"))
        // a half with a separator of its own: the middle one
        assertEquals("Ogenj · fire" to "Fire · fire", HudText.halves("Ogenj · fire · Fire · fire"))
        assertEquals("Les" to "Wood", HudText.halves("Les · Wood"))
        assertEquals("+60 🪵" to null, HudText.halves("+60 🪵"))
    }

    @Test fun `the timeline says when, so a line loses its own`() {
        assertEquals("Janez pove zgodbo", HudText.withoutWhen("Nocoj ob ognju: Janez pove zgodbo"))
        assertEquals("A bridge over the stream", HudText.withoutWhen("Tomorrow: a bridge over the stream"))
        assertEquals("Krošnjar pride v torek", HudText.withoutWhen("Krošnjar pride v torek"))
    }

    @Test fun `when a teaser comes, in both languages`() {
        val story = Teaser(Teaser.Kind.STORY, "⛰️", "x", day = 0, evening = true)
        val step = Teaser(Teaser.Kind.PROJECT, "🌉", "x", day = 1)
        val pedlar = Teaser(Teaser.Kind.PEDLAR, "🎒", "x", day = 4)
        val feast = Teaser(Teaser.Kind.FESTIVAL, "🍷", "x", day = 9)
        assertEquals("Nocoj" to "Tonight", HudText.whenOf(story, friday))
        assertEquals("Jutri" to "Tomorrow", HudText.whenOf(step, friday))
        assertEquals("Torek" to "Tuesday", HudText.whenOf(pedlar, friday))
        assertEquals("Čez 9 dni" to "In 9 days", HudText.whenOf(feast, friday))
        assertEquals(listOf(story, step, pedlar, feast), HudText.timeline(listOf(feast, pedlar, step, story)))
    }

    @Test fun `each open step has its action, a done one none`() {
        assertEquals("🪵 Nabiraj" to "Gather", HudText.actionOf(AgeStep(AgeStep.Kind.RESOURCE, "🪵", "Les · Wood", 200, 260, res = Res.WOOD)))
        assertEquals("🔩 Očisti" to "Polish", HudText.actionOf(AgeStep(AgeStep.Kind.RUSTY, "🔩", "4 zarjavele besede · 4 rusty words", 236, 240)))
        assertNull(HudText.actionOf(AgeStep(AgeStep.Kind.RESOURCE, "🌾", "Hrana · Food", 600, 300, res = Res.FOOD)))
    }
}
