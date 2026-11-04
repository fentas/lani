# Teo's talks at B1 and B2 (talks-it)

Built 2026-11-03 on branch `talks-it` (followups-16, section 1, Italian). The free talks ("🗣️ Conversazione · Gespräch":
role-plays where the tutor plays someone in a real situation) stopped at A2: nine Italian ones for a child, A1 and A2
(docs/plans/05-built/scenarios-it.md). Now the Friuli village has six more at B1 for a child and six at B2 for an older
learner, with nothing in them a child mayn't hear. A curated talk is now offered by level: at most one level above the
learner's, so an A1 learner never meets B1 or B2. Everything written here is machine-written and needs a native check.

## Native check

Every file says so in its `review` ("it, de, sl: machine-written, not reviewed by a native speaker"). What most needs a
native eye, in this order:

- **Italian, the lines Teo hears and the hints he says** (the twelve openers and 93 hints). Most of all:
  - B1, a child's register: «Ce l'hai il portiere dell'Udinese?» and «Sì, ce l'ho doppio!» (the stickers), «Facciamo
    una per una» (or «uno a uno», «una contro una»?), «Ho combinato un guaio», «Al posto tuo, le regalerei una delle mie
    matite», «Vedo che zoppichi un po'», «Mentre correvo, ho messo il piede in una buca», «Sono caduto e mi si è girata
    la caviglia» (or «ho preso una storta»?), «Miagolava forte, perché non riusciva a scendere», «Alla fine il gatto è
    sceso sano e salvo», «Siccome lo scivolo traballa, è pericoloso», «Potreste mettere anche una panchina per i nonni?»
    (a child to the mayor: voi for the council), «Me lo può cambiare, per favore?» and «Potrei riavere i soldi?» (a child
    to the shopkeeper in Lei: the role lets the child say tu or Lei, never corrected).
  - B2: «è stato soppresso» and «autobus sostitutivo» (the railway's words?), «Avrei un appuntamento a Trieste» (the
    conditional of politeness), «Vorrei che mi venisse rimborsato il biglietto», «Se l'avessi saputo prima, avrei preso
    la corriera» (corriera in Friuli for the regional bus), «Bisogna che la strada venga riaperta prima della
    vendemmia», «Se il Comune ci desse i materiali, il lavoro lo faremmo noi», «Allora contiamo su di Lei», «E se
    rinunciassimo alle decorazioni?», «A patto che sia pronto entro il quindici settembre», «e con l'inglese me la
    cavo», «Che io sappia, non sono allergico a niente», «Forse avrei dovuto bere più acqua», «Credo che tu debba
    provarci», «Qualunque cosa tu decida, ti aiuteremo».
  - The titles: «Ahi, la caviglia!», «Il gatto sull'albero», «La frana sulla strada», «Il cancello dell'orto», «Se
    fossi in te…».
