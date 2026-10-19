# Built: the hosted mode, "☁️ Lani na tem telefonu · Lani on this phone"

*Plan 5, phase A, the app's side (2026-10-19, branch `hosted`). What [plan 5](../05-in-app-tutor.md) asks in "The modes
side by side", "Data" (export and import), "Background work on Android" and "What the hosted mode leaves out at first",
as built on [the bridge on the phone](phone-runtime.md), [the relay](relay.md) and [git sync](git-sync.md).*

The hosted mode runs Lani on the phone alone: no node, no Tailscale, no Claude Code. The bridge's TypeScript runs in
the app's process (QuickJS, `PhoneBridge`), the learner's data lives in `<filesDir>/lani/data` with a node's data
repository's layout, and the app talks to that bridge exactly as to a node. It is opt-in: the first launch offers it next
to pairing, and the settings switch to it and back. A paired phone never runs it, and self-hosted works as before.

## What is built

| Part | Where | What |
|---|---|---|
| The setup's choice | `ui/SetupAndPermission.kt` | "☁️ Lani na tem telefonu · Lani on this phone" above today's pairing, which is now headed "🔗 Poveži svojega učitelja · Connect my own tutor" |
| The onboarding | `ui/hosted/HostedScreens.kt` `HostedSetup` | A fresh start (the questions below), an export imported, or the data already on the phone; then the tutor's step |
| The new learner's data | `companion/bridge/src/phone/entry.ts` `initLearner`, `PhoneBridge.initLearner` | The six databases from `data-examples/`, exactly as `lani-setup` makes them (`learners.ts initLearnerData`), before the bridge boots |
| The in-process transport | `phone/PhoneTransport.kt`, installed in `data/Http.kt` | An OkHttp interceptor for `http://phone.lani.invalid`: every request answered by `PhoneBridge.handle`, a stream as a streaming body |
| The runtime | `hosted/HostedRuntime.kt` | The bridge for the whole process (the screens and the background jobs): boot, stop, restart, the lease's environment |
| The controller | `hosted/HostedController.kt` | The splash's boot, the onboarding, the export and import, the relay setting, the way back to a node |
| Export and import | `hosted/DataExport.kt`; `.claude/hooks/lani_export.py`, `companion/bin/lani-export`, `lani-import` | One zip, `lani.export/v1`, written and read the same on the phone and on a node |
| The rhythm's wake-up | `hosted/HostedWork.kt` | One-time WorkManager work at the next morning, evening or weekly moment |
| The settings | `ui/home/ConnectionSheet.kt`, `ui/hosted/HostedScreens.kt` `HostedSection`, `MoveToPhoneSection` | Export, import, the relay, "🖥️ Na svoj računalnik · To my own computer"; on a node "☁️ Preklopi na ta telefon · Switch to this phone" |
| QA | `companion/bin/qa --steps hosted` | A fresh install with no node at all, to the export |

## The onboarding

What `/lani-setup` and `lani-setup` ask a new learner (`companion/bridge/src/cli/setup.ts`):

- the name;
- how the village speaks to them: "kot ženska (lačna)" or "kot moški (lačen)" (the profile's `gender`; without a choice
  the tutor asks later);
- the language learned: slovenščina (the default), English, italiano, Deutsch, the languages the culture packs speak
  (a language other than Slovene says its village has less content so far);
- the language explanations are in (the other three);
- the level now (A1 to C2, A1 for a beginner);
- minutes a day (10, 15, 20, 30, 45, 60).

"🏡 Ustanovi mojo vas · Found my village" sends them to `PhoneBridge.initLearner` (JSON `{name, gender?, target, base,
level, goal}`). The bundle is loaded but the bridge not built yet: its stores read the profile when they are built (the
dictionary's base language among them), so the data comes first and the boot after. The village's culture pack is the
one of the language learned (`cultures.ts cultureFor`, as lani-setup picks it); on every boot without `LANI_CULTURE`, the
phone's bridge picks it from the profile the same way, so an imported Italian learner plays friuli. A directory that
holds a learner already is never overwritten.

The other ways in:

- **"📥 Uvozi izvoz · Import an export":** the system's file picker, then the import below.
- **"▶ Nadaljuj kot Ana · Go on as Ana":** shown when the phone holds a learner's data already (an earlier hosted start,
  or what git pulled), it boots over that.

