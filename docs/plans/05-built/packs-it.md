# Built: Italian's B1 and B2 word packs (packs-it, 2026-11-03)

Wave E, batch 1, section 8 (followups-14.md). Italian's packs stopped at A2: 941 different words in `companion/packs/it` and
`companion/cultures/friuli/packs`, where a B2 aim asks 2,000 learned words by Mesto (GAME.md, "Words learned per age"). This
round adds 59 packs, 32 at B1 and 27 at B2, with 1,223 words no older pack has. Everything is machine-written (Claude) and not
reviewed by a native speaker; every pack's `review` says so.

## Native check (first)

All 1,223 words, their meanings in Slovene, English and German, their examples in four languages and their notes are
machine-written. A native speaker of each language should read them; these are the ones worth a first look:

- **Italian, regional or colloquial:** `il cren` (a-tavola: horseradish as Friuli and Trieste say it; standard Italian also
  says il rafano), `il titolone` (le-notizie: a big headline, colloquial), `la tabella di marcia` (organizzarsi), `fare
  tardi` (organizzarsi: "to be late"), `prendere al volo` (muoversi), `soffiarsi il naso`, `sbucciarsi il ginocchio`
  (dal-medico), `il bidello` (i-mestieri: the school caretaker; some schools now say collaboratore scolastico).
- **Facts said in examples or notes** (each checked against general knowledge only, no source opened): Aquileia founded by the
  Romans in 181 BC and a World Heritage site; the Lombards at Cividale in 568, its Tempietto Longobardo; the Constitution of 1948;
  Pasolini writing in Friulian; the mulberry trees that fed Friuli's silkworms; the Plöckenpass (Passo di Monte Croce) to
  Austria; the Tagliamento's mouth between Lignano and Bibione; the Zoncolan's ski runs; the cover charge (il coperto);
  Italian marks 1–10 and university marks to 30 e lode; la dolina from Slovene dolina; la chiave inglese, "Engländer" in German.
  Examples that would state something that changes (a football league, a marathon, where a hospital stands) were rewritten
  about the village (11c53b32).
