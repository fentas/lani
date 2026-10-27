// Smoke checks: a child's town without the PC (src/town-requests.ts, src/features/town-requests.ts, the join and accept of
// src/features/towns.ts, lani-town requests | approve | decline), with the towns section's bridges: Jan (the harness's, an
// adult's), Mia (an adult's) and Luka (a child's), Jan's town Luka's guardian, Mia not linked with Luka. Luka accepts
// Mia's invitation and the family page says no; Mia accepts Luka's and Jan's app says yes; then strangers' links that wait:
// that a non-guardian (unlinked, or linked but not the guardian) can't see or decide them, the child's app can't approve
// (not the family page's route, not the parent's), a signed decision can't be replayed and a decision can't be turned, a
// request for another town is refused; the child takes one back, the parent declines one on the node; one child's town to
// another's (both parents say yes, in either order). Called by towns.ts after the progress and voice checks.
import { readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import type { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { loadBridgeKey, type BridgeKey } from '../../src/pairing'
import { parseInvite, signRequest } from '../../src/towns'
import { check, tempDir } from './harness'
import type { Town } from './visits'

type Peer = Town & { notes?: any[]; client?: Client }

const cliDir = resolve(import.meta.dir, '../../src/cli')
const towns = (d: string) => JSON.parse(readFileSync(join(d, 'app/towns.json'), 'utf8')).links as any[]
const asked = (d: string) => JSON.parse(readFileSync(join(d, 'app/town-requests.json'), 'utf8')).requests as any[]
const call = async (t: Town, method: string, path: string, body?: unknown, headers: Record<string, string> = t.auth) => {
  const r = await fetch(`${t.base}${path}`, { method, headers, ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
  return { status: r.status, body: (await r.json().catch(() => null)) as any }
}
/** A request signed by [key] to town [to], straight to its bridge: its exact [headers] and [text], to send again. */
function signed(key: BridgeKey, to: Town, method: 'GET' | 'POST', path: string, body?: unknown) {
  const text = body === undefined ? '' : JSON.stringify(body)
  const headers = { ...signRequest(key, { method, path, body: text, to: to.key.fingerprint }), ...(body === undefined ? {} : { 'content-type': 'application/json' }) }
  const send = async () => {
    const r = await fetch(`${to.base}${path}`, { method, headers, ...(body === undefined ? {} : { body: text }) })
    return { status: r.status, body: (await r.json().catch(() => null)) as any }
  }
  return { send }
}
const backlog = async (t: Town) => ((await call(t, 'GET', '/events/backlog?since=0')).body ?? []).map((b: any) => b.event)
const linkEvents = async (t: Town, kind: string) => (await backlog(t)).filter((e: any) => e.type === 'town_link' && e.kind === kind)
const cli = (args: string[], env: Record<string, string>) => {
  const p = Bun.spawnSync(['bun', join(cliDir, 'town.ts'), ...args], { env: { ...process.env, ...env }, stdout: 'pipe', stderr: 'pipe' })
  return { code: p.exitCode, out: p.stdout.toString() + p.stderr.toString() }
}
/** Waits up to [ms] for [ok] (a notice in the background: the guardian's town told). */
async function until(ok: () => Promise<boolean>, ms = 3_000) {
  for (const end = Date.now() + ms; Date.now() < end; await Bun.sleep(100)) if (await ok()) return true
  return ok()
}

/** A town with no bridge of its own (its key only), joining [to]'s invitation [code] with a signed POST /town/join. */
function stranger(name: string) {
  const key = loadBridgeKey(tempDir('lani-smoke-stranger-'))
  const join = (to: Town, code: string, extra: Record<string, unknown> = {}) => {
    const body = { v: 1, code, town: { id: key.fingerprint, public_key: key.spki.toString('base64'), url: 'http://127.0.0.1:9', display: { name, learner: name, culture: 'friuli', language: 'it' } }, ...extra }
    return signed(key, to, 'POST', '/town/join', body).send()
  }
  return { key, join }
}

export async function townRequestChecks(jan: Peer, mia: Peer, luka: Peer, cliEnv: Record<string, string>) {
  const ids = { jan: jan.key.fingerprint, mia: mia.key.fingerprint, luka: luka.key.fingerprint }
  const family = { authorization: `Bearer ${readFileSync(join(luka.data, 'app/family-token'), 'utf8').trim()}`, 'content-type': 'application/json' }
  check('requests: to begin, Mia and Luka are not linked; Jan is Luka\'s guardian', !towns(mia.data).some(l => l.id === ids.luka) && !towns(luka.data).some(l => l.id === ids.mia) && towns(luka.data).find(l => l.id === ids.jan)?.guardian === true)

  // --- Luka accepts Mia's invitation (its web link); the family page says no ----------------------------------------------
  const miaInv = (await call(mia, 'POST', '/towns/invite')).body
  const took = await call(luka, 'POST', '/towns/accept', { invite: miaInv.web_link })
  check("requests: Luka's app accepts Mia's invitation by its web link: it waits for the parent (pending: parent)", took.status === 200 && took.body.pending === 'parent' && took.body.town?.id === ids.mia && took.body.town.learner === 'Mia', took)
  const r1 = took.body.request as string
  const mine = asked(luka.data).find(r => r.id === r1)
  check("requests: … Luka's town keeps a request (via accept), and links nothing; Mia's links Luka as waiting for the parent", mine?.status === 'pending' && mine.via === 'accept' && mine.town.id === ids.mia && !towns(luka.data).some(l => l.id === ids.mia) && towns(mia.data).find(l => l.id === ids.luka)?.pending === 'guardian', mine)
  const lukaList = (await call(luka, 'GET', '/towns')).body
  check("requests: … Luka's GET /towns: the parent approves (parent_approves, the guardian Jan), the request waits, Mia isn't a linked town", lukaList.self?.parent_approves === true && lukaList.self.guardians?.[0]?.id === ids.jan && lukaList.requests?.some((r: any) => r.id === r1 && r.status === 'pending' && r.town.name && !('public_key' in r.town) && !('url' in r.town)) && !lukaList.towns.some((t: any) => t.id === ids.mia), lukaList)
  const miaRow = (await call(mia, 'GET', '/towns')).body.towns.find((t: any) => t.id === ids.luka)
  check("requests: … Mia's GET /towns shows Luka's town as pending (no state yet: it refuses Mia until the parent says yes)", miaRow?.status === 'pending' && miaRow.age === undefined, miaRow)
  check("requests: … Jan's app hears of it (town_link request: the child, and the town that waits)", await until(async () => (await linkEvents(jan, 'request')).some((e: any) => e.request === r1 && e.child?.id === ids.luka && e.child.learner === 'Luka' && e.town?.learner === 'Mia')), await linkEvents(jan, 'request'))
  check("requests: … and Luka's app (town_link pending)", (await linkEvents(luka, 'pending')).some((e: any) => e.request === r1))

  // nobody but the guardian sees or decides it; the child's app can't
  const stranger1 = loadBridgeKey(tempDir('lani-smoke-stranger-'))
  const nobody = {
    miaUnlinked: await signed(mia.key, luka, 'GET', '/town/guardian/requests').send(),
    strangerRead: await signed(stranger1, luka, 'GET', '/town/guardian/requests').send(),
    strangerDecide: await signed(stranger1, luka, 'POST', '/town/guardian/decide', { v: 1, request: r1, approve: true }).send(),
    unsigned: await call(luka, 'POST', '/town/guardian/decide', { v: 1, request: r1, approve: true }, { 'content-type': 'application/json' }),
  }
  check('requests: an unlinked town (Mia, whose link waits), a stranger, no signature: 401 for the requests and a decision', Object.values(nobody).every(r => r.status === 401) && nobody.miaUnlinked.body.code === 'unlinked' && nobody.unsigned.body.code === 'unsigned', nobody)
  const childFamily = await call(luka, 'POST', `/family/api/towns/requests/${r1}`, { approve: true })
  const childParent = await call(luka, 'POST', `/towns/children/${ids.mia}/requests/${r1}`, { approve: true })
  check("requests: Luka's app can't approve its own link: the family page's route takes the family token only (403 family_only), a child's town decides nothing for others (403 child)", childFamily.status === 403 && childFamily.body.code === 'family_only' && childParent.status === 403 && childParent.body.code === 'child', { childFamily, childParent })
  check('requests: … and nothing was linked by trying', asked(luka.data).find(r => r.id === r1)?.status === 'pending' && !towns(luka.data).some(l => l.id === ids.mia))

  // the parent sees it: in their app (with Mia's village), and on the child's family page
  const kids = (await call(jan, 'GET', '/towns/children')).body
  const kid = kids.children?.find((c: any) => c.id === ids.luka)
  const shown = kid?.requests?.find((r: any) => r.id === r1)
  check("requests: Jan's app (GET /towns/children): Luka, Jan the guardian, Mia's town waiting, with how its village looks", kid?.guardian === true && kid.linked === true && shown?.town?.id === ids.mia && shown.town.learner === 'Mia' && shown.via === 'accept' && shown.preview?.age === 'VAS' && shown.preview.villagers === 3 && shown.preview.buildings?.HUT === 1, kid)
  const page = await call(luka, 'GET', '/family/api/towns', undefined, family)
  check("requests: the family page (GET /family/api/towns): a child's, its request with the preview, its linked towns with the guardian", page.status === 200 && page.body.child === true && page.body.learner === 'Luka' && page.body.requests.some((r: any) => r.id === r1 && r.preview?.age === 'VAS') && page.body.towns.some((t: any) => t.id === ids.jan && t.guardian === true), page.body)
  const no = await call(luka, 'POST', `/family/api/towns/requests/${r1}`, { approve: false, town: ids.mia }, family)
  check('requests: the family page says no: declined, and Mia\'s town is told to let go', no.status === 200 && no.body.status === 'declined' && no.body.told === true && !towns(mia.data).some(l => l.id === ids.luka) && !towns(luka.data).some(l => l.id === ids.mia) && asked(luka.data).find(r => r.id === r1)?.by === 'family', no)
  const again = await call(luka, 'POST', `/family/api/towns/requests/${r1}`, { approve: true }, family)
  check('requests: … a decision is final: approving it afterwards is a 409 (decided)', again.status === 409 && again.body.code === 'decided' && !towns(luka.data).some(l => l.id === ids.mia), again)
  check("requests: … Luka's app hears it (town_link declined)", (await linkEvents(luka, 'declined')).some((e: any) => e.request === r1))

  // --- Mia accepts Luka's invitation; Jan's app says yes ------------------------------------------------------------------
  const lukaInv = (await call(luka, 'POST', '/towns/invite')).body
  const miaTook = await call(mia, 'POST', '/towns/accept', { invite: lukaInv.link })
  check("requests: Mia's app accepts Luka's invitation: linked on her side as waiting for Luka's parent (pending: their_parent)", miaTook.status === 200 && miaTook.body.pending === 'their_parent' && miaTook.body.town?.id === ids.luka && towns(mia.data).find(l => l.id === ids.luka)?.pending === 'guardian', miaTook)
  const r2 = asked(luka.data).find(r => r.town.id === ids.mia && r.status === 'pending')
  check("requests: … Luka's town keeps it (via invite: Mia accepted Luka's invitation)", r2?.via === 'invite', r2)
  const yes = await call(jan, 'POST', `/towns/children/${ids.luka}/requests/${r2.id}`, { approve: true, town: ids.mia })
  check("requests: Jan's app says yes (signed by Jan's town, Luka's guardian): Luka's town links Mia's, not as a guardian", yes.status === 200 && yes.body.status === 'approved' && towns(luka.data).some(l => l.id === ids.mia && l.via === 'invite' && !l.guardian) && asked(luka.data).find(r => r.id === r2.id)?.by === 'guardian' && asked(luka.data).find(r => r.id === r2.id)?.guardian === ids.jan, yes)
  check("requests: … Mia's town was told (POST /town/approved): no longer waiting, and both see each other", !towns(mia.data).find(l => l.id === ids.luka)?.pending && (await call(mia, 'GET', '/towns')).body.towns.find((t: any) => t.id === ids.luka)?.status === 'ok' && (await call(luka, 'GET', '/towns')).body.towns.find((t: any) => t.id === ids.mia)?.status === 'ok')
  check("requests: … Luka's app and Mia's hear it (town_link linked)", (await linkEvents(luka, 'linked')).some((e: any) => e.request === r2.id) && (await linkEvents(mia, 'linked')).some((e: any) => e.town?.id === ids.luka))
  const twice = await call(jan, 'POST', `/towns/children/${ids.luka}/requests/${r2.id}`, { approve: false })
  check("requests: … Jan's app can't turn it afterwards (409 decided, passed on)", twice.status === 409 && twice.body.code === 'decided' && towns(luka.data).some(l => l.id === ids.mia), twice)

  // --- strangers' links that wait: a linked non-guardian, a replay, a mismatch; withdrawn by the child, declined on the node -----
  const s1 = stranger('Tujec')
  const code1 = (await call(luka, 'POST', '/towns/invite')).body.code
  const joined = await s1.join(luka, code1)
  check("requests: a town joins Luka's invitation: the signed answer says it waits for the parent (pending: guardian)", joined.status === 200 && joined.body.pending === 'guardian' && joined.body.town?.id === ids.luka, joined)
  const q1 = asked(luka.data).find(r => r.town.id === s1.key.fingerprint && r.status === 'pending')
  const miaRead = await signed(mia.key, luka, 'GET', '/town/guardian/requests').send()
  const miaDecide = await signed(mia.key, luka, 'POST', '/town/guardian/decide', { v: 1, request: q1.id, approve: true }).send()
  check("requests: Mia's town, linked with Luka's but not its guardian, can't see or decide what waits (403 not_guardian)", miaRead.status === 403 && miaRead.body.code === 'not_guardian' && miaDecide.status === 403 && miaDecide.body.code === 'not_guardian' && asked(luka.data).find(r => r.id === q1.id)?.status === 'pending', { miaRead, miaDecide })
  const janRead = await signed(jan.key, luka, 'GET', '/town/guardian/requests').send()
  check("requests: Jan's town (the guardian) reads them, signed back, without the other town's key or address", janRead.status === 200 && janRead.body.requests.some((r: any) => r.id === q1.id && r.town.name === 'Tujec' && r.preview === null) && !JSON.stringify(janRead.body).includes(s1.key.spki.toString('base64')) && !JSON.stringify(janRead.body).includes('127.0.0.1:9'), janRead.body)
  const decision = signed(jan.key, luka, 'POST', '/town/guardian/decide', { v: 1, request: q1.id, approve: false, town: s1.key.fingerprint })
  const first = await decision.send()
  const replay = await decision.send()
  const turned = await signed(jan.key, luka, 'POST', '/town/guardian/decide', { v: 1, request: q1.id, approve: true }).send()
  check("requests: Jan's signed no: declined (that town couldn't be told: it has no bridge); the same request again is a replay (401), a new yes for it a 409", first.status === 200 && first.body.status === 'declined' && first.body.told === false && replay.status === 401 && replay.body.code === 'replayed' && turned.status === 409 && turned.body.code === 'decided' && !towns(luka.data).some(l => l.id === s1.key.fingerprint), { first, replay, turned })

  const s2 = stranger('Drugi')
  await s2.join(luka, (await call(luka, 'POST', '/towns/invite')).body.code)
  const q2 = asked(luka.data).find(r => r.town.id === s2.key.fingerprint && r.status === 'pending')
  const mismatch = await signed(jan.key, luka, 'POST', '/town/guardian/decide', { v: 1, request: q2.id, approve: true, town: ids.mia }).send()
  check("requests: a decision for another town than the request's is refused (409 mismatch), nothing linked", mismatch.status === 409 && mismatch.body.code === 'mismatch' && asked(luka.data).find(r => r.id === q2.id)?.status === 'pending' && !towns(luka.data).some(l => l.id === s2.key.fingerprint), mismatch)
  const listed = cli(['requests', '--profile', 'luka'], cliEnv)
  check('requests: lani-town requests --profile luka lists what waits for the parent', listed.code === 0 && listed.out.includes(q2.id) && listed.out.includes('Drugi'), listed.out)
  const declined = cli(['decline', q2.id, '--profile', 'luka'], cliEnv)
  check('requests: lani-town decline <request> --profile luka: declined on the node', declined.code === 0 && asked(luka.data).find(r => r.id === q2.id)?.status === 'declined' && asked(luka.data).find(r => r.id === q2.id)?.by === 'node', declined.out)

  const s3 = stranger('Tretji')
  await s3.join(luka, (await call(luka, 'POST', '/towns/invite')).body.code)
  const q3 = asked(luka.data).find(r => r.town.id === s3.key.fingerprint && r.status === 'pending')
  const back = await call(luka, 'POST', `/towns/requests/${q3.id}/withdraw`)
  check("requests: the child takes one back (POST /towns/requests/:id/withdraw): withdrawn, nothing linked", back.status === 200 && back.body.status === 'withdrawn' && asked(luka.data).find(r => r.id === q3.id)?.by === 'child' && !towns(luka.data).some(l => l.id === s3.key.fingerprint), back)

  // --- one child's town and another's: each parent says yes for their own side ----------------------------------------------
  // A town that is a child's too (a key only) joins Luka's invitation, saying its own parent decides for its side.
  const kid2 = stranger('Ana')
  const kidJoin = await kid2.join(luka, (await call(luka, 'POST', '/towns/invite')).body.code, { pending: 'guardian' })
  const q4 = asked(luka.data).find(r => r.town.id === kid2.key.fingerprint && r.status === 'pending')
  check("requests: a child's town joins a child's invitation: both wait (the request knows the other side waits too)", kidJoin.body.pending === 'guardian' && q4?.theirs_pending === true, q4)
  const ok4 = await signed(jan.key, luka, 'POST', '/town/guardian/decide', { v: 1, request: q4.id, approve: true }).send()
  check("requests: … Luka's parent says yes first: Luka's town links it, still waiting for the other parent", ok4.status === 200 && towns(luka.data).find(l => l.id === kid2.key.fingerprint)?.pending === 'guardian', ok4)
  const theirs = await signed(kid2.key, luka, 'POST', '/town/approved', { v: 1 }).send()
  check("requests: … the other town's parent says yes (POST /town/approved, signed by it): Luka's link is whole", theirs.status === 200 && towns(luka.data).find(l => l.id === kid2.key.fingerprint) && !towns(luka.data).find(l => l.id === kid2.key.fingerprint)?.pending, theirs)
  const kid3 = stranger('Bor')
  await kid3.join(luka, (await call(luka, 'POST', '/towns/invite')).body.code, { pending: 'guardian' })
  const early = await signed(kid3.key, luka, 'POST', '/town/approved', { v: 1 }).send()
  const q5 = asked(luka.data).find(r => r.town.id === kid3.key.fingerprint && r.status === 'pending')
  const ok5 = await signed(jan.key, luka, 'POST', '/town/guardian/decide', { v: 1, request: q5.id, approve: true }).send()
  check("requests: … the other way round (the other parent's yes first, kept on the request): Luka's parent's yes links it whole", early.status === 200 && q5?.theirs_approved === true && ok5.status === 200 && towns(luka.data).some(l => l.id === kid3.key.fingerprint && !l.pending), { early, q5 })
  const nobodyApproves = await signed(loadBridgeKey(tempDir('lani-smoke-stranger-')), luka, 'POST', '/town/approved', { v: 1 }).send()
  check('requests: … POST /town/approved from a town with neither a link nor a request here: 401', nobodyApproves.status === 401, nobodyApproves)
  // they let go again (a town tells it unlinks: POST /town/unlink), so the later checks see Luka's links as before
  for (const k of [kid2, kid3]) await signed(k.key, luka, 'POST', '/town/unlink', {}).send()
  check('requests: … and let go (POST /town/unlink)', !towns(luka.data).some(l => [kid2.key.fingerprint, kid3.key.fingerprint].includes(l.id)))

  // a town whose link waited takes it back itself (POST /town/unlink with the request's key): the request is withdrawn
  const s6 = stranger('Šesti')
  await s6.join(luka, (await call(luka, 'POST', '/towns/invite')).body.code)
  const gone = await signed(s6.key, luka, 'POST', '/town/unlink', {}).send()
  check('requests: a town whose link waits for the parent takes it back (POST /town/unlink): the request is withdrawn (by them)', gone.status === 200 && asked(luka.data).find(r => r.town.id === s6.key.fingerprint)?.status === 'withdrawn' && asked(luka.data).find(r => r.town.id === s6.key.fingerprint)?.by === 'them', gone)
  check('requests: requests.json holds no secrets (no private key, no token, no invitation code)', !readFileSync(join(luka.data, 'app/town-requests.json'), 'utf8').includes('PRIVATE') && !readFileSync(join(luka.data, 'app/town-requests.json'), 'utf8').includes(code1))
  check('requests: the web link of a child\'s invitation reads back as its lani:// link', JSON.stringify(parseInvite(lukaInv.web_link)) === JSON.stringify(parseInvite(lukaInv.link)))
}
