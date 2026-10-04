package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res
import java.time.LocalDate

/**
 * The numbers of a counting dialog (companion/SCENES.md, "Numbers: counting what the village has"): Slovene's four
 * categories with the hundreds' rule and the others' two, the wrong forms, every number word in the four languages,
 * the number from the village, and a dialog rendered with it.
 */
class NumbersTest {
    @Test fun `Slovene's categories go by the last two figures - 1, 2, 3 and 4, 5 and more`() {
        val one = listOf(1, 101, 201, 301, 901)
        val two = listOf(2, 102, 202, 902)
        val few = listOf(3, 4, 103, 104, 303, 904)
        val many = listOf(0, 5, 6, 10, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24, 25, 99, 100, 105, 111, 112, 113, 114, 121, 122, 200, 999)
        for (n in one) assertEquals("$n", Numbers.ONE, Numbers.category(n, 4))
        for (n in two) assertEquals("$n", Numbers.TWO, Numbers.category(n, 4))
        for (n in few) assertEquals("$n", Numbers.FEW, Numbers.category(n, 4))
        for (n in many) assertEquals("$n", Numbers.MANY, Numbers.category(n, 4))
    }

    @Test fun `two forms are one and more - Italian, German, English`() {
        assertEquals(0, Numbers.category(1, 2))
        for (n in listOf(0, 2, 3, 5, 11, 21, 101)) assertEquals("$n", 1, Numbers.category(n, 2))
    }

    @Test fun `the wrong forms learners put - never the right one`() {
        // 1: the 3–4 form (ena ovce), then the dual; 2: 3–4 (dve ovce), then 5+; 3–4: 5+ (tri ovc), then the dual; 5+: 3–4 (pet ovce), then one
        assertEquals(listOf(Numbers.FEW, Numbers.TWO), (1..2).map { Numbers.wrong(1, 4, it) })
        assertEquals(listOf(Numbers.FEW, Numbers.MANY), (1..2).map { Numbers.wrong(102, 4, it) })
        assertEquals(listOf(Numbers.MANY, Numbers.TWO), (1..2).map { Numbers.wrong(4, 4, it) })
        assertEquals(listOf(Numbers.FEW, Numbers.ONE), (1..2).map { Numbers.wrong(20, 4, it) })
        for (n in 0..250) for (k in 1..2) assertTrue("$n/$k", Numbers.wrong(n, 4, k) != Numbers.category(n, 4))
        assertEquals(1, Numbers.wrong(1, 2, 1))
        assertEquals(0, Numbers.wrong(7, 2, 2))
    }

    @Test fun `Slovene number words agree with their noun - gender and case for 1 to 4`() {
        fun sl(n: Int, g: String, case: String = "nom", animate: Boolean = false) = Numbers.words(n, "sl", g, case, animate)
        assertEquals(listOf("en", "ena", "eno"), listOf("m", "f", "n").map { sl(1, it) })
        assertEquals(listOf("en", "enega", "eno", "eno"), listOf(sl(1, "m", "acc"), sl(1, "m", "acc", animate = true), sl(1, "f", "acc"), sl(1, "n", "acc")))
        assertEquals(listOf("dva", "dve", "dve"), listOf("m", "f", "n").map { sl(2, it) })
        assertEquals(listOf("trije", "tri", "tri", "tri"), listOf(sl(3, "m"), sl(3, "f"), sl(3, "n"), sl(3, "m", "acc")))
        assertEquals(listOf("štirje", "štiri", "štiri"), listOf(sl(4, "m"), sl(4, "f"), sl(4, "m", "acc")))
        val words = mapOf(
            5 to "pet", 9 to "devet", 10 to "deset", 11 to "enajst", 12 to "dvanajst", 14 to "štirinajst", 17 to "sedemnajst", 20 to "dvajset",
            21 to "enaindvajset", 22 to "dvaindvajset", 23 to "triindvajset", 24 to "štiriindvajset", 25 to "petindvajset", 30 to "trideset",
            40 to "štirideset", 99 to "devetindevetdeset", 100 to "sto", 105 to "sto pet", 111 to "sto enajst", 121 to "sto enaindvajset",
            200 to "dvesto", 345 to "tristo petinštirideset", 999 to "devetsto devetindevetdeset", 0 to "nič",
        )
        for ((n, w) in words) assertEquals("$n", w, sl(n, "f"))
        // the hundreds' rule: 101 agrees like 1, 102 like 2, 103 like 3
        assertEquals("sto ena", sl(101, "f"))
        assertEquals("sto dve", sl(102, "f"))
        assertEquals("sto dva", sl(102, "m"))
        assertEquals("sto trije", sl(103, "m"))
        assertEquals("dvesto štiri", sl(204, "n"))
        assertEquals("1000", sl(1000, "f"))
    }

