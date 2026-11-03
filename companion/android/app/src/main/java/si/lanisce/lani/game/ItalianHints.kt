package si.lanisce.lani.game

/**
 * The hint chip's rules for the Italian book (companion/grammar/it; companion/SCENES.md, "The hint";
 * docs/plans/05-built/hint-chip-it.md): each page's rule in words ([Rule.text], a string table's key, in the learner's
 * base), the sentence the 🔊 says of it ([Rule.said]), models with other words, the question where the page asks one
 * ("Tu o Lei?"), and what in the sentence decides the turn's form ([Look], tried in the page's order: the first found is
 * the trigger). Italian has no cases: what decides is the noun an article or an adjective agrees with ("Manca ____ tetto":
 * with «tetto», its gender, its number and its first sound), who does it (the subject: «io», «le api»), who is spoken to
 * («maestra»: tu or Lei), the participle that picks essere or avere, a word that asks for the congiuntivo («bisogna che»),
 * «se», the preposition before «cui», the verb before an infinitive, a word of time. Word lists, no dictionary: the chip
 * works at once and offline, and finds a trigger only where the sentence shows it plainly; else the rule alone. It reads,
 * it never grades. Pure.
 */
object ItalianHints {
    /** What the chip looks for in the sentence, by page, in order (each is described where it is found: [find]). */
    enum class Look {
        WHO, PAST, FUTURE, HABIT, ONCE, ARTICLE, AGREES, MANY, AUX, PARTICIPLE, REFLEXIVE, SUBJ, IF, CUI, CHE, TO_WHOM, NO,
        INFINITIVE, VERB_PREP, STARE, ABOUT_TO, THING, LIKER, MORE, PRONOUN, FIXED, SAID, HOUR, PLACE, FEEL, FIRST, YOU_AGREE,
    }

    /**
     * A page's rule: [text] the card's rule in words and [said] the 🔊's sentence (string tables' keys), [models] with
     * other words than any turn's, [question] (Italian) where the page asks one, [looks] what decides the form, in order.
     */
    class Rule(val text: String, val said: String, val models: List<String>, val question: String? = null, val looks: List<Look> = emptyList())

