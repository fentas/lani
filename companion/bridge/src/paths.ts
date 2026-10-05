// Where a learner's caches and results live (docs/setup.md, "Where things live"). The learner data is a git repository
// of its own (lani-setup); what can be made or fetched again stays out of it:
// - the voice clips: $LANI_VOICE_CACHE (lani.env's for the default learner; ~/.cache/lani/voice after lani-setup), else
//   <data>/app/voice as before;
// - the released APKs: $LANI_RELEASE_DIR (~/.local/share/lani/releases after lani-setup), else <data>/app/release.
// A node that isn't moved yet keeps working: while only the old place has the clips (or a release), it is used.
// The same rules for Python are in .claude/hooks/lani_paths.py (voice_dir_of, results_dir_of).
import { existsSync } from 'node:fs'
import { homedir } from 'node:os'
import { join, resolve } from 'node:path'
import { expandHome, setting } from './env'

type Env = Record<string, string | undefined>

const home = (env: Env) => env.HOME || homedir()

/** The voice store's directory (voice.db, files/) of the learner whose app directory is [appDir]. */
export function voiceDir(appDir: string, env: Env = process.env): string {
  const old = join(appDir, 'voice')
  const configured = setting('LANI_VOICE_CACHE', env)
  if (!configured) return old
  const dir = resolve(expandHome(configured, home(env)))
  return !existsSync(join(dir, 'voice.db')) && existsSync(join(old, 'voice.db')) ? old : dir
}

/**
 * The release directories, the configured one first: $LANI_RELEASE_DIR, then [appDir]/release (where release-app wrote
 * before, read while the configured one has no release yet). Without the setting, only the old one.
 */
export function releaseDirs(appDir: string, env: Env = process.env): string[] {
  const old = join(appDir, 'release')
  const configured = env.LANI_RELEASE_DIR ? resolve(expandHome(env.LANI_RELEASE_DIR, home(env))) : undefined
  return configured && configured !== old ? [configured, old] : [old]
}

/** The release directory the app's update comes from now: the first of [releaseDirs] with a latest.json (else the first). */
export function releaseDir(appDir: string, env: Env = process.env): string {
  const dirs = releaseDirs(appDir, env)
  return dirs.find(d => existsSync(join(d, 'latest.json'))) ?? dirs[0]
}

/**
 * Where the tutor's session result files (results/*.md) of the learner whose data is [dataDir] go: $LANI_RESULTS_DIR
 * (lani.env's for the default learner); in a data repository (lani-setup) its results/; else next to the data (the
 * checkout's results/, a profile's profiles/<id>/results/).
 */
export function resultsDirOf(dataDir: string, env: Env = process.env): string {
  const configured = setting('LANI_RESULTS_DIR', env)
  if (configured) return resolve(expandHome(configured, home(env)))
  if (existsSync(join(dataDir, '.git')) || existsSync(join(dataDir, 'results'))) return join(dataDir, 'results')
  return resolve(dataDir, '..', 'results')
}
