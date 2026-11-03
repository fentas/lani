# Dialogs by level: the village speaks at the learner's level

Wave E, batch 1, `dialog-levels` (followups-14.md §1, 2026-11-03). Before this, none of the 1,554 scene dialogs and
variants had a level: a B2 learner heard what an A1 learner heard, and only the turn's mode changed (chosen, typed,
said). Arrivals, project steps, parties and stories already had levels (`Arrivals.levelFor`: the highest at or below the
learner's). Now a scene's dialogs and happenings can carry one too.

## Native check

Everything in the model variants is machine-written (Claude) and not reviewed by a native speaker: 16 dialogs, every
line in four languages. The system has no learner-facing text of its own. For a native speaker, first:

- **Slovene** (na-trznici; Jan's wife): the pluperfect "je bila Micka že prodala ves sir", "Ko bi bila vsaj pol ure prej
  vstala!", "Kdo bi si mislil, da bo sira tako hitro zmanjkalo?", "Spala sem kot polh", "Kljub temu da je dražji, ga
  kupite.", "Pa naj ga plačam dvakrat, …", "godrnjati nad joto", "Rekel je, naj mu prinesete še en kozarec terana.",
  "skoraj sem vrgel puško v koruzo", "Potem ne bi nikoli pokusil / ne bi bil nikoli pokusil tvoje nove rebule" (both
  right), "Ta letnik je preživel točo". Every wrong choice must be a learner's mistake that nobody grown up says: "Rekel
  je, da jota je preslana.", "Rekel je, ali je jota preslana.", "Kljub temu je dražji, ga kupite.", "Če bom imel vinograd,
  bi pridelal rebulo.", "…, ki si prinesel na Martinovo?", "Potem nikoli ne bom pokusil …". The Slovene translations in
  the other three markets too ("krendlati" for krendeln is a loan; "zapreti" may be better).
- **Italian** (al-mercato, Friuli; for Teo, 7, at B1): "Avresti dovuto chiamarmi! Ti avrei aiutata volentieri.", "Me ne
  dai ancora un bicchiere?", "il prato in cui giochiamo a calcio", "come se fosse festa", "Cantando si fa meno fatica.",
  "Ha detto di passare da lei dopo pranzo.". The wrong ones, never said by an adult in Friuli: "Avrai dovuto …", "Se non
  avrei avuto …", "… avrei venuto …", "Ha detto passare …", "Mi ne dai …", "Il prato in che …", "come se sia festa", "Se
  avrei un'osteria …". The German translations with du.
- **German** (auf-dem-markt, Austrian): "der Kirchtag falle heuer aus", "Wegen dem Wetter?" as a second right answer
  beside "Wegen des Wetters?", "Kasnudeln krendeln" (settled: [BMLUK, Kärntner Kasnudeln](https://www.bmluk.gv.at/themen/lebensmittel/trad-lebensmittel/speisen/ktn_kasnudeln.html),
  [Wiktionary, krendeln](https://de.wiktionary.org/wiki/krendeln)), "jemand anderer", "nix heraus", "mein Ross", "die
  Bäume brächten ohnehin nichts mehr", "als wären die Bäume nichts wert". The wrong ones: "der das kann" (for die),
  "Ich würde backen Kärntner Reindling", "Wegen das Wetter", "Ich hätte … gegangen", "werden waschen", "auf es", "als die
  Bäume nichts wert wären".
- **English** (at-the-market, Cumbrian): "ever so cross", "tatie pot", "the beck", "daft", "rotten luck", "Aye". The wrong
  ones: "He said me …", "I'll give him" after "If he came back", "since months", "I used to helping", "I wish I can", "must
  have told you" (for should), "could have stop".

## What it does

- **The format** (companion/SCENES.md, "Dialogs by level"): `"level": "B1"` on a dialog (in a scene's `dialogs` or
  `variants`, or a tutor's variant) and on a happening. A dialog is of its own level, else its happening's. No level:
  for everyone, as every dialog was before.
- **Who meets what** (`DialogLevels.forLearner`, game/scene/DialogLevels.kt): a learner meets what is at or below their
  level in the scene's language (`AppViewModel.levelIn`: the node's level, the treasure's new one included; QA's
  `level:<lv>`). The app trims the scenes before anything reads them (`SceneController.all` is derived from the served
  list and the level), so the bubbles, "Here today", the scene's markers, the road (car mode), the day's audio and a
  visit (`VisitController.settle`, at the visitor's level in the town's language) all see the same trimmed village. An
  A1 learner never meets a B1 or B2 dialog, nor a B1 or B2 happening, nor a dialog only those happenings play.
- **Their own level first** (`DialogVariants.pick`, with each variant's rank from `DialogLevels.rankOf`): the learner's
  own level's variants are asked first (a new one, else the review due three days on, never the same twice in a row);
  only on a day their own level has none to play, the level below, down to the dialogs without a level. The variants of
  one rank (every happening before levels) are picked exactly as before. On a visit the day's dice picks among the
  visitor's own level's.
- **The bridge** (bridge/src/scenes.ts): the schema keeps `level` on a dialog and a happening; `levelErrors` (in
  `crossCheck`, so `publish_scene`, `publish_dialog_variant`, the smoke and `check-culture` all apply it): a happening for
  everyone keeps a dialog for everyone, and its `dialog` (what an older app plays) has no level; a happening whose every
  dialog has a level says it; a dialog of a levelled happening is at least of its level; a storyteller's happening has no
  level. `levelPageErrors` (the smoke over every curated scene, `check-culture`): a levelled dialog names grammar pages of
  at most one level above its own. `allHeard` counts only the dialogs at or below the learner's level, so the tutor is
  asked for a fresh variant once the learner's own are all heard; the note says each heard dialog's level and asks a B1+
  learner for one that says theirs; `publish_dialog_variant` says when a variant is above the learner; `list_scenes`
  lists `dialog_levels` and a happening's `level`.
- **Grammar pages of B1 and B2** (no code change was needed, verified): a page at the learner's level or below is asked
  as any rule is (Adaptive turns: chosen, typed once secure, said once mastered) and its hint chip shows; a page one level
  above isn't asked yet (`Introduction.turn`: its wrong choices go), the learner meets it (`GameState.meetings`), and it
  ripens and unlocks the next level's page as any rule met does (`Introduction.ripe`, `unlocks`: `next(page, learner)`).
  So a B1 dialog may name B2 pages as what a B1 learner meets next.

## Older apps

The app sends no version on its requests (checked: Bridge.kt, bridge/src/http.ts). The scenes' request has carried a
capability number since arts 2: `GET /scenes?arts=N`, and the visit relay passes the visitor's on
(`/towns/:id/scenes?arts=N`). Levels are arts **7** (`SceneArt.VERSION`, `SCENE_ARTS_VERSION`; the smoke checks the two
agree). An app that asks for less, or says nothing (Teo's tablet on 0.2.698 asks with 6 or less), gets no dialog and no
happening with a level, and each happening's variants without them (`forApp` → `withoutLevels`): exactly the dialogs it
had. A file with only `dialogs` gives an older app the first variant without a count or a level as its `dialog`. An app
of arts 7 gets everything and trims it itself, so a learner's level change (the treasure, QA's hook) needs no new request.

**The order to deploy:** a bridge started before this commit doesn't know `level` and strips the key (zod), so it would
serve every levelled dialog to every learner as if it had none. The bridge code and its restart (lani-bridge restart
--all) come before any content with levels reaches main; then the app. The writers' branches add only content.

## Tests

- Android: `DialogLevelsTest` (10): the ranks; an A1 and A2 learner meet no B1/B2 dialog or happening, B1 meets B1, B2
  everything (the same scene object); a happening whose `dialog` went plays the first left, one with nothing left goes,
  a dialog only a held-back happening plays goes; scenes without levels are the same lists, and the pick with one rank is
  the pick of before day after day; the own level first (a B2 learner's eight days: B2, B2, B1, B2, B2, B1, B2, B2; a
  B1 learner's five), the visit's dice within the own level; the village through `Happenings.active`; a child's village:
  sixty days at A1 bring no A2+, at A2 no B1+, at B1 no B2 levelled dialog; a visit at the visitor's level; the app reads
  the keys; every curated scene: what has no level stays for everyone, in its order, at every level, no happening's
  `dialog` has a level, nothing above the learner is left. `DialogVariantsTest` and `HappeningsTest` as they were (11,
  14).
- Bridge smoke `test/smoke/dialog-levels.ts`: the format and the five validator rules; `levelPageErrors` and every
  curated scene (all 132 files) against it; `forApp` at arts 6 and 7 and with no arts; a scene without levels the same
  object; the older app's `dialog` from a file with only `dialogs`; a happening with nothing left goes; `allHeard` by
  level (A2, B1, B2 learners); the note's levels; the routes `/scenes` and `/scenes/<id>` with and without arts;
  `list_scenes`; a tutor's B2 variant published, its "waits" note, held back from an older app.

## The QA hook

`level:<lv>` (QaHooks.level, a debug build) now sets the village's level too: `SceneController.all` reads `levelIn`,
which QA's level overrides, so the scenes are trimmed to it and its variants play first. With
`happening:<scene>/<happening>/<dialog>`, a B2 variant opens at `level:B2`; at `level:A1` it isn't in the scene, and the
happening plays its own dialog (`QaHooks.withForced` finds no such dialog). The QA step `dialog-levels` (bin/qa) plays
that: see [QA](#qa).

## Writers' guide

The standard the model scenes set (one per culture, below). Read companion/SCENES.md "Dialogs by level" and the rules
of every scene dialog (Format, Reactions, Adaptive turns, The learner in the content) first.

**Where.** In the scene file's `variants`, each with `"level": "B1"` or `"B2"` and an id
`<happening>-b1-<topic>` / `<happening>-b2-<topic>`, listed in its happening's `dialogs` (first: the order between levels
is the level's, within a level the file's). Keep the happening's `dialog`, its other variants and their words as they
are: the learners have heard them, and changed text loses its voice clip. Add `level` to the scene's `review` line
("B1, B2 variants: machine-written, not reviewed by a native speaker"), within its 200 characters.

**How many.** Per scene: two B1 and two B2, on the grown-ups' happenings that carry a conversation (the innkeeper, the
trader, the teacher, the miller), one B1 and one B2 on each of two happenings, so a learner at either level meets one of
their own at each. A children's game or I spy stays as it is. A happening of its own for B1+ (`level` on the happening)
only where the situation is B1+ itself (a complaint at the inn, a meeting about the bridge), at most one a scene, with a
`dialog` of that level. Limits: 36 variants a scene, 12 dialogs a happening (the most today: 19 a scene, 5 a happening).

**How long.** B1: 5–7 lines, two turns, a line one or two sentences (up to about 140 characters) with one subordinate
clause. B2: 6–8 lines, two or three turns, lines up to about 180 characters (the format's limit is 200), two clauses
where natural. The person speaks first and last; the learner's turns sit where they'd answer.

**What a B1 line does that an A2 one doesn't.** It leaves the here and now: why something happened, what someone said,
what would happen if, what the person plans or hopes; it asks the learner's view and gives its own with a reason; a small
problem to solve together (a cart that won't fit, a cheese that didn't come). The learner's turn gives a reason, a
condition, a report. The forms are B1's: the conditional, reported speech, relative clauses, subordinate clauses of time
and reason, the language's B1 pages.

**What a B2 line does that a B1 one doesn't.** It weighs and concedes ("of course, but…"), regrets and speculates about
the past ("if we had…"), reports what was said in another tense, shifts register (polite to the mayor, joking with an old
friend), uses an idiom the region has. More is left to the learner: the right answer follows only from understanding the
line, and a turn may test understanding (the reply that fits the person's argument), its wrong choice's why in meaning
words and no page. The forms are B2's: the past conditional, the pluperfect, participle clauses, the language's B2 pages.

**Turns.** Each B1/B2 turn tests something only its level asks: a wrong choice is the mistake a learner a level below
makes there (the indicative where the conditional goes, the tense not shifted back in reported speech, the wrong
relative), with a why that states the rule and its `grammar` page. Never a form people say as a wrong choice
(followups-12.md, "What people say"): German *weil* with the verb second, *wegen dem*, *ich bin gesessen*; English *if I
was you*, *less people*; Italian *se lo sapevo, venivo* (the imperfetto in a hypothetical), *a me mi piace*; Slovene
spoken forms. Where the point is such a form, the standard one is the right choice and the spoken one a second `ok`
with the same reply. The learner's own forms are gender pairs (`{m:…|f:…}`, Slovene and Italian participles and
adjectives); a turn never tests the learner's gender. A reaction to a wrong choice never says the right form; two
choices share a reply only when both are right. The happening's person answers every turn (`to` after someone else's
line).

**Grammar pages.** A B1 dialog names pages of A1 to B2, a B2 one A1 to C1 (`levelPageErrors`); pages at the dialog's
level are asked, the next level's are met and ripen. Each language's B1 and B2 pages (companion/grammar/<lang>):

| | B1 | B2 |
|---|---|---|
| sl | cas-izrazi custva dolocna-oblika dvojina-skloni glagolniki naslonke-red nedolocni-zaimki odvisni-govor odvisniki oziralni-ki pogojnik-ce-bi sklanjatev-moski sklanjatev-posebnosti sklanjatev-srednji sklanjatev-zenski stevila-glagol trpnik vid-casi | besedni-red brezosebno delezja delezniki frazemi govorjeni-jezik izpeljanke odvisniki-napredno oziralni-kateri pogojnik-pretekli pomanjsevalnice predlogi-dva-sklona predpone predpreteklik stevniki stopnjevanje ukazi uradno-pismo |
| it | condizionale congiuntivo-espressioni congiuntivo congiunzioni discorso-indiretto futuro-anteriore gerundio indefiniti infinito-preposizioni passato-remoto passivo periodo-ipotetico pronomi-combinati relativi-cui si-impersonale stare-per trapassato verbi-pronominali | alterati concordanza-tempi condizionale-passato congiuntivo-imperfetto congiuntivo-indipendente congiuntivo-passato congiuntivo-trapassato congiunzioni-congiuntivo connettivi discorso-indiretto-passato fare-lasciare gerundio-passato infinito-passato participio-assoluto passivo-venire-andare periodo-ipotetico-irrealta raccontare relativi-il-quale |
| de | adjektiv-tabelle doppelkonnektoren genitiv indefinitpronomen infinitiv-zu konjunktiv-zwei obwohl-trotzdem passiv plusquamperfekt praepositionen-genitiv praeteritum relativsatz temporalsaetze werden-futur wo-da-woerter | adjektiv-nomen adjektive-praepositionen als-ob formeller-brief indem-sodass indirekte-rede konjunktiv-vergangenheit modalverben-vermutung n-deklination nomen-verb nominalisierung oesterreichisch partizip-adjektiv partizipialattribut passiv-ersatz passiv-zeiten satzbau zwar-aber |
| en | contrast-linkers for-since future-continuous might-must passive past-perfect phrasal-verbs present-perfect-continuous question-tags reflexive-pronouns relative-clauses reported-questions reported-speech second-conditional third-conditional too-enough used-to verb-patterns | collocations emphasis formal-informal future-perfect have-it-done mixed-conditionals narrative-tenses participle-clauses passive-more past-modals phrasal-verbs-more quantifiers relative-clauses-more reporting-verbs result-linkers wish-if-only word-building would-used-to |

**Audience and variety.** Primorska: an adult's village (Jan: Slovene from English). Friuli: Teo, 7, learning Italian
from German, B1 for now: its B1 variants a child's world (school, family, animals, games, food, the sagra), the German
with "du", every form a child needs glossed, nothing frightening; its B2 variants fine for an older learner and nothing
unsuitable for a child (no drink offered to the learner, no money worries, no romance). Kärnten: Austrian German
(heuer, Jänner, Paradeiser; the German-German form accepted, never wrong). Lakeland: British English, Cumbrian where a
source ties it there (followups-12.md, "Which variety").

**Translations.** Every line in the scene's language and in each base the culture's scenes have (Primorska sl, en, de,
it; Friuli it, sl, en, de; Kärnten de, sl, en, it; Lakeland en, sl, it, de), a why in English or per language, the
memory (if a variant has its own) likewise. All machine-written: say so in `review`.

**Voices.** A scene's lines are voiced in their speaker's voice from the voice cast (the happening's person and anyone
the lines name), the learner's choices in the learner's voice; nothing new to cast. A voice build of a variant costs the
characters of its texts in the scene's language: its lines, its choices, their replies and its puzzled reactions (the
voice corpus, `corpus()` in bridge/src/voice.ts, gives the list). Say the total in your doc; never call ElevenLabs.

**Words.** A tap on a word of a line opens its card: every word your lines use that the language's dictionary lacks gets
an entry in the supplement, `companion/lexicon/<lang>.extra.json` (its gloss, its forms, its meanings in the other
bases), and `companion/bin/lexicon-build --coverage --lang <lang>` must not fall (the model's Slovene: 21 entries, 95.5 →
95.6 % of tokens). It needs the language's dictionary (`<lang>.json`), which only Slovene's checkout has; for Italian,
German and English run it where the dictionary is.

**Checks.** `bun test/check-culture.ts <culture>` (the levels and the pages), the smoke (`dialog-levels`, `scenes`,
`variants`, the culture's section), and the Android scene tests: `DialogLevelsTest`, `DialogReactionsTest`,
`WhoAnswersTest`, `VariantsContentTest`, `ScenesContentTest`/`CultureScenesTest`. QA: `companion/bin/qa --steps
dialog-levels` with your scene's ids (see below).

## The model scenes

The four markets, which mirror each other (a grandmother at her stall, the innkeeper shopping, the wine or cider or
ale cart), each with one B1 and one B2 variant on the innkeeper's happening and on the cart's: a learner at either level
meets one of their own at each. The happenings' `dialog` and their old variants stay as they were; the new ones come
first in `dialogs`.

| Culture, scene | Happening | B2 | B1 |
|---|---|---|---|
| Primorska, `na-trznici` (Vida says vi, Marko ti) | `gostilna` (Vida) | `gostilna-b2-sir`: the cheese sold out; "Ko bi bila … vstala!" understood (`pogojnik-pretekli`), "kljub temu da / čeprav" (both right) against "kljub temu" (`odvisniki-napredno`) | `gostilna-b1-gost`: what the grumbling guest said: da and the short words after it (`naslonke-red`), da not ali, naj mu not naj mi (`odvisni-govor`) |
| | `rebula` (Marko) | `rebula-b2-toca`: the hail; "vreči puško v koruzo" understood (`frazemi`); "ne bi (bil) nikoli pokusil" (both right) against "ne bom" (`pogojnik-pretekli`) | `rebula-b1-vinograd`: če bi … bi against če bom (`pogojnik-ce-bi`); "ki si ga prinesel" (`oziralni-ki`) |
| Friuli, `al-mercato` (Italian for a child; B2 fine for an older learner) | `osteria` (Marisa) | `osteria-b2-sagra`: the frico ran out at the sagra; "avresti dovuto" against "avrai dovuto" (`condizionale-passato`); "se non avessi avuto la febbre, sarei venuto" against "se non avrei" and "avrei venuto" (`periodo-ipotetico-irrealta`) | `osteria-b1-messaggio`: Nonna Rosa's message, "ha detto di passare da lei" against "ha detto passare" and "da me" (`discorso-indiretto`); "se avessi un'osteria, farei la pizza" against "se avrei" (`periodo-ipotetico`) |
| | `carro` (Matteo) | `carro-b2-grandine` (September to November): the hail after the harvest, "avremmo perso tutto" understood (`periodo-ipotetico-irrealta`); "come se fosse" against "come se sia" (`congiunzioni-congiuntivo`) | `carro-b1-succo`: grape juice, "me ne dai" against "mi ne dai" (`pronomi-combinati`); "il prato in cui" against "in che" (`relativi-cui`) |
| Kärnten, `auf-dem-markt` (Kathi says du, the learner Sie; Stefan and the learner du) | `gasthaus` (Kathi) | `gasthaus-b2-kirchtag` (July to September): the mayor's "der Kirchtag falle aus", "wegen des Wetters" and Austria's "wegen dem Wetter" (both right) against "wegen das" (`praepositionen-genitiv`); "ich wäre an Ihrer Stelle gegangen" against "hätte … gegangen" and "bin … gegangen" (`konjunktiv-vergangenheit`) | `gasthaus-b1-hochzeit`: the wedding and the Kasnudeln to krendel; "eine Frau, die das kann" against "der" (`relativsatz`); "ich würde Reindling backen" against "würde backen Reindling" and "werde" (`konjunktiv-zwei`) |
| | `most` (Stefan) | `most-b2-obstwiese`: the council's "die Bäume brächten nichts" understood as reported (`indirekte-rede`); "als wären" and "als ob … wären" (both right) against "als die Bäume … wären" (`als-ob`) | `most-b1-presse` (September to November): "werden gewaschen" against "werden waschen" (`passiv`); "darauf" against "auf es" (`wo-da-woerter`) |
| Lake District, `at-the-market` (Mrs Birkett says love) | `inn` (Mrs Birkett) | `inn-b2-jam` (August to October): the last jar sold; "should have told you" against "must have" (`past-modals`); "I wish I could" against "I wish I can" (`wish-if-only`) | `inn-b1-walker`: "he told me his room was too cold" against "said me" and "my room" (`reported-speech`); "if he came back, I'd give him" against "I'll" (`second-conditional`) |
| | `cart` (Sam) | `cart-b2-flood`: the beck in the brewhouse, "Had I moved them, I'd be selling them" understood (`mixed-conditionals`); "could have stopped" against "could have stop" (`past-modals`) | `cart-b1-ale`: "for months" against "since months" (`for-since`); "I used to help" against "I used to helping" (`used-to`) |

Each is 5–7 lines with two turns, the person first and last, a memory of its own; every wrong choice has its why (with
its page) and a reaction that doesn't say the right form; two right choices have their own replies; the learner's forms
that agree with them are gender pairs (Slovene, Italian). Every page named is at most one level above the dialog's
(`levelPageErrors`), all of them existing pages of the scene's language.

**Voices** (no ElevenLabs called): the new texts in the scenes' languages, the lines, the choices, the replies: Slovene
2,977 characters (54 texts: Vida 548, Marko 517, the learner's choices 1,006), Italian 2,827 (51: Marisa 529, Matteo 558,
the learner 861), German 3,525 (56: Kathi 644, Stefan 606, the learner 1,031), English 2,871 (50: Mrs Birkett 564, Sam
577, the learner 815): **12,200 characters** in all. The learner's choices with a gender pair are voiced once per form.
No new voices: Vida, Marko, Marisa, Matteo, Kathi, Stefan, Mrs Birkett and Sam have theirs in the cast.

## QA

`companion/bin/qa --steps dialog-levels` (through `qa-slot`), on a synthetic learner with a market
(`LANI_DEV_DATA=$(cd companion/bridge && bun test/fixtures/make-learner.ts <dir> --target <lang>)`, `LANI_CULTURE` for
another culture's): an older app's `?arts=6` gets none of the market's levelled dialogs; at `level:B2` the innkeeper's
B2 variant opens with its first line, at `level:B1` the B1 one; at `level:B1` the B2 variant asked for isn't in the scene
and at `level:A1` the B1 one isn't: the happening's own dialog plays. Screenshots also in `$LANI_QA_LEVELS_SHOTS`.

## What's left

- The other 128 scenes' B1/B2 variants: the eight content writers, by this guide.
- The Italian, German and English model lines against their dictionaries (`lexicon-build --coverage`): the checkout has
  only Slovene's, so their words' supplement entries are left to a run where the dictionaries are.
- The native check above, and the voice build (12,200 characters) when Jan spends the credits.
- An older app's learner at B1+ (on 0.2.698) never gets the levelled variants, and `allHeard` counts them as theirs, so
  the tutor isn't asked about a happening whose levelled variants that app never plays. It ends with the app's update.
