// The case trainer ("🧩 Skloni · Cases", case-bank.ts; companion/README.md "Case trainer"): its bank for the app (GET /cases:
// the curated items and the tutor's, the rules, the frames, the adjectives and nouns, the tutor's assignment), a session
// the app played persisted as grammar practice (POST /cases/done: update-db.py, no LLM; the tutor hears a summary), and
// the tutor's tools: get_cases (the learner's mastery per case and per rule, the bank), publish_case_items and
// remove_case_item (items checked as the bank is), assign_cases (a session focused on a case or a rule).
import { z } from 'zod'
import { bankCounts, CASE_EN, CASES, CASES_LANGUAGES, caseItem, casesOf, checkItems, type CaseCheck, type CaseItem } from '../case-bank'
import type { FeatureFactory } from '../feature'
import { LineIndex } from '../forms'
import { json, readJson } from '../http'
import { LANGUAGES } from '../learners'
import { localDate } from '../rhythm'
import { badNote, fail, noteIn, ok } from '../tool'

const instructions = `
Case trainer ("🧩 Skloni · Cases"): short sessions (10 items, 8 for a child) on the six cases, the kinds interleaved,
one at a time with feedback at once. "Kateri sklon? · Which case?": a real sentence, one word highlighted, the learner
picks its case by the school's question words (1. kdo? kaj? 2. koga? česa? 3. komu? čemu? 4. koga? kaj? 5. pri kom? pri
čem? 6. s kom? s čim?), then "Zakaj? · Why?": taps the word that decides it (brez, iz, k, v + a verb of motion, ni,
nimam, pet, kozarec, pomagati …) or says nothing decides it (the subject, the object); then the rule's line and its
grammar page. "Kje ali kam?": v/na + the locative or the accusative (sem v šoli, grem v šolo), pri or k, v or na, od
kod, over the village's places (its scene's picture above it). "⚡ Sklonski vihar · Case storm": short phrases sorted into
the six cases against the clock (60 s), a medal at the end. "Sklanjaj! · Decline it": the learner's own nouns (their
cards with forms) and the bank's adjectives and possessives typed into a frame with a known trigger ("Ni ___ (miza)",
"Grem k ___ (moj brat)"), graded as any typed answer (č, š, ž forgotten is almost, a wrong ending wrong; a spoken form is
right). Items are picked towards the weak cases and rules (the learner's mistakes count from the start); a missed item
comes back in the next sessions (the same day, then 1, 3, 7 and 16 days). The rule ids are the grammar pages that
explain them (rodilnik-predlogi, rodilnik-nikalnica, rodilnik-kolicina, kam-tozilnik, kje-mestnik-orodnik, dajalnik,
orodnik, tozilnik, mestnik, skloni for the subject).
cases_done: a session the app played, ALREADY persisted by the bridge as grammar practice (and writing for the typed
ones), its wrong answers in the mistakes database under the learner's own pattern ids where one fits (else
cases_<case>_<rule>): don't persist it again. Reply only when it helps (a case that keeps failing, a trigger mixed up).
get_cases shows the learner's mastery per case and per rule (stars 0-5, right of the last answers, how many sessions),
what comes back, the bank's size and the assignment. To focus Jan's next session (a case or a rule they keep missing,
after you explained it in chat), assign_cases {focus: a case id (rodilnik …) or a rule id (rodilnik-predlogi …), note}:
Home and the trainer offer it until it is played; focus null takes it back. publish_case_items adds items in the bank's
format (lani.cases/v0: a sentence, the word's token, lemma, case, number, the rule's key, the trigger's token or the role,
en, de and it), checked as the bank is (each form against the dictionary, the trigger against its rule, the pages):
add sentences of a topic Jan meets (their words, their village) or to cover a rule thin in the bank; ids of yours,
not the bank's. An item Jan reports wrong (about_exercise.source kind cases: its item, by tutor when yours): yours,
publish it again fixed or remove_case_item; the bank's, a note for the developer (the lani-fix skill); a decline's
form is the dictionary's (card.py show). Machine-written: the native check marks spoken forms;
never call a spoken form wrong.
`.trim()

