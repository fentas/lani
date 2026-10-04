// The learner databases for the app: the full state, and flashcard reviews. Reviews are graded by
// the app (self-rated), so they are persisted deterministically via update-db.py; Claude only hears
// a summary. Both take an optional language: another language's review deck and data (visits), the
// home language's by default (docs/DB_SCRIPTS.md, "Languages"). A review may say which of its cards asked a form of
// their word (forms: "Jaz ___ na klop." → sedem, GET /forms) and how that went: the tutor hears of the wrong ones in
// the summary, nothing more is written (the word's card got its quality; the app counts the form on its rule).
import { z } from 'zod'
import type { FeatureFactory } from '../feature'
import { json, readJson } from '../http'
import { languageParam } from '../learner'
import { LANGUAGES } from '../learners'

/** A form of a card's word asked in a review: the slot ("pres.1sg"), the grammar page it counts on, how it went. */
const formAsked = z.object({
  item_id: z.string().min(1),
  key: z.string().trim().min(1).max(20),
  page: z.string().trim().min(1).max(64),
  right: z.boolean(),
  answer: z.string().trim().max(200).optional(),
  expected: z.string().trim().max(200).optional(),
})

const reviewRun = z.object({
  results: z.array(z.object({ item_id: z.string().min(1), quality: z.number().int().min(0).max(5) })).min(1),
  duration_minutes: z.number().int().min(1).max(240),
  language: languageParam,
  forms: z.array(formAsked).max(50).optional(),
})

/** " Forms: 3 asked, 1 wrong: sedi for sedem (glagoli-sedanjik)." for the tutor; empty without forms. */
export function formsNote(forms: z.infer<typeof formAsked>[] | undefined): string {
  if (!forms?.length) return ''
  const wrong = forms.filter(f => !f.right)
  const said = wrong.map(f => `${f.answer ? `"${f.answer}"` : '(no answer)'} for "${f.expected ?? '?'}" (${f.page}, ${f.key}, ${f.item_id})`)
  return ` Forms of words asked: ${forms.length}, ${wrong.length} wrong${said.length ? `: ${said.join('; ')}` : ''}.`
}

export const reviews: FeatureFactory = ({ learner, channel, outbox }) => {
  async function handleReviews(req: Request): Promise<Response> {
    const r = reviewRun.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    const { results, duration_minutes } = r.data
    const language = learner.other(r.data.language)
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
        handle: ({ url }) => {
          const l = languageParam.safeParse(url.searchParams.get('language') ?? undefined)
          if (!l.success) return json({ error: 'language: a language code, e.g. "it"' }, 400)
          return new Response(learner.stateJson(l.data), { headers: { 'content-type': 'application/json' } })
        },
      },
    ],
  }
}
