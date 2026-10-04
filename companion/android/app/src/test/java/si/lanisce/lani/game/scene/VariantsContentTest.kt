package si.lanisce.lani.game.scene

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import java.io.File

/**
 * The happenings' variants in the curated content (companion/SCENES.md, "Variants", "Numbers"): every happening of Jan's
 * scenes and of the culture packs' has more than one dialog, the one of before (what an older app plays, heard by Jan)
 * last, never a counting one; every dialog is some happening's; the files keep at most 12 dialogs for an older bridge;
 * and some dialogs count what the village has, Luka's sheep at the brook among them. The reactions, the numbers and the
 * names are checked where every dialog is (DialogReactionsTest, MentionsContentTest).
 */
class VariantsContentTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "scenes").isDirectory && File(it, "cultures").isDirectory }
    private val files = (File(companion, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().toList() +
        File(companion, "cultures").listFiles().orEmpty().flatMap { File(it, "scenes").listFiles { f -> f.extension == "json" }.orEmpty().toList() })
        .sortedBy { it.path }

    private fun JsonElement?.obj() = this as? JsonObject
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.list(k: String) = (this[k] as? JsonArray).orEmpty().mapNotNull { it.obj() }
    private fun dialogsOf(s: JsonObject) = s.list("dialogs") + s.list("variants")

    @Test fun `every happening has variants - the dialog Jan heard last, never a counting one, and some count`() {
        val few = ArrayList<String>()
        var counting = 0
        for (s in files.map { json.parseToJsonElement(it.readText()).jsonObject }) {
            val ids = dialogsOf(s).mapNotNull { it.str("id") }
            val counts = dialogsOf(s).filter { it["count"] != null }.mapNotNull { it.str("id") }.toSet()
            counting += counts.size
            fun variants(h: JsonObject) = (h["dialogs"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
            for (h in s.list("happenings")) {
                if ((h["stories"] as? JsonPrimitive)?.booleanOrNull == true) continue
                val at = "${s.str("id")}/${h.str("id")}"
                val vs = variants(h)
                if (vs.size < 2) few += "$at: ${vs.size} variants"
                assertTrue("$at: its variants are dialogs of the scene: $vs", vs.all { it in ids } && vs.toSet().size == vs.size)
                // an older app plays `dialog` as it is; Jan has heard it, so it comes last
                assertTrue("$at: its dialog is the one of before, not a counting one", h.str("dialog") != null && h.str("dialog") !in counts)
                assertEquals("$at: the dialog of before comes last", h.str("dialog"), vs.lastOrNull())
            }
            for (id in ids) assertTrue("${s.str("id")}/$id: some happening plays it", s.list("happenings").any { h -> h.str("dialog") == id || id in variants(h) })
            // an older bridge reads at most 12 dialogs: the variants are kept apart
            assertTrue("${s.str("id")}: at most 12 dialogs, the rest are variants", s.list("dialogs").size <= 12 && s.list("variants").size <= 36)
        }
        assertTrue(few.joinToString("\n"), few.isEmpty())
        assertTrue("dialogs that count what the village has ($counting)", counting >= 15)
        // where it began: Luka counts his sheep at the brook
        val brook = files.first { it.name == "ob-potoku.json" }.let { json.parseToJsonElement(it.readText()).jsonObject }
        val ovce = brook.list("happenings").first { it.str("id") == "ovce" }
        assertTrue((ovce["dialogs"] as JsonArray).any { id -> dialogsOf(brook).any { it.str("id") == (id as JsonPrimitive).content && it["count"] != null } })
    }
}
