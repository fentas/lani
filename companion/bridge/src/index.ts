#!/usr/bin/env bun
// What Claude Code spawns from .mcp.json (docs/plans/04-bridge-service.md):
// - in a tutor session started in the service mode (lani-session sets LANI_BRIDGE_SHIM=1): the shim, which proxies
//   to the bridge service (src/shim.ts);
// - anywhere else: the bridge itself, the classic mode (bridge.ts). Every other Claude Code session in the repo starts
//   one too; only the tutor's serves the app (config.ts).
// A change to this file, as to the shim, reaches a tutor session only when it restarts.
import './env' // first: LANI_* from the FLUENT_* names of a node set up before the rename

if (process.env.LANI_BRIDGE_SHIM === '1') await import('./shim')
else await (await import('./bridge')).startBridge({ mode: 'stdio' })
