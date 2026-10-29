// The talk benchmark's scores: each variant's WER, takes needing a retake, nothing heard, sure (sent by itself) and
// wrong while sure, noise let through, and latency. The decisions are the bridge's own (src/stt-talk.ts).
//   bun score.ts <items.json> <raw.jsonl> --lexicon <dir with it.json> --voice <voice.json> [--split dev|test|all]
//        [--md out.md] [--title "…"] [--weighing '{"sureWord": 0.9}']
// dev and test: the even and the odd clips (the weighing's settings were chosen on dev).
import { readFileSync, writeFileSync } from 'node:fs'
import { Lexicon, normalizeForm } from '../../bridge/src/lexicon'
import { cleanResult, type SttResult } from '../../bridge/src/features/stt'
import { keys, weigh, WEIGHING, type Knows, type TalkContext, type Voice, type Weighing } from '../../bridge/src/stt-talk'

const args = process.argv.slice(2)
const opt = (n: string) => (args.includes(n) ? args[args.indexOf(n) + 1] : undefined)
const [itemsPath, rawPath] = args
const split = opt('--split') ?? 'all'
const lex = new Lexicon({ dir: opt('--lexicon') ?? '.tmp/stt-bench/lexicon', language: 'it', tutorFile: '/nonexistent/lexicon.json' })
const dictKnows = lex.knows()
const knows: Knows = w => dictKnows(normalizeForm(w, 'it'))

const data = JSON.parse(readFileSync(itemsPath, 'utf8'))
const voices: Record<string, Voice> = JSON.parse(readFileSync(opt('--voice') ?? 'voice.json', 'utf8'))
const W: Weighing = { ...WEIGHING, ...(opt('--weighing') ? JSON.parse(opt('--weighing')!) : {}) }
const items = new Map<string, any>(data.items.map((i: any) => [i.id, i]))
// lines in another language than Italian (Slovene, German words the Italian voice read): not Italian speech
const EXCLUDE = new Set([...items.values()].filter((i: any) => /^(veverica|zajec|Netz|rechts)$/.test(i.text.trim())).map((i: any) => i.id))
type Raw = { id: string; cond: string; pass: string; took: number; decode: number; seconds: number; prompt: string | null; r: any }
const raws = readFileSync(rawPath, 'utf8').trim().split('\n').map(l => JSON.parse(l) as Raw)
const takes = new Map<string, Record<string, Raw>>()
for (const r of raws) {
  const k = `${r.cond}/${r.id}`
  takes.set(k, { ...(takes.get(k) ?? {}), [r.pass]: r })
}

const UNITS = ['zero', 'uno', 'due', 'tre', 'quattro', 'cinque', 'sei', 'sette', 'otto', 'nove', 'dieci', 'undici', 'dodici', 'tredici', 'quattordici', 'quindici', 'sedici', 'diciassette', 'diciotto', 'diciannove']
const TENS = ['', '', 'venti', 'trenta', 'quaranta', 'cinquanta', 'sessanta', 'settanta', 'ottanta', 'novanta']
const spell = (n: number): string => {
  if (n < 20) return UNITS[n]
  if (n < 100) {
    const t = TENS[Math.floor(n / 10)]
    const u = n % 10
    return u === 0 ? t : (u === 1 || u === 8 ? t.slice(0, -1) : t) + UNITS[u]
  }
  return n === 100 ? 'cento' : String(n)
}
/** Words as compared: the bridge's keys, numbers written out ("10" → "dieci"). */
const words = (t: string) => keys(t.replace(/\d+/g, d => ` ${spell(Number(d))} `))
const wer = (h: string[], r: string[]) => {
  const d = Array.from({ length: r.length + 1 }, (_, j) => j)
  for (let i = 1; i <= h.length; i++) {
    let prev = d[0]
    d[0] = i
    for (let j = 1; j <= r.length; j++) {
      const t = d[j]
      d[j] = Math.min(d[j] + 1, d[j - 1] + 1, prev + (h[i - 1] === r[j - 1] ? 0 : 1))
      prev = t
    }
  }
  return d[r.length]
}

const res = (r: Raw | undefined): SttResult | null => (r ? cleanResult(r.r) : null)
const ctxOf = (i: any, which: 'ctx' | 'far'): TalkContext => ({ said: which === 'ctx' ? i.said : i.far, words: i.hints, names: i.names })

