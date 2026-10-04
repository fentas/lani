package si.lanisce.lani.ui.home

import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.data.FamilyChallenge
import si.lanisce.lani.data.ModuleInfo
import si.lanisce.lani.data.PackInfo
import si.lanisce.lani.data.PackSession
import si.lanisce.lani.data.Stats
import si.lanisce.lani.data.previewLine
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Calendar
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.Projects
import si.lanisce.lani.game.Surprises
import si.lanisce.lani.game.Tomorrow
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.QuestSource
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.ActiveKeeper
import si.lanisce.lani.game.scene.Keepers
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.villagers.Arrival
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.bi
import java.time.LocalDate

// Pure logic behind Home: what to do today, who in the village needs Jan, and the tiles' statuses.

/** Something Home offers to do; the screen maps each to its entry point. */
sealed interface HomeAction {
    /** Identifies the thing itself, so one thing is never offered twice. */
    val key: String

    /** The daily spaced-repetition reviews. */
    data class Review(val cards: Int) : HomeAction {
        override val key get() = "review"
    }

    /** A tutor module: it drills one of the learner's weak spots ([weak]), or it's new since last opened ([isNew]). */
    data class Module(val info: ModuleInfo, val weak: Boolean, val isNew: Boolean) : HomeAction {
        override val key get() = "module:${info.id}"
    }

    /** New words from a word pack. */
    data class Pack(val info: PackInfo, val isNew: Boolean) : HomeAction {
        override val key get() = "pack:${info.id}"
    }

    /** Role-play; [continuing] is the title of a conversation left half-way. */
    data class Talk(val continuing: String?) : HomeAction {
        override val key get() = "talk"
    }

    /** A family member's challenge waiting for an answer. */
    data class Family(val challenge: FamilyChallenge) : HomeAction {
        override val key get() = "family:${challenge.id}"
    }
}

/** "Danes · Today": the one thing to do, at most [MAX_MORE] other ideas, and today's progress. */
data class Today(
    val primary: HomeAction,
    val more: List<HomeAction>,
    /** Reviews due now; 0 means today's reviews are done. */
    val due: Int,
    val minutes: Int,
    /** The daily goal in minutes; 0 when there is none. */
    val goal: Int,
    val streak: Int,
    /** Today already counts for the streak. */
    val practised: Boolean,
) {
    /** 0..1 of the daily goal. */
    val progress: Float get() = if (goal > 0) (minutes.toFloat() / goal).coerceIn(0f, 1f) else 0f

    companion object {
        const val MAX_MORE = 2
    }
}

/** Someone in the village who needs Jan, for the "Vaščani te čakajo" strip. */
data class VillagerNeed(
    val name: String,
    val emoji: String,
    /** Their sprite for the portrait, when known (the cast, else a scene they're in). */
    val art: String?,
    /** Their id in the cast: a tap opens their page. Null when the cast doesn't know them (yet). */
    val villagerId: String?,
    /** What they ask for or what's going on with them ("Stari plug · The old plough"). */
    val request: String,
    /** Something happening now (a scene), not a request. */
    val now: Boolean,
    /** How many more requests or happenings of theirs there are. */
    val more: Int,
    /** Where a tap goes when they have no page. */
    val target: NeedTarget,
)

sealed interface NeedTarget {
    data class Quest(val id: String) : NeedTarget
    data class Scene(val id: String, val focus: String) : NeedTarget
}

/**
 * Someone on the village's map who waits for Jan now, for "Danes · Today" (their bubble, as the scroll's Today lists
 * them): a tap opens them straight away.
 */
sealed interface VillageCall {
    val emoji: String
    val title: String

    /** Someone who joined the village and waits to be met ([id]; companion/VILLAGERS.md "Arrivals"): their introduction. */
    data class Arrival(val id: String, override val emoji: String, override val title: String) : VillageCall

    /** Someone at a spot of the landscape now, his talk not done today (the charcoal burner by his pile): his talk. */
    data class Keeper(val keeper: ActiveKeeper, override val emoji: String, override val title: String) : VillageCall

    /**
     * The treasure map (companion/GAME.md, "The treasure map"): the storyteller's map waiting to be taken, the hunt on, or
     * the new level the tutor hasn't recorded yet: the map.
     */
    data class Treasure(override val emoji: String, override val title: String) : VillageCall
}

/** What the header says about the village, besides "tap to go there". */
sealed interface VillageHint {
    data class Event(val event: GameEvent) : VillageHint
    data object FireLow : VillageHint
    /** Someone would move in, but the houses are full: a nudge to build housing. */
    data class NeedsRoom(val arrival: Arrival) : VillageHint
    data class Calm(val age: Age) : VillageHint
}

