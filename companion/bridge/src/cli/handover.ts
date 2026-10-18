#!/usr/bin/env bun
// companion/bin/lani-handover (`lani handover`): move a learner's data between this node and their phone through the data
// repository on GitHub (docs/plans/05-built/git-sync.md, "Switching: node → phone → node").
//
//   lani handover [--profile <id>] [--code] [--device <id>] [--name <phone>] [--yes] [--no-qr]
//       commit and push the data, hand the lease over to the phone, and show the town's secrets as a QR code for the
//       app (Podatki · Data → "📷 Skeniraj kodo računalnika · Scan the computer's code"). From then on this node only
//       reads the data. --code: the QR code needs a code typed on the phone too (a photo of the screen alone opens
//       nothing). --device: only that phone may take it (its id is in the app's Data section).
//   lani handover --take [--force] [--profile <id>]
//       take the data back once the phone handed it over (in the app: "Predaj računalniku · Hand over to the computer"):
//       pull, take the lease, push. --force: the phone is lost; take it without a handover.
//   lani handover --status [--profile <id>]
//       who writes the data now.
//
// The git side is .claude/hooks/lani_data_repo.py's (handover, take, lease); this script adds the QR code.
import '../env' // first: LANI_* from the FLUENT_* names of a node set up before the rename
import { spawnSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { createInterface } from 'node:readline/promises'
import { parseArgs } from 'node:util'
import { encodeHandover, githubRepo, HANDOVER_TTL_MS, HANDOVER_VERSION, scalarOf, type HandoverBundle } from '../handover'
import { groupFingerprint, loadTownKey } from '../pairing'
import { appDir, die, projectDir, resolveProfile, short } from './common'

const { values } = parseArgs({
  args: process.argv.slice(2),
  options: {
    profile: { type: 'string', short: 'p' },
    take: { type: 'boolean', default: false },
    force: { type: 'boolean', default: false },
    status: { type: 'boolean', default: false },
    code: { type: 'boolean', default: false },
    device: { type: 'string' },
    name: { type: 'string' },
    yes: { type: 'boolean', short: 'y', default: false },
    'no-qr': { type: 'boolean', default: false },
    help: { type: 'boolean', short: 'h', default: false },
  },
})

if (values.help) {
  console.log(`usage: lani handover [--profile <id>] [--code] [--device <id>] [--name <phone>] [--yes] [--no-qr]
       lani handover --take [--force] [--profile <id>]
       lani handover --status [--profile <id>]`)
  process.exit(0)
}

const p = resolveProfile(values.profile)
const whose = `${p.name}'s data${p.isDefault ? '' : ` (profile ${p.id})`}`
if (!existsSync(join(p.dataDir, '.git'))) die(`${short(p.dataDir)} is not a data repository: companion/bin/lani-setup makes it one, with a remote on GitHub.`)

/** lani_data_repo.py <cmd> <data> --json …: its JSON answer. */
function repo(cmd: string, ...more: string[]): any {
  const r = spawnSync('python3', [join(projectDir, '.claude/hooks/lani_data_repo.py'), cmd, p.dataDir, '--json', ...more], { encoding: 'utf8', timeout: 300_000 })
  try {
    return JSON.parse(r.stdout)
  } catch {
    die(`lani_data_repo.py ${cmd} failed: ${(r.stderr || r.stdout || `exit ${r.status}`).trim()}`)
  }
}

if (values.status) {
  const s = repo('lease')
  console.log(`${whose}: ${s.describe}`)
  process.exit(0)
}

if (values.take) {
  const r = repo('take', ...(values.force ? ['--force'] : []))
  if (!r.ok) die(r.error)
  console.log(r.already ? `This node writes ${whose} already.` : `✅ This node writes ${whose} again${r.pushed ? ' (pushed to the repository)' : ''}.`)
  if (!r.already) console.log('The tutor session and the app paired with this node work as before; the phone only reads the data now.')
  process.exit(0)
}

// To the phone.
const before = repo('lease')
console.log(`${whose}: ${before.describe}\n`)
if (!['mine', 'offered-away'].includes(before.state)) die("This node doesn't write the data, so it can't hand it over.")
if (!values.yes) {
  if (!process.stdin.isTTY) die('Confirm with --yes (no terminal to ask in).')
  const rl = createInterface({ input: process.stdin, output: process.stdout })
  const answer = (await rl.question(`Hand ${whose} and the town over to the phone? This node then only reads it. [y/N] `)).trim().toLowerCase()
  rl.close()
  if (!['y', 'yes', 'j', 'ja', 'd', 'da'].includes(answer)) die('Nothing handed over.')
}
const r = repo('handover', ...(values.device ? ['--device', values.device] : []), ...(values.name ? ['--name', values.name] : []))
if (!r.ok) die(r.error)
console.log(`✅ Handed over: the data is pushed (${r.commit}), and the lease waits for the phone.\n`)

// The town's secrets, for the phone.
const key = loadTownKey(appDir(p))
const familyFile = join(appDir(p), 'family-token')
const family = existsSync(familyFile) ? readFileSync(familyFile, 'utf8').trim() : ''
const ghRepo = githubRepo(r.remote)
const bundle: HandoverBundle = {
  v: HANDOVER_VERSION,
  p: p.id,
  n: p.name,
  h: String(r.lease?.offer?.by?.device ?? ''),
  k: scalarOf(key),
  ...(family ? { t: family } : {}),
  ...(ghRepo ? { r: ghRepo } : {}),
  ...(r.branch && r.branch !== 'main' ? { b: r.branch } : {}),
}
const expiresAt = Date.now() + HANDOVER_TTL_MS
const { uri, code } = encodeHandover(bundle, { fingerprint: key.fingerprint, expiresAt, withCode: values.code })

if (!ghRepo) console.log(`⚠️  The data repository (${r.remote}) isn't on GitHub: the app syncs with GitHub only.\n`)
console.log(`In the app: 💾 Podatki · Data → "📷 Skeniraj kodo računalnika · Scan the computer's code".\n`)
if (values['no-qr']) {
  console.log(uri)
} else {
  const qrcode = (await import('qrcode-terminal').catch(() => null))?.default
  if (qrcode) {
    qrcode.setErrorLevel('L')
    qrcode.generate(uri, { small: true }, qr => console.log(qr))
  } else {
    console.log('(No QR code: run `bun install` in companion/bridge once. The text below works in a QR app too.)\n')
    console.log(uri)
  }
}
const at = new Date(expiresAt)
const until = `${String(at.getHours()).padStart(2, '0')}:${String(at.getMinutes()).padStart(2, '0')}`
if (code) console.log(`\nThe code to type on the phone: ${code.replace(/(.{4})(?=.)/g, '$1-')}`)
console.log(`\nIt works until ${until} (10 minutes). Town ${groupFingerprint(key.fingerprint)}.`)
console.log('It holds the town\'s key: don\'t photograph it or share this screen.')
if (process.stdin.isTTY && process.stdout.isTTY) {
  const rl = createInterface({ input: process.stdin, output: process.stdout })
  await rl.question('\nPress Enter once the phone has it: the code is wiped from the screen. ')
  rl.close()
  process.stdout.write('\x1b[2J\x1b[3J\x1b[H') // the screen and its scrollback
  console.log(`${whose}: handed over to the phone. Take it back here with: lani handover --take${p.isDefault ? '' : ` --profile ${p.id}`}`)
}
