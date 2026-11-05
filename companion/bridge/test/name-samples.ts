// Lines with the learner's name built by the real store from real takes: the splice test's (docs/plans/05-built/audio-names.md),
// replayed by the fake ElevenLabs, so nothing is voiced and nothing is spent. What it writes is what an older app downloads, the
// same parts the phone joins: samples to listen to, and a manifest beside each for measuring them.
//
//   bun test/name-samples.ts <the test's folder, with takes/ and store/> <folder to write into>
import './isolate'
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { addressee } from '../src/addressee'
import { NameCatalogue, PROBE_NAME } from '../src/name-lines'
import { NameStore } from '../src/name-store'
import { ElevenLabs, VoiceStore } from '../src/voice'
import { fakeEleven } from './fakes/eleven'

const [kit, out] = process.argv.slice(2)
if (!kit || !out || !existsSync(join(kit, 'takes'))) {
  console.error('usage: bun test/name-samples.ts <the splice test folder> <folder to write into>')
  process.exit(1)
}
mkdirSync(out, { recursive: true })

// id, voice, the line: the test's lines, in the cast's voices
const LINES: [string, string, string][] = [
  ['g1', 'grandma', '{n}! Pridi, juha je vroča.'],
  ['g2', 'grandma', 'O, {n}! Ravno kuham. Si lačen?'],
  ['g3', 'grandma', 'Hvala, {n}. Ti si zlat.'],
  ['g4', 'grandma', '{n}? Še buden? Prinesla sem odejo. In vroč čaj!'],
  ['p1', 'grandpa', 'Lovski blagor, {n}!'],
  ['p2', 'grandpa', 'Dober dan, {n}! Si prišel pomagat?'],
  ['p3', 'grandpa', 'Lahko noč, {n}.'],
  ['p4', 'grandpa', '{n}, imaš čas za eno zgodbo?'],
  ['p5', 'grandpa', 'Glasen? Kdo je glasen, {n}?'],
  ['t1', 'teacher', '{n}! To je pa res posebno darilo. Najlepša hvala!'],
  ['t2', 'teacher', 'Hvala, {n}! Odlično darilo. Pet plus!'],
  ['t3', 'teacher', 'Hvala, {n}. Odlično delo.'],
  ['t4', 'teacher', '{n}, kaj nosiš na čelu sprevoda?'],
  ['t5', 'teacher', 'Oho? Še enkrat, {n}, in na glas.'],
]

// every take of the test by what it says and who says it, played in turn: a second ask gets the second take
const takes = new Map<string, { audio: Uint8Array; alignment: unknown }[]>()
for (const f of readdirSync(join(kit, 'takes')).filter(n => n.endsWith('.json'))) {
  const m = JSON.parse(readFileSync(join(kit, 'takes', f), 'utf8'))
  const key = `${m.voice}|${m.text}`
  takes.set(key, [...(takes.get(key) ?? []), { audio: new Uint8Array(readFileSync(join(kit, 'takes', f.replace('.json', '.mp3')))), alignment: m.alignment }])
}
const asked: string[] = []
const missing: string[] = []
const given = new Map<string, number>()
const fe = fakeEleven()
fe.st.script = (voice, text) => {
  const key = `${voice}|${text}`
  const all = takes.get(key)
  asked.push(text)
  if (!all) {
    missing.push(text)
    return undefined
  }
  const n = given.get(key) ?? 0
  given.set(key, n + 1)
  return all[n % all.length]
}

const vs = new VoiceStore({ appDir: mkdtempSync(join(tmpdir(), 'lani-name-samples-')), engines: [new ElevenLabs({ key: 'test-key', base: fe.url, reserve: 0 })], queueDelayMs: 0, language: 'sl' })
const rendered = LINES.map(([, , l]) => l.replace('{n}', PROBE_NAME))
const cat = new NameCatalogue('sl', rendered, rendered)
const logged: string[] = []
const ns = new NameStore(vs, { settings: { on: true, daily: 1_000_000, names: 10, takes: 2 }, catalogue: () => cat, log: (...a) => logged.push(a.join(' ')) })
const jan = addressee({ name: 'Jan' })

const rows: unknown[] = []
for (const [i, voice, line] of LINES) {
  const text = line.replace('{n}', 'Jan')
  const r = await ns.line(text, voice, jan)
  const flag = cat.find(text, 'Jan')
  if (r.say !== 'joined') {
    rows.push({ i, voice, text, type: flag?.type, template: flag?.template, joined: false, say: r.say })
    console.log(`${i} ${flag?.type} ${flag?.template}: NOT JOINED (${r.say}): ${text}`)
    continue
  }
  const id = r.url.match(/([a-f0-9]{40})/)![1]
  const m = ns.phone(id)!
  const file = await ns.finished(id)
  copyFileSync(file!, join(out, `${i}-joined.mp3`))
  const whole = join(kit, 'store', `${i}.mp3`)
  if (existsSync(whole)) copyFileSync(whole, join(out, `${i}-whole.mp3`))
  rows.push({ i, voice, text, type: flag?.type, template: flag?.template, joined: true, parts: m.parts.map(p => ({ kind: p.kind, ms: p.ms, ...(p.kind === 'gap' ? {} : { gain: p.gain }) })) })
  console.log(`${i} ${flag?.type} ${flag?.template}: ${m.parts.map(p => (p.kind === 'gap' ? `${p.ms} ms` : `${p.kind} ${p.ms} ms${p.gain ? ` ${p.gain} dB` : ''}`)).join(', ')}`)
}
writeFileSync(join(out, 'samples.json'), JSON.stringify(rows, null, 1))
console.log(`\n${asked.length} takes asked, ${missing.length} the test doesn't have${missing.length ? `: ${[...new Set(missing)].join(' | ')}` : ''}`)
for (const l of logged) console.log(`log: ${l}`)
process.exit(0)
