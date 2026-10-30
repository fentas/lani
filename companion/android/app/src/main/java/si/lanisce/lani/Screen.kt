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
    /**
     * "🎯 Vadi · Practise" on grammar page [page]: its exercises or cards ([practice]); [run] tells one run from the next.
     * [forQuest]: the drill of a request tried below the mark ("🎯 Vadi in poskusi znova"): passed, it brings the request back
     * today ([si.lanisce.lani.game.RequestWindow.retried]).
     */
    data class GrammarPractice(val page: String, val practice: si.lanisce.lani.data.Grammar.Practice, val run: Int = 0, val forQuest: String? = null) : Screen
    data object Village : Screen
    /** A village challenge: intro, timed run and result. */
    data class Challenge(val origin: ChallengeOrigin, val challenge: GameChallenge) : Screen
    /** Word packs ("📚 Nove besede · New words"). */
    data object Packs : Screen
    /** "📗 Branje · Reading", the reading corner (ui/reading). */
    data object Readings : Screen
    /** Reading [id] read aloud ("🎤 Beri na glas · Read aloud", ui/reading/ReadAloudScreen). */
    data class ReadAloud(val id: String) : Screen
    /** Introduce [words] of [pack], practise them, save them; [scene]: the scene whose words they are ("📚 Nauči se teh besed"). */
    data class PackLearn(val pack: Pack, val words: List<PackWord>, val scene: String? = null) : Screen
    /**
     * "🎯 Vadi jih zdaj · Practise them now": the words building [building]'s upgrade to [toLevel] still asks for, learned
     * and polished in one short run ([run]; companion/GAME.md, "Upgrades"); back leads to the building's card. [n] tells
     * one run from the next.
     */
    data class UpgradeWords(val building: String, val toLevel: Int, val run: si.lanisce.lani.game.UpgradeRun, val n: Int = 0) : Screen
    /** "Pogovor · Talk": role-play scenes; which part shows is up to [AppViewModel.talk]. */
    data object Talk : Screen
    /** "📊 Napredek · Progress". */
    data object Progress : Screen
    /** "🔤 Moje besede · My words" (ui/words/MyWordsScreen): the learner's words and a dictionary lookup. */
    data object MyWords : Screen
    /** A family challenge to answer, or (null) the family's recording mode. */
    data class Family(val challengeId: String? = null) : Screen
    /**
     * A close-up scene of the village; [focus] is a happening key or an object slot to start with; [building] the one it
     * was opened from (the hut or the house tapped: its room, at its level), null for its place's; [from] the scene it was
     * gone into from through a thing of its picture ([si.lanisce.lani.game.scene.SceneDoors]: the mill from the stream),
     * which it lies on and Back returns to, null when the village opened it (beside the other scenes, [destination]).
     */
    data class Scene(val id: String, val focus: String? = null, val building: String? = null, val from: String? = null) : Screen
    /** "Prebivalci · Villagers": the register of the village's people. */
    data object Villagers : Screen
    /** One villager: their story, the friendship, their memories, talking to them. */
    data class Villager(val id: String) : Screen
    /** "Prijatelji · Friends": the towns linked with this one, invitations (ui/towns). */
    data object Friends : Screen
    /** "📈 Napredek · Progress" of linked town [id]: what its learner shares of their learning with this one (ui/towns/TownProgressScreen). */
    data class TownProgress(val id: String) : Screen
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
     * storyteller's offer, the path and its stations, the treasure.
     */
    data object Treasure : Screen
    /** "🏫 Šola · School" (ui/school, companion/GAME.md "The school"): the course's levels and units, the classroom under the linden. */
    data object School : Screen
    /** Unit [id] of the school's course (ui/school/UnitScreen): its intro, worksheets, test and homework. */
    data class CourseUnit(val id: String) : Screen
    /** Unit [id]'s guided introduction (ui/school/IntroScreen): steps with a quick question each, resumable. */
    data class UnitIntro(val id: String) : Screen
    /** A sheet of unit [unit]: a worksheet, its test or homework ([kind], [sheet]); [run] tells one run from the next. */
    data class Sheet(val unit: String, val kind: si.lanisce.lani.app.SheetKind, val sheet: String, val run: Int = 0) : Screen
    /**
     * "🔢 Števila · Numbers" (ui/numbers/NumbersScreen, data/NumbersPractice): the ranges, the ways of asking, a run to start;
     * closed, what opens it and the way there.
     */
    data object Numbers : Screen
    /** A run of the numbers module: its [tasks]; [run] tells one run from the next. */
    data class NumbersRun(val tasks: List<si.lanisce.lani.data.NumberTask>, val run: Int = 0) : Screen
    /**
     * "🧩 Skloni · Cases" (ui/cases/CasesScreen, data/CaseTrainer): each case's and rule's mastery, a session to start;
     * closed, what opens it. [focus]: the case or the rule a session is about (opened from its grammar page, a case unit,
     * Home's suggestion or the tutor's assignment), null for a mixed one.
     */
    data class Cases(val focus: String? = null) : Screen
    /**
     * A session of the case trainer: its tasks with their exercises, its [focus]; [storm]: "⚡ Sklonski vihar · Case storm"
     * (phrases sorted into the six cases against the clock); [run] tells one session from the next.
     */
    data class CasesRun(val planned: List<si.lanisce.lani.data.CasePlanned>, val focus: String? = null, val run: Int = 0, val storm: Boolean = false) : Screen
    /**
     * "⚙️ Nastavitve · Settings" (ui/settings/SettingsScreen): its sections ([section] null), or one of them as a page of its
     * own ([SettingsSection]'s ids: "tutor", "data", "languages", "sound", "connection", "developer").
     */
    data class Settings(val section: String? = null) : Screen
    /** "📖 Slovnica · Grammar": the grammar book's index (ui/grammar/GrammarBookScreen), its pages opened over it. */
    data object GrammarBook : Screen
}

/** An exercise run: leaving it now would lose the answers so far. */
val Screen.inRun: Boolean
    get() = this is Screen.Review || this is Screen.Player || this is Screen.Challenge || this is Screen.PackLearn || this is Screen.VisitRequest ||
        this is Screen.GrammarPractice || this is Screen.UpgradeWords || this is Screen.Sheet || this is Screen.NumbersRun || this is Screen.CasesRun

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
    /** "⇄ Zamenjaj · Trade": [amount] of [give] for [get], after a short run of both skills (see game/Trade). */
    data class Trade(val give: Res, val get: Res, val amount: Int) : ChallengeOrigin
}

enum class Link { CONNECTING, ONLINE, OFFLINE }
