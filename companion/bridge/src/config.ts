// Where the project and the learner data live, and how the bridge runs the repo's helper scripts.
import { mkdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { setting } from './env'

export type Log = (...a: unknown[]) => void

/** stdout is the MCP transport: everything the bridge says goes to stderr. */
export const log: Log = (...a) => console.error('[lani-bridge]', ...a)

export type Config = {
  projectDir: string
  host: string
  port: number
  /**
   * Whether this bridge serves the app's HTTP API: the bridge service always (docs/plans/04-bridge-service.md);
   * in the classic mode only in the tutor session (lani-session sets LANI_TUTOR=1), or with an explicit
   * LANI_BRIDGE_PORT (profiles, QA, the smoke tests). Any other Claude Code session in the repo spawns a
   * bridge too, from .mcp.json; it keeps its MCP tools, and the tutor's bridge gets the port.
   */
  serveApp: boolean
  /** The learner data directory (the 6 databases). */
  dataDir: string
  /** <data>/app: everything the bridge and the app own. */
  appDir: string
  /** The family page's static files. */
  webDir: string
}

const here = dirname(import.meta.path)

/** Runs [args] in the project directory; throws with stderr when it fails. */
export function run(projectDir: string, args: string[], stdin?: string): string {
  const p = spawn(projectDir, args, stdin)
  if (p.exitCode !== 0) throw new Error(`${args.join(' ')} failed: ${p.stderr}`)
  return p.stdout
}

/** Secrets the helper scripts don't need: they stay in the bridge (under their names from before the rename too). */
const SECRETS = ['ELEVENLABS_API_KEY', 'LANI_BRIDGE_TOKEN', 'LANI_FAMILY_TOKEN', 'FLUENT_BRIDGE_TOKEN', 'FLUENT_FAMILY_TOKEN']

/** Runs [args] in the project directory and returns what it printed, without throwing on failure. */
export function spawn(projectDir: string, args: string[], stdin?: string): { exitCode: number | null; stdout: string; stderr: string } {
  const env: Record<string, string | undefined> = { ...process.env, CLAUDE_PROJECT_DIR: projectDir }
  for (const k of SECRETS) delete env[k]
  const p = Bun.spawnSync(args, {
    cwd: projectDir,
    env,
    ...(stdin !== undefined ? { stdin: Buffer.from(stdin) } : {}),
  })
  return { exitCode: p.exitCode, stdout: p.stdout.toString(), stderr: p.stderr.toString() }
}

/** [o.service]: the bridge service, which serves the app whatever the environment says. */
export function loadConfig(env = process.env, o: { service?: boolean } = {}): Config {
  const projectDir = env.CLAUDE_PROJECT_DIR ?? resolve(here, '../../..')
  const dataDir = run(projectDir, ['python3', '.claude/hooks/ensure_data_dir.py']).trim()
  const appDir = join(dataDir, 'app')
  mkdirSync(appDir, { recursive: true })
  return {
    projectDir,
    host: env.LANI_BRIDGE_HOST ?? '127.0.0.1',
    // The default learner's port may be set in lani.env; only a port in the environment makes a bridge serve the app.
    port: Number(setting('LANI_BRIDGE_PORT', env) ?? 8790),
    serveApp: !!o.service || env.LANI_TUTOR === '1' || !!env.LANI_BRIDGE_PORT,
    dataDir,
    appDir,
    webDir: resolve(here, '../web/family'),
  }
}
