package si.lanisce.lani.road

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.ClipIndex
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.json
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply

class RoadTest {
    /** The voice store's index: normalized text → voice → URL (only what is here exists). */
    private val index: ClipIndex = mapOf(
        "dober dan" to mapOf("female" to "/voice/file/dober.mp3"),
        "hvala" to mapOf("female" to "/voice/file/hvala.mp3"),
        "kruh" to mapOf("female" to "/voice/file/kruh.mp3"),
        "kupim kruh" to mapOf("female" to "/voice/file/kupim.mp3"),
        "mleko" to mapOf("female" to "/voice/file/mleko.mp3"),
        "o jan pridi" to mapOf("grandma" to "/voice/file/pridi-gm.mp3", "female" to "/voice/file/pridi-f.mp3"),
        "seveda vam pomagam" to mapOf("female" to "/voice/file/seveda.mp3"),
        "vzemi moko" to mapOf("female" to "/voice/file/moko-f.mp3"),
        "potica je dobra" to mapOf("grandma" to "/voice/file/potica.mp3"),
        "hvala babica" to mapOf("female" to "/voice/file/hvala-b.mp3"),
        "nekoč je živel kralj" to mapOf("grandpa" to "/voice/file/kralj.mp3"),
        "imel je tri sinove" to mapOf("grandpa" to "/voice/file/sinovi.mp3"),
        "dober večer jan sedi k ognju" to mapOf("grandpa" to "/voice/file/vecer.mp3"),
        "dober večer" to mapOf("female" to "/voice/file/dober-vecer.mp3"),
        "kralj je bil star" to mapOf("grandpa" to "/voice/file/star.mp3"),
        "najmlajši sin je šel v svet" to mapOf("grandpa" to "/voice/file/svet.mp3"),
    )
    private val clips = ClipLookup.of(index)
    private val youSay: (String) -> String = { "You say: $it" }
    private val grandma = listOf("grandma", "female")

    private fun clipTexts(item: RoadItem) = item.sounds.filterIsInstance<Sound.Clip>().map { it.text }
    private fun prompts(item: RoadItem) = item.sounds.filterIsInstance<Sound.Prompt>().map { it.text }

    @Test fun `a card is its meaning, a pause to say it, the Slovene, a pause to repeat it, the Slovene again`() {
        val item = RoadPlay.card(ReviewCard("c1", "dober dan", "good day (polite hello)"), "2026-09-29", clips)!!
        val s = item.sounds
        assertEquals(Sound.Prompt("good day"), s[0])
        // two words: 1.5 s each and 1 s more to say it, a fifth less to repeat it
        assertEquals(Sound.Pause(4_000, Gap.SAY), s[1])
        assertEquals(Sound.Clip("dober dan", listOf("dober.mp3")), s[2])
        assertEquals(Sound.Pause(3_200, Gap.REPEAT), s[3])
        assertEquals(s[2], s[4])
        assertEquals(Rate("c1"), item.rate)
        assertEquals("card:c1", item.id)
        assertEquals("2026-09-29", item.due)
    }

    @Test fun `the pause to say it follows the answer, 1_5 s a word and 1 s more, 2_5 s to 8 s, repeating a fifth shorter`() {
        assertEquals(1, RoadPlay.words("Hvala."))
        assertEquals(3, RoadPlay.words("Seveda vam pomagam!"))
        assertEquals(2, RoadPlay.words("Nekoč … kralj — "))
        assertEquals(2_500, RoadPlay.thinkMs("Hvala."))
        assertEquals(4_000, RoadPlay.thinkMs("dober dan"))
        assertEquals(5_500, RoadPlay.thinkMs("Seveda vam pomagam!"))
        assertEquals(8_000, RoadPlay.thinkMs("Ker je hotel zlato za dekle."))
        assertEquals(2_500, RoadPlay.thinkMs(""))
        assertEquals(2_000, RoadPlay.repeatMs("Hvala."))
        assertEquals(4_400, RoadPlay.repeatMs("Seveda vam pomagam!"))
        assertEquals(6_400, RoadPlay.repeatMs("Ker je hotel zlato za dekle."))
    }

