package si.lanisce.lani.game.villagers

import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.Happening
import si.lanisce.lani.game.scene.SceneSpec
import java.util.IdentityHashMap

/**
 * The people a text names, in one [language] (companion/VILLAGERS.md, "Who a text may name"): the villagers of [cast] (the
 * cast, the extras, and the people born here or who moved in, when the list has them) by their own name, as a sentence of
 * that language says it. In Slovene a name is declined ([Mentions.forms]): "Anton", "Antona", "Antonu", "z Antonom",
 * "Antonov med"; "Micka", "pri Micki", "Mickina kuhinja"; "Tone", "Toneta"; in German "Resis Küche"; with or without their
 * title ("Čebelar Anton", "Babica Micka"). Nobody else counts: the learner (their name in any form: l10n/Learner.kt), a word that is a name only
 * with its capital ("luka", a harbour; "rosa", pink), someone else with the same first name and a surname or a title of
 * their own ("France Prešeren", "Franceta Prešerna", "King Arthur", "Lepa Vida"; not in German, which capitalises every
 * noun). A cast member's own name is the last word of theirs ("Čebelar Anton" → Anton, "Mr Tyson" → Tyson); someone who
 * moved in or was born here goes by their first name ("Ana Furlan" → Ana).
 */
class Names(cast: List<Villager>, val language: String, learner: Set<String> = si.lanisce.lani.l10n.Learner.current.names) {
    /** Each form a name takes, and whose name it is (ids: two people may share a name). */
    private val forms: Map<String, Set<String>>

    /** A resident's family name ("Furlan"): their first name followed by it is them. */
    private val families: Map<String, String>

    /** Their names by id, as the cast has them ("Čebelar Anton"). */
    val names: Map<String, String>

    /** The other words of their name, their title ("Čebelar", "Stari"), as stems that its cases start with ("Staremu Janezu"). */
    private val titles: Map<String, List<String>>

    /**
     * German capitalises every noun: a capital next to a name there is no one else's surname or title ("Hat Florian
     * Hunger?", "die Kinder Lena und Maxi").
     */
    private val capitals = language != "de"

    init {
        val f = HashMap<String, MutableSet<String>>()
        val fam = HashMap<String, String>()
        val t = HashMap<String, List<String>>()
        val n = LinkedHashMap<String, String>()
        for (v in cast) {
            val own = Mentions.personal(v)
            if (own.length < 2 || !own[0].isUpperCase()) continue
            n[v.id] = v.name
            // the learner's own name is never someone of the village a text names (a newcomer of the same name aside)
            for (form in Mentions.forms(own, language)) if (form !in learner) f.getOrPut(form) { LinkedHashSet() } += v.id
            if (Mentions.resident(v)) v.name.trim().substringAfter(' ', "").substringAfterLast(' ').takeIf { it.isNotEmpty() }?.let { fam[v.id] = it }
            t[v.id] = v.name.trim().split(' ').filter { it.isNotEmpty() && it != own }.map { w -> if (w.length > 3) w.dropLast(1) else w }
        }
        forms = f
        families = fam
        titles = t
        names = n
    }

    /** The ids [text] names (in the order it names them); none for none. */
    fun of(text: String?): Set<String> {
        if (text.isNullOrEmpty() || forms.isEmpty()) return emptySet()
        var out: LinkedHashSet<String>? = null
        for (m in WORD.findAll(text)) {
            val who = forms[m.value] ?: continue
            if (capitals && (surnamed(text, m, who) || titled(text, m, who))) continue
            (out ?: LinkedHashSet<String>().also { out = it }).addAll(who)
        }
        return out ?: emptySet()
    }

    /** A word with a capital and a small letter: a name, a title ("Prešeren", "King"); not "OK", not "I". */
    private fun capital(w: String) = w.length >= 2 && w[0].isUpperCase() && w[1].isLowerCase()

    /**
     * Whether the name [m] of [who] is followed by a surname that isn't theirs ("France Prešeren", "Anton Janša"): one
     * space, then a capital word that is no one's name here.
     */
    private fun surnamed(text: String, m: MatchResult, who: Set<String>): Boolean {
        val end = m.range.last + 1
        if (end + 1 >= text.length || text[end] != ' ') return false
        val next = WORD.find(text, end + 1)?.takeIf { it.range.first == end + 1 }?.value ?: return false
        if (!capital(next) || next in forms) return false
        return who.none { families[it] == next }
    }

