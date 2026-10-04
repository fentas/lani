#!/usr/bin/env bun
// The bridge as a service of its own (docs/plans/04-bridge-service.md). companion/bin/lani-bridge runs it in tmux, and
// restarts it to deploy new code; the tutor session reaches it through its shim (src/shim.ts).
import './env' // first: LANI_* from the FLUENT_* names of a node set up before the rename
import { startBridge } from './bridge'

await startBridge({ mode: 'service' })
