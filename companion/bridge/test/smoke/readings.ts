// Smoke checks: the reading corner (readings.ts, features/readings.ts): the culture pack's readings served, the tutor's
// tools (publish_reading validated like the pack's readings, list, get, remove), and reading practice reported by the
// app (POST /readings/done: update-db.py, reading and, read aloud, speaking; idempotent by client_id).
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { QUESTION_TYPES, readingDone, readingLength, readingNote, readingPayload, readingSpec } from '../../src/readings'
import { auth, base, channelEvents, check, client, dataDir, guideOf } from './harness'

/** A tool result's text. */
const text = (r: any) => (r.content ?? []).map((c: any) => c.text ?? '').join('\n')
const t = (sl: string, en: string) => ({ sl, en })

/** An A1 page with one question of every type and two new words. */
const reading = {
  id: 'smoke-jutro',
  kind: 'page',
  level: 'A1',
  title: t('Jutro v vasi', 'Morning in the village'),
  by: 'micka',
  lines: [
    { text: t('Zjutraj Micka odpre okno.', 'In the morning Micka opens the window.') },
    { text: t('Sonce že sije na hrib.', 'The sun is already shining on the hill.') },
    { text: t('Micka skuha kavo in speče kruh.', 'Micka makes coffee and bakes bread.') },
  ],
  questions: [
    { type: 'main_idea', ask: t('O čem govori besedilo?', 'What is the text about?'), options: ['O Mickinem jutru', 'O zimi', 'O šoli'], explain: t('Micka zjutraj odpre okno, skuha kavo in speče kruh.', 'In the morning Micka opens the window, makes coffee and bakes bread.') },
    { type: 'detail', ask: t('Kaj Micka speče?', 'What does Micka bake?'), options: ['Kruh', 'Potico', 'Pico'], explain: t('»Micka skuha kavo in speče kruh.«', '»Micka makes coffee and bakes bread.«') },
    { type: 'word', word: 'sije', ask: t('Kaj pomeni »sije«?', 'What does »sije« mean?'), options: ['Sveti', 'Dežuje', 'Spi'], explain: t('Sonce sije: sonce sveti.', 'The sun shines.') },
    { type: 'true_false', ask: t('Micka zjutraj zapre okno.', 'Micka closes the window in the morning.'), answer: false, explain: t('Micka okno odpre, ne zapre.', 'Micka opens the window, she doesn\'t close it.') },
  ],
  words: [
    { word: 'odpreti', form: 'odpre', pos: 'verb', means: { en: 'to open' } },
    { word: 'sijati', form: 'sije', pos: 'verb', means: { en: 'to shine' } },
  ],
  topics: ['morning', 'home'],
}

