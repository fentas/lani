// Smoke checks: the cheat sheet (lani.card/v0, cards.ts). The curated cards (companion/cards/<language>) and the grammar
// pages' own (their `card`), serving them, the tutor's publish_card, list_cards and remove_card, the guide.
import { existsSync, readdirSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { cardExamples, cardIdOf, CardStore, decayed, sheetUse, validateCard } from '../../src/cards'
import { validateGrammar } from '../../src/grammar'
import { localDate } from '../../src/rhythm'
import { auth, base, check, client, dataDir, guideOf, tempDir } from './harness'

const repo = resolve(import.meta.dir, '../../..')
const text = (r: any): string => r?.content?.[0]?.text ?? ''
/** Who reads each language's cards: the bases their texts are in (English always), as the grammar book's pages. */
const BASES: Record<string, string[]> = { sl: ['en', 'de', 'it'], it: ['sl', 'en', 'de'], de: ['sl', 'en', 'it'], en: ['sl', 'it', 'de'] }

export default async function cards() {
  // --- the curated cards and the pages' own ------------------------------------------------------------------------
  let curatedSl = 0
  for (const [lang, bases] of Object.entries(BASES)) {
    const pageDir = join(repo, 'grammar', lang)
    const pages = existsSync(pageDir) ? readdirSync(pageDir).filter(f => f.endsWith('.json')).flatMap(f => { const v = validateGrammar(JSON.parse(readFileSync(join(pageDir, f), 'utf8'))); return v.ok ? [v.page] : [] }) : []
    const ids = new Set(pages.map(p => p.id))
    const cell = (c: unknown) => (typeof c === 'string' ? [] : bases.filter(l => !(c as Record<string, string>)[l]))
    const lacks = (where: string, c: { title?: unknown; rule?: Record<string, string>; tip?: Record<string, string>; examples?: Record<string, unknown>[]; table?: { head: unknown[]; rows: unknown[][] } }) => [
      ...(c.title && typeof c.title === 'object' ? [lang, ...bases].filter(l => !(c.title as Record<string, string>)[l]).map(l => `${where}: title.${l}`) : []),
      ...(c.rule ? bases.filter(l => !c.rule![l]).map(l => `${where}: rule.${l}`) : []),
      ...(c.tip ? bases.filter(l => !c.tip![l]).map(l => `${where}: tip.${l}`) : []),
      ...(c.examples ?? []).flatMap((e, i) => [lang, ...bases].filter(l => typeof e[l] !== 'string').map(l => `${where}: examples[${i}].${l}`)),
      ...(c.table ? [...c.table.head, ...c.table.rows.flat()].flatMap(x => cell(x).map(l => `${where}: a table cell's ${l}`)) : []),
    ]
    const pageCards = pages.filter(p => p.card)
    check(`${lang}: each page's own card reads in every base (${bases.join(', ')})`, pageCards.flatMap(p => lacks(p.id, p.card!)).length === 0, pageCards.flatMap(p => lacks(p.id, p.card!)).slice(0, 20))
    const dir = join(repo, 'cards', lang)
    const files = existsSync(dir) ? readdirSync(dir).filter(f => f.endsWith('.json')).sort() : []
    const got = files.map(f => ({ f, v: validateCard(JSON.parse(readFileSync(join(dir, f), 'utf8'))) }))
    check(`${lang}: every curated card validates, is of ${lang}, in the file of its id`, got.every(r => r.v.ok && r.v.card.language === lang && `${r.v.card.id}.json` === r.f), got.filter(r => !r.v.ok || r.v.card.language !== lang).map(r => [r.f, r.v.ok ? r.v.card.language : r.v.errors]))
    const cs = got.flatMap(r => (r.v.ok ? [r.v.card] : []))
    check(`${lang}: each curated card reads in every base, names pages of its book, is machine-written and says so`, cs.every(c => !c.markdown && typeof c.title === 'object' && c.grammar.length > 0 && c.grammar.every(g => ids.has(g)) && c.machine_written && c.review?.includes('not reviewed by a native speaker')) && cs.flatMap(c => lacks(c.id, c)).length === 0, [cs.filter(c => !c.grammar.every(g => ids.has(g))).map(c => c.id), cs.flatMap(c => lacks(c.id, c)).slice(0, 20)])
    if (lang === 'sl') curatedSl = cs.length
  }

  // --- the formats ---------------------------------------------------------------------------------------------------
  const biti = JSON.parse(readFileSync(join(repo, 'grammar/sl/biti.json'), 'utf8'))
  const own = { rule: { en: '**sem, si, je** …', de: '**sem, si, je** …', it: '**sem, si, je** …' }, examples: [{ sl: 'Doma sem.', en: "I'm at home.", de: 'Ich bin zu Hause.', it: 'Sono a casa.' }], start: true }
  check("a page's own card validates (a rule line, examples, start)", validateGrammar({ ...biti, card: own }).ok, validateGrammar({ ...biti, card: own }))
  check("a page's own card needs its rule line", !validateGrammar({ ...biti, card: { examples: own.examples } }).ok)
  check("a page's own card keeps no key the format lacks", !validateGrammar({ ...biti, card: { ...own, colour: 'red' } }).ok)
  check("a page's own card's example is in the page's language", !validateGrammar({ ...biti, card: { ...own, examples: [{ en: 'no Slovene' }] } }).ok)
  check("a page's own card has at most three examples", !validateGrammar({ ...biti, card: { ...own, examples: Array(4).fill(own.examples[0]) } }).ok)
  const card = {
    schema: 'lani.card/v0', id: 'smoke-zaimki', level: 'A1', emoji: '👥',
    title: { sl: 'Jaz, midva, mi', en: 'I, we two, we' },
    rule: { en: 'Slovene counts **one, two, three+**.' },
    table: { head: [{ en: 'who' }, { en: '1' }, { en: '2' }, { en: '3+' }], rows: [['I/we', 'jaz', 'midva', 'mi']] },
    examples: [{ sl: 'Midva greva domov.', en: 'The two of us are going home.' }],
    grammar: ['dvojina'], start: true,
  }
  check('a curated card validates: a title per language, its rule line, a table, examples, its pages', validateCard(card).ok, validateCard(card))
  check('a card needs its rule line or the Markdown', !validateCard({ ...card, rule: undefined }).ok)
  check("a Markdown card is the whole card: no rule beside it", !validateCard({ ...card, markdown: '| a | b |' }).ok)
  check('a card keeps no key the format lacks (strict)', !validateCard({ ...card, colour: 'red' }).ok)
  check("a card's example is in its language, its title too", !validateCard({ ...card, examples: [{ en: 'x' }] }).ok && !validateCard({ ...card, title: { en: 'only English' } }).ok)
  check('cardIdOf: a title to an id, marks taken off', cardIdOf('Imeti: vse osebe!') === 'imeti-vse-osebe' && cardIdOf('Čigav? Kdo, kaj …') === 'cigav-kdo-kaj' && cardIdOf('!') === 'card-x', [cardIdOf('Imeti: vse osebe!'), cardIdOf('Čigav? Kdo, kaj …')])
  check('an open counts half after two weeks, nothing without a count', Math.abs(decayed({ n: 2, on: '2026-10-01' }, '2026-10-15') - 1) < 1e-9 && decayed(undefined, '2026-10-15') === 0 && decayed({ n: 3, on: '2026-10-15' }, '2026-10-15') === 3)
  const ex = cardExamples([{ id: 'biti', language: 'sl', card: own as any }, { id: 'mestnik', language: 'sl' }], [{ id: 'smoke-zaimki', language: 'sl', examples: card.examples }, { id: 'md', language: 'sl' }])
  check("the cards' examples are voiced as the pages' are: a page's own card's, a card's", JSON.stringify(ex.map(p => [p.id, p.examples.length])) === '[["card:biti",1],["card:smoke-zaimki",1]]', ex)

  // --- served --------------------------------------------------------------------------------------------------------
  const list = (await (await fetch(`${base}/cards`, { headers: auth })).json()) as any[]
  check('GET /cards serves the curated Slovene cards', Array.isArray(list) && list.length === curatedSl && list.every(c => c.source === 'curated' && c.language === 'sl'), list.map?.(c => [c.id, c.source]))
  check('GET /cards needs the token', (await fetch(`${base}/cards`)).status === 401)
  check('GET /cards/:id is a 404 for a card there is not', (await fetch(`${base}/cards/nope`, { headers: auth })).status === 404)
  const pages = (await (await fetch(`${base}/grammar`, { headers: auth })).json()) as any[]
  check("GET /grammar serves a page's own card with the page", pages.filter(p => p.card).every(p => typeof p.card.rule?.en === 'string'), pages.filter(p => p.card).map(p => p.id))

  // --- the tutor's cards ---------------------------------------------------------------------------------------------
  const md = '**imeti** (to have): the whole verb\n\n| | 1 | 2 | 3+ |\n|---|---|---|---|\n| I/we | **imam** | **imava** | **imamo** |\n\n- *Vida **ima** gostilno.*'
  const noMd = await client.callTool({ name: 'publish_card', arguments: { title: 'Imeti', markdown: '' } })
  check('publish_card refuses a card without its Markdown', noMd.isError, noMd)
  const noPage = await client.callTool({ name: 'publish_card', arguments: { title: 'Imeti', markdown: md, grammar: ['no-such-page'] } })
  check('publish_card refuses a page the book lacks', noPage.isError && text(noPage).includes('no-such-page'), noPage)
  const badLevel = await client.callTool({ name: 'publish_card', arguments: { title: 'Imeti', markdown: md, level: 'Z9' } })
  check('publish_card refuses a level there is not', badLevel.isError, badLevel)
  const pub = await client.callTool({ name: 'publish_card', arguments: { title: 'Imeti: vse osebe', markdown: md, grammar: ['imeti-iti'], note: 'The whole of imeti' } })
  check('publish_card publishes, its id from the title, the level the learner\'s', !pub.isError && text(pub).includes('published card imeti-vse-osebe') && text(pub).includes('A1') && text(pub).includes('imeti-iti'), pub)
  check("the tutor's card is written to <data>/app/cards", existsSync(join(dataDir, 'app/cards/imeti-vse-osebe.json')))
  await Bun.sleep(100)
  const backlog = (await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as any[]
  const published = backlog.find(b => b.event.type === 'card_published' && b.event.id === 'imeti-vse-osebe')?.event
  check('card_published event with its title, emoji and note', published?.title === 'Imeti: vse osebe' && published.emoji === '📋' && published.note === 'The whole of imeti', published)
  const theirs = (await (await fetch(`${base}/cards`, { headers: auth })).json()) as any[]
  const mine = theirs.at(-1)
  check("GET /cards lists the tutor's card after the curated ones, as the tutor's", mine?.id === 'imeti-vse-osebe' && mine.source === 'tutor' && mine.markdown === md && mine.grammar?.[0] === 'imeti-iti' && mine.machine_written === true && typeof mine.published_at === 'number', mine)
  const again = await client.callTool({ name: 'publish_card', arguments: { id: 'imeti-vse-osebe', title: 'Imeti · imam, imaš', markdown: md, level: 'A2', emoji: '🎒' } })
  check('publish_card under the same id replaces the card', !again.isError && text(again).includes('replaced card imeti-vse-osebe'), again)
  const one = await (await fetch(`${base}/cards/imeti-vse-osebe`, { headers: auth })).json()
  check('GET /cards/:id serves the card as replaced', one.title === 'Imeti · imam, imaš' && one.level === 'A2' && one.emoji === '🎒', one)

  // a tutor's card can't take a curated card's id (a store of its own: the curated deck may be empty)
  const curated = tempDir('lani-smoke-cards-')
  writeFileSync(join(curated, 'smoke-zaimki.json'), JSON.stringify(card))
  const store = new CardStore(curated, 'sl', tempDir('lani-smoke-cards-tutor-'))
  const v = validateCard({ ...card, title: 'mine', rule: undefined, table: undefined, examples: undefined, markdown: '- a' })
  check("the store refuses a tutor's card under a curated id", v.ok && 'error' in store.publish(v.card), v)
  check("the store refuses a card of another language than the sheet's", v.ok && 'error' in store.publish({ ...v.card, id: 'other', language: 'it' }))
  check('the store reads the curated cards', store.all().map(c => `${c.id}:${c.source}`).join() === 'smoke-zaimki:curated')

  // list_cards: the pages', the cards, what Jan pinned and opens most (the village state's sheet, as the app writes it)
  const gamePath = join(dataDir, 'app/game.json')
  const existed = existsSync(gamePath)
  const doc = existed ? JSON.parse(readFileSync(gamePath, 'utf8')) : { rev: 0, state: {} }
  const today = localDate(new Date()) // the app's and the bridge's day, not UTC's (yesterday's after midnight)
  const sheet = {
    pinned: [{ id: 'pin-m1', language: 'sl', title: 'mi / vi / oni', markdown: '| | 1 | 2 | 3+ |', pages: ['osebni-zaimki'], level: 'A1', on: today }, { id: 'pin-it', language: 'it', title: 'io', markdown: '- io' }],
    opens: { 'sl/page:biti': { n: 4, on: today }, 'sl/deck:smoke': { n: 1, on: today }, 'it/page:essere': { n: 9, on: today } },
    lookups: { 'sl/biti': { [today]: 2 }, 'sl/dvojina': { [today]: 3 }, 'it/essere': { [today]: 5 } },
  }
  writeFileSync(gamePath, JSON.stringify({ ...doc, state: { ...doc.state, sheet } }))
  const listed = JSON.parse(text(await client.callTool({ name: 'list_cards', arguments: {} })))
  check(
    "list_cards shows the pages' cards, the tutor's, what Jan pinned and opens most (this language's)",
    listed.language === 'sl' && listed.pages.length === pages.length && listed.pages[0].id.startsWith('page:') &&
      listed.cards.some((c: any) => c.id === 'imeti-vse-osebe' && c.source === 'tutor' && c.markdown) &&
      JSON.stringify(listed.pinned.map((p: any) => p.id)) === '["pin-m1"]' && JSON.stringify(listed.opened.map((o: any) => [o.card, o.opens])) === '[["page:biti",4],["deck:smoke",1]]',
    listed,
  )
  check("list_cards shows the pages Jan looked up during exercises (this language's), most first", JSON.stringify(listed.looked_up) === JSON.stringify([{ page: 'dvojina', n: 3, last: today }, { page: 'biti', n: 2, last: today }]), listed.looked_up)
  check('sheetUse reads a village from before the sheet', JSON.stringify(sheetUse(undefined, 'sl', today)) === '{"pinned":[],"opened":[]}')
  if (existed) writeFileSync(gamePath, JSON.stringify(doc))
  else unlinkSync(gamePath)

  const removed = await client.callTool({ name: 'remove_card', arguments: { id: 'imeti-vse-osebe' } })
  check("remove_card takes the tutor's card back", !removed.isError && !existsSync(join(dataDir, 'app/cards/imeti-vse-osebe.json')), removed)
  await Bun.sleep(100)
  const after = (await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as any[]
  check('card_removed event', after.some(b => b.event.type === 'card_removed' && b.event.id === 'imeti-vse-osebe'))
  const gone = await client.callTool({ name: 'remove_card', arguments: { id: 'imeti-vse-osebe' } })
  check('remove_card of a card the tutor has not is an error', gone.isError, gone)

  // the guide
  const guide = await guideOf()
  check('the channel guide says when to publish a card', guide.includes('publish_card') && guide.includes('Plonk listek') && guide.includes('list_cards'))
  check('… and that opening the sheet during an exercise is help, not a hint, what was looked up shown by list_cards, get_village and get_course',
    guide.includes('(help, not a hint: a right') && guide.includes('the last two weeks; get_village and get_course show it too'))
}
