package si.lanisce.lani.road

import si.lanisce.lani.data.Build
import si.lanisce.lani.data.Drill
import si.lanisce.lani.data.DrillKind
import si.lanisce.lani.data.DrillText
import si.lanisce.lani.data.Drills
import si.lanisce.lani.data.RapidItem
import si.lanisce.lani.data.RapidSet
import si.lanisce.lani.data.Riddle
import si.lanisce.lani.data.TransformItem
import si.lanisce.lani.data.TransformSet
import si.lanisce.lani.game.Introduction

/**
 * The car's audio drills on the road (lani.drill/v0, data/Drills.kt; companion/README.md "Im Auto · In the car"): each
 * a prompt, a pause to say it aloud ([Gap.SAY]: next during it plays the answer at once), the answer. Only what has its
 * clips; a set or a build of a rule not introduced to the learner yet ([Introduction]: the page above their level, not
 * met) is left out. Pure: what they sound like and in which order they play.
 */
object RoadDrills {
    /** The drills' kinds of item, in the order a "🚗 Za pot" block takes their bursts when all were heard alike. */
    val KINDS = listOf(Kind.TRANSFORM, Kind.RAPID, Kind.BUILD, Kind.RIDDLE)

    /** A drill kind's items. */
    fun kindOf(k: DrillKind): Kind = when (k) {
        DrillKind.TRANSFORM -> Kind.TRANSFORM
        DrillKind.RAPID -> Kind.RAPID
        DrillKind.BUILD -> Kind.BUILD
        DrillKind.RIDDLE -> Kind.RIDDLE
    }

    /** Rapid fire's pause to answer: about 2 s, half a second more a word from the second (at most 3.5 s). */
    fun rapidMs(answer: String): Long = (1_500L + 500L * RoadPlay.words(answer)).coerceIn(2_000L, 3_500L)

    /** After a rapid answer, on at once: a short breath. */
    const val RAPID_GAP_MS = 700L

    /** Between a riddle's clues. */
    const val CLUE_MS = 700L

    /** The pause to guess a riddle aloud. */
    const val GUESS_MS = 5_000L

    /** The items of one set played in a row before the next set's (transformations, rapid fire): the pattern settles. */
    const val ROUND = 6

    /** How many items a burst in "🚗 Za pot" has, by kind: a few, a minute or two. */
    val BURST = mapOf(Kind.TRANSFORM to 4, Kind.RAPID to 5, Kind.BUILD to 3, Kind.RIDDLE to 3)

    /** What the learner hears before a build's steps, in their base language: "Say: I'm going.", "Now add: tomorrow". */
    data class Words(val say: (String) -> String, val add: (String) -> String) {
        companion object {
            val EN = Words({ "Say: $it" }, { "Now add: $it" })
        }
    }

    private fun clip(text: String, voices: List<String>, clips: ClipLookup): Sound.Clip? =
        clips.urls(text, voices)?.takeIf { it.isNotEmpty() }?.let { Sound.Clip(text, it.map(RoadPlay::fileOf)) }

    /** "V preteklik · Into the past.": a text in the drill's language and the learner's base, as the car's screen shows it. */
    private fun shown(t: DrillText, lang: String, base: String): String {
        val a = t.said(lang)
        val b = t.meaning(base)
        return listOf(a, b).filter { it.isNotBlank() }.distinct().joinToString(" · ")
    }

    // --- the items ------------------------------------------------------------------------------------------------

    /**
     * A transformation: the sentence ([TransformItem.from]), what to do with it in the base language (the item's own
     * instruction, else its set's: "Into the past."), a pause to say the new sentence, the new sentence, a pause to repeat
     * it, the new sentence again. Null when either sentence has no clip.
     */
    fun transform(set: TransformSet, item: TransformItem, lang: String, base: String, clips: ClipLookup): RoadItem? {
        val from = clip(item.from.said(lang), RoadPlay.NARRATOR, clips) ?: return null
        val to = clip(item.to.said(lang), RoadPlay.NARRATOR, clips) ?: return null
        val instruction = (item.instruction ?: set.instruction).meaning(base).takeIf { it.isNotBlank() } ?: return null
        val sounds = listOf(
            from, Sound.Pause(400), Sound.Prompt(instruction, cue = true),
            Sound.Pause(RoadPlay.thinkMs(to.text), Gap.SAY), to, Sound.Pause(RoadPlay.repeatMs(to.text), Gap.REPEAT), to, Sound.Pause(RoadPlay.GAP_MS),
        )
        return RoadItem("transform:${set.id}/${Drills.slug(to.text)}", Kind.TRANSFORM, from.text, shown(set.instruction, lang, base), sounds)
    }

