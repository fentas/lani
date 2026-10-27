package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Decor
import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.FireCrowd
import si.lanisce.lani.game.scene.SceneWorld
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The village's centre as its age has it (companion/SCENES.md "Ob ognju by the age", GAME.md "Hearths"): the camp in its
 * clearing ([CAMP], Ogenj and Tabor), the hamlet's trodden ground with its first huts among the spruces ([HAMLET]), the
 * village's fire ring with its benches under the young linden, the well and the house fronts round the place ([VILLAGE]),
 * the market town's cobbled square with its fire basket, the market's stalls and the church ([MARKET]), the town's paved
 * square with its lanterns and the clock in the church's tower ([TOWN]).
 */
internal enum class FireAge {
    CAMP, HAMLET, VILLAGE, MARKET, TOWN;

    companion object {
        fun of(age: Age): FireAge = when (age) {
            Age.OGENJ, Age.TABOR -> CAMP
            Age.ZASELEK -> HAMLET
            Age.VAS -> VILLAGE
            Age.TRG -> MARKET
            Age.MESTO -> TOWN
        }
    }
}

/** The top of a church's tower in a culture: a spire, the Friulian campanile's low cap, an onion dome, battlements. */
internal enum class Tower { SPIRE, CAMPANILE, ONION, BATTLEMENTS }

/** What a roof is covered with: red tiles, the coppi of the Friulian roofs, wooden shingles, slate, reed thatch. */
internal enum class RoofKind { TILES, COPPI, SHINGLES, SLATE, THATCH }

/**
 * A culture's village round the fire (companion/SCENES.md "Ob ognju by the age"): its houses' plaster or stone, their roofs
 * and shutters, wooden balconies or a timbered upper storey, its church's walls and the top of its tower, the linden (the
 * Lake District's oak), the huts' build, and on the coast (Teo's Friuli by the sea) the pines and the sand.
 */
internal class FireStyle(
    val id: String,
    /** The houses' walls in turn, each light, middle and dark. */
    val walls: List<IntArray>,
    val roof: IntArray,
    val roofKind: RoofKind,
    val shutter: Int,
    val tower: Tower,
    val churchWall: IntArray,
    val towerWall: IntArray,
    val towerTop: IntArray,
    /** The houses' walls are of stone (the Lake District's slate stone), not plaster. */
    val stone: Boolean = false,
    /** A wooden balcony across the gable, geraniums on it in summer (Primorska's, Carinthia's). */
    val balcony: Boolean = false,
    /** The upper storey of dark timber (Carinthia's). */
    val timber: Boolean = false,
    /** The huts of stone (Friuli's, the Lake District's) rather than of logs. */
    val hutStone: Boolean = false,
    /** The huts thatched with reed, the casoni of the lagoon (Friuli's coast). */
    val thatch: Boolean = false,
    /** The linden is an oak (the Lake District's). */
    val oak: Boolean = false,
    /** By the sea: pines instead of spruces round the camp, sand in the ground (Friuli's coast). */
    val coast: Boolean = false,
) {
    companion object {
        private fun t(l: Long, m: Long, d: Long) = intArrayOf(Col.hex(l), Col.hex(m), Col.hex(d))

        /** [culture]'s village, on [landscape] (the coast's own touches). */
        fun of(culture: String, landscape: String): FireStyle {
            val coast = landscape == si.lanisce.lani.game.Landscape.COAST.id
            return when (culture) {
                "friuli" -> FireStyle(
                    "friuli",
                    walls = listOf(t(0xF0CB86, 0xD8AE66, 0xAE8848), t(0xE8A07A, 0xCC8460, 0xA06448), t(0xF2E0CC, 0xDCC6AE, 0xB49E86)),
                    roof = t(0xC8683E, 0xA4502E, 0x7A3820), roofKind = RoofKind.COPPI,
                    shutter = if (coast) Col.hex(0x3E6E8E) else Col.hex(0x4F6E46),
                    tower = Tower.CAMPANILE, churchWall = t(0xF2E8D6, 0xDDD0BA, 0xB8AA92), towerWall = t(0xC47A56, 0xA4603E, 0x7E4628),
                    towerTop = t(0xC8683E, 0xA4502E, 0x7A3820),
                    hutStone = true, thatch = coast, coast = coast,
                )
                "kaernten" -> FireStyle(
                    "kaernten",
                    walls = listOf(t(0xF7F3EA, 0xE2DACB, 0xBDB29D), t(0xF4EAD2, 0xDFD2B4, 0xB9AB8A), t(0xF7F3EA, 0xE2DACB, 0xBDB29D)),
                    roof = intArrayOf(Pal.SHINGLE_L, Pal.SHINGLE_M, Pal.SHINGLE_D), roofKind = RoofKind.SHINGLES,
                    shutter = Col.hex(0x2F5A3A),
                    tower = Tower.ONION, churchWall = t(0xF7F3EA, 0xE2DACB, 0xBDB29D), towerWall = t(0xF7F3EA, 0xE2DACB, 0xBDB29D),
                    towerTop = t(0x6FA890, 0x4E8C78, 0x2F5E50),
                    balcony = true, timber = true,
                )
                "lakeland" -> FireStyle(
                    "lakeland",
                    walls = listOf(t(0x9AA0A4, 0x7C8286, 0x5E6468), t(0xF0F0EA, 0xD8D8D0, 0xB0B0A8), t(0x8E928C, 0x72766F, 0x575B55)),
                    roof = intArrayOf(Pal.SLATE_L, Pal.SLATE_M, Pal.SLATE_D), roofKind = RoofKind.SLATE,
                    shutter = Col.hex(0x2E4F6E),
                    tower = Tower.BATTLEMENTS, churchWall = t(0x9AA0A4, 0x7C8286, 0x5E6468), towerWall = t(0x9AA0A4, 0x7C8286, 0x5E6468),
                    towerTop = intArrayOf(Pal.SLATE_L, Pal.SLATE_M, Pal.SLATE_D),
                    stone = true, hutStone = true, oak = true,
                )
                else -> FireStyle(
                    "primorska",
                    walls = listOf(intArrayOf(Pal.WALL_L, Pal.WALL_M, Pal.WALL_D), intArrayOf(Pal.OCHRE_L, Pal.OCHRE_M, Pal.OCHRE_D), t(0xF4E2DA, 0xDCC8BE, 0xB8A094)),
                    roof = intArrayOf(Pal.ROOF_L, Pal.ROOF_M, Pal.ROOF_D), roofKind = RoofKind.TILES,
                    shutter = Pal.SHUTTER,
                    tower = Tower.SPIRE, churchWall = intArrayOf(Pal.WALL_L, Pal.WALL_M, Pal.WALL_D), towerWall = intArrayOf(Pal.WALL_L, Pal.WALL_M, Pal.WALL_D),
                    towerTop = t(0x7A6A60, 0x5E5048, 0x42382F),
                    balcony = true, coast = coast,
                )
            }
        }
    }
}

/**
 * A lot along the square's back edges, where a building stands: x [x0]..[x1], y [y0]..[y1]; its gable faces the square,
 * toward +x ([gableX]: the lot at the left corner) or toward +y (along the right-back edge).
 */
internal class Lot(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val gableX: Boolean)

/** A home on a [lot]: a hut or a house ([hut]) at its [level], its building's id (its hearth cold or not) and a [seed]. */
internal class Home(val lot: Lot, val hut: Boolean, val level: Int, val id: String, val seed: Int, val newest: Boolean)

/**
 * A spot of the crowd round the fire ([FireCrowd]): where one sits ([sit]: on a bench) or stands, facing left when [flip].
 */
internal class CrowdSpot(val x: Float, val y: Float, val sit: Boolean, val flip: Boolean)

/**
 * The centre as [world] has it (companion/SCENES.md "Ob ognju by the age"): its [age], the culture's [style], whether the
 * camp's tent stands by the fire, the church and the homes on the lots round the square, the linden's growth (0: none,
 * 1: a full-grown tree), the well, the fountain's steps, the market's stalls, the lanterns, the spruces that are left of
 * the woods, the town clock's steps in the church's tower, and the crowd's spots.
 */
internal class FireLayout(
    val age: FireAge,
    val style: FireStyle,
    val tent: Boolean,
    val church: Boolean,
    val homes: List<Home>,
    val linden: Float,
    val well: Boolean,
    val fountain: Int,
    val fountainSteps: Int,
    val clock: Int,
    val clockSteps: Int,
    val stalls: Boolean,
    val lanterns: List<FloatArray>,
    val trees: List<FloatArray>,
    val crowd: List<CrowdSpot>,
) {
    /** The ground is the village's own (not the camp's clearing). */
    val square: Boolean get() = age >= FireAge.VILLAGE

    companion object {
        // the lots round the square: the church's along the right-back edge (B), the homes' beside it (C1, C2: not while the
        // tent stands there), the home at the left corner (D); without a church its lot holds two homes (B1, B2)
        val B = Lot(3.35f, 0.35f, 5.75f, 1.85f, false)
        val B1 = Lot(3.3f, 0.35f, 5.15f, 1.95f, false)
        val B2 = Lot(5.4f, 0.35f, 7.2f, 1.95f, false)
        val C1 = Lot(7.45f, 0.35f, 9.4f, 1.95f, false)
        val C2 = Lot(9.7f, 0.35f, 11.7f, 1.95f, false)
        val D = Lot(0.25f, 9.95f, 1.65f, 11.75f, true)

        /** The church's tower beside its nave (lot [B]). */
        const val TOWER_X0 = 5.75f; const val TOWER_X1 = 6.95f; const val TOWER_Y0 = 0.4f; const val TOWER_Y1 = 1.6f

        /**
         * The linden in the square's back corner; the well at the square's front right, where the market town's fountain
         * takes its place once its project has begun.
         */
        const val LINDEN_X = 2.25f; const val LINDEN_Y = 2.05f
        const val WELL_X = 11.3f; const val WELL_Y = 10.3f
        const val FOUNT_X = WELL_X; const val FOUNT_Y = WELL_Y

        /** The market's stalls before the homes on the right-back edge: x, the counter's front y. */
        val STALLS = floatArrayOf(7.75f, 9.85f)
        const val STALL_W = 1.3f; const val STALL_Y0 = 2.4f; const val STALL_Y1 = 3.1f

        /** The town projects the square shows: the clock in the church's tower, the fountain. */
        const val CLOCK = "mestna_ura"
        const val FOUNTAIN = "vodnjak_na_trgu"

        /** The spruces round the camp: x, y, height (px), seed; back to front. */
        val SPRUCES: List<FloatArray> = listOf(
            floatArrayOf(0.4f, 0.45f, 24f, 3f), floatArrayOf(0.35f, 2.0f, 27f, 8f), floatArrayOf(1.8f, 0.35f, 28f, 5f),
            floatArrayOf(0.4f, 3.8f, 26f, 12f), floatArrayOf(3.6f, 0.4f, 30f, 7f), floatArrayOf(5.4f, 0.35f, 27f, 10f),
            floatArrayOf(0.35f, 5.9f, 24f, 15f), floatArrayOf(7.2f, 0.3f, 25f, 4f), floatArrayOf(0.4f, 7.9f, 22f, 9f),
            floatArrayOf(9.9f, 0.3f, 22f, 14f), floatArrayOf(0.5f, 10.1f, 24f, 11f),
        ).sortedBy { it[0] + it[1] }

        /**
         * The crowd's spots by the age, in the order they fill ([FireCrowd.count] of them): on the benches beside the stage's
         * seats first, then standing round the fire beside it (never in front of it or just behind it), before the homes on
         * the right where the tent doesn't stand there.
         */
        fun crowd(age: FireAge, tent: Boolean): List<CrowdSpot> {
            val left2 = CrowdSpot(6.46f, 9.95f, true, false)
            val right2 = CrowdSpot(8.8f, 8.07f, true, true)
            val leftFront = CrowdSpot(3.55f, 8.55f, false, false)
            val right3 = CrowdSpot(11.69f, 6.48f, true, true)
            val backRight = CrowdSpot(8.45f, 3.75f, false, true)
            val farRight = CrowdSpot(10.3f, 4.1f, false, true)
            val homes = if (tent) emptyList() else listOf(backRight, farRight)
            return when (age) {
                FireAge.CAMP -> listOf(left2, right2)
                FireAge.HAMLET -> listOf(left2, right2, leftFront)
                FireAge.VILLAGE -> listOf(left2, right2, leftFront, right3) + homes.take(1)
                FireAge.MARKET, FireAge.TOWN -> listOf(left2, right2, leftFront, right3) + homes
            }
        }

        /** The linden's growth from [days] since it was planted: a young tree the first year, full-grown after a few. */
        fun growth(days: Long?): Float = if (days == null) 1f else (1f - 0.62f * exp(-max(0L, days) / 420f)).coerceIn(0.38f, 1f)

        /** [world]'s centre on the day [day] (epoch day; null: the tests' and tools' day, a full-grown linden). */
        fun of(world: SceneWorld, day: Long?): FireLayout {
            val age = FireAge.of(world.age)
            val style = FireStyle.of(world.culture, world.landscape)
            val tent = si.lanisce.lani.game.scene.SceneFixtures.there("campfire", "tent", world)
            val camp = age == FireAge.CAMP
            val church = !camp && world.has(BuildingType.CHURCH)
            // the homes: as the village has them, else what its buildings say (everything built: the town's houses)
            val all = world.homes?.map { b -> Triple(b.type == BuildingType.HUT, b.level, b.id) to b.builtAt }
                ?: buildList {
                    if (world.has(BuildingType.HOUSE)) repeat(5) { add(Triple(false, world.level(BuildingType.HOUSE), "house-$it") to it.toLong()) }
                    else if (world.has(BuildingType.HUT)) repeat(3) { add(Triple(true, world.level(BuildingType.HUT), "hut-$it") to it.toLong()) }
                }
            val lots = buildList {
                if (!tent) add(C1)
                if (!church) add(B1)
                add(D)
                if (!tent) add(C2)
                if (!church) add(B2)
            }
            val shown = if (camp) emptyList() else all.take(lots.size)
            val newest = shown.maxOfOrNull { it.second }
            val homes = shown.mapIndexed { i, (h, built) -> Home(lots[i], h.first, h.second, h.third, i * 7 + 3, built == newest) }
            val linden = if (!camp && world.has(BuildingType.LIPA)) growth(world.lindenDay?.let { l -> day?.let { it - l } }) else 0f
            // the spruces the village left: all round the camp; the hamlet's where no hut, the linden or the church stands
            val trees = when {
                camp -> SPRUCES
                age == FireAge.HAMLET -> SPRUCES.filter { t -> homes.none { near(it.lot, t[0], t[1]) } && !(church && near(B, t[0], t[1])) &&
                    !(linden > 0f && hypot(t[0] - LINDEN_X, t[1] - LINDEN_Y) < 1.9f) }
                else -> emptyList()
            }
            val lanterns = when (age) {
                FireAge.CAMP, FireAge.HAMLET -> emptyList()
                FireAge.VILLAGE -> listOf(floatArrayOf(11.8f, 4.3f))
                FireAge.MARKET -> listOf(floatArrayOf(7.2f, 2.3f), floatArrayOf(11.8f, 4.3f))
                FireAge.TOWN -> listOf(floatArrayOf(7.2f, 2.3f), floatArrayOf(11.6f, 2.4f), floatArrayOf(11.8f, 4.3f))
            }
            val fountain = if (age >= FireAge.MARKET && !world.isDown(FOUNTAIN)) world.stage(FOUNTAIN) else 0
            return FireLayout(
                age, style, tent, church, homes, linden,
                well = !camp && world.has(BuildingType.WELL) && fountain == 0,
                fountain = fountain, fountainSteps = world.stages(FOUNTAIN),
                clock = if (church && !world.isDown(CLOCK)) world.stage(CLOCK) else 0, clockSteps = world.stages(CLOCK),
                stalls = age >= FireAge.MARKET && !tent && world.has(BuildingType.MARKET),
                lanterns = lanterns, trees = trees,
                crowd = if (world.age == Age.OGENJ) emptyList() else crowd(age, tent),
            )
        }

        /** Whether world ([x], [y]) is on or just by [lot] (a spruce there makes way for its building). */
        private fun near(lot: Lot, x: Float, y: Float): Boolean = x in lot.x0 - 0.7f..lot.x1 + 0.7f && y in lot.y0 - 0.7f..lot.y1 + 0.7f
    }
}

