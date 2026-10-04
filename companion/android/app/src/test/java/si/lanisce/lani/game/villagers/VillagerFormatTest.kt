package si.lanisce.lani.game.villagers

import kotlinx.serialization.builtins.ListSerializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.data.parsePack
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * The villager format's lines, roles and likes, and a word pack's words, in any language (plan 2, step 3b): the old
 * {"sl", "en"} and "Slovene · English" still read as they always did for Jan (sl · en), and the new ones name every
 * language, shown in the learner's pair.
 */
class VillagerFormatTest {
    private val pair = L10n.pair
    private val sonsPair = LangPair(Lang.IT, Lang.SL)

    @After fun back() {
        L10n.pair = pair
    }

    private fun villager(lines: String, role: String = "\"Pastir · Shepherd\"", likes: String = "[\"ovce · his sheep\"]") =
        parseVillagers("""[{"id": "x", "name": "X", "art": "shepherd", "role": $role, "likes": $likes, "lines": {"greet": $lines}}]""").single()

    @Test fun `an old line reads as it always did`() {
        val v = villager("""[{"sl": "Dober dan!", "en": "Good day!"}, {"sl": "Ej, Jan!", "en": "Hey, Jan!", "level": 1}]""")
        L10n.pair = LangPair.DEFAULT
        val (a, b) = v.lines.greet
        assertEquals("Dober dan!" to "Good day!", a.target to a.base)
        assertEquals(0 to 1, a.level to b.level)
        assertEquals("Pastir · Shepherd", v.role)
        assertEquals(listOf("ovce · his sheep"), v.likes)
        // a learner of Italian meets a Slovene line: it's said in Slovene, translated into English, never twice in Slovene
        assertEquals("Dober dan!" to "Good day!", a.target(sonsPair) to a.base(sonsPair))
        assertEquals(VillagerLine("Dober dan!", "Good day!"), a)
    }

    @Test fun `a line in another language is said in it and translated into the learner's base`() {
        val v = villager(
            """[{"it": "Ciao! Hai fame?", "sl": "Živjo! Si lačen?", "en": "Hi! Are you hungry?"}, {"it": "Tesoro mio!", "sl": "Moj zaklad!", "en": "My treasure!", "level": 4}]""",
            role = """{"it": "Nonna", "sl": "Babica", "en": "Grandmother"}""",
            likes = """[{"it": "la gubana", "sl": "gubana", "en": "gubana"}, "the garden"]""",
        )
        val hi = v.lines.greet.first()
        assertEquals("Ciao! Hai fame?" to "Živjo! Si lačen?", hi.target(sonsPair) to hi.base(sonsPair))
        assertEquals("Ciao! Hai fame?" to "Hi! Are you hungry?", hi.target(LangPair(Lang.IT, Lang.EN)) to hi.base(LangPair(Lang.IT, Lang.EN)))
        assertEquals(4, v.lines.greet[1].level)
        // role and likes read "target · base" in the pair they were read in
        L10n.pair = sonsPair
        val again = villager("""[{"it": "Ciao!", "sl": "Živjo!", "en": "Hi!"}]""", role = """{"it": "Nonna", "sl": "Babica", "en": "Grandmother"}""", likes = """[{"it": "la gubana", "sl": "gubana", "en": "gubana (a nut roll)"}]""")
        assertEquals("Nonna · Babica", again.role)
        assertEquals(listOf("la gubana · gubana"), again.likes)
        L10n.pair = LangPair(Lang.IT, Lang.EN)
        assertEquals("Nonna · Grandmother", villager("[]", role = """{"it": "Nonna", "sl": "Babica", "en": "Grandmother"}""").role)
    }

    @Test fun `lines keep their languages when the app caches the cast, and join language by language`() {
        val v = villager("""[{"it": "Ciao!", "sl": "Živjo!", "en": "Hi!", "level": 2}, {"sl": "Adijo!", "en": "Bye!"}]""")
        val back = json.decodeFromString(ListSerializer(Villager.serializer()), json.encodeToString(ListSerializer(Villager.serializer()), listOf(v))).single()
        assertEquals(v.lines, back.lines)
        val joined = VillagerLine.join(v.lines.greet[0], VillagerLine(mapOf("it" to "Ascolta.", "sl" to "Poslušaj.", "en" to "Listen.")))
        assertEquals("Ciao! Ascolta." to "Živjo! Poslušaj.", joined.target(sonsPair) to joined.base(sonsPair))
        assertEquals(2, joined.level)
    }

