# Faces: the person you talk to, on screen, with expressions (built 2026-10-29)

Jan: "maybe we can have the character shown on the free speech / dialogs - with different expressions." Until now the free
talk ("Pogovor · Talk") was a plain chat with an emoji and a name, and a scene's dialog moved the small sprite in the
picture through body poses only: no face of its own, and the person gone while the panel rose over the picture for a
typed turn. This is Part A (the code, the art, the bridge); Part B, the faces in the authored content, is
`faces-content.md`.

## What

### Twelve faces, apart from the pose

- **The contract.** A face is an expression beside the body's `Pose`: someone can wave and laugh. The names, in this
  order: `neutral` (the default, never written), `happy`, `laugh`, `surprised`, `curious`, `confused`, `thinking`,
  `worried`, `sad`, `cross`, `shy`, `proud` (`game/scene/Face.kt`). Each pose implies one (`Pose.face`: HAPPY and WAVE
  smile, CHEER laughs, SAD is sad, THINK thinks), so every existing mood (the training stage, the school's teacher, the
  villagers' pages) gains its face.
- **The art** (`PeoplePainter.faceOf`), in the sprite's own pixels, the same in a scene and in a portrait (the sprite cut
  at the waist): the eyes (dots, shut in arcs for a laugh, shut in a content "v" for pride, wide 2 × 2 with a glint,
  squinted, up, down, aside), the brows (up, up in the middle, down in the middle, one up, askew; clear of the eyes so they
  never read as "> <"), the mouth by the face with a talking mouth moving inside its shape (an open smile, the laugh
  wider, the round "o" taller, a pout opening), the blush (a laugh, a cross face and a shy one bring it to everyone, the
  shy one deeper and wider), the chin lifted for pride (a neck pixel row under it). Blinking goes on with the eyes open.
  The baby takes the faces it has (a laugh beams, sad and worried cry, thinking sucks its thumb).
