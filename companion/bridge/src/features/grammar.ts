// The grammar book (lani.grammar/v0, grammar.ts): its pages for the app, and the tutor's tools to add and extend them.
import { badNote, fail, noteIn, ok } from '../channel'
import type { FeatureFactory } from '../feature'
import { ofLanguage, pageInfo, RIPE, ripeRules, titleFor, validateGrammar, type LoadedGrammar, type Meetings } from '../grammar'
import { json } from '../http'
import { LANGUAGES, learnerLangs } from '../learners'
import { learnerLevel } from '../villagers'
import { corpus } from '../voice'

const instructions = `
Grammar book: the app's village book has a chapter "📖 Slovnica · Grammar", one page per rule (lani.grammar/v0): the
rule in Jan's base language, a table, examples, the modules that practise it, and Jan's own mistakes on it. A page
unlocks the first time Jan meets its rule: an exercise that names it ("grammar": "<page id>" on any exercise). When you
explain a rule in chat (a "why", a grammar question), keep it in the book: list_grammar, then publish_grammar the page
again with your example or a clearer explanation ("more") added, or a new page for a rule the book lacks (load
lani-studio first: reference/grammar-spec.md). Tag the exercises of every grammar module you write with "grammar",
and a scene's wrong choice (or its turn) whose why is a rule the book has: its why then leads to the page.
A chat with data.about_grammar comes from a page's "💬 Vprašaj · Ask" button: answer about that rule. One with
data.grammar_drill asks for a short drill on the page: publish a module whose exercises name it, then add the module to
the page's "modules".
The app asks a turn that tests a page's rule (a scene's form choice, a choice exercise with a gap) by how far Jan has it
(data.about_grammar.mastery): new or learning, they choose; secure (4 right in a row, 3 stars, no open mistake), they type
the missing word; mastered (8 in a row over days, 4 stars), they say the whole sentence; a slip steps it back a level. An
open mistake on the rule (mistakes-db) keeps it learning until a review of its card is right on a later day. Where a turn
tests a form, the app shows Jan's own confusion as the wrong choice, taken from mistakes-db's examples: record errors[]
with your_answer exactly as Jan had it and correct_answer the right sentence (companion/GAME.md, "Mastery and adaptive
turns").
Rules not yet (companion/GAME.md): a rule whose page's level is above Jan's (learner-profile current_level) and that
wasn't introduced (its page not unlocked, no answers, cards or mistakes on it) isn't asked for. The dialogs stay natural,
but where a turn's form is of such a rule its wrong choices of that form go: Jan chooses about meaning, or hears the line
and repeats it (an echo). Each such turn meets the rule (list_grammar: met_in_dialogs). Met ${RIPE} times, a rule of the
next level is ripe to introduce (list_grammar's "ripe", a session_end's data.grammar_ripe, the morning snapshot): introduce
it when Jan is ready, with a module whose exercises name it (playing it unlocks the page) or the page extended for them
(publish_grammar under its id, a "more" in their words: it unlocks when published). After 6 meetings on 2 days the app
opens the page itself. From then on its turns ask for it. Write the dialogs natural at the scene's level; their forms
above Jan's level are fine: Jan meets them first.
`.trim()

/** The chapter's name in the village's language, as the app's village book shows it (l10n "townScroll.grammar"). */
const CHAPTER: Record<string, string> = { sl: 'Slovnica', it: 'Grammatica', de: 'Grammatik', en: 'Grammar' }

/**
 * A village in another language than Slovene: whose book it is, what its curated pages are, and what else names them
 * (the culture pack's requests, questions and scenes' choices).
 */
