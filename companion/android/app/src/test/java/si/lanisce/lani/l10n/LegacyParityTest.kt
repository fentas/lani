package si.lanisce.lani.l10n

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.ui.game.Reward
import si.lanisce.lani.ui.game.helpText
import si.lanisce.lani.ui.game.stepsLeft
import si.lanisce.lani.ui.game.tallyText
import si.lanisce.lani.ui.game.tiredText

/**
 * Text built by functions that were converted by hand: each, for sl · en, against its old implementation (kept here
 * verbatim, with the hand-written plural helper it used), over sample inputs. The parity fixture checks the keys; this
 * checks the code that puts them together.
 */
class LegacyParityTest {
    private fun slCount(n: Int, one: String, two: String, few: String, many: String): String = when (n % 100) {
        1 -> one
        2 -> two
        3, 4 -> few
        else -> many
    }

    private val counts = listOf(0, 1, 2, 3, 4, 5, 11, 21, 101, 102, 103, 111, 1000)

    // --- ui/game/VillageLogic.kt ----------------------------------------------------------------------------

    private fun oldHelpText(n: Int): String? =
        n.takeIf { it > 0 }?.let { "+$it 🤝 pomoč · help: sosedje ti bodo pomagali graditi · the neighbours will help you build" }

    private fun oldTiredText(r: Reward): String? = r.tired.takeIf { it > 0 }?.let { n ->
        "🌙 $n ${slCount(n, "odgovor", "odgovora", "odgovori", "odgovorov")} za pol nagrade · $n at half pay: " +
            "po ${Catalog.FRESH_ANSWERS} odgovorih na dan vas počiva · after ${Catalog.FRESH_ANSWERS} answers a day the village rests. " +
            "Jutri spet cela nagrada · Full pay again tomorrow."
    }

    private fun oldTallyText(r: Reward): String =
        if (r.answered >= r.total && r.almost == 0) "${r.correct} / ${r.total} pravilno · correct"
        else buildString {
            append("✅ ${r.correct}")
            if (r.almost > 0) append(" · 🟡 ${r.almost}")
            append(" · ${r.answered}/${r.total} odgovorjenih · answered")
        }

    private fun oldStepsLeft(n: Int): String = "še $n ${slCount(n, "korak", "koraka", "koraki", "korakov")} · $n ${if (n == 1) "step" else "steps"} to go"

    // --- game/Engine.kt --------------------------------------------------------------------------------------

    private fun oldFriendsText(n: Int): String {
        val sl = when (n % 100) {
            1 -> "prijatelj"
            2 -> "prijatelja"
            3, 4 -> "prijatelji"
            else -> "prijateljev"
        }
        return "$n $sl · $n ${if (n == 1) "friend" else "friends"}"
    }

    @Test
    fun `the friends an age needs read as before`() {
        for (n in counts) assertEquals(oldFriendsText(n), si.lanisce.lani.game.GameEngine.friendsText(n))
    }

    @Test
    fun `village results read as before`() {
        for (n in counts) {
            assertEquals(oldHelpText(n), helpText(n))
            assertEquals(oldStepsLeft(n), stepsLeft(n))
            val tired = Reward(emptyMap(), tired = n)
            assertEquals(oldTiredText(tired), tiredText(tired))
        }
        for ((correct, total, answered, almost) in listOf(listOf(5, 7, 7, 0), listOf(2, 7, 3, 1), listOf(0, 4, 2, 0), listOf(3, 3, 3, 2))) {
            val r = Reward(emptyMap(), correct = correct, total = total, answered = answered, almost = almost)
            assertEquals(oldTallyText(r), tallyText(r))
        }
    }
}
