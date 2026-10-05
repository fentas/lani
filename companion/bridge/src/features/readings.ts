// Readings (readings.ts): the culture pack's and the tutor's, for the app's reading corner ("Branje · Reading"), the
// tutor's tools to write them, and the reading practice the app reports (POST /readings/done), persisted like a
// flashcard review: update-db.py, no LLM; the tutor hears a summary.
import { z } from 'zod'
import { badNote, fail, noteIn, ok } from '../channel'
import type { FeatureFactory } from '../feature'
import { json, readJson } from '../http'
import { LANGUAGES, learnerLangs } from '../learners'
import { checkReading, readingDone, readingLength, readingPayload, readingSpec, type ServedReading } from '../readings'
import { learnerLevel } from '../villagers'

const instructions = `
Readings: the app's reading corner ("📖 Branje · Reading", also from the village book and the chest) lists short
texts by level: the culture pack's (what the chest's things hold, and more) and yours. Each is read first, its lines
with their translation, every word to tap, 🔊 per line; then the text hides and its questions come one at a time ("👁
Pokaži besedilo" shows it again; a note says how often: looks), then its new words to add to the reviews; and "🎤 Beri
na glas", reading it aloud, graded word by word by the node's Whisper. list_readings shows them with their level and
length, and which the learner has read. Write one with publish_reading (validated like the culture pack's readings)
when the learner has read those at their level (list_readings: unread_at_level 0), when they ask for something to
read, or to bring back their topics and weak spots (mistakes-db): a short text at their level (A1 40-80 words, A2
80-150, B1 150-250) in the village's language with the translation of every line in their base (and the other
languages its title has), read aloud by a villager of the cast (by), 3-5 questions (types main_idea, detail, word in
context, true_false, inference: the lani-reading skill's method; asked in the village's language, the answer found
in the text, explain quoting it) and up to 8 new words (words: the word as a dictionary has it, its form in the text,
what it means). reading_done: the app finished a reading's questions, a story by the fire, or a reading aloud, ALREADY
persisted by the bridge as reading practice (aloud: reading and speaking): don't persist it again. Reply only when it
helps (the words misread aloud, a question type that keeps going wrong, a next reading).
`.trim()

/** The readings the village state marks read (GameState.read: "<culture>/<id>"). */
type Read = string[]