- **Emotes, drawn not emoji** (`PeoplePainter.emote`), where they help the read: ♪ laugh, ! surprised and ? curious in
  little white badges (an outlined 1-px glyph fills its own holes: the badge keeps a ? a ?), a violet ? wobbling a pixel
  for confused, … in a thought bubble filling dot by dot for thinking, a sweat drop for worried, the comic cross mark for
  cross, two twinkling sparkles for proud; none for happy, sad and shy (the face reads alone). By the head's far top
  corner (away from the near hand that waves), popping in from a pixel lower; in a portrait kept inside its canvas
  (moved in over the hair's corner on a small one); in a scene drawn in the effects pass after the outlines, unlit and
  never a part of the person to tap.
- **Framing.** The portraits frame the bust as before, or the head alone (`Framing.HEAD`) for small avatars, so a face
  reads at a bubble's size.
- **Pixel-identical without a face.** `FacesGoldenTest` holds every sprite in every pose, in portraits at three sizes by
  day and night and on the scene's sprite still and talking at four moments, to digests taken before the change; the
  culture looks' golden scenes pass unchanged too.

### The free talk

- **A stage over the conversation** (`ui/talk/TalkStage.kt`): the talk partner (`Cast.talker`: the villager's sprite or
  the scenario's person) as an animated bust (132 dp) on their place (`StagePlace`, blurred and dimmed), their name, role
  and friendship beside it. They talk while their line is said (`Speaker.talking`, polled), listen with a hand at the ear
  while the 🎤 records (`TalkInput.onListening`), think while the tutor's answer is awaited (`st.waiting`), and wear the
  face of their line: the tutor's `face`, else the opener smiles, the last line smiles once the scene is over, others rest
  (`Talk.faceOf`).
- **Folds.** With the keyboard up, on a short screen (under 640 dp: a phone turned), with large fonts (the training
  stage's `StageLayout.compact`), or after a tap on it, the stage folds to a live 40 dp avatar in the bar; a tap on that
  brings it back (remembered for the conversation). The goals, Back and "Končaj · End" stay in the bar.
- **The bubbles' faces.** Each of the character's lines has a small still face (34 dp, the head alone) of the face it was
  said with; the "typing" bubble a thinking one.
- **The intro** shows the person on their place, as it did.

### Scene dialogs and every other dialog

- **The content's `face`** on a spoken line, a choice's reply and a turn's puzzled reaction (`DialogLine.face`,
  `DialogReply.face`, read leniently by `FaceSerializer`) is carried into what is said (`Said.face`).
- **The reactions have faces** (`DialogMood`): a wave and a smile at the start, a smile at a right pick, a worried shrug
  at a wrong one, a hand to the chin and confused (or the reaction's own face) at a wrong one they answer or a form typed
  that is none of the choices, listening at the 🎤, at the end without a mistake a cheer and proud (a child laughs), else a
  smile. A line without a face is said with what the moment brings (`DialogMood.lineFace`: the first line smiling, a
  reply to a right line smiling, a reaction confused).
- **The timing** (`ui/stage/Look.kt`, `Expression`): a line's face shows while it is said and holds 3.5 s after it was
  heard, then eases back through its softer face (a laugh through a smile, surprise and confusion through curiosity, pride
  and shyness through a smile) for 0.9 s, then rests. A reaction shows for its time (1.5 to 2.5 s); the newer of the
  reaction and a line's held face wins; listening is attentive.
- **On the screen.** The dialog panel's header shows whom the learner answers (the person, or in a dialog of several
  whoever spoke last, `whoIs`) as a live 52 dp portrait, the head alone, with the face (`SpeakerPortrait`); with the panel
  risen over the picture for a typed turn it keeps them in view. Each of their lines has a still 34 dp face
  (`FaceAvatar`). The picture's sprite wears the same face. One shared set of pieces (`rememberPartnerLook`,
  `SpeakerPortrait`, `FaceAvatar`, `VillagerPortrait(face = …)`) in every host: a scene (and the storyteller's story), a
  keeper's talk, an arrival, a project's step and an age's party (their stages' portraits with the face; the header's
  portrait alive only once the stage folds away for the keyboard, so one portrait animates at a time), a guest's dialog on
  a visit (the person now drawn talking there too), the school's teacher (her pose's face on her stage).

### Bridge and tutor

- `face` on `dialogLine`, `dialogReply` and `puzzled` (`companion/bridge/src/scenes.ts`), a `z.object` key an older
  bridge strips; `faceErrors` in every validator's cross-check (scenes, variants, stories, arrivals, project steps,
  keepers' talks, parties) reports an unknown name with where it is, `neutral`, and a face on a choice or a turn.
- The role-play instructions ask for `face` in the reply's data ("leave it out for neutral"); the dev bridge's tutor
  sends faces. The publish tools' docs and the studio skill say where faces go and how to direct them; the tutor guide is
  regenerated.

### Performance and accessibility

