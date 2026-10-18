// node:crypto for the phone's bridge, the calls it makes, over the host's Java crypto (si.lanisce.lani.phone.HostCrypto):
// createHash and createHmac (sha256, sha1, sha512, md5), randomBytes, randomUUID, timingSafeEqual, and the bridge's
// EC P-256 key (pairing.ts, towns.ts): generateKeyPairSync('ec', {namedCurve: 'P-256'}), createPrivateKey from PEM,
// createPublicKey from a private key or SPKI DER, export as PKCS#8 PEM or SPKI DER, sign and verify with sha256
// (ECDSA, DER signatures: what node and Java's SHA256withECDSA make).
import { Buffer } from './buffer'
import { fromHost, host, toHost } from './host'

type Data = string | ArrayBufferView
type Enc = 'hex' | 'base64' | 'base64url' | 'latin1' | 'binary' | 'utf8'

const bytesOf = (d: Data, enc?: string): Uint8Array =>
  typeof d === 'string' ? Buffer.from(d, enc as 'utf8' | undefined) : new Uint8Array(d.buffer, d.byteOffset, d.byteLength)

const ALG: Record<string, string> = { sha256: 'sha256', 'sha-256': 'sha256', sha1: 'sha1', 'sha-1': 'sha1', sha512: 'sha512', 'sha-512': 'sha512', md5: 'md5' }

function algOf(a: string): string {
  const alg = ALG[a.toLowerCase()]
  if (!alg) throw new Error(`Digest method not supported: ${a}`)
  return alg
}

class Digest {
  private parts: Uint8Array[] = []
  private done = false
  constructor(private readonly f: (data: Int8Array) => Int8Array) {}
  update(d: Data, enc?: string) {
    if (this.done) throw new Error('Digest already called')
    this.parts.push(bytesOf(d, enc))
    return this
  }
  digest(): Buffer
  digest(enc: Enc): string
  digest(enc?: Enc): Buffer | string {
    if (this.done) throw new Error('Digest already called')
    this.done = true
    const out = Buffer.view(fromHost(this.f(toHost(Buffer.concat(this.parts)))))
    return enc ? out.toString(enc) : out
  }
  copy() {
    const c = new Digest(this.f)
    c.parts = [...this.parts]
    return c
  }
}

export const createHash = (alg: string) => {
  const a = algOf(alg)
  return new Digest(d => host().hash(a, d))
}

export const createHmac = (alg: string, key: Data | KeyObject) => {
  const a = algOf(alg)
  const k = toHost(key instanceof KeyObject ? key._secret() : bytesOf(key))
  return new Digest(d => host().hmac(a, k, d))
}

export function randomBytes(n: number, cb?: (e: Error | null, b: Buffer) => void): Buffer {
  const b = n > 0 ? Buffer.view(fromHost(host().randomBytes(n))) : Buffer.alloc(0)
  if (cb) queueMicrotask(() => cb(null, b))
  return b
}

export const randomUUID = () => host().randomUUID()

export function randomInt(min: number, max?: number): number {
  const [lo, hi] = max === undefined ? [0, min] : [min, max]
  const span = hi - lo
  if (span <= 0) throw new RangeError('The value of "max" is out of range')
  const r = randomBytes(6)
  const n = r.readUInt32BE(0) * 0x10000 + ((r[4] << 8) | r[5])
  return lo + (n % span)
}

export function getRandomValues<T extends ArrayBufferView>(a: T): T {
  const b = randomBytes(a.byteLength)
  new Uint8Array(a.buffer, a.byteOffset, a.byteLength).set(b)
  return a
}

export function timingSafeEqual(a: ArrayBufferView, b: ArrayBufferView): boolean {
  if (a.byteLength !== b.byteLength) throw new RangeError('Input buffers must have the same byte length')
  const x = new Uint8Array(a.buffer, a.byteOffset, a.byteLength)
  const y = new Uint8Array(b.buffer, b.byteOffset, b.byteLength)
  let diff = 0
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i]
  return diff === 0
}

// --- keys --------------------------------------------------------------------------------------------------------------

type KeyLike = KeyObject | string | Buffer | { key: string | Buffer | KeyObject; format?: 'pem' | 'der'; type?: 'pkcs8' | 'spki' | 'sec1' }

const pem = (label: string, der: Uint8Array) =>
  `-----BEGIN ${label}-----\n${Buffer.view(der).toString('base64').replace(/.{1,64}/g, l => `${l}\n`)}-----END ${label}-----\n`

const derOfPem = (text: string) => Buffer.from(text.replace(/-----[^-]+-----/g, '').replace(/\s+/g, ''), 'base64')

