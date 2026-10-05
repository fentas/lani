// lani.scene/v0 — close-up scenes of the village: objects that teach pack words, people, daily
// happenings and short dialogs (companion/SCENES.md). Curated scenes live in companion/scenes/*.json (a
// village of another language: companion/scenes/<language>/), and a culture pack brings its own places in
// companion/cultures/<id>/scenes/*.json (scenesDirs); the tutor publishes more to <data>/app/scenes (a tutor
// scene with a curated id replaces it).
// Keep in sync with .claude/skills/lani-studio/reference/module-spec.md ("Scenes").
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, statSync, unlinkSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { learnerErrorsIn, sameFor } from './addressee'
import { schemaField } from './schema'
import type { Pack } from './packs'
import type { Gender } from './cast'
import { countErrors, countSpec } from './counts'
import { grammarRef } from './grammar'
import { label, labelTarget, langText, type Label } from './langs'

/**
 * What the app's painters can draw, mirrored from SceneArt.kt (arts with their object slots, person
 * spots and effects, the people sprites) and Model.kt (building types, ages). The smoke test parses
 * those files and compares, so the two can't drift.
 */
export const SCENE_ART = {
  objects: {
    campfire: ['fire', 'logs', 'kettle', 'stones', 'smoke', 'sparks', 'moon', 'stars', 'tent', 'axe', 'bucket', 'stump'],
    kitchen: [
      'stove', 'pot', 'bowl', 'table', 'chair', 'bread', 'plate', 'glass', 'cup', 'spoon', 'fork', 'knife',
      'window', 'door', 'cupboard', 'clock', 'milk', 'eggs', 'water', 'salt', 'potatoes',
      'hearth', 'bench', 'bed', 'shelf', 'curtains', 'corner',
    ],
    forest: [
      'tree', 'spruce', 'mushroom', 'berries', 'stream', 'stone', 'path', 'bird', 'deer', 'squirrel', 'owl', 'leaves', 'sheep',
      'highseat', 'hare', 'butterfly', 'fox', 'stag', 'hedgehog', 'boar', 'badger', 'dormouse', 'bat', 'flowers',
    ],
    square: ['linden', 'bench', 'ball', 'well', 'dog', 'cat', 'house', 'tower', 'bicycle', 'flowers', 'sun', 'fence'],
    tent: ['lantern', 'sleepingbag', 'blanket', 'pillow', 'backpack', 'map', 'boots', 'coat', 'hat', 'book', 'cup', 'rope'],
    field: ['wheat', 'corn', 'potatoes', 'cabbage', 'kozolec', 'hay', 'scythe', 'rake', 'basket', 'cart', 'scarecrow', 'fence', 'path', 'deer', 'hare', 'butterfly', 'fox', 'boar', 'bat', 'horse'],
    smithy: ['forge', 'fire', 'anvil', 'hammer', 'tongs', 'bellows', 'horseshoe', 'nails', 'bucket', 'chain', 'axe', 'wheel', 'window', 'door'],
    school: ['blackboard', 'chalk', 'sponge', 'desk', 'chair', 'book', 'notebook', 'pencil', 'bag', 'map', 'globe', 'clock', 'window', 'door'],
    hills: ['vineyard', 'vine', 'grapes', 'hill', 'church', 'belltower', 'farmhouse', 'cypress', 'persimmon', 'wall', 'mountains', 'sea', 'road', 'crate'],
    sea: ['sea', 'lagoon', 'casone', 'boat', 'posts', 'gull', 'wave', 'beach', 'pier', 'shell', 'fish', 'net', 'horizon', 'crab'],
    alps: ['mountain', 'peak', 'lake', 'church', 'bridge', 'pasture', 'hut', 'cow', 'cowbell', 'larch', 'spruce', 'waterfall', 'stream', 'stone', 'wall', 'chamois', 'ibex', 'path'],
    stream: ['stream', 'stones', 'bridge', 'willow', 'trout', 'dragonfly', 'cress', 'mill', 'laundry', 'fox', 'hedgehog', 'butterfly', 'bat', 'sheep'],
    pond: ['pond', 'lilies', 'reeds', 'frog', 'ducks', 'jetty', 'boat', 'fish', 'heron', 'deer', 'fox', 'butterfly', 'bat', 'dragonfly'],
    church: ['altar', 'candles', 'cross', 'painting', 'pews', 'flowers', 'window', 'organ', 'font', 'bell', 'rope', 'door'],
    apiary: ['beehouse', 'hive', 'panel', 'bee', 'honey', 'comb', 'flowers', 'tree', 'smoker', 'extractor', 'wax', 'bench'],
    market: [
      'stall', 'scale', 'weights', 'eggs', 'bread', 'cheese', 'honey', 'apples', 'vegetables', 'basket',
      'crate', 'hen', 'cart', 'wine', 'pigeon', 'bell',
    ],
    watchtower: [
      'tower', 'railing', 'torch', 'horn', 'spyglass', 'flag', 'crow', 'roofs', 'palisade', 'gate', 'road',
      'forest', 'river', 'valley', 'mountains', 'horizon',
    ],
    // the rooms of the houses (companion/SCENES.md, "Houses and their rooms")
    livingroom: [
      'stove', 'bench', 'table', 'chest', 'window', 'door', 'dog', 'chair', 'curtains', 'rug', 'lamp', 'clock',
      'corner', 'photo', 'glasses', 'book',
    ],
    workshop: [
      'workbench', 'hammer', 'nails', 'boards', 'axe', 'broom', 'window', 'door', 'saw', 'tools', 'ladder', 'rake',
      'wheelbarrow', 'horse', 'lantern',
    ],
    cellar: [
      'barrel', 'press', 'stairs', 'door', 'window', 'candle', 'jug', 'glass', 'crate', 'shelf', 'bottle', 'grapes',
      'wine', 'ham', 'cheese',
    ],
    attic: [
      'bed', 'pillow', 'blanket', 'cradle', 'chest', 'roof', 'window', 'stairs', 'wardrobe', 'rug', 'lamp', 'ball',
      'mirror', 'toy',
    ],
  } as Record<string, string[]>,
  personSlots: {
    campfire: ['left', 'right', 'back'],
    kitchen: ['stove', 'table', 'door'],
    forest: ['path', 'clearing', 'highseat'],
    square: ['bench', 'well', 'play1', 'play2', 'play3'],
    tent: ['left', 'right', 'door'],
    field: ['field', 'kozolec', 'path'],
    smithy: ['anvil', 'forge', 'door'],
    school: ['teacher', 'desk1', 'desk2', 'door'],
    hills: ['vines', 'wall', 'road'],
    sea: ['beach', 'pier', 'boat'],
    alps: ['path', 'wall', 'stream'],
    stream: ['bank', 'ford', 'mill'],
    pond: ['shore', 'reeds', 'jetty'],
    church: ['altar', 'aisle', 'door'],
    apiary: ['hives', 'table', 'bench'],
    market: ['stall', 'buyer', 'cart'],
    watchtower: ['watch', 'rail', 'hatch'],
    livingroom: ['stove', 'table', 'door'],
    workshop: ['bench', 'floor', 'door'],
    cellar: ['barrel', 'press', 'stairs'],
    attic: ['bed', 'chest', 'stairs'],
  } as Record<string, string[]>,
  people: ['grandma', 'grandpa', 'child1', 'child2', 'child3', 'shepherd', 'smith', 'teacher', 'farmer', 'beekeeper', 'innkeeper', 'winemaker', 'aunt', 'pedlar', 'pilgrim', 'woman', 'man', 'baby', 'hunter', 'burner'],
  /** What each art's painter shows when a dialog line's `fx` sets it (companion/SCENES.md, "Effects"). */
  effects: {
    campfire: ['flare', 'steam', 'bell'],
    kitchen: ['oven', 'steam'],
    forest: ['birds', 'herd', 'fox', 'hoot', 'bats'],
    square: ['bell'],
    tent: ['lantern'],
    field: ['crows', 'bell', 'bats'],
    smithy: ['forge', 'sparks'],
    school: [],
    hills: ['bell', 'tractor', 'starlings'],
    sea: ['waves', 'gulls', 'sail'],
    alps: ['cowbells', 'bell', 'fish', 'birds', 'glow'],
    stream: ['trout', 'wheel', 'bats', 'bell'],
    pond: ['ducks', 'frog', 'fish', 'bats'],
    church: ['candles', 'bell', 'organ'],
    apiary: ['swarm', 'smoke', 'honey'],
    market: ['bustle', 'bell', 'pigeons'],
    watchtower: ['torch', 'horn', 'birds', 'wolves'],
    livingroom: ['stove', 'dog'],
    workshop: ['dust'],
    cellar: ['tap', 'candle'],
    attic: ['lamp', 'cradle'],
  } as Record<string, string[]>,
  buildings: ['tent', 'field', 'well', 'hut', 'kozolec', 'beehive', 'palisade', 'watchtower', 'smithy', 'lipa', 'house', 'church', 'school', 'market'],
  ages: ['ogenj', 'tabor', 'zaselek', 'vas', 'trg', 'mesto'],
}

