// Smoke checks: «Vidim, vidim» (I spy, companion/SCENES.md "I spy"; src/ispy.ts). The bridge serves none of it (the app
// bundles companion/ispy); it reads it for the voices: the child's lines and every scene's clues in the voice corpus
// (voice-build's "ispy" rows), in the voice of each child who may play in the scene.
import { join, resolve } from 'node:path'
import { ispyTexts, readISpy } from '../../src/ispy'
import { PackStore, packsDirs } from '../../src/packs'
import { ScenarioStore } from '../../src/scenarios'
import { SceneStore, scenesDirs } from '../../src/scenes'
import { VillagerStore } from '../../src/villagers'
import { corpus, PRIORITY_LABELS } from '../../src/voice'
import { check, culturesDir, dataDir } from './harness'
import { JAN } from '../learner-content'

const repo = resolve(import.meta.dir, '../../..')

export default async function ispy() {
  const content = readISpy(join(repo, 'ispy'))
  check("the child's lines in Slovene, Italian, German and English", ['sl', 'it', 'de', 'en'].every(l => content.lines[l]?.start?.[l]), Object.keys(content.lines))
  const packs = new PackStore(packsDirs(join(repo, 'packs'), culturesDir, 'primorska', 'sl'), join(dataDir, 'unused-packs-ispy')).all()
  const villagers = new VillagerStore(join(culturesDir, 'primorska/villagers'), join(dataDir, 'unused-villagers-ispy')).all()
  const scenarios = new ScenarioStore(join(repo, 'scenarios'), join(dataDir, 'unused-scenarios-ispy')).all()
  const scenes = new SceneStore(scenesDirs(join(repo, 'scenes'), culturesDir, 'primorska', 'sl'), join(dataDir, 'unused-scenes-ispy'), () => ({ packs, scenarios, villagers })).resolved()
  check('every scene of the Slovene village has its clues', scenes.length > 0 && scenes.every(s => content.books[s.id]), scenes.filter(s => !content.books[s.id]).map(s => s.id))

  // the kitchen's: the game's lines, each thing's find and reveal with its word, its clues; nothing with a placeholder
  check('the scenes as the bridge serves them, the kitchen among them', scenes.some(s => s.id === 'kuhinja'), scenes.map(s => s.id))
  const kitchen = scenes.find(s => s.id === 'kuhinja')!
  const words = Object.fromEntries(kitchen.objects.map(o => [o.slot, o.sl as string]))
  const texts = ispyTexts(content.lines.sl, content.books.kuhinja, 'sl', words, new Set(['door', 'fork']))
  check(
    "ispyTexts: the child's lines, the finds and the reveals with the thing's word (a plural's), the clues; no placeholder",
    ['Vidim, vidim nekaj, česar ti ne vidiš …', 'Ja, to je nož! Bravo!', 'To so vrata! Glej, tukaj so.', 'Je moder.', 'Mrzlo, mrzlo!'].every(t => texts.includes(t)) &&
      texts.every(t => !t.includes('{')),
    texts.slice(0, 12),
  )

  // in the corpus: after the scenes' dialogs; the kitchen (no child of its own: one comes by) in every child's voice, the
  // living room in Zala's (the girl's); the goodbye said to the learner (Jan: "našel")
  const items = corpus({ learner: JAN, packs: [], modules: [], scenarios: [], villagers, scenes, ispy: content })
  const at = (t: string, source: string) => items.filter(i => i.text === t && i.source === source)
  const p = PRIORITY_LABELS.indexOf('ispy')
  const found = at('Ja, to je nož! Bravo!', 'ispy:kuhinja')
  check(
    "corpus: I spy's lines after the scenes' dialogs, before the A2 tellings; where no child is in the scene, in every child's voice (one comes by)",
    p > PRIORITY_LABELS.indexOf('scenes') && p < PRIORITY_LABELS.indexOf('stories A2') && found.length >= 2 && found.every(i => i.priority === p) &&
      new Set(found.map(i => i.voice)).size === found.length && items.some(i => i.text === 'Vidim, vidim nekaj, česar ti ne vidiš' && i.priority === p),
    found,
  )
  const voices = new Set(items.filter(i => i.source === 'ispy:v-hisi').map(i => i.voice))
  check('… the living room in Zala\'s voice alone (the girl)', voices.size === 1 && voices.has('girl'), [...voices])
  check('… the goodbye said to the learner', items.some(i => i.source.startsWith('ispy:') && i.text.startsWith('Vse si našel!')) && !items.some(i => i.text.includes('{m:')))
}