**Then the tutor.** A step says what the tutor on this phone is: an AI on the learner's own key (Anthropic, OpenAI or a
ChatGPT plan), paid to the provider by use, and that the village, the exercises and the words work without it. "🧑‍🏫
Nastavi učitelja · Set up the tutor" goes Home and opens the live tutor's own section ("🧑‍🏫 Učitelj · Tutor",
`ui/tutor/TutorSettings`, in a sheet of its own: `ui/hosted/TutorSheet`); "🏡 V mojo vas · To my village" goes Home, and
the same section is in the connection sheet later.

- **The tutor in the app is the only one on the phone:** the section shows no "Claude Code on my node" choice there,
  and the tutor turns on by itself as soon as a credential is set (the view model watches `hosted.active`, the
  live tutor's credential and its `on`, and calls `LiveTutor.setOn(true)`: the session read, the channel attached).
- **Its bridge is the phone's own:** `TutorRuntime.live` gives a hosted connection's `LiveBridge` a `PhoneLink` client
  (`/tutor/session`, attach, the inbox's long poll, the tools: in-process), booting the phone's bridge first in a
  process the screens didn't start (`TutorWork`).
- **Until a credential is set** the chat and a role-play say "🧑‍🏫 Na tem telefonu še ni učitelja: sporočila počakajo
  nanj · There's no tutor on this phone yet: messages wait for one". What is written waits in the bridge's inbox.

Then Home, and the village is founded as with a node: the bridge has no `app/game.json` (`GET /game` is a 404), so the
founding screen comes.

## The in-process transport

The hosted mode's connection is `BridgeConfig("http://phone.lani.invalid", <token>)`, stored as a node's is. The token
is the phone bridge's own (`PhoneBridge.Config.token`), made at the onboarding. So every part of the app's client works
unchanged: `Bridge`, the outbox, `EventStream`, `ClipsApi`, `FamilyApi`, the chat's photos, the background check.

- **The interceptor** (`PhoneTransport`) is in the shared OkHttp client (`data/Http.kt`). A request to the reserved
  host goes to `PhoneBridge.handle` with its method, path, headers (the body's content type added: an application
  interceptor sees the request before OkHttp puts it there) and body. Every other request goes out as before. `.invalid`
  never resolves (RFC 2606), so a request that missed the interceptor fails at once and reaches no network.
- **Streams** (`/events`): the answer's `Flow<ByteArray>` becomes a streaming `ResponseBody`. The parts are collected
  into a queue and read with a 250 ms wait, so a cancelled call (the event source let go) ends the read. Closing the
  body stops the collection, which ends the stream on the bridge's side, as a closed connection does. okhttp-sse reads
  it as it reads a node's chunked answer (`text/event-stream`).
- **No bridge, or a failure:** an `IOException`. The app's callers treat it as a node out of reach. Anything else is
  wrapped, because OkHttp's asynchronous calls treat a non-IOException as a crash.
- **Writes:** after a mutating request (POST, PUT, PATCH, DELETE) the bridge took (2xx, 3xx), `HostedRuntime.onWrite`
  runs, which calls `DataSync.touched()`.
- **Not used in this mode:** pairing (`/pair`), `/app/latest` and `/app/apk`. The update check asks `/app/latest`,
  which has no release on the phone, so no banner shows. Updates come from Play or the GitHub build's self-update.

### The voice paths and the "can't connect" places

| Where | In the hosted mode |
|---|---|
| `/voice/say` (a line's clip) | 204: `ClipsApi.say` gives null, and the speaker uses the phone's text-to-speech. Quiet. |
| `/voice/index`, `/voice/prepare` | An empty index; `url: null`. No clip is fetched. |
| The day's clips ahead, the offline voice (Piper) | Off (`OfflineAudioController.onPhone`): nothing planned or offered. The connection sheet hides "🔊 Zvok brez povezave". |
| Speech recognition (`/stt`) | `Stt.connect` doesn't ask: `available = false`, so the phone's recognizer is used. No "node listens" option shows. |
| The status dot and the connection sheet | "☁️ Lani na tem telefonu" in place of "tutor online" or a URL. No pairing, no "Scan the QR code". |
| The chat | Its note while no tutor is set up (above). The outbox delivers in-process, so nothing waits for a network. |
| Friends (towns) | Without a relay: "🛰️ Na tem telefonu se vasi povezujejo prek posrednika …", and no Invite or Accept. |
| The family page | Through the relay only. Without one it isn't reachable; the family screen's own routes work locally. |
| Re-recording a clip (a long press on 🔊) | The bridge answers "no voices on this device". |

## Lifecycle

- **At the app's start** with the hosted connection, the view model calls `HostedController.boot` before `connect`. The
  splash ("☁️ Lani na tem telefonu", a spinner; `Screen.Loading`) shows meanwhile. A boot that fails shows its reason
  and "Poskusi znova · Retry".
- **The boot** (`HostedRuntime.ensure`, on `Dispatchers.IO`): `AppBridge.create` unpacks the content when the app's
  version changed, then `PhoneBridge.start` loads the bundle (from its bytecode after the first time), builds the
  bridge and starts the features (the rhythm's timers, the town's relay client). It is once per process. A background
  job's process boots it the same way, on the first request to the phone's address: the background check
  (`BackgroundCheck` calls `HostedRuntime.init`), the rhythm's job.
- **While the process lives** the features' timers run (the rhythm every minute, the events' flush, the relay's poll).
- **The rhythm's wake-up** (`HostedWork`): one-time WorkManager work at the next moment of `<data>/app/rhythm.json`
  (morning 07:30, evening 20:30, Sunday 19:00 by default, as `rhythm.ts`; the time plus 30 s). It boots the bridge,
  waits up to 90 s until the bridge's rhythm has marked what is due as run (the channel's `daily_morning`,
  `daily_evening`, `daily_weekly`: `HostedWork.rhythmNow`), lets the live tutor answer it at once
  (`LiveTutor.backgroundCheck`), and schedules the next moment. It is scheduled at every `connect` in the hosted mode,
  and cancelled on the way back to a node.
- **The live tutor's check** (`TutorWork`, every ~15 minutes while it is on) runs `HostedWork.rhythmNow` first in the
  hosted mode, so a routine the process missed is sent before the check reads the inbox.
- **A clean stop** (`HostedRuntime.stop`: `PhoneBridge.stop` keeps the events and closes the streams and the runtime):
  before an import takes the data's place, on a restart (a pull, a new relay), and when the learner switches to a node.
  Android ends the process without notice otherwise; the bridge's files are written as they change (the events within
  2 s), so nothing waits for a stop.

## Git and the lease

The phone's bridge runs as the lease's phone: `LANI_SIDE=phone` and `LANI_LEASE_DEVICE=<the sync's device id>`
(`HostedRuntime.phoneEnvironment`, read from `<filesDir>/lani/sync/settings.json`, so a background job's boot has it
too). With no lease file every side writes, as before. While the node holds the lease, the phone's write routes answer
423 and the outbox keeps the writes queued.

- **`DataSync.touched()`** runs after every mutating request through the transport and after each write of the db
  port (`host.dataWritten`, the db port's `afterWrite`, where a node's Python commits the data repository). The sync
  pushes 20 s after the last write, at most 2 minutes after the first, when git is connected and the phone holds the
  lease.
- **`DataSync.onPulled`:** a pull that brought files boots the bridge again (`HostedRuntime.restart`, some stores read
  their files when they are built), and the app reads everything again.
- **The Data sheet** opens from the connection sheet ("💾 Podatki · Data"), in both modes.
- In a process a background job started without the screens, a write doesn't reach `touched()` (there is no
  `DataSync` there). The sync finds it when the app comes to the front (a pull, then the local changes every two
  minutes).

