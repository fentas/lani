// node:child_process for the phone's bridge: spawnSync and execFileSync over the dispatcher (scripts.ts); nothing runs
// asynchronously (spawn, exec: ENOENT, as a program that isn't there).
import { Buffer } from './buffer'
import { dispatch, enoent } from './scripts'

type Opts = { cwd?: string; env?: Record<string, string | undefined>; input?: string | Uint8Array; encoding?: string; timeout?: number; stdio?: unknown }

const text = (x: string | Uint8Array | undefined) => (x === undefined ? undefined : typeof x === 'string' ? x : Buffer.view(x).toString())

export function spawnSync(cmd: string, args: readonly string[] | Opts = [], o: Opts = {}) {
  const argv = Array.isArray(args) ? [...args] : []
  const opts = Array.isArray(args) ? o : (args as Opts)
  const env = { ...(globalThis as unknown as { process: { env: Record<string, string> } }).process.env, ...(opts.env ?? {}) }
  const r = dispatch(cmd, argv, text(opts.input), env, opts.cwd)
  const out = (s: string) => (opts.encoding && opts.encoding !== 'buffer' ? s : Buffer.from(s))
  if ('missing' in r) {
    const error = enoent(cmd, `spawnSync ${cmd}`)
    return { pid: 0, output: [null, out(''), out('')], stdout: out(''), stderr: out(''), status: null, signal: null, error }
  }
  return { pid: 0, output: [null, out(r.stdout), out(r.stderr)], stdout: out(r.stdout), stderr: out(r.stderr), status: r.status, signal: null }
}

export function execFileSync(cmd: string, args: readonly string[] = [], o: Opts = {}) {
  const r = spawnSync(cmd, args, o)
  if (r.error) throw r.error
  if (r.status !== 0) throw Object.assign(new Error(`Command failed: ${cmd} ${args.join(' ')}\n${String(r.stderr)}`), { status: r.status, stdout: r.stdout, stderr: r.stderr })
  return r.stdout
}

export function execSync(cmd: string): never {
  throw enoent(cmd.split(/\s+/)[0], 'spawnSync /bin/sh')
}

export function spawn(cmd: string): never {
  throw enoent(cmd, 'spawn')
}

export const exec = execSync
export const execFile = spawn

export default { spawnSync, execFileSync, execSync, spawn, exec, execFile }