    /**
     * A rapid-fire answer: asked in the base language ("twenty-one", "It's half past three."), about two seconds to say
     * it ([rapidMs]), the answer once, and on. Null without its clip.
     */
    fun rapid(set: RapidSet, item: RapidItem, lang: String, base: String, clips: ClipLookup): RoadItem? {
        val sl = clip(item.text.said(lang), RoadPlay.NARRATOR, clips) ?: return null
        val ask = item.text.meaning(base).takeIf { it.isNotBlank() } ?: return null
        val sounds = listOf(Sound.Prompt(ask, cue = true), Sound.Pause(rapidMs(sl.text), Gap.SAY), sl, Sound.Pause(RAPID_GAP_MS))
        return RoadItem("rapid:${set.id}/${item.key}", Kind.RAPID, item.figure ?: ask, shown(set.title, lang, base), sounds)
    }

    /**
     * A sentence grown step by step: the first said whole ("Say: I'm going."), each after it what it adds ("Now add:
     * tomorrow"); each step a pause to say it, the sentence so far, a pause to repeat it; at the end the whole sentence once
     * more. Null when a step has no clip. [title]: what it grows into, in the base language.
     */
    fun build(b: Build, lang: String, base: String, words: Words, drill: String, clips: ClipLookup): RoadItem? {
        val out = mutableListOf<Sound>()
        var last: Sound.Clip? = null
        for ((i, step) in b.steps.withIndex()) {
            val sl = clip(step.text.said(lang), RoadPlay.NARRATOR, clips) ?: return null
            val cue = if (i == 0) words.say(step.text.meaning(base)) else words.add(step.add?.meaning(base)?.takeIf { it.isNotBlank() } ?: return null)
            out += listOf(Sound.Prompt(cue, cue = true), Sound.Pause(RoadPlay.thinkMs(sl.text), Gap.SAY), sl, Sound.Pause(RoadPlay.repeatMs(sl.text), Gap.REPEAT))
            last = sl
        }
        val whole = last ?: return null
        out += listOf(whole, Sound.Pause(RoadPlay.GAP_MS))
        return RoadItem("build:${b.id}", Kind.BUILD, b.steps.last().text.meaning(base), drill, out)
    }

    /**
     * A riddle in the teller's voices ([teller]): the clues, the question ("Kaj sem?"), a pause to guess aloud, the answer
     * and its meaning in the base language, then each clue again with its meaning. Null when a text has no clip.
     */
    fun riddle(r: Riddle, lang: String, base: String, teller: List<String>, drill: String, clips: ClipLookup): RoadItem? {
        val clues = r.clues.map { c -> (clip(c.said(lang), teller, clips) ?: return null) to c.meaning(base) }
        val ask = clip(r.ask.said(lang), teller, clips) ?: return null
        val answer = clip(r.answer.said(lang), teller, clips) ?: return null
        val sounds = clues.flatMap { (c, _) -> listOf(c, Sound.Pause(CLUE_MS)) } +
            listOf(ask, Sound.Pause(GUESS_MS, Gap.SAY), answer, Sound.Pause(400), Sound.Prompt(r.answer.meaning(base)), Sound.Pause(900)) +
            clues.flatMap { (c, meaning) -> listOf(c, Sound.Pause(300), Sound.Prompt(meaning), Sound.Pause(500)) } +
            Sound.Pause(RoadPlay.GAP_MS)
        return RoadItem("riddle:${r.id}", Kind.RIDDLE, shown(r.ask, lang, base), drill, sounds)
    }

    /**
     * Whether a set, a build or a riddle at [level] asking for [rules] is for a learner at [learner]: none of its rules not
     * introduced yet ([notYet]), and it is at most one level above theirs.
     */
    fun offered(level: String, rules: List<String>, learner: String, notYet: (String) -> Boolean): Boolean {
        if (rules.any(notYet)) return false
        val l = Introduction.rank(learner) ?: 0
        return (Introduction.rank(level) ?: 0) <= l + 1
    }

    /**
     * The road's drill items from [drills] in [target] (the others' language is left out) for a learner at [level]
     * explaining in [base]: each kind A1 first, then in the file's order; only what is [offered] and has its clips. [teller]
     * gives a riddle drill's teller's voices (their villager id; null: the narrator), [words] the build's prompts.
     */
    fun items(
        drills: List<Drill>,
        target: String,
        base: String,
        level: String,
        notYet: (String) -> Boolean,
        teller: (String?) -> List<String>,
        words: Words,
        clips: ClipLookup,
    ): List<RoadItem> {
        val out = mutableListOf<RoadItem>()
        fun <T> easiest(xs: List<T>, lv: (T) -> String) = xs.withIndex().sortedBy { (i, x) -> (Introduction.rank(lv(x)) ?: 0) * 10_000 + i }.map { it.value }
        for (d in drills.filter { it.language == target }) {
            val title = d.titleShown(base)
            when (d.kind) {
                DrillKind.TRANSFORM -> for (s in easiest(d.transforms) { it.level }) {
                    if (!offered(s.level, listOf(s.rule), level, notYet)) continue
                    s.items.forEach { i -> transform(s, i, target, base, clips)?.let(out::add) }
                }
                DrillKind.RAPID -> for (s in easiest(d.rapid) { it.level }) {
                    if (!offered(s.level, listOfNotNull(s.rule), level, notYet)) continue
                    s.items(target).forEach { i -> rapid(s, i, target, base, clips)?.let(out::add) }
                }
                DrillKind.BUILD -> for (b in easiest(d.builds) { it.level }) {
                    if (offered(b.level, b.rules, level, notYet)) build(b, target, base, words, title, clips)?.let(out::add)
                }
                DrillKind.RIDDLE -> {
                    val voices = teller(d.teller)
                    for (r in easiest(d.riddles) { it.level }) if (offered(r.level, emptyList(), level, notYet)) riddle(r, target, base, voices, title, clips)?.let(out::add)
                }
            }
        }
        return out.distinctBy { it.id }
    }

