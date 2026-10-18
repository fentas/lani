# The bridge on the phone (plan 5, the hosted mode's runtime)

Built 2026-10-18, branch `phone-runtime`. The bridge's TypeScript runs in the app, in QuickJS (quickjs-kt, as
[phase 0's check 4](../05-phase-0.md#check-4-the-validators-in-a-js-engine-2026-10-14) chose), and serves its routes
in-process: the same stores, features, routes, tools, channel instructions and guide as a node's. Nothing in the app
uses it yet: the screens and the setup wire the hosted mode in after this, through the Kotlin API below. The
self-hosted mode (a node and Claude Code) is unchanged.

## The design as built

```
the app (Kotlin)                                   QuickJS, one runtime on its own thread
─────────────────────────────────────────          ──────────────────────────────────────────────────
PhoneBridge.handle(Request) ──► __laniPhone.handle(id) ──► router (http.ts) ──► the features' routes
          ◄── Response (or a Flow of parts) ◄── host.respond / host.chunk / host.end
PhoneBridge.callTool(name, args) ──► __laniPhone.call(id) ──► the tools (channel_guide, reply, …)
          ◄── ToolResult ◄── host.toolDone
PhoneBridge.notifications ◄── host.channel(content, meta) ◄── PhoneChannel.notify (the same as Claude Code's)
the host: __lani (fs on roots, crypto, fetch over OkHttp, timers, clock, log, env) ◄── node:* shims, Bun, fetch …
```

- **One builder.** `companion/bridge/src/build.ts` builds the bridge (the context, the features, the guide, the
  tools) on a `Platform`: the config, the channel, the voices, the recordings' leveller, what runs at exit. A node's
  platform is `bridge.ts`'s (`nodePlatform`: `loadConfig`, the MCP `Channel`, ElevenLabs, Gepard and the SQLite voice
  store, ffmpeg); the phone's is `src/phone/entry.ts`'s. `appApi` (the routes with `/health`, rendered for the learner,
  and who a request is) is shared too. Ctx.channel is a `ChannelPort` (notify, permission); what an event looks like
  (`channelEvent`, `withHint`) is in `src/tool.ts`, with the tools' types and `ok`/`fail`, so the phone has them without
  the MCP SDK.
