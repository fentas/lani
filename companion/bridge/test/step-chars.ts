// How much the village projects' steps add to voice (companion/SCENES.md "Project steps"): the corpus's step items, said
// to Jan, in each speaker's voice (bin/voice-build's way), their characters, and how many voice-build would make anew
// (not already a text of the scenes' dialogs or the packs in the same voice). `bun test/step-chars.ts [culture]`.
import { join, resolve } from 'node:path'
import { corpus } from '../src/voice'
import { cultureStepFiles, stepTellings } from '../src/project-steps'
import { VillagerStore } from '../src/villagers'
import { PackStore, packsDirs } from '../src/packs'
import { SceneStore, scenesDirs } from '../src/scenes'
import { JAN } from './learner-content'

const companion = resolve(import.meta.dir, '../..')
const cultures = join(companion, 'cultures')
const id = process.argv[2] ?? 'primorska'
const villagers = new VillagerStore(join(cultures, id, 'villagers'), '/tmp/lani-step-chars-v').all()
const packs = new PackStore(packsDirs(join(companion, 'packs'), cultures, id, 'sl'), '/tmp/lani-step-chars-p')
const scenes = new SceneStore(scenesDirs(join(companion, 'scenes'), cultures, id, 'sl'), '/tmp/lani-step-chars-s', () => ({ packs: packs.all(), scenarios: [], villagers }), () => {})
const steps = cultureStepFiles(cultures, id).map(f => ({ project: f.project, tellings: stepTellings(f) }))
const only = corpus({ learner: JAN, packs: [], modules: [], scenarios: [], villagers: [], steps, ...{} as object })
  .filter(i => i.source.startsWith('project:'))
// with the villagers' voices (each speaker's), as voice-build makes them
const voiced = corpus({ learner: JAN, packs: [], modules: [], scenarios: [], villagers, steps }).filter(i => i.source.startsWith('project:'))
const rest = corpus({ learner: JAN, packs: packs.all(), modules: [], scenarios: [], villagers, scenes: scenes.resolved() })
const had = new Set(rest.map(i => `${i.voice}\t${i.text}`))
const fresh = voiced.filter(i => !had.has(`${i.voice}\t${i.text}`))
const chars = (l: { text: string }[]) => l.reduce((n, i) => n + i.text.length, 0)
console.log(`${id}: ${voiced.length} step texts to voice (${chars(voiced)} characters), ${fresh.length} not voiced by other content (${chars(fresh)} characters); ${only.length} texts in the default voices`)
const byProject = new Map<string, number>()
for (const i of fresh) byProject.set(i.source, (byProject.get(i.source) ?? 0) + i.text.length)
console.log([...byProject].map(([k, n]) => `${k.slice(8)} ${n}`).join(', '))