export const ARTS = Object.keys(SCENE_ART.objects)

/**
 * The poses someone can take on a spot of a stage (Stance in Stage.kt): standing, sitting, squatting, hidden (only a
 * clue shows), peeking out, lying.
 */
export const POSES = ['stand', 'sit', 'crouch', 'hide', 'peek', 'lie'] as const
/**
 * The arts' stages, mirrored from SceneArt.kt (`stage`; the smoke test compares): the spots a dialog's `act` can send someone
 * to, each with its poses and the object slot that hides someone there (a tap on it is a tap on the spot in a tap turn).
 * An art that isn't here has no stage directions yet (companion/SCENES.md, "Stage directions").
 */
export const SCENE_STAGE: Record<string, Record<string, { poses: string[]; cover?: string }>> = {
  livingroom: {
    stove: { poses: ['stand'] },
    table: { poses: ['stand'] },
    door: { poses: ['stand'] },
    bench: { poses: ['sit'] },
    'under-table': { poses: ['stand', 'hide', 'peek'], cover: 'table' },
    'behind-door': { poses: ['stand', 'hide', 'peek'], cover: 'door' },
  },
  tent: {
    left: { poses: ['stand'] },
    right: { poses: ['stand'] },
    door: { poses: ['stand'] },
    lantern: { poses: ['sit', 'stand'] },
  },
}

/**
 * Which arts and slots an app draws (SceneArt.VERSION in SceneArt.kt): 1 the first seventeen arts, 2 the rooms of the houses
 * and the kitchen's level slots. The app says it when it asks for its scenes (GET /scenes?arts=2); one that doesn't is an
 * older one, which gets what the first arts drew ([forApp]).
 */
export const SCENE_ARTS_VERSION = 2
/** What each version after the first added: its new arts, and new slots of an art the version before had. */
const ADDED: Record<number, { arts: string[]; slots: Record<string, string[]> }> = {
  2: { arts: ['livingroom', 'workshop', 'cellar', 'attic'], slots: { kitchen: ['hearth', 'bench', 'bed', 'shelf', 'curtains', 'corner'] } },
}

/** The arts version an app asked with (`?arts=`): 1, the first, when it doesn't say (an older app). */
export function artsOf(url: URL): number {
  const n = Number(url.searchParams.get('arts'))
  return Number.isInteger(n) && n >= 1 ? n : 1
}

/**
 * [scenes] as an app that draws arts version [version] can paint them: without the places of a later art, nor the things
 * in a later slot of an art it has (an older app's kitchen has no hearth). A later need (a level) it reads past, as
 * everything it doesn't know: it shows the thing, as before.
 */
export function forApp<T extends { art: string; objects: { slot: string }[] }>(scenes: T[], version: number): T[] {
  const later = Object.entries(ADDED).filter(([v]) => Number(v) > version).map(([, a]) => a)
  if (!later.length) return scenes
  const arts = new Set(later.flatMap(a => a.arts))
  const slots = (art: string) => new Set(later.flatMap(a => a.slots[art] ?? []))
  return scenes.filter(s => !arts.has(s.art)).map(s => {
    const drop = slots(s.art)
    return drop.size ? { ...s, objects: s.objects.filter(o => !drop.has(o.slot)) } : s
  })
}
/**
 * The landscape's named spots (TownSpots in TownMarkers.kt; the full table is in villagers.ts). "horizon" is the land at
 * the village's horizon, what its culture pack says is there (world.json `backdrop`): Primorska's hills, Friuli's sea.
 */
export const SPOT_NAMES = ['meadow', 'pond', 'highseat', 'path', 'woodpile', 'rocks', 'riverbank', 'road', 'kopa', 'horizon']
/** The languages a scene's lines can be in (the app's, l10n/Lang.kt): the scene's own, then translations. */
export const SCENE_LANGUAGES = ['sl', 'en', 'it', 'de'] as const
/**
 * Where the village can open a scene: the fire, the forest, a spot of the landscape, or a building of that type; or a
 * village project's landmark ("project:<id>", open once the project is finished: the vineyard's terraces), see PROJECT_PLACE.
 */
export const PLACES = ['fire', 'forest', ...SPOT_NAMES.map(s => `spot:${s}`), ...SCENE_ART.buildings]
/** A village project's landmark as a scene's place: "project:<id>", the id one of the culture's projects (checkNeeds). */
export const PROJECT_PLACE = /^project:[a-z][a-z0-9_]{0,39}$/
export const RESOURCES = ['food', 'wood', 'stone', 'wisdom'] as const
/** The parts of the day a happening can be on in; dawn is the morning's first hours, round sunrise (TimeOfDay in SceneSpec.kt). */
export const TIMES = ['morning', 'afternoon', 'evening', 'night', 'dawn'] as const

