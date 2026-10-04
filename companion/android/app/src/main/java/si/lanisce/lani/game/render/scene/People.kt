package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.Pose
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * How one of the people looks: sizes, colours, hair, hat, prop and the little things (glasses, a beard,
 * rosy cheeks). Shared by the scene sprites ([PeoplePainter]) and the portraits ([PortraitsPainter]).
 *
 * The people are drawn small and round: a big head (about half of them) with a mop of hair, dot eyes and a
 * small mouth, a short body in blocks of colour, short legs and dark shoes.
 */
internal data class Look(
    val headW: Int = 15, val headH: Int = 13, val torsoW: Int = 11, val torsoH: Int = 8, val legH: Int = 6,
    val skin: Int = Pal.SKIN, val hair: Int = Pal.HAIR[0], val hairStyle: Int = Looks.HAIR_SHORT,
    val top: Int = Pal.SHIRTS[3], val bottom: Int = Col.hex(0x3A3246), val shoes: Int = Col.hex(0x2E2018),
    val skirt: Boolean = false, val shorts: Boolean = false, val stockings: Int = 0,
    val apron: Int = 0, val hat: Int = Looks.HAT_NONE, val hatCol: Int = 0, val prop: Int = Looks.PROP_NONE,
    val beard: Int = 0, val moustache: Int = 0, val glasses: Boolean = false, val cheeks: Boolean = false,
    val stripes: Int = 0, val suspenders: Boolean = false, val vest: Int = 0, val dots: Int = 0, val collar: Int = 0,
    /** On the back: [Looks.PACK_KROSNJA], the pedlar's wooden frame loaded with suha roba. */
    val pack: Int = Looks.PACK_NONE,
    /** A short cape over the shoulders (the pilgrim's), a shoulder bag on a strap across the body: their colours, 0 = none. */
    val cape: Int = 0, val satchel: Int = 0,
    /** A scallop shell on the hat (the pilgrim's badge); [boots] reach above the ankle, dusty from the road. */
    val shell: Boolean = false, val boots: Boolean = false,
    /** Binoculars on a strap round the neck, on the chest (the hunter's). */
    val binoculars: Boolean = false,
) {
    /** From the soles to the top of the hair (a hat adds a little). */
    val height get() = legH + torsoH + headH
    /** The children: a small body under a big round head. */
    val child get() = torsoW <= 9
    /** Grey hair: the old people get a few lines in their portraits. */
    val old get() = hair == Pal.HAIR[4]
}

/** The cast's looks by sprite name (see [si.lanisce.lani.game.scene.SceneArt.people]). */
internal object Looks {
    const val HAIR_SHORT = 0; const val HAIR_BALD = 1; const val HAIR_BRAIDS = 2; const val HAIR_LONG = 3
    const val HAIR_BUN = 4; const val HAIR_PONYTAIL = 5; const val HAIR_CURLY = 6
    const val HAT_NONE = 0; const val HAT_SCARF = 1; const val HAT_FELT = 2; const val HAT_STRAW = 3
    const val HAT_VEIL = 4; const val HAT_CAP = 5; const val HAT_BANDANA = 6
    const val PROP_NONE = 0; const val PROP_STICK = 1; const val PROP_CROOK = 2; const val PROP_HAMMER = 3; const val PROP_BOOK = 4
    const val PROP_SMOKER = 5; const val PROP_MUG = 6; const val PROP_BOTTLE = 7; const val PROP_BASKET = 8; const val PROP_SPOON = 9; const val PROP_FLOWER = 10
    /** A pilgrim's tall staff with a gourd for water, standing at the near side. */
    const val PROP_STAFF = 11
    const val PACK_NONE = 0; const val PACK_KROSNJA = 1

    // the children's proportions: a big head, a small body
    private const val KID_HEAD_W = 13; private const val KID_HEAD_H = 12; private const val KID_TORSO_W = 9; private const val KID_TORSO_H = 6; private const val KID_LEG_H = 4

    val all: Map<String, Look> = mapOf(
        "grandma" to Look(
            torsoW = 13, torsoH = 8, legH = 5, hair = Pal.HAIR[4], hairStyle = HAIR_BUN,
            top = Col.hex(0x2F4A8A), bottom = Col.hex(0x2F4A8A), skirt = true, stockings = Col.hex(0x6E6A72), apron = Col.hex(0xF4EEDD),
            hat = HAT_SCARF, hatCol = Pal.FLAG_RED, prop = PROP_SPOON, cheeks = true, dots = Col.hex(0xF7F9FD),
        ),
        "grandpa" to Look(
            hair = Pal.HAIR[4], hairStyle = HAIR_BALD,
            top = Col.hex(0xE9E2D0), vest = Col.hex(0x6F4527), bottom = Col.hex(0x4A4652), hat = HAT_FELT, hatCol = Col.hex(0x5A4232),
            prop = PROP_STICK, beard = Col.hex(0xE6E2DA), moustache = Col.hex(0xE6E2DA),
        ),
        "child1" to Look(
            headW = KID_HEAD_W, headH = KID_HEAD_H, torsoW = KID_TORSO_W, torsoH = KID_TORSO_H, legH = KID_LEG_H, hair = Pal.HAIR[3], hairStyle = HAIR_SHORT,
            top = Pal.SHIRTS[0], bottom = Col.hex(0x2E63B0), shorts = true, cheeks = true,
        ),
        "child2" to Look(
            headW = KID_HEAD_W, headH = KID_HEAD_H, torsoW = KID_TORSO_W, torsoH = KID_TORSO_H, legH = KID_LEG_H, hair = Pal.HAIR[2], hairStyle = HAIR_BRAIDS,
            top = Col.hex(0xF0C23A), bottom = Col.hex(0xF0C23A), skirt = true, stockings = Col.hex(0xF7F9FD), cheeks = true, prop = PROP_FLOWER,
        ),
        "child3" to Look(
            headW = KID_HEAD_W, headH = KID_HEAD_H, torsoW = KID_TORSO_W, torsoH = KID_TORSO_H, legH = KID_LEG_H, hair = Pal.HAIR[1], hairStyle = HAIR_CURLY,
            top = Pal.SHIRTS[2], stripes = Col.hex(0xF4EEDD), bottom = Col.hex(0x6F4527), hat = HAT_CAP, hatCol = Col.hex(0x2C7F86), cheeks = true,
        ),
        "shepherd" to Look(
            hair = Pal.HAIR[0], hairStyle = HAIR_SHORT,
            top = Col.hex(0x8A6A48), vest = Col.hex(0xE9E2D0), bottom = Col.hex(0x4A3A2F), hat = HAT_FELT, hatCol = Col.hex(0x3E5A3A),
            prop = PROP_CROOK, moustache = Pal.HAIR[0],
        ),
        "smith" to Look(
            torsoW = 13, torsoH = 9, hair = Pal.HAIR[1], hairStyle = HAIR_SHORT,
            top = Col.hex(0x4A5A6A), bottom = Col.hex(0x2E2A34), apron = Col.hex(0x8A5A32), hat = HAT_BANDANA, hatCol = Pal.FLAG_RED,
            prop = PROP_HAMMER, beard = Col.hex(0x3A2A24),
        ),
        "teacher" to Look(
            hair = Col.hex(0x8A4A22), hairStyle = HAIR_PONYTAIL,
            top = Col.hex(0x7A4E9A), bottom = Col.hex(0x3A3246), skirt = true, stockings = Col.hex(0x3A3246), glasses = true, prop = PROP_BOOK, collar = Col.hex(0xF4EEDD),
        ),
        "farmer" to Look(
            hair = Pal.HAIR[3], hairStyle = HAIR_SHORT,
            top = Col.hex(0x2E63B0), bottom = Col.hex(0x6F4527), hat = HAT_STRAW, hatCol = Pal.HAY_L, suspenders = true, skin = Col.hex(0xE0A878),
        ),
        "beekeeper" to Look(
            hair = Pal.HAIR[0], hairStyle = HAIR_SHORT,
            top = Col.hex(0xF1E3BA), bottom = Col.hex(0xF1E3BA), hat = HAT_VEIL, hatCol = Pal.HAY_L, prop = PROP_SMOKER,
        ),
        "innkeeper" to Look(
            torsoW = 13, torsoH = 8, hair = Pal.HAIR[0], hairStyle = HAIR_BALD,
            top = Col.hex(0xE9E2D0), vest = Col.hex(0x2C7F86), bottom = Col.hex(0x2E2A34), apron = Col.hex(0xF4EEDD), prop = PROP_MUG,
            moustache = Pal.HAIR[0], cheeks = true,
        ),
        "winemaker" to Look(
            hair = Pal.HAIR[1], hairStyle = HAIR_SHORT,
            top = Col.hex(0x7A2E3A), bottom = Col.hex(0x3A3246), hat = HAT_FELT, hatCol = Col.hex(0x2E2A34), prop = PROP_BOTTLE, moustache = Pal.HAIR[1],
        ),
        "aunt" to Look(
            legH = 5, hair = Pal.HAIR[0], hairStyle = HAIR_BUN,
            top = Col.hex(0xE07A8A), bottom = Col.hex(0xE07A8A), skirt = true, stockings = Col.hex(0xF7F9FD), dots = Col.hex(0xF7F9FD),
            apron = Col.hex(0x3E8A3A), prop = PROP_BASKET, cheeks = true, collar = Col.hex(0xF7F9FD),
        ),
        // the strangers at the road (the day's surprise): the krošnjar from Ribnica, a white shirt, a red neckerchief, the
        // krošnja's leather straps, a black hat and a stick; a pilgrim in a cape, with a staff, a bag and dusty boots
        "pedlar" to Look(
            hair = Pal.HAIR[1], hairStyle = HAIR_SHORT,
            top = Col.hex(0xE9E2D0), bottom = Col.hex(0x4A3A2F), hat = HAT_FELT, hatCol = Col.hex(0x2A2426), collar = Pal.FLAG_RED,
            suspenders = true, prop = PROP_STICK, moustache = Pal.HAIR[1], cheeks = true, pack = PACK_KROSNJA,
        ),
        "pilgrim" to Look(
            hair = Pal.HAIR[3], hairStyle = HAIR_SHORT,
            top = Col.hex(0x8A6E4E), bottom = Col.hex(0x4A4652), shoes = Col.hex(0x5E4632), boots = true, cape = Col.hex(0x5E4A38),
            hat = HAT_FELT, hatCol = Col.hex(0x7A5E3E), shell = true, prop = PROP_STAFF, satchel = Col.hex(0xB08A56), beard = Pal.HAIR[3],
        ),
        // the hunter of the hunting club (an extra): a loden-green jacket over a red shirt collar, breeches and boots, a
        // green felt hat with a jay's feather, a grey moustache, binoculars on his chest and a walking stick
        "hunter" to Look(
            hair = Pal.HAIR[4], hairStyle = HAIR_SHORT,
            top = Col.hex(0x4E6A3E), bottom = Col.hex(0x6A5238), shoes = Col.hex(0x3A2A1E), boots = true, collar = Col.hex(0xC8503A),
            hat = HAT_FELT, hatCol = Col.hex(0x3E5436), prop = PROP_STICK, moustache = Pal.HAIR[4], binoculars = true,
        ),
        // the charcoal burner who watches his pile in the woods (a passer-by, not one of the village): a work jacket gone
        // grey with soot over a dark waistcoat, a black felt hat, a dark beard, heavy boots, the long stick he pokes the
        // pile's vents with; a smudge of soot on the cheek
        "burner" to Look(
            hair = Pal.HAIR[1], hairStyle = HAIR_SHORT, skin = Col.hex(0xE8B894),
            top = Col.hex(0x5A524A), vest = Col.hex(0x2E2A2A), bottom = Col.hex(0x3A3432), shoes = Col.hex(0x241C18), boots = true,
            hat = HAT_FELT, hatCol = Col.hex(0x2A2426), prop = PROP_STICK, beard = Col.hex(0x3A2E28), moustache = Col.hex(0x3A2E28),
        ),
    )

