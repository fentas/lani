package si.lanisce.lani.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.Projects
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.DaySky
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.Sky
import si.lanisce.lani.game.scene.parseScenes
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.Visit
import si.lanisce.lani.game.villagers.parseVillagers
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.bi
import java.time.LocalDate

// Visiting a linked town (companion/README.md, "Towns", "Visiting"; plan 2, §3). The app asks its own bridge, which asks
// the host's: GET /towns/:id/public (the town's public state, data/Towns.kt's M1), /towns/:id/villagers (its culture's
// cast) and /towns/:id/scenes (its curated places). A visit is read-only: the map is drawn from a GameState made of the
// public state ([Visits.gameState]), which never reaches the learner's own village, and nothing of the host changes.

/** How a town shows itself: its village's name, its learner's first name, its culture pack and language. */
@Serializable
data class TownHead(val id: String = "", val name: String = "", val learner: String = "", val culture: String = "", val language: String = "")

@Serializable
data class PublicBuilding(val type: String, val plot: Int = NO_PLOT, val level: Int = 1, val damaged: Boolean = false)

/** Someone who lives in the town: a cast member by id, or someone born there or who moved in (with [emoji] and [art]). */
@Serializable
data class PublicResident(val id: String, val name: String = "", val stage: String = "adult", val emoji: String? = null, val art: String? = null)

@Serializable
data class PublicVisitor(val id: String, val name: String = "")

@Serializable
data class PublicSky(
    val scene: String = "",
    val rain: Float = 0f,
    val snow: Float = 0f,
    val fog: Float = 0f,
    val wind: Float = 0f,
    val gloom: Float = 0f,
    val lightning: Boolean = false,
)

/**
 * The land a town stands on, as its bridge shows it (see [si.lanisce.lani.game.Land]): its landscape and the number its
 * map is generated from (the land's own, not the village's dice).
 */
@Serializable
data class PublicLand(val kind: String = "", val map: Int = 0)

@Serializable
data class PublicFestival(
    val id: String,
    val emoji: String = "🎉",
    val name: Map<String, String> = emptyMap(),
    val day: String = "",
    val late: Int = 0,
    val done: Boolean = false,
)

/**
 * A town's public state as its bridge shows it to linked towns (companion/README.md, "What a town shows"), with when
 * this app's bridge had it ([fetchedAt]) and whether that's an older copy because the town isn't answering ([stale]).
 */
@Serializable
data class PublicTown(
    val v: Int = 1,
    val town: TownHead = TownHead(),
    val date: String = "",
    val age: String? = null,
    @SerialName("founded_on") val foundedOn: String? = null,
    val villagers: Int = 0,
    val buildings: List<PublicBuilding> = emptyList(),
    val projects: Map<String, Int> = emptyMap(),
    val residents: List<PublicResident> = emptyList(),
    val visitor: PublicVisitor? = null,
    val sky: PublicSky? = null,
    val festival: PublicFestival? = null,
    /** The land its village stands on; null from an older bridge, or a village from before generated land: the classic valley. */
    val land: PublicLand? = null,
    @SerialName("fetched_at") val fetchedAt: String? = null,
    val stale: Boolean = false,
)

/** A visited town's people or places as the bridge passed them on: [items], when they were had, whether stale. */
data class VisitContent<T>(val items: List<T>, val fetchedAt: String?, val stale: Boolean)

/** What asking the bridge for a part of a visit came to. */
sealed interface VisitFetch<out T> {
    data class Got<T>(val value: T, val stale: Boolean, val fetchedAt: String?) : VisitFetch<T>
    /** Why not, for the learner ("target · base"). */
    data class Failed(val problem: String) : VisitFetch<Nothing>
}

object Visits {
    /** The parts of a visit: the town's state, its people, its places (the bridge's routes under /towns/:id/). */
    const val PUBLIC = "public"
    const val VILLAGERS = "villagers"
    const val SCENES = "scenes"

    fun parsePublic(body: String): PublicTown = json.decodeFromString(PublicTown.serializer(), body)

