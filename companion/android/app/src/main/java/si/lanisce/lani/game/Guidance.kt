package si.lanisce.lani.game

import kotlinx.serialization.Serializable
import si.lanisce.lani.game.scene.TownMarker
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Localized
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// "💡 Namigi · Tips" (companion/GAME.md "Guidance"; docs/plans/05-built/guidance.md): the game notices a moment (a store
// full, a first school, an upgrade within reach …), says in one line what the learner can do now, and offers one tap to
// do it. One at a time, at its place (the map, Home, a scene), gone once dealt with. Pure: the catalogue, the queue and
// what the village keeps of it; the screens draw it (ui/game/TipCard.kt).

/**
 * What the village keeps of its tips ([GameState.tips]), synced with it so another device doesn't repeat them. Each map is
 * by moment id ([TipMoment.id]); days are ISO dates. An older save has none (every field defaults), an older app drops it and
 * the bridge keeps it (PUT /game, as the course).
 */
@Serializable
data class Tips(
    /** The day each moment was dealt with: its action tapped, "Razumem · Got it", or the learner did the thing anyway. */
    val seen: Map<String, String> = emptyMap(),
    /** The day each moment was last first shown (a bubble on the map, a card on Home or in a scene). */
    val shown: Map<String, String> = emptyMap(),
    /** The day a moment put off ("Pozneje · Later") comes back: the next day. */
    val later: Map<String, String> = emptyMap(),
    /** The day the tips began in this village; "" before ([Guidance.begin]). */
    val since: String = "",
    /**
     * The moments already past when the tips began in a village from before (Teo's wisdom full, his school standing): the
     * still useful ones come once, gently, at most [Guidance.OLD_A_DAY] a day, never a flood on update.
     */
    val backlog: Set<String> = emptySet(),
    /** When a tip was last first shown or dealt with (epoch ms): the next new one waits a quiet moment ([Guidance.GAP_MS]). */
    val lastAt: Long = 0,
    /** What the learner did that a tip would teach, wherever it was done ("trade", "book", "scene:<id>" …: [Guidance.did]). */
    val did: Set<String> = emptySet(),
    /** "💡 Namigi · Tips" off in the settings: none shows. */
    val off: Boolean = false,
    /** The tutor pointed at a moment (show_tip, "show Teo how to trade"): it comes first at its place, once, until dealt with. */
    val asked: TipAsk? = null,
)

/** The tutor's pointer ([Tips.asked]): the moment, a line of why (the learner's base), when it was asked (epoch ms). */
@Serializable
data class TipAsk(val moment: String, val note: String? = null, val at: Long = 0)

/** Where a tip shows: a bubble at its thing on the map, a card in Home's "Danes · Today", a card in a scene. */
enum class TipPlace { MAP, HOME, SCENE }

/**
 * What a tip's one button does: opens the right screen with the right thing selected. [label] is its key in the string
 * tables (the button reads it through bi()).
 */
sealed interface TipAction {
    val label: String

    /** "⇄ Zamenjaj · Trade", [give] chosen to give (the getting one the village is shortest of: [Trade.suggest]). */
    data class Trade(val give: Res) : TipAction { override val label get() = "tips.do.trade" }
    /** "🔨 Gradi · Build": the build sheet. */
    data object Build : TipAction { override val label get() = "tips.do.build" }
    /** The building's card (its upgrade, its repair, who lives there). */
    data class Building(val id: String, override val label: String = "tips.do.card") : TipAction
    /** The run of the words building [id]'s upgrade asks for (companion/GAME.md "Knowledge builds"). */
    data class Words(val id: String) : TipAction { override val label get() = "tips.do.words" }
    /** Into scene [id] (seen in [building], a house's room). */
    data class Scene(val id: String, val building: String? = null) : TipAction { override val label get() = "tips.do.goIn" }
    /** The school's unit suggested next ([unit]), else the school. */
    data class Lesson(val unit: String?) : TipAction { override val label get() = "tips.do.lesson" }
    /** "🧰 Skrinja · The chest": its tools (the forge), goods, the stalls. */
    data object Chest : TipAction { override val label get() = "tips.do.chest" }
    /** The village projects' sheet at project [id]. */
    data class Projects(val id: String?) : TipAction { override val label get() = "tips.do.project" }
    /** Villager's request [id]: its run. */
    data class Quest(val id: String) : TipAction { override val label get() = "tips.do.help" }
    /** Request [id], tried below the mark and back tomorrow: its drill ("🎯 Vadi in poskusi znova"), which brings it back today. */
    data class Retry(val id: String) : TipAction { override val label get() = "quests.practiseRetry" }
    /** Villager [id]'s card (today's visitor). */
    data class Villager(val id: String) : TipAction { override val label get() = "tips.do.hello" }
    /** Someone who joined the village ([id]): their introduction. */
    data class Meet(val id: String) : TipAction { override val label get() = "tips.do.meet" }
    /** The day's surprise at the road. */
    data object Surprise : TipAction { override val label get() = "tips.do.surprise" }
    /** Festival [id]'s run. */
    data class Festival(val id: String) : TipAction { override val label get() = "tips.do.celebrate" }
    /** Today's reviews. */
    data object Review : TipAction { override val label get() = "tips.do.review" }
    /** The word packs. */
    data object Packs : TipAction { override val label get() = "tips.do.packs" }
    /** The tutor's chat. */
    data object Chat : TipAction { override val label get() = "tips.do.chat" }
    /** A talk (role-play). */
    data object Talk : TipAction { override val label get() = "tips.do.talk" }
    /** The grammar book. */
    data object Grammar : TipAction { override val label get() = "tips.do.grammar" }
    /** The case trainer. */
    data object Cases : TipAction { override val label get() = "tips.do.cases" }
    /** "🤝 Prijatelji · Friends": the linked towns, their visits. */
    data object Friends : TipAction { override val label get() = "tips.do.friends" }
    /** Nothing to open: what to do is right here (a scene's things and people); its button is "Razumem · Got it". */
    data object None : TipAction { override val label get() = "tips.gotIt" }
}

