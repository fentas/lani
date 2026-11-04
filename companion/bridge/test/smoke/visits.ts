// Smoke checks: visiting a linked town (src/visits.ts, src/features/towns.ts), with the towns section's bridges (Jan's,
// the harness's; Mia's, friuli; Luka's, a child's, friuli): a town gives a linked town its culture's cast and its
// curated scenes, signed, never what its tutor made; the visitor's bridge passes them on to its app, checked and
// cleaned, keeps them for a minute and, when the town is away, its last state with a note. Called by towns.ts while
// the towns are linked ([visits]), and at its end, when a town goes away ([visitsOffline]).
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import type { BridgeKey } from '../../src/pairing'
import { H, responseMessage, signRequest, verifySigned } from '../../src/towns'
import { PeerCache, cleanScenes, cleanVillagers } from '../../src/visits'
import { check, culturesDir, fam, tempDir } from './harness'
import { loadBridgeKey } from '../../src/pairing'

export type Town = { base: string; auth: Record<string, string>; key: BridgeKey; data: string }

const ids = (list: { id: string }[]) => list.map(v => v.id).sort().join(',')
const castOf = (culture: string) => readdirSync(join(culturesDir, culture, 'villagers')).filter(f => f.endsWith('.json')).map(f => JSON.parse(readFileSync(join(culturesDir, culture, 'villagers', f), 'utf8')))

