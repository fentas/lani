package si.lanisce.lani.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import si.lanisce.lani.data.ModuleInfo
import si.lanisce.lani.data.PackInfo
import si.lanisce.lani.data.PersonVoice
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.data.Bridge
import si.lanisce.lani.data.OutboxEntry
import si.lanisce.lani.data.Writes
import si.lanisce.lani.data.Scenario
import si.lanisce.lani.data.json
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Quest
import kotlinx.serialization.json.JsonObject
import si.lanisce.lani.game.villagers.Arrivals
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Meeting
import si.lanisce.lani.game.villagers.Memory
import si.lanisce.lani.game.villagers.Mentions
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Arrival
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.parseVillagers
import si.lanisce.lani.ui.villagers.FriendGain
import si.lanisce.lani.ui.villagers.GiftMoment
import si.lanisce.lani.ui.villagers.GiftTalk
import si.lanisce.lani.ui.villagers.VillagerLogic
import si.lanisce.lani.ui.villagers.hearts
import java.io.File
import java.time.LocalDate

/**
 * An introduction being played ([VillagerController.meet], companion/VILLAGERS.md "Arrivals"): who speaks and what the
 * newcomer keeps of it ([meeting]), and its dialog as a scene's is played ([talk]: the newcomer is its person).
 */
data class MeetingTalk(val meeting: Meeting, val talk: SceneTalk)

/**
 * The people of the village (companion/VILLAGERS.md): the cast from the node (cached, so the register
 * works offline), and Jan's friendship with each (kept in the village state, [GameState.bonds]): who
 * gets how much for what, and the notice when a friendship reaches a new level.
 */