/**
 * A moment noticed now: [moment]'s line ([key], "target · base" through bi(); a child's [childKey], in the base with the
 * course language's word for the thing marked «…»), [thing] (that word: a building's, a resource's, an age's name, as the
 * culture says it) and the line's other [args], the picture ([emoji]), where it points on the map ([at]) and its one tap
 * ([action]).
 */
data class Tip(
    val moment: String,
    val emoji: String,
    val key: String,
    val childKey: String,
    val action: TipAction,
    val thing: Localized? = null,
    val args: Map<String, Any?> = emptyMap(),
    val at: TownPlace? = null,
)

/**
 * What the screens know beyond the village state, for the moments: the dashboard's ([ready]: loaded; the learning moments
 * wait for it), the map's bubbles now ([markers]: requests, visitors, arrivals), what can be built ([canBuild]), the scene
 * a place opens ([scenes]) and an upgrade's state ([upgrade]); in a scene, which ([scene]) and how many things were found
 * in the scenes so far ([found]).
 */
data class GuideFacts(
    val today: LocalDate,
    val now: Long = 0,
    val ready: Boolean = false,
    val due: Int = 0,
    /** A review was ever answered (the dashboard's marks). */
    val reviewed: Boolean = false,
    /** A word pack all learned: its title. */
    val packDone: String? = null,
    /** The learner wrote to the tutor in the chat. */
    val chatted: Boolean = false,
    /** The case trainer is open where the village's language has it. */
    val cases: Boolean = false,
    /** Linked towns. */
    val towns: Int = 0,
    /** The school's unit suggested next, once the class meets. */
    val lesson: String? = null,
    val markers: List<TownMarker> = emptyList(),
    val canBuild: Set<BuildingType> = emptySet(),
    val scenes: (TownPlace) -> String? = { null },
    val upgrade: (String) -> UpgradeOption? = { null },
    val scene: String? = null,
    val found: Int = 0,
)

/**
 * A moment of the catalogue ([Guidance.MOMENTS]): [id], where it shows ([place]), [priority] (the higher first), whether it
 * comes [again] that many days after it was dealt with (null: once), whether a learner already past it gets it once
 * ([catchUp]: Teo's school standing; not "the first pack done" weeks later), [askedOnly] (only when the tutor points at it),
 * [learning] (judged once the dashboard is in), [known] (the learner showed they know it: skipped) and [check] (the tip
 * now, or null).
 */
class TipMoment(
    val id: String,
    val place: TipPlace,
    val priority: Int,
    val again: Int? = null,
    val catchUp: Boolean = true,
    val askedOnly: Boolean = false,
    val learning: Boolean = false,
    val known: (GameState, GuideFacts) -> Boolean = { _, _ -> false },
    val check: (GameState, GuideFacts) -> Tip?,
)

/** What a phone remembers of the tips for a village founded anew by the same learner ([Guidance.inherit]). */
@Serializable
data class TipsMemory(val learner: String = "", val seen: Set<String> = emptySet(), val did: Set<String> = emptySet(), val off: Boolean = false)

