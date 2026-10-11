// Smoke checks: flashcard reviews persisted via update-db.py, and the answers typed in them (answers.ts).
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { answerLine, AnswerLog, fold, reviewAnswers, type Answer } from '../../src/answers'
import { answersOf } from '../../src/features/reviews'
import { auth, base, channelEvents, check, client, dataDir, tempDir } from './harness'

const answersFile = join(dataDir, 'app/answers.json')
const logged = (): Answer[] => (existsSync(answersFile) ? JSON.parse(readFileSync(answersFile, 'utf8')).answers : [])
const tool = async (args: object) => {
  const r: any = await client.callTool({ name: 'recent_answers', arguments: args })
  return { error: !!r?.isError, text: String(r?.content?.[0]?.text ?? '') }
}
const post = (body: object) => fetch(`${base}/reviews`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
const lastNote = () => String(channelEvents.filter(n => n.params?.meta?.kind === 'review_done').at(-1)?.params?.content ?? '')
const hoursAgo = (h: number) => new Date(Date.now() - h * 3_600_000).toISOString()

export default async function reviews() {
  const rv = await post({ results: [{ item_id: 'vocab_hvala', quality: 5 }, { item_id: 'vocab_zivjo', quality: 2 }], duration_minutes: 3 })
  const rvBody = await rv.json()
  check('POST /reviews persists via update-db.py', rv.ok && rvBody.correct === 1 && rvBody.total === 2, rvBody)
  await Bun.sleep(100)
  check('review_done reaches Claude', channelEvents.some(n => n.params?.meta?.kind === 'review_done'))
  const before = logged()
  const old = await tool({ card_id: 'vocab_hvala' })
  check('an app from before (no answers, no from): each card logged with its quality, and the tutor told that app sent no answer',
    before.length === 2 && before[0].quality === 5 && before[0].answer === undefined && before[0].from === undefined && before[0].session_id === rvBody.session_id &&
      old.text.includes('review vocab_hvala: quality 5; no answer sent (an app from before'), [before, old])
  check('recent_answers with nothing found says what the log holds', (await tool({ card_id: 'vocab_nikoli' })).text.includes('No answers for vocab_nikoli in the last 7 days. The log holds 2 answer(s) since'))

  // --- what was typed: each card's answer, logged once the review is persisted --------------------------------------
  const answeredAt = hoursAgo(1)
  const r2 = await post({
    results: [{ item_id: 'vocab_hvala', quality: 1 }, { item_id: 'vocab_zivjo', quality: 2 }, { item_id: 'vocab_word_sesti', quality: 1 }, { item_id: 'vocab_kartica', quality: 4 }, { item_id: 'vocab_spoon', quality: 3 }],
    duration_minutes: 2,
    from: 'review',
    answered_at: answeredAt,
    answers: [
      { item_id: 'vocab_hvala', variant: 'type', type: 'translate', prompt: 'thank you', answer: 'hvla', expected: 'hvala', verdict: 'wrong' },
      // right, but two hints lowered its quality to 2: not wrong
      { item_id: 'vocab_zivjo', variant: 'tiles', type: 'reorder', prompt: 'hi', answer: 'živjo', expected: 'živjo', verdict: 'correct', hints_used: 2 },
      { item_id: 'vocab_word_sesti', variant: 'form_type', type: 'cloze', prompt: 'Jaz ___ na klop.', answer: 'sedi', expected: 'sedem', verdict: 'wrong' },
      { item_id: 'vocab_kartica', variant: 'flip', type: 'flashcard', prompt: 'kartica = card', verdict: 'correct' },
      { item_id: 'vocab_spoon', variant: 'type', type: 'translate', prompt: 'a little spoon', answer: 'zlicko', expected: 'žličko', verdict: 'almost', hint: 'diacritics' },
      { verdict: 'nonsense' },
    ],
    forms: [{ item_id: 'vocab_word_sesti', key: 'pres.1sg', page: 'glagoli-sedanjik', right: false, answer: 'Jaz sedi na klop.', expected: 'Jaz sedem na klop.' }],
  })
  const b2 = await r2.json()
  const of2 = logged().filter(a => a.session_id === b2.session_id)
  const hvala = of2.find(a => a.item_id === 'vocab_hvala')
  const sesti = of2.filter(a => a.item_id === 'vocab_word_sesti')
  check('a review with its answers: 200; each card once, with its answer, the quality it gave the card, where and the session, a bad answer left out (never the review)',
    r2.ok && of2.length === 5 && hvala?.kind === 'review' && hvala.answer === 'hvla' && hvala.expected === 'hvala' && hvala.verdict === 'wrong' && hvala.quality === 1 && hvala.variant === 'type' &&
      hvala.from === 'review' && of2.find(a => a.item_id === 'vocab_zivjo')?.hints_used === 2, [b2, of2])
  check('… at the time the app says it was answered, not when it arrived', of2.every(a => a.at === answeredAt), of2.map(a => a.at))
  check('… a form asked joins its card\'s answer once: what was typed, its slot and grammar page',
    sesti.length === 1 && sesti[0].kind === 'review' && sesti[0].answer === 'sedi' && sesti[0].form === 'pres.1sg' && sesti[0].page === 'glagoli-sedanjik' && sesti[0].quality === 1, sesti)
  await Bun.sleep(100)
  const note = lastNote()
  check('the tutor\'s note names the answers typed wrong, and a form slip once (with the forms, its page)',
    note.includes('Answered wrong: vocab_hvala "hvla" for "hvala"; vocab_spoon "zlicko" for "žličko" (almost) (recent_answers') && !note.includes('"živjo" for') &&
      !/Answered wrong:[^.]*sesti/.test(note) && note.includes('Forms of words asked: 1, 1 wrong: "Jaz sedi na klop." for "Jaz sedem na klop." (glagoli-sedanjik, pres.1sg'), note)
  const byCard = await tool({ card_id: 'vocab_hvala' })
  const accented = await tool({ text: 'žličko' })
  const plain = await tool({ text: 'ZLICKO' })
  const wrong = await tool({ wrong_only: true })
  const flip = await tool({ card_id: 'vocab_kartica' })
  check('recent_answers: a card\'s answers (what was typed, what was expected, the verdict, the quality, what was asked, where)',
    !byCard.error && byCard.text.includes('review vocab_hvala (type, translate) "hvla" for "hvala": wrong; quality 1; asked "thank you"; in the review; session') &&
      flip.text.includes('nothing typed (a flashcard, self-rated)'), [byCard, flip])
  check('recent_answers: a word searched in any case, with or without diacritics, on either side',
    accented.text.includes('vocab_spoon') && plain.text.includes('vocab_spoon') && !plain.text.includes('vocab_hvala') && fold('Đurđa Straße perché Ćevapčići ÄÖÜ') === 'durda strasse perche cevapcici aou', [accented, plain])
  check('recent_answers: only the wrong ones by the verdict (almost too); a right answer whose hints lowered its quality isn\'t one; an app from before\'s by its quality',
    ['vocab_hvala', 'vocab_spoon', 'vocab_word_sesti'].every(id => wrong.text.includes(`review ${id} `)) && !wrong.text.includes('vocab_kartica') &&
      !wrong.text.includes('review vocab_zivjo (tiles') && wrong.text.includes('review vocab_zivjo: quality 2; no answer sent'), wrong)
  check('recent_answers refuses a bad argument', (await tool({ limit: 0 })).error)

  // --- the car: ✓/✗ pressed, nothing typed; each rating at its own time ------------------------------------------------
  const r3 = await post({
    results: [{ item_id: 'vocab_car_a', quality: 1 }, { item_id: 'vocab_car_b', quality: 4 }],
    duration_minutes: 20,
    from: 'car',
    answered_at: hoursAgo(1),
    answers: [{ item_id: 'vocab_car_a', type: 'rated', verdict: 'wrong', at: hoursAgo(2) }, { item_id: 'vocab_car_b', type: 'rated', verdict: 'correct', at: new Date(Date.now() + 86_400_000).toISOString() }],
  })
  const b3 = await r3.json()
  const of3 = logged().filter(a => a.session_id === b3.session_id)
  await Bun.sleep(100)
  const car = await tool({ card_id: 'vocab_car_a' })
  check('a review from the car: each rating at its own time (one ahead of the node\'s clock: now), nothing typed there, and the tutor told where',
    r3.ok && of3.length === 2 && Math.abs(Date.parse(of3.find(a => a.item_id === 'vocab_car_a')!.at) - (Date.now() - 2 * 3_600_000)) < 60_000 &&
      Date.parse(of3.find(a => a.item_id === 'vocab_car_b')!.at) <= Date.now() && car.text.includes('review vocab_car_a (rated): wrong; quality 1; nothing typed there; in the car') &&
      !car.text.includes('app from before') && lastNote().includes("App review in the car (✓ knew / ✗ didn't pressed) finished"), [of3, car.text, lastNote()])

  // --- never a review refused over its answers: too many, a bad where or time ------------------------------------------
  const many = Array.from({ length: 250 }, (_, i) => ({ item_id: 'vocab_many', answer: `a${i}`, verdict: 'correct' }))
  const r4 = await post({ results: [{ item_id: 'vocab_many', quality: 4 }], duration_minutes: 1, answers: many, from: 'Not a name!', answered_at: 'yesterday' })
  const b4 = await r4.json()
  const of4 = logged().filter(a => a.session_id === b4.session_id)
  check('a review with 250 answers, a from that isn\'t a name and a time that isn\'t one: 200; the log takes the first 100, at the arrival\'s time, no from',
    r4.ok && of4.length === 100 && of4[0].answer === 'a0' && of4[0].quality === 4 && of4[1].quality === undefined && of4.every(a => a.from === undefined && Date.now() - Date.parse(a.at) < 60_000), [b4, of4.length])

  // --- the log itself: the days kept, the most kept, the order answered, the file ---------------------------------------
  let now = new Date('2026-10-01T10:00:00Z')
  const dir = tempDir('lani-answers-')
  const file = join(dir, 'answers.json')
  const log = new AnswerLog(file, () => now, { days: 7, max: 3 })
  log.add([{ kind: 'review', item_id: 'a' }, { kind: 'review', item_id: 'b' }])
  now = new Date('2026-10-09T10:00:00Z')
  log.add([{ kind: 'review', item_id: 'c' }])
  const aged = log.all().map(a => a.item_id).join()
  log.add([1, 2, 3].map(i => ({ kind: 'form' as const, item_id: `f${i}` })))
  check('the log keeps the last days (older answers dropped) and at most its most (the oldest go first); newest first when asked',
    aged === 'c' && log.all().map(a => a.item_id).join() === 'f1,f2,f3' && log.find().map(a => a.item_id).join() === 'f3,f2,f1' && log.find({ limit: 1 }).length === 1, log.all())
  const roomy = new AnswerLog(file, () => now, { days: 7, max: 10 })
  roomy.add([{ kind: 'review', item_id: 'late', at: '2026-10-09T08:00:00Z' }, { kind: 'review', item_id: 'gone', at: '2026-09-20T08:00:00Z' }])
  check('… an answer sent late takes its place by when it was answered; one older than the days kept is dropped',
    roomy.all().map(a => a.item_id).join() === 'late,f1,f2,f3' && roomy.find()[0].item_id === 'f3', roomy.all())
  check('… the file is written whole (no temporary file left), readable by this user only', (statSync(file).mode & 0o777) === 0o600 && readdirSync(dir).join() === 'answers.json', [statSync(file).mode.toString(8), readdirSync(dir)])
  const long = 'x'.repeat(390)
  const parsed = reviewAnswers([{ item_id: 'k', answer: `  ${long}  `, verdict: 'almost', at: 'not a time' }, { item_id: '' }, 'nope', { item_id: 'm', verdict: 'maybe' }])
  check('a review\'s answers: the well-formed ones, their text trimmed to 200 characters, a bad time left out (not the answer); not a list: none',
    parsed.length === 1 && parsed[0].answer?.length === 200 && parsed[0].at === undefined && reviewAnswers({ item_id: 'k' }).length === 0 && reviewAnswers(undefined).length === 0, parsed)
  check('an answer as one line: no answer, heard, a hint, I spy\'s tap',
    answerLine({ at: '2026-10-11T09:14:00Z', kind: 'review', item_id: 'vocab_kurim', answer: '', expected: 'kurim', verdict: 'wrong' }) === '2026-10-11 09:14 review vocab_kurim (no answer) for "kurim": wrong' &&
      answerLine({ at: '2026-10-11T09:14:00Z', kind: 'review', item_id: 'v', answer: 'kurim', spoken: true, verdict: 'almost', hint: 'ending' }).includes('"kurim" (heard): almost, ending') &&
      answerLine({ at: '2026-10-11T09:14:00Z', kind: 'review', item_id: 'vocab_miza', type: 'ispy', answer: 'miza', expected: 'miza', verdict: 'correct', quality: 4, from: 'ispy' }).endsWith('"miza" for "miza": correct; quality 4; in I spy (the thing tapped in the picture)'))

  // --- the car's quiz sends a form in its forms and in the dialog's words: logged once; nothing counted, no quality ---------
  const quiz = answersOf({
    results: [],
    dialog: { scene: 'road', happening: 'quiz', title: '🚗 Kviz', words: [{ item_id: 'vocab_jajce', word: 'jajc', right: false, how: 'form', said: 'Deset jajce.', expected: 'Deset jajc.', review: false }, { item_id: 'vocab_miza', word: 'mizo', right: true, how: 'meaning', review: true }] },
    forms: [{ item_id: 'vocab_jajce', key: 'gen.pl', page: 'rodilnik', right: false, answer: 'Deset jajce.', expected: 'Deset jajc.' }],
    from: 'car-quiz',
  })
  check('a dialog\'s body: each word once, a form joined to its word (slot and page), no quality where nothing was counted',
    quiz.length === 2 && quiz[0].kind === 'dialog' && quiz[0].form === 'gen.pl' && quiz[0].page === 'rodilnik' && quiz[0].answer === 'Deset jajce.' && quiz.every(a => a.quality === undefined && a.from === 'car-quiz'), quiz)
}