/** Which voice a sprite speaks with in the app's clips (a villager's own voice wins when there is one). */
export function spriteVoice(art: string): Gender {
  return ['grandpa', 'shepherd', 'smith', 'farmer', 'beekeeper', 'winemaker', 'man', 'hunter', 'burner'].includes(art) ? 'male' : 'female'
}

const text = z.string().trim().min(1)
const id = z.string().regex(/^[a-z0-9][a-z0-9-]{0,62}$/, 'must be kebab-case, 1-63 chars')
const line = text.max(200)
const emoji = text.max(16)

/**
 * What someone says or the learner chooses, in the scene's language and translations: {"sl", "en"} in a Slovene scene,
 * {"it", "sl", "en"} in an Italian one (English always; crossCheck wants the scene's language and "en").
 */
export const said = { sl: line.optional(), en: line.optional(), it: line.optional(), de: line.optional() }
export const reply = z.object(said)
const level = z.number().min(0).max(1)
/** The weather changes when a line or a reply plays: new levels 0-1 for what it names (companion/SCENES.md, "Sky cues"). */
export const skyCue = z
  .strictObject({ rain: level.optional(), snow: level.optional(), fog: level.optional(), wind: level.optional(), gloom: level.optional(), lightning: z.boolean().optional() })
  .refine(c => Object.keys(c).length > 0, 'a sky cue names at least one of rain, snow, fog, wind, gloom, lightning')
/** The scene's effects change when a line or a reply plays: effect name (one of the art's) → level 0-1 or true / false. */
export const fxCue = z
  .record(z.string().regex(/^[a-z][a-z0-9-]{0,30}$/, 'an effect name is lowercase'), z.union([level, z.boolean()]))
  .refine(c => Object.keys(c).length > 0, 'an fx cue names at least one effect')
/**
 * Stage directions when a line or a reply plays (companion/SCENES.md, "Stage directions"): person id → where they go
 * (`to`, a spot of the art's stage, SCENE_STAGE) and how (`pose`); `to` alone stands there, `pose` alone is taken where they
 * are. crossCheck follows them through the dialog: the people are the scene's, each spot the art's, each pose the spot's.
 */
export const actCue = z
  .record(
    z.string().regex(/^[a-z0-9][a-z0-9-]{0,62}$/, 'a person id of the scene'),
    z.strictObject({ to: z.string().regex(/^[a-z0-9][a-z0-9-]{0,30}$/, 'a spot of the art').optional(), pose: z.enum(POSES).optional() })
      .refine(a => a.to !== undefined || a.pose !== undefined, 'a stage direction names a spot (to), a pose or both'),
  )
  .refine(c => Object.keys(c).length > 0, 'an act cue names at least one person')
const dialogReply = reply.extend({ sky: skyCue.optional(), fx: fxCue.optional(), act: actCue.optional() })
/** Why a wrong choice is wrong: in English, or per language ({"sl": …, "en": …}: the learner reads it in their base). */
const why = z.union([line, langText(200)])
/**
 * The grammar book's page of the rule a wrong choice breaks (lani.grammar/v0, grammar.ts: "kam-tozilnik"): its why
 * leads there, and the pick unlocks it. On the turn, it is every choice's. It is also the rule a form turn's mastery
 * follows (typed or said once the learner has it secure or mastered: companion/SCENES.md, "Adaptive turns"). An older
 * bridge strips the key.
 */
const grammar = grammarRef.optional()
const choice = z.object({
  ...said,
  ok: z.boolean().default(false),
  why: why.optional(),
  grammar,
  reply: dialogReply.optional(),
  /** A counting dialog's wrong choice: its forms take the wrong one learners put there (1), or the next (2): counts.ts. */
  wrong_form: z.union([z.literal(1), z.literal(2)]).optional(),
  /**
   * A tap turn's choice (companion/SCENES.md, "Tap turns"): what it stands for in the picture, a spot of the art's stage, an
   * object slot of the art or a person of the scene; the learner answers by tapping it there. Every choice of the turn has
   * one, or none has.
   */
  tap: z.string().regex(/^[a-z0-9][a-z0-9-]{0,62}$/, 'a spot, a slot of the art or a person id').optional(),
})
export const dialogLine = z.object({
  who: id.optional(),
  ...said,
  /** 2-3 choices; a tap turn up to 4 (crossCheck). A bridge before tap turns reads at most 3 and skips the scene. */
  choices: z.array(choice).max(4).default([]),
  grammar,
  sky: skyCue.optional(),
  fx: fxCue.optional(),
  act: actCue.optional(),
  /**
   * A learner's turn: how the person reacts to a wrong answer with no reaction of its own (the learner's own trap, a form
   * typed wrong); without it the app's "Hm? Kako, prosim?" (companion/SCENES.md, "Own traps").
   */
  puzzled: reply.optional(),
})
export const dialog = z.object({
  id,
  lines: z.array(dialogLine).min(3).max(8),
  talk: id.optional(),
  /** Whether the sky the cues reached stays over the scene and the village until midnight; by default it does. */
  sky_stays: z.boolean().optional(),
  /** Whether the effects the cues set stay in the scene until midnight; by default they go with the dialog. */
  fx_stays: z.boolean().optional(),
  /** Whether where its stage directions leave people stays until midnight; by default they go back to their spots. */
  act_stays: z.boolean().optional(),
  /** Meant for a guest ("Sei di fuori?"): only a visitor from a linked town meets it, never the village's own learner. */
  guests: z.boolean().optional(),
  /** A number from the village it counts: its texts carry {n} and the forms that agree (companion/SCENES.md, "Numbers"). */
  count: countSpec.optional(),
  /** What the person remembers of this variant, instead of the happening's memory. */
  memory: reply.optional(),
  /** A variant the tutor wrote (publish_dialog_variant); the curated ones say nothing. */
  source: z.enum(['curated', 'tutor']).optional(),
})
export type Dialog = z.infer<typeof dialog>
/** The top level a building can be upgraded to (Catalog.MAX_LEVEL in Catalog.kt). */
export const MAX_LEVEL = 5

/**
 * What an object or a happening needs to be there besides its scene being open (companion/SCENES.md, "What the village has
 * built"): an age reached, a building standing (a building type, "KOZOLEC"; any case), at least at `level` when it says one
 * (the one the scene is opened from, when it is of that type), a village project of the culture with at least `step` of its
 * steps done (by default all of them: finished); a `level` alone is the place's own: the building the scene is opened from
 * (the hut or the house tapped) at least at that level. With `before`, the other way round (until the building stands, or
 * gets to the level, or the project gets that far). All it names must hold. A happening's may still be just the age ("vas").
 */
