# Phase D: the relay, and the bridge's relay client

Plan 5, phase D: towns and family without a node. A town with no node (the phone's bridge) and a self-hosted town in a
private tailnet can't reach each other. Both now reach the **relay**, a small service with outgoing HTTPS from the
towns only, and talk through it: visits, trades, friendship, questions, guests, invitations. The family page works
without a node, and the relay keeps the encrypted backups.

Built tonight as code, tests and a container image. It isn't hosted yet.

| Part | Where |
|---|---|
| The relay (Bun, TypeScript, SQLite) | `companion/relay/` (`src/`, `test/`, `Dockerfile`, `deploy/k8s.yaml`) |
| The bridge's relay client | `companion/bridge/src/relay.ts` |
| What changed in the towns code | `src/towns.ts`, `src/features/towns.ts`, `src/cli/town.ts`, `src/bridge.ts`, `src/bridge-service.ts`, `src/family.ts`, `src/feature.ts` |
| The family page | `companion/bridge/web/family/app.js` (its API's address when the relay serves it) |
| The end-to-end smoke | `companion/bridge/test/relay.ts` |

```
 Ana's node (self-hosted)            the relay                         Marta's phone (hosted)
 bridge ──── long poll, answers ───► /v1/mailbox  ◄──── long poll ────── bridge (in the app)
        ──── PUT public, readers ──► /v1/towns/A/public
        ──── GET …/v1/towns/M/town/villagers ──► M's mailbox ──► M's router ──► the answer, signed by M
                                     /family/M/  ◄──── the family's browser (the page, then its API)
                                     /v1/backup  ◄──── the backup client
```

## The API

Every write is signed with the town's own key (below). No accounts: a town's id is its key's fingerprint, the same id
the towns know each other by. Errors are JSON `{error, code}`. Every answer of the relay's own carries
`X-Lani-Relay` (`ok`, `error`, `away`), a town's answer passed on carries `X-Lani-Relay: tunnel`: a bridge never takes
the relay's answer for the town's.

### Health

| Method | Path | |
|---|---|---|
| `GET` | `/health` | `{ok: true, service: "lani-relay", version: 1, time}` |

### A town's public state

| Method | Path | Signed by | |
|---|---|---|---|
| `PUT` | `/v1/towns/{id}/public` | the town | Its last public state (JSON, at most 256 KB), as `GET /town/public` answers it. The relay keeps the town's signature of it. |
| `PUT` | `/v1/towns/{id}/readers` | the town | `{readers: [town ids]}`: the towns that may read it (its links; at most 1,000). |
| `GET` | `/v1/towns/{id}/public` | a reader (or the town) | The state, with `X-Lani-Publisher-Path`, `-Time`, `-Sig` (the town's own signature of its `PUT`) and `X-Lani-Published-At`. `403 not_a_reader`, `404 none`. |
| `GET` | `/v1/towns/{id}/town/hello` | nobody | `{v: 1, id, public_key, relay: true}`, as a town's bridge answers it, from the key the town signed with. `404 unknown_town` until the town polled or published once. |

The reader checks the publisher's signature with the key of its link. So the relay can serve an old state, but never
a made-up one.

### Mailboxes

| Method | Path | Signed by | |
|---|---|---|---|
| `POST` | `/v1/towns/{id}/mail` | any town | `{request: {method: GET\|POST, path: "/town/…", headers, body?: text}, ttl_s?}`: a request for town `id`, kept in its mailbox (14 days, or `ttl_s`) → `202 {id, expires}`. The request inside must be the signer's and for that town (`x-lani-town-from`, `x-lani-town-to`): `403 not_yours`, `400 misaddressed`. |
| `GET` | `/v1/mailbox?wait=<s>&limit=<n>` | the town | Its waiting messages, oldest first: at once, or a long poll of up to 30 s. `{mail: [{id, kind: "http", scope: "town"\|"family", from, at, expires, request: {method, path, headers}, sender: {key, time, sig, path} \| null, body_size, body? (base64, up to 64 KB) \| body_url}]}`. A message handed out is the town's for 60 s (`RELAY_LEASE_MS`); unanswered, it is handed out again. |
| `GET` | `/v1/mail/{mid}/body` | the town | A large body (over 64 KB: a family recording), on its own. |
| `POST` | `/v1/mail/{mid}/reply` | the town | The answer: the body as it is (at most 8 MB), `X-Lani-Reply-Status` and `X-Lani-Reply-Headers` (JSON; only `content-type`, the towns' signatures, `retry-after`, `cache-control`, `content-security-policy`, `x-content-type-options` are kept). The message is gone; the answer waits for its sender (1 day). |
| `POST` | `/v1/mailbox/ack` | the town | `{ids}`: messages gone without an answer. |
| `GET` | `/v1/mail/{mid}/reply?wait=<s>` | the sender | The answer (`X-Lani-Reply-Status`, `-Headers`, the body), or `202 {pending: true}` after the wait. |

`sender` is the sender's own signature of what it sent the relay: the town can check it, and the relay can't make it up.

### The tunnel

Any request to `/v1/towns/{id}/town/…` (`GET` or `POST`, at most 64 KB) is a bridge-to-bridge request for town `id`.
The relay puts it in the town's mailbox, holds the sender until the town answers, and answers with the town's own
answer (status, the town's signature header, the body). So a bridge reaches a town that is only on the relay by its URL
`<relay>/v1/towns/{id}`, as it reaches any other: the towns code sends its existing requests (visits, friendship,
trades, questions, guests, the join of an invitation) unchanged.

- The request is signed twice with the sender's key: inside for the town (`x-lani-town-*`, checked by the town as
  today), outside for the relay (`X-Lani-*`). The inside must be the outside's signer (`403 not_yours`) and for that town
  (`400 misaddressed`).
- `X-Lani-Relay-Wait` (ms, 500 to 25,000; default 8,000): how long the sender waits. The bridge asks for its own timeout
  less 0.7 s, so the relay answers first.
- A town that isn't polling (none in the last 45 s, `RELAY_ONLINE_MS`): `504 away` at once. One that doesn't answer in
  time: `504 away`, and the request is taken back while the town hasn't fetched it (`504 timeout` when it has: its late
  answer is kept for `GET /v1/mail/{mid}/reply`).

### The family page

| Method | Path | |
|---|---|---|
| `PUT` | `/v1/towns/{id}/family` | Signed by the town: `{token_sha256: <hex> \| null}`. The family token's SHA-256 (never the token): the page goes through the relay for it; `null` turns it off. |
| `GET` | `/family/{id}/` (and `app.js`, `style.css`) | The page, as the bridge serves it (the same files, the same CSP), its script and style from under its path. |
| any | `/v1/family/{id}/…` | Its API, `Authorization: Bearer <family token>`: only what the family token does on a node (below). Tunnelled to the town as `scope: "family"`, with the token, which the town checks again. |

The family's routes: `GET`/`PUT family/api/settings`, `GET family/api/words`, `GET`/`POST family/api/challenges`,
`POST audio` (a recording, at most 2 MB + 64 KB), `GET audio/index`, `GET`/`DELETE audio/file/<file>`. The learner's
answer (`POST family/api/challenges/:id/answer`) is the app's: `403`. JSON bodies at most 16 KB. `401` for a wrong token,
`404 no_family` for a town whose page isn't on the relay. 60 requests a minute per town (`RELAY_FAMILY_RATE_PER_MIN`).

While the town is away, a read is `504 away`; a challenge or a recording waits in its mailbox (`202 {queued: true}`,
14 days) and reaches the town when it is back. The page says so ("📮 Shranjeno · Saved: … when their town is back
online").

CORS: none is needed for the relay's own page. A page served elsewhere (lani.team) calls the API from an origin in
`RELAY_FAMILY_ORIGINS`; preflights from other origins are `403`.

### Backup

Exactly the shared contract, scoped to the signing key:

| Method | Path | |
|---|---|---|
| `PUT` | `/v1/backup/{name}` | The body: the encrypted blob, at most 50 MB → `{name, size, time}` |
| `GET` | `/v1/backup/{name}` | The blob (`application/octet-stream`, `X-Lani-Backup-Time`, `X-Lani-Backup-Sha256`) |
| `GET` | `/v1/backup` | `[{name, size, time}]`, by name. `time`: unix ms of its `PUT`. |
| `DELETE` | `/v1/backup/{name}` | `{ok: true}` |

A name is 1 to 128 of `A-Z a-z 0-9 . _ -`, starting with a letter or digit (`400 bad_name`). Per key at most 100 backups
and 512 MB together (`507 quota`). A `PUT` with the same name replaces it. The body is streamed to a temp file and
hashed on the way; the signature over that hash is checked before the file takes the name.

## Signed requests

The shared contract: `X-Lani-Key` (the town key, base64 SPKI DER, EC P-256), `X-Lani-Time` (unix ms), `X-Lani-Sig`
(base64 DER ECDSA, SHA-256) over `${METHOD}\n${path with query}\n${time}\n${hex SHA-256 of the body}`.

- A time more than 5 minutes off: `401 stale`. Unsigned: `401 unsigned`; not a P-256 key: `401 bad_key`; a wrong
  signature: `401 bad_signature`.
- Each signed text is taken once (`401 replayed`), remembered in the database for 6 minutes, so a restart doesn't
  forget it. The relay remembers the text, not the signature: an ECDSA signature can be changed into another valid one.
  So two requests must differ: the bridge's `relayHeaders` takes the clock, or one millisecond more than its last time.
- The path is the one the relay sees: it sits at the root of its host (a proxy that strips a prefix breaks every
  signature).

## The security model

- **Who is who:** a town is its key. The relay checks the signature of every write, and the town signs as itself for
  its own routes (its mailbox, its state, its readers, its family hash, its answers): `403 not_yours` otherwise. The
  first time a town signs as itself, the relay keeps its key; that key answers `hello` for it.
- **End to end between towns:** the relay can't make up or change a request between towns, nor an answer. The request
  inside is signed by the sending town (nonce, time, from, to, the body's hash) and checked by the receiving town with
  the key of its link, as without the relay. The answer is signed by the town for that request's nonce, and checked by
  the asking town. The public state carries the town's own signature. What the relay *can* do: drop or delay things,
  serve an older state, see who talks to whom, and see the content (TLS ends at the relay).
- **Invitations:** `hello` through the relay answers with the key the town registered, and the accepting town checks
  it against the invitation's fingerprint, as it does today. A relay can't put its own key in.
- **The app's routes never come through:** the bridge answers what comes through the relay with its own router, but
  with a role of its own: another town (only `/town/…`, only the towns' headers) or the family page (only `/family/api/…`
  and `/audio…`, only its token and the body's type). Never the app token, a paired phone's token, or the app's routes.
- **The family token:** the relay keeps its SHA-256 only, checks it in constant time, and passes the token on to the
  town, which checks it again. The token passes the relay (as it passes a proxy), and sits in a challenge or a recording
  that waits for an away town (deleted when it arrives, or after 14 days).
- **What the relay stores:** each town's key, when it was seen, its readers and family hash; its public state; mail and
  answers until they're picked up or run out; the backups' ciphertext. Nothing else: no accounts, no names but what the
  public state shows (the village's name, the learner's first name), no tokens.
- **Limits:** per key 600 signed requests a minute (burst 120), per address 1,200 a minute, per town's family page 60.
  A mailbox holds at most 500 messages and 64 MB, a sender's waiting mail at most 32 MB, a town's waiting answers at
  most 64 MB. Mail runs out after 14 days, answers after 1 day.
- **Not yet:** the relay trusts nobody's word that a town is who it says beyond its key, so anyone can make a key and
  send mail: the limits are the protection against floods. There is no abuse list, no proof of work, and no way to
  block a key yet.

## The bridge

### Settings

| Setting | | |
|---|---|---|
| `LANI_RELAY_URL` | this machine's | The relay, e.g. `https://relay.lani.team`. Unset: off, and the node behaves exactly as before. |
| `LANI_TOWN_URL=relay` | the learner's | A town with no address of its own (the phone's bridge): its URL is its relay address, and the family page goes through the relay. |
| `LANI_RELAY_FAMILY` | this machine's | `on` or `off`: the family page through the relay. Unset: on only with `LANI_TOWN_URL=relay`. A node's family page stays on its tailnet unless it is turned on. |
| `LANI_RELAY_POLL_S`, `LANI_RELAY_PUBLISH_MS` | | The long poll (25 s) and how often it publishes what changed (2 minutes). |

### What it does

With `LANI_RELAY_URL` set, the bridge that serves the app (the service, or the tutor's bridge in the classic mode)
starts the relay client (`RelayClient`, `src/relay.ts`) once its HTTP API is up:

- **It publishes** its public state (built as `GET /town/public` builds it) whenever it changed, at least every 6 hours,
  its readers (its links) when they change (at once after a link comes or goes), and the family token's hash (or
  `null`).
- **It polls its mailbox** (a long poll) and answers each message on its own, through the bridge's router, so a request
  that asks another town back (the market's rates ask the friendship) doesn't hold up the mailbox. At most 16 at once;
  each gets 30 s.
- **Its address on the relay** is `<LANI_RELAY_URL>/v1/towns/<its id>`. Its invitations carry it as `r=` beside its own
  URL (`lani://town?v=1&u=…&c=…&f=…&r=…`; a bridge from before the relay reads past it), its answer to a join carries it
  (`town.relay`), and `GET /towns` shows it (`self.relay`).

### Reaching another town

`townRequest` (towns.ts) knows two ways to a town: its URL, and its relay address (`TownLink.relay`, new in
`towns.json`). It tries the URL first; when that doesn't answer, the relay; a way that didn't answer is tried last for
10 minutes. Neighbours on one tailnet keep the direct path. A request to a relay address is signed for the relay too,
and an answer that isn't the town's own (`X-Lani-Relay: away`, or the relay's refusal) is `unreachable`, never a
refusal of the town: no cache is dropped, and the stale fallbacks work as for a town that is off.

Which way a link keeps:
- **Accepting an invitation:** the inviter's URL first (`hello`). When it doesn't answer, or answers as another town (a
  `127.0.0.1` URL of the inviter's own machine reaches this machine's), its relay address: then the link's URL is the
  relay address, so a direct request never lands on a wrong town.
- **A join that came through the relay:** the joining town is reached through its relay address.
- **A direct join:** its URL, and its relay address beside it.

**A town that is away:** its public state is the one it last published on the relay (`GET /towns`, `GET
/towns/:id/public`, with `fetched_at` its time), checked with the town's key. A visit's people and places come from the
cache, as for a town that is off.

### The family page without a node

The bridge registers the family token's hash when the family page goes through the relay. The page's link is
`<LANI_RELAY_URL>/family/<town id>/#t=<the family token>`; the town id is in `lani-town list` and in the bridge's log
("the family page goes through the relay: …"). The page itself (`web/family/app.js`) calls its API at
`/v1/family/<town id>/…` when the relay serves it, and at `/family/api/…` when the bridge does.

### On the phone

The client uses only `node:crypto` and `fetch` (no Bun APIs, no `AbortSignal.any`), so it runs in QuickJS as the rest of
the bridge does. The phone's runtime builds the bridge with `buildBridge` (which creates the client and gives it the
router, when `LANI_RELAY_URL` is set) and then calls `ctx.relay?.start()`, as `startBridge` and the service do once the
API is up. It sets `LANI_RELAY_URL` and `LANI_TOWN_URL=relay`.

## Operations

```bash
cd companion/relay
bun install                      # the type definitions only: no dependencies at run time
bun src/index.ts                 # RELAY_PORT=8080, RELAY_DATA_DIR=./data
bun test                         # the unit tests (in process, no port)
bunx tsc --noEmit -p .

# the image (the build context is companion/: the family page's files are the bridge's)
docker build -f companion/relay/Dockerfile -t lani-relay companion
docker run -p 8080:8080 -v lani-relay:/data lani-relay
```

| Setting | Default | |
|---|---|---|
| `RELAY_HOST`, `RELAY_PORT` | `0.0.0.0`, `8080` | |
| `RELAY_DATA_DIR` | `./data` (`/data` in the image) | `relay.sqlite` (WAL) and `backup/<key>/<name>` |
| `RELAY_WEB_DIR` | `../bridge/web/family` | The family page's files (`/app/web/family` in the image) |
| `RELAY_TRUST_PROXY` | off | `1`: the client's address from `X-Forwarded-For` (behind the ingress) |
| `RELAY_FAMILY_ORIGINS` | none | Origins that may call the family API (CORS), comma-separated |
| `RELAY_RATE_PER_MIN`, `RELAY_RATE_BURST` | 600, 120 | Signed requests per key |
| `RELAY_IP_RATE_PER_MIN` | 1,200 | Requests per address |
| `RELAY_FAMILY_RATE_PER_MIN` | 60 | The family page's requests per town |
| `RELAY_MAIL_DAYS` | 14 | Mail's retention |
| `RELAY_MAILBOX_MAX`, `RELAY_MAILBOX_BYTES` | 500, 64 MB | One mailbox |
| `RELAY_SENDER_BYTES` | 32 MB | One sender's waiting mail |
| `RELAY_MAIL_MAX_BYTES`, `RELAY_INLINE_MAX_BYTES` | 64 KB, 64 KB | A town's request; a body listed inline |
| `RELAY_REPLY_MAX_BYTES`, `RELAY_REPLY_BYTES`, `RELAY_REPLY_HOURS` | 8 MB, 64 MB, 24 | An answer; a town's waiting answers; their retention |
| `RELAY_FAMILY_AUDIO_MAX_BYTES`, `RELAY_FAMILY_JSON_MAX_BYTES` | 2 MB + 64 KB, 16 KB | The family page's bodies |
| `RELAY_PUBLIC_MAX_BYTES` | 256 KB | A public state |
| `RELAY_TUNNEL_WAIT_MS`, `RELAY_TUNNEL_WAIT_MAX_MS` | 8 s, 25 s | A tunnelled request's wait |
| `RELAY_POLL_WAIT_MAX_MS`, `RELAY_ONLINE_MS`, `RELAY_LEASE_MS` | 30 s, 45 s, 60 s | The long poll; "there"; a message's lease |
| `RELAY_BACKUP_MAX_BYTES`, `RELAY_BACKUP_QUOTA_BYTES`, `RELAY_BACKUP_MAX_COUNT` | 50 MB, 512 MB, 100 | Backups |

- **One process, one replica.** The rate limits, the long polls and the senders waiting for answers are in memory;
  the replays, the mail and everything else in SQLite. A second replica would need those shared (Redis, or one
  database server): not before there are many towns.
- **Its own backup:** `relay.sqlite` (with `sqlite3 .backup`, it is in WAL mode) and `backup/`. Losing the mail loses
  only what was on its way; losing `towns` makes each town known again at its next poll.
- **Logs:** stderr, one line at the start, one per retention sweep that removed something, and internal errors with
  their stack. Nothing per request, no bodies, no tokens.

### Hosting it later

glas runs on Jan's lok8s cluster; the relay fits beside it:
1. Build and push the image to the cluster's registry (`docker build -f companion/relay/Dockerfile -t <registry>/lani-relay:<tag> companion`).
2. Apply `companion/relay/deploy/k8s.yaml` (a PVC of 5 Gi, one replica with `Recreate`, a Service), with the image set.
3. An ingress with TLS for its host (e.g. `relay.lani.team`), at the root, with `RELAY_TRUST_PROXY=1`. Long polls need
   the ingress's read timeout above 30 s (nginx: `proxy-read-timeout: "60"`), and a body limit of 51 MB for backups
   (`proxy-body-size: 51m`).
4. Set `LANI_RELAY_URL` on the nodes that should use it, and in the phone's runtime.

## How it was tested

- **The relay's unit tests** (`companion/relay/test/relay.test.ts`, `bun test`): 15 tests, 115 checks, in process with
  a clock the tests move and fake towns that poll and answer. Signatures (unsigned, a bad key, an RSA key, stale, a
  changed body, a replay), the rate limit, the public state and its readers (the publisher's signature checked), hello,
  mail (deliver, long poll, the sender's signature, lease, answer, the sender's fetch, ack, a full mailbox, retention),
  the tunnel (the town's answer and headers, a large answer and a large body as blobs, a refusal passed on, a stranger's
  inner request, away at once, no answer in time and taken back), the family page (the page, no family, a wrong token,
  the learner's answer route refused, the token passed on, a recording, away: a read refused, a challenge queued), CORS,
  backups (put, get, list, delete, another key's view, a changed body, a replay, too large, the quota, a replaced one,
  bad names).
- **The end-to-end smoke** (`companion/bridge/test/relay.ts`): the relay and three bridges, 33 checks, all through the
  relay: Ana's town is self-hosted with a URL the others can't reach, Marta's (the QA's fixture town) and Cene's are
  hosted (`LANI_TOWN_URL=relay`). Linking by invitation (Ana's URL fails, her relay address is taken; two hosted towns),
  the public state both ways, a visit (98 KB of people, the places, the requests, the market), a request done, a gift with
  its message (the tutor told), a trade (once, however often sent; and between the two hosted towns), a question and its
  answer, the friendship both agree on, the family page (the page, settings, a wrong token, the learner's route refused,
  a challenge, a 100 KB recording up and down byte for byte, deleted; a node's page not on the relay), a backup (1 MB
  put, get, list, delete, another key, a replay); then Marta's town goes away (its published state served, a read `504`,
  a challenge queued) and comes back (the challenge arrives, the live state again).

  ```bash
  cd companion/bridge
  LANI_RELAY_SMOKE_PORT=<port> bun test/relay.ts   # the relay on it, the bridges on the next three (default 18870)
  ```
- **The bridge's smoke** stays green with the relay off (`LANI_RELAY_URL` unset): the towns, friendship, questions,
  guests and family sections unchanged.
- **The image** builds (103 MB, `oven/bun:1.3-alpine`), runs as the `bun` user, and answers `/health` and the family
  page.

## What's left

- **Hosting it** (above), and the phone's runtime calling `ctx.relay?.start()`.
- **The app:** the family page's link for a hosted town (today: the log line and `lani-town list`); showing that a town
  is on the relay or away.
- **Queued writes between towns:** the tunnel only reaches a town that is there. A gift or a question to a sleeping
  phone fails as "unreachable", as to a node that is off, and the app retries. The mailbox can keep them (`POST
  /v1/towns/{id}/mail`), but the receiving town checks the time inside (5 minutes): delivering later needs the relay's
  receipt time to count for it, and the towns code a "pending" state for a write that waits.
- **A visit to an away town:** its people and places come from the visitor's cache only. A town could publish its
  curated people and places on the relay too (they're the same for every visitor).
- **The family recordings** stay on the town (the phone). The plan's "recordings in glas's private namespace" would
  keep them in the SaaS: then the relay's upload becomes a glas upload.
- **Abuse:** blocking a key or an address, and a proof of work or an invitation for the first contact.
- **More than one replica** (above).
- **The backup client** is the backup agent's (`/v1/backup` is ready).