object Guidance {
    /** A new tip comes at least this long after the last one was first shown or dealt with: a quiet moment, never a nag. */
    const val GAP_MS = 5 * 60_000L
    /** At most this many tips first shown a day. */
    const val MAX_A_DAY = 3
    /** Of them, at most this many moments already past when the tips began ([Tips.backlog]). */
    const val OLD_A_DAY = 1
    /** A new age's tip, this many days after it came. */
    const val AGE_DAYS = 3L
    /** A store full comes again this many days after it was dealt with, while the learner never traded. */
    const val FULL_AGAIN = 7

    // --- the catalogue ---------------------------------------------------------------------------------------------------

    /** Where a store full points: the market (it trades better), else where the resource comes from. */
    private fun storePlace(s: GameState, r: Res): TownPlace {
        fun has(t: BuildingType) = s.buildings.any { it.type == t }
        return when {
            has(BuildingType.MARKET) -> TownPlace.At(BuildingType.MARKET)
            r == Res.FOOD && has(BuildingType.FIELD) -> TownPlace.At(BuildingType.FIELD)
            r == Res.WOOD -> TownPlace.Spot("woodpile")
            r == Res.STONE -> TownPlace.Spot("rocks")
            r == Res.WISDOM && has(BuildingType.LIPA) -> TownPlace.At(BuildingType.LIPA)
            else -> TownPlace.Fire
        }
    }

    /** The learner traded one resource for another (since the tips, or today, or still in the chronicle). */
    fun traded(s: GameState): Boolean = "trade" in s.tips.did || s.trades != null || s.log.any { it.emoji == "⇄" }

    /** The learner went into scene [id] (since the tips, or its dialogs heard, its happenings played). */
    fun visited(s: GameState, id: String): Boolean =
        "scene:$id" in s.tips.did || s.dialogsHeard.keys.any { it.startsWith("$id/") } || s.happeningsDone.keys.any { it.startsWith("$id/") }

    /** The learner went into any scene (or played a game, heard a story there). */
    fun anyScene(s: GameState): Boolean =
        s.tips.did.any { it.startsWith("scene:") } || s.dialogsHeard.isNotEmpty() || s.happeningsDone.isNotEmpty() || s.ispy != null || s.stories.isNotEmpty()

    /** The first building of [type] that stands undamaged (the lowest plot), or null. */
    private fun first(s: GameState, type: BuildingType): Building? =
        s.buildings.filter { it.type == type && !it.damaged }.minByOrNull { if (it.plot >= 0) it.plot else Int.MAX_VALUE }

    /** A store full ([r]): trade some for what the village is shortest of, else spend it on what can be built. */
    private fun full(r: Res, priority: Int) = TipMoment(
        "full-${r.name.lowercase()}", TipPlace.MAP, priority, again = FULL_AGAIN,
        known = { s, _ -> traded(s) },
    ) { s, f ->
        val cap = GameEngine.attributes(s).caps.getValue(r)
        if (s.age < Age.TABOR || s.res(r) < cap) return@TipMoment null
        val get = Trade.suggest(s, r)
        when {
            get != null && Trade.limit(s, r, get, f.today).available ->
                Tip("full-${r.name.lowercase()}", r.emoji, "tips.full.trade", "tips.full.trade.child", TipAction.Trade(r), r.names, mapOf("getEmoji" to get.emoji), storePlace(s, r))
            f.canBuild.any { t -> (Catalog.spec(t).cost[r] ?: 0) > 0 } ->
                Tip("full-${r.name.lowercase()}", r.emoji, "tips.full.build", "tips.full.build.child", TipAction.Build, r.names, at = storePlace(s, r))
            else -> null
        }
    }

    /** One resource for another, when the tutor points at trading: the fullest store's (by its share of the cap). */
    private val trade = TipMoment("trade", TipPlace.MAP, 0, askedOnly = true) { s, f ->
        val caps = GameEngine.attributes(s).caps
        Res.entries.sortedByDescending { s.res(it).toFloat() / caps.getValue(it).coerceAtLeast(1) }.firstNotNullOfOrNull { r ->
            val get = Trade.suggest(s, r)
            if (get != null && Trade.limit(s, r, get, f.today).available) {
                Tip("trade", "⇄", "tips.trade", "tips.trade.child", TipAction.Trade(r), r.names, mapOf("getEmoji" to get.emoji), storePlace(s, r))
            } else null
        }
    }

    /**
     * The first of a building ([type]) standing: what it is for, one tap to it ([action]: its scene, else its card, unless
     * given); [known]: the learner showed they know it.
     */
    private fun built(
        id: String, type: BuildingType, priority: Int, key: String, childKey: String,
        known: (GameState, GuideFacts) -> Boolean = { _, _ -> false },
        action: (GameState, GuideFacts, Building) -> TipAction? = { _, f, b -> f.scenes(TownPlace.At(type))?.let { TipAction.Scene(it, b.id) } ?: TipAction.Building(b.id) },
    ) = TipMoment(id, TipPlace.MAP, priority, known = known) { s, f ->
        val b = first(s, type) ?: return@TipMoment null
        val a = action(s, f, b) ?: return@TipMoment null
        Tip(id, type.emoji, key, childKey, a, type.names, at = TownPlace.At(type))
    }

