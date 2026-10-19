# Built: git sync, switching modes through git, the handover's QR code, the encrypted backup

*Plan 5, phase B, the data side (2026-10-18/19, branch `git-sync`). What [plan 5](../05-in-app-tutor.md) asks in "Data",
"Git (optional)", "Switching modes through git" and "The secrets at the handover: a QR code", as built, with what Jan
sets up, how a switch goes, and what is left.*

Everything here is opt-in. Until git is connected in the app (or `lani handover` runs on the node), nothing changes: a
data repository without a lease is the node's, as it always was, and the app does what it did.

## What is built

| Part | Where | What |
|---|---|---|
| The lease's rules | `.claude/hooks/lani_lease.py`, `companion/bridge/src/lease.ts`, `companion/android/.../sync/Lease.kt` | Who writes the data repository: `app/lease.json`. The same states and rules in the three. |
| The node's handover | `.claude/hooks/lani_data_repo.py` (`handover`, `take`, `lease`, `push`) | Commit, offer the lease, push; take it back with a fast-forward pull; a refused push that finds the phone's lease stops the node. |
| The node refuses writes | `update-db.py` (exit 2), `card.py` (exit 1), `lani_data_repo.py commit` (no commit), the bridge (423, writing tools fail) | While the lease isn't the node's, each says who writes and how to take the data back. |
| `lani handover` | `companion/bin/lani-handover`, `companion/bridge/src/cli/handover.ts`, `src/handover.ts` | Hands over, then shows the town's secrets as a QR code; `--take`, `--status`, `--code`. |
| The town's own key | `companion/bridge/src/pairing.ts` `loadTownKey` | `app/town-key.pem` when there is one, else `bridge-key.pem` as before. Towns, friendship, the questions between towns, the relay client and `lani-town` speak as the town with it; pairing keeps the bridge key. |
| The learner-data scripts' twins | `companion/bridge/src/db/lease.ts` | The TypeScript update-db and card (the phone's) refuse as the Python does, byte for byte (`tests/test_db_port.py` `LeaseTest`). |
| The shared ignore list | `.claude/hooks/data-repo.gitignore` | The `.gitignore` lani-setup writes, now one file: `lani_data_repo.py` reads it, the app bundles it (`sync/data-repo.gitignore`) and never pushes what it keeps out. |
| The app's sync | `companion/android/.../sync/` (`SyncEngine`, `GitHub`, `DataFilter`) | The phone's `<filesDir>/lani/data` with a GitHub repository over the Git Data API. No JGit. |
| Connect git | `sync/DeviceFlow.kt`, `sync/DataSync.kt` | The OAuth device flow, or a pasted fine-grained token; pick a repository or make a private one. |
| The QR code on the phone | `sync/Handover.kt`, `sync/P256.kt`, `ui/data/HandoverScan.kt` | Opens the node's code, checks the town key, keeps the secrets. |
| The encrypted backup | `sync/Backup.kt` | Zipped, AES-256-GCM, to the relay's backup API as the shared contract says; restore on a second phone. |
| The UI | `ui/data/DataSheet.kt`; the connection sheet's "💾 Podatki · Data" | Git, who writes, the handover, the backup. Slovene first, with en/de/it. |

## What Jan sets up

### 1. A GitHub OAuth app, for "Sign in with GitHub" (optional)

Without it, the app connects with a pasted token only (below). The client id is public: it is in every device-flow
request and may be committed.

