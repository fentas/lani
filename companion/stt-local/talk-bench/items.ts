// The talk benchmark's items (companion/stt-local/README.md, "Talks"): each clip with the contexts a talk would give it,
// built by the bridge's own talkPrompt.
//   bun items.ts <corpus dir> <audio manifest> <out items.json>
// ctx: the 1-2 lines before it in its source (the corpus keeps a scene's or a road's lines in order), never its own
//      text; far: two lines of another source (the learner says something the character didn't). Both with the names
//      (the learner, Teo, and a Friuli villager) and the villager's talk phrases and likes, as villagerScenario gives them.
import { readFileSync, writeFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { talkPrompt } from '../../bridge/src/stt-talk'

const [corpusDir, manifestPath, outPath] = process.argv.slice(2)
const clips: { file: string; text: string; voice: string; words: number; source: string }[] = JSON.parse(readFileSync(`${corpusDir}/corpus.json`, 'utf8'))
const manifest: { id: string; cond: string; audio: string; seconds: number }[] = JSON.parse(readFileSync(manifestPath, 'utf8'))
const villagers = [
  ['Apicoltore Aldo', ['le api', 'il miele']], ['Nonno Bepi', ['le storie', 'il tiglio']], ['Fabbro Bruno', ['il ferro', 'i cavalli']],
  ['Pastore Davide', ['le pecore', 'i campanacci']], ['Maestra Elena', ['i libri', 'la corsa']], ['Mugnaio Franco', ['il mais', 'il mulino']],
  ['Giulia', ['gli agnellini', 'le ciliegie']], ['Lorenzo', ['i cavalli', 'il trattore']], ['Ostessa Marisa', ['il frico', 'il caffè']],
  ['Vignaiolo Matteo', ["l'uva", 'la vendemmia']], ['Zia Nives', ['la radio', 'le notizie']], ['Nonna Rosa', ['la gubana', 'i nipotini']],
  ['Tommaso', ['il calcio', 'lo stagno']],
] as const
const h = (s: string) => parseInt(createHash('sha256').update(s).digest('hex').slice(0, 8), 16)
const norm = (s: string) => s.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim()

const items = clips.map((c, n) => {
  const [who, likes] = villagers[h(c.file) % villagers.length]
  const words = ['Come stai?', 'Bene, grazie. E tu?', ...likes, 'Ciao!']
  const names = ['Teo', who]
  const before: string[] = []
  for (let i = n - 1; i >= 0 && before.length < 2; i--) {
    if (clips[i].source !== c.source) break
    if (norm(clips[i].text) !== norm(c.text) && !before.includes(clips[i].text)) before.unshift(clips[i].text)
  }
  const others = clips.filter(o => o.source !== c.source && norm(o.text) !== norm(c.text))
  const far = [others[h(c.file + 'a') % others.length].text, others[h(c.file + 'b') % others.length].text]
  return {
    id: String(n).padStart(4, '0'),
    text: c.text,
    voice: c.voice,
    source: c.source,
    words: c.words,
    names,
    hints: words,
    said: before,
    far,
    prompt: { ctx: talkPrompt({ said: before, words, names }), far: talkPrompt({ said: far, words, names }) },
  }
})
const noise = manifest.filter(m => m.cond === 'noise').map((m, i) => {
  const c = items[(i * 37) % items.length]
  return { id: m.id, text: '', voice: 'none', source: 'noise', words: 0, names: c.names, hints: c.hints, said: c.said, far: c.far, prompt: c.prompt }
})
writeFileSync(outPath, JSON.stringify({ items: [...items, ...noise], manifest }, null, 1))
console.log(items.length, 'clips,', noise.length, 'noise takes; e.g.:')
for (const i of [0, 3, 400, 800, 1200]) console.log(' ', items[i].text, ' ⇐ ', items[i].prompt.ctx, ' | far: ', items[i].prompt.far)
