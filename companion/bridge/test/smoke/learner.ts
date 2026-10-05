// Smoke checks: the learner in the content (companion/SCENES.md, "The learner in the content"): a first name declined and a
// text said to a learner as the app says them (the shared cases of test/fixtures/learner-render.json), the curated content
// said to Jan as it read before its placeholders (test/learner-content.ts), and the bridge saying what it serves, speaks
// and is given to the learner of the profile, checking the placeholders the tutor writes.
import { readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { addressee, learnerErrors, render, sloveneName, type Gender } from '../../src/addressee'
import { corpus } from '../../src/voice'
import { audit, compare, FIXTURE, repoDir, type Fixture } from '../learner-content'
import { auth, base, check, client, culturesDir, dataDir } from './harness'

const text = (r: any) => (r.content ?? []).map((c: any) => c.text ?? '').join('\n')
const cases = JSON.parse(readFileSync(resolve(import.meta.dir, '../fixtures/learner-render.json'), 'utf8'))

/** The profile of the smoke's learner says [learner] (its name and gender) until [then] has run; then it is as it was. */
async function asLearner<T>(learner: { name: string; gender?: Gender }, then: () => Promise<T>): Promise<T> {
  const path = join(dataDir, 'learner-profile.json')
  const was = readFileSync(path, 'utf8')
  const p = JSON.parse(was)
  p.learner = { ...p.learner, name: learner.name, ...(learner.gender ? { gender: learner.gender } : {}) }
  writeFileSync(path, JSON.stringify(p, null, 2))
  try {
    return await then()
  } finally {
    writeFileSync(path, was)
  }
}

export default async function learner() {
  // --- the shared cases: the app's LearnerTest reads them too ------------------------------------------------------------
  const names = cases.names.filter((n: any) => JSON.stringify(sloveneName(n.name, n.gender)) !== JSON.stringify(n.forms))
  check('first names decline as the app declines them (Jan, Ana, Luka, Tone, Nejc, Pavel, Peter, Igor, Nives …)', names.length === 0, names.map((n: any) => [n.name, sloveneName(n.name, n.gender)]))
  const texts = cases.texts.filter((t: any) => render(t.text, addressee(t.learner)) !== t.said)
  check('a text is said to a learner as the app says it: the name and its cases, a gender pair, the word for a friend without a name, the other braces left alone', texts.length === 0, texts.map((t: any) => [t.text, render(t.text, addressee(t.learner))]))
  const errors = cases.errors.filter((e: any) => learnerErrors(e.text).length !== e.errors)
  check('a malformed placeholder is found: an unknown case, a pair without both forms or with one twice, braces of another kind in it', errors.length === 0, errors.map((e: any) => [e.text, learnerErrors(e.text)]))

  // --- said to Jan, the curated content reads as it did ----------------------------------------------------------------
  const fx: Fixture = JSON.parse(readFileSync(FIXTURE, 'utf8'))
  const r = compare(fx)
  check(`said to Jan, every text of the curated content reads as it did at ${fx.base} (${r.compared} files compared, ${r.edited.length} edited since)`, Object.keys(r.differ).length === 0 && r.missing.length === 0 && r.compared > 0, r.differ)

  const woman = addressee(cases.content_learner)
  const textAt = (file: string, path: string): unknown => path.split('/').reduce<any>((x, k) => x?.[/^\d+$/.test(k) ? Number(k) : k], JSON.parse(readFileSync(join(repoDir, file), 'utf8')))
  const samples = cases.content.filter((c: any) => {
    const t = textAt(c.file, c.path)
    return typeof t !== 'string' || render(t, woman) !== c.said
  })
  check(`said to ${woman.name}, a woman, the content's sampled lines agree with her (${cases.content.length}: villagers, an arrival, stories, a reading, scenes, a module, the grammar, a pack, a role-play; in Italian, German, English too)`, samples.length === 0, samples.map((c: any) => [c.file, c.path, render(String(textAt(c.file, c.path)), woman)]))
  const a = audit()
  check('the curated content names no learner Jan (Jan Vitovec, the captain, aside)', a.literal.length === 0, a.literal.slice(0, 10))
  check("the curated content's placeholders are all well formed ({learner}, its cases, {m:…|f:…})", a.errors.length === 0, a.errors.slice(0, 10))
  check("no turn of the curated content has two choices that read the same to a man or to a woman", a.same.length === 0, a.same.slice(0, 10))

  // --- the bridge says what it serves to the learner of the profile -------------------------------------------------------
  const luka = JSON.parse(readFileSync(join(culturesDir, 'primorska/villagers/luka.json'), 'utf8'))
  const l = (sl: string, en: string, level = 0) => ({ sl, en, level })
  const spec = {
    ...luka,
    id: 'n-mia-novak',
    name: 'Mia Novak',
    lines: {
      ...luka.lines,
      greet: [l('Dober dan!', 'Good day!'), l('Živjo, {learner}! Si {m:lačen|f:lačna}?', 'Hi, {learner}! Are you hungry?', 1)],
      thanks: [l('Hvala, {m:dragi|f:draga} {learner}!', 'Thanks, dear {learner}!'), l('Hvala {learner:dat} za vse.', 'Thanks to {learner} for everything.')],
    },
  }
  const pub = await client.callTool({ name: 'publish_villager', arguments: { villager: spec } })
  check('publish_villager takes the learner\'s placeholders in any line ({learner}, its cases, {m:…|f:…})', !pub.isError, text(pub))
  const greetOf = async () => (await (await fetch(`${base}/villagers/n-mia-novak`, { headers: auth })).json()).lines
  const ana = await asLearner({ name: 'Ana', gender: 'female' }, greetOf)
  const jan = await asLearner({ name: 'Jan' }, greetOf)
  check('GET /villagers/:id says their lines to the profile\'s learner: Ana (a woman), Jan (no gender: a man)',
    ana.greet[1].sl === 'Živjo, Ana! Si lačna?' && ana.thanks[0].sl === 'Hvala, draga Ana!' && ana.thanks[1].en === 'Thanks to Ana for everything.' && ana.thanks[1].sl === 'Hvala Ani za vse.' &&
      jan.greet[1].sl === 'Živjo, Jan! Si lačen?' && jan.thanks[0].sl === 'Hvala, dragi Jan!',
    { ana, jan })
  const all = await asLearner({ name: 'Ana', gender: 'female' }, async () => await (await fetch(`${base}/villagers?time=evening`, { headers: auth })).text())
  check('… and GET /villagers, the cast too: no placeholder of the learner goes out', all.includes('Živjo, Ana! Si lačna?') && !/\{learner|\{[mf]:/.test(all))
  const items = corpus({ packs: [], modules: [], scenarios: [], villagers: [spec], learner: addressee({ name: 'Ana', gender: 'female' }) }).map(i => i.text)
  check('the voice corpus is what the app says: each line said to the learner (so a clip is keyed by it)', items.includes('Živjo, Ana! Si lačna?') && items.includes('Hvala Ani za vse.') && !items.some(t => t.includes('{')), items)
  const without = corpus({ packs: [], modules: [], scenarios: [], villagers: [spec] }).map(i => i.text)
  check('… and without a learner, a line with a placeholder is voiced when played, as a counted line', !without.some(t => t.includes('{')) && !without.includes('Živjo, Ana! Si lačna?') && without.includes('Dober dan!'), without)
  const bad = await client.callTool({ name: 'publish_villager', arguments: { villager: { ...spec, lines: { ...spec.lines, thanks: [l('Hvala, {learner:vok}!', 'Thanks!'), l('Hvala, {m:dragi}!', 'Thanks!')] } } } })
  check('publish_villager refuses a malformed placeholder of the learner, saying how they are written', bad.isError && text(bad).includes('{learner:vok}') && text(bad).includes('{m:dragi}') && text(bad).includes('{m:…|f:…}'), text(bad))
  check('… (the tutor\'s villager removed again)', !(await client.callTool({ name: 'remove_villager', arguments: { id: 'n-mia-novak' } })).isError)

  // a turn never tests the learner's own gender: its choices read differently for anyone
  const scene = JSON.parse(text(await client.callTool({ name: 'get_scene', arguments: { id: 'smoke-ogenj' } })))
  const turn = (a: string, b: string) => ({ choices: [
    { sl: a, en: 'I am hungry.', ok: true, reply: { sl: 'Dobro.', en: 'Good.' } },
    { sl: b, en: 'I am hungry.', why: 'Lačen, lačna: who is hungry.', reply: { sl: 'Hm?', en: 'Hm?' } },
  ] })
  const dialog = (t: object) => ({ id: 'juha', lines: [{ who: scene.people[0].id, sl: 'Si {m:lačen|f:lačna}, {learner}?', en: 'Are you hungry, {learner}?' }, t, { who: scene.people[0].id, sl: 'Izvoli.', en: 'Here you are.' }] })
  const trap = await client.callTool({ name: 'publish_scene', arguments: { scene: { ...scene, id: 'smoke-learner', dialogs: [dialog(turn('Sem {m:lačen|f:lačna}.', 'Sem lačna.'))], variants: [], happenings: scene.happenings.map((h: any) => ({ ...h, dialog: 'juha', dialogs: undefined })) } } })
  check('publish_scene refuses a turn whose choices read the same to a learner of one gender (Sem lačna. / Sem lačna.)', trap.isError && text(trap).includes('read the same to a female learner'), text(trap))
  const fine = await client.callTool({ name: 'publish_scene', arguments: { scene: { ...scene, id: 'smoke-learner', dialogs: [dialog(turn('Sem {m:lačen|f:lačna}.', 'Sem {m:žejen|f:žejna}.'))], variants: [], happenings: scene.happenings.map((h: any) => ({ ...h, dialog: 'juha', dialogs: undefined })) } } })
  const served = await asLearner({ name: 'Ana', gender: 'female' }, async () => await (await fetch(`${base}/scenes/smoke-learner`, { headers: auth })).json())
  check('… takes one that differs for both, and GET /scenes/:id says it to the learner', !fine.isError && served.dialogs[0].lines[0].sl === 'Si lačna, Ana?' && served.dialogs[0].lines[1].choices[1].sl === 'Sem žejna.', { fine: text(fine), served: served.dialogs?.[0] })
  check('… (the scene removed again)', !(await client.callTool({ name: 'remove_scene', arguments: { id: 'smoke-learner' } })).isError)
}
