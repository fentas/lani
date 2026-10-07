// «Vidim, vidim» (I spy, companion/SCENES.md "I spy"): what a child of the village says when they play I spy with the learner
// in a scene's picture. The content is companion/ispy: the child's lines in each language (<language>.json,
// lani.ispy-lines/v0) and each scene's clues (scenes/<scene>.json, lani.ispy/v0). The app bundles it; the bridge serves
// none of it, and reads it only for the voices (voice.ts corpus: voice-build, the bridge's own prebuild), so the clips are
// there when a child says a line.
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

/** A text in its languages ("sl" said in a Slovene scene, "en" what it means …). */
export type ISpyText = Partial<Record<string, string>>

/** What the child says in a language: the opening, the reactions, the find and the reveal (a singular word, a plural), the end. */
export type ISpyLines = {
  language: string
  start?: ISpyText
  again?: ISpyText
  cold?: ISpyText[]
  warm?: ISpyText[]
  found?: { sg?: ISpyText; pl?: ISpyText }
  show?: { sg?: ISpyText; pl?: ISpyText }
  end?: ISpyText
  end_all?: ISpyText
}

/** A scene's clues: by its things' slots, each clue a text in the scene's language with its translations. */
export type ISpyBook = { scene: string; language: string; things: Record<string, { number?: string; clues?: ISpyText[] }> }

/** The content of companion/ispy (the lines by language, the clues by scene); what doesn't read is left out. */
export function readISpy(dir: string): { lines: Record<string, ISpyLines>; books: Record<string, ISpyBook> } {
  const read = (f: string): unknown => {
    try {
      return JSON.parse(readFileSync(f, 'utf8'))
    } catch {
      return undefined
    }
  }
  const lines: Record<string, ISpyLines> = {}
  const books: Record<string, ISpyBook> = {}
  if (!existsSync(dir)) return { lines, books }
  for (const f of readdirSync(dir).filter(f => f.endsWith('.json'))) {
    const l = read(join(dir, f)) as ISpyLines | undefined
    if (l && typeof l.language === 'string') lines[l.language] = l
  }
  const scenes = join(dir, 'scenes')
  if (existsSync(scenes)) for (const f of readdirSync(scenes).filter(f => f.endsWith('.json'))) {
    const b = read(join(scenes, f)) as ISpyBook | undefined
    if (b && typeof b.scene === 'string' && b.things && typeof b.things === 'object') books[b.scene] = b
  }
  return { lines, books }
}

/**
 * Every line a child says in I spy in a scene of [language] whose things' words are [words] (slot → the word as the scene
 * says it; [plural]: the slots whose word is a plural): the game's own lines, every clue of the scene's [book], and each
 * thing's find and reveal with its word in it ({word}). The learner's guesses aren't said. A text with another placeholder
 * than {word} (a learner's, rendered first; the picture-free clues' {letter}, {n}) is left out: it is voiced when played.
 */
export function ispyTexts(lines: ISpyLines, book: ISpyBook | undefined, language: string, words: Record<string, string>, plural: Set<string>): string[] {
  const said = (t: ISpyText | undefined) => t?.[language]
  const out: string[] = []
  const add = (t: string | undefined) => {
    if (t && !t.includes('{')) out.push(t)
  }
  for (const t of [lines.start, lines.again, ...(lines.cold ?? []), ...(lines.warm ?? []), lines.end, lines.end_all]) add(said(t))
  for (const [slot, word] of Object.entries(words)) {
    const pl = plural.has(slot) || book?.things[slot]?.number === 'pl'
    const with_ = (t: { sg?: ISpyText; pl?: ISpyText } | undefined) => said(pl ? (t?.pl ?? t?.sg) : t?.sg)?.replaceAll('{word}', word)
    add(with_(lines.found))
    add(with_(lines.show))
    for (const c of book?.things[slot]?.clues ?? []) add(said(c))
  }
  return [...new Set(out)]
}
