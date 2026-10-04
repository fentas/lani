package si.lanisce.lani.game

import si.lanisce.lani.data.GrammarCell
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.Numbers
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget

/**
 * The question a missing word answers and the case it asks for: [question] as a Slovene teacher asks it ("Kje?", or the
 * case's own, "Koga? Česa?"), [gloss] what it means in the learner's base (a string table's key: "where? German: wo?";
 * null for the case's own question, whose case's name says it).
 */
data class CaseLine(val question: String, val gloss: String?, val case: Case)

/**
 * What "📖 Namig · Hint" shows at a learner's turn (companion/SCENES.md, "The hint"): the rule, never the answer. The
 * turn's rule is its [pages] of the grammar book (they lead there, "📖 V knjigo"). For a Slovene turn: the question the
 * missing word answers and its case ([cases]; a preposition that takes two, both: where to and where), or the rule in
 * words ([rule], a string table's key, after its Slovene [question]); what decides the form in this sentence ([trigger]:
 * the preposition, the number, the no, the verb); [models] with other words; the rows of the page's table about the
 * trigger ([rows]). [right]: the turn's answer at its gap (lower case), [forms]: every choice's word there; nothing
 * shown gives the answer away ([TurnHints.fair]).
 */
data class TurnHint(
    val pages: List<String>,
    val cases: List<CaseLine> = emptyList(),
    val question: String? = null,
    val rule: String? = null,
    val trigger: Trigger? = null,
    val models: List<String> = emptyList(),
    val rows: List<List<GrammarCell>> = emptyList(),
    val right: Set<String> = emptySet(),
    val forms: Set<String> = emptySet(),
)

/**
 * The hint in the learner's languages: its [lines] (the case's question, the trigger, the rule), the [models] ("Like: pet
 * hiš · pet stolov"), the page table's [rows], and [links] to the pages (their title, null where it would give the
 * answer away: "Nimam časa: …" at "Nimam ____.").
 */
data class HintText(val lines: List<String>, val models: String?, val rows: List<String>, val links: List<Pair<String, String?>>) {
    val isEmpty: Boolean get() = lines.isEmpty() && models == null && rows.isEmpty() && links.isEmpty()
}

/**
 * The hint of a learner's turn (companion/SCENES.md, "The hint"; docs/grammar-syllabus.md, "The hint chip"): the rule of
 * the turn, the question and its trigger in the sentence, and a model with another word; never the turn's own form. Its
 * rule is the pages the wrong choices name (`grammar`), else the turn's, else the right choice's (a tap turn names it
 * there), else the page the app guessed from their whys ([si.lanisce.lani.game.scene.DialogChoice.rule]). Pure.
 */
object TurnHints {
    private const val NUMBERS = "stevila-samostalniki"

    /**
     * What a page's rule is, for the hint: the [cases] its missing word takes (the first the page's own), the Slovene
     * [question] and its [gloss] when it isn't the case's own, the rule in words ([text], a string table's key: the pages
     * that aren't about a case, and those whose rule needs saying), and [models] with other words than any turn's.
     */
    private class Rule(
        val cases: List<Case> = emptyList(),
        val question: String? = null,
        val gloss: String? = null,
        val text: String? = null,
        val models: List<String> = emptyList(),
        val cue: Trigger.Cue.Kind? = null,
    )

    private val ALL = Case.entries.toList()

