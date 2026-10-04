package si.lanisce.lani.ui.stage

import si.lanisce.lani.data.Exercise
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang

/**
 * What people say on the stage and in intros when their own lines (lani.villager/v0 `lines`) don't
 * cover it: built-in lines, each in the ti and the vi form. Slovene said to Jan uses masculine forms.
 * A1–A2, short enough to read at a glance.
 *
 * In Italian (a learner whose village is Italian, the second learner's) they say tu: the villagers speak to a child, and vi (Lei)
 * is only how the learner addresses them. Their Slovene is then the learner's translation: the ti form, or [G.itSl]
 * where the Slovene line says something the Italian doesn't (Jan's name). In German (a village of the kaernten pack)
 * they say du likewise; its Slovene is [G.deSl], else the Italian's, else the ti form, and its Italian the [G.it] line.
 * In English (the lakeland village) the English line is said, or [G.enSaid] where that names Jan, meant in Slovene in the
 * register's form. A line keeps its Slovene and English, and the learner's base language when it has a line in it
 * (German for a learner of Slovene from German).
 */
object Lines {
    /**
     * One built-in line: [ti] and [vi] forms (often the same), English, the friendship level it needs, in Italian
     * ([it]; [itSl]: its Slovene translation when that isn't the ti form) and in German ([de]; [deSl] likewise), what
     * English villagers say where [en] names Jan ([enSaid]), and the times of day it is said at ([times], a line's
     * `when`: "Lep dan še naprej!" by day; none, any time).
     */
    private class G(
        val ti: String, val vi: String, val en: String, val level: Int = 0,
        val it: String? = null, val itSl: String? = null, val de: String? = null, val deSl: String? = null, val enSaid: String? = null,
        val times: Set<TimeOfDay> = emptySet(),
    ) {
        constructor(
            both: String, en: String, level: Int = 0, it: String? = null, itSl: String? = null, de: String? = null, deSl: String? = null, enSaid: String? = null,
            times: Set<TimeOfDay> = emptySet(),
        ) : this(both, both, en, level, it, itSl, de, deSl, enSaid, times)

        fun of(register: String): VillagerLine {
            val pair = L10n.pair
            val sl = if (register == "vi") vi else ti
            // away from Jan's Slovene village, a line that names Jan is said without the name, in every language
            val english = enSaid ?: en
            val said = when (pair.target) {
                Lang.IT -> it?.let { linkedMapOf("it" to it, "sl" to (itSl ?: ti), "en" to english) }
                Lang.DE -> de?.let { linkedMapOf("de" to it, "sl" to (deSl ?: itSl ?: ti), "en" to english) }
                Lang.EN -> linkedMapOf("en" to english, "sl" to if (enSaid != null) itSl ?: sl else sl)
                Lang.SL -> null
            } ?: linkedMapOf("sl" to sl, "en" to en)
            // the learner's base, when the line has it (a learner of Slovene from German reads the German)
            val base = when (pair.base) {
                Lang.IT -> it
                Lang.DE -> de
                else -> null
            }
            if (base != null && pair.base.code !in said) said[pair.base.code] = base
            return VillagerLine(said, level, times)
        }
    }

    private fun List<G>.of(register: String) = map { it.of(register) }

    private val CHEER = listOf(
        G("Bravo!", "Well done!", it = "Bravo!", de = "Super!"),
        G("Točno tako!", "Exactly!", it = "Esatto!", de = "Genau!"),
        G("Odlično!", "Excellent!", it = "Ottimo!", de = "Ausgezeichnet!"),
        G("Tako je!", "That's right!", it = "Giusto!", de = "Richtig!"),
        G("Lepo, Jan!", "Nice, Jan!", 1, it = "Che bravo!", itSl = "Tako priden!", de = "Schön gemacht!", deSl = "Lepo narejeno!", enSaid = "Nicely done!"),
        G("Kar tako naprej!", "Keep it up!", 1, it = "Continua così!", de = "Weiter so!"),
        G("Vidiš, da znaš!", "Vidite, da znate!", "You see, you know it!", 2, it = "Vedi che lo sai!", de = "Siehst du, du kannst es!"),
    )