/** A session as the app reports it (data/CaseTrainer.kt wire): its answers, the errors ready for update-db.py, the mastery after. */
const answerIn = z.object({
  kind: z.enum(['which', 'place', 'decline']),
  item: z.string().trim().min(1).max(120),
  case: z.enum(CASES),
  number: z.enum(['sg', 'du', 'pl']),
  rule: z.string().trim().min(1).max(64),
  verdict: z.enum(['correct', 'almost', 'wrong']),
  /** The "Zakaj?" step: the trigger tapped right, wrong, or not asked. */
  why: z.enum(['right', 'wrong', 'none']).optional(),
  answer: z.string().max(200).optional(),
  expected: z.string().max(200).optional(),
  sentence: z.string().max(200).optional(),
  looked_up: z.boolean().optional(),
})
const errorIn = z.object({
  pattern_id: z.string().regex(/^[a-z0-9][a-z0-9_]{0,80}$/),
  category: z.string().max(40).default('grammar'),
  subcategory: z.string().max(40).optional(),
  description: z.string().max(300).optional(),
  severity: z.enum(['critical', 'moderate', 'minor']).default('moderate'),
  your_answer: z.string().max(200).default(''),
  correct_answer: z.string().max(200).default(''),
  context: z.string().max(300).optional(),
})
const masteryIn = z.record(z.string().max(64), z.object({ right: z.number().int().min(0).max(100), of: z.number().int().min(0).max(100), stars: z.number().int().min(0).max(5).optional() }))
export const casesDone = z.object({
  client_id: z.string().optional(),
  duration_minutes: z.number().int().min(1).max(240),
  focus: z.string().trim().max(64).nullish(),
  answers: z.array(answerIn).min(1).max(40),
  errors: z.array(errorIn).max(40).default([]),
  mastery: masteryIn.optional(),
  rules: masteryIn.optional(),
  language: z.string().regex(/^[a-z]{2}$/).optional(),
  machine_written: z.boolean().optional(),
  /** "⚡ Sklonski vihar · Case storm": phrases sorted into the six cases against the clock, nothing more asked. */
  storm: z.boolean().optional(),
})
export type CasesDone = z.infer<typeof casesDone>

/** What a session done says in a sentence, for the session's notes and the tutor. */
export function casesNote(d: CasesDone): string {
  const right = d.answers.filter(a => a.verdict === 'correct').length
  const almost = d.answers.filter(a => a.verdict === 'almost').length
  const kinds = (['which', 'place', 'decline'] as const).map(k => [k, d.answers.filter(a => a.kind === k).length] as const).filter(([, n]) => n > 0)
  const names = { which: 'which case', place: 'kje ali kam', decline: 'decline it' }
  const wrong = d.answers.filter(a => a.verdict === 'wrong').slice(0, 6).map(a => `${a.expected ?? a.item} (${CASE_EN[a.case]}${a.number === 'sg' ? '' : ` ${a.number}`}, ${a.rule}${a.answer ? `: "${a.answer.slice(0, 40)}"` : ''})`)
  const why = d.answers.filter(a => a.why && a.why !== 'none')
  const whyWrong = why.filter(a => a.why === 'wrong').length
  return (
    `${d.storm ? 'Played a case storm in the Lani app (⚡ Sklonski vihar, phrases sorted into the six cases against the clock)' : `Practised the cases in the Lani app (🧩 Skloni · Cases${d.focus ? `, focused on ${d.focus}` : ''})`}: ${right}/${d.answers.length} right${almost ? `, ${almost} almost` : ''}` +
    ` (${kinds.map(([k, n]) => `${n} ${names[k]}`).join(', ')})` +
    (why.length ? `; the deciding word tapped right ${why.length - whyWrong}/${why.length}` : '') +
    (wrong.length ? `. Wrong: ${wrong.join('; ')}` : '') +
    '.'
  )
}

/**
 * The update-db.py report of a session (docs/DB_SCRIPTS.md): grammar practice (Kateri sklon?, Kje ali kam?), writing
 * for the forms typed (Sklanjaj!), the errors as the app made them, the rules practised as its topics, the weakest case
 * as the next focus. In [session]'s data (another language's, with its code).
 */
export function casesPayload(d: CasesDone, session: { session_id: string; date: string; language?: string }) {
  const typed = d.answers.filter(a => a.kind === 'decline')
  const chosen = d.answers.filter(a => a.kind !== 'decline')
  const right = (as: typeof d.answers) => as.filter(a => a.verdict === 'correct').length
  const minutes = (n: number) => Math.max(0, Math.round((d.duration_minutes * n) / d.answers.length))
  const skill_scores: Record<string, { exercises: number; correct: number; time_minutes: number }> = {}
  if (chosen.length) skill_scores.grammar = { exercises: chosen.length, correct: right(chosen), time_minutes: minutes(chosen.length) }
  if (typed.length) skill_scores.writing = { exercises: typed.length, correct: right(typed), time_minutes: minutes(typed.length) }
  const weakest = Object.entries(d.mastery ?? {})
    .filter(([, m]) => m.of > 0)
    .sort((a, b) => a[1].right / a[1].of - b[1].right / b[1].of)[0]
  return {
    session_id: session.session_id,
    date: session.date,
    ...(session.language ? { language: session.language } : {}),
    duration_minutes: d.duration_minutes,
    command_used: '/lani-app-cases',
    skills_practiced: Object.keys(skill_scores),
    skill_scores,
    errors: d.errors.map(e => ({ ...e, subcategory: e.subcategory ?? 'cases' })),
    topics_covered: [...new Set(d.answers.map(a => a.rule))],
    ...(weakest && weakest[1].right / weakest[1].of < 0.7 ? { focus_next_session: [`The ${CASE_EN[weakest[0] as keyof typeof CASE_EN] ?? weakest[0]} (${weakest[0]}): ${weakest[1].right}/${weakest[1].of} lately in the case trainer`] } : {}),
    session_notes: casesNote(d),
  }
}

