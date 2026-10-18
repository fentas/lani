package si.lanisce.lani.game

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import si.lanisce.lani.data.json
import si.lanisce.lani.game.culture.CelebrationFile
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.ISpy
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.inPair
import si.lanisce.lani.game.villagers.Arrivals
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Memory
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.bi
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * What decorates the village for an age's celebration ([AgeUps]): the renderer draws them (game/render, [Festive]) as the
 * age's moment puts them up, and on the map from the day the age came for [AgeUps.DECOR_DAYS] days.
 */
enum class Decor(val id: String) {
    /** Torches on poles round the fire: the first camp's evening. */
    TORCHES("torches"),

    /** The likof's little spruce with ribbons on the ridge of the newest roof (a house's, else a hut's). */
    SPRUCE("spruce"),

    /** Garlands of little flags strung round the open ground by the fire, with lanterns in them after dusk. */
    BUNTING("bunting"),

    /** A wooden dance floor by the fire (the veselica's), a bench and a barrel beside it. */
    FLOOR("floor"),

    /** Market stalls with striped awnings along the road by the fire (the semenj). */
    STALLS("stalls"),

    /** Banners on tall poles along the road (the town's festival), the procession walking under them in the moment. */
    BANNERS("banners"),
    ;

    companion object {
        fun of(id: String): Decor? = entries.firstOrNull { it.id == id }
    }
}

/** The music of an age's moment (game/ambient/Sfx: synthesized, like the village's other sounds). */
enum class Music(val id: String) {
    /** A slow waltz on the harmonika by the fire. */
    VALCEK("valcek"),

    /** A polka on the harmonika: the village party's. */
    HARMONIKA("harmonika"),

    /** The village band's polka, its brass (the godba): the town's procession. */
    GODBA("godba"),
    ;

    companion object {
        fun of(id: String?): Music? = entries.firstOrNull { it.id == id }
    }
}

/** Where an age's party is: by the fire, or on the square under the linden (by the fire while there is no linden). */
enum class PartyPlace(val id: String) {
    FIRE("fire"),
    SQUARE("square"),
    ;

