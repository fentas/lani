package si.lanisce.lani.game.scene

import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas

/**
 * Per-frame inputs of a close-up scene. Rendering is a pure function of (painter, frame, canvas size).
 *
 * @param time animation clock in seconds
 * @param hour local hour of day, 0..24 (light: dawn, day, dusk, night)
 * @param month 1..12 (season: leaves, snow)
 * @param objects the object slots to draw as tappable; a slot not listed is still drawn as scenery
 * @param people who is there now, and where
 * @param highlight an object slot or person id to outline (the one just tapped)
 * @param sky the weather now: rain, snow, fog, wind, gloom, lightning (see [Sky]; clear by default)
 * @param fx the art's effects a dialog set, by name ([SceneArt.effects]) → level 0..1; one not listed shows as by itself
 * @param pokes what taps set off and plays now, by [Poke] id ([ScenePainter.pokes]): the step and when it began, on [time]'s clock
 * @param world what the village has built ([SceneWorld]): a fixture is drawn only when it's there (the kozolec, the mill),
 *   a project's as far as its steps have come; by default everything is there ([SceneWorld.ALL])
 * @param moon the moon's age, days since the new moon ([si.lanisce.lani.game.render.Moon.age]; the app passes today's):
 *   its phase and when it is up; full by default
 * @param date the local day (its month is [month]), for the feasts a place keeps (the church's Easter baskets, the bee
 *   house's flags on World Bee Day); null for none: the tests, the training stage
 * @param village the village's seed and [day] the date (epoch day): the wild animals out ([Wildlife.out]) are that
 *   village's mix for the day and the [hour]'s part of it
 * @param wild the wild animals out, when a test or a tool names them ([Wildlife] ids, all of them at any hour); null: the
 *   day's, from [village], [day], [hour] and [month]
 * @param skyNow the real sky over the village at this minute: an outdoor scene shows its stars, planets, Milky Way and
 *   shooting stars, and the real moon; null (tests, previews, stickers): the painted stars and the moon of [moon]
 * @param wall the clock's epoch ms, for the shooting stars
 * @param skyMark what of the sky to show brighter, its card being open ([si.lanisce.lani.game.sky.SkyTap.mark])
 */
data class SceneFrame(
    val time: Double = 0.0,
    val hour: Float = 12f,
    val month: Int = 6,
    val objects: Set<String> = emptySet(),
    val people: List<PersonInScene> = emptyList(),
    val highlight: String? = null,
    val sky: Sky = Sky.CLEAR,
    val fx: Map<String, Float> = emptyMap(),
    val pokes: Map<String, Poked> = emptyMap(),
    val world: SceneWorld = SceneWorld.ALL,
    val moon: Float = si.lanisce.lani.game.render.Moon.FULL,
    val date: java.time.LocalDate? = null,
    val village: Long = 0L,
    val day: Long = 0L,
    val wild: Set<String>? = null,
    val skyNow: si.lanisce.lani.game.sky.SkyNow? = null,
    val wall: Long = 0L,
    val skyMark: String? = null,
) {
    /** The wild animals of [art] out in this frame: [wild], or the day's ([Wildlife.out]). */
    fun wildOf(art: String): Set<String> = wild ?: Wildlife.out(art, village, day, hour, month)
}

/**
 * A person drawn in a scene: [art] sprite at [slot] (a person spot of the art, or a spot of its stage, [SceneArt.stage]);
 * [talking] animates them (mouth, gesture), [pose] shows a mood.
 *
 * On the stage ([Stance], companion/SCENES.md "Stage directions") they are at [slot] in [stance]: standing, sitting,
 * crouching, hidden (only a clue shows, and they can't be tapped), peeking out, lying. On their way there, [from] is the
 * spot they come from (in [fromStance]) and [moveAge] the seconds since they set off (a walk, a dash into hiding, a pop
 * out of it; over by [Stage.MOVE_S]): the painter draws the move from it, so every look of the scene agrees.
 */
data class PersonInScene(
    val id: String,
    val art: String,
    val slot: String,
    val talking: Boolean = false,
    val pose: Pose = Pose.IDLE,
    val stance: Stance = Stance.STAND,
    val from: String? = null,
    val fromStance: Stance = Stance.STAND,
    val moveAge: Float = 0f,
)

