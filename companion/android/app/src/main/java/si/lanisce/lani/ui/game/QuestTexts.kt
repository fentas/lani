package si.lanisce.lani.ui.game

import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.EventKind
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
 * vrsto · Waits its turn", "💤 Vpraša te kdaj drugič · Asks you another day", "🐺 Po volkovih · After the wolves" (wolves,
 * a bear or a storm about), "✓ Naloga je opravljena: za vajo · The task is done: for practice" (a drill whose task is passed),
 * "☕ Počiva do 14:30 · Resting until 14:30" (just helped), "🌙 Jutri še enkrat · Again tomorrow" (tried below the mark).
 */
fun whyText(why: QuestQueue.Why): String = when (why) {
    is QuestQueue.Why.After -> "⏸️ ${bi("quests.first")}: ${why.drill.title.substringBefore(" · ")}"
    QuestQueue.Why.Queued -> "🕰️ ${bi("quests.queued")}"
    QuestQueue.Why.Stale -> "💤 ${bi("quests.askAgain")}"
    is QuestQueue.Why.Danger -> "${why.kind.emoji} ${dangerText(why.kind)}"
    is QuestQueue.Why.Spent -> "✓ ${bi("quests.taskDone")}"
    is QuestQueue.Why.Resting -> "☕ ${bi("quests.resting", "time" to clockText(why.until))}"
    // when it comes back, plainly ("Riproviamo domani dalle 05:00"): never "gone"
    is QuestQueue.Why.Again -> "🌙 ${bi("quests.againAt", "time" to clockText(why.at))}"
}

/** A time of the day as the phone's clock shows it: "14:30". */
fun clockText(ms: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
    java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

/** "Po volkovih · After the wolves", "Po medvedu · After the bear", "Po nevihti · After the storm": until [kind] is over. */
fun dangerText(kind: EventKind): String = when (kind) {
    EventKind.BEAR -> bi("quests.afterBear")
    EventKind.STORM -> bi("quests.afterStorm")
    else -> bi("quests.afterWolves")
}

/** "📜 Kasneje · Later": where the waiting requests are listed. */
fun laterTitle(): String = "📜 ${bi("quests.later")}"

/** "🎯 Vadi, kar ti ne gre · Practise what's hard": a request tried [QuestQueue.HARD_TRIES] times without a drill for it. */
fun practiseHardLabel(): String = "🎯 ${bi("quests.practiseHard")}"

/**
 * "🎯 Vadi in poskusi znova · Practise and try again": a request tried below the mark, back tomorrow ([QuestQueue.retry]):
 * a short drill of the rules the try missed, passed, brings it back today.
 */
fun practiseRetryLabel(): String = "🎯 ${bi("quests.practiseRetry")}"

/**
 * A request's run as the tutor is told it (a module's session_end, its data.quest; companion/GAME.md "Quests"): the request
 * [q] and its giver, [passed] or not, right of [total] against the pass mark, the results missed (their indices in
 * data.results, from [verdicts]: an almost counts as missed, as for the pass), the grammar pages they named and the tries
 * ([after]: the request as the run left it); below the mark it comes back tomorrow (comes_back), the tutor puts what was
 * missed into the reviews.
 */
internal fun questEnd(q: si.lanisce.lani.game.Quest, after: si.lanisce.lani.game.Quest?, passed: Boolean, total: Int, verdicts: List<Verdict>) =
    kotlinx.serialization.json.buildJsonObject {
        put("request", kotlinx.serialization.json.JsonPrimitive(q.id))
        put("giver", kotlinx.serialization.json.JsonPrimitive(q.giver))
        put("passed", kotlinx.serialization.json.JsonPrimitive(passed))
        put("correct", kotlinx.serialization.json.JsonPrimitive(verdicts.count { it == Verdict.CORRECT }))
        put("total", kotlinx.serialization.json.JsonPrimitive(total))
        put("pass_mark", kotlinx.serialization.json.JsonPrimitive(si.lanisce.lani.game.Quests.passMark(total)))
        put("missed", kotlinx.serialization.json.JsonArray(verdicts.withIndex().filter { it.value != Verdict.CORRECT }.map { kotlinx.serialization.json.JsonPrimitive(it.index) }))
        after?.missed?.takeIf { it.isNotEmpty() }?.let { pages -> put("missed_pages", kotlinx.serialization.json.JsonArray(pages.map { kotlinx.serialization.json.JsonPrimitive(it) })) }
        after?.let { put("tries", kotlinx.serialization.json.JsonPrimitive(it.tries)) }
        if (!passed) put("comes_back", kotlinx.serialization.json.JsonPrimitive("tomorrow"))
    }
