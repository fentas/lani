// .claude/hooks/lani_lease.py, as far as update-db.py and card.py use it: node_refusal(data), why this side may not
// write the learner's data now (the lease, app/lease.json: docs/plans/05-built/git-sync.md). The same text, byte for
// byte (tests/test_db_port.py's lease cases). Without a lease the node writes, as always; the phone's bridge runs these
// scripts as side "phone" (LANI_SIDE=phone, its device id LANI_LEASE_DEVICE), and writes while it holds the lease.
import { PyDict, PyError, isDict, strip, type Py } from './py'
import { loads } from './pyjson'
import { pjoin, readText, type Ctx, type Env } from './sys'

const FILE = 'app/lease.json'
const REMOTE_NOTE = 'lani-lease-remote.json'
const SIDES = ['node', 'phone']
const MODES: Record<string, string> = { node: 'self-hosted', phone: 'hosted' }
const TAKEABLE = ['mine', 'offered', 'offered-away', 'free', 'expired']

type Party = { side: string; device: string; name: string; mode: string }
type Lease = { invalid?: true; seen_remote?: true; holder?: Party | null; offer?: (Party & { by: Party | null; at: string | null }) | null; since?: string | null; renewed?: string | null; expires?: string | null }

/** str.isprintable() of one character: not "Other" (Cc, Cf, Cs, Co, Cn) nor a separator (Z*), but the space. */
const printable = (c: string) => c === ' ' || !/^[\p{C}\p{Z}]$/u.test(c)
/** _text(v, n): printable characters, stripped, at most [n] (code points); "" for anything but a str. */
function text(v: Py | undefined, n: number): string {
  if (typeof v !== 'string') return ''
  return [...strip([...v].filter(printable).join(''))].slice(0, n).join('')
}

const TIME = /^\d{4}-\d{2}-\d{2}(?:[T ](\d{2}):\d{2}(?::\d{2}(?:\.\d{1,6})?)?)?(?:Z|[+-](\d{2}):\d{2})?$/

/** parse_time: a lease's time in ms (UTC when it names no offset), or null; the same times as the Python's. */
export function parseTime(s: Py | undefined): number | null {
  if (typeof s !== 'string') return null
  const m = TIME.exec(s)
  if (!m || Number(m[1] ?? 0) > 23 || Number(m[2] ?? 0) > 23) return null
  const [, y, mo, d, H, M, S, frac, sign, oh, om] =
    /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,6}))?)?)?(?:Z|([+-])(\d{2}):(\d{2}))?$/.exec(s) ?? []
  const n = (x: string | undefined) => Number(x ?? 0)
  const leap = n(y) % 4 === 0 && (n(y) % 100 !== 0 || n(y) % 400 === 0)
  const dim = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  if (n(y) < 1 || n(mo) < 1 || n(mo) > 12 || n(d) < 1 || n(d) > dim[n(mo) - 1] || n(M) > 59 || n(S) > 59 || n(om) > 59) return null
  const ms = Date.UTC(n(y), n(mo) - 1, n(d), n(H), n(M), n(S), Math.floor(Number(`0.${frac ?? '0'}`) * 1000))
  const off = sign ? (sign === '-' ? -1 : 1) * (n(oh) * 60 + n(om)) * 60_000 : 0
  return ms - off
}

function party(p: Py | undefined): Party | null {
  if (p === undefined || !isDict(p)) return null
  const side = p.get('side')
  if (typeof side !== 'string' || !SIDES.includes(side)) return null
  const mode = p.get('mode')
  return { side, device: text(p.get('device'), 64), name: text(p.get('name'), 60), mode: typeof mode === 'string' && Object.values(MODES).includes(mode) ? mode : MODES[side] }
}

/** parse(text): the lease, or { invalid: true } when it can't be read. */
export function parseLease(raw: string): Lease {
  let d: Py
  try {
    d = loads(raw)
  } catch {
    return { invalid: true }
  }
  if (!isDict(d)) return { invalid: true }
  const schema = d.get('schema', '')
  if (typeof schema !== 'string' || !schema.startsWith('lani.lease/')) return { invalid: true }
  const o = d.get('offer')
  const op = party(o)
  const out: Lease = { holder: party(d.get('holder')), offer: op ? { ...op, by: party((o as PyDict).get('by')), at: parseTime((o as PyDict).get('at')) !== null ? ((o as PyDict).get('at') as string) : null } : null }
  for (const k of ['since', 'renewed', 'expires'] as const) out[k] = parseTime(d.get(k)) !== null ? (d.get(k) as string) : null
  return out
}

