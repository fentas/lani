// Smoke checks: the school's printed sheets for the app (companion/COURSE.md, "Printed sheets"; src/prints.ts):
// GET /course/sheet/<unit>, GET /course/booklet/<level> and GET /course/overview/<page> serve the generator's own HTML
// (companion/prints/build.ts, overview.ts) of the fixture course and the grammar book, for the bridge's learner (Jan, a
// man) or as the query says, kept until the content changes.
import { readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { sheetsOf, unitDoc } from '../../../prints/build'
import { buildOverview, markAdjective, markDeclension, OVERVIEWS, overviewDoc } from '../../../prints/overview'
import { loadContent, plain } from '../../../prints/sheet'
import { auth, base, check, courseRoot, curatedDir } from './harness'

const repo = resolve(import.meta.dir, '../../../..')
const get = (path: string, headers: Record<string, string> = auth) => fetch(`${base}${path}`, { headers })

export default async function prints() {
  // --- a unit's sheet -------------------------------------------------------------------------------------------------
  const sheet = await get('/course/sheet/biti')
  const html = await sheet.text()
  check(
    "GET /course/sheet/biti: the unit's page as HTML, named for the print job",
    sheet.status === 200 && (sheet.headers.get('content-type') ?? '').startsWith('text/html') && sheet.headers.get('x-lani-print-name') === '01-biti' && sheet.headers.get('x-lani-print-units') === '1',
    [sheet.status, Object.fromEntries(sheet.headers)],
  )
  check(
    "… its title, its table, its words, said to the learner ({learner} → Jan), the answers at the foot",
    html.includes('<html lang="en" data-mode="unit">') && html.includes('Biti: sem, si, je') && html.includes('data-unit="biti"') && html.includes('<table class="forms') &&
      html.includes('<td><span class="f">sem</span></td>') && html.includes('Jaz <b>sem</b> Jan.') && html.includes('class="answers"') && !html.includes('{learner}'),
    html.slice(html.indexOf('<body>'), html.indexOf('<body>') + 600),
  )
  check(
    '… standing alone: the CSS, the fonts (č š ž: their latin-ext faces) and fit.js inlined, nothing fetched',
    html.includes('@page') && (html.match(/@font-face\{font-family:'Nunito'/g) ?? []).length === 4 && html.includes('U+0100-02BA') && html.includes('dataset.fitted') && html.includes('id="fit-report"') &&
      !/\b(?:src|href)="(?!data:)/.test(html),
  )
  // one source: what the generator makes of the same content
  const content = loadContent(repo, 'sl', { course: join(courseRoot, 'sl'), grammar: join(repo, 'companion/grammar/sl'), packs: curatedDir, lexicon: join(repo, 'companion/lexicon') })
  const own = unitDoc(content, sheetsOf(content, 'A1', { base: 'en', learner: 'Jan', gender: 'm' }, ['biti'])[0], 'en')
  const asked = await (await get('/course/sheet/biti?base=en&learner=Jan&gender=m')).text()
  check("… the generator's own page of the unit (companion/prints/build.ts), byte for byte", asked === own.html && asked === html, [asked.length, own.html.length])

  const ana = await (await get('/course/sheet/biti?learner=Ana&gender=f&base=de')).text()
  check('?learner, ?gender and ?base say otherwise: Ana, in German', ana.includes('Jaz <b>sem</b> Ana.') && !ana.includes('Jaz <b>sem</b> Jan.') && ana.includes('<html lang="de"') && ana.includes('Die Regel'), ana.slice(ana.indexOf('<body>'), ana.indexOf('<body>') + 400))

  // --- a level's booklet ------------------------------------------------------------------------------------------------
  const booklet = await get('/course/booklet/A1')
  const book = await booklet.text()
  check(
    "GET /course/booklet/A1: the level's booklet, its cover, contents and every unit",
    booklet.status === 200 && (booklet.headers.get('content-type') ?? '').startsWith('text/html') && booklet.headers.get('x-lani-print-name') === 'lani-sl-A1-en' && booklet.headers.get('x-lani-print-units') === '1' &&
      book.includes('data-mode="booklet"') && book.includes('class="cover"') && book.includes('class="toc"') && book.includes('data-unit="biti"') && book.includes('<table class="forms') && book.includes('dataset.fitted'),
    [booklet.status, Object.fromEntries(booklet.headers)],
  )

  // --- the cases overview ------------------------------------------------------------------------------------------------
  const ov = await get('/course/overview/skloni')
  const ovHtml = await ov.text()
  check(
    "GET /course/overview/skloni: the cases overview as HTML, named for the print job, no unit's sheet in it",
    ov.status === 200 && (ov.headers.get('content-type') ?? '').startsWith('text/html') && ov.headers.get('x-lani-print-name') === 'pregled-sklonov' && ov.headers.get('x-lani-print-units') === '0' &&
      ovHtml.includes('<html lang="en" data-mode="overview">') && ovHtml.includes('<section class="overview" data-unit="skloni">') && !ovHtml.includes('class="unit"') && ovHtml.includes('dataset.fitted'),
    [ov.status, Object.fromEntries(ov.headers)],
  )
  const sheetsIn = (ovHtml.match(/<div class="sheet">/g) ?? []).length
  check(
    '… its three pages: the six cases at a glance (each with its question, endings, prepositions and an example), the declension tables with their endings marked, the pronouns, what the genitive, dative and instrumental do',
    sheetsIn === 3 && (ovHtml.match(/<span class="o-n">/g) ?? []).length === 6 && ovHtml.includes('kdo? kaj?') && ovHtml.includes('-om · -o · -om') && ovHtml.includes('Nimam <b>knjige</b>.') &&
      ovHtml.includes('Sklanjatve: moški spol') && ovHtml.includes('s stol<b class="end">om</b>') && ovHtml.includes('Osebni zaimki') && ovHtml.includes('Orodnik: s kom? s čim?') && ovHtml.includes('Strojno napisano'),
    ovHtml.slice(ovHtml.indexOf('<body>'), ovHtml.indexOf('<body>') + 600),
  )
  const ownOverview = overviewDoc(content, 'skloni', { base: 'en', learner: 'Jan', gender: 'm' })
  check("… the generator's own overview (companion/prints/overview.ts), byte for byte", ownOverview?.html === ovHtml && ownOverview.name === OVERVIEWS.skloni.name, [ovHtml.length, ownOverview?.html.length])
  const ovDe = await (await get('/course/overview/skloni?base=de')).text()
  check('… in German with ?base=de: its headings and the pages\' German', ovDe.includes('<html lang="de" data-mode="overview">') && ovDe.includes('Die Fälle auf einen Blick') && ovDe.includes('Ich habe das Buch nicht.'))

  // made of the content as it is: the units' tables when the course has them (kje-kam-od-kod, pridevniki-skloni), else
  // the grammar book's page on where to; nothing written for it
  const without = buildOverview(content, 'skloni', { base: 'en', learner: 'Jan', gender: 'm' })
  const head = (sl: string, en: string) => ({ sl, en })
  const withUnits = {
    ...content,
    units: new Map([
      ...content.units,
      ['kje-kam-od-kod', { id: 'kje-kam-od-kod', level: 'A2', title: head('Kje? Kam? Od kod?', 'Where? Where to? From where?'), intro: { table: { head: [head('kraj', 'place'), 'kje?', 'kam?', 'od kod?'] }, steps: [{ rows: [['v', 'v šoli', 'v šolo', 'iz šole']] }, { rows: [['na', 'na pošti', 'na pošto', 's pošte']] }] } }],
      ['pridevniki-skloni', { id: 'pridevniki-skloni', level: 'A2', title: head('Pridevniki v sklonih', 'Adjectives through the cases'), intro: { table: { head: [head('sklon', 'case'), 'moški', 'ženski', 'srednji'] }, steps: [{ rows: [['1. kdo? kaj?', 'velik gozd', 'velika hiša', 'veliko mesto'], ['2. koga? česa?', 'velikega gozda', 'velike hiše', 'velikega mesta']] }] } }],
    ]),
  }
  const withThem = buildOverview(withUnits, 'skloni', { base: 'en', learner: 'Jan', gender: 'm' })
  check(
    "the overview from the content as it is: without the units, Kje? Kam? from the page kam-tozilnik and no adjectives' table; with them, the units' tables (their endings marked), named as they came",
    !!without && without.sources.includes('grammar:kam-tozilnik') && without.where?.source === 'book' && !without.adjectives && !without.sources.some(s => s.startsWith('unit:')) &&
      !!withThem && withThem.where?.source === 'unit' && withThem.where.table.rows.map(r => r[3].sl).join() === 'iz šole,s pošte' && withThem.sources.includes('unit:kje-kam-od-kod') &&
      withThem.adjectives?.table.rows[1].map(c => c.sl).slice(1).join('|') === 'velik⟦ega⟧ gozda|velik⟦e⟧ hiše|velik⟦ega⟧ mesta' && !withThem.sources.includes('grammar:kam-tozilnik'),
    [without?.sources, withThem?.sources, withThem?.adjectives?.table.rows[1]],
  )
  check(
    'the cases at a glance from the page skloni: each case with its question, what it does (its example\'s note), its endings (the card) and a sentence with its form bold',
    without?.glance.rows.map(r => `${r.n}.${r.name.sl}:${r.question}`).join() === '1.imenovalnik:kdo? kaj?,2.rodilnik:koga? česa?,3.dajalnik:komu? čemu?,4.tožilnik:koga? kaj?,5.mestnik:pri kom? o čem?,6.orodnik:s kom? s čim?' &&
      without.glance.rows.every(r => r.does && r.endings && r.example?.sl.includes('**')) && plain(without.glance.rows[5].example?.sl ?? '') === 'Nejc pride s knjigo.',
    without?.glance.rows,
  )
  const decl = { head: [{ sl: 'sklon' }, { sl: 'ednina' }, { sl: 'množina' }], rows: [[{ sl: 'imenovalnik' }, { sl: 'okno, vas' }, { sl: 'okna, vasi' }], [{ sl: 'rodilnik' }, { sl: 'okna, vasi' }, { sl: 'oken, vasi' }], [{ sl: 'orodnik' }, { sl: 'z oknom, z vasjo' }, { sl: 'z okni, z vasmi' }]], from: 'x' }
  markDeclension(decl)
  const adj = { head: [{ sl: 'sklon' }, { sl: 'moški' }, { sl: 'ženski' }], rows: [[{ sl: '1.' }, { sl: 'dober kruh' }, { sl: 'nova hiša' }], [{ sl: '2.' }, { sl: 'dobrega kruha' }, { sl: 'v novi hiši' }]], from: 'x' }
  markAdjective(adj)
  check(
    'the endings marked against the words (okn⟦o⟧, vas⟦jo⟧; oken and a stem that changes left as they are)',
    decl.rows.map(r => r.slice(1).map(c => c.sl).join(' | ')).join(' / ') === 'okn⟦o⟧, vas | okn⟦a⟧, vas⟦i⟧ / okn⟦a⟧, vas⟦i⟧ | oken, vas⟦i⟧ / z okn⟦om⟧, z vas⟦jo⟧ | z okn⟦i⟧, z vas⟦mi⟧' &&
      adj.rows[1].slice(1).map(c => c.sl).join(' | ') === 'dobrega kruha | v nov⟦i⟧ hiši',
    [decl.rows, adj.rows],
  )

  // --- what there is none of, what is refused ------------------------------------------------------------------------------
  const none: [string, number][] = [
    ['/course/sheet/osebni-zaimki', 404], // planned: not written yet
    ['/course/sheet/no-such-unit', 404],
    ['/course/booklet/B1', 404], // no units yet
    ['/course/booklet/X9', 404], // no such level: not a route
    ['/course/overview/biti', 404], // a page without an overview
    ['/course/overview/no-such-page', 404],
    ['/course/overview/skloni?base=fr', 400],
    ['/course/sheet/biti?base=fr', 400],
    ['/course/sheet/biti?gender=x', 400],
  ]
  for (const [path, status] of none) {
    const r = await get(path)
    check(`GET ${path}: ${status}`, r.status === status, [r.status, await r.text()])
  }
  check('the print routes need the token', (await get('/course/sheet/biti', {})).status === 401 && (await get('/course/booklet/A1', {})).status === 401 && (await get('/course/overview/skloni', {})).status === 401)

  // --- kept until the content changes ------------------------------------------------------------------------------------
  const unitFile = join(courseRoot, 'sl/units/biti.json')
  const saved = readFileSync(unitFile, 'utf8')
  const fixed = JSON.parse(saved)
  fixed.title.sl = 'Biti: sem, si, je, smo'
  writeFileSync(unitFile, JSON.stringify(fixed, null, 2))
  const changed = await (await get('/course/sheet/biti')).text()
  writeFileSync(unitFile, saved)
  const back = await (await get('/course/sheet/biti')).text()
  check('a unit changed prints as it is now, and as it was once changed back', changed.includes('Biti: sem, si, je, smo') && back === html, [changed.includes('Biti: sem, si, je, smo'), back === html])
}
