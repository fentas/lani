package si.lanisce.lani.game.villagers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import si.lanisce.lani.data.json
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply
import si.lanisce.lani.game.scene.inPair
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Message
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A culture pack's arrivals (companion/cultures/<id>/arrivals.json, lani.arrivals/v0; companion/VILLAGERS.md
 * "Arrivals"): how each of its cast is introduced, and the templates for the people the village makes up (a newcomer, a
 * birth, a mover). The texts stay as the file has them (every language) until a dialog is made for the learner's pair.
 */
@Serializable
data class ArrivalsFile(
    val schema: String = "",
    /** The pack's language: what is said. */
    val language: String = "sl",
    val review: String? = null,
    /** By villager id: {by, memory, levels: {"A1": {late, lines}, …}}. */
    val cast: Map<String, JsonObject> = emptyMap(),
    /** newcomer, birth, moved: the same, with placeholders ({first}, {g, select, …}). */
    val templates: Map<String, JsonObject> = emptyMap(),
)

/**
 * Someone who lives in the village and whom the learner hasn't met yet: their introduction waits ([Arrivals.pending]).
 * [late]: they came days ago (an older village, or the bubble waited): the dialog opens with its late line.
 */
data class PendingArrival(val resident: Resident, val kind: Kind, val late: Boolean) {
    enum class Kind { CAST, NEWCOMER, BIRTH, MOVED }

    val id: String get() = resident.id

    /** "👶" for a baby, "🧳" for everyone else. */
    val emoji: String get() = if (kind == Kind.BIRTH) "👶" else "🧳"
}

/**
 * An introduction ready to play: its [dialog] (in the learner's pair, the placeholders filled, the late line first when
 * it's [late]), who says a choice's reply at each turn ([repliers], the line's index → id: whoever spoke last), everyone
 * who speaks ([speakers], the newcomer first), what the newcomer keeps of it ([memory]), and where it came from
 * ([source]: tutor, culture or template).
 */
data class Meeting(
    val id: String,
    val kind: PendingArrival.Kind,
    val late: Boolean,
    val level: String,
    val dialog: Dialog,
    val repliers: Map<Int, String>,
    val speakers: List<String>,
    val memory: Memory?,
    val source: String,
)

/**
 * Arrivals (companion/VILLAGERS.md): when someone joins the village an introduction waits for the learner, and nobody hosts
 * a run, asks for help, leads a feast or stands in a scene before the learner has met them ([met]). Pure.
 *
 * Whether a village introduces its people is [on]: its culture pack has arrivals, and the village has been through
 * [migrate] (which forgets the meetings of an older village that were no meeting at all). Otherwise everyone counts as
 * met, as before.
 */
object Arrivals {
    /** The one-time fix of [GameState.migrated] that turns arrivals on for a village. */
    const val MIGRATION = "arrivals"

    /** Someone who came this many days ago or more is met late: "Nisva se še spoznala …". */
    const val LATE_DAYS = 2L

    /** The levels an arrival can be written for, easiest first. */
    val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

    /** The village's own culture's arrivals (also during a visit), when its pack has them. */
    val file: ArrivalsFile? get() = Cultures.home.arrivals

    /** Whether [state]'s village introduces its people: migrated, and its culture has arrivals. */
    fun on(state: GameState, file: ArrivalsFile? = this.file): Boolean = MIGRATION in state.migrated && file != null

    /** Whether the learner has met [id]: the friendship started (the introduction, or before arrivals); everyone while [on] isn't. */
    fun met(state: GameState, id: String, file: ArrivalsFile? = this.file): Boolean = !on(state, file) || state.bonds[id]?.met != null

    /**
     * Turns arrivals on for [state]'s village, once, when its culture has them ([hasArrivals]). An older village has
     * people the learner never really met (a greeting, a training on the stage): whoever shares no memory and fewer than
     * [Bonds.DIALOG] points with the learner is a stranger again, and gets a late introduction. The rest keep their
     * friendship. Returns the same state when there is nothing to do.
     */
    fun migrate(state: GameState, hasArrivals: Boolean = file != null): GameState {
        if (!hasArrivals || MIGRATION in state.migrated) return state
        val bonds = state.bonds.mapValues { (_, b) -> if (b.met != null && b.memories.isEmpty() && b.points < Bonds.DIALOG) b.copy(met = null) else b }
        return state.copy(bonds = bonds, migrated = state.migrated + MIGRATION)
    }