    /** The book's pages (companion/grammar/sl), by id. The cases' questions are fixed: a small table, not a page field. */
    private val RULES: Map<String, Rule> = mapOf(
        "imenovalnik" to Rule(listOf(Case.NOM), models = listOf("To je knjiga.", "Kje je ključ?", "Anton, poglej!")),
        "rodilnik" to Rule(listOf(Case.GEN), models = listOf("sledi medveda", "kos kruha", "brez sladkorja")),
        "rodilnik-nikalnica" to Rule(listOf(Case.GEN), models = listOf("Nimam časa.", "Ne pijem kave.", "Danes ni vetra.", "Ne vidim ovce.")),
        "rodilnik-predlogi" to Rule(listOf(Case.GEN), "Od kod?", "hint.qWhereFrom", models = listOf("iz Gorice", "s pošte", "od babice", "do Ljubljane", "brez mleka")),
        "rodilnik-kolicina" to Rule(listOf(Case.GEN), "Koliko?", "hint.qHowMany", models = listOf("malo vode", "kos kruha", "veliko jabolk", "liter mleka")),
        "dajalnik" to Rule(listOf(Case.DAT), models = listOf("Pomagam mami.", "Dam bratu kruh.", "Grem k zdravniku.", "Nesem gostom vino.")),
        "tozilnik" to Rule(listOf(Case.ACC), models = listOf("Vidim brata.", "Pijem kavo.", "Imam psa.", "Jem kruh.")),
        "kam-tozilnik" to Rule(listOf(Case.ACC, Case.DAT), "Kam?", "hint.qWhereTo", "hint.ruleWhereTo", listOf("Grem v šolo.", "Grem na pošto.", "Grem k babici.", "Nesem drva za šotor.")),
        "mestnik" to Rule(listOf(Case.LOC), models = listOf("o knjigi", "pri babici", "po kosilu", "v šoli")),
        // the locative of place (v, na, ob, pri); a tutor's older turn may still test pod, za … + the instrumental here
        "kje-mestnik-orodnik" to Rule(listOf(Case.LOC, Case.INS), "Kje?", "hint.qWhere", "hint.ruleWhere", listOf("v šoli", "na travniku", "pri babici", "ob ribniku", "pod mizo")),
        // with (z/s), and where with pod, nad, pred, za, med (the page's rows, one per preposition)
        "orodnik" to Rule(listOf(Case.INS), models = listOf("kava z mlekom", "s kruhom", "z babico", "pod mizo", "za hišo", "nad vasjo", "pred šolo", "med drevesi")),
        "kdaj-cas" to Rule(listOf(Case.LOC, Case.ACC, Case.INS), "Kdaj?", "hint.qWhen", "hint.ruleWhen", listOf("ob sedmih", "v soboto", "čez eno uro", "zjutraj")),
        "skloni" to Rule(ALL, text = "hint.ruleCases"),
        "sklanjatev-moski" to Rule(ALL),
        "sklanjatev-zenski" to Rule(ALL),
        "sklanjatev-srednji" to Rule(ALL),
        "osebni-zaimki" to Rule(ALL, text = "hint.rulePronouns", models = listOf("meni · mi", "njega · ga", "njih · jih", "tebe · te")),
        NUMBERS to Rule(text = "hint.ruleNumbers"),
        "ti-vi" to Rule(question = "Ti ali vi?", text = "hint.ruleTiVi", models = listOf("Kako si? · Kako ste?", "Pridi! · Pridite!", "Imaš čas? · Imate čas?")),
        "pridevniki-ujemanje" to Rule(question = "Kakšen? Kakšna? Kakšno?", text = "hint.ruleAgree", models = listOf("dober dan · dobra kava · dobro jutro", "moj brat · moja sestra · moje vino", "star mož · stara žena · staro drevo")),
        "spol-samostalnikov" to Rule(text = "hint.ruleGender", models = listOf("brat, kruh: on · sestra, kava: ona · jajce, mleko: ono")),
        "dvojina" to Rule(text = "hint.ruleDual", models = listOf("midva sva · vidva sta", "midva greva · onadva gresta", "dva brata · dve sestri"), cue = Trigger.Cue.Kind.WHO),
        "glagoli-sedanjik" to Rule(question = "Kdo?", text = "hint.rulePerson", models = listOf("delam · delaš · dela · delamo · delate · delajo", "govorim · govoriš · govori", "pijem · piješ · pije"), cue = Trigger.Cue.Kind.WHO),
        "biti" to Rule(question = "Kdo?", text = "hint.ruleBiti", models = listOf("jaz sem · ti si · mi smo · vi ste"), cue = Trigger.Cue.Kind.WHO),
        "imeti-iti" to Rule(question = "Kdo?", text = "hint.rulePerson", models = listOf("imam · imaš · ima · imamo", "grem · greš · gre · gremo"), cue = Trigger.Cue.Kind.WHO),
        "pretekli-cas" to Rule(question = "Kdaj?", text = "hint.rulePast", models = listOf("Včeraj sem delal.", "Bila je doma.", "Kupili smo kruh."), cue = Trigger.Cue.Kind.PAST),
        "prihodnjik" to Rule(question = "Kdaj?", text = "hint.ruleFuture", models = listOf("Jutri bom delal.", "Jutri bomo šli.", "Kmalu bo deževalo."), cue = Trigger.Cue.Kind.FUTURE),
        "naslonke" to Rule(text = "hint.ruleClitics", models = listOf("Dal sem ti ga.", "Imenujem se Jan.", "Videl ga bom.")),
        "modalni-glagoli" to Rule(text = "hint.ruleModal", models = listOf("moram narediti", "znam plavati", "lahko pomagam", "smem vzeti")),
        "povratni-glagoli" to Rule(text = "hint.ruleReflexive", models = listOf("učim se", "umijem se", "bojim se teme")),
        "pogojnik" to Rule(text = "hint.ruleWould", models = listOf("kupil bi", "rad bi kavo", "rada bi šla")),
        "velelnik" to Rule(text = "hint.ruleImperative", models = listOf("pazi! · pazite!", "pridi! · pridite!", "počakaj! · počakajte!")),
        "primernik" to Rule(text = "hint.ruleComparative", models = listOf("lep · lepši · najlepši", "star · starejši · najstarejši", "dober · boljši · najboljši")),
        "prislovi" to Rule(question = "Kako?", text = "hint.ruleAdverb", models = listOf("Sova dobro vidi.", "Muri hitro teče.", "Govorite počasi.")),
        "zanikanje" to Rule(text = "hint.ruleNoTwice", models = listOf("Ne vidim nič.", "Nikoli ni doma.", "Nihče ne pride.")),
        "svoj" to Rule(text = "hint.ruleSvoj", models = listOf("Vzamem svoj klobuk.", "Micka išče svojo košaro.")),
        "vprasalnice" to Rule(text = "hint.ruleQuestions", models = listOf("Kje si? · Kam greš? · Od kod si?")),
        "glagolski-vid" to Rule(question = "Kaj delam? Kaj naredim?", text = "hint.ruleAspect", models = listOf("pisal sem · napisal sem", "kupujem · kupim", "jedel sem · pojedel sem")),
        "datumi" to Rule(text = "hint.ruleOrdinal", models = listOf("drugi · tretji · četrti", "petega septembra")),
        "mnozina" to Rule(text = "hint.rulePlural", models = listOf("konj · konji", "ovca · ovce", "okno · okna")),
        "namenilnik" to Rule(text = "hint.ruleSupine", models = listOf("Grem spat.", "Pridi jest!")),
        "oziralni-ki" to Rule(text = "hint.ruleKi", models = listOf("Človek, ki pride.", "Knjiga, ki jo berem.")),
        "odvisni-govor" to Rule(text = "hint.ruleReported", models = listOf("Rekel je, da pride.", "Rekla je, naj počakam.")),
        "vezniki" to Rule(text = "hint.ruleJoining", models = listOf("Doma sem, ker dežuje.", "Dežuje, zato sem doma.")),
    )

