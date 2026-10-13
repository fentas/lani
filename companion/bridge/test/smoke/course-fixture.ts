// A small Slovene course for the smoke checks (companion/COURSE.md): its outline (A1: one unit written, one planned; A2,
// B1 and B2 empty) and the unit "biti", which has every one of the school's four types, an intro with a table, rows and
// questions, two worksheets and a test. The harness writes them to the bridge's course root before it starts.
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const review = 'machine-written, not reviewed by a native speaker'

export const outline = {
  schema: 'lani.course/v0',
  language: 'sl',
  levels: [
    {
      level: 'A1',
      units: [
        { id: 'biti', title: { sl: 'Biti: sem, si, je', en: 'To be: sem, si, je', de: 'Sein: sem, si, je', it: 'Essere: sem, si, je' } },
        { id: 'osebni-zaimki', title: { sl: 'Jaz, ti, on, ona', en: 'I, you, he, she', de: 'Ich, du, er, sie', it: 'Io, tu, lui, lei' }, grammar: ['osebni-zaimki'], planned: true },
      ],
    },
    { level: 'A2', units: [] },
    { level: 'B1', units: [] },
    { level: 'B2', units: [] },
  ],
  machine_written: true,
  review,
}

export const sort = {
  type: 'sort',
  grammar: 'biti',
  instruction: 'Razvrsti · Sort',
  columns: [
    { label: 'ednina · singular', words: ['sem', 'si', 'je'] },
    { label: 'množina · plural', words: ['smo', 'ste', 'so'] },
  ],
  explain: 'One person: sem, si, je; three or more: smo, ste, so.',
}

export const table = {
  type: 'table',
  grammar: 'biti',
  instruction: 'Dopolni tabelo · Fill in the table',
  prompt: 'biti (sedanjik) · to be (present)',
  lemma: 'biti',
  columns: ['ednina · singular', 'množina · plural'],
  rows: [
    { label: '1. oseba', cells: ['sem', { accept: ['smo'], form: 'pres.1pl' }] },
    { label: '2. oseba', cells: [{ accept: ['si'], form: 'pres.2sg', hint: 'ti …' }, { accept: ['ste'], form: 'pres.2pl' }] },
    { label: '3. oseba', cells: [{ accept: ['je'], form: 'pres.3sg' }, 'so'] },
  ],
}

export const gaptext = {
  type: 'gaptext',
  grammar: 'biti',
  instruction: 'Dopolni z glagolom biti · Fill in with biti',
  title: 'Pri babici Micki',
  text: 'Danes ___ pri babici Micki. Babica ___ v kuhinji, otroci ___ na vrtu.',
  gaps: [
    { accept: ['sem'], hint: 'jaz → ?' },
    { accept: ['je'], hint: 'ona → ?' },
    { accept: ['so'], hint: 'oni → ?', explain: 'otroci = oni (three or more): so.' },
  ],
  bank: true,
  distractors: ['si'],
  translation: "Today I'm at Granny Micka's. Granny is in the kitchen, the children are in the garden.",
}

export const transform = {
  type: 'transform',
  grammar: 'zanikanje',
  instruction: 'Zanikaj · Say it with no',
  cue: 'ne',
  items: [
    { from: 'Sem doma.', accept: ['Nisem doma.'], hint: 'sem → nisem' },
    { from: 'Babica je v kuhinji.', accept: ['Babica ni v kuhinji.'], lemma: 'biti', form: 'neg.3sg' },
  ],
}

export const unit = {
  schema: 'lani.unit/v0',
  id: 'biti',
  language: 'sl',
  level: 'A1',
  emoji: '🟰',
  title: { sl: 'Biti: sem, si, je', en: 'To be: sem, si, je', de: 'Sein: sem, si, je', it: 'Essere: sem, si, je' },
  goal: { en: "You can say who and where people are, and who isn't.", de: 'Du kannst sagen, wer und wo jemand ist.', it: 'Sai dire chi e dove sono le persone.' },
  grammar: ['biti', 'zanikanje'],
  words: ['doma', 'kuhinja', 'šola'],
  intro: {
    table: { head: [{ sl: 'oseba', en: 'who' }, { sl: 'biti', en: 'to be' }] },
    steps: [
      {
        say: { en: '**Biti** (to be) has a short form for each person. Start with one person: I, you, he or she.' },
        examples: [{ sl: 'Jaz sem {learner}.', en: 'I am {learner}.' }],
        rows: [['jaz', 'sem'], ['ti', 'si'], ['on, ona, ono', 'je']],
        ask: { type: 'choice', prompt: 'Babica ___ v kuhinji.', options: ['sem', 'si', 'je'], answer: 2, explain: 'Babica is one person (ona): je.' },
      },
      {
        say: { en: 'Three or more people: smo, ste, so.' },
        rows: [['mi', 'smo'], ['vi', 'ste'], ['oni', 'so']],
        ask: { type: 'cloze', text: 'Mi ___ doma.', accept: ['smo'] },
      },
    ],
  },
  worksheets: [
    { id: 'biti-1', title: { sl: 'Oblike', en: 'The forms' }, exercises: [sort, table] },
    { id: 'biti-2', title: { sl: 'Pri babici Micki', en: "At Granny Micka's" }, exercises: [gaptext, transform] },
  ],
  test: {
    pass: 80,
    exercises: [
      { type: 'choice', prompt: 'Ti ___ utrujen.', options: ['sem', 'si', 'je'], answer: 1 },
      { type: 'cloze', text: 'Oni ___ v šoli.', accept: ['so'] },
    ],
  },
  machine_written: true,
  review,
}

/** The course root the bridge reads (LANI_COURSE_DIR): <root>/sl/course.json and <root>/sl/units/biti.json. */
export function writeCourse(root: string) {
  mkdirSync(join(root, 'sl/units'), { recursive: true })
  writeFileSync(join(root, 'sl/course.json'), JSON.stringify(outline, null, 2))
  writeFileSync(join(root, 'sl/units/biti.json'), JSON.stringify(unit, null, 2))
}
