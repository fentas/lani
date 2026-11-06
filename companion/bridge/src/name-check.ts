// The name check, as the voice service (glas) has it, so a node checks a name the same way before any paid voice says it, with
// the service set up or not (docs/plans/05-built/names-guard.md). glas's src/name-lists.ts and the start of its name check, to
// the letter:
//
// 1. the shape rules ([nameOk], name-lines.ts), the name read in NFKC first (a wide letter or a ligature is its letter), and only
//    a name that folds to plain letters: a voice reads a small capital or an IPA letter as the letter it looks like, and no list
//    would know the word ("ᴋurba");
// 2. the blocklists: LDNOOBW in its 28 languages, profane-words, Lani's own list (its Slovene expanded to every form Sloleks 3.0
//    has) and the shape rules' short list. Each word, each part of a word, the whole name and the parts run together, as written
//    and with the marks off ("scheisse" is "scheiße"). The blocklist wins over a real name (Dick, Willy);
// 3. the keep list: names a list would take that are voiced all the same (Marica, Guido), as a whole name or a whole word of one.
//
// The service's next step, a real given name, matters here only where it changes the second: a given name is looked up whole and
// by its words, and a list's word only with the marks off doesn't count against it (Peter is no French "péter", Beom-sik no
// Turkish "sik"). So the bridge keeps the given names a list's word is in (name-lists/given-listed.txt), not all of them: with
// them, every name gets the service's verdict on the lists. Whether a name nobody knows waits for approval is the service's
// alone: it needs every given name and a review list.
//
// The lists are generated (companion/bridge/name-lists/: companion/bin/names-sync from a glas checkout) and read once, when the
// first name is checked. test/name-check.ts holds this check to the service's own verdicts over a probe set
// (test/fixtures/name-check-verdicts.json).
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { nameOk, normalizeName } from './name-lines'

/** Where the generated lists are. */
export const NAME_LISTS_DIR = join(import.meta.dir, '../name-lists')

/** The letters with no mark to take off, as a plain alphabet spells them (what the given names use, ə and ŋ among them). */
const LETTERS: Record<string, string> = {
  ß: 'ss', æ: 'ae', œ: 'oe', ø: 'o', đ: 'd', ł: 'l', ı: 'i', ħ: 'h', þ: 'th', ð: 'd', ŀ: 'l', ə: 'e', ɛ: 'e', ɔ: 'o', ŋ: 'ng', ɣ: 'g', ɗ: 'd', ƶ: 'z', ʒ: 'z', '’': "'",
}
const SPELLED = new RegExp(`[${Object.keys(LETTERS).join('')}]`, 'g')

/**
 * [s] with its marks off, the letters that have none to take off spelled plainly, and what is a letter only by its look (wide
 * letters, ligatures) as the letter: "Scheiße" and "scheisse" are one, and so are "ｋurba" and "kurba".
 */
export const fold = (s: string) => s.normalize('NFKD').replace(/\p{M}/gu, '').replace(SPELLED, c => LETTERS[c]).normalize('NFC')

