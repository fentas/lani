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
// what was typed or said, what was expected, the verdict, the grading hint); with the forms and a dialog's words, which
// carry theirs already, it goes into a rolling log of the last days once the review is persisted, and the tutor's note
// names the wrong ones. When Jan reports a card later without the exercise attached, the tool recent_answers shows what
// was typed (the lani-fix skill).
import { join } from 'node:path'
import { z } from 'zod'
import { answerLine, AnswerLog, reviewAnswers, type Answer, type ReviewAnswer } from '../answers'
import { fail, ok } from '../channel'
import { countsInDialog, type Card } from '../dialog-words'
import type { FeatureFactory } from '../feature'
import { json, readJson } from '../http'
import { languageParam } from '../learner'
import { LANGUAGES } from '../learners'

/** What GET /state says this bridge takes beyond the first API: an app sends a dialog's words only to one that says so. */
export const STATE_FEATURES = ['dialog-words'] as const

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
    // each card's answer (answers.ts reviewAnswer): a bad one is left out of the log, never a reason to refuse the review
    answers: z.array(z.unknown()).max(200).optional(),
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

/**
 * What of a review goes into the answer log: each card's answer (with the quality it gave the card), the forms asked
 * whose card has no answer of its own there, and a dialog's words (what was said, against what was right).
 */
export function answersOf(o: { results: Result[]; answers?: ReviewAnswer[]; forms?: FormAsked[]; dialog?: DialogRun; session_id?: string; language?: string }): Omit<Answer, 'at'>[] {
  const quality = new Map(o.results.map(x => [x.item_id, x.quality]))
  const common = { ...(o.session_id ? { session_id: o.session_id } : {}), ...(o.language ? { language: o.language } : {}) }
  const out: Omit<Answer, 'at'>[] = []
  const answered = new Set<string>()
  for (const a of o.answers ?? []) {
    answered.add(a.item_id)
    const q = quality.get(a.item_id)
    out.push({ kind: 'review', ...a, ...(q !== undefined ? { quality: q } : {}), ...common })
  }
  for (const f of o.forms ?? []) {
    if (answered.has(f.item_id)) continue
    out.push({ kind: 'form', item_id: f.item_id, variant: f.key, answer: f.answer ?? '', ...(f.expected ? { expected: f.expected } : {}), verdict: f.right ? 'correct' : 'wrong', where: f.page, ...common })
  }
  if (o.dialog) {
    const where = whereOf(o.dialog)
    for (const w of o.dialog.words) {
      const q = quality.get(w.item_id)
      out.push({
        kind: 'dialog', item_id: w.item_id, variant: w.how, prompt: w.word, ...(w.said !== undefined ? { answer: w.said } : {}), ...(w.expected ? { expected: w.expected } : {}),
        verdict: w.right ? 'correct' : 'wrong', ...(q !== undefined ? { quality: q } : {}), where, ...common,
      })
    }
  }
  return out
}