- **Slovene:** the prompts that had to be told apart from older words are the least natural: `korist` was avoided, but
  `posledično` (quindi), `vrsta čakajočih` (la fila), `živalska ali rastlinska vrsta` (la specie), `kos – ptič` (il merlo),
  `krilo – oblačilo` (la gonna), `pravljični zmaj` (il drago), `pod v sobi` (il pavimento), `šolski hišnik` (il bidello),
  `tekma v teku` (la corsa), `poštni naslov` (l'indirizzo).
- **German:** likewise `Kaufpreis` (il prezzo), `Viehweide` (il pascolo), `Warenlager` (il magazzino), `Arbeitsschicht` (il turno
  di lavoro), `Firmenrechnung` (la fattura), `Restaurantrechnung` (il conto), `Stolzgefühl` (l'orgoglio), `ärztliche Verordnung`
  (la prescrizione), `einen Schreck bekommen` (spaventarsi), `jemandem vertrauen` (fidarsi). The German is standard German
  (Bürgermeister, Gehsteig, Matura, Schulwart, Kren appear where Austria and Germany agree or the Austrian word is the common one);
  a Carinthian reader might prefer other words in a few places.
- **English:** British throughout (favourite, colour, lorry, pavement, the bill); prompts kept apart from older words read a
  little stiffly in places: "to be snowing", "to stay afloat", "to get moving", "the change you get back", "player's turn",
  "slice of cake, slice of bread", "to put your signature on".

## What

**B1, a child's world** (Teo, 7, learns Italian from German and aims at B1): school and feelings, friends and sport, the doctor's
calmly, the morning, chores, pets, the farm, the weather, the mountains, holidays, travelling, the library, music and drawing,
cooking, money, clothes, the vegetable garden, fairy tales (the witch kind, the dragon shy), free time, relatives, describing
people kindly, the house, road safety, trees and flowers, letters and messages, little experiments, a birthday, moving about,
and two packs of the words the village's lines use most and no pack taught: the verbs of every story (succedere, decidere,
sembrare, riuscire, accorgersi, mancare, bastare, smettere, conoscere…) and the words a story is told with (all'improvviso,
intanto, mentre, ormai, appena, purtroppo, alla fine…).

`ai-fornelli` Ai fornelli (Nonna Rosa, 22); `alberi-e-fiori` Alberi e fiori (Apicoltore Aldo, 24); `amici-e-litigi` Amici e litigi
(Tommaso, 20); `animali-di-casa` Animali di casa (Pastore Davide, 21); `buon-compleanno` Buon compleanno! (Ostessa Marisa, 20);
`che-tempo-fa` Che tempo fa? (Zia Nives, 20); `come-sei` Come sei? (Giulia, 21); `cose-che-succedono` Cose che succedono (Nonno
Bepi, 20); `dal-medico` Dal medico (Nonna Rosa, 21); `fiabe-e-magie` Fiabe e magie (Nonno Bepi, 21); `i-parenti` I parenti (Nonna
Rosa, 20); `i-sentimenti` I sentimenti (Giulia, 21); `i-soldi` I soldi (Ostessa Marisa, 21); `i-vestiti` I vestiti (Zia Nives,
22); `il-tempo-libero` Il tempo libero (Giulia, 21); `in-biblioteca` In biblioteca (Maestra Elena, 21); `in-classe` In classe
(Maestra Elena, 20); `in-fattoria` In fattoria (Mugnaio Franco, 20); `in-montagna` In montagna (Pastore Davide, 20); `in-vacanza`
In vacanza (Ostessa Marisa, 22); `in-viaggio` In viaggio (Zia Nives, 22); `la-mia-casa` La mia casa (Nonna Rosa, 21);
`le-faccende` Le faccende di casa (Nonna Rosa, 20); `lo-sport` Lo sport (Tommaso, 20); `messaggi-e-lettere` Messaggi e lettere
(Zia Nives, 20); `muoversi` Muoversi (Tommaso, 20); `musica-e-arte` Musica e arte (Zia Nives, 22); `nell-orto` Nell'orto
(Apicoltore Aldo, 21); `ogni-mattina` Ogni mattina (Giulia, 20); `per-strada` Per strada (Maestra Elena, 20);
`piccoli-scienziati` Piccoli scienziati (Maestra Elena, 21); `raccontare` Per raccontare (Nonno Bepi, 20).

**B2, for an older learner and fine for a child** (no violence, crime, accidents, illness drama, romance; wine is Matteo's trade,
nobody drinks in an example; the news and the bills are calm and local): work, trades, running the osteria, the health service,
grown-up feelings, opinions, the news, the environment, Friuli's land, travelling far, the bank, the household bills, repairs,
renovating a house, the town hall, rules and rights, technology, culture, a meal out, farming, character, the community, getting
organised, B2's linking words (tuttavia, eppure, purché, affinché, sebbene, anzi, insomma, a meno che), keeping fit, studying,
history.

`a-tavola` A tavola in osteria (Ostessa Marisa, 21); `connettivi` Le parole che legano (Maestra Elena, 20); `i-mestieri` I mestieri
(Fabbro Bruno, 21); `il-carattere` Il carattere (Nonno Bepi, 21); `il-lavoro` Il lavoro (Vignaiolo Matteo, 22); `il-territorio`
Il territorio (Mugnaio Franco, 20); `in-banca` In banca (Vignaiolo Matteo, 20); `in-forma` In forma (Pastore Davide, 20);
`in-municipio` In municipio (Maestra Elena, 21); `l-ambiente` L'ambiente (Apicoltore Aldo, 21); `l-osteria` L'osteria come
azienda (Ostessa Marisa, 22); `la-campagna` Il lavoro dei campi (Mugnaio Franco, 20); `la-comunita` La comunità (Nonno Bepi, 20);
`la-cultura` La cultura (Zia Nives, 20); `la-memoria` La memoria e la storia (Apicoltore Aldo, 20); `la-salute` La salute (Nonna
Rosa, 21); `la-tecnologia` La tecnologia (Vignaiolo Matteo, 22); `le-emozioni` Le emozioni (Zia Nives, 21); `le-notizie` Le
notizie (Zia Nives, 21); `le-opinioni` Le opinioni (Nonno Bepi, 22); `le-regole` Le regole e i diritti (Maestra Elena, 21);
`le-riparazioni` Le riparazioni (Fabbro Bruno, 22); `le-spese-di-casa` Le spese di casa (Nonna Rosa, 20); `organizzarsi`
Organizzarsi (Maestra Elena, 19); `ristrutturare` Ristrutturare casa (Mugnaio Franco, 20); `studiare` Studiare (Maestra Elena,
20); `viaggiare` Viaggiare (Ostessa Marisa, 20).

Each word: the Italian (a noun with its article), gender and plural for every noun (a noun said only in the plural, or without
one, says so in its note), one emoji, the meaning in sl, en and de, an example in it, sl, en and de, and on about a third of the
words a note in en, sl and de (an odd plural, a gender trap such as il tema or il diploma, a false friend such as largo or la
libreria, a set phrase). Examples that address "you" or speak as "I" carry the learner's gender pair where Slovene needs it
(`{m:pozoren|f:pozorna}`). Each pack's giver is one of Friuli's thirteen, the examples set in the village and the region.

## The numbers

Counted as the bridge has a word (`normWord` of the Italian; a word in two packs is one card, `learnedWords`):

| | Different Italian pack words |
|---|---:|
| before (A1 835, A2 129; festivals 112, projects 155) | 941 |
| new at B1 (32 packs, 665 words) | 665 |
| new at B2 (27 packs, 558 words) | 558 |
| **after** | **2,164** |

Against the B2 aim (GAME.md): Trg's 1,000 were already reachable from the A1–A2 packs plus the first B1 ones; Mesto's 2,000 now
are, with 164 to spare (8 %), all of them from curated packs. The spare covers words still rusty when the milestone comes and
the festivals' and projects' words a village meets only on their day. None of the 1,223 is in an older pack, and no older pack
changed.

## How they were made

- **Sources and checks.** The packs were written as compact sources (`.packs-src/`, not committed: one line a word) and turned
  into the JSON by a small script that also checked each pack as the smoke would: the schema (`validatePack`), ids unique across
  every language's packs, 8–24 words, `review` ≤ 200 characters, every noun's gender and plural, no word an older pack has,
  `unmarkedHomonyms`, the prompt clashes of `bun test/prompt-clashes.ts` (none new: the baseline is untouched) and the
  supplement's own clash check (`it_cov_build.py --prompts`, none new).
- **Every prompt its own.** A card asks the meaning in the learner's base, split at commas and its brackets dropped, so a new word
  never shares a part with an old one: il lavoro "work, job" beside lavorare, il magazzino "Warenlager" beside the camp's Lager,
  la tempesta "tempest, gale" beside il temporale's storm. Where a new word needed an older new word's meaning, the newer pack
  gave way or the older new one narrowed (i soldi "money, coins and notes" beside i contanti's cash). No older pack's meaning
  was touched.
- **No homonyms.** Every spelling another pack has with another meaning was avoided (la vite the vine, il dado the dice, la
  cartella the satchel, il soggiorno the stay, la ricetta the recipe beside la prescrizione), so no word is marked `homonym` and
  the smoke's homonym check (`spellings.size === 8`) is unchanged here; packs-de makes it `>= 8`, which this branch doesn't need.
- **The supplement.** Every Italian form the 1,223 words and their examples use is known to a tap. Two new tables, appended
  after the others: `companion/lexicon/tools/it_cov_60_packs_b1.txt` and `it_cov_61_packs_b2.txt` (the pack verbs' forms as
  `=pack`, irregular ones with their participles; the examples' other words with their meanings: la tabellina, il fazzoletto, il
  pulmino, la buccia, il sughero, la marca da bollo's marca, Bibione, Lignano, the Zoncolan …). `companion/lexicon/it.extra.json`
  is made by `it_cov_build.py` from the file before supplement-it and every table, as friuli-projects-b set up: 2,196 entries on
  main, 2,488 now; run twice, the same file.
- **Coverage** (`lexicon-build --coverage --lang it --all`, no Wiktionary, as a phone has it): 283 → 342 files, 111,387 → 121,618
  tokens, 5,270 → 6,568 different words; a tap means **99.8 % of the tokens and 98.5 % of the different words** (was 99.8 % and
  97.8 %), in each of sl, de and en alike. The words none knows went from 115 to 97: none of them in these packs; the new tables
  also gave meanings to older content's pista, autobus, dentista, noia, medioevo, tradizione, amicizia, emozione, cantiere,
  vincitore and others. What stays: the wrong forms the lessons quote on purpose, letters spelt out, and a few older words
  (quartiere, drappo, stomaco, onore) for their owners.

## How they reach a learner

Checked against the shared fix from packs-en, cherry-picked here (6b7652fc, from 3c5f3ba5; this branch doesn't change it):

- **A new app** (`GET /packs?packs=1`): `PackSession.suggest` offers a new tutor pack, the festival's, one under way, then the
  first at the learner's level, then one at or below it, then one above, the nearest level first. An A1 learner who has done the
  A1 packs is offered A2 ones before any B1, and never a B2 while a B1 is left. The packs screen folds the packs two levels or
  more above the learner's and not started under "Za pozneje · For later": an A1 learner sees the B1 and B2 packs folded, an A2
  learner the B2 ones.
- **An older app** (0.2.698, Teo's tablet), which sends no `?packs=`: the bridge serves the curated packs up to one level above
  the learner's current level, plus every started, festival, project and tutor pack. Teo at A2 gets the B1 packs, written for
  him, and none of the B2 ones; at A1 neither.
- **The road** (`RoadGather.packOrder`) already took the packs under way first, then the easiest level first, so the B1 and B2
  words come after A1's and A2's.
- **The window of requests** doesn't hold packs: a pack is offered from Home's words tile and the packs screen only.
- **The sheets** (prints/sheet.ts) took a word's meaning from the packs in file order, so A1 sheets could print a B1 pack's
  wording; packs-de's fix, cherry-picked (9f52ee3e, here edecbf27), reads the A1 and A2 packs first. `school-prints --language it --level all
  --html-only` in de, en and sl, main against this branch: the B1 and B2 sheets and booklets are the same; four A1 units
  (articoli, plurale, avere, imperativo) and the A1 booklet change in one way only: l'oca, la paura and il tesoro are pack
  words now, so their line shows Italian's gender and the article form as every pack word's does ("f (l'oca) Gans" for
  "(l'oca) die Gans"), the meaning the same.

## Verified (on 11c53b32, main f9339ab8 with the two cherry-picks)

- The pack checker on all 59 packs: 0 problems; every token known; no new prompt clash (`bun test/prompt-clashes.ts`: the 339
  of the baseline, nothing added in any language); no unmarked homonym; the supplement's `--prompts`: only its six old clashes.
- `bunx tsc --noEmit -p .` clean (after `bun install`); `tutor-export --check`: 140 files match; `check-culture friuli`: ok.
- The smoke and the Android suite: see "Checks" below.
- `school-prints` in de, en and sl, as above.

## Not done, or left

- **No emulator QA.** The change is content: the packs screen, its fold and the suggestion are packs-en's (and its QA). A pack of
  each level was not played on a device here.
- **Not verified with sources:** the facts listed in the native check.
- **The most frequent words of the app's Italian are no pack's**: essere, avere, fare, andare, dire, venire, volere, potere, the
  basic adjectives (piccolo, bello, grande, nuovo). They are A1, so outside this brief; a small A1 pack of them would let their
  cards count as words learned. Listed with their counts in the round's scratch list, for the lead.
- **Notes**: about a third of the words have one; the rest could get theirs from a native reader.

## Voice

The packs' words and examples are in the voice corpus (`pack words`, `examples`): **59,222 characters** for one Italian voice
(13,376 the words, 45,846 the examples). No clip was made; nothing older lost its clip (no older text changed).

## Checks

(filled in below once the smoke and the Android suite have run)

## Commits

fda8005e, 4c11e689, 1c7d8253, 0522681e, 1cfa718f, 061e358f, 63e65ae5, f9db4107, 4a5793aa, 8f9bb7f3 (B1); e90bbcee, e53ceb83,
14fd7cdd, dc82c264, 2a6b7554, 46304909, a9b91a9e, 0aa8c8f8, 1832ceae (B2); 11c53b32 (facts); cherry-picked 6b7652fc (packs-en's
3c5f3ba5: the order and older apps) and edecbf27 (packs-de's 9f52ee3e: the sheets' order).
