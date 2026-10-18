// The handover QR code made by the bridge (src/handover.ts) from fixed inputs, for the app's test (sync/HandoverTest.kt):
// the app must open what the node shows.
//   bun test/handover-fixture.ts > ../android/app/src/test/resources/sync/handover-fixture.json
import { createECDH, createPrivateKey, createPublicKey } from 'node:crypto'
import { encodeHandover, scalarOf } from '../src/handover'
import { fingerprintOf, keyFromPem } from '../src/pairing'

// A town key from a fixed scalar (never a real town's): 0x42, then 31 times 0x07.
const d = Buffer.alloc(32, 7)
d[0] = 0x42
const ecdh = createECDH('prime256v1')
ecdh.setPrivateKey(d)
const pub = ecdh.getPublicKey()
const jwk = { kty: 'EC', crv: 'P-256', d: d.toString('base64url'), x: pub.subarray(1, 33).toString('base64url'), y: pub.subarray(33).toString('base64url') }
const key = keyFromPem(createPrivateKey({ key: jwk, format: 'jwk' }).export({ type: 'pkcs8', format: 'pem' }).toString())
const spki = createPublicKey(key.privateKey).export({ type: 'spki', format: 'der' }) as Buffer
const pkcs8 = key.privateKey.export({ type: 'pkcs8', format: 'der' }) as Buffer
const bundle = { v: 1, p: 'default', n: 'Ana', h: 'jan-desktop', k: scalarOf(key), t: 'fam-token-0123456789abcdef', r: 'ana/lani-data' }
const expiresAt = Date.UTC(2026, 9, 18, 21, 10, 0)
const withKey = encodeHandover(bundle, { fingerprint: key.fingerprint, expiresAt, key: Buffer.alloc(32, 1), nonce: Buffer.alloc(12, 2) })
const withCode = encodeHandover(bundle, { fingerprint: key.fingerprint, expiresAt, withCode: true, code: 'K7Q2M9XA4HPD', salt: Buffer.alloc(16, 3), nonce: Buffer.alloc(12, 4) })
console.log(JSON.stringify({
  note: 'made by companion/bridge/test/handover-fixture.ts: the handover QR code as the node shows it',
  uri: withKey.uri,
  uriWithCode: withCode.uri,
  code: 'K7Q2-M9XA-4HPD',
  expiresAt,
  bundle,
  fingerprint: key.fingerprint,
  fingerprintCheck: fingerprintOf(spki),
  spki: spki.toString('base64'),
  pkcs8: pkcs8.toString('base64'),
}, null, 2))