    @Test fun `only what has a clip, no card, word or line the node would have to voice now`() {
        assertNull(RoadPlay.card(ReviewCard("c2", "nasvidenje", "goodbye"), null, clips))
        // a rule isn't a phrase to say aloud
        assertNull(RoadPlay.card(ReviewCard("c3", "dober → dobra", "good (m → f)"), null, ClipLookup { _, _ -> listOf("/voice/file/x.mp3") }))
        assertNull(RoadPlay.word("hrana", PackWord("w9", sl = "sir", en = "cheese"), "cheese", null, clips))
    }

    @Test fun `a word brings its example when the example has a clip`() {
        val w = PackWord("w1", sl = "kruh", en = "bread", exampleSl = "Kupim kruh.", exampleEn = "I buy bread.")
        val item = RoadPlay.word("hrana", w, "bread", w.exampleSl!! to w.exampleEn!!, clips)!!
        assertEquals(listOf("bread", "I buy bread."), prompts(item))
        assertEquals(listOf("kruh", "kruh", "Kupim kruh."), clipTexts(item))
        assertEquals(Rate("w1", "hrana"), item.rate)
        val noExample = RoadPlay.word("hrana", w, "bread", "Kruh je svež." to "The bread is fresh.", clips)!!
        assertEquals(listOf("bread"), prompts(noExample))
    }

    @Test fun `a word is heard again, shorter`() {
        val w = RoadPlay.word("hrana", PackWord("w2", sl = "mleko", en = "milk"), "milk", null, clips)!!
        val again = RoadPlay.again(w)!!
        assertEquals("word:hrana/w2#again", again.id)
        assertEquals(listOf("milk"), prompts(again))
        assertEquals(listOf("mleko"), clipTexts(again))
        assertEquals(w.rate, again.rate)
    }

    private val potica = Dialog(
        "potica",
        listOf(
            DialogLine(who = "babica", sl = "O, Jan! Pridi.", en = "Oh, Jan! Come in."),
            DialogLine(
                choices = listOf(
                    DialogChoice("Seveda vas pomagam!", "Of course I'll help you!"),
                    DialogChoice("Seveda vam pomagam!", "Of course I'll help you!", ok = true, reply = DialogReply("Vzemi moko.", "Take the flour.")),
                ),
            ),
            DialogLine(who = "babica", sl = "Potica je dobra.", en = "The potica is good."),
            DialogLine(choices = listOf(DialogChoice("Hvala, babica.", "Thank you, grandma.", ok = true))),
        ),
    )

    private fun voicesOf(who: String?) = if (who == "babica") grandma else RoadPlay.NARRATOR

    @Test fun `a dialog, the other's lines in their voice, before each turn what to say, a pause, the right answer, the reply`() {
        val item = RoadPlay.dialog("kuhinja", "Kuhinja · Kitchen", potica, ::voicesOf, youSay, clips)!!
        assertEquals("dialog:kuhinja/potica", item.id)
        val clips = item.sounds.filterIsInstance<Sound.Clip>()
        // her line in her own voice, the answer said right (never the wrong choice), her reply in the voice the store has
        assertEquals(listOf("pridi-gm.mp3", "seveda.mp3", "moko-f.mp3", "potica.mp3", "hvala-b.mp3"), clips.flatMap { it.files })
        assertEquals(listOf("You say: Of course I'll help you!", "You say: Thank you, grandma."), prompts(item))
        // the prompt comes before the pause and the answer
        val i = item.sounds.indexOf(Sound.Prompt("You say: Of course I'll help you!", cue = true))
        assertEquals(Sound.Pause(5_500, Gap.SAY), item.sounds[i + 1])
        assertEquals("Seveda vam pomagam!", (item.sounds[i + 2] as Sound.Clip).text)
        assertEquals(Sound.Pause(4_400, Gap.REPEAT), item.sounds[i + 3])
        assertNull(item.rate)
    }

