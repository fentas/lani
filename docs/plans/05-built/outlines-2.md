# Built: the outlines of the school's remaining levels

*Built 2026-11-02, branch `outlines-2`. Jan, 2026-11-02: "yes complete". Every course goes to B2, the default aim. Seven
levels had no units: German A2, B1, B2; English A2, B1, B2; Italian B2. This branch outlines them, so that three writers per
level can write units side by side without overlapping. Only the three `course.json` files and this doc changed. Everything
here is machine-written (Claude) and waits for a native speaker's check.*

## What

Seven levels of 18 planned units each, 126 in all, in `companion/course/{de,en,it}/course.json`, each with its id, its
title in the four languages and `"planned": true`. Each level splits into three parts of six units.

**Why 18.** The Italian course has 18 at each level, Slovene's B2 and the German and English A1 too. Every level's
material fits without padding, so nothing here argues for more or fewer. Slovene's A2 (14) and B1 (16) are smaller, but
the levels outlined since all took 18.

| level | `-a`, units 1–6 | `-b`, units 7–12 | `-c`, units 13–18 |
|---|---|---|---|
| German A2 | the dative and where things are | more verbs | adjective endings, comparing, clauses |
| German B1 | telling the past, the fourth case | relative clauses, adjectives in full, zu | mood, voice, small words |
| German B2 | moods and voice in full | the noun phrase | text and register |
| English A2 | commands, the past | how much, comparing, how | the future, must, the perfect, if |
| English B1 | time | clauses, voice, verb patterns | if, might, what was said |
| English B2 | tenses and what might have been | clauses and verb forms | emphasis, words, register |
| Italian B2 | the congiuntivo's other tenses | if, regret, telling | other forms, words, a text |

## How to write from this