- **The phone's channel** (`src/phone/channel.ts`): its events go to the host (`host.channel(content, meta)`) with the
  content and meta Claude Code's channel gets: the tags made harmless, the reply line, the guide asked for on the first
  conversation's event of a session (`PhoneBridge.newSession()` starts a new one). No tool approvals: `POST
  /permission` answers 503, as the service does without a session.
- **No voices** (`src/phone/voice.ts`): a voice store with no engine and no clips. `/voice/say` answers 204 (the app
  speaks with the phone's text-to-speech), `/voice/prepare` gives `url: null`, a re-record fails with "no voices on
  this device", `voice_status` says so, a publish's texts are let go. The voice profiles (one voice per person: their
  archetype, pitch and pace) work as on a node, their table in `<data>/app/voice-profiles.json`
  (`profiles-table.ts`); no voice is designed.
- **The app's token.** The host passes `LANI_BRIDGE_TOKEN` (random per `PhoneBridge`, or given), and `handle` adds it to
  every request that has no Authorization header: the router accepts the app as a node's does (a wrong token is a 401).
- **The event loop is the host's.** QuickJS runs only while the app calls into it, and every call is a short
  evaluation. What waits, waits in Kotlin: a timer (`setTimeout`, `setInterval`) on the runtime's thread, a fetch in
  OkHttp, a stream's next part; when it's time, Kotlin calls `__laniLoop.timer(id)` or `__laniLoop.fetched(id)`, and
  QuickJS runs the promise jobs that made. So a long event stream, a request waiting on a fetch, a tool call and the
  features' timers interleave as on a node, and nothing blocks the thread. (quickjs-kt's own async bindings would hold
  one evaluation open until every job it started ends: a 60-second interval would never let it return.)
- **Requests.** `__laniPhone.handle(id)` reads the request from the host (`host.request`, `host.requestBody`), runs the
  router and answers through `host.respond(id, head, text | bytes)`. A text body crosses as text, bytes as bytes (no
  encoding in JavaScript). An answer whose body is a stream (`/events`) answers its head at once and then each part
  (`host.chunk`), until it ends (`host.end`) or the app lets go (the Flow's collector stops: `__laniPhone.cancel(id)`
  aborts the request's signal, as a closed connection would).
- **Features' start()** runs once, after the bundle's boot: the rhythm's timers, the scenes' variants, the stories,
  the dictionary download (only for a language the content lacks), the dev data's pruning. The events are kept in
  `<data>/app/events.json` across the app's restarts, as the bridge service keeps them.

### The learner-data scripts

A node runs `.claude/hooks/{read-db,update-db,card,validate-data}.py` with Python (`config.ts spawn`, through
`Bun.spawnSync`). On the phone, `Bun.spawnSync`, `Bun.spawn` and `child_process.spawnSync` go to a dispatcher
(`phone/shims/scripts.ts`):

- `python3 .claude/hooks/<name>.py …` runs the function registered for `<name>` (`registerScript(name, run)`, with
  the db port's contract `run(argv, stdin, env) → {status, stdout, stderr}`). **The db port** (`companion/bridge/src/db/
  cli.ts`, branch db-port) is registered in one marked spot of `src/phone/entry.ts` ("WIRE THE DB PORT HERE") when it
  is merged; until then such a script answers status 127, "<name>.py is not on this device", and the routes that need
  it fail with a 500 (`/state`, `/reviews`, `POST /words`, `/packs/:id/learn` and the others that read or write the
  databases: [below](#what-isnt-on-the-phone-yet)).
- `git …` answers as outside a repository (status 128, "fatal: not a git repository"): the bridge leaves the data's
  git alone (`ignore.ts`, the dev data's `.gitignore`).
- Any other program isn't there: ENOENT, as on a machine without it.

## The bundle: `companion/bridge/phone/`

`bun phone/build.ts [--out <dir>] [--debug]` bundles `src/phone/entry.ts` into one script, `bridge-phone.js` (an IIFE,
target browser). **829.7 KB minified, 281.7 KB gzipped** (1.48 MB unminified); it builds in under a second.

- **Aliases** (`phone/shims/`, over the host `globalThis.__lani`, `shims/host.ts`): `node:fs` (and `fs/promises`),
  `path`, `os`, `crypto`, `util`, `zlib`, `url`, `child_process`; `bun:sqlite` is a stub that says why. Anything else of
  Node's or Bun's (`node:net`: the control socket), the MCP SDK, `@clack`, `qrcode-terminal`: the build fails, so
  nothing of the node's own parts slips into a module the phone shares. zod's locales: English only.
- `import.meta.dir`/`.path` in the repository's modules (`cast.ts`'s voice cast, `config.ts`, `companion/prints`'
  fonts) become paths under the phone's project directory.
- The biggest parts: `voice.ts` (100 KB unminified, most of it the node's voice store, kept though unused: Bun's tree
  shaking keeps classes), zod (about 350 KB), the cultures, the scenes, the towns, the prints.

**Polyfills: only what the bridge uses.** QuickJS (Bellard's, 2026-06-04, in quickjs-kt 1.0.15) has the language up to
iterator helpers, Set methods, `Object.groupBy`, `Promise.withResolvers`, the `v` regex flag, `String.normalize`; it has
no `console`, timers, `queueMicrotask`, `structuredClone`, `performance`, `URL`, `TextEncoder`, `fetch` or `Intl`. The
prelude (`shims/prelude.ts`) installs, before any of the bridge's modules runs:

| | How |
|---|---|
| `process` | env from the host; cwd the project dir; `on`/`emit` (`exit`); `exit` throws (the app's process never ends) |
| `console` | to the host's log; the bridge's `[lani-bridge]` lines as info |
| `Buffer` | a `Uint8Array` with what the bridge uses (from/toString in utf8, hex, base64, base64url, latin1; concat, equals, `readUInt32BE`, `slice` sharing memory); UTF-8 and base64 by the host |
| `URL`, `URLSearchParams` | WHATWG's parsing for http(s) and file: URLs, relative references, percent-encoding |
| `Headers`, `Request`, `Response` | enough for the router and the features: text, bytes, a stream or a file as the body, `Response.json` as Bun's |
| `ReadableStream` | start/pull/cancel, a reader, `desiredSize` (the event stream's back pressure), async iteration |
| `AbortController`, `AbortSignal` | `timeout` (passed to the host as the fetch's timeout), `abort`, `any` |
| `TextEncoder`, `TextDecoder` | UTF-8 by the host |
| timers, `queueMicrotask`, `fetch` | the host's loop (above); fetch over OkHttp, a whole answer at once, `redirect: 'manual'` |
| `Bun` | `file` (a body or read), `sleep`, `sleepSync`, `which` → null, `spawnSync`/`spawn` → the dispatcher; `serve` and `listen` say they aren't there |
| `crypto` (web), `structuredClone`, `performance` | `randomUUID`/`getRandomValues` by the host; JSON's clone (the bridge never calls it); a clock |
| ICU's answers | `toLocaleDateString('sv')` (the local YYYY-MM-DD the bridge reads "today" by: QuickJS gives "10/18/2026"); `localeCompare` by the host's collator (ICU: QuickJS compares code points, so "č" would sort after "z"), two plain ids (`[a-z0-9-]`) compared in JavaScript, which orders them alike |

**Generated at build time, not committed.** The app's Gradle task `phoneBridgeBundle` runs bun and puts the bundle in
the APK's assets (`phone/bridge-phone.js`). A committed bundle (as `companion/tutor` is, with a freshness check) would
add a megabyte of generated code to the repository's history at every change of the bridge, which changes several
times a day. So bun 1.3.13 (the bridge CI's version, the baseline build, pinned by its SHA-256) is in the build image
(`companion/android/docker/Dockerfile`), which CI's Android job and the release workflow build in; a local Gradle build
uses the `bun` on the PATH (or `LANI_BUN`, `-Plani.bun`). The task installs the bridge's packages first when they
aren't there (`bun install --frozen-lockfile`: bun.lock pins them, so the bundle is the same everywhere). The smoke
checks that the bundle builds (section `phone`), so a shared module that starts importing something the phone hasn't
fails the bridge's own CI.

## The Kotlin host: `si.lanisce.lani.phone`

```kotlin
class PhoneBridge(config: PhoneBridge.Config) : Closeable {
    class Config(projectDir: File, dataDir: File, home: File,
                 roots: List<HostFs.Root> = [home rw, projectDir ro], bundle: () -> String,
                 env: Map<String, String> = {}, log: (level: String, text: String) -> Unit = {},
                 memoryLimit: Long = 384 MB, maxStack: Long = 4 MB, threadStack: Long = 16 MB,
                 evaluationTimeoutMs: Long = 60_000, requestTimeoutMs: Long = 120_000,
                 http: OkHttpClient = OkHttpClient(), token: String = randomToken(), bytecodeDir: File? = null)
    class Request(method: String, url: String, headers: List<Pair<String, String>> = [], body: ByteArray? = null)
        // Request.get(url), Request.json(method, url, json); url: a path ("/lookup?w=hiša") or absolute
    class Response(status: Int, headers: List<Pair<String, String>>, body: ByteArray, stream: Flow<ByteArray>?)
        // .text, .header(name); stream: an event stream's parts (collected once; cancelling it ends the stream)
    data class ChannelEvent(content: String, meta: Map<String, String>)
    data class ToolResult(text: String, isError: Boolean, json: String)
    data class Boot(loadMs: Double, bootMs: Double, fromBytecode: Boolean, summary: String)