/** A name folded to these only: anything else is a letter a voice reads as another, which no list would know. */
export const plain = (name: string) => /^[a-z' -]+$/.test(fold(name))

/** How an entry of a list is matched: normalized as a name is, then folded; and with German's umlauts written out ("moese"). */
export function entryKeys(raw: string): string[] {
  const n = normalizeName(raw)
  const spelled = n.replace(/ä/g, 'ae').replace(/ö/g, 'oe').replace(/ü/g, 'ue')
  return [...new Set([fold(n), fold(spelled)])].filter(Boolean)
}

/** What a name is looked up by: the whole of it, each word, each part of a word, the parts with nothing or a space between. */
export function lookups(name: string, folded = true): string[] {
  const f = folded ? fold(name) : name.replace(/’/g, "'")
  const words = f.split(' ')
  const parts = words.flatMap(w => w.split(/['-]/)).filter(Boolean)
  return [...new Set([f, ...words, ...parts, parts.join(' '), parts.join('')])]
}

/** The shortest list word looked for inside a name: shorter ones are inside too many names ("ass", "tit"). */
const SUSPECT_MIN = 4

/** The whole of a name and its words, folded or as written: what a given name is looked up by. */
function wholeAndWords(name: string, folded: boolean): string[] {
  const f = folded ? fold(name) : name.replace(/’/g, "'")
  return [...new Set([f, ...f.split(' ')])]
}

/** keep.txt: a name a line, its reason after "#". given-listed.txt the same, without reasons. */
export const readKeep = (text: string) =>
  text
    .split('\n')
    .map(l => l.replace(/#.*/, '').trim())
    .filter(Boolean)

/** A list as blocklist.json has it: its entries as the source wrote them. */
export type ListData = {
  /** `ldnoobw:<lang>`, `profane-words`, `lani:<lang>` (`lani:*` every language's), `not-names`. */
  id: string
  source: 'ldnoobw' | 'profane-words' | 'lani' | 'not-names'
  /** The language its words are of: `*` all of them (hate and extremism), null none in particular. */
  lang: string | null
  entries: string[]
}
export type BlocklistData = { v: 1; lists: ListData[] }

/**
 * The blocklists, the keep list and the given names the lists touch, read once. An entry is matched as its source wrote it and
 * folded. A match as written wins over a real name; a match only with the marks off doesn't count against one.
 */
export class NameLists {
  private readonly exact = new Map<string, string>()
  private readonly folded = new Map<string, string>()
  private readonly given = new Set<string>()
  private readonly keep = new Set<string>()

  /** [given]: the given names a list's word is in, or all of them (the service's); [keep]: keep.txt's names. */
  constructor(
    readonly data: BlocklistData,
    given: Iterable<string>,
    keep: Iterable<string> = [],
  ) {
    // an entry of more than one word also as one ("blow job" for "Blowjob")
    const put = (m: Map<string, string>, k: string, list: string) => {
      for (const x of [k, k.split(/[ '-]/).join('')]) if (x && !m.has(x)) m.set(x, list)
    }
    for (const list of data.lists) {
      for (const raw of list.entries) {
        put(this.exact, normalizeName(raw).replace(/’/g, "'"), list.id)
        for (const k of entryKeys(raw)) put(this.folded, k, list.id)
      }
    }
    for (const g of given) this.given.add(fold(g))
    for (const k of keep) this.keep.add(normalizeName(k.normalize('NFKC')))
  }

  /** Whether [name] (normalized: a name or one word of it) is on the keep list, as written: Thông is, "Thong" isn't. */
  kept(name: string): boolean {
    return this.keep.has(name)
  }

  /**
   * The list [name] (normalized) is on, by any of its [lookups]; undefined when it is on none. A given name is looked up whole and
   * by its words only: "Beom-sik" is a Korean name, while "Kurba-Svinja", nobody's name, is its parts.
   */
  blockedBy(name: string): string | undefined {
    if (this.kept(name)) return undefined
    // a kept word lifts its own hit and no other: "Marica Novak" is clear, "Marica Kurba" is "kurba"
    const lifted = new Set(name.split(' ').flatMap(w => (this.kept(w) ? [w, fold(w)] : [])))
    const given = this.isGiven(name)
    const keys = (folded: boolean) => (given ? wholeAndWords(name, folded) : lookups(name, folded)).filter(k => !lifted.has(k))
    // as written, and with the name's own marks off: "Dĭck" is Dick, which a list has as written
    for (const k of [...keys(false), ...keys(true)]) {
      const list = this.exact.get(k)
      if (list) return list
    }
    if (given) return undefined
    for (const k of keys(true)) {
      const list = this.folded.get(k)
      if (list) return list
    }
    return undefined
  }

  /**
   * A list's word of four letters or more inside [name] (normalized), run together and folded: "Kurbasvinja". Not a block, which
   * would take real names with it ("Cassandra"): the service holds a name nobody knows with one in it for review.
   */
  hidesWord(name: string): boolean {
    const f = lookups(name).at(-1)!
    for (let i = 0; i < f.length; i++) {
      for (let j = i + SUSPECT_MIN; j <= f.length; j++) {
        const k = f.slice(i, j)
        if (this.folded.has(k) || this.exact.has(k)) return true
      }
    }
    return false
  }

  /** Whether [name] (normalized) is a given name the lists touch, the whole of it. */
  isGiven(name: string): boolean {
    return this.given.has(fold(name))
  }

  /**
   * Whether a list has any of [name]'s [lookups]: a given name of which being one can change the verdict. The rest are left out
   * of given-listed.txt: none of their lookups is on a list, so they are clear either way (names-sync).
   */
  touches(name: string): boolean {
    return lookups(name).some(k => this.folded.has(k) || this.exact.has(k))
  }

  get givenCount() {
    return this.given.size
  }
}

/** The lists in [dir]: blocklist.json, given-listed.txt and keep.txt. Throws when they can't be read. */
export function readNameLists(dir = NAME_LISTS_DIR): NameLists {
  const data = JSON.parse(readFileSync(join(dir, 'blocklist.json'), 'utf8')) as BlocklistData
  if (data.v !== 1 || !Array.isArray(data.lists) || !data.lists.length) throw new Error(`${join(dir, 'blocklist.json')} is not a blocklist`)
  return new NameLists(data, readKeep(readFileSync(join(dir, 'given-listed.txt'), 'utf8')), readKeep(readFileSync(join(dir, 'keep.txt'), 'utf8')))
}

let loaded: NameLists | undefined
/** The bridge's lists, read the first time a name is checked. */
export function nameLists(): NameLists {
  loaded ??= readNameLists()
  return loaded
}

/**
 * What the check says of [raw]: `invalid` (the shape rules, or a letter a voice would read as another), `blocked` (a list's
 * word; [list] says which), else `clear`, with what the service decides the rest by: its words, and whether a list's word hides
 * inside it.
 */
export type NameVerdict =
  | { verdict: 'invalid' }
  | { verdict: 'blocked'; name: string; list: string }
  | { verdict: 'clear'; name: string; words: number; hides: boolean }

export function checkName(raw: string, lists: NameLists = nameLists()): NameVerdict {
  const name = checkedName(raw)
  if (!name || !plain(name)) return { verdict: 'invalid' }
  const list = lists.blockedBy(name)
  if (list) return { verdict: 'blocked', name, list }
  return { verdict: 'clear', name, words: name.split(' ').length, hides: lists.hidesWord(name) }
}

/** [raw] as the library keys it (read in NFKC first, as the service reads it), or undefined when it fails the shape rules. */
export const checkedName = (raw: string) => nameOk(raw.normalize('NFKC'))

/** Why no voice may say [raw]: 'invalid' or 'blocked'; undefined when the check lets it through. */
export function nameRefusal(raw: string, lists?: NameLists): 'invalid' | 'blocked' | undefined {
  const name = checkedName(raw)
  if (!name || !plain(name)) return 'invalid'
  return (lists ?? nameLists()).blockedBy(name) ? 'blocked' : undefined
}