    /** A number and its noun, by the number's category (1, 2, 3–4, 5 and up): feminine, masculine, neuter. */
    private val COUNTED: Map<Int, List<String>> = mapOf(
        Numbers.ONE to listOf("ena hiša · en stol · eno jabolko", "ena miza · en kozarec · eno okno"),
        Numbers.TWO to listOf("dve hiši · dva stola · dve jabolki", "dve mizi · dva kozarca · dve okni"),
        Numbers.FEW to listOf("tri hiše · trije stoli · tri jabolka", "štiri mize · štirje kozarci · štiri okna"),
        Numbers.MANY to listOf("pet hiš · pet stolov · pet jabolk", "deset miz · deset kozarcev · deset oken"),
    )

    /** The four categories of one noun: what the numbers page teaches. */
    private val LADDERS = listOf("ena hiša · dve hiši · tri hiše · pet hiš", "en stol · dva stola · trije stoli · pet stolov")

    /** The number's own word, by its noun's gender. */
    private val AGREEING = listOf("en stol · ena hiša · eno jabolko", "dva stola · dve hiši · dve jabolki", "trije stoli · tri hiše · tri jabolka")

    /**
     * The pages of [line]'s rule: those its wrong choices name (at a form turn, those of the form's; else all), else the
     * turn's own, else those its right choices name (a tap turn's), in order, each once.
     */
    fun pagesOf(line: DialogLine): List<String> {
        val c = line.choices
        val right = c.indexOfFirst { it.ok }.takeIf { r -> r >= 0 && c.count { it.ok } == 1 }
        val wrong = right?.let { Forms.turn(c.map { it.sl }, it) }?.wrong ?: c.indices.filter { !c[it].ok }
        return wrong.mapNotNull { c[it].rule(line) }.distinct()
            .ifEmpty { listOfNotNull(line.grammar) }
            .ifEmpty { c.filter { it.ok }.mapNotNull { it.grammar }.distinct() }
    }