    /**
     * An answer with a list under [key] ({villagers: […]} or {scenes: […]}, with fetched_at and stale), its items read
     * by [parse] (in the pair shown now: a villager's role, a scene's texts).
     */
    fun <T> parseContent(body: String, key: String, parse: (String) -> List<T>): VisitContent<T> {
        val o = json.parseToJsonElement(body) as? JsonObject ?: throw IllegalArgumentException("not an object")
        val list = o[key] as? JsonArray ?: JsonArray(emptyList())
        return VisitContent(
            items = parse(list.toString()),
            fetchedAt = (o["fetched_at"] as? JsonPrimitive)?.contentOrNull,
            stale = (o["stale"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }

    fun parseVillagersOf(body: String): VisitContent<Villager> = parseContent(body, VILLAGERS, ::parseVillagers)

    fun parseScenesOf(body: String): VisitContent<SceneSpec> = parseContent(body, SCENES, ::parseScenes)

    /**
     * The bridge's answer ([status], [body]) for part [kind] of a visit to [town] (its name): read by [parse], or why it
     * can't be had. A 502 carries the host's trouble (`unreachable`: it isn't answering; `refused`: it no longer knows
     * this town; `unsupported`: its bridge is older than visits); a 404 without a code is a bridge older than visits.
     */
    fun <T> outcome(status: Int, body: String, town: String, parse: (String) -> T, stale: (T) -> Boolean = { false }, fetchedAt: (T) -> String? = { null }): VisitFetch<T> {
        if (status == 200) {
            val v = runCatching { parse(body) }.getOrNull() ?: return VisitFetch.Failed("⚠️ ${bi("towns.answerNotUnderstood")}")
            return VisitFetch.Got(v, stale(v), fetchedAt(v))
        }
        return VisitFetch.Failed(problem(status, Towns.errorCode(body), town))
    }

    /** Why a visited town can't be seen now, "target · base". */
    fun problem(status: Int, code: String?, town: String): String = when {
        code == "unreachable" -> "📡 ${bi("visit.notAnswering", "town" to town.ifBlank { "?" })}"
        code == "refused" || code == "unknown_town" -> "🔒 ${bi("towns.noLongerKnowsYou")}"
        code == "unsupported" -> "🧰 ${bi("visit.hostTooOld")}"
        status == 404 -> "🧰 ${bi("towns.tutorTooOld")}"
        status == 0 -> "📡 ${bi("towns.cantReachTutor")}"
        else -> "⚠️ ${bi("towns.answerNotUnderstood")}"
    }

    /**
     * The visited town as the game draws it: a read-only [GameState] from its public state [p]. Its age, buildings
     * (on their plots, levels, damage), project landmarks, the people who live there ([castIds]: who of them is the
     * host's cast, looked up by id; everyone else comes as they are, a child at their stage), today's visitor, the sky
     * a scene left today, the festival celebrated, and the land it stands on (its map as the host sees it). What the public state doesn't say (resources, the fire's
     * strength, morale, requests, the chest) is left at a new village's, and nothing is ever written back.
     */
    fun gameState(p: PublicTown, castIds: Set<String>, today: LocalDate = LocalDate.now()): GameState {
        val age = Age.entries.firstOrNull { it.name == p.age } ?: Age.OGENJ
        val plots = PLOTS_PER_AGE[age.ordinal]
        val taken = HashSet<Int>()
        val buildings = p.buildings.take(80).mapNotNull { b ->
            val type = BuildingType.entries.firstOrNull { it.name == b.type } ?: return@mapNotNull null
            val plot = if (!type.onPlot) NO_PLOT else b.plot
            if (plot != NO_PLOT && (plot !in 0 until plots || !taken.add(plot))) return@mapNotNull null
            Building("", type, plot, b.level.coerceIn(1, 9), b.damaged)
        }.mapIndexed { i, b -> b.copy(id = "v$i") }
        val since = p.foundedOn?.takeIf { it.isNotBlank() } ?: today.toString()
        val residents = p.residents.take(40).distinctBy { it.id }.map { r ->
            if (r.id in castIds) Resident(r.id, since)
            else Resident(
                id = r.id, since = since, name = r.name.ifBlank { r.id }, emoji = r.emoji, art = r.art,
                voice = voiceOf(r), born = bornFor(r.stage, today),
            )
        }
        return GameState(
            seed = seedOf(p.town.id),
            age = age,
            buildings = buildings,
            villagers = p.villagers.coerceIn(0, 200),
            lastTick = today.toString(),
            foundedOn = p.foundedOn.orEmpty(),
            residents = residents,
            visitor = p.visitor?.takeIf { it.id.isNotBlank() }?.let { Visit(it.id, today.toString()) },
            projects = p.projects.mapNotNull { (id, n) -> Projects.spec(id)?.let { id to n.coerceIn(0, it.steps.size) } }.filter { it.second > 0 }.toMap(),
            sky = p.sky?.let { s -> DaySky(today.toString(), s.scene, Sky(s.rain.unit(), s.snow.unit(), s.fog.unit(), s.wind.unit(), s.gloom.unit(), s.lightning)) },
            festivals = p.festival?.takeIf { it.done && it.day.isNotBlank() }?.let { mapOf(it.id to it.day) }.orEmpty(),
            land = p.land?.takeIf { it.kind.isNotBlank() }?.let { Land(it.kind, it.map) },
        )
    }

    private fun Float.unit() = if (isNaN()) 0f else coerceIn(0f, 1f)

    /** The day's dice of a visited town: from its id, so it reads the same on every visit. */
    fun seedOf(id: String): Long = id.take(15).toLongOrNull(16) ?: id.hashCode().toLong()

    /** A birthday that gives [stage] today ([si.lanisce.lani.game.villagers.Residents.Stage]); a grown-up has none. */
    fun bornFor(stage: String, today: LocalDate): String? = when (stage) {
        "baby" -> today.minusDays(10)
        "child" -> today.minusDays(100)
        "youth" -> today.minusDays(300)
        else -> null
    }?.toString()

    /** Someone the town made (their [PublicResident.art] and [PublicResident.emoji]): a woman or a man, for their voice. */
    fun voiceOf(r: PublicResident): String =
        if (r.art in setOf("woman", "grandma", "aunt", "teacher") || r.emoji in setOf("👩", "👧", "👵", "👱‍♀️", "🧕")) "female" else "male"

    /** "Na obisku · Visiting: Il mio villaggio (Mia)" */
    fun banner(name: String, learner: String): String =
        "🧳 ${bi("visit.visiting")}: ${name.ifBlank { "?" }}" + if (learner.isBlank()) "" else " ($learner)"

    /** When the shown state was had, "HH:mm" today, else the day ("2026-09-26"); null when unknown. */
    fun asOf(fetchedAt: String?, today: LocalDate = LocalDate.now(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String? {
        val t = fetchedAt?.let { runCatching { java.time.Instant.parse(it).atZone(zone) }.getOrNull() } ?: return null
        return if (t.toLocalDate() == today) "%02d:%02d".format(t.hour, t.minute) else t.toLocalDate().toString()
    }

    /** The festival that is on in the town, in the pair shown now: "🍇 Vendemmia · Trgatev". */
    fun festivalLine(f: PublicFestival): String {
        val pair = L10n.pair
        val t = f.name[pair.target.code] ?: f.name.values.firstOrNull() ?: f.id
        val b = f.name[pair.base.code]?.takeIf { it != t }
        return "${f.emoji} $t" + (b?.let { " · $it" } ?: "")
    }
}

/**
 * The world a visit shows (plan 2, §3.2): the host town's language, explained in the learner's base ([pairFor]), and its
 * culture pack's look, names and people ([Cultures.visiting]), until the visit ends ([leave]); the learner's own pair
 * and culture stay what they are underneath, and come back.
 */
object VisitWorld {
    val active: Boolean get() = L10n.visit != null

    /** The visited town's language ("it"), whose profile a visit's learning goes to; null at home. */
    val language: String? get() = L10n.visit?.target?.code

    /**
     * The pair on a visit to a town speaking [host]: the host's language, explained in the learner's base ([own].base);
     * when that is the host's language itself (the second learner, Italian from Slovene, visiting a Slovene town), the two swap
     * ([LangPair.withTarget]). A language the app doesn't have leaves [own].
     */
    fun pairFor(host: String, own: LangPair): LangPair = Lang.of(host)?.let { own.withTarget(it) } ?: own

    /** Shows the town of [language] and [culture] from now on; a culture pack this app doesn't bundle keeps its own look. */
    fun enter(language: String, culture: String) {
        L10n.visit = pairFor(language, L10n.ownPair)
        Cultures.visiting = culture.takeIf { it.isNotBlank() }?.let { runCatching { Cultures.load(it) }.getOrNull() }
    }

    /** Home again: the learner's own pair and culture. */
    fun leave() {
        L10n.visit = null
        Cultures.visiting = null
    }
}
