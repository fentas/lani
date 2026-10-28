package si.lanisce.lani.game.render

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration

/**
 * The pictures kept while their screen is away, for when it comes back (2026-10-28, Jan: "going back to the main screen
 * freezes the app for a short moment, then it works; consistently"; docs/plans/05-built/home-return.md). Home and the
 * village are composed anew each time the learner comes back to them (the back stack keeps their place, not their
 * composition): a renderer made anew there had nothing kept (the ground's cells, the trees' stamps, the land's layout)
 * and drew its first picture whole. Kept here, process-wide, are the Home header's village ([strip], by canvas size) and
 * the learner's own village's map ([townRenderer], and its pictures by canvas size: [town]), with their renderers and
 * their last pictures: coming back shows the last picture at once, and its render thread brings the next.
 *
 * What is kept goes under memory pressure ([trim], from the app's [ComponentCallbacks2]): the views not on the screen
 * now are let go whole, and those on it keep their pictures but let their renderers' kept ground and trees go.
 * A visited town's map (another renderer) is kept too, but only until another one comes ([MAX_TOWNS]).
 */
internal object Retained {
    /** A kept view and how many composables use it now. */
    private class Kept<T>(val value: T) { var users = 0 }

    private val strips = LinkedHashMap<Long, Kept<StripPictures>>()
    private val towns = LinkedHashMap<Pair<VillageRenderer, Long>, Kept<TownPictures>>()
    private var own: VillageRenderer? = null
    private var attached = false

    /** The views kept by size at most: a phone turned (two sizes), the strip in two places. */
    const val MAX_STRIPS = 2
    const val MAX_TOWNS = 2

    private fun size(w: Int, h: Int) = (w.toLong() shl 32) or h.toLong()

    /** The learner's own village's renderer, kept (the village screen's: its map, its taps, its people's anchors). */
    @Synchronized fun townRenderer(): VillageRenderer = own ?: VillageRenderer().also { own = it }

    /** The Home header's village of a [w] × [h] canvas, kept; [use] while it is on the screen. */
    @Synchronized fun strip(w: Int, h: Int): StripPictures {
        val key = size(w, h)
        strips[key]?.let { strips.remove(key); strips[key] = it; return it.value }
        val k = Kept(StripPictures(w, h, VillageRenderer()))
        strips[key] = k
        evict(strips, MAX_STRIPS) { it.close() }
        return k.value
    }

    /** The map of [renderer]'s village on a [w] × [h] canvas, kept; [use] while it is on the screen. */
    @Synchronized fun town(w: Int, h: Int, renderer: VillageRenderer): TownPictures {
        val key = renderer to size(w, h)
        towns[key]?.let { towns.remove(key); towns[key] = it; return it.value }
        val k = Kept(TownPictures(w, h, renderer))
        towns[key] = k
        evict(towns, MAX_TOWNS) { it.close() }
        return k.value
    }

    /** [view] (a kept strip or map) is on the screen ([on]) or not any more. */
    @Synchronized fun use(view: Any, on: Boolean) {
        val k = strips.values.firstOrNull { it.value === view } ?: towns.values.firstOrNull { it.value === view } ?: return
        k.users = (k.users + if (on) 1 else -1).coerceAtLeast(0)
    }

    /** The oldest kept views not on the screen go until at most [max] are kept. */
    private fun <K, T> evict(map: LinkedHashMap<K, Kept<T>>, max: Int, close: (T) -> Unit) {
        val it = map.entries.iterator()
        while (map.size > max && it.hasNext()) {
            val e = it.next()
            if (e.value.users > 0) continue
            close(e.value.value)
            it.remove()
        }
    }

    /**
     * Memory is short ([level]: a [ComponentCallbacks2] level): from [ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW] (and when
     * the app is in the background, from [ComponentCallbacks2.TRIM_MEMORY_BACKGROUND]) the views not on the screen go whole,
     * the others' renderers let their kept ground and trees go (drawn again as they are shown).
     */
    @Synchronized fun trim(level: Int) {
        val low = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (!low) return
        for (map in listOf<LinkedHashMap<*, out Kept<*>>>(strips, towns)) {
            val it = map.entries.iterator()
            while (it.hasNext()) {
                val k = it.next().value
                val v = k.value
                if (k.users > 0) { (v as? StripPictures)?.renderer?.trim(); (v as? TownPictures)?.renderer?.trim(); continue }
                (v as? StripPictures)?.close(); (v as? TownPictures)?.close()
                it.remove()
            }
        }
        if (towns.keys.none { it.first === own }) { own?.trim(); own = null }
    }

    /** Listens for memory pressure ([trim]) from [context]'s application, once. */
    @Synchronized fun attach(context: Context) {
        if (attached) return
        attached = true
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) = trim(level)
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = trim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        })
    }

    /** Forgets everything kept (tests). */
    @Synchronized fun clear() {
        strips.values.forEach { it.value.close() }; towns.values.forEach { it.value.close() }
        strips.clear(); towns.clear(); own = null
    }

    /** How many views are kept (tests). */
    val kept: Int @Synchronized get() = strips.size + towns.size
}
