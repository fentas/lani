# Plan 4: the bridge as a service, the tutor with a shim

*Status: built, 2026-09-29, waiting for the switchover. Jan agreed to split the bridge: a long-running bridge
service, restarted on its own when its code changes, and a thin stdio MCP shim inside the tutor session that proxies
to it. The old mode ("classic") stays the default until Jan switches (see
[The switchover](#the-switchover-jan-does-it-once)). The smoke's `service` section checks it with a scripted Claude
Code; a real session is tested first in the rehearsal.*

## Why

Today Claude Code spawns the bridge (`companion/bridge/src/index.ts`) over stdio inside the tutor session. The
same process serves the app's HTTP API on 127.0.0.1:8790. So:

- Every bridge code change takes effect only when Jan restarts the tutor session. New app features wait.
- A tutor restart loses the tutor's context. So restarts are rare, and every change must stay readable by the
  old code that is still running.
- While the session is down, the app has no API at all.

What Claude Code does (checked in its docs, code.claude.com/docs/en/mcp.md, channels.md, channels-reference.md):

- It honors `notifications/tools/list_changed`: new tools become callable mid-session.
- It does **not** reconnect a stdio server that exits. (It does reconnect remote HTTP/SSE servers.)
- The docs don't cover a channel that proxies another process. So this must be tested with a real session (the
  rehearsal in the switchover).

## The parts

```
Android app ──HTTPS (tailscale serve)──► bridge service ◄──unix socket (MCP)──► shim ◄──stdio (MCP)──► claude (tmux)
                                         tmux "lani-bridge"                  spawned by Claude Code
                                         companion/bridge/src/service.ts       src/index.ts, LANI_BRIDGE_SHIM=1
```

**The service** is today's bridge: every feature, store and background job, and the app's HTTP API on the
learner's port. It no longer talks stdio. It listens on a unix socket for the shim instead.

**The shim** is what Claude Code spawns from `.mcp.json`. It holds almost nothing: the last tool list, a queue
of the session's messages while the service is down, and whether the session has seen a conversation yet. It
forwards everything else.

`.mcp.json` doesn't change. `src/index.ts` becomes a tiny switch: with `LANI_BRIDGE_SHIM=1` it runs the shim,
without it the old bridge (the old mode, and every other Claude Code session in the repo).

## The control connection

**MCP itself, over a unix socket.** The shim is an MCP client of the service. The service runs an MCP server per
connection, with the same channel code as today. One JSON-RPC message per line, as on stdio.

Why MCP: the tools, the instructions and the channel's events already speak it. The shim forwards what it doesn't
need to understand. The SDK handles request ids, cancellation and timeouts.

Why a unix socket, not a port with a token:

- The ports 8790–8799 are taken, and a port would need one more number per learner.
- The socket file's mode (600, in a directory of mode 700) is the authentication. There's no token to make, store
  and pass on.
- One socket per data directory. A dev bridge on a copy of the data never meets the real one.

**Where the socket is:** `~/.local/state/lani/run/bridge-<hash>.sock` (`$XDG_STATE_HOME` if set), where
`<hash>` is the first 12 hex digits of the SHA-256 of the learner's `<data>/app` path. `LANI_BRIDGE_SOCKET`
overrides it. Next to it: `bridge-<hash>-status.json` (for `lani-bridge status`) and
`bridge-<hash>-channel.json` (the last instructions and tools, for a shim that starts while the service is down).

Not in `data/app/`: QA copies `data/` with `cpSync`, which fails on a socket. Not in `$XDG_RUNTIME_DIR`: without
linger, `/run/user/<uid>` is removed when Jan's last login ends, while tmux keeps running.

**The messages:**

| From → to | Message |
|---|---|
| shim → service | `initialize` with `clientInfo: {name: "lani-shim"}` and `capabilities.experimental["lani/shim"]: {protocol: 1, session, heard, pid}` |
| service → shim | the reply: the channel's `instructions`, `experimental["claude/channel"]`, `["claude/channel/permission"]` (not for a child), `["lani/service"]: {protocol: 1, pid, started_at, profile}` |
| shim → service | `tools/list`, `tools/call` (and `notifications/cancelled` when the session cancels a call) |
| shim → service | every `notifications/claude/*` from the session: today the permission prompt (`…/channel/permission_request`) |
| service → shim | every `notifications/claude/*` for the session: events (`…/channel`) and Jan's verdicts (`…/channel/permission`) |
| service → shim | `notifications/lani/replaced`: another tutor session took over this learner's channel |

The shim forwards every `notifications/claude/*` both ways without looking inside. So a new kind of channel
message needs no shim change.

`protocol: 1` is the shim's version of this contract. A future service must keep accepting `1` until every tutor
session has restarted.

## The shim

- **Start.** It connects to the socket and waits at most 8 s for the service. It takes the service's instructions,
  capabilities and tools, then answers Claude Code's `initialize`. When the service isn't up, it takes them from
  `bridge-<hash>-channel.json`, which the service writes at each start. Without that file: short built-in
  instructions (with the child's rule when `LANI_CHILD` is set) and no tools until the service is up.
- **Capabilities** to Claude Code: `claude/channel`, `claude/channel/permission` unless the learner is a child, and
  `tools: {listChanged: true}`.
- **tools/list** answers from the last list, so it works while the service is down.
- **tools/call** goes to the service.
  - While the service is down, the call waits up to 10 s for it to come back (a restart takes about a second).
    Then it fails with `isError`: "The Lani bridge service isn't running (it may be restarting). Nothing was
    done. Try again in a minute."
  - A call that arrives while the service is stopping is refused by the service, and the shim retries it once on
    the next service. A tool call during a deploy is then just a moment slower.
  - A call cut off mid-way (the service died while it ran) fails with: "…it may or may not have taken effect;
    check before you repeat it."
  - Any other error from the service passes through unchanged.
- **Channel events** go to Claude Code as they come. Before Claude Code has initialized, the shim holds them.
- **Permission relay:** a prompt from the session goes to the service (and the app); while the service is down,
  the shim keeps up to 50 and sends them when it's back.
- **Reconnect.** When the connection drops, it tries again every 0.25 s for 10 s (a restart takes about a second),
  then doubling to 5 s at most, forever. On reconnect it lists the tools. When the list differs (a name, a
  description or a schema), it sends `notifications/tools/list_changed`, and Claude Code fetches the new list.
  - Instructions can't change mid-session. A changed text is only logged. The full guide comes from the
    `channel_guide` tool, which is live.
- **It never exits on its own.** It exits only when Claude Code closes its stdin (the session ends).
- **Replaced.** When a second shim connects to the same service, the newest wins. The older one stops
  reconnecting and fails calls with "another tutor session took over this learner's channel".

## The service

What changes against today's bridge:

- **It always serves the app's HTTP API.** When the port is taken, it exits with an error, and the supervisor
  tries again. So during the switchover it takes 8790 as soon as the old bridge lets go.
- **Events for the tutor while no session is attached are kept**, in order: at most 200, at most 3 days old, in
  `<data>/app/channel-queue.json`. They're delivered when a session attaches, with `queued_at` in the tag.
  `POST /message` answers `200` with `queued: true`.
  - Today such a message failed (`502` from tailscale serve), and the app gave up after 5 tries.
- **`POST /permission`** with no session attached: `503`.
- **The app's event buffer** (the last 200 SSE events) is saved to `<data>/app/events.json` (2 s after a change
  and at shutdown) and read again at start. So a restart doesn't lose a reply the phone hasn't fetched yet. Event
  ids keep increasing across restarts, as now.
- **Stopping** (SIGTERM, SIGINT, SIGHUP): it stops accepting connections, refuses new tool calls as "restarting"
  (the shim retries them), lets running HTTP requests and tool calls finish (at most 5 s), saves, closes the shim's
  connection, removes the socket and exits `0`.
- **One service per data directory.** At start, a socket file where nothing answers is removed. When a service
  answers there, the new one exits.
- **The first-event hint** ("call channel_guide first"): the shim says in its hello whether the session has seen a
  conversation event yet, so a service restart doesn't ask the tutor again.
- **Status:** `bridge-<hash>-status.json` holds the pid, the port, the commit it runs, whether a session is
  attached, the queued events and the tool names.

## How it runs

`companion/bin/lani-bridge`:

```bash
companion/bin/lani-bridge start [--profile <id>]       # the service in tmux "lani-bridge" (-<id>); nothing if it runs
companion/bin/lani-bridge restart [--profile <id> | --all]     # deploy: the current code, about a second of downtime
companion/bin/lani-bridge status [--profile <id> | --all]
companion/bin/lani-bridge stop [--profile <id>]
companion/bin/lani-bridge logs [--profile <id>] [-f]
companion/bin/lani-bridge run [--profile <id>]         # the supervisor loop (what the tmux session runs)
companion/bin/lani-bridge exec [--profile <id>]        # the service once, in the foreground (for systemd)
```

- **The supervisor** (`run`) loads `~/.config/lani/keys.env` and the profile's environment (from
  `profiles.json`) before each start, runs the service, and starts it again when it exits: at once after a clean
  stop, after 1 s, doubling to 30 s, when it keeps failing within 10 s of its start. `stop` leaves a mark first, so
  the loop ends instead of starting it again.