    /** Who [r] is, for their introduction: of the cast (no name of their own: the cast's), born here, from a friend's town, or a newcomer. */
    fun kindOf(r: Resident, home: String = Cultures.home.id): PendingArrival.Kind = when {
        r.born != null -> PendingArrival.Kind.BIRTH
        r.name == null -> PendingArrival.Kind.CAST
        r.culture != null && r.culture != home -> PendingArrival.Kind.MOVED
        else -> PendingArrival.Kind.NEWCOMER
    }

    /** The people living here the learner hasn't met, in the order they came. */
    fun pending(state: GameState, today: LocalDate, file: ArrivalsFile? = this.file): List<PendingArrival> {
        if (!on(state, file)) return emptyList()
        return state.residents.withIndex()
            .filter { (_, r) -> state.bonds[r.id]?.met == null }
            .sortedWith(compareBy({ it.value.since }, { it.index }))
            .map { (_, r) -> PendingArrival(r, kindOf(r), late(r, today)) }
    }

    /**
     * The introductions that can be played now: those whose other people are met, or don't live here (a relative who
     * introduces them waits for their own introduction first; the parents show their baby once the learner knows them).
     * A baby whose parents both left is shown by someone the learner knows.
     */
    fun ready(state: GameState, today: LocalDate, file: ArrivalsFile? = this.file): List<PendingArrival> =
        pending(state, today, file).filter { p ->
            needs(p, state, file).all { met(state, it, file) } && (p.kind != PendingArrival.Kind.BIRTH || parentsOf(p.resident, state).first != null)
        }

    /** [ready], [id]'s when it is, else null. */
    fun readyFor(state: GameState, id: String, today: LocalDate, file: ArrivalsFile? = this.file): PendingArrival? =
        ready(state, today, file).firstOrNull { it.id == id }

    /**
     * The introduction to play when the learner goes to meet [id]: theirs when it's ready, else (they wait for someone to
     * introduce them, a baby for its parents) the one of whoever they wait for; null when [id] is met or there is none.
     */
    fun next(state: GameState, id: String, today: LocalDate, file: ArrivalsFile? = this.file, seen: Set<String> = emptySet()): PendingArrival? {
        val all = ready(state, today, file)
        all.firstOrNull { it.id == id }?.let { return it }
        val p = pending(state, today, file).firstOrNull { it.id == id } ?: return null
        val waits = needs(p, state, file) - seen - id
        return all.firstOrNull { it.id in waits } ?: waits.firstNotNullOfOrNull { next(state, it, today, file, seen + id) }
    }

    /**
     * What the bubble and the dialog's header say: the chronicle's words for the day they came ("Zala se je preselila v vas ·
     * Zala moved into the village", "Pri Furlanovih se je rodila Ana! · …"), in the pair of the moment.
     */
    fun title(p: PendingArrival, v: Villager?): String {
        val news = Cultures.home.people.news
        val name = v?.name ?: p.resident.name ?: p.id
        val g = if ((p.resident.voice ?: v?.voice) == "female") "f" else "m"
        if (p.kind != PendingArrival.Kind.BIRTH) return news.movedInCast.bi("name" to name, "g" to g)
        val family = p.resident.family ?: name.substringAfter(' ', name)
        val at = si.lanisce.lani.game.culture.localized { lang -> if (lang == Lang.SL) Residents.plural(family) else family }
        return news.born.bi("first" to name.substringBefore(' '), "family" to family, "family_at" to at, "g" to g)
    }

    /** Whether [r] came [LATE_DAYS] days ago or more. */
    fun late(r: Resident, today: LocalDate): Boolean =
        runCatching { ChronoUnit.DAYS.between(LocalDate.parse(r.since), today) >= LATE_DAYS }.getOrDefault(false)

