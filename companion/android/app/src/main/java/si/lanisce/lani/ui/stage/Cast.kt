package si.lanisce.lani.ui.stage

import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Scenario
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.Events
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Surprises
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.VillagerLines
import si.lanisce.lani.l10n.bi
import java.time.LocalDate

// Who asks in an intro and who stands on the training stage (companion/VILLAGERS.md, "The training stage").
// Pure: everything comes from the cast, the village state and the day.

/**
 * Someone on the stage or asking in an intro: a villager of the cast, or a stand-in with built-in lines
 * when the cast doesn't know them (yet: an older node, a tutor's giver, a scenario's character).
 */
data class StagePerson(
    /** Their id in the cast; null for a stand-in, who has no friendship to grow. */
    val id: String?,
    /** "Babica Micka" */
    val name: String,
    val emoji: String,
    /** A people sprite ([si.lanisce.lani.game.scene.SceneArt.people]) for the portrait. */
    val art: String,
    /** female | male ([Clips.FEMALE], [Clips.MALE]): their gender's narrator, and the fallback for [speaker]. */
    val voice: String,
    /** "Babica · Grandmother"; empty when unknown. */
    val role: String,
    /** ti | vi: how they speak to Jan. */
    val register: String,
    val lines: VillagerLines = VillagerLines(),
    /** Friendship level with Jan, 0 (stranger) … 4 (like family). */
    val level: Int = 0,
    /** Their own voice in the node's voice cast ("grandma", "gruff" …; companion/voice-cast.json), else [voice]. */
    val speaker: String = voice,
) {
    /** "Micka": the name without the title, for "+10 ♥ Micka". */
    val short: String get() = name.trim().substringAfterLast(' ')

    /** Varies the portrait of people who share a sprite (the village's own people). */
    val seed: Int get() = (id ?: name).hashCode()

    val formal: Boolean get() = register == "vi"
}

/** What a run is: where it came from decides who is on stage and what the intro says. */
sealed interface Run {
    /** The daily reviews: today's companion. */
    data object Review : Run
    /** A villager's local quest. */
    data class Quest(val giver: String, val emoji: String) : Run
    /** A module; [giver] is its quest's giver when it has one. */
    data class Module(val giver: String?, val emoji: String? = null) : Run
    /** Defending the village against an event. */
    data class Event(val kind: EventKind) : Run
    /** A gathering run for one resource. */
    data class Gather(val res: Res) : Run
    /** A word pack; [giver] is the pack's giver. */
    data class Pack(val giver: String?, val emoji: String? = null) : Run
    /** A role-play with the tutor playing [scenario]'s character. */
    data class Talk(val scenario: Scenario) : Run
    /** The day's surprise with a stranger from the road ([kind]: the pilgrim), who is on the stage himself. */
    data class Stranger(val kind: String) : Run
}

object Cast {
    /** Who welcomes the learner before they've met anyone: the culture's host (Babica Micka in Primorska). */
    val HOST: String get() = Cultures.current.world.host.name
    /** Scenario ids of role-plays with a villager ("villager:luka"). */
    const val VILLAGER_SCENARIO = "villager:"

    /** Who stands with Jan against each event (VILLAGERS.md): the culture's (events.json); null: today's companion. */
    fun defender(kind: EventKind): String? = Events.texts(kind).leader

    /** Who goes gathering with Jan: the fields, the forest, the quarry, the linden (VILLAGERS.md; world.json). */
    fun gatherer(res: Res): String? = Cultures.current.leader(res)

    /**
     * Who's on stage for [run] (VILLAGERS.md): the quest's giver; for a module, its quest's giver; for an
     * event, the defender; for gathering, the one who works there; for a word pack, its giver; for a
     * role-play, the villager or character played; otherwise (the reviews, a module or pack without a
     * giver) today's companion. Someone of the cast who isn't in the village today (they haven't moved in
     * yet) never is: today's companion stands in. [daily] pins today's companion once picked, so it stays
     * the same all day.
     */
    fun onStage(run: Run, cast: List<Villager>, state: GameState?, today: LocalDate, daily: String? = null): StagePerson {
        fun here(name: String?): String? = name?.takeIf { state == null || Residents.here(state, it, cast, today) }
        fun companion() = today(cast, state, today, daily)
        return when (run) {
            Run.Review -> companion()
            is Run.Quest -> here(run.giver)?.let { person(it, run.emoji, cast, state) } ?: companion()
            is Run.Module -> here(run.giver)?.let { person(it, run.emoji, cast, state) } ?: companion()
            is Run.Event -> here(defender(run.kind))?.let { person(it, null, cast, state) } ?: companion()
            is Run.Gather -> here(gatherer(run.res))?.let { person(it, null, cast, state) } ?: companion()
            is Run.Pack -> here(run.giver)?.let { person(it, run.emoji, cast, state) } ?: companion()
            is Run.Talk -> talker(run.scenario, cast, state)
            is Run.Stranger -> Surprises.strangers[run.kind]?.let(::stranger) ?: companion()
        }
    }

