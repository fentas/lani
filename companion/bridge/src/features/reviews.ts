// The learner databases for the app: the full state, and flashcard reviews. Reviews are graded by
// the app (self-rated), so they are persisted deterministically via update-db.py; Claude only hears
// a summary. Both take an optional language: another language's review deck and data (visits), the
// home language's by default (docs/DB_SCRIPTS.md, "Languages"). A review may say which of its cards asked a form of
// their word (forms: "Jaz ___ na klop." → sedem, GET /forms) and how that went: the tutor hears of the wrong ones in
// the summary, nothing more is written (the word's card got its quality; the app counts the form on its rule).
//
// A dialog's words (dialog-words.ts): after a dialog whose turns tested the learner's words, the app sends the right
// first answers it counts as reviews (results) and every word with how it went (dialog). The bridge checks again what
// may count (the card exists, wasn't reviewed today and is due within its window), so a stale or offline phone can't
// farm reviews; a word got wrong where its meaning was asked lowers its card gently (no review), a form slip touches no
// card (the app counts it on the grammar rule).
//
// What the learner typed or said (answers.ts): a review may carry each card's answer (answers: what was asked and how,
// what was typed or said, what was expected, the verdict, the grading hint), where in the app it came from (from: the
// review, I spy, the car, …) and when it was answered (answered_at, or an answer's own at); with the forms and a dialog's
// words, which carry theirs already, it goes into a rolling log of the last days once the review is persisted, one entry
// per card (a form asked joins its card's answer), and the tutor's note names the wrong ones. When Jan reports a card
// later without the exercise attached, the tool recent_answers shows what was typed (the lani-fix skill). An answer may
// say the cheat sheet was opened during it (looked_up: the cards opened, their grammar pages): it is logged with it, the
// note counts the topics looked up (the app gave such a right answer quality 4 at most). Nothing about the answers is
// ever a reason to refuse a review: a bad one is left out, the extras too.
//
// Counting cards (counting.ts): a review may say how its counting cards went (counting: each card's four steps, right or
// not, what was answered and what was right, the kind of a wrong one), read leniently too: each wrong step is written as
// a mistake of its kind (update-db.py `errors`: mistakes-db.json's counting_noun_gender, counting_dual, counting_plural,
// counting_from_five), the steps count as grammar practice, and the tutor's note names them.
//
// Forms cards (forms-cards.ts): a review may say how its forms cards went (forms_cards: each card's cells played, right
// or not, what was answered and what was right, the kind of a wrong one), read leniently the same way: each wrong cell
// is a mistake of its kind (forms_case_locative, forms_number_dual, forms_verb_person_3pl, forms_tense_past …), the
// cells count as grammar practice with the counting steps, and the tutor's note names them.
import { join } from 'node:path'
import { z } from 'zod'
import { answerLine, AnswerLog, fromLabel, fromOf, lookedUpNote, reviewAnswers, timeOf, type NewAnswer, type ReviewAnswer } from '../answers'
import { fail, ok } from '../tool'
import { countingErrors, countingNote, countingRuns } from '../counting'
import { formsCardsNote, formsErrors, formsRuns } from '../forms-cards'
import { countsInDialog, type Card } from '../dialog-words'
import type { FeatureFactory } from '../feature'
import { json, readJson } from '../http'
import { languageParam } from '../learner'
import { LANGUAGES } from '../learners'

/**
 * What GET /state says this bridge takes beyond the first API: an app sends a dialog's words only to one that says so
 * ("dialog-words"), and asks for a school intro's explanation as one clip (`lang` on /voice/say and /voice/prepare) only
 * from one that voices it so ("explanations"; an older bridge would voice the English as a Slovene line); it asks for
 * counting cards (POST /counting) and sends how they went (POST /reviews `counting`) only to one that makes them
 * ("counting"); a photo in the chat goes to one that keeps it (PUT /chat/images/<id>, "chat-images"; an older bridge
 * answers 404 and the message goes without it); it asks for forms cards (POST /forms-cards) and sends how they went
 * (POST /reviews `forms_cards`) only to one that makes them ("forms-cards"); it sends a case trainer's session to POST
 * /cases/done only to one that persists it ("cases"; an older one gets it as a session_end for the tutor to persist).
 */
