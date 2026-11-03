# Built: the Lake District's parties (parties-lakeland, wave D)

Jan, 2026-11-02: "yes complete" (followups-10, 3–5). Only Primorska had `celebrations/<age>.json`; the Lake District had a
generic gathering at every age, with no dialog. It now has its own five, each from a Cumbrian custom, with a party at A1, A2,
B1 and B2. Everything written in a language is machine-written (Claude) and waits for a native check: every file says so in
its `review`.

## What

| Age | Celebration | Pack (words) | Tested words | Decor | Music | Place | Host, guests |
|---|---|---|---|---|---|---|---|
| Tabor | 🎶 A merry neet | `party-merry-neet` (9) | pie, song | torches | `valcek` | fire | Old Wilf; Granny Maggie, Shepherd Jack, Poppy |
| Zaselek | 🤝 The boon day | `party-boon-day` (9) | ladder, neighbour | bunting, torches | `valcek` | fire | Mr Tyson; Granny Maggie, Shepherd Jack, Auntie Edna, Alfie |
| Vas | 🎻 The barn dance | `party-barn-dance` (9) | partner, caller | bunting, floor | `harmonika` | square | Mrs Birkett; Mrs Hartley, Brewer Sam, Harry, Granny Maggie |
| Trg | 🍎 The crab fair | `party-crab-fair` (10) | apple, face | stalls, bunting | `harmonika` | square | Brewer Sam; Mr Dixon, Poppy, Harry, Auntie Edna |
| Mesto | 📜 Charter Day | `party-charter-day` (10) | charter, banner | banners, bunting | `godba` | square | Mrs Hartley; Old Wilf, Mr Dixon, Mrs Birkett, Alfie |

In all: 5 celebrations, 20 tellings, 177 lines, 67 wrong choices (7 of them naming a page of the grammar book), 47 words. Each
telling has 8 to 10 lines and 3 or 4 turns. A learner above B2 gets the B2 telling (`Arrivals.levelFor`).

- **B1 and B2** go beyond Primorska's A1 and A2, as the lead asked, since B2 is the default aim. What they ask for: used to, the
  present perfect with since, the first and second conditional, reported speech, "I wish I could", the third conditional and
  phrasal verbs (turn up, turn off). The English grammar book stops at A2, so those form turns name no page and are always chosen,
  never typed. They get a page when the English B1 and B2 books exist.
- **The rushbearing and the shepherds' meet** are not parties, although the brief names them as examples. Both are already festivals
  of the calendar, with their days, packs, readings and a story. A party that sold them as new would repeat them.
- **The spruce** isn't used: it is the likof's. Zaselek has flags and torches.
- **Words shared with another pack** (pie, fiddle, song, neighbour, ladder, roof, supper, apple, cart, to throw, to catch, penny,
  band, procession) mean the same as in that pack, so each stays one card. `bun test/prompt-clashes.ts`: no new clash (two were
  fixed while writing: Italian "gratis" for `for free`, "passo" for `step`).

## How each tradition was settled

No claim without a source. **Read** means the page itself was opened; **search** means only the search result's quotation could
be read (the site refused the fetch).

### A merry neet