export const needsSpec = z
  .strictObject({
    age: z.enum(SCENE_ART.ages).optional(),
    building: z
      .string()
      .refine(b => SCENE_ART.buildings.includes(b.toLowerCase()), { message: `not a building type (${SCENE_ART.buildings.map(b => b.toUpperCase()).join(', ')})` })
      .optional(),
    level: z.number().int().min(1).max(MAX_LEVEL).optional(),
    project: z.string().regex(/^[a-z][a-z0-9_]{0,39}$/, 'a project id, as in the culture pack\'s projects.json').optional(),
    step: z.number().int().min(1).max(20).optional(),
    before: z.boolean().optional(),
  })
  .refine(n => n.age || n.building || n.project || n.level, 'needs names an age, a building, a level or a project')
  .refine(n => n.step === undefined || n.project, 'a step goes with a project')
  .refine(n => n.level === undefined || !n.project, 'a level goes with a building (or alone: the place\'s own), not with a project')
  .refine(n => !n.before || n.building || n.project || n.level, 'before goes with a building, a level or a project')
export type Needs = z.infer<typeof needsSpec>
const sceneObject = z.object({ slot: text.max(30), word: id, pack: id.optional(), needs: needsSpec.optional() })
const person = z.object({
  id,
  name: text.max(60),
  emoji: emoji.default('🙂'),
  art: z.enum(SCENE_ART.people),
  slot: text.max(30),
  always: z.boolean().default(false),
  /** Who this is in the cast (a villager id), so the friendship grows here too. */
  villager: id.optional(),
})
const happening = z.object({
  id,
  /** "Babica kuha juho · Grandma is cooking soup", or per language. */
  title: label(100),
  who: id,
  when: z.array(z.enum(TIMES)).max(5).default([]),
  weekdays: z.array(z.number().int().min(1).max(7)).max(7).default([]),
  chance: z.number().min(0).max(1).default(1),
  dialog: id.optional(),
  /**
   * Its variants, dialog ids of the scene in order: the app plays one not heard yet, then the one heard longest ago
   * (companion/SCENES.md, "Variants"); `dialog` stays what an older app plays.
   */
  dialogs: z.array(id).min(1).max(12).optional(),
  reward: z.partialRecord(z.enum(RESOURCES), z.number().int().min(0).max(300)).default({}),
  marker: emoji.default('💬'),
  /** The earliest age ("vas"), or what the village must have built (or not yet): see needsSpec. */
  needs: z.union([z.enum(SCENE_ART.ages), needsSpec]).optional(),
  /** What the person remembers once the dialog is done, in their words, fitting "Še vedno mislim na {memory}". */
  memory: reply.optional(),
  /** Only for a guest from a linked town (a visitor meets it; the village's own learner never does): see forHost. */
  guests: z.boolean().optional(),
  /**
   * The person tells tonight's story (lani.story/v0, stories.ts) instead of a dialog: the next one of theirs the learner
   * hasn't heard, at the learner's level. Their person needs a villager: the stories' teller.
   */
  stories: z.boolean().optional(),
})

export const sceneSpec = z.object({
  schema: schemaField('lani.scene/v0'),
  id: z.string().regex(/^[a-z0-9][a-z0-9-]{1,62}$/, 'id must be kebab-case, 2-63 chars'),
  /** The language its lines are in: the village's (Slovene unless it says otherwise; a culture pack's scene is in the pack's). */
  language: z.enum(SCENE_LANGUAGES).default('sl'),
  /** "Ob ognju · At the campfire" (target · English), or per language: {"it": "Il mare", "sl": "Morje", "en": "The sea"}. */
  title: label(80),
  emoji: emoji.default('📍'),
  level: z.enum(['A1', 'A2', 'B1', 'B2', 'C1', 'C2']).default('A1'),
  /** Who wrote which of its languages and who checked them: "de, it: machine-written, not reviewed by a native speaker". */
  review: text.max(200).optional(),
  art: z.enum(ARTS),
  from: z.array(z.union([z.enum(PLACES), z.string().regex(PROJECT_PLACE, 'a place of the village, or project:<id> of the culture\'s projects')])).min(1).max(6),
  needs: z.enum(SCENE_ART.ages).optional(),
  /**
   * A room of a house (it opens from "house"): who lives in the house whose room it is, villager ids of the cast. Each house
   * opens one room, its own: the app gives the houses the rooms of the households that live in the village, in the order
   * they moved in (companion/SCENES.md, "Houses and their rooms").
   */
  household: z.array(id).max(8).optional(),
  pack: id.optional(),
  objects: z.array(sceneObject).max(30).default([]),
  people: z.array(person).max(8).default([]),
  happenings: z.array(happening).max(12).default([]),
  dialogs: z.array(dialog).max(12).default([]),
  /**
   * More dialogs: the happenings' other variants. Kept apart from `dialogs` so that an older bridge (at most 12 dialogs,
   * and it leaves this out) reads a scene with variants as it was; served to the app among the dialogs (resolveScene).
   */
  variants: z.array(dialog).max(36).default([]),
})

export type Scene = z.infer<typeof sceneSpec>
export type SceneSource = 'curated' | 'tutor'
/** [culture]: the culture pack a scene of its own comes from (companion/cultures/<id>/scenes). */
export type LoadedScene = Scene & { source: SceneSource; published_at?: number; culture?: string }

/**
 * What the validator looks references up in: the packs (object words), the scenarios (`talk`) and, when given, the cast
 * (`people[].villager`) and the village projects of the culture (`needs.project`, with their number of steps).
 */
export type SceneRefs = {
  packs: (Pick<Pack, 'id' | 'words'> & { language?: Pack['language'] })[]
  scenarios: { id: string }[]
  villagers?: { id: string }[]
  projects?: SceneProject[]
}

/** A village project of the culture, as `needs` may name it: its id and how many steps it has. */
export type SceneProject = { id: string; steps: number }

/**
 * The places [s]'s `needs` name: a level alone is the level of the building the scene is opened from, so the scene opens
 * from a building (not the fire, the forest, a spot or a landmark); and, against the culture's [projects] (not checked
 * without them), the projects and their steps.
 */
export function checkNeeds(s: Scene, projects?: SceneProject[]): string[] {
  const errs: string[] = []
  const fromBuilding = s.from.some(f => SCENE_ART.buildings.includes(f))
  const steps = projects && new Map(projects.map(p => [p.id, p.steps]))
  const one = (n: Needs | string | undefined, at: string) => {
    if (!n || typeof n === 'string') return
    if (n.level !== undefined && !n.building && !fromBuilding)
      errs.push(`${at}.needs: a level alone is the level of the building the scene opens from, and it opens from none (from: ${s.from.join(', ')}); name the building`)
    if (!steps || !n.project) return
    const of = steps.get(n.project)
    if (of === undefined) errs.push(`${at}.needs: no project "${n.project}" in the village's culture (projects: ${[...steps.keys()].join(', ')})`)
    else if (n.step !== undefined && n.step > of) errs.push(`${at}.needs: the project "${n.project}" has ${of} steps, not ${n.step}`)
  }
  s.objects.forEach((o, i) => one(o.needs, `objects[${i}] (${o.slot})`))
  s.happenings.forEach((h, i) => one(h.needs, `happenings[${i}] (${h.id})`))
  // a project's landmark as the scene's place
  if (steps) s.from.forEach((f, i) => {
    const id = f.startsWith('project:') ? f.slice('project:'.length) : undefined
    if (id !== undefined && !steps.has(id)) errs.push(`from[${i}]: no project "${id}" in the village's culture (projects: ${[...steps.keys()].join(', ')})`)
  })
  return errs
}