- **Name your part** by the tables below (`de-a2-a` is German A2's units 1–6). Each part assumes the units before it in
  that language's outline, written or not, and nothing after it.
- **`grammar` in the outline** holds only pages that exist today, because the smoke refuses any other. A unit whose pages
  are all new has `"grammar": []`. The full list is the table's: **bold** is a new page your part writes, the first name is
  the unit's summary card.
- **A new page has one owner**, the part listed under its level. A unit never needs another part's new page while it is
  written: the column "once merged, also" holds those (39 units), for one small commit after the parts concerned are
  merged, into the unit and its outline line. Until then, say the rule in the unit's own intro and name nothing.
- **Your unit's line in `course.json`**: take off `"planned": true`, set `grammar`, keep the id and the title. Each unit is
  one line, so that the lead's `resolve-outline.py` can settle the parts' conflicts.
- **Ids.** No new unit id or page id equals an existing unit's or page's in that language. A new page may carry its own
  unit's id, as the earlier levels do. Each unit's title in the language learned is at most 41 characters, in letters the
  blackboard can write (`SchoolBoardTest`).
- **The audience stays the course's**: German and English for adults in their village, Italian for the child of the Friuli
  village (tu, nothing frightening), COURSE.md's sections for each.

## The levels

### German A2

**`de-a2-a`** (units 1–6)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 1 | `wem-dativ` | Ich gebe dem Hund Wasser | Who gets something: dem, der, dem, den + -n in the plural; einem, einer, meinem, keinem; geben, zeigen, schenken with two objects. | dativ, faelle | – |
| 2 | `mir-dir` | Das gefällt mir, ich helfe dir | mir, dir, ihm, ihr, uns, euch, ihnen beside mich, dich, ihn; gefallen, helfen, schmecken, gehören, danken; es geht mir gut. | **pronomen-dativ**, dativ, akkusativ | – |
| 3 | `mit-nach-bei` | Mit dem Bus nach Villach | aus, bei, mit, nach, seit, von, zu; beim, vom, zum, zur; nach Hause and zu Hause. | **praepositionen-dativ**, dativ | – |
| 4 | `fuer-ohne` | Für dich, ohne mich | bis, durch, für, gegen, ohne, um, always with the accusative; set against the dative list before it. | **praepositionen-akkusativ**, akkusativ | – |
| 5 | `wo-wohin` | In der Küche, in die Küche | an, auf, hinter, in, neben, über, unter, vor, zwischen: wo? takes the dative, wohin? the accusative; im, ins, am, ans. | wechselpraepositionen, dativ, akkusativ | – |
| 6 | `stehen-stellen` | Das Glas steht, ich stelle es hin | stehen and stellen, liegen and legen, sitzen and setzen, hängen: the state with the dative, the movement with the accusative. Present only: their Perfekt comes in `partizip-perfekt`. | **stehen-stellen**, wechselpraepositionen | – |

**`de-a2-b`** (units 7–12)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 7 | `muessen-duerfen` | Ich muss, ich darf, ich will | müssen, dürfen, sollen, wollen, mögen beside A1's können and möchten; the infinitive last; nicht müssen against nicht dürfen. | modalverben, wortstellung | – |
| 8 | `partizip-perfekt` | Aufgestanden, verstanden, gesessen | ge-…-t and ge-…-en; separable auf-ge-standen; be-, ver-, er-, ent- without ge-; -ieren; haben or sein; the Austrian standard's ich bin gesessen, gestanden, gelegen, with ich habe gesessen accepted. | **partizip-zwei**, perfekt, trennbare-verben | – |
| 9 | `war-hatte` | Ich war, ich hatte, ich konnte | The Präteritum of sein, haben and the modal verbs, and es gab: what people say even when everything else is Perfekt. | **praeteritum-kurz**, sein-haben, modalverben | – |
| 10 | `reflexiv` | Ich wasche mir die Hände | mich, dich, sich, uns, euch; sich freuen, sich waschen, sich setzen; mir and dir once a thing is named (ich wasche mir die Hände); where sich stands. | **reflexive-verben** | pronomen-dativ |
| 11 | `warten-auf` | Ich warte auf den Bus | warten auf, denken an, sich freuen auf and über, sprechen mit and über, Angst haben vor: each learned as one chunk with its case. | **verben-praepositionen**, akkusativ, dativ | – |
| 12 | `am-ersten-mai` | Am ersten Mai, vor zwei Tagen | der erste, zweite, dritte, siebte, zwanzigste; am ersten Mai, der 1. Mai, vom … bis zum; vor zwei Tagen, in einer Woche; Jänner and heuer. | **ordinalzahlen**, uhrzeit, zahlen | – |

**`de-a2-c`** (units 13–18)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 13 | `adjektiv-der` | Der große Hund, den großen Hund | After der, die, das: -e or -en, nominative, accusative and dative; dieser, jeder, welcher work the same. | **adjektiv-der**, artikel-genus, faelle | – |
| 14 | `adjektiv-ein` | Ein großer Hund, ein kleines Haus | After ein, kein, mein: -er and -es where the article shows no ending, else as after der. | **adjektiv-ein**, **adjektiv-der**, possessiv, kein-nicht | – |
| 15 | `adjektiv-null` | Frisches Brot, mit heißem Tee | No article: the adjective takes the ending der would have (frischer Käse, frische Milch, mit heißem Tee); plurals with viele, einige. | **adjektiv-null**, **adjektiv-der** | – |
| 16 | `groesser-als` | Größer als, so groß wie, am größten | -er als, so … wie, am -sten; the umlaut (alt, älter); gut, besser; gern, lieber; viel, mehr. Before a noun comes in B1. | **steigerung** | – |
| 17 | `weil-denn` | Weil es regnet, denn es regnet | und, aber, oder, denn, sondern keep the word order; deshalb takes first place and the verb follows; weil sends the verb last. | **konnektoren**, nebensatz, wortstellung | – |
| 18 | `dass-wenn-ob` | Ich weiß nicht, ob er kommt | dass after glauben and wissen; wenn for if; ob and a question word in an indirect question (Ich weiß nicht, wo er ist); a separable verb whole at the end. | nebensatz, fragen, wortstellung | – |

New pages of German A2 (14):

- `pronomen-dativ` (`de-a2-a`, unit `mir-dir`): The personal pronouns in all three cases taught so far: mir, mich, ich; verbs that take a dative.
- `praepositionen-dativ` (`de-a2-a`, unit `mit-nach-bei`): aus, bei, mit, nach, seit, von, zu, their contractions, nach Hause and zu Hause.
- `praepositionen-akkusativ` (`de-a2-a`, unit `fuer-ohne`): bis, durch, für, gegen, ohne, um.
- `stehen-stellen` (`de-a2-a`, unit `stehen-stellen`): The pairs stehen and stellen, liegen and legen, sitzen and setzen, and hängen, with their cases.
- `partizip-zwei` (`de-a2-b`, unit `partizip-perfekt`): How every kind of verb makes its participle; haben or sein; ich bin gesessen as Austria says it.
- `praeteritum-kurz` (`de-a2-b`, unit `war-hatte`): war, hatte, es gab and the modal verbs' past.
- `reflexive-verben` (`de-a2-b`, unit `reflexiv`): Reflexive pronouns in the accusative and the dative; their place in the sentence.
- `verben-praepositionen` (`de-a2-b`, unit `warten-auf`): The list of verbs with a fixed preposition and its case.
- `ordinalzahlen` (`de-a2-b`, unit `am-ersten-mai`): Ordinals, how to say and write a date, the months as Austria names them, vor and in with a time.
- `adjektiv-der` (`de-a2-c`, unit `adjektiv-der`): Adjective endings after der and its kind, nominative, accusative and dative.
- `adjektiv-ein` (`de-a2-c`, unit `adjektiv-ein`): Adjective endings after ein and its kind.
- `adjektiv-null` (`de-a2-c`, unit `adjektiv-null`): Adjective endings with no article.
- `steigerung` (`de-a2-c`, unit `groesser-als`): The comparative and superlative: forms, umlaut, the irregular ones, als and wie.
- `konnektoren` (`de-a2-c`, unit `weil-denn`): Linking words by what they do to the verb: no change, verb second, verb last.

### German B1

**`de-b1-a`** (units 1–6)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 1 | `werden-futur` | Es wird kalt, ich werde kommen | ich werde, du wirst, er wird; werden for become (Es wird kalt, Stefan wird Wirt); Futur I for a promise or a guess; the present with a time word stays the usual way. | **werden-futur** | – |
| 2 | `praeteritum` | Es war einmal, er ging, sie sagte | Regular -te, strong ging, kam, sah, mixed dachte, brachte; in stories and writing, while Austria talks in the Perfekt. | **praeteritum**, perfekt | praeteritum-kurz |
| 3 | `als-waehrend` | Als ich klein war, bevor er kam | als for once in the past against wenn for each time; während, bevor, bis, seit. | **temporalsaetze**, nebensatz | – |
| 4 | `plusquamperfekt` | Nachdem ich gegessen hatte | hatte or war + the participle; nachdem and the tense it asks for; what came first told second. | **plusquamperfekt**, perfekt, **temporalsaetze** | – |
| 5 | `genitiv` | Das Haus meines Vaters | des + -s or -es, der; eines, meines; Stefans Hund; von + the dative as people say it, right too; the table of the four cases complete. | **genitiv**, faelle | – |
| 6 | `wegen-trotz` | Wegen des Regens, trotz der Kälte | wegen, trotz, während, statt, innerhalb, außerhalb; the spoken dative (wegen dem Regen) is a spoken form, labelled, never wrong. | **praepositionen-genitiv**, **genitiv** | – |

**`de-b1-b`** (units 7–12)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 7 | `relativsatz` | Der Mann, der dort steht | der, die, das, den, dem, denen: the gender from the noun, the case from its own clause; the verb last, the commas. | **relativsatz**, nebensatz, dativ, akkusativ | – |
| 8 | `relativsatz-praeposition` | Der Tisch, auf dem das Brot liegt | A preposition before the relative; wo for a place; was after alles, etwas, nichts. | **relativsatz**, wechselpraepositionen | – |
| 9 | `adjektiv-alle-faelle` | Mit einem guten Freund | The genitive and the plural added: one table for all three (der, ein, no article), and the one rule behind it: the ending shows the case once. | **adjektiv-tabelle** | adjektiv-der, adjektiv-ein, adjektiv-null |
| 10 | `schnellere` | Der schnellere Zug, das beste Bier | The comparative and superlative before a noun, with the endings; adverbs (am besten, lieber); immer größer; je … desto. | **adjektiv-tabelle** | steigerung |
| 11 | `um-zu` | Ich habe Lust zu kochen | anfangen zu, versuchen zu, Lust haben zu, es ist schön zu; aufzustehen; um … zu against damit; ohne … zu, statt … zu. | **infinitiv-zu**, wortstellung | – |
| 12 | `obwohl-trotzdem` | Obwohl es regnet, gehen wir | obwohl with the verb last, trotzdem with the verb second; set against weil and deshalb. | **obwohl-trotzdem**, nebensatz | konnektoren |

**`de-b1-c`** (units 13–18)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 13 | `haette-waere` | Ich hätte gern, ich wäre, ich würde | hätte, wäre, würde + the infinitive; wishes and asking kindly (ich hätte gern, ich würde gern). | **konjunktiv-zwei** | – |
| 14 | `wenn-ich-haette` | Wenn ich Zeit hätte, käme ich | könnte, müsste, sollte, dürfte, käme; advice (du solltest), asking (könntest du); wenn + Konjunktiv II for what isn't so; Hätte ich Zeit, … to read. | **konjunktiv-zwei**, modalverben, nebensatz | – |
| 15 | `passiv` | Das Brot wird gebacken | werden + the participle, wurde for the past, von + the dative; man as the everyday way round it. | **passiv** | werden-futur |
| 16 | `woran-daran` | Worauf wartest du? Darauf! | worauf, woran, wofür; darauf, daran, dafür for things; auf wen, an wen, auf ihn for people. | **wo-da-woerter** | verben-praepositionen |
| 17 | `jemand-irgendwer` | Jemand, niemand, irgendwer | man, jemand, niemand, etwas, nichts, alle, manche, wenige, irgendwer, irgendwelche; einer, keiner, welche standing alone; derselbe. | **indefinitpronomen** | – |
| 18 | `nicht-nur-sondern` | Nicht nur Brot, sondern auch Wein | nicht nur … sondern auch, sowohl … als auch, weder … noch, entweder … oder. | **doppelkonnektoren** | konnektoren |

New pages of German B1 (15):

- `werden-futur` (`de-b1-a`, unit `werden-futur`): werden's forms, werden as become, and Futur I.
- `praeteritum` (`de-b1-a`, unit `praeteritum`): The Präteritum of regular, strong and mixed verbs; when it is used against the Perfekt.
- `temporalsaetze` (`de-b1-a`, unit `als-waehrend`): als, wenn, während, bevor, nachdem, bis, seit: which for what.
- `plusquamperfekt` (`de-b1-a`, unit `plusquamperfekt`): Forms and use; nachdem and its tenses.
- `genitiv` (`de-b1-a`, unit `genitiv`): Genitive forms of the articles and nouns; names; von + the dative.
- `praepositionen-genitiv` (`de-b1-a`, unit `wegen-trotz`): wegen, trotz, während, statt, innerhalb, außerhalb; the spoken dative.
- `relativsatz` (`de-b1-b`, unit `relativsatz`): The relative pronouns' table, with and without a preposition; wo and was.
- `adjektiv-tabelle` (`de-b1-b`, unit `adjektiv-alle-faelle`): All the adjective endings in one table, every case, singular and plural, and the rule behind it.
- `infinitiv-zu` (`de-b1-b`, unit `um-zu`): The infinitive with zu after verbs, nouns and adjectives; um, ohne, statt; damit.
- `obwohl-trotzdem` (`de-b1-b`, unit `obwohl-trotzdem`): obwohl and trotzdem, the same meaning and different word order.
- `konjunktiv-zwei` (`de-b1-c`, unit `haette-waere`): Its forms, würde + the infinitive, and what it is for: wishes, courtesy, advice, what isn't so.
- `passiv` (`de-b1-c`, unit `passiv`): The passive in the present and the Präteritum; von; man.
- `wo-da-woerter` (`de-b1-c`, unit `woran-daran`): wo- and da- with a preposition, for things; the preposition + a pronoun, for people.
- `indefinitpronomen` (`de-b1-c`, unit `jemand-irgendwer`): The indefinite pronouns with their case forms; derselbe.
- `doppelkonnektoren` (`de-b1-c`, unit `nicht-nur-sondern`): The paired conjunctions and the verb after them.

### German B2

**`de-b2-a`** (units 1–6)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 1 | `haette-gewusst` | Hätte ich das nur gewusst | hätte or wäre + the participle; regrets; the unreal past condition; hätte kommen können. | **konjunktiv-vergangenheit** | konjunktiv-zwei |
| 2 | `als-ob` | Er tut, als ob er schliefe | als ob, als wenn, als + the verb; wishes that can't come true (Wenn er doch käme!). | **als-ob** | konjunktiv-zwei |
| 3 | `konjunktiv-eins` | Er sagt, er sei krank | sei, habe, komme in news and writing; Konjunktiv II where the form is the present's; the pronouns that shift; in talk dass + the indicative, right too. | **indirekte-rede** | – |
| 4 | `passiv-zeiten` | Das Haus ist gebaut worden | ist gebaut worden (never geworden), war gebaut worden; with a modal verb (muss gemacht werden, müsste gemacht werden). | **passiv-zeiten** | passiv |
| 5 | `passiv-ersatz` | Das lässt sich machen, das ist machbar | sich lassen, sein + zu, -bar, man; the state against the action (die Tür ist geöffnet, wird geöffnet). | **passiv-ersatz** | passiv |
| 6 | `soll-duerfte` | Er soll reich sein, er dürfte da sein | muss, dürfte, könnte for how sure, soll for what others say, will for what they claim; Futur II for a guess (er wird schon angekommen sein). | **modalverben-vermutung**, modalverben | – |

**`de-b2-b`** (units 7–12)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 7 | `herr-kollege` | Dem Herrn, den Kollegen, des Namens | The n-declension: der Herr, der Kollege, der Bauer, der Name; every case but the nominative. | **n-deklination**, faelle | – |
| 8 | `der-bekannte` | Der Bekannte, etwas Gutes | der Bekannte, ein Bekannter, die Angestellten: an adjective's endings on a noun; etwas Neues, nichts Gutes, viel Schönes. | **adjektiv-nomen** | adjektiv-tabelle |
| 9 | `partizip-adjektiv` | Der spielende Hund, das gebackene Brot | Partizip I (-end) for what is going on, Partizip II for what was done, each with the endings. | **partizip-adjektiv** | adjektiv-tabelle, partizip-zwei |
| 10 | `partizipialattribut` | Das von Oma gebackene Brot | A long attribute before the noun, to read above all, and its relative clause back and forth. | **partizipialattribut**, **partizip-adjektiv** | relativsatz |
| 11 | `stolz-auf` | Stolz auf, zufrieden mit, bekannt für | stolz auf, zufrieden mit, bekannt für, abhängig von; nouns too (Angst vor, Interesse an); darauf, dass …. | **adjektive-praepositionen** | wo-da-woerter |
| 12 | `nominalisierung` | Beim Kochen, wegen des Regens | das Kochen, -ung, -heit, -keit, and a clause turned into a phrase and back (weil es regnet, wegen des Regens; als ich ankam, bei meiner Ankunft). | **nominalisierung** | praepositionen-genitiv |

**`de-b2-c`** (units 13–18)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 13 | `indem-sodass` | Indem du übst, sodass es klappt | indem, dadurch dass; sodass, so … dass; zu …, um zu; ohne dass, anstatt dass. | **indem-sodass**, nebensatz | – |
| 14 | `zwar-aber` | Zwar teuer, aber gut | zwar … aber, auch wenn, selbst wenn, dennoch, allerdings, jedoch; während and wohingegen; einerseits, andererseits. | **zwar-aber** | konnektoren |
| 15 | `nomen-verb` | Eine Entscheidung treffen | Fixed pairs: eine Entscheidung treffen, Bescheid sagen, in Frage stellen, zur Verfügung stehen, Rücksicht nehmen. | **nomen-verb** | – |
| 16 | `satzbau` | Gestern hat mir Oma im Garten geholfen | The middle of the sentence: pronouns first, dative before accusative, time, cause, manner, place; first place for emphasis; where nicht goes. | **satzbau**, wortstellung | – |
| 17 | `formeller-brief` | Sehr geehrte Frau Magister | Opening and closing, Austrian titles, a request and a complaint; dabei, dafür, diese to tie a text together; Konjunktiv II for courtesy. | **formeller-brief**, du-sie | – |
| 18 | `oesterreichisch` | Heuer, Jänner, ich bin gesessen | Austrian standard against German German in words and grammar; spoken Carinthian to recognise, never marked wrong (as Slovene's `govorjeni-jezik`). | **oesterreichisch** | – |

New pages of German B2 (18):

- `konjunktiv-vergangenheit` (`de-b2-a`, unit `haette-gewusst`): hätte or wäre + the participle; with a modal verb.
- `als-ob` (`de-b2-a`, unit `als-ob`): Unreal comparison and unreal wishes.
- `indirekte-rede` (`de-b2-a`, unit `konjunktiv-eins`): Konjunktiv I's forms, when II replaces it, and how speech differs from writing.
- `passiv-zeiten` (`de-b2-a`, unit `passiv-zeiten`): The passive in the Perfekt and Plusquamperfekt and with modal verbs.
- `passiv-ersatz` (`de-b2-a`, unit `passiv-ersatz`): sich lassen, sein + zu, -bar, man, and the state passive.
- `modalverben-vermutung` (`de-b2-a`, unit `soll-duerfte`): Modal verbs for how sure the speaker is; Futur II for a guess.
- `n-deklination` (`de-b2-b`, unit `herr-kollege`): Which nouns take -n and -en, and the exceptions such as der Name, des Namens.
- `adjektiv-nomen` (`de-b2-b`, unit `der-bekannte`): Adjectives used as nouns, with their endings; etwas, nichts, viel + an adjective.
- `partizip-adjektiv` (`de-b2-b`, unit `partizip-adjektiv`): Partizip I and II before a noun.
- `partizipialattribut` (`de-b2-b`, unit `partizipialattribut`): How to read a long attribute and turn it into a relative clause.
- `adjektive-praepositionen` (`de-b2-b`, unit `stolz-auf`): Adjectives and nouns with a fixed preposition and its case.
- `nominalisierung` (`de-b2-b`, unit `nominalisierung`): Nouns from verbs and adjectives; clause and phrase turned into each other.
- `indem-sodass` (`de-b2-c`, unit `indem-sodass`): Clauses of manner and result: indem, dadurch dass, sodass, ohne dass, anstatt dass.
- `zwar-aber` (`de-b2-c`, unit `zwar-aber`): Concession and contrast, each with the word order it asks for.
- `nomen-verb` (`de-b2-c`, unit `nomen-verb`): Fixed noun and verb pairs, with the plain verb each stands for.
- `satzbau` (`de-b2-c`, unit `satzbau`): The order in the middle of a sentence; first place; nicht.
- `formeller-brief` (`de-b2-c`, unit `formeller-brief`): A formal letter's parts and set phrases; titles in Austria; words that tie a text together.
- `oesterreichisch` (`de-b2-c`, unit `oesterreichisch`): What Austria's standard says otherwise, in words and grammar; spoken forms to recognise.

### English A2

**`en-a2-a`** (units 1–6)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 1 | `imperatives` | Sit down! Don't run! Let's go! | The base form, don't, let's; please. A1 left it out, so A2 opens with it. | **imperatives** | – |
| 2 | `past-regular` | I walked, she stayed | -ed and its spelling (stayed, stopped, carried), its three sounds; yesterday, last week, ago. | past-simple | – |
| 3 | `past-irregular` | I went, I saw, I took | The thirty or so irregular pasts of daily life, one form for every person. | past-simple | – |
| 4 | `did-you` | Did you go? I didn't. | did and didn't with the base form (never didn't went); Where did you go? Who saw it? | past-simple, questions-do | – |
| 5 | `past-continuous` | I was walking when it started | was and were + -ing; what was going on when something happened; while. | **past-continuous**, past-simple, present-continuous | – |
| 6 | `because-so` | Because it rained, so we stayed | and, but, or, because, so, when, before, after, then, joining what A1 and this part have said. | **linking-words**, past-simple | – |

**`en-a2-b`** (units 7–12)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 7 | `some-and-any` | Some bread, any milk | Countable and uncountable; some in statements and offers, any in questions and negatives; no article before what can't be counted. | some-any, articles | – |
| 8 | `how-much` | How much? How many? A few, a little | much and many, a lot of, a few and a little, how much and how many. | some-any, plurals | – |
| 9 | `comparatives` | Bigger, better, more useful | -er and more, the spelling (bigger, happier), good and bad, than; never more bigger. | **comparatives** | – |
| 10 | `superlatives` | The biggest, the best | the -est and the most; best, worst; in the village, of the three. | **comparatives**, articles | – |
| 11 | `adverbs` | Quickly, carefully, well | -ly and its spelling; well, fast, hard; an adjective for a thing, an adverb for what is done; where each goes. | **adverbs**, word-order | – |
| 12 | `someone-anything` | Someone, anything, nothing | some-, any-, no-, every- with -one, -thing, -where; one negative only (I saw nothing, I didn't see anything). | **indefinite-pronouns**, some-any | – |

**`en-a2-c`** (units 13–18)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 13 | `going-to` | I'm going to walk; I'm meeting Jack | going to for a plan or what you can see coming; the present continuous for what is arranged. | future, present-continuous | – |
| 14 | `will` | I'll help you, it'll rain | will and won't: offers, promises, what you decide now, what you think will happen; shall I? | future | – |
| 15 | `have-to` | I have to, you must, you should | have to, must, mustn't against don't have to; should for advice. | **must-have-to**, can | – |
| 16 | `would-like` | I'd like to go, I enjoy walking | want to, would like to, Could you …?; like, love, enjoy, hate + -ing. | **would-like** | – |
| 17 | `have-you-ever` | Have you ever seen a raven? | have + the participle; ever, never, just, already, yet; set against the past simple, as British English keeps them apart. | present-perfect, past-simple | – |
| 18 | `if-it-rains` | If it rains, I'll stay | The zero and first conditional; the present after if and when, will in the other half. | **first-conditional**, future | – |

New pages of English A2 (9):

- `imperatives` (`en-a2-a`, unit `imperatives`): The base form, don't, let's, please.
- `past-continuous` (`en-a2-a`, unit `past-continuous`): was and were + -ing; with when and while.
- `linking-words` (`en-a2-a`, unit `because-so`): and, but, or, because, so, when, before, after, then.
- `comparatives` (`en-a2-b`, unit `comparatives`): Comparatives and superlatives: forms, spelling, than, the.
- `adverbs` (`en-a2-b`, unit `adverbs`): Adverbs of manner: forms, the irregular ones, where they go.
- `indefinite-pronouns` (`en-a2-b`, unit `someone-anything`): some-, any-, no-, every- words; one negative only.
- `must-have-to` (`en-a2-c`, unit `have-to`): have to, must, mustn't, don't have to, should.
- `would-like` (`en-a2-c`, unit `would-like`): want to, would like to, polite asking; like and enjoy + -ing.
- `first-conditional` (`en-a2-c`, unit `if-it-rains`): The zero and first conditional; when.

### English B1

**`en-b1-a`** (units 1–6)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 1 | `for-since` | I've lived here for ten years | The present perfect for what still goes on; for and since; How long?; set against the past simple. | **for-since**, present-perfect | – |
| 2 | `been-waiting` | I've been waiting for an hour | have been + -ing; the activity against the result; verbs that don't take it. | **present-perfect-continuous**, present-perfect | – |
| 3 | `past-perfect` | The bus had already gone | had + the participle; the one of two past things that came first; 'd; by the time. | **past-perfect**, past-simple | – |
| 4 | `used-to` | I used to live in Leeds | used to + the base form, didn't use to; what was so and isn't now. | **used-to**, past-simple | – |
| 5 | `future-forms` | It'll rain, the bus leaves at nine | will against going to for what is likely; the present for a timetable; the future continuous (This time tomorrow I'll be walking). | **future-continuous**, future | – |
| 6 | `question-tags` | It's cold, isn't it? | Tags with every tense; So do I, Neither do I; short answers. | **question-tags**, to-be | – |

**`en-b1-b`** (units 7–12)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 7 | `who-which` | The man who lives next door | who, which, that, where, whose; defining clauses; leaving out that when it is the object. | **relative-clauses** | – |
| 8 | `passive` | The bread is baked here | be + the participle in the present and the past; by; can be done, as B1 Preliminary lists it. | **passive**, past-simple | – |
| 9 | `verb-patterns` | I decided to go, I enjoy walking | Verbs with to, with -ing, with both; -ing after a preposition (good at walking); tell, ask, want someone to do. | **verb-patterns** | would-like |
| 10 | `phrasal-verbs` | Get up, look after, give up | The everyday ones; those that part and those that don't; the pronoun in between (turn it off). | **phrasal-verbs**, object-pronouns | – |
| 11 | `too-enough` | Too cold, warm enough, as good as | too, enough, very, quite, really, a bit; too cold to swim, warm enough to swim; as … as, not as … as. | **too-enough** | comparatives |
| 12 | `myself` | I did it myself; we know each other | myself, yourself, themselves; on my own, by myself; each other; one and ones. | **reflexive-pronouns**, object-pronouns | – |

**`en-b1-c`** (units 13–18)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 13 | `if-i-had` | If I had a boat, I'd row across | The second conditional: the past after if, would in the other half; If I were you; never would after if. | **second-conditional** | first-conditional |
| 14 | `might-must` | It might rain; it must be Jack | may, might, could for what is possible; must and can't for what you work out; probably. | **might-must** | must-have-to |
| 15 | `if-i-had-known` | If I'd known, I'd have come | The third conditional; the two 'd (had and would); would have said as people say it. | **third-conditional**, **second-conditional** | past-perfect |
| 16 | `although-however` | Although it rained, despite the rain | although, even though, however, despite, in spite of; so that, in order to; because of; as a result. | **contrast-linkers** | linking-words |
| 17 | `reported-speech` | She said she was tired | say and tell; the tense one step back; here, there, today, the next day; She told me to wait. | **reported-speech**, past-simple | – |
| 18 | `reported-questions` | He asked where I lived | asked if and asked where, the word order of a statement; Do you know where it is? | **reported-questions**, **reported-speech**, questions-do | – |

New pages of English B1 (18):

- `for-since` (`en-b1-a`, unit `for-since`): for against since, How long?, and the present perfect against the past simple.
- `present-perfect-continuous` (`en-b1-a`, unit `been-waiting`): Forms and use; verbs that stay simple.
- `past-perfect` (`en-b1-a`, unit `past-perfect`): Forms and use, by the time.
- `used-to` (`en-b1-a`, unit `used-to`): Forms, the negative and the question.
- `future-continuous` (`en-b1-a`, unit `future-forms`): The future continuous and how to choose among the ways to talk about the future.
- `question-tags` (`en-b1-a`, unit `question-tags`): Tags in every tense; so and neither; short answers.
- `relative-clauses` (`en-b1-b`, unit `who-which`): Defining clauses: who, which, that, where, whose; when the pronoun is left out.
- `passive` (`en-b1-b`, unit `passive`): be + the participle, present and past, with can; by.
- `verb-patterns` (`en-b1-b`, unit `verb-patterns`): Verbs with to, with -ing, with an object + to; -ing after prepositions.
- `phrasal-verbs` (`en-b1-b`, unit `phrasal-verbs`): What a phrasal verb is; separable or not; the place of a pronoun.
- `too-enough` (`en-b1-b`, unit `too-enough`): too, enough, very, quite; too … to, enough to; as … as.
- `reflexive-pronouns` (`en-b1-b`, unit `myself`): myself and the rest, by myself, each other, one and ones.
- `second-conditional` (`en-b1-c`, unit `if-i-had`): Forms and use; If I were you.
- `might-must` (`en-b1-c`, unit `might-must`): Possibility and deduction: may, might, could, must, can't.
- `third-conditional` (`en-b1-c`, unit `if-i-had-known`): Forms and use; the two 'd.
- `contrast-linkers` (`en-b1-c`, unit `although-however`): although, even though, however, despite, in spite of; so that, in order to.
- `reported-speech` (`en-b1-c`, unit `reported-speech`): say and tell; the tenses, pronouns and time words that shift.
- `reported-questions` (`en-b1-c`, unit `reported-questions`): Reported and indirect questions; the word order.

### English B2

**`en-b2-a`** (units 1–6)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 1 | `narrative-tenses` | I had been walking for hours | The past perfect continuous; all the past tenses together in a story. | **narrative-tenses**, past-simple | past-perfect, past-continuous |
| 2 | `future-perfect` | By Friday I'll have finished | The future perfect, simple and continuous, with the future continuous; by, by then, by the time. | **future-perfect**, future | future-continuous |
| 3 | `be-used-to` | We would walk; I'm used to it | would for what happened again and again; be used to and get used to + -ing, against used to. | **would-used-to** | used-to |
| 4 | `should-have` | You should have told me | should have, could have, might have, must have, can't have, needn't have, with didn't need to. | **past-modals** | might-must |
| 5 | `i-wish` | I wish I had a dog; if only I'd known | wish and if only with the past, the past perfect and would. | **wish-if-only** | second-conditional, third-conditional |
| 6 | `mixed-conditionals` | If I'd listened, I'd be there now | Mixed conditionals; unless, as long as, provided that, in case. | **mixed-conditionals** | second-conditional, third-conditional |

**`en-b2-b`** (units 7–12)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 7 | `non-defining` | Mr Dixon, who lives by the beck | Clauses with commas, which can't be that; whose; the preposition (the man I spoke to, to whom); which for a whole sentence. | **relative-clauses-more** | relative-clauses |
| 8 | `participle-clauses` | Walking up the fell, I saw a raven | -ing and -ed clauses for a shorter sentence; a mill built in 1800; having finished; one subject for both halves. | **participle-clauses** | relative-clauses |
| 9 | `passive-more` | It has been repaired; it is said that | The passive in every tense and with modals; It is said that, He is thought to be. | **passive-more** | passive |
| 10 | `have-it-done` | I had my hair cut | have and get something done; set against doing it yourself. | **have-it-done** | passive |
| 11 | `reporting-verbs` | He advised me to wait | suggest, advise, warn, promise, admit, deny, refuse, each with its pattern. | **reporting-verbs** | reported-speech, verb-patterns |
| 12 | `both-neither` | Both, neither, each, all of them | both, either, neither, each, every, all, none of them; both … and, neither … nor. | **quantifiers**, some-any | – |

**`en-b2-c`** (units 13–18)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 13 | `cleft-sentences` | It was Jack who found her | It was … who, What I need is …; do for emphasis (I do like it); Never have I … to recognise. | **emphasis** | – |
| 14 | `so-such-whereas` | So cold that we stayed | so … that, such a … that; whereas, while, nevertheless, on the other hand; firstly, to sum up, as far as I'm concerned. | **result-linkers** | contrast-linkers |
| 15 | `phrasal-verbs-more` | Put up with, come across, run out of | Three-word verbs and the ones that mean more than their parts. | **phrasal-verbs-more** | phrasal-verbs |
| 16 | `word-building` | Happy, unhappy, happiness | Prefixes and suffixes; -ness, -ment, -tion, -ful, -less; families of words, as B2 First's word formation asks. | **word-building** | – |
| 17 | `collocations` | Make a decision, do the washing up | make, do, take, have with their nouns; heavy rain, strong tea; everyday British idioms. | **collocations** | – |
| 18 | `formal-informal` | Dear Sir or Madam; cheers, see you | A letter and an email, a request and a complaint; spoken British to recognise (gonna, gotta, innit), never marked wrong; British against American. | **formal-informal** | – |

New pages of English B2 (18):

- `narrative-tenses` (`en-b2-a`, unit `narrative-tenses`): The past perfect continuous; choosing among the past tenses in a story.
- `future-perfect` (`en-b2-a`, unit `future-perfect`): The future perfect, simple and continuous; by.
- `would-used-to` (`en-b2-a`, unit `be-used-to`): would for habits; be used to and get used to, against used to.
- `past-modals` (`en-b2-a`, unit `should-have`): should, could, might, must, can't, needn't + have and the participle.
- `wish-if-only` (`en-b2-a`, unit `i-wish`): wish and if only with each tense.
- `mixed-conditionals` (`en-b2-a`, unit `mixed-conditionals`): Mixed time in a condition; unless, as long as, provided, in case.
- `relative-clauses-more` (`en-b2-b`, unit `non-defining`): Clauses with commas; whose, whom; prepositions; which for a whole sentence.
- `participle-clauses` (`en-b2-b`, unit `participle-clauses`): -ing, -ed and having done in a shorter clause.
- `passive-more` (`en-b2-b`, unit `passive-more`): The passive in every tense and with modals; It is said that.
- `have-it-done` (`en-b2-b`, unit `have-it-done`): have and get something done.
- `reporting-verbs` (`en-b2-b`, unit `reporting-verbs`): Reporting verbs with their patterns.
- `quantifiers` (`en-b2-b`, unit `both-neither`): both, either, neither, each, every, all, none.
- `emphasis` (`en-b2-c`, unit `cleft-sentences`): It was … who, What … is, do for emphasis; inversion to recognise.
- `result-linkers` (`en-b2-c`, unit `so-such-whereas`): so and such … that; whereas, nevertheless; words that order an argument.
- `phrasal-verbs-more` (`en-b2-c`, unit `phrasal-verbs-more`): Three-word verbs; the common ones with a meaning of their own.
- `word-building` (`en-b2-c`, unit `word-building`): Common prefixes and suffixes and what they do.
- `collocations` (`en-b2-c`, unit `collocations`): make, do, take, have; adjective and noun pairs; British idioms.
- `formal-informal` (`en-b2-c`, unit `formal-informal`): Formal and informal writing; spoken forms to recognise; British against American.

### Italian B2

**`it-b2-a`** (units 1–6)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 1 | `congiuntivo-passato` | Penso che sia partito | abbia or sia + the participle, its agreement; after an opinion, a doubt or a feeling about what has happened. | **congiuntivo-passato**, congiuntivo, passato-prossimo | – |
| 2 | `congiuntivo-imperfetto` | Pensavo che fosse qui | -assi, -essi, -issi in every person (B1 had only se fossi, se avessi); fossi, dessi, stessi, facessi, dicessi; after a past or vorrei che. | **congiuntivo-imperfetto**, congiuntivo, periodo-ipotetico | – |
| 3 | `congiuntivo-trapassato` | Pensavo che fosse già partito | avessi or fossi + the participle, what came before a past; the Profilo asks for it only in part, so more to recognise than to make. | **congiuntivo-trapassato**, **congiuntivo-imperfetto**, trapassato | – |
| 4 | `concordanza-tempi` | Penso che venga, pensavo che venisse | The four tenses in one table: before, at the same time, after, for a main verb in the present and in the past. | **concordanza-tempi**, **congiuntivo-passato**, **congiuntivo-imperfetto**, **congiuntivo-trapassato** | – |
| 5 | `benche-purche` | Benché piova, purché tu venga | benché, sebbene, nonostante; purché, a condizione che, a patto che; affinché; a meno che (non); come se. | **congiunzioni-congiuntivo**, congiuntivo-espressioni, congiunzioni | – |
| 6 | `magari` | Magari venisse! Che sia lui? | The congiuntivo on its own: magari, almeno, se solo; non l'avessi mai detto; Che sia lui? | **congiuntivo-indipendente**, **congiuntivo-imperfetto** | – |

**`it-b2-b`** (units 7–12)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 7 | `condizionale-passato` | Sarei venuto, avrei mangiato | avrei or sarei + the participle; what would have been and regret; avrei dovuto, avrei potuto; the newspapers' conditional to recognise. | **condizionale-passato**, condizionale, passato-prossimo | – |
| 8 | `se-potessi` | Se potessi, verrei | The periodo ipotetico of what could be, in every person, with the whole imperfect congiuntivo; either half first; never se + the conditional. | periodo-ipotetico, condizionale | congiuntivo-imperfetto |
| 9 | `se-avessi-saputo` | Se l'avessi saputo, sarei venuto | The periodo ipotetico of what can't be any more; the mixed one (se avessi studiato, ora saprei); spoken se lo sapevo, venivo labelled, never wrong. | **periodo-ipotetico-irrealta**, periodo-ipotetico, **condizionale-passato** | congiuntivo-trapassato |
| 10 | `disse-che` | Disse che sarebbe tornato | The tenses that shift after a past (B1 stayed with ha detto che + the present), the future in the past (sarebbe tornato), oggi to quel giorno; mi chiese dove fosse. | **discorso-indiretto-passato**, discorso-indiretto, **condizionale-passato** | – |
| 11 | `raccontare` | Andò, è andato, va | The passato remoto used, not only recognised: its frequent verbs; against the passato prossimo by distance and region; the presente storico. | **raccontare**, passato-remoto, imperfetto-passato | – |
| 12 | `gerundio-passato` | Avendo finito, essendo arrivato | avendo or essendo + the participle; cause and time, with one subject; essendo in due for a reason; pur + the gerund. | **gerundio-passato**, gerundio | – |

**`it-b2-c`** (units 13–18)

| # | unit | title | teaches | pages it names | once merged, also |
|---|---|---|---|---|---|
| 13 | `infinito-passato` | Dopo aver mangiato, finita la cena | dopo + aver or esser + the participle; il mangiare, the infinitive as a noun; the participle alone (finita la cena) and as an adjective. | **infinito-passato**, **participio-assoluto**, infinito-preposizioni | – |
| 14 | `fare-lasciare` | Te lo faccio vedere, lasciami andare | fare and lasciare + the infinitive, the pronouns' place; farsi + the infinitive (mi sono fatto tagliare i capelli). | **fare-lasciare**, pronomi-combinati | – |
| 15 | `viene-aperto` | Viene aperto, va fatto, si vendono | venire + the participle (simple tenses only), andare + the participle for what must be done, the si passivante with its agreement. | **passivo-venire-andare**, passivo, si-impersonale | – |
| 16 | `il-quale` | Il quale, chi, chiunque, qualunque | il quale, la quale, dal quale; il cui; chi for the one who; chiunque, qualunque, qualsiasi + the congiuntivo. | **relativi-il-quale**, relativi-cui, indefiniti | – |
| 17 | `alterati` | Casetta, gattino, ragazzaccio, nasone | -ino, -etto, -ello, -uccio, -one, -accio; -mente; in-, s-, ri-; families of words (pane, panino, panetteria). | **alterati** | – |
| 18 | `connettivi` | Tuttavia, anzi, in effetti | tuttavia, anzi, in effetti, inoltre, invece; a causa di, in base a; il pane l'ho comprato, è Rosa che; a short polite letter (Gentile, La ringrazio). | **connettivi**, congiunzioni, tu-lei | – |

New pages of Italian B2 (18):

- `congiuntivo-passato` (`it-b2-a`, unit `congiuntivo-passato`): Forms, agreement, and when it follows a verb in the present.
- `congiuntivo-imperfetto` (`it-b2-a`, unit `congiuntivo-imperfetto`): Every person of every conjugation, the irregular ones, after a past and vorrei che.
- `congiuntivo-trapassato` (`it-b2-a`, unit `congiuntivo-trapassato`): Forms and what came before a past.
- `concordanza-tempi` (`it-b2-a`, unit `concordanza-tempi`): The table: main verb against subordinate, in the congiuntivo.
- `congiunzioni-congiuntivo` (`it-b2-a`, unit `benche-purche`): The conjunctions that ask for the congiuntivo, by what they mean, and come se.
- `congiuntivo-indipendente` (`it-b2-a`, unit `magari`): The congiuntivo in a clause of its own: wishes and doubts.
- `condizionale-passato` (`it-b2-b`, unit `condizionale-passato`): Forms, regret, and the future seen from the past.
- `periodo-ipotetico-irrealta` (`it-b2-b`, unit `se-avessi-saputo`): The if-sentence of what can't be any more, the mixed one, the spoken one.
- `discorso-indiretto-passato` (`it-b2-b`, unit `disse-che`): The tense and word shifts after a past; indirect questions.
- `raccontare` (`it-b2-b`, unit `raccontare`): Remoto, prossimo, imperfetto and presente storico in telling; distance and region.
- `gerundio-passato` (`it-b2-b`, unit `gerundio-passato`): Forms, cause and time; pur + the gerund.
- `infinito-passato` (`it-b2-c`, unit `infinito-passato`): dopo + the past infinitive; the infinitive as a noun.
- `participio-assoluto` (`it-b2-c`, unit `infinito-passato`): The past participle alone for time (finita la cena) and as an adjective.
- `fare-lasciare` (`it-b2-c`, unit `fare-lasciare`): fare and lasciare + the infinitive, the pronouns, farsi.
- `passivo-venire-andare` (`it-b2-c`, unit `viene-aperto`): venire and andare + the participle; the si passivante.
- `relativi-il-quale` (`it-b2-c`, unit `il-quale`): il quale and its forms, il cui, chi, chiunque, qualunque, qualsiasi.
- `alterati` (`it-b2-c`, unit `alterati`): Suffixes that make a word small, big, dear or bad; -mente; common prefixes.
- `connettivi` (`it-b2-c`, unit `connettivi`): Words that join a text; moving a word to the front; a polite letter's phrases.
## What the syllabi say, and where the outline departs

The sections named were read, unless marked. A checker of this list should start with the weak spots: **German B2 has no published grammar
list behind it**, and Italian's sources disagree on level.

### German

- [ÖIF Rahmencurriculum A2](https://www.integrationsfonds.at/fileadmin/user_upload/A2_Rahmencurriculum_OEIF.pdf), section 3.3
  (Fassung 2025-08-01): Austria's own list. All of it has a unit. One move: `damit` goes to B1's `um-zu`, beside um … zu.
- [ÖIF Rahmencurriculum B1](https://www.integrationsfonds.at/fileadmin/user_upload/OEIF_Rahmencurriculum_B1.pdf), section 3.3:
  all of it has a unit. Two moves: the indirect question with ob or a question word comes earlier, in A2's `dass-wenn-ob`;
  Partizip I comes later, in B2's `partizip-adjektiv`, as the Bozen syllabus has it.
- [ÖIF Rahmencurriculum B2](https://www.integrationsfonds.at/fileadmin/user_upload/B2_Rahmencurriculum_OEIF.pdf): no grammar
  list ("Grammatikvertiefung im Kontext").
- [Goethe-Zertifikat B2, Prüfungsziele](https://www.goethe.de/pro/relaunch/prf/de/Pruefungsziele_Testbeschreibung_B2.pdf),
  section 4.4: no list either; it points to *Profile deutsch* (2005, a paid CD-ROM, **not read**).
- [Goethe-Zertifikat A2 Wortliste](https://www.goethe.de/pro/relaunch/prf/de/Goethe-Zertifikat_A2_Wortliste.pdf) and the
  [Goethe/ÖSD B1 Wortliste](https://www.goethe.de/pro/relaunch/prf/de/Goethe-Zertifikat_B1_Wortliste.pdf): words only, no
  grammar. The B1 exam is one exam under both names.
- **ÖSD**: no grammar list found. The [ÖSD Zertifikat B2 folder](https://www.osd.at/wp-content/uploads/2024/02/ZB2_Folder.pdf)
  appeared in a search as a description of the exam's format and was **not opened**.
- [Free University of Bozen-Bolzano, Deutsch B2.2b](https://guide.unibz.it/assets/languages/language-courses/syllabi-single-modules/Syllabi-Deutsch/Syllabus_Deutsch-B2.2b_v2.pdf?vid=11):
  Partizip I and II as adjectives, substitutes for the passive, unreal conditions and comparisons.
- [Deutsche Grammatik 2.0, Übungsbuch Band 2](https://deutschegrammatik20.de/wp-content/Deutsche-Grammatik20-Uebungsbuch-Band-2-Inhaltsverzeichnis.pdf),
  its contents, not level-marked: subjective modal verbs, Funktionsverbgefüge, Konjunktiv I and II, the passive and its
  substitutes, participial attributes, nominalisation.
- **So German B2 is a judgement** built from those two and the CEFR's B2. `herr-kollege`, `der-bekannte`, `stolz-auf`,
  `satzbau` and `oesterreichisch` are in no list above: the first three carry the cases and endings on, as the brief asks,
  the last two are for writing and for Austria. Check the level against *Profile deutsch* if a copy is at hand.
- **Austrian standard**: ich bin gesessen, gestanden, gelegen with sein, from the footnote at
  [Duden, sitzen](https://www.duden.de/konjugation/sitzen) and an
  [Austrian school publisher's sheet](https://www.hpt.at/sites/default/files/schulbuch_plus_downloads/hpt_190.006_35_INFO-BOX-Erg%C3%A4nzung.pdf),
  both seen as search extracts only. The writer of `partizip-perfekt` should open them, and take its URLs into the unit's doc.

### English

- [Cambridge A2 Key handbook](https://www.cambridgeenglish.org/images/504505-a2-key-handbook-2020.pdf), "Inventory of
  grammatical areas", pp. 49–51.
- [Cambridge B1 Preliminary handbook](https://www.cambridgeenglish.org/Images/168150-b1-preliminary-teachers-handbook.pdf),
  the same, pp. 70–72.
- [Cambridge B2 First handbook](https://www.cambridgeenglish.org/pt/Images/167791-b2-first-handbook.pdf): no grammar list;
  its Use of English parts (word formation, Part 3) give `word-building`.
- [British Council and EAQUALS Core Inventory, posters, 2018 update](https://www.eaquals.org/wp-content/uploads/Core-Inventory-Posters-2018-update.pdf),
  grammar per level; the [Core Inventory itself](https://www.eaquals.org/wp-content/uploads/EAQUALS_British_Council_Core_Curriculum_April2011.pdf)
  was fetched and not read through. The posters' A2, B1 and B2 lists all have units.
- **Where they differ**: A2 Key lists the passive and relative pronouns at A2; the Core Inventory has the passive at B1 and
  doesn't name relatives before B2, so A2 stays lighter and both go to B1. The third conditional is B1 in the Core Inventory
  and not in B1 Preliminary: it stays in B1.
- **Moves**: A1 has no imperative, so A2 opens with it. Common phrasal verbs (Core A2) wait for B1's `phrasal-verbs`, which
  takes them all at once. should have and might have (Core B1) join can't have and needn't have (Core B2) in B2's
  `should-have`, as one unit.
- **Not in the B2 lists read**: `participle-clauses`, `cleft-sentences`, a judgement for reading and writing at B2. `quantifiers`
  and `have-it-done` come from B1 Preliminary's list, taken further.

### Italian

- [Profilo della lingua italiana, verbi, B2](https://www.unistrapg.it/profilo_lingua_italiana/site/gram_verbi_b2.html), from
  Perugia, the home of CELI. The site's certificate doesn't verify from this machine, so it was read in the
  [Internet Archive's copy of 2024-09-09](https://web.archive.org/web/2023/https://www.unistrapg.it/profilo_lingua_italiana/site/gram_verbi_b2.html).
  It has every tense of the congiuntivo with its concordance, the periodo ipotetico of possibility and of irrealtà, the past
  conditional with the future in the past, the gerundio passato, the past infinitive, fare and lasciare, the participle, the
  passive with venire. The congiuntivo trapassato it asks for "only in part", hence `congiuntivo-trapassato`'s note.
- [Sillabo grammaticale B2 in *Percorso CILS UNO B2*](https://www.ornimieditions.com/pdf/SPECIMEN%20CILS%20DUE%20B2.pdf), p. 29:
  a publisher's book for CILS, **not Siena's own document**, which wasn't found. It agrees with the Profilo, and adds the
  wish on its own (`magari`), the connectives and prepositional phrases (`connettivi`), the passato remoto against the
  prossimo (`raccontare`), and the passive to recognise only.
- [PLIDA sillabo (Società Dante Alighieri)](https://dante-limoges.fr/images/stories/pdf/Sillabo.pdf), sections 2.8 and 2.9: **disagrees on
  level**. It keeps the congiuntivo imperfetto, passato and trapassato, the past conditional, the periodo ipotetico with the
  congiuntivo, il quale and the suffixes for C1. The brief names CILS and CELI, and the outline follows them; a writer who
  finds a unit heavy has this reason. CELI 3's own syllabus wasn't found.
- **What B1 left out**, from the outline and COURSE.md: every congiuntivo but the present and the se fossi, se avessi of
  `periodo-ipotetico`; the irrealtà; the past conditional; reported speech after a past; the passato remoto beyond recognising.
- **For the lead**: the outline keeps the course's audience, a child, as its other levels do, though the brief doesn't say
  so for B2. No child of seven needs B2 soon, and `connettivi`'s polite letter is the least childlike point: a letter to the
  mayor would do.

## What an outline does to the app

Nothing in the school's look changes, so no emulator QA, as the brief allows.

- **A level of planned units shows as German's A1 did**: its units only in the level's folded list, "📝 Kmalu · Coming",
  never a row (`School.reveal`, `SchoolRevealTest`); once nothing written is left, the teacher is preparing the next lessons
  (`School.preparing`).
- **Levels open sooner than they will later, in German and English only.** A level with nothing written holds nothing back
  (`School.levelDone`), so B1 and B2 open on the school's upgrades alone, each with a chronicle line, and stay open
  (`School.opened`), also once their units are written. The order is free anyway. Jan's village is Slovene and Teo's Italian,
  so neither meets this.
- **Italian**: B2 was already in the outline, empty, so it opens exactly as before and shows 18 planned units in its folded
  list. The fields are the ones B1's planned outline already uses; no 0.2.698 build was run.
- **Prints**: a planned unit has no sheet and a level with no written unit no booklet (`Prints.sheet`, `Prints.booklet`,
  the bridge's `prints.ts`).
- **The tutor**: `get_course` lists 54 more planned rows in German and English and 18 in Italian; nothing else.
- **For the writers, not done here**: COURSE.md's "The German course" and "The English course" still say the outline is A1.

## Checks

The lead's `check-outline-lang.ts` for de, en, it (schema ok, 72 units each, no lacking page, no duplicate id), tsc, the
smoke with this branch's ports, `tutor-export --check`, the Python tests, and the Android suite through `lani-slot.sh`
including `SchoolBoardTest` and `SchoolRevealTest`. A script in the scratchpad checked what no test does: 18 units a level,
six a part, no id collision, at most four pages a unit, one owner a page, every new page named, every title's letters on
the board.

## For the native check

All of it is machine-written: 126 titles, each in four languages.

- **German** (Austrian standard first): every `de` title, and heuer, Jänner, ich bin gesessen, Frau Magister; which spoken
  Carinthian belongs in `oesterreichisch`.
- **English** (British first): every `en` title, and which spoken forms belong in `formal-informal`.
- **Italian**: every `it` title of B2, and the spoken se lo sapevo, venivo.
- **The bases**, the grammar words above all: `Stato o moto` and `Il passato breve` (it); `Kratki preteklik`, `Pisni
  preteklik`, `Dati narediti`, `Konjunktiv predpreteklika`, `Pretekli gerundij` (sl); `Klein und groß gemacht`, `Verben des
  Sagens`, `Frageanhängsel` (de).

## Voice

Nothing to voice here: the voice build reads written units, not the outline's titles. 0 characters, no credits spent. For
planning, from A1's figures (course-de-a1-c.md, course-en-a1-c.md): a German level is about 16,000 characters in the
teacher's voice and about 29,000 per base for the explanations; an English level about 26,000 and about 31,000. Seven
levels would then be about 150,000 for the teachers alone, likely more, as B2's sentences are longer. That is Jan's to decide.

## What's left

The 126 units and their 110 new pages (47 German, 45 English, 18 Italian). After the parts are merged, the "once merged,
also" pass. A check of German B2 against *Profile deutsch*, and of Italian against CILS's and CELI's own syllabi if they can
be had.