    /**
     * Whether the name [m] of [who] follows a title that isn't theirs, inside a sentence ("King Arthur", "Lepa Vida",
     * "Kralj Matjaž"): a capital word one space before it that no sentence starts with there, no one's name and no title of
     * theirs ("Čebelar Anton", "Staremu Janezu" are them).
     */
    private fun titled(text: String, m: MatchResult, who: Set<String>): Boolean {
        val start = m.range.first
        if (start < 2 || text[start - 1] != ' ') return false
        var j = start - 2
        while (j >= 0 && text[j].isLetter()) j--
        val prev = text.substring(j + 1, start - 1)
        if (!capital(prev) || prev in forms) return false
        var k = j
        while (k >= 0 && text[k] == ' ') k--
        if (k < 0 || text[k] in SENTENCE) return false
        return who.none { id -> titles[id].orEmpty().any { prev.startsWith(it) } }
    }

    private val memo = IdentityHashMap<Any, Set<String>>()

    /** What [key] (a dialog) names, worked out once for these people. */
    internal fun remember(key: Any, f: () -> Set<String>): Set<String> = synchronized(memo) {
        memo[key] ?: f().also { if (memo.size > 4096) memo.clear(); memo[key] = it }
    }

    companion object {
        private val WORD = Regex("\\p{L}+")

        /** What a sentence (or a quote, a line) starts after: a capital there is no title. */
        private const val SENTENCE = ".!?…:;«»\"„“”'(—–-\n"
    }
}

/**
 * Who a text may name (companion/VILLAGERS.md, "Who a text may name"): only people who are in the village and whom the
 * learner has met ([Residents.present]). A happening whose dialog names someone else is off ([si.lanisce.lani.game.scene
 * .Happenings.on]: Zala's "Anton toči med!" waits for Anton), and so is a request ([Residents.openQuests], [si.lanisce
 * .lani.game.Quests.refill]), a letter ([si.lanisce.lani.game.Surprises.roll]), a reading of the corner
 * ([si.lanisce.lani.game.Readings.corner]), a villager's line ([at]) and a level of an introduction
 * ([Arrivals.meeting]); another takes its place. The storyteller's legends name who they name (historical people).
 *
 * The app keeps [cast] (the villagers the node sent: VillagerController) and [known] (who is present and met now:
 * GameController, with the village); without a cast, or without a village, nothing is held back.
 */
object Mentions {
    /** The village's villagers (the cast and the extras, the tutor's too), as the node sent them: whose names texts may say. */
    @Volatile var cast: List<Villager> = emptyList()

    /** Who is in the village and met now ([Residents.present]); null: anyone may be named (no village, an older bridge). */
    @Volatile var known: Set<String>? = null

