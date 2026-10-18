// argparse, as far as read-db.py and card.py use it: the same parsing (options before or after a subcommand, --opt=x,
// unambiguous prefixes, "--", a negative number as a value, store_true, int and choices) and the same errors, usage
// and help texts (argtexts.ts). A line-by-line port of Python 3.14's ArgumentParser._parse_known_args.
import { TEXTS } from './argtexts'
import { int, isExc, repr, str as pyStr, SysExit, type Py } from './py'
import type { Ctx } from './sys'

export const SUPPRESS = Symbol('SUPPRESS')

export type Action = {
  dest: string | typeof SUPPRESS
  optionStrings: string[]
  /** null: one argument; '?': at most one; 'PARSER': a subcommand and what follows; 0: none */
  nargs: null | '?' | 'PARSER' | 0
  kind: 'store' | 'store_true' | 'help' | 'parsers'
  type?: 'int'
  choices?: (string | number)[]
  required?: boolean
  default?: Py | typeof SUPPRESS
  metavar?: string
  parsers?: Map<string, Parser>
}

export type Namespace = Map<string, Py>

class ArgumentError {
  constructor(
    readonly action: Action | null,
    readonly message: string,
  ) {}
  toString() {
    return this.action ? `argument ${actionName(this.action)}: ${this.message}` : this.message
  }
}

function actionName(a: Action): string {
  if (a.optionStrings.length) return a.optionStrings.join('/')
  if (a.metavar !== undefined) return a.metavar
  if (a.dest !== SUPPRESS) return a.dest
  return `{${[...(a.parsers?.keys() ?? [])].join(',')}}`
}

export class Parser {
  readonly actions: Action[] = []
  readonly options = new Map<string, Action>()

  constructor(
    readonly ctx: Ctx,
    /** argparse's prog: 'card.py', 'card.py show' … (and the key of its texts) */
    readonly prog: string,
    readonly textKey: string,
  ) {
    this.add({ dest: SUPPRESS, optionStrings: ['-h', '--help'], nargs: 0, kind: 'help', default: SUPPRESS })
  }

  add(a: Action): Action {
    const action: Action = { default: a.kind === 'store_true' ? false : null, required: false, ...a }
    if (action.optionStrings.length === 0 && action.nargs === null) action.required = true
    this.actions.push(action)
    for (const o of action.optionStrings) this.options.set(o, action)
    return action
  }

  /** add_argument(name, …): an option when [name] starts with '-' (its dest the name without them). */
  arg(name: string, o: Partial<Action> = {}): Action {
    if (name.startsWith('-')) {
      return this.add({ dest: name.replace(/^-+/, '').replaceAll('-', '_'), optionStrings: [name], nargs: null, kind: 'store', ...o })
    }
    return this.add({ dest: name, optionStrings: [], nargs: null, kind: 'store', ...o })
  }

  usage(): string {
    return TEXTS[this.textKey].usage
  }

  error(message: string): never {
    this.ctx.err.push(this.usage(), `${this.prog}: error: ${message}\n`)
    throw new SysExit(2)
  }

  parseArgs(args: string[]): Namespace {
    const [ns, extras] = this.parseKnownArgs(args, new Map())
    if (extras.length) this.error(`unrecognized arguments: ${extras.join(' ')}`)
    return ns
  }

  parseKnownArgs(args: string[], ns: Namespace): [Namespace, string[]] {
    for (const a of this.actions) {
      if (a.dest !== SUPPRESS && !ns.has(a.dest) && a.default !== SUPPRESS) ns.set(a.dest, a.default as Py)
    }
    let extras: string[]
    try {
      extras = this.parse(args, ns)
    } catch (e) {
      if (e instanceof ArgumentError) this.error(e.toString())
      throw e
    }
    const unknown = ns.get('\0unrecognized') as string[] | undefined
    if (unknown) {
      extras.push(...unknown)
      ns.delete('\0unrecognized')
    }
    return [ns, extras]
  }