    private val fallback = Look()

    /**
     * The look of [art]; the village's own people ("woman", "man") vary with [seed] (hair, clothes, a hat, a
     * moustache), so newcomers don't all look alike. The seed is the person's id hashed (see ArtPainter.person).
     */
    fun of(art: String, seed: Int = 0): Look = when (art) {
        "woman" -> woman(seed)
        "man" -> man(seed)
        else -> all[art] ?: fallback
    }

    private val skirts = intArrayOf(Col.hex(0x3A3246), Col.hex(0x2F4A8A), Col.hex(0x7A2E3A), Col.hex(0x3E5A3A), Col.hex(0x6F4527), Col.hex(0xE07A8A))
    private val trousers = intArrayOf(Col.hex(0x3A3246), Col.hex(0x4A4652), Col.hex(0x6F4527), Col.hex(0x2E2A34))
    private val vests = intArrayOf(Col.hex(0x6F4527), Col.hex(0x2C7F86), Col.hex(0x3E5A3A))
    private val hats = intArrayOf(Col.hex(0x5A4232), Col.hex(0x2E2A34), Col.hex(0x3E5A3A))
    private val scarves = intArrayOf(Pal.FLAG_RED, Col.hex(0x2F4A8A), Col.hex(0xF4EEDD), Col.hex(0x3E8A3A))
    private val skins = intArrayOf(Pal.SKIN, Pal.SKIN, Col.hex(0xF6CDB0), Col.hex(0xE0A878))

    private fun pick(seed: Int, shift: Int, n: Int) = ((seed ushr shift) and 0xFFFF) % n

    private fun woman(seed: Int): Look {
        val hair = Pal.HAIR[pick(seed, 2, 4)]
        val style = intArrayOf(HAIR_BUN, HAIR_BRAIDS, HAIR_LONG, HAIR_PONYTAIL)[pick(seed, 5, 4)]
        val top = Pal.SHIRTS[pick(seed, 8, Pal.SHIRTS.size)]
        val skirt = skirts[pick(seed, 11, skirts.size)]
        val apron = if (pick(seed, 14, 3) == 0) Col.hex(0xF4EEDD) else 0
        val scarf = pick(seed, 16, 4) == 0
        return Look(
            legH = 5, hair = hair, hairStyle = style, skin = skins[pick(seed, 28, skins.size)],
            top = top, bottom = skirt, skirt = true, stockings = if (pick(seed, 19, 2) == 0) Col.hex(0xF7F9FD) else Col.hex(0x3A3246),
            apron = apron, hat = if (scarf) HAT_SCARF else HAT_NONE, hatCol = scarves[pick(seed, 21, scarves.size)],
            cheeks = pick(seed, 18, 2) == 0, collar = if (pick(seed, 24, 3) == 0) Col.hex(0xF7F9FD) else 0,
            dots = if (pick(seed, 26, 4) == 0) Col.hex(0xF7F9FD) else 0,
        )
    }

    private fun man(seed: Int): Look {
        val hair = Pal.HAIR[pick(seed, 2, 4)]
        val style = intArrayOf(HAIR_SHORT, HAIR_SHORT, HAIR_CURLY, HAIR_BALD)[pick(seed, 5, 4)]
        val top = Pal.SHIRTS[pick(seed, 8, Pal.SHIRTS.size)]
        val hat = intArrayOf(HAT_NONE, HAT_NONE, HAT_CAP, HAT_FELT)[pick(seed, 16, 4)]
        return Look(
            hair = hair, hairStyle = style, skin = skins[pick(seed, 28, skins.size)],
            top = top, bottom = trousers[pick(seed, 11, trousers.size)],
            vest = if (pick(seed, 14, 3) == 0) vests[pick(seed, 15, vests.size)] else 0,
            hat = hat, hatCol = hats[pick(seed, 21, hats.size)],
            moustache = if (pick(seed, 18, 3) == 0) hair else 0, beard = if (pick(seed, 20, 5) == 0) hair else 0,
            suspenders = pick(seed, 22, 3) == 0,
        )
    }
}

/**
 * Where someone's body is ([PeoplePainter.body]): [tw] and [hw] the torso's and the head's half widths from the feet's
 * middle; in rows from the soles' line (up is negative) the shoulders ([armTop]), the hanging hands ([hand]), the head's
 * top ([hTop]), the eyes ([eyeRow]) and the hips ([legTop]).
 */
internal class Body(val tw: Int, val hw: Int, val armTop: Int, val hand: Int, val hTop: Int, val eyeRow: Int, val legTop: Int)

/**
 * The people of the scenes: one procedural sprite per [si.lanisce.lani.game.scene.SceneArt.people] entry,
 * 22 (children) to 31 px tall, small and round: a big head with a mop of hair, dot eyes that blink, a small
 * mouth, rosy cheeks on some; a short body in blocks of colour with the sleeves at its sides, short legs, dark
 * shoes. They breathe, talk (mouth and a raised hand), walk and sit, and strike the [Pose]s: a happy hop and a
 * smile, a cheer with both arms up and eyes shut with joy, a sad shrug under drooping brows, a hand at the chin
 * with the eyes up, a hand cupped at the ear, a wave; and asleep, lying in a bed under a cover ([lying], [zzz]).
 */
internal class PeoplePainter {
    private lateinit var c: PixelCanvas

    /** Drawing someone squatting ([draw]'s crouch): sat low, the shins short. */
    private var crouching = false

    fun height(art: String): Int = if (art == "baby") BABY_H else Looks.of(art).height

    /** How tall [art] (with [seed]'s look) is squatting ([draw]'s crouch): from the soles to the top of the hair. */
    fun crouchHeight(art: String, seed: Int = 0): Int = if (art == "baby") BABY_H else 1 - body(art, seed, crouch = true).hTop

    /**
     * Where the near hand was drawn by the last [draw] (its inner column, next to the sleeve, and its top row; the hand
     * is 2 × 2, reaching one column further out): what it carries is drawn from there.
     */
    var heldX = 0; private set
    var heldY = 0; private set

    /**
     * Where [art]'s body is, standing (or squatting, [crouch]) with the feet's middle at (bx, by0): the torso's and the
     * head's half widths, and in rows from by0 (up is negative) the shoulders' row (where the arms start), the hands' row
     * when they hang, the head's top and the eyes' row (without a breath's dip). For a caller placing what someone holds,
     * or where a hand reaches ([draw]'s reach).
     */
    fun body(art: String, seed: Int = 0, crouch: Boolean = false): Body {
        val L = Looks.of(art, seed)
        val legTop = -(if (crouch) 2 else L.legH)
        val torsoTop = legTop - (if (crouch) max(4, L.torsoH - 2) else L.torsoH)
        val hTop = torsoTop - L.headH + 1
        return Body(L.torsoW / 2, L.headW / 2, torsoTop + 1, legTop - 2, hTop, hTop + (L.headH * 0.55f).toInt(), legTop)
    }

    private val eye = Col.hex(0x1E1618)
    private val mouthCol = Col.hex(0xB8484A)
    private val mouthIn = Col.hex(0x6A2226)
    private val blush = Col.hex(0xF2A09A)
    private val dust = Col.hex(0xC8B89A)

    private fun row(x0: Int, x1: Int, y: Int, col: Int) { if (x1 >= x0) c.hline(x0, x1, y, col) }

