// Content shared by several sections: a module spec, a scenario, and the module-spec reference.
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

export const spec = {
  schema: 'lani.module/v0',
  id: 'clitic-se',
  title: 'Where does se go?',
  level: 'A1',
  targets: ['clitic_placement_se'],
  exercises: [
    { type: 'reorder', prompt: 'My name is Jan.', tokens: ['se', 'Imenujem', 'Jan.'], solutions: [['Imenujem', 'se', 'Jan.']] },
    { type: 'choice', prompt: 'Which is correct?', options: ['Se imenujem Jan.', 'Imenujem se Jan.'], answer: 1 },
  ],
}

// The lani-studio reference whose JSON examples must validate.
export const reference = readFileSync(resolve(import.meta.dir, '../../../../.claude/skills/lani-studio/reference/module-spec.md'), 'utf8')

export const postOffice = {
  schema: 'lani.scenario/v0',
  id: 'na-posti',
  title: 'Na pošti · At the post office',
  emoji: '📮',
  level: 'A1',
  setting: 'The post office in Nova Gorica. You want to send a parcel to Germany.',
  role: 'Uslužbenka Mojca at the counter. Patient, says vi.',
  goals: ['Greet her', 'Say you want to send a parcel', 'Ask how much it costs'],
  opener_sl: 'Dober dan! Izvolite?',
  opener_en: 'Hello! How can I help?',
}