    @Test fun `German, Italian and English number words`() {
        assertEquals(listOf("ein", "eine", "ein", "einen"), listOf(Numbers.words(1, "de", "n"), Numbers.words(1, "de", "f"), Numbers.words(1, "de", "m"), Numbers.words(1, "de", "m", "acc")))
        val de = mapOf(2 to "zwei", 7 to "sieben", 11 to "elf", 12 to "zwölf", 16 to "sechzehn", 17 to "siebzehn", 20 to "zwanzig", 21 to "einundzwanzig", 30 to "dreißig", 37 to "siebenunddreißig", 100 to "einhundert", 101 to "einhunderteins", 245 to "zweihundertfünfundvierzig")
        for ((n, w) in de) assertEquals("de $n", w, Numbers.words(n, "de", "n"))
        assertEquals(listOf("un", "uno", "uno", "una", "un'"), listOf(Numbers.words(1, "it", "m", next = "ciocco"), Numbers.words(1, "it", "m", next = "stivale"), Numbers.words(1, "it", "m", next = "zaino"), Numbers.words(1, "it", "f", next = "pecora"), Numbers.words(1, "it", "f", next = "oca")))
        val it = mapOf(2 to "due", 11 to "undici", 17 to "diciassette", 20 to "venti", 21 to "ventuno", 23 to "ventitré", 28 to "ventotto", 31 to "trentuno", 38 to "trentotto", 40 to "quaranta", 100 to "cento", 108 to "centotto", 180 to "centottanta", 233 to "duecentotrentatré")
        for ((n, w) in it) assertEquals("it $n", w, Numbers.words(n, "it", "f"))
        val en = mapOf(1 to "one", 2 to "two", 13 to "thirteen", 20 to "twenty", 21 to "twenty-one", 45 to "forty-five", 100 to "one hundred", 101 to "one hundred and one", 250 to "two hundred and fifty")
        for ((n, w) in en) assertEquals("en $n", w, Numbers.words(n, "en"))
        assertEquals("7", Numbers.words(7, "fr"))
    }

    // --- the number from the village ------------------------------------------------------------------------------

    private val village = GameState(
        seed = 42, villagers = 9, resources = mapOf(Res.WOOD to 73, Res.FOOD to 5),
        buildings = listOf(Building("h1", BuildingType.HOUSE, 0), Building("h2", BuildingType.HOUSE, 1), Building("b1", BuildingType.BEEHIVE, 2)),
    )
    private val day = LocalDate.of(2026, 9, 27)

    @Test fun `the number comes from the village - its stores per so many, its people, its buildings, else the day's dice`() {
        assertEquals(7, Counts.of(DialogCount("wood", min = 2, max = 20, per = 10), village, day, "k"))
        assertEquals(2, Counts.of(DialogCount("food", min = 2, max = 20, per = 10), village, day, "k")) // 0 logs is kept at the least
        assertEquals(9, Counts.of(DialogCount("villagers", min = 2, max = 30), village, day, "k"))
        assertEquals(2, Counts.of(DialogCount("building:house"), village, day, "k"))
        assertEquals(1, Counts.of(DialogCount("building:beehive"), village, day, "k"))
        val dice = (0 until 60).map { Counts.of(DialogCount("dice", min = 12, max = 24), village, day.plusDays(it.toLong()), "ob-potoku/ovce/ovce-stejem") }
        assertTrue("within min and max: $dice", dice.all { it in 12..24 })
        assertTrue("another number another day: $dice", dice.toSet().size >= 8)
        assertEquals("the same all day", dice[0], Counts.of(DialogCount("dice", min = 12, max = 24), village, day, "ob-potoku/ovce/ovce-stejem"))
    }

    // --- a dialog rendered --------------------------------------------------------------------------------------

    private val ovce = Dialog(
        "ovce-stejem",
        count = DialogCount("dice", 12, 24, gender = "f"),
        lines = listOf(
            DialogLine(who = "luka", sl = "{n} {ovca|ovci|ovce|ovc} {je|sta|so|je} pri vodi. Preštej jih!", en = "{n} {sheep|sheep} {is|are} at the water. Count them!"),
            DialogLine(choices = listOf(
                DialogChoice("Pri vodi {je|sta|so|je} {n} {!ovca|ovci|ovce|ovc}.", "There {is|are} {n} {sheep|sheep} at the water.", wrongForm = 1,
                    why = "{One takes the singular|Two take the dual|Three and four the plural|From five on, the genitive plural}: {n} {ovca|ovci|ovce|ovc}.",
                    reply = DialogReply("Hm? Štej še enkrat.", "Hm? Count again.")),
                DialogChoice("Pri vodi {je|sta|so|je} {n} {ovca|ovci|ovce|ovc}.", "There {is|are} {n} {sheep|sheep} at the water.", ok = true, reply = DialogReply("Tako je, {n}!", "That's it, {n}!")),
                DialogChoice("Pri vodi {je|sta|so|je} {n} {!ovca|ovci|ovce|ovc}.", "There {is|are} {n} {sheep|sheep} at the water.", wrongForm = 2, why = "…", reply = DialogReply("Koliko?", "How many?")),
            )),
        ),
    )