    private val COMFORT = listOf(
        G("Nič hudega. Še enkrat!", "Never mind. Once more!", it = "Non fa niente. Ancora una volta!", de = "Macht nichts. Noch einmal!"),
        G("Nič ne de.", "It doesn't matter.", it = "Non importa.", de = "Das macht nichts."),
        G("Vsak se kdaj zmoti.", "Everyone makes mistakes sometimes.", it = "Tutti sbagliano, qualche volta.", de = "Jeder macht mal einen Fehler."),
        G("Počasi, saj gre.", "Slowly, you're getting there.", it = "Piano piano, ci arrivi.", de = "Langsam, das wird schon."),
        G("Naslednjič bo šlo.", "It'll work next time.", 1, it = "La prossima volta andrà meglio.", de = "Nächstes Mal klappt es."),
        G("Iz napak se učimo.", "We learn from mistakes.", 1, it = "Sbagliando s'impara.", de = "Aus Fehlern lernt man."),
    )

    private val ALMOST = listOf(
        G("Skoraj!", "Almost!", it = "Quasi!", de = "Fast!"),
        G("Zelo blizu!", "Very close!", it = "Ci sei quasi!", itSl = "Skoraj si že tam!", de = "Ganz knapp!", deSl = "Zelo blizu!"),
        G("Skoraj prav, še malo!", "Almost right, just a little more!", it = "Quasi giusto, ancora un pochino!", de = "Fast richtig, nur noch ein bisschen!"),
        G("Samo še malenkost.", "Just a tiny thing left.", 1, it = "Manca solo una piccola cosa.", de = "Nur noch eine Kleinigkeit."),
    )

    private val LISTEN = listOf(
        G("Poslušaj …", "Poslušajte …", "Listen …", it = "Ascolta …", de = "Hör zu …"),
        G("Dobro prisluhni.", "Dobro prisluhnite.", "Listen carefully.", it = "Ascolta bene.", de = "Hör gut zu."),
        G("Kaj slišiš?", "Kaj slišite?", "What do you hear?", it = "Che cosa senti?", de = "Was hörst du?"),
    )

    private val THINK = listOf(
        G("Hm, pomisli …", "Hm, pomislite …", "Hm, think …", it = "Mmm, pensaci …", de = "Hm, denk nach …"),
        G("Namig ti bo pomagal.", "Namig vam bo pomagal.", "The hint will help you.", it = "Il suggerimento ti aiuterà.", de = "Der Tipp hilft dir."),
        G("Počasi, brez skrbi.", "Take your time, no worries.", it = "Con calma, non preoccuparti.", de = "Lass dir Zeit, keine Sorge."),
    )

    private val GREET = listOf(
        G("Pozdravljen, Jan!", "Hello, Jan!", it = "Ciao! Eccoti qua!", itSl = "Živjo! Tu si!", de = "Servus! Da bist du ja!", enSaid = "Hello! There you are!"),
        G("Greva vadit?", "Shall we practise, the two of us?", 1, it = "Ci esercitiamo insieme?", itSl = "Bova vadila skupaj?", de = "Üben wir zusammen?"),
        G("Lepo te je videti!", "Lepo vas je videti!", "Nice to see you!", 1, it = "Che bello vederti!", de = "Schön, dich zu sehen!"),
    )

