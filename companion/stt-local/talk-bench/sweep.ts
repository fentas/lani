// A sweep of the weighing's settings (stt-talk.ts Weighing) on one split of the teo takes and on the noise takes: WER,
// retakes, nothing heard, sure, wrong while sure, noise into the draft (companion/stt-local/README.md, "Talks").
//   bun sweep.ts <items.json> <raw.jsonl> <dev|test|all> '<json list of Weighing overrides>' <lexicon dir> <voice.json>
import { readFileSync } from 'node:fs'
import { Lexicon, normalizeForm } from '../../bridge/src/lexicon'
import { cleanResult } from '../../bridge/src/features/stt'
import { keys, weigh, WEIGHING, type Knows } from '../../bridge/src/stt-talk'
const [itemsPath, rawPath, split, grid, lexDir, voicePath] = process.argv.slice(2)
const lex = new Lexicon({ dir: lexDir ?? '.tmp/stt-bench/lexicon', language: 'it', tutorFile: '/nonexistent' })
const dk = lex.knows()
const knows: Knows = w => dk(normalizeForm(w, 'it'))
const voices = JSON.parse(readFileSync(voicePath ?? 'voice.json', 'utf8'))
const items = new Map<string, any>(JSON.parse(readFileSync(itemsPath, 'utf8')).items.map((i: any) => [i.id, i]))
const takes = new Map<string, any>()
for (const l of readFileSync(rawPath, 'utf8').trim().split('\n')) { const r = JSON.parse(l); const k = `${r.cond}/${r.id}`; takes.set(k, { ...(takes.get(k) ?? {}), [r.pass]: r }) }
const UN = ['zero','uno','due','tre','quattro','cinque','sei','sette','otto','nove','dieci','undici','dodici']
const words = (t: string) => keys(t.replace(/\d+/g, d => ` ${UN[Number(d)] ?? d} `))
const wer = (h: string[], r: string[]) => { const d = Array.from({ length: r.length + 1 }, (_, j) => j); for (let i = 1; i <= h.length; i++) { let p = d[0]; d[0] = i; for (let j = 1; j <= r.length; j++) { const t = d[j]; d[j] = Math.min(d[j] + 1, d[j - 1] + 1, p + (h[i - 1] === r[j - 1] ? 0 : 1)); p = t } } return d[r.length] }
for (const over of JSON.parse(grid)) {
  const o = { ...WEIGHING, ...over }
  const m: Record<string, number> = { n: 0, e: 0, rw: 0, retake: 0, nothing: 0, sure: 0, wrongSure: 0, noise: 0, noiseText: 0, noiseSure: 0 }
  for (const [k, t] of takes) {
    if (!t.ctx || !t.plain) continue
    const [cond, id] = k.split('/')
    const i = items.get(id)
    const h = weigh(cleanResult(t.ctx.r)!, cleanResult(t.plain.r), { said: i.said, words: i.hints, names: i.names }, t.ctx.prompt, 'it', knows, voices[k] ?? null, o)
    if (cond === 'noise') { m.noise++; if (words(h.text).length) m.noiseText++; if (h.sure) m.noiseSure++; continue }
    if (cond !== 'teo' || /^(veverica|zajec|Netz|rechts)$/.test(i.text)) continue
    if (split === 'dev' && Number(id) % 2) continue
    if (split === 'test' && !(Number(id) % 2)) continue
    const hw = words(h.text), r = words(i.text)
    m.n++; m.e += wer(hw, r); m.rw += r.length
    const wrong = hw.join('') !== r.join('')
    if (wrong) m.retake++
    if (!hw.length) m.nothing++
    if (h.sure) m.sure++
    if (h.sure && wrong) m.wrongSure++
  }
  const p = (a: number, b: number) => (100 * a / b).toFixed(1).padStart(5)
  console.log(`${JSON.stringify(over).padEnd(46)} WER ${p(m.e, m.rw)} retake ${p(m.retake, m.n)} nothing ${p(m.nothing, m.n)} sure ${p(m.sure, m.n)} wrongSure ${p(m.wrongSure, m.n)} (prec ${p(m.sure - m.wrongSure, m.sure)}) | noise text ${p(m.noiseText, m.noise)} sure ${p(m.noiseSure, m.noise)}`)
}