export class KeyObject {
  readonly asymmetricKeyType: 'ec' | undefined
  constructor(
    readonly type: 'private' | 'public' | 'secret',
    /** A private key's PKCS#8 PEM; a public key's SPKI DER; a secret's bytes. */
    private readonly material: string | Uint8Array,
  ) {
    this.asymmetricKeyType = type === 'secret' ? undefined : 'ec'
  }
  get asymmetricKeyDetails() {
    return this.type === 'secret' ? undefined : { namedCurve: 'prime256v1' }
  }
  get symmetricKeySize() {
    return this.type === 'secret' ? (this.material as Uint8Array).length : undefined
  }
  _pem(): string {
    return this.material as string
  }
  _spki(): Uint8Array {
    return this.type === 'public' ? (this.material as Uint8Array) : fromHost(host().ecPublic(this.material as string))
  }
  _secret(): Uint8Array {
    return this.material as Uint8Array
  }
  export(o: { type?: 'pkcs8' | 'spki' | 'sec1'; format?: 'pem' | 'der' | 'jwk' } = {}): string | Buffer {
    const format = o.format ?? 'pem'
    if (this.type === 'secret') return Buffer.from(this._secret())
    if (this.type === 'private') {
      if ((o.type ?? 'pkcs8') !== 'pkcs8') throw new Error(`exporting a private key as ${o.type} is not available in the phone bridge`)
      return format === 'pem' ? this._pem() : derOfPem(this._pem())
    }
    if ((o.type ?? 'spki') !== 'spki') throw new Error(`exporting a public key as ${o.type} is not available in the phone bridge`)
    return format === 'pem' ? pem('PUBLIC KEY', this._spki()) : Buffer.from(this._spki())
  }
  equals(other: KeyObject): boolean {
    return this.type === other.type && Buffer.from(this._spki()).equals(other._spki())
  }
}

export function generateKeyPairSync(type: string, o: { namedCurve?: string } = {}): { privateKey: KeyObject; publicKey: KeyObject } {
  if (type !== 'ec' || !['P-256', 'prime256v1', 'secp256r1'].includes(o.namedCurve ?? '')) throw new Error(`only EC P-256 keys are made in the phone bridge (asked: ${type} ${o.namedCurve ?? ''})`)
  const privateKey = new KeyObject('private', host().ecGenerate())
  return { privateKey, publicKey: new KeyObject('public', privateKey._spki()) }
}

export function createPrivateKey(k: KeyLike): KeyObject {
  if (k instanceof KeyObject) return k
  const spec = typeof k === 'string' || k instanceof Uint8Array ? { key: k } : k
  const key = spec.key instanceof KeyObject ? spec.key : spec.key
  if (key instanceof KeyObject) return key
  const text = typeof key === 'string' ? key : spec.format === 'der' ? pem('PRIVATE KEY', key) : key.toString()
  if (!/-----BEGIN PRIVATE KEY-----/.test(text)) throw new Error('only PKCS#8 private keys are read in the phone bridge')
  // read once, so a broken key fails here as in node
  host().ecPublic(text)
  return new KeyObject('private', text)
}

export function createPublicKey(k: KeyLike): KeyObject {
  if (k instanceof KeyObject) return k.type === 'public' ? k : new KeyObject('public', k._spki())
  const spec = typeof k === 'string' || k instanceof Uint8Array ? { key: k } : k
  const key = spec.key
  if (key instanceof KeyObject) return createPublicKey(key)
  if (typeof key === 'string' || spec.format !== 'der') {
    const text = typeof key === 'string' ? key : key.toString()
    if (/-----BEGIN PRIVATE KEY-----/.test(text)) return new KeyObject('public', fromHost(host().ecPublic(text)))
    return new KeyObject('public', derOfPem(text))
  }
  return new KeyObject('public', new Uint8Array(key.buffer, key.byteOffset, key.byteLength))
}

export const createSecretKey = (key: Data) => new KeyObject('secret', bytesOf(key))

/** ECDSA with SHA-256 over [data] with a private key, its DER signature. */
export function sign(alg: string | null, data: ArrayBufferView, key: KeyLike): Buffer {
  if (alg && algOf(alg) !== 'sha256') throw new Error(`only sha256 signatures are made in the phone bridge (asked: ${alg})`)
  const k = createPrivateKey(key)
  return Buffer.view(fromHost(host().ecSign(k._pem(), toHost(bytesOf(data)))))
}

export function verify(alg: string | null, data: ArrayBufferView, key: KeyLike, signature: ArrayBufferView): boolean {
  if (alg && algOf(alg) !== 'sha256') throw new Error(`only sha256 signatures are checked in the phone bridge (asked: ${alg})`)
  const k = createPublicKey(key)
  return host().ecVerify(toHost(k._spki()), toHost(bytesOf(data)), toHost(bytesOf(signature)))
}

export const webcrypto = { getRandomValues, randomUUID }

export default {
  createHash, createHmac, randomBytes, randomUUID, randomInt, getRandomValues, timingSafeEqual, generateKeyPairSync, createPrivateKey, createPublicKey,
  createSecretKey, sign, verify, KeyObject, webcrypto,
}