    /** The book's pages (companion/grammar/it), by id: every page, A1 to B2. */
    val RULES: Map<String, Rule> = mapOf(
        // A1
        "aggettivi-accordo" to Rule("hint.it.ruleAgree", "hintSay.it.ruleAgree", listOf("il gatto nero · la gatta nera", "i fiori gialli · le rose gialle", "un prato verde · due prati verdi", "Il pane è caldo. · La minestra è calda."), null, listOf(Look.AGREES, Look.YOU_AGREE)),
        "articoli-genere" to Rule("hint.it.ruleArticle", "hintSay.it.ruleArticle", listOf("il pane · lo zaino · l'orso", "la mela · l'ape", "un cane · uno gnomo · una rana · un'oca", "il libro · lo specchio · l'albero"), null, listOf(Look.ARTICLE)),
        "ce-ci-sono" to Rule("hint.it.ruleThereIs", "hintSay.it.ruleThereIs", listOf("C'è un gatto. · Ci sono tre gatti.", "C'è la luna. · Ci sono le stelle.", "Non c'è il sole."), null, listOf(Look.THING)),
        "domande" to Rule("hint.it.ruleQuestions", "hintSay.it.ruleQuestions", listOf("Dove abiti? · Quando vieni?", "Chi è? · Che cosa fai?", "Quanti anni hai? · Quante mele?", "Come stai? · Perché ridi?"), null, listOf(Look.AGREES)),
        "dove-preposizioni" to Rule("hint.it.ruleWhere", "hintSay.it.ruleWhere", listOf("a Udine · a casa", "in Italia · in cucina", "da Nonna Rosa · dal medico", "sotto il tavolo · vicino alla porta"), "Dove?", listOf(Look.PLACE)),
        "essere-avere" to Rule("hint.it.ruleBeHave", "hintSay.it.ruleBeHave", listOf("sono stanco · ho fame", "sei qui · hai sete", "siamo amici · abbiamo freddo", "è contenta · ha sette anni"), "Chi?", listOf(Look.FEEL, Look.WHO)),
        "imperativo" to Rule("hint.it.ruleImperative", "hintSay.it.ruleImperative", listOf("Guarda! · Guardate!", "Leggi! · Leggete!", "Dormi! · Dormite!", "Andiamo! · Non correre!"), null, listOf(Look.NO, Look.TO_WHOM)),
        "numeri" to Rule("hint.it.ruleNumbers", "hintSay.it.ruleNumbers", listOf("uno · due · tre · quattro", "ventuno · ventotto", "trentadue · quarantacinque", "cento · mille"), "Quanti?"),
        "ore-giorni" to Rule("hint.it.ruleTime", "hintSay.it.ruleTime", listOf("Sono le due. · È l'una.", "alle cinque · all'una · a mezzogiorno", "le tre e mezza", "il lunedì · la domenica"), "Che ore sono? A che ora?", listOf(Look.HOUR)),
        "piacere" to Rule("hint.it.ruleLike", "hintSay.it.ruleLike", listOf("Mi piace il gelato.", "Mi piacciono le fragole.", "Ti piace nuotare?", "Gli piacciono i cani."), null, listOf(Look.THING, Look.LIKER)),
        "plurale" to Rule("hint.it.rulePlural", "hintSay.it.rulePlural", listOf("il gatto · i gatti", "la rosa · le rose", "il fiore · i fiori", "lo zaino · gli zaini"), "Quanti?", listOf(Look.MANY)),
        "preposizioni-articolate" to Rule("hint.it.ruleFused", "hintSay.it.ruleFused", listOf("nel bosco · nella casa", "al mare · alla porta", "dal medico · dalla nonna", "sul tavolo · sulle sedie", "dell'orso · degli amici"), null, listOf(Look.ARTICLE)),
        "presente-regolare" to Rule("hint.it.rulePresent", "hintSay.it.rulePresent", listOf("parlo · parli · parla", "prendo · prendi · prende", "dormo · dormi · dorme", "parliamo · parlate · parlano"), "Chi?", listOf(Look.INFINITIVE, Look.WHO)),
        "tu-lei" to Rule("hint.it.ruleTuLei", "hintSay.it.ruleTuLei", listOf("Come stai? · Come sta?", "Hai fame? · Ha fame?", "Vieni anche tu? · Viene anche Lei?"), "Tu o Lei?", listOf(Look.TO_WHOM, Look.WHO)),
        "verbi-irregolari" to Rule("hint.it.ruleIrregular", "hintSay.it.ruleIrregular", listOf("vado · vai · va · andiamo", "faccio · fai · fa · facciamo", "vengo · vieni · viene", "sto · stai · sta · stiamo"), "Chi?", listOf(Look.INFINITIVE, Look.WHO)),
        // A2
        "ci-ne" to Rule("hint.it.ruleCiNe", "hintSay.it.ruleCiNe", listOf("Vai al mare? Sì, ci vado.", "Quante mele hai? Ne ho tre.", "Ce ne sono sei.", "Ne voglio un po'."), null, listOf(Look.THING)),
        "comparativi" to Rule("hint.it.ruleCompare", "hintSay.it.ruleCompare", listOf("più forte del vento", "meno freddo dell'inverno", "più grande di me", "alto come Tommaso", "migliore · meglio"), null, listOf(Look.MORE)),
        "futuro" to Rule("hint.it.ruleFuture", "hintSay.it.ruleFuture", listOf("Domani andrò al mare.", "parlerò · leggerò · dormirò", "sarò · avrò · farò", "Domani pioverà."), "Quando?", listOf(Look.FUTURE, Look.WHO)),
        "imperativo-pronomi" to Rule("hint.it.ruleImperativePronoun", "hintSay.it.ruleImperativePronoun", listOf("Guardami!", "Prendilo!", "Dammi la mano!", "Non toccarlo!"), null, listOf(Look.NO)),
        "imperfetto" to Rule("hint.it.ruleImperfect", "hintSay.it.ruleImperfect", listOf("C'era una volta un lupo.", "Da piccolo giocavo nel fiume.", "Era sera. Faceva freddo.", "dormivo · leggevi · parlava"), null, listOf(Look.HABIT, Look.PAST, Look.WHO)),
        "imperfetto-passato" to Rule("hint.it.ruleTwoPasts", "hintSay.it.ruleTwoPasts", listOf("Dormivo quando è arrivato Moro.", "Mentre leggevo, ha suonato il telefono.", "Ogni estate andavamo al mare.", "All'improvviso è caduto."), "Com'era? Che cosa è successo?", listOf(Look.HABIT, Look.ONCE)),
        "modali" to Rule("hint.it.ruleModal", "hintSay.it.ruleModal", listOf("Posso giocare?", "Devo aiutare la nonna.", "Voglio dormire.", "So nuotare."), null, listOf(Look.INFINITIVE, Look.WHO)),
        "negazioni" to Rule("hint.it.ruleNo", "hintSay.it.ruleNo", listOf("Non vedo niente.", "Non piove più.", "Non c'è nessuno.", "Moro non morde mai."), null, listOf(Look.NO)),
        "participi-irregolari" to Rule("hint.it.ruleIrregularParticiple", "hintSay.it.ruleIrregularParticiple", listOf("fare · fatto", "scrivere · scritto", "mettere · messo", "vedere · visto", "aprire · aperto"), null, listOf(Look.PARTICIPLE)),
        "partitivo" to Rule("hint.it.rulePartitive", "hintSay.it.rulePartitive", listOf("del pane · dell'acqua", "dei fichi · delle mele", "un po' di miele", "qualche fungo"), null, listOf(Look.ARTICLE, Look.MANY)),
        "passato-prossimo" to Rule("hint.it.rulePast", "hintSay.it.rulePast", listOf("Ho mangiato.", "Sono andato a casa.", "Abbiamo visto un gufo.", "Giulia è arrivata."), null, listOf(Look.REFLEXIVE, Look.AUX, Look.PARTICIPLE, Look.PAST, Look.WHO)),
        "possessivi" to Rule("hint.it.rulePossessive", "hintSay.it.rulePossessive", listOf("il mio cane · la mia casa", "i miei amici · le mie pecore", "mia madre · i miei nonni", "il suo zaino · la sua casa"), "Di chi è?", listOf(Look.AGREES)),
        "pronomi-diretti" to Rule("hint.it.ruleDirect", "hintSay.it.ruleDirect", listOf("Moro? Lo vedo.", "La mela? La prendo.", "I biscotti? Li mangio.", "Le pecore? Le conto.")),
        "pronomi-indiretti" to Rule("hint.it.ruleIndirect", "hintSay.it.ruleIndirect", listOf("Gli do la palla.", "Le porto un fiore.", "Mi dai il pane?", "Ti scrivo una lettera.")),
        "relative-che" to Rule("hint.it.ruleChe", "hintSay.it.ruleChe", listOf("il cane che abbaia", "la torta che fa la nonna", "i bambini che giocano"), null, listOf(Look.CHE)),
        "riflessivi" to Rule("hint.it.ruleReflexive", "hintSay.it.ruleReflexive", listOf("mi lavo · ti lavi · si lava", "mi alzo · mi siedo", "ci divertiamo", "Mi sono svegliato."), null, listOf(Look.REFLEXIVE, Look.WHO)),
        "stare-gerundio" to Rule("hint.it.ruleStare", "hintSay.it.ruleStare", listOf("Sto giocando.", "Che cosa stai facendo?", "Stanno dormendo.", "leggendo · dormendo"), null, listOf(Look.STARE, Look.WHO)),
        "superlativi" to Rule("hint.it.ruleSuperlative", "hintSay.it.ruleSuperlative", listOf("il più piccolo di tutti", "la più alta del paese", "altissimo · bellissima", "l'albero più vecchio del bosco"), null, listOf(Look.AGREES)),
        "vorrei" to Rule("hint.it.ruleWould", "hintSay.it.ruleWould", listOf("Vorrei un gelato, per favore.", "Vorrei giocare.", "Potrei avere dell'acqua?", "Mi piacerebbe nuotare."), null, listOf(Look.INFINITIVE)),
        // B1
        "condizionale" to Rule("hint.it.ruleConditional", "hintSay.it.ruleConditional", listOf("mangerei · prenderei · dormirei", "sarei · avrei · andrei", "Dovresti dormire.", "Al posto tuo, porterei l'ombrello."), null, listOf(Look.WHO)),
        "congiuntivo" to Rule("hint.it.ruleSubj", "hintSay.it.ruleSubj", listOf("Voglio che tu venga.", "Spero che Moro dorma.", "Penso che sia vero.", "giochi · prenda · dorma"), null, listOf(Look.SUBJ, Look.WHO)),
        "congiuntivo-espressioni" to Rule("hint.it.ruleSubjAfter", "hintSay.it.ruleSubjAfter", listOf("È importante che tu dorma.", "Torna prima che faccia buio.", "Sono contenta che tu sia qui.", "prima di mangiare"), null, listOf(Look.SUBJ)),
        "congiunzioni" to Rule("hint.it.ruleLinking", "hintSay.it.ruleLinking", listOf("perché piove · siccome piove", "quindi · allora · così", "ma · però · invece", "anche se piove")),
        "discorso-indiretto" to Rule("hint.it.ruleReported", "hintSay.it.ruleReported", listOf("Dice che è stanco.", "Mi chiede se vengo.", "Mi dice di aspettare.", "Ha detto che aveva fame."), null, listOf(Look.SAID)),
        "futuro-anteriore" to Rule("hint.it.ruleFuturePerfect", "hintSay.it.ruleFuturePerfect", listOf("Quando avrò finito, gioco.", "Appena sarà arrivata, ti chiamo.", "avremo mangiato · saranno partiti"), null, listOf(Look.AUX, Look.PARTICIPLE, Look.FIRST)),
        "gerundio" to Rule("hint.it.ruleGerund", "hintSay.it.ruleGerund", listOf("Camminando, ho visto un riccio.", "Giocando si impara.", "facendo · dicendo · bevendo")),
        "indefiniti" to Rule("hint.it.ruleIndefinite", "hintSay.it.ruleIndefinite", listOf("qualcuno · qualcosa", "ogni giorno · ogni bambino", "Ognuno porta qualcosa.", "tutti i bambini · tutta la notte"), null, listOf(Look.MANY)),
        "infinito-preposizioni" to Rule("hint.it.ruleVerbPrep", "hintSay.it.ruleVerbPrep", listOf("comincio a leggere · imparo a nuotare", "finisco di mangiare · smetti di ridere", "provo ad aprire", "ho voglia di giocare"), null, listOf(Look.VERB_PREP, Look.INFINITIVE)),
        "passato-remoto" to Rule("hint.it.ruleRemote", "hintSay.it.ruleRemote", listOf("andò · partì · credette", "fu · ebbe · disse · fece", "andarono · partirono", "C'era una volta un re."), null, listOf(Look.WHO)),
        "passivo" to Rule("hint.it.rulePassive", "hintSay.it.rulePassive", listOf("Il miele è fatto dalle api.", "La torta è stata mangiata.", "Le lettere sono state scritte.", "fatto · fatta · fatti · fatte"), null, listOf(Look.AUX, Look.AGREES)),
        "periodo-ipotetico" to Rule("hint.it.ruleIf", "hintSay.it.ruleIf", listOf("Se piove, resto a casa.", "Se fossi un uccello, volerei.", "Se hai fame, mangia!", "fossi · avessi · giocassi"), null, listOf(Look.IF)),
        "pronomi-combinati" to Rule("hint.it.ruleDoublePronoun", "hintSay.it.ruleDoublePronoun", listOf("Me lo dai?", "Te la porto.", "Glielo do.", "Non te lo dico."), null, listOf(Look.PRONOUN)),
        "relativi-cui" to Rule("hint.it.ruleCui", "hintSay.it.ruleCui", listOf("la casa in cui abito", "il bambino con cui gioco", "il libro di cui parlo", "l'amica a cui scrivo"), null, listOf(Look.CUI)),
        "si-impersonale" to Rule("hint.it.ruleSi", "hintSay.it.ruleSi", listOf("In Friuli si mangia il frico.", "Si mangiano le castagne.", "Come si dice?", "In classe non si corre."), null, listOf(Look.THING)),
        "stare-per" to Rule("hint.it.ruleAboutTo", "hintSay.it.ruleAboutTo", listOf("Sto per partire!", "Il treno sta per arrivare.", "Ha appena mangiato.", "È appena arrivata."), null, listOf(Look.ABOUT_TO, Look.INFINITIVE)),
        "trapassato" to Rule("hint.it.rulePluperfect", "hintSay.it.rulePluperfect", listOf("Moro aveva già mangiato.", "Giulia era già partita.", "avevo visto · ero uscito"), null, listOf(Look.AUX, Look.PARTICIPLE)),
        "verbi-pronominali" to Rule("hint.it.rulePronominal", "hintSay.it.rulePronominal", listOf("me ne vado · se ne va", "ce la faccio · ce la fai", "Ci vuole un'ora. · Ci vogliono due ore."), null, listOf(Look.PRONOUN, Look.FIXED, Look.THING)),
        // B2
        "alterati" to Rule("hint.it.ruleAltered", "hintSay.it.ruleAltered", listOf("casa · casetta · casona", "naso · nasone", "ragazzo · ragazzaccio", "lento · lentamente", "possibile · impossibile")),
        "concordanza-tempi" to Rule("hint.it.ruleSequence", "hintSay.it.ruleSequence", listOf("Penso che venga. · Pensavo che venisse.", "Penso che sia venuto. · Pensavo che fosse venuto."), null, listOf(Look.SUBJ)),
        "condizionale-passato" to Rule("hint.it.rulePastConditional", "hintSay.it.rulePastConditional", listOf("Avrei mangiato.", "Sarei venuto.", "Avrei dovuto dirlo.", "Ha detto che sarebbe venuta."), null, listOf(Look.SAID, Look.AUX)),
        "congiuntivo-imperfetto" to Rule("hint.it.ruleSubjImperfect", "hintSay.it.ruleSubjImperfect", listOf("Pensavo che fosse qui.", "Vorrei che venissi.", "Se avessi tempo, verrei.", "giocassi · prendessi · dormissi"), null, listOf(Look.SUBJ, Look.IF)),
        "congiuntivo-indipendente" to Rule("hint.it.ruleSubjAlone", "hintSay.it.ruleSubjAlone", listOf("Magari venisse!", "Magari fosse venuto!", "Che sia lui?", "Se solo potessi!"), null, listOf(Look.SUBJ)),
        "congiuntivo-passato" to Rule("hint.it.ruleSubjPast", "hintSay.it.ruleSubjPast", listOf("Penso che sia partito.", "Spero che abbia mangiato.", "Credo che siano arrivati."), null, listOf(Look.SUBJ, Look.AUX)),
        "congiuntivo-trapassato" to Rule("hint.it.ruleSubjPluperfect", "hintSay.it.ruleSubjPluperfect", listOf("Pensavo che fosse già partito.", "Se avessi saputo, sarei venuto.", "Credevo che avesse capito."), null, listOf(Look.SUBJ, Look.IF, Look.AUX)),
        "congiunzioni-congiuntivo" to Rule("hint.it.ruleSubjLinking", "hintSay.it.ruleSubjLinking", listOf("benché piova", "purché tu venga", "affinché capisca", "a meno che non piova", "come se fosse facile"), null, listOf(Look.SUBJ)),
        "connettivi" to Rule("hint.it.ruleConnectives", "hintSay.it.ruleConnectives", listOf("tuttavia · invece · anzi", "in effetti · inoltre", "a causa della pioggia", "Il pane l'ho comprato io.")),
        "discorso-indiretto-passato" to Rule("hint.it.ruleReportedPast", "hintSay.it.ruleReportedPast", listOf("Disse che era stanco.", "Disse che sarebbe tornato.", "Mi chiese dove fosse.", "domani · il giorno dopo"), null, listOf(Look.SAID)),
        "fare-lasciare" to Rule("hint.it.ruleFare", "hintSay.it.ruleFare", listOf("Faccio riparare la bici.", "Mi fa ridere.", "Te lo faccio vedere.", "Lascialo dormire!"), null, listOf(Look.INFINITIVE)),
        "gerundio-passato" to Rule("hint.it.ruleGerundPast", "hintSay.it.ruleGerundPast", listOf("Avendo finito, è uscito.", "Essendo arrivata tardi, ha cenato da sola.", "Pur essendo stanco, ha giocato.")),
        "infinito-passato" to Rule("hint.it.ruleInfinitivePast", "hintSay.it.ruleInfinitivePast", listOf("dopo aver mangiato", "dopo essere arrivata", "Penso di aver capito.", "Grazie per aver aiutato."), null, listOf(Look.PARTICIPLE)),
        "participio-assoluto" to Rule("hint.it.ruleParticipleAlone", "hintSay.it.ruleParticipleAlone", listOf("Finito il lavoro, siamo andati a casa.", "Finita la cena, …", "Arrivata a casa, ha dormito.", "la porta chiusa")),
        "passivo-venire-andare" to Rule("hint.it.rulePassiveMust", "hintSay.it.rulePassiveMust", listOf("La porta viene aperta.", "Il compito va fatto.", "I piatti vanno lavati.", "Si vendono i biscotti."), null, listOf(Look.PARTICIPLE, Look.THING)),
        "periodo-ipotetico-irrealta" to Rule("hint.it.ruleIfPast", "hintSay.it.ruleIfPast", listOf("Se l'avessi saputo, sarei venuto.", "Se avessi studiato, ora saprei.", "Se fosse venuta, l'avrei vista."), null, listOf(Look.IF, Look.AUX)),
        "raccontare" to Rule("hint.it.ruleStory", "hintSay.it.ruleStory", listOf("andò · è andato · va", "C'era una volta un re.", "Fondarono il paese.", "Il nonno ha costruito questa casa."), null, listOf(Look.HABIT)),
        "relativi-il-quale" to Rule("hint.it.ruleQuale", "hintSay.it.ruleQuale", listOf("il mugnaio del quale ti parlo", "la casa nella quale abito", "Chi cerca trova.", "Chiunque venga, è il benvenuto."), null, listOf(Look.CUI)),
    )

