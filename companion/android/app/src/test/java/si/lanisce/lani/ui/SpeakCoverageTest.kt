package si.lanisce.lani.ui

import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Slovene

class SpeakCoverageTest {
    private val sl = Slovene::looks

    @Test fun `feedback hears the whole sentence`() {
        assertEquals("Moja partnerka je iz Gorice.", sloveneOf(Exercise.Cloze("Moja partnerka ___ iz Gorice.", listOf("je")), sl))
        // not what the brackets at the end add: a translation, a word form question's lemma
        assertEquals("Nimam časa.", sloveneOf(Exercise.Cloze("Nimam ___. (I don't have time.)", listOf("časa")), sl))
        assertEquals("Jaz sedem na klop.", sloveneOf(Exercise.Cloze("Jaz ___ na klop. (sesti)", listOf("sedem")), sl))
        assertEquals("Imenujem se Jan.", sloveneOf(Exercise.Reorder("My name is Jan.", listOf("se", "Imenujem", "Jan."), listOf(listOf("Imenujem", "se", "Jan."))), sl))
        assertEquals("Sem iz Berlina.", sloveneOf(Exercise.Translate("I'm from Berlin.", listOf("Sem iz Berlina.")), sl))
    }

    @Test fun `choice hears the Slovene side`() {
        // Recognize: the Slovene is the prompt (say); pick: the right option.
        assertEquals("dober dan", sloveneOf(Exercise.Choice("dober dan", listOf("good day", "goodbye"), 0, say = "dober dan"), sl))
        assertEquals("Dobro jutro", sloveneOf(Exercise.Choice("Your neighbour at 8:00, you say…", listOf("Dober jutro", "Dobro jutro"), 1), sl))
        assertNull(sloveneOf(Exercise.Choice("Which is polite?", listOf("formal", "informal"), 0), sl))
        // a sentence with a gap: filled with the right option, without what its brackets add
        assertEquals("Ko bo brada devetkrat okoli mize, se bo kralj zbudil.", sloveneOf(Exercise.Choice("Ko bo brada devetkrat okoli ____, se bo kralj zbudil. (miza)", listOf("mizi", "mize", "mizo"), 1), sl))
        assertEquals("Nimam časa.", sloveneOf(Exercise.Choice("Nimam ___. (I don't have time.)", listOf("čas", "časa"), 1), sl))
    }

    @Test fun `quoted spans terminate and Slovene spans become tappable`() {
        val speak = SpeakSpans(Slovene::looks) {}
        val a = inlineMarkdown("Say „Dober dan“ or *good day* to **Babica Micka**: *Kako ste?*", speak)
        assertTrue(a.text.contains("„Dober dan“"))
        val links = a.getLinkAnnotations(0, a.length).map { a.text.substring(it.start, it.end) }
        assertEquals(listOf("„Dober dan“ 🔈", "Kako ste? 🔈"), links)
        assertTrue(a.getLinkAnnotations(0, a.length).all { it.item is LinkAnnotation.Clickable })
    }

    @Test fun `an option's picture isn't spoken`() {
        assertEquals("ob ribniku", unpictured("🪷 ob ribniku"))
        assertEquals("ob potoku", unpictured("🏞️ ob potoku"))
        assertEquals("pomol", unpictured("pomol"))
        assertEquals("„Dober dan“", unpictured("„Dober dan“"))
        assertEquals("🌲", unpictured("🌲"))
        // the feedback hears the words of a pictured answer
        assertEquals("pod veliko smreko", sloveneOf(Exercise.Choice("Pod čim?", listOf("🌲 pod veliko smreko", "🌳 pod staro lipo"), 0, sayOptions = true), sl))
    }
}