    /**
     * Draws [art] with the feet centred at ([bx], [by0]) (the soles' row is by0 - 1). [flip] faces left.
     * [walking] animates the legs; [seated] draws them sitting (legs forward, on a bench or a stump), [crouch] squatting
     * on the floor (the knees up, the body lower: under a table, behind a chest). [talking]
     * alone is the [Pose.TALK] pose; any other [pose] shows that mood, with the mouth moving if also talking.
     * [Pose.SLEEP] lies them down on a pillow there (see [lying]), or [upright] (a portrait) has them doze standing.
     * At work (the village map's people): [reach] puts the near hand at canvas point (reach[0], reach[1]) (its inner
     * column and top row, as [heldX], [heldY]), the arm going there from the shoulder, and with four values the far hand at
     * (reach[2], reach[3]); [holding] leaves their prop out of the near hand (it holds what the caller draws); [wink] shuts
     * the near eye in a wink.
     */
    fun draw(
        canvas: PixelCanvas, art: String, bx: Int, by0: Int, time: Double, seed: Int, talking: Boolean, flip: Boolean,
        walking: Boolean, seated: Boolean, pose: Pose = Pose.IDLE, upright: Boolean = false, crouch: Boolean = false,
        reach: IntArray? = null, holding: Boolean = false, wink: Boolean = false,
    ) {
        if (crouch && art != "baby" && pose != Pose.SLEEP) {
            // squatting: sat low on the heels, the torso a little shorter; drawn as sitting, with short shins
            crouching = true
            draw(canvas, art, bx, by0, time, seed, talking, flip, walking = false, seated = true, pose = pose, upright = upright, reach = reach, holding = holding, wink = wink)
            crouching = false
            return
        }
        c = canvas
        if (art == "baby") { baby(bx, by0, time, seed, talking, flip, pose); return }
        if (pose == Pose.SLEEP && !upright) {
            val (hx, hy, dx) = flat(bx, by0, flip)
            lying(canvas, art, hx, hy, dx, 0, time, seed, pillow = true)
            return
        }
        val L = Looks.of(art, seed)
        val d = if (flip) -1 else 1
        val P = if (pose == Pose.IDLE && talking) Pose.TALK else pose
        val speaking = talking || P == Pose.TALK
        // a hop (happy) or a jump (cheer) lifts the whole figure; not when sitting
        val hop = when {
            seated -> 0
            P == Pose.HAPPY -> if (sin(time * 5 + seed * 0.3) > 0.3) 2 else 0
            P == Pose.CHEER -> (max(0.0, sin(time * 4 + seed * 0.3)) * 3.5).toInt()
            else -> 0
        }
        val by = by0 - hop
        val breath = if (!walking && hop == 0 && sin(time * 1.7 + seed * 0.37) > 0.55) 1 else 0
        val step = if (walking) (((time * 7).toInt() + seed) and 1) else -1
        val hw = L.headW / 2; val tw = L.torsoW / 2
        val legH = if (crouching) 2 else if (seated) 4 else L.legH
        val legTop = by - legH
        val torsoTop = legTop - (if (crouching) max(4, L.torsoH - 2) else L.torsoH)
        // the head sits on the shoulders: its chin row is the torso's first; it dips a little on a breath, when sad and dozing
        val hBot = torsoTop + breath + (if (P == Pose.SAD || P == Pose.SLEEP) 1 else 0)
        val hTop = hBot - L.headH + 1
        val eyeRow = hTop + (L.headH * 0.55f).toInt()
        val topD = Col.scale(L.top, 0.78f); val topL = Col.mix(L.top, Col.hex(0xFFFFFF), 0.18f)

        // ---- behind the body: a long crook, long hair and braids, the basket on the far arm (the crook and the staff
        // are in the near hand: not while it holds something else)
        if (L.prop == Looks.PROP_CROOK && !holding) {
            val px = bx + d * (tw + 4)
            c.vline(px, hTop - 4, by - 1, Pal.WOOD_M)
            c.set(px, hTop - 5, Pal.WOOD_L); c.set(px - d, hTop - 6, Pal.WOOD_L); c.set(px - d * 2, hTop - 6, Pal.WOOD_L); c.set(px - d * 3, hTop - 5, Pal.WOOD_L); c.set(px - d * 3, hTop - 4, Pal.WOOD_L)
        }
        if (L.prop == Looks.PROP_STAFF && !holding) staff(bx + d * (tw + 4), hTop, by, d)
        if (L.pack == Looks.PACK_KROSNJA) krosnja(bx, hw, hTop, torsoTop, legTop, d)
        hairBehind(L, bx, hw, hTop, hBot, d)
        if (L.prop == Looks.PROP_BASKET) basket(bx - d * (tw + 3) - 3, legTop - 2)

        // ---- legs (or the lap), shoes
        val shoeW = if (L.child) 3 else 4
        if (seated) {
            // sitting: the lap comes toward the viewer, the shins go down to the shoes
            val lapTop = legTop - 1
            c.fillRect(bx - tw, lapTop, L.torsoW, 2, L.bottom)
            row(bx - tw, bx + tw, lapTop + 1, Col.scale(L.bottom, 0.8f))
            val shin = if (L.skirt || L.shorts) (if (L.stockings != 0) L.stockings else L.skin) else L.bottom
            for (side in intArrayOf(-1, 1)) {
                val x0 = if (side < 0) bx - tw + 1 else bx + tw - 3
                c.fillRect(x0, lapTop + 2, 3, by - 2 - lapTop - 2, shin)
                c.fillRect(x0, by - 2, 3, 2, L.shoes); c.set(x0 + 1, by - 2, Col.mix(L.shoes, Col.hex(0xFFFFFF), 0.25f))
                if (L.boots) { c.fillRect(x0, by - 3, 3, 1, L.shoes); c.set(x0 + 2, by - 1, dust) }
            }
        } else for (leg in 0..1) {
            val side = if (leg == 0) -1 else 1
            val lift = if (step >= 0 && step == leg) 1 else 0
            val shift = if (step >= 0) (if (step == leg) d else -d) else 0
            val x0 = (if (side < 0) bx - shoeW + 1 else bx + 1) + shift
            val legCol = if (L.skirt || L.shorts) (if (L.stockings != 0) L.stockings else L.skin) else L.bottom
            c.fillRect(x0, legTop - lift, shoeW - 1 + (if (side < 0) 0 else 0), by - 2 - legTop, legCol)
            if (L.shorts) c.fillRect(x0, legTop - lift, shoeW - 1, (by - 2 - legTop) / 2 + 1, L.bottom)
            if (!L.skirt && !L.shorts) c.vline(if (side < 0) x0 + shoeW - 2 else x0, legTop - lift, by - 3 - lift, Col.scale(L.bottom, 0.8f))
            // shoes: a little wider than the leg, toes out, a shine on the toe
            val sx = if (side < 0) x0 - 1 else x0
            c.fillRect(sx, by - 2 - lift, shoeW, 2, L.shoes)
            c.set(if (side < 0) sx + 1 else sx + shoeW - 2, by - 2 - lift, Col.mix(L.shoes, Col.hex(0xFFFFFF), 0.25f))
            // boots up the shin, the road's dust on the toes and the heels
            if (L.boots) {
                c.fillRect(x0, by - 4 - lift, shoeW - 1, 2, L.shoes); c.set(x0, by - 4 - lift, Col.scale(L.shoes, 0.8f))
                c.set(sx + (if (side < 0) 0 else shoeW - 1), by - 1 - lift, dust); c.set(sx + 2, by - 1 - lift, dust)
            }
        }

        // ---- the skirt, flaring out over the legs
        if (L.skirt) {
            val sTop = legTop - 2; val sBot = if (seated) legTop + 1 else legTop + max(1, legH - 3)
            val n = sBot - sTop
            for (k in 0..n) {
                val ww = tw + (k * 2 + 1) / (n + 1)
                row(bx - ww, bx + ww, sTop + k, if (k == n) Col.scale(L.bottom, 0.72f) else L.bottom)
                c.set(bx + ww, sTop + k, Col.scale(L.bottom, 0.8f))
                if (L.dots != 0 && k % 2 == 1) { var xx = bx - ww + 1 + (k % 4) / 2; while (xx < bx + ww) { c.set(xx, sTop + k, L.dots); xx += 3 } }
            }
        }

        // ---- the body: shirt in blocks, a lit left edge and a shaded right one, what's worn over it
        val tTop = torsoTop; val tBot = legTop - 1
        for (yy in tTop..tBot) {
            val inset = if (yy == tTop) 1 else 0
            row(bx - tw + inset, bx + tw - inset, yy, L.top)
        }
        c.vline(bx + tw, tTop + 1, tBot, topD); c.vline(bx - tw, tTop + 1, tBot, topL)
        if (L.stripes != 0) for (yy in tTop + 2..tBot step 2) row(bx - tw + 1, bx + tw - 1, yy, L.stripes)
        if (L.dots != 0 && !L.skirt) for (yy in tTop + 2..tBot step 2) { var xx = bx - tw + 1 + (yy % 4) / 2; while (xx < bx + tw) { c.set(xx, yy, L.dots); xx += 3 } }
        if (L.vest != 0) {
            val vw = max(2, tw - 2)
            c.fillRect(bx - tw, tTop + 1, vw, tBot - tTop, L.vest)
            c.fillRect(bx + tw - vw + 1, tTop + 1, vw, tBot - tTop, Col.scale(L.vest, 0.86f))
            c.set(bx - tw + vw, tTop + 3, Pal.GOLD); c.set(bx - tw + vw, tTop + 5, Pal.GOLD)
        }
        if (L.suspenders) { c.vline(bx - 2, tTop + 1, tBot, Col.hex(0x3A2A20)); c.vline(bx + 2, tTop + 1, tBot, Col.hex(0x3A2A20)) }
        if (!L.skirt) { row(bx - tw, bx + tw, tBot, Col.hex(0x3A2A20)); c.set(bx, tBot, Pal.GOLD) } // the belt
        if (L.apron != 0) {
            val aTop = tTop + 3; val aBot = if (seated) legTop + 1 else if (L.skirt) legTop + max(0, legH - 4) else legTop + legH / 2
            c.fillRect(bx - tw + 2, aTop, L.torsoW - 4, aBot - aTop + 1, L.apron)
            c.vline(bx + tw - 2, aTop, aBot, Col.scale(L.apron, 0.86f))
            c.vline(bx - 2, tTop + 1, aTop, L.apron); c.vline(bx + 2, tTop + 1, aTop, L.apron)
            row(bx - tw + 2, bx + tw - 2, aBot, Col.scale(L.apron, 0.8f))
        }
        if (L.collar != 0) { row(bx - 2, bx + 2, tTop + 1, L.collar); c.set(bx - 1, tTop + 2, L.collar); c.set(bx + 1, tTop + 2, L.collar) }
        if (L.cape != 0) cape(L.cape, bx, tw, tTop)
        if (L.binoculars) binoculars(bx, tTop)
        // a shoulder bag's strap, from the near shoulder across the chest to the far hip
        if (L.satchel != 0) c.line(bx + d * (tw - 1), tTop, bx - d * (tw - 1), tBot - 1, Col.scale(L.satchel, 0.7f))

        // ---- the head
        head(L, bx, hw, hTop, hBot, eyeRow, d)
        face(L, P, bx, hw, hTop, hBot, eyeRow, time, seed, speaking, if (wink) d else 0)
        hat(L, art, bx, hw, hTop, hBot, eyeRow, d)

        // ---- arms, after the head so a hand can rest at the chin or the ear (far side plain, near side gesturing)
        val gesture = P == Pose.TALK && ((time * 2).toInt() and 1) == 0
        val armTop = tTop + 1; val armBot = tBot
        val swing = if (step >= 0) (if (step == 0) 1 else -1) else 0
        val sleeve = L.top; val sleeveD = topD
        var handX = bx + d * (tw + 1); var handY = armBot - 1
        var busy = false
        fun arm(side: Int, dy: Int) {
            // a sleeve down the body's side and the hand below it
            val x0 = if (side > 0) bx + tw + 1 else bx - tw - 2
            c.fillRect(x0, armTop + dy, 2, armBot - armTop - 1, sleeve)
            c.vline(if (side > 0) x0 + 1 else x0, armTop + dy + 1, armBot - 2 + dy, sleeveD)
            c.fillRect(x0, armBot - 1 + dy, 2, 2, L.skin)
        }
        when (P) {
            Pose.CHEER -> for (side in intArrayOf(-d, d)) {
                val hx = bx + side * (hw + 1); val hy = hTop + 2
                limb(bx + side * (tw + 1), armTop, hx, hy + 1, side, sleeve)
                hand(hx, hy - 1, side, L.skin)
                busy = true
            }
            Pose.SAD -> for (side in intArrayOf(-d, d)) {
                // a shrug: elbows out, palms up
                val ex = bx + side * (tw + 3); val ey = armTop + 4
                limb(bx + side * (tw + 1), armTop, ex, ey, side, sleeve)
                limb(ex, ey, bx + side * (tw + 4), armTop + 1, side, sleeve)
                hand(bx + side * (tw + 4), armTop - 1, side, L.skin)
                busy = true
            }
            else -> {
                val far = reach?.takeIf { it.size >= 4 }
                if (far != null) {
                    // the far hand reaching too (across the body: the sleeve on the shirt, the hand showing)
                    limb(bx - d * (tw + 1), armTop, far[2], far[3], -d, sleeveD)
                    hand(far[2], far[3], -d, L.skin)
                } else arm(-d, if (swing != 0) swing else 0)
                when {
                    reach != null -> {
                        // the near hand where the work takes it: a tool's handle, a load, a lantern held out
                        limb(bx + d * (tw + 1), armTop, reach[0], reach[1], d, sleeve)
                        handX = reach[0]; handY = reach[1]
                        hand(handX, handY, d, L.skin)
                    }
                    P == Pose.THINK -> {
                        // the forearm comes up to the chin
                        limb(bx + d * (tw + 1), armTop, bx + d * (tw + 1), armBot - 2, d, sleeve)
                        limb(bx + d * (tw + 1), armBot - 2, bx + d * 3, hBot, d, sleeve)
                        hand(bx + d * 2, hBot - 1, d, L.skin)
                        busy = true
                    }
                    P == Pose.LISTEN -> {
                        // a hand cupped at the ear
                        limb(bx + d * (tw + 1), armTop, bx + d * (hw + 1), eyeRow + 3, d, sleeve)
                        hand(bx + d * (hw + 1), eyeRow, d, L.skin); c.set(bx + d * (hw + 2), eyeRow - 1, L.skin)
                        busy = true
                    }
                    P == Pose.WAVE -> {
                        val wob = if (((time * 3).toInt() and 1) == 0) d else 0
                        limb(bx + d * (tw + 1), armTop, bx + d * (hw + 2), hTop + 4, d, sleeve)
                        hand(bx + d * (hw + 2) + wob, hTop + 1, d, L.skin); c.set(bx + d * (hw + 2) + wob, hTop, L.skin)
                        handX = bx + d * (hw + 2) + wob; handY = hTop
                        busy = true
                    }
                    gesture -> {
                        // the forearm raised in front, the open hand out
                        limb(bx + d * (tw + 1), armTop, bx + d * (tw + 3), armTop + 2, d, sleeve)
                        handX = bx + d * (tw + 3); handY = armTop - 1
                        hand(handX, handY, d, L.skin)
                    }
                    else -> { arm(d, -swing); handY = armBot - 1 - swing }
                }
            }
        }

        // ---- the shoulder bag at the far hip, over the arm; what the near hand holds
        if (L.satchel != 0) satchel(L.satchel, bx - d * (tw + 1), tBot, d)
        if (!busy && !holding) prop(L, bx, tw, handX, handY, by, d, gesture, time)
        heldX = handX; heldY = handY
    }

