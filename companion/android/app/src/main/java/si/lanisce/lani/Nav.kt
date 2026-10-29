package si.lanisce.lani

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A screen in the back stack, with the [id] its remembered state (a list's scroll, a section left open) is kept under
 * while it waits below another (MainActivity's SaveableStateHolder), and across the process's death.
 */
data class NavEntry(val id: Long, val screen: Screen)

/**
 * The app's back stack (docs/plans/05-built/navigation.md): the screen on view is the top one, Back (the system's, its
 * predictive gesture, every "‹ …" on screen) returns to the one below it, and the screen below names the way back
 * ("‹ Vas", "‹ Napredek").
 *
 * How it moves:
 * - [go] opens a screen on top; the same screen already in the stack ([destination]: a scene beside another, a run
 *   after another take its place too) is gone back to instead, so the stack never loops (Napredek → Šola → a unit →
 *   Napredek is Napredek again, with Domov under it), while different screens of a kind stay (a unit, the school, another
 *   unit: Back walks them);
 * - a run ([inRun]: an exercise run, from its intro to its result) never stays under another screen: what opens from it
 *   takes its place, so Back after it goes where the run began, never into a run that would start over;
 * - [back] pops the top; the root (Home, or Setup) is left by the system: Back there leaves the app;
 * - [reset] puts a new stack (a notification's target over Home, Setup).
 *
 * The screens' own Back handling (a card closed, a step back, a dialog ended) comes first, as Compose's BackHandler
 * registered later does.
 */
class NavStack(start: Screen = Screen.Loading) {
    private var nextId = 1L

    var entries: List<NavEntry> by mutableStateOf(listOf(NavEntry(nextId++, start)))
        private set

    /** The screen on view. */
    val top: Screen get() = entries.last().screen

    /** Where Back leads; null at the root. */
    val below: Screen? get() = entries.getOrNull(entries.lastIndex - 1)?.screen

    val canGoBack: Boolean get() = entries.size > 1

    /** Told of each screen that leaves the stack (popped, replaced, dropped by a [reset]), the top one first. */
    var onRemoved: (Screen) -> Unit = {}

    /**
     * Opens [s]: on top, or back to the same screen already in the stack ([destination]), which [s] then is (its remembered
     * state kept when it is the very screen, [sameAs]). A run on top is replaced, unless [s] is that run.
     */
    fun go(s: Screen) {
        var base = entries
        if (base.last().screen.inRun && !base.last().screen.sameAs(s)) base = base.dropLast(1)
        val i = base.indexOfLast { it.screen.destination == s.destination }
        set(
            if (i >= 0) base.take(i) + NavEntry(base[i].id.takeIf { base[i].screen.sameAs(s) } ?: nextId++, s)
            else base + NavEntry(nextId++, s),
        )
    }

    /** [s] in place of the top screen (its state kept when it is the same screen). */
    fun replace(s: Screen) {
        val last = entries.last()
        set(entries.dropLast(1) + NavEntry(if (last.screen.sameAs(s)) last.id else nextId++, s))
    }

    /** Back to the screen below; false at the root (the system's Back leaves the app there). */
    fun back(): Boolean {
        if (!canGoBack) return false
        set(entries.dropLast(1))
        return true
    }

    /** Back to the last screen [where] holds for, everything above it gone; false (nothing changes) when there is none. */
    fun backTo(where: (Screen) -> Boolean): Boolean {
        val i = entries.indexOfLast { where(it.screen) }
        if (i < 0) return false
        if (i < entries.lastIndex) set(entries.take(i + 1))
        return true
    }

    /** A new stack of [screens] (the first is the root); the screens it shares at its start with the old one keep their state. */
    fun reset(vararg screens: Screen) {
        require(screens.isNotEmpty())
        val old = entries
        var same = true
        set(
            screens.mapIndexed { k, s ->
                val o = old.getOrNull(k)
                same = same && o != null && o.screen.sameAs(s)
                NavEntry(if (same) o!!.id else nextId++, s)
            },
        )
    }

    /** The stack as saved for after the process's death ([NavRoutes]): the screens that can be opened again, under their ids. */
    fun saved(): String = NavRoutes.encode(entries, nextId)

    /**
     * The stack saved by [saved], as far as its screens can be opened again ([NavRoutes.screen]), over [root] (Home);
     * false when nothing above the root came back.
     */
    fun restore(saved: String?, root: Screen = Screen.Home): Boolean {
        val decoded = NavRoutes.decode(saved)
        val rootId = decoded.firstOrNull()?.takeIf { it.screen == root }?.id
        val above = decoded.drop(if (rootId != null) 1 else 0).filter { it.screen != root }
        val all = listOf(NavEntry(rootId ?: -1L, root)) + above
        // the ids as they were (the screens' saved states are kept under them), new ones after every id given before (a run's
        // or a visit's, not restored, may have a state saved under its id still)
        nextId = maxOf(nextId, all.maxOf { it.id } + 1, NavRoutes.next(saved))
        set(all.map { if (it.id < 0) it.copy(id = nextId++) else it })
        return above.isNotEmpty()
    }

    private fun set(next: List<NavEntry>) {
        val old = entries
        entries = next
        val kept = next.mapTo(HashSet()) { it.id }
        for (e in old.asReversed()) if (e.id !in kept) onRemoved(e.screen)
    }
}

/**
 * Where [this] leads, for [NavStack.go]: the same destination in the stack is gone back to rather than opened again. A
 * scene is one destination with every other scene (they lie side by side, the village under them), but one gone into
 * through a thing of another's picture ([Screen.Scene.from]: the mill from the stream's mill) lies on that one; a visited
 * town's places are one too, and every run (a run is only ever on top); any other screen is its own (a unit and another
 * unit, two villagers, a settings page and the sections' list are different destinations).
 */
val Screen.destination: Any
    get() = when {
        inRun -> "run"
        // a scene gone into from another's picture (the mill from the stream) lies on that one: Back returns to it
        this is Screen.Scene -> if (from != null) "scene-inside" else "scene"
        this is Screen.VisitScene -> "visit-scene"
        else -> this
    }

/**
 * The same screen as [other], its remembered state still good: the same scene at another happening or thing (its focus),
 * else the very same screen (a run is the same only as itself: each run is a screen of its own).
 */
fun Screen.sameAs(other: Screen): Boolean = when {
    this::class != other::class -> false
    this is Screen.Scene -> id == (other as Screen.Scene).id && building == other.building
    else -> this == other
}

/**
 * The back stack as text, for the saved state that survives the process's death: each screen that can be opened again
 * from its ids alone, as a route ("scene", "ob-ognju", "", "hut-2"). Runs (their answers live in the run) and visits (the
 * visit's world is entered again from Friends) are not: the stack is saved up to the first of them.
 */
object NavRoutes {
    @Serializable
    private data class Saved(val id: Long, val route: List<String>)

    /** The stack as saved: its entries up to the first that can't be opened again, and the next id it would give. */
    @Serializable
    private data class Stack(val next: Long = 0, val entries: List<Saved> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(entries: List<NavEntry>, next: Long = (entries.maxOfOrNull { it.id } ?: 0L) + 1): String {
        val out = ArrayList<Saved>()
        for (e in entries) out += Saved(e.id, route(e.screen) ?: break)
        return json.encodeToString(Stack.serializer(), Stack(next, out))
    }

    private fun read(text: String?): Stack? =
        text?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(Stack.serializer(), it) }.getOrNull() }

    fun decode(text: String?): List<NavEntry> {
        val saved = read(text) ?: return emptyList()
        val out = ArrayList<NavEntry>()
        for (s in saved.entries) out += NavEntry(s.id, screen(s.route) ?: break)
        return out
    }

    /** The next id the saved stack would have given (0 with none). */
    fun next(text: String?): Long = read(text)?.next ?: 0L

    /** [s]'s route; null when it can't be opened again from it (a run, a visit, the start). */
    fun route(s: Screen): List<String>? = when (s) {
        Screen.Home -> listOf("home")
        Screen.Village -> listOf("village")
        Screen.Packs -> listOf("packs")
        Screen.Readings -> listOf("readings")
        is Screen.ReadAloud -> listOf("readaloud", s.id)
        Screen.Talk -> listOf("talk")
        Screen.Progress -> listOf("progress")
        Screen.MyWords -> listOf("mywords")
        is Screen.Family -> listOf("family", s.challengeId.orEmpty())
        // a focus opens straight into a dialog: not again after a restart, the scene as it is
        is Screen.Scene -> listOfNotNull("scene", s.id, s.building.orEmpty(), s.from)
        Screen.Villagers -> listOf("villagers")
        is Screen.Villager -> listOf("villager", s.id)
        Screen.Friends -> listOf("friends")
        is Screen.TownProgress -> listOf("townprogress", s.id)
        is Screen.TownQuestion -> listOf("question", s.key, if (s.asked) "asked" else "received")
        Screen.Treasure -> listOf("treasure")
        Screen.School -> listOf("school")
        is Screen.CourseUnit -> listOf("unit", s.id)
        is Screen.UnitIntro -> listOf("intro", s.id)
        Screen.Numbers -> listOf("numbers")
        is Screen.Cases -> listOf("cases", s.focus.orEmpty())
        is Screen.Settings -> listOf("settings", s.section.orEmpty())
        Screen.GrammarBook -> listOf("grammar")
        else -> null
    }

    /** The screen of [route]; null for one this app doesn't know (an older or newer route). */
    fun screen(route: List<String>): Screen? {
        fun arg(i: Int): String? = route.getOrNull(i)?.takeIf { it.isNotEmpty() }
        return when (route.firstOrNull()) {
            "home" -> Screen.Home
            "village" -> Screen.Village
            "packs" -> Screen.Packs
            "readings" -> Screen.Readings
            "readaloud" -> arg(1)?.let { Screen.ReadAloud(it) }
            "talk" -> Screen.Talk
            "progress" -> Screen.Progress
            "mywords" -> Screen.MyWords
            "family" -> Screen.Family(arg(1))
            "scene" -> arg(1)?.let { Screen.Scene(it, null, arg(2), arg(3)) }
            "villagers" -> Screen.Villagers
            "villager" -> arg(1)?.let { Screen.Villager(it) }
            "friends" -> Screen.Friends
            "townprogress" -> arg(1)?.let { Screen.TownProgress(it) }
            "question" -> arg(1)?.let { Screen.TownQuestion(it, asked = arg(2) == "asked") }
            "treasure" -> Screen.Treasure
            "school" -> Screen.School
            "unit" -> arg(1)?.let { Screen.CourseUnit(it) }
            "intro" -> arg(1)?.let { Screen.UnitIntro(it) }
            "numbers" -> Screen.Numbers
            "cases" -> Screen.Cases(arg(1))
            "settings" -> Screen.Settings(arg(1))
            "grammar" -> Screen.GrammarBook
            else -> null
        }
    }
}