- **German (Teo's base), what a child of seven reads in the B1 talks**: the settings (short, du), «Sammelbilder
  tauschen» (or «Fußballbilder», «Sticker»?), «Ich hab was angestellt», «Mama hat mir Eis draufgelegt», «Du wirst sehen,
  Giulia wird nicht böse», «Machen wir eins gegen eins», «Wenn du willst, komme ich mit». Germany's or Austria's German
  for Teo is still open (scenarios-it.md asked); these are written in a German that fits both, with no Jause or Sackerl.
- **German, the B2 talks**: «Der Zug fällt aus», «Ich hätte um zehn einen Termin in Triest», «Ich möchte, dass mir die
  Fahrkarte erstattet wird», «Wenn die Gemeinde uns das Material gäbe, würden wir die Arbeit machen», «Vorausgesetzt,
  es ist bis zum fünfzehnten September fertig».
- **Slovene (another base)**: the gender pairs ({m:kupil|f:kupila}, {m:Padel|f:Padla}, {m:Rekel|f:Rekla}), «Nisem
  {m:kriv|f:kriva}: bilo je le malo vetra», «Nekaj sem ušpičil» (Lorenzo), «Zvil se mi je gleženj», the place names
  (Videm for Udine, Krmin for Cormòns, Čedad for Cividale, Nadiža for the Natisone, Karnija for Carnia), «Mlinska
  cesta» for via del Mulino, vi in the B2 settings' «ste prišli».
- **English**: plain; «the family doctor's surgery», «Madam Mayor» (what a child would say?).
- **Facts to keep true** (from memory, not checked against a source): Cividale del Friuli is the Lombard town on the Natisone (the Tempietto Longobardo, the Ponte
  del Diavolo, UNESCO since 2011); the University of Udine has agriculture; Monte Sabotino is above Gorizia; Udinese is
  Udine's football club. The talks say no more than that: no train times, no line, no company's name.

## What

Twelve role-plays in `companion/scenarios/it/`, in Italian, with German, Slovene and English for everything the
learner reads, in the format scenarios-it left (`lani.scenario/v0`, no new field):

| Id | Level | Situation | Who | Says | Packs | Forms the hints use |
|---|---|---|---|---|---|---|
| `l-aquilone-rotto` 🪁 L'aquilone rotto | B1 child | a complaint: the kite bought yesterday broke on its first flight | Signora Paola, the toy shop in Gorizia (Papà there) | tu | in-paese, numeri-e-ore, saluti | passato prossimo, mentre + imperfetto, vorrei, potrei, me lo |
| `scambio-di-figurine` ⚽ Scambio di figurine | B1 child | a negotiation: trading football stickers, not two for one | Tommaso (the cast's), break time, Maestra Elena on duty | tu | a-scuola, numeri-e-ore, saluti | ce l'ho, te lo do se…, mi mancano |
| `l-altalena-rotta` 🛝 L'altalena rotta | B1 child | a problem told to the mayor: the swing broken for a week, the slide wobbling | Signora Cossar, the mayor of Cormòns, the children's hour (Mamma there) | tu | al-parco-giochi, in-paese, numeri-e-ore, saluti | da una settimana, siccome, potreste |
| `ahi-la-caviglia` 🩹 Ahi, la caviglia! | B1 child | a doctor's history: how the ankle was twisted, since when, what was done | Dottoressa Visintin, a paediatrician in Gorizia (Mamma there; no needles) | tu | il-corpo, numeri-e-ore, saluti | stavo + gerundio, mentre correvo, mi si è girata, da ieri |
| `un-consiglio-per-lorenzo` 🖍️ Un consiglio per Lorenzo | B1 child | giving advice: Lorenzo broke Giulia's pencil and dares not tell her | Lorenzo (the cast's), by the stream behind the mill (his nonno nearby) | tu | a-scuola, famiglia, saluti | dovresti dirglielo, perché non…, al posto tuo, vedrai |
| `il-gatto-sull-albero` 🐈 Il gatto sull'albero | B1 child | telling what happened: the cat up the linden and the firefighters' ladder | Zia Nives (the cast's), hard of hearing, at the well | tu | dai-pompieri, in-piazza, saluti | imperfetto and passato prossimo, ho detto che… era |
| `il-treno-soppresso` 🚆 Il treno soppresso | B2 | an appointment that goes wrong (and a complaint): the regional train to Trieste cancelled | Signora Tonon, the ticket office at Udine | Lei | numeri-e-ore, in-paese, saluti | avrei, è importante che io arrivi, se l'avessi saputo… avrei, vorrei che venisse rimborsato |
| `la-frana-sulla-strada` 🚧 La frana sulla strada | B2 | a problem put to the mayor: the landslide on the road to the mill before the harvest | Signora Cossar, the mayor of Cormòns | Lei | nel-mulino, nel-campo, festa-vendemmia, in-paese | bisogna che venga riaperta, benché capisca, se ci desse… faremmo |
| `il-cancello-dell-orto` ⚒️ Il cancello dell'orto | B2 | a negotiation: the school garden's gate, 900 euros asked, 700 collected, September or October | Fabbro Bruno (the cast's), in the smithy | Lei, «signor Bruno» | nella-fucina, a-scuola, numeri-e-ore, saluti | ci servirebbe, prima che cominci, e se rinunciassimo, a patto che |
| `un-colloquio-a-cividale` 💼 Un colloquio a Cividale | B2 | a job: the tourist office's summer desk | Signora Zanier, the head of the office | Lei | in-paese, numeri-e-ore, saluti | mi piacerebbe, me la cavo, se si arrabbiasse… cercherei, credo che sia |
| `dal-medico-di-base` 🩺 Dal medico di base | B2 | a doctor's history: two weeks of afternoon headaches | Dottor Perusin, the family doctor in Cormòns | Lei | il-corpo, numeri-e-ore, saluti | da un paio di settimane, che io sappia, pensa che sia, avrei dovuto |
| `se-fossi-in-te` 🐑 Se fossi in te… | B2 | giving advice: Pastore Davide's place at the University of Udine and his flock | Pastore Davide (the cast's), at the gate in the evening | tu | al-portone, nel-campo, saluti | se fossi in te… andrei, potresti, anche se, credo che tu debba, qualunque cosa tu decida |

- **A child's B1, an older learner's B2.** The B1 talks are `audience: "child"`: a grown-up close by, one or two short
  sentences a turn, asking nothing personal, nothing frightening (the ankle is "only a little sprain", the cat gets
  down safe and sound). The B2 talks leave `audience` out: they are for an older learner, Lei to strangers, but hold
  nothing a child mayn't hear (no wine, no accident, no illness that frightens), and a child's profile still adds the
  child line to every turn's brief (the bridge's `scenarioBrief`).
- **The cast plays its own.** Tommaso, Lorenzo, Zia Nives, Fabbro Bruno and Pastore Davide are the village's (their
  sprite, voice and friendship); the others stand in by their title (Signora, Dottoressa, Dottor), with names no
  villager's id can be taken from (CastTest).
