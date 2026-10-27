// Smoke checks: the village game state.
import { readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { auth, base, check, client, dataDir, sendChunked } from './harness'

const text = (r: any) => (r.content ?? []).map((c: any) => c.text ?? '').join('\n')

export default async function game() {
  // village game state
  check('GET /game is 404 before the first save', (await fetch(`${base}/game`, { headers: auth })).status === 404)
  const put = (rev: number, state: object) => fetch(`${base}/game`, { method: 'PUT', headers: auth, body: JSON.stringify({ rev, state }) })
  const first = await put(0, { age: 'TABOR', resources: { FOOD: 12 }, buildings: [{ id: 'b1', type: 'TENT', plot: 0 }], log: [] })
  check('PUT /game creates rev 1', first.ok && (await first.json()).rev === 1)
  const stale = await put(0, { age: 'OGENJ' })
  const staleBody = await stale.json()
  check('stale PUT /game is a 409 with the current copy', stale.status === 409 && staleBody.rev === 1 && staleBody.state.age === 'TABOR')
  check('bad PUT /game body is a 400', (await fetch(`${base}/game`, { method: 'PUT', headers: auth, body: '{nope' })).status === 400)
  const village = await client.callTool({ name: 'get_village', arguments: {} })
  check('get_village summarises the village', JSON.stringify(village).includes('TABOR') && JSON.stringify(village).includes('TENT'))
  // the hearths (GAME.md "Hearths"): the camp's campfire alone, all lit, its 3 🪵 a day, no wood in store (0 days)
  const camp = JSON.parse(text(village)).hearths
  check("get_village's hearths: the camp's campfire, lit, 3 🪵 a day", camp?.centre === 'campfire' && camp.lit === 1 && camp.all === 1 && camp.wood_a_day === 3 && camp.days_of_wood === 0, camp)

  const big = await sendChunked(`${base}/game`, 'PUT', auth, 600 * 1024)
  check('a chunked PUT /game over 512 KB is a 413', big.status === 413 || big.status === 0, big)
  // An internal error (here: a corrupt game.json) answers a bare 500, never Bun's page with the stack.
  const gamePath = join(dataDir, 'app/game.json')
  const saved = readFileSync(gamePath, 'utf8')
  writeFileSync(gamePath, '{corrupt')
  const broken = await fetch(`${base}/game`, { headers: auth })
  const brokenText = await broken.text()
  check('an internal error is a bare 500 without stack or source', broken.status === 500 && brokenText === '{"error":"internal error"}' && broken.headers.get('content-type')?.startsWith('application/json'), brokenText.slice(0, 200))
  writeFileSync(gamePath, saved)

  // The land a village was founded on stays when an app from before generated land leaves it out of its state (GAME.md,
  // "The land"); the tutor's summary names it. The village as it was afterwards.
  const rev = (JSON.parse(saved) as { rev: number }).rev
  const founded = await put(rev, { age: 'TABOR', buildings: [], land: { kind: 'hills', seed: -5 } })
  const older = await put(rev + 1, { age: 'ZASELEK', buildings: [] })
  const kept = await (await fetch(`${base}/game`, { headers: auth })).json()
  check("PUT /game keeps the village's land when an older app's state leaves it out", founded.ok && older.ok && kept.rev === rev + 2 && kept.state.age === 'ZASELEK' && kept.state.land?.kind === 'hills' && kept.state.land.seed === -5, kept)
  const summary = JSON.stringify(await client.callTool({ name: 'get_village', arguments: {} }))
  check('get_village names the land the village stands on', summary.includes('\\"land\\": \\"hills\\"'), summary.slice(0, 300))
  // the numbers module (companion/README.md "Numbers"): an app from before it leaves its numbers out, which stay; the tutor's
  // summary says where the learner is once the unit stevila's intro is done (the ranges open, the newest's answers, the
  // numbers missed coming back), nothing before
  const numbers = { v: 1, opened: { tens: '2026-10-18' }, last: { tens: '1101111' }, again: [{ n: 27, mode: 'write', due: '2026-10-19', box: 0 }, { n: 530, mode: 'time_hear', due: '2026-10-20', box: 1 }], runs: 4, day: '2026-10-19' }
  const course = { v: 1, units: { 'sl/stevila': { intro: 6, introDone: true } } }
  const hearths = { on: '2026-10-20', out: [], since: '2026-10-20' }
  const withNumbers = await put(rev + 2, { age: 'ZASELEK', buildings: [], course, numbers, hearths })
  const olderApp = await put(rev + 3, { age: 'ZASELEK', buildings: [], course })
  const keptNumbers = await (await fetch(`${base}/game`, { headers: auth })).json()
  check("PUT /game keeps the learner's numbers when an older app's state leaves them out", withNumbers.ok && olderApp.ok && keptNumbers.state.numbers?.runs === 4 && keptNumbers.state.numbers?.opened?.tens === '2026-10-18', keptNumbers.state.numbers)
  // … and the village's hearths (GAME.md "Hearths": the first day it kept them, which piles count as given)
  check("PUT /game keeps the village's hearths when an older app's state leaves them out", keptNumbers.state.hearths?.since === '2026-10-20', keptNumbers.state.hearths)
  const nv = JSON.parse(text(await client.callTool({ name: 'get_village', arguments: {} }))).numbers
  check(
    "get_village shows the numbers: the ranges open, the newest one's last answers, the numbers missed as asked",
    JSON.stringify(nv?.ranges_open) === '["0–10","11–20","30–90"]' && nv?.newest?.range === '30–90' && nv.newest.right === 6 && nv.newest.of === 7 &&
      JSON.stringify(nv.missed) === '[{"number":"27","mode":"write","due":"2026-10-19"},{"number":"5:30","mode":"time_hear","due":"2026-10-20"}]' && nv.runs === 4,
    nv,
  )
  await put(rev + 4, { age: 'ZASELEK', buildings: [], course: { v: 1, units: {} }, numbers })
  const before = JSON.parse(text(await client.callTool({ name: 'get_village', arguments: {} })))
  check('get_village has no numbers before the unit stevila\'s intro', before.numbers === undefined, before.numbers)
  // the land as it was for the checks below
  const back = await put(rev + 5, { age: 'ZASELEK', buildings: [], land: { kind: 'hills', seed: -5 } })
  check('… the village back as it was', back.ok)

  // the finished projects whose landmark is down for the season (the maypole after May): the tutor doesn't send the learner to it
  const seasonal = await put(rev + 6, { age: 'ZASELEK', buildings: [], projects: { mlaj: 5 }, landmarksDown: ['mlaj'] })
  const downNow = JSON.parse(text(await client.callTool({ name: 'get_village', arguments: {} })))
  check("get_village says which finished projects' landmarks are down for the season", seasonal.ok && JSON.stringify(downNow.landmarks_down) === '["mlaj"]' && downNow.projects.mlaj === 5, downNow.landmarks_down)

  // The open requests: each one's module, how often Jan tried it and their best try against its pass mark (8 of 12), and the
  // task a drill helps (quest.helps); a done request and the fields a request doesn't have yet are left out.
  const micka = { giver: 'Babica Micka', emoji: '👵', story: 'Micka forgot the kitchen words.', skill: 'FOOD', reward: { FOOD: 40 }, source: 'TUTOR' }
  const quests = [
    { ...micka, id: 'q-task', title: "Micka's kitchen", moduleId: 'micka-kitchen', tries: 3, best: 7, bestOf: 12, since: '2026-09-28', missed: ['predlogi-kraja'] },
    { ...micka, id: 'q-drill', title: 'The pots first', moduleId: 'micka-pots', helps: 'micka-kitchen', since: '2026-09-30' },
    { ...micka, id: 'q-old', title: 'Old soup', moduleId: 'micka-soup', done: true, tries: 1, best: 5, bestOf: 5 },
    { id: 'q-2026-09-30-1', giver: 'Kovač Tone', emoji: '⚒️', title: 'Nails', story: 'Tone needs nails.', skill: 'STONE', reward: { STONE: 20 }, source: 'LOCAL' },
    // a drill for the soup Jan passed already (by its module), and one for it by the request's id: optional practice now
    { ...micka, id: 'q-salt', title: 'Salt first', moduleId: 'micka-salt', helps: 'micka-soup' },
    { ...micka, id: 'q-pepper', title: 'Pepper first', moduleId: 'micka-pepper', helps: 'q-old' },
  ]
  const asked = await put(rev + 7, { age: 'ZASELEK', buildings: [], quests })
  const open = JSON.parse(text(await client.callTool({ name: 'get_village', arguments: {} }))).open_quests
  const [task, drill, local, salt, pepper] = open ?? []
  check(
    "get_village shows each open request's module, tries, best try, pass mark and the task a drill helps",
    asked.ok && open?.length === 5 &&
      task.module === 'micka-kitchen' && task.tries === 3 && task.best === '7/12' && task.pass_mark === 8 && task.helps === undefined &&
      JSON.stringify(task.missed) === '["predlogi-kraja"]' && drill.missed === undefined &&
      drill.module === 'micka-pots' && drill.helps === 'micka-kitchen' && drill.tries === undefined && drill.best === undefined && drill.pass_mark === undefined &&
      drill.task_passed === undefined && task.task_passed === undefined &&
      JSON.stringify(local) === JSON.stringify({ giver: 'Kovač Tone', title: 'Nails', skill: 'STONE', source: 'LOCAL' }),
    open,
  )
  check("get_village says when the task a drill helps is passed already (task_passed: the drill is practice now)", salt?.task_passed === true && pepper?.task_passed === true, open)
  // the app's window of requests (GAME.md "A window of requests"): the ones the map shows today (on_map), one tried below the
  // mark coming back the next morning (back), who rests after a run of theirs (resting); what is over is left out
  const now = Date.now()
  const pace = {
    up: [{ id: 'q-task', at: now }, { id: 'q-drill', at: now - 2 * 86_400_000 }],
    rests: { 'Kovač Tone': { at: now - 600_000, until: now + 3 * 3_600_000, passed: false }, 'Babica Micka': { at: now - 9 * 3_600_000, until: now - 3_600_000 } },
    back: { 'q-2026-09-30-1': now + 12 * 3_600_000, 'q-task': now - 1000 },
    roll: 1,
    rollAt: now - 600_000,
  }
  const paced = await put(rev + 8, { age: 'ZASELEK', buildings: [], quests, pace })
  const v2 = JSON.parse(text(await client.callTool({ name: 'get_village', arguments: {} })))
  const [t2, d2, l2] = v2.open_quests ?? []
  check(
    'get_village says which requests the map shows today (on_map), when one tried below the mark comes back (back), and who rests (resting)',
    paced.ok && t2?.on_map === true && d2?.on_map === undefined && /^\d{4}-\d\d-\d\d \d\d:\d\d$/.test(l2?.back) && t2?.back === undefined &&
      Object.keys(v2.resting ?? {}).join() === 'Kovač Tone' && /^\d\d:\d\d$/.test(v2.resting?.['Kovač Tone']),
    v2,
  )
  writeFileSync(gamePath, saved)
}
