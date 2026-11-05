package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import si.lanisce.lani.data.FamilyMembersApi
import java.io.IOException

/**
 * A family on a computer on a parent's device (data/FamilyMembersApi; ui/settings/FamilyShare): who's in it, the member just added
 * and the code for their device, what's left on the computer. [api] is null where there's no computer. Nothing here runs on a
 * child's device, and its bridge would refuse it. Compose state, on the main thread.
 */
class FamilyComputer(private val scope: CoroutineScope, private val api: () -> FamilyMembersApi?, private val child: () -> Boolean) {
    /** The family, once read; null before and from an older node ([older]). */
    var family by mutableStateOf<FamilyMembersApi.Family?>(null)
        private set

    /** The computer's Lani doesn't know this yet. */
    var older by mutableStateOf(false)
        private set

    /** The member or code on screen. */
    var code by mutableStateOf<FamilyMembersApi.Code?>(null)
        private set

    /** Why the last step didn't happen: the bridge's word, or "offline". */
    var problem by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    private fun step(work: suspend (FamilyMembersApi) -> Unit) {
        val a = api() ?: return
        if (child() || busy) return
        busy = true
        problem = null
        scope.launch {
            try {
                work(a)
            } catch (e: FamilyMembersApi.Refused) {
                problem = e.code
            } catch (_: IOException) {
                problem = "offline"
            } finally {
                busy = false
            }
        }
    }

    fun load() {
        step { a ->
            val f = a.list()
            older = f == null
            family = f
        }
    }

    /** A new member: their code on screen, the family read again. */
    fun add(name: String, target: String, base: String, child: Boolean) {
        step { a ->
            code = a.add(name.trim(), target, base, child)
            family = a.list()
        }
    }

    /** Another code for [profile]. */
    fun newCode(profile: String) {
        step { a -> code = a.code(profile) }
    }

    fun unpair(profile: String, device: String) {
        step { a ->
            a.unpair(profile, device)
            family = a.list()
        }
    }

    fun budget(profile: String, device: String, cap: Double?) {
        step { a ->
            a.budget(profile, device, cap)
            family = a.list()
        }
    }

    /** The code goes off the screen; it stays good until it's used or runs out. */
    fun done() {
        code = null
        problem = null
    }
}