/** How a person stands: moods and gestures for scenes, intros and the training stage. */
enum class Pose {
    IDLE,
    /** Speaking (mouth, a hand gesture); like `talking`. */
    TALK,
    /** Pleased: a smile, a little hop. */
    HAPPY,
    /** Arms up, a jump: a great answer, a finished quest. */
    CHEER,
    /** Kind disappointment: head tilted, a small shrug. */
    SAD,
    /** Hand to the chin, looking up. */
    THINK,
    /** Hand cupped at the ear: before a listening exercise. */
    LISTEN,
    /** A wave: hello and goodbye. */
    WAVE,
    /**
     * Asleep: lying in a bed under a cover, eyes shut, breathing slowly, Zzz rising (at a painter's [ScenePainter.beds] at
     * night, see [Sleep]); elsewhere lying on a pillow on the ground, and in a portrait dozing.
     */
    SLEEP,
}

/**
 * Draws one person big, for intros and the training stage: head and shoulders filling the canvas (a bust),
 * on a plain or softly lit background. Nominal canvas 64×64; any square-ish size works.
 */
interface PortraitPainter {
    /** [seed] varies a sprite per person (hair, clothes) for the village's own people: their id's hash. */
    fun render(canvas: PixelCanvas, art: String, pose: Pose, time: Double, hour: Float = 12f, seed: Int = 0)
}

/** The portrait painter: the art's (see game/render/scene/Portraits.kt); replaceable for tests. */
object Portraits {
    var painter: PortraitPainter = si.lanisce.lani.game.render.scene.PortraitsPainter()
}

/** What a tap can land on. */
sealed interface SceneTarget {
    data class Thing(val slot: String) : SceneTarget
    data class Person(val id: String) : SceneTarget
}

/**
 * A tappable area in canvas pixels: [left, top, right, bottom], inclusive-exclusive. [anchorX]/[anchorY]
 * is where a label or speech bubble points to (the top centre of the thing or the person's head).
 */
data class SceneHit(val target: SceneTarget, val left: Int, val top: Int, val right: Int, val bottom: Int, val anchorX: Int, val anchorY: Int)

/**
 * Draws one art (see [SceneArt]). Nominal canvas 240×160; the view may give up to 1.5× the width and
 * 2× the height (like the village's SceneFit): keep the composition centred and extend floor, walls or sky.
 *
 * Closer up, the same scene is drawn finer: at [Lens.k] 2 or 3 the picture is k times the size of the scene's
 * canvas ([Lens.baseW] × [Lens.baseH]), the same composition in k × k pixels per canvas pixel, and [canvas]
 * holds just a window of it from ([Lens.x0], [Lens.y0]) (see [PixelCanvas.originX]).
 */
interface ScenePainter {
    val art: String

    /**
     * Renders the frame and returns the hit areas of the tappable objects and the people, front-most last, always
     * in the scene canvas's pixels (at detail 1), whatever the [lens]; the default lens: the canvas is the scene's.
     */
    fun render(canvas: PixelCanvas, frame: SceneFrame, lens: Lens = Lens(1, 0, 0, canvas.width, canvas.height)): List<SceneHit>

    /** The background colour around the canvas (letterbox), usually the dominant wall or sky colour. */
    fun backdrop(frame: SceneFrame): Int

    /**
     * What the last frame drawn on [canvas] shows at its pixel ([x], [y]), to the pixel: the thing or person
     * that painted it, or else the nearest one within [slop] px. Null when nothing tappable is there (or the
     * painter can't tell): then the hit rectangles decide.
     */
    fun targetAt(canvas: PixelCanvas, x: Int, y: Int, slop: Int): SceneTarget? = null

    /**
     * Which spot of the art's stage ([SceneArt.stage]) the last frame drew around the scene canvas's point ([x], [y]) (at
     * detail 1), within [slop] px: where someone stands, sits or hides there (a tap turn's place, companion/SCENES.md "Tap
     * turns"); null for none, or an art without a stage.
     */
    fun spotAt(x: Float, y: Float, slop: Float): String? = null

