package si.lanisce.lani.game.villagers

import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.Events
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.ReactionTexts
import si.lanisce.lani.game.culture.ReactionsFile
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.TownPlace
import java.time.LocalDate

/**
 * What the village reacts to now (companion/GAME.md "People react"): its event, on since [since] seconds ([Phase.ON]),
 * or just over: a fight won ([Phase.WON]: the cheer), a fight lost ([Phase.LOST]), any other event ([Phase.OVER]),
 * [since] seconds after it ended, while the people go back to their day. [strength]: the event's; [damaged]: the
 * buildings a lost one damaged.
 */
data class Stir(val kind: EventKind, val phase: Phase, val since: Double, val strength: Int = 1, val damaged: List<String> = emptyList()) {
    enum class Phase { ON, WON, LOST, OVER }

    /** Wolves or a bear: a fight. */
    val threat: Boolean get() = Reactions.threat(kind)

    /** The everyday talks wait now: wolves, a bear or a storm about ([Reactions.quiet]). */
    val quiet: Boolean get() = phase == Phase.ON && Reactions.quieting(kind)
}

/** What someone does while the village reacts ([Reactions.stances]). */
enum class Stance {
    /** Faces the wolves or the bear at a distance: the smith with his hammer, the shepherd and the hunter with a stick. */
    DEFENDER,
    /** Up on the watchtower, waving. */
    LOOKOUT,
    CHILD,
    /** The grandparents, the storyteller, the aunt. */
    ELDER,
    /** The innkeeper, who keeps a shop: first at the merchant's cart. */
    KEEPER,
    OTHER,
}

/**
 * How the people react to the village's events (companion/GAME.md "People react"), pure: while wolves or a bear are
 * about a few grown-ups face them at a distance ([Stance.DEFENDER]), someone is up on the watchtower ([Stance.LOOKOUT]),
 * the children, the old and the innkeeper gather by the fire and the others go home; in a storm they go in; at the
 * merchant's some gather at his cart; at a festival round the fire. Their everyday talks wait while wolves, a bear or a
 * storm are about ([quiet]: the happenings, the stories, the meetings, the keepers' talks, the lessons, the games) and
 * come back when it's over, a dialog that was due still due; meanwhile each says something of it ([line], from the
 * culture's reactions.json), and their card has the call to the event's run ([call]). The map places them
 * ([si.lanisce.lani.game.render.TownPeople]); the scenes show who is there by [placeOf].
 */
object Reactions {
    /** How long the cheer round the defenders lasts after a won fight (s). */
    const val CHEER_S = 150.0

    /** How long the walk to a reaction (and back to the day after it) takes at most (s): as long as the map's longest. */
    const val WALK_S = Routine.WALK_S

    /** The old: they gather by the fire. */
    private val ELDERS = setOf(Routine.Kind.GRANDMA, Routine.Kind.GRANDPA, Routine.Kind.STORYTELLER, Routine.Kind.AUNT)

    /** Who stands up to a threat, best first (after the event's leader). */
    private val GUARDS = listOf(Routine.Kind.SMITH, Routine.Kind.SHEPHERD, Routine.Kind.HUNTER)

    /** Who else stands up to it when none of [GUARDS] lives here. */
    private val GROWN = listOf(Routine.Kind.FARMER, Routine.Kind.ADULT, Routine.Kind.WINEMAKER, Routine.Kind.BEEKEEPER)

    /** Who goes up the watchtower, best first. */
    private val WATCH = listOf(Routine.Kind.HUNTER, Routine.Kind.WINEMAKER, Routine.Kind.ADULT, Routine.Kind.FARMER, Routine.Kind.BEEKEEPER)

    /** Wolves or a bear: a fight. */
    fun threat(kind: EventKind?): Boolean = kind == EventKind.WOLVES || kind == EventKind.BEAR

    /** Whether the everyday talks wait while [kind] is on: wolves, a bear, a storm. */
    fun quieting(kind: EventKind?): Boolean = threat(kind) || kind == EventKind.STORM

    /**
     * Whether the everyday talks wait in [state]'s village now: wolves, a bear or a storm about. The happenings, the
     * storyteller's story, the meetings with who joined, the keepers' talks (not in a storm: theirs know the rain), the
     * class's lesson, «Vidim, vidim», the stranger at the road and the bubbles of the day's surprise, the projects and the
     * festivals are off until it's over; so are the requests' bubbles (they wait in the scroll and on the cards:
     * [si.lanisce.lani.game.QuestQueue.Why.Danger]), and the day's surprise stays the learner's to play (the HUD, Home).
     */
    fun quiet(state: GameState?): Boolean = quieting(state?.event?.kind)