    /** Who else speaks in [p]'s introduction and lives here: the relative who introduces them, a baby's parents. */
    private fun needs(p: PendingArrival, state: GameState, file: ArrivalsFile?): List<String> {
        val living = state.residents.map { it.id }.toSet()
        val others = when (p.kind) {
            PendingArrival.Kind.BIRTH -> p.resident.parents
            PendingArrival.Kind.CAST -> listOfNotNull(file?.cast?.get(p.id)?.let { str(it, "by") })
            else -> emptyList()
        }
        return others.filter { it != p.id && it in living }
    }

    // --- the dialog -------------------------------------------------------------------------------------------------

    /**
     * [p]'s introduction for a learner at [level] (in the village's language), read in [pair]: the tutor's ([tutor], by
     * resident id, as the node serves them), else the culture pack's for someone of the cast, else the template for who
     * they are; null when there is none. [cast] is the cast and the village's own people (their names, sprites, homes).
     */
    fun meeting(
        p: PendingArrival,
        state: GameState,
        cast: List<Villager>,
        level: String,
        today: LocalDate,
        tutor: Map<String, JsonObject> = emptyMap(),
        file: ArrivalsFile? = this.file,
        pair: LangPair = L10n.pair,
    ): Meeting? {
        val r = p.resident
        val living = state.residents.map { it.id }.toSet()
        val lang = file?.language ?: Cultures.home.manifest.language
        // the tutor's, when everyone it names lives here
        tutor[r.id]?.let { t ->
            val named = whoOf(t) + listOfNotNull(str(t, "by"))
            if (named.all { it == r.id || it in living }) {
                build(p, t, str(t, "language") ?: lang, level, today, pair, NO_ARGS, { it }, "tutor")?.let { return it }
            }
        }
        if (file == null) return null
        // the pack's, for someone of its cast, when whoever introduces them lives here; at a level that names only people
        // the learner knows (Mentions: Mojca's B1 "od Tineta do Zale" once they're met), else as it is
        if (p.kind == PendingArrival.Kind.CAST) file.cast[r.id]?.let { a ->
            val by = str(a, "by")
            val known = Residents.present(state, today)?.plus(listOfNotNull(r.id, by))
            val names = Mentions.names(file.language, cast)
            val fits: (JsonObject) -> Boolean = { lv -> known == null || Mentions.known(textsOf(lv, file.language).flatMap { names.of(it) }, known) }
            if (by == null || by in living) build(p, a, file.language, level, today, pair, NO_ARGS, { it }, "culture", fits)?.let { return it }
        }
        val kind = when (p.kind) {
            PendingArrival.Kind.BIRTH -> "birth"
            PendingArrival.Kind.MOVED -> if ("moved" in file.templates) "moved" else "newcomer"
            else -> "newcomer"
        }
        val t = file.templates[kind] ?: return null
        val self = r.id
        val (parent, parent2) = parentsOf(r, state)
        val who: (String) -> String = { w ->
            when (w) {
                "self" -> self
                "parent" -> parent ?: self
                "parent2" -> parent2 ?: parent ?: self
                else -> w
            }
        }
        if (p.kind == PendingArrival.Kind.BIRTH && parent == null) return null // a baby can't introduce itself
        return build(p, t, file.language, level, today, pair, argsOf(r, state, cast, lang), who, "template")
    }

    /**
     * Who shows a baby: its parents who live here (the first, and the other: null when only one does); when both left,
     * the grown-up the learner has known longest.
     */
    private fun parentsOf(r: Resident, state: GameState): Pair<String?, String?> {
        val living = r.parents.filter { id -> state.residents.any { it.id == id } }
        if (living.isNotEmpty() || r.born == null) return living.getOrNull(0) to living.getOrNull(1)
        val known = state.residents.filter { it.born == null && it.id != r.id && state.bonds[it.id]?.met != null }
            .minByOrNull { state.bonds[it.id]?.met.orEmpty() }
        return known?.id to null
    }

