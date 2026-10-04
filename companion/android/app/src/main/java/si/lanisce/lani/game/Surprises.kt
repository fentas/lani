package si.lanisce.lani.game

import si.lanisce.lani.data.Exercise
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.SurprisesFile
import si.lanisce.lani.game.culture.localized
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.StoryBooks
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Mentions
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * "Dnevno presenečenje · The day's surprise" at the road (companion/GAME.md): one a day from Tabor, rolled from the
 * village's seed and the date, so it's the same all day and on every phone. Short and optional: the pedlar with a
 * rare good to buy, the postman with a letter (reading), the shepherd's lost lamb (listening), a pilgrim asking the way
 * (directions), a child with a riddle. The people in it live here (or visit today). The kinds, their odds and what they
 * pay are the game's; the letters, the ways, the riddles, the strangers and every line are the culture's
 * (game/culture, surprises.json: in Primorska a letter from Gorizia, Luka's lamb, the pilgrim to Sveta Gora). Pure, like
 * the engine.
 */
object Surprises {
    const val PEDLAR = "pedlar"
    const val LETTER = "letter"
    const val LAMB = "lamb"
    const val PILGRIM = "pilgrim"
    const val RIDDLE = "riddle"
    val KINDS = listOf(PEDLAR, LETTER, LAMB, PILGRIM, RIDDLE)

    /** Jan's own letter (always possible): the addressee "jan". */
    const val JAN = "jan"

    /** Whose lamb it is: the culture's shepherd (Luka in Primorska). */
    val SHEPHERD: String get() = pack.lamb.shepherd

    /** What a surprise pays: this much of what the village is shortest of at the campfire, growing with the age. */
    const val REWARD = 12
    /** 🤝 for helping (reading someone their letter, finding the lamb, showing the way …). */
    const val HELP = 1
    /** ♥ with the villager it was about (the lamb's shepherd, the child); the letter's addressee a little less. */
    const val POINTS = 5
    const val LETTER_POINTS = 3

    private val pack: SurprisesFile get() = Cultures.current.surprises

    /** A letter: to [to] ([toAcc] after "for"; [female] for "her"/"him"), its [text], and two questions ("target · base"). */
    data class Letter(val to: String, val toAcc: String, val text: String, val questions: List<Question>, val female: Boolean = false)

    /**
     * A question with its answer first (the options are shuffled), and what it means; [grammar]: the grammar book's page of
     * its rule (surprises.json), which answering it unlocks.
     */
    data class Question(val ask: String, val options: List<String>, val explain: String, val grammar: String? = null)

    /** A riddle: in the target language, the answer first among the options, and the base language's answer and riddle. */
    data class Riddle(val sl: String, val options: List<String>, val en: String)

    /** The letters the postman brings, in the learner's pair of the moment. */
    val letters: List<Letter>
        get() = pack.letter.letters.map { l ->
            Letter(
                l.to, l.toName?.target.orEmpty(), l.text.target,
                l.questions.map { q -> Question(q.ask.bi(), q.options, q.explain.bi(), q.grammar) }, l.female,
            )
        }

    /** A pilgrim asks the way: what to say (in the base language), the right words first, and their glosses. */
    val directions: List<Question>
        get() = pack.pilgrim.ways.map { w -> Question(w.say.base, w.options, w.explain.joinToString(", ") { it.bi() }, w.grammar) }

    /** Where the shepherd hears the lamb: in the target language, in the base language. */
    val lambPlaces: List<Pair<String, String>> get() = pack.lamb.places.map { it.target to it.base }

    val riddles: List<Riddle>
        get() = pack.riddle.riddles.map { r -> Riddle(r.riddle.target, r.options, "${r.answer.base} · ${r.meaning.base}") }

    /**
     * The strangers of the day's surprise, drawn and staged like villagers (their own sprites: [Villager.art]) but never
     * living here, never friends: in Primorska the krošnjar from Ribnica, who sells from his krošnja, and a pilgrim on
     * his way to Sveta Gora. They wait at the road ("spot:road") while their surprise is on. They say vi to Jan, a
     * stranger.
     */
    val strangers: Map<String, Villager> get() = pack.strangers

    /**
     * The stranger at the road [today], to draw there: the pedlar all his day, the pilgrim until he's been shown the way
     * (his surprise done). Null on other days.
     */
    fun stranger(s: GameState, today: LocalDate): Villager? = today(s, today)?.takeIf { !it.done }?.let { strangers[it.kind] }

    /** The children in the village today (the cast's and those born here), ids; null = everyone (no names yet). */
    private fun children(s: GameState, today: LocalDate): List<String>? {
        if (s.residents.isEmpty()) return null
        val present = Residents.present(s, today).orEmpty()
        val cast = Catalog.children.filter { it in present }.sorted()
        val born = s.residents.filter { r ->
            r.born != null && Residents.stage(r, today).let { it == Residents.Stage.CHILD || it == Residents.Stage.YOUTH }
        }.map { it.id }.sorted()
        return cast + born
    }

    /** Rolls [today]'s surprise: from Tabor, one a day, from the seed and the date (who it's about lives here). */
    fun roll(s: GameState, today: LocalDate): Surprise? {
        if (s.age < Age.TABOR) return null
        val present = Residents.present(s, today)
        fun here(id: String) = present == null || id in present
        val kids = children(s, today) ?: listOf(pack.riddle.child)
        val all = pack.letter.letters
        // a letter for someone here, naming only people the learner knows (Mentions)
        val lang = Cultures.current.manifest.language
        val names = Mentions.names(lang)
        val said = si.lanisce.lani.l10n.Lang.of(lang) ?: si.lanisce.lani.l10n.L10n.pair.target
        val mail = all.filter { (it.to == JAN || here(it.to)) && Mentions.known(names.of(it.text.of(said)), present) }
        val kinds = buildList {
            add(PEDLAR to 2)
            if (mail.isNotEmpty()) add(LETTER to 2)
            add(PILGRIM to 2)
            if (here(SHEPHERD)) add(LAMB to 1)
            if (kids.isNotEmpty()) add(RIDDLE to 2)
        }
        var x = Happenings.roll(s.seed, today, "surprise") * kinds.sumOf { it.second }
        val kind = kinds.firstOrNull { (_, w) -> x -= w; x < 0f }?.first ?: kinds.last().first
        val v = (Happenings.roll(s.seed, today, "surprise/variant") * 997).toInt()
        val day = today.toString()
        return when (kind) {
            LETTER -> mail[v % mail.size].let { l -> Surprise(day, LETTER, all.indexOf(l), l.to.takeIf { it != JAN }) }
            LAMB -> Surprise(day, LAMB, v, SHEPHERD)
            RIDDLE -> Surprise(day, RIDDLE, v, kids[v % kids.size])
            else -> Surprise(day, kind, v)
        }
    }

    /** Today's surprise, when one was rolled for today. */
    fun today(s: GameState, today: LocalDate): Surprise? = s.surprise?.takeIf { it.on == today.toString() && it.kind in KINDS }

    /** Whether who it's about is in the village today (the pedlar, the postman and the pilgrim come by themselves). */
    fun whoHere(s: GameState, sp: Surprise, today: LocalDate): Boolean =
        sp.who == null || Residents.present(s, today)?.contains(sp.who) ?: true

    /** Today's surprise when it can still be played: rolled for today, not done, not the pedlar (he sells), its person here. */
    fun playable(s: GameState, today: LocalDate): Surprise? =
        today(s, today)?.takeIf { !it.done && it.kind != PEDLAR && whoHere(s, it, today) }

    fun emoji(kind: String): String = when (kind) {
        PEDLAR -> pack.pedlar.emoji
        LETTER -> pack.letter.emoji
        LAMB -> pack.lamb.emoji
        PILGRIM -> pack.pilgrim.emoji
        else -> pack.riddle.emoji
    }

    /** "🎒 Krošnjar · The pedlar". */
    fun title(sp: Surprise): String = emoji(sp.kind) + " " + when (sp.kind) {
        PEDLAR -> pack.pedlar.title
        LETTER -> pack.letter.title
        LAMB -> pack.lamb.title
        PILGRIM -> pack.pilgrim.title
        else -> pack.riddle.title
    }.bi()

    /** The day's surprise in a line, "target · base" (names from the village: [name] of a villager id). */
    fun intro(sp: Surprise, name: (String) -> String): String = when (sp.kind) {
        PEDLAR -> pack.pedlar.intro.bi()
        LETTER -> letterOf(sp).let { l ->
            val e = pack.letter.letters[Math.floorMod(sp.variant, pack.letter.letters.size)]
            // after "for": the letter's own form where the language needs one ("za Marka"), else the name ("for Marko")
            val to = localized { lang -> e.toName?.by?.get(lang.code) ?: name(l.to) }
            if (l.to == JAN) pack.letter.introMine.bi()
            else pack.letter.intro.bi("to" to to, "name" to name(l.to), "g" to if (l.female) "f" else "m")
        }
        LAMB -> pack.lamb.intro.bi()
        PILGRIM -> pack.pilgrim.intro.bi()
        else -> pack.riddle.intro.bi("name" to name(sp.who ?: pack.riddle.child))
    }

    /**
     * What the one on the stage says to start it, "target · base": who it's about in their own words (the shepherd,
     * the child, the letter's addressee, the pilgrim himself), else today's companion about Jan's letter.
     */
    fun ask(sp: Surprise): String = when (sp.kind) {
        LETTER -> if (letterOf(sp).to == JAN) pack.letter.askMine.bi() else pack.letter.ask.bi()
        LAMB -> pack.lamb.ask.bi()
        PILGRIM -> pack.pilgrim.ask.bi()
        RIDDLE -> pack.riddle.ask.bi()
        else -> pack.pedlar.ask.bi()
    }

    fun letterOf(sp: Surprise): Letter = letters.let { it[Math.floorMod(sp.variant, it.size)] }

    /** The skill a surprise trains (and pays in). */
    fun skill(kind: String): Res = when (kind) {
        LAMB -> Res.WOOD
        RIDDLE -> Res.FOOD
        else -> Res.WISDOM
    }

    /** The surprise's run: a letter's two questions, three of the pilgrim's ways, three of the shepherd's calls, two riddles. */
    fun exercises(sp: Surprise, seed: Long, canSpeak: Boolean): List<Exercise> {
        val r = rng(seed, "surprise", sp.on, sp.kind, sp.variant)
        // [spoken]: the options are in the village's language, each with its 🔊 (the lamb's places are in the base's);
        // [grammar]: the grammar book's page of the question's rule
        fun choice(prompt: String, options: List<String>, explain: String?, instruction: String, audio: String? = null, spoken: Boolean = true, grammar: String? = null): Exercise {
            val opts = options.shuffled(r)
            return Exercise.Choice(prompt, opts, opts.indexOf(options.first()), explain = explain, audio = audio, instruction = instruction, sayOptions = spoken, grammar = grammar)
        }
        val e = emoji(sp.kind)
        return when (sp.kind) {
            // the letter is read first and hidden while its questions are asked ([text]): they ask about it alone
            LETTER -> letterOf(sp).let { l ->
                l.questions.map { q -> choice("$e ${q.ask}", q.options, q.explain, pack.letter.title.bi(), grammar = q.grammar) }
            }
            PILGRIM -> {
                val ways = pack.pilgrim.ways
                directions.withIndex().shuffled(r).take(3).map { (i, q) ->
                    choice("$e ${pack.pilgrim.prompt.bi("say" to ways[i].say)}", q.options, q.explain, pack.pilgrim.show.bi(), grammar = q.grammar)
                }
            }
            LAMB -> lambPlaces.shuffled(r).take(3).map { (said, meant) ->
                val others = lambPlaces.map { it.second }.filter { it != meant }.shuffled(r).take(3)
                if (canSpeak) choice(pack.lamb.where.bi(), listOf(meant) + others, said, pack.lamb.listen.bi(), audio = said, spoken = false)
                else choice("$e ${pack.lamb.heard.bi("said" to said)}", listOf(meant) + others, said, pack.lamb.read.bi(), spoken = false)
            }
            RIDDLE -> riddles.shuffled(r).take(2).map { q ->
                choice("$e »${q.sl}«", q.options, "${q.options.first()} · ${q.en}", pack.riddle.title.bi())
            }
            else -> emptyList()
        }
    }

    /**
     * What the run of [sp] is about, read first (companion/GAME.md, "Read first, then answer"): a letter's text, its
     * lines as paragraphs of sentences, each with its 🔊; the letter is hidden while its questions are asked, and "👁
     * Pokaži besedilo · Show the text" brings it back. Null for the other surprises: their questions carry what they ask.
     */
    fun text(sp: Surprise): ReadFirst? {
        if (sp.kind != LETTER) return null
        val paragraphs = letterOf(sp).text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { StoryBooks.sentences(it) }
        return ReadFirst("${pack.letter.emoji} ${pack.letter.read.bi()}", paragraphs)
    }

    /**
     * The letters' questions and the pilgrim's ways that name a grammar book page, as their surprise asks them (the options
     * shuffled with [seed]): "🎯 Vadi · Practise" on a page plays those that name it, like the tent challenge's questions.
     */
    fun onRules(seed: Long): List<Exercise> {
        val r = rng(seed, "surprise", "rules")
        fun choice(prompt: String, q: Question, instruction: String): Exercise {
            val opts = q.options.shuffled(r)
            return Exercise.Choice(prompt, opts, opts.indexOf(q.options.first()), explain = q.explain, instruction = instruction, sayOptions = true, grammar = q.grammar)
        }
        val ways = pack.pilgrim.ways
        return letters.flatMap { l -> l.questions.filter { it.grammar != null }.map { q -> choice("${pack.letter.emoji} ${l.text}\n\n${q.ask}", q, pack.letter.read.bi()) } } +
            directions.withIndex().filter { it.value.grammar != null }.map { (i, q) -> choice("${pack.pilgrim.emoji} ${pack.pilgrim.prompt.bi("say" to ways[i].say)}", q, pack.pilgrim.show.bi()) }
    }

    fun passMark(total: Int): Int = ceil(total * 0.5).toInt().coerceAtLeast(1)

    /** What a surprise pays: [REWARD] of what the village is shortest of (a request's side reward), growing with the age. */
    fun reward(s: GameState): Map<Res, Int> {
        val caps = GameEngine.attributes(s).caps
        val want = Res.entries.minBy { s.res(it).toFloat() / caps.getValue(it).coerceAtLeast(1) }
        return mapOf(want to (REWARD * Quests.ageMultiplier(s.age)).roundToInt())
    }

    /**
     * Today's surprise was played: [correct] of [total]. It's done for the day either way. Enough right pays its reward,
     * [HELP] 🤝 and ♥ with who it was about; less than that a third of the reward, kindly. [cost]: the percent the looks
     * back at a letter took off the reward ([Peeks.cost]); looking never makes it a fail.
     */
    fun finish(s: GameState, correct: Int, total: Int, today: LocalDate, now: Long, cost: Int = 0): Pair<GameState, ChallengeResult> {
        val sp = playable(s, today) ?: return s to ChallengeResult(false, emptyMap(), emptyMap(), emptyList(), "")
        val passed = total > 0 && correct >= passMark(total)
        val pay = Peeks.cut(reward(s).let { if (passed) it else it.mapValues { (_, n) -> (n / 3).coerceAtLeast(1) } }, cost)
        var (st, got) = credit(s.copy(surprise = sp.copy(done = true)), pay)
        val name = sp.who?.let { Chest.nameOf(s, it) }
        val child = name ?: Chest.nameOf(s, pack.riddle.child)
        val line = if (passed) when (sp.kind) {
            LETTER -> if (name == null) pack.letter.wonMine.bi() else pack.letter.won.bi("name" to name)
            LAMB -> pack.lamb.won.bi()
            PILGRIM -> pack.pilgrim.won.bi()
            else -> pack.riddle.won.bi("name" to child)
        } else when (sp.kind) {
            LETTER -> pack.letter.lost.bi()
            LAMB -> pack.lamb.lost.bi()
            PILGRIM -> pack.pilgrim.lost.bi()
            else -> pack.riddle.lost.bi("name" to child)
        }
        val message = "${emoji(sp.kind)} $line"
        st = st.logged(now, emoji(sp.kind) to line)
        if (passed) {
            st = Help.earn(st, HELP)
            sp.who?.let { st = Bonds.add(st, it, if (sp.kind == LETTER) LETTER_POINTS else POINTS, today, now = now) }
        }
        return st to ChallengeResult(passed, got, emptyMap(), emptyList(), message, help = if (passed) HELP else 0)
    }
}