A "Merry-night" was a night of mirth at a country ale-house in the Christmas holidays, with pies of different kinds, music and
ale, for lads and lasses of the neighbouring villages (Robert Anderson's essay on the Cumberland peasantry, 1820). The party
moves it to the fire and out of Christmas, and says why: the camp has no alehouse. "Neet" as the dialect's spelling is in the
titles below; that it means night is plain from the 1820 "Merry-night" and the dialect glossary's use of both.

- search: https://led.kmi.open.ac.uk/browse/http%3A-_--_-data.open.ac.uk-_-led-_-lexp-_-1658488632841 (Anderson's essay, quoted)
- read: https://balladindex.org/Ballads/VWP012.html ("Blackell Murry Neet", Roud 1529: dance to the fiddle, eat, drink, smoke, sing)
- read: https://en.wikipedia.org/wiki/Robert_Anderson_(poet) ("The Blackwell Merry Night" among the ballads still known)
- read: https://hefted.substack.com/p/countrystride-141-a-cumbrian-christmas ("Merryneets, where dalesfolk would gather for nights of feasting and frivolity")
- read: https://gutenberg.org/cache/epub/62370/pg62370-images.html (Folk-Speech of Cumberland: "rural ball or merry-night", "Merry-Neet")
- search: https://www.efestivals.co.uk/festivals/furnesstradition/2025 (the "Big Merry Neet" at Ulverston today)

### The boon day

Boon-ploughing and boon-shearing are "a day's plowing or shearing given gratuitously to a farmer by his neighbors on a special
occasion", with "Boon Ploughing at Burgh" in the Carlisle Journal of 1886. At Lake Country clippings, shearers went "from farm to
farm in merry succession, giving their aid, feasting on the fat of the land … until they in their turn called upon their
neighbours for help". Anderson's ballad of a clay daubing has the neighbours finish a clay cottage's walls by dusk, whisky, oatcakes,
a fiddle and dancing on the clay floor, and clay dabbins were built communally. The card keeps the fiddle and the dance to the clay
house, where they are sourced. Raising a slate roof by a boon is this village's own: no source says the dales did so.

- read: https://www.wehd.com/10/Boon_sb1.html (the English Dialect Dictionary's entry)
- read: https://djo.org.uk/household-words/volume-vi/page-450.html (Household Words VI, 1852/3, p. 450)
- read: https://gutenberg.org/cache/epub/62370/pg62370-images.html ("Clippin' … A great festival on the larger dale farms")
- read: https://www.poemhunter.com/poem/the-clay-daubin/ (the ballad)
- read: https://buildingconservation.com/articles/clay-buildings/clay-buildings.htm ("the traditional communal process of clay dabbin construction")

### The barn dance

A barn dance is traditional dancing, seldom in a barn today, community-minded, for people who don't normally dance, often with a live
band and a caller who keeps to easy dances. The Cumberland Square Eight is a real dance for four couples: across and back, stars, the
basket, a circle, a promenade. Its name is the weak point: the earliest description (1939) calls it "The Square Eight", near the
Border, and "Cumberland" may be a later addition, so the party calls it by its usual name and claims no origin. No source ties the barn
dance to Cumbria specially; the Lake District's rescue hut already holds one (`project-gasilski-dom`), and the fiddle at a Cumbrian
dance is Anderson's. "The barn is too small, so the boards are on the green" is the game's: the stage and the map draw the green.

- read: https://en.wikipedia.org/wiki/Barn_dance
- search: https://www.oxfordlearnersdictionaries.com/us/definition/english/barn-dance
- read: https://my.strathspey.org/dd/dance/1479 (4 couples, a square; the 1939 description)
- read: https://barndances.org.uk/Antony/dancecard.php?ID=779 (its figures)
- search: https://folkdancefootnotes.org/dance/a-real-folk-dance-what-is-it/about/cumberland-square-eight-l-england-scotland/

### The crab fair

Egremont's Crab Fair, held since 1267 (or 1297, Guinness says) under a royal charter, the fair's history "goes back to the
Thirteenth Century"; the Lord of Egremont gave away crab apples; the Parade of the Apple Cart throws apples to the crowd on Main
Street, ordinary eating apples now; a street market and stalls; climbing the greasy pole (dropped in 2004 by one account, on a 2024
schedule by another); gurning through a braffin, a horse collar, since at least 1852. The party's fair is this town's own, "after"
Egremont's, and says so. The sources disagree on which King Henry, so no line names one.

- read: https://calendarcustoms.com/?p=778
- read: https://theherdwick.com/2024/09/18/crab-fair-and-world-gurning-championship-all-you-need-to-know-events-times-locations-and-more/
- read: https://www.guinnessworldrecords.com/world-records/758640-longest-running-gurning-world-championships
- search: https://en.wikipedia.org/wiki/Greasy_pole

### Charter Day

Broughton's fair has been proclaimed since 1593: the Bailiff leads the procession with the ceremonial halberd to the Market Place,
the Proclamation is read from the Obelisk's steps, pennies are scattered among the crowd to the children's delight, then "Fair Cake"
and ale at a pub. Carlisle recites the rights Edward III granted in 1352 at the Market Cross, after a procession of the Mayor and the
Sword and Mace Bearers from the Old Town Hall. Kendal's Mayor's Parade is led by a band, and its first borough charter came from
Elizabeth I in 1575. What is read out at Broughton and Carlisle is a proclamation of those rights; in the game the new town reads its own
charter, and the card keeps the two apart. "Charter Day" is this town's name for it, no source's.

- read: https://www.westmorlandandfurness.gov.uk/news/2023/annual-proclamation-broughton-fair-2023
- read: https://www.cumberland.gov.uk/news/2025/international-market-comes-carlisle-august
- search: https://visit-kendal.co.uk/mayors-sunday-parade-comes-to-kendal/
- search: https://artuk.org/visit/venues/kendal-town-hall-6089

## A village already past an age

`LakelandCelebrationsTest` plays it on a synthetic village: at Vas, with Zaselek's day kept and Tabor's not known, the chronicle's
ages list the barn dance, the boon day and the merry neet, each with its celebration now, and the day where it is known; each plays its
moment on the village as it was, and has its dialog. Long past its evenings there is no bubble tonight. Had from the chronicle, an
earlier age's party pays nothing, and the village's own age's pays once, with its line and how the people remember it. So a Lake District
village that had the generic gathering gets the new party through "Watch again", and is paid for its current age's once, as Primorska's
villages were when the parties came. No migration is needed: a gathering kept no `parties` day.

## Who says what

Nothing in the five files names anyone of the cast (MentionsContentTest), and the lines of a host or a guest fit whoever stands in: no
"my inn" or "my bees", and the Slovene and Italian translations avoid a speaker's gender too, though the bridge's check of that is
for Slovene villages only. The learner's agreements in the translations are `{m:…|f:…}` pairs, and "love", "{m:lad|f:lass}", "{m:son|f:lass}"
and "mate" follow each villager's personality (a stand-in keeps the host's word).