export const STATE_FEATURES = ['dialog-words', 'explanations', 'counting', 'chat-images', 'forms-cards', 'cases'] as const

/**
 * What makes counting and forms cards is Slovene's (its numerals, cases and dual), and so is the case trainer (GET /cases,
 * POST /cases/done: features/cases.ts): a village in another language says none of them.
 */
const SLOVENE_ONLY = new Set<string>(['counting', 'forms-cards', 'cases'])

/** [STATE_FEATURES] as a bridge whose village speaks [language] says them. */
export const stateFeatures = (language: string): string[] => STATE_FEATURES.filter(f => language === 'sl' || !SLOVENE_ONLY.has(f))

/** A form of a card's word asked in a review: the slot ("pres.1sg"), the grammar page it counts on, how it went. */
const formAsked = z.object({
  item_id: z.string().min(1),
  key: z.string().trim().min(1).max(20),
  page: z.string().trim().min(1).max(64),
  right: z.boolean(),
  answer: z.string().trim().max(200).optional(),
  expected: z.string().trim().max(200).optional(),
})

const ref = z.string().trim().max(120).optional()

/**
 * A word of the learner's a dialog's turn tested: its card, the word as in the sentence, whether the first answer was
 * right, what was asked (its meaning: another word in its place; its form), whether the app counted it as a review, and
 * what was said against what was right.
 */
const dialogWord = z.object({
  item_id: z.string().min(1).max(128),
  word: z.string().trim().min(1).max(80),
  lemma: z.string().trim().min(1).max(80).optional(),
  right: z.boolean(),
  how: z.enum(['meaning', 'form']),
  review: z.boolean().optional(),
  said: z.string().trim().max(200).optional(),
  expected: z.string().trim().max(200).optional(),
})

/** The dialog the words were tested in: where and with whom, and its words (1-20). */
const dialogRun = z.object({
  scene: ref,
  happening: ref,
  dialog: ref,
  person: ref,
  title: ref,
  words: z.array(dialogWord).min(1).max(20),
})

const reviewRun = z
  .object({
    // with a dialog, the app may have no review to count (a word not due, one got wrong): results may be empty then
    results: z.array(z.object({ item_id: z.string().min(1), quality: z.number().int().min(0).max(5) })),
    duration_minutes: z.number().int().min(1).max(240),
    language: languageParam,
    forms: z.array(formAsked).max(50).optional(),
    dialog: dialogRun.optional(),
    // each card's answer (answers.ts reviewAnswer), where in the app (answers.ts REVIEW_FROM), when it was answered (ISO):
    // read leniently (answers.ts reviewAnswers, fromOf, timeOf), never a reason to refuse the review
    answers: z.unknown().optional(),
    from: z.unknown().optional(),
    answered_at: z.unknown().optional(),
    // how its counting cards went (counting.ts countingRuns): read leniently, never a reason to refuse the review
    counting: z.unknown().optional(),
    // how its forms cards went (forms-cards.ts formsRuns): read leniently too
    forms_cards: z.unknown().optional(),
  })
  .refine(r => r.dialog || r.results.length >= 1, { path: ['results'], message: 'at least one result (only a body with a dialog may have none)' })

/** " Forms: 3 asked, 1 wrong: sedi for sedem (glagoli-sedanjik)." for the tutor; empty without forms. */
export function formsNote(forms: z.infer<typeof formAsked>[] | undefined): string {
  if (!forms?.length) return ''
  const wrong = forms.filter(f => !f.right)
  const said = wrong.map(f => `${f.answer ? `"${f.answer}"` : '(no answer)'} for "${f.expected ?? '?'}" (${f.page}, ${f.key}, ${f.item_id})`)
  return ` Forms of words asked: ${forms.length}, ${wrong.length} wrong${said.length ? `: ${said.join('; ')}` : ''}.`
}

type Result = { item_id: string; quality: number }
type DialogRun = z.infer<typeof dialogRun>
type FormAsked = z.infer<typeof formAsked>

/** [o] without its undefined fields: an answer in the log has only what is known. */
const known = <T extends object>(o: T): T => Object.fromEntries(Object.entries(o).filter(([, v]) => v !== undefined)) as T