1. GitHub → Settings → Developer settings → **OAuth Apps** → **New OAuth App**.
   - Application name: `Lani`; Homepage URL: `https://fentas.github.io/lani`; Authorization callback URL: the same (the
     device flow doesn't use it, but the form wants one).
   - **Enable Device Flow**: tick it.
2. Copy the **Client ID** (no client secret is needed, and none goes into the app).
3. Put it where the build reads it, one of:
   - `lani.githubClientId=<client id>` in `~/.gradle/gradle.properties` (local builds) or in
     `companion/android/gradle.properties` (committed: every build has it);
   - the environment variable `LANI_GITHUB_CLIENT_ID` (the release workflow: a repository variable).
   It becomes `BuildConfig.GITHUB_CLIENT_ID`; empty (the default) hides the sign-in button and says so.

The OAuth app asks for the `repo` scope: the token can reach all of the learner's private repositories. **A GitHub App**
is narrower (the learner picks the repositories on GitHub's install screen): it works with the same device flow and its
client id goes in the same place, but its user tokens must not expire (turn off "Expire user authorization tokens"),
because the app has no client secret to renew them; and making a repository from the app needs the "Administration"
permission (else the learner makes it on GitHub and picks it).

### 2. Or a fine-grained token, pasted

GitHub → Settings → Developer settings → **Personal access tokens → Fine-grained tokens** → Generate:
- Repository access: **Only select repositories** → the data repository (make it first, **private**);
- Permissions → Repository → **Contents: Read and write** (Metadata: read comes with it).

In the app: 💾 Podatki · Data → 🔗 Poveži git · Connect git → paste it. The app checks it (`GET /user`) and keeps it in
the Keystore.

### 3. The data repository on GitHub

Jan's node pushes to `fentas/lani-jan` already (`LANI_DATA_REMOTE` in lani.env, `docs/setup.md` "Your data
repository"). The phone uses the same repository. For `lani handover` the node's `origin` must be reachable without a
prompt (an SSH key or a credential helper), as for the pushes after sessions.

### 4. The relay's address, for the backup (once the relay is hosted)

`lani.relayUrl=https://…` (Gradle property) or `LANI_RELAY_URL`, as for the client id: `BuildConfig.RELAY_URL`. Empty
(the default): the backup section says the service isn't there yet. A developer can point one phone elsewhere through
`DataSettings.relay`.

## Switching: node → phone → node

The data repository is the one place both sides read; the lease says who writes it. A switch is a handover, not an
export.

**Before the first switch (once):** connect git in the app (💾 Podatki · Data → 🔗 Poveži git · Connect git → pick the
data repository). The phone pulls the repository into `<filesDir>/lani/data` and only reads it: the lease is the
node's ("🖥️ The computer writes your data (no handover yet)").

**Node → phone:**

1. On the node: `lani handover` (another learner: `--profile <id>`; one phone only: `--device <its id>`, shown in the
   app's Data section).
   - It says who writes now and asks "Hand Ana's data and the town over to the phone? [y/N]".
   - It commits what changed, writes `app/lease.json` offered to the phone, commits that and pushes (synchronously: it
     stops on an error and says so; run it again).
   - From now on the node only reads: `update-db.py`, `card.py`'s writes, the commits after sessions and the bridge's
     write routes and tools refuse, each saying "Lani's data isn't this node's to write now: … lani handover --take".
   - It shows the QR code with the town's key and the family token, valid 10 minutes (with `--code`, also a 12-letter
     code to type on the phone). Enter wipes the screen and its scrollback.
2. In the app: 💾 Podatki · Data → 📷 Skeniraj kodo računalnika · Scan the computer's code. The phone checks the town
   key against the fingerprint in the code and keeps it (`app/town-key.pem`, `app/family-token`, never synced, and a
   sealed copy in the Keystore).
3. In the app: 📥 Prevzemi podatke · Take over the data. The phone pulls (the offer is in the repository), writes the
   lease as its own, and pushes. "📱 This phone writes your data."
4. The hosted mode (another agent's work) uses the phone's data from here: its bridge writes `<filesDir>/lani/data`,
   the sync pushes a moment after each write and when the app goes to the background.

**Phone → node:**

1. In the app: 🖥️ Predaj računalniku · Hand over to the computer. The phone pushes its last changes, writes the lease
   offered to the node, pushes, and stops writing.
2. On the node: `lani handover --take`. It fetches, fast-forwards to the repository (never a merge), takes the lease,
   commits and pushes. The node writes again; the tutor session and a paired phone work as before.

**A lost device:** the other side takes the data without a handover, after a confirmation ("Take over anyway" in the
app; `lani handover --take --force` on the node). The old side stops at its next sync: the phone sees the lease in the
pull; the node's next push is refused, it fetches the repository's lease into `.git/lani-lease-remote.json` and
refuses writes from then on.

**The node's histories went apart** (it committed while the phone held the data, before it noticed): `lani handover
--take` changes nothing, keeps the node's commits on a branch `lani/node-<time>` and says how to compare them.

## The lease

`app/lease.json` in the data repository (the plan named `.lani/active.json`; the brief's `app/lease.json` keeps it with
the bridge's files, the same layout on both sides):

```json
{
  "schema": "lani.lease/v1",
  "holder": {"side": "phone", "device": "p_0a1b2c3d", "name": "Pixel 8", "mode": "hosted"},
  "offer": null,
  "since": "2026-10-18T21:00:00Z",
  "renewed": "2026-10-18T21:00:00Z",
  "expires": "2026-11-01T21:00:00Z"
}
```

- `holder` writes. `null` while a handover waits (`offer`: the side it is handed to, `by` whom, `at`), or after a
  release.
- The node is the node on any machine (its side counts); a phone is one device (its id).
- The holder renews it with a commit once a day (`renewed`; `expires` = renewed + 14 days). A lease that ran out may be
  taken without a handover; it never lets anyone write by itself.
- **No lease: every side writes, as before the lease** (a node as always; a phone without git). Every node's data has
  none until `lani handover` writes one, so nothing changes there. Taking it is another matter: for a phone a repository
  without a lease is "unclaimed" when it holds data (only a confirmed takeover), "free" when empty.
- A note of the repository's lease from a refused push (`.git/lani-lease-remote.json`) counts only when it can be read:
  a broken one, or a `.git` that is a file (a worktree's, a submodule's), is no note.
- Who "this side" is: the node; with `LANI_SIDE=phone` the phone, its device id `LANI_LEASE_DEVICE` (the phone's bridge
  and its TypeScript learner-data scripts).

| State (for one side) | It writes | It may take it |
|---|---|---|
| mine | yes | (has it) |
| offered (to it) | no | yes |
| offered-away (it offered it) | no | yes, back |
| free | no | yes |
| expired | no | yes |
| unclaimed | no | only confirmed |
| other | no | only confirmed |

## The sync (the app)

`SyncEngine` keeps, in `<filesDir>/lani/sync/state.json`, the commit both sides last agreed on, its tree, the branch's
ETag and a stat cache.

- **Fast-forward only.** It pulls a change of the repository's when the phone changed nothing, or other files; the
  phone's changes go on top of the repository's head. The branch is moved with `force: false`; a branch that went on
  meanwhile is pulled first at the next sync.
- **Never lose a change.** The same file changed on both sides, or a history that was rewritten: nothing is
  overwritten. The phone's version is committed to a branch `lani/phone-<device>-<time>`, the phone's files stay, and
  the sync waits for the learner: "⬇️ Use the repository's version" or "📱 Keep the phone's version" (on top of the
  repository's, which stays in its history; it needs the lease, or a confirmed takeover).
- **The lease decides who pushes.** The phone always pulls; it pushes only while it holds the lease. Changes made
  without it stay on the phone ("This phone's changes stay on it").
- **When:** a pull when the app comes to the front; a push 20 seconds after the last write (at most 2 minutes after the
  first: `DataSync.touched()`, which the hosted mode's bridge calls), a look for changes every 2 minutes in the front,
  and a push when the app goes to the background.
- **Never the private files** (`DataFilter`): the shared `.gitignore`'s lines (the tokens, keys, `*.pem`, pairing and
  invitation codes, the voice clips and APKs, the runtime files, the recent answers, the developer's data, the chat's
  photos, backups and temporary files) and the repository's own `.gitignore`, whose lines come first, so a `!` of its
  own can't bring a secret back. A file of the repository's that the phone keeps out is neither pulled nor removed.
- **GitHub's limits.** Text files go inline in the new tree (no blob requests); a binary one as a blob. A push is four
  or five requests, an unchanged branch a 304 (free). Each answer's `X-RateLimit-Remaining` and `-Reset` are kept: once
  used up, nothing is asked until the reset ("GitHub asks for a pause until 14:05"); a secondary limit's Retry-After
  likewise. Files over 50 MB stay on the phone and are named. An empty repository gets its first commit through the
  contents API (the Git Data API refuses an empty one).
- **Paths from the repository** that would leave the data directory are never written.

## The handover's QR code

`lani handover` shows, after the handover's push:

```
lani://handover?v=1&f=<town fingerprint>&x=<expires, unix s>&d=<base64url(nonce ‖ ciphertext ‖ tag)>&k=<key>
lani://handover?v=1&f=…&x=…&d=…&s=<salt>          (--code)
```

- The bundle (JSON, short keys): the profile, the learner's name, the node's host name, **the town key's private
  scalar** (32 bytes: the phone derives the public key, checks its fingerprint against `f`, and writes the PKCS#8 PEM),
  **the family page's token**, the data repository (`owner/name`, from the remote) and its branch.
- AES-256-GCM under a one-time key: 32 random bytes in the code (`k`, one scan), or with `--code` PBKDF2-HMAC-SHA256 of a
  12-letter code (60 bits, 200,000 rounds; `s` is the salt), so a photo of the QR code alone opens nothing. The
  additional data binds the fingerprint and the expiry. About 390 characters: a QR code a terminal shows (error level L).
- Not moved: the bridge token (one pairing's), the paired phones, open town invitations (they last 30 minutes).
- The plan proposed a one-time code fetched over the paired connection; the brief asked for the secrets in the code
  ("code to phone, easiest"). The default puts the key in the QR code, with `--code` for the stricter way; the CLI wipes
  the screen and its scrollback once the phone has it.
- A phone that holds another town already asks before it replaces it.

## The encrypted backup

- **One master secret**, sealed in the Keystore: from a **recovery code** (160 random bits and a 20-bit check, as nine
  groups of four letters, shown once) or a **passphrase** (at least 12 characters, PBKDF2-HMAC-SHA256 with 600,000
  rounds and a fixed salt).
- **The archive:** the data directory as a zip with a manifest (`lani.backup/v1`: when, the app, the device, how many
  files, the town's fingerprint), without the voice clips, APKs, the developer's data, snapshots and temporary files.
  The town's key and the family token are in it: a restore brings the town back.
- **Sealed:** `LANIBAK1`, a 16-byte salt, a 12-byte nonce, then AES-256-GCM (the key: HMAC of the master and the salt;
  the header is the additional data). The relay holds only this.
- **The relay's API, as the shared contract says** (companion/relay's `backup.ts`: keys are free, a key's backups are its
  own): `PUT /v1/backup/{name}` (at most 50 MB), `GET /v1/backup/{name}`,
  `GET /v1/backup`, `DELETE /v1/backup/{name}`, each signed with `X-Lani-Key` (base64 SPKI DER), `X-Lani-Time` (unix ms)
  and `X-Lani-Sig` (ECDSA P-256 over `METHOD\npath?query\ntime\nhex(sha256(body))`). Names: `lani-<time>.lbk`; the
  newest ten are kept.
- **Signed with a key derived from the master, not the town key** (a deliberate change from the brief's "signed with the
  town key"): the relay keeps each signing key's backups apart, and a lost phone takes the town key with it. With a key
  that comes from the recovery code, a new phone finds and opens its backups with the code alone, as plan 5 wants
  ("restored on a new phone with the recovery code"). The relay must accept any P-256 key for the backup routes (it
  verifies the signature with the key the request brings). Switching to the town key is one line (`DataSync.backups`),
  if the relay requires a known town.
- **Daily** when the app goes to the background (20 hours after the last), and "💾 Back up now".
- **Restore:** "♻️ Obnovi · Restore"; on a new phone, type the recovery code or passphrase. The data there is moved to
  `<filesDir>/lani/data.before-restore-<time>` first; the sync starts anew.

## The UI

Home → the tutor's status dot → 🔗 Povezava · Connection → 💾 Podatki · Data → "Odpri · Open" (QA: the hook `data`; the
hosted mode's setup: `vm.dataOpen = true`):

- **🔗 Git (optional):** connect (sign in with GitHub, or paste a token; pick a repository, the handover's first, or make
  a private one; a public one asks first), the repository (🔒/⚠️), the account, the last sync and how it went, "🔄 Sync
  now", disconnect; a divergence's two ways out.
- **📍 Who writes the data:** the lease in one line, this phone's name and id; "📥 Take over the data", "🖥️ Hand over
  to the computer", "Take over anyway" (confirmed), "📷 Scan the computer's code".
- **🔐 Backup:** set it up (a recovery code or a passphrase), the last backup, "💾 Back up now", "♻️ Restore".

Screenshot: `data-sheet.png` in the round's scratchpad (the emulator, the default build: no client id, no relay).

## Tests

- **Python** (`tests/test_lease.py`, 20, with no lease file the node writes as before: the session written, committed
  and pushed, card.py, a commit, nothing made; `tests/test_db_port.py` `LeaseTest`, 6: the TypeScript twins against the
  Python, byte for byte, for no lease, the phone's, the node's, offered, run out, released, broken files, odd fields,
  the repository's note, a .git that is a file, `LANI_LEASE=ignore`, the phone's side): the rules; handover and take against a bare remote with a second clone as the
  phone; update-db.py, card.py and commits refused while the phone holds the data; a takeover without a handover seen
  at the node's next push; histories that went apart kept on a branch; `--force`; uncommitted changes never lost; the
  daily renewal; `LANI_LEASE=ignore`.
- **The bridge's smoke** (`test/smoke/lease.ts`; `test/smoke/lease-none.ts`, the last section: without a lease a
  write route, POST /readings/done through update-db.py, and publish_gloss write, and nothing makes a lease): the 423 and the tools' refusal while the phone holds the data, reads
  and non-data routes going on, a broken lease file; the QR code's round trip (key, code, a wrong code, another
  fingerprint, after its time); the town key. `test/handover-fixture.ts` writes the app's fixture.
- **Android** (`sync/*Test`, 40): the sync against a fake GitHub (MockWebServer over an in-memory git: the first push
  into an empty repository, a pull, changes both ways, the same file on both sides kept on a branch and either way out,
  a rewritten history, the private files, escaping paths, the rate limit, the ETag's 304, a file over 50 MB, a binary
  blob), the lease and the filter, the node's QR code opened (the bridge's own fixture: the same PKCS#8, SPKI and
  fingerprint), P-256 against the platform's keys, backup and restore on a second phone with the code alone against a
  fake relay that checks the signatures (stale time refused, each key apart, the newest ten kept, zip-slip refused), the
  device flow, the debounced push.

## What's left

- **The hosted mode's side** (another agent's): the guard is in the bridge's one builder (`build.ts`: the Ctx's
  `lease`, `guardTools`, `guardRoutes` around `appApi`'s routes and the relay's), so the phone's bridge has it too; the
  hosted setup sets `LANI_SIDE=phone` and `LANI_LEASE_DEVICE=<DataSync.settings.device>` in its environment (until then
  the phone's bridge counts as a node: with no lease file, as now, it writes), calls `DataSync.touched()` after its
  writes and reads again on `DataSync.onPulled`. Until the hosted mode is on, a phone that took the data over still talks
  to the node, which answers its writes with 423: the app keeps them queued (the outbox retries a 423) until the lease
  or the mode changes.
- **The way back's secrets to a new node:** a node that never had the town (a town born on the phone) needs its key.
  The node keeps its own key through a switch, so node → phone → node needs nothing; the new-node case (the phone sends
  the bundle over the paired connection, plan 5's "Hosted → self-hosted") is not built.
- **Several devices at once:** one writer at a time; a tablet takes turns through the lease.
- **Not run against the real GitHub** (no token in this round) nor the real relay (another branch): only against
  fakes that follow their documented behaviour.
- **GitHub App tokens that expire** need a client secret to renew: not supported; turn expiry off.
