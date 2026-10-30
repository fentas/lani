// What the app's reads depend on, as one version (GET /changes, docs/plans/05-built/nav-perf.md): the app reads
// everything again when Home comes back only while this version moved since its last whole read. And the answers'
// ETags (conditional): a read whose answer is the same as the one the app has costs a 304 and no body.
import { createHash, randomUUID } from 'node:crypto'
import { readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import type { Route } from './http'

/** The bridge's local date of [now] (YYYY-MM-DD), as towns.ts's localDate. */
const localDate = (now = Date.now()) => {
  const d = new Date(now)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * Where the learner's data has what the app's reads never serve, or what changes for nothing they read: the data
 * repository's history, the voice clips (the voice store's database stands for them), the released APKs, the backups,
 * the chat's photos.
 */
const DATA_SKIP = new Set(['.git', '.backups', 'voice', 'release', 'chat-images'])

/** The project's directories with no content the bridge serves (its code, the app's, the workers'), and big trees. */
const PROJECT_SKIP = new Set(['android', 'bridge', 'bin', 'relay', 'stt-local', 'systemd', 'tests', 'voice-local', 'voice-drama3'])

/**
 * The version of what the bridge serves the app (GET /changes): a digest of
 * - this run of the bridge (a restart is new code or settings: everything may read differently),
 * - the events emitted so far (each one is something the tutor or the bridge did: a page published, a guest come),
 * - the day (the cards due, today's happenings, the rhythm),
 * - every file the reads come from, by its inode, size and time of change: the learner's data directory (the six
 *   databases, app/'s stores, the tutor's content there; [DATA_SKIP] left out), the voice store's database (its
 *   clips' index) and, on a node, the project's content (companion/'s scenes, packs, grammar, the culture packs …:
 *   [PROJECT_SKIP] left out), which a merge on main changes under a running bridge.
 *
 * Anything that writes a file the app reads moves it: the app's writes through the bridge, the tutor's tools, a session's
 * update-db.py, card.py's fixes, a merge. A walk is a stat of each file (about 900 on a node: 3 to 4 ms).
 */
export class Changes {
  private readonly run = randomUUID()
  private events = 0

  constructor(
    private readonly o: {
      dataDir: string
      /** The voice store's directory (voice.db and its write-ahead log), read when asked (the store is made after this). */
      voiceDir?: () => string | undefined
      /**
       * Where the content the bridge serves comes from, when it can change under a running bridge (a node's checkout:
       * the project's companion/, and a content directory set apart from it, LANI_SCENES_DIR …); none on the phone,
       * whose content came with the APK.
       */
      contentDirs?: string[]
    },
  ) {}

  /** Something happened that the app may read differently now (an event emitted). */
  bump() {
    this.events++
  }

  /** The version now ([now]: the clock, for its day). */
  version(now = Date.now()): string {
    const h = createHash('sha1')
    h.update(`${this.run}\n${this.events}\n${localDate(now)}\n`)
    walk(this.o.dataDir, h, DATA_SKIP)
    const voice = this.o.voiceDir?.()
    if (voice) for (const f of ['voice.db', 'voice.db-wal']) stamp(join(voice, f), h)
    for (const dir of this.o.contentDirs ?? []) walk(dir, h, PROJECT_SKIP)
    return h.digest('base64url').slice(0, 22)
  }
}

/** [path]'s inode, size and time of change into [h]; nothing for a file that isn't there. */
function stamp(path: string, h: ReturnType<typeof createHash>) {
  try {
    const s = statSync(path)
    h.update(`${path}\0${s.ino}\0${s.size}\0${s.mtimeMs}\n`)
  } catch {} // not there (yet): nothing
}

/** Every file under [dir] (its top-level names in [skip] left out, and any .git), in a fixed order, stamped into [h]. */
function walk(dir: string, h: ReturnType<typeof createHash>, skip: Set<string> = new Set()) {
  let names: string[]
  try {
    names = readdirSync(dir).sort()
  } catch {
    return // not there (yet)
  }
  for (const name of names) {
    if (skip.has(name) || name === '.git' || name === 'node_modules') continue
    const path = join(dir, name)
    let s
    try {
      s = statSync(path)
    } catch {
      continue // gone meanwhile
    }
    if (s.isDirectory()) walk(path, h)
    else h.update(`${path}\0${s.ino}\0${s.size}\0${s.mtimeMs}\n`)
  }
}

/** The route of [changes]: GET /changes, the app's only (its version, and the day it counts). */
export function changesRoute(changes: Changes): Route {
  return { method: 'GET', path: '/changes', handle: () => Response.json({ version: changes.version(), day: localDate() }) }
}

/**
 * [routes], each GET's JSON answer with its ETag (its bytes' SHA-1): asked again with that ETag (If-None-Match), the same
 * answer is a 304 without a body. Only JSON (never a stream, a clip or an APK), only a 200.
 */
export function conditional(routes: Route[]): Route[] {
  return routes.map(r =>
    r.method !== 'GET'
      ? r
      : {
          ...r,
          handle: async c => {
            const res = await r.handle(c)
            if (res.status !== 200 || !(res.headers.get('content-type') ?? '').includes('json')) return res
            const body = new Uint8Array(await res.arrayBuffer())
            const tag = `"${createHash('sha1').update(body).digest('base64url')}"`
            const headers = new Headers(res.headers)
            headers.set('etag', tag)
            if (matches(c.req.headers.get('if-none-match'), tag)) {
              headers.delete('content-length')
              headers.delete('content-type')
              return new Response(null, { status: 304, headers })
            }
            headers.delete('content-length')
            return new Response(body, { status: 200, headers })
          },
        },
  )
}

/** Whether an If-None-Match header ([header]: "*" or a list of ETags, weak or strong) names [tag]. */
function matches(header: string | null, tag: string): boolean {
  if (!header) return false
  return header.split(',').some(t => {
    const v = t.trim()
    return v === '*' || v === tag || v === `W/${tag}`
  })
}
