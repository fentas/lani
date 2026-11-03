package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang

/**
 * English A2's units 7–12 (docs/plans/05-built/course-en-a2-b.md): some and any, how much, the comparative, the superlative,
 * the adverb, someone and anything, with their new pages and the mistakes they added to grading/en.json. What every unit of
 * the course must do (played at its level, every right answer right, no distractor even almost right, each base there) is
 * [EnglishCourseContentTest]'s; this is what these six add.
 */
class EnglishA2bContentTest {
    private val ids = listOf("some-and-any", "how-much", "comparatives", "superlatives", "adverbs", "someone-anything")
    private val course = checkNotNull(Courses.bundled("en")) { "the bundled English course" }
    private val bases = listOf("sl", "de", "it", "en")

    private fun en(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.EN)
    private fun wrong(answer: String, right: String) = assertEquals("$answer for $right", Verdict.WRONG, en(answer, right).verdict)
    private fun slip(answer: String, right: String) {
        val r = en(answer, right)
        assertEquals("$answer for $right", Verdict.ALMOST to Hint.TYPO, r.verdict to r.hint)
    }

    private fun sheetItems(unit: String): List<SheetItem> {
        val u = checkNotNull(course.units[unit]) { unit }
        return (u.worksheets.flatMap { it.exercises } + u.test.exercises).filter { it !is Exercise.Sort }.flatMap { Sheets.items(it, Lang.EN) }
    }

    @Test fun `units 7 to 12 of A2 are written in the outline's order, at the size of the level`() {
        val refs = course.refs("A2")
        assertEquals(ids, refs.subList(6, 12).map { it.id })
        for (id in ids) {
            val u = checkNotNull(course.units[id]) { "$id isn't written" }
            assertEquals(id, "A2", u.level)
            assertTrue("$id: ${u.intro?.steps?.size} steps", u.intro?.steps?.size in 5..6)
            assertEquals("$id: worksheets", 3, u.worksheets.size)
            assertEquals("$id: pass", 80, u.test.pass)
            assertTrue("$id: flagged for a native check", u.machineWritten && u.review.orEmpty().contains("native speaker"))
        }
    }

    @Test fun `each new page has a card of its own in every base, and is the first page of the unit that brings it`() {
        val book = Grammar.bundled("en").associateBy { it.id }
        for (id in listOf("comparatives", "adverbs", "indefinite-pronouns")) {
            val page = checkNotNull(book[id]) { "no page $id" }
            assertEquals(id, "A2", page.level)
            val card = checkNotNull(page.card) { "$id: no card of its own" }
            for (b in bases) {
                assertTrue("$id: the card's rule in $b", !card.rule[b].isNullOrBlank())
                assertTrue("$id: the card's tip in $b", !card.tip?.get(b).isNullOrBlank())
            }
        }
        assertEquals("comparatives", course.units.getValue("comparatives").grammar.first())
        assertEquals("comparatives", course.units.getValue("superlatives").grammar.first())
        assertEquals("adverbs", course.units.getValue("adverbs").grammar.first())
        assertEquals("indefinite-pronouns", course.units.getValue("someone-anything").grammar.first())
    }

    @Test fun `than and then for each other are wrong, never a slip`() {
        wrong("older then Jack", "older than Jack")
        wrong("then", "than")
        wrong("than", "then")
        slip("tahn", "than")
    }

    @Test fun `a comparative's and a superlative's spelling mistakes are wrong, each only for a word of its rule`() {
        for ((typed, right) in listOf(
            "biger" to "bigger", "hoter" to "hotter", "thiner" to "thinner", "weter" to "wetter",
            "bigest" to "biggest", "hotest" to "hottest", "thinest" to "thinnest",
            "happyer" to "happier", "busyer" to "busier", "easyer" to "easier",
            "happyest" to "happiest", "busyest" to "busiest", "the busyest" to "the busiest",
            "gooder" to "better", "badder" to "worse", "goodest" to "best",
            "worse" to "worst", "worst" to "worse", "bigger" to "biggest", "older" to "oldest", "more" to "most",
        )) wrong(typed, right)
        // the rest stays a slip of the keyboard: a letter of the stem, the double l of a word that hasn't that rule
        slip("taler", "taller")
        slip("olfer", "older")
        slip("bigegr", "bigger")
        slip("hapier", "happier")
    }

    @Test fun `an adverb's spelling and its adjective are wrong, usualy and a slip in the stem forgiven`() {
        for ((typed, right) in listOf(
            "carefuly" to "carefully", "beautifuly" to "beautifully", "happyly" to "happily", "easyly" to "easily", "angryly" to "angrily",
            "careful" to "carefully", "happy" to "happily", "quietly" to "quiet",
            "fastly" to "fast", "hardly" to "hard", "goodly" to "well",
        )) wrong(typed, right)
        slip("usualy", "usually")
        slip("quitely", "quietly")
        slip("qiuckly", "quickly")
    }

