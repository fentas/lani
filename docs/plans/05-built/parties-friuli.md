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

Every claim the game makes about a custom, with the page it comes from. All of these pages were opened and read. What no page says
was reworded or dropped, and is listed as such.

**Il primo fogolâr**
- The fogolâr: "un caminetto di grandi dimensioni", "un luogo di incontro e condivisione per le famiglie", where they gathered to
  "discutere questioni quotidiane, pregare e pianificare il futuro"; and the emigrants' clubs: "le associazioni degli emigrati e dei loro
  discendenti portano il nome di 'Fogolâr Furlan'": Turismo FVG, https://www.turismofvg.it/en/food-and-wine/discovering-the-fogolars.
- A villotta is a popular song, especially of Friuli, four lines and a refrain: https://en.wikipedia.org/wiki/Villotta. The dialogs say
  "four lines" and leave out the refrain; the pack's note has both.
- More than three thousand villotte collected ("Dai raccoglitori si contano oltre tremila le villotte"): La voce di Furlania,
  2 November 1944, https://manoscrittisbhu.comune.udine.it/uploads/PERIODICI/voce_di_furlania/1944/La_voce_di_Furlania_19441102.pdf.
- **The game's own:** a first camp's fire isn't a kitchen hearth, so "il nostro primo fogolâr" is a figure, and the song and story that
  evening are the party's. No page says families sang at the fogolâr, so no line says it.

**Il licôf**
- Friuli's word for the feast at a finished roof, "lincòuf o licôf (Friuli)", a meal "offerta ai muratori", a branch or small tree as
  the sign: https://it.wikipedia.org/wiki/Posa_della_bandiera_sul_tetto. The flag some sites use now is that page's subject.
