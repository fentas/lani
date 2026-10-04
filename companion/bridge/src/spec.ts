// lani.module/v0 — the contract between Claude (author) and the app (renderer).
// Keep in sync with .claude/skills/lani-studio/reference/module-spec.md.
import { z } from 'zod'
import { schemaField } from './schema'
import { grammarRef } from './grammar'

const text = z.string().trim().min(1)
const explain = text.optional()
// Shown small above the prompt: what to do ("Kaj pomeni?"). The prompt itself is shown large.
const instruction = text.optional()
// Slovene text behind a read-aloud button next to the prompt (not auto-played).
const say = text.optional()
// The grammar book's page of the rule the exercise practises (lani.grammar/v0, grammar.ts): meeting it unlocks the page,
// and its "why" leads there.
const grammar = grammarRef.optional()

const flashcard = z.object({
  type: z.literal('flashcard'),
  grammar,
  front: text,
  back: text,
  note: explain,
  speak: z.boolean().default(true), // app reads `front` aloud with sl-SI TTS
})

const choice = z
  .object({
    type: z.literal('choice'),
    grammar,
    prompt: text,
    audio: text.optional(), // Slovene text played via TTS (listening exercise)
    options: z.array(text).min(2).max(6),
    answer: z.number().int().min(0),
    explain,
    instruction,
    say,
    say_options: z.boolean().default(false), // read-aloud button on each (Slovene) option
  })
  .refine(e => e.answer < e.options.length, { message: 'answer index out of range', path: ['answer'] })

const cloze = z.object({
  type: z.literal('cloze'),
  grammar,
  text: text.refine(s => s.includes('___'), 'text must contain a ___ gap'),
  accept: z.array(text).min(1),
  hint: explain,
  explain,
  instruction,
})

const reorder = z
  .object({
    type: z.literal('reorder'),
    grammar,
    prompt: text,
    tokens: z.array(text).min(2).max(12),
    distractors: z.array(text).max(6).default([]), // extra tiles that belong in no solution
    solutions: z.array(z.array(text)).min(1),
    explain,
    instruction,
    say,
  })
  .superRefine((e, ctx) => {
    // Case-insensitive: capitalisation follows position (Tudi jaz… / Jaz se tudi…).
    const key = (xs: string[]) => xs.map(x => x.toLocaleLowerCase('sl')).sort().join('\u0000')
    e.solutions.forEach((sol, i) => {
      if (key(sol) !== key(e.tokens)) {
        ctx.addIssue({ code: 'custom', message: 'solution must be a permutation of tokens', path: ['solutions', i] })
      }
    })
  })

const translate = z.object({
  type: z.literal('translate'),
  grammar,
  prompt: text,
  accept: z.array(text).min(1),
  grade: z.enum(['match', 'claude']).default('match'),
  explain,
  instruction,
  say,
})

const free = z.object({
  type: z.literal('free'),
  grammar,
  prompt: text,
  rubric: text,
  grade: z.literal('claude').default('claude'),
})

// Select all that apply.
const multi = z
  .object({
    type: z.literal('multi'),
    grammar,
    prompt: text,
    options: z.array(text).min(3).max(8),
    answers: z.array(z.number().int().min(0)).min(1),
    explain,
    instruction,
    say_options: z.boolean().default(false),
  })
  .refine(e => e.answers.every(a => a < e.options.length), { message: 'answer index out of range', path: ['answers'] })
  .refine(e => new Set(e.answers).size === e.answers.length, { message: 'duplicate answer index', path: ['answers'] })

// A situation: someone says something (spoken via TTS), the learner responds by picking or typing.
const scenario = z
  .object({
    type: z.literal('scenario'),
    grammar,
    scene: text, // e.g. "🥖 Bakery in Nova Gorica, 8:00"
    speaker: text.optional(), // e.g. "Baker"
    line: text.optional(), // what the speaker says, in Slovene
    prompt: text, // what the learner should do
    options: z.array(text).min(2).max(6).optional(),
    answer: z.number().int().min(0).optional(),
    accept: z.array(text).min(1).optional(),
    explain,
  })
  .superRefine((e, ctx) => {
    const picks = e.options !== undefined && e.answer !== undefined
    const types = e.accept !== undefined
    if (picks === types) ctx.addIssue({ code: 'custom', message: 'use either options+answer or accept, not both' })
    if (picks && e.answer! >= e.options!.length) ctx.addIssue({ code: 'custom', message: 'answer index out of range', path: ['answer'] })
  })

// Listening and spelling: the Slovene is only heard (TTS), never shown; the learner types it.
const dictation = z.object({
  type: z.literal('dictation'),
  grammar,
  audio: text,
  accept: z.array(text).min(1),
  explain,
  instruction,
})

// Speaking: the learner says `say` out loud; the app grades it with speech recognition.
const speak = z.object({
  type: z.literal('speak'),
  grammar,
  say: text, // the Slovene to produce (the model answer, played with TTS when shown)
  prompt: text, // the situation or meaning, in English
  accept: z.array(text).optional(), // other valid ways to say it
  show: z.boolean().default(true), // false: `say` stays hidden until the answer, produce it from `prompt` alone
  explain,
  instruction,
})

export const exercise = z.union([flashcard, choice, cloze, reorder, translate, free, multi, scenario, dictation, speak])

const resource = z.enum(['food', 'wood', 'stone', 'wisdom'])

// A module id: it names the module's directory (store.ts), so kebab-case only.
const MODULE_ID = /^[a-z0-9][a-z0-9-]{1,62}$/

// Makes a module a village sidequest in the app's game ("Moja vas"). See companion/GAME.md.
const quest = z.object({
  giver: text, // "Babica Micka"
  emoji: text.default('🧑'),
  story: text, // one or two sentences: why the villager needs Jan's help
  skill: resource.optional(), // which skill it trains
  reward: z.partialRecord(resource, z.number().int().min(0).max(500)).default({}),
  // A smaller drill for a task Jan keeps failing: the module id of that open task, at the same giver. The app keeps the
  // task waiting behind this drill and brings it back once the drill is passed.
  helps: z.string().regex(MODULE_ID, 'quest.helps must be a module id (kebab-case, 2-63 chars)').optional(),
})

export const moduleSpec = z
  .object({
    schema: schemaField('lani.module/v0'),
    id: z.string().regex(MODULE_ID, 'id must be kebab-case, 2-63 chars'),
    title: text,
    description: explain,
    level: z.enum(['A1', 'A2', 'B1', 'B2', 'C1', 'C2']),
    targets: z.array(text).default([]), // mistakes-db pattern ids this module drills
    tags: z.array(text).default([]),
    exercises: z.array(exercise).min(1).max(50),
    quest: quest.optional(),
  })
  .refine(m => m.quest?.helps !== m.id, {
    message: 'quest.helps names this module itself: it must be the id of another open task at the same giver',
    path: ['quest', 'helps'],
  })

export type ModuleSpec = z.infer<typeof moduleSpec>

export type Validation = { ok: true; spec: ModuleSpec } | { ok: false; errors: string }

export function validateModule(input: unknown): Validation {
  const r = moduleSpec.safeParse(input)
  return r.success ? { ok: true, spec: r.data } : { ok: false, errors: z.prettifyError(r.error) }
}
