// Requests signed with a town's key (the shared contract of plan 5's overnight design; docs/plans/05-built/relay.md).
// A town's key is the bridge's EC P-256 key (companion/bridge/src/pairing.ts); its id is the first 16 bytes of the
// SHA-256 of its public key (SubjectPublicKeyInfo DER), hex: the same id the towns know each other by. A signed request
// carries:
// - X-Lani-Key: the public key, base64 SPKI DER;
// - X-Lani-Time: unix milliseconds;
// - X-Lani-Sig: a base64 DER ECDSA signature (SHA-256) over `${METHOD}\n${path with query}\n${time}\n${hex SHA-256 of the body}`.
// The relay takes a time within ±5 minutes and each signed message once (replays: store.ts, seen).
import { createHash, createPublicKey, verify, type KeyObject } from 'node:crypto'

export const MAX_SKEW_MS = 5 * 60_000

export const sha256hex = (b: Uint8Array | string) => createHash('sha256').update(b).digest('hex')

/** What a signed request's signature covers. */
export const signedMessage = (method: string, pathWithQuery: string, time: string | number, bodySha256: string) =>
  `${method.toUpperCase()}\n${pathWithQuery}\n${time}\n${bodySha256}`

/** A town's id: the first 16 bytes of SHA-256(SPKI DER), hex (pairing.ts fingerprintOf). */
export const fingerprintOf = (spki: Uint8Array) => createHash('sha256').update(spki).digest().subarray(0, 16).toString('hex')

export const TOWN_ID = /^[0-9a-f]{32}$/

export type TownKey = { key: KeyObject; spki: Buffer; fingerprint: string; base64: string }

/** The key of X-Lani-Key: base64 SPKI DER of an EC P-256 public key; undefined for anything else. */
export function parseKey(b64: string | null): TownKey | undefined {
  if (!b64 || b64.length > 400 || !/^[A-Za-z0-9+/]+={0,2}$/.test(b64)) return undefined
  try {
    const spki = Buffer.from(b64, 'base64')
    const key = createPublicKey({ key: spki, format: 'der', type: 'spki' })
    if (key.asymmetricKeyType !== 'ec' || key.asymmetricKeyDetails?.namedCurve !== 'prime256v1') return undefined
    return { key, spki, fingerprint: fingerprintOf(spki), base64: spki.toString('base64') }
  } catch {
    return undefined
  }
}

/** Whether [sig] (base64 DER) is [message] signed by [key]. */
export function verifySig(key: KeyObject | string, message: string, sig: string): boolean {
  try {
    const k = typeof key === 'string' ? createPublicKey({ key: Buffer.from(key, 'base64'), format: 'der', type: 'spki' }) : key
    return verify('sha256', Buffer.from(message), k, Buffer.from(sig, 'base64'))
  } catch {
    return false
  }
}

export type Signed = {
  /** The signer's id (its key's fingerprint) and key (base64 SPKI DER). */
  id: string
  publicKey: string
  time: string
  sig: string
  /** The signed text: what the replay check remembers (not the signature: an ECDSA signature can be changed and stay valid). */
  message: string
}

export type SignCheck = { ok: true; signed: Signed } | { ok: false; code: 'unsigned' | 'bad_key' | 'stale' | 'bad_signature' }

/** Checks a request's X-Lani-* signature over [method], [pathWithQuery] and the body's hash, at [now]. */
export function checkSigned(headers: Headers, o: { method: string; pathWithQuery: string; bodySha256: string; now: number }): SignCheck {
  const keyText = headers.get('x-lani-key')
  const time = headers.get('x-lani-time') ?? ''
  const sig = headers.get('x-lani-sig') ?? ''
  if (!keyText || !time || !sig) return { ok: false, code: 'unsigned' }
  if (!/^\d{1,16}$/.test(time) || sig.length > 200) return { ok: false, code: 'unsigned' }
  const key = parseKey(keyText)
  if (!key) return { ok: false, code: 'bad_key' }
  if (Math.abs(o.now - Number(time)) > MAX_SKEW_MS) return { ok: false, code: 'stale' }
  const message = signedMessage(o.method, o.pathWithQuery, time, o.bodySha256)
  if (!verifySig(key.key, message, sig)) return { ok: false, code: 'bad_signature' }
  return { ok: true, signed: { id: key.fingerprint, publicKey: key.base64, time, sig, message } }
}
