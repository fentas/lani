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

A budget from a parent's device is a **ceiling** on that device (`LiveSettings.familyCap`, `computerCap`, `capNow`): it chooses under it and
can't go over it, and only the parent's device raises it. A ceiling rather than the device's own setting, because the bill is the parent's: an
adult on the family's key could otherwise raise it back. "Tutor" there says whose limit it is. What came over the bond and what came through
a computer are kept apart, and the lower counts, so a computer answering "none" takes nothing away.

**If the parent's device is lost.** An adult's device can leave the family: "🔒 Drži za izhod iz družine" in its tutor's settings. It costs what
the limit protects: the family's key goes with it. **A child's device has no such way, on purpose:** a child's device can't change a budget,
and a two-second press there proves nothing. So a child's device under a limit from a lost phone stays under it until it's set up again with a new
code. A limit is a lower ceiling, not a stop: the tutor goes on under it.

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

## The hard limits, and how each is held

| Limit | Held by |
|---|---|
| The QR holds no key and works once | `Offer` carries an address, a one-time code, a public key, an expiry and the relay's address, and tests pin the fields; `Sender.claim` is one claim across both ways. A family computer's code is the same: `lani://pair`, five fields, one use, 10 minutes |
| What crosses stays sealed end to end | ECDH with the code as salt for the exchange; a key each way from the bond after it; a test keeps every byte a relay is given and looks for the key, the name, the public key and the code |
| A child's device can't add a member or change a budget | the page isn't there, `FamilyDevices` and `FamilyShareController` do nothing, its bridge answers `403 child` whatever it's sent, and it has no list to send to |
| A parent's hold, everywhere | a new member, a new code, another device, a budget, a removal, taking a device off the list, leaving: each behind `HoldToOpen`. It's a two-second press in the app, not a proof: the bridge can't see it, so limit 3 doesn't rest on it |

**An independent review** of this branch, against those four limits, found two breaks and both are fixed: "new code" and "another device" minted a pairing code on one tap, and `leave()` lifted a limit on a child's device. It also found a removed device getting the family's key from a check still waiting, the computer's "no rule" wiping a limit that came over the bond, a device forgetting the relay its code named, a multicast discovery left running by every find, a crash on a QR with an unusable relay address, sockets held open by a byte now and then, a device listed when nothing went, `FLUENT_*` tokens and a data remote that could reach a new learner's bridge, and drop boxes one address could fill. All fixed, each with a test.

**Left as it is, on purpose:** a device's name isn't covered by the proof and crosses the Wi-Fi in the clear, so an on-path neighbour could read it, or swap the name a parent reads before holding; they still can't take the key. Covering it would break pairing with an older app. A hello played back on the Wi-Fi can move "last in touch" and nothing else. A budget is kept by the child's own app, so it's as strong as that app. Someone who floods the office with connections can delay a note, a visit at a time; nothing leaks.

## How

- `companion/relay/src/drop.ts`; `Store` `putDrop`, `takeDrops`.
- `companion/bridge/src/family-members.ts`, `features/family-members.ts`; `learners.ts` `addLearner`, `tailnetHost`; `features/town-requests.ts` `ctx.linkLocalChild`.
- `data/FamilyLink` (`Way`, `Listener`, `RelayWay`, `Drop`, `bondKey`, `Offer.relay`), `data/FamilyPost` (`FamilyBook`, `FamilyNote`, `Keys`, `Office`, `visit`), `data/FamilyDrop` (`HttpDrop`, `FamilyNsd`), `data/FamilyMembersApi`.
- `app/FamilyDevices` (`FamilyCourier`, `FamilyHome`), `app/FamilyComputer`, `app/FamilyShareController`; `ui/settings/FamilyShare`; `LiveTutor` `receiveFamily`, `forgetFamily`; `LiveSettings` `ceiling`, `capNow`.

## Tap counts

| Journey | Before | After |
|---|---|---|
| A new member on a computer | three commands on the computer (`lani-profile add`, `tailscale serve`, `lani-pair`), then 3 taps on the new device | none on the computer where `tailscale serve` is allowed; on the parent's device ⚙️, 👪, the name, "for a child" if so, the hold; 3 taps on the new device |
| A device's daily budget | on that device: ⚙️, Tutor, a hold on a child's, the chip | on the parent's device: ⚙️, 👪, the row, the chip, the hold; nothing on the device |
| Stopping a device on a computer | `lani-pair --revoke` on the computer | ⚙️, 👪, the row, the hold |
| Stopping a device with the family's key | change the key at the provider, for everyone | ⚙️, 👪, the row, the hold; it forgets the key when it's next in reach. A copy needs the provider, as before |
| A new device with no internet | the code spent, a parent shows a new one | scan again once online, or one tap to take it now |

## Native check

All machine-written by Claude (`_note_leftovers_app` in the four tables, 53 strings and the 4 after it):