    /** A template's arguments for [r], in each language: their names, their trade, their gender, a baby's parents. */
    private fun argsOf(r: Resident, state: GameState, cast: List<Villager>, lang: String): (Lang) -> Map<String, Any?> {
        val v = cast.firstOrNull { it.id == r.id }
        val name = r.name ?: v?.name ?: r.id
        val first = name.substringBefore(' ')
        val g = if ((r.voice ?: v?.voice) == "female") "f" else "m"
        val (p1, p2) = parentsOf(r, state)
        fun person(id: String?) = id?.let { i -> state.residents.firstOrNull { it.id == i } }
        val parent = person(p1)
        val parent2 = person(p2) ?: parent
        val trade = tradeOf(r.role ?: v?.role, g == "f")
        return { l ->
            val role = trade?.let { t -> if (t.has(l)) t.of(l) else null } ?: roleIn(r.role ?: v?.role, l, lang)
            mapOf(
                "name" to name, "first" to first, "family" to (r.family ?: name.substringAfter(' ', "")), "g" to g,
                // the trade mid-sentence: lower-case, but a German noun keeps its capital ("die Holzfällerin")
                "role" to (if (l == Lang.DE) role else role.lowercase()), "from" to r.from.orEmpty(),
                "parent" to parent?.name?.substringBefore(' ').orEmpty(), "parent2" to parent2?.name?.substringBefore(' ').orEmpty(),
                "pg" to if (parent?.voice == "female") "f" else "m", "p2g" to if (parent2?.voice == "female") "f" else "m",
            )
        }
    }

    /** The culture's trade whose name [role] ("Drvarka · Woodcutter") has, in every language it has. */
    private fun tradeOf(role: String?, female: Boolean): Text? {
        val said = role?.substringBefore(" · ")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return Cultures.home.people.trades.map { if (female) it.female else it.male }
            .firstOrNull { t -> t.by.values.any { it.equals(said, ignoreCase = true) } }
    }

    /** [role] ("target · base") in [l]: its first half in the village's language [lang], else its second. */
    private fun roleIn(role: String?, l: Lang, lang: String): String {
        val r = role.orEmpty()
        return if (l.code == lang || " · " !in r) r.substringBefore(" · ").trim() else r.substringAfter(" · ").trim()
    }