- **Portraits off the main thread.** `Portrait` drew every frame in its Canvas on the main thread. Now an animated one
  asks the portraits' thread (`PortraitThread`: one for every portrait on screen, the newest ask of each, a painter of its
  own, ended after 2 s idle) and the main thread only draws the bitmap handed over (three per portrait, never the one on
  screen drawn into); its first frame is drawn at once so it never shows empty. Stills come from a cache (`Stills`, 192,
  by sprite, pose, face, framing, size, seed and the quarter hour's light), drawn there once: a conversation's bubbles
  are a few faces drawn once each. A still's moment has the eyes open whatever the seed.
- **One portrait animates at a time** per screen in the talk and the dialogs (the stage's or the header's); the bubbles'
  faces are still.
- **TalkBack** hears who and how: "Babica Micka, se smehlja · smiles" (`described`, `face.*` in sl, en, de and it,
  machine-written by Claude: for a native speaker's check, `_note_faces`).

## Decisions

- **The portrait is the sprite.** The faces are drawn once, in the sprite's pixels, so a face looks the same in a scene,
  on a stage and in a bubble, as Jan's scene style asks ("portraits = the sprite"). A finer portrait face would read
  better on the tablet but break the chibi look and double the art.
- **Avatars frame the head alone** at about 26 canvas pixels, so the face is about 60 % of a bubble's avatar instead of
  a third; the emote moves in over the hair's corner there, where the bust has room beside the head.
- **Badges for ! ? and ….** A 1-px glyph outlined fills its own holes and turned to a blob; in a white badge it reads on
  the cream day backdrop, the dark night one and the grass alike.
- **Emotes only with a face of its own.** Pixel identity where no face is set; and the scene's people at work (no face)
  carry no signs.
- **The pose's face by default in the composable portraits** (not in the painter): every mood on the stages gains its
  face, and the painter's legacy path (scenes without faces) stays as it was.
- **Hold 3.5 s, then ease through the softer face for 0.9 s.** Long enough to see after the voice ends, short enough that
  the next line isn't read with an old face; the soft step avoids a hard cut from a laugh to nothing.
- **Defaults when the content says nothing**: a greeting smiles, a reply to a right line smiles, a reaction is confused,
  the rest rest; the end proud, a child laughing. Part B's faces override any of them.
- **The header's portrait** (52 dp, the head alone) replaced the emoji circle rather than adding a third portrait on the
  screen; the sheets with a stage keep their stage and make the header's portrait still while it shows.
- **The talk's stage grows on a tall screen** (from 1,000 dp high: Teo's tablet upright) to 212 dp with a 192 dp bust; in
  landscape and on phones it stays 148 dp with a 132 dp bust, its row (the bust, the name, the role) at most 560 dp wide,
  centred over the place's full width.
- **The talk's fold rule is the training stage's** (keyboard, under 640 dp, fonts at 130 %): one rule for the learner to
  learn; a tap overrides it for the conversation.
- **Easing from line to line.** About half of Part B's lines carry a face (curious on the questions, laugh on the "Ha ha"
  reactions), so the faces change often. Two things keep the portrait from flickering: a line without a face of its own
  after one of the same speaker's goes on with that one's softer face (after a laugh a smile, after a smile at rest:
  `DialogMood.lineFace`), so a face fades over a line or two instead of snapping back; and a new face comes in with a
  blink (`PortraitsPainter`: 0.12 s, the eyes shut as one face gives way to the next, as people do), never popping.
- **A face on a sleeper: the sleep wins.** Nejc's "Ti tudi, {learner}. Burja, tiho bodi!" is `cross` and lies him down
  (`act` pose `sleep`) as it is said. Asleep on the stage, the eyes stay shut and the Zzz rise in the picture, and the
  panel's header shows him dozing (`SceneScreen`: the stage's stance `sleep` over the look), no cross brows over shut eyes;
  his line's bubble keeps its cross face, which is how the words were said as he dozed off. Lying awake in bed (`lie`),
  a face shows as on anyone (`PeoplePainter.lying`), with its emote.

## How to use it

Nothing to turn on. Content: add `"face": "<name>"` to a spoken line, a reply or a puzzled reaction (SCENES.md, "Faces").
The tutor: `face` in a role-play reply's data. QA: `companion/bin/qa --steps faces` (Babica's soup through a wrong and a
right pick, then a free talk with the dev tutor's faces, folded with the keyboard up), and `typed-turn` for the panel risen
with the person in its header.

## Tap counts

Unchanged everywhere: the stage, the header's portrait and the bubbles' faces need no tap. A tap folds the talk's stage,
one on the bar's avatar brings it back.

## What's left

- A native check of `face.*` in sl, de and it (machine-written).
- The intro sheet's portrait wears the pose's face only (its talking, neutral); a face for the request would need the
  intros' content.
