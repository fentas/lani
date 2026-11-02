# Built: Friuli's five parties for the ages

Friuli had the generic gathering at every age: no tradition, no dialog. Now each age has its own celebration, as Primorska's
has (`companion/GAME.md` "Ages and their celebrations"). Teo's age-ups use these, so a child can play them.

Everything here is machine-written (Claude) and marked so in `review`. Nothing has been checked by a native speaker.

## What

| Age | Celebration | Host, guests | Decor, music, place | Pack (words its turns test) |
|---|---|---|---|---|
| Accampamento | 🔥 Il primo fogolâr | Nonna Rosa; Bepi, Davide, Giulia, Lorenzo | torches; `valcek`; fire | `il-primo-fogolar` (fiaccola, cantare) |
| Borgata | 🌿 Il licôf | Franco; Bruno, Rosa, Davide, Lorenzo | spruce, bunting; `valcek`; fire | `il-licof` (frasca, tetto) |
| Paese | 🧀 La sagra | Marisa; Matteo, Rosa, Tommaso, Lorenzo | bunting, floor; `harmonika`; square | `la-sagra` (frico, ballare) |
| Borgo | 🧺 La fiera | Matteo; Aldo, Nives, Giulia, Tommaso | stalls, bunting; `harmonika`; square | `la-fiera` (gubana, comprare) |
| Cittadina | 🚩 Il palio | Elena; Bepi, Bruno, Giulia, Tommaso | banners, bunting; `godba`; square | `il-palio` (palio, vincere) |

- **Levels:** A1, A2, B1 and B2 in each: 20 dialogs, 197 lines. The validator requires A1 and A2. The lead set all four for the
  three cultures, because B2 is the default aim. A learner above B2 plays B2 (`Arrivals.levelFor`).
- **Packs:** 5 packs of 10 words. A word another pack has keeps its gloss and note, so it stays one card: 24 words
  shared, 26 new.
- **Lexicon:** licôf, villotta and fiaccola are new in `companion/lexicon/it.extra.json`, so a tap explains them and the plural
  fiaccole counts for its card.
- **The learner's right answers use no tu or Lei.** Whoever is away has their lines said by someone else (`AgeUps.cast`), so a
  right answer that said "Lei" could land on someone the learner says tu to.
- **Short on the card.** The moment's card shows `about` in two languages, so each is about as long as Primorska's (at most 154
  characters in Italian, 169 in German). An earlier draft ran to 293 and pushed "Le novità" off the screen.
- **A turn's question doesn't say what its right answer tests**, as far as it can without reading oddly: "vieni con me?", not "vieni a ballare?".
- **Tested forms are the pack's own:** a verb as its infinitive, a noun as its singular or plural. Italian has no Wiktionary
  dictionary in the repository, so other forms of a verb (compriamo, comprerei) would neither pass the stem guess nor count
  for the card.

## How each tradition was settled

Every claim the game makes about a custom, with where it comes from. Pages marked "read" were opened. The rest come from a search
result's summary and weren't opened, so a native check should look at those first.

**Il primo fogolâr**
- The fogolâr is a large hearth, a meeting place for families, the heart of the kitchen, where they talked over daily matters,
  prayed and planned the future: Turismo FVG, https://www.turismofvg.it/en/food-and-wine/discovering-the-fogolars (read).
- Friulian emigrants' clubs are called Fogolâr Furlan: same page (read); John Paul II to the Fogolâr Furlan of Rome, 1989,
  https://www.vatican.va/content/john-paul-ii/it/speeches/1989/october/documents/hf_jp-ii_spe_19891021_fogolar-furlan.pdf (not opened).
- A first camp's fire isn't a kitchen hearth: calling it "il nostro primo fogolâr" is the game's own figure, not a custom.
- A villotta is a popular song, especially of Friuli, four lines and a refrain: https://en.wikipedia.org/wiki/Villotta (read).
  The dialogs say "four lines" and leave out the refrain.
- More than three thousand villotte collected ("Dai raccoglitori si contano oltre tremila le villotte"): La voce di Furlania,
  2 November 1944, https://manoscrittisbhu.comune.udine.it/uploads/PERIODICI/voce_di_furlania/1944/La_voce_di_Furlania_19441102.pdf (read).

**Il licôf**
- Friuli's word for the feast at a finished roof, "lincòuf o licôf (Friuli)", a meal for the builders, a branch or small tree as
  the sign, and today often a flag: https://it.wikipedia.org/wiki/Posa_della_bandiera_sul_tetto (read).
