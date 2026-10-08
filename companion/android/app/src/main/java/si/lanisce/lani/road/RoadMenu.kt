package si.lanisce.lani.road

/**
 * The car's menu (companion/README.md, "Im Auto · In the car"), as Android Auto browses it and the phone's sheet shows it:
 * no scrolling while driving. Two tabs: "🚗 Za pot" with four big items (a grid: ▶ Nadaljuj, ❓ Kviz, 🌙 Mirno, 📖
 * Zgodba) and "Več · More" with the specific sessions. Every session id there ever was still plays (a car remembers the
 * one it played last). Pure: the service and the tests share it.
 */
object RoadMenu {
    const val ROOT = "road:root"

    /** The first tab, "🚗 Za pot" (its id from before the second tab: a car that remembered it still browses it). */
    const val TAB = "road:tab"

    /** The second tab, "Več · More". */
    const val MORE = "road:more"

    /** Where to get ready first, when the phone has nothing for the road yet. */
    const val NONE = "road:none"

    /** The first tab's items, at most four: what a drive starts with. */
    val MAIN = listOf(RoadService.MIX, RoadService.QUIZ, RoadService.EASY, RoadService.STORY)

    /** The second tab's: each kind on its own. */
    val MORE_SESSIONS = listOf(
        RoadService.REVIEWS, RoadService.WORDS, RoadService.DIALOGS, RoadService.STORIES, RoadService.SHADOW,
        RoadService.TRANSFORMS, RoadService.RAPID, RoadService.BUILDS, RoadService.RIDDLES,
    )

    /** Every session that plays from its id (the old ones too). */
    val SESSIONS: List<String> = MAIN + MORE_SESSIONS

    /** How a node shows: a tab or a folder ([browsable]) shows its items as a [grid] or a list; a session plays. */
    data class Node(val id: String, val browsable: Boolean, val grid: Boolean = false) {
        val playable: Boolean get() = !browsable && id != NONE
    }

    /** The tabs, under the root. */
    val TABS = listOf(Node(TAB, browsable = true, grid = true), Node(MORE, browsable = true))

    /**
     * The items under [parent]: the tabs under the root; a tab's sessions, or where to get ready first when the phone has
     * nothing ([ready] false); null for an id that has no items (a session, an unknown id).
     */
    fun children(parent: String, ready: Boolean): List<Node>? = when (parent) {
        ROOT -> TABS
        TAB -> if (ready) MAIN.map { Node(it, browsable = false) } else listOf(Node(NONE, browsable = false))
        MORE -> if (ready) MORE_SESSIONS.map { Node(it, browsable = false) } else listOf(Node(NONE, browsable = false))
        else -> null
    }

    /** The node of [id] (a tab, a session), or null for none of them. */
    fun node(id: String): Node? = when (id) {
        ROOT -> Node(ROOT, browsable = true)
        TAB, MORE -> TABS.first { it.id == id }
        in SESSIONS -> Node(id, browsable = false)
        else -> null
    }

    /** The switch a session's custom action makes: "🌙 Mirno" and back, "❓ Kviz" and back, to "🚗 Za pot" ([RoadService.MIX]). */
    fun toggle(playing: String?, to: String): String = if (playing == to) RoadService.MIX else to

    /**
     * The two custom actions while [playing] (beside ✓ and ✗ on a card): "🌙 Mirno" and "❓ Kviz"; in one of them its
     * button goes back to "🚗 Za pot". The ids of the sessions they switch to, in their order.
     */
    fun actions(playing: String?): List<String> = listOf(RoadService.EASY, RoadService.QUIZ)
}
