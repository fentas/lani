// .claude/hooks/ensure_data_dir.py: print the learner's data directory, made when it is missing (the bridge's config
// asks for it on start).
import { ensureDataDir } from './paths'
import type { Ctx } from './sys'

export function main(ctx: Ctx): void {
  const d = ensureDataDir(ctx)
  ctx.dataRoot = d
  ctx.print(d)
}