    // ------------------------------------------------------------------ the head

    /** The head: a rounded block of skin, the ears, a little shade on the far cheek and under the chin. */
    private fun head(L: Look, bx: Int, hw: Int, hTop: Int, hBot: Int, eyeRow: Int, d: Int) {
        val n = hBot - hTop
        for (yy in hTop..hBot) {
            val r = yy - hTop
            val inset = when (r) { 0 -> 3; 1 -> 1; n -> 3; n - 1 -> 1; else -> 0 }
            row(bx - hw + inset, bx + hw - inset, yy, L.skin)
        }
        val skinD = Col.scale(L.skin, 0.86f)
        c.vline(bx + hw, hTop + 2, hBot - 2, skinD)
        row(bx - hw + 3, bx + hw - 3, hBot, skinD)
        // ears, where the hair leaves them out
        if (L.hairStyle != Looks.HAIR_LONG && L.hairStyle != Looks.HAIR_BRAIDS && L.hat != Looks.HAT_SCARF) {
            c.vline(bx - hw - 1, eyeRow, eyeRow + 1, L.skin); c.vline(bx + hw + 1, eyeRow, eyeRow + 1, skinD)
        }
        hair(L, bx, hw, hTop, hBot, eyeRow, d)
    }

    /** Long hair and braids hang behind the shoulders: drawn before the body. */
    private fun hairBehind(L: Look, bx: Int, hw: Int, hTop: Int, hBot: Int, d: Int) {
        if (L.hat == Looks.HAT_SCARF) return
        val hairD = Col.scale(L.hair, 0.78f)
        when (L.hairStyle) {
            Looks.HAIR_LONG -> for (side in intArrayOf(-1, 1)) {
                c.fillRect(if (side < 0) bx - hw - 1 else bx + hw - 1, hTop + 3, 3, hBot - hTop + 1, if (side < 0) L.hair else hairD)
            }
            Looks.HAIR_PONYTAIL -> {
                val px = bx - d * (hw + 1)
                c.fillRect(px - (if (d > 0) 1 else 0), hTop + 3, 2, hBot - hTop - 1, L.hair)
                c.set(px - d, hBot, hairD); c.set(px - d, hBot + 1, hairD)
            }
            else -> Unit
        }
    }

    /**
     * The hair over the head: a mop over the forehead with a ragged fringe and a lighter sheen, down the sides
     * to the ears; bald crowns with a fringe of grey, curls, a bun, braids with red ties, long hair framing the face.
     */
    private fun hair(L: Look, bx: Int, hw: Int, hTop: Int, hBot: Int, eyeRow: Int, d: Int) {
        val hair = L.hair; val hairD = Col.scale(hair, 0.78f); val hairL = Col.mix(hair, Col.hex(0xFFFFFF), 0.28f)
        val fringe = eyeRow - 3
        if (L.hairStyle == Looks.HAIR_BALD) {
            // the crown shines; a band of hair round the back, above the ears
            c.set(bx - 3, hTop + 1, Col.mix(L.skin, Col.hex(0xFFFFFF), 0.5f)); c.set(bx - 2, hTop + 1, Col.mix(L.skin, Col.hex(0xFFFFFF), 0.35f))
            for (side in intArrayOf(-1, 1)) { c.fillRect(if (side < 0) bx - hw - 1 else bx + hw, fringe, 2, 3, if (side < 0) hair else hairD) }
            return
        }
        // the mop: one row over the crown, then the full width a little wider than the head
        row(bx - hw + 3, bx + hw - 3, hTop - 1, hair)
        for (yy in hTop until fringe) {
            val out = if (yy - hTop >= 2) 1 else 0
            row(bx - hw - out, bx + hw + out, yy, hair)
        }
        // the fringe: a ragged edge swept to one side
        for (x in bx - hw..bx + hw) {
            val u = x - bx
            val tip = ((u + 7) * 5 + d * 3) % 4 == 0
            if (tip || abs1(u) >= hw - 1) c.set(x, fringe, hair)
            if (abs1(u) >= hw - 1 && L.hairStyle != Looks.HAIR_CURLY) c.set(x, fringe + 1, hairD)
        }
        // the sides down to the ears
        c.vline(bx - hw - 1, hTop + 2, fringe + 1, hair); c.vline(bx + hw + 1, hTop + 2, fringe + 1, hairD)
        // the sheen on the crown and the shade on the far side
        row(bx - 4, bx - 1, hTop, hairL); c.set(bx - 5, hTop + 1, hairL)
        c.vline(bx + hw, hTop + 2, fringe - 1, hairD)
        val covered = L.hat != Looks.HAT_NONE
        when (L.hairStyle) {
            Looks.HAIR_CURLY -> {
                // bumps round the outline (under a hat only at the sides)
                if (!covered) for (x in bx - hw..bx + hw step 2) c.set(x, hTop - 2 + ((x - bx) and 1), hair)
                c.vline(bx - hw - 2, hTop + 3, fringe, hair); c.vline(bx + hw + 2, hTop + 3, fringe, hairD)
                for (x in bx - hw + 1..bx + hw - 1 step 3) c.set(x, fringe, hair)
            }
            Looks.HAIR_BUN -> if (!covered) {
                // a bun on the crown, tied with a darker twist
                c.fillRect(bx - 2, hTop - 4, 5, 3, hair); row(bx - 1, bx + 1, hTop - 5, hair)
                row(bx - 2, bx + 2, hTop - 2, hairD); c.set(bx - 1, hTop - 4, hairL)
            }
            Looks.HAIR_BRAIDS -> {
                c.vline(bx, hTop, fringe - 1, hairD) // the parting
                for (side in intArrayOf(-1, 1)) {
                    val x = bx + side * (hw + 1)
                    for (yy in fringe..hBot + 4) c.set(x, yy, if ((yy - fringe) % 2 == 0) hair else hairD)
                    c.vline(x + side, fringe, hBot, hair)
                    c.set(x, hBot + 5, Pal.FLAG_RED); c.set(x + side, hBot + 5, Pal.FLAG_RED)
                }
            }
            Looks.HAIR_LONG -> for (side in intArrayOf(-1, 1)) {
                c.vline(bx + side * hw, fringe, hBot - 1, if (side < 0) hair else hairD)
                c.vline(bx + side * (hw + 1), fringe, hBot + 2, if (side < 0) hair else hairD)
            }
            Looks.HAIR_PONYTAIL -> { c.set(bx - d * (hw + 1), hTop + 2, hairD); c.set(bx - d * (hw + 2), hTop + 3, Pal.FLAG_RED) }
            else -> Unit
        }
    }

