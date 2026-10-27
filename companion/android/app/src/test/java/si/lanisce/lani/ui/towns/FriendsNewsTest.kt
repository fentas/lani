package si.lanisce.lani.ui.towns

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lanisce.lani.data.FeastNews
import si.lanisce.lani.data.FriendTown
import si.lanisce.lani.data.FriendTrouble
import si.lanisce.lani.data.FriendshipList
import si.lanisce.lani.data.GuestEntry
import si.lanisce.lani.data.HostAnswer
import si.lanisce.lani.data.MovesInfo
import si.lanisce.lani.data.ReceivedQuestion
import si.lanisce.lani.data.SharedFeast
import si.lanisce.lani.data.TownMove
import si.lanisce.lani.data.TownQuestionsList

/**
 * Friends' badge on Home and over the village (FriendsNews): what the learner can answer or act on there now, each once,
 * and a guest only until Friends was seen after they came.
 */
class FriendsNewsTest {
    private val seen = "2026-10-26T12:00:00Z"

    @Test
    fun `nothing linked, nothing waits`() {
        assertEquals(0, FriendsNews.count(null, emptyList(), null, ""))
        assertEquals(0, FriendsNews.count(FriendshipList(), emptyList(), TownQuestionsList(), seen))
    }

    @Test
    fun `a guest counts until Friends was seen after they came`() {
        val guests = listOf(GuestEntry("g1", at = "2026-10-26T09:00:00Z"), GuestEntry("g2", at = "2026-10-27T08:00:00Z"), GuestEntry("g3", at = ""))
        assertEquals(1, FriendsNews.count(null, guests, null, seen))
        assertEquals(2, FriendsNews.count(null, guests, null, ""))
        assertEquals("2026-10-27T08:00:00Z", FriendsNews.newest(guests))
        assertEquals(0, FriendsNews.count(null, guests, null, FriendsNews.newest(guests)))
        assertEquals("", FriendsNews.newest(emptyList()))
    }

    @Test
    fun `a question to answer, help to send, a move offered and a feast to share each count`() {
        val questions = TownQuestionsList(received = listOf(ReceivedQuestion("q1"), ReceivedQuestion("q2", answer = HostAnswer("Dobro."))))
        val towns = FriendshipList(
            listOf(
                FriendTown("a", event = FriendTrouble("e1", can = true)),
                // help sent already, or not possible yet: nothing to do about it now
                FriendTown("b", event = FriendTrouble("e2", can = true, aided = true)),
                FriendTown("c", event = FriendTrouble("e3", can = false)),
                FriendTown(
                    "d",
                    moves = MovesInfo(list = listOf(TownMove("m1", by = "them"), TownMove("m2", by = "me"), TownMove("m3", by = "them", status = "done"))),
                    feast = FeastNews(openUntil = "2026-10-30"),
                ),
                FriendTown("e", feast = FeastNews(openUntil = "2026-10-30", shared = SharedFeast("k"))),
            ),
        )
        // q1; a's trouble; d's move offered by them; d's open feast
        assertEquals(4, FriendsNews.count(towns, emptyList(), questions, seen))
    }
}
