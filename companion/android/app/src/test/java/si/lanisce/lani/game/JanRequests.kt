package si.lanisce.lani.game

import si.lanisce.lani.game.villagers.Villager
import java.time.LocalDate

/**
 * A village shaped like Jan's on 30 September 2026 (data/app/game.json, read once): eight open requests. Kovač Tone has
 * four (the tutor's "V ali na" played three times below the mark, then its short drill "Kam? Na tržnico!", "Rodilnik", and
 * his own "Podkev za konja" from yesterday), Učiteljica Mojca three of the tutor's, Stari Janez his own of today; done ones
 * of Mojca, Luka and Micka in between, in the order they came.
 */
internal object JanRequests {
    val today: LocalDate = LocalDate.of(2026, 9, 30)

    val cast = listOf(
        Villager("tone", "Kovač Tone", "⚒️", "smith", voice = "male", home = listOf("smithy")),
        Villager("mojca", "Učiteljica Mojca", "👩‍🏫", "teacher", home = listOf("school", "lipa")),
        Villager("janez", "Stari Janez", "👴", "old-man", voice = "male", home = listOf("lipa")),
        Villager("luka", "Pastir Luka", "🐑", "shepherd", voice = "male", home = listOf("forest")),
        Villager("micka", "Babica Micka", "👵", "grandma", home = listOf("house", "hut")),
    )

    private fun tutor(module: String, v: Int, giver: String, emoji: String, title: String, done: Boolean = false, reward: Int = 60) = Quest(
        id = "tutor-$module-v$v", giver = giver, emoji = emoji, title = title, story = "$title · $title", skill = Res.STONE,
        reward = mapOf(Res.STONE to reward, Res.WISDOM to 10), source = QuestSource.TUTOR, moduleId = module, done = done,
    )

    val vAliNa = tutor("predlogi-kraja", 1, "Kovač Tone", "⚒️", "V ali na: v šoli, na pošti")
    val rodilnik = tutor("rodilnik-po-nikalnici", 1, "Kovač Tone", "⚒️", "Rodilnik: nimam časa, iz Gorice")
    val naTrznico = tutor("kam-na-trznico", 1, "Kovač Tone", "⚒️", "Kam? Na tržnico! (-a → -o)", reward = 70)
    val imetiIti = tutor("imeti-in-iti", 1, "Učiteljica Mojca", "👩‍🏫", "Imeti in iti: imam, grem")
    val tozilnik = tutor("tozilnik-osnove", 1, "Učiteljica Mojca", "👩‍🏫", "Tožilnik: vidim brata, pijem kavo")
    val dvojina = tutor("dvojina-osnove", 2, "Učiteljica Mojca", "👩‍🏫", "Dvojina: dva brata, dve sestri")
    val podkev = Quest(
        "q-2026-09-29-1", "Kovač Tone", "⚒️", "Podkev za konja · A horseshoe for the horse", "Tone kuje podkev. · Tone is forging a horseshoe.",
        Res.STONE, mapOf(Res.STONE to 48, Res.WOOD to 16), cardIds = listOf("vocab_ampak", "vocab_hvala", "vocab_prosim", "vocab_zivjo", "vocab_dober_dan"),
        expiresAt = noonOf(LocalDate.of(2026, 10, 2)), grammar = "naslonke",
    )
    val zgodbe = Quest(
        "q-2026-09-30-0", "Stari Janez", "👴", "Janezove zgodbe · Janez's stories", "Stari Janez pripoveduje pod lipo. · Old Janez tells stories.",
        Res.WISDOM, mapOf(Res.WISDOM to 48, Res.WOOD to 16), cardIds = listOf("vocab_ampak", "vocab_hvala", "vocab_prosim", "vocab_zivjo", "vocab_dober_dan"),
        expiresAt = noonOf(LocalDate.of(2026, 10, 3)), grammar = "pretekli-cas",
    )

    /** The pages of each module, as the grammar book lists them (curated and the tutor's). */
    val pages: Map<String, Set<String>> = mapOf(
        "predlogi-kraja" to setOf("kam-tozilnik", "kje-mestnik-orodnik", "vprasalnice"),
        "kam-na-trznico" to setOf("kam-tozilnik", "kje-mestnik-orodnik", "tozilnik"),
        "rodilnik-po-nikalnici" to setOf("rodilnik-nikalnica", "rodilnik-predlogi"),
        "imeti-in-iti" to setOf("kam-tozilnik", "dvojina", "imeti-iti", "rodilnik-nikalnica"),
        "tozilnik-osnove" to setOf("tozilnik"),
        "dvojina-osnove" to setOf("dvojina", "stevila-samostalniki"),
    )

    /** The short drills (tagged "short"). */
    val short = setOf("kam-na-trznico")

    /** The quests as they stood, "V ali na" with its three tries (5/12, 7/12, 6/12), before any link was found. */
    val quests: List<Quest> = listOf(
        tutor("biti-sedanjik", 1, "Učiteljica Mojca", "👩‍🏫", "Biti: sem, si, je", done = true),
        imetiIti,
        QuestQueue.tried(QuestQueue.tried(QuestQueue.tried(vAliNa, 5, 12), 7, 12), 6, 12),
        rodilnik,
        tozilnik,
        dvojina,
        tutor("clitic-se-basics", 5, "Pastir Luka", "🐑", "Where does se go?", done = true),
        tutor("dober-dobra-dobro", 2, "Babica Micka", "👵", "Dober, dobra, dobro: pri babici", done = true),
        tutor("ime-mi-je", 1, "Babica Micka", "👵", "Ime mi je Jan: ime or imam?", done = true),
        podkev,
        zgodbe,
        naTrznico,
    )

    /** The Primorska village: the smithy, the school, the linden; nobody named yet (everyone counts as here). */
    val state = GameState(
        seed = 11, age = Age.VAS,
        buildings = listOf(
            Building("b1", BuildingType.TENT, 0), Building("b2", BuildingType.HUT, 1), Building("b3", BuildingType.SMITHY, 2),
            Building("b4", BuildingType.SCHOOL, 3), Building("b5", BuildingType.LIPA, 4),
        ),
        quests = quests,
    )

    /** [state] with the links the app finds (the short drill "Kam? Na tržnico!" helps "V ali na"). */
    val linked: GameState get() = state.copy(quests = QuestQueue.link(quests, { pages[it].orEmpty() }, short))

    fun noonOf(d: LocalDate): Long = Fixtures.noon(d)
}
