// Two words of one language that ask one prompt in a base: card.py's overlap() (.claude/hooks/card.py; its port
// src/db/card.ts), a meaning the app asks for when it quizzes one of the two cards, the words themselves different. Asked
// "bench" (or "klop"), a learner who knows both das Bankerl and die Bank can answer either, and only one is right.
//
// The words: every word of the curated packs of each language (companion/packs/*.json for Slovene, companion/packs/<lang>/
// for the others, and every culture's packs/ in that language: a learner meets the packs of their village's language and
// of the towns they visit), as the card the bridge makes of it for a learner of each other base (packs.ts learnPayload:
// the word on its front, meaningOf(base) on its back), with the gender pairs as a man and as a woman has them. Two words
// that take one answer (the same word in two packs) are one card (packs.ts learnedWords), or two meanings of one spelling
// (marked homonym), each its own card with its own prompt: never a clash.
//
// The clashes there were when the check came (2026-11-02) are its baseline, test/prompt-clashes.json: synonyms the packs
// teach (Grüß Gott and Guten tag, Servus and Tschüss, das Bankerl and die Bank …), left as they are. A clash not in it
// fails the smoke (smoke/packs.ts): give one word a prompt of its own (die Rutsche "playground slide", der Schnupfen
// "runny nose, head cold"), or, a synonym on purpose, add it with --write. A baseline entry no longer there is listed.
//
// Run: bun test/prompt-clashes.ts            the new clashes, then the baseline's (exit 1 with a new one)
//      bun test/prompt-clashes.ts --write    the baseline written anew from the packs as they are (review the diff)
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { addressee, render } from '../src/addressee'
import { normalize, overlap, promptParts, quizzed } from '../src/db/card'
import { PyDict } from '../src/db/py'
import { meaningOf, validatePack, WORD_LANGUAGES, wordOf, type Pack } from '../src/packs'

const REPO = resolve(import.meta.dir, '../../..')
export const BASELINE = resolve(import.meta.dir, 'prompt-clashes.json')

/** language → base → "<word> | <word>" (the two fronts, normalized, in order) → the prompts they share (", "). */
export type Clashes = Record<string, Record<string, Record<string, string>>>

/** The curated packs of the repository: companion/packs (its subdirectories too) and every culture's packs/. */
export function curatedPacks(repo = REPO): { file: string; pack: Pack }[] {
  const dirs = [join(repo, 'companion/packs')]
  for (const d of readdirSync(join(repo, 'companion/packs'), { withFileTypes: true })) if (d.isDirectory()) dirs.push(join(repo, 'companion/packs', d.name))
  const cultures = join(repo, 'companion/cultures')
  for (const c of readdirSync(cultures, { withFileTypes: true })) if (c.isDirectory() && existsSync(join(cultures, c.name, 'packs'))) dirs.push(join(cultures, c.name, 'packs'))
  const out: { file: string; pack: Pack }[] = []
  for (const dir of dirs)
    for (const f of readdirSync(dir).filter(f => f.endsWith('.json')).sort()) {
      const v = validatePack(JSON.parse(readFileSync(join(dir, f), 'utf8')))
      if (v.ok) out.push({ file: join(dir, f).slice(repo.length + 1), pack: v.pack })
    }
  return out
}

const LEARNERS = [addressee({ name: 'Learner' }), addressee({ name: 'Learner', gender: 'female' })]

