// Smoke checks: the case trainer (case-bank.ts, features/cases.ts; companion/README.md "Case trainer"). The repository's bank
// against the dictionary, the forms book, the grammar book and the scenes (every word's form that case of its lemma
// where the dictionary has the word, every trigger the word its rule names, every page and scene there), its spread over
// the cases, numbers and levels, and what the checks catch; GET /cases; the tutor's publish_case_items, remove_case_item,
// assign_cases and get_cases; POST /cases/done persisted as grammar practice with the app's errors (update-db.py, once per
// client id), the assignment done, cases_done to the tutor; PUT /game keeping the trainer's progress, get_village's.
import { readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { bankCounts, checkItems, checkRest, validateBank, type CaseItem } from '../../src/case-bank'
import { casesNote, casesPayload, casesDone } from '../../src/features/cases'
import { caseCheck } from '../check-cases'
import { auth, base, channelEvents, check, client, dataDir, guideOf } from './harness'

const repo = resolve(import.meta.dir, '../../..')
const text = (r: any): string => (r?.content ?? []).map((c: any) => c.text ?? '').join('\n')

export default async function cases() {
  // --- the bank ----------------------------------------------------------------------------------------------------
  const raw = JSON.parse(readFileSync(join(repo, 'cases/sl/bank.json'), 'utf8'))
  const ctx = caseCheck('sl', repo)
  const v = validateBank(raw, ctx)
  check('the case bank validates: every form that case of its lemma (the dictionary), every trigger its rule\'s word, every page and scene there', v.ok, v.ok ? undefined : v.errors.split('\n').slice(0, 30))
  if (!v.ok) return
  const bank = v.bank
  const n = bankCounts(bank.items)
  check(`… about 300 items over the six cases (${n.items}: ${JSON.stringify(n.case)})`, n.items >= 300 && Object.keys(n.case).length === 6 && Object.values(n.case).every(c => c >= 20), n)
  check(`… "Kateri sklon?" and "Kje ali kam?" (${JSON.stringify(n.kind)})`, (n.kind.which ?? 0) >= 200 && (n.kind.place ?? 0) >= 60, n.kind)
  check(`… the singular, the plural and the dual (${JSON.stringify(n.number)})`, (n.number.sg ?? 0) > 0 && (n.number.pl ?? 0) >= 40 && (n.number.du ?? 0) >= 15, n.number)
  check(`… A1, A2 and B1 (${JSON.stringify(n.level)})`, ['A1', 'A2', 'B1'].every(l => (n.level[l] ?? 0) >= 30), n.level)
  check(`… the dictionary judged most of its words (${v.judged})`, v.judged >= 250, v.unjudged)
  check('… machine-written and flagged for the native check', bank.machine_written && !bank.reviewed && /native speaker/.test(bank.review))
  check('… every case has frames, the adjectives and possessives every form', ['imenovalnik', 'rodilnik', 'dajalnik', 'tozilnik', 'mestnik', 'orodnik'].every(c => bank.frames.some(f => f.case === c)) && bank.adjectives.some(a => a.kind === 'possessive'))
  const ids = new Set(bank.items.map(i => i.id))
  check('… every item an id of its own', ids.size === bank.items.length)
  const child = bank.items.filter(i => i.level !== 'B1' && i.sl.trim().split(/\s+/).length <= 6).length
  check(`… short A1–A2 sentences for a child (${child})`, child >= 100, child)

  // --- what the checks catch --------------------------------------------------------------------------------------------
  const good = bank.items.find(i => i.id === 'brez-mleka') as CaseItem
  const probs = (it: Partial<CaseItem>) => checkItems([{ ...good, id: 'probe', ...it } as CaseItem], bank.rules, ctx).problems.join(' | ')
  check('a form that is no case of its lemma is caught', /isn't the gen\.sg of mleko/.test(probs({ sl: 'Kavo pijem brez mleko.' })), probs({ sl: 'Kavo pijem brez mleko.' }))
  check('… a trigger that names another word', /trigger "pijem" isn't a word of the rule "brez"/.test(probs({ trigger: 1 })), probs({ trigger: 1 }))
  check('… a rule of another case', /is the rule of tozilnik/.test(probs({ why: 'v-na-kam' })), probs({ why: 'v-na-kam' }))
  check('… a word past the sentence', /word 9 is past/.test(probs({ word: 9 })))
  check("… an ambiguous form no word decides, without a note", /no word decides it/.test(probs({ trigger: null, role: 'object', sl: 'Vidim mize.', word: 1, lemma: 'miza', case: 'tozilnik', number: 'pl', why: 'object' })), probs({ trigger: null, role: 'object', sl: 'Vidim mize.', word: 1, lemma: 'miza', case: 'tozilnik', number: 'pl', why: 'object' }))
  check('… another reading that the form isn\'t', /ambiguous dat\.sg/.test(probs({ ambiguous: ['dat.sg'] })), probs({ ambiguous: ['dat.sg'] }))
  check('… a level below its page', /below its page rodilnik-predlogi's A2/.test(probs({ level: 'A1' })))
  const place = bank.items.find(i => i.kind === 'place' && i.question === 'kje-kam') as CaseItem & { kind: 'place' }
  check("… a place item's answer that isn't its gap", /isn't the gap's/.test(checkItems([{ ...place, id: 'probe', answer: (place.answer + 1) % place.options.length }], bank.rules, ctx).problems.join(' ')))
  check('… a scene the village lacks', /isn't one of the village's/.test(checkItems([{ ...place, id: 'probe', scene: 'nowhere' }], bank.rules, ctx).problems.join(' ')))
  const rest = checkRest({ ...bank, frames: [{ ...bank.frames[0], sl: 'Danes ni {x} ni {x}.' }], adjectives: [{ ...bank.adjectives.find(a => a.lemma === 'lep')!, forms: { ...bank.adjectives.find(a => a.lemma === 'lep')!.forms, 'gen.f.sg': 'lepa' } }], nouns: [], uncountable: ['vodaa'] }, ctx).problems.join(' | ')
  check('… a frame with its gap twice, an adjective\'s form the dictionary lacks, a mass noun that is none', /\{x\} once/.test(rest) && /gen\.f\.sg "lepa" isn't the dictionary's/.test(rest) && /uncountable\.vodaa/.test(rest), rest)

  // --- served ----------------------------------------------------------------------------------------------------------
  const served = (await (await fetch(`${base}/cases`, { headers: auth })).json()) as any
  check('GET /cases serves the bank: its items, rules, frames, adjectives and nouns', served.items?.length === bank.items.length && served.items.every((i: any) => i.source === 'curated') && Object.keys(served.rules).length === Object.keys(bank.rules).length && served.frames.length === bank.frames.length, { items: served.items?.length })
  check('GET /cases needs the token', (await fetch(`${base}/cases`)).status === 401)

  // --- the tutor's items ----------------------------------------------------------------------------------------------
  const mine = { ...good, id: 'smoke-brez-sladkorja', sl: 'Čaj pijem brez sladkorja.', en: 'I drink tea without sugar.', de: 'Ich trinke Tee ohne Zucker.', it: 'Bevo il tè senza zucchero.', lemma: 'sladkor', from: 'bank' }
  const wrong = await client.callTool({ name: 'publish_case_items', arguments: { items: [{ ...mine, sl: 'Čaj pijem brez sladkor.' }] } })
  check('publish_case_items checks the form as the bank is checked', wrong.isError === true && /isn't the gen\.sg of sladkor/.test(text(wrong)), text(wrong))
  const curated = await client.callTool({ name: 'publish_case_items', arguments: { items: [{ ...mine, id: 'brez-mleka' }] } })
  check("… and refuses a curated item's id", curated.isError === true && /curated/.test(text(curated)), text(curated))
  const pub = await client.callTool({ name: 'publish_case_items', arguments: { items: [mine], note: 'Brez in rodilnik' } })
  const after = (await (await fetch(`${base}/cases`, { headers: auth })).json()) as any
  const got = after.items.find((i: any) => i.id === 'smoke-brez-sladkorja')
  check('… publishes a right one: GET /cases has it, marked the tutor\'s', !pub.isError && got?.source === 'tutor' && got.from === 'tutor' && after.items.length === bank.items.length + 1, [text(pub), got])
  const rm = await client.callTool({ name: 'remove_case_item', arguments: { id: 'smoke-brez-sladkorja' } })
  const rmCurated = await client.callTool({ name: 'remove_case_item', arguments: { id: 'brez-mleka' } })
  check("remove_case_item takes the tutor's back, never the bank's", !rm.isError && rmCurated.isError === true && ((await (await fetch(`${base}/cases`, { headers: auth })).json()) as any).items.length === bank.items.length)

  // --- the assignment ---------------------------------------------------------------------------------------------------
  const badFocus = await client.callTool({ name: 'assign_cases', arguments: { focus: 'nominativ' } })
  check('assign_cases refuses a focus that is neither a case nor a rule', badFocus.isError === true && /neither a case/.test(text(badFocus)), text(badFocus))
  const assign = await client.callTool({ name: 'assign_cases', arguments: { focus: 'rodilnik-predlogi', note: 'iz, brez: the genitive' } })
  const withAssign = (await (await fetch(`${base}/cases`, { headers: auth })).json()) as any
  check('… assigns a session on a rule: GET /cases has it until it is played', !assign.isError && withAssign.assigned?.focus === 'rodilnik-predlogi' && withAssign.assigned.note === 'iz, brez: the genitive', withAssign.assigned)

  // --- a session done --------------------------------------------------------------------------------------------------
  const body = {
    duration_minutes: 4,
    focus: 'rodilnik-predlogi',
    answers: [
      { kind: 'which', item: 'brez-mleka', case: 'rodilnik', number: 'sg', rule: 'rodilnik-predlogi', verdict: 'correct', why: 'right', answer: '2. rodilnik · brez', expected: '2. rodilnik · brez', sentence: 'Kavo pijem brez mleka.' },
      { kind: 'which', item: 'iz-gorice', case: 'rodilnik', number: 'sg', rule: 'rodilnik-predlogi', verdict: 'wrong', why: 'right', answer: '5. mestnik · iz', expected: '2. rodilnik · iz', sentence: 'Moja partnerka je iz Gorice.' },
      { kind: 'place', item: 'gre-na-trznico', case: 'tozilnik', number: 'sg', rule: 'kam-tozilnik', verdict: 'almost', why: 'wrong', answer: 'na tržnico · Micka', expected: 'na tržnico · gre' },
      { kind: 'decline', item: 'd:danes-ni:miza', case: 'rodilnik', number: 'sg', rule: 'rodilnik-nikalnica', verdict: 'wrong', why: 'none', answer: 'mizo', expected: 'mize', sentence: 'Danes ni mize.' },
    ],
    errors: [
      { pattern_id: 'cases_genitive_iz', category: 'grammar', subcategory: 'cases', description: 'Genitive (rodilnik): iz always takes the genitive', severity: 'moderate', your_answer: '5. mestnik · iz', correct_answer: '2. rodilnik · iz', context: '🧩 which: Moja partnerka je iz Gorice. (rodilnik-predlogi)' },
      { pattern_id: 'cases_trigger_v_na_kam', severity: 'minor', your_answer: 'na tržnico · Micka', correct_answer: 'na tržnico · gre' },
      { pattern_id: 'cases_form_genitive', severity: 'moderate', your_answer: 'mizo', correct_answer: 'mize', context: '🧩 decline: Danes ni mize. (rodilnik-nikalnica)' },
    ],
    mastery: { rodilnik: { right: 1, of: 3, stars: 1 }, tozilnik: { right: 1, of: 1, stars: 2 } },
    rules: { 'rodilnik-predlogi': { right: 1, of: 2 } },
    machine_written: true,
    client_id: 'smoke-cases-0001',
  }
  check('the session format reads the app\'s wire (data/CaseTrainer.kt wire)', casesDone.safeParse(body).success, casesDone.safeParse(body).error)
  const payload = casesPayload(casesDone.parse(body), { session_id: 'session-x', date: '2026-10-29' })
  check('… as grammar practice (the chosen ones) and writing (the typed one), the rules its topics, the weakest case next', JSON.stringify(payload.skill_scores) === JSON.stringify({ grammar: { exercises: 3, correct: 1, time_minutes: 3 }, writing: { exercises: 1, correct: 0, time_minutes: 1 } }) && payload.topics_covered.includes('rodilnik-nikalnica') && payload.focus_next_session?.[0]?.includes('genitive') === true, payload)
  check("… its note says what went wrong and the deciding word's taps", /1\/4 right, 1 almost/.test(casesNote(casesDone.parse(body))) && /tapped right 2\/3/.test(casesNote(casesDone.parse(body))), casesNote(casesDone.parse(body)))
  const storm = casesNote(casesDone.parse({ ...body, storm: true, focus: undefined }))
  check('… a case storm says so (sorted against the clock)', /^Played a case storm/.test(storm) && /right/.test(storm), storm)
  const read = (f: string) => JSON.parse(readFileSync(join(dataDir, f), 'utf8'))
  const mistakesBefore = read('mistakes-db.json').error_patterns ?? {}
  const sessionsBefore = (read('session-log.json').sessions ?? []).length
  const done = await fetch(`${base}/cases/done`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
  const d = (await done.json()) as any
  const mistakes = read('mistakes-db.json').error_patterns ?? {}
  const sessions = read('session-log.json').sessions ?? []
  check('POST /cases/done persists the session (update-db.py): a session in the log, the errors under their patterns', done.ok && sessions.length === sessionsBefore + 1 && mistakes.cases_genitive_iz?.frequency === (mistakesBefore.cases_genitive_iz?.frequency ?? 0) + 1 && mistakes.cases_form_genitive?.examples?.at(-1)?.incorrect === 'mizo' && mistakes.cases_trigger_v_na_kam?.severity === 'minor', { d, sessions: sessions.at(-1) })
  check('… its assignment is done (the session had its focus)', d.assignment_done === 'rodilnik-predlogi' && ((await (await fetch(`${base}/cases`, { headers: auth })).json()) as any).assigned === undefined, d)
  const replay = await fetch(`${base}/cases/done`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
  check('… once per client id: a repeat counts nothing twice', replay.headers.get('x-lani-replay') === '1' && (read('session-log.json').sessions ?? []).length === sessions.length)
  await Bun.sleep(100)
  const note = channelEvents.find(e => e.params?.meta?.kind === 'cases_done')
  check('cases_done reaches the tutor, already persisted, the assignment done', !!note && /Already persisted/.test(note.params.content) && /assignment \(rodilnik-predlogi\) is done/.test(note.params.content), note?.params?.content)
  const empty = await fetch(`${base}/cases/done`, { method: 'POST', headers: auth, body: JSON.stringify({ duration_minutes: 1, answers: [] }) })
  check('… a session without answers is refused', empty.status === 400)
  const odd = await fetch(`${base}/cases/done`, { method: 'POST', headers: auth, body: JSON.stringify({ ...body, client_id: 'smoke-cases-0002', answers: [{ ...body.answers[0], case: 'vokativ' }] }) })
  check('… and a case there is not', odd.status === 400)

  // --- the village state: kept, and what the tutor reads ------------------------------------------------------------------
  const game = (await (await fetch(`${base}/game`, { headers: auth })).json()) as any
  const progress = { v: 1, keys: { 'case:rodilnik': '1001', 'rule:rodilnik-predlogi': '10', 'kind:which': '11' }, why: { 'rodilnik-predlogi': '11' }, again: [{ item: 'iz-gorice', due: '2026-10-29', box: 0 }], seen: { 'kam-tozilnik': { n: 2, day: '2026-10-29' } }, recent: ['iz-gorice'], runs: 3, day: '2026-10-29' }
  const put = (rev: number, state: object) => fetch(`${base}/game`, { method: 'PUT', headers: auth, body: JSON.stringify({ rev, state }) })
  const rev = game?.rev ?? 0
  const state = game?.state ?? {}
  const w = await put(rev, { ...state, cases: progress })
  const { cases: _drop, ...older } = state
  const o = await put(rev + 1, older)
  const kept = (await (await fetch(`${base}/game`, { headers: auth })).json()) as any
  check("PUT /game keeps the trainer's progress when an older app's state leaves it out", w.ok && o.ok && kept.state.cases?.runs === 3, kept.state.cases)
  const village = JSON.parse(text(await client.callTool({ name: 'get_village', arguments: {} })))
  check("get_village shows the trainer: each case's and rule's last answers, what comes back, the lens's", village.cases?.cases?.rodilnik?.right === 2 && village.cases.cases.rodilnik.of === 4 && village.cases.rules['rodilnik-predlogi'].of === 2 && village.cases.coming_back?.[0]?.item === 'iz-gorice' && village.cases.seen_in_lens?.['kam-tozilnik']?.n === 2, village.cases)
  const gc = JSON.parse(text(await client.callTool({ name: 'get_cases', arguments: {} })))
  check('get_cases: the learner\'s mastery, the bank\'s spread, no assignment open', gc.learner?.sessions === 3 && gc.bank?.items === bank.items.length && gc.assigned === undefined, gc)

  // --- the guide -------------------------------------------------------------------------------------------------------
  const guide = await guideOf()
  check('the channel guide has the trainer: cases_done persisted, assign_cases, publish_case_items, get_cases', ['Case trainer', 'cases_done', 'ALREADY persisted', 'assign_cases', 'publish_case_items', 'get_cases', 'never call a spoken form wrong'].every(s => guide.includes(s)))
}
