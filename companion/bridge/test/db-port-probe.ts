// The TypeScript port's Python building blocks, for tests/test_db_port.py to compare with Python itself: reads a JSON
// list of cases on stdin, prints a JSON list of results (one line each). A case:
//   ["json", text]               json.loads(text), then json.dumps(indent=2) and json.dumps(); or the JSONDecodeError
//   ["round", x, nd]             round(float(x), nd) (nd null: round(x)), repr'd
//   ["fixed", x, nd]             f"{float(x):.{nd}f}"
//   ["repr", x]                  repr(float(x))
//   ["date", s, n]               (datetime.strptime(s, "%Y-%m-%d") + timedelta(days=n)).strftime(...), or the error
//   ["normdate", v]              [lani_languages.norm_date(v), lani_languages.date_of(v)]
//   ["int", s] / ["float", s]    int(s) / float(s), or the error
//   ["str", op, s]               s.strip() / s.lower() / repr(s) / len(s)
//   ["run", script, argv, stdin, env, cwd]   cli.ts run() in this process, with a hook that records its calls:
//                                {status, stdout, stderr, calls}
//   ["datadir", env, cwd]        cli.ts dataDirFor(): lani_paths.data_dir()
// Floats travel as their repr (a string), so that nothing is lost on the way.
import { readFileSync } from 'node:fs'
import { F, float, fixed, floatRepr, fmtDate, int, isExc, len, lower, plusDays, repr, round, str, strip, strptime, type Py, PyError } from '../src/db/py'
import { dumpIndented, dumpsCompact, loads } from '../src/db/pyjson'
import { dataDirFor, run, type Script } from '../src/db/cli'
import { dateOf, normDate } from '../src/db/languages'

const err = (e: unknown) => (e instanceof PyError ? `${e.type}: ${e.msg}` : `JS: ${String(e)}`)

function one(c: unknown[]): unknown {
  const [kind] = c
  if (kind === 'datadir') return dataDirFor(c[1] as Record<string, string>, { cwd: c[2] as string })
  if (kind === 'run') {
    const [, script, argv, stdin, env, cwd] = c as [string, Script, string[], string | null, Record<string, string>, string]
    const calls: unknown[] = []
    const r = run(script, argv, stdin ?? undefined, env, {
      cwd,
      hooks: {
        afterWrite: e => {
          calls.push(e)
          return e.commit ? `[Lani] data repository: committed ${e.commit.split('\n')[0]}` : undefined
        },
      },
    })
    return { ...r, calls }
  }
  try {
    if (kind === 'json') {
      const v = loads(c[1] as string)
      return [dumpIndented(v), dumpsCompact(v)]
    }
    if (kind === 'round') return repr(round(float(c[1] as string), c[2] === null ? undefined : (c[2] as number)))
    if (kind === 'fixed') return fixed(float(c[1] as string).v, c[2] as number)
    if (kind === 'repr') return floatRepr(float(c[1] as string).v)
    if (kind === 'date') return fmtDate(plusDays(strptime(c[1] as string), c[2] as Py))
    if (kind === 'normdate') return [normDate(c[1] as Py), dateOf(c[1] as Py)]
    if (kind === 'int') return str(int(c[1] as string))
    if (kind === 'float') return repr(float(c[1] as string))
    if (kind === 'str') {
      const s = c[2] as string
      if (c[1] === 'strip') return strip(s)
      if (c[1] === 'lower') return lower(s)
      if (c[1] === 'repr') return repr(s)
      if (c[1] === 'len') return len(s)
    }
    return `unknown case ${String(kind)}`
  } catch (e) {
    if (isExc(e, 'Exception')) return err(e)
    throw e
  }
}

const cases = JSON.parse(readFileSync(0, 'utf8')) as unknown[][]
process.stdout.write(cases.map(c => JSON.stringify(one(c))).join('\n') + '\n')
void F