const verdictOf = (right: boolean) => (right ? 'correct' : 'wrong') as 'correct' | 'wrong'

/**
 * What of a review goes into the answer log, one entry per answer, at the time it was answered ([o].at: the review's,
 * unless an answer has its own):
 * - a review's cards: each result with the answer the app sent for it (else the quality alone: an app from before, or a
 *   place in the app with nothing to type, [o].from says which);
 * - a dialog's words: what was said against what was right, with a quality only for the cards the bridge counted
 *   ([o].results: the counted ones);
 * - the forms asked: with the answer of their card (its slot and grammar page added), else an entry of their own.
 */
export function answersOf(o: {
  results: Result[]; answers?: ReviewAnswer[]; forms?: FormAsked[]; dialog?: DialogRun; session_id?: string; language?: string; from?: string; at?: string
}): NewAnswer[] {
  const common = { from: o.from, session_id: o.session_id, language: o.language }
  const out: NewAnswer[] = []
  if (o.dialog) {
    const quality = new Map(o.results.map(x => [x.item_id, x.quality]))
    const where = whereOf(o.dialog)
    for (const w of o.dialog.words) {
      out.push(known<NewAnswer>({
        kind: 'dialog', item_id: w.item_id, variant: w.how, prompt: w.word, answer: w.said, expected: w.expected, verdict: verdictOf(w.right),
        quality: quality.get(w.item_id), where, ...common, at: o.at,
      }))
    }
  } else {
    for (const r of o.results) out.push(known<NewAnswer>({ kind: 'review', item_id: r.item_id, quality: r.quality, ...common, at: o.at }))
    // each answer joins its card's result (the first not answered yet; a card asked twice has two)
    const answered = new Set<NewAnswer>()
    for (const a of o.answers ?? []) {
      const fields = known({ ...a, at: a.at ?? o.at })
      const e = out.find(x => x.item_id === a.item_id && !answered.has(x))
      const into = e ? Object.assign(e, fields) : known<NewAnswer>({ kind: 'review', ...common, ...fields })
      if (!e) out.push(into)
      answered.add(into)
    }
  }
  // a form asked joins its card's answer (a review's, a dialog's word asked as a form), once
  for (const f of o.forms ?? []) {
    const e = out.find(x => x.item_id === f.item_id && x.form === undefined && (x.kind === 'review' || x.variant === 'form'))
    if (e) {
      Object.assign(e, { form: f.key, page: f.page })
      if (e.verdict === undefined) Object.assign(e, known({ answer: f.answer, expected: f.expected, verdict: verdictOf(f.right) }))
    } else {
      out.push(known<NewAnswer>({ kind: 'form', item_id: f.item_id, form: f.key, page: f.page, answer: f.answer, expected: f.expected, verdict: verdictOf(f.right), ...common, at: o.at }))
    }
  }
  return out
}

/**
 * " Answered wrong: vocab_kurim "kurim" for "kurim ogenj"; …" for the tutor's note: the wrong answers typed or said (at
 * most 5), not the forms (formsNote names them, with their page); empty without.
 */
export function wrongNote(answers: NewAnswer[]): string {
  const wrong = answers.filter(a => a.kind === 'review' && a.form === undefined && (a.verdict === 'wrong' || a.verdict === 'almost') && a.answer !== undefined)
  if (!wrong.length) return ''
  const said = wrong.slice(0, 5).map(a => `${a.item_id} ${a.answer ? `"${a.answer}"` : '(no answer)'}${a.expected ? ` for "${a.expected}"` : ''}${a.verdict === 'almost' ? ' (almost)' : ''}`)
  return ` Answered wrong: ${said.join('; ')}${wrong.length > 5 ? `; ${wrong.length - 5} more` : ''} (recent_answers has them).`
}

/**
 * What of a dialog's body may be written [today], with the learner's cards [items]: the [results] that count (each card
 * once, reviewed not yet today, due within its window: countsInDialog), the cards to lower gently (a word got wrong
 * where its meaning was asked: once a day, not a card counted as a review in the same dialog) and what was dropped.
 */
