# Built: challenges, the car and numbers in German (practice-de, 2026-11-03)

Wave E, batch 1, section 13 (followups-14.md, "practice-de"). Kärnten's learner of German (from Slovene, English or Italian,
an adult) had no curated module (Home's challenges said "None yet", a run "No grammar drills yet"), no car drill and no numbers
module. Each now works in German, A1 to B2, built on what practice-it.md made language-neutral: data files, one
`NumberTongue`, a row in the smoke's table.

Everything German, Slovene and Italian written here is machine-written (Claude) and not reviewed by a native speaker; each file
says so in its `review`.

## For a native check (first)

German (Austrian standard first), then each base. Ask a native speaker of Austrian German, then of Slovene and of Italian.

**Numbers module (data/NumberTongue.kt, the numbers.de.* strings):**
- Right: hundert/einhundert, tausend/eintausend, hundertundeins, tausendundeins, a year's hundreds for 1100–1999
  (neunzehnhundertneunundachtzig as the answer to 1989), zwo for 2, siebente beside siebte, Januar and Feber beside Jänner
  and Februar, um ein Uhr, fünf Uhr dreißig, siebzehn Uhr dreißig, zu Mittag, um Mitternacht (for 12:00).
- The course's form shown first: hundertzwanzig, tausendeinhundert (inside a number einhundert), zweitausendsechsundzwanzig.
- Wrong, as the learner's own words: einsundzwanzig, sechszehn, siebenzig, siebenzehn; for a date einste, einte, dreite,
  dreiste, achtte, zehnste (-ste below 20), zwanzigte (-te from 20).
- Almost: an ending typed wrong after der (fünfzehnten), apart (sieben und zwanzig), ae/oe/ue/ss for ä/ö/ü/ß.
- The frame "Heute ist der ___ Mai. (15.)", Slovene's tip "kot slovensko sedem-in-dvajset", the half's why
  ("halb šteje k naslednji uri, kot slovenski pol").

**Modules (companion/modules/de, 28 files):**
- Austrian forms chosen: ich bin gesessen / gestanden (both auxiliaries right in the gaps and the speaking), die Semmel,
  Schwammerl, das Geschäft hat offen (named), in der Früh / jede Früh (accepted), die Kathi (a name with its article,
  accepted), heim and daheim (accepted), Baba and Servus (accepted in "ohne … zu sagen"), Kirchtag.
- Is each wrong option firmly wrong for an adult native speaker? "Wann Stefan kommt?" (as a main question; an echo question
  may sound like it), "Leg das Buch auf dem Tisch!", "Ohne dir gehe ich nicht.", "Immer als es regnete", "Ich habe nicht Hund.",
  "Heute du hast Zeit?", "Fährst langsam!", "Wir haben zu Hause geblieben.", "Hätte ich nur zu Hause geblieben!", "Wir hätten
  früher fahren gesollt.", "eine Entscheidung nehmen", "Er legt einen Antrag", "zu Verfügung", "Das ist etwas neues."
  (lower case), "Ich habe nach Villach gefahren.", "Mitzi sagt, sie seie krank." (seie may be heard in a dialect), "Wann ich jung
  war" (Austrian speech says wie for als; wann for wenn, which the table makes almost, was taken out of the options when
  `GermanPracticeTest` found it).
