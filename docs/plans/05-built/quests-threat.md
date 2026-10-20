# The requests during a threat, and a drill whose task is passed (built 2026-10-20)

Two of Jan's reports from 2026-10-20:

- "I did it for Vinar Marko, but it's still there with the exclamation." The wine request (`marko-kaksno-vino`) passed,
  7/11 against the 60 % mark. The "!" that stayed was a second request at Marko: the tutor's drill
  `tutor-marko-mehki-e-v1`, "Rdeč teran, sveže mleko", `helps: "marko-kaksno-vino"`, published that morning to come
  before the wine request. Its task was passed, and the drill kept its bubble, so the done request looked open.
- "And they overrule the current wolf event?" The reactions round kept the requests up during a threat, while the
  everyday talks waited. That was inconsistent.

## What

1. **The requests fold away during a threat.** While wolves, a bear or a storm are about (`Reactions.quieting`), no
   request is up:
   - no request bubble on the map, no request marker in a scene, no tap on someone in a scene straight into one, no "!"
     on the register's cards;
   - the Scroll's villagers and the villager's card list them under "📜 Kasneje · Later", first of each villager's, with
     "🐺 Po volkovih · After the wolves" ("🐻 Po medvedu · After the bear", "⛈️ Po nevihti · After the storm"; en, de, it
     too). Each is still a tap away, and its intro says why it waits;
   - Home's "Vaščani te čakajo" has the threat first: "🐺 Branimo vas! · Defend the village!" (the culture's call; the
     event's title without a reactions file) in red, with the portrait of the event's leader, a tap its run. Then the
     requests, dim, each with "🐺 Po volkovih";
   - only what shows changes: no try, no state change. When the threat is over they are up again as they were. The
     merchant and a festival hold nothing back.
2. **A drill whose task is passed loses its "!".** It waits in "Later" as practice: "✓ Naloga je opravljena: za vajo ·
   The task is done: for practice". No bubble, no scene marker, not one of the two up (`QuestQueue.UP`). It can still be
   played from "Later" and from the villager's card, its tries count, and passed all the same it is done as any request.
   A drill whose task is still open comes first, as before.
