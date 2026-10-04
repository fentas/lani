package si.lanisce.lani.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SloveneTest {
    @Test fun `diacritics are Slovene`() {
        assertTrue(Slovene.looks("čebelnjak"))
        assertTrue(Slovene.looks("Danes piha burja, zato je zelo mraz."))
    }

    @Test fun `Slovene sentences without diacritics`() {
        assertTrue(Slovene.looks("Moja partnerka je iz Gorice."))
        assertTrue(Slovene.looks("Kako si?"))
        assertTrue(Slovene.looks("Dober dan"))
    }

    @Test fun `English is not`() {
        assertFalse(Slovene.looks("My name is Jan."))
        assertFalse(Slovene.looks("good day"))
        assertFalse(Slovene.looks("Where are you from?"))
        assertFalse(Slovene.looks("in the kitchen"))
        assertFalse(Slovene.looks(""))
    }

    @Test fun `unknown single words are left to the voice store`() = assertFalse(Slovene.looks("kozolec"))
}
