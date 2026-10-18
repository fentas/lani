// URL and URLSearchParams for the phone's bridge (QuickJS has neither; prelude.ts makes them globals), and node:url's
// fileURLToPath and pathToFileURL. Enough of WHATWG's URL for what the bridge parses: the app's request URLs (the host
// passes them percent-encoded), the towns' and the services' http(s) URLs, file: URLs; relative references against a
// base; the default ports dropped; what may not stand in a path or a query percent-encoded as UTF-8.
import { utf8Bytes } from './buffer'

const SPECIAL: Record<string, string> = { 'http:': '80', 'https:': '443', 'ws:': '80', 'wss:': '443', 'ftp:': '21', 'file:': '' }

function encodeSet(s: string, keep: RegExp): string {
  let out = ''
  for (const ch of s) {
    if (keep.test(ch)) out += ch
    else for (const b of utf8Bytes(ch)) out += `%${b.toString(16).toUpperCase().padStart(2, '0')}`
  }
  return out
}

// what stays as it is: printable ASCII but the set's own; "%" stays (it is already an escape)
const PATH_KEEP = /^[!$%&'()*+,\-./0-9:;=@A-Z\[\\\]^_a-z|~]$/
const QUERY_KEEP = /^[!$%&'()*+,\-./0-9:;=?@A-Z\[\\\]^_`a-z{|}~]$/
const FRAGMENT_KEEP = /^[!#$%&'()*+,\-./0-9:;=?@A-Z\[\\\]^_a-z{|}~]$/
const USER_KEEP = /^[!$%&'()*+,\-.0-9;=A-Z_a-z~]$/

function removeDots(path: string): string {
  const out: string[] = []
  const segs = path.split('/')
  for (let i = 0; i < segs.length; i++) {
    const s = segs[i]
    const last = i === segs.length - 1
    if (s === '.' || s.toLowerCase() === '%2e') {
      if (last) out.push('')
    } else if (s === '..' || /^(\.|%2e){2}$/i.test(s)) {
      if (out.length > 1) out.pop()
      if (last) out.push('')
    } else out.push(s)
  }
  return out.join('/')
}

type Parts = { protocol: string; username: string; password: string; hostname: string; port: string; pathname: string; search: string; hash: string }

const SCHEME = /^([a-zA-Z][a-zA-Z0-9+.\-]*):/

function parseAbsolute(input: string): Parts | null {
  const m = SCHEME.exec(input)
  if (!m) return null
  const protocol = `${m[1].toLowerCase()}:`
  let rest = input.slice(m[0].length)
  const special = protocol in SPECIAL
  if (special) rest = rest.replace(/\\/g, '/')
  let hash = ''
  let search = ''
  const h = rest.indexOf('#')
  if (h >= 0) {
    hash = rest.slice(h)
    rest = rest.slice(0, h)
  }
  const q = rest.indexOf('?')
  if (q >= 0) {
    search = rest.slice(q)
    rest = rest.slice(0, q)
  }
  let username = ''
  let password = ''
  let hostname = ''
  let port = ''
  let pathname = rest
  if (rest.startsWith('//') || (special && protocol !== 'file:')) {
    const auth = rest.replace(/^\/*/, '')
    const end = auth.search(/[/]/)
    let authority = end < 0 ? auth : auth.slice(0, end)
    pathname = end < 0 ? '' : auth.slice(end)
    const at = authority.lastIndexOf('@')
    if (at >= 0) {
      const info = authority.slice(0, at)
      authority = authority.slice(at + 1)
      const c = info.indexOf(':')
      username = encodeSet(c < 0 ? info : info.slice(0, c), USER_KEEP)
      password = c < 0 ? '' : encodeSet(info.slice(c + 1), USER_KEEP)
    }
    const pm = /^(\[[^\]]*\]|[^:]*)(?::(\d*))?$/.exec(authority)
    if (!pm) throw new TypeError(`Invalid URL: ${input}`)
    hostname = pm[1].toLowerCase()
    port = pm[2] ?? ''
    if (port && Number(port) > 65535) throw new TypeError(`Invalid URL: ${input}`)
    if (special && port === SPECIAL[protocol]) port = ''
    if (special && protocol !== 'file:' && !hostname) throw new TypeError(`Invalid URL: ${input}`)
    if (/[\s<>^|%]/.test(hostname.replace(/%[0-9a-f]{2}/gi, ''))) throw new TypeError(`Invalid URL: ${input}`)
  }
  if (special && !pathname) pathname = '/'
  pathname = special || pathname.startsWith('/') ? removeDots(encodeSet(pathname, PATH_KEEP)) : encodeSet(pathname, /^[\x21-\x7e]$/)
  return { protocol, username, password, hostname, port, pathname, search: search === '?' ? '' : encodeQuery(search, special), hash: hash === '#' ? '' : encodeSet(hash, FRAGMENT_KEEP) }
}

/** A query as WHATWG encodes it: a special scheme's (http, https …) with its ' encoded too. */
const encodeQuery = (s: string, special: boolean) => {
  const e = encodeSet(s, QUERY_KEEP)
  return special ? e.replace(/'/g, '%27') : e
}

function resolve(input: string, base: Parts): Parts {
  const special = base.protocol in SPECIAL
  let ref = special ? input.replace(/\\/g, '/') : input
  if (ref.startsWith('//')) return parseAbsolute(base.protocol + ref)!
  let hash = ''
  let search: string | undefined
  const h = ref.indexOf('#')
  if (h >= 0) {
    hash = ref.slice(h)
    ref = ref.slice(0, h)
  }
  const q = ref.indexOf('?')
  if (q >= 0) {
    search = ref.slice(q)
    ref = ref.slice(0, q)
  }
  let pathname: string
  if (!ref) pathname = base.pathname
  else if (ref.startsWith('/')) pathname = ref
  else pathname = base.pathname.slice(0, base.pathname.lastIndexOf('/') + 1) + ref
  const enc = (s: string | undefined, f: (x: string) => string) => (s === undefined ? undefined : s === '?' || s === '#' ? '' : f(s))
  return {
    ...base,
    pathname: removeDots(encodeSet(pathname, PATH_KEEP)) || '/',
    search: enc(search, s => encodeQuery(s, special)) ?? (ref ? '' : base.search),
    hash: enc(hash, s => encodeSet(s, FRAGMENT_KEEP)) ?? '',
  }
}

export class URLSearchParams {
  private list: [string, string][] = []
  /** The URL whose query this is: changed with it. */
  _url?: URL

  constructor(init?: string | Record<string, string> | Iterable<[string, string]> | URLSearchParams) {
    if (init == null) return
    if (typeof init === 'string') this.parse(init)
    else if (init instanceof URLSearchParams) this.list = init.list.map(([k, v]) => [k, v])
    else if (Symbol.iterator in Object(init)) for (const [k, v] of init as Iterable<[string, string]>) this.list.push([String(k), String(v)])
    else for (const [k, v] of Object.entries(init)) this.list.push([k, String(v)])
  }

  _parse(s: string) {
    this.list = []
    this.parse(s)
  }

  private parse(s: string) {
    for (const part of (s.startsWith('?') ? s.slice(1) : s).split('&')) {
      if (!part) continue
      const eq = part.indexOf('=')
      const k = eq < 0 ? part : part.slice(0, eq)
      const v = eq < 0 ? '' : part.slice(eq + 1)
      this.list.push([decode(k), decode(v)])
    }
  }

  private changed() {
    if (this._url) this._url._setSearch(this.toString())
  }

  get size() {
    return this.list.length
  }

  append(k: string, v: string) {
    this.list.push([String(k), String(v)])
    this.changed()
  }

  delete(k: string, v?: string) {
    this.list = this.list.filter(([a, b]) => a !== k || (v !== undefined && b !== v))
    this.changed()
  }

  get(k: string): string | null {
    return this.list.find(([a]) => a === k)?.[1] ?? null
  }

  getAll(k: string): string[] {
    return this.list.filter(([a]) => a === k).map(([, b]) => b)
  }

  has(k: string, v?: string): boolean {
    return this.list.some(([a, b]) => a === k && (v === undefined || b === v))
  }

  set(k: string, v: string) {
    const i = this.list.findIndex(([a]) => a === k)
    if (i < 0) this.list.push([String(k), String(v)])
    else {
      this.list[i] = [String(k), String(v)]
      this.list = this.list.filter(([a], j) => a !== k || j === i)
    }
    this.changed()
  }

  sort() {
    this.list.sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    this.changed()
  }

  forEach(f: (v: string, k: string, p: URLSearchParams) => void) {
    for (const [k, v] of this.list) f(v, k, this)
  }

  keys() {
    return this.list.map(([k]) => k)[Symbol.iterator]()
  }

  values() {
    return this.list.map(([, v]) => v)[Symbol.iterator]()
  }

  entries() {
    return this.list.map(([k, v]) => [k, v] as [string, string])[Symbol.iterator]()
  }

  [Symbol.iterator]() {
    return this.entries()
  }

  toString() {
    return this.list.map(([k, v]) => `${encodeForm(k)}=${encodeForm(v)}`).join('&')
  }
}

function decode(s: string): string {
  const t = s.replace(/\+/g, ' ')
  try {
    return decodeURIComponent(t)
  } catch {
    // a broken escape stays as it is, as WHATWG's decoder leaves it
    return t.replace(/%([0-9a-f]{2})/gi, (m, h) => {
      const c = parseInt(h, 16)
      return c < 0x80 ? String.fromCharCode(c) : m
    })
  }
}

/** application/x-www-form-urlencoded: what URLSearchParams writes. */
const encodeForm = (s: string) => encodeSet(s, /^[*\-._0-9A-Za-z]$/).replace(/%20/g, '+')

export class URL {
  private p: Parts
  private params?: URLSearchParams

  constructor(input: string | URL, base?: string | URL) {
    const s = String(input).trim()
    const abs = parseAbsolute(s)
    if (abs) {
      this.p = abs
      return
    }
    if (base === undefined) throw new TypeError(`Invalid URL: ${s}`)
    const b = base instanceof URL ? base.p : parseAbsolute(String(base))
    if (!b) throw new TypeError(`Invalid base URL: ${String(base)}`)
    this.p = resolve(s, b)
  }

  static canParse(input: string, base?: string): boolean {
    try {
      new URL(input, base)
      return true
    } catch {
      return false
    }
  }

  get protocol() {
    return this.p.protocol
  }
  set protocol(v: string) {
    this.p.protocol = `${String(v).replace(/:$/, '').toLowerCase()}:`
  }
  get username() {
    return this.p.username
  }
  set username(v: string) {
    this.p.username = encodeSet(v, USER_KEEP)
  }
  get password() {
    return this.p.password
  }
  set password(v: string) {
    this.p.password = encodeSet(v, USER_KEEP)
  }
  get hostname() {
    return this.p.hostname
  }
  set hostname(v: string) {
    this.p.hostname = String(v).toLowerCase()
  }
  get port() {
    return this.p.port
  }
  set port(v: string) {
    const s = String(v)
    this.p.port = s === SPECIAL[this.p.protocol] ? '' : s
  }
  get host() {
    return this.p.port ? `${this.p.hostname}:${this.p.port}` : this.p.hostname
  }
  set host(v: string) {
    const [h, port] = String(v).split(':')
    this.hostname = h
    this.port = port ?? ''
  }
  get origin() {
    return this.p.protocol in SPECIAL && this.p.protocol !== 'file:' ? `${this.p.protocol}//${this.host}` : 'null'
  }
  get pathname() {
    return this.p.pathname
  }
  set pathname(v: string) {
    const s = String(v)
    this.p.pathname = removeDots(encodeSet(s.startsWith('/') ? s : `/${s}`, PATH_KEEP))
  }
  get search() {
    return this.p.search
  }
  set search(v: string) {
    this._setSearch(String(v))
    this.params?._parse(this.p.search)
  }
  /** The query from searchParams' changes. */
  _setSearch(v: string) {
    const s = v.startsWith('?') ? v.slice(1) : v
    this.p.search = s ? `?${encodeQuery(s, this.p.protocol in SPECIAL)}` : ''
  }
  get searchParams(): URLSearchParams {
    if (!this.params) {
      this.params = new URLSearchParams(this.p.search)
      this.params._url = this
    }
    return this.params
  }
  get hash() {
    return this.p.hash
  }
  set hash(v: string) {
    const s = String(v)
    this.p.hash = s && s !== '#' ? encodeSet(s.startsWith('#') ? s : `#${s}`, FRAGMENT_KEEP) : ''
  }
  get href() {
    const p = this.p
    const auth = p.username || p.password ? `${p.username}${p.password ? `:${p.password}` : ''}@` : ''
    const slashes = p.protocol in SPECIAL || p.hostname ? '//' : ''
    return `${p.protocol}${slashes}${auth}${this.host}${p.pathname}${p.search}${p.hash}`
  }
  set href(v: string) {
    const abs = parseAbsolute(String(v))
    if (!abs) throw new TypeError(`Invalid URL: ${v}`)
    this.p = abs
    this.params?._parse(abs.search)
  }
  toString() {
    return this.href
  }
  toJSON() {
    return this.href
  }
}

/** node:url's: the path of a file: URL. */
export function fileURLToPath(u: string | URL): string {
  const url = typeof u === 'string' ? new URL(u) : u
  if (url.protocol !== 'file:') throw new TypeError('The URL must be of scheme file')
  return decodeURIComponent(url.pathname)
}

export function pathToFileURL(path: string): URL {
  return new URL(`file://${encodeSet(path, PATH_KEEP)}`)
}

export default { URL, URLSearchParams, fileURLToPath, pathToFileURL }
