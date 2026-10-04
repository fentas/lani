// Smoke checks: the tutor's notes on a dialog sentence's grammar (publish_sentence_note, <data>/app/sentences.json),
// shown the next time Jan long-presses the sentence (GET /sentence). Runs after the lexicon section.
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { sentenceKey } from '../../src/sentences'
import { auth, base, check, client, dataDir, guideOf } from './harness'

const file = join(dataDir, 'app/sentences.json')
const kept = () => (existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : null)
const sentence = async (s: string | null, o: { language?: string; base?: string } = {}) => {
  const q = new URLSearchParams({ ...(s !== null ? { s } : {}), ...Object.fromEntries(Object.entries(o).filter(([, v]) => v)) as Record<string, string> })
  const r = await fetch(`${base}/sentence?${q}`, { headers: auth })
  return { status: r.status, body: (await r.json()) as any }
}
const publish = (args: Record<string, unknown>) => client.callTool({ name: 'publish_sentence_note', arguments: args })
const backlog = async () => ((await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as { event: any }[]).map(b => b.event)

export default async function sentences() {
  check('a sentence\'s key: NFC, lower case, punctuation and spaces one space', sentenceKey('Dvanajst krav se pase!') === 'dvanajst krav se pase' && sentenceKey('  dvanajst  krav – se pase. ') === 'dvanajst krav se pase' && sentenceKey('Žena je tu.') === 'žena je tu' && sentenceKey('...') === '')

  // --- nothing kept yet ------------------------------------------------------------------------------------------
  const none = await sentence('Dvanajst krav se pase!')
  check('GET /sentence: before the tutor explained it, the sentence and no note', none.status === 200 && none.body.sentence === 'Dvanajst krav se pase!' && none.body.note === null, none)
  const wrong = await Promise.all([sentence(' '), sentence(null), sentence('a'.repeat(401)), sentence('Dober dan.', { language: 'xx' }), sentence('Dober dan.', { base: 'klingon' })])
  check('GET /sentence: 400 for a blank, missing or too long sentence, an unknown language or base', wrong.every(r => r.status === 400), wrong.map(r => r.status))
  check('GET /sentence: the app token only', (await fetch(`${base}/sentence?s=Dober%20dan`)).status === 401)

  // --- publish_sentence_note -------------------------------------------------------------------------------------
  const bad = await Promise.all([
    publish({ sentence: 'Dvanajst krav se pase!', text: ' ' }),
    publish({ text: 'no sentence' }),
    publish({ sentence: '?!', text: 'only punctuation' }),
    publish({ sentence: 'Dvanajst krav se pase!', text: 'x', gloss_lang: 'english' }),
    publish({ sentence: 'Dvanajst krav se pase!', text: 'x', language: 'xx' }),
  ])
  check('publish_sentence_note refuses an empty text, no sentence, only punctuation, a wrong language code: nothing kept', bad.every(r => r.isError) && kept() === null, bad.map(r => (r as any).content?.[0]?.text))
  const text = '**krav**: genitive plural, after *dvanajst* (5 and up take the genitive plural). *se pase*: 3rd person singular, the number takes a singular verb. 📖 stevila'
  const first = await publish({ sentence: 'Dvanajst krav se pase!', text })
  const one = kept()
  check(
    'publish_sentence_note keeps the note in <data>/app/sentences.json: its key, the language and the learner\'s base (en)',
    !first.isError && one?.schema === 'lani.sentences/v0' && one.entries.length === 1 && one.entries[0].key === 'dvanajst krav se pase' && one.entries[0].sentence === 'Dvanajst krav se pase!' && one.entries[0].language === 'sl' && one.entries[0].gloss_lang === 'en' && one.entries[0].text === text && !!one.entries[0].at,
    { first, one },
  )
  const found = await sentence('dvanajst  krav se pase.')
  check('GET /sentence: other case and punctuation find the note, with its language and when', found.status === 200 && found.body.sentence === 'dvanajst  krav se pase.' && found.body.note?.text === text && found.body.note.gloss_lang === 'en' && found.body.note.at === one.entries[0].at, found)
  const heard = (await backlog()).filter(e => e.type === 'sentence_explained')
  check('publish_sentence_note: the app hears sentence_explained, without a language (the village\'s)', heard.length === 1 && heard[0].sentence === 'Dvanajst krav se pase!' && !('language' in heard[0]), heard)

  // the same sentence and language again: replaced
  const newer = 'Dvanajst → **krav** (genitive plural); the verb *se pase* is singular.'
  const again = await publish({ sentence: 'Dvanajst krav se pase.', text: newer, gloss_lang: 'EN' })
  const replaced = kept()
  const shown = await sentence('Dvanajst krav se pase!')
  check('publish_sentence_note again for the same sentence and gloss_lang: replaced, one entry, the new text', !again.isError && replaced.entries.length === 1 && replaced.entries[0].text === newer && shown.body.note?.text === newer, { again, replaced, shown })

  // another gloss language: the asked base's note first, else the newest in any
  const german = 'Der Genitiv Plural *krav* nach *dvanajst*.'
  await publish({ sentence: 'Dvanajst krav se pase!', text: german, gloss_lang: 'de' })
  const inEnglish = await sentence('Dvanajst krav se pase!')
  const inGerman = await sentence('Dvanajst krav se pase!', { base: 'de' })
  const inItalian = await sentence('Dvanajst krav se pase!', { base: 'it' })
  check(
    'GET /sentence: the note in the base asked for (en by default, de), else the newest in another language (it: the German)',
    kept().entries.length === 2 && inEnglish.body.note?.text === newer && inGerman.body.note?.text === german && inGerman.body.note.gloss_lang === 'de' && inItalian.body.note?.text === german && inItalian.body.note.gloss_lang === 'de',
    { inEnglish, inGerman, inItalian },
  )

  // on a visit: the sentence is the town's language, kept apart from the village's
  const visit = await publish({ sentence: 'Il gabbiano vola.', text: '*vola*: 3rd person singular of volare.', language: 'it' })
  const there = await sentence('il gabbiano vola', { language: 'it' })
  const home = await sentence('Il gabbiano vola.')
  const visitHeard = (await backlog()).filter(e => e.type === 'sentence_explained' && e.sentence === 'Il gabbiano vola.')
  check("publish_sentence_note with language: a town's sentence, found with its language only, and the app hears which", !visit.isError && there.body.note?.gloss_lang === 'en' && there.body.note.text.startsWith('*vola*') && home.body.note === null && visitHeard.length === 1 && visitHeard[0].language === 'it', { visit, there, home, visitHeard })

  const guide = await guideOf()
  check('the guide tells the tutor about the sentence grammar: data.about_sentence and publish_sentence_note', guide.includes('data.about_sentence') && guide.includes('publish_sentence_note') && guide.includes('Slovnica stavka'))
}