    /**
     * A stranger of the day's surprise ([Surprises.strangers]: the pedlar, the pilgrim) as someone on the stage: their
     * sprite, voice and lines, but no friendship to grow (no id, a stranger's level).
     */
    fun stranger(v: Villager): StagePerson = of(v, null).copy(id = null, level = 0)

    /**
     * Today's companion, [daily] (the id picked earlier today) while they are still in the cast and in the village for the
     * learner (met: someone who waits for their introduction never is, VILLAGERS.md "Arrivals").
     */
    fun today(cast: List<Villager>, state: GameState?, today: LocalDate, daily: String? = null): StagePerson {
        val present = state?.let { Residents.present(it, today) }
        val v = daily?.let { id -> cast.firstOrNull { it.id == id && (present == null || id in present) } } ?: todaysCompanion(cast, state, today)
        return v?.let { of(it, state) } ?: Cultures.current.world.host.let { person(it.name, it.emoji, cast, state) }
    }

    /**
     * The friend Jan hasn't seen for the longest, among those they've met who are in the village today; the
     * day's dice pick among equals. Null until Jan has met anyone of them (then it's Babica Micka).
     */
    fun todaysCompanion(cast: List<Villager>, state: GameState?, today: LocalDate): Villager? {
        val bonds = state?.bonds.orEmpty()
        val present = state?.let { Residents.present(it, today) }
        val met = cast.filter { bonds[it.id]?.met != null && it.art != "baby" && (present == null || it.id in present) }
        if (met.isEmpty()) return null
        fun seen(v: Villager) = bonds[v.id]?.seen ?: ""
        val oldest = met.minOf(::seen) // ISO dates sort as text
        val longest = met.filter { seen(it) == oldest }.sortedBy { it.id }
        val i = (Happenings.roll(state?.seed ?: 0L, today, "stage/companion") * longest.size).toInt()
        return longest[i.coerceIn(0, longest.lastIndex)]
    }

    /** A villager of the cast by [name] (or by the id it suggests), else a stand-in. */
    fun person(name: String, emoji: String?, cast: List<Villager>, state: GameState?): StagePerson =
        find(name, cast)?.let { of(it, state) } ?: standIn(name, emoji)

    fun find(name: String, cast: List<Villager>): Villager? =
        cast.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: cast.firstOrNull { it.id == idOf(name) }

    /** The id a name suggests: its last word, lower case, without č/š/ž ("Teta Ančka" → "ancka"). */
    fun idOf(name: String): String = fold(name.trim().substringAfterLast(' ').lowercase())

    fun of(v: Villager, state: GameState?) = StagePerson(
        id = v.id, name = v.name, emoji = v.emoji, art = v.art,
        voice = if (v.voice == Clips.MALE) Clips.MALE else Clips.FEMALE,
        role = v.role, register = if (v.register == "ti") "ti" else "vi", lines = v.lines,
        level = state?.let { Bonds.level(it, v.id) } ?: 0,
        speaker = v.speakerVoice,
    )

    /** Someone the cast doesn't know: the sprite, voice and role their title suggests; built-in lines. */
    fun standIn(name: String, emoji: String?, voice: String? = null, register: String = "ti"): StagePerson {
        val title = TITLES.entries.firstOrNull { (t, _) -> name.trim().startsWith("$t ", ignoreCase = true) }?.value
        val female = voice?.let { it == Clips.FEMALE } ?: title?.female ?: femaleName(name)
        return StagePerson(
            id = null,
            name = name.trim(),
            emoji = emoji ?: title?.emoji ?: if (female) "👩" else "👨",
            art = title?.art ?: if (female) "woman" else "man",
            voice = if (female) Clips.FEMALE else Clips.MALE,
            role = title?.role ?: "",
            register = register,
        )
    }