/** The references a scene makes: the art's slots and spots, its own people and dialogs, packs, scenarios. */
export function crossCheck(s: Scene, refs?: SceneRefs): string[] {
  const errs: string[] = []
  const slots = SCENE_ART.objects[s.art] ?? []
  const spots = SCENE_ART.personSlots[s.art] ?? []
  const dupes = (items: string[], what: string) => {
    const seen = new Set<string>()
    for (const x of items) {
      if (seen.has(x)) errs.push(`duplicate ${what} "${x}"`)
      seen.add(x)
    }
  }
  dupes(s.objects.map(o => o.slot), 'object slot')
  dupes(s.people.map(p => p.id), 'person id')
  dupes(s.happenings.map(h => h.id), 'happening id')
  const all = [...s.dialogs, ...s.variants]
  dupes(all.map(d => d.id), 'dialog id')

  const packs = refs && new Map(refs.packs.map(p => [p.id, new Set(p.words.map(w => w.id))]))
  s.objects.forEach((o, i) => {
    const at = `objects[${i}] (${o.slot})`
    if (!slots.includes(o.slot)) errs.push(`${at}: the art "${s.art}" has no slot "${o.slot}" (slots: ${slots.join(', ')})`)
    const pack = o.pack ?? s.pack
    if (!pack) errs.push(`${at}: no pack for word "${o.word}" (set the scene's pack or the object's)`)
    else if (packs) {
      const words = packs.get(pack)
      if (!words) errs.push(`${at}: unknown pack "${pack}"`)
      else if (!words.has(o.word)) errs.push(`${at}: pack "${pack}" has no word "${o.word}"`)
    }
  })

  const people = new Set(s.people.map(p => p.id))
  const cast = refs?.villagers && new Set(refs.villagers.map(v => v.id))
  s.people.forEach((p, i) => {
    if (!spots.includes(p.slot)) errs.push(`people[${i}] (${p.id}): the art "${s.art}" has no person spot "${p.slot}" (spots: ${spots.join(', ')})`)
    if (p.villager && cast && !cast.has(p.villager)) errs.push(`people[${i}] (${p.id}): villager "${p.villager}" is not in the cast (list_villagers shows the ids)`)
  })
  // who lives in the house whose room it is: only a room of a house has a household, of the cast
  if (s.household?.length) {
    if (!s.from.includes('house')) errs.push(`household: only a room of a house has one (from: ${s.from.join(', ')}; a room opens from "house")`)
    dupes(s.household, 'household villager')
    s.household.forEach((v, i) => { if (cast && !cast.has(v)) errs.push(`household[${i}]: villager "${v}" is not in the cast (list_villagers shows the ids)`) })
  }

  // every text in the scene's language (the village's), and in English; a title per language too
  const lang = s.language
  const inLang = (t: Partial<Record<string, unknown>> | undefined, at: string) => {
    if (!t) return
    if (typeof t[lang] !== 'string') errs.push(`${at}: no "${lang}" text (the scene's language)`)
    if (typeof t.en !== 'string') errs.push(`${at}: no "en" text (the English translation)`)
  }
  // a title is "target · English", or per language with the scene's
  const titled = (l: Label, at: string) => {
    if (typeof l !== 'string' && !labelTarget(l, lang)) errs.push(`${at}: no "${lang}" text (the scene's language)`)
  }
  titled(s.title, 'title')

  const dialogs = new Map(all.map(d => [d.id, d]))
  s.happenings.forEach((h, i) => {
    const at = `happenings[${i}] (${h.id})`
    if (!people.has(h.who)) errs.push(`${at}: who "${h.who}" is not a person of this scene`)
    if (h.dialog && !dialogs.has(h.dialog)) errs.push(`${at}: dialog "${h.dialog}" does not exist`)
    for (const v of h.dialogs ?? []) if (!dialogs.has(v)) errs.push(`${at}.dialogs: dialog "${v}" does not exist`)
    dupes(h.dialogs ?? [], `variant of ${at}`)
    // an older app plays `dialog` as it is: {n} would show
    if (h.dialog && dialogs.get(h.dialog)?.count) errs.push(`${at}: dialog "${h.dialog}" counts, and an older app plays a happening's dialog as it is ({n}): a counting dialog is one of its dialogs (variants), not its dialog`)
    if (h.stories && (h.dialog || h.dialogs)) errs.push(`${at}: a happening tells tonight's story (stories) or plays a dialog, not both`)
    if (h.stories && people.has(h.who) && !s.people.find(p => p.id === h.who)?.villager) errs.push(`${at}: the storyteller "${h.who}" needs a villager (the stories' teller)`)
    dupes(h.weekdays.map(String), `weekday in ${at}`)
    titled(h.title, `${at}.title`)
    inLang(h.memory, `${at}.memory`)
  })

  const scenarios = refs && new Set(refs.scenarios.map(x => x.id))
  const effects = SCENE_ART.effects[s.art] ?? []
  const effectsOf = (fx: Record<string, unknown> | undefined, at: string) => {
    for (const name of Object.keys(fx ?? {}))
      if (!effects.includes(name)) errs.push(`${at}: the art "${s.art}" has no effect "${name}" (effects: ${effects.join(', ') || 'none'})`)
  }
  all.forEach((d, di) => {
    const at = di < s.dialogs.length ? `dialogs[${di}] (${d.id})` : `variants[${di - s.dialogs.length}] (${d.id})`
    if (d.talk && scenarios && !scenarios.has(d.talk)) errs.push(`${at}: talk "${d.talk}" is not a scenario`)
    d.lines.forEach((l, li) => {
      const here = `${at}.lines[${li}]`
      effectsOf(l.fx, here)
      l.choices.forEach((c, ci) => effectsOf(c.reply?.fx, `${here}.choices[${ci}].reply`))
      if (l.choices.length) {
        if (l.who || SCENE_LANGUAGES.some(k => l[k])) errs.push(`${here}: a choice turn has no who/${lang}/en`)
        if (l.choices.length < 2) errs.push(`${here}: a choice turn needs 2-3 choices`)
        if (!l.choices.some(c => c.ok)) errs.push(`${here}: no choice is ok`)
        const same = sameFor(l.choices.map(c => c[lang] as string | undefined))
        if (same) errs.push(`${here}: two choices read the same to a ${same} learner ({m:…|f:…}): a turn never tests the learner's own gender`)
        l.choices.forEach((c, ci) => {
          if (!c.ok && !c.why) errs.push(`${here}.choices[${ci}]: a wrong choice needs a why`)
          errs.push(...reactionErrors(c, `${here}.choices[${ci}]`))
          inLang(c, `${here}.choices[${ci}]`)
          inLang(c.reply, `${here}.choices[${ci}].reply`)
        })
        inLang(l.puzzled, `${here}.puzzled`)
      } else {
        if (!l.who || !l[lang] || !l.en) errs.push(`${here}: a spoken line needs who, ${lang} and en`)
        else if (!people.has(l.who)) errs.push(`${here}: who "${l.who}" is not a person of this scene`)
        if (l.grammar) errs.push(`${here}: a grammar page goes on the learner's turn or a choice, not on a spoken line`)
        if (l.puzzled) errs.push(`${here}: puzzled is a learner's turn's (the reaction to a wrong answer), not a spoken line's`)
      }
    })
    if (d.lines[0]?.choices.length) errs.push(`${at}: the first line must be someone's line, not the learner's choices`)
    errs.push(...stageErrors(d, s, at))
    errs.push(...staysErrors(d, at))
    inLang(d.memory, `${at}.memory`)
    errs.push(...countErrors(d, lang, at, SCENE_LANGUAGES))
  })
  errs.push(...checkNeeds(s, refs?.projects))
  return errs
}

