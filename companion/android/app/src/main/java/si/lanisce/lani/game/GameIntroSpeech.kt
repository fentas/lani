package si.lanisce.lani.game

import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair

/**
 * What the village's first words say aloud (ui/game/GameIntro: arriving in the valley, what feeds the village, what
 * threatens it; companion/GAME.md, "The first visit"): each step's lines as it shows them, the language learned big and the
 * learner's base under it. Read by [si.lanisce.lani.data.HintVoice]: a piece in the language learned ([HintPiece.target])
 * in the narrator's voice, the node's clip (made in the village's language: Italian for a Friuli village), else the
 * phone's voice for that language; a piece in the base by the phone's voice of the base. Pure.
 */
object GameIntroSpeech {
    /** Each step's keys, in the order they show: whether in the language learned (true) or the base (false). */
    val STEPS: List<List<Pair<String, Boolean>>> = listOf(
        listOf("gameIntro.myVillage" to true, "gameIntro.arrive" to true, "gameIntro.arrive" to false),
        listOf("gameIntro.learningBuilds" to true, "gameIntro.learningBuilds" to false),
        listOf("gameIntro.buildDefendGrow" to true, "gameIntro.villagersEat" to true, "gameIntro.villagersEat" to false),
    )

    /** Step [i]'s pieces in [pair]; a base line that reads as the learned one (a table without it) isn't said twice. */
    fun step(i: Int, pair: LangPair = L10n.pair): List<HintPiece> {
        val keys = STEPS.getOrNull(i) ?: return emptyList()
        val target = keys.filter { it.second }.map { (k, _) -> L10n.text(pair.target, k, target = pair.target) }
        return keys.mapNotNull { (k, learned) ->
            val text = L10n.text(if (learned) pair.target else pair.base, k, target = pair.target)
            if (!learned && text in target) null else HintPiece(text, learned)
        }
    }

    /** Every line in the language learned, for the node to voice ahead as the intro opens. */
    fun learned(pair: LangPair = L10n.pair): List<String> = STEPS.indices.flatMap { i -> step(i, pair).filter { it.target }.map { it.text } }
}