/**
 * How the centre's fire burns, before a dialog's cues and a tap (companion/SCENES.md "Ob ognju by the age"): its flames
 * burnt [low] (0 burning .. 1 only the embers glowing), [built] up (0 .. 1), [cold] (out: grey ash, no glow, no smoke), the
 * market town's [kres] of a feast evening. The fire's scene draws it ([CampfirePainter]) and its crackle follows it
 * ([sound], game/ambient/Soundscape).
 */
internal class FireBurn(val low: Float, val built: Float, val cold: Boolean, val kres: Boolean) {
    /** How loud it crackles, of the scene's fire sound: nothing out, a murmur of embers, full burning, louder built up and for the kres. */
    val sound: Float get() = when {
        cold -> 0f
        kres -> 1.3f
        else -> (1f - 0.8f * low) * (1f + 0.25f * built)
    }

    companion object {
        /**
         * The fire of [world]'s centre at [hour] of [month] ([dark]: the sky's, [si.lanisce.lani.game.render.Env.dark]),
         * [tended] when someone sits by it. The campfire (to the hamlet) burns all day and never quite goes out: low in the
         * morning (embers, ash, a thin smoke), let die down to its embers in a summer's noon unless someone sits by it, built
         * up for the evening, burning down in the night. From the village it burns for the evening's gathering (17:30 to
         * 23:30), the lunch by the fire and whenever someone sits by it, higher for the age's party and a feast; else its
         * coals glow under a thread of smoke, and in a summer's noon it is out; out too when the village had no wood for it
         * today. On a feast evening (and the age's party's) the market town burns its kres.
         */
        fun of(world: SceneWorld, hour: Float, month: Int, dark: Float, tended: Boolean): FireBurn {
            val age = FireAge.of(world.age)
            val ce = world.centre
            val evening = hour >= si.lanisce.lani.game.render.CentreLook.EVENING && hour < si.lanisce.lani.game.render.CentreLook.LATE
            val summerNoon = month in 6..8 && hour >= 11f && hour < 15.5f
            if (age <= FireAge.HAMLET) return when {
                evening -> FireBurn(0f, 0.3f, false, false)
                hour < 5f || hour >= si.lanisce.lani.game.render.CentreLook.LATE -> FireBurn(0.55f, 0f, false, false)
                hour < 9f -> FireBurn(0.7f, 0f, false, false)
                summerNoon && !tended -> FireBurn(0.93f, 0f, false, false)
                else -> FireBurn(0f, 0f, false, false)
            }
            if (ce.out) return FireBurn(1f, 0f, true, false)
            val dusk = evening || dark > 0.3f && hour >= 12f
            val gathering = (ce.party || ce.feast) && dusk
            return when {
                gathering && age >= FireAge.MARKET -> FireBurn(0f, 1f, false, true)
                gathering -> FireBurn(0f, 0.6f, false, false)
                evening -> FireBurn(0f, 0.3f, false, false)
                (hour >= 12.5f && hour < 13.2f) || tended -> FireBurn(0.45f, 0f, false, false)
                summerNoon -> FireBurn(1f, 0f, true, false)
                else -> FireBurn(0.97f, 0f, false, false)
            }
        }

        /** Whether someone of [people] sits by the fire (on its seats, the crowd's spots round it), awake: they tend it. */
        fun tended(people: List<si.lanisce.lani.game.scene.PersonInScene>): Boolean = people.any {
            it.pose != si.lanisce.lani.game.scene.Pose.SLEEP && (it.slot == "left" || it.slot == "right" || FireCrowd.isCrowd(it.slot))
        }
    }
}

/**
 * The village round the fire (companion/SCENES.md "Ob ognju by the age"): what [CampfirePainter] draws of the centre as the
 * village grows, its homes, church, linden, well, fountain, stalls and lanterns, the square's ground and the decorations of
 * its feasts, each finer closer up. What stands still at the back is drawn into the kept still picture
 * ([ArtPainter.still]); the lanterns, the decorations, the chimneys' smoke and the windows' light are the frame's.
 */
internal abstract class FireVillagePainter : DioramaPainter() {
    private var layoutFor: SceneWorld? = null
    private var layoutDay: Long? = null
    private var layoutKept: FireLayout? = null

    /** The centre of this frame's world on its day (kept while they stay the same). */
    protected val layout: FireLayout get() {
        val day = frame.date?.toEpochDay()
        val kept = layoutKept
        if (kept != null && layoutDay == day && (layoutFor === frame.world || layoutFor == frame.world)) return kept
        return FireLayout.of(frame.world, day).also { layoutKept = it; layoutFor = frame.world; layoutDay = day }
    }

    protected val style: FireStyle get() = layout.style

    /** The windows are lit (the evening and the night). */
    protected val windowsLit: Boolean get() = env.windows > 0.35f

    // ------------------------------------------------------------------ planes

    /** The wall y = [y] over x [x0]..[x1], z [z0]..[z1]: each pixel by [shade] (x, z, px, py); 0 leaves it. */
    protected inline fun faceY(y: Float, x0: Float, x1: Float, z0: Float, z1: Float, crossinline shade: (Float, Float, Int, Int) -> Int) {
        c.polyBegin()
        c.polyAdd(iso.sx(x0, y), iso.sy(x0, y, z0)); c.polyAdd(iso.sx(x1, y), iso.sy(x1, y, z0))
        c.polyAdd(iso.sx(x1, y), iso.sy(x1, y, z1)); c.polyAdd(iso.sx(x0, y), iso.sy(x0, y, z1))
        c.polyScan { px, py -> plane(px, py, shade(wallYx(px, y), wallYz(px, py, y), px, py)) }
    }

    /** The wall x = [x] over y [y0]..[y1], z [z0]..[z1]: each pixel by [shade] (y, z, px, py); 0 leaves it. */
    protected inline fun faceX(x: Float, y0: Float, y1: Float, z0: Float, z1: Float, crossinline shade: (Float, Float, Int, Int) -> Int) {
        c.polyBegin()
        c.polyAdd(iso.sx(x, y0), iso.sy(x, y0, z0)); c.polyAdd(iso.sx(x, y1), iso.sy(x, y1, z0))
        c.polyAdd(iso.sx(x, y1), iso.sy(x, y1, z1)); c.polyAdd(iso.sx(x, y0), iso.sy(x, y0, z1))
        c.polyScan { px, py -> plane(px, py, shade(wallXy(px, x), wallXz(px, py, x), px, py)) }
    }

    /** The gable triangle in the wall y = [y] over x [x0]..[x1] from [z0] up to its peak at [zp] over the middle. */
    protected inline fun gableY(y: Float, x0: Float, x1: Float, z0: Float, zp: Float, crossinline shade: (Float, Float, Int, Int) -> Int) {
        val xm = (x0 + x1) / 2f
        c.polyBegin()
        c.polyAdd(iso.sx(x0, y), iso.sy(x0, y, z0)); c.polyAdd(iso.sx(x1, y), iso.sy(x1, y, z0)); c.polyAdd(iso.sx(xm, y), iso.sy(xm, y, zp))
        c.polyScan { px, py -> plane(px, py, shade(wallYx(px, y), wallYz(px, py, y), px, py)) }
    }

    /** The gable triangle in the wall x = [x] over y [y0]..[y1] from [z0] up to its peak at [zp] over the middle. */
    protected inline fun gableX(x: Float, y0: Float, y1: Float, z0: Float, zp: Float, crossinline shade: (Float, Float, Int, Int) -> Int) {
        val ym = (y0 + y1) / 2f
        c.polyBegin()
        c.polyAdd(iso.sx(x, y0), iso.sy(x, y0, z0)); c.polyAdd(iso.sx(x, y1), iso.sy(x, y1, z0)); c.polyAdd(iso.sx(x, ym), iso.sy(x, ym, zp))
        c.polyScan { px, py -> plane(px, py, shade(wallXy(px, x), wallXz(px, py, x), px, py)) }
    }

    /** A plane's pixel ([px], [py]) in [col] (0: left as it is; [glowing]: drawn glowing, the light pass leaves it). */
    @PublishedApi internal fun plane(px: Int, py: Int, col: Int) {
        if (col == 0) return
        if (col ushr 24 == GLOW) {
            c.penEmissive = true
            c.set(px, py, col or OPAQUE)
            c.penEmissive = false
        } else c.set(px, py, col)
    }

    /** [col] marked to glow where a plane draws it ([plane]): a lit window, the clock's lit face. */
    protected fun glowing(col: Int): Int = (col and 0x00FFFFFF) or (GLOW shl 24)

    /**
     * A roof's slope whose ridge runs along y at x [xr] (height [zr]) and whose eave runs along y at x [xe] (height [ze]),
     * over y [y0]..[y1]: each pixel by [shade] (along: 0..1 from y0, down: 0 at the ridge .. 1 at the eave, px, py).
     */
    protected inline fun slopeAlongY(xr: Float, zr: Float, xe: Float, ze: Float, y0: Float, y1: Float, crossinline shade: (Float, Float, Int, Int) -> Int) {
        val g = (ze - zr) / (xe - xr)
        c.polyBegin()
        c.polyAdd(iso.sx(xr, y0), iso.sy(xr, y0, zr)); c.polyAdd(iso.sx(xr, y1), iso.sy(xr, y1, zr))
        c.polyAdd(iso.sx(xe, y1), iso.sy(xe, y1, ze)); c.polyAdd(iso.sx(xe, y0), iso.sy(xe, y0, ze))
        c.polyScan { px, py ->
            val u = ua(px); val v = vb(py)
            // z = zr + (x - xr) g on the plane: v = 2x + 2y - z with y = x - u
            val x = (v + 2f * u + zr - xr * g) / (4f - g)
            val y = x - u
            val col = shade(((y - y0) / (y1 - y0)).coerceIn(0f, 1f), ((x - xr) / (xe - xr)).coerceIn(0f, 1f), px, py)
            if (col != 0) c.set(px, py, col)
        }
    }

    /** A roof's slope whose ridge runs along x at y [yr] (height [zr]) and whose eave along x at y [ye] ([ze]), over x [x0]..[x1]. */
    protected inline fun slopeAlongX(yr: Float, zr: Float, ye: Float, ze: Float, x0: Float, x1: Float, crossinline shade: (Float, Float, Int, Int) -> Int) {
        val g = (ze - zr) / (ye - yr)
        c.polyBegin()
        c.polyAdd(iso.sx(x0, yr), iso.sy(x0, yr, zr)); c.polyAdd(iso.sx(x1, yr), iso.sy(x1, yr, zr))
        c.polyAdd(iso.sx(x1, ye), iso.sy(x1, ye, ze)); c.polyAdd(iso.sx(x0, ye), iso.sy(x0, ye, ze))
        c.polyScan { px, py ->
            val u = ua(px); val v = vb(py)
            // z = zr + (y - yr) g on the plane: v = 2x + 2y - z with x = u + y
            val y = (v - 2f * u + zr - yr * g) / (4f - g)
            val x = u + y
            val col = shade(((x - x0) / (x1 - x0)).coerceIn(0f, 1f), ((y - yr) / (ye - yr)).coerceIn(0f, 1f), px, py)
            if (col != 0) c.set(px, py, col)
        }
    }

    /** The flat top z = [z] over x [x0]..[x1], y [y0]..[y1]: each pixel by [shade] (x, y, px, py). */
    protected inline fun topZ(z: Float, x0: Float, y0: Float, x1: Float, y1: Float, crossinline shade: (Float, Float, Int, Int) -> Int) {
        c.polyBegin()
        c.polyAdd(iso.sx(x0, y0), iso.sy(x0, y0, z)); c.polyAdd(iso.sx(x1, y0), iso.sy(x1, y0, z))
        c.polyAdd(iso.sx(x1, y1), iso.sy(x1, y1, z)); c.polyAdd(iso.sx(x0, y1), iso.sy(x0, y1, z))
        c.polyScan { px, py -> val col = shade(flatX(px, py, z), flatY(px, py, z), px, py); if (col != 0) c.set(px, py, col) }
    }

    /** A box x [x0]..[x1], y [y0]..[y1], z [z0]..[z1] in plain tones: its top, its +y face (lit) and its +x face. */
    protected fun block(x0: Float, y0: Float, x1: Float, y1: Float, z0: Float, z1: Float, top: Int, left: Int, right: Int) {
        faceX(x1, y0, y1, z0, z1) { _, _, _, _ -> right }
        faceY(y1, x0, x1, z0, z1) { _, _, _, _ -> left }
        topZ(z1, x0, y0, x1, y1) { _, _, _, _ -> top }
    }

    // ------------------------------------------------------------------ materials

    /**
     * Plaster at (u, z) of a wall in [tones] (light, middle, dark: [face] 0 the lit +y face, 1 the +x face in the shade): soft
     * blotches, the splash of the rain darker at its foot; closer up, its grain and here and there a crack.
     */
    protected fun plaster(tones: IntArray, face: Int, u: Float, z: Float, px: Int, py: Int, seed: Int): Int {
        var col = if (face == 0) tones[0] else Col.mix(tones[0], tones[1], 0.75f)
        val n = Noise.v2(u * 2.3f + seed, z * 0.35f, 71 + seed)
        if (n > 0.66f) col = Col.mix(col, tones[1], if (face == 0) 0.45f else 0.6f)
        if (z < 4.5f && Dither.at(px, py) < (4.5f - z) / 6f) col = Col.mix(col, tones[2], 0.35f)
        if (detail >= 2) {
            val g = Noise.rnd(px, py, 73)
            if (g < 0.06f) col = Col.mix(col, tones[1], 0.5f) else if (g > 0.97f) col = Col.mix(col, Col.hex(0xFFFFFF), 0.25f)
        }
        return col
    }

