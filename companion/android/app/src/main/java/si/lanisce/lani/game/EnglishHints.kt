package si.lanisce.lani.game

/**
 * The hint chip's rules for the English book (companion/grammar/en; companion/SCENES.md, "The hint";
 * docs/plans/05-built/hint-chip-en.md): each page's rule in words ([Rule.text], a string table's key, in the learner's
 * base), the sentence the 🔊 says of it ([Rule.said]), models with other words, the question where the page asks one
 * ("Who?", "Where?"), and what in the sentence decides the turn's form ([Look], tried in the page's order: the first found
 * is the trigger). English has no cases and few endings: what decides is who does it (the subject: «I», «the bees», after
 * the verb in a question: "Where ____ the matches?"), the helper before a verb (after «can», «did», «will», «to»: the base
 * form; after «have»: the participle; after «is»: the -ing form), the -ing form after it (be before it: "____ going"), a
 * feeling English says with be («cold», «hungry», «old»), «got» after have, what there is (one or more), the first sound
 * after a or an, a number before a noun, the noun after whose it is, a no before any, the place or the time after a
 * preposition, «since» or «for» by what follows, a word of time (yesterday, now, every day). Word lists, no dictionary: the
 * chip works at once and offline, and finds a trigger only where the sentence shows it plainly; else the rule alone. It
 * reads, it never grades. Pure.
 */
object EnglishHints {
    /** What the chip looks for in the sentence, by page, in order (each is described where it is found: [find]). */
    enum class Look {
        WHO, BASE, PARTICIPLE, ING, BE_ING, FEEL, NEEDS_BE, DO_VERB, GOT, THERE, ARTICLE, NUMBER, THESE, OWNER, ALONE, OBJECT,
        SUBJECT, NO, COUNT, ONE_NO, NO_HAVE, HAVE_BEEN, MOTION, FIXED, PLACE, TIME, SINCE, PAST, FUTURE, NOW, HABIT, IF, MORE,
        PATTERN, SAID, REL, TAG, WHILE, BY, SELF, HOW,
    }

    /**
     * A page's rule: [text] the card's rule in words and [said] the 🔊's sentence (string tables' keys), [models] with
     * other words than any turn's, [question] (English) where the page asks one, [looks] what decides the form, in order.
     */
    class Rule(val text: String, val said: String, val models: List<String>, val question: String? = null, val looks: List<Look> = emptyList())

