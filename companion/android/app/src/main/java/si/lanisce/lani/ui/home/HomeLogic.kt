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
import si.lanisce.lani.game.Events
import si.lanisce.lani.game.Projects
import si.lanisce.lani.game.Surprises
import si.lanisce.lani.game.Tomorrow
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.QuestQueue
import si.lanisce.lani.game.QuestSource
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.ActiveKeeper
import si.lanisce.lani.game.scene.Keepers
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.villagers.Arrival
import si.lanisce.lani.game.villagers.Reactions
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Stir
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.game.whyText
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

    /**
     * "🔢 Števila · Numbers" (data/NumbersPractice.kt), open: [due] numbers missed due again today, else the newest range
     * open ([range]) and its last answers right of how many ([right], [of]).
     */
    data class Numbers(val due: Int, val range: String, val right: Int, val of: Int) : HomeAction {
        override val key get() = "numbers"
    }

    /** The school's unit suggested next (companion/GAME.md, "The school"): [unit]'s [title] of [level]; [started] once begun. */
    data class Lesson(val unit: String, val title: String, val emoji: String, val level: String, val started: Boolean) : HomeAction {
        override val key get() = "school"
    }

    /**
     * The village's threat (wolves, a bear, a storm: companion/GAME.md "Events"), to defend it before its deadline: the
     * culture's call ([title], "🐺 Branimo vas! · Defend the village!"), the event's own name ([what]), the time [left].
     */
    data class Defend(val kind: EventKind, val title: String, val what: String, val left: String) : HomeAction {
        override val key get() = "defend"
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
    /** Why their request waits ("🐺 Po volkovih · After the wolves": wolves, a bear or a storm about); dim then. */
    val waits: String? = null,
    /** The village's threat itself ("🐺 Branimo vas! · Defend the village!"): first, in red, a tap its run. */
    val alarm: Boolean = false,
)

sealed interface NeedTarget {
    data class Quest(val id: String) : NeedTarget
    data class Scene(val id: String, val focus: String) : NeedTarget

    /** The village's event's run (wolves, a bear, a storm). */
    data object Event : NeedTarget
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

    /** At most this many of the village's calls in "Danes · Today" (who to meet, the burner …); the rest: "še N v vasi ›". */
    const val MAX_CALLS = 2

