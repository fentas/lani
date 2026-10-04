// A node set up before the project was renamed from Fluent to Lani (docs/migrate-from-fluent.md) keeps working: every
// LANI_* variable falls back to its old FLUENT_* name, and the directories named lani (~/.config/lani,
// ~/.local/state/lani, ~/.cache/lani) to the ones named fluent while only those exist. Entry points import this module
// first, so the rest of the code reads LANI_* only.
import { existsSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'

const LEGACY_PREFIX = 'FLUENT_'

/** Copies each FLUENT_X of [env] into LANI_X when LANI_X isn't set (LANI_X wins). Returns [env]. */
export function adoptLegacyEnv<T extends Record<string, string | undefined>>(env: T): T {
  for (const [k, v] of Object.entries(env)) {
    if (!k.startsWith(LEGACY_PREFIX) || v === undefined) continue
    const name = `LANI_${k.slice(LEGACY_PREFIX.length)}`
    if (env[name] === undefined) (env as Record<string, string | undefined>)[name] = v
  }
  return env
}

adoptLegacyEnv(process.env)

/** [base]/lani, or [base]/fluent while only that one exists (a node from before the rename). */
export function laniDir(base: string): string {
  const dir = join(base, 'lani')
  const legacy = join(base, 'fluent')
  return !existsSync(dir) && existsSync(legacy) ? legacy : dir
}

/** ~/.config/lani ($LANI_CONFIG_DIR), or ~/.config/fluent while only that one exists. */
export const configDir = (env: Record<string, string | undefined> = process.env) =>
  env.LANI_CONFIG_DIR || laniDir(join(env.HOME || homedir(), '.config'))

/** A file of the config directory: under ~/.config/lani, or under ~/.config/fluent while only the old one has it. */
export function configFile(name: string, env: Record<string, string | undefined> = process.env): string {
  if (env.LANI_CONFIG_DIR) return join(env.LANI_CONFIG_DIR, name)
  const home = env.HOME || homedir()
  const file = join(home, '.config', 'lani', name)
  const legacy = join(home, '.config', 'fluent', name)
  return !existsSync(file) && existsSync(legacy) ? legacy : file
}

/** The API keys the tutor session and the bridge service load: $LANI_KEYS_FILE, else keys.env in the config directory. */
export const keysFile = (env: Record<string, string | undefined> = process.env) => env.LANI_KEYS_FILE || configFile('keys.env', env)
