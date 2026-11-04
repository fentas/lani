package si.lanisce.lani.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The hint chip's rules for the German book (companion/grammar/de; companion/SCENES.md, "The hint";
 * docs/plans/05-built/hint-chip-de.md): each page's rule in words ([Rule.text], a string table's key, in the learner's
 * base), the sentence the 🔊 says of it ([Rule.said]), models with other words, the question where the page asks one
 * ("Wen? Was?", "Wo? Wohin?"), and what in the sentence decides the turn's form ([Look], tried in the page's order: the
 * first found is the trigger). What decides a German word's case is German's trigger table's, read here and repeated
 * nowhere ([Table]: companion/grammar/de/data/case-triggers.json, from the case-de branch): the preposition before it
 * (the two-way ones by the verb: Wohin?, the accusative; Wo?, the dative), the verb that takes a dative, the chunk with its
 * preposition, the verb of being before a nominative. The rest are this file's word lists: who does it (before the verb
 * or after it), the helper before an infinitive or a participle, the participle that decides sein or haben, the noun that
 * decides an article, a number before a plural, the one spoken to (du or Sie). German writes its nouns with a capital,
 * so a noun is plain to see. A form people say is never wrong: the line after «wegen» names the genitive of writing and the
 * dative people say, as the table marks it. Word lists and a table, no dictionary: the chip works at once and offline, and
 * finds a trigger only where the sentence shows it plainly; else the rule alone. It reads, it never grades. Pure once
 * the table is loaded.
 */
object GermanHints {
    /** What the chip looks for in the sentence, by page, in order (each is described where it is found: [find]). */
    enum class Look {
        PREP, CHUNK, VERB_CASE, OBJECT, COPULA, BITTE, SELF, TWO_OBJECTS, GENDER, ADJ, ADJ_NOUN, WHOSE, WEAK, MANY, VERB_MANY, ONE_OR_MORE, THING,
        COUNT, WHO, TO_WHOM, INFINITIVE, DOUBLE_INF, PARTICIPLE, BY_PARTICIPLE, PAST_IMAGINED, FEEL, KEIN, REL, IMAGINED, POLITE, PAIR,
        PARTICLE, ASK_REST, WHEN, VERB_NEXT, NOUN_VERB, PART_ADJ, WHAT_FOR, PASSIVE, WORDEN, VERB_NOUN, PAST,
    }

    /**
     * A page's rule: [text] the card's rule in words and [said] the 🔊's sentence (string tables' keys), [models] with
     * other words than any turn's, [question] (German) where the page asks one, [looks] what decides the form, in order.
     */
    class Rule(val text: String, val said: String, val models: List<String>, val question: String? = null, val looks: List<Look> = emptyList())

