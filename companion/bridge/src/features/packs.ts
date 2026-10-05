// Word packs (lani.pack/v0): new vocabulary the app introduces, then turns into SR items.
import { z } from 'zod'
import { badNote, fail, noteIn, ok } from '../channel'
import type { FeatureFactory } from '../feature'
import { json, readJson } from '../http'
import type { SrItem } from '../learner'
import { LANGUAGES, learnerLangs } from '../learners'
import { renderDeep } from '../addressee'
import { itemId, learnedWords, learnPayload, learnRun, meaningOf, titleEn, titleFor, validatePack, wordOf, type LoadedPack } from '../packs'
import { corpus } from '../voice'

const instructions = `
New words reach the app as word packs (8-24 themed words each; the app introduces 5-7 at a time,
drills them, and adds them as vocabulary SR items itself). When Jan asks for words on a topic
("teach me words for the doctor"), load lani-studio and write a lani.pack/v0 pack with
publish_pack; list_packs shows what exists and how much Jan has learned. A word's emoji must show
the word itself; never a different object — when no emoji shows it, use the pack's topic emoji.
words_learned events are ALREADY persisted: don't persist them again.
`.trim()

function packInfo(p: LoadedPack, items: Record<string, SrItem>) {
  const { words, schema: _, ...meta } = p
  return { ...meta, total: words.length, learned: learnedWords(p, items).length }
}

export const packs: FeatureFactory = ({ packs, learner, channel, events, outbox, voice, cfg, culture, addressee }) => {
  /** The village's language and the learner's base: a pack's words are learned in the one and meant in the other. */
  const langs = () => learnerLangs(cfg.dataDir, culture.manifest?.language ?? 'sl')

  /** Adds a pack's newly learned words as SR vocabulary via update-db.py; Claude hears a summary. */
  async function handleLearn(req: Request, id: string): Promise<Response> {
    const raw = packs.get(id)
    if (!raw) return json({ error: 'not found' }, 404)
    // the words as the learner learned them ({m:rad|f:rada} imam: addressee.ts), and so in their data
    const pack = renderDeep(raw, addressee())
    const r = learnRun.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    const unknown = r.data.results.filter(x => !pack.words.some(w => w.id === x.word_id)).map(x => x.word_id)
    if (unknown.length) return json({ error: `unknown word ids: ${unknown.join(', ')}` }, 400)
    // the words of another language (a visit's) go to its data, and are known or not there
    const language = learner.other(r.data.language)
    const known = new Set(learnedWords(pack, learner.srItems(language)))
    const fresh = r.data.results.filter((x, i, all) => !known.has(x.word_id) && all.findIndex(y => y.word_id === x.word_id) === i)
    const skipped = r.data.results.filter(x => !fresh.includes(x)).map(x => x.word_id)
    if (!fresh.length) return json({ added: [], skipped })

    const state = learner.state(language)
    const payload = learnPayload(pack, fresh, {
      session_id: state.computed.next_session_id,
      date: state.computed.today,
      duration_minutes: r.data.duration_minutes,
      language,
    }, langs().base)
    const u = learner.update(payload)
    if (!u.ok) return json({ error: u.output }, 500)
    const label = (x: { word_id: string }) => {
      const w = pack.words.find(w => w.id === x.word_id)!
      return `${wordOf(pack, w)} (${meaningOf(pack, w, 'en')})`
    }
    const weak = fresh.filter(x => x.quality < 3)
    await channel.notify(
      `App word pack "${titleEn(pack)}" (${pack.id})${language ? ` in ${LANGUAGES[language] ?? language} (language "${language}")` : ''}, already persisted as ${payload.session_id}: learned ${fresh.length} new words: ${fresh.map(label).join(', ')}.${weak.length ? ` Struggled with: ${weak.map(label).join(', ')}.` : ''}`,
      { kind: 'words_learned', conversation_id: 'main', msg_id: payload.session_id },
    )
    return json({ session_id: payload.session_id, added: fresh.map(x => itemId(pack.id, x.word_id)), skipped })
  }

  const { target, base } = langs()
  // the languages a new pack's words carry: the village's, and its meanings in the learner's base and in English (an
  // English village's "en" is the word itself, so there it's the base alone)
  const noun = target === 'sl' ? '' : target === 'en' ? ' (a noun without "the", its plural in "plural")' : ' (a noun with its article)'
  const meanings = target === 'en'
    ? `with "${base}" translation, examples in both (example_en, example_${base}), notes in "note_${base}"`
    : base === 'en'
      ? `with "en" translations, examples in both (example_${target}, example_en), notes in "note"`
      : `with "${base}" and "en" translations, examples in all three (example_${target}, example_${base}, example_en), notes in "note" and "note_${base}"`
  return {
    // Jan's paragraph as it always was; a village in another language, or a learner with another base, gets its packs' languages
    instructions: target === 'sl' && base === 'en' ? instructions : `${instructions}\nThis village speaks ${LANGUAGES[target] ?? target}, explained in ${LANGUAGES[base] ?? base}: write packs with "language": "${target}", each word in "${target}"${noun} ${meanings}.`,
    routes: [
      {
        method: 'GET',
        path: '/packs',
        addressed: true,
        handle: () => {
          const items = learner.srItems()
          return json(packs.all().map(p => packInfo(p, items)))
        },
      },
      {
        method: 'GET',
        path: /^\/packs\/([a-z0-9-]+)$/,
        addressed: true,
        handle: ({ params: [id] }) => {
          const p = packs.get(id)
          return p ? json({ ...p, learned: learnedWords(p, learner.srItems()) }) : json({ error: 'not found' }, 404)
        },
      },
      { method: 'POST', path: /^\/packs\/([a-z0-9-]+)\/learn$/, handle: ({ req, url, params: [id] }) => outbox.once(req, url.pathname, r => handleLearn(r, id)) },
    ],
    tools: [
      {
        name: 'publish_pack',
        description:
          'Validate a lani.pack/v0 word pack and publish it to the app (replaces a tutor pack with the same id). Returns validation errors instead of publishing if invalid. Load the lani-studio skill before using. Say the learner {learner} and what agrees with them {m:…|f:…} (companion/SCENES.md, the learner in the content).',
        inputSchema: {
          type: 'object',
          properties: {
            pack: { type: 'object', description: 'The word pack (lani.pack/v0)' },
            note: { type: 'string', description: 'One-line announcement shown in the app' },
          },
          required: ['pack'],
        },
        handle: args => {
          const v = validatePack(args.pack)
          if (!v.ok) return fail(`pack invalid, nothing published:\n${v.errors}`)
          const note = noteIn.safeParse(args.note)
          if (!note.success) return badNote()
          const err = packs.publish(v.pack)
          if (err) return fail(err)
          const { target, base } = langs()
          events.emit({ type: 'pack_published', id: v.pack.id, title: titleFor(v.pack, target, base), emoji: v.pack.emoji, note: note.data ?? undefined })
          voice.enqueue(corpus({ packs: [v.pack], modules: [], scenarios: [], language: target, learner: addressee() }))
          return ok(`published pack ${v.pack.id} (${v.pack.words.length} words)`)
        },
      },
      {
        name: 'list_packs',
        description: "List word packs (curated and tutor-written) with Jan's progress and each pack's words (in the pack's language).",
        inputSchema: { type: 'object', properties: {} },
        handle: () => {
          const items = learner.srItems()
          const list = packs.all().map(p => ({ ...packInfo(p, items), words: p.words.map(w => wordOf(p, w)) }))
          return ok(JSON.stringify(list, null, 2))
        },
      },
    ],
  }
}
