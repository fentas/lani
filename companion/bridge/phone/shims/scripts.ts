// What the phone's bridge does instead of running a program (the Bun.spawnSync and child_process shims, bun.ts and
// child_process.ts): the learner-data scripts a node runs with Python (`python3 .claude/hooks/<name>.py …`: read-db,
// update-db, card, validate-data) go to a function registered for them, the TypeScript port (companion/bridge/src/db,
// registered by src/phone/entry.ts); git answers as outside a repository, so the bridge leaves the data's git alone; any
// other program isn't there (ENOENT), as on a machine without it.

export type ScriptRun = (argv: string[], stdin: string | undefined, env: Record<string, string | undefined>) => { status: number; stdout: string; stderr: string }

const scripts = new Map<string, ScriptRun>()

/** [name] (read-db, update-db, card, validate-data …) runs [run] when the bridge runs `python3 .claude/hooks/<name>.py`. */
export function registerScript(name: string, run: ScriptRun) {
  scripts.set(name, run)
}

export const registeredScripts = () => [...scripts.keys()]

export type Ran = { status: number; stdout: string; stderr: string } | { missing: string }

const HOOK = /^(?:.*\/)?\.claude\/hooks\/([\w.-]+)\.py$/

/** Runs [cmd] with [args] as the phone can: a registered script, git's "not a git repository", or nothing (missing). */
export function dispatch(cmd: string, args: string[], stdin: string | undefined, env: Record<string, string | undefined>): Ran {
  const name = cmd.split('/').pop() ?? cmd
  if (/^python3?$/.test(name)) {
    const m = HOOK.exec(args[0] ?? '')
    if (!m) return { status: 2, stdout: '', stderr: `${name}: the phone runs only the learner-data scripts (.claude/hooks/<name>.py), not ${args.join(' ')}\n` }
    const run = scripts.get(m[1])
    if (!run) return { status: 127, stdout: '', stderr: `${m[1]}.py is not on this device: its TypeScript port isn't registered (src/phone/entry.ts)\n` }
    try {
      return run(args.slice(1), stdin, env)
    } catch (e) {
      return { status: 1, stdout: '', stderr: `${m[1]}: ${(e as Error)?.stack ?? e}\n` }
    }
  }
  if (name === 'git') return { status: 128, stdout: '', stderr: 'fatal: not a git repository (or any of the parent directories): .git\n' }
  return { missing: cmd }
}

/** The error a missing program throws, as Bun's and node's spawn do. */
export function enoent(cmd: string, syscall = 'posix_spawn'): Error & { code: string; errno: number; syscall: string; path: string } {
  return Object.assign(new Error(`ENOENT: no such file or directory, ${syscall} '${cmd}'`), { code: 'ENOENT', errno: -2, syscall, path: cmd })
}
