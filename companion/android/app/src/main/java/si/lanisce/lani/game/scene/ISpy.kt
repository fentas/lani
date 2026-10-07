package si.lanisce.lani.game.scene

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.data.json
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.PlayReviews
import si.lanisce.lani.game.villagers.Villager
import java.time.LocalDate
import kotlin.random.Random

/** A line of «Vidim, vidim» in the learner's pair: what is said ([sl], in the scene's language) and what it means ([en], in their base). */
data class ISpyLine(val sl: String, val en: String = "") {
    /** With [word] for {word} where it is said and [meaning] where it is meant, and [letter] for {letter}. */
    fun with(word: String = "", meaning: String = word, letter: String = ""): ISpyLine =
        ISpyLine(sl.replace("{word}", word).replace("{letter}", letter), en.replace("{word}", meaning).replace("{letter}", letter))
}

/**
 * A clue: what the child says ([line]), what kind of clue it is ([kind], [ISpy.KINDS]: the vaguer ones are said first),
 * the grammar book's pages it needs ([grammar]: one not introduced to the learner yet keeps it back, as the dialogs' turns
 * do), and the thing of the same scene it names ([ref]: "Leži na mizi." is only said while the table is in the picture).
 */
data class ISpyClue(val kind: String, val line: ISpyLine, val grammar: List<String> = emptyList(), val ref: String? = null)

/**
 * A thing of a scene to spy, by its object [slot]: its [clues] (in the file's order, the simpler before the ones that need
 * more grammar); [plural]: its word is a plural ("vrata", "vilice"); [meaning]: what it means where the word's own meaning
 * isn't one plain word ("ura": "hour, clock").
 */
data class ISpyThing(val slot: String, val clues: List<ISpyClue>, val plural: Boolean = false, val meaning: String? = null)

/** A scene's clues (companion/ispy/scenes/<scene>.json, `lani.ispy/v0`), read in the learner's pair. */
data class ISpyBook(val scene: String, val language: String, val things: Map<String, ISpyThing>)

/**
 * What the child says in a language (companion/ispy/<language>.json, `lani.ispy-lines/v0`), read in the learner's pair:
 * the offer, the game's opening and a round's, the reactions to a wrong tap (far from the thing: [cold]; near it:
 * [warm]), the find and the reveal (one for a singular word, one for a plural), the end (all found: [endAll]), the
 * picture-free clues of a thing without its own ([letter]: its first letter; [letters]: how many letters, with the forms
 * that agree with the number, `{n}` and `{a|b|c|d}` as a counting dialog has them) and what the child remembers.
 */
data class ISpyLines(
    val language: String,
    val offer: ISpyLine,
    val start: ISpyLine,
    val again: ISpyLine,
    val cold: List<ISpyLine>,
    val warm: List<ISpyLine>,
    val found: ISpyLine,
    val foundPlural: ISpyLine,
    val show: ISpyLine,
    val showPlural: ISpyLine,
    val end: ISpyLine,
    val endAll: ISpyLine,
    val letter: ISpyLine,
    val letters: ISpyLine? = null,
    val memory: ISpyLine? = null,
)

/** A round: the thing spied ([slot]), its word as the scene says it and what it means, and the clues to give, in order. */
data class ISpyRound(val slot: String, val word: String, val meaning: String, val plural: Boolean, val clues: List<ISpyClue>)

/** Who plays I spy with the learner: [person] as the scene shows them; [comes]: they came by (no person of the scene's own). */
data class ISpyHost(val person: ScenePerson, val comes: Boolean = false)

/** «Vidim, vidim» played on day [on]: by scene ([scenes]), and the children who played it ([children]: their friendship grew today). */
@Serializable
data class ISpyToday(val on: String, val scenes: Map<String, ISpyPlayed> = emptyMap(), val children: List<String> = emptyList())

/** The games played in a scene today and the things spied in them (the next game spies others). */
@Serializable
data class ISpyPlayed(val games: Int = 0, val things: List<String> = emptyList())

/**
 * «Vidim, vidim nekaj, česar ti ne vidiš» (I spy, companion/SCENES.md "I spy"): a child of the village picks a thing in the
 * scene's picture and gives clues one at a time; the learner taps the thing in the picture. Pure: the content, who plays,
 * which things and which clues, and what the day keeps. The game being played is [si.lanisce.lani.ui.scene.ISpyRun].
 */
