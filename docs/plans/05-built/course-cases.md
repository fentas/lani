# Built: the cases in the school, and the cases overview to print (2026-10-27)

Jan asked: "so there are no school lessons for cases? (one for each case + maybe a PDF, this PDF, as a compact
overview)". Branch `course-cases`. The format is companion/COURSE.md ("A unit", "Writing a unit", "The cases (A2)"),
the prints companion/COURSE.md ("Printed sheets"), the school's rules companion/GAME.md ("The school").

## What

- **Six A2 units**, the first of the A2 outline: `skloni`, `rodilnik`, `kje-kam-od-kod`, `dajalnik`, `orodnik`, and
  `pridevniki-skloni` (after the plural's unit `mnozina-skloni`, which another writer has). A1 keeps its three case
  units (`tozilnik`, `mestnik`, `nikalnica-rodilnik`); the A2 units take them as known.

  | unit | title | intro (6 steps each) | worksheets | test |
  |---|---|---|---|---|
  | `skloni` 🧩 | Šest sklonov: vprašaj! | the six cases in the school's order, each with its question and its singular (knjiga, brat), one table | Šest vprašanj (table of all six, sort by case) · Dan pri babici (gap text, two question choices) · Vprašaj in odgovori (answers in the right case) | 12 |
  | `rodilnik` 🧺 | Rodilnik: brez mleka, iz Gorice | of (sledi medveda), amounts, the plural after amounts and five (ovc, bratov, jajc), iz/brez/zaradi (sladkorja, dežja), od … do, blizu, mimo, bati se | Koliko? (table, sort malo mleka / malo jajc) · Na tržnici (gap text) · Zamenjaj besedo | 12 |
  | `kje-kam-od-kod` 🧭 | Kje? Kam? Od kod? | v → iz, na → z/s, pri → k → od, doma/domov/od doma, tukaj/sem/od tod, places that take na (Bled, morje), things (na mizo, z mize) | Tri vprašanja (table) · Zalin dan (gap text) · Od kod prihajaš? | 12 |
  | `dajalnik` 🎁 | Dajalnik: babici, k babici | -i / -u / -om, -am; giving and telling verbs, pomagati; k/h, proti; mi/ti/mu/ji and meni; mrzlo mi je, všeč mi je | Komu? (table, sort komu/koga verbs) · Darila za praznik · Komu? Njemu, njej (two transforms) | 12 |
  | `orodnik` 🔨 | Orodnik: s kruhom, pod mizo | -o / -om / -em, plural; s or z; tool, transport, food; z mano, s tabo; pod/nad/pred/za/med kje? against kam? | S kom? S čim? (table, sort s/z) · Pri kovaču Tonetu · Kam? Kje? | 12 |
  | `pridevniki-skloni` 🎨 | Pridevniki v sklonih: v veliki hiši | velik through the six cases (a row per case, the overview prints it), the animate accusative, the plural -ih/-im/-imi | Nov, nova, novo (table, sort) · Sejem v vasi · Povej natančneje | 11 |

  Each: the goal and the explanations in en, de and it, examples from the village (Micka, Luka, France, Tone, Zala,
  Nejc, Muri …), the Slovene marked for Mojca's reading, a quick question a step, 26–33 worksheet items, all
  machine-written with what a native speaker should look at in its `review`.
- **What they turn on** (COURSE.md's table): the genitive, dative and instrumental rows of the forms cards and the
  adjective's cases; `skloni` and `kje-kam-od-kod` nothing new.
- **Content checks** in the bridge's smoke (`test/smoke/course-content.ts`), on every unit of the course: a named form
  (lemma + form) is the dictionary's for its slot, an answer with a word in brackets is a form of that word (441
  answers of all 48 units; a word made of it, a verbal noun or a participle, isn't judged as its form), and COURSE.md's "What a unit turns on" lists every unit that turns on a form, with its pages.
- **The cases overview to print**, "🧩 Šest sklonov · The six cases", three A4 pages made by the school's print
  generator from the grammar book's pages and two of the units' tables (`companion/prints/overview.ts`; nothing written
  for it but its headings): the six cases at a glance (question, endings, what it does, prepositions, an example), how
  to find the case, numbers, Kje? Kam? Od kod?, the three declension tables with their endings marked, an adjective
  through the cases, the personal pronouns, and the genitive's, dative's and instrumental's uses. In the app as
  "🖨️ Pregled sklonov · Cases overview" on the unit `skloni` (under "🖨️ Natisni list") and on the grammar book's page
  Skloni (under "🎯 Vadi · 💬 Vprašaj"), and on the school's level A2 beside "🖨️ Vsi listi", also while A2 is closed:
  Android's print dialog, "Save as PDF". The bridge serves it
  (`GET /course/overview/skloni`), `school-prints --overview skloni [--base de|it] [--png]` writes the PDF. 3 pages in
  en, de and it with the real units, black-and-white safe.
- **QA:** the step `school-cases` (the unit skloni end to end, through QA's hook `school:skloni`, which opens the
  unit's level) and `school-print-overview` (the overview printed from the book's page).

## Taps

| what | before | after |
|---|---|---|
| a lesson on the dative, the instrumental, the genitive's uses, the place triad, adjectives in the cases | none in the school (the grammar book's pages only) | Home's "🏫 Šola" (1) → the unit (1) → "📖 Uvod" (1): 3, once A2 is open |
| the cases overview on paper | a hand-made PDF outside the app | Home's "🏫 Šola" (1) → A2's "🖨️ Pregled sklonov" (1, scrolled to) → the print dialog's "Save as PDF": 2 taps and the dialog, A2 open or not; from the unit skloni one more (the unit); from the grammar book's page Skloni (once met) 1 |

## How Jan gets to them

A2 opens by the school's rule (GAME.md "The school opens the next levels"): **the school standing and every A1 unit
done** (its test passed). So:

1. The school built (the hamlet's building: 100🌾 100🪵 100🪨 100📜), if it isn't yet.
2. The A1 units not done yet: each one's test passed; "⏩ Znam že: kar na test · I know it: straight to the test" on a
   unit tests out of it at once, without its sheets.
3. A2 opens then (the chronicle says so), and `skloni` is the unit suggested next (★) on the school's screen and Home's
   tile. A level opened once stays open.

Before that the A2 units' sheets print from their rows' 🖨️ and A2's "🖨️ Vsi listi" (a locked unit's too, to learn
from on the way), and the cases overview from A2's "🖨️ Pregled sklonov" (and the grammar book's page Skloni once met).
There is no preview of a closed level's lessons in the app: its units open with the level.

