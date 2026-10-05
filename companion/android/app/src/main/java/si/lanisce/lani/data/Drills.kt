package si.lanisce.lani.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import si.lanisce.lani.l10n.Lang

/**
 * The car's audio drills (lani.drill/v0, companion/bridge/src/drills.ts; companion/README.md "Im Auto · In the car"):
 * hands-free practice for "🚗 Za pot · For the road", a prompt, a pause to say it aloud, the answer. Curated in
 * companion/drills/<language>/, bundled in the app (resources drills/<language>/, drills/index.json) and served by the
 * bridge (GET /drills), whose copy wins.
 */
enum class DrillKind(val code: String) {
    /** "🔄 Preobrat · Transformations": a sentence and an instruction ("Into the past"), the sentence changed. */
    TRANSFORM("transform"),
    /** "⚡ Hitri odziv · Rapid fire": a number, a time, a day in the base language, the word at once. */
    RAPID("rapid"),
    /** "🧱 Gradnja stavkov · Sentence building": a sentence grown in steps ("Now add: tomorrow"). */
    BUILD("build"),
    /** "🕵️ Uganke · Riddles": the storyteller's clues to a thing, an animal, a person or a place of the village. */
    RIDDLE("riddle");

    companion object {
        fun of(code: String?): DrillKind? = entries.firstOrNull { it.code == code }
    }
}

/** A text said in the drill's language with its meanings in the bases ([texts] by language code). */
data class DrillText(val texts: Map<String, String>) {
    /** What is said, in the drill's [language]. */
    fun said(language: String): String = texts[language].orEmpty()

    /** What it means in [base], else in English. */
    fun meaning(base: String): String = Grammar.text(texts, base)
}

/** A sentence heard ([from]) and what it becomes ([to]), said by the learner; [instruction] the item's own ("Three."). */
data class TransformItem(val from: DrillText, val to: DrillText, val instruction: DrillText? = null)

/** A set of one change of one [rule] (a grammar book page): its [instruction] ("V preteklik · Into the past") and items. */
data class TransformSet(val id: String, val rule: String, val level: String, val instruction: DrillText, val items: List<TransformItem>)

/**
 * A rapid-fire answer: [text] said in the drill's language and asked in the bases; [key] stable in its set (a number's
 * figure, else the answer); [figure] a generated number's ("21"), shown on the car's screen.
 */
data class RapidItem(val key: String, val text: DrillText, val figure: String? = null)

/** A set of rapid fire: its [items] (the curated answers, then the [numbers] generated). */
data class RapidSet(val id: String, val level: String, val rule: String?, val title: DrillText, val curated: List<DrillText>, val numbers: List<Int>) {
    /** Every answer: the curated ones, then the numbers in [language] ([Drills.number]), asked in the bases. */
    fun items(language: String): List<RapidItem> =
        curated.map { RapidItem(Drills.slug(it.said(language)), it) } + numbers.mapNotNull { n -> Drills.number(n, language)?.let { RapidItem("$n", it, "$n") } }
}

/** A step of a sentence grown: what it adds ([add], in the bases; null for the first, said whole) and the sentence so far. */
data class BuildStep(val add: DrillText?, val text: DrillText)

/** A sentence grown in steps; [rules]: the grammar book's pages it asks for beyond the obvious. */
data class Build(val id: String, val level: String, val rules: List<String>, val steps: List<BuildStep>)

/** A riddle: the teller's [clues], the question ([ask]: "Kaj sem?") and the [answer] ("Miza!"); of a pack [word] ("v-kuhinji/miza") or a [villager]. */
data class Riddle(
    val id: String,
    val level: String,
    val clues: List<DrillText>,
    val ask: DrillText,
    val answer: DrillText,
    val word: String? = null,
    val villager: String? = null,
)

/** A drill: its [kind]'s parts ([transforms], [rapid], [builds], [riddles]); a riddle drill's [teller] tells them. */
data class Drill(
    val id: String,
    val kind: DrillKind,
    val language: String,
    val emoji: String,
    val title: DrillText,
    val teller: String? = null,
    val transforms: List<TransformSet> = emptyList(),
    val rapid: List<RapidSet> = emptyList(),
    val builds: List<Build> = emptyList(),
    val riddles: List<Riddle> = emptyList(),
) {
    /** "Preobrat · Transformations" for a learner explaining in [base]. */
    fun titleShown(base: String): String {
        val t = title.said(language).ifBlank { title.meaning(base) }
        val b = title.meaning(base)
        return if (base != language && b != t) "$t · $b" else t
    }
}

