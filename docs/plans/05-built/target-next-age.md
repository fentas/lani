# A target raised later starts with the next age (built 2026-10-31)

Teo, a child of about 7 learning Italian, aimed at A2: setup set the target one level above his A1. His hamlet needed 45
words for Vas, and he had them. Jan wants him aiming at B1, but not at once: "lets do b1 with the next age then; to do it
now would be sad for him - if he has now to add 200 more words; rather next age more." And for everyone new: "b2 should
be default aim."

## What

- **A pending target.** The learner profile keeps a raise that starts with the village's next age:

  ```json
  "learner": {
    "target_level": "A2",
    "target_level_next": { "level": "B1", "from_age": "VAS", "set": "2026-10-31" }
  }
  ```

  The step the village is on keeps the words of the target it began with (Zaselek → Vas: A2's 45); from `from_age` on the
  new target counts (Vas → Trg: B1's 500, where A2 asked 150). `from_age` is the age after the village's age when the
  raise was set.
- **Applied once, at the age-up, by whoever sees it.** The bridge owns learner-profile.json: when the village is saved at
  `from_age` or later (`PUT /game`: the app's save, on the node or on the phone's own bridge), it applies the raise,
  `target_level` = the level, `target_level_since` = the day, the pending one removed. A save before that age changes
  nothing. The app counts with the raise before the bridge has the save (offline too): the same rule, in
  `game/Aims.kt`. So whoever reaches the age, and whichever applies it first, the target is the same, and applying it
  again finds nothing to do.
- **An age once reached is never taken back.** The raise never touches the age; it only sets what the next step needs.
- **A lower target applies at once.** It never makes the current step harder. The current target asked again takes a
  waiting raise back. A raise at Mesto, the last age, applies at once (no step is left to keep). A raise can also be
  applied at once on purpose: `lani-profile set-target <id> B1` without `--from-next-age`.
- **Where it shows.** While a raise waits: the ages' sheet (the HUD's ring) under the words' step, "🎯 Od naslednje dobe:
  B1 · From the next age: B1" and the age after with its words, "🏛️ Trg: 500 besed · Market town: 500 words"; the progress
  screen under the levels, the same pill; the settings' "🎯 Cilj · Aim" under the aim now. The progress screen's target
  badge is the target the village's step counts with.
- **Setting it.** `lani-profile set-target <id> <level> [--from-next-age]` on the node; the tutor's `set_target_level`
  (for a raise it always sets the pending target). Both say what the current and the next step then need. In the app:
  "⚙️ Nastavitve · Settings → 🎯 Cilj · Aim" and the setup's aim (setup-refresh), both `POST /learner/target`.
- **"🎯 Cilj · Aim" in the settings** (after the languages; a parent's on a child's device, the hold): "Zdaj: A1 → A2 ·
  Now: A1 → A2", what the aim means (the setup's line: the town at the aim's words), a raise waiting under it as the ages'
  sheet says it, the levels above the learner's as chips (the setup's choices, the aim the learner will have marked) and
  the rule: "Višji cilj velja od naslednje dobe vasi: korak, na katerem si zdaj, ostane, kot je. Nižji cilj velja takoj. ·
  A higher aim starts with the village's next age: the step you're on now stays as it is. A lower aim applies at once." A
  chip sends the aim; the banner says what the node did ("🎯 Cilj C1 velja od naslednje dobe · Aim C1 from the next age",
  "🎯 Cilj je zdaj A2 · Your aim is A2 now") and the learner's data is read anew. The settings' row says the aim and a
  raise waiting.
- **A new learner aims at B2**, or at the level above theirs when they start at B2 or higher (C2 stays C2), instead of one
  level above. lani-setup, `lani-profile add` (and `--target-level` to choose another) and the phone's setup (the hosted
  answers' `target_level`) set it at once: there is no village yet. A child's other defaults (15 minutes a day) stay.
  Existing learners are untouched.

## How

### The bridge (`companion/bridge`)