type Out = { text: string; sure: boolean; ms: number; noise?: boolean }
type T = Record<string, Raw>
const hear = (t: T, i: any, which: 'ctx' | 'far', k: Knows, v: boolean): Out | null => {
  const p = t[which]
  if (!p || !t.plain) return null
  const h = weigh(res(p)!, res(t.plain), ctxOf(i, which), p.prompt ?? '', 'it', k, v ? voices[`${p.cond}/${p.id}`] ?? null : null, W)
  return { text: h.text, sure: h.sure, ms: p.took + t.plain.took, noise: h.noise }
}
const VARIANTS: Record<string, (t: T, i: any) => Out | null> = {
  'plain (today)': t => (t.plain ? { text: res(t.plain)!.text, sure: false, ms: t.plain.took } : null),
  'context prompt': t => (t.ctx ? { text: res(t.ctx)!.text, sure: false, ms: t.ctx.took } : null),
  'context prompt, unrelated context': t => (t.far ? { text: res(t.far)!.text, sure: false, ms: t.far.took } : null),
  '+ second opinion': (t, i) => hear(t, i, 'ctx', null, false),
  '+ dictionary (near-misses to the tutor)': (t, i) => hear(t, i, 'ctx', knows, false),
  '+ voice check (all)': (t, i) => hear(t, i, 'ctx', knows, true),
  'all, unrelated context': (t, i) => hear(t, i, 'far', knows, true),
}
const related = (i: any) => {
  const said = new Set(keys(i.said.join(' ')).filter(k => k.length >= 4))
  return keys(i.text).some(k => k.length >= 4 && said.has(k))
}

const pct = (a: number, b: number) => (b ? `${((100 * a) / b).toFixed(1)} %` : '–')
const q = (xs: number[], p: number) => {
  const s = [...xs].sort((a, b) => a - b)
  return s.length ? s[Math.min(s.length - 1, Math.floor(p * s.length))] : NaN
}
const lines: string[] = []
const title = opt('--title') ?? rawPath
lines.push(`### ${title} (${split})`, '')
for (const cond of ['clean', 'teo', 'teo-related']) {
  lines.push(`**${cond}**`, '', '| Variant | Takes | WER | Needs a retake | Nothing heard | Sure (sent by itself) | Wrong while sure | Latency median / p90 |', '|---|---|---|---|---|---|---|---|')
  for (const [name, f] of Object.entries(VARIANTS)) {
    let n = 0, edits = 0, refWords = 0, retake = 0, nothing = 0, sure = 0, wrongSure = 0
    const ms: number[] = []
    for (const [k, t] of takes) {
      const c = cond === 'teo-related' ? 'teo' : cond
      if (!k.startsWith(c + '/')) continue
      const id = k.slice(c.length + 1)
      if (EXCLUDE.has(id)) continue
      if (split === 'dev' && Number(id) % 2 === 1) continue
      if (split === 'test' && Number(id) % 2 === 0) continue
      const i = items.get(id)
      if (cond === 'teo-related' && !related(i)) continue
      const o = f(t, i)
      if (!o) continue
      n++
      const h = words(o.text)
      const r = words(i.text)
      edits += wer(h, r)
      refWords += r.length
      const wrong = h.join('') !== r.join('')
      if (wrong) retake++
      if (!h.length) nothing++
      if (o.sure) sure++
      if (o.sure && wrong) wrongSure++
      ms.push(o.ms + t.plain.decode)
    }
    if (!n) continue
    lines.push(`| ${name} | ${n} | ${pct(edits, refWords)} | ${pct(retake, n)} | ${pct(nothing, n)} | ${pct(sure, n)} | ${pct(wrongSure, n)} | ${q(ms, 0.5).toFixed(2)} / ${q(ms, 0.9).toFixed(2)} s |`)
  }
  lines.push('')
}
// noise: what lands in the draft from a take with no words
lines.push('**noise** (120 takes without words: room, a sung "ooo", a reversed mumble, breaths and knocks)', '', '| Variant | Takes | Text in the draft | Sent by itself |', '|---|---|---|---|')
for (const [name, f] of Object.entries(VARIANTS)) {
  let n = 0, text = 0, sure = 0
  for (const [k, t] of takes) {
    if (!k.startsWith('noise/')) continue
    const i = items.get(k.slice(6))
    const o = f(t, i)
    if (!o) continue
    n++
    if (words(o.text).length) text++
    if (o.sure) sure++
  }
  if (n) lines.push(`| ${name} | ${n} | ${pct(text, n)} | ${pct(sure, n)} |`)
}
const md = lines.join('\n')
console.log(md)
if (opt('--md')) writeFileSync(opt('--md')!, md + '\n')