    @Test fun `a line may say when it is said, and keeps it`() {
        val v = villager("""[{"sl": "Lahko noč!", "en": "Good night!", "when": ["night", "evening"]}, {"sl": "Adijo!", "en": "Bye!", "level": 1}]""")
        val (night, any) = v.lines.greet
        assertEquals(setOf(TimeOfDay.EVENING, TimeOfDay.NIGHT), night.times)
        assertTrue(night.fits(TimeOfDay.EVENING) && night.fits(TimeOfDay.NIGHT) && !night.fits(TimeOfDay.MORNING) && !night.fits(TimeOfDay.AFTERNOON))
        // an old line fits any time, dawn too (the morning's)
        assertTrue(TimeOfDay.entries.all { any.fits(it) } && any.times.isEmpty())
        assertEquals(listOf(night, any), v.lines.greet.at(TimeOfDay.NIGHT))
        assertEquals(listOf(any), v.lines.greet.at(TimeOfDay.MORNING))
        // the time isn't a language: said and meant as before (the voice's text is the same)
        assertEquals("Lahko noč!" to "Good night!", night.target(LangPair.DEFAULT) to night.base(LangPair.DEFAULT))
        assertEquals(mapOf("sl" to "Lahko noč!", "en" to "Good night!"), night.by)
        // kept when the app caches the cast, written in the day's order
        val back = json.decodeFromString(ListSerializer(Villager.serializer()), json.encodeToString(ListSerializer(Villager.serializer()), listOf(v))).single()
        assertEquals(v.lines, back.lines)
        assertTrue(json.encodeToString(VillagerLine.serializer(), night).replace(" ", "").contains("\"when\":[\"evening\",\"night\"]"))
        // a memory put in, a tally joined to a goodbye: the times stay, and a joined line fits when both do
        assertEquals(night.times, night.map { _, s -> s.uppercase() }.times)
        assertEquals(night.times, VillagerLine.join(VillagerLine("4 od 5 pravilno.", "4 of 5 right."), night).times)
        assertEquals(setOf(TimeOfDay.NIGHT), VillagerLine.join(night, VillagerLine("Zzz.", "Zzz.", times = setOf(TimeOfDay.NIGHT, TimeOfDay.MORNING))).times)
        assertTrue(night != VillagerLine("Lahko noč!", "Good night!") && "when=[evening, night]" in night.toString())
        assertEquals("VillagerLine(sl=Adijo!, en=Bye!, level=1)", any.toString())
    }