    /** The small reactions to a tap this art plays (the cat stretching, the pot's lid rattling), see [Poke]; none by default. */
    val pokes: List<Poke> get() = emptyList()

    /** Which of [pokes] the last frame drawn on [canvas] has at its pixel ([x], [y]) or within [slop] px; null for none. */
    fun pokeAt(canvas: PixelCanvas, x: Int, y: Int, slop: Int): String? = null

    /**
     * What of the real sky the last frame drew is at the scene canvas's point ([x], [y]) (at detail 1), within [slop]
     * px, at [wall] epoch ms: the moon, a star, a planet, a constellation, the Milky Way or a shooting star; null for
     * none (an indoor scene, a frame without the real sky).
     */
    fun skyAt(x: Float, y: Float, slop: Float, wall: Long): si.lanisce.lani.game.sky.SkyTap? = null

    /** Whether the last frame drew the real sky (an outdoor scene): TalkBack gets the moon and tonight's sky then. */
    val showsSky: Boolean get() = false

    /** What of the real sky the last frame shows, to open from a list ([si.lanisce.lani.game.sky.SkyTargets.up]); none indoors. */
    fun skyUp(): List<si.lanisce.lani.game.sky.SkyTap> = emptyList()

    /**
     * The beds of this art (the tent's sleeping bag, the bench by the kitchen's stove): at night someone whose home this
     * place is lies there asleep ([Sleep]), a person of the frame at that "slot" in [Pose.SLEEP]; none by default.
     */
    val beds: List<String> get() = emptyList()

    /**
     * Which of [beds] are a child's (a small bed, a cot): a child of the home sleeps in one of them first, a grown-up never
     * (companion/SCENES.md, "Asleep at night"); none by default.
     */
    val childBeds: Set<String> get() = emptySet()
}

/** The painters by art id. Unknown arts fall back to [Placeholder]. */
object ScenePainters {
    /** Every built-in painter; add new ones here. */
    private val painters: Map<String, ScenePainter> = si.lanisce.lani.game.render.scene.ScenePaintersList.all().associateBy { it.art }

    fun of(art: String): ScenePainter = painters[art] ?: Placeholder(art)

    /** A painter of its own for [art] (a view drawing two pictures at once, each with its own last frame to hit-test). */
    fun create(art: String): ScenePainter = si.lanisce.lani.game.render.scene.ScenePaintersList.create(art) ?: Placeholder(art)

    /** Flat boxes in a grid, one per slot, so screens work before (or without) the real art. */
    class Placeholder(override val art: String) : ScenePainter {
        override fun backdrop(frame: SceneFrame): Int = 0xFF2B2B36.toInt()

        override fun render(canvas: PixelCanvas, frame: SceneFrame, lens: Lens): List<SceneHit> {
            canvas.originX = lens.x0; canvas.originY = lens.y0
            canvas.reset(backdrop(frame))
            if (lens.k > 1) canvas.zoomAt(0, 0, lens.k) // the same boxes, bigger
            val slots = SceneArt.objects[art].orEmpty()
            val hits = ArrayList<SceneHit>()
            val cols = 6
            val w = lens.baseW / cols
            val h = 24
            for ((k, slot) in slots.withIndex()) {
                val x = (k % cols) * w + 2; val y = 8 + (k / cols) * (h + 4)
                val col = if (slot == frame.highlight) 0xFFFFE08A.toInt() else 0xFF6F7A8C.toInt()
                canvas.fillRect(x, y, w - 4, h, col)
                hits.add(SceneHit(SceneTarget.Thing(slot), x, y, x + w - 4, y + h, x + (w - 4) / 2, y))
            }
            val spots = SceneArt.personSlots[art].orEmpty()
            for (p in frame.people) {
                val k = spots.indexOf(p.slot).coerceAtLeast(0)
                val x = 12 + k * 40; val y = lens.baseH - 44
                canvas.fillRect(x, y, 16, 36, 0xFFE0A070.toInt())
                hits.add(SceneHit(SceneTarget.Person(p.id), x, y, x + 16, y + 36, x + 8, y))
            }
            canvas.zoomOff()
            return hits
        }
    }
}