    val notifications: Flow<ChannelEvent>          // the channel's events, held until collected (one collector)
    suspend fun start(): Boot                      // once; every call below starts it first
    suspend fun handle(request: Request): Response
    suspend fun callTool(name: String, args: String = "{}"): ToolResult
    suspend fun tools(): String                    // JSON [{name, description, inputSchema}], as tools/list
    suspend fun instructions(): String             // the MCP instructions (the rules that fit Claude Code's limit)
    suspend fun guide(): String                    // channel_guide's text
    suspend fun newSession()                       // the next conversation's event asks for the guide again
    suspend fun pending(): String                  // JSON {timers, fetches, streams}
    suspend fun memory(gc: Boolean = false): MemoryUsage
    fun interrupt()                                // stops the evaluation running now
    fun stop()                                     // = close(): the events kept, streams closed, the runtime closed
    fun environment(): Map<String, String>
}
object AppBridge { fun create(context, dataDir = <filesDir>/lani/data, env, log): Pair<PhoneBridge, Unpacker.Result> }
class Unpacker(open: (String) -> InputStream, version: String) { fun unpack(dir: File): Result }  // Unpacker.of(context)
```

- **The runtime:** quickjs-kt 1.0.15 (`io.github.dokar3:quickjs-kt`; the unit tests take its JVM build,
  `quickjs-kt-jvm`, with the host's library, in place of the Android one). One runtime on a thread of its own (16 MB of
  stack, QuickJS's limit 4 MB), made on first use; the memory limit (384 MB), an evaluation's timeout (60 s: a loop
  gone wrong is interrupted) and `interrupt()`. The bundle is evaluated from source, or from its bytecode kept in
  `bytecodeDir` by the source's hash (`AppBridge`: `<filesDir>/lani/cache/bytecode`; a new bundle compiles again).
- **R8:** `-keep class com.dokar.quickjs.** { *; }` (its JNI finds its classes and methods by name: the bindings, the
  MemoryUsage and the exceptions it makes); the AAR's own rules keep only some.
- **Files** (`HostFs`): only under the roots; a path is made canonical first (`..` and symlinks), and the most
  specific root decides. On the phone: `<filesDir>/lani` writable, `<filesDir>/lani/project` read only; in the JVM
  tests a temp dir and the repository (read only). Outside: EACCES; a write into a read-only root: EROFS. The calls:
  read and write text and bytes (append, exclusive), exists, stat (with fractional milliseconds), readdir (in the
  directory's order, as Bun's readdirSync: node's sorts), mkdir (-p), rename (replacing), unlink, rmdir, rm (-r,
  force), copy (a hard link is a copy that fails on EEXIST), realpath, chmod (a check only). Errors are node's: code,
  errno, syscall, path, message.
- **Crypto** (`HostCrypto`): sha256, sha1, sha512, md5 and their HMACs; random bytes and UUIDs; the bridge's EC P-256
  key exactly as the bridge uses node's: `generateKeyPairSync('ec', P-256)` → PKCS#8 PEM, `createPrivateKey(pem)`,
  `createPublicKey(private)` → SPKI DER (Java reads a private key without its point: Q = d·G is computed once per
  key), `sign('sha256', …)` and `verify` with DER signatures (Java's SHA256withECDSA, the format node and the app
  use), `timingSafeEqual` in JavaScript.
- **Network:** fetch through OkHttp (asynchronous; a call's timeout from `AbortSignal.timeout`; manual redirects;
  cancelled with its signal).
- **Text:** UTF-8, base64 as node reads it (either alphabet, padding or not), gzip, the collator (Android's
  `android.icu`, ICU4J in the JVM tests when it's on the classpath, else Java's).
- **Time:** QuickJS's own clock and local time (libc's, the device's zone; the same offset as Java's in the tests),
  `TZ` in the environment; the host has `tz()` and `tzOffset(ms)` too.
- **Environment** (`environment()`): `HOME=<filesDir>/lani` and its `tmp`, `config`, `cache`, `state`, `share`
  (XDG), `CLAUDE_PROJECT_DIR`, `LANI_DATA_DIR`, `LANI_BRIDGE_TOKEN`, `LANI_VOICE_DESIGN=off`, `TZ`, then
  `Config.env`. No lani.env is there, so none is read.
- **Log:** to the app's log (`AppBridge.androidLog`: tag `LaniBridge`, long texts in parts).

## Content on the phone

What the bridge reads of the repository (traced through `build.ts` and every feature): the culture packs
(`companion/cultures/**`: each pack's `culture.json`, world, quests, festivals, surprises, chest, projects, events,
people, chronicle, sky, arrivals, its villagers, readings, stories, scenes, packs, project steps, celebrations and
voice cast), `companion/voice-cast.json`, the modules, packs, grammar book, cheat sheet's cards, course, drills,
scenarios and scenes of `companion/`, the dictionary and forms (`companion/lexicon/*.json`), «Vidim, vidim»'s clues
(`companion/ispy`, for voice_status), the printed sheets' `sheet.css`, `fit.js` and fonts (`site/fonts/*.woff2`), the
family page (`companion/bridge/web/family`), and the learner data's templates (`data-examples/*-template.json`). Not
read at run time: `companion/tutor` (the app bundles it), `LEARNING_SYSTEM.md`, `docs/`, `.claude/skills`.

- **In the APK:** the Gradle task `phoneProject` copies them into the assets (`phone/project/`, the repository's
  layout) with `phone/project.json`: their paths and sizes, and a hash of them all. 549 files, 11.7 MB (4.1 MB
  compressed in the APK).
- **On the phone:** `Unpacker` copies them to `<filesDir>/lani/project` when the app's version (name and code) or the
  content's hash differs from the last copy's (`.lani-unpacked`), into `project.new` first, which then takes the old
  one's place, so the bridge never reads half of one: 181 ms on the emulator.

## Tests

On the JVM (quickjs-kt's JVM build, the same QuickJS), with the real bundle (`phoneBridgeBundle`), the learner data
from data-examples' templates and the repository as the project (read only):

- `PhoneBridgeTest` (20; 4 ignored until the db port is wired): `/health`; a wrong token's 401; `GET /packs` and a
  pack; `/modules`, `/grammar`; `/lookup?w=hiša`, `/forms`; `GET /game`, `PUT /game` (and its 409 for a stale rev);
  `/state` fails cleanly without the scripts; `POST /message` (the channel's event: its meta, the text, the reply
  line, the guide asked for once, a `</channel>` made harmless); `GET /events` (an event the reply tool puts there
  arrives on the stream; the stream ends with the collector); the tools (`channel_guide` first, the same names, order,
  descriptions and schemas as `companion/tutor/tools`, exported from a node), `get_village`, `publish_gloss` then a
  lookup that shows it, an unknown tool; voice_status's "no voices on this device", `/voice/say`'s 204 and the voice
  profiles; the instructions and the guide; the bridge key (PKCS#8, sign and verify, Q = d·G against Java's own); the
  file roots (EACCES, EROFS, ENOENT, EEXIST); the bundle from bytecode. Ignored, with the reason: `GET /state`,
  `POST /reviews`, `POST /words`, `POST /packs/:id/learn` (they need read-db.py and update-db.py: the db port).
- `PhoneBridgeNodeTest` (23): a node bridge (`bun src/index.ts`, classic, its MCP channel initialized over stdio) on
  127.0.0.1:19110 on a copy of the same data, against the phone's: `/packs`, a pack, `/modules`, `/grammar`, `/cards`,
  `/course`, `/drills`, `/scenarios`, `/scenes`, `/villagers`, `/stories`, `/readings`, `/culture`, `/cultures`,
  `/lookup` (a noun, a verb's form), `/forms`, `/voice/profiles`, `/game`, `/health`, `/arrivals`, `/words` give the
  same status and the same JSON; and one `POST /message` reaches both channels with the same content and meta (but
  the msg_id). Skipped without bun or python3.

```bash
cd companion/android && ./gradlew :app:testDebugUnitTest --tests 'si.lanisce.lani.phone.*'
```

## On the emulator

The debug (and qa) build's probe (`src/debug/java/.../phone/PhoneBridgeProbe.kt`, a receiver only those builds have),
on a data directory of its own (`<filesDir>/lani/probe/data`, from the templates), never the learner's:

```bash
adb shell am broadcast -n si.lanisce.lani/.phone.PhoneBridgeProbe
adb logcat -s PhoneBridgeProbe LaniBridge      # the result, also in <filesDir>/lani/probe/result.json
```

## The numbers

The x86_64 emulator (Android 16, API 36) on a desktop CPU, a debug build, 2026-10-18 (an arm64 phone is likely 2–4×
slower for QuickJS, as phase 0 estimated):

| | Emulator | JVM (the same machine) |
|---|---|---|
| Unpacking the content (549 files, 11.7 MB), first time | 181 ms | |
| The bundle compiled and run | 237 ms | 227–354 ms |
| The bundle from its bytecode | 57 ms | 70–84 ms |
| The boot: the bridge built (stores, culture check, 33 features), start() | 1,017–1,061 ms | 1,003–1,156 ms |
| The first `GET /state` | 4 ms (a 500: the db port isn't wired) | |
| The first `/lookup?w=hiša` (the dictionary read) | 251 ms | |
| The next lookup | 7 ms | |
| `GET /packs` (58 KB) | 87 ms | |
| `POST /message` to the channel's event | 3 ms | |
| QuickJS's heap after a lookup | 66 MB allocated, 35.6 MB used, 223,000 objects | |
| The app's PSS, before and after | 98.7 → 174.4 MB (+76 MB) | |

On a node (Bun, the same machine) the bridge builds in 447 ms: QuickJS's boot is about twice as slow.

**The APK** (debug: arm64-v8a and x86_64; release: arm64-v8a, R8), before (71d4595) and after:

| | Before | After | |
|---|---|---|---|
| Debug | 54,073,789 B (51.6 MB) | 59,686,983 B (56.9 MB) | +5.6 MB |
| Release | 19,025,962 B (18.1 MB) | 23,411,990 B (22.3 MB) | +4.4 MB |

In the release APK: the content 3.5 MB compressed (11.7 MB), the bundle 0.28 MB (0.85 MB), QuickJS's library 0.41 MB
(0.9 MB), the rest the Kotlin. In the debug APK the content is 4.1 MB, the bundle 0.33 MB, the library 0.45 MB a
processor type (two).

## What isn't on the phone (yet)

- **The learner-data scripts** until the db port is registered (above): what reads or writes the six databases
  (`/state`, `/reviews`, `/readings/done`, `/level`, `POST /words`, `/packs/:id/learn`, `/counting`, `/forms-cards`)
  fails with a 500; the rhythm's routines, the scenes' variants and the stories' check, which read the state, log why
  and do nothing.
- **Voices:** no voice store, no ElevenLabs, no Gepard: the phone's text-to-speech (glas, plan 3, later). Recordings
  for the family aren't levelled (no ffmpeg).
- **The MCP server, the control socket, the shim:** the in-app tutor is the channel. No tool approvals.
- **The HTTP server:** the app calls the routes in-process; the family page and other towns can't reach a phone
  (towns and family through the relay: phase D). Outgoing requests (a visit, a town's hello) go out through fetch.
- **The pairing QR code, tailscale, git, lani-setup:** the CLIs aren't in the bundle. `git` answers "not a git
  repository".
- **Speech recognition** (the node's Whisper worker at `LANI_STT_URL`): its routes say it's down; the phone's
  recognizer is the app's.
- **The offline voice's download through the bridge** (`/voice/offline`, Piper's files from Hugging Face): it would
  hold a whole file in memory (fetch's answer at once); the app gets its voice another way in the hosted mode.
- **File watching** (`fs.watch`: a release dropped in, voice-build's clips): nothing changes the files behind the
  phone bridge's back. `FormData` (the family page's upload) isn't there.
- **Releases:** `/app/latest` has none (Play or GitHub releases).
- **Smaller differences:** an error's stack in QuickJS has no message line (`errorText` adds it in the log);
  `process.exit` throws instead of ending the app; `localeCompare` asks the host for anything but plain ids (a
  sort of many accented strings costs a call per comparison).

## What's left

- Wire the hosted mode into the app (the screens, the setup, the in-process transport): another change, on this API.
- Register the db port at merge (`src/phone/entry.ts`), and un-ignore the four tests.
- The boot takes about a second on the emulator (more on a phone): the culture pack's check (`validateCulture`, which
  only logs) could run after the first answers; the bundle could leave out the node's voice store (split
  `voice.ts`'s shared helpers from its store: about 100 KB).
- The content duplicates in part what the app bundles as Java resources (the culture packs, grammar, cards, course,
  drills, I spy, the festivals' packs): the app could read them from the unpacked project, or the unpacker could make
  the project from them (about a third of the 4.1 MB).
- A test on an arm64 phone (the numbers above are the emulator's), and of the qa build (R8) on the emulator: the probe
  runs there too.