    /**
     * The face: dot eyes that blink now and then (shut with joy at a cheer, down when sad, up when thinking),
     * brows for the moods, rosy cheeks, a beard or a moustache, the mouth by the mood and moving when talking; [wink]
     * (-1 the left eye, 1 the right one, 0 none) shuts one eye in a happy curve.
     */
    private fun face(L: Look, P: Pose, bx: Int, hw: Int, hTop: Int, hBot: Int, eyeRow: Int, time: Double, seed: Int, speaking: Boolean, wink: Int = 0) {
        val ex = intArrayOf(bx - 3, bx + 3)
        val look = if (P == Pose.THINK) -1 else if (P == Pose.SAD) 1 else 0
        val blink = ((time + (seed and 0xFF) * 0.031) % 3.7) < 0.14 && P != Pose.CHEER
        val brow = Col.scale(if (L.hairStyle == Looks.HAIR_BALD) Pal.HAIR[0] else L.hair, 0.7f)
        for ((i, x) in ex.withIndex()) {
            val side = if (i == 0) -1 else 1
            when {
                P == Pose.CHEER -> { c.set(x - 1, eyeRow + 1, eye); c.set(x, eyeRow, eye); c.set(x + 1, eyeRow + 1, eye) } // ^ ^
                P == Pose.SLEEP -> { c.set(x - 1, eyeRow + 1, eye); c.set(x, eyeRow + 2, eye); c.set(x + 1, eyeRow + 1, eye) } // shut, lashes down
                wink == side -> { c.set(x - 1, eyeRow + 1, eye); c.set(x, eyeRow, eye); c.set(x + 1, eyeRow + 1, eye) } // a wink: ^
                blink -> row(x - 1, x, eyeRow + 1, eye)
                else -> c.vline(x, eyeRow + look, eyeRow + 1 + look, eye)
            }
            when (P) {
                Pose.SAD -> { c.set(x - side, eyeRow - 2, brow); c.set(x, eyeRow - 2, brow); c.set(x + side, eyeRow - 3, brow) }
                Pose.THINK -> if (side > 0) { c.set(x - 1, eyeRow - 3, brow); c.set(x, eyeRow - 3, brow) }
                Pose.LISTEN -> row(x - 1, x, eyeRow - 2, brow)
                else -> Unit
            }
        }
        if (L.glasses) {
            val rim = Col.hex(0x3A3A44)
            for (x in ex) {
                row(x - 2, x + 1, eyeRow - 1, rim); row(x - 2, x + 1, eyeRow + 2, rim)
                c.vline(x - 2, eyeRow - 1, eyeRow + 2, rim); c.vline(x + 1, eyeRow - 1, eyeRow + 2, rim)
                c.set(x - 1, eyeRow, Col.hex(0xE8F0F8))
            }
            row(ex[0] + 2, ex[1] - 3, eyeRow, rim)
        }
        if (L.cheeks || L.child) { c.set(ex[0] - 2, eyeRow + 2, blush); c.set(ex[0] - 1, eyeRow + 2, blush); c.set(ex[1] + 1, eyeRow + 2, blush); c.set(ex[1] + 2, eyeRow + 2, blush) }
        val my = eyeRow + 3
        if (L.beard != 0) {
            // round under the chin and over the collar, the mouth a gap in it
            for (yy in my - 1..hBot + 2) {
                val r = yy - (my - 1); val last = hBot + 2 - yy
                val inset = if (last == 0) 3 else if (last == 1) 1 else if (r == 0) 2 else 0
                row(bx - hw + 1 + inset, bx + hw - 1 - inset, yy, L.beard)
            }
            c.vline(bx + hw - 1, my, hBot, Col.scale(L.beard, 0.82f))
        }
        if (L.moustache != 0) { row(bx - 2, bx + 1, my - 1, L.moustache); c.set(bx - 3, my, L.moustache); c.set(bx + 2, my, L.moustache) }
        val open = (speaking && ((time * 7).toInt() and 1) == 0) || P == Pose.CHEER
        val lip = if (L.beard != 0) Col.scale(L.beard, 0.55f) else mouthCol
        when {
            open -> { c.fillRect(bx - 1, my, 2, 2, mouthIn); c.set(bx, my + 1, Col.hex(0xE07070)); if (P == Pose.CHEER) { c.set(bx - 2, my, mouthIn); c.set(bx + 1, my, mouthIn) } }
            P == Pose.HAPPY || P == Pose.WAVE -> { row(bx - 1, bx, my + 1, lip); c.set(bx - 2, my, lip); c.set(bx + 1, my, lip) }
            P == Pose.SAD -> { row(bx - 1, bx, my, lip); c.set(bx - 2, my + 1, lip); c.set(bx + 1, my + 1, lip) }
            P == Pose.THINK -> c.set(bx + 1, my, lip)
            // asleep: a little "o", a touch wider on the breath in
            P == Pose.SLEEP -> { c.set(bx, my, mouthIn); if (sin(time * 1.6 + seed * 0.37) > 0.3) c.set(bx - 1, my, mouthIn) }
            else -> row(bx - 1, bx, my, lip)
        }
        if (P == Pose.SAD && ((time * 2).toInt() and 1) == 0 && L.child) c.set(ex[1] + 1, eyeRow + 3, Pal.WATER_L) // a tear
        if (L.old) { c.set(ex[0] - 1, eyeRow + 3, Col.scale(L.skin, 0.84f)); c.set(ex[1] + 1, eyeRow + 3, Col.scale(L.skin, 0.84f)) }
    }

    /** Hats over the hair: the kerchief, felt hats, the straw hat, the beekeeper's veil, a cap, the smith's bandana. */
    private fun hat(L: Look, art: String, bx: Int, hw: Int, hTop: Int, hBot: Int, eyeRow: Int, d: Int) {
        val col = L.hatCol; val colD = Col.scale(col, 0.72f); val colL = Col.mix(col, Col.hex(0xFFFFFF), 0.25f)
        val brimY = eyeRow - 4
        when (L.hat) {
            Looks.HAT_SCARF -> {
                // over the hair and down the sides, a fold at the forehead, the knot under the chin
                row(bx - hw + 2, bx + hw - 2, hTop - 1, col)
                for (yy in hTop..eyeRow - 4) row(bx - hw - (if (yy == hTop) 0 else 1), bx + hw + (if (yy == hTop) 0 else 1), yy, col)
                row(bx - hw, bx + hw, eyeRow - 4, colD)
                for (side in intArrayOf(-1, 1)) c.fillRect(if (side < 0) bx - hw - 1 else bx + hw, eyeRow - 2, 2, hBot - eyeRow + 2, if (side < 0) col else colD)
                c.fillRect(bx + d * 2 - 1, hBot + 1, 3, 2, col); c.set(bx + d * 4, hBot + 3, colD); c.set(bx + d * 3, hBot + 3, col)
                row(bx - 4, bx - 1, hTop - 1, colL)
                if (L.dots != 0) for (yy in hTop + 1..eyeRow - 5 step 2) for (xx in bx - hw + 1 + ((yy / 2) % 2) * 2..bx + hw - 1 step 4) c.set(xx, yy, L.dots)
            }
            Looks.HAT_FELT -> {
                // a crown with a dent, a dark band, a wide brim over the fringe
                for (yy in brimY - 5 until brimY) row(bx - 5, bx + 5, yy, col)
                row(bx - 4, bx - 1, brimY - 6, col); row(bx + 1, bx + 4, brimY - 6, col)
                row(bx - 5, bx + 5, brimY - 1, colD)
                c.vline(bx + 5, brimY - 5, brimY - 2, colD); row(bx - 4, bx - 2, brimY - 5, colL)
                row(bx - hw - 2, bx + hw + 2, brimY, colL); row(bx - hw - 1, bx + hw + 1, brimY + 1, colD)
                if (art == "shepherd") { c.set(bx + 5, brimY - 4, Pal.GOLD); c.set(bx + 6, brimY - 5, Pal.GOLD); c.set(bx + 6, brimY - 6, Pal.FLAG_WHITE) } // a feather
                if (art == "hunter") {
                    // a jay's feather in the band at the back, blue barred with black, and a little brush of chamois hair
                    val blue = Col.hex(0x3A78C8); val bar = Col.hex(0x1E2A44)
                    c.set(bx - d * 5, brimY - 2, blue); c.set(bx - d * 6, brimY - 3, bar); c.set(bx - d * 6, brimY - 4, blue)
                    c.set(bx - d * 7, brimY - 5, blue); c.set(bx - d * 7, brimY - 6, Pal.FLAG_WHITE)
                    c.set(bx - d * 4, brimY - 6, Col.hex(0x5A4A3A)); c.set(bx - d * 4, brimY - 7, Col.hex(0x8A7A62))
                }
                if (L.shell) {
                    // a scallop shell on the crown, the pilgrim's badge: a fan of ribs over a little hinge
                    val sh = Col.hex(0xF4EEDD); val rib = Col.hex(0xC9B690)
                    row(bx - 1, bx + 1, brimY - 5, sh); row(bx - 2, bx + 2, brimY - 4, sh); row(bx - 2, bx + 2, brimY - 3, sh)
                    c.set(bx - 1, brimY - 4, rib); c.set(bx + 1, brimY - 4, rib); c.set(bx, brimY - 3, rib)
                    c.set(bx, brimY - 2, sh); c.set(bx - 1, brimY - 2, rib); c.set(bx + 1, brimY - 2, rib)
                }
            }
            Looks.HAT_STRAW, Looks.HAT_VEIL -> {
                val straw = if (L.hat == Looks.HAT_VEIL) Pal.CANVAS_L else Pal.HAY_M
                for (yy in brimY - 4 until brimY) row(bx - 5, bx + 5, yy, straw)
                row(bx - 4, bx + 4, brimY - 5, straw)
                if (L.hat == Looks.HAT_STRAW) row(bx - 5, bx + 5, brimY - 1, Pal.FLAG_RED)
                row(bx - hw - 3, bx + hw + 3, brimY, Col.mix(straw, Col.hex(0xFFFFFF), 0.3f)); row(bx - hw - 2, bx + hw + 2, brimY + 1, Col.scale(straw, 0.75f))
                for (x in bx - 4..bx + 4 step 3) c.set(x, brimY - 3, Col.scale(straw, 0.8f))
                if (L.hat == Looks.HAT_VEIL) {
                    val veil = Col.hex(0xC8CCD8)
                    for (yy in brimY + 2..hBot + 2) for (xx in bx - hw - 2..bx + hw + 2) if (Dither.at(xx, yy) < 0.4f) c.blend(xx, yy, veil, 0.8f)
                    c.vline(bx - hw - 3, brimY + 1, hBot + 2, veil); c.vline(bx + hw + 3, brimY + 1, hBot + 2, veil)
                }
            }
            Looks.HAT_CAP -> {
                // a cap over the mop, its peak shading the brow, a button on top
                for (yy in hTop - 1 until brimY + 1) row(bx - hw + (if (yy == hTop - 1) 2 else 0), bx + hw - (if (yy == hTop - 1) 2 else 0), yy, col)
                row(bx - 3, bx, hTop, colL)
                row(bx - hw - 1 + (if (d > 0) 3 else 0), bx + hw + 1 - (if (d < 0) 3 else 0), brimY + 1, colD)
                c.set(bx, hTop - 2, colD)
            }
            Looks.HAT_BANDANA -> {
                row(bx - hw - 1, bx + hw + 1, brimY - 1, col); row(bx - hw - 1, bx + hw + 1, brimY, colD)
                for (x in bx - hw..bx + hw step 3) c.set(x, brimY - 1, Pal.FLAG_WHITE)
                val kx = bx - d * (hw + 2)
                c.set(kx, brimY, col); c.set(kx - d, brimY + 1, col); c.set(kx - d, brimY + 2, colD); c.set(kx, brimY + 2, colD)
            }
        }
    }

