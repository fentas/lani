# Built: the last small branch (last-small, 2026-11-05)

Two small items from final-polish's "What's left". Both turned out larger than they looked: the typed turn's row broke on ordinary phones too, and
the relay's limit wasn't the one it seemed to be. No new text, so nothing for a native check and nothing for the voice build.

## The typed turn on a small phone

### The row

It broke, and worse than the answer's did. `LetMeChoose` takes what it wants and Check, with `weight(1f)`, what's left. In real text, the row as it was:

| Row | Pairs broken | Worst |
|---|---|---|
| 328 dp, a small phone | all twelve | six pairs had 0 dp for Check's text: a letter a line |
| 379 dp, a phone | four | it-de: "Contr / olla", "Prüf / en" |

So this wasn't only a small phone's flaw: Teo's own pair broke on an ordinary phone. On a device, the old build:

| Screen | Pair | Check's text |
|---|---|---|
| Small phone | it-sl | 40 × 280 px |
| | it-en, it-de | not there: an empty grey sliver with no label |
| Phone | it-en | 114 × 159 px, "Contr / olla · / Check" |
| | it-de | 57 × 371 px, seven lines: "Co / ntr / oll / a · / Pr / üfe / n" |

The logic is `AnswerButtons`', moved to `WayOnRow` in `Components.kt` and used by both rows, not written twice. "✋ Izberi raje · Let me choose" gives up its words for its ✋ (`EmojiButton`; TalkBack still says the name) where the row can't hold both. A hand with no words is no wall: a wrong answer brings the choices back too, and its tap does (`typed-buttons` taps it in every pair).

On a small phone the ✋ stands alone in all twelve pairs, and on a phone in the longer ones. That's the price of the row, and the one thing here that a look on a real phone might want changed.

On the final build, `typed-buttons` read with the field focused and the keyboard up, in each village with its three bases (shots: `ls-typed-*.png`):

| Screen | Pairs | Check | "Let me choose" |
|---|---|---|---|
| Small phone, 720 × 1280 at 320 dpi | all twelve, the four villages | one line, 40 px tall, 200 to 243 px wide | ✋ |
| Phone, 1080 × 2400 | it-sl, sl-en, sl-de, sl-it, de-sl | two lines, a whole word each (106 px) | with its words |
| | it-en, it-de, de-en, de-it | one line, 53 px | ✋ |

The phone wasn't run in the Lake District. In each pair the field and Check stood whole above the keyboard's top (682 px on the small phone, 1517 on the phone; de-sl's once its first dialog's tip was closed, item 5 below), and the tap on ✋ brought the choices back. `AnswerButtonsTest` holds both rows to the twelve pairs on rows of 200 to 380 dp, the device's 328 and 379 among them, at 1, 1.3 and 1.6 times the text. With the old `Row` back, three of its tests fail: "[P, r, e, v, e, r, i, …]". `typed-turn`, the step that already checked a typed turn with the keyboard up, passes on the final build too.

### The header above it

**Not in the brief, and in a commit of its own (`fix(scenes): a long title…`), to drop if unwanted.** Fixing the row alone left the screen broken, and QA showed it: in Kärnten on a small phone, the title shared 182 dp with "👁 Übersetzung" and had 40 of it. "Oma Resi wäscht am Bach · …" ran to thirteen lines with words in pieces, and with the keyboard up the header took the whole panel: the sentence, the field and the chips gone, so the learner couldn't see what they typed. All three German pairs, any dialog with a long title. The field took 4 taps of the "▾" to reach, against 0 or 1 elsewhere.

`TitleAndToggle` uses the same `WayOnRow`: the chip gives up its word for its 👁 where it would leave the title less than 80 dp. Longest word alone wasn't enough, since a title of short words a word wide runs just as long. Where a title has 80 dp nothing changes, so a phone shows what it did.

| In Kärnten, small phone | Before | After |
|---|---|---|
| Title | 13 lines | 3 or 4 |
| Taps of the "▾" to the field | 4 | 0 or 1 |
| Field and Check with the keyboard up | out of sight | whole, above the keyboard |

