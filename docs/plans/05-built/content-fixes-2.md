# Built: content that was wrong, part 2

*Built 2026-11-02, branch `content-fixes-2` (wave C, part 5; Jan: "sure continue"). The items come from the agents'
leftovers (followups.md), each checked against the files on main 21aa4e9b before it was changed. Every text written here
is machine-written (Claude): the list for the native check is at the end. No spoken form was "corrected", and none was
retracted.*

## What, item by item

### Already done on main (nothing to do)

| Item | Done by |
|---|---|
| Friuli's chapel step says shingles, the ancona is drawn with coppi | 5414592c: projects.json's step 4 is "Il tetto di coppi" (Friuli has no project-steps of its own) |
| al-fuoco's and il-mare's "for the two of you / für euch beide" in the whys | content-fixes: "For Tommaso and you together, say andiamo", corriamo the same |
| dvojina additions ("Kuhali sta." for onidve …) | content-fixes: dvojina-3 and the test accept the forms without the pronoun |
| B1a distractors graded almost («pesom», «sedli») | content-fixes (f003d81b): a sheet's distractor is wrong, never almost |
| A child's reply in an adult's mouth (zala A2/B1, nejc A2; giulia, lorenzo, tommaso A2) | content-fixes: the turns name whom the learner answers (`to`) |
| faces-content's facts and slips (vinograd, mlin's done line, bel-dim, klop-sneg "Zimo", the v-sotoru gloss) | content-fixes; "pote" settled by SSKJ² (e5c5d810: written together, standard) |
| BuildingWords.PACKS' doc comment | polish-content: it says what each culture's buildings ask for now |

### The Lake District

**The chapel's fourth step: an iron gate, not a wooden door.** At the chapel (ChapelPainter, the Lakeland look) draws an open
wrought-iron gate in the arch, but the step said "a heavy wooden door". The step's word `wooden` stays; the wooden thing is
now the oak roof beams, and Mr Dixon brings the gate from the forge:

| | Was | Now |
|---|---|---|
| A1, Edna | Oak beams for the roof, and a heavy wooden door. | Oak beams for the roof. Big, heavy beams! |
| A1, the turn | The door is wooden / woollen and heavy. | The beams are wooden / woollen and heavy. ("never a roof!") |
| A1, Dixon | And a big iron key, from the forge. | And for the front, an iron gate from the forge. With a big iron key. |
| A1, Edna | … a roof, a door and a key! | … a roof, a gate and a key! |
| A2, Edna | The beams are up and the roof is on. Now the door! | The walls are up. Now the roof, and then the gate! |
| A2, Dixon | Heavy wooden door. Oak. Lasts a hundred years. | Heavy wooden beams. Oak. They last a hundred years. |
| A2, the turn | A wooden door is / are strong. | Wooden beams are strong / Wooden beams is strong ("There are six of them, love!") |
| A2, Edna | And who keeps the key? Me, of course! | And the iron gate has a key. Who keeps it? Me, of course! |

The chronicle's task and line ("The roof beams and the gate", "They put up oak beams for the roof and hung an iron gate in
the front."), the project pack's description and `wooden`'s example ("The chapel's roof has heavy wooden beams.", 🪵) say the
same. A village that has played the step keeps the line it logged (`GameState.log` stores the text, so its chronicle still
says the wooden door); a step played from now on logs the new one. The same for the hives below.

**The bee meadow's fourth step: the hives painted white.** At the bees draws white WBC hives; the step and its chronicle line
had the children paint them "in lots of colours". Arthur now answers "Which colour do you like?" with "White, friend, like
every hive in the dale. White keeps them cool in the sun." and "White it is. Here's a brush for you."; at A2 "Our hives are
always white", red gets hot and bees can't see it, and the can / can has turn is "Then every hive can have the same colour:
white!". The chronicle: "The children painted the hives white." (sl task "Pobarvati panje", de "anstreichen": painting a
surface, not decorating it); the pack's description and `colour`'s example agree.