    @Test fun `a dialog missing more than a quarter of its lines is left out, one missing line is skipped`() {
        val short = ClipLookup { t, v -> if (t.startsWith("Potica")) null else clips.urls(t, v) }
        val item = RoadPlay.dialog("kuhinja", "", potica, ::voicesOf, youSay, short)!!
        assertFalse(clipTexts(item).contains("Potica je dobra."))
        val poor = ClipLookup { t, v -> if (t.startsWith("Potica") || t.startsWith("O, Jan")) null else clips.urls(t, v) }
        assertNull(RoadPlay.dialog("kuhinja", "", potica, ::voicesOf, youSay, poor))
        val noTurns = ClipLookup { t, v -> if (t.startsWith("Seveda") || t.startsWith("Hvala")) null else clips.urls(t, v) }
        assertNull(RoadPlay.dialog("kuhinja", "", potica, ::voicesOf, youSay, noTurns))
    }

    /** An evening: a greeting (asides only), a turn, two paragraphs of the story with a turn between them. */
    private val kralj = listOf(
        DialogLine(sl = "Dober večer, Jan. Sedi k ognju.", en = "Good evening, Jan. Sit by the fire."),
        DialogLine(choices = listOf(DialogChoice("Dober večer!", "Good evening!", ok = true))),
        DialogLine(sl = "Nekoč je živel kralj.", en = "Once there lived a king."),
        DialogLine(sl = "Imel je tri sinove.", en = "He had three sons."),
        DialogLine(choices = listOf(DialogChoice("Hvala.", "Thanks.", ok = true))),
        DialogLine(sl = "Kralj je bil star.", en = "The king was old."),
        DialogLine(sl = "Najmlajši sin je šel v svet.", en = "The youngest son went out into the world."),
    )

    private fun kralj(level: String, teaser: String? = "Janez tells the story of the king") =
        RoadPlay.story("kralj", 0, "Kralj · The king", kralj, listOf("grandpa", "male", "female"), youSay, clips, level = level, teaser = teaser)!!

    @Test fun `a story at A1, a setup, each paragraph in Slovene only and a recap after it, the turns as in a dialog`() {
        val item = kralj("A1")
        assertEquals("story:kralj/0", item.id)
        assertEquals(
            listOf<Sound>(
                Sound.Prompt("Janez tells the story of the king"),
                Sound.Clip("Dober večer, Jan. Sedi k ognju.", listOf("vecer.mp3")),
                // the greeting is asides only: nothing to recap
                Sound.Prompt("You say: Good evening!", cue = true), Sound.Clip("Dober večer!", listOf("dober-vecer.mp3")),
                Sound.Clip("Nekoč je živel kralj.", listOf("kralj.mp3")), Sound.Clip("Imel je tri sinove.", listOf("sinovi.mp3")),
                Sound.Prompt("Once there lived a king. He had three sons."),
                Sound.Prompt("You say: Thanks.", cue = true), Sound.Clip("Hvala.", listOf("hvala.mp3")),
                Sound.Clip("Kralj je bil star.", listOf("star.mp3")), Sound.Clip("Najmlajši sin je šel v svet.", listOf("svet.mp3")),
                Sound.Prompt("The king was old. The youngest son went out into the world."),
            ),
            item.sounds.filter { it !is Sound.Pause },
        )
        // the turn's pauses are there as in a dialog
        assertTrue(Sound.Pause(2_500, Gap.SAY) in item.sounds)
    }

    @Test fun `a story at A2 recaps every second paragraph, from B1 only at the end, without a teaser its first paragraph sets it up`() {
        val a2 = prompts(kralj("A2")).filter { !it.startsWith("You say") }
        assertEquals(listOf("Janez tells the story of the king", "Once there lived a king. He had three sons. The king was old. The youngest son went out into the world."), a2)
        // at A1 without a teaser: the first paragraph's meaning before the telling, not again after it
        val a1 = prompts(kralj("A1", teaser = null)).filter { !it.startsWith("You say") }
        assertEquals(listOf("Once there lived a king. He had three sons.", "The king was old. The youngest son went out into the world."), a1)
    }