    /** The book's pages (companion/grammar/en), by id: every page, A1 to B2. */
    val RULES: Map<String, Rule> = mapOf(
        // A1
        "articles" to Rule("hint.en.ruleArticles", "hintSay.en.ruleArticles", listOf("a horse · an apple · an hour · a uniform", "a pear · an onion", "the sun · the moon"), null, listOf(Look.ARTICLE, Look.NUMBER)),
        "can" to Rule("hint.en.ruleCan", "hintSay.en.ruleCan", listOf("I can swim. · She can swim.", "Can you whistle? — Yes, I can.", "We can't stop.", "He could read at four."), null, listOf(Look.BASE, Look.WHO)),
        "numbers" to Rule("hint.en.ruleNumbers", "hintSay.en.ruleNumbers", listOf("fifteen · fifty", "twenty-one · thirty-six", "a hundred and five", "one hen · three hens"), "How many?", listOf(Look.NUMBER)),
        "object-pronouns" to Rule("hint.en.ruleObjectPronouns", "hintSay.en.ruleObjectPronouns", listOf("Jack knows her.", "Sit with me.", "Ask them!", "It's me."), null, listOf(Look.OBJECT)),
        "plurals" to Rule("hint.en.rulePlurals", "hintSay.en.rulePlurals", listOf("a cat · two cats", "a box · three boxes", "a lady · two ladies", "a wolf · two wolves", "a man · two men"), "How many?", listOf(Look.NUMBER, Look.THESE)),
        "possessive-s" to Rule("hint.en.rulePossessive", "hintSay.en.rulePossessive", listOf("Jack's cap · the girls' bikes", "my coat · mine", "her cat · hers", "the men's boots"), "Whose?", listOf(Look.OWNER, Look.ALONE, Look.OBJECT)),
        "prepositions-place" to Rule("hint.en.rulePlace", "hintSay.en.rulePlace", listOf("in the kitchen · in Leeds", "on the shelf · on the wall", "at the station · at work", "behind the shed · next to the gate"), "Where?", listOf(Look.MOTION, Look.FIXED, Look.PLACE)),
        "present-continuous" to Rule("hint.en.ruleNow", "hintSay.en.ruleNow", listOf("I'm reading. · She's sleeping.", "They're laughing.", "Is it snowing?", "We aren't waiting."), null, listOf(Look.ING, Look.BE_ING, Look.WHO, Look.NOW)),
        "present-simple" to Rule("hint.en.ruleHabits", "hintSay.en.ruleHabits", listOf("I read · she reads", "you wash · he washes", "we fly · it flies", "they have · she has"), "Who?", listOf(Look.BASE, Look.WHO, Look.HABIT)),
        "pronouns" to Rule("hint.en.rulePronouns", "hintSay.en.rulePronouns", listOf("I'm Tom. · He's my brother.", "She's here. · It's a pen.", "We're late. · They're nice."), null, listOf(Look.SUBJECT, Look.OBJECT)),
        "questions-do" to Rule("hint.en.ruleDo", "hintSay.en.ruleDo", listOf("Do you swim? · Does she swim?", "I don't swim. · She doesn't swim.", "Did you see it? · I didn't see it."), "Who?", listOf(Look.BASE, Look.WHO, Look.DO_VERB)),
        "there-is-are" to Rule("hint.en.ruleThereIs", "hintSay.en.ruleThereIs", listOf("There's a cat. · There are two cats.", "There isn't a bus.", "Are there any eggs?", "Is there a café?"), null, listOf(Look.THERE, Look.WHO)),
        "this-that" to Rule("hint.en.ruleThisThat", "hintSay.en.ruleThisThat", listOf("this pen · these pens", "that boat · those boats", "What's this?", "Those are mine."), "Here or there?", listOf(Look.THESE)),
        "time-dates" to Rule("hint.en.ruleTime", "hintSay.en.ruleTime", listOf("at six · at noon", "on Friday · on the third of June", "in June · in winter · in 1999", "half past four"), "When? What time?", listOf(Look.TIME)),
        "to-be" to Rule("hint.en.ruleBe", "hintSay.en.ruleBe", listOf("I'm Tom. · You're late.", "He's tall. · She's ill.", "We're ready. · They're out."), "Who?", listOf(Look.FEEL, Look.NEEDS_BE, Look.BE_ING, Look.THERE, Look.WHO)),
        "to-have" to Rule("hint.en.ruleHave", "hintSay.en.ruleHave", listOf("I've got a bike. · She's got a bike.", "I have a bike. · She has a bike.", "Have you got a pen?", "We haven't got time."), "Who?", listOf(Look.GOT, Look.PARTICIPLE, Look.WHO)),
        "was-were" to Rule("hint.en.ruleWasWere", "hintSay.en.ruleWasWere", listOf("I was at home. · They were at home.", "It wasn't far.", "Were you there? — Yes, I was."), "Who?", listOf(Look.WHO)),
        "word-order" to Rule("hint.en.ruleWordOrder", "hintSay.en.ruleWordOrder", listOf("Tom feeds the cat in the morning.", "We like it very much.", "She often walks to work.")),
        // A2
        "adverbs" to Rule("hint.en.ruleAdverbs", "hintSay.en.ruleAdverbs", listOf("a quiet voice · she speaks quietly", "a careful driver · he drives carefully", "a good singer · she sings well", "fast · hard"), null, listOf(Look.HOW)),
        "comparatives" to Rule("hint.en.ruleCompare", "hintSay.en.ruleCompare", listOf("tall · taller than · the tallest", "useful · more useful than · the most useful", "good · better · the best", "big · bigger · the biggest"), null, listOf(Look.MORE)),
        "first-conditional" to Rule("hint.en.ruleIf", "hintSay.en.ruleIf", listOf("If you heat ice, it melts.", "If it snows, we'll stay in.", "When I get home, I'll ring you."), null, listOf(Look.IF, Look.BASE)),
        "future" to Rule("hint.en.ruleFuture", "hintSay.en.ruleFuture", listOf("I'm going to paint the shed.", "It's going to snow.", "I'll carry that for you!", "We're meeting at six."), null, listOf(Look.BASE, Look.ING, Look.BE_ING, Look.WHO, Look.FUTURE)),
        "imperatives" to Rule("hint.en.ruleImperatives", "hintSay.en.ruleImperatives", listOf("Sit down! · Don't shout!", "Let's go!", "Come in, please.", "Wait here!"), null, listOf(Look.BASE)),
        "indefinite-pronouns" to Rule("hint.en.ruleIndefinite", "hintSay.en.ruleIndefinite", listOf("someone · something · somewhere", "Is anyone there?", "Nobody knows.", "Everyone likes cake."), null, listOf(Look.NO, Look.WHO)),
        "linking-words" to Rule("hint.en.ruleLinking", "hintSay.en.ruleLinking", listOf("I stayed in because it was raining.", "It was raining, so I stayed in.", "After lunch, we went out."), null),
        "must-have-to" to Rule("hint.en.ruleMust", "hintSay.en.ruleMust", listOf("I have to work. · She has to work.", "You mustn't touch it.", "You don't have to pay.", "You should rest."), null, listOf(Look.BASE, Look.WHO)),
        "past-continuous" to Rule("hint.en.ruleWasDoing", "hintSay.en.ruleWasDoing", listOf("I was reading when the phone rang.", "While she was cooking, it started to snow.", "They were laughing."), null, listOf(Look.ING, Look.BE_ING, Look.WHILE, Look.WHO)),
        "past-simple" to Rule("hint.en.rulePast", "hintSay.en.rulePast", listOf("walk · walked · play · played", "go · went · see · saw", "Did you see it? · I didn't go.", "It was cold. · We were tired."), "When?", listOf(Look.BASE, Look.WHO, Look.NO_HAVE, Look.PAST)),
        "present-perfect" to Rule("hint.en.rulePerfect", "hintSay.en.rulePerfect", listOf("I've never been to Paris.", "Have you ever ridden a camel?", "She's lived here since May.", "We've known them for years."), null, listOf(Look.PARTICIPLE, Look.ONE_NO, Look.HAVE_BEEN, Look.SINCE, Look.WHO, Look.PAST)),
        "some-any" to Rule("hint.en.ruleSomeAny", "hintSay.en.ruleSomeAny", listOf("We've got some eggs.", "There isn't any bread.", "Would you like some tea?", "How many eggs? · How much milk?"), "How much? How many?", listOf(Look.NO, Look.COUNT)),
        "would-like" to Rule("hint.en.ruleWouldLike", "hintSay.en.ruleWouldLike", listOf("I want to go. · I'd like to go.", "Would you like a biscuit?", "Could you help me, please?", "I enjoy swimming."), null, listOf(Look.PATTERN, Look.BASE)),
        // B1
        "contrast-linkers" to Rule("hint.en.ruleContrast", "hintSay.en.ruleContrast", listOf("although it was late", "despite the rain · in spite of the rain", "because of the snow", "so that we can see"), null, listOf(Look.PATTERN)),
        "for-since" to Rule("hint.en.ruleForSince", "hintSay.en.ruleForSince", listOf("for three weeks · for an hour", "since Tuesday · since 1990", "How long have you known her?", "I lived in York for two years."), "How long?", listOf(Look.SINCE, Look.PARTICIPLE)),
        "future-continuous" to Rule("hint.en.ruleWillBeDoing", "hintSay.en.ruleWillBeDoing", listOf("This time tomorrow I'll be flying.", "I think it'll snow.", "Look at the sky! It's going to rain.", "The train leaves at six."), null, listOf(Look.BASE, Look.ING)),
        "might-must" to Rule("hint.en.ruleGuess", "hintSay.en.ruleGuess", listOf("It might snow.", "That must be the postman.", "It can't be true.", "He'll probably ring."), null, listOf(Look.BASE, Look.PARTICIPLE)),
        "passive" to Rule("hint.en.rulePassive", "hintSay.en.rulePassive", listOf("The cheese is made here.", "The church was built in 1500.", "It was painted by my aunt.", "Can it be fixed?"), null, listOf(Look.PARTICIPLE, Look.BY, Look.WHO)),
        "past-perfect" to Rule("hint.en.rulePastPerfect", "hintSay.en.rulePastPerfect", listOf("When we arrived, the film had started.", "She hadn't slept.", "By then the shop had closed."), null, listOf(Look.PARTICIPLE)),
        "phrasal-verbs" to Rule("hint.en.rulePhrasal", "hintSay.en.rulePhrasal", listOf("get up · look after", "Turn the light off. · Turn it off.", "She looks after them.")),
        "present-perfect-continuous" to Rule("hint.en.rulePerfectGoingOn", "hintSay.en.rulePerfectGoingOn", listOf("I've been waiting for an hour.", "She's been painting all day.", "She's painted three walls.", "I've known him for years."), null, listOf(Look.ING, Look.PARTICIPLE, Look.SINCE, Look.WHO)),
        "question-tags" to Rule("hint.en.ruleTags", "hintSay.en.ruleTags", listOf("It's late, isn't it?", "You aren't ill, are you?", "She lives here, doesn't she?", "So do I. · Neither can I."), null, listOf(Look.TAG)),
        "reflexive-pronouns" to Rule("hint.en.ruleSelf", "hintSay.en.ruleSelf", listOf("I hurt myself.", "She did it herself.", "on my own · by myself", "They help each other."), null, listOf(Look.SELF)),
        "relative-clauses" to Rule("hint.en.ruleWho", "hintSay.en.ruleWho", listOf("the girl who sings", "the cake which I baked", "the town where I grew up", "the man whose dog barks"), null, listOf(Look.REL)),
        "reported-questions" to Rule("hint.en.ruleAskedWhere", "hintSay.en.ruleAskedWhere", listOf("He asked where I worked.", "She asked if I was hungry.", "Do you know where the station is?"), null, listOf(Look.SAID)),
        "reported-speech" to Rule("hint.en.ruleReported", "hintSay.en.ruleReported", listOf("She said she was busy.", "He told me he was busy.", "She said she would ring.", "He told me to wait."), null, listOf(Look.SAID)),
        "second-conditional" to Rule("hint.en.ruleIfWould", "hintSay.en.ruleIfWould", listOf("If I had a car, I'd drive.", "If I were you, I'd ask.", "If it snowed, we could ski."), null, listOf(Look.IF, Look.BASE)),
        "third-conditional" to Rule("hint.en.ruleIfHad", "hintSay.en.ruleIfHad", listOf("If I had known, I would have rung.", "If we'd left earlier, we'd have caught it.", "She could have won."), null, listOf(Look.IF, Look.PARTICIPLE)),
        "too-enough" to Rule("hint.en.ruleTooEnough", "hintSay.en.ruleTooEnough", listOf("too hot · hot enough", "too much sugar · too many cars", "too tired to walk", "as old as my brother"), null, listOf(Look.COUNT)),
        "used-to" to Rule("hint.en.ruleUsedTo", "hintSay.en.ruleUsedTo", listOf("I used to play the piano.", "I didn't use to like fish.", "Did you use to walk to school?"), null, listOf(Look.BASE, Look.PATTERN)),
        "verb-patterns" to Rule("hint.en.rulePatterns", "hintSay.en.rulePatterns", listOf("I decided to stay.", "I enjoy cooking.", "good at singing", "I want you to stay."), null, listOf(Look.PATTERN, Look.BASE)),
        // B2
        "collocations" to Rule("hint.en.ruleCollocations", "hintSay.en.ruleCollocations", listOf("make a cake · do the dishes", "take a seat · have a rest", "heavy traffic · strong coffee", "a piece of cake")),
        "emphasis" to Rule("hint.en.ruleEmphasis", "hintSay.en.ruleEmphasis", listOf("It was Anna who rang.", "What I want is sleep.", "I did lock the door!"), null, listOf(Look.BASE)),
        "formal-informal" to Rule("hint.en.ruleFormal", "hintSay.en.ruleFormal", listOf("Dear Sir or Madam, … Yours faithfully,", "Dear Mr Hill, … Yours sincerely,", "Hi Tom, … Cheers,")),
        "future-perfect" to Rule("hint.en.ruleWillHave", "hintSay.en.ruleWillHave", listOf("By ten I'll have finished.", "By the time you arrive, we'll have left.", "By June I'll have been working here a year."), null, listOf(Look.PARTICIPLE, Look.BASE)),
        "have-it-done" to Rule("hint.en.ruleHaveItDone", "hintSay.en.ruleHaveItDone", listOf("I had my bike repaired.", "We've had the windows cleaned.", "I got my hair cut.", "She had her bag stolen.")),
        "mixed-conditionals" to Rule("hint.en.ruleMixedIf", "hintSay.en.ruleMixedIf", listOf("If I'd slept, I wouldn't be tired now.", "If I were taller, I'd have reached it.", "unless it rains · in case it rains"), null, listOf(Look.IF, Look.PARTICIPLE)),
        "narrative-tenses" to Rule("hint.en.ruleStory", "hintSay.en.ruleStory", listOf("I had been driving for hours when I saw it.", "The door opened.", "It was snowing.", "She had lost her key."), null, listOf(Look.ING, Look.PARTICIPLE, Look.WHO)),
        "participle-clauses" to Rule("hint.en.ruleParticipleClauses", "hintSay.en.ruleParticipleClauses", listOf("Opening the door, I saw a cat.", "Not knowing the answer, she guessed.", "a house built in 1900", "Having eaten, we left.")),
        "passive-more" to Rule("hint.en.rulePassiveMore", "hintSay.en.rulePassiveMore", listOf("It has been sold.", "It is being repaired.", "It is said that the lake is deep.", "He is said to be rich."), null, listOf(Look.PARTICIPLE, Look.BY)),
        "past-modals" to Rule("hint.en.rulePastModals", "hintSay.en.rulePastModals", listOf("You should have asked.", "It must have been the cat.", "She can't have known.", "I needn't have hurried."), null, listOf(Look.PARTICIPLE)),
        "phrasal-verbs-more" to Rule("hint.en.rulePhrasalMore", "hintSay.en.rulePhrasalMore", listOf("I can't put up with it.", "I look forward to meeting you.", "She takes after her mum.", "They called it off."), null, listOf(Look.PATTERN)),
        "quantifiers" to Rule("hint.en.ruleQuantifiers", "hintSay.en.ruleQuantifiers", listOf("both cats · neither cat", "every child · each of them", "all of them · none of them", "either bus"), null, listOf(Look.NUMBER)),
        "relative-clauses-more" to Rule("hint.en.ruleWhoMore", "hintSay.en.ruleWhoMore", listOf("My aunt, who lives in Leeds, …", "the man to whom I wrote", "…, which was a shame."), null, listOf(Look.REL)),
        "reporting-verbs" to Rule("hint.en.ruleReporting", "hintSay.en.ruleReporting", listOf("She promised to ring.", "He advised me to rest.", "They suggested walking.", "He apologised for being late."), null, listOf(Look.PATTERN, Look.SAID)),
        "result-linkers" to Rule("hint.en.ruleResult", "hintSay.en.ruleResult", listOf("so hot that we swam", "such a long day that I slept", "so much snow · so many cars", "whereas her brother is tall")),
        "wish-if-only" to Rule("hint.en.ruleWish", "hintSay.en.ruleWish", listOf("I wish I had a car.", "I wish I'd listened.", "I wish it would stop.", "I hope it stops.")),
        "word-building" to Rule("hint.en.ruleWordBuilding", "hintSay.en.ruleWordBuilding", listOf("happy · unhappy · happiness", "care · careful · careless", "quick · quickly", "agree · disagree · agreement"), null, listOf(Look.HOW)),
        "would-used-to" to Rule("hint.en.ruleWould", "hintSay.en.ruleWould", listOf("We would swim in the river.", "I'm used to getting up early.", "You'll get used to it.", "I used to get up early."), null, listOf(Look.BASE, Look.PATTERN)),
    )

