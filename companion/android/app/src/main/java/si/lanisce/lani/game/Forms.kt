package si.lanisce.lani.game

/**
 * A sentence with one word left out: a dialog turn's right answer with the word its choices differ in as the gap
 * ("Deset ____, prosim."), or an exercise's sentence with its gap ("Nimam ___. (I don't have time.)") and the option
 * that fills it. [slot]: which word of the sentence it is (-1 for an exercise's gap).
 */
data class FormGap(val before: String, val word: String, val after: String, val slot: Int = -1) {
    /** The sentence with the gap shown. */
    val shown: String get() = before + GAP + after

    /** The whole sentence, without a translation in brackets at its end: what is said. */
    val sentence: String get() = filled(word)

    /** [w] in the gap, without a translation in brackets at the end. */
    fun filled(w: String): String = (before + w.trim() + after).replace(TRANSLATION, "").trim()

    /** What counts as typed right: the word, or the whole sentence typed out. */
    val accept: List<String> get() = listOf(word, sentence)

    companion object {
        const val GAP = "____"
        private val TRANSLATION = Regex("""\s*\([^()]*\)\s*$""")
    }
}

/**
 * Which turns and exercises test a form (companion/SCENES.md, "Adaptive turns"), and which rule a wrong choice tests
 * when its scene doesn't say ([rule]). Pure.
 */
object Forms {
    private val WORD = Regex("""[\p{L}\p{N}]+(?:['’][\p{L}\p{N}]+)*""")

    /** The words of [s] in order, lower case. */
    fun words(s: String): List<String> = WORD.findAll(s).map { it.value.lowercase() }.toList()

    /** The words of [s] in order, as written ([words] are these, lower case), each with where it is in [s]. */
    fun spans(s: String): List<MatchResult> = WORD.findAll(s).toList()

    /**
     * A turn that tests a form: [gap], its right choice with the word in question left out, and [wrong], the wrong choices
     * (their indices) that differ from it in that word alone.
     */
    data class Turn(val gap: FormGap, val wrong: List<Int>)

    /**
     * Whether a turn's choices test a form: a wrong one differs from the one right choice [right] in a single word, case
     * and punctuation aside ("Deset jajc, prosim." / "Deset jajce, prosim."). The word most wrong choices differ in alone
     * (on a tie, the first one's) is the gap; a wrong choice that differs otherwise (other words, another length, meaning)
     * is no part of it. Null when none differs in one word.
     */
    fun turn(texts: List<String>, right: Int): Turn? {
        if (texts.size < 2 || right !in texts.indices) return null
        val found = WORD.findAll(texts[right]).toList()
        val key = found.map { it.value.lowercase() }
        val at = LinkedHashMap<Int, MutableList<Int>>()
        for ((i, t) in texts.withIndex()) {
            if (i == right) continue
            val w = words(t)
            if (w.size != key.size) continue
            val differ = key.indices.filter { key[it] != w[it] }
            if (differ.size == 1) at.getOrPut(differ[0]) { mutableListOf() } += i
        }
        val (slot, wrong) = at.entries.maxByOrNull { it.value.size }?.toPair() ?: return null
        val m = found[slot]
        val s = texts[right]
        return Turn(FormGap(s.substring(0, m.range.first), m.value, s.substring(m.range.last + 1), slot), wrong)
    }

    /** The gap of a turn that tests a form ([turn]); null when it tests none. */
    fun gap(texts: List<String>, right: Int): FormGap? = turn(texts, right)?.gap

    /** An exercise's sentence with a gap (two or more underscores), filled by [answer]; null without a gap. */
    fun gap(prompt: String, answer: String): FormGap? {
        val m = Regex("_{2,}").find(prompt) ?: return null
        if (answer.isBlank()) return null
        return FormGap(prompt.substring(0, m.range.first), answer.trim(), prompt.substring(m.range.last + 1))
    }

    /** The word at [slot] of [text] (another choice of the turn, as written); null when it has none there. */
    fun wordAt(text: String, slot: Int): String? = WORD.findAll(text).elementAtOrNull(slot)?.value

    /** [text] with its word at [slot] replaced by [word], its punctuation and the rest as they are. */
    fun replaceAt(text: String, slot: Int, word: String): String? {
        val m = WORD.findAll(text).elementAtOrNull(slot) ?: return null
        return text.substring(0, m.range.first) + word + text.substring(m.range.last + 1)
    }