    /**
     * The hint of learner's turn [line] in [language], the book's pages by [page]; null when its rule has no page in the
     * book (a word's meaning, a greeting's hour: no hint). A turn of another language than Slovene gets its pages alone.
     */
    fun of(line: DialogLine, language: String, page: (String) -> GrammarPage?): TurnHint? {
        val c = line.choices
        if (c.size < 2) return null
        val pages = pagesOf(line).filter { page(it) != null }
        if (pages.isEmpty()) return null
        val texts = c.map { it.sl }
        val right = c.indexOfFirst { it.ok }.takeIf { r -> r >= 0 && c.count { it.ok } == 1 }
        val form = right?.let { Forms.turn(texts, it) }
        val base = if (form != null) {
            val w = form.gap.word.lowercase()
            TurnHint(pages, right = setOf(w), forms = form.wrong.mapNotNull { Forms.wordAt(texts[it], form.gap.slot)?.lowercase() }.toSet() + w)
        } else {
            // no one word: the words its right choices say and not every choice does (a tap turn's place, "vrati")
            val common = texts.map { Forms.words(it).toSet() }.reduce { a, b -> a intersect b }
            fun own(t: String) = Forms.words(t).filter { it !in common && it.length >= 3 && it !in Triggers.PREPOSITIONS }.toSet()
            val r = c.filter { it.ok }.flatMap { own(it.sl) }.toSet()
            TurnHint(pages, right = r, forms = r + texts.flatMap { own(it) })
        }
        if (language != "sl") return base
        val id = pages.firstOrNull { it in RULES } ?: return base
        val rule = RULES.getValue(id)
        val sentence = if (form != null && right != null) texts[right] else null
        val slot = form?.gap?.slot
        val made = when {
            id == NUMBERS -> numbers(base, sentence, slot)
            rule.cases.isNotEmpty() -> cases(base, id, rule, if (sentence != null && slot != null) Triggers.of(sentence, slot) else emptyList())
            else -> base.copy(
                question = rule.question, rule = rule.text,
                trigger = rule.cue?.let { k -> if (sentence != null && slot != null) Triggers.cues(sentence, slot).firstOrNull { it.kind == k } else null },
            )
        }
        // a model with the trigger's own word first (after «pod»: pod mizo; after «do»: do Ljubljane)
        val near = (made.trigger as? Trigger.Preposition ?: made.trigger as? Trigger.Amount)?.word?.lowercase()
        val models = made.models.ifEmpty { rule.models }.filter { quiet(it, base.forms) }
            .sortedBy { m -> if (near != null && near in Forms.words(m)) 0 else 1 }.take(2)
        return made.copy(models = models, rows = rows(page(id), made.trigger, base.forms))
    }

    /** A numbers turn: the number's category and its case (after the number), or the number's own word (by its noun). */
    private fun numbers(base: TurnHint, sentence: String?, slot: Int?): TurnHint {
        if (sentence == null || slot == null) return base.copy(rule = "hint.ruleNumbers", models = LADDERS)
        val spans = Forms.spans(sentence)
        if (Triggers.number(spans[slot].value) != null) {
            val noun = spans.getOrNull(slot + 1)?.let { Trigger.Noun(it.value, slot + 1) }
            return base.copy(rule = "hint.ruleNumberAgrees", trigger = noun, models = AGREEING)
        }
        val count = Triggers.of(sentence, slot).firstOrNull { it is Trigger.Count } as? Trigger.Count
            ?: return base.copy(rule = "hint.ruleNumbers", models = LADDERS)
        val lines = if (count.category == Numbers.MANY) listOf(CaseLine(Case.GEN.question, null, Case.GEN)) else emptyList()
        return base.copy(cases = lines, trigger = count, models = COUNTED.getValue(count.category))
    }