/**
 * The grammar book's pages the learner's turns in [dialogs] name (a turn's `grammar`, a choice's), with where: the
 * smoke and the tutor check they are pages of the scene's language (a page the book lacks leaves the why without a link).
 */
export function dialogGrammar(dialogs: { id: string; lines: { grammar?: string; choices: { grammar?: string }[] }[] }[]): [string, string][] {
  const out: [string, string][] = []
  dialogs.forEach((d, di) => d.lines.forEach((l, li) => {
    const at = `dialogs[${di}] (${d.id}).lines[${li}]`
    if (l.grammar) out.push([`${at}.grammar`, l.grammar])
    l.choices.forEach((c, ci) => { if (c.grammar) out.push([`${at}.choices[${ci}].grammar`, c.grammar]) })
  }))
  return out
}

type Choice = z.infer<typeof choice>
type Lines = { lines: { sky?: unknown; fx?: unknown; act?: unknown; choices: Choice[] }[]; sky_stays?: boolean; fx_stays?: boolean; act_stays?: boolean }
type Act = z.infer<typeof actCue>

/**
 * Dialog [d]'s stage directions and tap turns in scene [s] (companion/SCENES.md, "Stage directions", "Tap turns"). The
 * directions are followed line by line from everyone's spot (a right choice's reply moves them, a reaction's passes): each
 * names a person of the scene, a spot of the art's stage and a pose that spot has. A tap turn's choices each stand for their
 * own spot of the stage, slot of the art or person of the scene; a turn of 4 choices is a tap turn.
 */
export function stageErrors(d: Dialog, s: Pick<Scene, 'art' | 'people'>, at: string): string[] {
  const errs: string[] = []
  const stage = SCENE_STAGE[s.art]
  const slots = SCENE_ART.objects[s.art] ?? []
  const people = new Set(s.people.map(p => p.id))
  const where = new Map(s.people.map(p => [p.id, { spot: p.slot, pose: 'stand' }]))
  const follow = (act: Act | undefined, here: string, move: boolean) => {
    if (!act) return
    if (!stage) return void errs.push(`${here}.act: the art "${s.art}" has no stage for stage directions yet (arts with one: ${Object.keys(SCENE_STAGE).join(', ')})`)
    for (const [who, cue] of Object.entries(act)) {
      const was = where.get(who)
      if (!was || !people.has(who)) { errs.push(`${here}.act: "${who}" is not a person of this scene`); continue }
      const spot = cue.to ?? was.spot
      const pose = cue.pose ?? (cue.to !== undefined ? 'stand' : was.pose)
      const it = stage[spot]
      if (!it) { errs.push(`${here}.act.${who}: the art "${s.art}" has no spot "${spot}" on its stage (spots: ${Object.keys(stage).join(', ')})`); continue }
      if (!it.poses.includes(pose)) { errs.push(`${here}.act.${who}: the spot "${spot}" has no pose "${pose}" (its poses: ${it.poses.join(', ')})`); continue }
      if (move) where.set(who, { spot, pose })
    }
  }
  d.lines.forEach((l, li) => {
    const here = `${at}.lines[${li}]`
    follow(l.act, here, true)
    if (!l.choices.length) return
    // a reaction's directions pass, and so are checked from here; the right choices' replies move them (the first one's
    // is followed: give them all the same cues)
    const right = l.choices.findIndex(c => c.ok)
    l.choices.forEach((c, ci) => { if (ci !== right) follow(c.reply?.act, `${here}.choices[${ci}].reply`, false) })
    if (right >= 0) follow(l.choices[right].reply?.act, `${here}.choices[${right}].reply`, true)
    const taps = l.choices.map(c => c.tap)
    if (taps.every(t => t === undefined)) {
      if (l.choices.length > 3) errs.push(`${here}: a choice turn needs 2-3 choices (a tap turn, 2-4)`)
      return
    }
    if (taps.some(t => t === undefined)) errs.push(`${here}: in a tap turn every choice stands for a place in the picture (tap), or none does`)
    const seen = new Set<string>()
    l.choices.forEach((c, ci) => {
      if (c.tap === undefined) return
      if (seen.has(c.tap)) errs.push(`${here}.choices[${ci}].tap: "${c.tap}" twice in the turn (each choice stands for its own place)`)
      seen.add(c.tap)
      if (!stage?.[c.tap] && !slots.includes(c.tap) && !people.has(c.tap))
        errs.push(`${here}.choices[${ci}].tap: "${c.tap}" is no spot of the stage, no slot of the art "${s.art}" and no person of this scene`)
    })
  })
  return errs
}

/**
 * Stage directions and tap turns are a scene's, whose picture shows them: a dialog played without one (a story by the
 * fire, an introduction, a keeper's talk over the village) has no `act` and no `tap`, and 2-3 choices a turn.
 */
export function offStage(d: { lines: { act?: unknown; choices: { tap?: string; reply?: { act?: unknown } }[] }[] }, at: string, what: string): string[] {
  const errs: string[] = []
  d.lines.forEach((l, li) => {
    const here = `${at}.lines[${li}]`
    if (l.act || l.choices.some(c => c.reply?.act)) errs.push(`${here}: ${what} has no scene around it: no act cue`)
    if (l.choices.some(c => c.tap !== undefined)) errs.push(`${here}: ${what} has no picture to tap in: no tap`)
    if (l.choices.length > 3) errs.push(`${here}: a turn needs 2-3 choices`)
  })
  return errs
}

/**
 * A wrong choice's reply is the other person's reaction to it ("Žejen? Tu imaš vodo."): it passes when the learner picks
 * again, so it may show an effect for a moment but never changes the weather.
 */
