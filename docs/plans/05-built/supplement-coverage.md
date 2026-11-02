# Built: a tap means the older German and English content's words (supplement-coverage, 2026-11-02)

Wave C, section 7 (followups-8.md). A phone has no German or English dictionary: a tapped word means something only
when a word pack, a villager's name or the language's supplement (`companion/lexicon/de.extra.json`, `en.extra.json`)
knows it. The newer stories and readings of Kärnten and the Lake District came with their words in the supplement
(stories-kaernten.md, stories-lakeland.md). The older content didn't: Kärnten's ten older stories, its chest readings,
its scenes, project steps, arrivals and sky, and the same in the Lake District, the function words too (the, a, and,
of). This round gives them their meanings, in the supplements' own format, written for the learner.

All of it is machine-written and not reviewed (both files say so in their meta). The list for a native check is at the
end.

## Already done before this round

Nothing of section 7 was. The two model rounds had added only their own texts' words: before this round a tap met
nothing for 2,923 different German words (43.7 % of the different words known, 87.9 % of the words met) and 2,463
English ones (42.7 % and 52.5 %).

## What

### How it was measured

`companion/bin/lexicon-build --coverage --lang de|en` counts every word of the language's content (the culture, its
packs, scenes, scenarios, modules and the grammar book's pages). It split a word at its apostrophe and hyphen, where
the app taps it whole (`WordTokens`: "don't", "it's", "geht's", "foot-and-mouth" are one tap each), and so counted "t",
"s", "don", "isn" as words no entry could ever mean. It counts the words as the app taps them now (`TAPPED`), the
villagers' possessives as names (below), and ä, ö, ü as vowels when it sorts the unknown words into kinds (hält, süß
were "sounds"). Numbers below are counted that way, before and after; the old count's are given where they differ.

### German (`companion/lexicon/de.extra.json`): 880 → 2,670 entries

1,790 new entries, 337 extended, 3,753 forms added:

| Part of speech | New | Examples |
|---|---:|---|
| noun | 816 | Hütte, Kasermandl, Kraxenträger, Heidensterz, Lärchenschindel, Laurentiustränen, Gluckhenne |
| verb | 434 | schmecken, riechen, hängen, kraxeln, kennenlernen, übernehmen, herunterklettern |
| adjective | 200 | fertig, grau, gemütlich, schlampig, rot-weiß-rot, helllicht |
| name | 102 | Petzen (Peca), Hochosterwitz, Luschariberg, Hermagor (Šmohor), Aldebaran, the Perseids |
| adverb | 86 | vorbei, herunter, drinnen, überall, freilich, eh |
| interjection | 64 | hm, hä, juhu, oje, jessas, psst, brr, Hau ruck, pfiat di |
| other | 92 | the grammar book's endings (-en, -st, ge-, -erl), numbers (zweitausend, II, XII), zwischen, bevor |

