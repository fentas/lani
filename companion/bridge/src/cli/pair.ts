#!/usr/bin/env bun
// companion/bin/lani-pair: pair a phone with a learner's tutor by QR code (companion/README.md,
// "Pair a phone").
//
//   lani-pair [--profile <id>] [--url <bridge URL>] [--no-qr]   a one-time code, as a QR code
//   lani-pair [--profile <id>] --devices                         the paired phones
//   lani-pair [--profile <id>] --revoke <name or id>             unpair one; its token stops working
//
// The code is a file under <data>/app/pairing/ that the bridge spends on POST /pair (src/pairing.ts):
// making one needs access to this machine's files, not the network.
import '../env' // first: LANI_* from the FLUENT_* names of a node set up before the rename
import { parseArgs } from 'node:util'
import { createPairingCode, Devices, groupFingerprint, loadBridgeKey, pairUri } from '../pairing'
import { appDir, bridgeUp, die, publicUrl, resolveProfile, short, table, tailnetHost, tailnetUrl } from './common'

const { values } = parseArgs({
  args: process.argv.slice(2),
  options: {
    profile: { type: 'string', short: 'p' },
    url: { type: 'string' },
    'no-qr': { type: 'boolean', default: false },
    devices: { type: 'boolean', default: false },
    revoke: { type: 'string' },
    help: { type: 'boolean', short: 'h', default: false },
  },
})

if (values.help) {
  console.log(`usage: lani-pair [--profile <id>] [--url <bridge URL>] [--no-qr]
       lani-pair [--profile <id>] --devices
       lani-pair [--profile <id>] --revoke <name or id>`)
  process.exit(0)
}

const p = resolveProfile(values.profile)
const whose = `${p.name}'s tutor${p.isDefault ? '' : ` (profile ${p.id})`}`
const when = (iso?: string) => (iso ? iso.slice(0, 16).replace('T', ' ') : '')

if (values.devices) {
  const list = new Devices(appDir(p)).list()
  if (!list.length) {
    console.log(`No phones are paired with ${whose} by QR code. (The typed token in ${short(appDir(p))}/bridge-token works as before.)`)
  } else {
    console.log(`Phones paired with ${whose}:\n`)
    console.log(table([['NAME', 'ID', 'PAIRED', 'LAST SEEN'], ...list.map(d => [d.name, d.id, when(d.created_at), when(d.last_seen) || 'never'])]))
    console.log(`\nUnpair one: companion/bin/lani-pair${p.isDefault ? '' : ` --profile ${p.id}`} --revoke "<name>"`)
  }
  process.exit(0)
}

if (values.revoke !== undefined) {
  const gone = new Devices(appDir(p)).revoke(values.revoke.trim())
  if (!gone) die(`No paired phone "${values.revoke}" (see --devices).`)
  console.log(`Unpaired ${gone.name} (${gone.id}): its token no longer works. To pair it again, make a new code.`)
  process.exit(0)
}

// A new code, as a QR code.
// --url, else lani.env's LANI_PUBLIC_URL (the default learner's: lani-setup), else the tailnet's HTTPS name
const url = (values.url ?? publicUrl(p) ?? (tailnetHost() ? tailnetUrl(p) : die('This node\'s tailnet name is unknown (is Tailscale up?). Pass the app\'s URL with --url https://…'))).replace(/\/+$/, '')
if (!/^https?:\/\/[^\s/?#]+(\/[^\s?#]*)?$/.test(url)) die(`--url: an http(s) URL, e.g. ${tailnetUrl(p, 'node.tailnet.ts.net')}`)
const key = loadBridgeKey(appDir(p))
const { code, expiresAt } = createPairingCode(appDir(p))
const payload = pairUri({ url, code, profile: p.id, fingerprint: key.fingerprint })

console.log(`Pair a phone with ${whose}.\n`)
if (!values['no-qr']) {
  // Loaded here, so the text below still comes when the dependency isn't installed yet.
  const qrcode = (await import('qrcode-terminal').catch(() => null))?.default
  if (qrcode) {
    qrcode.setErrorLevel('M')
    qrcode.generate(payload, { small: true }, qr => console.log(qr))
  } else console.log('(No QR code: run `bun install` in companion/bridge once. The text below works too, e.g. in a QR app.)')
}
const at = new Date(expiresAt)
const until = `${String(at.getHours()).padStart(2, '0')}:${String(at.getMinutes()).padStart(2, '0')}`
console.log(`
${payload}

In the app: "📷 Skeniraj QR · Scan the QR code" (on the first screen; to pair again, tap the tutor's status dot on Home).
The code works once, until ${until} (10 minutes).
Bridge ${url} → 127.0.0.1:${p.port}, key fingerprint ${groupFingerprint(key.fingerprint)}`)
if (!(await bridgeUp(p.port))) {
  console.log(`\n⚠️  No bridge answers on 127.0.0.1:${p.port}: start the tutor first (companion/bin/lani-session${p.isDefault ? '' : ` --profile ${p.id}`}).`)
}
