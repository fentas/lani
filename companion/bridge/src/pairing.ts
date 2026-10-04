// Pairing a phone by QR code (companion/README.md, "Pair a phone"). Shared by the bridge and the CLI
// (src/cli/pair.ts):
// - the bridge's key: an ECDSA P-256 key pair made on first use; its fingerprint goes into the QR code,
//   and the bridge signs its answer to POST /pair with it, so the app knows it paired with that bridge;
// - pairing codes: lani-pair writes one as a file under <data>/app/pairing/ (named by the code's hash,
//   so the code itself is never stored); the bridge spends it on POST /pair. Valid for 10 minutes, once;
// - device tokens: long-lived, one per paired phone, stored hashed in <data>/app/devices.json, revocable;
// - the limit on wrong codes.
import { createHash, createPrivateKey, createPublicKey, generateKeyPairSync, randomBytes, sign, type KeyObject } from 'node:crypto'
import { closeSync, existsSync, linkSync, mkdirSync, openSync, readdirSync, readFileSync, renameSync, statSync, unlinkSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

/** The version of the QR payload and of the POST /pair answer. */
export const PAIR_VERSION = 1
export const CODE_TTL_MS = 10 * 60_000

const sha256 = (s: string | Buffer) => createHash('sha256').update(s).digest()

/** Writes [path] through a temp file and a rename, so a reader never sees half of it. */
export function writeAtomic(path: string, text: string) {
  const tmp = `${path}.tmp-${process.pid}-${randomBytes(3).toString('hex')}`
  writeFileSync(tmp, text, { mode: 0o600 })
  renameSync(tmp, path)
}

/**
 * Runs [fn] while holding [lock] (a file made with O_EXCL): the bridge and the CLI both change
 * devices.json (and towns.json, see towns.ts). A lock older than 10 s is left over from a crash and is taken over.
 */
export function withLock<T>(lock: string, fn: () => T): T {
  const until = Date.now() + 3_000
  for (;;) {
    try {
      closeSync(openSync(lock, 'wx', 0o600))
      break
    } catch (e) {
      if ((e as NodeJS.ErrnoException).code !== 'EEXIST') throw e
      try {
        if (Date.now() - statSync(lock).mtimeMs > 10_000) unlinkSync(lock)
      } catch {}
      if (Date.now() > until) throw new Error(`${lock} is held by another process`)
      Bun.sleepSync(15)
    }
  }
  try {
    return fn()
  } finally {
    try {
      unlinkSync(lock)
    } catch {}
  }
}

// --- the bridge's key ------------------------------------------------------------------------------

export type BridgeKey = {
  privateKey: KeyObject
  /** The public key, SubjectPublicKeyInfo DER: the app checks its fingerprint and verifies with it. */
  spki: Buffer
  /** The first 16 bytes of SHA-256(spki), hex: what the QR code carries as `f`. */
  fingerprint: string
}

export const fingerprintOf = (spki: Buffer) => sha256(spki).subarray(0, 16).toString('hex')

/** "3f9a1c0b…" → "3f9a 1c0b …": easier to compare by eye. */
export const groupFingerprint = (f: string) => f.replace(/(.{4})(?=.)/g, '$1 ')

/** <appDir>/bridge-key.pem, made on first use (mode 600). The bridge and the CLI may both make it: the first one wins. */
export function loadBridgeKey(appDir: string): BridgeKey {
  mkdirSync(appDir, { recursive: true })
  const path = join(appDir, 'bridge-key.pem')
  if (!existsSync(path)) {
    const { privateKey } = generateKeyPairSync('ec', { namedCurve: 'P-256' })
    const tmp = `${path}.tmp-${process.pid}`
    writeFileSync(tmp, privateKey.export({ type: 'pkcs8', format: 'pem' }).toString(), { mode: 0o600 })
    try {
      linkSync(tmp, path) // fails when another process made the key meanwhile: then that one is the key
    } catch (e) {
      if ((e as NodeJS.ErrnoException).code !== 'EEXIST') throw e
    } finally {
      unlinkSync(tmp)
    }
  }
  const privateKey = createPrivateKey(readFileSync(path, 'utf8'))
  const spki = createPublicKey(privateKey).export({ type: 'spki', format: 'der' }) as Buffer
  return { privateKey, spki, fingerprint: fingerprintOf(spki) }
}

/**
 * The QR code's text: `lani://pair?v=1&u=<bridge URL>&c=<code>&p=<profile>&f=<fingerprint>`. The app
 * refuses a version it doesn't know (data/Pairing.kt parses it; it reads fluent://, from before the rename, too).
 */
export const pairUri = (o: { url: string; code: string; profile: string; fingerprint: string }) =>
  `lani://pair?v=${PAIR_VERSION}&u=${encodeURIComponent(o.url)}&c=${o.code}&p=${o.profile}&f=${o.fingerprint}`

/**
 * What the bridge signs in its answer to POST /pair; the app builds the same text to verify it (and verifies
 * "fluent-pair/1", what a bridge from before the rename signs, too).
 */
export const pairMessage = (o: { profile: string; code: string; token: string }) => `lani-pair/${PAIR_VERSION}\n${o.profile}\n${o.code}\n${o.token}`

/** An ECDSA signature (DER, SHA-256) of [message], base64: Java's SHA256withECDSA verifies it. */
export const signPairing = (key: BridgeKey, message: string) => sign('sha256', Buffer.from(message), key.privateKey).toString('base64')

// --- pairing codes ---------------------------------------------------------------------------------

/** 32 letters and digits without 0/O and 1/I: a byte % 32 is unbiased. 12 of them are 60 bits. */
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
const CODE = /^[A-HJ-NP-Z2-9]{12}$/

export const newCode = () => Array.from(randomBytes(12), b => ALPHABET[b % 32]).join('')

/** " k7q2-…" → "K7Q2…": how a code is compared. */
export const normalizeCode = (c: string) => c.toUpperCase().replace(/[\s-]/g, '')

const codesDir = (appDir: string) => join(appDir, 'pairing')
const codeFile = (dir: string, code: string) => join(dir, `${sha256(normalizeCode(code)).toString('hex')}.json`)

/** A new one-time code, valid for [ttlMs]; files of codes that ran out a day ago go. */
export function createPairingCode(appDir: string, o: { ttlMs?: number; now?: number } = {}): { code: string; expiresAt: number } {
  return createOneTimeCode(codesDir(appDir), { ...o, ttlMs: o.ttlMs ?? CODE_TTL_MS })
}

/**
 * A new one-time code in [dir] (a file named by the code's hash, holding when it expires and [extra]), valid for
 * [ttlMs]: the pairing codes, and the invitations between towns (towns.ts). Files of codes that ran out a day ago go.
 */
export function createOneTimeCode(dir: string, o: { ttlMs: number; now?: number; extra?: Record<string, unknown> }): { code: string; expiresAt: number } {
  const now = o.now ?? Date.now()
  mkdirSync(dir, { recursive: true, mode: 0o700 })
  sweepCodes(dir, now)
  const code = newCode()
  const expiresAt = now + o.ttlMs
  writeAtomic(codeFile(dir, code), JSON.stringify({ ...o.extra, created_at: new Date(now).toISOString(), expires_at: new Date(expiresAt).toISOString() }) + '\n')
  return { code, expiresAt }
}

/** Removes the files of codes that expired more than a day ago (kept until then to say "expired" rather than "wrong"). */
function sweepCodes(dir: string, now: number) {
  for (const f of existsSync(dir) ? readdirSync(dir) : []) {
    const p = join(dir, f)
    try {
      const { expires_at } = JSON.parse(readFileSync(p, 'utf8'))
      if (now - Date.parse(expires_at) > 86_400_000) unlinkSync(p)
    } catch {}
  }
}

/** Spends [code]: 'ok' once, while it is valid; 'expired' after its time; 'invalid' when unknown or spent. */
export function redeemCode(appDir: string, code: string, now = Date.now()): 'ok' | 'invalid' | 'expired' {
  return spendOneTimeCode(codesDir(appDir), code, now).status
}

/** Spends a code of [dir] (see [createOneTimeCode]): 'ok' once, while it is valid, with what its file holds. */
export function spendOneTimeCode(dir: string, code: string, now = Date.now()): { status: 'ok' | 'invalid' | 'expired'; data?: Record<string, unknown> } {
  const c = normalizeCode(code)
  if (!CODE.test(c)) return { status: 'invalid' }
  const p = codeFile(dir, c)
  let data: Record<string, unknown>
  try {
    data = JSON.parse(readFileSync(p, 'utf8'))
  } catch {
    return { status: 'invalid' }
  }
  if (!(now < Date.parse(String(data?.expires_at)))) return { status: 'expired' }
  try {
    unlinkSync(p) // whoever removes the file spent the code
  } catch {
    return { status: 'invalid' }
  }
  return { status: 'ok', data }
}

// --- device tokens ---------------------------------------------------------------------------------

export type Device = { id: string; name: string; token_sha256: string; created_at: string; via: 'qr' }
export type DeviceView = Omit<Device, 'token_sha256'> & { last_seen?: string }

/** A device name as the phone sent it: printable, one line, at most 60 characters. */
export const cleanDeviceName = (s: unknown) =>
  (typeof s === 'string' ? s : '').replace(/[\u0000-\u001f\u007f<>]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 60) || 'Phone'

/**
 * The paired phones: <appDir>/devices.json (written by the bridge when a phone pairs, and by the CLI to
 * revoke one), and devices-seen.json (last seen, written by the bridge only). The bridge reads
 * devices.json again whenever it changed, so a revocation takes effect at the next request.
 */
export class Devices {
  private readonly file: string
  private readonly seenFile: string
  private stamp = ''
  private byHash = new Map<string, Device>()
  private seen: Record<string, string> = {}
  private seenTimer?: ReturnType<typeof setTimeout>

  constructor(
    appDir: string,
    private readonly o: { log?: (...a: unknown[]) => void; seenFlushMs?: number } = {},
  ) {
    mkdirSync(appDir, { recursive: true })
    this.file = join(appDir, 'devices.json')
    this.seenFile = join(appDir, 'devices-seen.json')
    try {
      this.seen = JSON.parse(readFileSync(this.seenFile, 'utf8'))
    } catch {}
  }

  private read(): Device[] {
    try {
      const d = JSON.parse(readFileSync(this.file, 'utf8'))
      return Array.isArray(d?.devices) ? d.devices.filter((x: Device) => x && typeof x.token_sha256 === 'string' && typeof x.id === 'string') : []
    } catch (e) {
      if ((e as NodeJS.ErrnoException).code !== 'ENOENT') this.o.log?.(`devices: ${this.file} unreadable: ${(e as Error).message}`)
      return []
    }
  }

  /** Reads devices.json again when it changed (a rename gives it a new inode). */
  private refresh() {
    let stamp = 'none'
    try {
      const st = statSync(this.file)
      stamp = `${st.ino}:${st.mtimeMs}:${st.size}`
    } catch {}
    if (stamp === this.stamp) return
    this.stamp = stamp
    this.byHash = new Map(this.read().map(d => [d.token_sha256, d]))
  }

  private write(devices: Device[]) {
    writeAtomic(this.file, JSON.stringify({ devices }, null, 2) + '\n')
  }

  /** The paired device a request's `Authorization: Bearer <token>` belongs to, if any. */
  authorize(req: Request): Device | undefined {
    const got = (req.headers.get('authorization') ?? '').replace(/^Bearer /, '')
    if (!got.startsWith('fd_')) return undefined
    this.refresh()
    const d = this.byHash.get(sha256(got).toString('hex'))
    if (d) this.touch(d.id)
    return d
  }

  /** Last seen, written at most once a minute. */
  private touch(id: string) {
    this.seen[id] = new Date().toISOString()
    this.dirty = true
    this.seenTimer ??= setTimeout(() => {
      this.seenTimer = undefined
      this.flushSeen()
    }, this.o.seenFlushMs ?? 60_000)
  }

  private dirty = false

  /** Writes when the phones were last seen, if that changed. */
  flushSeen() {
    if (!this.dirty) return
    this.dirty = false
    try {
      const known = new Set(this.read().map(d => d.id))
      writeAtomic(this.seenFile, JSON.stringify(Object.fromEntries(Object.entries(this.seen).filter(([id]) => known.has(id))), null, 2) + '\n')
    } catch (e) {
      this.o.log?.(`devices: last seen not saved: ${(e as Error).message}`)
    }
  }

  /** A new paired device named [name] (made unique: "Pixel 8", "Pixel 8 (2)"); its token is only returned here. */
  add(name: string): { device: Device; token: string } {
    const token = `fd_${randomBytes(32).toString('base64url')}`
    const device = withLock(`${this.file}.lock`, () => {
      const all = this.read()
      const base = cleanDeviceName(name)
      const names = new Set(all.map(d => d.name))
      let unique = base
      for (let n = 2; names.has(unique); n++) unique = `${base} (${n})`
      const d: Device = { id: `d_${randomBytes(4).toString('hex')}`, name: unique, token_sha256: sha256(token).toString('hex'), created_at: new Date().toISOString(), via: 'qr' }
      this.write([...all, d])
      return d
    })
    this.refresh()
    return { device, token }
  }

  /** Removes the device with this name or id; undefined when there is none. Its token stops working at once. */
  revoke(nameOrId: string): Device | undefined {
    const gone = withLock(`${this.file}.lock`, () => {
      const all = this.read()
      const d = all.find(x => x.id === nameOrId) ?? all.find(x => x.name === nameOrId) ?? all.find(x => x.name.toLowerCase() === nameOrId.toLowerCase())
      if (d) this.write(all.filter(x => x !== d))
      return d
    })
    this.refresh()
    return gone
  }

  /** The paired devices without their token hashes, with when each was last seen. */
  list(): DeviceView[] {
    let seen = this.seen
    try {
      seen = { ...JSON.parse(readFileSync(this.seenFile, 'utf8')), ...this.seen }
    } catch {}
    return this.read().map(({ token_sha256, ...d }) => ({ ...d, ...(seen[d.id] ? { last_seen: seen[d.id] } : {}) }))
  }
}

// --- the limit on wrong codes ----------------------------------------------------------------------

/**
 * Wrong or expired codes: at most [max] in [windowMs]; beyond that every attempt is refused until the
 * oldest one leaves the window. The API is behind `tailscale serve`, where every request comes from
 * 127.0.0.1, so the limit is for the bridge as a whole, not per address. With 60-bit codes, 10 guesses
 * per 10 minutes can't find one.
 */
export class Attempts {
  private fails: number[] = []

  constructor(
    private readonly max = 10,
    private readonly windowMs = 10 * 60_000,
  ) {}

  /** Milliseconds until the next attempt is allowed; 0 when it is allowed now. */
  wait(now = Date.now()): number {
    this.fails = this.fails.filter(t => now - t < this.windowMs)
    return this.fails.length >= this.max ? this.fails[0] + this.windowMs - now : 0
  }

  fail(now = Date.now()) {
    this.fails.push(now)
  }
}