    /** The book's pages (companion/grammar/de), by id: every page, A1 to B2. */
    val RULES: Map<String, Rule> = mapOf(
        // A1
        "akkusativ" to Rule("hint.de.ruleAkkusativ", "hintSay.de.ruleAkkusativ", listOf("Ich sehe den Bus. · Ich sehe ihn.", "Er kauft einen Mantel.", "Sie sucht meinen Schlüssel.", "Kennst du mich?", "Wir rufen dich.", "Er liest die Zeitung."), "Wen? Was?", listOf(Look.PREP, Look.COPULA, Look.SELF, Look.OBJECT, Look.BITTE)),
        "artikel-genus" to Rule("hint.de.ruleArtikelGenus", "hintSay.de.ruleArtikelGenus", listOf("der Bus · die Tasche · das Buch", "ein Bus · eine Tasche · ein Buch", "unser Bus · unsere Tasche", "die Busse · die Taschen · die Bücher"), null, listOf(Look.ADJ, Look.GENDER)),
        "du-sie" to Rule("hint.de.ruleDuSie", "hintSay.de.ruleDuSie", listOf("Wie heißt du? · Wie heißen Sie?", "Kommst du? · Kommen Sie?", "Ich danke dir. · Ich danke Ihnen.", "Dein Mantel? · Ihr Mantel?"), "Du oder Sie?", listOf(Look.TO_WHOM)),
        "faelle" to Rule("hint.de.ruleFaelle", "hintSay.de.ruleFaelle", listOf("Der Bus kommt.", "Ich sehe den Bus.", "Ich danke dem Fahrer.", "die Tasche des Fahrers", "Eine Frau kommt.", "Ich sehe einen Bus."), "Wer? Wen? Wem? Wessen?", listOf(Look.COPULA, Look.PREP, Look.OBJECT, Look.BITTE)),
        "fragen" to Rule("hint.de.ruleFragen", "hintSay.de.ruleFragen", listOf("Wo bist du? · Wohin gehst du?", "Woher kommst du?", "Kommst du mit?", "Wie viele Kinder? · Wie viel Zeit?"), null, listOf(Look.THING, Look.ASK_REST, Look.COUNT)),
        "imperativ" to Rule("hint.de.ruleImperativ", "hintSay.de.ruleImperativ", listOf("Lauf! · Lauft! · Laufen Sie!", "Nimm das! · Fahr los!", "Sei still! · Seid still!", "Steh auf!"), null, listOf(Look.TO_WHOM)),
        "kein-nicht" to Rule("hint.de.ruleKeinNicht", "hintSay.de.ruleKeinNicht", listOf("Ich habe keine Zeit.", "Das ist kein Bus.", "Ich komme nicht.", "Es ist nicht weit.", "Sie kauft das Buch nicht."), null, listOf(Look.KEIN, Look.GENDER)),
        "plural" to Rule("hint.de.rulePlural", "hintSay.de.rulePlural", listOf("der Arm · die Arme", "die Bank · die Bänke", "das Kind · die Kinder", "die Frau · die Frauen", "das Auto · die Autos"), "Wie viele?", listOf(Look.MANY, Look.VERB_MANY, Look.ONE_OR_MORE, Look.ADJ, Look.THING, Look.WHO)),
        "possessiv" to Rule("hint.de.rulePossessiv", "hintSay.de.rulePossessiv", listOf("unser Bus · unsere Tasche", "sein Bruder · ihre Schwester", "Ich suche unseren Schlüssel.", "euer Haus · eure Häuser"), "Wem gehört das?", listOf(Look.ADJ, Look.GENDER)),
        "praesens" to Rule("hint.de.rulePraesens", "hintSay.de.rulePraesens", listOf("ich lerne · du lernst · er lernt", "wir lernen · ihr lernt · sie lernen", "ich arbeite · du arbeitest · sie arbeitet", "ich reise · du reist"), "Wer?", listOf(Look.WHO, Look.TO_WHOM)),
        "sein-haben" to Rule("hint.de.ruleSeinHaben", "hintSay.de.ruleSeinHaben", listOf("ich bin · du bist · er ist", "wir sind · ihr seid · sie sind", "ich habe · du hast · sie hat", "wir haben · ihr habt"), "Wer?", listOf(Look.BY_PARTICIPLE, Look.FEEL, Look.THING, Look.WHO)),
        "uhrzeit" to Rule("hint.de.ruleUhrzeit", "hintSay.de.ruleUhrzeit", listOf("halb neun · Viertel nach zehn", "um drei · am Freitag · im März", "Viertel vor zwölf", "dreiviertel neun")),
        "vokalwechsel" to Rule("hint.de.ruleVokalwechsel", "hintSay.de.ruleVokalwechsel", listOf("du fährst · er fährt · ihr fahrt", "du sprichst · er spricht", "du siehst · sie sieht", "du läufst · wir laufen"), "Wer?", listOf(Look.WHO)),
        "wortstellung" to Rule("hint.de.ruleWortstellung", "hintSay.de.ruleWortstellung", listOf("Heute koche ich.", "Ich koche heute.", "Im Sommer fahren wir weg.", "Am Montag arbeitet sie.")),
        "zahlen" to Rule("hint.de.ruleZahlen", "hintSay.de.ruleZahlen", listOf("sechzehn · siebzehn", "dreißig · vierzig", "einundzwanzig", "Wie viele Kinder? · Wie viel Geld?"), null, listOf(Look.MANY)),
        // A2
        "adjektiv-der" to Rule("hint.de.ruleAdjektivDer", "hintSay.de.ruleAdjektivDer", listOf("der alte Mann · die alte Frau", "den alten Mann · dem alten Mann", "dieses kleine Haus", "die alten Häuser"), null, listOf(Look.ADJ, Look.GENDER)),
        "adjektiv-ein" to Rule("hint.de.ruleAdjektivEin", "hintSay.de.ruleAdjektivEin", listOf("ein alter Mann · ein altes Haus", "eine alte Frau", "einen alten Mann", "keine alten Häuser"), null, listOf(Look.ADJ, Look.GENDER)),
        "adjektiv-null" to Rule("hint.de.ruleAdjektivNull", "hintSay.de.ruleAdjektivNull", listOf("frischer Fisch · frische Luft", "mit frischem Fisch · mit frischer Luft", "viele alte Häuser", "frisches Gemüse"), null, listOf(Look.ADJ, Look.PREP)),
        "dativ" to Rule("hint.de.ruleDativ", "hintSay.de.ruleDativ", listOf("Ich helfe dem Mann.", "Ich danke einer Frau.", "mit meinem Bruder", "Das gefällt ihm.", "Sie gibt uns Geld.", "bei unseren Freunden"), "Wem?", listOf(Look.PREP, Look.VERB_CASE)),
        "konnektoren" to Rule("hint.de.ruleKonnektoren", "hintSay.de.ruleKonnektoren", listOf("…, denn es schneit.", "…, deshalb bleibe ich hier.", "…, weil es schneit.", "…, aber ich bleibe hier.")),
        "modalverben" to Rule("hint.de.ruleModalverben", "hintSay.de.ruleModalverben", listOf("Ich kann gut singen.", "Du darfst nicht rauchen.", "Wir müssen jetzt lernen.", "Er will nach Hause fahren.", "Ihr sollt still sein."), null, listOf(Look.DOUBLE_INF, Look.INFINITIVE, Look.WHO)),
        "nebensatz" to Rule("hint.de.ruleNebensatz", "hintSay.de.ruleNebensatz", listOf("…, weil ich müde bin.", "…, dass er kommt.", "Wenn es schneit, bleibe ich hier.", "…, ob du Zeit hast.")),
        "ordinalzahlen" to Rule("hint.de.ruleOrdinalzahlen", "hintSay.de.ruleOrdinalzahlen", listOf("der erste Mai · am ersten Mai", "der dritte · der siebte", "der zwanzigste Juni", "vor zwei Tagen")),
        "partizip-zwei" to Rule("hint.de.rulePartizipZwei", "hintSay.de.rulePartizipZwei", listOf("kaufen · gekauft", "trinken · getrunken", "aufstehen · aufgestanden", "verstehen · verstanden", "telefonieren · telefoniert"), null, listOf(Look.PARTICIPLE)),
        "perfekt" to Rule("hint.de.rulePerfekt", "hintSay.de.rulePerfekt", listOf("Ich habe gelernt.", "Sie hat Kaffee getrunken.", "Wir sind nach Graz gefahren.", "Er ist früh aufgestanden.", "Du bist gewachsen."), null, listOf(Look.BY_PARTICIPLE, Look.PARTICIPLE, Look.WHO)),
        "praepositionen-akkusativ" to Rule("hint.de.rulePraepAkk", "hintSay.de.rulePraepAkk", listOf("für meinen Vater", "ohne einen Mantel", "gegen ihn", "durch den Park", "um den Tisch"), null, listOf(Look.PREP)),
        "praepositionen-dativ" to Rule("hint.de.rulePraepDat", "hintSay.de.rulePraepDat", listOf("mit meinem Vater", "bei einer Freundin", "aus dem Haus", "nach dem Essen", "seit einem Monat", "von ihm"), null, listOf(Look.PREP)),
        "praeteritum-kurz" to Rule("hint.de.rulePraeteritumKurz", "hintSay.de.rulePraeteritumKurz", listOf("ich war · wir waren", "du hattest · sie hatten", "ich konnte · er musste", "es gab"), null, listOf(Look.WHO)),
        "pronomen-dativ" to Rule("hint.de.rulePronomenDativ", "hintSay.de.rulePronomenDativ", listOf("Das gehört uns.", "Ich danke euch.", "Es geht ihr gut.", "Das gefällt ihm."), null, listOf(Look.PREP, Look.VERB_CASE, Look.OBJECT)),
        "reflexive-verben" to Rule("hint.de.ruleReflexiveVerben", "hintSay.de.ruleReflexiveVerben", listOf("Ich wasche mich.", "Ich wasche mir die Hände.", "Er freut sich.", "Wir haben uns gefreut."), null, listOf(Look.TWO_OBJECTS, Look.VERB_CASE, Look.SELF)),
        "stehen-stellen" to Rule("hint.de.ruleStehenStellen", "hintSay.de.ruleStehenStellen", listOf("Das Buch liegt auf dem Regal.", "Ich lege das Buch auf das Regal.", "Der Mantel hängt im Kasten.", "Ich setze mich auf die Bank."), "Wo? Wohin?"),
        "steigerung" to Rule("hint.de.ruleSteigerung", "hintSay.de.ruleSteigerung", listOf("klein · kleiner · am kleinsten", "alt · älter · am ältesten", "gut · besser · am besten", "so groß wie ich")),
        "trennbare-verben" to Rule("hint.de.ruleTrennbareVerben", "hintSay.de.ruleTrennbareVerben", listOf("Ich stehe früh auf.", "Der Film fängt um acht an.", "Wir kaufen heute ein.", "Ich bin früh aufgestanden."), null, listOf(Look.PARTICIPLE, Look.PARTICLE)),
        "verben-praepositionen" to Rule("hint.de.ruleVerbenPraep", "hintSay.de.ruleVerbenPraep", listOf("Ich warte auf den Bus.", "Sie denkt an ihren Bruder.", "Wir träumen von einem Haus.", "Ich spreche mit meinem Vater."), null, listOf(Look.CHUNK, Look.PREP)),
        "wechselpraepositionen" to Rule("hint.de.ruleWechsel", "hintSay.de.ruleWechsel", listOf("Ich gehe in den Park. · Ich bin im Park.", "Ich lege es auf den Tisch. · Es liegt auf dem Tisch.", "Wir fahren an das Meer. · Wir sind am Meer.", "Stell es neben das Bett! · Es steht neben dem Bett."), "Wo? Wohin?", listOf(Look.PREP)),
        // B1
        "adjektiv-tabelle" to Rule("hint.de.ruleAdjektivTabelle", "hintSay.de.ruleAdjektivTabelle", listOf("der alte Mann · ein alter Mann", "das alte Haus · ein altes Haus", "den alten Mann", "mit altem Wein"), null, listOf(Look.ADJ)),
        "doppelkonnektoren" to Rule("hint.de.ruleDoppelkonnektoren", "hintSay.de.ruleDoppelkonnektoren", listOf("nicht nur Tee, sondern auch Kaffee", "sowohl Tee als auch Kaffee", "weder Tee noch Kaffee", "entweder Tee oder Kaffee"), null, listOf(Look.PAIR)),
        "genitiv" to Rule("hint.de.ruleGenitiv", "hintSay.de.ruleGenitiv", listOf("das Auto meines Vaters", "die Tasche meiner Mutter", "das Ende des Films", "Annas Bruder"), "Wessen?", listOf(Look.WEAK, Look.WHOSE)),
        "indefinitpronomen" to Rule("hint.de.ruleIndefinitpronomen", "hintSay.de.ruleIndefinitpronomen", listOf("Jemand ruft.", "Niemand weiß es.", "Ich sehe etwas.", "Hast du einen? · Ja, ich habe einen."), null, listOf(Look.WHO, Look.VERB_CASE, Look.OBJECT)),
        "infinitiv-zu" to Rule("hint.de.ruleInfinitivZu", "hintSay.de.ruleInfinitivZu", listOf("Ich versuche zu schlafen.", "Es ist schön, dich zu sehen.", "Ich lerne, um zu reisen.", "Er geht, ohne zu grüßen."), null, listOf(Look.WHAT_FOR)),
        "konjunktiv-zwei" to Rule("hint.de.ruleKonjunktivZwei", "hintSay.de.ruleKonjunktivZwei", listOf("Ich ginge gern mit.", "An deiner Stelle bliebe ich hier.", "Wenn ich reich wär, kaufte ich ein Schiff.", "Würdest du mir das Salz reichen?", "Es wäre mir lieber."), null, listOf(Look.POLITE, Look.IMAGINED, Look.INFINITIVE)),
        "obwohl-trotzdem" to Rule("hint.de.ruleObwohlTrotzdem", "hintSay.de.ruleObwohlTrotzdem", listOf("…, obwohl ich müde bin.", "Ich bin müde, trotzdem lerne ich.", "Obwohl es schneit, fahren wir.", "Es schneit, trotzdem fahren wir."), null, listOf(Look.VERB_NEXT)),
        "passiv" to Rule("hint.de.rulePassiv", "hintSay.de.rulePassiv", listOf("Die Tür wird geöffnet.", "Die Briefe werden geschrieben.", "Das Haus wurde verkauft.", "Es wurde von meinem Onkel gebaut."), null, listOf(Look.PASSIVE, Look.INFINITIVE)),
        "plusquamperfekt" to Rule("hint.de.rulePlusquamperfekt", "hintSay.de.rulePlusquamperfekt", listOf("Sie hatte schon gegessen.", "Der Zug war schon abgefahren.", "Nachdem ich gelernt hatte, schlief ich."), null, listOf(Look.BY_PARTICIPLE)),
        "praepositionen-genitiv" to Rule("hint.de.rulePraepGen", "hintSay.de.rulePraepGen", listOf("wegen des Sturms", "trotz der Kälte", "während des Unterrichts", "innerhalb einer Stunde", "statt eines Briefes"), null, listOf(Look.PREP)),
        "praeteritum" to Rule("hint.de.rulePraeteritum", "hintSay.de.rulePraeteritum", listOf("sagte · arbeitete", "ging · kam · sah", "dachte · brachte", "wir gingen · ihr gingt"), null, listOf(Look.WHO, Look.PAST)),
        "relativsatz" to Rule("hint.de.ruleRelativsatz", "hintSay.de.ruleRelativsatz", listOf("die Frau, die hier wohnt", "der Film, den ich sehe", "das Kind, dem ich helfe", "die Leute, denen ich schreibe", "die Stadt, in der ich lebe"), null, listOf(Look.REL)),
        "temporalsaetze" to Rule("hint.de.ruleTemporalsaetze", "hintSay.de.ruleTemporalsaetze", listOf("Als ich klein war, …", "Immer wenn es schneit, …", "Während ich koche, …", "Bevor ich gehe, …", "Bis du kommst, …"), "Wann?", listOf(Look.WHEN, Look.VERB_NEXT)),
        "werden-futur" to Rule("hint.de.ruleWerdenFutur", "hintSay.de.ruleWerdenFutur", listOf("Ich werde dich anrufen.", "Es wird dunkel.", "Sie wird Ärztin.", "Er wird wohl krank sein."), null, listOf(Look.BY_PARTICIPLE, Look.INFINITIVE, Look.WHO)),
        "wo-da-woerter" to Rule("hint.de.ruleWoDaWoerter", "hintSay.de.ruleWoDaWoerter", listOf("Worauf wartest du? · Ich warte darauf.", "Wofür? · Dafür.", "Womit? · Damit.", "Auf wen wartest du? · Auf ihn."), null, listOf(Look.CHUNK)),
        // B2
        "adjektiv-nomen" to Rule("hint.de.ruleAdjektivNomen", "hintSay.de.ruleAdjektivNomen", listOf("der Verwandte · ein Verwandter", "die Verwandten · zwei Verwandte", "etwas Neues · nichts Neues", "alles Liebe"), null, listOf(Look.ADJ_NOUN)),
        "adjektive-praepositionen" to Rule("hint.de.ruleAdjektivePraep", "hintSay.de.ruleAdjektivePraep", listOf("stolz auf seine Tochter", "froh über die Nachricht", "zufrieden mit dem Ergebnis", "abhängig vom Wetter", "Worauf bist du stolz?"), null, listOf(Look.CHUNK, Look.PREP)),
        "als-ob" to Rule("hint.de.ruleAlsOb", "hintSay.de.ruleAlsOb", listOf("Er tut, als ob er schliefe.", "Sie lacht, als wäre nichts passiert.", "Wenn er doch käme!", "Hätte ich nur Zeit!")),
        "formeller-brief" to Rule("hint.de.ruleFormellerBrief", "hintSay.de.ruleFormellerBrief", listOf("Sehr geehrte Frau Magister Huber,", "Könnten Sie mir bitte mitteilen, …", "Ich wäre Ihnen dankbar, wenn …", "Mit freundlichen Grüßen"), null, listOf(Look.CHUNK)),
        "indem-sodass" to Rule("hint.de.ruleIndemSodass", "hintSay.de.ruleIndemSodass", listOf("Ich lerne, indem ich lese.", "Es schneit, sodass wir bleiben.", "Es ist so kalt, dass wir frieren.", "Er ging, ohne dass ich es sah."), "Wie?"),
        "indirekte-rede" to Rule("hint.de.ruleIndirekteRede", "hintSay.de.ruleIndirekteRede", listOf("Er sagt, er sei krank.", "Sie sagt, sie habe keine Zeit.", "Er sagt, er komme später.", "Sie sagen, sie hätten keine Zeit.")),
        "konjunktiv-vergangenheit" to Rule("hint.de.ruleKonjunktivVergangenheit", "hintSay.de.ruleKonjunktivVergangenheit", listOf("Ich hätte geholfen.", "Ich wäre gekommen.", "Hätte ich das nur gewusst!", "Wir hätten bleiben sollen.", "Wäre sie nur da gewesen!"), null, listOf(Look.DOUBLE_INF, Look.BY_PARTICIPLE, Look.PAST_IMAGINED, Look.INFINITIVE)),
        "modalverben-vermutung" to Rule("hint.de.ruleModalVermutung", "hintSay.de.ruleModalVermutung", listOf("Das muss der Briefträger sein.", "Er dürfte schon zu Hause sein.", "Das könnte stimmen.", "Sie soll sehr reich sein."), null, listOf(Look.INFINITIVE)),
        "n-deklination" to Rule("hint.de.ruleNDeklination", "hintSay.de.ruleNDeklination", listOf("der Student · den Studenten", "der Löwe · dem Löwen", "der Herr · dem Herrn", "der Mensch · des Menschen"), null, listOf(Look.WEAK)),
        "nomen-verb" to Rule("hint.de.ruleNomenVerb", "hintSay.de.ruleNomenVerb", listOf("eine Frage stellen", "Hilfe leisten", "eine Rolle spielen", "in Frage kommen"), null, listOf(Look.NOUN_VERB)),
        "nominalisierung" to Rule("hint.de.ruleNominalisierung", "hintSay.de.ruleNominalisierung", listOf("schwimmen · das Schwimmen", "beim Schwimmen", "die Wohnung · die Freiheit", "bei meiner Ankunft"), null, listOf(Look.VERB_NOUN)),
        "oesterreichisch" to Rule("hint.de.ruleOesterreichisch", "hintSay.de.ruleOesterreichisch", listOf("heuer · im Jänner", "Paradeiser · Erdäpfel · Topfen", "ich bin gesessen", "das geht sich aus")),
        "partizip-adjektiv" to Rule("hint.de.rulePartizipAdjektiv", "hintSay.de.rulePartizipAdjektiv", listOf("das weinende Kind", "der bellende Hund", "der gekochte Fisch", "das verkaufte Haus"), null, listOf(Look.PART_ADJ, Look.ADJ)),
        "partizipialattribut" to Rule("hint.de.rulePartizipialattribut", "hintSay.de.rulePartizipialattribut", listOf("der am Fenster sitzende Mann", "das gestern gekaufte Buch", "die im Wald verlorene Tasche"), null, listOf(Look.ADJ)),
        "passiv-ersatz" to Rule("hint.de.rulePassivErsatz", "hintSay.de.rulePassivErsatz", listOf("Das lässt sich reparieren.", "Das ist zu schaffen.", "Das ist machbar.", "Das kann man reparieren."), null, listOf(Look.INFINITIVE)),
        "passiv-zeiten" to Rule("hint.de.rulePassivZeiten", "hintSay.de.rulePassivZeiten", listOf("Das Auto ist verkauft worden.", "Es war schon repariert worden.", "Das muss getan werden.", "Die Tür müsste geschlossen werden."), null, listOf(Look.WORDEN, Look.INFINITIVE)),
        "satzbau" to Rule("hint.de.ruleSatzbau", "hintSay.de.ruleSatzbau", listOf("Ich gebe es ihm.", "Ich gebe ihm das Buch.", "Ich gebe dem Kind das Buch.", "Er kommt heute wegen des Sturms nicht.")),
        "zwar-aber" to Rule("hint.de.ruleZwarAber", "hintSay.de.ruleZwarAber", listOf("Zwar regnet es, aber wir gehen.", "Es regnet zwar, aber wir gehen.", "Allerdings ist es kalt.", "Auch wenn es regnet, gehen wir."), null, listOf(Look.PAIR)),
    )

