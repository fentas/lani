// Smoke checks: role-play scenarios and the roleplay protocol.
import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { ScenarioStore, talkLevelOffered, validateScenario } from '../../src/scenarios'
import { talksOffered } from '../../src/scenes'
import { postOffice, reference } from './fixtures'
import { curatedTalksOf } from './village-talks'
import { auth, base, channelEvents, check, client, dataDir, guideOf, tempDir } from './harness'

export default async function scenarios() {
  // role-play scenarios: the repo's curated ones are served as they are
  const curatedScenarios = resolve(import.meta.dir, '../../../scenarios')
  const scenarioFiles = readdirSync(curatedScenarios).filter(f => f.endsWith('.json'))
  const levelOf = (f: string) => JSON.parse(readFileSync(join(curatedScenarios, f), 'utf8')).level
  const byLevel = (lv: string) => scenarioFiles.filter(f => levelOf(f) === lv)
  check('21 curated scenarios: Jan\'s six at A1, three at A2, six at B1, six at B2', scenarioFiles.length === 21 && byLevel('A1').length === 6 && byLevel('A2').length === 3 && byLevel('B1').length === 6 && byLevel('B2').length === 6, scenarioFiles.map(f => [f, levelOf(f)]))
  // the village's packs (companion/packs and the Primorska culture pack's): what a Slovene talk's packs must be
  const slPacks = new Set(
    [resolve(curatedScenarios, '../packs'), resolve(curatedScenarios, '../cultures/primorska/packs')].flatMap(d => readdirSync(d).filter(f => f.endsWith('.json')).map(f => JSON.parse(readFileSync(join(d, f), 'utf8')).id)),
  )
  for (const f of scenarioFiles) {
    const raw = JSON.parse(readFileSync(join(curatedScenarios, f), 'utf8'))
    const v = validateScenario(raw)
    check(`curated scenario validates: ${f}`, v.ok && `${v.scenario.id}.json` === f && v.scenario.language === 'sl', v)
    // Jan's six read in a German or Italian base too: every text per base (en, de, it), the title and who per language
    const texts = [raw.setting, raw.opener_en, ...raw.goals, ...raw.vocabulary_hints.map((h: any) => h.en)]
    check(`curated scenario has its texts in en, de and it, its title and who in sl too: ${f}`, texts.every(t => t?.en && t.de && t.it) && [raw.title, raw.who].every(t => t?.sl && t.en && t.de && t.it) && /machine-written/.test(raw.review), texts.filter(t => !(t?.en && t.de && t.it)))
    // a talk above A1: a grown-up's, its goals in Slovene too (read in the pair), the village's packs, wholly machine-written
    if (raw.level !== 'A1')
      check(
        `a ${raw.level} talk is a grown-up's, its goals in sl · the base, its packs the village's: ${f}`,
        raw.audience === 'adult' && raw.goals.every((g: any) => g.sl) && raw.packs?.length > 0 && raw.packs.every((p: string) => slPacks.has(p)) && /^sl, en, de, it: machine-written/.test(raw.review),
        { packs: raw.packs?.filter((p: string) => !slPacks.has(p)), goals: raw.goals.filter((g: any) => !g.sl) },
      )
  }
  // the German village's role-plays (companion/scenarios/de) and the English one's (companion/scenarios/en): for a grown-up,
  // every text in each base the language has, the title, goals and who per language, machine-written
  for (const [language, bases] of [['de', ['sl', 'en', 'it']], ['en', ['sl', 'de', 'it', 'en']]] as const) {
    const talks = curatedTalksOf(language, [...bases])
    // five at A1, three at A2, six each at B1 and B2 (docs/plans/05-built/talks-de.md, talks-en.md)
    const levels = [...new Set(talks.map(t => t.raw.level as string))].sort().map(lv => `${lv} ${talks.filter(t => t.raw.level === lv).length}`).join(', ')
    const want = 'A1 5, A2 3, B1 6, B2 6'
    check(`${language} scenarios by level: ${want}`, levels === want, levels)
    for (const { f, raw, missing } of talks) {
      const v = validateScenario(raw)
      check(
        `${language} scenario validates, for a grown-up, its texts in ${[language, ...bases.filter(b => b !== language)].join(' · ')}: ${language}/${f}`,
        v.ok && `${v.scenario.id}.json` === f && raw.language === language && raw.audience === 'adult' && /machine-written/.test(raw.review) && missing.length === 0 && raw.packs?.length > 0,
        v.ok ? missing : v.errors,
      )
    }
  }
  // Teo's Italian ones (companion/scenarios/it): A1 to B1 for a child; B2 for an older learner with nothing a child
  // mayn't hear (no audience: a child's profile still adds the child brief); every text per language with its English
  const italian = join(curatedScenarios, 'it')
  const italianFiles = readdirSync(italian).filter(f => f.endsWith('.json'))
  const italianLevels = italianFiles.map(f => JSON.parse(readFileSync(join(italian, f), 'utf8')).level)
  check('21 Italian scenarios: 9 at A1 and A2, 6 at B1, 6 at B2', italianFiles.length === 21 && italianLevels.filter(l => l === 'B1').length === 6 && italianLevels.filter(l => l === 'B2').length === 6, italianLevels)
  for (const f of italianFiles) {
    const raw = JSON.parse(readFileSync(join(italian, f), 'utf8'))
    const v = validateScenario(raw)
    const texts = [raw.title, raw.who, raw.setting, raw.opener_en, ...raw.goals, ...raw.vocabulary_hints.map((h: any) => h.en)]
    const audience = raw.level === 'B2' ? raw.audience === undefined : raw.audience === 'child'
    check(
      `Italian scenario validates, ${raw.level === 'B2' ? 'for an older learner and fit for a child' : 'for a child'}, in it · de · sl · en: it/${f}`,
      v.ok && `${v.scenario.id}.json` === f && raw.language === 'it' && audience && /machine-written/.test(raw.review) && texts.every(t => t && t.de && t.sl && t.en) && [raw.title, raw.who, ...raw.goals].every(t => t.it) && raw.packs?.length > 0 && /nothing personal/.test(raw.role),
      v.ok ? texts.filter(t => !(t?.de && t.sl && t.en)) : v.errors,
    )
  }
  const scenarioExample = [...reference.matchAll(/```json\n([\s\S]*?)\n```/g)].map(m => m[1]).find(s => s.includes('lani.scenario/v0'))
  check('module-spec.md scenario example validates', scenarioExample && validateScenario(JSON.parse(scenarioExample)).ok, scenarioExample)
  const scList = await (await fetch(`${base}/scenarios`, { headers: auth })).json()
  // the smoke's learner has no level yet (A1): the A1 and A2 talks, never a B1 or B2 one
  check(
    "GET /scenarios lists the A1 learner's curated scenarios, the A2 ones with them, none at B1 or B2",
    [...byLevel('A1'), ...byLevel('A2')].every(f => scList.some((s: any) => `${s.id}.json` === f && s.source === 'curated')) && !scList.some((s: any) => s.level === 'B1' || s.level === 'B2'),
    scList.map((s: any) => [s.id, s.level]),
  )
  check('GET /scenarios/:id: a B2 talk is not found for an A1 learner', (await fetch(`${base}/scenarios/pri-zupanji`, { headers: auth })).status === 404)
  {
    // at B1 the B1 and B2 talks too, their title, goals and who in the pair, the setting and meanings in the base
    const profile = join(dataDir, 'learner-profile.json')
    const before = readFileSync(profile, 'utf8')
    const p = JSON.parse(before)
    writeFileSync(profile, JSON.stringify({ ...p, learner: { ...p.learner, current_level: 'B1' } }, null, 2))
    try {
      const atB1 = await (await fetch(`${base}/scenarios`, { headers: auth })).json()
      check('a B1 learner is offered all 21, the B1 and B2 talks with them', scenarioFiles.every(f => atB1.some((s: any) => `${s.id}.json` === f)), atB1.map((s: any) => [s.id, s.level]))
      const mayor = await (await fetch(`${base}/scenarios/pri-zupanji`, { headers: auth })).json()
      check(
        "a B2 talk served in Jan's pair: Pri županji · At the mayor's office, a goal in sl · en for a man, the setting in English",
        mayor.title === "Pri županji · At the mayor's office" && mayor.who === 'Županja · The mayor' && mayor.level === 'B2' && mayor.goals[0] === 'Predstavi se in na kratko povej, zakaj si prišel · Introduce yourself and say briefly why you have come' && mayor.setting.startsWith('Since the bus stop was moved') && mayor.vocabulary_hints.some((h: any) => h.sl === 'Otroci hodijo ob cesti, kjer ni pločnika.'),
        mayor,
      )
      check('the talks are listed by level, easiest first, not by file name', atB1.map((s: any) => s.level).join() === [...atB1.map((s: any) => s.level)].sort().join() && atB1[0].level === 'A1' && atB1.at(-1).level === 'B2', atB1.map((s: any) => [s.id, s.level]))
      const marko = atB1.find((s: any) => s.id === 'vino-za-praznovanje')
      check("Marko's B1 talk names the cast's Vinar Marko, who plays it", marko?.role.startsWith('Vinar Marko,') && marko.level === 'B1', marko)
      const osmica = atB1.find((s: any) => s.id === 'na-osmici')
      check("Marko's A2 talk, at the osmica his reading tells of, names him too", osmica?.role.startsWith('Vinar Marko,') && osmica.level === 'A2' && osmica.title === 'Na osmici · At the osmica', osmica)
      const linked = { dialogs: [{ id: 'vino', talk: 'vino-za-praznovanje', lines: [] }] } as any
      check("a scene's \"keep talking\" into a B1 talk only for a learner it is offered to", talksOffered(linked, new Set(atB1.map((s: any) => s.id))) === linked && !('talk' in talksOffered(linked, new Set(scList.map((s: any) => s.id))).dialogs[0]))
    } finally {
      writeFileSync(profile, before)
    }
  }
  check("GET /scenarios lists none of the Italian village's (Jan's village speaks Slovene)", !scList.some((s: any) => ['al-mercato', 'dal-fornaio', 'in-gelateria'].includes(s.id)) && scList.every((s: any) => s.language === 'sl'), scList.map((s: any) => [s.id, s.language]))
  check("an adult is offered the grown-up's role-play (the inn), its texts as they were", scList.some((s: any) => s.id === 'v-gostilni' && s.audience === 'adult' && s.title === 'V gostilni · At the inn' && s.goals[0] === 'Order a drink' && s.setting.startsWith('Friday evening') && s.who === 'Natakar · The waiter'), scList.find((s: any) => s.id === 'v-gostilni'))
  check("GET /scenarios lists none of the German or English villages' (Jan's village speaks Slovene)", !scList.some((s: any) => ['beim-baecker', 'im-gasthaus', 'at-the-farm-shop', 'in-the-pub'].includes(s.id)), scList.map((s: any) => s.id))
  // a scene's "keep talking" leads only into a role-play the learner is offered: a child's scene never into the inn
  const talking = { dialogs: [{ id: 'kosilo', talk: 'nedeljsko-kosilo', lines: [] }, { id: 'vino', talk: 'v-gostilni', lines: [] }] } as any
  const forChild = talksOffered(talking, new Set(['nedeljsko-kosilo']))
  check("a child's scene leads into no grown-up's role-play; a grown-up's into both", forChild.dialogs[0].talk === 'nedeljsko-kosilo' && !('talk' in forChild.dialogs[1]) && talksOffered(talking, new Set(['nedeljsko-kosilo', 'v-gostilni'])) === talking, forChild)
  // a talk by level (talkLevelOffered): at the learner's level or below, or one above; a tutor's at any level
  check(
    'a talk is offered at the learner\'s level, below it and one above: A1 meets A1 and A2, never B1 or B2; B1 meets B2',
    talkLevelOffered('A1', 'A1') && talkLevelOffered('A2', 'A1') && !talkLevelOffered('B1', 'A1') && !talkLevelOffered('B2', 'A1') && talkLevelOffered('B1', 'A2') && !talkLevelOffered('B2', 'A2') &&
      talkLevelOffered('B2', 'B1') && talkLevelOffered('A1', 'B2') && !talkLevelOffered('B2', 'nope') && talkLevelOffered('A2', 'nope') && !talkLevelOffered('B1', '?'),
  )
  {
    const curated = tempDir('lani-smoke-levels-')
    const tutor = join(tempDir('lani-smoke-levels-tutor-'), 'scenarios')
    mkdirSync(tutor, { recursive: true })
    for (const [id, level] of [['na-posti-a1', 'A1'], ['na-posti-a2', 'A2'], ['na-posti-b1', 'B1'], ['na-posti-b2', 'B2']]) writeFileSync(join(curated, `${id}.json`), JSON.stringify({ ...postOffice, id, level }))
    writeFileSync(join(tutor, 'na-posti-tutor.json'), JSON.stringify({ ...postOffice, id: 'na-posti-tutor', level: 'B2' }))
    const store = new ScenarioStore(curated, tutor)
    // a file name that sorts before the level: a-b1 comes first by name, after the A1 and A2 talks in the list
    writeFileSync(join(curated, 'a-b1.json'), JSON.stringify({ ...postOffice, id: 'a-b1', level: 'B1' }))
    check('a store lists its talks by level, a tutor\'s among them, a file name never first for it', store.offered(false, 'A2').map(s => s.id).join(' ') === 'na-posti-a1 na-posti-a2 a-b1 na-posti-b1 na-posti-tutor', store.offered(false, 'A2').map(s => s.id))
    rmSync(join(curated, 'a-b1.json'))
    const ids = (level?: string) => store.offered(false, level).map(s => s.id).join(' ')
    check(
      'an A1 learner is offered the A1 and A2 talks and the tutor\'s B2 one, a B1 learner all, no level every one',
      ids('A1') === 'na-posti-a1 na-posti-a2 na-posti-tutor' && ids('A2') === 'na-posti-a1 na-posti-a2 na-posti-b1 na-posti-tutor' && ids('B1').split(' ').length === 5 && ids().split(' ').length === 5,
      { A1: ids('A1'), A2: ids('A2'), B1: ids('B1') },
    )
  }
  const lunch = await (await fetch(`${base}/scenarios/nedeljsko-kosilo`, { headers: auth })).json()
  check('GET /scenarios/:id serves the scenario', lunch.goals?.length >= 3 && lunch.opener_sl && lunch.vocabulary_hints?.length > 0, lunch)
  check('GET /scenarios/:id 404s', (await fetch(`${base}/scenarios/nope`, { headers: auth })).status === 404)

  for (const [label, bad] of [
    ['scenario with 2 goals rejected', { ...postOffice, goals: ['a', 'b'] }],
    ['scenario without opener rejected', { ...postOffice, opener_sl: undefined }],
    ['scenario with a bad id rejected', { ...postOffice, id: 'Na Posti' }],
    ['scenario with an empty hint rejected', { ...postOffice, vocabulary_hints: [{ sl: '', en: 'x' }] }],
  ] as const) {
    const r = await client.callTool({ name: 'publish_scenario', arguments: { scenario: bad } })
    check(label, r.isError, r)
  }
  const scItalian = await client.callTool({ name: 'publish_scenario', arguments: { scenario: { ...postOffice, id: 'alla-posta', language: 'it' } } })
  check('publish_scenario refuses a role-play in another language than the village\'s', scItalian.isError && /this village speaks/.test(JSON.stringify(scItalian)) && !existsSync(join(dataDir, 'app/scenarios/alla-posta.json')), scItalian)
  const scNoPack = await client.callTool({ name: 'publish_scenario', arguments: { scenario: { ...postOffice, id: 'na-posti-2', packs: ['nope-pack'] } } })
  check('publish_scenario refuses a pack the village lacks', scNoPack.isError && /nope-pack/.test(JSON.stringify(scNoPack)), scNoPack)
  const scClash = await client.callTool({ name: 'publish_scenario', arguments: { scenario: { ...postOffice, id: 'v-pekarni' } } })
  check('publish_scenario refuses a curated id', scClash.isError, scClash)
  const scPub = await client.callTool({ name: 'publish_scenario', arguments: { scenario: postOffice, note: 'New: the post office' } })
  check('publish_scenario publishes, in the village\'s language', !scPub.isError && existsSync(join(dataDir, 'app/scenarios/na-posti.json')) && JSON.parse(readFileSync(join(dataDir, 'app/scenarios/na-posti.json'), 'utf8')).language === 'sl', scPub)
  const scServed = await (await fetch(`${base}/scenarios/na-posti`, { headers: auth })).json()
  check('published scenario served with defaults', scServed.source === 'tutor' && scServed.vocabulary_hints?.length === 0, scServed)
  await Bun.sleep(100)
  const scEvents = await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()
  check('scenario_published event', scEvents.some((b: any) => b.event.type === 'scenario_published' && b.event.id === 'na-posti'))

  // the roleplay protocol: conversation ids with dashes, and the scenario brief attached for the tutor
  const rp = await fetch(`${base}/message`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ kind: 'roleplay', conversation_id: 'rp-0f8b8e2c-5a1d-4d6b-9b8e-2f3c4d5e6f70', text: 'Dober dan!', data: { scenario_id: 'nedeljsko-kosilo', turn: 1, heard: 'Dober dan!', alternatives: ['dober dan'] } }),
  })
  check('roleplay message accepted', rp.ok, await rp.clone().text())
  await Bun.sleep(100)
  const rpEv = channelEvents.findLast(n => n.params?.meta?.kind === 'roleplay')
  check('roleplay reaches Claude with the scenario brief', rpEv?.params?.meta?.conversation_id?.startsWith('rp-') && rpEv.params.content.includes('Tašča Marija') && rpEv.params.content.includes('"goals"') && rpEv.params.content.includes('"language": "sl"') && !rpEv.params.content.includes('"child"'), rpEv)
  const rpB1 = await fetch(`${base}/message`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ kind: 'roleplay', conversation_id: 'rp-1f8b8e2c-5a1d-4d6b-9b8e-2f3c4d5e6f71', text: 'Dobro jutro!', data: { scenario_id: 'po-noci-burje', turn: 1, heard: 'Dobro jutro!' } }),
  })
  await Bun.sleep(100)
  const rpB1Ev = channelEvents.findLast(n => n.params?.meta?.kind === 'roleplay')
  check("a B1 talk's turn reaches Claude with its brief: the bora's commander, level B1, its packs", rpB1.ok && rpB1Ev?.params?.content.includes('Poveljnik Boris') && rpB1Ev.params.content.includes('"level": "B1"') && rpB1Ev.params.content.includes('"v-gasilskem-domu"'), rpB1Ev?.params?.content)
  // the character's line comes with the face it is said with (companion/SCENES.md, "Faces"): passed on to the app as written
  const rpId = 'rp-0f8b8e2c-5a1d-4d6b-9b8e-2f3c4d5e6f70'
  const said = { sl: 'Ha ha, prav imaš!', en: "Ha ha, you're right!", face: 'laugh', goals_done: [0] }
  const sent = await client.callTool({ name: 'reply', arguments: { conversation_id: rpId, text: said.sl, data: said } })
  await Bun.sleep(100)
  const got = ((await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as any[]).findLast(b => b.event.type === 'reply' && b.event.conversation_id === rpId)
  check("a role-play reply's face reaches the app in its data, with the line", !sent.isError && got?.event.data?.face === 'laugh' && got.event.data.sl === said.sl && JSON.stringify(got.event.data.goals_done) === '[0]', got)
  check("the guide asks for the face of the character's line", (await guideOf()).includes('"face"?: "<the face of your line, one of happy, laugh,'))
  const rpEnd = await fetch(`${base}/message`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ kind: 'roleplay_end', conversation_id: 'rp-0f8b8e2c-5a1d-4d6b-9b8e-2f3c4d5e6f70', text: 'done', data: { scenario_id: 'nedeljsko-kosilo', transcript: [] } }),
  })
  check('roleplay_end accepted', rpEnd.ok)
  // "Ask your tutor" under the debrief: a chat on the debrief's own conversation, with the role-play as context
  const debriefThread = 'rp-0f8b8e2c-5a1d-4d6b-9b8e-2f3c4d5e6f70-debrief'
  const ask = await fetch(`${base}/message`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({
      kind: 'chat',
      conversation_id: debriefThread,
      text: 'Zakaj "kosilo" in ne "kosila"?',
      data: { about_roleplay: { scenario_id: 'nedeljsko-kosilo', title: 'Nedeljsko kosilo', conversation_id: 'rp-0f8b8e2c-5a1d-4d6b-9b8e-2f3c4d5e6f70', debrief: 'Bravo!' } },
    }),
  })
  check('a question under the debrief is accepted', ask.ok, await ask.clone().text())
  await Bun.sleep(100)
  const askEv = channelEvents.findLast(n => n.params?.meta?.kind === 'chat')
  check(
    'it reaches Claude as a chat on the debrief thread, with the role-play',
    askEv?.params?.meta?.conversation_id === debriefThread && askEv.params.content.includes('"about_roleplay"') && askEv.params.content.includes('nedeljsko-kosilo'),
    askEv,
  )
  check('the guide says a chat with about_roleplay continues the debrief', JSON.stringify(await guideOf()).includes('data.about_roleplay'))
  check('bad conversation id is a 400', (await fetch(`${base}/message`, { method: 'POST', headers: auth, body: JSON.stringify({ kind: 'roleplay', conversation_id: 'rp/../x', text: 'x' }) })).status === 400)
}
