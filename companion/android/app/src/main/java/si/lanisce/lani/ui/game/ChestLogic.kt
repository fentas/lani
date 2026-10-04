package si.lanisce.lani.ui.game

import si.lanisce.lani.game.Chest
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Help
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.bi
import java.time.LocalDate

// Pure helpers behind "Skrinja · The chest" (companion/GAME.md): who can get a good, what a moba is like. A friend's
// next gift isn't one of them: it comes as a surprise (Jan).

/** Someone who lives here and likes a good: [blocked] says why it can't be given to them today, null when it can. */
data class Liker(val id: String, val name: String, val emoji: String, val blocked: String?)

object ChestLogic {
    /** Who of [people] likes [good]: only those who live here (they're the ones Jan can give it to). */
    fun likers(s: GameState, good: String, people: List<Villager>, today: LocalDate): List<Liker> =
        people.filter { v -> Chest.likes(v.id).any { it.id == good } }
            .map { v -> Liker(v.id, shortName(v.name), v.emoji, Chest.giveBlocker(s, good, v.id, today)) }

    /** "Luka" of "Pastir Luka". */
    private fun shortName(name: String) = name.trim().substringAfterLast(' ')

    /**
     * What a moba is like in this village, for the build sheet's switch: "5 sosedov · 5 neighbours: do 50 % 🪵 🪨 ·
     * up to half the wood and stone; 1 🤝 = 8 🪵/🪨", or why there's none.
     */
    fun mobaText(s: GameState): String {
        val helpers = Help.helpers(s)
        if (helpers <= 0) return bi("chestLogic.noNeighboursHelpYet")
        val share = (Help.share(helpers) * 100).toInt()
        val per = si.lanisce.lani.game.Catalog.mobaPerHelp(s.age)
        return "${bi("chestLogic.neighbours", "n" to helpers)}: ${bi("chestLogic.upToWoodStone", "share" to share)}; 1 🤝 = $per 🪵/🪨"
    }
}