    /** The 🔊's sentence of a card's rule [key] ("hint.de.ruleDativ"); null for one of another table. */
    fun spoken(key: String): String? = SPOKEN[key]

    private val SPOKEN: Map<String, String> = RULES.values.associate { it.text to it.said }

    /**
     * The hint of a German turn: its first page this table has ([TurnHint.pages], those the book has), with that page's
     * question, rule and models (all of them: the caller keeps those that show no form of the turn), and what decides the
     * form in [sentence] at its gap [slot] (the turn's right choice; null: a turn that isn't one word's form); [names]: the
     * words of the cast's names ([TurnHints.names]). A turn of no one word whose right choice is [right] and another of
     * [texts] the same words in another order is about where the verb goes ([order]). Null when no page of the turn is here.
     */
    fun of(base: TurnHint, sentence: String?, slot: Int?, names: Set<String> = emptySet(), right: String? = null, texts: List<String> = emptyList()): Pair<String, TurnHint>? {
        val id = base.pages.firstOrNull { it in RULES } ?: return null
        val rule = RULES.getValue(id)
        val trigger = when {
            sentence != null && slot != null -> triggerOf(rule.looks, sentence, slot, base, names)
            right != null -> texts.firstNotNullOfOrNull { order(right, it) }
            else -> null
        }
        return id to base.copy(question = rule.question, rule = rule.text, trigger = trigger, models = rule.models)
    }

    /** What decides the form of the word at [slot] of [sentence], by the first of [looks] that finds it; null for none. */
    fun triggerOf(looks: List<Look>, sentence: String, slot: Int, h: TurnHint, names: Set<String> = emptySet()): Trigger? {
        val s = Said(sentence, names)
        if (slot !in s.words.indices) return null
        return looks.firstNotNullOfOrNull { find(it, s, slot, h) }
    }

    /**
     * Where the verb goes, at a turn of two sentences of the same words in another order ([right], [wrong]), at the first
     * word they differ in: the verb of a clause opened by a joining word (weil, damit, and after a comma wie, der) goes last;
     * a helper's other verb goes last; und, aber, oder take no place; else what comes first (Morgen, Im Winter) or the clause
     * before it (Obwohl es schneit,) has the verb next. Null when they aren't the same words, or what moved is no verb (es
     * ihm, nicht: the order after the verb).
     */
    fun order(right: String, wrong: String): Trigger? {
        val a = Forms.words(right)
        val b = Forms.words(wrong)
        if (a == b || a.sorted() != b.sorted()) return null
        val d = a.indices.first { a[it] != b[it] }
        val s = Said(right, emptySet())
        val start = s.start(d)
        val first = a[start]
        val last = a[s.end(d)]
        // the word the wrong sentence moved up stands last in the right one
        val movedUp = b[d] == last
        val joins = (first in JOINING && (movedUp || first !in T.prepositions)) || (movedUp && !s.opens(start) && (first in WH || first in T.relative))
        if (joins) return asks(s, start, start, "hint.de.verbLast", "hintSay.after")
        if (movedUp && (participle(last) || infinitive(last))) (start..d).firstOrNull { a[it] in HELPERS }?.let { return asks(s, it, it, "hint.de.endVerb", "hintSay.with") }
        if (first in NO_PLACE) return asks(s, start, start, "hint.de.noPlace", "hintSay.after")
        if (a[d] in FUNCTION - HELPERS) return null
        if (start < d) return asks(s, start, d - 1, "hint.de.verbSecond", "hintSay.de.first")
        // the clause before it comes first: the verb at the comma
        val before = (start - 1).takeIf { it >= 0 && a[s.start(it)] in JOINING } ?: return null
        return asks(s, s.start(before), before, "hint.de.verbSecond", "hintSay.de.first")
    }

    // --- German's trigger table -------------------------------------------------------------------------------------

    /** One way a preposition takes a case: [case] ("acc", "dat", "gen"), [sense] ("motion", "rest" …), [spoken]: what people say beside the written form (wegen dem). */
    data class Take(val case: String, val sense: String, val spoken: Boolean = false)

    /** A chunk's preposition and its case (warten auf + acc); [particle]: of a verb that is one only with it (aufpassen). */
    data class Chunk(val lemma: String, val preposition: String, val case: String, val particle: String? = null)

    /** A verb's governed [cases] ("dat", or "dat" and "acc"); [particle]: of a separable one, only with it at its clause's end. */
    data class Governed(val cases: List<String>, val particle: String? = null)

    /** The table as the chip reads it ([parse]). */
    class Table(
        val prepositions: Map<String, List<Take>>,
        /** A preposition with its article as one word (im, zum, ins): its preposition and case. */
        val fused: Map<String, Pair<String, String>>,
        /** A verb's forms, each with what it governs. */
        val verbs: Map<String, List<Governed>>,
        /** The verbs', adjectives' and nouns' forms that have a preposition of their own. */
        val chunks: Map<String, List<Chunk>>,
        val motion: Set<String>,
        val rest: Set<String>,
        val copulas: Set<String>,
        /** A determiner's form, its slots ("den": acc.m, dat.pl). */
        val determiners: Map<String, Set<String>>,
        /** Which determiners' forms end in nothing: ein, kein, mein … (the adjective then shows it). */
        val bare: Set<String>,
        /** A personal pronoun's form, its cases. */
        val pronouns: Map<String, Set<String>>,
        /** The relative pronoun's form, its cases. */
        val relative: Map<String, Set<String>>,
        /** The nouns of the n-Deklination, lower case: the lemma and its other forms. */
        val weak: Set<String>,
        /** The nouns of time, lower case. */
        val times: Set<String>,
    ) {
        companion object {
            val EMPTY = Table(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptySet(), emptySet(), emptySet(), emptyMap(), emptySet(), emptyMap(), emptyMap(), emptySet(), emptySet())
        }
    }

    /** The bundled table of German; empty when it can't be read (the chip says the page's rule alone then). */
    val table: Table by lazy {
        GermanHints::class.java.getResourceAsStream("/grammar/de/data/case-triggers.json")?.use { runCatching { parse(it.readBytes().decodeToString()) }.getOrNull() } ?: Table.EMPTY
    }

    private val T: Table get() = table