export default async function readings() {
  // --- the format --------------------------------------------------------------------------------------------------
  const v = readingSpec.safeParse(reading)
  check('the reading format: question types, a true/false statement without options, new words', v.success && QUESTION_TYPES.every(q => q === 'inference' || reading.questions.some(x => x.type === q)), v.success ? '' : v.error)
  check('… its length in words (the lines, not the title)', v.success && readingLength(v.data, 'sl') === 15, v.success && readingLength(v.data, 'sl'))
  const tfWithOptions = readingSpec.safeParse({ ...reading, questions: [{ ...reading.questions[3], options: ['Drži', 'Ne drži', 'Ne vem'] }] })
  check('… a true/false question has no options', !tfWithOptions.success)
  const plain = readingSpec.safeParse({ ...reading, questions: [{ ask: reading.questions[1].ask, options: ['Kruh', 'Potico', 'Pico'], explain: reading.questions[1].explain }] })
  check("… a question without a type is a plain one, as the first readings' are", plain.success)
  const payload = readingPayload(readingDone.parse({ kind: 'aloud', id: 'x', exercises: 4, correct: 3, duration_minutes: 2, aloud: { words: 30, right: 27, misread: 2, skipped: 1, wpm: 64 } }), { session_id: 'S-1', date: '2026-09-27' })
  check('a reading aloud reports as reading and speaking, its sentences read well as correct', JSON.stringify(payload.skills_practiced) === '["reading","speaking"]' && payload.skill_scores.reading.correct === 3 && payload.skill_scores.speaking.exercises === 4 && payload.command_used === '/lani-app-read-aloud', payload)
  const looked = readingNote(readingDone.parse({ kind: 'reading', id: 'x', exercises: 2, correct: 2, duration_minutes: 1, looks: 3 }))
  check('a reading whose text was looked at again while answering says how often', looked.includes('looked back at the text 3 times while answering'), looked)
  check('… and one without says nothing of it', !readingNote(readingDone.parse({ kind: 'reading', id: 'x', exercises: 2, correct: 2, duration_minutes: 1 })).includes('looked back'))

  // --- the culture pack's readings, served ---------------------------------------------------------------------------
  const list = (await (await fetch(`${base}/readings`, { headers: auth })).json()) as any[]
  const curated = list.filter(r => r.source === 'curated')
  check("GET /readings: primorska's readings, the culture pack's", curated.length >= 6 && ['frtalja', 'potica', 'zdravljica'].every(id => curated.some(r => r.id === id && r.culture === 'primorska')), list.map(r => r.id))
  const potica = await fetch(`${base}/readings/potica`, { headers: auth })
  check('GET /readings/:id: one reading', potica.ok && (await potica.json()).kind === 'recipe')
  check('… and 404 for none', (await fetch(`${base}/readings/no-such-reading`, { headers: auth })).status === 404)

  // --- the tutor's -------------------------------------------------------------------------------------------------
  const rejects: [string, unknown, string][] = [
    ['a curated id', { ...reading, id: 'potica' }, 'culture pack'],
    ["a word question whose word isn't in the text", { ...reading, questions: [{ ...reading.questions[2], word: 'dežuje' }] }, "isn't in the text"],
    ['a true/false question without its answer', { ...reading, questions: [{ type: 'true_false', ask: reading.questions[3].ask, explain: reading.questions[3].explain }] }, 'answer'],
    ['a line without its translation', { ...reading, lines: [{ text: { sl: 'Zjutraj Micka odpre okno.' } }, ...reading.lines.slice(1)] }, 'no "en" text'],
    ['a title without the learner\'s base', { ...reading, title: { sl: 'Jutro', de: 'Morgen' }, lines: reading.lines.map(l => ({ text: { sl: l.text.sl, de: 'x' } })), questions: [{ ...reading.questions[1], ask: { sl: 'Kaj?', de: 'Was?' }, explain: { sl: 'Kruh.', de: 'Brot.' } }], words: [] }, 'no "en" text'],
    ['a reader not in the cast', { ...reading, by: 'nobody' }, 'no villager "nobody"'],
    ['a key the format lacks', { ...reading, author: 'me' }, 'author'],
    ['six questions', { ...reading, questions: [...reading.questions, ...reading.questions.slice(0, 2)] }, 'questions'],
  ]
  for (const [label, r, mention] of rejects) {
    const res = await client.callTool({ name: 'publish_reading', arguments: { reading: r } })
    check(`publish_reading refuses ${label}`, res.isError && text(res).includes(mention), text(res))
  }
  check('… and nothing was published', !existsSync(join(dataDir, 'app/readings/potica.json')) && !existsSync(join(dataDir, 'app/readings/smoke-jutro.json')))
  const pub = await client.callTool({ name: 'publish_reading', arguments: { reading, note: 'Micka wrote you something to read' } })
  check('publish_reading publishes', !pub.isError && text(pub).includes('published reading smoke-jutro (A1, 15 words, 4 questions, 2 new words)'), text(pub))
  const saved = JSON.parse(readFileSync(join(dataDir, 'app/readings/smoke-jutro.json'), 'utf8'))
  check('… to <data>/app/readings, flagged machine-written', saved.machine_written === true && saved.id === 'smoke-jutro')
  await Bun.sleep(50)
  const ev = (await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as any[]
  check('reading_published event, with the title in the learner\'s pair', ev.some(b => b.event.type === 'reading_published' && b.event.id === 'smoke-jutro' && b.event.title === 'Jutro v vasi · Morning in the village' && b.event.level === 'A1' && b.event.note === 'Micka wrote you something to read'))
  const after = (await (await fetch(`${base}/readings`, { headers: auth })).json()) as any[]
  const mine = after.find(r => r.id === 'smoke-jutro')
  check("GET /readings: the tutor's after the culture pack's", after.at(-1)?.id === 'smoke-jutro' && mine.source === 'tutor' && typeof mine.published_at === 'number' && mine.questions[3].answer === false, after.map(r => r.id))
  const listed = JSON.parse(text(await client.callTool({ name: 'list_readings', arguments: {} })))
  const row = listed.readings.find((r: any) => r.id === 'smoke-jutro')
  check('list_readings: level, length, question types, whether it was read', row?.level === 'A1' && row.words === 15 && row.questions.join() === 'main_idea,detail,word,true_false' && row.read === false && typeof listed.unread_at_level === 'number', row)
  const got = JSON.parse(text(await client.callTool({ name: 'get_reading', arguments: { id: 'smoke-jutro' } })))
  check('get_reading: the reading as it was published, to change and publish again', got.id === 'smoke-jutro' && got.source === undefined && readingSpec.safeParse(got).success)

  // --- reading practice, reported by the app -----------------------------------------------------------------------
  const mastery = () => JSON.parse(readFileSync(join(dataDir, 'mastery-db.json'), 'utf8')).skills
  const before = mastery()
  const done = (body: object) => fetch(`${base}/readings/done`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
  const r1 = await done({ kind: 'reading', id: 'smoke-jutro', title: 'Jutro v vasi', level: 'A1', exercises: 4, correct: 3, duration_minutes: 3, questions: [{ type: 'main_idea', right: true }, { type: 'detail', right: true }, { type: 'word', right: false }, { type: 'true_false', right: true }], client_id: 'smoke-read-0001' })
  const b1 = await r1.json()
  const m1 = mastery()
  check('POST /readings/done: a reading counts as reading practice (update-db.py)', r1.ok && JSON.stringify(b1.skills) === '["reading"]' && m1.reading.practice_count === (before.reading.practice_count ?? 0) + 4 && m1.reading.total_practice_time === before.reading.total_practice_time + 3 && m1.reading.last_practiced, { b1, reading: m1.reading })
  const replay = await done({ kind: 'reading', id: 'smoke-jutro', exercises: 4, correct: 3, duration_minutes: 3, client_id: 'smoke-read-0001' })
  const again = await replay.json()
  check('… once per client_id: a repeat gets the first answer, nothing counted twice', replay.headers.get('x-lani-replay') === '1' && again.session_id === b1.session_id && mastery().reading.practice_count === m1.reading.practice_count)
  await Bun.sleep(100)
  const note = channelEvents.find(n => n.params?.meta?.kind === 'reading_done')
  check('reading_done reaches Claude, already persisted, the questions by type', !!note && note.params.content.includes('3/4 questions right') && note.params.content.includes('word ✗') && note.params.content.includes('Already persisted'), note?.params?.content)
  const r2 = await done({ kind: 'aloud', id: 'smoke-jutro', title: 'Jutro v vasi', exercises: 3, correct: 2, duration_minutes: 2, aloud: { words: 17, right: 15, misread: 1, skipped: 1, wpm: 58, recognizer: 'node', misses: [{ word: 'sije', heard: 'sja' }, { word: 'že' }] }, client_id: 'smoke-read-0002' })
  const m2 = mastery()
  check('… a reading aloud counts as reading and speaking', r2.ok && m2.reading.practice_count === m1.reading.practice_count + 3 && m2.speaking.practice_count === (m1.speaking.practice_count ?? 0) + 3 && m2.speaking.total_practice_time === m1.speaking.total_practice_time + 2, { reading: m2.reading, speaking: m2.speaking })
  const r3 = await done({ kind: 'story', id: 'zlatorog', title: 'Zlatorog', level: 'A1', exercises: 3, correct: 3, duration_minutes: 4 })
  check('… a story heard to its end, its turns', r3.ok && mastery().reading.practice_count === m2.reading.practice_count + 3)
  const log = readFileSync(join(dataDir, 'session-log.json'), 'utf8')
  check('… each a session of its own in the log', ['/lani-app-reading', '/lani-app-read-aloud', '/lani-app-story'].every(c => log.includes(c)))
  const bad = await done({ kind: 'reading', id: 'x', exercises: 2, correct: 3, duration_minutes: 1 })
  check('… more right than asked is refused', bad.status === 400)
  const odd = await done({ kind: 'listening', id: 'x', exercises: 1, correct: 1, duration_minutes: 1 })
  check('… and a kind it does not know', odd.status === 400)

  // --- removing, and the guide -----------------------------------------------------------------------------------
  const rm = await client.callTool({ name: 'remove_reading', arguments: { id: 'smoke-jutro' } })
  const gone = (await (await fetch(`${base}/readings`, { headers: auth })).json()) as any[]
  check("remove_reading takes the tutor's reading away", !rm.isError && !gone.some(r => r.id === 'smoke-jutro'))
  check("… and refuses the culture pack's", (await client.callTool({ name: 'remove_reading', arguments: { id: 'potica' } })).isError === true)
  const guide = await guideOf()
  check('the channel guide says when to write a reading, and that reading_done is persisted', guide.includes('publish_reading') && guide.includes('reading_done') && guide.includes('ALREADY'))
}