    /** The first building that may be upgraded now (its age, its price, the course): its words first, or the upgrade. */
    private val upgrade = TipMoment(
        "upgrade", TipPlace.MAP, 75,
        known = { s, _ -> "upgrade" in s.tips.did || s.buildings.any { it.level > 1 } },
    ) { s, f ->
        s.buildings.filter { !it.damaged }.sortedBy { if (it.plot >= 0) it.plot else Int.MAX_VALUE }.firstNotNullOfOrNull { b ->
            val o = f.upgrade(b.id)?.takeIf { it.reason == null && it.courseDone } ?: return@firstNotNullOfOrNull null
            val missing = o.words?.takeIf { !it.done }?.missing?.size ?: 0
            if (missing > 0) {
                Tip("upgrade", "⬆️", "tips.upgrade.words", "tips.upgrade.words.child", TipAction.Words(b.id), b.type.names, mapOf("n" to missing), TownPlace.of(b, s))
            } else {
                Tip("upgrade", "⬆️", "tips.upgrade.ready", "tips.upgrade.ready.child", TipAction.Building(b.id, "tips.do.upgrade"), b.type.names, at = TownPlace.of(b, s))
            }
        }
    }

    /** The first damaged building: it gives nothing until repaired, and the neighbours help (a moba). */
    private val repair = TipMoment(
        "repair", TipPlace.MAP, 90,
        known = { s, _ -> "repair" in s.tips.did || s.log.any { it.emoji == "🔧" } },
    ) { s, _ ->
        val b = s.buildings.filter { it.damaged }.minByOrNull { if (it.plot >= 0) it.plot else Int.MAX_VALUE } ?: return@TipMoment null
        Tip("repair", "🔧", "tips.repair", "tips.repair.child", TipAction.Building(b.id, "tips.do.repair"), b.type.names, at = TownPlace.of(b, s))
    }

    /** The map's first bubble whose id starts with [prefix], as a tip of moment [id]. */
    private fun marker(f: GuideFacts, prefix: String): TownMarker? = f.markers.firstOrNull { it.id.startsWith(prefix) }

    private val request = TipMoment("request", TipPlace.MAP, 78, known = { s, _ -> s.stats.questsDone > 0 }) { _, f ->
        val m = marker(f, "quest:") ?: return@TipMoment null
        Tip("request", m.emoji, "tips.request", "tips.request.child", TipAction.Quest(m.id.removePrefix("quest:")), word("tips.word.help"), at = m.place)
    }

    /**
     * A request tried below the mark waits for tomorrow (its "🔁" at the giver): where it went, and the drill that brings it
     * back today; once.
     */
    private val back = TipMoment("back", TipPlace.MAP, 87) { s, f ->
        val m = marker(f, si.lanisce.lani.game.scene.TownMarkers.BACK) ?: return@TipMoment null
        val id = m.id.removePrefix(si.lanisce.lani.game.scene.TownMarkers.BACK)
        val title = s.quests.firstOrNull { it.id == id }?.title ?: return@TipMoment null
        val target = title.substringBefore(" · ")
        val base = title.substringAfter(" · ", target)
        Tip("back", "🔁", "tips.back", "tips.back.child", TipAction.Retry(id), Localized { lang -> if (lang == L10n.pair.target) target else base }, at = m.place)
    }

    private val visitor = TipMoment("visitor", TipPlace.MAP, 60) { _, f ->
        val m = marker(f, "visitor:") ?: return@TipMoment null
        Tip("visitor", m.emoji, "tips.visitor", "tips.visitor.child", TipAction.Villager(m.id.removePrefix("visitor:")), word("tips.word.hello"), at = m.place)
    }

    private val arrival = TipMoment(
        "arrival", TipPlace.MAP, 85,
        known = { s, _ -> s.bonds.values.any { b -> b.memories.any { it.kind == "arrival" } } },
    ) { _, f ->
        val m = marker(f, "arrival:") ?: return@TipMoment null
        Tip("arrival", m.emoji, "tips.arrival", "tips.arrival.child", TipAction.Meet(m.id.removePrefix("arrival:")), word("tips.word.hello"), at = m.place)
    }

