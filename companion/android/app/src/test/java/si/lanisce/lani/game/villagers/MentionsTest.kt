package si.lanisce.lani.game.villagers

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair
import java.io.File

/**
 * Who a text names (companion/VILLAGERS.md, "Who a text may name"): a villager by their own name as the village's language
 * says it in a sentence (Slovene declines it), with or without their title; not the learner, not a word that is a name
 * only with its capital, not someone else with the same first name and a surname.
 */
class MentionsTest {
    private fun v(id: String, name: String, source: String = "curated") = Villager(id, name, art = "man", source = source)
    private val primorska = listOf(
        v("micka", "Babica Micka"), v("luka", "Pastir Luka"), v("zala", "Zala"), v("janez", "Stari Janez"), v("france", "Mlinar France"),
        v("tine", "Tine"), v("ancka", "Teta Ančka"), v("anton", "Čebelar Anton"), v("tone", "Kovač Tone"), v("marko", "Vinar Marko"),
        v("vida", "Gostilničarka Vida"), v("nejc", "Nejc"), v("mojca", "Učiteljica Mojca"), v("joze", "Lovec Jože"),
    )
    private val sl = Names(primorska, "sl")
    private val pair = L10n.pair

    @Before fun setUp() {
        L10n.pair = LangPair.DEFAULT
    }

    @After fun tearDown() {
        Mentions.cast = emptyList()
        Mentions.known = null
        L10n.pair = pair
    }

    @Test fun `a Slovene name is found in every case, and as a possessive`() {
        assertEquals(setOf("anton"), sl.of("Jan! Anton toči med! Poglej, kako teče!"))
        assertEquals(setOf("anton"), sl.of("Letijo na lipo. Hitro, pokliči Antona!"))
        assertEquals(setOf("anton"), sl.of("Pomagaj Antonu, prosim."))
        assertEquals(setOf("anton"), sl.of("Grem z Antonom k panjem."))
        assertEquals(setOf("anton"), sl.of("Antonov med je najboljši."))
        assertEquals(setOf("anton"), sl.of("Čebelar Anton pregleduje panje."))
        assertEquals(setOf("micka"), sl.of("Kosilo je pri Micki."))
        assertEquals(setOf("micka"), sl.of("Mickina kuhinja diši po potici."))
        assertEquals(setOf("micka"), sl.of("Babica Micka peče."))
        assertEquals(setOf("tone"), sl.of("Nesi to Tonetu."))
        assertEquals(setOf("tone", "marko"), sl.of("Toneta ni, Markov kombi pa je tu."))
        assertEquals(setOf("nejc"), sl.of("Nejčeva žoga je v ribniku. Kje je Nejc?"))
        assertEquals(setOf("mojca"), sl.of("Mojčina tabla je polna."))
        assertEquals(setOf("luka"), sl.of("Luko je spet iskala Bela."))
        assertEquals(setOf("joze"), sl.of("Jožetova preža je v gozdu."))
        assertEquals(setOf("janez", "zala"), sl.of("Pri Janezu je bila tudi Zala."))
        assertEquals(setOf("mojca", "tine", "zala"), sl.of("Mojca uči vse otroke, od Tineta do Zale."))
        assertEquals(setOf("ancka"), sl.of("Tetin radio? Ne, Ančkin radio!"))
    }

