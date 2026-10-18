// The secrets at a handover, node → phone, by QR code (docs/plans/05-built/git-sync.md, "The secrets: a QR code").
// `lani handover` (cli/handover.ts) shows it once the data repository is handed over; the app scans it with the pairing
// scanner and keeps the secrets (sync/Handover.kt). What moves: the town's key (its id among its friends) and the family
// page's token, with the learner's profile and name and the data repository, so the phone's git connection can pick it.
// Not the bridge token (one pairing's), the paired phones, nor open town invitations (they last 30 minutes).
//
//   lani://handover?v=1&f=<town fingerprint>&x=<expires, unix s>&d=<base64url(nonce ‖ ciphertext ‖ tag)>&k=<key>
//   lani://handover?v=1&f=…&x=…&d=…&s=<salt>      (--code: the key comes from a 12-letter code typed on the phone)
//
// The ciphertext is AES-256-GCM of the bundle's JSON under a one-time key: 32 random bytes in the QR code itself (k), or
// PBKDF2-HMAC-SHA256 of the code with the salt (s), CODE_ITERATIONS rounds, so a photo of the QR code alone opens nothing.
// The additional data "lani-handover/1\n<f>\n<x>" binds the fingerprint and the expiry to it. The town's key travels as
// its private scalar (JWK "d"): the phone derives the public key, checks its fingerprint against f, and writes the PEM.
import { createCipheriv, createDecipheriv, pbkdf2Sync, randomBytes } from 'node:crypto'
import { normalizeCode, newCode, type BridgeKey } from './pairing'

export const HANDOVER_VERSION = 1
export const HANDOVER_TTL_MS = 10 * 60_000
export const CODE_ITERATIONS = 200_000

/** What the QR code carries, encrypted. Short keys: the QR code stays small enough for a terminal. */
export type HandoverBundle = {
  v: number
  /** the profile ("default" or a registry id) */
  p: string
  /** the learner's name */
  n: string
  /** the node's host name */
  h: string
  /** the town key's private scalar, base64url (32 bytes, P-256) */
  k: string
  /** the family page's token */
  t?: string
  /** the data repository on GitHub, "owner/name" */
  r?: string
  /** its branch, when not main */
  b?: string
}

const aad = (f: string, x: number) => Buffer.from(`lani-handover/${HANDOVER_VERSION}\n${f}\n${x}`)

/** "owner/name" of a GitHub remote (https, ssh or scp-like), else undefined. */
export function githubRepo(remote: string | undefined): string | undefined {
  const m = /^(?:https?:\/\/(?:[^@/]+@)?github\.com\/|ssh:\/\/git@github\.com\/|git@github\.com:)([\w.-]+)\/([\w.-]+?)(?:\.git)?\/?$/.exec((remote ?? '').trim())
  return m ? `${m[1]}/${m[2]}` : undefined
}

/** The private scalar of a P-256 key, base64url. */
export const scalarOf = (key: BridgeKey) => String(key.privateKey.export({ format: 'jwk' }).d)

/** The derived key of a code (PBKDF2-HMAC-SHA256, [CODE_ITERATIONS] rounds, 32 bytes). */
export const keyOfCode = (code: string, salt: Buffer) => pbkdf2Sync(normalizeCode(code), salt, CODE_ITERATIONS, 32, 'sha256')

/**
 * The QR code's text for [bundle] of the town [fingerprint], valid until [expiresAt]: with the one-time key in it, or
 * ([withCode]) with a salt, and the code to type on the phone returned.
 */
export function encodeHandover(bundle: HandoverBundle, o: { fingerprint: string; expiresAt: number; withCode?: boolean; key?: Buffer; code?: string; nonce?: Buffer; salt?: Buffer }) {
  const x = Math.floor(o.expiresAt / 1000)
  const code = o.withCode ? (o.code ?? newCode()) : undefined
  const salt = o.withCode ? (o.salt ?? randomBytes(16)) : undefined
  const key = code && salt ? keyOfCode(code, salt) : (o.key ?? randomBytes(32))
  const nonce = o.nonce ?? randomBytes(12)
  const c = createCipheriv('aes-256-gcm', key, nonce)
  c.setAAD(aad(o.fingerprint, x))
  const sealed = Buffer.concat([nonce, c.update(JSON.stringify(bundle), 'utf8'), c.final(), c.getAuthTag()])
  const q = [`v=${HANDOVER_VERSION}`, `f=${o.fingerprint}`, `x=${x}`, `d=${sealed.toString('base64url')}`, salt ? `s=${salt.toString('base64url')}` : `k=${key.toString('base64url')}`]
  return { uri: `lani://handover?${q.join('&')}`, code }
}

/** The bundle back from a QR code's text ([code]: the typed code, when it has a salt); throws when it can't be opened. */
export function decodeHandover(uri: string, o: { code?: string; now?: number } = {}): { bundle: HandoverBundle; fingerprint: string; expiresAt: number } {
  const u = new URL(uri.trim())
  if (u.protocol !== 'lani:' || u.host !== 'handover') throw new Error('not a handover code')
  const q = u.searchParams
  const f = q.get('f') ?? ''
  const x = Number(q.get('x'))
  if (Number(q.get('v')) !== HANDOVER_VERSION || !/^[0-9a-f]{32}$/.test(f) || !Number.isInteger(x)) throw new Error('the handover code is incomplete')
  if ((o.now ?? Date.now()) > x * 1000) throw new Error('the handover code has expired')
  const sealed = Buffer.from(q.get('d') ?? '', 'base64url')
  const salt = q.get('s')
  const key = salt ? keyOfCode(o.code ?? '', Buffer.from(salt, 'base64url')) : Buffer.from(q.get('k') ?? '', 'base64url')
  const d = createDecipheriv('aes-256-gcm', key, sealed.subarray(0, 12))
  d.setAAD(aad(f, x))
  d.setAuthTag(sealed.subarray(sealed.length - 16))
  const plain = Buffer.concat([d.update(sealed.subarray(12, sealed.length - 16)), d.final()])
  return { bundle: JSON.parse(plain.toString('utf8')), fingerprint: f, expiresAt: x * 1000 }
}
