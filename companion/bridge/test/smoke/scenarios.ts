// Smoke checks: role-play scenarios and the roleplay protocol.
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { validateScenario } from '../../src/scenarios'
import { postOffice, reference } from './fixtures'
import { auth, base, channelEvents, check, client, dataDir, guideOf } from './harness'

export default async function scenarios() {
  // role-play scenarios: the repo's curated ones are served as they are
  const curatedScenarios = resolve(import.meta.dir, '../../../scenarios')
  const scenarioFiles = readdirSync(curatedScenarios).filter(f => f.endsWith('.json'))
  check('6 curated scenarios', scenarioFiles.length === 6, scenarioFiles)
  for (const f of scenarioFiles) {
    const v = validateScenario(JSON.parse(readFileSync(join(curatedScenarios, f), 'utf8')))
    check(`curated scenario validates: ${f}`, v.ok && `${v.scenario.id}.json` === f, v)
  }
  const scenarioExample = [...reference.matchAll(/```json\n([\s\S]*?)\n```/g)].map(m => m[1]).find(s => s.includes('lani.scenario/v0'))
  check('module-spec.md scenario example validates', scenarioExample && validateScenario(JSON.parse(scenarioExample)).ok, scenarioExample)
  const scList = await (await fetch(`${base}/scenarios`, { headers: auth })).json()
  check('GET /scenarios lists the curated scenarios', scenarioFiles.every(f => scList.some((s: any) => `${s.id}.json` === f && s.source === 'curated')), scList.map((s: any) => s.id))
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
  const scClash = await client.callTool({ name: 'publish_scenario', arguments: { scenario: { ...postOffice, id: 'v-pekarni' } } })
  check('publish_scenario refuses a curated id', scClash.isError, scClash)
  const scPub = await client.callTool({ name: 'publish_scenario', arguments: { scenario: postOffice, note: 'New: the post office' } })
  check('publish_scenario publishes', !scPub.isError && existsSync(join(dataDir, 'app/scenarios/na-posti.json')), scPub)
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
  check('roleplay reaches Claude with the scenario brief', rpEv?.params?.meta?.conversation_id?.startsWith('rp-') && rpEv.params.content.includes('Tašča Marija') && rpEv.params.content.includes('"goals"'), rpEv)
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