**Left, on purpose:** `ISpyPanel` has the same chip beside its title and isn't changed. And on a small phone with the keyboard up, the sentence with the gap is cut or scrolled above the field, in every pair: 612 px has to hold the header, the field, the chips and the row. It's a matter of room, not of words, and the learner has read that sentence before they tap the field. Whether a swipe up stays there against `KeepAtEnd` wasn't tried.

### Tap counts

Check and "Let me choose" are 1 tap each, before and after. To the field on a small phone: as in the table above.

### QA was wrong five times on the way

Each time it claimed something a screenshot then contradicted, and each is fixed in `typed-buttons`:

1. It read the row without scrolling, and on a small phone the row is below the fold.
2. Its tap on the field hit the "▾", which stands in the middle at the panel's foot and only scrolls: it read the row with no keyboard and printed "the field focused".
3. It printed "as its ✋" for a label it simply couldn't find, on a build with no such thing.
4. **de-en passed while the field was out of sight.** A Check 9 px high counted as a Check, and the broken header was found by eye. The step now requires the field and Check whole above the keyboard, and on the build with only the row fixed it fails on all three German pairs.
5. Its one red on the final build wasn't the app's. The tip of a learner's very first dialog took the room, but that tip goes with that dialog and a turn is typed only for a secure rule, so only QA's hook puts the two together. The step taps its ✕.

## The relay's upload window

### What the limit really was

The question was whether `PUT /v1/backup/{name}` could check its signature's time when the request starts, because it checked after the last byte and refused anything older than 5 minutes: 1.4 Mbit/s for 50 MiB.

