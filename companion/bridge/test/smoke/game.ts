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

  // The open requests: each one's module, how often Jan tried it and their best try against its pass mark (8 of 12), and the
  // task a drill helps (quest.helps); a done request and the fields a request doesn't have yet are left out.
  const micka = { giver: 'Babica Micka', emoji: '👵', story: 'Micka forgot the kitchen words.', skill: 'FOOD', reward: { FOOD: 40 }, source: 'TUTOR' }
  const quests = [
    { ...micka, id: 'q-task', title: "Micka's kitchen", moduleId: 'micka-kitchen', tries: 3, best: 7, bestOf: 12, since: '2026-09-28', missed: ['predlogi-kraja'] },
    { ...micka, id: 'q-drill', title: 'The pots first', moduleId: 'micka-pots', helps: 'micka-kitchen', since: '2026-09-30' },
    { ...micka, id: 'q-old', title: 'Old soup', moduleId: 'micka-soup', done: true, tries: 1, best: 5, bestOf: 5 },
    { id: 'q-2026-09-30-1', giver: 'Kovač Tone', emoji: '⚒️', title: 'Nails', story: 'Tone needs nails.', skill: 'STONE', reward: { STONE: 20 }, source: 'LOCAL' },
  ]
  const asked = await put(rev + 2, { age: 'ZASELEK', buildings: [], quests })
  const open = JSON.parse(text(await client.callTool({ name: 'get_village', arguments: {} }))).open_quests
  const [task, drill, local] = open ?? []
  check(
    "get_village shows each open request's module, tries, best try, pass mark and the task a drill helps",
    asked.ok && open?.length === 3 &&
      task.module === 'micka-kitchen' && task.tries === 3 && task.best === '7/12' && task.pass_mark === 8 && task.helps === undefined &&
      JSON.stringify(task.missed) === '["predlogi-kraja"]' && drill.missed === undefined &&
      drill.module === 'micka-pots' && drill.helps === 'micka-kitchen' && drill.tries === undefined && drill.best === undefined && drill.pass_mark === undefined &&
      JSON.stringify(local) === JSON.stringify({ giver: 'Kovač Tone', title: 'Nails', skill: 'STONE', source: 'LOCAL' }),
    open,
  )
  writeFileSync(gamePath, saved)
}