- The school's teacher stage still draws on the main thread (`TeacherStage`, as before: its pointing hand follows the
  layout each frame).
- The scene's sprite's emote doesn't pop in (a scene's frame has no age for it); it simply shows.
- **The evening stories** (46, with 1,193 replies) carry no faces yet: their lines have no `who` (the teller says them).
  The panel shows the teller now (its header's portrait and each line's face, with what the moment brings: a smile at
  the start, a smile at a right reply); faces of their own for the stories' lines are a content follow-up (the story
  shape would take `face` as a scene's line does).

## Tests

- **App** (after the rebase onto main with Part B's 5,690 faces): the whole unit suite, 3,324 tests in 426 classes, 0
  failures (2 skipped). New:
  - `FacesGoldenTest` (pixel identity without a face, against the digests taken before).
  - `FacesRenderTest` (9): the contact sheets (build/faces: the bust by day and night, at 64 and 32 px, the head alone,
    the scene's sprite, the talking beats, the faces with the poses, a contact sheet, two close-ups); every face differs
    from every other on nine sprites and in a scene; the resting face is the face without one; the blink (not with the
    eyes shut) and the talking mouth; the emotes inside the canvas at three sizes and both framings; a face in six arts'
    scenes, its emote no one's to tap; closer up; the frame time.
  - `FacesTest` (13): the names and their lenient reading, each pose's face, the emotes and the softer faces, the
    content's faces on a line, a reply and a puzzled one carried into what is said (a form typed included), the moment's
    faces and the easing from line to line, the reactions' faces, the hold and the easing back, the order of the line, the
    reaction and listening, the tutor's face in a role-play, the talk's look, the fold rules and the stage's size (a phone
    turned, large fonts, Teo's tablet both ways), TalkBack's words in every table.
  - `L10nTablesTest` holds with the new keys. The scenes' frame-time test (`SceneDetailTest`) failed once at 25.3 ms
    against 25 ms while the machine's load was over 400; alone it passes.
- **Bridge:** `bunx tsc --noEmit -p .` clean; the whole smoke (`LANI_SMOKE_PORT=8950`, 8953, 8956) after the rebase:
  2,878 checks, all passed, Part B's content validating against the faces' check (a face check of every kind: the enum
  against Face.kt, seven refusals of publish_scene, faces served by the scenes, variants, stories, arrivals, a visit, the
  keepers', steps' and parties' refusals with their paths, a role-play reply's face reaching the app's event).
  `tutor-export --check`: 82 files match.
- **Python:** `python3 -m unittest discover -s tests` (251, OK) and `-s companion/tests` (98, OK, 10 skipped).
- **Emulator QA** (`companion/bin/qa --steps faces`, a synthetic learner, the dev bridge's fake ElevenLabs; 6.5 minutes
  with the emulator's boot; earlier runs `typed-turn` too). Babica's soup by the fire: her portrait in the header at rest
  ("Babica Micka"), after a wrong pick her reaction's own face from the content ("se jezi · is cross", her cross mark by
  her head in the picture too), after the right one a smile; a free talk with Gospod Stanko: the stage with his bust,
  folded to a 126 px avatar in the bar with the keyboard up, the dev tutor's faces (a smile, then curious with its ?);
  Teo's tablet's screen (2560 × 1600 at 320 dpi) landscape and upright. The typed turn with the keyboard up: the panel
  risen, the child's portrait in its header keeping her in view. Screenshots: scratchpad `ux-faces/qa/`.

What QA found and was fixed: a pose's change drew one frame at the portrait clock's start (a jump to a blink); the
talk's newest answer hidden under the correction (the answer takes the typing bubble's place, so the count the list
scrolled on didn't change; and the stage unfolding as the keyboard went down shrank the list); on a tablet upright the
stage stayed phone-sized (now 212 dp with a 192 dp bust from 1,000 dp high).