    private val surprise = TipMoment("surprise", TipPlace.MAP, 35) { s, f ->
        val sp = Surprises.today(s, f.today)?.takeIf { !it.done && it.kind != Surprises.PEDLAR && Surprises.whoHere(s, it, f.today) } ?: return@TipMoment null
        Tip("surprise", Surprises.emoji(sp.kind), "tips.surprise", "tips.surprise.child", TipAction.Surprise, word("tips.word.surprise"), at = TownPlace.Spot("road"))
    }

    /** The first place on the map whose scene can be entered: the fire's first (the village has it from the start). */
    private val scene = TipMoment("scene", TipPlace.MAP, 60, known = { s, f -> anyScene(s) || f.found > 0 }) { s, f ->
        val places = listOf(TownPlace.Fire) + s.buildings.map { TownPlace.of(it, s) }.distinct()
        places.firstNotNullOfOrNull { p -> f.scenes(p)?.let { p to it } }?.let { (p, id) ->
            Tip("scene", "🚪", "tips.scene", "tips.scene.child", TipAction.Scene(id), word("tips.word.inside"), at = p)
        }
    }

    /** A new age ([age]) reached in the last [AGE_DAYS] days, while something it opens can be built. */
    private fun age(age: Age) = TipMoment(
        "age-${age.name.lowercase()}", TipPlace.MAP, 40, catchUp = false,
        known = { s, _ -> s.buildings.any { Catalog.spec(it.type).minAge == age } },
    ) { s, f ->
        if (s.age != age) return@TipMoment null
        val day = s.grown[age.name.lowercase()]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@TipMoment null
        if (ChronoUnit.DAYS.between(day, f.today) !in 0..AGE_DAYS) return@TipMoment null
        if (f.canBuild.none { Catalog.spec(it).minAge == age }) return@TipMoment null
        Tip("age-${age.name.lowercase()}", age.emoji, "tips.age", "tips.age.child", TipAction.Build, age.names, at = TownPlace.Fire)
    }

    private val project = TipMoment("project", TipPlace.MAP, 55, known = { s, _ -> s.projects.values.any { it > 0 } }) { s, f ->
        val o = Projects.today(s, f.today).firstOrNull() ?: return@TipMoment null
        Tip("project", o.spec.emoji, "tips.project", "tips.project.child", TipAction.Projects(o.spec.id), o.spec.nameText, at = si.lanisce.lani.game.scene.TownMarkers.projectPlace(o.spec, s))
    }

    private val festival = TipMoment("festival", TipPlace.MAP, 88, known = { s, _ -> s.festivals.isNotEmpty() }) { s, f ->
        val d = Calendar.open(s, f.today).firstOrNull { !it.done && !it.festival.quiet } ?: return@TipMoment null
        val place = TownPlace.At(BuildingType.LIPA).takeIf { p -> s.buildings.any { it.type == p.type } } ?: TownPlace.Fire
        Tip("festival", d.festival.emoji, "tips.festival", "tips.festival.child", TipAction.Festival(d.festival.id), d.festival.nameText, at = place)
    }

    private val chest = TipMoment(
        "chest", TipPlace.MAP, 45,
        known = { s, _ -> s.chest.given.isNotEmpty() || s.bought.isNotEmpty() || s.chest.tools.values.any { it.forged > 0 } },
    ) { s, _ ->
        if (s.chest.goods.values.none { it > 0 } && s.chest.tools.isEmpty()) return@TipMoment null
        Tip("chest", "🧰", "tips.chest", "tips.chest.child", TipAction.Chest, word("tips.word.chest"), at = TownPlace.Fire)
    }

    // the learning moments, on Home: judged once the dashboard is in
    private val reviews = TipMoment("reviews", TipPlace.HOME, 90, learning = true, known = { _, f -> f.reviewed }) { _, f ->
        if (f.due <= 0) null else Tip("reviews", "🔁", "tips.reviews", "tips.reviews.child", TipAction.Review, word("tips.word.cards"))
    }

    private val packDone = TipMoment("pack-done", TipPlace.HOME, 80, catchUp = false, learning = true) { _, f ->
        f.packDone?.let { Tip("pack-done", "📚", "tips.packDone", "tips.packDone.child", TipAction.Packs, word("tips.word.words")) }
    }

    private val cases = TipMoment("cases", TipPlace.HOME, 70, learning = true, known = { s, _ -> s.cases != null }) { _, f ->
        if (!f.cases) null else Tip("cases", "🧩", "tips.cases", "tips.cases.child", TipAction.Cases, word("tips.word.cases"))
    }