    /**
     * The village's threat as "Danes · Today"'s one thing to do ([today]'s primary): wolves, a bear or a storm on now
     * ([threat]), with the culture's call to defend it and the time left; null when there is none.
     */
    fun defend(state: GameState?, now: Long = System.currentTimeMillis()): HomeAction.Defend? {
        val e = state?.event?.takeIf { threat(it.kind) } ?: return null
        val call = Reactions.call(Stir(e.kind, Stir.Phase.ON, 0.0, e.strength))?.bi()
        // the badge shows the event's emoji: not again before its name
        val title = Events.title(state, e).removePrefix("${e.kind.emoji} ")
        return HomeAction.Defend(e.kind, call ?: title, if (call != null) title else "", si.lanisce.lani.ui.game.timeLeft(e.deadline, now))
    }

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
        /** The school's unit suggested next, once the class meets: an idea after the modules, before a pack. */
        lesson: HomeAction.Lesson? = null,
        /**
         * The numbers module, once open and not played today ([numbersIdea]): an idea after the modules while numbers
         * missed are due again, else after a pack (light: only where there is room).
         */
        numbers: HomeAction.Numbers? = null,
        /**
         * The village's threat ([defend]): the one thing to do while it's on (it is lost at its deadline), the reviews then
         * the first idea.
         */
        threat: HomeAction.Defend? = null,
    ): Today {
        val due = d.dueCards.size
        val weak = modules.filter { m -> m.targets.any { it in d.weakPatterns } }.sortedByDescending { it.id in newModules }
            .firstOrNull()?.let { HomeAction.Module(it, weak = true, isNew = it.id in newModules) }
        val fresh = modules.firstOrNull { it.id in newModules && it.id != weak?.info?.id }
            ?.let { HomeAction.Module(it, weak = false, isNew = true) }
        val pack = PackSession.suggest(packs, d.level, festival) { it.id in newPacks }?.let { HomeAction.Pack(it, it.id in newPacks) }
        val answer = family.firstOrNull { it.answer == null }?.let { HomeAction.Family(it) }

        val primary = listOfNotNull(
            threat,
            HomeAction.Review(due).takeIf { due > 0 },
            weak,
            fresh,
            pack,
            HomeAction.Talk(talking),
        ).first()
        val numbersDue = numbers?.takeIf { it.due > 0 }
        val numbersLater = numbers?.takeIf { it.due == 0 }
        val ideas = listOfNotNull(
            HomeAction.Review(due).takeIf { due > 0 && threat != null },
            talking?.let { HomeAction.Talk(it) }, answer, weak, fresh, numbersDue, lesson, pack, numbersLater, HomeAction.Talk(null),
        )
        val more = ArrayList<HomeAction>()
        for (a in ideas) {
            if (more.size >= Today.MAX_MORE) break
            if ((listOf(primary) + more).any { it.key == a.key || it::class == a::class }) continue
            more += a
        }
        return Today(primary, more, due, d.minutesToday, d.goalMinutes, d.streak, d.practisedToday)
    }

    /**
     * The numbers' idea for "Danes · Today" (companion/README.md, "Numbers"): open ([open]: the unit stevila's intro done) and
     * not played today ([day]: its last run's), with the numbers missed due again ([due]) or the newest range open; null
     * otherwise.
     */
    fun numbersIdea(open: Boolean, p: si.lanisce.lani.data.NumbersProgress?, today: LocalDate): HomeAction.Numbers? {
        if (!open || p?.day == today.toString()) return null
        val newest = si.lanisce.lani.data.NumbersPractice.newest(p, true) ?: return null
        val (right, of) = si.lanisce.lani.data.NumbersPractice.score(p, newest)
        return HomeAction.Numbers(si.lanisce.lani.data.NumbersPractice.due(p, today).size, newest.label, right, of)
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
     * portraits. Tutor quests for a module Home already offers today ([offered] module ids) are left out. While wolves, a
     * bear or a storm are about (companion/GAME.md "People react") the threat comes first ("🐺 Branimo vas! · Defend the
     * village!", its leader's portrait, a tap its run) and the requests up wait, dim, until it's over ([VillagerNeed.waits]).
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
        // wolves, a bear or a storm about: the threat first, the requests waiting for it to pass
        threatNeed(state, people)?.let { all += it }
        // the requests of who is in the village today (a tutor's request waits for its giver to come), those up now
        val queue = Residents.queue(state, people, today)
        val held = queue.later.filter { it.why is QuestQueue.Why.Danger }
        val quests = (queue.up.map { it to null } + held.map { it.quest to whyText(it.why) })
            .filter { (q, _) -> !(q.source == QuestSource.TUTOR && q.moduleId in offered) }
            .sortedBy { (q, _) -> q.source != QuestSource.LOCAL }
        for ((q, waits) in quests) {
            val v = people.firstOrNull { it.name == q.giver }
            all += VillagerNeed(q.giver, v?.emoji ?: q.emoji, artOf(q.giver), v?.id, q.title, now = false, more = 0, NeedTarget.Quest(q.id), waits = waits)
        }
        for (a in active) {
            val p = a.person ?: continue
            val v = p.villager?.let { id -> people.firstOrNull { it.id == id } } ?: people.firstOrNull { it.name == p.name }
            all += VillagerNeed(p.name, v?.emoji ?: p.emoji, v?.art ?: p.art, v?.id, a.happening.title, now = true, more = 0, NeedTarget.Scene(a.scene.id, a.key))
        }
        return all.groupBy { it.name }.values.map { mine -> mine.first().copy(more = mine.size - 1) }.take(max)
    }

    /**
     * The village's threat for "Vaščani te čakajo" while wolves, a bear or a storm are about ([Reactions.quiet]): the culture's
     * call to its run ("🐺 Branimo vas! · Defend the village!", [Reactions.call]; its title without a reactions file), the
     * event's title under it, the portrait of its leader when they live here (the smith against the wolves); null otherwise.
     */
    fun threatNeed(state: GameState, people: List<Villager>): VillagerNeed? {
        val e = state.event?.takeIf { Reactions.quiet(state) } ?: return null
        val call = Reactions.call(Stir(e.kind, Stir.Phase.ON, 0.0, e.strength))?.bi()
        val title = Events.title(state, e)
        val leader = runCatching { Events.texts(e.kind).leader }.getOrNull()?.let { name -> people.firstOrNull { it.name == name } }
        return VillagerNeed(
            call ?: title.removePrefix("${e.kind.emoji} "), e.kind.emoji, leader?.art, null, if (call != null) title else "", now = false, more = 0,
            NeedTarget.Event, alarm = true,
        )
    }

    /**
     * The header's word on the village: an event first, then hearths going out (companion/GAME.md "Hearths"), then someone waiting for room
     * ([outlook], see [si.lanisce.lani.app.VillagerController.outlook]), else just the age.
     */
    fun villageHint(state: GameState, outlook: List<Arrival> = emptyList()): VillageHint {
        val room = outlook.firstOrNull { it.waitingFor == ROOM }
        return when {
            state.event != null -> VillageHint.Event(state.event)
            si.lanisce.lani.game.Hearths.short(state) -> VillageHint.FireLow
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

    /** "🔤 Moje besede": how many of the learner's words they know, and how many they are still learning. */
    fun myWords(known: Int, learning: Int): TileStatus = when {
        known + learning == 0 -> TileStatus(bi("nav.myWordsNone"))
        learning > 0 -> TileStatus("${bi("myWords.known", "n" to known)} · ${bi("myWords.learning", "n" to learning)}")
        else -> TileStatus(bi("myWords.known", "n" to known))
    }

    /** "📖 Slovnica": the pages met ([new] of them this week), else that the book fills as rules are met. */
    fun grammarBook(pages: Int, new: Int): TileStatus = when {
        new > 0 -> TileStatus("✨ ${bi("nav.grammarNew", "n" to new)}", attention = true)
        pages > 0 -> TileStatus(bi("nav.grammarPages", "n" to pages))
        else -> TileStatus(bi("nav.grammarEmpty"))
    }

    /** "🔢 Števila", once open: the numbers missed due again, else the newest range open. */
    fun numbers(idea: HomeAction.Numbers?, range: String?): TileStatus = when {
        idea != null && idea.due > 0 -> TileStatus("🔁 ${bi("numbers.dueAgain", "n" to idea.due)}", attention = true)
        else -> TileStatus(range ?: bi("numbers.title"))
    }

    /** "Plonk listek": how many cards are new (unlocked, never opened), else how many are on it. */
    fun cheatSheet(cards: Int, fresh: Int): TileStatus = when {
        fresh > 0 -> TileStatus("✨ ${bi("cheatSheet.fresh", "n" to fresh)}", attention = true)
        else -> TileStatus(bi("cheatSheet.cards", "n" to cards))
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
