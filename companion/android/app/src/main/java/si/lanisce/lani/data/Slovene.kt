package si.lanisce.lani.data

import si.lanisce.lani.l10n.Lang

/**
 * Guesses whether a short text is in a language (the learner's target: Slovene for Jan), to decide where a 🔊 makes
 * sense. Callers combine it with what is known for sure (the voice store has a clip for the text). A text reads as the
 * target when it has the target's own letters (č/š/ž, à/è/é, ä/ö/ü/ß), or more of its frequent words than of the base
 * language's.
 */
object Spoken {
    private val letters = mapOf(
        Lang.SL to Regex("[čšžČŠŽ]"),
        Lang.IT to Regex("[àèéìòùÀÈÉÌÒÙ]"),
        Lang.DE to Regex("[äöüßÄÖÜ]"),
    )
    private val word = Regex("\\p{L}+")

    // Frequent function words and A1 staples of each language; the English ones include those that look like Slovene
    // words ("to", "in", "on").
    private val words = mapOf(
        Lang.SL to setOf(
            "je", "sem", "si", "smo", "ste", "so", "sva", "sta", "se", "ga", "jo", "mu", "ji", "jih",
            "da", "ne", "ni", "nisem", "pa", "ali", "ki", "kaj", "kako", "kje", "kdo", "kdaj", "zakaj", "koliko",
            "moj", "moja", "moje", "tvoj", "tvoja", "tvoje", "njegov", "njen", "naš", "vaš",
            "dober", "dobra", "dobro", "lep", "lepa", "lepo", "hvala", "prosim", "oprostite", "živjo", "nasvidenje",
            "iz", "na", "za", "po", "od", "do", "pri", "brez", "ob", "tudi", "ampak", "zelo", "že", "še", "lahko",
            "bi", "bo", "bom", "imam", "ima", "imaš", "nimam", "grem", "gre", "greš", "danes", "jutri", "včeraj",
            "tukaj", "tam", "jaz", "ona", "ono", "vi", "oni", "rad", "rada", "dan", "jutro", "večer", "kruh", "kava",
        ),
        Lang.EN to setOf(
            "the", "is", "are", "a", "an", "and", "of", "to", "in", "on", "at", "you", "i", "it", "this", "that",
            "what", "how", "my", "your", "for", "with", "be", "do", "does", "not", "from", "by", "or", "was",
            "were", "has", "have", "say", "word", "means", "mean", "he", "she", "we", "they", "good", "day",
        ),
        Lang.IT to setOf(
            "il", "lo", "la", "le", "gli", "un", "una", "di", "del", "della", "nel", "nella", "al", "alla", "che", "non",
            "sono", "sei", "siamo", "ho", "hai", "ha", "mi", "ti", "ci", "per", "con", "come", "dove", "quando", "perché",
            "questo", "questa", "anche", "molto", "grazie", "prego", "ciao", "buongiorno", "buonasera", "io", "tu", "lui",
            "noi", "voi", "loro", "ma", "più", "oggi", "domani", "ieri", "casa", "bene", "sì", "no", "cosa",
        ),
        Lang.DE to setOf(
            "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "und", "ist", "sind", "bin", "bist", "nicht",
            "ich", "du", "er", "sie", "wir", "ihr", "mit", "auf", "für", "von", "zu", "im", "am", "auch", "sehr", "danke",
            "bitte", "hallo", "guten", "tag", "morgen", "abend", "wie", "was", "wo", "wann", "warum", "heute", "gestern",
            "ja", "nein", "gut", "haus", "kein", "keine", "mein", "meine", "dein", "deine", "es", "geht", "habe", "hast",
        ),
    )

    fun looks(text: String, target: Lang, base: Lang): Boolean {
        if (letters[target]?.containsMatchIn(text) == true) return true
        if (letters[base]?.containsMatchIn(text) == true) return false
        val found = word.findAll(text.lowercase()).map { it.value }.toList()
        if (found.isEmpty()) return false
        val t = found.count { it in words.getValue(target) }
        val b = found.count { it in words.getValue(base) }
        return t > b && t * 3 >= minOf(found.size, 6)
    }
}

/** Whether a short text reads as Slovene rather than English ([Spoken], for Jan's pair). */
object Slovene {
    fun looks(text: String): Boolean = Spoken.looks(text, Lang.SL, Lang.EN)
}
