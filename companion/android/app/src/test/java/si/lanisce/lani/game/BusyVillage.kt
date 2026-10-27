package si.lanisce.lani.game

import si.lanisce.lani.game.villagers.Villager
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A busy village shaped like Jan's on 27 October 2026 (what the coordinator found in their state): thirteen open requests,
 * twelve of them the tutor's (Učiteljica Mojca three, Babica Micka two, Stari Janez two, Čebelar Anton two, Kovač Tone,
 * Mlinar France and Pastir Luka one each) and Tone's own "Podkev za konja"; four of the tutor's tried once below the mark
 * (3 to 7 right of 10 or 12). Everyone counts as here (nobody named yet), as in [JanRequests].
 */
internal object BusyVillage {
    val zone: ZoneId = ZoneOffset.UTC
    val today: LocalDate = LocalDate.of(2026, 10, 27)

    fun at(d: LocalDate, hour: Int, minute: Int = 0): Long = d.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    val cast = listOf(
        Villager("mojca", "Učiteljica Mojca", "👩‍🏫", "teacher", home = listOf("school", "lipa")),
        Villager("micka", "Babica Micka", "👵", "grandma", home = listOf("house", "hut")),
        Villager("janez", "Stari Janez", "👴", "old-man", voice = "male", home = listOf("lipa")),
        Villager("anton", "Čebelar Anton", "🐝", "beekeeper", voice = "male", home = listOf("beehive", "forest")),
        Villager("tone", "Kovač Tone", "⚒️", "smith", voice = "male", home = listOf("smithy")),
        Villager("france", "Mlinar France", "👨‍🌾", "farmer", voice = "male", home = listOf("field")),
        Villager("luka", "Pastir Luka", "🐑", "shepherd", voice = "male", home = listOf("forest")),
        Villager("vida", "Gostilničarka Vida", "👩‍🍳", "innkeeper", home = listOf("market")),
    )

    private val emoji = cast.associate { it.name to it.emoji }

    fun tutor(module: String, giver: String, title: String, tries: Int = 0, best: Int = 0, of: Int = 10, since: String = "2026-10-20", helps: String? = null) = Quest(
        id = "tutor-$module-v1", giver = giver, emoji = emoji.getValue(giver), title = title, story = "$title · $title", skill = Res.STONE,
        reward = mapOf(Res.STONE to 60, Res.WISDOM to 10), source = QuestSource.TUTOR, moduleId = module, since = since,
        tries = tries, best = if (tries > 0) best else 0, bestOf = if (tries > 0) of else 0, helps = helps,
    )

    fun local(id: String, giver: String, title: String, cards: List<String> = listOf("vocab_hvala", "vocab_prosim", "vocab_zivjo", "vocab_ampak", "vocab_dober_dan"), since: String = today.toString()) = Quest(
        id, giver, emoji.getValue(giver), "$title · $title", "$title · $title", Res.FOOD, mapOf(Res.FOOD to 48, Res.WOOD to 16),
        cardIds = cards, expiresAt = at(LocalDate.parse(since).plusDays(Quests.LIFETIME_DAYS.toLong()), 12), since = since,
    )

    val quests: List<Quest> = listOf(
        tutor("dvojina-osnove", "Učiteljica Mojca", "Dvojina: dva brata, dve sestri", tries = 1, best = 5, since = "2026-10-18"),
        tutor("tozilnik-osnove", "Učiteljica Mojca", "Tožilnik: vidim brata", since = "2026-10-19"),
        tutor("imeti-in-iti", "Babica Micka", "Imeti in iti: imam, grem", tries = 1, best = 3, since = "2026-10-19"),
        tutor("predlogi-kraja", "Kovač Tone", "V ali na: v šoli, na pošti", tries = 1, best = 7, of = 12, since = "2026-10-20"),
        tutor("rodilnik", "Stari Janez", "Rodilnik: nimam časa", since = "2026-10-21"),
        tutor("pridevniki", "Čebelar Anton", "Med je sladek: pridevniki", since = "2026-10-22"),
        tutor("glagoli-gibanja", "Mlinar France", "Grem, peljem, nesem", tries = 1, best = 4, since = "2026-10-23"),
        tutor("stevila-mleko", "Babica Micka", "Dva litra mleka", since = "2026-10-24"),
        tutor("cas-ura", "Stari Janez", "Koliko je ura?", since = "2026-10-25"),
        tutor("cebele-mestnik", "Čebelar Anton", "V panju, na cvetu", since = "2026-10-25"),
        tutor("naslonke", "Učiteljica Mojca", "Kam gre se?", since = "2026-10-26"),
        tutor("ovce-mnozina", "Pastir Luka", "Ovce in jagnjeta", since = "2026-10-26"),
        local("q-2026-10-27-0", "Kovač Tone", "Podkev za konja"),
    )

    val state = GameState(
        seed = 27, age = Age.VAS,
        resources = mapOf(Res.FOOD to 200, Res.WOOD to 200, Res.STONE to 100, Res.WISDOM to 50),
        buildings = listOf(
            Building("b1", BuildingType.TENT, 0), Building("b2", BuildingType.HUT, 1), Building("b3", BuildingType.SMITHY, 2),
            Building("b4", BuildingType.SCHOOL, 3), Building("b5", BuildingType.LIPA, 4), Building("b6", BuildingType.FIELD, 5),
            Building("b7", BuildingType.BEEHIVE, 6), Building("b8", BuildingType.HOUSE, 7), Building("b9", BuildingType.MARKET, 8),
        ),
        quests = quests,
    )

    /** The window at [now] for [s] (this device's start [base]), nothing to practise known, no view. */
    fun window(s: GameState, now: Long, base: Int = RequestWindow.PHONE, practice: Practice = Practice.NONE) =
        QuestQueue.Window(now, s.pace, RequestWindow.size(base, s.pace, now), AgeNeeds.of(s, cast, practice), practice, zone = zone)

    fun queue(s: GameState, now: Long, base: Int = RequestWindow.PHONE, practice: Practice = Practice.NONE): QuestQueue.Queue {
        val day = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return si.lanisce.lani.game.villagers.Residents.queue(s, cast, day, window(s, now, base, practice))
    }

    /** What the app does when a run of [id] ends at [now]: the engine's pay and try, the window's record, the window kept. */
    fun play(s: GameState, id: String, correct: Int, total: Int, now: Long, base: Int = RequestWindow.PHONE): Pair<GameState, ChallengeResult> {
        val q = s.quests.single { it.id == id }
        val (n, r) = GameEngine.completeQuest(s, id, correct, total, now, today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
        val f = RequestWindow.finished(n, q, r.won, now, zone)
        return RequestWindow.settle(f, queue(f, now, base), now, zone) to r
    }
}
