// App self-update: companion/bin/release-app writes latest.json and lani-N.apk into the release directory
// ($LANI_RELEASE_DIR: ~/.local/share/lani/releases after lani-setup; else <data>/app/release, which is also read while
// the configured one has no release yet: paths.ts releaseDirs). The bridge announces a new version as an app_update
// event and serves the APK. Every learner's bridge serves the same releases.
import { existsSync, mkdirSync, readFileSync, watch } from 'node:fs'
import { join } from 'node:path'
import type { FeatureFactory } from '../feature'
import { json } from '../http'
import { releaseDirs } from '../paths'

type Release = { versionCode: number; versionName: string; file: string; notes?: string }

/** The APK is a plain file name next to latest.json. */
const APK = /^[\w.-]+\.apk$/

export const releases: FeatureFactory = ({ cfg, events, log }) => {
  // Another learner's bridge serves the default profile's releases (LANI_RELEASE_DIR, set by lani-session).
  const dirs = releaseDirs(cfg.appDir)
  mkdirSync(dirs[0], { recursive: true })

  /** The current release and its directory: the first directory with a latest.json; undefined when it can't be read. */
  function current(): { release: Release; dir: string } | undefined {
    const dir = dirs.find(d => existsSync(join(d, 'latest.json')))
    if (!dir) return undefined
    const p = join(dir, 'latest.json')
    try {
      return { release: JSON.parse(readFileSync(p, 'utf8')), dir }
    } catch (e) {
      log(`release: ${p} unreadable: ${(e as Error).message}`)
      return undefined
    }
  }
  const latest = () => current()?.release

  return {
    routes: [
      {
        method: 'GET',
        path: '/app/latest',
        handle: () => {
          const r = latest()
          return r ? json(r) : json({ error: 'no release' }, 404)
        },
      },
      {
        method: 'GET',
        path: '/app/apk',
        handle: () => {
          const c = current()
          const r = c?.release
          if (!c || !r || typeof r.file !== 'string' || !APK.test(r.file) || !existsSync(join(c.dir, r.file))) return json({ error: 'no release' }, 404)
          return new Response(Bun.file(join(c.dir, r.file)), { headers: { 'content-type': 'application/vnd.android.package-archive' } })
        },
      },
    ],
    start() {
      let announced = latest()?.versionCode ?? 0
      const check = () => {
        // Never throw here: an exception in a watcher would end the process.
        const r = latest()
        if (r && typeof r.versionCode === 'number' && r.versionCode > announced) {
          announced = r.versionCode
          events.emit({ type: 'app_update', versionCode: r.versionCode, versionName: r.versionName, notes: r.notes })
        }
      }
      // the configured directory, and the old one while it exists (release-app writes only to the configured one)
      for (const d of dirs) {
        try {
          if (existsSync(d)) watch(d, check)
        } catch (e) {
          log(`release: can't watch ${d}: ${(e as Error).message}`)
        }
      }
    },
  }
}