export const cases: FeatureFactory = ({ cases: store, learner, channel, events, outbox, game, grammar, scenes, dictionary, culture }) => {
  const language = culture.manifest?.language ?? 'sl'
  const slovene = CASES_LANGUAGES.has(language)
  /** What a published item is checked against: the dictionary and forms book, the grammar book, the scenes. */
  const check = (): CaseCheck => {
    const pages = grammar.all()
    return {
      forms: { dict: dictionary.dict, book: dictionary.book, pages: new Set(pages.map(p => p.id)), lines: new LineIndex([]), language },
      pages: new Map(pages.map(p => [p.id, p.level])),
      scenes: new Set(scenes.all().map(s => s.id)),
    }
  }
  const state = () => (game.read()?.state as { cases?: unknown } | undefined)?.cases

  async function handleDone(req: Request): Promise<Response> {
    if (!slovene) return json({ error: `the case trainer is Slovene's; this village speaks "${language}"` }, 400)
    const r = casesDone.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    const d = r.data
    const other = learner.other(d.language)
    const st = learner.state(other)
    const payload = casesPayload(d, { session_id: st.computed.next_session_id, date: st.computed.today, language: other })
    const u = learner.update(payload)
    if (!u.ok) return json({ error: u.output }, 500)
    const done = store.done(d.focus ?? undefined, st.computed.today)
    const deck = other ? ` in ${LANGUAGES[other] ?? other} (language "${other}")` : ''
    await channel.notify(`${payload.session_notes.replace(/\.$/, '')}${deck}; ${d.duration_minutes} min.${done ? ` Your assignment (${done.focus}) is done.` : ''} Already persisted as ${payload.session_id}.`, {
      kind: 'cases_done',
      conversation_id: 'main',
      msg_id: payload.session_id,
    })
    if (done) events.emit({ type: 'cases_assigned', focus: null })
    return json({ session_id: payload.session_id, skills: payload.skills_practiced, errors: payload.errors.length, output: u.output, ...(done ? { assignment_done: done.focus } : {}), ...(other ? { language: other } : {}) })
  }

  return {
    instructions,
    routes: [
      {
        method: 'GET',
        path: '/cases',
        addressed: true,
        handle: () => {
          const s = slovene ? store.served() : null
          return s ? json(s) : json({ error: `no case trainer for "${language}"` }, 404)
        },
      },
      { method: 'POST', path: '/cases/done', handle: ({ req, url }) => outbox.once(req, url.pathname, handleDone) },
    ],
    tools: [
      {
        name: 'get_cases',
        description:
          "The case trainer (🧩 Skloni · Cases): the learner's mastery per case (imenovalnik … orodnik) and per rule (the grammar page: rodilnik-predlogi, kam-tozilnik …) as right of their last answers, the items coming back and when, the rules the case lens showed them, the sessions, the bank's size by kind, case, number and level, your items and the open assignment.",
        inputSchema: { type: 'object', properties: {} },
        handle: () => {
          if (!slovene) return fail(`the case trainer is Slovene's; this village speaks "${language}"`)
          const items = store.items()
          return ok(JSON.stringify({
            learner: casesOf(state()) ?? { note: 'no session played yet' },
            bank: { ...bankCounts(items), tutor_items: items.filter(i => i.source === 'tutor').map(i => i.id) },
            assigned: store.assigned() ?? undefined,
          }, null, 2))
        },
      },
      {
        name: 'publish_case_items',
        description:
          "Add items to the case trainer's bank (lani.cases/v0, companion/README.md \"Case trainer\"): items: 1-30 {id (kebab-case, not the bank's), kind which|place, level A1|A2|B1, sl (one sentence, ≤14 words), en, de, it, word (its token index: the sentence split at spaces, from 0), lemma, pos noun|adj|pron|name, gender (an adjective's noun's), case imenovalnik|rodilnik|dajalnik|tozilnik|mestnik|orodnik, number sg|du|pl, why (a rule key of get_cases' bank rules: brez, iz, nikalnica, k, v-na-kam, v-na-kje, z-s, pod-za-kje, object, subject …), trigger (the deciding word's token, or null with role subject|object), also (more tokens right to tap: the verb beside v/na), ambiguous (other slots of the same form: \"nom.pl\"), note {en,de,it} (how the sentence decides an ambiguous form); a place item also question kje-kam|v-na|pri-k|od-kod, gap [from,to], options (2-3), answer, cue, scene}. Each is checked as the bank is: the form against the dictionary, the trigger against its rule, the pages and scenes. Publishing again under an id replaces your item. Returns the problems instead of publishing if any.",
        inputSchema: {
          type: 'object',
          properties: {
            items: { type: 'array', items: { type: 'object' }, description: 'The items (1-30)' },
            note: { type: 'string', description: 'One-line announcement shown in the app' },
          },
          required: ['items'],
        },
        handle: args => {
          if (!slovene) return fail(`the case trainer is Slovene's; this village speaks "${language}"`)
          const list = Array.isArray(args.items) ? args.items : []
          if (list.length < 1 || list.length > 30) return fail('items: 1 to 30 items; nothing published')
          const parsed: CaseItem[] = []
          const problems: string[] = []
          list.forEach((x, i) => {
            const v = caseItem.safeParse(x)
            if (v.success) parsed.push(v.data)
            else problems.push(`items[${i}] (${(x as { id?: string })?.id ?? '?'}): ${z.prettifyError(v.error)}`)
          })
          const bank = store.curated()
          if (!bank) return fail('the bank (companion/cases/sl/bank.json) is missing: nothing published')
          if (!problems.length) problems.push(...checkItems(parsed, bank.rules, check()).problems)
          const ids = parsed.map(p => p.id)
          if (new Set(ids).size !== ids.length) problems.push('two items share an id')
          if (problems.length) return fail(`items invalid, nothing published:\n${problems.map(p => `✖ ${p}`).join('\n')}`)
          const note = noteIn.safeParse(args.note)
          if (!note.success) return badNote()
          const r = store.publish(parsed)
          if ('error' in r) return fail(`${r.error}; nothing published`)
          events.emit({ type: 'cases_published', ids, note: note.data ?? undefined })
          return ok(`published ${parsed.length} case item(s): ${r.added.length} new, ${r.replaced.length} replaced (${ids.join(', ')}); the app's trainer has them at its next session`)
        },
      },
      {
        name: 'remove_case_item',
        description: 'Take back one of your case trainer items (publish_case_items) by its id; the bank\'s own can\'t be removed.',
        inputSchema: { type: 'object', properties: { id: { type: 'string' } }, required: ['id'] },
        handle: args => {
          const itemId = typeof args.id === 'string' ? args.id : ''
          if (!store.remove(itemId)) return fail(`no item of yours "${itemId}" (get_cases lists yours)`)
          events.emit({ type: 'cases_published', ids: [], removed: itemId })
          return ok(`removed your case item ${itemId}`)
        },
      },
      {
        name: 'assign_cases',
        description:
          "Assign the learner a case trainer session focused on one case or rule: focus = a case id (imenovalnik, rodilnik, dajalnik, tozilnik, mestnik, orodnik) or a rule id (the grammar page: rodilnik-predlogi, rodilnik-nikalnica, rodilnik-kolicina, kam-tozilnik, kje-mestnik-orodnik, dajalnik, orodnik, tozilnik, mestnik, skloni), with a one-line note in the learner's base. Home and the trainer offer it until a session with that focus is played (cases_done says so); focus null takes it back.",
        inputSchema: {
          type: 'object',
          properties: {
            focus: { type: ['string', 'null'], description: 'A case id or a rule id; null takes the assignment back' },
            note: { type: 'string', description: 'Why, in a line (shown with it)' },
          },
          required: ['focus'],
        },
        handle: args => {
          if (!slovene) return fail(`the case trainer is Slovene's; this village speaks "${language}"`)
          if (args.focus === null || args.focus === '') {
            store.assign(null)
            events.emit({ type: 'cases_assigned', focus: null })
            return ok('assignment taken back')
          }
          const focus = typeof args.focus === 'string' ? args.focus.trim() : ''
          const rules = new Set(Object.values(store.curated()?.rules ?? {}).map(r => r.page))
          if (!(CASES as readonly string[]).includes(focus) && !rules.has(focus)) {
            return fail(`focus "${focus}" is neither a case (${CASES.join(', ')}) nor a rule of the bank (${[...rules].sort().join(', ')})`)
          }
          const note = typeof args.note === 'string' ? args.note.trim().slice(0, 200) : undefined
          store.assign({ focus, ...(note ? { note } : {}), at: localDate(new Date()) })
          events.emit({ type: 'cases_assigned', focus, note })
          return ok(`assigned a case trainer session on ${focus}: Home and the trainer offer it until it is played`)
        },
      },
    ],
  }
}

