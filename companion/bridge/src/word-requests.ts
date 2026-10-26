// The words the learner asked to add that the dictionary didn't know (POST /words/requests, features/lexicon.ts): each
// waits here for the tutor's meaning (publish_gloss), then becomes one of their words. Kept in the learner's data
// (<data>/app/word-requests.json), so neither a restart nor a handover to the phone loses one.
import { existsSync, readFileSync, renameSync, statSync, writeFileSync } from 'node:fs'
import type { Log } from './config'
import { normalizeForm } from './lexicon'

/** One word waiting for its meaning. */
export type WordRequest = {
  /** The app's client_id of the request: one per tap, the outbox's. */
  id: string
  /** The form as it was tapped ("vretenom"), and [key] as the lexicon compares it ("vretenom"). */
  word: string
  key: string
  /** The word's language (on a visit, the town's), and the language its meaning is wanted in (the learner's base). */
  language: string
  base: string
  /** The line it was met in and its translation: the meaning meant there, and the review item's example. */
  line?: string
  en?: string
  /** Where in the app (POST /words `from`). */
  from?: string
  /** When it was asked; when the tutor was last told, and how many times. */
  at: string
  told?: string
  times?: number
}

/** At most this many words wait at once (the app hears a 409 past it). */
export const MAX_REQUESTS = 200
/** A request this old is dropped (logged): its tutor never answered it. */
const MAX_AGE_MS = 60 * 86_400_000

const isRequest = (r: any): r is WordRequest =>
  typeof r?.id === 'string' && typeof r.word === 'string' && typeof r.key === 'string' && typeof r.language === 'string' && typeof r.base === 'string' && typeof r.at === 'string'

export class WordRequests {
  private list: WordRequest[] = []
  private mtime = -1

  constructor(
    private readonly path: string,
    private readonly log: Log,
  ) {}

  /** The file read again when it changed (a handover, git sync); the stale ones dropped. */
  private load(): WordRequest[] {
    let mtime = 0
    try {
      mtime = existsSync(this.path) ? statSync(this.path).mtimeMs : 0
    } catch {} // read below
    if (mtime === this.mtime) return this.list
    this.mtime = mtime
    let raw: unknown[] = []
    try {
      if (mtime) raw = JSON.parse(readFileSync(this.path, 'utf8')).requests ?? []
    } catch (e) {
      this.log(`word requests: ${this.path} unreadable (${(e as Error).message}): none kept`)
    }
    const fresh = Date.now() - MAX_AGE_MS
    const valid = (Array.isArray(raw) ? raw : []).filter(isRequest)
    this.list = valid.filter(r => Date.parse(r.at) > fresh)
    if (this.list.length < valid.length) {
      this.log(`word requests: ${valid.length - this.list.length} older than ${MAX_AGE_MS / 86_400_000} days dropped (never answered)`)
      this.save()
    }
    return this.list
  }

  private save() {
    const tmp = `${this.path}.tmp`
    writeFileSync(tmp, JSON.stringify({ schema: 'lani.word-requests/v0', requests: this.list }, null, 2) + '\n')
    renameSync(tmp, this.path)
    try {
      this.mtime = statSync(this.path).mtimeMs
    } catch {
      this.mtime = -1
    }
  }

  /** Every word waiting, oldest first. */
  all(): WordRequest[] {
    return [...this.load()]
  }

  /** The word of [language] waiting under [key] (normalized), if one is. */
  find(language: string, key: string): WordRequest | undefined {
    return this.load().find(r => r.language === language && r.key === key)
  }

  /** Keeps [r]; one of the same language and key replaces it. */
  add(r: WordRequest) {
    this.list = [...this.load().filter(x => !(x.language === r.language && x.key === r.key)), r]
    this.save()
  }

  /** The tutor was told of [ids] now. */
  told(ids: string[], at = new Date().toISOString()) {
    const set = new Set(ids)
    if (!this.load().some(r => set.has(r.id))) return
    this.list = this.list.map(r => (set.has(r.id) ? { ...r, told: at, times: (r.times ?? 0) + 1 } : r))
    this.save()
  }

  /**
   * The words a gloss of [form] with [lemma] in [glossLang] answers (the word's language [language]): those waiting for
   * a meaning in that language whose form is the gloss's form or its lemma. Taken out.
   */
  take(language: string, form: string, lemma: string, glossLang: string): WordRequest[] {
    const keys = new Set([normalizeForm(form, language), normalizeForm(lemma, language)])
    const hit = (r: WordRequest) => r.language === language && r.base === glossLang && keys.has(r.key)
    const taken = this.load().filter(hit)
    if (!taken.length) return []
    this.list = this.list.filter(r => !hit(r))
    this.save()
    return taken
  }

  /** The word of [language] waiting under [key] for a meaning in another language than [glossLang], if one is. */
  waitingOtherwise(language: string, form: string, lemma: string, glossLang: string): WordRequest | undefined {
    const keys = new Set([normalizeForm(form, language), normalizeForm(lemma, language)])
    return this.load().find(r => r.language === language && keys.has(r.key) && r.base !== glossLang)
  }
}
