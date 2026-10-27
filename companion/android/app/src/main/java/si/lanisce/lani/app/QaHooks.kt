package si.lanisce.lani.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import si.lanisce.lani.BuildConfig
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.game.render.TownClock
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.SceneSpec

/**
 * What the end-to-end QA (companion/bin/qa) asks of a debug build (or of the qa build: the release build's R8 with the
 * hooks, qa --minified) through the launch intent's [EXTRA]; a release build ignores it ([enabled] is [BuildConfig.QA]).
 * QA runs the app against its dev bridge, on a copy of the learner data.
 *
 * - "zoom": the camera on screen (the village's, a scene's) zooms in, as its "Približaj · Zoom in" action does: a double
 *   tap sent through adb comes too slow on a busy machine, and adb can't reach a TalkBack action.
 * - "happening:<scene>/<happening>": that happening counts as on ([withForced]), so its deep link ("scene:<scene>@<key>")
 *   opens its dialog whatever the hour, the day's dice or who has been met; "happening:<scene>/<happening>/<dialog>" with
 *   that variant of its dialogs, whatever was heard lately; "happening:" alone lets it go again.
 * - "road:<what>": asked of the road (the car's "🚗 Za pot"): "road:mini" gets a small library ready (a couple of items of
 *   each kind, in a minute), "road:car" browses and plays the sessions as Android Auto does (road/RoadCarCheck).
 * - "forms": a review of the word cards that have a form reached starts (due or not), and it and the next reviews ask
 *   a form of every such card, familiar or not (companion/GAME.md, "A word's forms"); "forms:off" ends it.
 * - "turns:type": the dialogs opened from now on ask every turn that tests a form typed into its gap, as if the learner
 *   had each rule secure ([typedTurns]; companion/SCENES.md, "Adaptive turns"): QA's typed turn with the keyboard up;
 *   "turns:" lets the learner's own masteries decide again.
 * - "mic:right": a dialog's 🎤 hears the turn's right choice when it is tapped, as the node's recognizer would give it (in
 *   small letters, without its punctuation), without listening ([micRight]): an emulator's microphone hears nothing, and
 *   QA checks a line said into the 🎤 isn't said again (companion/SCENES.md, "The learner's voice"); the 🎤 shows while it
 *   is set. "mic:" lets the microphone listen again.
 * - "bubbles:<n>": the village shows only the n bubbles nearest the fire ([fewer]), for a recording that isn't crowded;
 *   "bubbles:" shows them all again.
 * - "ispy:<scene>/<slot>": «Vidim, vidim» is offered in that scene whatever the day (games played, the hour: a child comes
 *   by if none is there), and its first round spies [slot]; the debug build logs where the round's things are on screen
 *   (tag ISpy), so QA taps them. "ispy:" lets it go again.
 * - "audio:<what>": the offline audio (companion/README.md, "Offline on the phone"): "audio:prefetch" makes the day's plan
 *   now and gets its clips at once; "audio:voice" downloads the offline voice; "audio:say:<text>" and "audio:slow:<text>"
 *   say a text as a 🔊 does (normal, 🐢), so QA hears the offline voice with the network off ("audio:say:<text>|<voice>|<person>"
 *   as a villager's line: that person in that voice, the debug build logging how it was said, tag Speaker); "audio:revoice:<text>|<url>"
 *   gives the text's narrator clip a new URL (url, else one the node hasn't got) as a re-voice on the node does, so its
 *   earlier clip stands in; "audio:refresh" takes the node's index again (a visited town's, on a visit). "audio:tts:off"
 *   has Android's text-to-speech count as having no voice for any language (a tablet without the visited town's; Speaker.qaNoTts),
 *   "audio:tts:" lets it be; "audio:visitvoice:reset" forgets the visits' offers of an offline voice declined, and lets this
 *   visit's note come again (app/VisitVoiceController), "audio:visitvoice:again" lets it come again keeping the declines (the
 *   🔇 note after a "Ne zdaj"); "audio:voice:remove:<lang>" takes that language's offline voice
 *   off the phone, as the settings' 🗑️ does.
 * - "render:full": the village's map draws its ground whole every frame, as it did before 2026-10-26, instead of keeping its
 *   still picture and drawing only its water anew ([si.lanisce.lani.game.render.VillageRenderer.groundKept]): QA measures
 *   the two (the frames' times, and the map's own: logged every 24 frames, tag TownScene); "render:" keeps it again.
 * - "clock:<f>": the towns' and scenes' time runs at f of the wall's (0.05 to 1; [TownClock.rate]), so a slow emulator
 *   renders every frame of a recording that is sped up again afterwards (with the animator duration scale at 1/f for
 *   the camera's flights and the bubbles' bob); "clock:" lets it run at its pace again.
 * - "step:<project>/<n>": the village (the dev bridge's copy) gets ready for step n of that project today ([readyStep]:
 *   its steps before it done, none today yet, the stores and the 🤝 it costs, a treat for a feast), and the projects sheet
 *   opens at it ([project]): QA plays it from there (companion/SCENES.md, "Project steps"); a step past its last
 *   ("step:most/7") has the project finished. "step:" lets it go again.
 * - "season:<day>": the landmarks with a season (the maypole, game/ProjectSeasons) stand or not as on that day
 *   ("2027-05-10", or "05-10": the next 10 May from today; [seasonDay]), on the map and in the scenes, without a line in
 *   the chronicle; the rest of the game keeps today. "season:" goes by today again.
 * - "hour:<h>": a scene is seen at that hour of today (0 to 24, "hour:7.5", to the second; [sceneNow]): its picture's light, sky and wild
 *   animals, who is there and who sleeps, without the real night sky; so a dawn's or a morning's cue looks as it does then,
 *   whatever the hour QA runs at. The village's map too ([mapNow]): its light, who is where and doing what (their day,
 *   what's on), the clock standing at that minute. "hour:" goes by the clock again.
 * - "build:<type>": the village (the dev bridge's copy) gets ready to build a building of that type ([readyBuild]: its
 *   stores full, a free plot, the next age's when its own are all taken) and the plot chooser opens for it over the map
 *   ([build], taken by the village: [tookBuild]), with its ★; QA looks at the HUD over it (its cards hidden) and goes back,
 *   nothing built. "build:" lets it go.
 * - "word:<word>|<line>|<en>": that word's card opens as a tap on it in that line (meant [en]) would, from a scene: QA's
 *   word the dictionary doesn't know, added from its card (companion/README.md, "Word lookup").
 * - "chat:<text>": a tutor message comes into the chat on the phone (as a reply would, not sent anywhere; "\n" in [text]
 *   a new line), "chat:table" the tutor's imeti with its list ([TUTOR_TABLE]): QA pins it to the cheat sheet.
 * - "cheat" opens the cheat sheet; "cheat:<page>,<page>" as an exercise of those grammar pages would (their cards first).
 * - "school": the village (the dev bridge's copy) gets ready for the class under the linden ([readySchool]: the hamlet,
 *   the linden standing, the unit biti as never begun), and the school opens, its class begun (companion/GAME.md, "The
 *   school"). "school:<unit>" does the same with that unit (its id: "oziralni-ki") as never begun and its course level
 *   open ([readyUnit]: a B1 unit without A2 done and the school upgraded), for QA to play it from the school's levels.
 * - "sheet:<unit>/<sheet>": the class readied (as "school") and that sheet of the unit played at once ("sheet:sedanjik/
 *   sedanjik-3", "sheet:biti/test": the unit's test), as its button in the unit starts it ([sheetOf]): a sheet's
 *   corrections looked at without playing the units before it.
 * - "counting": counting cards (data/Counting.kt): today's asked of the dev bridge now, then a review of a few of them, due
 *   or not; "counting:type" types their steps from then on, as if each card were secure ([countingTyped]), "counting:off"
 *   lets the cards' own reviews decide again; "counting:intro" readies the class (as "school") with the unit stevila as
 *   never begun ([readyCounting]: counting off) and opens its intro, whose end ("⏭ Poznam to") turns counting on.
 * - "numbers": "🔢 Števila · Numbers" (data/NumbersPractice.kt) opened; "numbers:locked" readies the class (as "school")
 *   with the unit stevila as never begun and the module never played ([readyNumbers]): its closed screen; "numbers:ready"
 *   the class with the unit's intro done (as "⏭ Poznam to" ends it) and the module never played; "numbers:time" the unit
 *   kdaj-cas's intro done too (the clock's ways on); "numbers:ranges:<id>" the ranges open up to that one
 *   ([numbersRanges]); "numbers:again" a few numbers missed due today ([numbersAgain]); "numbers:near" the newest range one
 *   right answer from opening the next ([numbersNear]); "numbers:only:<mode>" asks every
 *   task of the next runs that way ([numbersOnly]: "hear", "write", "time_hear" …), "numbers:only:" mixes them again.
 * - "formscard": forms cards (data/FormsCard.kt): today's asked of the dev bridge now, then a review of up to three of
 *   them that ask something, due or not; "formscard:<word>" a review of that word's card (made on demand first);
 *   "formscard:type" types their gaps from then on, as if each card were secure ([formsTyped]), "formscard:off" lets
 *   the cards' own reviews decide again; "formscard:unit:<id>" the unit's intro done (as "⏭ Poznam to" ends it: its
 *   pages met, the node told), "formscard:word:<word>" the word's card open (as a tap on it in a dialog).
 * - "shadow": the developer settings shown and the shadow tutor's debug panel open (tutor/ShadowTutor);
 *   "shadow:<url>" with the Messages API's base URL set to <url> first (QA's mock, http://10.0.2.2:8771). The key is
 *   entered in the panel, as the learner would.
 * - "data": the "💾 Podatki · Data" sheet open (ui/data/DataSheet, sync/DataSync), the same as the settings' page
 *   "💾 Podatki · Data".
 * - "tutorlive:<url>": the tutor in the app's Messages API base URL set to <url> (QA's mock, http://10.0.2.2:8771);
 *   "tutorlive:" the API again (tutor/live/LiveTutor). The key is entered in the Tutor section, as the learner would.
 * - "friends": "🤝 Prijatelji · Friends" opened (the linked towns, their visits), as the scroll's people page opens it: a busy
 *   emulator's village map often answers uiautomator with no screen at all, and QA's visits start here.
 * - "review": a review of up to five of the learner's cards, due or not (a new learner's words are due tomorrow: the
 *   hosted mode's QA reviews them today).
 * - "request:<card id>,<card id>…": the village (the dev bridge's copy) gets Mojca's request "Napake na tabli" of those
 *   cards of the learner's ([withRequest]: the cards a request was made from, a grammar card among them) and its challenge
 *   starts, as from its bubble: what the challenge asks of each card (companion/GAME.md, "Cards in a challenge").
 * - "pair:<target>-<base>": the app shown in that language pair ("pair:sl-de"), as the settings' languages page
 *   chooses it; "pair:" follows the learner profile again.
 * - "kopa:<day>": the spots' cycles (the charcoal pile's rounds, game/scene/SpotCycles) are as on that day ("2026-07-14",
 *   "07-14": the next 14 July from today), or on the first day of a stage ("kopa:rumen-dim": of the next round that has it,
 *   not its breakout night), of its next breakout night ("kopa:breakout"; "kopa:breakout:2" the one after), of its next
 *   delivery ("kopa:delivery"), between rounds ("kopa:rest") or in the winter ("kopa:winter") ([kopaDay]); while it is set
 *   the keepers come whatever their dice. The rest of the game keeps today. "kopa:" goes by today again.
 * - "show:<spot>": the village's camera flies to that spot of the landscape ("show:kopa"), as a place of the scroll's
 *   "Kraji" flies it there ([show]): a spot deep in the woods framed without drags that a busy machine sends unevenly;
 *   asked again, it flies there again; "show:fire" the fire, "show:cart" the merchant's cart on the road, "show:threat"
 *   the wolves or the bear. "show:" lets it go.
 * - "dev:again": the developer's next upload to the node when the app goes to the background doesn't wait for the 30
 *   minutes since the last one (app/DevUpload); "dev:off" hides the developer settings (as their "Hide these settings").
 * - "ageup:<age>": the village (the dev bridge's copy) grows to that age today from the one before ([grownTo]: whatever it
 *   lacks for it, its party not had yet) and its moment plays over the village ([ageUp], taken by the village:
 *   [tookAgeUp]; companion/GAME.md "Ages and their celebrations"); "ageup:at:<s>" holds a moment at that second for a
 *   screenshot (the card from its end on: [ageUpAt]), "ageup:at:" lets it play on.
 * - "level:<A1|A2|…>": the dialogs opened from now on (a scene's, a party's, a step's) are at that level, whatever the
 *   learner's ([level]: an age's party at A1); "level:" lets the learner's own decide again.
 * - "event:<kind>" ("event:wolves", "event:bear/3", "event:storm", "event:merchant", "event:festival"): the village (the
 *   dev bridge's copy) has that event on from now ([eventState]: of strength 1, or the one after the slash), as the day's
 *   tick brings one, and the village shows, its people reacting (companion/GAME.md "People react"); "event:won" and
 *   "event:lost" end the one on as its run would ("event:lost" damages a house when the dice didn't), "event:" ends it
 *   without a trace (nor the reactions after it).
 * - "hearths:<what>" (companion/GAME.md "Hearths"): the village (the dev bridge's copy) as [hearthsState] makes it, and the
 *   village shown: "hearths:tabor" (… "zaselek", "vas", "trg", "mesto") a village of that age built as QA's step needs it,
 *   every hearth lit and wood for days; "hearths:short" the store short of wood, the outlying hearths out as the day's tick
 *   leaves them; "hearths:lit" every one lit again; "hearths:feast" a feast today (the square's kres in the evening);
 *   "hearths:cold" the forge without charcoal.
 * - "seed:<n>": the founding's place ("Kje bo tvoja vas?", ui/game/FoundingScreen) is the one of that seed for the dice and
 *   the land ([foundingSeed]; 551323397: Teo's coast, whose land seed it is too), a reroll going on from it; "seed:" rolls
 *   one at random again.
 */