class VillagerController(
    context: Context,
    private val scope: CoroutineScope,
    private val bridge: () -> Bridge?,
    private val notices: Notices,
    private val game: GameController,
    /** Sends a message to the tutor through the outbox (see [SyncController.submitLater]). */
    private val post: (OutboxEntry) -> Unit = {},
    /** The learner's level in a language ("sl" → "A1"), for the level an introduction is played at. */
    private val level: (String) -> String = { "A1" },
) {
    private val app = context.applicationContext
    private val cache = File(context.filesDir, "villagers.json")
    private val arrivalsCache = File(context.filesDir, "arrivals.json")
    private val io = Dispatchers.IO.limitedParallelism(1) // cache writes stay in order

    /** The tutor's introductions of people who joined the village, by resident id (GET /arrivals; cached). */
    var tutorArrivals by mutableStateOf<Map<String, JsonObject>>(emptyMap())
        private set

    private var everyone by mutableStateOf<List<Villager>>(emptyList())

    /** Every villager the node knows; also whose names the village's texts may say ([Mentions.cast]). */
    var all: List<Villager>
        get() = everyone
        private set(value) {
            everyone = value
            Mentions.cast = value
        }

    /** [all] came from the node (or its cache), not the debug fixture: the node knows the villagers. */
    private var served = false

    /** The villager whose talk with the tutor is being prepared (the scenario is on its way). */
    var opening by mutableStateOf<String?>(null)
        private set

    /** The friendship level each page last showed, so a level reached meanwhile gets its hearts. */
    private val levelShown = HashMap<String, Int>()

    init {
        scope.launch {
            val cached = withContext(io) { runCatching { parseVillagers(cache.readText()) }.getOrNull() }
            if (all.isEmpty()) {
                served = !cached.isNullOrEmpty()
                all = cached?.takeIf { it.isNotEmpty() } ?: fixture()
                game.settleResidents()
            }
            val arrivals = withContext(io) { runCatching { Arrivals.parseServed(arrivalsCache.readText()) }.getOrNull() }
            if (tutorArrivals.isEmpty() && arrivals != null) tutorArrivals = arrivals
        }
    }

    suspend fun reload(b: Bridge) {
        val list = try {
            b.villagers()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // offline, or an older bridge without villagers: keep what we have
        }
        if (!list.isNullOrEmpty()) {
            served = true
            all = list
            scope.launch(io) { runCatching { write(list) } }
        } else if (all.isEmpty()) {
            all = fixture()
        }
        game.settleResidents() // the cast decides who lives here
        reloadArrivals(b)
    }

    /** The tutor's introductions (GET /arrivals); an older bridge (or none right now) keeps what we have. */
    suspend fun reloadArrivals(b: Bridge? = bridge()) {
        val raw = try {
            b?.arrivals()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return
        tutorArrivals = Arrivals.parseServed(raw)
        scope.launch(io) { runCatching { writeAtomically(arrivalsCache, Arrivals.served(tutorArrivals)) } }
    }

    // --- arrivals: meeting someone who joined the village (companion/VILLAGERS.md "Arrivals") -------------------------

    /**
     * The turns of an introduction (its dialog, in a language) that test the learner's own words ([DialogWordsController.words];
     * companion/SCENES.md, "Your words in the dialogs"); set by the view model, none until then.
     */
    var dialogWords: (si.lanisce.lani.game.scene.Dialog, String) -> Map<Int, si.lanisce.lani.game.TurnWords> = { _, _ -> emptyMap() }

    /** The introduction being played (ArrivalScene); null when none. */
    var meeting by mutableStateOf<MeetingTalk?>(null)
        private set

    /** Whether the learner has met [id] ([Arrivals.met]; everyone in a village that introduces nobody). */
    fun met(state: GameState?, id: String): Boolean = state == null || Arrivals.met(state, id)

    /** Whether [id] lives here and waits to be met (their introduction, or someone's they wait for, can be played). */
    fun waitsToMeet(state: GameState?, id: String): Boolean =
        state != null && livesHere(state, id) && !Arrivals.met(state, id) && Arrivals.next(state, id, LocalDate.now()) != null

    /**
     * Meets [id] (a bubble at their home, the scroll's Today, their card): their introduction opens, at the learner's
     * level, in the village's language; or, while they wait for someone who introduces them, that one's first. False when
     * there is none to play.
     */
    fun meet(id: String): Boolean {
        val s = game.state ?: return false
        val today = LocalDate.now()
        val p = Arrivals.next(s, id, today) ?: return false
        val lang = si.lanisce.lani.game.culture.Cultures.home.manifest.language
        val m = Arrivals.meeting(p, s, people(s, today), level(lang), today, tutorArrivals) ?: return false
        val v = byId(p.id, s)
        val person = si.lanisce.lani.game.scene.ScenePerson(
            id = p.id, name = v?.name ?: p.id, emoji = v?.emoji ?: p.emoji, art = v?.art ?: "woman", slot = "", villager = p.id,
        )
        val happening = si.lanisce.lani.game.scene.Happening(id = "arrival", title = Arrivals.title(p, v), who = p.id, marker = p.emoji)
        val run = si.lanisce.lani.ui.scene.DialogRun.start(m.dialog, p.id, seed = kotlin.random.Random.nextLong(), repliers = m.repliers, words = dialogWords(m.dialog, lang))
        meeting = MeetingTalk(m, SceneTalk("arrival:${p.id}", "arrival:${p.id}", person, happening, m.dialog, run))
        settleMeeting()
        return true
    }

    fun meetChoose(k: Int) = stepMeeting { it.choose(k) }

    fun meetNext() = stepMeeting { it.next() }

    /** "📖 Namig · Hint" opened at the learner's turn ([DialogRun.hint]). */
    fun meetHint() = stepMeeting { it.hint() }

    /** Leaves the introduction; before its end nothing changes: they still wait to be met. */
    fun closeMeeting() {
        meeting = null
    }

    private fun stepMeeting(f: (si.lanisce.lani.ui.scene.DialogRun) -> si.lanisce.lani.ui.scene.DialogRun) {
        val m = meeting ?: return
        meeting = m.copy(talk = m.talk.copy(run = f(m.talk.run)))
        settleMeeting()
    }

    /** At the end: they're met, the friendship starts (a dialog's points), and they keep the day as their first memory. */
    private fun settleMeeting() {
        val m = meeting ?: return
        val t = m.talk
        if (t.settled || t.run.step != si.lanisce.lani.ui.scene.DialogRun.Step.END) return
        val points = if (t.run.mistakes == 0) Bonds.DIALOG else Bonds.DIALOG_WITH_MISTAKES
        val gain = befriend(m.meeting.id, points, m.meeting.memory, announce = false, meet = true)
        meeting = m.copy(talk = t.copy(settled = true, friend = gain))
    }

    /**
     * Everyone: the cast, and the people born here or who moved in ([Residents]), as villagers. Cast
     * members come first; whether someone lives here is [livesHere].
     */
    fun people(state: GameState?, today: LocalDate = LocalDate.now()): List<Villager> =
        all + (state?.let { s -> Residents.people(s, all, today).filter { p -> all.none { it.id == p.id } } }.orEmpty())

    fun livesHere(state: GameState?, id: String): Boolean = state?.residents?.any { it.id == id } == true

    /** Whether the person called [name] is in the village today ([Residents.here]): the cast only once they live here. */
    fun here(state: GameState?, name: String, today: LocalDate = LocalDate.now()): Boolean =
        state == null || Residents.here(state, name, people(state, today), today)

    /** [m] as the lists show it: a villager's request only while they're in the village, else just the tutor's module. */
    fun shown(m: ModuleInfo, state: GameState?): ModuleInfo = if (m.quest == null || here(state, m.quest.giver)) m else m.copy(quest = null)

    /** [p] as the lists show it: its giver only while they're in the village. */
    fun shown(p: PackInfo, state: GameState?): PackInfo = if (p.giver == null || here(state, p.giver.name)) p else p.copy(giver = null)

    /**
     * Who lives in the village, as the node's voice profiles need them ([si.lanisce.lani.data.Clips.syncProfiles]):
     * each with their gender, their speaker (archetype) and age; babies don't speak yet.
     */
    fun voicePeople(state: GameState?, today: LocalDate = LocalDate.now()): List<PersonVoice> {
        val s = state ?: return emptyList()
        return s.residents.mapNotNull { r ->
            val v = byId(r.id, s) ?: return@mapNotNull null
            val stage = Residents.stage(r, today)
            if (v.art == "baby" || stage == Residents.Stage.BABY) return@mapNotNull null
            val age = when (stage) { Residents.Stage.CHILD -> "child"; Residents.Stage.YOUTH -> "youth"; Residents.Stage.ADULT -> "adult"; else -> null }
            PersonVoice(v.id, v.name, v.voice, v.speaker ?: v.voice, age, cast = all.any { it.id == v.id }, order = v.order)
        }
    }

    /** Who comes next and what they're waiting for (room, a building). */
    fun outlook(state: GameState?, today: LocalDate = LocalDate.now()): List<Arrival> =
        state?.let { Residents.outlook(it, all, today) }.orEmpty()

    /** A cast member, or someone born here or who moved in (needs the village state for the latter). */
    fun byId(id: String, state: GameState? = game.state): Villager? = all.firstOrNull { it.id == id }
        ?: state?.residents?.firstOrNull { it.id == id }?.let { Residents.villagerOf(it, all, LocalDate.now()) }

    fun byName(name: String): Villager? = all.firstOrNull { it.name == name }

    fun bond(state: GameState?, id: String): Bond = state?.bonds?.get(id) ?: Bond()

    fun level(state: GameState?, id: String): Int = Bonds.level(bond(state, id).points)

    /**
     * Jan helped, met or trained with villager [id]: the friendship grows by [points] (see [Bonds] for the
     * amounts), and they may keep a [memory]. Unknown ids are fine (a tutor's villager not loaded yet).
     * A new level shows as a notice ("♥ Luka: Znanec · Acquaintance") unless [announce] is false (a
     * result card shows it). Returns the change, or null when the village isn't loaded.
     */
    fun befriend(id: String, points: Int, memory: Memory? = null, training: Boolean = false, announce: Boolean = true, meet: Boolean = false): FriendGain? = notices.village {
        val before = game.state ?: return@village null
        game.befriend(id, points, memory, training, meet)
        val gain = VillagerLogic.gain(id, byId(id), before, game.state, memory) ?: return@village null
        // a gift is the bigger news: "🪓 Luka ti podari sekiro · Luka gives you an axe: +10 % 🪵"
        if (announce && gain.leveledUp) notices.banner = gain.gifts.firstOrNull()?.let { "${it.item.emoji} ${it.text}" } ?: gain.levelText
        gain
    }

    /**
     * Jan gives [good] from the chest to villager [id]: ♥ by what it is to them ([si.lanisce.lani.game.Chest.giftPoints];
     * once a day each), and they remember it. The change shows as a notice unless [announce] is false (the gift moment
     * shows it); null when it couldn't be given.
     */
    fun give(good: String, id: String, announce: Boolean = true): FriendGain? = notices.village {
        val before = game.state ?: return@village null
        val g = si.lanisce.lani.game.Catalog.goods[good] ?: return@village null
        val v = byId(id)
        val memory = VillagerLogic.giftMemory(g, v, LocalDate.now())
        if (!game.give(good, id, memory)) return@village null
        val gain = VillagerLogic.gain(id, v, before, game.state, memory) ?: return@village null
        if (announce) notices.banner = gain.gifts.firstOrNull()?.let { "${it.item.emoji} ${it.text}" }
            ?: if (gain.leveledUp) gain.levelText else "${g.name.emoji} ${gain.short}: hvala za ${g.name.acc}! ${hearts(gain.plus)}"
        gain
    }

    /** The gift moment on screen (GiftScene, companion/VILLAGERS.md "Giving a good"); null when none. */
    var gift by mutableStateOf<GiftMoment?>(null)
        private set

    /**
     * Jan means to give [good] to villager [id] (from the chest, or their page): the gift moment opens. When it can't be
     * given today (they got something today already), no moment: the reason shows as a notice, kindly.
     */
    fun offer(good: String, id: String) {
        val s = game.state ?: return
        val v = byId(id, s) ?: return
        val today = LocalDate.now()
        // someone who lives here and hasn't been met yet: their introduction comes first
        if (livesHere(s, id) && !Arrivals.met(s, id) && meet(id)) return
        GiftTalk.blocker(s, good, id, today)?.let {
            notices.banner = "${v.emoji} ${VillagerLogic.shortName(v.name)}: $it"
            return
        }
        gift = GiftTalk.start(s, v, good, today)
    }

    /**
     * Jan says choice [k] of the gift moment: the wrong register says why; the right words hand the gift over, and they
     * answer in their words (the friendship grows, a level's gift of theirs comes with it).
     */
    fun say(k: Int) {
        val m = gift ?: return
        val next = m.pick(k)
        if (!next.handed || m.handed) {
            gift = next
            return
        }
        val gain = give(m.good, m.villager, announce = false)
        if (gain == null) { // gone meanwhile (sold, given): the moment ends
            gift = null
            return
        }
        gift = next.copy(reply = GiftTalk.reply(byId(m.villager), m.reaction, gain.level, LocalDate.now()), gain = gain)
    }

    /** The gift moment is closed (before Jan handed it over, nothing was given). */
    fun closeGift() {
        gift = null
    }

    /** A villager's quest is done ([Quest.giver] is their name): +10, and they remember it in their words. */
    fun questDone(q: Quest): FriendGain? {
        val v = byName(q.giver) ?: return null
        return befriend(v.id, Bonds.QUEST, VillagerLogic.questMemory(q, v, LocalDate.now()), announce = false)
    }

    /**
     * A scene's dialog is done with a person who is a villager (their `villager` id, else their name in the
     * cast): +5, +3 with mistakes, and the happening's memory. Not again when it was done today already.
     */
    fun sceneDone(t: SceneTalk): FriendGain? {
        if (t.paid?.again == true) return null
        val id = t.person.villager ?: byName(t.person.name)?.id ?: return null
        val points = if (t.run.mistakes == 0) Bonds.DIALOG else Bonds.DIALOG_WITH_MISTAKES
        val memory = t.happening.memory?.let { Memory(LocalDate.now().toString(), it.sl, it.en, "dialog") }
        return befriend(id, points, memory, announce = false)
    }

    /**
     * A role-play with villager [id] ended (+6 ♥ and [si.lanisce.lani.game.Catalog.HELP_TALK] 🤝: talking with
     * someone is helping them), or its debrief brought a [memory] (no points).
     */
    fun talked(id: String, points: Int, memory: Memory?): FriendGain? {
        val gain = befriend(id, points, memory, announce = false) ?: return null
        if (points <= 0) return gain
        game.helped(si.lanisce.lani.game.Catalog.HELP_TALK)
        return gain.copy(help = si.lanisce.lani.game.Catalog.HELP_TALK)
    }

    /**
     * Someone new moved in or was born ([Residents]): the tutor hears of it (`villager_arrived`) and may
     * give them a personality and their own lines. Only a node that serves villagers is told.
     */
    fun arrived(people: List<Resident>) {
        if (!served || all.isEmpty()) return
        for (r in people) VillagerLogic.arrival(r)?.let { (text, data) -> post(Writes.message(VillagerLogic.ARRIVED, "main", text, data)) }
    }

    /**
     * Jan greeted [id] (the scripted greeting): they count as seen today, and as met in a village that doesn't introduce
     * its people (with arrivals only the introduction meets them: [meet]).
     */
    fun greeted(id: String) {
        // 0 points, but seen today: a visitor who was greeted has no bubble left (TownMarkers)
        befriend(id, 0, announce = false)
    }

    /** True once when [level] is above the level the page showed for [id] last time: time for hearts. */
    fun levelUpSince(id: String, level: Int): Boolean {
        val seen = levelShown.put(id, level)
        return seen != null && level > seen
    }

    /**
     * The role-play with [id] played by the tutor, built by the node for the friendship now
     * (`GET /villagers/:id/scenario`). Debug builds make one on the phone while the node can't.
     */
    suspend fun scenario(id: String): Scenario {
        opening = id
        try {
            val b = bridge() ?: throw java.io.IOException("not connected")
            return try {
                b.villagerScenario(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val v = byId(id)
                if (!BuildConfig.DEBUG || v == null) throw e
                VillagerLogic.localScenario(v, bond(game.state, id), LocalDate.now())
            }
        } finally {
            opening = null
        }
    }

    private fun write(list: List<Villager>) = writeAtomically(cache, json.encodeToString(ListSerializer(Villager.serializer()), list))

    private fun writeAtomically(file: File, text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    /** Debug builds only: a cast to try the screens with while the node has none (app/src/debug/assets). */
    private suspend fun fixture(): List<Villager> {
        if (!BuildConfig.DEBUG) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching { app.assets.open(FIXTURE).bufferedReader().use { parseVillagers(si.lanisce.lani.l10n.Learner.current.renderJson(it.readText())) } }.getOrDefault(emptyList())
        }
    }

    private companion object {
        const val FIXTURE = "villagers-fixture.json"
    }
}
