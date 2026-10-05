package si.lanisce.lani.l10n

/**
 * The learner as the content speaks to them and of them (companion/SCENES.md, "The learner in the content"): their name in
 * each case Slovene declines it, and their gender for the forms that agree with them. Content writes placeholders instead
 * of a name and a gender of its own:
 *
 * - `{learner}`: the name as it is ("Dober večer, {learner}!");
 * - `{learner:gen}`, `:dat`, `:acc`, `:loc`, `:ins`: the Slovene cases (Jana, Janu, Jana, pri Janu, z Janom; Ane, Ani …);
 *   `{learner:poss}`: the possessive's stem, its ending written after it ("{learner:poss}a hiša": Janova, Anina hiša);
 * - `{m:prišel|f:prišla}`: what agrees with the learner ("Si {m:lačen|f:lačna}?"), either order; a branch may hold the
 *   name, nothing else in braces.
 *
 * The bridge renders them for its learner wherever it serves a text (bridge/src/addressee.ts, the same rules); the app
 * renders what it bundles (the culture packs, the festivals' packs, the grammar book, the drills) and, should a bridge
 * not have, what it gets. Rendered for a male learner whose name is the one a text had before, every text reads as it
 * did, so its voice clip is the same.
 *
 * [current] is the learner of this phone (the learner profile's, kept by [LearnerSetting]); [generic] when the profile
 * has no name and the village's word for a friend stands in for it ("prijatelj"), written small inside a sentence.
 */
data class Learner(val name: String, val female: Boolean, val forms: Map<String, String>, val generic: Boolean = false) {

    /** [text] said to this learner: each gender pair as their gender has it, then the name in its case. */
    fun render(text: String): String {
        if ('{' !in text) return text
        val paired = PAIR.replace(text) { m ->
            val (l1, t1, l2, t2) = m.destructured
            if (l1 == l2) m.value else if ((l1 == "f") == female) t1 else t2
        }
        return NAME.replace(paired) { m -> form(m.groupValues[1], paired, m.range.first) }
    }

    /**
     * A JSON document's text with every placeholder rendered: they only stand inside strings (a `{` of the JSON's own is
     * followed by a quote, a space or a `}`), so the name goes in escaped as a JSON string's text, a branch as it is.
     */
    fun renderJson(raw: String): String {
        if ('{' !in raw || !(raw.contains("{learner") || raw.contains("{m:") || raw.contains("{f:"))) return raw
        val paired = PAIR.replace(raw) { m ->
            val (l1, t1, l2, t2) = m.destructured
            if (l1 == l2) m.value else if ((l1 == "f") == female) t1 else t2
        }
        return NAME.replace(paired) { m -> escape(form(m.groupValues[1], paired, m.range.first)) }
    }

    /**
     * Every form of the name a sentence may say: the name, its cases and its possessive's forms (Janov, Janova, Janovega …),
     * as [si.lanisce.lani.game.villagers.Mentions] lists a villager's; none for the generic word (it isn't a name).
     */
    val names: Set<String> by lazy {
        if (generic) emptySet() else buildSet {
            add(name)
            for (c in CASES) if (c != "poss") forms[c]?.let(::add)
            forms["poss"]?.let { stem -> POSSESSIVE.forEach { add(stem + it) } }
        }
    }

    /** The name in case [case] ("" for the name itself), capitalised where a sentence starts when it is the generic word. */
    private fun form(case: String, whole: String, at: Int): String {
        val f = if (case.isEmpty()) name else forms[case] ?: name
        return if (generic && startsSentence(whole, at)) f.replaceFirstChar { it.uppercaseChar() } else f
    }

