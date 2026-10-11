// Smoke checks: flashcard reviews persisted via update-db.py, and the answers typed in them (answers.ts).
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { answerLine, AnswerLog, reviewAnswers, type Answer } from '../../src/answers'
import { auth, base, channelEvents, check, client, dataDir, tempDir } from './harness'

const answersFile = join(dataDir, 'app/answers.json')
const logged = (): Answer[] => (existsSync(answersFile) ? JSON.parse(readFileSync(answersFile, 'utf8')).answers : [])
const tool = async (args: object) => {
  const r: any = await client.callTool({ name: 'recent_answers', arguments: args })
  return { error: !!r?.isError, text: String(r?.content?.[0]?.text ?? '') }
}

export default async function reviews() {
  const rv = await fetch(`${base}/reviews`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ results: [{ item_id: 'vocab_hvala', quality: 5 }, { item_id: 'vocab_zivjo', quality: 2 }], duration_minutes: 3 }),
  })
  const rvBody = await rv.json()
  check('POST /reviews persists via update-db.py', rv.ok && rvBody.correct === 1 && rvBody.total === 2, rvBody)
  await Bun.sleep(100)
  check('review_done reaches Claude', channelEvents.some(n => n.params?.meta?.kind === 'review_done'))
  check('an app from before sends no answers: none logged', logged().length === 0, logged())
  check('recent_answers with nothing logged says so, and that an app from before sends none', (await tool({ card_id: 'vocab_hvala' })).text.includes('No answers for vocab_hvala'))

  // --- what was typed: each card's answer, logged once the review is persisted --------------------------------------
  const r2 = await fetch(`${base}/reviews`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({
      results: [{ item_id: 'vocab_hvala', quality: 1 }, { item_id: 'vocab_zivjo', quality: 4 }],
      duration_minutes: 2,
      answers: [
        { item_id: 'vocab_hvala', variant: 'type', type: 'translate', prompt: 'thank you', answer: 'hvla', expected: 'hvala', verdict: 'wrong' },
        { item_id: 'vocab_zivjo', variant: 'tiles', type: 'reorder', prompt: 'hi', answer: 'živjo', expected: 'živjo', verdict: 'correct', hints_used: 1 },
        { verdict: 'nonsense' },
      ],
    }),
  })
  const b2 = await r2.json()
  const after = logged()
  const hvala = after.find(a => a.item_id === 'vocab_hvala')
  check('a review with its answers: 200; each well-formed answer logged with the quality it gave the card and the session, a bad one left out (never the review)',
    r2.ok && after.length === 2 && hvala?.kind === 'review' && hvala.answer === 'hvla' && hvala.expected === 'hvala' && hvala.verdict === 'wrong' && hvala.quality === 1 && hvala.variant === 'type' &&
      hvala.session_id === b2.session_id && after.find(a => a.item_id === 'vocab_zivjo')?.hints_used === 1 && !!Date.parse(hvala.at), [b2, after])
  await Bun.sleep(100)
  const note = String(channelEvents.filter(n => n.params?.meta?.kind === 'review_done').at(-1)?.params?.content ?? '')
  check('the tutor\'s note names the answers typed wrong', note.includes('Answered wrong: vocab_hvala "hvla" for "hvala"') && !note.includes('"živjo" for'), note)
  const byCard = await tool({ card_id: 'vocab_hvala' })
  const byText = await tool({ text: 'ŽIVJO' })
  const wrong = await tool({ wrong_only: true })
  check('recent_answers: a card\'s answers (what was typed, what was expected, the verdict, the quality, what was asked), a word searched (any case), only the wrong ones',
    !byCard.error && byCard.text.includes('review vocab_hvala (type, translate) "hvla" for "hvala": wrong; quality 1; asked "thank you"') &&
      byText.text.includes('vocab_zivjo') && !byText.text.includes('vocab_hvala') && wrong.text.includes('vocab_hvala') && !wrong.text.includes('vocab_zivjo'), [byCard, byText, wrong])
  check('recent_answers refuses a bad argument', (await tool({ limit: 0 })).error)

  // --- the log itself: the days kept, the most kept, what a bad answer is ---------------------------------------
  let now = new Date('2026-10-01T10:00:00Z')
  const file = join(tempDir('lani-answers-'), 'answers.json')
  const log = new AnswerLog(file, () => now, { days: 7, max: 3 })
  log.add([{ kind: 'review', item_id: 'a' }, { kind: 'review', item_id: 'b' }])
  now = new Date('2026-10-09T10:00:00Z')
  log.add([{ kind: 'review', item_id: 'c' }])
  const aged = log.all().map(a => a.item_id).join()
  log.add([1, 2, 3].map(i => ({ kind: 'form' as const, item_id: `f${i}` })))
  check('the log keeps the last days (older answers dropped) and at most its most (the oldest go first); newest first when asked',
    aged === 'c' && log.all().map(a => a.item_id).join() === 'f1,f2,f3' && log.find().map(a => a.item_id).join() === 'f3,f2,f1' && log.find({ limit: 1 }).length === 1, log.all())
  const long = 'x'.repeat(390)
  const parsed = reviewAnswers([{ item_id: 'k', answer: `  ${long}  `, verdict: 'almost' }, { item_id: '' }, 'nope', { item_id: 'm', verdict: 'maybe' }])
  check('a review\'s answers: the well-formed ones, their text trimmed to 200 characters; not a list: none',
    parsed.length === 1 && parsed[0].answer?.length === 200 && reviewAnswers({ item_id: 'k' }).length === 0 && reviewAnswers(undefined).length === 0, parsed)
  check('an answer as one line: no answer, heard, a hint',
    answerLine({ at: '2026-10-11T09:14:00Z', kind: 'review', item_id: 'vocab_kurim', answer: '', expected: 'kurim', verdict: 'wrong' }) === '2026-10-11 09:14 review vocab_kurim (no answer) for "kurim": wrong' &&
      answerLine({ at: '2026-10-11T09:14:00Z', kind: 'review', item_id: 'v', answer: 'kurim', spoken: true, verdict: 'almost', hint: 'ending' }).includes('"kurim" (heard): almost, ending'))
}