    @Test fun `someone and no one - each kind is its own word, and no one takes nobody and the hyphen only when listed`() {
        for ((typed, right) in listOf(
            "something" to "anything", "anything" to "something", "nothing" to "anything", "someone" to "anyone",
            "everyone" to "anyone", "somewhere" to "anywhere", "noone" to "no one", "anyone" to "no one",
        )) wrong(typed, right)
        val table = sheetItems("someone-anything").first { "no one" in it.accept }
        assertTrue(table.accept.containsAll(listOf("nobody", "no-one")))
        assertEquals(Verdict.CORRECT, Grading.check("no-one", table.accept, Lang.EN).verdict)
    }

    @Test fun `what a native speaker says too is right, labelled with the standard form - walks slow, some in a question`() {
        for ((unit, standard, said) in listOf(
            Triple("adverbs", "quickly", "quick"),
            Triple("adverbs", "slowly", "slow"),
            Triple("adverbs", "clearly", "clear"),
            Triple("adverbs", "Sam walks slowly.", "Sam walks slow."),
            Triple("adverbs", "badly", "bad"),
            Triple("some-and-any", "Is there any hay in the barn?", "Is there some hay in the barn?"),
            Triple("someone-anything", "anything", "something"),
            Triple("how-much", "There aren't many boats on the lake.", "There's not many boats on the lake."),
            Triple("how-much", "a lot of", "loads of"),
            Triple("some-and-any", "There aren't any walkers at the inn.", "There's no walkers at the inn."),
            Triple("superlatives", "Alfie is the youngest.", "Alfie is youngest."),
            Triple("superlatives", "the best", "best"),
            Triple("adverbs", "Mr Tyson drives carefully.", "Mr Tyson drives careful."),
            Triple("adverbs", "Poppy sings beautifully.", "Poppy sings beautiful."),
        )) {
            val item = sheetItems(unit).first { it.accept.first() == standard && said in it.spoken }
            val m = Sheets.check(item, said, Lang.EN)
            assertEquals("$unit: $said", Verdict.CORRECT, m.verdict)
            assertEquals("$unit: $said", standard, m.standard)
        }
    }

    /** Every quick question and choice of [unit]. */
    private fun choices(unit: String): List<Exercise.Choice> {
        val u = course.units.getValue(unit)
        return u.intro?.steps.orEmpty().mapNotNull { it.ask as? Exercise.Choice } + (u.worksheets.flatMap { it.exercises } + u.test.exercises).filterIsInstance<Exercise.Choice>()
    }

    /** What the sheet asks in [item] with its gap's sentence: its first answer, and the text before it. */
    private fun SheetItem.before() = frame.substringBefore(Sheets.BLANK, "")

    @Test fun `good for well is almost wherever the six units ask well, never wrong`() {
        val items = ids.flatMap { sheetItems(it) }.filter { it.accept.first().contains(Regex("""\bwell\b""")) }
        assertTrue("the units ask well typed", items.size >= 4)
        for (item in items) {
            val typed = item.accept.first().replace(Regex("""\bwell\b"""), "good")
            val m = Sheets.check(item, typed, Lang.EN)
            assertEquals("$typed in ${item.frame}", Triple(Verdict.ALMOST, Hint.NONSTANDARD, item.accept.first()), Triple(m.verdict, m.hint, m.expected))
        }
    }

    @Test fun `a double negative is almost wherever a negative stands before what the six units ask, never wrong`() {
        val no = mapOf("anything" to "nothing", "anyone" to "no one", "anybody" to "nobody", "anywhere" to "nowhere", "any" to "no")
        val negative = Regex("""\b(not|cannot|never)\b""")
        var seen = 0
        for (id in ids) for (item in sheetItems(id)) for ((word, said) in no) {
            val first = item.accept.first()
            val at = Regex("""\b$word\b""").find(first) ?: continue
            if (!negative.containsMatchIn(Grading.canonical(item.before() + " " + first.substring(0, at.range.first), Lang.EN))) continue
            val typed = first.replaceRange(at.range, said)
            val m = Sheets.check(item, typed, Lang.EN)
            assertEquals("$id: $typed for $first", Verdict.ALMOST to Hint.NONSTANDARD, m.verdict to m.hint)
            seen++
        }
        assertTrue("$seen double negatives", seen >= 15)
    }

