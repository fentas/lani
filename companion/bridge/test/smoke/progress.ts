// Smoke checks: learning progress between linked towns (src/progress.ts, src/features/progress.ts, lani-town share and
// guardian), with the towns section's bridges: Jan (the harness's, an adult's, primorska), Mia (an adult's, friuli) and
// Luka (a child's, friuli), Jan's town Luka's guardian (lani-town link default luka). Luka's learning is a synthetic one
// (test/fixtures/learning.ts) with a marker in everything that must never leave the child's town. What each level shares, that a
// guardian sees Details and the child's app can't lower it (the parent can, on the node), the other way round at what the
// parent shares, levels changed and the other side seeing less, a challenge from the progress screen and how it went,
// the weekly note, the tutor's tool; and that an unlinked town, an unsigned request, the app's or the family's token get
// nothing, and no free-text answer, correction, note or chat ever appears at any level. Called by towns.ts while the
// towns are linked (before Luka is linked with Mia).
import { readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import type { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { loadBridgeKey } from '../../src/pairing'
import { cleanProgress, isoWeek, learned, progressOf, rusty, streakOf, weekStart, type Progress } from '../../src/progress'
import { localDate, shareOf, signRequest } from '../../src/towns'
import { writeLearning } from '../fixtures/learning'
import { check, fam, tempDir } from './harness'
import type { Town } from './visits'

type Peer = Town & { notes: any[]; client: Client }

const SECRET = 'NEVERSHARED'
const cliDir = resolve(import.meta.dir, '../../src/cli')
const get = async (t: Town, path: string, headers: Record<string, string> = t.auth) => {
  const r = await fetch(`${t.base}${path}`, { headers })
  const text = await r.text()
  let body: any = null
  try {
    body = JSON.parse(text)
  } catch {}
  return { status: r.status, body, text }
}
const send = async (t: Town, method: string, path: string, body: unknown) => {
  const r = await fetch(`${t.base}${path}`, { method, headers: t.auth, body: JSON.stringify(body) })
  return { status: r.status, body: (await r.json().catch(() => null)) as any }
}
/** A signed GET of [path] by [from]'s key to town [to], straight to its bridge. */
const signedGet = (fromKey: Town['key'], to: Town, path: string) => fetch(`${to.base}${path}`, { headers: signRequest(fromKey, { method: 'GET', path, to: to.key.fingerprint }) })
const settle = () => Bun.sleep(200)

function cli(cmd: string[], env: Record<string, string>) {
  const p = Bun.spawnSync(cmd, { env: { ...process.env, ...env }, stdout: 'pipe', stderr: 'pipe' })
  return { code: p.exitCode, out: p.stdout.toString(), err: p.stderr.toString() }
}

/** The pure part: the summary's numbers, the cards' states, what each level holds, the cleaning on the asking side. */
function pure() {
  const today = '2026-10-28' // a Wednesday
  check('progress: weeks start on Monday; the ISO week', weekStart(today) === '2026-10-26' && weekStart('2026-10-26') === '2026-10-26' && weekStart('2026-11-01') === '2026-10-26' && isoWeek(today) === '2026-W44' && isoWeek('2027-01-01') === '2026-W53')
  check('progress: a streak counts back from today, or from yesterday while today is open', streakOf(new Set(['2026-10-28', '2026-10-27', '2026-10-25']), today) === 2 && streakOf(new Set(['2026-10-27', '2026-10-26']), today) === 2 && streakOf(new Set(['2026-10-25']), today) === 0)
  check("progress: learned and rusty as the app's Stats counts them", learned({ repetitions: 1 }) && !learned({ repetitions: 0, total_reviews: 2, last_quality: 2 }) && rusty({ repetitions: 0, total_reviews: 2, last_quality: 2 }) && learned({ total_reviews: 0, last_quality: 3 }) && !learned({ total_reviews: 0, last_quality: 3, source: 'lookup' }))
  const db = { databases: {}, computed: { today } }
  const dir = tempDir('lani-smoke-progress-pure-')
  for (const f of ['spaced-repetition', 'mistakes-db', 'session-log', 'learner-profile']) writeFileSync(join(dir, `${f}.json`), '{"learner":{}}')
  const now = Date.parse(`${today}T12:00:00`)
  writeLearning(dir, { language: 'it', base: 'de', secret: SECRET, now })
  const read = (f: string) => JSON.parse(readFileSync(join(dir, `${f}.json`), 'utf8'))
  Object.assign(db.databases, { spaced_repetition: read('spaced-repetition'), mistakes_db: read('mistakes-db'), session_log: read('session-log'), learner_profile: read('learner-profile') })
  const town = { id: 'a'.repeat(32), name: 'Borgo', learner: 'Luka', culture: 'friuli', language: 'it' }
  const at = (level: 'village' | 'summary' | 'details') => progressOf({ db, level, town, child: true, guardian: level === 'details', base: 'de', viewer: 'en', meaning: w => (w === 'il cane' ? 'the dog' : undefined), now })
  const v = at('village')
  const s = at('summary')
  const d = at('details')
  check('progress: just the village: who and the day, nothing of the learning', JSON.stringify(Object.keys(v).sort()) === '["child","date","level","town","v"]', v)
  check("progress: a summary: the streak, this week's days as dots, minutes, reviews, new words, the words known, the level",
    !s.details && s.summary?.streak === 4 && JSON.stringify(s.summary.week.days) === '[true,true,true,false,false,false,false]' && s.summary.week.days_practised === 3 && s.summary.week.minutes === 35 && s.summary.week.new_words === 2 && s.summary.words.known === 8 && s.summary.words.tracked === 10 && s.summary.level === 'A1' && s.summary.target_level === 'A2' && s.summary.week.reviews > 0, s.summary)
  const dd = d.details!
  check('progress: details: the newest cards being learned (the rusty one, the new one), with their meaning in the learner\'s base and the viewer\'s where the dictionary has it',
    dd.learning.length === 10 && dd.learning[0].word === 'il cane' && dd.learning[0].state === 'learning' && dd.learning[0].meanings.de === 'der Hund' && dd.learning[0].meanings.en === 'the dog' && dd.learning[1].state === 'rusty' && dd.learning[2].state === 'known', dd.learning.slice(0, 3))
  check('progress: … what is hard: the patterns with mistakes lately by name, each with the cards its mistakes name; the old one left out',
    dd.hard.patterns.length === 2 && dd.hard.patterns[0].name === 'Articoli: il / la' && dd.hard.patterns[0].trend === 'worse' && dd.hard.patterns[0].items.map(i => i.word).join(',') === 'la casa,la finestra' && dd.hard.patterns.every(p => p.id !== 'old_pattern'), dd.hard.patterns)
  check('progress: … the cards failed lately that no pattern names (a review below 3 this week), and what is due', dd.hard.words.length === 1 && dd.hard.words[0].word === 'la mela' && dd.hard.words[0].misses === 1 && dd.due.today >= 1, { words: dd.hard.words, due: dd.due })
  const all = JSON.stringify([v, s, d])
  check('progress: … and never what was typed, the corrections, the contexts, the notes, an error pattern\'s own card, the session notes or the profile', !all.includes(SECRET) && !all.includes('TYPED') && !all.includes('CORRECTED'), all.match(new RegExp(`.{40}${SECRET}.{40}`))?.[0])
  // the asking side: a town that sends more than its level is cut back to it; something else than a progress is refused
  const more = cleanProgress({ ...d, level: 'summary' }, town.id)!
  const lying = cleanProgress({ ...s, town: { ...town, id: 'b'.repeat(32) } }, town.id)
  const extra = cleanProgress({ ...d, secret: SECRET, details: { ...d.details, chat: SECRET, learning: [{ ...dd.learning[0], answer: SECRET }] } }, town.id)!
  check('progress: the asking side keeps at most what the level says, of the town it asked, and only the fields it knows', !more.details && !!more.summary && lying === undefined && !JSON.stringify(extra).includes(SECRET) && extra.details?.learning[0].word === 'il cane', { more: Object.keys(more), extra: extra.details?.learning[0] })
  // the defaults: a guardian sees details, a child's town shares just the village with anyone else, adults a summary
  check("progress: the defaults: a guardian's town sees details (never less than its floor), a child's town shares just the village with anyone else, an adult's a summary",
    shareOf({ guardian: true }, true).level === 'details' && shareOf({ guardian: true, share: 'village' }, true).level === 'details' && shareOf({ guardian: true, floor: 'summary', share: 'village' }, true).level === 'summary' && shareOf({}, true).level === 'village' && shareOf({}, false).level === 'summary' && shareOf({ guardian: true }, false).level === 'summary')
}

export async function progress(jan: Peer, mia: Peer, luka: Peer, cliEnv: Record<string, string>) {
  pure()
  const ids = { jan: jan.key.fingerprint, mia: mia.key.fingerprint, luka: luka.key.fingerprint }
  // Luka's learning: Italian cards with meanings in Slovene (Luka's base), mistakes, sessions; Mia's: Italian from English
  writeLearning(luka.data, { language: 'it', base: 'sl', secret: SECRET })
  writeLearning(mia.data, { language: 'it', base: 'en', secret: SECRET, streak: 2 })

  // --- between bridges: signed, linked towns only ------------------------------------------------------------------------
  const r = await signedGet(jan.key, luka, '/town/progress?base=en')
  const text = await r.text()
  const p = JSON.parse(text) as Progress
  check("progress: GET /town/progress answers Jan's town (Luka's guardian) with Luka's details, signed back", r.status === 200 && !!r.headers.get('x-lani-town-signature') && p.level === 'details' && p.guardian === true && p.child === true && p.town.id === ids.luka && p.summary?.streak === 4 && (p.details?.learning.length ?? 0) > 0 && p.details!.hard.patterns[0].name === 'Articoli: il / la', p)
  check('progress: … no free-text answer, correction, context, note or session note in it', !text.includes(SECRET), text.match(new RegExp(`.{60}${SECRET}.{20}`))?.[0])
  const codeOf = async (res: Response) => [res.status, (await res.json().catch(() => ({}))).code]
  const stranger = loadBridgeKey(tempDir('lani-smoke-progress-stranger-'))
  const refused = {
    // Mia's town isn't linked with Luka's yet
    unlinked: await codeOf(await signedGet(mia.key, luka, '/town/progress')),
    stranger: await codeOf(await signedGet(stranger, luka, '/town/progress')),
    unsigned: await codeOf(await fetch(`${luka.base}/town/progress`)),
    appToken: await codeOf(await fetch(`${luka.base}/town/progress`, { headers: luka.auth })),
    familyToken: await codeOf(await fetch(`${luka.base}/town/progress`, { headers: fam })),
    otherPath: await codeOf(await fetch(`${luka.base}/town/progress?base=en`, { headers: signRequest(jan.key, { method: 'GET', path: '/town/progress', to: ids.luka }) })),
  }
  check("progress: an unlinked town, a stranger's key, no signature, the app's or the family's token, a signature for another path: 401, nothing else", Object.values(refused).every(([s]) => s === 401) && refused.unlinked[1] === 'unlinked' && refused.unsigned[1] === 'unsigned', refused)
  const famApp = await fetch(`${jan.base}/towns/${ids.luka}/progress`, { headers: fam })
  const noAuth = await fetch(`${jan.base}/towns/${ids.luka}/progress`)
  check("progress: the app's routes take the app's token only (the family's: 403, none: 401)", famApp.status === 403 && noAuth.status === 401 && (await fetch(`${jan.base}/towns/${ids.luka}/sharing`, { method: 'PUT', headers: fam, body: '{"share":"village"}' })).status === 403)

  // --- the parent's app: the child's progress ---------------------------------------------------------------------------
  const seen = await get(jan, `/towns/${ids.luka}/progress?fresh=1`)
  check("progress: Jan's app gets Luka's progress through Jan's bridge: details, checked and cleaned, with what each shares", seen.status === 200 && seen.body.level === 'details' && seen.body.guardian === true && seen.body.details?.hard?.patterns?.length === 2 && !!seen.body.fetched_at && seen.body.sharing?.share === 'summary' && seen.body.sharing.share_default === true && seen.body.sharing.theirs?.share === 'details' && seen.body.sharing.theirs.guardian === true && !seen.text.includes(SECRET), seen.body.sharing ?? seen.body)
  const word = seen.body.details?.learning?.[0]
  check("progress: … a word being learned with its meaning in Luka's base (Slovene)", word?.word === 'il cane' && word.meanings.sl === 'pes', word)
  const janRow = (await get(jan, '/towns')).body.towns.find((t: any) => t.id === ids.luka)
  check("progress: GET /towns shows both ways: Jan shares a summary with Luka (the default); Luka shares details (Jan is Luka's guardian)", janRow?.sharing?.share === 'summary' && janRow.sharing.theirs?.share === 'details' && janRow.sharing.theirs.guardian === true && !janRow.sharing.guardian, janRow?.sharing)

  // --- the child's app: the parent can see; Jan's progress at what Jan shares --------------------------------------------
  const lukaRow = (await get(luka, '/towns')).body.towns.find((t: any) => t.id === ids.jan)
  check("progress: Luka's Friends: the parent's town sees details, the child can't choose less", lukaRow?.sharing?.share === 'details' && lukaRow.sharing.guardian === true && lukaRow.sharing.floor === 'details' && JSON.stringify(lukaRow.sharing.choices) === '["details"]', lukaRow?.sharing)
  const lower = await send(luka, 'PUT', `/towns/${ids.jan}/sharing`, { share: 'village' })
  check("progress: … the child's app can't lower it (403 guardian): a parent can, on the node", lower.status === 403 && lower.body.code === 'guardian' && shareOf(JSON.parse(readFileSync(join(luka.data, 'app/towns.json'), 'utf8')).links.find((l: any) => l.id === ids.jan), true).level === 'details', lower)
  const papa = await get(luka, `/towns/${ids.jan}/progress?fresh=1`)
  check("progress: Luka sees Jan's progress at what Jan shares: a summary, no details", papa.status === 200 && papa.body.level === 'summary' && !!papa.body.summary && papa.body.details === undefined && !papa.body.guardian && papa.body.sharing?.theirs?.share === 'summary', papa.body)

  // --- the sharer changes the level: the other side sees less ---------------------------------------------------------------
  const set = await send(jan, 'PUT', `/towns/${ids.luka}/sharing`, { share: 'village' })
  const papa2 = await get(luka, `/towns/${ids.jan}/progress?fresh=1`)
  check("progress: Jan shares just the village with Luka now: Luka's app sees no summary", set.status === 200 && set.body.share === 'village' && !set.body.share_default && papa2.status === 200 && papa2.body.level === 'village' && papa2.body.summary === undefined && papa2.body.details === undefined, { set: set.body, papa2: papa2.body })
  const toMia = await get(mia, `/towns/${ids.jan}/progress?fresh=1`)
  check("progress: between two adults' towns the default is a summary (Mia sees Jan's)", toMia.status === 200 && toMia.body.level === 'summary' && !!toMia.body.summary && toMia.body.details === undefined, toMia.body)
  await send(mia, 'PUT', `/towns/${ids.jan}/sharing`, { share: 'details' })
  const miaDetails = await get(jan, `/towns/${ids.mia}/progress?fresh=1`)
  await send(mia, 'PUT', `/towns/${ids.jan}/sharing`, { share: 'village' })
  const miaVillage = await get(jan, `/towns/${ids.mia}/progress?fresh=1`)
  check("progress: Mia shares details with Jan, then just the village: Jan's app sees the words, then nothing of them", miaDetails.body?.level === 'details' && miaDetails.body.details?.learning?.[0]?.meanings?.en === 'the dog' && miaVillage.body?.level === 'village' && miaVillage.body.summary === undefined && !miaDetails.text.includes(SECRET), { d: miaDetails.body?.level, v: miaVillage.body })
  check('progress: a level that isn\'t one is a 400; an unknown town a 404', (await send(jan, 'PUT', `/towns/${ids.mia}/sharing`, { share: 'everything' })).status === 400 && (await send(jan, 'PUT', `/towns/${'0'.repeat(32)}/sharing`, { share: 'summary' })).status === 404 && (await get(jan, `/towns/${'0'.repeat(32)}/progress`)).status === 404)

  // --- the parent on the node: lani-town share … --profile luka, list both ways ---------------------------------------------------
  const lowered = cli(['bun', join(cliDir, 'town.ts'), 'share', 'default', 'summary', '--profile', 'luka'], cliEnv)
  const afterCli = await get(jan, `/towns/${ids.luka}/progress?fresh=1`)
  check('progress: lani-town share default summary --profile luka: the parent lowers what Jan\'s town sees to a summary', lowered.code === 0 && lowered.out.includes('summary') && afterCli.body?.level === 'summary' && afterCli.body.guardian === true && afterCli.body.details === undefined, lowered.out + lowered.err + JSON.stringify(afterCli.body?.level))
  const raised = await send(luka, 'PUT', `/towns/${ids.jan}/sharing`, { share: 'details' })
  const below = await send(luka, 'PUT', `/towns/${ids.jan}/sharing`, { share: 'village' })
  check("progress: … Luka's app may share more again (details), never less than the parent set", raised.status === 200 && raised.body.share === 'details' && below.status === 403, { raised: raised.body, below: below.body })
  const listed = cli(['bun', join(cliDir, 'town.ts'), 'list'], cliEnv)
  const listedLuka = cli(['bun', join(cliDir, 'town.ts'), 'list', '--profile', 'luka'], cliEnv)
  check('progress: lani-town list shows the level each way', listed.code === 0 && /SHARES\s+THEY SHARE/.test(listed.out) && /village\s+details \(you are the guardian\)/.test(listed.out) && listedLuka.code === 0 && /details \(guardian, at least summary\)\s+village/.test(listedLuka.out), listed.out + listedLuka.out + listed.err)
  check('progress: lani-town share refuses a level that isn\'t one', cli(['bun', join(cliDir, 'town.ts'), 'share', 'default', 'all'], cliEnv).code !== 0)
  cli(['bun', join(cliDir, 'town.ts'), 'share', 'luka', 'summary'], cliEnv) // Jan shares a summary with Luka again

  // --- a challenge from the progress screen, and how it went ---------------------------------------------------------------
  const n0 = luka.notes.length
  const ch = { id: 'progress-ch-0001', type: 'translate', text: 'pes', hint: 'Il o la? 🙂' }
  const sentCh = await send(jan, 'POST', `/towns/${ids.luka}/challenges`, ch)
  const again = await send(jan, 'POST', `/towns/${ids.luka}/challenges`, ch)
  await settle()
  const thread = (await get(luka, '/family/api/challenges')).body as any[]
  const mine = thread.filter(c => c.town?.id === ids.jan)
  const note = luka.notes.slice(n0).find(n => n.params?.meta?.kind === 'partner_challenge')
  check("progress: Jan sends Luka a challenge from Luka's progress: it is a family challenge in Luka's app (from Jan), once however often it is sent",
    sentCh.status === 200 && sentCh.body.ok && again.body?.duplicate === true && mine.length === 1 && mine[0].from === 'Jan' && mine[0].text === 'pes' && mine[0].type === 'translate', { sentCh: sentCh.body, again: again.body, mine })
  check("progress: … Luka's tutor gets it as untrusted content from a town, the note to write in Jan's base", String(note?.params?.content ?? '').includes('untrusted_town_content') && String(note?.params?.content).includes('in English'), note?.params?.content)
  const linky = await send(jan, 'POST', `/towns/${ids.luka}/challenges`, { id: 'progress-ch-0002', type: 'question', text: 'Guarda www.example.com' })
  check("progress: … a child's town takes no link (409 not_for_a_child)", linky.status === 409 && linky.body.code === 'not_for_a_child', linky)
  const answered = await send(luka, 'POST', `/family/api/challenges/${mine[0]?.id}/answer`, { text: `il cane ${SECRET}-ANSWER` })
  await luka.client.callTool({ name: 'reply', arguments: { conversation_id: `fam_${mine[0]?.id}`, text: 'Bravo! 🟢', data: { score: 9, partner_note: 'Luka knew it: il cane.' } } })
  await settle()
  const how = await get(jan, `/towns/${ids.luka}/progress?fresh=1`)
  const result = how.body?.challenges?.find((c: any) => c.id === ch.id)
  check("progress: … Jan's progress screen shows how it went: answered, the score and the tutor's note for Jan; never Luka's answer", answered.status === 200 && result?.answered === true && result.score === 9 && result.note === 'Luka knew it: il cane.' && !how.text.includes(SECRET), result ?? how.body)
  // Mia's challenge to Jan: in Jan's answer to Mia, never in Jan's answer to Luka (each asking town sees its own)
  const miaCh = await send(mia, 'POST', `/towns/${ids.jan}/challenges`, { id: 'progress-ch-mia-01', type: 'question', text: 'Come stai?' })
  const toLuka = await signedGet(luka.key, jan, '/town/progress')
  const toMiaAgain = await signedGet(mia.key, jan, '/town/progress')
  const [lt, mt] = [await toLuka.text(), await toMiaAgain.text()]
  check("progress: … each town sees how its own challenges went, never another's", miaCh.status === 200 && toLuka.status === 200 && !lt.includes('progress-ch-mia-01') && mt.includes('progress-ch-mia-01') && !mt.includes('Luka knew it'), { miaCh: miaCh.body, lt: lt.slice(0, 200) })

  // --- the weekly note (Mia's bridge checks every few hundred ms, today being its day) -------------------------------------
  const since = (await get(mia, '/events/backlog?since=0')).body?.at(-1)?.id ?? 0
  await send(mia, 'PUT', `/towns/${ids.jan}/sharing`, { weekly: true })
  let noteEv: any
  for (let i = 0; i < 30 && !noteEv; i++) {
    await Bun.sleep(200)
    noteEv = ((await get(mia, `/events/backlog?since=${since}`)).body ?? []).map((e: any) => e.event ?? e).find((e: any) => e?.type === 'town_progress')
  }
  const week = isoWeek(localDate())
  const miaLink = JSON.parse(readFileSync(join(mia.data, 'app/towns.json'), 'utf8')).links.find((l: any) => l.id === ids.jan)
  check("progress: the weekly note: once in the week's time, a town_progress event with the days, new words and what is hard", noteEv?.town === ids.jan && noteEv.learner === 'Jan' && typeof noteEv.days === 'number' && typeof noteEv.new_words === 'number' && Array.isArray(noteEv.hard) && noteEv.week === week && miaLink?.weekly === true && miaLink.weekly_sent === week, { noteEv, miaLink })
  await Bun.sleep(800)
  const notes = ((await get(mia, `/events/backlog?since=${since}`)).body ?? []).map((e: any) => e.event ?? e).filter((e: any) => e?.type === 'town_progress')
  check('progress: … once a week only', notes.length === 1, notes.length)
  await send(mia, 'PUT', `/towns/${ids.jan}/sharing`, { weekly: false })

  // --- the tutor knows what is shared ----------------------------------------------------------------------------------------
  await get(luka, `/towns/${ids.jan}/progress?fresh=1`) // Luka's town asks Jan's again: Jan shares a summary since lani-town share
  const overview = await luka.client.callTool({ name: 'town_progress', arguments: {} })
  const ov = String((overview.content as any[])[0]?.text ?? '')
  check("progress: Luka's tutor (town_progress): what Luka shares with the parent's town and what it shares with Luka", !overview.isError && /Jan's town .*you share details \(their guardian's town/.test(ov) && ov.includes('they share summary with you'), ov)
  const jans = await jan.client.callTool({ name: 'town_progress', arguments: { town: 'Luka' } })
  const jt = String((jans.content as any[])[0]?.text ?? '')
  check("progress: … Jan's tutor reads Luka's shared progress when asked (the hard words), never anything private", !jans.isError && jt.includes('Articoli: il / la') && jt.includes('la casa') && !jt.includes(SECRET), jt)
  const guide = String(((await jan.client.callTool({ name: 'channel_guide', arguments: {} })).content as any[])[0]?.text ?? '')
  check("progress: the tutor's guide says what is shared and when to use another learner's", guide.includes('Progress between towns') && guide.includes('only when the learner asks'), guide.length)
}
