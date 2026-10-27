package si.lanisce.lani.ui.towns

import si.lanisce.lani.data.HardPattern
import si.lanisce.lani.data.ShareLevels
import si.lanisce.lani.data.Sharing
import si.lanisce.lani.data.TownProgress
import si.lanisce.lani.data.TownProgresses
import si.lanisce.lani.data.Towns
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi

/** A challenge to send from the progress screen: its kind, its text and hint, prefilled from a word or a pattern. */
data class ChallengeDraft(val type: String, val text: String, val hint: String, val key: String)

/** What the friend's card and "📈 Napredek · Progress" show of a linked town's learning (data/TownProgress.kt). Pure. */
object TownProgressLogic {
    /** A level as the learner chooses it: "🏡 Samo vas · Just my village", "📊 Povzetek · Summary", "🔎 Podrobno · Details". */
    fun levelName(level: String): String = when (level) {
        ShareLevels.DETAILS -> "🔎 ${bi("townProgress.levelDetails")}"
        ShareLevels.SUMMARY -> "📊 ${bi("townProgress.levelSummary")}"
        else -> "🏡 ${bi("townProgress.levelVillage")}"
    }

    /** What a level shows the other town, in a line. */
    fun levelAbout(level: String): String = when (level) {
        ShareLevels.DETAILS -> bi("townProgress.aboutDetails")
        ShareLevels.SUMMARY -> bi("townProgress.aboutSummary")
        else -> bi("townProgress.aboutVillage")
    }

    /** "👁️ Kaj vidi Teo · What Teo sees: 📊 Povzetek · Summary" (the link's default marked, but a parent's link: the parent's). */
    fun shareLine(s: Sharing, learner: String): String =
        "👁️ ${bi("townProgress.whatTheySee", "name" to learner.ifBlank { "?" })}: ${levelName(s.share)}" + if (s.shareDefault && !s.guardian) " (${bi("townProgress.default")})" else ""

    /** What the other town shares with this one, when it said: "👀 Teo ti kaže: 🔎 Podrobno · Teo shows you: 🔎 Details". */
    fun theirLine(s: Sharing, learner: String): String? =
        s.theirs?.let { "👀 ${bi("townProgress.whatTheyShow", "name" to learner.ifBlank { "?" })}: ${levelName(it.share)}" }

    /** The child's note on a guardian's link: "👪 Tvoj starš vidi tvoj napredek · Your parent can see your progress". */
    fun guardianNote(s: Sharing): String? = if (s.guardian) "👪 ${bi("townProgress.parentSees")}" else null

    /**
     * Whether the card offers "📈 Napredek · Progress": whenever the bridge knows progress between towns, also while the town
     * shares just its village (the screen says so, and asks again: it may share more since).
     */
    fun offersProgress(s: Sharing?): Boolean = s != null

    /** The choices of what to share: those the link allows (a child's guardian: not below its floor), in order. */
    fun choices(s: Sharing): List<String> = ShareLevels.ALL.filter { it in s.choices }

    /** This week's seven days, Monday first, as dots for TalkBack and the eyes: "●●●○○○○". */
    fun dots(days: List<Boolean>): String = (0 until 7).joinToString("") { if (days.getOrElse(it) { false }) "●" else "○" }

    /** A word's meaning to show: in the viewer's [base], else in the sharer's, else any; with its language's flag when it isn't the viewer's. */
    fun meaning(meanings: Map<String, String>, base: String, theirBase: String?): String? {
        meanings[base]?.let { return it }
        val (lang, m) = (theirBase?.let { b -> meanings[b]?.let { b to it } } ?: meanings.entries.firstOrNull()?.toPair()) ?: return null
        return "$m ${Towns.flag(lang)}"
    }

    /** A card's state: 🌱 still being learned, 🔁 rusty (its last review failed), ✓ known. */
    fun stateMark(state: String): String = when (state) {
        TownProgresses.STATE_RUSTY -> "🔁"
        TownProgresses.STATE_KNOWN -> "✓"
        else -> "🌱"
    }