    // ------------------------------------------------------------------ asleep

    /**
     * Where someone asleep at a standing spot lies (their feet's middle at ([bx], [by0]), facing left when [flip]): the
     * head's middle and chin row on the pillow, and how far along the feet are (px, signed the way they lie).
     */
    fun flat(bx: Int, by0: Int, flip: Boolean): Triple<Int, Int, Int> {
        val d = if (flip) -1 else 1
        return Triple(bx - d * 6, by0 - 3, d * 17)
    }

    /**
     * [art] asleep in a bed ([Pose.SLEEP]), on their back under a cover: the head on the pillow, its middle at [bx] and its
     * chin's row at [by0]; the body under the cover along the bed to the feet [dx], [dy] px away (an iso bed runs 2:1:
     * [dy] is half of [dx], either sign). [cover] is the cover's light, middle and dark (the tent's sleeping bag); with a
     * fourth colour a wool blanket of them with that check, with more a patchwork quilt of the colours past the third; null,
     * a red wool blanket with a check; [pillow] brings a pillow of their own. The eyes shut, a little "o" of a mouth, the
     * sheet turned down under the chin, a hand over the cover, the chest rising and falling with a slow breath. Tapped
     * ([si.lanisce.lani.game.scene.Sleep]): [turn] 0..1 rolls them onto their far side and back, the back of their head
     * to us and the shoulder's hump under the cover; [stir] 0..1 half wakes them, one eye open under a frown, the mouth
     * mumbling, a hand out from under the cover shooing the tapper off.
     */
    fun lying(
        canvas: PixelCanvas, art: String, bx: Int, by0: Int, dx: Int, dy: Int, time: Double, seed: Int,
        cover: IntArray? = null, pillow: Boolean = false, turn: Float = 0f, stir: Float = 0f,
    ) {
        c = canvas
        val d = if (dx < 0) -1 else 1
        // a baby sleeps in its basket
        if (art == "baby") { baby(bx + d * 4, by0 + 9, time, seed, false, d < 0, Pose.SLEEP); return }
        val base = Looks.of(art, seed)
        // in bed: the hat off (the grandmother keeps her kerchief on), nothing in hand or on the back
        val L = base.copy(
            hat = if (base.hat == Looks.HAT_SCARF) Looks.HAT_SCARF else Looks.HAT_NONE,
            prop = Looks.PROP_NONE, pack = Looks.PACK_NONE, cape = 0, satchel = 0,
        )
        val len = max(10, abs(dx))
        val hw = L.headW / 2
        val cl = cover?.getOrNull(0) ?: BLANKET_L; val cm = cover?.getOrNull(1) ?: BLANKET_M; val cd = cover?.getOrNull(2) ?: BLANKET_D
        // a fourth colour: a wool blanket of these colours with that check; more: a patchwork quilt of them past the third
        val wool = cover == null || cover.size == 4
        val check = cover?.getOrNull(3) ?: BLANKET_CHECK
        val patches = if (cover != null && cover.size > 4) cover.size - 3 else 0
        // rolled onto the far side for most of a turn, the cover heaving on the way over and back
        val away = turn > 0.22f && turn < 0.78f
        val heave = turn > 0f && !away
        val awake = stir > 0.25f
        val hBot = by0 - (if (awake) 1 else 0)
        val hTop = hBot - L.headH + 1
        val eyeRow = hTop + (L.headH * 0.55f).toInt()
        val breath = sin(time * 1.6 + seed * 0.37) > 0.2 // a slow breath, one every four seconds

        if (pillow) {
            val pw = hw + 2
            row(bx - pw + 1, bx + pw - 1, by0 - 2, PILLOW_L)
            for (yy in by0 - 1..by0 + 1) row(bx - pw, bx + pw, yy, if (yy == by0 + 1) PILLOW_D else PILLOW_M)
        }
        // long hair spreads out on the pillow behind the head
        hairBehind(L, bx, hw, hTop, hBot, d)
        if (away) backOfHead(L, bx, hw, hTop, hBot, eyeRow)
        else {
            head(L, bx, hw, hTop, hBot, eyeRow, d)
            face(L, Pose.SLEEP, bx, hw, hTop, hBot, eyeRow, time, seed, false)
            if (L.hat == Looks.HAT_SCARF) hat(L, art, bx, hw, hTop, hBot, eyeRow, d)
            if (awake) grumble(L, bx, eyeRow, time, d)
        }

        // the cover, column by column along the bed: up to the chin under the head, then the body's mound (the chest
        // rising with the breath, the hips, the legs, the feet), lit along its top, its near side in shadow
        val neck = hw - 2
        val x0 = bx - d * (hw - 1)
        val n = len + hw
        val tops = IntArray(n)
        for (k in 0 until n) {
            val x = x0 + d * k
            val u = k - (hw - 1)
            val bottom = by0 + 2 + (if (u > 0) Math.floorDiv(u * dy, len) else 0)
            val top = if (u <= neck) by0 - 1 else {
                // the shoulder, the chest (rising with a breath), a dip at the waist, the hips, the legs, the toes up
                val v = (u - neck).toFloat() / (len - neck)
                var h = when {
                    v < 0.1f -> 6
                    v < 0.38f -> if (breath) 8 else 7
                    v < 0.62f -> 6
                    v < 0.84f -> 5
                    v < 0.97f -> 6
                    else -> 4
                }
                if (away && v < 0.36f) h += 2
                if (heave && v < 0.62f) h += 1
                bottom - h
            }
            tops[k] = top
            for (yy in top..bottom + 1) {
                val r = yy - top
                val col = when {
                    u <= neck && yy <= by0 -> if (yy == by0 - 1) SHEET_L else SHEET_M // the sheet, turned down
                    yy > bottom -> Col.scale(cd, 0.8f) // the hem over the bed's edge
                    yy >= bottom - 1 || k == n - 1 -> cd
                    // the patchwork: squares of its colours, lit along the top
                    patches > 0 -> {
                        val pc = cover!![3 + Math.floorMod(((k + 1) / 4) * 5 + (r / 3) * 3, patches)]
                        if (r == 0) Col.mix(pc, Col.hex(0xFFFFFF), 0.25f) else if ((k + 1) % 4 == 0) Col.scale(pc, 0.8f) else pc
                    }
                    r == 0 -> cl
                    // the wool blanket: a band along it and stripes across; the sleeping bag: its quilting
                    wool && r == 2 -> check
                    wool && k % 6 == 3 -> Col.mix(cm, check, 0.45f)
                    !wool && k % 4 == 1 -> Col.scale(cm, 0.86f)
                    r == 1 -> Col.mix(cm, cl, 0.4f)
                    else -> cm
                }
                c.set(x, yy, col)
            }
        }
        if (awake) {
            // a hand out from under the cover by the head, shooing
            val hx = bx + d * (hw + 2); val wave = if (((time * 5).toInt() and 1) == 0) 0 else -1
            val hy = hTop + 5 + wave
            val kx = 2 * hw + 1 // the column of hx
            val ct = if (kx in 0 until n) tops[kx] else hy + 4
            for (yy in hy + 2..ct) { c.set(hx, yy, L.top); c.set(hx + d, yy, L.top) }
            c.fillRect(if (d > 0) hx else hx - 1, hy, 2, 2, L.skin)
        } else if (!away) {
            // a hand over the cover on the chest, the sleeve along it
            val kh = hw - 1 + neck + 5
            if (kh + 1 < n) {
                for (k in kh - 3 until kh) c.set(x0 + d * k, tops[k], L.top)
                c.fillRect(if (d > 0) x0 + kh else x0 - kh - 1, tops[kh] - 1, 2, 2, L.skin)
            }
        }
    }

    /**
     * The back of the head, rolled over: the hair all over it (a bald crown with its fringe, a kerchief knotted at the
     * nape), a sheen on the crown, the ears either side.
     */
    private fun backOfHead(L: Look, bx: Int, hw: Int, hTop: Int, hBot: Int, eyeRow: Int) {
        val n = hBot - hTop
        val scarf = L.hat == Looks.HAT_SCARF
        val bald = L.hairStyle == Looks.HAIR_BALD && !scarf
        val col = if (scarf) L.hatCol else if (bald) L.skin else L.hair
        val colD = Col.scale(col, 0.8f); val colL = Col.mix(col, Col.hex(0xFFFFFF), 0.25f)
        for (yy in hTop..hBot) {
            val r = yy - hTop
            val inset = when (r) { 0 -> 3; 1 -> 1; n -> 3; n - 1 -> 1; else -> 0 }
            row(bx - hw + inset, bx + hw - inset, yy, if (r >= n - 1) colD else col)
        }
        c.vline(bx + hw, hTop + 2, hBot - 2, colD)
        row(bx - 4, bx - 1, hTop + 1, colL); c.set(bx - 5, hTop + 2, colL)
        if (bald) for (side in intArrayOf(-1, 1)) c.fillRect(if (side < 0) bx - hw else bx + hw - 3, eyeRow - 3, 4, 3, L.hair)
        if (scarf) { row(bx - 1, bx + 1, hBot - 1, colD); c.set(bx, hBot, colD) } // the knot at the nape
        if (!scarf && L.hairStyle == Looks.HAIR_BUN) { c.fillRect(bx - 2, hBot - 5, 5, 3, colD); row(bx - 1, bx + 1, hBot - 6, colD) }
        if (!scarf && L.hairStyle == Looks.HAIR_BRAIDS) c.vline(bx, hTop + 1, hBot - 2, colD) // the parting
        if (!scarf) { c.vline(bx - hw - 1, eyeRow, eyeRow + 1, L.skin); c.vline(bx + hw + 1, eyeRow, eyeRow + 1, Col.scale(L.skin, 0.86f)) }
    }