object QaHooks {
    const val EXTRA = "si.lanisce.lani.qa"

    /** Whether the hooks answer: in a debug or qa build only. */
    var enabled = BuildConfig.QA

    /** How many zooms QA asked for: a camera on screen zooms in once for each one asked while it shows. */
    var zooms by mutableIntStateOf(0)
        private set

    /** Every word card with a form reached asks one in the next reviews ([FormsController.questions]). */
    var forms by mutableStateOf(false)
        private set

    /** The happening QA counts as on ("scene/happening", or "scene/happening/dialog" with that variant), or null. */
    var happening by mutableStateOf<String?>(null)
        private set

    /** QA asked for typed turns ("turns:type"): every rule of a dialog's form turns counts as secure. */
    var typedTurns = false
        private set

    /** QA asked a dialog's 🎤 to hear the turn's right choice without listening ("mic:right"). */
    var micRight by mutableStateOf(false)
        private set

    /** QA asked for typed forms cards ("formscard:type"): every forms card's gaps are typed (a fresh part's chosen still). */
    var formsTyped = false
        private set

    /** QA asked for one way of asking in the numbers' runs ("numbers:only:<mode>"), or null: mixed as ever. */
    var numbersOnly: si.lanisce.lani.data.NumberMode? = null
        private set

    /** QA asked for typed counting cards ("counting:type"): every counting card's steps are typed. */
    var countingTyped = false
        private set