    @Test fun `when the recaps come, and how long they are`() {
        val has = listOf(false, true, true, true, true, true)
        assertEquals(mapOf(1 to listOf(1), 2 to listOf(2), 3 to listOf(3), 4 to listOf(4), 5 to listOf(5)), RoadStory.schedule(has, "A1"))
        assertEquals(mapOf(2 to listOf(1, 2), 4 to listOf(3, 4), 5 to listOf(5)), RoadStory.schedule(has, "A2"))
        assertEquals(mapOf(5 to listOf(1, 2, 3, 4, 5)), RoadStory.schedule(has, "B1"))
        assertEquals(mapOf(5 to listOf(1, 2, 3, 4, 5)), RoadStory.schedule(has, "B2"))
        assertTrue(RoadStory.schedule(listOf(false), "A1").isEmpty())
        // the first sentence of each note first, then what still fits, in their order
        val said = listOf(
            RoadStory.Said("Where the blood fell, a red flower grew.", true),
            RoadStory.Said("Zlatorog ate it and was well again.", false),
            RoadStory.Said("He was very angry.", false),
            RoadStory.Said("The hunter could not see anything and fell into the abyss.", true),
        )
        assertEquals(
            "Where the blood fell, a red flower grew. Zlatorog ate it and was well again. The hunter could not see anything and fell into the abyss.",
            RoadStory.shorten(said, RoadStory.RECAP_CHARS),
        )
        assertEquals("Where the blood fell, a red flower grew.", RoadStory.shorten(said, 50))
    }

    @Test fun `a story's notes are its recaps, each note after the paragraph it tells of`() {
        val notes = listOf(
            DialogReply("Nekoč je živel star kralj.", "Once there lived an old king."),
            DialogReply("Imel je tri sinove.", "He had three sons."),
            DialogReply("Najmlajši sin je šel v svet.", "The youngest son went out into the world."),
        )
        val item = RoadPlay.story("kralj", 0, "", kralj, listOf("grandpa"), youSay, clips, level = "A1", teaser = "Janez tells a story", notes = notes)!!
        assertEquals(
            listOf("Janez tells a story", "Once there lived an old king. He had three sons.", "The youngest son went out into the world."),
            prompts(item).filter { !it.startsWith("You say") },
        )
        assertEquals(intArrayOf(0, 0, 1).toList(), RoadStory.align(listOf("Nekoč je živel kralj. Imel je tri sinove.", "Kralj je bil star. Najmlajši sin je šel v svet."), notes.map { it.sl }).toList())
    }

    @Test fun `next during the pause to say it plays the answer at once, at any other moment the next item`() {
        val card = RoadPlay.card(ReviewCard("c1", "dober dan", "good day"), null, clips)!!
        // meaning, pause to say it, the Slovene, pause to repeat it, the Slovene, a gap: a piece each
        assertEquals(listOf(0, 1, 2, 3, 4, 5), RoadPlay.pieces(card.sounds))
        assertEquals(RoadControls.Next.Reveal(2), RoadControls.next(card, 1))
        assertEquals(RoadControls.Next.Skip, RoadControls.next(card, 0))
        assertEquals(RoadControls.Next.Skip, RoadControls.next(card, 2))
        assertEquals(RoadControls.Next.Skip, RoadControls.next(card, 3))
        assertEquals(RoadControls.Next.Skip, RoadControls.next(card, 99))
        assertEquals(RoadControls.Next.Skip, RoadControls.next(null, 1))
        // a clip of two files is two pieces: the answer after it is further on
        val two = RoadItem("card:x", Kind.CARD, "", "", listOf(Sound.Prompt("a"), Sound.Clip("b", listOf("b1.mp3", "b2.mp3")), Sound.Pause(2_500, Gap.SAY), Sound.Clip("c", listOf("c.mp3"))), Rate("x"))
        assertEquals(listOf(0, 1, 1, 2, 3), RoadPlay.pieces(two.sounds))
        assertEquals(RoadControls.Next.Reveal(4), RoadControls.next(two, 3))
        // a dialog: during the learner's turn's pause, the right answer; during the repeat pause, the next item
        val dialog = RoadPlay.dialog("kuhinja", "", potica, ::voicesOf, youSay, clips)!!
        val say = dialog.sounds.indexOfFirst { it is Sound.Pause && it.gap == Gap.SAY }
        assertEquals(RoadControls.Next.Reveal(say + 1), RoadControls.next(dialog, say))
        assertEquals("Seveda vam pomagam!", (dialog.sounds[say + 1] as Sound.Clip).text)
        assertEquals(RoadControls.Next.Skip, RoadControls.next(dialog, say + 2))
    }

