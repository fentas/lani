// Smoke checks: a language profile per language (docs/DB_SCRIPTS.md, "Languages"). The routes that write learning
// data take an optional language: another language's words, reviews and packs go to <data>/languages/<code>/, never
// into the home language's databases; GET /state sums up every language and serves one with ?language=.
import { existsSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { initLearnerData } from '../../src/learners'
import { auth, base, channelEvents, check, dataDir, tempDir } from './harness'

const db = (rel: string) => JSON.parse(readFileSync(join(dataDir, rel), 'utf8'))
const post = (path: string, body: object) => fetch(`${base}${path}`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
const state = async (q = '') => (await fetch(`${base}/state${q}`, { headers: auth })).json()

export default async function languages() {
  const homeSessions = db('session-log.json').sessions.length
  const home0 = await state()
  check('GET /state sums up the languages: the home one first', Array.isArray(home0.languages) && home0.languages[0]?.source === 'home' && home0.languages.length === 1, home0.languages)

  // a word met on a visit: an Italian review item, not a Slovene one
  const w1 = await (await post('/words', { sl: 'ciao', en: 'hi', from: 'villager', language: 'it' })).json()
  const itSr = () => db('languages/it/spaced-repetition.json').items
  check('POST /words with a language adds the word to that language', w1.added === true && w1.item_id === 'vocab_word_ciao' && itSr().vocab_word_ciao?.content === 'ciao', w1)
  check('… and not to the home language', !db('spaced-repetition.json').items.vocab_word_ciao)
  const w2 = await (await post('/words', { sl: 'Ciao', en: 'hi', language: 'it' })).json()
  check('… where it is known the next time', w2.added === false && w2.item_id === 'vocab_word_ciao', w2)
  check('… and the home profile lists the language', db('learner-profile.json').languages?.some((l: any) => l.code === 'it' && l.source === 'visits'), db('learner-profile.json').languages)

  // a review of the Italian deck
  const rv = await post('/reviews', { results: [{ item_id: 'vocab_word_ciao', quality: 4 }], duration_minutes: 2, language: 'it' })
  const rvBody = await rv.json()
  check('POST /reviews with a language persists it in that language', rv.ok && rvBody.language === 'it' && /^session-it-\d{3}$/.test(rvBody.session_id) && itSr().vocab_word_ciao.total_reviews === 1, rvBody)
  check('… with a session of its own, the home log untouched', db('languages/it/session-log.json').sessions.length === 1 && db('session-log.json').sessions.length === homeSessions)
  await Bun.sleep(100)
  check('… and Claude hears which language', channelEvents.some(n => n.params?.meta?.kind === 'review_done' && n.params.content.includes('Italian')))

  // pack words learned in another language
  const learn = await (await post('/packs/smoke-kitchen/learn', { results: [{ word_id: 'kruh', quality: 4 }, { word_id: 'kroznik', quality: 3 }], duration_minutes: 3, language: 'it' })).json()
  check('POST /packs/:id/learn with a language: the words are new there, and go there', learn.added?.length === 2 && !!itSr()['vocab_smoke-kitchen_kroznik'] && learn.session_id?.startsWith('session-it-'), learn)

  // the state: every language, and one in full
  const all = await state()
  const it = all.languages.find((l: any) => l.code === 'it')
  check('GET /state lists the other language: level, words, due, from visits', it?.level === 'A1' && it.words === 3 && typeof it.due === 'number' && it.source === 'visits' && all.language === home0.language, all.languages)
  check('… and serves the home language as before', JSON.stringify(Object.keys(all.databases).sort()) === JSON.stringify(Object.keys(home0.databases).sort()) && !all.databases.spaced_repetition.items.vocab_word_ciao)
  const one = await state('?language=it')
  check('GET /state?language=it: that language in full, the same shape', one.language === 'it' && !!one.databases.spaced_repetition.items.vocab_word_ciao && !one.databases.spaced_repetition.items.vocab_hvala && one.computed.next_session_id === 'session-it-003', one.computed)
  const home1 = await state('?language=sl')
  check('… the village language is the home language', home1.computed.next_session_id === all.computed.next_session_id && !home1.databases.spaced_repetition.items.vocab_word_ciao)
  const bad = await Promise.all([
    fetch(`${base}/state?language=Italian`, { headers: auth }),
    post('/reviews', { results: [{ item_id: 'x', quality: 3 }], duration_minutes: 1, language: 'Italian' }),
    post('/words', { sl: 'x', en: 'y', language: 'i t' }),
    post('/packs/smoke-kitchen/learn', { results: [{ word_id: 'sir', quality: 3 }], duration_minutes: 1, language: '1t' }),
  ])
  check('a language that is not a code: 400', bad.every(r => r.status === 400), bad.map(r => r.status))
  check('… and nothing written for it', !existsSync(join(dataDir, 'languages/italian')))

  // a new learner starts in their town's language, with no others yet
  const fresh = tempDir('lani-smoke-newlearner-')
  initLearnerData(resolve(import.meta.dir, '../../../..'), fresh, { name: 'Ana', target: 'it', base: 'sl' })
  const lp = JSON.parse(readFileSync(join(fresh, 'learner-profile.json'), 'utf8'))
  check("a new learner's profile names the home language and no others", lp.home_language === 'it' && Array.isArray(lp.languages) && lp.languages.length === 0, { home: lp.home_language, languages: lp.languages })
}