    companion object {
        /** The forms of a name besides its own: the Slovene cases (the vocative is the nominative) and the possessive's stem. */
        val CASES = listOf("gen", "dat", "acc", "loc", "ins", "poss")

        /** A possessive adjective's endings: Janov, Janova, Janovo, Janovi, Janove, Janovega … */
        private val POSSESSIVE = listOf("", "a", "o", "i", "e", "ega", "emu", "em", "im", "ih", "ima", "imi")

        private val CASE = "(?::(" + CASES.joinToString("|") + "))?"
        private val NAME = Regex("""\{learner$CASE\}""")
        private val BRANCH = """((?:[^\{\}|]|\{learner(?::(?:${CASES.joinToString("|")}))?\})*)"""
        private val PAIR = Regex("""\{([mf]):$BRANCH\|([mf]):$BRANCH\}""")

        /** What a sentence starts after: the end of one, a line break or an opening quote (a JSON string's own too). */
        private val STARTS = Regex("""(?:[.!?…]\s+|\n\s*|[«„"“(]\s*)$""")

        /** Whether a sentence starts at [at] of [text]: at its start (but for spaces), or after the end of one ([STARTS]). */
        fun startsSentence(text: String, at: Int): Boolean =
            text.substring(0, minOf(at, 8)).isBlank() && at <= 8 || STARTS.containsMatchIn(text.substring(maxOf(0, at - 8), at))

        /** The village's word for a friend, the name of a learner whose profile has none: a man's and a woman's. */
        private val FRIEND = mapOf("sl" to ("prijatelj" to "prijateljica"), "it" to ("amico" to "amica"), "de" to ("Freund" to "Freundin"), "en" to ("friend" to "friend"))

        /** A profile's text that is a template's placeholder ("{YOUR_NAME}") rather than a value. */
        private val TEMPLATE = Regex("""\{[^\{\}]*\}""")

        /**
         * The learner of a profile's [name], [gender] ("female", else male) and [forms] (learner.name_forms: any of the cases,
         * each overriding the rules'). Without a name, the village's ([language]) word for a friend.
         */
        fun of(name: String?, gender: String? = null, forms: Map<String, String>? = null, language: String = "sl"): Learner {
            val female = gender?.trim()?.lowercase() in setOf("f", "female")
            val given = name?.trim()?.takeIf { it.isNotEmpty() && !TEMPLATE.containsMatchIn(it) }
            val friend = FRIEND[language] ?: FRIEND.getValue("sl")
            val n = given ?: if (female) friend.second else friend.first
            val fs = slovene(n, female).toMutableMap()
            if (given != null) forms?.forEach { (k, v) -> if (k in CASES && v.isNotBlank() && !TEMPLATE.containsMatchIn(v)) fs[k] = v.trim() }
            return Learner(n, female, fs, generic = given == null)
        }

        /** [text] for [current]. */
        fun said(text: String): String = current.render(text)

        private fun soft(stem: String) = if (stem.endsWith("c")) stem.dropLast(1) + "č" else stem

        /**
         * The Slovene forms of first name [name] (the singular; a man's accusative is his genitive, the locative the
         * dative), as bridge/src/addressee.ts sloveneName makes them: a name in -a declines as Ana, Ane, Ani, Ano, Ani, Ano
         * (a man's too: Luka, Lukov); another woman's name stays as it is (Nives, Nivesin; Beti, Betin); a man's in -o Marko,
         * Marka, Markom; in -e Tone, Toneta; in -i, -u, -y Toni, Tonija, Tonijem; in a consonant Jan, Jana, Janu, Janom,
         * Janov, a soft one with -em and -ev (Nejc, Nejcem, Nejčev), with the fleeting e of Pavel, Karel, Peter, Aleksander
         * and the diminutives in -ek (Pavla, Petra, Tončka) and a j after another r (Igor, Igorja).
         */
        fun slovene(name: String, female: Boolean): Map<String, String> {
            val n = name.trim()
            fun all(f: String, poss: String) = mapOf("gen" to f, "dat" to f, "acc" to f, "loc" to f, "ins" to f, "poss" to poss)
            if (n.isEmpty()) return all(n, n)
            val last = n.last().lowercaseChar()
            if (last == 'a') {
                val s = n.dropLast(1)
                return mapOf("gen" to s + "e", "dat" to s + "i", "acc" to s + "o", "loc" to s + "i", "ins" to s + "o", "poss" to if (female) soft(s) + "in" else s + "ov")
            }
            if (female) return all(n, (if (last == 'i') n.dropLast(1) else n) + "in")
            fun man(stem: String, softStem: Boolean = false) = mapOf(
                "gen" to stem + "a", "dat" to stem + "u", "acc" to stem + "a", "loc" to stem + "u",
                "ins" to stem + if (softStem) "em" else "om", "poss" to if (softStem) soft(stem) + "ev" else stem + "ov",
            )
            if (last == 'o') return man(n.dropLast(1))
            if (last == 'e') return man(n + "t")
            if (last in "iuy") return man(n + "j", true)
            if (!LETTER_END.containsMatchIn(n)) return all(n, n)
            val fleeting = FLEETING.containsMatchIn(n) && !TWO_VOWELS.containsMatchIn(n)
            val stem = if (fleeting) n.dropLast(2) + n.last() else n
            if (stem.endsWith("r") && !fleeting) return man(stem + "j", true)
            return man(stem, SOFT_END.containsMatchIn(stem))
        }

        private val LETTER_END = Regex("[a-zčšžćđ]$", RegexOption.IGNORE_CASE)
        private val FLEETING = Regex("(?:[vr]el|[^aeiou]ek|(?:[aeiou]|s)ter|nder)$", RegexOption.IGNORE_CASE)
        private val TWO_VOWELS = Regex("[aeiou]{2}[a-z]$", RegexOption.IGNORE_CASE)
        private val SOFT_END = Regex("[cčšžj]$", RegexOption.IGNORE_CASE)

        // last: what they are made with is initialised above
        /** Nobody named yet: a man, the Slovene word for a friend. */
        val UNNAMED = of(null)

        /**
         * The learner of this phone (the learner profile's, [LearnerSetting]); [UNNAMED] until a profile is known. The unit
         * tests name theirs (the system property `lani.learner`, app/build.gradle.kts).
         */
        @Volatile
        var current: Learner = System.getProperty("lani.learner")?.let { of(it, System.getProperty("lani.learner.gender")) } ?: UNNAMED

        /** [s] as a JSON string's text (without its quotes). */
        private fun escape(s: String): String = buildString {
            for (c in s) when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
    }
}