    /** The 🔊's sentence of a card's rule [key] ("hint.it.ruleTuLei"); null for one of another table. */
    fun spoken(key: String): String? = SPOKEN[key]

    private val SPOKEN: Map<String, String> = RULES.values.associate { it.text to it.said }

    /**
     * The hint of an Italian turn: its first page this table has ([TurnHint.pages], those the book has), with that page's
     * question, rule and models (all of them: the caller keeps those that show no form of the turn), and what decides the
     * form in [sentence] at its gap [slot] (the turn's right choice; null: a turn that isn't one word's form); [names]: the
     * words of the cast's names ([TurnHints.names]), a name wherever it stands. Null when no page of the turn is here.
     */
    fun of(base: TurnHint, sentence: String?, slot: Int?, names: Set<String> = emptySet()): Pair<String, TurnHint>? {
        val id = base.pages.firstOrNull { it in RULES } ?: return null
        val rule = RULES.getValue(id)
        val trigger = if (sentence != null && slot != null) triggerOf(rule.looks, sentence, slot, base, names) else null
        return id to base.copy(question = rule.question, rule = rule.text, trigger = trigger, models = rule.models)
    }

    /** What decides the form of the word at [slot] of [sentence], by the first of [looks] that finds it; null for none. */
    fun triggerOf(looks: List<Look>, sentence: String, slot: Int, h: TurnHint, names: Set<String> = emptySet()): Trigger? {
        val s = Said(sentence, names)
        if (slot !in s.words.indices) return null
        return looks.firstNotNullOfOrNull { find(it, s, slot, h) }
    }