- `src/aim.ts`: the rule, pure (`aimAt`, `pendingOf`, `applyDue`, `setTarget`, `describeAim`, `explainChange`,
  `defaultTarget`, `ageWords`, which mirrors the app's `Catalog.ageWords`) and the profile's file helpers
  (`applyPendingTarget`, `setTargetIn`, `villageAge`: read just before, written beside the file and renamed over it).
- `features/game.ts`: `PUT /game` applies a due raise after the save (a failure is logged; the next save tries again).
  `get_village` has `aim`: the target the step counts with, the next age and its words, and `from_next_age` (the level, the
  age it starts with, the age after and its words).
- `features/level.ts`: the tool `set_target_level {level}` (a `set_` tool: refused while the lease isn't this side's) and
  the guide's paragraph (topic "level-up", "Level-ups and the target"): how a target change works, never by editing the
  profile, a raise from the next age, the app's setup and settings by the same rule. `companion/tutor` regenerated with
  tutor-export. setup-refresh's `GET`/`POST /learner/target` (the app's aim) now keeps the aim by `setTargetIn` with
  `fromNextAge` = the learner has a village (`app/game.json`): at setup at once, later a raise from the next age; it still
  refuses a level at or below the learner's, and the lease guards it. Both answer `next` (the raise waiting, `{level,
  from_age}`, or null); `POST` also `change` (`now`, `next_age`, `unchanged` with `already`).
- `src/learners.ts` `initLearnerData`: the target is the learner's choice when above their level (setup-refresh's
  `aimOf`), else `defaultTarget(level)`. `src/phone/entry.ts` `initLearner` takes the hosted answers' `target_level`.
- `src/cli/profile.ts`: `set-target` and `add --target-level` (above the level, as the app).
- `.claude/hooks/session-start.py`: "🎯 Level: A1 → A2 (B1 from the village's age Vas on)".

### The app (`companion/android`)

- `data/TargetNext.kt`: the profile's `target_level_next`, read as the bridge reads it (a level and an age, else none);
  `Dashboard.targetNext`, `ProgressStats.targetNext`. `target_level` is read as a level (B2 when it names none or a
  template's placeholder).
- `game/Aims.kt`: `at(target, next, age)`, `waiting`, `then`, `note`, `thenLine`. `ContentController.targetAt(age)` feeds
  `GameController.advance`, the ages' sheet's check (`rememberAdvanceCheck`), the school's.
- `ui/game/VillageSheets.kt` `AgeSheet(aim = …)`: the note under the words' step. `ui/progress/ProgressScreen.kt`: the
  badge and the pill.
- `ui/settings/AimSettings.kt` (`AimShown`, pure: the aim at the village's age, the chip marked, the row's line, the
  banner) and `SettingsSection.AIM`; `data/AimApi` reads `next` and `change`; `AppViewModel.keepAim` (setup-refresh's
  `setAim` is it now) sends the aim and reads everything anew.
- The setup's "🎯 Cilj · Aim" is setup-refresh's (`app/Aim`, `HostedAnswers.targetLevel` → `target_level`, B2
  preselected); a raise during setup is at once (no village yet).
- Strings `aim.*` in sl, en, de and it: **written by Claude, for a native speaker's check** (`_note_aim` in each table):
  - `aim.fromNextAge`: sl "Od naslednje dobe: {level}", en "From the next age: {level}", de "Ab dem nächsten Zeitalter:
    {level}", it "Dalla prossima età: {level}" ("età" as the app's "La prossima età" has it)
  - `aim.ageWords`: sl "{age}: {n} besed" (ICU plural: beseda, besedi, besede, besed), en "… words", de "… Wörter", it
    "… parole"
  - `aim.now`: sl "Zdaj: {level} → {target}", en "Now: …", de "Jetzt: …", it "Ora: …"
  - `aim.rule`: sl "Višji cilj velja od naslednje dobe vasi: korak, na katerem si zdaj, ostane, kot je. Nižji cilj velja
    takoj.", en "A higher aim starts with the village's next age: the step you're on now stays as it is. A lower aim
    applies at once.", de "Ein höheres Ziel gilt ab dem nächsten Zeitalter des Dorfes: Der Schritt, bei dem du gerade
    bist, bleibt, wie er ist. Ein niedrigeres Ziel gilt sofort.", it "Un obiettivo più alto vale dalla prossima età del
    villaggio: il passo a cui sei adesso resta com'è. Un obiettivo più basso vale subito."
  - `aim.keptNext`: sl "Cilj {level} velja od naslednje dobe", en "Aim {level} from the next age", de "Ziel {level} ab dem
    nächsten Zeitalter", it "Obiettivo {level} dalla prossima età"
  - `aim.keptNow`: sl "Cilj je zdaj {level}", en "Your aim is {level} now", de "Dein Ziel ist jetzt {level}", it "Il tuo
    obiettivo ora è {level}"
  - `aim.notKept`: sl "Cilja ni bilo mogoče shraniti", en "The aim couldn't be kept", de "Das Ziel konnte nicht gespeichert
    werden", it "Non è stato possibile salvare l'obiettivo"
  The section's title is setup-refresh's `setup.aim` ("Cilj · Aim"), its explanation `setup.aimWhat`.

## How to use it

Teo's B1 from the next age (the lead does it after the merge, with a backup of his data first):

```bash
companion/bin/lani-profile set-target teo B1 --from-next-age
```

With his village at Zaselek and his target A2 it says:

```
teo (Teo): The target stays A2 for the step the village is on and becomes B1 with the next age. The village is Zaselek:
the step to Vas keeps 45 words; from Vas on, Trg needs 500 words (it needed 150). The app says "From the next age: B1" on
the age card and the progress screen.
  learner-profile.json: target_level stays A2; target_level_next {"level":"B1","from_age":"VAS"}.
  The app shows it the next time it reads the learner's data (back on Home, or opened again).
