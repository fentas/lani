// Sentence grammar (sentences.ts): in a dialog Jan long-presses a line and asks the tutor about its grammar (a chat
// with data.about_sentence); the tutor's explanation is kept (publish_sentence_note) and the app shows it the next time
// the sentence is long-pressed (GET /sentence). An older bridge answers 404 there: the app shows no note then.
import { join } from 'node:path'
import { z } from 'zod'
import { fail, ok } from '../channel'
import type { FeatureFactory } from '../feature'
import { json } from '../http'
import { LANGUAGES, learnerLangs } from '../learners'
import { MAX_SENTENCE, SentenceNotes, sentenceNoteIn } from '../sentences'

const instructions = `
Sentence grammar: in a dialog Jan long-presses a line (🔍 Slovnica stavka · The sentence's grammar) and sees each
word's dictionary form, its reading and, where the app knows, why that form (the preposition, the number, the
negation, the verb) with its grammar book page. A chat with data.about_sentence comes from its "💬 Vprašaj učitelja ·
Ask the tutor": it holds the sentence, its translation (en), the scene, and the words as the app read them. Explain the
sentence's grammar, briefly and phone-sized: each form's case and why, the verb's person and tense, where the short
words go; name the book's pages. Reply, AND call publish_sentence_note with the sentence exactly as
data.about_sentence.sentence has it and your explanation (Markdown, in the language you explain in: gloss_lang), so
the app shows it the next time; on a visit pass data.about_sentence.language as language.
`.trim()

const LANG = /^[a-z]{2,3}$/
const known = (code: string) => LANG.test(code) && !!LANGUAGES[code]

export const sentences: FeatureFactory = ({ cfg, events, culture, log }) => {
  const language = culture.manifest?.language ?? 'sl'
  const notes = new SentenceNotes({ file: join(cfg.appDir, 'sentences.json'), log })
  /** What a sentence of [lang] is explained in unless asked otherwise: the learner's base. */
  const baseOf = (lang: string) => learnerLangs(cfg.dataDir, lang).base

  return {
    instructions,
    routes: [
      {
        method: 'GET',
        path: '/sentence',
        handle: ({ url }) => {
          const s = url.searchParams.get('s') ?? ''
          if (!s.trim() || s.trim().length > MAX_SENTENCE) return json({ error: `s: the sentence, 1-${MAX_SENTENCE} characters` }, 400)
          // on a visit: the town's language; explained in the learner's base unless the app asks for another
          const lang = url.searchParams.get('language')?.trim().toLowerCase() || language
          const asks = url.searchParams.get('base')?.trim().toLowerCase()
          if (!known(lang)) return json({ error: 'language: a language code, e.g. "it"' }, 400)
          if (asks && !known(asks)) return json({ error: 'base: a language code, e.g. "en"' }, 400)
          const n = notes.get(s, lang, asks || baseOf(lang))
          return json({ sentence: s, note: n ? { text: n.text, gloss_lang: n.gloss_lang, at: n.at } : null })
        },
      },
    ],
    tools: [
      {
        name: 'publish_sentence_note',
        description:
          "Keep your explanation of a dialog sentence's grammar (a chat with data.about_sentence), so the app shows it the next time Jan long-presses that sentence. The sentence exactly as data.about_sentence.sentence has it (case and punctuation don't matter), the explanation (Markdown, phone-sized), the language it is in (gloss_lang, default Jan's base) and the sentence's (language, default the village's; on a visit, data.about_sentence.language). Replaces your earlier note of the same sentence in the same languages.",
        inputSchema: {
          type: 'object',
          properties: {
            sentence: { type: 'string', description: 'The sentence as data.about_sentence.sentence has it, e.g. "Dvanajst krav se pase."' },
            text: { type: 'string', description: 'The explanation, Markdown, at most 4000 characters' },
            gloss_lang: { type: 'string', description: 'The language the explanation is in: "en", "sl", "de", "it" …; default Jan\'s base' },
            language: { type: 'string', description: 'The sentence\'s language, e.g. "it" on a visit to an Italian town; default the village\'s' },
          },
          required: ['sentence', 'text'],
        },
        handle: args => {
          const r = sentenceNoteIn.safeParse(args)
          if (!r.success) return fail(`sentence note invalid, nothing kept:\n${z.prettifyError(r.error)}`)
          const lang = r.data.language ?? language
          const glossLang = r.data.gloss_lang ?? baseOf(lang)
          if (!LANGUAGES[lang] || !LANGUAGES[glossLang]) return fail(`language and gloss_lang are language codes (${Object.keys(LANGUAGES).join(', ')}); nothing kept`)
          const n = notes.publish({ sentence: r.data.sentence, language: lang, gloss_lang: glossLang, text: r.data.text })
          events.emit({ type: 'sentence_explained', sentence: n.sentence, ...(lang !== language ? { language: lang } : {}) })
          return ok(`kept: "${n.sentence}" (${LANGUAGES[glossLang]}${lang !== language ? `, a ${LANGUAGES[lang]} sentence` : ''}); the app shows it the next time Jan long-presses the sentence`)
        },
      },
    ],
  }
}
