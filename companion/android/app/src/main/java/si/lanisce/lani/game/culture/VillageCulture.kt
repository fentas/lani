package si.lanisce.lani.game.culture

import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.l10n.Lang

/**
 * Where a picture of a village takes its culture from (docs/plans/05-built/culture-source.md): one answer for the village
 * map and Home's header ([si.lanisce.lani.game.render.Frame.culture]), the widget, the founding's and the setup's
 * previews, the charcoal pile's picture, the words' stickers and the scenes ([si.lanisce.lani.game.scene.SceneWorld.culture]),
 * so the scene behind a door is in the look the map showed outside it, for any course and culture pair: a Slovene course
 * in Friuli's village is Friuli's on the map and inside, as an Italian one is.
 *
 * - **The learner's own village** ([home]): the culture its bridge serves (GET /culture, kept on the phone by
 *   CultureSetting and applied with [Cultures.use]), as this app can play it: a pack it doesn't bundle, a stub or a broken
 *   one is the default's, on the map and in the scenes alike. The course the learner takes doesn't enter into it.
 * - **A visit** ([shown] while it lasts): the host town's culture (TownHead.culture, [visiting]), as this app can play it;
 *   one it can't is the learner's own, on the town's map and in its places alike.
 * - **The setup's first screens** ([preview]), before a bridge has said a culture: the region of the language chosen; once
 *   one has (the setup again, to pair anew), the village's.
 * - **The land** ([landscape]): the landscape as the map draws it (one this app doesn't know is the classic valley).
 *
 * Every picture asks here, so none can take the culture from somewhere else (CultureSourceTest holds the app's sources
 * to it).
 */
object VillageCulture {
    /** The culture of the village on screen: a visited town's while a visit is on ([Cultures.visiting]), else [home]. */
    val shown: Culture get() = Cultures.current

    /** The learner's own village's culture, during a visit too (the widget, the founding): what its bridge serves. */
    val home: Culture get() = Cultures.home

    /** Pack [id] when this app can play it (bundled, complete, sound), else null: what a visit to a town of [id] shows. */
    fun visiting(id: String?): Culture? = id?.takeIf { it.isNotBlank() }?.let { runCatching { Cultures.load(it) }.getOrNull() }

    /** Pack [id] as a village of it is drawn: the pack when this app can play it, else the default's (as [Cultures.use] falls back). */
    fun playable(id: String?): Culture = visiting(id) ?: Cultures.default()

    /** Whether the horizon of [c]'s village shows the sea (its world.json `backdrop.sea`). */
    fun sea(c: Culture): Boolean = c.world.backdrop?.sea == true

    /** The landscape [state]'s village stands on, as the map draws it ([Land.landscape]: an unknown one is the classic valley). */
    fun landscape(state: GameState): String = (state.land ?: Land.CLASSIC).landscape.id

    /**
     * The culture of the setup's preview of a village (ui/setup): the village's ([home]) once a bridge has said one
     * ([said]: the setup again, to pair anew), else the first complete region of [target]'s language, else [home].
     */
    fun preview(target: Lang?, said: Boolean = Cultures.said): String =
        (if (said) null else target?.let(::region)) ?: home.id

    /** The first complete culture pack of [language] the app bundles, as the setup offers its region; null when there's none. */
    fun region(language: Lang): String? =
        Cultures.ids.firstOrNull { id -> Cultures.manifest(id)?.let { it.language == language.code && it.status == "complete" } == true }
}
