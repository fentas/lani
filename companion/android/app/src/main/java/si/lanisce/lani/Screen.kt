package si.lanisce.lani

import si.lanisce.lani.data.Module
import si.lanisce.lani.data.Pack
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Challenge as GameChallenge

sealed interface Screen {
    data object Loading : Screen
    data object Setup : Screen
    data object Home : Screen
    /**
     * Flashcard reviews of [cards]: the due ones, or ([polish]) rusty words polished now. [run] tells one run from
     * the next, also when the same cards are polished again. [language]: the deck of another language than the home
     * one (practised on visits); null for the home language's.
     */
    data class Review(val cards: List<ReviewCard>, val polish: Boolean = false, val run: Int = 0, val language: String? = null) : Screen
    data class Player(val module: Module) : Screen
    /** "🎯 Vadi · Practise" on grammar page [page]: its exercises or cards ([practice]); [run] tells one run from the next. */
    data class GrammarPractice(val page: String, val practice: si.lanisce.lani.data.Grammar.Practice, val run: Int = 0) : Screen
    data object Village : Screen
    /** A village challenge: intro, timed run and result. */
    data class Challenge(val origin: ChallengeOrigin, val challenge: GameChallenge) : Screen
    /** Word packs ("Nove besede"); back leads to the village when opened from there. */
    data class Packs(val fromVillage: Boolean = false) : Screen
    /** "📖 Branje · Reading", the reading corner (ui/reading); back leads to the village when opened from there (its book, its chest). */
    data class Readings(val fromVillage: Boolean = false) : Screen
    /**
     * Reading [id] read aloud ("🎤 Beri na glas · Read aloud", ui/reading/ReadAloudScreen); back leads to the reading
     * corner ([fromCorner]), else to the village (a chest's reading) or Home.
     */
    data class ReadAloud(val id: String, val fromVillage: Boolean = false, val fromCorner: Boolean = true) : Screen
    /** Introduce [words] of [pack], practise them, save them; back leads to [scene] when learning a scene's words. */
    data class PackLearn(val pack: Pack, val words: List<PackWord>, val fromVillage: Boolean = false, val scene: String? = null) : Screen
    /**
     * "🎯 Vadi jih zdaj · Practise them now": the words building [building]'s upgrade to [toLevel] still asks for, learned
     * and polished in one short run ([run]; companion/GAME.md, "Upgrades"); back leads to the building's card. [n] tells
     * one run from the next.
     */
    data class UpgradeWords(val building: String, val toLevel: Int, val run: si.lanisce.lani.game.UpgradeRun, val n: Int = 0) : Screen
    /** "Pogovor · Talk": role-play scenes; which part shows is up to [AppViewModel.talk]. */
    data object Talk : Screen
    /** "Napredek · Progress"; back leads to the village when opened from there. */
    data class Progress(val fromVillage: Boolean = false) : Screen
    /** A family challenge to answer, or (null) the family's recording mode. */
    data class Family(val challengeId: String? = null) : Screen
    /**
     * A close-up scene of the village; [focus] is a happening key or an object slot to start with; [building] the one it
     * was opened from (the hut or the house tapped: its room, at its level), null for its place's.
     */
    data class Scene(val id: String, val focus: String? = null, val building: String? = null) : Screen
    /** "Prebivalci · Villagers": the register of the village's people. */
    data object Villagers : Screen
    /** One villager: their story, the friendship, their memories, talking to them. */
    data class Villager(val id: String) : Screen
    /** "Prijatelji · Friends": the towns linked with this one, invitations (ui/towns). */
    data object Friends : Screen
    /** Linked town [id], visited: its village read-only, in its language and look (ui/towns/VisitScreen). */
    data class Visit(val id: String) : Screen
    /** Place [scene] of visited town [town]: its words to tap, a guest's dialog (ui/towns/VisitSceneScreen). */
    data class VisitScene(val town: String, val scene: String) : Screen
    /** Request [request] of visited town [town]: its run in the town's language (ui/towns/VisitActs). */
    data class VisitRequest(val town: String, val request: String) : Screen
    /**
     * A question between towns ([key]; ui/towns/TownQuestionScreens): a guest's to the learner, to answer in their town's
     * language, or ([asked]) the learner's to another town, with the tutor's feedback and the answer.
     */
    data class TownQuestion(val key: String, val asked: Boolean) : Screen
    /**
     * "🗺️ Zemljevid zaklada · The treasure map" (ui/game/TreasureScreen, companion/GAME.md "The treasure map"): the
     * storyteller's offer, the path and its stations, the treasure; back leads to the village when opened from there.
     */
    data class Treasure(val fromVillage: Boolean = false) : Screen
}

/** An exercise run: leaving it now would lose the answers so far. */
val Screen.inRun: Boolean
    get() = this is Screen.Review || this is Screen.Player || this is Screen.Challenge || this is Screen.PackLearn || this is Screen.VisitRequest ||
        this is Screen.GrammarPractice || this is Screen.UpgradeWords

/** What a village challenge answers to. */
sealed interface ChallengeOrigin {
    data object Event : ChallengeOrigin
    data class Gather(val res: Res) : ChallengeOrigin
    data class Quest(val questId: String) : ChallengeOrigin
    /** Today's step of a village project (see game/Projects). */
    data class Project(val id: String) : ChallengeOrigin
    /** A calendar festival that is on (see game/Calendar). */
    data class Festival(val id: String) : ChallengeOrigin
    /** Today's surprise at the road (see game/Surprises). */
    data object Surprise : ChallengeOrigin
    /** A station of the treasure hunt ([station]: its id, see game/Treasure). */
    data class Treasure(val station: String) : ChallengeOrigin
}

enum class Link { CONNECTING, ONLINE, OFFLINE }
