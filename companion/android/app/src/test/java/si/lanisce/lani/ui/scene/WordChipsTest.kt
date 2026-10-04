package si.lanisce.lani.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.scene.SceneHint
import si.lanisce.lani.game.scene.SceneObject
import si.lanisce.lani.game.scene.SceneSights
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.time.LocalDateTime

/** The words panel's chips (companion/SCENES.md, "The words panel"): the hints in words, and which chips open their word. */
class WordChipsTest {
    private val jan = LangPair(Lang.SL, Lang.EN)

    @Test fun `a hint reads in both languages, its tag first`() {
        assertEquals("🌙 ponoči · at night", WordChips.note(listOf(SceneHint.NIGHT), jan))
        assertEquals("ponoči · at night", WordChips.note(listOf(SceneHint.NIGHT), jan, tags = false)) // what TalkBack reads
        assertEquals("🔄 spet jutri · again tomorrow", WordChips.note(listOf(SceneHint.LATER), jan))
        assertEquals("🌇🌙🌿🎲 ob mraku, ponoči, razen pozimi, včasih · at dusk, at night, except in winter, sometimes",
            WordChips.note(listOf(SceneHint.DUSK, SceneHint.NIGHT, SceneHint.NOT_WINTER, SceneHint.SOMETIMES), jan))
        assertEquals("🌕 ko je luna na nebu · when the moon is up", WordChips.note(listOf(SceneHint.MOON), jan))
        assertEquals("⬆️3 nadgradi na stopnjo 3 · upgrade to level 3", WordChips.note(listOf(SceneHint(SceneHint.Kind.UPGRADE, level = 3)), jan))
        val kozolec = BuildingType.KOZOLEC.names
        assertEquals("🏗️🪜 zgradi: ${kozolec.of(Lang.SL)} · build: ${kozolec.of(Lang.EN)}",
            WordChips.note(listOf(SceneHint(SceneHint.Kind.BUILD, building = BuildingType.KOZOLEC)), jan))
        assertEquals("🏗️⚒️ zgradi: ${BuildingType.SMITHY.names.of(Lang.SL)}, stopnja 2 · build: ${BuildingType.SMITHY.names.of(Lang.EN)}, level 2",
            WordChips.note(listOf(SceneHint(SceneHint.Kind.BUILD, building = BuildingType.SMITHY, level = 2)), jan))
        assertEquals("", WordChips.note(emptyList(), jan))
        // every kind has its words in every table
        for (k in SceneHint.Kind.entries) for (lang in Lang.entries) {
            val text = WordChips.text(SceneHint(k, building = BuildingType.WELL, level = 2, project = "mlin", age = si.lanisce.lani.game.Age.VAS), lang, Lang.SL)
            assertTrue("$k in ${lang.code}: $text", text.isNotBlank() && !text.startsWith("wordGroups."))
        }
    }

    @Test fun `the legend names each hint once`() {
        val legend = WordChips.legend(listOf(SceneHint.NIGHT, SceneHint.MOON, SceneHint.NIGHT), jan)
        assertEquals(listOf("🌙 ponoči · at night", "🌕 ko je luna na nebu · when the moon is up"), legend)
    }

    @Test fun `a found night-only word opens by day with when it shows, a hidden one doesn't`() {
        val campfire = SceneSpec(
            id = "ob-ognju", title = "Ob ognju", art = "campfire", from = listOf("fire"),
            objects = listOf(SceneObject("fire", "ogenj", sl = "ogenj"), SceneObject("stars", "zvezde", sl = "zvezde"), SceneObject("moon", "luna", sl = "luna")),
        )
        val noon = SceneSights.frame(LocalDateTime.of(2026, 6, 20, 13, 0))
        val g = SceneSights.group(campfire, noon, found = setOf("stars"))
        val (fire, stars, moon) = campfire.objects
        assertTrue(WordChips.opens(fire, g)) // here now: a tap finds it, as a tap on the picture does
        assertTrue(WordChips.opens(stars, g)) // seen before: it opens though it isn't in the picture
        assertFalse(WordChips.opens(moon, g)) // still hidden: its word stays unrevealed
        assertEquals("🌙 ponoči · at night", WordChips.noteOf("stars", g, jan))
        assertNull(WordChips.noteOf("fire", g, jan))
        assertNull(WordChips.noteOf("moon", g, jan))
    }
}