    @Test fun `a held button grades a card or a word, and nothing else`() {
        val card = RoadPlay.card(ReviewCard("c1", "dober dan", "good day"), null, clips)!!
        val word = RoadPlay.word("hrana", PackWord("w2", sl = "mleko", en = "milk"), "milk", null, clips)!!
        assertEquals(true, RoadControls.hold(card, forward = true))
        assertEquals(false, RoadControls.hold(card, forward = false))
        assertEquals(true, RoadControls.hold(RoadPlay.again(word), forward = true))
        assertNull(RoadControls.hold(RoadPlay.dialog("kuhinja", "", potica, ::voicesOf, youSay, clips), forward = true))
        assertNull(RoadControls.hold(null, forward = false))
    }

    @Test fun `listening only, a dialog plays straight, the lines and the right answers in turn, no prompt or pause to say it`() {
        val dialog = RoadPlay.dialog("kuhinja", "", potica, ::voicesOf, youSay, clips)!!
        val easy = RoadPlay.easy(dialog)
        assertEquals("dialog:kuhinja/potica#easy", easy.id)
        assertEquals(Kind.DIALOG, easy.kind)
        assertTrue(prompts(easy).isEmpty())
        assertTrue(easy.sounds.none { it is Sound.Pause && it.gap != null })
        // the same clips as the drill, in the same order, the answer a breath before the reply
        assertEquals(clipTexts(dialog), clipTexts(easy))
        val answer = easy.sounds.indexOfFirst { it is Sound.Clip && it.text == "Seveda vam pomagam!" }
        assertEquals(Sound.Pause(RoadPlay.BREATH_MS), easy.sounds[answer + 1])
        assertTrue(easy.seconds < dialog.seconds)
        // a story keeps its setup and recaps, only the turns play straight
        val story = RoadPlay.easy(kralj("A1"))
        assertEquals(listOf("Janez tells the story of the king", "Once there lived a king. He had three sons.", "The king was old. The youngest son went out into the world."), prompts(story))
        assertEquals("story:kralj/0#easy", story.id)
    }

    @Test fun `Mirno, only stories and dialogs, an evening of a story then about ten minutes of dialogs`() {
        val dialogs = (1..12).map { RoadItem("dialog:s/$it", Kind.DIALOG, "d$it", "", listOf(Sound.Pause(120_000))) }
        val l = lib().let { it.copy(items = it.items.filter { i -> i.kind != Kind.DIALOG } + dialogs) }
        val easy = RoadMix.easy(l, emptyMap())
        assertTrue(easy.all { it.id.endsWith("#easy") && (it.kind == Kind.DIALOG || it.kind == Kind.STORY) })
        assertEquals(
            listOf("story:a/0") + (1..5).map { "dialog:s/$it" } + "story:a/1" + (6..10).map { "dialog:s/$it" } + "story:b/0" + (11..12).map { "dialog:s/$it" },
            easy.map { it.id.substringBefore('#') },
        )
        // what was heard lately comes last
        assertEquals("story:b/0", RoadMix.easy(l, mapOf("story:a/0" to 5L)).first().id.substringBefore('#'))
    }

