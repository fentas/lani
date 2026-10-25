package si.lanisce.lani.ui.scene

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.app.SceneTalk
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Speaker
import si.lanisce.lani.data.Voice

/**
 * A dialog's lines said aloud as it goes on (companion/SCENES.md, "The learner's voice"). A step of the dialog (a pick, a
 * turn typed, "Naprej") brings the learner's own line and someone's after it (a reply, the dialog's next line): the
 * learner's is said first, in their voice ([Clips.PLAYER]: the node's cast's `player` for their gender), when it is said
 * at all ([Said.voiced]: picked or typed, not one they said into the 🎤 themselves); someone's comes once it has been
 * heard to its end, and after a 🔊 tapped meanwhile, never over them. The next step cuts what is being said, as it always
 * did. [talking]: whose line is being said now (they're drawn talking); null while the learner's is, or none.
 */
@Stable
class DialogVoice {
    var talking by mutableStateOf<String?>(null)
        private set

    /** How many lines the dialog had when it last said something; -1: nothing yet (the dialog just shown). */
    private var heard = -1

    /**
     * Says what [run]'s newest step brought ([toSay], since the last time), in turn: [mine] says the learner's line and
     * returns once it has been heard (or cut); [quiet] waits while anything else is still being said (a 🔊 tapped); then
     * [theirs] says someone's, drawn talking meanwhile as [idOf] has them. The pauses are the dialog's ([pauses]).
     */
    suspend fun say(
        run: DialogRun,
        mine: suspend (Said) -> Unit,
        theirs: suspend (Said) -> Unit,
        quiet: suspend () -> Unit = {},
        idOf: (String) -> String = { it },
    ) {
        val lines = toSay(run.said, heard)
        heard = run.said.size
        val pauses = pauses(run, lines)
        for ((i, line) in lines.withIndex()) {
            val who = line.who
            if (who == null) {
                delay(pauses[i])
                mine(line)
                continue
            }
            // after the learner's line: what a 🔊 tapped meanwhile says is heard to its end first
            if (i > 0) quiet()
            delay(pauses[i])
            talking = idOf(who)
            try {
                theirs(line)
            } finally {
                talking = null
            }
        }
    }

    companion object {
        /**
         * The lines of [said] to say now, in order, the dialog having had [before] lines the last time (-1: just shown, as it
         * opens or comes back: only its newest line, when it is someone's): the learner's own line when it is said
         * ([Said.voiced]), then someone's line after it. A step adds at most the two; a dialog that went on by more meanwhile
         * says only its newest step's.
         */
        fun toSay(said: List<Said>, before: Int): List<Said> {
            val last = said.lastOrNull() ?: return emptyList()
            if (before < 0 || before > said.size) return listOfNotNull(last.takeIf { it.who != null })
            if (before == said.size) return emptyList()
            val mine = said.getOrNull(said.size - 2)?.takeIf { said.size - 2 >= before && it.who == null && last.who != null }
            return listOfNotNull(mine?.takeIf { it.voiced }, last.takeIf { it.who != null || it.voiced })
        }

        /**
         * The pause before each of [lines] (of [run]'s newest step): the learner's line a moment after it shows (the person's
         * reaction to the pick plays meanwhile); someone's a breath after the learner's ([LinesInTurn.GAP_MS]), else as a
         * dialog's line always came ([DialogMood.sayAfter]: later for the first line and a reply).
         */
        fun pauses(run: DialogRun, lines: List<Said>): List<Long> = lines.mapIndexed { i, l ->
            when {
                l.who == null -> DialogMood.SAY_MS
                i > 0 && lines[i - 1].who == null -> LinesInTurn.GAP_MS
                else -> DialogMood.sayAfter(run)
            }
        }
    }
}

/**
 * [talk]'s lines said aloud as it goes on ([DialogVoice]): the learner's in their voice ([Clips.PLAYER]), each of the
 * others' in theirs ([voiceOf]: who is drawn talking for a line's speaker, and the voice it is read in). Returns whose line
 * is being said now. Every dialog panel's screen says its lines this way: a scene's dialog (the storyteller's story too),
 * a keeper's talk, an arrival, a project's step and a guest's dialog on a visit.
 */
@Composable
fun rememberDialogVoice(speaker: Speaker, talk: SceneTalk?, voiceOf: (String) -> Pair<String, Spoken>): String? {
    val voice = remember(talk?.key, talk?.run?.seed) { DialogVoice() }
    val of by rememberUpdatedState(voiceOf)
    LaunchedEffect(talk?.key, talk?.run?.said?.size) {
        val run = talk?.run ?: return@LaunchedEffect
        voice.say(
            run,
            mine = { line ->
                val from = System.currentTimeMillis()
                speaker.sayThrough(line.sl, voiceName = Clips.PLAYER, stopIfLeft = false)
                if (BuildConfig.QA) Log.i("Speaker", "heard «${Voice.normalize(line.sl)}» after ${System.currentTimeMillis() - from} ms (the learner's line, ${line.by})")
            },
            theirs = { line ->
                val (_, v) = of(line.who!!)
                val from = System.currentTimeMillis()
                // drawn talking while it is heard (nothing to say it: for as long as it takes to read)
                if (!speaker.sayThrough(line.sl, voiceName = v.voice, fallback = v.fallback, person = v.person, stopIfLeft = false)) delay(Speaker.readingMs(line.sl))
                if (BuildConfig.QA) Log.i("Speaker", "heard «${Voice.normalize(line.sl)}» after ${System.currentTimeMillis() - from} ms (${line.who})")
            },
            quiet = speaker::awaitQuiet,
            idOf = { who -> of(who).first },
        )
    }
    return voice.talking
}
