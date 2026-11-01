# Built: every speaker as themselves in a scene's dialog (scene-speakers, 2026-10-31)

Follows [scene-replier.md](scene-replier.md). There the emulator showed that a scene's panel drew and voiced every line
of a happening's dialog as the happening's person: `SceneScreen` gave `DialogPanel` other speakers only for a project
step's cast, so `whoIs` made anyone else the happening's person (the header's portrait and name, each line's little face),
`DialogVoice` said every line in their voice, and the picture's `facing` was always theirs. Nejc's "Ana! Jože! Gremo v
gozd!" came under Jože's portrait, in Jože's voice. Jan's lead: make the app change, production grade.

## What

**The cast** (`ui/scene/DialogCast.kt`): a dialog's speakers are the happening's person, then everyone its lines name
(`who`) and its turns answer (`to`), in the order they first speak, each as the scene has them (`SceneSpec.people`, placed
in the picture or not), else as a villager of the village (their sprite in the panel, no place in the picture). A project's
step keeps its cast, as before. `DialogCast.speakers` gives the panel each one's portrait, name, face seed and voice
(`SceneWords.voiceOf`: a villager's own speaker, else the sprite's).

**On the screen**, in a scene's happening and in a guest's dialog on a visit (`SceneScreen`, `VisitSceneScreen`):

- **Lines:** each line's little face and its 🔊 are its speaker's (`whoIs`, `Theirs`), and each line is said aloud in its
  speaker's voice as it comes (`rememberDialogVoice`, its `speakerOf` the cast's).
- **Header:** follows `DialogCast.facing`, which is `DialogRun.facing`: at a turn the one it names (`to`), else whoever
  spoke last. While Nejc speaks the header is Nejc; at the turn Jože answers (its `to`) it is Jože.
- **Picture:** the one faced wears the look (the face, the pose, talking while their line is said) when the scene places
  them; Jože's sprite rests while Nejc speaks from off the picture.
- **Missing speaker:** a speaker only the village knows gets their sprite in the panel and nothing in the picture; one
  nobody knows keeps the old fallback, the happening's person's portrait and voice (the bridge already refuses a line whose
  `who` isn't a person of the scene). Nothing throws.

**The tutor's content** (`answererErrors`, bridge `scenes.ts`): `publish_scene` and `publish_dialog_variant` refuse a turn
whose answerer the header can't show: a turn after someone else's line that the happening's person answers without naming
them as `to`. The error says where and what to do:

```
✖ dialog ovce-nejc.lines[1]: the header shows nejc (who spoke last) but luka, the happening's person, answers: name "to": "luka" on the turn and write its replies for them (or let luka speak before it)
```

It plays every way through the right choices (a right one with a reply and one without leave someone else speaking last).
Only at publishing: a variant or a scene the learner has kept is never dropped when the bridge reads it. The curated
scenes are held to the same by `WhoAnswersTest` (scene-replier).

**The QA log** says whose voice a line was said in: `heard «…» after N ms (nejc, in nejc's voice)` (tag Speaker).

## Voice clips

No new texts. voice-build has always voiced a line in its speaker's voice (`voices.get(l.who)`: the villager's voice, else
the sprite's), and a reply in the voice of the turn's `to`, else of whoever spoke last; the app now asks for each line in
its speaker's voice, and for a reply in the voice of the turn's `to`, else the happening's person's. The two agree for every
curated turn (`WhoAnswersTest`) and for every turn the tutor can publish (`answererErrors`). So:

- 63 curated lines of someone else than the happening's person (23 Primorska, 8 Friuli, 11 Kärnten, 21 Lake District) are
  asked for in their speaker's own voice now: the clips voice-build made of them are the ones played; clips made live
  earlier in the happening's person's voice for those texts are no longer asked for.
- The 26 turns fixed on scene-replier: their replies are asked for in the happening's person's voice, as voice-build makes
  them now; the clips built before in the other speaker's voice are never asked for. The 68 clips scene-replier lists are
  still wanted (voice-build, not run here: it spends ElevenLabs credits).

## How to use

Nothing to do: it comes with the next app build. QA: `companion/bin/qa-slot --steps scene-speakers` (only when named), on
Primorska's `pri-vratih`/zapah-nejc, or with `LANI_CULTURE=friuli` (an Italian learner, `LANI_QA_PAIR=sl-en`) Friuli's
`in-cantina`/brocca: the other speaker's lines heard in their own voice, the header on whom the first turn names, the
happening's person's reaction as themselves, and what follows the right choice (Nejc's own line: the header on Nejc);
screenshots also as speakers-*.png in `$LANI_QA_SPEAKERS_SHOTS`.

## Tap counts

Unchanged: the same panel, the same steps.

## QA

On the emulator (`qa-slot --steps scene-speakers`; synthetic learners from make-learner.ts, a Slovene one and an Italian
one with `LANI_CULTURE=friuli`, the phone in sl · en), with this branch's app (after) and the app before it (scene-replier's
build, before), both on scene-replier's content. Screenshots in the scratchpad's `ux-scene-speakers/after/` and
`before/` (speakers-primorska-*, speakers-friuli-*: the turn, the reaction, what follows the right choice).

| Moment | Before | After |
|---|---|---|
| zapah-nejc: Nejc's first line, at the turn | under Jože's face, said as Jože's; header Lovec Jože | Nejc's face, "in nejc's voice"; header Lovec Jože (the turn's `to`) |
| zapah-nejc: Jože's reaction | Jože | Jože, "in joze's voice" |
| zapah-nejc: Nejc's "Zakaj ne? Saj nisem majhen!" | under Jože's face; header Lovec Jože | Nejc's face, "in nejc's voice"; header **Nejc** |
| brocca: Matteo's line, at the turn | under Marisa's face, said as Marisa's | Matteo's face, "in matteo's voice"; header Ostessa Marisa (`to`) |
| brocca: Marisa's reaction and reply | Marisa | Marisa, "in marisa's voice" |

After: the step's checks pass on both cultures. Before: they fail where they should (the older app's log names no voice,
and its header stays on the happening's person).

## Checks

- Android unit tests, the full suite (`lani-slot.sh`, `:app:testDebugUnitTest`): 3572 tests in 456 classes, 0 failed,
  2 skipped; `DialogCastTest` (5), `WhoAnswersTest`, `DialogReactionsTest`, `SceneCoherenceTest`, `ScenesContentTest`
  among them.
- Bridge: `bunx tsc --noEmit -p .` clean; the smoke (`LANI_SMOKE_PORT=9410`, `LANI_SMOKE_DRAMA3_PORT=9428`): all checks
  passed, the four new ones of the two-person scene too; `tutor-export --check`: 88 files match (53 tools, 32 guide topics).
- Python: `tests` 251 OK, `companion/tests` 135 OK (11 skipped).
- Emulator: `qa-slot --steps scene-speakers`, Primorska and Friuli, after and before (QA above).

## What's left

- **Who is in the picture.** A speaker the happening doesn't bring into the scene (Nejc at the gate: the happening `zapah`
  has no `with`) speaks from off the picture: their portrait in the panel, nobody turning. A happening's `with` could bring
  them. Done on `polish-content` ([polish-content.md](polish-content.md)): Nejc's evening is a happening of its own, with
  him on the bench.
- **The clips** above (voice-build), and scene-replier's native check.
