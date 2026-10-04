package si.lanisce.lani.ui.game

import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.QuestQueue
import si.lanisce.lani.l10n.bi

// What a request's row says of it (the villager's card, the Scroll, the intro): how close Jan is, and why it waits.

/**
 * "🎯 Najboljše 7/12 · potrebuješ 8 · Best 7/12, you need 8  🔁 3 poskusi · 3 tries": the best try and its pass mark, and
 * how often it was played; null before the first try.
 */
fun progressText(q: Quest): String? {
    if (q.tries <= 0 || q.bestOf <= 0) return null
    val best = bi("quests.best", "best" to q.best, "of" to q.bestOf, "need" to QuestQueue.passMark(q))
    return "🎯 $best  🔁 ${bi("quests.tries", "n" to q.tries)}"
}

/**
 * Why a request waits in "📜 Kasneje · Later": "⏸️ Najprej · First: Kam? Na tržnico!" (behind its drill), "🕰️ Čaka na
 * vrsto · Waits its turn", "💤 Vpraša te kdaj drugič · Asks you another day".
 */
fun whyText(why: QuestQueue.Why): String = when (why) {
    is QuestQueue.Why.After -> "⏸️ ${bi("quests.first")}: ${why.drill.title.substringBefore(" · ")}"
    QuestQueue.Why.Queued -> "🕰️ ${bi("quests.queued")}"
    QuestQueue.Why.Stale -> "💤 ${bi("quests.askAgain")}"
}

/** "📜 Kasneje · Later": where the waiting requests are listed. */
fun laterTitle(): String = "📜 ${bi("quests.later")}"

/** "🎯 Vadi, kar ti ne gre · Practise what's hard": a request tried [QuestQueue.HARD_TRIES] times without a drill for it. */
fun practiseHardLabel(): String = "🎯 ${bi("quests.practiseHard")}"
