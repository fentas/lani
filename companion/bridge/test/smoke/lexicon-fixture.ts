// Tiny Wiktionary extracts in kaikki.org's shape, and the dictionaries companion/bin/lexicon-build makes of them: an
// Italian one (a download, it.json.gz and its manifest) and a Slovene one, both with their meanings in the other
// languages. Built once, the first time a section asks.
import { mkdirSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'

const t = (lang_code: string, word: string, sense: string, extra: object = {}) => ({ lang_code, word, sense, ...extra })

/** English Wiktionary's English entries: their translation tables (the pivot). */
export const english = [
  {
    word: 'house', pos: 'noun', lang_code: 'en',
    senses: [
      { glosses: ['A structure built or serving as an abode of human beings.'] },
      { glosses: ['A deliberative assembly.'], translations: [t('it', 'camera', 'debating chamber'), t('sl', 'zbornica', 'debating chamber'), t('de', 'Kammer', 'debating chamber')] },
    ],
    // a table the extract couldn't attach to a sense; a dialect's word
    translations: [t('it', 'casa', 'human abode'), t('sl', 'híša', 'human abode'), t('de', 'Haus', 'human abode'), t('de', 'Huus', 'human abode', { tags: ['Alemannic-German'] })],
  },
  { word: 'home', pos: 'noun', lang_code: 'en', senses: [{ glosses: ['A dwelling.'], translations: [t('it', 'casa', 'dwelling'), t('sl', 'dom', 'dwelling'), t('de', 'Heim', 'dwelling')] }] },
  // two words "bank": the money's and the river's
  { word: 'bank', pos: 'noun', lang_code: 'en', senses: [{ glosses: ['An institution where one can place and borrow money.'], translations: [t('it', 'banca', 'institution'), t('sl', 'banka', 'institution'), t('de', 'Bank', 'institution')] }] },
  { word: 'bank', pos: 'noun', lang_code: 'en', senses: [{ glosses: ['An edge of river, lake, or other watercourse.'], translations: [t('it', 'riva', 'edge of river'), t('sl', 'breg', 'edge of river'), t('de', 'Ufer', 'edge of river')] }] },
  { word: 'shadow', pos: 'noun', lang_code: 'en', senses: [{ glosses: ['A dark image.'], translations: [t('it', 'ombra', 'dark image'), t('sl', 'senca', 'dark image'), t('de', 'Schatten', 'dark image')] }] },
  // no Slovene in its table: Italian essere has no Slovene meaning
  { word: 'be', pos: 'verb', lang_code: 'en', senses: [{ glosses: ['To exist.'], translations: [t('it', 'essere', 'exist'), t('de', 'sein', 'exist')] }] },
  // "present" has a table for the time only (the gift has none): never taken for regalo, "present, gift"
  { word: 'present', pos: 'noun', lang_code: 'en', senses: [{ glosses: ['The current moment.'], translations: [t('sl', 'sedanjost', 'current time'), t('de', 'Gegenwart', 'current time')] }] },
  { word: 'gift', pos: 'noun', lang_code: 'en', senses: [{ glosses: ['Something given.'], translations: [t('it', 'regalo', 'present'), t('sl', 'darilo', 'present'), t('de', 'Geschenk', 'present')] }] },
]

/** English Wiktionary's Italian entries: the dictionary, and the English glosses the pivot starts from. */
export const italian = [
  { word: 'casa', pos: 'noun', lang_code: 'it', senses: [{ glosses: ['house'] }, { glosses: ['home'] }], forms: [{ form: 'case', tags: ['plural'] }], head_templates: [{ name: 'it-noun', args: { g: 'f' } }] },
  { word: 'banca', pos: 'noun', lang_code: 'it', senses: [{ glosses: ['bank (financial institution)'] }], head_templates: [{ name: 'it-noun', args: { g: 'f' } }] },
  { word: 'riva', pos: 'noun', lang_code: 'it', senses: [{ glosses: ['shore; bank'] }] },
  { word: 'sponda', pos: 'noun', lang_code: 'it', senses: [{ glosses: ['bank (of a river)'] }] },
  { word: 'banco', pos: 'noun', lang_code: 'it', senses: [{ glosses: ['bank'] }] },
  { word: 'ombra', pos: 'noun', lang_code: 'it', senses: [{ glosses: ['shadow'] }, { glosses: ['shade'] }], head_templates: [{ name: 'it-noun', args: { g: 'f' } }] },
  {
    word: 'essere', pos: 'verb', lang_code: 'it', senses: [{ glosses: ['to be'] }],
    forms: [
      { form: 'sono', tags: ['first-person', 'singular', 'present', 'indicative'] },
      { form: 'sono', tags: ['third-person', 'plural', 'present', 'indicative'] },
      { form: 'è', tags: ['third-person', 'singular', 'present', 'indicative'] },
    ],
  },
  { word: 'regalo', pos: 'noun', lang_code: 'it', senses: [{ glosses: ['present, gift'] }] },
]

/** English Wiktionary's Slovene entries. */
export const slovene = [{ word: 'hiša', pos: 'noun', lang_code: 'sl', senses: [{ glosses: ['house'] }], forms: [{ form: 'híše', tags: ['genitive', 'singular'] }], head_templates: [{ name: 'sl-noun', args: { g: 'f' } }] }]

/** German Wiktionary: a German word's table, and a Slovene and an Italian word explained in German. */
export const german = [
  { word: 'Haus', pos: 'noun', lang_code: 'de', senses: [{ glosses: ['Gebäude'], sense_index: '1' }], translations: [t('sl', 'hiša', 'Gebäude', { sense_index: '1' }), t('it', 'casa', 'Gebäude', { sense_index: '1' })] },
  // German Wiktionary explains a foreign word, then translates it
  { word: 'hiša', pos: 'noun', lang_code: 'sl', senses: [{ glosses: ['zum Wohnen dienendes Gebäude; Haus'] }] },
  { word: 'casa', pos: 'noun', lang_code: 'it', senses: [{ glosses: ['das Haus, das Zuhause'] }] },
  { word: 'banca', pos: 'noun', lang_code: 'it', senses: [{ glosses: ['männliche oder weibliche Person, die Geld verwahrt'] }] },
]

export type Built = { dir: string; work: string; it: string; sl: string; log: string }
let built: Built | undefined

/**
 * Runs companion/bin/lexicon-build on the extracts above: it.json(.gz) with its manifest in [dir], sl.json there too;
 * [tempDir] makes the directories (the harness's, removed when the run ends).
 */
export function lexiconFixture(tempDir: (prefix: string) => string): Built {
  if (built) return built
  const src = tempDir('lani-smoke-kaikki-')
  const work = tempDir('lani-smoke-lexicon-work-')
  const dir = tempDir('lani-smoke-lexicon-built-')
  const jsonl = (name: string, rows: object[]) => {
    const p = join(src, name)
    writeFileSync(p, rows.map(r => JSON.stringify(r)).join('\n') + '\n')
    return p
  }
  const files = { english: jsonl('English.jsonl', english), italian: jsonl('Italian.jsonl', italian), slovene: jsonl('Slovene.jsonl', slovene), german: jsonl('de-raw.jsonl', german) }
  mkdirSync(work, { recursive: true })
  const bin = resolve(import.meta.dir, '../../../bin/lexicon-build')
  const log: string[] = []
  const run = (...args: string[]) => {
    const p = Bun.spawnSync(['bun', bin, ...args, '--work', work])
    log.push(`$ lexicon-build ${args.join(' ')}\n${p.stdout.toString()}${p.stderr.toString()}`)
    if (p.exitCode !== 0) throw new Error(`lexicon-build ${args.join(' ')} failed:\n${log.at(-1)}`)
  }
  run('--index', files.english, '--edition', 'en', '--name', 'English')
  run('--index', files.italian, '--edition', 'en', '--name', 'Italian')
  run('--index', files.slovene, '--edition', 'en', '--name', 'Slovene')
  run('--index', files.german, '--edition', 'de', '--name', 'raw')
  run('--from', files.italian, '--lang', 'it', '--out', join(dir, 'it.json'), '--meanings', '--manifest', '--gzip')
  run('--from', files.slovene, '--lang', 'sl', '--out', join(dir, 'sl.json'), '--meanings')
  built = { dir, work, it: join(dir, 'it.json'), sl: join(dir, 'sl.json'), log: log.join('\n') }
  return built
}
