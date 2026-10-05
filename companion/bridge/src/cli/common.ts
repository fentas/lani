// What the profile CLIs share: the project, the profiles (the default one and the registry's), their
// data directories and their tailnet URLs.
import { spawnSync } from 'node:child_process'
import { join, resolve } from 'node:path'
import { controlSocket } from '../control'
import { DEFAULT_CULTURE } from '../cultures'
import { setting } from '../env'
import { releaseDir } from '../paths'
import { DEFAULT_ID, DEFAULT_PROFILE, defaultDataDir, entryDataDir, learnerFacts, profilesDir, readRegistry, type ProfileEntry } from '../learners'

export const projectDir = process.env.CLAUDE_PROJECT_DIR ?? resolve(import.meta.dir, '../../../..')
export const registryDir = profilesDir(projectDir)

/** A profile as the CLIs see it: the default one, or a registry entry, with its data directory resolved. */
export type Resolved = {
  id: string
  isDefault: boolean
  name: string
  target?: string
  base?: string
  /** Their village's culture pack (companion/cultures/<id>). */
  culture: string
  child: boolean
  port: number
  httpsPort: number
  tmux: string
  remoteControl: string
  dataDir: string
  entry?: ProfileEntry
}

export function allProfiles(): Resolved[] {
  return [resolveProfile(DEFAULT_ID), ...readRegistry(registryDir).profiles.map(e => fromEntry(e))]
}

function fromEntry(e: ProfileEntry): Resolved {
  const dataDir = entryDataDir(registryDir, e)
  return {
    id: e.id,
    isDefault: false,
    name: learnerFacts(dataDir).name || e.name,
    target: e.target,
    base: e.base,
    culture: e.culture ?? DEFAULT_CULTURE,
    child: e.child,
    port: e.port,
    httpsPort: e.https_port,
    tmux: e.tmux,
    remoteControl: e.remote_control,
    dataDir,
    entry: e,
  }
}

/** The profile [id] ("default" or none: today's setup); exits with a message when there is no such profile. */
export function resolveProfile(id?: string): Resolved {
  if (!id || id === DEFAULT_ID) {
    const dataDir = defaultDataDir(projectDir)
    return {
      id: DEFAULT_ID,
      isDefault: true,
      name: learnerFacts(dataDir).name || 'default',
      // the default learner's village and port may be set in lani.env (lani-setup)
      culture: setting('LANI_CULTURE')?.trim() || DEFAULT_CULTURE,
      child: false,
      port: Number(setting('LANI_BRIDGE_PORT') ?? DEFAULT_PROFILE.port),
      httpsPort: DEFAULT_PROFILE.https_port,
      tmux: DEFAULT_PROFILE.tmux,
      remoteControl: DEFAULT_PROFILE.remote_control,
      dataDir,
    }
  }
  const e = readRegistry(registryDir).profiles.find(p => p.id === id)
  if (!e) die(`no profile "${id}" (see: companion/bin/lani-session --list)`)
  return fromEntry(e)
}

export const appDir = (p: Resolved) => join(p.dataDir, 'app')

/**
 * The environment a profile's bridge needs (lani-session exports it for the classic bridge, lani-bridge for the
 * service): nothing for the default profile; for another learner, their data, port, culture and the rest. Without
 * LANI_TUTOR: the caller adds it for the classic tutor session.
 */
export function bridgeEnv(p: Resolved): Record<string, string> {
  if (p.isDefault || !p.entry) return {}
  return {
    ...(p.entry.env ?? {}),
    LANI_PROFILE: p.id,
    LANI_DATA_DIR: p.dataDir,
    LANI_BRIDGE_PORT: String(p.port),
    LANI_CULTURE: p.culture,
    ...(p.entry.landscape ? { LANI_LANDSCAPE: p.entry.landscape } : {}),
    // The app updates itself from its bridge: every learner's bridge serves the default profile's releases
    // ($LANI_RELEASE_DIR, ~/.local/share/lani/releases after lani-setup; else the default's <data>/app/release).
    LANI_RELEASE_DIR: releaseDir(join(defaultDataDir(projectDir), 'app')),
    ...(p.child ? { LANI_CHILD: '1' } : {}),
  }
}

/** The control socket of [p]'s bridge service (docs/plans/04-bridge-service.md); LANI_BRIDGE_SOCKET in this shell doesn't count. */
export const serviceSocket = (p: Resolved) => controlSocket(appDir(p), { ...process.env, LANI_BRIDGE_SOCKET: '' })

/** The tmux session of [p]'s bridge service. */
export const serviceTmux = (p: Resolved) => (p.isDefault ? 'lani-bridge' : `lani-bridge-${p.id}`)

/** 'x' → `'x'`, safe inside a shell command line. */
export const q = (s: string) => `'${s.replace(/'/g, `'\\''`)}'`

/** This node's tailnet name (node.tailnet.ts.net): $LANI_TAILNET_HOST, else `tailscale status`; undefined when unknown. */
export function tailnetHost(): string | undefined {
  if (process.env.LANI_TAILNET_HOST) return process.env.LANI_TAILNET_HOST
  const p = spawnSync('tailscale', ['status', '--json'], { encoding: 'utf8', timeout: 5_000 })
  if (p.status !== 0) return undefined
  try {
    return String(JSON.parse(p.stdout)?.Self?.DNSName ?? '').replace(/\.$/, '') || undefined
  } catch {
    return undefined
  }
}

/** The HTTPS URL the app uses for [p], once `tailscale serve` publishes it. */
export const tailnetUrl = (p: Resolved, host = tailnetHost() ?? '<node>.<tailnet>.ts.net') => `https://${host}${p.httpsPort === 443 ? '' : `:${p.httpsPort}`}`

/** The URL the default learner's app uses when lani.env names it (LANI_PUBLIC_URL: a reverse proxy, a LAN name). */
export const publicUrl = (p: Resolved) => (p.isDefault ? setting('LANI_PUBLIC_URL')?.trim() || undefined : undefined)

/** The command that publishes [p]'s bridge to the tailnet. The default's is the one in the README. */
export const serveCommand = (p: Resolved) => (p.httpsPort === 443 ? `tailscale serve --bg ${p.port}` : `tailscale serve --bg --https=${p.httpsPort} http://127.0.0.1:${p.port}`)

/** Whether a bridge answers on [port]. */
export async function bridgeUp(port: number): Promise<boolean> {
  try {
    const r = await fetch(`http://127.0.0.1:${port}/health`, { signal: AbortSignal.timeout(1_500) })
    return r.ok
  } catch {
    return false
  }
}

/** Whether tmux has a session called [name]. */
export function tmuxUp(name: string): boolean {
  return spawnSync('tmux', ['has-session', '-t', `=${name}`], { stdio: 'ignore' }).status === 0
}

export function die(msg: string): never {
  console.error(msg)
  process.exit(1)
}

/** "~/…" for paths under the home directory, "…" relative to the project for paths in it. */
export function short(path: string): string {
  if (path.startsWith(projectDir + '/')) return path.slice(projectDir.length + 1)
  const home = process.env.HOME
  return home && path.startsWith(home + '/') ? `~${path.slice(home.length)}` : path
}

/** A plain text table, columns padded. */
export function table(rows: string[][]): string {
  const widths = rows[0].map((_, i) => Math.max(...rows.map(r => [...(r[i] ?? '')].length)))
  return rows.map(r => r.map((c, i) => (i === r.length - 1 ? c : c + ' '.repeat(widths[i] - [...c].length))).join('  ').trimEnd()).join('\n')
}
