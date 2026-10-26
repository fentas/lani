// Smoke checks: a word the dictionary doesn't know, added from its card (POST /words/requests): "Did you mean …?" first
// (GET /lookup's suggestions), then the word waits for the tutor's meaning (a gloss_request note), the tutor's
// publish_gloss adds it to the learner's words, and the app hears word_request_done. Runs after the lexicon section: gozd
// is one of Jan's words there. A visit's word, and a child's (Italian from German), are the friuli section's.
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { WordRequests } from '../../src/word-requests'
import { auth, base, channelEvents, check, client, dataDir, guideOf } from './harness'

const lookup = async (w: string, o: { line?: string; en?: string; language?: string } = {}) => {
  const q = new URLSearchParams({ w, ...Object.fromEntries(Object.entries(o).filter(([, v]) => v)) as Record<string, string> })
  return (await (await fetch(`${base}/lookup?${q}`, { headers: auth })).json()) as any
}
const ask = (body: object) => fetch(`${base}/words/requests`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
const sr = (lang?: string) => JSON.parse(readFileSync(join(dataDir, ...(lang ? ['languages', lang] : []), 'spaced-repetition.json'), 'utf8')).items
const backlog = async () => ((await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as { id: number; event: any }[])
const notes = (since: number) => channelEvents.slice(since).filter(n => n.params?.meta?.kind === 'gloss_request')
const dataOf = (n: any) => JSON.parse(/```json\n([\s\S]*?)\n```/.exec(n.params.content)?.[1] ?? '{}')
const publish = (args: object) => client.callTool({ name: 'publish_gloss', arguments: args }) as Promise<any>
const text = (r: any) => String(r?.content?.[0]?.text ?? '')

export default async function wordRequests() {
  // --- did you mean … -----------------------------------------------------------------------------------------------
  const [zlica, cesnja, gozdk, nothing] = await Promise.all([lookup('zlica'), lookup('cesnja'), lookup('gozdk'), lookup('xyzzyq')])
  check('GET /lookup: not found, the accents a learner left out given back: zlica → žlica (the pack word), alone', zlica.entries.length === 0 && zlica.suggestions?.length === 1 && zlica.suggestions[0].word === 'žlica' && zlica.suggestions[0].lemma === 'žlica' && zlica.suggestions[0].gloss.length > 0 && zlica.suggestions[0].gloss_lang === 'en', zlica)
  check('GET /lookup: two accents at once (cesnja → češnja), and a typo of a dictionary form (gozdk → gozd)', cesnja.suggestions?.[0]?.lemma === 'češnja' && gozdk.suggestions?.[0]?.word === 'gozd' && gozdk.suggestions[0].gloss.includes('forest'), { cesnja, gozdk })
  check('GET /lookup: nothing like it: no suggestions, as before', nothing.entries.length === 0 && nothing.suggestions === undefined && nothing.requested === undefined, nothing)
  const found = await lookup('gozdu')
  check('GET /lookup: a word found has no suggestions', found.entries.length > 0 && found.suggestions === undefined)

  // --- asked for: it waits for the tutor's meaning ---------------------------------------------------------------
  const line = 'Babica prede z vretenom.'
  const en = 'Grandma spins with a spindle.'
  const before = await lookup('vretenom', { line, en })
  check('GET /lookup: vretenom is not in the dictionary, nothing like it, not asked for yet', before.entries.length === 0 && !before.suggestions && !before.requested, before)
  const notesBefore = channelEvents.length
  const body = { client_id: 'request-vretenom-0001', word: 'vretenom', line, en, from: 'scene' }
  const r1 = await ask(body)
  const a1 = await r1.json()
  const again = await ask(body)
  check('POST /words/requests: the word waits for its meaning, the request kept by its client_id', r1.ok && a1.state === 'waiting' && a1.word === 'vretenom' && a1.id === body.client_id && again.headers.get('x-lani-replay') === '1', a1)
  const twice = await (await ask({ client_id: 'request-vretenom-0002', word: 'Vretenom', line })).json()
  check('POST /words/requests: the same word asked again (another tap) waits under the first request', twice.state === 'waiting' && twice.id === body.client_id, twice)
  await Bun.sleep(700)
  const told = notes(notesBefore)
  const d0 = told[0] ? dataOf(told[0]).gloss_requests : undefined
  check(
    'the tutor hears it once, gathered: gloss_request, what to publish, in English, the line in its JSON block',
    told.length === 1 && told[0].params.content.includes('«vretenom»') && told[0].params.content.includes('publish_gloss') && told[0].params.content.includes('gloss_lang: "en"') && told[0].params.content.includes("don't persist it") && d0?.gloss_lang === 'en' && d0.language === 'sl' && d0.words?.[0]?.form === 'vretenom' && d0.words[0].line === line && d0.words[0].en === en && !told[0].params.content.includes('is a child'),
    told.map(n => n.params.content),
  )
  const waiting = await lookup('vretenom', { line })
  check('GET /lookup: a word asked for says so (requested), so a card opened again shows the tutor writing', waiting.entries.length === 0 && waiting.requested === true, waiting)
  const listed = await (await fetch(`${base}/words/requests`, { headers: auth })).json()
  check('GET /words/requests lists the words waiting, with their line', listed.requests?.length === 1 && listed.requests[0].word === 'vretenom' && listed.requests[0].language === 'sl' && listed.requests[0].line === line, listed)
  const file = join(dataDir, 'app/word-requests.json')
  const kept = new WordRequests(file, () => {}).all()
  check('the requests are kept in the learner data (app/word-requests.json), told once: a restart keeps them', existsSync(file) && kept.length === 1 && kept[0].key === 'vretenom' && kept[0].times === 1 && !!kept[0].told, kept)

  // a second word, left waiting: it is told again with the next word asked once LANI_WORD_REQUEST_RETELL_MS passed
  const motovilo = await (await ask({ client_id: 'request-motovilo-01', word: 'motovilo', from: 'search' })).json()
  check('POST /words/requests: a word from the search (no line) waits too', motovilo.state === 'waiting', motovilo)
  const motoviloAt = Date.now()

  // --- the tutor's meaning: the word is added -------------------------------------------------------------------
  const before2 = (await backlog()).at(-1)?.id ?? 0
  const pub = await publish({ form: 'vretenom', lemma: 'vreteno', pos: 'noun', gloss: ['spindle'], grammar: 'instrumental singular' })
  check("publish_gloss: the word asked for is added to Jan's words, and the tutor is told not to persist it", !pub.isError && text(pub).includes("«vretenom» was asked for: added to Jan's words as vreteno (vocab_word_vreteno)") && text(pub).includes("don't persist it"), text(pub))
  const item = sr().vocab_word_vreteno
  check('the item is a vocabulary card from a lookup, the meaning the tutor wrote, the line it was met in', item?.type === 'vocabulary' && item.content === 'vreteno' && item.answer === 'spindle' && item.example === line && item.example_translation === en && item.source === 'lookup' && item.total_reviews === 0, item)
  const after = (await backlog()).filter(b => b.id > before2).map(b => b.event)
  const types = after.map(e => e.type)
  const done = after.find(e => e.type === 'word_request_done')
  check(
    'the app hears words_added, lexicon_updated (its card looks again) and then word_request_done with the meaning',
    types.includes('words_added') && types.indexOf('lexicon_updated') < types.indexOf('word_request_done') && done?.word === 'vretenom' && done.lemma === 'vreteno' && done.gloss?.[0] === 'spindle' && done.item_id === 'vocab_word_vreteno' && done.added === true && done.language === undefined,
    after,
  )
  const filled = await lookup('vretenom', { line })
  check("GET /lookup: now the tutor's entry, flagged machine-written, known, no longer waiting", filled.entries[0]?.lemma === 'vreteno' && filled.entries[0].source === 'tutor' && filled.entries[0].gloss_via === 'claude' && filled.entries[0].known === true && filled.entries[0].item_id === 'vocab_word_vreteno' && filled.requested === undefined, filled)
  const left = await (await fetch(`${base}/words/requests`, { headers: auth })).json()
  check('GET /words/requests: the word is no longer waiting', left.requests.map((r: any) => r.word).join() === 'motovilo', left)
  const twiceMore = await publish({ form: 'vretenom', lemma: 'vreteno', pos: 'noun', gloss: ['spindle', 'distaff spindle'] })
  check('publish_gloss again: nothing asked for any more, nothing added again', !twiceMore.isError && !text(twiceMore).includes('asked for'), text(twiceMore))

  // --- known by the time it arrives (offline): added at once ---------------------------------------------------------
  const before3 = (await backlog()).at(-1)?.id ?? 0
  const metle = await (await ask({ client_id: 'request-metle-000001', word: 'metle', line: 'Kje so metle?', from: 'scene' })).json()
  const metleDone = (await backlog()).filter(b => b.id > before3).map(b => b.event).find(e => e.type === 'word_request_done')
  check('POST /words/requests: a word the dictionary knows by now is added at once (metle: metla), the app told', metle.state === 'added' && metle.lemma === 'metla' && metle.item_id === 'vocab_word_metla' && metle.added === true && sr().vocab_word_metla?.answer.includes('broom') && sr().vocab_word_metla.example === 'Kje so metle?' && metleDone?.item_id === 'vocab_word_metla', { metle, metleDone })
  const gozd = await (await ask({ client_id: 'request-gozdu-00001', word: 'gozdu' })).json()
  check('POST /words/requests: one of theirs already is not added again', gozd.state === 'added' && gozd.item_id === 'vocab_word_gozd' && gozd.added === false, gozd)

  // --- a name is not added; what is refused -----------------------------------------------------------------------
  await ask({ client_id: 'request-zalka-00001', word: 'Zalka', line: 'Zalka prede.' })
  const name = await publish({ form: 'Zalka', lemma: 'Zalka', pos: 'name', gloss: ['Zalka (a girl\'s name)'] })
  check('publish_gloss: a name asked for is not added as a word, and no longer waits', text(name).includes('a name, not added') && !sr().vocab_word_zalka && !(await lookup('Zalka')).requested, text(name))
  const refused = await Promise.all([
    ask({ word: '' }),
    ask({ word: 'a'.repeat(41) }),
    ask({ word: '<b>x</b>' }),
    ask({ word: 'hiša', language: 'xx' }),
    ask({ word: 'hiša', base: 'sl' }),
    ask({ word: 'hiša', from: 'nowhere' }),
  ])
  check('POST /words/requests: 400 for no word, a word too long, not a word, an unknown language, a base the same as the word\'s, an unknown origin', refused.every(r => r.status === 400), refused.map(r => r.status))
  check('POST /words/requests: the app token only', (await fetch(`${base}/words/requests`, { method: 'POST', body: '{}' })).status === 401)

  // --- a gloss in another language than the one asked for: the word waits on -----------------------------------------
  await ask({ client_id: 'request-preslica-01', word: 'preslica', base: 'de', line: 'Babica ima preslico.' })
  const english = await publish({ form: 'preslica', lemma: 'preslica', pos: 'noun', gloss: ['distaff'], gloss_lang: 'en' })
  check('publish_gloss in English for a word asked for in German: not added, the tutor told which language it waits for', text(english).includes('«preslica» waits for a meaning in German (gloss_lang "de")') && !sr().vocab_word_preslica, text(english))

  // --- told again: still waiting after LANI_WORD_REQUEST_RETELL_MS (4 s here), with the next word asked ----------------
  await Bun.sleep(Math.max(0, 4800 - (Date.now() - motoviloAt)))
  const retold = channelEvents.length
  await ask({ client_id: 'request-snovalnik', word: 'snovalnik' })
  await Bun.sleep(700)
  const next = notes(retold)
  const retoldWords = next.flatMap(n => dataOf(n).gloss_requests?.words?.map((w: any) => w.form) ?? [])
  check('a word told before and still waiting is told again with the next one asked, said so', next.length >= 1 && retoldWords.includes('snovalnik') && retoldWords.includes('motovilo') && next.some(n => n.params.content.includes('asked before: still waiting')), next.map(n => n.params.content))
  // the rest explained, so nothing waits after this section
  for (const [form, args] of [['motovilo', { gloss: ['reel'] }], ['snovalnik', { gloss: ['warper'] }], ['preslica', { gloss: ['Spinnrocken'], gloss_lang: 'de' }]] as const)
    await publish({ form, lemma: form, pos: 'noun', ...args })
  const empty = await (await fetch(`${base}/words/requests`, { headers: auth })).json()
  check('every word asked for explained: none waits', empty.requests.length === 0, empty)
  const guide = await guideOf()
  check('the guide tells the tutor about gloss_request: publish_gloss, the bridge adds it, no reply', guide.includes('gloss_request') && guide.includes("adds the word to Jan's words itself"), guide.slice(guide.indexOf('gloss_request') - 10, guide.indexOf('gloss_request') + 600))
}