export function dialogReviews(results: Result[], d: DialogRun, items: Record<string, Card>, today: string): { counted: Result[]; lowered: string[]; skipped: string[] } {
  const counted: Result[] = []
  const skipped = new Set<string>()
  const seen = new Set<string>()
  for (const x of results) {
    if (seen.has(x.item_id) || !countsInDialog(items[x.item_id], today)) {
      skipped.add(x.item_id)
      continue
    }
    seen.add(x.item_id)
    counted.push(x)
  }
  const lowered: string[] = []
  for (const w of d.words) {
    if (w.right || w.how !== 'meaning' || seen.has(w.item_id) || lowered.includes(w.item_id)) continue
    const c = items[w.item_id]
    if (!c || c.last_lapse === today) skipped.add(w.item_id)
    else lowered.push(w.item_id)
  }
  return { counted, lowered, skipped: [...skipped].filter(id => !seen.has(id) && !lowered.includes(id)) }
}

/** "Babica Micka, Babica kuha kosilo (v-kuhinji/kosilo, dialog kosilo-zlica)": who and where, for the notes. */
const whereOf = (d: DialogRun): string => {
  const at = [d.scene && d.happening ? `${d.scene}/${d.happening}` : d.scene, d.dialog ? `dialog ${d.dialog}` : undefined].filter(Boolean).join(', ')
  return [d.person, d.title].filter(Boolean).join(', ') + (at ? ` (${at})` : '') || 'a dialog'
}