    /** A case page's turn: the question and case its trigger asks for (the page's first), else the page's own. */
    private fun cases(base: TurnHint, id: String, rule: Rule, triggers: List<Trigger>): TurnHint {
        val t = triggers.firstOrNull { t -> Triggers.cases(t).any { it in rule.cases } }
        val lines = when (t) {
            is Trigger.Preposition -> prepositions(t, id, rule)
            is Trigger.Amount -> listOf(CaseLine("Koliko?", "hint.qHowMany", Case.GEN))
            null -> rule.cases.firstOrNull()?.takeIf { rule.cases.size == 1 || id == "kam-tozilnik" }?.let {
                listOf(CaseLine(rule.question ?: it.question, rule.gloss, it))
            }.orEmpty()
            // a number, a no, a verb: the case it asks for, the page's
            else -> Triggers.cases(t).first { it in rule.cases }.let { listOf(CaseLine(it.question, null, it)) }
        }
        // a preposition says it all; without one, the page's rule in words where it has one (Kje? v, na … mestnik)
        return base.copy(
            cases = lines, trigger = t, question = rule.question.takeIf { lines.isEmpty() },
            rule = rule.text.takeIf { t !is Trigger.Preposition },
        )
    }

    /** The questions a preposition asks, the page's case first: v + Kam? the accusative, v + Kje? the locative. */
    private fun prepositions(t: Trigger.Preposition, id: String, rule: Rule): List<CaseLine> {
        val time = id == "kdaj-cas"
        // where to against where is the sentence's meaning: both, the page's first; z/s (with, or off), the page's alone
        val cases = if (!time && t.word.lowercase() in Triggers.PLACES) t.cases.sortedBy { c -> rule.cases.indexOf(c).takeIf { it >= 0 } ?: 9 }
        else t.cases.filter { it in rule.cases }.take(1)
        return cases.map { preposition(t.word, it, time) }
    }

    /**
     * The question [prep] asks with [case]: Kam? (v + the accusative), Kje? (v + the locative, pod + the instrumental),
     * Od kod? (iz), O kom? O čem? (o), Kdaj? ([time]: ob petih, v sredo); else the case's own.
     */
    fun preposition(prep: String, case: Case, time: Boolean = false): CaseLine {
        val w = prep.lowercase()
        return when {
            time -> CaseLine("Kdaj?", "hint.qWhen", case)
            w in Triggers.PLACES && case == Case.ACC -> CaseLine("Kam?", "hint.qWhereTo", case)
            w in Triggers.PLACES && (case == Case.LOC || case == Case.INS) -> CaseLine("Kje?", "hint.qWhere", case)
            w == "ob" || w == "pri" -> CaseLine("Kje?", "hint.qWhere", case)
            case == Case.GEN && w in setOf("iz", "od", "z", "s") -> CaseLine("Od kod?", "hint.qWhereFrom", case)
            case == Case.LOC && w == "o" -> CaseLine("O kom? O čem?", null, case)
            else -> CaseLine(case.question, null, case)
        }
    }

    /** The rows of [p]'s table about [trigger] (its word in a cell; a number's category in the first) that show no form of the turn. */
    private fun rows(p: GrammarPage?, trigger: Trigger?, forms: Set<String>): List<List<GrammarCell>> {
        val table = p?.table ?: return emptyList()
        val about: (List<GrammarCell>) -> Boolean = when (trigger) {
            null, is Trigger.Noun, is Trigger.Cue -> return emptyList()
            is Trigger.Count -> { row -> Regex("""\d+""").findAll(all(row.first())).any { Numbers.slovene(it.value.toInt()) == trigger.category } }
            else -> { row -> row.any { c -> trigger.word.lowercase() in Forms.words(all(c)) } }
        }
        return table.rows.filter { r -> about(r) && r.none { c -> forms.any { shows(all(c), it, stem = true) } } }.take(2)
    }