const otherBook = (lang: string, name: string, curated: number) =>
  `This village's grammar book is in ${name} ("language": "${lang}", the chapter "📖 ${CHAPTER[lang] ?? 'Grammar'}"): ${
    curated ? `${curated} curated pages of the rules ${name} learners meet first, explained in the learner's base language (list_grammar); the culture pack's requests, the tent's and the surprises' questions and the scenes' wrong choices name them too, so they unlock as Jan plays. Extend a curated page, or write one for a rule the book lacks.` : `no curated pages yet, so write the rules ${name} learners meet.`
  }`

/** The learner's record of a page in the village state (the app's GameState.grammar, "<language>/<id>"); `run`: right in a row now. */
type Met = { on?: string; right?: number; wrong?: number; run?: number }

export const grammar: FeatureFactory = ({ grammar: book, modules, game, events, voice, cfg, culture }) => {
  const langs = () => learnerLangs(cfg.dataDir, culture.manifest?.language ?? 'sl')
  /** What the learner has met of each page, from the village state. */
  const met = (): Record<string, Met> =>
    ofLanguage((game.read()?.state as { grammar?: Record<string, Met> } | undefined)?.grammar, book.language)
  /** The rules not introduced yet the dialogs said, from the village state (an app from before has none). */
  const meetings = (): Record<string, Meetings> =>
    ofLanguage((game.read()?.state as { meetings?: Record<string, Meetings> } | undefined)?.meetings, book.language)
  /** Ids a page names that the book or the modules don't have: said back to the tutor, not refused. */
  const unknown = (p: { id: string; see: string[]; modules: string[] }) => {
    const pages = new Set(book.all().map(x => x.id))
    const mods = new Set(modules.list().map(m => m.id))
    return [
      ...p.see.filter(id => !pages.has(id) && id !== p.id).map(id => `see: no page "${id}"`),
      ...p.modules.filter(id => !mods.has(id)).map(id => `modules: no module "${id}"`),
    ]
  }
  const own = book.language

  return {
    // Jan's paragraph as written; a village in another language says whose book it is
    instructions: own === 'sl' ? instructions : `${instructions}\n${otherBook(own, LANGUAGES[own] ?? own, book.curated().length)}`,
    routes: [
      { method: 'GET', path: '/grammar', handle: () => json(book.all()) },
      {
        method: 'GET',
        path: /^\/grammar\/([a-z0-9-]+)$/,
        handle: ({ params: [id] }) => {
          const p = book.get(id)
          return p ? json(p) : json({ error: 'not found' }, 404)
        },
      },
    ],
    tools: [
      {
        name: 'publish_grammar',
        description:
          "Validate a lani.grammar/v0 page of the grammar book and publish it to the app: a new page, or a page again with your examples or a clearer explanation (\"more\") added. Under a curated page's id it extends that page (the curated text stays; your examples are marked yours). Returns validation errors instead of publishing if invalid. Load the lani-studio skill before using.",
        inputSchema: {
          type: 'object',
          properties: {
            page: { type: 'object', description: 'The grammar page (lani.grammar/v0)' },
            note: { type: 'string', description: 'One-line announcement shown in the app' },
          },
          required: ['page'],
        },
        handle: args => {
          const input = args.page && typeof args.page === 'object' && !Array.isArray(args.page) ? { language: own, ...(args.page as object) } : args.page
          const v = validateGrammar(input)
          if (!v.ok) return fail(`page invalid, nothing published:\n${v.errors}`)
          const note = noteIn.safeParse(args.note)
          if (!note.success) return badNote()
          const r = book.publish(v.page)
          if ('error' in r) return fail(r.error)
          const served = book.get(v.page.id) as LoadedGrammar
          const { target, base } = langs()
          events.emit({ type: 'grammar_published', id: served.id, title: titleFor(served, target, base), emoji: served.emoji, extended: r.extended, note: note.data ?? undefined })
          // the examples are heard with 🔊: voiced like a module's lines
          voice.enqueue(corpus({ packs: [], modules: [], scenarios: [], grammar: [served], language: own }))
          const warn = unknown(served)
          const what = r.extended
            ? `extended the curated page ${served.id}: ${r.added} example(s) of yours${v.page.more ? ', your explanation' : ''}`
            : `published grammar page ${served.id} (${served.examples.length} examples)`
          return ok(`${what}${warn.length ? `\nNote: ${warn.join('; ')}` : ''}`)
        },
      },
      {
        name: 'list_grammar',
        description:
          "List the grammar book's pages (curated and yours) with their level, examples, modules and tags, and what Jan has met: the date a page unlocked and their answers on it (right, wrong; run: right in a row now, since the day `since`); for a rule not introduced yet, how often the dialogs said it (met_in_dialogs); and the rules ripe to introduce (ripe).",
        inputSchema: { type: 'object', properties: {} },
        handle: () => {
          const m = met()
          const seen = meetings()
          const pages = book.all().map(p => ({ ...pageInfo(p), met: m[p.id] ?? null, ...(seen[p.id]?.times ? { met_in_dialogs: seen[p.id] } : {}) }))
          const level = learnerLevel(cfg.dataDir)
          const ripe = ripeRules(book.all(), seen, m, level)
          return ok(JSON.stringify({ language: own, level, met: pages.filter(p => p.met).length, ...(ripe.length ? { ripe } : {}), pages }, null, 2))
        },
      },
      {
        name: 'get_grammar',
        description: 'Fetch one grammar page as the app shows it (to extend it: add to it and publish_grammar it again).',
        inputSchema: { type: 'object', properties: { id: { type: 'string' } }, required: ['id'] },
        handle: args => {
          const p = book.get(String(args.id))
          return p ? ok(JSON.stringify(p, null, 2)) : fail(`no grammar page ${args.id}; list_grammar shows the ${book.all().length} there are`)
        },
      },
    ],
  }
}
