// What the learner's data repository never commits, made sure of by the bridge (docs/setup.md, "Your data repository"):
// files the app sends that stay on this machine (the developer's data, app/dev/: features/dev.ts; the tutor chat's
// photos, app/chat-images/: chat-images.ts). lani-setup's .gitignore (.claude/hooks/lani_data_repo.py GITIGNORE) has
// their lines, and each commit adds a missing one back (PRIVATE); the bridge appends one at its start to a .gitignore
// written before it, so the files are out also before the next commit.
import { appendFileSync, existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import type { Log } from './config'

/** A line of the .gitignore that keeps a directory of the data out, and how to tell it is there. */
export type PrivateLine = {
  /** The line, a directory from the data's root: "/app/dev/". */
  line: string
  /** Paths the line keeps out, for git check-ignore (another line of the learner's may do it already). */
  samples: string[]
  /** The comment over it, one line: what the files are and that the bridge added it. */
  comment: string
}

/** Lines of a .gitignore that keep [line]'s directory out as it does ("/app/dev/", "app/dev", "/app/dev/**" …). */
function sameLines(line: string): Set<string> {
  const core = line.replace(/^\/+|\/+$/g, '')
  return new Set([`/${core}/`, `/${core}`, `${core}/`, core, `/${core}/*`, `/${core}/**`])
}

/** Whether git ignores [rel] in the repository [dataDir] (its .gitignore and the user's excludes). */
export function gitIgnores(dataDir: string, rel: string): boolean {
  const env: Record<string, string | undefined> = { ...process.env, GIT_TERMINAL_PROMPT: '0' }
  for (const k of ['GIT_DIR', 'GIT_WORK_TREE', 'GIT_INDEX_FILE', 'GIT_COMMON_DIR']) delete env[k]
  try {
    return Bun.spawnSync(['git', '-C', dataDir, 'check-ignore', '-q', '--no-index', '--', rel], { env, stdout: 'ignore', stderr: 'ignore' }).exitCode === 0
  } catch {
    return false // no git on this machine: the line goes in
  }
}

/**
 * The learner's data repository keeps [p]'s directory out: a .gitignore (or a repository) without a line that does gets
 * [PrivateLine.line] appended, once, under its comment. A plain data directory without a .gitignore is left alone.
 * Returns whether it added it.
 */
export function keepOut(dataDir: string, p: PrivateLine, log: Log): boolean {
  const file = join(dataDir, '.gitignore')
  const repo = existsSync(join(dataDir, '.git'))
  if (!repo && !existsSync(file)) return false
  const text = existsSync(file) ? readFileSync(file, 'utf8') : ''
  const same = sameLines(p.line)
  if (text.split('\n').some(l => same.has(l.trim()))) return false
  if (repo && p.samples.every(rel => gitIgnores(dataDir, rel))) return false
  const sep = text && !text.endsWith('\n') ? '\n' : ''
  appendFileSync(file, `${sep}\n# ${p.comment}\n${p.line}\n`)
  log(`${file} now keeps ${p.line} out`)
  return true
}
