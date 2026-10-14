// Smoke checks: the school's printed sheets for the app (companion/COURSE.md, "Printed sheets"; src/prints.ts):
// GET /course/sheet/<unit> and GET /course/booklet/<level> serve the generator's own HTML (companion/prints/build.ts) of
// the fixture course, for the bridge's learner (Jan, a man) or as the query says, kept until the content changes.
import { readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { sheetsOf, unitDoc } from '../../../prints/build'
import { loadContent } from '../../../prints/sheet'
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

  // --- what there is none of, what is refused ------------------------------------------------------------------------------
  const none: [string, number][] = [
    ['/course/sheet/osebni-zaimki', 404], // planned: not written yet
    ['/course/sheet/no-such-unit', 404],
    ['/course/booklet/B1', 404], // no units yet
    ['/course/booklet/X9', 404], // no such level: not a route
    ['/course/sheet/biti?base=fr', 400],
    ['/course/sheet/biti?gender=x', 400],
  ]
  for (const [path, status] of none) {
    const r = await get(path)
    check(`GET ${path}: ${status}`, r.status === status, [r.status, await r.text()])
  }
  check('the print routes need the token', (await get('/course/sheet/biti', {})).status === 401 && (await get('/course/booklet/A1', {})).status === 401)

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