    @Test fun `shadowing, a dialog's phrases of two to eight words that have clips, each the phrase, a pause sized to it, the phrase again`() {
        val lines = potica.lines + DialogLine(who = "babica", sl = "Kruh.", en = "Bread.") +
            DialogLine(who = "babica", sl = "Nasvidenje, Jan.", en = "Goodbye, Jan.")
        val phrases = RoadPlay.phrases(lines, ::voicesOf, clips)
        // her lines in her voice, the right answer and her reply; one word ("Kruh.") too short, no clip for the last
        assertEquals(listOf("O, Jan! Pridi.", "Seveda vam pomagam!", "Vzemi moko.", "Potica je dobra.", "Hvala, babica."), phrases.map { it.text })
        assertEquals(listOf("pridi-gm.mp3"), phrases.first().files)
        val item = RoadPlay.phrase(phrases[1], "Kuhinja · Kitchen")
        assertEquals("phrase:seveda vam pomagam", item.id)
        assertEquals(Kind.PHRASE, item.kind)
        assertEquals(listOf(phrases[1], Sound.Pause(5_500, Gap.REPEAT), phrases[1], Sound.Pause(RoadPlay.GAP_MS)), item.sounds)
        // no English, nothing graded, next skips on
        assertTrue(prompts(item).isEmpty())
        assertNull(item.rate)
        assertEquals(RoadControls.Next.Skip, RoadControls.next(item, 1))
        val long = DialogLine(who = "babica", sl = "Ena dva tri štiri pet šest sedem osem devet.", en = "")
        assertTrue(RoadPlay.phrases(listOf(long), { listOf("female") }, ClipLookup { _, _ -> listOf("/voice/file/x.mp3") }).isEmpty())
    }

    private fun lib(): RoadLibrary {
        val cards = (1..12).map { RoadItem("card:$it", Kind.CARD, "c$it", "m$it", listOf(Sound.Prompt("m$it"), Sound.Pause(20_000)), Rate("$it"), due = if (it <= 10) "2026-09-29" else "2026-10-05") }
        val words = (1..30).map { RoadItem("word:p/$it", Kind.WORD, "w$it", "wm$it", listOf(Sound.Prompt("wm$it"), Sound.Pause(4_000), Sound.Clip("w$it", listOf("w$it.mp3")), Sound.Pause(15_000)), Rate("$it", "p")) }
        val dialogs = (1..6).map { RoadItem("dialog:s/$it", Kind.DIALOG, "d$it", "", listOf(Sound.Pause(60_000))) }
        val stories = listOf("a/0", "a/1", "b/0").map { RoadItem("story:$it", Kind.STORY, it, "", listOf(Sound.Pause(180_000))) }
        return RoadLibrary(1, "en", cards + words + dialogs + stories)
    }

    @Test fun `reviews are the cards due today, least recently heard first`() {
        val l = lib()
        assertEquals((1..10).map { "card:$it" }, RoadMix.reviews(l, emptyMap(), "2026-09-29").map { it.id })
        val heard = mapOf("card:1" to 100L, "card:2" to 50L)
        assertEquals(listOf("card:3", "card:4"), RoadMix.reviews(l, heard, "2026-09-29").take(2).map { it.id })
        assertEquals(listOf("card:2", "card:1"), RoadMix.reviews(l, heard, "2026-09-29").takeLast(2).map { it.id })
        assertEquals(12, RoadMix.reviews(l, emptyMap(), "2026-10-05").size)
    }

    @Test fun `a road block interleaves cards and words, a dialog after every four, a story about every ten minutes`() {
        val block = RoadMix.block(lib(), emptyMap(), "2026-09-29")
        val kinds = block.map { it.kind }
        // cards and words take turns, a dialog after two of each
        assertEquals(listOf(Kind.CARD, Kind.WORD, Kind.CARD, Kind.WORD, Kind.DIALOG), kinds.take(5))
        // about 30 minutes, not much over
        val minutes = block.sumOf { it.seconds } / 60
        assertTrue("$minutes", minutes in 30.0..34.0)
        assertTrue(Kind.STORY in kinds)
        // a new word comes again three items later
        val w = block.indexOfFirst { it.id == "word:p/1" }
        assertEquals("word:p/1#again", block[w + 4].id)
        // nothing twice
        assertEquals(block.size, block.map { it.id }.toSet().size)
        // only today's due cards
        assertTrue(block.none { it.id == "card:11" })
    }