    @Test fun `when names the parts of the day, and nothing else`() {
        for (bad in listOf("""["dawn"]""", """["noon"]""", """[]""", """"evening"""", """[1]""")) {
            try {
                villager("""[{"sl": "Živjo", "en": "Hi", "when": $bad}]""")
                fail(bad)
            } catch (e: Exception) {
                assertTrue(e.message.orEmpty(), "villager line's \"when\"" in e.message.orEmpty())
            }
        }
    }

    @Test fun `a line has language codes and a level, nothing else`() {
        for (bad in listOf("""[{"en": 3}]""", """[{"sl": "Živjo", "note": "x"}]""", """[{}]""")) {
            try {
                villager(bad)
                fail(bad)
            } catch (e: Exception) {
                assertTrue(e.message.orEmpty(), "villager line" in e.message.orEmpty())
            }
        }
    }

    @Test fun `a word pack in Italian teaches Italian and explains in the learner's base`() {
        val pack = parsePack(
            """{"id": "famiglia", "language": "it", "title": {"it": "La famiglia", "sl": "Družina", "en": "Family"}, "description": {"sl": "Kdo je kdo.", "en": "Who is who."},
               "words": [{"id": "mamma", "it": "la mamma", "sl": "mama", "en": "mum", "gender": "f", "plural": "le mamme",
               "example_it": "La mamma è in cucina.", "example_sl": "Mama je v kuhinji.", "example_en": "Mum is in the kitchen.", "note": "Feminine.", "note_sl": "Ženski spol."}]}""",
        )
        L10n.pair = sonsPair
        val w = pack.words.single()
        assertEquals("it", w.lang)
        assertEquals("la mamma" to "mama", w.word to w.meaning)
        assertEquals("La mamma è in cucina." to "Mama je v kuhinji.", w.example to w.exampleMeaning)
        assertEquals("Ženski spol.", w.noteText)
        L10n.pair = LangPair(Lang.IT, Lang.EN)
        assertEquals("mum" to "Feminine.", w.meaning to w.noteText)
    }

    @Test fun `a line and a word pack in German read in the learner's pair, from Slovene, English or Italian`() {
        val v = villager(
            """[{"de": "Servus! Hast du Hunger?", "sl": "Živjo! Si lačen?", "en": "Hi! Are you hungry?", "it": "Ciao! Hai fame?"}]""",
            role = """{"de": "Oma", "sl": "Babica", "en": "Grandmother", "it": "Nonna"}""",
        )
        val hi = v.lines.greet.single()
        for ((base, meant) in listOf(Lang.SL to "Živjo! Si lačen?", Lang.EN to "Hi! Are you hungry?", Lang.IT to "Ciao! Hai fame?")) {
            assertEquals("Servus! Hast du Hunger?" to meant, hi.target(LangPair(Lang.DE, base)) to hi.base(LangPair(Lang.DE, base)))
        }
        val pack = parsePack(
            """{"id": "familie", "language": "de", "title": {"de": "Die Familie", "sl": "Družina", "en": "Family", "it": "La famiglia"},
               "words": [{"id": "mama", "de": "die Mama", "sl": "mama", "en": "mum", "it": "la mamma", "gender": "f", "plural": "die Mamas",
               "example_de": "Die Mama ist in der Küche.", "example_sl": "Mama je v kuhinji.", "example_en": "Mum is in the kitchen.", "example_it": "La mamma è in cucina.",
               "note": "Feminine: die.", "note_sl": "Ženski spol: die.", "note_it": "Femminile: die."}]}""",
        )
        val w = pack.words.single()
        assertEquals("de", w.lang)
        L10n.pair = LangPair(Lang.DE, Lang.SL)
        assertEquals(listOf("die Mama", "mama", "Die Mama ist in der Küche.", "Mama je v kuhinji.", "Ženski spol: die."), listOf(w.word, w.meaning, w.example, w.exampleMeaning, w.noteText))
        L10n.pair = LangPair(Lang.DE, Lang.IT)
        assertEquals(listOf("la mamma", "La mamma è in cucina.", "Femminile: die."), listOf(w.meaning, w.exampleMeaning, w.noteText))
        L10n.pair = LangPair(Lang.DE, Lang.EN)
        assertEquals(listOf("mum", "Mum is in the kitchen.", "Feminine: die."), listOf(w.meaning, w.exampleMeaning, w.noteText))
    }

    @Test fun `an English village's line is said in English and meant in the learner's base, never in English twice`() {
        val grandma = """{"en": "Grandmother", "sl": "Babica", "it": "Nonna", "de": "Oma"}"""
        val v = villager("""[{"en": "Now then! Are you hungry?", "sl": "Živjo! Si lačen?", "it": "Ciao! Hai fame?", "de": "Hallo! Hast du Hunger?"}]""", role = grandma)
        val hi = v.lines.greet.single()
        val fromSl = LangPair(Lang.EN, Lang.SL)
        val fromDe = LangPair(Lang.EN, Lang.DE)
        assertEquals("Now then! Are you hungry?" to "Živjo! Si lačen?", hi.target(fromSl) to hi.base(fromSl))
        assertEquals("Now then! Are you hungry?" to "Hallo! Hast du Hunger?", hi.target(fromDe) to hi.base(fromDe))
        // English is every line's translation of last resort, but not of an English line: without the base, none
        val short = VillagerLine(mapOf("en" to "Aye.", "sl" to "Ja."))
        assertEquals("Aye." to "", short.target(fromDe) to short.base(fromDe))
        L10n.pair = fromDe
        assertEquals("Grandmother · Oma", villager("[]", role = grandma).role)
        assertEquals("Grandmother", villager("[]", role = """{"en": "Grandmother", "sl": "Babica"}""").role)
    }

    @Test fun `a word pack in English teaches English and explains in the learner's base, never by the word itself`() {
        // (its title is read in the pair of the moment)
        L10n.pair = LangPair(Lang.EN, Lang.DE)
        val pack = parsePack(
            """{"id": "family", "language": "en", "title": {"en": "Family", "sl": "Družina", "it": "La famiglia", "de": "Die Familie"},
               "words": [{"id": "child", "en": "child", "sl": "otrok", "it": "il bambino", "de": "das Kind", "plural": "children",
               "example_en": "The child is in the garden.", "example_sl": "Otrok je na vrtu.", "example_it": "Il bambino è in giardino.", "example_de": "Das Kind ist im Garten.",
               "note": "One child, two children.", "note_sl": "Množina: children.", "note_de": "Mehrzahl: children."},
               {"id": "mum", "en": "Mum", "sl": "mama", "example_en": "Mum is here.", "example_sl": "Mama je tu."}]}""",
        )
        val (child, mum) = pack.words
        assertEquals("en", child.lang)
        assertEquals("Family · Die Familie", pack.title)
        assertEquals(listOf("child", "das Kind", "The child is in the garden.", "Das Kind ist im Garten.", "Mehrzahl: children."), listOf(child.word, child.meaning, child.example, child.exampleMeaning, child.noteText))
        L10n.pair = LangPair(Lang.EN, Lang.IT)
        assertEquals("il bambino" to "One child, two children.", child.meaning to child.noteText)
        // a word the pack gives no German: another of its translations, never the English word as its own meaning
        L10n.pair = LangPair(Lang.EN, Lang.DE)
        assertEquals(listOf("Mum", "mama", "Mum is here.", "Mama je tu."), listOf(mum.word, mum.meaning, mum.example, mum.exampleMeaning))
    }

    @Test fun `a Slovene pack reads as it always did`() {
        val pack = parsePack("""{"id": "druzina", "title": "Družina · Family", "words": [{"id": "mama", "sl": "mama", "en": "mother", "example_sl": "To je mama.", "example_en": "This is Mum.", "note": "Feminine."}]}""")
        L10n.pair = LangPair.DEFAULT
        val w = pack.words.single()
        assertEquals("sl", pack.language)
        assertEquals("Družina · Family", pack.title)
        assertEquals(listOf("mama", "mother", "To je mama.", "This is Mum.", "Feminine."), listOf(w.word, w.meaning, w.example, w.exampleMeaning, w.noteText))
    }
}