    companion object {
        fun of(id: String?): PartyPlace? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What an age changes on the map, as its moment shows them one after another (the renderer: [Growth]): the forest draws
 * back (the clearing of the new age), its new plots, the road to the west over its bridge (Zaselek), the road wider (Vas),
 * the open ground round the fire (Zaselek, Trg), its cobbles (Trg), the palisade moving out with the forest, the fire.
 */
enum class Change(val emoji: String, val key: String) {
    FOREST("🌲", "ageUp.change.forest"),
    PLOTS("📐", "ageUp.change.plots"),
    WEST("🌉", "ageUp.change.west"),
    WIDE("🛣️", "ageUp.change.wide"),
    SQUARE("⭕", "ageUp.change.square"),
    COBBLES("🧱", "ageUp.change.cobbles"),
    PALISADE("🪵", "ageUp.change.palisade"),
    FIRE("🔥", "ageUp.change.fire"),
}

/**
 * An age-up on the map as its moment shows it (the renderer's Frame.growth): what of the new age shows yet, each 0 (as it
 * was at [from]) to 1 (grown): the forest drawing back ([forest]: the clearing's edge between the two ages', the trees going
 * as it passes them, the palisade moving out at its end), the road to the west and its bridge ([west]), the road widening
 * ([wide]), the open ground round the fire ([square]), its cobbles ([cobbles]), the fire ([fire]). [plots]: the new plots to
 * mark on the ground (their frames, as the plot chooser's), while they are shown.
 */
data class Growth(
    val from: Age,
    val forest: Float = 1f,
    val west: Float = 1f,
    val wide: Float = 1f,
    val square: Float = 1f,
    val cobbles: Float = 1f,
    val fire: Float = 1f,
    val plots: List<Int> = emptyList(),
)

/**
 * A celebration's decorations on the map (the renderer's Frame.festive): which ([decor]), how far each is up (0..1, by its
 * index in [decor]: [up]), the village's sign at the road showing the age ([sign]: how far it has turned to the new one,
 * 0..1; null: no sign), whether the village gathers round the fire ([gather]), and the procession along the road ([march]:
 * 0..1 of its way, null: none).
 */
data class Festive(
    val decor: List<Decor>,
    val up: List<Float> = decor.map { 1f },
    val sign: Float? = null,
    val gather: Boolean = false,
    val march: Float? = null,
) {
    /** How far [d] is up (0: not yet, or not among them). */
    fun upOf(d: Decor): Float = decor.indexOf(d).takeIf { it >= 0 }?.let { up.getOrNull(it) } ?: 0f
}

/**
 * How the village celebrates reaching [age] (companion/GAME.md "Ages and their celebrations"): its culture's tradition
 * (celebrations/<age>.json: Primorska's likof, veselica, semenj …) or, where the culture has none, a gathering of the village
 * ([generic]). [nameText] "Veselica · The village party"; [aboutText] what it is; [momentText] what the moment says as the
 * decorations go up; [lineText] the chronicle's line once the party was had; [memoryText] how the people remember it
 * ("Še vedno mislim na …"); [decor], [music], [place]; who hosts the party and speaks in it ([host], [guests]: villager
 * ids), its word pack ([pack]) and the words its turns test ([words]); its dialog per level ([levels]; none: a gathering).
 */
data class Celebration(
    val age: Age,
    val emoji: String,
    val nameText: Text,
    val aboutText: Text,
    val momentText: Text,
    val lineText: Text,
    val memoryText: Text?,
    val decor: List<Decor>,
    val music: Music,
    val place: PartyPlace,
    val host: String?,
    val guests: List<String>,
    val pack: String?,
    val words: List<String>,
    val levels: JsonObject,
    val generic: Boolean,
) {
    /** "Veselica · The village party". */
    val name: String get() = nameText.bi()
    val about: String get() = aboutText.bi()
    val line: String get() = lineText.bi()

    /** It has a party to play: a dialog at some level, and someone to host it. */
    val party: Boolean get() = levels.isNotEmpty() && host != null
}

/** One beat of an age's moment ([AgeUps.moment]): what it shows ([kind], the [change] it brings), from [from] to [to] seconds. */
data class Beat(val kind: Kind, val from: Double, val to: Double, val change: Change? = null) {
    enum class Kind {
        /** "Nova doba · A new age": the village as it was, the camera wide. */
        OPEN,

        /** One of the age's changes ([change]) comes, where it is. */
        GROW,

        /** The villagers gather round the fire, which burns up. */
        GATHER,

        /** The celebration's decorations go up one by one, its music starts. */
        DECORATE,

        /** The village's sign at the road turns to the new age. */
        SIGN,

        /** Fireworks over the village, the celebration's name. */
        CHEER,
    }

    /** How far into it [t] is, 0..1 (eased). */
    fun progress(t: Double): Float = AgeUps.ease(((t - from) / (to - from)).toFloat())
}

/** What the moment shows at one time ([Moment.at]): the beat on, the map's [growth] and [festive], the music started. */
data class MomentFrame(val beat: Beat?, val growth: Growth, val festive: Festive, val music: Boolean, val done: Boolean)

/**
 * The moment the village reaches [to] from [from] ([AgeUps.moment]): its [beats] one after another, the celebration's
 * [celebration], the new plots ([plots]). Pure: what shows at each second ([at]).
 */
data class Moment(val from: Age, val to: Age, val celebration: Celebration, val beats: List<Beat>, val plots: List<Int>, val changes: List<Change>) {
    /** How long it lasts (s): to the end of its last beat. */
    val length: Double get() = beats.lastOrNull()?.to ?: 0.0

    /** When its music starts (s): as the decorations go up. */
    val musicAt: Double get() = beats.firstOrNull { it.kind == Beat.Kind.DECORATE }?.from ?: 0.0

    /** The beat on at [t], or null after the last. */
    fun beat(t: Double): Beat? = beats.firstOrNull { t >= it.from && t < it.to }

    /**
     * What shows at [t] seconds into it: each change at 0 before its beat, growing through it, 1 after (the fire with the
     * gathering); the new plots marked while theirs is on; the villagers gathered from the gathering on; the decorations
     * going up one after another through theirs; the sign turning through its own; the procession marching (a town's).
     */
    fun at(t: Double): MomentFrame {
        fun of(c: Change): Float {
            val b = beats.firstOrNull { it.change == c } ?: return 1f
            return if (t <= b.from) 0f else if (t >= b.to) 1f else b.progress(t)
        }
        val gather = beats.firstOrNull { it.kind == Beat.Kind.GATHER }
        val fire = when {
            gather == null -> 1f
            t <= gather.from -> 0f
            t >= gather.to -> 1f
            else -> gather.progress(t)
        }
        val plotsOn = beats.firstOrNull { it.change == Change.PLOTS }?.let { t >= it.from && t < it.to } == true
        val growth = Growth(
            from = from,
            forest = of(Change.FOREST), west = of(Change.WEST), wide = of(Change.WIDE),
            square = of(Change.SQUARE), cobbles = of(Change.COBBLES), fire = fire,
            plots = if (plotsOn) plots else emptyList(),
        )
        val decorate = beats.firstOrNull { it.kind == Beat.Kind.DECORATE }
        val decor = celebration.decor
        val up = decor.indices.map { i ->
            val d = decorate ?: return@map 1f
            val n = decor.size.coerceAtLeast(1)
            val span = (d.to - d.from) / n
            val a = d.from + i * span
            ((t - a) / (span * 0.85)).toFloat().coerceIn(0f, 1f).let(AgeUps::ease)
        }
        val sign = beats.firstOrNull { it.kind == Beat.Kind.SIGN }?.let { b -> if (t <= b.from) 0f else if (t >= b.to) 1f else b.progress(t) }
        val gathered = gather != null && t >= gather.from
        val march = if (Decor.BANNERS in decor) decorate?.let { d -> ((t - d.from) / (length - d.from)).toFloat().coerceIn(0f, 1f) } else null
        return MomentFrame(beat(t), growth, Festive(decor, up, sign, gathered, march), t >= musicAt, t >= length)
    }
}

/**
 * Something the new age brings, for the moment's card "Kaj je novo · What's new" ([AgeUps.whatsNew]): its [emoji] and what
 * it is ([text], "target · base"), and where it is ([go], with its [arg]: a building type, a project, a scene, a person).
 */
data class NewThing(val emoji: String, val text: String, val go: Go, val arg: String? = null) {
    enum class Go { BUILD, PROJECTS, SCENE, PERSON, CHEST, SCHOOL, INFO, FESTIVALS, NONE }
}

/** An age the village reached, for the chronicle ([AgeUps.milestones]): its [celebration] and the day ([on]; null: not known). */
data class Milestone(val age: Age, val celebration: Celebration, val on: LocalDate?)

/**
 * "Nova doba · A new age" (companion/GAME.md "Ages and their celebrations"): every age-up is a celebration, each from the
 * village tradition of its culture (Primorska: the first evening at the fire, likof, veselica, semenj, the town's feast).
 * In three parts: the moment over the map ([moment]: the changes the age brings one after another, the villagers
 * gathering, the decorations going up, the sign, the music, then the card with what's new, [whatsNew]); the party that
 * evening ([partyNow], [partyHad]: a short dialog with all the village, its toasts, a song, a dance, and its word pack;
 * the decorations stay on the map, [decorOn]); and "🎉 Poglej spet · Watch again" in the chronicle ([milestones],
 * [ageOf]), which plays the moment again and lets the party be had again, for every age the village reached, also one
 * reached before the celebrations came ([reachedOn]: the chronicle's line says when). Pure, like the engine.
 */
object AgeUps {
    /** The days a celebration's decorations stay on the map: the day the age came and the next. */
    const val DECOR_DAYS = 2L

    /** The evenings an age's party is on: the evening of the day it came, and the next for whoever missed it. */
    const val PARTY_EVENINGS = 2L

    /** When a party evening begins (the hour), and when its night ends the next morning. */
    const val EVENING = 17
    const val NIGHT_END = 5

    /** What the first party of an age brings: the spirits up, everyone who lives here closer. */
    const val PARTY_MORALE = 10
    const val PARTY_POINTS = 2

    /** A party's dialog: its id, by age ("party:vas"), the key its words and its talk are kept under. */
    fun dialogId(age: Age): String = "party:${key(age)}"

    /** An age's key in the state's maps ([GameState.grown], [GameState.parties]): its name in lower case. */
    fun key(age: Age): String = age.name.lowercase()

    // --- the celebrations ------------------------------------------------------------------------------------------

    /** How [age] is celebrated in the culture on screen: its own celebration, else the generic gathering. */
    fun celebration(age: Age): Celebration = of(age, Cultures.current.celebrations[age])

    /** [age]'s celebration from its file [f] (what it leaves out, the generic's), or the generic gathering without one. */
    fun of(age: Age, f: CelebrationFile?): Celebration {
        val g = generic(age)
        if (f == null) return g
        return Celebration(
            age = age,
            emoji = f.emoji,
            nameText = f.name,
            aboutText = f.about ?: g.aboutText,
            momentText = f.moment ?: g.momentText,
            lineText = f.line ?: g.lineText,
            memoryText = f.memory,
            decor = f.decor.mapNotNull(Decor::of).distinct().ifEmpty { g.decor },
            music = Music.of(f.music) ?: g.music,
            place = PartyPlace.of(f.place) ?: g.place,
            host = f.host,
            guests = f.guests,
            pack = f.pack,
            words = f.words,
            levels = f.levels,
            generic = false,
        )
    }

    /** The decorations of an age without a celebration of its culture's. */
    fun defaultDecor(age: Age): List<Decor> = when (age) {
        Age.OGENJ, Age.TABOR -> listOf(Decor.TORCHES)
        Age.ZASELEK -> listOf(Decor.SPRUCE, Decor.BUNTING)
        Age.VAS -> listOf(Decor.BUNTING, Decor.FLOOR)
        Age.TRG -> listOf(Decor.STALLS, Decor.BUNTING)
        Age.MESTO -> listOf(Decor.BANNERS, Decor.BUNTING)
    }

    /** The music of an age without a celebration of its culture's. */
    fun defaultMusic(age: Age): Music = when (age) {
        Age.OGENJ, Age.TABOR, Age.ZASELEK -> Music.VALCEK
        Age.VAS, Age.TRG -> Music.HARMONIKA
        Age.MESTO -> Music.GODBA
    }

    /**
     * The gathering of a culture with no celebration of its own for [age]: "Praznovanje · The celebration" (from the app's
     * labels, in every language it has), the village gathering by the fire, its decorations and music by the age; no party
     * dialog.
     */
    fun generic(age: Age): Celebration = Celebration(
        age = age,
        emoji = "🎉",
        nameText = label("ageUp.generic.name"),
        aboutText = label("ageUp.generic.about"),
        momentText = label("ageUp.generic.moment"),
        lineText = label("ageUp.generic.line"),
        memoryText = null,
        decor = defaultDecor(age),
        music = defaultMusic(age),
        place = if (age >= Age.VAS) PartyPlace.SQUARE else PartyPlace.FIRE,
        host = null,
        guests = emptyList(),
        pack = null,
        words = emptyList(),
        levels = JsonObject(emptyMap()),
        generic = true,
    )

    /** An app label as a text in every language the app has (the age's name filled in, [args]). */
    private fun label(key: String, vararg args: Pair<String, Any?>): Text =
        Text(Lang.entries.associate { it.code to L10n.text(it, key, args.toMap()) })

    // --- the moment --------------------------------------------------------------------------------------------------

    /** How long each kind of beat lasts (s). */
    private const val OPEN_S = 1.8
    private const val GROW_S = 2.3
    private const val GATHER_S = 2.6
    private const val DECORATE_S = 3.4
    private const val SIGN_S = 2.2
    private const val CHEER_S = 2.8

    /**
     * What reaching [to] from [from] changes on [state]'s map ([Change]), in the order the moment shows them: the forest
     * drawing back (with the palisade, where there is one), the new plots, the road west over the stream (Zaselek), the road
     * wider (Vas), the open ground round the fire (Zaselek, Trg), its cobbles (Trg). The fire grows at every age, with the
     * villagers' gathering.
     */
    fun changes(from: Age, to: Age, state: GameState): List<Change> = buildList {
        if (to <= from) return@buildList
        add(Change.FOREST)
        if (state.buildings.any { it.type == BuildingType.PALISADE }) add(Change.PALISADE)
        if (newPlots(from, to).isNotEmpty()) add(Change.PLOTS)
        if (from < Age.ZASELEK && to >= Age.ZASELEK) add(Change.WEST)
        if (from < Age.VAS && to >= Age.VAS) add(Change.WIDE)
        if (from < Age.ZASELEK && to >= Age.ZASELEK || from < Age.TRG && to >= Age.TRG) add(Change.SQUARE)
        if (from < Age.TRG && to >= Age.TRG) add(Change.COBBLES)
        add(Change.FIRE)
    }

    /** The plots [to] brings beyond [from]'s (their indices). */
    fun newPlots(from: Age, to: Age): List<Int> = (PLOTS_PER_AGE[from.ordinal] until PLOTS_PER_AGE[to.ordinal]).toList()

    /**
     * The moment of reaching [to] from [from] in [state]'s village: "Nova doba" over the village as it was; each change it
     * brings ([changes]) where it is, one after another (the palisade moves out with the forest, the fire burns up with the
     * gathering); the villagers gather round the fire; the celebration's decorations go up and its music starts; the sign at
     * the road turns to the new age; fireworks and the celebration's name. Then the card ([whatsNew]).
     */
    fun moment(from: Age, to: Age, state: GameState, celebration: Celebration = celebration(to)): Moment {
        val changes = changes(from, to, state)
        val beats = ArrayList<Beat>()
        var t = 0.0
        fun add(kind: Beat.Kind, s: Double, change: Change? = null) { beats += Beat(kind, t, t + s, change); t += s }
        add(Beat.Kind.OPEN, OPEN_S)
        // the palisade moves with the forest, the fire with the gathering: no beats of their own
        for (c in changes) if (c != Change.PALISADE && c != Change.FIRE) add(Beat.Kind.GROW, GROW_S, c)
        add(Beat.Kind.GATHER, GATHER_S)
        add(Beat.Kind.DECORATE, DECORATE_S)
        add(Beat.Kind.SIGN, SIGN_S)
        add(Beat.Kind.CHEER, CHEER_S)
        return Moment(from, to, celebration, beats, newPlots(from, to).filter { p -> state.buildings.none { it.type.onPlot && it.plot == p } }, changes)
    }

    /** The caption of a change, "target · base": "🌲 Gozd se je umaknil · The forest has drawn back" (the plots counted). */
    fun caption(c: Change, from: Age, to: Age): String {
        // the plots' count in it (the other changes have no argument)
        val count = "n" to newPlots(from, to).size
        return "${c.emoji} ${bi(c.key, count)}"
    }

    /** An S-curve from 0 to 1, slow at both ends. */
    fun ease(x: Float): Float {
        val v = x.coerceIn(0f, 1f)
        return v * v * (3f - 2f * v)
    }

    // --- what's new ---------------------------------------------------------------------------------------------------

    /**
     * What reaching [to] brings, from the game's data, each with where it is (a tap leads there): the buildings it opens, the
     * upgrades to the level it allows, its village projects, the scenes that open with it ([scenes]: their `needs`), the
     * people who come with it ([cast]: their `since`), its new plots and bigger stores; and what the rules start with it: the
     * calendar's feasts, the day's surprise and the village's events (Tabor), the school, the feast under the linden and the
     * bear (Zaselek), the smith's better tools (Vas), the market's trading and better prices (Trg).
     */
    fun whatsNew(to: Age, scenes: List<SceneSpec> = emptyList(), cast: List<Villager> = emptyList()): List<NewThing> = buildList {
        val from = Age.entries.getOrNull(to.ordinal - 1) ?: return@buildList
        for ((type, spec) in Catalog.specs) if (spec.minAge == to) add(NewThing(type.emoji, "${type.sl} · ${type.en}", NewThing.Go.BUILD, type.name))
        (2..Catalog.MAX_LEVEL).firstOrNull { Catalog.upgradeAge(it) == to }?.let { lv ->
            add(NewThing("⬆️", bi("ageUp.new.upgrades", "level" to lv), NewThing.Go.BUILD))
        }
        for (p in Catalog.projects.filter { it.age == to }) add(NewThing(p.emoji, p.name, NewThing.Go.PROJECTS, p.id))
        for (sc in scenes.filter { it.needs?.equals(key(to), ignoreCase = true) == true && it.household.isEmpty() }) {
            add(NewThing(sc.emoji, sc.title, NewThing.Go.SCENE, sc.id))
        }
        for (v in cast.filter { it.since?.trim()?.equals(key(to), ignoreCase = true) == true }) {
            add(NewThing(v.emoji, bi("ageUp.new.person", "name" to v.name), NewThing.Go.PERSON, v.id))
        }
        when (to) {
            Age.TABOR -> {
                add(NewThing("🎉", bi("ageUp.new.festivals"), NewThing.Go.FESTIVALS))
                add(NewThing("🎁", bi("ageUp.new.surprise"), NewThing.Go.NONE))
                add(NewThing("🐺", bi("ageUp.new.events"), NewThing.Go.NONE))
            }
            Age.ZASELEK -> {
                add(NewThing("📚", bi("ageUp.new.school"), NewThing.Go.SCHOOL))
                add(NewThing("🍽️", bi("ageUp.new.feast"), NewThing.Go.CHEST))
                add(NewThing("🐻", bi("ageUp.new.bear"), NewThing.Go.NONE))
            }
            Age.VAS -> add(NewThing("⚒️", bi("ageUp.new.tools"), NewThing.Go.CHEST))
            Age.TRG -> add(NewThing("⇄", bi("ageUp.new.market"), NewThing.Go.CHEST))
            else -> Unit
        }
        newPlots(from, to).size.takeIf { it > 0 }?.let { n -> add(NewThing("📐", bi("ageUp.new.plots", "n" to n), NewThing.Go.BUILD)) }
        add(NewThing("🏚️", bi("ageUp.new.stores", "n" to Catalog.baseCap[to.ordinal]), NewThing.Go.INFO))
    }

    // --- when, and the decorations ---------------------------------------------------------------------------------

    /**
     * The day the village reached [age]: as the village keeps it ([GameState.grown]), else the day of its line in the
     * chronicle ([ageOf]) while the chronicle has it; null when neither says (a save from before, its line gone). The first
     * age is the founding's.
     */
    fun reachedOn(s: GameState, age: Age, zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
        if (age > s.age) return null
        s.grown[key(age)]?.let(::isoDay)?.let { return it }
        if (age == Age.OGENJ) return isoDay(s.foundedOn)
        return s.log.lastOrNull { ageOf(it) == age }?.let { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
    }

    /**
     * The age a chronicle line tells the village reached, or null for any other line: its emoji the age's and its text the
     * culture's line for it ("Nova doba: Vas · Vas · A new age!"), or naming the age in the language learned (a line written
     * in another pair, or by an older culture's words).
     */
    fun ageOf(e: LogEntry): Age? = Age.entries.drop(1).firstOrNull { a ->
        e.emoji == a.emoji && (e.text == Cultures.current.chronicle.newAge.bi("age" to a.names) || a.names.by.values.any { e.text.contains(it) })
    }

    /**
     * The decorations on the map [today]: the celebration of the village's age, from the day it came for [DECOR_DAYS]
     * days; none before or after, nor when the day isn't known.
     */
    fun decorOn(s: GameState, today: LocalDate): Celebration? {
        if (s.age == Age.OGENJ) return null
        val on = s.grown[key(s.age)]?.let(::isoDay) ?: return null
        val days = java.time.temporal.ChronoUnit.DAYS.between(on, today)
        return if (days in 0 until DECOR_DAYS) celebration(s.age) else null
    }

    /** Whether [now] is in one of the party's evenings of an age reached on [on]: from [EVENING] until [NIGHT_END] the next morning. */
    fun evening(on: LocalDate, now: LocalDateTime): Boolean = (0 until PARTY_EVENINGS).any { d ->
        val start = on.plusDays(d).atTime(EVENING, 0)
        val end = on.plusDays(d + 1).atTime(NIGHT_END, 0)
        !now.isBefore(start) && now.isBefore(end)
    }

    /**
     * The party on [now] (companion/GAME.md "Ages and their celebrations"): the celebration of the village's age, on the
     * evening of the day it came (from [EVENING], or at once when it came later) and the next, until it was had; null
     * otherwise, for a gathering without a dialog (a culture without its own), and when the day isn't known. Played again
     * from the chronicle any time ([milestones]).
     */
    fun partyNow(s: GameState, now: LocalDateTime): Celebration? {
        if (s.age == Age.OGENJ || s.parties.containsKey(key(s.age))) return null
        val on = s.grown[key(s.age)]?.let(::isoDay) ?: return null
        return celebration(s.age).takeIf { it.party && evening(on, now) }
    }

    /**
     * The party still to come tonight: the village's age reached today (or yesterday, its second evening), its party not
     * had, and the evening not begun yet; for the moment's card and the HUD's "Danes in kmalu".
     */
    fun partyTonight(s: GameState, now: LocalDateTime): Celebration? {
        if (s.age == Age.OGENJ || s.parties.containsKey(key(s.age))) return null
        val on = s.grown[key(s.age)]?.let(::isoDay) ?: return null
        val today = now.toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(on, today)
        if (days !in 0 until PARTY_EVENINGS || now.hour >= EVENING || now.hour < NIGHT_END) return null
        return celebration(s.age).takeIf { it.party }
    }

    /** The village gathers round the fire now: an evening of its age's party (had or not), as the map shows it. */
    fun gathering(s: GameState, now: LocalDateTime): Boolean {
        if (s.age == Age.OGENJ) return false
        val on = s.grown[key(s.age)]?.let(::isoDay) ?: return false
        return evening(on, now)
    }

    /**
     * [age]'s party was played to its end [today]: the first time while it is the village's age, the spirits go up
     * ([PARTY_MORALE]), everyone who lives here grows closer ([PARTY_POINTS] ♥, remembering it when the celebration says
     * how), the chronicle keeps its line, and the village keeps the day ([GameState.parties]); an earlier age's (played again
     * from the chronicle), or one had before, brings nothing: [s] as it is. Returns whether it was the first.
     */
    fun partyHad(s: GameState, age: Age, today: LocalDate, now: Long): Pair<GameState, Boolean> {
        if (age != s.age || age == Age.OGENJ || s.parties.containsKey(key(age))) return s to false
        val c = celebration(age)
        var st = s.copy(morale = (s.morale + PARTY_MORALE).coerceAtMost(100), parties = s.parties + (key(age) to today.toString()))
            .logged(now, c.emoji to c.line)
        val memory = c.memoryText?.let { Memory(today.toString(), it.target, it.base, "party") }
        for (r in st.residents) st = Bonds.add(st, r.id, PARTY_POINTS, today, memory, now = now)
        return Help.earn(st, Catalog.HELP_HAPPENING) to true
    }

    // --- the chronicle ----------------------------------------------------------------------------------------------

    /** Every age the village reached after the first, the latest first: its celebration and the day ([reachedOn]). */
    fun milestones(s: GameState): List<Milestone> =
        Age.entries.filter { it > Age.OGENJ && it <= s.age }.reversed().map { Milestone(it, celebration(it), reachedOn(s, it)) }

    /**
     * [s] as it was the day it reached [age], for that age's moment played again: at that age, without the buildings that
     * came after (built later than that day, when it is known, or on a plot of a later age) and the projects of later ages.
     * The people who live here stay.
     */
    fun asItWas(s: GameState, age: Age, zone: ZoneId = ZoneId.systemDefault()): GameState {
        if (age >= s.age) return s
        val until = reachedOn(s, age, zone)?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        val plots = PLOTS_PER_AGE[age.ordinal]
        val built = s.buildings.filter { b ->
            (until == null || b.builtAt <= 0L || b.builtAt < until) && (!b.type.onPlot || b.plot < plots) && Catalog.spec(b.type).minAge <= age
        }
        val later = Catalog.projects.filter { it.age > age }.map { it.id }.toSet()
        return s.copy(
            age = age, buildings = built.map { it.copy(level = it.level.coerceAtMost(maxLevel(age))) },
            projects = s.projects - later, projectsFinished = s.projectsFinished - later,
        )
    }

    /** The highest level a building has at [age] ([Catalog.upgradeAge]). */
    private fun maxLevel(age: Age): Int = (1..Catalog.MAX_LEVEL).last { it == 1 || Catalog.upgradeAge(it) <= age }

    // --- the party's dialog ----------------------------------------------------------------------------------------

    /**
     * [c]'s party as a dialog for a learner at [level] in the village's [language] (the highest level it has up to theirs,
     * else its easiest), read in [pair] as a scene's dialog is ([inPair]); null when it has none.
     */
    fun dialog(c: Celebration, level: String, language: String, pair: LangPair = L10n.pair): Dialog? {
        if (!c.party) return null
        val lv = Arrivals.levelFor(c.levels.keys, level) ?: return null
        val lines = (c.levels[lv] as? JsonObject)?.get("lines") as? JsonArray ?: return null
        val dialog = mapOf<String, JsonElement>("id" to JsonPrimitive(dialogId(c.age)), "lines" to lines)
        val scene = JsonObject(mapOf("language" to JsonPrimitive(language), "dialogs" to JsonArray(listOf(JsonObject(dialog)))))
        val d = (inPair(scene, pair)["dialogs"] as? JsonArray)?.firstOrNull() ?: return null
        return runCatching { json.decodeFromJsonElement(Dialog.serializer(), d) }.getOrNull()?.takeIf { it.lines.isNotEmpty() }
    }

    /** Who speaks in [d], in the order they first do; the host first. */
    fun speakers(c: Celebration, d: Dialog): List<String> = (listOfNotNull(c.host) + d.lines.mapNotNull { it.who }).distinct()

    /**
     * Who says each speaker's lines tonight: the host their own, else (away, not met) the first of the guests who is here,
     * else anyone of the village who is; a guest theirs when they are here, else another guest who is and doesn't speak in it
     * yet (a child for a child), else anyone else of the village who is, of their age, else the host. So a party never waits
     * for anyone. [present]: who is in the village and met (null: everyone, a village without names); [people]: everyone (the
     * cast and the village's own).
     */
    fun cast(c: Celebration, d: Dialog, present: Set<String>?, people: List<Villager>): Map<String, String> {
        val byId = people.associateBy { it.id }
        fun here(id: String) = present == null || id in present
        val speakers = speakers(c, d)
        val out = LinkedHashMap<String, String>()
        for (h in speakers) if (here(h)) out[h] = h
        for (h in speakers) {
            if (h in out) continue
            val child = byId[h]?.art?.let(ISpy::isChild)
            val used = out.values.toSet() + speakers
            fun fits(v: Villager) = v.id !in used && here(v.id) && v.art != "baby" && (child == null || ISpy.isChild(v.art) == child)
            val other = c.guests.mapNotNull(byId::get).firstOrNull(::fits) ?: people.firstOrNull(::fits)
            out[h] = other?.id ?: out[c.host] ?: out.values.firstOrNull() ?: h
        }
        return out
    }

    /**
     * Everyone at the party, for its stage: who speaks first (the host first), then everyone else who lives here and is in
     * the village tonight ([present]; null: all of [people] who live here), at most [CROWD].
     */
    fun crowd(speakers: List<String>, present: Set<String>?, residents: List<String>): List<String> =
        (speakers + residents.filter { present == null || it in present }).distinct().take(CROWD)

    /** At most this many people on a party's stage. */
    const val CROWD = 14
}