- It is alive: the licôf at Udine's via Derna site, roof finished, a meal for workers and masons (Telefriuli, 7 June 2025,
  https://www.telefriuli.it/cronaca/riqualificazione-di-san-domenico-licof-per-il-cantiere-del-pinqua/); the frasca raised on the keep of
  Colloredo di Monte Albano, "il simbolo concreto di un traguardo" (Corriere Nazionale, 4 August 2026,
  https://www.corrierenazionale.net/2026/08/04/celebrata-ieri-la-tradizione-del-licof/). So a line says the branch is the sign that the work is done.
- The frasca also over a door where wine is sold (the pack's note): "si esponeva un ramo di un albero fuori dalla porta: la frasca,
  'frascje' in lingua friulana": https://www.fogolarlimbiate.it/?page_id=19.
- **Dropped:** "Dicono che porti fortuna". The only page saying so is about Veneto's ganzèga.
- **Not claimed:** who pays. The Wikipedia page doesn't say the owner does, so the text says "chi ha lavorato viene invitato a pranzo".
  Nor do the texts mention ribbons: the `spruce` decor draws them, in Friuli's blue and gold, as a picture and not as a claim.
- Primorska's Zaselek has the likof too. This is not a copy: each side of the border has its own source for the custom.

**La sagra**
- The yearly feast of a patron or of a church's consecration, or a popular feast of a dish: https://sapere.virgilio.it/parole/vocabolario/sagra.
- Food stands and orchestras at a Friulian sagra, Azzano Decimo: "serate danzanti con orchestre", waiting "ai chioschi":
  https://www.viaggiando-italia.it/sagra-della-faraona-ad-azzano-decimo-2026-programma-date-e-cosa-sapere-per-vivere-la-festa/.
- Frico with potatoes and Montasio, the flag dish of Friulian cooking: https://www.italia.it/it/friuli-venezia-giulia/cosa-fare/prodotti-piatti-tipici-friuli-venezia-giulia.
  Of Carnia, first made from "i ritagli del formaggio", when "non veniva sprecato proprio nulla", with potato, Montasio and onion:
  https://www.scattidigusto.it/2020/09/21/friuli-venezia-giulia-frico-il-piatto-della-tradizione-e-la-ricetta-originale/.
- A village sagra about frico, Carpacco, with music: https://siviaggia.it/notizie/sagre-feste-friuli-venezia-giulia-agosto-2026/596630/.
- **The waltz and the polka take two steps.** A Friulian sagra has "serate di ballo liscio" (Zoppola,
  https://siviaggia.it/notizie/sagre-feste-friuli-venezia-giulia-luglio-2026/596582/), and the liscio's dances include the mazurka, the waltz and
  the polka (https://en.wikipedia.org/wiki/Liscio, of Romagna's origin; "Valzer, polka e mazurca", https://www.rsi.ch/rete-due/programmi/cultura/konsigli/La-rivincita-del-liscio--3869645.html). No Friulian page names the two dances itself,
  so the card says "il liscio, valzer e polca", and it is on the native list.
- **Not used:** breâr, the wooden dance floor. I found no source for the Friulian word, so the text says "pista da ballo".

**La fiera**
- The cramârs: they crossed the Alps on foot to German lands with goods bought in Venice, kept in the crassigne, "una sorta di piccola
  cassettiera di legno", carried "come se fosse uno zaino": https://www.quotidiano.net/itinerari/cosa-fare/sutrio-festa-cjarsons-carnia-ricetta-969b3b6c.
  And what they sold: "i cramars: venditori di tele, di spezie, di medicinali semplici e compositi, di droghe" (Ferigo,
  https://forumeditrice.it/percorsi/storia-e-societa/varia/le-cifre-le-anime/di-alcuni-cramari-di-povolaro-carnia-1596-1846/download_capitolo).
- Gubana from the Natisone valleys, "un dolce ripieno di noci, nocciole, pinoli, uvetta e altri 15 ingredienti":
  https://www.italia.it/it/friuli-venezia-giulia/cosa-fare/cividale-del-friuli-e-valli-del-natisone. Snail-shaped: the italia.it page on typical dishes, above.
- Gorizia's Fiera di Sant'Andrea at the end of November, in the pack's note: "Fiera di Sant'Andrea (30 novembre), la più antica e importante
  della città", https://www.centerbratuz.org/piazza-vittoria.html; 29 November to 1 December in 2025,
  https://www.go2025.eu/en/imports/events/turismo-fvg/2025/novembre/saint-andrew-fair-2025. Primorska's semenj leans on the same fair, so the
  party itself leans on the cramârs and not on it.
- **The game's own:** the first fair of a town of its own, and a cramâr walking in today. The sources put them in the past, and the game's
  pedlar is one already.

**Il palio**
- A tournament of Cividale on St Donatus's day, made permanent by the statutes of 1368, the winner's palio of precious cloth, often
  crimson or golden damask, the last one's a piglet, a sparrow hawk or a cock: https://consorziocastelli.it/visiting-castles/thematic-itineraries/festivals/cividale_palio.
- Cividale's districts compete today, in costume: https://borghibellifvg.it/en/the-villages/cividale-del-friuli/traditions;
  "cortei in costume, duelli e gare tra i borghi": the siviaggia August page above.
- A gonfalone with the civic arms at "pubbliche manifestazioni, ricorrenze e cerimonie": Udine's regulation,
  https://www.comune.udine.it/content/download/3023/49410/version/current/file/regolamento_gonfalone.pdf.
- A band marching through a Friulian village: "una sfilata delle formazioni bandistiche nelle vie del paese", ANBIMA FVG,
  https://www.anbimafvg.it/index.php/2026/06/11/festeggiamenti-per-i-140-anni-della-banda-di-sutrio/.
- **The game's own:** a new town holding a palio at all, with the gonfalone and the band leading it. Each part is real in Friuli; the
  pages don't show them together. No line calls it a tradition of that town: only Cividale's is Cividale's.

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
   say it?
2. **The sagra's dances:** a Friulian sagra has the liscio, and the liscio has the waltz and the polka, but no Friulian page I found names
   the two together. Do people there dance them at a sagra?
3. **Toasts:** "Alla salute!" and "Cin cin!" are both accepted. Is there a Friulian toast that belongs here? I found no source, so
   the parties use none.
4. **Friulian words in Italian lines:** fogolâr (with the plural fogolârs: the Turismo FVG page has only the singular), licôf, cramâr, crassigne, villotta (Italian spelling; Friulian
   may differ). `la crassigne` as a feminine in Italian.
5. **Slovene:** "villotta" is left as it is, declined as "villotto"; "palio" as "paliu", "palia". Čedad for Cividale. "plamenica" as the
   prompt for fiaccola, because bakla is taken by torcia.
6. **German:** "brennende Fackel" (fiaccola), "Festzug" (corteo) because Fackel and Umzug are taken by torcia and sfilata; "Palio"
   masculine, "Crassigne" feminine, "Fogolâr" masculine.
7. **B2 lines** for register: "Tocca ferro!", "a stomaco vuoto", "regga a neve, bora e nipoti", "non tornava a casa a mani vuote".
8. **A child's reading:** the toasts raise glasses at every level, as Primorska's do. The lines don't say what's in them, and the
   Tabor B1 toast says "anche con l'acqua".

## Voice

No credits spent. What a voice build would take (Italian, the culture's voice cast):

| | Characters |
|---|---|
| What the people say, with their replies, all 20 dialogs | 13,725 |
| The learner's choices, if those are voiced | 3,804 |
| The packs' words and examples (some shared words' clips exist) | 1,872 |

By level the people's lines are A1 2,598, A2 3,286, B1 3,672, B2 4,169. Primorska's five at A1 and A2 are 4,996. Jan decides.

## What's left

- A Friulian toast and breâr, if a native speaker or a source can settle them.
- Whether Friulian sagre dance the waltz and the polka by those names (native list).