```

If his village is already at Vas when it is run, the raise starts with Trg (`from_age` TRG) and the step to Trg keeps A2's
150. Take a raise back: `lani-profile set-target teo A2` (the current target again). Apply at once: without
`--from-next-age`. While the phone holds the data (the lease) it is refused, as every write of the node's.

The tutor: "Teo should aim at B1" → `set_target_level {level: "B1"}`; it answers the same lines. In the app (Teo's
tablet, a parent's hold): ⚙️ Nastavitve · Settings → 🎯 Cilj · Aim → "B1": the banner says "Cilj B1 velja od naslednje
dobe · Aim B1 from the next age".

## Decisions

- **A pending target with its age, not a bare level.** With `from_age` in the profile, the app knows whether the raise
  counts without knowing when the bridge saw what: the rule is a function of the profile and the village's age alone, so
  the app, the phone's bridge and the node agree, and a lost write (update-db.py rewriting the profile at the same
  moment) is applied again at the next save.
- **A lower target applies at once**, even asked "from the next age": it never makes the step harder, and a learner who
  needs it lower needs it now. The CLI says so.
- **The tutor's tool and the app's settings always raise from the next age** (once there is a village). A raise at once
  is the node's (`lani-profile` without the flag).
- **The app's aim keeps setup-refresh's limits**: only levels above the learner's are offered and accepted (C2 at C2), so
  a lower aim is one still above the level.
- **Mesto applies at once**: there is no next age to wait for.
- **One edge**: the age a raise starts with is the village's age as the bridge knows it when the raise is set. A village
  that reached that age on a phone offline, its save not on the bridge yet, counts the raise as soon as the app reads the
  profile, for the step it is on then.

## Tap counts

- Seeing what the next age needs and that the target rises after it: the ring, 1 tap (the ages' sheet); or Home →
  "📊 Napredek", 1 tap (the pill). Before: the raise couldn't be scheduled, and a raised target showed nowhere but in
  the numbers.
- Setting it: one command on the node, or one sentence to the tutor; in the app Home → ⚙️ → 🎯 Cilj · Aim → a level, 3
  taps (a child's: the parent's hold before the level). Before: an edit of learner-profile.json by hand, effective at
  once; the app had no place for the aim after setup.

## Tests

- Bridge smoke `test/smoke/aim.ts` (32 checks): the shared cases of `test/fixtures/target-next-age.json`; the rules (a
  raise waits, applied at its age exactly once and never before, a lower one at once, the current one takes a raise
  back, at once without the flag and at Mesto); `set_target_level`, `PUT /game` at Zaselek (nothing) and at Vas (B1, once),
  `get_village`'s `aim`; a new learner's default; `lani-profile add` and `set-target` on a synthetic child at Zaselek,
  the age-up then asking B1's 500 for Trg; refused under the phone's lease. The tool counts in core and service by one.
- Smoke (aim) also: `POST /learner/target` with a village (a raise waits, asked again already, the current aim takes it
  back, a lower aim at once) and without one (at once); `--target-level` at or below the level refused.
- App: `AimsTest` (7: the step keeps its words until the age-up, the raise exactly then, never before, the bridge applying
  it changes nothing for the app, a lower target at once, a pending target that can't be read, the labels in sl · en and
  it · de), `AimsParityTest` (3: the shared cases, so the app and the bridge agree), `AimSettingsTest` (5: the aim and the
  raise waiting at the hamlet and at Vas, the chip marked, the row's line, the banner in sl · en and it · de, the node's
  answer read).
- Python: `test_setup_wizard` (a fresh learner aims at B2).
- Emulator QA: `companion/bin/qa-slot --steps target-next-age` on a synthetic child of Italian (see below).

## QA

`LANI_DEV_DATA=<make-learner.ts <dir> --target it --base de --name Teo, target_level A2> LANI_CULTURE=friuli LANI_CHILD=1
LANI_QA_PAIR=sl-en companion/bin/qa-slot --steps target-next-age` (passed on slot 1, 2026-10-31). The village made a
hamlet (QA's hook "hearths:zaselek"); the tutor's `set_target_level B1` through the dev bridge (`POST
/tutor/tools/set_target_level`) answered "The target stays A2 for the step the village is on and becomes B1 with the next
age. The village is Zaselek: the step to Vas keeps 45 words; from Vas on, Trg needs 500 words (it needed 150)." Shots
(`tna-*.png`):

1. `tna-ages-before`: the ages' sheet at Zaselek, "Besede · Words 8 / 45" and under it "🎯 Od naslednje dobe: B1 · From the
   next age: B1", "🏛️ Trg: 500 besed · Market town: 500 words".
2. `tna-progress-before`: "A1 → A2", the pill "🎯 Od naslednje dobe: B1 · From the next age: B1".
3. `tna-settings-before`: "🎯 Cilj · Aim", "Zdaj: A1 → A2 · Now: A1 → A2", the raise waiting, B1 marked, the rule.
4. `tna-ageup-card`: the age-up's card (QA's hook "ageup:vas"); the dev bridge's profile then `target_level` B1,
   `target_level_since` the day, nothing waiting.
5. `tna-ages-after`: Trg's step, "Besede · Words 8 / 500", no note.
6. `tna-progress-after`: "A1 → B1", no pill.
7. `tna-settings-after`, `tna-settings-raise` ("C1" chosen: "🎯 Od naslednje dobe: C1", "🏰 Mesto: 3500 besed · Town: 3500
   words", the banner "🎯 Cilj C1 velja od naslednje dobe · Aim C1 from the next age"; the profile's `target_level_next`
   {C1, TRG}), `tna-settings-back` ("B1" again: the raise taken back).

The same screens in Teo's pair (it · de) aren't shot: QA's hook "pair:it-de" didn't carry to the open sheet; AimsTest and
AimSettingsTest check those labels.

## What's left

- ~~The app's own chat tutor (the in-app tutor's fixed tool list, phase A) has no `set_target_level`~~ Done (polish-app,
  2026-11-01): `PhaseA.TOOLS` has it after `set_rhythm`, a write through the node's `/tutor/tools` (the same rule and the
  same lease guard); the shadow's dry run says the rule. The prefix changed: a thread made before starts anew.
- `update-db.py`'s treasure-map level-up raises `current_level` but never the target: a learner who passes their target
  keeps it (the village's last age is reached with it). Whether the target should then rise by itself is Jan's call.
- The strings above, for a native speaker's check.