- **sl** (Jan's wife): `family.addWhat`, `family.leaving`, `family.reachWifi` ("Druge poti do nje še ni: do takrat ostane, kar ima": is it clear?), `family.capWhat`, `family.capHere`, `family.offline`, `family.takeAnyway`, `family.leaveWhat`. "Učenec" for a learner on a computer, and `family.memberAdded`, which has no gender but may read oddly; `family.townLinked` says "njihova vas" for one person.
- **de**: `family.addWhat` says "Profil" where the Slovene says "učenec"; `family.capHere`, `family.offline`.
- **it**: `family.addWhat`, `family.leaving` ("In uscita"), `family.offline`, `family.leaveWhat`.
- **en**: British English, as elsewhere.

## Voice

Nothing spent. Nothing new needs a clip: these are settings' lines, read by nobody but the page. **0 characters** for a voice build. No line that had a clip changed; `family.computerNot`, `family.newCode` and `family.anotherDevice` went, none of them ever voiced.

## Tests

- `data/FamilyLinkTest`, `data/FamilyPostTest`, `app/FamilyDevicesTest`, `data/FamilyMembersApiTest`, `data/HttpDropTest`, `tutor/live/LiveTutorTest`: the QR's fields, a relay that keeps every byte, a code used once on either way, a wrong proof, a note played back, a stranger on the Wi-Fi, a byte now and then, a child's device changing nothing, a key waiting for its check, the two ceilings.
- `companion/relay/test`: the drop boxes' limits; none of a parent's routes through the relay.
- The bridge's smoke, `test/smoke/family-members.ts`: a real child's bridge refusing all of it; an id that's never shell text; what reaches the new bridge's environment; another adult's devices; what's left, with its command.
- `companion/bin/qa`: `family-parent` with `family-child` (`LANI_QA_FAMILY_TABLET=1`), `family-computer-parent` with `family-computer-child`, on the two emulators at once. Between two emulators nothing multicasts, so QA gives the address (hook "family:peer:"): **`FamilyNsd`, the Wi-Fi's discovery, has run in no test and on no emulator.** The channel behind it has; finding the other device hasn't, and that's the first thing to try on two real phones.

## What a device has shown

Seen on emulators, each journey's steps green, at this branch's final code:

| Journey | Parent | New device | Seen |
|---|---|---|---|
| A family on devices | phone | tablet, on its side, a child's | the code unspent with no internet here; "take it now"; the parent's hold, a tap doing nothing; the key waiting, then checked once online; the budget on its way, then there on both; a ceiling behind the child's hold, higher chips off, no way out; no family's page on a child's device; the removal, leaving, then gone, the key gone there; a second scan refused |
| A family on a computer | tablet, on its side | phone, a child's | a new member by a hold, a tap doing nothing; a **real second bridge** by the real `lani-bridge start`; another code by a hold; the code read by "Someone in my family already uses Lani", paired with that bridge; the child's town linked to the parent's as its guardian; no family's page on it; the device on the parent's list; its budget through the computer, read by the device; its removal, and "Ni več seznanjen · Not paired any more" on Home |

**Only tested in code, not on a device:**

- **`FamilyNsd`, finding the other device on a Wi-Fi.** Two emulators don't multicast to each other, so QA gives the address. Everything after the finding is seen; the finding is not.
- **The relay, on a device.** The exchange and the notes are tested against the relay's contract, and the relay's own routes in its tests, but no two devices have met through a running one.
- **`tailscale serve` from a bridge.** QA's node has a stand-in for it and an address from `LANI_FAMILY_URL`. The smoke has the failing case.
- **The tutor of a new member.** QA starts no tutor session.
- **A real camera** reading the codes: QA hands the text over (hook "family:scan:").

## What needs Jan

**To try on two real phones and a camera, in this order:**

1. Two phones on one Wi-Fi: add a member with the camera, then set a budget with Lani open on both. It should arrive within a minute with no address given: that's `FamilyNsd`'s first real run. Try a guest Wi-Fi too, which may block devices from seeing each other: the row should simply go on saying "on its way".
2. Remove that device with both open, and see the tutor gone from it.
3. A new phone in flight mode with Wi-Fi on but no internet (a phone's hotspot with its data off): scan, see the code unspent, "take it now", then go online.
4. On the family's computer: add a member from a phone, with the real `tailscale serve`. If it asks for the operator's right, the page should show the command.
5. Read the family page on the real tablet, upright too: QA saw it on its side.

**Decisions:**

- **A child's device under a limit from a lost phone** stays under it until it's set up again with a new code. An adult's device can leave the family; a child's can't, because a child's device can't change a budget. Jan may prefer another way out, such as the family's computer or a code from a second parent.
- **Any adult on a computer looks after every child there**, as `looksAfter` has it, while only the owner's town links a child. So a second parent can manage a child's devices, and so can any other adult with a profile there.
- **A parent isn't told when an adult's device leaves by itself:** the row just stops being in touch. The key is gone from it either way.
- **Hosting the relay:** see "The relay".