    /** The names made lately, by language and who they are (the same people give the same [Names], and what it worked out). */
    private val built = object : LinkedHashMap<String, Names>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Names>?) = size > 12
    }

    /** The names of [cast] in [language] (kept for the same people). */
    fun names(language: String, cast: List<Villager> = this.cast): Names {
        val key = cast.joinToString("|", "$language|${si.lanisce.lani.l10n.Learner.current.name}|") { "${it.id}:${it.name}:${resident(it)}" }
        return synchronized(built) { built.getOrPut(key) { Names(cast, language) } }
    }

    /** Someone born here or who moved in (their name is "first family"), not one of the cast (a title, then the name). */
    fun resident(v: Villager): Boolean = v.source == "village" || v.id.startsWith("n-")

    /** The name a text calls [v] by: the last word of a cast member's ("Čebelar Anton" → Anton), a resident's first name. */
    fun personal(v: Villager): String {
        val name = v.name.trim()
        return if (resident(v)) name.substringBefore(' ') else name.substringAfterLast(' ')
    }

    /** The forms [name] takes in a sentence of [language]: Slovene declines it ([slovene]), German adds a genitive -s. */
    fun forms(name: String, language: String): Set<String> = when (language) {
        "sl" -> slovene(name)
        "de" -> setOf(name, name + "s")
        else -> setOf(name)
    }

    /** A possessive adjective's endings: Antonov, Antonova, Antonovo, Antonovi, Antonove, Antonovega … */
    private val POSSESSIVE = listOf("", "a", "o", "i", "e", "ega", "emu", "em", "im", "ih", "ima", "imi")

    /**
     * A Slovene name, declined (singular) and as a possessive adjective: Micka, Micke, Micki, Micko, Mickin(a …); Luka,
     * Luke, Luki, Luko, Lukov; Marko, Marka, Marku, Markom, Markov; Tone, Toneta, Tonetu, Tonetom, Tonetov; Anton, Antona,
     * Antonu, Antonom, Antonov; Nejc, Nejca, Nejcu, Nejcem, Nejčev; Mojca, Mojčin; Toni, Tonija, Tonijev.
     */
    fun slovene(name: String): Set<String> {
        val out = linkedSetOf(name)
        fun cases(stem: String, vararg endings: String) = endings.forEach { out += stem + it }
        fun possessive(stem: String, suffix: String) = POSSESSIVE.forEach { out += stem + suffix + it }
        fun soft(stem: String) = if (stem.endsWith("c")) stem.dropLast(1) + "č" else stem
        val last = name.last().lowercaseChar()
        when {
            last == 'a' -> name.dropLast(1).let { s -> cases(s, "a", "e", "i", "o", "ama", "ami", "ah"); possessive(soft(s), "in"); possessive(s, "ov") }
            last == 'o' -> name.dropLast(1).let { s -> cases(s, "a", "u", "om"); possessive(s, "ov") }
            last == 'e' -> (name + "t").let { s -> cases(s, "a", "u", "om"); possessive(s, "ov") }
            last in "cčšžj" -> { cases(name, "a", "u", "em"); possessive(soft(name), "ev") }
            last in "iuy" -> (name + "j").let { s -> cases(s, "a", "u", "em"); possessive(s, "ev") }
            else -> { cases(name, "a", "u", "om"); possessive(name, "ov") }
        }
        return out
    }

    /** What is said of a "target · base" text: the part before the dot. */
    fun said(text: String?): String? = text?.substringBefore(" · ")

    /** Whether everyone in [named] is someone [known] (present and met; null: anyone). */
    fun known(named: Collection<String>, known: Set<String>?): Boolean = known == null || named.all { it in known }

    /**
     * The people [d] names: its lines, the learner's choices and the replies to them (what is said, as the app reads it),
     * and what the person remembers of it (a variant's own memory).
     */
    fun inDialog(d: Dialog?, names: Names): Set<String> {
        if (d == null) return emptySet()
        return names.remember(d) {
            val out = LinkedHashSet<String>()
            for (l in d.lines) {
                out += names.of(l.sl)
                for (c in l.choices) { out += names.of(c.sl); out += names.of(c.reply?.sl) }
            }
            out += names.of(d.memory?.sl)
            out
        }
    }

    /** The people happening [h] names besides its dialogs: its title (what is said) and its memory. */
    fun inTitle(h: Happening, names: Names): Set<String> = names.of(said(h.title)) + names.of(h.memory?.sl)

    /**
     * The people happening [h] of [scene] names: its dialogs (all its variants: [DialogVariants.of]), its title (what is
     * said) and its memory. A storyteller's legends aren't among them (their people are history).
     */
    fun inHappening(scene: SceneSpec, h: Happening, names: Names = names(scene.language)): Set<String> {
        val dialogs = si.lanisce.lani.game.scene.DialogVariants.of(h).mapNotNull { id -> scene.dialogs.firstOrNull { it.id == id } }
        return inTitle(h, names) + dialogs.flatMap { inDialog(it, names) }
    }

    /** The people a request names: its title and its story (what is said). */
    fun inQuest(q: Quest, names: Names): Set<String> = names.of(said(q.title)) + names.of(said(q.story))

    /** Whether a villager's [line] may be said now: it names only people the learner knows ([known]); any line without a village. */
    fun sayable(line: VillagerLine): Boolean {
        val k = known ?: return true
        val c = cast
        if (c.isEmpty()) return true
        return names(line.spokenIn(), c).of(line.target).all { it in k }
    }
}