    /** A sentence's words ([Forms.spans]), lower case, which clause each is in ([CaseTriggers.clauses]), and the cast's [names]. */
    private class Said(val text: String, val names: Set<String> = emptySet()) {
        val spans = Forms.spans(text)
        val words = spans.map { it.value.lowercase().replace('’', '\'') }
        val clause = CaseTriggers.clauses(text)

        /** The sentence's own text of the words [from] to [to] ("Le api"). */
        fun text(from: Int, to: Int): String = text.substring(spans[from].range.first, spans[to].range.last + 1)

        fun same(a: Int, b: Int): Boolean = a in words.indices && b in words.indices && clause[a] == clause[b]
    }

    // --- the word lists ---------------------------------------------------------------------------------------------

    private fun set(s: String): Set<String> = s.split(" ").filter { it.isNotEmpty() }.toSet()

    /** The definite and indefinite articles (lo, la, le, l' are pronouns too: [PURE_ARTICLES] never are). */
    private val ARTICLES = set("il lo la i gli le un uno una")
    private val PURE_ARTICLES = set("il i un uno una")

    /** A preposition fused with the article (del, alla, nei …): always before a noun. */
    private val FUSED = set(
        "del dello della dei degli delle al allo alla ai agli alle dal dallo dalla dai dagli dalle nel nello nella nei negli " +
            "nelle sul sullo sulla sui sugli sulle col coi",
    )

    /** A token that is an elided article or fused preposition with its noun: l'acqua, un'arnia, dell'orso, all'una. */
    private val ELIDED = Regex("""^(l|un|dell|all|dall|nell|sull|quell|quest|bell|sant)'\p{L}""")
    private val ELIDED_ARTICLE = Regex("""^(l|un|quell|quest)'\p{L}""")

    private val DEMONSTRATIVES = set("questo questa questi queste quello quella quelli quelle")
    private val POSSESSIVES = set(
        "mio mia miei mie tuo tua tuoi tue suo sua suoi sue nostro nostra nostri nostre vostro vostra vostri vostre",
    )
    private val NUMBERS = set("due tre quattro cinque sette otto nove dieci undici dodici venti trenta cento mille")
    private val PLURAL_DETERMINERS = set(
        "i gli le questi queste quei quegli quelle molti molte tanti tante pochi poche alcuni alcune quanti quante parecchi " +
            "parecchie diversi diverse miei mie tuoi tue suoi sue nostri nostre vostri vostre dei degli delle ai agli alle dai " +
            "dagli dalle nei negli nelle sui sugli sulle",
    )
    private val EACH = set("ogni qualche")

    /** What stands before a noun: an article, a demonstrative, a possessive, a number, a word of quantity. */
    private val DETERMINERS = ARTICLES + DEMONSTRATIVES + set("quel quei quegli") + POSSESSIVES + NUMBERS +
        set("ogni qualche nessun nessuna alcuni alcune molti molte tanti tante pochi poche quanti quante")

    /** The adjectives that often stand between the article and the noun: il grande fienile, una vecchia pompa. */
    private val BEFORE_NOUN = set(
        "grande gran grandi piccolo piccola piccoli piccole bel bello bella bei begli belle buon buono buona buoni buone " +
            "vecchio vecchia vecchi vecchie nuovo nuova nuovi nuove primo prima primi prime ultimo ultima altro altra altri " +
            "altre lungo lunga alto alta povero povera caro cara vero vera stesso stessa",
    )

    /** Words that are never a noun nor a name: what a word list can't take for the noun a form agrees with. */
    private val FUNCTION = ARTICLES + FUSED + set(
        "e ed o od ma però che chi cui di a ad da in su con per tra fra non si ci ne mi ti vi me te ce ve se è sono sei " +
            "siamo siete ho hai ha abbiamo avete hanno più meno molto tanto così come quando dove perché anche già mai poi " +
            "ora adesso qui qua lì là sì no ecco tutto tutti tutte",
    )

    private val PREPOSITIONS = FUSED + set(
        "a ad di da in su con per tra fra senza sotto sopra dietro davanti vicino verso dopo prima contro fino presso oltre " +
            "dentro fuori tranne",
    )

    /** The personal pronouns that are the subject (not after a preposition: con noi, per lei), and those of no one in particular. */
    private val SUBJECTS = set("io tu lui lei noi voi loro egli ella essi esse nessuno qualcuno ognuno")

    /** The short words that stand between a subject and its verb: l'agnellino non beve, le api ci portano. */
    private val CLITICS = set("non mi ti si ci vi lo la li le gli ne me te ce ve se")

    /** The forms of essere and avere that build a compound tense (and venire, andare: the passive), by verb. */
    private val AVERE = set(
        "ho hai ha abbiamo avete hanno avevo avevi aveva avevamo avevate avevano avrò avrai avrà avremo avrete avranno avrei " +
            "avresti avrebbe avremmo avreste avrebbero abbia abbiamo abbiate abbiano avessi avesse avessimo aveste avessero " +
            "aver avere avendo ebbi ebbe",
    )
    private val ESSERE = set(
        "sono sei è siamo siete ero eri era eravamo eravate erano sarò sarai sarà saremo sarete saranno sarei saresti sarebbe " +
            "saremmo sareste sarebbero sia siate siano fossi fosse fossimo foste fossero essere esser essendo fui fu",
    )
    private val PASSIVE = set("viene vengono veniva venivano verrà verranno va vanno andava andavano andrà andranno")
    private val AUXILIARIES = AVERE + ESSERE + PASSIVE

    /** What may stand between the helper verb and its participle: ho già mangiato, è appena arrivata, sono tutti partiti. */
    private val BETWEEN = set("già mai appena ancora sempre anche tutti tutte tutto subito bene male poi più proprio davvero stato stata stati state")

    /** The copula and its like, after which an adjective agrees with the subject. */
    private val COPULAS = ESSERE + set("stato stata stati state sembra sembrano sembrava diventa diventano diventato diventata resta restano rimane rimangono")
    private val COPULA_ADVERBS = set("più meno molto tanto così troppo proprio davvero già sempre anche ancora non ben piuttosto abbastanza ora adesso tutto tutta tutti tutte")

    /** The participles of their own (the -ato, -uto, -ito ones are found by their ending): in the -o form. */
    private val PARTICIPLES = set(
        "fatto detto letto scritto rotto preso messo chiuso perso acceso sceso visto chiesto risposto rimasto aperto coperto " +
            "offerto vinto spento dipinto bevuto venuto nato stato morto scelto tolto sparso corso mosso cotto successo vissuto " +
            "raccolto sciolto accolto giunto pianto spinto stretto deciso diviso ucciso riso compreso sorpreso speso appeso " +
            "steso tratto distrutto costretto risolto",
    )
    private val REGULAR_PARTICIPLE = Regex("""\p{L}{2,}(at|ut|it)[oaie]$""")

    /** The words that end like a participle and aren't one, as the content has them. */
    private val NOT_PARTICIPLES = set(
        "gelato prato lato fiato mercato soldato dito mito sito lite vite viti partite minuto minuti aiuto saluto saluti " +
            "estate patate insalate frittate fate date matite salite salute tributo",
    )

    private fun participle(w: String): Boolean {
        val bare = w.substringBefore('\'')
        if (bare in PARTICIPLES || (bare.length > 3 && bare.dropLast(1) + "o" in PARTICIPLES && bare.last() in "oaie")) return true
        return REGULAR_PARTICIPLE.matches(bare) && bare !in NOT_PARTICIPLES
    }

    private fun infinitive(w: String): Boolean = w != "mare" && Regex("""\p{L}+(are|ere|ire|rre)(lo|la|li|le|ne|ci|vi|mi|ti|si|gli|glielo|gliela|sene)?$""").matches(w) ||
        Regex("""\p{L}{2,}(ar|er|ir)(si|mi|ti|ci|vi|lo|la|li|le|ne|gli)$""").matches(w)

    private fun gerund(w: String): Boolean = Regex("""\p{L}{2,}(ando|endo)(lo|la|li|le|ne|ci|vi|mi|ti|si|gli)?$""").matches(w)