/** Reading the drills: the bundled ones, the bridge's, and the numbers generated. Pure. */
object Drills {
    const val SCHEMA = "lani.drill/v0"

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonElement?.text(): DrillText? =
        (this as? JsonObject)?.mapNotNull { (k, v) -> v.str()?.let { k to it } }?.toMap()?.takeIf { it.isNotEmpty() }?.let(::DrillText)
    private fun JsonElement?.list(): List<JsonObject> = (this as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    private fun JsonElement?.strings(): List<String> = (this as? JsonArray).orEmpty().mapNotNull { it.str() }

    /**
     * A drill as the bridge serves it (or as the app bundles it); null when it lacks what it can't do without (an id, a
     * kind this app knows). Parts it can't read are left out, so a newer bridge's drill still plays.
     */
    fun parse(o: JsonObject): Drill? {
        val id = o["id"].str() ?: return null
        val kind = DrillKind.of(o["kind"].str()) ?: return null
        val language = o["language"].str() ?: "sl"
        fun level(x: JsonObject) = x["level"].str() ?: "A1"
        val transforms = if (kind != DrillKind.TRANSFORM) emptyList() else o["sets"].list().mapNotNull { s ->
            val items = s["items"].list().mapNotNull { i ->
                val from = i["from"].text()?.takeIf { it.said(language).isNotEmpty() } ?: return@mapNotNull null
                val to = i["to"].text()?.takeIf { it.said(language).isNotEmpty() } ?: return@mapNotNull null
                TransformItem(from, to, i["do"].text())
            }
            TransformSet(s["id"].str() ?: return@mapNotNull null, s["rule"].str() ?: return@mapNotNull null, level(s), s["do"].text() ?: return@mapNotNull null, items)
                .takeIf { items.isNotEmpty() }
        }
        val rapid = if (kind != DrillKind.RAPID) emptyList() else o["sets"].list().mapNotNull { s ->
            val items = (s["items"] as? JsonArray).orEmpty().mapNotNull { it.text()?.takeIf { t -> t.said(language).isNotEmpty() } }
            val numbers = s["numbers"].list().flatMap(::numbers).distinct()
            RapidSet(s["id"].str() ?: return@mapNotNull null, level(s), s["rule"].str(), s["title"].text() ?: DrillText(emptyMap()), items, numbers)
                .takeIf { items.isNotEmpty() || numbers.isNotEmpty() }
        }
        val builds = if (kind != DrillKind.BUILD) emptyList() else o["builds"].list().mapNotNull { b ->
            val steps = b["steps"].list().mapNotNull { s ->
                val text = DrillText(s.filterKeys { it != "add" }.mapNotNull { (k, v) -> v.str()?.let { k to it } }.toMap())
                BuildStep(s["add"].text(), text).takeIf { text.said(language).isNotEmpty() }
            }
            Build(b["id"].str() ?: return@mapNotNull null, level(b), b["rules"].strings(), steps).takeIf { steps.size >= 2 }
        }
        val riddles = if (kind != DrillKind.RIDDLE) emptyList() else o["riddles"].list().mapNotNull { r ->
            val clues = (r["clues"] as? JsonArray).orEmpty().mapNotNull { it.text()?.takeIf { t -> t.said(language).isNotEmpty() } }
            val ask = r["ask"].text()?.takeIf { it.said(language).isNotEmpty() } ?: return@mapNotNull null
            val answer = r["answer"].text()?.takeIf { it.said(language).isNotEmpty() } ?: return@mapNotNull null
            Riddle(r["id"].str() ?: return@mapNotNull null, level(r), clues, ask, answer, r["word"].str(), r["villager"].str()).takeIf { clues.size >= 2 }
        }
        return Drill(
            id = id,
            kind = kind,
            language = language,
            emoji = o["emoji"].str() ?: "🚗",
            title = o["title"].text() ?: DrillText(mapOf("en" to id)),
            teller = o["teller"].str(),
            transforms = transforms,
            rapid = rapid,
            builds = builds,
            riddles = riddles,
        )
    }

    /** A rapid set's range ({"from": 1, "to": 100, "step": 1}) or list ({"list": [101, 250]}) of numbers, 0 … 999. */
    private fun numbers(o: JsonObject): List<Int> {
        fun int(k: String) = (o[k] as? JsonPrimitive)?.intOrNull
        val list = (o["list"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
        val all = list ?: run {
            val from = int("from") ?: return emptyList()
            val to = int("to") ?: return emptyList()
            (from..to step (int("step") ?: 1).coerceAtLeast(1)).toList()
        }
        return all.filter { it in 0..999 }
    }

    /**
     * Number [n] as a rapid-fire answer in [language]: said as it is counted there ([SpokenNumbers]: "enaindvajset"),
     * asked in each base as its number ("twenty-one", "einundzwanzig"); null in a language the app doesn't count in.
     */
    fun number(n: Int, language: String): DrillText? {
        if (Lang.entries.none { it.code == language }) return null
        return DrillText(Lang.entries.associate { it.code to SpokenNumbers.forms(n, it).first() })
    }

    /** "Ura je pol štirih." → "ura-je-pol-stirih": a text as a stable key (the car's items, heard or not). */
    fun slug(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60).trimEnd('-')

    /** GET /drills: the drills in the bridge's order. */
    fun parseList(raw: String): List<Drill> =
        (json.parseToJsonElement(si.lanisce.lani.l10n.Learner.current.renderJson(raw)) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parse) }

    /**
     * The drills of [language] the app bundles (companion/drills/<language>/, resources drills/<language>/ with
     * drills/index.json listing them: app/build.gradle.kts), in their files' order; none for a language without any.
     */
    fun bundled(language: String): List<Drill> {
        val index = resource("index.json")?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return emptyList()
        return index[language].strings().mapNotNull { id ->
            resource("$language/$id.json")?.let { raw -> runCatching { parse(json.parseToJsonElement(raw) as JsonObject) }.getOrNull() }
        }.filter { it.language == language }
    }

    private fun resource(path: String): String? =
        Drills::class.java.getResourceAsStream("/drills/$path")?.use { si.lanisce.lani.l10n.Learner.current.renderJson(it.readBytes().decodeToString()) }

    /** The drills played: the bridge's (null: an older bridge without them), then the bundled ones it doesn't have. */
    fun merge(bundled: List<Drill>, bridge: List<Drill>?): List<Drill> {
        if (bridge == null) return bundled
        val ids = bridge.map { it.id }.toSet()
        return bridge + bundled.filter { it.id !in ids }
    }
}
