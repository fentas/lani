// A synthetic learner's learning, for the smoke and the QA's dev towns (companion/bin/qa, test/dev-town.ts): review cards
// added over the last weeks (some learned, one rusty, some new this week), error patterns with recent mistakes (their
// examples name the cards), sessions on the last days (a streak), in the shapes update-db.py writes. Nothing of a real
// learner. [secret]: a marker put in every text that must never leave the town (what was typed, the corrections, the
// contexts' words, the notes, an error pattern's own card, the session notes), so a check can look for it.
import { readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { localDate } from '../../src/towns'

type Word = { id: string; word: string; meanings: Record<string, string> }

const WORDS: Record<'it' | 'sl', Word[]> = {
  it: [
    { id: 'vocab_cane', word: 'il cane', meanings: { de: 'der Hund', sl: 'pes', en: 'the dog' } },
    { id: 'vocab_casa', word: 'la casa', meanings: { de: 'das Haus', sl: 'hiša', en: 'the house' } },
    { id: 'vocab_gatto', word: 'il gatto', meanings: { de: 'die Katze', sl: 'mačka', en: 'the cat' } },
    { id: 'vocab_mela', word: 'la mela', meanings: { de: 'der Apfel', sl: 'jabolko', en: 'the apple' } },
    { id: 'vocab_pane', word: 'il pane', meanings: { de: 'das Brot', sl: 'kruh', en: 'the bread' } },
    { id: 'vocab_acqua', word: "l'acqua", meanings: { de: 'das Wasser', sl: 'voda', en: 'the water' } },
    { id: 'vocab_scuola', word: 'la scuola', meanings: { de: 'die Schule', sl: 'šola', en: 'the school' } },
    { id: 'vocab_libro', word: 'il libro', meanings: { de: 'das Buch', sl: 'knjiga', en: 'the book' } },
    { id: 'vocab_finestra', word: 'la finestra', meanings: { de: 'das Fenster', sl: 'okno', en: 'the window' } },
    { id: 'vocab_sole', word: 'il sole', meanings: { de: 'die Sonne', sl: 'sonce', en: 'the sun' } },
  ],
  sl: [
    { id: 'vocab_hisa', word: 'hiša', meanings: { en: 'house', de: 'das Haus', it: 'la casa' } },
    { id: 'vocab_pes', word: 'pes', meanings: { en: 'dog', de: 'der Hund', it: 'il cane' } },
    { id: 'vocab_macka', word: 'mačka', meanings: { en: 'cat', de: 'die Katze', it: 'il gatto' } },
    { id: 'vocab_kruh', word: 'kruh', meanings: { en: 'bread', de: 'das Brot', it: 'il pane' } },
    { id: 'vocab_voda', word: 'voda', meanings: { en: 'water', de: 'das Wasser', it: "l'acqua" } },
    { id: 'vocab_sola', word: 'šola', meanings: { en: 'school', de: 'die Schule', it: 'la scuola' } },
    { id: 'vocab_knjiga', word: 'knjiga', meanings: { en: 'book', de: 'das Buch', it: 'il libro' } },
    { id: 'vocab_okno', word: 'okno', meanings: { en: 'window', de: 'das Fenster', it: 'la finestra' } },
    { id: 'vocab_sonce', word: 'sonce', meanings: { en: 'sun', de: 'die Sonne', it: 'il sole' } },
    { id: 'vocab_jabolko', word: 'jabolko', meanings: { en: 'apple', de: 'der Apfel', it: 'la mela' } },
  ],
}

const PATTERNS: Record<'it' | 'sl', { id: string; description: string; category: string; cards: string[] }[]> = {
  it: [
    { id: 'articles_il_la', description: 'Articoli: il / la', category: 'grammar', cards: ['vocab_casa', 'vocab_finestra'] },
    { id: 'apostrophe_l', description: "L'apostrofo: l'acqua", category: 'spelling', cards: ['vocab_acqua'] },
  ],
  sl: [
    { id: 'dual_endings', description: 'Dvojina: -a / -i', category: 'grammar', cards: ['vocab_hisa', 'vocab_knjiga'] },
    { id: 'accusative_fem', description: 'Tožilnik ženskega spola: -o', category: 'grammar', cards: ['vocab_macka'] },
  ],
}

/**
 * Writes a learner's learning into [dataDir] (its six databases from `lani-profile add`): the cards of [language] with
 * meanings in [base], two error patterns with mistakes this week and one from long ago, sessions on the last [streak]
 * days. [secret] goes into everything that must never leave the town.
 */
export function writeLearning(dataDir: string, o: { language: 'it' | 'sl'; base: string; secret?: string; streak?: number; now?: number }) {
  const now = o.now ?? Date.now()
  // calendar days, not 24-hour steps: across a change to or from summer time a day has 23 or 25 hours, and n × 24 h from
  // the day's last hour lands two sessions on one date (the streak one short)
  const day = (n: number) => {
    const d = new Date(now)
    d.setDate(d.getDate() + n)
    return localDate(d.getTime())
  }
  const secret = o.secret ?? 'PRIVATE'
  const words = WORDS[o.language]
  const items: Record<string, any> = {}
  words.forEach((w, k) => {
    // the newest first: two this week, the rest over the weeks before; the 2nd rusty, the 1st still being learned
    const created = day(-[0, 1, 3, 6, 9, 12, 15, 20, 26, 33][k])
    const rusty = k === 1
    const fresh = k === 0
    items[w.id] = {
      item_id: w.id,
      type: 'vocabulary',
      content: w.word,
      answer: w.meanings[o.base] ?? w.meanings.en,
      category: 'pack',
      created_date: created,
      due_date: rusty ? day(0) : day(k % 4),
      interval_days: rusty ? 1 : 3,
      repetitions: rusty ? 0 : fresh ? 0 : 2,
      easiness_factor: rusty ? 2.1 : 2.5,
      consecutive_correct: rusty ? 0 : 1,
      consecutive_incorrect: rusty ? 1 : 0,
      last_reviewed: day(-1),
      last_quality: rusty ? 2 : fresh ? 3 : 4,
      mastery_level: rusty || fresh ? 0 : 2,
      total_reviews: fresh ? 0 : 2,
      // the 4th failed a review this week and got it right since: a hard word, not a rusty one
      review_history: fresh ? [] : [{ date: day(-2), quality: k === 3 ? 2 : 4, score: k === 3 ? 0 : 1 }, { date: day(-1), quality: rusty ? 2 : 4, score: rusty ? 0 : 1 }],
      // the newest was added from a word's card, not answered yet: being learned
      ...(fresh ? { source: 'lookup' } : {}),
      example: `${secret}-EXAMPLE-SENTENCE`,
    }
  })
  const patterns: Record<string, any> = {}
  for (const [n, p] of PATTERNS[o.language].entries()) {
    patterns[p.id] = {
      category: p.category,
      subcategory: '',
      description: p.description,
      severity: 'moderate',
      frequency: 4 - 2 * n,
      mastery_level: 0,
      difficulty_score: 0.5,
      last_seen: day(-1),
      last_occurred: day(-1),
      next_review: day(0),
      consecutive_correct: 0,
      consecutive_incorrect: 2,
      // what the learner typed, the tutor's correction and its context: never shared (the context only names the cards)
      examples: p.cards.map((c, i) => ({ incorrect: `${secret}-TYPED-${i}`, correct: `${secret}-CORRECTED-${i}`, context: `review of ${c}: ${secret}-CONTEXT`, date: day(-i) })),
      notes: `${secret}-NOTES`,
    }
    // an error pattern's own card holds what was typed
    items[p.id] = { item_id: p.id, type: 'error_pattern', content: `${secret}-TYPED-CARD`, answer: `${secret}-CORRECTED-CARD`, created_date: day(-5), due_date: day(1), repetitions: 0, total_reviews: 0, last_quality: 2 }
  }
  patterns.old_pattern = { category: 'grammar', description: `${secret}-OLD-PATTERN`, frequency: 9, last_seen: day(-60), examples: [], consecutive_incorrect: 1 }

  const sessions = Array.from({ length: o.streak ?? 4 }, (_, k) => ({
    session_id: `session-${String(k + 1).padStart(3, '0')}`,
    date: day(-k),
    duration_minutes: [12, 8, 15, 6, 10][k % 5],
    skill_practiced: 'vocabulary',
    command_used: '/lani-app-review',
    exercises_completed: 10,
    exercises_correct: 8,
    exercises_incorrect: 2,
    accuracy: 0.8,
    notes: `${secret}-SESSION-NOTE`,
  }))

  const write = (f: string, fn: (d: any) => any) => {
    const p = join(dataDir, f)
    writeFileSync(p, JSON.stringify(fn(JSON.parse(readFileSync(p, 'utf8'))), null, 2) + '\n')
  }
  write('spaced-repetition.json', d => ({ ...d, items }))
  write('mistakes-db.json', d => ({ ...d, error_patterns: patterns }))
  write('session-log.json', d => ({ ...d, sessions }))
  write('learner-profile.json', d => ({ ...d, last_updated: day(0), learner: { ...d.learner, current_level: 'A1', target_level: 'A2', motivation: `${secret}-MOTIVATION` } }))
  return { words, patterns: PATTERNS[o.language] }
}