    @Test fun `an adverb said flat is never wrong where the unit asks the adverb, typed in any item`() {
        val flat = mapOf(
            "quickly" to "quick", "slowly" to "slow", "loudly" to "loud", "quietly" to "quiet", "clearly" to "clear",
            "carefully" to "careful", "badly" to "bad", "beautifully" to "beautiful", "terribly" to "terrible",
        )
        var seen = 0
        for (item in sheetItems("adverbs")) for ((adverb, said) in flat) {
            val first = item.accept.first()
            if (!first.contains(Regex("""\b$adverb\b"""))) continue
            val typed = first.replace(Regex("""\b$adverb\b"""), said)
            val m = Sheets.check(item, typed, Lang.EN)
            assertEquals("$typed for $first", Verdict.CORRECT to first, m.verdict to m.standard)
            seen++
        }
        assertTrue("$seen flat adverbs", seen >= 8)
    }

    @Test fun `no choice and no word bank offers what people say as the wrong form`() {
        for (id in ids) {
            for (c in choices(id)) {
                val right = c.options[c.answer]
                c.options.forEachIndexed { i, o ->
                    if (i != c.answer && i !in c.almost) {
                        val (typed, whole) = if (Sheets.GAP.containsMatchIn(c.prompt)) c.prompt.replace(Sheets.GAP, o) to c.prompt.replace(Sheets.GAP, right) else o to right
                        assertTrue("$id: «$typed» offered as wrong beside «$whole»", Grading.check(typed, listOf(whole), Lang.EN).hint != Hint.NONSTANDARD)
                    }
                }
            }
            val u = course.units.getValue(id)
            for (e in (u.worksheets.flatMap { it.exercises } + u.test.exercises).filterIsInstance<Exercise.GapText>()) {
                for (d in e.distractors) for (item in Sheets.items(e, Lang.EN)) {
                    assertTrue("$id: the distractor «$d» in ${item.frame}", Grading.check(d, item.accept, Lang.EN, item.before()).hint != Hint.NONSTANDARD)
                    assertFalse("$id: the distractor «$d» is said in ${item.frame}", d in item.spoken)
                }
            }
        }
    }

    @Test fun `the intro and the page say people say it and writing wants the other, never that it is never said`() {
        val said = Regex("""\*[^*]*\b(good|nothing|careful|slow)\b[^*]*\*""")
        val never = Regex("""(?i)\b(never|nikoli|nie|mai)\s+\*[^*]*\b(good|nothing|careful|slow)\b""")
        val texts = ids.flatMap { id -> course.units.getValue(id).intro?.steps.orEmpty().flatMap { it.say.values } } +
            Grammar.bundled("en").filter { it.id in listOf("adverbs", "indefinite-pronouns") }.flatMap { p -> p.rule.values + p.card?.rule?.values.orEmpty() + p.card?.tip?.values.orEmpty() }
        for (t in texts) assertFalse(t, never.containsMatchIn(t))
        val adverbs = course.units.getValue("adverbs").intro?.steps.orEmpty().flatMap { it.say.values }
        val negatives = course.units.getValue("someone-anything").intro?.steps.orEmpty().flatMap { it.say.values }
        for ((lines, what, standard) in listOf(Triple(adverbs, "sings good", "**well**"), Triple(negatives, "I didn't see nothing", "**I didn't see anything.**"))) {
            val telling = lines.filter { said.containsMatchIn(it) && it.contains("*$what") }
            assertEquals("$what named in each base", bases.size, telling.size)
            assertTrue("$what beside its standard form", telling.all { it.contains(standard) })
        }
        for ((id, what) in listOf("adverbs" to "*sings good*", "indefinite-pronouns" to "*I didn't see nothing*")) {
            val page = Grammar.bundled("en").first { it.id == id }
            assertEquals("$id: named on the page in each base", bases.size, page.rule.values.count { it.contains(what) })
        }
    }

    @Test fun `a mistake the unit teaches is never listed as right or spoken`() {
        val taught = mapOf(
            "comparatives" to listOf("more bigger", "very bigger", "gooder", "badder"),
            "superlatives" to listOf("the most biggest", "the goodest"),
            "adverbs" to listOf("fastly", "goodly", "hardly"),
            "someone-anything" to listOf("I didn't see nothing.", "Everyone are at the inn."),
            "how-much" to listOf("a lot sheep", "How much sheep"),
        )
        for ((unit, mistakes) in taught) {
            for (item in sheetItems(unit)) for (m in mistakes) {
                assertFalse("$unit: $m in ${item.accept}", item.accept.any { it.equals(m, ignoreCase = true) } || item.spoken.any { it.equals(m, ignoreCase = true) })
            }
        }
    }
}