    /** The day, the evening and the night each have their own wish ([Director] says the ones for the time). */
    private val BYE = listOf(
        G("Adijo!", "Na svidenje!", "Bye!", it = "Ciao!", de = "Servus!"),
        G("Se vidiva!", "See you!", it = "Ci vediamo!", de = "Bis bald!"),
        G("Lep dan še naprej!", "Have a nice day!", it = "Buona giornata!", de = "Noch einen schönen Tag!", times = setOf(TimeOfDay.MORNING, TimeOfDay.AFTERNOON)),
        G("Lep večer še naprej!", "Have a nice evening!", it = "Buona serata!", de = "Noch einen schönen Abend!", times = setOf(TimeOfDay.EVENING)),
        G("Lahko noč!", "Good night!", it = "Buonanotte!", de = "Gute Nacht!", times = setOf(TimeOfDay.EVENING, TimeOfDay.NIGHT)),
    )

    fun cheer(register: String) = CHEER.of(register)
    fun comfort(register: String) = COMFORT.of(register)
    fun almost(register: String) = ALMOST.of(register)
    fun listen(register: String) = LISTEN.of(register)
    fun think(register: String) = THINK.of(register)
    fun greet(register: String) = GREET.of(register)
    fun bye(register: String) = BYE.of(register)

    /** What they say as a new exercise comes up, by its kind: a few to vary. */
    fun leadIns(ex: Exercise, register: String): List<VillagerLine> = when (ex) {
        is Exercise.Flashcard -> listOf(
            G("Se spomniš te besede?", "Se spomnite te besede?", "Do you remember this word?", it = "Ti ricordi questa parola?", de = "Weißt du dieses Wort noch?"),
            G("Kaj pomeni tale beseda?", "What does this word mean?", it = "Che cosa vuol dire questa parola?", de = "Was bedeutet dieses Wort?"),
        )
        is Exercise.Choice -> if (ex.audio != null) LISTEN else listOf(
            G("Kaj je prav?", "What's right?", it = "Qual è giusto?", de = "Was ist richtig?"),
            G("Izberi pravi odgovor.", "Izberite pravi odgovor.", "Pick the right answer.", it = "Scegli la risposta giusta.", de = "Wähl die richtige Antwort."),
        )
        is Exercise.Cloze -> listOf(
            G("Kaj manjka?", "What's missing?", it = "Che cosa manca?", de = "Was fehlt?"),
            G("Dopolni stavek.", "Dopolnite stavek.", "Complete the sentence.", it = "Completa la frase.", de = "Ergänze den Satz."),
        )
        is Exercise.Reorder -> listOf(
            G("Sestavi stavek.", "Sestavite stavek.", "Put the sentence together.", it = "Metti in ordine la frase.", de = "Bau den Satz zusammen."),
            G("V kakšnem vrstnem redu?", "In what order?", it = "In che ordine?", de = "In welcher Reihenfolge?"),
        )
        is Exercise.Translate -> listOf(
            howDoYouSay(),
            G("Poskusi prevesti.", "Poskusite prevesti.", "Try translating it.", it = "Prova a tradurre.", de = "Versuch es zu übersetzen."),
        )
        is Exercise.Dictation -> LISTEN
        is Exercise.Speak -> listOf(G("Povej na glas!", "Povejte na glas!", "Say it out loud!", it = "Dillo ad alta voce!", de = "Sag es laut!"))
        is Exercise.Free -> listOf(G("Napiši mi nekaj.", "Napišite mi nekaj.", "Write me something.", it = "Scrivimi qualcosa.", de = "Schreib mir etwas."))
        is Exercise.Scenario -> listOf(G("Kaj bi rekel?", "Kaj bi rekli?", "What would you say?", it = "Che cosa diresti?", de = "Was würdest du sagen?"))
        is Exercise.Multi -> listOf(G("Poišči vse prave.", "Poiščite vse prave.", "Find all the right ones.", it = "Trova tutte quelle giuste.", de = "Finde alle richtigen."))
        is Exercise.Unsupported -> listOf(G("Tole je nekaj novega.", "This is something new.", it = "Questa è una cosa nuova.", de = "Das ist etwas Neues."))
    }.of(register)

