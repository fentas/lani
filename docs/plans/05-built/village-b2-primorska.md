# Built: Primorska's village to B2 (village-b2-primorska, 2026-11-03)

Wave E, followups-16 §3, for Primorska (Slovene: Jan's village). The parties, the arrivals and the project steps stopped at A2
or B1, so a B2 learner met A2 or B1 lines. This branch adds B1 and B2 tellings where they were missing. All of it is
machine-written (Claude) and flagged so in each file's `review`; nothing was voiced.

## Native check (for Jan's wife: register and naturalness; the dictionary questions were settled below)

Each line is a question about how something sounds. A spoken form is never graded wrong anywhere in this branch.

1. **What the learner says to whom.** The wrong ti/vi choices follow each villager's card: vi to Micka, Ančka, Janez, France,
   Jože, Tone, Anton, Vida, Mojca and the newcomer; ti to Luka, Marko and the children. The right choice to Micka is
   "ste se odločili" (vi with the masculine plural, the written norm). "Ste se odločila" is never offered as wrong.
2. **Sayings.** "Kuj železo, dokler je vroče" (Tone, the fountain) and "Brez muje se še čevelj ne obuje" (Janez, already
   in his B1 arrival) are meant as the real proverbs. **Sayings that are characters' own, not proverbs.** Tone: "Kdor veliko govori, malo skuje." Micka (mill): "Mlin, ki ga zidaš
   počasi in skrbno, stoji sto let." France: "Kdor gradi na blatu, gradi za eno zimo." "Kdor misli naprej, ne gradi dvakrat."
   Do they sound like something a Primorska elder says, or too made up?
3. **Images and jokes:** "nagnil kot pijanec" (a crooked spruce, a leaning mill), "noge mi modrijo od mraza", "Pokonci jih ne
   zbija nihče, ki ima rad svoje prste", "grem kovat podkve kozam", "kot da bi skupaj pasli krave" (Micka on being said ti to),
   "Kakšno tikanje je pa to?" (Ančka), "Mladi ste danes hitri" (Jože), "mi je prirasel k srcu" (Tine about his grandad).
4. **Single words a native may hear differently:** "barantat" (trg B2, the supine after pojdi), "nimamo ga kam natočiti" (vas B1),
   "Ti skoči ponje" (vas B2), "na vsakih deset korakov" (mlaj), "obleži, kamor ga postaviš" (most), "lenejši potok" (mlin),
   "na slepo" (most B2: graditi na slepo).
5. **The wrong choices that name a grammar page.** Each must be wrong in writing *and* in speech, or it has to go:
   "Prižgem bi …", "rad bi pojem", "bi plešem", "ko bi le znam" (bi with the present); "bi bil treba" (treba with bil);
   "trakove, katere bi plapolali", "vsi, katere so ga zgradili" (katere for a subject); "brez kateri"; "pet medenjaki",
   "šest metra", "štiri sten"; "bo vse štiri stene stalo" (stevila-glagol); "na planini" for na planino, "na tem bregu" for
   na ta breg, "v gozdu … v gozd" swapped, "na ramah" for na rame, "na mostu" for na most; "živeti tja, kjer";
   "predlagam, da ga nesimo", "da prinesimo", "da kopljimo", "da prečešimo"; "ne da bo pritekel"; "naj zvoniti"; the
   imperative "zidi"; "dokler bo gladka" for until; "na kateri" with igrišče, "v katero" with korito; "s toboganom" for
   off the slide; "na igrišču se tečejo"; "velikost številčnica"; "iz štiri cevi", "iz vse štiri cevi"; "od les"; "tako boljša, kot", "tako višjega"; "olupljen deblo"; "Če sem na tvojem
   mestu, bi …"; "ne da bo pritekel"; "z ostrim nož", "z dolgo vrv", "z velikim kolo"; "toliko kruh"; "brez trdni temelji".
   None of them is a dual against a plural (Primorska's speech varies there), and none is a clitic placed first.

## What the learner gets

| What | Before | Now | Where |
|---|---|---|---|
| Parties (5 ages) | A1, A2 | + B1, B2 (10 tellings) | `cultures/primorska/celebrations/*.json` |
| Arrivals (14 villagers + newcomer) | A1, A2, B1 | + B2 (15 tellings) | `cultures/primorska/arrivals.json` |
| Project steps (13 projects) | A1, A2 (78) | + B1, B2 for all 78 (156 tellings) | `cultures/primorska/project-steps/*.json` |
| Keeper talks (Oglar Miha's cycle) | A1, A2 | unchanged (to do) | `cultures/primorska/world.json` |

The app plays the highest level at or below the learner's (`Arrivals.levelFor`, used by arrivals, steps, parties and the
keeper), so a B1 learner now meets the B1 telling and a B2 learner the B2 one. An A1 or A2 learner meets what they met before.
`levelFor` has known B1 and B2 since the app's first commit, so an older app reads the new keys the same way; nothing needs
holding back. The birth and moved templates stay at A1/A2 (the brief asks for the newcomer's).

**Parties.** B1: 6–8 lines, 2 turns; B2: 8–10 lines, 3 turns, one of them a turn of understanding (the reply that fits what the
person means). Each telling tests the party's two words as A1/A2 do (bakla, peti; trak, smrečica; kozarec, plesati; medenjak,
kupiti; godba, zastava). Guests' lines keep the speaker's gender out (a guest may be stood in for). Grammar pages named:
pogojnik-ce-bi, brezosebno, oziralni-ki, oziralni-kateri, stevila-samostalniki.

**Arrivals.** Each B2 arrival has its late line and 7–9 lines with 2 turns, and tells the person's story further than B1 does:
what they'd have done otherwise (Micka: "Če bi mi kdo takrat rekel …, bi ga poslala k zdravniku"), reported speech without
backshift (Luka's father, Zala's parents), a choice that weighs (Vida's terrace). Pages: predlogi-dva-sklona, stopnjevanje,
pogojnik-ce-bi, kam-tozilnik, ti-vi.

**Steps.** 6–7 lines, 2 turns. The tap turns keep the A2 telling's slots (spruce, stones, stream); the cues (birds and fell,
maypole, band, cart 0/0.8/1, wheel) and the `done` line sit where the A2 telling has them, so the picture shows the work at the
same moment. Each step's words are tested by a one-word swap, as the checks want (`testedIn`: one or two words differ).

## Facts the lines state, with their sources

- World Bee Day on 20 May, Anton Janša's birthday (1734), proposed by Slovenia: FAO, https://www.fao.org/world-bee-day/en
  (Anton's B2 arrival; "beekeeper at the imperial court in Vienna" is the common account of Janša).
- The painted hive panels' motifs "hudič babi jezik brusi" and "lovčev pogreb" (animals bury the hunter): Makarovič's list on
  dlib.si (https://dlib.si/stream/URN:NBN:SI:DOC-LIAVC6RH/8e1f0587-2453-4918-b636-afa8977c4981/PDF) and Bokal, Čebela 9/2022
  (https://dlib.si/stream/URN:NBN:SI:DOC-9LYRT1RS/1900221c-36b5-4899-9384-0ec3940d0890/PDF).
- trak: SP2001 "trák -ú/-a, -ôvi -ôv" (https://fran.si/iskanje?FilteredDictionaryIds=134&View=1&Query=trak): the supplement's
  traku, trakov; medenjak's forms are regular (-a, -om, -ov, -ih).
- barantati: SSKJ² "pog." (haggle; https://fran.si/iskanje?FilteredDictionaryIds=133&View=1&Query=barantati): only in
  Ančka's line at the fair, never a choice graded either way. krmišče: SSKJ², the hunting sense labelled "lov."
  (https://fran.si/iskanje?FilteredDictionaryIds=133&View=1&Query=krmi%C5%A1%C4%8De), Jože's B2 arrival. suhozid is no SSKJ²
  headword and "suhi zid" isn't shown either (https://fran.si/iskanje?FilteredDictionaryIds=133&View=1&Query=suhozid; the
  stone-masons' terminology dictionary has one): the vineyard's and the chapel's "suhi zid / suh zid" wait for the native check.
- Larch shingles don't rot readily (mlin B1), shingles split by hand (mlin B2), the mlaj peeled but for its green top with a
  wreath and ribbons (as the A1/A2 tellings have it): general, no new custom is claimed.

## Checks

- `bun test/check-celebrations.ts primorska`: 5 celebrations, 20 parties. `bun test/check-culture.ts primorska`: ok.
  `bun test/check-steps.ts primorska`: 13 files, 78 steps, 312 dialogs, 2,103 lines, ok. `companion/bin/content-count`:
  Primorska's steps A1 78 · A2 78 · B1 78 · B2 78, parties A1–B2 5 each, arrivals A1–B2 14 each.
- `bunx tsc --noEmit -p .` after merging main (615fddee4): clean.
- The smoke (LANI_SMOKE_PORT=10770, LANI_SMOKE_DRAMA3_PORT=10788): all checks passed after the parties and arrivals.
- The supplement: the forms the word checks needed (medenjak, trak, končnica, gugalnica, tobogan, veselica).
- **Coverage fell** (`companion/bin/lexicon-build --coverage --lang sl`): 95.5 → 95.3 % of tokens with the supplement
  (97.2 → 96.9 with pack words and names), after parties, arrivals and the first seven projects' steps. The new lines use
  511 word forms neither dictionary knows (`.lani-lead-kit/village-b2-primorska/unknown-words.txt`, made by `newwords.py` there);
  their supplement entries are left to do (each wants its lemma and form settled, not guessed).

## Voices (nothing spent)

The new tellings' Slovene, as voice-build would voice them (lines, the learner's choices, replies): parties 10,986
characters, arrivals 17,368, steps 123,001 (156 tellings): 151,355 in all, about twice the 77k characters the voice notes
call more than a month's credits; Jan decides when. The voices are the cast's own; nothing new
to cast.

## Found on the way (not changed)

- gasilski_dom step 6 at A2 grades "Nocoj gremo vsi plesati!" wrong (`namenilnik`) against "plesat". The infinitive after
  a verb of motion is what many people say; by the rule "a spoken form is never wrong" it should be a second `ok` with
  the same reply, or the turn tested another way. Left for the native check (changing it loses its voice clip).
- check-steps' `speakerGendered` flags "sam" and "bil/bila" anywhere in a helper's line, also when they don't refer to
  the speaker ("kamen sam pove", "kako široka bi bila"); the lines were reworded rather than the check changed.

## What's left (ordered)

1. Oglar Miha's cycle (world.json `spots.kopa`): its 62 levelled pieces (33 stage talks, 11 done, the meeting, 3
   greetings, missed, the breakout's four, 2 nights, the keeper's own meeting, 6 tales; 88 KB of JSON at A2 alone) are
   at A1 and A2. The brief's line ("Primorska's keeper has 44 levelled talks, the others 1 … give the others a cycle to
   B2") reads as the other cultures' work; Primorska's own B1/B2 cycle is the next step if "to B2" covers it too.
2. The lexicon coverage: supplement entries for the 511 word forms in `.lani-lead-kit/village-b2-primorska/unknown-words.txt`
   (re-made with `newwords.py <coverage output>`), each form's lemma settled against Sloleks, not guessed.
3. The Android content tests (Arrivals*, Celebrations*, ProjectSteps*, SceneCoherence, StepScenesRender, DialogReactions,
   WhoAnswers, IntroductionContent) and a short QA through qa-slot (one B2 step: "level:B2" then "step:mlaj/1"; one B2
   arrival: the LANI_QA_MEET path reads B1, a B2 one needs its level): not run, the machine's heavy slots were taken.
4. The native check above.