## Voice

The new units' Slovene in Mojca's voice, `voice-build --only teacher` on a synthetic store (a learner from
`make-learner.ts`, a course of the six units only, dry run): **300 texts, 5,176 characters** in the course's row (her
villager lines and scenes besides: 33 and 192 texts, as before). Their explanations, made on demand as an intro opens
(or ahead with `--explanations`): 42 clips, 9,631 characters in English (~4,800 credits); en, de and it: 126 clips,
29,199 characters (~14,600 credits). The whole course now: 935 texts, 15,903 characters.

## Tests

- Bridge: `bunx tsc --noEmit -p .` green; the smoke all ✅ on main after the last rebase, with the course's content
  checks (441 answers of A1, A2 and B1 against the dictionary, COURSE.md's 40 rows) and the overview's route.
- Android: 3,138 unit tests; `PhoneBridgeNodeTest` (25) failed once with another agent's bridge on its fixed port 19110
  and passes alone. New: `PrintsTest` (the overview, a level's overview).
- Python: 251 + 98 (`tests`, `companion/tests`).
- Emulator (`companion/bin/qa --steps school-cases`, then `school-print-overview`): the unit skloni end to end (its six
  intro steps, the table of the six cases, the sort by case, its test passed 11 of 12, the unit done) and the overview
  printed from the book's page Skloni (fit.js the same in the WebView as in Chromium: 3 pages, density 0; the PDF A4).
  Screenshots: the scratchpad's `ux-course-cases/qa-shots/`.

## What's left

- **A native speaker's check** of the six units (each `review` names its sentences).
- **The school's pacing** (GAME.md "Pacing", `SimulationTest.COURSE`): the sim still plays A1's 18 units only. With
  A2 written the school's level 2 waits for A2's units; measure once A2 and B1 are complete.
- Other writers' units that name a page a form counts on need their row in COURSE.md's "What a unit turns on" (the
  smoke says which).
