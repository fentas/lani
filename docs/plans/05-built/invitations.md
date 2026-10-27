# Invitations without a PC (built 2026-10-27)

Jan asked: "How is one doing the linking without PC? Would assume maybe QR code on phone?" and, for the choices: "make it
the best, prod ready". Jan is a parent: their child Teo learns Italian from German on a tablet, in a town of their own.

Before this round, three things needed a PC or didn't work:

- **A child's town** could be linked only on the node (`lani-town link`, or `invite`/`accept --profile`). The child's app
  got `403` for an invitation and hid the buttons.
- **A shared invitation didn't open the app.** It was `lani://town?…`. The manifest registered only `lani://tutor`, and
  messengers don't make a custom scheme tappable. The learner copied the text, opened Lani, found Friends (4 taps deep),
  "Sprejmi", and pasted it.
- **The address in an invitation** was `http://127.0.0.1:<port>` without `LANI_TOWN_URL` or a relay. Only the towns of
  the same computer can reach that address, and nothing said so: a friend elsewhere got "📡 The other town doesn't
  answer".

## What was built

### 1. A child's town without the PC

A child's app shows and scans invitations like any other app. **Nothing links without a parent.**

| Step | What happens |
|---|---|
| The child asks | The child's invitation is accepted by another town, or the child accepts another town's invitation. The other town links the child's town and marks the link as waiting (`pending: "guardian"`). The child's town keeps a **request** (`<data>/app/town-requests.json`) and links nothing. It refuses the other town's signed requests until a parent says yes. |
| The parent hears of it | The child's town tells its guardians' towns (`POST /town/guardian/notify`). The parent's phone gets a notification: "👪 Teo: nov prijatelj? · Teo: a new friend?". A tap opens Friends. |
| The parent decides | In their own app: "👪 Tvoji otroci · Your children" in Friends shows each request, with the other town's village as it looks now (its age, buildings and people) and how it came, and "✓ Dovoli · Allow" / "Ne dovoli · Don't allow". Or on the child's family page ("🤝 Prijatelji · Friends"). Or on the node (`lani-town requests`, `approve`, `decline`). |
| Then | Yes: the child's town links the other town and tells it (`POST /town/approved`). No, or the child takes the request back: the child's town tells the other town to let go (`POST /town/unlink`). |

**How the child's bridge knows that the parent decides.** The parent's app talks only to the parent's bridge. That bridge
sends the decision to the child's bridge as a signed request between towns (`POST /town/guardian/decide`, signed with the
parent's town key, as every `/town/…` request is). The child's bridge accepts the list of requests and a decision only
from a town that is linked with it and whose link says `guardian`:

- unsigned, badly signed, too old, or from a town that isn't linked: `401` (`unsigned`, `bad_signature`, `stale`,
  `unlinked`);
- from a linked town that isn't the child's guardian (a friend): `403 not_guardian`;
- the same signed request again: `401 replayed` (the nonce);
- a request decided before: `409 decided`. A decision is final. Even after a restart, which forgets the nonces, a
  replayed approval can't turn a "no" into a "yes";
- a decision for another town than the request's: `409 mismatch`.

The child can't decide for themselves. The child's app holds the child bridge's app token: the family page's route takes
the family token only (`403 family_only`), and the parent's routes refuse a child's town (`403 child`). The child's app
can only take a request back, which links less.

**Where the guardian link comes from.** The parent's town becomes the child's guardian in one of these ways:

- **the parent's own app**, when both towns are on the parent's node: "🔗 Poveži kot starš · Link as their parent" (the
  app's `lani-town link`). Only the node owner's town (the default learner's) may do this. Another adult's town on the
  same node gets `403 not_owner` and asks the child's parent like any other town. The child's key comes from its bridge
  (`GET /town/hello`); the parent's bridge reads no key from the child's files;
- **the family page**: a "yes" with "👪 To je vas starša (vidi napredek) · This is a parent's town (sees the progress)".
  This is the way for a parent whose town is on another node, or only on a phone;
- **the node**: `lani-town link`, `lani-town guardian`, `lani-town approve --guardian`.

**Two children's towns** link once both parents said yes, in either order.

### 2. A shared invitation opens the app

- **The web link.** An invitation has a web link besides its `lani://` text:
  `https://lani.page/town/#v=1&u=…&c=…&f=…[&r=…]`. The QR code and the share button carry it. The share text is a line
  that says what it is, then the web link on a line of its own, which a messenger makes tappable. The invitation is in
  the **fragment**: a browser never sends a fragment to the server, so the one-time code stays between the two towns.
- **An Android App Link.** The manifest has an `https` filter for `lani.page`, `lani.team` and the Gradle property
  `lani.inviteHost`, with `autoVerify`. `site/.well-known/assetlinks.json` names the SHA-256 of the release certificate
  (read from the public v0.2.27 APK: `5C:97:FE:4D:…:CD:D9:DA`). When the Pages workflow publishes it, Android verifies
  `lani.page` and opens the app for the link.
- **The page** (`site/town/`), for a phone without Lani, or before the link is verified: "✉️ Povabilo v vas · An
  invitation to a village", "Odpri v Lani · Open in Lani" (an `intent://` link: the app if it is there, else the install
  page), "Namesti Lani (Android) · Install Lani (Android)" (the newest GitHub release), and the `lani://town?…` text to
  paste in the app. The second half of each label is in the browser's language (en, de, it). The page sends nothing
  anywhere and has a strict Content-Security-Policy.
- **The app** opens `lani://town?…`, `fluent://town?…` and the web link on Friends, with "🔗 Sprejmi · Accept" filled
  in: "✉️ Povabilo iz povezave je vpisano. Sprejmi ga, če veš, kdo ti ga je poslal. · The invitation from the link is
  filled in. Accept it if you know who sent it." A link never links a town by itself: the learner taps Accept. When the
  app isn't paired yet, the link waits until it is.
- **Every form is read everywhere.** The bridges (`parseInvite`) and the app (`InviteLink.parse`) read the three forms:
  any host, the path `/town`, the parameters in the fragment or the query. The app sends its bridge the `lani://` form,
  so an older bridge takes it too.

### 3. The address in an invitation

- The bridge says who can accept an invitation (`reach` in `POST /towns/invite` and in `GET /towns`' `self`): `node` for
  a loopback address (only the towns of this computer), `tailnet` (a `*.ts.net` name, `100.64.0.0/10`), `network`
  (another name), `relay` (anyone).
- **The app says so before it makes an invitation** that can't work off the node: "🖥️ Samo vasi na tem računalniku lahko
  sprejmejo to povabilo · Only towns on this computer can accept this invitation", why ("Your tutor has no address that
  others reach. For friends elsewhere it needs a relay or its tailnet address: whoever runs the computer sets it."), and
  "✉️ Pokaži kodo · Show the code" for a town of the same computer. With an older bridge, the invitation's dialog says it
  above the QR code. A tailnet address: "📡 Sprejmejo ga lahko vasi v tvojem tailnetu · Towns in your tailnet can accept
  it".
- **`lani-town invite` warns** the same, with the exact tailnet address to give the town when Tailscale knows it
  (`tailscale serve …`, then `--url https://<node>.<tailnet>.ts.net:<port>` or `LANI_TOWN_URL`), and the relay.
- The invitation carries the tailnet URL once `LANI_TOWN_URL` is set, and the relay address once `LANI_RELAY_URL` is set
  (as before).

## How to use it

1. Update the node and the apps (`release-app`): the bridges of both towns need this version for a child's links.
2. **Your own child on your node** (Teo's town is a profile of Jan's node): in your app, "🤝 Prijatelji · Friends" → "👪
   Tvoji otroci · Your children" → "🔗 Poveži kot starš · Link as their parent". Once only.
3. **Teo links a friend.** On the tablet: Friends → "🔗 Sprejmi · Accept" → scan the friend's QR code (or open the link the
   friend sent). Teo's Friends says "⏳ Čaka na starša · Waiting for your parent".
4. **You say yes.** The notification "👪 Teo: nov prijatelj?" opens Friends: the friend's village, "✓ Dovoli · Allow".
   Or on Teo's family page (its link from `lani-town list --profile teo` and the family token), or `lani-town approve`.
5. **A friend elsewhere** needs an address their town reaches: `LANI_TOWN_URL` (your tailnet's) or a relay
   (`LANI_RELAY_URL`). Until then the app says "only towns on this computer".
6. **The web link opens the app** once the Pages workflow has published `site/.well-known/assetlinks.json`. Until then,
   and on a phone without Lani, the page's "Odpri v Lani · Open in Lani" opens it. A new release key needs a new file:
   `companion/bin/lani-assetlinks --apk <the release's APK>` (`--check` compares).

Settings: `LANI_INVITE_URL` (the web link's base, `https://lani.page/town/` by default; `off`: none),
`LANI_TOWN_REQUEST_DAYS` (14), `LANI_TOWN_OPEN_REQUESTS` (20), `LANI_TOWN_NOTIFY_PER_HOUR` (12); the app's Gradle property
`lani.inviteHost` (another host for the App Link filter).

## Tap counts

From Home, Friends is 4 taps today (the village, its scroll, "👥 Prebivalci", "🤝 Prijatelji"); the navigation round
adds a Friends entry on Home and the village HUD (1 tap). The counts below start on Friends.

| Journey | Before | After |
|---|---|---|
| A parent links their child's town (both on the parent's node) | not in the app: `lani-town link default teo` on the PC | "🔗 Poveži kot starš · Link as their parent": **1 tap** |
| The child scans a friend's code, the parent approves | not in the app (the child's 403); on the PC: `lani-town invite --profile teo`, then the friend accepts | the child: "🔗 Sprejmi" → "📷 Skeniraj kodo" (accepted on the scan): **2 taps**; the parent: the notification → "✓ Dovoli": **2 taps** |
| A friend's shared link opens Accept | `lani://` isn't tappable in a messenger: copy the text (2), open Lani (1), Friends (4), "🔗 Sprejmi" (1), paste (2), "🤝 Sprejmi" (1): **11 taps** | tap the link (1), "🤝 Sprejmi" (1): **2 taps** (before the link is verified, or without a published assetlinks.json: the page's "Odpri v Lani" between them, **3 taps**) |
| An invitation only this computer's towns can accept | the QR code shows; a friend elsewhere gets "📡 The other town doesn't answer" after scanning | "✉️ Povabi" → the note first → "✉️ Pokaži kodo": 1 tap more, only for such an invitation |

## How it was tested

- **Bridge smoke** (`companion/bridge/test/smoke/towns.ts`, `town-requests.ts`): 2,801 checks, all passing, 45 of them
  new (after the rebase on main, 2,800: one of the progress checks failed on a date flake of its fixture, `day(-k)` in
  `test/fixtures/learning.ts` subtracting k × 24 h, in the last hour of a day just after the change to winter time). The web link (in the fragment, read back as the `lani://` form, the old `fluent://` and another host's query), who
  can accept an invitation (`reach`: loopback, `*.ts.net`, `100.64/10`, another name, a relay); a child's invitation and
  a child's accept waiting for the parent; the node owner's app linking its child's town (another adult's `403
  not_owner`, the child's `403 child`), the child's request for the parent's town approved by it. Then
  (`town-requests.ts`, Jan the guardian, Mia not linked with Luka): Luka accepts Mia's invitation by its web link (a
  request, nothing linked, Mia's link waiting, `status: pending` on Mia's list, Jan's app told, Luka's app told); an
  unlinked town, a stranger and no signature `401`; Luka's app can't approve (`403 family_only`, `403 child`); Jan's app
  sees it with Mia's village; the family page sees it and says no (Mia told to let go), a yes afterwards `409 decided`;
  Mia accepts Luka's invitation and Jan's app says yes (linked, Mia told, both see each other), a no afterwards `409`;
  strangers' links: a linked non-guardian (Mia) `403 not_guardian` for the list and a decision, the guardian's list
  without the other town's key or address, a signed no, the same request again `401 replayed`, a new yes for it `409`, a
  decision for another town `409 mismatch`, `lani-town requests` and `decline` on the node, the child taking one back;
  two children's towns, each parent's yes in either order; `POST /town/approved` from a town with neither a link nor a
  request `401`; a town taking its waiting link back; no secret in `town-requests.json`. `lani-town invite` warns that
  only this computer's towns can accept a loopback invitation, with the tailnet URL and the relay.
- **Relay smoke** (`test/relay.ts`): 45 checks, all passing: an invitation through the relay says `reach: relay`, its
  web link carries the relay address; the family page's new routes reach a town through the relay. The relay's tests
  (19) pass.
- **Android unit tests**: 3,089 tests, all passing. `TownInvitationsTest` (11): the web link's forms and refusals, the
  relay address kept only when it is the town's own, the text sent to the bridge, a link opening the app
  (`DeepLink.ofUri`, kept in the intent's extra until paired), who can accept from an older bridge's invitation, a
  child's `GET /towns`, a parent's `GET /towns/children`, what accepting came to, the refusals, the `town_link` events'
  banners, notices and link. `FriendsLogicTest` (+8): when a child's app links, who says yes first, a waiting town, a
  child's request lines, the other town's village, the children to link, the share text, the step before an invitation
  only this computer accepts. `PhoneBridgeTest`: the phone's bridge boots `town-requests` too (37 features).
- **`tsc`** (bridge, relay) clean; `tutor-export --check` matches (82 files).
- **Emulator QA** (one run, 348 s on the emulator, no crash; a driver over `companion/bin/qa`'s helpers: a synthetic
  parent, the node's owner, on the dev bridge; a child's town registered as a profile of its node, `dev-town-child.json`;
  a friend's town, `dev-town.json`; `site/` served locally). Before (the app of main): the child's Friends without
  Invite or Accept ("A parent links your town with friends"); a tapped `lani://town` link opens nothing. After: the
  child's Friends with Invite and Accept and who says yes; a friend's link opens Accept filled in; accepted, "⏳ Čaka na
  starša" with the request; the parent's Friends with "👪 Tvoji otroci" and "🔗 Poveži kot starš"; "Povabi" says first that
  only this computer's towns can accept, then the QR code (of the web link) with the note; linked as the parent, the
  child's request with the friend's village (🏘️ 🌾 🪣 🛖×2 🪜 🐝 🪵 · 👥 6); "✓ Dovoli": linked, "✓ Nobeno prijateljstvo ne
  čaka nate"; the child's town then lists the friend, its request approved; a friend's web link (selected as the app's,
  as a verified one is) opens Accept, accepted, the friend linked; the page in Chrome for an unverified link, its "Odpri v
  Lani" opens Accept. The `companion/bin/qa` tool on main defines `l10n` twice (the second hides the first that its
  `setup()` uses): the driver works around it.

## What's left

- **Publishing the verified link.** `site/.well-known/assetlinks.json` goes live with the next Pages deploy of `site/`
  (the workflow runs on a push to main that touches `site/`). Until it is live, a tapped web link opens the page, whose
  "Odpri v Lani · Open in Lani" opens the app (one tap more). Android checks the file when the app is installed or
  updated: an app installed before the file was live verifies at its next update. `adb shell pm get-app-links
  si.lanisce.lani` shows the state on a phone.
- **lani.team** is in the manifest's filter, but nothing is hosted there yet. When the relay and the family page move
  there (the SaaS launch), it needs the same `/.well-known/assetlinks.json` and `/town/` page, and `LANI_INVITE_URL` can
  point there.
- **A debug or QA build** is signed with the debug key, so its links never verify (QA selects them by hand: `pm
  set-app-links-user-selection`). Only the release certificate is in `assetlinks.json`, on purpose.
- **A hosted child's town (no node)** has no family page link in the app yet (relay.md's "What's left"), and no node owner
  links it directly. Its first guardian comes from the family page once the app shows its link, or from a parent's node
  (`lani-town`). Its links wait until then.
- **A town can say it is your child's.** A linked town that sends `POST /town/guardian/notify` makes the parent's bridge
  ask it for requests, and they show under "👪 Tvoji otroci". Nothing links on the parent's side by it (a decision only
  goes to that town), and it takes a link the parent made first. A town's word can't be checked without a third party.
- **No block list.** A declined town can ask again with a new invitation from the child; the parent sees it again.
- **A notice that couldn't be delivered** (the parent's bridge was down) isn't sent again; the request shows on the
  parent's Friends at their next visit.
- **The labels' translations** are machine-written in all four tables (`towns.*` of
  `companion/android/app/src/main/resources/l10n/{sl,en,de,it}.json`, the family page's and the web page's): a native
  check of the Slovene, German and Italian is needed. The Slovene avoids gendered past forms ("Starš se strinja",
  "Sprejeli so povabilo") and possessives of names it can't decline.
- **The QA's invitation journeys** run from a driver in the scratchpad (`qa_invites.py`, over `companion/bin/qa`'s
  helpers), not as `companion/bin/qa` steps yet.