/** A tile's live status line; [attention] when something waits for the learner. */
data class TileStatus(val text: String, val attention: Boolean = false)

/** The road to the target level: the level now, the next one, and the words towards it. */
data class Road(val level: String, val next: String, val target: String, val words: Int, val goal: Int) {
    val progress: Float get() = if (goal > 0) (words.toFloat() / goal).coerceIn(0f, 1f) else 0f
    /** The levels from now to the target, both included ("A1", "A2", "B1", "B2"). */
    val steps: List<String>
        get() {
            val from = Stats.LEVELS.indexOf(level).coerceAtLeast(0)
            val to = Stats.LEVELS.indexOf(target).coerceAtLeast(from + 1).coerceAtMost(Stats.LEVELS.lastIndex)
            return Stats.LEVELS.subList(from, to + 1)
        }
}

object HomeLogic {
    /** At most this many people in the strip. */
    const val MAX_NEEDS = 6

    /**
     * Today's plan. The primary action: due reviews first (the daily habit), then a module for a weak spot,
     * then a module the tutor just published, then new words, then talking. The other ideas are of other
     * kinds than the primary and each other (interleaving), a conversation left half-way and a family
     * question first.
     */
    fun today(
        d: Dashboard,
        modules: List<ModuleInfo>,
        newModules: Set<String>,
        packs: List<PackInfo>,
        newPacks: Set<String>,
        talking: String? = null,
        family: List<FamilyChallenge> = emptyList(),
        /** The festival that's on or coming (a calendar id, see game/Calendar.soon): its word pack may be suggested. */
        festival: String? = null,
    ): Today {
        val due = d.dueCards.size
        val weak = modules.filter { m -> m.targets.any { it in d.weakPatterns } }.sortedByDescending { it.id in newModules }
            .firstOrNull()?.let { HomeAction.Module(it, weak = true, isNew = it.id in newModules) }
        val fresh = modules.firstOrNull { it.id in newModules && it.id != weak?.info?.id }
            ?.let { HomeAction.Module(it, weak = false, isNew = true) }
        val pack = PackSession.suggest(packs, d.level, festival) { it.id in newPacks }?.let { HomeAction.Pack(it, it.id in newPacks) }
        val answer = family.firstOrNull { it.answer == null }?.let { HomeAction.Family(it) }

        val primary = listOfNotNull(
            HomeAction.Review(due).takeIf { due > 0 },
            weak,
            fresh,
            pack,
            HomeAction.Talk(talking),
        ).first()
        val ideas = listOfNotNull(talking?.let { HomeAction.Talk(it) }, answer, weak, fresh, pack, HomeAction.Talk(null))
        val more = ArrayList<HomeAction>()
        for (a in ideas) {
            if (more.size >= Today.MAX_MORE) break
            if ((listOf(primary) + more).any { it.key == a.key || it::class == a::class }) continue
            more += a
        }
        return Today(primary, more, due, d.minutesToday, d.goalMinutes, d.streak, d.practisedToday)
    }

    /**
     * The village's day for "Danes · Today" (companion/GAME.md): what's on today (the festival, the day's surprise, the
     * pedlar, today's project step; the first of them) and what's coming ("Jutri · Tomorrow": a festival's countdown,
     * the next step, the pedlar's day …). Empty before the village has people (Tabor).
     */
    fun village(state: GameState?, today: LocalDate = LocalDate.now(), story: si.lanisce.lani.game.Teaser? = null): List<String> {
        val s = state ?: return emptyList()
        if (s.age < Age.TABOR) return emptyList()
        val sp = Surprises.today(s, today)?.takeIf { !it.done && Surprises.whoHere(s, it, today) }
        val now = Calendar.open(s, today).firstOrNull()?.let { d -> "${d.festival.emoji} ${Calendar.todayText(d).replace(" ${d.festival.emoji}", "")}" }
            ?: sp?.let {
                if (it.kind == Surprises.PEDLAR) "🎒 ${bi("homeLogic.pedlarRoadToday")}"
                else "🎁 ${bi("homeLogic.surpriseWaitingRoad")}"
            }
            ?: Projects.today(s, today).firstOrNull()?.let { o -> bi("homeLogic.todaysStep", "specEmoji" to o.spec.emoji, "short" to o.spec.short, "done" to (o.done + 1), "steps" to o.steps) }
        val next = Tomorrow.teasers(s, today, story).firstOrNull()?.line
        return listOfNotNull(now, next)
    }