- **Something to settle.** Each role says what the character holds out for and when they give way (Tommaso first asks
  two for one; Signora Tonon names the bus only when asked twice; Bruno gives way on price or date, never both; the mayor
  commits to a date only when pressed), so the learner has to argue, not only answer.

## Offered by level

`ScenarioStore.offered(child, level)` (bridge/src/scenarios.ts) keeps a curated talk when `talkLevelOffered(talk, learner)`: at
the learner's level or below, or one level above. Two above never: an A1 learner meets A1 and A2 (as before: the two A2
ones were offered to everyone), an A2 learner B1 as the next step, a B1 learner B2. A tutor's talk is always offered
(the tutor wrote it for this learner). The level is the learner profile's `current_level`, as modules read it (A1 when
unknown). The list (`GET /scenarios`), one by id (404 above it) and a scene's "keep talking" links all go through it.

Why one above and not "at or below" as the dialogs and arrivals pick: a talk is chosen from a list that shows its
level, and the tutor keeps to the learner's level while it plays (the brief's `level` and the guide's "at Jan's
level"); Teo, aiming at B1, gets the B1 talks as the next step from A2, and an A1 learner keeps the two A2 talks the list
always had. A dialog or an arrival comes to the learner unasked, so those stay at or below.

**Older apps** (Teo's tablet 0.2.698): the bridge decides, and the list's format is as it was (no new field), so an old
app gets the talks of its learner's level and reads them as it read the A2 ones.

## The dev tutor and QA

- `test/dev-bridge.ts`: `ROLEPLAY_LEVELS.it.B1` and `.B2`, canned lines that fit any of the six of their level (B1: «Ah,
  capisco. E poi che cosa è successo?», «Davvero? Hai fatto bene a dirmelo.»; B2: «Capisco perfettamente. Mi dispiace
  che sia andata così.», «Se l'avessimo saputo prima, avremmo trovato una soluzione.»), a correction at the level
  (the imperfetto beside the passato prossimo; the unreal past) explained in de, sl and en, and practise points. B1's
  say tu, as every B1 talk does; B2's neither tu nor Lei, so they fit Davide and the counter alike. A talk of another
  level gets the language's A1 lines as before.
- `bin/qa`: `village_talk` takes the dev tutor's lines at the talk's level (`VILLAGE_TALKS.friuli.levels`), and says so
  when the named talk isn't offered to the learner's level.

## How to use

- Teo at A2: Home's "Conversazione · Gespräch" tile lists the B1 talks among the A1 and A2 ones, each with its level's
  tag (the list is by file name, not by level: "Ahi, la caviglia!", B1, comes first; see What's left);
  a talk, "Inizia · Starten". At B1 the B2 ones too.
- QA of a B1 talk: a synthetic Teo at A2 (`bun test/fixtures/make-learner.ts <dir> --target it --base de --name Teo
  --level A2`), then `LANI_DEV_DATA=<dir> LANI_CULTURE=friuli LANI_CHILD=1
  LANI_QA_SETUP_PAIR=it-de LANI_QA_LEARNER=Teo LANI_QA_TALK=l-aquilone-rotto LANI_QA_TALK_SHOTS=<shots>
  companion/bin/qa-slot --steps friuli-talk`.
- Count: `companion/bin/content-count` has a "role-plays" row now (it 21: A1 7 · A2 2 · B1 6 · B2 6).

## Tap counts

| Journey | Before | After |
|---|---|---|
| Teo at A2, Home to talking in a B1 situation | not possible: no talk above A2 | 3: "🗣️ Conversazione · Gespräch", the talk, "Inizia · Starten" |
| Teo at A1, Home to talking (the market) | 3 | 3 (scenarios-it's count, not run again; the list has no B1 or B2) |

## Checks

- Bridge: `bunx tsc --noEmit -p .`; the smoke (its port block 10750, LANI_SMOKE_DRAMA3_PORT 10768): `talkLevelOffered`; the 21
  Italian files (9 A1/A2, 6 B1, 6 B2), each validating, B1 a child's and B2 without an audience, every text in it · de ·
  sl · en, "nothing personal" in each role; Friuli's child Tim at A1 served the nine A1 and A2 talks and a B2 one 404 by
  id, at A2 fifteen, at B1 all 21, a B2 one served in the pair; a B2 turn's brief with its level and the child line.
  `test/check-culture.ts friuli`, `tutor-export --check`.
- Android: `TalkTest` (the Italian files' audience by level; six at B1 and six at B2 read in it · de, the cat's and
  Davide's talks), `CastTest` (the five B1/B2 talks naming a villager are played by them; no stand-in takes a
  villager's look; the doctors' sprites).
- Python: `companion/tests/test_content_count.py` (the role-plays row).
- QA: `friuli-talk` on a synthetic Teo at A2 through `qa-slot` (slot 2), the talk `l-aquilone-rotto`: passed, 3 taps from
  Home to talking. The list with B1 tags in it · de, the intro (the setting in German, the goals in the pair, Signora
  Paola as "La negoziante di giocattoli · Die Spielzeughändlerin"), the opener, the B1 canned answers, a hint that is
  the talk's first phrase, the correction in German, the goals, the debrief, the list and the debrief on the tablet.
  One B1 talk only: no B2 talk ran on the emulator, and that a B2 talk stays off an A2 learner's list is the smoke's
  check, not QA's. The step had been stale since Home became a grid: it looked for "🗣️ Conversazione · Gespräch" as one
  text, found none, and reported "skipped" with exit code 0. It now finds the tile by its title (kaernten-talk and
  lakeland-talk use the same code; neither was run).

## What's left

- The native check above.
- No scene leads into a B1 or B2 talk yet (a dialog's `talk`): Tommaso's stickers could follow his football at school,
  Zia Nives's cat the cat up the linden on the square, Davide's advice his flock at the gate. The links would be
  dropped for a learner the talk isn't offered to (`talksOffered`), so they are safe to add.
- The packs above A2 don't exist in Italian (all A1/A2): the talks name the nearest the village has. The common-words
  and pack branches may give them better ones.
- **The list's order** was by file name, so a learner's easiest talks didn't come first: at A2 the first row was a B1
  talk. Done on talks-de: `ScenarioStore.all` sorts by level, easiest first, for every language.
- Voice: the twelve openers and 93 hints (105 lines, 4,454 characters for one gender; 109 lines and 4,691 characters
  with both forms of the 4 gendered lines) join the Italian voice corpus; Teo's node voices them ahead with its engine, which may spend
  ElevenLabs credits: ask Jan before a voice build. Nothing was spent.
- The other languages' talks: the offer by level is talks-sl's `talkLevelOffered` (this branch built the same rule in
  2e75338dc under another name; on the merge into talks-de, 73306bae1, one was kept), the dev bridge's level lines are one
  table for every language (`ROLEPLAY_LEVELS`, their language's key beside `it`), and content-count's row (97fa05fd7)
  serves them all.

## Commits

See the branch: 2e75338dc (offered by level), 16082fd3b (the twelve talks), 5a4f681ed (smoke, tests, dev tutor, QA),
97fa05fd7 (content-count), 19537f741 (README and module-spec), fd0d19af4 (this doc), c719420de (main merged), then the QA
step's tile and this doc's corrections.