    private val grammar = TipMoment("grammar", TipPlace.HOME, 60, learning = true, known = { s, _ -> "book" in s.tips.did }) { s, _ ->
        val lang = "${L10n.ownPair.target.code}/"
        if (s.grammar.keys.none { it.startsWith(lang) }) null else Tip("grammar", "📖", "tips.grammar", "tips.grammar.child", TipAction.Grammar, word("tips.word.book"))
    }

    private val chat = TipMoment("chat", TipPlace.HOME, 50, learning = true, known = { _, f -> f.chatted }) { s, _ ->
        if (s.stats.totalEarned.values.sum() <= 0) null else Tip("chat", "💬", "tips.chat", "tips.chat.child", TipAction.Chat, word("tips.word.tutor"))
    }

    private val talk = TipMoment(
        "talk", TipPlace.HOME, 45, learning = true,
        known = { s, _ -> "talk" in s.tips.did || s.bonds.values.any { b -> b.memories.any { it.kind == "talk" } } },
    ) { s, _ ->
        if (s.age < Age.TABOR) null else Tip("talk", "🗣️", "tips.talk", "tips.talk.child", TipAction.Talk, word("tips.word.talk"))
    }

    private val friends = TipMoment(
        "friends", TipPlace.HOME, 40, learning = true,
        known = { s, _ -> s.towns.values.any { it.met.isNotEmpty() || it.helped > 0 || it.gifts > 0 || it.trades > 0 } },
    ) { _, f ->
        if (f.towns <= 0) null else Tip("friends", "🤝", "tips.friends", "tips.friends.child", TipAction.Friends, word("tips.word.friends"))
    }

    /** In a scene for the first time: its things and people are to tap. */
    private val inside = TipMoment("inside", TipPlace.SCENE, 50, known = { _, f -> f.found > 0 }) { _, f ->
        if (f.scene == null) null else Tip("inside", "👆", "tips.inside", "tips.inside.child", TipAction.None, Localized { L10n.pair.target.ownName })
    }

    /** A word of the course language for a thing the culture doesn't name ("lezione", "skrinja"): its string table's. */
    private fun word(key: String): Localized = Localized { lang -> L10n.text(lang, key) }

    /** The moments, in no particular order: [next] goes by their [TipMoment.priority]. */
    val MOMENTS: List<TipMoment> = listOf(
        full(Res.WISDOM, 95), full(Res.STONE, 94), full(Res.WOOD, 93), full(Res.FOOD, 92), trade,
        repair, festival, back, arrival,
        built(
            "school", BuildingType.SCHOOL, 80, "tips.school", "tips.school.child",
            known = { s, _ -> "school" in s.tips.did || s.course?.units?.isNotEmpty() == true },
            action = { _, f, _ -> f.lesson?.let { TipAction.Lesson(it) } },
        ),
        TipMoment(
            "class", TipPlace.MAP, 79,
            known = { s, _ -> "school" in s.tips.did || s.course?.units?.isNotEmpty() == true },
        ) { s, f ->
            if (!School.open(s) || s.buildings.any { it.type == BuildingType.SCHOOL } || f.lesson == null) return@TipMoment null
            val lipa = s.buildings.any { it.type == BuildingType.LIPA && !it.damaged }
            Tip("class", "📚", "tips.class", "tips.class.child", TipAction.Lesson(f.lesson), word("tips.word.lesson"), at = if (lipa) TownPlace.At(BuildingType.LIPA) else TownPlace.Fire)
        },
        request, upgrade,
        built(
            "market", BuildingType.MARKET, 70, "tips.market", "tips.market.child", known = { s, _ -> traded(s) },
            action = { s, _, _ ->
                val caps = GameEngine.attributes(s).caps
                TipAction.Trade(Res.entries.maxBy { s.res(it).toFloat() / caps.getValue(it).coerceAtLeast(1) })
            },
        ),
        built(
            "smithy", BuildingType.SMITHY, 70, "tips.smithy", "tips.smithy.child",
            known = { s, _ -> s.chest.tools.values.any { it.forged > 0 } }, action = { _, _, _ -> TipAction.Chest },
        ),
        visitor, scene, project,
        built("lipa", BuildingType.LIPA, 50, "tips.lipa", "tips.lipa.child", known = { s, f -> f.scenes(TownPlace.At(BuildingType.LIPA))?.let { visited(s, it) } == true }),
        built("well", BuildingType.WELL, 48, "tips.well", "tips.well.child", known = { s, f -> f.scenes(TownPlace.At(BuildingType.WELL))?.let { visited(s, it) } == true }),
        built("watchtower", BuildingType.WATCHTOWER, 50, "tips.watchtower", "tips.watchtower.child"),
        built("church", BuildingType.CHURCH, 50, "tips.church", "tips.church.child", known = { s, f -> f.scenes(TownPlace.At(BuildingType.CHURCH))?.let { visited(s, it) } == true }),
        built("beehive", BuildingType.BEEHIVE, 50, "tips.beehive", "tips.beehive.child", known = { s, f -> f.scenes(TownPlace.At(BuildingType.BEEHIVE))?.let { visited(s, it) } == true }),
        TipMoment("home", TipPlace.MAP, 45) { s, _ ->
            val b = (first(s, BuildingType.HOUSE) ?: first(s, BuildingType.HUT)) ?: return@TipMoment null
            Tip("home", b.type.emoji, "tips.home", "tips.home.child", TipAction.Building(b.id), b.type.names, at = TownPlace.of(b, s))
        },
        built(
            "palisade", BuildingType.PALISADE, 45, "tips.palisade", "tips.palisade.child",
            known = { s, _ -> s.stats.eventsWon + s.stats.eventsLost > 0 }, action = { _, _, b -> TipAction.Building(b.id) },
        ),
        chest, surprise,
        age(Age.TABOR), age(Age.ZASELEK), age(Age.VAS), age(Age.TRG), age(Age.MESTO),
        reviews, packDone, cases, grammar, chat, talk, friends,
        inside,
    )

