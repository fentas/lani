// The learner's own voice, "🗣️ Moj glas · My voice" (companion/SCENES.md, "The learner's voice"; docs/plans/05-built/
// my-voice.md): the voice their own lines in a dialog are said in. They choose it from the cast's `players` for their
// gender and age (cast.ts optionsOf: 2–3 voices, a child's for a child), or turn their lines off. The choice is the
// learner's, kept in their learner profile (learner-profile.json `preferences.my_voice`), so the bridge, voice-build and a
// visit say their lines in it; without one, the default: the first voice of their gender (the female one's while the
// profile says none) and age (the profile's child flag, LANI_CHILD: Teo a boy's, Jan Luka).
//
// Only node:fs and node:path: the phone's bridge (QuickJS) runs this too.
import { readFileSync, renameSync, statSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import type { Addressee } from './addressee'
import { optionsOf, playerOption, type Age, type Gender, type PlayerOption } from './cast'

/** The choice: a voice of the cast's `players` as the cast names it ("player-boy-b", "male.sl"), [OFF], or [DEFAULT]. */
export type Choice = string
/** The learner's lines are not said. */
export const OFF = 'off'
/** The learner saw the choice and keeps the default (it follows their gender and age, and the cast). */
export const DEFAULT = 'default'
/** What a choice may be: off, default, or a voice of a cast. */
export const CHOICE = /^(off|default|[a-z][a-z0-9-]{0,31}(\.[a-z]{2})?)$/

/**
 * The learner's voice now: [options], the voices offered (their gender's, both genders' while the profile says none;
 * their age's), the default first; [option], the one their lines are said in (the choice, else the default; also while
 * they're [off], for an app that asks anyway: an older one on another device), undefined when the cast has none that may
 * say them (the phone speaks); [chosen]: the learner has chosen (a voice, off, or the default), so the app doesn't offer
 * the choice again; [choice] as kept.
 */
export type MyVoice = { options: PlayerOption[]; option?: PlayerOption; off: boolean; chosen: boolean; choice?: Choice; age: Age }

/** The learner's voice for a learner of [gender] (undefined: the profile doesn't say), a [child] or not, who chose [choice]. */
export function myVoice(o: { gender?: Gender; child: boolean; choice?: Choice }): MyVoice {
  const age: Age = o.child ? 'child' : 'adult'
  const genders: Gender[] = o.gender ? [o.gender] : ['female', 'male']
  const options = genders.flatMap(g => optionsOf(g, age))
  const picked = o.choice && o.choice !== OFF && o.choice !== DEFAULT ? options.find(x => x.id === o.choice) : undefined
  return {
    options,
    option: picked ?? playerOption(o.gender, age),
    off: o.choice === OFF,
    chosen: o.choice !== undefined,
    ...(o.choice !== undefined ? { choice: o.choice } : {}),
    age,
  }
}

/**
 * The voice, as the store speaks it, that [dataDir]'s learner ([a]: the profile's gender, said or not; [child]) hears their
 * own lines in now: their choice, else the default; undefined while they're off, or when the cast has none (voice-build and
 * the status voice their lines in it: corpus `mine`).
 */
export function mineVoice(dataDir: string, a: Pick<Addressee, 'gender' | 'unsaid'>, child: boolean): string | undefined {
  const m = myVoice({ gender: a.unsaid ? undefined : a.gender, child, choice: choiceOf(dataDir) })
  return m.off ? undefined : m.option?.voice
}

const PROFILE = 'learner-profile.json'
const read = new Map<string, { mtime: number; choice?: Choice }>()

/** The choice in [dataDir]'s learner profile (`preferences.my_voice.voice`), read again when it changes; undefined: none yet. */
export function choiceOf(dataDir: string): Choice | undefined {
  const path = join(dataDir, PROFILE)
  let mtime = -1
  try {
    mtime = statSync(path).mtimeMs
  } catch {
    // no profile yet: no choice
  }
  const hit = read.get(path)
  if (hit && hit.mtime === mtime) return hit.choice
  let choice: Choice | undefined
  try {
    const v = JSON.parse(readFileSync(path, 'utf8'))?.preferences?.my_voice
    const c = typeof v === 'string' ? v : v?.voice
    if (typeof c === 'string' && CHOICE.test(c)) choice = c
  } catch {
    // unreadable: as if none
  }
  read.set(path, { mtime, choice })
  return choice
}

/**
 * Keeps [choice] in [dataDir]'s learner profile: `preferences.my_voice` = {voice, since: [today]}, everything else as it
 * is (read just before, written to a file beside it and renamed over it). Throws when there's no profile to keep it in.
 */
export function writeChoice(dataDir: string, choice: Choice, today: string) {
  if (!CHOICE.test(choice)) throw new Error(`not a voice choice: ${choice}`)
  const path = join(dataDir, PROFILE)
  const p = JSON.parse(readFileSync(path, 'utf8'))
  if (!p || typeof p !== 'object' || Array.isArray(p)) throw new Error(`${PROFILE} isn't a learner profile`)
  if (!p.preferences || typeof p.preferences !== 'object' || Array.isArray(p.preferences)) p.preferences = {}
  p.preferences.my_voice = { voice: choice, since: today }
  const tmp = `${path}.voice.tmp`
  writeFileSync(tmp, JSON.stringify(p, null, 2) + '\n')
  renameSync(tmp, path)
  read.delete(path)
}

/**
 * The sample a voice to choose says (its ▶): a short line in the village's [language] with the learner's name, "Ciao!
 * Sono Teo."; without a name, a greeting and a question. Written by Claude (docs/plans/05-built/my-voice.md: for a native
 * speaker's check).
 */
const SAMPLES: Record<string, { named: string; unnamed: string }> = {
  sl: { named: 'Živjo! Jaz sem {name}.', unnamed: 'Živjo! Kako si?' },
  it: { named: 'Ciao! Sono {name}.', unnamed: 'Ciao! Come stai?' },
  de: { named: 'Hallo! Ich bin {name}.', unnamed: 'Hallo! Wie geht es dir?' },
  en: { named: "Hello! I'm {name}.", unnamed: 'Hello! How are you?' },
}

/** [a]'s sample line in [language] (Slovene's for a language without one). */
export function sampleLine(language: string, a: Pick<Addressee, 'name' | 'generic'>): string {
  const s = SAMPLES[language] ?? SAMPLES.sl
  return a.generic || !a.name.trim() ? s.unnamed : s.named.replace('{name}', a.name.trim())
}
