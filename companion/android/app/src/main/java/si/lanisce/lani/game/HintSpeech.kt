package si.lanisce.lani.game

import si.lanisce.lani.l10n.inBase

/**
 * A piece of the hint said out loud: [text] in the learner's base language (the phone's own voice says it), or, with
 * [target], in the language learned (the app's voice: a clip of the voice store, else the phone's voice for it).
 */
data class HintPiece(val text: String, val target: Boolean)

/**
 * What the 🔊 of "📖 Namig · Hint" says (companion/SCENES.md, "The hint"): the turn's rule as a short TL;DR, built from
 * the hint's own data, never the answer. Sentence by sentence, in the learner's base with the Slovene in it marked «…»:
 * what applies here ("After «dvanajst», five and up:"), the rule in one sentence ("the noun goes into the genitive
 * plural."), the question to ask ("Ask: «Koga? Česa?»"), a model with other words ("Like: «pet hiš», «pet stolov»");
 * and for the rules not introduced yet, that they come later. [of] cuts them into the pieces each voice says. Pure.
 */
object HintSpeech {
    /** The card's rules in words ([TurnHint.rule]) as the 🔊 says them: one short sentence each, the Slovene marked «…». */
    private val RULES: Map<String, String> = mapOf(
        "hint.ruleAdverb" to "hintSay.ruleAdverb",
        "hint.ruleAgree" to "hintSay.ruleAgree",
        "hint.ruleAspect" to "hintSay.ruleAspect",
        "hint.ruleBiti" to "hintSay.ruleBiti",
        "hint.ruleCases" to "hintSay.ruleCases",
        "hint.ruleClitics" to "hintSay.ruleClitics",
        "hint.ruleComparative" to "hintSay.ruleComparative",
        "hint.ruleDual" to "hintSay.ruleDual",
        "hint.ruleFuture" to "hintSay.ruleFuture",
        "hint.ruleGender" to "hintSay.ruleGender",
        "hint.ruleImperative" to "hintSay.ruleImperative",
        "hint.ruleJoining" to "hintSay.ruleJoining",
        "hint.ruleKi" to "hintSay.ruleKi",
        "hint.ruleModal" to "hintSay.ruleModal",
        "hint.ruleNoTwice" to "hintSay.ruleNoTwice",
        "hint.ruleNumberAgrees" to "hintSay.ruleNumberAgrees",
        "hint.ruleNumbers" to "hintSay.ruleNumbers",
        "hint.ruleOrdinal" to "hintSay.ruleOrdinal",
        "hint.rulePast" to "hintSay.rulePast",
        "hint.rulePerson" to "hintSay.rulePerson",
        "hint.rulePlural" to "hintSay.rulePlural",
        "hint.rulePronouns" to "hintSay.rulePronouns",
        "hint.ruleQuestions" to "hintSay.ruleQuestions",
        "hint.ruleReflexive" to "hintSay.ruleReflexive",
        "hint.ruleReported" to "hintSay.ruleReported",
        "hint.ruleSupine" to "hintSay.ruleSupine",
        "hint.ruleSvoj" to "hintSay.ruleSvoj",
        "hint.ruleTiVi" to "hintSay.ruleTiVi",
        "hint.ruleWhen" to "hintSay.ruleWhen",
        "hint.ruleWhere" to "hintSay.ruleWhere",
        "hint.ruleWhereTo" to "hintSay.ruleWhereTo",
        "hint.ruleWould" to "hintSay.ruleWould",
    )

    /** What a case's question means ([CaseLine.gloss]), as said: "where to", without the card's German. */
    private val GLOSSES: Map<String, String> = mapOf(
        "hint.qHowMany" to "hintSay.qHowMany",
        "hint.qWhen" to "hintSay.qWhen",
        "hint.qWhere" to "hintSay.qWhere",
        "hint.qWhereFrom" to "hintSay.qWhereFrom",
        "hint.qWhereTo" to "hintSay.qWhereTo",
    )