export const reviews: FeatureFactory = ({ cfg, learner, channel, outbox, culture }) => {
  // what the learner typed or said lately (answers.ts): a rolling log beside the learner's data
  const log = new AnswerLog(join(cfg.appDir, 'answers.json'))

  /** A dialog's words (the body has `dialog`): written as far as they count, the tutor told in one line. */
  async function handleDialog(
    results: Result[], d: DialogRun, duration_minutes: number, forms: FormAsked[] | undefined, language: string | undefined, sent: { from?: string; at?: string },
  ): Promise<Response> {
    const state = learner.state(language)
    const today: string = state.computed.today
    const items = (state.databases?.spaced_repetition?.items ?? {}) as Record<string, Card>
    const { counted, lowered, skipped } = dialogReviews(results, d, items, today)
    const correct = d.words.filter(w => w.right).length
    const total = d.words.length
    const deck = language ? ` (language "${language}")` : ''
    // each word: ✓ or ✗, and what came of it
    const fate = (w: DialogRun['words'][number]): string => {
      const as = w.lemma && w.lemma !== w.word ? ` (${w.lemma})` : ''
      const what = !items[w.item_id] ? `no card ${w.item_id}`
        : counted.some(x => x.item_id === w.item_id) ? 'counted as a review'
        : lowered.includes(w.item_id) ? 'lowered gently, due tomorrow'
        : w.how === 'form' && !w.right ? `a form slip${w.said ? `: "${w.said}"${w.expected ? ` for "${w.expected}"` : ''}` : ''}, on its rule only`
        : skipped.includes(w.item_id) ? (w.right ? 'skipped: not due, or reviewed today' : 'lowered today already')
        : w.right ? 'not a review (not due)' : 'nothing'
      return `${w.word}${as} ${w.right ? '✓' : '✗'} ${what}`
    }
    const words = d.words.map(fate).join('; ')
    // a form the words name already (a word asked as a form: the car's quiz sends it in both) isn't named twice
    const otherForms = formsNote(forms?.filter(f => !d.words.some(w => w.item_id === f.item_id && w.how === 'form')))
    if (!counted.length && !lowered.length) {
      // nothing counted: no quality in the log (the qualities the app sent were not given)
      log.add(answersOf({ results: counted, forms, dialog: d, language, ...sent }))
      await channel.notify(`App: words in a dialog${deck}, ${whereOf(d)}: ${words}. Nothing to persist. No reply needed.${otherForms}`, {
        kind: 'dialog_words', conversation_id: 'main', msg_id: `dw-${Date.now().toString(36)}`,
      })
      return json({ correct, total, skipped, lowered })
    }
    const payload = {
      session_id: state.computed.next_session_id,
      date: today,
      ...(language ? { language } : {}),
      duration_minutes,
      command_used: '/lani-app-dialog-words',
      skills_practiced: ['vocabulary'],
      skill_scores: { vocabulary: { exercises: total, correct, time_minutes: duration_minutes } },
      // a lowering is no review: update-db.py's gentle path (its quality, none here, would be ignored)
      review_results: [...counted, ...lowered.map(item_id => ({ item_id, gentle: true }))],
      session_notes: `The learner's words in a dialog in the Lani app: ${whereOf(d)}. ${correct}/${total} right.`,
    }
    const u = learner.update(payload)
    if (!u.ok) return json({ error: u.output }, 500)
    log.add(answersOf({ results: counted, forms, dialog: d, session_id: payload.session_id, language, ...sent }))
    await channel.notify(
      `App: words in a dialog${deck} (already persisted as ${payload.session_id}), ${whereOf(d)}: ${words}. No reply needed.${otherForms}`,
      { kind: 'dialog_words', conversation_id: 'main', msg_id: payload.session_id },
    )
    return json({ session_id: payload.session_id, correct, total, skipped, lowered, output: u.output, ...(language ? { language } : {}) })
  }

  async function handleReviews(req: Request): Promise<Response> {
    const r = reviewRun.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    const { results, duration_minutes } = r.data
    const language = learner.other(r.data.language)
    // where in the app and when it was answered (the app's time; the arrival's without one)
    const sent = { from: fromOf(r.data.from), at: timeOf(r.data.answered_at) }
    if (r.data.dialog) return handleDialog(results, r.data.dialog, duration_minutes, r.data.forms, language, sent)
    const state = learner.state(language)
    const correct = results.filter(x => x.quality >= 3).length
    // the counting cards' steps (the home language's: their numerals are Slovene's): grammar, each wrong one a mistake
    const counted = language ? [] : countingRuns(r.data.counting)
    // the forms cards' cells (the home language's: their tables are the village dictionary's), grammar too
    const tables = language ? [] : formsRuns(r.data.forms_cards)
    const cells = tables.flatMap(t => t.cells)
    const steps = [...counted.flatMap(c => c.steps), ...cells]
    const errors = [...countingErrors(counted), ...formsErrors(tables)]
    const countingWrong = countingErrors(counted).length
    const payload = {
      session_id: state.computed.next_session_id,
      date: state.computed.today,
      ...(language ? { language } : {}),
      duration_minutes,
      command_used: '/lani-app-review',
      skills_practiced: steps.length ? ['vocabulary', 'grammar'] : ['vocabulary'],
      skill_scores: {
        vocabulary: { exercises: results.length, correct, time_minutes: duration_minutes },
        ...(steps.length ? { grammar: { exercises: steps.length, correct: steps.filter(s => s.right).length, time_minutes: 0 } } : {}),
      },
      review_results: results,
      ...(errors.length ? { errors } : {}),
      session_notes: `Flashcard review in the Lani app (self-rated).${counted.length ? ` ${counted.length} counting card(s), ${countingWrong} wrong step(s).` : ''}${tables.length ? ` ${tables.length} forms card(s), ${cells.filter(c => !c.right).length} wrong cell(s).` : ''}`,
    }
    const u = learner.update(payload)
    if (!u.ok) return json({ error: u.output }, 500)
    // what was typed or said, into the log once the review is persisted (a retry after an error isn't logged twice)
    const given = reviewAnswers(r.data.answers)
    const answered = answersOf({ results, answers: given, forms: r.data.forms, session_id: payload.session_id, language, ...sent })
    log.add(answered)
    const weak = results.filter(x => x.quality < 3).map(x => x.item_id)
    const deck = language ? ` of ${LANGUAGES[language] ?? language} cards (language "${language}")` : ''
    const place = sent.from && sent.from !== 'review' ? ` in ${fromLabel(sent.from)}` : ''
    await channel.notify(
      `App review${deck}${place} finished (already persisted as ${payload.session_id}): ${correct}/${results.length} recalled in ${duration_minutes} min.${weak.length ? ` Struggled with: ${weak.join(', ')}.` : ''}${wrongNote(answered)}${formsNote(r.data.forms)}${countingNote(counted)}${formsCardsNote(tables)}${lookedUpNote(given)}`,
      { kind: 'review_done', conversation_id: 'main', msg_id: payload.session_id },
    )
    return json({ session_id: payload.session_id, correct, total: results.length, output: u.output, ...(language ? { language } : {}) })
  }

  return {
    routes: [
      { method: 'POST', path: '/reviews', handle: ({ req, url }) => outbox.once(req, url.pathname, handleReviews) },
      {
        method: 'GET',
        path: '/state',
        // ?language=it: that language's databases (the same shape); always with every language's summary (languages)
        // and what this bridge takes (features: an app sends a dialog's words only when "dialog-words" is there)
        handle: ({ url }) => {
          const l = languageParam.safeParse(url.searchParams.get('language') ?? undefined)
          if (!l.success) return json({ error: 'language: a language code, e.g. "it"' }, 400)
          return json({ ...JSON.parse(learner.stateJson(l.data)), features: stateFeatures(culture.manifest?.language ?? 'sl') })
        },
      },
    ],
    tools: [
      {
        name: 'recent_answers',
        description:
          `What the learner typed or said lately in the app (the last ${log.kept.days} days, at most ${log.kept.max} answers), newest first: each card a review counted, where in the app (the review, the grammar book, a building's words run, a challenge, I spy, the car), with its exercise (how it was asked, what was typed or heard, what was expected, the verdict, the grading hint, the quality it gave the card; nothing typed where there is nothing to type, as the car's ✓/✗), the forms of words asked (their slot and grammar page), and a dialog's word turns; "📋 looked up …": the cheat sheet was open during it (the grammar pages of the cards opened). Use it when Jan says a card or an answer was marked wrong but the message has no about_exercise: look the card up (card_id, its id in spaced-repetition.json) or search a word (text, in what was asked, typed or expected; diacritics optional: "zivjo" finds "živjo"). The lani-fix skill uses it. looked_up: true lists what Jan answered with the cheat sheet open, what they look up.`,
        inputSchema: {
          type: 'object',
          properties: {
            card_id: { type: 'string', description: 'a review card id, e.g. vocab_kurim' },
            text: { type: 'string', description: 'a word in what was asked, typed or expected, e.g. kurim' },
            wrong_only: { type: 'boolean', description: 'only answers marked wrong or almost (by the verdict: a right answer whose quality hints lowered is not wrong)' },
            looked_up: { type: 'boolean', description: 'only answers given with the cheat sheet open (📋 Plonk listek): what Jan looked up' },
            language: { type: 'string', description: 'another language\'s deck ("it"); "" for the home language only' },
            limit: { type: 'number', description: 'at most this many (default 20, at most 100)' },
          },
        },
        handle: async (args: unknown) => {
          const r = recentIn.safeParse(args ?? {})
          if (!r.success) return fail(z.prettifyError(r.error))
          const found = log.find({ item_id: r.data.card_id, text: r.data.text, wrong: r.data.wrong_only, looked_up: r.data.looked_up, language: r.data.language, limit: r.data.limit })
          if (!found.length) {
            const all = log.all()
            const what = `${r.data.card_id ? ` for ${r.data.card_id}` : ''}${r.data.text ? ` with "${r.data.text}"` : ''}${r.data.wrong_only ? ' marked wrong' : ''}${r.data.looked_up ? ' given with the cheat sheet open' : ''}`
            const held = !all.length
              ? 'The log is empty: nothing was answered since the bridge began keeping answers, or in those days.'
              : `The log holds ${all.length} answer(s) since ${all[0].at.slice(0, 16).replace('T', ' ')} UTC${r.data.card_id ? `: ${r.data.card_id} wasn't reviewed, asked as a form or met in a dialog since (another id? try text)` : ''}.`
            return ok(`No answers${what} in the last ${log.kept.days} days. ${held} Ask Jan what they typed.`)
          }
          return ok([`${found.length} answer(s), newest first (times UTC):`, ...found.map(answerLine)].join('\n'))
        },
      },
    ],
  }
}

const recentIn = z.object({
  card_id: z.string().trim().min(1).max(128).optional(),
  text: z.string().trim().min(1).max(80).optional(),
  wrong_only: z.boolean().optional(),
  looked_up: z.boolean().optional(),
  language: z.string().trim().max(8).optional(),
  limit: z.number().int().min(1).max(100).optional(),
})