    /** «Vidim, vidim» QA offers ("scene/slot": the scene, the thing its first round spies), or null. */
    var ispy by mutableStateOf<String?>(null)
        private set

    /** The project QA readied a step of ("step:<project>/<n>"): the projects sheet opens at it; null: none. */
    var project by mutableStateOf<String?>(null)
        private set

    /** The hour of today QA asks the scenes and the village's map to be seen at ("hour:<h>"), or null: the clock's. */
    var sceneHour by mutableStateOf<Float?>(null)
        private set

    /** The day QA asks the spots' cycles to be on ("kopa:<day>"), or null: today (what the village looks again by). */
    var kopa by mutableStateOf<java.time.LocalDate?>(null)
        private set

    /** The spot QA asked the village's camera to fly to ("show:<spot>") and how many times it was asked, or null. */
    var show by mutableStateOf<Pair<String, Int>?>(null)
        private set

    /** How many bubbles the village shows while QA asks for fewer ("bubbles:<n>"), or null: all of them. */
    var bubbles by mutableStateOf<Int?>(null)
        private set

    /** The age QA grew the dev village to ("ageup:<age>"), until the village plays its moment ([tookAgeUp]); null: none. */
    var ageUp by mutableStateOf<si.lanisce.lani.game.Age?>(null)
        private set

    /** The second QA holds an age's moment at ("ageup:at:<s>"), or null: it plays on its clock. */
    var ageUpAt by mutableStateOf<Float?>(null)
        private set

    /** The seed QA founds a village with ("seed:<n>"), or null: a random one. */
    var foundingSeed by mutableStateOf<Long?>(null)
        private set

    /** The level QA asks the dialogs to be at ("level:<lv>"), or null: the learner's. */
    var level by mutableStateOf<String?>(null)
        private set

    /** The village played the moment QA asked for. */
    fun tookAgeUp() { ageUp = null }