    /** Every `who` [a] names, at every level and in its late lines. */
    private fun whoOf(a: JsonObject): List<String> = (a["levels"] as? JsonObject)?.values.orEmpty().flatMap { v ->
        val o = v as? JsonObject ?: return@flatMap emptyList()
        listOfNotNull((o["late"] as? JsonObject)?.let { str(it, "who") }) +
            (o["lines"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { l -> str(l, "who") } }
    }.distinct()

    /** The level to play of those [have]: the highest up to the learner's [level], else the easiest. */
    fun levelFor(have: Collection<String>, level: String): String? {
        val at = LEVELS.indexOf(level.uppercase()).takeIf { it >= 0 } ?: 0
        val known = LEVELS.filter { it in have }
        return known.lastOrNull { LEVELS.indexOf(it) <= at } ?: known.firstOrNull()
    }

    /** What is said at one level of an arrival, in [lang]: its late line, its lines, the choices and the replies. */
    fun textsOf(level: JsonObject, lang: String): List<String> {
        val out = ArrayList<String>()
        fun said(o: JsonObject?) = o?.let { str(it, lang) }?.let { out += it }
        said(level["late"] as? JsonObject)
        for (l in (level["lines"] as? JsonArray).orEmpty()) {
            val o = l as? JsonObject ?: continue
            said(o)
            for (c in (o["choices"] as? JsonArray).orEmpty()) {
                said(c as? JsonObject)
                said((c as? JsonObject)?.get("reply") as? JsonObject)
            }
        }
        return out
    }

    private fun build(
        p: PendingArrival, a: JsonObject, lang: String, level: String, today: LocalDate, pair: LangPair,
        args: (Lang) -> Map<String, Any?>, who: (String) -> String, source: String, fits: (JsonObject) -> Boolean = { true },
    ): Meeting? {
        val levels = a["levels"] as? JsonObject ?: return null
        // the learner's, or an easier one when it names someone they don't know yet ([fits]); else the learner's as it is
        val best = levelFor(levels.keys, level) ?: return null
        val lv = LEVELS.take(LEVELS.indexOf(best) + 1).lastOrNull { k -> (levels[k] as? JsonObject)?.let(fits) == true } ?: best
        val raw = levels[lv] as? JsonObject ?: return null
        // read in the pair as a scene's dialog is (inPair): what is said, and what it means in the learner's base
        val lines = raw["lines"] as? JsonArray ?: return null
        val late = raw["late"] as? JsonObject
        val scene = JsonObject(
            mapOf(
                "language" to JsonPrimitive(lang),
                "dialogs" to JsonArray(listOfNotNull(
                    JsonObject(mapOf("id" to JsonPrimitive("lines"), "lines" to lines)),
                    late?.let { JsonObject(mapOf("id" to JsonPrimitive("late"), "lines" to JsonArray(listOf(it)))) },
                )),
                "happenings" to JsonArray(listOfNotNull((a["memory"] as? JsonObject)?.let { JsonObject(mapOf("memory" to it)) })),
            ),
        )
        val read = inPair(scene, pair)
        fun dialog(id: String): List<DialogLine>? = (read["dialogs"] as? JsonArray)?.firstOrNull { (it as? JsonObject)?.let { d -> str(d, "id") } == id }
            ?.let { json.decodeFromJsonElement(Dialog.serializer(), it).lines }
        val body = dialog("lines") ?: return null
        val opening = if (p.late) dialog("late")?.firstOrNull() else null
        val memory = ((read["happenings"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("memory")
            ?.let { runCatching { json.decodeFromJsonElement(DialogReply.serializer(), it) }.getOrNull() }
        // the placeholders: what is said in the village's language, what it means in the learner's base
        val said = Lang.of(lang) ?: pair.target
        val sayArgs = args(said)
        val meantArgs = args(pair.base)
        fun say(s: String) = fill(s, said, sayArgs)
        fun mean(s: String) = fill(s, pair.base, meantArgs)
        fun reply(r: DialogReply) = r.copy(sl = say(r.sl), en = mean(r.en))
        fun line(l: DialogLine) = l.copy(
            who = l.who?.let(who), sl = l.sl?.let(::say), en = l.en?.let(::mean),
            choices = l.choices.map { c -> c.copy(sl = say(c.sl), en = mean(c.en), why = c.why?.let(::mean), reply = c.reply?.let(::reply)) },
        )
        val all = (listOfNotNull(opening) + (if (opening != null) body.drop(1) else body)).map(::line)
        if (all.isEmpty()) return null
        // a reply is said by whoever spoke last before the turn
        val repliers = HashMap<Int, String>()
        var last: String? = null
        all.forEachIndexed { i, l -> if (l.choices.isEmpty()) l.who?.let { last = it } else last?.let { repliers[i] = it } }
        val speakers = (listOf(p.id) + all.mapNotNull { it.who }).distinct()
        val baby = p.kind == PendingArrival.Kind.BIRTH
        return Meeting(
            id = p.id, kind = p.kind, late = opening != null, level = lv,
            dialog = Dialog(id = "arrival:${p.id}", lines = all, talk = if (baby) null else "villager:${p.id}"),
            repliers = repliers, speakers = speakers,
            memory = memory?.let { Memory(today.toString(), say(it.sl), mean(it.en), KIND) },
            source = source,
        )
    }

    /** What an introduction's memory is, among a friendship's ([Memory.kind]). */
    const val KIND = "arrival"

    /** A cast member's or the tutor's arrival names people as they are: nothing to put in. */
    private val NO_ARGS: (Lang) -> Map<String, Any?> = { emptyMap() }

    /** [s] with the template's arguments put in (as it is when it has none, or doesn't parse). */
    private fun fill(s: String, lang: Lang, args: Map<String, Any?>): String =
        if ('{' !in s || args.isEmpty()) s else runCatching { Message.of(s).format(lang, args) }.getOrDefault(s)

    private fun str(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    /** The tutor's arrivals as the node serves them (GET /arrivals: {arrivals: [{id, …}]}), by id. */
    fun parseServed(raw: String): Map<String, JsonObject> {
        val o = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return emptyMap()
        val list = o["arrivals"] as? JsonArray ?: return emptyMap()
        return list.mapNotNull { e -> (e as? JsonObject)?.let { a -> str(a, "id")?.let { it to a } } }.toMap()
    }

    /** The served arrivals back as the node sends them, for the phone's cache. */
    fun served(arrivals: Map<String, JsonObject>): String =
        JsonObject(mapOf("arrivals" to JsonArray(arrivals.values.toList<JsonElement>()))).toString()
}
