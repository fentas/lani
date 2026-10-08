package si.lanisce.lani.road

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import si.lanisce.lani.data.ClipIndex
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Drill
import si.lanisce.lani.data.Drills
import si.lanisce.lani.data.PackInfo
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.Stats
import si.lanisce.lani.data.VoiceProfile
import si.lanisce.lani.data.json
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.Stories
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.ui.scene.SceneWords

/** What the app gathers for the road, from the node and the phone ([RoadGather.library] makes the items). */
data class RoadInputs(
    /** The review cards and their due dates (ISO). */
    val cards: List<Pair<ReviewCard, String?>> = emptyList(),
    /** Words to learn: their pack's id and the word. */
    val words: List<Pair<String, PackWord>> = emptyList(),
    val scenes: List<SceneSpec> = emptyList(),
    val villagers: List<Villager> = emptyList(),
    /** Everyone's voice profile (the node's, [Clips.profiles]). */
    val profiles: Map<String, VoiceProfile> = emptyMap(),
    /** The voice store's index: only these clips are played. */
    val index: ClipIndex = emptyMap(),
    /** The learner's level in the target language: the stories are told at it. */
    val level: String = "A1",
    val target: Lang = Lang.SL,
    val base: Lang = Lang.EN,
    /** The car's audio drills ([Drills]: the bridge's, else the app's own). */
    val drills: List<Drill> = emptyList(),
    /** The grammar book's rules not introduced to the learner yet ([si.lanisce.lani.game.Mastery.NOT_YET]): their drills are left out. */
    val notYet: Set<String> = emptySet(),
    /** What the quiz asks about and how its answers count ([RoadQuiz]). */
    val quiz: QuizInputs = QuizInputs(),
)

/** Everything on the road, from what the app has: pure, so the tests build it too. */
object RoadGather {
    /** At most this many new words (the packs' 545 words not learned yet, with their examples, are about three hours). */
    const val MAX_WORDS = 600

    /** At most this many phrases to shadow (each once, from the scenes in their order). */
    const val MAX_PHRASES = 600

    /** Cards due up to this many days after the day the road is got ready, so a drive the next day has its reviews. */
    const val DUE_AHEAD_DAYS = 3L