object ISpy {
    /** Games a day in each scene: a treat, not a drill. */
    const val GAMES_PER_DAY = 2

    /** Rounds in a game (fewer where fewer things can be spied). */
    const val ROUNDS = 3

    /** Clues at most in a round; after the last one the child shows the thing. */
    const val MAX_CLUES = 4

    /** A find within this many clues is quick: a right answer, and a review of the word's card ([PlayReviews]). */
    const val QUICK = 2

    /** A wrong tap this near the thing (canvas pixels between their areas) is warm, else cold. */
    const val WARM_PX = 14

    /** The kinds of clue, in the order a round gives them: what it looks like first, where it is, then what it does or is for. */
    val KINDS = listOf("colour", "size", "trait", "material", "compare", "where", "does", "use")

    /** The sprites of the village's children: who plays I spy. */
    fun isChild(art: String): Boolean = art.startsWith("child")

    // --- the content -----------------------------------------------------------------------------------------------

    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    /** A text object of [language] read in the learner's [base]: what is said, and what it means (the base's, else English). */
    private fun line(o: JsonObject?, language: String, base: String): ISpyLine? {
        o ?: return null
        val said = str(o, language) ?: return null
        val meant = (if (base != language) str(o, base) else null) ?: str(o, "en").takeIf { language != "en" } ?: str(o, base) ?: ""
        return ISpyLine(said, meant)
    }

