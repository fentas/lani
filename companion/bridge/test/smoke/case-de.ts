// Smoke checks: German's cases (docs/plans/05-built/case-de.md): its trigger table (case-triggers-de.ts) against the
// German grammar book, every example of it through German's lens (case-lens-de.ts) as it says; the trainer's German bank
// (case-bank-de.ts) against the table, the packs and the pages, its spread, and what its checks catch; the lens against
// the bank's gold (never a wrong case said as sure); the forms the app declines by (german-forms.ts and its fixture). In
// the Kärnten village's bridge (caseDeLive, from kaernten.ts): GET /cases serves the German bank, GET /lookup gives a
// German word's case only to an app that reads a language's own cases (?lens=2), POST /case-lens colours German lines,
// the tutor's tools take German's cases and items, POST /cases/done persists a German session.
import type { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { bankCounts, validateBank } from '../../src/case-bank'
import { checkItemsDe, checkRestDe, type CaseBankDe, type CaseItemDe } from '../../src/case-bank-de'
import { CaseLensDe } from '../../src/case-lens-de'
import { validateCaseTriggersDe } from '../../src/case-triggers-de'
import { stateFeatures } from '../../src/features/reviews'
import { GermanForms } from '../../src/german-forms'
import { caseCheck } from '../check-cases'
import { check } from './harness'

const repo = resolve(import.meta.dir, '../../..')
const text = (r: any): string => (r?.content ?? []).map((c: any) => c.text ?? '').join('\n')

/** The Kärnten village's names, as the bridge gives them to the lens (the villagers' given names and genders). */
export const KAERNTEN_NAMES = [
  ...['Kathi', 'Mitzi', 'Resi', 'Lena', 'Eva'].map(name => ({ name, gender: 'f' as const })),
  ...['Stefan', 'Franz', 'Sepp', 'Toni', 'Jakob', 'Florian', 'Maxi', 'Stanko', 'Ana'].map(name => ({ name, gender: 'm' as const })),
]

/** The lens over the repository's German table, dictionary supplement and the Kärnten packs' nouns. */
export function repoLensDe(): CaseLensDe {
  const ctx = caseCheck('de', repo)
  return new CaseLensDe({ table: ctx.de!.forms.table, dict: ctx.de!.dict, nouns: ctx.de!.nouns })
}

/** The lens on the bank's "which" items: how often its pick is the gold case and number, and how often it says a wrong one as sure. */
export function lensOnBank(lens: CaseLensDe, items: CaseItemDe[]) {
  const slot = { nominativ: 'nom', akkusativ: 'acc', dativ: 'dat', genitiv: 'gen' } as const
  let right = 0
  let open = 0
  const wrongDecided: string[] = []
  const wrongLikely: string[] = []
  const which = items.filter(i => i.kind === 'which')
  for (const it of which) {
    const toks = it.de.trim().split(/\s+/)
    const word = toks[it.word]!.replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '')
    const nth = toks.slice(0, it.word).filter(t => t.replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '').toLowerCase() === word.toLowerCase()).length
    const c = lens.at(it.de, word, nth, KAERNTEN_NAMES)
    const pick = c && 'pick' in c ? c.pick : null
    const want = `${slot[it.case]}.${it.number}`
    if (!pick || c!.sure === 'ambiguous') open++
    else if (`${pick.case}.${pick.number}` === want) right++
    else if (c!.sure === 'decided') wrongDecided.push(`${it.id}: ${pick.case}.${pick.number} (${c!.kind}), not ${want}`)
    else wrongLikely.push(`${it.id}: ${pick.case}.${pick.number} likely, not ${want}`)
  }
  return { items: which.length, right, open, wrongDecided, wrongLikely }
}