    /** Half awake: the near eye open under a frown, the other still shut, the mouth mumbling. */
    private fun grumble(L: Look, bx: Int, eyeRow: Int, time: Double, d: Int) {
        val skin = L.skin
        val brow = Col.scale(if (L.hairStyle == Looks.HAIR_BALD) Pal.HAIR[0] else L.hair, 0.7f)
        val ex = bx + d * 3
        c.set(ex - 1, eyeRow + 1, skin); c.set(ex, eyeRow + 2, skin); c.set(ex + 1, eyeRow + 1, skin)
        c.vline(ex, eyeRow, eyeRow + 1, eye); c.set(ex - d, eyeRow, eye) // open, the lid heavy
        for (x in intArrayOf(bx - 3, bx + 3)) { c.set(x - 1, eyeRow - 2, brow); c.set(x, eyeRow - 2, brow); c.set(x + (if (x > bx) -2 else 2), eyeRow - 3, brow) }
        val my = eyeRow + 3
        val mouthBg = if (L.beard != 0) L.beard else skin
        row(bx - 2, bx + 1, my, mouthBg); row(bx - 2, bx + 1, my + 1, mouthBg)
        val w = ((time * 7).toInt() and 1)
        c.set(bx - 2, my + w, mouthIn); c.set(bx - 1, my + 1 - w, mouthIn); c.set(bx, my + w, mouthIn); c.set(bx + 1, my + 1 - w, mouthIn)
    }

    /**
     * The Zzz over someone asleep, [art] with the head's middle and chin at ([bx], [by0]) (sprite px), drifting [d]-ward
     * and up: a z every second or so, each growing as it rises and fading out, from the clock [time]. [big] 0..1 brings
     * them faster, higher and bigger (turned over); [quiet] 0..1 fades them (half awake). Blended and glowing, so it shows
     * at night and leaves what is under it to tap; drawn after the outlines (the painter's effects pass).
     */
    fun zzz(canvas: PixelCanvas, art: String, bx: Int, by0: Int, d: Int, time: Double, seed: Int, big: Float = 0f, quiet: Float = 0f) {
        val a0 = 1f - quiet.coerceIn(0f, 1f)
        if (a0 <= 0.02f) return
        val b = big.coerceIn(0f, 1f)
        val top = by0 - (if (art == "baby") 12 else Looks.of(art, seed).headH)
        val period = 3.3 - 1.3 * b
        val was = canvas.penEmissive
        canvas.penEmissive = true
        for (j in 0 until 3) {
            val ph = (((time + (seed and 0xFF) * 0.017) / period + j / 3.0) % 1.0).toFloat()
            val size = 4 + (if (ph > 0.45f) 1 else 0) + (if (b > 0.4f) 1 else 0)
            val x = bx + d * (2 + (ph * (9f + 6f * b)).toInt()) - (if (d < 0) size - 1 else 0)
            val y = top - (ph * (10f + 6f * b)).toInt() - size
            val a = a0 * min(1f, ph / 0.12f) * (if (ph > 0.7f) (1f - ph) / 0.3f else 1f)
            glyph(canvas, x + 1, y + 1, size, ZZZ_SHADE, a * 0.4f)
            glyph(canvas, x, y, size, ZZZ, a)
        }
        canvas.penEmissive = was
    }

    /** A z [n] × [n] with its top left at ([x], [y]): the top bar, the stroke down to the left, the bottom bar. */
    private fun glyph(canvas: PixelCanvas, x: Int, y: Int, n: Int, col: Int, a: Float) {
        if (a <= 0f) return
        for (i in 0 until n) { canvas.blend(x + i, y, col, a); canvas.blend(x + i, y + n - 1, col, a) }
        for (r in 1 until n - 1) canvas.blend(x + n - 1 - r, y + r, col, a)
    }

    // ------------------------------------------------------------------ hands and props

    private fun prop(L: Look, bx: Int, tw: Int, handX: Int, handY: Int, by: Int, d: Int, gesture: Boolean, time: Double) {
        when (L.prop) {
            Looks.PROP_STICK -> {
                val px = handX + d * 2
                c.vline(px, handY - 1, by - 1, Pal.WOOD_D); c.set(px, handY - 2, Pal.WOOD_L); c.set(px - d, handY - 2, Pal.WOOD_L)
            }
            Looks.PROP_HAMMER -> {
                if (gesture) { c.vline(handX, handY - 5, handY - 1, Pal.WOOD_M); c.fillRect(handX - 2, handY - 8, 5, 3, Pal.SLATE_M); row(handX - 2, handX + 2, handY - 8, Pal.SLATE_L) }
                else { row(handX, handX + d * 4, handY + 1, Pal.WOOD_M); c.fillRect(handX + d * 4 - 1, handY - 1, 3, 5, Pal.SLATE_M); c.set(handX + d * 4 - 1, handY - 1, Pal.SLATE_L) }
            }
            Looks.PROP_BOOK -> {
                val x0 = if (gesture) handX - 3 else bx - 3
                val y0 = if (gesture) handY - 4 else handY - 4
                c.fillRect(x0, y0, 7, 5, Pal.FLAG_BLUE); c.fillRect(x0 + 1, y0 + 1, 5, 3, Pal.FLAG_WHITE); c.vline(x0 + 3, y0 + 1, y0 + 3, Pal.STONE_L)
            }
            Looks.PROP_SMOKER -> {
                val sx = handX + d * 2; val sy = handY - 3
                c.fillRect(sx - 1, sy, 3, 5, Pal.STONE_M); c.vline(sx - 1, sy, sy + 4, Pal.STONE_L); c.set(sx + d, sy - 1, Pal.STONE_D)
                val puff = ((time * 3).toInt() and 1)
                c.set(sx + d, sy - 3 - puff, Pal.SMOKE); c.set(sx + d * 2, sy - 4 - puff, Col.hex(0xC8C8D0))
            }
            Looks.PROP_MUG -> {
                val mx = handX + d * 2; val my = handY - 3
                c.fillRect(mx - 1, my, 3, 5, Pal.GOLD); row(mx - 1, mx + 1, my, Pal.FLAG_WHITE); c.set(mx, my - 1, Pal.FLAG_WHITE)
                c.vline(mx + d * 2, my + 1, my + 3, Pal.GOLD_L)
            }
            Looks.PROP_BOTTLE -> {
                val px = handX + d * 2
                c.vline(px, handY - 7, handY - 4, Col.hex(0x2E5A3A)); c.fillRect(px - 1, handY - 3, 3, 6, Col.hex(0x2E5A3A)); c.set(px, handY - 8, Pal.FLAG_RED)
                c.vline(px - 1, handY - 3, handY + 1, Col.hex(0x4A8A5A))
            }
            Looks.PROP_SPOON -> {
                c.line(handX, handY, handX + d * 3, handY - 5, Pal.WOOD_M); c.fillRect(handX + d * 3 - 1, handY - 8, 3, 3, Pal.WOOD_L); c.set(handX + d * 3, handY - 7, Pal.WOOD_M)
            }
            Looks.PROP_FLOWER -> {
                val fx = handX + d
                c.vline(fx, handY - 5, handY - 1, Pal.LEAF); c.set(fx, handY - 6, Pal.GOLD); c.set(fx - 1, handY - 6, Pal.GERANIUM); c.set(fx + 1, handY - 6, Pal.GERANIUM)
                c.set(fx, handY - 7, Pal.GERANIUM); c.set(fx, handY - 5, Pal.GERANIUM)
            }
            else -> Unit
        }
    }

    /** A 2-px-wide limb from ([x0], [y0]) to ([x1], [y1]); the columns sit at x, x+1 on the right [side], x-1, x on the left. */
    private fun limb(x0: Int, y0: Int, x1: Int, y1: Int, side: Int, col: Int) {
        val o = if (side > 0) 0 else -1
        c.line(x0 + o, y0, x1 + o, y1, col); c.line(x0 + o + 1, y0, x1 + o + 1, y1, col)
    }

    private fun hand(x: Int, y: Int, side: Int, col: Int) { c.fillRect(x + (if (side > 0) 0 else -1), y, 2, 2, col) }

    private fun abs1(v: Int) = if (v < 0) -v else v

    private companion object {
        const val BABY_H = 22
        // a sleeper's own pillow, the sheet turned down under the chin, a red wool blanket with a check, the Zzz
        val PILLOW_L = Col.hex(0xFAF6EC); val PILLOW_M = Col.hex(0xEAE2CE); val PILLOW_D = Col.hex(0xC8BCA2)
        val SHEET_L = Col.hex(0xF6F0E2); val SHEET_M = Col.hex(0xDCD2BC)
        val BLANKET_L = Col.hex(0xD8604A); val BLANKET_M = Col.hex(0xB8452E); val BLANKET_D = Col.hex(0x7E2A20); val BLANKET_CHECK = Col.hex(0xC98A3A)
        val ZZZ = Col.hex(0xEEF2FF); val ZZZ_SHADE = Col.hex(0x1A2040)
    }

    private fun basket(x: Int, y: Int) {
        c.fillRect(x, y, 7, 5, Pal.WOOD_M)
        for (yy in y..y + 4) for (xx in x..x + 6) if ((xx + yy) % 2 == 0) c.set(xx, yy, Pal.WOOD_L)
        row(x, x + 6, y, Pal.WOOD_D)
        c.set(x + 1, y - 1, Pal.GERANIUM); c.set(x + 3, y - 1, Pal.GOLD); c.set(x + 5, y - 1, Pal.GERANIUM); c.set(x + 2, y - 2, Pal.GERANIUM_D)
        c.line(x + 1, y - 1, x + 3, y - 4, Pal.WOOD_D); c.line(x + 3, y - 4, x + 5, y - 1, Pal.WOOD_D)
    }

    // ------------------------------------------------------------------ the strangers' things