That wasn't the limit. **Bun ended the upload at two minutes.** `idleTimeout` reads as a timer for a silent connection, and the code's comments said so ("Bun cuts a stalled request after two minutes"), but a request's body doesn't reset it. A signed PUT over a real socket, one piece a second, on the relay as it was (Bun 1.3.13, the image's):

| | Result |
|---|---|
| Body spread over 320 s | cut at 120 s, no answer, though bytes were arriving every second |
| The same in isolation, `idleTimeout: 8` | cut at 8.0 s, on 1.3.13 and on 1.4.4 |

So on a bare relay `401 stale` couldn't happen to a slow upload at all, and a fix of the time check alone would have changed nothing.

| Upload | 50 MiB took at least | Most that got through at 1 Mbit/s |
|---|---|---|
| Thought to be (5 minutes) | 1.4 Mbit/s | 35.8 MiB |
| Really (Bun's 2 minutes) | 3.5 Mbit/s | 14.3 MiB |
| Now (30 minutes) | 0.23 Mbit/s | the 50 MiB, in 7 minutes |

A download doesn't have this: Bun's timer is reset by what it writes (24 MiB read over 15 s under `idleTimeout: 8`, whole). A restore isn't cut.

### What changed

- The time is checked once, as the request starts. Bun calls `fetch` at the headers: a request with an old time was refused on a socket with no byte of body sent.
- The last byte has 30 minutes after that, `UPLOAD_WINDOW_MS`. Later: `408 too_slow`, the temp file gone, the slot free, nothing admitted. It carries a `Retry-After` because OkHttp repeats a plain 408 by itself, with a signature that is old by then.
- Each piece sets the server's timer again (`server.timeout(req, 120)`), so two minutes of silence still end an upload: now it's true of silence. That call is a total from the moment it's made, not an idle timer, so it has to be made again with each piece; 0 turns the timer off, and a stalled sender then keeps its slot.
- The signature is still checked over the whole body, before the file takes its name.

### What it does to the replay window

**A replay is still refused, at every moment one could arrive. A request that never arrived can now land later.** Those are different things, so both are written down.

Checking the time at the start alone would have opened a hole. A message is remembered for 6 minutes, and a copy started in the last fresh moment could take longer than that to arrive: by then its row is swept, and it's taken. Taking the longer memory out of the code shows it: the test's copy gets a `200` and is stored. Two things close it:

1. **A hard end.** No upload is admitted more than `UPLOAD_WINDOW_MS` after it started, checked at every piece and at the end of the body, by the relay's own clock and not by Bun's timer.
2. **A longer memory, for this route only.** A backup's PUT is remembered for `MAX_SKEW_MS + UPLOAD_WINDOW_MS + 1 minute`, 36 minutes.

For a message signed at time *t*: a copy can start until *t* + 5 minutes, so it lands by *t* + 35. Its row lives at least until *t* + 36, so a copy always finds it, and after *t* + 5 none can start. Other routes are remembered as they were, and it costs one row per backup, so about one a day per learner.

**What did get wider.** A request that was never delivered could land until *t* + 5 minutes, and can now land until *t* + 35. It's inherent: a held request can't be told from a slow one. To use it, someone has to have the whole signed request, so they are inside TLS, and must have stopped the original. It lands once, under the name and with the bytes the key signed. A name is `lani-<second>.lbk`, so it replaces nothing. It would list as the newest backup until the next one, at most 35 minutes older than it looks (5 before this), and it can be no more than that learner's own ciphertext. Someone in that position can already drop every backup, which is worse. So it's accepted, and it's the reason the window is 30 minutes and not more; `UPLOAD_WINDOW_MS` is the one number to change.

**Who can hold an upload's place.** The signature can't be checked before the body, so anyone with a key they made up and a fresh time can open an upload. With the window alone, trickling a byte every 119 s held one of the relay's four places for 30 minutes instead of two, and four such uploads blocked every real backup. So what can be checked as the body comes is:

- **One upload at a time per claimed key, and two per address** (`429 busy`; `RELAY_BACKUP_UPLOADS_PER_ADDRESS`, the address `fetch` already has, `X-Forwarded-For`'s under `RELAY_TRUST_PROXY`). One address can't fill the four places.
- **A rate floor.** Once the first minute is over, an upload slower than 12,500 bytes a second on average (0.1 Mbit/s, `RELAY_BACKUP_MIN_BYTES_PER_S`, `RELAY_BACKUP_RATE_GRACE_MS`) is cut with the same `408 too_slow` and `Retry-After`. The floor is checked as each piece arrives, and the two minutes of silence end a sender who stops sending, so a trickler is out after about two minutes. It's under the 0.23 Mbit/s the window asks of 50 MiB, so a slow phone isn't cut by it; the test sends 50 MiB at 0.25 Mbit/s over 28 minutes.

What's left: to hold a place for the whole window a sender has to keep 0.1 Mbit/s up, and two per address. Many addresses could still fill the four. Making that impossible means checking a signature over the body's hash before the body is sent, which changes the protocol, so it's left. A claimed key isn't checked before the body, so someone who knows a learner's backup key could take that key's one place, for the two minutes the floor gives them. That key is derived from the recovery code and only ever goes to the relay, so they'd need the relay's view.

### Behind a proxy: the fix needs the body to stream

Measured: the new relay, the same 5 minute 19 second upload at the same moment, through a local nginx both ways.

| The proxy's request buffering | Answer |
|---|---|
| On (its default; what relay.md's steps left in place) | `401 stale`, after the last byte |
| Off | `200` |

A buffering proxy opens the request to the relay only once it holds the whole body, so "the start" is the end, and five minutes is the limit however long the relay allows. **This is the brief's `401 stale`, and the relay can't fix it from its side.** relay.md's hosting steps now say `proxy-request-buffering: "off"`. Whoever hosts the relay has to set it: nothing in this repository can.

### What the app does when an upload fails

Read from the code (`DataSync`, `Backup.kt`, `DataSheet`), not watched on a device. This hasn't changed.

**Does the learner see it? Not the daily backup.** The only automatic trigger is the app going to the background (`DataSync.onBackground`), and a failure goes to `log`, which is logcat. Settings' data row and the Data page show the last backup's date, with no warning however old it gets. Only "💾 Back up now" says anything: "⚠️ The server can't be reached." for a cut upload or a 408, "⚠️ The server refused the request." for a 401.

**Does it retry forever? Yes.** `lastBackup` moves only on success, so while the last good backup is over 20 hours old, every trip to the background starts again:

- from the first byte, the whole blob, under a new name;
- with no backoff and no limit on attempts;
- with no check of the network, so on mobile data too: up to 50 MiB per attempt, silently;
- with nothing to stop two at once: `busy` greys the button, `onBackground` doesn't read it, so leaving the app, coming back and leaving again runs a second upload beside the first.

It stops when one succeeds. A phone whose clock is over five minutes off gets `401 stale` each time, so that loop never ends either, and says nothing.

**Two more things that limit a slow upload, in the app:**

- `AppViewModel` passes no client to `DataSync`, so `RelayClient` gets `GitHub.DEFAULT_CLIENT`: read timeout 60 s and OkHttp's default write timeout of 10 s. `RelayClient.DEFAULT_CLIENT`, with the 120 s meant for this, is never used. Ten seconds with no room in the socket's buffer ends the upload, and a radio that stalls for ten seconds is ordinary on the connections this item is about. It's one line, and it's left for whoever takes the list below because this branch was asked to report the app, not change it.
- The upload starts as the app goes to the background and nothing keeps the process alive for it (no foreground service, no WorkManager job), so Android may freeze or end it first. A long upload is the one this hits. No device has shown how often.

**The honest summary:** the relay no longer refuses a slow upload, if the proxy streams. Whether a slow phone now finishes one depends on those two, so this isn't "fixed for slow connections" yet.

Left for a decision, in order of what they cost a learner:

1. Let the learner see that backups are failing (a line on the Data page once the last good one is, say, three days old).
2. The client: `RelayClient.DEFAULT_CLIENT`'s timeouts for the relay.
3. Back off after a failure, skip it on a metered connection unless asked, and don't start one while one runs.
4. Run the upload as a WorkManager job, which brings the backoff and the network check with it, and answers the freezing above.

No real backup's size is known here: learner data isn't read on this branch, and a synthetic learner's says nothing. If real ones are a few MiB, all of this bites far less, so that's worth looking at first.

## Tests

On the final code: the bridge's and the relay's `tsc` clean; the smoke 4,571 checks, none failed (`prompt-clashes` within its baseline of 343); the relay's tests 38, 10 of them new, on Bun 1.4.4 and on 1.3.13, the image's; the relay smoke 45 checks, none failed, its backup round trip among them; Python `companion/tests` 188 (11 skipped) and `tests` 253, with `/usr/bin` first in `PATH`; the Android suite 4,526 tests, 2 skipped, 0 failures, 8 of them new (`AnswerButtonsTest` 4, `DialogHeaderTest` 3, `BackupTest` 1).

Each new safeguard was taken out once to see that a test catches it:

| Taken out | Caught by |
|---|---|
| The relay's longer memory for a PUT | the copy at the worst moment (it gets a `200` and is stored) and the memory's lengths |
| The deadline | the 408 test and the body that stops after it |
| The time from the start (the clock at the end, as before) | the slow upload, the copy at the worst moment, the changed body |
| The timer set again per piece | the real socket's upload, past a 4 s `idleTimeout` |
| The time check at the headers | a time too old or too far ahead, before the body |
| The `Retry-After` | the 408 test; `BackupTest` sees OkHttp send a plain 408 twice |
| The longer memory for every request | the memory's lengths |
| `WayOnRow` under a typed turn (the old `Row`) | three of `AnswerButtonsTest` |
| The chip giving up its word | `DialogHeaderTest`: the title at seven lines |
| (QA) the header on the build with only the row fixed | `typed-buttons` fails on all three German pairs |

Measured on a socket, not in the tests: the relay as it was and as it is, on Bun 1.3.13, the same 1 MiB over 5 minutes 19 seconds. Before: cut at 120 s with no answer, and the copy sent after it took the backup (`200`), since the first never landed. After: `200` at 319 s; a sender that stops after its first piece cut at 120 s; a copy sent at once after a 150 s upload `401 replayed`, and after the 319 s one `401 stale`.

## What no device has shown

- **The relay behind its real proxy.** The streaming and buffering results are from a local nginx. Whether the host's ingress buffers, and what it does with a 30 minute request, isn't known until it's set.
- **A slow phone finishing a backup.** No phone ran one on a slow connection: the app's 10 s write timeout and Android freezing the upload in the background are read from the code, not seen.
- **TalkBack** on the ✋ and the 👁. Their names are in the tree, and no screen reader has said them.
- **Teo's tablet** and **large text on a device**: the row and the header are held there by the unit tests only.
- **The Lake District on a phone** (the small phone only), and **`ISpyPanel`'s header**, which has the same chip and wasn't looked at.
