// The name check (src/name-check.ts) against the voice service's own verdicts (test/fixtures/name-check-verdicts.json, made by
// companion/bin/names-sync from a glas checkout): every probe gets the same verdict here, so a node with no service refuses what
// the service refuses on its lists. The smoke's name-lines section runs this. On its own: `bun test/name-check.ts`.
import './isolate'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { checkName, nameLists, nameRefusal, readNameLists, NAME_LISTS_DIR } from '../src/name-check'

type Check = (name: string, cond: unknown, detail?: unknown) => void
type Row = [string, 'invalid'] | [string, 'blocked', string] | [string, 'known'] | [string, 'unknown', number, boolean]

export default function nameCheck(check: Check) {
  const fixture = JSON.parse(readFileSync(join(import.meta.dir, 'fixtures/name-check-verdicts.json'), 'utf8')) as { glas: { commit: string }; names: Row[] }
  const sources = JSON.parse(readFileSync(join(NAME_LISTS_DIR, 'sources.json'), 'utf8')) as { glas: { commit: string; given_listed: number } }
  check('name check: the lists and the verdicts come from one glas commit', fixture.glas.commit === sources.glas.commit, { fixture: fixture.glas, lists: sources.glas })
  const lists = nameLists()
  check("name check: the lists are read, and the given names the lists touch are all there", lists.data.lists.length >= 30 && lists.givenCount > 600 && lists.givenCount <= sources.glas.given_listed, { lists: lists.data.lists.length, given: lists.givenCount })

  // each probe as the service has it
  const differ: unknown[] = []
  for (const row of fixture.names) {
    const [name, want] = row
    const got = checkName(name, lists)
    const same =
      want === 'invalid' ? got.verdict === 'invalid'
      : want === 'blocked' ? got.verdict === 'blocked' && got.list === row[2]
      : want === 'known' ? got.verdict === 'clear'
      : got.verdict === 'clear' && got.words === row[2] && got.hides === row[3]
    if (!same) differ.push({ name, glas: row.slice(1), bridge: got })
  }
  check(`name check: the bridge gives glas's verdict on each of its ${fixture.names.length} probes`, fixture.names.length > 3000 && !differ.length, differ.slice(0, 8))
  const of = (v: string) => fixture.names.filter(r => r[1] === v).length
  check('name check: the probes have every verdict, many of each', of('invalid') > 100 && of('blocked') > 500 && of('known') > 1000 && of('unknown') > 100, { invalid: of('invalid'), blocked: of('blocked'), known: of('known'), unknown: of('unknown') })

  // what the bridge refuses: a list's word and a name that is none; a real name, a kept one, a name nobody knows go on
  const refused = (n: string) => nameRefusal(n, lists)
  check('name check: a list\'s word is refused, however it is written', ['Kurba', 'KURBA', 'ｋurba', 'Kûrba', 'Kurba-Svinja', 'Jan Kurba', 'Dick', 'Dĭck', 'Kill Yourself', 'Moese'].every(n => refused(n) === 'blocked'), ['Kurba', 'ｋurba', 'Dĭck', 'Kill Yourself'].map(n => [n, refused(n)]))
  check("name check: … and a word of the shape rules' own short list is no name at all", ['Scheiße', 'scheisse', 'Heil Hitler', 'Fuck'].every(n => refused(n) === 'invalid'))
  check('name check: a name that is none, or a letter a voice reads as another, is refused', ['ᴋurba', 'Kurba Kurba Kurba', 'Юлия', 'R2D2', 'Admin', ''].every(n => refused(n) === 'invalid'), ['ᴋurba', 'Kurba Kurba Kurba'].map(n => [n, refused(n)]))
  check("name check: real names go on, those a list's word is in among them", ['Jan', 'Teo', 'Ana Marija', 'Peter', 'Mosè', 'Beom-sik', 'Cassandra', 'Marica', 'Marica Novak', 'Guido', 'Thông', 'Gergő', "O'Brien"].every(n => !refused(n)), ['Peter', 'Mosè', 'Beom-sik', 'Marica Novak'].map(n => [n, refused(n)]))
  check('name check: names nobody knows go on too: whether they wait for approval is the service\'s', ['Zzqbor', 'Qwertina', 'Kurbasvinja', 'Zuzq Novak'].every(n => !refused(n)) && checkName('Kurbasvinja', lists).verdict === 'clear')
  check('name check: a kept word lifts its own hit only', !refused('Marica Novak') && refused('Marica Kurba') === 'blocked')

  // the given names matter only where a list's word is in them: without them Peter would be the French "péter"
  const bare = readNameLists(NAME_LISTS_DIR)
  check('name check: the lists read again are the same', bare.givenCount === lists.givenCount && nameRefusal('Peter', bare) === undefined)
}

if (import.meta.main) {
  let failed = 0
  const check: Check = (name, cond, detail) => {
    if (!cond) {
      failed++
      console.log('❌', name, detail === undefined ? '' : JSON.stringify(detail))
    } else if (process.env.VERBOSE) console.log('✅', name)
  }
  nameCheck(check)
  console.log(failed ? `${failed} failed` : 'all passed')
  process.exit(failed ? 1 : 0)
}