- **The forms the content uses, from each word's paradigm.** The entries were written as a table (lemma, part of
  speech, gender, a paradigm code, the three meanings), and a generator made each word's forms with their grammar in
  the file's style ("present, er/sie/es", "past participle, as an adjective", "dative plural"), keeping only the forms
  the content has: the weak verbs by their ending (with the e of zeichnet, rechnet, arbeitet), 110 strong and mixed
  verbs and their prefixed kin (verstehen, bekommen, übernehmen), the separable verbs' joined forms (aufgegangen,
  kennenzulernen, the verb at a clause's end), the nouns' plurals, dative plurals and genitives (eines Tages), the
  adjectives' endings and comparison (größer, am längsten). A bare imperative that is another, commoner word isn't made
  (mal, Kreis, Staub, Wein, Teil, weh: werden's imperative is werde, not wir).
- **The existing entries got their forms too**: sagen had no sagst or gesagt, finden no fanden, Land no Ländern, and
  some had no lemma among their forms (still, lieb, wissen, müssen, tun). The earlier rounds' grammar labels written
  as forms are gone (18 keys: "present, wir/sie", "past participle", "ihr" under gehen and sagen, which made a tap on
  ihr show gehen).
- **The packs' words where the pack lacks a form** (166 entries): Hand has no Händen, fangen no fängt, dunkel no
  dunklen. Each entry has the pack's own meanings, so a tap finds the pack's word (the pack's card, the pack's prompt).
- **The irregular verbs' missing forms**: wäre, wär's, würde, wirst, werde, mag, magst, darfst, dürft, könnten, hab's,
  sag's, käme, ginge, bräuchte, and the Carinthian song's "Is schon still uman See".
- **The interjections and sounds with a meaning** get one (hm: thinking; psst: be quiet; brr: it's cold; Hau ruck:
  heave-ho; jessas: goodness), their drawn-out spellings as forms (juhuu, brrr, pssst, tuuut).
- **The Slovene words the German lines quote** (48: hvala, dober dan, lahko noč, kozolec, kranjska sivka, počasi se
  daleč pride, the valleys Rož, Podjuna, Zila) say they are Slovene in every base: "slovensko: hvala", "Slovene for
  thank you", "in sloveno: grazie".
- **Austrian first**, as the file is: Jänner beside Januar, Gelse, Zwetschke, Schwammerl, Palatschinke, Zuckerl,
  Verlängerter, Stiegenhaus, eh, freilich, bissl.

### English (`companion/lexicon/en.extra.json`)

442 → 2,227 entries: 1,785 new, 122 older ones extended with 165 forms (better, best, sat, written, getting,
mother's). Written by this round's helper agent with the same generator (an English paradigm: -s, -ed, -ing with their
spelling rules, the irregular verbs' past and participle, the nouns' plurals and possessives, -er and -est).

| Part of speech | New | Examples |
|---|---:|---|
| noun | 769 | yarn, fell, beck, tarn, hob, cuckoo, bobbin, bloomery, rushbearing |
| verb | 289 | thunder, whistle, starve, skim, shear, count |
| adjective | 224 | common, bonny, daft, safe, wee |
| name | 176 | Penrith, Kendal, Dunmail, Skiddaw, Castlerigg, Lyth, Excalibur, Bedivere |
| interjection | 88 | oh, eh, wow, hey, shh, brr, aye, hiya, tut-tut |
| function words | 168 | the, a/an, and, of, in, that, there, what, my, their, some, every, than, because (determiners, pronouns, prepositions, conjunctions, adverbs) |
| other | 71 | numbers (fifty, eleven, V, XII), the endings (-s, -ed, -ing, -th, -teen, -ty), to before a verb |

- **The function words**: the most frequent words of the content (the 11,206 times, a 4,510, you, I, and, in, on, of)
  meant nothing on a tap. Slovene has no articles: "the" is "(določni člen: ta, tisti)", as the German der's is.
- **The contractions as the app taps them** (one word with its apostrophe), under the word they start with: I'm, I've,
  I'll, I'd under I; it's under it ("it is, it has"); that's, there's, what's, who's, here's; you're, we'll, they've,
  she's under their pronouns; let's under let; don't, doesn't, didn't under do; won't under will; can't, wouldn't,
  mustn't, haven't, should've under their verbs; nobody's, everyone's, the sun's ("the sun is").
- **The dialect** with its standard meaning, said to be Cumbrian or northern: lass, lad, bonny, aye, marra, tatie, gowk,
  and the sheep counting (yan, tyan, tethera, methera, pimp …: "tri pri štetju ovac (staro kumbrijsko)").
- **The interjections and sounds with a meaning** (shh: be quiet; brr: it's cold; tut-tut), their drawn-out spellings
  as forms (yesss, pleeease, yaaay, brrr).
- **The grammar book's terms and endings** (verb, noun, plural, irregular, vowel, consonant; -s, -ing, -teen, -ty), the
  night sky's stars, constellations and showers, the festivals (Mothering Sunday, Boxing Day, Shrove Tuesday).
- **The packs' words where the pack lacks a form** (193 entries: possessives, plurals), with the pack's meanings, as in
  German. Another language's pack that means a new noun alike gives it its article (church: die Kirche, la chiesa).
- The villagers' possessives (Jack's, Maggie's, Wilf's) need no entry: see below.

### The printed sheets keep their meanings (companion/prints/sheet.ts)

The sheets read the same supplements, and take the supplement's entry of a word before a pack's, so the new entries
could change what a course's sheet says. Printing every course in every base before and after (`school-prints --level
all --html-only`) showed they did: English warm printed ogreti for topel, book rezervirati for knjiga, hammer its verb,
fish and farm fell off their lists, nouns lost the article that tells their gender (church: Kirche for die Kirche),
German gleich printed enak for takoj. Fixed in the print's rules, and in the data:

- an English word listed without its "to" is no verb when a pack has the word, as an Italian word without its article
  is no noun, unless the entry names its "to …" (the course's own to walk, to rain);
- an entry that only repeats a pack's meaning, there for the forms the pack lacks, comes after another entry of the
  word (rain's noun, the pack's dež, after the course's to rain);
- an entry found only by its lemma comes after one that lists the form (the adjective gleich lists gleiche and gleichen
  and leaves its bare form to the adverb, takoj);
- a new entry of a pack's word has the pack's meanings, in the print's order of the packs.

After them the Slovene and Italian courses' sheets are the same in every base, and the German and English ones differ
only in what they gain: the articles sheet's milk and sun, which had no meaning there; the particle mal for "(das Mal)";
fertig's second sense; price's "Preis, Kaufpreis" in German (prize is Preis).

### A villager's possessive is theirs (bridge/src/lexicon.ts)

A tap on "Stankos" (Stankos Honig) or "Wilf's" found nothing: the lookup knew a villager by their name and, in
Slovene, by its declined forms. Now a capitalized German genitive in -s and an English 's are the villager's too
(`possessives`, also what `lexicon-build --coverage` counts as names). A name ending in s, x or z has only an
apostrophe in German, which the tap leaves off, so it is the name itself.

## Results

`lexicon-build --coverage` (the content as above), a tap means the word … :

| | German before | German after | English before | English after |
|---|---:|---:|---:|---:|
| of the words met | 87.9 % | **99.9 %** | 52.5 % | **99.8 %** |
| of the different words | 43.7 % | **99.0 %** | 42.7 % | **98.1 %** |
| in the learner's base (sl, it, de/en), of the words met | 86.8 %¹ | 98.8 % | 49.8 %¹ | 98.4 % |
| different words left | 2,923 | 50 | 2,463 | 81 |

¹ The old count's, which split words at their apostrophes; the old count's other rows: German 87.8 % and 43.7 %,
English 51.1 % and 44.8 % before. The "in the base" rows leave out the villagers' names (a person, not a word's
meaning).

What is left in German: the wrong forms the turns offer on purpose (37: gebaucht, gesieht, fangst, Nusse, Pfahle,
ausgetrinkt, Vorrate, Fledermause …), single letters spelt out (M, B, C, K, W, H, i, a, u; Slovene č, š, ž), "zu-was"
(a learner's half-understood echo), "se" (in the Slovene saying) and an emoji's variation selector.

What is left in English: 59 wrong forms the turns offer on purpose (186 words met: sheeps, mouses, runned, childs,
goed, catched, o'clocks …), 20 letters spelt out or pieces of a word (x, b, ch, sh, thir, ged), "Dixons" (a villager
family's plural, which the possessive rule doesn't cover) and the variation selector.

### Every prompt its own

A word looked up becomes a card whose prompt is its meanings in the learner's base, without their notes in brackets
(`Words.meaning`, card.py's `meaning()`). No new entry's prompt is another word's in the same base, against the
language's packs (the course's and the culture's) and the supplement's other entries (a scratch check, card.py's
comparison of the whole prompt: articles and "to" left out, brackets stripped). Where a base has one word for two, the
meaning says which, outside the brackets: Eimer "vedro, kangla" beside the pack's Kübel "vedro"; Gelse "komar" beside
Mücke "mušica, komar"; Zunge "jezik v ustih" beside Sprache "jezik"; Fernglas "dvogled" beside the tower's Fernrohr;
Januar "the month of January" beside the pack's Jänner; a feminine noun "woman goalkeeper" beside Tormann. An entry for
a pack's word has the pack's meaning: its card is the pack's. The old entries and the packs keep their 195 shared
prompts (German) and about 100 (English), listed by the scratch check; they are qa-repair's prompt-clash baseline to
decide. Single meanings shared with another word stay where they are ordinary synonyms or homographs of the base (med:
between and honey, da: that and yes).

## Tests

Main had not moved past this branch's base (21aa4e9b), so the merge had nothing to bring in.

- **Bridge**: `bunx tsc --noEmit -p .` clean. The smoke (`LANI_SMOKE_PORT=9770`, `LANI_SMOKE_DRAMA3_PORT=9788`): 3,471
  checks, none failed. New checks: the German supplement without Wiktionary (zeichnet is zeichnen, Stöcke Stock's plural,
  Hütte koča and la capanna, hvala a Slovene word in each base, mal the particle; ihr no form of gehen or sagen, no form
  a grammar label); a villager's possessive (Stankos, Wilf's; a small stankos isn't); the sheets' meanings (warm topel,
  book knjiga, hammer kladivo, rain deževati, church die Kirche, gleich takoj). The file-by-file check that every
  supplement validates covers both files: `validateExtra` leaves none of the 2,670 and 2,227 entries out.
- `tutor-export --check`: 89 files match the bridge (54 tools, 32 guide topics).
- **Python**: `python3 -m unittest discover -s tests` 251 tests OK; `-s companion/tests` 142 OK (11 skipped).
- **Android**: no app code changed. The full unit suite (`testDebugUnitTest` through lani-slot.sh) had not finished when this was written; its result is in the round's report.
- **Prints**: `school-prints --level all --html-only` for every course in every base, before and after: see "The printed
  sheets keep their meanings".

## Emulator QA

Through `qa-slot` (slot 1) on synthetic learners from Slovene (`make-learner.ts --target de|en --base sl --name Ana`,
`LANI_CULTURE=kaernten LANI_QA_SETUP_PAIR=de-sl` and `lakeland`, `en-sl`). The dev bridge had no Wiktionary, as a phone
has none. The new step `supplement-words` (only when named) opens each card as a tap in its line does (QA's hook
"word:") and checks the lemma and the Slovene meaning in GET /lookup and on the card; each is flagged "strojno napisano,
ni pregledano". A tap is still one tap; what it shows changed. Screenshots: scratchpad `ux-supplement-coverage/de`, `en`.

| Word | Card | Before |
|---|---|---|
| zeichnet | zeichnen, "risati" (present, er/sie/es) | not in the dictionary |
| Hütte | Hütte, "koča" | not in the dictionary |
| hängt | hängen, "viseti; obesiti" | not in the dictionary |
| Kasermandl | "Kasermandl (planšarski škrat)" | not in the dictionary |
| hvala | "slovensko: hvala" | not in the dictionary |
| Stankos | Imker Stanko, the beekeeper | not in the dictionary |
| the | "(določni člen: ta, tisti)" | not in the dictionary |
| don't | do, "delati; pomožni glagol …" (negative, do not) | not in the dictionary |
| lass | "dekle, punca (severnoangleško)" | not in the dictionary |
| tethera | "tri pri štetju ovac (staro kumbrijsko)" | not in the dictionary |
| went | go, "iti" (past) | known already (stories-lakeland) |
| Wilf's | Old Wilf, the storyteller | not in the dictionary |

## How to use

- A learner of German or English, on a phone without Wiktionary: tap any word in a scene, a story, a reading, a
  project step or a villager's line; the card shows its lemma, its grammar and its meaning in the learner's base,
  flagged machine-written (`gloss_via: claude`), and "➕" adds it to their words.
- To add words later: append entries to the file by hand, in its format (lemma, pos, gender, gloss, forms with their
  grammar, meanings in the other bases); `companion/bin/lexicon-build --coverage --lang de --all` lists the words no
  entry knows, the most frequent first. Keep each new meaning's prompt its own in each base.
- QA: `LANI_DEV_DATA=$(cd companion/bridge && bun test/fixtures/make-learner.ts /tmp/de-learner/data --name Ana --target de --base sl)
  LANI_CULTURE=kaernten LANI_QA_SETUP_PAIR=de-sl companion/bin/qa-slot --no-town --steps supplement-words` (and
  lakeland, en-sl): QA's hook "word:" opens each card as a tap in its line does.

## Voice

Nothing here is voiced: the supplement is read on the word card and the printed sheets. No ElevenLabs credits were
spent.

## Native check

Everything here: both files' new entries, about 3,600 in all. What most needs a native eye:

**Slovene (Jan's base, for Jan's wife):**
- the quoted Slovene in the German lines (hvala, kozolec, kranjska sivka, Rož, Podjuna, Zila) is given as written
  there; the meanings around them: "Pehtrina noč" (Perchtennacht), "krendelj" (Krendel), "zbogom, adijo" (pfiat di),
  "Bog plačaj" (Vergelt's Gott), "teknit" is now "tekniti" (schmecken);
- the places' Slovene names: Marija Lukava (Maria Luggau), Lesna dolina (Lesachtal), Šmohor (Hermagor), Podklošter
  (Arnoldstein), Šentvid ob Glini (St. Veit), Ostrovica (Hochosterwitz), Veliki Klek, Predarlska;
- the stars and constellations: Volar, Voznik, Dvojčka, Gostosevci, Kasiopeja, Betelgeza, Regul, Prokion, Laurentius's
  "solze svetega Lovrenca";
- English's articles ("(določni člen: ta, tisti)", "(nedoločni člen: en, neki)"), the dialect's "(severnoangleško)",
  the sheep counting, the animal sounds (kruli kruli, kokokodak), bluebell ("modra zvončnica" is likely wrong for the
  wild hyacinth), multure ("mlinarina"), hogback ("grbasti nagrobnik"), bloomery ("starinska talilna peč"), toucher
  (bowls), skim stones ("žabice spuščati"), rushbearing ("nošenje ločja"), grandparent ("stari starš"), the pronoun one;
- register and naturalness are hers; nothing here is graded.

**German (a base of the English, and the German learned):**
- Austrian first: Gelse, Zwetschke, Schwammerl, Zuckerl, Bröserl, Verlängerter, bissl, eh; the Carinthian song's "Is
  schon still uman See" (is, uman);
- English's "sie – eine Frau, ein Mädchen" and "sie – mehrere" (the dash keeps two prompts apart), "Mahllohn"
  (multure), "Rennofen" (bloomery), "flitschen" (skim stones), "Faschingsdienstag" for Shrove Tuesday (Austrian and
  southern);
- the generated forms' grammar labels ("present, du (in a clause)", "past participle, as an adjective").

**Italian (a base):**
- the nouns' articles in the German file's meanings (il, la, l'), the disambiguations made for unique prompts ("la
  lingua in bocca" for Zunge, "il nipote di nonno", "la margherita dei prati"), the festivals and recipes
  ("smielare", "la carbonaia", "il gallinaccio"), the stars (il Boote, l'Auriga).

**English (a base of the German):** the short glosses made unique ("pail, bucket", "the month of January", "woman
goalkeeper", "it snows (to snow)").

## What's left

- **The native check** (above).
- **A tap on a bare word with two entries**: gleich shows only the adverb now, as the adjective leaves it the bare form,
  and the same word's other readings come in the file's order (Mal the noun before mal the particle).
- **The prompts the old entries and the packs share** (195 German, about 100 English): qa-repair's prompt-clash check
  takes them as its baseline.
- **Forms of content written later** fall back on the lookup's "did you mean" (ENDINGS); a later round adds entries by
  hand, or with `lexicon-build --coverage --all` listing what's missing.
- **A family's plural** (the Dixons) and the German genitive of a name ending in s, x or z (written with an apostrophe
  the tap leaves off) don't find their villager.
