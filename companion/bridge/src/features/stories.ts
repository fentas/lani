// Stories (lani.story/v0): the storyteller's evening stories by the fire, one an evening at the learner's level. The
// app gets them with the scene where they are told (features/scenes.ts, attachStories) and picks tonight's; the tutor
// lists them (with what the learner has heard), reads, publishes and removes them. Every story heard is written down in
// the learner's story notebook in the app, its pictures from the vignette library sketched. When fewer than three the learner hasn't heard are
// left at their level, the tutor is asked for another (stories_low), once a day at most.
import { join } from 'node:path'
import { badNote, fail, noteIn, ok } from '../channel'
import type { FeatureFactory } from '../feature'
import type { GameDoc } from '../game'
import { json } from '../http'
import { labelEn, labelShown } from '../langs'
import { learnerLangs } from '../learners'
import { localDate } from '../rhythm'
import {
  chaptersOf, levelsOf, MAX_FIGURES, MAX_PROPS, STORIES_LOW, StoryAsks, storiesLeft, storyScene, validateStory, VIGNETTES,
  type Heard, type LoadedStory,
} from '../stories'
import { learnerLevel } from '../villagers'

const instructions = `
Stories are told by the fire (lani.story/v0, companion/SCENES.md "Stories"): every evening the village's
storyteller (the cast's Storyteller: Stari Janez, Nonno Bepi) tells the learner one story, the next one they
haven't heard, at their level (A1 → the A1 telling, A2 → A2, higher → the highest there is); a long legend is
told in chapters, one an evening; the new stories first, then the continuations, then the old ones retold a level
up. The app picks tonight's and records what was heard; every story heard is written down in the learner's story
notebook ("Moj zvezek zgodb"): its notes (what the learner would have written down listening: the story as prose in
the past tense, no asides, at the telling's level; without them, the telling itself) with its pictures as pencil
sketches, and the turns as questions ("Preveri se", reading practice).
list_stories shows the collection, each story's levels, chapters and pictures, what the learner has heard,
all_heard and left_at_level. Write a new story with publish_story when the stories at the learner's level run
low (a stories_low note, below; all_heard), when they ask for a story, or to bring a week's words back in one: told by the
storyteller in short parts at the learner's current level with words they know (a few new ones, listed in
words), 2-3 turns for them between the parts (what happened, a reaction), a why on every wrong choice, 2-4
pictures (one or two an evening in chapters) from the vignette library (list_stories: picture_library), and its
notes for the notebook, a telling each. It joins the rotation and is told the next evening. Load lani-studio
("Stories") before writing one.
kind stories_low: fewer than ${STORIES_LOW} stories the learner hasn't heard are left at their level (data: the
stories told and how, the level, the weak patterns from mistakes-db, the region, the picture library). Write one
with publish_story, today: a legend of the region the collection doesn't have, or a continuation of one they
liked (a new chapter or a sequel, "Martin Krpan se vrne", with "continues": its id: told after it, its book joins
that one's), at their level, bringing a weak pattern or two into the learner's turns, with its pictures. No reply
to the learner needed; the app says a new story is waiting.
`.trim()

/** The weakest patterns in mistakes-db now, a few, with an example each: what a story's turns can bring back. */
type Weak = { id: string; example?: { incorrect?: string; correct?: string } }