- It is alive: the licôf at Udine's via Derna site, roof finished, a meal for workers and masons (Telefriuli, 7 June 2025,
  https://www.telefriuli.it/cronaca/riqualificazione-di-san-domenico-licof-per-il-cantiere-del-pinqua/, read); the frasca raised on the
  keep of Colloredo di Monte Albano, "il momento di festa per celebrare la fine di un lavoro importante" (Corriere Nazionale,
  4 August 2026, https://www.corrierenazionale.net/2026/08/04/celebrata-ieri-la-tradizione-del-licof/, read).
- "Dicono che porti fortuna" rests on the frasca as a sign of good omen in Veneto's related ganzèga:
  https://www.dbhotelverona.it/gazega-o-ganzega-festa-di-fine-lavori/ (not opened). Not a Friulian source: check it.
- **Not settled:** who pays. Wikipedia doesn't say the owner does, so the text says "chi ha lavorato viene invitato a pranzo" and
  names no payer. Nor do the sources mention ribbons: the texts don't either, but the `spruce` decor draws a little tree with
  ribbons, in Friuli's blue and gold. A small tree is in the source; the ribbons are the picture's.
- Slovene's likof is Primorska's Zaselek too. This is not a copy: both sides of the border have the custom under related words.

**La sagra**
- The yearly feast of a patron or of a church's consecration, or a popular feast of a dish: https://sapere.virgilio.it/parole/vocabolario/sagra (read).
- Frico made with potatoes and Montasio, the flag dish of Friulian cooking: https://www.italia.it/it/friuli-venezia-giulia/cosa-fare/prodotti-piatti-tipici-friuli-venezia-giulia (read).
  Born from leftover cheese, hedged with "dicono": https://www.scattidigusto.it/2020/09/21/friuli-venezia-giulia-frico-il-piatto-della-tradizione-e-la-ricetta-originale/ (not opened).
- A village sagra about frico, Carpacco, with music: https://siviaggia.it/notizie/sagre-feste-friuli-venezia-giulia-agosto-2026/596630/ (read).
- Evenings of ballo liscio at a Friulian sagra, Zoppola: https://siviaggia.it/notizie/sagre-feste-friuli-venezia-giulia-luglio-2026/596582/ (read).
  That the liscio includes the waltz and the polka is general knowledge, no source for Friuli in particular.
- **Not used:** breâr, the wooden dance floor. I found no source for the Friulian word, so the text says "pista da ballo".

**La fiera**
- The cramârs: spice sellers who crossed the Alps on foot to German lands, goods bought in Venice, kept in the crassigne, "una sorta
  di piccola cassettiera di legno", carried "come se fosse uno zaino": https://www.quotidiano.net/itinerari/cosa-fare/sutrio-festa-cjarsons-carnia-ricetta-969b3b6c (read).
  Cloth as well: "venditori di tele, di spezie" (Ferigo, https://forumeditrice.it/percorsi/storia-e-societa/varia/le-cifre-le-anime/di-alcuni-cramari-di-povolaro-carnia-1596-1846/download_capitolo, not opened).
- Gubana from the Natisone valleys, snail-shaped: the italia.it page above (read). Walnuts, raisins, pine nuts:
  https://www.italia.it/it/friuli-venezia-giulia/cosa-fare/cividale-del-friuli-e-valli-del-natisone (not opened).
- A real fair of the area: Gorizia's Fiera di Sant'Andrea, about 200 stalls at the end of November:
  https://www.italia.it/en/friuli-venezia-giulia/gorizia/things-to-do/event-the-saint-andrew-s-fair-gorizia (not opened). Only the pack's note names it.
  Primorska's semenj leans on the same fair, so the party itself leans on the cramârs and not on it.
- The first fair of a town of the game's own is the game's fiction. So is a cramâr walking in today: the sources put them
  mostly in the 1700s and 1800s, and the game's pedlar is one already.

**Il palio**
- A tournament of Cividale on St Donatus's day, made permanent by the statutes of 1368, the winner's palio of precious cloth, often
  crimson or golden damask, the last one's a piglet, a sparrow hawk or a cock: https://consorziocastelli.it/visiting-castles/thematic-itineraries/festivals/cividale_palio (read).
- Cividale's districts compete today, in costume: https://borghibellifvg.it/en/the-villages/cividale-del-friuli/traditions (read);
  "cortei in costume, duelli e gare tra i borghi": the siviaggia August page above (read).
- A gonfalone with the civic arms at public ceremonies: Udine's regulation, https://www.comune.udine.it/content/download/3023/49410/version/current/file/regolamento_gonfalone.pdf (read).
- A band marching through a Friulian village: ANBIMA FVG, https://www.anbimafvg.it/index.php/2026/06/11/festeggiamenti-per-i-140-anni-della-banda-di-sutrio/ (read).
- **The game's own:** a new town holding a palio at all, and the gonfalone and the band leading it. Each part is real in Friuli; the
  sources don't show them together at Cividale's palio. The texts call what happens at Cividale Cividale's and don't say every
  town does it.

## What the app does

- Watch again: `AgeUps.milestones` reads the current culture's file, so a village past an age sees the new party in the chronicle.
  An earlier age's pays nothing. The village's own age, if it only had a gathering, pays once from the chronicle, as any first
  party does (`FriuliCelebrationsTest`).
- An older app bundles the celebrations it was built with, so a tablet on 0.2.698 keeps the gathering until it updates. The
  bridge serves the five packs at once, as new packs. A village in the middle of a project doesn't notice: no project,
  building or step changed.
- The stage's colours are the culture's since 4dbd6960 (shared with Kärnten and Lakeland).

## Checks

See the report for the numbers of the final run: `check-culture friuli`, `check-celebrations friuli`, `prompt-clashes`,
`learner-content`, tsc, the smoke, `tutor-export --check`, the Android suite and Python. `FriuliCelebrationsTest` is new.

QA: `LANI_DEV_DATA=<make-learner.ts --target it --base de --name Teo> LANI_CULTURE=friuli LANI_CHILD=1 LANI_QA_SETUP_PAIR=it-de
LANI_QA_LEARNER=Teo LANI_QA_PARTY_AGE=mesto LANI_QA_PARTY_LEVEL=A1 companion/bin/qa-slot --steps party`. Run to the end on a synthetic
Teo for the palio at A1 and the first hearth at B2, with the chronicle's "Watch again" after each.

**What QA doesn't show.** The synthetic village has only the host on the stage, so one person says every line (`AgeUps.cast`
standing in for the guests). A stage full of the cast is covered by the tests, not seen on a screen. The licôf, the sagra and the
fair were not played on the emulator, and all of it ran on a phone's screen, not on Teo's tablet.

## Native check

Italian, German and Slovene are all machine-written. Ask first:

1. **licôf:** is "il licôf" natural in Italian spoken by children in the Collio, or only in Friulian-speaking Friuli? Does Gorizia's side
   say it? Is "Dicono che porti fortuna" true of it?
2. **Toasts:** "Alla salute!" and "Cin cin!" are both accepted. Is there a Friulian toast that belongs here? I found no source, so
   the parties use none.
3. **Friulian words in Italian lines:** fogolâr (with the plural fogolârs), licôf, cramâr, crassigne, villotta (Italian spelling; Friulian
   may differ). `la crassigne` as a feminine in Italian.
4. **Slovene:** "villotta" is left as it is, declined as "villotto"; "palio" as "paliu", "palia". Čedad for Cividale. "plamenica" as the
   prompt for fiaccola, because bakla is taken by torcia.
5. **German:** "brennende Fackel" (fiaccola), "Festzug" (corteo) because Fackel and Umzug are taken by torcia and sfilata; "Palio"
   masculine, "Crassigne" feminine, "Fogolâr" masculine.
6. **B2 lines** for register: "Tocca ferro!", "a stomaco vuoto", "regga a neve, bora e nipoti", "non tornava a casa a mani vuote".
7. **A child's reading:** the toasts raise glasses at every level, as Primorska's do. The lines don't say what's in them, and the
   Tabor B1 toast says "anche con l'acqua".

## Voice

No credits spent. What a voice build would take (Italian, the culture's voice cast):

| | Characters |
|---|---|
| What the people say, with their replies, all 20 dialogs | 13,713 |
| The learner's choices, if those are voiced | 3,812 |
| The packs' words and examples (some shared words' clips exist) | 1,872 |

By level the people's lines are A1 2,598, A2 3,272, B1 3,674, B2 4,169. Primorska's five at A1 and A2 are 4,996. Jan decides.

## What's left

- The `spruce` decor's ribbons, as above: the picture says more than the sources do.
- A Friulian toast and breâr, if a native speaker or a source can settle them.
