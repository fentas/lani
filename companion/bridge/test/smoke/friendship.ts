// Smoke checks: friendship between towns (src/friendship.ts, src/features/friendship.ts, the market's rates in
// src/features/towns.ts), with the towns section's bridges after the things done on visits: Jan (the harness's,
// primorska), Mia (friuli, one help a day against an event from a town) and Luka (a child's, friuli). The level both
// towns agree on, that one town's records can't raise it, the market's rates by the level, help sent when wolves come
// (once, limited), the shared feast, a move offered and said yes to (both sides the same), and that with a child's town
// the grown-up side says yes. Called by towns.ts while the towns are linked.
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import {
  FRIEND_LEVELS,
  RATES,
  agree,
  cleanMover,
  combine,
  feastOpenUntil,
  friendshipOf,
  levelOf,
  pickMover,
  pointsOf,
  sharedFeast,
  troubleOf,
  zero,
} from '../../src/friendship'
import { chestOf, valueOf } from '../../src/guests'
import { loadBridgeKey } from '../../src/pairing'
import { localDate, signRequest } from '../../src/towns'
import { check, culturesDir, tempDir } from './harness'
import type { Town } from './visits'

const ledgerOf = (t: Town) => join(t.data, 'app/town-friends.json')
const readLedger = (t: Town) => JSON.parse(readFileSync(ledgerOf(t), 'utf8'))
const writeLedger = (t: Town, d: unknown) => writeFileSync(ledgerOf(t), JSON.stringify(d))
const day = (offset: number) => localDate(Date.now() + offset * 86_400_000)

/** Both towns' shared history, as if it had happened: [n] more visit days each way, in both towns' records. */
function history(a: Town, b: Town, n: number) {
  for (const [t, other] of [[a, b], [b, a]] as const) {
    const d = readLedger(t)
    const c = (d.towns[other.key.fingerprint] ??= { gave: {}, got: {} })
    c.gave.visit = (c.gave.visit ?? 0) + n
    c.got.visit = (c.got.visit ?? 0) + n
    writeLedger(t, d)
  }
}