## Decor and music: what's still the app's

- **The banners, garlands and awnings** read the Lake District's look since parties-friuli's shared commit (`MapLook.festive`).
- **The stalls' goods on the stage** are the semenj's red dots and coloured strokes. The dots read well as apples here, so nothing was asked.
- **The music is the app's three**: the accordion's waltz and polka, and the brass band. The Lake District's rescue hut's barn dance
  already has a drum, an accordion and a fiddle, and Kendal's parade a band. But a fiddle, which the merry neet and the boon day name, isn't among the sounds. It
  would be a new `Music` value, a synthesized sound (`tools/ambient_sounds.py`, `Sfx`) and a line in `celebrations.ts`'s `MUSIC`: shared code,
  not built here. An older app would fall back by `Music.of`; an older bridge's check would refuse the unknown value.

## How it was checked

On the branch merged with main (0949f159, main at 1e0cdf04):

- `bunx tsc --noEmit -p .`: clean; `bun build --no-bundle` on each changed test file; `tutor-export --check`: 89 files match (54 tools, 32 guide topics).
- The smoke (`LANI_SMOKE_PORT=9730 LANI_SMOKE_FRIULI_PORT=9733 LANI_SMOKE_TOWN_PORT=9736 LANI_SMOKE_DRAMA3_PORT=9748`): all checks passed, 3521.
- `check-culture` ok for all four cultures; `prompt-clashes`: no new clash.
- The Android unit suite through lani-slot.sh: 487 classes, 3794 tests, 0 failures, 2 skipped, among them SceneCoherenceTest (no "look" without a cue),
  MentionsContentTest (nobody of the cast named), CelebrationsContentTest and PartyStageTest. WhoAnswersTest and DialogReactionsTest read scenes, not parties;
  LakelandCelebrationsTest holds the parties to the reaction rule.
- Python: 253 tests, and 151 with 11 skipped, all passing.

On the emulator (`qa --steps party`, a synthetic learner of English from Slovene, `--no-town`), with shots in the round's scratch folder `ux-parties-lakeland/`:

- **The barn dance at A1**, on an APK built before the last translation fixes: the moment, the card, the party to its end with the chronicle's line, the village past that age, the
  chronicle's ages (the merry neet, the boon day, the barn dance, the crab fair) with "Watch again" and the card offering the party again.
- **Charter Day at B2**, on an APK of the branch before the merge with main (which changes no party): 4 turns answered, the banners and the garlands in the Lake District's colours.
- **Not played on the emulator:** the merry neet, the boon day and the crab fair (those three by the tests alone), and a wrong choice's reaction (the step takes the right ones).