    @Test fun `the learner, a word in lower case and someone else with a surname are nobody`() {
        assertTrue(sl.of("Jan! Jana ni doma, Janu pa pišem.").isEmpty())
        assertTrue("Janez isn't Jan, and Jan isn't Janez", sl.of("Jan").isEmpty() && sl.of("Janez") == setOf("janez"))
        assertTrue("luka: a harbour", sl.of("Ladja je v luki.").isEmpty())
        assertTrue("vidi: sees", sl.of("Ali vidi tole?").isEmpty())
        assertTrue(sl.of("France Prešeren je največji slovenski pesnik.").isEmpty())
        assertTrue(sl.of("Pesmi Franceta Prešerna beremo pod lipo.").isEmpty())
        assertTrue(sl.of("20. maja se je rodil Anton Janša, slavni slovenski čebelar.").isEmpty())
        assertEquals("the miller himself", setOf("france"), sl.of("France gre na njivo. Kaj pravi France?"))
        assertEquals("two of the village side by side are both", setOf("zala", "nejc"), sl.of("Zala Nejca ne mara."))
        assertTrue("a title of someone else's", sl.of("Janez pozna pesem o Lepi Vidi.").minus("janez").isEmpty())
        assertEquals("their own title, declined", setOf("janez"), sl.of("Pomagaj Staremu Janezu, prosim."))
        assertEquals("a sentence starts with a capital", setOf("anton"), sl.of("Jan! Pokliči Antona."))
        val en = Names(listOf(v("arthur", "Beekeeper Arthur")), "en")
        assertTrue(en.of("They say it was where King Arthur's sword went back into the water.").isEmpty())
        assertEquals(setOf("arthur"), en.of("Beekeeper Arthur's honey. Arthur smiles."))
    }

    @Test fun `other languages - Italian and English as they are, German with a genitive -s`() {
        val it = Names(listOf(v("rosa", "Nonna Rosa"), v("bruno", "Fabbro Bruno")), "it")
        assertEquals(setOf("rosa"), it.of("Nonna Rosa cucina il frico."))
        assertTrue("rosa: pink; bruno: brown", it.of("Una rosa rosa e un cane bruno.").isEmpty())
        val de = Names(listOf(v("resi", "Oma Resi"), v("florian", "Hirte Florian")), "de")
        assertEquals(setOf("resi"), de.of("Resis Küche ist warm."))
        assertEquals("German capitalises its nouns: that's no surname", setOf("florian"), de.of("Hat Florian Hunger?"))
        val en = Names(listOf(v("maggie", "Granny Maggie"), v("tyson", "Mr Tyson"), v("harry", "Harry")), "en")
        assertEquals(setOf("maggie"), en.of("Granny Maggie's scones are the best."))
        assertEquals(setOf("tyson", "harry"), en.of("Mr Tyson says Harry is OK."))
    }

    @Test fun `someone who moved in goes by their first name, and their family name is theirs`() {
        val ana = v("n-ana-furlan", "Ana Furlan", source = "village")
        val names = Names(primorska + ana, "sl")
        assertEquals(setOf("n-ana-furlan"), names.of("Ana Furlan je prinesla jajca."))
        assertEquals(setOf("n-ana-furlan"), names.of("Ani je ime Ana."))
        assertEquals(setOf("anton", "n-ana-furlan"), names.of("Anton in Ana."))
    }

    @Test fun `a happening names who its dialog, its title and its memory name`() {
        val dir = listOf(File("../../scenes"), File("../scenes"), File("companion/scenes")).first { it.isDirectory }
        val scene = parseScene(dir.resolve("pri-cebelnjaku.json").readText())
        fun of(id: String) = Mentions.inHappening(scene, scene.happenings.first { it.id == id }, sl)
        assertEquals("Zala's honey: Anton toči med", setOf("zala", "anton"), of("med"))
        assertEquals("Janez's swarm: pokliči Antona", setOf("anton"), of("roj"))
        assertEquals("Anton's hives: “Tako mi reče samo Zala.”", setOf("zala"), of("panji") - "anton")
    }

    @Test fun `a villager says only lines that name people Jan knows`() {
        val lines = listOf(
            VillagerLine("Nejc me je spet ujel!", "Nejc caught me again!"),
            VillagerLine("Dober dan!", "Good day!"),
        )
        assertEquals("without a village, everything", 2, lines.at(TimeOfDay.MORNING).size)
        Mentions.cast = primorska
        Mentions.known = setOf("micka", "zala")
        assertEquals(listOf("Dober dan!"), lines.at(TimeOfDay.MORNING).map { it.target })
        Mentions.known = setOf("micka", "zala", "nejc")
        assertEquals(2, lines.at(TimeOfDay.MORNING).size)
    }
}