- Forms people say, never offered as wrong and left to the grading table's almost in typed gaps: weil with the verb second
  (weil-dass's gap that wants denn), trotzdem for obwohl, wie for als, wann for wenn, the indicative or Konjunktiv II after a
  verb of saying ("er kommt", "sie hätte keine Zeit"), als ob with the indicative ("als ob er alles weiß"). Accepted as right
  where the units accept them: wegen dem Regen (speaking), dass + the indicative in reported speech, ich habe gesessen.
- Imperatives with the e-to-i change (iss, lies, gib, nimm) are tested only as right: ess, les, geb are heard.
- Choices of meaning, where a second answer could be argued: "Hier ___ man nicht parken" (darf; not muss, will), "Das Licht ist
  an. Sie ___ zu Hause sein" (muss), "Trotz des schlechten Wetters" (not wegen, statt).
- Slovene in the bases: the learner's gender is {m:…|f:…} throughout; "žegnanje" for Kirchtag; Villach as Beljak, Italian
  Villaco; "Veselimo se medu (ki ga že imamo)" for sich freuen über; "Ozira se na sosede" for Rücksicht nehmen; "Kdo je kdo?".
- Italian in the bases: "Sono {m:stato seduto|f:stata seduta} all'osteria" for the Austrian Perfekt of sitzen; "Non vedo l'ora"
  for sich freuen auf; "si dice che" for soll.

**Drills (companion/drills/de; from the helper that wrote them):**
- Austrian forms: Jänner, heuer, in der Früh, zu Mittag, Paradeiser, Erdäpfel, Semmel, Topfen, Marillen, Kasten, der Polster,
  Sessel, Bankerl, Jause, Schwammerl, Kren, Germteig, Stube, am Markt, am Gemeindeamt, Meldezettel, zwei Kaffee, Grüß Gott,
  Servus; ist gesessen, "Wären wir nur in der ersten Reihe gesessen!", "ist 1945 geboren"; the clock's "viertel neun" and
  "dreiviertel sieben" (marked "the Austrian way" in the cue); Februar rather than Feber. The genitive set starts from the
  spoken "wegen dem Regen", "trotz dem Schnee" and turns them written.
- German to check: "Mein Rand wird von Hand gekrendelt" (Kasnudeln), "Jeden Morgen hört Tante Mitzi zu laut Radio", "Morgen
  fahre ich mit Stefan nach Villach auf den Markt", the riddles' "Die Familie isst an mir", "Ich bin im Meiler gemacht worden,
  tagelang und ohne Flamme", "Man sagt, ich sei härter und hielte länger als anderes Holz", "Wegen der Straßensperre."
- Slovene: decimals ("Tri cele pet odstotka", "Dve celi pet stopinje"), times ("četrt na tri", "tri četrt na pet", "ob četrt do
  desetih"), "Danes je tretji marec", "gostilna Pri lipi", Kasnudel as masculine, "kmečka pratika", dedek Sepp, babica Resi,
  teta Mitzi in narration; masculine plural as the default where a "we" stands for the learner.
- Italian: "Franz mi ha costruita" (the Harpfe feminine), "il Kasnudel", "il monte Peca", "lago di Faak", "passo del Loibl",
  "smielato", "Siamo stati sdraiati al sole", "Vengo cotto con la pasta lievitata" (Reindling), "Lunedì." for "Am Montag.".

## What was wrong (checked on f9339ab8)

| Where | What it did | Now |
|---|---|---|
| Home's ✨ for a German village | "None yet": companion/modules/de didn't exist | the modules at the learner's level, one request at a time |
| A run (`Content.pick`) | "No grammar drills yet", "No conversation drills yet" | the modules' exercises (`GermanPracticeTest`) |
| The car (`RoadMix`) | no German drill: the four sessions stayed away | the four drills, A1 to B2; they play once their clips exist |
| "🔢 Števila" for German | no tile: `NumberTongue.of("de")` was null | German's numbers, opened by the unit zahlen |

## Modules: `companion/modules/de/` (28)

Each in Italian's shape: texts per base `{sl, en, it}` (English required by the format), 12 exercises (three choices, a multi,
four typed gaps, two sentences to build, one to translate, one to say aloud with `show: false`), every exercise naming its
page, a giver of Kärnten's cast with a story "German · base", `order` within the level for the pace.

| Level | Modules (order) and giver |
|---|---|
| A1 | sein-haben (Opa Sepp), der-die-das (Oma Resi), praesens (Hirte Florian), verb-zweite (Lena), akkusativ-kein (Müller Franz), trennbar-imperativ (Wirtin Kathi), perfekt (Maxi) |
| A2 | dativ (Oma Resi), mit-fuer (Mostbauer Stefan), wo-wohin (Schmied Toni), perfekt-partizip (Tante Mitzi), muessen-war (Opa Sepp), sich-freuen-auf (Imker Stanko), adjektiv-endungen (Wirtin Kathi), weil-dass (Lehrerin Eva) |
| B1 | werden-praeteritum (Opa Sepp), als-nachdem (Tante Mitzi), genitiv-wegen (Mostbauer Stefan), relativsatz (Lehrerin Eva), um-zu-obwohl (Hirte Florian), haette-waere (Wirtin Kathi), passiv (Müller Franz) |
| B2 | haette-gewusst (Opa Sepp), indirekte-rede (Tante Mitzi), passiv-zeiten (Schmied Toni), vermutung (Wirtin Kathi), partizip-attribut (Oma Resi), nominalisierung (Mostbauer Stefan) |

They cover 52 of German's 68 pages, each of which now lists its modules (`modules` on the page). Not covered by a module: zahlen,
uhrzeit and ordinalzahlen (the numbers module's), faelle, steigerung, adjektiv-tabelle, doppelkonnektoren, indefinitpronomen,
wo-da-woerter, n-deklination, adjektive-praepositionen, indem-sodass, zwar-aber, satzbau, formeller-brief, oesterreichisch.

**The wrong options are the learners' mistakes**, from Slovene, English and Italian: haben for age and ich bin kalt (Italian
avere, English be), ich helfe dich (aiutare qualcuno), warten für (wait for), ob against wenn (if), nicht dürfen for nicht
müssen (mustn't), Heute Oma backt, ein Sack for einen, ich habe gefahren, geaufstanden, getelefoniert, müssten for mussten, für
for um … zu, Konnten Sie, gebauen, gesprecht. **What people say is never one** (above). `GermanPracticeTest` holds this against
the grading table: no choice's or multi's wrong option, and no reorder distractor in place of a word, is graded NONSTANDARD.

**A sentence to build lists every order German allows**: the chips show no capital and no full stop (`ChipLabels`), so "Morgen
fahre ich" and "Ich fahre morgen" are both solutions.

**The learner's gender** in a base's text is `{m:…|f:…}`, rendered by the app; the German needs none. Typed answers list the
German-German word where it differs, and both auxiliaries of sitzen and stehen.

**The pace** is the bridge's (`ModuleStore.offered`, practice-it.md): one open request at a time, at most one new a day, in the
level's order, the next level's after; the smoke's kaernten section checks that a new German village gets sein-haben alone, in
Ana's Slovene.

## Drills: `companion/drills/de/` (4)

Written by a helper to this branch's brief, reviewed against the smoke's checks (commit 65a911e6).

| Drill | What | Levels |
|---|---|---|
| preobrat 🔄 | 26 sets, 300 items: plural, du's vowel, Sie, kein/nicht, den, verb second; Perfekt, war/hatte, dem, wo/wohin, weil, comparing, modals; Präteritum, passive, Futur, Konjunktiv II, genitive, relative clause, obwohl; reported speech, the passive's Perfekt, hätte nur, nominalisation, the long attribute, modals of guessing | A1 74, A2 85, B1 76, B2 65 |
| hitri-odziv ⚡ | 11 sets: 1–100 and the hundreds generated (the bridge's `numberWord`), the time, at what time, days, when, months; dates and prices; years; per cent, decimals and fractions | A1 7, A2 2, B1 1, B2 1 |
| gradnja 🧱 | 81 sentences grown in 3–4 steps, word order the point | A1 40, A2 20, B1 12, B2 9 |
| uganke 🕵️ | 55 riddles told by Opa Sepp (`teller: sepp`), 46 pack words, 9 villagers | A1 30, A2 13, B1 7, B2 5 |

Every said text is in de, en, sl and it, every cue in en, sl and it (Italian's lack sl: practice-it.md's "what's left"), so a
learner from Slovene or Italian hears their own base. The smoke's practice section checks this for German (`drillBases`).

## Numbers: `data/NumberTongue.kt` (GermanNumbers)

| | German | Graded as |
|---|---|---|
| Opens | the unit zahlen's intro; uhrzeit's brings the clock; am-ersten-mai's (page ordinalzahlen) the dates | — |
| Words | one word, the ones first, as the page counts: einundzwanzig, hundertzwanzig, zweitausendsechsundzwanzig (`SpokenNumbers.german`, to 999,999) | einhundert, eintausend, hundertundeins, a year's hundreds, zwo right; apart or ae for ä almost |
| Order | the tens first, as English and Italian say them | "zwanzigsieben", 72 for 27: `numbers_order` with its why |
| Own words | eins, sechs, sieben kept whole | einsundzwanzig, sechszehn, siebenzig: wrong, `numbers_spelling` |
| Date | "Heute ist der ___ Mai. (15.)" → fünfzehnte; erste, dritte, siebte (siebente), achte; -ste from 20; Jänner, Januar and Feber too | einste, achtte, zehnste, zwanzigte: wrong, `numbers_ordinal_date` |
| Clock | um fünf, um eins, um ein Uhr, fünf Uhr dreißig, siebzehn Uhr dreißig, zu Mittag | — |
| Half past | um halb sechs = 5:30, halb towards the next hour, as Slovene's pol | 6:30 typed, "halb fünf" written: `numbers_clock_time_half` |

Speech: `SpokenNumbers.forms` for German has hundert… beside einhundert… for 100 to 199, so a recognizer's "120" matches a
text's "hundertzwanzig". The bridge's `get_village` shows a German village's numbers (`NUMBERS_UNITS`: de/zahlen); the tutor's
guide on the numbers says what the ids mean in German (features/counting.ts, tutor-export run). `NumbersGermanTest` (12).

## Older apps

- **Modules:** the exercise kinds are those Slovene's and Italian's use (choice, multi, cloze, reorder, translate, speak), which
  Teo's 0.2.698 reads (practice-it.md read it at a37c1765); the texts per base are resolved by the bridge into plain strings; the
  level and the pace are the bridge's, so an old app gets an A1 learner's A1 modules only, one at a time.
- **Drills:** the format is Italian's; the bridge serves them (`GET /drills`), the app plays a set only at or below the
  learner's level and once its page is introduced, so a B2 set never reaches an A1 learner on any app that plays drills.
- **Numbers:** the app's own code; an older app has no German tongue and shows no tile, as before.
- No German village runs on Teo's tablet or Jan's phone; neither learner sees any of this.

## Tap counts (a German learner on Home)

| Journey | Before | After |
|---|---|---|
| A grammar drill | none: ✨ opened "None yet" | 3: ✨, the module, "▶ Start" (its giver's card between) |
| Numbers | none: no tile | 2 once zahlen's intro is done: 🔢, "▶ Start"; closed, 1 to the intro |
| A car drill | none | as Italian's, once voiced: 🚗, the session |

## Voice

Nothing was voiced; no ElevenLabs call. A build would take:

| What | Texts | Characters | Who |
|---|---|---|---|
| preobrat | 595 | 18,134 | the narrator (Fiona) |
| hitri-odziv, with the 124 generated number words | 310 | 4,576 | the narrator |
| gradnja | 281 | 10,995 | the narrator |
| uganke | 239 | 7,179 | Opa Sepp (the old man's voice, Helmut) |
| **The drills** | 1,425 | 40,884 | |
| The modules (`say`, options read aloud) | 371 | 6,597 | the narrator |
| **Together** | 1,796 | 47,481, about 23,700 credits on `eleven_v4_turbo` | |

An upper bound (a text the store has isn't made again; no learner's store was read). The numbers module's words aren't in the
corpus: made when heard, as Italian's. Until the drills are voiced the car leaves them out. Jan's decision.

## Lexicon

25 supplement entries and seven forms on existing ones (lexicon/de.extra.json) for the modules' words. German coverage: 99.9 % of
tokens as before; words 98.6 % (from 99.1 %): the 27 words none knows that the modules brought are their wrong options, the
learners' own forms (geaufstanden, getelefoniert, gehte, denkte, kommte, gibte, habte, seie, zurückgetretet …), on purpose. The
drills aren't tapped (the car's screen), so their words weren't added.

## Checks

On the merge with main e6209062 (b5a8dab3):

- Bridge: `tsc`; the smoke, all checks passed (3,729), its practice section for German (23 checks of modules and drills) and
  kaernten's serving (one module at first, in the learner's Slovene; the four drills); `tutor-export --check` (140 files);
  `bun test/check-practice.ts de` all good; each touched TypeScript test file built.
- Android: the full unit suite through lani-slot.sh, 4,119 tests, 0 failures. New: `NumbersGermanTest` (12), `GermanPracticeTest`
  (7: every module parsed in sl, en and it; a run at each level A1 to B2; every typed answer right, an umlaut typed as ae almost;
  no wrong option, multi option or chip what the grading table says people say; the drills bundled). `GermanPracticeTest` found one:
  wann among the options of "Immer ___ es regnete" (fixed: ob).
- Python: `tests` (253) and `companion/tests` (164).
- QA through qa-slot (before the merge, on 36ad2a52's APK) on a synthetic Ana (de from sl, Kärnten): `--steps practice-de` ok,
  screenshots pd-*.png: the ✨ tile "1 neu · 1 nov", the sheet with the level's first module (Hirte Florian's praesens: Opa Sepp and
  Oma Resi hadn't moved in yet, so the pace took the first whose giver lives here), its card and a question's why in Slovene; the
  numbers closed, the intro skipped, the note; "siebzig fünf" for 75 wrong with the ones first's why in Slovene; "um halb drei"
  for 3:30 wrong with halb's why; 🔢 on Home.
- Not run: the car on the emulator (no German clips: the road leaves the drills out, as for Italian), a number heard in German,
  Teo's tablet size (no German village there).

## What's left

- English's modules, drills and numbers (practice-en); Italian's B2 (practice-it).
- The 16 German pages with no module (above), at most one module a level more if the pace allows.
- The drills' Slovene cues on Italian (practice-it.md).
- Voicing (above).
- A native check (first section).