    /** An intro's request, by what the run is (the villager's own greeting may come first). */
    fun ask(run: Run, register: String): VillagerLine = when (run) {
        is Run.Quest -> HELP
        is Run.Module -> if (run.giver != null) HELP else G("Gremo vadit!", "Let's go and practise!", it = "Andiamo a esercitarci!", de = "Komm, wir üben!")
        is Run.Pack -> G("Naučim te nekaj novih besed.", "Naučim vas nekaj novih besed.", "Let me teach you some new words.", it = "Ti insegno qualche parola nuova.", de = "Ich bringe dir ein paar neue Wörter bei.")
        is Run.Gather -> when (run.res) {
            Res.FOOD -> G("Greva na njivo?", "Shall we go to the field?", it = "Andiamo nel campo?", de = "Gehen wir auf den Acker?")
            Res.WOOD -> G("Greva v gozd po les?", "Shall we go to the forest for wood?", it = "Andiamo nel bosco a prendere la legna?", de = "Gehen wir in den Wald, Holz holen?")
            Res.STONE -> G("Greva v kamnolom?", "Shall we go to the quarry?", it = "Andiamo alla cava?", de = "Gehen wir zum Steinbruch?")
            Res.WISDOM -> G("Sedi k meni pod lipo.", "Sedite k meni pod lipo.", "Sit with me under the linden.", it = "Siediti con me sotto il tiglio.", de = "Setz dich zu mir unter die Linde.")
        }
        is Run.Event -> when (run.kind) {
            EventKind.WOLVES -> G("Volkovi so pri vasi! Pomagaj mi!", "Volkovi so pri vasi! Pomagajte mi!", "Wolves by the village! Help me!", it = "Ci sono i lupi vicino al paese! Aiutami!", de = "Wölfe beim Dorf! Hilf mir!")
            EventKind.BEAR -> G("Medved je v sadovnjaku! Pomagaj mi!", "Medved je v sadovnjaku! Pomagajte mi!", "A bear in the orchard! Help me!", it = "C'è un orso nel frutteto! Aiutami!", de = "Ein Bär im Obstgarten! Hilf mir!")
            EventKind.STORM -> G("Nevihta prihaja. Hitro, pomagaj mi!", "Nevihta prihaja. Hitro, pomagajte mi!", "A storm is coming. Quick, help me!", it = "Arriva un temporale. Presto, aiutami!", de = "Ein Unwetter kommt. Schnell, hilf mir!")
            EventKind.MERCHANT -> G(
                "Trgovec iz Gorice je tu. Mi pomagaš?", "Trgovec iz Gorice je tu. Mi pomagate?", "The merchant from Gorizia is here. Will you help me?",
                it = "È arrivato il mercante. Mi aiuti?", itSl = "Trgovec je prišel. Mi pomagaš?", de = "Der Händler ist da. Hilfst du mir?",
                enSaid = "The merchant is here. Will you help me?",
            )
            EventKind.FESTIVAL -> G("Danes je praznik! Pridi, gostje že čakajo.", "Danes je praznik! Pridite, gostje že čakajo.", "It's a feast day! Come, the guests are waiting.", it = "Oggi è festa! Vieni, gli ospiti aspettano già.", de = "Heute ist ein Fest! Komm, die Gäste warten schon.")
        }
        Run.Review -> G("Greva ponavljat?", "Shall we review, the two of us?", it = "Ripassiamo insieme?", itSl = "Bova ponavljala skupaj?", de = "Wiederholen wir zusammen?")
        is Run.Talk -> G("Pogovoriva se.", "Let's talk, the two of us.", it = "Facciamo due chiacchiere.", itSl = "Malo poklepetajva.", de = "Plaudern wir ein bisschen.")
        is Run.Stranger -> HELP
    }.of(register)

