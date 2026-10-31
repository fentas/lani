# Built: who answers in a scene (scene-replier, 2026-10-31)

From places-lakeland's open point "Who answers in a scene" ([places-lakeland.md](places-lakeland.md)). In a scene's
dialog the app has the happening's person answer every turn (`DialogRun`'s partner: their voice, their face, their
friendship), while `DialogRun.facing`, whom the panel shows, is the turn's `to`, else whoever spoke last. A turn that came
after someone else's line and didn't name `to` had its `facing` on one person and its answer from another, and its
replies were mostly written in that other person's words: Jože answered Nejc's turn with "Zakaj ne? Saj nisem majhen!"
(Nejc's own words). Jan's lead chose the content fix: the app stays as it is, and each such turn names the happening's
person as its `to` and is written for them, as the Lake District's scenes already do. Everything written here is
machine-written and waits for a native check (the list for Jan's wife is below).

**What the screen shows** (found on the emulator, see [QA](#qa)): a scene's panel draws and voices every line of a
happening's dialog as the happening's person. `SceneScreen` gives `DialogPanel` other speakers only for a project step's
cast; for a happening `speakers` is empty, so `whoIs` makes anyone else the happening's person: the header's portrait and
name, every line's little face and every line's voice (`Theirs`: `who?.voice ?: voice`, the happening's person's), and
the picture's `facing` is `talk.person.id` there. So the header never showed Nejc: what Jan saw was Jože saying Nejc's
words, at the turn and on Nejc's own lines, which still come with Jože's face and voice ("Ana! Jože! Gremo v gozd!", Nejc's
line, under Jože's portrait). The content fix puts the right words in the answering mouth and names every line's speaker
right; showing each speaker as themselves is an app change (What's left).

## What

### The turns

26 turns, in 18 dialogs of 11 scenes. places-lakeland counted 22 (15 Slovene, 7 Friuli); the guard found 4 more in
Kärnten's places (`in-der-schmiede`, `in-der-werkstatt`), fixed here too. "To only": the replies read right from the
happening's person, so the turn only names them. "Rewritten": replies, reactions or puzzled lines that were the other
speaker's are written for the happening's person. "Reworked": the lines round the turn changed.

| Scene | Dialog | Turn (line) | After whose line | Answers now (`to`) | Change |
|---|---|---|---|---|---|
| `na-cebeljem-travniku` | roj | 3 | Čebelar Anton | Nejc | rewritten: the 3 replies |
| `na-cebeljem-travniku` | roj-dim | 2 | Čebelar Anton | Nejc | rewritten: the 3 replies, puzzled |
| `na-cebeljem-travniku` | koncnice-nejc | 1 | Nejc | Zala | rewritten: the 3 replies, puzzled |
| `na-mostu` | voda-tone | 1 | Kovač Tone | Mlinar France | reworked: the first line is France's; a reaction and its why |
| `na-mostu` | voda-tone | 3 | Kovač Tone | Mlinar France | rewritten: the 3 replies |
| `pod-mlajem` | straza-vrh | 2 | Vinar Marko | Pastir Luka | rewritten: 2 reactions, puzzled |
| `pod-mlajem` | straza-vrh | 5 | Vinar Marko | Pastir Luka | to only |
| `pod-mlajem` | ples-postavljanje | 2 | Pastir Luka | Vinar Marko | rewritten: 1 reaction |
| `pod-uro` | zamuda-ancka | 3 | Teta Ančka | Učiteljica Mojca | rewritten: the 3 replies, puzzled |
| `pri-vodnjaku` | vecer | 2 | Mlinar France | Kovač Tone | to only |
| `pri-vodnjaku` | vaja | 2 | Mlinar France | Kovač Tone | to only |
| `pri-vodnjaku` | lekcija-dvojina | 3 | Zala | Učiteljica Mojca | rewritten: 2 replies, puzzled |
| `pri-vodnjaku` | vecer-zvon | 1 | Mlinar France | Kovač Tone | rewritten: 1 reaction |
| `pri-vodnjaku` | vaja-vedra | 3 | Mlinar France | Kovač Tone | to only |
| `pri-vratih` | zapah-nejc | 1 | Nejc | Lovec Jože | reworked: Nejc's reply a line of his; 2 reactions, puzzled |
| `in-bottega` (Friuli) | sega-segatura | 4 | Lorenzo | Mugnaio Franco | rewritten: 2 replies |
| `in-bottega` | cavallino | 3 | Mugnaio Franco | Lorenzo | rewritten: 2 replies |
| `in-bottega` | cavallino-gamba | 3 | Mugnaio Franco | Lorenzo | rewritten: 2 replies |
| `in-cantina` | brocca | 2 | Vignaiolo Matteo | Ostessa Marisa | rewritten: the right one's reply |
| `in-cantina` | brocca | 4 | Vignaiolo Matteo | Ostessa Marisa | to only (a reaction's Slovene: "lačna", she is a woman) |
| `in-cantina` | brocca-bottiglie | 3 | Vignaiolo Matteo | Ostessa Marisa | to only |
| `in-soffitta` | palla | 3 | Ostessa Marisa | Tommaso | rewritten: 2 replies |
| `in-der-schmiede` (Kärnten) | rad | 4 | Schmied Toni | Mostbauer Stefan | rewritten: 2 replies, puzzled |
| `in-der-werkstatt` | pferd | 4 | Müller Franz | Jakob | rewritten: 2 replies, puzzled |
| `in-der-werkstatt` | saege-kehren | 2 | Jakob | Müller Franz | rewritten: 2 replies, puzzled |
| `in-der-werkstatt` | saege-kehren | 4 | Jakob | Müller Franz | rewritten: 2 replies; Jakob's last line speaks to him |

Beside them, `pri-vratih`'s zapah-nejc had a second turn the guard can't see: it came after Jože's own line, so the header
and the voice agreed (Jože), but its replies were Nejc's ("Prav … Ampak jutri zgodaj!", "Jože je prav? Kaj je prav? Ha
ha!" in Jože's mouth). It names Jože now too and is reworked with the first. A scan of every scene for a reply that names
the one who says it ("Kdo, Tone?" in Tone's mouth) found no other in someone else's words: the rest name themselves on
purpose ("Tiri? Sono Tommaso, mica il sindaco!").

The learner's own lines and choices are as they were: still addressed to whom they were ("Meni je všeč, Nejc!", "Che
bravo, signor Franco!", "Ja, Herr Toni. Ich halte es gerade."), with the happening's person answering beside them, as the
Lake District does ("No, Alfie. It's too sharp for you." answered by Mr Tyson). The grammar points, the choices, their
whys (but one) and the levels are unchanged, so the adaptive turns (typed, said) and the hints work as before.

### Before and after

The Slovene ones; the translations follow the Slovene.

**`na-cebeljem-travniku`, roj, turn 3** (Anton: "Mirno, mirno. … Kam letijo?"; the happening is Nejc's swarm). Before: the
header showed Anton, Nejc answered in Anton's words. After, Nejc:
- "Na lipo!": "Tako je. Na lipi se bodo ustavile." → "Ja! Glej, vse so na lipi!"
- "V čebelnjak!": "V čebelnjak? Ne, ta roj išče nov dom." → "V čebelnjak? Ne, saj iščejo nov dom!"
- "Na rože!": "Na rože? Ne, roj leti visoko." → "Na rože? Ne, ne! Višje, višje!"

**roj-dim, turn 2** (Anton: "{learner}, podajte mi kadilnik, prosim."). After, Nejc beside Anton:
- "Izvolite, kadilnik.": "Hvala. Malo dima … in čebele so mirne." → "Glej, dim! Čebele so že mirne." (the smoke as before)
- "Izvoli, kadilnik.": "Izvoli? … Mlad svet, mlad svet." → "Izvoli? Ha ha, Anton ni otrok!"
- "Izvolite, kadilnika.": "Kadilnika? Dva? Eden je dovolj." → "Kadilnika? Dva? Na štoru je samo eden!"
- puzzled: "Hm? … Počasi, prosim." → Nejc's "Kaj? Govori hitreje, {learner}!" (as at his other turn)

**koncnice-nejc, turn 1** (Nejc: "Moj hudič je najlepši na čebelnjaku!"; the happening is Zala's panels). After, Zala,
before her "Najlepši? Ima tri noge!":
- "Meni je všeč, Nejc!": "Vidiš? Ti imaš dober okus!" → "Res? Ti si pa {m:prijazen|f:prijazna}, {learner}!"
- "Mene je všeč, Nejc!": "Mene? Kaj mene? Ha ha, ne razumem!" → "Mene? Kaj mene? Hi hi, ne razumem!"
- "Jaz je všeč, Nejc!": "Jaz? Ti si všeč? Komu?" → "Jaz? Ti si všeč? Komu pa, hi hi?"
- puzzled: Nejc's "Kaj? Govori hitreje, {learner}!" → Zala's "Kaj? Hi hi, ne razumem!"

**`na-mostu`, voda-tone** (France's morning of high water; before, every line was Tone's and France, silent, answered).
Reworked: the first line is France's, "Jutro, {learner}. Voda je visoka. Tone pregleduje stebra." (was Tone's "Jutro.
Voda je visoka. Pregledam stebra."), so the learner offers France their help.
- turn 1, "Ti lahko pomagam?": why "Tone says ti …" → "France says ti to you but expects vi"; "Ti? Hm. Spet ta ti …" →
  "Ti? Tikaš me? No, prav …"
- turn 3 (Tone: "No? Je kaj narobe s kamni?"), France answers: "Dobro. Ali je prav, ali ni: je prav." → "Dobro! Ko voda
  upade, peljem voz čez."; "Nobena razpoka? Hm. Ali je prav, ali ni." → "Nobena razpoka? Hm. Tone, kaj pravi
  {learner}?"; "Je? Hm. Povej jasno, {learner}." → "Je? Kje? Tone, poglej še enkrat!"

**`pod-mlajem`, straza-vrh, turn 2** (Marko: "Malo rebule? Da ne zaspiš."; Luka's watch). After, Luka:
- "… samo malo vino.": "Malo vino? Moje vino ni malo, je veliko! Ha ha." → "Malo vino? Ha ha! Marko, slišiš? Tvoje vino je malo!"
- "… samo malo mleka.": "Mleka? Pri meni je samo rebula, {learner}!" → "Mleka? Ha ha, mleko imajo moje ovce!"
- puzzled: Marko's "Allora? Kako, prosim?" → Luka's "Kaj? Ej, še enkrat!"
- turn 5 (Marko: "Na zdravje!"): to Luka only; his replies fit ("Za mlaj! In da ga nihče ne ukrade.").

**ples-postavljanje, turn 2** (Luka: "Vsi na vrvi! … Zdaj!"; Marko's polka). After, Marko: "Vleče! Hej, hop!": "Kdo vleče?
Marko? Marko pije! Ha ha." → "Kdo vleče? Jaz? Ha ha, jaz samo gledam!"

**`pod-uro`, zamuda-ancka, turn 3** (Ančka: "Kaj? Že ob osmih?"; Mojca's choir). After, Mojca, who says vi to the learner:
- "Dober večer! Pridite, vaje se začnejo.": "Hvala, {learner}. Tako {m:prijazen|f:prijazna} si!" → "Hvala, {learner}. Teta
  Ančka, tukaj je vaše mesto!"
- "… Pridi, …": "Pridi? Glasneje! … A tako, tikaš me." → "Pridi? Oho, teto Ančko tikate?"
- "… vaje se začne.": "Začne? Kaj se začne? Glasneje!" → "Začne? Hm, kaj se začne, {learner}?"
- puzzled: Ančka's "Kaj? Glasneje, prosim!" → Mojca's "Hm? Še enkrat, prosim, počasi."

**`pri-vodnjaku`**: vecer turn 2, vaja turn 2 and vaja-vedra turn 3 (after France) name Tone only: his replies fit.
vecer-zvon turn 1 (France: "Pridi, pij."), Tone, before his "Štiri cevi sem skoval": "Hvala. Res je dober in mrzla.":
"Dober? Kdo, Tone? Ha ha, včasih." → "Dober? Kdo, jaz? Ha, včasih." lekcija-dvojina turn 3 (Zala: "… nosim dve vedri."),
Mojca:
- "Obe vedri sta polni.": "Bravo! In nič vode na tleh." → "Odlično! In nič vode na tleh, Zala?" (Zala: "No, malo vode je pa le na tleh. Ups!")
- "Obe vedri je polno.": "Je? Samo eno? Hi hi, eno sem spila!" → "Je? Samo eno vedro? Zala, kje je drugo?"
- puzzled: Zala's "Kaj? Hi hi, ne razumem!" → Mojca's "Hm? Še enkrat, prosim, počasi."

**`pri-vratih`, zapah-nejc** (Jože bars the gate; Nejc wants to go into the forest). Reworked: Jože answers both turns,
and Nejc's own words, which were the right choices' replies in Jože's mouth, are lines of Nejc's after them: "Zakaj ne?
Saj nisem majhen!" (before Jože's "Nejc. Poslušaj. Ponoči so v gozdu volkovi.") and "Prav … Ampak jutri zgodaj!" (before
Jože's "Pametno. …"). 7 lines (was 5).
- turn 1, "… ne gremo v gozdu.": "V gozdu? Saj sem še tu, ha ha!" → "V gozdu? Nismo v gozdu, smo pri vratih."
- turn 1, "Ja, Nejc! Gremo takoj!": "Juhu! … Ampak Jože gleda grdo." → "Takoj? Hm. Vrata so že zaprta."
- turn 4, "Jože je prav. …": "Jože je prav? Kaj je prav? Ha ha!" → "Jože je prav? Hm. Jože je tu, pri vratih."
- puzzled, both: Nejc's "Kaj? Ne slišim, ha ha!" → Jože's "Pst … kako, prosim?"

Friuli (Italian, Teo's village) and Kärnten (German):

- **`in-bottega`, sega-segatura, turn 4** (Lorenzo: "Dove la mettiamo?"), Franco: "Sì! Il nonno dice che piace
  all'insalata." → "Sì, bravi! La segatura fa bene all'orto."; "Lo? Il nonno? No, il nonno resta qui!" → "Lo? Chi, me? No,
  il nonno resta qui! Ah ah!"
- **cavallino, turn 3** (Franco: "Un po' di colla …"), Lorenzo: "Ah ah, i nonni sanno fare tutto!" → "Sì! Il nonno sa fare
  tutto!"; "Brava? Io? Ah ah, sono un nonno, sai!" → "Brava? Ah ah, il nonno non è una signora!"
- **cavallino-gamba, turn 3** (Franco: "Passami la colla"), Lorenzo: "Grazie. Ecco: quattro gambe!" → "Evviva! Adesso ha di
  nuovo quattro gambe!"; "Hm? Cosa mi passi? Ripeti." → "Il colla? Ah ah, parli strano!"
- **`in-cantina`, brocca, turn 2** (Matteo: "Metti la brocca sotto il rubinetto."), Marisa: "Bene. Adesso apro il
  rubinetto." → "{m:Bravo|f:Brava}! Matteo, apri il rubinetto!"; turn 4 and brocca-bottiglie turn 3: to Marisa only.
- **`in-soffitta`, palla, turn 3** (Marisa: "Niente palla in soffitta."), Tommaso: "{m:Bravo|f:Brava}. E portate anche il
  pallone!" → "Sì! In cortile! Chi arriva primo?"; "Come mi hai chiamata? Ah ah!" → "Scusa, Marisa? Ah ah, sei
  {m:coraggioso|f:coraggiosa}!"
- **`in-der-schmiede`, rad, turn 4** (Toni: "Halt das Rad. Gerade."), Stefan: "Gut. So." → "Super! Gleich ist es wieder
  dran."; "Ihn? Das Rad ist kein Mann. Genau sein." → "Ihn? Hoho, das Rad ist doch kein Mann!"; puzzled → Stefan's "Hm? Wie bitte?"
- **`in-der-werkstatt`, pferd, turn 4** (Franz: "Wie lange, glaubst du?"), Jakob: "Eine Stunde. Genau. Und dann steht er
  wieder." → "Eine Stunde! Und dann kann er wieder laufen!"; "Ein Stunde? Hm. Fast. Genau sein, bitte." → "Ein Stunde?
  Hihi, das klingt komisch!"; puzzled → Jakob's "Hä? Was hast du gesagt?"
- **saege-kehren, turns 2 and 4** (Jakob: "Aber wohin damit?", "Zählst du mit?"), Franz: "Ja! Opa sagt, das ist gut für
  die Erde." → "Genau. Sägemehl ist gut für die Erde."; "… Da ist es ja noch nicht. Hihi." → "In dem Garten? Da ist es ja
  noch nicht."; "Sieben, acht, neun, zehn! Super!" → "Sieben, acht … Ihr zählt ja schneller, als ich säge!"; "Hihi, du
  zählst wie ein Hase, der hüpft!" → "Hoppla! Du zählst ja wie ein Hase, der hüpft."; Jakob's last line "Fertig! Alles
  sauber. Opa wird sich freuen." → "Fertig! Alles sauber, Opa!" (Franz is in the talk now).

### The guard

`WhoAnswersTest` (companion/android, next to `DialogReactionsTest`) plays every scene of Jan's village and of the culture
packs (companion/scenes, companion/cultures/*/scenes: Primorska, Friuli, Kärnten, the Lake District; every dialog and
variant, a counting one at its least number) as the app does (`DialogRun.start` with the happening's person as partner),
on every way through its right choices (a right choice with a reply and one without leave someone else speaking last
before the next turn). At each turn the panel's person (`DialogRun.facing`) must be the one who answers (the turn's `to`,
else the partner). A failure says, for instance:

```
pri-vratih (scenes/pri-vratih.json), dialog zapah-nejc, turn 1: the panel shows nejc, joze answers: name joze as its to
```

A second test shows it catches such a turn and that a `to`, or the happening's person's reply before the next turn,
settles it. On main's scenes (c14c31cd) it fails with the 26 turns above, and no other. SCENES.md "Format" says so.

### Reviews

Each changed scene's `review` ends "scene-replier.md: to check" (this list). The bridge reads a review as at most 200
characters and skips a scene whose file doesn't validate, so where there was no room, "not reviewed by a native speaker"
became "unreviewed" and "(Claude)" went; each still says its reactions are machine-written (`DialogReactionsTest`).

## How to use

Nothing to do in the app: the scenes come from the bridge as they are merged (content only, the app is the same). The
person the panel shows at such a turn now answers in their own words.

QA: `companion/bin/qa-slot --steps scene-replier` (only when named) opens the village culture's dialog through QA's hook:
Primorska's `pri-vratih`/zapah-nejc, or with `LANI_CULTURE=friuli` (an Italian learner, `LANI_QA_PAIR=sl-en`) Friuli's
`in-cantina`/brocca. At its first turn it reads the header's name (the one level with its ✕), picks a wrong choice (the
reaction's text, its face in the Faces log, the header then) and the right one; screenshots of each, also as
replier-*.png in `$LANI_QA_REPLIER_SHOTS`. The shots come before the checks; on the content before the fix the run shows
the happening's person saying the other speaker's words.

## QA

On the emulator (`qa-slot --steps scene-replier`, synthetic learners from make-learner.ts: a Slovene one, and an Italian
one with `LANI_CULTURE=friuli`, the phone in sl · en), after the fix and, for comparison, on main's scenes (c14c31cd's
files in the worktree for the run). Screenshots in the scratchpad's `ux-scene-replier/after/` and `before/`
(replier-primorska-*, replier-friuli-*: the turn, the reaction, the right one).

| Turn | Header at the turn | Wrong pick: who reacts, with what | Right pick |
|---|---|---|---|
| zapah-nejc 1, before | Lovec Jože | Jože (laugh): "V gozdu? Saj sem še tu, ha ha!" (Nejc's words) | Jože says "Zakaj ne? Saj nisem majhen!" (Nejc's) |
| zapah-nejc 1, after | Lovec Jože | Jože (curious): "V gozdu? Nismo v gozdu, smo pri vratih." | Nejc's line "Zakaj ne? Saj nisem majhen!", drawn and voiced as Jože's (the app) |
| brocca 2, before | Ostessa Marisa | Marisa (laugh): "Il brocca? Hm, e chi è questo signore?" | Marisa says "Bene. Adesso apro il rubinetto." (Matteo's: he opens the tap) |
| brocca 2, after | Ostessa Marisa | Marisa (laugh): the same | Marisa: "Bravo! Matteo, apri il rubinetto!" |

The step's checks passed after the fix (the header's name at the turn and at the reaction is the happening's person's,
the reaction's face in the Faces log is the reaction's). They pass on main's scenes too: the panel there never showed
anyone but the happening's person (What the screen shows); what was wrong was the words.

## Tap counts

Unchanged: no screen changed. A dialog takes the taps it took (zapah-nejc: a right pick, then "Naprej" after Nejc's line,
as after his reply before).

## For Jan's wife: the Slovene to check

Machine-written, not checked by a native speaker. Settled against Fran: "hopla" (SSKJ², SP: hòpla, an interjection,
https://fran.si/iskanje?View=1&Query=hopla). The rest are common words; how natural they sound is hers to say. In the
Slovene village's scenes:

| Scene | Dialog | Who says it | Slovene |
|---|---|---|---|
| na-cebeljem-travniku | roj | Nejc | V čebelnjak? Ne, saj iščejo nov dom! |
| | | | Ja! Glej, vse so na lipi! |
| | | | Na rože? Ne, ne! Višje, višje! |
| | roj-dim | Nejc | Glej, dim! Čebele so že mirne. |
| | | | Izvoli? Ha ha, Anton ni otrok! |
| | | | Kadilnika? Dva? Na štoru je samo eden! |
| | koncnice-nejc | Zala | Res? Ti si pa {m:prijazen\|f:prijazna}, {learner}! |
| | | | Mene? Kaj mene? Hi hi, ne razumem! |
| | | | Jaz? Ti si všeč? Komu pa, hi hi? |
| na-mostu | voda-tone | France | Jutro, {learner}. Voda je visoka. Tone pregleduje stebra. |
| | | | Ti? Tikaš me? No, prav … |
| | | | Dobro! Ko voda upade, peljem voz čez. |
| | | | Nobena razpoka? Hm. Tone, kaj pravi {learner}? |
| | | | Je? Kje? Tone, poglej še enkrat! |
| pod-mlajem | straza-vrh | Luka | Malo vino? Ha ha! Marko, slišiš? Tvoje vino je malo! |
| | | | Mleka? Ha ha, mleko imajo moje ovce! |
| | ples-postavljanje | Marko | Kdo vleče? Jaz? Ha ha, jaz samo gledam! |
| pod-uro | zamuda-ancka | Mojca | Hvala, {learner}. Teta Ančka, tukaj je vaše mesto! |
| | | | Pridi? Oho, teto Ančko tikate? |
| | | | Začne? Hm, kaj se začne, {learner}? |
| pri-vodnjaku | lekcija-dvojina | Mojca | Odlično! In nič vode na tleh, Zala? |
| | | | Je? Samo eno vedro? Zala, kje je drugo? |
| | vecer-zvon | Tone | Dober? Kdo, jaz? Ha, včasih. |
| pri-vratih | zapah-nejc | Jože | V gozdu? Nismo v gozdu, smo pri vratih. |
| | | | Takoj? Hm. Vrata so že zaprta. |
| | | | Jože je prav? Hm. Jože je tu, pri vratih. |

Lines that only moved to someone else (a puzzled line another turn of the dialog has, "Kaj? Ej, še enkrat!", "Pst …
kako, prosim?"; Nejc's "Zakaj ne? Saj nisem majhen!" and "Prav … Ampak jutri zgodaj!", now his lines) were there before.

The Slovene translations in the Friuli and Kärnten scenes (what a learner of Italian or German from Slovene reads):

| Scene | Dialog | Slovene |
|---|---|---|
| in-bottega | sega-segatura | Ja, pridna sta! Žagovina je dobra za vrt. · Ga? Koga, mene? Ne, dedek ostane tukaj! Ha ha! |
| | cavallino | Ja! Dedek zna vse! · Spretna? Ha ha, dedek ni gospa! |
| | cavallino-gamba | Juhu! Zdaj ima spet štiri noge! · Il colla? Ha ha, smešno govoriš! |
| in-cantina | brocca | Bravo! Matteo, odpri pipo! · Pieno? Kdo je sit? Jaz ne, lačna sem! |
| in-soffitta | palla | Ja! Na dvorišče! Kdo bo prvi? · Oprosti, Marisa? Ha ha, {m:pogumen\|f:pogumna} si! |
| in-der-schmiede | rad | Super! Takoj bo spet na vozu. · Njega? Hoho, kolo vendar ni moški! |
| in-der-werkstatt | pferd | Eno uro! Potem lahko spet teče! · Ein Stunde? Hihi, to se sliši smešno! · A? Kaj si {m:rekel\|f:rekla}? |
| | saege-kehren | Točno. Žagovina je dobra za zemljo. · In dem Garten? Saj še ni tam. · Prosim? Počasi, še enkrat. · Sedem, osem … Hitreje štejeta, kot jaz žagam! · Hopla! Šteješ kot zajec, ki skače. · Gotovo! Vse je čisto, dedek! |

The Italian and German above (Before and after) are machine-written as well, for a native check of their own.

## Voice clips

Not made: voice-build spends ElevenLabs credits. voice-build voices a turn's replies in the voice of the turn's `to`, else
of whoever spoke last. Before, it voiced these turns' replies in the other speaker's voice while the app asks for them in
the happening's person's (a clip missed: made live or the fallback); now both are the happening's person's, so every reply
of these turns wants a clip in their voice, the texts kept as well as the new ones. The lines that changed speaker
(France's first line, Nejc's two, Jakob's last) want theirs too; the app asks for another's line in the happening's
person's voice as long as the panel has no other speakers (above). 68 clips (puzzled lines are voiced when said, not by
voice-build):

| Scene | Dialog | Voice | Kind | Text |
|---|---|---|---|---|
| `in-bottega` | cavallino | Lorenzo | reply | Sì! Il nonno sa fare tutto! |
| `in-bottega` | cavallino | Lorenzo | reaction | Brava? Ah ah, il nonno non è una signora! |
| `in-bottega` | sega-segatura | Mugnaio Franco | reply | Sì, bravi! La segatura fa bene all'orto. |
| `in-bottega` | sega-segatura | Mugnaio Franco | reaction | Lo? Chi, me? No, il nonno resta qui! Ah ah! |
| `in-bottega` | cavallino-gamba | Lorenzo | reply | Evviva! Adesso ha di nuovo quattro gambe! |
| `in-bottega` | cavallino-gamba | Lorenzo | reaction | Il colla? Ah ah, parli strano! |
| `in-cantina` | brocca | Ostessa Marisa | reply | {m:Bravo\|f:Brava}! Matteo, apri il rubinetto! |
| `in-cantina` | brocca | Ostessa Marisa | reaction | Il brocca? Hm, e chi è questo signore? |
| `in-cantina` | brocca | Ostessa Marisa | reply | Giusto, appena in tempo! |
| `in-cantina` | brocca | Ostessa Marisa | reaction | Pieno? Chi è pieno? Io no, ho fame! |
| `in-cantina` | brocca-bottiglie | Ostessa Marisa | reply | {m:Bravo\|f:Brava}, ma piano: il vetro si rompe! |
| `in-cantina` | brocca-bottiglie | Ostessa Marisa | reaction | Io? Allora tu cosa fai? Ah ah! |
| `in-soffitta` | palla | Tommaso | reply | Sì! In cortile! Chi arriva primo? |
| `in-soffitta` | palla | Tommaso | reaction | Scusa, Marisa? Ah ah, sei {m:coraggioso\|f:coraggiosa}! |
| `in-der-schmiede` | rad | Mostbauer Stefan | reply | Super! Gleich ist es wieder dran. |
| `in-der-schmiede` | rad | Mostbauer Stefan | reaction | Ihn? Hoho, das Rad ist doch kein Mann! |
| `in-der-werkstatt` | pferd | Jakob | reply | Eine Stunde! Und dann kann er wieder laufen! |
| `in-der-werkstatt` | pferd | Jakob | reaction | Ein Stunde? Hihi, das klingt komisch! |
| `in-der-werkstatt` | saege-kehren | Müller Franz | reply | Genau. Sägemehl ist gut für die Erde. |
| `in-der-werkstatt` | saege-kehren | Müller Franz | reaction | In dem Garten? Da ist es ja noch nicht. |
| `in-der-werkstatt` | saege-kehren | Müller Franz | reply | Sieben, acht … Ihr zählt ja schneller, als ich säge! |
| `in-der-werkstatt` | saege-kehren | Müller Franz | reaction | Hoppla! Du zählst ja wie ein Hase, der hüpft. |
| `in-der-werkstatt` | saege-kehren | Jakob | line | Fertig! Alles sauber, Opa! |
| `na-cebeljem-travniku` | roj | Nejc | reaction | V čebelnjak? Ne, saj iščejo nov dom! |
| `na-cebeljem-travniku` | roj | Nejc | reply | Ja! Glej, vse so na lipi! |
| `na-cebeljem-travniku` | roj | Nejc | reaction | Na rože? Ne, ne! Višje, višje! |
| `na-cebeljem-travniku` | roj-dim | Nejc | reply | Glej, dim! Čebele so že mirne. |
| `na-cebeljem-travniku` | roj-dim | Nejc | reaction | Izvoli? Ha ha, Anton ni otrok! |
| `na-cebeljem-travniku` | roj-dim | Nejc | reaction | Kadilnika? Dva? Na štoru je samo eden! |
| `na-cebeljem-travniku` | koncnice-nejc | Zala | reply | Res? Ti si pa {m:prijazen\|f:prijazna}, {learner}! |
| `na-cebeljem-travniku` | koncnice-nejc | Zala | reaction | Mene? Kaj mene? Hi hi, ne razumem! |
| `na-cebeljem-travniku` | koncnice-nejc | Zala | reaction | Jaz? Ti si všeč? Komu pa, hi hi? |
| `na-mostu` | voda-tone | Mlinar France | line | Jutro, {learner}. Voda je visoka. Tone pregleduje stebra. |
| `na-mostu` | voda-tone | Mlinar France | reply | Lahko. Glej v vodo in povej, če vidiš razpoko. |
| `na-mostu` | voda-tone | Mlinar France | reaction | Vas? Hm. Ne razumem. |
| `na-mostu` | voda-tone | Mlinar France | reaction | Ti? Tikaš me? No, prav … |
| `na-mostu` | voda-tone | Mlinar France | reply | Dobro! Ko voda upade, peljem voz čez. |
| `na-mostu` | voda-tone | Mlinar France | reaction | Nobena razpoka? Hm. Tone, kaj pravi {learner}? |
| `na-mostu` | voda-tone | Mlinar France | reaction | Je? Kje? Tone, poglej še enkrat! |
| `pod-mlajem` | straza-vrh | Pastir Luka | reply | Bravo! Samo malo, ker stražiš. |
| `pod-mlajem` | straza-vrh | Pastir Luka | reaction | Malo vino? Ha ha! Marko, slišiš? Tvoje vino je malo! |
| `pod-mlajem` | straza-vrh | Pastir Luka | reaction | Mleka? Ha ha, mleko imajo moje ovce! |
| `pod-mlajem` | straza-vrh | Pastir Luka | reply | Za mlaj! In da ga nihče ne ukrade. |
| `pod-mlajem` | straza-vrh | Pastir Luka | reply | Živijo! {m:Pravi stražar\|f:Prava stražarka} si, {learner}. |
| `pod-mlajem` | straza-vrh | Pastir Luka | reaction | Dober tek? Saj ne jemo, {learner}! Ha ha. |
| `pod-mlajem` | ples-postavljanje | Vinar Marko | reply | Tako! Gre gor! Še malo! |
| `pod-mlajem` | ples-postavljanje | Vinar Marko | reaction | Kdo vleče? Jaz? Ha ha, jaz samo gledam! |
| `pod-mlajem` | ples-postavljanje | Vinar Marko | reaction | Vleči? Ej, ne govori, roke na vrv! |
| `pod-uro` | zamuda-ancka | Učiteljica Mojca | reply | Hvala, {learner}. Teta Ančka, tukaj je vaše mesto! |
| `pod-uro` | zamuda-ancka | Učiteljica Mojca | reaction | Pridi? Oho, teto Ančko tikate? |
| `pod-uro` | zamuda-ancka | Učiteljica Mojca | reaction | Začne? Hm, kaj se začne, {learner}? |
| `pri-vodnjaku` | vecer | Kovač Tone | reply | Novega? Sosedova krava je dobila tele! |
| `pri-vodnjaku` | vecer | Kovač Tone | reaction | V vas? Kam pa greš? Saj si že tu. |
| `pri-vodnjaku` | vecer | Kovač Tone | reaction | So? Hm. To pa ni prav. |
| `pri-vodnjaku` | vaja | Kovač Tone | reply | Nosi vodo. Vedro za vedrom. |
| `pri-vodnjaku` | vaja | Kovač Tone | reaction | Kje? Tu, pri vodnjaku. Ampak delo? |
| `pri-vodnjaku` | vaja | Kovač Tone | reaction | Jaz? Jaz vem, kaj delam. Ti? |
| `pri-vodnjaku` | lekcija-dvojina | Učiteljica Mojca | reply | Odlično! In nič vode na tleh, Zala? |
| `pri-vodnjaku` | lekcija-dvojina | Učiteljica Mojca | reaction | So? Kje so še druga vedra? |
| `pri-vodnjaku` | lekcija-dvojina | Učiteljica Mojca | reaction | Je? Samo eno vedro? Zala, kje je drugo? |
| `pri-vodnjaku` | vecer-zvon | Kovač Tone | reply | Iz hriba pride. Po moji cevi. |
| `pri-vodnjaku` | vecer-zvon | Kovač Tone | reaction | Dober? Kdo, jaz? Ha, včasih. |
| `pri-vodnjaku` | vaja-vedra | Kovač Tone | reaction | Hm? Štej še enkrat, počasi. |
| `pri-vodnjaku` | vaja-vedra | Kovač Tone | reaction | Toliko? Ne, ne, še enkrat! |
| `pri-vratih` | zapah-nejc | Lovec Jože | reaction | V gozdu? Nismo v gozdu, smo pri vratih. |
| `pri-vratih` | zapah-nejc | Lovec Jože | reaction | Takoj? Hm. Vrata so že zaprta. |
| `pri-vratih` | zapah-nejc | Lovec Jože | reaction | Jože je prav? Hm. Jože je tu, pri vratih. |
| `pri-vratih` | zapah-nejc | Nejc | line | Prav … Ampak jutri zgodaj! |

## What's left

- **Each speaker as themselves (the app).** In a scene's happening the panel draws and voices everyone as the happening's
  person (above): Nejc's lines under Jože's portrait and in his voice, Anton's in the swarm as Nejc's, Tone's at the bridge
  as France's, in every scene where two people speak. Giving `DialogPanel` the scene's people who speak in the dialog as
  `speakers` (as a project step's cast is), and letting the picture's `facing` follow `run.facing` there too, would show
  each line's own face and say it in their voice; the header would then follow `to`, which every scene turn now names
  where it matters. An app change with its release: the lead's call, the content is ready for it.
- **The tutor's variants.** `publish_dialog_variant` and `publish_scene` don't check this: a variant the tutor writes for a
  happening of several people can bring it back. A check at publishing (not when reading, so a variant Jan has kept is
  never dropped) would refuse a turn after someone else's line without `to`; it needs a fixture scene of two people in the
  smoke's variants section. The lead's call.
- **The native check** of the lines above (Slovene, Italian, German), and the 68 clips.
- **The second turn's kind.** The guard sees a turn whose `facing` and answer disagree, not replies written in someone
  else's words after the partner's own line (zapah-nejc's second turn, found by reading); the scan for a reply naming its
  own speaker found no other.

## Checks

- Android unit tests, the full suite (`lani-slot.sh`, `:app:testDebugUnitTest`): 3567 tests in 455 classes, 0 failed, 2
  skipped; among them `WhoAnswersTest` (2), `DialogReactionsTest`, `SceneCoherenceTest` (it caught Zala's "Poglej ga
  dobro", a look at nothing of the scene, rewritten), `ScenesContentTest`, `ISpyRoundsTest`. `WhoAnswersTest` on main's
  scene files: fails with the 26 turns, no other.
- Bridge: `bunx tsc --noEmit -p .` clean; the smoke (`LANI_SMOKE_PORT=9410`, `LANI_SMOKE_DRAMA3_PORT=9428`): all checks
  passed (its first run caught the reviews over 200 characters, which would have had the bridge skip the 11 scenes);
  `tutor-export --check`: 88 files match (53 tools, 32 guide topics).
- Python: `tests` 251 OK, `companion/tests` 135 OK (11 skipped).
- Emulator: `qa-slot --steps scene-replier`, Primorska and Friuli, after and before (QA above).