    /**
     * Who waits for Jan on the village's map now, as their bubbles there: who joined the village and waits to be met
     * (companion/VILLAGERS.md "Arrivals"), then who sits at a spot of the landscape with his talk not done today ([keepers],
     * [Keepers.here]: the charcoal burner by his pile, "At the charcoal pile").
     */
    fun calls(state: GameState?, cast: List<Villager>, keepers: List<ActiveKeeper>, today: LocalDate = LocalDate.now()): List<VillageCall> {
        val s = state ?: return emptyList()
        return TownMarkers.arrivals(s, cast, today).map { VillageCall.Arrival(it.id.removePrefix("arrival:"), it.emoji, it.label) } +
            Keepers.markers(s, keepers, today).mapNotNull { m -> Keepers.of(keepers, m.id)?.let { VillageCall.Keeper(it, m.emoji, m.label) } }
    }

    /**
     * The treasure map on Home, while it asks for something ([status] of the hunt [h], [teller] who gave it, the stations
     * [required] on this phone): the map waiting to be taken, the path so far, or the new level that waits for the tutor
     * ([waiting]); none otherwise.
     */
    fun treasure(
        status: si.lanisce.lani.game.HuntStatus, h: si.lanisce.lani.game.TreasureHunt?, teller: String,
        required: List<si.lanisce.lani.game.Station>, waiting: Boolean,
    ): VillageCall.Treasure? = when {
        waiting -> VillageCall.Treasure("⏳", bi("treasure.tutorWaits"))
        status == si.lanisce.lani.game.HuntStatus.OFFERED -> VillageCall.Treasure("🗺️", bi("treasure.homeOffered", "teller" to teller))
        status == si.lanisce.lani.game.HuntStatus.ON && h != null ->
            VillageCall.Treasure("🗺️", bi("treasure.homeOn", "done" to required.count { h.passed(it) }, "n" to required.size))
        else -> null
    }

    /**
     * Who needs Jan now: each open quest's giver (the villagers' own requests before the tutor's), then the
     * people of the happenings on now; one entry per person, with how many more things of theirs wait.
     * [people] are the village's people (the cast and those born here or who moved in,
     * [si.lanisce.lani.app.VillagerController.people]): their ids open their pages, their sprites draw the
     * portraits. Tutor quests for a module Home already offers today ([offered] module ids) are left out.
     */
    fun needs(
        state: GameState?,
        active: List<ActiveHappening>,
        people: List<Villager>,
        scenes: List<SceneSpec>,
        offered: Set<String> = emptySet(),
        max: Int = MAX_NEEDS,
        today: LocalDate = LocalDate.now(),
    ): List<VillagerNeed> {
        if (state == null) return emptyList()
        fun artOf(name: String): String? =
            people.firstOrNull { it.name == name }?.art ?: scenes.flatMap { it.people }.firstOrNull { it.name == name }?.art

        val all = ArrayList<VillagerNeed>()
        // the requests of who is in the village today (a tutor's request waits for its giver to come), those up now
        val quests = Residents.queue(state, people, today).up.filter { !(it.source == QuestSource.TUTOR && it.moduleId in offered) }
            .sortedBy { it.source != QuestSource.LOCAL }
        for (q in quests) {
            val v = people.firstOrNull { it.name == q.giver }
            all += VillagerNeed(q.giver, v?.emoji ?: q.emoji, artOf(q.giver), v?.id, q.title, now = false, more = 0, NeedTarget.Quest(q.id))
        }
        for (a in active) {
            val p = a.person ?: continue
            val v = p.villager?.let { id -> people.firstOrNull { it.id == id } } ?: people.firstOrNull { it.name == p.name }
            all += VillagerNeed(p.name, v?.emoji ?: p.emoji, v?.art ?: p.art, v?.id, a.happening.title, now = true, more = 0, NeedTarget.Scene(a.scene.id, a.key))
        }
        return all.groupBy { it.name }.values.map { mine -> mine.first().copy(more = mine.size - 1) }.take(max)
    }

    /**
     * The header's word on the village: an event first, then a dying fire, then someone waiting for room
     * ([outlook], see [si.lanisce.lani.app.VillagerController.outlook]), else just the age.
     */
    fun villageHint(state: GameState, outlook: List<Arrival> = emptyList()): VillageHint {
        val room = outlook.firstOrNull { it.waitingFor == ROOM }
        return when {
            state.event != null -> VillageHint.Event(state.event)
            state.fire < 25 -> VillageHint.FireLow
            room != null -> VillageHint.NeedsRoom(room)
            else -> VillageHint.Calm(state.age)
        }
    }