**The bell's Italian.** On the fell's `bell` is the one bell word of the culture: the chapel's, the church's, the market's,
the gate's and the rescue hut's slots all name it. Its Italian was "campanaccio", a cowbell. It is "campana" now, as Friuli
has it for the same slots; Daisy's sentence keeps "Il campanaccio di Daisy" and the note tells the two apart. Its Slovene had
the same fault in a smaller way: SSKJ² has zvonec as a signal device or a small bell ("kravji zvonec", no church or chapel:
https://fran.si/iskanje?View=1&Query=zvonec), and the chapel's, the church's, the gate's and the hut's lines say "zvon"; the
prompt is "zvon; zvonec" now, the market's handbell and the cow's bell still "zvonec" in their lines. No other word of the
Lake District or of the English packs asks "campana", "zvon" or "zvonec".

**Old Wilf on the green, not in his story's evening.** Wilf is the storyteller: every evening he is by the fire, and the map
stands him there (`TownMarkers.standing`: a villager's first happening, the story). "Where's Bess?" and the summer fête on the
green were evening happenings too. Both are afternoon ones now: Bess's goodbye is a daytime one ("Bye, Wilf! Bye, Bess!",
"See you, Wilf! Look after Bess!"), its wrong choice "Good night" ("Night? It's nowhere near bedtime"), safe for any hour from
11 to 17 in any month; its memory "the day Bess came to you" (was "the evening …"). An English village fête is an afternoon
one anyway; its lines name no time.

**At the gate's "ovac": not changed.** SSKJ² gives the genitive plural of ovca as "ôvc in ovác", both standard
(https://fran.si/iskanje?View=1&Query=ovca); the course says so already (stevila: "ovca (ovc, also ovac)").

**lawn-bowl reads "bowl": not fixed, it needs code.** It is more than a look-alike; measured with the bridge's own functions:

- **One of the two is never taught.** A pack word counts as had when the learner has any card with the same word
  (`learnedWords` in packs.ts; the app's `Dashboard.learnedWords` and `WordBook.card`). With the kitchen's `bowl` (skleda) carded,
  the bowling green's `lawn-bowl` (krogla) counts as had, and the other way round.
- **A tap can give the wrong meaning.** The lookup keeps one entry per lemma and part of speech, the first pack's, and packs
  are read in file order. With the kitchen's pack first, a tap on "bowl" in "Wilf rolls his bowl along the grass." says
  "skleda"; "krogla" doesn't show.
- Reviews are fine: a choice's distractors leave out cards with the same front. The scene's ball, tapped, names its own word.

Content can't fix it. A card's word is compared without its brackets, so "bowl (bowls)" is still "bowl"; "lawn bowl" isn't what
the lines or a Cumbrian bowler say, and "wood" is already the wood's. It wants a rule, for instance a pack word marked as a
homonym (`"homonym": true`) that gets its own card and its own entry on a tap: app and bridge code (Packs.kt, Models.kt,
BuildingWords.kt, packs.ts, lexicon.ts), and a pack key an older bridge's schema must learn first, so one owner and the lead's
call. `round` has the same fault (in-the-pub's round of drinks, project-mestna-ura's round, okrogel).

### Kärnten

**der Graben and graben: the cards stay, the tap's label is fixed.** Measured, the two don't collide as bowl's do: the words differ by
the article, which the same-word rule keeps, so each is taught whichever came first, and the note teaches the link ("the verb
graben, to dig, is the same word"). But since supplement-coverage gave the German supplement its verbs, a tap called the noun a
verb: a pack word takes its part of speech from the dictionary's reading of its bare word, and "der Graben" found the supplement's
graben. Three more nouns had the same (der Tropfen, der Haken, der Husten; found by scanning the packs for a noun whose bare word
is only a verb there). A word with a gender is no verb now (`lookup` in lexicon.ts), so the tap reads "der Graben, noun, jarek"
and "graben, verb, kopati"; the smoke checks it. Noun entries in the supplement were tried first and dropped: they listed the
meaning twice.

One small thing is left in the lookup: from English the line's translation puts the right one first, but from Slovene and
Italian the noun stands first even in "Wir graben ein tiefes Loch." (jarek, then kopati). In German a lower-case word inside a
sentence can't be the noun, so the lookup could decide it.

**The two wood steps in Im Wald.** The toplar's first step (the larches felled for the Doppelharpfe) and the tower's third
(the larch trunks carried) played over the village until Im Wald was on main. Both name `"scene": "im-wald"` now, as the
bridge project's fourth step does; the first says it from there ("Hier im Wald gibt es viele Bäume"), the tower's lines read
right as they were ("Heute bringen die Burschen das Holz aus dem Wald"; "tragen" stays out of them, since it is what the
turn after them tests). The wood doesn't draw these projects, so they show their work once played, as the log step does. Played
in the picture, the larch's "Achtung, sie fällt! … Bumm!" said with every tree still standing, so at that line a flock flies up
out of the trees (`birds`, as Primorska's oak and Carinthia's maypole spruce have it). The tree itself isn't drawn falling: the
wood's `fell` is the maypole's spruce (a finished maypole has only its stump there) and its `fell-oak` an oak; see What's left.

### Primorska and the Slovene course

**sejem in every base.** The B1 units list `sejem` and the dictionary was said to read it as sejati's "I sow". In English it
doesn't any more (the supplement's noun first, the prints' order of 6ab6d07c), but the supplement's entry had no German or
Italian meaning: a sheet in German or Italian printed "sejem: ich säe" / "semino" (naslonke-red, stevila-glagol), and the
lookup gave the English. sejem has de "Markt, Jahrmarkt" and it "fiera, mercato" now, semenj "Jahrmarkt (auch sejem)" and
"fiera (anche sejem)", as Primorska's slavje-semenj pack has them. `sejem` is back in the words of besedni-red (18 uses) and
stopnjevanje-napredno, which 4d5725b5 had taken it out of; govorjeni-jezik stays without it (one more word takes its German
and Italian sheet from one page to two). All seven sheets that list it print the fair in de and it; their page counts are main's
(vezniki, cas-izrazi, naslonke-red, stevila-glagol, pogojnik-pretekli compared in de and it; besedni-red, stopnjevanje-napredno in
en, de and it).

**The spoken hour in three more turns** (asked for during this round; found by hints-lens-3, whose lens reads "ob deset" as the
locative). A spoken form is never wrong, but three curated turns graded it:

| Turn | Spoken choice | Its reply was | Now |
|---|---|---|---|
| kuhinja `radio` | Kdaj so poročila? Ob pet? | Ob pet? Pet česa? Ne slišim dobro! | Ob petih in ob sedmih. Vsak dan. |
| ob-potoku `potok-dolina` | Kdaj greva? Ob osem? | Ob osem? Hm. Moja ura tega ne pozna. | Ob osmih, ja. Pri brvi te čakam. |
| v-soli `naloga-dnevi` | Ob deset zvečer. | Ob deset? Hm, učiteljica tako ne piše. | Ob desetih! Tako pozno? Jaz takrat že spim. |

Each is a second right choice, without `grammar` or `why`, glossed as the scenes that already accept the hour gloss it: "(as
people say it)", "(wie man es sagt)", "(come si dice a voce)" (v-soli's said "(wrong form)"). Those scenes (Pod uro, V cerkvi, Na
vasi …) mostly write the spoken choice a reply of its own ("Ob desetih, ja! …"). Here the reply is the standard choice's, word
for word: each of the three already says the written form first, so it is the same recast, and its clip exists.

kuhinja and v-soli keep their wrong choice. ob-potoku had only two, so it has a third, wrong by meaning: "Kam greva? Ob osmih?"
(France has just said where; page vprasalnice), reply "Kam? V dolino, v stari mlin. Saj sem že rekel!". Kam is SSKJ²'s plain
adverb, "kam greš?" (https://fran.si/iskanje?View=1&Query=kam). A scan of every turn of the curated scenes and Primorska's pack
finds no other choice grading "ob" with a bare hour; 22 choices still name `kdaj-cas`, and no test counted them.

**For the voice plan** (not voiced here; Jan's call): France's "Kam? V dolino, v stari mlin. Saj sem že rekel!" and the choice
"Kam greva? Ob osmih?". Nothing else in this round's three turns needs a clip.

### Docs

The Italian, German and English grammar books had "16 each, A1 and A2" in companion/README.md and the studio's grammar-spec.
They have 52 (15 A1, 19 A2, 18 B1), 21 (15 A1, 6 A2) and 22 (18 A1, 4 A2); the README's lists name every page with its level.

## How to see it

- The chapel: a Lake District village (make-learner.ts --target en --base sl, LANI_CULTURE=lakeland), the chapel's fourth
  step from "Village projects · Skupni projekti" (QA's hook "step:kapelica/4"); the bee meadow's "step:cebelji_travnik/4".
- Bess: On the village green from 11 to 17, or QA's hooks "hour:14" and "happening:on-the-green/bess/bess".
- The wood steps: a Carinthian village (--target de, LANI_CULTURE=kaernten), "step:toplar/1" and "step:razgledni_stolp/3":
  "🔨 Heutiger Schritt" opens Im Wald.
- QA's step `content-fixes-2` (skipped outside those two cultures) drives all of it, shots cf2-*.png.

No tap counts change: the same sheet, button and turns; the wood steps open the forest instead of the backdrop over the
village (0 taps more).

## Order of going out

The content needs nothing first: every key it uses an older bridge knows (a step's `scene` and `fx`, the supplement's `meanings`).
The lookup's fix is bridge code; until it is deployed a tap goes on calling those four nouns verbs, as it does on main today. The
QA step is test code.

## Native check

Slovene (the wife's register calls; a spoken form is never wrong):
- the chapel: "Hrastovi tramovi za streho. Veliki, težki tramovi!", "Tramovi so leseni / volneni in težki.", "Volneni? Spletla
  sem veliko stvari, a strehe še nikoli!", "Spredaj pa železna vrata iz kovačnice. Z velikim železnim ključem.", "Stene
  stojijo. Zdaj pa streha, potem pa vrata!", "Težki leseni tramovi. Hrastovi. Zdržijo sto let.", "Leseni tramovi so trdni.",
  "In lepo dišijo. Po gozdu.", "Volneni? Spletla bi jih, a streha bi se podrla!", "Več tramov (beams): are. Is je za enega.",
  "Is? Saj jih je šest!", "Železna vrata imajo ključ. Kdo ga hrani? Jaz, seveda!", the chronicle's "… in spredaj obesili
  železna vrata", "Streha kapelice ima težke lesene trame."; is "železna vrata" right for an open wrought-iron gate in an
  arch (or "kovana vrata", "mreža")?
- the hives: "Bela, prijatelj, kot vsi panji v dolini. Bela barva jih na soncu hladi.", "Torej bela. Izvoli čopič.", "Naši
  panji so vedno beli …", "Rdeča se na soncu segreje, čebele pa je tudi ne vidijo dobro. Bela je najboljša.", "Potem ima
  lahko vsak panj isto barvo: belo!", "Ja. Vrsta belih panjev, kot ovce na hribu.", "Pobarvati panje", "Otroci so panje
  pobarvali belo.", "Vsi naši panji so iste barve: beli.";
- Bess: "Adijo, Wilf! Adijo, Bess!", "Hov! Tako Bess reče adijo.", "Se vidiva, Wilf! Pazi na Bess!", "Bom. Hov, reče Bess.",
  "Noč? Saj še zdaleč ni čas za spat!", "Good night je za pred spanjem. Ko greš čez dan: goodbye ali bye.", "dan, ko je Bess
  prišla k tebi";
- Kärnten's sl: "Tu v gozdu je veliko dreves.";
- the spoken hour: "Kam greva? Ob osmih?", "Kam? V dolino, v stari mlin. Saj sem že rekel!";
- the bell's prompt "zvon; zvonec" (SSKJ²'s zvonec has no church or chapel; the market's handbell and the cow's bell keep it);
- settled, nothing to check: "ovac", SSKJ² "ôvc in ovác" (https://fran.si/iskanje?View=1&Query=ovca).

English (the Lake District's lines above): "an iron gate from the forge", "There are six of them, love!", "White, friend,
like every hive in the dale", "Red gets hot in the sun", "Bye, Wilf! Bye, Bess!", "Night? It's nowhere near bedtime".

German: "Wohin gehen wir zwei? Um acht?", "Kam (wohin)? Ins Tal, zur alten Mühle. Das hab ich doch schon gesagt!"; "Und vorne ein Eisentor aus der Schmiede", "Balken aus Wolle", "Die Bienenstöcke anstreichen", "weiß angestrichen";
Kärnten's "Hier im Wald gibt es viele Bäume"; the supplement's sejem "Markt, Jahrmarkt" and semenj "Jahrmarkt (auch sejem)".

Italian: "Dove andiamo? Alle otto?", "Kam (dove)? A valle, al vecchio mulino. L'ho già detto!"; "un cancello di ferro della fucina", "Le travi di lana sono robuste", "Allora bianco.", "Il bianco le tiene fresche
al sole", "Manca un sacco all'ora di andare a letto"; "campana" for the Lake District's bell; sejem "fiera, mercato".

## Tests

On the branch with main merged (a407a0a2, qa-repair and supplement-coverage in):

- the bridge's `bunx tsc --noEmit -p .`: clean; `bun build --no-bundle` on the merged and changed test files; `tutor-export
  --check`: 89 files match; `check-culture` and `check-steps` on all four cultures: ok;
- the smoke (`LANI_SMOKE_PORT=9730 LANI_SMOKE_FRIULI_PORT=9733 LANI_SMOKE_TOWN_PORT=9736 LANI_SMOKE_DRAMA3_PORT=9748`): all 3,477
  checks passed, among them the new der Graben check and qa-repair's prompt clashes (the bell's new prompts add none);
- the Android suite in full, one merge earlier (4b9cc941 and the lookup's fix, before the spoken hour and qa-repair's merge): 3,760
  tests in 483 classes, none failed; on the final tree the scene content tests (ScenesContentTest, SceneCoherenceTest,
  CultureScenesTest, ProjectStepsContentTest): 27, none failed. **The full suite hasn't run on the final tree**: the lead's gate
  re-runs it;
- Python: 251 OK and 142 OK (11 skipped) at the first merge; the QA scripts' own tests again on the final tree.

**Emulator QA** (qa-slot's slot 2, whose dev bridge is 8781; synthetic learners; the APK of the lookup's fix, so with every
change above but the spoken hour, which **no emulator has played**, and the bell's prompt, which no step here taps): QA's new step
`content-fixes-2`, green in both cultures. Shots in the session's scratchpad, `ux-content-fixes-2/final-kaernten/` and `final-lakeland/`;
the folders `kaernten/` and `lakeland/` beside them are earlier runs, some with lines since changed.

- Carinthia (de · sl): the sheet's "🔨 Heutiger Schritt" opens Im Wald for the Doppelharpfe's first step and the tower's third,
  each played to its end card ("Die Doppelharpfe 1/6", "Franz und die Nachbarn haben im Wald Lärchen gefällt.").
- The Lake District (en · sl): the chapel's fourth step over the village to "A little chapel by the road 4/6"; the bee meadow's
  fourth in At the bees with its white hives; Old Wilf's Bess at two in the afternoon to "Woof! That's Bess for goodbye."

The runs that failed on the way were QA's own, each fixed in the step: the cast not met, a tap on a choice's bold word, the end
card's button over the village, a choice's text matched in the talk above the turn.

## What's left

- **Homonyms in packs** (above), this list's one open item: bowl and bowl, round and round. One meaning is never taught and a
  tap can give the wrong one. A pack-word flag and the rule in the app and the bridge.
- A German tap's order for der Graben and graben from Slovene and Italian (above).
- **Im Wald has no falling larch.** Primorska's hayrack fells its oak in the picture (`fell-oak`); Carinthia's would need that
  tree drawn as a larch in its look (ForestPainter's `felled`, with its render digests). Until then the birds say it.
- In the wood's steps the third person stands on the art's third spot, up in the hunter's high seat (Schmied Toni calling "Achtung,
  sie fällt!" from it, a child in the tower's). It's `Projects.placed`, the same as Primorska's wood steps.
- **The storyteller's other evening happenings, every culture.** The Lake District's green is fixed, but each storyteller has
  evening happenings elsewhere while his story is on by the fire: Primorska's Janez (na-vasi `muri`, pri-ribniku `ribe`,
  v-hisi `fotografija`, v-soli `stara-sola`), Friuli's Bepi (in-piazza `moro`, `sagra`; in-salotto `foto`), Kärnten's Sepp
  (auf-dem-dorfplatz `musik`, in-der-stube `foto`), the Lake District's Wilf (in-the-living-room `photo`, "put the lamp on").
  The map shows him at the fire and the other bubble without him. Either the content moves (as here), or a rule: while the
  story is on, his other happenings wait (`Happenings.active`'s one-per-person is per scene today). After the story he goes to
  bed (Routine), so a rule alone would leave them never on in the evening; a lead's call.
- govorjeni-jezik could list `sejem` once its German and Italian sheet has room.
