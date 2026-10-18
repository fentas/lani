#!/usr/bin/env bun
// lani-relay: towns, the family page and backups for towns without a node (docs/plans/05-built/relay.md). One process,
// its storage on disk (RELAY_DATA_DIR), configured by the environment (config.ts).
import { configFromEnv } from './config'
import { createRelay, VERSION } from './relay'

const cfg = configFromEnv()
const relay = createRelay(cfg)

const server = Bun.serve({
  hostname: cfg.host,
  port: cfg.port,
  // long polls hold a request up to RELAY_POLL_WAIT_MAX_MS (30 s); Bun closes idle ones after this many seconds
  idleTimeout: 120,
  development: false,
  // a backup (50 MB) and a little over; every route has its own, smaller limit
  maxRequestBodySize: cfg.backupMaxBytes + 1024 * 1024,
  fetch(req, srv) {
    // the entry the proxy in front added (the right-most): the ones before it are the client's to write
    const forwarded = cfg.trustProxy ? req.headers.get('x-forwarded-for')?.split(',').at(-1)?.trim() : undefined
    return relay.fetch(req, forwarded || srv.requestIP(req)?.address || 'unknown')
  },
  error(e) {
    console.error(`[lani-relay] ${e.stack ?? e}`)
    return Response.json({ error: 'internal error', code: 'internal' }, { status: 500, headers: { 'x-lani-relay': 'error' } })
  },
})

const sweeper = setInterval(() => {
  try {
    const gone = relay.sweep()
    if (gone.mail || gone.replies) console.error(`[lani-relay] retention: ${gone.mail} message(s), ${gone.replies} answer(s) gone`)
  } catch (e) {
    console.error(`[lani-relay] sweep: ${(e as Error).message}`)
  }
}, 60_000)

console.error(`[lani-relay] v${VERSION} on http://${cfg.host}:${server.port}, data ${cfg.dataDir}`)

for (const sig of ['SIGTERM', 'SIGINT'] as const) {
  process.on(sig, () => {
    clearInterval(sweeper)
    void server.stop()
    relay.close()
    process.exit(0)
  })
}
