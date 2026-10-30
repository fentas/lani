// Smoke checks: the case lens (case-lens.ts, docs/plans/05-built/case-lens.md). Its trigger table (companion/grammar/sl/data/
// case-triggers.json: the schema, the shared case ids and colours, every rule a page, the personal pronouns as the
// osebni-zaimki page has them, every example analysed as it says), the analysis on the hand-labelled sentences (the set
// it was built on and the held-out lines: test/case-lens-eval.ts), on every line of the content (none throws, in time),
// and through the bridge: GET /lookup's case, POST /case-lens for the colours, the lookup's seen flag.
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { addressee, renderDeep } from '../../src/addressee'
import { CASE_IDS, CASE_KEYS, validateCaseTriggers, type CaseExample } from '../../src/case-triggers'
import { contentLines } from '../../src/forms'
import { evaluate, goldItems, repoLens, summary, trainerItems, VILLAGE_NAMES } from '../case-lens-eval'
import { auth, base, check, dataDir } from './harness'

const companion = resolve(import.meta.dir, '../../..')
const lookup = async (q: Record<string, string>) => (await (await fetch(`${base}/lookup?${new URLSearchParams(q)}`, { headers: auth })).json()) as any
const colours = async (body: unknown) => {
  const r = await fetch(`${base}/case-lens`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
  return { status: r.status, body: (await r.json()) as any }
}

export default async function cases() {
  // --- the trigger table -----------------------------------------------------------------------------------------
  const raw = JSON.parse(readFileSync(join(companion, 'grammar/sl/data/case-triggers.json'), 'utf8'))
  const pages = new Set(readdirSync(join(companion, 'grammar/sl')).filter(f => f.endsWith('.json')).map(f => f.slice(0, -5)))
  const v = validateCaseTriggers(raw, pages)
  check('case lens: the trigger table validates (its schema; each preposition, verb, phrase and pronoun once; every rule a page of the grammar book), machine-written and saying so', v.ok && raw.machine_written === true && /not reviewed by a native speaker/.test(raw.review), v.ok ? raw.review : v.errors)
  const shared = [
    ['nom', 'imenovalnik', 1, 'kdo? kaj?', '#4A7FC1'], ['gen', 'rodilnik', 2, 'koga? česa?', '#C2503C'], ['dat', 'dajalnik', 3, 'komu? čemu?', '#D69A2D'],
    ['acc', 'tozilnik', 4, 'koga? kaj?', '#3E9B5F'], ['loc', 'mestnik', 5, 'pri kom? pri čem?', '#8A5CB8'], ['ins', 'orodnik', 6, 's kom? s čim?', '#2C9AA0'],
  ]
  check(
    'case lens: the six cases as the case trainer has them (the shared contract): ids, numbers, question words, colours; the locative\'s o kom? o čem? too',
    JSON.stringify(raw.cases.map((c: any) => [c.key, c.id, c.n, c.questions.join(' '), c.colour])) === JSON.stringify(shared) &&
      JSON.stringify(Object.values(CASE_IDS)) === JSON.stringify(shared.map(s => s[1])) && raw.cases[4].also.join(' ') === 'o kom? o čem?',
    raw.cases,
  )
  const broken = [
    validateCaseTriggers({ ...raw, negation: { ...raw.negation, rule: 'nowhere' } }, pages),
    validateCaseTriggers({ ...raw, cases: [raw.cases[1], raw.cases[0], ...raw.cases.slice(2)] }),
    validateCaseTriggers({ ...raw, colour: 'red' }),
    validateCaseTriggers({ ...raw, prepositions: [...raw.prepositions, raw.prepositions[0]] }),
    validateCaseTriggers({ ...raw, verbs: [{ lemma: 'pomagati', present: 'Pomagam', takes: ['dat'], rule: 'dajalnik' }] }),
  ]
  check('case lens: a table with a rule no page has, the cases out of order, a key the format lacks, a preposition twice, a word not in lowercase: refused, each saying why', broken.every(b => !b.ok && b.errors.length > 0), broken.map(b => (b.ok ? 'ok' : b.errors.slice(0, 120))))

  // the personal pronouns as the grammar book's page has them (osebni-zaimki: kdo?, komu?, koga?, s kom?)
  const zaimki = JSON.parse(readFileSync(join(companion, 'grammar/sl/osebni-zaimki.json'), 'utf8')).table as { head: { sl: string }[]; rows: string[][] }
  const columns: Record<string, string> = { 'kdo?': 'nom', 'komu?': 'dat', 'koga?': 'acc', 's kom?': 'ins' }
  const missing: string[] = []
  for (const row of zaimki.rows) {
    const lemmas = row[0].split(/,\s*/)
    zaimki.head.forEach((h, k) => {
      const c = columns[h.sl]
      for (const form of row[k].split(/,\s*/).map(f => f.replace(/^[zs] /, ''))) {
        if (!raw.pronouns.some((p: any) => lemmas.includes(p.lemma) || lemmas.some(l => (p.forms.nom ?? []).includes(l)) ? (p.forms[c] ?? []).includes(form) && (lemmas.includes(p.lemma) || (p.forms.nom ?? []).some((n: string) => lemmas.includes(n))) : false))
          missing.push(`${row[0]} ${h.sl} ${form}`)
      }
    })
  }
  check('case lens: the table\'s personal pronouns have every form the grammar book\'s osebni-zaimki page gives, in its case (and the genitive and locative it leaves out)', missing.length === 0 && raw.pronouns.find((p: any) => p.lemma === 'on').forms.gen.join(' ') === 'njega ga' && raw.pronouns.find((p: any) => p.lemma === 'jaz').forms.loc.join(' ') === 'meni', missing)
  check('case lens: each verb of the table is given by its infinitive and its first person singular (pomagati, pomagam)', [...raw.verbs, ...raw.motion, ...raw.rest, ...raw.copulas, ...raw.transitive, ...raw.intransitive].every((x: any) => /ti$|či$/.test(x.lemma) && /m$/.test(x.present)), [...raw.verbs, ...raw.motion].filter((x: any) => !/ti$|či$/.test(x.lemma) || !/m$/.test(x.present)).map((x: any) => x.lemma))

  // every example of the table, analysed: the case, the number and the trigger it says
  const lens = repoLens()
  const examples: CaseExample[] = [
    ...raw.prepositions.flatMap((p: any) => p.examples), ...raw.negation.examples, ...raw.quantity.examples, ...raw.time.examples,
    ...raw.verbs.flatMap((x: any) => x.examples ?? []), ...raw.predicates.flatMap((x: any) => x.examples), ...Object.values(raw.roles).flatMap((r: any) => r.examples),
  ]
  const wrong = examples.flatMap(e => {
    const w = lens.at(e.sl, e.word, e.nth ?? 0, VILLAGE_NAMES)
    const ok = !!w && 'pick' in w && !!w.pick && w.pick.case === e.case && w.pick.number === e.number && w.sure !== 'ambiguous' && (e.trigger ? w.trigger?.word === e.trigger : true)
    return ok ? [] : [`${e.sl} ${e.word}: ${w && 'pick' in w ? `${w.pick?.case}.${w.pick?.number} ${w.sure} by ${w.trigger?.word}` : 'none'}`]
  })
  check(`case lens: every example of the table comes out as it says (${examples.length}: each preposition, the negation, quantity, the verbs, the words of feeling, the roles)`, examples.length >= 70 && wrong.length === 0, wrong)
  // a relative adverb that is a noun's form too (koder: wherever, and a curl) starts its clause: no case, no second subject
  const koder = lens.analyze('da, koder sonce hodi,', VILLAGE_NAMES).words
  check('case lens: koder (wherever; a curl too) in "da, koder sonce hodi," starts its clause: no case of its own, sonce the one subject', koder.map(w => `${w.word}:${w.pick?.case}:${w.sure}`).join(' ') === 'sonce:nom:decided', koder.map(w => `${w.word}:${w.pick?.case}:${w.sure}`))

  // --- the analysis on the hand-labelled sentences ------------------------------------------------------------------
  const gold = goldItems('gold')
  const dev = evaluate(lens, gold)
  const rules = new Set(gold.map(g => g.rule).filter(Boolean))
  const needs = ['rodilnik-predlogi', 'rodilnik-nikalnica', 'rodilnik-kolicina', 'kam-tozilnik', 'kje-mestnik-orodnik', 'dajalnik', 'orodnik', 'tozilnik', 'mestnik', 'imenovalnik', 'kdaj-cas', 'rodilnik']
  check(
    `case lens: the labelled sentences (${dev.items}, every rule, the ambiguities): ${summary(dev)}`,
    dev.items >= 150 && needs.every(r => rules.has(r)) && dev.right / dev.labelled >= 0.95 && dev.wrongDecided === 0 && dev.wrongLikely === 0 &&
      dev.triggers.right / dev.triggers.labelled >= 0.98 && dev.rules.right === dev.rules.labelled && dev.ambiguous.labelled >= 5 && dev.ambiguous.keptOpen === dev.ambiguous.labelled,
    { missingRules: needs.filter(r => !rules.has(r)), misses: dev.misses },
  )
  const held = evaluate(lens, goldItems('heldout'))
  check(`case lens: the held-out lines (${held.items}, labelled before the lens ran on them, its misses fixed since): ${summary(held)}`, held.items >= 100 && held.right / held.labelled >= 0.9 && held.wrongDecided === 0, held.misses)
  // the case trainer's bank (its gold annotations), once the trainer is merged
  const bank = trainerItems()
  if (bank) {
    const t = evaluate(lens, bank)
    // the two guard each other: the lens never contradicts a gold annotation (no wrong case said as decided or as likely, no
    // other deciding word), and a bank item the lens reads otherwise is one to look at again
    check(`case lens: the case trainer's bank (${t.items} "which case?" items, its gold annotations), never contradicting one: ${summary(t)}`, t.items >= 100 && t.right / t.labelled >= 0.93 && t.wrongDecided === 0 && t.wrongLikely === 0 && t.triggers.right === t.triggers.labelled, t.misses)
  } else console.log('ℹ️ case lens: the case trainer\'s bank not here (companion/cases/sl/bank.json): its check skipped')
  const held2 = evaluate(lens, goldItems('heldout2'))
  check(`case lens: the second held-out lines (${held2.items}, drawn after those fixes): ${summary(held2)}`, held2.items >= 100 && held2.right / held2.labelled >= 0.88 && held2.wrongDecided === 0, held2.misses)

  // --- every line of the content ------------------------------------------------------------------------------------
  const js = (d: string) => (existsSync(d) ? readdirSync(d).filter(f => f.endsWith('.json')).map(f => JSON.parse(readFileSync(join(d, f), 'utf8'))) : [])
  const culture = join(companion, 'cultures/primorska')
  const lines = contentLines(renderDeep({
    language: 'sl',
    scenes: [...js(join(companion, 'scenes')), ...js(join(culture, 'scenes'))],
    stories: js(join(culture, 'stories')),
    readings: js(join(culture, 'readings')),
    grammar: js(join(companion, 'grammar/sl')),
    packs: [...js(join(companion, 'packs')), ...js(join(culture, 'packs'))],
  }, addressee({ name: 'Jan', gender: 'male' })))
  const t0 = performance.now()
  const thrown: string[] = []
  let words = 0
  let decided = 0
  for (const l of lines) {
    try {
      const r = lens.analyze(l.text, VILLAGE_NAMES)
      words += r.words.length
      decided += r.words.filter(w => w.sure !== 'ambiguous').length
    } catch (e) {
      thrown.push(`${l.text}: ${(e as Error).message}`)
    }
  }
  const ms = performance.now() - t0
  check(`case lens: every line of the content analysed (${lines.length} lines, ${words} words with a case, ${Math.round((decided / words) * 100)}% decided or likely; ${(ms / lines.length).toFixed(2)} ms a line)`, lines.length > 3000 && thrown.length === 0 && ms / lines.length < 5 && decided / words > 0.8, thrown.slice(0, 5))

  // --- through the bridge --------------------------------------------------------------------------------------------
  const brez = await lookup({ w: 'mleka', line: 'Kavo pijem brez mleka.', en: 'I drink coffee without milk.' })
  const c0 = brez.case
  check(
    'GET /lookup: mleka in "Kavo pijem brez mleka." has its case: genitive singular, decided by brez (its place in the line), the rule rodilnik-predlogi, its readings',
    brez.entries?.[0]?.lemma === 'mleko' && c0?.pick?.case === 'gen' && c0.pick.number === 'sg' && c0.sure === 'decided' && c0.kind === 'preposition' && c0.trigger?.word === 'brez' &&
      c0.trigger.start === 11 && c0.trigger.end === 15 && c0.start === 16 && c0.rule === 'rodilnik-predlogi' && c0.readings.some((r: any) => r.case === 'gen'),
    c0,
  )
  const second = await lookup({ w: 'vasi', line: 'Hodi od vasi do vasi.', n: '1' })
  check('GET /lookup: n picks the same word\'s second place in its line (do vasi, not od vasi); vasi the genitive, the singular likely (the plural reads alike)', second.case?.trigger?.word === 'do' && second.case.i === 4 && second.case.sure === 'likely' && second.case.options.length === 2, second.case)
  const mize = await lookup({ w: 'mize', line: 'Vidim mize.' })
  check('GET /lookup: mize in "Vidim mize." the accusative plural, the object of vidim; the genitive singular it reads as too is out (nothing in the line asks for it)', mize.case?.pick?.case === 'acc' && mize.case.pick.number === 'pl' && mize.case.kind === 'object' && mize.case.trigger?.word === 'Vidim' && mize.case.out.some((o: any) => o.case === 'gen' && o.why === 'needs-trigger'), mize.case)
  const pod = await lookup({ w: 'mizo', line: 'Pod mizo!' })
  check('GET /lookup: mizo in "Pod mizo!" (no verb: where it is, or where it goes) keeps both readings, where it is (the instrumental) likely, never decided', pod.case && pod.case.sure === 'likely' && pod.case.pick?.case === 'ins' && pod.case.options.map((o: any) => o.case).join(' ') === 'ins acc', pod.case)
  const verb = await lookup({ w: 'pijem', line: 'Kavo pijem brez mleka.' })
  const prep = await lookup({ w: 'brez', line: 'Kavo pijem brez mleka.' })
  const noLine = await lookup({ w: 'mleka' })
  check('GET /lookup: a verb has no case; a preposition says what it decides (brez: mleka, the genitive); without its line a word has none', !verb.case && prep.case?.governs?.words?.[0]?.i === 3 && prep.case.governs.words[0].case === 'gen' && !prep.case.pick && !noLine.case, { verb: verb.case, prep: prep.case })
  const visit = await lookup({ w: 'acqua', line: "L'acqua è fredda.", language: 'it', base: 'en' })
  check('GET /lookup: an Italian word on a visit has no case (the lens is Slovene\'s)', !visit.case, visit.case)

  const many = await colours({ lines: ['Babica je v kuhinji.', 'Pod mizo!', 'Hvala, Jan!'] })
  const w0 = many.body.lines?.[0]?.words ?? []
  check(
    'POST /case-lens: each line\'s words with a case where they are, its case (Pod mizo: the likely one), how sure, the trigger\'s place',
    many.status === 200 && many.body.language === 'sl' && many.body.lines.length === 3 &&
      w0.map((w: any) => `${w.word}:${w.case}:${w.sure}`).join(' ') === 'Babica:nom:decided kuhinji:loc:decided' && w0[1].trigger.start === 10 &&
      many.body.lines[1].words[0].case === 'ins' && many.body.lines[1].words[0].sure === 'likely' && many.body.lines[2].words[0].kind === 'address',
    many.body,
  )
  const bad = await Promise.all([colours({}), colours({ lines: ['x'.repeat(401)] }), colours({ lines: Array.from({ length: 61 }, () => 'Grem.') }), colours({ lines: [3] })])
  check('POST /case-lens: no lines, a line too long, more than 60, a line not a string: 400', bad.every(b => b.status === 400 && typeof b.body.error === 'string'), bad.map(b => b.status))

  const repeat = await lookup({ w: 'mleka', line: 'Kavo pijem brez mleka.' })
  check(
    'GET /lookup: a look that counts as seen for its rule says so (seen: true: the app counts it in the case trainer\'s progress, the one count; rodilnik-predlogi by brez and by do, tozilnik the object); the same word of the same line again today, or a case left open (Pod mizo: likely), does not; nothing kept in <data>/app',
    c0?.seen === true && second.case?.seen === true && mize.case?.seen === true && repeat.case?.pick?.case === 'gen' && repeat.case.seen === undefined && pod.case?.seen === undefined &&
      !existsSync(join(dataDir, 'app/case-lens-seen.json')) && (await fetch(`${base}/case-lens/seen`, { headers: auth })).status === 404,
    { first: c0?.seen, second: second.case?.seen, mize: mize.case?.seen, repeat: repeat.case?.seen, pod: pod.case?.seen },
  )
  void CASE_KEYS
}