    /**
     * Rubble stone at (u, z) of a wall ([tones]; [face] as [plaster]): courses of stones of uneven length, mortar between
     * them, each stone a shade of its own; closer up, a lit top edge on each.
     */
    protected fun stones(tones: IntArray, face: Int, u: Float, z: Float, px: Int, py: Int, seed: Int, course: Float = 1.7f): Int {
        val row = floor(z / course).toInt()
        val fz = z / course - row
        val shift = Noise.rnd(row, seed, 81) * 0.6f
        val len = 0.32f + Noise.rnd(row, seed, 82) * 0.18f
        val su = (u + shift) / len
        val stone = floor(su).toInt()
        val fu = su - stone
        val mortar = Col.mix(tones[2], Col.hex(0xD8D2C4), 0.35f)
        // the joints a picture px wide at every detail: finer closer up
        if (fz < 1.1f / (course * K) || fu < 1.1f / (len * 4f * K)) return if (face == 0) mortar else Col.scale(mortar, 0.85f)
        val k = Noise.rnd(stone, row, seed + 83)
        var col = if (k < 0.35f) tones[0] else if (k < 0.8f) tones[1] else Col.mix(tones[1], tones[2], 0.6f)
        if (face == 1) col = Col.mix(col, tones[2], 0.4f)
        if (detail >= 2 && fz > 0.82f) col = Col.mix(col, Col.hex(0xFFFFFF), 0.12f)
        return col
    }

    /** Logs laid one on another along a wall at (u, z): each round, lit on top, a dark joint under it, the bark's knots. */
    protected fun logWall(face: Int, u: Float, z: Float, px: Int, seed: Int): Int {
        val size = 1.9f
        val row = floor(z / size).toInt()
        val f = z / size - row
        var col = when {
            f > 0.78f -> Pal.LOG_L
            f > 0.22f -> Pal.LOG_M
            else -> Pal.LOG_D
        }
        if (f < 0.9f / (size * K)) col = Col.hex(0x3E2814)
        if (Noise.v1(u * 9f + row * 3.7f, seed) > 0.72f && f in 0.3f..0.7f) col = Col.scale(col, 0.85f)
        if (detail >= 2 && Noise.rnd(px, row, seed) < 0.05f) col = Col.mix(col, Pal.LOG_L, 0.5f)
        return if (face == 1) Col.scale(col, 0.84f) else col
    }

    /** Boards standing side by side ([w] cells each) in [tones] at (u, z): the dark timber of a Carinthian upper storey. */
    protected fun boards(tones: IntArray, face: Int, u: Float, z: Float, px: Int, py: Int, w: Float = 0.22f): Int {
        val b = floor(u / w).toInt()
        val f = u / w - b
        var col = if (Noise.rnd(b, 11) < 0.5f) tones[1] else Col.mix(tones[1], tones[2], 0.4f)
        if (f < 0.12f) col = tones[2] else if (f > 0.85f && detail >= 2) col = Col.mix(col, tones[0], 0.4f)
        if (detail >= 2 && Noise.v1(z * 1.4f + b * 5f, 13) > 0.8f) col = Col.scale(col, 0.88f)
        return if (face == 1) Col.scale(col, 0.82f) else col
    }

    /**
     * A roof's covering at ([along], [down]) of its slope ([len] cells long, [fall] cells from the ridge to the eave; [front]:
     * the slope toward us, lit): its rows of tiles, coppi, shingles, slate or reed; snow on it in winter, the lowest rows
     * showing.
     */
    protected fun roofing(kind: RoofKind, tones: IntArray, along: Float, down: Float, len: Float, fall: Float, front: Boolean, px: Int, py: Int, seed: Int): Int {
        val rows = max(2f, fall * 5.2f)
        val rv = down * rows
        val row = floor(rv).toInt()
        val fr = rv - row
        val ua = along * len
        var col = when (kind) {
            RoofKind.TILES -> {
                val tile = floor(ua / 0.24f + (row % 2) * 0.5f).toInt()
                val k = Noise.rnd(tile, row, seed)
                var t = if (k < 0.3f) tones[0] else if (k < 0.85f) tones[1] else Col.mix(tones[1], tones[2], 0.5f)
                if (fr > 0.78f) t = tones[2]
                if (detail >= 2 && fr < 0.18f) t = Col.mix(t, Col.hex(0xFFFFFF), 0.12f)
                if (detail >= 2 && (ua / 0.24f + (row % 2) * 0.5f) % 1f < 0.08f) t = Col.mix(t, tones[2], 0.6f)
                t
            }
            RoofKind.COPPI -> {
                // the rounded tiles in channels down the slope: lit on one flank, shaded on the other
                val ch = ua / 0.2f
                val f = ch - floor(ch)
                var t = if (f < 0.35f) tones[0] else if (f < 0.7f) tones[1] else tones[2]
                if (fr > 0.85f) t = Col.mix(t, tones[2], 0.6f)
                if (Noise.rnd(floor(ch).toInt(), row, seed) < 0.12f) t = Col.mix(t, Col.hex(0x8A7A5A), 0.35f)
                t
            }
            RoofKind.SHINGLES -> {
                val sh = floor(ua / 0.16f + (row % 2) * 0.5f).toInt()
                var t = if (Noise.rnd(sh, row, seed) < 0.5f) tones[1] else Col.mix(tones[0], tones[1], 0.5f)
                if (fr > 0.72f) t = tones[2]
                if (detail >= 2 && (ua / 0.16f + (row % 2) * 0.5f) % 1f < 0.1f) t = tones[2]
                t
            }
            RoofKind.SLATE -> {
                val sl = floor(ua / 0.3f + (row % 2) * 0.5f).toInt()
                var t = if (Noise.rnd(sl, row, seed) < 0.4f) tones[0] else tones[1]
                if (fr > 0.82f) t = tones[2]
                if (detail >= 2 && (ua / 0.3f + (row % 2) * 0.5f) % 1f < 0.06f) t = tones[2]
                t
            }
            RoofKind.THATCH -> {
                val reed = Noise.v1(ua * 14f + Noise.v1(down * 3f, seed) * 2f, seed)
                var t = if (reed > 0.62f) Col.hex(0xD8C07A) else if (reed > 0.3f) Col.hex(0xB89A58) else Col.hex(0x8E7440)
                if (down > 0.9f) t = Col.hex(0x6E5A34)
                t
            }
        }
        if (!front) col = Col.mix(col, tones[2], 0.35f)
        if (winter) {
            val cover = 0.92f - down + (Noise.v2(along * 6f + seed, down * 3f, 77) - 0.5f) * 0.3f
            if (cover > 0f) col = if (front || cover > 0.2f) Pal.SNOW_L else Pal.SNOW_M
        }
        return col
    }

    /** Dark glass in a window, lit warm at night; [u] and [v] 0..1 across it and up it. */
    protected fun pane(u: Float, v: Float, lit: Boolean, px: Int, py: Int): Int {
        if (lit) {
            val d = hypot(u - 0.5f, v - 0.4f) + (Dither.at(px, py) - 0.5f) * 0.2f
            return glowing(if (d < 0.3f) Pal.WINDOW_LIT_HI else Pal.WINDOW_LIT)
        }
        return if (u + v * 0.6f < 0.45f) Pal.GLASS_HI else Pal.GLASS
    }

    // ------------------------------------------------------------------ the square's ground

    /**
     * The ground of the village's centre at world ([x], [y]) over [base] (the grass the plot has there): the hamlet's
     * trodden earth round the fire and on the ways to the huts, the village's packed gravel with grass along its edges, the
     * market town's cobbles, the town's paving of cut stone in rows with a ring round the fire; [fireR] how far round the
     * fire the hearth reaches.
     */
    protected fun squareGround(x: Float, y: Float, px: Int, py: Int, base: Int, fireX: Float, fireY: Float): Int {
        val l = layout
        val dFire = hypot(x - fireX, y - fireY)
        val edge = min(min(x, y), min(12f - x, 12f - y)) + (Noise.v1((x - y) * 1.3f, 91) - 0.5f) * 0.5f
        val sand = style.coast
        return when (l.age) {
            FireAge.CAMP -> base
            FireAge.HAMLET -> {
                // trodden earth round the fire and out to the huts' doors, grass beyond
                val way = l.homes.minOfOrNull { h -> segDist(x, y, fireX, fireY, doorX(h.lot), doorY(h.lot)) } ?: 9f
                val near = max(1f - (dFire - 2.6f) / 1.6f, 1f - (way - 0.35f) / 0.5f)
                if (near <= 0f) base
                else {
                    val f = (near * (0.75f + Noise.v2(x * 2f, y * 2f, 3) * 0.5f)).coerceIn(0f, 1f)
                    if (sand) sandAt(px, py, f, base) else dirtAt(px, py, f, base)
                }
            }
            FireAge.VILLAGE -> {
                if (edge < 0.55f) return base
                // packed gravel, a little earth, pebbles; a ring of flat stones by the fire is the hearth's
                val n = Noise.rnd(px, py, 93)
                val tone = Noise.v2(x * 1.6f, y * 1.6f, 95)
                val g = if (sand) Col.mix(Col.hex(0xE6D4A4), Col.hex(0xCDB884), tone) else Col.mix(Col.hex(0xC4B08E), Col.hex(0xA8946E), tone)
                val col = when {
                    n < 0.05f -> Pal.STONE_M
                    n < 0.08f -> Pal.STONE_L
                    n > 0.96f -> Col.scale(g, 0.8f)
                    else -> g
                }
                if (edge < 0.85f && Dither.at(px, py) < (0.85f - edge) * 3.3f) base else if (winter) snowOver(col, x, y, px, py) else col
            }
            FireAge.MARKET -> {
                if (edge < 0.4f) return base
                // a round of earth and grass about the linden's foot
                if (l.linden > 0f && hypot(x - FireLayout.LINDEN_X, y - FireLayout.LINDEN_Y) < 0.75f + 0.35f * l.linden) return if (winter) Pal.SNOW_M else base
                val col = cobble(x, y, px, py)
                if (winter) snowOver(col, x, y, px, py) else col
            }
            FireAge.TOWN -> {
                if (edge < 0.3f) return Pal.STONE_M
                if (l.linden > 0f && hypot(x - FireLayout.LINDEN_X, y - FireLayout.LINDEN_Y) < 0.75f + 0.35f * l.linden) return if (winter) Pal.SNOW_M else base
                val col = if (dFire < 2.3f) ringPaving(x - fireX, y - fireY, dFire, px, py) else paving(x, y, px, py)
                if (winter) snowOver(col, x, y, px, py) else col
            }
        }
    }

    /** Sand over [base], [f] 0 (none) to 1: the coast's square. */
    private fun sandAt(px: Int, py: Int, f: Float, base: Int): Int {
        val n = Noise.rnd(px, py, 39)
        val s = if (winter) Pal.SNOW_M else if (n < 0.2f) Col.hex(0xD8C08C) else if (n < 0.9f) Col.hex(0xE8D4A2) else Col.hex(0xF4E6C0)
        return if (f + (Dither.at(px, py) - 0.5f) * 0.6f > 0.5f) s else base
    }

    /** Snow lying on the square, swept from round the fire, the stones showing through here and there. */
    private fun snowOver(col: Int, x: Float, y: Float, px: Int, py: Int): Int {
        val swept = Noise.v2(x * 1.4f, y * 1.4f, 97)
        return if (swept > 0.42f || Dither.at(px, py) < 0.25f) Pal.SNOW_L else Col.mix(col, Pal.SNOW_M, 0.5f)
    }

    /** A cobble of the market town's square at world ([x], [y]): rounded stones in rows, dark joints between them. */
    protected fun cobble(x: Float, y: Float, px: Int, py: Int): Int {
        val u = x * 2.6f; val v = y * 2.6f + if (floor(x * 2.6f).toInt() % 2 == 0) 0.5f else 0f
        val fu = u - floor(u); val fv = v - floor(v)
        if (fu < 0.12f || fv < 0.12f) return Pal.COBBLE_D
        val n = Noise.rnd(floor(u).toInt(), floor(v).toInt(), 63)
        val base = if (n < 0.33f) Pal.COBBLE_L else if (n < 0.8f) Pal.COBBLE_M else Col.mix(Pal.COBBLE_M, Pal.DIRT_M, 0.4f)
        return if (fu + fv < 0.55f && Noise.rnd(px, py, 64) < 0.5f) Col.mix(base, Col.hex(0xFFFFFF), 0.12f) else base
    }

    /** The town's paving: long slabs of cut stone in rows, each a shade of its own, a lit edge closer up. */
    private fun paving(x: Float, y: Float, px: Int, py: Int): Int {
        val row = floor(y / 0.8f).toInt()
        val u = x / 1.15f + (row % 3) * 0.37f
        val fu = u - floor(u); val fv = y / 0.8f - row
        if (fu < 0.05f || fv < 0.06f) return Col.hex(0x7E786C)
        val n = Noise.rnd(floor(u).toInt(), row, 66)
        var col = if (n < 0.3f) Col.hex(0xCFC8B8) else if (n < 0.8f) Col.hex(0xBDB6A4) else Col.hex(0xAEA592)
        if (detail >= 2 && fv < 0.14f) col = Col.mix(col, Col.hex(0xFFFFFF), 0.15f)
        if (Noise.rnd(px, py, 67) < 0.03f) col = Col.scale(col, 0.9f)
        return col
    }

    /** The paving's ring round the fire basket: wedges of stone in two rounds about it. */
    private fun ringPaving(dx: Float, dy: Float, d: Float, px: Int, py: Int): Int {
        val ring = if (d < 1.55f) 0 else 1
        val a = kotlin.math.atan2(dy, dx)
        val n = if (ring == 0) 14 else 20
        val w = (a / (2f * PI.toFloat()) + 1f) * n
        val f = w - floor(w)
        if (f < 0.07f || abs(d - 1.55f) < 0.05f || d > 2.25f) return Col.hex(0x7E786C)
        val k = Noise.rnd(floor(w).toInt(), ring, 68)
        return if (k < 0.5f) Col.hex(0xC9C1AE) else Col.hex(0xB4AC98)
    }