export const readings: FeatureFactory = ({ readings: store, villagers, learner, channel, events, outbox, game, cfg, culture }) => {
  const language = culture.manifest?.language ?? 'sl'
  const langs = () => learnerLangs(cfg.dataDir, language)
  const read = (): Set<string> => new Set(((game.read()?.state as { read?: Read } | undefined)?.read ?? []).filter(k => typeof k === 'string'))
  const titleOf = (r: ServedReading) => {
    const { target, base } = langs()
    const t = r.title as Record<string, string>
    return t[target] && t[base] && t[base] !== t[target] ? `${t[target]} · ${t[base]}` : (t[target] ?? t[base] ?? r.id)
  }
  const info = (r: ServedReading, done: Set<string>) => ({
    id: r.id,
    title: titleOf(r),
    kind: r.kind,
    level: r.level,
    source: r.source,
    words: readingLength(r, language),
    questions: r.questions.map(q => q.type ?? 'question'),
    new_words: r.words.length,
    topics: r.topics,
    by: r.by,
    read: done.has(`${culture.id}/${r.id}`),
  })

  async function handleDone(req: Request): Promise<Response> {
    const r = readingDone.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    const d = r.data
    const other = learner.other(d.language)
    const state = learner.state(other)
    const payload = readingPayload(d, { session_id: state.computed.next_session_id, date: state.computed.today, language: other })
    const u = learner.update(payload)
    if (!u.ok) return json({ error: u.output }, 500)
    const deck = other ? ` in ${LANGUAGES[other] ?? other} (language "${other}")` : ''
    await channel.notify(`${payload.session_notes.replace(/\.$/, '')}${deck}; ${d.duration_minutes} min. Already persisted as ${payload.session_id}.`, {
      kind: 'reading_done',
      conversation_id: 'main',
      msg_id: payload.session_id,
    })
    return json({ session_id: payload.session_id, skills: payload.skills_practiced, output: u.output, ...(other ? { language: other } : {}) })
  }

  return {
    instructions,
    routes: [
      { method: 'GET', path: '/readings', addressed: true, handle: () => json(store.all()) },
      {
        method: 'GET',
        path: /^\/readings\/([a-z0-9][a-z0-9_-]{0,62})$/,
        addressed: true,
        handle: ({ params: [id] }) => {
          const r = store.get(id)
          return r ? json(r) : json({ error: 'not found' }, 404)
        },
      },
      { method: 'POST', path: '/readings/done', handle: ({ req, url }) => outbox.once(req, url.pathname, handleDone) },
    ],
    tools: [
      {
        name: 'list_readings',
        description:
          "List the readings of the app's reading corner (the culture pack's and yours) in the village's language: each one's level, kind, length in words, question types, new words, topics, and whether the learner has read it (answered its questions); with the learner's level and how many at that level they haven't read.",
        inputSchema: { type: 'object', properties: {} },
        handle: () => {
          const done = read()
          const level = learnerLevel(cfg.dataDir)
          const list = store.all().map(r => info(r, done))
          const unread = list.filter(r => r.level === level && !r.read).length
          return ok(JSON.stringify({ learner_level: level, unread_at_level: unread, readings: list }, null, 2))
        },
      },
      {
        name: 'get_reading',
        description: 'One reading as its JSON (the format of the culture packs\' readings/), to change and publish again.',
        inputSchema: { type: 'object', properties: { id: { type: 'string', description: 'The reading id' } }, required: ['id'] },
        handle: args => {
          const r = typeof args.id === 'string' ? store.get(args.id) : undefined
          if (!r) return fail(`no reading "${args.id}"; list_readings shows the ids`)
          const { source: _s, culture: _c, published_at: _p, ...spec } = r
          return ok(JSON.stringify(spec, null, 2))
        },
      },
      {
        name: 'publish_reading',
        description:
          "Validate a reading and publish it to the app's reading corner: {id, kind (page, letter, story, article, proverbs, recipe), level (A1-B2), title, by (a villager id: who reads it aloud), intro?, lines: [{text}] (a recipe: servings, ingredients, steps instead), questions: 1-5 [{type (main_idea, detail, word, true_false, inference), ask, options: 3-4 with the answer first | true_false: answer: true/false and no options, word (a word question's word as it stands in the text), explain}], words?: [{word, form?, pos?, means}], topics?}. Every text is {\"<village language>\": …, \"<base>\": …, …}: the village's language and the learner's base at least; a new word's means is in the other languages. Replaces your earlier reading with the same id; a culture pack's id is refused. Returns validation errors instead of publishing if invalid.",
        inputSchema: {
          type: 'object',
          properties: {
            reading: { type: 'object', description: 'The reading' },
            note: { type: 'string', description: 'One-line announcement shown in the app' },
          },
          required: ['reading'],
        },
        handle: args => {
          const v = readingSpec.safeParse(args.reading)
          if (!v.success) return fail(`reading invalid, nothing published:\n${z.prettifyError(v.error)}`)
          const r = v.data
          const { base } = langs()
          const problems = checkReading(r, language, 'reading', villagers.all())
          if (!(language in r.title)) problems.push(`title: no "${language}" text (the village's language)`)
          if (!(base in r.title)) problems.push(`title: no "${base}" text (the learner reads the translations in ${LANGUAGES[base] ?? base}); every text needs it`)
          if (store.isCurated(r.id)) problems.push(`id: "${r.id}" is the culture pack's reading; pick another id`)
          if (problems.length) return fail(`reading invalid, nothing published:\n${problems.map(p => `✖ ${p.replace(/^reading: /, '')}`).join('\n')}`)
          const note = noteIn.safeParse(args.note)
          if (!note.success) return badNote()
          store.publish(r)
          const served = store.get(r.id)!
          events.emit({ type: 'reading_published', id: r.id, title: titleOf(served), level: r.level, note: note.data ?? undefined })
          return ok(`published reading ${r.id} (${r.level}, ${readingLength(r, language)} words, ${r.questions.length} questions, ${r.words.length} new words): it's in the app's reading corner`)
        },
      },
      {
        name: 'remove_reading',
        description: 'Remove a reading you published from the app\'s reading corner.',
        inputSchema: { type: 'object', properties: { id: { type: 'string', description: 'The reading id' } }, required: ['id'] },
        handle: args => {
          const id = typeof args.id === 'string' ? args.id : ''
          if (!store.remove(id)) return fail(`no reading of yours "${id}" to remove`)
          events.emit({ type: 'reading_removed', id })
          return ok(`removed reading ${id}`)
        },
      },
    ],
  }
}