  private parseOptional(arg: string): [Action | null, string, string | null, string | null][] | null {
    if (!arg) return null
    if (arg[0] !== '-') return null
    const known = this.options.get(arg)
    if (known) return [[known, arg, null, null]]
    if (arg.length === 1) return null
    const eq = arg.indexOf('=')
    if (eq >= 0) {
      const opt = arg.slice(0, eq)
      const a = this.options.get(opt)
      if (a) return [[a, opt, '=', arg.slice(eq + 1)]]
    }
    const tuples = this.optionTuples(arg)
    if (tuples.length) return tuples
    if (/^-\.?\p{Nd}/u.test(arg)) return null
    if (arg.includes(' ')) return null
    return [[null, arg, null, null]]
  }

  private optionTuples(opt: string): [Action | null, string, string | null, string | null][] {
    const out: [Action | null, string, string | null, string | null][] = []
    const eq = opt.indexOf('=')
    const prefix = eq >= 0 ? opt.slice(0, eq) : opt
    const sep = eq >= 0 ? '=' : null
    const explicit = eq >= 0 ? opt.slice(eq + 1) : null
    if (opt[1] === '-') {
      for (const [o, a] of this.options) if (o.startsWith(prefix)) out.push([a, o, sep, explicit])
    } else {
      const short = opt.slice(0, 2)
      const shortExplicit = opt.slice(2)
      for (const [o, a] of this.options) {
        if (o === short) out.push([a, o, '', shortExplicit])
        else if (o.startsWith(prefix)) out.push([a, o, sep, explicit])
      }
    }
    return out
  }

  private nargsPattern(a: Action): string {
    const opt = a.optionStrings.length > 0
    if (a.nargs === null) return opt ? '([A])' : '(-*A-*)'
    if (a.nargs === '?') return opt ? '(A?)' : '(-*A?-*)'
    if (a.nargs === 'PARSER') return opt ? '(A[AO]*)' : '(-*A[-AO]*)'
    return opt ? '([AO]{0})' : '((?:-*A){0}-*)'
  }

  private matchArgument(a: Action, pattern: string): number {
    const m = new RegExp('^' + this.nargsPattern(a)).exec(pattern)
    if (!m) {
      const msg = a.nargs === null ? 'expected one argument' : a.nargs === '?' ? 'expected at most one argument' : `expected ${a.nargs} argument`
      throw new ArgumentError(a, msg)
    }
    return m[1].length
  }

  private matchPartial(actions: Action[], pattern: string): number[] {
    for (let i = actions.length; i > 0; i--) {
      const re = new RegExp('^' + actions.slice(0, i).map(a => this.nargsPattern(a)).join(''))
      const m = re.exec(pattern)
      if (m) {
        const result = m.slice(1).map(s => s.length)
        if (m[0].length < pattern.length && pattern[m[0].length] === 'O') {
          while (result.length && !result[result.length - 1]) result.pop()
        }
        return result
      }
    }
    return []
  }

  private value(a: Action, s: string): Py {
    if (a.type !== 'int') return s
    try {
      return int(s)
    } catch (e) {
      if (isExc(e, 'ValueError') || isExc(e, 'TypeError')) throw new ArgumentError(a, `invalid int value: ${repr(s)}`)
      throw e
    }
  }

  private check(a: Action, v: Py) {
    const choices = a.kind === 'parsers' ? [...(a.parsers?.keys() ?? [])] : a.choices
    if (!choices) return
    if (!choices.some(c => (typeof c === 'number' ? typeof v === 'number' && v === c : v === c))) {
      throw new ArgumentError(a, `invalid choice: ${repr(pyStr(v))} (choose from ${choices.map(c => repr(String(c))).join(', ')})`)
    }
  }

  private values(a: Action, strings: string[]): Py | Py[] {
    if (!strings.length && a.nargs === '?') {
      const v = a.default as Py
      return typeof v === 'string' ? this.value(a, v) : v
    }
    if (strings.length === 1 && (a.nargs === null || a.nargs === '?')) {
      const v = this.value(a, strings[0])
      this.check(a, v)
      return v
    }
    if (a.nargs === 'PARSER') {
      const vs = strings.map(s => this.value(a, s))
      this.check(a, vs[0])
      return vs
    }
    return strings.map(s => this.value(a, s))
  }

