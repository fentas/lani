// The learner's aim: the target level the village's ages lead to (companion/GAME.md "Ages", "A target raised later";
// docs/plans/05-built/target-next-age.md). learner-profile.json keeps it as learner.target_level, and a raise that starts
// with the village's next age as learner.target_level_next = {level, from_age, set}: the step the village is on keeps the
// words of the target it began with, and from the age `from_age` on the new target counts. The bridge owns the profile, so
// it applies the raise (target_level = level, the pending one gone, target_level_since the day) where it sees the village
// at that age: the app's save of the village (PUT /game), on a node and on the phone's own bridge alike. The app counts
// with the same rule before the bridge has it (game/Aims.kt): whoever reaches the age, the target is the same, and
// applying it again changes nothing. A lowered target never makes a step harder: it applies at once.
//
// Pure where it can be: the file helpers read and write learner-profile.json as the other writers do (read just before,
// written beside it and renamed over it). No imports beyond node: learners.ts (and through it the phone's entry) uses it.
import { readFileSync, renameSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

export const TARGET_LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'] as const
export type TargetLevel = (typeof TARGET_LEVELS)[number]

/** The village's ages in order (the app's game/Model.kt Age), as game.json's state.age names them. */
export const AGES = ['OGENJ', 'TABOR', 'ZASELEK', 'VAS', 'TRG', 'MESTO'] as const
export type VillageAge = (typeof AGES)[number]

/** A raised target that starts with the village's age [from_age]; [set]: the day it was set. */
export type TargetNext = { level: TargetLevel; from_age: VillageAge; set?: string }

/** The target a new learner aims at, unless they choose another: B2 (Jan, 2026-10-31: "b2 should be default aim"). */
export const DEFAULT_TARGET: TargetLevel = 'B2'

export const levelOf = (v: unknown): TargetLevel | undefined => {
  const u = typeof v === 'string' ? v.trim().toUpperCase() : ''
  return (TARGET_LEVELS as readonly string[]).includes(u) ? (u as TargetLevel) : undefined
}

export const ageOf = (v: unknown): VillageAge | undefined => {
  const u = typeof v === 'string' ? v.trim().toUpperCase() : ''
  return (AGES as readonly string[]).includes(u) ? (u as VillageAge) : undefined
}

const rank = (l: TargetLevel) => TARGET_LEVELS.indexOf(l)
const ageRank = (a: VillageAge) => AGES.indexOf(a)

/** The age after [age]; undefined after the last (Mesto). */
export const nextAge = (age: VillageAge): VillageAge | undefined => AGES[ageRank(age) + 1]

/**
 * A new learner's target at [level]: B2, or the level above theirs when they start at B2 or higher (C2 stays C2). It
 * replaced "one level above" (2026-10-31); the learner can still choose another (setup's answers, `lani-profile set-target`).
 */
export function defaultTarget(level: unknown): TargetLevel {
  const l = levelOf(level) ?? 'A1'
  return rank(l) < rank(DEFAULT_TARGET) ? DEFAULT_TARGET : TARGET_LEVELS[Math.min(rank(l) + 1, TARGET_LEVELS.length - 1)]
}

/** The profile's target (learner.target_level), B2 when it names none (the app's Catalog.DEFAULT_TARGET). */
export const targetOf = (learner: any): TargetLevel => levelOf(learner?.target_level) ?? DEFAULT_TARGET

/** The profile's pending target (learner.target_level_next), when it is a good one: a level and an age; else undefined. */
export function pendingOf(learner: any): TargetNext | undefined {
  const n = learner?.target_level_next
  if (!n || typeof n !== 'object') return undefined
  const level = levelOf(n.level)
  const from = ageOf(n.from_age)
  if (!level || !from) return undefined
  return { level, from_age: from, ...(typeof n.set === 'string' && n.set ? { set: n.set } : {}) }
}

/** The target the village's step from [age] counts with: the pending one once the village has reached its age. */
export function aimAt(learner: any, age: VillageAge): TargetLevel {
  const n = pendingOf(learner)
  return n && ageRank(age) >= ageRank(n.from_age) ? n.level : targetOf(learner)
}

/**
 * Words learned to reach each age on the way to [target] (the app's Catalog.ageWords; GAME.md "Ages"): each age's share
 * of the words the level needs (Stats.wordsToReach: A2 300, B1 1000, B2 2000, C1 3500, C2 5000, A1 as A2), at least its
 * minimum, and always more than the age before. Ogenj, the first age, needs none.
 */
export function ageWords(target: unknown): Record<VillageAge, number> {
  const reach = [300, 300, 1000, 2000, 3500, 5000][rank(levelOf(target) ?? DEFAULT_TARGET)]
  const rules: [VillageAge, number, number][] = [['TABOR', 0, 10], ['ZASELEK', 0.03, 40], ['VAS', 0.15, 0], ['TRG', 0.5, 0], ['MESTO', 1, 0]]
  const out = { OGENJ: 0 } as Record<VillageAge, number>
  let before = 0
  for (const [age, share, min] of rules) {
    before = Math.max(Math.round(reach * share), min, before + 1)
    out[age] = before
  }
  return out
}

/**
 * Applies the pending target once the village is at [age] or past its age: target_level becomes it, target_level_since
 * [today], the pending one goes. Mutates [learner]; true when it changed. Applying it again finds none: nothing changes.
 */
export function applyDue(learner: any, age: VillageAge, today: string): boolean {
  const n = pendingOf(learner)
  if (!learner || typeof learner !== 'object') return false
  if (!n) {
    // one that can't be read is dropped where it is seen, so it never stands in the way
    if (learner.target_level_next !== undefined) {
      delete learner.target_level_next
      return true
    }
    return false
  }
  if (ageRank(age) < ageRank(n.from_age)) return false
  learner.target_level = n.level
  learner.target_level_since = today
  delete learner.target_level_next
  return true
}

/** What [setTarget] did: at once, from the next age, or nothing (the target and no pending one as asked). */
export type TargetChange =
  | { mode: 'now'; level: TargetLevel; before: TargetLevel; why: 'lower' | 'same' | 'asked' | 'last-age' }
  | { mode: 'next_age'; level: TargetLevel; before: TargetLevel; from_age: VillageAge }
  | { mode: 'unchanged'; level: TargetLevel }

/**
 * Sets [level] as the learner's target, the village at [age] ([learner] mutated). A raise with [fromNextAge] waits for the
 * village's next age (target_level_next); a raise without it, or with the village at its last age (no step left to keep),
 * applies at once. A lower or the same target applies at once, since it never makes the current step harder; either
 * drops a pending raise (so the current target asked again takes a pending raise back).
 */
export function setTarget(learner: any, level: TargetLevel, age: VillageAge, o: { fromNextAge: boolean; today: string }): TargetChange {
  const before = targetOf(learner)
  const pending = pendingOf(learner)
  const at = nextAge(age)
  if (rank(level) > rank(before) && o.fromNextAge && at) {
    if (pending?.level === level && pending.from_age === at) return { mode: 'unchanged', level }
    learner.target_level_next = { level, from_age: at, set: o.today }
    return { mode: 'next_age', level, before, from_age: at }
  }
  if (level === before && !pending && learner.target_level === level) return { mode: 'unchanged', level }
  learner.target_level = level
  if (level !== before) learner.target_level_since = o.today
  delete learner.target_level_next
  // the same target: a raise waiting taken back (or the default B2 written down: as asked)
  const why = rank(level) < rank(before) ? 'lower' : level === before ? (pending ? 'same' : 'asked') : !at ? 'last-age' : 'asked'
  return { mode: 'now', level, before, why }
}

/**
 * The aim as the tutor and the CLI say it, the village at [age]: the target the step it is on counts with, the next age
 * and the words it needs; with a pending raise, its level, the age it starts with and the age and words after that.
 */
export function describeAim(learner: any, age: VillageAge) {
  const target = aimAt(learner, age)
  const next = nextAge(age)
  const n = pendingOf(learner)
  const waiting = n && ageRank(age) < ageRank(n.from_age) ? n : undefined
  const after = waiting ? nextAge(waiting.from_age) : undefined
  return {
    target,
    age,
    ...(next ? { next_age: next, words_for_next_age: ageWords(target)[next] } : {}),
    ...(waiting
      ? {
          from_next_age: {
            level: waiting.level,
            from_age: waiting.from_age,
            ...(waiting.set ? { set: waiting.set } : {}),
            ...(after ? { then_age: after, then_words: ageWords(waiting.level)[after] } : {}),
          },
        }
      : {}),
  }
}

const AGE_NAMES: Record<VillageAge, string> = { OGENJ: 'Ogenj (the campfire)', TABOR: 'Tabor', ZASELEK: 'Zaselek', VAS: 'Vas', TRG: 'Trg', MESTO: 'Mesto' }

/**
 * [change] in plain words, the village at [age] and [learner] as it is after it: what the step the village is on needs
 * now, and from when the new target counts and what the next step then needs. For the tutor's tool and the CLI.
 */
export function explainChange(change: TargetChange, learner: any, age: VillageAge): string {
  const next = nextAge(age)
  const step = (target: TargetLevel) => (next ? `${ageWords(target)[next]} words for ${AGE_NAMES[next]}` : 'nothing more: the village is a town')
  const where = `The village is ${AGE_NAMES[age]}`
  if (change.mode === 'unchanged') {
    const n = pendingOf(learner)
    return n
      ? `Nothing changed: the target is ${targetOf(learner)} and ${n.level} starts with ${AGE_NAMES[n.from_age]} already.`
      : `Nothing changed: the target is ${change.level} already. ${where}: the step it is on needs ${step(change.level)}.`
  }
  if (change.mode === 'next_age') {
    const after = nextAge(change.from_age)
    return (
      `The target stays ${change.before} for the step the village is on and becomes ${change.level} with the next age. ${where}: the step ` +
      `to ${AGE_NAMES[change.from_age]} keeps ${ageWords(change.before)[change.from_age]} words; from ${AGE_NAMES[change.from_age]} on, ` +
      (after ? `${AGE_NAMES[after]} needs ${ageWords(change.level)[after]} words (it needed ${ageWords(change.before)[after]}).` : `the target is ${change.level}.`) +
      ` The app says "From the next age: ${change.level}" on the age card and the progress screen.`
    )
  }
  if (change.why === 'same') return `The target stays ${change.level}; the raise that waited for the next age is taken back. ${where}: the step it is on needs ${step(change.level)}.`
  const why = { lower: 'a lower target applies at once', asked: 'at once, as asked', 'last-age': 'at once: the village is a town, no age is left to wait for' }[change.why]
  const was = next ? ageWords(change.before)[next] : undefined
  return `The target is ${change.level} now (it was ${change.before}; ${why}). ${where}: the step it is on needs ${step(change.level)}${next && was !== ageWords(change.level)[next] ? ` (it needed ${was})` : ''}.`
}

// --- the profile's file -------------------------------------------------------------------------------------------------

const PROFILE = 'learner-profile.json'

/** [dataDir]'s learner profile; throws when it can't be read. */
export function readProfile(dataDir: string): any {
  const p = JSON.parse(readFileSync(join(dataDir, PROFILE), 'utf8'))
  if (!p || typeof p !== 'object' || Array.isArray(p)) throw new Error(`${PROFILE} isn't a learner profile`)
  return p
}

/** Writes [profile] as [dataDir]'s learner profile: beside it, then renamed over it. */
export function writeProfile(dataDir: string, profile: any) {
  const path = join(dataDir, PROFILE)
  writeFileSync(`${path}.aim.tmp`, JSON.stringify(profile, null, 2) + '\n')
  renameSync(`${path}.aim.tmp`, path)
}

/** The village's age in [dataDir]'s app/game.json ({rev, state}); Ogenj when there is no village yet or it can't be read. */
export function villageAge(dataDir: string): VillageAge {
  try {
    return ageOf(JSON.parse(readFileSync(join(dataDir, 'app', 'game.json'), 'utf8'))?.state?.age) ?? 'OGENJ'
  } catch {
    return 'OGENJ'
  }
}

/**
 * The pending target of [dataDir]'s learner applied, the village at [age], when it is due: the level it applied, else
 * undefined (none, not yet, or no profile to keep it in). Written only when it changed.
 */
export function applyPendingTarget(dataDir: string, age: VillageAge, today: string): TargetLevel | undefined {
  let p: any
  try {
    p = readProfile(dataDir)
  } catch {
    return undefined
  }
  const l = p.learner
  if (!l || typeof l !== 'object' || l.target_level_next === undefined) return undefined
  const n = pendingOf(l)
  if (!applyDue(l, age, today)) return undefined
  writeProfile(dataDir, p)
  return n && l.target_level === n.level ? n.level : undefined
}

/**
 * Sets [level] as the target of [dataDir]'s learner (the village at its age in app/game.json, [setTarget]'s rules), written
 * when it changed: what changed and the words for it ([explainChange]). Throws when there's no profile.
 */
export function setTargetIn(dataDir: string, level: TargetLevel, o: { fromNextAge: boolean; today: string; age?: VillageAge }) {
  const p = readProfile(dataDir)
  if (!p.learner || typeof p.learner !== 'object' || Array.isArray(p.learner)) p.learner = {}
  const age = o.age ?? villageAge(dataDir)
  // one already due (the village past its age while no save came through) is applied first: it was the target already
  const due = applyDue(p.learner, age, o.today)
  const change = setTarget(p.learner, level, age, o)
  if (due || change.mode !== 'unchanged') writeProfile(dataDir, p)
  return { change, age, text: explainChange(change, p.learner, age), aim: describeAim(p.learner, age) }
}