## The relay

One setting for the backup and the town: `DataSettings.relay` (`DataSync.setRelay`), the connection sheet's "🛰️
Posrednik (relay) · Relay" in the hosted mode, over the build's `BuildConfig.RELAY_URL` (Gradle `lani.relayUrl` or
`LANI_RELAY_URL`, empty by default).

- **With a relay,** the bridge gets `LANI_RELAY_URL=<url>` and `LANI_TOWN_URL=relay`: its town publishes and polls
  its mailbox through the relay, and the family page goes through it (relay.md). A changed setting boots the bridge again.
- **Without one,** towns aren't available in this mode. Friends says so ("🛰️ Na tem telefonu se vasi povezujejo prek
  posrednika (relay), ki ga v nastavitvah še ni …") and offers no Invite or Accept, and the connection sheet says
  "Brez posrednika: obiski drugih vasi in družinska stran na tem telefonu še niso na voljo".

## Export and import

The format, `lani.export/v1`, is the same on both sides: the app's `DataExport` and the node's
`.claude/hooks/lani_export.py`.

- **A zip** of the data directory's files at their paths, and a manifest at the root, `lani-export.json`:
  ```json
  {
    "format": "lani.export/v1",
    "created": "2026-10-19T08:15:00Z",
    "from": {"side": "phone", "mode": "hosted", "name": "Pixel 8", "app": "si.lanisce.lani 0.2.1300 (2300)"},
    "learner": {"name": "Ana", "target": "sl", "base": "en"},
    "schemas": {"app/game.json": 1, "app/towns.json": "lani.towns/v1"},
    "files": [{"path": "learner-profile.json", "size": 2048, "sha256": "…"}],
    "secrets": []
  }
  ```
  A node's `from` is `{"side": "node", "mode": "self-hosted", "name": <host>, "profile": <id>, "version": <git describe>}`.
  `schemas`: every JSON file whose top-level `schema` is a text, and the village's `state.schema`.
