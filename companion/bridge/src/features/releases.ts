// App self-update: companion/bin/release-app writes <data>/app/release/{latest.json, lani-N.apk};
// the bridge announces a new version as an app_update event and serves the APK. Every learner's bridge
// serves the same releases (LANI_RELEASE_DIR).
import { existsSync, mkdirSync, readFileSync, watch } from 'node:fs'
import { join } from 'node:path'
import type { FeatureFactory } from '../feature'
import { json } from '../http'

type Release = { versionCode: number; versionName: string; file: string; notes?: string }

/** The APK is a plain file name next to latest.json. */
const APK = /^[\w.-]+\.apk$/

export const releases: FeatureFactory = ({ cfg, events, log }) => {
  // Another learner's bridge serves the default profile's releases (LANI_RELEASE_DIR, set by lani-session).
  const dir = process.env.LANI_RELEASE_DIR || join(cfg.appDir, 'release')
  mkdirSync(dir, { recursive: true })

  /** The current release; undefined when there is none or latest.json can't be read. */
  function latest(): Release | undefined {
    const p = join(dir, 'latest.json')
    if (!existsSync(p)) return undefined
    try {
      return JSON.parse(readFileSync(p, 'utf8'))
    } catch (e) {
      log(`release: ${p} unreadable: ${(e as Error).message}`)
      return undefined
    }
  }

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
          const r = latest()
          if (!r || typeof r.file !== 'string' || !APK.test(r.file) || !existsSync(join(dir, r.file))) return json({ error: 'no release' }, 404)
          return new Response(Bun.file(join(dir, r.file)), { headers: { 'content-type': 'application/vnd.android.package-archive' } })
        },
      },
    ],
    start() {
      let announced = latest()?.versionCode ?? 0
      watch(dir, () => {
        // Never throw here: an exception in a watcher would end the process.
        const r = latest()
        if (r && typeof r.versionCode === 'number' && r.versionCode > announced) {
          announced = r.versionCode
          events.emit({ type: 'app_update', versionCode: r.versionCode, versionName: r.versionName, notes: r.notes })
        }
      })
    },
  }
}