    /**
     * What [state]'s village reacts to at [wall] (epoch ms; 0: no clock, a test's or a tool's: an event on since long, none
     * just over): its event while it's on, else the last one's end for a while ([CHEER_S] and [WALK_S] after a won fight,
     * [WALK_S] after anything else). Null: nothing.
     */
    fun stir(state: GameState, wall: Long): Stir? {
        state.event?.let { e ->
            val since = if (wall <= 0L) Double.POSITIVE_INFINITY else ((wall - e.startedAt) / 1000.0).coerceAtLeast(0.0)
            return Stir(e.kind, Stir.Phase.ON, since, e.strength)
        }
        val end = state.ended ?: return null
        if (wall <= 0L) return null
        val since = (wall - end.at) / 1000.0
        val phase = when {
            !threat(end.kind) -> Stir.Phase.OVER
            end.won -> Stir.Phase.WON
            else -> Stir.Phase.LOST
        }
        val until = if (phase == Stir.Phase.WON) CHEER_S + WALK_S else WALK_S
        if (since < 0.0 || since >= until) return null
        return Stir(end.kind, phase, since, damaged = end.damaged)
    }

    /** How many stand up to a threat of [strength]: two, three from strength 3. */
    fun defenders(strength: Int): Int = if (strength >= 3) 3 else 2

    /** Whether [state]'s village has a watchtower someone can go up (standing, not damaged). */
    fun tower(state: GameState): Boolean = state.buildings.any { it.type == BuildingType.WATCHTOWER && !it.damaged && it.plot >= 0 }

    /**
     * What each of [people] (the village's, in its order) does in [kind] of [strength]: for a threat the lookout on the
     * watchtower (when there is one: the hunter, else the winemaker, else a grown-up), the defenders (the event's leader
     * when a grown-up lives here, the smith, the shepherd, the hunter; else a grown-up), then everyone by who they are.
     * The babies, the strangers at the road and who doesn't live here (a visitor, the cast still to come) aren't anyone's.
     */
    fun stances(state: GameState, people: List<Villager>, kind: EventKind, strength: Int = 1): Map<String, Stance> {
        val out = LinkedHashMap<String, Stance>()
        val living = state.residents.mapTo(HashSet()) { it.id }
        val folk = people.filter { it.art != "baby" && it.art != "pedlar" && it.art != "pilgrim" && (living.isEmpty() || it.id in living) }.distinctBy { it.id }
        val kinds = folk.associate { it.id to Routine.kindOf(it) }
        val grown = folk.filter { kinds[it.id] != Routine.Kind.CHILD && kinds[it.id] !in ELDERS }
        if (threat(kind)) {
            if (tower(state)) WATCH.firstNotNullOfOrNull { k -> grown.firstOrNull { kinds[it.id] == k } }?.let { out[it.id] = Stance.LOOKOUT }
            val leader = runCatching { Events.texts(kind).leader }.getOrNull()?.let { name -> grown.firstOrNull { it.name == name } }
            val guards = listOfNotNull(leader) + GUARDS.flatMap { k -> grown.filter { kinds[it.id] == k } }
            val order = (guards.ifEmpty { GROWN.flatMap { k -> grown.filter { kinds[it.id] == k } } }).distinctBy { it.id }.filter { it.id !in out }
            for (v in order.take(defenders(strength))) out[v.id] = Stance.DEFENDER
        }
        for (v in folk) if (v.id !in out) out[v.id] = when (kinds[v.id]) {
            Routine.Kind.CHILD -> Stance.CHILD
            in ELDERS -> Stance.ELDER
            Routine.Kind.INNKEEPER -> Stance.KEEPER
            else -> Stance.OTHER
        }
        return out
    }

    /** Whether someone in [stance] gathers by the fire while wolves or a bear are about (the others go home). */
    fun byFire(stance: Stance): Boolean = stance == Stance.CHILD || stance == Stance.ELDER || stance == Stance.KEEPER

    /**
     * Where someone in [stance] is while [kind] is on and keeps the everyday talks waiting ([quieting]), for the scenes: by
     * the fire, up on the watchtower, at home ([home]: in the house, so in no scene but their room's), or nowhere a scene
     * shows (a defender out by the forest's edge: null).
     */
    fun placeOf(kind: EventKind, stance: Stance, home: TownPlace?): TownPlace? = when {
        !quieting(kind) -> null
        kind == EventKind.STORM -> home
        stance == Stance.LOOKOUT -> TownPlace.At(BuildingType.WATCHTOWER)
        stance == Stance.DEFENDER -> null
        byFire(stance) -> TownPlace.Fire
        else -> home
    }

    /** Who patches up what a lost event damaged, best first: the smith, then a grown-up. */
    private val MENDERS = listOf(Routine.Kind.SMITH, Routine.Kind.FARMER, Routine.Kind.ADULT, Routine.Kind.WINEMAKER, Routine.Kind.HUNTER, Routine.Kind.SHEPHERD, Routine.Kind.BEEKEEPER)

