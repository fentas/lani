# Built: Slovene's challenges and car drills to B2 (practice-sl, 2026-11-03)

Wave E, batch 1, section 11 (followups-14.md). Slovene had six curated modules, all A1, and four car drills that stopped at
A2 (riddles at A1 only). A learner of Slovene above A1 got no new challenge after those six, and the car, the treasure
hunt's riddle and forms stations had nothing above A2 to give. Now there are modules at A2, B1 and B2 from the course's
weak spots, and every drill reaches B2.

Everything Slovene, German and Italian written here is machine-written (Claude) and not reviewed by a native speaker; the
files say so (`review`). Forms I was unsure of were settled with fran.si and Sloleks (URLs below); naturalness and register
are for a native speaker.

## For a native check (first)

**Settled by a dictionary, to confirm:**
- *za dva dni* (the grammar page `predlogi-dva-sklona` and the module `po-kruh-po-vasi`'s `say`): Slovenski pravopis gives
  the dual of *dan* as *dnéva* (acc.), *dní* the genitive (https://fran.si/iskanje?View=1&Query=dan&FilteredDictionaryIds=134).
  Both *za dva dni* and *za dva dneva* are accepted. Is *dva dni* standard enough to be the model answer?
- *kila* for *kilogram*: SSKJ marks it *pog.* (https://fran.si/iskanje?View=1&Query=kila&FilteredDictionaryIds=130): accepted
  in `koliko-cesa`'s translation, said so in its why.
- *zmleti*, perfective of *mleti* (Pravopis: dov., *zméljem*, https://fran.si/iskanje?View=1&Query=zmleti&FilteredDictionaryIds=134);
  its l-form *zmlel* follows *mleti*'s *mlel* (`vid-delati-narediti`, the drills).

**Accepted as spoken, never a wrong choice** (each as the grammar page says, or common speech):
*gremo, greva* for *pojdimo, pojdiva*; *počakava* beside *počakajva*; *pet ljudi so prišli*, *veliko ljudi so prišli* (the page
`stevila-glagol` names it); *ne rabiš hiteti* beside *ni ti treba*; *mraz mi je*; clitic first: *Se mi mudi*, *Ti pomagam*,
*Si ga videl?*, *Ti ga vrnem jutri*; *a* for *ali* in questions; *kar se spomnim* is not offered anywhere (avoided).

**Wrong options: is each firmly wrong (nobody grown up says it)?**
- A2: *Mrzlo jaz sem*, *Pomagam te*, *Grem k babica*; *Pijem čaj z med*, *Grem s Tone*, *pod klopi* for *pod klopjo*,
  *z njega*, *z on*; *Grem v šoli*, *Sem na trg*, *iz planine*, *Nejc gre pri babici* / *od babici*; *Imam dve evri*, *Kupim
  pet jajca*, *veliko ljudje*, *kilogram jabolka*; *Ob osmih začnem napisati*, *Nehaj kupiti!*, *Skleda je prazna: Muri je
  jedel vse meso*, *Kaj narediš? Pišem pismo.*; *Rad bom kozarec rebule*, *Zala bi rada vidi konjička*, *Kupim bi*, *Rad bi
  spati*, *Ste mi pomagali* (for a request).
- B1: *ga mi*, *me ga*, *mi se*, *Dal sem ga ti*, *Moram vrniti ti ga*; *Človek, ki ga stoji*, *Knjiga, ki sem ga prebrala*,
  *v ki živim*, *v katero živim*; *Pet krav je zbolele*, *Dva gosta so prišla*, *Tri gostje*, *Koliko ovce je ušlo?*; *Če bi imel
  čas, bom prišel*, *Če bi imam čas*; *Vprašala me je, da sem lačen*, *Rekla je, ali pridem jutri*, *da pridem* (for »Pridem«
  said by Luka); *Imam mrzlo*, *Tone zanima vse o konjih*, *Mamina boli glava*, *Zalo je strah temo*.
- B2: *Grem po zdravniku* (for the doctor), *Pes spi pod stol*; *Fant, katerega sem ga videl*, *Komur ni bilo*, *Ki išče, najde*,
  *Kar išče, najde*; *Namesto da pasel ovce*, *Odšel je, ne da se je poslovil*, *dokler ne prihajam*; *Treba sem iti*, *Ni bil treba
  čakati*; *Ovca je uidla*, *Nejc je odšal*, *prenesel* for *prinesel* (the grading table's prefix rule agrees); *Naj pridi
  Luka*, *Vzami*, *Ne zapirati vrat*.

**Naturalness and register, for a native speaker:**
- Who says *ti* or *vi*: the learner says *vi* to Vida (*Lahko vam pomagam? Bi mi pomagali?*) and Jože (*Bi lahko zaprli
  okno?*, *ti* accepted), *ti* to Tone, Marko, Zala, Nejc. Right for a village?
- The modules' lines: *Pastirju Luki se je izgubila ovca*; *Kosilo je danes pri Vidi*; *Antonu je ušlo pet rojev* (does a swarm
  *uiti*?); *Zala in Nejc bi rada šla k ognju*; *Tone, kaj bi rad? Kozarec terana?*; *Gostilničarka Vida hoče vedeti vse, kar se
  govori po vasi*; *Tone si ga je kupil na sejmu* (a hat); *Kdor pride prvi, dobi najboljši prostor*; *Babica Micka pripoveduje,
  kako se je v vasi včasih živelo*; *Mlinar France ureja papirje za nov mlinski kamen*.
- The drills: *Ključ dam v žep*; *Ptica sede na streho*; *Tone vrže kladivo pod klop*; *Tone obesi zvon nad vrata*; *Jože je
  nasekal drva*; *Mojca je popravila naloge* (for marking homework); *Sinoči je iz Lukove staje ušlo pet ovc*; *Nocoj nam bo
  Janez ob ognju povedal zgodbo*; *Treba je bilo čakati*; the riddles *Ribič, ki sedi v meni, lovi postrvi*, *Na meni visi
  nataknjena vaba*, *Ljubljana me ima v grbu, čeprav me ni nihče nikoli videl*, *Dokler niso vsi siti, ne sede k mizi*, *Kar je
  nekoč pripovedovala njena mama, zdaj pove vnukom*, *Narejeno sem iz lesa, ki je tlel brez plamena*, *Iz hrasta sem, okoli
  mene so železni obroči*.
- German and Italian (the bases' explanations, instructions and meanings): not reviewed; *Harpfe, Heuharfe* for *kozolec*,
  *Ziehharmonika* for *harmonika*.

## What was there (checked on main f9339ab8)

| | Before | Now |
|---|---|---|
| Modules (`companion/modules/*.json`) | 6, all A1, texts in English only, no `order` | 24: the same six, untouched, and 6 at A2, 6 at B1, 6 at B2, every text in en, de and it |
| Transformations (`preobrat`) | 11 sets, 215 items, A1 and A2 | 21 sets, 324 items: 5 sets at A2, 4 at B1, 3 at B2 more |
| Rapid fire (`hitri-odziv`) | 8 sets, A1 | 11: how much (A2), when exactly (B1), how many times (B2) |
| Sentence building (`gradnja`) | 73, A1 and A2 | 95: 12 at A2, 8 at B1, 8 at B2 |
| Riddles (`uganke`) | 54, all A1 | 74: 8 at A2, 6 at B1, 6 at B2 |

Verified on the base: the six modules' levels and files, the drills' sets and levels by script; the brief's counts hold.

## The modules

Each names its grammar page(s) on every exercise, has a giver of Primorska's cast, twelve exercises (3 choices, a multi,
4 gaps, 2 sentences to build, a translation to type and one to say, as the A1 ones), an `order` in its level and a `review`.
The texts the learner reads are per base (`{"en", "de", "it"}`, Slovene's bases), served as plain strings in the learner's
(`validateCuratedModule`, practice-it.md), so an app of any age reads them. The grammar pages link them (`modules`), so a
page's 🎯 plays them too once they are in the app's list.

| Level | Order | Module | Pages | Giver | The weak spot |
|---|---|---|---|---|---|
| A2 | 1 | `dajalnik-komu` | dajalnik, osebni-zaimki | Babica Micka | komu: -u, -i, -om; k/h; pomagati komu; všeč mi je |
| A2 | 2 | `orodnik-s-kom` | orodnik, mnozina-skloni, osebni-zaimki | Kovač Tone | s or z; pod/za + instrumental for where; z njim |
| A2 | 3 | `kje-kam-od-kod` | kje-mestnik-orodnik, kam-tozilnik, rodilnik-predlogi | Pastir Luka | v šoli / v šolo / iz šole; na / s; pri / k / od; doma, domov |
| A2 | 4 | `koliko-cesa` | rodilnik-kolicina, mnozina-skloni, stevila-samostalniki | Gostilničarka Vida | malo vode, pet evrov, veliko ljudi, kilogram jabolk |
| A2 | 5 | `vid-delati-narediti` | glagolski-vid | Mlinar France | going on or done; the perfective present is the future; začeti + imperfective |
| A2 | 6 | `pogojnik-rad-bi` | pogojnik, dvojina | Vinar Marko | rad bi + the l-form; bi never changes; Bi mi pomagali? |
| B1 | 1 | `naslonke-mu-ga` | naslonke-red | Stari Janez | sem – se – mi – ga – je; ne bom; after da |
| B1 | 2 | `ki-ga-ki-mu` | oziralni-ki | Učiteljica Mojca | ki + its pronoun; kateri after a preposition |
| B1 | 3 | `pet-jih-je` | stevila-glagol | Čebelar Anton | dva sta, trije so, pet … je -o; veliko ljudi je prišlo |
| B1 | 4 | `ce-bi-imel` | pogojnik-ce-bi | Vinar Marko | če bo … bomo, če bi … bi; ko bi le; da bi |
| B1 | 5 | `rekla-je-da` | odvisni-govor | Gostilničarka Vida | da, ali/če, kje, naj; the tense stays, the person changes |
| B1 | 6 | `mudi-se-mi` | custva | Teta Ančka | mudi se mi, žal mi je; boli me, zebe me, strah me je; neuter in the past |
| B2 | 1 | `po-kruh-po-vasi` | predlogi-dva-sklona | Kovač Tone | po kruh / po vasi; pod stol / pod stolom; ob mizo; za babico / za hišo |
| B2 | 2 | `kdor-kar-cigar` | oziralni-kateri | Učiteljica Mojca | kateri in its cases, čigar, katerih, kdor/kogar, kar, kamor |
| B2 | 3 | `ceprav-medtem-ko` | odvisniki-napredno | Vinar Marko | kljub temu da, medtem ko, dokler ne, ne da bi, namesto da |
| B2 | 4 | `treba-je-govori-se` | brezosebno | Babica Micka | treba je (ni ti treba), govori se, plesalo se je, ne da se |
| B2 | 5 | `predpone-na-pre-pod` | predpone | Mlinar France | prepisati / podpisati; prinesti / odnesti; obšel; raznašati |
| B2 | 6 | `naj-pride-pojdimo` | ukazi | Lovec Jože | pojdimo (gremo), naj pride, ne zapiraj, nikar, Bi lahko …? |

Chosen from the course's weak spots: what the A2–B2 units teach that a learner from English, German or Italian gets wrong
and that A1's six don't drill (the cases after A1, aspect, the clitics' order, the numbers' verb, ki with its pronoun, the
conditional, prepositions with two cases). Six a level, not eight: each is played one at a time (below), so 18 is weeks
of requests; more can follow the same files. Not made into modules (they are in the drills or the course only):
`svoj`/`cigav`, `dvojina-skloni`, `nedolocni-zaimki`, `trpnik`, `vid-casi`, `pogojnik-pretekli`, `uradno-pismo`.

**What people say.** A form the grammar page names as spoken, or that adults say, is accepted in the typed answers and
never a wrong choice (the list above). The gender of what the learner says follows them (`{m:…|f:…}`), never in a
sentence's chips.

## The pace, and Jan's six

The bridge offers a curated module with a request one at a time (`ModuleStore.offered`, practice-it.md "The pace"): the
ones already asked stay listed; when none is open and none was asked today, the next by level and `order` whose giver
lives in the village; nothing above the learner's level. Nothing in the code changed. What it means for Jan's village:

- **His six stay as they are**: the files are untouched; five have their own request and `biti-sedanjik` is led to by a
  tutor's request (`helps`), so all six stay listed at any level (the lead's note, practice-it.md).
- **While one of them is open, nothing new comes.** Once all are played and none was asked today, the first A2 module
  comes (`dajalnik-komu`, Babica Micka's request), the next the day after it is played, in order; B1's after A2's, B2's
  after B1's, each only once his level is there.
- The smoke holds this on the real files (`practice.ts`: Jan's six as his village has them, one open, all played, the
  next in order, never above the level, plain strings for an older app).

**For the lead:** the new ids are distinctive (`dajalnik-komu`, `ki-ga-ki-mu`, `naj-pride-pojdimo` …), but a module Jan's
tutor published under one of them would be read as curated (`version` 1) and paced. Worth a read-only check of Jan's
`app/modules/` names against the 18 ids before deploying.

**Older apps.** Nothing new reaches an app it can't read: the modules are served as plain strings in the learner's base
(Teo's 0.2.698 reads them as any module, practice-it.md), use only exercise types the A1 ones use, and the pace is on the
bridge. Teo learns Italian, so none of this is served to the tablet. The drills are data the app has read since they
exist (`Drills.parse` skips what it can't read); the bundled copy updates with the next app.

## The drills

The app plays only what is offered (`RoadDrills.offered`: none of its rules not introduced yet, at most one level above the
learner's) and only what has its clips: nothing new plays in the car until it is voiced. The treasure hunt's forms and
riddle stations take the level's items (GAME.md "The hunt"): its riddle station at A2–B2 now has the storyteller's riddles of
the level.

- **Transformations**, one set a rule, each set's level its page's (DrillsTest): A2 `ga-ji` (osebni-zaimki: Vidim Toneta.
  → Vidim ga.; Pokličem ju.), `naredil` (glagolski-vid: Micka peče kruh. → Micka je spekla kruh.), `rad-bi` (pogojnik);
  B1 `ki` (oziralni-ki: two sentences joined), `rekel-je` (odvisni-govor: »Pridem zvečer.« → Luka je rekel, da pride zvečer.),
  `ce-bi` (pogojnik-ce-bi), `mu-ga` (naslonke-red: Micka je dala Tonetu kruh. → Micka mu ga je dala.); B2 `kam`
  (predlogi-dva-sklona: pod stolom → pod stol), `naj` (ukazi), `treba-je` (brezosebno).
- **Sentence building**: 22 more, each naming its rules (A2: dajalnik, orodnik, rodilnik-kolicina, glagolski-vid; B1:
  oziralni-ki, pogojnik-ce-bi, odvisni-govor, naslonke-red, stevila-glagol, custva, trpnik; B2: predlogi-dva-sklona,
  odvisniki-napredno, brezosebno, ukazi, oziralni-kateri).
- **Riddles**: 20 more by Stari Janez, of pack words (A2 packs too: zdravnik, vlak, ključ, ogledalo, harmonika, tehtnica,
  daljnogled, luna; čoln, zibelka, utrinek, lekarna, žaga; sod, velikan, oglje, trnek, zmaj) and two villagers (Vida at B1,
  Micka at B2), the clues in their level's grammar (z mano, ki sedi v meni, če bi imel luknjo, kdor bi me hotel premagati).
- **Rapid fire**: three listed sets (`koliko-cesa` A2, `kdaj-tocno` B1, `kolikokrat` B2).
- The new texts have `sl`, `en` and `de`, as Slovene's drills do; `it` is left out, as in the old ones (left, below).

## Voice (no ElevenLabs call)

What a build would add, counted with `drillTexts` and the corpus's module rows against main (a text there already counted
out; Jan's voice store not read):

| What | Texts | Characters | Voice |
|---|---|---|---|
| Drills A2 (new) | 129 | 2,489 | the female narrator (`drills A2+`) |
| Drills B1 | 153 | 4,195 | the female narrator; riddles Stari Janez's |
| Drills B2 | 125 | 3,017 | the same |
| of them the riddles | 78 | 2,077 | Stari Janez (`janez`, his own voice once ready; else the grandpa speaker) |
| **All new drill texts** (a text at two levels once) | 406 | 9,693 | |
| Modules A2–B2 (`say`, options read aloud, both forms of a gender pair) | 272 | 3,802 | the narrator (`modules`) |
| **Together** | 678 | 13,495, about 6,750 credits on `eleven_v4_turbo` | |

Characters the build needs: the female narrator and Stari Janez. Jan's decision; until then the car leaves these out, and in
the app a module's 🔊 asks `POST /voice/say` as any line does.

## Words on a tap

`lexicon-build --coverage --lang sl` on the curated content: 97.2 % of tokens and 84.2 % of words known before, 97.5 % and
85.0 % after (German 89.7/73.9 → 90.1/74.4, Italian 88.5/73.6 → 88.9/74.1). `sl.extra.json` got the forms the new content
uses (27 entries extended) and 57 new entries, each meant in de and it, among them the declined names Tone, Micka, Ančka,
France and the possessives Markov, Lukov. The tool leaves the drills out, so they were counted the same way in a copy. The
wrong options (*odšal*, *uidla*, *vzami*) stay unknown on purpose.

## Tap counts (a learner of Slovene above A1, on Home)

| Journey | Before | After |
|---|---|---|
| A challenge above A1 | none: after the six A1 modules nothing came | 3: ✨, the module, "▶" (its giver's card between); one a day at most, after the last is played |
| A grammar page's 🎯 for an A2–B2 page | the learner's cards on it, or "ask the tutor" | the module's exercises once it is listed |
| The car above A2 | nothing new | the same buttons; the new items play once voiced |

## Checks

On this branch (base main f9339ab8): tsc; the smoke with ports 10510–10529; `check-practice.ts sl`; the smoke's `practice`
section with Slovene in it (modules in each base, each level A1–B2 with its modules and their order, givers in the cast, the
drills to B2 in every kind, Jan's six and the pace); the grammar smoke reading the modules per base; `tutor-export --check`;
the Android suite through lani-slot.sh; Python. The numbers are in the final section's report to the lead.

## What's left

- Jan's six A1 modules keep English-only explanations (a learner of Slovene from German or Italian reads them in English)
  and no `order` or `review`: kept as they are on purpose. Giving them `de`/`it` texts later changes nothing for Jan (his base
  is English) and loses no clip (explanations aren't voiced).
- The Slovene drills have no `it` meanings (old and new): a learner of Slovene from Italian hears English cues.
- Not made into modules: `svoj`/`cigav`, `dvojina-skloni`, `nedolocni-zaimki`, `trpnik`, `vid-casi`, `pogojnik-pretekli`,
  `uradno-pismo`.
- The voice build (above): nothing new plays in the car before it.
- `docs/grammar-syllabus.md` (2026-10) still says the six modules are all there is; its point 7 ("a module for each new page")
  is now partly done.
