// The cheat sheet (lani.card/v0, cards.ts; companion/README.md "Cheat sheet"): its cards that aren't a grammar page's, for
// the app (the curated ones and the tutor's), and the tutor's tools to add one, list them, take one back.
import { badNote, fail, noteIn, ok } from '../channel'
import { cardIdOf, cardTitleEn, CARD_ID, sheetUse, validateCard, type LoadedCard } from '../cards'
import type { FeatureFactory } from '../feature'
import { json } from '../http'
import { learnerLangs } from '../learners'
import { localDate } from '../rhythm'
import { learnerLevel } from '../villagers'

const instructions = `
Cheat sheet ("📋 Plonk listek · Cheat sheet"): compact reference cards of the basics in the app (a bold rule line, a small
table, two or three examples, a 💡 tip), opened from Home, the chat's header and any exercise (free: help, not a hint).
Every grammar page gives a card (its "card", else made from the page), unlocked the day Jan meets the page; a few A1
basics are there from the start (the pronouns 1 · 2 · 3+, biti, the question words, the numbers). Jan can pin a message of
yours with a table or a list ("📌 Na plonk listek"). When you explain a basic in chat with a table or a list worth keeping
(a whole verb, the pronouns, endings, a case at a glance), publish it yourself with publish_card: your message's table or
list as the card's markdown, short (one bold rule line, the table, two or three examples, at most one 💡 tip: one phone
screen), in Jan's base language with the Slovene in bold, and the grammar pages it is about (list_grammar's ids), so it
comes first in their exercises. Don't publish what a card already says (list_cards; publish under the same id to improve
yours). list_cards also shows what Jan pinned and which cards they open most: what they look up often is what to practise.
`.trim()

export const cards: FeatureFactory = ({ cards: store, grammar: book, game, events, cfg, culture }) => {
  const own = store.language
  const langs = () => learnerLangs(cfg.dataDir, culture.manifest?.language ?? 'sl')
  /** The village state's sheet (GameState.sheet: what the app keeps of the cards). */
  const sheet = () => (game.read()?.state as { sheet?: unknown } | undefined)?.sheet
  const brief = (c: LoadedCard) => ({ id: c.id, title: cardTitleEn(c), level: c.level, source: c.source, grammar: c.grammar, ...(c.start ? { start: true } : {}), ...(c.markdown ? { markdown: true } : {}) })

  return {
    instructions,
    routes: [
      { method: 'GET', path: '/cards', addressed: true, handle: () => json(store.all()) },
      {
        method: 'GET',
        path: /^\/cards\/([a-z0-9-]+)$/,
        addressed: true,
        handle: ({ params: [id] }) => {
          const c = store.get(id)
          return c ? json(c) : json({ error: 'not found' }, 404)
        },
      },
    ],
    tools: [
      {
        name: 'publish_card',
        description:
          "Publish a card to the learner's cheat sheet (📋 Plonk listek): a basic you just explained, as a compact reference. markdown: the card itself as you write in chat (one bold rule line, a | table | or a - list, two or three examples, at most one 💡 tip; one phone screen), in the learner's base language with the Slovene in **bold**. grammar: the grammar pages it is about (list_grammar's ids): it comes first in their exercises. Publishing again under the same id replaces your card. Returns validation errors instead of publishing if invalid.",
        inputSchema: {
          type: 'object',
          properties: {
            title: { type: 'string', description: 'Short title, ≤80 chars: "Imeti · imam, imaš …"' },
            markdown: { type: 'string', description: 'The card in Markdown (≤2400 chars)' },
            grammar: { type: 'array', items: { type: 'string' }, description: 'Grammar page ids it belongs to (list_grammar)' },
            level: { type: 'string', enum: ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'], description: "Default: the learner's level" },
            emoji: { type: 'string', description: 'Default 📋' },
            id: { type: 'string', description: 'kebab-case; default: from the title. The same id replaces your earlier card.' },
            note: { type: 'string', description: 'One-line announcement shown in the app' },
          },
          required: ['title', 'markdown'],
        },
        handle: args => {
          const title = typeof args.title === 'string' ? args.title.trim() : ''
          const id = typeof args.id === 'string' && args.id ? args.id : cardIdOf(title)
          const v = validateCard({
            schema: 'lani.card/v0',
            id,
            language: own,
            level: args.level ?? learnerLevel(cfg.dataDir),
            ...(args.emoji ? { emoji: args.emoji } : {}),
            title,
            markdown: args.markdown,
            grammar: args.grammar ?? [],
            review: 'machine-written by the tutor (Claude); not reviewed by a native speaker',
          })
          if (!v.ok) return fail(`card invalid, nothing published:\n${v.errors}`)
          const note = noteIn.safeParse(args.note)
          if (!note.success) return badNote()
          const pages = new Set(book.all().map(p => p.id))
          const unknown = v.card.grammar.filter(g => !pages.has(g))
          if (unknown.length) return fail(`no grammar page ${unknown.join(', ')}; list_grammar shows the ${pages.size} there are. Nothing published.`)
          const r = store.publish(v.card)
          if ('error' in r) return fail(r.error)
          events.emit({ type: 'card_published', id: v.card.id, title: cardTitleEn(v.card), emoji: v.card.emoji, note: note.data ?? undefined })
          return ok(`${r.replaced ? 'replaced' : 'published'} card ${v.card.id} on the cheat sheet (${v.card.level}${v.card.grammar.length ? `, ${v.card.grammar.join(', ')}` : ''}): it is on Jan's sheet at once, marked ✨ new`)
        },
      },
      {
        name: 'list_cards',
        description:
          "List the cheat sheet: the grammar pages' cards (one per page: hand-written ones marked), the curated cards no page fits and yours (id, title, level, grammar pages), the tutor messages Jan pinned, and the cards Jan opens most (a count that halves every two weeks).",
        inputSchema: { type: 'object', properties: {} },
        handle: () => {
          const { base } = langs()
          const pages = book.all().map(p => ({ id: `page:${p.id}`, title: p.title[base] ?? p.title.en, level: p.level, ...(p.card ? { hand_written: true } : {}), ...(p.card?.start ? { start: true } : {}) }))
          const use = sheetUse(sheet(), own, localDate(new Date()))
          return ok(JSON.stringify({ language: own, pages, cards: store.all().map(brief), pinned: use.pinned, opened: use.opened.slice(0, 15) }, null, 2))
        },
      },
      {
        name: 'remove_card',
        description: 'Take one of your cards off the cheat sheet (publish_card, by its id). Curated cards and the grammar pages\' stay.',
        inputSchema: { type: 'object', properties: { id: { type: 'string' } }, required: ['id'] },
        handle: args => {
          const id = String(args.id ?? '')
          if (!CARD_ID.test(id) || !store.remove(id)) return fail(`you have no card "${id}"; list_cards shows yours (source "tutor")`)
          events.emit({ type: 'card_removed', id })
          return ok(`removed your card ${id} from the cheat sheet`)
        },
      },
    ],
  }
}