    /** [raw] (lani.case-triggers/v0, language de) as the chip reads it. */
    fun parse(raw: String): Table {
        val o = Json.parseToJsonElement(raw).jsonObject
        fun str(e: JsonObject, k: String): String? = e[k]?.jsonPrimitive?.contentOrNull
        fun strings(e: Any?): List<String> = (e as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        fun objects(k: String): List<JsonObject> = o[k]?.jsonArray.orEmpty().map { it.jsonObject }
        fun forms(k: String): Set<String> = objects(k).flatMap { strings(it["forms"]) }.toSet()
        val preps = LinkedHashMap<String, List<Take>>()
        for (p in objects("prepositions")) {
            val takes = p["takes"]?.jsonArray.orEmpty().map { t ->
                val to = t.jsonObject
                Take(str(to, "case").orEmpty(), str(to, "sense") ?: "other", to["spoken"]?.jsonPrimitive?.booleanOrNull == true)
            }
            for (w in strings(p["words"])) preps[w] = takes
        }
        val verbs = HashMap<String, MutableList<Governed>>()
        for (v in objects("verbs")) {
            val cases = strings(v["takes"])
            val particle = str(v, "particle")
            for (f in strings(v["forms"])) verbs.getOrPut(f) { ArrayList() } += Governed(cases, particle)
            for (f in strings(v["joined"])) verbs.getOrPut(f) { ArrayList() } += Governed(cases)
        }
        val chunks = HashMap<String, MutableList<Chunk>>()
        for (c in objects("chunks")) {
            val chunk = Chunk(str(c, "lemma").orEmpty(), str(c, "preposition").orEmpty(), str(c, "case").orEmpty(), str(c, "particle"))
            for (f in strings(c["forms"])) chunks.getOrPut(f) { ArrayList() } += chunk
            for (f in strings(c["joined"])) chunks.getOrPut(f) { ArrayList() } += chunk.copy(particle = null)
        }
        val dets = HashMap<String, MutableSet<String>>()
        val bare = HashSet<String>()
        for (d in objects("determiners")) {
            val lemma = str(d, "lemma")
            for ((slot, f) in d["forms"]?.jsonObject.orEmpty()) {
                val form = f.jsonPrimitive.content
                dets.getOrPut(form) { HashSet() } += slot
                if (form == lemma && str(d, "kind") != "definite" && str(d, "kind") != "der") bare += form
            }
        }
        val pronouns = HashMap<String, MutableSet<String>>()
        for (p in objects("pronouns")) {
            for ((case, fs) in p["forms"]?.jsonObject.orEmpty()) for (f in strings(fs)) pronouns.getOrPut(f) { HashSet() } += case
        }
        val weak = objects("n_declension").flatMap { n -> listOfNotNull(str(n, "lemma"), str(n, "oblique"), str(n, "genitive"), str(n, "accusative"), str(n, "plural")) }.map { it.lowercase() }.toSet()
        return Table(
            prepositions = preps,
            fused = objects("contractions").associate { c -> str(c, "form").orEmpty() to (str(c, "preposition").orEmpty() to str(c, "case").orEmpty()) },
            verbs = verbs,
            chunks = chunks,
            motion = forms("motion"),
            rest = forms("rest"),
            copulas = forms("copulas"),
            determiners = dets,
            bare = bare,
            pronouns = pronouns,
            relative = o["relative"]?.jsonObject?.get("forms")?.jsonObject.orEmpty().entries.groupBy({ it.value.jsonPrimitive.content }, { it.key.substringBefore('.') }).mapValues { it.value.toSet() },
            weak = weak,
            times = strings(o["time"]?.jsonObject?.get("nouns")).map { it.lowercase() }.toSet(),
        )
    }

    // --- a sentence -------------------------------------------------------------------------------------------------

    /** A sentence's words ([Forms.spans]), lower case, which clause each is in ([CaseTriggers.clauses]), and the cast's [names]. */
    private class Said(val text: String, val names: Set<String>) {
        val spans = Forms.spans(text)
        val words = spans.map { it.value.lowercase().replace('’', '\'') }
        val clause = CaseTriggers.clauses(text)

        /** The sentence's own text of the words [from] to [to] ("die Bienen"). */
        fun text(from: Int, to: Int): String = text.substring(spans[from].range.first, spans[to].range.last + 1)

        fun same(a: Int, b: Int): Boolean = a in words.indices && b in words.indices && clause[a] == clause[b]

        /** The first word of [i]'s clause. */
        fun start(i: Int): Int = words.indices.first { clause[it] == clause[i] }

        /** The last word of [i]'s clause. */
        fun end(i: Int): Int = words.indices.last { clause[it] == clause[i] }

        /** Whether the sentence the word at [i] is in ends with a question mark. */
        fun asks(i: Int): Boolean = text.substring(spans[i].range.last + 1).firstOrNull { it in ".!?…" } == '?'

        fun capital(k: Int): Boolean = spans[k].value.first().isUpperCase()

        /** What stands between the words [a] and [a] + 1. */
        fun after(a: Int): String = if (a + 1 < words.size) text.substring(spans[a].range.last + 1, spans[a + 1].range.first) else text.substring(spans[a].range.last + 1)

        /** Whether the word at [k] starts its sentence: its capital says nothing then. */
        fun opens(k: Int): Boolean = k == 0 || after(k - 1).any { it in ".!?…:" }
    }

    // --- the word lists ---------------------------------------------------------------------------------------------

    private fun set(s: String): Set<String> = s.split(" ").filter { it.isNotEmpty() }.toSet()

    /** Who can do it: the pronouns of the subject, and the words that stand for someone or something. */
    private val SUBJECTS = set("ich du er sie es wir ihr man wer das dies alles nichts etwas jemand niemand jeder keiner einer alle beide manche viele")
    private val JOINS = set("und aber oder denn dass weil wenn ob als sondern bis obwohl damit nachdem bevor während")

    /** The words that send the verb last. */
    private val JOINING = set("weil dass wenn ob damit bis bevor nachdem obwohl als während sodass indem seit falls ehe sobald solange")

    /** The words that take no place in the sentence. */
    private val NO_PLACE = set("und aber oder denn sondern")
    private val WH = set("wo wohin woher was wer wen wem wessen wie warum wann welche welcher welches")

    /** What may stand in a sentence's middle: not a noun, not what a verb acts on. */
    private val MIDDLE = set(
        "nicht nie noch schon auch nur sehr ganz so wieder immer oft gern gerne hier dort da dann jetzt heute morgen gestern bald doch ja nein denn wohl " +
            "mal einmal erst mehr lieber bestimmt sicher vielleicht wirklich einfach aber also eben halt eh lei gar kaum fast zu aus weg oben unten draußen " +
            "drinnen drüben links rechts hinten vorne vorn heuer damals früher später sofort gleich zuerst danach dabei dafür daran darauf darin damit davon",
    )

    /** Words that stand in front of a noun without being its determiner. */
    private val SOFT = set("sehr ganz so zu nur noch auch")

    /** What stands before a noun beside the table's determiners: a number, a word of quantity. */
    private val QUANTITY = set("viele vielen mehrere einige wenige manche paar beide")
    private val NUMBERS = set("zwei drei vier fünf sechs sieben acht neun zehn elf zwölf zwanzig dreißig vierzig fünfzig sechzig siebzig achtzig neunzig hundert tausend")
    private val NUMBER_WORD = Regex("""\p{L}*(zehn|zig|ßig|hundert|tausend)""")
    private val DIGITS = Regex("""\d+""")

    /** Measures that stay as they are after a number (zwei Kilo, drei Meter). */
    private val MEASURES = set("kilo gramm liter meter euro prozent stück mal")

    private val SEIN = set("bin bist ist sind seid war warst waren wart gewesen sein wäre wärst wären wärt sei")
    private val HABEN = set("habe hast hat haben habt hatte hattest hatten hattet gehabt hätte hättest hätten hättet")
    private val MODALS = set(
        "kann kannst können könnt konnte konntest konnten konntet könnte könntest könnten könntet darf darfst dürfen dürft durfte durftest durften durftet dürfte " +
            "dürftest dürften dürftet muss musst müssen müsst musste musstest mussten musstet müsste müsstest müssten müsstet soll sollst sollen sollt sollte solltest " +
            "sollten solltet will willst wollen wollt wollte wolltest wollten wolltet möchte möchtest möchten möchtet mag magst mögen mögt lässt lässt lassen ließ",
    )
    private val WERDEN = set("werde wirst wird werden werdet wurde wurdest wurden wurdet würde würdest würden würdet")
    private val HELPERS = SEIN + HABEN + MODALS + WERDEN

    /** The verbs that go with a modal's or werden's infinitive at a clause's end. */
    private val BEFORE_INFINITIVE = MODALS + WERDEN

    /** The forms of one verb by who does it. */
    private val PERSON = listOf(
        set("bin bist ist sind seid"), set("war warst waren wart"), set("wäre wärst wären wärt"), set("habe hast hat haben habt"), set("hatte hattest hatten hattet"),
        set("hätte hättest hätten hättet"), set("werde wirst wird werden werdet"), set("wurde wurdest wurden wurdet"), set("würde würdest würden würdet"),
        set("kann kannst können könnt"), set("darf darfst dürfen dürft"), set("muss musst müssen müsst"), set("soll sollst sollen sollt"), set("will willst wollen wollt"),
        set("mag magst mögen mögt"), set("weiß weißt wissen wisst"), set("möchte möchtest möchten möchtet"),
    )

    /** The stems a verb's form may have: its ending off, the vowel it changes in du and er set back (fährst, liest, nimmt, frisst). */
    private fun stems(w: String): Set<String> = listOf("est", "en", "et", "st", "e", "n", "t", "").filter { w.endsWith(it) }.map { end ->
        w.removeSuffix(end).replace('ä', 'a').replace('ö', 'o').replace('ü', 'u').replace("ie", "e").replace('i', 'e').replace("h", "").replace("ß", "s").replace(Regex("(.)\\1"), "$1")
    }.filter { it.length >= 2 }.toSet()

    private fun personPair(a: String, b: String): Boolean {
        if (a == b) return false
        if (PERSON.any { a in it && b in it }) return true
        return stems(a).any { it in stems(b) }
    }

    private val PARTICLES = listOf(
        "zusammen", "zurück", "hinauf", "hinunter", "hinein", "hinaus", "hinüber", "herauf", "herunter", "herein", "heraus", "herüber", "vorbei", "weiter", "wieder", "nach", "heim", "fest", "hoch",
        "mit", "weg", "auf", "aus", "ein", "los", "vor", "zu", "ab", "an", "bei", "um", "her", "hin", "durch", "über",
    )

    /** Whether [w] is a participle by its shape: ge…t, ge…en, a particle before them, be-, ver-, er-, ent-, zer-, -iert. */
    private fun participle(w: String): Boolean {
        if (w in set("gewesen geworden worden")) return true
        fun made(x: String) = x.length >= 5 && x.startsWith("ge") && (x.endsWith("t") || x.endsWith("en"))
        if (made(w)) return true
        if (w.length >= 6 && Regex("^(be|ver|er|ent|zer|emp|über|unter)").containsMatchIn(w) && (w.endsWith("t") || w.endsWith("en"))) return true
        if (w.length >= 6 && w.endsWith("iert")) return true
        return PARTICLES.any { p -> w.startsWith(p) && made(w.removePrefix(p)) }
    }

    /** Whether [w] may be an infinitive by its shape. */
    private fun infinitive(w: String): Boolean = w == "sein" || w == "tun" || (w.length >= 3 && (w.endsWith("en") || w.endsWith("ln") || w.endsWith("rn")))

    private val FUNCTION: Set<String> by lazy {
        T.prepositions.keys + T.fused.keys + T.determiners.keys + T.pronouns.keys + MIDDLE + JOINS + JOINING + WH + NUMBERS + QUANTITY + HELPERS + set("nichts etwas alles jemand niemand man")
    }

    /** Whether the word at [k] is a determiner: the table's, a number, a word of quantity. */
    private fun determiner(s: Said, k: Int): Boolean = s.words[k] in T.determiners || number(s.words[k]) || s.words[k] in QUANTITY

    private fun number(w: String): Boolean = w in NUMBERS || NUMBER_WORD.matches(w) || DIGITS.matches(w)

    /** Whether the word at [k] may be an adjective before a noun: small, no function word, an adjective's ending. */
    private fun adjective(s: Said, k: Int): Boolean {
        val w = s.words[k]
        return (!s.capital(k) || s.opens(k)) && w !in FUNCTION && Regex("(e|en|er|es|em)$").containsMatchIn(w)
    }

    /** Whether the word at [k] is a noun or a name: written with a capital, not at its sentence's start unless after a determiner; not Sie, Ihnen. */
    private fun noun(s: Said, k: Int): Boolean {
        if (k !in s.words.indices || !s.capital(k)) return false
        val w = s.words[k]
        if (w in T.pronouns || w in T.determiners || w in MIDDLE || w in WH || w in JOINS) return false
        return !s.opens(k) || (k > 0 && s.same(k - 1, k) && determiner(s, k - 1)) || w in s.names.map { it.lowercase() }
    }

    /** The noun of the phrase from [n] on: past adjectives and a word that softens them (ein ganz kleiner Hund). */
    private fun nounFrom(s: Said, n: Int): Int? {
        var k = n
        while (k < s.words.size && s.same(k, n) && k - n <= 3) {
            if (noun(s, k) || (k > 0 && s.capital(k) && s.same(k - 1, k) && determiner(s, k - 1) && s.words[k] !in T.pronouns)) return k
            if (!adjective(s, k) && s.words[k] !in SOFT && !number(s.words[k])) return null
            k++
        }
        return null
    }

    /** The first word of the noun phrase ending at the noun [k]: its determiner (past adjectives), a title, or itself at its clause's start or after a joining word. */
    private fun phraseEnding(s: Said, k: Int): Int? {
        val w = s.words
        if (k !in w.indices || !s.capital(k) || w[k] in FUNCTION) return null
        var d = k - 1
        while (d >= 0 && s.same(d, k) && (adjective(s, d) || w[d] in SOFT)) d--
        if (d >= 0 && s.same(d, k) && determiner(s, d)) return if (d > 0 && s.same(d - 1, k) && w[d - 1] == "wie") d - 1 else d
        if (d >= 0 && s.same(d, k) && s.capital(d) && w[d] !in FUNCTION) return d.takeIf { it == s.start(k) || w[it - 1] in JOINS }
        val from = d + 1
        return from.takeIf { it == s.start(k) || (d >= 0 && s.same(d, k) && w[d] in JOINS) }
    }

    /** The last word of the phrase that starts at [n]: a determiner and its noun, or a noun alone; it may be a subject (no den, dem, einen). */
    private fun subjectFrom(s: Said, n: Int): Pair<Int, Int>? {
        val w = s.words
        if (n !in w.indices) return null
        if (w[n] in set("ich du er sie es wir ihr man")) return n to n
        if (w[n] in T.determiners) {
            if (T.determiners.getValue(w[n]).none { it.startsWith("nom") }) return null
            return nounFrom(s, n + 1)?.let { n to it } ?: (n to n).takeIf { w[n] == "das" }
        }
        if (number(w[n]) || w[n] in QUANTITY) return nounFrom(s, n + 1)?.let { n to it }
        if (adjective(s, n)) return nounFrom(s, n)?.let { n to it }
        if (s.capital(n) && w[n] !in FUNCTION) return if (s.same(n + 1, n) && s.capital(n + 1) && w[n + 1] !in FUNCTION) n to n + 1 else n to n
        return null
    }

    /** The cases of a determiner's or a pronoun's form. */
    private fun casesOf(w: String): Set<String> = T.determiners[w].orEmpty().map { it.substringBefore('.') }.toSet() + T.pronouns[w].orEmpty() + T.relative[w].orEmpty()

    /** A word with its umlauts set back. */
    private fun plain(w: String) = w.replace('ä', 'a').replace('ö', 'o').replace('ü', 'u')

    /** Two forms of one word, or a determiner and a pronoun of a turn: the sentence's case can decide between them. */
    private fun related(a: String, b: String): Boolean = (casesOf(a).isNotEmpty() && casesOf(b).isNotEmpty()) || plain(a).commonPrefixWith(plain(b)).length >= 3

    /** One noun's forms: the plural and its singular (Köpfe, Kopf; Mütter, Mutter). */
    private fun oneNoun(gap: String, forms: Set<String>): Boolean = forms.any { it != gap && plain(it).commonPrefixWith(plain(gap)).length >= 3 }

    // --- what decides the form --------------------------------------------------------------------------------------

    private fun asks(s: Said, from: Int, to: Int, line: String, said: String) = Trigger.Asks(s.text(from, to), to, line, said)

    private fun find(look: Look, s: Said, i: Int, h: TurnHint): Trigger? {
        val gap = s.words[i]
        val forms = h.forms.map { it.replace('’', '\'') }.toSet()
        return when (look) {
            Look.PREP -> prep(s, i, gap, forms)
            Look.CHUNK -> chunk(s, i, gap, forms)
            Look.VERB_CASE -> verbCase(s, i, gap, forms)
            Look.OBJECT -> objectOf(s, i, gap, forms)
            Look.COPULA -> copula(s, i, gap, forms)
            Look.BITTE -> bitte(s, i, gap)
            Look.SELF -> self(s, i, gap)
            Look.TWO_OBJECTS -> twoObjects(s, i, gap, forms)
            Look.GENDER -> gender(s, i, gap, forms)
            Look.ADJ -> if (s.capital(i) && !s.opens(i)) null else adjectiveEnding(s, i, gap, forms)
            Look.ADJ_NOUN -> adjectiveEnding(s, i, gap, forms)
            Look.WHOSE -> whose(s, i, gap, forms)
            Look.WEAK -> weak(s, i, gap, forms)
            Look.MANY -> many(s, i, gap, forms)
            Look.VERB_MANY -> verbMany(s, i, gap, forms)
            Look.ONE_OR_MORE -> oneOrMore(s, i, gap, forms)
            Look.THING -> thing(s, i, gap, forms)
            Look.COUNT -> count(s, i, gap, forms)
            Look.WHO -> who(s, i, gap, forms)
            Look.TO_WHOM -> addressed(s, i)?.let { (a, b) -> asks(s, a, b, "hint.de.toWhom", "hintSay.de.toWhom") }
            Look.INFINITIVE -> infinitiveAfter(s, i, gap, forms)
            Look.DOUBLE_INF -> doubleInfinitive(s, i, gap, forms)
            Look.PARTICIPLE -> participleAfter(s, i, gap, forms)
            Look.BY_PARTICIPLE -> byParticiple(s, i, gap, forms)
            Look.PAST_IMAGINED -> pastImagined(s, i, gap)
            Look.FEEL -> feel(s, i, gap, forms)
            Look.KEIN -> kein(s, i, gap, forms)
            Look.REL -> relative(s, i, gap, forms)
            Look.IMAGINED -> imagined(s, i, gap, forms)
            Look.POLITE -> polite(s, i, gap, forms)
            Look.PAIR -> pair(s, i, gap)
            Look.PARTICLE -> particle(s, i, gap, forms)
            Look.ASK_REST -> askRest(s, i, gap, forms)
            Look.WHEN -> whenClause(s, i, gap, forms)
            Look.VERB_NEXT -> verbNext(s, i, gap)
            Look.NOUN_VERB -> nounVerb(s, i, gap)
            Look.PART_ADJ -> partAdjective(s, i, gap, forms)
            Look.WHAT_FOR -> whatFor(s, i, gap)
            Look.PASSIVE -> passive(s, i, gap, forms)
            Look.WORDEN -> worden(s, i, gap, forms)
            Look.VERB_NOUN -> verbNoun(s, i, gap, forms)
            Look.PAST -> past(s, i)
        }
    }

    /** The word before the word at [i], past the words [skip] allows, in its clause; null at its start. */
    private fun before(s: Said, i: Int, skip: Set<String> = MIDDLE): Int? {
        var k = i - 1
        while (k >= 0 && s.same(k, i) && s.words[k] in skip) k--
        return k.takeIf { it >= 0 && s.same(it, i) }
    }

    /** The word after the word at [i], past the words [skip] allows, in its clause; null at its end. */
    private fun after(s: Said, i: Int, skip: Set<String> = MIDDLE): Int? {
        var n = i + 1
        while (n < s.words.size && s.same(n, i) && s.words[n] in skip) n++
        return n.takeIf { s.same(it, i) }
    }

    /** The word of [i]'s clause that [hit] finds, the nearest to [i] first. */
    private fun nearest(s: Said, i: Int, hit: (Int) -> Boolean): Int? = s.words.indices.filter { it != i && s.same(it, i) }.sortedBy { kotlin.math.abs(it - i) }.firstOrNull(hit)

    /** Whether the word at [k] can be a verb: written small, or first in its clause; a noun is no verb (das Leben). */
    private fun verbShaped(s: Said, k: Int): Boolean = !s.capital(k) || s.opens(k) || k == s.start(k)

    /**
     * The preposition that governs the word at [i]: right before it, or past its determiner and adjectives (mit dem
     * schweren Hammer); not für in was für ein (no preposition there). Its index, or null.
     */
    private fun governing(s: Said, i: Int): Int? {
        val w = s.words
        var k = i - 1
        // (mit dem Hammer und dem Meißel: the phrase before and)
        if (k > 0 && s.same(k, i) && w[k] in set("und oder") && noun(s, k - 1)) k -= 2
        while (k >= 0 && s.same(k, i) && (w[k] !in T.prepositions && w[k] !in T.fused) && (determiner(s, k) || adjective(s, k) || w[k] in SOFT)) k--
        if (k < 0 || !s.same(k, i)) return null
        if (w[k] !in T.prepositions && w[k] !in T.fused) return null
        if (w[k] == "für" && (0 until k).any { s.same(it, k) && w[it] == "was" }) return null
        return k
    }

    /** The verb, adjective or noun of [i]'s clause with [prep] for its own preposition: its index and the chunk's case. */
    private fun chunkOf(s: Said, i: Int, prep: String): Pair<Int, Chunk>? {
        val w = s.words
        for (k in w.indices.filter { it != i && s.same(it, i) }.sortedBy { kotlin.math.abs(it - i) }) {
            for (c in T.chunks[w[k]].orEmpty()) {
                if (c.preposition != prep) continue
                if (c.particle != null && w[s.end(i)] != c.particle) continue
                // a noun's chunk with a capital, a verb's small: Angst vor, wartet auf
                if (w[k] in set("angst lust interesse") != s.capital(k)) continue
                return k to c
            }
        }
        return null
    }

    /** What the verb of [i]'s clause says of a two-way preposition: motion, rest, or neither (hängt: both). */
    private fun motionOrRest(s: Said, i: Int): Pair<Int, Boolean>? {
        val k = nearest(s, i) { (s.words[it] in T.motion || s.words[it] in T.rest) && verbShaped(s, it) } ?: return null
        val w = s.words[k]
        if (w in T.motion && w in T.rest) return null
        // (a helper or a modal says nothing of where: the verb it goes with does)
        return k to (w in T.motion)
    }

    /**
     * A determiner, a pronoun, an adjective or a noun at the gap, and what governs its case: the preposition before it, with
     * the chunk it is part of (Angst vor), the case the table has (after a preposition with the dative people say: both, the
     * written one first), a two-way preposition by the verb (Wohin? Wo?) or by a noun of time (vor zehn Jahren), else both.
     */
    private fun prep(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val w = s.words
        // the fused word at the gap itself (ins Zelt, not im Zelt): what it is part of says which
        if (gap in T.fused) {
            val two = T.prepositions[T.fused.getValue(gap).first]?.size == 2 && forms.any { it in T.fused && T.fused.getValue(it).second != T.fused.getValue(gap).second }
            return if (two) twoWay(s, i, i) else null
        }
        if (forms.none { it != gap && related(gap, it) }) return null
        val k = governing(s, i) ?: return null
        T.fused[w[k]]?.let { (p, case) ->
            chunkOf(s, i, p)?.let { (at, c) -> return chunkLine(s, at, k, c.case) }
            return asks(s, k, k, afterCase(case), "hintSay.after")
        }
        chunkOf(s, i, w[k])?.let { (at, c) -> return chunkLine(s, at, k, c.case) }
        val takes = T.prepositions.getValue(w[k])
        val written = takes.filter { !it.spoken }
        return when {
            written.size == 1 && takes.size == 2 -> asks(s, k, k, "hint.de.afterGenSpoken", "hintSay.after")
            written.size == 1 -> asks(s, k, k, afterCase(written[0].case), "hintSay.after")
            written.any { it.sense == "motion" } -> twoWay(s, i, k)
            else -> asks(s, k, k, "hint.de.afterEither", "hintSay.after")
        }
    }

    private fun afterCase(case: String) = when (case) {
        "acc" -> "hint.de.afterAcc"
        "gen" -> "hint.de.afterGen"
        else -> "hint.de.afterDat"
    }

    /** A chunk's line: its word and preposition (Angst vor) when they stand side by side, else the preposition's own. */
    private fun chunkLine(s: Said, at: Int, k: Int, case: String): Trigger = if (at == k - 1) asks(s, at, k, afterCase(case), "hintSay.after") else asks(s, k, k, chunkCase(case), "hintSay.with").copy(word = "${s.spans[at].value} … ${s.spans[k].value}")

    private fun chunkCase(case: String) = if (case == "acc") "hint.de.withAcc" else "hint.de.withDat"

    /** A two-way preposition at [k]: where to, the accusative, by a verb of motion; where, the dative, by a verb of rest; when, the dative, by a noun of time; else both. */
    private fun twoWay(s: Said, i: Int, k: Int): Trigger {
        val time = (k + 1 until minOf(s.words.size, k + 4)).firstOrNull { s.same(it, k) && noun(s, it) }?.let { n -> s.words[n] in T.times || s.words[n].removeSuffix("n").removeSuffix("e") in T.times }
        if (time == true) return asks(s, k, k, "hint.de.whenDat", "hintSay.after")
        val (v, motion) = motionOrRest(s, i) ?: return asks(s, k, k, "hint.de.twoWay", "hintSay.after")
        return asks(s, v, v, if (motion) "hint.de.whereTo" else "hint.de.whereAt", "hintSay.with")
    }

    /** A preposition at the gap (or da-, wo- with it: darauf, dafür) against another, and the verb, adjective or noun whose own it is. */
    private fun chunk(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val prep = daWord(gap) ?: gap
        if (prep !in T.prepositions || forms.size < 2) return null
        val (k, _) = chunkOf(s, i, prep) ?: return null
        return asks(s, k, k, "hint.de.chunk", "hintSay.with")
    }

    /** The preposition of a da- or wo-word (darauf: auf, dafür: für, womit: mit); null for another word. */
    private fun daWord(w: String): String? {
        val rest = Regex("^(dar|wor|da|wo)").find(w)?.let { w.substring(it.value.length) } ?: return null
        return rest.takeIf { it in T.prepositions }
    }

    /** A dative at the gap and a verb of its clause that takes one (helfe, gefällt), alone or with an accusative (gibst): who gets it. */
    private fun verbCase(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if ("dat" !in casesOf(gap) || forms.none { it != gap && related(gap, it) }) return null
        val w = s.words
        val hit = nearest(s, i) { k -> verbShaped(s, k) && T.verbs[w[k]].orEmpty().any { g -> "dat" in g.cases && (g.particle == null || w[s.end(i)] == g.particle) } } ?: return null
        val alone = T.verbs.getValue(w[hit]).any { it.cases == listOf("dat") }
        return asks(s, hit, hit, if (alone) "hint.de.verbDat" else "hint.de.verbWhom", "hintSay.with")
    }

    /**
     * The verb of [i]'s clause: the verb at its end after a helper (Darf ich den Hammer halten), else the one beside the
     * subject (Ich nehme, Warum bewacht ihr, Der Stern zeigt, Wer fährt), else the first word of an order (Stell die Milch …).
     */
    private fun verbOf(s: Said, i: Int): Int? {
        val w = s.words
        val start = s.start(i)
        val end = s.end(i)
        val helper = (start until end).firstOrNull { it != i && (w[it] in MODALS || w[it] in WERDEN || w[it] in HABEN || w[it] in SEIN - "sein") && verbShaped(s, it) }
        if (helper != null && end != i && !s.capital(end) && (participle(w[end]) || infinitive(w[end]))) return end
        val p = (start..end).firstOrNull { k -> k != i && w[k] in set("ich du er sie es wir ihr man wer jemand niemand") && !(k > start && w[k - 1] in T.prepositions) }
        if (p != null) {
            if (p > start && w[p - 1] !in FUNCTION - HELPERS && verbShaped(s, p - 1)) return p - 1
            if (p + 1 != i && p + 1 <= end && w[p + 1] !in FUNCTION - HELPERS && !s.capital(p + 1)) return p + 1
            // weil sie ihn sieht: last
            if (end != i && !s.capital(end) && w[end] !in FUNCTION) return end
            return null
        }
        val b = (i - 1).takeIf { it >= start } ?: return null
        if (b == start && s.opens(b)) return b.takeIf { w[it] in T.motion || w[it] in T.rest || w[it] in T.verbs || w[it].endsWith("ß") || w[it].length <= 5 }?.takeIf { w[it] !in FUNCTION }
        if (w[b] !in FUNCTION - HELPERS && !s.capital(b) && b > start && s.capital(b - 1)) return b
        return null
    }

    /** An accusative at the gap, and the verb it is the object of (ich nehme den Rechen): what it acts on. */
    private fun objectOf(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if ("acc" !in casesOf(gap) || forms.none { it != gap && related(gap, it) }) return null
        val v = verbOf(s, i) ?: return null
        if (s.words[v] in T.copulas) return null
        return asks(s, v, v, "hint.de.object", "hintSay.with")
    }

    /** A nominative at the gap after a verb of being (Das ist der Stein, Was ist das für ein Stein?): what it is. */
    private fun copula(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if ("nom" !in casesOf(gap) || forms.none { "acc" in casesOf(it) && it != gap }) return null
        val k = nearest(s, i) { it < i && s.words[it] in T.copulas } ?: return null
        return asks(s, k, k, "hint.de.copula", "hintSay.with")
    }

    /** An order with no verb: an accusative at the gap beside bitte (Einen Kaffee, bitte.): it is what you ask for. */
    private fun bitte(s: Said, i: Int, gap: String): Trigger? {
        if ("acc" !in casesOf(gap)) return null
        val w = s.words
        val k = w.indices.firstOrNull { w[it] == "bitte" } ?: return null
        if (kotlin.math.abs(s.clause[k] - s.clause[i]) > 1 || (s.start(i)..s.end(i)).any { w[it] in T.motion || w[it] in T.rest || w[it] in HELPERS }) return null
        return asks(s, k, k, "hint.de.bitte", "hintSay.with")
    }

    /** Who does it to themselves: sich anyone (empty), mich ich, dich du. */
    private val OWN_SELF = mapOf("sich" to "", "mich" to "ich", "dich" to "du", "uns" to "wir", "euch" to "ihr")

    /** sich at the gap, or mich, dich with their own pronoun (ich wasche mich), and who does it: the subject of its clause. */
    private fun self(s: Said, i: Int, gap: String): Trigger? {
        val own = OWN_SELF[gap] ?: return null
        val k = before(s, i, emptySet()) ?: return null
        val at = k - 1
        if (at < 0 || !s.same(at, i)) return null
        val from = if (gap == "sich") phraseEnding(s, at) ?: at.takeIf { s.words[it] in SUBJECTS } else at.takeIf { s.words[it] == own }
        return from?.let { Trigger.Cue(s.text(it, at), at, Trigger.Cue.Kind.WHO) }
    }

    /** mir or dir at the gap against mich or dich, and an object of its own in the clause (Ich stelle es mir vor): the dative. */
    private fun twoObjects(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if ("dat" !in casesOf(gap) || forms.none { "acc" in casesOf(it) }) return null
        val k = (i - 1 downTo s.start(i)).firstOrNull { s.words[it] in set("es das ihn sie") && it != s.start(i) } ?: return null
        return asks(s, k, k, "hint.de.twoObjects", "hintSay.with")
    }

    /** One determiner's forms of gender at the gap (der, die; ein, eine; deine, dein) and its noun, past its adjectives; a pronoun's noun in a sentence of its own before it (Ein Fuchs! Er ist rot.). */
    private fun gender(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (forms.size < 2) return null
        if (gap in T.determiners && forms.all { it in T.determiners }) {
            val n = nounFrom(s, i + 1) ?: return null
            return asks(s, n, n, "hint.de.gender", "hintSay.with")
        }
        if (gap in set("er sie es ihn") && forms.all { it in set("er sie es ihn") } && s.clause[i] > 0) {
            val ks = s.words.indices.filter { s.clause[it] == s.clause[i] - 1 }
            val n = ks.lastOrNull()?.takeIf { ks.size in 2..3 && s.capital(it) && s.words[ks.first()] in T.determiners } ?: return null
            return asks(s, n, n, "hint.de.gender", "hintSay.with")
        }
        return null
    }

    /**
     * An adjective at the gap (or one written as a noun: der Fremde, etwas Nettes), its forms of one stem: after a determiner
     * with an ending, it shows the case; after one with none (ein, kein, mein) the adjective shows it; with no determiner too.
     */
    private fun adjectiveEnding(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val w = s.words
        if (gap in T.determiners || gap in T.pronouns || forms.size < 2 || forms.none { it != gap && it.commonPrefixWith(gap).length >= 3 }) return null
        if (!Regex("(e|en|er|es|em)$").containsMatchIn(gap)) return null
        var d = i - 1
        while (d >= 0 && s.same(d, i) && (adjective(s, d) || w[d] in SOFT)) d--
        val inClause = d >= 0 && s.same(d, i)
        // (die von einem Männlein ausgesprochene Warnung: what stands before is another phrase's)
        if (inClause && s.capital(d) && !s.opens(d) && !determiner(s, d)) return null
        if (inClause && w[d] == "etwas") return asks(s, d, d, "hint.de.afterEtwas", "hintSay.after")
        if (inClause && w[d] in T.fused) return asks(s, d, d, "hint.de.adjAfter", "hintSay.after")
        if (inClause && w[d] in T.determiners) return asks(s, d, d, if (w[d] in T.bare) "hint.de.adjEin" else "hint.de.adjAfter", "hintSay.after")
        val n = nounFrom(s, i + 1) ?: return null
        return asks(s, n, n, "hint.de.adjNone", "hintSay.with")
    }

    /** A genitive's article at the gap after a noun (Bilder der Burg): whose it is, by the noun's gender; a noun's -s after des. */
    private fun whose(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val w = s.words
        if (gap in T.determiners && "gen" in casesOf(gap)) {
            val before = (i - 1).takeIf { it >= 0 && s.same(it, i) && s.capital(it) } ?: return null
            val n = nounFrom(s, i + 1) ?: return null
            return if (w[before] !in FUNCTION) asks(s, n, n, "hint.de.whose", "hintSay.with") else null
        }
        val k = (i - 1).takeIf { it >= 0 && s.same(it, i) && w[it] in T.determiners && "gen" in casesOf(w[it]) } ?: return null
        return if (s.capital(i) && forms.any { it != gap && gap.startsWith(it) }) asks(s, k, k, "hint.de.genEnding", "hintSay.after") else null
    }

    /** A noun of the n-Deklination at the gap against its nominative, and the article before it: it changes too. */
    private fun weak(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in T.weak && forms.none { it in T.weak }) return null
        val k = before(s, i, emptySet()) ?: return null
        return if (s.words[k] in T.determiners && s.words[k] !in set("der die das")) asks(s, k, k, "hint.de.weakNoun", "hintSay.after") else null
    }