    /** A scene's clues from their file ([raw], lani.ispy/v0), in the learner's [base]; null when it doesn't read. */
    fun parseBook(raw: String, base: String): ISpyBook? = runCatching {
        val o = json.parseToJsonElement(raw) as JsonObject
        val scene = str(o, "scene") ?: return null
        val language = str(o, "language") ?: "sl"
        val things = (o["things"] as? JsonObject).orEmpty().mapNotNull { (slot, t) ->
            val thing = t as? JsonObject ?: return@mapNotNull null
            val clues = (thing["clues"] as? JsonArray).orEmpty().mapNotNull { c ->
                val co = c as? JsonObject ?: return@mapNotNull null
                val l = line(co, language, base) ?: return@mapNotNull null
                ISpyClue(
                    str(co, "kind") ?: "trait", l,
                    (co["grammar"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                    str(co, "ref"),
                )
            }
            val meaning = (thing["name"] as? JsonObject)?.let { n -> str(n, base) ?: str(n, "en") }
            slot to ISpyThing(slot, clues, str(thing, "number") == "pl", meaning)
        }.toMap()
        ISpyBook(scene, language, things)
    }.getOrNull()

    /** The child's lines in a language from their file ([raw], lani.ispy-lines/v0), in the learner's [base]; null when they don't read. */
    fun parseLines(raw: String, base: String): ISpyLines? = runCatching {
        val o = json.parseToJsonElement(raw) as JsonObject
        val lang = str(o, "language") ?: return null
        fun one(k: String) = line(o[k] as? JsonObject, lang, base)
        fun many(k: String) = (o[k] as? JsonArray).orEmpty().mapNotNull { line(it as? JsonObject, lang, base) }
        fun of(k: String, n: String) = line((o[k] as? JsonObject)?.get(n) as? JsonObject, lang, base)
        ISpyLines(
            lang, one("offer")!!, one("start")!!, one("again")!!, many("cold").ifEmpty { return null }, many("warm").ifEmpty { many("cold") },
            of("found", "sg")!!, of("found", "pl") ?: of("found", "sg")!!, of("show", "sg")!!, of("show", "pl") ?: of("show", "sg")!!,
            one("end")!!, one("end_all") ?: one("end")!!, one("letter")!!, one("letters"), one("memory"),
        )
    }.getOrNull()

    // --- the day: games left in a scene, the children played with -------------------------------------------------

    /** What [s] kept of today's games ([ISpyToday]); an empty day when it kept another day's. */
    fun today(s: GameState?, today: LocalDate): ISpyToday =
        s?.ispy?.takeIf { it.on == today.toString() } ?: ISpyToday(today.toString())

    /** The games left today in scene [scene] ([GAMES_PER_DAY] a day). */
    fun gamesLeft(s: GameState?, scene: String, today: LocalDate): Int =
        (GAMES_PER_DAY - (today(s, today).scenes[scene]?.games ?: 0)).coerceAtLeast(0)

    /** The things already spied today in [scene]: the next game spies others. */
    fun spied(s: GameState?, scene: String, today: LocalDate): Set<String> = today(s, today).scenes[scene]?.things.orEmpty().toSet()

    /** Whether the friendship with [child] grows with a game today: the first game with them of the day. */
    fun befriends(s: GameState?, child: String, today: LocalDate): Boolean = child !in today(s, today).children

    /** [s] with a game played in [scene] today, its [things] spied, with [child] (a villager id, or null). */
    fun played(s: GameState, scene: String, things: Collection<String>, child: String?, today: LocalDate): GameState {
        val d = today(s, today)
        val was = d.scenes[scene] ?: ISpyPlayed()
        val now = ISpyPlayed(was.games + 1, (was.things + things).distinct())
        return s.copy(ispy = d.copy(scenes = d.scenes + (scene to now), children = (d.children + listOfNotNull(child)).distinct()))
    }

    // --- who plays --------------------------------------------------------------------------------------------------

    /**
     * Who plays with the learner in [scene] now: a child of the scene who is in the picture ([drawn], not asleep), else a
     * child of the village who comes by ([children]: the village's children who are here and met, awake, not busy with a
     * happening of their own elsewhere; the day's dice picks one, the same all day in the scene) to a person spot of the
     * scene's art nobody stands at. Null: no child to play with (all asleep, none in the village, no room).
     */
    fun host(
        scene: SceneSpec, drawn: List<PersonInScene>, children: List<Villager>, seed: Long, today: LocalDate, cast: (String) -> Villager? = { null },
    ): ISpyHost? {
        val here = drawn.filter { it.pose != Pose.SLEEP }
        here.firstNotNullOfOrNull { p -> scene.people.firstOrNull { it.id == p.id && isChild(it.art) } }?.let { p ->
            // "Otroci" by the fire are Nejc and his friends: the child who plays is the villager
            val v = p.villager?.let(cast)
            return ISpyHost(if (v != null) p.copy(name = v.name, emoji = v.emoji) else p)
        }
        val taken = here.map { it.slot }.toSet()
        val spots = SceneArt.personSlots[scene.art].orEmpty().filter { it !in taken }
        val spot = spots.firstOrNull { it == "door" } ?: spots.lastOrNull() ?: return null
        val kids = children.filter { isChild(it.art) && here.none { p -> p.id == it.id } }.sortedBy { it.id }
        if (kids.isEmpty()) return null
        val v = kids[(Happenings.roll(seed, today, "ispy/${scene.id}") * kids.size).toInt().coerceIn(0, kids.lastIndex)]
        return ISpyHost(ScenePerson(v.id, v.name, v.emoji, v.art, spot, villager = v.id), comes = true)
    }

    // --- which things, which clues -----------------------------------------------------------------------------------

    /**
     * The clues of [thing] the child gives now: those whose pages are all introduced to the learner ([notYet] says which
     * aren't) and whose thing they name is in the picture ([shown]); of each kind the last of the file's (the one that
     * needs the most grammar the learner has: "Je iz lesa." once rodilnik-predlogi is theirs, "Je lesen." before), in
     * the order of [KINDS], at most [MAX_CLUES] (with more kinds, the first ones and the last). Picture-free ones
     * ([fallback]) make up a thing with fewer than two.
     */
    fun clues(thing: ISpyThing?, notYet: (String) -> Boolean, shown: (String) -> Boolean, fallback: List<ISpyClue> = emptyList()): List<ISpyClue> {
        val byKind = LinkedHashMap<String, ISpyClue>()
        for (c in thing?.clues.orEmpty()) if (c.kind in KINDS && c.grammar.none(notYet) && (c.ref == null || shown(c.ref))) byKind[c.kind] = c
        // more kinds than a round gives: the vaguest ones and the last, the most telling (what it does, what it is for)
        val all = KINDS.mapNotNull { byKind[it] }
        val own = if (all.size > MAX_CLUES) all.take(MAX_CLUES - 1) + all.last() else all
        return if (own.size >= 2) own else own + fallback.take(2 - own.size)
    }

    /**
     * The picture-free clues of a thing whose [word] (in [language]) has none of its own, or too few: the word's first
     * letter, and, for a word of one piece, how many letters it has (`{n}` in words where it is said, in figures where it is
     * meant; the forms that agree with it).
     */
    fun fallback(word: String, language: String, lines: ISpyLines): List<ISpyClue> {
        val bare = word.substringAfter('\'').substringAfterLast(' ')
        val first = bare.firstOrNull { it.isLetter() } ?: return emptyList()
        val out = mutableListOf(ISpyClue("letter", lines.letter.with(letter = first.uppercase())))
        val n = word.count { it.isLetter() }
        lines.letters?.takeIf { word.trim().none { it == ' ' } && n in 2..20 }?.let { l ->
            val said = counted(l.sl, n, Numbers.words(n, language, "f", "acc"), if (language == "sl") 4 else 2)
            val meant = counted(l.en, n, n.toString(), 2)
            out += ISpyClue("letters", ISpyLine(said, meant))
        }
        return out
    }

    /** [text] with [shown] for {n} and, of each `{a|b…}`, the form [n] takes among [forms] ([Numbers.category]). */
    private fun counted(text: String, n: Int, shown: String, forms: Int): String =
        Regex("""\{([^\{\}|]+(?:\|[^\{\}|]+)+)\}""").replace(text.replace("{n}", shown)) { m ->
            val f = m.groupValues[1].split('|')
            f[Numbers.category(n, f.size).coerceAtMost(f.lastIndex)]
        }

    /**
     * How much the child wants to spy the thing of the learner's [card] (null: they have none), [found] before or not:
     * a card due or nearly due ([PlayReviews.due]) most, one still being learned next, then a thing found before, a
     * word known well, and last a thing never found.
     */
    fun weight(card: ReviewCard?, found: Boolean, today: LocalDate): Int = when {
        card != null && PlayReviews.due(card, today) -> 8
        card != null && ReviewPlanner.familiarity(card) < 2 -> 5
        found -> 3
        card != null -> 2
        else -> 1
    }

    /**
     * A game's rounds in [scene] (in its world now): of the things in the picture now ([visible]) that have two clues to
     * give, not spied today already ([used]; when that leaves too few, any), up to [ROUNDS] picked by [weight] with
     * [random]; [first]: the thing of the first round (QA's hook). [book]: the scene's clues; [lines]: the child's, for a
     * thing's picture-free clues; [card]: the learner's card of a thing's word; [found]: the things found before.
     */
    fun rounds(
        scene: SceneSpec, book: ISpyBook?, lines: ISpyLines, visible: Set<String>, notYet: (String) -> Boolean,
        card: (SceneObject) -> ReviewCard?, found: Set<String>, used: Set<String>, today: LocalDate, random: Random,
        first: String? = null, count: Int = ROUNDS,
    ): List<ISpyRound> {
        val things = scene.objects.distinctBy { it.slot }.filter { it.slot in visible }.mapNotNull { o ->
            val t = book?.things?.get(o.slot)
            val word = o.sl.ifBlank { o.word }
            val cs = clues(t, notYet, { it in visible }, fallback(word, scene.language, lines))
            if (cs.size < 2) null else ISpyRound(o.slot, word, t?.meaning ?: meaningOf(o.en), t?.plural == true, cs) to o
        }
        if (things.isEmpty()) return emptyList()
        val fresh = things.filter { it.first.slot !in used }.takeIf { it.size >= minOf(count, things.size) } ?: things
        val pool = fresh.map { (r, o) -> r to weight(card(o), o.slot in found, today) }.toMutableList()
        val out = ArrayList<ISpyRound>()
        first?.let { f -> things.firstOrNull { it.first.slot == f }?.let { out += it.first; pool.removeAll { p -> p.first.slot == f } } }
        while (out.size < count && pool.isNotEmpty()) {
            var x = random.nextInt(pool.sumOf { it.second })
            val i = pool.indexOfFirst { x -= it.second; x < 0 }.coerceAtLeast(0)
            out += pool.removeAt(i).first
        }
        return out
    }

    /** A word's meaning as a round says it: its first sense, without notes ("hour, clock, o'clock" → "hour"). */
    fun meaningOf(en: String): String = en.substringBefore(',').substringBefore(';').replace(Regex("""\s*\([^)]*\)"""), "").trim().ifEmpty { en }

    /** The day's dice for a game: the village, the day, the scene and how many were played there today (the same game after a restart). */
    fun random(seed: Long, today: LocalDate, scene: String, game: Int): Random =
        Random((Happenings.roll(seed, today, "ispy/$scene#$game") * Int.MAX_VALUE).toLong() xor seed)
}