3. **For the tutor:** `get_village`'s `open_quests` has `task_passed: true` on a drill whose task is passed;
   `publish_module` says so when a drill is published for a task passed already ("the app shows this drill as optional
   practice"). The guide's village topic says both, and that the requests wait during a threat.

## How

- `game/QuestQueue.kt`:
  - `of(all, open, today, event)`: with wolves, a bear or a storm, the requests that would be up go to `later` as
    `Why.Danger(kind)`, first of each villager's; `Residents.queue` passes `state.event?.kind`, so the map
    (`TownMarkers.of`), the scenes (`SceneOffers.requestsBy`), the card, the Scroll (`TownOverview.later`) and Home read
    the same queue.
  - `spentFor(d, all)`: the task a tutor's drill helps with, when it is passed: a request of `all` that `d.helps` names
    is done and none is open. Such a drill goes to `later` as `Why.Spent(task)`, after the others, not counted in `UP`.
  - `names(helps, q)`: `helps` names a request by its module, its id, or the module of a tutor's request id of another
    version (`tutor-<module>-v<n>`). `drillFor` and `returning` use it too.
  - `helpedByOpen(q, quests)`: a done request an open drill names. `GameEngine.withTutorQuests` keeps such a tutor
    request when its module is retired or published as a new version, and `Quests.prune` keeps such a local request
    (it drops the villagers' done ones otherwise). So the drill stays practice and doesn't ask again. When the drill is
    done or gone, the old task goes at the next sync or tick.
- How a passed request is recorded: the village's quest with `done = true`. The tutor's stay while their module (that
  version) is published; the villagers' own are pruned at the next tick; `stats.questsDone` only counts. A new version
  of the task, published and open, puts the drill first again.
- `ui/game/QuestTexts.kt` `whyText` (`dangerText`); l10n `quests.afterWolves`, `quests.afterBear`, `quests.afterStorm`,
  `quests.taskDone` in sl, en, de, it.
- `ui/home/HomeLogic.kt` `threatNeed` and `needs` (`VillagerNeed.waits`, `.alarm`, `NeedTarget.Event`);
  `ui/home/VillagersStrip.kt` draws the threat in red and the waiting requests dim.
- The register's "!" (`VillagersScreen`) reads the queue's requests up.
- The bridge: `src/game.ts` `taskPassed` (the app's rule) and `openQuest`'s `task_passed`; `src/features/modules.ts`
  publish note; `src/features/game.ts` instructions and get_village's description (companion/tutor regenerated with
  `tutor-export`).

## How to use it

- Nothing to turn on. The tutor can read `task_passed` before sending Jan to a drill.
- QA: `companion/bin/qa --no-town --steps quests-threat` on a synthetic learner (`LANI_DEV_DATA`, `LANI_ENV_FILE=/dev/null`;
  `LANI_QA_QUESTS_SHOTS` copies its screenshots). The learner needs Vinar Marko with the wine request done and the drill
  open, as made by a script like the one used for this round (the reactions' town of the whole cast, the three Marko
  modules in `app/modules/`, the quests in `app/game.json`).

## Tests

- `QuestQueueTest`: a spent drill under "Later" with `Why.Spent`, no bubble, not counted in the two up (his two other
  requests both up, one bubble counting 2); passed after all it is done, failed it counts a try and stays practice; a drill
  whose task is open comes first and parks it; a new version of the task puts the drill first again; the task by its
  request id (this version and another); an unknown task leaves the drill an ordinary request; the module retired after
  the pass keeps the done request while the drill is open, and drops it with the drill; a passed local request kept
  while a drill names it.
- `ReactionTalksTest`: the map shows only the event; the requests fold away for wolves, a bear and a storm (no bubble, no
  scene offer, listed in "Later" with the danger's text, the Scroll's `later`, nothing in Today, the quests unchanged)
  and come back as they were after a won or a lost fight; the merchant and a feast day keep them up; Home's strip with
  the threat first ("Branimo vas! · Defend the village!", the smith's portrait) and the requests waiting.
- The Android unit tests: 2,879, 0 failures (TownLogicTest's three checks of the overview during wolves updated: the
  event only, the requests in "Later"). Python: `tests` 249 OK, `companion/tests` 98 OK (10 skipped).
- Bridge: `tsc --noEmit` clean; the smoke 2,566 checks, all passed (ports 19800/19802/19804).
- Bridge smoke: `get_village` `task_passed` (by module and by request id), not on a drill whose task is open; the
  publish note for a task passed, none while it is open.
- The emulator (the debug build; the dev bridge on 8791 on a synthetic learner, Ana with the Primorska town of the whole
  cast, Marko's wine request done 7/11, the drill and a new request of his, Tone's own, `LANI_DEV_DATA`,
  `LANI_ENV_FILE=/dev/null`; `companion/bin/qa --no-build --no-town --steps quests-threat`, twice): setup, quests-threat
  and the crash check passed. By day 3 request bubbles (Tone 2, Mojca 2, Marko 1: the drill isn't one); with the wolves
  none for 80 s, the event's only; the Scroll's "Kasneje · Later" with "🐺 Po volkovih" on each that was up; Home's
  strip red "Branimo vas! · Defend the village!" first, Tone and Marko dim; the same 3 bubbles after; Marko's card with
  "Trgatev" up and "Rdeč teran, sveže mleko" under Later, "✓ Naloga je opravljena: za vajo".

## What's left

- A drill whose task was retired before this round, without a pass recorded, stays an ordinary request: there is no
  trace of a pass to go by.
- During a threat the requests' intros say why they wait, but a run of one is not refused (the learner's work).
- The new strings are machine-written, not reviewed by a native speaker.