    private val byId: Map<String, TipMoment> = MOMENTS.associateBy { it.id }

    fun moment(id: String): TipMoment? = byId[id]

    /** The moments the tutor may point at (show_tip): every one of the catalogue, by id. */
    val POINTABLE: List<String> get() = MOMENTS.map { it.id }

    // --- the queue ---------------------------------------------------------------------------------------------------------

    /** TipMoment [id] was shown and not dealt with since: it stays while it holds. */
    fun open(t: Tips, id: String): Boolean {
        val shown = t.shown[id] ?: return false
        val seen = t.seen[id] ?: return true
        return seen < shown
    }

    /** Whether [m] may come as a new tip [today]: never dealt with, or [TipMoment.again] days since; not put off till later. */
    private fun eligible(t: Tips, m: TipMoment, today: LocalDate): Boolean {
        if (!waits(t, m.id, today)) return false
        val seen = t.seen[m.id]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return true
        val again = m.again ?: return false
        return ChronoUnit.DAYS.between(seen, today) >= again
    }

    /** Not put off ("Pozneje · Later") past [today]. */
    private fun waits(t: Tips, id: String, today: LocalDate): Boolean = t.later[id]?.let { it <= today.toString() } ?: true

    /** [m]'s tip now, unless the learner showed they know it, or the dashboard it needs isn't in; a moment's trouble is no tip. */
    private fun tipOf(m: TipMoment, s: GameState, f: GuideFacts, ignoreKnown: Boolean = false): Tip? {
        if (m.learning && !f.ready) return null
        return runCatching { if (!ignoreKnown && m.known(s, f)) null else m.check(s, f) }.getOrNull()
    }

    /**
     * The one tip to show at [place] now, or null: none while the tips are off or wolves, a bear or a storm are about
     * ([si.lanisce.lani.game.villagers.Reactions.quiet]). The tutor's pointer first; then the tip shown and not dealt with
     * yet (it stays: its bubble doesn't jump to another); then, at a quiet moment ([quiet]: no exercise, dialog, talk, card or
     * sheet open, and [GAP_MS] since the last), the best new one by priority, at most [MAX_A_DAY] a day, of the moments past
     * when the tips began at most [OLD_A_DAY].
     */
    fun next(s: GameState, f: GuideFacts, place: TipPlace, quiet: Boolean = true): Tip? {
        val t = s.tips
        if (t.off || si.lanisce.lani.game.villagers.Reactions.quiet(s)) return null
        val today = f.today.toString()
        t.asked?.let { a -> moment(a.moment)?.takeIf { it.place == place }?.let { m -> tipOf(m, s, f, ignoreKnown = true)?.let { return it } } }
        val here = MOMENTS.filter { it.place == place && !it.askedOnly }.sortedByDescending { it.priority }
        here.filter { open(t, it.id) && waits(t, it.id, f.today) }.firstNotNullOfOrNull { tipOf(it, s, f) }?.let { return it }
        if (!quiet || f.now - t.lastAt < GAP_MS) return null
        val firstToday = t.shown.filterValues { it == today }.keys
        if (firstToday.size >= MAX_A_DAY) return null
        val old = firstToday.count { it in t.backlog }
        return here.filter { !open(t, it.id) && eligible(t, it, f.today) && (it.id !in t.backlog || old < OLD_A_DAY) }
            .firstNotNullOfOrNull { tipOf(it, s, f) }
    }

    // --- what the village keeps --------------------------------------------------------------------------------------------

