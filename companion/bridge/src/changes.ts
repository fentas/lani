// What the app's reads depend on (GET /changes, docs/plans/05-built/nav-perf.md, changes-areas.md): a version of everything,
// and one of each area (the learner's own data, the village, the towns, the family's, the content), so the app reads again
// only what depends on an area that moved. Each read of the app says the versions it was made at (Lani-Areas), and its
// answer has its ETag (conditional): a read whose answer is the same as the one the app has costs a 304 and no body.
import { createHash, randomUUID } from 'node:crypto'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import type { Route } from './http'

/** The bridge's local date of [now] (YYYY-MM-DD), as towns.ts's localDate. */
const localDate = (now = Date.now()) => {
  const d = new Date(now)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * The areas of what the bridge serves the app, each with a version of its own (GET /changes: `areas`). A read the app makes
 * again on a Back depends on some of them ([Route.areas], and its answer says their versions: [AREAS_HEADER]):
 * - `learner`: what the learner did: the databases (the six, another language's in languages/, the sessions' results)
 *   and the bridge's other records of the learner (the answers' log, the word requests, the tutor's notebook …: every file
 *   of the data directory that is in none of the others);
 * - `village`: the village (app/game.json), which every move in it writes;
 * - `towns`: the town's links, guests, visits, questions and friendships (app/towns.json, app/town-*);
 * - `family`: the family's challenges and recordings (app/family, app/audio);
 * - `content`: what the tutor and the project wrote for the app to show (the tutor's stores in app/: [CONTENT_STORES];
 *   on a node the project's content under companion/ and any content directory set apart), and who the learner is (the
 *   profile's learner, preferences and home language: the name, the gender, the languages and the level the content is
 *   said in, the learner's own voice);
 * - `friends`: what the friendships between towns take of the village and of the learner, by value (no files of its own:
 *   [Changes.value], features/friendship.ts): the village's last feast and the learner's level in each linked town's
 *   language. A move in the village or a session moves it only when one of those changes.
 * A session's results move `learner` only, a move in the village `village` only: the scenes, the course, the grammar
 * book, the towns' friendships … aren't read again for them.
 */
export const AREAS = ['learner', 'village', 'towns', 'family', 'content', 'friends'] as const
export type Area = (typeof AREAS)[number]

/** The header of an answer that says the versions of the areas it depends on, as they were before it was made. */
export const AREAS_HEADER = 'lani-areas'

/** The tutor's stores in the app's directory (build.ts), and the cast's voices on the phone: content, whatever the learner does. */
const CONTENT_STORES = new Set(['modules', 'packs', 'grammar', 'cards', 'cases', 'course', 'scenes', 'variants', 'stories', 'readings', 'villagers', 'arrivals', 'scenarios', 'overrides', 'overrides.json', 'lexicon.json', 'voice-profiles.json'])

/** The area a file of the data directory belongs to ([rel]: its path from there). */
export function dataArea(rel: string): Area {
  if (!rel.startsWith('app/')) return 'learner'
  const top = rel.slice(4).split('/')[0]
  if (top === 'game.json') return 'village'
  if (top === 'towns.json' || top.startsWith('town-')) return 'towns'
  if (top === 'family' || top === 'audio') return 'family'
  if (CONTENT_STORES.has(top)) return 'content'
  return 'learner'
}

/**
 * Where the learner's data has what the app's reads never serve, or what changes for nothing they read (paths from the
 * data directory): the data repository's history, the backups, the voice clips (the voice store's database stands for
 * them), the released APKs, the chat's photos, and the bridge's own bookkeeping: the events kept for the app (written two
 * seconds after each event, which moves the version anyway), the outbox's client ids, the paired phones and their last
 * visits, the developer's uploads, the notifications waiting for the tutor (written with a session's note while no tutor
 * listens).
 */
const DATA_SKIP = new Set(['.git', '.backups', 'app/voice', 'app/release', 'app/chat-images', 'app/events.json', 'app/client-ids.json', 'app/devices.json', 'app/devices-seen.json', 'app/dev', 'app/channel-queue.json'])

/**
 * The project's directories with no content the bridge serves (its code, the app's, the workers'), big trees, QA's
 * screenshots and logs (companion/qa-shots: a QA run writes them while its dev bridge serves the app, which moved the
 * content's version under it), and its docs (a merge of docs alone moved the content's version: one merge in six that
 * touched what the walk sees).
 */
const PROJECT_SKIP = new Set(['android', 'bridge', 'bin', 'relay', 'stt-local', 'systemd', 'tests', 'voice-local', 'voice-drama3', 'qa-shots', 'README.md', 'GAME.md', 'SCENES.md', 'VILLAGERS.md', 'COURSE.md'])

/** How long the versions of a walk are taken again for the answers after it, while no write came through the bridge (ms). */
export const REUSE_MS = 2_000

/** The versions of what the bridge serves: of everything ([version]), of each area ([areas]), and the day they count. */
export type Versions = { version: string; day: string; areas: Record<Area, string> }

/**
 * The versions of what the bridge serves the app (GET /changes), each a digest of
 * - this run of the bridge (a restart is new code or settings: everything may read differently),
 * - the day (the cards due, today's happenings, the rhythm),
 * - every file its reads come from, by its inode, size and time of change, in its area ([dataArea]; on a node the project's
 *   content, [PROJECT_SKIP] left out, in `content`), and for `content` who the learner is (the profile's learner,
 *   preferences and home language, by value: a session writes the profile's counts, not them).
 * [Versions.version] is all of them together, for an app that reads everything again when anything moved (before the
 * areas).
 *
 * Anything that writes a file the app reads moves its area: the app's writes through the bridge, the tutor's tools, a
 * session's update-db.py, card.py's fixes, a merge. Not the events (the app handles each as it comes, and what one says
 * changed is written to a file the walk sees), nor the voice store (each clip made says so: voice_updated, and the app reads
 * its clips' index again then; a node voicing in the background moved every Back's version before). A walk is a stat of
 * each file (about 900 on a node: 3 to 6 ms). The versions an answer says ([areas]) are the last walk's for [REUSE_MS]
 * while no write came through the bridge ([written]): walked before the answer was made, they are never newer than it.
 */
export class Changes {
  private readonly run = randomUUID()
  private last: { at: number; v: Versions } | null = null
  /** The areas said by value ([value]): each one's value, worked out again only when the areas it comes from moved. */
  private readonly values: { area: Area; from: readonly Area[]; of: () => string; key?: string; text?: string }[] = []

  constructor(
    private readonly o: {
      dataDir: string
      /**
       * Where the content the bridge serves comes from, when it can change under a running bridge (a node's checkout:
       * the project's companion/, and a content directory set apart from it, LANI_SCENES_DIR …); none on the phone,
       * whose content came with the APK.
       */
      contentDirs?: string[]
      /** The clock (ms; tests). */
      now?: () => number
    },
  ) {}

  /** The versions now, every area walked. */
  versions(): Versions {
    const at = (this.o.now ?? Date.now)()
    const v = this.walked(at)
    this.last = { at, v }
    return v
  }

  /** The version of everything now ([versions]). */
  version(): string {
    return this.versions().version
  }

  /**
   * The versions of [areas] for an answer about to be made: the last walk's while it is under [REUSE_MS] old and no write
   * came through the bridge since ([written]), else a walk now; never newer than the answer made after it.
   */
  areas(areas: readonly Area[]): Partial<Record<Area, string>> {
    const at = (this.o.now ?? Date.now)()
    const last = this.last
    const v = last && at - last.at >= 0 && at - last.at < REUSE_MS ? last.v : this.versions()
    return Object.fromEntries(areas.map(a => [a, v.areas[a]]))
  }

  /** A write came through the bridge (one of the app's): the next answer's versions are walked anew. */
  written() {
    this.last = null
  }

  /**
   * [area]'s version also says a value worked out from what areas [from] hold ([of]: a text; "" when it fails), worked
   * out again only when one of them moved: a read that depends on that value, not on every file of [from], says [area]
   * instead of them, and isn't read again for a change of theirs that leaves the value as it was (the towns' friendships:
   * the village's last feast, not its every move). [from] are areas of files, never one said by value.
   */
  value(area: Area, from: readonly Area[], of: () => string) {
    if (from.some(a => a === area || this.values.some(v => v.area === a))) throw new Error(`changes: ${area} comes from an area said by value`)
    this.values.push({ area, from, of })
    this.last = null
  }

  private walked(at: number): Versions {
    const day = localDate(at)
    const hashes = Object.fromEntries(AREAS.map(a => [a, createHash('sha1').update(`${this.run}\n${day}\n${a}\n`)])) as Record<Area, ReturnType<typeof createHash>>
    walk(this.o.dataDir, rel => hashes[dataArea(rel)], DATA_SKIP)
    for (const dir of this.o.contentDirs ?? []) walk(dir, () => hashes.content, PROJECT_SKIP)
    hashes.content.update(`facts\0${facts(this.o.dataDir)}\n`)
    const digest = (a: Area) => hashes[a].digest('base64url').slice(0, 22)
    const byValue = new Set(this.values.map(v => v.area))
    const areas = Object.fromEntries(AREAS.filter(a => !byValue.has(a)).map(a => [a, digest(a)])) as Record<Area, string>
    // the areas said by value, after the areas they come from (their versions say whether the value is worked out again)
    for (const v of this.values) {
      const key = v.from.map(a => areas[a]).join(',')
      if (v.key !== key) {
        try {
          v.text = v.of()
        } catch {
          v.text = ''
        }
        v.key = key
      }
      hashes[v.area].update(`value\0${v.text}\n`)
    }
    for (const a of byValue) areas[a] = digest(a)
    const all = createHash('sha1')
    for (const a of AREAS) all.update(`${a}=${areas[a]}\n`)
    return { version: all.digest('base64url').slice(0, 22), day, areas }
  }
}

/**
 * Who the learner is, as the content is said to them: learner-profile.json's learner (the name, the gender, the languages,
 * the level), its preferences (the learner's own voice …) and its home language, as one text; "" without a profile.
 */
function facts(dataDir: string): string {
  try {
    const p = JSON.parse(readFileSync(join(dataDir, 'learner-profile.json'), 'utf8'))
    return JSON.stringify([p?.learner ?? null, p?.preferences ?? null, p?.home_language ?? null])
  } catch {
    return ''
  }
}

/**
 * Every file under [dir] (the paths from it in [skip] left out, and any .git or node_modules), in a fixed order, stamped
 * into the hash [into] gives for its path from where the walk began; [rel] is [dir]'s path from there.
 */
function walk(dir: string, into: (rel: string) => ReturnType<typeof createHash>, skip: Set<string> = new Set(), rel = '') {
  let names: string[]
  try {
    names = readdirSync(dir).sort()
  } catch {
    return // not there (yet)
  }
  for (const name of names) {
    const at = rel ? `${rel}/${name}` : name
    if (skip.has(at) || name === '.git' || name === 'node_modules') continue
    const path = join(dir, name)
    let s
    try {
      s = statSync(path)
    } catch {
      continue // gone meanwhile
    }
    if (s.isDirectory()) walk(path, into, skip, at)
    else into(at).update(`${path}\0${s.ino}\0${s.size}\0${s.mtimeMs}\n`)
  }
}

/** The route of [changes]: GET /changes, the app's only (the version of everything, of each area, and the day they count). */
export function changesRoute(changes: Changes): Route {
  return { method: 'GET', path: '/changes', handle: () => Response.json(changes.versions()) }
}

/** [areas] as [AREAS_HEADER] says them: `learner=…, content=…`. */
export function areasHeader(areas: Partial<Record<Area, string>>): string {
  return Object.entries(areas).map(([a, v]) => `${a}=${v}`).join(', ')
}

/**
 * [routes], each GET's JSON answer with its ETag (its bytes' SHA-1): asked again with that ETag (If-None-Match), the same
 * answer is a 304 without a body. Only JSON (never a stream, a clip or an APK), only a 200. A GET with [Route.areas] says
 * their versions from [changes] as they were before its answer was made ([AREAS_HEADER]) on a 200, a 304 and a 404 (an
 * answer too: no course for this language), never on another error (the app asks again); every other request may write:
 * the next answer's versions are walked anew ([Changes.written]).
 */
export function conditional(routes: Route[], changes?: Changes): Route[] {
  return routes.map(r =>
    r.method !== 'GET'
      ? {
          ...r,
          handle: async c => {
            try {
              return await r.handle(c)
            } finally {
              if (c.req.method !== 'GET' && c.req.method !== 'HEAD') changes?.written()
            }
          },
        }
      : {
          ...r,
          handle: async c => {
            const areas = r.areas?.length && changes ? areasHeader(changes.areas(r.areas)) : null
            const res = await r.handle(c)
            const said = areas !== null && [200, 304, 404].includes(res.status)
            if (res.status !== 200 || !(res.headers.get('content-type') ?? '').includes('json')) {
              if (!said) return res
              const headers = new Headers(res.headers)
              headers.set(AREAS_HEADER, areas)
              return new Response(res.body, { status: res.status, statusText: res.statusText, headers })
            }
            const body = new Uint8Array(await res.arrayBuffer())
            const tag = `"${createHash('sha1').update(body).digest('base64url')}"`
            const headers = new Headers(res.headers)
            headers.set('etag', tag)
            if (said) headers.set(AREAS_HEADER, areas)
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
