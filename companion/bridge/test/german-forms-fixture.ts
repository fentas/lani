// The app's parity fixture for German's forms (test/fixtures/german-forms.json): each noun's forms in every slot as
// german-forms.ts makes them, which the app's data/GermanForms.kt must make too (GermanFormsTest) and the smoke checks
// against german-forms.ts (test/smoke/case-de.ts). `bun test/german-forms-fixture.ts --write` makes it again.
import { readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { validateCaseTriggersDe } from '../src/case-triggers-de'
import { GermanForms } from '../src/german-forms'

const repo = resolve(import.meta.dir, '../..')
const t = validateCaseTriggersDe(JSON.parse(readFileSync(join(repo, 'grammar/de/data/case-triggers.json'), 'utf8')))
if (!t.ok) throw new Error(t.errors)
const f = new GermanForms(t.table)
const NOUNS: [string, 'm' | 'f' | 'n', string | undefined][] = [
  ['Hund', 'm', 'Hunde'], ['Tisch', 'm', 'Tische'], ['Lehrer', 'm', 'Lehrer'], ['Apfel', 'm', 'Äpfel'], ['Abend', 'm', 'Abende'], ['Fuß', 'm', 'Füße'], ['Bus', 'm', 'Busse'], ['See', 'm', 'Seen'],
  ['Käse', 'm', undefined], ['Kollege', 'm', undefined], ['Herr', 'm', undefined], ['Bub', 'm', undefined], ['Name', 'm', undefined], ['Student', 'm', undefined],
  ['Frau', 'f', 'Frauen'], ['Oma', 'f', 'Omas'], ['Mutter', 'f', 'Mütter'], ['Kuh', 'f', 'Kühe'],
  ['Haus', 'n', 'Häuser'], ['Kind', 'n', 'Kinder'], ['Auto', 'n', 'Autos'], ['Mädchen', 'n', 'Mädchen'], ['Zeugnis', 'n', 'Zeugnisse'], ['Herz', 'n', undefined], ['Brot', 'n', 'Brote'], ['Museum', 'n', 'Museen'],
]
const out = {
  about: 'German nouns by case and number as bridge/src/german-forms.ts makes them: the app (data/GermanForms.kt) must make the same. Made by test/german-forms-fixture.ts.',
  nouns: NOUNS.map(([lemma, gender, plural]) => ({
    lemma, gender, ...(plural ? { plural } : {}),
    slots: Object.fromEntries(['nom', 'acc', 'dat', 'gen'].flatMap(c => ['sg', 'pl'].map(n => [`${c}.${n}`, f.noun(lemma, gender, plural, c as 'nom', n as 'sg')]))),
  })),
  adjectives: ['groß', 'hoch', 'dunkel', 'rosa'].flatMap(a => (['der', 'ein', null] as const).flatMap(d => ['nom', 'acc', 'dat', 'gen'].flatMap(c => (['m', 'f', 'n'] as const).flatMap(g => ['sg', 'pl'].map(n => ({ lemma: a, det: d, slot: `${c}.${g}.${n}`, form: f.adjectiveForm(a, d, c as 'nom', g, n as 'sg') })))))),
  determiners: ['der', 'ein', 'kein', 'mein', 'euer', 'dieser'].flatMap(d => ['nom', 'acc', 'dat', 'gen'].flatMap(c => (['m', 'f', 'n'] as const).flatMap(g => ['sg', 'pl'].map(n => ({ lemma: d, slot: `${c}.${g}.${n}`, form: f.determinerForm(d, c as 'nom', g, n as 'sg') ?? null }))))),
}
const path = join(repo, 'bridge/test/fixtures/german-forms.json')
if (process.argv.includes('--write')) writeFileSync(path, JSON.stringify(out, null, 1) + '\n')
else console.log(JSON.stringify(out).length, 'bytes; --write to save')
