package si.lanisce.lani.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import si.lanisce.lani.game.ambient.AmbientBus
import si.lanisce.lani.game.ambient.Cue
import si.lanisce.lani.game.ambient.Place
import si.lanisce.lani.game.ambient.Sfx
import si.lanisce.lani.game.ambient.Soundscape
import si.lanisce.lani.game.ambient.Thunder
import si.lanisce.lani.game.render.TownClock
import si.lanisce.lani.game.scene.Poked
import si.lanisce.lani.game.scene.Sky
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The background sounds (companion/GAME.md, "Background sounds"): birds, crickets, rain, the stream … quietly under what
 * is on screen. The village and the scene screens say where the learner is ([show]); [Soundscape] decides what is heard
 * there now, and [AmbientPlayer] plays it. Short sounds join in where the picture shows one, timed on its clock: the
 * thunder after each flash ([Thunder]), what a tap sets off ([poke]) and a dialog's effect coming on ([Sfx]). On by
 * default at a low level; the learner can switch them off or set their volume ([on], [volume], kept on the phone). They
 * duck under a voice ([talking]), stop while the microphone records ([MicOpen]) and while the app is away ([away]).
 */
class Ambience(context: Context, scope: CoroutineScope, talking: () -> Boolean) {
    private val prefs = context.getSharedPreferences("ambience", Context.MODE_PRIVATE)
    private val player = AmbientPlayer(context.applicationContext, talking, micOpen = { MicOpen.open })
    private val wake: () -> Unit = { player.wake() }

    /** The learner wants them. */
    var on by mutableStateOf(prefs.getBoolean(KEY_ON, true))
        private set

    /** Their volume, 0..1 ([AmbientBus.master]). */
    var volume by mutableFloatStateOf(prefs.getFloat(KEY_VOLUME, AmbientBus.DEFAULT_VOLUME).coerceIn(0f, 1f))
        private set

    /** Who said where the learner is last, and what they said: the screen on top. */
    private var owner: Any? = null
    private var place: Place? = null
    private var sky: Sky = Sky.CLEAR
    /** The picture's clock of the screen on top ([TownClock.origin]), if it said. */
    private var clock: TownClock? = null

    init {
        MicOpen.listen(wake)
        // the hour moves on: dusk brings the crickets in slowly
        scope.launch {
            while (true) {
                delay(TICK_MS)
                update()
            }
        }
    }

    fun turn(on: Boolean) {
        this.on = on
        prefs.edit().putBoolean(KEY_ON, on).apply()
        update()
    }

    /** The slider moves: heard at once; [save] when it's let go. */
    fun setVolume(v: Float, save: Boolean = true) {
        volume = v.coerceIn(0f, 1f)
        if (save) prefs.edit().putFloat(KEY_VOLUME, volume).apply()
        update()
    }

    /**
     * [owner] (a screen) shows [place] under [sky], its picture on [clock]: its sounds fade in, the ones before fade out. An
     * effect a dialog brings to the same scene sounds as it comes on (a bell's peal, the oven catching: [Sfx.fx]).
     */
    fun show(owner: Any, place: Place, sky: Sky, clock: TownClock? = null) {
        if (this.owner === owner && this.place == place && this.sky == sky && this.clock === clock) return
        val before = (this.place as? Place.Scene)?.takeIf { this.owner === owner && it.art == (place as? Place.Scene)?.art }
        this.owner = owner
        this.place = place
        this.sky = sky
        this.clock = clock
        update()
        if (before != null && place is Place.Scene && before.fx != place.fx) cue(clock) { now -> Sfx.fx(place.art, before.fx, place.fx, now) }
    }

    /** A tap on [id] of [art] set off its reaction [poked] (on the scene's [clock]): its sound, if it makes one ([Sfx.poke]). */
    fun poke(art: String, id: String, poked: Poked, clock: TownClock) {
        val origin = clock.origin.takeIf { it >= 0 } ?: (System.nanoTime() - (poked.start * 1e9).toLong())
        if (on) player.play(Sfx.poke(art, id, poked.step, poked.start), origin)
    }

    /** [owner] left the screen: its sounds fade out, unless another screen has said where the learner is since. */
    fun leave(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        place = null
        update()
    }

    /** The app left the screen, or the voice dialog of the phone's recognizer came over it: they stop, and come back after. */
    fun away(away: Boolean) = player.away(away)

    fun release() {
        MicOpen.unlisten(wake)
        player.release()
    }

    private fun update() {
        val now = LocalDateTime.now()
        val here = place
        val levels = here?.let { Soundscape.mix(it, now.hour + now.minute / 60f, now.monthValue, sky) }.orEmpty()
        player.set(levels, on, volume)
        val c = clock
        player.sky(here?.let { Thunder.lightning(it, sky) }, { c?.origin ?: -1L }, here?.let(Thunder::heard) ?: 1f, shown = here != null)
        if (on && here != null) player.prepare(Sfx.sounds(here, sky))
    }

    /** The short sounds [make] gives for the picture's second now, on [clock] (without one yet: from now). */
    private fun cue(clock: TownClock?, make: (Double) -> List<Cue>) {
        if (!on) return
        val t = System.nanoTime()
        val origin = clock?.origin?.takeIf { it >= 0 } ?: t
        player.play(make((t - origin) / 1e9), origin)
    }

    private companion object {
        const val KEY_ON = "on"
        const val KEY_VOLUME = "volume"

        /** How often the hour is looked at again. */
        const val TICK_MS = 20_000L
    }
}

/**
 * The microphone is open somewhere in the app (a speaking exercise, the dialog's 🎤, reading aloud, a family recording):
 * the background sounds keep quiet meanwhile, since they'd spoil speech recognition. Whatever opens it [hold]s it and
 * releases the hold when it closes (a composable: ui/Mic.kt, `MicHold`).
 */
object MicOpen {
    private val holds = AtomicInteger(0)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    val open: Boolean get() = holds.get() > 0

    /** The microphone opens; [Hold.release] when it closes (once is enough, twice is harmless). */
    fun hold(): Hold {
        holds.incrementAndGet()
        listeners.forEach { it() }
        return Hold()
    }

    fun listen(f: () -> Unit) = listeners.add(f)
    fun unlisten(f: () -> Unit) = listeners.remove(f)

    class Hold internal constructor() {
        private val done = AtomicBoolean(false)

        fun release() {
            if (!done.compareAndSet(false, true)) return
            holds.decrementAndGet()
            listeners.forEach { it() }
        }
    }
}
