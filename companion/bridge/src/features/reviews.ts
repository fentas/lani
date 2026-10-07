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
import { z } from 'zod'
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

export const reviews: FeatureFactory = ({ learner, channel, outbox }) => {
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
    const weak = results.filter(x => x.quality < 3).map(x => x.item_id)
    const deck = language ? ` of ${LANGUAGES[language] ?? language} cards (language "${language}")` : ''
    await channel.notify(
      `App review${deck} finished (already persisted as ${payload.session_id}): ${correct}/${results.length} recalled in ${duration_minutes} min.${weak.length ? ` Struggled with: ${weak.join(', ')}.` : ''}${formsNote(r.data.forms)}`,
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
  }
}