- **What it holds:** what a commit of the data repository takes. On the phone that is the sync's filter (`DataFilter`:
  the shared `data-repo.gitignore`, then the directory's own `.gitignore`); on a node it is git's list, or the same
  rules. It never holds the secrets, the caches (voice clips, APKs), the runtime files, the recent answers, the
  developer's data or the chat's photos. `lani export --with-secrets` adds the town's key (`app/town-key.pem`, the
  node's bridge key when it has no town key) and the family token, named in `secrets`. The app exports without them.
- **This side's own files:** `.gitignore` and `app/lease.json` are in an export, but an import never takes them. The
  other side's lease would say the other side writes, and its `.gitignore` could let this side's secrets into a commit.
- **The import refuses** a file that isn't a zip or has no manifest; a format other than `lani.export/v1` (a `v2`:
  "made by a newer Lani: update this app first"); a path that is absolute, has `..`, a backslash, a NUL or a `.git` part;
  an entry the manifest doesn't list, or a listed file that isn't there; a size or SHA-256 that differs; more than
  50,000 files or 1 GB; a file the rules keep out, unless it is one of the two secrets the manifest names; no learner
  profile.
- **The check** (the app): validate-data on every JSON file and read-db on a copy of the databases, both through the db
  port (`PhoneBridge.db`, `__laniPhone.db`: the TypeScript of the node's Python). read-db runs on a copy because its
  migrations may write, and what takes the data's place is the export as it came. During the onboarding the bridge
  is only loaded for this; from the settings it is the running bridge.
- **The replace** (the app, after asking "📥 Zamenjam podatke na tem telefonu?" in the settings): the bridge stops, the
  data goes to `<filesDir>/lani/data.before-import-<time>` (the newest three are kept), the checked files take its
  place, and the phone's own local files the export doesn't carry come along: its town key, its tokens, its lease and
  `.gitignore`. Then the bridge boots again and the app reads everything again.
- **On a node** (`lani import <file>`): the same checks with the Python (read-db.py on a copy), the lease (another side
  holding the data refuses), what changes and a confirmation, what was there kept (a commit in a data repository, else
  a copy beside it), the files replaced, then a commit. `--dry-run` stops after saying what changes, and
  `lani import --verify <file>` only checks.

### Moving between the modes

- **Self-hosted → hosted:** on the node `lani export`, the file to the phone (Taildrop, a cable, a cloud drive), then in
  the app "☁️ Lani na tem telefonu" → "📥 Uvozi izvoz" (a fresh install), or, from a paired phone, the connection
  sheet's "☁️ Preklopi na ta telefon · Switch to this phone" → the same steps. With git, `lani handover` on the node and
  "📥 Prevzemi podatke · Take over the data" in 💾 Podatki · Data, then "▶ Nadaljuj kot …" in the hosted steps.
- **Hosted → self-hosted:** the connection sheet's "📤 Izvozi moje podatke · Export my data" (the share sheet), then
  `lani import <file>` on the node; or with git, 💾 Podatki · Data → "🖥️ Predaj računalniku" and `lani handover
  --take`. Then "🖥️ Na svoj računalnik · To my own computer": the phone's bridge stops, the hosted connection goes, and
  setup pairs with the node. The phone's data stays for coming back.

Both switches say how the data moves first, and nothing is switched silently. Switching to the phone also says how
many changes still wait for the computer.

## The numbers

The x86_64 emulator (Android 16, API 36) on a desktop CPU, 2026-10-19, the qa build (R8, as a release) unless said.
The host was busy with other builds (load 18-40), so these are upper bounds; an arm64 phone is likely 2-4× slower for
QuickJS.

| | |
|---|---|
| A fresh install's onboarding: the content unpacked (549 files, 11.7 MB) | 80-102 ms |
| … the bundle compiled (then kept as bytecode) | 349-373 ms |
| … the bridge's boot (no village yet) | 145-175 ms |
| … "Ustanovi mojo vas" to the bridge up (the learner's data made too) | 0.6-0.7 s |
| … Home's data after "🏡 V mojo vas" | 45-86 ms |
| A cold start (the process killed): the first frame, the splash (`am start -W`) | 0.69-1.7 s |
| … the bundle from its bytecode | 150-310 ms |
| … the bridge's boot over the learner's village | 160-580 ms |
| … Home's data after the view model started | **1.2-1.7 s** (2.4 s the first time after an install) |
| The same cold start before the two fixes below | boot 1.8-2.2 s, Home 2.5-2.6 s (debug build: 1.8 s to the first frame, Home 3.7 s) |
| A new learner's export (6 cards, a village) | 10 KB, 10 files |

Two things made a cold start slow, both found here:

- **The stories' daily check in the boot.** With a village, the stories feature's start resolved every story against
  the packs and the cast (`checkLow`): about a second of QuickJS (1,039 ms against 90-105 ms on the JVM:
  `HostedBootTest`). The onboarding's first boot has no village, so it never showed there. The phone now sets
  `LANI_STORIES_CHECK_DELAY_MS=20000`: the check runs 20 s after the boot. A node checks at once, as before.
- **The bridge key's public point** (Java reads a PKCS#8 key without it, so `HostCrypto` computes Q = d·G): affine
  double-and-add, one modular inverse per step. Now Jacobian coordinates, one inverse at the end (2.3 ms on the JVM);
  the bridge logs a public point that takes over 20 ms. It was the smaller part.

## Tests

- **`HostedTransportTest`** (7, the JVM against the real bundle and the repository): the onboarding's data
  (`initLearner`: the six databases, the profile's name, gender, languages, level and goal, the culture), a directory
  with a learner never overwritten (and two refusals that write nothing), the app's `Bridge` reading through the
  transport (the dashboard, the packs, the culture, a lookup, the game's 404), a write heard (and the db port's notice),
  the event stream as a streaming body that ends on the bridge's side when the collector lets go, no bridge (an
  IOException), a wrong token (401).
- **`HostedExportTest`** (6): an export's entries and manifest (what stays out, the schemas, the hashes, the file's
  name); a round trip into another phone's data (every file's hash, the phone's own key and lease kept, a stale file
  gone into the backup, read-db over the result); the newest three backups kept; 14 refusals; a broken database found by
  the db port's validate-data; the node's `lani export` imported here and this app's export passing the node's
  `lani import --verify` (with python3).