    /**
     * The grammar book's page (Slovene, companion/grammar/sl) a wrong choice's English [why] tells of, guessed: the
     * scenes' whys say what a choice gets wrong ("Two takes the dual: dve košuti.", "Babica Micka is old: vi."), so the
     * first of [GUESSES] whose words are in it names the rule. Where the why sets a place against a direction (kje/kam),
     * the one it names first is the right choice's. Null when none fits (a word's meaning, a greeting's hour: no rule of
     * the book). A scene's choice may name its page itself (`grammar`), which wins.
     */
    fun rule(why: String?): String? {
        val w = why?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val place = PLACE.find(w)
        val direction = DIRECTION.find(w)
        // where something is with pod, nad, pred, za, med: the instrumental's page, unless the why names a direction first
        // (the right choice's: "Where it goes is a direction: za šotor. Za šotorom (instrumental) is where it is.")
        UNDER.find(w)?.let { u -> if (direction == null || u.range.first < direction.range.first) return "orodnik" }
        for ((page, words) in GUESSES) {
            if (page in PLACES && place != null && direction != null) {
                return if (place.range.first < direction.range.first) "kje-mestnik-orodnik" else "kam-tozilnik"
            }
            if (words.containsMatchIn(w)) return page
        }
        return null
    }

    private val PLACES = setOf("kam-tozilnik", "kje-mestnik-orodnik", "tozilnik")
    // "where it is", not "where it goes is a direction"
    private val PLACE = Regex("""\blocative\b|\bis a place\b|\bwhere (something|someone|it|they|the \p{L}+|you are|you will)\b(?! (goes|go|went)\b)[^.]*\b(is|are|lies|hangs|meet)\b|\bwhere it (already )?is\b|\bbeing at\b""")
    private val DIRECTION = Regex("""\bdirection\b|\bwhere to\b|\baccusative\b|\bgoing\b|\bputting\b|\bclimbing\b|\bcoming in\b|\bdomov\b""")

    /** Where something is with pod, nad, pred, za, med and the instrumental ("Za takes the instrumental: za vrati", "Behind it: za smreko"). */
    private val UNDER = Regex("""\b(pod|nad|pred|za|med)\b[^.]*\binstrumental\b|\binstrumental\b[^.]*\b(pod|nad|pred|za|med)\b|\b(under|behind|over|above|in front of|between|among) (it|them|us|you|the \p{L}+):\s*(pod|za|nad|pred|med)\b""")

    /*
     * A word that starts or ends in č, š, ž is fenced by lookarounds, not \b: the JVM's \b (Java 19 on) sees only ASCII
     * letters as a word's, the phone's (ICU) every letter, and a guess must be the same on both.
     */