    /** The 🔊's sentence of a card's rule [key] ("hint.en.ruleBe"); null for one of another table. */
    fun spoken(key: String): String? = SPOKEN[key]

    private val SPOKEN: Map<String, String> = RULES.values.associate { it.text to it.said }

    /**
     * The hint of an English turn: its first page this table has ([TurnHint.pages], those the book has), with that page's
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
    private class Said(val text: String, val names: Set<String>) {
        val spans = Forms.spans(text)
        val words = spans.map { it.value.lowercase().replace('’', '\'') }
        val clause = CaseTriggers.clauses(text)

        /** The sentence's own text of the words [from] to [to] ("the bees"). */
        fun text(from: Int, to: Int): String = text.substring(spans[from].range.first, spans[to].range.last + 1)

        fun same(a: Int, b: Int): Boolean = a in words.indices && b in words.indices && clause[a] == clause[b]

        /** The first word of [i]'s clause. */
        fun start(i: Int): Int = words.indices.first { clause[it] == clause[i] }

        /** Whether the sentence the word at [i] is in ends with a question mark. */
        fun asks(i: Int): Boolean = text.substring(spans[i].range.last + 1).firstOrNull { it in ".!?…" } == '?'
    }

    // --- the word lists ---------------------------------------------------------------------------------------------

    private fun set(s: String): Set<String> = s.split(" ").filter { it.isNotEmpty() }.toSet()

    private val SUBJECTS = set("i you he she it we they")
    private val INDEFINITE = set("everyone everybody someone somebody anyone anybody nobody everything something anything nothing")
    private val DEMONSTRATIVES = set("this that these those")
    private val OBJECTS = set("me him her us them")
    private val POSSESSIVES = set("my your his her its our their whose")
    private val OWN = set("mine yours hers ours theirs")
    private val ARTICLES = set("a an the")
    private val NUMBERS = set(
        "two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen " +
            "nineteen twenty thirty forty fifty sixty seventy eighty ninety hundred thousand",
    )

    /** What stands before a noun: an article, a demonstrative, a possessive, a number, a word of quantity. */
    private val DETERMINERS = ARTICLES + DEMONSTRATIVES + POSSESSIVES + NUMBERS +
        set("one every each some any no all both many few several much little which what another other either neither")

    /** The adjectives that often stand between the article and the noun: the old mill, a little lamb. */
    private val BEFORE_NOUN = set("old new big little small young other last first next same whole great good long tall tiny real full poor")

    private val BE = set("am is are was were be been being isn't aren't wasn't weren't")
    private val HAVE = set("have has had having haven't hasn't hadn't")
    private val DO = set("do does did don't doesn't didn't")
    private val MODALS = set(
        "can can't cannot could couldn't will won't would wouldn't shall shan't should shouldn't must mustn't might " +
            "mightn't may needn't let's",
    )

    /** The words «'s» is «is» or «has» after (it's, there's), not someone's. */
    private val S_IS = set("it he she that what there here who where how this everything everyone everybody nobody something")

    private val PREPOSITIONS = set(
        "in on at to into onto from of for with without by under over above below behind before after near between among " +
            "through across along around round up down off about against past inside outside beside towards until during " +
            "since despite next opposite",
    )
    private val CONJUNCTIONS = set("and but or so because when if while that then as than though although unless until")
    private val WH = set("what where why how which who whose when")

    /** What may stand between a helper, its subject and its verb: not, never, always, just … */
    private val ADVERBS = set("not never always often usually sometimes really also just still only even already ever yet all both soon")

    /** Words that are never a noun nor a name: what a word list can't take for a subject or the noun a word goes with. */
    private val FUNCTION = ARTICLES + DEMONSTRATIVES + POSSESSIVES + OBJECTS + SUBJECTS + BE + HAVE + DO + MODALS + PREPOSITIONS +
        CONJUNCTIONS + WH + ADVERBS + (DETERMINERS - NUMBERS) + set(
            "not no yes there here very so too now then please oh wow ok okay well again away out back home today tonight " +
                "tomorrow yesterday quick quickly everywhere somewhere anywhere nowhere downstairs upstairs inside outside",
        )

    /** The words of a no. */
    private val NEGATIVES = set("not never no nobody nothing none nowhere without")

    /** A feeling, an age, a distance, a state: English says them with be (I'm cold, how old is she?). */
    private val FEEL = set(
        "cold hot warm hungry thirsty tired sleepy scared afraid frightened ready late early old far right wrong sorry happy " +
            "sad ill sick fine well busy lucky careful sure glad angry bored worried hurt wet dry awake asleep",
    )

    /** The words that soften or say no between be and what it says: very cold, a bit cold, not afraid. */
    private val SOFT = set("very so too really quite a bit little not all still always never just")

    /** The participles of their own (the -ed ones are found by their ending). */
    private val PARTICIPLES = set(
        "been seen done gone won made come found got gotten put cut taken given written eaten broken spoken known shown " +
            "thrown grown drawn flown sung swum run drunk begun brought bought caught taught thought fought sold told held " +
            "kept slept left felt met sent spent built lost heard read hurt hit let set shut bent stung hung sat stood " +
            "understood said paid laid led fed meant become forgotten chosen frozen stolen woken worn torn hidden ridden " +
            "bitten driven fallen beaten shaken risen had",
    )
    private val NOT_ED = set("red bed shed need feed seed speed weed hundred sled wed bred")
    private val NOT_ING = set("thing something anything nothing everything king ring spring string swing wing sing sting bring evening morning ceiling pudding during")

    private fun participle(w: String): Boolean = w in PARTICIPLES || (w.length >= 4 && w.endsWith("ed") && w !in NOT_ED)

    private fun ing(w: String): Boolean = w.length >= 5 && w.endsWith("ing") && w !in NOT_ING

    /** A short form's first part and its ending: i'm → i, m; it's → it, s; we'll → we, ll. */
    private fun head(w: String): String = w.substringBefore('\'')
    private fun tail(w: String): String = if ('\'' in w) w.substringAfterLast('\'') else ""

    /** A form of be, or a short form with it (I'm, you're, it's; the water's when its noun alone is a choice of the turn). */
    private fun beForm(w: String, forms: Set<String> = emptySet()): Boolean =
        w in BE || tail(w) in setOf("m", "re") || (tail(w) == "s" && (head(w) in S_IS || head(w) in forms))

    private fun haveForm(w: String): Boolean = w in HAVE || tail(w) == "ve"

    /** A helper before a verb's base form: a modal, do, to, will and would in short (I'll, we'd), let's. */
    private fun helper(w: String): Boolean = w in MODALS || w in DO || w == "to" || w == "let" || tail(w) in setOf("ll", "d")

    /** The forms of one verb by who does it: am, is, are; do, does; keep, keeps; carry, carries. */
    private val PERSON = listOf(set("am is are"), set("was were"), set("do does"), set("don't doesn't"), set("has have"), set("hasn't haven't"), set("isn't aren't"), set("wasn't weren't"))

    private fun personPair(a: String, b: String): Boolean {
        if (a == b) return false
        if (PERSON.any { a in it && b in it }) return true
        fun s(x: String, y: String) = y == x + "s" || y == x + "es" || (x.endsWith("y") && y == x.dropLast(1) + "ies")
        return s(a, b) || s(b, a)
    }

    /** One noun and its plural: cat, cats; box, boxes; lady, ladies; knife, knives. */
    private fun pluralPair(a: String, b: String): Boolean {
        if (a == b) return false
        fun p(x: String, y: String) = y == x + "s" || y == x + "es" || (x.endsWith("y") && y == x.dropLast(1) + "ies") ||
            (x.endsWith("f") && y == x.dropLast(1) + "ves") || (x.endsWith("fe") && y == x.dropLast(2) + "ves")
        return p(a, b) || p(b, a)
    }

    // --- what decides the form --------------------------------------------------------------------------------------

    private fun asks(s: Said, from: Int, to: Int, line: String, said: String) = Trigger.Asks(s.text(from, to), to, line, said)

    private fun find(look: Look, s: Said, i: Int, h: TurnHint): Trigger? {
        val w = s.words
        val gap = w[i]
        val forms = h.forms.map { it.replace('’', '\'') }.toSet()
        return when (look) {
            Look.WHO -> who(s, i, gap, forms)
            Look.BASE -> base(s, i, gap, forms)
            Look.PARTICIPLE -> participleAfter(s, i, gap)
            Look.ING -> ingAfter(s, i, gap)
            Look.BE_ING -> beBeforeIng(s, i, gap, forms)
            Look.FEEL -> feel(s, i, gap, forms)
            Look.NEEDS_BE -> needsBe(s, i, gap, forms)
            Look.DO_VERB -> doVerb(s, i, gap, forms)
            Look.GOT -> got(s, i, gap, forms)
            Look.THERE -> there(s, i, gap)
            Look.ARTICLE -> article(s, i, gap, forms)
            Look.NUMBER -> number(s, i, gap, forms)
            Look.THESE -> these(s, i, gap, forms)
            Look.OWNER -> owner(s, i, gap, forms)
            Look.ALONE -> alone(s, i, gap, forms)
            Look.OBJECT -> objectAfter(s, i, gap, forms)
            Look.SUBJECT -> subjectBeforeVerb(s, i, gap, forms)
            Look.NO -> if (gap.startsWith("any") || gap.startsWith("some")) negation(s, i) else null
            Look.COUNT -> count(s, i, gap, forms)
            Look.ONE_NO -> oneNo(s, i, gap, forms)
            Look.NO_HAVE -> noHave(s, i, gap, forms)
            Look.HAVE_BEEN -> haveBeen(s, i, gap, forms)
            Look.MOTION -> motion(s, i, gap, forms)
            Look.FIXED -> fixed(s, i, gap)
            Look.PLACE -> place(s, i, gap, forms)
            Look.TIME -> time(s, i, gap, forms)
            Look.SINCE -> since(s, i, gap, forms)
            Look.PAST -> nearest(s, i, sentence = true) { k -> w[k] in PAST || (w[k] == "ago" && k > 0) || (w[k] == "last" && k + 1 < w.size && w[k + 1] in TIME_NOUNS) || YEAR.matches(w[k]) && k > 0 && w[k - 1] == "in" }
                ?.let { k -> Trigger.Cue(if (w[k] == "last") s.text(k, k + 1) else s.spans[k].value, if (w[k] == "last") k + 1 else k, Trigger.Cue.Kind.PAST) }
            Look.FUTURE -> nearest(s, i, sentence = true) { k -> w[k] in FUTURE || (w[k] == "next" && k + 1 < w.size && w[k + 1] in TIME_NOUNS) }
                ?.let { k -> Trigger.Cue(if (w[k] == "next") s.text(k, k + 1) else s.spans[k].value, if (w[k] == "next") k + 1 else k, Trigger.Cue.Kind.FUTURE) }
            Look.NOW -> nearest(s, i, sentence = true) { k -> w[k] == "now" || (w[k] == "moment" && k > 1 && w[k - 2] == "at") || (w[k] in set("look listen") && k == s.start(k) && !s.same(k, i)) }
                ?.let { k -> if (w[k] == "moment") asks(s, k - 2, k, "hint.en.now", "hintSay.en.now") else asks(s, k, k, "hint.en.now", "hintSay.en.now") }
            Look.HABIT -> habit(s, i)
            Look.IF -> ifClause(s, i)
            Look.MORE -> more(s, i, gap, forms)
            Look.PATTERN -> pattern(s, i, gap, forms)
            Look.SAID -> said(s, i)
            Look.REL -> relative(s, i, gap)
            Look.TAG -> tag(s, i)
            Look.WHILE -> (i - 1 downTo 0).firstOrNull { k -> s.same(k, i) && w[k] == "while" }?.let { asks(s, it, it, "hint.en.while", "hintSay.after") }
            Look.BY -> by(s, i, gap, forms)
            Look.SELF -> if (gap.endsWith("self") || gap.endsWith("selves")) subjectOf(s, i) else null
            Look.HOW -> how(s, i, gap, forms)
        }
    }

    /** The word before the word at [i], past the words [skip] allows (not, never …), in its clause; null at its start. */
    private fun before(s: Said, i: Int, skip: Set<String> = ADVERBS): Int? {
        var k = i - 1
        while (k >= 0 && s.same(k, i) && s.words[k] in skip) k--
        return k.takeIf { it >= 0 && s.same(it, i) }
    }

    /** The word after the word at [i], past the words [skip] allows, in its clause; null at its end. */
    private fun after(s: Said, i: Int, skip: Set<String> = ADVERBS): Int? {
        var n = i + 1
        while (n < s.words.size && s.same(n, i) && s.words[n] in skip) n++
        return n.takeIf { s.same(it, i) }
    }

    /** The word of [s] (of [i]'s clause, unless [sentence]) that [hit] finds, the nearest to [i] first. */
    private fun nearest(s: Said, i: Int, sentence: Boolean, hit: (Int) -> Boolean): Int? =
        s.words.indices.filter { it != i && (sentence || s.same(it, i)) }.sortedBy { kotlin.math.abs(it - i) }.firstOrNull(hit)

    private val TITLES = set("mr mrs miss ms dr auntie aunt uncle granny grandad grandma grandpa old farmer captain")

    /**
     * A name: one of the cast's ([Said.names]) wherever it stands, or a word written with a capital in the middle of a
     * sentence (I aside); a title before it goes with it (Mrs Hartley). Its first word's index, or null.
     */
    private fun name(s: Said, k: Int): Int? {
        val v = s.spans[k].value
        val w = s.words[k]
        if (w == "i" || head(w) == "i" || w in FUNCTION && v !in s.names) return null
        val named = v in s.names || (k > 0 && v.first().isUpperCase() && s.text.substring(s.spans[k - 1].range.last + 1, s.spans[k].range.first).none { it in ".!?…«\"" })
        if (!named) return null
        return if (k > 0 && s.same(k - 1, k) && s.words[k - 1] in TITLES && s.spans[k - 1].value.first().isUpperCase()) k - 1 else k
    }

    /**
     * A noun phrase ending at [k] (the noun): with its determiner before it (at most two words between: the young trees,
     * my favourite place), or Jack's before it; or, with no determiner, at its clause's start or after a joining word
     * (Water always flows, Because mice eat, Wooden beams are), when [bare]. Its first word's index, or null.
     */
    private fun nounEndingAt(s: Said, k: Int, bare: Boolean = true): Int? {
        val w = s.words
        if (k !in w.indices || w[k] in FUNCTION) return null
        var d = k - 1
        var between = 0
        while (between < 2 && d >= 0 && s.same(d, k) && w[d] !in FUNCTION && tail(w[d]) != "s") {
            d--
            between++
        }
        if (d >= 0 && s.same(d, k) && (w[d] in DETERMINERS || (tail(w[d]) == "s" && head(w[d]) !in S_IS))) return d
        if (!bare || between > 1) return null
        val from = d + 1
        return from.takeIf { it == s.start(k) || (d >= 0 && s.same(d, k) && w[d] in set("because and but so when if that")) }
    }

    /**
     * A noun phrase starting at [n]: a determiner and its noun (an old word between: the old mill), or one word that is
     * no function word (shears, Annie). Its last word's index, or null.
     */
    private fun nounStartingAt(s: Said, n: Int): Int? {
        val w = s.words
        if (!s.same(n, n) || n !in w.indices) return null
        if (w[n] in DETERMINERS) {
            var e = n + 1
            if (e + 1 < w.size && s.same(e + 1, n) && w[e] in BEFORE_NOUN && w[e + 1] !in FUNCTION) e++
            return e.takeIf { it < w.size && s.same(it, n) && w[it] !in FUNCTION }
        }
        return n.takeIf { w[n] !in FUNCTION || w[n] in INDEFINITE }
    }

    /** A pronoun of the subject, someone, a name or a noun phrase starting at [n] (a question's subject, after its verb): its first and last word. */
    private fun subjectStarting(s: Said, n: Int): Pair<Int, Int>? {
        val w = s.words
        if (n !in w.indices) return null
        if (w[n] in SUBJECTS || w[n] in INDEFINITE) return n to n
        if (w[n] in DEMONSTRATIVES && !(s.same(n + 1, n) && w[n + 1] !in FUNCTION)) return n to n
        name(s, n)?.let { from -> return if (from == n && n + 1 < w.size && s.same(n + 1, n) && w[n] in TITLES) n to n + 1 else from to n }
        return nounStartingAt(s, n)?.let { n to it }
    }

    /** A pronoun of the subject, someone, a name or a noun phrase ending at [k], before its verb: its first word. */
    private fun subjectEnding(s: Said, k: Int, bare: Boolean): Int? {
        val w = s.words
        if (k !in w.indices) return null
        val governed = k > 0 && s.same(k - 1, k) && w[k - 1] in PREPOSITIONS
        if ((w[k] in SUBJECTS || w[k] in INDEFINITE || w[k] in DEMONSTRATIVES) && !governed) return k
        name(s, k)?.let { return if (governed) null else it }
        return nounEndingAt(s, k, bare)
    }

    /** Whether the verb at [i] asks: the sentence asks, and its clause starts with it or with a question word (Where are …, How many needles does …). */
    private fun inverted(s: Said, i: Int): Boolean {
        if (!s.asks(i)) return false
        val start = s.start(i)
        if (start == i) return true
        return s.words[start] in WH && i - start <= 4 && (start until i).none { s.words[it] in SUBJECTS }
    }

    /**
     * Who does it: the gap a verb whose choices differ by who does it (is, are; does, do; keeps, keep; was, were), and its
     * subject: in a question with the verb first, after it (Where are the matches? Does it need oil?); else before it (the
     * bees go, Water always flows, Wilf is, nobody takes). A question word that is the subject itself (Who sleeps …?
     * What happens …?) is who.
     */
    private fun who(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (forms.none { personPair(gap, it) }) return null
        val w = s.words
        if (inverted(s, i)) {
            val start = s.start(i)
            // "Which one is the best?": which is the subject, or the best: say nothing
            if (start < i && w[start] == "which" && gap in BE) return null
            val n = after(s, i, set("not")) ?: return whAlone(s, i)
            val (a, b) = subjectStarting(s, n) ?: return whAlone(s, i)
            return Trigger.Cue(s.text(a, b), b, Trigger.Cue.Kind.WHO)
        }
        val k = before(s, i) ?: return null
        val from = subjectEnding(s, k, bare = true) ?: return null
        return Trigger.Cue(s.text(from, k), k, Trigger.Cue.Kind.WHO)
    }

    /** who or what alone before the verb of a question, with nothing after it that could be its subject: who does it. */
    private fun whAlone(s: Said, i: Int): Trigger? {
        val start = s.start(i)
        return if (start == i - 1 && s.words[start] in set("who what")) Trigger.Cue(s.spans[start].value, start, Trigger.Cue.Kind.WHO) else null
    }

    /**
     * A verb's base form at the gap after its helper: a modal (can, will, I'll, shall), do, did, don't, to, let's, before
     * or before the subject in a question (Can I keep, Did the thorns hurt, Where do they go, Does a pony need). After be
     * used to and look forward to, the -ing form.
     */
    private fun base(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap in FUNCTION && gap !in set("be have do")) return null
        if (forms.size < 2 || forms.any { it in FUNCTION && it !in BE && it !in HAVE && it !in DO }) return null
        val w = s.words
        var k = before(s, i) ?: return null
        if (!helper(w[k])) {
            val from = subjectEnding(s, k, bare = false) ?: k.takeIf { it > 0 && s.same(it - 1, it) && helper(w[it - 1]) && w[it] !in FUNCTION } ?: return null
            k = before(s, from) ?: return null
            if (!helper(w[k]) || w[k] == "to") return null
        }
        // had + the participle is no base form (we'd gone)
        if (tail(w[k]) == "d" && participle(gap) && forms.none { participle(it) && it != gap }) return null
        if (w[k] == "to" && k > 0 && s.same(k - 1, k) && (w[k - 1] == "forward" || (w[k - 1] == "used" && k > 1 && (beForm(w[k - 2]) || w[k - 2] in set("get got getting"))))) {
            return if (ing(gap)) asks(s, k - 1, k, "hint.en.ingAfter", "hintSay.after") else null
        }
        return asks(s, k, k, "hint.en.base", "hintSay.after")
    }

    /** The participle at the gap after have (I've painted, have you caught, it's been) or be (it was built): the helper. */
    private fun participleAfter(s: Said, i: Int, gap: String): Trigger? {
        if (!participle(gap)) return null
        val w = s.words
        fun helps(x: String) = haveForm(x) || beForm(x) || tail(x) in setOf("s", "d")
        var k = before(s, i) ?: return null
        if (!helps(w[k])) {
            val from = subjectEnding(s, k, bare = false) ?: return null
            k = before(s, from) ?: return null
            if (!helps(w[k])) return null
        }
        return asks(s, k, k, "hint.en.participle", "hintSay.after")
    }

    /** The -ing form at the gap after be (it's going, the wind is pulling, are you building, what is it doing): be. */
    private fun ingAfter(s: Said, i: Int, gap: String): Trigger? {
        if (!ing(gap)) return null
        val w = s.words
        fun be(x: String) = beForm(x) || tail(x) == "s" || x in set("been")
        var k = before(s, i) ?: return null
        if (!be(w[k])) {
            val from = subjectEnding(s, k, bare = false) ?: return null
            k = before(s, from) ?: return null
            if (!be(w[k])) return null
        }
        return asks(s, k, k, "hint.en.afterBe", "hintSay.after")
    }

    /** Be at the gap against no be or do (I'm or I, are or do), and the -ing form after it (past a question's subject): be goes before it. */
    private fun beBeforeIng(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (!beForm(gap, forms) || forms.all { beForm(it, forms) }) return null
        val w = s.words
        var n = after(s, i) ?: return null
        if (!ing(w[n]) && inverted(s, i)) n = subjectStarting(s, n)?.let { (_, b) -> after(s, b) } ?: return null
        return if (ing(w[n])) asks(s, n, n, "hint.en.beIng", "hintSay.with") else null
    }

    /** Be at the gap against have, do or no verb, and a feeling, an age or a distance (I'm cold, are you scared, how old is it): English says it with be. */
    private fun feel(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (!beForm(gap, forms) || forms.all { beForm(it, forms) }) return null
        val w = s.words
        if (i >= 2 && s.same(i - 2, i) && w[i - 2] == "how" && w[i - 1] in FEEL) return asks(s, i - 1, i - 1, "hint.en.feelBe", "hintSay.with")
        var n = i + 1
        while (n < w.size && s.same(n, i) && n - i <= 4) {
            if (w[n] in FEEL) return asks(s, n, n, "hint.en.feelBe", "hintSay.with")
            if (w[n] !in SOFT && w[n] !in SUBJECTS) return null
            n++
        }
        return null
    }

    /** A short form with be at the gap against its pronoun alone (it's or it), and what it says after it: English needs be. */
    private fun needsBe(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (tail(gap) !in setOf("m", "s", "re") || head(gap) !in forms) return null
        val w = s.words
        val n = after(s, i, SOFT - set("a little")) ?: return null
        if (w[n] in PREPOSITIONS || ing(w[n]) || w[n] in CONJUNCTIONS) return null
        val end = if (w[n] in DETERMINERS) nounStartingAt(s, n) ?: return null else n
        return asks(s, i + 1, end, "hint.en.needsBe", "hintSay.with")
    }

    /** Do at the gap against be or a no alone (do or are, don't or not), and the verb of its own after it (past a question's subject). */
    private fun doVerb(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in DO) return null
        val be = forms.any { it in BE || (it != gap && beForm(it, forms)) }
        val no = forms.any { it == "not" || it == "no" }
        if (!be && !no) return null
        val w = s.words
        var n = after(s, i) ?: return null
        if (inverted(s, i)) n = subjectStarting(s, n)?.let { (_, b) -> after(s, b) } ?: return null
        if (w[n] in FUNCTION || ing(w[n])) return null
        return asks(s, n, n, if (be) "hint.en.doNotBe" else "hint.en.doNo", "hintSay.with")
    }

    /** Have at the gap against do, and got after it: have got asks and says no by itself. */
    private fun got(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (!haveForm(gap) || forms.none { it in DO }) return null
        val n = (i + 1 until minOf(s.words.size, i + 4)).firstOrNull { s.same(it, i) && s.words[it] == "got" } ?: return null
        return asks(s, n, n, "hint.en.got", "hintSay.with")
    }

    /**
     * Be at the gap after there or here, or before there (There are …, here are …, Is there …, how many bats are there),
     * and what there is: the noun phrase after it, with its amount (a lot of lakes, so many, two stones, any tea), or the
     * one before (How many seeds): one or more decides the verb.
     */
    private fun there(s: Said, i: Int, gap: String): Trigger? {
        if (gap !in BE && !(tail(gap) == "s" && head(gap) in set("there here"))) return null
        val w = s.words
        val before = i > 0 && s.same(i - 1, i) && w[i - 1] in set("there here")
        val after = s.same(i + 1, i) && w[i + 1] == "there"
        if (!before && !after) return null
        val p = what(s, if (after) i + 2 else i + 1) ?: howMany(s, i) ?: return null
        return asks(s, p.first, p.second, "hint.en.thing", "hintSay.with")
    }

    /** What there is, from [n]: an amount (a lot of, lots of, so many, only, any) and its noun phrase; null when it shows nothing of one or more. */
    private fun what(s: Said, n: Int): Pair<Int, Int>? {
        val w = s.words
        if (!s.same(n, n) || n !in w.indices) return null
        var k = n
        fun at(j: Int, x: String) = j < w.size && s.same(j, n) && w[j] == x
        when {
            at(k, "a") && at(k + 1, "lot") && at(k + 2, "of") -> k += 3
            at(k, "lots") -> if (at(k + 1, "of")) k += 2 else return n to k
        }
        while (k < w.size && s.same(k, n) && w[k] in set("so only just")) k++
        if (k >= w.size || !s.same(k, n)) return null
        if (w[k] in set("many much")) return n to k
        if (w[k] in set("any some no")) {
            if (!s.same(k + 1, n) || w[k + 1] in FUNCTION) return null
            // any head torches: its noun of one or two words
            return n to if (s.same(k + 2, n) && w[k + 2] !in FUNCTION) k + 2 else k + 1
        }
        val end = if (w[k] in DETERMINERS) nounStartingAt(s, k) ?: k.takeIf { w[k] == "one" || w[k] in NUMBERS } else k.takeIf { w[k] !in FUNCTION }
        return end?.let { n to it }
    }

    /** How many and its noun before the gap (How many seeds are there): its first and last word. */
    private fun howMany(s: Said, i: Int): Pair<Int, Int>? {
        val start = s.start(i)
        val w = s.words
        return if (i - start >= 3 && w[start] == "how" && w[start + 1] == "many" && w[i - 1] !in FUNCTION) start to i - 1 else null
    }

    /** a against an at the gap, and the word after it: its first sound (a big basket, an egg, an hour). */
    private fun article(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in set("a an") || !forms.containsAll(set("a an"))) return null
        val n = i + 1
        return if (s.same(n, i) && s.words[n] !in FUNCTION) asks(s, n, n, "hint.en.sound", "hintSay.with") else null
    }

    /**
     * A noun at the gap (gears, gear; mice, mouses) and before it, past a word or two of its own (six oak beams): a number
     * or a word of more than one (two hundred, lots of, a lot of, how many, these): more than one; every, each: one at a
     * time; a, an, one, this: just one (only for a noun and its plural).
     */
    private fun number(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap in FUNCTION || gap == "o'clock" || forms.any { it in FUNCTION }) return null
        val w = s.words
        var k = i - 1
        var between = 0
        while (between < 2 && k >= 0 && s.same(k, i) && w[k] !in FUNCTION && w[k] !in NUMBERS) {
            k--
            between++
        }
        if (k < 0 || !s.same(k, i)) return null
        fun at(j: Int, x: String) = j >= 0 && s.same(j, i) && w[j] == x
        return when {
            w[k] in NUMBERS || (DIGITS.matches(w[k]) && w[k] != "1") -> {
                var from = k
                while (from > 0 && s.same(from - 1, i) && (w[from - 1] in NUMBERS || w[from - 1] == "and")) from--
                if (w[from] == "and") from++
                asks(s, from, k, "hint.en.many", "hintSay.after")
            }
            w[k] in set("these those many several both few") -> {
                val from = if (w[k] == "many" && at(k - 1, "how")) k - 1 else k
                asks(s, from, k, "hint.en.many", "hintSay.after")
            }
            w[k] == "of" && at(k - 1, "lot") && at(k - 2, "a") -> asks(s, k - 2, k, "hint.en.many", "hintSay.after")
            w[k] == "of" && at(k - 1, "lots") -> asks(s, k - 1, k, "hint.en.many", "hintSay.after")
            forms.none { pluralPair(gap, it) } -> null
            w[k] in set("every each") -> asks(s, k, k, "hint.en.each", "hintSay.after")
            w[k] in set("a an one another this that") -> asks(s, k, k, "hint.en.one", "hintSay.after")
            else -> null
        }
    }

    /** this against these, that against those at the gap, and the noun after it: one or more decides. */
    private fun these(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in DEMONSTRATIVES || forms.any { it !in DEMONSTRATIVES }) return null
        val n = i + 1
        return if (s.same(n, i) && s.words[n] !in FUNCTION) asks(s, n, n, "hint.en.oneOrMore", "hintSay.with") else null
    }

    /** Whose it is at the gap (your, uncle's, whose, its: against you, uncle, who, it's), and the noun after it. */
    private fun owner(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val owns = gap in POSSESSIVES || (tail(gap) == "s" && head(gap) in forms) || gap.endsWith("s'")
        if (!owns || forms.size < 2) return null
        val w = s.words
        var n = i + 1
        if (s.same(n + 1, i) && w[n] in BEFORE_NOUN && w[n + 1] !in FUNCTION) n++
        return if (s.same(n, i) && w[n] !in FUNCTION && !ing(w[n])) asks(s, n, n, "hint.en.owner", "hintSay.en.before") else null
    }

    /** mine, yours, ours at the gap against my, your, our, with no noun after it: it stands alone. */
    private fun alone(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in OWN || forms.none { it in POSSESSIVES }) return null
        val w = s.words
        val n = i + 1
        if (s.same(n, i) && w[n] !in FUNCTION) return null
        val k = if (s.same(n, i)) n else (i - 1).takeIf { s.same(it, i) } ?: return null
        return asks(s, k, k, "hint.en.alone", "hintSay.with")
    }

    /** An object pronoun at the gap (me, him, them, you against I, his, your …) after a verb or a preposition. */
    private fun objectAfter(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in OBJECTS && gap !in set("you it")) return null
        if (forms.none { it in SUBJECTS || it in POSSESSIVES }) return null
        if (s.same(i + 1, i) && s.words[i + 1] !in FUNCTION && gap in set("you it her")) return null
        val k = (i - 1).takeIf { it >= 0 && s.same(it, i) } ?: return null
        val w = s.words[k]
        if (w in FUNCTION && w !in PREPOSITIONS) return null
        return asks(s, k, k, "hint.en.object", "hintSay.after")
    }

    /** A subject pronoun at the gap (he against him, his) and its verb after it: the one who does it. */
    private fun subjectBeforeVerb(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in SUBJECTS || forms.none { it in OBJECTS || it in POSSESSIVES }) return null
        val n = i + 1
        if (!s.same(n, i)) return null
        val w = s.words[n]
        val verb = w in BE || w in HAVE || w in DO || w in MODALS || (w !in FUNCTION && !s.spans[n].value.first().isUpperCase())
        return if (verb) asks(s, n, n, "hint.en.subject", "hintSay.en.before") else null
    }

    /** A no before the gap in its clause (not, haven't, never, isn't): after it, any. */
    private fun negation(s: Said, i: Int): Trigger? =
        (i - 1 downTo 0).firstOrNull { k -> s.same(k, i) && (s.words[k] in NEGATIVES || s.words[k].endsWith("n't")) }?.let { Trigger.Negation(s.spans[it].value, it) }

    /** much against many at the gap, and the noun after it: whether it can be counted. */
    private fun count(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in set("much many") || !forms.containsAll(set("much many"))) return null
        val n = i + 1
        return if (s.same(n, i) && s.words[n] !in FUNCTION) asks(s, n, n, "hint.en.count", "hintSay.with") else null
    }

    /** A helper and its no at the gap (have against haven't) and another no in the clause (never, nothing): one no is enough. */
    private fun oneNo(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val pair = forms.any { it != gap && (it == gap + "n't" || gap == it + "n't" || (it == "haven't" && gap == "have") || (it == "won't" && gap == "will")) }
        if (!pair) return null
        val k = nearest(s, i, sentence = false) { s.words[it] in NEGATIVES - set("not") } ?: return null
        return asks(s, k, k, "hint.en.oneNo", "hintSay.with")
    }

    /** A past at the gap against a participle of its own (saw, seen; did, done), no have before it: one word, the past. */
    private fun noHave(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap in PARTICIPLES || forms.none { it != gap && it in PARTICIPLES && !it.endsWith("ed") }) return null
        val w = s.words
        if ((0 until i).any { s.same(it, i) && haveForm(w[it]) }) return null
        val k = (i - 1).takeIf { it >= 0 && s.same(it, i) } ?: return null
        return asks(s, k, k, "hint.en.noHave", "hintSay.after")
    }

    /** have against be at the gap and been after it (How long has it been there?): the participle decides the helper. */
    private fun haveBeen(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (!(haveForm(gap) || gap in BE) || forms.none { haveForm(it) } || forms.none { it in BE }) return null
        val n = (i + 1 until minOf(s.words.size, i + 4)).firstOrNull { s.same(it, i) && participle(s.words[it]) } ?: return null
        return asks(s, n, n, "hint.en.byParticiple", "hintSay.with")
    }

    private val STATIC = set("in on at")
    private val TOWARDS = set("to into onto")
    private val MOTION = set("go goes going went come comes coming came run runs ran walk walks get gets fly flies climb jump")

    /** to or into at the gap against at, in, on after a verb of going (go to the hut, come into the tent): going there. */
    private fun motion(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val all = forms + gap
        if (all.none { it in TOWARDS } || all.none { it in STATIC } || all.any { it !in TOWARDS && it !in STATIC }) return null
        val k = before(s, i) ?: return null
        return if (s.words[k] in MOTION) asks(s, k, k, "hint.en.motion", "hintSay.after") else null
    }

    /** to or of after next, in front, out: they always go together. */
    private fun fixed(s: Said, i: Int, gap: String): Trigger? {
        val k = (i - 1).takeIf { it >= 0 && s.same(it, i) } ?: return null
        val w = s.words[k]
        return if ((gap == "to" && w == "next") || (gap == "of" && w in set("front out instead"))) asks(s, k, k, "hint.en.fixed", "hintSay.with") else null
    }

    /** in, on or at at the gap, and the place after it (the attic, the scales, London): the kind of place decides. */
    private fun place(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val all = forms + gap
        if (gap !in STATIC + TOWARDS || all.any { it !in STATIC + TOWARDS } || all.count { it in STATIC } < 1) return null
        val n = i + 1
        if (!s.same(n, i)) return null
        val end = nounStartingAt(s, n) ?: return null
        if (s.words[n] in SUBJECTS) return null
        return asks(s, n, end, "hint.en.place", "hintSay.with")
    }

    private val DAYS = set("monday tuesday wednesday thursday friday saturday sunday mondays tuesdays wednesdays thursdays fridays saturdays sundays weekend")
    private val MONTHS = set("january february march april may june july august september october november december spring summer autumn winter")
    private val DAY_PARTS = set("morning afternoon evening night noon midnight midday christmas easter")
    private val DIGITS = Regex("""\d+""")
    private val YEAR = Regex("""1\d{3}|20\d{2}""")

    /** at, on or in at the gap and the time after it (seven, Monday, May, the morning, 1720): the kind of time decides. */
    private fun time(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in STATIC || (forms + gap).any { it !in STATIC }) return null
        val w = s.words
        var n = i + 1
        if (s.same(n, i) && w[n] in set("the a")) n++
        if (!s.same(n, i)) return null
        val x = w[n]
        val timeWord = x in DAYS || x in MONTHS || x in DAY_PARTS || x in NUMBERS || x in set("one half quarter") || DIGITS.matches(x)
        return if (timeWord) asks(s, i + 1, n, "hint.en.time", "hintSay.with") else null
    }

    private val TIME_NOUNS = set("second seconds minute minutes hour hours day days week weeks month months year years summer winter spring autumn night morning time ages")

    /** for against since (or from, ago) at the gap, and what follows: how long (ten years, an hour) or when it began (Monday, 2016, then). */
    private fun since(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val all = forms + gap
        if (gap !in set("for since from ago") || !(all.contains("for") || all.contains("since"))) return null
        val w = s.words
        val n = i + 1
        if (!s.same(n, i)) return null
        val end = (n until minOf(w.size, n + 3)).takeWhile { s.same(it, i) }.firstOrNull { w[it] in TIME_NOUNS }
        if (end != null && (w[n] in NUMBERS || w[n] in set("a an one two few several many ages ten") || DIGITS.matches(w[n]))) return asks(s, n, end, "hint.en.howLong", "hintSay.with")
        val point = w[n] in DAYS || w[n] in MONTHS || w[n] in DAY_PARTS || YEAR.matches(w[n]) || w[n] in set("then yesterday last christmas") || w[n] in NUMBERS
        return if (point) asks(s, n, if (w[n] == "last" && s.same(n + 1, i)) n + 1 else n, "hint.en.whenBegan", "hintSay.with") else null
    }

    private val PAST = set("yesterday once")
    private val FUTURE = set("tomorrow soon later tonight")

    /** every day, always, usually, often, on Mondays, once a week: again and again (the present simple). */
    private fun habit(s: Said, i: Int): Trigger? {
        val w = s.words
        val k = nearest(s, i, sentence = false) { k ->
            w[k] in set("always usually often sometimes never") || (w[k] == "every" && s.same(k + 1, k)) ||
                (w[k] in DAYS && w[k].endsWith("s") && k > 0 && w[k - 1] == "on")
        } ?: return null
        val to = if (w[k] == "every") k + 1 else k
        val from = if (w[k] in DAYS) k - 1 else k
        return asks(s, from, to, "hint.en.habit", "hintSay.en.habit")
    }

    /** «if» or «unless» in the gap's half (the if-half), else in the other half of the sentence (what would follow). */
    private fun ifClause(s: Said, i: Int): Trigger? {
        val w = s.words
        val ifs = w.indices.filter { w[it] in set("if unless") && it != i && !(it > 0 && w[it - 1] in set("as even what")) }
        ifs.firstOrNull { s.same(it, i) && it < i }?.let { return asks(s, it, it, "hint.en.ifHalf", "hintSay.after") }
        return ifs.firstOrNull { !s.same(it, i) }?.let { asks(s, it, it, "hint.en.ifOther", "hintSay.en.inOther") }
    }

    /** An adjective's form at the gap before than (taller than): comparing two; after the (the tallest): the most of a group. */
    private fun more(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val w = s.words
        if (gap in set("than then as that") && forms.any { it in set("than then as that") && it != gap }) {
            val k = (i - 1).takeIf { it >= 0 && s.same(it, i) } ?: return null
            return if (w[k].endsWith("er") || (k > 0 && w[k - 1] in set("more less"))) asks(s, k, k, "hint.en.than", "hintSay.with") else null
        }
        if (gap in FUNCTION && gap !in set("more most")) return null
        if (s.same(i + 1, i) && w[i + 1] == "than") return asks(s, i + 1, i + 1, "hint.en.than", "hintSay.with")
        val k = before(s, i, set("very")) ?: return null
        return if (w[k] == "the") asks(s, k, k, "hint.en.most", "hintSay.after") else null
    }

    private val PATTERN_VERBS = set(
        "enjoy enjoys enjoyed finish finishes finished mind minds minded like likes liked love loves loved hate hates hated " +
            "start starts started stop stops stopped keep keeps kept begin begins began decide decides decided want wants wanted " +
            "hope hopes hoped need needs needed try tries tried learn learns learned learnt forget forgets forgot agree agrees " +
            "agreed promise promises promised refuse refuses refused plan plans planned suggest suggests suggested admit admits " +
            "admitted deny denies denied avoid avoids avoided practise practises practised remember remembers remembered " +
            "imagine miss misses missed risk can't couldn't",
    )

    /** An -ing form or a base form after a verb that takes one (enjoy walking, decided to): each verb has its own; after a small word (good at walking, despite): the -ing form. */
    private fun pattern(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val w = s.words
        val k = before(s, i) ?: return null
        val ingTurn = ing(gap) || forms.any(::ing)
        if (ingTurn && w[k] in PATTERN_VERBS && w[k] !in set("can't couldn't")) return asks(s, k, k, "hint.en.pattern", "hintSay.after")
        if (ingTurn && w[k] == "help" && forms.any(::ing)) return asks(s, k, k, "hint.en.pattern", "hintSay.after")
        if (ingTurn && w[k] in PREPOSITIONS && w[k] != "to") return asks(s, k, k, "hint.en.ingAfter", "hintSay.after")
        if (ingTurn && w[k] == "to" && k > 0 && s.same(k - 1, k) && w[k - 1] in set("forward used")) return asks(s, k - 1, k, "hint.en.ingAfter", "hintSay.after")
        if (gap == "to" && forms.any { it != gap } && k >= 0 && w[k] in PATTERN_VERBS) return asks(s, k, k, "hint.en.pattern", "hintSay.after")
        return null
    }

    private val SAID_PAST = set("said told asked explained promised thought knew wondered wanted answered replied")
    private val SAID_NOW = set("says tells asks explains thinks knows wonders")

    /** A verb of saying or asking before the gap in its clause (she said, he asked where, she told me): said later, so the tense moves back. */
    private fun said(s: Said, i: Int): Trigger? {
        val w = s.words
        val k = (i - 1 downTo maxOf(0, i - 5)).firstOrNull { s.same(it, i) && (w[it] in SAID_PAST || w[it] in SAID_NOW) } ?: return null
        return asks(s, k, k, if (w[k] in SAID_PAST) "hint.en.saidPast" else "hint.en.saidNow", "hintSay.after")
    }

    /** who, which, that, where, whose at the gap after the noun it stands for (the man who, the town where): who or what was just named. */
    private fun relative(s: Said, i: Int, gap: String): Trigger? {
        if (gap !in set("who which that where whose whom when")) return null
        val k = (i - 1).takeIf { it >= 0 && s.same(it, i) } ?: return null
        name(s, k)?.let { return asks(s, it, k, "hint.en.afterNoun", "hintSay.after") }
        val from = nounEndingAt(s, k, bare = false) ?: return null
        return asks(s, from, k, "hint.en.afterNoun", "hintSay.after")
    }

    /** A question tag at the gap (…, isn't it?), and the verb of the sentence before it: the tag takes it, yes against no. */
    private fun tag(s: Said, i: Int): Trigger? {
        val w = s.words
        val c = s.clause[i]
        val tagWords = w.indices.filter { s.clause[it] == c }
        if (tagWords.size > 2 || tagWords.first() != i || !s.asks(i) || c == 0) return null
        val main = w.indices.filter { s.clause[it] == c - 1 }
        val k = main.firstOrNull { x -> w[x] in BE || w[x] in HAVE || w[x] in DO || w[x] in MODALS || w[x].endsWith("n't") || tail(w[x]) in setOf("s", "re", "m", "ll", "ve", "d") }
            ?: main.firstOrNull { x -> x > main.first() && w[x - 1] in SUBJECTS + INDEFINITE && w[x] !in FUNCTION }
            ?: return null
        return asks(s, k, k, "hint.en.tag", "hintSay.with")
    }

    /** by at the gap against from, of, with after a participle (built by): who did it. */
    private fun by(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if ("by" !in forms + gap || gap !in set("by from of with")) return null
        val k = (i - 1).takeIf { it >= 0 && s.same(it, i) } ?: return null
        return if (participle(s.words[k])) asks(s, k, k, "hint.en.by", "hintSay.after") else null
    }

    /** myself, herself … at the gap, and who does it: the subject of its clause. */
    private fun subjectOf(s: Said, i: Int): Trigger? {
        val w = s.words
        val k = (s.start(i) until i).firstOrNull { w[it] in SUBJECTS || w[it] in INDEFINITE || head(w[it]) in SUBJECTS && tail(w[it]).isNotEmpty() } ?: return null
        return Trigger.Cue(s.spans[k].value, k, Trigger.Cue.Kind.WHO)
    }

    /** quickly against quick at the gap: before a noun, what it is like; else after its verb, how it is done. */
    private fun how(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val ly = (forms + gap).filter { it.endsWith("ly") || it == "well" }
        if (ly.isEmpty() || ly.size == forms.size) return null
        val w = s.words
        if (s.same(i + 1, i) && w[i + 1] !in FUNCTION && gap !in ly) return asks(s, i + 1, i + 1, "hint.en.whatLike", "hintSay.en.before")
        val k = before(s, i) ?: return null
        return if (w[k] !in FUNCTION) asks(s, k, k, "hint.en.how", "hintSay.after") else null
    }
}