export default async function caseDe() {
  // --- the trigger table ---------------------------------------------------------------------------------------------
  const ctx = caseCheck('de', repo)
  const pages = new Set(ctx.pages.keys())
  const raw = JSON.parse(readFileSync(join(repo, 'grammar/de/data/case-triggers.json'), 'utf8'))
  const tv = validateCaseTriggersDe(raw, pages)
  check("German's trigger table validates: the four cases in the course's order with their questions, colours and pages, every determiner's sixteen forms, every rule a page of the German grammar book", tv.ok, tv.ok ? undefined : tv.errors.split('\n').slice(0, 20))
  if (!tv.ok) return
  const t = tv.table
  check("… the cases' colours are Slovene's for the same case (one look for a learner of both)", t.cases.map(c => `${c.key}:${c.colour}`).join(' ') === 'nom:#4A7FC1 acc:#3E9B5F dat:#D69A2D gen:#C2503C')
  check('… the two-way prepositions take the accusative by motion and the dative by rest', ['in', 'an', 'auf', 'über', 'unter', 'vor', 'hinter', 'neben', 'zwischen'].every(w => {
    const p = t.prepositions.find(x => x.words.includes(w))
    return p?.takes.some(x => x.case === 'acc' && x.sense === 'motion') && p.takes.some(x => x.case === 'dat' && x.sense === 'rest')
  }))
  check("… the genitive's prepositions with the dative people say, marked spoken (wegen dem Regen)", ['wegen', 'trotz', 'während', 'statt'].every(w => t.prepositions.find(x => x.words.includes(w))?.takes.some(x => x.case === 'dat' && x.spoken)))
  check('… the verbs with a dative (helfen, danken, gefallen) and with a dative and an accusative (geben, zeigen)', ['helfen', 'danken', 'gefallen', 'gehören', 'schmecken'].every(l => t.verbs.some(v => v.lemma === l && v.takes[0] === 'dat')) && ['geben', 'zeigen', 'schenken'].every(l => t.verbs.some(v => v.lemma === l && v.takes.join() === 'dat,acc')))
  const f = new GermanForms(t)
  const forms = [f.determinerForm('der', 'dat', 'm', 'sg'), f.determinerForm('ein', 'acc', 'm', 'sg'), f.determinerForm('mein', 'gen', 'f', 'sg'), f.adjectiveForm('groß', 'ein', 'nom', 'm', 'sg'), f.adjectiveForm('hoch', 'der', 'dat', 'f', 'sg'), f.adjectiveForm('frisch', null, 'dat', 'n', 'sg'), f.noun('Kind', 'n', 'Kinder', 'dat', 'pl').forms[0], f.noun('Kollege', 'm', undefined, 'acc', 'sg').forms[0], f.noun('Haus', 'n', 'Häuser', 'gen', 'sg').forms[0], f.noun('Herr', 'm', undefined, 'nom', 'pl').forms[0]].join(' ')
  check(`… its forms: dem einen meiner großer hohen frischem Kindern Kollegen Hauses Herren (${forms})`, forms === 'dem einen meiner großer hohen frischem Kindern Kollegen Hauses Herren')
  check("… a noun whose genitive may double its -s isn't sure (des Busses)", !f.noun('Bus', 'm', 'Busse', 'gen', 'sg').sure && f.noun('Hund', 'm', 'Hunde', 'gen', 'sg').also.includes('Hunds'))
  const parity = JSON.parse(readFileSync(join(repo, 'bridge/test/fixtures/german-forms.json'), 'utf8')) as { nouns: { lemma: string; gender: 'm' | 'f' | 'n'; plural?: string; slots: Record<string, { forms: string[]; also: string[]; sure: boolean }> }[] }
  const drift = parity.nouns.filter(n => Object.entries(n.slots).some(([k, want]) => {
    const [c, num] = k.split('.') as ['nom', 'sg']
    return JSON.stringify(f.noun(n.lemma, n.gender, n.plural, c, num)) !== JSON.stringify(want)
  }))
  check(`… the app's German forms fixture (data/GermanForms.kt's parity) is what german-forms.ts makes (${parity.nouns.length} nouns)`, drift.length === 0, drift.map(n => n.lemma))

  // --- the table's examples through the lens -----------------------------------------------------------------------------
  const lens = repoLensDe()
  const examples = [
    ...t.prepositions.flatMap(p => p.examples), ...t.predicates.flatMap(p => p.examples), ...t.relative.examples, ...t.time.examples, ...t.time_genitive.examples,
    ...Object.values(t.roles).flatMap(r => r.examples),
  ]
  const missed = examples.flatMap(e => {
    const c = lens.at(e.de, e.word, e.nth ?? 0, KAERNTEN_NAMES)
    const pick = c && 'pick' in c ? c.pick : null
    const ok = pick && pick.case === e.case && pick.number === e.number && (!e.trigger || (c as any).trigger?.word === e.trigger)
    return ok ? [] : [`${e.de} ${e.word}: ${pick ? `${pick.case}.${pick.number} by ${(c as any).trigger?.word ?? (c as any).kind}` : 'nothing'}, not ${e.case}.${e.number}${e.trigger ? ` by ${e.trigger}` : ''}`]
  })
  check(`German's lens reads every example of the table as it says (${examples.length})`, missed.length === 0, missed)

  // --- the bank ----------------------------------------------------------------------------------------------------------
  const bankRaw = JSON.parse(readFileSync(join(repo, 'cases/de/bank.json'), 'utf8'))
  const v = validateBank(bankRaw, ctx)
  check("the German case bank validates: every article, adjective and noun that case's (the table's forms, the packs' genders and plurals), every trigger its rule's word, every page and scene there", v.ok, v.ok ? undefined : v.errors.split('\n').slice(0, 30))
  if (!v.ok) return
  const bank = v.bank as unknown as CaseBankDe
  const n = bankCounts(bank.items as any)
  check(`… items over the four cases (${n.items}: ${JSON.stringify(n.case)})`, n.items >= 250 && ['nominativ', 'akkusativ', 'dativ', 'genitiv'].every(c => (n.case[c] ?? 0) >= 30), n)
  check(`… "Welcher Fall?" and "Wo oder wohin?" (${JSON.stringify(n.kind)})`, (n.kind.which ?? 0) >= 170 && (n.kind.place ?? 0) >= 50, n.kind)
  check(`… A1 to B2 (${JSON.stringify(n.level)})`, ['A1', 'A2', 'B1', 'B2'].every(l => (n.level[l] ?? 0) >= 25), n.level)
  check(`… the singular and the plural (${JSON.stringify(n.number)})`, (n.number.sg ?? 0) > 0 && (n.number.pl ?? 0) >= 30, n.number)
  check('… machine-written and flagged for the native check', bank.machine_written && !bank.reviewed && /native speaker/.test(bank.review))
  check('… every case has frames, singular and plural', ['nominativ', 'akkusativ', 'dativ', 'genitiv'].every(c => bank.frames.some(x => x.case === c)) && bank.frames.some(x => x.number === 'pl'))
  const child = bank.items.filter(i => (i.level === 'A1' || i.level === 'A2') && i.de.trim().split(/\s+/).length <= 7).length
  check(`… short A1–A2 sentences for a child (${child})`, child >= 80, child)
  const spoken = bank.items.filter(i => i.spoken)
  check(`… forms people say are right and labelled, never a wrong option (${spoken.length})`, spoken.length >= 3 && bank.items.every(i => i.kind !== 'place' || !spoken.some(s => i.options.some((o, k) => k !== i.answer && o.toLowerCase() === s.spoken!.form.toLowerCase()))))

  // --- what the checks catch -----------------------------------------------------------------------------------------------
  const d = ctx.de!
  const good = bank.items.find(i => i.id === 'mit-dem-traktor')!
  const probs = (it: Partial<CaseItemDe>) => checkItemsDe([{ ...good, id: 'probe', ...it } as CaseItemDe], bank.rules, d).problems.join(' | ')
  check('the German checks catch an article of another case', /"den" isn't a determiner's dat\.m/.test(probs({ de: 'Franz fährt mit den Traktor aufs Feld.' })), probs({ de: 'Franz fährt mit den Traktor aufs Feld.' }))
  check("… an adjective's ending", /isn't alt after der in dat\.m/.test(probs({ de: 'Franz fährt mit dem alte Traktor.', word: 5, phrase: [3, 5] })), probs({ de: 'Franz fährt mit dem alte Traktor.', word: 5, phrase: [3, 5] }))
  check('… a trigger that is no word of its rule', /trigger "fährt" isn't a word of the rule/.test(probs({ trigger: 1 })), probs({ trigger: 1 }))
  check('… a level below its page', /below its page praepositionen-dativ's A2/.test(probs({ level: 'A1' })))
  check("… a noun's form that isn't the case's (den Kindern, not den Kinder)", /isn't the dat\.pl of Kind/.test(probs({ de: 'Franz spielt mit den Kinder.', word: 4, phrase: [3, 4], lemma: 'Kind', gender: 'n', number: 'pl' })), probs({ de: 'Franz spielt mit den Kinder.', word: 4, phrase: [3, 4], lemma: 'Kind', gender: 'n', number: 'pl' }))
  const rest = checkRestDe({ ...bank, frames: [{ ...bank.frames[0]!, de: 'Wir gehen zu {x}.', det: 'der', number: 'sg' }, { ...bank.frames[0]!, id: 'probe-2', det: 'ein', number: 'pl' }], adjectives: [], nouns: [{ lemma: 'Bus', gender: 'm', plural: 'Busse', level: 'A1', sl: 'avtobus', en: 'bus', it: 'autobus' }], uncountable: [] }, d).problems.join(' | ')
  check('… a frame whose der would fuse with its preposition (zu dem: zum), ein in the plural, a noun whose genitive the rules aren\'t sure of', /contraction would be the usual form/.test(rest) && /ein has no plural/.test(rest) && /genitive isn't sure/.test(rest), rest)

  // --- the lens against the bank's gold ------------------------------------------------------------------------------------
  const e = lensOnBank(lens, bank.items)
  check(`German's lens never says a wrong case as sure on the bank's ${e.items} items (right ${e.right}, open ${e.open}, likely but wrong ${e.wrongLikely.length})`, e.wrongDecided.length === 0, e.wrongDecided)
  check(`… and reads most of them right (${e.right}/${e.items})`, e.right >= e.items * 0.85, e.wrongLikely.slice(0, 20))

  // the lens on lines of the content it wasn't built on (every 79th line, a word labelled by hand before the lens ran)
  const held = JSON.parse(readFileSync(join(repo, 'bridge/test/fixtures/case-lens-de-heldout.json'), 'utf8')).items as { de: string; word: string; nth?: number; case: string; number: string }[]
  const heldOut = held.map(it => {
    const c = lens.at(it.de, it.word, it.nth ?? 0, KAERNTEN_NAMES)
    const p = c && 'pick' in c ? c.pick : null
    return { it, p, sure: c && 'sure' in c ? c.sure : null }
  })
  const heldWrong = heldOut.filter(x => x.p && x.sure === 'decided' && (x.p.case !== x.it.case || x.p.number !== x.it.number)).map(x => `${x.it.word} in «${x.it.de}»: ${x.p!.case}.${x.p!.number}`)
  const heldRight = heldOut.filter(x => x.p && x.sure !== 'ambiguous' && x.p.case === x.it.case && x.p.number === x.it.number).length
  check(`German's lens on ${held.length} held-out content lines: none wrong as sure, most right (${heldRight})`, heldWrong.length === 0 && heldRight >= held.length * 0.85, heldWrong)

  // --- older apps -----------------------------------------------------------------------------------------------------------
  check('GET /state says "cases" in a Slovene and a German village, "counting" and "forms-cards" in Slovene\'s only', stateFeatures('de').includes('cases') && !stateFeatures('de').includes('counting') && !stateFeatures('de').includes('forms-cards') && stateFeatures('sl').includes('counting') && !stateFeatures('it').includes('cases'))
}

/** In the Kärnten village's bridge (kaernten.ts): its trainer, its lens, the tutor's tools, a session persisted. */
export async function caseDeLive(base: string, auth: Record<string, string>, client: Client) {
  const served = (await (await fetch(`${base}/cases`, { headers: auth })).json()) as any
  const bank = JSON.parse(readFileSync(join(repo, 'cases/de/bank.json'), 'utf8'))
  check("kaernten: GET /cases serves German's bank (its items, rules, frames)", served.language === 'de' && served.items?.length === bank.items.length && Object.keys(served.rules).length === Object.keys(bank.rules).length, { language: served.language, items: served.items?.length })
  const line = 'Franz fährt mit dem Traktor aufs Feld.'
  const q = (extra: string) => fetch(`${base}/lookup?w=Traktor&line=${encodeURIComponent(line)}${extra}`, { headers: auth }).then(r => r.json() as Promise<any>)
  const old = await q('')
  const now = await q('&lens=2')
  check("kaernten: GET /lookup gives a German word's case only to an app that reads a language's own cases (?lens=2): an older app knows Slovene's six alone",
    !old.case && now.case?.pick?.case === 'dat' && now.case?.trigger?.word === 'mit' && now.case?.rule === 'praepositionen-dativ', { old: old.case, now: now.case?.pick })
  const colours = (await (await fetch(`${base}/case-lens`, { method: 'POST', headers: auth, body: JSON.stringify({ lines: [line, 'Ich warte auf den Bus.'] }) })).json()) as any
  const marks = colours.lines?.map((l: any) => l.words.map((w: any) => `${w.word}:${w.case ?? '?'}`).join(' '))
  check('kaernten: POST /case-lens colours German lines by case (the article, the adjectives, the noun)', marks?.[0]?.includes('dem:dat') && marks[0].includes('Traktor:dat') && marks[1]?.includes('den:acc') && marks[1].includes('Bus:acc'), marks)
  const got = text(await client.callTool({ name: 'get_cases', arguments: {} }))
  check("kaernten: get_cases answers in a German village (the bank's four cases)", /"items"/.test(got) && /dativ/.test(got), got.slice(0, 300))
  const assigned = text(await client.callTool({ name: 'assign_cases', arguments: { focus: 'dativ', note: 'mit, von, zu' } }))
  const refused = text(await client.callTool({ name: 'assign_cases', arguments: { focus: 'rodilnik' } }))
  check("kaernten: assign_cases takes German's cases, not Slovene's", /assigned a case trainer session on dativ/.test(assigned) && /neither a case \(nominativ, akkusativ, dativ, genitiv\)/.test(refused), [assigned, refused])
  await client.callTool({ name: 'assign_cases', arguments: { focus: null } })
  const item = { id: 'tutor-bei-der-oma', kind: 'which', level: 'A2', de: 'Ich bin heute bei der Oma.', sl: 'Danes sem pri babici.', en: "I'm at Granny's today.", it: 'Oggi sono dalla nonna.', word: 5, phrase: [4, 5], lemma: 'Oma', gender: 'f', case: 'dativ', number: 'sg', why: 'dat-praep', trigger: 3 }
  const published = text(await client.callTool({ name: 'publish_case_items', arguments: { items: [item] } }))
  const wrong = text(await client.callTool({ name: 'publish_case_items', arguments: { items: [{ ...item, id: 'tutor-bei-die-oma', de: 'Ich bin heute bei die Oma.' }] } }))
  check("kaernten: publish_case_items takes a German item, checked as the bank is (its article against its case)", /published 1 case item/.test(published) && /"die" isn't a determiner's dat\.f/.test(wrong), [published, wrong.slice(0, 300)])
  await client.callTool({ name: 'remove_case_item', arguments: { id: 'tutor-bei-der-oma' } })
  const done = await fetch(`${base}/cases/done`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ client_id: 'smoke-case-de-1', duration_minutes: 4, answers: [{ kind: 'which', item: 'mit-dem-traktor', case: 'dativ', number: 'sg', rule: 'praepositionen-dativ', verdict: 'correct', why: 'right' }, { kind: 'decline', item: 'd:helfe-dat:Opa', case: 'dativ', number: 'sg', rule: 'dativ', verdict: 'wrong', answer: 'meinen Opa', expected: 'meinem Opa' }], errors: [{ pattern_id: 'cases_dativ_dat_verb', your_answer: 'meinen Opa', correct_answer: 'meinem Opa' }], mastery: { dativ: { right: 1, of: 2 } } }),
  })
  const body = (await done.json()) as any
  check('kaernten: POST /cases/done persists a German session (its cases, the dative weak)', done.ok && typeof body.session_id === 'string' && body.errors === 1, body)
  // German's cases overview, from its page faelle (prints/overview.ts)
  const ov = await fetch(`${base}/course/overview/faelle?base=sl`, { headers: auth })
  const ovText = await ov.text()
  check("kaernten: GET /course/overview/faelle prints German's cases overview: its four cases, German first, the base's headings", ov.ok && ov.headers.get('x-lani-print-name') === 'ueberblick-faelle' && ovText.includes('Die vier Fälle') && ovText.includes('Štirje skloni') && ovText.includes('Wessen?') && !ovText.includes('Šest sklonov'), [ov.status, ov.headers.get('x-lani-print-name')])
}