    /** "How do you say this in …?": the language asked for is the learner's target, in every translation. */
    private fun howDoYouSay(): G = when (L10n.pair.target) {
        Lang.SL -> G("Kako se to reče po slovensko?", "How do you say this in Slovene?", it = "Come si dice in sloveno?", de = "Wie sagt man das auf Slowenisch?")
        Lang.IT -> G("Kako se to reče po italijansko?", "How do you say this in Italian?", it = "Come si dice in italiano?", de = "Wie sagt man das auf Italienisch?")
        Lang.DE -> G("Kako se to reče po nemško?", "How do you say this in German?", it = "Come si dice in tedesco?", de = "Wie sagt man das auf Deutsch?")
        Lang.EN -> G("Kako se to reče po angleško?", "How do you say this in English?", it = "Come si dice in inglese?", de = "Wie sagt man das auf Englisch?")
    }

    private val HELP = G("Mi pomagaš?", "Mi pomagate?", "Will you help me?", it = "Mi aiuti?", de = "Hilfst du mir?")

    /**
     * The end of a run: the tally of [correct] answers among the run's [total] exercises, then a word on
     * how it went ("7 od 10 pravilno. Dobro delo!"). With nothing right, only thanks for the effort (the
     * tally would sting); null when nothing was [answered].
     */
    fun closing(correct: Int, answered: Int, total: Int, register: String): VillagerLine? {
        if (answered <= 0 || total <= 0) return null
        if (correct <= 0) {
            return G("Hvala za trud! Jutri bo še bolje.", "Thanks for trying! Tomorrow will be even better.", it = "Grazie per l'impegno! Domani andrà ancora meglio.", de = "Danke fürs Mitmachen! Morgen wird es noch besser.").of(register)
        }
        val praise = when {
            correct * 10 >= total * 8 -> G("Odlično si se odrezal!", "Odlično ste se odrezali!", "You did excellently!", it = "Sei stato bravissimo!", de = "Das hast du super gemacht!")
            correct * 2 >= total -> G("Dobro delo!", "Good work!", it = "Bel lavoro!", de = "Gute Arbeit!")
            else -> G("Jutri bo še bolje.", "Tomorrow will be even better.", it = "Domani andrà ancora meglio.", de = "Morgen wird es noch besser.")
        }.of(register)
        val tally = mapOf("sl" to "$correct od $total pravilno.", "it" to "$correct su $total giuste.", "de" to "$correct von $total richtig.", "en" to "$correct of $total right.")
        return praise.map { lang, s -> "${tally[lang] ?: tally.getValue("en")} $s" }
    }

    /**
     * Their own lines, topped up with the built-in [generic] ones while fewer than two of their own are
     * open at [level] (a villager with one cheer shouldn't say it twenty times).
     */
    fun pool(own: List<VillagerLine>, generic: List<VillagerLine>, level: Int): List<VillagerLine> =
        if (Bonds.linesFor(own, level).size >= 2) own else own + generic

    /**
     * One line of [pool] open at [level], by the dice [roll] (0..1): warmer lines weigh more (a line of
     * level 2 counts three times), and [avoid] (the line said last) is skipped when there is another.
     */
    fun pick(pool: List<VillagerLine>, level: Int, roll: Float, avoid: String? = null): VillagerLine? {
        val open = Bonds.linesFor(pool, level).distinctBy { it.target }.let { o -> if (o.size > 1) o.filter { it.target != avoid } else o }
        if (open.isEmpty()) return null
        val weights = open.map { 1 + it.level.coerceAtLeast(0) }
        var r = roll.coerceIn(0f, 0.99999f) * weights.sum()
        for ((i, w) in weights.withIndex()) {
            r -= w
            if (r < 0) return open[i]
        }
        return open.last()
    }

    /** The warmest line of [pool] their [level] allows (a greeting: "Ej, Jan! Kako si?"). */
    fun warmest(pool: List<VillagerLine>, level: Int): VillagerLine? = Bonds.linesFor(pool, level).firstOrNull()
}
