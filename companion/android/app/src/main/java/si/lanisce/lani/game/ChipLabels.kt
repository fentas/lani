package si.lanisce.lani.game

/**
 * What a word chip shows ("Put the words in order", word chips by ear). A chip is a word of the sentence or a distractor,
 * and the sentence's punctuation and capitals would give its place away: "posto." against the distractor "posti" is the
 * last word, "Jutri" the first. So a chip shows its bare word, lowercase unless it is a name, and the sentence's closing
 * mark stands after the answer ([ending]). The tokens themselves stay as they are: the answer is graded as before.
 */
object ChipLabels {
    /** [token] as its chip shows it: without the punctuation round it, the first letter lowercase unless it's one of [names]. */
    fun label(token: String, names: Set<String>): String {
        val bare = token.trim { !it.isLetterOrDigit() }.ifEmpty { return token }
        if (!bare.first().isUpperCase() || bare.lowercase() in names) return bare
        // an acronym or a word in capitals keeps them (EU, OK)
        if (bare.length > 1 && bare.drop(1).any { it.isUpperCase() }) return bare
        return bare.replaceFirstChar { it.lowercaseChar() }
    }

    /** The mark the sentence [solution] ends with ("." "?" "!" "…"), shown after the answer; empty when there is none. */
    fun ending(solution: List<String>): String = solution.lastOrNull()?.takeLastWhile { !it.isLetterOrDigit() }?.trim().orEmpty()

    /** The names of the village (its people, in every form a sentence gives them) and [more], lowercase, for [label]. */
    fun names(language: String, more: Collection<String> = emptyList()): Set<String> {
        val people = runCatching { si.lanisce.lani.game.villagers.Mentions.cast }.getOrDefault(emptyList())
        val forms = people.flatMap { v ->
            val name = si.lanisce.lani.game.villagers.Mentions.personal(v)
            si.lanisce.lani.game.villagers.Mentions.forms(name, language)
        }
        return (forms + more + PLACES).map { it.lowercase() }.toSet()
    }

    /** Places the curated sentences name (a chip keeps their capital). */
    private val PLACES = listOf(
        "Slovenija", "Slovenije", "Sloveniji", "Slovenijo", "Ljubljana", "Ljubljane", "Ljubljani", "Ljubljano",
        "Gorica", "Gorice", "Gorici", "Gorico", "Trnovo", "Trnovega", "Trnovem", "Triglav", "Triglava", "Triglavu",
        "Bled", "Bleda", "Bledu", "Brda", "Brdih", "Trst", "Trsta", "Trstu", "Italija", "Italije", "Italiji", "Italijo",
        "Avstrija", "Avstrije", "Avstriji", "Avstrijo", "Nemčija", "Nemčije", "Nemčiji", "Nemčijo", "Jan", "Jana", "Janu", "Janom",
    )
}
