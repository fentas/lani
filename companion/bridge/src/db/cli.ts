// The learner-data scripts in TypeScript: run(script, argv, stdin, env) does what `python3 .claude/hooks/<script>.py
// argv… < stdin` does in [env], synchronously: the same stdout and stderr, exit status, files, backups and atomic
// writes. The phone's bridge (QuickJS, no Python) runs these; the node keeps running the Python, and the differential
// test (tests/test_db_port.py) keeps the two the same. docs/plans/05-built/db-port.md.
//
//   bun companion/bridge/src/db/cli.ts <script> [args…]   (read-db, update-db, card, validate-data, ensure_data_dir)
//
// Only node:fs (sync) is used, through sys.ts; the clock is Date, or LANI_NOW.
import * as fs from 'node:fs'
import { main as card } from './card'
import { main as ensureDataDir } from './ensure-data-dir'
import { adoptEnv, dataDir } from './paths'
import { SysExit, traceback } from './py'
import { main as readDb } from './read-db'
import { Ctx, recordWrites, utf8, type DbHooks, type Env } from './sys'
import { main as updateDb } from './update-db'
import { main as validateData } from './validate-data'

export type Script = 'read-db' | 'update-db' | 'card' | 'validate-data' | 'ensure_data_dir'
export type { DbHooks, Env }

export const SCRIPTS: Script[] = ['read-db', 'update-db', 'card', 'validate-data', 'ensure_data_dir']

export type RunOptions = {
  /** The working directory (the Python's os.getcwd()): ./data is looked for there. Default: env.PWD, else "/". */
  cwd?: string
  hooks?: DbHooks
}

/**
 * Runs [script] as `python3 .claude/hooks/<script>.py [argv]` would, with [stdin] (a string, or bytes decoded as the
 * Python decodes them) in [env]. What the Python does on import (lani_paths: FLUENT_* names, lani.env) is done here.
 */
export function run(
  script: Script,
  argv: string[],
  stdin: string | Uint8Array | undefined,
  env: Env,
  o: RunOptions = {},
): { status: number; stdout: string; stderr: string } {
  const ctx = new Ctx(env, o)
  let status = 0
  recordWrites(ctx.written)
  try {
    adoptEnv(ctx)
    const input = () => (stdin === undefined ? '' : typeof stdin === 'string' ? stdin : utf8(stdin))
    if (script === 'read-db') readDb(ctx, argv)
    else if (script === 'update-db') updateDb(ctx, argv, input)
    else if (script === 'card') card(ctx, argv)
    else if (script === 'validate-data') validateData(ctx, argv, input)
    else if (script === 'ensure_data_dir') ensureDataDir(ctx)
    else {
      ctx.err.push(`python3: can't open file '.claude/hooks/${script}.py': [Errno 2] No such file or directory\n`)
      status = 2
    }
  } catch (e) {
    if (e instanceof SysExit) status = e.code
    else {
      ctx.err.push(traceback(e))
      status = 1
    }
  } finally {
    recordWrites(null)
  }
  const hook = o.hooks?.afterWrite
  if (hook && (ctx.written.length || ctx.commit)) {
    try {
      const said = hook({
        script,
        dataDir: ctx.commit?.dataDir ?? ctx.dataRoot ?? '',
        paths: [...new Set(ctx.written)],
        ...(ctx.commit ? { commit: ctx.commit.message } : {}),
      })
      if (said) ctx.err.push(said.endsWith('\n') ? said : said + '\n')
    } catch (e) {
      if (ctx.commit) ctx.err.push(`[Lani] data repository: not committed (${e instanceof Error ? e.message : String(e)})\n`)
    }
  }
  return { status, stdout: ctx.out.join(''), stderr: ctx.err.join('') }
}

/**
 * lani_paths.data_dir() in [env] (what learners.ts's `python3 -c "… print(data_dir())"` prints, without its newline):
 * the learner's data directory, resolved as the scripts resolve it. Pure: it creates nothing.
 */
export function dataDirFor(env: Env, o: { cwd?: string } = {}): string {
  const ctx = new Ctx(env, o)
  adoptEnv(ctx)
  return dataDir(ctx)
}

/** The script a `python3 …/.claude/hooks/<name>.py` command line runs, or undefined. */
export function scriptOf(path: string): Script | undefined {
  const m = /(?:^|\/)\.claude\/hooks\/([\w-]+)\.py$/.exec(path)
  return m && (SCRIPTS as string[]).includes(m[1]) ? (m[1] as Script) : undefined
}

// Run as a command (bun …/db/cli.ts): only then is there a process to read from and write to. A script engine (the
// phone's) has no `process`, so this doesn't run there.
const host = (globalThis as { process?: NodeJS.Process }).process
if (host && /(^|\/)db\/cli\.[jt]s$/.test(host.argv?.[1] ?? '')) {
  const [script, ...args] = host.argv.slice(2)
  if (!script || !(SCRIPTS as string[]).includes(script)) {
    host.stderr.write(`usage: bun companion/bridge/src/db/cli.ts {${SCRIPTS.join(',')}} [args…]\n`)
    host.exitCode = 2
  } else {
    const stdin = script === 'update-db' || script === 'validate-data' ? (fs.readFileSync(0) as Uint8Array) : undefined
    const r = run(script as Script, args, stdin, host.env, { cwd: host.cwd() })
    // exitCode, not exit(): a pipe takes a long output after this returns, and exit() would cut it off
    host.stdout.write(r.stdout)
    host.stderr.write(r.stderr)
    host.exitCode = r.status
  }
}