    private fun GameState.tipsWith(f: (Tips) -> Tips): GameState = copy(tips = f(tips))

    /** [tip] shows now ([today], [now] epoch ms): first shown, unless it was open already. */
    fun shown(s: GameState, tip: Tip, today: LocalDate, now: Long): GameState =
        if (open(s.tips, tip.moment)) s else s.tipsWith { it.copy(shown = it.shown + (tip.moment to today.toString()), lastAt = now) }

    /** TipMoment [id] dealt with: its action tapped, "Razumem · Got it". The tutor's pointer at it is done. */
    fun dealt(s: GameState, id: String, today: LocalDate, now: Long): GameState = s.tipsWith {
        it.copy(seen = it.seen + (id to today.toString()), later = it.later - id, lastAt = now, asked = it.asked?.takeIf { a -> a.moment != id })
    }

    /** TipMoment [id] put off ("Pozneje · Later"): back the next day. */
    fun later(s: GameState, id: String, today: LocalDate, now: Long): GameState = s.tipsWith {
        it.copy(later = it.later + (id to today.plusDays(1).toString()), lastAt = now, asked = it.asked?.takeIf { a -> a.moment != id })
    }

    /** The learner did [what] ("trade", "book", "school", "talk", "upgrade", "repair", "scene:<id>"): its tips are known. */
    fun did(s: GameState, what: String): GameState = if (what in s.tips.did) s else s.tipsWith { it.copy(did = it.did + what) }

    /** "💡 Namigi · Tips" on or off ([off]). */
    fun off(s: GameState, off: Boolean): GameState = if (s.tips.off == off) s else s.tipsWith { it.copy(off = off) }

    /**
     * "Pokaži jih spet · Show them again": what was dealt with, put off, shown and done is forgotten, the tips on again; the
     * day they began stays (so nothing becomes a backlog again).
     */
    fun again(s: GameState): GameState = s.tipsWith { Tips(since = it.since) }

    /** The tutor points at moment [moment] (show_tip) at [at] (epoch ms); a replayed event older than the one kept changes nothing. */
    fun ask(s: GameState, moment: String, note: String?, at: Long): GameState {
        if (moment(moment) == null) return s
        if ((s.tips.asked?.at ?: Long.MIN_VALUE) >= at && s.tips.asked?.moment == moment) return s
        return s.tipsWith { it.copy(asked = TipAsk(moment, note?.take(200), at)) }
    }

    /** The tutor takes the pointer back. */
    fun unask(s: GameState): GameState = if (s.tips.asked == null) s else s.tipsWith { it.copy(asked = null) }

    /**
     * The tips begin in this village ([f]: the dashboard in, so the learning moments can be judged): the day kept; in a village
     * from before today (an update), the moments that hold already are past: the still useful ones ([TipMoment.catchUp]) wait in
     * the backlog (one a day), the others count as seen. A village founded today begins with none past.
     */
    fun begin(s: GameState, f: GuideFacts): GameState {
        if (s.tips.since.isNotEmpty() || !f.ready) return s
        val today = f.today.toString()
        val older = s.foundedOn.isNotEmpty() && s.foundedOn < today
        if (!older) return s.tipsWith { it.copy(since = today) }
        val past = MOMENTS.filter { !it.askedOnly && tipOf(it, s, f) != null }
        return s.tipsWith {
            it.copy(
                since = today,
                backlog = past.filter { m -> m.catchUp }.map { m -> m.id }.toSet(),
                seen = it.seen + past.filter { m -> !m.catchUp }.associate { m -> m.id to today },
            )
        }
    }

    /** What a phone remembers of [s]'s tips for [learner]: what was dealt with and done, and whether they are off. */
    fun memory(s: GameState, learner: String): TipsMemory = TipsMemory(learner, s.tips.seen.keys, s.tips.did, s.tips.off)

    /**
     * A village founded anew by the same learner on this phone ([m]'s learner is [learner]): what they dealt with and did stays
     * known, and the tips stay off if they turned them off; the new village's own firsts come as new. Another learner's
     * memory changes nothing.
     */
    fun inherit(s: GameState, m: TipsMemory?, learner: String, today: LocalDate): GameState {
        if (m == null || m.learner.isEmpty() || m.learner != learner) return s
        return s.tipsWith { it.copy(seen = it.seen + m.seen.associateWith { today.toString() }, did = it.did + m.did, off = m.off) }
    }

    /** The moments first shown in the last [days] days, newest first: moment id → day (the tutor's get_village says so too). */
    fun recent(s: GameState, today: LocalDate, days: Long = 14): List<Pair<String, String>> {
        val from = today.minusDays(days).toString()
        return s.tips.shown.filterValues { it >= from }.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
}
