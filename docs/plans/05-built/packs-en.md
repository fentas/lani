# Built: English B1 and B2 word packs, and the packs by level (packs-en, 2026-11-03)

Wave E, batch 1, section 10 (followups-14.md): B1 and B2 packs for English, in the Lake District, so that a learner can
reach the B2 aim's word goals from curated packs alone. Built on main f9339ab8. It also holds three fixes that every
packs branch shares (packs-en owns them, the lead relays them): the packs by the learner's level and the old-app
filter, sheets that read only the packs of their level, and "started" counted only through a pack's own words.

## For the native check (first)

Everything here is machine-written (Claude). Every pack's `review` says so. Please check:

- **All 46 new packs** (`companion/cultures/lakeland/packs/`, listed below): the English (British usage, the examples'
  naturalness), and the Slovene, Italian and German meanings, examples and notes (1,104 words, 316 of them with notes).
- **Meanings bent to keep each prompt unique.** A card is asked by its meaning, so no two English words may share a
  meaning part in a base (`bun test/prompt-clashes.ts`). The most likely to read stiffly:
  - sl: smer poti (route), prihodek od prodaje (turnover), zrel po duhu (mature), pereče vprašanje (issue),
    vrsta čakajočih (queue), blagajna v trgovini / registrska blagajna (checkout / till), skalni žleb (gully),
    obris na obzorju (skyline), gorski vrh (plateau's neighbour, no peak), denarni dolg (debt), vpisati se (to sign up),
    pasti na izpitu / opraviti izpit (to fail / to pass), popravljati napake (to correct), spraviti v red (to fix),
    zakrpati (to mend), peči pecivo / peči meso (to bake / to roast), mešati z žlico (to stir), športni sodnik (referee),
    izid tekme (score), partija (game), nogometno igrišče (pitch), občinski svetnik (councillor), kriv dejanja (guilty),
    poln miru (peaceful), šolsko spričevalo (school report), lazar (slug), vtikljiv (nosy), dobrosrčen (kind),
    brada iz dlak (beard, beside chin brada), nekodrav (straight hair), imeti oblečeno (to wear).
  - it: accendere un apparecchio (to switch on), storia come materia (history), tirare su i figli (to raise),
    diventare grande (to grow up; its example "Sono diventato grande in una fattoria" is stiff), nipote maschio /
    femmina (nephew / niece), chi ospita (host), rompere i rapporti (to fall out), rimestare (to stir), impervio
    (rugged), freddino (chilly), coperto di ghiaccio (icy), convinto (sure), infortunato (casualty), spettatori (audience),
    pensione di vecchiaia (pension), diceria (rumour), tragitto (route), spina elettrica (plug), ricciuto (curly),
    non riccio (straight hair), area boschiva (woodland).
  - de: Kaufpreis (price), ganz ehrlich (honestly), sich sicher (sure), zufolge (according to), Pressevertreter (the
    press), Geldschuld (debt), wert sein (worth), Faktura (invoice), in der Pfanne braten / im Ofen braten (fry /
    roast), eingießen (pour), Teespender (tea urn), Felsrinne (gully), Bergspitze (peak's neighbour), Kehle (throat),
    das Stricken, das Singen, als Hobby sammeln, in stabilem Zustand (stable), erwachsen im Verhalten (mature),
    Rauchfangkehrer (Austrian chimney sweep), erste Klasse (first class post), Rente (pension), nicht gelockt (straight
    hair), anhaben (to wear), Tiere aufziehen (to rear).
- **Notes on false friends and British usage** (sympathetic, sensible, actually, eventually, magazine, library, chef,
  agenda, stranger, embarrassed; petrol, pavement, queue, rubber, trolley, car park): that the base's side of each
  note is true for that base.
- **The supplement's 286 new entries** (`companion/lexicon/en.extra.json`): their sl, de and it meanings.
- **The app's two new strings** (`packsScreen.forLater`, `packsScreen.forLaterWhy` in sl, en, de, it): "Za pozneje ·
  For later", "Besede za {levels}: pridejo na vrsto, ko se tvoja raven dvigne".

## The numbers

"Words learned" counts a word once however many packs have it (`learnedWords`: its spelling, a word marked homonym by
its meaning). Distinct English words in the curated packs (`companion/packs/en/` and the Lake District's `packs/`):

| | Before | After |
|---|---:|---:|
| Packs | 83 | 129 (+25 B1, +21 B2) |
| Cards (pack words) | 1,108 | 2,212 |
| Distinct words | 1,047 (A1 841, A2 206) | **2,151** (B1 +600, B2 +504) |

The B2 aim asks 1,000 words learned by Trg and 2,000 by Mesto (GAME.md, "Words learned per age"). A1 and A2 give 1,047,
so Trg is reached with the starter packs alone, and B1 adds 600 more. All four levels give 2,151 for Mesto's 2,000:
151 words (7.5 %) to spare. The plan had 54 packs and 1,296 words (2,343, 17 % to spare). The other eight packs are
planned, with their words chosen and checked for repeats, and listed under "What's left".

## The packs

Every pack has 24 words, a giver from the cast, a title and description in en, sl, it and de, and the `review` line.
Each word has its meanings in sl, it and de and an example in all four. A countable noun has its `plural`, there is no
gender (English has none), and a note in all four languages where something is worth knowing (false friends, British
and American usage, irregular forms, uncountables, prepositions, silent letters). The ids are the English words in
kebab case. They are unique across all cultures (the other languages' ids are in their own languages), and a word id
is one English word in every English pack (the smoke's lakeland check).

| Level | Pack | Giver | Theme |
|---|---|---|---|
| B1 | at-work | Mrs Birkett | jobs, shifts, wages, applying |
| B1 | lambing-time | Shepherd Jack | the hill farm in spring |
| B1 | feeling-poorly | Granny Maggie | symptoms, small injuries, medicine |
| B1 | how-do-you-feel | Poppy | feelings |
| B1 | what-do-you-think | Old Wilf | opinions, agreeing |
| B1 | the-local-news | Auntie Edna | the local paper, the radio |
| B1 | up-on-the-fells | Shepherd Jack | ridges, crags, bogs |
| B1 | going-on-holiday | Mrs Birkett | travel |
| B1 | money-matters | Mr Tyson | money |
| B1 | odd-jobs | Mr Dixon | repairs |
| B1 | the-village-hall | Mrs Hartley | the village's institutions |
| B1 | shopping-in-keswick | Auntie Edna | shops and shopping |
| B1 | in-maggies-kitchen | Granny Maggie | cooking and baking |
| B1 | four-seasons-in-a-day | Mr Tyson | weather |
| B1 | what-are-they-like | Auntie Edna | character |
| B1 | friends-and-neighbours | Brewer Sam | relationships |
| B1 | back-to-school | Mrs Hartley | school |
| B1 | phones-and-messages | Harry | phones, the internet |
| B1 | on-the-road | Brewer Sam | driving |
| B1 | sport-and-games | Harry | sport |
| B1 | in-an-emergency | Mr Dixon | emergencies |
| B1 | free-time | Brewer Sam | hobbies |
| B1 | creatures-of-the-dale | Beekeeper Arthur | wild animals and birds |
| B1 | the-body | Mrs Hartley | the body |
| B1 | what-to-wear | Granny Maggie | looks and clothes |
| B2 | a-new-line-of-work | Brewer Sam | running a business |
| B2 | at-the-health-centre | Beekeeper Arthur | health and treatment |
| B2 | ups-and-downs | Auntie Edna | stronger feelings |
| B2 | the-great-debate | Old Wilf | arguing a case |
| B2 | on-the-airwaves | Mr Tyson | news and media |
| B2 | describing-places | Shepherd Jack | what a place is like |
| B2 | further-afield | Brewer Sam | travelling further |
| B2 | paying-the-bills | Mr Dixon | income, tax, the cost of living |
| B2 | doing-up-the-cottage | Mr Dixon | renovation |
| B2 | the-parish-council | Mrs Hartley | the council, planning, petitions |
| B2 | cooking-for-the-inn | Mrs Birkett | cooking for guests |
| B2 | a-changing-climate | Beekeeper Arthur | climate and environment |
| B2 | a-matter-of-character | Auntie Edna | character, finer words |
| B2 | family-life | Granny Maggie | generations, family events |
| B2 | forms-and-red-tape | Mr Tyson | paperwork, the post |
| B2 | mountain-rescue | Shepherd Jack | a rescue call-out |
| B2 | tourists-in-the-lakes | Mrs Birkett | tourism |
| B2 | law-and-order | Shepherd Jack | crime, the court |
| B2 | books-and-stories | Old Wilf | books and literature |
| B2 | music-and-the-arts | Granny Maggie | music, theatre, the gallery |
| B2 | the-hill-farmers-year | Mr Tyson | hill farming today |

The B2 examples use B2 structures (passives, second and third conditionals, reported speech, relative clauses) and a
higher register where the word belongs there. The village is for grown-ups, so work, money, law, health and a funeral
are fine. Words that an older pack's word already meant were replaced, so a pack never teaches a word twice: fever
(temperature), peak (pike, summit), tranquil (calm), reservation (booking), paintbrush (brush), loft (attic), melody
(tune), and tup (ram). They became thermometer, plateau, bustling, travel agent, sandpaper, chimney sweep, conductor
and to bottle-feed.

How they were written: from a compact source per pack through a builder script (scratchpad, not in the repository).
A check script ran on every pack: the schema, the four languages of every field, notes in all four or none, older words
repeated, homonyms, every new prompt clash, and synonym hints. No new prompt clash (339 in the baseline, as on main).
No homonym needed marking.

## How packs reach a learner (and the shared fixes)

On main, the suggestion fell back to the first unfinished pack in the files' order once the learner's own level was
done, and the packs screen listed untouched packs in that order. With 46 B1/B2 packs, an A1 learner would have been
offered a B2 pack and seen B1/B2 packs among the A1 ones. Four commits fix this, with no English content in them, so
that the other packs branches can take them:

- **3c5f3ba5, the packs by level.** `PackSession.suggest` goes: a new tutor pack, the festival's, one in progress, the
  first at the learner's level, the first left at or below it, then one above, the nearest level first. The packs
  screen folds the curated packs two levels or more above the learner's, not started, under "▸ ⏳ Za pozneje · For later
  (n)" at the end. Home's tile counts the packs not folded. An app sends `GET /packs?packs=1` (`PackSession.VERSION`). An
  older app, which doesn't send it, gets the curated packs up to its learner's level + 1, plus every tutor's, festival's,
  project's and started pack (`packsForApp`). At A1 it sees the A1 and A2 packs, as before; at A2 also the B1 ones.
  `GET /packs/:id` serves any pack.
- **9e07e082, "started" through a pack's own words.** `learned` counts a word the learner has from another pack or a
  looked-up card. The bridge now also gives `own`, the words learned in the pack itself. Above the learner's level, a
  pack is under way, out of "For later" and sent to an older app only when `own > 0`.
- **d47937e1, sheets read only the packs of their level.** A printed sheet took a word's meaning from any pack, so the
  English A1 sheets printed the B1 pack's open (odprt) for the course's verb open (odpreti). `Dictionary.at(level)`
  reads the packs at or below the unit's level, and an A1 sheet reads the A2 packs too (1894f03a: they always did, e.g.
  Italian A1 preposizioni's il Friuli from an A2 feast pack). A word only a higher pack has falls back to the supplement and the
  course. 9f52ee3e's order (packs-de: the B1+ packs after the A1/A2 ones) still decides on B1 and B2 sheets.

The window of requests carries no packs: a villager's request runs on the learner's cards of its topics, which are only
words already learned (`Quests`, `RequestWindow`). So B1/B2 packs reach a learner only through the suggestion, the
packs screen, and their own scenes, stories or sky (none for these packs).

## The supplement and the prints

- `lexicon-build --coverage --lang en`: every word of the new packs' texts is known. There are 286 new entries and 86
  forms on older entries: the packs' own words' forms carry the packs' meanings, the parts of phrases, names and other
  words carry hand-written meanings. Words none knows: 139, against main's 151, which are the wrong forms that turns
  offer on purpose. 97.6 % of the different words are known (main 96.7 %), and 99.8 % of the words met.
- open, burn, paint and damage list their "to …" in the supplement, so the B1/B2 sheets (passive, have-it-done,
  myself) keep them as verbs beside the packs' adjective and nouns.
- `school-prints --level all --html-only` for every course (sl, it, de, en) in every base, with every B1+ pack hidden
  under the code before the cap (7caadabd) and under this code (0 lines differ on any sheet: existing sheets are as
  they were), and with the packs in (0 lines differ on every A1 and A2 sheet and booklet). On the English B1/B2 sheets, broken
  (pokvarjen), stranger (neznanec, no longer "strange's" čuden) and training (usposabljanje) take the packs' meanings.

## Voice

The packs' words and examples are in the voice corpus (bridge/src/voice.ts): 1,104 words (8,871 characters) and
1,104 examples (58,209 characters), **about 67,100 characters** for a build of the new packs in the English narrator's
voice. No credits were spent.

## Tests

- Bridge: `bunx tsc --noEmit -p .` clean. Smoke after merging main (ports 10490…, DRAMA3 10508): **3,762 checks, all
  passed**. New checks: `packLevels` (old app at A1, A2 and B1, and with no level; `?packs=1` and `2`; `own`; `/packs/:id`)
  and the prints' level cap (a B2 pack sharing open leaves the A1 to-be sheet as it was).
- Android: the full unit suite after merging main, 4,110 tests, 0 failures (PackSessionTest 9, PhoneBridgeNodeTest's
  `/packs?packs=1` parity among them).
- Python: `tests` 253 OK, `companion/tests` 179 OK (11 skipped). `tutor-export --check`: 140 files match.
- Emulator QA (`qa-slot --steps packs-later`, new and named only): a synthetic learner of English at A1 from Slovene
  (make-learner.ts --target en --base sl, LANI_CULTURE=lakeland, LANI_QA_SETUP_PAIR=en-sl). An older app's GET /packs
  holds no untouched B1/B2 pack. The packs screen lists the A1 and A2 packs and the projects. At its end, "⏳ For later ·
  Za pozneje (46)" is folded with "Words for B1–B2 …". A tap opens it, B1 first (At work · Na delu, Back to school …).
  Screenshots in the scratchpad's ux-packs-en/. Taps: reaching a B1 pack was the list's own scroll on main, among the
  A1 ones; now it is the scroll to the end, plus 1 tap on the fold.
- The check script over all 46 packs: 0 problems. `bun test/prompt-clashes.ts`: no new clash.

## What's left

1. **Eight planned packs** (`plan.json` in the scratchpad, words chosen and checked for repeats): making-plans,
   everyday-verbs, getting-things-done (B1); more-phrasal-verbs, sayings-of-the-dale, science-and-technology,
   verbs-for-the-evening-class, linking-words (B2). Writing them gives 2,343 distinct words (17 % to spare). The phrasal verbs, idioms and linking words will need the most prompt work.
2. The native check above.
3. A voice build for the packs (~67,100 characters), when Jan decides.