    /**
     * [s] with the event QA asks for ("event:<what>", [handle]) at [now] (epoch ms): "wolves", "bear", "storm", "merchant",
     * "festival" on from now (strength 1, or "bear/3"'s), as the day's tick brings one (36 hours to play it); "won" and
     * "lost" the one on ended as its run ends it ([si.lanisce.lani.game.Events.outcome]; lost, a house or hut damaged when
     * the dice damaged nothing and the age can be damaged); "" none, nor how the last one ended. Anything else: [s]. A debug
     * build's, on the dev bridge's copy.
     */
    fun eventState(s: si.lanisce.lani.game.GameState, what: String, now: Long): si.lanisce.lani.game.GameState {
        val name = what.substringBefore('/').trim().uppercase()
        val strength = what.substringAfter('/', "1").toIntOrNull()?.coerceIn(1, 5) ?: 1
        si.lanisce.lani.game.EventKind.entries.firstOrNull { it.name == name }?.let { k ->
            return s.copy(event = si.lanisce.lani.game.GameEvent("ev-qa-${k.name.lowercase()}", k, strength, now, now + 36 * 3_600_000L), ended = null)
        }
        return when (name) {
            "" -> s.copy(event = null, ended = null)
            "WON", "LOST" -> {
                val e = s.event ?: return s
                var r = si.lanisce.lani.game.Events.outcome(s, e, won = name == "WON", expired = false, now = now).first
                val end = r.ended
                if (name == "LOST" && end != null && end.damaged.isEmpty() && r.age > si.lanisce.lani.game.Age.TABOR) {
                    r.buildings.firstOrNull { (it.type == si.lanisce.lani.game.BuildingType.HOUSE || it.type == si.lanisce.lani.game.BuildingType.HUT) && !it.damaged }?.let { b ->
                        r = r.copy(buildings = r.buildings.map { if (it.id == b.id) it.copy(damaged = true) else it }, ended = end.copy(damaged = listOf(b.id)))
                    }
                }
                r
            }
            else -> s
        }
    }

    /**
     * [s] grown to [to] [today] from the age before it ("ageup:<age>"), whatever it lacks for it: the age, the day it grew
     * (its decorations up, its party tonight, not had yet), its line in the chronicle. A debug build's, on the dev bridge's copy.
     */
    fun grownTo(s: si.lanisce.lani.game.GameState, to: si.lanisce.lani.game.Age, today: java.time.LocalDate, now: Long): si.lanisce.lani.game.GameState {
        val key = si.lanisce.lani.game.AgeUps.key(to)
        val line = si.lanisce.lani.game.culture.Cultures.current.chronicle.newAge.bi("age" to to.names)
        return s.copy(
            age = to, grown = s.grown + (key to today.toString()), parties = s.parties - key,
            log = (s.log + si.lanisce.lani.game.LogEntry(now, to.emoji, line)).takeLast(50),
        )
    }

    /** The buildings of QA's village of each age ("hearths:<age>"), on plots 0, 1, … (the palisade round the clearing). */
    private val HEARTH_VILLAGES: Map<si.lanisce.lani.game.Age, List<si.lanisce.lani.game.BuildingType>> = run {
        fun of(vararg names: String) = names.map { si.lanisce.lani.game.BuildingType.valueOf(it) }
        val tabor = of("TENT", "FIELD", "HUT", "KOZOLEC", "TENT")
        val zaselek = of("TENT", "FIELD", "HUT", "KOZOLEC", "LIPA", "HUT", "CHURCH", "SMITHY", "SCHOOL", "HOUSE")
        val vas = zaselek + of("WELL", "MARKET", "HOUSE", "HOUSE")
        val trg = vas + of("HOUSE", "BEEHIVE", "HOUSE", "WATCHTOWER", "HOUSE", "FIELD")
        mapOf(
            si.lanisce.lani.game.Age.TABOR to tabor, si.lanisce.lani.game.Age.ZASELEK to zaselek, si.lanisce.lani.game.Age.VAS to vas,
            si.lanisce.lani.game.Age.TRG to trg, si.lanisce.lani.game.Age.MESTO to trg + of("HOUSE", "HOUSE", "BEEHIVE", "KOZOLEC"),
        )
    }

    /**
     * [s] as QA's "hearths:<what>" asks (companion/GAME.md "Hearths"): an age's village ("tabor" … "mesto": its buildings on
     * plots 0, 1, … and the palisade, every hearth lit, wood for days, the age's decorations down), the store short of wood
     * ("short": the outlying hearths out as the day's tick leaves them with half a day's wood), every one lit again ("lit"), a
     * feast today ("feast"), the forge without charcoal ("cold"). A debug build's, on the dev bridge's copy.
     */
    fun hearthsState(s: si.lanisce.lani.game.GameState, what: String, today: java.time.LocalDate, now: Long): si.lanisce.lani.game.GameState {
        val day = today.toString()
        // kept from today: the piles whose wood was due before count as given (the forge on their charcoal)
        val lit = si.lanisce.lani.game.HearthDay(on = day, since = s.hearths?.since?.ifEmpty { null } ?: day)
        fun wood(st: si.lanisce.lani.game.GameState, n: Int) = st.copy(resources = st.resources + (si.lanisce.lani.game.Res.WOOD to n))
        si.lanisce.lani.game.Age.entries.firstOrNull { it.name == what.uppercase() }?.let { age ->
            val types = HEARTH_VILLAGES[age] ?: return s
            val built = types.mapIndexed { i, t -> si.lanisce.lani.game.Building("${t.name.lowercase()}-${i + 1}", t, i, level = 2, builtAt = now - 200L * 86_400_000L) } +
                si.lanisce.lani.game.Building("palisade-qa", si.lanisce.lani.game.BuildingType.PALISADE, si.lanisce.lani.game.NO_PLOT, builtAt = now - 200L * 86_400_000L)
            val v = s.copy(age = age, buildings = built, hearths = lit.copy(since = day), grown = s.grown - si.lanisce.lani.game.AgeUps.key(age), lastFeast = "", event = null, ended = null)
            return wood(v, maxOf(v.res(si.lanisce.lani.game.Res.WOOD), 300)).copy(fire = 100)
        }
        return when (what) {
            "short" -> {
                // the day's tick with half a day's wood: the outlying hearths go out; then the store is empty
                val need = si.lanisce.lani.game.Hearths.need(s)
                val d = si.lanisce.lani.game.Hearths.day(wood(s, need / 2), today, soft = false).state
                wood(s, 0).copy(hearths = d.hearths?.copy(on = day), fire = d.fire)
            }
            "lit" -> wood(s, maxOf(s.res(si.lanisce.lani.game.Res.WOOD), 300)).copy(hearths = lit, fire = 100)
            "feast" -> s.copy(lastFeast = day)
            "cold" -> s.buildings.firstOrNull { it.type == si.lanisce.lani.game.BuildingType.SMITHY }?.let { f ->
                val h = s.hearths ?: lit
                s.copy(hearths = h.copy(out = (h.out + f.id).distinct(), charcoal = today.minusDays(1).toString()))
            } ?: s
            else -> s
        }
    }

