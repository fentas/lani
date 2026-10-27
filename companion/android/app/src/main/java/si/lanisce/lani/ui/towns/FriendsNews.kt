package si.lanisce.lani.ui.towns

import si.lanisce.lani.data.FriendshipList
import si.lanisce.lani.data.GuestEntry
import si.lanisce.lani.data.TownQuestionsList

/**
 * What waits for the learner in "🤝 Prijatelji · Friends", for the badge on its entries on Home and over the village
 * (docs/plans/05-built/navigation.md, "Friends one tap away"): each thing they can answer or act on there now, counted
 * once.
 */
object FriendsNews {
    /**
     * How many things wait: a guest's question not answered yet ([questions]), a guest who came after Friends was last seen
     * ([guests] newer than [seenAt], an ISO time: help, a gift or a message, a trade), a friend's trouble they can send
     * help against, a move a friend's town offers, a feast open to share ([friendships]): what Friends' cards offer to do
     * ([FriendsLogic.notices]).
     */
    fun count(friendships: FriendshipList?, guests: List<GuestEntry>, questions: TownQuestionsList?, seenAt: String): Int {
        val asked = questions?.open?.size ?: 0
        val came = guests.count { it.at.isNotBlank() && it.at > seenAt }
        val towns = friendships?.towns.orEmpty()
        val trouble = towns.count { t -> t.event?.let { it.can && !it.aided } == true }
        val offers = towns.sumOf { t -> t.moves.list.count { it.open && it.by == "them" } }
        val feasts = towns.count { t -> t.feast.openUntil != null && t.feast.shared == null }
        return asked + came + trouble + offers + feasts
    }

    /** The guests' newest time ([GuestEntry.at]), Friends' "seen" mark once it shows; "" with none. */
    fun newest(guests: List<GuestEntry>): String = guests.maxOfOrNull { it.at }.orEmpty()
}
