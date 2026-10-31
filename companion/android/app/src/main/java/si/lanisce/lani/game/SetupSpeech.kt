package si.lanisce.lani.game

import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * What each step of the first launch says on its 🔊, and as it opens (docs/plans/05-built/setup-refresh.md; ui/setup,
 * ui/hosted/HostedSetup), as the founding does ([ReadAloud.founding]): the step's title in the language learned (the
 * culture's narrator: the node's clip once paired, else the phone's voice for it), then in the base what the step shows
 * and asks, by the phone's voice of the base. Before a language is chosen, the welcome is in the phone's language alone;
 * a language's tile says its own name in it ("italiano"), so a child who doesn't read yet hears what they chose.
 *
 * Every piece carries its language ([HintPiece.lang]): the pair is being chosen while these play. Emoji aren't said
 * ([ReadAloud.spoken]); a piece the same as the one before (a name) is said once. Pure.
 */
object SetupSpeech {
    /** The welcome before a language is chosen: the greeting, what Lani is and the question, in the phone's [phone]. */
    fun welcome(phone: Lang): List<HintPiece> = pieces(base(phone, LangPair(otherThan(phone), phone), "setup.hello", "setup.what", "setup.learnWhich"))

    /** A language's own name, in it: what its tile or chip says when tapped. */
    fun name(l: Lang): List<HintPiece> = listOf(HintPiece(l.ownName, target = false, lang = l))

    /**
     * The languages' step: the language learned's name, its welcome and what Lani is in it; then in the base the welcome,
     * what Lani is and "Explanations in".
     */
    fun languages(pair: LangPair): List<HintPiece> = pieces(
        listOf(HintPiece(pair.target.ownName, true, pair.target)) + target(pair, "setup.hello", "setup.what") +
            base(pair.base, pair, "setup.hello", "setup.what", "setup.explainIn"),
    )

    /** "How do you want to use Lani?": the question in the language learned; in the base the question and both ways. */
    fun ways(pair: LangPair): List<HintPiece> = pieces(
        target(pair, "setup.howUse") +
            base(pair.base, pair, "setup.howUse", "setup.deviceTitle", "setup.deviceWhat", "setup.computerTitle", "setup.computerWhat"),
    )

    /** The family's computer: its title; in the base, the title and how to scan its code. */
    fun computer(pair: LangPair): List<HintPiece> =
        pieces(target(pair, "setup.computerTitle") + base(pair.base, pair, "setup.computerTitle", "setup.scanHow"))

    /** "Ročno · Enter manually": its title; in the base, the title and where the address and token come from. */
    fun manual(pair: LangPair): List<HintPiece> =
        pieces(target(pair, "setup.manual") + base(pair.base, pair, "setup.manual", "setup.manualHint"))

    /** Just this device with a learner on it already: go on, start afresh or import. */
    fun deviceStart(pair: LangPair): List<HintPiece> =
        pieces(target(pair, "hosted.title") + base(pair.base, pair, "hosted.title", "hosted.what", "hosted.importWhat"))

    /**
     * Just this device's questions: its title; in the base what it is, then each question as the step asks it (the
     * name, who learns here, how the village speaks to them, the level, the aim, the minutes a day).
     */
    fun questions(pair: LangPair): List<HintPiece> = pieces(
        target(pair, "hosted.title") +
            base(pair.base, pair, "hosted.what", "hosted.name", "hosted.gender", "hosted.level", "setup.aim", "hosted.goal"),
    )

    /**
     * The voice and the tutor (just this device, the learner's data made): "My voice" in the language learned; in the
     * base what it is, then the tutor, or for a child that a parent sets it up.
     */
    fun voiceAndTutor(pair: LangPair, child: Boolean): List<HintPiece> = pieces(
        target(pair, "myVoice.title") +
            base(pair.base, pair, "myVoice.title", "myVoice.offerText", "hosted.tutorTitle", if (child) "setup.parentSets" else "hosted.tutorWhat"),
    )

    /**
     * Paired with the computer, "A little about you": its title; in the base the aim and what it changes ([aimWhat], as
     * the step shows it; null: an older node without the aim, not said), then the voice.
     */
    fun about(pair: LangPair, aimWhat: String?): List<HintPiece> = pieces(
        target(pair, "setup.aboutTitle") + base(pair.base, pair, "setup.aboutTitle") +
            (if (aimWhat != null) base(pair.base, pair, "setup.aim") + HintPiece(aimWhat, false, pair.base) else emptyList()) +
            base(pair.base, pair, "myVoice.title", "myVoice.offerText"),
    )

    /** [keys] in the language learned of [pair]. */
    private fun target(pair: LangPair, vararg keys: String): List<HintPiece> =
        keys.map { HintPiece(L10n.text(pair.target, it, target = pair.target), true, pair.target) }

    /** [keys] in [lang], explaining [pair]'s language learned (a text may name it). */
    private fun base(lang: Lang, pair: LangPair, vararg keys: String): List<HintPiece> =
        keys.map { HintPiece(L10n.text(lang, it, target = pair.target), false, lang) }

    /** Said as shown: without emoji; a piece the same as the one before it once; none empty. */
    private fun pieces(all: List<HintPiece>): List<HintPiece> {
        val kept = all.map { it.copy(text = ReadAloud.spoken(it.text)) }.filter { it.text.isNotBlank() }
        return kept.filterIndexed { i, p -> i == 0 || kept[i - 1].text != p.text }
    }

    /** The language the welcome's labels may name before one is chosen (as app/SetupFlow.pair does). */
    private fun otherThan(l: Lang): Lang = if (l == Lang.SL) Lang.EN else Lang.SL
}