    /** The building QA asked the plot chooser for ("build:<type>"), until the village opens it ([tookBuild]); null: none. */
    var build by mutableStateOf<si.lanisce.lani.game.BuildingType?>(null)
        private set

    /** A tutor message with a list, as the tutor explains imeti ("chat:table"): QA pins it to the cheat sheet. */
    const val TUTOR_TABLE = "**Imeti** – the whole verb:\n" +
        "- jaz **imam** · ti **imaš** · on/ona **ima**\n" +
        "- midva **imava** · vidva/onadva **imata**\n" +
        "- mi **imamo** · vi **imate** · oni **imajo**\n\n" +
        "*Vida ima gostilno.* (one) · *Vida in Nejc imata konja.* (two) · *Vsi imajo čas.* (three+)\n\n" +
        "The two-person endings are the same everywhere: **-va** (me + one more), **-ta** (two others), like *sva / sta* with biti."

    /** The village opened the plot chooser QA asked for. */
    fun tookBuild() { build = null }

    /** Does what [command] asks; [road] gets what is asked of the road ("mini", "car"); [forms] starts the forms' review. */
    fun handle(
        command: String?, road: (String) -> Unit = {}, forms: () -> Unit = {}, step: (String, Int) -> Unit = { _, _ -> }, audio: (String) -> Unit = {},
        season: () -> Unit = {}, ready: () -> Unit = {}, chat: (String) -> Unit = {}, cheat: (List<String>) -> Unit = {}, school: () -> Unit = {},
        schoolUnit: (String) -> Unit = {},
        shadow: (String) -> Unit = {}, counting: (String) -> Unit = {}, dev: (String) -> Unit = {}, pair: (String) -> Unit = {},
        grow: (si.lanisce.lani.game.Age) -> Unit = {}, formsCard: (String, String) -> Unit = { _, _ -> }, data: () -> Unit = {}, tutorLive: (String) -> Unit = {},
        review: () -> Unit = {}, numbers: (String) -> Unit = {}, event: (String) -> Unit = {}, hearths: (String) -> Unit = {},
        request: (List<String>) -> Unit = {}, word: (String) -> Unit = {}, friends: () -> Unit = {},
        sheet: (String, String) -> Unit = { _, _ -> },
    ) {
        if (!enabled || command.isNullOrBlank()) return
        when {
            command.startsWith("event:") -> event(command.removePrefix("event:").trim())
            command.startsWith("hearths:") -> hearths(command.removePrefix("hearths:").trim())
            command.startsWith("dev:") -> dev(command.removePrefix("dev:"))
            command.startsWith("seed:") -> foundingSeed = command.removePrefix("seed:").trim().toLongOrNull()
            command.startsWith("level:") -> level = command.removePrefix("level:").trim().uppercase().takeIf { it in listOf("A1", "A2", "B1", "B2", "C1", "C2") }
            command.startsWith("ageup:at:") -> ageUpAt = command.removePrefix("ageup:at:").toFloatOrNull()?.coerceAtLeast(0f)
            command.startsWith("ageup:") -> {
                val name = command.removePrefix("ageup:").trim().uppercase()
                si.lanisce.lani.game.Age.entries.firstOrNull { it.name == name && it != si.lanisce.lani.game.Age.OGENJ }?.let { a -> ageUp = a; grow(a) }
            }
            command.startsWith("pair:") -> pair(command.removePrefix("pair:").trim())
            command == "shadow" -> shadow("")
            command == "data" -> data()
            command == "review" -> review()
            command == "friends" -> friends()
            command.startsWith("request:") -> command.removePrefix("request:").split(',').map { it.trim() }.filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }?.let(request)
            command.startsWith("shadow:") -> shadow(command.removePrefix("shadow:"))
            command.startsWith("tutorlive:") -> tutorLive(command.removePrefix("tutorlive:"))
            command.startsWith("audio:") -> audio(command.removePrefix("audio:"))
            command == "zoom" -> zooms++
            command.startsWith("happening:") -> happening = command.removePrefix("happening:").takeIf { '/' in it }
            command.startsWith("road:") -> command.removePrefix("road:").takeIf { it.isNotBlank() }?.let(road)
            command == "forms" -> {
                this.forms = true
                forms()
            }
            command == "forms:off" -> this.forms = false
            command.startsWith("turns:") -> typedTurns = command == "turns:type"
            command.startsWith("mic:") -> micRight = command == "mic:right"
            command == "counting" -> counting("")
            command == "counting:intro" -> counting("intro")
            command == "counting:type" -> countingTyped = true
            command == "counting:off" -> countingTyped = false
            command.startsWith("numbers:only:") -> numbersOnly = si.lanisce.lani.data.NumberMode.of(command.removePrefix("numbers:only:").trim())
            command == "numbers" -> numbers("")
            command.startsWith("numbers:") -> numbers(command.removePrefix("numbers:").trim())
            command == "formscard" -> formsCard("review", "")
            command == "formscard:type" -> formsTyped = true
            command == "formscard:off" -> formsTyped = false
            command.startsWith("formscard:unit:") -> formsCard("unit", command.removePrefix("formscard:unit:").trim())
            command.startsWith("formscard:word:") -> formsCard("word", command.removePrefix("formscard:word:").trim())
            command.startsWith("formscard:") -> formsCard("review", command.removePrefix("formscard:").trim())
            command.startsWith("ispy:") -> ispy = command.removePrefix("ispy:").takeIf { '/' in it }
            command.startsWith("bubbles:") -> bubbles = command.removePrefix("bubbles:").toIntOrNull()?.coerceAtLeast(0)
            command.startsWith("hour:") -> sceneHour = command.removePrefix("hour:").toFloatOrNull()?.takeIf { it in 0f..24f }
            command.startsWith("clock:") -> TownClock.rate = command.removePrefix("clock:").toFloatOrNull()?.coerceIn(0.05f, 1f) ?: 1f
            command.startsWith("render:") -> si.lanisce.lani.game.render.VillageRenderer.groundKept = command != "render:full"
            command.startsWith("show:") -> show = command.removePrefix("show:").trim().takeIf { it.isNotEmpty() }?.let { it to (show?.second ?: 0) + 1 }
            command.startsWith("kopa:") -> {
                kopa = kopaDay(command.removePrefix("kopa:"), java.time.LocalDate.now())
                si.lanisce.lani.game.scene.SpotCycles.qaDay = kopa
            }
            command.startsWith("season:") -> {
                si.lanisce.lani.game.ProjectSeasons.qaDay = seasonDay(command.removePrefix("season:"), java.time.LocalDate.now())
                season()
            }
            command.startsWith("step:") -> {
                val arg = command.removePrefix("step:")
                val n = arg.substringAfter('/', "").toIntOrNull()
                project = arg.substringBefore('/').takeIf { it.isNotBlank() && n != null }
                project?.let { step(it, n!!) }
            }
            command.startsWith("word:") -> word(command.removePrefix("word:"))
            command == "chat:table" -> chat(TUTOR_TABLE)
            command.startsWith("chat:") -> command.removePrefix("chat:").replace("\\n", "\n").takeIf { it.isNotBlank() }?.let(chat)
            command == "cheat" -> cheat(emptyList())
            command.startsWith("cheat:") -> cheat(command.removePrefix("cheat:").split(',').map { it.trim() }.filter { it.isNotEmpty() })
            command == "school" -> school()
            command.startsWith("sheet:") -> sheetOf(command.removePrefix("sheet:"))?.let { (unit, id) -> sheet(unit, id) }
            command.startsWith("school:") -> command.removePrefix("school:").trim().takeIf { it.isNotEmpty() }?.let(schoolUnit)
            command.startsWith("build:") -> {
                val name = command.removePrefix("build:").trim().uppercase()
                build = si.lanisce.lani.game.BuildingType.entries.firstOrNull { it.name == name && it.onPlot }
                if (build != null) ready()
            }
        }
    }

    /**
     * [s] ready to build a building QA chooses a plot for ("build:<type>"): the stores full ([caps]), and a free plot: when
     * the age's plots are all taken, the next age (its new plots). A debug build's, on the dev bridge's copy.
     */
    fun readyBuild(s: si.lanisce.lani.game.GameState, caps: Map<si.lanisce.lani.game.Res, Int>): si.lanisce.lani.game.GameState {
        val full = s.copy(resources = s.resources + caps.mapValues { (r, cap) -> maxOf(s.res(r), cap) })
        val plots = si.lanisce.lani.game.PLOTS_PER_AGE[s.age.ordinal]
        val free = (0 until plots).any { p -> s.buildings.none { it.type.onPlot && it.plot == p } }
        val next = si.lanisce.lani.game.Age.entries.getOrNull(s.age.ordinal + 1)
        return if (free || next == null) full else full.copy(age = next)
    }

    /** The id of the request QA makes ("request:<ids>", [withRequest]). */
    const val REQUEST = "q-qa-request"

    /**
     * [s] with Mojca's request "Napake na tabli" (grammar, stone) of the learner's cards [cardIds] ("request:<ids>"), in place of
     * one QA made before, open, untried, asked [today]. A debug build's, on the dev bridge's copy.
     */
    fun withRequest(s: si.lanisce.lani.game.GameState, cardIds: List<String>, today: java.time.LocalDate, now: Long): si.lanisce.lani.game.GameState {
        val stone = si.lanisce.lani.game.Res.STONE
        val q = si.lanisce.lani.game.Quest(
            id = REQUEST, giver = "Učiteljica Mojca", emoji = "👩‍🏫", title = "Napake na tabli",
            story = "Nekdo je na tablo napisal stavke z napakami. Mojca prosi, da jih popraviš.", skill = stone,
            reward = mapOf(stone to 30), cardIds = cardIds, expiresAt = now + 7L * 24 * 3600 * 1000, since = today.toString(),
        )
        return s.copy(quests = s.quests.filter { it.id != REQUEST } + q)
    }

    /** "sedanjik/sedanjik-3" ("sheet:<unit>/<sheet>") as its unit and sheet; null without both. */
    fun sheetOf(arg: String): Pair<String, String>? {
        val unit = arg.substringBefore('/').trim()
        val id = arg.substringAfter('/', "").trim()
        return if (unit.isEmpty() || id.isEmpty()) null else unit to id
    }

    /**
     * [s] ready for the class under the linden ("school"): a hamlet at least (the class begins with it), the linden standing
     * (on a free plot of the age; none when they're all taken: the class then meets by the fire). A debug build's, on the
     * dev bridge's copy.
     */
    fun readySchool(s: si.lanisce.lani.game.GameState, now: Long): si.lanisce.lani.game.GameState {
        var r = if (s.age < si.lanisce.lani.game.Age.ZASELEK) s.copy(age = si.lanisce.lani.game.Age.ZASELEK) else s
        if (r.buildings.none { it.type == si.lanisce.lani.game.BuildingType.LIPA }) {
            val plots = si.lanisce.lani.game.PLOTS_PER_AGE[r.age.ordinal]
            val free = (0 until plots).firstOrNull { p -> r.buildings.none { it.type.onPlot && it.plot == p } }
            r = if (free != null) r.copy(buildings = r.buildings + si.lanisce.lani.game.Building("lipa-qa", si.lanisce.lani.game.BuildingType.LIPA, free, builtAt = now))
            else r
        }
        // the unit biti as never begun, whatever the copy's learner did with it: QA plays its intro from its first step
        val course = r.course?.let { c -> c.copy(units = c.units - si.lanisce.lani.game.School.key("sl", "biti")) }
        return r.copy(buildings = r.buildings.map { if (it.type == si.lanisce.lani.game.BuildingType.LIPA) it.copy(damaged = false) else it }, course = course)
    }

    /**
     * [s] ready for the class of unit [unit] of [language] ("school:<unit>"): as "school" ([readySchool]), the unit as never
     * begun and its course [level] open ([today], or the day it opened before), whatever holds the level back (the levels
     * before it, the school's upgrade): QA plays a later level's unit from its first step. A debug build's, on the dev
     * bridge's copy.
     */
    fun readyUnit(s: si.lanisce.lani.game.GameState, language: String, unit: String, level: String, today: java.time.LocalDate, now: Long): si.lanisce.lani.game.GameState {
        val r = readySchool(s, now)
        val c = r.course ?: si.lanisce.lani.game.CourseProgress()
        val opened = si.lanisce.lani.game.School.key(language, level)
        return r.copy(course = c.copy(units = c.units - si.lanisce.lani.game.School.key(language, unit), opened = c.opened + (opened to (c.opened[opened] ?: today.toString()))))
    }

    /**
     * [s] with the unit stevila as never begun ("counting:intro"): its intro to do again, counting off until it ends. A
     * debug build's, on the dev bridge's copy.
     */
    fun readyCounting(s: si.lanisce.lani.game.GameState): si.lanisce.lani.game.GameState =
        s.copy(course = s.course?.let { c -> c.copy(units = c.units - si.lanisce.lani.game.School.key(si.lanisce.lani.data.Counting.LANGUAGE, si.lanisce.lani.data.Counting.UNIT)) })

    /**
     * [s] with the numbers module as never played ("numbers:locked", "numbers:ready"): no ranges opened by a run, no
     * number missed. A debug build's, on the dev bridge's copy.
     */
    fun readyNumbers(s: si.lanisce.lani.game.GameState): si.lanisce.lani.game.GameState = s.copy(numbers = null)

    /** [s] with the numbers' ranges open up to [id] ("numbers:ranges:<id>"), each opened [today]. */
    fun numbersRanges(s: si.lanisce.lani.game.GameState, id: String, today: java.time.LocalDate): si.lanisce.lani.game.GameState {
        val to = si.lanisce.lani.data.NumberRange.byId(id) ?: return s
        val p = s.numbers ?: si.lanisce.lani.data.NumbersProgress()
        val opened = si.lanisce.lani.data.NumberRange.entries.filter { it.ordinal <= to.ordinal && it !in si.lanisce.lani.data.NumbersPractice.FIRST }
            .associate { it.id to (p.opened[it.id] ?: today.toString()) }
        return s.copy(numbers = p.copy(opened = p.opened + opened))
    }

    /**
     * [s] with the newest numbers range open one right answer from the next ("numbers:near"): its last answers 9 right of
     * 9, so a run's first right one there opens the next range.
     */
    fun numbersNear(s: si.lanisce.lani.game.GameState): si.lanisce.lani.game.GameState {
        val p = s.numbers ?: si.lanisce.lani.data.NumbersProgress()
        val newest = si.lanisce.lani.data.NumbersPractice.newest(p, true) ?: return s
        return s.copy(numbers = p.copy(last = p.last + (newest.id to "1".repeat(si.lanisce.lani.data.NumbersPractice.WINDOW - 1))))
    }

    /** [s] with a few numbers missed due [today] ("numbers:again"): 17 heard, 12 written, 19 read. */
    fun numbersAgain(s: si.lanisce.lani.game.GameState, today: java.time.LocalDate): si.lanisce.lani.game.GameState {
        val p = s.numbers ?: si.lanisce.lani.data.NumbersProgress()
        val d = today.toString()
        val add = listOf(
            si.lanisce.lani.data.NumberAgain(17, "hear", d), si.lanisce.lani.data.NumberAgain(12, "write", d), si.lanisce.lani.data.NumberAgain(19, "read", d),
        )
        return s.copy(numbers = p.copy(again = p.again.filter { a -> add.none { it.n == a.n && it.mode == a.mode } } + add))
    }

    /** Today at the hour QA asks the village's map to be seen at ("hour:<h>"), or null: the clock's (and the real sky). */
    fun mapNow(): java.time.LocalDateTime? = if (enabled && sceneHour != null) sceneNow(java.time.LocalDateTime.now()) else null

    /** [now], or today at the hour QA asks the scenes to be seen at ("hour:<h>"). */
    fun sceneNow(now: java.time.LocalDateTime): java.time.LocalDateTime {
        val h = sceneHour?.takeIf { enabled } ?: return now
        val s = (h * 3600 + 0.5f).toInt().coerceIn(0, 24 * 3600 - 1)
        return now.toLocalDate().atTime(s / 3600, s / 60 % 60, s % 60)
    }

    /** The mastery a dialog's turns are asked by: [own] (the learner's), or secure for every rule while QA asks for typed turns. */
    fun mastery(own: (String) -> Mastery?): (String) -> Mastery? = if (enabled && typedTurns) { _ -> Mastery.SECURE } else own

    /** [items] (the village's bubbles) as shown: all, or while QA asks for fewer the [bubbles] nearest by [distance], in their order. */
    fun <T> fewer(items: List<T>, distance: (T) -> Float): List<T> {
        val n = bubbles?.takeIf { enabled } ?: return items
        val keep = items.indices.sortedBy { distance(items[it]) }.take(n).toSet()
        return items.filterIndexed { i, _ -> i in keep }
    }

    /**
     * [s] ready for step [n] (1: the first) of project [id] [today]: its steps before it done, no step today yet, the stores,
     * the 🤝 and (the feast) a treat it costs; the leader must live here already. A step past its last: every step done,
     * the project finished. A debug build's, on the dev bridge's copy.
     */
    fun readyStep(s: si.lanisce.lani.game.GameState, id: String, n: Int, today: java.time.LocalDate): si.lanisce.lani.game.GameState {
        val spec = si.lanisce.lani.game.Projects.spec(id) ?: return s
        if (n > spec.steps.size) return s.copy(projects = s.projects + (id to spec.steps.size), projectsFinished = s.projectsFinished + (id to today.toString()))
        val i = (n - 1).coerceIn(0, spec.steps.size - 1)
        val step = spec.steps[i]
        val cost = si.lanisce.lani.game.Catalog.stepCost(spec.age, step.kind)
        var r = s.copy(projects = s.projects + (id to i), projectDay = "", resources = s.resources + cost.mapValues { (res, c) -> maxOf(s.res(res), c) })
        if (step.help) r = r.copy(help = maxOf(r.help, si.lanisce.lani.game.Catalog.stepHelp(spec.age)))
        if (step.kind == si.lanisce.lani.game.StepKind.FEAST && si.lanisce.lani.game.Projects.treat(r) == null) {
            si.lanisce.lani.game.Catalog.goods.values.firstOrNull { it.food && !it.rare }?.let { g -> r = si.lanisce.lani.game.Chest.add(r, g.id, 1) }
        }
        return r
    }

    /**
     * The day "season:<day>" asks for: an ISO date ("2027-05-10"), or a month and day ("05-10"): the next one from [today]
     * on; null for none ("season:") or one that isn't a day.
     */
    fun seasonDay(arg: String, today: java.time.LocalDate): java.time.LocalDate? {
        runCatching { java.time.LocalDate.parse(arg) }.getOrNull()?.let { return it }
        val md = si.lanisce.lani.game.culture.StandsEntry.monthDay(arg) ?: return null
        return md.atYear(today.year).let { if (it.isBefore(today)) md.atYear(today.year + 1) else it }
    }

    /**
     * The day "kopa:<what>" asks the charcoal pile's cycle to be on (from [today], in [world]'s cycle of the spot "kopa"): a
     * day ([seasonDay]'s forms), the first day of a stage of the next round that has it (its second when the first is the
     * round's breakout night: the stage's own day, its talk and its title), the next breakout night, the next
     * delivery, the next day between rounds or in the winter; null for none ("kopa:") or what the cycle hasn't got.
     */
    fun kopaDay(arg: String, today: java.time.LocalDate, world: si.lanisce.lani.game.culture.WorldFile = si.lanisce.lani.game.culture.Cultures.current.world): java.time.LocalDate? {
        if (arg.isBlank()) return null
        seasonDay(arg, today)?.let { return it }
        val c = world.spots[si.lanisce.lani.game.scene.TownSpots.KOPA]?.cycle ?: return null
        val spot = si.lanisce.lani.game.scene.TownSpots.KOPA
        val rounds = (today.year..today.year + 2).flatMap { si.lanisce.lani.game.scene.SpotCycles.rounds(c, spot, it) }.filter { !it.until.isBefore(today) }
        return when (arg) {
            "breakout" -> rounds.mapNotNull { it.breakout }.firstOrNull { !it.isBefore(today) }
            // "breakout:2": the one after the next (the next decided already)
            in (2..9).map { "breakout:$it" } -> rounds.mapNotNull { it.breakout }.filter { !it.isBefore(today) }.getOrNull(arg.substringAfter(':').toInt() - 1)
            "delivery" -> rounds.mapNotNull { it.delivery }.firstOrNull { !it.isBefore(today) }
            "rest", "winter" -> (0..800).asSequence().map { today.plusDays(it.toLong()) }
                .firstOrNull { si.lanisce.lani.game.scene.SpotCycles.on(c, spot, it).id == arg }
            // a stage's first day that is an ordinary one: not its round's breakout night (that one is "kopa:breakout")
            else -> rounds.flatMap { r -> r.spans.filter { it.stage.id == arg && !it.from.isBefore(today) }.map { r to it } }
                .firstNotNullOfOrNull { (r, sp) -> (0 until sp.days).map { sp.from.plusDays(it.toLong()) }.firstOrNull { it != r.breakout } }
        }
    }

    /** [active] with the happening QA counts as on first, when it is one of [scenes]' and isn't on already. */
    fun withForced(scenes: List<SceneSpec>, active: List<ActiveHappening>, key: String? = happening): List<ActiveHappening> {
        if (!enabled || key == null) return active
        val (sid, hid, variant) = (key.split('/', limit = 3) + listOf("", "")).take(3)
        // a variant asked for plays even when the happening is on already (with the day's)
        if (variant.isEmpty() && active.any { it.key == "$sid/$hid" }) return active
        val s = scenes.firstOrNull { it.id == sid } ?: return active
        val h = s.happenings.firstOrNull { it.id == hid } ?: return active
        val dialog = variant.takeIf { v -> s.dialogs.any { it.id == v } }
        return listOf(ActiveHappening(s, h, s.people.firstOrNull { it.id == h.who }, dialog = dialog)) + active.filter { it.key != "$sid/$hid" }
    }
}