    @Test fun `a dialog with its number - said in words, meant in figures, the forms agreeing, a wrong choice's wrong`() {
        val d = Counts.render(ovce, 20, "sl")
        assertEquals("Dvajset ovc je pri vodi. Preštej jih!", d.lines[0].sl)
        assertEquals("20 sheep are at the water. Count them!", d.lines[0].en)
        val (w1, right, w2) = d.lines[1].choices
        assertEquals("Pri vodi je dvajset ovc.", right.sl)
        assertEquals("Tako je, dvajset!", right.reply?.sl)
        assertEquals("Pri vodi je dvajset ovce.", w1.sl) // 5+: the 3–4 form, marked; the verb agrees
        assertEquals("Pri vodi je dvajset ovca.", w2.sl) // then the singular
        assertEquals("From five on, the genitive plural: dvajset ovc.", w1.why) // the why explains the right one, in the scene's words
        assertEquals("There is 20 sheep at the water.", w2.en) // the meaning mirrors the slip, as the files' do

        val two = Counts.render(ovce, 2, "sl")
        assertEquals(listOf("Pri vodi sta dve ovce.", "Pri vodi sta dve ovci.", "Pri vodi sta dve ovc."), two.lines[1].choices.map { it.sl })
        assertEquals("Dve ovci sta pri vodi. Preštej jih!", two.lines[0].sl)
        val one = Counts.render(ovce, 101, "sl")
        assertEquals("Pri vodi je sto ena ovca.", one.lines[1].choices[1].sl)
        assertEquals("Pri vodi je sto ena ovce.", one.lines[1].choices[0].sl)
        assertEquals("Sto ena ovca je pri vodi. Preštej jih!", one.lines[0].sl)
        val few = Counts.render(ovce, 3, "sl")
        assertEquals(listOf("Pri vodi so tri ovc.", "Pri vodi so tri ovce.", "Pri vodi so tri ovci."), few.lines[1].choices.map { it.sl })
        // a dialog without a count is played as it is
        val plain = Dialog("x", listOf(DialogLine(who = "a", sl = "Brez {števil}.", en = "No numbers.")))
        assertEquals(plain, Counts.render(plain, 5, "sl"))
    }

    @Test fun `a wrong form that comes out right, or as another wrong one, is left out`() {
        val sheep = Dialog(
            "sheep", count = DialogCount("dice", 1, 30),
            lines = listOf(
                DialogLine(who = "jack", sl = "Count the cows!", en = "Preštej krave!"),
                DialogLine(choices = listOf(
                    DialogChoice("There are {n} {!sheep|sheep}.", "Tam je {n} ovc.", wrongForm = 1, why = "…"),
                    DialogChoice("There are {n} {sheep|sheep}.", "Tam je {n} ovc.", ok = true),
                    DialogChoice("There are {n} {!cow|cows}.", "Tam je {n} krav.", wrongForm = 2, why = "…"),
                    DialogChoice("There are {n} {!cow|cows}.", "Tam je {n} krav.", wrongForm = 1, why = "…"),
                )),
            ),
        )
        val d = Counts.render(sheep, 7, "en")
        assertEquals(listOf("There are seven sheep.", "There are seven cow."), d.lines[1].choices.map { it.sl })
        // German and Italian: the number word agrees with the gender, Italian looks at the word after it
        val it = Dialog("legna", count = DialogCount("wood", 1, 20, per = 10, gender = "m"), lines = listOf(DialogLine(who = "t", sl = "Porto {n} {ciocco|ciocchi}.", en = "Prinesem {n} {poleno|poleni|polena|polen}.")))
        assertEquals("Porto un ciocco.", Counts.render(it, 1, "it").lines[0].sl)
        assertEquals("Prinesem 1 poleno.", Counts.render(it, 1, "it").lines[0].en)
        assertEquals("Porto ventuno ciocchi.", Counts.render(it, 21, "it").lines[0].sl)
        val de = Dialog("holz", count = DialogCount("dice", 1, 9, gender = "n"), lines = listOf(DialogLine(who = "m", sl = "Ich bringe {n} {Scheit|Scheite}.", en = "I bring {n} {log|logs}.")))
        assertEquals("Ich bringe ein Scheit.", Counts.render(de, 1, "de").lines[0].sl)
        assertEquals("Ich bringe drei Scheite.", Counts.render(de, 3, "de").lines[0].sl)
    }
}
