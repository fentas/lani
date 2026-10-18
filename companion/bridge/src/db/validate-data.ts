// .claude/hooks/validate-data.py: Claude Code's PostToolUse hook after a Write or Edit (its JSON on stdin): a .json
// file in the data directory must still be JSON; a good one is backed up beside itself (<file>.backup-<time>, the
// last 10 kept). Exit 0 (fine, or not a data file), 1 (bad hook input, or an error), 2 (the file isn't JSON).
import { dataDir } from './paths'
import { endswith, excStr, fmtStamp, get, isExc, PyError, PyDict, strCmp, SysExit, truthy, type Py } from './py'
import { loads } from './pyjson'
import { baseName, checkPath, copy2, exists, globPrefix, isUnder, parent, pnorm, readText, resolve, unlink, type Ctx } from './sys'

const MAX_BACKUPS_PER_FILE = 10

/** rotate_backups: only the most recent [keep] <file>.backup-* files. */
function rotateBackups(filePath: string, keep = MAX_BACKUPS_PER_FILE) {
  const dir = parent(filePath)
  const stem = baseName(filePath)
  const backups = globPrefix(dir, `${stem}.backup-`).sort((a, b) => strCmp(b, a))
  for (const old of backups.slice(keep)) {
    try {
      unlink(old)
    } catch (e) {
      if (!isExc(e, 'OSError')) throw e
    }
  }
}

export function main(ctx: Ctx, _argv: string[], stdin: () => string): void {
  let hookInput: Py
  try {
    hookInput = loads(stdin())
  } catch (e) {
    if (!(e instanceof PyError) || e.type !== 'JSONDecodeError') throw e
    ctx.eprint(`Error: Invalid hook input: ${e.msg}`)
    throw new SysExit(1)
  }
  const toolInput = get(hookInput, 'tool_input', new PyDict())
  const filePath = get(toolInput, 'file_path', '')
  if (!truthy(filePath) || !endswith(filePath, '.json')) throw new SysExit(0)
  const path = filePath as string

  const data = resolve(dataDir(ctx), ctx.cwd)
  ctx.dataRoot = data
  try {
    checkPath(path)
    if (!isUnder(resolve(path, ctx.cwd), data)) throw new SysExit(0)
  } catch (e) {
    if (e instanceof SysExit || isExc(e, 'ValueError') || isExc(e, 'OSError')) throw new SysExit(0)
    throw e
  }
  if (!exists(path.startsWith('/') ? path : `${ctx.cwd}/${path}`)) throw new SysExit(0)

  const at = (p: string) => (p.startsWith('/') ? p : `${pnorm(ctx.cwd)}/${p}`)
  try {
    loads(readText(at(path)))
    const backupPath = `${path}.backup-${fmtStamp(ctx.now())}`
    copy2(at(path), at(backupPath))
    rotateBackups(at(path))
    ctx.print(`[Lani] ✓ Data saved and validated: ${path}`)
    ctx.print(`[Lani] 💾 Backup created: ${backupPath}`)
    throw new SysExit(0)
  } catch (e) {
    if (e instanceof SysExit) throw e
    if (e instanceof PyError && e.type === 'JSONDecodeError') {
      ctx.eprint(`[Lani] ⚠️  WARNING: Invalid JSON in ${path}`)
      ctx.eprint(`[Lani] Error: ${e.msg}`)
      throw new SysExit(2)
    }
    ctx.eprint(`[Lani] Error processing ${path}: ${excStr(e)}`)
    throw new SysExit(1)
  }
}
