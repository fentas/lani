// Smoke checks: the grammar book (lani.grammar/v0). The curated Slovene pages (companion/grammar/sl) and what names
// them (the curated modules' exercises, the tent challenge), serving them, the tutor's pages and extensions, the guide.
import { existsSync, readdirSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { cultureIds, packGrammar, readManifest, validateCulture } from '../../src/cultures'
import { extend, GrammarStore, tagMatches, validateGrammar, type GrammarPage } from '../../src/grammar'
import { channelRules, INSTRUCTIONS_LIMIT } from '../../src/learners'
import { dialogGrammar, validateScene } from '../../src/scenes'
import { validateModule } from '../../src/spec'
import { auth, base, channelEvents, check, client, dataDir, guideOf } from './harness'

const repo = resolve(import.meta.dir, '../../..')
const text = (r: any): string => r?.content?.[0]?.text ?? ''
/** Who reads each language's pages: the rule, the titles, the examples in these bases (English always). */
const BASES: Record<string, string[]> = { sl: ['en', 'de', 'it'], it: ['sl', 'en', 'de'], de: ['sl', 'en', 'it'], en: ['sl', 'it', 'de'] }

export default async function grammar() {
  // --- the curated pages -----------------------------------------------------------------------------------------
  const dir = join(repo, 'grammar/sl')
  const files = readdirSync(dir).filter(f => f.endsWith('.json')).sort()
  const read = files.map(f => ({ f, raw: JSON.parse(readFileSync(join(dir, f), 'utf8')) }))
  const results = read.map(({ f, raw }) => ({ f, v: validateGrammar(raw) }))
  check('every curated grammar page validates', results.every(r => r.v.ok), results.filter(r => !r.v.ok).map(r => [r.f, (r.v as { errors: string }).errors]))
  const pages = results.flatMap(r => (r.v.ok ? [r.v.page] : []))
  const ids = new Set(pages.map(p => p.id))
  check('15 or more Slovene pages, each in the file of its id', pages.length >= 15 && results.every(r => !r.v.ok || `${r.v.page.id}.json` === r.f), files)
  check('each page has its title, rule and examples in English, German and Italian', pages.every(p => ['en', 'de', 'it'].every(l => p.title[l] && p.rule[l] && p.examples.every(e => typeof e[l] === 'string'))), pages.map(p => p.id))
  check('each page is machine-written and says who checked it', pages.every(p => p.machine_written && p.review?.includes('not reviewed by a native speaker')))
  check('a page sees only pages there are', pages.every(p => p.see.every(s => ids.has(s))), pages.map(p => [p.id, p.see.filter(s => !ids.has(s))]))
  const moduleIds = readdirSync(join(repo, 'modules')).filter(f => f.endsWith('.json')).map(f => f.replace(/\.json$/, ''))
  check('a page links only curated modules', pages.every(p => p.modules.every(m => moduleIds.includes(m))), pages.map(p => [p.id, p.modules]))
  check('no page keeps a key the format lacks (strict)', read.every(({ raw }) => validateGrammar({ ...raw, colour: 'red' }).ok === false))

  // what names them: every exercise of the curated modules, the tent challenge's questions
  const modules = moduleIds.map(id => validateModule(JSON.parse(readFileSync(join(repo, 'modules', `${id}.json`), 'utf8'))))
  const named = modules.flatMap(m => (m.ok ? m.spec.exercises.map(e => e.grammar) : []))
  check('every exercise of the curated modules names a grammar page there is', modules.every(m => m.ok) && named.length === 72 && named.every(g => !!g && ids.has(g)), named.filter(g => !g || !ids.has(g)))
  const tent = JSON.parse(readFileSync(join(repo, 'cultures/primorska/quests.json'), 'utf8')).tent_move.questions as { grammar?: string }[]
  check("the tent challenge's questions name the pages of their rules", JSON.stringify(tent.map(q => q.grammar)) === JSON.stringify(['kje-mestnik-orodnik', 'orodnik', 'rodilnik-predlogi', 'orodnik']), tent.map(q => q.grammar))
  const reached = new Set([...named, ...tent.map(q => q.grammar)])
  // the others unlock with the learner's own cards and mistakes (their tags) and with the tutor's modules
  check('10 of the pages or more are met in the curated practice', [...ids].filter(id => reached.has(id)).length >= 10, [...ids].filter(id => !reached.has(id)))

  // --- every language's book: each page valid, in its bases; every page a culture pack or a scene names is one ------
  const books: Record<string, Set<string>> = {}
  for (const [lang, bases] of Object.entries(BASES)) {
    const d = join(repo, 'grammar', lang)
    const got = (existsSync(d) ? readdirSync(d).filter(f => f.endsWith('.json')).sort() : []).map(f => ({ f, v: validateGrammar(JSON.parse(readFileSync(join(d, f), 'utf8'))) }))
    const ps = got.flatMap(r => (r.v.ok ? [r.v.page] : []))
    books[lang] = new Set(ps.map(p => p.id))
    check(`${lang}: every curated page validates, is of ${lang}, in the file of its id`, got.every(r => r.v.ok && r.v.page.language === lang && `${r.v.page.id}.json` === r.f), got.filter(r => !r.v.ok || r.v.page.language !== lang).map(r => [r.f, r.v.ok ? r.v.page.language : r.v.errors]))
    const all = [lang, ...bases]
    const lacks = ps.flatMap(p => [
      ...all.filter(l => !p.title[l]).map(l => `${p.id}: title.${l}`),
      ...bases.filter(l => !p.rule[l]).map(l => `${p.id}: rule.${l}`),
      ...p.examples.flatMap((e, i) => all.filter(l => typeof e[l] !== 'string').map(l => `${p.id}: examples[${i}].${l}`)),
      ...p.examples.flatMap((e, i) => (e.note ? bases.filter(l => !e.note![l]).map(l => `${p.id}: examples[${i}].note.${l}`) : [])),
      ...(p.table ? [...p.table.head, ...p.table.rows.flat()].flatMap(c => (typeof c === 'string' ? [] : bases.filter(l => !c[l]).map(l => `${p.id}: a table cell's ${l}`))) : []),
    ])
    check(`${lang}: each page reads in every base (${bases.join(', ')}): its title, rule, examples, notes and table`, lacks.length === 0, lacks.slice(0, 20))
    check(`${lang}: each page is machine-written, says so, and sees only pages of its book`, ps.every(p => p.machine_written && p.review?.includes('not reviewed by a native speaker') && p.see.every(s => books[lang].has(s))), ps.filter(p => !p.see.every(s => books[lang].has(s))).map(p => p.id))
  }
  check("the Slovene book's pages are the ones above", [...books.sl].join() === [...ids].join())
  check('Italian, German and English have 16 curated pages each', ['it', 'de', 'en'].every(l => books[l].size === 16), Object.fromEntries(Object.entries(books).map(([l, s]) => [l, s.size])))
  const tents = Object.fromEntries(['friuli', 'kaernten', 'lakeland'].map(c => [c, (JSON.parse(readFileSync(join(repo, 'cultures', c, 'quests.json'), 'utf8')).tent_move.questions as { grammar?: string }[]).map(q => q.grammar)]))
  check(
    "every village's tent questions name the pages of their rules",
    JSON.stringify(tents) === JSON.stringify({ friuli: ['dove-preposizioni', 'dove-preposizioni', 'preposizioni-articolate', 'dove-preposizioni'], kaernten: ['wechselpraepositionen', 'wechselpraepositionen', 'dativ', 'wechselpraepositionen'], lakeland: Array(4).fill('prepositions-place') }),
    tents,
  )
  const culturesRoot = join(repo, 'cultures')
  for (const id of cultureIds(culturesRoot)) {
    const m = readManifest(culturesRoot, id)
    if (!m.ok || m.manifest.status !== 'complete') continue
    const lang = m.manifest.language
    const v = validateCulture(culturesRoot, id, undefined, undefined, books[lang] ?? new Set())
    check(`${id}: every page its requests, questions and scenes' choices name is a page of the ${lang} book`, v.ok || !v.errors.includes('no grammar page'), !v.ok && v.errors.split('\n').filter(e => e.includes('no grammar page')))
  }
  const sceneRefs = readdirSync(join(repo, 'scenes')).filter(f => f.endsWith('.json')).sort().flatMap(f => {
    const v = validateScene(JSON.parse(readFileSync(join(repo, 'scenes', f), 'utf8')))
    return v.ok ? dialogGrammar([...v.scene.dialogs, ...v.scene.variants]).map(([at, g]) => [`${f}: ${at}`, g] as const) : [[`${f}: invalid`, ''] as const]
  })
  check("every page a Slovene scene's choices name is a page of the book", sceneRefs.every(([, g]) => books.sl.has(g)), sceneRefs.filter(([, g]) => !books.sl.has(g)))

  // the format: a choice and a turn name a page, kept; not a spoken line; a page a pack names that isn't there is said
  const turnScene = (extra: object, line: object = {}) => ({
    schema: 'lani.scene/v0', id: 'smoke-grammar-turn', title: 'Test · Test', art: 'kitchen', from: ['hut'],
    people: [{ id: 'luka', name: 'Luka', art: 'shepherd', slot: 'stove' }],
    happenings: [{ id: 'h', title: 'Luka · Luka', who: 'luka', dialog: 'd' }],
    dialogs: [{ id: 'd', lines: [{ who: 'luka', sl: 'Kam greš?', en: 'Where are you going?', ...line }, { choices: [{ sl: 'V gozd.', en: 'To the forest.', ok: true }, { sl: 'V gozdu.', en: 'In the forest.', why: 'Where to: v gozd.', grammar: 'kam-tozilnik' }], ...extra }, { who: 'luka', sl: 'Pojdiva!', en: "Let's go!" }] }],
  })
  const kept = validateScene(turnScene({ grammar: 'vprasalnice' }))
  check("a scene's wrong choice and its turn name a grammar page, and keep it", kept.ok && kept.scene.dialogs[0].lines[1].grammar === 'vprasalnice' && kept.scene.dialogs[0].lines[1].choices[1].grammar === 'kam-tozilnik' && JSON.stringify(dialogGrammar(kept.scene.dialogs).map(([, g]) => g)) === '["vprasalnice","kam-tozilnik"]', kept)
  const spoken = validateScene(turnScene({}, { grammar: 'kam-tozilnik' }))
  check("a grammar page on someone's spoken line is refused", !spoken.ok && spoken.errors.includes('not on a spoken line'), spoken)
  check('a grammar page with a bad id is refused', !validateScene(turnScene({ grammar: 'Not A Page' })).ok)
  const pubScene = await client.callTool({ name: 'publish_scene', arguments: { scene: turnScene({ grammar: 'no-such-rule' }) } })
  check('publish_scene notes a turn naming a page the book lacks, not one it has', !pubScene.isError && text(pubScene).includes('no grammar page "no-such-rule"') && !text(pubScene).includes('"kam-tozilnik"'), pubScene)
  const servedScene = await (await fetch(`${base}/scenes/smoke-grammar-turn`, { headers: auth })).json()
  check('the app gets the scene with its turn and choice pages', servedScene.dialogs?.[0]?.lines?.[1]?.grammar === 'no-such-rule' && servedScene.dialogs[0].lines[1].choices[1].grammar === 'kam-tozilnik', servedScene.dialogs?.[0]?.lines?.[1])
  await client.callTool({ name: 'remove_scene', arguments: { id: 'smoke-grammar-turn' } })
  const noPage = packGrammar({
    quests: { requests: [{ giver: 'x', emoji: 'x', skill: 'stone', title: { sl: 'x' }, story: { sl: 'x' }, topics: [], grammar: 'dvojina' }], lines: {} as never },
    surprises: { letter: { letters: [{ questions: [{ grammar: 'no-such-page' }] }] }, pilgrim: { ways: [{ grammar: 'rodilnik-predlogi' }] } } as never,
  })
  check("a pack's requests, letters' questions and pilgrim's ways name their pages, with where", JSON.stringify(noPage) === JSON.stringify([['quests.requests[0].grammar', 'dvojina'], ['surprises.letter.letters[0].questions[0].grammar', 'no-such-page'], ['surprises.pilgrim.ways[0].grammar', 'rodilnik-predlogi']]), noPage)

  // tags and extensions, pure
  check('a tag matches its words in a row', tagMatches('accusative', 'accusative_feminine_a_to_o') && tagMatches('grammar_biti', 'grammar_biti') && tagMatches('iz', 'vocab_iz_genitive_fem') && tagMatches('clitic', 'clitic_placement_se'))
  check('a tag matches no part of a word', !tagMatches('od', 'food') && !tagMatches('genitive', 'genitives') && !tagMatches('dual_verb', 'dual'))
  const kjeFile = pages.find(p => p.id === 'kje-mestnik-orodnik')!
  const mine: GrammarPage = {
    ...kjeFile,
    rule: { en: 'A rule of my own.' },
    examples: [...kjeFile.examples.slice(0, 2), { sl: 'Luka sedi pred šotorom.', en: 'Luka is sitting in front of the tent.' }],
    more: { en: 'Think of Kje? as a photo, Kam? as a film.' },
    tags: ['pred'],
  }
  const merged = extend(kjeFile, mine)
  check('an extension keeps the curated rule and table, adds the new example marked', merged.rule.en === kjeFile.rule.en && merged.table === kjeFile.table && merged.examples.length === kjeFile.examples.length + 1 && merged.examples.at(-1)?.by === 'tutor' && merged.more?.en.includes('photo') && merged.tags.includes('pred') && merged.tags.includes('locative'), merged)

  // --- served ------------------------------------------------------------------------------------------------------
  const list = (await (await fetch(`${base}/grammar`, { headers: auth })).json()) as any[]
  check('GET /grammar serves the curated Slovene pages', Array.isArray(list) && list.length === pages.length && list.every(p => p.source === 'curated' && p.language === 'sl'), list.map?.(p => [p.id, p.source]))
  const kje = await (await fetch(`${base}/grammar/kje-mestnik-orodnik`, { headers: auth })).json()
  check('GET /grammar/:id serves a page: title, rule, table, examples', kje.title?.sl === 'Kje? Predlogi kraja' && kje.rule?.de && kje.table?.rows?.length === 6 &&kje.examples?.[0]?.sl === 'Šotor bo stal ob ribniku.' && kje.modules?.[0] === 'predlogi-kraja', kje)
  check('GET /grammar/:id is a 404 for a page there is not', (await fetch(`${base}/grammar/nope`, { headers: auth })).status === 404)
  check('GET /grammar needs the token', (await fetch(`${base}/grammar`)).status === 401)

  // --- the tutor writes pages ----------------------------------------------------------------------------------------
  const page = {
    schema: 'lani.grammar/v0', id: 'smoke-s-z', level: 'A2', emoji: '🥛',
    title: { sl: 'S, z: z orodnikom', en: 'S, z: with' },
    rule: { en: '**z** (with) takes the instrumental: kava **z mlekom**; before p, t, k … it is **s**: **s sladkorjem**.' },
    table: { head: [{ en: 'preposition' }, { en: 'example' }], rows: [['z', 'z mlekom'], ['s', 's sladkorjem']] },
    examples: [{ sl: 'Kavo pijem z mlekom.', en: 'I drink my coffee with milk.' }],
    modules: ['predlogi-kraja', 'no-such-module'],
    tags: ['with_instrumental'],
    see: ['kje-mestnik-orodnik', 'no-such-page'],
  }
  const noEn = await client.callTool({ name: 'publish_grammar', arguments: { page: { ...page, rule: { de: 'nur Deutsch' } } } })
  check('publish_grammar refuses a rule without English', noEn.isError && text(noEn).includes('en'), noEn)
  const shortRow = await client.callTool({ name: 'publish_grammar', arguments: { page: { ...page, table: { head: [{ en: 'a' }, { en: 'b' }], rows: [['z']] } } } })
  check('publish_grammar refuses a table row shorter than its head', shortRow.isError, shortRow)
  const noTarget = await client.callTool({ name: 'publish_grammar', arguments: { page: { ...page, examples: [{ en: 'no Slovene' }] } } })
  check("publish_grammar refuses an example without the page's language", noTarget.isError && text(noTarget).includes('"sl"'), noTarget)
  const italian = await client.callTool({ name: 'publish_grammar', arguments: { page: { ...page, id: 'smoke-it', language: 'it', title: { it: 'Con', en: 'With' }, examples: [{ it: 'Con il latte.', en: 'With milk.' }] } } })
  check("publish_grammar refuses a page of another language than the village's", italian.isError && text(italian).includes('"sl"'), italian)

  const pub = await client.callTool({ name: 'publish_grammar', arguments: { page, note: 'A page on s and z' } })
  check('publish_grammar publishes a new page, the language taken from the village', !pub.isError && text(pub).includes('published grammar page smoke-s-z'), pub)
  check('publish_grammar says which links lead nowhere', text(pub).includes('no page "no-such-page"') && text(pub).includes('no module "no-such-module"'), text(pub))
  check("the tutor's page is written to <data>/app/grammar", existsSync(join(dataDir, 'app/grammar/smoke-s-z.json')))
  await Bun.sleep(100)
  const backlog = (await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as any[]
  const published = backlog.find(b => b.event.type === 'grammar_published' && b.event.id === 'smoke-s-z')?.event
  check('grammar_published event with the title in the learner\'s pair', published?.title === 'S, z: z orodnikom · S, z: with' && published.emoji === '🥛' && published.note === 'A page on s and z' && published.extended === false, published)
  const theirs = (await (await fetch(`${base}/grammar`, { headers: auth })).json()) as any[]
  check("GET /grammar lists the tutor's page after the curated ones, as the tutor's", theirs.at(-1)?.id === 'smoke-s-z' && theirs.at(-1)?.source === 'tutor' && theirs.at(-1)?.machine_written === true && typeof theirs.at(-1)?.published_at === 'number', theirs.at(-1))

  // extending a curated page: its text stays, the example and the explanation are the tutor's
  const got = JSON.parse(text(await client.callTool({ name: 'get_grammar', arguments: { id: 'kje-mestnik-orodnik' } })))
  const extended = await client.callTool({
    name: 'publish_grammar',
    arguments: { page: { ...got, rule: { en: 'Shorter.' }, examples: [...got.examples, { sl: 'Luka sedi pred šotorom.', en: 'Luka is sitting in front of the tent.' }], more: { en: 'Kje? is a photo, Kam? a film.' } } },
  })
  check('publish_grammar extends a curated page (get_grammar, add, publish again)', !extended.isError && text(extended).includes('extended the curated page kje-mestnik-orodnik: 1 example(s) of yours, your explanation'), extended)
  const after = await (await fetch(`${base}/grammar/kje-mestnik-orodnik`, { headers: auth })).json()
  check("an extended page keeps the curated rule, adds the tutor's example marked and the explanation", after.rule.en === kje.rule.en && after.source === 'curated' && after.extended === true && after.examples.length === kje.examples.length + 1 && after.examples.at(-1).by === 'tutor' && after.examples.at(-1).sl === 'Luka sedi pred šotorom.' && after.more?.en === 'Kje? is a photo, Kam? a film.', after)
  const again = await client.callTool({ name: 'publish_grammar', arguments: { page: { ...got, examples: got.examples } } })
  check('publishing the curated page as it is takes the addition back', !again.isError && text(again).includes(': 0 example(s) of yours'), again)
  const back = await (await fetch(`${base}/grammar/kje-mestnik-orodnik`, { headers: auth })).json()
  check('… and the page is the curated one again, still marked extended', back.examples.length === kje.examples.length && !back.more && back.extended === true, back)

  // what Jan met: the village state's grammar ("sl/<id>"), as the app writes it
  const gamePath = join(dataDir, 'app/game.json')
  const existed = existsSync(gamePath)
  const doc = existed ? JSON.parse(readFileSync(gamePath, 'utf8')) : { rev: 0, state: {} }
  writeFileSync(gamePath, JSON.stringify({ ...doc, state: { ...doc.state, grammar: { 'sl/kje-mestnik-orodnik': { on: '2026-09-27', right: 3, wrong: 1 }, 'it/other': { on: '2026-09-27' } } } }))
  const listed = JSON.parse(text(await client.callTool({ name: 'list_grammar', arguments: {} })))
  const kjeListed = listed.pages?.find((p: any) => p.id === 'kje-mestnik-orodnik')
  check("list_grammar shows the pages and what Jan met of this village's language", listed.language === 'sl' && listed.met === 1 && kjeListed?.met?.right === 3 && kjeListed.title === 'Where? Prepositions of place' && listed.pages.find((p: any) => p.id === 'smoke-s-z')?.source === 'tutor', listed)
  check('list_grammar from an app before rules not yet: no meetings, nothing ripe', listed.ripe === undefined && listed.pages.every((p: any) => p.met_in_dialogs === undefined), listed)
  // rules not yet: the dialogs said them without asking (GameState.meetings); the next level's, met often enough, are ripe
  const meetings = {
    'sl/dajalnik': { times: 4, days: 2, first: '2026-09-26', last: '2026-09-27' },
    'sl/orodnik': { times: 2, days: 1, first: '2026-09-27', last: '2026-09-27' },
    'sl/odvisni-govor': { times: 9, days: 3, first: '2026-09-20', last: '2026-09-27' },
    'sl/kje-mestnik-orodnik': { times: 5, days: 2, first: '2026-09-20', last: '2026-09-21' },
  }
  writeFileSync(gamePath, JSON.stringify({ ...doc, state: { ...doc.state, grammar: { 'sl/kje-mestnik-orodnik': { on: '2026-09-27' } }, meetings } }))
  const seen = JSON.parse(text(await client.callTool({ name: 'list_grammar', arguments: {} })))
  check(
    'list_grammar says how often the dialogs said a rule, and which are ripe to introduce: the next level, met 3 times, page not open',
    seen.level === 'A1' && JSON.stringify(seen.ripe?.map((r: any) => [r.id, r.met])) === '[["dajalnik",4]]' &&
      seen.pages.find((p: any) => p.id === 'orodnik')?.met_in_dialogs?.times === 2,
    seen,
  )
  const { ripeRules } = await import('../../src/grammar')
  const book = pages.map(p => ({ ...p, source: 'curated' as const }))
  const ripeAt = (level: string) => ripeRules(book, { dajalnik: { times: 3 }, 'odvisni-govor': { times: 7 } }, {}, level).map(r => r.id).join()
  check('a rule two levels up waits for the learner; at A2 the B1 one is next', ripeAt('A1') === 'dajalnik' && ripeAt('A2') === 'odvisni-govor' && ripeAt('B1') === '', [ripeAt('A1'), ripeAt('A2')])
  // the village as it was (none yet: the game section starts without one)
  if (existed) writeFileSync(gamePath, JSON.stringify(doc))
  else unlinkSync(gamePath)
  const none = await client.callTool({ name: 'get_grammar', arguments: { id: 'nope' } })
  check('get_grammar of a page there is not is an error', none.isError, none)

  // a module naming a page the book lacks is published, and the tutor hears it
  const tagged = await client.callTool({
    name: 'publish_module',
    arguments: { spec: { schema: 'lani.module/v0', id: 'smoke-grammar-tags', title: 'Tags', level: 'A1', exercises: [{ type: 'cloze', grammar: 'tozilnik', text: 'Pijem ___.', accept: ['kavo'] }, { type: 'cloze', grammar: 'no-such-rule', text: 'Jem ___.', accept: ['juho'] }] } },
  })
  check('publish_module notes an exercise naming a page the book lacks', !tagged.isError && text(tagged).includes('no grammar page "no-such-rule"') && !text(tagged).includes('"tozilnik"'), tagged)
  const served = await (await fetch(`${base}/modules/smoke-grammar-tags`, { headers: auth })).json()
  check("a module's exercises keep their grammar page", served.exercises?.[0]?.grammar === 'tozilnik', served)
  check('an exercise naming a page with a bad id is refused', !validateModule({ schema: 'lani.module/v0', id: 'x-y', title: 'x', level: 'A1', exercises: [{ type: 'cloze', grammar: 'Not An Id', text: 'a ___', accept: ['b'] }] }).ok)

  // the store: a page file of another language, a broken one, skipped
  const store = new GrammarStore(dir, 'sl', join(dataDir, 'unused-grammar'))
  check('the store reads the curated pages in file order', store.all().map(p => p.id).join() === pages.map(p => p.id).join())
  const empty = new GrammarStore(join(repo, 'grammar/fr'), 'fr', join(dataDir, 'unused-grammar-fr'))
  check('a language without curated pages has an empty book', empty.all().length === 0)
  const itBook = new GrammarStore(join(repo, 'grammar/it'), 'it', join(dataDir, 'unused-grammar-it'))
  check('an Italian book reads its curated pages, all of them Italian', itBook.all().length === books.it.size && itBook.all().every(p => p.language === 'it' && p.source === 'curated'))

  // the guide and the rules
  const guide = await guideOf()
  check('the channel guide says when to write and extend pages', guide.includes('publish_grammar') && guide.includes('data.about_grammar') && guide.includes('data.grammar_drill') && guide.includes('"grammar": "<page id>"'))
  const rules = channelRules({ id: 'default', isDefault: true, child: false, culture: 'primorska' }, { name: '', target: '', base: '' }, '/data')
  check('the channel rules name the grammar book and stay within the limit', rules.includes('grammar book') && rules.length < INSTRUCTIONS_LIMIT, rules.length)

  // the lani-studio reference: its example validates
  const reference = readFileSync(resolve(import.meta.dir, '../../../../.claude/skills/lani-studio/reference/grammar-spec.md'), 'utf8')
  const example = /```json\n(\{\n  "schema": "lani\.grammar\/v0"[\s\S]*?\n\})\n```/.exec(reference)?.[1]
  check('grammar-spec.md example validates', !!example && validateGrammar(JSON.parse(example)).ok, example && validateGrammar(JSON.parse(example)))
  void channelEvents
}
