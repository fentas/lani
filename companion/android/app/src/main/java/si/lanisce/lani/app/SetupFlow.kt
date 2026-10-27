package si.lanisce.lani.app

import si.lanisce.lani.data.PairFailure
import si.lanisce.lani.data.PairPayload
import si.lanisce.lani.data.PairTrouble
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.bi

/**
 * The first launch (docs/plans/05-built/onboarding.md), as a state the screens draw (ui/setup/SetupScreen) and the view
 * model keeps: a new language pair builds the screens anew (MainActivity's key), and the step stays. Pure.
 *
 * 1. [Step.WELCOME]: a greeting without a gender, what Lani is, and the language to learn ([chooseTarget]).
 * 2. [Step.LANGUAGES]: the language explanations are in ([chooseBase]); the welcome reads in the pair now.
 * 3. [Step.WAYS]: how Lani runs: [Way.DEVICE] alone (the hosted mode's steps, ui/hosted/HostedSetup, then the tutor), or
 *    with [Way.COMPUTER], the family's computer: its QR code scanned ([Step.COMPUTER]), or its address and token typed
 *    behind "Ročno · Enter manually" ([Step.MANUAL]).
 *
 * What goes wrong is a [PairTrouble]: plain words and the way out ([PairFailure.Out]). [back] goes a step back.
 */
data class SetupFlow(
    val step: Step = Step.WELCOME,
    /** The language learned, once chosen. */
    val target: Lang? = null,
    /** The language explanations are in: the phone's own (when it's one of ours) until another is chosen. */
    val base: Lang = Lang.EN,
    /** How Lani runs here, once chosen. */
    val way: Way? = null,
    /** "Ročno · Enter manually": the computer's address (or the text of a pairing code, pasted) and its token. */
    val url: String = "https://",
    val token: String = "",
    /** Pairing or connecting now. */
    val busy: Boolean = false,
    /** What went wrong, and the way out; null while nothing did. */
    val trouble: PairTrouble? = null,
) {
    enum class Step { WELCOME, LANGUAGES, WAYS, COMPUTER, MANUAL, DEVICE }

    /** "Samo ta telefon / tablica · Just this device" or "S svojim računalnikom · With my family's computer". */
    enum class Way { DEVICE, COMPUTER }

    /** The pair the setup reads in: the one chosen, else the phone's language explaining the default's target. */
    val pair: LangPair get() = target?.let { LangPair(it, base) } ?: LangPair(if (base == Lang.SL) Lang.EN else Lang.SL, base)

    /** The language to learn: the explanations stay in the phone's language unless that's the one learned. */
    fun chooseTarget(t: Lang): SetupFlow =
        copy(target = t, base = if (base == t) otherThan(t) else base, step = Step.LANGUAGES, trouble = null)

    /** The language explanations are in; never the one learned. */
    fun chooseBase(b: Lang): SetupFlow = if (b == target) this else copy(base = b)

    /** On from the languages to the two ways. */
    fun next(): SetupFlow = when (step) {
        Step.WELCOME -> if (target != null) copy(step = Step.LANGUAGES) else this
        Step.LANGUAGES -> copy(step = Step.WAYS)
        else -> this
    }

    fun choose(w: Way): SetupFlow = copy(way = w, step = if (w == Way.DEVICE) Step.DEVICE else Step.COMPUTER, trouble = null, busy = false)

    /** "Ročno · Enter manually": the address and the token. */
    fun manual(): SetupFlow = copy(step = Step.MANUAL, trouble = null, busy = false)

    fun edit(url: String = this.url, token: String = this.token): SetupFlow = copy(url = url, token = token.trim(), trouble = null)

    /** What was typed is a pairing code's text (`lani://pair?…`, as lani-pair prints it under the QR code): it pairs. */
    val pasted: Boolean get() = PAIR_LINK.containsMatchIn(url.trim())

    /** The connect button: a pasted code, or an address and a token. */
    val canConnect: Boolean get() = !busy && (pasted || (url.trim().length > "https://".length && token.isNotBlank()))

    /**
     * Connecting with what was typed: a pasted code pairs, else the address is checked first ([PairPayload.bridgeUrl]:
     * an http(s) URL with a host and nothing after its path); one that isn't stays, with [trouble] saying so.
     */
    fun connecting(): SetupFlow =
        if (!pasted && PairPayload.bridgeUrl(url.trim()) == null) copy(busy = false, trouble = PairTrouble.of(PairFailure.BAD_ADDRESS))
        else copy(busy = true, trouble = null)

    /** Pairing with a scanned code. */
    fun pairing(): SetupFlow = copy(busy = true, trouble = null)

    fun failed(t: PairTrouble): SetupFlow = copy(busy = false, trouble = t)

    /** Connected: the app goes Home (the setup's state is dropped). */
    fun done(): SetupFlow = copy(busy = false, trouble = null)

    /** The way out of [trouble] taken: it's dismissed, the step stays (or goes to the typed address, [PairFailure.Out.MANUAL]). */
    fun out(): SetupFlow = when (trouble?.out) {
        PairFailure.Out.MANUAL -> manual()
        else -> copy(trouble = null, busy = false)
    }

    fun back(): SetupFlow {
        val to = when (step) {
            Step.WELCOME -> Step.WELCOME
            Step.LANGUAGES -> Step.WELCOME
            Step.WAYS -> Step.LANGUAGES
            Step.COMPUTER, Step.DEVICE -> Step.WAYS
            Step.MANUAL -> Step.COMPUTER
        }
        return copy(step = to, trouble = null, busy = false)
    }

    companion object {
        /** The languages a village speaks (companion/cultures), offered to learn: Slovene first, as Lani began. */
        val LANGUAGES = listOf(Lang.SL, Lang.IT, Lang.DE, Lang.EN)

        private val PAIR_LINK = Regex("""^(lani|fluent)://pair\?""", RegexOption.IGNORE_CASE)

        /** The first screen, the explanations in the phone's language [device] (an ISO code; English when not ours). */
        fun start(device: String?): SetupFlow = SetupFlow(base = Lang.of(device) ?: Lang.EN)

        /** The explanations' language when the phone's is the one learned: English, or Slovene for English. */
        fun otherThan(t: Lang): Lang = if (t == Lang.EN) Lang.SL else Lang.EN
    }
}