/** Jan visits Mia's Italian town, and Mia Jan's; what their tutors made stays home. */
export async function visits(jan: Town, mia: Town, luka: Town) {
  // Mia's tutor made a villager of its own, changed Nonna Rosa, and wrote a scene: private to Mia's town.
  const friuli = castOf('friuli')
  const rosa = friuli.find(v => v.id === 'rosa')
  mkdirSync(join(mia.data, 'app/villagers'), { recursive: true })
  mkdirSync(join(mia.data, 'app/scenes'), { recursive: true })
  writeFileSync(join(mia.data, 'app/villagers/rosa.json'), JSON.stringify({ ...rosa, name: 'XQZ Rosa', personality: 'XQZ-PERSONA' }))
  writeFileSync(join(mia.data, 'app/villagers/xqz-tutor.json'), JSON.stringify({ ...rosa, id: 'xqz-tutor', name: 'XQZ Tutor' }))
  const sea = JSON.parse(readFileSync(join(culturesDir, 'friuli/scenes/il-mare.json'), 'utf8'))
  writeFileSync(join(mia.data, 'app/scenes/xqz-mare.json'), JSON.stringify({ ...sea, id: 'xqz-mare', title: { it: 'XQZ', en: 'XQZ' }, from: ['fire'] }))
  const miaOwn = await (await fetch(`${mia.base}/villagers`, { headers: mia.auth })).json()
  const miaScenes = await (await fetch(`${mia.base}/scenes`, { headers: mia.auth })).json()
  check("visits: Mia's own app sees what her tutor made (the smoke's premise)", miaOwn.some((v: any) => v.id === 'xqz-tutor') && miaOwn.find((v: any) => v.id === 'rosa')?.name === 'XQZ Rosa' && miaScenes.some((s: any) => s.id === 'xqz-mare'), { villagers: ids(miaOwn), scenes: ids(miaScenes) })

  // --- the host's side: signed, linked towns only ------------------------------------------------------------
  const ask = async (to: Town, path: string, from: BridgeKey = jan.key) => {
    const headers = signRequest(from, { method: 'GET', path, to: to.key.fingerprint })
    const res = await fetch(`${to.base}${path}`, { headers })
    const text = await res.text()
    return { res, text, body: res.ok ? JSON.parse(text) : JSON.parse(text || '{}'), nonce: headers[H.nonce] }
  }
  const people = await ask(mia, '/town/villagers')
  const signedBack = verifySigned(mia.key.spki.toString('base64'), responseMessage(people.nonce, people.text), people.res.headers.get(H.signature) ?? '')
  check("visits: GET /town/villagers answers a linked town, signed back: the town, its culture and language, the culture's cast", people.res.status === 200 && signedBack && people.body.town === mia.key.fingerprint && people.body.culture === 'friuli' && people.body.language === 'it' && ids(people.body.villagers) === ids(friuli), { status: people.res.status, head: { ...people.body, villagers: undefined }, got: ids(people.body.villagers ?? []) })
  const hostRosa = people.body.villagers?.find((v: any) => v.id === 'rosa')
  check("visits: … as the culture pack has them (looks, voice, role, homes, lines), never the tutor's changes or newcomers", hostRosa?.name === rosa.name && hostRosa.art === rosa.art && hostRosa.lines?.greet?.length === rosa.lines.greet.length && hostRosa.source === 'curated' && !/XQZ|published_at/.test(people.text), hostRosa?.name)
  // a line's "when" only for a bridge that says its time of day: an older one would refuse the villager
  const timedHost = await ask(mia, '/town/villagers?time=morning')
  const rosaNight = (list: any[]) => list?.find((v: any) => v.id === 'rosa')?.lines?.bye?.find((l: any) => l.it === 'Buonanotte! E mangia!')?.when
  check('visits: … the lines\' "when" only for a bridge that says its time of day (?time=), none for an older one', timedHost.res.status === 200 && JSON.stringify(rosaNight(timedHost.body.villagers)) === '["evening","night"]' && !people.text.includes('"when"'), rosaNight(timedHost.body.villagers))
  const places = await ask(mia, '/town/scenes')
  const mare = places.body.scenes?.find((s: any) => s.id === 'il-mare')
  check("visits: GET /town/scenes: the town's curated places (the culture's sea), their words resolved, never the tutor's scenes", places.res.status === 200 && places.body.town === mia.key.fingerprint && mare?.culture === 'friuli' && mare.language === 'it' && mare.objects.find((o: any) => o.slot === 'sea')?.it && mare.objects.find((o: any) => o.slot === 'sea')?.en && !/XQZ|published_at/.test(places.text), { ids: ids(places.body.scenes ?? []), sea: mare?.objects?.[0] })
  const stranger = loadBridgeKey(tempDir('lani-smoke-visit-stranger-'))
  const refusedFor = async (path: string) => [
    (await ask(mia, path, stranger)).res.status,
    (await fetch(`${mia.base}${path}`)).status,
    (await fetch(`${mia.base}${path}`, { headers: mia.auth })).status,
  ]
  const refusals = { villagers: await refusedFor('/town/villagers'), scenes: await refusedFor('/town/scenes') }
  check('visits: an unlinked town, an unsigned request and the app token are refused (401)', Object.values(refusals).every(r => r.every(s => s === 401)), refusals)

  // --- the visitor's bridge: the proxies for its app ---------------------------------------------------------------
  const proxy = (from: Town, to: Town, kind: string, headers = from.auth) => fetch(`${from.base}/towns/${to.key.fingerprint}/${kind}`, { headers })
  const pv = await proxy(jan, mia, 'villagers')
  const pvText = await pv.text()
  const pvBody = JSON.parse(pvText)
  check("visits: GET /towns/:id/villagers: Jan's bridge asks Mia's and passes her town's cast on, checked", pv.status === 200 && pvBody.town === mia.key.fingerprint && pvBody.culture === 'friuli' && pvBody.language === 'it' && ids(pvBody.villagers) === ids(friuli) && !!Date.parse(pvBody.fetched_at) && pvBody.stale === undefined && pvBody.villagers.every((v: any) => v.lines?.greet?.some((l: any) => l.it)), { status: pv.status, ids: ids(pvBody.villagers ?? []) })
  const pvTimed = await (await proxy(jan, mia, 'villagers?time=evening')).json()
  check("visits: … the people's \"when\" for an app that says its time of day, none for an older app", !pvText.includes('"when"') && JSON.stringify(rosaNight(pvTimed.villagers)) === '["evening","night"]', rosaNight(pvTimed.villagers))
  const ps = await proxy(jan, mia, 'scenes')
  const psText = await ps.text()
  const psBody = JSON.parse(psText)
  const pmare = psBody.scenes?.find((s: any) => s.id === 'il-mare')
  check("visits: GET /towns/:id/scenes: … and her town's places, the sea with its Italian words", ps.status === 200 && pmare?.from?.includes('spot:horizon') && pmare.objects.length === mare.objects.length && pmare.objects.every((o: any, i: number) => o.it === mare.objects[i].it && o.en === mare.objects[i].en), { status: ps.status, ids: ids(psBody.scenes ?? []) })
  check('visits: … nothing private passes: no tutor villagers or scenes, no tokens, no storage dates', !/XQZ|published_at|mia-town-token|smoke-token/.test(pvText + psText))
  const pp = await (await proxy(jan, mia, 'public')).json()
  check("visits: GET /towns/:id/public says when it was had", pp.town?.id === mia.key.fingerprint && !!Date.parse(pp.fetched_at) && pp.stale === undefined)
  const again = await (await proxy(jan, mia, 'villagers')).json()
  check('visits: … a town\'s people are kept for a minute (asked again: the same answer, the same time)', again.fetched_at === pvBody.fetched_at && ids(again.villagers) === ids(pvBody.villagers))
  const unknown = await fetch(`${jan.base}/towns/${'0'.repeat(32)}/villagers`, { headers: jan.auth })
  check('visits: an unknown town is a 404; the family token reaches none of it', unknown.status === 404 && (await proxy(jan, mia, 'villagers', fam)).status === 403 && (await fetch(`${jan.base}/towns/${mia.key.fingerprint}/scenes`)).status === 401)

  // --- the other way: Mia visits Jan's Slovene town; what Jan's tutor made (earlier sections) stays home -----------------
  const janOwnV = await (await fetch(`${jan.base}/villagers`, { headers: jan.auth })).json()
  const janOwnS = await (await fetch(`${jan.base}/scenes`, { headers: jan.auth })).json()
  const mv = await (await proxy(mia, jan, 'villagers')).json()
  const ms = await (await proxy(mia, jan, 'scenes')).json()
  const primorska = castOf('primorska')
  const tutorOnlyV = janOwnV.filter((v: any) => v.source === 'tutor' && !primorska.some(p => p.id === v.id)).map((v: any) => v.id)
  const tutorOnlyS = janOwnS.filter((s: any) => s.source === 'tutor').map((s: any) => s.id)
  check("visits: Mia visits Jan's town: its Slovene cast (primorska) and its curated places (its hills among them)", mv.language === 'sl' && mv.culture === 'primorska' && ids(mv.villagers) === ids(primorska) && ms.scenes.some((s: any) => s.id === 'na-gricih' && s.culture === 'primorska'), { villagers: ids(mv.villagers ?? []), scenes: ids(ms.scenes ?? []) })
  check("visits: … none of what Jan's tutor published", !mv.villagers.some((v: any) => tutorOnlyV.includes(v.id)) && !ms.scenes.some((s: any) => s.source !== 'curated' || (tutorOnlyS.includes(s.id) && !janOwnS.some((c: any) => c.id === s.id && c.source === 'curated'))), { tutorOnlyV, tutorOnlyS })

  // the child's town: Jan's bridge knows it; its people are asked for now (and kept, for when it is away)
  const lv = await (await proxy(jan, luka, 'villagers')).json()
  const lp = await (await proxy(jan, luka, 'public')).json()
  check("visits: Jan visits Luka's town too (its cast; its state, kept)", ids(lv.villagers ?? []) === ids(friuli) && lp.town?.id === luka.key.fingerprint, { lv: ids(lv.villagers ?? []), lp })

  // --- the cleaning, pure -------------------------------------------------------------------------------------------------
  const evil = { town: 'x'.repeat(32), culture: 'friuli', language: 'it', villagers: [{ ...rosa, name: 'Rosa <script>\u0007' }, { id: 'broken' }], scenes: [{ ...sea, objects: [{ ...sea.objects[0], it: 'mare<b>', en: 'sea' }], source: 'tutor', published_at: 1 }, { id: 'broken' }] }
  const cv = cleanVillagers(evil, 'x'.repeat(32))
  const cs = cleanScenes(evil, 'x'.repeat(32))
  check("visits: another town's answers are cleaned: shapes checked, texts plain, the broken left out, another town's refused", cv?.villagers.length === 1 && cv.villagers[0].name === 'Rosa  script  ' && cs?.scenes.length === 1 && cs.scenes[0].objects[0].it === 'mare b ' && cs.scenes[0].source === 'curated' && !('published_at' in cs.scenes[0]) && cleanVillagers(evil, 'y'.repeat(32)) === undefined && cleanScenes({ ...evil, town: 'z' }, 'x'.repeat(32)) === undefined, { cv: cv?.villagers[0]?.name, cs: cs?.scenes[0]?.objects[0] })
  const c = new PeerCache(1_000, 5_000)
  c.put('t', 'villagers', 1, 0)
  check('visits: the cache: fresh for a while, stale after, forgotten after that or when dropped', c.fresh('t', 'villagers', 999) !== undefined && c.fresh('t', 'villagers', 1_000) === undefined && c.stale('t', 'villagers', 4_999) !== undefined && c.stale('t', 'villagers', 5_000) === undefined && (c.drop('t'), c.stale('t', 'villagers', 1) === undefined))
}

/** Luka's town went away ([luka] already stopped): Jan's app still sees its last state, marked; what it never had, a 502. */
export async function visitsOffline(jan: Town, luka: Town) {
  const pub = await fetch(`${jan.base}/towns/${luka.key.fingerprint}/public`, { headers: jan.auth })
  const pubBody = await pub.json()
  const scenes = await fetch(`${jan.base}/towns/${luka.key.fingerprint}/scenes`, { headers: jan.auth })
  const scenesBody = await scenes.json()
  const people = await (await fetch(`${jan.base}/towns/${luka.key.fingerprint}/villagers`, { headers: jan.auth })).json()
  check("visits: a town that isn't answering: its last state, stale, with when it was had", pub.status === 200 && pubBody.stale === true && pubBody.town?.id === luka.key.fingerprint && !!Date.parse(pubBody.fetched_at), pubBody)
  check("visits: … what was never had is a 502 'unreachable'; its people, had a moment ago, are still kept", scenes.status === 502 && scenesBody.code === 'unreachable' && people.villagers?.length > 0, { scenes: scenesBody, people: people.code })
}
