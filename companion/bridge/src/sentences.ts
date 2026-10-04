// lani.sentences/v0 — the tutor's explanations of dialog sentences (publish_sentence_note, <data>/app/sentences.json).
// In a dialog Jan long-presses a line (🔍 Slovnica stavka · The sentence's grammar) and asks the tutor about it; the
// tutor's answer is kept here, so the app shows it the next time the same sentence is long-pressed (GET /sentence).
// Like the tutor's glosses (lexicon.ts): one file, newest last, written atomically, read again when it changes.
import { existsSync, mkdirSync, readFileSync, renameSync, statSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'
import { z } from 'zod'
import type { Log } from './config'

export const SENTENCES_SCHEMA = 'lani.sentences/v0'
/** The longest sentence a note is kept for (a dialog line). */
export const MAX_SENTENCE = 400
/** The notes kept: the newest this many. */
const MAX_NOTES = 2000

const LANG_CODE = /^[a-z]{2,3}$/

/**
 * A sentence as the notes know it: NFC, lower case, every run of what isn't a letter or a digit one space. "Dvanajst
 * krav se pase!" and "dvanajst  krav se pase." are the same sentence.
 */
export function sentenceKey(s: string): string {
  return s.normalize('NFC').toLowerCase().replace(/[^\p{L}\p{M}\p{N}]+/gu, ' ').trim()
}

/**
 * What the tutor keeps with publish_sentence_note: the sentence as the app sent it (data.about_sentence.sentence), the
 * explanation (Markdown), the language it is in ([gloss_lang]; the learner's base when not given) and the sentence's
 * ([language]: another town's on a visit; the village's when not given).
 */
export const sentenceNoteIn = z.object({
  sentence: z.string().trim().min(1).max(MAX_SENTENCE).refine(s => sentenceKey(s) !== '', 'a sentence with words, not only punctuation'),
  text: z.string().trim().min(1).max(4000),
  gloss_lang: z.string().trim().toLowerCase().regex(LANG_CODE, 'a language code, e.g. "en"').optional(),
  language: z.string().trim().toLowerCase().regex(LANG_CODE, 'a language code, e.g. "sl"').optional(),
})

/** A note kept: [key] is the sentence's (sentenceKey), [at] when the tutor wrote it. */
export type SentenceNote = { key: string; sentence: string; language: string; gloss_lang: string; text: string; at: string }

/** An entry of the file as it should be: one written by hand, or by an older bridge, may not be. */
const isNote = (e: unknown): e is SentenceNote => {
  const n = e as SentenceNote
  return !!n && typeof n === 'object' && [n.key, n.sentence, n.language, n.gloss_lang, n.text, n.at].every(v => typeof v === 'string')
}

/** The tutor's sentence notes, in one file (every language's), read on first use and again when it changes. */
export class SentenceNotes {
  private cache: { mtime: number; entries: SentenceNote[] } = { mtime: -1, entries: [] }

  constructor(private readonly o: { file: string; log?: Log }) {}

  /** The notes, newest last. */
  all(): SentenceNote[] {
    const path = this.o.file
    const mtime = existsSync(path) ? statSync(path).mtimeMs : 0
    if (mtime !== this.cache.mtime) {
      let entries: SentenceNote[] = []
      try {
        if (mtime) {
          const all: unknown = JSON.parse(readFileSync(path, 'utf8')).entries
          entries = Array.isArray(all) ? all.filter(isNote) : []
        }
      } catch (e) {
        this.o.log?.(`sentence notes ${path} unreadable: ${(e as Error).message}`)
      }
      this.cache = { mtime, entries }
    }
    return this.cache.entries
  }

  /** Keeps a note, replacing an earlier one of the same sentence, language and gloss language; the oldest go past MAX_NOTES. */
  publish(n: { sentence: string; language: string; gloss_lang: string; text: string }): SentenceNote {
    const entry: SentenceNote = { key: sentenceKey(n.sentence), sentence: n.sentence, language: n.language, gloss_lang: n.gloss_lang, text: n.text, at: new Date().toISOString() }
    const same = (e: SentenceNote) => e.key === entry.key && e.language === entry.language && e.gloss_lang === entry.gloss_lang
    const entries = [...this.all().filter(e => !same(e)), entry].slice(-MAX_NOTES)
    const path = this.o.file
    mkdirSync(dirname(path), { recursive: true })
    writeFileSync(`${path}.tmp`, JSON.stringify({ schema: SENTENCES_SCHEMA, entries }, null, 2) + '\n')
    renameSync(`${path}.tmp`, path)
    // what was written is what the next read would find: no need to read it back
    this.cache = { mtime: statSync(path).mtimeMs, entries }
    return entry
  }

  /** The note of [sentence] (of [language]): the one in [base] if there is one, else the newest in any language, else null. */
  get(sentence: string, language: string, base: string): SentenceNote | null {
    const key = sentenceKey(sentence)
    if (!key) return null
    const notes = this.all().filter(e => e.key === key && e.language === language)
    return notes.findLast(e => e.gloss_lang === base) ?? notes.at(-1) ?? null
  }
}
