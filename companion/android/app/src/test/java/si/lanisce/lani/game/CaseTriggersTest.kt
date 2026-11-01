package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.scene.Numbers
import java.io.File

/**
 * The trigger table as the hint chip reads it (game/CaseTriggers.kt): the bundled table is the repository's
 * (companion/grammar/sl/data/case-triggers.json), read at once and offline; a verb's forms and a measure's as the case lens
 * makes them (bridge/src/case-lens.ts); the prepositions of place, the numbers and their categories, the pronouns only
 * ever the subject.
 */
class CaseTriggersTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "grammar/sl/data").isDirectory }
    private val repo = CaseTriggers.parse(File(companion, "grammar/sl/data/case-triggers.json").readText())
    private val t = CaseTriggers.table

    @Test fun `the bundled table is the repository's, read whole`() {
        assertEquals(repo.prepositions, t.prepositions)
        assertEquals(repo.governors, t.governors)
        assertEquals(repo.negations to repo.beforeVerb, t.negations to t.beforeVerb)
        assertEquals(repo.quantity to repo.measures, t.quantity to t.measures)
        assertEquals(repo.fromFive to repo.agreeing, t.fromFive to t.agreeing)
        assertEquals(repo.subjects, t.subjects)
        // every preposition of the table, with its senses: v where to and where, with their pages
        assertTrue(t.prepositions.size > 30)
        assertEquals(
            listOf(CaseTriggers.Take(Case.ACC, "motion", "kam-tozilnik", time = "kdaj-cas"), CaseTriggers.Take(Case.LOC, "rest", "kje-mestnik-orodnik")),
            CaseTriggers.takes("V"),
        )
        assertTrue("ne" in t.beforeVerb && "nimam" in t.negations && "ne" !in t.negations)
    }

    @Test fun `a verb's forms and a measure's as the case lens makes them`() {
        val help = CaseTriggers.verbForms("pomagati", "pomagam")
        assertTrue(help.containsAll(listOf("pomagam", "pomagaš", "pomaga", "pomagava", "pomagata", "pomagamo", "pomagate", "pomagajo", "pomagati", "pomagal", "pomagala", "pomagali", "pomagaj", "pomagajte", "pomagajmo", "pomagajva", "pomagajta")))
        // not the supine (pomagat): without the dictionary brat, of brati, would be the brother
        assertFalse("pomagat" in help || "brat" in CaseTriggers.verbForms("brati", "berem"))
        // an e-stem: pišejo and pišo, the imperative piši
        assertTrue(CaseTriggers.verbForms("pisati", "pišem").containsAll(listOf("pišejo", "pišo", "piši", "pišite", "pisal")))
        // the table's verbs by every form: bati se (bojim), dati (dam, not da: most often "that")
        assertEquals(listOf(Case.GEN), t.governors.getValue("bojim").single().cases)
        assertEquals(listOf(Case.DAT, Case.ACC), t.governors.getValue("dam").single().cases)
        assertNull(t.governors["da"])
        // veseliti: with se the genitive (veselim se počitnic), without it the accusative of who is glad (veseli me)
        assertEquals(setOf(true to listOf(Case.GEN), false to listOf(Case.ACC)), t.governors.getValue("veseli").map { it.se to it.cases }.toSet())
        // the words of feeling, and those that govern what follows them (poln vode)
        assertEquals(listOf(Case.DAT), t.governors.getValue("všeč").single().cases)
        assertTrue(t.governors.getValue("poln").single().after)
        // measures: kozarec's fleeting e and its soft c (kozarca, kozarcem, kozarcev), skodelica's genitive plural
        assertTrue(CaseTriggers.measureForms("kozarec").containsAll(listOf("kozarec", "kozarca", "kozarcu", "kozarcem", "kozarcev", "kozarci")))
        assertTrue(CaseTriggers.measureForms("skodelica").containsAll(listOf("skodelica", "skodelico", "skodelice", "skodelic", "skodelicami")))
        assertTrue(CaseTriggers.isAmount("Kos") && CaseTriggers.isAmount("kozarca") && CaseTriggers.isAmount("malo"))
        // kupi is kupiti's (buy!), not a heap's (kup)
        assertFalse(CaseTriggers.isAmount("kupi"))
    }

    @Test fun `places, numbers and who does it`() {
        val places = t.prepositions.keys.filter { CaseTriggers.place(it) }.toSet()
        assertEquals(setOf("v", "na", "ob", "pod", "nad", "pred", "med", "za"), places)
        // za + the accusative: where to, before its "for"; po: after (the locative), to fetch (the accusative)
        assertEquals("motion", CaseTriggers.sense("za", Case.ACC))
        assertEquals("after" to "fetch", CaseTriggers.sense("po", Case.LOC) to CaseTriggers.sense("po", Case.ACC))
        assertEquals(
            listOf(Numbers.MANY, Numbers.MANY, Numbers.TWO, Numbers.TWO, Numbers.FEW, Numbers.ONE, Numbers.MANY, Numbers.TWO, Numbers.MANY),
            listOf("pet", "petindvajset", "dvema", "oba", "štirje", "eden", "5", "2", "sto").map { CaseTriggers.numberCategory(it) },
        )
        assertNull(CaseTriggers.numberCategory("hiša"))
        assertTrue(listOf("jaz", "on", "midva", "oni", "vi").all(CaseTriggers::isSubject))
        assertFalse(listOf("mi", "ti", "me", "se").any(CaseTriggers::isSubject))
    }
}