- **`HostedWorkTest`** (3): the rhythm's next moment and what is due, as `rhythm.ts` decides it, from `rhythm.json`.
  **`HostedEnvironmentTest`** (1): the lease's phone, its device id, the shared relay.
- **`HostedBootTest`** (1): the onboarding's boot, then boots from the bytecode over a learner with a village (90-105
  ms on the JVM), and one with the stories' check in the start (1,039 ms).
- **`HostCryptoPointTest`** (2): the bridge key's public point (Jacobian coordinates) is Java's own for 40 keys; 2.3 ms.
- **`companion/tests/test_export.py`** (18): the node's side (the manifest, the rules, a round trip, a git data
  repository's commits, the lease, `--with-secrets`, `--dry-run`, 18 refusals).
- **QA** (`companion/bin/qa --steps hosted`, alone: no dev bridge, and nothing may listen on its port, checked before,
  during and after), on the debug and the qa build. A fresh install: the setup's choice, the onboarding (Ana, as a
  woman, Slovene from English), the tutor's step, Home on the phone's bridge and the village founded; the chat's "no
  tutor yet" line; hiša's card from the phone's dictionary; the packs and a pack learned (its words saved by the phone's
  update-db); a review saved (QA's hook "review": a new learner's words are due tomorrow); the app killed and started
  again (`am start -W`, the splash, the log's "home: N ms"), the review still in the phone's `spaced-repetition.json`
  (6 cards, 5 reviewed), the packs again; the export from the connection sheet (the share sheet), pulled and passed by
  the node's `lani import --verify`; the Tutor section in the connection sheet (the phone's line, no Claude Code to
  choose, "no tutor yet"). The pairing steps (`--steps home,data-sheet,tutor-live`) pass as before.

```bash
cd companion/android && ./gradlew :app:testDebugUnitTest --tests 'si.lanisce.lani.hosted.*'
python3 -m unittest discover -s companion/tests
companion/bin/qa --steps hosted      # inside flock /tmp/lani-emulator.lock; LANI_QA_HOSTED_SHOTS=<dir> for the screenshots
```

## What's left

- **One interceptor:** the live tutor's `PhoneLink` and the app's `PhoneTransport` both answer in-process. `PhoneLink`
  has no streams and lets the bridge add its token; `PhoneTransport` streams and keeps the app's. One could serve both.
- **Voices:** none on the phone yet (glas, plan 3). The phone's text-to-speech speaks, and the offline voice (Piper) and
  the day's clips are off in this mode.
- **The car** ("🚗 Za pot") gets ready from a node's clips: in the hosted mode it has only the phone's prompts.
- **Towns and the family page** need a hosted relay (the relay isn't hosted yet).
- **The chat archive** isn't in the export yet (it lives in the app's files, outside the data directory). The live
  tutor's notebook is: it lives in the data (`app/tutor/notebook.md`), and the export takes what a commit takes.
- **A background job's writes** reach the git sync only when the app comes to the front.
- **An arm64 phone's numbers** (the emulator's above).