/** " Answered wrong: vocab_kurim "kurim" for "kurim ogenj"; …" for the tutor's note: the wrong answers typed or said (at most 5); empty without. */
export function wrongNote(answers: Omit<Answer, 'at'>[]): string {
  const wrong = answers.filter(a => a.kind === 'review' && (a.verdict === 'wrong' || a.verdict === 'almost') && a.answer !== undefined)
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

export const reviews: FeatureFactory = ({ cfg, learner, channel, outbox }) => {
  // what the learner typed or said lately (answers.ts): a rolling log beside the learner's data
  const log = new AnswerLog(join(cfg.appDir, 'answers.json'))

  /** A dialog's words (the body has `dialog`): written as far as they count, the tutor told in one line. */
  async function handleDialog(results: Result[], d: DialogRun, duration_minutes: number, forms: z.infer<typeof formAsked>[] | undefined, language: string | undefined): Promise<Response> {
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
    if (!counted.length && !lowered.length) {
      log.add(answersOf({ results, forms, dialog: d, language }))
      await channel.notify(`App: words in a dialog${deck}, ${whereOf(d)}: ${words}. Nothing to persist. No reply needed.${formsNote(forms)}`, {
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
    log.add(answersOf({ results: counted, forms, dialog: d, session_id: payload.session_id, language }))
    await channel.notify(
      `App: words in a dialog${deck} (already persisted as ${payload.session_id}), ${whereOf(d)}: ${words}. No reply needed.${formsNote(forms)}`,
      { kind: 'dialog_words', conversation_id: 'main', msg_id: payload.session_id },
    )
    return json({ session_id: payload.session_id, correct, total, skipped, lowered, output: u.output, ...(language ? { language } : {}) })
  }

  async function handleReviews(req: Request): Promise<Response> {
    const r = reviewRun.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    const { results, duration_minutes } = r.data
    const language = learner.other(r.data.language)
    if (r.data.dialog) return handleDialog(results, r.data.dialog, duration_minutes, r.data.forms, language)
    const state = learner.state(language)
    const correct = results.filter(x => x.quality >= 3).length
    const payload = {
      session_id: state.computed.next_session_id,
      date: state.computed.today,
      ...(language ? { language } : {}),
      duration_minutes,
      command_used: '/lani-app-review',
      skills_practiced: ['vocabulary'],
      skill_scores: { vocabulary: { exercises: results.length, correct, time_minutes: duration_minutes } },
      review_results: results,
      session_notes: 'Flashcard review in the Lani app (self-rated).',
    }
    const u = learner.update(payload)
    if (!u.ok) return json({ error: u.output }, 500)
    // what was typed or said, into the log once the review is persisted (a retry after an error isn't logged twice)
    const answered = answersOf({ results, answers: reviewAnswers(r.data.answers), forms: r.data.forms, session_id: payload.session_id, language })
    log.add(answered)
    const weak = results.filter(x => x.quality < 3).map(x => x.item_id)
    const deck = language ? ` of ${LANGUAGES[language] ?? language} cards (language "${language}")` : ''
    await channel.notify(
      `App review${deck} finished (already persisted as ${payload.session_id}): ${correct}/${results.length} recalled in ${duration_minutes} min.${weak.length ? ` Struggled with: ${weak.join(', ')}.` : ''}${wrongNote(answered)}${formsNote(r.data.forms)}`,
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
          return json({ ...JSON.parse(learner.stateJson(l.data)), features: STATE_FEATURES })
        },
      },
    ],
    tools: [
      {
        name: 'recent_answers',
        description:
          `What the learner typed or said lately in the app (the last ${log.kept.days} days, at most ${log.kept.max} answers), newest first: each review card's exercise (how it was asked, what was typed or heard, what was expected, the verdict, the grading hint, the quality it gave the card), the forms of words a review asked, and a dialog's word turns. Use it when Jan says a card or an answer was marked wrong but the message has no about_exercise: look the card up (card_id, its id in spaced-repetition.json) or search a word (text, in what was asked, typed or expected). The lani-fix skill uses it.`,
        inputSchema: {
          type: 'object',
          properties: {
            card_id: { type: 'string', description: 'a review card id, e.g. vocab_kurim' },
            text: { type: 'string', description: 'a word in what was asked, typed or expected, e.g. kurim' },
            wrong_only: { type: 'boolean', description: 'only answers marked wrong or almost' },
            language: { type: 'string', description: 'another language\'s deck ("it"); "" for the home language only' },
            limit: { type: 'number', description: 'at most this many (default 20, at most 100)' },
          },
        },
        handle: async (args: unknown) => {
          const r = recentIn.safeParse(args ?? {})
          if (!r.success) return fail(z.prettifyError(r.error))
          const found = log.find({ item_id: r.data.card_id, text: r.data.text, wrong: r.data.wrong_only, language: r.data.language, limit: r.data.limit })
          if (!found.length) return ok(`No answers${r.data.card_id ? ` for ${r.data.card_id}` : ''}${r.data.text ? ` with "${r.data.text}"` : ''} in the last ${log.kept.days} days. An app from before sends no review answers (only forms and dialog words); ask Jan what they typed.`)
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
  language: z.string().trim().max(8).optional(),
  limit: z.number().int().min(1).max(100).optional(),
})