    /** What [Arrival.waitingFor] says when the houses are full. */
    private val ROOM: String get() = bi("common.room")

    /** "🍼 Družina Furlan pričakuje otroka · The Furlan family is expecting: they need room". */
    fun roomText(a: Arrival): String = when (a.kind) {
        Arrival.Kind.CAST -> "${a.emoji} ${bi("homeLogic.whoNeedsRoom", "who" to a.who)}: ${bi("homeLogic.buildHome")}"
        Arrival.Kind.BIRTH -> "🍼 ${bi("homeLogic.whoFamilyExpectingThey", "who" to a.who)}"
        Arrival.Kind.NEWCOMER -> "🧳 ${bi("homeLogic.newcomersWouldCome")}: ${bi("homeLogic.buildHome")}"
    }

    /** Wolves, a bear and storms threaten; the merchant and the festival are visits. */
    fun threat(kind: EventKind): Boolean = kind == EventKind.WOLVES || kind == EventKind.BEAR || kind == EventKind.STORM

    /** "Nove besede": new tutor packs first, else the words still to learn in all packs. */
    fun words(packs: List<PackInfo>, newPacks: Set<String>): TileStatus {
        if (packs.isEmpty()) return TileStatus(bi("homeLogic.wordPacks"))
        val fresh = packs.count { it.id in newPacks }
        if (fresh > 0) return TileStatus(bi("homeLogic.newPacks", "fresh" to fresh), attention = true)
        val left = packs.sumOf { it.left }
        return if (left == 0) TileStatus("✅ ${bi("homeLogic.allLearned")}")
        else TileStatus(bi("homeLogic.leftLearn", "left" to left))
    }

    /** "Izzivi": how many modules are new, else how many there are. */
    fun challenges(modules: List<ModuleInfo>, newModules: Set<String>): TileStatus {
        val fresh = modules.count { it.id in newModules }
        return when {
            fresh > 0 -> TileStatus(bi("homeLogic.newChallenges", "fresh" to fresh), attention = true)
            modules.isEmpty() -> TileStatus(bi("homeLogic.noneYet"))
            else -> TileStatus(bi("homeLogic.challengesToPlay", "modulesSize" to modules.size))
        }
    }

    /**
     * "Branje": how many readings at the learner's [level] (and a step up) are still unread, the tutor's first ([tutor]
     * of them new); else that they're all read.
     */
    fun reading(unread: Int, tutor: Int, all: Int): TileStatus = when {
        tutor > 0 -> TileStatus(bi("homeLogic.newReadings", "n" to tutor), attention = true)
        unread > 0 -> TileStatus(bi("homeLogic.toRead", "n" to unread))
        all == 0 -> TileStatus(bi("homeLogic.nothingToRead"))
        else -> TileStatus("✅ ${bi("homeLogic.allRead")}")
    }

    /** "Pogovor": the conversation left half-way, else what it is. */
    fun talk(continuing: String?): TileStatus =
        if (continuing != null) TileStatus("${bi("todayCard.continue")}: $continuing", attention = true)
        else TileStatus(bi("homeLogic.rolePlay"))

    /** "Družina": questions waiting for an answer, else the latest answer's state, else recording. */
    fun family(challenges: List<FamilyChallenge>): TileStatus {
        val open = challenges.count { it.answer == null }
        if (open > 0) return TileStatus(bi("homeLogic.openWaiting", "open" to open), attention = true)
        val last = challenges.firstOrNull { it.answer != null }
        return when {
            last == null -> TileStatus(bi("homeLogic.recordWords"))
            last.feedback != null -> TileStatus("🧑‍🏫 ${bi("common.feedback")}")
            else -> TileStatus("⏳ ${bi("common.tutorReviewing")}")
        }
    }

    /** The way to the next level: words learned (answered right at least once) of the level's goal. */
    fun road(d: Dashboard): Road = Road(d.level, Stats.nextLevel(d.level), d.targetLevel, d.wordsLearned, Stats.vocabGoal(d.level))

    /**
     * The tutor's plan in one line for its collapsed row: the first bullet (2–3 bullets is the plan's
     * shape), else its first line; markup removed.
     */
    fun planPreview(plan: String, max: Int = 120): String {
        val bullet = Regex("""^\s*(?:[-*•]|\d+[.)])\s+(.+)$""")
        val first = plan.lineSequence().mapNotNull { bullet.find(it)?.groupValues?.get(1) }.firstOrNull()
        return previewLine(first ?: plan, max).trim()
    }
}