    /** Modal verbs, vorrei and its like, fare and lasciare (someone else does it), sapere (know how): before an infinitive. */
    private val BEFORE_INFINITIVE = set(
        "posso puoi può possiamo potete possono potevo poteva potrei potresti potrebbe potremmo potreste potrebbero devo devi " +
            "deve dobbiamo dovete devono dovevo doveva dovrei dovresti dovrebbe dovremmo dovreste dovrebbero voglio vuoi vuole " +
            "vogliamo volete vogliono volevo voleva vorrei vorresti vorrebbe vorremmo vorreste vorrebbero so sai sa sappiamo " +
            "sapete sanno faccio fai fa facciamo fate fanno facevo faceva fatto farò lascio lasci lascia lasciamo lasciate " +
            "lasciano lascialo lasciala lasciali lasciale piacerebbe per",
    )
    private val STARE = set("sto stai sta stiamo state stanno stavo stavi stava stavamo stavate stavano starò starà stia stiano")
    private val VERB_PREPS = set("a ad di da per")

    /** What a feeling or an age is in Italian: avere and a noun (ho fame, ha sei anni). */
    private val FEELINGS = set("fame sete sonno paura freddo caldo fretta ragione torto bisogno voglia anni mal male")

    /** A word that asks for the congiuntivo after it, with che (spero che, bisogna che, è importante che). */
    private val WANTS = set(
        "spero speri spera speriamo sperate sperano speravo sperava penso pensi pensa pensiamo pensate pensano pensavo " +
            "pensava credo credi crede crediamo credete credono credevo credeva voglio vuoi vuole vogliamo volete vogliono " +
            "volevo voleva vorrei vorresti vorrebbe vorremmo vorreste vorrebbero bisogna bisognava necessario importante meglio " +
            "possibile impossibile probabile giusto strano peccato sembra sembrava pare pareva dispiace dispiaceva contento " +
            "contenta contenti contente felice felici paura temo dubito immagino prima senza basta occorre",
    )

    /** A word that asks for the congiuntivo by itself: although, as long as, so that, unless, whoever, if only, as if. */
    private val SUBJ_WORDS = set("benché sebbene nonostante purché affinché magari qualunque chiunque dovunque ovunque")

    /** The verbs of saying and asking, past and present, before what is told. */
    private val SAID_PAST = set("detto chiesto risposto spiegato raccontato scritto promesso disse dissero chiese risposte rispose diceva dicevano spiegò raccontò promise")
    private val SAID_NOW = set("dice dicono dici dico chiede chiedono chiedi risponde spiega racconta scrive")

    /** A word of time: the past, the future, again and again or going on (the imperfetto), once (the passato prossimo). */
    private val PAST = set("ieri altroieri scorso scorsa scorsi scorse")
    private val TIME_NOUNS = set("giorno giorni anno anni mese mesi settimana settimane ora ore minuti minuto tempo poco secoli secolo")
    private val FUTURE = set("domani dopodomani prossimo prossima prossimi prossime")
    private val CHILDHOOD = set("bambino bambina bambini bambine piccolo piccola piccoli piccole giovane giovani ragazzo ragazza ragazzi")
    private val EVERY_TIME = set("giorno mattina sera notte estate inverno anno settimana domenica sabato volta mese primavera autunno")
    private val ONCE_START = set("giorno mattina sera notte volta")

    /** Who is spoken to: the family's, a title, a group (", maestra!", "Zia, …", ", pecore!"). */
    private val ADDRESSED = set(
        "maestra maestro nonno nonna nonni zia zio zii mamma papà signora signore signor signorina signori dottore " +
            "dottoressa professore professoressa sindaco bambini bambine ragazzi ragazze amici amiche bimbi bimbe piccolo piccola " +
            "piccoli tesoro cara caro cari care pecore pecora galline gallina mucche mucca api caprette capretta cani cane gatti " +
            "gatto asino cavallo pulcini uccellini pompieri vicini signori",
    )

    /** The polite pronouns written with a capital mid-sentence: not a name. */
    private val POLITE = set("lei la le loro suo sua suoi sue vi")

    // --- what decides the form --------------------------------------------------------------------------------------

    private fun asks(s: Said, from: Int, to: Int, line: String, said: String) = Trigger.Asks(s.text(from, to), to, line, said)

    private fun find(look: Look, s: Said, i: Int, h: TurnHint): Trigger? {
        val w = s.words
        val gap = w[i]
        val forms = h.forms.map { it.replace('’', '\'') }.toSet()
        return when (look) {
            Look.WHO -> who(s, i)
            Look.PAST -> time(s, i, sentence = true) { k -> w[k] in PAST || (w[k] == "fa" && k > 0 && w[k - 1] in TIME_NOUNS) }
                ?.let { Trigger.Cue(s.spans[it].value, it, Trigger.Cue.Kind.PAST) }
            Look.FUTURE -> time(s, i, sentence = true) { k -> w[k] in FUTURE }?.let { Trigger.Cue(s.spans[it].value, it, Trigger.Cue.Kind.FUTURE) }
            // mentre, di solito, da piccolo, ogni estate, sempre: again and again, or going on (the imperfetto)
            Look.HABIT -> time(s, i, sentence = false) { k ->
                w[k] == "mentre" || w[k] == "sempre" || (w[k] == "solito" && k > 0 && w[k - 1] == "di") ||
                    (w[k] in CHILDHOOD && k > 0 && w[k - 1] == "da") || (w[k] in EVERY_TIME && k > 0 && w[k - 1] == "ogni")
            }?.let { k -> asks(s, if (w[k] == "mentre" || w[k] == "sempre") k else k - 1, k, "hint.it.habit", "hintSay.it.habit") }
            // all'improvviso, un giorno, tre volte: once, at one moment (the passato prossimo)
            Look.ONCE -> time(s, i, sentence = false) { k ->
                w[k] == "all'improvviso" || w[k] == "improvvisamente" || (w[k] == "volte" && k > 0 && w[k - 1] in NUMBERS) ||
                    (w[k] in ONCE_START && k > 0 && w[k - 1] in set("un una") && !(k > 1 && w[k - 2] == "c'era"))
            }?.let { k -> asks(s, if (w[k] in ONCE_START || w[k] == "volte") k - 1 else k, k, "hint.it.once", "hintSay.it.once") }
            Look.ARTICLE -> article(s, i, forms)
            Look.AGREES -> agrees(s, i, gap, forms)
            Look.MANY -> many(s, i, gap, forms)
            Look.AUX -> aux(s, i, gap, forms)
            Look.PARTICIPLE -> afterAux(s, i, gap)
            Look.REFLEXIVE -> reflexive(s, i, gap)
            Look.SUBJ -> subjunctive(s, i)
            Look.IF -> ifClause(s, i)
            Look.CUI -> cui(s, i, gap, forms)
            Look.CHE -> che(s, i, gap)
            Look.TO_WHOM -> addressed(s, i)?.let { (a, b) -> asks(s, a, b, "hint.it.toWhom", "hintSay.it.toWhom") }
            // an adjective about the one spoken to: "Sei buffo, Tommaso!" (a verb of you in its half, or no noun there,
            // the one spoken to in the half next to it), not "Che belle! Ma attento, Tommaso!"
            Look.YOU_AGREE -> addressed(s, i)?.takeIf { (a, _) -> formGap(gap, forms) && aboutYou(s, i, a) }?.let { (a, b) -> asks(s, a, b, "hint.it.toWhom", "hintSay.it.toWhom") }
            Look.NO -> (i - 1 downTo 0).firstOrNull { k -> s.same(k, i) && w[k] == "non" }?.let { Trigger.Negation(s.spans[it].value, it) }
            Look.INFINITIVE -> beforeInfinitive(s, i, gap)
            Look.VERB_PREP -> verbPrep(s, i, gap, forms)
            Look.STARE -> before(s, i)?.takeIf { w[it] in STARE && gerund(gap) }?.let { asks(s, it, it, "hint.it.stare", "hintSay.after") }
            Look.ABOUT_TO -> before(s, i)?.takeIf { w[it] in STARE && gap in VERB_PREPS && forms.any { f -> f != gap && f in VERB_PREPS } && s.same(i, i + 1) && infinitive(w[i + 1]) }
                ?.let { asks(s, it, it, "hint.it.aboutTo", "hintSay.with") }
            Look.THING -> thing(s, i, gap, forms)
            Look.LIKER -> (i - 1).takeIf { it >= 0 && s.same(it, i) && w[it] in set("mi ti gli le ci vi") && gap.startsWith("piac") }?.let { asks(s, it, it, "hint.it.liker", "hintSay.with") }
            Look.MORE -> more(s, i, gap)
            Look.PRONOUN -> pronounBefore(s, i, gap)
            Look.FIXED -> fixed(s, i, gap)
            Look.SAID -> said(s, i)
            Look.HOUR -> hour(s, i, gap)
            Look.PLACE -> place(s, i, gap, forms)
            Look.FEEL -> feel(s, i, gap, forms)
            Look.FIRST -> first(s, i, gap)
        }
    }