    @Test fun `the next block brings new items, and when all were heard it goes round again`() {
        val big = lib().let { l -> l.copy(items = l.items + (31..90).map { RoadItem("word:p/$it", Kind.WORD, "w$it", "wm$it", listOf(Sound.Prompt("wm$it"), Sound.Clip("w$it", listOf("w$it.mp3")), Sound.Pause(15_000)), Rate("$it", "p")) }) }
        val first = RoadMix.block(big, emptyMap(), "2026-09-29")
        val firstIds = first.map { it.id.substringBefore('#') }.toSet()
        val next = RoadMix.block(big, emptyMap(), "2026-09-29", exclude = firstIds)
        assertTrue(next.isNotEmpty())
        assertTrue(next.none { it.id.substringBefore('#') in firstIds })
        // everything queued already: the least recently heard again, not silence
        val all = big.items.map { it.id }.toSet()
        assertTrue(RoadMix.block(big, emptyMap(), "2026-09-29", exclude = all).isNotEmpty())
    }

    @Test fun `without cards the words and dialogs go on, the stories in the order of their evenings`() {
        val l = lib().let { it.copy(items = it.items.filter { i -> i.kind != Kind.CARD }) }
        val block = RoadMix.block(l, emptyMap(), "2026-09-29")
        assertEquals(listOf(Kind.WORD, Kind.WORD, Kind.DIALOG), block.take(3).map { it.kind })
        assertEquals(listOf("story:a/0", "story:a/1", "story:b/0"), RoadMix.stories(l, emptyMap()).map { it.id })
        // a story heard lately comes last
        assertEquals(listOf("story:b/0", "story:a/0", "story:a/1"), RoadMix.stories(l, mapOf("story:a/0" to 5L)).map { it.id })
    }

    @Test fun `ratings, a card answered as a spoken answer, each pack's words learned, the last rating kept`() {
        assertEquals(5, RoadRatings.KNEW)
        assertEquals(1, RoadRatings.DIDNT)
        var r = emptyList<Rating>()
        r = RoadRatings.add(r, Rating("c1", null, RoadRatings.DIDNT, 0))
        r = RoadRatings.add(r, Rating("w1", "hrana", RoadRatings.KNEW, 60_000))
        r = RoadRatings.add(r, Rating("c1", null, RoadRatings.KNEW, 150_000))
        assertEquals(2, r.size)
        var n = 0
        val writes = RoadRatings.writes(r, id = { "id${n++}" }, now = 1)
        assertEquals(listOf("/reviews", "/packs/hrana/learn"), writes.map { it.path })
        val review = json.parseToJsonElement(writes[0].body).jsonObject
        val results = review["results"]!!.jsonArray
        assertEquals("c1", results.single().jsonObject["item_id"]!!.jsonPrimitive.content)
        assertEquals(5, results.single().jsonObject["quality"]!!.jsonPrimitive.int)
        // from the first rating kept (60 s) to the last (150 s): a minute and a half, counted as 2
        assertEquals(2, review["duration_minutes"]!!.jsonPrimitive.int)
        val learn = json.parseToJsonElement(writes[1].body).jsonObject
        assertEquals("w1", learn["results"]!!.jsonArray.single().jsonObject["word_id"]!!.jsonPrimitive.content)
        assertTrue(RoadRatings.writes(emptyList()).isEmpty())
    }

    @Test fun `the library keeps its items, and a prompt's file is the same for the same text`() {
        val l = lib()
        val back = RoadPlay.json.decodeFromString(RoadLibrary.serializer(), RoadPlay.json.encodeToString(RoadLibrary.serializer(), l))
        assertEquals(l, back)
        assertEquals(RoadPlay.promptFile("en", "good day"), RoadPlay.promptFile("en", "good day"))
        assertFalse(RoadPlay.promptFile("en", "good day") == RoadPlay.promptFile("de", "good day"))
        assertNotNull(RoadMix.hours(l))
        assertEquals("abc.mp3", RoadPlay.fileOf("/voice/file/abc.mp3?count=0"))
    }
}
