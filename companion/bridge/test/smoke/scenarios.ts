// Smoke checks: role-play scenarios and the roleplay protocol.
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
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
  check('6 curated scenarios', scenarioFiles.length === 6, scenarioFiles)
  for (const f of scenarioFiles) {
    const raw = JSON.parse(readFileSync(join(curatedScenarios, f), 'utf8'))
    const v = validateScenario(raw)
    check(`curated scenario validates: ${f}`, v.ok && `${v.scenario.id}.json` === f && v.scenario.language === 'sl', v)
    // Jan's six read in a German or Italian base too: every text per base (en, de, it), the title and who per language
    const texts = [raw.setting, raw.opener_en, ...raw.goals, ...raw.vocabulary_hints.map((h: any) => h.en)]
    check(`curated scenario has its texts in en, de and it, its title and who in sl too: ${f}`, texts.every(t => t?.en && t.de && t.it) && [raw.title, raw.who].every(t => t?.sl && t.en && t.de && t.it) && /machine-written/.test(raw.review), texts.filter(t => !(t?.en && t.de && t.it)))
  }
  // the German village's role-plays (companion/scenarios/de) and the English one's (companion/scenarios/en): for a grown-up,
  // every text in each base the language has, the title, goals and who per language, machine-written
  for (const [language, bases] of [['de', ['sl', 'en', 'it']], ['en', ['sl', 'de', 'it', 'en']]] as const) {
    const talks = curatedTalksOf(language, [...bases])
    check(`8 ${language} scenarios`, talks.length === 8, talks.map(t => t.f))
    for (const { f, raw, missing } of talks) {
      const v = validateScenario(raw)
      check(
        `${language} scenario validates, for a grown-up, its texts in ${[language, ...bases.filter(b => b !== language)].join(' · ')}: ${language}/${f}`,
        v.ok && `${v.scenario.id}.json` === f && raw.language === language && raw.audience === 'adult' && /machine-written/.test(raw.review) && missing.length === 0 && raw.packs?.length > 0,
        v.ok ? missing : v.errors,
      )
    }
  }
  // Teo's Italian ones (companion/scenarios/it): for a child, every text per language with its English, machine-written
  const italian = join(curatedScenarios, 'it')
  for (const f of readdirSync(italian).filter(f => f.endsWith('.json'))) {
    const raw = JSON.parse(readFileSync(join(italian, f), 'utf8'))
    const v = validateScenario(raw)
    const texts = [raw.title, raw.who, raw.setting, raw.opener_en, ...raw.goals, ...raw.vocabulary_hints.map((h: any) => h.en)]
    check(
      `Italian scenario validates, for a child, in it · de · sl · en: it/${f}`,
      v.ok && `${v.scenario.id}.json` === f && raw.language === 'it' && raw.audience === 'child' && /machine-written/.test(raw.review) && texts.every(t => t && t.de && t.sl && t.en) && [raw.title, raw.who, ...raw.goals].every(t => t.it) && raw.packs?.length > 0 && /asks nothing personal|nothing personal/.test(raw.role),
      v.ok ? texts.filter(t => !(t?.de && t.sl && t.en)) : v.errors,
    )
  }
  const scenarioExample = [...reference.matchAll(/```json\n([\s\S]*?)\n```/g)].map(m => m[1]).find(s => s.includes('lani.scenario/v0'))
  check('module-spec.md scenario example validates', scenarioExample && validateScenario(JSON.parse(scenarioExample)).ok, scenarioExample)
  const scList = await (await fetch(`${base}/scenarios`, { headers: auth })).json()
  check('GET /scenarios lists the curated scenarios', scenarioFiles.every(f => scList.some((s: any) => `${s.id}.json` === f && s.source === 'curated')), scList.map((s: any) => s.id))
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
      talkLevelOffered('B2', 'B1') && talkLevelOffered('A1', 'B2') && !talkLevelOffered('B2', 'nope') && talkLevelOffered('A2', 'nope'),
  )
  {
    const curated = tempDir('lani-smoke-levels-')
    const tutor = join(tempDir('lani-smoke-levels-tutor-'), 'scenarios')
    mkdirSync(tutor, { recursive: true })
    for (const [id, level] of [['na-posti-a1', 'A1'], ['na-posti-a2', 'A2'], ['na-posti-b1', 'B1'], ['na-posti-b2', 'B2']]) writeFileSync(join(curated, `${id}.json`), JSON.stringify({ ...postOffice, id, level }))
    writeFileSync(join(tutor, 'na-posti-tutor.json'), JSON.stringify({ ...postOffice, id: 'na-posti-tutor', level: 'B2' }))
    const store = new ScenarioStore(curated, tutor)
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