/** read_file: null when there is no file, { invalid: true } when it can't be read. */
function readLease(path: string): Lease | null {
  try {
    return parseLease(readText(path))
  } catch (e) {
    if (e instanceof PyError && (e.is('FileNotFoundError') || e.is('NotADirectoryError'))) return null
    return { invalid: true }
  }
}

const same = (a: Party | null | undefined, me: Party) => !!a && a.side === me.side && (me.side === 'node' || a.device === me.device)
const offeredTo = (o: Party | null | undefined, me: Party) => !!o && o.side === me.side && (!o.device || me.side === 'node' || o.device === me.device)
const expired = (l: Lease) => {
  const t = parseTime(l.expires)
  return t !== null && Date.now() > t
}

function state(lease: Lease | null, me: Party): string {
  if (lease === null) return me.side === 'node' ? 'mine' : 'unclaimed'
  if (lease.invalid) return 'other'
  if (lease.holder) return same(lease.holder, me) ? 'mine' : expired(lease) ? 'expired' : 'other'
  if (lease.offer) {
    if (offeredTo(lease.offer, me)) return 'offered'
    if (same(lease.offer.by, me)) return 'offered-away'
    return expired(lease) ? 'expired' : 'other'
  }
  return 'free'
}

const who = (p: Party | null | undefined) => (p ? `the ${p.side}${p.name || p.device ? ` (${p.name || p.device})` : ''}` : 'nobody')

function describe(lease: Lease | null): string {
  if (lease === null) return 'no lease: the node writes the data (as before the lease)'
  if (lease.invalid) return `${FILE} can't be read: nobody may write until it is fixed (or removed, on the side that writes)`
  const since = (lease.since || '?').replaceAll('T', ' ').replace(/Z+$/, '')
  let out: string
  if (lease.holder) out = `${who(lease.holder)} writes the data, since ${since} UTC`
  else if (lease.offer) out = `handed over to ${who(lease.offer)} by ${who(lease.offer.by)} on ${since} UTC: waiting for ${who(lease.offer)} to take it`
  else out = 'released: nobody writes the data until a side takes it'
  if (lease.seen_remote) out += " (the repository's lease: a push of this node's was refused)"
  return out
}

/** this_side(): the node, or the phone with LANI_SIDE=phone (its device LANI_LEASE_DEVICE). */
function thisSide(env: Env): Party {
  if (strip(env.LANI_SIDE ?? '').toLowerCase() !== 'phone') return { side: 'node', device: '', name: '', mode: MODES.node }
  const device = text(env.LANI_LEASE_DEVICE ?? '', 64)
  return { side: 'phone', device, name: device, mode: MODES.phone }
}

/**
 * effective(data): the data's own lease, unless a refused push found the repository's in another's hands (a note that
 * can't be read, or a .git that is a file, is no note).
 */
function effective(data: string, me: Party): Lease | null {
  const local = readLease(pjoin(data, FILE))
  const note = readLease(pjoin(data, '.git', REMOTE_NOTE))
  if (note !== null && !note.invalid && state(note, me) !== 'mine') return { ...note, seen_remote: true }
  return local
}

/** node_refusal(data): why this side may not write [data] now, or null when it may (LANI_LEASE=ignore: it may). */
export function nodeRefusal(ctx: Ctx, data: string): string | null {
  if ((ctx.env.LANI_LEASE || '').toLowerCase() === 'ignore') return null
  const me = thisSide(ctx.env)
  const lease = effective(data, me)
  if (lease === null) return null // no lease: every side writes, as before the lease (a node as always, a phone without git)
  const st = state(lease, me)
  if (st === 'mine') return null
  const how =
    me.side === 'phone'
      ? 'take it over in the app (Podatki · Data)'
      : TAKEABLE.includes(st)
        ? 'take it back with `lani handover --take`'
        : 'once it is handed back to the node, take it with `lani handover --take`'
  return `Lani's data isn't this ${me.side}'s to write now: ${describe(lease)}. This ${me.side} only reads it; ${how}.`
}