    /** The spoken sentence of the card's rule [key] ("hint.ruleTiVi"), a string table's key; null for a rule it lacks. */
    fun spoken(key: String): String? = RULES[key]

    /** How many phrases of the models it says at most: a TL;DR, not the table. */
    private const val MODEL_PHRASES = 3

    /**
     * What the 🔊 says for hint [h] (null: a turn of rules not introduced yet alone) and the rules not yet ([later]: each
     * page's title in the language learned and in the learner's base), in the base with the Slovene marked «…», sentence by
     * sentence. Each sentence of the hint is [TurnHints.fair] and its Slovene shows none of the turn's forms (unless it sets
     * them all side by side): never the answer, never a wrong form of the turn either. Empty: nothing to say.
     */
    fun lines(h: TurnHint?, later: List<Pair<String, String>> = emptyList()): List<String> {
        val out = mutableListOf<String>()
        if (h != null) {
            val said = buildList {
                h.trigger?.let { add(opener(it)) }
                addAll(rule(h))
                question(h)?.let(::add)
                models(h.models)?.let { add(inBase("hintSay.like", "models" to it)) }
            }
            out += said.filter { quiet(it, h) }
        }
        if (later.isNotEmpty()) out += inBase("hintSay.later", "titles" to later.distinct().joinToString(", ") { (t, b) -> title(t, b) })
        // a model that is a sentence ends the line itself: «Grem na pošto.», not «Grem na pošto.».
        return out.map { it.replace(STOPPED, "$1»") }
    }

    private val STOPPED = Regex("""([.!?…])»\.""")

    /** What the 🔊 says ([lines]) in the pieces each voice says, in order. Empty: no 🔊. */
    fun of(h: TurnHint?, later: List<Pair<String, String>> = emptyList()): List<HintPiece> = pieces(lines(h, later).joinToString(" "))

    /**
     * [text] cut at its «…»: the marked parts in the language learned, the rest in the base (a part of punctuation alone
     * is left out, so «pet hiš», «pet stolov» are two pieces back to back).
     */
    fun pieces(text: String): List<HintPiece> {
        val out = mutableListOf<HintPiece>()
        fun base(s: String) {
            val t = s.trim().trimStart(',', ';', ':', '.', ')', ' ').trim()
            if (t.none { it.isLetterOrDigit() }) return
            val last = out.lastOrNull()
            if (last != null && !last.target) out[out.lastIndex] = HintPiece("${last.text} $t", false) else out += HintPiece(t, false)
        }
        var at = 0
        for (m in MARKED.findAll(text)) {
            base(text.substring(at, m.range.first))
            m.groupValues[1].trim().takeIf { t -> t.any { it.isLetterOrDigit() } }?.let { out += HintPiece(it, true) }
            at = m.range.last + 1
        }
        base(text.substring(at))
        return out
    }

    private val MARKED = Regex("«([^»]*)»")

    /** [line] without its marks: what it says, as the card would show it. */
    fun plain(line: String): String = line.replace("«", "").replace("»", "")

    /** Roughly how long [pieces] take to say: the phone's voice about 14 characters a second, the app's Slovene about 12. */
    fun seconds(pieces: List<HintPiece>): Double = pieces.sumOf { p -> if (p.target) 0.5 + p.text.length / 12.0 else 0.4 + p.text.length / 14.0 }

    /** [line] gives nothing of [h] away: it is [TurnHints.fair], and its Slovene shows no form of the turn, or all of them. */
    private fun quiet(line: String, h: TurnHint): Boolean {
        if (!TurnHints.fair(plain(line), h)) return false
        val sl = MARKED.findAll(line).joinToString(" ") { it.groupValues[1] }
        val shown = h.forms.filter { TurnHints.shows(sl, it) }
        return shown.isEmpty() || (h.forms.size > 1 && shown.size == h.forms.size)
    }