    /** The word before the word at [i], past the short words (non, mi, lo …), in its clause; null at its start. */
    private fun before(s: Said, i: Int, skip: Set<String> = CLITICS): Int? {
        var k = i - 1
        while (k >= 0 && s.same(k, i) && s.words[k] in skip) k--
        return k.takeIf { it >= 0 && s.same(it, i) }
    }

    /** The first word of [s] (of [i]'s clause, unless [sentence]) that [hit] finds, the nearest to [i] first. */
    private fun time(s: Said, i: Int, sentence: Boolean, hit: (Int) -> Boolean): Int? =
        s.words.indices.filter { it != i && (sentence || s.same(it, i)) }.sortedBy { kotlin.math.abs(it - i) }.firstOrNull(hit)

    /** A form of the turn's [gap] word: another form of it shares its start (pieno, piena; lunghe, lunghi; mio, mia). */
    private fun related(a: String, b: String): Boolean {
        if (a == b) return false
        val p = a.commonPrefixWith(b).length
        return p >= 2 && (p >= 3 || maxOf(a.length, b.length) <= 5) && a.length - p <= 5 && b.length - p <= 5
    }

    private fun formGap(gap: String, forms: Set<String>): Boolean = forms.any { related(gap, it) }

    /**
     * A noun phrase ending at [k] (the noun): with its article or determiner before it (one adjective between: il grande
     * fienile), or a token of article and noun (l'acqua). Its first word's index, or null when [k] isn't one.
     */
    private fun nounEndingAt(s: Said, k: Int, determiners: Set<String> = DETERMINERS + FUSED): Int? {
        val w = s.words
        if (k !in w.indices || w[k] in FUNCTION) return null
        if (ELIDED.containsMatchIn(w[k])) return k
        val d = k - 1
        if (d >= 1 && s.same(d - 1, k) && w[d] in POSSESSIVES && w[d - 1] in ARTICLES + FUSED) return d - 1
        if (d >= 0 && s.same(d, k) && w[d] in determiners) return d
        if (d >= 1 && s.same(d - 1, k) && w[d] in BEFORE_NOUN && w[d - 1] in determiners) return d - 1
        return null
    }

    /** A noun phrase starting at [k] (its determiner: le api, una corda lunga; or l'acqua), to its noun; null when none. */
    private fun nounStartingAt(s: Said, k: Int, determiners: Set<String> = DETERMINERS + FUSED): Int? {
        val w = s.words
        if (k !in w.indices) return null
        if (ELIDED.containsMatchIn(w[k])) return k
        if (w[k] !in determiners) return null
        var n = k + 1
        repeat(2) { if (n + 1 < w.size && s.same(n + 1, k) && w[n] in BEFORE_NOUN + POSSESSIVES && w[n + 1] !in FUNCTION) n++ }
        return n.takeIf { it < w.size && s.same(it, k) && w[it] !in FUNCTION }
    }

    /**
     * A name: one of the cast's ([Said.names]) wherever it stands (Giulia è stanca.), or a word written with a capital in
     * the middle of a sentence (Italian writes no common noun so); not Lei.
     */
    private fun name(s: Said, k: Int): Boolean {
        val v = s.spans[k].value
        if (s.words[k] in POLITE) return false
        if (v in s.names) return true
        if (k == 0 || !v.first().isUpperCase()) return false
        val gapBefore = s.text.substring(s.spans[k - 1].range.last + 1, s.spans[k].range.first)
        return gapBefore.none { it in ".!?…«\"" }
    }

    /**
     * Who does it: a pronoun of the subject in the clause (io, noi, anch'io; not after a preposition or an article: con noi,
     * il loro), else the noun phrase right before the verb (le api abiteranno, l'acqua vola, il grande fienile ha, l'agnellino
     * non beve) or a name there. Not when the word is right after a determiner (it is a noun then).
     */
    private fun who(s: Said, i: Int): Trigger.Cue? {
        val w = s.words
        if (i > 0 && s.same(i - 1, i) && w[i - 1] in DETERMINERS + FUSED + PREPOSITIONS) return null
        // after a modal the gap is its infinitive: who does it says nothing of its form (posso provare anch'io)
        if (before(s, i)?.let { w[it] in BEFORE_INFINITIVE } == true) return null
        val near = w.indices.filter { it != i && s.same(it, i) }.sortedBy { kotlin.math.abs(it - i) }
        for (k in near) {
            val subject = w[k] in SUBJECTS || w[k] == "anch'io"
            val governed = k > 0 && s.same(k - 1, k) && (w[k - 1] in PREPOSITIONS || w[k - 1] in ARTICLES)
            if (subject && !governed) return Trigger.Cue(s.spans[k].value, k, Trigger.Cue.Kind.WHO)
        }
        val k = before(s, i) ?: return null
        if (name(s, k)) return Trigger.Cue(s.spans[k].value, k, Trigger.Cue.Kind.WHO)
        val from = nounEndingAt(s, k, ARTICLES + DEMONSTRATIVES + set("quel quei quegli") + POSSESSIVES + NUMBERS) ?: return null
        if (from == k && !ELIDED_ARTICLE.containsMatchIn(w[k])) return null
        return Trigger.Cue(s.text(from, k), k, Trigger.Cue.Kind.WHO)
    }

    /**
     * An article or a fused preposition at the gap (il, uno, alla, dei: one of the turn's forms is one that is never a
     * pronoun), and the word after it: its gender, its number and its first sound (lo scivolo, l'acqua, il grande albero).
     */
    private fun article(s: Said, i: Int, forms: Set<String>): Trigger? {
        val w = s.words
        if (forms.none { it in PURE_ARTICLES || it in FUSED }) return null
        if (forms.any { it !in ARTICLES && it !in FUSED && !ELIDED.containsMatchIn(it) && it != "a" && it != "di" && it != "in" && it != "da" && it != "su" }) return null
        val n = i + 1
        if (n >= w.size || !s.same(n, i) || w[n] in FUNCTION) return null
        val end = if (w[n] in BEFORE_NOUN && n + 1 < w.size && s.same(n + 1, i) && w[n + 1] !in FUNCTION) n + 1 else n
        return asks(s, n, end, "hint.it.agreesSound", "hintSay.with")
    }