    /**
     * The review cards of GET /state ([raw]) due by [until] (ISO date), with their due dates: the ones with both sides and
     * not an error pattern (whose content is the learner's wrong answer), as the dashboard reads them.
     */
    fun cards(raw: String, until: String): List<Pair<ReviewCard, String?>> {
        val root = json.parseToJsonElement(raw).jsonObject
        val items = ((root["databases"] as? JsonObject)?.get("spaced_repetition") as? JsonObject)?.get("items") as? JsonObject ?: return emptyList()
        return items.mapNotNull { (id, v) ->
            val o = v as? JsonObject ?: return@mapNotNull null
            fun str(k: String) = o[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            if (str("type") == "error_pattern") return@mapNotNull null
            val front = str("content") ?: return@mapNotNull null
            val back = str("answer") ?: return@mapNotNull null
            val due = str("due_date")
            if (due != null && due.take(10) > until) return@mapNotNull null
            ReviewCard(id, front, back, str("type") ?: "vocabulary") to due?.take(10)
        }.sortedBy { it.second ?: "" }
    }

    /**
     * The packs whose words the road teaches, in order: the ones under way (some words learned) first, then the others
     * from the easiest level; done ones and festivals' left out.
     */
    fun packOrder(packs: List<PackInfo>): List<String> =
        packs.filter { !it.done && it.festival == null && it.total > 0 }
            .withIndex()
            .sortedWith(compareBy({ if (it.value.learned > 0) 0 else 1 }, { Stats.LEVELS.indexOf(it.value.level).let { l -> if (l < 0) 99 else l } }, { it.index }))
            .map { it.value.id }

    /**
     * The voices someone's lines are played in, best first: their own voice (when they have one), their archetype's at
     * their profile, their speaker, their gender's narrator, the female narrator. [art]: the sprite of someone who isn't a
     * villager (then the sprite's voice).
     */
    fun voices(villager: Villager?, art: String?, profiles: Map<String, VoiceProfile>): List<String> {
        if (villager == null) return Clips.chain(art?.let(SceneWords::voiceOf) ?: Clips.FEMALE)
        val p = profiles[villager.id]
        val shared = (listOfNotNull(p?.takeIf { !it.own }?.speaker, p?.archetype) + Clips.chain(villager.speakerVoice, villager.voice)).distinct()
        return if (p?.own == true) (listOf(p.speaker) + shared).distinct() else shared
    }

    /**
     * The road's items from [inputs]: the cards, the words (with their examples), the scenes' dialogs and the stories at the
     * learner's level (each evening with its setup and recaps, [RoadStory]), the dialogs' phrases to shadow (of the scenes
     * at the learner's level or below), the drills' items ([RoadDrills.items]: of the rules introduced, a riddle in its
     * teller's voices), only what has its clips in [RoadInputs.index]; and the quiz's questions ([RoadQuiz.items]: what the
     * voice store lacks of their options voiced while getting ready). [youSay] makes "You say: …" in the base language,
     * [drillWords] a build's "Say: …" and "Now add: …", [quizWords] what the quiz says.
     */
    fun library(
        inputs: RoadInputs,
        youSay: (String) -> String,
        now: Long = System.currentTimeMillis(),
        clips: ClipLookup = ClipLookup.of(inputs.index),
        drillWords: RoadDrills.Words = RoadDrills.Words.EN,
        quizWords: QuizWords = QuizWords.EN,
    ): RoadLibrary {
        val base = inputs.base
        val byId = inputs.villagers.associateBy { it.id }
        val items = mutableListOf<RoadItem>()
        inputs.cards.forEach { (c, due) -> RoadPlay.card(c, due, clips)?.let(items::add) }
        inputs.words.distinctBy { it.first + "/" + it.second.id }.take(MAX_WORDS).forEach { (pack, w) ->
            val example = w.example?.let { s -> exampleMeaning(w, base)?.let { s to it } }
            RoadPlay.word(pack, w, w.meaningIn(base), example, clips)?.let(items::add)
        }
        val scenes = inputs.scenes.filter { it.language == inputs.target.code }
        val phrases = mutableListOf<RoadItem>()
        val levelAt = Stats.LEVELS.indexOf(inputs.level.uppercase()).let { if (it < 0) Stats.LEVELS.size else it }
        for (scene in scenes) {
            val people = scene.people.associateBy { it.id }
            fun voicesOf(who: String?): List<String> {
                val p = who?.let(people::get) ?: return RoadPlay.NARRATOR
                return voices(p.villager?.let(byId::get), p.art, inputs.profiles)
            }
            for (d in scene.dialogs) RoadPlay.dialog(scene.id, scene.title, d, ::voicesOf, youSay, clips)?.let(items::add)
            // the phrases to shadow: of the scenes at the learner's level or below
            if (Stats.LEVELS.indexOf(scene.level.uppercase()) <= levelAt) {
                for (d in scene.dialogs) RoadPlay.phrases(d.lines, ::voicesOf, clips).forEach { phrases += RoadPlay.phrase(it, scene.title) }
            }
        }
        val told = mutableSetOf<String>()
        for (story in scenes.flatMap { it.stories }) {
            if (story.language != inputs.target.code || !told.add(story.id)) continue
            val level = Stories.levelFor(story, inputs.level) ?: continue
            val teller = voices(byId[story.teller], null, inputs.profiles)
            val parts = story.parts
            parts.forEachIndexed { i, part ->
                val telling = part.levels[level] ?: return@forEachIndexed
                val title = if (parts.size > 1) "${story.title} (${i + 1}/${parts.size})" else story.title
                val notes = story.noteOf(i, level)?.text.orEmpty()
                RoadPlay.story(
                    story.id, i, title, telling.lines, teller, youSay, clips,
                    level = level, teaser = part.teaser?.en, notes = notes, lang = story.language, base = base.code,
                )?.let(items::add)
            }
        }
        items += phrases.distinctBy { it.id }.take(MAX_PHRASES)
        items += RoadDrills.items(
            inputs.drills, inputs.target.code, base.code, inputs.level, inputs.notYet::contains,
            teller = { id -> voices(id?.let(byId::get), null, inputs.profiles) }, words = drillWords, clips = clips,
        )
        items += RoadQuiz.items(
            inputs, clips, quizWords,
            voicesOf = { scene, who ->
                val p = who?.let { w -> scene.people.firstOrNull { it.id == w } }
                if (p == null) RoadPlay.NARRATOR else voices(p.villager?.let(byId::get), p.art, inputs.profiles)
            },
            teller = { id -> voices(id?.let(byId::get), null, inputs.profiles) },
        )
        return RoadLibrary(
            now, base.code, items.distinctBy { it.id },
            target = inputs.target.code, played = inputs.quiz.played, dialogWords = inputs.quiz.dialogWords,
        )
    }

    /** A pack word's example in the learner's base language, else English. */
    private fun exampleMeaning(w: PackWord, base: Lang): String? = when (base) {
        Lang.EN -> w.exampleEn
        Lang.DE -> w.exampleDe ?: w.exampleEn
        Lang.IT -> w.exampleIt ?: w.exampleEn
        Lang.SL -> w.exampleSl ?: w.exampleEn
    }?.takeIf { it.isNotBlank() }

    /** "5 h 20 min", "40 min": how long [seconds] of listening is. */
    fun duration(seconds: Double): String {
        val m = (seconds / 60).toInt()
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }
}