export function reactionErrors(c: Choice, at: string): string[] {
  return !c.ok && c.reply?.sky ? [`${at}.reply: a reaction to a wrong choice passes: no sky cue (an fx may show it for a moment)`] : []
}

/** sky_stays, fx_stays and act_stays need a cue that stays: on a line, or on a right choice's reply (a reaction's passes). */
export function staysErrors(d: Lines, at: string): string[] {
  const errs: string[] = []
  const cues = d.lines.some(l => l.sky || l.choices.some(c => c.ok && c.reply?.sky))
  if (d.sky_stays !== undefined && !cues) errs.push(`${at}: sky_stays without a sky cue on any line or reply`)
  const fx = d.lines.some(l => l.fx || l.choices.some(c => c.ok && c.reply?.fx))
  if (d.fx_stays !== undefined && !fx) errs.push(`${at}: fx_stays without an fx cue on any line or reply to a right choice`)
  const act = d.lines.some(l => l.act || l.choices.some(c => c.ok && c.reply?.act))
  if (d.act_stays !== undefined && !act) errs.push(`${at}: act_stays without an act cue on any line or reply to a right choice`)
  return errs
}

/** Whether happening [h] of [s] is only for guests: it says so, or its dialog does. */
export const forGuests = (s: Pick<Scene, 'dialogs'>, h: Scene['happenings'][number]) =>
  h.guests === true || s.dialogs.some(d => variantsOf(h).includes(d.id) && d.guests === true)

/** The dialogs happening [h] plays: its variants (`dialogs`), else its one `dialog` (Happening.dialogs in SceneSpec.kt). */
export const variantsOf = (h: Pick<Scene['happenings'][number], 'dialog' | 'dialogs'>): string[] => h.dialogs ?? (h.dialog ? [h.dialog] : [])

/**
 * A scene as its own village's app gets it: without the happenings meant for guests (a visitor from a linked town meets
 * them: its bridge gets the scene whole, visits.ts), nor the dialogs only they play.
 */
export function forHost<T extends Pick<Scene, 'happenings' | 'dialogs'>>(s: T): T {
  if (!s.happenings.some(h => forGuests(s, h)) && !s.dialogs.some(d => d.guests)) return s
  const happenings = s.happenings.filter(h => !forGuests(s, h))
  const used = new Set(happenings.flatMap(h => [...variantsOf(h), h.dialog]))
  const guests = new Set(s.happenings.filter(h => forGuests(s, h)).flatMap(h => [...variantsOf(h), h.dialog]))
  return { ...s, happenings, dialogs: s.dialogs.filter(d => used.has(d.id) || (!d.guests && !guests.has(d.id))) }
}

export function validateScene(input: unknown, refs?: SceneRefs): { ok: true; scene: Scene } | { ok: false; errors: string } {
  const r = sceneSpec.safeParse(input)
  if (!r.success) return { ok: false, errors: z.prettifyError(r.error) }
  const errs = [...learnerErrorsIn(r.data), ...crossCheck(r.data, refs)]
  return errs.length ? { ok: false, errors: errs.map(e => `✖ ${e}`).join('\n') } : { ok: true, scene: r.data }
}

/**
 * An object as the app gets it: the word's fields copied from its pack, in every language the word has ("sl" and "en" in
 * a Slovene pack; "it", "sl" and "en" in an Italian one) and its examples likewise.
 */
export type ResolvedObject = Scene['objects'][number] & {
  sl?: string
  en: string
  it?: string
  de?: string
  emoji?: string
  gender?: string
  plural?: string
  example_sl?: string
  example_en?: string
  example_it?: string
  example_de?: string
}
export type ResolvedScene = Omit<LoadedScene, 'objects' | 'variants'> & { objects: ResolvedObject[] }

/**
 * A scene's happenings and dialogs as the app gets them: the variants among the dialogs, and every happening with a
 * `dialog` for an older app (which plays one per happening), the first of its variants without a count when the file
 * gives only `dialogs`.
 */
export function servedDialogs(s: Pick<Scene, 'happenings' | 'dialogs' | 'variants'>): Pick<Scene, 'happenings' | 'dialogs'> {
  const dialogs = [...s.dialogs, ...s.variants]
  const counts = new Set(dialogs.filter(d => d.count).map(d => d.id))
  const happenings = s.happenings.map(h => {
    if (h.dialog || !h.dialogs) return h
    const plain = h.dialogs.find(id => !counts.has(id))
    return plain ? { ...h, dialog: plain } : h
  })
  return { happenings, dialogs }
}

/** The scene with every object's word resolved from the packs (an unknown word keeps its id, in the scene's language). */
export function resolveScene(s: LoadedScene, packs: SceneRefs['packs']): ResolvedScene {
  const byId = new Map(packs.map(p => [p.id, p]))
  const texts = (w: Partial<Record<string, string>>, prefix: string) =>
    Object.fromEntries(SCENE_LANGUAGES.filter(k => k !== 'en' && w[prefix + k]).map(k => [prefix + k, w[prefix + k]]))
  const { variants: _variants, ...scene } = s
  return {
    ...scene,
    ...servedDialogs(s),
    objects: s.objects.map(o => {
      const w = byId.get(o.pack ?? s.pack ?? '')?.words.find(w => w.id === o.word)
      return {
        ...o,
        pack: o.pack ?? s.pack,
        ...(w ? texts(w, '') : { [s.language]: o.word }),
        // an English scene's word is its "en" (its id, when no pack has it)
        en: w?.en ?? (s.language === 'en' ? o.word : ''),
        emoji: w?.emoji,
        gender: w?.gender,
        plural: w?.plural,
        ...(w ? texts(w, 'example_') : {}),
        example_en: w?.example_en,
      }
    }),
  }
}

/**
 * Where the curated scenes of a village are, in order: its language's ([scenesRoot] itself for Slovene, as Jan's always
 * were; scenesRoot/<language> for another), then its culture pack's own (companion/cultures/<id>/scenes: the places only
 * that culture has, its landscape at the horizon). The first with an id wins. Mirrors packsDirs.
 */
export function scenesDirs(scenesRoot: string, culturesDir: string, cultureId: string, language: string): { dir: string; culture?: string }[] {
  return [{ dir: language === 'sl' ? scenesRoot : join(scenesRoot, language) }, { dir: join(culturesDir, cultureId, 'scenes'), culture: cultureId }]
}

/**
 * A dialog the tutor wrote for a happening (publish_dialog_variant, companion/SCENES.md "Variants"), kept in
 * <data>/app/variants/<scene>.<happening>.<dialog>.json.
 */
export type TutorVariant = { scene: string; happening: string; dialog: Dialog; published_at?: number }

const tutorVariantSpec = z.object({ scene: id, happening: id, dialog })

/**
 * [s] with the tutor's variants [vs] of its happenings: each among the scene's variants (source "tutor") and after the
 * others in its happening's dialogs. One whose happening is gone (or tells stories), or whose id the scene has, is left out.
 */
