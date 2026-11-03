# Built: German B1 word packs, part one (packs-de, 2026-11-03)

Wave E, section 9 (followups-14.md): B1 and B2 word packs for German (Kärnten, Austrian standard), so that a learner
can reach the B2 aim's word milestones from the curated packs. **Done: 17 of the 21 planned B1 packs. Not done: 4 B1
packs and all 40 planned B2 packs** (see "What's left": the plan, the tools and the brief for whoever goes on are ready).
No helper agent could start (the session was at its limit of concurrent agents), so one writer did all of it.

## Native check (first)

Everything is machine-written (Claude) and not reviewed. Each pack says so in its `review`. Check first:

- **Austrian forms and their German-German notes:** das Spital, die Rettung, verkühlt and die Verkühlung, das Zahnweh,
  das Stiegenhaus, das Erdgeschoß (spelled with ß), das Vorzimmer, der Gang (corridor), der Lift, der Mist and der
  Mistkübel, der Fetzen, die Kluppe, das Leintuch, abwaschen, die Aktion ("in Aktion"), die Kassa, der Kassabon, die
  Trafik, der Fleischhauer, das Leiberl, die Haube, die Nächtigung, die Raststation, das Pickerl, die Kurzparkzone,
  der Hochnebel, "es hat dreißig Grad", die Schularbeit, die Matura, die Volksschule, Einser and Fünfer, die Karenz,
  das Begräbnis, der Bankomat, die Bankomatkarte, der Erlagschein, das Geldbörsel (its plural die Geldbörsel), gescheit,
  schlampig ("a little rude").
- **Facts to confirm:** the deposit on cans and plastic bottles since 2025 (einkaufen/pfand); the Weltspartag at the end
  of October (geld-und-bank/sparbuch); the breakdown numbers 120 (ÖAMTC) and 123 (ARBÖ); the ambulance's 144; "fourteen
  salaries a year" (arbeit-und-beruf/gehalt); a third of young people doing a Lehre; the Firmpate's watch.
- **Slovene to check:** žegnanje for the Kirchtag; Ziljska dolina, Rož, Šmohor, Dobrač, Baško jezero, Ostrovica; "cela
  roka" for der Arm (Slovene has roka for arm and hand: der Arm needed a prompt of its own); "kmečka peč" for the
  Kachelofen; zdravnik urgentne pomoči; "obrok (odplačila)"; "nizka oblačnost" for Hochnebel; the gender pairs
  ({m:…|f:…}) in speichern, verkühlt, packen, Bügeleisen and Zunge.
- **Italian to check:** "cambiare in spiccioli" (wechseln), l'infreddatura (die Verkühlung), l'ingessatura (der Gips),
  la postazione di lavoro (der Arbeitsplatz), "fare un cambio (in negozio)" (umtauschen), la nebbia alta (Hochnebel),
  "diventare grande" (aufwachsen), "l'unione coniugale" (die Ehe), "la carta da riciclare" (Altpapier).

## The numbers

"Words learned" counts the vocabulary cards a learner has right (GAME.md, "Words learned per age"); a pack word whose
spelling the learner already has is one card (packs.ts `learnedWords`). So what counts is the **distinct** words of the
curated packs a German learner gets: `companion/packs/de` and `cultures/kaernten/packs`, compared as packs.ts's
`normWord` (lower case, no punctuation, the article kept; a word marked homonym once per meaning).

| | packs | words | distinct, new | distinct, in all |
|---|---:|---:|---:|---:|
| A1 (before) | 69 | 898 | 745 | 745 |
| A2 (before) | 12 | 182 | 177 | 922 |
| **B1 (this branch)** | 17 | 406 | 406 | **1,328** |
| B2 | 0 | 0 | 0 | 1,328 |

The brief's "de 1,080" is the words (with repeats); the distinct words were 922. The B2 aim asks 1,000 learned words by
Trg and 2,000 by Mesto. **Trg's 1,000 is covered with a third to spare** (1,328: festivals' and projects' packs only come
with their day or their project, and failed reviews leave words rusty, so the margin is wanted). **Mesto's 2,000 is not
yet**: 672 short, about 830 words with a 15 % margin, so the planned 40 B2 packs (about 950 words) and the 4 B1 packs
left (about 95) would bring the total to about 2,370.

Recount with `python3 .packs-work/pk.py count` (in the worktree).

## What

