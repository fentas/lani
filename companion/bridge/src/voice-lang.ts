// Which language a text asked to be voiced is in, as far as this node knows: its content (the villagers' lines and the
// scenes' dialogs, in each of their languages) and the app's own labels (companion/android/…/l10n/<lang>.json). A text the
// node knows only in another language than the village's is never voiced as the village's (ElevenLabs would read it with
// the village's language_code: Nonna Rosa's Slovene translation in Italian). docs/plans/05-built/visit-voice-lang.md.
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { lineLangs, type SpokenLine } from './langs'
import { allLines, type VillagerLines } from './villagers'

/** The languages a scene's texts and a label table come in (the app's: l10n/Lang.kt). */
const LANGS = ['sl', 'en', 'it', 'de'] as const

/** The key a text is known by: trimmed, its spaces one, in lower case (the app asks for a line as the content has it). */
export const textKey = (t: string) => t.normalize('NFC').trim().replace(/\s+/g, ' ').toLowerCase()

/**
 * [text] as the app says it: what agrees with the learner ({m:…|f:…}) in each gender. A text with any other placeholder
 * ({learner}, {memory}, {n}) is said with what only the app knows, so none (it stays unknown: voiced as asked).
 */
export function forms(text: string): string[] {
  const gendered = /\{m:([^|{}]*)\|f:([^|{}]*)\}/g
  const m = text.replace(gendered, '$1')
  const f = text.replace(gendered, '$2')
  return [...new Set([m, f])].filter(t => !t.includes('{'))
}

/** A text in a language. */
export type Known = [lang: string, text: string]

type DialogLike = { lines?: ({ choices?: ({ reply?: Record<string, unknown> } & Record<string, unknown>)[] } & Record<string, unknown>)[] }

/** Every villager line's texts, each in its language. */
export function* villagerTexts(villagers: { lines: VillagerLines }[]): Generator<Known> {
  for (const v of villagers) for (const l of allLines(v.lines)) for (const k of lineLangs(l as SpokenLine)) yield [k, l[k] as string]
}

/** Every dialog line's, choice's and reply's texts of [scenes], each in its language. */
export function* sceneTexts(scenes: { dialogs?: DialogLike[]; variants?: DialogLike[] }[]): Generator<Known> {
  const of = function* (o: Record<string, unknown> | undefined): Generator<Known> {
    for (const k of LANGS) if (typeof o?.[k] === 'string') yield [k, o[k] as string]
  }
  for (const s of scenes)
    for (const d of [...(s.dialogs ?? []), ...(s.variants ?? [])])
      for (const l of d.lines ?? []) {
        yield* of(l)
        for (const c of l.choices ?? []) {
          yield* of(c)
          yield* of(c.reply)
        }
      }
}

/** The app's labels of each language under [projectDir], when the app's sources are there (a node); none on a phone. */
export function* labelTexts(projectDir: string): Generator<Known> {
  for (const lang of LANGS) {
    const path = join(projectDir, 'companion/android/app/src/main/resources/l10n', `${lang}.json`)
    if (!existsSync(path)) continue
    try {
      const table = JSON.parse(readFileSync(path, 'utf8')) as Record<string, unknown>
      for (const [k, v] of Object.entries(table)) if (!k.startsWith('_') && typeof v === 'string' && !v.includes('{')) yield [lang, v]
    } catch {} // a table that can't be read: its texts aren't known
  }
}

/**
 * The texts this node knows, by language, made from [sources] when first asked and again after [ttlMs] (content changes:
 * a tutor's villager or scene).
 */
export class KnownTexts {
  private built?: { at: number; by: Map<string, Set<string>> }

  constructor(
    private readonly sources: () => Iterable<Known>,
    private readonly ttlMs = 60_000,
    private readonly clock: () => number = Date.now,
  ) {}

  private map(): Map<string, Set<string>> {
    const now = this.clock()
    if (this.built && now - this.built.at < this.ttlMs) return this.built.by
    const by = new Map<string, Set<string>>()
    for (const [lang, text] of this.sources())
      for (const form of forms(text)) {
        const k = textKey(form)
        if (!k) continue
        let s = by.get(k)
        if (!s) by.set(k, (s = new Set()))
        s.add(lang)
      }
    this.built = { at: now, by }
    return by
  }

  /** The languages [text] is known in; empty when the node doesn't know it. */
  languagesOf(text: string): string[] {
    return [...(this.map().get(textKey(text)) ?? [])]
  }

  /** A language [text] is known in when it is known only in others than [village]'s; undefined otherwise. */
  foreign(text: string, village: string): string | undefined {
    const langs = this.languagesOf(text)
    return langs.length && !langs.includes(village) ? langs[0] : undefined
  }
}

/**
 * Why [text] isn't voiced as the village's [village] language, or undefined when it may be. Asked with [lang] (an
 * explanation, in the learner's own language or another the app shows), it is voiced plainly, without a language_code:
 * it can't be read in the wrong language, so this never refuses it. Asked without one, it is taken as the village's,
 * so a text the node knows only in another language isn't voiced.
 */
export function notVoiced(text: string, lang: string | undefined, village: string, known: KnownTexts): string | undefined {
  if (lang !== undefined) return undefined
  const other = known.foreign(text, village)
  return other ? `known in ${other}, not the village's ${village}` : undefined
}