export function withTutorVariants<T extends Scene>(s: T, vs: TutorVariant[]): T {
  const mine = vs.filter(v => v.scene === s.id)
  if (!mine.length) return s
  const ids = new Set([...s.dialogs, ...s.variants].map(d => d.id))
  const added: Dialog[] = []
  const happenings = s.happenings.map(h => {
    const its = mine.filter(v => v.happening === h.id && !h.stories && !ids.has(v.dialog.id) && ids.add(v.dialog.id))
    if (!its.length) return h
    added.push(...its.map(v => ({ ...v.dialog, source: 'tutor' as const })))
    return { ...h, dialogs: [...variantsOf(h), ...its.map(v => v.dialog.id)] }
  })
  return added.length ? { ...s, happenings, variants: [...s.variants, ...added] } : s
}

/** The file name of a tutor's variant: <scene>.<happening>.<dialog>.json (ids are kebab-case: the dots part them). */
const variantFile = (scene: string, happening: string, dialogId: string) => `${scene}.${happening}.${dialogId}.json`

export class SceneStore {
  private warned = new Set<string>()
  private curatedDirs: { dir: string; culture?: string }[]

  /**
   * [curated]: the directories of the curated scenes, in order (the village's language's, then its culture pack's own:
   * scenesDirs); the first with an id wins. [variantsDir]: the tutor's variants of the happenings (publishVariant).
   */
  constructor(
    curated: string | { dir: string; culture?: string }[],
    private tutorDir: string,
    private refs: () => SceneRefs,
    private log: (...a: unknown[]) => void = () => {},
    private variantsDir?: string,
  ) {
    this.curatedDirs = typeof curated === 'string' ? [{ dir: curated }] : curated
    mkdirSync(tutorDir, { recursive: true })
    if (variantsDir) mkdirSync(variantsDir, { recursive: true })
  }

  /** The tutor's variants, oldest first (a file that doesn't read is skipped, once in the log). */
  tutorVariants(): TutorVariant[] {
    const dir = this.variantsDir
    if (!dir || !existsSync(dir)) return []
    return readdirSync(dir)
      .filter(f => f.endsWith('.json'))
      .flatMap(f => {
        const path = join(dir, f)
        const at = statSync(path).mtimeMs
        const r = (() => {
          try {
            return tutorVariantSpec.safeParse(JSON.parse(readFileSync(path, 'utf8')))
          } catch (e) {
            return { success: false as const, error: e as Error }
          }
        })()
        if (r.success) return [{ ...r.data, published_at: at }]
        const key = `${path}:${at}`
        if (!this.warned.has(key)) this.log(`skipping dialog variant ${path}: ${r.error.message}`)
        this.warned.add(key)
        return []
      })
      .sort((a, b) => a.published_at - b.published_at)
  }

  /** Writes the tutor's variant [dialog] of happening [happening] of scene [scene] (the same ids: replaced). */
  publishVariant(scene: string, happening: string, dialog: Dialog): void {
    if (!this.variantsDir) throw new Error('no variants directory')
    const path = join(this.variantsDir, variantFile(scene, happening, dialog.id))
    const { source: _source, ...plain } = dialog
    writeFileSync(`${path}.tmp`, JSON.stringify({ scene, happening, dialog: plain }, null, 2) + '\n')
    renameSync(`${path}.tmp`, path)
  }

  /** Removes the tutor's variant [dialogId] of [scene]/[happening]. False when there is none. */
  removeVariant(scene: string, happening: string, dialogId: string): boolean {
    if (!this.variantsDir || ![scene, happening, dialogId].every(x => /^[a-z0-9][a-z0-9-]{0,62}$/.test(x))) return false
    const path = join(this.variantsDir, variantFile(scene, happening, dialogId))
    if (!existsSync(path)) return false
    unlinkSync(path)
    return true
  }

  private read(dir: string, source: SceneSource, culture?: string): LoadedScene[] {
    if (!existsSync(dir)) return []
    const refs = this.refs()
    return readdirSync(dir)
      .filter(f => f.endsWith('.json'))
      .sort()
      .flatMap(f => {
        const path = join(dir, f)
        try {
          const v = validateScene(JSON.parse(readFileSync(path, 'utf8')), refs)
          if (!v.ok) throw new Error(v.errors)
          const published_at = source === 'tutor' ? statSync(path).mtimeMs : undefined
          return [{ ...v.scene, source, published_at, ...(culture ? { culture } : {}) }]
        } catch (e) {
          const key = `${path}:${statSync(path).mtimeMs}`
          if (!this.warned.has(key)) this.log(`skipping scene ${path}: ${(e as Error).message}`)
          this.warned.add(key)
          return []
        }
      })
  }

  /** The curated scenes, one per id (the first directory with it wins). */
  private curated(): LoadedScene[] {
    const seen = new Set<string>()
    return this.curatedDirs.flatMap(({ dir, culture }) => this.read(dir, 'curated', culture).filter(s => !seen.has(s.id) && seen.add(s.id)))
  }

  /**
   * Every scene, one per id: a tutor scene replaces the curated one with the same id; the tutor's variants of its
   * happenings join them (withTutorVariants).
   */
  all(): LoadedScene[] {
    const tutor = this.read(this.tutorDir, 'tutor')
    const ids = new Set(tutor.map(s => s.id))
    const variants = this.tutorVariants()
    return [...this.curated().filter(s => !ids.has(s.id)), ...tutor].map(s => withTutorVariants(s, variants))
  }

  get(id: string): LoadedScene | undefined {
    return this.all().find(s => s.id === id)
  }

  /** Every scene as the app gets it, objects resolved. */
  resolved(): ResolvedScene[] {
    const packs = this.refs().packs
    return this.all().map(s => resolveScene(s, packs))
  }

  resolve(id: string): ResolvedScene | undefined {
    const s = this.get(id)
    return s && resolveScene(s, this.refs().packs)
  }

  /** The curated scenes alone, resolved with [packs] (a visiting town sees these, with the curated packs: visits.ts). */
  curatedResolved(packs: SceneRefs['packs']): ResolvedScene[] {
    return this.curated().map(s => resolveScene(s, packs))
  }

  isCurated(id: string): boolean {
    return this.curated().some(s => s.id === id)
  }

  /** Writes a tutor scene (same id = replace; a curated id is replaced too, until remove()). */
  publish(s: Scene): void {
    const path = join(this.tutorDir, `${s.id}.json`)
    writeFileSync(`${path}.tmp`, JSON.stringify(s, null, 2) + '\n')
    renameSync(`${path}.tmp`, path)
  }

  /** Removes a tutor scene (a curated one with that id shows again). False when there is none. */
  remove(id: string): boolean {
    if (!/^[a-z0-9][a-z0-9-]{1,62}$/.test(id)) return false
    const path = join(this.tutorDir, `${id}.json`)
    if (!existsSync(path)) return false
    unlinkSync(path)
    return true
  }
}
