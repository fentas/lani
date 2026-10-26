# The Italian dictionary for an Italian village (built 2026-10-26)

Jan set up a second learner, a child (profile `teo`), who learns Italian from German in the Friuli village. Jan: "a
lot of words are missing from the dictionary." Lani had only the Slovene dictionary in the repository
(`companion/lexicon/sl.json`). Italian's was designed as a download (plan 2, M7; README "Word lookup"), but no node
had one in place. When a village has no dictionary, a tapped word finds only the word packs, the villagers and the
tutor's glosses.

This round builds the Italian dictionary, makes it easy to install and hard to miss, gives the Friuli content a
supplement, keeps what is Slovene-only away from an Italian learner, and fixes two related faults: the profile env's
`~`, and words just learned that the app did not count as the learner's.

## What was built

### The dictionary: `it.json` and its download

Built with the pipeline the README documents (`companion/bin/lexicon-build`), from kaikki.org's extracts downloaded on
2026-10-26:

| Extract | Use | Size |
|---|---|---:|
| `kaikki.org-dictionary-Italian.jsonl.gz` (English Wiktionary's Italian entries, 2026-10-25) | the lemmas, glosses, genders, forms | 77 MB |
| `kaikki.org-dictionary-English.jsonl.gz` | the English words' translation tables: the pivot | 534 MB |
| `…-German`, `…-Slovene` | their words' English glosses | 99 MB, 3 MB |
| `dewiktionary/raw-wiktextract-data.jsonl.gz` | German Wiktionary: Italian words explained in German, German words' tables | 310 MB |
| `itwiktionary/raw-wiktextract-data.jsonl.gz` | Italian Wiktionary: its words' tables | 44 MB |

The steps: `--index` once per extract (6 indexes, 49 MB in all, 1-40 s each), then `--from … --lang it` (68 s), then
`--meanings --manifest --gzip` (19 s).

- **201,408 lemmas, 684,809 forms**, 289 grammar strings. 38.0 MB of JSON, 6.5 MB gzipped (`it.json.gz`), version
  `2026-10-26-43539c73`.
- **Meanings**:
  - German, Teo's base: 40,873 lemmas (22.2 %), and 94.8 % of the 2,563 different words of the Italian content it
    knows. Found by pivot 25,246, direct 9,493 (German Wiktionary's own), translations 6,134.
  - Slovene: 14,309 lemmas (7.8 %), 85.3 % of the content's words.
  - English: every lemma.
- **Memory.** A busy node's memory first stopped the build: it took 6 GB and more. The builder now builds the
  Italian extract with 1.5 GB at most, and the meanings take 1.9 GB. The output is byte for byte the same (checked on
  the Slovene extract and a 30,000-entry Italian slice). Four changes did it:
  - one `Intl.Collator` for the lemmas' sort: `localeCompare(b, lang)` made one a comparison, gigabytes outside the
    heap;
  - the file written a row at a time, not joined into one string after `Object.fromEntries` of 684,000 forms;
  - the tag sets interned;
  - the garbage collected every 20,000 lines.
- The built files are in `/mnt/scratch/github/fentas/lani-itlex-work/out/` (`it.json.gz`, `it.manifest.json`, and
  `it.json`, `it.disagree.tsv`, `it.coverage.txt`).

### How it is shipped: a download, not bundled

| | lemmas | forms | JSON | gzipped |
|---|---:|---:|---:|---:|
| `sl.json` (bundled, in the repository and the APK) | 5,774 | 43,097 | 1.8 MB | |
| `it.json` (this build) | 201,408 | 684,809 | 38.0 MB | 6.5 MB |

`sl.json` is in the repository: the bridge reads `companion/lexicon/<language>.json`, and the APK bundles
`companion/lexicon/*.json` (Gradle `phoneProject`). The Italian dictionary is about 20 times larger than `sl.json`, so
it doesn't go into the repository or the APK. This follows plan 1's decision ("Dictionaries are downloads, not
bundled", 2026-09-26). It is a download: the bridge fetches `<url>/it.manifest.json`, then `it.json.gz` when its
cache doesn't have that version. It checks the size and the sha256, and keeps both in `~/.cache/lani/lexicon/`
(`bridge/src/lexicon-download.ts`).

**To install it on the node** (the lead or Jan; this round wrote nothing under `~/.local/share/lani`):

```bash
mkdir -p ~/.local/share/lani/lexicon
cp /mnt/scratch/github/fentas/lani-itlex-work/out/it.json.gz /mnt/scratch/github/fentas/lani-itlex-work/out/it.manifest.json ~/.local/share/lani/lexicon/
# every learner at once (and Jan's visits to Italian towns):
echo 'LANI_LEXICON_URL=~/.local/share/lani/lexicon' >> ~/.config/lani/keys.env
# or only Teo's: "env": {"LANI_LEXICON_URL": "~/.local/share/lani/lexicon"} in profiles/profiles.json
```

Then restart Teo's bridge. Its log says `lexicon it: <version> (download), … lemmas`, and `GET /lexicon/status` says
`"download": "ready"`, with `"from": "download"` and then `"cache"`.

**`~` in a profile's env.** `docs/howto-second-town.md` told you to write `"LANI_LEXICON_URL":
"~/.local/share/lani/lexicon"` in `profiles.json`. `lani-session` and `lani-bridge` passed that value to the bridge as
it was: quoted, so no shell expanded the `~`, and the bridge looked for a directory literally named `~` and found no
dictionary. Now `bridgeEnv` expands a leading `~` or `$HOME` in each value of a profile's env, and the download reads a
local `LANI_LEXICON_URL` (and `LANI_LEXICON_CACHE`) with it expanded too. Without access to Teo's env, this round can't
say whether that, or simply no files in place, left his bridge without a dictionary. Either way, the steps above give
him one.

**On the phone** (Lani on this phone, no node): the APK has Slovene's dictionary, forms book and supplement, and now
Italian's supplement (`it.extra.json`, 9 KB). The phone's bridge downloads a dictionary from `LANI_LEXICON_URL` as a
node's does, but the app sets none (`HostedRuntime.phoneEnvironment`). Until the dictionaries are published at an
`https://` address (plan 1: "versioned release files"), a phone-only Italian learner gets the packs, the supplement,
the villagers and the tutor's glosses. What's left: publish `it.json.gz` and its manifest, and pass the URL into the
phone's environment (a build setting, as the relay's `BuildConfig.RELAY_URL` is).

**Said when it's missing.** A bridge whose village has no dictionary and no `LANI_LEXICON_URL` now logs once at start:
`lexicon it: no dictionary (it.json isn't in …/companion/lexicon, LANI_LEXICON_URL is unset): …; build it with
companion/bin/lexicon-build and set LANI_LEXICON_URL`. `lani-profile add` warns the same way when it adds a learner whose
village has none. It checks the repository, the environment, `lani.env`, `keys.env` and the profile's env.

### Coverage of the Italian content

`lexicon-build --coverage --lang it` counts the words of the text Teo meets: the Friuli culture pack (its dialogs,
scenes, stories, readings, arrivals, people, festivals, packs and the rest), `companion/packs/it`, and now the Italian
grammar book's pages (their titles and examples, `companion/grammar/it`).

| | share of the 27,740 words met | of the 2,680 different words |
|---|---:|---:|
| without a dictionary: the packs and the villagers' names (Teo's bridge before) | 21.1 % | 15.4 % |
| Wiktionary (`it.json`) | 97.9 % | 97.5 % |
| + the supplement (`it.extra.json`) | 99.0 % | 98.7 % |
| + the pack words and the names | **99.4 %** | 98.8 % |
| a meaning in German (one of the readings) | 92.2 % | 90.6 % |
| a meaning in Slovene | 88.0 % | 81.5 % |

Before the supplement, 63 different words were unknown (measured with September's build of the same extract). The 31
words none knows now (158 of the words met):

| Kind | Words | Met | Which |
|---|---:|---:|---|
| Names | 8 | 40 | Gorica and Rimska, which are only in Nova Gorica and Rimska cesta (the supplement has them as expressions, and the lookup finds them in the line); people: Marko, Nika, Nerina (a sheep), Humar, Novak |
| Dialect (Friulian) | 0 | 0 | the supplement has them: sbilf(s), fogolâr, pignarûl, cramaro, and the krivapete (from the Natisone valleys' Slovene) |
| Sounds and interjections | 12 | 91 | hm, shh, frrr, mmm, bzz, oooh, ssst; units g, l, ml |
| Words | 11 | 27 | the dialogs' wrong choices on purpose (venirò, vedito), the Slovene guba (in a reading: gubana's name), onomatopoeia (din, bip, glu, splash, ih, hi), siiì, boscaiola |
| Multi-word pack words without an entry | 13 of 42 | | a destra, a domani, a sinistra, apparecchiare la tavola, e tu, esprimere un desiderio, ho fame, quanti anni hai, questo è, san nicolò, sempre dritto, tre re, vino novello: found through their pack in a line |

897 words are known only as a form of another word. They are listed for checking, as for Slovene; the frequent ones
read right: è, un, sono (essere first), del, sul, va, anni, vieni. Two were wrong and are in the supplement now:
"sua", whose German through English was "sein; n", and the elided c'è, c'era, dov'è, cos'è, com'è, which were a junk
"infinitive" or English only.

The tool now splits the words none knows by kind:

- **Names**: never written small, and capitalized inside a sentence.
- **Dialect**: Friulian, written with â ê î ô û ç, or named so in a translation ("mandi: Friulian for bye").
- **Sounds and interjections**: no vowel, or a letter three times (hm, shh, oooh).
- **Words of the language**: the rest.

It also lists the multi-word pack words that no dictionary or supplement entry has. The packs find these in a line
anyway. Changes to the count, the same for every language:

- The pack words are compared as the lookup compares them, without the article ("il frico" is found as frico).
- A gender pair counts both forms ({m:piccolo|f:piccola}).
- A message select (`{g, select, …}`) and an emoji's variation selector are no longer counted as words.

### The supplement: `companion/lexicon/it.extra.json`

38 entries, machine-written (`"machine_written": true`, `"reviewed": false`), for the important gaps:

- The folklore of Nonno Bepi's stories: sbilfs, krivapete, the Orcolat.
- Friulian words: fogolâr, pignarûl, cramaro.
- The region's places as the content names them: the Collio, Cormòns, Brda, Nova Gorica, Cividale, Carnia, Duino,
  Monte Lussari, Camporosso, Monte Canin, Castelmonte, Monte Amariana.
- The stories' heroes (Lopichis, Tergesteo), Miklavž and Rimska cesta.
- The sky's meteor showers.
- agnellina, cividalesi, and two imperatives with their pronoun joined (arrotolala, immaginalo).
- The elided c'è (c'era, c'erano, ci sono: esserci), dov'è, cos'è, com'è, com'era, and eh: 22 times in the content.
- suo, as an override: its German was "sein; n".

Left out, as in `sl.extra.json`: the people's names (the villagers' come from the cast), interjections and sounds (but
eh), and the wrong choices the dialogs offer on purpose (venirò, vedito). Unlike Slovene's, it has the region's places
and the stories' names, which would come from nowhere else.

**Elided names.** A tapped "dell'acqua" found only Dell'Acqua, a surname: an elided word stays whole when that is an
entry of its own ("dov'è"). Now a name counts only when it is written capitalized, so "dell'acqua" is acqua.

**Meanings in the supplement.** An entry may now carry its meaning in Lani's other languages (`"meanings": {"de":
["Frico (Käse-Kartoffel-Gericht)"], "sl": [...]}`). It comes before the dictionary's meaning of the lemma, flagged
machine-written (`gloss_via: "claude"`). Every `it.extra.json` entry has German and Slovene, so Teo reads them in
German, not as the English fallback that asks the tutor for a gloss. An override's own meanings replace the
dictionary's. A wrong `meanings` (not a list) leaves that entry out, as any wrong entry does.

## Slovene-only things for an Italian learner

The app already gated counting cards, forms cards, the numbers module and "📋 Vse oblike · All forms" to Slovene
(`Counting.LANGUAGE`, `FormsCard.LANGUAGE`, `NumbersPractice.LANGUAGE`). Two things were not gated:

- the word card's "📚 Oblike" table (`WordFormsTable`: Slovene pronouns, the dual, the cases);
- the review's form questions (Slovene frames: "Jaz ____.", "brez", "pri").

Both were hidden only because the Italian grammar book has none of the Slovene page ids that `pageOf` names. Now:

- **`GET /forms`** answers no entries for a village in another language than Slovene (`FORMS_LANGUAGES` in
  `forms.ts`). Italian's dictionary has the forms (its present, future, imperative and the rest). The slots, pages,
  tables and questions are Slovene's, and the app has no Italian table yet.
- **`GET /state`'s `features`** leave out `counting` and `forms-cards` for a village that doesn't speak Slovene
  (`stateFeatures`). Their routes already answered 400.

## Two related fixes

**Words just learned were "not in your words"** (Teo's tablet: Learn on Home, then the same run's practice). Two causes:

1. The app saves a pack run's cards only when the run ends (`finishPack`, through the outbox), and the word card's
   "✓ In your words" came only from the bridge's `known`. During the run's practice, a word just introduced was nobody's
   yet, in any language.
2. The bridge's `known` matched the learner's cards by item id or by exact content. An Italian card is "la casa" and
   the dictionary's entry is "casa", so they never matched. Slovene has no articles, so it worked there.

Fixes:

- The bridge compares a card's word as a pack word is compared, its article aside: the card "la casa" is the
  dictionary's casa.
- The app keeps a pack run's words once introduced (`WordsController.fresh`, from `PackLearnScreen`) and checks an entry
  against the deck's cards and these (`Words.yours`): by item id, or by the card's word against the entry's lemma,
  article aside, with a dictionary or without one. The exercise's dots (KnownWords) count them too.

**`lani-profile add --gender male|female`** (`m`, `f`). Teo's profile had no gender, so the village spoke to him in
the masculine and his own lines were voiced female; the lead set it by hand. Left out, the CLI warns: "Warning: no
gender. The village speaks to Teo in the masculine forms, and Teo's own lines in the dialogs are said in the female
voice. Set it: --gender male|female (or learner.gender in …/learner-profile.json)."

## Tests

- **Bridge**:
  - `bunx tsc --noEmit -p .` clean.
  - The whole smoke passes: 2,606 checks, `LANI_SMOKE_PORT=19900`, 125 s, the phone bundle included. The friuli
    section passes again with the supplement in place (70 checks).
  - New: `meanings.ts`, `friuli.ts`, `pairs.ts`, `pairing.ts` (see the commits).
    - An Italian verb in the fixture: mangio is mangiare, first-person singular present, essen (German Wiktionary,
      direct), in the Lexicon and over HTTP (`base=de`).
    - The supplement's meanings and override.
    - The elided surname.
    - The manifest's content coverage.
    - `fetchLexicon` from `~/lex`.
    - `GET /forms` empty and `GET /state` without counting or forms cards for the Italian village.
    - casa's two entries known once la casa is learned.
    - `lani-profile add --gender` (male, F, a wrong value) and its two warnings.
    - A profile env value with `~` expanded in the session command.
- **The builder**: its output is byte for byte the same as before on the Slovene extract and a 30,000-entry Italian
  slice. Real lookups from German on the built dictionary read right: sua, sono, mangio, case, vado, l'acqua and
  dell'acqua, c'è, dov'è, krivapete, Collio, fogolâr.
- **Python**: `python3 -m unittest discover -s tests` (251 tests) and `-s companion/tests` (98, 10 skipped) pass.
- **Android**: not run here. Gradle needs 3–4 GB more than the machine had above the 12 GB floor, and it was aborted
  twice. The lead runs `:app:testDebugUnitTest` in the merge gate. New and untested: three tests in
  `data/WordsTest.kt` (whose a word is: the Italian case without a dictionary, with one, and the articles per language).
  Changed and untested: `Words.kt` (`ownKeys`, `Mine`, `yours`), `WordsController.kt` (`fresh`, `learning`, `mine`),
  `WordSheet.kt`, `ExerciseLook.kt` (`rememberKnownWords`) and `PackLearnScreen.kt`. No app string changed.
- **Emulator**: not taken, for the same reason. What it should show, on a synthetic Friuli learner (Italian from
  German, a child, male; the dev bridge with `LANI_CULTURE=friuli` and the Italian dictionary):
  1. Home's "▶ Impara · Lernen" and the pack's intro, its words one by one, then "Esercitiamoci! · Üben wir!".
  2. In the practice, after the first answer, a tap on one of the run's new words (la casa, say).
  3. The word card with "✓ Tra le tue parole · Unter deinen Wörtern", not "➕ Aggiungi alle mie parole · Zu meinen
     Wörtern hinzufügen", its meaning in German, and no dots under the run's words in the exercise.
  A driver for it is in the work directory (`lani-itlex-work/qa/friuli_words.py`: bridge, install, connect, learn,
  shot; `qa/make-learner.ts` makes the learner).

## What's left

- Publish the dictionaries (it, de, en) at an `https://` address and give the phone's bridge the URL.
- A native speaker's review of `it.extra.json` (machine-written).
- An Italian forms table in the app (the present's six persons, the noun's plural, the adjective's four forms); then
  `FORMS_LANGUAGES` can take `it`.
- The Slovene dictionary is thin for today's content. With the grammar book's pages and today's content (89,476 words met, 7,464 different), Wiktionary's sl.json knows
83.1 % of the words met, with sl.extra.json 90.3 %, with the pack words and names 92.0 %. Content has grown since
September, and 2,157 different words are unknown, many of them everyday words (preveč, toliko, skozi, najraje). A
rebuild from today's Slovene extract and a longer supplement are the next step for Jan's own lookups.
