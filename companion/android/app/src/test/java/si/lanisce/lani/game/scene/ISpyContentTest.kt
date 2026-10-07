package si.lanisce.lani.game.scene

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.render.PixelCanvas
import java.io.File

/**
 * The clues of «Vidim, vidim» (companion/ispy, companion/SCENES.md "I spy"), every one of every scene: there for every thing
 * the scenes draw, true to the grammar and to the picture. Slovene is checked against the dictionary (companion/lexicon/
 * sl.json, Wiktionary): an adjective's ending by its noun's gender and number, the case a preposition takes and the noun's
 * form in it, the pronoun that stands for the thing, s/z and k/h by the next sound, the verb's number, never the answer's
 * own word; "where" against the picture (each art drawn as the scene view draws it, at each level of a room): "na mizi" is
 * on the table's top, "pod oknom" under the window. Italian and German: the adjective and the pronoun by the noun's gender.
 */
class ISpyContentTest {
    private val companion = CuratedContent.companion
    private val dir = companion.resolve("ispy")

    private fun obj(f: File) = json.parseToJsonElement(f.readText()).jsonObject
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.strings(k: String) = (this[k] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    /** Every scene file, as its file has it (the objects' words to resolve in the packs). */
    private val scenes: List<JsonObject> by lazy {
        (companion.resolve("scenes").listFiles { f -> f.extension == "json" }.orEmpty().toList() +
            companion.resolve("cultures").listFiles().orEmpty().flatMap { it.resolve("scenes").listFiles { f -> f.extension == "json" }.orEmpty().toList() })
            .sortedBy { it.name }.map(::obj)
    }

    /** pack id → word id → the word (its texts, gender, plural). */
    private val packs: Map<String, Map<String, JsonObject>> by lazy {
        val files = companion.resolve("packs").walkTopDown().filter { it.isFile && it.extension == "json" } +
            companion.resolve("cultures").listFiles().orEmpty().asSequence().flatMap { c -> c.resolve("packs").listFiles { f -> f.extension == "json" }.orEmpty().asSequence() }
        files.mapNotNull { f -> runCatching { obj(f) }.getOrNull() }.associate { p ->
            p.str("id")!! to p["words"]!!.jsonArray.map { it.jsonObject }.associateBy { it.str("id")!! }
        }
    }

    /** language → page id → level. */
    private val pages: Map<String, Map<String, String>> by lazy {
        companion.resolve("grammar").listFiles().orEmpty().filter { it.isDirectory }.associate { d ->
            d.name to d.listFiles { f -> f.extension == "json" }.orEmpty().associate { f -> obj(f).let { it.str("id")!! to (it.str("level") ?: "A1") } }
        }
    }

    private data class Thing(val slot: String, val word: String, val gender: String?, val plural: Boolean, val needs: Boolean)

    private fun things(scene: JsonObject): List<Thing> {
        val lang = scene.str("language") ?: "sl"
        return scene["objects"]!!.jsonArray.map { it.jsonObject }.map { o ->
            val pack = o.str("pack") ?: scene.str("pack")!!
            val w = packs[pack]?.get(o.str("word")!!) ?: error("${scene.str("id")}/${o.str("slot")}: no word ${o.str("word")} in $pack")
            val word = w.str(lang) ?: w.str("sl")!!
            Thing(o.str("slot")!!, word, w.str("gender"), w.str("plural") == word, o["needs"] != null)
        }
    }

    @Test fun `every scene has its clues, every thing it draws at least three, two of them at A1`() {
        val fails = ArrayList<String>()
        var n = 0
        for (s in scenes) {
            val id = s.str("id")!!
            val f = dir.resolve("scenes/$id.json")
            if (!f.isFile) { fails += "$id: no companion/ispy/scenes/$id.json"; continue }
            val o = obj(f)
            val lang = s.str("language") ?: "sl"
            if (o.str("schema") != "lani.ispy/v0") fails += "$id: schema"
            if (o.str("scene") != id || (o.str("language") ?: "sl") != lang) fails += "$id: scene or language"
            if ((o["machine_written"] as? JsonPrimitive)?.contentOrNull != "true" || o.str("review").isNullOrBlank()) fails += "$id: machine_written and its review"
            val given = o["things"]!!.jsonObject
            val ts = things(s)
            for (t in ts) if (t.slot !in given) fails += "$id/${t.slot}: no clues"
            for (slot in given.keys) if (ts.none { it.slot == slot }) fails += "$id/$slot: no thing of the scene"
            // as the app reads them: what a beginner (nothing above A1 introduced) and an A2 learner get
            val book = ISpy.parseBook(f.readText(), "en")!!
            val above = { lv: String -> setOf("A2", "B1", "B2", "C1", "C2").let { if (lv == "A1") it else it - "A2" } }
            for (t in ts) {
                val thing = book.things[t.slot] ?: continue
                n += thing.clues.size
                if (thing.clues.size < 3) fails += "$id/${t.slot}: ${thing.clues.size} clues"
                for (lv in listOf("A1", "A2")) {
                    val got = ISpy.clues(thing, { p -> (pages[lang]?.get(p) ?: "A1") in above(lv) }, { true })
                    if (got.size < 2) fails += "$id/${t.slot}: ${got.size} clues for a learner at $lv"
                }
            }
        }
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
        assertTrue("$n clues", n > 1000)
    }

    @Test fun `every clue is a sentence with its translation, of a known kind, naming pages the book has`() {
        val fails = ArrayList<String>()
        forEachClue { scene, lang, t, c, at ->
            val text = c.str(lang)
            if (c.str("kind") !in ISpy.KINDS) fails += "$at: kind ${c.str("kind")}"
            if (text.isNullOrBlank() || !text.first().isUpperCase() || text.last() !in ".!") fails += "$at: a sentence in $lang"
            if (c.str("en").isNullOrBlank() || (lang != "sl" && c.str("sl").isNullOrBlank())) fails += "$at: its translations"
            for (p in c.strings("grammar")) if (pages[lang]?.containsKey(p) != true) fails += "$at: no page $lang/$p"
            if (lang == "sl" && c.strings("grammar").isEmpty()) fails += "$at: names its grammar"
            c.str("ref")?.let { r -> if (things(scene).none { it.slot == r }) fails += "$at: ref $r is no thing of the scene" }
            if (c.str("ref") == t.slot) fails += "$at: refers to itself"
        }
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }

    // --- Slovene ------------------------------------------------------------------------------------------------------

    @Test fun `the Slovene agrees, adjectives, prepositions and their cases, pronouns, s and z, never the answer`() {
        val fails = ArrayList<String>()
        val guessed = HashSet<String>()
        forEachClue { _, lang, t, c, at ->
            if (lang != "sl") return@forEachClue
            val text = c.str("sl") ?: return@forEachClue
            val ws = Sl.words(text)
            val grammar = c.strings("grammar").toSet()
            val g = t.gender ?: run { fails += "$at: no gender"; return@forEachClue }
            val plural = t.plural || c.str("number") == "pl"
            val number = if (plural) "pl" else "sg"
            val kind = c.str("kind")
            // the answer's own word is never said
            val answer = Sl.formsOf(t.word)
            (answer intersect ws.toSet()).takeIf { it.isNotEmpty() }?.let { fails += "$at: says the answer ($it)" }
            // an adjective agreeing with the thing, and the copula's number
            c.str("adj")?.let { adj ->
                val want = Sl.adjective(adj, g, number, comparative = kind == "compare", guessed = guessed)
                when {
                    want.isEmpty() -> fails += "$at: no forms of $adj"
                    want.none { it in ws } -> fails += "$at: wants ${want.joinToString("/")} for $g $number"
                }
                if (kind == "compare") { if ("primernik" !in grammar) fails += "$at: names primernik" }
                else if ("pridevniki-ujemanje" !in grammar) fails += "$at: names pridevniki-ujemanje"
                val (cop, bad) = if (plural) "so" to "je" else "je" to "so"
                if (cop !in ws || bad in ws) fails += "$at: «$cop» for $number"
            }
            // a preposition with its case, and the noun after it
            c.str("prep")?.let { prep ->
                val case = c.str("case")
                if (case !in Sl.PREP_CASES[prep].orEmpty()) fails += "$at: $prep + $case"
                val page = Sl.CASE_PAGE[case]
                if (page != null && page !in grammar && !(case == "loc" && "kje-mestnik-orodnik" in grammar)) fails += "$at: names $page"
                val form = c.str("form").orEmpty()
                if ("$prep $form".lowercase() !in text.lowercase()) fails += "$at: says «$prep $form»?"
                val ref = c.str("ref")?.let { r -> things(sceneOf(at)).firstOrNull { it.slot == r } }
                val noun = ref?.word?.split(' ')?.last() ?: c.str("noun")
                val nounPlural = ref?.plural ?: (c.str("noun_number") == "pl")
                if (noun == null) fails += "$at: a preposition without its noun"
                else if (case != null) {
                    val ok = Sl.noun(noun, case, if (nounPlural || c.str("ref_number") == "pl") "plural" else "singular", guessed)
                    if (ok.isEmpty()) fails += "$at: no $case of $noun"
                    else if (form.split(' ').last().lowercase() !in ok) fails += "$at: $noun $case is ${ok.joinToString("/")}, not «$form»"
                }
            }
            // a pronoun for the thing
            c.str("pronoun")?.let { case ->
                val table = Sl.PRONOUNS[case] ?: run { fails += "$at: pronoun $case"; return@let }
                val want = if (plural) table.getValue("pl") else table.getValue(g)
                if (want !in ws) fails += "$at: «$want» for $g $number"
                (table.values.toSet() - want).filter { it in ws }.takeIf { it.isNotEmpty() }?.let { fails += "$at: another pronoun than «$want»: $it" }
                if (Sl.CASE_PAGE.getValue(case) !in grammar) fails += "$at: names ${Sl.CASE_PAGE[case]}"
            }
            if (kind == "does" && "glagoli-sedanjik" !in grammar) fails += "$at: names glagoli-sedanjik"
            c.str("verb")?.let { v ->
                val tag = ws.firstNotNullOfOrNull { Sl.verbTag(v, it) }
                val person = if (plural) "third-person plural present" else "third-person singular present"
                if (tag == null || person !in tag) fails += "$at: $v in the ${person.substringBefore(" present")}"
            }
            // s/z and k/h by the next word's first sound
            for (m in Regex("""(?<![\p{L}])([szkh])\s+(\p{L}+)""").findAll(text.lowercase())) {
                val (p, next) = m.destructured
                if (!Sl.sz(p, next)) fails += "$at: «$p $next»"
            }
        }
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
        println("forms the regular paradigm gave (no dictionary entry): ${guessed.sorted()}")
    }

    // --- Italian, German -----------------------------------------------------------------------------------------------

    @Test fun `the Italian adjective and the German pronoun go by the noun's gender`() {
        val fails = ArrayList<String>()
        forEachClue { _, lang, t, c, at ->
            val text = c.str(lang) ?: return@forEachClue
            val g = t.gender
            when (lang) {
                "it" -> c.str("adj")?.let { adj ->
                    val want = if (g == "f" && adj.endsWith("o")) adj.dropLast(1) + "a" else adj
                    if (want !in text.lowercase().split(Regex("""[^\p{L}']+"""))) fails += "$at: wants «$want» for $g"
                }
                "de" -> {
                    val first = text.substringBefore(' ')
                    val bad = when (g) {
                        "m" -> first == "Sie"
                        "f" -> first == "Er"
                        "n" -> first == "Er" || first == "Sie"
                        else -> false
                    }
                    if (bad) fails += "$at: «$first» for a ${g} noun"
                }
            }
        }
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }

    // --- the picture ------------------------------------------------------------------------------------------------------

    @Test fun `where a clue says a thing is, the picture has it, at every level the two are drawn together`() {
        val fails = ArrayList<String>()
        val rooms = mapOf("smithy" to BuildingType.SMITHY, "school" to BuildingType.SCHOOL)
        for (s in scenes) {
            val id = s.str("id")!!
            val art = s.str("art")!!
            val f = dir.resolve("scenes/$id.json").takeIf { it.isFile } ?: continue
            val clues = obj(f)["things"]!!.jsonObject.flatMap { (slot, t) -> t.jsonObject["clues"]!!.jsonArray.map { slot to it.jsonObject } }
                .filter { (_, c) -> c.str("ref") != null && c.str("kind") in setOf("where") }
            if (clues.isEmpty()) continue
            val objects = s["objects"]!!.jsonArray.map { it.jsonObject.str("slot")!! }.toSet()
            val worlds = if (art in setOf("kitchen", "livingroom", "workshop", "cellar", "attic", "smithy", "school"))
                (1..3).map { SceneWorld.room(it, rooms[art] ?: BuildingType.HOUSE) } else listOf(SceneWorld.ALL)
            val frames = worlds.flatMap { w -> listOf(12f, 22.5f).map { h -> SceneFrame(time = 2.0, hour = h, month = 7, objects = objects, world = w, wild = Wildlife.all(art)) } }
            val painter = ScenePainters.create(art)
            val hits = frames.map { fr -> painter.render(PixelCanvas(240, 160), fr).filter { it.target is SceneTarget.Thing }.associateBy { (it.target as SceneTarget.Thing).slot } }
            for ((slot, c) in clues) {
                val ref = c.str("ref")!!
                val prep = c.str("prep") ?: continue
                val both = hits.filter { slot in it && ref in it }
                if (both.isEmpty()) fails += "$id/$slot «${c.str(s.str("language") ?: "sl")}»: never drawn with $ref"
                for (h in both) if (!Where.holds(prep, h.getValue(slot), h.getValue(ref))) {
                    fails += "$id/$slot «${c.str(s.str("language") ?: "sl")}»: ${h.getValue(slot).box()} is not «$prep» $ref ${h.getValue(ref).box()}"
                }
            }
        }
        assertTrue(fails.distinct().joinToString("\n"), fails.isEmpty())
    }

    private fun SceneHit.box() = "($left,$top,$right,$bottom)"

    /** Where one thing is to another in the picture, by their tap areas (canvas pixels, y down), roughly as an eye would say. */
    private object Where {
        fun holds(prep: String, t: SceneHit, r: SceneHit): Boolean {
            val cx = (t.left + t.right) / 2f
            val cy = (t.top + t.bottom) / 2f
            val ox = minOf(t.right, r.right) - maxOf(t.left, r.left)
            val gx = maxOf(r.left - t.right, t.left - r.right, 0)
            val gy = maxOf(r.top - t.bottom, t.top - r.bottom, 0)
            val gap = Math.hypot(gx.toDouble(), gy.toDouble())
            val rh = r.bottom - r.top
            return when (prep) {
                // on it: over its area, its foot on the top part or within it
                "na" -> cx in (r.left - 2f)..(r.right + 2f) && t.bottom in (r.top - 4)..(r.bottom + 2) && t.top < r.bottom
                "v" -> cx in (r.left - 1f)..(r.right + 1f) && cy in (r.top - 1f)..(r.bottom + 1f)
                "pod" -> ox > 0 && cy >= r.top + 0.35f * rh
                "nad" -> ox > 0 && t.bottom <= r.top + 0.5f * rh
                "pri", "ob", "zraven", "poleg", "blizu" -> gap <= 18.0
                "za" -> gap <= 10.0 && t.bottom <= r.bottom + 2
                "pred" -> gap <= 10.0 && t.bottom >= r.bottom - 6
                else -> true
            }
        }
    }

    // --- walking the clues -----------------------------------------------------------------------------------------------

    private val atScene = HashMap<String, JsonObject>()
    private fun sceneOf(at: String): JsonObject = atScene.getValue(at.substringBefore('/'))

    private fun forEachClue(f: (scene: JsonObject, lang: String, t: Thing, clue: JsonObject, at: String) -> Unit) {
        for (s in scenes) {
            val id = s.str("id")!!
            atScene[id] = s
            val file = dir.resolve("scenes/$id.json").takeIf { it.isFile } ?: continue
            val lang = s.str("language") ?: "sl"
            val ts = things(s).associateBy { it.slot }
            for ((slot, t) in obj(file)["things"]!!.jsonObject) {
                val thing = ts[slot] ?: continue
                val number = t.jsonObject.str("number")
                for ((i, c) in t.jsonObject["clues"]!!.jsonArray.withIndex()) {
                    val co = c.jsonObject.let { if (number != null && it["number"] == null) JsonObject(it + ("number" to JsonPrimitive(number))) else it }
                    f(s, lang, thing, co, "$id/$slot#$i «${co.str(lang)}»")
                }
            }
        }
    }

    // --- Slovene: the dictionary, and the rules the content goes by --------------------------------------------------------

    /**
     * The Slovene dictionary (companion/lexicon/sl.json: lemmas, their forms with the forms' grammar) as the checks read it;
     * a word it lacks takes the regular paradigm (said in the test's output) or a hand-checked table ([HAND]).
     */
    private object Sl {
        private val lex = json.parseToJsonElement(CuratedContent.companion.resolve("lexicon/sl.json").readText()).jsonObject
        private val lemmas = lex["lemmas"]!!.jsonArray.map { it.jsonObject }
        private val grammar = lex["grammar"]!!.jsonArray.map { it.jsonPrimitive.content }
        private val byLemma: Map<String, List<Int>> = lemmas.withIndex().groupBy({ it.value["lemma"]!!.jsonPrimitive.content }) { it.index }
        private val formsOf: Map<Int, List<Pair<String, String>>> = HashMap<Int, MutableList<Pair<String, String>>>().also { m ->
            for ((form, list) in lex["forms"]!!.jsonObject) for (e in list.jsonArray) {
                val a = e.jsonArray
                if (a.size > 1) m.getOrPut(a[0].jsonPrimitive.int) { ArrayList() } += form to grammar[a[1].jsonPrimitive.int]
            }
        }

        private fun ids(lemma: String, pos: String? = null) = byLemma[lemma].orEmpty().filter { pos == null || lemmas[it]["pos"]?.jsonPrimitive?.content == pos }

        private val CASES = setOf("nominative", "genitive", "dative", "accusative", "locative", "instrumental", "vocative")
        private val GENDERS = setOf("masculine", "feminine", "neuter")
        private val NUMBERS = setOf("singular", "dual", "plural")
        private val SHORT = mapOf("nom" to "nominative", "gen" to "genitive", "dat" to "dative", "acc" to "accusative", "loc" to "locative", "ins" to "instrumental")
        private val GENDER = mapOf("m" to "masculine", "f" to "feminine", "n" to "neuter")
        private val SOFT = setOf('c', 'č', 'š', 'ž', 'j')

        private data class Tag(val cases: Set<String>, val genders: Set<String>, val numbers: Set<String>, val rest: Set<String>)

        private fun tags(tag: String): List<Tag> = tag.split(';').map { alt ->
            val cs = HashSet<String>(); val gs = HashSet<String>(); val ns = HashSet<String>(); val rest = HashSet<String>()
            for (t in alt.replace(',', ' ').split(' ').filter { it.isNotBlank() }) {
                val parts = t.split('/')
                when {
                    parts.all { it in CASES } -> cs += parts
                    parts.all { it in GENDERS } -> gs += parts
                    parts.all { it in NUMBERS } -> ns += parts
                    else -> rest += t
                }
            }
            Tag(cs, gs, ns, rest)
        }

        /** Hand-checked forms of words the dictionary lacks: lemma → "case number" or "gender number" → form (machine-written). */
        val HAND = mapOf(
            "lesen" to mapOf("f sg" to "lesena", "n sg" to "leseno", "m pl" to "leseni", "f pl" to "lesene", "n pl" to "lesena"),
            "steklen" to mapOf("f sg" to "steklena", "n sg" to "stekleno", "m pl" to "stekleni", "f pl" to "steklene", "n pl" to "steklena"),
            "železen" to mapOf("f sg" to "železna", "n sg" to "železno", "m pl" to "železni", "f pl" to "železne", "n pl" to "železna"),
            "kamnit" to mapOf("f sg" to "kamnita", "n sg" to "kamnito", "m pl" to "kamniti", "f pl" to "kamnite", "n pl" to "kamnita"),
            "tla" to mapOf("loc plural" to "tleh", "gen plural" to "tal", "ins plural" to "tlemi"),
        )

        fun words(s: String): List<String> = Regex("""\p{L}+""").findAll(s.lowercase()).map { it.value }.toList()

        /** Every form of [word] (each word of it) the dictionary has, the words themselves too. */
        fun formsOf(word: String): Set<String> = word.lowercase().split(' ').flatMap { w -> listOf(w) + ids(w).flatMap { i -> formsOf[i].orEmpty().map { it.first } } }.toSet()

        /** Noun [lemma]'s forms in [case] ("loc") and [number] ("singular", "plural"). */
        fun noun(lemma: String, case: String, number: String, guessed: MutableSet<String>): Set<String> {
            HAND[lemma]?.let { h -> return setOfNotNull(h["$case $number"]) }
            val ids = ids(lemma, "noun")
            if (ids.isEmpty()) {
                guessed += "$lemma $case $number"
                return regular(lemma, case, number)
            }
            return ids.flatMap { i ->
                formsOf[i].orEmpty().filter { (_, tag) -> tags(tag).any { SHORT.getValue(case) in it.cases && number in it.numbers } }.map { it.first }
            }.toSet()
        }

        private fun regular(lemma: String, case: String, number: String): Set<String> {
            if (number != "singular") return emptySet()
            val st = lemma.dropLast(1)
            return setOf(
                when {
                    lemma.endsWith("a") -> mapOf("gen" to st + "e", "dat" to st + "i", "acc" to st + "o", "loc" to st + "i", "ins" to st + "o")
                    lemma.endsWith("o") || lemma.endsWith("e") -> mapOf("gen" to st + "a", "dat" to st + "u", "acc" to lemma, "loc" to st + "u", "ins" to st + if (st.last() in SOFT) "em" else "om")
                    else -> mapOf("gen" to lemma + "a", "dat" to lemma + "u", "acc" to lemma, "loc" to lemma + "u", "ins" to lemma + if (lemma.last() in SOFT) "em" else "om")
                }.getValue(case),
            )
        }

        /** The predicate (nominative) forms of adjective [lemma] for [gender] (m, f, n) and [number] (sg, pl). */
        fun adjective(lemma: String, gender: String, number: String, comparative: Boolean, guessed: MutableSet<String>): Set<String> {
            if (comparative || (lemma.endsWith("ši") || lemma.endsWith("ji")) && ids(lemma, "adj").isEmpty()) {
                val st = lemma.dropLast(1)
                return setOf(mapOf("m sg" to lemma, "f sg" to st + "a", "n sg" to st + "e", "m pl" to lemma, "f pl" to st + "e", "n pl" to st + "a").getValue("$gender $number"))
            }
            if (gender == "m" && number == "sg") return setOf(lemma)
            HAND[lemma]?.let { h -> return setOfNotNull(h["$gender $number"]) }
            val ids = ids(lemma, "adj")
            if (ids.isEmpty()) {
                if (listOf("en", "el", "er", "ek", "ec").any { lemma.endsWith(it) }) return emptySet()
                guessed += "$lemma $gender $number"
                val soft = lemma.last() in SOFT
                return setOf(mapOf("f sg" to lemma + "a", "n sg" to lemma + if (soft) "e" else "o", "m pl" to lemma + "i", "f pl" to lemma + "e", "n pl" to lemma + "a").getValue("$gender $number"))
            }
            val g = GENDER.getValue(gender)
            val n = if (number == "sg") "singular" else "plural"
            return ids.flatMap { i ->
                formsOf[i].orEmpty().filter { (_, tag) ->
                    tags(tag).any { "nominative" in it.cases && g in it.genders && n in it.numbers && it.rest.none { r -> r == "comparative" || r == "superlative" || r == "definite" } }
                }.map { it.first }
            }.toSet()
        }

        /** The grammar of [form] as a form of verb [lemma] ("third-person singular present"); null when it isn't one. */
        fun verbTag(lemma: String, form: String): String? = ids(lemma, "verb").firstNotNullOfOrNull { i -> formsOf[i].orEmpty().firstOrNull { it.first == form }?.second }

        /** The cases a preposition takes where a thing is (or what it is made of, what it's bigger than). */
        val PREP_CASES = mapOf(
            "v" to setOf("loc"), "na" to setOf("loc"), "pri" to setOf("loc"), "ob" to setOf("loc"), "po" to setOf("loc"),
            "pod" to setOf("ins"), "nad" to setOf("ins"), "za" to setOf("ins"), "pred" to setOf("ins"), "med" to setOf("ins"),
            "zraven" to setOf("gen"), "poleg" to setOf("gen"), "blizu" to setOf("gen"), "okrog" to setOf("gen"), "okoli" to setOf("gen"),
            "sredi" to setOf("gen"), "vrh" to setOf("gen"), "iz" to setOf("gen"), "od" to setOf("gen"), "do" to setOf("gen"), "brez" to setOf("gen"),
            "s" to setOf("ins"), "z" to setOf("ins"), "k" to setOf("dat"), "h" to setOf("dat"),
        )

        /** The grammar book's page of a case after a preposition. */
        val CASE_PAGE = mapOf("loc" to "mestnik", "ins" to "orodnik", "gen" to "rodilnik-predlogi", "acc" to "tozilnik", "dat" to "dajalnik")

        /** The 3rd person's pronoun for a thing in a case: m, n, f singular, and a plural. */
        val PRONOUNS = mapOf(
            "acc" to mapOf("m" to "ga", "n" to "ga", "f" to "jo", "pl" to "jih"),
            "loc" to mapOf("m" to "njem", "n" to "njem", "f" to "njej", "pl" to "njih"),
            "ins" to mapOf("m" to "njim", "n" to "njim", "f" to "njo", "pl" to "njimi"),
            "gen" to mapOf("m" to "njega", "n" to "njega", "f" to "nje", "pl" to "njih"),
            "dat" to mapOf("m" to "njemu", "n" to "njemu", "f" to "njej", "pl" to "njim"),
        )

        /** s before c č f h k p s š t, z before the rest; h before k and g, k before the rest. */
        fun sz(prep: String, next: String): Boolean = when (prep) {
            "s", "z" -> prep == if (next.first() in "cčfhkpsšt") "s" else "z"
            "k", "h" -> prep == if (next.first() in "kg") "h" else "k"
            else -> true
        }
    }
}