    /**
     * An adjective, a participle, a possessive at the gap (a form of it is among the turn's: pieno, piena) and the noun it
     * agrees with: the one after it (una vecchia pompa, che bel castello, quante pecore), the one before it (una finestra
     * rotonda), or the subject of essere before it (il cesto è pieno, la corteccia è stata tolta, questa è gialla).
     */
    private fun agrees(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val w = s.words
        if (!formGap(gap, forms) || gap in AUXILIARIES && forms.all { it in AUXILIARIES }) return null
        val prev = (i - 1).takeIf { it >= 0 && s.same(it, i) }
        val opens = prev == null || w[prev] in DETERMINERS + FUSED + set("che quanto quanta quanti quante")
        if (opens && s.same(i + 1, i) && w[i + 1] !in FUNCTION && w[i + 1] !in COPULAS && !participle(w[i + 1])) {
            val end = if (w[i + 1] in BEFORE_NOUN && s.same(i + 2, i) && w[i + 2] !in FUNCTION) i + 2 else i + 1
            return asks(s, i + 1, end, "hint.it.agrees", "hintSay.with")
        }
        if (prev != null) nounEndingAt(s, prev)?.let { from -> return asks(s, from, prev, "hint.it.agrees", "hintSay.with") }
        // the subject of essere: walk back over the adverbs (più, molto, già …) to the copula, then over the short words
        var k = i - 1
        while (k >= 0 && s.same(k, i) && w[k] in COPULA_ADVERBS) k--
        if (k < 0 || !s.same(k, i) || w[k] !in COPULAS) return null
        while (k >= 0 && s.same(k, i) && w[k] in COPULAS) k--
        val subject = before(s, k + 1) ?: return null
        if (w[subject] in DEMONSTRATIVES || w[subject] in set("lui lei loro")) return asks(s, subject, subject, "hint.it.agrees", "hintSay.with")
        if (name(s, subject)) return asks(s, subject, subject, "hint.it.agrees", "hintSay.with")
        val from = nounEndingAt(s, subject) ?: return null
        if (from == subject && !ELIDED_ARTICLE.containsMatchIn(w[subject])) return null
        return asks(s, from, subject, "hint.it.agrees", "hintSay.with")
    }

    /**
     * A noun at the gap (pietre, pietra) after a number or a word of the plural (tre, gli, molti): more than one; after ogni,
     * qualche: the singular all the same; after un, una: one.
     */
    private fun many(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (!formGap(gap, forms)) return null
        val w = s.words
        var k = i - 1
        if (k >= 0 && s.same(k, i) && w[k] in BEFORE_NOUN) k--
        if (k < 0 || !s.same(k, i)) return null
        return when {
            // (sei is six here: a noun's forms at the gap, not essere's)
            w[k] in NUMBERS || w[k] == "sei" || w[k] in PLURAL_DETERMINERS || Regex("""\d+""").matches(w[k]) && w[k] != "1" -> asks(s, k, k, "hint.it.many", "hintSay.after")
            w[k] in EACH -> asks(s, k, k, "hint.it.each", "hintSay.after")
            w[k] in set("un uno una") -> asks(s, k, k, "hint.it.one", "hintSay.after")
            else -> null
        }
    }