    /** A role-play's other person: the villager ("villager:<id>", or the cast's name), else the scenario's character. */
    fun talker(s: Scenario, cast: List<Villager>, state: GameState?): StagePerson {
        val id = s.id.takeIf { it.startsWith(VILLAGER_SCENARIO) }?.removePrefix(VILLAGER_SCENARIO)
        val v = id?.let { i -> cast.firstOrNull { it.id == i } } ?: find(s.character, cast)
        if (v != null) return of(v, state)
        val role = s.role.substringAfter(',', "").trim().trimEnd('.', '…', ' ')
        return standIn(s.character, s.emoji.takeIf { id != null }, s.characterVoice, register = "vi").let { c ->
            if (role.isNotEmpty()) c.copy(role = role) else c // the scenario's own words are more specific than a title's
        }
    }

    /** Friendship points a session on the stage still brings today (0 once the day's trainings are used). */
    fun trainingLeft(state: GameState?, id: String?, today: LocalDate): Int {
        if (id == null) return 0
        val b = state?.bonds?.get(id) ?: return Bonds.TRAINING
        val used = if (b.seen == today.toString()) b.trainedToday else 0
        return Bonds.TRAINING.coerceAtMost(Bonds.TRAINING_PER_DAY - used).coerceAtLeast(0)
    }

    /** [roleKey]: the role's label ("Babica · Grandmother"), read when shown so it is in the pair of the moment. */
    private class Title(val art: String, val female: Boolean, private val roleKey: String, val emoji: String) {
        val role: String get() = bi(roleKey)
    }

    /** Village titles and what they suggest about a person. */
    private val TITLES = mapOf(
        "Babica" to Title("grandma", true, "cast.grandmother", "👵"),
        "Dedek" to Title("grandpa", false, "cast.grandfather", "👴"),
        "Stari" to Title("grandpa", false, "cast.storyteller", "👴"),
        "Pastir" to Title("shepherd", false, "cast.shepherd", "🐑"),
        "Kovač" to Title("smith", false, "cast.smith", "⚒️"),
        "Učiteljica" to Title("teacher", true, "cast.teacherF", "👩‍🏫"),
        "Učitelj" to Title("teacher", false, "cast.teacherM", "👨‍🏫"),
        "Mlinar" to Title("farmer", false, "cast.miller", "🧑‍🌾"),
        "Kmet" to Title("farmer", false, "common.farmer", "🧑‍🌾"),
        "Čebelar" to Title("beekeeper", false, "common.beekeeper", "🐝"),
        "Gostilničarka" to Title("innkeeper", true, "cast.innkeeperF", "🍷"),
        "Gostilničar" to Title("innkeeper", false, "cast.innkeeperM", "🍷"),
        "Natakar" to Title("innkeeper", false, "cast.waiter", "🍽️"),
        "Vinar" to Title("winemaker", false, "cast.winemaker", "🍇"),
        "Teta" to Title("aunt", true, "cast.aunt", "📻"),
        "Soseda" to Title("aunt", true, "cast.neighbourF", "👩"),
        "Sosed" to Title("farmer", false, "cast.neighbourM", "👨"),
        "Tašča" to Title("aunt", true, "cast.motherLaw", "👩"),
        // The role-play scenarios' people (companion/scenarios): the village's everyday sprites.
        "Gospod" to Title("man", false, "cast.mister", "👨"),
        "Gospa" to Title("woman", true, "cast.missus", "👩"),
        "Prodajalka" to Title("woman", true, "cast.shopAssistantF", "🛍️"),
        "Prodajalec" to Title("man", false, "cast.shopAssistantM", "🛍️"),
        "Dr." to Title("man", false, "cast.doctorM", "🩺"),
        "Zdravnica" to Title("woman", true, "cast.doctorF", "🩺"),
    )

    /** Slovene given names ending in -a are mostly women's; these are men's. */
    private val MEN_IN_A = setOf("luka", "jaka", "miha", "andrija", "saša", "nikola")

    private fun femaleName(name: String): Boolean {
        val first = name.trim().substringAfterLast(' ').lowercase()
        return first.endsWith("a") && first !in MEN_IN_A
    }

    private fun fold(s: String) = s.replace('č', 'c').replace('š', 's').replace('ž', 'z').replace('ć', 'c').replace('đ', 'd')
}