export async function friendship(jan: Town, mia: Town, luka: Town) {
  const miaId = mia.key.fingerprint
  const janId = jan.key.fingerprint
  const rows = async (t: Town) => {
    const r = await fetch(`${t.base}/towns/friendship`, { headers: t.auth })
    return { status: r.status, body: await r.json() }
  }
  const rowOf = async (t: Town, other: Town) => (await rows(t)).body.towns?.find((x: any) => x.id === other.key.fingerprint)
  const post = (from: Town, to: Town, what: string, body: unknown = {}) =>
    fetch(`${from.base}/towns/${to.key.fingerprint}/${what}`, { method: 'POST', headers: from.auth, body: JSON.stringify(body) })
  const answer = async (r: Response | Promise<Response>) => {
    const res = await r
    return { status: res.status, body: await res.json() }
  }

  // --- the level both towns agree on ------------------------------------------------------------------------------------
  const jm = await rowOf(jan, mia)
  const mj = await rowOf(mia, jan)
  check("friendship: GET /towns/friendship: Jan and Mia see the same level and points, each the other's side mirrored",
    jm?.status === 'ok' && mj?.status === 'ok' && jm.level === mj.level && jm.points === mj.points && JSON.stringify(jm.mine) === JSON.stringify(mj.theirs) && JSON.stringify(jm.theirs) === JSON.stringify(mj.mine) && jm.points === combine(pointsOf(jm.mine), pointsOf(jm.theirs)),
    { jm: jm && { level: jm.level, points: jm.points, mine: jm.mine, theirs: jm.theirs }, mj: mj && { level: mj.level, points: mj.points } })
  check("friendship: … grown by what Jan did on their visits to Mia's town: a visit's day, a request, three gifts, a trade; Mia's visit to Jan's",
    jm.mine.visit === 1 && jm.mine.help === 1 && jm.mine.gift === 3 && jm.mine.trade === 1 && jm.theirs.visit === 1 && jm.level === 1 && jm.next?.level === 2 && jm.next.points === FRIEND_LEVELS[2],
    { mine: jm.mine, theirs: jm.theirs, level: jm.level })

  // one town's records alone can't raise it: Mia's say Jan brought 50 gifts, then Jan's say Jan left 50
  const miaLedger = readLedger(mia)
  const inflated = structuredClone(miaLedger)
  inflated.towns[janId].got.gift = 50
  inflated.towns[janId].gave.gift = 50
  writeLedger(mia, inflated)
  const [a1, b1] = [await rowOf(jan, mia), await rowOf(mia, jan)]
  writeLedger(mia, miaLedger)
  const janLedger = readLedger(jan)
  const inflated2 = structuredClone(janLedger)
  inflated2.towns[miaId].gave.gift = 50
  inflated2.towns[miaId].got.help = 40
  writeLedger(jan, inflated2)
  const [a2, b2] = [await rowOf(jan, mia), await rowOf(mia, jan)]
  writeLedger(jan, janLedger)
  check("friendship: one town's records can't raise it: 50 gifts in Mia's records, or in Jan's, change nothing on either side",
    [a1, b1, a2, b2].every(r => r.points === jm.points && r.level === jm.level) && a1.mine.gift === 3 && b2.theirs.gift === 3,
    [a1, b1, a2, b2].map(r => [r.points, r.mine.gift, r.theirs.gift]))

  // --- the market's rates by the level ----------------------------------------------------------------------------------
  const game = await (await fetch(`${mia.base}/game`, { headers: mia.auth })).json()
  const chest = { ...game.state.chest, goods: { ...game.state.chest.goods, miele: 9, gubana: 9 } }
  await fetch(`${mia.base}/game`, { method: 'PUT', headers: mia.auth, body: JSON.stringify({ rev: game.rev, state: { ...game.state, chest } }) })
  const market1 = await (await fetch(`${jan.base}/towns/${miaId}/market`, { headers: jan.auth })).json()
  const gubana = valueOf({ culture: 'friuli', goods: { gubana: 1 }, res: {} }, chestOf(culturesDir, 'friuli'))
  const cheap = Math.ceil(0.4 * gubana) // less than neighbours' share (0.45), enough between close friends (0.35)
  const trade = (id: string, o: { score?: number; wood?: number; want?: Record<string, number> } = {}) =>
    answer(post(jan, mia, 'trade', { id, give: { res: { WOOD: o.wood ?? 20 } }, get: { culture: 'friuli', goods: o.want ?? { gubana: 1 } }, score: o.score ?? 8 }))
  const low1 = await trade('rp-trade-friend-01', { score: 5 })
  const cheap1 = await trade('rp-trade-friend-02', { wood: cheap })
  check("friendship: the market's rates at level 1 (neighbours): three of a good spared, a haggle of 6, at least 0.45 of the value",
    JSON.stringify(market1.rates) === JSON.stringify({ level: 1, ...RATES[1] }) && market1.wares.miele === 3 && low1.body.code === 'no_deal' && cheap1.body.code === 'unfair',
    { rates: market1.rates, wares: market1.wares, low1, cheap1 })

  // --- help when trouble hits a friend -----------------------------------------------------------------------------------
  const now = Date.now()
  const g2 = await (await fetch(`${mia.base}/game`, { headers: mia.auth })).json()
  const wolves = { id: 'ev-smoke-1', kind: 'WOLVES', strength: 2, startedAt: now, deadline: now + 36 * 3_600_000 }
  await fetch(`${mia.base}/game`, { method: 'PUT', headers: mia.auth, body: JSON.stringify({ rev: g2.rev, state: { ...g2.state, event: wolves } }) })
  const seen = await rowOf(jan, mia)
  check("friendship: Jan's app sees the wolves at Mia's town, and that Jan can send help", seen.event?.id === 'ev-smoke-1' && seen.event.kind === 'WOLVES' && seen.event.can === true && seen.event.aided === false && Date.parse(seen.event.until) > now, seen.event)
  const aid = await answer(post(jan, mia, 'aid', { id: 'aid-00000001', event: 'ev-smoke-1' }))
  const aidAgain = await answer(post(jan, mia, 'aid', { id: 'aid-00000001', event: 'ev-smoke-1' }))
  const aidTwice = await answer(post(jan, mia, 'aid', { id: 'aid-00000002', event: 'ev-smoke-1' }))
  const book = JSON.parse(readFileSync(join(mia.data, 'app/town-guests.json'), 'utf8')).entries.filter((e: any) => e.kind === 'aid')
  check('friendship: POST /towns/:id/aid: into Mia\'s guest book once (a retry is the same answer), once per event and town',
    aid.status === 200 && aid.body.ok && aid.body.event === 'ev-smoke-1' && aidAgain.body.duplicate === true && aidAgain.body.key === aid.body.key && aidTwice.status === 409 && aidTwice.body.code === 'already_helped' && book.length === 1 && book[0].aid.kind === 'WOLVES' && book[0].from.id === janId,
    { aid, aidAgain, aidTwice, book })
  const guests = await (await fetch(`${mia.base}/towns/guests`, { headers: mia.auth })).json()
  const after = await rowOf(jan, mia)
  check("friendship: … Mia's app gets it with the guests (it lessens the damage there); Jan's shows it sent, and the help counts", guests.entries[0]?.kind === 'aid' && guests.entries[0].aid.event === 'ev-smoke-1' && after.event.aided === true && after.mine.aid === 1 && after.points > jm.points, { entry: guests.entries[0], after: after.event, mine: after.mine })
  const g3 = await (await fetch(`${mia.base}/game`, { headers: mia.auth })).json()
  await fetch(`${mia.base}/game`, { method: 'PUT', headers: mia.auth, body: JSON.stringify({ rev: g3.rev, state: { ...g3.state, event: { ...wolves, id: 'ev-smoke-2', kind: 'STORM' } } }) })
  const limited = await answer(post(jan, mia, 'aid', { id: 'aid-00000003', event: 'ev-smoke-2' }))
  const over = await answer(post(jan, mia, 'aid', { id: 'aid-00000004', event: 'ev-smoke-1' }))
  check('friendship: … one a day from a town (LANI_TOWN_AID_PER_DAY), then 429; help against trouble that is over: 409 no_event', limited.status === 429 && limited.body.code === 'rate_limited' && over.status === 409 && over.body.code === 'no_event', { limited, over })
  const early = await answer(post(jan, luka, 'aid', { id: 'aid-00000005', event: 'ev-anything' }))
  check("friendship: … help comes from good neighbours: Jan and Luka aren't yet (409 too_early)", early.status === 409 && early.body.code === 'too_early', early)
  const stranger = loadBridgeKey(tempDir('lani-smoke-friend-stranger-'))
  const text = JSON.stringify({ v: 1, id: 'aid-00000006', event: 'ev-smoke-2' })
  const unlinked = [
    (await fetch(`${mia.base}/town/friendship`, { headers: signRequest(stranger, { method: 'GET', path: '/town/friendship', to: miaId }) })).status,
    (await fetch(`${mia.base}/town/aid`, { method: 'POST', headers: { ...signRequest(stranger, { method: 'POST', path: '/town/aid', body: text, to: miaId }), 'content-type': 'application/json' }, body: text })).status,
    (await fetch(`${mia.base}/town/moves`, { method: 'POST', headers: mia.auth, body: text })).status,
    (await fetch(`${mia.base}/towns/friendship`)).status,
  ]
  check('friendship: an unlinked town, an unsigned request (the app token is none), no token: 401', unlinked.every(s => s === 401), unlinked)

  // --- closer friends: better rates, the shared feast ------------------------------------------------------------------------
  history(jan, mia, 12)
  const [jm3, mj3] = [await rowOf(jan, mia), await rowOf(mia, jan)]
  const market3 = await (await fetch(`${jan.base}/towns/${miaId}/market`, { headers: jan.auth })).json()
  const low3 = await trade('rp-trade-friend-03', { score: 5 })
  const cheap3 = await trade('rp-trade-friend-04', { wood: cheap })
  check('friendship: twelve more visit days both ways (both towns have them): close friends (level 3) on both sides',
    jm3.level === 3 && mj3.level === 3 && jm3.points === mj3.points, { jan: [jm3.level, jm3.points], mia: [mj3.level, mj3.points] })
  check("friendship: … the market's rates follow: four of a good spared, a haggle of 5 is a deal, 0.4 of the value is enough",
    market3.rates?.level === 3 && market3.rates.max_spare === RATES[3].max_spare && market3.wares.miele === 4 && low3.status === 200 && cheap3.status === 200,
    { rates: market3.rates, wares: market3.wares, low3, cheap3 })

  const setFeast = async (t: Town, d: string) => {
    const g = await (await fetch(`${t.base}/game`, { headers: t.auth })).json()
    return (await fetch(`${t.base}/game`, { method: 'PUT', headers: t.auth, body: JSON.stringify({ rev: g.rev, state: { ...(g.state ?? {}), lastFeast: d, festivals: {} } }) })).ok
  }
  const set = (await setFeast(jan, day(-30))) && (await setFeast(mia, day(0)))
  const openBefore = (await rowOf(jan, mia)).feast
  await setFeast(jan, day(-1))
  const [jf, mf] = [(await rowOf(jan, mia)).feast, (await rowOf(mia, jan)).feast]
  check("friendship: Mia held a feast today: Jan's app invites Jan to hold theirs within three days", set && openBefore?.theirs === day(0) && openBefore.mine === day(-30) && openBefore.open_until === day(3) && openBefore.shared === null, openBefore)
  check('friendship: … Jan held theirs yesterday: a shared feast ("skupna veselica") on both sides, the same days',
    jf.shared?.first === day(-1) && jf.shared.last === day(0) && mf.shared?.first === day(-1) && mf.shared.last === day(0) && jf.shared.key === `feast:${miaId}:${day(-1)}:${day(0)}` && jf.open_until === null,
    { jf, mf })

  // --- people moving between towns (Jan and Mia: two grown-ups) -----------------------------------------------------------------
  const itDir = join(jan.data, 'languages/it')
  const hadIt = existsSync(itDir)
  const skill = await answer(post(jan, mia, 'moves', { id: 'move-00000001', dir: 'in' }))
  check("friendship: someone of Mia's town moving in needs Jan's Italian at A2 (it's A1): 409 skill", skill.status === 409 && skill.body.code === 'skill' && skill.body.level === 'A1' && skill.body.need === 'A2' && (await rowOf(jan, mia)).moves.in.why === 'skill', skill)
  mkdirSync(itDir, { recursive: true })
  writeFileSync(join(itDir, 'learner-profile.json'), JSON.stringify({ learner: { current_level: 'A2' } }))
  try {
    const offer = await answer(post(jan, mia, 'moves', { id: 'move-00000002', dir: 'in' }))
    const person = offer.body.move?.person
    const retry = await answer(post(jan, mia, 'moves', { id: 'move-00000002', dir: 'in' }))
    const second = await answer(post(jan, mia, 'moves', { id: 'move-00000003', dir: 'out' }))
    check("friendship: Jan (Italian A2 now) invites someone of Mia's town: Mia's bridge names them, of her culture and a family of her village",
      offer.status === 200 && offer.body.move.status === 'offered' && offer.body.move.by === 'me' && offer.body.move.dir === 'in' && person?.culture === 'friuli' && person.language === 'it' && person.from.id === miaId && person.name.endsWith(' Furlan') && /^m-/.test(person.id),
      offer)
    check('friendship: … offered again (a retry): the same; another offer while one is open: 409 open', retry.body.duplicate === true && retry.body.move.person.id === person.id && second.status === 409 && second.body.code === 'open', { retry, second })
    const own = await answer(post(jan, mia, 'moves/move-00000002/accept'))
    const shown = (await rowOf(mia, jan)).moves.list.find((m: any) => m.id === 'move-00000002')
    check("friendship: … the town that offered doesn't say yes to it (409); Mia's app shows the offer: someone of hers moving to Jan's", own.status === 409 && own.body.code === 'not_yours' && shown?.status === 'offered' && shown.by === 'them' && shown.dir === 'out' && shown.person.id === person.id, { own, shown })
    const yes = await answer(post(mia, jan, 'moves/move-00000002/accept'))
    const yesAgain = await answer(post(mia, jan, 'moves/move-00000002/accept'))
    const [janMove, miaMove] = [(await rowOf(jan, mia)).moves.list.find((m: any) => m.id === 'move-00000002'), (await rowOf(mia, jan)).moves.list.find((m: any) => m.id === 'move-00000002')]
    check('friendship: Mia says yes: the move is done on both sides, the same person: into Jan\'s village, out of Mia\'s; saying yes again is the same',
      yes.status === 200 && yes.body.move.status === 'done' && yesAgain.body.duplicate === true && janMove?.status === 'done' && janMove.dir === 'in' && miaMove?.status === 'done' && miaMove.dir === 'out' && JSON.stringify(janMove.person) === JSON.stringify(miaMove.person) && janMove.person.id === person.id && !!Date.parse(janMove.done_at),
      { yes, janMove, miaMove })
    const soon = (await rowOf(jan, mia)).moves.in
    check('friendship: … the next one that way waits a fortnight', soon.ok === false && soon.why === 'soon' && soon.until === day(14), soon)

    // someone of Jan's moving to Mia's: Mia's Slovene is checked on her side; then declined
    const hers = await answer(post(jan, mia, 'moves', { id: 'move-00000004', dir: 'out' }))
    const slDir = join(mia.data, 'languages/sl')
    mkdirSync(slDir, { recursive: true })
    writeFileSync(join(slDir, 'learner-profile.json'), JSON.stringify({ learner: { current_level: 'B1' } }))
    const out = await answer(post(jan, mia, 'moves', { id: 'move-00000005', dir: 'out' }))
    const no = await answer(post(mia, jan, 'moves/move-00000005/decline'))
    const [jd, md] = [(await rowOf(jan, mia)).moves.list.find((m: any) => m.id === 'move-00000005'), (await rowOf(mia, jan)).moves.list.find((m: any) => m.id === 'move-00000005')]
    check("friendship: someone of Jan's moving to Mia's: her bridge checks her Slovene (A1: 409 skill); at B1 offered (someone of Jan's culture), then declined on both sides",
      hers.status === 409 && hers.body.code === 'skill' && out.status === 200 && out.body.move.person.culture === 'primorska' && out.body.move.person.from.id === janId && no.body.move?.status === 'declined' && jd?.status === 'declined' && md?.status === 'declined',
      { hers, out, no, jd, md })
  } finally {
    if (!hadIt) rmSync(join(jan.data, 'languages'), { recursive: true, force: true })
  }

  // --- a child's town: the child's side offers, the grown-up side says yes ----------------------------------------------------
  history(jan, luka, 20)
  const jl = await rowOf(jan, luka)
  const toChild = await answer(post(jan, luka, 'moves', { id: 'move-00000010', dir: 'out' }))
  const direct = JSON.stringify({ v: 1, id: 'move-00000011', action: 'offer', from_town: janId, to_town: luka.key.fingerprint, person: pickMover({ people: {}, state: {}, seed: 'x', culture: 'primorska', language: 'sl', town: { id: janId, name: 'x' } }) })
  const childSide = await answer(fetch(`${luka.base}/town/moves`, { method: 'POST', headers: { ...signRequest(jan.key, { method: 'POST', path: '/town/moves', body: direct, to: luka.key.fingerprint }), 'content-type': 'application/json' }, body: direct }))
  check("friendship: Luka's is a child's town: Jan's app can't offer it a move (409 child: it starts on Luka's side), and Luka's bridge refuses offers itself",
    jl.level >= 3 && jl.child === true && jl.moves.out.why === 'child' && toChild.status === 409 && toChild.body.code === 'child' && childSide.status === 409 && childSide.body.code === 'child',
    { level: jl.level, child: jl.child, out: jl.moves.out, toChild, childSide })
  const lukaRow = await rowOf(luka, jan)
  const ask = await answer(post(luka, jan, 'moves', { id: 'move-00000012', dir: 'in' }))
  const selfYes = await answer(post(luka, jan, 'moves/move-00000012/accept'))
  check("friendship: … Luka's app offers: someone of Jan's town moving in (Slovene is Luka's base: no skill needed); his app can't say yes (403 child)",
    lukaRow.moves.in.ok === true && lukaRow.moves.skill.level === 'C2' && ask.status === 200 && ask.body.move.person.culture === 'primorska' && selfYes.status === 403 && selfYes.body.code === 'child',
    { can: lukaRow.moves, ask, selfYes })
  const parent = await answer(post(jan, luka, 'moves/move-00000012/accept'))
  const [lm, jlm] = [(await rowOf(luka, jan)).moves.list.find((m: any) => m.id === 'move-00000012'), (await rowOf(jan, luka)).moves.list.find((m: any) => m.id === 'move-00000012')]
  check("friendship: … Jan (the grown-up side) says yes: done on both sides, the same person, into Luka's village",
    parent.status === 200 && lm?.status === 'done' && lm.dir === 'in' && jlm?.status === 'done' && jlm.dir === 'out' && lm.person.id === jlm.person.id, { parent, lm, jlm })

  // --- the rules, pure ----------------------------------------------------------------------------------------------------------
  const own = { gave: { ...zero(), gift: 5, visit: 2 }, got: { ...zero(), trade: 1 } }
  const rep = { gave: { ...zero(), trade: 4 }, got: { ...zero(), gift: 2, visit: 9 } }
  const ag = agree(own, rep)
  const f0 = friendshipOf(own, undefined)
  check('friendship: the rules: each direction the lower of both counts, twice the smaller side plus a lead of at most 30, the levels',
    ag.mine.gift === 2 && ag.mine.visit === 2 && ag.theirs.trade === 1 && f0.level === 0 && f0.points === 0 && combine(100, 0) === 30 && combine(10, 12) === 22 && levelOf(7) === 0 && levelOf(8) === 1 && levelOf(90) === 4 && levelOf(1000) === 4 && friendshipOf(own, rep).level === 1 && friendshipOf(own, rep).next?.level === 2,
    { ag, f0 })
  check('friendship: … the shared feast: three days apart at most, lately, from level 2; the day to hold one by',
    sharedFeast('2026-09-01', '2026-09-04', 2, '2026-09-05')?.first === '2026-09-01' && sharedFeast('2026-09-01', '2026-09-05', 2, '2026-09-05') === null && sharedFeast('2026-09-01', '2026-09-02', 1, '2026-09-05') === null && sharedFeast('2026-08-01', '2026-08-02', 4, '2026-09-05') === null &&
      feastOpenUntil(null, '2026-09-04', 2, '2026-09-05') === '2026-09-07' && feastOpenUntil(null, '2026-09-01', 2, '2026-09-05') === null)
  const mover = pickMover({ people: JSON.parse(readFileSync(join(culturesDir, 'friuli/people.json'), 'utf8')), state: { residents: [{ family: 'Zorzut' }] }, seed: 's', culture: 'friuli', language: 'it', town: { id: miaId, name: 'Il mio villaggio' } })
  check("friendship: … who moves: of the village's families, the same for the same move; a mover from another town is checked; trouble is an open event",
    mover?.name.endsWith(' Zorzut') && JSON.stringify(mover) === JSON.stringify(pickMover({ people: JSON.parse(readFileSync(join(culturesDir, 'friuli/people.json'), 'utf8')), state: { residents: [{ family: 'Zorzut' }] }, seed: 's', culture: 'friuli', language: 'it', town: { id: miaId, name: 'Il mio villaggio' } })) &&
      cleanMover({ ...mover, id: 'x' }) === undefined && cleanMover({ ...mover, name: '<b>' })?.name === 'b' && troubleOf({ event: { ...wolves, kind: 'MERCHANT' } }) === null && troubleOf({ event: { ...wolves, deadline: now - 1 } }) === null && troubleOf({ event: wolves })?.kind === 'WOLVES',
    mover)
}