17 packs in `companion/packs/de/`, all level B1, 22–24 words each (406 words), each word with its gender and plural
(nouns), an example in German, Slovene, English and Italian, and a note where Austria and Germany differ, a verb's
forms are irregular, or a custom is worth knowing. Givers from the cast; examples set in the village, the Gailtal,
Villach and Klagenfurt.

| pack | giver | theme |
|---|---|---|
| arbeit-und-beruf | Wirtin Kathi | the job, application, CV, interview, Lehre, shifts, Gehalt, AMS, Feierabend |
| handy-und-computer | Lehrerin Eva | the Handy, message, e-mail, WLAN, password, screen, printer, saving, the charger |
| gefuehle | Oma Resi | glad, sad, angry, fear, worry, homesickness, tears, laughing, offended |
| wie-menschen-sind | Tante Mitzi | friendly, polite, patient, nosy, shy, generous, stingy, tidy, reliable, gescheit |
| der-koerper | Lena | eye, ear, nose, tooth, shoulder, elbow, knee, foot, skin, heart, lungs, blood, bones |
| im-spital | Tante Mitzi | das Spital, die Rettung, injury, bandage, injection, allergy, blood pressure, specialist |
| die-wohnung | Mostbauer Stefan | the flat, rent, deposit, service charges, Stiegenhaus, Erdgeschoß, Vorzimmer, house rules |
| reparaturen | Schmied Toni | tap, socket, bulb, screwdriver, drill, plumber, blocked, tiles, fuse, smoke alarm |
| im-haushalt | Oma Resi | hoovering, washing up, ironing, the Mist, sorting rubbish, Fetzen, Kluppe, Leintuch |
| geld-und-bank | Mostbauer Stefan | the bank, account, Bankomat, transfer, Erlagschein, saving, loan, interest, debts |
| einkaufen | Wirtin Kathi | supermarket, Aktion, Kassa, Kassabon, Trafik, Fleischhauer, deposit, dear and cheap |
| kleidung | Tante Mitzi | trousers, shirt, skirt, dress, Leiberl, Haube, dirndl, lederhosen, to fit, tight |
| auf-reisen | Wirtin Kathi | the trip, Pension, booking, suitcase, airport, Vignette, Stau, Raststation, sights |
| mit-dem-auto | Schmied Toni | licence, filling up, Pickerl, winter tyres, Kurzparkzone, lights, roundabout, Panne |
| wetterbericht | Müller Franz | forecast, degrees, frost, black ice, Föhn, muggy, heatwave, Hochnebel, avalanche |
| das-leben | Opa Sepp | birth, growing up, marriage, divorce, christening, Firmung, funeral, widow, Karenz |
| schule-und-lernen | Lehrerin Eva | exam, Schularbeit, marks, report, Matura, university, VHS, holidays, Volksschule |

**One spelling, two meanings:** die Bank (money) and die Bank (the bench, in-der-kueche and projekt-obstgarten) are
marked homonym in all three packs, so the learner who has the bench is still taught the bank.

**Prompts kept apart.** A new word's meaning must not equal an older German word's in any base
(`bun test/prompt-clashes.ts`; no new clash, the baseline untouched). Most were settled on the new word (bremsen "to put
the brakes on" beside die Bremse; die Kälte "cold weather" beside kalt; aufräumen "to tidy up" beside ordentlich). A person's
noun is one card with the other gender in its note (der Chef: "A woman: die Chefin"), as beim-arzt has der Arzt; two
cards would ask one prompt. Three older meanings were sharpened, none voiced: das Ufer's English "shore, riverbank"
(was "bank, shore"), der Bauch's "belly, tummy" (was "stomach, tummy": der Magen is the stomach).

**The tap** (`companion/lexicon/de.extra.json`): every German word of the new titles, words and examples is known (a pack
word, a villager's name, or the supplement): 110 entries added and 29 extended (347 forms), with the forms the examples use, each
with the pack's meanings where it is a pack's word. `lexicon-build --coverage --lang de`: 99.1 % → 99.2 % of the
different words known, 99.9 % of the tokens; the words none knows 51 → 49 (Einser and the like now have an entry).

**The printed sheets** (`companion/prints/sheet.ts`): the sheets mean a word as the first pack that has it; the B1 money
pack's die Bank came before the A1 bench, and every German sheet with "Bank" printed "bank" for the bench (12 files in
each base). The B1 and higher packs now come after the A1 and A2 ones. With no pack above A2 (every other language
today) the order is as before. The German sheets of all four levels in en, sl and it are the same as main's.

**Shared, from another branch:** packs-en's 3c5f3ba5 (cherry-picked as 5e2e4505): the packs by the learner's level,
the folded "For later" on the packs screen, an older app held to a level above. Not changed here.

**Shared line touched:** `test/smoke/packs.ts`, the homonym check's `spellings.size === 8` is `>= 8` (the eight
listed spellings stay required): every language's new packs may mark their own. The other packs-* branches may make the
same change.