  private take(a: Action, strings: string[], ns: Namespace, seen: Set<Action>) {
    seen.add(a)
    const v = this.values(a, strings)
    if (a.kind === 'help') {
      this.ctx.out.push(TEXTS[this.textKey].help)
      throw new SysExit(0)
    }
    if (a.kind === 'store_true') return void ns.set(a.dest as string, true)
    if (a.kind === 'parsers') {
      const [name, ...rest] = v as string[]
      if (a.dest !== SUPPRESS) ns.set(a.dest, name)
      const sub = a.parsers!.get(name)!
      const [subNs, extra] = sub.parseKnownArgs(rest, new Map())
      for (const [k, x] of subNs) ns.set(k, x)
      if (extra.length) {
        const list = (ns.get('\0unrecognized') as string[] | undefined) ?? []
        list.push(...extra)
        ns.set('\0unrecognized', list as unknown as Py)
      }
      return
    }
    ns.set(a.dest as string, v as Py)
  }

  private parse(args: string[], ns: Namespace): string[] {
    const optionAt = new Map<number, [Action | null, string, string | null, string | null][]>()
    const pat: string[] = []
    for (let i = 0; i < args.length; i++) {
      if (args[i] === '--') {
        pat.push('-')
        for (i++; i < args.length; i++) pat.push('A')
        break
      }
      const t = this.parseOptional(args[i])
      if (t === null) pat.push('A')
      else {
        optionAt.set(i, t)
        pat.push('O')
      }
    }
    const pattern = pat.join('')
    const seen = new Set<Action>()
    const extras: string[] = []

    const consumeOptional = (start: number): number => {
      const tuples = optionAt.get(start)!
      if (tuples.length > 1) {
        throw new ArgumentError(null, `ambiguous option: ${args[start]} could match ${tuples.map(t => t[1]).join(', ')}`)
      }
      let [action, option, sep, explicit] = tuples[0]
      const todo: [Action, string[], string][] = []
      let stop: number
      for (;;) {
        if (action === null) {
          extras.push(args[start])
          return start + 1
        }
        if (explicit !== null) {
          const count = this.matchArgument(action, 'A')
          if (count === 0 && option[1] !== '-' && explicit !== '') {
            if (sep || explicit[0] === '-') throw new ArgumentError(action, `ignored explicit argument ${repr(explicit)}`)
            todo.push([action, [], option])
            option = '-' + explicit[0]
            const next = this.options.get(option)
            if (next) {
              action = next
              explicit = explicit.slice(1)
              if (!explicit) sep = explicit = null
              else if (explicit[0] === '=') {
                sep = '='
                explicit = explicit.slice(1)
              } else sep = ''
            } else {
              extras.push('-' + explicit)
              stop = start + 1
              break
            }
          } else if (count === 1) {
            stop = start + 1
            todo.push([action, [explicit], option])
            break
          } else throw new ArgumentError(action, `ignored explicit argument ${repr(explicit)}`)
        } else {
          const from = start + 1
          const count = this.matchArgument(action, pattern.slice(from))
          stop = from + count
          todo.push([action, args.slice(from, stop), option])
          break
        }
      }
      for (const [a, as] of todo) this.take(a, as, ns, seen)
      return stop
    }

    let positionals = this.actions.filter(a => a.optionStrings.length === 0)
    const consumePositionals = (start: number): number => {
      const counts = this.matchPartial(positionals, pattern.slice(start))
      counts.forEach((count, i) => {
        const a = positionals[i]
        const as = args.slice(start, start + count)
        if (a.nargs === 'PARSER') {
          if (pattern[start] === '-') as.splice(as.indexOf('--'), 1)
        } else if (pattern.slice(start, start + count).includes('-')) as.splice(as.indexOf('--'), 1)
        start += count
        this.take(a, as, ns, seen)
      })
      positionals = positionals.slice(counts.length)
      return start
    }

    let start = 0
    const maxOption = optionAt.size ? Math.max(...optionAt.keys()) : -1
    while (start <= maxOption) {
      let next = start
      while (next <= maxOption && !optionAt.has(next)) next++
      if (start !== next) {
        const end = consumePositionals(start)
        if (end > start) {
          start = end
          continue
        }
        start = end
      }
      if (!optionAt.has(start)) {
        extras.push(...args.slice(start, next))
        start = next
      }
      start = consumeOptional(start)
    }
    const stop = consumePositionals(start)
    extras.push(...args.slice(stop))

    const required = this.actions.filter(a => !seen.has(a) && a.required).map(actionName)
    if (required.length) throw new ArgumentError(null, `the following arguments are required: ${required.join(', ')}`)
    return extras
  }
}