    // --- the sessions ---------------------------------------------------------------------------------------------

    /** "transform:preteklik/micka-je-kuhala-kosilo" → "preteklik": the set of an item of a set. */
    fun setOf(id: String): String = id.substringAfter(':').substringBefore('/')

    /**
     * A stable shuffle of [id] for the day [seed] (SplitMix64 of the two): a set's items heard alike not always in their
     * file's order, another order the next day.
     */
    private fun shuffle(id: String, seed: Long): Long {
        var z = id.hashCode().toLong() * 0x9E3779B97F4A7C15uL.toLong() + seed
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
        return z xor (z ushr 31)
    }

    /**
     * A set's items, the least recently heard first ([RoadMix.fresh]); with [seed], the ones heard alike in a shuffled order
     * of the day (the numbers not 1, 2, 3 …).
     */
    private fun inSet(items: List<RoadItem>, heard: Map<String, Long>, seed: Long?): List<RoadItem> =
        if (seed == null) RoadMix.fresh(items, heard)
        else items.sortedWith(compareBy({ heard[it.id] ?: 0L }, { shuffle(it.id, seed) }))

    /**
     * The sets of [kind] in rounds: [ROUND] items of each set in turn, the set heard least recently first (its items,
     * [inSet]), until every item came once. So the sets mix, and each pattern has a few items to settle.
     */
    private fun rounds(lib: RoadLibrary, kind: Kind, heard: Map<String, Long>, seed: Long?): List<RoadItem> {
        val sets = lib.of(kind).groupBy { setOf(it.id) }.entries.withIndex()
            .sortedWith(compareBy({ (_, e) -> e.value.maxOf { heard[it.id] ?: 0L } }, { it.index }))
            .map { ArrayDeque(inSet(it.value.value, heard, seed)) }
        val out = mutableListOf<RoadItem>()
        while (sets.any { it.isNotEmpty() }) for (s in sets) repeat(ROUND) { s.removeFirstOrNull()?.let(out::add) }
        return out
    }

    /** "🔄 Preobrat · Transformations": the sets in rounds of [ROUND], the least recently heard first. */
    fun transforms(lib: RoadLibrary, heard: Map<String, Long>): List<RoadItem> = rounds(lib, Kind.TRANSFORM, heard, null)

    /** "⚡ Hitri odziv · Rapid fire": the sets in rounds, each set's items heard alike shuffled for the day ([seed]). */
    fun rapid(lib: RoadLibrary, heard: Map<String, Long>, seed: Long): List<RoadItem> = rounds(lib, Kind.RAPID, heard, seed)

    /** "🧱 Gradnja stavkov · Sentence building": the builds, the least recently heard first. */
    fun builds(lib: RoadLibrary, heard: Map<String, Long>): List<RoadItem> = RoadMix.fresh(lib.of(Kind.BUILD), heard)

    /** "🕵️ Uganke · Riddles": the riddles, the least recently heard first. */
    fun riddles(lib: RoadLibrary, heard: Map<String, Long>): List<RoadItem> = RoadMix.fresh(lib.of(Kind.RIDDLE), heard)

    /** The drill kinds the library has, the least recently heard first (a kind's latest item heard): the bursts' turn. */
    fun kinds(lib: RoadLibrary, heard: Map<String, Long>): List<Kind> =
        KINDS.filter { k -> lib.items.any { it.kind == k } }.withIndex()
            .sortedWith(compareBy({ (_, k) -> lib.of(k).maxOf { heard[it.id] ?: 0L } }, { it.index }))
            .map { it.value }

    /**
     * A short burst of [kind] for "🚗 Za pot" ([BURST] items): of transformations and rapid fire a few of one set (the
     * least recently heard), of builds and riddles the least recently heard; nothing of [exclude] (queued already).
     */
    fun burst(lib: RoadLibrary, heard: Map<String, Long>, kind: Kind, seed: Long, exclude: Set<String> = emptySet()): List<RoadItem> {
        val n = BURST[kind] ?: return emptyList()
        return when (kind) {
            Kind.TRANSFORM, Kind.RAPID -> {
                val all = lib.of(kind).filter { it.id !in exclude }
                val set = all.groupBy { setOf(it.id) }.entries.withIndex()
                    .minWithOrNull(compareBy({ (_, e) -> e.value.maxOf { heard[it.id] ?: 0L } }, { it.index }))?.value?.value.orEmpty()
                inSet(set, heard, seed.takeIf { kind == Kind.RAPID }).take(n)
            }
            else -> RoadMix.fresh(lib.of(kind).filter { it.id !in exclude }, heard).take(n)
        }
    }
}