    /** The helper verb at the gap, essere against avere among the turn's forms (hai, sei), and the participle after it: its verb decides. */
    private fun aux(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in AVERE && gap !in ESSERE) return null
        if (forms.none { it in AVERE } || forms.none { it in ESSERE }) return null
        val w = s.words
        var n = i + 1
        while (n < w.size && s.same(n, i) && w[n] in BETWEEN - set("stato stata stati state")) n++
        if (n >= w.size || !s.same(n, i) || !participle(w[n])) return null
        return asks(s, n, n, "hint.it.byParticiple", "hintSay.with")
    }

    /** A participle at the gap after its helper verb (hanno tolto, avremo seminato, è stata, va fatto): the participle. */
    private fun afterAux(s: Said, i: Int, gap: String): Trigger? {
        if (!participle(gap)) return null
        val k = before(s, i, BETWEEN + set("non")) ?: return null
        return if (s.words[k] in AUXILIARIES || s.words[k] in set("aver esser")) asks(s, k, k, "hint.it.afterAux", "hintSay.after") else null
    }

    /** essere against avere at the gap after a reflexive pronoun (mi sono bagnato): a reflexive verb takes essere. */
    private fun reflexive(s: Said, i: Int, gap: String): Trigger? {
        if (gap !in AVERE && gap !in ESSERE) return null
        val k = i - 1
        if (k < 0 || !s.same(k, i) || s.words[k] !in set("mi ti si ci vi")) return null
        return asks(s, k, k, "hint.it.reflexive", "hintSay.with")
    }

    /**
     * A word before the gap in its clause that asks for the congiuntivo: one of [WANTS] with che (spero che, bisogna che),
     * prima che, senza che, a meno che, come se, or one of [SUBJ_WORDS] alone (sebbene, magari). The gap is a verb: not
     * right after a determiner or a preposition.
     */
    private fun subjunctive(s: Said, i: Int): Trigger? {
        val w = s.words
        if (i > 0 && s.same(i - 1, i) && (w[i - 1] in DETERMINERS + FUSED + PREPOSITIONS)) return null
        for (k in i - 1 downTo 0) {
            if (!s.same(k, i)) break
            if (w[k] in SUBJ_WORDS) return asks(s, k, k, "hint.it.subj", "hintSay.after")
            if (w[k] == "se" && k > 0 && w[k - 1] == "come") return asks(s, k - 1, k, "hint.it.subj", "hintSay.after")
            if (w[k] == "che") {
                val g = (k - 1 downTo maxOf(0, k - 3)).firstOrNull { it >= 0 && s.same(it, k) && w[it] in WANTS } ?: continue
                val from = if (w[g] == "meno" && g > 0 && w[g - 1] == "a") g - 1 else g
                return asks(s, from, k, "hint.it.subj", "hintSay.after")
            }
        }
        return null
    }

    /** «se» in the gap's half (the if-half), else in the other half of the sentence (what would follow); not anche se. */
    private fun ifClause(s: Said, i: Int): Trigger? {
        val w = s.words
        val ifs = w.indices.filter { w[it] == "se" && !(it > 0 && w[it - 1] in set("anche come")) && it != i }
        ifs.firstOrNull { s.same(it, i) && it < i }?.let { return asks(s, it, it, "hint.it.ifHalf", "hintSay.after") }
        return ifs.firstOrNull { !s.same(it, i) }?.let { asks(s, it, it, "hint.it.ifOther", "hintSay.it.inOther") }
    }

    /** cui (or il quale) at the gap after a preposition (in cui, di cui, dal quale): the preposition. */
    private fun cui(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val k = i - 1
        if (k < 0 || !s.same(k, i)) return null
        val w = s.words
        if ((gap == "cui" || "cui" in forms) && w[k] in PREPOSITIONS) return asks(s, k, k, "hint.it.afterPrep", "hintSay.after")
        // il quale agrees with the noun it stands for: la casa nella quale, il mugnaio del quale
        if (gap in set("quale quali") && formGap(gap, forms) && w[k] in FUSED + ARTICLES) {
            val noun = before(s, k) ?: return null
            val from = nounEndingAt(s, noun) ?: return null
            return asks(s, from, noun, "hint.it.agrees", "hintSay.with")
        }
        return null
    }

    /** che at the gap (against chi, cui) after the noun it stands for: gli ingranaggi che. */
    private fun che(s: Said, i: Int, gap: String): Trigger? {
        if (gap != "che") return null
        val k = i - 1
        if (k < 0 || !s.same(k, i)) return null
        val from = nounEndingAt(s, k) ?: return if (name(s, k)) asks(s, k, k, "hint.it.afterNoun", "hintSay.after") else null
        return asks(s, from, k, "hint.it.afterNoun", "hintSay.after")
    }

    /**
     * Who is spoken to: a clause of one word that is someone (maestra, nonno, zia, pecore: [ADDRESSED]) or a name (Tommaso,
     * written with a capital mid-sentence), or of a title and a name (signor Franco, zia Nives), set off by a comma: after
     * one, ending its sentence (", maestra!", "Buongiorno, signora Marisa! Cosa compra?"), or the text's first, before one
     * ("Zia, …"). Not the gap's own clause. Its first and last word.
     */
    private fun addressed(s: Said, i: Int): Pair<Int, Int>? {
        val w = s.words
        if (w.size < 2) return null
        fun between(a: Int, b: Int) = s.text.substring(s.spans[a].range.last + 1, s.spans[b].range.first)
        fun who(ks: List<Int>): Pair<Int, Int>? = when {
            i in ks -> null
            ks.size == 1 -> ks[0].takeIf { w[it] in ADDRESSED || name(s, it) }?.let { it to it }
            ks.size == 2 && w[ks[0]] in set("signor signora signorina nonno nonna zia zio maestra maestro dottor dottore") &&
                s.spans[ks[1]].value.first().isUpperCase() -> ks[0] to ks[1]
            else -> null
        }
        for (c in s.clause.distinct()) {
            val ks = w.indices.filter { s.clause[it] == c }
            val after = if (ks.last() + 1 < w.size) between(ks.last(), ks.last() + 1) else s.text.substring(s.spans[ks.last()].range.last + 1)
            val before = if (ks.first() > 0) between(ks.first() - 1, ks.first()) else ""
            // (between two words only punctuation: "un po', Aldo?")
            val end = after.trim().trimStart('\'').let { it.isEmpty() || it.first() in ".!?…" }
            val vocative = (',' in before && end) || (ks.first() == 0 && ',' in after)
            if (vocative) who(ks)?.let { return it }
        }
        return null
    }

    private val YOU_ARE = set("sei siete stai state eri eravate sarai sarete")

    /**
     * Whether the gap's half is about the one spoken to at [at]: next to it, and with a verb of you (sei buffo) or nothing
     * but the adjective, an exclamation's che or an adverb (Brava!, Che bravo!); not "Sono buonissime." nor "È giallo."
     */
    private fun aboutYou(s: Said, i: Int, at: Int): Boolean {
        val c = s.clause[i]
        if (kotlin.math.abs(s.clause[at] - c) != 1) return false
        val half = s.words.indices.filter { s.clause[it] == c && it != i }
        return half.any { s.words[it] in YOU_ARE } || half.all { s.words[it] in set("che come proprio molto tanto troppo così davvero") }
    }

    /** An infinitive at the gap after a modal, vorrei, fare, lasciare, sapere or per (posso giocare, faccio riparare). */
    private fun beforeInfinitive(s: Said, i: Int, gap: String): Trigger? {
        if (!infinitive(gap)) return null
        val k = before(s, i) ?: return null
        val w = s.words
        if (w[k] in BEFORE_INFINITIVE || (w[k] in set("a ad di") && k > 0 && s.same(k - 1, k))) return asks(s, k, k, "hint.it.infinitive", "hintSay.after")
        return null
    }

    /** a against di at the gap before an infinitive (cominciamo a scavare): the verb before decides. */
    private fun verbPrep(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val w = s.words
        if (gap !in VERB_PREPS || forms.none { it != gap && it in VERB_PREPS }) return null
        if (!s.same(i + 1, i) || !infinitive(w[i + 1])) return null
        val k = (i - 1).takeIf { it >= 0 && s.same(it, i) } ?: return null
        if (w[k] in STARE) return null
        return asks(s, k, k, "hint.it.verbPrep", "hintSay.with")
    }

    /**
     * A verb at the gap (piace, piacciono; si tolgono; ci vogliono) and the thing it goes with: the noun phrase after it in
     * the clause (la cioccolata, i sassi, mille scandole), else the one right before it: one or more decides the verb.
     */
    private fun thing(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val w = s.words
        val clitic = i > 0 && s.same(i - 1, i) && w[i - 1] in set("si ci mi ti gli le vi ne")
        if (!formGap(gap, forms) && !clitic) return null
        if (i > 0 && s.same(i - 1, i) && w[i - 1] in DETERMINERS + FUSED) return null
        var n = i + 1
        while (n < w.size && s.same(n, i) && w[n] in set("anche già ancora sempre tutti tutte molto tanto")) n++
        nounStartingAt(s, n)?.let { end -> return asks(s, n, end, "hint.it.thing", "hintSay.with") }
        val k = before(s, i) ?: return null
        val from = nounEndingAt(s, k) ?: return null
        if (from == k && !ELIDED_ARTICLE.containsMatchIn(w[k])) return null
        return asks(s, from, k, "hint.it.thing", "hintSay.with")
    }

    /** di, che, come or quanto at the gap after più or meno in its clause (più alto di quello): comparing. */
    private fun more(s: Said, i: Int, gap: String): Trigger? {
        val w = s.words
        if (gap !in set("di che come quanto") && gap !in FUSED.filter { it.startsWith("de") } && !gap.startsWith("dell'")) return null
        val k = (i - 1 downTo 0).firstOrNull { s.same(it, i) && w[it] in set("più meno") } ?: return null
        return asks(s, k, k, "hint.it.more", "hintSay.after")
    }

    /** mi, me, ti, te … at the gap before lo, la, li, le, ne (te la passo, me ne dai): the short word after it. */
    private fun pronounBefore(s: Said, i: Int, gap: String): Trigger? {
        if (gap !in set("mi me ti te ci ce vi ve si se")) return null
        val n = i + 1
        if (n >= s.words.size || !s.same(n, i) || s.words[n] !in set("lo la li le ne")) return null
        return asks(s, n, n, "hint.it.pronoun", "hintSay.it.before")
    }

    /** la against lo after ce (ce la facciamo): farcela keeps its short words. */
    private fun fixed(s: Said, i: Int, gap: String): Trigger? {
        if (gap != "la" || i == 0 || s.words[i - 1] != "ce") return null
        val n = i + 1
        if (n >= s.words.size || !s.same(n, i)) return null
        return asks(s, n, n, "hint.it.fixed", "hintSay.with")
    }

    /** What is told: a verb of saying before the gap in its clause, with che, se or di (ha detto che, dice che). */
    private fun said(s: Said, i: Int): Trigger? {
        val w = s.words
        for (k in i - 1 downTo 0) {
            if (!s.same(k, i)) break
            if (w[k] !in set("che se di")) continue
            val v = k - 1
            if (v < 0 || !s.same(v, k)) continue
            val from = if (v > 0 && w[v - 1] in AVERE) v - 1 else v
            if (w[v] in SAID_PAST) return asks(s, from, k, "hint.it.saidPast", "hintSay.after")
            if (w[v] in SAID_NOW) return asks(s, v, k, "hint.it.saidNow", "hintSay.after")
        }
        return null
    }

    /** At the gap before an hour (alle otto, all'una, è l'una): the hour. */
    private fun hour(s: Said, i: Int, gap: String): Trigger? {
        val w = s.words
        if (gap !in set("alle a all' è sono le la") && !gap.startsWith("all'")) return null
        var n = i + 1
        if (n < w.size && s.same(n, i) && w[n] in set("le la")) n++
        if (n >= w.size || !s.same(n, i)) return null
        val hourWord = w[n] in NUMBERS || w[n] in set("una l'una mezzogiorno mezzanotte sei") || Regex("""\d{1,2}""").matches(w[n])
        return if (hourWord) asks(s, n, n, "hint.it.hour", "hintSay.with") else null
    }

    /** A preposition at the gap (a, in, da against each other) and the place after it: a town, a country or a room, a person. */
    private fun place(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in set("a in da su") || forms.none { it != gap && it in set("a in da su") }) return null
        val w = s.words
        var n = i + 1
        if (n < w.size && s.same(n, i) && w[n] in ARTICLES) n++
        if (n >= w.size || !s.same(n, i) || w[n] in FUNCTION) return null
        return asks(s, n, n, "hint.it.place", "hintSay.with")
    }

    /** essere against avere at the gap and a feeling or an age after it (ho fame, ha sei anni): Italian has them. */
    private fun feel(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in AVERE && gap !in ESSERE) return null
        if (forms.none { it in AVERE } || forms.none { it in ESSERE }) return null
        val w = s.words
        val n = (i + 1 until w.size).takeWhile { s.same(it, i) }.take(3).firstOrNull { w[it] in FEELINGS } ?: return null
        return asks(s, n, n, "hint.it.feel", "hintSay.with")
    }

    /** quando, appena or dopo che before the gap in its clause, the gap a helper verb (quando avremo finito): what is done first. */
    private fun first(s: Said, i: Int, gap: String): Trigger? {
        if (gap !in AVERE && gap !in ESSERE && !participle(gap)) return null
        val w = s.words
        val k = (i - 1 downTo 0).firstOrNull { s.same(it, i) && (w[it] in set("quando appena") || (w[it] == "che" && it > 0 && w[it - 1] == "dopo")) } ?: return null
        val from = if (w[k] == "che") k - 1 else k
        return asks(s, from, k, "hint.it.first", "hintSay.after")
    }
}