    /** A noun at the gap against its singular, and before it, past its adjectives, a number or a word of more than one. */
    private fun many(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val w = s.words
        if (!s.capital(i) || gap in MEASURES || !oneNoun(gap, forms)) return null
        var k = i - 1
        while (k >= 0 && s.same(k, i) && (adjective(s, k) && w[k] !in QUANTITY && !number(w[k]))) k--
        if (k < 0 || !s.same(k, i)) return null
        if (number(w[k])) {
            var from = k
            while (from > 0 && s.same(from - 1, i) && number(w[from - 1])) from--
            return asks(s, from, k, "hint.de.many", "hintSay.after")
        }
        if (w[k] in QUANTITY || w[k] == "handvoll") return asks(s, if (k > 0 && w[k - 1] == "wie" && s.same(k - 1, k)) k - 1 else k, k, "hint.de.many", "hintSay.after")
        return null
    }

    /** A noun at the gap against its singular, and the verb of more than one beside it (Die Blüten sind, Das sind Stare, Sind das Bäume?). */
    private fun verbMany(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val w = s.words
        if (!s.capital(i) || !oneNoun(gap, forms)) return null
        val n = i + 1
        if (s.same(n, i) && !s.capital(n) && (w[n] in set("sind waren haben hatten") || (w[n].endsWith("en") && w[n] !in FUNCTION))) return asks(s, n, n, "hint.de.verbMany", "hintSay.with")
        val k = (i - 1).takeIf { it >= 0 && s.same(it, i) } ?: return null
        if (w[k] == "sind") return asks(s, k, k, "hint.de.verbMany", "hintSay.with")
        if (w[k] == "das" && k > 0 && s.same(k - 1, i) && w[k - 1] == "sind") return asks(s, k - 1, k - 1, "hint.de.verbMany", "hintSay.with")
        return null
    }