    /** What applies here: the trigger in this sentence ("After «dvanajst», five and up:", "With «pomagam»:"). */
    private fun opener(t: Trigger): String {
        val w = t.word.lowercase()
        return when (t) {
            is Trigger.Preposition, is Trigger.Amount -> inBase("hintSay.after", "word" to w)
            is Trigger.Count -> inBase("hintSay.afterNumber", "word" to w, "cat" to TurnHints.category(t.category))
            is Trigger.Negation -> inBase("hintSay.afterNo", "word" to w)
            is Trigger.Partner -> if (t.lemma == "pomagati") inBase("hintSay.withHelfen", "word" to w) else inBase("hintSay.with", "word" to w)
            is Trigger.Noun -> inBase("hintSay.withNoun", "word" to w)
            is Trigger.Cue -> when (t.kind) {
                Trigger.Cue.Kind.WHO -> inBase("hintSay.who", "word" to w)
                Trigger.Cue.Kind.PAST -> inBase("hintSay.whenPast", "word" to w)
                Trigger.Cue.Kind.FUTURE -> inBase("hintSay.whenFuture", "word" to w)
            }
        }
    }

    /**
     * The rule in one sentence: the page's rule in words where the card has one, else a number's ("the noun goes into the
     * genitive plural"), else the case the missing word takes, or the two a preposition takes by what the sentence means.
     */
    private fun rule(h: TurnHint): List<String> {
        h.rule?.let(RULES::get)?.let { return listOf(inBase(it)) }
        val t = h.trigger
        if (t is Trigger.Count) return listOf(inBase("hintSay.number", "cat" to TurnHints.category(t.category)))
        val c = h.cases
        return when (c.size) {
            0 -> emptyList()
            1 -> listOf(inBase("hintSay.case", "case" to inBase(c[0].case.key)))
            else -> listOf(inBase("hintSay.twoCases", "q1" to meaning(c[0]), "case1" to inBase(c[0].case.key), "q2" to meaning(c[1]), "case2" to inBase(c[1].case.key)))
        }
    }

    /** The question to ask: the case's ("Ask: «Kam?», where to."), both of a preposition's, or the page's own. */
    private fun question(h: TurnHint): String? {
        val c = h.cases
        return when {
            c.size >= 2 -> inBase("hintSay.askEither", "q1" to c[0].question, "q2" to c[1].question)
            c.size == 1 -> c[0].gloss?.let(GLOSSES::get)?.let { inBase("hintSay.askMeaning", "question" to c[0].question, "meaning" to inBase(it)) }
                ?: inBase("hintSay.ask", "question" to c[0].question)
            h.question != null -> inBase("hintSay.ask", "question" to h.question)
            else -> null
        }
    }

    /** What a case's question means, as said ("where to"); the Slovene question itself where the card glosses none. */
    private fun meaning(c: CaseLine): String = c.gloss?.let(GLOSSES::get)?.let { inBase(it) } ?: "«${c.question}»"

    /**
     * The models as said, each phrase marked: the first model's phrases (at most [MODEL_PHRASES]: "pet hiš · pet stolov ·
     * pet jabolk"), and the next model's first when the first is one phrase ("Grem v šolo.", "Grem na pošto.").
     */
    private fun models(models: List<String>): String? {
        val phrases = models.map { m -> m.split(" · ").map(String::trim).filter(String::isNotEmpty) }.filter { it.isNotEmpty() }
        val first = phrases.firstOrNull() ?: return null
        val said = if (first.size == 1) first + phrases.drop(1).take(1).map { it.first() } else first.take(MODEL_PHRASES)
        return said.joinToString(" ") { p -> if (p.last() in ".!?…") "«$p»" else "«$p»," }.trimEnd(',')
    }

    /**
     * A rule not yet's title as said: the base's title ([base]), a part of it the Slovene title ([target]) has too marked
     * Slovene ("«Nimam časa»: the genitive after a negation", "The plural: «konji, ovce, okna»").
     */
    fun title(target: String, base: String): String {
        if (target == base) return base
        val sl = target.split(": ").map { it.trim().lowercase() }.toSet()
        return base.split(": ").joinToString(": ") { part -> if (part.trim().lowercase() in sl) "«${part.trim()}»" else part }
    }
}