## How packs reach a learner (checked)

With packs-en's fix, a German learner at A1 gets the A1 packs first, then A2, then B1, nearest level first; the packs
two levels or more above wait under "For later"; an older app at A1 gets the A1 and A2 packs only. **One gap, for the
lead:** `suggest` and `order` take any pack with `learned > 0` as one under way, whatever its level. A B1 pack with one
word the learner already has elsewhere (a word looked up in a dialog, a tutor pack, a course card) shows as started
and is suggested before the A1 packs. The new German packs repeat no word of another pack, which keeps that rare, but
a looked-up card is enough. A started pack above the next level could count as started only once a word of it was
learned in the pack itself (its own item id, `vocab_<pack>_…`), not through another card.

## Voice

The new packs' German (each word and its example, what `corpus()` voices): 23,425 characters (4,330 for the words,
19,095 for the examples). Jan's credits: nothing was generated.

## Verified (on 9f52ee3e)

- `bunx tsc --noEmit -p .` in companion/bridge: clean. `bun build --no-bundle` of test/smoke/packs.ts and prints/sheet.ts.
- The smoke (`LANI_SMOKE_PORT=10470 LANI_SMOKE_FRIULI_PORT=10473 LANI_SMOKE_TOWN_PORT=10476 LANI_SMOKE_DRAMA3_PORT=10488`):
  all checks passed, 3,727, with every new pack validating in the kaernten section, the homonym and prompt-clash checks.
  Run before the print fix, which only changes the sheets' order of packs above A2.
- `bun test/prompt-clashes.ts`: no new clash (339 in the baseline, as before).
- `bun src/cli/tutor-export.ts --check`: 140 files match.
- Android (through lani-slot.sh): PacksTest, PackSessionTest, KaerntenCultureTest, CalendarTest, WordStickersTest, 51
  tests, no failure. **The full Android suite was not run** (content and a print change only; packs-en's commit
  brought its own tests).
- `school-prints --language de --level all --html-only` in en, sl and it, against main's: the same files.
- Not done: emulator QA (no screen changed on this branch; the packs screen is packs-en's).

## What's left (ordered)

The plan and the tools are in the worktree, untracked, at `/mnt/scratch/github/fentas/lani-packs-de/.packs-work/`:
`BRIEF.md` (the rules for a writer), `BATCHES.md` (every pack: id, level, giver, title in four languages, scope),
`pk.py` (compact source → pack JSON, the checks: limits, giver, duplicates, the tap, prompt clashes; count; the
supplement's merge), `src/` (the 17 packs' sources) and `supp-a.jsonl` (merged already).

1. The B1 packs a16 `freizeit-und-sport` (Hirte Florian), a17 `kochen-und-backen` (Oma Resi), a20 `vereine-im-dorf`
   (Opa Sepp), a21 `koerperpflege` (Jakob): about 95 words.
2. Batch B, 20 B2 packs (institutions, the council, the state, the law, news, opinions, environment, farming, forestry,
   mountain animals, economy, contract and pay, health, mood, relationships, art, customs and dialect, history,
   building, house and land). b12 has die Pension (retirement): mark it and auf-reisen's guesthouse homonym.
3. Batch C, 20 B2 packs (heating, insurance, public transport, abroad, the mountains, landscape, cooking, old age and
   care, children, digital life, education, emergencies, the company, talking, time and plans, society, science, sport,
   faith and the parish, crafts).
4. For each batch: `pk.py check` all ok, `pk.py supp-merge`, the smoke, prompt clashes, `lexicon-build --coverage`, and
   school-prints in en, sl and it against main's.
5. When B2 is in: recount (target about 2,300 distinct, Mesto's 2,000 with a margin), update this doc and the README's
   pack counts if it has any, and the voice estimate.