    /**
     * Who patches up the building a lost event damaged, and which (the first of [si.lanisce.lani.game.EventEnd.damaged]
     * still damaged), on the day it was lost and the next ([day], in [zone]); null: none. Of [people] (who may: on the map
     * by their day, not waiting for the learner), the smith first, else a grown-up. The map has them at it by daylight
     * ([si.lanisce.lani.game.render.TownReactions]), their card says so ([line] with repair).
     */
    fun repair(state: GameState, people: List<Villager>, day: LocalDate, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Pair<String, si.lanisce.lani.game.Building>? {
        val end = state.ended ?: return null
        if (end.won || end.damaged.isEmpty() || state.event != null) return null
        val on = java.time.Instant.ofEpochMilli(end.at).atZone(zone).toLocalDate()
        if (day.isBefore(on) || day.isAfter(on.plusDays(1))) return null
        val b = state.buildings.firstOrNull { it.id in end.damaged && it.damaged } ?: return null
        val v = MENDERS.firstNotNullOfOrNull { k -> people.firstOrNull { it.art != "baby" && Routine.kindOf(it) == k } } ?: return null
        return v.id to b
    }

    /** The culture's texts for [stir] (its event's while it's on, the won and lost ones just after a fight); null: none. */
    fun texts(stir: Stir, file: ReactionsFile? = Cultures.currentReactions): ReactionTexts? {
        val f = file ?: return null
        return when (stir.phase) {
            Stir.Phase.ON -> when (stir.kind) {
                EventKind.WOLVES -> f.wolves
                EventKind.BEAR -> f.bear
                EventKind.STORM -> f.storm
                EventKind.MERCHANT -> f.merchant
                EventKind.FESTIVAL -> f.festival
            }
            Stir.Phase.WON -> f.won
            Stir.Phase.LOST -> f.lost
            Stir.Phase.OVER -> null
        }
    }

    /**
     * What [v] says in [stance] now (on their card, greeting the learner, in a scene): a line of [stir]'s for who they are
     * (the defenders' for the lookout too; anyone's whose own is empty, the others'), the day's dice picking one; [repair]:
     * they patch up what a lost fight damaged. Null without a reactions file, or nothing to say of it (after a storm).
     */
    fun line(stir: Stir, stance: Stance, v: Villager, day: LocalDate, file: ReactionsFile? = Cultures.currentReactions, seed: Long = 0L, repair: Boolean = false): Text? {
        val t = texts(stir, file) ?: return null
        val own = when {
            repair -> t.repair
            stance == Stance.DEFENDER || stance == Stance.LOOKOUT -> t.defender
            stance == Stance.CHILD -> t.child
            stance == Stance.ELDER -> t.elder
            stance == Stance.KEEPER -> t.keeper
            else -> t.other
        }
        val pool = own.ifEmpty { t.other }
        if (pool.isEmpty()) return null
        val i = (Happenings.roll(seed, day, "react:${stir.kind}:${stir.phase}:${v.id}") * pool.size).toInt().coerceIn(0, pool.size - 1)
        return pool[i]
    }

    /**
     * What [v] says now in [state]'s village at [wall] (epoch ms), [people] being the village's: while an event is on, or
     * right after a fight, their line of it for what they do in it ([line]); the day a fight was lost and the next, who
     * patches up what it damaged says so ([repair]). Null: nothing of the kind (their everyday lines then), or no file.
     */
    fun says(state: GameState, v: Villager, people: List<Villager>, day: LocalDate, wall: Long, file: ReactionsFile? = Cultures.currentReactions): Text? {
        val stir = stir(state, wall)
        if (stir != null && stir.phase != Stir.Phase.OVER) {
            val stance = stances(state, people, stir.kind, stir.strength)[v.id] ?: return null
            return line(stir, stance, v, day, file, state.seed)
        }
        val (who, _) = repair(state, people, day) ?: return null
        if (who != v.id) return null
        val end = state.ended ?: return null
        return line(Stir(end.kind, Stir.Phase.LOST, 0.0), Stance.OTHER, v, day, file, state.seed, repair = true)
    }

    /** The call to [stir]'s run on a villager's card while it's on ("Branimo vas!"); null: none (or no file). */
    fun call(stir: Stir?, file: ReactionsFile? = Cultures.currentReactions): Text? =
        stir?.takeIf { it.phase == Stir.Phase.ON }?.let { texts(it, file)?.call }

    /** The short calls over the map for [stir] now ("Volkovi!", "Hitro noter!"); none without a file. */
    fun shouts(stir: Stir, file: ReactionsFile? = Cultures.currentReactions): List<Text> = texts(stir, file)?.shout.orEmpty()
}