    /** Everything a cell says, in any language: what a turn's form is looked for in. */
    private fun all(c: GrammarCell): String = c.words ?: c.text?.values?.joinToString(" ").orEmpty()

    /**
     * Whether [text] shows [form]: one of its words is it; with [stem], also one of the same stem, the ending aside (four
     * letters or more: "krav" in "krava", "mleko" in "z mlekom"), which an example with another word never has.
     */
    fun shows(text: String, form: String, stem: Boolean = false): Boolean = Forms.words(text).any { w -> w == form || (stem && same(w, form)) }

    private fun same(a: String, b: String): Boolean {
        if (a.length < 4 || b.length < 4) return false
        val p = a.commonPrefixWith(b).length
        return p >= 4 && p >= minOf(a.length, b.length) - 2
    }

    /** A model shows none of the turn's forms, nor their stems: not the answer, and not a wrong one either. */
    private fun quiet(text: String, forms: Set<String>): Boolean = forms.none { shows(text, it, stem = true) }

    /**
     * Whether [text] gives none of [h]'s answer away: it shows no right form, or every form of the turn side by side (a
     * rule that sets them against each other: "dva, dve by the noun's gender").
     */
    fun fair(text: String, h: TurnHint): Boolean = h.right.none { shows(text, it) } || (h.forms.size > 1 && h.forms.all { shows(text, it) })

    // --- the hint in words ------------------------------------------------------------------------------------------

    /** The hint in the learner's languages ([L10n.pair]), every part [fair]: what the chip's card shows. */
    fun text(h: TurnHint, page: (String) -> GrammarPage?): HintText {
        val lines = buildList {
            for (c in h.cases) add(caseLine(c))
            h.trigger?.let { add(trigger(it)) }
            h.rule?.let { r -> add(listOfNotNull(h.question, inBase(r)).joinToString(" ")) }
        }.filter { fair(it, h) }
        val models = h.models.takeIf { it.isNotEmpty() }?.let { "${inBase("hint.like")}: ${it.joinToString(" · ")}" }
        val base = L10n.pair.base.code
        val rows = h.rows.map { r -> r.joinToString(" · ") { it.shown(base) } }.filter { fair(it, h) }
        val links = h.pages.map { id -> id to page(id)?.titleShown(L10n.pair)?.takeIf { fair(it, h) } }
        return HintText(lines, models, rows, links)
    }

    /** "Kje? (where? German: wo?) → 5. mestnik (locative)". */
    fun caseLine(c: CaseLine): String =
        "${c.question}${c.gloss?.let { " (${inBase(it)})" }.orEmpty()} → ${c.case.n}. ${inTarget(c.case.key)} (${inBase(c.case.key)})"

    /** What decides the form, in the learner's base: "after «Deset»: five and up, the genitive plural". */
    fun trigger(t: Trigger): String = when (t) {
        is Trigger.Preposition, is Trigger.Amount -> inBase("hint.after", "word" to t.word)
        is Trigger.Count -> inBase("hint.afterNumber", "word" to t.word, "cat" to category(t.category))
        is Trigger.Negation -> inBase("hint.afterNo", "word" to t.word)
        is Trigger.Partner -> inBase("hint.with", "word" to t.word) + if (t.lemma == "pomagati") " " + inBase("hint.likeHelfen") else ""
        is Trigger.Noun -> inBase("hint.withNoun", "word" to t.word)
        is Trigger.Cue -> when (t.kind) {
            Trigger.Cue.Kind.WHO -> inBase("hint.who", "word" to t.word)
            Trigger.Cue.Kind.PAST -> inBase("hint.whenPast", "word" to t.word)
            Trigger.Cue.Kind.FUTURE -> inBase("hint.whenFuture", "word" to t.word)
        }
    }

    /** A number's category as the string tables' select reads it: one, two, few, other (5 and up). */
    fun category(c: Int): String = when (c) {
        Numbers.ONE -> "one"
        Numbers.TWO -> "two"
        Numbers.FEW -> "few"
        else -> "other"
    }
}
