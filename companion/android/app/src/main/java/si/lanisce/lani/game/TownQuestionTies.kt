package si.lanisce.lani.game

import si.lanisce.lani.data.TownQuestions
import si.lanisce.lani.data.TownQuestionsList
import si.lanisce.lani.l10n.L10n
import java.time.LocalDate

/**
 * The questions answered between this village and linked towns (data/TownQuestions.kt; plan 2, §3.2), in the ties with
 * each town ([TownTies.questions]): an answer that came to this learner's question, and this learner's answer that reached
 * a guest's town, each once (its key in [GameState.guestbook], "q:<question>"), with a line in the chronicle. The
 * friendship between towns (§3.3, "answered questions") grows by them. Pure, like the engine.
 */
object TownQuestionTies {
    fun keyOf(question: String): String = "q:$question"

    /** A line in the learner's own pair ("target · base"), whatever is on screen (a visit's language). */
    private fun own(key: String, vararg args: Pair<String, Any?>): String = L10n.label(L10n.ownPair, key, args.toMap())

    private fun counted(s: GameState, key: String, town: String, name: String, today: LocalDate, now: Long, line: Pair<String, String>): GameState {
        val t = s.towns[town] ?: TownTies()
        val tie = t.copy(questions = t.questions + 1, name = name.ifBlank { t.name }, last = today.toString())
        return s.copy(towns = s.towns + (town to tie), guestbook = (s.guestbook - key + key).takeLast(Guests.KEEP)).logged(now, line)
    }

    /** [list]'s answered questions not counted yet, counted (oldest first); the village as it was when there are none. */
    fun apply(s: GameState, list: TownQuestionsList, today: LocalDate, now: Long): GameState {
        var st = s
        for (q in list.asked.filter { it.answer != null && it.town.id.isNotBlank() }.sortedBy { it.answer?.at }) {
            val k = keyOf(q.key)
            if (k in st.guestbook) continue
            st = counted(st, k, q.town.id, q.town.name, today, now, "💬" to own("questions.logAnswer", *TownQuestions.whoArgs(q.who), "town" to q.town.name))
        }
        for (q in list.received.filter { it.answer?.sent == true && it.from.id.isNotBlank() }.sortedBy { it.answer?.at }) {
            val k = keyOf(q.key)
            if (k in st.guestbook) continue
            st = counted(st, k, q.from.id, q.from.name, today, now, "❓" to own("questions.logYouAnswered", *TownQuestions.whoArgs(q.who), "town" to q.from.name))
        }
        return st
    }
}
