# Built: the leftovers of the app (leftovers-app, 2026-11-05)

Branch 2 of the leftovers round: what earlier rounds left of a family's devices, and what no device had shown. Everything written in a
language here is machine-written; "Native check" lists it.

## A family on a computer

A family on a computer couldn't add a member from the app: each member is a learner with a bridge of their own, and a running bridge
could only mint a code for its own learner. Now "⚙️ → 👪" on a parent's device asks a name, two languages and "for a child", and a
parent's hold does the rest (`bridge/src/family-members.ts`, `features/family-members.ts`):

| Step | Was | Now |
|---|---|---|
| The profile | `lani-profile add` | the same code (`learners.ts addLearner`), under a lock now, since the CLI and a bridge may add at once |
| Their bridge | `lani-session` or `lani-bridge start` | `companion/bin/lani-bridge start --profile <id>`, started by the parent's bridge |
| The tailnet | `tailscale serve …` | the same command |
| The code | `lani-pair --profile <id>` | the same file in their `app/`, shown as a QR code on the parent's device |
| A child's town | `lani-town link`, or Friends | linked with the owner's as its parent's, once its bridge answers |

**What only the shell could do, and the route chosen.** A bridge has the machine's rights, so the danger is what a phone could make it
do. So a request carries a name, two languages and a yes or no, and nothing else; the id is made here and matches `ID`, so nothing from a
device reaches a shell; the commands are fixed. The started process gets none of the parent's learner, tokens or port. Two things can
still need the computer, and the page says each with its command (`left`): `tailscale serve` where the owner hasn't allowed it (`sudo
tailscale set --operator=$USER`), and a tutor where nothing starts one (`lani-session --profile <id>`; `lani-watchdog --install` makes it
start by itself). Behind a proxy of the owner's own, `LANI_FAMILY_URL` says the address. `LANI_FAMILY_ADD=off` turns all of it off, and
`LANI_FAMILY_MAX` (8) bounds what a lost phone could start.

**It works with the QR flow.** The code is a pairing code, and "Someone in my family already uses Lani" now reads it too, so the new
device has one way in, whichever kind of family it joins.

**Who looks after whom.** A device of an adult learner sees and changes: themselves, every child of this computer, and whoever they added.
Another adult's devices are theirs: not on the list, `404` if asked.

## The channel, and what it carries

A family on "just this device" had no way to reach a device once the QR exchange was over. Now the exchange leaves a **bond**: a key
both devices work out from the session key (`FamilyLink.bondKey`). It never crosses, so it's in no QR and in no frame. Each side keeps it
in the tutor's vault, sealed by the Keystore (`app/FamilyDevices`, `FamilyBook`): never in an export, a backup or a log.

| | Over | What it takes |
|---|---|---|
| A family on devices | their Wi-Fi: the parent's device answers while it has something to say or the page is open, found by a hashed name over the system's multicast DNS; the other device comes by | Lani open on both |
| The same, through the relay | two drop boxes, named by hashes of the bond | a relay set; the next time Lani is open on the device |
| A family on a computer | the computer: `PUT …/budget` is kept with the learner, the device reads `GET /family/rules` as it comes to the screen | that it's paired |

Nothing runs behind the learner's back: no service, no background wake-up. So a note on devices waits until both are open, and the row says
so: "⏳ Na poti: odpri Lani na tej napravi". **Where no channel exists the page says that plainly:** a device added before this update
isn't listed, and keeps its own limit.

**Sealed both ways, one way of command** (`data/FamilyPost`): a key for each direction, so nothing can be sent back at its sender; a
note's sequence only goes up, so one recorded and played back does nothing. Only the side that shared has a list to send to, so a child's
device has nobody to tell anything.

### The daily budget

A budget from a parent's device is a **ceiling** on that device (`LiveSettings.familyCap`, `capNow`): it chooses under it and can't go over
it, and only the parent's device raises it. A ceiling rather than the device's own setting, because the bill is the parent's: an adult on
the family's key could otherwise raise it back. "Tutor" there says whose limit it is.

### Removing a device

| | What it does | What it can't |
|---|---|---|
| On devices | the note waits; when it arrives the device forgets the family's key, only if that key is still the one in use, and the limit | a device that never comes back keeps its copy. The page says so: only changing the key at the provider truly stops it. "Take it off the list now" gives up on telling it |
| On a computer | `lani-pair --revoke`: its token stops at its next request | |

## The relay

The relay was built and isn't hosted. Everything of a family's that could use it is built and tested against it: the QR exchange and the
notes above, in drop boxes (`companion/relay/src/drop.ts`, `data/FamilyDrop`), tried only when nothing answers on the Wi-Fi.

- **Why a box, not a town's mailbox:** a mailbox needs a town's key and a router on the other side, and a new device has neither yet.
- **What the relay sees:** opaque bytes under opaque names. A claim is sealed under a key from the one-time code, so not even a public key or
  a device's name shows; the gift under the session's key; a note under the bond's. There's no account and no signature, which would only
  tie a family's boxes together.
- **The QR** gains the relay's address (`r=`) when one is set. It still holds no key and still works once: the same `Sender`, the same claim.
  An older app reads past it.

**What the lead has to deploy, and the one place it's set:**

1. Host `companion/relay` over HTTPS, from its Dockerfile, with `RELAY_DATA_DIR` on a disk that stays and `RELAY_TRUST_PROXY=1` behind a proxy
   (docs/plans/05-built/relay.md, "Operations"). A relay built before this branch has no `/v1/drop`.
2. Set `lani.relayUrl` (or `LANI_RELAY_URL`) for the APK's build: `BuildConfig.RELAY_URL`, the one setting. Nothing in the app changes; the
   page's words follow it ("family.reachRelay", "family.viaRelay").
3. Check it: `PUT <relay>/v1/drop/<64 hex digits>` with a body answers `202`, and `GET` gives it back once.

## A code isn't spent on a check that couldn't run

A new device with no internet got "the family's key doesn't work here", and the code was gone.

- **Before a claim:** with no internet the device claims nothing. It says why, the code stays good on the parent's device, and it offers
  "📷 Scan again" and "📥 Take it now, check it later" (never a wall: at a holiday house the Wi-Fi may be all there is).
- **After a claim:** a check that couldn't run, on any provider, says nothing of the key. It's kept sealed (`FamilyBook.keyWaiting`), tried again
  each round once there's a network, and the learner is told. Only the provider's no is "the family's key doesn't work here" (`FamilyKey`).
  A key the learner set meanwhile stays theirs.