export const stories: FeatureFactory = ({ stories, scenes, packs, villagers, events, game, cfg, culture, channel, learner, log }) => {
  const refs = () => ({ packs: packs.all(), villagers: villagers.all() })
  const language = culture.manifest?.language ?? 'sl'
  const asks = new StoryAsks(join(cfg.appDir, 'story-requests.json'))
  const titleOf = (s: LoadedStory['title']) => {
    const { target, base } = learnerLangs(cfg.dataDir, language)
    return labelShown(s, target, base)
  }
  const heard = (doc: GameDoc | undefined = game.read()): Heard => ((doc?.state as { stories?: Heard } | undefined)?.stories ?? {})
  /** The village's stories: in its language, told by someone who tells stories in one of its scenes. */
  const told = () => {
    const all = stories.all().filter(s => s.language === language)
    const resolved = scenes.resolved()
    const tellers = new Set(all.map(s => s.teller).filter(t => storyScene(resolved, t)))
    return all.filter(s => tellers.has(s.teller))
  }
  const info = (s: LoadedStory, h: Heard) => ({
    id: s.id,
    title: s.title,
    teller: s.teller,
    language: s.language,
    order: s.order,
    source: s.source,
    levels: levelsOf(s),
    chapters: chaptersOf(s).length,
    ...(s.continues ? { continues: s.continues } : {}),
    pictures: s.pictures?.length ?? 0,
    notes: s.notes?.length ?? 0,
    words: s.words.map(w => (typeof w === 'string' ? w : `${w.pack}/${w.word}`)),
    heard: { told: h[s.id]?.told ?? 0, chapter: h[s.id]?.chapter ?? 0, ...(h[s.id]?.level ? { level: h[s.id]?.level } : {}), ...(h[s.id]?.levels ? { levels: h[s.id]?.levels } : {}) },
  })
  const library = { ...VIGNETTES, max_figures: MAX_FIGURES, max_props: MAX_PROPS }

  function weakPatterns(): Weak[] {
    try {
      const patterns = (learner.state().databases as any).mistakes_db?.error_patterns ?? {}
      return Object.entries(patterns)
        .filter(([, p]: any) => (p?.consecutive_incorrect ?? 0) > 0)
        .sort(([, a]: any, [, b]: any) => (b.frequency ?? 0) - (a.frequency ?? 0))
        .slice(0, 3)
        .map(([id, p]: any) => {
          const e = Array.isArray(p?.examples) ? p.examples[p.examples.length - 1] : undefined
          return { id, ...(e ? { example: { incorrect: e.incorrect, correct: e.correct } } : {}) }
        })
    } catch (e) {
      log(`stories_low: no mistakes to build on (${(e as Error).message})`)
      return []
    }
  }

  // What the last check saw: the village's stories heard at the learner's level, so a write that changes nothing of it
  // (most do) costs nothing.
  let seen = ''
  /**
   * Asks the tutor for a story when fewer than STORIES_LOW the learner hasn't heard are left at their level: once a day
   * at most (the day is kept in <data>/app/story-requests.json), after a village write ([doc]) or at start.
   */
  async function checkLow(doc: GameDoc | undefined, now = new Date()) {
    if (!doc) return // no village yet
    const today = localDate(now)
    if (asks.last().date === today) return
    const h = heard(doc)
    const level = learnerLevel(cfg.dataDir)
    const key = `${today}|${level}|${JSON.stringify(h)}`
    if (key === seen) return
    seen = key
    const all = told()
    if (!all.length) return // nobody tells stories here
    const left = storiesLeft(all, h, level)
    if (left.length >= STORIES_LOW) return
    asks.mark(today, left.map(s => s.id), level) // before sending: a failing channel mustn't ask on every write
    const teller = villagers.all().find(v => v.id === all[0].teller)
    const data = {
      level,
      language,
      region: { culture: culture.id, name: culture.manifest ? labelEn(culture.manifest.region) : culture.id, about: culture.manifest?.about?.en },
      teller: { id: all[0].teller, name: teller?.name },
      left: left.map(s => s.id),
      told: all.map(s => ({ ...info(s, h), title: labelEn(s.title) })).map(({ words: _, language: _l, teller: _t, ...rest }) => rest),
      weak_patterns: weakPatterns(),
      picture_library: library,
      tool: 'publish_story',
    }
    const what = left.length ? `only ${left.length} left they haven't heard at ${level} (${left.map(s => s.id).join(', ')})` : `none left they haven't heard at ${level}: ${teller?.name ?? 'the storyteller'} retells the old ones a level up`
    await channel
      .notify(
        `App: Jan's evening stories are running low, ${what}. Write the next one with publish_story today (see Stories): a legend of ${data.region.name} the collection doesn't have, or a continuation of one they liked ("continues": its id), at ${level}, with 2-4 pictures from the picture library. No reply needed.\n\n\`\`\`json\n${JSON.stringify(data, null, 2)}\n\`\`\``,
        { kind: 'stories_low', conversation_id: 'main', msg_id: `s${Date.now().toString(36)}` },
      )
      .catch(e => log(`stories_low not sent: ${(e as Error).message}`))
  }

  return {
    instructions,
    routes: [{ method: 'GET', path: '/stories', addressed: true, handle: () => json(stories.resolved()) }],
    start: () => {
      game.onWrite(doc => void checkLow(doc).catch(e => log(`stories_low: ${(e as Error).message}`)))
      void checkLow(game.read()).catch(e => log(`stories_low: ${(e as Error).message}`))
    },
    tools: [
      {
        name: 'list_stories',
        description:
          "List the storyteller's evening stories (curated and tutor-written) in the village's language, in the rotation's order: their levels, chapters, pictures and words, the story each continues, what the learner has heard of each (told: how many times to the end; chapter: chapters heard of a telling under way; levels: evenings heard at each level), their level, whether all are heard, those left at their level, and the vignette library the pictures are made of.",
        inputSchema: { type: 'object', properties: {} },
        handle: () => {
          const h = heard()
          const own = stories.all().filter(s => s.language === language)
          const list = own.map(s => info(s, h))
          const level = learnerLevel(cfg.dataDir)
          return ok(JSON.stringify({
            learner_level: level,
            all_heard: list.length > 0 && list.every(s => s.heard.told > 0),
            left_at_level: storiesLeft(own, h, level).map(s => s.id),
            stories: list,
            picture_library: library,
          }, null, 2))
        },
      },
      {
        name: 'get_story',
        description: 'One story as its lani.story/v0 spec (its levels or chapters, lines, words, pictures), to change and publish again.',
        inputSchema: { type: 'object', properties: { id: { type: 'string', description: 'The story id' } }, required: ['id'] },
        handle: args => {
          const s = typeof args.id === 'string' ? stories.get(args.id) : undefined
          return s ? ok(JSON.stringify(s, null, 2)) : fail(`no story "${args.id}"; list_stories shows the ids`)
        },
      },
      {
        name: 'publish_story',
        description:
          "Validate a lani.story/v0 story and publish it: the village's storyteller tells it by the fire (it joins the rotation: the next evening's story when the learner has heard the others) and it becomes a book with its pictures. With \"continues\": the story it goes on from (told after it, in its book). Replaces a tutor story with the same id; a curated id is replaced until remove_story. Returns validation errors instead of publishing if invalid. Load the lani-studio skill before using.",
        inputSchema: {
          type: 'object',
          properties: {
            story: { type: 'object', description: 'The story (lani.story/v0)' },
            note: { type: 'string', description: 'One-line announcement shown in the app' },
          },
          required: ['story'],
        },
        handle: args => {
          const v = validateStory(args.story, refs())
          if (!v.ok) return fail(`story invalid, nothing published:\n${v.errors}`)
          if (v.story.language !== language) return fail(`story invalid, nothing published:\n✖ language: the village speaks "${language}", the story is in "${v.story.language}"`)
          const errs = continuesErrors(v.story)
          if (errs.length) return fail(`story invalid, nothing published:\n${errs.map(e => `✖ ${e}`).join('\n')}`)
          const note = noteIn.safeParse(args.note)
          if (!note.success) return badNote()
          const replaced = stories.isCurated(v.story.id)
          stories.publish(v.story)
          // where it is told: the app opens that scene from the note
          const where = storyScene(scenes.resolved(), v.story.teller)
          events.emit({ type: 'story_published', id: v.story.id, title: titleOf(v.story.title), emoji: v.story.emoji, note: note.data ?? undefined, scene: where?.id })
          const levels = levelsOf(v.story)
          const pics = v.story.pictures?.length ?? 0
          return ok(
            `published story ${v.story.id} (${levels.join(', ')}; ${chaptersOf(v.story).length} ${chaptersOf(v.story).length === 1 ? 'evening' : 'chapters'}, ${v.story.words.length} words, ${pics} ${pics === 1 ? 'picture' : 'pictures'})` +
              (v.story.continues ? `, going on from ${v.story.continues}` : '') +
              (where ? `, told in ${where.id}` : '; no scene has its teller telling stories yet (a happening with "stories": true)') +
              (replaced ? `; it replaces the curated story "${v.story.id}" until remove_story` : ''),
          )
        },
      },
      {
        name: 'remove_story',
        description: 'Remove a tutor-published story. A curated story with the same id is told again.',
        inputSchema: { type: 'object', properties: { id: { type: 'string', description: 'The story id' } }, required: ['id'] },
        handle: args => {
          const id = typeof args.id === 'string' ? args.id : ''
          if (!stories.remove(id)) return fail(`no tutor story "${id}" to remove`)
          events.emit({ type: 'story_removed', id })
          return ok(`removed tutor story ${id}${stories.get(id) ? ' (the curated one is back)' : ''}`)
        },
      },
    ],
  }

  /** The story a new one goes on from: one of the village's, told by the same teller, and not going on from the new one. */
  function continuesErrors(s: { id: string; teller: string; continues?: string }): string[] {
    if (!s.continues) return []
    const first = stories.get(s.continues)
    if (!first || first.language !== language) return [`continues: no story "${s.continues}" in the village (list_stories shows the ids)`]
    if (first.teller !== s.teller) return [`continues: "${s.continues}" is ${first.teller}'s, and this one is told by ${s.teller} (the same teller goes on)`]
    // not a circle: the one it goes on from mustn't go on from this one
    const seen = new Set([s.id])
    for (let at: LoadedStory | undefined = first; at; at = at.continues ? stories.get(at.continues) : undefined) {
      if (seen.has(at.id)) return [`continues: "${s.continues}" goes on from "${s.id}" itself (a circle)`]
      seen.add(at.id)
    }
    return []
  }
}