    /** A pattern's way: ↗ it keeps happening, ↘ answered right since, → neither. */
    fun trendMark(trend: String): String = when (trend) {
        "worse" -> "↗"
        "better" -> "↘"
        else -> "→"
    }

    /** "Ta teden: 4 dnevi vaje, 23 novih besed, 35 min, 18 ponovitev · This week: …": the week of a summary, in a line. */
    fun weekLine(p: TownProgress): String? {
        val w = p.summary?.week ?: return null
        return bi("townProgress.weekSummary", "days" to w.daysPractised, "words" to w.newWords, "minutes" to w.minutes, "reviews" to w.reviews)
    }

    /**
     * The weekly note's text: "Teo ta teden: 4 dni, 23 novih besed, težave: la/il · Teo this week: 4 days, 23 new words,
     * hard: la/il".
     */
    fun weeklyNote(learner: String, days: Int, newWords: Int, hard: List<String>): String =
        bi("townProgress.weekly", "name" to learner.ifBlank { "?" }, "days" to days, "words" to newWords, "hard" to hard.joinToString(", ").ifBlank { "-" }, "any" to if (hard.isEmpty()) "no" else "yes")

    /**
     * A challenge from word [word] (its [meanings]) of town [p]'s learner: to translate what it means in their base into
     * their language; for a child, gently ("Brez skrbi, poskusi! 🌟", in the child's base). [pattern]: the pattern it is
     * hard under, its name the hint.
     */
    fun draftFor(p: TownProgress, word: String, meanings: Map<String, String>, pattern: HardPattern? = null): ChallengeDraft {
        val base = p.base
        val text = base?.let { meanings[it] } ?: meanings.values.firstOrNull() ?: word
        val gentle = if (p.child) gentleHint(base) else ""
        val hint = listOfNotNull(pattern?.name, gentle.ifBlank { null }).joinToString(" · ")
        return ChallengeDraft("translate", text.take(TownProgresses.MAX_TEXT), hint.take(TownProgresses.MAX_HINT), "${p.town.id}:${pattern?.id.orEmpty()}:$word")
    }

    /** A challenge about pattern [pattern] as a whole (a question its learner answers), its name the hint. */
    fun draftForPattern(p: TownProgress, pattern: HardPattern): ChallengeDraft {
        pattern.items.firstOrNull()?.let { return draftFor(p, it.word, it.meanings, pattern) }
        val gentle = if (p.child) gentleHint(p.base) else ""
        return ChallengeDraft("question", "", listOfNotNull(pattern.name, gentle.ifBlank { null }).joinToString(" · ").take(TownProgresses.MAX_HINT), "${p.town.id}:${pattern.id}")
    }

    /** A blank challenge to town [p]'s learner. */
    fun draftBlank(p: TownProgress): ChallengeDraft = ChallengeDraft("question", "", if (p.child) gentleHint(p.base) else "", "${p.town.id}:")

    /** The gentle line a child reads under a challenge, in their base ([base]; the app's own base without it). */
    fun gentleHint(base: String?): String = L10n.text(Lang.of(base) ?: L10n.pair.base, "townProgress.gentleHint")

    /** A challenge's kind as the sender picks it. */
    fun typeName(type: String): String = when (type) {
        "translate" -> "🔁 ${bi("family.translate")}"
        "say" -> "🗣️ ${bi("family.sayOutLoud")}"
        else -> "❓ ${bi("townProgress.question")}"
    }

    /** How a sent challenge went: "✅ 9/10 · Luka knew it", "⏳ čaka na odgovor · waiting for an answer". */
    fun resultLine(answered: Boolean, score: Double?, note: String?): String =
        if (!answered) "⏳ ${bi("townProgress.waiting")}"
        else listOfNotNull("✅ ${bi("townProgress.answered")}", score?.let { "${it.toInt()}/10" }, note?.takeIf { it.isNotBlank() }).joinToString(" · ")
}