    /** die against das or der at the gap, on the plural's page, and its noun: one or more decides. */
    private fun oneOrMore(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap != "die" || forms.none { it in set("der das") }) return null
        val n = nounFrom(s, i + 1) ?: return null
        return asks(s, n, n, "hint.de.oneOrMore", "hintSay.with")
    }

    /** ist against sind after es or das, or before das (Das sind Spuren, Es sind hundert Stufen, Sind das Spuren?), and the noun after it: one or more decides the verb. */
    private fun thing(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (forms.none { personPair(gap, it) } || gap !in SEIN) return null
        val w = s.words
        val k = (i - 1).takeIf { it >= 0 && s.same(it, i) }
        val n = when {
            k != null && w[k] in set("das es dies") -> after(s, i, set("schon aber nur doch ja wirklich")) ?: return null
            k == null && s.same(i + 1, i) && w[i + 1] in set("das es dies") -> i + 2
            else -> return null
        }
        val (a, b) = subjectFrom(s, n)?.takeIf { it.first != it.second || noun(s, n) } ?: return null
        if (w[b] == "uhr") return null
        return asks(s, a, b, "hint.de.thing", "hintSay.with")
    }

    /** viel against viele before a noun of more than one (Wie viele Stämme): can you count it? */
    private fun count(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in set("viel viele") || !forms.containsAll(set("viel viele"))) return null
        val n = nounFrom(s, i + 1) ?: return null
        return asks(s, n, n, "hint.de.count", "hintSay.with")
    }

    /**
     * Who does it: the gap a verb whose choices differ by who does it, and its subject: a pronoun or a noun phrase right
     * before it (Die Enten schwimmen, ich glaube, sie halten, Herr Toni kann); else after it (Was kosten die Eier? Dann
     * gewinnen wir. Fressen sie Mücken?); wer or was alone before it is who.
     */
    private fun who(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if ((s.capital(i) && !s.opens(i)) || forms.none { personPair(gap, it) }) return null
        val w = s.words
        val k = before(s, i, set("auch nur noch schon nicht"))
        if (k != null) {
            val governed = k > 0 && s.same(k - 1, k) && (w[k - 1] in T.prepositions || w[k - 1] == "wie")
            if (w[k] in SUBJECTS && w[k] != "wer" && !governed) return Trigger.Cue(s.spans[k].value, k, Trigger.Cue.Kind.WHO)
            phraseEnding(s, k)?.let { from ->
                if (from == k || w[from] !in T.determiners || T.determiners.getValue(w[from]).any { it.startsWith("nom") }) return Trigger.Cue(s.text(from, k), k, Trigger.Cue.Kind.WHO)
            }
        }
        val n = after(s, i, set("denn nicht auch schon doch ja wirklich"))
        if (n != null) subjectFrom(s, n)?.let { (a, b) -> return Trigger.Cue(s.text(a, b), b, Trigger.Cue.Kind.WHO) }
        if (k != null && k == s.start(i) && w[k] in set("wer was")) return Trigger.Cue(s.spans[k].value, k, Trigger.Cue.Kind.WHO)
        return null
    }

    private val TITLES = set("herr frau oma opa tante onkel fräulein doktor")

    /**
     * Who is spoken to: a clause of a name or a title and a name (Herr Toni, Opa Sepp, Florian, Feuerwehr), set off by a
     * comma: after one, ending its sentence ("Tanzen wir, Opa Sepp!", "Ich helfe dir, Maxi."), or opening one, before one
     * ("Florian, komm!"). Not the gap's own clause. Its first and last word.
     */
    private fun addressed(s: Said, i: Int): Pair<Int, Int>? {
        val w = s.words
        fun who(ks: List<Int>): Pair<Int, Int>? = when {
            i in ks -> null
            ks.size == 1 -> ks[0].takeIf { s.capital(it) && w[it] !in FUNCTION && w[it] !in set("danke bitte servus hallo achtung ja nein") }?.let { it to it }
            ks.size == 2 && w[ks[0]] in TITLES && s.capital(ks[1]) -> ks[0] to ks[1]
            else -> null
        }
        for (c in s.clause.distinct()) {
            val ks = w.indices.filter { s.clause[it] == c }
            val after = s.after(ks.last())
            val before = if (ks.first() > 0) s.after(ks.first() - 1) else ""
            val end = after.trim().let { it.isEmpty() || it.first() in ".!?…" }
            val vocative = (',' in before && end) || (s.opens(ks.first()) && ',' in after)
            if (vocative) who(ks)?.let { return it }
        }
        return null
    }

    /** An infinitive at the gap against another form of it, and a modal, würde or werden before it in its clause (Darf ich … machen, lässt sich … herausfinden). */
    private fun infinitiveAfter(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (!infinitive(gap) || forms.none { it != gap && (personPair(gap, it) || participle(it) || (gap == "sein" && it in SEIN) || (gap == "haben" && it in HABEN)) }) return null
        val k = (i - 1 downTo s.start(i)).firstOrNull { s.words[it] in BEFORE_INFINITIVE && verbShaped(s, it) } ?: return null
        return asks(s, k, k, "hint.de.infinitive", "hintSay.after")
    }

    /** A modal's infinitive at the gap against its participle, after another verb's infinitive (hätte bleiben können): two infinitives. */
    private fun doubleInfinitive(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in MODALS || forms.none { participle(it) && it != gap }) return null
        val k = (i - 1).takeIf { it >= 0 && s.same(it, i) && infinitive(s.words[it]) } ?: return null
        return asks(s, k, k, "hint.de.doubleInf", "hintSay.after")
    }

    /** A participle at the gap against another form of it, and haben or sein before it in its clause: the helper. */
    private fun participleAfter(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (!participle(gap) || forms.size < 2) return null
        val helps = { k: Int -> (s.words[k] in HABEN || s.words[k] in SEIN - "sein") && verbShaped(s, k) }
        // (in a clause with its verb last, the helper comes after it: dass es gehalten hat)
        (i + 1).takeIf { s.same(it, i) && helps(it) }?.let { return asks(s, it, it, "hint.de.participleBefore", "hintSay.with") }
        val k = (i - 1 downTo s.start(i)).firstOrNull(helps) ?: return null
        return asks(s, k, k, "hint.de.participle", "hintSay.after")
    }

    /** sein against haben at the gap (ist, hat; wäre, hätte; war, hatte), and the participle of its clause: its verb decides. */
    private fun byParticiple(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (forms.none { it in SEIN } || forms.none { it in HABEN }) return null
        val k = nearest(s, i) { participle(s.words[it]) && !s.capital(it) } ?: return null
        return asks(s, k, k, "hint.de.byParticiple", "hintSay.with")
    }

    /** hätte against habe, hatte or würde, and the participle of its clause: imagined about the past. */
    private fun pastImagined(s: Said, i: Int, gap: String): Trigger? {
        if (gap !in KONJUNKTIV) return null
        val k = nearest(s, i) { participle(s.words[it]) && !s.capital(it) } ?: return null
        return asks(s, k, k, "hint.de.pastImagined", "hintSay.with")
    }

    /** What German says with haben (a noun: Hunger, Durst) or sein (an adjective: alt, kalt). */
    private val HAVE_NOUNS = set("hunger durst angst zeit lust fieber glück pech recht sorgen schmerzen mut geduld")

    /** sein against haben at the gap, and what comes with it: a noun of feeling (Ich habe Hunger), a thing someone has, an adjective (Ich bin müde, Wie alt bist du?). */
    private fun feel(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (forms.none { it in SEIN } || forms.none { it in HABEN }) return null
        val w = s.words
        if (gap in HABEN) {
            val from = after(s, i, MIDDLE + set("du ich er sie es wir ihr")) ?: return null
            val n = if (w[from] in T.determiners || number(w[from])) nounFrom(s, from + 1) else nounFrom(s, from)
            n ?: return null
            return asks(s, n, n, if (w[n] in HAVE_NOUNS) "hint.de.feelHaben" else "hint.de.haveThing", "hintSay.with")
        }
        if (i >= 2 && s.same(i - 2, i) && w[i - 2] == "wie") return asks(s, i - 1, i - 1, "hint.de.feelSein", "hintSay.with")
        val e = s.end(i)
        if (e == i || s.capital(e) || w[e] in MIDDLE || w[e] in FUNCTION) return null
        return asks(s, e, e, "hint.de.feelSein", "hintSay.with")
    }

    /** kein against nicht at the gap: a noun after it (ich habe keine Zeit); nicht against kein: the verb before it, no noun to say no to. */
    private fun kein(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val w = s.words
        if (gap.startsWith("kein") && "nicht" in forms) {
            val n = nounFrom(s, i + 1) ?: return null
            return asks(s, n, n, "hint.de.noNoun", "hintSay.with")
        }
        if (gap !in set("nicht nichts") || forms.none { it.startsWith("kein") }) return null
        val k = (i - 1 downTo s.start(i)).firstOrNull { w[it] !in MIDDLE && w[it] !in set("ich du er sie es wir ihr man das") && !s.capital(it) } ?: return null
        return asks(s, k, k, "hint.de.noVerb", "hintSay.with")
    }

    /**
     * A relative pronoun at the gap: after a preposition, its case; a verb of its clause that takes a dative; who does it in
     * its clause, a subject of its own after it (the object) or none (it does it itself); else its noun before the comma.
     */
    private fun relative(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in T.relative || forms.size < 2) return null
        val w = s.words
        prep(s, i, gap, forms)?.let { return it }
        val nominative = "nom" in casesOf(gap)
        if ("dat" in casesOf(gap) || gap == "denen") verbCase(s, i, "dem", forms + "dem")?.let { return it }
        val noun = (i - 1).takeIf { it >= 0 && !s.same(it, i) }?.let { k -> phraseEnding(s, k)?.let { it to k } ?: k.takeIf { s.capital(it) }?.let { it to it } }
        val n = i + 1
        if (s.same(n, i) && w[n] in set("ich du er sie wir ihr man")) return asks(s, n, n, "hint.de.relObject", "hintSay.with")
        if (nominative && noun != null && s.same(n, i) && (w[n] in MIDDLE || w[n] in T.prepositions || w[n] in T.fused || w[n] == "sich")) {
            return asks(s, noun.second, noun.second, "hint.de.relSubject", "hintSay.with")
        }
        return noun?.let { (_, b) -> asks(s, b, b, "hint.de.relNoun", "hintSay.with") }
    }

    private val KONJUNKTIV = set(
        "wäre wärst wären wärt hätte hättest hätten hättet würde würdest würden würdet könnte könntest könnten könntet müsste müsstest müssten müsstet dürfte dürftest " +
            "dürften sollte solltest sollten möchte möchtest möchten wüsste wüsstest wüssten",
    )

    /** The Konjunktiv at the gap against the plain form, and what says it isn't so: another Konjunktiv in the sentence's other half (Wenn ich krank wäre, würde ich …), ich wünschte, ohne at its clause's start. */
    private fun imagined(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val w = s.words
        if (gap !in KONJUNKTIV && forms.none { it != gap && plain(it) == plain(gap).removeSuffix("e") }) return null
        val other = w.indices.firstOrNull { !s.same(it, i) && w[it] in KONJUNKTIV && kotlin.math.abs(s.clause[it] - s.clause[i]) == 1 }
        if (other != null) return asks(s, other, other, "hint.de.imagined", "hintSay.with")
        (0 until i).firstOrNull { w[it] == "wünschte" }?.let { return asks(s, it, it, "hint.de.imagined", "hintSay.with") }
        val start = s.start(i)
        if (w[start] == "ohne" && start < i) return asks(s, start, start, "hint.de.imagined", "hintSay.with")
        return null
    }

    /** The Konjunktiv at the gap against the plain form, and gern with it (Ich hätte gern …), or a question to Sie or du (Könnten Sie …?): politely. */
    private fun polite(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val w = s.words
        if (gap !in KONJUNKTIV || forms.none { it !in KONJUNKTIV }) return null
        nearest(s, i) { w[it] in set("gern gerne") }?.let { return asks(s, it, it, "hint.de.polite", "hintSay.with") }
        if (i == s.start(i) && s.asks(i) && s.same(i + 1, i) && w[i + 1] in set("sie du")) return asks(s, i + 1, i + 1, "hint.de.polite", "hintSay.with")
        return null
    }

    private val PAIRS = mapOf("als" to "sowohl", "noch" to "weder", "oder" to "entweder", "sondern" to "nur", "aber" to "zwar")

    /** The second half of a pair at the gap (als, noch, oder, sondern, aber), and its first half before it (sowohl, weder, entweder, nicht nur, zwar). */
    private fun pair(s: Said, i: Int, gap: String): Trigger? {
        val first = PAIRS[gap] ?: return null
        val k = (0 until i).firstOrNull { s.words[it] == first } ?: return null
        return if (first == "nur" && k > 0 && s.words[k - 1] == "nicht") asks(s, k - 1, k, "hint.de.pair", "hintSay.with") else asks(s, k, k, "hint.de.pair", "hintSay.with")
    }

    /** A verb's own little word at the gap, at its clause's end: the verb of the clause it belongs to (fängt … an, kommt … herunter). */
    private fun particle(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (forms.size < 2 || i != s.end(i)) return null
        if (gap !in PARTICLES) return null
        val k = before(s, i, MIDDLE + set("nicht mehr alles ich du er sie es wir ihr")) ?: return null
        return asks(s, k, k, "hint.de.particle", "hintSay.with")
    }

    /** wo against wohin at the gap, and the verb after it: of rest (Wo wohnt er?, Wo ist …?): being there. */
    private fun askRest(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in set("wo wohin") || !forms.containsAll(set("wo wohin"))) return null
        val n = i + 1
        if (!s.same(n, i)) return null
        val w = s.words[n]
        return if (w in T.rest && w !in T.motion) asks(s, n, n, "hint.de.askRest", "hintSay.with") else if (w in T.motion) asks(s, n, n, "hint.de.askMotion", "hintSay.with") else null
    }

    /** als against wenn at the gap: immer before it, every time; the clause's verb in the past, once; in the present, now or later; wenn against wann: a question; bevor against vor: a clause. */
    private fun whenClause(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val w = s.words
        val e = s.end(i)
        if (gap in set("als wenn") && forms.containsAll(set("als wenn")) && i < e) {
            (i - 1).takeIf { it >= 0 && s.same(it, i) && w[it] == "immer" }?.let { return asks(s, it, it, "hint.de.whenEvery", "hintSay.with") }
            return if (w[e] in set("war waren hatte hatten") || w[e].endsWith("te") || w[e].endsWith("ten")) asks(s, e, e, "hint.de.whenOnce", "hintSay.with") else asks(s, e, e, "hint.de.whenNow", "hintSay.with")
        }
        if (gap == "bevor" && "vor" in forms && i < e) return asks(s, e, e, "hint.de.clause", "hintSay.with")
        return null
    }

    /** A word that takes first place at the gap against one that sends the verb last, and the verb right after it (Trotzdem hat's …, Wann fängt …?). */
    private fun verbNext(s: Said, i: Int, gap: String): Trigger? {
        if (gap !in set("trotzdem deshalb deswegen dann wann")) return null
        val n = i + 1
        return if (s.same(n, i) && !s.capital(n)) asks(s, n, n, "hint.de.verbNext", "hintSay.with") else null
    }

    private val NOUN_VERB = set("vorschlag rücksicht beschluss platz entscheidung entscheidungen abschied verfügung antrag bescheid frage rolle hilfe bezug")

    /** A verb at the gap and a noun of its clause that has its own verb (einen Vorschlag machen, Platz nehmen). */
    private fun nounVerb(s: Said, i: Int, gap: String): Trigger? {
        val k = nearest(s, i) { s.words[it] in NOUN_VERB && s.capital(it) } ?: return null
        return asks(s, k, k, "hint.de.nounVerb", "hintSay.with")
    }

    /** A participle as an adjective at the gap, I against II (tanzenden, getanzten), and its noun: doing it itself, or done to it. */
    private fun partAdjective(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val present = Regex("nde[nrsm]?$")
        if (forms.none { present.containsMatchIn(it) } || forms.all { present.containsMatchIn(it) }) return null
        val n = nounFrom(s, i + 1) ?: return null
        return asks(s, n, n, if (present.containsMatchIn(gap)) "hint.de.partNow" else "hint.de.partDone", "hintSay.with")
    }

    /** um against für at the gap, and zu with its verb at its clause's end: what for, with a verb. */
    private fun whatFor(s: Said, i: Int, gap: String): Trigger? {
        if (gap != "um") return null
        val e = s.end(i)
        return if (e > i + 1 && s.words[e - 1] == "zu") asks(s, e - 1, e, "hint.de.whatFor", "hintSay.with") else null
    }

    /** A participle at the gap against an infinitive after werden (Daraus wird Heidensterz gekocht); wurde or wird against hat or worden after a participle at the clause's end. */
    private fun passive(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        val w = s.words
        if (participle(gap)) {
            val k = (i - 1 downTo s.start(i)).firstOrNull { w[it] in WERDEN } ?: return null
            return asks(s, k, k, "hint.de.passive", "hintSay.after")
        }
        if (gap !in WERDEN) return null
        val k = (i - 1).takeIf { it >= 0 && s.same(it, i) && participle(w[it]) } ?: return null
        // (müssen noch übersetzt werden: the modal's infinitive)
        (k - 1 downTo s.start(i)).firstOrNull { w[it] in MODALS }?.let { return asks(s, it, it, "hint.de.infinitive", "hintSay.after") }
        return when {
            "worden" in forms -> asks(s, k, k, "hint.de.oneVerb", "hintSay.with")
            forms.any { it in HABEN } -> asks(s, k, k, "hint.de.passiveDone", "hintSay.with")
            else -> null
        }
    }

    /** worden against geworden at the gap, and the participle before it: in the passive, no ge-. */
    private fun worden(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap != "worden" || "geworden" !in forms) return null
        val k = (i - 1).takeIf { it >= 0 && s.same(it, i) && participle(s.words[it]) } ?: return null
        return asks(s, k, k, "hint.de.worden", "hintSay.with")
    }

    /** A fused word at the gap against its preposition (Zum Imkern, beim Pusten), and a verb written as a noun after it: das. */
    private fun verbNoun(s: Said, i: Int, gap: String, forms: Set<String>): Trigger? {
        if (gap !in T.fused || forms.none { it == T.fused.getValue(gap).first }) return null
        val n = i + 1
        return if (s.same(n, i) && s.capital(n) && s.words[n].endsWith("n")) asks(s, n, n, "hint.de.verbNoun", "hintSay.with") else null
    }

    private val PAST_WORDS = set("gestern vorgestern damals früher einst")

    /** A word of the past in the sentence (gestern, damals). */
    private fun past(s: Said, i: Int): Trigger? {
        val k = s.words.indices.firstOrNull { s.words[it] in PAST_WORDS } ?: return null
        return Trigger.Cue(s.spans[k].value, k, Trigger.Cue.Kind.PAST)
    }
}