    protected fun segDist(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val u = (((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return hypot(x - ax - dx * u, y - ay - dy * u)
    }

    /** Where a lot's door is, on its gable to the square. */
    protected fun doorX(lot: Lot): Float = if (lot.gableX) lot.x1 + 0.2f else (lot.x0 + lot.x1) / 2f
    protected fun doorY(lot: Lot): Float = if (lot.gableX) (lot.y0 + lot.y1) / 2f else lot.y1 + 0.2f

    /** Nothing grows on the square's ground: the tufts keep to the grass of the camp and the hamlet, and the verges. */
    protected fun grassy(x: Float, y: Float): Boolean {
        val l = layout
        val edge = min(min(x, y), min(12f - x, 12f - y))
        return when (l.age) {
            FireAge.CAMP -> true
            FireAge.HAMLET -> l.homes.none { h -> x in h.lot.x0 - 0.3f..h.lot.x1 + 0.3f && y in h.lot.y0 - 0.3f..h.lot.y1 + 0.5f }
            FireAge.VILLAGE -> edge < 0.6f
            else -> false
        }
    }

    // ------------------------------------------------------------------ what stands round the square

    /** The homes, the church and the linden at the back of the square, back to front (the still picture's). */
    protected fun villageBack() {
        val l = layout
        if (l.age == FireAge.CAMP) return
        val lit = windowsLit
        // along the right-back edge from its far end: the church's lot (or its two homes), then the homes beside it
        for (h in l.homes.filter { !it.lot.gableX && it.lot.x0 < FireLayout.C1.x0 }) prop { home(h, lit) }
        if (l.church) prop { church(lit) }
        for (h in l.homes.filter { !it.lot.gableX && it.lot.x0 >= FireLayout.C1.x0 }.sortedBy { it.lot.x0 }) prop { home(h, lit) }
        if (l.age >= FireAge.VILLAGE) prop(outline = 0) { gardenWall() }
        if (l.linden > 0f) prop(Pal.OUTLINE_TREE) { linden(l.linden) }
        for (h in l.homes.filter { it.lot.gableX }) prop { home(h, lit) }
    }

    /**
     * A home on its lot, its gable to the square: a hut of logs (stone on the coast and in the Lake District, the lagoon's
     * reed thatch on Friuli's coast) under shingles, or a house on its stone socle, its door and windows with their shutters,
     * a balcony across the gable (geraniums on it in summer) or a timbered upper storey, under the culture's roof; its
     * windows lit in the evening (not while its hearth is out), snow on its roof in winter.
     */
    private fun home(h: Home, lit: Boolean) {
        val lot = h.lot
        val warm = lit && h.id !in frame.world.centre.cold
        if (h.hut) hut(lot, warm, h.seed) else house(lot, h.level, warm, h.seed)
    }

    /** The u, z rectangle of an opening on a wall, and what it is. */
    private class Hole(val u0: Float, val u1: Float, val z0: Float, val z1: Float, val door: Boolean)

    /** The colour of an opening's pixel at ([u], [z]) (its frame, its shutters, the door's boards, the glass), or 0 outside every opening. */
    private fun opening(holes: List<Hole>, u: Float, z: Float, lit: Boolean, px: Int, py: Int, trim: Int, shutters: Boolean): Int {
        val fw = 0.05f
        for (o in holes) {
            val sw = if (shutters && !o.door) (o.u1 - o.u0) * 0.5f else 0f
            if (u < o.u0 - sw || u > o.u1 + sw || z < o.z0 - 0.5f || z > o.z1 + 0.5f) continue
            // the shutters folded back beside the window: slats of the culture's colour
            if (u < o.u0 || u > o.u1) {
                if (z < o.z0 || z > o.z1) return 0
                val slat = ((z - o.z0) * 1.6f).toInt() % 2 == 0
                return if (detail >= 2 && slat) Col.scale(style.shutter, 0.82f) else style.shutter
            }
            // the sill and the lintel
            if (z < o.z0 || z > o.z1) return if (o.door && z < o.z0) Pal.STONE_M else trim
            if (u - o.u0 < fw || o.u1 - u < fw || z - o.z0 < 0.45f && !o.door || o.z1 - z < 0.45f) return trim
            val fu = (u - o.u0) / (o.u1 - o.u0); val fz = (z - o.z0) / (o.z1 - o.z0)
            if (o.door) {
                // planks, a knob, a small window high in it lit at night
                if (fz > 0.72f && abs(fu - 0.5f) < 0.24f) return pane((fu - 0.26f) / 0.48f, (fz - 0.72f) / 0.2f, lit, px, py)
                val plank = floor(fu * 4f).toInt()
                var col = if (plank % 2 == 0) Pal.DOOR else Col.mix(Pal.DOOR, Pal.WOOD_D, 0.4f)
                if (detail >= 2 && (fu * 4f) % 1f < 0.1f) col = Col.scale(Pal.DOOR, 0.75f)
                if (abs(fu - 0.8f) < 0.06f && abs(fz - 0.45f) < 0.05f) col = Pal.GOLD_L
                return col
            }
            // the window's cross
            if (abs(fu - 0.5f) < 0.07f || abs(fz - 0.55f) < 0.06f) return trim
            return pane(fu, fz, lit, px, py)
        }
        return 0
    }

    /**
     * A house on [lot], level [level] (from 2 a balcony or a painted band, flowers in the windows in summer): walls of
     * plaster or stone on a stone socle to the eave [EAVE], its gable to the square with the door and the windows, the
     * roof's ridge [RIDGE] higher; a chimney on the roof.
     */
    private fun house(lot: Lot, level: Int, lit: Boolean, seed: Int) {
        val st = style
        val tones = st.walls[Math.floorMod(seed, st.walls.size)]
        val eave = EAVE; val ridge = eave + RIDGE
        val over = 0.22f
        val frameC = if (st.stone) Col.hex(0xEDEBE2) else Col.mix(tones[0], Col.hex(0xFFFFFF), 0.4f)
        fun wall(face: Int, u: Float, z: Float, px: Int, py: Int): Int = when {
            z < SOCLE -> stones(intArrayOf(Pal.STONE_L, Pal.STONE_M, Pal.STONE_D), face, u, z, px, py, seed, 1.1f)
            st.stone -> stones(tones, face, u, z, px, py, seed)
            st.timber && z > eave * 0.55f -> boards(intArrayOf(Col.hex(0x8A5E3A), Col.hex(0x6A4426), Col.hex(0x4A2E1A)), face, u, z, px, py)
            else -> {
                var col = plaster(tones, face, u, z, px, py, seed)
                // a painted band under the eave from the second level
                if (level >= 2 && !st.stone && abs(z - (eave - 1.3f)) < 0.45f) col = Col.mix(col, if (st.id == "friuli") Col.hex(0xB86A4A) else Col.hex(0x6A8A9A), 0.55f)
                col
            }
        }
        if (lot.gableX) {
            // the gable toward +x: its ridge along x at the lot's middle
            val ym = (lot.y0 + lot.y1) / 2f; val x0 = lot.x0; val x1 = lot.x1; val y0 = lot.y0; val y1 = lot.y1
            val len = x1 - x0 + over * 2f; val fall = (y1 - y0) / 2f + over
            slopeAlongX(ym, ridge, y0 - over, eave - 1.2f, x0 - over * 0.5f, x1 + over) { a, d, px, py -> roofing(st.roofKind, st.roof, a, d, len, fall, false, px, py, seed) }
            val side = listOf(Hole(0.35f, 0.75f, 6f, 11.5f, false))
            faceY(y1, x0, x1, 0f, eave) { x, z, px, py -> opening(side, x - x0, z, lit, px, py, frameC, true).takeIf { it != 0 } ?: wall(0, x - x0, z, px, py) }
            val holes = gableHoles(y1 - y0, level)
            faceX(x1, y0, y1, 0f, eave) { y, z, px, py -> opening(holes, y - y0, z, lit, px, py, frameC, true).takeIf { it != 0 } ?: wall(1, y - y0, z, px, py) }
            gableX(x1, y0, y1, eave, ridge) { y, z, px, py -> gableWall(tones, 1, y - y0, z, y1 - y0, eave, ridge, lit, px, py, seed) }
            slopeAlongX(ym, ridge, y1 + over, eave - 1.2f, x0 - over * 0.5f, x1 + over) { a, d, px, py -> roofing(st.roofKind, st.roof, a, d, len, fall, true, px, py, seed) }
            verge(x1 + over, y0 - over, ym, y1 + over, eave - 1.2f, ridge, true)
            if (st.balcony && level >= 2) balconyX(x1, y0 + 0.1f, y1 - 0.1f, eave - 6.5f)
            chimney(x0 + 0.45f, ym - 0.35f, ridge - 2f)
        } else {
            // the gable toward +y: its ridge along y at the lot's middle
            val xm = (lot.x0 + lot.x1) / 2f; val x0 = lot.x0; val x1 = lot.x1; val y0 = lot.y0; val y1 = lot.y1
            val len = y1 - y0 + over * 2f; val fall = (x1 - x0) / 2f + over
            slopeAlongY(xm, ridge, x0 - over, eave - 1.2f, y0 - over * 0.5f, y1 + over) { a, d, px, py -> roofing(st.roofKind, st.roof, a, d, len, fall, false, px, py, seed) }
            val side = listOf(Hole(0.4f, 0.8f, 6f, 11.5f, false))
            faceX(x1, y0, y1, 0f, eave) { y, z, px, py -> opening(side, y - y0, z, lit, px, py, frameC, true).takeIf { it != 0 } ?: wall(1, y - y0, z, px, py) }
            val holes = gableHoles(x1 - x0, level)
            faceY(y1, x0, x1, 0f, eave) { x, z, px, py -> opening(holes, x - x0, z, lit, px, py, frameC, true).takeIf { it != 0 } ?: wall(0, x - x0, z, px, py) }
            gableY(y1, x0, x1, eave, ridge) { x, z, px, py -> gableWall(tones, 0, x - x0, z, x1 - x0, eave, ridge, lit, px, py, seed) }
            slopeAlongY(xm, ridge, x1 + over, eave - 1.2f, y0 - over * 0.5f, y1 + over) { a, d, px, py -> roofing(st.roofKind, st.roof, a, d, len, fall, true, px, py, seed) }
            verge(x0 - over, y1 + over, xm, x1 + over, eave - 1.2f, ridge, false)
            if (st.balcony && level >= 2) balconyY(y1, x0 + 0.1f, x1 - 0.1f, eave - 6.5f)
            chimney(xm + 0.35f, y0 + 0.45f, ridge - 2f)
        }
    }

    /** The gable's openings across a wall [w] cells wide: the door and a window below, two windows above (one from the second level). */
    private fun gableHoles(w: Float, level: Int): List<Hole> {
        val m = w / 2f
        return buildList {
            add(Hole(m - 0.62f, m - 0.12f, 0.6f, 10f, true))
            add(Hole(m + 0.22f, m + 0.62f, 4.5f, 9f, false))
            add(Hole(m - 0.6f, m - 0.2f, 12.5f, 16.5f, false))
            if (level >= 2) add(Hole(m + 0.2f, m + 0.6f, 12.5f, 16.5f, false))
        }
    }

    /** The gable's triangle over the eave ([u] across its [w] cells), a small window in its middle, lit at night. */
    private fun gableWall(tones: IntArray, face: Int, u: Float, z: Float, w: Float, eave: Float, ridge: Float, lit: Boolean, px: Int, py: Int, seed: Int): Int {
        val m = w / 2f
        if (abs(u - m) < 0.16f && z in eave + 1.6f..eave + 4.4f) {
            if (abs(u - m) > 0.12f || z < eave + 1.9f || z > eave + 4.1f) return Col.hex(0xEDEBE2)
            return pane((u - m + 0.12f) / 0.24f, (z - eave - 1.9f) / 2.2f, lit, px, py)
        }
        return when {
            style.stone -> stones(tones, face, u, z, px, py, seed)
            style.timber -> boards(intArrayOf(Col.hex(0x8A5E3A), Col.hex(0x6A4426), Col.hex(0x4A2E1A)), face, u, z, px, py)
            else -> plaster(tones, face, u, z, px, py, seed + 1)
        }
    }

    /** The bargeboards along a gable's two roof edges: from the eaves at ([ea]…) up to the ridge, a dark board. */
    private fun verge(xa: Float, ya: Float, mid: Float, end: Float, eave: Float, ridge: Float, alongX: Boolean) {
        val q = detail
        val dark = Col.mix(style.roof[2], Pal.WOOD_X, 0.5f)
        if (alongX) {
            // the gable at x = [xa]: from (ya, eave) up to (mid, ridge) and down to (end, eave)
            for (o in 0 until q) {
                c.line(iso.ix(xa, ya), iso.iy(xa, ya, eave) + o, iso.ix(xa, mid), iso.iy(xa, mid, ridge) + o, dark)
                c.line(iso.ix(xa, mid), iso.iy(xa, mid, ridge) + o, iso.ix(xa, end), iso.iy(xa, end, eave) + o, dark)
            }
        } else {
            for (o in 0 until q) {
                c.line(iso.ix(xa, ya), iso.iy(xa, ya, eave) + o, iso.ix(mid, ya), iso.iy(mid, ya, ridge) + o, dark)
                c.line(iso.ix(mid, ya), iso.iy(mid, ya, ridge) + o, iso.ix(end, ya), iso.iy(end, ya, eave) + o, dark)
            }
        }
    }

    /** A wooden balcony across a gable toward +y at [y], x [x0]..[x1], its floor at [z]: rails and balusters, geraniums in summer. */
    private fun balconyY(y: Float, x0: Float, x1: Float, z: Float) {
        val d = 0.32f
        block(x0, y, x1, y + d, z - 0.8f, z, Pal.WOOD_L, Pal.WOOD_D, Pal.WOOD_X)
        faceY(y + d, x0, x1, z, z + 4f) { x, zz, _, _ ->
            val b = (x - x0) / 0.12f
            when {
                zz > z + 3.3f -> Pal.WOOD_L
                b - floor(b) < 0.45f -> Pal.WOOD_M
                else -> 0
            }
        }
        if (env.month in 5..9 && !winter) flowersAlong(x0, y + d, x1, y + d, z + 4.6f)
    }

    /** A wooden balcony across a gable toward +x at [x], y [y0]..[y1], its floor at [z]. */
    private fun balconyX(x: Float, y0: Float, y1: Float, z: Float) {
        val d = 0.32f
        block(x, y0, x + d, y1, z - 0.8f, z, Pal.WOOD_L, Pal.WOOD_D, Pal.WOOD_X)
        faceX(x + d, y0, y1, z, z + 4f) { y, zz, _, _ ->
            val b = (y - y0) / 0.12f
            when {
                zz > z + 3.3f -> Pal.WOOD_M
                b - floor(b) < 0.45f -> Pal.WOOD_D
                else -> 0
            }
        }
        if (env.month in 5..9 && !winter) flowersAlong(x + d, y0, x + d, y1, z + 4.6f)
    }

    /** Geraniums along a rail from ([xa], [ya]) to ([xb], [yb]) at [z]: red heads among leaves. */
    private fun flowersAlong(xa: Float, ya: Float, xb: Float, yb: Float, z: Float) {
        val n = (hypot(xb - xa, yb - ya) / 0.13f).toInt()
        for (i in 0..n) {
            val f = i / max(1f, n.toFloat())
            val x = xa + (xb - xa) * f; val y = ya + (yb - ya) * f
            val px = iso.ix(x, y); val py = iso.iy(x, y, z)
            val col = when (i % 3) { 0 -> Pal.GERANIUM; 1 -> Pal.LEAF; else -> Pal.GERANIUM_D }
            c.fillRect(px, py - detail, detail, 2 * detail, col)
        }
    }

    /** A chimney on a roof: a short stack of plaster (stone in the Lake District) with a cap, at world ([x], [y]) from [z]. */
    private fun chimney(x: Float, y: Float, z: Float) {
        val tones = if (style.stone) style.walls[0] else intArrayOf(Pal.WALL_L, Pal.WALL_M, Pal.WALL_D)
        block(x - 0.16f, y - 0.16f, x + 0.16f, y + 0.16f, z, z + 6.5f, tones[0], tones[0], tones[1])
        block(x - 0.2f, y - 0.2f, x + 0.2f, y + 0.2f, z + 6.5f, z + 7.3f, Pal.STONE_D, Pal.STONE_M, Pal.STONE_D)
        if (winter) topZ(z + 7.3f, x - 0.2f, y - 0.2f, x + 0.2f, y + 0.2f) { _, _, _, _ -> Pal.SNOW_L }
    }

    /** Where a home's chimney smokes from (picture px): the frame's smoke rises there while its hearth burns. */
    protected fun chimneyTop(h: Home): Pair<Float, Float> {
        val lot = h.lot
        val ridge = (if (h.hut) HUT_EAVE + HUT_RIDGE else EAVE + RIDGE) - 2f
        return if (h.hut) {
            val x = (lot.x0 + lot.x1) / 2f; val y = (lot.y0 + lot.y1) / 2f
            iso.sx(x, y) to iso.sy(x, y, ridge + 4f)
        } else if (lot.gableX) {
            val x = lot.x0 + 0.45f; val y = (lot.y0 + lot.y1) / 2f - 0.35f
            iso.sx(x, y) to iso.sy(x, y, ridge + 8f)
        } else {
            val x = (lot.x0 + lot.x1) / 2f + 0.35f; val y = lot.y0 + 0.45f
            iso.sx(x, y) to iso.sy(x, y, ridge + 8f)
        }
    }

    /**
     * A hut on [lot]: walls of logs (of stone where the culture builds so) to [HUT_EAVE], a steep roof of shingles (slate, or
     * the lagoon's reed thatch reaching low), its door and a small window in the gable to the square.
     */
    private fun hut(lot: Lot, lit: Boolean, seed: Int) {
        val st = style
        val eave = if (st.thatch) HUT_EAVE - 3f else HUT_EAVE; val ridge = HUT_EAVE + HUT_RIDGE
        val over = if (st.thatch) 0.32f else 0.25f
        val kind = when {
            st.thatch -> RoofKind.THATCH
            st.roofKind == RoofKind.SLATE -> RoofKind.SLATE
            st.roofKind == RoofKind.COPPI -> RoofKind.COPPI
            else -> RoofKind.SHINGLES
        }
        val roofT = if (kind == RoofKind.SHINGLES) intArrayOf(Pal.SHINGLE_L, Pal.SHINGLE_M, Pal.SHINGLE_D) else st.roof
        val stoneT = intArrayOf(Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        fun wall(face: Int, u: Float, z: Float, px: Int, py: Int): Int =
            if (st.hutStone && !st.thatch) stones(stoneT, face, u, z, px, py, seed) else if (st.thatch) plaster(intArrayOf(Col.hex(0xE6DCC4), Col.hex(0xCFC2A4), Col.hex(0xA89A7C)), face, u, z, px, py, seed) else logWall(face, u, z, px, seed)
        val frameC = Pal.WOOD_D
        if (lot.gableX) {
            val ym = (lot.y0 + lot.y1) / 2f; val x0 = lot.x0 + 0.1f; val x1 = lot.x1 - 0.1f; val y0 = lot.y0 + 0.15f; val y1 = lot.y1 - 0.15f
            val len = x1 - x0 + over * 2f; val fall = (y1 - y0) / 2f + over
            slopeAlongX(ym, ridge, y0 - over, eave - 1.5f, x0 - over * 0.5f, x1 + over) { a, d, px, py -> roofing(kind, roofT, a, d, len, fall, false, px, py, seed) }
            faceY(y1, x0, x1, 0f, eave) { x, z, px, py -> wall(0, x - x0, z, px, py) }
            val holes = listOf(Hole((y1 - y0) / 2f - 0.3f, (y1 - y0) / 2f + 0.2f, 0.4f, min(8.5f, eave - 0.6f), true))
            faceX(x1, y0, y1, 0f, eave) { y, z, px, py -> opening(holes, y - y0, z, lit, px, py, frameC, false).takeIf { it != 0 } ?: wall(1, y - y0, z, px, py) }
            gableX(x1, y0, y1, eave, ridge) { y, z, px, py -> hutGable(y - y0, z, y1 - y0, eave, lit, px, py, seed) }
            slopeAlongX(ym, ridge, y1 + over, eave - 1.5f, x0 - over * 0.5f, x1 + over) { a, d, px, py -> roofing(kind, roofT, a, d, len, fall, true, px, py, seed) }
            verge(x1 + over, y0 - over, ym, y1 + over, eave - 1.5f, ridge, true)
        } else {
            val xm = (lot.x0 + lot.x1) / 2f; val x0 = lot.x0 + 0.15f; val x1 = lot.x1 - 0.15f; val y0 = lot.y0 + 0.1f; val y1 = lot.y1 - 0.1f
            val len = y1 - y0 + over * 2f; val fall = (x1 - x0) / 2f + over
            slopeAlongY(xm, ridge, x0 - over, eave - 1.5f, y0 - over * 0.5f, y1 + over) { a, d, px, py -> roofing(kind, roofT, a, d, len, fall, false, px, py, seed) }
            faceX(x1, y0, y1, 0f, eave) { y, z, px, py -> wall(1, y - y0, z, px, py) }
            val holes = listOf(Hole((x1 - x0) / 2f - 0.3f, (x1 - x0) / 2f + 0.2f, 0.4f, min(8.5f, eave - 0.6f), true))
            faceY(y1, x0, x1, 0f, eave) { x, z, px, py -> opening(holes, x - x0, z, lit, px, py, frameC, false).takeIf { it != 0 } ?: wall(0, x - x0, z, px, py) }
            gableY(y1, x0, x1, eave, ridge) { x, z, px, py -> hutGable(x - x0, z, x1 - x0, eave, lit, px, py, seed) }
            slopeAlongY(xm, ridge, x1 + over, eave - 1.5f, y0 - over * 0.5f, y1 + over) { a, d, px, py -> roofing(kind, roofT, a, d, len, fall, true, px, py, seed) }
            verge(x0 - over, y1 + over, xm, x1 + over, eave - 1.5f, ridge, false)
        }
    }

    /** A hut's gable: boards (the thatch's reeds on the coast), a little window in it. */
    private fun hutGable(u: Float, z: Float, w: Float, eave: Float, lit: Boolean, px: Int, py: Int, seed: Int): Int {
        val m = w / 2f
        if (abs(u - m) < 0.15f && z in eave + 1.2f..eave + 3.8f) {
            if (abs(u - m) > 0.11f || z < eave + 1.5f || z > eave + 3.5f) return Pal.WOOD_D
            return pane((u - m + 0.11f) / 0.22f, (z - eave - 1.5f) / 2f, lit, px, py)
        }
        return if (style.thatch) roofing(RoofKind.THATCH, style.roof, u / w, 0.4f, w, 1f, true, px, py, seed)
        else boards(intArrayOf(Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D), 0, u, z, px, py, 0.18f)
    }

    /**
     * The church on its lot: the nave with its tall round-arched windows (lit for the evening mass, not at night), its roof,
     * and its bell tower at the right end with the door at its foot, the belfry's openings and the bell in them, the town clock
     * as far as its steps have come ([FireLayout.clock]: the round window made for it, the face lifted in, going and lit at
     * night), and the tower's top in the culture's way.
     */
    private fun church(lit: Boolean) {
        val st = style
        val lot = FireLayout.B
        val x0 = lot.x0; val x1 = FireLayout.TOWER_X0; val y0 = lot.y0; val y1 = lot.y1
        val eave = 19f; val ridge = 28.5f; val over = 0.2f
        val ym = (y0 + y1) / 2f
        val wall = st.churchWall
        val mass = lit && s.hour < 21.5f
        // the nave: its back slope, its long side to the square with two windows, its front slope
        slopeAlongX(ym, ridge, y0 - over, eave - 1f, x0 - over, x1) { a, d, px, py -> roofing(st.roofKind, st.roof, a, d, x1 - x0, (y1 - y0) / 2f + over, false, px, py, 31) }
        faceY(y1, x0, x1, 0f, eave) { x, z, px, py ->
            val u = x - x0
            val w = churchWindow(u, z, 0.45f, mass, px, py) ?: churchWindow(u, z, 1.55f, mass, px, py)
            w ?: if (z < 2.2f) stones(intArrayOf(Pal.STONE_L, Pal.STONE_M, Pal.STONE_D), 0, u, z, px, py, 32, 1.1f)
            else if (st.stone) stones(wall, 0, u, z, px, py, 33) else plaster(wall, 0, u, z, px, py, 33)
        }
        // the gable at the nave's left end shows as its edge only; the front slope
        slopeAlongX(ym, ridge, y1 + over, eave - 1f, x0 - over, x1) { a, d, px, py -> roofing(st.roofKind, st.roof, a, d, x1 - x0, (y1 - y0) / 2f + over, true, px, py, 31) }
        tower(lit)
    }

    /** A tall round-arched window of the nave at [u0] cells along it, lit warm for the evening mass; null outside it. */
    private fun churchWindow(u: Float, z: Float, u0: Float, lit: Boolean, px: Int, py: Int): Int? {
        val w = 0.42f; val zb = 7f; val zt = 15f
        val du = u - u0
        if (du < 0f || du > w || z < zb - 0.5f) return null
        val mid = u0 + w / 2f
        val arch = zt + sqrt(max(0f, 1f - ((u - mid) / (w / 2f)).let { it * it })) * 1.8f
        if (z > arch + 0.5f) return null
        if (z > arch || z < zb || du < 0.05f || du > w - 0.05f) return Pal.STONE_L
        val f = (u - u0) / w; val v = (z - zb) / (arch - zb)
        if (lit) return glowing(if (Noise.rnd(floor(f * 3f).toInt(), floor(v * 5f).toInt(), 35) < 0.3f) Col.hex(0xF0B860) else Pal.WINDOW_LIT)
        // leaded glass: small panes, a little colour
        val pane = Noise.rnd(floor(f * 3f).toInt(), floor(v * 5f).toInt(), 35)
        return if ((f * 3f) % 1f < 0.12f || (v * 5f) % 1f < 0.1f) Col.hex(0x2A2E38) else if (pane < 0.2f) Col.hex(0x6A4E7A) else if (pane < 0.35f) Col.hex(0x5A7AA0) else Pal.GLASS
    }

    /** The bell tower: its walls to [TOWER_TOP], the door, the clock, the belfry, and its top. */
    private fun tower(lit: Boolean) {
        val st = style
        val x0 = FireLayout.TOWER_X0; val x1 = FireLayout.TOWER_X1; val y0 = FireLayout.TOWER_Y0; val y1 = FireLayout.TOWER_Y1
        val top = TOWER_TOP
        val tones = st.towerWall
        val brick = st.tower == Tower.CAMPANILE
        fun wall(face: Int, u: Float, z: Float, px: Int, py: Int): Int {
            // the corners in dressed stone (closer up), the walls plastered (brick for the campanile, stone in the Lake District)
            val corner = u < 0.12f || u > (x1 - x0) - 0.12f
            if (corner && !st.stone && Math.floorMod(floor(z / 2.2f).toInt(), 2) == 0) return if (face == 0) Pal.STONE_L else Pal.STONE_M
            return when {
                brick -> {
                    val row = floor(z / 0.9f).toInt()
                    val b = floor(u / 0.2f + (row % 2) * 0.5f).toInt()
                    if (z / 0.9f - row < 0.16f) Col.mix(tones[2], Col.hex(0xD8CCBA), 0.4f)
                    else if (Noise.rnd(b, row, 37) < 0.3f) tones[0] else if (face == 0) tones[1] else Col.mix(tones[1], tones[2], 0.4f)
                }
                st.stone -> stones(tones, face, u, z, px, py, 38)
                else -> plaster(tones, face, u, z, px, py, 38)
            }
        }
        val clock = layout.clock; val steps = layout.clockSteps
        faceX(x1, y0, y1, 0f, top) { y, z, px, py -> belfry(y - y0, z, y1 - y0, top, 1, lit, px, py) ?: wall(1, y - y0, z, px, py) }
        faceY(y1, x0, x1, 0f, top) { x, z, px, py ->
            val u = x - x0; val w = x1 - x0
            belfry(u, z, w, top, 0, lit, px, py)
                ?: towerDoor(u, z, w, lit, px, py)
                ?: clockFace(u, z, w, clock, steps, px, py)
                ?: wall(0, u, z, px, py)
        }
        // a cornice under the belfry and one at the top
        block(x0 - 0.06f, y0 - 0.06f, x1 + 0.06f, y1 + 0.06f, top - 9.6f, top - 9f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        when (st.tower) {
            Tower.SPIRE -> {
                block(x0 - 0.08f, y0 - 0.08f, x1 + 0.08f, y1 + 0.08f, top, top + 0.8f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
                spire(x0 - 0.04f, y0 - 0.04f, x1 + 0.04f, y1 + 0.04f, top + 0.8f, SPIRE_H)
                cross((x0 + x1) / 2f, (y0 + y1) / 2f, top + 0.8f + SPIRE_H)
            }
            Tower.CAMPANILE -> {
                block(x0 - 0.1f, y0 - 0.1f, x1 + 0.1f, y1 + 0.1f, top, top + 1f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
                spire(x0 - 0.06f, y0 - 0.06f, x1 + 0.06f, y1 + 0.06f, top + 1f, 7f)
                cross((x0 + x1) / 2f, (y0 + y1) / 2f, top + 8f)
            }
            Tower.ONION -> {
                block(x0 - 0.06f, y0 - 0.06f, x1 + 0.06f, y1 + 0.06f, top, top + 0.8f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
                onion((x0 + x1) / 2f, (y0 + y1) / 2f, top + 0.8f)
            }
            Tower.BATTLEMENTS -> battlements(x0, y0, x1, y1, top)
        }
    }

    /** A round-arched opening of the belfry on a face of the tower (two on each), the bell in the shade inside; null outside. */
    private fun belfry(u: Float, z: Float, w: Float, top: Float, face: Int, lit: Boolean, px: Int, py: Int): Int? {
        val zb = top - 7.6f; val zt = top - 3.2f
        if (z < zb - 0.4f || z > zt + 1.4f) return null
        for (k in 0..1) {
            val u0 = w * (0.18f + 0.42f * k); val ow = w * 0.24f
            val mid = u0 + ow / 2f
            if (u < u0 - 0.05f || u > u0 + ow + 0.05f) continue
            val arch = zt + sqrt(max(0f, 1f - ((u - mid) / (ow / 2f)).let { it * it })) * 1.1f
            if (z > arch + 0.4f) return null
            if (z > arch || z < zb || u < u0 || u > u0 + ow) return Pal.STONE_L
            // the bell: a dark bronze shape in the opening, its lip catching the light
            val bu = (u - mid) / (ow / 2f); val bz = (z - zb) / (zt - zb)
            if (bz in 0.25f..0.85f && abs(bu) < 0.25f + (0.85f - bz) * 0.5f) return if (bz < 0.32f) Col.hex(0xC8A050) else if (bu < 0f && face == 0) Col.hex(0x9A7A3A) else Col.hex(0x6E5428)
            return Col.hex(0x241E1C)
        }
        return null
    }

    /** The church's door at the tower's foot: an arch of stone, two leaves of boards, a step; null outside it. */
    private fun towerDoor(u: Float, z: Float, w: Float, lit: Boolean, px: Int, py: Int): Int? {
        val m = w / 2f; val hw = 0.3f
        if (abs(u - m) > hw + 0.08f || z > 11.5f) return null
        val arch = 9f + sqrt(max(0f, 1f - ((u - m) / hw).let { it * it })) * 1.5f
        if (z > arch + 0.6f) return null
        if (z > arch || abs(u - m) > hw) return Pal.STONE_L
        val leaf = if (u < m) 0 else 1
        var col = if (leaf == 0) Pal.DOOR else Col.mix(Pal.DOOR, Pal.WOOD_D, 0.3f)
        if (abs(u - m) < 0.03f) col = Col.scale(Pal.DOOR, 0.6f)
        if (detail >= 2 && Math.floorMod(floor(z * 1.4f).toInt(), 4) == 0) col = Col.scale(col, 0.85f)
        if (lit && s.hour < 21.5f && abs(u - m) < 0.03f) col = glowing(Pal.WINDOW_LIT)
        return col
    }

    /**
     * The town clock in the tower as far as its steps have come (GAME.md "Village projects"): a slit window before it, then
     * the round window made for it, the face lifted in (stopped at twelve), then going (the hands at the frame's hour) and
     * lit at night; null outside it.
     */
    private fun clockFace(u: Float, z: Float, w: Float, step: Int, steps: Int, px: Int, py: Int): Int? {
        val m = w / 2f; val zc = TOWER_TOP - 12.5f
        val du = (u - m) * 5f; val dz = z - zc
        val r = 2.6f
        val d = hypot(du, dz)
        if (step <= 1) {
            // the slit window the tower always had
            return if (abs(du) < 0.45f && abs(dz) < 1.6f) Col.hex(0x2A2624) else null
        }
        if (d > r + 0.5f) return null
        if (d > r) return Pal.STONE_L
        if (step < 4) return Col.hex(0x2A2624) // the round window, empty
        // the face: white, its ring of marks, the hands
        val going = step >= steps
        val face = if (going && env.windows > 0.35f) glowing(Col.hex(0xFFF2C8)) else Col.hex(0xF4F0E6)
        if (d > r - 0.35f) return Col.hex(0x2E2A26)
        val ang = kotlin.math.atan2(du, dz) // 0 up, clockwise
        val h = if (going) s.hour else 12f
        val ha = (h % 12f) / 12f * 2f * PI.toFloat(); val ma = (h % 1f) * 2f * PI.toFloat()
        fun hand(a: Float, len: Float): Boolean = d < len && abs(sin(ang - a)) * d < 0.38f && cos(ang - a) > 0f
        if (hand(ha, r * 0.55f) || hand(ma, r * 0.85f)) return Col.hex(0x1E1A16)
        if (detail >= 2 && d > r - 0.9f && abs(((ang / (2f * PI.toFloat()) * 12f) + 12f) % 1f - 0.5f) > 0.42f) return Col.hex(0x5A524A)
        return face
    }

    /** A spire: four faces of shingles or tiles narrowing from the box x [x0]..[x1], y [y0]..[y1] at [z] up [h]. */
    private fun spire(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, h: Float) {
        val t = style.towerTop
        val ax = (x0 + x1) / 2f; val ay = (y0 + y1) / 2f; val az = z + h
        // the two faces we see: toward +y (lit) and toward +x
        c.polyBegin(); c.polyAdd(iso.sx(x0, y1), iso.sy(x0, y1, z)); c.polyAdd(iso.sx(x1, y1), iso.sy(x1, y1, z)); c.polyAdd(iso.sx(ax, ay), iso.sy(ax, ay, az))
        c.polyScan { px, py -> c.set(px, py, spireTone(t, 0, px, py, iso.sy(ax, ay, az), iso.sy(x0, y1, z))) }
        c.polyBegin(); c.polyAdd(iso.sx(x1, y0), iso.sy(x1, y0, z)); c.polyAdd(iso.sx(x1, y1), iso.sy(x1, y1, z)); c.polyAdd(iso.sx(ax, ay), iso.sy(ax, ay, az))
        c.polyScan { px, py -> c.set(px, py, spireTone(t, 1, px, py, iso.sy(ax, ay, az), iso.sy(x1, y1, z))) }
    }

    private fun spireTone(t: IntArray, face: Int, px: Int, py: Int, top: Float, bottom: Float): Int {
        val v = ((py - top) / max(1f, bottom - top)).coerceIn(0f, 1f)
        var col = if (face == 0) t[0] else t[1]
        if (detail >= 2 && Math.floorMod(py, 2 * detail) == 0) col = Col.mix(col, t[2], 0.5f)
        if (winter && Noise.rnd(px / max(1, detail), py / max(1, detail), 39) < 0.5f - v * 0.4f) col = Pal.SNOW_M
        return col
    }

    /** A small cross of iron (a gilded ball under it) at the top of a spire at world ([x], [y], [z]). */
    private fun cross(x: Float, y: Float, z: Float) {
        val px = iso.ix(x, y); val py = iso.iy(x, y, z)
        val q = detail
        c.fillRect(px - q / 2, py - 7 * q, q, 7 * q, Col.hex(0x3A3630))
        c.fillRect(px - 2 * q, py - 5 * q, 4 * q, q, Col.hex(0x3A3630))
        c.fillRect(px - q, py - 2 * q, 2 * q, 2 * q, Pal.GOLD)
        c.set(px - q, py - 2 * q, Pal.GOLD_L)
    }

    /** The onion dome of a Carinthian tower: a bulb of green copper over a drum, a lantern and a spike with a cross on it. */
    private fun onion(x: Float, y: Float, z: Float) {
        val t = style.towerTop
        val cx = iso.sx(x, y); val base = iso.sy(x, y, z)
        val r = 0.72f * 4f * K
        // the bulb: widest a third up, pinched to a point
        val h = 9f * K
        for (py in floor(base - h).toInt()..floor(base).toInt()) {
            val v = (base - py) / h // 0 at the base .. 1 at the tip
            val half = r * when {
                v < 0.12f -> 0.7f + v * 1.5f
                v < 0.4f -> 0.88f + sin((v - 0.12f) / 0.28f * PI.toFloat() / 2f) * 0.12f
                else -> (1f - (v - 0.4f) / 0.6f).let { it * it } * 1f + 0.06f
            }
            for (px in floor(cx - half).toInt()..floor(cx + half).toInt()) {
                val f = (px + 0.5f - cx) / half
                var col = if (f < -0.35f) t[0] else if (f < 0.4f) t[1] else t[2]
                if (detail >= 2 && Math.floorMod(px - floor(cx).toInt(), 3 * detail) == 0) col = Col.mix(col, t[2], 0.4f)
                if (winter && v > 0.25f && v < 0.5f && f < 0.3f) col = Pal.SNOW_L
                c.set(px, py, col)
            }
        }
        // the lantern and the spike
        val tip = base - h
        val q = detail
        c.fillRect(floor(cx).toInt() - q, floor(tip - 3f * K).toInt(), 2 * q, (3f * K).toInt(), t[1])
        c.fillRect(floor(cx).toInt() - q / 2 - q, floor(tip - 1.8f * K).toInt(), 3 * q, q, Pal.GOLD)
        cross(x, y, z + 9f + 3f)
    }

    /** The Lake District's square tower: a parapet with its merlons all round the top, a flagpole on it. */
    private fun battlements(x0: Float, y0: Float, x1: Float, y1: Float, top: Float) {
        val tones = style.towerWall
        block(x0 - 0.06f, y0 - 0.06f, x1 + 0.06f, y1 + 0.06f, top, top + 0.8f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        faceX(x1 + 0.06f, y0 - 0.06f, y1 + 0.06f, top + 0.8f, top + 3.6f) { y, z, _, _ ->
            val m = ((y - y0) / 0.3f).let { it - floor(it) }
            if (z > top + 2.2f && m > 0.5f) 0 else Col.mix(tones[1], tones[2], 0.4f)
        }
        faceY(y1 + 0.06f, x0 - 0.06f, x1 + 0.06f, top + 0.8f, top + 3.6f) { x, z, _, _ ->
            val m = ((x - x0) / 0.3f).let { it - floor(it) }
            if (z > top + 2.2f && m > 0.5f) 0 else tones[0]
        }
        val q = detail
        val fx = iso.ix((x0 + x1) / 2f, (y0 + y1) / 2f); val fy = iso.iy((x0 + x1) / 2f, (y0 + y1) / 2f, top + 0.8f)
        c.fillRect(fx, fy - 11 * q, q, 11 * q, Col.hex(0x5A524A))
    }

    /**
     * The linden in the square's back corner (the Lake District's oak), [g] grown (0.4 a young tree, 1 full-grown): its
     * trunk and boughs, its crown in clumps lit from the upper left, in the season's leaves (yellow in autumn, bare in
     * winter with snow along the boughs), blossom in June and July; closer up its single leaves and its furrowed bark.
     */
    private fun linden(g: Float) {
        val bx = iso.sx(FireLayout.LINDEN_X, FireLayout.LINDEN_Y); val by = iso.sy(FireLayout.LINDEN_X, FireLayout.LINDEN_Y, 0f)
        val oak = style.oak
        shade(bx + 3f * K * g, by, 11f * K * g, 3.5f * K * g, 0.8f, groundId)
        val trunkH = (6f + 6f * g) * K
        val tw = (0.45f + 1.05f * g) * K
        val q = detail
        // the trunk: lit on the left, furrows up it closer up
        for (py in floor(by - trunkH).toInt()..floor(by).toInt()) for (px in floor(bx - tw).toInt()..floor(bx + tw).toInt()) {
            val u = (px + 0.5f - bx) / tw
            if (abs(u) > 1f) continue
            var col = if (u < -0.45f) Pal.LOG_L else if (u < 0.4f) Pal.LOG_M else Pal.LOG_D
            if (q >= 2 && Noise.v1(u * 6f, 47) > 0.66f) col = Col.scale(col, 0.82f)
            // the root flare at its foot
            c.set(px, py, col)
        }
        for (side in intArrayOf(-1, 1)) for (o in 0 until q) c.line(floor(bx + side * tw).toInt(), floor(by).toInt() - o, floor(bx + side * (tw + 1.6f * K * g)).toInt(), floor(by + 0.6f * K).toInt() - o, Pal.LOG_D)
        val crownR = (5f + 8.5f * g) * K
        val cy = by - trunkH - crownR * 0.62f
        val m = env.month
        if (winter) {
            bareCrown(bx, by - trunkH * 0.85f, crownR, g)
            return
        }
        val (dk, md, lt) = when {
            oak && m == 10 -> Triple(Col.hex(0x7A4E1E), Col.hex(0xA06A28), Col.hex(0xC88E3A))
            oak && m == 11 -> Triple(Col.hex(0x6A4420), Col.hex(0x8A5A2A), Col.hex(0xAA7A3A))
            oak -> Triple(Col.hex(0x2A5226), Col.hex(0x3E6E30), Col.hex(0x5E8E3E))
            m in 3..4 -> Triple(Col.hex(0x5A9A3A), Col.hex(0x7AB84A), Col.hex(0xA8D868))
            m == 10 -> Triple(Col.hex(0xB07A1E), Col.hex(0xD8A02A), Col.hex(0xF2CC50))
            m == 11 -> Triple(Col.hex(0x8A6A2A), Col.hex(0xB08A34), Col.hex(0xD0A848))
            m == 9 -> Triple(Col.hex(0x5A7A2A), Col.hex(0x7E9A36), Col.hex(0xB8B84A))
            else -> Triple(Col.hex(0x2E6A2A), Col.hex(0x4A8A34), Col.hex(0x6EAE44))
        }
        val bloom = !oak && (m == 6 || m == 7)
        val rx = crownR; val ry = crownR * 0.86f
        val clumpS = (2.4f + 1.4f * g) * K
        for (py in max(c.top, floor(cy - ry * 1.15f).toInt())..min(c.bottom - 1, floor(cy + ry).toInt())) {
            for (px in max(c.left, floor(bx - rx * 1.1f).toInt())..min(c.right - 1, floor(bx + rx * 1.1f).toInt())) {
                val dx = (px + 0.5f - bx) / rx; val dy = (py + 0.5f - cy) / ry
                val clump = Noise.v2(px / clumpS, py / clumpS, if (oak) 54 else 50)
                val d = dx * dx + dy * dy * (if (dy < 0f) 0.9f else 1.1f) + (clump - 0.5f) * 0.55f
                if (d > 1f) continue
                if (m == 11 && !oak && Noise.rnd(px / (2 * q), py / (2 * q), 51) < 0.4f) continue
                val light = -dx * 0.6f - dy * 0.8f + (clump - 0.5f) * 1.2f - d * 0.2f
                var col = if (light > 0.35f) lt else if (light > -0.25f) md else dk
                if (q >= 2) {
                    // single leaves catching the light, the shade's leaf edges
                    val n = Noise.rnd(px / max(1, q - 1), py / max(1, q - 1), 53)
                    if (light > 0.1f && n < 0.06f) col = Col.mix(lt, Col.hex(0xFFFFFF), 0.25f)
                    else if (light < 0f && n > 0.93f) col = Col.scale(dk, 0.8f)
                }
                if (bloom && Noise.rnd(px / q, py / q, 52) < 0.08f && light > -0.3f) col = Col.hex(0xF2E8A0)
                c.set(px, py, col)
            }
        }
    }

    /** The linden bare in winter: boughs forking into twigs over the trunk, snow along them. */
    private fun bareCrown(bx: Float, by: Float, r: Float, g: Float) {
        val q = detail
        for (k in 0 until 7) {
            val ang = -PI.toFloat() / 2f + (k - 3f) * 0.32f + (Noise.rnd(k, 49) - 0.5f) * 0.15f
            val len = r * (0.75f + Noise.rnd(k, 50) * 0.35f)
            val ex = bx + cos(ang) * len; val ey = by + sin(ang) * len
            for (o in 0 until max(1, (q * (1f + g)).toInt())) c.line(floor(bx).toInt() + o, floor(by).toInt(), floor(ex).toInt() + o, floor(ey).toInt(), Pal.LOG_D)
            for (f in intArrayOf(-1, 1)) {
                val a2 = ang + f * 0.45f
                val l2 = len * 0.45f
                val fx = ex + cos(a2) * l2; val fy = ey + sin(a2) * l2
                c.line(floor(ex).toInt(), floor(ey).toInt(), floor(fx).toInt(), floor(fy).toInt(), Pal.LOG_M)
                c.line(floor(fx).toInt(), floor(fy).toInt(), floor(fx + cos(a2 + f * 0.4f) * l2 * 0.5f).toInt(), floor(fy + sin(a2 + f * 0.4f) * l2 * 0.5f).toInt(), Col.hex(0x85705F))
                if (winter) c.fillRect(floor(fx).toInt() - q, floor(fy).toInt() - q, 2 * q, q, Pal.SNOW_L)
            }
            if (winter) c.fillRect(floor(ex).toInt() - q, floor(ey).toInt() - q, 2 * q, q, Pal.SNOW_L)
        }
    }

    /**
     * The well at the square's front right: the village's a round curb of stones under a wooden frame, the winch across it
     * with its crank, the rope down to the bucket on the rim; the market town's of dressed stone under an iron arch with its
     * pulley. Ice in it in winter, snow on its rim.
     */
    protected fun well(stone: Boolean) {
        val x = FireLayout.WELL_X; val y = FireLayout.WELL_Y
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, 0f)
        val r = 0.58f * 4f * K * 1.4142f
        val q = detail
        shade(cx + 2f * K, cy, r * 1.3f, r * 0.55f, 0.8f, groundId)
        val h = 5.5f * K
        // the curb: courses of stones round the shaft, lit on the left (dressed blocks at the market town)
        for (py in max(c.top, floor(cy - h - r * 0.5f).toInt())..min(c.bottom - 1, floor(cy + r * 0.5f).toInt())) for (px in max(c.left, floor(cx - r).toInt())..min(c.right - 1, floor(cx + r).toInt())) {
            val u = (px + 0.5f - cx) / r
            if (abs(u) > 1f) continue
            val e = sqrt(1f - u * u) * r * 0.5f
            val top = cy - h + e; val bot = cy + e
            if (py < top - e * 2f || py > bot) continue
            val col = if (py < top) {
                // the rim's top, the dark water inside
                val inner = (py + 0.5f - (cy - h)) / (r * 0.5f)
                if (u * u + inner * inner < 0.62f) (if (winter) Pal.ICE_M else if (u * u + inner * inner > 0.45f) Col.hex(0x2A3A44) else Col.hex(0x1A2630))
                else if (winter) Pal.SNOW_L else if (stone) Col.hex(0xD2CCBC) else Pal.STONE_L
            } else {
                val course = if (stone) 2.2f * K else 1.4f * K
                val row = floor((bot - py) / course).toInt()
                val blocks = if (stone) 3f else 5f
                val bu = u * blocks + (row % 2) * 0.5f
                var col = if (u < -0.4f) Pal.STONE_L else if (u < 0.45f) Pal.STONE_M else Pal.STONE_D
                if (stone) col = Col.mix(col, Col.hex(0xE0DAC8), 0.3f)
                if (Noise.rnd(floor(bu).toInt(), row, 41) < 0.3f) col = Col.mix(col, Pal.STONE_L, 0.3f)
                if (((bot - py) / course) % 1f < q / course || bu - floor(bu) < 0.08f) col = Pal.STONE_X
                col
            }
            c.set(px, py, col)
        }
        if (stone) {
            // the iron arch over it, a scroll each side, the pulley at its top, the rope down into it
            val iron = Col.hex(0x3A3A40)
            val ah = 9f * K
            for (a in 0..60 * q) {
                val f = a / (60f * q) * PI.toFloat()
                val px = floor(cx - cos(f) * r * 0.8f).toInt(); val py = floor(cy - h - sin(f) * ah).toInt()
                c.fillRect(px, py, q, q, iron)
            }
            c.fillEllipse(cx, cy - h - ah + 1.4f * K, 1.2f * K, 1.2f * K, Col.hex(0x5A5A62))
            c.fillRect(floor(cx).toInt(), floor(cy - h - ah + 2.4f * K).toInt(), max(1, q / 2), floor(ah - 3f * K).toInt(), Col.hex(0xC8AC72))
            c.fillRect(floor(cx - 0.8f * K).toInt(), floor(cy - h - 2.2f * K).toInt(), floor(1.6f * K).toInt(), floor(1.4f * K).toInt(), Pal.WOOD_M)
            return
        }
        // the frame: two posts and the beam over the curb, the winch on it with its crank, the rope down to the bucket
        val postH = 7f * K
        for (side in intArrayOf(-1, 1)) {
            val px = cx + side * r * 0.8f
            c.fillRect(floor(px).toInt() - q, floor(cy - h - postH).toInt(), 2 * q, floor(postH + h * 0.6f).toInt(), if (side < 0) Pal.WOOD_L else Pal.WOOD_D)
        }
        val by = cy - h - postH
        c.fillRect(floor(cx - r * 0.8f - q).toInt(), floor(by).toInt(), floor(r * 1.6f).toInt() + 2 * q, q, Pal.WOOD_D)
        c.fillRect(floor(cx - r * 0.55f).toInt(), floor(by + q).toInt(), floor(r * 1.1f).toInt(), floor(1.6f * K).toInt(), Pal.LOG_M)
        c.fillRect(floor(cx - r * 0.55f).toInt(), floor(by + q).toInt(), floor(r * 1.1f).toInt(), q, Pal.LOG_L)
        c.fillRect(floor(cx + r * 0.8f + q).toInt(), floor(by + q).toInt(), q, floor(1.6f * K).toInt(), Col.hex(0x3A3A40))
        c.fillRect(floor(cx + r * 0.8f + q).toInt(), floor(by + 1.6f * K).toInt(), floor(1.2f * K).toInt(), q, Col.hex(0x3A3A40))
        c.fillRect(floor(cx + 0.3f * K).toInt(), floor(by + 1.6f * K + q).toInt(), max(1, q / 2), floor(postH - 3.5f * K).toInt(), Col.hex(0xC8AC72))
        val bt = by + postH - 1.5f * K
        c.fillRect(floor(cx - 0.4f * K).toInt(), floor(bt).toInt(), floor(1.6f * K).toInt(), floor(1.5f * K).toInt(), Pal.WOOD_M)
        c.fillRect(floor(cx - 0.4f * K).toInt(), floor(bt).toInt(), floor(1.6f * K).toInt(), q, Col.hex(0x5A5A62))
        if (winter) c.fillRect(floor(cx - r * 0.8f - q).toInt(), floor(by - q).toInt(), floor(r * 1.6f).toInt() + 2 * q, q, Pal.SNOW_L)
    }

    /** The garden wall along the left-back edge behind the woodpile: dry stone, low, moss and ivy on it, snow on its top. */
    private fun gardenWall() {
        val x = 0.02f
        faceX(x + 0.18f, 4.3f, 9.75f, 0f, 4.2f) { y, z, px, py ->
            var col = stones(intArrayOf(Pal.STONE_L, Pal.STONE_M, Pal.STONE_D), 1, y, z, px, py, 45, 1.3f)
            if (!winter && Noise.v2(y * 3f, z * 0.6f, 46) > 0.7f) col = if (env.season == Season.AUTUMN) Col.hex(0x9A4A2A) else Col.hex(0x3E6E30)
            col
        }
        topZ(4.2f, x, 4.3f, x + 0.18f, 9.75f) { _, _, _, _ -> if (winter) Pal.SNOW_L else Pal.STONE_L }
    }

    // ------------------------------------------------------------------ the square's furniture: the frame's

    /**
     * The fountain on the square as far as its steps have come (GAME.md "Village projects", `vodnjak_na_trgu`): pegs and a
     * string where it will stand, the block of white stone on its beams, then the round trough carved with its pillar in the
     * middle (dry), the four iron pipes fitted, and the water running from them into it; ice in winter.
     */
    protected fun fountain(step: Int, steps: Int) {
        if (step <= 0) return
        val x = FireLayout.FOUNT_X; val y = FireLayout.FOUNT_Y
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, 0f)
        val r = 0.72f * 4f * K * 1.4142f
        val q = detail
        val white = Col.hex(0xE6E1D4); val whiteM = Col.hex(0xCCC6B6); val whiteD = Col.hex(0xA8A292)
        if (step == 1) {
            // pegs and a string round where it will stand
            for (k in 0 until 6) {
                val a = k * PI.toFloat() / 3f + 0.3f
                val px = floor(cx + cos(a) * r).toInt(); val py = floor(cy + sin(a) * r * 0.5f).toInt()
                c.fillRect(px, py - 2 * K, q, 2 * K, Pal.WOOD_M)
                c.set(px, py - 2 * K, Pal.WOOD_L)
            }
            for (i in 0..48 * q) {
                val a = i / (48f * q) * 2f * PI.toFloat()
                c.set(floor(cx + cos(a) * r).toInt(), floor(cy + sin(a) * r * 0.5f - 1.6f * K).toInt(), Col.hex(0xE8E0C8))
            }
            return
        }
        shade(cx + 1.5f * K, cy, r * 1.25f, r * 0.55f, 0.82f, groundId)
        if (step == 2) {
            // the block of white stone on its beams
            c.fillRect(floor(cx - r * 0.9f).toInt(), floor(cy - 0.9f * K).toInt(), floor(r * 1.8f).toInt(), floor(0.9f * K).toInt(), Pal.LOG_M)
            c.fillRect(floor(cx - r * 0.6f).toInt(), floor(cy - 6f * K).toInt(), floor(r * 1.2f).toInt(), floor(5.1f * K).toInt(), white)
            c.fillRect(floor(cx + r * 0.15f).toInt(), floor(cy - 6f * K).toInt(), floor(r * 0.45f).toInt(), floor(5.1f * K).toInt(), whiteM)
            c.fillRect(floor(cx - r * 0.6f).toInt(), floor(cy - 6f * K).toInt(), floor(r * 1.2f).toInt(), q, Col.hex(0xF6F2E8))
            return
        }
        val h = 3.4f * K
        val water = step >= steps
        // the trough's wall: round, lit on the left, its rim, the basin inside
        for (py in max(c.top, floor(cy - h - r * 0.5f).toInt())..min(c.bottom - 1, floor(cy + r * 0.5f).toInt())) for (px in max(c.left, floor(cx - r).toInt())..min(c.right - 1, floor(cx + r).toInt())) {
            val u = (px + 0.5f - cx) / r
            if (abs(u) > 1f) continue
            val e = sqrt(1f - u * u) * r * 0.5f
            val top = cy - h + e; val bot = cy + e
            if (py < top - e * 2f || py > bot) continue
            val col = if (py < top) {
                val inner = (py + 0.5f - (cy - h)) / (r * 0.5f)
                val d = u * u + inner * inner
                when {
                    d > 0.72f -> if (winter) Pal.SNOW_L else white
                    !water -> if (d > 0.6f) whiteD else whiteM
                    winter -> Pal.ICE_M
                    d < 0.25f && Noise.rnd(px / q, py / q, 44) < 0.2f -> Pal.WATER_L
                    else -> Pal.WATER_M
                }
            } else if (u < -0.45f) white else if (u < 0.4f) whiteM else whiteD
            c.set(px, py, col)
        }
        // the pillar in the middle with its cap; the pipes from the next to last step
        val pw = 1.1f * K; val ph = 7f * K
        c.fillRect(floor(cx - pw).toInt(), floor(cy - h - ph).toInt(), floor(2 * pw).toInt(), floor(ph).toInt(), whiteM)
        c.fillRect(floor(cx - pw).toInt(), floor(cy - h - ph).toInt(), q, floor(ph).toInt(), white)
        c.fillRect(floor(cx + pw * 0.3f).toInt(), floor(cy - h - ph).toInt(), floor(pw * 0.7f).toInt(), floor(ph).toInt(), whiteD)
        c.fillEllipse(cx, cy - h - ph, pw * 1.6f, 0.8f * K, white)
        c.fillEllipse(cx, cy - h - ph - 0.9f * K, pw * 0.8f, 0.9f * K, whiteM)
        val pipeY = cy - h - ph * 0.62f
        if (step >= steps - 1) for (side in intArrayOf(-1, 1)) {
            c.fillRect(floor(if (side < 0) cx - pw - 1.2f * K else cx + pw).toInt(), floor(pipeY).toInt(), floor(1.2f * K).toInt(), q, Col.hex(0x3A3A40))
        }
        if (water && !winter) s.fx {
            // the water from the pipes, arcing down into the trough
            for (side in intArrayOf(-1, 1)) for (j in 0 until 5) {
                val ph2 = ((t * 2.2 + j / 5.0) % 1.0).toFloat()
                val px = cx + side * (pw + 1.2f * K + ph2 * 2f * K); val py = pipeY + ph2 * ph2 * (ph * 0.62f + h * 0.4f)
                c.blend(floor(px).toInt(), floor(py).toInt(), Pal.WATER_L, 0.85f)
                if (q >= 2) c.blend(floor(px).toInt(), floor(py).toInt() + 1, Pal.WATER_M, 0.7f)
            }
        }
    }

    /**
     * The market's stalls before the homes on the right-back edge (the market town's, while the tent isn't there): a
     * counter with its goods, two posts and an awning of striped canvas (gayer on the semenj); goods by the season.
     */
    protected fun stalls(festive: Boolean) {
        for ((k, x0) in FireLayout.STALLS.withIndex()) {
            val x1 = x0 + FireLayout.STALL_W; val y0 = FireLayout.STALL_Y0; val y1 = FireLayout.STALL_Y1
            val stripe = if (festive) intArrayOf(Col.hex(0xE23A3A), Col.hex(0xF6F0E0)) else if (k == 0) intArrayOf(Col.hex(0x3E7AB8), Col.hex(0xF0EADA)) else intArrayOf(Col.hex(0x3E8A4E), Col.hex(0xF0EADA))
            // the posts at the back and the front corners
            for (px in floatArrayOf(x0 + 0.05f, x1 - 0.05f)) {
                post(px, y0 + 0.05f, 0f, 13f)
            }
            // the counter
            block(x0, y0 + 0.2f, x1, y1, 0f, 5.5f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            faceY(y1, x0, x1, 0.4f, 5f) { x, z, _, _ -> if (((x - x0) / 0.2f) % 1f < 0.12f) Pal.WOOD_D else 0 }
            goods(x0, y0 + 0.25f, x1, y1 - 0.05f, 5.5f, k)
            for (px in floatArrayOf(x0 + 0.05f, x1 - 0.05f)) post(px, y1 + 0.05f, 0f, 11.5f)
            // the awning sloping to the front
            slopeAlongX(y0, 14f, y1 + 0.45f, 11f, x0 - 0.12f, x1 + 0.12f) { a, d, _, _ ->
                val col = stripe[Math.floorMod(floor(a * 6f).toInt(), 2)]
                if (d > 0.88f) Col.scale(col, 0.82f) else if (winter && d < 0.6f) Pal.SNOW_L else col
            }
            // its scalloped valance
            faceY(y1 + 0.45f, x0 - 0.12f, x1 + 0.12f, 9.6f, 11f) { x, z, _, _ ->
                val f = Math.floorMod(floor((x - x0) / 0.25f * 100f).toInt(), 100) / 100f
                if (z < 10f + abs(f - 0.5f) * 1.6f) 0 else Col.scale(stripe[Math.floorMod(floor((x - x0) / 0.22f).toInt(), 2)], 0.9f)
            }
        }
    }

    /** Goods on a counter: apples, cabbages, bread and honey jars in summer and autumn, crates, a basket; less in winter. */
    private fun goods(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, k: Int) {
        val q = detail
        val n = if (winter) 3 else 6
        for (j in 0 until n) {
            val x = x0 + 0.18f + j * (x1 - x0 - 0.36f) / max(1, n - 1)
            val y = (y0 + y1) / 2f + (if (j % 2 == 0) -0.1f else 0.12f)
            val px = iso.ix(x, y); val py = iso.iy(x, y, z)
            val col = when ((j + k * 2) % 5) {
                0 -> Col.hex(0xD8392E) // apples
                1 -> Col.hex(0x7AAE4A) // cabbages
                2 -> Col.hex(0xC8904A) // bread
                3 -> Col.hex(0xE8A21E) // honey
                else -> Col.hex(0xE07A2A) // pumpkins, carrots
            }
            c.fillEllipse(px.toFloat(), py - 1.2f * K, 1.3f * K, 1.1f * K, col)
            c.set(px - q, py - (1.8f * K).toInt(), Col.mix(col, Col.hex(0xFFFFFF), 0.4f))
        }
    }

    /** A lantern on its iron post at world ([x], [y]), lit from dusk: its glass warm, its light on the square (the frame's). */
    protected fun lantern(x: Float, y: Float, lit: Boolean, seed: Int) {
        val q = detail
        val iron = Col.hex(0x3A3A40); val ironD = Col.hex(0x26262B); val ironL = Col.hex(0x5C5C66)
        val px = iso.ix(x, y); val base = iso.iy(x, y, 0f)
        val top = iso.iy(x, y, LANTERN_POST)
        shade(px + K.toFloat(), base.toFloat(), 2.2f * K, 0.8f * K, 0.8f, groundId)
        // the foot, the post, a scroll under the lantern
        c.fillRect(px - q, base - 2 * q, 3 * q, 2 * q, ironD)
        c.fillRect(px, top, q, base - top, iron)
        if (q >= 2) c.fillRect(px - q / 2, top, q / 2 + 1, base - top, ironL)
        // the lantern: a cap, four panes, a base
        val lw = 3 * detail; val lh = 3 * K
        val lx = px - lw / 2 + q / 2; val ly = top - lh - q
        c.fillRect(lx, ly - q, lw, q, ironD)
        c.fillRect(lx + q, ly - 2 * q, q, q, iron)
        val f = 0.85f + 0.15f * sin(t * 5 + seed).toFloat()
        for (yy in ly until ly + lh) for (xx in lx until lx + lw) {
            val frameP = xx == lx || xx == lx + lw - 1 || (q >= 2 && xx == lx + lw / 2)
            if (frameP) { c.set(xx, yy, ironD); continue }
            if (lit) {
                c.penEmissive = true
                c.set(xx, yy, if ((yy - ly) * 2 > lh && Noise.rnd(xx, yy, 7) < f) Pal.WINDOW_LIT_HI else Pal.WINDOW_LIT)
                c.penEmissive = false
            } else c.set(xx, yy, if (xx - lx < lw / 2) Pal.GLASS_HI else Pal.GLASS)
        }
        c.fillRect(lx, ly + lh, lw, q, ironD)
        if (lit) s.light(px.toFloat(), (ly + lh / 2).toFloat(), 24f, 0.62f * f)
    }

    // ------------------------------------------------------------------ the frame's light and smoke from the homes

    /** The homes' chimneys smoking while their hearths burn, the lit windows' glow on the square at night. */
    protected fun homesAlive() {
        val l = layout
        val cold = frame.world.centre.cold
        for (h in l.homes) {
            if (h.id in cold) continue
            val (sx, sy) = chimneyTop(h)
            if (h.hut) s.smoke(sx, sy, 0.35f, h.seed) else s.smoke(sx, sy, 0.45f, h.seed)
            if (windowsLit) {
                val lot = h.lot
                val x = doorX(lot); val y = doorY(lot)
                s.light(iso.sx(x, y), iso.sy(x, y, 7f), 26f, 0.42f * env.windows)
            }
        }
        if (l.church && windowsLit && s.hour < 21.5f) {
            val x = (FireLayout.B.x0 + FireLayout.TOWER_X0) / 2f
            s.light(iso.sx(x, FireLayout.B.y1 + 0.3f), iso.sy(x, FireLayout.B.y1 + 0.3f, 10f), 30f, 0.4f * env.windows)
        }
    }

    // ------------------------------------------------------------------ the decorations of the feasts (the frame's)

    /**
     * The decorations up today ([SceneWorld.Centre.decor], as on the map: the first camp's torches round the fire, the likof's
     * little spruce on the newest roof, garlands of flags round the square with lanterns in them after dusk, the town's
     * banners), and on a feast day the garlands from the village on (companion/SCENES.md "Ob ognju by the age").
     */
    protected fun decorations(fireX: Float, fireY: Float) {
        val l = layout
        val centre = frame.world.centre
        val decor = centre.decor
        if (Decor.TORCHES in decor) torches(fireX, fireY)
        if (Decor.SPRUCE in decor) l.homes.firstOrNull { it.newest }?.let { likof(it) }
        if (Decor.BUNTING in decor || Decor.STALLS in decor || (centre.feast && l.age >= FireAge.VILLAGE)) bunting()
        if (Decor.BANNERS in decor) banners()
    }

    /** Six torches on poles round the clearing, their flames flickering, their light on it. */
    private fun torches(fireX: Float, fireY: Float) {
        for (p in TORCHES) {
            val x = p[0]; val y = p[1]
            prop { post(x, y, 0f, 11f) }
            val px = iso.ix(x, y); val py = iso.iy(x, y, 12f)
            val f = ((t * 9 + x * 1.7) % 2.0).toFloat()
            val q = detail
            glow {
                c.fillRect(px - q, py - q, 2 * q, 2 * q, Pal.FLAME[2])
                c.fillRect(px - q / 2, py - 2 * q - (if (f < 1f) 0 else q), q, q, Pal.FLAME[1])
                c.set(px, py - 3 * q, Pal.FLAME[0])
            }
            s.light(px.toFloat(), py - q.toFloat(), 18f, 0.6f)
        }
    }

    /** The likof's little spruce with its ribbons on the ridge of the newest home round the square. */
    private fun likof(h: Home) {
        val lot = h.lot
        val ridge = if (h.hut) HUT_EAVE + HUT_RIDGE else EAVE + RIDGE
        val (x, y) = if (lot.gableX) (lot.x1 - 0.1f) to (lot.y0 + lot.y1) / 2f else (lot.x0 + lot.x1) / 2f to (lot.y1 - 0.1f)
        val px = iso.ix(x, y); val py = iso.iy(x, y, ridge)
        val q = detail
        prop(Pal.OUTLINE_TREE) {
            c.fillRect(px - q / 2, py - 2 * q, q, 2 * q, Pal.WOOD_D)
            for (i in 0 until 6) {
                val half = (6 - i) / 2
                c.fillRect(px - half * q, py - (3 + i) * q, (2 * half + 1) * q, q, if (i % 2 == 0) Col.hex(0x2E6A3A) else Col.hex(0x3E8A4A))
            }
        }
        val cols = intArrayOf(Pal.FLAG_RED, Pal.FLAG_WHITE, Pal.FLAG_BLUE, Pal.GOLD)
        for (k in 0 until 4) {
            val sway = sin(t * 4 + k).toFloat()
            for (j in 0 until 5) c.fillRect(px + ((k - 1.5f) * q + j * q * 0.8f * (if (k < 2) -1 else 1)).toInt() + (sway * j * 0.3f * q).toInt(), py - 8 * q + j * q, q, q, cols[k])
        }
    }

    /**
     * Garlands of little flags strung round the square, sagging between the linden, the church's tower or the homes and the
     * lanterns, fluttering; their little lanterns lit after dusk.
     */
    private fun bunting() {
        val l = layout
        val anchors = buildList {
            add(floatArrayOf(FireLayout.LINDEN_X + 0.2f, FireLayout.LINDEN_Y + 0.3f, if (l.linden > 0f) 17f else 12f))
            if (l.church) add(floatArrayOf(FireLayout.TOWER_X1, FireLayout.TOWER_Y1, 16f))
            else add(floatArrayOf(7.2f, 2.1f, 14f))
            if (!l.tent) add(floatArrayOf(11.6f, 2.1f, 14f))
        }
        val left = floatArrayOf(2.85f, 9.3f, LANTERN_POST - 1f)
        val lines = buildList {
            for (i in 0 until anchors.size - 1) add(anchors[i] to anchors[i + 1])
            add(anchors[0] to left)
        }
        val cols = intArrayOf(Pal.FLAG_RED, Pal.GOLD, Pal.FLAG_BLUE, Col.hex(0x3E9A52), Pal.FLAG_WHITE)
        val lit = windowsLit
        prop(outline = 0) {
            for ((a, b) in lines) {
                rope(a[0], a[1], a[2], b[0], b[1], b[2], Col.hex(0xE8E0C8), sag = 2.2f)
                val n = (hypot(b[0] - a[0], b[1] - a[1]) / 0.38f).toInt()
                for (j in 1 until n) {
                    val f = j / n.toFloat()
                    val x = a[0] + (b[0] - a[0]) * f; val y = a[1] + (b[1] - a[1]) * f
                    val z = a[2] + (b[2] - a[2]) * f - 2.2f * 4f * f * (1 - f)
                    val px = iso.ix(x, y); val py = iso.iy(x, y, z)
                    val q = detail
                    val sway = (sin(t * 3.4 + j * 0.9).toFloat() * q).toInt()
                    if (lit && j % 3 == 0) {
                        glow { c.fillRect(px - q / 2, py, q, 2 * q, Pal.WINDOW_LIT) }
                        s.light(px.toFloat(), py + q.toFloat(), 9f, 0.35f)
                    } else {
                        val col = cols[(j + n) % cols.size]
                        for (r in 0 until 3) c.fillRect(px - (2 - r) * q / 2 + sway * r / 3, py + r * q, max(1, (3 - r) * q), q, col)
                    }
                }
            }
        }
    }

    /** The town's banners on two tall poles at the square's back corners, hanging long, stirring. */
    private fun banners() {
        val cols = intArrayOf(Pal.FLAG_RED, Pal.FLAG_WHITE, Pal.FLAG_BLUE)
        for ((k, p) in BANNER_POLES.withIndex()) {
            val x = p[0]; val y = p[1]
            prop { post(x, y, 0f, 26f) }
            val px = iso.ix(x, y); val py = iso.iy(x, y, 25f)
            val q = detail
            prop(Pal.OUTLINE) {
                for (r in 0 until 14) {
                    val sway = (sin(t * 2.2 + r * 0.35 + k).toFloat() * 0.8f * q * r / 14f).toInt()
                    val col = cols[r * 3 / 14]
                    c.fillRect(px + q + sway, py + r * q, 4 * q - (if (r == 13) 2 * q else 0), q, col)
                }
            }
        }
    }

    // ------------------------------------------------------------------ the sky over the backdrop

    /**
     * The top of what stands at the back over the picture's columns [x0]..[x1] (picture px): the highest row the homes, the
     * church, the linden or the camp's spruces reach there (the picture's bottom where nothing does). The moon keeps above it
     * where the sky has room ([CampfirePainter]).
     */
    protected fun skyline(x0: Float, x1: Float): Float {
        val l = layout
        var top = h.toFloat()
        fun span(ax: Float, bx: Float, y: Float) { if (bx >= x0 && ax <= x1) top = min(top, y) }
        for (tr in l.trees) {
            val bx = iso.sx(tr[0], tr[1]); val by = iso.sy(tr[0], tr[1], 0f)
            if (style.coast) {
                // the coast's umbrella pines: a broad flat crown
                span(bx - tr[2] * K * 0.6f, bx + tr[2] * K * 0.6f, by - tr[2] * K * 0.82f)
                continue
            }
            val hgt = tr[2] * K; val hw = tr[2] * K * 0.25f
            // a spruce is a narrow cone: its top over the columns it reaches
            val near = if (bx in x0..x1) 0f else min(abs(x0 - bx), abs(x1 - bx))
            if (near < hw) top = min(top, by - hgt + near / hw * hgt * 0.9f)
        }
        for (hm in l.homes) {
            val lot = hm.lot
            val ridge = (if (hm.hut) HUT_EAVE + HUT_RIDGE else EAVE + RIDGE + 8f)
            span(iso.sx(lot.x0, lot.y1), iso.sx(lot.x1, lot.y0), iso.sy(lot.x0, lot.y0, ridge))
        }
        if (l.church) {
            val b = FireLayout.B
            span(iso.sx(b.x0, b.y1), iso.sx(FireLayout.TOWER_X0, b.y0), iso.sy(b.x0, b.y0, 28.5f))
            val tz = TOWER_TOP + when (style.tower) { Tower.SPIRE -> SPIRE_H + 4.5f; Tower.CAMPANILE -> 11.5f; Tower.ONION -> 16f; Tower.BATTLEMENTS -> 9f }
            span(iso.sx(FireLayout.TOWER_X0, FireLayout.TOWER_Y1), iso.sx(FireLayout.TOWER_X1, FireLayout.TOWER_Y0), iso.sy(FireLayout.TOWER_X0, FireLayout.TOWER_Y0, tz))
        }
        if (l.linden > 0f) {
            val bx = iso.sx(FireLayout.LINDEN_X, FireLayout.LINDEN_Y); val by = iso.sy(FireLayout.LINDEN_X, FireLayout.LINDEN_Y, 0f)
            val g = l.linden
            val r = (5f + 8.5f * g) * K
            val cy = by - (6f + 6f * g) * K - r * 0.62f
            val near = if (bx in x0..x1) 0f else min(abs(x0 - bx), abs(x1 - bx))
            if (near < r * 1.1f) top = min(top, cy - sqrt(max(0f, r * r * 1.2f - near * near)))
        }
        return top
    }

    companion object {
        /** A house's eave and its roof's rise over it; its stone socle; a hut's (nominal px). */
        const val EAVE = 18f
        const val RIDGE = 8.5f
        const val SOCLE = 2.6f
        const val HUT_EAVE = 11f
        const val HUT_RIDGE = 9f

        /** The church's tower: its walls' top, its spire's height. */
        const val TOWER_TOP = 34f
        const val SPIRE_H = 12f

        /** A lantern's post (nominal px). */
        const val LANTERN_POST = 15f

        /** The first camp's torches round the clearing: x, y (clear of the stage's spots and the tent). */
        val TORCHES = arrayOf(
            floatArrayOf(2.4f, 3.2f), floatArrayOf(7.1f, 4.3f), floatArrayOf(11.6f, 7.9f),
            floatArrayOf(7.6f, 11.6f), floatArrayOf(2.6f, 9.6f), floatArrayOf(4.3f, 1.4f),
        )

        /** The top byte of a colour a plane draws glowing ([glowing], [plane]); an opaque colour's. */
        const val GLOW = 0xFE
        const val OPAQUE = 0xFF000000.toInt()

        /** The town's banners' poles at the square's back corners. */
        val BANNER_POLES = arrayOf(floatArrayOf(2.75f, 3.25f), floatArrayOf(7.25f, 2.6f))
    }
}