- **Logs** go to the tmux pane and to `~/.local/state/lani/bridge.log` (`bridge-<id>.log`), rotated at 10 MB.
  Today they're in Claude Code's MCP log.
- **restart** first checks that the code builds (`bun build`, about 0.1 s). When it doesn't, the running service is
  left alone. Then it sends SIGTERM to the pid in the status file, waits for the new service's `/health`, and
  for the tutor's shim to reconnect. It prints how long each took and which tools came or went.

**tmux or systemd.** Decided: tmux now, a systemd user unit as a template for later
(`companion/systemd/lani-bridge@.service`, not installed).

- **systemd** restarts a crashed service and logs to the journal. But user services run under Jan's user
  manager, which stops them when Jan's last login session ends, unless linger is on. The tutor's tmux session
  survives a logout (logind's default `KillUserProcesses=no`, which this node keeps). So with systemd and no
  linger, the app would lose its API while the tutor keeps running.
- **Linger** (`loginctl enable-linger fentas`) keeps user services running without a login and starts them at
  boot. The backup timer would then run without a login too. The cost: Jan's processes run while nobody is logged
  in, which is the point, and it's a setting of the machine, not of Jan's files. It's Jan's decision. It's off now.
- **tmux** gives the service the same lifetime as the tutor: it survives a logout and doesn't start at boot, like
  the tutor today. The supervisor loop does the restarts.

`lani-bridge restart` works with both: it signals the pid, and whichever supervisor runs starts it again.

## Profiles

**A service per learner.** Each has its own tmux session (`lani-bridge-<id>`), socket, port (the one in
`profiles.json`), log and data.

Why not one service for everyone:

- Everything the bridge holds is per learner: the data, the culture pack and its language, the tokens, the port.
  One process would mean rebuilding every store per learner inside it.
- A restart or a crash of one learner's service doesn't touch another's. A child's service is its own.
- The environment stays as today (`LANI_PROFILE`, `LANI_DATA_DIR`, `LANI_BRIDGE_PORT`, …): no feature code
  changes.

The cost is one more process per learner: measured, a service takes about 150 MB and a shim about 90 MB (mostly Bun
and the MCP SDK), where the classic bridge took about 165 MB. `lani-bridge restart --all` deploys to every running
one.

## Choosing the mode

`lani-session` picks the mode: `--service` or `--classic`, else `$LANI_BRIDGE_MODE`, else the word in
`~/.config/lani/bridge-mode`, else `classic` (today's). `lani-session --print` shows the commands.

- **classic:** exactly today's command (`LANI_TUTOR=1`, the bridge inside the session).
- **service:** `lani-bridge start` first (waits up to 30 s for the service to answer), then the tutor session with
  `LANI_BRIDGE_SHIM=1` and `LANI_BRIDGE_SOCKET`, and without `LANI_TUTOR` and `LANI_BRIDGE_PORT`. So even
  a bridge started by mistake inside the session never takes the port. Profiles get the same (`lani-profile
  session <id> --mode service`).

## Deploying a code update

1. The change is on `main` (the node runs from the checkout).
2. `companion/bin/lani-bridge restart --all`.
3. The app sees about a second of downtime (measured on a test learner: the API was back after 0.6 s, the tutor's
   shim reconnected after 0.8 s). Requests meanwhile get `502` from tailscale serve, the event stream reconnects, and
   the app's outbox sends its writes again. The tutor sees nothing, except that a tool call during the restart
   waits a moment, and a new tool appears at once.

The rule that new data must stay readable by old code still holds for the app on the phone. For the bridge it
holds only for the second of a restart.

## What still needs a tutor restart

- A change to the shim: `src/shim.ts`, `src/control.ts` (the socket transport), `src/index.ts` (the switch), and
  the MCP SDK the shim loads. Keep them small. They change rarely.
- The channel instructions (the short rules; Claude Code reads them once at `initialize`). The full guide is the
  `channel_guide` tool and is live.
- New kinds of MCP capabilities (prompts, resources), which Claude Code negotiates once.
- Claude Code's own settings: `.mcp.json`, `--remote-control`, the appended prompt.

## The switchover (Jan does it once)

**Before:**

1. The change is merged on `main`, and `cd companion/bridge && bun install` has run.
2. `cd companion/bridge && bun test/smoke.ts` passes.

**Rehearse on a throwaway learner** (a temporary registry, port 9120, no API keys; nothing of Jan's is touched):

```bash
export LANI_PROFILES_DIR=$(mktemp -d) LANI_KEYS_FILE=/dev/null
companion/bin/lani-profile add rehearsal --name Test --target sl --base en --port 9120 --https-port 19443
companion/bin/lani-session --profile rehearsal --service       # confirm the development channel; Ctrl-b d
```

3. In the same shell, after detaching: `companion/bin/lani-bridge status --profile rehearsal` says the service is
   up on 9120 and the tutor is attached.
4. Send a chat and watch the reply come (the token is in `$LANI_PROFILES_DIR/rehearsal/data/app/bridge-token`):
   ```bash
   T=$(cat $LANI_PROFILES_DIR/rehearsal/data/app/bridge-token)
   curl -N -H "Authorization: Bearer $T" 127.0.0.1:9120/events &
   curl -H "Authorization: Bearer $T" -d '{"text":"Živjo! Say hi."}' 127.0.0.1:9120/message
   ```
5. `companion/bin/lani-bridge restart --profile rehearsal`. It says the tutor reconnected. Send another chat: the
   reply comes. Ask the tutor to call a tool (e.g. "list the grammar pages"): it works.
6. Clean up: `companion/bin/lani-bridge stop --profile rehearsal`, `tmux kill-session -t lani-rehearsal`, and
   `unset LANI_PROFILES_DIR LANI_KEYS_FILE` (else the real service would start without the keys).

**Switch Jan's tutor** (a quiet moment: no role-play running; the tutor's context is lost as with any restart):

7. `echo service > ~/.config/lani/bridge-mode`
8. Quit the tutor: `tmux attach -t lani`, then `/exit`. Its bridge exits and frees 8790.
9. `companion/bin/lani-session`. It starts the service (tmux `lani-bridge`), waits until it answers on 8790,
   then starts the tutor with the shim. Confirm the development channel as usual.
10. `companion/bin/lani-bridge status`: up on 8790, the tutor attached. On the phone, send a chat: the reply
    comes. `tailscale serve` needs nothing: it still proxies to 8790.
11. `companion/bin/lani-bridge restart`: the phone is back within a second or two. Send another chat.
12. Each other learner: quit their tutor (`tmux attach -t lani-<id>`, `/exit`), then
    `companion/bin/lani-session --profile <id>`.

**Rollback** (any time):

1. `echo classic > ~/.config/lani/bridge-mode`
2. Quit the tutor (`tmux attach -t lani`, `/exit`).
3. `companion/bin/lani-bridge stop`. It frees 8790 and says how many tutor events were still queued.
4. `companion/bin/lani-session` starts the old way.

The data stays readable both ways. The service only adds files the old bridge ignores (`<data>/app/events.json`,
`<data>/app/channel-queue.json`) and its files under `~/.local/state/lani/`. Events still queued for the tutor at
a rollback aren't delivered by the old bridge (step 3 shows the number; roll back when it's 0).

## Risks

- **A channel through a proxy isn't documented.** To Claude Code the shim *is* the channel: the same capabilities,
  the same notifications. Tested here with a scripted MCP client only. The rehearsal is the real test.
- **Claude Code's start timeout.** The shim answers `initialize` within 8 s, from its cached file when the service
  is late.
- **A tool call cut off by a restart** may have taken effect (a reply was sent). The drain (5 s) makes this rare,
  and the shim says so when it happens.
- **A bug in the shim** needs a tutor restart to fix. It's small and has its own smoke checks.
- **Queued events arrive late:** a role-play turn from hours ago. They carry `queued_at`, so the tutor can tell.
- **tmux as the supervisor** doesn't start at boot, and a tmux server crash takes it down: the same as the tutor
  today. Changing the supervisor loop itself needs `lani-bridge stop` and `start`.
- **Two tutor sessions for one learner:** the newest wins, and the older one's tools fail clearly.
- **A permission prompt held during an outage** reaches the app late. If Jan answered it in the terminal meanwhile,
  the app's answer is for a closed prompt. Claude Code should ignore it; that isn't tested.
- **Secrets:** unchanged. The service loads `keys.env` as the session did. The tutor session still loads it too.
  Once the service runs, it no longer needs to (a follow-up: the tutor's shell wouldn't see the ElevenLabs key).
- **One more process per learner,** and the logs move from Claude Code's MCP log to `~/.local/state/lani/`.

## Build order

All built on 2026-09-29; the smoke's `service` section has 45 checks.

1. This plan.
2. The control protocol (`src/control.ts`): the socket's path, the transport, the hello.
3. The service mode (`src/bridge.ts`, `src/service.ts`): the channel per connection, the queue, the event buffer
   kept across restarts, the drain, the status.
4. The shim (`src/shim.ts`, the switch in `src/index.ts`).
5. `lani-bridge`, `lani-session --service`, `lani-profile session --mode`, the systemd template.
6. Smoke checks (`test/smoke/service.ts`): a service and a shim with a scripted MCP client (initialize, tools, a
   call, a channel event both ways, the permission relay, a restart mid-way with the reconnect and
   `list_changed`, a call during the outage, the queue, a child's profile beside it).
7. The README ("Run", "Learners", "Bridge code") and this plan's status.