    /**
     * In order: the first page whose words a why has is the rule its wrong choice tests. The rules the scenes' whys state
     * in a phrase of their own come first (a question word, aspect, a date, the plural, the supine, ki, reported speech, a
     * joining word, a clitic's order, a double no, se, the hour, the comparative, the modal verbs, the future, the dative,
     * the instrumental with z/s, the nominative, a quantity …), then the older, broader guesses.
     */
    private val GUESSES: List<Pair<String, Regex>> = listOf(
        // what a question word asks ("Kje asks where. To ask when: kdaj.")
        "vprasalnice" to Regex("""\b(kje|kam|kdaj|kaj|kdo|kako|od kod) (is|asks)\b"""),
        "glagolski-vid" to Regex("""\bperfective\b|\bimperfective\b|\baspect\b|\(done\)|\bis only '(i|you|he|she|we|they) (was|were)\b"""),
        "datumi" to Regex("""\bordinal\b|\bprvi is\b|\bthe first (one|time)\b|\bthe date\b"""),
        "mnozina" to Regex("""\bis more than one\b|\bthe plural of\b"""),
        "namenilnik" to Regex("""\bsupine\b|\bgrem spat\b"""),
        "oziralni-ki" to Regex("""\brelative (clause|pronoun)\b|\bki (is|refers|joins|takes)\b"""),
        "odvisni-govor" to Regex("""\breported speech\b|\bin reported\b"""),
        "vezniki" to Regex("""\bjoining word\b|\bconjunction\b|(?<!\p{L})(ker|zato|vendar|čeprav|ampak) (is|means)\b"""),
        "naslonke" to Regex("""\bfixed order\b|\bshort (words|pronouns)\b|\bclitic|\bbefore the accusative\b|\bcomes before (si|jih|mi|je|bom|se)\b|\bse comes before\b"""),
        "zanikanje" to Regex("""\bdoubles the no\b|\bno twice\b|\bwith (nič|nikoli)(?!\p{L})"""),
        "biti" to Regex("""\bno-form of (sem|je)\b|'not' of sem\b"""),
        "imeti-iti" to Regex("""\bnisem (is|means) '?i am not|\bhaving is imeti\b|\bne \+ imam\b"""),
        "povratni-glagoli" to Regex("""\bneeds se\b|\bis reflexive\b|\bwithout se\b|\bbati se\b|\bse (bojim|boji|bojiš|zbudim)(?!\p{L})|\bigram se\b"""),
        // when: the hour with ob, a day, a season, čez and pred, how long
        "kdaj-cas" to Regex("""\bob\b[^.]*\b(time|times|hour|clock)\b|\b(time|hour)\b[^.]*\bob\b|(?<!\p{L})čez(?!\p{L})|\bpred eno uro\b|\ban hour ago\b|\bhow long\b|\bday of the week\b|\bon a day\b|\bthe day, wednesday\b|\bone word: pozimi\b|\bwhen: zjutraj\b|\ba time word\b|\btelling the time\b|\bura is one\b"""),
        "primernik" to Regex("""\bcomparative\b|\bsuperlative\b|\bnaj- goes\b|\bbolj\b[^.]*\b(not said|isn't said|not used|twice)\b|\bless \(an amount\)"""),
        "pridevniki-ujemanje" to Regex("""\bagreeing with\b"""),
        "prislovi" to Regex("""\badverb\b|\bkako lepo\b|\b(noter|notri|zunaj|spodaj)\b"""),
        "modalni-glagoli" to Regex("""\bafter (moram|moraš|smem|a modal|lahko)\b|\bsmem takes\b|\bmodal verb\b|\blahko (goes|takes|has)\b|\brad goes with\b|\bknowing how to do something\b|\bto ask (if you may|for permission)\b|\bhad to is morati\b|\bcan't is ne morem\b|\bne morem means\b|\bsmeš means\b|\bmoram asks if\b"""),
        "pogojnik" to Regex("""\bconditional\b|\bbi goes with\b|\brad bi\b"""),
        // the imperative when it is the right form ("Telling someone to wait: the imperative, počakaj!")
        "velelnik" to Regex(""":\s*the imperative\b"""),
        // the future when it is the right form ("the future, bom pogrnil"; not "Bom spal is the future (I will sleep)")
        "prihodnjik" to Regex("""\bthe future[,:]|\bneeds the future\b|\bstill to come\b|\bfor tomorrow\b|\babout the future\b|\(future\)|\bthe future is bom\b|\bbom \+ the l-form\b|\bafter bom\b|\bis 'he or she will'|\bis 'you \(vi\) will'|\babout yourself: bom\b|\bfor yourself: jaz bom\b|\bare many: bodo\b|\bbom is i will\b|\bthe weather is bo\b|\bbo nehalo\b|\bpridem bom\b|\bgoes with bom\b|\bfor what you will do\b"""),
        "pretekli-cas" to Regex("""\bthe past( tense)? is sem \+|\blast night\b|\bafter nisem comes the l-form\b|\bthe l-form is too\b|\b(masculine|feminine)[^.]*: (\p{L}+ )?je \p{L}+(l|la|lo)\b"""),
        // the dative when it is the right form ("takes the dative", "Giving to the ducks: the dative"; not "Mu is the dative")
        "dajalnik" to Regex("""\btakes the dative\b|\b(is|are) in the dative\b|\bwith the dative\b|: the dative\b|\(dative \p{L}+\)|\bis the dative:|\bfeeling cold\b|\b(mi|meni) je mrzlo\b|(?<!\p{L})všeč(?!\p{L})|\bk \(towards"""),
        "dvojina" to Regex("""\bdual pronoun\b"""),
        // a pronoun's number and person: jih for a plural, jim to them, te to the one you talk to
        "osebni-zaimki" to Regex("""\bplural[^.]*:\s*(\p{L}+ )?jih\b|\bmany:\s*(\p{L}+ )?jih\b|\bga is 'him'\.|\bjim is to them\b"""),
        // jo or ga by the noun's gender
        "spol-samostalnikov" to Regex("""\b(masculine|feminine|neuter|a woman)\b[^.]*:\s*(\p{L}+ ){0,3}(jo|ga)\b"""),
        "mestnik" to Regex("""\bo \(about\)|\bdeclines like an adjective\b|\bpri \(at someone's\)"""),
        // z/s, with: the instrumental of company and means (pod, za … + the instrumental, where: UNDER, before these)
        "orodnik" to Regex("""\b(z|s|s/z|z/s|with)\b[^.]*\binstrumental\b|\binstrumental\b[^.]*\b(z|s)\b|\bbefore (a voiced|k, p)\b|\bwith is z\b"""),
        "imenovalnik" to Regex(""": the nominative\b|\bin the nominative\b|\bname stays as it is\b|\bname as it is\b|\bcalling her\b|\bkot ogledalo\b|\bexists only in the plural\b|\bis plural in slovene: \p{L}+ so\b|\bvrata \(door\) is plural\b"""),
        "rodilnik-kolicina" to Regex("""\b(malo|veliko|kos|koliko)\b[^.]*\bgenitive\b|(?<!\p{L})(več|preveč)(?!\p{L})[^.]*\bgenitive\b|\ban amount takes\b|\bis a quantity\b"""),
        "rodilnik" to Regex("""\bwhose\b[^.]*\bgenitive\b|\bof the \p{L}+: the genitive\b|\bthe top of something\b"""),
        "rodilnik-nikalnica" to Regex("""\bafter a no\b|\bne bo \(there won't be\) takes\b"""),
        "stevila-samostalniki" to Regex("""\b(dva|dve|en|ena|eno) is (for )?(masculine|feminine)"""),
        "svoj" to Regex("""\bsvoj\b|\bnjen\p{L}* is 'her'"""),
        // an accusative pronoun after a verb ("Ujeti takes the accusative: ujel sem te. Ti is the dative.")
        "tozilnik" to Regex("""\btakes the accusative: (\p{L}+ )*(te|ga|jo|me|vas)\b|\ba direct object\b"""),
        // numbers with nouns: a number (or koliko, an amount) and the noun's form after it
        "stevila-samostalniki" to Regex("""\b(from|after) (five|5|pet|dvajset)\b|\bfive and up\b|\bfor (three|3) and (four|4)\b|\bthree and four\b|(?<!\p{L})(two|three|four|five|six|seven|eight|nine|ten|twenty|dva|dve|tri|štiri|pet|šest|sedem|osem|devet|deset|dvajset) takes\b|\bfor (five|six|seven|eight|nine|ten|twenty)\b|\bkoliko\b|\ban amount\b|\bcounting\b"""),
        "dvojina" to Regex("""\bdual\b|\bthe two of (you|us)\b|\bjust (the two|you and)\b|\btwo of you\b|\byou two\b"""),
        "biti" to Regex("""\bbiti\b|\bmeans 'you are'"""),
        // who does it: the verb's person ("Kuham is 'I cook'")
        "glagoli-sedanjik" to Regex("""\b\p{L}+ is '(i|you|he|she|it|we|they)\b|\bis you \(|\bgoes with (imam|grem|sem)\b"""),
        "ti-vi" to Regex("""\b(vi|ti)\b|\bpolite\b|\bformal\b|\bis (old|older)\b|\bis for one person\b|\bseveral children\b"""),
        "rodilnik-nikalnica" to Regex("""\bnegat|\bnimam\b|\bnima\b|\bni\b[^.]*\bgenitive\b|\bgenitive\b[^.]*\b(no|not|none)\b"""),
        "rodilnik-predlogi" to Regex("""\b(iz|od|do|brez|blizu|z|s)\b[^.]*\bgenitive\b|\bgenitive\b[^.]*\b(iz|od|do|brez|blizu)\b|\bcoming from\b"""),
        // the instrumental of an older why that names no preposition: pod, za … where something is
        "orodnik" to Regex("""\binstrumental\b"""),
        "vprasalnice" to Regex("""\b(kje|kam|kdaj|kaj|kdo|kako|od kod) (is|asks)\b|\basks where\b|\basking (about|what|where|when|who)\b"""),
        "kam-tozilnik" to Regex("""\bdirection\b|\bwhere to\b|\bdomov\b"""),
        "kje-mestnik-orodnik" to Regex("""\blocative\b"""),
        "tozilnik" to Regex("""\baccusative\b|\bdirect object\b|\bthe object\b"""),
        "pretekli-cas" to Regex("""\bpast\b|\bparticiple\b"""),
        "naslonke" to Regex("""\bclitic|\bsecond place\b|\bword order\b"""),
        "pridevniki-ujemanje" to Regex("""\b(masculine|feminine|neuter)\b|\bis a man\b|\bis a woman\b|\bagree"""),
        "biti" to Regex("""\b(je|so|sem|si) is for\b|\bso \(they are\)"""),
        "glagoli-sedanjik" to Regex("""\binfinitive\b|\bconjugat|\bpersonal form\b|\bimperative\b|\bis for several\b|\bis the plural\b|\bplural, three\b|\bthree or more\b|\bmeans you\b|'(i|you|he|she|we|they)\b|\((i|you|he|she|we|they|jaz|mi|ti|vi|oni)\b|\bis (we|you|they)\b|\bverb\b"""),
    )
}