/**
 * The tutor's step with a key or an account (the hosted mode's onboarding, the settings' tutor and data pages): a
 * child doesn't get asked (docs/plans/05-built/onboarding.md, "A child's device"). It says a parent sets this up, and
 * opens after a hold of [HOLD_MS] ([held]); a grown-up's is open. Pure.
 */
data class ParentGate(val child: Boolean, val opened: Boolean = false) {
    /** What the step shows: the parent's line and the hold (true), or what it holds. */
    val locked: Boolean get() = child && !opened

    /** A press of [ms] milliseconds on the hold: long enough opens it. */
    fun held(ms: Long): ParentGate = if (ms >= HOLD_MS) copy(opened = true) else this

    /** Closed again (the sheet left). */
    fun close(): ParentGate = copy(opened = false)

    companion object {
        /** How long a parent holds: longer than a tap or a child's quick press, short enough not to annoy. */
        const val HOLD_MS = 2_000L
    }
}

/**
 * The tutor's key checked at the setup's tutor step, as [si.lanisce.lani.tutor.live.LiveTutor.setKey] said ([error]:
 * null when it works): a refused key or an empty one in plain words ("this key doesn't work"), anything else (the network)
 * as it came.
 */
object TutorKey {
    fun problem(error: String?): String? = when {
        error == null -> null
        error.contains("401") || error.contains("refused") || error.contains("empty") -> "🔑 ${bi("setup.keyBad")}"
        else -> "⚠️ $error"
    }
}