The first runs stopped at "could not get back to the plain village": `bin/qa` read the village's HUD in Jan's sl · en, and on en-sl it took the Today card's "Danes" for the ring. Fixed for all
pairs in 62db179c (the labels follow the pair the phone shows), checked on en-sl as above and on sl-en (Primorska's semenj at A1, `hearths`).

- `bun test/check-celebrations.ts lakeland` and `bun test/check-culture.ts lakeland`: ok, 5 celebrations, 20 parties, 177 lines.
- `LakelandCelebrationsTest` (7 tests, new): see its header. On the way it found "charter's" isn't a form of `charter` in the app's tokens, though
  the bridge's guess passed it; Charter Day's B2 line says "The charter is" now.
- Two shared fixes came in, one of them from this branch: c572dc3a (`check-celebrations.ts` keeps the dictionary's flags; "neighbours" for
  `neighbour` failed the script, not the bridge).

## How to use

Nothing to switch on. A Lake District village's next app build bundles the parties and their packs; a bridge serves the packs at once. QA, with
a synthetic learner of English (never a real learner's data):

```
bun companion/bridge/test/fixtures/make-learner.ts <dir> --name Jan --target en --base sl
LANI_DEV_DATA=<dir> LANI_ENV_FILE=/dev/null LANI_CULTURE=lakeland LANI_QA_SETUP_PAIR=en-sl LANI_QA_PARTY_AGE=vas LANI_QA_PARTY_LEVEL=A1 \
LANI_QA_PARTY_SHOTS=<shots> companion/bin/qa-slot --no-town --steps party
```

## Tap counts

To a party from the new age's card: before, none (a gathering had no dialog); now 1 ("Celebrate with the village"), then as many taps as its lines
and turns (8 to 10 lines). From the chronicle, as in Primorska: the age's row, then the party's button on the card after its moment (Skip goes to the card).

## For the native speaker

Everything is machine-written. A spoken form is never graded wrong.

- **English**: "merry neet" as a word of the pack; "boon" in its northern sense; "Now then", "Aye", "love", "mate", "lad", "lass", "son";
  "on my watch", "Get away with you!", "Get on with you!", "carry the tune", "join in the song", "a wet old verse", "out of your neighbours' debt",
  "That's what neighbours are for", "It's on us", "turn up", "two left feet", "since I was courting", "sit this one out", "have a go", "pull a face",
  "I'd take X over Y any day", "Wrong tent", "seen to", "The wind nearly had it", "Grand!", "Mind your head!"; the caller's "Take your partners!",
  "across and back, a star, then the basket"; "market cross"; "fair cake".
- **Slovene**: vesel večer, dan sosedske pomoči, sosedska pomoč (for boon), krčma, "Živeli!" (Good health!), skrla, klin (a ladder's rung), voditelj plesa,
  plesni partner, vaški ples, zelenica (the green), lesnika, sejem lesnik, komat, namazan drog, spakovati se, mestna listina, prapor, graščinski oskrbnik
  (bailiff), helebarda, sejemski kolač; the memories after "Še vedno mislim na …".
- **Italian**: pasticcio di carne (pie), serata allegra, "aiuto tra vicini" (boon), maestro di ballo (caller), compagno di ballo, festa da ballo, fiera delle mele
  selvatiche, mela selvatica, collare da cavallo, albero della cuccagna, fare le boccacce, carta cittadina, stendardo, balivo, alabarda, dolce della fiera;
  "tesoro" for "love", "amico" for "mate".
- **German**: fröhlicher Abend, Wirtshaus, Nachbarschaftshilfe and "ein Boon", Tanzansager, Tanzpartner, Scheunentanz, Dorfanger (the green), Holzapfel, Holzapfelmarkt,
  Kummet, Kletterbaum, Grimassen schneiden, Marktflecken, Stadturkunde, Vogt, Hellebarde, Jahrmarktskuchen; "Schatz" for "love", "Kumpel" for "mate".
- **Worth a Cumbrian's eye**: whether a merry neet by a fire, a boon for a roof and a town's own Charter Day read as true to the place, since each is built from
  a custom, not copied from one.

## Credits

No voice was built and no ElevenLabs credit spent. A voice build of the new lines in the cast's voices (the villagers' lines, their replies and their puzzled
reactions, in English, all four levels) would take about 14,900 characters: Tabor 3,240, Zaselek 2,780, Vas 3,240, Trg 2,660, Mesto 2,970. The learner's own
lines (every choice, right and wrong) add about 4,400 for each voice the learners have chosen.

## What's left

- **The native check** above.
- **Voices**, as above.
- **A fiddle among the sounds**, if wanted: see "Decor and music".
- **Grammar pages for B1 and B2**: the form turns at those levels name none yet.
