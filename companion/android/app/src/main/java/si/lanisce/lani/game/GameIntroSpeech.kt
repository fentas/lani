package si.lanisce.lani.game

import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.game.culture.CultureIntro
import si.lanisce.lani.l10n.LangPair

/**
 * What the village's first words say aloud (ui/game/GameIntro: arriving in the village's place, the culture's own words for
 * it, what feeds the village, what threatens it; companion/GAME.md, "The first visit"): each step's lines as it shows them, the language learned big and the
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

    /** The key whose line is the place's own when the village's culture says it ([arrive]). */
    const val ARRIVE = "gameIntro.arrive"

    /**
     * Where the learner arrives, in [lang]: the village's culture's words for it ([intro], culture.json `intro.arrive`:
     * Friuli's Collio near Gorizia, the Karawanken, the fells), else the app's own (a valley below Triglav).
     */
    fun arrive(lang: si.lanisce.lani.l10n.Lang, pair: LangPair = L10n.pair, intro: CultureIntro? = home()): String =
        intro?.arrive?.of(lang) ?: L10n.text(lang, ARRIVE, target = pair.target)

    /** The picture over the arrival: the culture's ([CultureIntro.emoji]), else the mountains. */
    fun emoji(intro: CultureIntro? = home()): String = intro?.emoji ?: "🏔️"

    /** The village's own culture's intro (also during a visit); none while no culture is read (a preview). */
    private fun home(): CultureIntro? = runCatching { si.lanisce.lani.game.culture.Cultures.home.manifest.intro }.getOrNull()

    /** [key]'s line in [lang]: the place's own for [ARRIVE], else the app's. */
    private fun line(key: String, lang: si.lanisce.lani.l10n.Lang, pair: LangPair, intro: CultureIntro?): String =
        if (key == ARRIVE) arrive(lang, pair, intro) else L10n.text(lang, key, target = pair.target)

    /** Step [i]'s pieces in [pair]; a base line that reads as the learned one (a table without it) isn't said twice. */
    fun step(i: Int, pair: LangPair = L10n.pair, intro: CultureIntro? = home()): List<HintPiece> {
        val keys = STEPS.getOrNull(i) ?: return emptyList()
        val target = keys.filter { it.second }.map { (k, _) -> line(k, pair.target, pair, intro) }
        return keys.mapNotNull { (k, learned) ->
            val text = line(k, if (learned) pair.target else pair.base, pair, intro)
            if (!learned && text in target) null else HintPiece(text, learned)
        }
    }

    /** Every line in the language learned, for the node to voice ahead as the intro opens. */
    fun learned(pair: LangPair = L10n.pair, intro: CultureIntro? = home()): List<String> =
        STEPS.indices.flatMap { i -> step(i, pair, intro).filter { it.target }.map { it.text } }
}