    /**
     * The krošnja: the Ribnica pedlar's wooden frame on his back, its posts up past his ears and rails across, loaded
     * higher than his hat with suha roba: an upright sieve (a bent-wood rim round a mesh), wooden spoons fanned out on
     * the near side, a ladle hanging from the far post. Drawn behind him.
     */
    private fun krosnja(bx: Int, hw: Int, hTop: Int, tTop: Int, legTop: Int, d: Int) {
        val wood = Pal.WOOD_M; val woodL = Pal.WOOD_L; val woodD = Pal.WOOD_D
        val top = hTop - 3 // the top rail, level with the hat's crown
        val xl = bx - hw - 1; val xr = bx + hw + 1
        // the posts, from the hips up past the top rail; the rails (the body hides their middles)
        c.vline(xl, top - 1, legTop - 1, wood); c.vline(xr, top - 1, legTop - 1, woodD)
        c.set(xl, top - 2, woodL); c.set(xr, top - 2, woodL)
        row(xl, xr, top, woodL); row(xl, xr, top + 1, woodD)
        row(xl, xr, tTop + 2, wood); row(xl, xr, legTop - 2, woodD)
        // the sieve standing on the top rail, a little to the far side
        val sx = bx - d * 2; val sy = top - 4
        for (yy in -4..4) for (xx in -4..4) {
            val r2 = xx * xx + yy * yy
            if (r2 > 17) continue
            val col = when {
                r2 >= 10 -> if (yy < 0) woodL else wood // the rim, lit on top
                (xx + yy) and 1 == 0 -> Pal.CANVAS_M // the mesh
                else -> Pal.CANVAS_D
            }
            c.set(sx + xx, sy + yy, col)
        }
        c.set(sx - 3, sy - 2, Col.mix(woodL, Col.hex(0xFFFFFF), 0.3f)) // a shine on the rim
        // wooden spoons fanned out of the load on the near side: a handle up and out, a round bowl at its end
        for ((k, tip) in listOf(intArrayOf(4, -7), intArrayOf(6, -6), intArrayOf(8, -4)).withIndex()) {
            val x1 = bx + d * tip[0]; val y1 = top + tip[1]
            c.line(bx + d * 3, top, x1, y1, if (k == 1) wood else woodL)
            c.fillRect(x1 - (if (d > 0) 0 else 1), y1 - 1, 2, 2, woodL); c.set(x1 + d, y1 - 1, woodD)
        }
        // a ladle hanging from the far post by the shoulder, on its hook
        val far = if (d > 0) xl - 1 else xr + 1
        c.set(far, tTop + 2, woodD)
        c.vline(far, tTop + 3, tTop + 6, woodL); c.fillRect(far - 1, tTop + 7, 3, 2, wood); c.set(far, tTop + 7, woodD)
    }

    /** The pilgrim's staff standing at [px], a knob on top and a gourd on a string for water; drawn behind him. */
    private fun staff(px: Int, hTop: Int, by: Int, d: Int) {
        c.vline(px, hTop - 5, by - 1, Pal.WOOD_M); c.vline(px + d, hTop - 4, by - 8, Pal.WOOD_D)
        c.fillRect(px - (if (d > 0) 0 else 1), hTop - 7, 2, 2, Pal.WOOD_D); c.set(px, hTop - 7, Pal.WOOD_L)
        // the gourd hangs outward from just under the knob
        val gx = px + d * 2; val gourd = Col.hex(0xD9A040); val gourdD = Col.hex(0xA8742A); val string = Col.hex(0x3A2A20)
        c.set(px + d, hTop - 5, string); c.set(gx, hTop - 4, string)
        c.set(gx, hTop - 3, gourd); c.fillRect(gx - 1, hTop - 2, 3, 3, gourd); c.set(gx + d, hTop, gourdD); c.set(gx, hTop + 1, gourdD)
        c.set(gx - d, hTop - 2, Pal.GOLD_L)
    }

    /** Binoculars hanging on the chest from a strap round the neck: two black barrels side by side, a glint on each lens. */
    private fun binoculars(bx: Int, tTop: Int) {
        val body = Col.hex(0x26262C); val lit = Col.hex(0x4A4A56); val glass = Col.hex(0x8AB4D8)
        c.vline(bx - 2, tTop, tTop + 1, body); c.vline(bx + 2, tTop, tTop + 1, body) // the strap
        c.fillRect(bx - 2, tTop + 2, 2, 3, body); c.fillRect(bx + 1, tTop + 2, 2, 3, body)
        c.set(bx - 2, tTop + 2, lit); c.set(bx + 1, tTop + 2, lit); c.set(bx, tTop + 3, body)
        c.set(bx - 1, tTop + 4, glass); c.set(bx + 2, tTop + 4, glass)
    }

    /** A short cape over the shoulders: a little wider than the body, its hem a darker, rounded edge, a lighter collar. */
    private fun cape(col: Int, bx: Int, tw: Int, tTop: Int) {
        val colD = Col.scale(col, 0.78f)
        for (k in 0..3) {
            val w = tw + 1 - (if (k == 3) 1 else 0)
            row(bx - w, bx + w, tTop + k, if (k == 3) colD else col)
        }
        c.set(bx - tw - 1, tTop + 3, colD); c.set(bx + tw + 1, tTop + 3, colD)
        c.vline(bx + tw + 1, tTop + 1, tTop + 2, colD)
        row(bx - 1, bx + 1, tTop, Col.mix(col, Col.hex(0xFFFFFF), 0.2f))
    }

    /** A shoulder bag at the far hip ([x] its edge by the body, [tBot] the belt), with a flap and a buckle. */
    private fun satchel(col: Int, x: Int, tBot: Int, d: Int) {
        val x0 = if (d > 0) x - 3 else x
        c.fillRect(x0, tBot - 2, 4, 4, col); row(x0, x0 + 3, tBot - 2, Col.scale(col, 0.72f))
        row(x0, x0 + 3, tBot + 1, Col.scale(col, 0.8f)); c.set(x0 + (if (d > 0) 2 else 1), tBot - 1, Pal.GOLD)
    }

    // ------------------------------------------------------------------ the baby

    /**
     * The baby: in a wicker basket with a high handle, wrapped in a blanket, its big round head out under a
     * frilled bonnet. It babbles when talking, bounces when happy, throws its little arms up at a cheer, cries when
     * sad, sucks its thumb when thinking, and waves.
     */
    private fun baby(bx: Int, by0: Int, time: Double, seed: Int, talking: Boolean, flip: Boolean, pose: Pose) {
        val d = if (flip) -1 else 1
        val P = if (pose == Pose.IDLE && talking) Pose.TALK else pose
        val hop = when (P) { Pose.HAPPY -> if (sin(time * 5 + seed * 0.3) > 0.3) 1 else 0; Pose.CHEER -> (max(0.0, sin(time * 4 + seed * 0.3)) * 2.5).toInt(); else -> 0 }
        val by = by0 - hop
        val blanket = if ((seed ushr 3) % 2 == 0) Col.hex(0xF4B8CC) else Col.hex(0x9CC8E8)
        val bonnet = Col.hex(0xF7F9FD); val bonnetD = Col.hex(0xD8D8E0)
        // the handle behind, the basket
        for (k in 0..1) c.line(bx - 7 + k, by - 8, bx + k, by - BABY_H + 1, Pal.WOOD_D)
        for (k in 0..1) c.line(bx + 7 + k, by - 8, bx + k, by - BABY_H + 1, Pal.WOOD_D)
        c.set(bx, by - BABY_H, Pal.WOOD_M); c.set(bx + 1, by - BABY_H, Pal.WOOD_M)
        // the blanket bump and the head above the rim
        c.fillRect(bx - 6, by - 11, 13, 3, blanket); row(bx - 5, bx + 6, by - 12, blanket)
        val breath = if (sin(time * 1.5 + seed * 0.37) > 0.5) 1 else 0
        val tilt = if (P == Pose.LISTEN) d else 0
        val hx = bx + tilt
        val hTop = by - 19 + breath; val hBot = by - 11 + breath
        for (yy in hTop..hBot) {
            val r = yy - hTop; val inset = if (r == 0 || yy == hBot) 2 else if (r == 1 || yy == hBot - 1) 1 else 0
            row(hx - 5 + inset, hx + 5 - inset, yy, Pal.SKIN)
        }
        // the bonnet: over the top and down the sides, a frill along its edge, a bow under the chin
        row(hx - 4, hx + 4, hTop - 2, bonnet); for (yy in hTop - 1..hTop + 1) row(hx - 6, hx + 6, yy, bonnet)
        c.vline(hx - 6, hTop + 2, hBot - 2, bonnet); c.vline(hx + 6, hTop + 2, hBot - 2, bonnetD)
        for (xx in hx - 5..hx + 5 step 2) c.set(xx, hTop + 2, bonnetD)
        c.set(hx - 1, hBot + 1, blanket); c.set(hx + 1, hBot + 1, blanket)
        // the face: dot eyes (shut when crying or at a cheer), cheeks, a mouth
        val eyeY = hTop + 4
        when (P) {
            Pose.SAD, Pose.CHEER, Pose.SLEEP -> { row(hx - 3, hx - 2, eyeY + 1, eye); row(hx + 2, hx + 3, eyeY + 1, eye) }
            else -> { c.vline(hx - 2, eyeY, eyeY + 1, eye); c.vline(hx + 2, eyeY, eyeY + 1, eye); if (P == Pose.THINK) { c.set(hx - 2, eyeY - 1, eye); c.set(hx + 2, eyeY - 1, eye) } }
        }
        c.set(hx - 4, eyeY + 2, blush); c.set(hx + 4, eyeY + 2, blush)
        val my = eyeY + 3
        val babble = (talking || P == Pose.TALK) && ((time * 6).toInt() and 1) == 0
        when {
            P == Pose.SAD -> { c.fillRect(hx - 1, my, 2, 2, mouthIn); if (((time * 3).toInt() and 1) == 0) { c.set(hx - 4, eyeY + 3, Pal.WATER_L); c.set(hx + 4, eyeY + 4, Pal.WATER_L) } }
            P == Pose.CHEER || babble -> c.fillRect(hx - 1, my, 2, 1, mouthIn)
            P == Pose.THINK -> c.fillRect(hx, my, 2, 2, Pal.SKIN_D) // a thumb
            talking || P == Pose.TALK -> c.set(hx, my, mouthIn) // an "o" between the babbles
            else -> c.set(hx, my, mouthCol)
        }
        // little hands out of the blanket
        when (P) {
            Pose.CHEER -> { c.fillRect(hx - 8, hTop + 1, 2, 2, Pal.SKIN); c.fillRect(hx + 7, hTop + 1, 2, 2, Pal.SKIN) }
            Pose.WAVE -> { val wob = if (((time * 3).toInt() and 1) == 0) d else 0; c.fillRect(hx + d * 7 + wob, hTop + 3, 2, 2, Pal.SKIN) }
            Pose.HAPPY -> { c.fillRect(hx - 7, hBot - 1, 2, 2, Pal.SKIN); c.fillRect(hx + 6, hBot - 1, 2, 2, Pal.SKIN) }
            else -> {}
        }
        // the basket in front: woven, a rim, feet
        for (y in by - 8 until by) {
            val half = if (y < by - 5) 8 else 8 - (y - (by - 5)) / 2
            row(bx - half, bx + half, y, if ((y + bx) % 2 == 0) Pal.WOOD_L else Pal.WOOD_M)
            c.set(bx - half, y, Pal.WOOD_D); c.set(bx + half, y, Pal.WOOD_D)
        }
        row(bx - 8, bx + 8, by - 8, Pal.WOOD_D); row(bx - 7, bx + 7, by - 7, Pal.WOOD_L)
        row(bx - 6, bx + 6, by - 1, Pal.WOOD_X)
        c.set(bx - 6, by - 9, blanket); c.set(bx + 6, by - 9, blanket) // the blanket's edge over the rim
    }
}
