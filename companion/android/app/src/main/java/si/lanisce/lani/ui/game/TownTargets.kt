package si.lanisce.lani.ui.game

import kotlin.math.roundToInt

/**
 * Where the invisible TalkBack targets over the village's pixels lie on the screen (see [TownView]): a place's centred
 * on its body, a person's across their head and down over them. Compose leaves a target that later ones cover entirely
 * out of the accessibility tree, so the fire's is drawn last, over the people standing round it.
 */
internal object TownTargets {
    /** A place's, a landmark's, a plot's, a person's or an animal's target: a fingertip (dp). */
    const val DP = 40

    /** The fire's: the village's heart and its most important place, a little bigger than the others (dp). */
    const val FIRE_DP = 48

    /** The box ([left, top, right, bottom] px) of a place's target [size] px wide on its body at screen point ([x], [y]). */
    fun place(x: Float, y: Float, size: Int): IntArray {
        val l = x.roundToInt() - size / 2; val t = y.roundToInt() - size / 2
        return intArrayOf(l, t, l + size, t + size)
    }

    /** The box of a person's target [size] px wide, their head at screen point ([x], [y]): across it, reaching down over them. */
    fun person(x: Float, y: Float, size: Int): IntArray {
        val l = x.roundToInt() - size / 2; val t = y.roundToInt() - size / 4
        return intArrayOf(l, t, l + size, t + size)
    }
}