/** Every clash of the packs' words ([curatedPacks]), each pair of words once, keyed by their fronts as a man has them. */
export function promptClashes(packs = curatedPacks()): Clashes {
  const out: Clashes = {}
  for (const language of WORD_LANGUAGES) {
    const own = packs.filter(p => p.pack.language === language).map(p => p.pack)
    if (!own.length) continue
    for (const base of WORD_LANGUAGES.filter(b => b !== language)) {
      // the cards: one per word and learner's gender, the word's key its front as a man has it
      const cards: { key: string; item: PyDict }[] = []
      const seen = new Set<string>()
      for (const p of own)
        for (const w of p.words) {
          const key = normalize(render(wordOf(p, w), LEARNERS[0]))
          for (const a of LEARNERS) {
            const content = render(wordOf(p, w), a)
            const answer = render(meaningOf(p, w, base), a)
            const id = `${key}\u0000${content}\u0000${answer}`
            if (seen.has(id)) continue
            seen.add(id)
            cards.push({ key, item: PyDict.of({ type: 'vocabulary', content, answer }) })
          }
        }
      // the cards that share a prompt part, compared as card.py's overlap() does
      const byPart = new Map<string, number[]>()
      cards.forEach((c, i) => {
        for (const part of promptParts(c.item.get('answer'))) byPart.set(part, [...(byPart.get(part) ?? []), i])
      })
      const found: Record<string, Set<string>> = {}
      const done = new Set<string>()
      for (const list of byPart.values())
        for (let x = 0; x < list.length; x++)
          for (let y = x + 1; y < list.length; y++) {
            const a = cards[list[x]], b = cards[list[y]]
            if (a.key === b.key || !(quizzed(a.item) || quizzed(b.item))) continue
            const pair = [a.key, b.key].sort().join(' | ')
            const ids = `${list[x]}:${list[y]}`
            if (done.has(ids)) continue
            done.add(ids)
            const o = overlap(a.item, b.item)
            // the same answer: one card (learnedWords), never two asked one prompt
            if (!o || o.has('same_answer') || !o.has('same_prompt')) continue
            for (const s of o.get('same_prompt') as string[]) (found[pair] ??= new Set()).add(s)
          }
      if (Object.keys(found).length) {
        out[language] ??= {}
        out[language][base] = Object.fromEntries(Object.keys(found).sort().map(k => [k, [...found[k]].sort().join(', ')]))
      }
    }
  }
  return out
}

/** The baseline (test/prompt-clashes.json's `clashes`): the clashes left as they are. */
export function baseline(file = BASELINE): Clashes {
  return existsSync(file) ? (JSON.parse(readFileSync(file, 'utf8')).clashes ?? {}) : {}
}

/** One line a clash: "de → en: das bankerl | die bank (bench)". */
const lines = (c: Clashes, keep: (l: string, b: string, k: string) => boolean = () => true) =>
  Object.entries(c).flatMap(([l, bases]) => Object.entries(bases).flatMap(([b, pairs]) => Object.entries(pairs).filter(([k]) => keep(l, b, k)).map(([k, p]) => `${l} → ${b}: ${k} (${p})`)))

/** What the packs have against the baseline: the clashes not in it ([added]), its entries no longer there ([gone]), all. */
export function compare(now: Clashes, base: Clashes) {
  return {
    added: lines(now, (l, b, k) => base[l]?.[b]?.[k] === undefined),
    gone: lines(base, (l, b, k) => now[l]?.[b]?.[k] === undefined),
    old: lines(now, (l, b, k) => base[l]?.[b]?.[k] !== undefined),
  }
}

if (import.meta.main) {
  const now = promptClashes()
  if (process.argv.includes('--write')) {
    const n = lines(now).length
    writeFileSync(
      BASELINE,
      JSON.stringify(
        {
          about:
            'Two words of one language of the curated packs that ask one prompt in a base (card.py overlap(): the same prompt, ' +
            'different words), left as they are: mostly the synonyms the packs teach (Grüß Gott, Guten Tag); a prompt with two ' +
            'meanings parts its words with a prompt of its own (il tronco "tree trunk", il baule "trunk"). ' +
            'test/prompt-clashes.ts fails the smoke on any other; ' +
            'language → base → "<word> | <word>" (their fronts, normalized) → the prompts they share.',
          count: n,
          clashes: now,
        },
        null,
        2,
      ) + '\n',
    )
    console.log(`${BASELINE}: ${n} clashes`)
  } else {
    const { added, gone, old } = compare(now, baseline())
    for (const l of old) console.log(`  ${l}`)
    console.log(`${old.length} clashes in the baseline (test/prompt-clashes.json)`)
    if (gone.length) console.log(`\nno longer there (bun test/prompt-clashes.ts --write takes them out):\n${gone.map(l => `  ${l}`).join('\n')}`)
    if (added.length) console.log(`\nnew, not in the baseline:\n${added.map(l => `  ${l}`).join('\n')}`)
    process.exit(added.length ? 1 : 0)
  }
}
