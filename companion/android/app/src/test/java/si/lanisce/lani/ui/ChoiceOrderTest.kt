package si.lanisce.lani.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.data.ChoiceOrder
import si.lanisce.lani.data.Courses
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading
import si.lanisce.lani.data.Grading.Verdict
import java.io.File
import java.time.LocalDate

/**
 * A choice's options are shown in the day's order ([ChoiceOrder]), and [Exercise.Choice.answer] and almost keep meaning the
 * file's indices: the option tapped is graded by what it is, wherever it stands (docs/plans/05-built/choices-order.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChoiceOrderTest {
    @get:Rule val compose = createComposeRule()

    private val today get() = LocalDate.now().toEpochDay()

    /** The file's options: the right one first, as the writers have it, a near miss second. */
    private val options = listOf("imam", "imaš", "ima", "imajo")

    /** A prompt whose order today doesn't begin with the right answer (the file's first), so a tap by place would be wrong. */
    private fun promptRightNotFirst(): String =
        (1..100).map { "Jaz ___ brata. $it" }.first { ChoiceOrder.of(options.size, ChoiceOrder.question(it, options[0]), today)[0] != 0 }

    private fun topOf(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    private fun rowsTopDown(texts: List<String>) = texts.sortedBy(::topOf)

    @Test fun `a choice shows its options in the day's order, the file's first not on top`() {
        val prompt = promptRightNotFirst()
        compose.setContent { Column { ChoiceEx(prompt, options, 0, null, locked = false, done = {}) } }
        val shown = ChoiceOrder.of(options.size, ChoiceOrder.question(prompt, options[0]), today).map { options[it] }
        assertEquals(shown, rowsTopDown(options))
        assertNotEquals("imam", shown[0])
    }

    @Test fun `the right answer tapped in a reordered list is right`() {
        val prompt = promptRightNotFirst()
        var got: Outcome? = null
        compose.setContent { Column { ChoiceEx(prompt, options, 0, "why", locked = false, done = { got = it }, almost = listOf(1)) } }
        compose.onNodeWithText("imam").performClick()
        compose.waitForIdle()
        assertEquals(Verdict.CORRECT, got?.verdict)
        assertEquals("imam", got?.answer)
        assertEquals("imam", got?.correct)
        assertEquals("why", got?.explain)
    }

    @Test fun `a near miss tapped in a reordered list stays almost`() {
        val prompt = promptRightNotFirst()
        var got: Outcome? = null
        compose.setContent { Column { ChoiceEx(prompt, options, 0, null, locked = false, done = { got = it }, almost = listOf(1)) } }
        compose.onNodeWithText("imaš").performClick()
        compose.waitForIdle()
        assertEquals(Verdict.ALMOST, got?.verdict)
        assertEquals("imaš", got?.answer)
        assertEquals("imam", got?.correct)
    }

    @Test fun `another option tapped in a reordered list is wrong, whatever now stands at the answer's place in the file`() {
        val prompt = promptRightNotFirst()
        val top = ChoiceOrder.of(options.size, ChoiceOrder.question(prompt, options[0]), today)[0]
        var got: Outcome? = null
        compose.setContent { Column { ChoiceEx(prompt, options, 0, null, locked = false, done = { got = it }, almost = listOf(1)) } }
        // the top row: right if the display's place were taken for the file's index
        compose.onNodeWithText(options[top]).performClick()
        compose.waitForIdle()
        assertEquals(if (top == 1) Verdict.ALMOST else Verdict.WRONG, got?.verdict)
        assertEquals(options[top], got?.answer)
    }

    @Test fun `a right answer that isn't the file's first is right too`() {
        val prompt = (1..100).map { "Babica ___ v kuhinji. $it" }.first { ChoiceOrder.of(3, ChoiceOrder.question(it, "je"), today)[0] != 1 }
        var got: Outcome? = null
        compose.setContent { Column { ChoiceEx(prompt, listOf("sem", "je", "so"), 1, null, locked = false, done = { got = it }) } }
        compose.onNodeWithText("je").performClick()
        compose.waitForIdle()
        assertEquals(Verdict.CORRECT, got?.verdict)
        assertEquals("je", got?.correct)
    }

    @Test fun `a multi is graded by what is ticked, wherever it stands, and told in the file's order`() {
        val ex = Exercise.Multi("iti", listOf("gremo", "imamo", "greste", "gredo", "imajo", "grem"), listOf(0, 2, 3, 5))
        var got: Outcome? = null
        compose.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { MultiEx(ex, locked = false, done = { got = it }) } }
        val shown = ChoiceOrder.of(ex.options.size, ChoiceOrder.question(ex.prompt, ex.options.joinToString("\n")), today).map { ex.options[it] }
        assertEquals(shown, rowsTopDown(ex.options))
        for (w in shown.filter { it.startsWith("gr") }) compose.onNodeWithText(w).performScrollTo().performClick()
        compose.onNodeWithText(multiCheckLabel(4)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(Verdict.CORRECT, got?.verdict)
        assertEquals("gremo, greste, gredo, grem", got?.answer)
        assertEquals("gremo, greste, gredo, grem", got?.correct)
    }

    @Test fun `a multi with one right one left out and one wrong one ticked is not right`() {
        val ex = Exercise.Multi("iti", listOf("gremo", "imamo", "greste"), listOf(0, 2))
        var got: Outcome? = null
        compose.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { MultiEx(ex, locked = false, done = { got = it }) } }
        compose.onNodeWithText("gremo").performScrollTo().performClick()
        compose.onNodeWithText("imamo").performScrollTo().performClick()
        compose.onNodeWithText(multiCheckLabel(2)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(Grading.multi(listOf(0, 1), listOf(0, 2)), got?.verdict)
        assertNotEquals(Verdict.CORRECT, got?.verdict)
        assertEquals("gremo, imamo", got?.answer)
    }

    @Test fun `true or false keeps its order`() {
        val prompt = (1..100).map { "Marko pride v nedeljo. $it" }.first { ChoiceOrder.of(2, ChoiceOrder.question(it, "Ne drži"), today)[0] != 0 }
        compose.setContent { Column { ChoiceEx(prompt, listOf("Drži", "Ne drži"), 1, null, locked = false, done = {}, fixedOrder = true) } }
        assertEquals(listOf("Drži", "Ne drži"), rowsTopDown(listOf("Drži", "Ne drži")))
    }

    @Test fun `the order holds when the state is restored, as after a process's death`() {
        val prompt = promptRightNotFirst()
        val restoring = StateRestorationTester(compose)
        restoring.setContent { Column { ChoiceEx(prompt, options, 0, null, locked = false, done = {}) } }
        val before = rowsTopDown(options)
        restoring.emulateSavedInstanceStateRestore()
        assertEquals(before, rowsTopDown(options))
    }

    @Test fun `no file can ask for the fixed order, it isn't in the format`() {
        val e = Json { ignoreUnknownKeys = true }.parseToJsonElement("""{"type":"choice","prompt":"p","options":["a","b"],"answer":0,"fixedOrder":true}""").jsonObject
        val ex = Courses.exercise(e) as Exercise.Choice
        assertFalse(ex.fixedOrder)
    }

    @Test fun `the same order all day, another another day, for the learner's own trap too`() {
        val q = ChoiceOrder.question("Jaz ___ brata.", "imam")
        assertEquals(ChoiceOrder.of(4, q, 20_000), ChoiceOrder.of(4, q, 20_000))
        assertTrue((20_000L until 20_014L).map { ChoiceOrder.of(4, q, it) }.distinct().size > 3)
        assertEquals(q, ChoiceOrder.question("Jaz ___ brata.", "imam"))
    }

    /** The modules' exercises, as the app parses them: Slovene's. */
    private fun modules(): List<Exercise> {
        val dir = listOf(File("../../modules"), File("../modules"), File("companion/modules")).first { it.isDirectory }
        val json = Json { ignoreUnknownKeys = true }
        return dir.listFiles { f -> f.name.endsWith(".json") }!!.flatMap { f ->
            (json.parseToJsonElement(f.readText()).jsonObject["exercises"] as? JsonArray).orEmpty().map { Courses.exercise(it as JsonObject) }
        }
    }

    /** Every choice and multi of the course of [language], and of the modules for Slovene: its question, a 1 for each right option in the file. */
    private fun written(language: String): List<Pair<String, List<Int>>> {
        val course = checkNotNull(Courses.bundled(language)) { language }
        val exercises = course.units.values.flatMap { u -> u.worksheets.flatMap { it.exercises } + u.test.exercises + u.intro?.steps.orEmpty().mapNotNull { it.ask } } +
            if (language == "sl") modules() else emptyList()
        return exercises.mapNotNull { e ->
            when (e) {
                is Exercise.Choice -> ChoiceOrder.question(e.prompt, e.options.getOrNull(e.answer)) to List(e.options.size) { i -> if (i == e.answer) 1 else 0 }
                is Exercise.Multi -> ChoiceOrder.question(e.prompt, e.options.joinToString("\n")) to List(e.options.size) { i -> if (i in e.answers) 1 else 0 }
                else -> null
            }
        }
    }

    @Test fun `over the whole of each course a right answer is first about as often as any other place`() {
        for (language in listOf("sl", "it", "de", "en")) {
            val all = written(language).filter { it.second.size == 3 && it.second.sum() == 1 }
            assertTrue("$language: its three-option choices (${all.size})", all.size >= 100)
            for (day in listOf(today, 20_000L, 20_001L, 20_365L)) {
                val at = IntArray(3)
                for ((q, right) in all) at[ChoiceOrder.of(3, q, day).indexOfFirst { right[it] == 1 }]++
                val shares = at.map { it.toDouble() / all.size }
                assertTrue("$language on day $day: right answers by place from the top $shares", shares.all { it in 0.22..0.45 })
            }
        }
    }

    @Test fun `over a fortnight no question keeps its right answer in one place for life`() {
        for (language in listOf("sl", "it", "de", "en")) {
            val all = written(language).filter { it.second.sum() == 1 }
            val stuck = all.filter { (q, right) ->
                (20_000L until 20_014L).map { d -> ChoiceOrder.of(right.size, q, d).indexOfFirst { right[it] == 1 } }.distinct().size == 1
            }
            assertTrue("$language: stuck in one place for 14 days: ${stuck.size} of ${all.size}", stuck.size <= all.size / 100)
        }
    }

    @Test fun `every course's order is a permutation of the file's indices`() {
        for (language in listOf("sl", "it", "de", "en")) for ((q, right) in written(language)) {
            assertEquals(right.indices.toList(), ChoiceOrder.of(right.size, q, today).sorted())
        }
    }
}
